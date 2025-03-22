package com.batflarrow.zerobs.applock.data

import android.content.Context
import android.util.Log // Import this
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object LockedAppsSerializer : Serializer<LockedAppsMapProto> {
    private const val TAG = "LockedAppsSerializer"

    override val defaultValue: LockedAppsMapProto = LockedAppsMapProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LockedAppsMapProto {
        try {
            return LockedAppsMapProto.parseFrom(input)
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading proto: ${exception.message}")

            // Instead of propagating corruption, return the default value
            // This effectively resets the data when corruption is detected
            return defaultValue
        }
    }

    override suspend fun writeTo(t: LockedAppsMapProto, output: OutputStream) {
        t.writeTo(output)
    }
}

// Extension property for DataStore
val Context.lockedAppsDataStore: DataStore<LockedAppsMapProto> by
        dataStore(
                fileName = LockedAppsRepository.DATA_STORE_FILE,
                serializer = LockedAppsSerializer
        )

class LockedAppsRepository(private val context: Context) {
    companion object {
        internal const val DATA_STORE_FILE = "locked_apps.pb"
    }

    // Get all locked apps as a Flow
    val lockedApps: Flow<Map<String, AppData>> =
            context.lockedAppsDataStore.data.map { proto -> proto.lockedAppsMap }

    // Lock an app
    suspend fun lockApp(packageName: String, appName: String) {
        Log.d("LockedAppsRepo", "Locking app: $packageName ($appName)")
        context.lockedAppsDataStore.updateData { currentMap ->
            val appData =
                    AppData.newBuilder()
                            .setAppName(appName)
                            .setLockTimestamp(System.currentTimeMillis())
                            // Keep existing auth timestamp if it exists
                            .also { builder ->
                                if (currentMap.lockedAppsMap.containsKey(packageName)) {
                                    val existingData = currentMap.lockedAppsMap[packageName]
                                    if (existingData?.lastAuthenticationTimestamp ?: 0L > 0) {
                                        builder.setLastAuthenticationTimestamp(
                                                existingData!!.lastAuthenticationTimestamp
                                        )
                                    }
                                }
                            }
                            .build()

            // Update the map with the new app data
            currentMap.toBuilder().putLockedApps(packageName, appData).build()
        }
    }

    // Record successful authentication
    suspend fun recordAuthentication(packageName: String) {
        Log.d("LockedAppsRepo", "Recording authentication for: $packageName")
        context.lockedAppsDataStore.updateData { currentMap ->
            if (currentMap.lockedAppsMap.containsKey(packageName)) {
                val existingData = currentMap.lockedAppsMap[packageName]!!
                val updatedData =
                        existingData
                                .toBuilder()
                                .setLastAuthenticationTimestamp(System.currentTimeMillis())
                                .build()

                currentMap.toBuilder().putLockedApps(packageName, updatedData).build()
            } else {
                // App not found, no update needed
                currentMap
            }
        }
    }

    // Check if app was recently authenticated
    suspend fun wasRecentlyAuthenticated(packageName: String, timeoutMs: Long): Boolean {
        val currentMap = context.lockedAppsDataStore.data.first()

        if (!currentMap.lockedAppsMap.containsKey(packageName)) {
            return false
        }

        val appData = currentMap.lockedAppsMap[packageName]!!
        val lastAuthTime = appData.lastAuthenticationTimestamp
        if (lastAuthTime == 0L) return false

        val timeSinceAuth = System.currentTimeMillis() - lastAuthTime
        return timeSinceAuth < timeoutMs
    }

    // Unlock an app
    suspend fun unlockApp(packageName: String) {
        Log.d("LockedAppsRepo", "Unlocking app: $packageName")
        context.lockedAppsDataStore.updateData { currentMap ->
            currentMap.toBuilder().removeLockedApps(packageName).build()
        }
    }
}
