package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.DialogAddCategoryBinding
import com.duorhythm.app.ui.common.AppIconRegistry
import com.duorhythm.app.ui.common.ColorPickerHelper

class AddCategoryDialogFragment : DialogFragment() {

    private var _binding: DialogAddCategoryBinding? = null
    private val binding get() = _binding!!

    private var onCategoryCreatedListener: (() -> Unit)? = null

    fun setOnCategoryCreatedListener(listener: () -> Unit) {
        this.onCategoryCreatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddCategoryBinding.inflate(layoutInflater)
        val context = requireContext()
        val dbHelper = DatabaseHelper.getInstance(context)

        var selectedColor = "#0284C7"
        var selectedIcon = "ic_wallet"

        fun updateTypeToggleUi() {
            val checkedId = binding.toggleCategoryType.checkedButtonId
            val buttons = listOf(
                Pair(binding.btnTypeExpense, R.id.btnTypeExpense),
                Pair(binding.btnTypeIncome, R.id.btnTypeIncome),
                Pair(binding.btnTypeBoth, R.id.btnTypeBoth)
            )
            for ((btn, id) in buttons) {
                if (id == checkedId) {
                    btn.setBackgroundColor(Color.parseColor("#0284C7"))
                    btn.setTextColor(Color.WHITE)
                    btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                } else {
                    btn.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.surface_variant))
                    btn.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
                    btn.strokeColor = ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(context, R.color.card_stroke))
                }
            }
        }
        binding.toggleCategoryType.addOnButtonCheckedListener { _, _, _ ->
            updateTypeToggleUi()
        }
        updateTypeToggleUi()

        var updateIconsRow: ((String, String) -> Unit)? = null
        var updateColorsRow: ((String) -> Unit)? = null

        fun updateUiColors() {
            val parsedColor = try { Color.parseColor(selectedColor) } catch (_: Exception) { Color.parseColor("#0284C7") }
            binding.btnSaveCategory.backgroundTintList = ColorStateList.valueOf(parsedColor)
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateIconsRow = AppIconRegistry.setupQuickPickIconsRow(
            context = context,
            container = binding.layoutCategoryIcons,
            initialIcon = selectedIcon,
            fragmentManager = childFragmentManager,
            initialAccentColor = selectedColor
        ) { newIcon ->
            selectedIcon = newIcon
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateColorsRow = ColorPickerHelper.setupPaletteRow(
            context = context,
            container = binding.layoutCategoryColors,
            initialColor = selectedColor
        ) { newColor ->
            selectedColor = newColor
            updateUiColors()
        }

        updateUiColors()

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        binding.btnCancelCategory.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSaveCategory.setOnClickListener {
            val name = binding.etCategoryName.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                Toast.makeText(context, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = when (binding.toggleCategoryType.checkedButtonId) {
                R.id.btnTypeIncome -> "INCOME"
                R.id.btnTypeBoth -> "BOTH"
                else -> "EXPENSE"
            }

            val existing = dbHelper.getTransactionCategoryByName(name)
            if (existing != null) {
                Toast.makeText(context, "Category '$name' already exists", Toast.LENGTH_SHORT).show()
            } else {
                dbHelper.insertTransactionCategory(name, type, selectedColor, selectedIcon)
                Toast.makeText(context, "Category '$name' added", Toast.LENGTH_SHORT).show()
            }

            onCategoryCreatedListener?.invoke()
            dialog.dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddCategoryDialogFragment"

        fun newInstance(): AddCategoryDialogFragment {
            return AddCategoryDialogFragment()
        }
    }
}
