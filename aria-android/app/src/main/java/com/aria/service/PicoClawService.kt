package com.aria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aria.picoclaw.ConfigWriter
import com.aria.picoclaw.PicoClawManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@AndroidEntryPoint
class PicoClawService : Service() {

    @Inject lateinit var configWriter: ConfigWriter
    @Inject lateinit var okHttpClient: OkHttpClient

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var picoClawProcess: Process? = null
    private var healthCheckJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 3

    companion object {
        const val TAG = "PicoClawService"
        const val CHANNEL_ID = "aria_picoclaw"
        const val NOTIFICATION_ID = 1
        const val HEALTH_URL = "http://localhost:7331/health"
        const val HEALTH_INTERVAL_MS = 30_000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("ARIA is starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            startPicoClaw()
        }
        return START_STICKY
    }

    private suspend fun startPicoClaw() {
        val manager = PicoClawManager(this)
        if (!manager.install()) {
            Log.w(TAG, "PicoClaw binary not available — running without orchestrator")
            updateNotification("ARIA running (no orchestrator)")
            return
        }

        try {
            // Generate config
            val configFile = configWriter.writeConfig()

            // Start PicoClaw process
            picoClawProcess = ProcessBuilder(
                manager.getBinaryPath(),
                "--config", configFile.absolutePath
            )
                .directory(filesDir)
                .redirectErrorStream(true)
                .start()

            // Clean up config after startup
            delay(2000)
            configWriter.deleteConfig()

            updateNotification("ARIA is running")
            retryCount = 0

            // Start health check loop
            startHealthChecks()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PicoClaw", e)
            handleProcessDeath()
        }
    }

    private fun startHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                if (!isProcessAlive()) {
                    Log.w(TAG, "PicoClaw process died")
                    handleProcessDeath()
                    return@launch
                }
            }
        }
    }

    private fun isProcessAlive(): Boolean {
        return try {
            val request = Request.Builder().url(HEALTH_URL).get().build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            picoClawProcess?.isAlive == true
        }
    }

    private suspend fun handleProcessDeath() {
        if (retryCount < maxRetries) {
            retryCount++
            val delayMs = 1000L * (1 shl retryCount) // exponential backoff
            Log.i(TAG, "Restarting PicoClaw (attempt $retryCount) in ${delayMs}ms")
            updateNotification("ARIA restarting... (attempt $retryCount)")
            delay(delayMs)
            startPicoClaw()
        } else {
            Log.e(TAG, "PicoClaw failed after $maxRetries retries")
            updateNotification("ARIA orchestrator offline")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        picoClawProcess?.destroy()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ARIA Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ARIA background orchestrator"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ARIA")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
