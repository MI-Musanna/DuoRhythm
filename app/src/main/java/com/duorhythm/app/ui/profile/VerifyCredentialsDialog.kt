package com.duorhythm.app.ui.profile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogProfileLoginBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class VerifyCredentialsDialog(
    private val activity: Activity,
    private val profile: ProfileEntity,
    private val isDestructive: Boolean = false,
    private val onSuccess: () -> Unit
) {
    fun show() {
        val dialogBinding = DialogProfileLoginBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        if (isDestructive) {
            dialogBinding.tvLoginTitle.text = "Confirm Identity"
            dialogBinding.tvLoginSubtitle.text = "Enter password to delete Workspace Slot ${profile.slotNumber}"
            dialogBinding.btnLoginSubmit.text = "VERIFY & DELETE"
        } else {
            dialogBinding.tvLoginTitle.text = "Verify Credentials"
            dialogBinding.tvLoginSubtitle.text = "Authenticating for ${profile.name}"
            dialogBinding.btnLoginSubmit.text = "VERIFY & SWITCH"
        }

        dialogBinding.etLoginPassword.requestFocus()

        dialogBinding.btnLoginCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnLoginSubmit.setOnClickListener {
            val enteredPassword = dialogBinding.etLoginPassword.text?.toString().orEmpty()

            if (profile.passwordHash.isNotBlank()) {
                if (enteredPassword.isBlank()) {
                    Toast.makeText(activity, "Please enter your password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val enteredHash = ProfileManager.hashPassword(enteredPassword)
                if (enteredPassword != profile.password && enteredHash != profile.passwordHash) {
                    Toast.makeText(activity, "Incorrect password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
            onSuccess()
        }

        dialog.show()
    }
}
