package com.duorhythm.app.ui.profile

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.local.pref.UserProfileManager
import com.duorhythm.app.databinding.ActivityProfileBinding
import com.duorhythm.app.databinding.DialogChangePasswordBinding
import com.duorhythm.app.databinding.DialogEditAcademicInfoBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var dbHelper: DatabaseHelper
    private var currentProfile: ProfileEntity? = null

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val savedPath = UserProfileManager.saveAvatarFromUri(this, it)
                if (savedPath != null) {
                    val activeId = ProfileManager.getActiveProfileId(this)
                    val profile = dbHelper.profileDao.getProfileById(activeId)
                    if (profile != null) {
                        val updated = profile.copy(avatarUri = savedPath)
                        dbHelper.profileDao.updateProfile(updated)
                        UserProfileManager.setAvatarUri(this, savedPath)
                        currentProfile = updated
                        loadProfileData()
                        Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.setBackgroundDrawableResource(R.drawable.bg_ambient_canvas)
            binding = ActivityProfileBinding.inflate(layoutInflater)
            setContentView(binding.root)

            dbHelper = DatabaseHelper.getInstance(this)
            dbHelper.ensureDefaultProfileIfEmpty()

            setupWindowInsets()
            setupToolbar()
            loadProfileData()
            setupActions()
        } catch (t: Throwable) {
            t.printStackTrace()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfileData()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.profileRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarProfile.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    private fun setupToolbar() {
        binding.btnProfileBack.setOnClickListener {
            finish()
        }
    }

    private fun loadProfileData() {
        val activeId = ProfileManager.getActiveProfileId(this)
        val profile = dbHelper.profileDao.getProfileById(activeId) ?: run {
            dbHelper.ensureDefaultProfileIfEmpty()
            dbHelper.profileDao.getProfileById(activeId)
        }
        currentProfile = profile

        if (profile != null) {
            binding.chipActiveSlot.text = "Slot ${profile.slotNumber} Active"
            binding.tvProfileName.text = profile.name.ifBlank { "Scholar" }
            binding.tvProfileEmail.text = profile.email.ifBlank { "No email registered" }

            binding.tvProfileInstitution.text = profile.institution.ifBlank { "Not Configured" }
            binding.tvProfileDepartment.text = profile.department.ifBlank { "Not Configured" }
            binding.tvProfileSemester.text = profile.semester.ifBlank { "Not Configured" }

            // Checkbox: Remember email
            binding.cbRememberEmail.setOnCheckedChangeListener(null)
            binding.cbRememberEmail.isChecked = profile.rememberEmail
            binding.cbRememberEmail.setOnCheckedChangeListener { _, isChecked ->
                val updated = profile.copy(rememberEmail = isChecked)
                dbHelper.profileDao.updateProfile(updated)
                currentProfile = updated
            }

            // Avatar
            val bitmap = UserProfileManager.loadCircularAvatarBitmap(this, 192)
            if (bitmap != null) {
                binding.ivProfileAvatar.setPadding(0, 0, 0, 0)
                binding.ivProfileAvatar.imageTintList = null
                binding.ivProfileAvatar.setImageBitmap(bitmap)
            } else {
                val density = resources.displayMetrics.density
                val pad16 = (16 * density).toInt()
                binding.ivProfileAvatar.setPadding(pad16, pad16, pad16, pad16)
                binding.ivProfileAvatar.setImageResource(R.drawable.ic_person)
            }
        }
    }

    private fun setupActions() {
        val openPicker = {
            pickAvatarLauncher.launch("image/*")
        }

        binding.containerProfileAvatar.setOnClickListener { openPicker() }
        binding.btnChangeAvatarBadge.setOnClickListener { openPicker() }

        binding.btnEditAcademicInfo.setOnClickListener {
            showEditAcademicDialog()
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnClearProfileData.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    private fun showEditAcademicDialog() {
        val profile = currentProfile ?: return
        val dialogBinding = DialogEditAcademicInfoBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        dialogBinding.etEditName.setText(profile.name)
        dialogBinding.etEditInstitution.setText(profile.institution)
        dialogBinding.etEditDepartment.setText(profile.department)
        dialogBinding.etEditSemester.setText(profile.semester)

        dialogBinding.btnEditCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnEditSave.setOnClickListener {
            val newName = dialogBinding.etEditName.text?.toString()?.trim() ?: ""
            val newInst = dialogBinding.etEditInstitution.text?.toString()?.trim() ?: ""
            val newDept = dialogBinding.etEditDepartment.text?.toString()?.trim() ?: ""
            val newSem = dialogBinding.etEditSemester.text?.toString()?.trim() ?: ""

            val finalName = if (newName.isNotBlank()) newName else profile.name
            val updated = profile.copy(
                name = finalName,
                institution = newInst,
                department = newDept,
                semester = newSem
            )

            dbHelper.profileDao.updateProfile(updated)
            UserProfileManager.setUserName(this, finalName)
            UserProfileManager.setInstitutionName(this, newInst)
            UserProfileManager.setAcademicFaculty(this, newDept)

            currentProfile = updated
            loadProfileData()

            Toast.makeText(this, "Academic info updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val profile = currentProfile ?: return
        val dialogBinding = DialogChangePasswordBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        dialogBinding.btnPassCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnPassSave.setOnClickListener {
            val currentPass = dialogBinding.etCurrentPassword.text?.toString() ?: ""
            val newPass = dialogBinding.etNewPassword.text?.toString() ?: ""
            val confirmPass = dialogBinding.etConfirmPassword.text?.toString() ?: ""

            if (currentPass.isBlank()) {
                Toast.makeText(this, "Please enter your current password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentHash = ProfileManager.hashPassword(currentPass)
            if (currentHash != profile.passwordHash) {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass.isBlank()) {
                Toast.makeText(this, "New password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newHash = ProfileManager.hashPassword(newPass)
            val updated = profile.copy(passwordHash = newHash)
            dbHelper.profileDao.updateProfile(updated)
            currentProfile = updated

            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showClearConfirmationDialog() {
        val profile = currentProfile ?: return
        AlertDialog.Builder(this)
            .setTitle("Clear Profile Data")
            .setMessage("Are you sure you want to reset your academic credentials and personalization for Slot ${profile.slotNumber}?")
            .setPositiveButton("Clear Data") { _, _ ->
                val updated = profile.copy(
                    institution = "",
                    department = "",
                    semester = "",
                    avatarUri = null
                )
                dbHelper.profileDao.updateProfile(updated)
                UserProfileManager.setInstitutionName(this, "")
                UserProfileManager.setAcademicFaculty(this, "")
                UserProfileManager.setAvatarUri(this, "")

                currentProfile = updated
                loadProfileData()
                Toast.makeText(this, "Profile data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
