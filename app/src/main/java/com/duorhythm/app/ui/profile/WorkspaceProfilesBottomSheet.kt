package com.duorhythm.app.ui.profile

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.local.pref.UserProfileManager
import com.duorhythm.app.databinding.BottomSheetWorkspaceProfilesBinding
import com.duorhythm.app.databinding.DialogProfileCreateBinding
import com.duorhythm.app.databinding.DialogProfileEditBinding
import com.duorhythm.app.databinding.DialogProfileLoginBinding
import com.duorhythm.app.databinding.ItemWorkspaceProfileBinding
import com.duorhythm.app.security.BiometricAuthHelper
import com.duorhythm.app.util.AnimationUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import database.entities.ProfileEntity

class WorkspaceProfilesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetWorkspaceProfilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private var onProfileSwitched: (() -> Unit)? = null

    private var pendingAvatarCallback: ((String?) -> Unit)? = null
    private var pendingAvatarProfileId: Long = 1L
    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val ctx = context ?: return@registerForActivityResult
                val targetFile = java.io.File(ctx.filesDir, "avatar_profile_${pendingAvatarProfileId}.jpg")
                try {
                    val sampled = com.duorhythm.app.util.ImageUtils.decodeSampledBitmapFromUri(ctx, uri, 1080, 1080)
                    if (sampled == null) {
                        Toast.makeText(ctx, "Failed to load image", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }
                    CropAvatarDialog.show(ctx, sampled) { cropped512 ->
                        try {
                            com.duorhythm.app.util.ImageUtils.saveBitmapToFile(cropped512, targetFile, quality = 85)
                            cropped512.recycle()
                            val uniqueUriString = Uri.fromFile(targetFile).toString() + "?t=" + System.currentTimeMillis()
                            pendingAvatarCallback?.invoke(uniqueUriString)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(ctx, "Failed to save cropped avatar", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(ctx, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetWorkspaceProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val act = activity ?: return
        dbHelper = DatabaseHelper.getInstance(act)
        dbHelper.ensureDefaultProfileIfEmpty()

        binding.btnProfileClose.setOnClickListener {
            dismiss()
        }

        binding.rvWorkspaceProfiles.layoutManager = LinearLayoutManager(context)
        refreshProfilesList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setOnProfileSwitchedListener(listener: (() -> Unit)?) {
        this.onProfileSwitched = listener
    }

    private fun refreshProfilesList() {
        val act = activity ?: return
        val activeProfileId = ProfileManager.getActiveProfileId(act)
        val profiles = dbHelper.profileDao.getAllProfiles()

        val slotItems = (1..3).map { slot ->
            val profile = profiles.firstOrNull { it.slotNumber == slot }
            SlotItem(
                slotNumber = slot,
                profile = profile,
                isActive = (profile != null && profile.id == activeProfileId)
            )
        }

        binding.rvWorkspaceProfiles.adapter = ProfileSlotsAdapter(slotItems)
    }

    private fun showVerifyCredentialsDialog(
        profile: ProfileEntity,
        isDestructive: Boolean = false,
        onSuccess: () -> Unit
    ) {
        val act = activity ?: return
        val dialogBinding = DialogProfileLoginBinding.inflate(LayoutInflater.from(act))
        val dialog = AlertDialog.Builder(act)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        if (isDestructive) {
            dialogBinding.tvLoginTitle.text = "Confirm Identity"
            dialogBinding.tvLoginSubtitle.text = "Enter password to delete Workspace Slot ${profile.slotNumber}"
        } else {
            dialogBinding.tvLoginSubtitle.text = "Authenticating for ${profile.name} (Slot ${profile.slotNumber})"
        }

        if (profile.rememberEmail) {
            dialogBinding.etLoginEmail.setText(profile.email)
            dialogBinding.etLoginEmail.isEnabled = false
            dialogBinding.layoutLoginEmail.alpha = 0.7f
            dialogBinding.cbRememberEmail.isChecked = true
            dialogBinding.etLoginPassword.requestFocus()
        } else {
            dialogBinding.etLoginEmail.setText("")
            dialogBinding.etLoginEmail.isEnabled = true
            dialogBinding.layoutLoginEmail.alpha = 1.0f
            dialogBinding.cbRememberEmail.isChecked = false
            dialogBinding.etLoginEmail.requestFocus()
        }

        dialogBinding.btnLoginCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnLoginSubmit.setOnClickListener {
            val email = dialogBinding.etLoginEmail.text?.toString()?.trim() ?: ""
            val password = dialogBinding.etLoginPassword.text?.toString() ?: ""

            if (password.isBlank()) {
                Toast.makeText(act, "Please enter your password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!profile.rememberEmail && !email.equals(profile.email.trim(), ignoreCase = true)) {
                Toast.makeText(act, "Email address does not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val enteredHash = ProfileManager.hashPassword(password)
            if (enteredHash != profile.passwordHash) {
                Toast.makeText(act, "Incorrect password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (dialogBinding.cbRememberEmail.isChecked != profile.rememberEmail) {
                dbHelper.profileDao.updateProfile(profile.copy(rememberEmail = dialogBinding.cbRememberEmail.isChecked))
            }

            dialog.dismiss()
            onSuccess()
        }

        dialog.show()
    }

    private fun handleSwitchWorkspace(profile: ProfileEntity) {
        val act = activity ?: return
        if (BiometricAuthHelper.isBiometricsAvailable(act)) {
            BiometricAuthHelper.authenticate(
                activity = act,
                title = "Switch Workspace",
                subtitle = "Confirm identity to access ${profile.name}'s workspace",
                negativeButtonText = "Use Password/PIN",
                onSuccess = {
                    executeProfileSwitch(profile)
                },
                onFallbackToPassword = {
                    showVerifyCredentialsDialog(profile) {
                        executeProfileSwitch(profile)
                    }
                }
            )
        } else {
            showVerifyCredentialsDialog(profile) {
                executeProfileSwitch(profile)
            }
        }
    }

    private fun executeProfileSwitch(profile: ProfileEntity) {
        val act = activity ?: return
        ProfileManager.setActiveProfileId(act, profile.id)
        ProfileManager.syncUserProfile(act, profile)
        Toast.makeText(act, "Switched to ${profile.name}", Toast.LENGTH_SHORT).show()
        dismiss()
        if (onProfileSwitched != null) {
            onProfileSwitched?.invoke()
        } else {
            act.recreate()
        }
    }

    private fun handleEditProfile(profile: ProfileEntity) {
        val act = activity ?: return
        if (BiometricAuthHelper.isBiometricsAvailable(act)) {
            BiometricAuthHelper.authenticate(
                activity = act,
                title = "Edit Profile",
                subtitle = "Confirm identity to edit ${profile.name}",
                negativeButtonText = "Use Password/PIN",
                onSuccess = {
                    showEditProfileDialog(profile)
                },
                onFallbackToPassword = {
                    showVerifyCredentialsDialog(profile) {
                        showEditProfileDialog(profile)
                    }
                }
            )
        } else {
            showVerifyCredentialsDialog(profile) {
                showEditProfileDialog(profile)
            }
        }
    }

    private fun showEditProfileDialog(profile: ProfileEntity) {
        val act = activity ?: return
        val dialogBinding = DialogProfileEditBinding.inflate(LayoutInflater.from(act))
        val dialog = AlertDialog.Builder(act)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        dialogBinding.tvEditTitle.text = "Edit Profile (Slot ${profile.slotNumber})"
        dialogBinding.etEditName.setText(profile.name)
        dialogBinding.etEditDepartment.setText(profile.department)
        dialogBinding.etEditSemester.setText(profile.semester)

        var selectedAvatarUri: String? = profile.avatarUri

        fun updateAvatarPreview() {
            dialogBinding.ivEditAvatar.setImageDrawable(null)
            dialogBinding.ivEditAvatar.setImageBitmap(null)
            if (!selectedAvatarUri.isNullOrBlank()) {
                try {
                    dialogBinding.ivEditAvatar.setImageURI(Uri.parse(selectedAvatarUri))
                    dialogBinding.ivEditAvatar.imageTintList = null
                } catch (e: Exception) {
                    dialogBinding.ivEditAvatar.setImageResource(R.drawable.ic_default_profile)
                    dialogBinding.ivEditAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                }
            } else {
                dialogBinding.ivEditAvatar.setImageResource(R.drawable.ic_default_profile)
                dialogBinding.ivEditAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }
        }

        updateAvatarPreview()

        pendingAvatarProfileId = profile.id
        pendingAvatarCallback = { newPath ->
            selectedAvatarUri = newPath
            updateAvatarPreview()
        }

        val openPicker = {
            pickAvatarLauncher.launch("image/*")
        }

        dialogBinding.containerEditAvatar.setOnClickListener { openPicker() }
        dialogBinding.btnChangeAvatar.setOnClickListener { openPicker() }
        dialogBinding.btnRemoveAvatar.setOnClickListener {
            val targetFile = java.io.File(act.filesDir, "avatar_profile_${profile.id}.jpg")
            if (targetFile.exists()) {
                try { targetFile.delete() } catch (_: Exception) {}
            }
            selectedAvatarUri = null
            updateAvatarPreview()
        }

        dialogBinding.btnEditCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnEditSubmit.setOnClickListener {
            val newName = dialogBinding.etEditName.text?.toString()?.trim() ?: ""
            if (newName.isBlank()) {
                Toast.makeText(act, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newDept = dialogBinding.etEditDepartment.text?.toString()?.trim() ?: ""
            val newSem = dialogBinding.etEditSemester.text?.toString()?.trim() ?: ""

            val updated = profile.copy(
                name = newName,
                department = newDept,
                semester = newSem,
                avatarUri = selectedAvatarUri
            )

            dbHelper.profileDao.updateProfile(updated)

            val activeId = ProfileManager.getActiveProfileId(act)
            if (activeId == profile.id) {
                ProfileManager.syncUserProfile(act, updated)
            }

            Toast.makeText(act, "Profile updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            refreshProfilesList()
            onProfileSwitched?.invoke()
        }

        dialog.show()
    }

    private fun handleDeleteWorkspace(profile: ProfileEntity) {
        val act = activity ?: return
        if (BiometricAuthHelper.isBiometricsAvailable(act)) {
            BiometricAuthHelper.authenticate(
                activity = act,
                title = "Delete Workspace Slot ${profile.slotNumber}",
                subtitle = "Confirm identity to delete Workspace Slot ${profile.slotNumber}",
                negativeButtonText = "Use Password/PIN",
                onSuccess = {
                    showDeleteConfirmationDialog(profile)
                },
                onFallbackToPassword = {
                    showVerifyCredentialsDialog(profile, isDestructive = true) {
                        showDeleteConfirmationDialog(profile)
                    }
                }
            )
        } else {
            showVerifyCredentialsDialog(profile, isDestructive = true) {
                showDeleteConfirmationDialog(profile)
            }
        }
    }

    private fun showDeleteConfirmationDialog(profile: ProfileEntity) {
        val act = activity ?: return
        val message = if (profile.slotNumber == 1) {
            "Deleting Workspace Slot 1 will wipe its data and promote your currently active workspace to Slot 1. Do you want to proceed?"
        } else {
            "Permanently delete Workspace Slot ${profile.slotNumber} and all associated data?"
        }
        AlertDialog.Builder(act)
            .setTitle("Delete Workspace Slot ${profile.slotNumber}")
            .setMessage(message)
            .setPositiveButton("Delete Permanently") { _, _ ->
                executeDeleteWorkspace(profile)
            }
            .setNegativeButton("Cancel", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#EF4444"))
            }
    }

    private fun executeDeleteWorkspace(targetProfile: ProfileEntity) {
        val act = activity ?: return
        try {
            val activeProfileId = ProfileManager.getActiveProfileId(act)
            if (targetProfile.slotNumber == 1) {
                // Promote active workspace to Slot 1
                dbHelper.promoteProfileToSlot1(activeProfileId)
                val newSlot1 = dbHelper.profileDao.getProfileBySlot(1)
                val newActiveId = newSlot1?.id ?: 1L
                ProfileManager.setActiveProfileId(act, newActiveId)
                if (newSlot1 != null) {
                    ProfileManager.syncUserProfile(act, newSlot1)
                }
                Toast.makeText(act, "Workspace Slot 1 deleted. Promoted active workspace to Slot 1.", Toast.LENGTH_SHORT).show()
            } else {
                val avatarFile = java.io.File(act.filesDir, "avatar_profile_${targetProfile.id}.jpg")
                if (avatarFile.exists()) {
                    try { avatarFile.delete() } catch (_: Exception) {}
                }
                dbHelper.deleteWorkspaceData(targetProfile.id)
                Toast.makeText(act, "Workspace Slot ${targetProfile.slotNumber} deleted", Toast.LENGTH_SHORT).show()
            }

            refreshProfilesList()
            if (onProfileSwitched != null) {
                onProfileSwitched?.invoke()
            } else {
                act.recreate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(act, "Failed to delete workspace: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateDialog(slotNumber: Int) {
        val act = activity ?: return
        val dialogBinding = DialogProfileCreateBinding.inflate(LayoutInflater.from(act))
        val dialog = AlertDialog.Builder(act)
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
            val email = dialogBinding.etCreateEmail.text?.toString()?.trim() ?: ""
            val password = dialogBinding.etCreatePassword.text?.toString() ?: ""
            val institution = dialogBinding.etCreateInstitution.text?.toString()?.trim() ?: ""
            val department = dialogBinding.etCreateDepartment.text?.toString()?.trim() ?: ""
            val semester = dialogBinding.etCreateSemester.text?.toString()?.trim() ?: ""
            val rememberEmail = dialogBinding.cbCreateRememberEmail.isChecked

            if (name.isBlank()) {
                Toast.makeText(act, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isBlank() || !email.contains("@")) {
                Toast.makeText(act, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isBlank()) {
                Toast.makeText(act, "Please create a password", Toast.LENGTH_SHORT).show()
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
                ProfileManager.setActiveProfileId(act, newId)
                ProfileManager.syncUserProfile(act, newProfile.copy(id = newId))
                Toast.makeText(act, "Workspace created: $name", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                dismiss()
                if (onProfileSwitched != null) {
                    onProfileSwitched?.invoke()
                } else {
                    act.recreate()
                }
            } else {
                Toast.makeText(act, "Failed to create profile", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private data class SlotItem(
        val slotNumber: Int,
        val profile: ProfileEntity?,
        val isActive: Boolean
    )

    private inner class ProfileSlotsAdapter(
        private val items: List<SlotItem>
    ) : RecyclerView.Adapter<ProfileSlotsAdapter.SlotViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
            val itemBinding = ItemWorkspaceProfileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return SlotViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class SlotViewHolder(private val itemBinding: ItemWorkspaceProfileBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: SlotItem) {
                val ctx = itemBinding.root.context
                val primaryColor = ContextCompat.getColor(ctx, R.color.primary)
                val strokeColor = ContextCompat.getColor(ctx, R.color.card_stroke)

                itemBinding.tvSlotTag.text = "SLOT ${item.slotNumber}"

                itemBinding.ivUserAvatar.setImageDrawable(null)
                itemBinding.ivUserAvatar.setImageBitmap(null)

                if (item.profile != null) {
                    val profile = item.profile
                    itemBinding.cardProfileSlot.strokeColor = if (item.isActive) primaryColor else strokeColor
                    itemBinding.cardProfileSlot.strokeWidth = if (item.isActive) 4 else 2

                    itemBinding.tvProfileName.text = profile.name.ifBlank { "Profile ${item.slotNumber}" }
                    itemBinding.tvProfileEmail.text = profile.email.ifBlank { "No email set" }

                    val meta = listOf(profile.department, profile.semester, profile.institution)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    itemBinding.tvProfileDept.text = if (meta.isNotBlank()) meta else "Workspace Slot ${item.slotNumber}"

                    val avatarUriString = profile.avatarUri
                    if (!avatarUriString.isNullOrBlank()) {
                        try {
                            itemBinding.ivUserAvatar.setImageURI(Uri.parse(avatarUriString))
                            itemBinding.ivUserAvatar.imageTintList = null
                        } catch (e: Exception) {
                            itemBinding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                            itemBinding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                        }
                    } else {
                        itemBinding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                        itemBinding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                    }

                    itemBinding.btnEditProfile.visibility = View.VISIBLE
                    itemBinding.btnEditProfile.setOnClickListener {
                        handleEditProfile(profile)
                    }

                    if (!item.isActive) {
                        itemBinding.btnDeleteProfile.visibility = View.VISIBLE
                        itemBinding.btnDeleteProfile.setOnClickListener {
                            handleDeleteWorkspace(profile)
                        }
                    } else {
                        itemBinding.btnDeleteProfile.visibility = View.GONE
                    }

                    itemBinding.btnSwitchProfile.visibility = View.VISIBLE
                    if (item.isActive) {
                        itemBinding.btnSwitchProfile.text = "✓ ACTIVE"
                        itemBinding.btnSwitchProfile.setBackgroundResource(R.drawable.bg_ocean_pill_btn)
                        val onActiveClick = View.OnClickListener {
                            Toast.makeText(ctx, "Currently in ${profile.name}'s workspace", Toast.LENGTH_SHORT).show()
                        }
                        itemBinding.cardProfileSlot.setOnClickListener(onActiveClick)
                        itemBinding.btnSwitchProfile.setOnClickListener(onActiveClick)
                    } else {
                        itemBinding.btnSwitchProfile.text = "SWITCH"
                        itemBinding.btnSwitchProfile.setBackgroundResource(R.drawable.bg_ocean_pill_btn)
                        val onSwitchClick = View.OnClickListener {
                            handleSwitchWorkspace(profile)
                        }
                        itemBinding.cardProfileSlot.setOnClickListener(onSwitchClick)
                        itemBinding.btnSwitchProfile.setOnClickListener(onSwitchClick)
                    }
                } else {
                    itemBinding.cardProfileSlot.strokeColor = strokeColor
                    itemBinding.cardProfileSlot.strokeWidth = 2

                    itemBinding.tvProfileName.text = "+ Set Up Profile Slot ${item.slotNumber}"
                    itemBinding.tvProfileEmail.text = "Tap to initialize isolated workspace"
                    itemBinding.tvProfileDept.text = "Available workspace slot"

                    itemBinding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                    itemBinding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#475569"))

                    itemBinding.btnDeleteProfile.visibility = View.GONE
                    itemBinding.btnEditProfile.visibility = View.GONE
                    itemBinding.btnSwitchProfile.visibility = View.GONE

                    itemBinding.cardProfileSlot.setOnClickListener {
                        showCreateDialog(item.slotNumber)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "WorkspaceProfilesBottomSheet"

        fun newInstance(onProfileSwitched: (() -> Unit)? = null): WorkspaceProfilesBottomSheet {
            return WorkspaceProfilesBottomSheet().apply {
                this.onProfileSwitched = onProfileSwitched
            }
        }
    }
}
