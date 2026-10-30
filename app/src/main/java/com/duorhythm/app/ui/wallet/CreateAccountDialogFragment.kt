package com.duorhythm.app.ui.wallet

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.databinding.DialogAddBucketBinding
import com.duorhythm.app.ui.common.AppIconRegistry
import com.duorhythm.app.ui.common.ColorPickerHelper

class CreateAccountDialogFragment : DialogFragment() {

    private var _binding: DialogAddBucketBinding? = null
    private val binding get() = _binding!!

    private var onAccountCreatedListener: (() -> Unit)? = null

    fun setOnAccountCreatedListener(listener: () -> Unit) {
        this.onAccountCreatedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddBucketBinding.inflate(layoutInflater)
        val context = requireContext()
        val dbHelper = DatabaseHelper.getInstance(context)

        var selectedIcon = "ic_cash"
        var selectedColor = "#06B6D4"

        var updateIconsRow: ((String, String) -> Unit)? = null
        var updateColorsRow: ((String) -> Unit)? = null

        fun updateUiColors() {
            val parsedColor = try { Color.parseColor(selectedColor) } catch (_: Exception) { Color.parseColor("#06B6D4") }
            binding.btnSaveBucket.backgroundTintList = ColorStateList.valueOf(parsedColor)
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateIconsRow = AppIconRegistry.setupQuickPickIconsRow(
            context = context,
            container = binding.layoutBucketIcons,
            initialIcon = selectedIcon,
            fragmentManager = childFragmentManager,
            initialAccentColor = selectedColor
        ) { newIcon ->
            selectedIcon = newIcon
            updateIconsRow?.invoke(selectedIcon, selectedColor)
        }

        updateColorsRow = ColorPickerHelper.setupPaletteRow(
            context = context,
            container = binding.layoutBucketColors,
            initialColor = selectedColor
        ) { newColor ->
            selectedColor = newColor
            updateUiColors()
        }

        updateUiColors()

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        binding.btnCancelBucket.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSaveBucket.setOnClickListener {
            val name = binding.etBucketName.text?.toString()?.trim().orEmpty()
            val balanceStr = binding.etBucketBalance.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                binding.tilBucketName.error = "Please enter an account name"
                return@setOnClickListener
            } else {
                binding.tilBucketName.error = null
            }

            val balance = balanceStr.toDoubleOrNull() ?: 0.0

            val activeProfileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(requireContext())
            val item = WalletBucketItem(
                profileId = activeProfileId,
                name = name,
                icon = selectedIcon,
                colorHex = selectedColor,
                connectedAccounts = name,
                balance = balance,
                note = "User-created account"
            )
            val id = dbHelper.insertWalletBucket(item)
            if (id != -1L) {
                Toast.makeText(context, "Account '$name' created", Toast.LENGTH_SHORT).show()
                onAccountCreatedListener?.invoke()
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Maximum of 5 accounts reached", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateAccountDialogFragment"

        fun newInstance(): CreateAccountDialogFragment {
            return CreateAccountDialogFragment()
        }
    }
}
