package com.wifi.autologin.data.model

enum class WifiState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_NO_INTERNET,
    CAPTIVE_PORTAL_DETECTED,
    CONNECTED_ONLINE
}

data class ConnectionStatus(
    val state: WifiState = WifiState.DISCONNECTED,
    val ssid: String = "",
    val ipAddress: String = "",
    val gatewayIp: String = "",
    val portalUrl: String = "",
    val pingLatencyMs: Long = -1L,
    val matchedProfile: WifiProfile? = null,
    val matchingProfiles: List<WifiProfile> = emptyList(),
    val isAutoLoginRunning: Boolean = false,
    val isKeepAliveActive: Boolean = false,
    val lastLoginMessage: String = "",
    val rssi: Int = -127,
    val signalPercent: Int = 0,
    val signalQuality: String = "",
    val linkSpeedMbps: Int = 0,
    val wifiFrequencyBand: String = ""
)

data class SpeedTestResult(
    val isRunning: Boolean = false,
    val progressPercent: Float = 0f,
    val currentStep: String = "",
    val gatewayLatencyMs: Long = -1L,
    val internetLatencyMs: Long = -1L,
    val downloadSpeedMbps: Double = 0.0,
    val rating: String = "",
    val errorMessage: String? = null
)

data class FormDetectionResult(
    val isSuccessful: Boolean = false,
    val actionUrl: String = "",
    val httpMethod: String = "POST",
    val detectedUsernameField: String = "",
    val detectedPasswordField: String = "",
    val hiddenFields: Map<String, String> = emptyMap(),
    val rawHtml: String = "",
    val errorMessage: String? = null
)
