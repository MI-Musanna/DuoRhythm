package com.duorhythm.app.util

import android.annotation.SuppressLint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.button.MaterialButton

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

    /**
     * Recursively attaches tactile Sink & Spring press physics to every MaterialButton inside a view tree
     */
    @JvmStatic
    fun applySpringToAllButtons(root: View?) {
        if (root == null) return
        if (root is MaterialButton) {
            addSpringPressEffect(root)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applySpringToAllButtons(root.getChildAt(i))
            }
        }
    }

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun addSpringPressEffect(
        view: View,
        scaleFactor: Float = 0.94f,
        downDuration: Long = 70L,
        upDuration: Long = 180L,
        onClick: (() -> Unit)? = null
    ) {
        if (view.getTag(0x7f0a9999) == true) return
        view.setTag(0x7f0a9999, true)

        if (onClick != null) {
            view.setOnClickListener { onClick() }
        }

        val rect = Rect()
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.getHitRect(rect)
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
            // Return false so existing OnClickListeners and Material ripples continue working seamlessly!
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
