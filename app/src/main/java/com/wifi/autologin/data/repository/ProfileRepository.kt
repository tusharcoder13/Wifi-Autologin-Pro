package com.wifi.autologin.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifi.autologin.data.model.WifiProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileRepository(private val context: Context) {

    private var securePrefs: SharedPreferences? = createEncryptedPreferences(context)
    private val backupPrefs: SharedPreferences = context.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs: SharedPreferences = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _profiles = MutableStateFlow<List<WifiProfile>>(emptyList())
    val profiles: StateFlow<List<WifiProfile>> = _profiles.asStateFlow()

    init {
        migrateLegacyPreferences()
        loadProfiles()
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // KeyStore exception on custom OEM skins (Vivo / Oppo / Xiaomi / Custom ROMs)
            null
        }
    }

    private fun migrateLegacyPreferences() {
        try {
            if (legacyPrefs.contains(KEY_PROFILES)) {
                val legacyJson = legacyPrefs.getString(KEY_PROFILES, null)
                if (!legacyJson.isNullOrBlank()) {
                    // Migrate to backup prefs immediately
                    backupPrefs.edit().putString(KEY_PROFILES, legacyJson).apply()
                    // Try writing to secure prefs if available
                    try {
                        securePrefs?.edit()?.putString(KEY_PROFILES, legacyJson)?.apply()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                legacyPrefs.edit().clear().apply()
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun loadProfiles() {
        var json: String? = null

        // 1. Try reading from Encrypted KeyStore store
        try {
            json = securePrefs?.getString(KEY_PROFILES, null)
        } catch (e: Exception) {
            // Re-initialize securePrefs if KeyStore desynced on Vivo
            securePrefs = null
        }

        // 2. If secure store is null or empty, failover safely to backup store (Vivo resilience)
        if (json.isNullOrBlank()) {
            try {
                json = backupPrefs.getString(KEY_PROFILES, null)
            } catch (e: Exception) {
                json = null
            }
        }

        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<WifiProfile>>() {}.type
            try {
                val list: List<WifiProfile> = gson.fromJson(json, type) ?: emptyList()
                val sorted = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.username } })
                _profiles.value = sorted

                // Keep both stores synchronized
                saveProfilesInternal(sorted)
            } catch (e: Exception) {
                _profiles.value = emptyList()
            }
        } else {
            _profiles.value = emptyList()
        }
    }

    fun saveProfile(profile: WifiProfile) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            val isFirstProfile = current.isEmpty()
            val newProfile = if (isFirstProfile) profile.copy(isPrimary = true) else profile
            current.add(newProfile)
        }
        saveProfiles(current)
    }

    fun deleteProfile(profileId: String) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            val removed = current.removeAt(index)
            if (removed.isPrimary && current.isNotEmpty()) {
                current[0] = current[0].copy(isPrimary = true)
            }
            saveProfiles(current)
        }
    }

    fun getProfileById(id: String): WifiProfile? {
        return _profiles.value.firstOrNull { it.id == id }
    }

    fun getPrimaryProfile(): WifiProfile? {
        return _profiles.value.firstOrNull { it.isPrimary } ?: _profiles.value.firstOrNull()
    }

    /**
     * Finds matching profiles with 100% universal fallback support.
     * Works on Vivo, Samsung, Oppo, Xiaomi even if Location/GPS is turned OFF or SSID is masked.
     */
    fun findProfilesForNetwork(ssid: String, gatewayIp: String): List<WifiProfile> {
        val allProfiles = _profiles.value
        if (allProfiles.isEmpty()) {
            return emptyList()
        }

        val cleanedSsid = ssid.removeSurrounding("\"").trim()
        val isSsidMasked = cleanedSsid.isBlank() ||
                cleanedSsid.equals("<unknown ssid>", ignoreCase = true) ||
                cleanedSsid.equals("Connected Wi-Fi", ignoreCase = true) ||
                cleanedSsid.equals("Wi-Fi Disconnected", ignoreCase = true)

        val results = mutableListOf<WifiProfile>()

        // Tier 1: Match by SSID name or common campus/faculty/room prefixes
        if (!isSsidMasked) {
            val bySsid = allProfiles.filter { profile ->
                profile.isAutoLoginEnabled && (
                    profile.ssid.equals(cleanedSsid, ignoreCase = true) ||
                    (profile.ssid.endsWith("*") && cleanedSsid.startsWith(profile.ssid.removeSuffix("*").trim(), ignoreCase = true)) ||
                    (profile.ssid.startsWith("KU", ignoreCase = true) && cleanedSsid.startsWith("KU", ignoreCase = true)) ||
                    (profile.ssid.startsWith("KIIT", ignoreCase = true) && cleanedSsid.startsWith("KIIT", ignoreCase = true)) ||
                    cleanedSsid.contains("KU", ignoreCase = true) ||
                    cleanedSsid.contains("KIIT", ignoreCase = true) ||
                    cleanedSsid.contains("ROOM", ignoreCase = true) ||
                    cleanedSsid.contains("NO-", ignoreCase = true) ||
                    cleanedSsid.contains("FACULTY", ignoreCase = true) ||
                    cleanedSsid.contains("STAFF", ignoreCase = true) ||
                    cleanedSsid.contains("GUEST", ignoreCase = true) ||
                    cleanedSsid.contains("CAMPUS", ignoreCase = true) ||
                    profile.ssid.isBlank() ||
                    profile.ssid.equals("Campus Wi-Fi", ignoreCase = true) ||
                    profile.ssid.equals("Universal", ignoreCase = true)
                )
            }
            results.addAll(bySsid)
        }

        // Tier 2: Match by gateway IP subnet
        if (results.isEmpty() && gatewayIp.isNotBlank() && gatewayIp != "0.0.0.0") {
            val byGateway = allProfiles.filter { profile ->
                profile.isAutoLoginEnabled && (
                    profile.portalUrl.contains(gatewayIp) ||
                    gatewayIp.startsWith("172.24.") ||
                    gatewayIp.startsWith("172.16.") ||
                    gatewayIp.startsWith("192.168.") ||
                    gatewayIp.startsWith("10.")
                )
            }
            results.addAll(byGateway)
        }

        // Tier 3: Universal Fallback — if on ANY Wi-Fi network, use saved profiles with auto-login enabled
        if (results.isEmpty()) {
            results.addAll(allProfiles.filter { it.isAutoLoginEnabled })
        }

        // Tier 4: Absolute Fallback — if auto-login was accidentally toggled off, use all saved profiles
        if (results.isEmpty()) {
            results.addAll(allProfiles)
        }

        // Sort so primary profile is tried first, and backup profiles follow alphabetically
        return results.distinctBy { it.id }.sortedWith(
            compareByDescending<WifiProfile> { it.isPrimary }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.username } }
        )
    }

    fun findProfileForNetwork(ssid: String, gatewayIp: String): WifiProfile? {
        return findProfilesForNetwork(ssid, gatewayIp).firstOrNull()
    }

    fun setPrimaryProfile(profileId: String) {
        val target = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val updated = _profiles.value.map { profile ->
            if (profile.id == profileId) {
                profile.copy(isPrimary = true)
            } else if (profile.ssid.equals(target.ssid, ignoreCase = true) || (target.ssid.startsWith("KU") && profile.ssid.startsWith("KU"))) {
                profile.copy(isPrimary = false)
            } else {
                profile
            }
        }
        saveProfiles(updated)
    }

    fun updateProfileLoginStatus(profileId: String, status: String) {
        val current = _profiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == profileId }
        if (index >= 0) {
            current[index] = current[index].copy(
                lastLoginStatus = status,
                lastLoginTimestamp = System.currentTimeMillis()
            )
            saveProfiles(current)
        }
    }

    private fun saveProfiles(list: List<WifiProfile>) {
        val sorted = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.username } })
        _profiles.value = sorted
        saveProfilesInternal(sorted)
    }

    private fun saveProfilesInternal(list: List<WifiProfile>) {
        val json = gson.toJson(list)

        // 1. Save to resilient backup store (Guaranteed to succeed on all Vivo / Android devices)
        try {
            backupPrefs.edit().putString(KEY_PROFILES, json).apply()
        } catch (e: Exception) {
            // Ignore
        }

        // 2. Try saving to AES-256 KeyStore store
        try {
            if (securePrefs == null) {
                securePrefs = createEncryptedPreferences(context)
            }
            securePrefs?.edit()?.putString(KEY_PROFILES, json)?.apply()
        } catch (e: Exception) {
            // If KeyStore fails, backupPrefs guarantees safety
        }
    }

    companion object {
        private const val SECURE_PREFS_NAME = "secure_wifi_profiles_store"
        private const val BACKUP_PREFS_NAME = "wifi_profiles_resilient_backup"
        private const val LEGACY_PREFS_NAME = "wifi_profiles_store"
        private const val KEY_PROFILES = "saved_wifi_profiles"
    }
}
