package com.chrisalvis.rotato.ui

import android.app.KeyguardManager
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    /**
     * Shows a biometric / device-credential prompt.
     *
     * [onSuccess] is called on the main thread when the user authenticates successfully.
     * [onUnavailable] is called only when the device has no screen lock at all, so there is
     * nothing to authenticate against; it defaults to failing open so the feature doesn't
     * permanently block those users. Any other problem (hardware busy, security update
     * required) keeps the collection locked.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock collection",
        subtitle: String = "Use biometrics or device PIN / pattern / password",
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit = onSuccess
    ) {
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL isn't supported below API 30 and reports an
        // error there, which used to unlock the collection without any prompt.
        val authenticators =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BiometricManager.Authenticators.BIOMETRIC_STRONG
             else BiometricManager.Authenticators.BIOMETRIC_WEAK) or
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
            else -> {
                val keyguard = activity.getSystemService(KeyguardManager::class.java)
                if (keyguard?.isDeviceSecure == false) onUnavailable()
                else Toast.makeText(activity, "Couldn't open the unlock prompt. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
