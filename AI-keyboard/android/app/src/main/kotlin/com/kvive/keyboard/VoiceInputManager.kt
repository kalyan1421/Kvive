package com.kvive.keyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Lifecycle helper around [SpeechRecognizer] with simple callback hooks.
 *
 * Usage pattern:
 * ```
 * val voiceManager = VoiceInputManager(context).apply {
 *     onResult = { text -> /* handle text */ }
 *     onError = { code, message -> /* surface error */ }
 * }
 * voiceManager.initialize()
 * voiceManager.startListening()
 * ```
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputManager"
        private const val EXTERNAL_MODEL_SUBDIR = "model"
        private const val ASSET_MODEL_DIR = "model"
        
        // Global flag to prevent duplicate starts across all instances
        @Volatile
        private var isGlobalListening = false
        
        fun isListening(): Boolean = isGlobalListening
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var voskEngine: VoskVoiceEngine? = null
    private var listening = false
    private var currentLanguageTag: String = Locale.getDefault().toLanguageTag()
    private var useVosk = false
    private var modelPath: String? = null
    private var lastVoskPartial: String? = null
    private var voskFinalDelivered = false

    var onResult: ((String) -> Unit)? = null
    var onError: ((Int, String) -> Unit)? = null
    var onListeningStateChange: ((Boolean) -> Unit)? = null
    var onPermissionRequired: (() -> Unit)? = null

    fun initialize(languageTag: String? = null, customModelPath: String? = null) {
        languageTag?.takeUnless { it.isBlank() }?.let { currentLanguageTag = it }
        modelPath = resolveModelPath(customModelPath)
        useVosk = modelPath?.let { File(it).exists() } == true

        if (useVosk) {
            setupVosk()
        } else {
            ensureRecognizer()
        }
    }

    fun startListening(languageTag: String? = null): Boolean {
        // Check global flag first to prevent duplicate starts across instances
        if (isGlobalListening || listening) {
            Log.w(TAG, "Voice input already active; ignoring duplicate start.")
            return false
        }

        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "Missing RECORD_AUDIO permission; requesting from caller.")
            mainHandler.post { onPermissionRequired?.invoke() }
            notifyError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return false
        }
        
        // Set global flag before starting
        isGlobalListening = true

        languageTag?.takeUnless { it.isBlank() }?.let { currentLanguageTag = it }

        return if (useVosk) {
            voskFinalDelivered = false
            lastVoskPartial = null
            if (voskEngine == null) {
                setupVosk()
            }

            val engine = voskEngine ?: return false
            if (!engine.prepare()) {
                notifyError(SpeechRecognizer.ERROR_CLIENT)
                return false
            }
            val started = engine.start()
            if (!started) {
                isGlobalListening = false
                notifyError(SpeechRecognizer.ERROR_CLIENT)
            }
            started
        } else {
            ensureRecognizer()

            val recognizer = speechRecognizer ?: return false
            val intent = buildRecognizerIntent(currentLanguageTag)

            try {
                recognizer.startListening(intent)
                setListening(true)
                true
            } catch (security: SecurityException) {
                Log.e(TAG, "SecurityException while starting recognition", security)
                isGlobalListening = false
                setListening(false)
                notifyError(SpeechRecognizer.ERROR_CLIENT)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start voice recognition", e)
                isGlobalListening = false
                setListening(false)
                notifyError(SpeechRecognizer.ERROR_CLIENT)
                false
            }
        }
    }

    fun stopListening() {
        if (!listening && !isGlobalListening) {
            return
        }

        // Reset global flag first
        isGlobalListening = false

        if (useVosk) {
            voskEngine?.stop()
            setListening(false)
            maybeDeliverPendingVoskResult()
        } else {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognizer", e)
            } finally {
                cancelRecognizer()
                setListening(false)
            }
        }
    }

    fun destroy() {
        // Ensure we stop listening and reset global flag
        isGlobalListening = false
        stopListening()
        
        if (useVosk) {
            voskEngine?.destroy()
            voskEngine = null
        } else {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer", e)
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) {
            return
        }

        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "Cannot create recognizer without RECORD_AUDIO permission.")
            mainHandler.post { onPermissionRequired?.invoke() }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device.")
            notifyError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    setListening(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    setListening(false)
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    setListening(false)
                    notifyError(error)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        deliverResult(text)
                    }
                    setListening(false)
                    Log.d(TAG, "Voice input result: $text")
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun buildRecognizerIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun cancelRecognizer() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling speech recognizer", e)
        }
    }

    private fun setListening(active: Boolean) {
        listening = active
        mainHandler.post { onListeningStateChange?.invoke(active) }
    }

    private fun deliverResult(text: String) {
        mainHandler.post { onResult?.invoke(text) }
    }

    private fun maybeDeliverPendingVoskResult() {
        if (!useVosk || voskFinalDelivered) {
            lastVoskPartial = null
            return
        }

        val fallback = lastVoskPartial?.trim().orEmpty()
        lastVoskPartial = null
        if (fallback.isNotEmpty()) {
            voskFinalDelivered = true
            deliverResult(fallback)
        }
    }

    private fun notifyError(code: Int) {
        val message = errorMessageForCode(code)
        mainHandler.post { onError?.invoke(code, message) }
    }

    private fun errorMessageForCode(code: Int): String {
        val resId = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice_input_error_no_match
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_input_error_timeout
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_input_error_retry
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_input_error_network
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_input_permission_required
            else -> R.string.voice_input_error
        }
        return context.getString(resId)
    }

    private fun resolveModelPath(customModelPath: String?): String? {
        customModelPath
            ?.takeIf { File(it).exists() }
            ?.let { return it }

        val externalDir = context.getExternalFilesDir(null)?.let {
            File(it, EXTERNAL_MODEL_SUBDIR).apply { if (!exists()) mkdirs() }
        }

        // If a model exists under /sdcard path (manual push), honor it first.
        findModelInDir(File("/sdcard/Android/data/${context.packageName}/files/$EXTERNAL_MODEL_SUBDIR"))
            ?.let { return it }

        // Try to ensure a packaged asset model is copied into external files dir.
        externalDir?.let { dir ->
            ensureModelFromAssets(dir)?.let { return it }
            findModelInDir(dir)?.let { return it }
        }

        return null
    }

    private fun findModelInDir(dir: File): String? {
        if (!dir.exists() || !dir.isDirectory) return null
        return dir.listFiles()
            ?.firstOrNull { it.isDirectory && File(it, "am").exists() }
            ?.absolutePath
    }

    private fun ensureModelFromAssets(targetRoot: File): String? {
        return try {
            val assetManager = context.assets
            val candidate = assetManager.list(ASSET_MODEL_DIR)
                ?.firstOrNull { name ->
                    val modelAssetPath = "$ASSET_MODEL_DIR/$name"
                    assetManager.list(modelAssetPath)?.contains("am") == true
                } ?: return null

            val destination = File(targetRoot, candidate)
            if (!File(destination, "am").exists()) {
                copyAssetFolder("$ASSET_MODEL_DIR/$candidate", destination)
            }
            destination.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Unable to prepare Vosk model from assets", e)
            null
        }
    }

    private fun copyAssetFolder(assetPath: String, destination: File) {
        val assetManager = context.assets
        val items = assetManager.list(assetPath)
        if (items.isNullOrEmpty()) {
            destination.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            if (!destination.exists()) destination.mkdirs()
            for (item in items) {
                copyAssetFolder("$assetPath/$item", File(destination, item))
            }
        }
    }

    private fun setupVosk() {
        val path = modelPath ?: return
        if (voskEngine == null) {
            voskEngine = VoskVoiceEngine(context, path).apply {
                onPartial = { partial ->
                    Log.d(TAG, "Vosk partial result: $partial")
                    lastVoskPartial = partial
                }
                onFinal = { text ->
                    voskFinalDelivered = true
                    val finalText = text.ifBlank { lastVoskPartial.orEmpty() }
                    lastVoskPartial = null
                    if (finalText.isNotBlank()) {
                        deliverResult(finalText)
                    }
                }
                onError = { message ->
                    setListening(false)
                    lastVoskPartial = null
                    mainHandler.post {
                        this@VoiceInputManager.onError?.invoke(
                            SpeechRecognizer.ERROR_CLIENT,
                            message
                        )
                    }
                }
                onListeningStateChange = { active ->
                    setListening(active)
                    if (!active) {
                        maybeDeliverPendingVoskResult()
                    }
                }
            }
        }

        if (hasRecordAudioPermission()) {
            if (voskEngine?.prepare() == false) {
                notifyError(SpeechRecognizer.ERROR_CLIENT)
            }
        } else {
            mainHandler.post { onPermissionRequired?.invoke() }
        }
    }
}
