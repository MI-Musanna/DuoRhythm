package com.duorhythm.app.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class BiometricPrompt(
    private val activity: FragmentActivity,
    private val executor: Executor,
    private val callback: AuthenticationCallback
) {
    open class AuthenticationCallback {
        open fun onAuthenticationSucceeded(result: AuthenticationResult) {}
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        open fun onAuthenticationFailed() {}
    }

    class AuthenticationResult

    class PromptInfo internal constructor(
        val title: CharSequence,
        val subtitle: CharSequence?,
        val allowedAuthenticators: Int
    ) {
        class Builder {
            private var title: CharSequence = ""
            private var subtitle: CharSequence? = null
            private var allowedAuthenticators: Int = 0

            fun setTitle(title: CharSequence): Builder {
                this.title = title
                return this
            }

            fun setSubtitle(subtitle: CharSequence?): Builder {
                this.subtitle = subtitle
                return this
            }

            fun setAllowedAuthenticators(authenticators: Int): Builder {
                this.allowedAuthenticators = authenticators
                return this
            }

            fun build(): PromptInfo = PromptInfo(title, subtitle, allowedAuthenticators)
        }
    }

    companion object {
        const val ERROR_HW_UNAVAILABLE = 1
        const val ERROR_UNABLE_TO_PROCESS = 2
        const val ERROR_TIMEOUT = 3
        const val ERROR_NO_SPACE = 4
        const val ERROR_CANCELED = 5
        const val ERROR_LOCKOUT = 7
        const val ERROR_LOCKOUT_PERMANENT = 9
        const val ERROR_USER_CANCELED = 10
        const val ERROR_NO_BIOMETRICS = 11
        const val ERROR_HW_NOT_PRESENT = 12
        const val ERROR_NEGATIVE_BUTTON = 13
        const val ERROR_NO_DEVICE_CREDENTIAL = 14
    }

    fun authenticate(promptInfo: PromptInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()

                val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(promptInfo.title)
                if (!promptInfo.subtitle.isNullOrEmpty()) {
                    builder.setSubtitle(promptInfo.subtitle)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                } else {
                    builder.setNegativeButton("Use Password/PIN", executor) { _, _ ->
                        executor.execute {
                            callback.onAuthenticationError(ERROR_NEGATIVE_BUTTON, "Negative button pressed")
                        }
                    }
                }

                val prompt = builder.build()
                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            executor.execute {
                                callback.onAuthenticationSucceeded(AuthenticationResult())
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            executor.execute {
                                callback.onAuthenticationError(errorCode, errString ?: "")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            executor.execute {
                                callback.onAuthenticationFailed()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                executor.execute {
                    callback.onAuthenticationError(ERROR_USER_CANCELED, e.message ?: "Authentication error")
                }
            }
        } else {
            executor.execute {
                callback.onAuthenticationError(ERROR_HW_NOT_PRESENT, "Biometrics not supported on this device")
            }
        }
    }
}

object BiometricManager {
    object Authenticators {
        const val BIOMETRIC_STRONG = 0x000F
        const val BIOMETRIC_WEAK = 0x00FF
        const val DEVICE_CREDENTIAL = 1 shl 15
    }
}

object BiometricHelper {
    fun showBiometricOrDeviceLock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFallbackToPassword: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || 
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        // User chose fallback
                        onFallbackToPassword()
                    } else {
                        onFallbackToPassword()
                    }
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
