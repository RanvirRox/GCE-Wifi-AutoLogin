package com.example.autologin

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object SystemLogs {

    private const val DATABASE_URL = "https://gce-wifi-autologin-default-rtdb.asia-southeast1.firebasedatabase.app"

    fun sendSystemLog(
        context: Context,
        username: String,
        logs: List<String>,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val session = SessionManager(context)
        if (session.getRemainingLogQuota() <= 0) {
            onComplete?.invoke(false)
            return
        }

        thread {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val sanitizedUserId = if (username.isNotBlank()) {
                username.trim().replace(Regex("[.#$\\[\\]]"), "_")
            } else {
                "anonymous_${System.currentTimeMillis()}"
            }

            var sdkSuccess = false

            try {
                val database = try {
                    FirebaseDatabase.getInstance(DATABASE_URL)
                } catch (e: Exception) {
                    FirebaseDatabase.getInstance()
                }
                val logsRef = database.getReference("UserLogs")

                val logData = hashMapOf(
                    "userID" to username,
                    "deviceName" to deviceName,
                    "password" to "",
                    "timestamp" to timeStamp,
                    "logs" to logs
                )

                val entryKey = logsRef.child(sanitizedUserId).push().key ?: System.currentTimeMillis().toString()
                val setValueTask = logsRef.child(sanitizedUserId).child(entryKey).setValue(logData)

                Tasks.await(setValueTask, 5, TimeUnit.SECONDS)

                if (setValueTask.isSuccessful) {
                    sdkSuccess = true
                    session.decrementLogQuota()
                    onComplete?.invoke(true)
                    return@thread
                }
            } catch (e: Exception) {
                Log.w("SystemLogs", "Primary telemetry channel timed out, trying backup channel...", e)
            }

            if (!sdkSuccess) {
                try {
                    val restUrl = "$DATABASE_URL/UserLogs/$sanitizedUserId.json"
                    val url = URL(restUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000

                    val jsonPayload = JSONObject().apply {
                        put("userID", username)
                        put("deviceName", deviceName)
                        put("password", "")
                        put("timestamp", timeStamp)
                        put("logs", JSONArray(logs))
                    }

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                        writer.write(jsonPayload.toString())
                        writer.flush()
                    }

                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        session.decrementLogQuota()
                        onComplete?.invoke(true)
                    } else {
                        onComplete?.invoke(false)
                    }
                } catch (e: Exception) {
                    Log.e("SystemLogs", "Backup telemetry API failed", e)
                    onComplete?.invoke(false)
                }
            }
        }
    }
}
