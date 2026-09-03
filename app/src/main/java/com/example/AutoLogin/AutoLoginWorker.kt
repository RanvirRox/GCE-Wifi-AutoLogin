package com.example.autologin

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoLoginWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        fun enqueue(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()

                val request = OneTimeWorkRequestBuilder<AutoLoginWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "GCE_AUTO_LOGIN_RUN",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d("AutoLoginWorker", "Worker enqueued successfully.")
            } catch (e: Exception) {
                Log.e("AutoLoginWorker", "Failed to enqueue work", e)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = SessionManager(context)
        if (!session.isAutoLoginEnabled() || session.getUsername().isEmpty()) {
            return@withContext Result.success()
        }

        Log.d("AutoLoginWorker", "Background Wi-Fi login starting...")
        val result = AuthClient.sendLoginRequestWithRetry(context, maxRetries = 3) { progress ->
            Log.d("AutoLoginWorker", progress)
        }

        Log.d("AutoLoginWorker", "Login finished: ${result.message}")
        if (result.success) Result.success() else Result.failure()
    }
}