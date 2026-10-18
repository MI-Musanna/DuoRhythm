package com.duorhythm.app.util

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils as AndroidAnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R

object AnimationUtils {

    private val fastOutSlowIn: Interpolator by lazy { FastOutSlowInInterpolator() }
    private val decelerate: Interpolator by lazy { DecelerateInterpolator(1.5f) }

    /**
     * Applies subtle tactile scale-down touch feedback on views/buttons.
     * On press: scale down to [scale] (default 0.96f).
     * On release/cancel: spring back to 1.0f.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachTactileFeedback(view: View, scale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(90)
                        .setInterpolator(fastOutSlowIn)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .setInterpolator(fastOutSlowIn)
                        .start()
                }
            }
            false // allow OnClickListener to continue receiving events
        }
    }

    fun attachTactileFeedback(vararg views: View) {
        views.forEach { attachTactileFeedback(it) }
    }

    /**
     * Staggered fade and slide-up animation for RecyclerView item view.
     * Alpha: 0 -> 1, translationY: 30dp -> 0dp, duration: 180ms.
     */
    fun animateItemEntry(itemView: View, position: Int, baseDuration: Long = 180L) {
        val density = itemView.resources.displayMetrics.density
        val startOffset = 30f * density

        itemView.alpha = 0f
        itemView.translationY = startOffset

        val delay = (position * 25L).coerceAtMost(200L)

        itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(baseDuration)
            .setStartDelay(delay)
            .setInterpolator(decelerate)
            .start()
    }

    /**
     * Applies standard layout animation for staggered RecyclerView entries.
     */
    fun applySlideUpLayoutAnimation(recyclerView: RecyclerView) {
        try {
            val anim = AndroidAnimationUtils.loadLayoutAnimation(
                recyclerView.context,
                R.anim.layout_animation_slide_up
            )
            recyclerView.layoutAnimation = anim
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Smooth accordion expand/collapse transition with AutoTransition.
     */
    fun beginAccordionTransition(container: ViewGroup, duration: Long = 200L) {
        try {
            val transition = AutoTransition().apply {
                this.duration = duration
                interpolator = fastOutSlowIn
            }
            TransitionManager.beginDelayedTransition(container, transition)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Rotates chevron 180 degrees smoothly for accordion status.
     */
    fun rotateChevron(chevronView: ImageView, isExpanded: Boolean, duration: Long = 200L) {
        chevronView.animate()
            .rotation(if (isExpanded) 180f else 0f)
            .setDuration(duration)
            .setInterpolator(fastOutSlowIn)
            .start()
    }

    /**
     * Attaches fluid scale and fade window animation to any dialog.
     */
    fun applyDialogAnimation(dialog: Dialog) {
        dialog.window?.setWindowAnimations(R.style.DialogAnimation)
    }

    /**
     * Crossfade in view smoothly.
     */
    fun crossfadeIn(view: View, duration: Long = 180L) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(fastOutSlowIn)
            .start()
    }
}
