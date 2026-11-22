package com.portly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.portly.core.common.update.UpdateManager
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.AppSettings
import com.portly.core.model.UpdateInfo
import com.portly.core.model.UpdateStatus
import com.portly.core.security.BiometricAuthHelper
import com.portly.core.security.BiometricResult
import com.portly.core.ui.theme.PortlyTheme
import com.portly.feature.archive.ArchiveScreen
import com.portly.feature.connections.ConnectionsScreen
import com.portly.feature.filemanager.FileManagerScreen
import com.portly.feature.history.HistoryScreen
import com.portly.feature.home.HomeScreen
import com.portly.feature.preview.PreviewScreen
import com.portly.feature.servers.ServersScreen
import com.portly.feature.settings.SettingsScreen
import com.portly.feature.transfers.TransfersScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

enum class PortlyDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    FILES("files", "Files", Icons.Outlined.FolderOpen),
    CONNECTIONS("connections", "Connect", Icons.Outlined.CloudQueue),
    SERVERS("servers", "Servers", Icons.Outlined.Dns),
    TRANSFERS("transfers", "Transfers", Icons.Outlined.SwapVert),
    HISTORY("history", "History", Icons.Outlined.History),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: PortlySettingsDataStore

    @Inject
    lateinit var biometricAuthHelper: BiometricAuthHelper

    @Inject
    lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val initialSharedUri = extractFirstSharedUri(intent)?.toString()
        val initialPairingUri = extractPairingUri(intent)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { }

            LaunchedEffect(Unit) {
                val needed = buildList {
                    if (Build.VERSION.SDK_INT >= 37) {
                        val p = "android.permission.ACCESS_LOCAL_NETWORK"
                        if (ContextCompat.checkSelfPermission(this@MainActivity, p) != PackageManager.PERMISSION_GRANTED) {
                            add(p)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val p = Manifest.permission.POST_NOTIFICATIONS
                        if (ContextCompat.checkSelfPermission(this@MainActivity, p) != PackageManager.PERMISSION_GRANTED) {
                            add(p)
                        }
                    }
                }
                if (needed.isNotEmpty()) {
                    permissionLauncher.launch(needed.toTypedArray())
                }
            }

            val settings by settingsDataStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val systemDark = isSystemInDarkTheme()
            val useDark = when (settings.themeMode) {
                com.portly.core.model.ThemeMode.SYSTEM -> systemDark
                com.portly.core.model.ThemeMode.LIGHT -> false
                com.portly.core.model.ThemeMode.DARK, com.portly.core.model.ThemeMode.AMOLED -> true
            }

            DisposableEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
                onDispose {}
            }

            var unlocked by rememberSaveable { mutableStateOf(!settings.biometricLockEnabled) }
            var authError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(settings.biometricLockEnabled) {
                if (!settings.biometricLockEnabled) {
                    unlocked = true
                }
            }

            LaunchedEffect(settings.autoCheckUpdates) {
                if (settings.autoCheckUpdates) {
                    updateManager.checkForUpdates(
                        repo = settings.customGitHubRepo,
                        channel = settings.updateReleaseChannel,
                    )
                }
            }

            PortlyTheme(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor,
            ) {
                if (settings.biometricLockEnabled && !unlocked) {
                    BiometricLockGate(
                        errorMessage = authError,
                        onUnlock = {
                            authError = null
                            biometricAuthHelper.authenticate(
                                activity = this@MainActivity,
                                title = "Unlock Portly",
                                subtitle = "Authenticate to access connections and files",
                            ) { result ->
                                when (result) {
                                    BiometricResult.Succeeded -> unlocked = true
                                    BiometricResult.Cancelled -> authError = "Authentication cancelled."
                                    is BiometricResult.Failed -> authError = result.reason
                                }
                            }
                        },
                    )
                } else {
                    PortlyAppShell(
                        initialSharedUri = initialSharedUri,
                        initialPairingUri = initialPairingUri,
                        updateManager = updateManager,
                    )
                }
            }
        }
    }

    private fun extractFirstSharedUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
            else -> null
        }
    }

    private fun extractPairingUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return if (data.scheme == "portly" && data.host == "pair") data.toString() else null
    }
}

@Composable
private fun BiometricLockGate(
    errorMessage: String?,
    onUnlock: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onUnlock()
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Portly is locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "Verify your identity to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onUnlock) {
                Text("Unlock")
            }
        }
    }
}

@Composable
private fun PortlyAppShell(
    initialSharedUri: String? = null,
    initialPairingUri: String? = null,
    updateManager: UpdateManager,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var pendingRemoteProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPairingUri by rememberSaveable { mutableStateOf(initialPairingUri) }
    val updateStatus by updateManager.status.collectAsStateWithLifecycle()
    var showChangelogDialog by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(initialSharedUri) {
        if (!initialSharedUri.isNullOrBlank()) {
            val encodedUri = Uri.encode(initialSharedUri)
            navController.navigate("preview?path=&uri=$encodedUri")
        }
    }

    LaunchedEffect(initialPairingUri) {
        if (!initialPairingUri.isNullOrBlank()) {
            pendingPairingUri = initialPairingUri
            navController.navigate(PortlyDestination.CONNECTIONS.route) {
                launchSingleTop = true
            }
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            PortlyDestination.entries.forEach { dest ->
                item(
                    selected = currentRoute == dest.route,
                    onClick = { navigateTo(dest.route) },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = {
                        Text(
                            text = dest.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = false,
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = PortlyDestination.HOME.route,
            modifier = Modifier.fillMaxSize(),
        ) {
                composable(PortlyDestination.HOME.route) {
                    HomeScreen(
                        onOpenFiles = { navigateTo(PortlyDestination.FILES.route) },
                        onOpenConnections = { navigateTo(PortlyDestination.CONNECTIONS.route) },
                        onOpenServers = { navigateTo(PortlyDestination.SERVERS.route) },
                        onOpenTransfers = { navigateTo(PortlyDestination.TRANSFERS.route) },
                        onConnectProfile = { profileId ->
                            pendingRemoteProfileId = profileId
                            navigateTo(PortlyDestination.FILES.route)
                        },
                    )
                }
                composable(PortlyDestination.FILES.route) {
                    FileManagerScreen(
                        initialRemoteProfileId = pendingRemoteProfileId,
                        onOpenPreview = { filePath, uriString ->
                            val encodedPath = Uri.encode(filePath.orEmpty())
                            val encodedUri = Uri.encode(uriString.orEmpty())
                            navController.navigate("preview?path=$encodedPath&uri=$encodedUri")
                        },
                        onOpenArchive = { archivePath, sourcePaths ->
                            val encodedPath = Uri.encode(archivePath.orEmpty())
                            val encodedSources = Uri.encode(sourcePaths.joinToString("\n"))
                            navController.navigate("archive?path=$encodedPath&sources=$encodedSources")
                        },
                    )
                }
                composable(PortlyDestination.CONNECTIONS.route) {
                    ConnectionsScreen(
                        initialPairingUri = pendingPairingUri,
                        onConsumeInitialPairingUri = { pendingPairingUri = null },
                        onConnectProfile = { profileId ->
                            pendingRemoteProfileId = profileId
                            navigateTo(PortlyDestination.FILES.route)
                        },
                    )
                }
                composable(PortlyDestination.SERVERS.route) {
                    ServersScreen()
                }
                composable(PortlyDestination.TRANSFERS.route) {
                    TransfersScreen()
                }
                composable(PortlyDestination.HISTORY.route) {
                    HistoryScreen()
                }
                composable(PortlyDestination.SETTINGS.route) {
                    SettingsScreen()
                }
                composable(
                    route = "preview?path={path}&uri={uri}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = "" },
                        navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = "" },
                    ),
                ) { entry ->
                    val path = entry.arguments?.getString("path")?.takeIf { it.isNotBlank() }
                    val uri = entry.arguments?.getString("uri")?.takeIf { it.isNotBlank() }
                    PreviewScreen(
                        filePath = path,
                        uriString = uri,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = "archive?path={path}&sources={sources}",
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = "" },
                        navArgument("sources") { type = NavType.StringType; nullable = true; defaultValue = "" },
                    ),
                ) { entry ->
                    val path = entry.arguments?.getString("path")?.takeIf { it.isNotBlank() }
                    val sources = entry.arguments?.getString("sources")
                        ?.split("\n")
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    ArchiveScreen(
                        archivePath = path,
                        sourcePaths = sources,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }

    if (updateStatus is UpdateStatus.UpdateAvailable) {
        val update = (updateStatus as UpdateStatus.UpdateAvailable).updateInfo
        AlertDialog(
            onDismissRequest = { updateManager.resetStatus() },
            title = { Text("PORTLY UPDATE AVAILABLE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Version: ${update.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A newer ${update.releaseChannel.name.lowercase()} version of Portly is available on GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    updateManager.startDownload(update)
                    navigateTo(PortlyDestination.SETTINGS.route)
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                Row {
                    if (update.releaseNotes.isNotBlank()) {
                        TextButton(onClick = { showChangelogDialog = update }) {
                            Text("View Changelog")
                        }
                    }
                    TextButton(onClick = { updateManager.resetStatus() }) {
                        Text("Remind Me Later")
                    }
                }
            },
        )
    }

    showChangelogDialog?.let { update ->
        AlertDialog(
            onDismissRequest = { showChangelogDialog = null },
            title = { Text("Release Notes — Portly ${update.versionName}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (update.releaseNotes.isNotBlank()) update.releaseNotes else "No release notes provided.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showChangelogDialog = null
                    updateManager.startDownload(update)
                    navigateTo(PortlyDestination.SETTINGS.route)
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangelogDialog = null }) {
                    Text("Close")
                }
            },
        )
    }
}
