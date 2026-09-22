package com.wifi.autologin.network

import android.annotation.SuppressLint
import android.content.Context
import com.wifi.autologin.data.model.WifiProfile
import com.wifi.autologin.data.model.WifiState
import com.wifi.autologin.data.repository.AppSettingsRepository
import com.wifi.autologin.data.repository.LogRepository
import kotlinx.coroutines.*
import okhttp3.*
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class AuthResult(
    val isSuccess: Boolean,
    val httpCode: Int = 0,
    val message: String,
    val portalTargetUrl: String = "",
    val details: String? = null
)

class AuthEngine(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository
) {

    private val detector = CaptivePortalDetector(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    private fun getHttpClient(bypassSsl: Boolean): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cookieJar(cookieJar)
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(3500, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        // Force OkHttp to route over the active Wi-Fi interface
        try {
            val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifiNetwork != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    connectivityManager.bindProcessToNetwork(wifiNetwork)
                }
                builder.socketFactory(wifiNetwork.socketFactory)
            }
        } catch (e: Exception) {
            // Ignore fallback
        }

        if (bypassSsl) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    @SuppressLint("CustomX509TrustManager")
                    object : X509TrustManager {
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        @SuppressLint("TrustAllX509TrustManager")
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
        return builder.build()
    }

    /**
     * Executes targeted single-shot login to the active gateway endpoint.
     * Consumes ONLY 1 session slot on the router, preserving other devices (like Laptop).
     */
    suspend fun executeLogin(profile: WifiProfile): AuthResult = withContext(Dispatchers.IO) {
        val settings = appSettingsRepository.settings.value
        LogRepository.info("Auth Started", "Attempting login for '${profile.name}' (${profile.username})...")

        val client = getHttpClient(settings.bypassSslErrors)
        val gateway = detector.getGatewayIpAddress()
        val currentIp = detector.getCurrentIpAddress()
        val cleanProfile = profile

        // 1. Determine primary targeted endpoint
        val primaryTargetUrl = when {
            cleanProfile.portalUrl.isNotBlank() && cleanProfile.portalUrl.startsWith("http") ->
                cleanProfile.portalUrl
            gateway.isNotBlank() && gateway != "0.0.0.0" && gateway != "127.0.0.1" ->
                "http://$gateway:8090/httpclient.html"
            currentIp.isNotBlank() && currentIp != "0.0.0.0" -> {
                val parts = currentIp.split(".")
                if (parts.size == 4) "http://${parts[0]}.${parts[1]}.${parts[2]}.1:8090/httpclient.html" else "http://172.24.16.1:8090/httpclient.html"
            }
            else -> "http://172.24.16.1:8090/httpclient.html"
        }

        LogRepository.info("Target Endpoint", "Single-shot auth targeted to: $primaryTargetUrl")

        // 2. Perform Single-Shot POST on primary endpoint
        val primaryResult = postSingleAuth(client, primaryTargetUrl, cleanProfile)
        if (primaryResult != null) {
            if (primaryResult.isSuccess) {
                detector.forceDismissCaptivePortalNotification()
                detector.blastSocket204Probes()
                LogRepository.success("Login Success", "Instant login verified on $primaryTargetUrl!")
                return@withContext primaryResult
            } else {
                // Return explicit error (e.g. limit reached, wrong password) immediately for fast failover
                return@withContext primaryResult
            }
        }

        // 3. Fallback: If primary target gateway timed out/failed, try default campus cluster gateway once
        val fallbackUrl = "http://172.24.16.1:8090/httpclient.html"
        if (primaryTargetUrl != fallbackUrl) {
            LogRepository.info("Fallback Gateway", "Retrying on campus cluster gateway: $fallbackUrl")
            val fallbackResult = postSingleAuth(client, fallbackUrl, cleanProfile)
            if (fallbackResult != null) {
                if (fallbackResult.isSuccess) {
                    detector.forceDismissCaptivePortalNotification()
                    detector.blastSocket204Probes()
                    LogRepository.success("Login Success", "Instant login verified on fallback gateway!")
                }
                return@withContext fallbackResult
            }
        }

        // 3b. Probe-based Intercepted Portal URL Fallback
        val probe = detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
        if (probe.portalUrl.isNotBlank() && probe.portalUrl != primaryTargetUrl && probe.portalUrl != fallbackUrl) {
            LogRepository.info("Intercepted Portal", "Retrying on detected portal: ${probe.portalUrl}")
            val probeResult = postSingleAuth(client, probe.portalUrl, cleanProfile)
            if (probeResult != null) {
                if (probeResult.isSuccess) {
                    detector.forceDismissCaptivePortalNotification()
                    detector.blastSocket204Probes()
                    LogRepository.success("Login Success", "Instant login verified on intercepted portal!")
                }
                return@withContext probeResult
            }
        }

        // 4. Secondary Fallback: Headless WebKit Browser Engine if raw HTTP endpoints were blocked
        val headlessUrl = if (probe.portalUrl.isNotBlank()) probe.portalUrl else primaryTargetUrl
        LogRepository.info("Auto-Engine", "Cascading to Headless WebKit Engine on '$headlessUrl' for ${cleanProfile.username}...")
        val webResult = WebViewLoginEngine.executeHeadlessLogin(context, cleanProfile, detector, headlessUrl)
        if (webResult.isSuccess) {
            detector.forceDismissCaptivePortalNotification()
            detector.blastSocket204Probes()
            return@withContext webResult
        }

        AuthResult(
            isSuccess = false,
            message = if (webResult.message.isNotBlank() && !webResult.message.contains("timed out", ignoreCase = true)) webResult.message else "Login Failed: Server unreachable or credentials rejected (${cleanProfile.username}).",
            portalTargetUrl = headlessUrl
        )
    }

    private suspend fun postSingleAuth(client: OkHttpClient, url: String, cleanProfile: WifiProfile): AuthResult? = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("mode", "191")
                .add("username", cleanProfile.username.trim())
                .add("password", cleanProfile.password.trim())
                .add("a", System.currentTimeMillis().toString())
                .add("producttype", "0")
                .add("saveinfo", "1")
                .add("popup", "0")
                .add("dst", "http://connectivitycheck.gstatic.com/generate_204")
                .build()

            val req = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", url)
                .header("Origin", url.split("/").take(3).joinToString("/"))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val snippet = if (body.length > 100) body.take(100).replace("\n", " ").trim() else body.trim()
            LogRepository.info("Direct POST", "POST $url -> HTTP ${resp.code}: $snippet")

            val messageMatch = Regex("<message>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?<\\/message>", RegexOption.IGNORE_CASE).find(body)
            val messageVal = messageMatch?.groupValues?.get(1)?.trim() ?: ""

            val isExplicitFailure = messageVal.contains("could not log you on", ignoreCase = true) ||
                    messageVal.contains("incorrect", ignoreCase = true) ||
                    messageVal.contains("limit reached", ignoreCase = true) ||
                    messageVal.contains("exceeded", ignoreCase = true) ||
                    messageVal.contains("failed", ignoreCase = true) ||
                    messageVal.contains("denied", ignoreCase = true) ||
                    messageVal.contains("another ip", ignoreCase = true) ||
                    messageVal.contains("another device", ignoreCase = true) ||
                    messageVal.contains("different ip", ignoreCase = true) ||
                    messageVal.contains("maximum", ignoreCase = true)

            val isExplicitSuccess = messageVal.contains("successfully", ignoreCase = true) ||
                    messageVal.contains("already logged in", ignoreCase = true) ||
                    messageVal.contains("success", ignoreCase = true) ||
                    body.contains("<ack>ACK</ack>", ignoreCase = true) ||
                    body.contains("successfully signed in", ignoreCase = true) ||
                    body.contains("successfully logged in", ignoreCase = true) ||
                    body.contains("status=1", ignoreCase = true) ||
                    (body.contains("<status><![CDATA[LOGIN]]></status>", ignoreCase = true) && !isExplicitFailure) ||
                    (body.contains("<status><![CDATA[LIVE]]></status>", ignoreCase = true) && !isExplicitFailure) ||
                    (body.contains("<status><![CDATA[SUCCESS]]></status>", ignoreCase = true) && !isExplicitFailure)

            if (isExplicitSuccess && !isExplicitFailure) {
                val msg = if (messageVal.isNotBlank()) messageVal else "Instant login verified!"
                AuthResult(isSuccess = true, httpCode = resp.code, message = msg, portalTargetUrl = url)
            } else if (isExplicitFailure) {
                val formattedMsg = formatLoginErrorMessage(messageVal, cleanProfile.username)
                LogRepository.warning("Auth Response", formattedMsg)
                AuthResult(isSuccess = false, httpCode = resp.code, message = formattedMsg, portalTargetUrl = url)
            } else {
                null
            }
        } catch (e: Exception) {
            LogRepository.info("Direct POST", "POST $url error: ${e.localizedMessage ?: e.javaClass.simpleName}")
            null
        }
    }

    /**
     * Sends Cyberoam/Sophos logout mode (193) to release ghost/stale sessions stuck on the router.
     */
    suspend fun forceReleaseSession(profile: WifiProfile): Boolean = withContext(Dispatchers.IO) {
        val settings = appSettingsRepository.settings.value
        val client = getHttpClient(settings.bypassSslErrors)
        val gateway = detector.getGatewayIpAddress()

        val targetUrl = when {
            profile.portalUrl.isNotBlank() && profile.portalUrl.startsWith("http") -> profile.portalUrl
            gateway.isNotBlank() && gateway != "0.0.0.0" -> "http://$gateway:8090/httpclient.html"
            else -> "http://172.24.16.1:8090/httpclient.html"
        }

        try {
            LogRepository.info("Session Release", "Sending session release (mode 193) for ${profile.username} to $targetUrl...")
            val formBody = FormBody.Builder()
                .add("mode", "193") // Cyberoam / Sophos logout mode
                .add("username", profile.username.trim())
                .add("password", profile.password.trim())
                .add("a", System.currentTimeMillis().toString())
                .add("producttype", "0")
                .build()

            val req = Request.Builder()
                .url(targetUrl)
                .post(formBody)
                .build()

            val resp = client.newCall(req).execute()
            resp.close()
            LogRepository.success("Session Released", "Session release request dispatched successfully.")
            true
        } catch (e: Exception) {
            LogRepository.warning("Session Release", "Failed to dispatch session release: ${e.localizedMessage}")
            false
        }
    }

    private fun formatLoginErrorMessage(rawMessage: String, username: String): String {
        val clean = rawMessage.lowercase()
        return when {
            clean.contains("could not log you on") || clean.contains("password") || clean.contains("incorrect") || clean.contains("invalid") ->
                "Login Failed: Username / Password is wrong. Check your profile ($username)."
            clean.contains("maximum login limit") || clean.contains("limit reached") ->
                "Login Failed: Maximum device login limit reached for $username."
            clean.contains("data transfer") || clean.contains("quota exceeded") || clean.contains("exceeded") ->
                "Login Failed: Data transfer limit exceeded for $username."
            clean.contains("disabled") || clean.contains("expired") || clean.contains("denied") ->
                "Login Failed: Account ($username) is disabled or expired."
            rawMessage.isNotBlank() ->
                "Login Failed: $rawMessage"
            else ->
                "Login Failed: Username / Password is wrong. Check profile ($username)."
        }
    }
}
