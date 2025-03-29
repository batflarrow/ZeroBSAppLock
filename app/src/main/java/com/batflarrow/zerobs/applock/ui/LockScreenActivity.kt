package com.batflarrow.zerobs.applock.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
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
    private var hasOverlayPermission = false

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

        // Check and request overlay permission if needed
        hasOverlayPermission = Settings.canDrawOverlays(this)
        if (!hasOverlayPermission) {
            promptForOverlayPermission()
            return
        }
        // Configure the window to appear as an overlay
        window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        // For overlay windows
        window.attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        // Handle status bar and system UI
        window.decorView.post {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        // Show when locked
        setShowWhenLocked(true)
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

    private fun promptForOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        startActivity(intent)
        // Just finish this instance; user needs to grant permission first
        finish()
    }

    private fun authenticateUser() {
        biometricAuthHelper.showBiometricPrompt(
                activity = this,
                title = "Authenticate to unlock",
                subtitle = "Unlock $appName",
                onSuccess = { onAuthenticationSuccess() }
        )
    }

    private fun onAuthenticationSuccess() {
        val accessibilityService = AppLockAccessibilityService.instance
        accessibilityService?.setCurrentLockedApp(packageName)
        finishAffinity()
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
