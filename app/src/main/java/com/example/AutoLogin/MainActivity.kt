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

        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
        }

        val rootFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

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

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvHeader = TextView(this).apply {
            text = "GCE Wi-Fi Login"
            textSize = 28f
            setTypeface(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnGuide = Button(this).apply {
            text = "Guide"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(180, 84)
            addClickAnimation()
            setOnClickListener { showOnboardingDialog() }
        }

        headerRow.addView(tvHeader)
        headerRow.addView(btnGuide)
        container.addView(headerRow)

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

        btnLogin = Button(this).apply {
            text = "Login to Wi-Fi"
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
            addClickAnimation()
            setOnClickListener { triggerLoginWithRetry() }
        }
        container.addView(btnLogin)

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
            text = "Ping Internet"
            textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            addClickAnimation()
            setOnClickListener {
                thread {
                    log("Testing internet connectivity via Google...")
                    val res = AuthClient.pingGoogle()
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
            addClickAnimation()
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
            addClickAnimation()
            setOnClickListener { fetchAndShareAppText() }
        }

        actionRow.addView(btnPing)
        actionRow.addView(btnToggleCreds)
        actionRow.addView(btnShare)
        container.addView(actionRow)

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
            hint = "UserID / aadharNumber"
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
            addClickAnimation()
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

        if (!session.isOnboarded()) {
            showOnboardingDialog()
        }

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

        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCheckVersion = Button(this).apply {
            text = "Re-check Version"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CCCCCC"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            addClickAnimation()
            setOnClickListener {
                isEnabled = false
                text = "Checking..."
                thread {
                    val updateCheck = AuthClient.checkForAppUpdates(this@MainActivity) { msg -> log(msg) }
                    runOnUiThread {
                        isEnabled = true
                        text = "Re-check Version"
                        if (!updateCheck.isUpdateAvailable) {
                            Toast.makeText(this@MainActivity, "App is up to date!", Toast.LENGTH_SHORT).show()
                            updateDialog?.dismiss()
                            updateDialog = null
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Update still required (Latest: v${updateCheck.latestVersion})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        val btnUpdate = Button(this).apply {
            text = "Update Now"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1.2f).apply {
                setMargins(8, 0, 0, 0)
            }
            addClickAnimation()
            setOnClickListener {
                isEnabled = false
                text = "Fetching link..."
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

        btnContainer.addView(btnCheckVersion)
        btnContainer.addView(btnUpdate)

        view.addView(tvTitle)
        view.addView(tvMsg)
        view.addView(btnContainer)

        updateDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        updateDialog?.show()
    }

    private fun showOnboardingDialog() {
        var currentPage = 0

        data class OnboardingStep(val title: String, val badge: String, val body: String)

        val steps = listOf(
            OnboardingStep(
                title = "Welcome to GCE Wi-Fi Login",
                badge = "STEP 1 OF 4 • GETTING STARTED",
                body = "Connecting to hostel Wi-Fi usually requires SignIn and typing your UserID and Password every single time.\n\nThis app automates that entire process instantly in 1 click! Just enter your UserID & password once under Credentials, and you're all set."
            ),
            OnboardingStep(
                title = "Add Quick Settings Tile",
                badge = "STEP 2 OF 4 • ONE-TAP LOGIN",
                body = "You don't even need to open this app to connect!\n\n1. Swipe down your phone's notification panel.\n2. Tap the Edit / Pencil icon.\n3. Drag 'Wi-Fi Login' into your active tiles.\n\nYou can log in directly from your notification bar while using any app!"
            ),
            OnboardingStep(
                title = "Add Home Screen Widget",
                badge = "STEP 3 OF 4 • HOME SCREEN SHORTCUT",
                body = "Prefer a Login Widget on your home screen?\n\n1. Long-press any empty space on your home screen.\n2. Select 'Widgets' and scroll to 'Wi-Fi Login'.\n3. Drag the circular tile to your home screen.\n\nTap it anytime to instantly authenticate Wi-Fi!"
            ),
            OnboardingStep(
                title = "Feedback & Contact Developer",
                badge = "STEP 4 OF 4 • SUPPORT & FEEDBACK",
                body = "Have suggestions, feedback, or facing an issue?\n\nFeel free to reach out directly to the developer:\n\nRANVIR SINGH\n• Email: ranvirrox5999@gmail.com\n• GitHub: github.com/RanvirRox\n\nThank you for using GCE Wi-Fi AutoLogin!"
            )
        )

        var dialogRef: AlertDialog? = null

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#141414"))
        }

        val tvBadge = TextView(this).apply {
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#D71921"))
            setPadding(0, 0, 0, 12)
        }

        val tvTitle = TextView(this).apply {
            textSize = 19f
            setTypeface(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 20)
        }

        val tvBody = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#D0D0D0"))
            setLineSpacing(1.3f, 1.3f)
            setPadding(0, 0, 0, 32)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnSkip = Button(this).apply {
            text = "Skip"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 100, 0.8f).apply {
                setMargins(0, 0, 8, 0)
            }
            addClickAnimation()
            setOnClickListener {
                session.setOnboarded(true)
                dialogRef?.dismiss()
            }
        }

        val btnNext = Button(this).apply {
            text = "Next ➔"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(0, 100, 1.2f).apply {
                setMargins(8, 0, 0, 0)
            }
            addClickAnimation()
        }

        fun renderPage(page: Int) {
            val step = steps[page]
            tvBadge.text = step.badge
            tvTitle.text = step.title
            tvBody.text = step.body

            if (page == steps.size - 1) {
                btnNext.text = "Get Started"
                btnSkip.visibility = View.GONE
            } else {
                btnNext.text = "Next ➔"
                btnSkip.visibility = View.VISIBLE
            }
        }

        btnNext.setOnClickListener {
            if (currentPage < steps.size - 1) {
                currentPage++
                renderPage(currentPage)
            } else {
                session.setOnboarded(true)
                dialogRef?.dismiss()
            }
        }

        btnRow.addView(btnSkip)
        btnRow.addView(btnNext)

        view.addView(tvBadge)
        view.addView(tvTitle)
        view.addView(tvBody)
        view.addView(btnRow)

        renderPage(0)

        dialogRef = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        dialogRef.show()
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
            val result = AuthClient.sendLoginRequestWithRetry(
                context = this@MainActivity,
                maxRetries = 3,
                onProgress = { step -> log(step) },
                onNetworkPromoted = {
                    runOnUiThread {
                        tvStatusTitle.text = "Connected & Verified"
                        tvStatusSubtitle.text = "Internet access active"
                        tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"))
                        ivStatusIcon.setImageResource(R.drawable.ic_wire_connected)
                    }
                },
                onCollectLogs = { ArrayList(activeLogsList) }
            )
            log(result.message)

            runOnUiThread {
                btnLogin.isEnabled = true
                btnLogin.text = "Login to Wi-Fi"
                if (!result.success) {
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

fun View.addClickAnimation() {
    setOnTouchListener { v, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(60).start()
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
        false
    }
}
