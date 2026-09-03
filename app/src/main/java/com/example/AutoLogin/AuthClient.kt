package com.example.autologin

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AuthResult(val success: Boolean, val message: String)

object AuthClient {
    private const val PORTAL_URL = "http://172.16.16.16/24online/servlet/E24onlineHTTPClient"

    fun pingGoogle(): Pair<Boolean, String> {
        return try {
            val url = URL("http://connectivitycheck.gstatic.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            if (code == 204 || code == 200) {
                Pair(true, "🌐 Ping Google: ONLINE (Status $code)")
            } else {
                Pair(false, "⚠️ Ping Google: CAPTIVE PORTAL DETECTED (Status $code)")
            }
        } catch (e: Exception) {
            Pair(false, "❌ Ping Google: FAILED (${e.localizedMessage})")
        }
    }

    // Retries up to 3 times on failure
    fun sendLoginRequestWithRetry(
        context: Context,
        maxRetries: Int = 3,
        onProgress: ((String) -> Unit)? = null
    ): AuthResult {
        var lastResult = AuthResult(false, "No attempts made")

        for (attempt in 1..maxRetries) {
            onProgress?.invoke("⏳ [Attempt $attempt/$maxRetries] Sending POST to 172.16.16.16...")
            lastResult = sendLoginRequest(context)

            if (lastResult.success) {
                return lastResult
            }

            if (attempt < maxRetries) {
                onProgress?.invoke("⚠️ Attempt $attempt failed: ${lastResult.message}. Retrying in 2s...")
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {}
            }
        }

        return AuthResult(false, "❌ All $maxRetries attempts failed. Last error: ${lastResult.message}")
    }

    fun sendLoginRequest(context: Context): AuthResult {
        val session = SessionManager(context)
        val user = session.getUsername()
        val pass = session.getPassword()

        if (user.isEmpty() || pass.isEmpty()) {
            return AuthResult(false, "⚠️ Aborted: Username/Password empty! Click 'Credentials'.")
        }

        var conn: HttpURLConnection? = null
        return try {
            val url = URL(PORTAL_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
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
                AuthResult(true, "✅ [200 OK] Response Received! (Len: ${responseText.length})")
            } else {
                AuthResult(false, "Server returned HTTP $statusCode")
            }
        } catch (e: Exception) {
            AuthResult(false, "POST Failed: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }
}