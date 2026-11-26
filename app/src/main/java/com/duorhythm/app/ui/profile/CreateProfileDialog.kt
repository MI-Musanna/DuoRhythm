package com.duorhythm.app.ui.profile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.repository.ProfileRepository
import com.duorhythm.app.ui.custom.DuoAlertBanner
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
                DuoAlertBanner.warning(activity, "Please enter your full name")
                return@setOnClickListener
            }

            val trimmedPassword = password.trim()
            if (trimmedPassword.length < 4) {
                DuoAlertBanner.warning(activity, "Please enter a profile password (at least 4 characters)")
                return@setOnClickListener
            }

            val newProfile = ProfileEntity(
                slotNumber = slotNumber,
                name = name,
                email = email,
                passwordHash = ProfileManager.hashPassword(trimmedPassword),
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
                ProfileRepository.refreshProfiles(activity)
                DuoAlertBanner.success(activity, "Workspace created: $name")
                dialog.dismiss()
                onProfileCreated?.invoke(createdProfile)
            } else {
                DuoAlertBanner.error(activity, "Failed to create profile")
            }
        }

        dialog.show()
        com.duorhythm.app.util.ViewAnimationExtensions.attachToDialog(dialog)
    }

    // PERSISTENT AVATAR STORAGE: Copies gallery stream directly into app private filesDir
    fun saveAvatarPermanently(context: android.content.Context, sourceUri: android.net.Uri, profileId: Long): String {
        return com.duorhythm.app.util.ProfileAvatarHelper.saveAvatarPermanently(context, sourceUri, profileId)
    }
}
