package com.duorhythm.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.CycleInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

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
    private var isInstalled = false

    @JvmStatic
    fun installGlobalMotionEngine(application: Application) {
        if (isInstalled) return
        isInstalled = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val rootView = activity.window?.decorView ?: return
                applySpringRecursively(rootView)

                rootView.viewTreeObserver.addOnGlobalLayoutListener(
                    ViewTreeObserver.OnGlobalLayoutListener {
                        applySpringRecursively(rootView)
                    }
                )

                // Catch EVERY Fragment, BottomSheetDialogFragment, and DialogFragment window!
                if (activity is FragmentActivity) {
                    activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentViewCreated(
                                fm: FragmentManager,
                                f: Fragment,
                                v: View,
                                savedInstanceState: Bundle?
                            ) {
                                applySpringRecursively(v)
                                if (f is DialogFragment) {
                                    f.dialog?.window?.decorView?.let { dialogDecor ->
                                        applySpringRecursively(dialogDecor)
                                        dialogDecor.viewTreeObserver.addOnGlobalLayoutListener {
                                            applySpringRecursively(dialogDecor)
                                        }
                                    }
                                }
                            }

                            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                                f.view?.let { applySpringRecursively(it) }
                                if (f is DialogFragment) {
                                    f.dialog?.window?.decorView?.let { applySpringRecursively(it) }
                                }
                            }
                        },
                        true
                    )
                }
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

    @JvmStatic
    fun attachToDialog(dialog: Dialog?) {
        dialog?.window?.decorView?.let { decor ->
            applySpringRecursively(decor)
            decor.viewTreeObserver.addOnGlobalLayoutListener {
                applySpringRecursively(decor)
            }
        }
    }

    @JvmStatic
    fun applySpringToAllButtons(root: View?) {
        applySpringRecursively(root)
    }

    @JvmStatic
    fun applySpringRecursively(view: View?) {
        if (view == null) return

        val isInteractiveControl = (
            view is MaterialButton ||
            (view is MaterialCardView && view.isClickable) ||
            (view.isClickable && view !is EditText && view !is ViewGroup) ||
            (view is ViewGroup && view.isClickable && view.childCount <= 4)
        )

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
