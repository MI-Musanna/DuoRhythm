package com.pocketdev.duorhythm.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * A minimal circular progress ring with electric cyan glow and cool silver typography.
 */
class CircularProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF")
        strokeWidth = 7f
        style = Paint.Style.STROKE
    }

    private val progressGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D00F2FE")
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F2FE")
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 26f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    private val arcBounds = RectF()
    private var progressFraction = 0.65f
    private var centerPrimaryText = "65%"
    private var centerSecondaryText = "DONE"

    fun setProgress(fraction: Float, primaryText: String = "${(fraction * 100).toInt()}%", secondaryText: String = "DONE") {
        progressFraction = fraction.coerceIn(0f, 1f)
        centerPrimaryText = primaryText
        centerSecondaryText = secondaryText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val padding = 12f
        val diameter = minOf(width, height) - padding * 2
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        arcBounds.set(left, top, left + diameter, top + diameter)

        // Draw background track
        canvas.drawOval(arcBounds, trackPaint)

        // Draw progress arc with electric cyan glow
        val sweepAngle = progressFraction * 360f
        if (sweepAngle > 0f) {
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressGlowPaint)
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, progressPaint)
        }

        // Center typography
        val centerX = width / 2f
        val centerY = height / 2f
        val textY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f) - 4f
        canvas.drawText(centerPrimaryText, centerX, textY, textPaint)

        val subTextY = textY + 20f
        canvas.drawText(centerSecondaryText, centerX, subTextY, subTextPaint)
    }
}
