package com.duorhythm.app.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Phase 1 Physics Engine: Spring touch feedbacks, error shakes, and transitions
 */
fun View.addSpringPressEffect(
    scaleFactor: Float = 0.94f,
    downDuration: Long = 70L,
    upDuration: Long = 180L,
    onClick: (() -> Unit)? = null
) = ViewAnimationExtensions.addSpringPressEffect(this, scaleFactor, downDuration, upDuration, onClick)

fun View.shakeError(
    amplitude: Float = 22f,
    cycles: Float = 4f,
    duration: Long = 420L,
    onComplete: (() -> Unit)? = null
) = ViewAnimationExtensions.shakeError(this, amplitude, cycles, duration, onComplete)

object ViewAnimationExtensions {

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun addSpringPressEffect(
        view: View,
        scaleFactor: Float = 0.94f,
        downDuration: Long = 70L,
        upDuration: Long = 180L,
        onClick: (() -> Unit)? = null
    ) {
        if (onClick != null) {
            view.setOnClickListener { onClick() }
        } else {
            view.isClickable = true
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleFactor)
                        .scaleY(scaleFactor)
                        .setDuration(downDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(upDuration)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(upDuration)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                }
            }
            false
        }
    }

    /**
     * Shakes a view horizontally to indicate validation/authentication error
     */
    @JvmStatic
    fun shakeError(
        view: View,
        amplitude: Float = 22f,
        cycles: Float = 4f,
        duration: Long = 420L,
        onComplete: (() -> Unit)? = null
    ) {
        view.animate()
            .translationX(amplitude)
            .setDuration(duration)
            .setInterpolator(CycleInterpolator(cycles))
            .withEndAction {
                view.translationX = 0f
                onComplete?.invoke()
            }
            .start()
    }
}
