package com.duorhythm.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class SmoothGapCollapseAnimator : DefaultItemAnimator() {

    init {
        removeDuration = 220L
        moveDuration = 280L
        addDuration = 220L
        changeDuration = 180L
        supportsChangeAnimations = false
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView
        view.animate().cancel()
        dispatchRemoveStarting(holder)

        val slideOutX = (view.width.takeIf { it > 0 } ?: 320) * 0.25f
        view.animate()
            .alpha(0f)
            .translationX(slideOutX)
            .scaleY(0.90f)
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

        return true
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

        return true
    }
}
