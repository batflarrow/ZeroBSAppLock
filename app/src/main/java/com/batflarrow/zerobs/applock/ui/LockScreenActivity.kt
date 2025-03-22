package com.batflarrow.zerobs.applock.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.batflarrow.zerobs.applock.auth.BiometricAuthHelper
import com.batflarrow.zerobs.applock.service.AppLockAccessibilityService
import com.batflarrow.zerobs.applock.ui.theme.ZeroBSAppLockTheme

class LockScreenActivity : FragmentActivity() {

    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private var packageName: String = ""
    private var appName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the package name and app name from the intent
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Unknown App"

        if (packageName.isEmpty()) {
            Log.e("LockScreenActivity", "No package name provided")
            finish()
            return
        }

        biometricAuthHelper = BiometricAuthHelper(this)
        biometricAuthHelper.registerForActivityResult(this)

        // Try to authenticate immediately if possible
        if (biometricAuthHelper.canAuthenticate()) {
            authenticateUser()
        }

        setContent {
            ZeroBSAppLockTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    LockScreenContent(
                            appName = appName,
                            onAuthenticateClick = { authenticateUser() }
                    )
                }
            }
        }
    }

    private fun authenticateUser() {
        biometricAuthHelper.showBiometricPrompt(
                activity = this,
                title = "Authenticate to unlock",
                subtitle = "Unlock $appName",
                onSuccess = { finishAndReturnToApp(packageName) }
        )
    }

    private fun finishAndReturnToApp(packageName: String) {
        try {
            // Tell the accessibility service this app is now authenticated
            val accessibilityService = AppLockAccessibilityService.instance
            accessibilityService?.setCurrentLockedApp(packageName)

            // We need to explicitly launch the app again to ensure it opens
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                startActivity(launchIntent)
            } else {
                Log.e("LockScreenActivity", "Could not find launch intent for: $packageName")
            }
        } catch (e: Exception) {
            Log.e("LockScreenActivity", "Error returning to app: $packageName", e)
        }

        // Finish our activity with a short delay to ensure the other app launches first
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 100)
    }

    @Composable
    fun LockScreenContent(appName: String, onAuthenticateClick: () -> Unit) {
        Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Icon",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = "App Locked",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                    text = appName,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onAuthenticateClick, modifier = Modifier.padding(16.dp)) {
                Text("Authenticate to Unlock")
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
    }
}
