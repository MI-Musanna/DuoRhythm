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

/**
 * Unified Speed-Dial Fan-Out Floating Action Button (+ -> x) that blooms 3-4 action pills
 * in a radial arc around the bottom-right corner across all screens.
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
    private val itemContainers = mutableListOf<LinearLayout>()
    var isExpanded = false
        private set
    private var bottomOffsetDp = 92f

    init {
        clipChildren = false
        clipToPadding = false
        elevation = 16f * density
        isClickable = false
        isFocusable = false

        // Full-screen translucent scrim when expanded
        scrimView = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#B806080F"))
            alpha = 0f
            visibility = GONE
            setOnClickListener { collapse() }
        }
        addView(scrimView)

        // Main circular cyan FAB (+)
        mainFab = FloatingActionButton(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = (20f * density).toInt()
                bottomMargin = (bottomOffsetDp * density).toInt()
            }
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#06080F"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            compatElevation = 8f * density
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (isExpanded) collapse() else expand()
            }
        }
        addView(mainFab)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!isExpanded) {
            // When collapsed, only handle touch if it's on mainFab
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
    }

    fun setItems(items: List<SpeedDialItem>) {
        if (isExpanded) collapse(immediate = true)
        itemContainers.forEach { removeView(it) }
        itemContainers.clear()

        // Arc offsets fanning upward and leftward around the bottom-right FAB
        val arcOffsetsDp = listOf(
            Pair(0f, -72f),
            Pair(-24f, -134f),
            Pair(-48f, -196f),
            Pair(-68f, -258f)
        )

        items.take(4).forEachIndexed { index, item ->
            val accentColor = try {
                Color.parseColor(item.accentHex)
            } catch (_: Exception) {
                Color.parseColor("#00E5FF")
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = GONE
                alpha = 0f
                scaleX = 0.75f
                scaleY = 0.75f
                elevation = 14f * density

                val (arcX, arcY) = arcOffsetsDp.getOrElse(index) { Pair(-48f, -72f * (index + 1)) }
                tag = Pair(arcX * density, arcY * density)

                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = (24f * density).toInt()
                    bottomMargin = ((bottomOffsetDp + 6f) * density).toInt()
                }

                // Labeled Glass Pill
                val pillBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f * density
                    setColor(Color.parseColor("#0F172A"))
                    setStroke((1.2f * density).toInt(), accentColor)
                }

                val labelView = TextView(context).apply {
                    text = item.label
                    setTextColor(Color.parseColor("#F8FAFC"))
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    background = pillBg
                    setPadding(
                        (14f * density).toInt(),
                        (8f * density).toInt(),
                        (14f * density).toInt(),
                        (8f * density).toInt()
                    )
                }

                // Mini Circular Icon Badge
                val circleBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }

                val iconFrame = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (42f * density).toInt(),
                        (42f * density).toInt()
                    ).apply {
                        marginStart = (10f * density).toInt()
                    }
                    background = circleBg

                    val iv = ImageView(context).apply {
                        layoutParams = LayoutParams(
                            (20f * density).toInt(),
                            (20f * density).toInt(),
                            Gravity.CENTER
                        )
                        setImageResource(item.iconRes)
                        imageTintList = ColorStateList.valueOf(Color.parseColor("#06080F"))
                    }
                    addView(iv)
                }

                addView(labelView)
                addView(iconFrame)

                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    collapse()
                    postDelayed({ item.onClick() }, 130L)
                }
            }

            itemContainers.add(row)
            // Insert below mainFab so mainFab stays on top
            addView(row, indexOfChild(mainFab))
        }
    }

    fun expand() {
        if (isExpanded || itemContainers.isEmpty()) return
        isExpanded = true

        scrimView.visibility = VISIBLE
        scrimView.animate().alpha(1f).setDuration(200L).start()

        mainFab.animate()
            .rotation(45f)
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()

        itemContainers.forEachIndexed { index, row ->
            val (targetX, targetY) = (row.tag as? Pair<Float, Float>) ?: Pair(0f, -72f * density * (index + 1))
            row.visibility = VISIBLE
            row.animate().cancel()
            row.animate()
                .translationX(targetX)
                .translationY(targetY)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 32L)
                .setDuration(250L)
                .setInterpolator(OvershootInterpolator(1.75f))
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
            itemContainers.forEach { row ->
                row.visibility = GONE
                row.translationX = 0f
                row.translationY = 0f
                row.alpha = 0f
                row.scaleX = 0.75f
                row.scaleY = 0.75f
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

        itemContainers.forEach { row ->
            row.animate().cancel()
            row.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(0f)
                .scaleX(0.75f)
                .scaleY(0.75f)
                .setStartDelay(0L)
                .setDuration(170L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction { if (!isExpanded) row.visibility = GONE }
                .start()
        }
    }
}
