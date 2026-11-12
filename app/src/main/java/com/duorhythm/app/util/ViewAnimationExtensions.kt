package com.duorhythm.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.CycleInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Global Window-Level Phase 1 Motion Engine: Spring touch physics & auth error head-shake
 */
fun View.addSpringPressEffect(
    scaleFactor: Float = 0.94f,
    downDuration: Long = 65L,
    upDuration: Long = 175L,
    onClick: (() -> Unit)? = null
) = ViewAnimationExtensions.addSpringPressEffect(this, scaleFactor, downDuration, upDuration, onClick)

fun View.shakeError(
    amplitude: Float = 22f,
    cycles: Float = 4f,
    duration: Long = 420L,
    onComplete: (() -> Unit)? = null
) = ViewAnimationExtensions.shakeError(this, amplitude, cycles, duration, onComplete)

object ViewAnimationExtensions {

    private const val TAG_SPRING_ATTACHED = 0x7F0A8888

    /**
     * Registers a global ActivityLifecycleCallback that attaches tactile Sink & Spring physics
     * to EVERY clickable button, pill, icon, and card across ALL Activities, Fragments, and Dialogs!
     */
    @JvmStatic
    fun installGlobalMotionEngine(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val rootView = activity.window?.decorView ?: return
                applySpringRecursively(rootView)

                // Re-scan dynamically inflated dialogs, bottom sheets, and RecyclerView items on layout changes
                rootView.viewTreeObserver.addOnGlobalLayoutListener(
                    ViewTreeObserver.OnGlobalLayoutListener {
                        applySpringRecursively(rootView)
                    }
                )
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                activity.window?.decorView?.let { applySpringRecursively(it) }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Recursively attaches tactile Sink & Spring press physics to all controls inside a view tree
     */
    @JvmStatic
    fun applySpringToAllButtons(root: View?) {
        applySpringRecursively(root)
    }

    /**
     * Recursively attaches Sink & Spring touch physics to every clickable control in the window
     */
    @JvmStatic
    fun applySpringRecursively(view: View?) {
        if (view == null) return

        val isInteractiveControl = (view is MaterialButton ||
                view is MaterialCardView ||
                (view.isClickable && view !is EditText && view !is ViewGroup))

        if (isInteractiveControl) {
            addSpringPressEffect(view)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applySpringRecursively(view.getChildAt(i))
            }
        }
    }

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun addSpringPressEffect(
        view: View,
        scaleFactor: Float = 0.94f,
        downDuration: Long = 65L,
        upDuration: Long = 175L,
        onClick: (() -> Unit)? = null
    ) {
        if (view.getTag(TAG_SPRING_ATTACHED) == true) return
        view.setTag(TAG_SPRING_ATTACHED, true)

        if (onClick != null) {
            view.setOnClickListener { onClick() }
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scaleFactor)
                        .scaleY(scaleFactor)
                        .setDuration(downDuration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(upDuration)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
            }
            false
        }
    }

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
