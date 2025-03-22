package com.batflarrow.zerobs.applock.auth

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthHelper(private val context: Context) {

        private val biometricManager = BiometricManager.from(context)

        // Store the success callback to use with credential fallback
        private var onSuccessCallback: (() -> Unit)? = null

        // Credential authentication launcher
        private var credentialLauncher: ActivityResultLauncher<Intent>? = null

        // Initialize the credential launcher
        fun registerForActivityResult(activity: FragmentActivity) {
                credentialLauncher =
                        activity.registerForActivityResult(
                                ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                                if (result.resultCode == FragmentActivity.RESULT_OK) {
                                        Log.d(TAG, "Device credential authentication succeeded")
                                        onSuccessCallback?.invoke()
                                } else {
                                        Log.d(
                                                TAG,
                                                "Device credential authentication failed or was canceled"
                                        )
                                }
                        }
        }

        fun canAuthenticate(): Boolean {
                val result =
                        biometricManager.canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                Log.d(TAG, "Can authenticate result: $result")
                return result == BiometricManager.BIOMETRIC_SUCCESS
        }

        fun showBiometricPrompt(
                activity: FragmentActivity,
                title: String,
                subtitle: String,
                onSuccess: () -> Unit
        ) {
                // Store callback for potential fallback
                this.onSuccessCallback = onSuccess

                val executor = ContextCompat.getMainExecutor(context)

                val callback =
                        object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult
                                ) {
                                        super.onAuthenticationSucceeded(result)
                                        Log.d(TAG, "Biometric authentication succeeded!")
                                        onSuccess()
                                }

                                override fun onAuthenticationError(
                                        errorCode: Int,
                                        errString: CharSequence
                                ) {
                                        super.onAuthenticationError(errorCode, errString)
                                        Log.e(TAG, "Authentication error: $errorCode, $errString")

                                        // Handle lockout errors by falling back to device
                                        // credentials
                                        if (errorCode == BIOMETRIC_ERROR_LOCKOUT ||
                                                        errorCode ==
                                                                BIOMETRIC_ERROR_LOCKOUT_PERMANENT
                                        ) {
                                                Log.d(
                                                        TAG,
                                                        "Biometric locked out, falling back to device credentials"
                                                )
                                                launchDeviceCredentialAuth()
                                        }
                                }

                                override fun onAuthenticationFailed() {
                                        super.onAuthenticationFailed()
                                        Log.w(TAG, "Authentication failed")
                                }
                        }

                val biometricPrompt = BiometricPrompt(activity, executor, callback)

                // Configure the prompt with appropriate settings
                val promptInfoBuilder =
                        BiometricPrompt.PromptInfo.Builder()
                                .setTitle(title)
                                .setSubtitle(subtitle)
                                .setAllowedAuthenticators(
                                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                )
                                .setNegativeButtonText("Cancel")

                // Skip confirmation step after successful biometric authentication
                promptInfoBuilder.setConfirmationRequired(false)

                val promptInfo = promptInfoBuilder.build()
                Log.d(TAG, "Launching biometric prompt")

                biometricPrompt.authenticate(promptInfo)
        }

        private fun launchDeviceCredentialAuth() {
                val keyguardManager =
                        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

                if (!keyguardManager.isDeviceSecure) {
                        Log.w(TAG, "Device doesn't have screen lock set up")
                        return
                }

                try {
                        // Use the modern approach with BiometricManager
                        val intent =
                                Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                        putExtra(
                                                android.provider.Settings
                                                        .EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                        )
                                }

                        credentialLauncher?.launch(intent)
                                ?: run {
                                        Log.e(
                                                TAG,
                                                "Credential launcher not initialized. Call registerForActivityResult first."
                                        )
                                }
                } catch (e: Exception) {
                        Log.e(TAG, "Error launching device credential: ${e.message}")
                }
        }

        companion object {
                private const val TAG = "BiometricAuthHelper"

                // Error code for too many failed attempts
                private const val BIOMETRIC_ERROR_LOCKOUT = 9
                private const val BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 10
        }
}
