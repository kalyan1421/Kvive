package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.managers.BaseManager
import kotlinx.coroutines.*
import io.flutter.plugin.common.MethodChannel
import android.util.Log

/**
 * Manages language switching, preferences, and app-specific language settings
 */
class LanguageManager(context: Context) : BaseManager(context) {
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // MethodChannel for progress callbacks to Flutter
    private var methodChannel: MethodChannel? = null
    companion object {
        private const val KEY_CURRENT_LANGUAGE = "current_language"
        private const val KEY_ENABLED_LANGUAGES = "enabled_languages"
        private const val KEY_AUTO_SWITCH = "auto_switch"
        private const val KEY_TAP_BEHAVIOR = "tap_behavior"
        private const val KEY_APP_PREFIX = "app_"
    }
    
    override fun getPreferencesName() = "keyboard_language_prefs"
    private var currentLanguage = "en"
    private var enabledLanguages = mutableSetOf("en")
    private val languageChangeListeners = mutableListOf<LanguageChangeListener>()
    
    interface LanguageChangeListener {
        fun onLanguageChanged(oldLanguage: String, newLanguage: String)
        fun onEnabledLanguagesChanged(enabledLanguages: Set<String>)
    }
    
    enum class TapBehavior {
        CYCLE,      // Tap to cycle through languages (default)
        POPUP       // Tap to show language selection popup
    }
    
    init {
        loadPreferences()
        // Default fallback: only English
        if (enabledLanguages.isEmpty()) {
            enabledLanguages.add("en")
            saveEnabledLanguages()
            logW("✅ Default language set: English")
        }
        
        logW("LanguageManager initialized")
        logW("Current language: $currentLanguage")
        logW("Enabled languages: $enabledLanguages")
        
        // Auto-preload English on first launch (offline assets)
        scope.launch {
            try {
                val multilingualDict = MultilingualDictionaryImpl(context)
                multilingualDict.preload("en")
                logW("🌐 Auto-preload English completed")
            } catch (e: Exception) {
                logE("❌ Failed to auto-preload English", e)
            }
        }
    }
    
    /**
     * Load language preferences from SharedPreferences
     */
    private fun loadPreferences() {
        currentLanguage = prefs.getString(KEY_CURRENT_LANGUAGE, "en") ?: "en"
        enabledLanguages = prefs.getStringSet(KEY_ENABLED_LANGUAGES, setOf("en"))?.toMutableSet() ?: mutableSetOf("en")
        
        // Ensure current language is in enabled languages
        if (!enabledLanguages.contains(currentLanguage)) {
            enabledLanguages.add(currentLanguage)
            saveEnabledLanguages()
        }
        
        logW("Loaded preferences - Current: $currentLanguage, Enabled: $enabledLanguages")
    }
    
    /**
     * Switch to the next enabled language
     */
    fun switchToNextLanguage() {
        val enabledList = enabledLanguages.toList().sorted()
        if (enabledList.size <= 1) {
            logW("Only one language enabled, no switching needed")
            return
        }
        
        val currentIndex = enabledList.indexOf(currentLanguage)
        val nextIndex = (currentIndex + 1) % enabledList.size
        val nextLanguage = enabledList[nextIndex]
        
        switchToLanguage(nextLanguage)
    }
    
    /**
     * Switch to a specific language
     */
    fun switchToLanguage(languageCode: String) {
        if (!isLanguageSupported(languageCode)) {
            logW("Language $languageCode is not supported")
            return
        }
        
        if (!enabledLanguages.contains(languageCode)) {
            logW("Language $languageCode is not enabled")
            return
        }
        
        if (currentLanguage == languageCode) {
            logW("Already using language $languageCode")
            return
        }
        
        val oldLanguage = currentLanguage
        currentLanguage = languageCode
        saveCurrentLanguage()
        
        logW("Switched language from $oldLanguage to $currentLanguage")
        
        // Notify listeners
        languageChangeListeners.forEach { listener ->
            try {
                listener.onLanguageChanged(oldLanguage, currentLanguage)
            } catch (e: Exception) {
                logE( "Error notifying language change listener", e)
            }
        }
    }
    
    /**
     * Get current language code
     */
    fun getCurrentLanguage(): String = currentLanguage
    
    /**
     * Get current language configuration
     */
    fun getCurrentLanguageConfig(): LanguageConfig? {
        return LanguageConfigs.getLanguageConfig(currentLanguage)
    }
    
    /**
     * Get all enabled languages
     */
    fun getEnabledLanguages(): Set<String> = enabledLanguages.toSet()
    
    /**
     * Get enabled language configurations
     */
    fun getEnabledLanguageConfigs(): List<LanguageConfig> {
        return LanguageConfigs.getEnabledLanguages(enabledLanguages)
    }
    
    /**
     * Set enabled languages
     */
    fun setEnabledLanguages(languages: Set<String>) {
        val validLanguages = languages.filter { isLanguageSupported(it) }.toMutableSet()
        
        // Ensure at least English is enabled
        if (validLanguages.isEmpty()) {
            validLanguages.add("en")
        }
        
        enabledLanguages = validLanguages
        saveEnabledLanguages()
        
        // Switch to a valid language if current is no longer enabled
        if (!enabledLanguages.contains(currentLanguage)) {
            switchToLanguage(enabledLanguages.first())
        }
        
        logW("Updated enabled languages: $enabledLanguages")
        
        // Notify listeners
        languageChangeListeners.forEach { listener ->
            try {
                listener.onEnabledLanguagesChanged(enabledLanguages)
            } catch (e: Exception) {
                logE( "Error notifying enabled languages change listener", e)
            }
        }
    }
    
    /**
     * Set MethodChannel for progress callbacks to Flutter
     */
    fun setMethodChannel(channel: MethodChannel) {
        this.methodChannel = channel
    }
    
    /**
     * Add a language to enabled languages with offline preload support
     * Loads dictionary + transliteration before adding to enabled list
     */
    fun enableLanguage(languageCode: String, callback: ((success: Boolean, error: String?) -> Unit)? = null) {
        if (!isLanguageSupported(languageCode)) {
            val error = "⚠️ Language $languageCode not supported"
            logW(error)
            callback?.invoke(false, error)
            return
        }
        
        if (enabledLanguages.contains(languageCode)) {
            logW("Language $languageCode already enabled")
            callback?.invoke(true, null)
            return
        }
        
        // Preload language data offline before enabling
        scope.launch {
            try {
                logW("📦 Preloading offline data for $languageCode")
                
                // Notify Flutter of download start
                methodChannel?.invokeMethod("languageDownloadProgress", mapOf(
                    "lang" to languageCode,
                    "progress" to 0,
                    "status" to "starting"
                ))
                
                // Load dictionary data
                val multilingualDict = MultilingualDictionaryImpl(context)
                multilingualDict.preload(languageCode)
                
                // Preload the language into memory after successful download
                logW("✅ Language data preloaded successfully: $languageCode")
                
                // Update progress
                methodChannel?.invokeMethod("languageDownloadProgress", mapOf(
                    "lang" to languageCode,
                    "progress" to 50,
                    "status" to "loading_transliteration"
                ))
                
                // Load transliteration if supported (offline)
                val supportedTranslitLangs = setOf("hi", "te", "ta")
                if (supportedTranslitLangs.contains(languageCode)) {
                    val translitEngine = TransliterationEngine(context, languageCode)
                    translitEngine.ensureMapAvailable(languageCode)
                }
                
                // Complete - add to enabled languages
                withContext(Dispatchers.Main) {
                    enabledLanguages.add(languageCode)
                    saveEnabledLanguages()
                    
                    logW("✅ Download complete – preloading language: $languageCode")
                    
                    // Notify Flutter of completion
                    methodChannel?.invokeMethod("languageDownloadProgress", mapOf(
                        "lang" to languageCode,
                        "progress" to 100,
                        "status" to "completed"
                    ))
                    
                    // Notify listeners
                    languageChangeListeners.forEach { listener ->
                        try {
                            listener.onEnabledLanguagesChanged(enabledLanguages)
                        } catch (e: Exception) {
                            logE("Error notifying enabled languages change listener", e)
                        }
                    }
                    
                    callback?.invoke(true, null)
                }
                
            } catch (e: Exception) {
                val error = "❌ Failed to download language $languageCode: ${e.message}"
                logE(error, e)
                logW("⚠️ Offline mode – will preload later when online")
                
                // Handle offline gracefully - add without cloud data
                withContext(Dispatchers.Main) {
                    logW("⚠️ Adding $languageCode offline (will download when online)")
                    enabledLanguages.add(languageCode)
                    saveEnabledLanguages()
                    
                    // Notify Flutter of offline enablement
                    methodChannel?.invokeMethod("languageDownloadProgress", mapOf(
                        "lang" to languageCode,
                        "progress" to 100,
                        "status" to "offline_enabled",
                        "error" to e.message
                    ))
                    
                    // Notify listeners
                    languageChangeListeners.forEach { listener ->
                        try {
                            listener.onEnabledLanguagesChanged(enabledLanguages)
                        } catch (e2: Exception) {
                            logE("Error notifying enabled languages change listener", e2)
                        }
                    }
                    
                    callback?.invoke(false, error)
                }
            }
        }
    }
    
    /**
     * Internal helper to add and load a language (legacy - for backwards compatibility)
     */
    private fun addAndLoadLanguage(code: String) {
        if (!enabledLanguages.contains(code)) {
            enabledLanguages.add(code)
            saveEnabledLanguages()
            logW("✅ Enabled language: $code")
            
            // Dictionary will be loaded lazily when language is activated
            // No need to preload immediately - on-demand loading is more efficient
        }
    }
    
    /**
     * Check if cloud language data exists for a language
     */
    suspend fun checkCloudLanguageAvailable(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val multilingualDict = MultilingualDictionaryImpl(context)
            multilingualDict.preload(languageCode)
            true
        } catch (e: Exception) {
            logW("Language data not available for $languageCode: ${e.message}")
            false
        }
    }
    
    /**
     * Queue download for later when offline
     */
    fun queueLanguageForDownload(languageCode: String) {
        val prefs = getSharedPreferences("language_download_queue", Context.MODE_PRIVATE)
        val queuedLangs = prefs.getStringSet("queued_languages", emptySet())?.toMutableSet() ?: mutableSetOf()
        queuedLangs.add(languageCode)
        prefs.edit().putStringSet("queued_languages", queuedLangs).apply()
        
        logW("Queued $languageCode for download when online")
    }
    
    /**
     * Process queued downloads when network becomes available
     */
    fun processQueuedDownloads() {
        val prefs = getSharedPreferences("language_download_queue", Context.MODE_PRIVATE)
        val queuedLangs = prefs.getStringSet("queued_languages", emptySet()) ?: emptySet()
        
        if (queuedLangs.isNotEmpty()) {
            logW("🗒 Processing ${queuedLangs.size} queued language downloads")
            
            queuedLangs.forEach { lang ->
                enableLanguage(lang) { success, error ->
                    if (success) {
                        logW("✅ Completed queued download for $lang")
                    } else {
                        logW("❌ Failed queued download for $lang: $error")
                    }
                }
            }
            
            // Clear the queue after processing
            prefs.edit().remove("queued_languages").apply()
        }
    }
    
    /**
     * Remove a language from enabled languages
     */
    fun disableLanguage(languageCode: String) {
        if (enabledLanguages.size > 1 && enabledLanguages.contains(languageCode)) {
            enabledLanguages.remove(languageCode)
            saveEnabledLanguages()
            
            // Switch to another language if current was disabled
            if (currentLanguage == languageCode) {
                switchToLanguage(enabledLanguages.first())
            }
            
            logW("Disabled language: $languageCode")
        }
    }
    
    /**
     * Check if a language is supported
     */
    fun isLanguageSupported(languageCode: String): Boolean {
        return LanguageConfigs.SUPPORTED_LANGUAGES.containsKey(languageCode)
    }
    
    /**
     * Check if a language is enabled
     */
    fun isLanguageEnabled(languageCode: String): Boolean {
        return enabledLanguages.contains(languageCode)
    }
    
    
    /**
     * Set auto-switch enabled
     */
    fun setAutoSwitchEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_SWITCH, enabled)
            .apply()
        logW("Auto-switch enabled: $enabled")
    }
    
    /**
     * Check if auto-switch is enabled
     */
    fun isAutoSwitchEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SWITCH, true)
    }
    
    /**
     * Set tap behavior for language switch button
     */
    fun setTapBehavior(behavior: TapBehavior) {
        prefs.edit()
            .putString(KEY_TAP_BEHAVIOR, behavior.name)
            .apply()
        logW("Tap behavior set to: $behavior")
    }
    
    /**
     * Get current tap behavior
     */
    fun getTapBehavior(): TapBehavior {
        val behaviorName = prefs.getString(KEY_TAP_BEHAVIOR, TapBehavior.CYCLE.name)
        return try {
            TapBehavior.valueOf(behaviorName ?: TapBehavior.CYCLE.name)
        } catch (e: IllegalArgumentException) {
            logW("Invalid tap behavior: $behaviorName, using default")
            TapBehavior.CYCLE
        }
    }
    
    /**
     * Toggle between CYCLE and POPUP tap behaviors
     */
    fun toggleTapBehavior() {
        val currentBehavior = getTapBehavior()
        val newBehavior = when (currentBehavior) {
            TapBehavior.CYCLE -> TapBehavior.POPUP
            TapBehavior.POPUP -> TapBehavior.CYCLE
        }
        setTapBehavior(newBehavior)
        logW("Toggled tap behavior from $currentBehavior to $newBehavior")
    }
    
    /**
     * Get language display name
     */
    fun getLanguageDisplayName(languageCode: String): String {
        val config = LanguageConfigs.getLanguageConfig(languageCode)
        return if (config != null) {
            "${config.flag} ${config.nativeName}"
        } else {
            languageCode.uppercase()
        }
    }
    
    /**
     * Get available layout types for enabled languages
     */
    fun getAvailableLayoutTypes(): Set<LayoutType> {
        return getEnabledLanguageConfigs().map { it.layoutType }.toSet()
    }
    
    /**
     * Add language change listener
     */
    fun addLanguageChangeListener(listener: LanguageChangeListener) {
        if (!languageChangeListeners.contains(listener)) {
            languageChangeListeners.add(listener)
            logW("Added language change listener")
        }
    }
    
    /**
     * Remove language change listener
     */
    fun removeLanguageChangeListener(listener: LanguageChangeListener) {
        languageChangeListeners.remove(listener)
        logW("Removed language change listener")
    }
    
    /**
     * Save current language to preferences
     */
    private fun saveCurrentLanguage() {
        prefs.edit()
            .putString(KEY_CURRENT_LANGUAGE, currentLanguage)
            .apply()
    }
    
    /**
     * Save enabled languages to preferences
     */
    private fun saveEnabledLanguages() {
        prefs.edit()
            .putStringSet(KEY_ENABLED_LANGUAGES, enabledLanguages)
            .apply()
    }
    
    /**
     * Get languages that can be suggested based on recent usage
     */
    fun getSuggestedLanguages(): List<String> {
        // This could be enhanced with usage analytics
        val allSupported = LanguageConfigs.SUPPORTED_LANGUAGES.keys
        val notEnabled = allSupported.filter { !enabledLanguages.contains(it) }
        return notEnabled.take(3) // Suggest top 3 non-enabled languages
    }
    
    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        currentLanguage = "en"
        enabledLanguages = mutableSetOf("en")
        prefs.edit().clear().apply()
        saveCurrentLanguage()
        saveEnabledLanguages()
        logW("Reset to default language settings")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
    
    /**
     * Get a SharedPreferences instance with a custom name
     */
    private fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences(name, mode)
}
