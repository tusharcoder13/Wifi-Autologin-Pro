package com.wifi.autologin.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifi.autologin.data.model.WifiProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_profiles_store", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _profiles = MutableStateFlow<List<WifiProfile>>(emptyList())
    val profiles: StateFlow<List<WifiProfile>> = _profiles.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val json = prefs.getString(KEY_PROFILES, null)
        if (json != null) {
            val type = object : TypeToken<List<WifiProfile>>() {}.type
            try {
                val list: List<WifiProfile> = gson.fromJson(json, type) ?: emptyList()
                val sorted = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.username } })
                _profiles.value = sorted
            } catch (e: Exception) {
                _profiles.value = emptyList()
            }
        } else {
            // Start with an empty list on fresh installs so each user enters their own private credentials
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

    fun findProfilesForNetwork(ssid: String, gatewayIp: String): List<WifiProfile> {
        val cleanedSsid = ssid.removeSurrounding("\"").trim()
        val results = mutableListOf<WifiProfile>()

        if (cleanedSsid.isNotBlank()) {
            val bySsid = _profiles.value.filter { profile ->
                profile.isAutoLoginEnabled && (
                    profile.ssid.equals(cleanedSsid, ignoreCase = true) ||
                    (profile.ssid.endsWith("*") && cleanedSsid.startsWith(profile.ssid.removeSuffix("*").trim(), ignoreCase = true)) ||
                    (profile.ssid.startsWith("KU", ignoreCase = true) && cleanedSsid.startsWith("KU", ignoreCase = true)) ||
                    cleanedSsid.contains("KU", ignoreCase = true) ||
                    cleanedSsid.contains("ROOM", ignoreCase = true) ||
                    cleanedSsid.contains("NO-", ignoreCase = true) ||
                    profile.ssid.isBlank() ||
                    profile.ssid.equals("Campus Wi-Fi", ignoreCase = true)
                )
            }
            results.addAll(bySsid)
        }

        if (results.isEmpty() && gatewayIp.isNotBlank()) {
            val byGateway = _profiles.value.filter { profile ->
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

        if (results.isEmpty()) {
            results.addAll(_profiles.value.filter { it.isAutoLoginEnabled })
        }

        // Sort so primary profile is tried first, and other backup profiles follow alphabetically
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
        val json = gson.toJson(sorted)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    companion object {
        private const val KEY_PROFILES = "saved_wifi_profiles"
    }
}
