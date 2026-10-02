package com.wifi.autologin.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wifi.autologin.data.model.FeedbackPayload
import com.wifi.autologin.data.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object FeedbackManager {

    private const val PREFS_NAME = "feedback_prefs"
    private const val KEY_PENDING_FEEDBACKS = "pending_feedbacks_queue"
    private val queueMutex = Mutex()

    // Default Webhook endpoint for receiving student feedback into Google Sheets
    var webhookUrl: String = "https://script.google.com/macros/s/AKfycbyjuoMJSLuzNHl3Ac_lBBT3eovj7G-E-r-m9-Wk6XjUeoxrWKS4vAaIfrJgg-GbwDNx/exec"

    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getClient(context: Context?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    // If cellular or any network has validated internet, prefer its socket factory
                    val validatedNetwork = cm.allNetworks.firstOrNull { net ->
                        val caps = cm.getNetworkCapabilities(net)
                        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }
                    if (validatedNetwork != null) {
                        builder.socketFactory(validatedNetwork.socketFactory)
                    }
                }
            } catch (e: Exception) {
                // Fallback to default routing
            }
        }
        return builder.build()
    }

    suspend fun submitFeedback(context: Context, payload: FeedbackPayload): Boolean = withContext(Dispatchers.IO) {
        val nameLabel = payload.name.ifBlank { "Anonymous" }
        LogRepository.info("Feedback", "Submitting feedback from $nameLabel (${payload.reaction})...")

        // First attempt direct send (works if phone has internet or active cellular data)
        val success = sendPayloadDirectly(context, payload)
        if (success) {
            LogRepository.success("Feedback Sent", "User feedback sent successfully to Google Sheets!")
            return@withContext true
        }

        // If offline or captive portal blocks Google DNS, save in offline queue
        enqueueFeedback(context, payload)
        LogRepository.warning("Feedback Queued", "Network offline / portal locked. Feedback saved offline and will send automatically once internet connects.")
        true
    }

    private fun sendPayloadDirectly(context: Context, payload: FeedbackPayload): Boolean {
        return try {
            val json = gson.toJson(payload)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
            val client = getClient(context)

            val request = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful || response.code == 302 || response.code == 200
            response.close()
            isSuccess
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun enqueueFeedback(context: Context, payload: FeedbackPayload) = queueMutex.withLock {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingJson = prefs.getString(KEY_PENDING_FEEDBACKS, null)
            val listType = object : TypeToken<MutableList<FeedbackPayload>>() {}.type
            val queue: MutableList<FeedbackPayload> = if (existingJson != null) {
                gson.fromJson(existingJson, listType) ?: mutableListOf()
            } else {
                mutableListOf()
            }
            queue.add(payload)
            prefs.edit().putString(KEY_PENDING_FEEDBACKS, gson.toJson(queue)).apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    suspend fun flushQueuedFeedback(context: Context) = withContext(Dispatchers.IO) {
        queueMutex.withLock {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val existingJson = prefs.getString(KEY_PENDING_FEEDBACKS, null) ?: return@withLock
                val listType = object : TypeToken<MutableList<FeedbackPayload>>() {}.type
                val queue: MutableList<FeedbackPayload> = gson.fromJson(existingJson, listType) ?: return@withLock
                if (queue.isEmpty()) return@withLock

                val iterator = queue.iterator()
                var sentCount = 0
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    if (sendPayloadDirectly(context, item)) {
                        iterator.remove()
                        sentCount++
                    }
                }

                prefs.edit().putString(KEY_PENDING_FEEDBACKS, gson.toJson(queue)).apply()
                if (sentCount > 0) {
                    LogRepository.success("Feedback Synced", "$sentCount offline feedback(s) successfully delivered to Google Sheets!")
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
