package com.batflarrow.zerobs.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.batflarrow.zerobs.applock.auth.BiometricAuthHelper
import com.batflarrow.zerobs.applock.service.AppLockAccessibilityService
import com.batflarrow.zerobs.applock.ui.screens.AppListScreen
import com.batflarrow.zerobs.applock.ui.theme.ZeroBSAppLockTheme

class MainActivity : FragmentActivity() {
    private var accessibilityDialog: AlertDialog? = null
    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricAuthHelper = BiometricAuthHelper(this)
        biometricAuthHelper.registerForActivityResult(this)

        setContent {
            ZeroBSAppLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isAuthenticated) {
                        AppListScreen()
                    } else {
                        // Show loading indicator while waiting for authentication
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Authenticate the user if not already authenticated
        if (!isAuthenticated) {
            authenticateUser()
        }

        // Check permissions first
        checkOverlayPermission()
        checkAccessibilityServicePermission()
    }

    private fun authenticateUser() {
        biometricAuthHelper.showBiometricPrompt(
                activity = this,
                title = "Authenticate to access App Locker",
                subtitle = "Biometric authentication required",
                onSuccess = { isAuthenticated = true }
        )
    }

    override fun onPause() {
        super.onPause()
        // Dismiss dialog when activity pauses to prevent it from showing again
        accessibilityDialog?.dismiss()
        isAuthenticated = false
    }

    override fun onStop() {
        super.onStop()
        // Reset authentication when the app goes to background
        isAuthenticated = false
    }

    private fun showAccessibilityServiceDialog() {
        // Only create a new dialog if one doesn't already exist
        if (accessibilityDialog == null) {
            accessibilityDialog =
                    AlertDialog.Builder(this)
                            .setTitle("Accessibility Service Required")
                            .setMessage(
                                    "This app needs accessibility service to detect when locked apps are opened. Please enable it in the settings."
                            )
                            .setPositiveButton("Open Settings") { _, _ ->
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                            }
                            .setCancelable(false)
                            .create()

            accessibilityDialog?.show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager =
                getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )

        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.getResolveInfo().serviceInfo
            if (enabledServiceInfo.packageName.equals(packageName) &&
                            enabledServiceInfo.name.equals(
                                    AppLockAccessibilityService::class.java.canonicalName
                            )
            )
                    return true
        }
        return false
    }

    private fun checkAccessibilityServicePermission() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityServiceDialog()
            return
        }
        accessibilityDialog?.dismiss()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage(
                            "This app needs permission to display over other apps for the app lock feature to work properly."
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        startActivity(intent)
                    }
                    .setCancelable(false)
                    .create()
                    .show()
            return
        }
        accessibilityDialog?.dismiss()
    }
}
