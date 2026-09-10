package com.wifi.autologin.service

import android.content.Context
import androidx.work.*
import com.wifi.autologin.data.repository.AppSettingsRepository
import com.wifi.autologin.data.repository.LogRepository
import com.wifi.autologin.data.repository.ProfileRepository
import com.wifi.autologin.network.CaptivePortalDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appSettingsRepository = AppSettingsRepository(applicationContext)
        val profileRepository = ProfileRepository(applicationContext)
        val detector = CaptivePortalDetector(applicationContext)
        val settings = appSettingsRepository.settings.value

        if (!settings.isKeepAliveEnabled) {
            return@withContext Result.success()
        }

        val currentSsid = detector.getCurrentSsid()
        val gateway = detector.getGatewayIpAddress()
        val profile = profileRepository.findProfileForNetwork(currentSsid, gateway)

        if (profile != null && profile.isKeepAliveEnabled) {
            LogRepository.info("Worker Ping", "KeepAliveWorker executing scheduled heartbeat for ${profile.name}...")
            detector.probeConnectivity(settings.customProbeUrl, settings.bypassSslErrors)
        }

        Result.success()
    }

    companion object {
        private const val WORK_NAME = "wifi_autologin_keepalive_work"

        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
