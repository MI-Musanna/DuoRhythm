package com.duorhythm.app.ui.profile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.DialogProfileCreateBinding
import com.duorhythm.app.databinding.DialogProfileLoginBinding
import com.duorhythm.app.databinding.DialogProfileSwitcherBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class ProfileSwitcherDialog(
    private val activity: Activity,
    private val onProfileSwitched: (() -> Unit)? = null
) {

    private val dbHelper = DatabaseHelper.getInstance(activity)

    fun show() {
        val binding = DialogProfileSwitcherBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.btnProfileClose.setOnClickListener {
            dialog.dismiss()
        }

        refreshSlotsUI(dialog, binding)

        dialog.show()
    }

    private fun refreshSlotsUI(
        parentDialog: AlertDialog,
        binding: DialogProfileSwitcherBinding
    ) {
        dbHelper.ensureDefaultProfileIfEmpty()

        val activeProfileId = ProfileManager.getActiveProfileId(activity)
        val profiles = dbHelper.profileDao.getAllProfiles()
        val slot1 = profiles.firstOrNull { it.slotNumber == 1 }
        val slot2 = profiles.firstOrNull { it.slotNumber == 2 }
        val slot3 = profiles.firstOrNull { it.slotNumber == 3 }

        val primaryColor = ContextCompat.getColor(activity, R.color.primary)
        val strokeColor = ContextCompat.getColor(activity, R.color.card_stroke)

        // Slot 1
        bindSlot(
            slotNumber = 1,
            profile = slot1,
            isActive = (slot1?.id == activeProfileId),
            card = binding.cardSlot1,
            badge = binding.badgeActiveSlot1,
            btnSwitch = binding.btnSwitchSlot1,
            tvName = binding.tvSlot1Name,
            tvEmail = binding.tvSlot1Email,
            tvDept = binding.tvSlot1Dept,
            primaryColor = primaryColor,
            strokeColor = strokeColor,
            parentDialog = parentDialog
        )

        // Slot 2
        bindSlot(
            slotNumber = 2,
            profile = slot2,
            isActive = (slot2?.id == activeProfileId),
            card = binding.cardSlot2,
            badge = binding.badgeActiveSlot2,
            btnSwitch = binding.btnSwitchSlot2,
            tvName = binding.tvSlot2Name,
            tvEmail = binding.tvSlot2Email,
            tvDept = binding.tvSlot2Dept,
            primaryColor = primaryColor,
            strokeColor = strokeColor,
            parentDialog = parentDialog
        )

        // Slot 3
        bindSlot(
            slotNumber = 3,
            profile = slot3,
            isActive = (slot3?.id == activeProfileId),
            card = binding.cardSlot3,
            badge = binding.badgeActiveSlot3,
            btnSwitch = binding.btnSwitchSlot3,
            tvName = binding.tvSlot3Name,
            tvEmail = binding.tvSlot3Email,
            tvDept = binding.tvSlot3Dept,
            primaryColor = primaryColor,
            strokeColor = strokeColor,
            parentDialog = parentDialog
        )
    }

    private fun bindSlot(
        slotNumber: Int,
        profile: ProfileEntity?,
        isActive: Boolean,
        card: com.google.android.material.card.MaterialCardView,
        badge: View,
        btnSwitch: View,
        tvName: android.widget.TextView,
        tvEmail: android.widget.TextView,
        tvDept: android.widget.TextView,
        primaryColor: Int,
        strokeColor: Int,
        parentDialog: AlertDialog
    ) {
        badge.visibility = if (isActive) View.VISIBLE else View.GONE
        btnSwitch.visibility = if (!isActive && profile != null) View.VISIBLE else View.GONE
        card.strokeColor = if (isActive) primaryColor else strokeColor
        card.strokeWidth = if (isActive) 4 else 2

        if (profile != null) {
            tvName.text = profile.name.ifBlank { "Profile $slotNumber" }
            tvEmail.text = profile.email.ifBlank { "No email set" }
            val meta = listOf(profile.department, profile.semester, profile.institution)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            tvDept.text = if (meta.isNotBlank()) meta else "Workspace Slot $slotNumber"

            val onSlotAction = View.OnClickListener {
                if (isActive) {
                    Toast.makeText(activity, "Currently in ${profile.name}'s workspace", Toast.LENGTH_SHORT).show()
                } else {
                    showLoginDialog(parentDialog, profile)
                }
            }
            card.setOnClickListener(onSlotAction)
            btnSwitch.setOnClickListener(onSlotAction)
        } else {
            tvName.text = "+ Set Up Profile Slot $slotNumber"
            tvEmail.text = "Tap to initialize isolated workspace"
            tvDept.text = "Available workspace slot"

            card.setOnClickListener {
                showCreateDialog(parentDialog, slotNumber)
            }
        }
    }

    private fun showLoginDialog(parentDialog: AlertDialog, profile: ProfileEntity) {
        val binding = DialogProfileLoginBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.tvLoginSubtitle.text = "Switching to ${profile.name} (Slot ${profile.slotNumber})"

        if (profile.rememberEmail) {
            binding.etLoginEmail.setText(profile.email)
            binding.etLoginEmail.isEnabled = false
            binding.layoutLoginEmail.alpha = 0.7f
            binding.cbRememberEmail.isChecked = true
            binding.etLoginPassword.requestFocus()
        } else {
            binding.etLoginEmail.setText("")
            binding.etLoginEmail.isEnabled = true
            binding.layoutLoginEmail.alpha = 1.0f
            binding.cbRememberEmail.isChecked = false
            binding.etLoginEmail.requestFocus()
        }

        binding.btnLoginCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnLoginSubmit.setOnClickListener {
            val email = binding.etLoginEmail.text?.toString()?.trim() ?: ""
            val password = binding.etLoginPassword.text?.toString() ?: ""

            if (password.isBlank()) {
                Toast.makeText(activity, "Please enter your password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!profile.rememberEmail && !email.equals(profile.email.trim(), ignoreCase = true)) {
                Toast.makeText(activity, "Email address does not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredHash = ProfileManager.hashPassword(password)
            if (enteredHash != profile.passwordHash) {
                Toast.makeText(activity, "Incorrect password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Update rememberEmail setting if changed
            val rememberChanged = binding.cbRememberEmail.isChecked != profile.rememberEmail
            if (rememberChanged) {
                dbHelper.profileDao.updateProfile(profile.copy(rememberEmail = binding.cbRememberEmail.isChecked))
            }

            // Switch active profile
            ProfileManager.setActiveProfileId(activity, profile.id)
            ProfileManager.syncUserProfile(activity, profile)

            Toast.makeText(activity, "Switched to ${profile.name}", Toast.LENGTH_SHORT).show()

            dialog.dismiss()
            parentDialog.dismiss()

            if (onProfileSwitched != null) {
                onProfileSwitched.invoke()
            } else {
                activity.recreate()
            }
        }

        dialog.show()
    }

    private fun showCreateDialog(parentDialog: AlertDialog, slotNumber: Int) {
        val binding = DialogProfileCreateBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.tvCreateTitle.text = "Create Profile (Slot $slotNumber)"

        binding.btnCreateCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnCreateSubmit.setOnClickListener {
            val name = binding.etCreateName.text?.toString()?.trim() ?: ""
            val email = binding.etCreateEmail.text?.toString()?.trim() ?: ""
            val password = binding.etCreatePassword.text?.toString() ?: ""
            val institution = binding.etCreateInstitution.text?.toString()?.trim() ?: ""
            val department = binding.etCreateDepartment.text?.toString()?.trim() ?: ""
            val semester = binding.etCreateSemester.text?.toString()?.trim() ?: ""
            val rememberEmail = binding.cbCreateRememberEmail.isChecked

            if (name.isBlank()) {
                Toast.makeText(activity, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isBlank() || !email.contains("@")) {
                Toast.makeText(activity, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(activity, "Please create a password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newProfile = ProfileEntity(
                slotNumber = slotNumber,
                name = name,
                email = email,
                passwordHash = ProfileManager.hashPassword(password),
                institution = institution,
                department = department,
                semester = semester,
                rememberEmail = rememberEmail
            )

            val newId = dbHelper.profileDao.insertProfile(newProfile)
            if (newId > 0) {
                ProfileManager.setActiveProfileId(activity, newId)
                ProfileManager.syncUserProfile(activity, newProfile.copy(id = newId))

                Toast.makeText(activity, "Workspace created: $name", Toast.LENGTH_SHORT).show()

                dialog.dismiss()
                parentDialog.dismiss()

                if (onProfileSwitched != null) {
                    onProfileSwitched.invoke()
                } else {
                    activity.recreate()
                }
            } else {
                Toast.makeText(activity, "Failed to create profile", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
