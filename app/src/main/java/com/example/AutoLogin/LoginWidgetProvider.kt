package com.example.autologin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import kotlin.concurrent.thread

class LoginWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CLICK = "com.example.autologin.ACTION_WIDGET_CLICK"
        private val mainHandler = Handler(Looper.getMainLooper())
        private var resetRunnable: Runnable? = null

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            iconResId: Int = R.drawable.ic_wire_disconnected,
            isActive: Boolean = false
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_login_tile)

            views.setImageViewResource(R.id.widget_icon, iconResId)

            val bgResId = if (isActive) R.drawable.widget_bg_active else R.drawable.widget_bg_inactive
            views.setInt(R.id.widget_container, "setBackgroundResource", bgResId)

            val intent = Intent(context, LoginWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(
            context: Context,
            iconResId: Int = R.drawable.ic_wire_disconnected,
            isActive: Boolean = false
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, LoginWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, iconResId, isActive)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_CLICK) {
            val session = SessionManager(context)
            if (session.isUpdateRequired()) {
                Toast.makeText(context, "Update Required! Opening app...", Toast.LENGTH_LONG).show()
                try {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(appIntent)
                } catch (_: Exception) {}
                return
            }

            // Cancel any pending reset
            resetRunnable?.let { mainHandler.removeCallbacks(it) }

            // Immediately switch to Active / Loading state
            updateAllWidgets(
                context = context,
                iconResId = R.drawable.ic_loading,
                isActive = true
            )

            Toast.makeText(context, "Wi-Fi Login triggered...", Toast.LENGTH_SHORT).show()

            thread {
                val result = AuthClient.sendLoginRequestWithRetry(context)

                mainHandler.post {
                    val iconRes = if (result.success) {
                        R.drawable.ic_wire_connected
                    } else {
                        R.drawable.ic_wire_disconnected
                    }

                    updateAllWidgets(
                        context = context,
                        iconResId = iconRes,
                        isActive = result.success
                    )

                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()

                    // Reset back to inactive after 5 seconds
                    val runnable = Runnable {
                        updateAllWidgets(
                            context = context,
                            iconResId = R.drawable.ic_wire_disconnected,
                            isActive = false
                        )
                    }
                    resetRunnable = runnable
                    mainHandler.postDelayed(runnable, 5000)
                }
            }
        }
    }
}
