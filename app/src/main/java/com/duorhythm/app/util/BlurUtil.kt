package com.duorhythm.app.util

import android.view.ViewGroup
import eightbitlab.com.blurview.BlurView

object BlurUtil {

    /**
     * Safely disables real-time background capture in BlurView widgets to prevent
     * recursive hardware canvas redraw crashes, OOM errors, and device-dependent SIGSEGV.
     * All views render their pre-existing glassmorphic shape drawables cleanly.
     */
    fun setupBlur(blurView: BlurView?, rootView: ViewGroup?, radius: Float = 16f) {
        if (blurView == null) return
        try {
            blurView.setBlurEnabled(false)
        } catch (_: Throwable) {}
    }
}
