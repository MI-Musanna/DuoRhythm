package com.duorhythm.app.ui.profile

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogProfileLoginBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class VerifyCredentialsDialog() : DialogFragment() {

    var onVerificationSuccess: (() -> Unit)? = null

    private var legacyActivity: Activity? = null
    private var legacyProfile: ProfileEntity? = null
    private var legacyIsDestructive: Boolean = false
    private var legacyOnSuccess: (() -> Unit)? = null

    constructor(
        activity: Activity,
        profile: ProfileEntity,
        isDestructive: Boolean = false,
        onSuccess: () -> Unit
    ) : this() {
        this.legacyActivity = activity
        this.legacyProfile = profile
        this.legacyIsDestructive = isDestructive
        this.legacyOnSuccess = onSuccess
        this.onVerificationSuccess = onSuccess
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val act = activity ?: legacyActivity ?: requireActivity()
        val dialogBinding = DialogProfileLoginBinding.inflate(LayoutInflater.from(act))
        val dialog = AlertDialog.Builder(act)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        val slot = arguments?.getInt(ARG_SLOT) ?: legacyProfile?.slotNumber ?: 1
        val profileName = arguments?.getString(ARG_PROFILE_NAME) ?: legacyProfile?.name.orEmpty()
        val isDestructive = arguments?.getBoolean(ARG_IS_DESTRUCTIVE, false) ?: legacyIsDestructive

        val dbHelper = DatabaseHelper.getInstance(act)
        val profile = legacyProfile ?: dbHelper.profileDao.getProfileBySlot(slot)

        if (isDestructive) {
            dialogBinding.tvLoginTitle.text = "Confirm Identity"
            dialogBinding.tvLoginSubtitle.text = "Enter password to delete Workspace Slot $slot"
            dialogBinding.btnLoginSubmit.text = "VERIFY & DELETE"
        } else {
            dialogBinding.tvLoginTitle.text = "Verify Credentials"
            dialogBinding.tvLoginSubtitle.text = if (profileName.isNotBlank()) "Authenticating for $profileName" else "Authenticating Workspace Slot $slot"
            dialogBinding.btnLoginSubmit.text = "VERIFY & SWITCH"
        }

        dialogBinding.etLoginPassword.requestFocus()

        dialogBinding.btnLoginCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnLoginSubmit.setOnClickListener {
            val enteredPassword = dialogBinding.etLoginPassword.text?.toString().orEmpty()

            if (profile != null && profile.passwordHash.isNotBlank()) {
                if (enteredPassword.isBlank()) {
                    Toast.makeText(act, "Please enter your password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val enteredHash = ProfileManager.hashPassword(enteredPassword)
                if (enteredPassword != profile.password && enteredHash != profile.passwordHash) {
                    Toast.makeText(act, "Incorrect password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
            onVerificationSuccess?.invoke()
            legacyOnSuccess?.invoke()
        }

        return dialog
    }

    fun show() {
        val act = legacyActivity ?: return
        val dialogBinding = DialogProfileLoginBinding.inflate(LayoutInflater.from(act))
        val dialog = AlertDialog.Builder(act)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        val profile = legacyProfile
        val isDestructive = legacyIsDestructive

        if (isDestructive) {
            dialogBinding.tvLoginTitle.text = "Confirm Identity"
            dialogBinding.tvLoginSubtitle.text = "Enter password to delete Workspace Slot ${profile?.slotNumber ?: 1}"
            dialogBinding.btnLoginSubmit.text = "VERIFY & DELETE"
        } else {
            dialogBinding.tvLoginTitle.text = "Verify Credentials"
            dialogBinding.tvLoginSubtitle.text = "Authenticating for ${profile?.name.orEmpty()}"
            dialogBinding.btnLoginSubmit.text = "VERIFY & SWITCH"
        }

        dialogBinding.etLoginPassword.requestFocus()

        dialogBinding.btnLoginCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnLoginSubmit.setOnClickListener {
            val enteredPassword = dialogBinding.etLoginPassword.text?.toString().orEmpty()

            if (profile != null && profile.passwordHash.isNotBlank()) {
                if (enteredPassword.isBlank()) {
                    Toast.makeText(act, "Please enter your password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val enteredHash = ProfileManager.hashPassword(enteredPassword)
                if (enteredPassword != profile.password && enteredHash != profile.passwordHash) {
                    Toast.makeText(act, "Incorrect password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
            onVerificationSuccess?.invoke()
            legacyOnSuccess?.invoke()
        }

        dialog.show()
    }

    companion object {
        const val TAG = "VerifyCredentialsDialog"
        private const val ARG_SLOT = "arg_slot"
        private const val ARG_PROFILE_NAME = "arg_profile_name"
        private const val ARG_IS_DESTRUCTIVE = "arg_is_destructive"

        fun newInstance(slot: Int, profileName: String, isDestructive: Boolean = false): VerifyCredentialsDialog {
            val dialog = VerifyCredentialsDialog()
            dialog.arguments = Bundle().apply {
                putInt(ARG_SLOT, slot)
                putString(ARG_PROFILE_NAME, profileName)
                putBoolean(ARG_IS_DESTRUCTIVE, isDestructive)
            }
            return dialog
        }
    }
}
