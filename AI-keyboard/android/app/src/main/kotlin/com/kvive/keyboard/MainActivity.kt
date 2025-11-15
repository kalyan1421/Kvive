package com.kvive.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import com.kvive.keyboard.utils.LogUtil
import com.kvive.keyboard.utils.BroadcastManager
import com.kvive.keyboard.GestureAction
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "ai_keyboard/config"
        private const val LANGUAGE_CHANNEL = "com.kvive.keyboard/language"
        private const val AI_CHANNEL = "ai_keyboard/unified_ai"
        private const val PROMPT_CHANNEL = "ai_keyboard/prompts"
        private const val CLIPBOARD_CHANNEL = "ai_keyboard/clipboard"
        private const val THEME_CHANNEL = "keyboard.theme"
        private const val SOUND_CHANNEL = "keyboard.sound"
        private const val EFFECTS_CHANNEL = "keyboard.effects"
    }
    
    private lateinit var unifiedAIService: UnifiedAIService

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var navigationMethodChannel: MethodChannel? = null
    private var clipboardChannel: MethodChannel? = null
    private var clipboardBroadcastReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        handleNavigationIntent(intent)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "default"
            val channelName = "Default Notifications"
            val channelDescription = "Default notification channel for app notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            LogUtil.d("MainActivity", "✅ Created notification channel: $channelId")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            LogUtil.d("MainActivity", "🧭 Deep link navigation: $navigateTo")
            
            // Send to Flutter via method channel (with delay to ensure Flutter is ready)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    navigationMethodChannel?.invokeMethod("navigate", mapOf("route" to navigateTo))
                    LogUtil.d("MainActivity", "✅ Navigation sent to Flutter: $navigateTo")
                } catch (e: Exception) {
                    LogUtil.e("MainActivity", "Error sending navigation to Flutter", e)
                }
            }, 500) // Delay to ensure Flutter is ready
        }
    }

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Initialize navigation method channel
        navigationMethodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        
        // Initialize Unified AI Service
        unifiedAIService = UnifiedAIService(this)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    try {
                        when (call.method) {
                            "isKeyboardEnabled" -> {
                                val isEnabled = withContext(Dispatchers.IO) { isKeyboardEnabled() }
                                result.success(isEnabled)
                            }
                            "isKeyboardActive" -> {
                                val isActive = withContext(Dispatchers.IO) { isKeyboardActive() }
                                result.success(isActive)
                            }
                            "openKeyboardSettings" -> {
                                openKeyboardSettings()
                                result.success(true)
                            }
                            "openInputMethodPicker" -> {
                                openInputMethodPicker()
                                result.success(true)
                            }
                            "updateSettings" -> {
                                // Enhanced settings with Gboard + CleverType features
                                val theme = call.argument<String>("theme") ?: "default_theme"
                                val popupEnabled = call.argument<Boolean>("popupEnabled") ?: false
                                val aiSuggestions = call.argument<Boolean>("aiSuggestions") ?: true
                                val autocorrect = call.argument<Boolean>("autoCorrect") ?: true  // ✅ Fixed: Match Flutter camelCase
                                val emojiSuggestions = call.argument<Boolean>("emojiSuggestions") ?: true
                                val nextWordPrediction = call.argument<Boolean>("nextWordPrediction") ?: true
                                val clipboardEnabled = call.argument<Boolean>("clipboardEnabled") ?: true
                                val clipboardWindowSec = call.argument<Int>("clipboardWindowSec") ?: 60
                                val clipboardHistoryItems = call.argument<Int>("clipboardHistoryItems") ?: 20
                                val dictionaryEnabled = call.argument<Boolean>("dictionaryEnabled") ?: true
                                val autoCapitalization = call.argument<Boolean>("autoCapitalization") ?: true
                                val doubleSpacePeriod = call.argument<Boolean>("doubleSpacePeriod") ?: true
                                val autoFillSuggestion = call.argument<Boolean>("autoFillSuggestion") ?: true
                                val rememberCapsState = call.argument<Boolean>("rememberCapsState") ?: false
                                
                                // Sound & Vibration Settings (Unified System)
                                val soundEnabled = call.argument<Boolean>("soundEnabled") ?: true
                                val soundVolume = call.argument<Double>("soundVolume") ?: 0.5
                                val keyPressSounds = call.argument<Boolean>("keyPressSounds") ?: true
                                val longPressSounds = call.argument<Boolean>("longPressSounds") ?: true
                                val repeatedActionSounds = call.argument<Boolean>("repeatedActionSounds") ?: true
                                val vibrationEnabled = call.argument<Boolean>("vibrationEnabled") ?: true
                                val vibrationMs = call.argument<Int>("vibrationMs") ?: 50
                                val useHapticInterface = call.argument<Boolean>("useHapticInterface") ?: true
                                val keyPressVibration = call.argument<Boolean>("keyPressVibration") ?: true
                                val longPressVibration = call.argument<Boolean>("longPressVibration") ?: true
                                val repeatedActionVibration = call.argument<Boolean>("repeatedActionVibration") ?: true
                                
                                // Legacy settings (backwards compat)
                                val swipeTyping = call.argument<Boolean>("swipeTyping") ?: true
                                val voiceInput = call.argument<Boolean>("voiceInput") ?: true
                                val shiftFeedback = call.argument<Boolean>("shiftFeedback") ?: false
                                val showNumberRow = call.argument<Boolean>("showNumberRow") ?: false
                                
                                withContext(Dispatchers.IO) {
                                    updateKeyboardSettingsV2(
                                        theme, popupEnabled, aiSuggestions, autocorrect, 
                                        emojiSuggestions, nextWordPrediction, clipboardEnabled,
                                        clipboardWindowSec, clipboardHistoryItems, dictionaryEnabled,
                                        autoCapitalization, doubleSpacePeriod, autoFillSuggestion,
                                        rememberCapsState, soundEnabled,
                                        soundVolume, keyPressSounds, longPressSounds, repeatedActionSounds,
                                        vibrationEnabled, vibrationMs, useHapticInterface,
                                        keyPressVibration, longPressVibration, repeatedActionVibration,
                                        swipeTyping, voiceInput, shiftFeedback, showNumberRow
                                    )
                                }
                                LogUtil.d("MainActivity", "✓ Settings updated via MethodChannel")
                                result.success(true)
                            }
                            "updateGestureSettings" -> {
                                val glideTyping = call.argument<Boolean>("glideTyping") ?: true
                                val showTrailPref = call.argument<Boolean>("showGlideTrail") ?: true
                                val effectiveShowTrail = glideTyping && showTrailPref
                                val fadeMs = if (effectiveShowTrail) {
                                    call.argument<Int>("glideTrailFadeTime") ?: 200
                                } else 0
                                val alwaysDeleteWord = call.argument<Boolean>("alwaysDeleteWord") ?: true
                                val velocityThreshold = call.argument<Double>("swipeVelocityThreshold") ?: 1900.0
                                val distanceThreshold = call.argument<Double>("swipeDistanceThreshold") ?: 20.0
                                val swipeUpAction = call.argument<String>("swipeUpAction") ?: GestureAction.SHIFT.code
                                val swipeDownAction = call.argument<String>("swipeDownAction") ?: GestureAction.HIDE_KEYBOARD.code
                                val swipeLeftAction = call.argument<String>("swipeLeftAction") ?: GestureAction.DELETE_CHARACTER_BEFORE_CURSOR.code
                                val swipeRightAction = call.argument<String>("swipeRightAction") ?: GestureAction.INSERT_SPACE.code
                                val spaceLongPressAction = call.argument<String>("spaceLongPressAction") ?: GestureAction.SHOW_INPUT_METHOD_PICKER.code
                                val spaceSwipeDownAction = call.argument<String>("spaceSwipeDownAction") ?: GestureAction.NONE.code
                                val spaceSwipeLeftAction = call.argument<String>("spaceSwipeLeftAction") ?: GestureAction.MOVE_CURSOR_LEFT.code
                                val spaceSwipeRightAction = call.argument<String>("spaceSwipeRightAction") ?: GestureAction.MOVE_CURSOR_RIGHT.code
                                val deleteSwipeLeftAction = call.argument<String>("deleteSwipeLeftAction") ?: GestureAction.DELETE_WORD_BEFORE_CURSOR.code
                                val deleteLongPressAction = call.argument<String>("deleteLongPressAction") ?: GestureAction.DELETE_CHARACTER_BEFORE_CURSOR.code

                                withContext(Dispatchers.IO) {
                                    val prefs = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
                                    prefs.edit()
                                        .putBoolean("gestures_glide_typing", glideTyping)
                                        .putBoolean("gestures_show_glide_trail", effectiveShowTrail)
                                        .putInt("gestures_glide_trail_fade_ms", fadeMs)
                                        .putBoolean("gestures_always_delete_word", alwaysDeleteWord)
                                        .putFloat("gestures_swipe_velocity_threshold", velocityThreshold.toFloat())
                                        .putFloat("gestures_swipe_distance_threshold", distanceThreshold.toFloat())
                                        .putString("gestures_swipe_up_action", swipeUpAction)
                                        .putString("gestures_swipe_down_action", swipeDownAction)
                                        .putString("gestures_swipe_left_action", swipeLeftAction)
                                        .putString("gestures_swipe_right_action", swipeRightAction)
                                        .putString("gestures_space_long_press_action", spaceLongPressAction)
                                        .putString("gestures_space_swipe_down_action", spaceSwipeDownAction)
                                        .putString("gestures_space_swipe_left_action", spaceSwipeLeftAction)
                                        .putString("gestures_space_swipe_right_action", spaceSwipeRightAction)
                                        .putString("gestures_delete_swipe_left_action", deleteSwipeLeftAction)
                                        .putString("gestures_delete_long_press_action", deleteLongPressAction)
                                        .apply()

                                    notifyKeyboardServiceSettingsChanged()
                                }
                                result.success(true)
                            }
                            "notifyConfigChange" -> {
                                // Unified method for all config changes (settings + themes)
                                LogUtil.d("MainActivity", "✓ notifyConfigChange received")
                                withContext(Dispatchers.IO) {
                                    sendSettingsChangedBroadcast()
                                }
                                result.success(true)
                            }
                            "broadcastSettingsChanged" -> {
                                // Force immediate broadcast to keyboard service
                                LogUtil.d("MainActivity", "✓ broadcastSettingsChanged received - forcing immediate update")
                                withContext(Dispatchers.IO) {
                                    sendSettingsChangedBroadcast()
                                }
                                result.success(true)
                            }
                            "notifyThemeChange" -> {
                                withContext(Dispatchers.IO) {
                                    notifyKeyboardServiceThemeChanged()
                                }
                                result.success(true)
                            }
                            "setOneHandedMode" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: false
                                val side = call.argument<String>("side") ?: "right"
                                val width = call.argument<Double>("width") ?: 0.75
                                val applied = withKeyboardService { service ->
                                    service.applyOneHandedMode(enabled, side, width.toFloat())
                                }
                                if (applied) {
                                    result.success(true)
                                } else {
                                    result.error("service_unavailable", "Keyboard service not available", null)
                                }
                            }
                            "themeChanged" -> {
                                val themeId = call.argument<String>("themeId") ?: "default_theme"
                                val themeName = call.argument<String>("themeName") ?: "Unknown Theme"
                                val hasThemeData = call.argument<Boolean>("hasThemeData") ?: false
                                
                                LogUtil.d("MainActivity", "🎨 Theme V2 changed: $themeName ($themeId)")
                                withContext(Dispatchers.IO) {
                                    notifyKeyboardServiceThemeChangedV2(themeId, themeName, hasThemeData)
                                }
                                result.success(true)
                            }
                            "updateTheme" -> {
                                // Unified theme update from Flutter with specific color values
                                val keyboardBg = call.argument<String>("keyboard_theme_bg")
                                val keyColor = call.argument<String>("keyboard_key_color")
                                
                                LogUtil.d("MainActivity", "🎨 updateTheme called: bg=$keyboardBg, key=$keyColor")
                                
                                withContext(Dispatchers.IO) {
                                    val prefs = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
                                    prefs.edit().apply {
                                        keyboardBg?.let { putString("keyboard_theme_bg", it) }
                                        keyColor?.let { putString("keyboard_key_color", it) }
                                        apply()
                                    }
                                    
                                    // Notify keyboard service to apply theme to panels
                                    notifyKeyboardServiceThemeChanged()
                                }
                                result.success(true)
                            }
                            "settingsChanged" -> {
                                LogUtil.d("MainActivity", "Settings changed broadcast requested")
                                withContext(Dispatchers.IO) {
                                    sendSettingsChangedBroadcast()
                                }
                                result.success(true)
                            }
                            "updateClipboardSettings" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: true
                                val maxHistorySize = call.argument<Int>("maxHistorySize") ?: 20
                                val autoExpiryEnabled = call.argument<Boolean>("autoExpiryEnabled") ?: true
                                val expiryDurationMinutes = call.argument<Long>("expiryDurationMinutes") ?: 60L
                                val templates = call.argument<List<Map<String, Any>>>("templates") ?: emptyList()
                                
                                withContext(Dispatchers.IO) {
                                    updateClipboardSettings(enabled, maxHistorySize, autoExpiryEnabled, expiryDurationMinutes, templates)
                                }
                                result.success(true)
                            }
                            "getEmojiSettings" -> {
                                val settings = withContext(Dispatchers.IO) { getEmojiSettings() }
                                result.success(settings)
                            }
                            "updateEmojiSettings" -> {
                                val skinTone = call.argument<String>("skinTone") ?: ""
                                val historyMaxSize = call.argument<Int>("historyMaxSize") ?: 90
                                
                                withContext(Dispatchers.IO) {
                                    updateEmojiSettings(skinTone, historyMaxSize)
                                }
                                result.success(true)
                            }
                            "getEmojiConfig" -> {
                                val config = withContext(Dispatchers.IO) { getEmojiConfig() }
                                result.success(config)
                            }
                            "updateEmojiConfig" -> {
                                val skinTone = call.argument<String>("skinTone") ?: ""
                                val recent = call.argument<List<String>>("recent") ?: emptyList()
                                
                                withContext(Dispatchers.IO) {
                                    updateEmojiConfig(skinTone, recent)
                                }
                                result.success(true)
                            }
                            "sendBroadcast" -> {
                                val action = call.argument<String>("action") ?: ""
                                LogUtil.d("MainActivity", "sendBroadcast called with action: $action")
                                withContext(Dispatchers.IO) {
                                    sendBroadcast(action)
                                }
                                result.success(true)
                            }
                            "clearLearnedWords" -> {
                                LogUtil.d("MainActivity", "Clearing learned words")
                                withContext(Dispatchers.IO) {
                                    clearUserLearnedWords()
                                }
                                result.success(true)
                            }
                            "setEnabledLanguages" -> {
                                val enabled = call.argument<List<String>>("enabled") ?: emptyList()
                                val current = call.argument<String>("current") ?: "en"
                                LogUtil.d("MainActivity", "Setting enabled languages: $enabled, current: $current")
                                withContext(Dispatchers.IO) {
                                    setEnabledLanguages(enabled, current)
                                }
                                result.success(true)
                            }
                            "setCurrentLanguage" -> {
                                val language = call.argument<String>("language") ?: "en"
                                LogUtil.d("MainActivity", "Setting current language: $language")
                                withContext(Dispatchers.IO) {
                                    setCurrentLanguage(language)
                                }
                                result.success(true)
                            }
                            "setMultilingual" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: false
                                LogUtil.d("MainActivity", "Setting multilingual mode: $enabled")
                                withContext(Dispatchers.IO) {
                                    setMultilingualMode(enabled)
                                }
                                result.success(true)
                            }
                            "setTransliterationEnabled" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: true
                                LogUtil.d("MainActivity", "Setting transliteration: $enabled")
                                withContext(Dispatchers.IO) {
                                    setTransliterationEnabled(enabled)
                                }
                                result.success(true)
                            }
                            "setReverseTransliterationEnabled" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: false
                                LogUtil.d("MainActivity", "Setting reverse transliteration: $enabled")
                                withContext(Dispatchers.IO) {
                                    setReverseTransliterationEnabled(enabled)
                                }
                                result.success(true)
                            }
                            else -> result.notImplemented()
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to execute method: ${call.method}", e.message)
                    }
                }
            }
            
        // Unified AI Channel
        setupUnifiedAIChannel(flutterEngine)
        
        // Dynamic Prompt Management Channel
        setupPromptChannel(flutterEngine)
        
        // Clipboard Management Channel
        setupClipboardChannel(flutterEngine)

        // Theme & personalization channels
        setupThemeChannel(flutterEngine)
        setupSoundChannel(flutterEngine)
        setupEffectsChannel(flutterEngine)

        // Language Download Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LANGUAGE_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "downloadLanguageData" -> {
                        val lang = call.argument<String>("lang")
                        if (lang == null) {
                            result.error("ARG_MISSING", "Language code missing", null)
                            return@setMethodCallHandler
                        }
                        LogUtil.d("MainActivity", "🌐 Starting Firebase download for language: $lang")
                        ioScope.launch {
                            try {
                                downloadLanguageFromFirebase(lang, flutterEngine)
                                withContext(Dispatchers.Main) { 
                                    LogUtil.d("MainActivity", "✅ Successfully downloaded language: $lang")
                                    result.success(true) 
                                }
                            } catch (e: Exception) {
                                LogUtil.e("MainActivity", "❌ Failed to download language $lang", e)
                                withContext(Dispatchers.Main) { 
                                    result.error("DOWNLOAD_FAIL", e.message, null) 
                                }
                            }
                        }
                    }
                    "deleteCachedLanguageData" -> {
                        val lang = call.argument<String>("lang")
                        if (lang == null) {
                            result.error("ARG_MISSING", "Language code missing", null)
                            return@setMethodCallHandler
                        }
                        ioScope.launch {
                            try {
                                deleteCachedLanguageData(lang)
                                withContext(Dispatchers.Main) { result.success(true) }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { 
                                    result.error("DELETE_FAIL", e.message, null) 
                                }
                            }
                        }
                    }
                    "updateCachedLanguagesList" -> {
                        val cachedLanguages = call.argument<List<String>>("cachedLanguages") ?: emptyList()
                        LogUtil.d("MainActivity", "📋 Updated cached languages list: $cachedLanguages")
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private suspend fun isKeyboardEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.let { inputMethodManager ->
                val packageName = packageName
                val enabledInputMethods = inputMethodManager.enabledInputMethodList
                enabledInputMethods.any { it.packageName == packageName }
            } ?: true // Fallback to true to avoid blocking the UI
        } catch (e: Exception) {
            true // Fallback to true to avoid blocking the UI
        }
    }

    private suspend fun isKeyboardActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val packageName = packageName
            val inputMethodId = "$packageName/$packageName.AIKeyboardService"
            
            val currentInputMethod = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            
            // Check if our keyboard service is currently selected
            val isDefault = inputMethodId == currentInputMethod
            
            // Additional check: see if our keyboard is in the current input method
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val containsPackage = currentIme?.contains(packageName) == true
            
            isDefault || containsPackage
        } catch (e: Exception) {
            false // Fallback to false to encourage user to set it up
        }
    }

    private fun openKeyboardSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openInputMethodPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    private suspend fun updateKeyboardSettingsV2(
        theme: String,
        popupEnabled: Boolean,
        aiSuggestions: Boolean,
        autocorrect: Boolean,
        emojiSuggestions: Boolean,
        nextWordPrediction: Boolean,
        clipboardEnabled: Boolean,
        clipboardWindowSec: Int,
        clipboardHistoryItems: Int,
        dictionaryEnabled: Boolean,
        autoCapitalization: Boolean,
        doubleSpacePeriod: Boolean,
        autoFillSuggestion: Boolean,
        rememberCapsState: Boolean,
        soundEnabled: Boolean,
        soundVolume: Double,
        keyPressSounds: Boolean,
        longPressSounds: Boolean,
        repeatedActionSounds: Boolean,
        vibrationEnabled: Boolean,
        vibrationMs: Int,
        useHapticInterface: Boolean,
        keyPressVibration: Boolean,
        longPressVibration: Boolean,
        repeatedActionVibration: Boolean,
        swipeTyping: Boolean,
        voiceInput: Boolean,
        shiftFeedback: Boolean,
        showNumberRow: Boolean
    ) = withContext(Dispatchers.IO) {
        // Store enhanced settings in SharedPreferences
        val prefs = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("keyboard_theme", theme)
            .putBoolean("popup_enabled", popupEnabled)
            .putBoolean("ai_suggestions", aiSuggestions)
            .putBoolean("auto_correct", autocorrect)  // ✅ Fixed: Match AIKeyboardService key
            .putBoolean("emoji_suggestions", emojiSuggestions)
            .putBoolean("next_word_prediction", nextWordPrediction)
            .putBoolean("clipboard_suggestions_enabled", clipboardEnabled)
            .putInt("clipboard_window_sec", clipboardWindowSec)
            .putInt("clipboard_history_items", clipboardHistoryItems)
            .putBoolean("dictionary_enabled", dictionaryEnabled)
            .putBoolean("auto_capitalization", autoCapitalization)
            .putBoolean("double_space_period", doubleSpacePeriod)
            .putBoolean("auto_fill_suggestion", autoFillSuggestion)
            .putBoolean("remember_caps_state", rememberCapsState)
            // Sound & Vibration Settings (Unified System)
            .putBoolean("sound_enabled", soundEnabled)
            .putFloat("sound_volume", soundVolume.toFloat())
            .putBoolean("key_press_sounds", keyPressSounds)
            .putBoolean("long_press_sounds", longPressSounds)
            .putBoolean("repeated_action_sounds", repeatedActionSounds)
            .putBoolean("vibration_enabled", vibrationEnabled)
            .putInt("vibration_ms", vibrationMs)
            .putBoolean("use_haptic_interface", useHapticInterface)
            .putBoolean("key_press_vibration", keyPressVibration)
            .putBoolean("long_press_vibration", longPressVibration)
            .putBoolean("repeated_action_vibration", repeatedActionVibration)
            // Legacy settings
            .putBoolean("swipe_typing", swipeTyping)
            .putBoolean("voice_input", voiceInput)
            .putBoolean("show_shift_feedback", shiftFeedback)
            .putBoolean("show_number_row", showNumberRow)
            .apply()

        // Mirror critical toggles into Flutter SharedPreferences so the service and Flutter stay in sync
        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit()
            .putBoolean("flutter.keyboard.popupPreview", popupEnabled)
            .putBoolean("flutter.keyboard_settings.popup_visibility", popupEnabled)
            .putBoolean("flutter.auto_capitalization", autoCapitalization)
            .putBoolean("flutter.autoCapitalization", autoCapitalization)
            .putBoolean("flutter.double_space_period", doubleSpacePeriod)
            .putBoolean("flutter.doubleSpacePeriod", doubleSpacePeriod)
            .putBoolean("flutter.auto_fill_suggestion", autoFillSuggestion)
            .putBoolean("flutter.autoFillSuggestion", autoFillSuggestion)
            .putBoolean("flutter.remember_caps_state", rememberCapsState)
            .putBoolean("flutter.rememberCapsState", rememberCapsState)
            // ✅ CRITICAL: Save sound settings to FlutterSharedPreferences for Android service
            .putBoolean("flutter.sound_enabled", soundEnabled)
            .putInt("flutter.sound_volume", (soundVolume * 100).toInt()) // Convert 0-1 to 0-100 scale
            .putBoolean("flutter.key_press_sounds", keyPressSounds)
            .putBoolean("flutter.long_press_sounds", longPressSounds)
            .putBoolean("flutter.repeated_action_sounds", repeatedActionSounds)
            .putBoolean("flutter.vibration_enabled", vibrationEnabled)
            .putInt("flutter.vibration_ms", vibrationMs)
            .putBoolean("flutter.use_haptic_interface", useHapticInterface)
            .putBoolean("flutter.key_press_vibration", keyPressVibration)
            .putBoolean("flutter.long_press_vibration", longPressVibration)
            .putBoolean("flutter.repeated_action_vibration", repeatedActionVibration)
            .apply()
            
        LogUtil.d("MainActivity", "✓ Settings V2 persisted to SharedPreferences")
        LogUtil.d("TypingSync", "Settings applied: popup=$popupEnabled, autocorrect=$autocorrect, emoji=$emojiSuggestions, nextWord=$nextWordPrediction, clipboard=$clipboardEnabled")
        
        // Notify keyboard service to reload settings immediately
        notifyKeyboardServiceSettingsChanged()
    }
    
    private fun notifyKeyboardServiceSettingsChanged() {
        BroadcastManager.sendToKeyboard(this, "com.kvive.keyboard.SETTINGS_CHANGED")
    }

    private suspend fun withKeyboardService(action: (AIKeyboardService) -> Unit): Boolean {
        return withContext(Dispatchers.Main) {
            val service = AIKeyboardService.getInstance()
            if (service != null) {
                action(service)
                true
            } else {
                LogUtil.w("MainActivity", "Keyboard service not available for channel request")
                false
            }
        }
    }

    private fun notifyKeyboardServiceThemeChanged() {
        try {
            LogUtil.d("MainActivity", "Starting theme change notification process")
            
            // Log SharedPreferences state for debugging
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val themeData = prefs.getString("flutter.current_theme_data", null)
            val themeId = prefs.getString("flutter.current_theme_id", null)
            LogUtil.d("MainActivity", "Theme data - ID: $themeId, Data length: ${themeData?.length ?: 0}")
            
            // Send broadcast with theme extras
            val extras = Bundle().apply {
                putString("theme_id", themeId)
                putBoolean("has_theme_data", themeData != null)
            }
            BroadcastManager.sendToKeyboard(this, "com.kvive.keyboard.THEME_CHANGED", extras)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Failed to send theme change broadcast", e)
        }
    }
    
    private fun notifyKeyboardServiceThemeChangedV2(themeId: String, themeName: String, hasThemeData: Boolean) {
        try {
            LogUtil.d("MainActivity", "Starting Theme V2 change notification: $themeName")
            
            // Log V2 SharedPreferences state for debugging
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val themeV2Data = prefs.getString("flutter.theme.v2.json", null)
            val settingsChanged = prefs.getBoolean("flutter.keyboard_settings.settings_changed", false)
            LogUtil.d("MainActivity", "Theme V2 data - ID: $themeId, Data length: ${themeV2Data?.length ?: 0}, Settings changed: $settingsChanged")
            
            // Send broadcast with theme V2 extras
            val extras = Bundle().apply {
                putString("theme_id", themeId)
                putString("theme_name", themeName)
                putBoolean("has_theme_data", hasThemeData)
                putBoolean("is_v2_theme", true)
            }
            BroadcastManager.sendToKeyboard(this, "com.kvive.keyboard.THEME_CHANGED", extras)
            LogUtil.d("MainActivity", "🎨 Theme V2 broadcast sent: $themeName")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Failed to send Theme V2 broadcast", e)
        }
    }
    
    private fun sendSettingsChangedBroadcast() {
        BroadcastManager.sendToKeyboard(this, "com.kvive.keyboard.SETTINGS_CHANGED")
    }
    
    private fun sendBroadcast(action: String) {
        BroadcastManager.sendToKeyboard(this, action)
    }
    
    private suspend fun updateClipboardSettings(
        enabled: Boolean,
        maxHistorySize: Int,
        autoExpiryEnabled: Boolean,
        expiryDurationMinutes: Long,
        templates: List<Map<String, Any>>
    ) = withContext(Dispatchers.IO) {
        LogUtil.d("MainActivity", "💾 Saving to SharedPreferences: clipboard_enabled=$enabled")
        
        // Store clipboard settings in SharedPreferences
        getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("clipboard_enabled", enabled)
            .putInt("max_history_size", maxHistorySize)
            .putBoolean("auto_expiry_enabled", autoExpiryEnabled)
            .putLong("expiry_duration_minutes", expiryDurationMinutes)
            .commit() // Use commit() for immediate persistence
            
        LogUtil.d("MainActivity", "✅ Saved to SharedPreferences successfully")
            
        // Store templates
        try {
            val templatesJson = org.json.JSONArray()
            templates.forEach { templateMap ->
                val templateJson = org.json.JSONObject().apply {
                    put("text", templateMap["text"] ?: "")
                    put("category", templateMap["category"] ?: "")
                    put("isTemplate", true)
                    put("isPinned", true)
                    put("id", templateMap["id"] ?: java.util.UUID.randomUUID().toString())
                    put("timestamp", System.currentTimeMillis())
                }
                templatesJson.put(templateJson)
            }
            
            getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
                .edit()
                .putString("template_items", templatesJson.toString())
                .commit()
                
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error saving clipboard templates", e)
        }
        
        // Notify keyboard service to reload clipboard settings
        notifyKeyboardServiceClipboardChanged()
    }
    
    private fun notifyKeyboardServiceClipboardChanged() {
        try {
            val intent = Intent("com.kvive.keyboard.CLIPBOARD_CHANGED").apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            LogUtil.d("MainActivity", "Clipboard settings broadcast sent")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Failed to send clipboard settings broadcast", e)
        }
    }
    
    private fun getEmojiSettings(): Map<String, Any> {
        val prefs = getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val skinTone = prefs.getString("preferred_skin_tone", "🏽") ?: "🏽"
        val historyMaxSize = prefs.getInt("emoji_history_max_size", 90)
        val historyJson = prefs.getString("emoji_history", "[]") ?: "[]"
        
        // Parse history list
        val history = mutableListOf<String>()
        try {
            val cleanJson = historyJson.trim('[', ']')
            if (cleanJson.isNotEmpty()) {
                cleanJson.split(",").forEach { emoji ->
                    val cleanEmoji = emoji.trim().trim('"')
                    if (cleanEmoji.isNotEmpty()) {
                        history.add(cleanEmoji)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error parsing emoji history", e)
        }
        
        return mapOf(
            "skinTone" to skinTone,
            "historyMaxSize" to historyMaxSize,
            "history" to history
        )
    }
    
    private fun updateEmojiSettings(skinTone: String, historyMaxSize: Int) {
        val prefs = getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("preferred_skin_tone", skinTone)
            .putInt("emoji_history_max_size", historyMaxSize)
            .apply()
        
        LogUtil.d("MainActivity", "Emoji settings updated: skinTone=$skinTone, historyMaxSize=$historyMaxSize")
        
        // Notify keyboard service to reload emoji settings
        notifyKeyboardServiceEmojiChanged()
    }
    
    private fun notifyKeyboardServiceEmojiChanged() {
        try {
            val intent = Intent("com.kvive.keyboard.EMOJI_SETTINGS_CHANGED").apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            LogUtil.d("MainActivity", "Emoji settings broadcast sent")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Failed to send emoji settings broadcast", e)
        }
    }
    
    private fun getEmojiConfig(): Map<String, Any> {
        val prefs = getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val skinTone = prefs.getString("preferred_skin_tone", "🏽") ?: "🏽"
        val historyJson = prefs.getString("emoji_history", "[]") ?: "[]"
        
        val recent = mutableListOf<String>()
        try {
            val cleanJson = historyJson.trim('[', ']')
            if (cleanJson.isNotEmpty()) {
                cleanJson.split(",").take(40).forEach { emoji ->
                    val cleanEmoji = emoji.trim().trim('"')
                    if (cleanEmoji.isNotEmpty()) {
                        recent.add(cleanEmoji)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error parsing emoji history", e)
        }
        
        return mapOf(
            "skinTone" to skinTone,
            "recent" to recent
        )
    }
    
    private fun updateEmojiConfig(skinTone: String, recent: List<String>) {
        val prefs = getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val historyJson = recent.joinToString(",") { "\"$it\"" }
        
        prefs.edit()
            .putString("preferred_skin_tone", skinTone)
            .putString("emoji_history", "[$historyJson]")
            .apply()
        
        LogUtil.d("MainActivity", "Emoji config updated: skinTone=$skinTone, recent=${recent.size}")
        notifyKeyboardServiceEmojiChanged()
    }

    private fun clearUserLearnedWords() {
        try {
            // Clear the local file
            val userWordsFile = java.io.File(filesDir, "user_words.json")
            if (userWordsFile.exists()) {
                userWordsFile.delete()
                LogUtil.d("MainActivity", "Local user words file deleted")
            }
            
            // Send broadcast to keyboard service to clear words
            val intent = Intent("com.kvive.keyboard.CLEAR_USER_WORDS").apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            
            LogUtil.d("MainActivity", "✅ Clear learned words request sent to keyboard service")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error clearing learned words", e)
            throw e
        }
    }

    private fun setEnabledLanguages(languages: List<String>, current: String) {
        try {
            // Store in FlutterSharedPreferences format
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            
            // Store enabled languages as comma-separated string
            prefs.edit()
                .putString("flutter.enabled_languages", languages.joinToString(","))
                .putString("flutter.current_language", current)
                .apply()
            
            LogUtil.d("MainActivity", "✅ Enabled languages saved: $languages")
            
            // Send broadcast to keyboard service
            sendLanguageChangeBroadcast(current)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error setting enabled languages", e)
        }
    }
    
    private fun setCurrentLanguage(language: String) {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("flutter.current_language", language)
                .apply()
            
            LogUtil.d("MainActivity", "✅ Current language set to: $language")
            
            // Send broadcast to keyboard service
            sendLanguageChangeBroadcast(language)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error setting current language", e)
        }
    }
    
    private fun setMultilingualMode(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("flutter.multilingual_enabled", enabled)
                .apply()
            
            LogUtil.d("MainActivity", "✅ Multilingual mode set to: $enabled")
            
            // Send broadcast to keyboard service
            val intent = Intent("com.kvive.keyboard.LANGUAGE_CHANGED").apply {
                setPackage(packageName)
                putExtra("multilingual_enabled", enabled)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error setting multilingual mode", e)
        }
    }
    
    private fun sendLanguageChangeBroadcast(language: String) {
        try {
            val intent = Intent("com.kvive.keyboard.LANGUAGE_CHANGED").apply {
                setPackage(packageName)
                putExtra("language", language)
            }
            sendBroadcast(intent)
            LogUtil.d("MainActivity", "✅ Language change broadcast sent: $language")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Failed to send language change broadcast", e)
        }
    }
    
    // Phase 2: Transliteration toggles
    private fun setTransliterationEnabled(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("flutter.transliteration_enabled", enabled)
                .apply()
            
            LogUtil.d("MainActivity", "✅ Transliteration enabled set to: $enabled")
            
            // Send broadcast to keyboard service
            val intent = Intent("com.kvive.keyboard.LANGUAGE_CHANGED").apply {
                setPackage(packageName)
                putExtra("transliteration_enabled", enabled)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error setting transliteration enabled", e)
        }
    }
    
    private fun setReverseTransliterationEnabled(enabled: Boolean) {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("flutter.reverse_transliteration_enabled", enabled)
                .apply()
            
            LogUtil.d("MainActivity", "✅ Reverse transliteration enabled set to: $enabled")
            
            // Send broadcast to keyboard service
            val intent = Intent("com.kvive.keyboard.LANGUAGE_CHANGED").apply {
                setPackage(packageName)
                putExtra("reverse_transliteration_enabled", enabled)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "❌ Error setting reverse transliteration enabled", e)
        }
    }
    
    /**
     * Download language data from Firebase Storage
     */
    private suspend fun downloadLanguageFromFirebase(lang: String, flutterEngine: FlutterEngine) {
        val storage = Firebase.storage
        val baseDictRef = storage.reference.child("dictionaries/$lang")
        val translitRef = storage.reference.child("transliteration/${lang}_map.json")
        
        val localDictDir = File(filesDir, "cloud_cache/dictionaries/$lang").apply { mkdirs() }
        val localTranslitDir = File(filesDir, "cloud_cache/transliteration").apply { mkdirs() }
        
        val dictFiles = listOf("${lang}_words.txt", "${lang}_bigrams.txt", "${lang}_trigrams.txt", "${lang}_corrections.txt", "${lang}_quadgrams.txt")
        val totalFiles = dictFiles.size + 1 // +1 for transliteration
        var completedFiles = 0
        
        // Progress reporting helper
        suspend fun reportProgress(progress: Int, status: String) {
            withContext(Dispatchers.Main) {
                MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LANGUAGE_CHANNEL)
                    .invokeMethod("languageDownloadProgress", mapOf(
                        "lang" to lang,
                        "progress" to progress,
                        "status" to status
                    ))
            }
        }
        
        LogUtil.d("MainActivity", "📥 Downloading dictionary files for $lang...")
        reportProgress(0, "starting")
        
        // Download dictionary files
        for (fileName in dictFiles) {
            val localFile = File(localDictDir, fileName)
            if (!localFile.exists()) {
                try {
                    baseDictRef.child(fileName).getFile(localFile).await()
                    LogUtil.d("MainActivity", "✅ Downloaded: $fileName")
                } catch (e: Exception) {
                    LogUtil.w("MainActivity", "⚠️ Failed to download $fileName: ${e.message}")
                    // Create empty file to avoid repeated download attempts
                    localFile.createNewFile()
                }
            }
            completedFiles++
            val progress = (completedFiles * 100) / totalFiles
            reportProgress(progress, "downloading")
        }
        
        // Download transliteration map
        val translitFile = File(localTranslitDir, "${lang}_map.json")
        if (!translitFile.exists()) {
            try {
                translitRef.getFile(translitFile).await()
                LogUtil.d("MainActivity", "✅ Downloaded: ${lang}_map.json")
            } catch (e: Exception) {
                LogUtil.w("MainActivity", "⚠️ Failed to download transliteration for $lang: ${e.message}")
                // Create minimal transliteration file
                translitFile.writeText("{\"vowels\":{},\"consonants\":{},\"matras\":{},\"special\":{}}")
            }
        }
        completedFiles++
        
        reportProgress(100, "completed")
        LogUtil.i("MainActivity", "🎉 Language download completed for $lang")
    }
    
    /**
     * Delete cached language data
     */
    private suspend fun deleteCachedLanguageData(lang: String) {
        val dictDir = File(filesDir, "cloud_cache/dictionaries/$lang")
        val translitFile = File(filesDir, "cloud_cache/transliteration/${lang}_map.json")
        
        // Delete dictionary directory
        if (dictDir.exists()) {
            dictDir.deleteRecursively()
            LogUtil.d("MainActivity", "🗑️ Deleted dictionary cache for $lang")
        }
        
        // Delete transliteration file
        if (translitFile.exists()) {
            translitFile.delete()
            LogUtil.d("MainActivity", "🗑️ Deleted transliteration cache for $lang")
        }
    }
    
    /**
     * Setup unified AI channel for Flutter communication
     */
    private fun setupUnifiedAIChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AI_CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    try {
                        when (call.method) {
                            "processAIText" -> {
                                val text = call.argument<String>("text") ?: ""
                                val mode = call.argument<String>("mode") ?: "GRAMMAR"
                                val toneName = call.argument<String>("tone")
                                val featureName = call.argument<String>("feature")
                                val stream = call.argument<Boolean>("stream") ?: false
                                val customPrompt = call.argument<String>("customPrompt")
                                
                                if (text.isEmpty()) {
                                    result.error("INVALID_ARGS", "Text cannot be empty", null)
                                    return@launch
                                }
                                
                                // Convert string arguments to enum types
                                val aiMode = try {
                                    UnifiedAIService.Mode.valueOf(mode)
                                } catch (e: Exception) {
                                    result.error("INVALID_MODE", "Invalid mode: $mode", null)
                                    return@launch
                                }
                                
                                val tone = toneName?.let { 
                                    try {
                                        AdvancedAIService.ToneType.valueOf(it)
                                    } catch (e: Exception) {
                                        result.error("INVALID_TONE", "Invalid tone: $it", null)
                                        return@launch
                                    }
                                }
                                
                                val feature = featureName?.let { 
                                    try {
                                        AdvancedAIService.ProcessingFeature.valueOf(it)
                                    } catch (e: Exception) {
                                        result.error("INVALID_FEATURE", "Invalid feature: $it", null)
                                        return@launch
                                    }
                                }
                                
                                if (stream) {
                                    // For streaming, we need to handle it differently
                                    // Flutter doesn't support streaming method channel responses directly
                                    // So we'll collect all results and return the final one
                                    val results = mutableListOf<UnifiedAIService.UnifiedResult>()
                                    unifiedAIService.processText(
                                        text = text,
                                        mode = aiMode,
                                        tone = tone,
                                        feature = feature,
                                        stream = stream,
                                        customPrompt = customPrompt
                                    )
                                        .collect { res ->
                                            results.add(res)
                                            if (res.isComplete) {
                                                withContext(Dispatchers.Main) {
                                                    result.success(mapOf(
                                                        "success" to res.success,
                                                        "text" to res.text,
                                                        "error" to res.error,
                                                        "fromCache" to res.fromCache,
                                                        "time" to res.time,
                                                        "isComplete" to res.isComplete
                                                    ))
                                                }
                                            }
                                        }
                                } else {
                                    // Non-streaming response
                                    unifiedAIService.processText(
                                        text = text,
                                        mode = aiMode,
                                        tone = tone,
                                        feature = feature,
                                        stream = stream,
                                        customPrompt = customPrompt
                                    )
                                        .collect { res ->
                                            withContext(Dispatchers.Main) {
                                                result.success(mapOf(
                                                    "success" to res.success,
                                                    "text" to res.text,
                                                    "error" to res.error,
                                                    "fromCache" to res.fromCache,
                                                    "time" to res.time,
                                                    "isComplete" to res.isComplete
                                                ))
                                            }
                                        }
                                }
                            }
                            
                            "generateSmartReplies" -> {
                                val message = call.argument<String>("message") ?: ""
                                val context = call.argument<String>("context") ?: "general"
                                val count = call.argument<Int>("count") ?: 3
                                val stream = call.argument<Boolean>("stream") ?: false
                                
                                if (message.isEmpty()) {
                                    result.error("INVALID_ARGS", "Message cannot be empty", null)
                                    return@launch
                                }
                                
                                unifiedAIService.generateSmartReplies(message, context, count, stream)
                                    .collect { res ->
                                        if (res.isComplete) {
                                            withContext(Dispatchers.Main) {
                                                result.success(mapOf(
                                                    "success" to res.success,
                                                    "text" to res.text,
                                                    "error" to res.error,
                                                    "fromCache" to res.fromCache,
                                                    "time" to res.time
                                                ))
                                            }
                                        }
                                    }
                            }
                            
                            "testConnection" -> {
                                val testResult = unifiedAIService.testConnection()
                                withContext(Dispatchers.Main) {
                                    result.success(mapOf(
                                        "success" to testResult.success,
                                        "text" to testResult.text,
                                        "error" to testResult.error,
                                        "time" to testResult.time
                                    ))
                                }
                            }
                            
                            "getAvailableTones" -> {
                                val tones = unifiedAIService.getAvailableTones().map { tone ->
                                    mapOf(
                                        "name" to tone.name,
                                        "displayName" to tone.displayName,
                                        "icon" to tone.icon,
                                        "color" to tone.color
                                    )
                                }
                                result.success(tones)
                            }
                            
                            "getAvailableFeatures" -> {
                                val features = unifiedAIService.getAvailableFeatures().map { feature ->
                                    mapOf(
                                        "name" to feature.name,
                                        "displayName" to feature.displayName,
                                        "icon" to feature.icon
                                    )
                                }
                                result.success(features)
                            }
                            
                            "getServiceStatus" -> {
                                val status = unifiedAIService.getStatus()
                                result.success(status)
                            }
                            
                            "getCacheStats" -> {
                                val stats = unifiedAIService.getCacheStats()
                                result.success(stats)
                            }
                            
                            "clearCache" -> {
                                unifiedAIService.clearCache()
                                result.success(true)
                            }
                            
                            else -> result.notImplemented()
                        }
                    } catch (e: Exception) {
                        LogUtil.e("MainActivity", "Error in AI method channel", e)
                        withContext(Dispatchers.Main) {
                            result.error("AI_ERROR", "AI processing error: ${e.message}", e.stackTraceToString())
                        }
                    }
                }
            }
    }

    /**
     * Setup dynamic prompt management channel for Flutter communication
     */
    private fun setupPromptChannel(flutterEngine: FlutterEngine) {
        // Initialize PromptManager
        PromptManager.init(this)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PROMPT_CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    try {
                        when (call.method) {
                            "savePrompt", "addPrompt" -> {
                                val category = call.argument<String>("category") ?: "assistant"
                                val title = call.argument<String>("title") ?: "Custom Prompt"
                                val prompt = call.argument<String>("prompt") ?: ""
                                
                                if (prompt.isBlank()) {
                                    result.error("INVALID_ARGS", "Prompt cannot be empty", null)
                                    return@launch
                                }
                                
                                val success = PromptManager.savePrompt(category, title, prompt)
                                
                                if (success) {
                                    // Broadcast update to keyboard service
                                    BroadcastManager.sendToKeyboard(
                                        this@MainActivity, 
                                        "com.kvive.keyboard.PROMPTS_UPDATED"
                                    )
                                    result.success(true)
                                    LogUtil.d(
                                        "MainActivity",
                                        "✅ Prompt saved: $title ($category) via ${call.method}"
                                    )
                                } else {
                                    result.error("SAVE_ERROR", "Failed to save prompt", null)
                                }
                            }
                            
                            "getPrompts" -> {
                                val category = call.argument<String>("category")
                                
                                val prompts = if (category != null) {
                                    PromptManager.getPrompts(category)
                                } else {
                                    PromptManager.getAllPrompts().values.flatten()
                                }
                                
                                val promptMaps = prompts.map { prompt ->
                                    mapOf(
                                        "title" to prompt.title,
                                        "prompt" to prompt.prompt,
                                        "timestamp" to prompt.timestamp
                                    )
                                }
                                
                                result.success(promptMaps)
                                LogUtil.d("MainActivity", "📋 Retrieved ${prompts.size} prompts for category: ${category ?: "all"}")
                            }
                            
                            "deletePrompt", "removePrompt" -> {
                                val category = call.argument<String>("category") ?: "assistant"
                                val title = call.argument<String>("title") ?: ""
                                
                                if (title.isBlank()) {
                                    result.error("INVALID_ARGS", "Title cannot be empty", null)
                                    return@launch
                                }
                                
                                val success = PromptManager.removePrompt(category, title)
                                
                                if (success) {
                                    // Broadcast update to keyboard service
                                    BroadcastManager.sendToKeyboard(
                                        this@MainActivity,
                                        "com.kvive.keyboard.PROMPTS_UPDATED"
                                    )
                                    result.success(true)
                                    LogUtil.d(
                                        "MainActivity",
                                        "🗑️ Prompt deleted: $title ($category) via ${call.method}"
                                    )
                                } else {
                                    result.error("DELETE_ERROR", "Failed to delete prompt", null)
                                }
                            }
                            
                            "getAllPrompts" -> {
                                val allPrompts = PromptManager.getAllPrompts()
                                val resultMap = mutableMapOf<String, List<Map<String, Any>>>()
                                
                                allPrompts.forEach { (category, prompts) ->
                                    resultMap[category] = prompts.map { prompt ->
                                        mapOf(
                                            "title" to prompt.title,
                                            "prompt" to prompt.prompt,
                                            "timestamp" to prompt.timestamp
                                        )
                                    }
                                }
                                
                                result.success(resultMap)
                                LogUtil.d("MainActivity", "📚 Retrieved all prompts: ${PromptManager.getTotalPromptCount()} total")
                            }
                            
                            "clearCategory" -> {
                                val category = call.argument<String>("category") ?: "assistant"
                                val success = PromptManager.clearCategory(category)
                                
                                if (success) {
                                    // Broadcast update to keyboard service
                                    BroadcastManager.sendToKeyboard(
                                        this@MainActivity,
                                        "com.kvive.keyboard.PROMPTS_UPDATED"
                                    )
                                    result.success(true)
                                    LogUtil.d("MainActivity", "🗑️ Category cleared: $category")
                                } else {
                                    result.error("CLEAR_ERROR", "Failed to clear category", null)
                                }
                            }
                            
                            else -> {
                                result.notImplemented()
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e("MainActivity", "❌ Error in prompt channel: ${e.message}", e)
                        result.error("PROMPT_ERROR", "Prompt management error: ${e.message}", e.stackTraceToString())
                    }
                }
            }
    }

    /**
     * Setup clipboard channel for Flutter communication
     */
    private fun setupClipboardChannel(flutterEngine: FlutterEngine) {
        clipboardChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CLIPBOARD_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    try {
                        when (call.method) {
                            "getHistory" -> {
                                val history = withContext(Dispatchers.IO) { getClipboardHistory() }
                                result.success(history)
                            }

                            "togglePin" -> {
                                val itemId = call.argument<String>("id")
                                    ?: call.argument<String>("itemId")
                                    ?: ""
                                if (itemId.isBlank()) {
                                    result.error("INVALID_ARGS", "Item ID cannot be empty", null)
                                    return@launch
                                }

                                withContext(Dispatchers.IO) {
                                    toggleClipboardPin(itemId)
                                    notifyKeyboardServiceClipboardChanged()
                                }
                                result.success(true)
                            }

                            "deleteItem" -> {
                                val itemId = call.argument<String>("id")
                                    ?: call.argument<String>("itemId")
                                    ?: ""
                                if (itemId.isBlank()) {
                                    result.error("INVALID_ARGS", "Item ID cannot be empty", null)
                                    return@launch
                                }

                                withContext(Dispatchers.IO) {
                                    deleteClipboardItem(itemId)
                                    notifyKeyboardServiceClipboardChanged()
                                }
                                result.success(true)
                            }

                            "clearAll" -> {
                                withContext(Dispatchers.IO) {
                                    clearAllClipboardItems()
                                    notifyKeyboardServiceClipboardChanged()
                                }
                                result.success(true)
                            }

                            "syncFromSystem" -> {
                                var operationSuccess = false
                                val delivered = withKeyboardService { service ->
                                    operationSuccess = service.syncClipboardFromSystem()
                                }
                                result.success(delivered && operationSuccess)
                            }

                            "syncToCloud" -> {
                                var operationSuccess = false
                                val delivered = withKeyboardService { service ->
                                    operationSuccess = service.syncClipboardToCloud()
                                }
                                result.success(delivered && operationSuccess)
                            }

                            "syncFromCloud" -> {
                                var operationSuccess = false
                                val delivered = withKeyboardService { service ->
                                    operationSuccess = service.syncClipboardFromCloud()
                                }
                                result.success(delivered && operationSuccess)
                            }

                            "updateSettings" -> {
                                val enabled = call.argument<Boolean>("enabled") ?: true
                                val maxHistorySize = call.argument<Int>("maxHistorySize") ?: 20
                                val autoExpiryEnabled = call.argument<Boolean>("autoExpiryEnabled") ?: true
                                val expiryDurationMinutes = (call.argument<Int>("expiryDurationMinutes") ?: 60).toLong()
                                val templates = call.argument<List<Map<String, Any>>>("templates") ?: emptyList()

                                LogUtil.d("MainActivity", "🔧 Received clipboard settings: enabled=$enabled, maxSize=$maxHistorySize, autoExpiry=$autoExpiryEnabled")

                                withContext(Dispatchers.IO) {
                                    updateClipboardSettings(enabled, maxHistorySize, autoExpiryEnabled, expiryDurationMinutes, templates)
                                    notifyKeyboardServiceClipboardChanged()
                                }
                                LogUtil.d("MainActivity", "✅ Clipboard settings saved and broadcast sent")
                                result.success(true)
                            }

                            "getSettings" -> {
                                val settings = withContext(Dispatchers.IO) { getClipboardSettings() }
                                result.success(settings)
                            }

                            else -> {
                                result.notImplemented()
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e("MainActivity", "❌ Error in clipboard channel: ${e.message}", e)
                        result.error("CLIPBOARD_ERROR", "Clipboard error: ${e.message}", e.stackTraceToString())
                    }
                }
            }
        }
        registerClipboardBroadcastReceiver()
    }

    private fun registerClipboardBroadcastReceiver() {
        if (clipboardBroadcastReceiver != null) return

        clipboardBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    ClipboardBroadcasts.ACTION_CLIPBOARD_HISTORY_UPDATED -> {
                        val count = intent.getIntExtra("count", 0)
                        try {
                            clipboardChannel?.invokeMethod("onHistoryChanged", count)
                        } catch (e: Exception) {
                            LogUtil.e("MainActivity", "Failed to forward clipboard history update", e)
                        }
                    }

                    ClipboardBroadcasts.ACTION_CLIPBOARD_NEW_ITEM -> {
                        val text = intent.getStringExtra("text") ?: return
                        val payload = mapOf(
                            "id" to (intent.getStringExtra("id") ?: ""),
                            "text" to text,
                            "timestamp" to intent.getLongExtra("timestamp", System.currentTimeMillis()),
                            "isOTP" to intent.getBooleanExtra("isOTP", false)
                        )
                        try {
                            clipboardChannel?.invokeMethod("onNewItem", payload)
                        } catch (e: Exception) {
                            LogUtil.e("MainActivity", "Failed to forward new clipboard item", e)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(ClipboardBroadcasts.ACTION_CLIPBOARD_HISTORY_UPDATED)
            addAction(ClipboardBroadcasts.ACTION_CLIPBOARD_NEW_ITEM)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                clipboardBroadcastReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(clipboardBroadcastReceiver, filter)
        }
    }

    private fun setupThemeChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, THEME_CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    when (call.method) {
                        "applyTheme" -> {
                            val payload = call.arguments as? Map<String, Any?>
                            if (payload == null) {
                                result.error("INVALID_ARGS", "Theme payload missing", null)
                                return@launch
                            }
                            val delivered = withKeyboardService { service ->
                                service.applyThemeFromFlutter(payload)
                            }
                            if (!delivered) {
                                notifyKeyboardServiceThemeChanged()
                            }
                            result.success(delivered)
                        }
                        else -> result.notImplemented()
                    }
                }
            }
    }

    private fun setupSoundChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SOUND_CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    when (call.method) {
                        "setSound" -> {
                            val payload = call.arguments as? Map<String, Any?>
                            if (payload == null) {
                                result.error("INVALID_ARGS", "Sound payload missing", null)
                                return@launch
                            }
                            val delivered = withKeyboardService { service ->
                                service.applySoundSelection(
                                    payload["name"]?.toString(),
                                    payload["asset"] as? String
                                )
                            }
                            result.success(delivered)
                        }
                        "setKeyboardSound" -> {
                            val file = call.argument<String>("file")
                            if (file != null) {
                                val delivered = withKeyboardService { service ->
                                    service.setKeyboardSound(file)
                                }
                                result.success(delivered)
                            } else {
                                result.error("NO_FILE", "Missing sound file", null)
                            }
                        }
                        "playSample" -> {
                            val asset = when (val arg = call.arguments) {
                                is String -> arg
                                is Map<*, *> -> arg["asset"] as? String
                                else -> null
                            }
                            val delivered = withKeyboardService { service ->
                                service.playSoundSample(asset)
                            }
                            result.success(delivered)
                        }
                        else -> result.notImplemented()
                    }
                }
            }
    }

    private fun setupEffectsChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, EFFECTS_CHANNEL)
            .setMethodCallHandler { call, result ->
                coroutineScope.launch {
                    when (call.method) {
                        "applyEffect" -> {
                            val payload = call.arguments as? Map<String, Any?>
                            val effectType = payload?.get("type")?.toString() ?: "ripple"
                            val opacity = (payload?.get("opacity") as? Number)?.toFloat() ?: 1f
                            val delivered = withKeyboardService { service ->
                                service.previewTapEffect(effectType, opacity)
                            }
                            result.success(delivered)
                        }
                        else -> result.notImplemented()
                    }
                }
            }
    }

    /**
     * Get clipboard history from SharedPreferences
     */
    private fun getClipboardHistory(): List<Map<String, Any>> {
        val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("clipboard_items", null) ?: return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(historyJson)
            val items = mutableListOf<Map<String, Any>>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                items.add(mapOf(
                    "id" to (jsonItem.optString("id", "")),
                    "text" to (jsonItem.optString("text", "")),
                    "timestamp" to (jsonItem.optLong("timestamp", 0L)),
                    "isPinned" to (jsonItem.optBoolean("isPinned", false)),
                    "isTemplate" to (jsonItem.optBoolean("isTemplate", false)),
                    "category" to (jsonItem.optString("category", ""))
                ))
            }
            
            items
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error parsing clipboard history", e)
            emptyList()
        }
    }

    /**
     * Get clipboard settings from SharedPreferences
     */
    private fun getClipboardSettings(): Map<String, Any> {
        val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        return mapOf(
            "enabled" to prefs.getBoolean("clipboard_enabled", true),
            "maxHistorySize" to prefs.getInt("max_history_size", 20),
            "autoExpiryEnabled" to prefs.getBoolean("auto_expiry_enabled", true),
            "expiryDurationMinutes" to prefs.getLong("expiry_duration_minutes", 60L)
        )
    }

    /**
     * Toggle pin status of a clipboard item
     */
    private fun toggleClipboardPin(itemId: String) {
        val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("clipboard_items", null) ?: return
        
        try {
            val jsonArray = org.json.JSONArray(historyJson)
            val newArray = org.json.JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                if (jsonItem.optString("id", "") == itemId) {
                    val currentPinned = jsonItem.optBoolean("isPinned", false)
                    jsonItem.put("isPinned", !currentPinned)
                }
                newArray.put(jsonItem)
            }
            
            prefs.edit()
                .putString("clipboard_items", newArray.toString())
                .apply()
                
            LogUtil.d("MainActivity", "Toggled pin for item: $itemId")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error toggling pin", e)
        }
    }

    /**
     * Delete a clipboard item
     */
    private fun deleteClipboardItem(itemId: String) {
        val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("clipboard_items", null) ?: return
        
        try {
            val jsonArray = org.json.JSONArray(historyJson)
            val newArray = org.json.JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                if (jsonItem.optString("id", "") != itemId) {
                    newArray.put(jsonItem)
                }
            }
            
            prefs.edit()
                .putString("clipboard_items", newArray.toString())
                .apply()
                
            LogUtil.d("MainActivity", "Deleted clipboard item: $itemId")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error deleting item", e)
        }
    }

    /**
     * Clear all non-pinned clipboard items
     */
    private fun clearAllClipboardItems() {
        val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("clipboard_items", null) ?: return
        
        try {
            val jsonArray = org.json.JSONArray(historyJson)
            val newArray = org.json.JSONArray()
            
            // Keep only pinned items
            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                if (jsonItem.optBoolean("isPinned", false) || jsonItem.optBoolean("isTemplate", false)) {
                    newArray.put(jsonItem)
                }
            }
            
            prefs.edit()
                .putString("clipboard_items", newArray.toString())
                .apply()
                
            LogUtil.d("MainActivity", "Cleared all non-pinned clipboard items")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "Error clearing clipboard", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        ioScope.cancel()
        clipboardBroadcastReceiver?.let {
            runCatching { unregisterReceiver(it) }
            clipboardBroadcastReceiver = null
        }
        clipboardChannel = null
    }
}
