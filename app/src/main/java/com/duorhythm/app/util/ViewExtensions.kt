package com.duorhythm.app.util

import android.view.View

/**
 * View extension for smooth crossfade transitions between visibility states.
 */
fun View.crossfade(show: Boolean, duration: Long = 150) {
    if (show) {
        alpha = 0f
        visibility = View.VISIBLE
        animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
    } else {
        animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction { visibility = View.GONE }
    }
}
