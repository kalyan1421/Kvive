package com.kvive.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Full-screen voice capture overlay that handles permissions, listening, and delivery back to the IME.
 */
class VoiceInputActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var micContainer: View
    private lateinit var micIcon: ImageView

    private lateinit var voiceInputManager: VoiceInputManager
    private var languageTag: String = Locale.getDefault().toLanguageTag()
    private var permissionRequested = false
    private var recognitionInProgress = false
    private var retryCount = 0
    private var statusFromError = false

    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure window to not interfere with keyboard's InputConnection
        window?.apply {
            // Allow the keyboard service to maintain its InputConnection
            addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            // Don't dim the background
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        
        setContentView(R.layout.activity_voice_input)
        applyPanelHeight()

        languageTag = intent.getStringExtra(EXTRA_LANGUAGE_TAG)?.takeUnless { it.isNullOrBlank() } ?: languageTag

        statusText = findViewById(R.id.voice_status_text)
        micContainer = findViewById(R.id.voice_mic_container)
        micIcon = findViewById(R.id.voice_mic_icon)

        findViewById<View>(R.id.voice_back_button).setOnClickListener {
            finishWithCancellation()
        }

        micContainer.setOnClickListener {
            if (!recognitionInProgress) {
                retryCount = 0
                ensurePermissionAndStart()
            }
        }

        voiceInputManager = VoiceInputManager(this).apply {
            onListeningStateChange = { isActive ->
                recognitionInProgress = isActive
                if (isActive) {
                    statusFromError = false
                }
                updateListeningUi(isActive)
                AIKeyboardService.getInstance()?.showVoiceInputFeedback(isActive)
            }
            onPermissionRequired = {
                requestPermission()
            }
            onResult = { spokenText ->
                deliverResult(spokenText)
            }
            onError = { code, message ->
                handleRecognizerError(code, message)
            }
        }
        voiceInputManager.initialize(languageTag)

        if (savedInstanceState != null) {
            permissionRequested = savedInstanceState.getBoolean(KEY_PERMISSION_REQUESTED, false)
            recognitionInProgress = savedInstanceState.getBoolean(KEY_RECOGNITION_IN_PROGRESS, false)
            retryCount = savedInstanceState.getInt(KEY_RETRY_COUNT, 0)
            statusFromError = savedInstanceState.getBoolean(KEY_STATUS_FROM_ERROR, false)
        }

        uiHandler.post { ensurePermissionAndStart() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_REQUESTED, permissionRequested)
        outState.putBoolean(KEY_RECOGNITION_IN_PROGRESS, recognitionInProgress)
        outState.putInt(KEY_RETRY_COUNT, retryCount)
        outState.putBoolean(KEY_STATUS_FROM_ERROR, statusFromError)
    }

    override fun onResume() {
        super.onResume()
        if (!recognitionInProgress) {
            uiHandler.post { ensurePermissionAndStart() }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_RECORD_AUDIO) return

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionRequested = false
            if (::voiceInputManager.isInitialized) {
                voiceInputManager.initialize(languageTag)
            }
            uiHandler.post { startRecognition() }
        } else {
            permissionRequested = false
            val rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            if (!rationale) {
                // Toast removed - permission error logged only
                val intent = Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } else {
                statusText.text = getString(R.string.voice_input_permission_required)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop listening immediately when paused to prevent background processing
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.stopListening()
            if (isFinishing) {
                // Ensure complete cleanup if finishing
                voiceInputManager.destroy()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure complete cleanup before destroying
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.stopListening()
            voiceInputManager.destroy()
        }
        // Notify service that voice input is closed
        AIKeyboardService.getInstance()?.onVoiceInputClosed()
    }

    private fun ensurePermissionAndStart() {
        applyPanelHeight()
        if (hasRecordAudioPermission()) {
            if (::voiceInputManager.isInitialized) {
                voiceInputManager.initialize(languageTag)
            }
            startRecognition()
        } else {
            requestPermission()
        }
    }

    private fun requestPermission() {
        if (permissionRequested) {
            return
        }
        permissionRequested = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_RECORD_AUDIO
        )
    }

    private fun startRecognition() {
        if (recognitionInProgress) {
            return
        }

        retryCount = 0
        val started = if (::voiceInputManager.isInitialized) {
            voiceInputManager.startListening(languageTag)
        } else {
            false
        }
        if (started) {
            recognitionInProgress = true
            updateListeningUi(true)
            statusFromError = false
        } else {
            recognitionInProgress = false
            micContainer.isActivated = false
            micIcon.isActivated = false
            AIKeyboardService.getInstance()?.showVoiceInputFeedback(false)
        }
    }

    private fun deliverResult(text: String) {
        recognitionInProgress = false
        statusFromError = false
        val service = AIKeyboardService.getInstance()
        if (service != null) {
            // Commit text directly without closing activity
            service.handleVoiceInputResult(text)
            
            // Reset UI and automatically start listening again for next input
            uiHandler.postDelayed({
                // Check if activity is still valid before proceeding
                if (isFinishing || isDestroyed) {
                    return@postDelayed
                }
                
                retryCount = 0
                statusFromError = false
                
                // Show brief success feedback
                statusText.text = "✓ Text added. Speak again or tap ← to close"
                
                // Auto-restart recognition after brief pause
                uiHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && !recognitionInProgress) {
                        startRecognition()
                    }
                }, 800) // 800ms pause before auto-restart
            }, 100)
        } else {
            // Service not available, close activity
            finishWithCancellation()
        }
    }

    private fun handleRecognizerError(error: Int, message: String) {
        recognitionInProgress = false
        AIKeyboardService.getInstance()?.showVoiceInputFeedback(false)

        statusText.text = message
        statusFromError = true
        micContainer.isActivated = false
        micIcon.isActivated = false

        if (shouldRetry(error)) {
            uiHandler.postDelayed({ startRecognition() }, RETRY_DELAY_MS)
        }
    }

    private fun shouldRetry(error: Int): Boolean {
        if (retryCount >= MAX_RETRY_COUNT) return false
        return when (error) {
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                retryCount += 1
                true
            }
            else -> false
        }
    }

    private fun updateListeningUi(isListening: Boolean) {
        micContainer.isActivated = isListening
        micIcon.isActivated = isListening
        if (isListening) {
            statusText.text = getString(R.string.voice_input_listening)
        } else if (!recognitionInProgress && !statusFromError) {
            statusText.text = getString(R.string.voice_input_prompt_subtitle)
        }
    }

    private fun finishWithCancellation() {
        // Stop listening and cleanup before closing
        if (::voiceInputManager.isInitialized) {
            voiceInputManager.stopListening()
            voiceInputManager.destroy()
        }
        statusFromError = false
        recognitionInProgress = false
        AIKeyboardService.getInstance()?.onVoiceInputClosed()
        
        // Properly finish the activity
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 701
        private const val MAX_RETRY_COUNT = 1
        private const val RETRY_DELAY_MS = 450L
        private const val EXTRA_LANGUAGE_TAG = "extra_language_tag"
        private const val KEY_PERMISSION_REQUESTED = "voice_permission_requested"
        private const val KEY_RECOGNITION_IN_PROGRESS = "voice_recognition_in_progress"
        private const val KEY_RETRY_COUNT = "voice_retry_count"
        private const val KEY_STATUS_FROM_ERROR = "voice_status_from_error"

        fun createIntent(context: Context, languageTag: String?): Intent =
            Intent(context, VoiceInputActivity::class.java).apply {
                putExtra(EXTRA_LANGUAGE_TAG, languageTag)
            }
    }

    private fun applyPanelHeight() {
        val panelHeight = resolvePanelHeight()
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, panelHeight)
            setGravity(Gravity.BOTTOM)
        }

        findViewById<View>(R.id.voice_root)?.let { root ->
            val params = root.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                panelHeight
            )
            params.height = panelHeight
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            root.layoutParams = params
        }
    }

    private fun resolvePanelHeight(): Int {
        val saved = KeyboardHeightManager.getSavedHeight(this)
        if (saved > 0) return saved
        val manager = KeyboardHeightManager(this)
        val computed = manager.calculateKeyboardHeight(includeToolbar = true, includeSuggestions = true)
        val minHeight = (resources.displayMetrics.heightPixels * 0.3f).toInt()
        return computed.coerceAtLeast(minHeight)
    }
}
