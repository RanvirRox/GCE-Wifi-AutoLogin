package com.example.autologin

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class AuthResult(val success: Boolean, val message: String)
data class UpdateCheckResult(val isUpdateAvailable: Boolean, val latestVersion: String, val downloadUrl: String)

object AuthClient {
    const val CURRENT_APP_VERSION = "1.0"
    private const val PORTAL_URL = "http://172.16.16.16/24online/servlet/E24onlineHTTPClient"

    private const val VERSION_URL = "https://raw.githubusercontent.com/RanvirRox/GCE-Wifi-AutoLogin/main/assets/version.txt"
    private const val DOWNLOAD_LINK_URL = "https://raw.githubusercontent.com/RanvirRox/GCE-Wifi-AutoLogin/main/assets/download_url.txt"

    // List of reliable endpoints for rapid internet verification
    private val PING_ENDPOINTS = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.google.com/generate_204",
        "http://1.1.1.1",
        "http://1.0.0.1"
    )

    private fun checkSingleEndpoint(endpointUrl: String, timeoutMs: Int = 2000): Pair<Boolean, String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpointUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            
            val code = conn.responseCode
            if (code == 204 || code in 200..302) {
                Pair(true, "ONLINE (Status $code via ${url.host})")
            } else {
                Pair(false, "CAPTIVE PORTAL DETECTED (Status $code via ${url.host})")
            }
        } catch (e: Exception) {
            Pair(false, "FAILED (${e.localizedMessage})")
        } finally {
            conn?.disconnect()
        }
    }

    // Parallel multi-server ping: returns true as soon as ANY server responds successfully
    fun checkInternetConnectivity(timeoutMs: Int = 2500): Pair<Boolean, String> {
        val executor = Executors.newFixedThreadPool(PING_ENDPOINTS.size)
        val futures = mutableListOf<Future<Pair<Boolean, String>>>()

        for (endpoint in PING_ENDPOINTS) {
            futures.add(executor.submit<Pair<Boolean, String>> {
                checkSingleEndpoint(endpoint, timeoutMs)
            })
        }

        var successResult: Pair<Boolean, String>? = null
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline && successResult == null) {
            for (future in futures) {
                if (future.isDone) {
                    try {
                        val res = future.get()
                        if (res.first) {
                            successResult = res
                            break
                        }
                    } catch (_: Exception) {}
                }
            }
            if (successResult != null) break
            try { Thread.sleep(50) } catch (_: InterruptedException) {}
        }

        executor.shutdownNow()

        return if (successResult != null) {
            Pair(true, "Internet Verified: ${successResult.second}")
        } else {
            Pair(false, "Internet Verification Failed (All endpoints timed out or failed)")
        }
    }

    fun pingGoogle(): Pair<Boolean, String> = checkInternetConnectivity()

    // Fetches version.txt and download_url.txt from GitHub to check for updates
    fun checkForAppUpdates(context: Context, onProgress: ((String) -> Unit)? = null): UpdateCheckResult {
        onProgress?.invoke("Checking for app updates at $VERSION_URL...")
        return try {
            val url = URL(VERSION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)")

            val code = conn.responseCode
            onProgress?.invoke("Version check server returned HTTP $code")

            if (code == 200) {
                val latestVer = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText().trim() }
                conn.disconnect()
                onProgress?.invoke("Latest online version: '$latestVer' (Current local version: '$CURRENT_APP_VERSION')")

                if (isNewerVersion(latestVer, CURRENT_APP_VERSION)) {
                    SessionManager(context).setUpdateRequired(true)
                    val downloadUrl = fetchDownloadUrl()
                    if (downloadUrl.isEmpty()) {
                        onProgress?.invoke("New version v$latestVer detected, but download URL could not be fetched.")
                        UpdateCheckResult(true, latestVer, "")
                    } else {
                        onProgress?.invoke("New update required! Latest: v$latestVer, Download: $downloadUrl")
                        UpdateCheckResult(true, latestVer, downloadUrl)
                    }
                } else {
                    SessionManager(context).setUpdateRequired(false)
                    onProgress?.invoke("App is up-to-date (v$CURRENT_APP_VERSION)")
                    UpdateCheckResult(false, CURRENT_APP_VERSION, "")
                }
            } else {
                conn.disconnect()
                onProgress?.invoke("Version check failed with status code $code")
                UpdateCheckResult(false, CURRENT_APP_VERSION, "")
            }
        } catch (e: Exception) {
            onProgress?.invoke("Version check failed with error: ${e.localizedMessage}")
            UpdateCheckResult(false, CURRENT_APP_VERSION, "")
        }
    }

    // Strictly returns only the exact URL string stored in download_url.txt (no fallback URLs)
    fun fetchDownloadUrl(): String {
        return try {
            val url = URL(DOWNLOAD_LINK_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            if (conn.responseCode == 200) {
                val downloadLink = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText().trim() }
                conn.disconnect()
                downloadLink
            } else {
                conn.disconnect()
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.replace("v", "").trim().split(".").map { it.toInt() }
            val currentParts = current.replace("v", "").trim().split(".").map { it.toInt() }
            val length = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {
            return latest != current && latest.isNotEmpty()
        }
        return false
    }

    fun sendLoginRequestWithRetry(
        context: Context,
        maxRetries: Int = 3,
        onProgress: ((String) -> Unit)? = null
    ): AuthResult {
        val session = SessionManager(context)
        if (session.isUpdateRequired()) {
            return AuthResult(false, "Update required! Please update to the latest version.")
        }

        var lastResult = AuthResult(false, "No attempts made")

        for (attempt in 1..maxRetries) {
            onProgress?.invoke("[Attempt $attempt/$maxRetries] Sending POST request to 172.16.16.16...")
            lastResult = sendLoginRequest(context)

            if (lastResult.success) {
                break
            }

            if (attempt < maxRetries) {
                onProgress?.invoke("Attempt $attempt failed: ${lastResult.message}. Retrying in 2s...")
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {}
            }
        }

        onProgress?.invoke("Verifying internet connectivity across servers...")
        val pingRes = checkInternetConnectivity()
        onProgress?.invoke(pingRes.second)

        // Run update check ONLY AFTER being online is verified
        if (pingRes.first) {
            val updateCheck = checkForAppUpdates(context, onProgress)
            if (updateCheck.isUpdateAvailable) {
                return AuthResult(false, "Update required to v${updateCheck.latestVersion}! Please update the app.")
            }
            return AuthResult(true, "Login Successful & Verified: ${pingRes.second}")
        } else {
            return AuthResult(false, lastResult.message + " | Ping: ${pingRes.second}")
        }
    }

    fun sendLoginRequest(context: Context): AuthResult {
        val session = SessionManager(context)
        if (session.isUpdateRequired()) {
            return AuthResult(false, "Update required! Please update the app.")
        }

        val user = session.getUsername()
        val pass = session.getPassword()

        if (user.isEmpty() || pass.isEmpty()) {
            return AuthResult(false, "Aborted: Username or Password empty. Please set credentials.")
        }

        var conn: HttpURLConnection? = null
        return try {
            val url = URL(PORTAL_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
            )

            val params = mapOf(
                "mode" to "191",
                "isAccessDenied" to "false",
                "url" to "null",
                "message" to "",
                "regusingpinid" to "",
                "checkClose" to "1",
                "sessionTimeout" to "-1",
                "guestmsgreq" to "false",
                "logintype" to "2",
                "orgSessionTimeout" to "-1",
                "chrome" to "-1",
                "alerttime" to "null",
                "timeout" to "-1",
                "popupalert" to "0",
                "dtold" to "0",
                "mac" to "",
                "servername" to "172.16.16.16",
                "temptype" to "",
                "selfregpageid" to "",
                "leave" to "no",
                "macaddress" to "",
                "ipaddress" to "",
                "successURL" to "/24online/webpages/client.jsp?pretemplateid=108",
                "username" to user,
                "password" to pass,
                "saveinfo" to "",
                "loginotp" to "false",
                "logincaptcha" to "false",
                "registeruserotp" to "false",
                "registercaptcha" to "false"
            )

            val postData = StringBuilder()
            for ((key, value) in params) {
                if (postData.isNotEmpty()) postData.append('&')
                postData.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(value, "UTF-8"))
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(postData.toString())
                it.flush()
            }

            val statusCode = conn.responseCode
            val responseText = if (statusCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
            }

            if (statusCode == 200) {
                AuthResult(true, "Response 200 OK Received (Length: ${responseText.length})")
            } else {
                AuthResult(false, "Server returned HTTP $statusCode")
            }
        } catch (e: Exception) {
            AuthResult(false, "POST Request Failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}