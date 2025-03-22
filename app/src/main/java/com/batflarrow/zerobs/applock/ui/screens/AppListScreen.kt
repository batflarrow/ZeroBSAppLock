package com.batflarrow.zerobs.applock.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.batflarrow.zerobs.applock.data.AppData
import com.batflarrow.zerobs.applock.data.LockedAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class to hold app information
data class AppInfo(val packageName: String, val appName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var includeSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isDrawerOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Create repository
    val repository = remember { LockedAppsRepository(context) }

    // State for app list
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // State for locked apps
    var lockedApps by remember { mutableStateOf<Map<String, AppData>>(emptyMap()) }

    // Load apps asynchronously when the component is first composed
    LaunchedEffect(includeSystemApps) {
        withContext(Dispatchers.IO) {
            val apps = getInstalledApps(packageManager, includeSystemApps)
            withContext(Dispatchers.Main) { allApps = apps }
        }
    }

    // Load locked apps from DataStore
    LaunchedEffect(Unit) {
        repository.lockedApps.collect { lockedAppsMap -> lockedApps = lockedAppsMap }
    }

    // Filter apps based on search query
    val filteredApps =
            remember(allApps, searchQuery) {
                if (searchQuery.isEmpty()) {
                    allApps
                } else {
                    allApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
                }
            }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Lock Apps") },
                        navigationIcon = {
                            IconButton(onClick = { isDrawerOpen = true }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                )
            }
    ) { paddingValues ->
        Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    },
                    singleLine = true
            )

            // System apps toggle
            Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Include System Apps")
                Switch(checked = includeSystemApps, onCheckedChange = { includeSystemApps = it })
            }

            // App list
            if (filteredApps.isEmpty()) {
                Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = if (allApps.isEmpty()) "Loading apps..." else "No apps found",
                            textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(
                            items = filteredApps,
                            key = { it.packageName } // Use package name as key for better recycling
                    ) { app ->
                        AppListItem(
                                appInfo = app,
                                isLocked = app.packageName in lockedApps,
                                onToggleLock = {
                                    coroutineScope.launch {
                                        if (app.packageName in lockedApps) {
                                            // Unlock app
                                            repository.unlockApp(app.packageName)
                                        } else {
                                            // Lock app
                                            repository.lockApp(app.packageName, app.appName)
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    // TODO: Implement drawer when needed
    if (isDrawerOpen) {
        // This is a placeholder for the drawer implementation
        AlertDialog(
                onDismissRequest = { isDrawerOpen = false },
                title = { Text("App Drawer") },
                text = { Text("Drawer content will be implemented here") },
                confirmButton = { TextButton(onClick = { isDrawerOpen = false }) { Text("Close") } }
        )
    }
}

@Composable
fun AppListItem(appInfo: AppInfo, isLocked: Boolean, onToggleLock: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Load the app icon asynchronously - fixed version
    val appIconBitmap =
            produceState<ImageBitmap?>(initialValue = null, appInfo.packageName) {
                        value =
                                withContext(Dispatchers.IO) {
                                    try {
                                        packageManager
                                                .getApplicationIcon(appInfo.packageName)
                                                .toBitmap()
                                                .asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                    }
                    .value

    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // App icon - simplified rendering logic
            Box(modifier = Modifier.size(40.dp).padding(end = 12.dp)) {
                if (appIconBitmap != null) {
                    Image(
                            bitmap = appIconBitmap,
                            contentDescription = "App icon",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AppIconPlaceholder(appInfo.appName)
                }
            }

            // App name
            Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
            )
        }

        // Lock/unlock icon
        IconButton(onClick = onToggleLock) {
            Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (isLocked) "Unlock" else "Lock",
                    tint =
                            if (isLocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider(
            modifier = Modifier.padding(start = 52.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun AppIconPlaceholder(appName: String) {
    Box(
            modifier =
                    Modifier.size(40.dp)
                            .padding(end = 12.dp)
                            .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                            ),
            contentAlignment = Alignment.Center
    ) { Text(text = appName.take(1).uppercase(), style = MaterialTheme.typography.bodyMedium) }
}

// Get installed apps with package names
fun getInstalledApps(packageManager: PackageManager, includeSystemApps: Boolean): List<AppInfo> {
    return packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                includeSystemApps || !isSystemApp // Include system apps only if toggle is ON
            }
            .map {
                AppInfo(
                        packageName = it.packageName,
                        appName = it.loadLabel(packageManager).toString()
                )
            }
            .sortedBy { it.appName }
}
