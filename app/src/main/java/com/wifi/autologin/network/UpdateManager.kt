package com.wifi.autologin.network

import com.google.gson.Gson
import com.wifi.autologin.data.model.UpdateInfo
import com.wifi.autologin.data.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateManager {

    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/tusharcoder13/Wifi-Autologin-Pro/main/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun fetchLatestUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(VERSION_JSON_URL)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val body = response.body?.string() ?: ""
            response.close()

            if (body.isBlank()) return@withContext null

            val updateInfo = gson.fromJson(body, UpdateInfo::class.java) ?: return@withContext null

            if (updateInfo.versionCode > currentVersionCode) {
                LogRepository.info("Update Checker", "Found new update: v${updateInfo.versionName} (Build ${updateInfo.versionCode})")
                updateInfo
            } else {
                LogRepository.info("Update Checker", "App is up to date (Installed: $currentVersionCode, Remote: ${updateInfo.versionCode})")
                null
            }
        } catch (e: Exception) {
            // Silently ignore network failures when checking for updates (e.g. offline or before captive portal login)
            null
        }
    }
}
