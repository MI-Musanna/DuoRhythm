package com.duorhythm.app.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.duorhythm.app.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Quarter-Circle Radial Speed-Dial FAB (+ -> x) using a pure FrameLayout circular button
 * with explicit touch hit-testing so the close (X) toggle works 100% reliably on every ROM.
 */
class SpeedDialFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class SpeedDialItem(
        val label: String,
        val iconRes: Int,
        val accentHex: String = "#38BDF8",
        val onClick: () -> Unit
    )

    private val density = resources.displayMetrics.density
    private val scrimView: View
    private val mainFabButton: FrameLayout
    private val mainFabBg: GradientDrawable
    private val mainFabIcon: ImageView
    private val nodeViews = mutableListOf<LinearLayout>()
    private val hitRect = Rect()

    var isExpanded = false
        private set
    private var baseBottomOffsetDp = 88f
    private var systemBottomInsetDp = 0f

    private val fabSizeDp = 56f
    private val fabMarginEndDp = 20f
    private val nodeBoxWidthDp = 88f
    private val alignedNodeMarginEndDp = fabMarginEndDp + (fabSizeDp / 2f) - (nodeBoxWidthDp / 2f) // 4dp

    init {
        clipChildren = false
        clipToPadding = false
        elevation = 24f * density

        // Full-screen translucent scrim
        scrimView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#CC0A0B0E"))
            alpha = 0f
            visibility = GONE
            isClickable = true
            isFocusable = true
            elevation = 10f * density
            setOnClickListener { collapse() }
        }
        addView(scrimView)

        // Pure FrameLayout Circular Cyan FAB (zero FloatingActionButton rotated contentRect bug)
        mainFabBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#38BDF8"))
            setStroke((1.5f * density).toInt(), Color.parseColor("#80FFFFFF"))
        }

        mainFabIcon = ImageView(context).apply {
            layoutParams = LayoutParams(
                (26f * density).toInt(),
                (26f * density).toInt(),
                Gravity.CENTER
            )
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#0A0B0E"))
        }

        mainFabButton = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                (fabSizeDp * density).toInt(),
                (fabSizeDp * density).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = (fabMarginEndDp * density).toInt()
                bottomMargin = ((baseBottomOffsetDp + systemBottomInsetDp) * density).toInt()
            }
            background = mainFabBg
            isClickable = true
            isFocusable = true
            elevation = 32f * density
            translationZ = 32f * density
            addView(mainFabIcon)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (isExpanded) collapse() else expand()
            }
        }
        addView(mainFabButton)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val sysBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom
            systemBottomInsetDp = sysBottom / density
            applyEffectiveBottomOffset()
            insets
        }
    }

    private fun applyEffectiveBottomOffset() {
        val totalDp = baseBottomOffsetDp + systemBottomInsetDp
        (mainFabButton.layoutParams as? LayoutParams)?.let { lp ->
            lp.bottomMargin = (totalDp * density).toInt()
            mainFabButton.layoutParams = lp
        }
        nodeViews.forEach { node ->
            (node.layoutParams as? LayoutParams)?.let { lp ->
                lp.bottomMargin = ((totalDp + 2f) * density).toInt()
                node.layoutParams = lp
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isExpanded) {
            // When collapsed, only handle touches directly over the circular FAB button
            val fabRect = Rect()
            mainFabButton.getGlobalVisibleRect(fabRect)
            if (!fabRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                return false
            }
        } else if (ev.action == MotionEvent.ACTION_UP) {
            // Explicit safety override: tapping mainFabButton (X) while expanded ALWAYS collapses immediately
            mainFabButton.getHitRect(hitRect)
            if (hitRect.contains(ev.x.toInt(), ev.y.toInt())) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                collapse()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun syncWorkspaceAccent(isWorkMode: Boolean) {
        val hex = if (isWorkMode) "#10B981" else "#38BDF8"
        mainFabBg.setColor(Color.parseColor(hex))
        mainFabButton.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val isWork = try {
            val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("is_work_mode", false)
        } catch (_: Exception) {
            false
        }
        syncWorkspaceAccent(isWork)
    }

    fun setBottomOffsetDp(dp: Float) {
        baseBottomOffsetDp = dp
        applyEffectiveBottomOffset()
    }

    fun setItems(items: List<SpeedDialItem>) {
        if (isExpanded) collapse(immediate = true)
        nodeViews.forEach { removeView(it) }
        nodeViews.clear()

        val limited = items.take(4)
        val count = limited.size
        if (count == 0) return

        val radiusPx = when (count) {
            4 -> 154f * density
            3 -> 132f * density
            else -> 110f * density
        }
        val startAngleDeg = 92.0
        val endAngleDeg = 178.0
        val stepDeg = if (count > 1) (endAngleDeg - startAngleDeg) / (count - 1) else 0.0

        limited.forEachIndexed { index, item ->
            val accentColor = try {
                Color.parseColor(item.accentHex)
            } catch (_: Exception) {
                Color.parseColor("#38BDF8")
            }

            val angleRad = Math.toRadians(startAngleDeg + (index * stepDeg))
            val targetX = (cos(angleRad) * radiusPx).toFloat()
            val targetY = (-sin(angleRad) * radiusPx).toFloat()

            val nodeColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                visibility = GONE
                alpha = 0f
                scaleX = 0.6f
                scaleY = 0.6f
                elevation = 16f * density
                tag = Triple(targetX, targetY, angleRad)

                layoutParams = LayoutParams(
                    (nodeBoxWidthDp * density).toInt(),
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = (alignedNodeMarginEndDp * density).toInt()
                    bottomMargin = ((baseBottomOffsetDp + systemBottomInsetDp + 2f) * density).toInt()
                }

                val circleBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#14171F"))
                    setStroke((1.5f * density).toInt(), accentColor)
                }

                val iconCircle = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (42f * density).toInt(),
                        (42f * density).toInt()
                    )
                    background = circleBg
                    elevation = 6f * density

                    val iv = ImageView(context).apply {
                        layoutParams = LayoutParams(
                            (19f * density).toInt(),
                            (19f * density).toInt(),
                            Gravity.CENTER
                        )
                        setImageResource(item.iconRes)
                        imageTintList = ColorStateList.valueOf(accentColor)
                    }
                    addView(iv)
                }

                val badgeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9f * density
                    setColor(Color.parseColor("#F51E222D"))
                    setStroke((1f * density).toInt(), Color.parseColor("#4D2B303E"))
                }

                val labelBadge = TextView(context).apply {
                    text = item.label
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    background = badgeBg
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(
                        (6f * density).toInt(),
                        (2f * density).toInt(),
                        (6f * density).toInt(),
                        (2f * density).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (3f * density).toInt()
                    }
                }

                addView(iconCircle)
                addView(labelBadge)

                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    collapse()
                    postDelayed({ item.onClick() }, 130L)
                }
            }

            nodeViews.add(nodeColumn)
            addView(nodeColumn, indexOfChild(mainFabButton))
        }
        mainFabButton.bringToFront()
    }

    fun expand() {
        if (isExpanded || nodeViews.isEmpty()) return
        isExpanded = true

        scrimView.visibility = VISIBLE
        scrimView.animate().alpha(1f).setDuration(200L).start()

        mainFabButton.bringToFront()
        mainFabIcon.animate()
            .rotation(45f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
        mainFabButton.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()

        val liveWidth = if (width > 0) width.toFloat() else resources.displayMetrics.widthPixels.toFloat()
        val maxSafeRadius = (liveWidth - (88f * density)).coerceAtLeast(96f * density)
        val baseRadius = when (nodeViews.size) {
            4 -> 154f * density
            3 -> 132f * density
            else -> 110f * density
        }
        val safeRadius = minOf(baseRadius, maxSafeRadius)

        nodeViews.forEachIndexed { index, node ->
            val triple = node.tag as? Triple<Float, Float, Double>
            val tx = if (triple != null) (kotlin.math.cos(triple.third) * safeRadius).toFloat() else 0f
            val ty = if (triple != null) (-kotlin.math.sin(triple.third) * safeRadius).toFloat() else (-80f * density)
            node.visibility = VISIBLE
            node.animate().cancel()
            node.animate()
                .translationX(tx)
                .translationY(ty)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 26L)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(1.65f))
                .start()
        }
    }

    fun collapse(immediate: Boolean = false) {
        if (!isExpanded && !immediate) return
        isExpanded = false

        if (immediate) {
            scrimView.visibility = GONE
            scrimView.alpha = 0f
            mainFabIcon.rotation = 0f
            mainFabButton.scaleX = 1f
            mainFabButton.scaleY = 1f
            nodeViews.forEach { node ->
                node.visibility = GONE
                node.translationX = 0f
                node.translationY = 0f
                node.alpha = 0f
                node.scaleX = 0.6f
                node.scaleY = 0.6f
            }
            return
        }

        scrimView.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { if (!isExpanded) scrimView.visibility = GONE }
            .start()

        mainFabIcon.animate()
            .rotation(0f)
            .setDuration(200L)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()
        mainFabButton.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200L)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()

        nodeViews.forEach { node ->
            node.animate().cancel()
            node.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(0f)
                .scaleX(0.6f)
                .scaleY(0.6f)
                .setStartDelay(0L)
                .setDuration(170L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction { if (!isExpanded) node.visibility = GONE }
                .start()
        }
    }
}
