package com.duorhythm.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun isBiometricsAvailable(activity: Activity): Boolean {
        val defaultPrefs = activity.getSharedPreferences(activity.packageName + "_preferences", Context.MODE_PRIVATE)
        val lifesyncPrefs = activity.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val duorhythmPrefs = activity.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = defaultPrefs.getBoolean("pref_biometric_enabled", false) ||
                lifesyncPrefs.getBoolean("key_biometric_lock_enabled", false) ||
                lifesyncPrefs.getBoolean("pref_biometric_enabled", false) ||
                duorhythmPrefs.getBoolean("pref_biometric_enabled", false)

        if (!isBiometricEnabled) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = activity.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            if (bm != null) {
                val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bm.canAuthenticate(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                } else {
                    @Suppress("DEPRECATION")
                    bm.canAuthenticate()
                }
                if (canAuth == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                    return true
                }
            }
        }
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure == true
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)
            canAuth == BiometricManager.BIOMETRIC_SUCCESS ||
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun promptBiometricAuth(
        activity: FragmentActivity,
        title: String = "Confirm Identity",
        subtitle: String = "Scan fingerprint to authorize profile switch",
        onSuccess: () -> Unit,
        onErrorOrFallback: () -> Unit
    ) {
        if (!isBiometricAvailable(activity)) {
            onErrorOrFallback()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onErrorOrFallback()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN / Password")
            .build()

        prompt.authenticate(promptInfo)
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use Password/PIN",
        onSuccess: () -> Unit,
        onFallbackToPassword: () -> Unit
    ) {
        if (!isBiometricsAvailable(activity)) {
            onFallbackToPassword()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(negativeButtonText, activity.mainExecutor) { _, _ ->
                        cancellationSignal.cancel()
                        activity.runOnUiThread {
                            onFallbackToPassword()
                        }
                    }
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            activity.runOnUiThread {
                                onSuccess()
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            activity.runOnUiThread {
                                onFallbackToPassword()
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Toast.makeText(activity, "Biometric unrecognized. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                onFallbackToPassword()
            }
        } else {
            onFallbackToPassword()
        }
    }
}
