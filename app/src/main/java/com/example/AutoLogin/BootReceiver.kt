package com.example.autologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val session = SessionManager(context)
            if (session.isAutoLoginEnabled() && session.getUsername().isNotEmpty()) {
                AutoLoginWorker.enqueue(context)
            }
        }
    }
}
