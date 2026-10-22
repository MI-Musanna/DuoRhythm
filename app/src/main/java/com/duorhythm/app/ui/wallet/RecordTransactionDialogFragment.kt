package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.AppDatabase
import database.dao.CategoryDao
import database.entities.CategoryEntity

class RecordTransactionDialogFragment : DialogFragment() {

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
