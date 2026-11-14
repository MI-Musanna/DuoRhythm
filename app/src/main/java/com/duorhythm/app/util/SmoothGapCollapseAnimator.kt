package com.duorhythm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Slides deleted rows out cleanly while smoothly collapsing trailing rows upward at 60/120fps.
 */
class SmoothGapCollapseAnimator : DefaultItemAnimator() {

    init {
        removeDuration = 210L
        moveDuration = 260L
        addDuration = 220L
        changeDuration = 200L
        supportsChangeAnimations = false
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        view.animate().cancel()
        dispatchRemoveStarting(holder)

        val slideDistance = (view.width.takeIf { it > 0 } ?: 300) * 0.22f
        view.animate()
            .alpha(0f)
            .translationX(slideDistance)
            .scaleY(0.92f)
            .setDuration(removeDuration)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    view.alpha = 1f
                    view.translationX = 0f
                    view.scaleY = 1f
                    dispatchRemoveFinished(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.alpha = 1f
                    view.translationX = 0f
                    view.scaleY = 1f
                    dispatchRemoveFinished(holder)
                }
            })
            .start()

        return false
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        val view = holder.itemView
        val deltaX = toX - fromX
        val deltaY = toY - fromY
        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder)
            return false
        }
        view.translationX = -deltaX.toFloat()
        view.translationY = -deltaY.toFloat()
        dispatchMoveStarting(holder)

        view.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(moveDuration)
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.animate().setListener(null)
                    view.translationX = 0f
                    view.translationY = 0f
                    dispatchMoveFinished(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.translationX = 0f
                    view.translationY = 0f
                    dispatchMoveFinished(holder)
                }
            })
            .start()

        return false
    }
}
