package com.example.autologin

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("DEV_AUTH_PREFS", Context.MODE_PRIVATE)

    fun saveCredentials(user: String, pass: String) {
        prefs.edit().putString("USER", user.trim()).putString("PASS", pass.trim()).apply()
    }

    fun getUsername(): String = prefs.getString("USER", "") ?: ""
    fun getPassword(): String = prefs.getString("PASS", "") ?: ""
}
