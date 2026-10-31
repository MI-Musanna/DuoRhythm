package com.duorhythm.app.ui.profile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogProfileEditBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class EditProfileDialog(
    private val activity: Activity,
    private val profile: ProfileEntity,
    private val onProfileUpdated: ((ProfileEntity) -> Unit)? = null
) {
    fun show() {
        val dialogBinding = DialogProfileEditBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        dialogBinding.tvEditTitle.text = "Edit Profile (Slot ${profile.slotNumber})"
        dialogBinding.etEditName.setText(profile.name)
        dialogBinding.etEditDepartment.setText(profile.department)
        dialogBinding.etEditSemester.setText(profile.semester)

        dialogBinding.btnEditCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnEditSubmit.setOnClickListener {
            val newName = dialogBinding.etEditName.text?.toString()?.trim() ?: ""
            if (newName.isBlank()) {
                Toast.makeText(activity, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newDept = dialogBinding.etEditDepartment.text?.toString()?.trim() ?: ""
            val newSem = dialogBinding.etEditSemester.text?.toString()?.trim() ?: ""
            val newPass = dialogBinding.etEditPassword.text?.toString().orEmpty()
            val newHash = if (newPass.isNotBlank()) ProfileManager.hashPassword(newPass) else profile.passwordHash

            val updated = profile.copy(
                name = newName,
                department = newDept,
                semester = newSem,
                passwordHash = newHash
            )

            val dbHelper = DatabaseHelper.getInstance(activity)
            dbHelper.profileDao.updateProfile(updated)

            val activeId = ProfileManager.getActiveProfileId(activity)
            if (activeId == profile.id) {
                ProfileManager.syncUserProfile(activity, updated)
            }

            Toast.makeText(activity, "Profile updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onProfileUpdated?.invoke(updated)
        }

        dialog.show()
    }
}
