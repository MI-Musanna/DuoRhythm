package com.duorhythm.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast

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
                val prompt = BiometricPrompt.Builder(activity)
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
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
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
