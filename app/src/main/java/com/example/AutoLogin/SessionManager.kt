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
            .putInt("REMAINING_LOG_QUOTA", 5)
            .apply()
    }

    fun getUsername(): String = prefs.getString("USER", "") ?: ""
    fun getPassword(): String = prefs.getString("PASS", "") ?: ""

    fun isUpdateRequired(): Boolean = prefs.getBoolean("UPDATE_REQUIRED", false)
    fun setUpdateRequired(required: Boolean) {
        prefs.edit().putBoolean("UPDATE_REQUIRED", required).apply()
    }

    fun isOnboarded(): Boolean = prefs.getBoolean("HAS_COMPLETED_ONBOARDING", false)
    fun setOnboarded(completed: Boolean) {
        prefs.edit().putBoolean("HAS_COMPLETED_ONBOARDING", completed).apply()
    }

    fun getRemainingLogQuota(): Int = prefs.getInt("REMAINING_LOG_QUOTA", 0)
    fun decrementLogQuota() {
        val current = getRemainingLogQuota()
        if (current > 0) {
            prefs.edit().putInt("REMAINING_LOG_QUOTA", current - 1).apply()
        }
    }
}
