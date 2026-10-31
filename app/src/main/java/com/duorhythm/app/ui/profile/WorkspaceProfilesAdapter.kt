package com.duorhythm.app.ui.profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.databinding.ItemWorkspaceProfileBinding
import com.duorhythm.app.security.BiometricHelper
import database.entities.ProfileEntity

data class WorkspaceSlotItem(
    val slotNumber: Int,
    val profile: ProfileEntity?,
    val isActive: Boolean
)

class WorkspaceProfilesAdapter(
    private val activity: FragmentActivity,
    private val items: List<WorkspaceSlotItem>,
    private val onEditClick: ((ProfileEntity) -> Unit)? = null,
    private val onDeleteClick: ((ProfileEntity) -> Unit)? = null,
    private val onCreateClick: ((Int) -> Unit)? = null,
    private val onDismiss: (() -> Unit)? = null
) : RecyclerView.Adapter<WorkspaceProfilesAdapter.SlotViewHolder>() {

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

    inner class SlotViewHolder(val binding: ItemWorkspaceProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val btnSwitch = binding.btnSwitchProfile

        fun bind(item: WorkspaceSlotItem) {
            val ctx = binding.root.context
            val primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            val strokeColor = ContextCompat.getColor(ctx, R.color.card_stroke)

            binding.tvSlotTag.text = "SLOT ${item.slotNumber}"
            binding.ivUserAvatar.setImageDrawable(null)
            binding.ivUserAvatar.setImageBitmap(null)

            if (item.profile != null) {
                val profile = item.profile
                binding.cardProfileSlot.strokeColor = if (item.isActive) primaryColor else strokeColor
                binding.cardProfileSlot.strokeWidth = if (item.isActive) 4 else 2

                binding.tvProfileName.text = profile.name.ifBlank { "Profile ${item.slotNumber}" }
                if (profile.email.isNotBlank()) {
                    binding.tvProfileEmail.visibility = View.VISIBLE
                    binding.tvProfileEmail.text = profile.email
                } else {
                    binding.tvProfileEmail.visibility = View.GONE
                }

                val meta = listOf(profile.department, profile.semester, profile.institution)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                binding.tvProfileDept.text = if (meta.isNotBlank()) meta else "Workspace Slot ${item.slotNumber}"

                val avatarUriString = profile.avatarUri
                if (!avatarUriString.isNullOrBlank()) {
                    try {
                        binding.ivUserAvatar.setImageURI(Uri.parse(avatarUriString))
                        binding.ivUserAvatar.imageTintList = null
                    } catch (e: Exception) {
                        binding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                        binding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                    }
                } else {
                    binding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                    binding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                }

                binding.btnEditProfile.visibility = View.VISIBLE
                binding.btnEditProfile.setOnClickListener {
                    onEditClick?.invoke(profile)
                }

                if (!item.isActive) {
                    binding.btnDeleteProfile.visibility = View.VISIBLE
                    binding.btnDeleteProfile.setOnClickListener {
                        onDeleteClick?.invoke(profile)
                    }
                } else {
                    binding.btnDeleteProfile.visibility = View.GONE
                }

                binding.btnSwitchProfile.visibility = View.VISIBLE
                if (item.isActive) {
                    binding.btnSwitchProfile.text = "✓ ACTIVE"
                    binding.btnSwitchProfile.setBackgroundResource(R.drawable.bg_ocean_pill_btn)
                    val onActiveClick = View.OnClickListener {
                        Toast.makeText(ctx, "Currently in ${profile.name}'s workspace", Toast.LENGTH_SHORT).show()
                    }
                    binding.cardProfileSlot.setOnClickListener(onActiveClick)
                    binding.btnSwitchProfile.setOnClickListener(onActiveClick)
                } else {
                    binding.btnSwitchProfile.text = "SWITCH"
                    binding.btnSwitchProfile.setBackgroundResource(R.drawable.bg_ocean_pill_btn)

                    btnSwitch.setOnClickListener {
                        val targetSlot = profile.slot
                        BiometricHelper.showBiometricOrDeviceLock(
                            activity = activity,
                            title = "Authenticate Workspace",
                            subtitle = "Confirm fingerprint or device PIN to switch to ${profile.name}",
                            onSuccess = {
                                ProfileManager.switchProfile(activity, targetSlot)
                                onDismiss?.invoke()
                                activity.recreate()
                            },
                            onFallbackToPassword = {
                                // ONLY if device credential / biometrics is cancelled or unavailable
                                val dialog = VerifyCredentialsDialog.newInstance(targetSlot, profile.name)
                                dialog.onVerificationSuccess = {
                                    ProfileManager.switchProfile(activity, targetSlot)
                                    onDismiss?.invoke()
                                    activity.recreate()
                                }
                                dialog.show(activity.supportFragmentManager, "VERIFY_DIALOG")
                            }
                        )
                    }
                    binding.cardProfileSlot.setOnClickListener {
                        btnSwitch.performClick()
                    }
                }
            } else {
                binding.cardProfileSlot.strokeColor = strokeColor
                binding.cardProfileSlot.strokeWidth = 2

                binding.tvProfileName.text = "+ Set Up Profile Slot ${item.slotNumber}"
                binding.tvProfileEmail.text = "Tap to initialize isolated workspace"
                binding.tvProfileDept.text = "Available workspace slot"

                binding.ivUserAvatar.setImageResource(R.drawable.ic_default_profile)
                binding.ivUserAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#475569"))

                binding.btnDeleteProfile.visibility = View.GONE
                binding.btnEditProfile.visibility = View.GONE
                binding.btnSwitchProfile.visibility = View.GONE

                binding.cardProfileSlot.setOnClickListener {
                    onCreateClick?.invoke(item.slotNumber)
                }
            }
        }
    }
}
