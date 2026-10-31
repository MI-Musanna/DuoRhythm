package com.duorhythm.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.duorhythm.app.MainActivity
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.local.pref.UserProfileManager
import com.duorhythm.app.databinding.ActivityOnboardingBinding
import com.duorhythm.app.util.AnimationUtils

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var selectedAvatarUriString: String? = null

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val savedPath = UserProfileManager.saveAvatarFromUri(this, it)
                selectedAvatarUriString = savedPath ?: it.toString()
                try {
                    val bitmap = UserProfileManager.loadCircularAvatarBitmap(this, 128)
                    if (bitmap != null) {
                        binding.ivAvatarPreview.imageTintList = null
                        binding.ivAvatarPreview.setImageBitmap(bitmap)
                    } else {
                        binding.ivAvatarPreview.setImageURI(it)
                    }
                } catch (_: Exception) {
                    binding.ivAvatarPreview.setImageURI(it)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAvatarPicker()
        setupValidation()
        setupCompletion()
    }

    private fun setupAvatarPicker() {
        binding.ivAvatarPreview.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
        binding.btnChooseAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }
    }

    fun validateInputs() {
        val nameValid = binding.etUserName.text?.toString()?.trim().orEmpty().length >= 2
        val passwordValid = binding.etUserPassword.text?.toString()?.trim().orEmpty().length >= 4
        val isReady = nameValid && passwordValid

        binding.btnCompleteOnboarding.isEnabled = isReady
        binding.btnCompleteOnboarding.alpha = if (isReady) 1.0f else 0.5f
    }

    private fun setupValidation() {
        validateInputs()
        binding.etUserName.doAfterTextChanged { validateInputs() }
        binding.etUserPassword.doAfterTextChanged { validateInputs() }
    }

    private fun setupCompletion() {
        AnimationUtils.attachTactileFeedback(binding.btnCompleteOnboarding, binding.btnChooseAvatar)

        binding.btnCompleteOnboarding.setOnClickListener {
            val name = binding.etUserName.text.toString().trim()
            val password = binding.etUserPassword.text.toString().trim()
            val avatarPath = selectedAvatarUriString ?: ""

            ProfileManager.createInitialProfile(this, name = name, password = password, avatarUri = avatarPath)
            val prefs = getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("pref_onboarding_completed", true).apply()
            getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE).edit().putBoolean("pref_onboarding_completed", true).apply()

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
