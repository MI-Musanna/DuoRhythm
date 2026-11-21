package com.duorhythm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import kotlin.math.hypot

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
                false
            }
        }
    }

    /**
     * Visible Circular Reveal wave across the workspace container using a transient mode-tinted
     * overlay so the wipe is clearly perceptible over the #0A0B0E carbon canvas.
     */
    fun triggerCircularReveal(targetView: View?, isWorkMode: Boolean = false, anchorView: View? = null) {
        if (targetView == null || !targetView.isAttachedToWindow) return
        val parent = targetView.parent as? ViewGroup ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && targetView.width > 0 && targetView.height > 0) {
            try {
                val waveOverlay = View(targetView.context).apply {
                    layoutParams = ViewGroup.LayoutParams(targetView.width, targetView.height)
                    x = targetView.x
                    y = targetView.y
                    setBackgroundColor(
                        Color.parseColor(if (isWorkMode) "#2E10B981" else "#2E38BDF8")
                    )
                    elevation = 20f * resources.displayMetrics.density
                    isClickable = false
                    isFocusable = false
                }
                parent.addView(waveOverlay)

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
                    (targetView.height * 0.14f).toInt()
                }

                val finalRadius = hypot(targetView.width.toDouble(), targetView.height.toDouble()).toFloat()
                val reveal = ViewAnimationUtils.createCircularReveal(waveOverlay, cx, cy, 16f, finalRadius).apply {
                    duration = 320L
                    interpolator = DecelerateInterpolator(1.3f)
                }
                val fadeOut = ObjectAnimator.ofFloat(waveOverlay, View.ALPHA, 1f, 0f).apply {
                    startDelay = 140L
                    duration = 220L
                }
                AnimatorSet().apply {
                    playTogether(reveal, fadeOut)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            parent.removeView(waveOverlay)
                        }
                    })
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    fun playTaskCompletionPop(view: View?, onPopPeak: (() -> Unit)? = null) {
        if (view == null) {
            onPopPeak?.invoke()
            return
        }
        view.animate().cancel()
        val downX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.90f).setDuration(75L)
        val downY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.90f).setDuration(75L)
        val upX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.90f, 1.06f, 1.0f).setDuration(210L)
        val upY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.90f, 1.06f, 1.0f).setDuration(210L)
        upX.interpolator = OvershootInterpolator(2.2f)
        upY.interpolator = OvershootInterpolator(2.2f)

        AnimatorSet().apply {
            play(downX).with(downY)
            play(upX).with(upY).after(downX)
            if (onPopPeak != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onPopPeak.invoke()
                    }
                })
            }
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

    /**
     * Never overwrite backgroundTintList on a GradientDrawable-backed view (which locks the first color);
     * instead clear backgroundTintList and apply mode-matched ambient/spot outer glow.
     */
    fun applyModeAccentGlow(view: View?, isWorkMode: Boolean) {
        if (view == null) return
        view.backgroundTintList = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val glowColor = if (isWorkMode) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
            view.outlineAmbientShadowColor = glowColor
            view.outlineSpotShadowColor = glowColor
        }
    }
}
