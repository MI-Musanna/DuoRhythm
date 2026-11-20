package com.duorhythm.app.ui.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.AppCompatTextView

/**
 * Production Notification Badge enforcing all 7 badge system rules:
 * 1. 99+ cap & fixed compact pill geometry (0 -> GONE)
 * 2. Corner pinning (top|end) with zero icon drift
 * 3. Strict separation between Count Badge (coral-red digits) and Status Dot (cyan live dot)
 * 4. One signal per item (setting count clears dot; setting dot clears count)
 * 5. Open It, Clear It (clear() hides immediately when destination is opened)
 * 6. Count Up, Never Blink (smooth scale pulse when incrementing; never flashes 0)
 * 7. Decision-only signaling
 */
class NotificationBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class BadgeMode {
        NONE,
        COUNT,
        STATUS_DOT
    }

    private val density = resources.displayMetrics.density
    private var currentCount: Int = 0
    private var currentMode: BadgeMode = BadgeMode.NONE

    private val countPillBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 99f * density
        setColor(Color.parseColor("#EF4444"))
        setStroke((1.2f * density).toInt(), Color.parseColor("#0A1322"))
    }

    private val statusDotBg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#2DD4BF"))
        setStroke((1.2f * density).toInt(), Color.parseColor("#0A1322"))
    }

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        typeface = Typeface.DEFAULT_BOLD
        textSize = 9f
        setTextColor(Color.WHITE)
        visibility = GONE
        elevation = 6f * density
    }

    fun getCount(): Int = currentCount

    fun getMode(): BadgeMode = currentMode

    /**
     * Updates actionable count badge with 99+ cap and blink-free count-up pulse.
     */
    fun setCount(newCount: Int) {
        val sanitized = newCount.coerceAtLeast(0)
        if (sanitized == 0) {
            clear()
            return
        }

        val previous = currentCount
        val wasCountMode = (currentMode == BadgeMode.COUNT && visibility == VISIBLE)
        currentCount = sanitized
        currentMode = BadgeMode.COUNT

        val label = if (sanitized > 99) "99+" else sanitized.toString()
        // Rule 6: Never flash 0; update text directly
        if (text?.toString() != label) {
            text = label
        }

        val hPad = if (sanitized >= 10) (4.5f * density).toInt() else (3.5f * density).toInt()
        minWidth = (16f * density).toInt()
        minHeight = (16f * density).toInt()
        setPadding(hPad, 0, hPad, 0)
        background = countPillBg

        if (!wasCountMode) {
            visibility = VISIBLE
            scaleX = 0.65f
            scaleY = 0.65f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(OvershootInterpolator(2.0f))
                .start()
        } else if (sanitized > previous) {
            // Smooth count-up pulse without blinking
            animate().cancel()
            scaleX = 1.20f
            scaleY = 1.20f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(1.8f))
                .start()
        }
    }

    /**
     * Shows a passive live status dot (no digits) while suppressing any count badge (Rules 3 & 4).
     */
    fun setStatusDot(active: Boolean) {
        if (!active) {
            if (currentMode == BadgeMode.STATUS_DOT) {
                clear()
            }
            return
        }
        currentCount = 0
        currentMode = BadgeMode.STATUS_DOT
        text = ""
        val dotPx = (8.5f * density).toInt()
        minWidth = dotPx
        minHeight = dotPx
        setPadding(0, 0, 0, 0)
        background = statusDotBg
        visibility = VISIBLE
    }

    /**
     * Rule 5: Open It, Clear It.
     */
    fun clear() {
        animate().cancel()
        currentCount = 0
        currentMode = BadgeMode.NONE
        text = ""
        visibility = GONE
    }
}
