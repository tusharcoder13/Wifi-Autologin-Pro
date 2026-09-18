package com.wifi.autologin.service

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.*
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wifi.autologin.MainActivity
import com.wifi.autologin.R
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.data.repository.AppSettingsRepository
import com.wifi.autologin.data.repository.LogRepository
import com.wifi.autologin.data.repository.ProfileRepository
import com.wifi.autologin.network.AuthEngine
import com.wifi.autologin.network.CaptivePortalDetector
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class WifiMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var detector: CaptivePortalDetector
    private lateinit var authEngine: AuthEngine
    private lateinit var profileRepository: ProfileRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private val isLoggingIn = AtomicBoolean(false)
    private var keepAliveJob: Job? = null
    private var lastNotifiedSuccessNetwork: String? = null
    private var lastNotifiedSuccessTime: Long = 0L

    companion object {
        const val CHANNEL_SILENT_DAEMON = "wifi_autologin_silent_daemon_v3"
        const val CHANNEL_ALERTS = "wifi_autologin_alerts_channel_v3"
        const val CHANNEL_ERRORS = "wifi_autologin_errors_channel_v3"
        const val NOTIFICATION_ID = 1001
        const val SUCCESS_NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003
        const val ACTION_MANUAL_LOGIN = "com.wifi.autologin.ACTION_MANUAL_LOGIN"

        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        fun triggerLogin(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java).apply {
                action = ACTION_MANUAL_LOGIN
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        detector = CaptivePortalDetector(applicationContext)
        settingsRepository = AppSettingsRepository(applicationContext)
        authEngine = AuthEngine(applicationContext, settingsRepository)
        profileRepository = ProfileRepository(applicationContext)

        createNotificationChannel()
        startInForeground()

        registerNetworkCallback()
        registerWifiStateReceiver()
        startKeepAliveLoop()

        LogRepository.info("Monitor Started", "Wi-Fi monitor foreground service active 24/7.")

        // Immediate check if Wi-Fi is already active upon service startup
        val wifiNet = detector.getPrimaryWifiNetwork()
        if (wifiNet != null) {
            bindNetworkAndTrigger(wifiNet, isCaptiveHint = false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        serviceScope.launch {
            val wifiNet = detector.getPrimaryWifiNetwork()
            if (wifiNet != null) {
                bindNetworkAndTrigger(wifiNet, isCaptiveHint = (action == ACTION_MANUAL_LOGIN))
            } else {
                triggerAutoLoginForCurrentWifi(isManual = (action == ACTION_MANUAL_LOGIN), isKnownCaptive = false)
            }
        }
        return START_STICKY
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val hasLocationPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasLocationPerm) {
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
        } else {
            0
        }

        val callbackHandler = object : ConnectivityManager.NetworkCallback(flag) {
            override fun onAvailable(network: Network) {
                LogRepository.info("Wi-Fi Connected", "Detected Wi-Fi network interface.")
                extractSsidFromNetwork(network)
                bindNetworkAndTrigger(network, isCaptiveHint = false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                extractSsidFromCapabilities(capabilities)

                val currentSsid = detector.getCurrentSsid().ifBlank { "Wi-Fi" }
                val hasCaptive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                if (hasCaptive) {
                    LogRepository.warning("Captive Detected", "OS detected captive portal lock on $currentSsid.")
                    bindNetworkAndTrigger(network, isCaptiveHint = true)
                } else if (isValidated) {
                    LogRepository.success("Network Validated", "Android OS confirmed full internet access on $currentSsid (Icon verified).")
                }
            }

            override fun onLost(network: Network) {
                CaptivePortalDetector.latestSsid = ""
                lastNotifiedSuccessNetwork = null
                LogRepository.info("Wi-Fi Lost", "Disconnected from Wi-Fi.")
                updateNotification("Monitoring Wi-Fi networks in background...")
            }
        }

        networkCallback = callbackHandler

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (se: SecurityException) {
            try {
                // Fallback without location info flag if permission denied
                val fallbackHandler = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        extractSsidFromNetwork(network)
                        bindNetworkAndTrigger(network, isCaptiveHint = false)
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        extractSsidFromCapabilities(capabilities)
                        val hasCaptive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                        if (hasCaptive) {
                            bindNetworkAndTrigger(network, isCaptiveHint = true)
                        }
                    }

                    override fun onLost(network: Network) {
                        CaptivePortalDetector.latestSsid = ""
                        updateNotification("Monitoring Wi-Fi networks in background...")
                    }
                }
                networkCallback = fallbackHandler
                connectivityManager.registerNetworkCallback(request, fallbackHandler)
            } catch (e: Exception) {
                // Ignore
            }
        } catch (e: Exception) {
            LogRepository.error("Callback Error", "Failed to register network callback: ${e.localizedMessage}")
        }
    }

    private fun extractSsidFromNetwork(network: Network) {
        try {
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps != null) {
                extractSsidFromCapabilities(caps)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun extractSsidFromCapabilities(capabilities: NetworkCapabilities) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transportInfo = capabilities.transportInfo
                if (transportInfo is WifiInfo) {
                    val ssid = transportInfo.ssid
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        val clean = ssid.removeSurrounding("\"").trim()
                        if (clean.isNotBlank()) {
                            CaptivePortalDetector.latestSsid = clean
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun registerWifiStateReceiver() {
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                    try {
                        @Suppress("DEPRECATION")
                        val netInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                        if (netInfo == null || netInfo.type != ConnectivityManager.TYPE_WIFI || !netInfo.isConnected) {
                            return
                        }

                        val extra = netInfo.extraInfo
                        if (!extra.isNullOrBlank() && extra != "<unknown ssid>") {
                            val clean = extra.removeSurrounding("\"").trim()
                            if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                                CaptivePortalDetector.latestSsid = clean
                            }
                        }

                        val wifiInfoFromIntent = intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)
                        val intentSsid = wifiInfoFromIntent?.ssid
                        if (!intentSsid.isNullOrBlank() && intentSsid != "<unknown ssid>") {
                            val clean = intentSsid.removeSurrounding("\"").trim()
                            if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                                CaptivePortalDetector.latestSsid = clean
                            }
                        }

                        val wifiNet = detector.getPrimaryWifiNetwork()
                        if (wifiNet != null) {
                            bindNetworkAndTrigger(wifiNet, isCaptiveHint = false)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        try {
            registerReceiver(wifiStateReceiver, filter)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private var loginTriggerJob: Job? = null

    private fun bindNetworkAndTrigger(network: Network, isCaptiveHint: Boolean = false) {
        val caps = connectivityManager.getNetworkCapabilities(network)
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(network)
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Fast debounce: 20ms for explicit captive hints, 80ms for general connection
        val delayTime = if (isCaptiveHint) 20L else 80L
        loginTriggerJob?.cancel()
        loginTriggerJob = serviceScope.launch {
            delay(delayTime)
            triggerAutoLoginForCurrentWifi(isManual = false, isKnownCaptive = isCaptiveHint)
        }
    }

    private suspend fun triggerAutoLoginForCurrentWifi(isManual: Boolean, isKnownCaptive: Boolean = false) {
        // Enforce Wi-Fi only: Never run when on Mobile Data or when Wi-Fi is OFF
        if (!detector.isWifiConnected()) {
            return
        }

        val settings = settingsRepository.settings.value
        if (!settings.isMasterAutoLoginEnabled && !isManual) {
            return
        }

        if (isLoggingIn.getAndSet(true)) {
            return
        }

        try {
            if (!detector.isWifiConnected()) {
                return
            }

            // 1. Fast DHCP IP & Gateway resolution (50ms intervals, max 3 tries = 150ms)
            var currentSsid = detector.getCurrentSsid()
            var gatewayIp = detector.getGatewayIpAddress()
            var retryCount = 0
            while ((gatewayIp.isBlank() || gatewayIp == "0.0.0.0") && retryCount < 3) {
                if (!detector.isWifiConnected()) return
                delay(50)
                retryCount++
                currentSsid = detector.getCurrentSsid()
                gatewayIp = detector.getGatewayIpAddress()
            }

            if (!detector.isWifiConnected()) return

            val matchingProfiles = profileRepository.findProfilesForNetwork(currentSsid, gatewayIp)
            val matchedProfile = matchingProfiles.firstOrNull()

            // Exact dynamic SSID resolution (KU_NO-17, KU-ROOM_16, etc.)
            val activeNetworkName = if (currentSsid.isNotBlank() && currentSsid != "<unknown ssid>") {
                currentSsid
            } else if (CaptivePortalDetector.latestSsid.isNotBlank() && CaptivePortalDetector.latestSsid != "<unknown ssid>") {
                CaptivePortalDetector.latestSsid
            } else if (matchedProfile != null && matchedProfile.ssid.isNotBlank()) {
                matchedProfile.ssid
            } else {
                "Campus Wi-Fi"
            }

            // 2. Normal Wi-Fi / Pre-flight check:
            // If not flagged as captive and not manual, check network capabilities or run a fast probe
            if (!isKnownCaptive && !isManual) {
                val wifiNet = detector.getPrimaryWifiNetwork()
                val caps = if (wifiNet != null) connectivityManager.getNetworkCapabilities(wifiNet) else null
                val isAlreadyValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == false

                if (isAlreadyValidated) {
                    // Normal Wi-Fi network with no captive portal - STAY COMPLETELY SILENT
                    LogRepository.info("Normal Wi-Fi", "'$activeNetworkName' is a standard online network. No captive login needed.")
                    return
                }

                val preCheck = detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
                if (preCheck.state == WifiState.CONNECTED_ONLINE && preCheck.httpCode == 204) {
                    // Standard unblocked network / already authenticated - STAY COMPLETELY SILENT
                    LogRepository.info("Normal Wi-Fi", "'$activeNetworkName' has direct internet access (HTTP 204). No captive login needed.")
                    return
                }
            }

            if (currentSsid.isNotBlank()) {
                LogRepository.info("Checking Profile", "Active SSID: '$currentSsid'. Gateway: '$gatewayIp'. Searching saved profiles...")
            } else {
                LogRepository.info("Checking Gateway", "Searching profiles for '$activeNetworkName' (Gateway '$gatewayIp')...")
            }

            // If no profile configured for this captive network, stay silent (do not change notification)
            if (matchingProfiles.isEmpty()) {
                LogRepository.info("No Profile", "No saved auto-login profile configured for '$activeNetworkName' yet.")
                return
            }

            var loginSuccess = false
            var lastErrorMessage = ""

            for ((index, profile) in matchingProfiles.withIndex()) {
                val accountLabel = if (profile.isPrimary) "Primary Account" else "Backup Account #${index + 1}"
                LogRepository.info("Hands-Free Login", "Logging into $activeNetworkName with $accountLabel (${profile.username})...")
                updateNotification("Authenticating on $activeNetworkName...")

                val result = authEngine.executeLogin(profile)

                if (result.isSuccess) {
                    profileRepository.updateProfileLoginStatus(profile.id, "SUCCESS")

                    // Rapid non-blocking multi-pulse OS network revalidation to instantly dismiss "Sign in to Wi-Fi network"
                    triggerImmediateNetworkRevalidation()

                    val realName = detector.getCurrentSsid().ifBlank { activeNetworkName }

                    val shouldShowSuccessPopup = lastNotifiedSuccessNetwork != realName ||
                            (System.currentTimeMillis() - lastNotifiedSuccessTime > 15 * 60 * 1000L)

                    if (settings.showNotifications && shouldShowSuccessPopup) {
                        lastNotifiedSuccessNetwork = realName
                        lastNotifiedSuccessTime = System.currentTimeMillis()
                        showSuccessNotification(realName, profile.username)
                    }

                    // Return foreground notification to silent default
                    updateNotification("Monitoring Wi-Fi networks in background...")
                    loginSuccess = true
                    break
                } else {
                    lastErrorMessage = result.message
                    profileRepository.updateProfileLoginStatus(profile.id, "FAILED")
                    LogRepository.warning("Login Failed", "$accountLabel (${profile.username}) login failed (${result.message}).")
                    if (index < matchingProfiles.size - 1) {
                        LogRepository.info("Auto Failover", "Automatically switching to next backup account...")
                    }
                }
            }

            if (!loginSuccess) {
                updateNotification("Monitoring Wi-Fi networks in background...")
                if (settings.showNotifications) {
                    val displayError = if (lastErrorMessage.isNotBlank()) lastErrorMessage else "Authentication failed. Check your saved username/password."
                    showErrorNotification(activeNetworkName, displayError)
                }
            }

        } catch (e: Exception) {
            LogRepository.error("AutoLogin Error", "Error executing auto-login: ${e.localizedMessage}")
        } finally {
            isLoggingIn.set(false)
        }
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepository.settings.value
                val intervalMs = (settings.keepAliveIntervalMinutes * 60 * 1000L).coerceAtLeast(60000L)
                delay(intervalMs)

                if (settings.isKeepAliveEnabled && detector.isWifiConnected()) {
                    val currentSsid = detector.getCurrentSsid()
                    val gatewayIp = detector.getGatewayIpAddress()
                    val profile = profileRepository.findProfileForNetwork(currentSsid, gatewayIp)

                    if (profile != null && profile.isKeepAliveEnabled) {
                        val probe = detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
                        if (probe.state != WifiState.CONNECTED_ONLINE) {
                            LogRepository.warning("Keep-Alive Trigger", "Connection expired on $currentSsid. Re-authenticating...")
                            triggerAutoLoginForCurrentWifi(isManual = false, isKnownCaptive = true)
                        } else {
                            LogRepository.info("Keep-Alive OK", "Connection active (Ping: ${probe.latencyMs}ms)")
                        }
                    }
                }
            }
        }
    }

    private fun triggerImmediateNetworkRevalidation() {
        serviceScope.launch {
            detector.forceDismissCaptivePortalNotification()
            detector.blastSocket204Probes()

            for (delayMs in listOf(40L, 100L, 200L, 400L, 800L, 1500L)) {
                delay(delayMs)
                try {
                    detector.forceDismissCaptivePortalNotification()
                    detector.blastSocket204Probes()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private fun startInForeground() {
        val notification = createNotification("Monitoring Wi-Fi networks in background...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_SILENT_DAEMON)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("WiFi AutoLogin Pro")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
    }

    private fun showSuccessNotification(ssid: String, username: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Clear any previous error notification upon success
        try {
            notificationManager.cancel(ERROR_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wi-Fi Auto-Login Successful")
            .setContentText("Online: $ssid ($username)")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(6000) // Auto-dismisses after 6 seconds
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)

        // Coroutine fallback to guarantee dismissal after 6 seconds
        serviceScope.launch {
            delay(6000)
            try {
                notificationManager.cancel(SUCCESS_NOTIFICATION_ID)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun showErrorNotification(ssid: String, errorMessage: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.cancel(SUCCESS_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanError = if (errorMessage.isNotBlank()) errorMessage else "Authentication failed. Check your profile credentials."

        val notification = NotificationCompat.Builder(this, CHANNEL_ERRORS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Wi-Fi Login Failed ($ssid)")
            .setContentText(cleanError)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanError))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // User can dismiss or tap to open app
            .setOngoing(false)   // Dismissible by user swipe
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(ERROR_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Silent Background Daemon Channel (Min Importance = No Status Bar Icon, completely quiet)
            val daemonChannel = NotificationChannel(
                CHANNEL_SILENT_DAEMON,
                "Silent Background Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent background keep-alive and network monitor"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(daemonChannel)

            // 2. 6-Second Temporary Connection Alert Channel (Auto-dismisses in 6s)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Wi-Fi Connection Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows temporary 6-second notification when connected"
                setShowBadge(false)
            }
            manager.createNotificationChannel(alertChannel)

            // 3. Persistent Error Alerts Channel (Stays until user manually swipes away)
            val errorChannel = NotificationChannel(
                CHANNEL_ERRORS,
                "Wi-Fi Login Errors & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows critical login errors (wrong password, device limit) that stay until cleared"
                setShowBadge(true)
            }
            manager.createNotificationChannel(errorChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            // Ignore
        }
        try {
            wifiStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
