package com.batflarrow.zerobs.applock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.batflarrow.zerobs.applock.data.LockedAppsRepository
import com.batflarrow.zerobs.applock.ui.LockScreenActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: LockedAppsRepository
    private var lockedPackages = setOf<String>()
    private var currentLockedApp: String? = null
    private var collectJob: Job? = null

    // Authentication timeout (2 seconds)
    private val AUTH_TIMEOUT_MS = 2 * 1000L

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClearTasks = mutableListOf<Runnable>()
    private val APP_SWITCH_VERIFICATION_DELAY = 3000L

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = LockedAppsRepository(applicationContext)
        Log.d(TAG, "Accessibility service created")
        collectLockedPackages()
    }

    private fun collectLockedPackages() {
        collectJob =
                CoroutineScope(Dispatchers.Main).launch {
                    repository.lockedApps.collect { lockedApps ->
                        val newLockedPackages = lockedApps.keys.toSet()
                        if (lockedPackages != newLockedPackages) {
                            Log.d(
                                    TAG,
                                    "Locked packages updated: ${newLockedPackages.joinToString()}"
                            )
                            lockedPackages = newLockedPackages
                            reconfigureService()
                        }
                    }
                }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        reconfigureService()
    }

    private fun reconfigureService() {
        try {
            val info = AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            if (currentLockedApp != null) {
                // If we have a locked app active, monitor all packages to detect when user leaves
                info.packageNames = null
                Log.d(TAG, "Monitoring ALL packages (locked app is active: $currentLockedApp)")
            } else if (lockedPackages.isNotEmpty()) {
                // Only monitor locked packages when no locked app is active
                info.packageNames = lockedPackages.toTypedArray()
                Log.d(TAG, "Monitoring ONLY: ${lockedPackages.joinToString()}")
            } else {
                // If no locked packages, monitor nothing
                info.packageNames = arrayOf("com.package.that.doesnt.exist")
                Log.d(TAG, "No locked packages, using dummy package")
            }

            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50

            this.serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error reconfiguring service", e)
        }
    }

    fun setCurrentLockedApp(packageName: String) {
        Log.d(TAG, "Setting current locked app: $packageName")
        currentLockedApp = packageName
        // Record authentication in repository
        serviceScope.launch { repository.recordAuthentication(packageName) }
        reconfigureService()
    }

    private fun isHomeOrSystemUI(packageName: String): Boolean {
        return packageName in
                listOf(
                        "com.android.launcher",
                        "com.google.android.apps.nexuslauncher",
                        "com.miui.home",
                        "com.sec.android.app.launcher",
                        "com.oppo.launcher",
                        "com.android.settings"
                )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only process window state changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (currentLockedApp == null) {
            if (packageName in lockedPackages) {
                Log.d(TAG, "Setting current locked app: $packageName")
                Handler(Looper.getMainLooper()).post { launchLockScreen(packageName) }

                reconfigureService()
            }
            return
        }
        /**
         * If the current locked app is the same as the package for which we received the event, we
         * don't need to do anything.
         */
        if (currentLockedApp == packageName) {
            // clear events that were launched to mark the app as locked
            clearPendingTasks()
            return
        }

        Log.d(
                TAG,
                "Full screen event from a non locked APP when we've already locked an APP ${event.toString()}"
        )
        if (packageName in lockedPackages && currentLockedApp != packageName) {
            Log.d(TAG, "Setting current locked app: $packageName")
            clearPendingTasks()
            Handler(Looper.getMainLooper()).post { launchLockScreen(packageName) }
            reconfigureService()
            return
        }

        if (!isHomeOrSystemUI(packageName)) {
            Log.d(TAG, "Not a home or system UI, skipping")
            return
        }
        /**
         * Mark the APP as locked again set currentLockedApp to null. Wait for some time to perform
         * this operation to avoid cases when we switch back to the locked app and we're not sure if
         * the user is in the lock screen or not.
         */
        val clearTask = Runnable {
            if (currentLockedApp != null) {
                Log.d(TAG, "Confirmed app switch away from: $currentLockedApp")
                currentLockedApp = null
                reconfigureService()
            }
        }
        pendingClearTasks.add(clearTask)
        handler.postDelayed(clearTask, AUTH_TIMEOUT_MS)
    }

    private fun launchLockScreen(packageName: String) {
        val intent =
                Intent(this, LockScreenActivity::class.java).apply {
                    // These flags will clear existing activities and create a new task
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

                    putExtra(LockScreenActivity.EXTRA_PACKAGE_NAME, packageName)

                    val appName =
                            try {
                                val packageManager = applicationContext.packageManager
                                packageManager
                                        .getApplicationLabel(
                                                packageManager.getApplicationInfo(packageName, 0)
                                        )
                                        .toString()
                            } catch (e: Exception) {
                                "Unknown App"
                            }

                    putExtra(LockScreenActivity.EXTRA_APP_NAME, appName)
                }

        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all pending clear tasks
        clearPendingTasks()
        collectJob?.cancel()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    // Add this helper function to clear pending tasks
    private fun clearPendingTasks() {
        pendingClearTasks.forEach { task -> handler.removeCallbacks(task) }
        pendingClearTasks.clear()
    }

    companion object {
        private const val TAG = "AppLockService"
        var instance: AppLockAccessibilityService? = null
            private set
    }
}
