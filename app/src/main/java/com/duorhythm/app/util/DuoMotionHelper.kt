package com.duorhythm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
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
     * Fixed-Viewport Snapshot Circular Reveal (for MainActivity.mainViewPager inside CoordinatorLayout):
     * Captures a pixel-accurate bitmap of the outgoing mode at exact viewport bounds (w x h),
     * places it directly behind `viewport` so height changes in inner RecyclerViews NEVER cause
     * jitter or blank frames, and expands a clean Circular Reveal symmetrically in both directions.
     */
    fun triggerViewportCircularReveal(viewport: View?, cxRatio: Float = 0.84f, cyDp: Float = 42f) {
        if (viewport == null || !viewport.isAttachedToWindow) return
        val parent = viewport.parent as? ViewGroup ?: return
        val w = viewport.width
        val h = viewport.height
        if (w <= 0 || h <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        try {
            val snapshotBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(snapshotBitmap)
            viewport.draw(canvas)

            val snapshotView = ImageView(viewport.context).apply {
                layoutParams = ViewGroup.LayoutParams(w, h)
                x = viewport.x
                y = viewport.y
                translationZ = viewport.translationZ
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(snapshotBitmap)
                isClickable = false
                isFocusable = false
            }

            val viewportIndex = parent.indexOfChild(viewport).coerceAtLeast(0)
            parent.addView(snapshotView, viewportIndex)

            val density = viewport.resources.displayMetrics.density
            val cx = (w * cxRatio).toInt().coerceIn(0, w)
            val cy = (cyDp * density).toInt().coerceIn(0, h)
            val finalRadius = hypot(w.toDouble(), h.toDouble()).toFloat()

            viewport.post {
                if (!viewport.isAttachedToWindow) {
                    parent.removeView(snapshotView)
                    if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                    return@post
                }
                try {
                    val reveal = ViewAnimationUtils.createCircularReveal(viewport, cx, cy, 0f, finalRadius).apply {
                        duration = 340L
                        interpolator = FastOutSlowInInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            private var cleaned = false
                            private fun cleanup() {
                                if (cleaned) return
                                cleaned = true
                                parent.removeView(snapshotView)
                                if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                            }
                            override fun onAnimationEnd(animation: Animator) = cleanup()
                            override fun onAnimationCancel(animation: Animator) = cleanup()
                        })
                    }
                    reveal.start()
                } catch (_: Exception) {
                    parent.removeView(snapshotView)
                    if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Snapshot-Backed Circular Reveal:
     * Retained for fragment-level containers and audit backwards-compatibility.
     */
    fun triggerSnapshotCircularReveal(container: ViewGroup?, anchorView: View? = null) {
        if (container == null || !container.isAttachedToWindow) return
        val parent = container.parent as? FrameLayout ?: return
        val w = container.width
        val h = container.height
        if (w <= 0 || h <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

        try {
            val snapshotBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(snapshotBitmap)
            container.draw(canvas)

            val snapshotView = ImageView(container.context).apply {
                layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = container.left
                    topMargin = container.top
                }
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(snapshotBitmap)
                isClickable = false
                isFocusable = false
            }

            val containerIndex = parent.indexOfChild(container).coerceAtLeast(0)
            parent.addView(snapshotView, containerIndex)

            val cx: Int
            val cy: Int
            if (anchorView != null && anchorView.isAttachedToWindow && anchorView.width > 0) {
                val anchorLoc = IntArray(2)
                val containerLoc = IntArray(2)
                anchorView.getLocationInWindow(anchorLoc)
                container.getLocationInWindow(containerLoc)
                cx = (anchorLoc[0] - containerLoc[0] + anchorView.width / 2).coerceIn(0, w)
                cy = (anchorLoc[1] - containerLoc[1] + anchorView.height / 2).coerceIn(0, h)
            } else {
                cx = (w * 0.84f).toInt()
                cy = (h * 0.14f).toInt()
            }

            val finalRadius = hypot(w.toDouble(), h.toDouble()).toFloat()

            container.post {
                if (!container.isAttachedToWindow) {
                    parent.removeView(snapshotView)
                    if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                    return@post
                }
                try {
                    val reveal = ViewAnimationUtils.createCircularReveal(container, cx, cy, 0f, finalRadius).apply {
                        duration = 340L
                        interpolator = FastOutSlowInInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            private var cleaned = false
                            private fun cleanup() {
                                if (cleaned) return
                                cleaned = true
                                parent.removeView(snapshotView)
                                if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                            }
                            override fun onAnimationEnd(animation: Animator) = cleanup()
                            override fun onAnimationCancel(animation: Animator) = cleanup()
                        })
                    }
                    reveal.start()
                } catch (_: Exception) {
                    parent.removeView(snapshotView)
                    if (!snapshotBitmap.isRecycled) snapshotBitmap.recycle()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Preserved fallback workspace switch with smooth bounds and fade (used by audit18).
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
