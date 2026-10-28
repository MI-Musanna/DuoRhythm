package com.duorhythm.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var sourceBitmap: Bitmap? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val focusX = detector.focusX
            val focusY = detector.focusY

            val currentValues = FloatArray(9)
            transformMatrix.getValues(currentValues)
            val currentScale = currentValues[Matrix.MSCALE_X]

            // Bound zoom between 0.5x and 8.0x
            val targetScale = currentScale * scaleFactor
            if (targetScale in 0.5f..8.0f) {
                transformMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
                imageMatrix = transformMatrix
                invalidate()
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        this.sourceBitmap = bitmap
        setImageBitmap(bitmap)
        post {
            initMatrix()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initMatrix()
    }

    private fun initMatrix() {
        val bmp = sourceBitmap ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        transformMatrix.reset()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        // Scale to fill at least 80% of view
        val scale = max(viewW * 0.85f / bmpW, viewH * 0.85f / bmpH)
        transformMatrix.postScale(scale, scale)

        // Center bitmap
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f
        transformMatrix.postTranslate(dx, dy)

        imageMatrix = transformMatrix
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    transformMatrix.postTranslate(dx, dy)
                    imageMatrix = transformMatrix
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    /**
     * Extracts and crops the area bounded by [cropBounds] into an anti-aliased 512x512 circular Bitmap.
     */
    fun cropCircular(cropBounds: RectF, targetSize: Int = 512): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (bmp.isRecycled) return null

        val resultBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // Setup anti-aliased circular clip
        val clipPath = Path().apply {
            addCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Invert view matrix to map cropBounds back to source bitmap coordinates
        transformMatrix.invert(inverseMatrix)

        val circleCenter = floatArrayOf(cropBounds.centerX(), cropBounds.centerY())
        inverseMatrix.mapPoints(circleCenter)

        val radiusPt = floatArrayOf(cropBounds.right, cropBounds.centerY())
        inverseMatrix.mapPoints(radiusPt)
        val mappedRadius = hypot(radiusPt[0] - circleCenter[0], radiusPt[1] - circleCenter[1])

        // Build mapping from mapped source circle to (targetSize/2, targetSize/2, targetSize/2)
        val renderMatrix = Matrix()
        val scale = (targetSize / 2f) / max(1f, mappedRadius)
        renderMatrix.postTranslate(-circleCenter[0], -circleCenter[1])
        renderMatrix.postScale(scale, scale)
        renderMatrix.postTranslate(targetSize / 2f, targetSize / 2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, renderMatrix, paint)

        return resultBitmap
    }

    fun resetZoom() {
        initMatrix()
    }
}
