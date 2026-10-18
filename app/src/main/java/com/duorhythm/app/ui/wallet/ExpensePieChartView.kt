package com.duorhythm.app.ui.wallet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.duorhythm.app.data.model.CategoryExpense
import kotlin.math.min

class ExpensePieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt() // high contrast white
        isFakeBoldText = true
    }

    private val centerSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF94A3B8.toInt() // muted ice-blue text
    }

    private val emptyRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x26FFFFFF.toInt() // translucent glass ring
    }

    private val oval = RectF()
    private var expenseItems: List<CategoryExpense> = emptyList()
    private var centerTotalText: String = "$0.00"

    init {
        setWillNotDraw(false)
    }

    fun setData(items: List<CategoryExpense>, totalFormatted: String) {
        this.expenseItems = items
        this.centerTotalText = totalFormatted
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val size = min(width, height)
        val strokeWidth = size * 0.16f
        slicePaint.strokeWidth = strokeWidth
        emptyRingPaint.strokeWidth = strokeWidth

        val padding = strokeWidth / 2f + 8f
        val left = (width - size) / 2f + padding
        val top = (height - size) / 2f + padding
        val right = (width + size) / 2f - padding
        val bottom = (height + size) / 2f - padding
        oval.set(left, top, right, bottom)

        val centerX = width / 2f
        val centerY = height / 2f

        if (expenseItems.isEmpty()) {
            canvas.drawOval(oval, emptyRingPaint)
            centerTextPaint.textSize = size * 0.08f
            centerSubTextPaint.textSize = size * 0.065f
            canvas.drawText("No Expenses", centerX, centerY, centerSubTextPaint)
            return
        }

        var startAngle = -90f
        for (item in expenseItems) {
            val sweepAngle = (item.percentage / 100f) * 360f
            if (sweepAngle <= 0f) continue

            try {
                slicePaint.color = Color.parseColor(item.colorHex)
            } catch (_: Exception) {
                slicePaint.color = 0xFF2563EB.toInt()
            }

            // Small gap between slices if multiple categories
            val adjustedSweep = if (expenseItems.size > 1 && sweepAngle > 3f) sweepAngle - 1.5f else sweepAngle
            canvas.drawArc(oval, startAngle, adjustedSweep, false, slicePaint)
            startAngle += sweepAngle
        }

        // Center text: Total Expenses and formatted amount
        centerSubTextPaint.textSize = size * 0.065f
        canvas.drawText("TOTAL EXPENSE", centerX, centerY - size * 0.04f, centerSubTextPaint)

        centerTextPaint.textSize = size * 0.09f
        canvas.drawText(centerTotalText, centerX, centerY + size * 0.06f, centerTextPaint)
    }
}
