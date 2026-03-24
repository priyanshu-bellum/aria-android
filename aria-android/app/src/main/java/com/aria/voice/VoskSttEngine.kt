package com.aria.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

/**
 * Offline speech-to-text engine backed by Vosk.
 *
 * Model loading priority:
 * 1. `context.filesDir/vosk-model/` — pre-downloaded model
 * 2. `assets/vosk-model-small-en-us/` — bundled lightweight model
 *
 * If neither location provides a model, [initialize] returns `false` and the caller
 * should fall back to Deepgram cloud STT.
 */
class VoskSttEngine(private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var onResult: ((String) -> Unit)? = null
    private var onPartial: ((String) -> Unit)? = null
    private var isReady = false

    companion object {
        private const val TAG = "VoskSttEngine"
        private const val SAMPLE_RATE = 16000f
        private const val FILES_DIR_MODEL = "vosk-model"
        private const val ASSETS_MODEL = "vosk-model-small-en-in-0.4"
    }

    /**
     * Load the Vosk model. Checks filesDir first, then attempts to copy from assets.
     * Returns true only when a [Model] is successfully loaded and [SpeechService] can be started.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = resolveModelDirectory() ?: run {
                Log.w(TAG, "No Vosk model available — offline STT disabled")
                return@withContext false
            }

            Log.d(TAG, "Loading Vosk model from ${modelDir.absolutePath}")
            model = Model(modelDir.absolutePath)
            isReady = true
            Log.i(TAG, "Vosk model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Vosk model", e)
            isReady = false
            false
        }
    }

    /**
     * Returns the model directory to use, or null if no model is available.
     *
     * Order:
     * 1. `filesDir/vosk-model` (user-downloaded or previously extracted)
     * 2. Copy from assets if `assets/vosk-model-small-en-us` exists
     */
    private fun resolveModelDirectory(): File? {
        // 1. Check filesDir
        val filesDirModel = File(context.filesDir, FILES_DIR_MODEL)
        if (filesDirModel.exists() && filesDirModel.isDirectory) {
            Log.d(TAG, "Found model in filesDir")
            return filesDirModel
        }

        // 2. Check assets and copy if present
        return try {
            val assetList = context.assets.list("") ?: emptyArray()
            if (ASSETS_MODEL in assetList) {
                Log.d(TAG, "Copying model from assets to filesDir")
                copyAssetFolder(ASSETS_MODEL, filesDirModel)
                if (filesDirModel.exists()) filesDirModel else null
            } else {
                Log.d(TAG, "No bundled model in assets ($ASSETS_MODEL)")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error checking/copying assets model", e)
            null
        }
    }

    /**
     * Recursively copies an asset folder to a destination [File].
     */
    private fun copyAssetFolder(assetPath: String, dest: File) {
        val children = try {
            context.assets.list(assetPath) ?: emptyArray()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list assets at $assetPath", e)
            return
        }

        if (children.isEmpty()) {
            // Leaf file
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            // Directory
            dest.mkdirs()
            for (child in children) {
                copyAssetFolder("$assetPath/$child", File(dest, child))
            }
        }
    }

    /**
     * Start continuous recognition. Callbacks fire on the thread used by [SpeechService].
     *
     * @param onPartialResult Called with each interim partial transcript.
     * @param onFinalResult   Called with the finalized transcript when the speaker pauses.
     */
    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit
    ) {
        if (!isReady || model == null) {
            Log.w(TAG, "startListening called but engine is not ready")
            return
        }
        if (speechService != null) {
            Log.d(TAG, "Already listening — ignoring duplicate startListening call")
            return
        }

        onPartial = onPartialResult
        onResult = onFinalResult

        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also { svc ->
                svc.startListening(recognitionListener)
            }
            Log.i(TAG, "SpeechService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechService", e)
            speechService = null
        }
    }

    /**
     * Stop the active [SpeechService]. Safe to call when not listening.
     */
    fun stopListening() {
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechService", e)
        }
        speechService = null
    }

    /** Whether the model is loaded and the engine can accept [startListening] calls. */
    fun isReady(): Boolean = isReady

    /**
     * Release all Vosk resources. After calling [destroy] the engine must not be used again.
     */
    fun destroy() {
        try {
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down SpeechService", e)
        }
        speechService = null
        try {
            model?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Vosk Model", e)
        }
        model = null
        isReady = false
        onResult = null
        onPartial = null
        Log.i(TAG, "VoskSttEngine destroyed")
    }

    // ------------------------------------------------------------------ //
    // Vosk RecognitionListener
    // ------------------------------------------------------------------ //

    private val recognitionListener = object : RecognitionListener {

        override fun onResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank()) return
            val text = extractTextField(hypothesis, "text")
            if (text.isNotBlank()) {
                Log.d(TAG, "Final result: $text")
                onResult?.invoke(text)
            }
        }

        override fun onPartialResult(hypothesis: String?) {
            if (hypothesis.isNullOrBlank()) return
            val partial = extractTextField(hypothesis, "partial")
            if (partial.isNotBlank()) {
                onPartial?.invoke(partial)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            // Fired when SpeechService is stopped. Treat like a regular result.
            if (hypothesis.isNullOrBlank()) return
            val text = extractTextField(hypothesis, "text")
            if (text.isNotBlank()) {
                Log.d(TAG, "Final (stop) result: $text")
                onResult?.invoke(text)
            }
        }

        override fun onError(e: Exception?) {
            Log.e(TAG, "Vosk recognition error", e)
        }

        override fun onTimeout() {
            Log.d(TAG, "Vosk recognition timed out")
        }
    }

    /**
     * Minimal JSON field extractor that avoids pulling in a full JSON library.
     *
     * Handles both `{"text": "hello"}` and `{"partial": "hel"}` shapes.
     */
    private fun extractTextField(json: String, field: String): String {
        return try {
            // Look for "field" : "value" or "field":"value"
            val marker = "\"$field\""
            val fieldIdx = json.indexOf(marker)
            if (fieldIdx < 0) return ""
            val colonIdx = json.indexOf(':', fieldIdx + marker.length)
            if (colonIdx < 0) return ""
            val quoteOpen = json.indexOf('"', colonIdx + 1)
            if (quoteOpen < 0) return ""
            val quoteClose = json.indexOf('"', quoteOpen + 1)
            if (quoteClose < 0) return ""
            json.substring(quoteOpen + 1, quoteClose).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Vosk JSON field '$field' from: $json", e)
            ""
        }
    }
}
