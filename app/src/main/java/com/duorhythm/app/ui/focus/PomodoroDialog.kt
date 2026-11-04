package com.duorhythm.app.ui.focus

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class PomodoroDialog() : DialogFragment() {

    private var explicitContext: Context? = null

    constructor(context: Context) : this() {
        this.explicitContext = context
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = context ?: explicitContext ?: requireContext()
        return com.duorhythm.app.ui.tools.PomodoroDialog(ctx).createDialog()
    }

    fun show() {
        explicitContext?.let { ctx ->
            com.duorhythm.app.ui.tools.PomodoroDialog(ctx).show()
        }
    }
}
