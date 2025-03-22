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

    // Authentication timeout (3 seconds)
    private val AUTH_TIMEOUT_MS = 3 * 1000L

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClearTask: Runnable? = null
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Only process window state changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Cancel any pending clear task since we have a new event
        pendingClearTask?.let {
            handler.removeCallbacks(it)
            pendingClearTask = null
        }

        // Handle system UI events specially (like back button)
        if (packageName == "com.android.systemui") {
            // Don't clear immediately, wait to see if we return to the locked app
            if (currentLockedApp != null) {
                pendingClearTask = Runnable {
                    Log.d(TAG, "Confirmed app switch away from: $currentLockedApp")
                    currentLockedApp = null
                    reconfigureService()
                    pendingClearTask = null
                }

                // Schedule the task to run after a short delay
                handler.postDelayed(pendingClearTask!!, APP_SWITCH_VERIFICATION_DELAY)
            }
            return
        }

        // If we're back in the currently locked app, cancel any pending clear
        if (packageName == currentLockedApp) {
            return
        }

        // If we have a current locked app and we're now in a different app
        // (not system UI and not our own app)
        if (currentLockedApp != null &&
                        packageName != currentLockedApp &&
                        packageName != this.packageName
        ) {
            Log.d(TAG, "User switched from locked app: $currentLockedApp to: $packageName")
            currentLockedApp = null
            reconfigureService()
            return
        }

        // Skip our own app
        if (packageName == this.packageName) {
            return
        }

        // Check if this is a locked app
        if (packageName !in lockedPackages) {
            return
        }

        // Check if app was recently authenticated
        serviceScope.launch {
            val wasRecentlyAuthenticated =
                    repository.wasRecentlyAuthenticated(packageName, AUTH_TIMEOUT_MS)

            if (wasRecentlyAuthenticated) {
                // Update on main thread
                Handler(Looper.getMainLooper()).post {
                    // Consider this app as the current locked app
                    if (currentLockedApp != packageName) {
                        currentLockedApp = packageName
                        reconfigureService()
                    }
                }
                return@launch
            }

            // Not recently authenticated, launch lock screen
            Handler(Looper.getMainLooper()).post { launchLockScreen(packageName) }
        }
    }

    private fun launchLockScreen(packageName: String) {
        val intent =
                Intent(this, LockScreenActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        pendingClearTask?.let { handler.removeCallbacks(it) }
        collectJob?.cancel()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    companion object {
        private const val TAG = "AppLockService"
        var instance: AppLockAccessibilityService? = null
            private set
    }
}
