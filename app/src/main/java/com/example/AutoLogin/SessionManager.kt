package com.example.autologin

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("DEV_AUTH_PREFS", Context.MODE_PRIVATE)

    fun saveCredentials(user: String, pass: String) {
        prefs.edit()
            .putString("USER", user.trim())
            .putString("PASS", pass.trim())
            .putBoolean("LOGS_SENT_FOR_CURRENT_CREDS", false)
            .apply()
    }

    fun getUsername(): String = prefs.getString("USER", "") ?: ""
    fun getPassword(): String = prefs.getString("PASS", "") ?: ""

    fun isUpdateRequired(): Boolean = prefs.getBoolean("UPDATE_REQUIRED", false)
    fun setUpdateRequired(required: Boolean) {
        prefs.edit().putBoolean("UPDATE_REQUIRED", required).apply()
    }

    fun hasSentLogsForCurrentCreds(): Boolean = prefs.getBoolean("LOGS_SENT_FOR_CURRENT_CREDS", false)
    fun setLogsSentForCurrentCreds(sent: Boolean) {
        prefs.edit().putBoolean("LOGS_SENT_FOR_CURRENT_CREDS", sent).apply()
    }
}
