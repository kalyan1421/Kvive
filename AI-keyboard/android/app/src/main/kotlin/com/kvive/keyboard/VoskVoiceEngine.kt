package com.kvive.keyboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Thin wrapper around the Vosk SpeechService that exposes callbacks similar to
 * the platform SpeechRecognizer path used elsewhere in the app.
 */
class VoskVoiceEngine(
    private val context: Context,
    private val modelPath: String,
    private val sampleRate: Float = 16_000f
) {

    companion object {
        private const val TAG = "VoskVoiceEngine"
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null

    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningStateChange: ((Boolean) -> Unit)? = null

    fun prepare(): Boolean = try {
        if (model == null) {
            model = Model(modelPath)
        }
        if (recognizer == null) {
            recognizer = Recognizer(model, sampleRate)
        }
        true
    } catch (e: LinkageError) {
        handlePreparationError(e)
        false
    } catch (e: Exception) {
        handlePreparationError(e)
        false
    }

    fun start(): Boolean {
        val rec = recognizer ?: return false
        stop()
        return try {
            service = SpeechService(rec, sampleRate).apply {
                startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        hypothesis?.let { parseAndDispatch(it, partial = true) }
                    }

                    override fun onResult(hypothesis: String?) {
                        hypothesis?.let { parseAndDispatch(it, partial = false) }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        hypothesis?.let { parseAndDispatch(it, partial = false) }
                        onListeningStateChange?.invoke(false)
                    }

                    override fun onError(exception: Exception?) {
                        onListeningStateChange?.invoke(false)
                        onError?.invoke(exception?.localizedMessage ?: "Unknown error")
                    }

                    override fun onTimeout() {
                        onListeningStateChange?.invoke(false)
                    }
                })
            }
            onListeningStateChange?.invoke(true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start Vosk recognizer", e)
            onError?.invoke(context.getString(R.string.voice_input_error))
            false
        }
    }

    fun stop() {
        try {
            service?.stop()
        } catch (_: Exception) {
        }
        try {
            service?.shutdown()
        } catch (_: Exception) {
        }
        service = null
    }

    fun destroy() {
        stop()
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        try {
            model?.close()
        } catch (_: Exception) {
        }
        recognizer = null
        model = null
    }

    private fun parseAndDispatch(json: String, partial: Boolean) {
        try {
            val obj = JSONObject(json)
            val text = when {
                obj.has("text") -> obj.optString("text")
                obj.has("partial") -> obj.optString("partial")
                else -> ""
            }.trim()

            if (text.isEmpty()) return

            if (partial) {
                onPartial?.invoke(text)
            } else {
                onFinal?.invoke(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing Vosk response: $json", e)
        }
    }

    private fun handlePreparationError(throwable: Throwable) {
        Log.e(TAG, "Failed to load Vosk model at $modelPath", throwable)
        onError?.invoke(context.getString(R.string.voice_input_error))
    }
}
