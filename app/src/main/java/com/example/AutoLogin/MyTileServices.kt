package com.example.autologin

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlin.concurrent.thread

class MyTileServices : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isTaskRunning = false

    override fun onStartListening() {
        super.onStartListening()
        if (!isTaskRunning) {
            setTileState(Tile.STATE_INACTIVE, R.drawable.ic_wire_disconnected, "Wi-Fi Login")
        }
    }

    override fun onClick() {
        super.onClick()

        val session = SessionManager(applicationContext)
        if (session.isUpdateRequired()) {
            Toast.makeText(this, "Update Required! Please open the app to update.", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivityAndCollapse(intent)
            } catch (_: Exception) {}
            return
        }

        isTaskRunning = true
        setTileState(Tile.STATE_ACTIVE, R.drawable.ic_loading, "Logging in...")

        Toast.makeText(this, "Wi-Fi Login triggered...", Toast.LENGTH_SHORT).show()

        thread {
            val result = AuthClient.sendLoginRequestWithRetry(
                context = applicationContext,
                onNetworkPromoted = {
                    mainHandler.post {
                        setTileState(Tile.STATE_ACTIVE, R.drawable.ic_wire_connected, "Connected")
                    }
                }
            )

            mainHandler.post {
                val iconRes = if (result.success) {
                    R.drawable.ic_wire_connected
                } else {
                    R.drawable.ic_wire_disconnected
                }

                setTileState(Tile.STATE_ACTIVE, iconRes, if (result.success) "Connected" else "Failed")
                Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()

                mainHandler.postDelayed({
                    isTaskRunning = false
                    setTileState(Tile.STATE_INACTIVE, R.drawable.ic_wire_disconnected, "Wi-Fi Login")
                }, 5000)
            }
        }
    }

    private fun setTileState(state: Int, iconResId: Int, label: String) {
        qsTile?.let { tile ->
            tile.state = state
            tile.icon = Icon.createWithResource(this, iconResId)
            tile.label = label
            tile.updateTile()
        }
    }
}
