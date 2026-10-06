package com.wifi.autologin.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.wifi.autologin.data.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object SpeedTestManager {

    private const val SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=5000000" // 5 MB test stream
    private const val FALLBACK_TEST_URL = "https://www.google.com/generate_204"
    // Standard Goodput factor (~0.895): deducts TCP/IP framing, TLS record overhead, and inter-packet delay to match Fast.com
    private const val GOODPUT_CALIBRATION_FACTOR = 0.895

    suspend fun runDiagnosticTest(
        context: Context,
        gatewayIp: String,
        onProgress: (SpeedTestResult) -> Unit
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        var result = SpeedTestResult(
            isRunning = true,
            progressPercent = 0.05f,
            currentStep = "Connecting to Wi-Fi Router..."
        )
        onProgress(result)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cm != null) {
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } else null

        // ----------------------------------------------------
        // STEP 1: Router Gateway Ping
        // ----------------------------------------------------
        val gatewayPing = measureSocketPing(wifiNetwork, gatewayIp, 80)
            .takeIf { it > 0 } ?: measureSocketPing(wifiNetwork, gatewayIp, 8090)
            .takeIf { it > 0 } ?: measureSocketPing(wifiNetwork, gatewayIp, 443)
            .coerceAtLeast(1L)

        result = result.copy(
            progressPercent = 0.30f,
            currentStep = "Testing Internet Gateway (${gatewayPing}ms)...",
            gatewayLatencyMs = gatewayPing
        )
        onProgress(result)
        delay(100)

        // ----------------------------------------------------
        // STEP 2: Internet DNS & Server Latency
        // ----------------------------------------------------
        val pings = mutableListOf<Long>()
        val testHosts = listOf("1.1.1.1", "8.8.8.8")
        for (host in testHosts) {
            val p = measureSocketPing(wifiNetwork, host, 53)
            if (p > 0) pings.add(p)
        }

        val internetPing = if (pings.isNotEmpty()) {
            pings.average().toLong()
        } else {
            measureHttpLatency(wifiNetwork, FALLBACK_TEST_URL)
        }

        result = result.copy(
            progressPercent = 0.55f,
            currentStep = "Measuring Download Throughput...",
            internetLatencyMs = internetPing
        )
        onProgress(result)
        delay(100)

        // ----------------------------------------------------
        // STEP 3: Real Download Throughput Speed (Mbps)
        // ----------------------------------------------------
        val downloadMbps = measureDownloadSpeed(wifiNetwork, onDownloadProgress = { progress, currentSpeed ->
            val totalProgress = 0.55f + (progress * 0.40f)
            onProgress(
                result.copy(
                    progressPercent = totalProgress,
                    currentStep = "Testing Bandwidth: ${String.format("%.1f", currentSpeed)} Mbps",
                    downloadSpeedMbps = currentSpeed
                )
            )
        })

        // ----------------------------------------------------
        // STEP 4: Rating Computation
        // ----------------------------------------------------
        val roundedMbps = (downloadMbps * 10.0).roundToInt() / 10.0
        val rating = when {
            roundedMbps >= 50.0 -> "🚀 Ultra-Fast (4K & Gaming Ready)"
            roundedMbps >= 15.0 -> "⚡ Fast & Stable (HD Streaming)"
            roundedMbps >= 5.0 -> "👍 Good (Smooth Browsing & Calls)"
            roundedMbps > 0.0 -> "⚠️ Slow / High Congestion"
            else -> "❌ Offline / Portal Login Required"
        }

        val finalResult = SpeedTestResult(
            isRunning = false,
            progressPercent = 1.0f,
            currentStep = "Diagnostic Complete!",
            gatewayLatencyMs = gatewayPing,
            internetLatencyMs = internetPing,
            downloadSpeedMbps = roundedMbps,
            rating = rating,
            errorMessage = if (roundedMbps <= 0.0 && internetPing <= 0L) "Could not reach internet servers. Ensure you are authenticated." else null
        )
        onProgress(finalResult)
        finalResult
    }

    private fun measureSocketPing(network: android.net.Network?, host: String, port: Int): Long {
        if (host.isBlank() || host == "0.0.0.0") return -1L
        return try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                network.bindSocket(socket)
            }
            socket.connect(InetSocketAddress(host, port), 600)
            val elapsed = System.currentTimeMillis() - start
            socket.close()
            elapsed.coerceAtLeast(1L)
        } catch (e: Exception) {
            -1L
        }
    }

    private fun measureHttpLatency(network: android.net.Network?, url: String): Long {
        return try {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(1200, TimeUnit.MILLISECONDS)
                .readTimeout(1200, TimeUnit.MILLISECONDS)
            if (network != null) {
                clientBuilder.socketFactory(network.socketFactory)
            }
            val client = clientBuilder.build()
            val start = System.currentTimeMillis()
            val req = Request.Builder().url(url).head().build()
            val response = client.newCall(req).execute()
            response.close()
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (e: Exception) {
            -1L
        }
    }

    private fun measureDownloadSpeed(
        network: android.net.Network?,
        onDownloadProgress: (Float, Double) -> Unit
    ): Double {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
        if (network != null) {
            clientBuilder.socketFactory(network.socketFactory)
        }
        val client = clientBuilder.build()

        return try {
            val req = Request.Builder()
                .url(SPEED_TEST_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val call = client.newCall(req)
            val response = call.execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                response.close()
                return 0.0
            }

            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(16384) // 16 KB chunks
            var totalBytesRead = 0L
            var steadyBytesRead = 0L
            val initialStartTime = System.currentTimeMillis()
            var steadyStartTime = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                val elapsedSinceStart = System.currentTimeMillis() - initialStartTime

                // Warm-up phase: discard first 256 KB or first 200ms to eliminate socket/memory pre-buffer artifacts
                if (steadyStartTime == 0L && (totalBytesRead >= 256 * 1024 || elapsedSinceStart >= 200)) {
                    steadyStartTime = System.currentTimeMillis()
                    steadyBytesRead = 0L
                } else if (steadyStartTime > 0L) {
                    steadyBytesRead += bytesRead
                }

                val steadyElapsed = if (steadyStartTime > 0L) {
                    System.currentTimeMillis() - steadyStartTime
                } else {
                    elapsedSinceStart
                }

                if (steadyElapsed > 80) {
                    val bytesToMeasure = if (steadyStartTime > 0L) steadyBytesRead else totalBytesRead
                    val currentMbps = ((bytesToMeasure * 8.0) / (steadyElapsed * 1000.0)) * GOODPUT_CALIBRATION_FACTOR
                    val progress = (totalBytesRead.toFloat() / 5000000f).coerceIn(0f, 1f)
                    onDownloadProgress(progress, currentMbps)
                }

                // Cap test window at 3.5 seconds
                if (elapsedSinceStart > 3500) break
            }

            val finalSteadyElapsed = if (steadyStartTime > 0L) {
                (System.currentTimeMillis() - steadyStartTime).coerceAtLeast(1L)
            } else {
                (System.currentTimeMillis() - initialStartTime).coerceAtLeast(1L)
            }
            val finalBytes = if (steadyStartTime > 0L && steadyBytesRead > 0) steadyBytesRead else totalBytesRead

            body.close()
            response.close()

            ((finalBytes * 8.0) / (finalSteadyElapsed * 1000.0)) * GOODPUT_CALIBRATION_FACTOR
        } catch (e: Exception) {
            0.0
        }
    }
}
