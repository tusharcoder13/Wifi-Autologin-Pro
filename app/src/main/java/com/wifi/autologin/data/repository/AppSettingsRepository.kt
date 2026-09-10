package com.wifi.autologin.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val isMasterAutoLoginEnabled: Boolean = true,
    val isKeepAliveEnabled: Boolean = true,
    val keepAliveIntervalMinutes: Int = 5,
    val showNotifications: Boolean = true,
    val autoRetryOnDisconnect: Boolean = true,
    val bypassSslErrors: Boolean = true, // Essential for captive portals with self-signed certs
    val customProbeUrl: String = "http://connectivitycheck.gstatic.com/generate_204"
)

class AppSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings_store", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            isMasterAutoLoginEnabled = prefs.getBoolean("master_auto_login", true),
            isKeepAliveEnabled = prefs.getBoolean("keep_alive", true),
            keepAliveIntervalMinutes = prefs.getInt("keep_alive_interval", 5),
            showNotifications = prefs.getBoolean("show_notifications", true),
            autoRetryOnDisconnect = prefs.getBoolean("auto_retry", true),
            bypassSslErrors = prefs.getBoolean("bypass_ssl", true),
            customProbeUrl = prefs.getString("probe_url", "http://connectivitycheck.gstatic.com/generate_204") ?: "http://connectivitycheck.gstatic.com/generate_204"
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putBoolean("master_auto_login", newSettings.isMasterAutoLoginEnabled)
            putBoolean("keep_alive", newSettings.isKeepAliveEnabled)
            putInt("keep_alive_interval", newSettings.keepAliveIntervalMinutes)
            putBoolean("show_notifications", newSettings.showNotifications)
            putBoolean("auto_retry", newSettings.autoRetryOnDisconnect)
            putBoolean("bypass_ssl", newSettings.bypassSslErrors)
            putString("probe_url", newSettings.customProbeUrl)
            apply()
        }
    }
}
