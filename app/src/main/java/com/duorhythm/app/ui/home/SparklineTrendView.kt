package com.duorhythm.app.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * A glowing electric cyan bezier sparkline graph with translucent gradient underfill.
 */
class SparklineTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D10B981")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8010B981")
        style = Paint.Style.FILL
    }

    private var points: FloatArray = floatArrayOf(0.2f, 0.45f, 0.35f, 0.6f, 0.5f, 0.85f, 0.75f, 1.0f)

    fun setPoints(newPoints: FloatArray) {
        if (newPoints.isNotEmpty()) {
            points = newPoints
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || points.size < 2) return

        try {
            val paddingL = paddingLeft.toFloat() + 4f
            val paddingR = paddingRight.toFloat() + 4f
            val paddingT = paddingTop.toFloat() + 6f
            val paddingB = paddingBottom.toFloat() + 2f

            val drawWidth = width.toFloat() - paddingL - paddingR
            val drawHeight = height.toFloat() - paddingT - paddingB
            if (drawWidth <= 1f || drawHeight <= 1f) return

            val minVal = points.minOrNull() ?: 0f
            val maxVal = points.maxOrNull() ?: 1f
            val range = if (maxVal - minVal == 0f) 1f else (maxVal - minVal)

            val path = Path()
            val fillPath = Path()

            val stepX = drawWidth / (points.size - 1)

            val coords = ArrayList<Pair<Float, Float>>()
            for (i in points.indices) {
                val x = paddingL + i * stepX
                val normalized = (points[i] - minVal) / range
                val y = paddingT + drawHeight - (normalized * drawHeight)
                coords.add(Pair(x, y))
            }

            path.moveTo(coords[0].first, coords[0].second)
            fillPath.moveTo(coords[0].first, height.toFloat())
            fillPath.lineTo(coords[0].first, coords[0].second)

            for (i in 1 until coords.size) {
                val prev = coords[i - 1]
                val curr = coords[i]
                val midX = (prev.first + curr.first) / 2f
                path.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
                fillPath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
            }

            fillPath.lineTo(coords.last().first, height.toFloat())
            fillPath.close()

            val safeHeight = maxOf(paddingT + 1f, height.toFloat())
            fillPaint.shader = LinearGradient(
                0f, paddingT,
                0f, safeHeight,
                Color.parseColor("#3310B981"),
                Color.parseColor("#00000000"),
                Shader.TileMode.CLAMP
            )

            val safeDrawWidth = maxOf(1f, drawWidth)
            linePaint.shader = LinearGradient(
                paddingL, 0f,
                paddingL + safeDrawWidth, 0f,
                Color.parseColor("#06B6D4"),
                Color.parseColor("#10B981"),
                Shader.TileMode.CLAMP
            )

            glowPaint.shader = LinearGradient(
                paddingL, 0f,
                paddingL + safeDrawWidth, 0f,
                Color.parseColor("#4D06B6D4"),
                Color.parseColor("#4D10B981"),
                Shader.TileMode.CLAMP
            )

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, linePaint)

            // Pulsing tip dot
            val last = coords.last()
            canvas.drawCircle(last.first, last.second, 8f, dotGlowPaint)
            canvas.drawCircle(last.first, last.second, 3.5f, dotPaint)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
