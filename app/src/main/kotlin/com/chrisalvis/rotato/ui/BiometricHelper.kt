package com.chrisalvis.rotato.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    /**
     * Shows a biometric / device-credential prompt.
     *
     * [onSuccess] is called on the main thread when the user authenticates successfully.
     * [onUnavailable] is called when no authentication method is enrolled — defaults to
     * failing open so the feature doesn't permanently block users on devices with no biometrics.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock collection",
        subtitle: String = "Use biometrics or device PIN / pattern / password",
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit = onSuccess
    ) {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()

                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) = onSuccess()

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            // User cancelled or hardware error — stay locked, no-op
                        }

                        override fun onAuthenticationFailed() {
                            // Finger/face didn't match — BiometricPrompt shows its own feedback
                        }
                    }
                ).authenticate(promptInfo)
            }
            else -> onUnavailable()
        }
    }
}
