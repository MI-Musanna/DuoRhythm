package com.duorhythm.app.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.CycleInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CompoundButton
import android.widget.EditText
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

fun View.addSpringPressEffect(
    scaleFactor: Float = 0.94f,
    downDuration: Long = 65L,
    upDuration: Long = 175L,
    onClick: (() -> Unit)? = null
) = ViewAnimationExtensions.addSpringPressEffect(this, scaleFactor, downDuration, upDuration, onClick)

fun View.applySpringToAllButtons() = ViewAnimationExtensions.applySpringToAllButtons(this)

fun View.addLongPressLiftEffect() = ViewAnimationExtensions.addLongPressLiftEffect(this)

fun CompoundButton.addSpringSwitchEffect() = ViewAnimationExtensions.addSpringSwitchEffect(this)

fun View.shakeError(
    amplitude: Float = 22f,
    cycles: Float = 4f,
    duration: Long = 420L,
    onComplete: (() -> Unit)? = null
) = ViewAnimationExtensions.shakeError(this, amplitude, cycles, duration, onComplete)

object ViewAnimationExtensions {

    private const val TAG_SPRING_ATTACHED = 0x7F0A8888
    private const val TAG_LIFT_ATTACHED = 0x7F0A8889
    private const val TAG_SWITCH_ATTACHED = 0x7F0A8890
    private const val TAG_ANIMATOR_ATTACHED = 0x7F0A8891
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
    fun applySpringToAllButtons(view: View?) {
        applySpringRecursively(view)
    }

    @JvmStatic
    fun applySpringRecursively(view: View?) {
        if (view == null) return

        // Phase 2: Smooth Gap-Collapse Animator on every RecyclerView
        if (view is RecyclerView && view.getTag(TAG_ANIMATOR_ATTACHED) != true) {
            view.setTag(TAG_ANIMATOR_ATTACHED, true)
            if (view.itemAnimator == null || view.itemAnimator is androidx.recyclerview.widget.DefaultItemAnimator) {
                view.itemAnimator = SmoothGapCollapseAnimator()
            }
        }

        // Phase 2: Spring-Stretch Bounce on all Toggle Switches
        if (view is CompoundButton) {
            addSpringSwitchEffect(view)
        }

        // Phase 2: Long-Press Sink-then-Lift on interactive MaterialCardViews
        if (view is MaterialCardView && (view.isClickable || view.isLongClickable)) {
            addLongPressLiftEffect(view)
        }

        // Phase 1: Tactile Sink & Spring Press on buttons, pills, and icons
        val isInteractiveControl = (
            view is MaterialButton ||
            (view is MaterialCardView && view.isClickable) ||
            (view.isClickable && view !is EditText && view !is ViewGroup && view !is CompoundButton) ||
            (view is ViewGroup && view.isClickable && view.childCount <= 4)
        )

        if (isInteractiveControl && view !is MaterialCardView) {
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
    @SuppressLint("ClickableViewAccessibility")
    fun addLongPressLiftEffect(view: View) {
        if (view.getTag(TAG_LIFT_ATTACHED) == true) return
        view.setTag(TAG_LIFT_ATTACHED, true)

        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var isLifted = false

        val liftRunnable = Runnable {
            isLifted = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.animate()
                .scaleX(1.035f)
                .scaleY(1.035f)
                .translationZ(14f * view.resources.displayMetrics.density)
                .setDuration(190L)
                .setInterpolator(OvershootInterpolator(1.8f))
                .start()
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isLifted = false
                    v.animate()
                        .scaleX(0.965f)
                        .scaleY(0.965f)
                        .setDuration(75L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    handler.postDelayed(liftRunnable, 360L)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLifted && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                        handler.removeCallbacks(liftRunnable)
                        v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .translationZ(0f)
                            .setDuration(150L)
                            .start()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(liftRunnable)
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(200L)
                        .setInterpolator(OvershootInterpolator(2.1f))
                        .start()
                }
            }
            false
        }
    }

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun addSpringSwitchEffect(view: CompoundButton) {
        if (view.getTag(TAG_SWITCH_ATTACHED) == true) return
        view.setTag(TAG_SWITCH_ATTACHED, true)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(1.14f)
                        .scaleY(0.88f)
                        .setDuration(75L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(230L)
                        .setInterpolator(OvershootInterpolator(2.8f))
                        .start()
                }
            }
            false
        }
    }

    /**
     * Smoothly shrinks and cross-fades a large page header into a pinned compact toolbar title on scroll.
     */
    @JvmStatic
    fun attachCollapsingLargeTitle(
        scrollView: NestedScrollView?,
        largeTitle: View?,
        compactToolbarTitle: View?,
        collapseThresholdDp: Float = 64f
    ) {
        if (scrollView == null || largeTitle == null) return
        val thresholdPx = collapseThresholdDp * scrollView.resources.displayMetrics.density
        compactToolbarTitle?.alpha = 0f

        scrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                val progress = (scrollY / thresholdPx).coerceIn(0f, 1f)
                val scale = 1f - (0.28f * progress)
                largeTitle.pivotX = 0f
                largeTitle.pivotY = largeTitle.height / 2f
                largeTitle.scaleX = scale
                largeTitle.scaleY = scale
                largeTitle.alpha = (1f - (progress * 1.15f)).coerceIn(0f, 1f)

                compactToolbarTitle?.let { toolbarTitle ->
                    toolbarTitle.alpha = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    toolbarTitle.translationY = (1f - progress) * (8f * scrollView.resources.displayMetrics.density)
                }
            }
        )
    }

    /**
     * Scroll-driven collapsing large header for RecyclerView screens (Portal, Tasks, Wallet, Details).
     */
    @JvmStatic
    fun attachCollapsingLargeTitle(
        recyclerView: RecyclerView?,
        largeHeaderContainer: View?,
        compactToolbarTitle: View? = null,
        collapseThresholdDp: Float = 72f
    ) {
        if (recyclerView == null || largeHeaderContainer == null) return
        val thresholdPx = collapseThresholdDp * recyclerView.resources.displayMetrics.density
        var totalScrollY = 0

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                totalScrollY = (totalScrollY + dy).coerceAtLeast(0)
                val progress = (totalScrollY / thresholdPx).coerceIn(0f, 1f)
                val scale = 1f - (0.22f * progress)

                largeHeaderContainer.pivotX = 0f
                largeHeaderContainer.pivotY = largeHeaderContainer.height / 2f
                largeHeaderContainer.scaleX = scale
                largeHeaderContainer.scaleY = scale
                largeHeaderContainer.alpha = (1f - (progress * 0.85f)).coerceIn(0.15f, 1f)
                largeHeaderContainer.translationY = -progress * (10f * rv.resources.displayMetrics.density)

                compactToolbarTitle?.let { toolbarTitle ->
                    toolbarTitle.alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                }
            }
        })
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
