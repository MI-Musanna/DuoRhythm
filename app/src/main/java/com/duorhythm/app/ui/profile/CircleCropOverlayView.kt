package com.duorhythm.app.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircleCropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B30F172A") // 70% deep navy scrim
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Vibrant cyan ring
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2f
    }

    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3338BDF8") // Subtle translucent inner ring
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1f
    }

    private val scrimPath = Path()
    private val circleBounds = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCircleBounds(w, h)
    }

    private fun updateCircleBounds(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val radius = minOf(w, h) * 0.42f
        val cx = w / 2f
        val cy = h / 2f
        circleBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        scrimPath.reset()
        scrimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        scrimPath.addCircle(cx, cy, radius, Path.Direction.CCW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (circleBounds.isEmpty) return

        // 1. Draw outer scrim leaving circular window transparent
        canvas.drawPath(scrimPath, scrimPaint)

        // 2. Draw 2dp cyan circular guide ring
        val cx = circleBounds.centerX()
        val cy = circleBounds.centerY()
        val r = circleBounds.width() / 2f
        canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawCircle(cx, cy, r - ringPaint.strokeWidth, innerGlowPaint)
    }

    fun getCropCircleBounds(): RectF = RectF(circleBounds)

    fun getCropCircleRadius(): Float = circleBounds.width() / 2f

    fun getCropCircleCenter(): PointF = PointF(circleBounds.centerX(), circleBounds.centerY())
}
