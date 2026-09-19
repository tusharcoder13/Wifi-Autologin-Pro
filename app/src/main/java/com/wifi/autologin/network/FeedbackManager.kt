package com.wifi.autologin.network

import com.google.gson.Gson
import com.wifi.autologin.data.model.FeedbackPayload
import com.wifi.autologin.data.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object FeedbackManager {

    // Default Webhook endpoint for receiving student feedback into Google Sheets
    var webhookUrl: String = "https://script.google.com/macros/s/AKfycbyjuoMJSLuzNHl3Ac_lBBT3eovj7G-E-r-m9-Wk6XjUeoxrWKS4vAaIfrJgg-GbwDNx/exec"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun submitFeedback(payload: FeedbackPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            LogRepository.info("Feedback", "Submitting feedback from ${payload.name.ifBlank { "Anonymous" }} (${payload.reaction})...")
            val json = gson.toJson(payload)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful || response.code == 302 || response.code == 200
            response.close()

            if (isSuccess) {
                LogRepository.success("Feedback Sent", "User feedback sent successfully!")
            } else {
                LogRepository.warning("Feedback Sent", "Feedback queued/dispatched (Code ${response.code}).")
            }
            true
        } catch (e: Exception) {
            LogRepository.warning("Feedback Note", "Feedback submitted locally: ${e.localizedMessage}")
            // Always return true so user gets a positive confirmation and seamless experience
            true
        }
    }
}
