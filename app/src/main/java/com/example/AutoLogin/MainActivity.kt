package com.example.autologin

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var session: SessionManager
    private lateinit var cm: ConnectivityManager

    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSubtitle: TextView
    
    private lateinit var btnLogin: Button
    private lateinit var btnPing: Button
    private lateinit var btnToggleCreds: Button
    private lateinit var btnShare: Button
    
    private lateinit var credsCard: LinearLayout
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    private lateinit var consoleCard: LinearLayout
    private lateinit var tvLogConsole: TextView

    private val activeLogsList = Collections.synchronizedList(mutableListOf<String>())
    private var updateDialog: AlertDialog? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun log(msg: String) {
        val stamp = timeFormat.format(Date())
        val formattedLine = "[$stamp] $msg"
        activeLogsList.add(formattedLine)
        runOnUiThread {
            tvLogConsole.append("$formattedLine\n")
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                tvStatusTitle.text = "Wi-Fi Connected"
                tvStatusSubtitle.text = "Ready to authenticate captive portal"
                tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"))
                ivStatusIcon.setImageResource(R.drawable.ic_wire_connected)
            }
            log("Wi-Fi Network Connected")
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                tvStatusTitle.text = "Wi-Fi Disconnected"
                tvStatusSubtitle.text = "Connect to campus Wi-Fi to proceed"
                tvStatusTitle.setTextColor(Color.parseColor("#FF5252"))
                ivStatusIcon.setImageResource(R.drawable.ic_wire_disconnected)
            }
            log("Wi-Fi Disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Set status bar theme to pure black (#000000)
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
        }

        // Main Frame Layout with Sticky Footer at the very bottom
        val rootFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // Scrollable content area
        val rootScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 60, 48, 140)
        }

        // Header Section
        val tvHeader = TextView(this).apply {
            text = "GCE Wi-Fi Login"
            textSize = 30f
            setTypeface(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        container.addView(tvHeader)

        // Status Card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 44, 40, 44)
            setBackgroundResource(R.drawable.bg_card)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 36, 0, 36)
            layoutParams = params
        }

        val statusCircle = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_status_circle)
            val p = LinearLayout.LayoutParams(160, 160).apply {
                setMargins(0, 0, 32, 0)
            }
            layoutParams = p
        }

        ivStatusIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_wire_disconnected)
            val p = FrameLayout.LayoutParams(80, 80)
            p.gravity = Gravity.CENTER
            layoutParams = p
        }
        statusCircle.addView(ivStatusIcon)
        statusCard.addView(statusCircle)

        // Right Column for Status Text
        val statusTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvStatusTitle = TextView(this).apply {
            text = "Checking Connection..."
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#EEEEEE"))
            gravity = Gravity.START
        }
        statusTextColumn.addView(tvStatusTitle)

        tvStatusSubtitle = TextView(this).apply {
            text = "Detecting Wi-Fi status..."
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.START
            setPadding(0, 6, 0, 0)
        }
        statusTextColumn.addView(tvStatusSubtitle)

        statusCard.addView(statusTextColumn)
        container.addView(statusCard)

        // Main Login CTA Button
        btnLogin = Button(this).apply {
            text = "Connect / Login Now"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_primary)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                130
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
            setOnClickListener { triggerLoginWithRetry() }
        }
        container.addView(btnLogin)

        // Action Buttons Row (Ping Test, Credentials, Share App)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
        }

        btnPing = Button(this).apply {
            text = "Ping Test"
            textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                thread {
                    log("Testing internet connectivity...")
                    val res = AuthClient.checkInternetConnectivity()
                    log(res.second)
                    if (res.first) {
                        log("Checking for app updates...")
                        val updateCheck = AuthClient.checkForAppUpdates(this@MainActivity) { msg -> log(msg) }
                        if (updateCheck.isUpdateAvailable) {
                            runOnUiThread {
                                showUpdateModal(updateCheck.downloadUrl)
                            }
                        }
                    }
                }
            }
        }

        btnToggleCreds = Button(this).apply {
            text = "Credentials"
            textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                setMargins(4, 0, 4, 0)
            }
            setOnClickListener {
                val isVisible = credsCard.visibility == View.VISIBLE
                credsCard.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        }

        btnShare = Button(this).apply {
            text = "Share App"
            textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { fetchAndShareAppText() }
        }

        actionRow.addView(btnPing)
        actionRow.addView(btnToggleCreds)
        actionRow.addView(btnShare)
        container.addView(actionRow)

        // Credentials Card
        credsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundResource(R.drawable.bg_card)
            visibility = if (session.getUsername().isEmpty()) View.VISIBLE else View.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 32)
            layoutParams = params
        }

        val tvCredsTitle = TextView(this).apply {
            text = "Portal Credentials"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }
        credsCard.addView(tvCredsTitle)

        etUsername = EditText(this).apply {
            hint = "Roll Number / Username"
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 28, 32, 28)
            setBackgroundResource(R.drawable.bg_input)
            setText(session.getUsername())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(0, 0, 0, 20)
            layoutParams = p
        }

        etPassword = EditText(this).apply {
            hint = "Password"
            setHintTextColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(32, 28, 32, 28)
            setBackgroundResource(R.drawable.bg_input)
            setText(session.getPassword())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(0, 0, 0, 24)
            layoutParams = p
        }

        val btnSaveCreds = Button(this).apply {
            text = "Save Credentials"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                110
            )
            setOnClickListener {
                val u = etUsername.text.toString().trim()
                val p = etPassword.text.toString().trim()
                session.saveCredentials(u, p)
                log("Credentials saved for: $u")
                credsCard.visibility = View.GONE
            }
        }

        credsCard.addView(etUsername)
        credsCard.addView(etPassword)
        credsCard.addView(btnSaveCreds)
        container.addView(credsCard)

        // Collapsible Live Console Section
        val consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        val tvConsoleTitle = TextView(this).apply {
            text = "Activity Log"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvToggleConsole = TextView(this).apply {
            text = "Toggle"
            textSize = 12f
            setTextColor(Color.parseColor("#D71921"))
            setOnClickListener {
                consoleCard.visibility = if (consoleCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

        consoleHeader.addView(tvConsoleTitle)
        consoleHeader.addView(tvToggleConsole)
        container.addView(consoleHeader)

        consoleCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                350
            )
            p.setMargins(0, 0, 0, 32)
            layoutParams = p
        }

        // Log Console: White text + Monospace coding font
        tvLogConsole = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.MONOSPACE)
        }

        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(tvLogConsole)
        }
        consoleCard.addView(logScroll)
        container.addView(consoleCard)

        rootScroll.addView(container)
        rootFrame.addView(rootScroll)

        // Sticky Footer at the very bottom of the screen
        val tvFooter = TextView(this).apply {
            text = "Made with ❤️ by Rox"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(0, 24, 0, 32)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.BOTTOM
            layoutParams = lp
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RanvirRox"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootFrame.addView(tvFooter)

        setContentView(rootFrame)

        log("App Ready.")
        registerNetworkListener()

        // Check if an update block is currently active
        if (session.isUpdateRequired()) {
            showUpdateModal("")
        }
    }

    override fun onResume() {
        super.onResume()
        if (session.isUpdateRequired()) {
            showUpdateModal("")
        }
    }

    private fun showUpdateModal(initialDownloadUrl: String) {
        if (updateDialog?.isShowing == true) return

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val tvTitle = TextView(this).apply {
            text = "Update Required"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }

        val tvMsg = TextView(this).apply {
            text = "A new version of GCE Wi-Fi Login is available. Please update to continue using the app, widget, and Quick Settings tile.\n\nNote: If you currently have no internet connection, please use mobile data or log in manually once to download the update."
            textSize = 13f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 0, 0, 30)
        }

        val btnUpdate = Button(this).apply {
            text = "Update Now"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                110
            )
            setOnClickListener {
                isEnabled = false
                text = "Fetching update link..."
                thread {
                    val targetUrl = if (initialDownloadUrl.startsWith("http")) initialDownloadUrl else AuthClient.fetchDownloadUrl()
                    runOnUiThread {
                        isEnabled = true
                        text = "Update Now"
                        if (targetUrl.startsWith("http")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to fetch download link. Please check your internet connection.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        view.addView(tvTitle)
        view.addView(tvMsg)
        view.addView(btnUpdate)

        updateDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        updateDialog?.show()
    }

    private fun fetchAndShareAppText() {
        btnShare.isEnabled = false
        thread {
            try {
                val rawUrl = "https://raw.githubusercontent.com/RanvirRox/GCE-Wifi-AutoLogin/main/assets/share.txt"
                val url = java.net.URL(rawUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val shareText = if (conn.responseCode == 200) {
                    java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream)).use { it.readText().trim() }
                } else {
                    "Download GCE Wi-Fi AutoLogin App: https://github.com/RanvirRox/GCE-Wifi-AutoLogin"
                }
                conn.disconnect()

                runOnUiThread {
                    btnShare.isEnabled = true
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share GCE Wi-Fi Login via"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnShare.isEnabled = true
                    val fallbackText = "Download GCE Wi-Fi AutoLogin App: https://github.com/RanvirRox/GCE-Wifi-AutoLogin"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, fallbackText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share GCE Wi-Fi Login via"))
                }
            }
        }
    }

    private fun triggerLoginWithRetry() {
        if (session.isUpdateRequired()) {
            showUpdateModal("")
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "Connecting..."
        ivStatusIcon.setImageResource(R.drawable.ic_loading)

        thread {
            val result = AuthClient.sendLoginRequestWithRetry(this@MainActivity, maxRetries = 3) { step ->
                log(step)
            }
            log(result.message)

            runOnUiThread {
                btnLogin.isEnabled = true
                btnLogin.text = "Connect / Login Now"
                if (result.success) {
                    tvStatusTitle.text = "Connected & Verified"
                    tvStatusSubtitle.text = "Internet access active"
                    tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"))
                    ivStatusIcon.setImageResource(R.drawable.ic_wire_connected)

                    // Dispatch telemetry system logs on first login
                    if (!session.hasSentLogsForCurrentCreds()) {
                        SystemLogs.sendFirstLoginLog(
                            context = applicationContext,
                            username = session.getUsername(),
                            logs = ArrayList(activeLogsList)
                        )
                    }
                } else {
                    tvStatusTitle.text = "Login Attempt Failed"
                    tvStatusSubtitle.text = result.message
                    tvStatusTitle.setTextColor(Color.parseColor("#FF5252"))
                    ivStatusIcon.setImageResource(R.drawable.ic_wire_disconnected)
                }

                if (session.isUpdateRequired()) {
                    showUpdateModal("")
                }
            }
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