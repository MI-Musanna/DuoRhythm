package com.duorhythm.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import kotlin.math.hypot

/**
 * Tactile Micro-Interaction & Spatial Transition Engine:
 * - 0ms ACTION_DOWN press scale feedback (without overriding OnClickListener)
 * - Interruptible Circular Reveal for Student <-> Work workspace mode transitions
 * - Task completion pop (0.85x -> 1.15x -> 1.0x)
 * - Damped horizontal validation shake (±8dp, 240ms)
 * - Dark-mode photo glare dimming (85% opacity) & mode-matched accent glow
 */
object DuoMotionHelper {

    fun attachZeroMsPressFeedback(vararg views: View?) {
        views.filterNotNull().forEach { v ->
            v.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        target.animate().cancel()
                        target.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(60L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target.animate().cancel()
                        target.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150L)
                            .setInterpolator(OvershootInterpolator(1.5f))
                            .start()
                    }
                }
                false // Never consume click event
            }
        }
    }

    fun triggerCircularReveal(targetView: View?, anchorView: View? = null) {
        if (targetView == null || !targetView.isAttachedToWindow) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val cx = if (anchorView != null && anchorView.isAttachedToWindow) {
                    val anchorLoc = IntArray(2)
                    val targetLoc = IntArray(2)
                    anchorView.getLocationInWindow(anchorLoc)
                    targetView.getLocationInWindow(targetLoc)
                    (anchorLoc[0] - targetLoc[0] + anchorView.width / 2).coerceIn(0, targetView.width)
                } else {
                    (targetView.width * 0.82f).toInt()
                }
                val cy = if (anchorView != null && anchorView.isAttachedToWindow) {
                    val anchorLoc = IntArray(2)
                    val targetLoc = IntArray(2)
                    anchorView.getLocationInWindow(anchorLoc)
                    targetView.getLocationInWindow(targetLoc)
                    (anchorLoc[1] - targetLoc[1] + anchorView.height / 2).coerceIn(0, targetView.height)
                } else {
                    (targetView.height * 0.12f).toInt()
                }
                val finalRadius = hypot(targetView.width.toDouble(), targetView.height.toDouble()).toFloat()
                if (finalRadius > 0f) {
                    val anim = ViewAnimationUtils.createCircularReveal(targetView, cx, cy, 24f, finalRadius)
                    anim.duration = 280L
                    anim.interpolator = DecelerateInterpolator(1.4f)
                    anim.start()
                }
            } catch (_: Exception) {}
        }
    }

    fun playTaskCompletionPop(view: View?) {
        if (view == null) return
        view.animate().cancel()
        val downX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.85f).setDuration(70L)
        val downY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.85f).setDuration(70L)
        val upX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.85f, 1.15f, 1.0f).setDuration(220L)
        val upY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.85f, 1.15f, 1.0f).setDuration(220L)
        upX.interpolator = OvershootInterpolator(2.0f)
        upY.interpolator = OvershootInterpolator(2.0f)

        AnimatorSet().apply {
            play(downX).with(downY)
            play(upX).with(upY).after(downX)
            start()
        }
    }

    fun playValidationShake(view: View?) {
        if (view == null) return
        val d = view.resources.displayMetrics.density
        val delta = 8f * d
        view.animate().cancel()
        ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f, -delta, delta, -delta * 0.65f, delta * 0.65f, -delta * 0.25f, 0f
        ).apply {
            duration = 250L
            start()
        }
    }

    fun applyDarkPhotoDimming(imageView: ImageView?, hasPhoto: Boolean) {
        imageView?.alpha = if (hasPhoto) 0.85f else 1.0f
    }

    fun applyModeAccentGlow(view: View?, isWorkMode: Boolean) {
        if (view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val glowColor = if (isWorkMode) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
            view.outlineAmbientShadowColor = glowColor
            view.outlineSpotShadowColor = glowColor
        }
        view.backgroundTintList = view.backgroundTintList ?: ColorStateList.valueOf(
            Color.parseColor(if (isWorkMode) "#10B981" else "#38BDF8")
        )
    }
}
