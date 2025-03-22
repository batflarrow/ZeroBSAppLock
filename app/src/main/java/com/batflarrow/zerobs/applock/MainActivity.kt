package com.batflarrow.zerobs.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.batflarrow.zerobs.applock.service.AppLockAccessibilityService
import com.batflarrow.zerobs.applock.ui.screens.AppListScreen
import com.batflarrow.zerobs.applock.ui.theme.ZeroBSAppLockTheme

class MainActivity : ComponentActivity() {
    private var accessibilityDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZeroBSAppLockTheme { AppListScreen() } }
    }

    override fun onResume() {
        super.onResume()

        // Check if service is running and only show dialog if needed
        if (!isAccessibilityServiceEnabled()) {
            Log.d("MainActivity", "Accessibility service is NOT enabled")
            showAccessibilityServiceDialog()
        } else {
            Log.d("MainActivity", "Accessibility service is enabled")
            // Dismiss any existing dialog if the service is now enabled
            accessibilityDialog?.dismiss()
            accessibilityDialog = null
        }
    }

    override fun onPause() {
        super.onPause()
        // Dismiss dialog when activity pauses to prevent it from showing again
        accessibilityDialog?.dismiss()
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
        // Get the AccessibilityManager
        val accessibilityManager =
                getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        // Get all the enabled accessibility services
        val enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )

        // The service ID we're looking for
        val serviceId = "${packageName}/${AppLockAccessibilityService::class.java.canonicalName}"

        // Check if our service is in the list of enabled services
        return enabledServices.any { serviceInfo -> serviceInfo.id == serviceId }
    }
}
