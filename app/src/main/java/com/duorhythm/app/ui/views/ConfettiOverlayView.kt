package com.duorhythm.app.ui.views

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight, non-blocking Canvas confetti burst for financial wins and milestone completions.
 * Auto-detaches from the Activity decorView upon completion to guarantee zero memory leaks.
 */
class ConfettiOverlayView(context: Context) : View(context) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val size: Float,
        val color: Int,
        var rotation: Float,
        val vRot: Float,
        val isCircle: Boolean
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particles = mutableListOf<Particle>()
    private var animator: ValueAnimator? = null
    private var currentAlpha = 255

    private val palette = intArrayOf(
        Color.parseColor("#38BDF8"),
        Color.parseColor("#10B981"),
        Color.parseColor("#38BDF8"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#A855F7")
    )

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun startBurst(originX: Float, originY: Float) {
        particles.clear()
        val count = 46
        for (i in 0 until count) {
            val angle = Random.nextDouble(-Math.PI * 0.92, -Math.PI * 0.08)
            val speed = Random.nextFloat() * (11f * density) + (5.5f * density)
            particles.add(
                Particle(
                    x = originX + Random.nextFloat() * (24f * density) - (12f * density),
                    y = originY,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    size = Random.nextFloat() * (4.5f * density) + (4f * density),
                    color = palette[i % palette.size],
                    rotation = Random.nextFloat() * 360f,
                    vRot = Random.nextFloat() * 14f - 7f,
                    isCircle = (i % 3 == 0)
                )
            )
        }

        val gravity = 0.42f * density
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1350L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val frac = anim.animatedFraction
                currentAlpha = if (frac > 0.72f) {
                    (((1f - frac) / 0.28f) * 255f).toInt().coerceIn(0, 255)
                } else {
                    255
                }
                for (p in particles) {
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += gravity
                    p.vx *= 0.985f
                    p.rotation += p.vRot
                }
                invalidate()
                if (frac >= 1f) {
                    (parent as? ViewGroup)?.removeView(this@ConfettiOverlayView)
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            paint.color = p.color
            paint.alpha = currentAlpha
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.rotation)
            if (p.isCircle) {
                canvas.drawCircle(0f, 0f, p.size * 0.55f, paint)
            } else {
                canvas.drawRect(-p.size * 0.6f, -p.size * 0.35f, p.size * 0.6f, p.size * 0.35f, paint)
            }
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    companion object {
        private var lastBurstTime = 0L

        fun burst(activity: Activity?) {
            val now = System.currentTimeMillis()
            if (now - lastBurstTime < 400L) return
            lastBurstTime = now

            val decor = activity?.window?.decorView as? ViewGroup ?: return
            decor.post {
                for (i in decor.childCount - 1 downTo 0) {
                    val child = decor.getChildAt(i)
                    if (child is ConfettiOverlayView) {
                        decor.removeViewAt(i)
                    }
                }
                val overlay = ConfettiOverlayView(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    elevation = 40f * activity.resources.displayMetrics.density
                }
                decor.addView(overlay)
                val cx = (decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels) * 0.5f
                val cy = (decor.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels) * 0.42f
                overlay.startBurst(cx, cy)
            }
        }
    }
}
