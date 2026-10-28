package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper

class ManageCategoriesDialog : DialogFragment() {

    private var onCategoriesChanged: (() -> Unit)? = null

    fun setOnCategoriesChangedListener(listener: () -> Unit) {
        this.onCategoriesChanged = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val dbHelper = DatabaseHelper.getInstance(ctx)
        return CategoryManagementDialog.create(ctx, dbHelper) {
            onCategoriesChanged?.invoke()
        }
    }

    companion object {
        const val TAG = "ManageCategoriesDialog"

        fun newInstance(onChanged: (() -> Unit)? = null): ManageCategoriesDialog {
            val dialog = ManageCategoriesDialog()
            dialog.onCategoriesChanged = onChanged
            return dialog
        }

        fun show(
            context: Context,
            dbHelper: DatabaseHelper = DatabaseHelper.getInstance(context),
            onChanged: (() -> Unit)? = null
        ) {
            CategoryManagementDialog.show(context, dbHelper, onChanged)
        }
    }
}
