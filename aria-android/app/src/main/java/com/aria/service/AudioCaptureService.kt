package com.aria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aria.voice.SpeechManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that manages continuous voice listening via [SpeechManager].
 *
 * Lifecycle:
 * - [onCreate] — creates the notification channel and pins the foreground notification.
 * - [onStartCommand] — initializes [SpeechManager] and starts listening. Returns [START_STICKY]
 *   so the OS restarts the service if killed.
 * - [onDestroy] — stops listening, destroys the speech manager, and cancels all coroutines.
 */
@AndroidEntryPoint
class AudioCaptureService : Service() {

    @Inject
    lateinit var speechManager: SpeechManager

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val CHANNEL_ID = "aria_audio"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_NAME = "Voice Listening"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("ARIA is listening"))
        Log.i(TAG, "AudioCaptureService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coroutineScope.launch {
            try {
                val ready = speechManager.initialize()
                if (ready) {
                    Log.i(TAG, "SpeechManager initialized — starting voice listener")
                    speechManager.startListening()
                    updateNotification("ARIA is listening")
                } else {
                    Log.w(TAG, "SpeechManager initialization failed — service running without voice input")
                    updateNotification("ARIA voice unavailable")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing SpeechManager", e)
                updateNotification("ARIA voice error")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            speechManager.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening on destroy", e)
        }
        try {
            speechManager.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SpeechManager on destroy", e)
        }
        coroutineScope.cancel()
        Log.i(TAG, "AudioCaptureService destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------ //
    // Notification helpers
    // ------------------------------------------------------------------ //

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ARIA ambient voice listener"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ARIA")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
