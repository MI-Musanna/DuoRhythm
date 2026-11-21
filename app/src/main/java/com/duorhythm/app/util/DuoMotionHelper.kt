package com.duorhythm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

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
     * Smooth, symmetric bidirectional workspace transition (Student <-> Work):
     * 1. Morphs segmented pill & layout bounds via AutoTransition (200ms FastOutSlowIn).
     * 2. Crossfades and glides the incoming content container (alpha 0.78 -> 1.0, translationY 6dp -> 0)
     *    without any jarring full-screen color flash.
     */
    fun animateWorkspaceSwitch(container: ViewGroup?, incomingContent: View? = null) {
        if (container != null && container.isAttachedToWindow) {
            try {
                val transition = AutoTransition().apply {
                    duration = 200L
                    interpolator = FastOutSlowInInterpolator()
                }
                TransitionManager.beginDelayedTransition(container, transition)
            } catch (_: Exception) {}
        }
        if (incomingContent != null) {
            val d = incomingContent.resources.displayMetrics.density
            incomingContent.animate().cancel()
            incomingContent.alpha = 0.78f
            incomingContent.translationY = 6f * d
            incomingContent.animate()
                .alpha(1.0f)
                .translationY(0f)
                .setDuration(190L)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
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
