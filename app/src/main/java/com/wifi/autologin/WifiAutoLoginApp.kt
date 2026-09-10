package com.wifi.autologin

import android.app.Application
import com.wifi.autologin.service.KeepAliveWorker
import com.wifi.autologin.service.WifiMonitorService

class WifiAutoLoginApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            // Automatically start the background monitor service
            WifiMonitorService.start(this)
            KeepAliveWorker.schedule(this)
        } catch (e: Exception) {
            // Guard against background service startup restrictions
        }
    }
}
