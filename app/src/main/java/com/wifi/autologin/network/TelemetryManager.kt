package com.wifi.autologin.network

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.wifi.autologin.data.model.InstallPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

object TelemetryManager {

    private const val PREFS_NAME = "telemetry_prefs"
    private const val KEY_INSTALL_ID = "unique_install_id"
    private const val KEY_REPORTED_VERSION = "reported_version_code"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Records a unique install / app update in Google Sheets (Sheet2)
     * Runs completely in background without blocking the UI.
     */
    fun trackInstallIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var installId = prefs.getString(KEY_INSTALL_ID, null)
        val isFirstLaunch = installId == null

        if (installId == null) {
            installId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
        }

        val lastReportedVersion = prefs.getInt(KEY_REPORTED_VERSION, 0)
        val currentVersionCode = 3 // v3.0.0

        if (lastReportedVersion < currentVersionCode) {
            val eventType = if (isFirstLaunch) "NEW_INSTALL" else "APP_UPDATE"
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val deviceModel = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            val payload = InstallPayload(
                type = "install",
                installId = installId,
                event = eventType,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                appVersion = "3.0.0",
                timestamp = System.currentTimeMillis()
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = gson.toJson(payload)
                    val body = json.toRequestBody(JSON_MEDIA_TYPE)
                    val request = Request.Builder()
                        .url(FeedbackManager.webhookUrl)
                        .post(body)
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful || response.code == 302 || response.code == 200) {
                        prefs.edit().putInt(KEY_REPORTED_VERSION, currentVersionCode).apply()
                    }
                    response.close()
                } catch (e: Exception) {
                    // Fail silently, retry on next launch
                }
            }
        }
    }
}
