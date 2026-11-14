package com.duorhythm.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class WormDotsIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val dotRadius = 3.5f * density
    private val dotDiameter = dotRadius * 2f
    private val dotSpacing = 8f * density

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D94A3B8")
        style = Paint.Style.FILL
    }

    private val activeWormPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    private val wormRect = RectF()
    private var dotCount: Int = 0
    private var currentPosition: Int = 0
    private var currentOffset: Float = 0f

    private var attachedPager: ViewPager2? = null
    private var observedAdapter: RecyclerView.Adapter<*>? = null
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private var adapterObserver: RecyclerView.AdapterDataObserver? = null

    fun attachToViewPager2(viewPager: ViewPager2) {
        detach()
        attachedPager = viewPager
        syncCountFromAdapter(viewPager)

        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                currentPosition = position
                currentOffset = positionOffset.coerceIn(0f, 1f)
                invalidate()
            }

            override fun onPageSelected(position: Int) {
                currentPosition = position
                currentOffset = 0f
                invalidate()
            }
        }
        pageCallback = callback
        try {
            viewPager.registerOnPageChangeCallback(callback)
        } catch (_: Exception) {}

        viewPager.adapter?.let { adapter ->
            observedAdapter = adapter
            val observer = object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() = syncCountFromAdapter(viewPager)
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = syncCountFromAdapter(viewPager)
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = syncCountFromAdapter(viewPager)
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = syncCountFromAdapter(viewPager)
                override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = syncCountFromAdapter(viewPager)
            }
            adapterObserver = observer
            try {
                adapter.registerAdapterDataObserver(observer)
            } catch (_: Exception) {}
        }
    }

    private fun syncCountFromAdapter(viewPager: ViewPager2) {
        val count = viewPager.adapter?.itemCount ?: 0
        setDotCount(count)
    }

    fun setDotCount(count: Int) {
        dotCount = count.coerceAtLeast(0)
        if (currentPosition >= dotCount) {
            currentPosition = (dotCount - 1).coerceAtLeast(0)
            currentOffset = 0f
        }
        visibility = if (dotCount > 1) VISIBLE else GONE
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (dotCount > 0) {
            ((dotCount * dotDiameter) + ((dotCount - 1) * dotSpacing) + paddingLeft + paddingRight).toInt()
        } else {
            0
        }
        val desiredHeight = (dotDiameter + (8f * density) + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dotCount <= 1) return

        val totalDotsWidth = (dotCount * dotDiameter) + ((dotCount - 1) * dotSpacing)
        val startX = (width - totalDotsWidth) / 2f
        val centerY = height / 2f

        for (i in 0 until dotCount) {
            val cx = startX + dotRadius + i * (dotDiameter + dotSpacing)
            canvas.drawCircle(cx, centerY, dotRadius, inactivePaint)
        }

        val step = dotDiameter + dotSpacing
        val baseCenter = startX + dotRadius + (currentPosition * step)

        // Elastic worm stretch: leading head stretches toward next dot, trailing tail snaps smoothly
        val headProgress = (currentOffset * 2f).coerceIn(0f, 1f)
        val tailProgress = ((currentOffset - 0.5f) * 2f).coerceIn(0f, 1f)

        val leftEdge = (baseCenter - dotRadius) + (tailProgress * step)
        val rightEdge = (baseCenter + dotRadius) + (headProgress * step)

        wormRect.set(
            leftEdge,
            centerY - dotRadius,
            rightEdge,
            centerY + dotRadius
        )
        canvas.drawRoundRect(wormRect, dotRadius, dotRadius, activeWormPaint)
    }

    fun detach() {
        try {
            pageCallback?.let { attachedPager?.unregisterOnPageChangeCallback(it) }
        } catch (_: Exception) {}
        try {
            adapterObserver?.let { observedAdapter?.unregisterAdapterDataObserver(it) }
        } catch (_: Exception) {}
        pageCallback = null
        adapterObserver = null
        observedAdapter = null
        attachedPager = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detach()
    }
}
