package com.duorhythm.app.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.cos
import kotlin.math.sin

/**
 * Quarter-Circle Radial Speed-Dial FAB (+ -> x) blooming in a clean circular arc
 * around the bottom-right corner with guaranteed clickable close toggle.
 */
class SpeedDialFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class SpeedDialItem(
        val label: String,
        val iconRes: Int,
        val accentHex: String = "#00E5FF",
        val onClick: () -> Unit
    )

    private val density = resources.displayMetrics.density
    private val scrimView: View
    private val mainFab: FloatingActionButton
    private val nodeViews = mutableListOf<LinearLayout>()
    var isExpanded = false
        private set
    private var bottomOffsetDp = 92f

    init {
        clipChildren = false
        clipToPadding = false
        elevation = 20f * density

        scrimView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#BD06080F"))
            alpha = 0f
            visibility = GONE
            isClickable = true
            isFocusable = true
            elevation = 10f * density
            setOnClickListener { collapse() }
        }
        addView(scrimView)

        mainFab = FloatingActionButton(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = (20f * density).toInt()
                bottomMargin = (bottomOffsetDp * density).toInt()
            }
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#06080F"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            compatElevation = 24f * density
            elevation = 24f * density
            translationZ = 24f * density
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (isExpanded) collapse() else expand()
            }
        }
        addView(mainFab)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!isExpanded) {
            // When collapsed, only handle touches directly over the circular FAB
            if (ev != null) {
                val fabRect = Rect()
                mainFab.getGlobalVisibleRect(fabRect)
                if (!fabRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    return false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setBottomOffsetDp(dp: Float) {
        bottomOffsetDp = dp
        (mainFab.layoutParams as? LayoutParams)?.let { lp ->
            lp.bottomMargin = (dp * density).toInt()
            mainFab.layoutParams = lp
        }
        nodeViews.forEach { node ->
            (node.layoutParams as? LayoutParams)?.let { lp ->
                lp.bottomMargin = ((dp + 4f) * density).toInt()
                node.layoutParams = lp
            }
        }
    }

    fun setItems(items: List<SpeedDialItem>) {
        if (isExpanded) collapse(immediate = true)
        nodeViews.forEach { removeView(it) }
        nodeViews.clear()

        val count = items.take(4).size
        if (count == 0) return

        // Quarter-circle arc angles from 94 deg (top of FAB) to 176 deg (left of FAB)
        val radiusPx = (if (count >= 4) 132f else 116f) * density
        val startAngleDeg = 94.0
        val endAngleDeg = 176.0
        val stepDeg = if (count > 1) (endAngleDeg - startAngleDeg) / (count - 1) else 0.0

        items.take(4).forEachIndexed { index, item ->
            val accentColor = try {
                Color.parseColor(item.accentHex)
            } catch (_: Exception) {
                Color.parseColor("#00E5FF")
            }

            val angleRad = Math.toRadians(startAngleDeg + (index * stepDeg))
            // Polar coordinates in top-left quadrant relative to bottom-right FAB
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
                tag = Pair(targetX, targetY)

                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = (22f * density).toInt()
                    bottomMargin = ((bottomOffsetDp + 4f) * density).toInt()
                }

                // Circular glowing mini-FAB node
                val circleBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#0D1526"))
                    setStroke((1.5f * density).toInt(), accentColor)
                }

                val iconCircle = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (46f * density).toInt(),
                        (46f * density).toInt()
                    )
                    background = circleBg
                    elevation = 6f * density

                    val iv = ImageView(context).apply {
                        layoutParams = LayoutParams(
                            (21f * density).toInt(),
                            (21f * density).toInt(),
                            Gravity.CENTER
                        )
                        setImageResource(item.iconRes)
                        imageTintList = ColorStateList.valueOf(accentColor)
                    }
                    addView(iv)
                }

                // Compact label badge directly beneath the circular node
                val badgeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f * density
                    setColor(Color.parseColor("#E60B1220"))
                    setStroke((1f * density).toInt(), Color.parseColor("#3394A3B8"))
                }

                val labelBadge = TextView(context).apply {
                    text = item.label
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    background = badgeBg
                    maxLines = 1
                    setPadding(
                        (7f * density).toInt(),
                        (2.5f * density).toInt(),
                        (7f * density).toInt(),
                        (2.5f * density).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4f * density).toInt()
                    }
                }

                addView(iconCircle)
                addView(labelBadge)

                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    collapse()
                    postDelayed({ item.onClick() }, 140L)
                }
            }

            nodeViews.add(nodeColumn)
            addView(nodeColumn, indexOfChild(mainFab))
        }
        mainFab.bringToFront()
    }

    fun expand() {
        if (isExpanded || nodeViews.isEmpty()) return
        isExpanded = true

        scrimView.visibility = VISIBLE
        scrimView.animate().alpha(1f).setDuration(200L).start()

        mainFab.bringToFront()
        mainFab.animate()
            .rotation(45f)
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(230L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        nodeViews.forEachIndexed { index, node ->
            val (tx, ty) = (node.tag as? Pair<Float, Float>) ?: Pair(0f, -80f * density)
            node.visibility = VISIBLE
            node.animate().cancel()
            node.animate()
                .translationX(tx)
                .translationY(ty)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 28L)
                .setDuration(250L)
                .setInterpolator(OvershootInterpolator(1.7f))
                .start()
        }
    }

    fun collapse(immediate: Boolean = false) {
        if (!isExpanded && !immediate) return
        isExpanded = false

        if (immediate) {
            scrimView.visibility = GONE
            scrimView.alpha = 0f
            mainFab.rotation = 0f
            mainFab.scaleX = 1f
            mainFab.scaleY = 1f
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

        mainFab.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(210L)
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
