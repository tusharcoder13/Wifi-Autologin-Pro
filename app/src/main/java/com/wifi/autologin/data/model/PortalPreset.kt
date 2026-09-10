package com.wifi.autologin.data.model

/**
 * Pre-configured presets for common enterprise and campus captive portals.
 */
enum class PortalPreset(
    val displayName: String,
    val description: String,
    val defaultHttpMethod: String,
    val defaultUsernameField: String,
    val defaultPasswordField: String,
    val defaultExtraFields: Map<String, String>,
    val defaultUrlPattern: String
) {
    GENERIC(
        displayName = "Standard HTML Web Portal",
        description = "Works with most college, hostel, and hotel web login forms",
        defaultHttpMethod = "POST",
        defaultUsernameField = "username",
        defaultPasswordField = "password",
        defaultExtraFields = emptyMap(),
        defaultUrlPattern = ""
    ),
    FORTINET(
        displayName = "Fortinet / FortiGate Portal",
        description = "Common university & enterprise firewall gateway (Port 1000/1003)",
        defaultHttpMethod = "POST",
        defaultUsernameField = "username",
        defaultPasswordField = "password",
        defaultExtraFields = mapOf("magic" to "", "4Tredir" to ""),
        defaultUrlPattern = "https://1.1.1.1:1003/login"
    ),
    SOPHOS_CYBEROAM(
        displayName = "Sophos / Cyberoam / Cyberores",
        description = "Captive portal running on port 8090",
        defaultHttpMethod = "POST",
        defaultUsernameField = "username",
        defaultPasswordField = "password",
        defaultExtraFields = mapOf("mode" to "191", "producttype" to "0"),
        defaultUrlPattern = "http://172.16.16.16:8090/login.xml"
    ),
    MIKROTIK(
        displayName = "MikroTik RouterOS Hotspot",
        description = "Hotspot gateway using /login or /login.html",
        defaultHttpMethod = "POST",
        defaultUsernameField = "username",
        defaultPasswordField = "password",
        defaultExtraFields = mapOf("dst" to ""),
        defaultUrlPattern = "http://192.168.88.1/login"
    ),
    CISCO_MERAKI(
        displayName = "Cisco Meraki / Aruba ClearPass",
        description = "Splash page authentication",
        defaultHttpMethod = "POST",
        defaultUsernameField = "user",
        defaultPasswordField = "password",
        defaultExtraFields = emptyMap(),
        defaultUrlPattern = ""
    ),
    KALINGA_UNIVERSITY(
        displayName = "Kalinga University Portal",
        description = "Kalinga University Campus & Hostel Wi-Fi (KU-ROOM, 172.24.16.1)",
        defaultHttpMethod = "POST",
        defaultUsernameField = "username",
        defaultPasswordField = "password",
        defaultExtraFields = emptyMap(),
        defaultUrlPattern = "http://172.24.16.1/"
    )
}
