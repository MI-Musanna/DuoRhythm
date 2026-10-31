package com.duorhythm.app.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.RecyclerView

object KeyboardUtils {
    fun hideKeyboard(activity: Activity?) {
        if (activity == null) return
        val view = activity.currentFocus ?: View(activity)
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    fun attachScrollDismiss(recyclerView: RecyclerView, activity: Activity?) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard(activity)
                }
            }
        })
    }
}
