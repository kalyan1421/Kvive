package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Singleton repository for all application settings.
 * Replaces scattered SharedPreferences usage in Service, Managers, and Controllers.
 */
object AppConfig {
    private const val PREFS_FLUTTER = "FlutterSharedPreferences"
    private const val PREFS_NATIVE = "ai_keyboard_settings"

    private lateinit var flutterPrefs: SharedPreferences
    private lateinit var nativePrefs: SharedPreferences

    var isInitialized = false
        private set

    fun init(context: Context) {
        if (isInitialized) return
        flutterPrefs = context.getSharedPreferences(PREFS_FLUTTER, Context.MODE_PRIVATE)
        nativePrefs = context.getSharedPreferences(PREFS_NATIVE, Context.MODE_PRIVATE)
        isInitialized = true
    }

    // --- Core Settings ---
    val isSoundEnabled: Boolean
        get() = flutterPrefs.getBoolean("flutter.sound_enabled", false)

    val isVibrationEnabled: Boolean
        get() = flutterPrefs.getBoolean("flutter.vibration_enabled", false)

    val showNumberRow: Boolean
        get() = nativePrefs.getBoolean("show_number_row", false) || 
                flutterPrefs.getBoolean("flutter.keyboard.numberRow", false)

    val swipeTypingEnabled: Boolean
        get() = nativePrefs.getBoolean("swipe_typing", true)

    val aiSuggestionsEnabled: Boolean
        get() = nativePrefs.getBoolean("ai_suggestions", true)

    // --- AI & Features ---
    val openAIApiKey: String?
        get() = nativePrefs.getString("openai_api_key", null)

    // --- Helper for bulk loading (Matches old SettingsManager logic) ---
    data class UnifiedSettings(
        val vibrationEnabled: Boolean,
        val soundEnabled: Boolean,
        val keyPreviewEnabled: Boolean,
        val showNumberRow: Boolean,
        val swipeTypingEnabled: Boolean,
        val aiSuggestionsEnabled: Boolean,
        val currentLanguage: String,
        val enabledLanguages: List<String>,
        val autocorrectEnabled: Boolean,
        val autoCapitalization: Boolean,
        val autoFillSuggestion: Boolean,
        val rememberCapsState: Boolean,
        val doubleSpacePeriod: Boolean,
        val popupEnabled: Boolean,
        val soundType: String,
        val effectType: String,
        val soundVolume: Float,
        val soundCustomUri: String?
    )

    fun loadAll(): UnifiedSettings {
        // Core Toggles
        val sound = flutterPrefs.getBoolean("flutter.sound_enabled", false)
        val vibration = flutterPrefs.getBoolean("flutter.vibration_enabled", false)
        
        // Native Prefs Fallbacks
        val showNumberRow = nativePrefs.getBoolean("show_number_row", false)
        val swipeTyping = nativePrefs.getBoolean("swipe_typing", true)
        val aiSuggestions = nativePrefs.getBoolean("ai_suggestions", true)
        val autocorrect = nativePrefs.getBoolean("auto_correct", true)
        
        // Advanced Flutter Settings
        val autoCap = flutterPrefs.getBoolean("flutter.auto_capitalization", true)
        val autoFill = flutterPrefs.getBoolean("flutter.auto_fill_suggestion", true)
        val rememberCaps = flutterPrefs.getBoolean("flutter.remember_caps_state", false)
        val doubleSpace = flutterPrefs.getBoolean("flutter.double_space_period", true)
        val popup = nativePrefs.getBoolean("popup_enabled", false)
        
        // Sound & Visuals
        val soundType = flutterPrefs.getString("flutter.sound.type", "default") ?: "default"
        val effectType = flutterPrefs.getString("flutter.effect.type", "none") ?: "none"
        val soundVolumePercent = flutterPrefs.getInt("flutter.sound_volume", 50)
        val soundVolume = (soundVolumePercent / 100.0f).coerceIn(0f, 1f)
        val soundCustomUri = nativePrefs.getString("sound_custom_uri", null)

        // Languages
        val currentLang = flutterPrefs.getString("flutter.current_language", "en") ?: "en"
        val enabledLangsStr = flutterPrefs.getString("flutter.enabled_languages", "en") ?: "en"
        val enabledLangs = try {
            enabledLangsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            listOf("en")
        }

        return UnifiedSettings(
            vibrationEnabled = vibration,
            soundEnabled = sound,
            keyPreviewEnabled = false, // DISABLED per requirements
            showNumberRow = showNumberRow,
            swipeTypingEnabled = swipeTyping,
            aiSuggestionsEnabled = aiSuggestions,
            currentLanguage = currentLang,
            enabledLanguages = enabledLangs,
            autocorrectEnabled = autocorrect,
            autoCapitalization = autoCap,
            autoFillSuggestion = autoFill,
            rememberCapsState = rememberCaps,
            doubleSpacePeriod = doubleSpace,
            popupEnabled = popup,
            soundType = soundType,
            effectType = effectType,
            soundVolume = soundVolume,
            soundCustomUri = soundCustomUri
        )
    }
}
