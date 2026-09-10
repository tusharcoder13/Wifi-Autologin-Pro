package com.wifi.autologin.data.model

import java.util.UUID

/**
 * Represents a saved Wi-Fi Captive Portal profile.
 */
data class WifiProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,                    // e.g. "Campus Wi-Fi", "Hostel Block A"
    val ssid: String,                    // Wi-Fi SSID e.g. "Campus_WiFi"
    val portalUrl: String = "",          // Login URL (e.g. http://192.168.1.1:1000/login or https://portal.campus.edu)
    val username: String = "",          // Wi-Fi ID / Roll Number / Username
    val password: String = "",          // Wi-Fi Password
    val presetType: String = "GENERIC",  // GENERIC, FORTINET, SOPHOS, MIKROTIK, CISCO
    val httpMethod: String = "POST",     // POST or GET
    val usernameField: String = "username", // HTML input name for username
    val passwordField: String = "password", // HTML input name for password
    val extraFields: Map<String, String> = emptyMap(), // Hidden tokens, magic numbers, producttype, dst, etc.
    val isAutoLoginEnabled: Boolean = true,
    val isKeepAliveEnabled: Boolean = true,
    val isPrimary: Boolean = true,
    val lastLoginTimestamp: Long = 0L,
    val lastLoginStatus: String = "NEVER_RUN" // SUCCESS, FAILED, NEVER_RUN
)
