package com.duorhythm.app.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorSpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val spectrumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectorOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    private val selectorInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var hue: Float = 200f
        set(value) {
            field = value.coerceIn(0f, 360f)
            updateShader()
            invalidate()
        }

    var saturation: Float = 0.8f
        private set

    var value: Float = 0.9f
        private set

    private var selectorX = 0f
    private var selectorY = 0f
    private var onColorChangedListener: ((Int) -> Unit)? = null

    fun setOnColorChangedListener(listener: (Int) -> Unit) {
        this.onColorChangedListener = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
        selectorX = saturation * width
        selectorY = (1f - value) * height
    }

    private fun updateShader() {
        if (width <= 0 || height <= 0) return
        val pureColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val valShader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        val satShader = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, pureColor, Shader.TileMode.CLAMP)
        spectrumPaint.shader = ComposeShader(valShader, satShader, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = 24f
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, spectrumPaint)

        val activeColor = getCurrentColor()
        selectorInnerPaint.color = activeColor
        canvas.drawCircle(selectorX, selectorY, 18f, selectorOuterPaint)
        canvas.drawCircle(selectorX, selectorY, 14f, selectorInnerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                selectorX = event.x.coerceIn(0f, width.toFloat())
                selectorY = event.y.coerceIn(0f, height.toFloat())
                saturation = (selectorX / width.toFloat()).coerceIn(0f, 1f)
                value = (1f - (selectorY / height.toFloat())).coerceIn(0f, 1f)
                invalidate()
                onColorChangedListener?.invoke(getCurrentColor())
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getCurrentColor(): Int {
        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        this.hue = hsv[0]
        this.saturation = hsv[1]
        this.value = hsv[2]
        if (width > 0 && height > 0) {
            selectorX = saturation * width
            selectorY = (1f - value) * height
            updateShader()
            invalidate()
        }
    }
}
