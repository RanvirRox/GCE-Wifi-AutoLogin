package com.example.autologin

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MyTileServices : TileService() {

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()

        Toast.makeText(this, "Attempting login...", Toast.LENGTH_SHORT).show()

        // Execute network request in background thread
        thread {
            val success = performHttpPost()

            // Update UI back on main state
            qsTile?.let {
                it.state = Tile.STATE_ACTIVE
                it.updateTile()
            }

            // Note: Toast from background thread should use mainLooper if needed, or simple logging
            Log.d("GCE_LOGIN", "Login result success: $success")
        }
    }

    private fun performHttpPost(): Boolean {
        return try {
            // Replace with your portal endpoint
            val targetUrl = URL("http://192.168.1.1/login") 
            val conn = targetUrl.openConnection() as HttpURLConnection
            
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val payload = "username=YOUR_USER&password=YOUR_PASSWORD"
            
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("GCE_LOGIN", "Request failed", e)
            false
        }
    }
}