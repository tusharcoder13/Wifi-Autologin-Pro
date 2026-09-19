package com.wifi.autologin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifi.autologin.data.model.ConnectionStatus
import com.wifi.autologin.data.model.FormDetectionResult
import com.wifi.autologin.data.model.LogEntry
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.data.repository.AppSettings
import com.wifi.autologin.data.repository.AppSettingsRepository
import com.wifi.autologin.data.repository.LogRepository
import com.wifi.autologin.data.repository.ProfileRepository
import com.wifi.autologin.network.AuthEngine
import com.wifi.autologin.network.CaptivePortalDetector
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val detector = CaptivePortalDetector(application)
    private val profileRepo = ProfileRepository(application)
    private val settingsRepo = AppSettingsRepository(application)
    private val authEngine = AuthEngine(application, settingsRepo)

    val profiles: StateFlow<List<WifiProfile>> = profileRepo.profiles
    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val logs: StateFlow<List<LogEntry>> = LogRepository.logs

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _inspectorResult = MutableStateFlow<FormDetectionResult?>(null)
    val inspectorResult: StateFlow<FormDetectionResult?> = _inspectorResult.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _updateInfo = MutableStateFlow<com.wifi.autologin.data.model.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.wifi.autologin.data.model.UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    init {
        refreshConnectionStatus()
        startLiveNetworkObserver()
        checkForUpdates(isManual = false)
    }

    private fun startLiveNetworkObserver() {
        // 1. Live OS Wi-Fi state callback for instant UI update on connect/disconnect
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    viewModelScope.launch {
                        delay(600)
                        refreshConnectionStatus()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    viewModelScope.launch {
                        refreshConnectionStatus()
                    }
                }

                override fun onLost(network: Network) {
                    viewModelScope.launch {
                        refreshConnectionStatus()
                    }
                }
            })
        } catch (e: Exception) {
            // Ignore fallback
        }

        // 2. Continuous real-time UI telemetry loop (refreshes state, latency & IP automatically)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                refreshConnectionStatus()
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isWifiOn = detector.isWifiConnected()
            if (!isWifiOn) {
                CaptivePortalDetector.latestSsid = ""
                _connectionStatus.value = ConnectionStatus(
                    state = WifiState.DISCONNECTED,
                    ssid = "Wi-Fi Disconnected",
                    ipAddress = "0.0.0.0",
                    gatewayIp = "",
                    portalUrl = "",
                    pingLatencyMs = -1,
                    matchedProfile = null,
                    matchingProfiles = emptyList(),
                    isAutoLoginRunning = false,
                    isKeepAliveActive = false
                )
                return@launch
            }

            val ssid = detector.getCurrentSsid()
            val ip = detector.getCurrentIpAddress()
            val gateway = detector.getGatewayIpAddress()
            val matchingProfiles = profileRepo.findProfilesForNetwork(ssid, gateway)
            val matchedProfile = matchingProfiles.firstOrNull()

            val probe = detector.probeConnectivity(
                settings.value.customProbeUrl,
                settings.value.bypassSslErrors
            )
            val ping = detector.measureSocketPing()

            val realSsid = if (ssid.isNotBlank() && ssid != "<unknown ssid>" && ssid != "Connected Wi-Fi") {
                ssid
            } else if (CaptivePortalDetector.latestSsid.isNotBlank() && CaptivePortalDetector.latestSsid != "<unknown ssid>" && CaptivePortalDetector.latestSsid != "Connected Wi-Fi") {
                CaptivePortalDetector.latestSsid
            } else if (matchedProfile != null && matchedProfile.ssid.isNotBlank()) {
                matchedProfile.ssid
            } else {
                "Campus Wi-Fi"
            }

            val targetPortalUrl = if (probe.portalUrl.isNotBlank()) {
                probe.portalUrl
            } else if (matchedProfile?.portalUrl?.isNotBlank() == true) {
                matchedProfile.portalUrl
            } else if (gateway.isNotBlank()) {
                "http://$gateway:8090/httpclient.html"
            } else {
                ""
            }

            _connectionStatus.value = ConnectionStatus(
                state = probe.state,
                ssid = realSsid,
                ipAddress = ip,
                gatewayIp = gateway,
                portalUrl = targetPortalUrl,
                pingLatencyMs = if (probe.latencyMs > 0) probe.latencyMs else ping,
                matchedProfile = matchedProfile,
                matchingProfiles = matchingProfiles,
                isAutoLoginRunning = _isLoggingIn.value,
                isKeepAliveActive = settings.value.isKeepAliveEnabled && matchedProfile?.isKeepAliveEnabled == true
            )
        }
    }

    fun setPrimaryProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepo.setPrimaryProfile(profileId)
            LogRepository.info("Account Switched", "Switched active primary account.")
            refreshConnectionStatus()
        }
    }

    fun triggerManualLogin(targetProfile: WifiProfile? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!detector.isWifiConnected()) {
                LogRepository.warning("Wi-Fi Disconnected", "Cannot log in: Please enable and connect to Wi-Fi first.")
                _isLoggingIn.value = false
                refreshConnectionStatus()
                return@launch
            }

            _isLoggingIn.value = true
            val currentSsid = detector.getCurrentSsid()
            val gateway = detector.getGatewayIpAddress()
            val profileToUse = targetProfile ?: profileRepo.findProfileForNetwork(currentSsid, gateway) ?: profiles.value.firstOrNull()

            if (profileToUse == null) {
                LogRepository.warning("No Profile", "Please create or select a Wi-Fi profile to log in.")
                _isLoggingIn.value = false
                return@launch
            }

            LogRepository.info("Manual Trigger", "Logging into ${profileToUse.name}...")
            val result = authEngine.executeLogin(profileToUse)

            if (result.isSuccess) {
                profileRepo.updateProfileLoginStatus(profileToUse.id, "SUCCESS")
                _connectionStatus.value = _connectionStatus.value.copy(
                    lastLoginMessage = "Success: ${result.message}"
                )

                // Rapid 4-stage network revalidation to dismiss captive portal popup immediately
                viewModelScope.launch(Dispatchers.IO) {
                    for (delayMs in listOf(0L, 250L, 700L, 1400L)) {
                        if (delayMs > 0) delay(delayMs)
                        detector.forceDismissCaptivePortalNotification()
                        detector.probeConnectivity()
                    }
                }
            } else {
                profileRepo.updateProfileLoginStatus(profileToUse.id, "FAILED")
                _connectionStatus.value = _connectionStatus.value.copy(
                    lastLoginMessage = "Failed: ${result.message}"
                )
            }

            _isLoggingIn.value = false
            refreshConnectionStatus()
        }
    }

    fun inspectPortalUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isInspecting.value = true
            LogRepository.info("Portal Inspector", "Inspecting $url...")
            try {
                val probe = detector.probeConnectivity(url, settings.value.bypassSslErrors)
                val html = probe.redirectHtml
                val isSuccess = html.contains("<form", ignoreCase = true) || html.contains("password", ignoreCase = true)
                val result = FormDetectionResult(
                    isSuccessful = isSuccess,
                    actionUrl = probe.portalUrl.ifBlank { url },
                    detectedUsernameField = "username",
                    detectedPasswordField = "password",
                    rawHtml = html
                )
                _inspectorResult.value = result
                LogRepository.success("Inspector Success", "Detected portal form at ${result.actionUrl}")
            } catch (e: Exception) {
                _inspectorResult.value = FormDetectionResult(
                    isSuccessful = false,
                    errorMessage = e.localizedMessage
                )
                LogRepository.error("Inspector Error", "Inspection failed: ${e.localizedMessage}")
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun clearLogs() {
        LogRepository.clear()
    }

    fun saveProfile(profile: WifiProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepo.saveProfile(profile)
            LogRepository.info("Profile Saved", "Saved profile '${profile.name}' (${profile.username})")
            refreshConnectionStatus()
            if (detector.isWifiConnected()) {
                val probe = detector.probeConnectivity(settings.value.customProbeUrl, settings.value.bypassSslErrors)
                if (probe.state != WifiState.CONNECTED_ONLINE) {
                    triggerManualLogin(profile)
                }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepo.deleteProfile(profileId)
            refreshConnectionStatus()
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateSettings(newSettings)
            refreshConnectionStatus()
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdates.value = true
            _updateCheckMessage.value = null

            val app = getApplication<Application>()
            val pInfo = try {
                app.packageManager.getPackageInfo(app.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val currentCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo?.longVersionCode?.toInt() ?: 1
            } else {
                @Suppress("DEPRECATION")
                pInfo?.versionCode ?: 1
            }

            val update = com.wifi.autologin.network.UpdateManager.fetchLatestUpdate(currentCode)
            if (update != null) {
                _updateInfo.value = update
            } else if (isManual) {
                _updateCheckMessage.value = "You are using the latest version (${pInfo?.versionName ?: "1.0.0"})"
            }

            _isCheckingUpdates.value = false
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    fun clearUpdateMessage() {
        _updateCheckMessage.value = null
    }
}
