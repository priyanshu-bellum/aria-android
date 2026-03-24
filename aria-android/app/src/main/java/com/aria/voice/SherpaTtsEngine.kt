package com.aria.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-to-speech engine with the Sherpa ONNX interface ready for future swap-in.
 *
 * Current implementation delegates to Android's built-in [TextToSpeech] while keeping
 * the public API surface identical to what a native Sherpa ONNX integration would expose.
 * Swapping in Sherpa ONNX requires only replacing the private implementation while leaving
 * [initialize], [speak], [stop], [isReady], and [destroy] signatures unchanged.
 */
class SherpaTtsEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    /** Pending completion callbacks keyed by utterance ID. */
    private val pendingCallbacks = ConcurrentHashMap<String, () -> Unit>()

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }

    /**
     * Initialize Android TTS and wait for the engine to become ready.
     *
     * Uses [CompletableDeferred] to convert the async [TextToSpeech.OnInitListener] callback
     * into a suspend call so callers can `await` readiness without blocking a thread.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Boolean>()

        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                deferred.complete(true)
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
                deferred.complete(false)
            }
        }

        val initOk = deferred.await()
        if (!initOk) {
            engine.shutdown()
            isReady = false
            return@withContext false
        }

        // Configure voice properties
        val langResult = engine.setLanguage(Locale.US)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale.US language data missing or not supported (result=$langResult). TTS may still work with device default.")
        }
        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)

        // Register utterance progress listener for onComplete callbacks
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS utterance started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                utteranceId ?: return
                pendingCallbacks.remove(utteranceId)?.invoke()
                Log.d(TAG, "TTS utterance done: $utteranceId")
            }

            @Deprecated("Deprecated in API 21 but still required for full compatibility")
            override fun onError(utteranceId: String?) {
                utteranceId ?: return
                Log.e(TAG, "TTS utterance error: $utteranceId")
                pendingCallbacks.remove(utteranceId)?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId ?: return
                Log.e(TAG, "TTS utterance error: $utteranceId, code=$errorCode")
                pendingCallbacks.remove(utteranceId)?.invoke()
            }
        })

        tts = engine
        isReady = true
        Log.i(TAG, "SherpaTtsEngine (Android TTS) initialized successfully")
        true
    }

    /**
     * Speak [text] via TTS. Text is appended to the internal queue so concurrent calls
     * are serialized naturally by [TextToSpeech.QUEUE_ADD].
     *
     * @param text       The string to synthesize and play.
     * @param onComplete Optional callback invoked when this specific utterance finishes.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        val engine = tts
        if (!isReady || engine == null) {
            Log.w(TAG, "speak() called but engine is not ready — dropping: \"$text\"")
            onComplete?.invoke()
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        if (onComplete != null) {
            pendingCallbacks[utteranceId] = onComplete
        }

        val params = Bundle()
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "TextToSpeech.speak() returned ERROR for utterance $utteranceId")
            pendingCallbacks.remove(utteranceId)?.invoke()
        } else {
            Log.d(TAG, "Queued utterance $utteranceId: \"${text.take(60)}\"")
        }
    }

    /**
     * Immediately stop any ongoing or queued speech.
     */
    fun stop() {
        try {
            tts?.stop()
            pendingCallbacks.clear()
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    /** Whether the engine is initialized and ready to accept [speak] calls. */
    fun isReady(): Boolean = isReady

    /**
     * Shut down the TTS engine and release all resources. After [destroy], the engine
     * must not be used again without calling [initialize] first.
     */
    fun destroy() {
        try {
            pendingCallbacks.clear()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying TTS engine", e)
        }
        tts = null
        isReady = false
        Log.i(TAG, "SherpaTtsEngine destroyed")
    }
}
