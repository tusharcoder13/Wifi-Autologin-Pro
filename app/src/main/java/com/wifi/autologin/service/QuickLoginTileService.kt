package com.wifi.autologin.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.wifi.autologin.data.repository.LogRepository

@RequiresApi(Build.VERSION_CODES.N)
class QuickLoginTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Login to Wi-Fi"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        LogRepository.info("Quick Tile Clicked", "User triggered manual login via Quick Settings Tile.")
        WifiMonitorService.triggerLogin(applicationContext)
    }
}
