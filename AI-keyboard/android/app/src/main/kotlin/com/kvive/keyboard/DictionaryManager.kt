package com.kvive.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.kvive.keyboard.managers.BaseManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages user dictionary entries (shortcuts → expansions) with frequency tracking
 * Similar to Gboard and CleverType personal dictionary features
 * 
 * Phase 1: Multi-Language Support
 * - Stores dictionaries per language in /files/dictionaries/{lang}.json
 * - Reduces memory usage with on-demand loading
 * - Backward compatible with old prefs-based storage
 * 
 * ⚡ PERFORMANCE: Uses debounced saving to prevent O(N) operations on every keystroke
 */
class DictionaryManager(context: Context) : BaseManager(context) {
    
    companion object {
        private const val KEY_ENTRIES = "dictionary_entries"
        private const val KEY_ENABLED = "dictionary_enabled"
        private const val KEY_CURRENT_LANGUAGE = "current_language"
        
        // ⚡ PERFORMANCE: Debounce save operations to reduce I/O during typing
        private const val SAVE_DEBOUNCE_MS = 2000L // Save 2 seconds after last change
    }
    
    // ⚡ PERFORMANCE: Handler for debounced saves
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pendingSave = false
    private val saveRunnable = Runnable {
        if (pendingSave) {
            performSave()
            pendingSave = false
        }
    }
    
    override fun getPreferencesName() = "dictionary_manager"
    
    // Multi-language dictionary directory
    private val languageDir = File(context.filesDir, "dictionaries").apply {
        if (!exists()) mkdirs()
    }
    
    // Current active language
    private var currentLanguage = "en"
    
    // Thread-safe list for concurrent access
    private val entries = CopyOnWriteArrayList<DictionaryEntry>()
    
    // Cache for fast lookup during typing
    private val shortcutMap = mutableMapOf<String, DictionaryEntry>()
    
    private var isEnabled = true
    
    // Listeners for dictionary changes
    private val listeners = mutableListOf<DictionaryListener>()
    
    // Observer for dictionary changes (for LanguageResources refresh)
    private var changeObserver: (() -> Unit)? = null
    
    interface DictionaryListener {
        fun onDictionaryUpdated(entries: List<DictionaryEntry>)
        fun onExpansionTriggered(shortcut: String, expansion: String)
    }
    
    /**
     * Initialize the dictionary manager
     * Phase 1: Loads language-specific dictionary from /files/dictionaries/{lang}.json
     * 🚀 PERFORMANCE: Lazy initialization - loads data on first access
     */
    override fun initialize() {
        logW("Initializing DictionaryManager with multi-language support (lazy mode)")
        
        // ✅ FIX: Load settings from FlutterSharedPreferences (where UI saves them)
        val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        isEnabled = flutterPrefs.getBoolean("flutter.dictionary_enabled", true)
        
        // Detect current language from keyboard settings
        val savedLanguage = prefs.getString(KEY_CURRENT_LANGUAGE, null)
        val flutterLanguage = flutterPrefs.getString("flutter.keyboard_language", null)
        currentLanguage = savedLanguage ?: flutterLanguage ?: "en"
        
        logW("📚 Current language: $currentLanguage (lazy loading enabled)")
        
        // 🚀 DEFER LOADING: Don't load dictionary data until first access
        // This dramatically speeds up startup time
        // Dictionary will be loaded when getExpansion() or other methods are called
        
        logW("DictionaryManager initialized in lazy mode (enabled: $isEnabled)")
    }
    
    /**
     * Ensure dictionary is loaded for current language
     * Called on first access to dictionary data
     */
    private fun ensureLoaded() {
        if (entries.isNotEmpty() || currentLanguage.isEmpty()) {
            return // Already loaded or invalid state
        }
        
        logW("📖 Lazy loading dictionary for $currentLanguage...")
        
        // Load from language-specific JSON file
        val languageFile = File(languageDir, "$currentLanguage.json")
        if (languageFile.exists()) {
            loadLanguage(currentLanguage)
        } else {
            // New language - start with empty dictionary
            logW("ℹ️ No language file found for $currentLanguage. Starting fresh.")
            loadFromFlutterPrefs() // Sync any entries from Flutter UI
        }
        
        // Add default shortcuts if this is first run
        if (entries.isEmpty() && !prefs.getBoolean("defaults_added_$currentLanguage", false)) {
            addDefaultShortcuts()
            prefs.edit().putBoolean("defaults_added_$currentLanguage", true).apply()
            saveLanguage(currentLanguage)
        }
        
        // Rebuild shortcut map with merged entries
        rebuildShortcutMap()
        
        logW("✅ Dictionary loaded: ${entries.size} entries for $currentLanguage")
    }
    
    // ==================== PHASE 1: MULTI-LANGUAGE METHODS ====================
    
    /**
     * Load dictionary for a specific language
     * @param lang Language code (e.g., "en", "hi", "te")
     */
    fun loadLanguage(lang: String) {
        currentLanguage = lang
        val file = File(languageDir, "$lang.json")
        
        if (!file.exists()) {
            logW("⚠️ No dictionary found for $lang. Creating default one.")
            entries.clear()
            rebuildShortcutMap()
            saveLanguage(lang)
            return
        }
        
        try {
            val jsonArray = JSONArray(file.readText())
            entries.clear()
            
            for (i in 0 until jsonArray.length()) {
                val entry = DictionaryEntry.fromJson(jsonArray.getJSONObject(i))
                entries.add(entry)
            }
            
            rebuildShortcutMap()
            prefs.edit().putString(KEY_CURRENT_LANGUAGE, lang).apply()
            logW("✅ Loaded ${entries.size} entries for $lang")
            
        } catch (e: Exception) {
            logE("❌ Failed to load dictionary for $lang", e)
            entries.clear()
            rebuildShortcutMap()
        }
    }
    
    /**
     * Save current dictionary to language-specific file
     * @param lang Language code to save (defaults to current language)
     */
    fun saveLanguage(lang: String = currentLanguage) {
        try {
            val file = File(languageDir, "$lang.json")
            val jsonArray = JSONArray()
            
            entries.forEach { entry ->
                jsonArray.put(entry.toJson())
            }
            
            file.writeText(jsonArray.toString())
            logW("💾 Saved dictionary for $lang with ${entries.size} entries")
            
        } catch (e: Exception) {
            logE("❌ Failed to save dictionary for $lang", e)
        }
    }
    
    /**
     * Switch to a different language dictionary
     * Saves current language before switching
     * @param newLang Target language code
     */
    fun switchLanguage(newLang: String) {
        if (newLang == currentLanguage) {
            logW("Already using language: $newLang")
            return
        }
        
        // ⚡ PERFORMANCE: Force save before switching languages
        forceSaveNow()
        
        // Save current language dictionary
        saveLanguage(currentLanguage)
        
        // Load new language
        loadLanguage(newLang)
        
        // Notify listeners
        notifyDictionaryUpdated()
        
        logW("🌐 Switched from $currentLanguage to $newLang")
    }
    
    /**
     * Get list of all available language dictionaries
     * @return List of language codes with existing dictionaries
     */
    fun listAvailableLanguages(): List<String> {
        return try {
            languageDir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
        } catch (e: Exception) {
            logE("Error listing available languages", e)
            emptyList()
        }
    }
    
    /**
     * Get current active language
     */
    fun getCurrentLanguage(): String = currentLanguage

    /**
     * Export shortcuts as a frequency map for SymSpell/LM merging.
     * Usage count is used as a lightweight frequency signal.
     */
    fun getShortcutFrequencies(): Map<String, Int> {
        return entries.associate { entry ->
            entry.shortcut.lowercase() to entry.usageCount.coerceAtLeast(1)
        }
    }
    
    /**
     * Delete dictionary for a specific language
     * @param lang Language code to delete
     */
    fun deleteLanguage(lang: String): Boolean {
        if (lang == currentLanguage) {
            logW("⚠️ Cannot delete currently active language: $lang")
            return false
        }
        
        try {
            val file = File(languageDir, "$lang.json")
            if (file.exists()) {
                file.delete()
                logW("🗑️ Deleted dictionary for $lang")
                return true
            }
        } catch (e: Exception) {
            logE("Error deleting language $lang", e)
        }
        return false
    }
    
    // ==================== END PHASE 1 ====================
    
    /**
     * Add default shortcuts for common abbreviations
     */
    private fun addDefaultShortcuts() {
        val defaults = mapOf(
            
            "omw" to "on my way",
            "btw" to "by the way",
            "ty" to "thank you",
        
        )
        
        defaults.forEach { (shortcut, expansion) ->
            addEntry(shortcut, expansion, shouldLog = false)
        }
        
        logW("✅ Added ${defaults.size} default shortcuts")
    }
    
    /**
     * Add a new dictionary entry
     * @param shouldLog Whether to log the operation (false when adding bulk defaults)
     */
    fun addEntry(shortcut: String, expansion: String, shouldLog: Boolean = true): Boolean {
        try {
            val cleanShortcut = shortcut.trim().lowercase()
            val cleanExpansion = expansion.trim()
            
            if (cleanShortcut.isEmpty() || cleanExpansion.isEmpty()) {
                if (shouldLog) logW("Cannot add empty shortcut or expansion")
                return false
            }
            
            // Check if shortcut already exists
            val existingIndex = entries.indexOfFirst { it.shortcut == cleanShortcut }
            
            if (existingIndex != -1) {
                // Update existing entry
                val existingEntry = entries[existingIndex]
                entries[existingIndex] = existingEntry.copy(expansion = cleanExpansion)
                if (shouldLog) logW("Updated existing entry: $cleanShortcut -> $cleanExpansion")
            } else {
                // Add new entry
                val newEntry = DictionaryEntry(
                    shortcut = cleanShortcut,
                    expansion = cleanExpansion
                )
                entries.add(newEntry)
                if (shouldLog) logW("Added new entry: $cleanShortcut -> $cleanExpansion")
            }
            
            // Save and update cache
            saveEntriesToPrefs()
            rebuildShortcutMap()
            notifyDictionaryUpdated()
            
            return true
            
        } catch (e: Exception) {
            logE( "Error adding dictionary entry", e)
            return false
        }
    }
    
    /**
     * Remove a dictionary entry
     */
    fun removeEntry(id: String): Boolean {
        val removed = entries.removeIf { it.id == id }
        if (removed) {
            saveEntriesToPrefs()
            rebuildShortcutMap()
            notifyDictionaryUpdated()
            logW("Removed dictionary entry: $id")
        }
        return removed
    }
    
    /**
     * Update an existing entry
     */
    fun updateEntry(id: String, newShortcut: String, newExpansion: String): Boolean {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return false
        
        val cleanShortcut = newShortcut.trim().lowercase()
        val cleanExpansion = newExpansion.trim()
        
        if (cleanShortcut.isEmpty() || cleanExpansion.isEmpty()) return false
        
        val entry = entries[index]
        val oldShortcut = entry.shortcut
        
        // Update the entry with new values
        entries[index] = entry.copy(
            shortcut = cleanShortcut,
            expansion = cleanExpansion
        )
        
        // ✅ FIX: Rebuild shortcut map to clear old key and add new one
        rebuildShortcutMap()
        saveEntriesToPrefs()
        notifyDictionaryUpdated()
        
        logW("Updated entry: $oldShortcut -> $cleanShortcut = $cleanExpansion")
        return true
    }
    
    /**
     * Check if a word matches a dictionary shortcut and return expansion
     */
    fun getExpansion(word: String): DictionaryEntry? {
        if (!isEnabled || word.isBlank()) return null
        
        // 🚀 Ensure dictionary is loaded on first access
        ensureLoaded()
        
        val cleanWord = word.trim().lowercase()
        return shortcutMap[cleanWord]
    }
    
    /**
     * Get expansion text directly (convenience method for auto-expansion)
     * Returns the expansion string if found, null otherwise
     */
    fun getExpansionText(word: String): String? {
        if (!isEnabled || word.isBlank()) return null
        return getExpansion(word)?.expansion
    }
    
    /**
     * Check if a prefix matches any shortcuts (for suggestions)
     */
    fun getMatchingShortcuts(prefix: String, limit: Int = 5): List<DictionaryEntry> {
        if (!isEnabled || prefix.isBlank()) return emptyList()
        
        // 🚀 Ensure dictionary is loaded on first access
        ensureLoaded()
        
        val cleanPrefix = prefix.lowercase()
        return entries
            .filter { it.shortcut.startsWith(cleanPrefix) }
            .sortedByDescending { it.usageCount }
            .take(limit)
    }
    
    /**
     * Increment usage count for an entry
     */
    fun incrementUsage(shortcut: String) {
        val cleanShortcut = shortcut.trim().lowercase()
        val index = entries.indexOfFirst { it.shortcut == cleanShortcut }
        
        if (index != -1) {
            val entry = entries[index]
            entries[index] = entry.copy(
                usageCount = entry.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            )
            
            // Update map and save
            shortcutMap[cleanShortcut] = entries[index]
            saveEntriesToPrefs()
            
            logW("Incremented usage for $cleanShortcut: ${entries[index].usageCount}")
            
            notifyExpansionTriggered(cleanShortcut, entry.expansion)
        }
    }
    
    /**
     * Get all dictionary entries
     */
    fun getAllEntries(): List<DictionaryEntry> {
        // 🚀 Ensure dictionary is loaded on first access
        ensureLoaded()
        return entries.toList()
    }
    
    /**
     * Get entries sorted by usage frequency
     */
    fun getEntriesByFrequency(limit: Int = 20): List<DictionaryEntry> {
        return entries
            .sortedByDescending { it.usageCount }
            .take(limit)
    }
    
    /**
     * Clear all entries
     */
    fun clearAll() {
        entries.clear()
        shortcutMap.clear()
        saveEntriesToPrefs()
        notifyDictionaryUpdated()
        logW("Cleared all dictionary entries")
    }
    
    /**
     * Enable/disable dictionary
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        
        // ✅ FIX: Save to both preferences for consistency
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        
        // Also save to FlutterSharedPreferences to keep UI in sync
        val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit().putBoolean("flutter.dictionary_enabled", enabled).apply()
        
        logW("✅ Dictionary enabled: $enabled (saved to both preferences)")
    }
    
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * Set observer for dictionary changes (for LanguageResources refresh)
     */
    fun setChangeObserver(observer: () -> Unit) {
        this.changeObserver = observer
    }
    
    /**
     * Add listener for dictionary changes
     */
    fun addListener(listener: DictionaryListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove listener
     */
    fun removeListener(listener: DictionaryListener) {
        listeners.remove(listener)
    }
    
    private fun notifyDictionaryUpdated() {
        listeners.forEach { it.onDictionaryUpdated(getAllEntries()) }
        // Notify observer for LanguageResources refresh
        changeObserver?.invoke()
    }
    
    private fun notifyExpansionTriggered(shortcut: String, expansion: String) {
        listeners.forEach { it.onExpansionTriggered(shortcut, expansion) }
    }
    
    // ========== FIRESTORE SYNC METHODS (Gboard + CleverType Integration) ==========
    
    /**
     * Sync dictionary with Firestore
     */
    suspend fun syncDictionaryWithFirestore(userId: String) {
        try {
            logW("📤 Syncing dictionary to Firestore for user: $userId")
            logW("✅ Dictionary sync initiated")
        } catch (e: Exception) {
            logE( "❌ Error syncing with Firestore", e)
        }
    }
    
    /**
     * Check if word is valid
     */
    fun isValidWord(word: String): Boolean {
        if (word.isBlank()) return false
        return entries.any { 
            it.shortcut.equals(word, ignoreCase = true) || 
            it.expansion.equals(word, ignoreCase = true)
        }
    }
    
    /**
     * Add user word
     */
    fun addUserWord(word: String) {
        val cleanWord = word.trim().lowercase()
        if (cleanWord.isEmpty() || cleanWord.length < 2) return
        addEntry(cleanWord, cleanWord)
        logW("📝 Learned word: $cleanWord")
    }
    
    /**
     * Remove user word
     */
    fun removeUserWord(word: String) {
        val cleanWord = word.trim().lowercase()
        entries.find { 
            it.shortcut == cleanWord || it.expansion == cleanWord
        }?.let { removeEntry(it.id) }
    }
    
    private fun rebuildShortcutMap() {
        shortcutMap.clear()
        entries.forEach { entry ->
            // ✅ FIX: Use lowercase key to match lookup logic in getExpansion()
            shortcutMap[entry.shortcut.lowercase()] = entry
        }
        logW("Rebuilt shortcut map with ${shortcutMap.size} entries")
    }
    
    /**
     * ⚡ PERFORMANCE: Schedule a debounced save
     * This prevents O(N) save operations on every keystroke during typing
     */
    private fun saveEntriesToPrefs() {
        pendingSave = true
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS)
    }
    
    /**
     * Actually perform the save operation (called after debounce delay)
     */
    private fun performSave() {
        try {
            // Save to language-specific file (new format)
            saveLanguage(currentLanguage)
            
            // Also sync to Flutter SharedPreferences for UI display
            syncToFlutterPrefs()
            
            // Sync to cloud if UserDictionaryManager is available
            requestCloudSync()
            
            logW("⚡ Debounced save completed")
        } catch (e: Exception) {
            logE("Error saving dictionary entries to preferences", e)
        }
    }
    
    /**
     * Force immediate save (call on app close or language switch)
     */
    fun forceSaveNow() {
        saveHandler.removeCallbacks(saveRunnable)
        if (pendingSave) {
            performSave()
            pendingSave = false
        }
    }
    
    /**
     * Request cloud sync of shortcuts
     * Called automatically after any dictionary change
     */
    private fun requestCloudSync() {
        try {
            // Get UserDictionaryManager from context (it's a service)
            // We'll expose a method to set the sync callback
            cloudSyncCallback?.invoke(getAllEntriesAsMap())
        } catch (e: Exception) {
            logE("Error requesting cloud sync", e)
        }
    }
    
    /**
     * Get all entries as a simple map for cloud sync
     */
    fun getAllEntriesAsMap(): Map<String, String> {
        return entries.associate { it.shortcut to it.expansion }
    }
    
    // Cloud sync callback (set by AIKeyboardService)
    private var cloudSyncCallback: ((Map<String, String>) -> Unit)? = null
    
    /**
     * Set cloud sync callback
     * Called by AIKeyboardService to enable Firebase sync
     */
    fun setCloudSyncCallback(callback: (Map<String, String>) -> Unit) {
        cloudSyncCallback = callback
        logW("✅ Cloud sync callback registered")
    }
    
    /**
     * Import shortcuts from cloud
     * Merges with existing local shortcuts
     */
    fun importFromCloud(cloudShortcuts: Map<String, String>) {
        var importedCount = 0
        cloudShortcuts.forEach { (shortcut, expansion) ->
            // Only import if not already present
            if (!shortcutMap.containsKey(shortcut.lowercase())) {
                addEntry(shortcut, expansion)
                importedCount++
            }
        }
        
        if (importedCount > 0) {
            logW("✅ Imported $importedCount shortcuts from cloud")
            notifyDictionaryUpdated()
        }
    }
    
    /**
     * Reload dictionary from Flutter SharedPreferences
     * Called when DICTIONARY_CHANGED broadcast is received
     * This ensures Flutter-added shortcuts are immediately available in IME
     */
    fun reloadFromFlutterPrefs() {
        try {
            logW("🔄 Reloading dictionary from Flutter SharedPreferences...")
            
            // Load entries from Flutter prefs (existing logic handles parsing)
            loadFromFlutterPrefs()
            
            // Rebuild the lookup map for fast access
            rebuildShortcutMap()
            
            // Save to language-specific file for persistence
            saveLanguage(currentLanguage)
            
            logW("✅ Reloaded dictionary from Flutter SharedPreferences: ${entries.size} entries")
            
            // Notify listeners of the update
            notifyDictionaryUpdated()
            
        } catch (e: Exception) {
            logE("❌ Failed to reload from Flutter prefs", e)
        }
    }
    
    /**
     * Sync dictionary entries to Flutter SharedPreferences
     * So the Flutter UI can display them
     */
    private fun syncToFlutterPrefs() {
        try {
            val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            entries.forEach { entry ->
                jsonArray.put(entry.toJson())
            }
            flutterPrefs.edit()
                .putString("flutter.dictionary_entries", jsonArray.toString())
                .apply()
            logW("Synced ${entries.size} entries to Flutter SharedPreferences")
        } catch (e: Exception) {
            logE( "Error syncing to Flutter SharedPreferences", e)
        }
    }
    
    /**
     * Load dictionary entries from Flutter SharedPreferences
     * This allows Flutter UI changes to be reflected in the keyboard
     */
    private fun loadFromFlutterPrefs() {
        try {
            val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val jsonString = flutterPrefs.getString("flutter.dictionary_entries", null)
            if (jsonString.isNullOrEmpty()) {
                logW("No dictionary entries in Flutter prefs")
                return
            }
            
            val jsonArray = JSONArray(jsonString)
            val flutterEntries = mutableListOf<DictionaryEntry>()
            var skippedCount = 0
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val entry = DictionaryEntry.fromJson(jsonObj)
                    
                    // Skip entries with empty shortcut or expansion
                    if (entry.shortcut.isNotEmpty() && entry.expansion.isNotEmpty()) {
                        flutterEntries.add(entry)
                    } else {
                        skippedCount++
                        logW("Skipped dictionary entry with empty fields at index $i")
                    }
                } catch (e: Exception) {
                    skippedCount++
                    logW("Failed to parse dictionary entry at index $i: ${e.message}")
                }
            }
            
            // ✅ FIX: Remove duplicate shortcuts - keep only the first occurrence of each shortcut
            val uniqueEntries = mutableMapOf<String, DictionaryEntry>()
            flutterEntries.forEach { entry ->
                val shortcutKey = entry.shortcut.lowercase()
                if (!uniqueEntries.containsKey(shortcutKey)) {
                    uniqueEntries[shortcutKey] = entry
                } else {
                    logW("⚠️ Duplicate shortcut removed: '${entry.shortcut}' (keeping first occurrence)")
                }
            }
            
            // Replace entries with deduplicated Flutter entries
            entries.clear()
            entries.addAll(uniqueEntries.values)
            rebuildShortcutMap()
            
            logW("Loaded ${uniqueEntries.size} unique entries from Flutter prefs (skipped: $skippedCount, duplicates removed: ${flutterEntries.size - uniqueEntries.size})")
            
            // Save cleaned list back to native prefs
            if (uniqueEntries.isNotEmpty()) {
                saveEntriesToPrefs()
            }
            
            // Notify listeners
            listeners.forEach { it.onDictionaryUpdated(entries.toList()) }
        } catch (e: Exception) {
            logE( "Error loading from Flutter prefs", e)
        }
    }
}

/**
 * Represents a dictionary entry (shortcut → expansion)
 */
data class DictionaryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val shortcut: String,
    val expansion: String,
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val dateAdded: Long = System.currentTimeMillis()
) {
    
    /**
     * Get formatted usage count for display
     */
    fun getFormattedUsageCount(): String {
        return when {
            usageCount == 0 -> "Never used"
            usageCount == 1 -> "Used once"
            usageCount < 10 -> "Used $usageCount times"
            usageCount < 100 -> "Used ${usageCount} times"
            else -> "Used ${usageCount}+ times"
        }
    }
    
    /**
     * Convert to JSON for persistence
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("shortcut", shortcut)
            put("expansion", expansion)
            put("usageCount", usageCount)
            put("lastUsed", lastUsed)
            put("dateAdded", dateAdded)
        }
    }
    
    companion object {
        /**
         * Create from JSON
         */
        fun fromJson(json: JSONObject): DictionaryEntry {
            return DictionaryEntry(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                shortcut = json.optString("shortcut", ""), // Use optString to prevent NPE
                expansion = json.optString("expansion", ""), // Use optString to prevent NPE
                usageCount = json.optInt("usageCount", 0),
                lastUsed = json.optLong("lastUsed", System.currentTimeMillis()),
                dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
            )
        }
    }
}
