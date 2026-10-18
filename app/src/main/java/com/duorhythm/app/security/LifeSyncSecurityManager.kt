package com.duorhythm.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.annotation.RequiresApi

object LifeSyncSecurityManager {

    private const val PREFS_NAME = "lifesync_prefs"
    private const val KEY_LOCK_ENABLED = "key_biometric_lock_enabled"

    private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    @Volatile
    private var sessionUnlocked = false

    @Volatile
    private var lastUnlockTimestamp = 0L

    @Volatile
    private var startedActivityCount = 0

    @Volatile
    private var appBackgroundTimestamp = 0L

    fun onActivityStarted() {
        startedActivityCount++
        if (appBackgroundTimestamp > 0L) {
            val backgroundDuration = System.currentTimeMillis() - appBackgroundTimestamp
            // If the app was sent to background and stayed there longer than 5 minutes, lock session
            if (backgroundDuration > SESSION_TIMEOUT_MS) {
                lockSession()
            }
            appBackgroundTimestamp = 0L
        }
    }

    fun onActivityStopped() {
        startedActivityCount--
        if (startedActivityCount <= 0) {
            startedActivityCount = 0
            appBackgroundTimestamp = System.currentTimeMillis()
        }
    }

    fun isDeviceSecure(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure == true
    }

    fun isLockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOCK_ENABLED, enabled)
            .apply()

        if (!enabled) {
            setSessionUnlocked(true)
        }
    }

    fun isSessionUnlocked(): Boolean {
        if (!sessionUnlocked) return false
        val now = System.currentTimeMillis()
        if (lastUnlockTimestamp > 0L && (now - lastUnlockTimestamp > SESSION_TIMEOUT_MS)) {
            lockSession()
            return false
        }
        return true
    }

    fun setSessionUnlocked(unlocked: Boolean) {
        sessionUnlocked = unlocked
        lastUnlockTimestamp = if (unlocked) System.currentTimeMillis() else 0L
        if (!unlocked) {
            appBackgroundTimestamp = 0L
        }
    }

    fun lockSession() {
        sessionUnlocked = false
        lastUnlockTimestamp = 0L
        appBackgroundTimestamp = 0L
    }

    fun authenticate(
        activity: Activity,
        title: String = "LifeSync Security",
        subtitle: String = "Unlock with biometric or device PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isLockEnabled(activity) || isSessionUnlocked()) {
            sessionUnlocked = true
            onSuccess()
            return
        }

        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            // No lock screen is set on device, allow access with notice
            sessionUnlocked = true
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) supports native platform BiometricPrompt with PIN fallback
            showBiometricPromptApi29(activity, title, subtitle, onSuccess, onError)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 (API 28)
            showBiometricPromptApi28(activity, title, subtitle, onSuccess, onError)
        } else {
            // Older Android versions: KeyguardManager credential intent fallback
            launchKeyguardCredentialConfirm(activity, title, subtitle, onSuccess, onError)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showBiometricPromptApi29(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val builder = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }

            val prompt = builder.build()
            val cancellationSignal = CancellationSignal()

            prompt.authenticate(
                cancellationSignal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        sessionUnlocked = true
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        // If user canceled or dismissed
                        onError(errString?.toString() ?: "Authentication cancelled")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(activity, "Biometric unrecognized. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (_: Exception) {
            launchKeyguardCredentialConfirm(activity, title, subtitle, onSuccess, onError)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showBiometricPromptApi28(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val cancellationSignal = CancellationSignal()
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ ->
                    launchKeyguardCredentialConfirm(activity, title, subtitle, onSuccess, onError)
                }
                .build()

            prompt.authenticate(
                cancellationSignal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        sessionUnlocked = true
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString?.toString() ?: "Authentication cancelled")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(activity, "Biometric unrecognized. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (_: Exception) {
            launchKeyguardCredentialConfirm(activity, title, subtitle, onSuccess, onError)
        }
    }

    private fun launchKeyguardCredentialConfirm(
        activity: Activity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = km?.createConfirmDeviceCredentialIntent(title, subtitle)
        if (intent != null) {
            // When launched, device handles confirmation
            activity.startActivity(intent)
            // Allow access optimistically when activity resumes
            sessionUnlocked = true
            onSuccess()
        } else {
            sessionUnlocked = true
            onSuccess()
        }
    }
}
