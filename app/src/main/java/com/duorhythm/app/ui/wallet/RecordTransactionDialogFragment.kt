package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.AppDatabase
import database.dao.CategoryDao
import database.entities.CategoryEntity

class RecordTransactionDialogFragment : DialogFragment() {

    private var selectedCategoryName: String = ""
    private var masterCategoryList: List<CategoryEntity> = emptyList()
    private val categoryDao: CategoryDao by lazy {
        AppDatabase.getInstance(requireContext()).categoryDao
    }
    private var selectedType: TransactionType = TransactionType.INCOME

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val initialTypeName = arguments?.getString(ARG_INITIAL_TYPE) ?: "INCOME"
        selectedType = try {
            TransactionType.valueOf(initialTypeName.uppercase())
        } catch (_: Exception) {
            TransactionType.INCOME
        }

        // 1. Maintain an Unfiltered Master List & 2. Observe / Load Master Categories
        masterCategoryList = try {
            categoryDao.getAllCategories()
        } catch (_: Exception) {
            emptyList()
        }

        val addRecordDialog = AddRecordDialog(
            context = requireContext(),
            initialType = selectedType
        )
        return addRecordDialog.create()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val whiteColor = android.graphics.Color.parseColor("#F8FAFC")
            val hintColor = android.graphics.Color.parseColor("#94A3B8")
            view.findViewById<android.widget.EditText>(com.duorhythm.app.R.id.etTransactionTitle)?.apply {
                setTextColor(whiteColor)
                setHintTextColor(hintColor)
            }
            view.findViewById<android.widget.EditText>(com.duorhythm.app.R.id.etTxTitle)?.apply {
                setTextColor(whiteColor)
                setHintTextColor(hintColor)
            }
            view.findViewById<android.widget.EditText>(com.duorhythm.app.R.id.etTxAmount)?.apply {
                setTextColor(whiteColor)
                setHintTextColor(hintColor)
            }
            val strokeColorStateList = androidx.core.content.ContextCompat.getColorStateList(
                view.context,
                com.duorhythm.app.R.color.selector_box_stroke_cyan
            )
            strokeColorStateList?.let { csl ->
                view.findViewById<com.google.android.material.textfield.TextInputLayout>(
                    com.duorhythm.app.R.id.tilTransactionTitle
                )?.setBoxStrokeColorStateList(csl)
            }
        } catch (_: Exception) {}
    }

    private fun setupCategoryDropdownAdapter(categories: List<CategoryEntity>) {
        val adapter = CategoryDropdownAdapter(
            categories = categories,
            onSelect = { category ->
                // 1. Store chosen category
                selectedCategoryName = category.name
            },
            onEdit = { category ->
                showEditCategoryDialog(category)
            },
            onDelete = { category ->
                showDeleteCategoryConfirmDialog(category)
            }
        )
    }

    private fun showEditCategoryDialog(category: CategoryEntity) {
        // Dialog edit callback hook
    }

    private fun showDeleteCategoryConfirmDialog(category: CategoryEntity) {
        // Dialog delete confirmation callback hook
    }

    companion object {
        const val ARG_INITIAL_TYPE = "initial_type"

        fun newInstance(initialType: String = "INCOME"): RecordTransactionDialogFragment {
            return RecordTransactionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_TYPE, initialType)
                }
            }
        }
    }
}
