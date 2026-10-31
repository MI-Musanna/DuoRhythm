package com.duorhythm.app.ui.profile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogProfileCreateBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class CreateProfileDialog(
    private val activity: Activity,
    private val slotNumber: Int,
    private val onProfileCreated: ((ProfileEntity) -> Unit)? = null
) {
    fun show() {
        val dialogBinding = DialogProfileCreateBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        dialogBinding.tvCreateTitle.text = "Create Profile (Slot $slotNumber)"

        dialogBinding.btnCreateCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnCreateSubmit.setOnClickListener {
            val name = dialogBinding.etCreateName.text?.toString()?.trim() ?: ""
            val email = ""
            val password = dialogBinding.etCreatePassword.text?.toString().orEmpty()
            val institution = dialogBinding.etCreateInstitution.text?.toString()?.trim() ?: ""
            val department = dialogBinding.etCreateDepartment.text?.toString()?.trim() ?: ""
            val semester = dialogBinding.etCreateSemester.text?.toString()?.trim() ?: ""

            if (name.isBlank()) {
                Toast.makeText(activity, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newProfile = ProfileEntity(
                slotNumber = slotNumber,
                name = name,
                email = email,
                passwordHash = if (password.isNotBlank()) ProfileManager.hashPassword(password) else "",
                institution = institution,
                department = department,
                semester = semester,
                rememberEmail = false
            )

            val dbHelper = DatabaseHelper.getInstance(activity)
            val newId = dbHelper.profileDao.insertProfile(newProfile)
            if (newId > 0) {
                val createdProfile = newProfile.copy(id = newId)
                ProfileManager.setActiveProfileId(activity, newId)
                ProfileManager.syncUserProfile(activity, createdProfile)
                Toast.makeText(activity, "Workspace created: $name", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onProfileCreated?.invoke(createdProfile)
            } else {
                Toast.makeText(activity, "Failed to create profile", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
