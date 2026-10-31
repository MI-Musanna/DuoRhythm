package com.duorhythm.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.local.pref.UserProfileManager
import com.duorhythm.app.databinding.ActivityOnboardingBinding
import com.duorhythm.app.databinding.ItemOnboardingFeaturesBinding
import com.duorhythm.app.databinding.ItemOnboardingLaunchBinding
import com.duorhythm.app.databinding.ItemOnboardingPermissionsBinding
import com.duorhythm.app.databinding.ItemOnboardingProfileBinding
import com.duorhythm.app.databinding.ItemOnboardingWelcomeBinding
import com.duorhythm.app.util.AnimationUtils
import database.entities.ProfileEntity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val dbHelper by lazy { DatabaseHelper.getInstance(this) }

    // Page 3 permission references
    private var permissionsBinding: ItemOnboardingPermissionsBinding? = null

    // Page 4 profile form references & state
    private var profileBinding: ItemOnboardingProfileBinding? = null
    private var selectedAvatarUri: String? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updateNotificationPermissionUI(isGranted)
            if (isGranted) {
                Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updateStoragePermissionUI(isGranted)
            if (isGranted) {
                Toast.makeText(this, "Storage access granted!", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val savedPath = UserProfileManager.saveAvatarFromUri(this, it)
                if (savedPath != null) {
                    selectedAvatarUri = savedPath
                    updateProfileAvatarPreview(savedPath)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupViewPager()
        setupNavigationActions()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOnboarding) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerOnboarding.setPadding(
                binding.headerOnboarding.paddingLeft,
                systemBars.top + 8,
                binding.headerOnboarding.paddingRight,
                binding.headerOnboarding.paddingBottom
            )
            binding.dockOnboarding.setPadding(
                binding.dockOnboarding.paddingLeft,
                binding.dockOnboarding.paddingTop,
                binding.dockOnboarding.paddingRight,
                systemBars.bottom + 12
            )
            insets
        }
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter()
        binding.viewPagerOnboarding.adapter = adapter
        binding.viewPagerOnboarding.offscreenPageLimit = 4

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateStepUI(position)
            }
        })
    }

    private fun updateStepUI(position: Int) {
        binding.tvOnboardingStep.text = "STEP ${position + 1} OF 5"

        // Skip is available on pages 0, 1, 2 (jumps directly to Profile Setup page 3)
        binding.btnOnboardingSkip.visibility = if (position < 3) View.VISIBLE else View.GONE
        binding.btnOnboardingBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE

        binding.btnOnboardingNext.text = if (position == 4) "GET STARTED" else "Next"

        // Update Dots
        val activeDrawable = ContextCompat.getDrawable(this, R.drawable.bg_ocean_pill_btn)
        val inactiveDrawable = ContextCompat.getDrawable(this, R.drawable.bg_glass_squircle_action)

        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5)
        for (i in dots.indices) {
            val dot = dots[i]
            val isActive = (i == position)
            dot.background = if (isActive) activeDrawable else inactiveDrawable
            val lp = dot.layoutParams
            lp.width = if (isActive) (18 * resources.displayMetrics.density).toInt() else (6 * resources.displayMetrics.density).toInt()
            dot.layoutParams = lp
        }

        // If on permissions page, ensure both states are updated
        if (position == 2) {
            checkAndRefreshNotificationState()
            checkAndRefreshStorageState()
        }
    }

    private fun setupNavigationActions() {
        binding.btnOnboardingSkip.setOnClickListener {
            binding.viewPagerOnboarding.currentItem = 3
        }

        binding.btnOnboardingBack.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current > 0) {
                binding.viewPagerOnboarding.currentItem = current - 1
            }
        }

        binding.btnOnboardingNext.setOnClickListener {
            val current = binding.viewPagerOnboarding.currentItem
            if (current == 3) {
                // Validate Profile Form before proceeding
                if (validateProfileForm()) {
                    saveProfile1Data()
                    binding.viewPagerOnboarding.currentItem = 4
                }
            } else if (current == 4) {
                saveProfileAndComplete()
            } else {
                binding.viewPagerOnboarding.currentItem = current + 1
            }
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnOnboardingSkip,
            binding.btnOnboardingBack,
            binding.btnOnboardingNext
        )
    }

    private fun checkAndRefreshNotificationState() {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        updateNotificationPermissionUI(isGranted)
    }

    private fun updateNotificationPermissionUI(isGranted: Boolean) {
        val b = permissionsBinding ?: return
        if (isGranted) {
            b.btnGrantNotifications.text = "NOTIFICATIONS ENABLED ✓"
            b.btnGrantNotifications.isEnabled = false
            b.ivCheckNotification.visibility = View.VISIBLE
        } else {
            b.btnGrantNotifications.text = "ENABLE NOTIFICATIONS"
            b.btnGrantNotifications.isEnabled = true
            b.ivCheckNotification.visibility = View.GONE
        }
    }

    private fun checkAndRefreshStorageState() {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        updateStoragePermissionUI(isGranted)
    }

    private fun updateStoragePermissionUI(isGranted: Boolean) {
        val b = permissionsBinding ?: return
        if (isGranted) {
            b.btnGrantStorage.text = "ACCESS GRANTED ✓"
            b.btnGrantStorage.isEnabled = false
            b.ivCheckStorage.visibility = View.VISIBLE
        } else {
            b.btnGrantStorage.text = "GRANT ACCESS"
            b.btnGrantStorage.isEnabled = true
            b.ivCheckStorage.visibility = View.GONE
        }
    }

    private fun validateProfileForm(): Boolean {
        val b = profileBinding ?: return true
        val name = b.etOnboardingName.text?.toString()?.trim() ?: ""

        if (name.isBlank()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
            b.etOnboardingName.requestFocus()
            return false
        }
        return true
    }

    private fun saveProfile1Data(): Long {
        val b = profileBinding ?: return 1L
        val name = b.etOnboardingName.text?.toString()?.trim() ?: "Scholar"
        val email = ""
        val password = b.etOnboardingPassword.text?.toString().orEmpty()
        val inst = b.etOnboardingInstitution.text?.toString()?.trim() ?: ""
        val dept = b.etOnboardingDepartment.text?.toString()?.trim() ?: ""
        val sem = b.etOnboardingSemester.text?.toString()?.trim() ?: "1st"
        val remember = false

        val existingSlot1 = dbHelper.profileDao.getProfileBySlot(1)
        val profile = ProfileEntity(
            id = existingSlot1?.id ?: 0L,
            slotNumber = 1,
            name = name,
            email = email,
            passwordHash = if (password.isNotBlank()) ProfileManager.hashPassword(password) else "",
            institution = inst,
            department = dept,
            semester = sem,
            avatarUri = selectedAvatarUri ?: existingSlot1?.avatarUri,
            rememberEmail = remember
        )

        val id = if (existingSlot1 != null) {
            dbHelper.profileDao.updateProfile(profile)
            existingSlot1.id
        } else {
            dbHelper.profileDao.insertProfile(profile)
        }

        ProfileManager.setActiveProfileId(this, id)
        ProfileManager.syncUserProfile(this, profile.copy(id = id))
        return id
    }

    private fun saveProfileAndComplete() {
        validateProfileForm()
        saveProfile1Data()

        ProfileManager.setOnboardingCompleted(this, true)
        Toast.makeText(this, "Welcome to DuoRhythm!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun updateProfileAvatarPreview(path: String) {
        val b = profileBinding ?: return
        val bitmap = UserProfileManager.loadCircularAvatarBitmap(this, 128)
        if (bitmap != null) {
            b.ivOnboardingAvatar.imageTintList = null
            b.ivOnboardingAvatar.setImageBitmap(bitmap)
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // Pager Adapter
    // ==========================================
    inner class OnboardingPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = 5

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> {
                    val binding = ItemOnboardingWelcomeBinding.inflate(inflater, parent, false)
                    WelcomeViewHolder(binding)
                }
                1 -> {
                    val binding = ItemOnboardingFeaturesBinding.inflate(inflater, parent, false)
                    FeaturesViewHolder(binding)
                }
                2 -> {
                    val binding = ItemOnboardingPermissionsBinding.inflate(inflater, parent, false)
                    permissionsBinding = binding
                    PermissionsViewHolder(binding)
                }
                3 -> {
                    val binding = ItemOnboardingProfileBinding.inflate(inflater, parent, false)
                    profileBinding = binding
                    ProfileViewHolder(binding)
                }
                else -> {
                    val binding = ItemOnboardingLaunchBinding.inflate(inflater, parent, false)
                    LaunchViewHolder(binding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is PermissionsViewHolder -> holder.bind()
                is ProfileViewHolder -> holder.bind()
                is LaunchViewHolder -> holder.bind()
            }
        }
    }

    inner class WelcomeViewHolder(binding: ItemOnboardingWelcomeBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class FeaturesViewHolder(binding: ItemOnboardingFeaturesBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class PermissionsViewHolder(val binding: ItemOnboardingPermissionsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            checkAndRefreshNotificationState()
            checkAndRefreshStorageState()

            binding.btnGrantNotifications.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    updateNotificationPermissionUI(true)
                }
            }

            binding.btnGrantStorage.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestStoragePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            AnimationUtils.attachTactileFeedback(
                binding.btnGrantNotifications,
                binding.btnGrantStorage
            )
        }
    }

    inner class ProfileViewHolder(val binding: ItemOnboardingProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            // Pre-fill existing defaults if available
            val currentName = UserProfileManager.getUserName(this@OnboardingActivity)
            if (currentName.isNotBlank() && binding.etOnboardingName.text.isNullOrBlank()) {
                binding.etOnboardingName.setText(currentName)
            }
            val currentInst = UserProfileManager.getInstitutionName(this@OnboardingActivity)
            if (currentInst.isNotBlank() && binding.etOnboardingInstitution.text.isNullOrBlank()) {
                binding.etOnboardingInstitution.setText(currentInst)
            }
            val currentFaculty = UserProfileManager.getAcademicFaculty(this@OnboardingActivity)
            if (currentFaculty.isNotBlank() && binding.etOnboardingDepartment.text.isNullOrBlank()) {
                binding.etOnboardingDepartment.setText(currentFaculty)
            }

            binding.containerOnboardingAvatar.setOnClickListener {
                pickAvatarLauncher.launch("image/*")
            }
        }
    }

    inner class LaunchViewHolder(val binding: ItemOnboardingLaunchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.btnGitHubStar.setOnClickListener {
                openExternalUrl("https://github.com/MI-Musanna")
            }
            binding.btnContact.setOnClickListener {
                openExternalUrl("https://taplink.cc/mi2007")
            }
            AnimationUtils.attachTactileFeedback(
                binding.btnGitHubStar,
                binding.btnContact
            )
        }
    }
}
