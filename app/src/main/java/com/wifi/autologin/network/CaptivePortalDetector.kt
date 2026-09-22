package com.wifi.autologin.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.wifi.autologin.data.model.WifiState
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ProbeResult(
    val state: WifiState,
    val portalUrl: String = "",
    val redirectHtml: String = "",
    val latencyMs: Long = -1,
    val httpCode: Int = 0
)

class CaptivePortalDetector(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Gets all active Wi-Fi Network interfaces.
     */
    fun getWifiNetworks(): List<android.net.Network> {
        return try {
            connectivityManager.allNetworks.filter { net ->
                val caps = connectivityManager.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets the primary active Wi-Fi Network interface.
     */
    fun getPrimaryWifiNetwork(): android.net.Network? {
        return getWifiNetworks().firstOrNull()
    }

    /**
     * Creates an OkHttpClient specifically configured for live captive portal probing.
     */
    private fun createProbeClient(followRedirects: Boolean = false, bypassSsl: Boolean = true): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (bypassSsl) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                // Fallback
            }
        }

        // Bind probe socket factory strictly to the active Wi-Fi interface
        try {
            val wifiNetwork = getPrimaryWifiNetwork()
            if (wifiNetwork != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectivityManager.bindProcessToNetwork(wifiNetwork)
                }
                builder.socketFactory(wifiNetwork.socketFactory)
            }
        } catch (e: Exception) {
            // Fallback
        }

        return builder.build()
    }

    /**
     * Runs an active connectivity probe to dynamically detect the exact intercepted captive portal URL.
     */
    fun probeConnectivity(probeUrl: String = "http://connectivitycheck.gstatic.com/generate_204", bypassSsl: Boolean = true): ProbeResult {
        if (!isWifiConnected()) {
            return ProbeResult(
                state = WifiState.DISCONNECTED,
                latencyMs = -1,
                httpCode = 0
            )
        }
        val startTime = System.currentTimeMillis()
        val client = createProbeClient(followRedirects = false, bypassSsl = bypassSsl)

        val request = Request.Builder()
            .url(probeUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val code = response.code
            val locationHeader = response.header("Location") ?: ""
            val bodyString = response.body?.string() ?: ""
            val gateway = getGatewayIpAddress()

            // 1. HTTP 204 No Content -> Full internet access (unblocked)
            if (code == 204) {
                return ProbeResult(
                    state = WifiState.CONNECTED_ONLINE,
                    latencyMs = latency,
                    httpCode = 204
                )
            }

            // 2. HTTP 302/301/307 Redirect -> Captive Portal Redirection URL directly in Location header
            if (code in listOf(301, 302, 303, 307, 308) && locationHeader.isNotBlank()) {
                val resolvedUrl = if (locationHeader.startsWith("http")) {
                    locationHeader
                } else if (locationHeader.startsWith("/")) {
                    "http://$gateway:8090$locationHeader"
                } else {
                    "http://$gateway:8090/$locationHeader"
                }
                return ProbeResult(
                    state = WifiState.CAPTIVE_PORTAL_DETECTED,
                    portalUrl = resolvedUrl,
                    redirectHtml = bodyString,
                    latencyMs = latency,
                    httpCode = code
                )
            }

            // 3. HTTP 200 with HTML Form / Login text -> Router transparently serving login HTML
            if (code == 200) {
                val hasForm = bodyString.contains("<form", ignoreCase = true) ||
                        bodyString.contains("password", ignoreCase = true) ||
                        bodyString.contains("captive", ignoreCase = true) ||
                        bodyString.contains("login", ignoreCase = true) ||
                        bodyString.contains("cyberoam", ignoreCase = true) ||
                        bodyString.contains("sophos", ignoreCase = true) ||
                        bodyString.contains("kalinga", ignoreCase = true) ||
                        bodyString.contains("httpclient", ignoreCase = true)

                if (hasForm) {
                    val extractedUrl = extractPortalUrlFromHtml(bodyString, gateway)
                    return ProbeResult(
                        state = WifiState.CAPTIVE_PORTAL_DETECTED,
                        portalUrl = extractedUrl,
                        redirectHtml = bodyString,
                        latencyMs = latency,
                        httpCode = 200
                    )
                } else if (bodyString.isBlank()) {
                    return ProbeResult(
                        state = WifiState.CONNECTED_ONLINE,
                        latencyMs = latency,
                        httpCode = 200
                    )
                }
            }

            // 4. Any other non-204 code is intercepted
            val extractedUrl = if (locationHeader.isNotBlank()) {
                if (locationHeader.startsWith("http")) locationHeader else "http://$gateway:8090$locationHeader"
            } else {
                extractPortalUrlFromHtml(bodyString, gateway)
            }

            return ProbeResult(
                state = WifiState.CAPTIVE_PORTAL_DETECTED,
                portalUrl = extractedUrl,
                redirectHtml = bodyString,
                latencyMs = latency,
                httpCode = code
            )

        } catch (e: IOException) {
            return if (isWifiConnected()) {
                ProbeResult(state = WifiState.CONNECTED_NO_INTERNET)
            } else {
                ProbeResult(state = WifiState.DISCONNECTED)
            }
        } catch (e: Exception) {
            return ProbeResult(state = WifiState.CONNECTED_NO_INTERNET)
        }
    }

    /**
     * Dynamically extracts the exact portal URL from HTML forms, JS redirects, meta-refresh tags, or embedded IPs.
     */
    private fun extractPortalUrlFromHtml(html: String, defaultGateway: String): String {
        // 1. Meta refresh: <meta http-equiv="refresh" content="...url=...">
        val metaRegex = Regex("""content=["'][^"']*url=([^"']+)["']""", RegexOption.IGNORE_CASE)
        val metaMatch = metaRegex.find(html)?.groupValues?.get(1)?.trim()
        if (!metaMatch.isNullOrBlank() && metaMatch.startsWith("http")) return metaMatch

        // 2. JavaScript redirection: window.location / location.href
        val jsRegex = Regex("""(?:window\.location(?:\.href)?|location\.replace)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val jsMatch = jsRegex.find(html)?.groupValues?.get(1)?.trim()
        if (!jsMatch.isNullOrBlank() && jsMatch.startsWith("http")) return jsMatch

        // 3. Form Action: <form action="...">
        val formRegex = Regex("""<form[^>]+action=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val formMatch = formRegex.find(html)?.groupValues?.get(1)?.trim()
        if (!formMatch.isNullOrBlank()) {
            return when {
                formMatch.startsWith("http://") || formMatch.startsWith("https://") -> formMatch
                formMatch.startsWith("/") -> "http://$defaultGateway:8090$formMatch"
                else -> "http://$defaultGateway:8090/$formMatch"
            }
        }

        // 4. Any embedded IP:8090 or IP/httpclient in the page scripts
        val ipRegex = Regex("""https?://(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?::\d+)?(?:/[^\s"'>]*)?)""")
        val ipMatch = ipRegex.find(html)?.value
        if (!ipMatch.isNullOrBlank()) return ipMatch

        return "http://$defaultGateway:8090/httpclient.html"
    }

    /**
     * Measures low-level TCP socket latency to Google DNS (8.8.8.8)
     */
    fun measureSocketPing(host: String = "8.8.8.8", port: Int = 53, timeoutMs: Int = 2000): Long {
        val startTime = System.currentTimeMillis()
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latency = System.currentTimeMillis() - startTime
            socket.close()
            latency
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Gets current connected Wi-Fi exact SSID directly from Android system APIs
     */
    fun getCurrentSsid(): String {
        if (!isWifiConnected()) {
            latestSsid = ""
            return ""
        }
        try {
            // 1. Cached live SSID from system callback
            if (latestSsid.isNotBlank() && latestSsid != "<unknown ssid>" && !latestSsid.contains(".com", ignoreCase = true) && !latestSsid.contains("gprs", ignoreCase = true)) {
                return latestSsid
            }

            // 2. ConnectivityManager TransportInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val transportInfo = caps.transportInfo
                        if (transportInfo is WifiInfo) {
                            val ssid = transportInfo.ssid
                            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                                val clean = ssid.removeSurrounding("\"").trim()
                                if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                                    latestSsid = clean
                                    return clean
                                }
                            }
                        }
                    }
                }
            }

            // 3. WifiManager connectionInfo
            val wifiInfo: WifiInfo? = wifiManager.connectionInfo
            val rawSsid = wifiInfo?.ssid ?: ""
            if (rawSsid.isNotBlank() && rawSsid != "<unknown ssid>") {
                val clean = rawSsid.removeSurrounding("\"").trim()
                if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                    latestSsid = clean
                    return clean
                }
            }

            // 4. ScanResults BSSID matching fallback
            val bssid = wifiInfo?.bssid ?: ""
            if (bssid.isNotBlank() && bssid != "02:00:00:00:00:00") {
                try {
                    val scanMatch = wifiManager.scanResults.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
                    val scanSsid = scanMatch?.SSID ?: ""
                    if (scanSsid.isNotBlank() && scanSsid != "<unknown ssid>") {
                        val clean = scanSsid.removeSurrounding("\"").trim()
                        if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                            latestSsid = clean
                            return clean
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // 5. NetworkInfo ExtraInfo fallback
            @Suppress("DEPRECATION")
            val netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            val extraInfo = netInfo?.extraInfo
            if (!extraInfo.isNullOrBlank() && extraInfo != "<unknown ssid>") {
                val clean = extraInfo.removeSurrounding("\"").trim()
                if (clean.isNotBlank() && !clean.contains(".com", ignoreCase = true) && !clean.contains("gprs", ignoreCase = true)) {
                    latestSsid = clean
                    return clean
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        val finalSsid = if (latestSsid.contains(".com") || latestSsid.contains("gprs") || latestSsid == "Connected Wi-Fi") "" else latestSsid
        return finalSsid
    }

    /**
     * Gets current local IP address
     */
    fun getCurrentIpAddress(): String {
        return try {
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return ""
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Gets gateway IP address with DHCP and LinkProperties fallback
     */
    fun getGatewayIpAddress(): String {
        return try {
            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = dhcpInfo?.gateway ?: 0
            if (gateway != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    gateway and 0xff,
                    gateway shr 8 and 0xff,
                    gateway shr 16 and 0xff,
                    gateway shr 24 and 0xff
                )
            }

            // Fallback via LinkProperties routes
            val wifiNet = connectivityManager.allNetworks.firstOrNull { net ->
                connectivityManager.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifiNet != null) {
                val linkProps = connectivityManager.getLinkProperties(wifiNet)
                val defaultRoute = linkProps?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }
                val gatewayHost = defaultRoute?.gateway?.hostAddress
                if (!gatewayHost.isNullOrBlank() && gatewayHost != "0.0.0.0") {
                    return gatewayHost
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Checks if device is connected to a Wi-Fi network (handles co-existence with Mobile Data)
     */
    fun isWifiConnected(): Boolean {
        return try {
            val active = connectivityManager.activeNetwork
            if (active != null) {
                val caps = connectivityManager.getNetworkCapabilities(active)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    return true
                }
            }

            // Check all networks for Wi-Fi interface (critical when captive Wi-Fi is active alongside mobile data)
            connectivityManager.allNetworks.any { net ->
                val caps = connectivityManager.getNetworkCapabilities(net)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if device Location (GPS) is toggled on (mandatory on Android to reveal Wi-Fi SSIDs)
     */
    fun isLocationEnabled(): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager?.isLocationEnabled == true
            } else {
                val mode = android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.LOCATION_MODE,
                    android.provider.Settings.Secure.LOCATION_MODE_OFF
                )
                mode != android.provider.Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Immediately instructs Android OS to re-validate connectivity and dismiss the "Sign in to network" system popup.
     * Iterates over all active Wi-Fi network interfaces (independent of Mobile Data state).
     */
    fun forceDismissCaptivePortalNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val wifiNets = getWifiNetworks()
                for (wifiNet in wifiNets) {
                    try {
                        connectivityManager.bindProcessToNetwork(wifiNet)
                        // Trigger immediate re-evaluation in Android OS NetworkMonitor
                        connectivityManager.reportNetworkConnectivity(wifiNet, false)
                        connectivityManager.reportNetworkConnectivity(wifiNet, true)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Blasts direct non-blocking HTTP 204 requests over the Wi-Fi physical socket
     * to immediately satisfy Android's kernel and socket-level verification.
     */
    fun blastSocket204Probes() {
        try {
            val wifiNet = getPrimaryWifiNetwork() ?: return
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .socketFactory(wifiNet.socketFactory)
                .followRedirects(false)
                .build()

            val urls = listOf(
                "http://connectivitycheck.gstatic.com/generate_204",
                "http://www.google.com/generate_204",
                "http://play.googleapis.com/generate_204",
                "http://gstatic.com/generate_204"
            )

            for (url in urls) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("Cache-Control", "no-cache")
                        .build()
                    client.newCall(req).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: IOException) {}
                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            response.close()
                        }
                    })
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        @Volatile
        var latestSsid: String = ""
    }
}
