package com.wifi.autologin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.wifi.autologin.service.WifiMonitorService
import com.wifi.autologin.ui.components.AppBottomNav
import com.wifi.autologin.ui.components.ScreenTab
import com.wifi.autologin.ui.screens.*
import com.wifi.autologin.ui.theme.WiFiAutoLoginProTheme
import com.wifi.autologin.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Restart service to pick up SSID if location permission was granted
        WifiMonitorService.start(this)
        viewModel.refreshConnectionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            WiFiAutoLoginProTheme {
                var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
                var showFeedbackDialog by remember { mutableStateOf(false) }

                val connectionStatus by viewModel.connectionStatus.collectAsState()
                val profiles by viewModel.profiles.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val logs by viewModel.logs.collectAsState()
                val inspectorResult by viewModel.inspectorResult.collectAsState()
                val isInspecting by viewModel.isInspecting.collectAsState()
                val isLoggingIn by viewModel.isLoggingIn.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
                val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()

                Scaffold(
                    bottomBar = {
                        AppBottomNav(
                            selectedTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        androidx.compose.animation.Crossfade(
                            targetState = currentTab,
                            animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "TabTransition"
                        ) { tab ->
                            when (tab) {
                                ScreenTab.DASHBOARD -> DashboardScreen(
                                    status = connectionStatus,
                                    settings = settings,
                                    recentLogs = logs,
                                    isLoggingIn = isLoggingIn,
                                    onLoginClick = { viewModel.triggerManualLogin() },
                                    onRefreshClick = { viewModel.refreshConnectionStatus() },
                                    onToggleAutoLogin = { enabled ->
                                        viewModel.updateSettings(settings.copy(isMasterAutoLoginEnabled = enabled))
                                    },
                                    onNavigateToLogs = { currentTab = ScreenTab.LOGS },
                                    onNavigateToProfiles = { currentTab = ScreenTab.PROFILES },
                                    onNavigateToSettings = { currentTab = ScreenTab.SETTINGS },
                                    onSwitchProfile = { profileId -> viewModel.setPrimaryProfile(profileId) }
                                )

                                ScreenTab.PROFILES -> ProfilesScreen(
                                    profiles = profiles,
                                    currentSsid = connectionStatus.ssid,
                                    onSaveProfile = { viewModel.saveProfile(it) },
                                    onDeleteProfile = { viewModel.deleteProfile(it) },
                                    onLoginProfile = { viewModel.triggerManualLogin(it) },
                                    onSetPrimary = { profileId -> viewModel.setPrimaryProfile(profileId) }
                                )

                                ScreenTab.INSPECTOR -> PortalInspectorScreen(
                                    currentPortalUrl = connectionStatus.portalUrl,
                                    inspectorResult = inspectorResult,
                                    isInspecting = isInspecting,
                                    onInspectUrl = { viewModel.inspectPortalUrl(it) },
                                    onSaveAsProfile = { profile ->
                                        viewModel.saveProfile(profile)
                                        currentTab = ScreenTab.PROFILES
                                    }
                                )

                                ScreenTab.LOGS -> LogsScreen(
                                    logs = logs,
                                    onClearLogs = { viewModel.clearLogs() }
                                )

                                ScreenTab.SETTINGS -> SettingsScreen(
                                    settings = settings,
                                    onUpdateSettings = { viewModel.updateSettings(it) },
                                    isCheckingUpdates = isCheckingUpdates,
                                    updateCheckMessage = updateCheckMessage,
                                    onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) },
                                    onClearUpdateMessage = { viewModel.clearUpdateMessage() },
                                    onOpenFeedback = { showFeedbackDialog = true }
                                )
                            }
                        }

                        // In-App Update Dialog
                        if (updateInfo != null) {
                            com.wifi.autologin.ui.components.UpdateDialog(
                                updateInfo = updateInfo!!,
                                onDismiss = { viewModel.dismissUpdateDialog() }
                            )
                        }

                        // In-App Feedback Dialog
                        if (showFeedbackDialog) {
                            com.wifi.autologin.ui.components.FeedbackDialog(
                                onDismiss = { showFeedbackDialog = false },
                                onSubmit = { payload ->
                                    viewModel.submitFeedback(payload)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WifiMonitorService.start(this)
        viewModel.refreshConnectionStatus()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Location permission is needed to read current Wi-Fi SSID
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
