package com.wifi.autologin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wifi.autologin.data.repository.LogRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.net.wifi.STATE_CHANGE" ||
            action == "android.net.wifi.WIFI_STATE_CHANGED" ||
            action == "android.net.conn.CONNECTIVITY_CHANGE") {
            LogRepository.info("Boot/State Trigger", "Network state change received ($action). Ensuring monitor service is active...")
            WifiMonitorService.start(context)
        }
    }
}
