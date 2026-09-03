package com.example.autologin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var session: SessionManager
    private lateinit var cm: ConnectivityManager

    private lateinit var tvWifiStatus: TextView
    private lateinit var tvLogConsole: TextView
    private lateinit var swAutoLogin: Switch
    private lateinit var credsContainer: LinearLayout
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun log(msg: String) {
        val stamp = timeFormat.format(Date())
        runOnUiThread {
            tvLogConsole.append("[$stamp] $msg\n")
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                tvWifiStatus.text = "Wi-Fi: Connected 🟢"
                tvWifiStatus.setTextColor(Color.parseColor("#2E7D32"))
            }
            log("📡 Wi-Fi Connected")

            if (session.isAutoLoginEnabled()) {
                log("⚡ AutoLogin is ON -> Starting 3-try POST...")
                triggerLoginWithRetry()
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                tvWifiStatus.text = "Wi-Fi: Disconnected 🔴"
                tvWifiStatus.setTextColor(Color.RED)
            }
            log("🔌 Wi-Fi Disconnected")
            if (session.isAutoLoginEnabled()) {
                try {
                    AutoLoginWorker.enqueue(applicationContext)
                } catch (e: Exception) {
                    log("⚠️ Worker notice: ${e.localizedMessage}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        tvWifiStatus = TextView(this).apply {
            text = "Wi-Fi: Checking..."
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        root.addView(tvWifiStatus)

        // Master AutoLogin Switch
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        val tvToggleLabel = TextView(this).apply {
            text = "Auto-Login on Wi-Fi Connect"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        swAutoLogin = Switch(this).apply {
            isChecked = session.isAutoLoginEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                session.setAutoLoginEnabled(isChecked)
                log("AutoLogin toggle set to: $isChecked")
                if (isChecked) {
                    try {
                        AutoLoginWorker.enqueue(applicationContext)
                    } catch (e: Exception) {
                        log("⚠️ Worker notice: ${e.localizedMessage}")
                    }
                }
            }
        }
        toggleRow.addView(tvToggleLabel)
        toggleRow.addView(swAutoLogin)
        root.addView(toggleRow)

        // Action Buttons Row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val btnLogin = Button(this).apply {
            text = "POST Login"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { triggerLoginWithRetry() }
        }

        val btnPing = Button(this).apply {
            text = "Ping Google"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                thread {
                    log("Testing ping to Google...")
                    val res = AuthClient.pingGoogle()
                    log(res.second)
                }
            }
        }

        val btnToggleCreds = Button(this).apply {
            text = "Credentials"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                credsContainer.visibility = if (credsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        btnRow.addView(btnLogin)
        btnRow.addView(btnPing)
        btnRow.addView(btnToggleCreds)
        root.addView(btnRow)

        // Embedded Credentials Panel (No Dialog = Zero Window Leaks/Crashes)
        credsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            visibility = if (session.getUsername().isEmpty()) View.VISIBLE else View.GONE
        }

        etUsername = EditText(this).apply {
            hint = "Roll / Username"
            setText(session.getUsername())
        }
        etPassword = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(session.getPassword())
        }

        val btnSaveCreds = Button(this).apply {
            text = "Save Credentials"
            setOnClickListener {
                val u = etUsername.text.toString().trim()
                val p = etPassword.text.toString().trim()
                session.saveCredentials(u, p)
                log("💾 Credentials saved for: $u")
                credsContainer.visibility = View.GONE
                try {
                    AutoLoginWorker.enqueue(applicationContext)
                } catch (e: Exception) {
                    log("⚠️ Worker notice: ${e.localizedMessage}")
                }
            }
        }

        credsContainer.addView(etUsername)
        credsContainer.addView(etPassword)
        credsContainer.addView(btnSaveCreds)
        root.addView(credsContainer)

        // Console Log
        val consoleHeader = TextView(this).apply {
            text = "--- Live Dev Log Console ---"
            setPadding(0, 16, 0, 8)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(consoleHeader)

        tvLogConsole = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#00FF66"))
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(16, 16, 16, 16)
        }

        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(tvLogConsole)
        }
        root.addView(logScroll)

        setContentView(root)

        log("Dev Console Initialized.")

        if (session.isAutoLoginEnabled() && session.getUsername().isNotEmpty()) {
            try {
                AutoLoginWorker.enqueue(applicationContext)
            } catch (e: Exception) {
                log("⚠️ Worker notice: ${e.localizedMessage}")
            }
        }

        registerNetworkListener()
    }

    private fun triggerLoginWithRetry() {
        thread {
            val result = AuthClient.sendLoginRequestWithRetry(this@MainActivity, maxRetries = 3) { step ->
                log(step)
            }
            log(result.message)
        }
    }

    private fun registerNetworkListener() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }
}