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
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
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
     * Executes parallel login combining live dynamic wire discovery + campus server clusters.
     */
    suspend fun executeLogin(profile: WifiProfile): AuthResult = withContext(Dispatchers.IO) {
        val settings = appSettingsRepository.settings.value
        LogRepository.info("Auth Started", "Logging in for '${profile.name}' (${profile.username})")

        val client = getHttpClient(settings.bypassSslErrors)
        val gateway = detector.getGatewayIpAddress()

        val cleanProfile = profile

        // 1. DYNAMIC DISCOVERY: Live probe interrogation to capture exact portal address on the wire
        val probeResult = detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
        val discoveredPortal = probeResult.portalUrl.trim()

        val discoveredHost = try {
            if (discoveredPortal.isNotBlank() && discoveredPortal.startsWith("http")) {
                val uri = URI(discoveredPortal)
                uri.host ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }

        LogRepository.info("Portal Discovery", "Dynamically discovered portal: '$discoveredPortal' (Host: '$discoveredHost', Gateway: '$gateway')")

        // 2. Build target URLs dynamically based on live discovered endpoints + campus servers
        val dynamicTargets = mutableListOf<String>()

        // Helper to add both http and https for an endpoint host/path
        fun addHostEndpoints(host: String) {
            if (host.isBlank()) return
            val cleanHost = host.removePrefix("http://").removePrefix("https://").split("/").firstOrNull() ?: host
            val portHost = if (cleanHost.contains(":")) cleanHost else "$cleanHost:8090"
            val rawHost = cleanHost.split(":").firstOrNull() ?: cleanHost

            dynamicTargets.add("http://$portHost/httpclient.html")
            dynamicTargets.add("https://$portHost/httpclient.html")
            dynamicTargets.add("http://$portHost/login.xml")
            dynamicTargets.add("https://$portHost/login.xml")
            dynamicTargets.add("http://$portHost/")
            dynamicTargets.add("https://$portHost/")
            if (rawHost != portHost) {
                dynamicTargets.add("http://$rawHost/httpclient.html")
                dynamicTargets.add("https://$rawHost/httpclient.html")
                dynamicTargets.add("http://$rawHost/login.xml")
                dynamicTargets.add("https://$rawHost/login.xml")
            }
        }

        // Add the exact live intercepted URL
        if (discoveredPortal.isNotBlank() && discoveredPortal.startsWith("http")) {
            dynamicTargets.add(discoveredPortal)
            val clean = discoveredPortal.split("?").firstOrNull() ?: discoveredPortal
            if (!clean.endsWith("/httpclient.html")) {
                dynamicTargets.add(clean.trimEnd('/') + "/httpclient.html")
            }
            if (!clean.endsWith("/login.xml")) {
                dynamicTargets.add(clean.trimEnd('/') + "/login.xml")
            }
        }

        // Add dynamically discovered host endpoints
        if (discoveredHost.isNotBlank()) {
            addHostEndpoints(discoveredHost)
        }

        // Add active DHCP gateway endpoints
        if (gateway.isNotBlank() && gateway != "0.0.0.0" && gateway != "127.0.0.1") {
            addHostEndpoints(gateway)
        }

        // Dynamically derive subnet gateway from local IP (e.g. 172.24.31.x -> 172.24.31.1)
        val currentIp = detector.getCurrentIpAddress()
        if (currentIp.isNotBlank() && currentIp != "0.0.0.0") {
            val parts = currentIp.split(".")
            if (parts.size == 4) {
                val derivedGateway = "${parts[0]}.${parts[1]}.${parts[2]}.1"
                addHostEndpoints(derivedGateway)
            }
        }

        // Add known Kalinga campus room gateway clusters (172.24.16.1, 172.24.64.1, 172.24.31.1, 172.24.17.1, 172.24.8.1, 172.24.1.1)
        listOf("172.24.16.1", "172.24.64.1", "172.24.31.1", "172.24.17.1", "172.24.8.1", "172.24.1.1").forEach { gw ->
            addHostEndpoints(gw)
        }

        // Add user-saved profile portal URL if provided
        if (profile.portalUrl.isNotBlank() && profile.portalUrl.startsWith("http")) {
            dynamicTargets.add(profile.portalUrl)
        }

        val distinctUrls = dynamicTargets.distinct()
        LogRepository.info("Target Endpoints", "Dispatching parallel authentication to: $distinctUrls")

        // 3. FAST PATH: Parallel Direct Socket POST to all target endpoints
        val results = coroutineScope {
            distinctUrls.map { url ->
                async(Dispatchers.IO) {
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

                        // Extract status and message from XML / HTML response
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
                        null
                    }
                }
            }.awaitAll()
        }

        val successfulResult = results.filterNotNull().firstOrNull { it.isSuccess }
        if (successfulResult != null) {
            detector.forceDismissCaptivePortalNotification()
            LogRepository.success("Hands-Free Success", "Instant login verified on ${successfulResult.portalTargetUrl}!")
            return@withContext successfulResult
        }

        val explicitFailure = results.filterNotNull().firstOrNull { !it.isSuccess && it.message.isNotBlank() }
        if (explicitFailure != null) {
            return@withContext explicitFailure
        }

        // Fast probe check (300ms)
        delay(300)
        val probe = detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
        if (probe.state == WifiState.CONNECTED_ONLINE && probe.httpCode == 204) {
            LogRepository.success("Hands-Free Success", "Internet unblocked successfully!")
            return@withContext AuthResult(isSuccess = true, httpCode = 204, message = "Internet verified online.")
        }

        // 4. SECONDARY PATH: Headless WebKit Browser Engine targeting dynamically discovered URL
        val targetBrowserUrl = if (discoveredPortal.isNotBlank() && discoveredPortal.startsWith("http")) {
            discoveredPortal
        } else if (discoveredHost.isNotBlank()) {
            "http://$discoveredHost:8090/httpclient.html"
        } else {
            "http://$gateway:8090/httpclient.html"
        }

        LogRepository.info("Auto-Engine", "Cascading to Headless WebKit Engine on '$targetBrowserUrl' for ${cleanProfile.username}...")
        val webResult = WebViewLoginEngine.executeHeadlessLogin(context, cleanProfile, detector, targetBrowserUrl)
        if (webResult.isSuccess) {
            return@withContext webResult
        }

        AuthResult(
            isSuccess = false,
            message = if (webResult.message.isNotBlank() && !webResult.message.contains("timed out", ignoreCase = true)) webResult.message else "Login Failed: Username / Password is wrong or server unreachable. Check profile (${cleanProfile.username}).",
            portalTargetUrl = targetBrowserUrl
        )
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
