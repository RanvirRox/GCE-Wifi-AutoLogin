package com.example.autologin

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlin.concurrent.thread

class MyTileServices : TileService() {

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()

        Toast.makeText(this, "Wi-Fi Login triggered...", Toast.LENGTH_SHORT).show()

        thread {
            val result = AuthClient.sendLoginRequest(applicationContext)

            Handler(Looper.getMainLooper()).post {
                qsTile?.let {
                    it.state = if (result.success) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    it.updateTile()
                }
                Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
