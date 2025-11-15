package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Phase 2: Multi-Language Cloud Sync Support
 * - Sync per-language dictionaries to Firestore
 * - Merge cloud + local data without overwriting
 * - Support language-specific shortcuts and learned words
 */
class UserDictionaryManager(private val context: Context) {
    private val TAG = "UserDictionaryManager"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: "anonymous"

    // Multi-language support
    private val userWordsDir = File(context.filesDir, "user_words").apply {
        if (!exists()) mkdirs()
    }
    
    // Legacy file (backward compatibility)
    private val localFile = File(context.filesDir, "user_words.json")
    
    private val localMap = mutableMapOf<String, Int>() // word → usageCount
    private var currentLanguage = "en"
    
    // Rejection blacklist for autocorrect
    private val rejectionBlacklist = mutableSetOf<Pair<String, String>>()
    
    // Debounced save mechanism
    private var saveJob: Job? = null
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Observer for user dictionary changes (for LanguageResources refresh)
    private var changeObserver: (() -> Unit)? = null

    init {
        // Detect current language
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        currentLanguage = prefs.getString("flutter.keyboard_language", "en") ?: "en"
        
        loadLocalCache()
        loadBlacklist()
    }

    /** Load user words from local JSON file (language-specific) */
    private fun loadLocalCache() {
        // Try language-specific file first
        val langFile = File(userWordsDir, "$currentLanguage.json")
        if (langFile.exists()) {
            try {
                val json = JSONObject(langFile.readText())
                localMap.clear()
                json.keys().forEach { key ->
                    localMap[key] = json.getInt(key)
                }
                LogUtil.i(TAG, "✅ Loaded ${localMap.size} learned words for $currentLanguage")
                return
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Failed to load cache for $currentLanguage: ${e.message}")
            }
        }
        
        // Fall back to legacy file and migrate
        if (localFile.exists()) {
            try {
                val json = JSONObject(localFile.readText())
                localMap.clear()
                json.keys().forEach { key ->
                    localMap[key] = json.getInt(key)
                }
                LogUtil.i(TAG, "✅ Migrated ${localMap.size} words from legacy cache")
                saveLocalCache() // Save to new format
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Failed to load legacy cache: ${e.message}")
            }
        }
    }

    /** Save local cache to file (language-specific) */
    private fun saveLocalCache() {
        try {
            val langFile = File(userWordsDir, "$currentLanguage.json")
            val json = JSONObject(localMap as Map<*, *>)
            langFile.writeText(json.toString())
            LogUtil.d(TAG, "💾 Saved user dictionary for $currentLanguage (${localMap.size} entries)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Failed to save cache: ${e.message}")
        }
    }

    /**
     * Set observer for user dictionary changes (for LanguageResources refresh)
     */
    fun setChangeObserver(observer: () -> Unit) {
        this.changeObserver = observer
    }
    
    /** 
     * Learn a new word (called from TypingSyncAudit or Autocorrect acceptance)
     * Phase 1 Enhancement: Improved frequency tracking with caps
     */
    fun learnWord(word: String) {
        if (word.length < 2 || word.any { it.isDigit() }) return
        
        // Phase 1: Improved frequency tracking with trigram stats integration
        val normalizedWord = word.lowercase() // Normalize for consistent storage
        val currentCount = localMap.getOrDefault(normalizedWord, 0)
        val newCount = (currentCount + 1).coerceAtMost(1000) // Cap at 1000 to prevent overflow
        
        localMap[normalizedWord] = newCount
        LogUtil.d(TAG, "✨ Learned '$normalizedWord' (count=$currentCount → $newCount)")
        
        // Track learning statistics
        incrementLearningStat("words_learned")
        
        // Notify observer of changes (for LanguageResources refresh)
        changeObserver?.invoke()
        
        // Debounced save: only save once after 2 seconds of inactivity
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(2000)
            saveLocalCache()
        }
    }
    
    /**
     * Phase 1: Track learning statistics for analytics
     */
    private fun incrementLearningStat(key: String) {
        try {
            val prefs = context.getSharedPreferences("user_dict_stats", Context.MODE_PRIVATE)
            val current = prefs.getInt(key, 0)
            prefs.edit().putInt(key, current + 1).apply()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to increment stat '$key': ${e.message}")
        }
    }
    
    /**
     * Phase 1: Get word frequency with normalized access
     * Returns usage count for a word, 0 if not learned
     */
    fun getFrequency(word: String): Int {
        val normalized = word.lowercase()
        return localMap[normalized] ?: 0
    }
    
    /** Force immediate save (call on keyboard close) */
    fun flush() {
        saveJob?.cancel()
        saveLocalCache()
        LogUtil.d(TAG, "🔄 User dictionary flushed to disk")
    }

    // ==================== PHASE 2: MULTI-LANGUAGE CLOUD SYNC ====================
    
    /**
     * Push local dictionary to Firestore (per language)
     * @param language Language code (defaults to current language)
     */
    fun syncToCloud(language: String = currentLanguage) {
        if (userId == "anonymous") {
            LogUtil.w(TAG, "⚠️ Skipping cloud sync (user not logged in)")
            return
        }
        
        val data = localMap.entries.map { mapOf("word" to it.key, "count" to it.value) }
        firestore.collection("users")
            .document(userId)
            .collection("dictionary")
            .document(language)
            .set(mapOf("entries" to data, "lastModified" to System.currentTimeMillis()))
            .addOnSuccessListener {
                LogUtil.i(TAG, "☁️ Synced ${localMap.size} words to cloud for $language")
            }
            .addOnFailureListener {
                LogUtil.e(TAG, "❌ Failed cloud sync for $language: ${it.message}")
            }
    }

    /**
     * Pull from Firestore and merge (per language)
     * Merges frequencies without overwriting higher local counts
     * @param language Language code (defaults to current language)
     */
    fun syncFromCloud(language: String = currentLanguage) {
        if (userId == "anonymous") {
            LogUtil.w(TAG, "⚠️ Skipping cloud pull (user not logged in)")
            return
        }
        
        firestore.collection("users")
            .document(userId)
            .collection("dictionary")
            .document(language)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    LogUtil.i(TAG, "No cloud dictionary found for $language")
                    return@addOnSuccessListener
                }
                
                val entries = (doc.get("entries") as? List<Map<String, Any>>) ?: return@addOnSuccessListener
                var merged = 0
                
                for (e in entries) {
                    val w = e["word"] as? String ?: continue
                    val c = (e["count"] as? Long)?.toInt() ?: 1
                    
                    // Merge by summing frequencies (Phase 4: merge logic)
                    val currentCount = localMap[w] ?: 0
                    localMap[w] = currentCount + c
                    merged++
                }
                
                saveLocalCache()
                LogUtil.i(TAG, "🔄 Merged $merged cloud words for $language")
            }
            .addOnFailureListener {
                LogUtil.w(TAG, "⚠️ Failed to pull cloud dictionary for $language: ${it.message}")
            }
    }
    
    /**
     * Sync custom shortcuts to Firebase (per language)
     * Called from DictionaryManager integration
     * @param shortcuts Map of shortcut -> expansion
     * @param language Language code (defaults to current language)
     */
    fun syncShortcutsToCloud(shortcuts: Map<String, String>, language: String = currentLanguage) {
        if (userId == "anonymous") {
            Log.w(TAG, "Cannot sync shortcuts - user not authenticated")
            return
        }
        
        try {
            val data = shortcuts.entries.map { 
                mapOf("shortcut" to it.key, "expansion" to it.value) 
            }
            
            firestore.collection("users")
                .document(userId)
                .collection("shortcuts")
                .document(language)
                .set(mapOf(
                    "shortcuts" to data,
                    "lastModified" to System.currentTimeMillis()
                ))
                .addOnSuccessListener {
                    Log.d(TAG, "☁️ Synced ${shortcuts.size} shortcuts for $language")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to sync shortcuts for $language", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing shortcuts to cloud", e)
        }
    }
    
    /**
     * Load custom shortcuts from Firebase (per language)
     * Returns map of shortcut -> expansion
     * @param language Language code (defaults to current language)
     */
    fun loadShortcutsFromCloud(language: String = currentLanguage, callback: (Map<String, String>) -> Unit) {
        if (userId == "anonymous") {
            Log.w(TAG, "Cannot load shortcuts - user not authenticated")
            callback(emptyMap())
            return
        }
        
        try {
            firestore.collection("users")
                .document(userId)
                .collection("shortcuts")
                .document(language)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val shortcuts = mutableMapOf<String, String>()
                        @Suppress("UNCHECKED_CAST")
                        val data = document.get("shortcuts") as? List<Map<String, String>>
                        data?.forEach { entry ->
                            val shortcut = entry["shortcut"]
                            val expansion = entry["expansion"]
                            if (shortcut != null && expansion != null) {
                                shortcuts[shortcut] = expansion
                            }
                        }
                        Log.d(TAG, "✅ Loaded ${shortcuts.size} shortcuts from cloud for $language")
                        callback(shortcuts)
                    } else {
                        Log.d(TAG, "No shortcuts found in cloud for $language")
                        callback(emptyMap())
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to load shortcuts for $language", e)
                    callback(emptyMap())
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shortcuts from cloud", e)
            callback(emptyMap())
        }
    }
    
    /**
     * Switch to a different language
     * Saves current language and loads new language data
     */
    fun switchLanguage(newLang: String) {
        if (newLang == currentLanguage) return
        
        // Save current language
        saveLocalCache()
        
        // Switch language
        currentLanguage = newLang
        
        // Load new language cache
        loadLocalCache()
        
        LogUtil.i(TAG, "🌐 Switched to language: $newLang")
    }
    
    /**
     * Phase 4: Merge cloud words with local dictionary
     * Called by DictionaryManager after cloud sync
     * @param words Map of word -> frequency count
     * @param onComplete Callback when merge is complete
     */
    fun mergeCloudWords(words: Map<String, Int>, onComplete: ((Int) -> Unit)? = null) {
        var mergedCount = 0
        
        words.forEach { (word, count) ->
            val currentCount = localMap[word] ?: 0
            // Sum frequencies from both sources
            localMap[word] = currentCount + count
            mergedCount++
        }
        
        if (mergedCount > 0) {
            saveLocalCache()
            LogUtil.i(TAG, "✅ Merged $mergedCount cloud words into local dictionary for $currentLanguage")
        }
        
        onComplete?.invoke(mergedCount)
    }
    
    /**
     * Get list of available language dictionaries
     */
    fun getAvailableLanguages(): List<String> {
        return try {
            userWordsDir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error listing languages: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get current language
     */
    fun getCurrentLanguage(): String = currentLanguage
    
    // ==================== END PHASE 2 ====================

    /** Get top learned words for suggestion ranking */
    fun getTopWords(limit: Int = 50): List<String> =
        localMap.entries.sortedByDescending { it.value }.take(limit).map { it.key }

    /** Check if word is in user dictionary */
    fun hasLearnedWord(word: String): Boolean = localMap.containsKey(word)

    /** Get word usage count */
    fun getWordCount(word: String): Int = localMap[word] ?: 0

    /** Clear all learned words locally and in cloud */
    fun clearAllWords() {
        localMap.clear()
        saveLocalCache()
        
        // Also clear from Firestore
        firestore.collection("users")
            .document(userId)
            .collection("user_dictionary")
            .document("words")
            .delete()
            .addOnSuccessListener {
                LogUtil.i(TAG, "🗑️ Cleared all user words from cloud")
            }
            .addOnFailureListener {
                LogUtil.w(TAG, "⚠️ Failed to clear cloud words: ${it.message}")
            }
    }

    /** 
     * Get statistics (Phase 1: Enhanced with learning stats)
     */
    fun getStats(): Map<String, Int> {
        val prefs = context.getSharedPreferences("user_dict_stats", Context.MODE_PRIVATE)
        
        return mapOf(
            "total_words" to localMap.size,
            "top_usage" to (localMap.values.maxOrNull() ?: 0),
            "avg_usage" to if (localMap.isNotEmpty()) localMap.values.average().toInt() else 0,
            "words_learned" to prefs.getInt("words_learned", 0),
            "words_corrected" to prefs.getInt("words_corrected", 0),
            "swipes_learned" to prefs.getInt("swipes_learned", 0)
        )
    }
    
    /**
     * Phase 1: Learn from correction acceptance
     * Track when user accepts an autocorrect suggestion
     */
    fun learnFromCorrection(original: String, corrected: String) {
        // Learn the corrected word with boosted frequency
        learnWord(corrected)
        
        // Track correction stat
        incrementLearningStat("words_corrected")
        
        LogUtil.d(TAG, "✨ Learned correction: '$original' → '$corrected'")
    }
    
    /**
     * Phase 1: Learn from swipe acceptance
     * Track when user accepts a swipe suggestion
     */
    fun learnFromSwipe(word: String) {
        learnWord(word)
        
        // Track swipe learning stat
        incrementLearningStat("swipes_learned")
        
        LogUtil.d(TAG, "✨ Learned swipe: '$word'")
    }
    
    /**
     * Phase 1: Decay old words to prioritize recent usage
     * Call this periodically (e.g., once per day) to reduce counts of old words
     */
    fun decayOldWords(decayFactor: Double = 0.9) {
        try {
            var decayed = 0
            localMap.keys.toList().forEach { word ->
                val currentCount = localMap[word] ?: return@forEach
                val newCount = (currentCount * decayFactor).toInt().coerceAtLeast(1)
                
                if (newCount != currentCount) {
                    localMap[word] = newCount
                    decayed++
                }
            }
            
            if (decayed > 0) {
                saveLocalCache()
                LogUtil.d(TAG, "🔄 Decayed $decayed words (factor=$decayFactor)")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error decaying words: ${e.message}")
        }
    }
    
    /**
     * Phase 1: Get top learned words with their frequencies
     * Useful for analytics and debugging
     */
    fun getTopWordsWithFrequency(limit: Int = 20): List<Pair<String, Int>> {
        return localMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Pair(it.key, it.value) }
    }
    
    // ==================== Autocorrect Rejection Blacklist ====================
    
    /**
     * Blacklist a correction that the user rejected
     */
    fun blacklistCorrection(original: String, corrected: String) {
        val pair = Pair(original.lowercase(), corrected.lowercase())
        rejectionBlacklist.add(pair)
        LogUtil.d(TAG, "🚫 Blacklisted correction '$original' → '$corrected'")
        saveBlacklist()
    }
    
    /**
     * Check if a correction is blacklisted
     */
    fun isBlacklisted(original: String, corrected: String): Boolean {
        return rejectionBlacklist.contains(Pair(original.lowercase(), corrected.lowercase()))
    }
    
    /**
     * Save blacklist to SharedPreferences
     */
    private fun saveBlacklist() {
        try {
            val prefs = context.getSharedPreferences("ai_keyboard_prefs", Context.MODE_PRIVATE)
            val json = JSONArray()
            for ((o, c) in rejectionBlacklist) {
                val obj = JSONObject()
                obj.put("o", o)
                obj.put("c", c)
                json.put(obj)
            }
            prefs.edit().putString("rejection_blacklist", json.toString()).apply()
            LogUtil.d(TAG, "💾 Saved ${rejectionBlacklist.size} rejected corrections to prefs")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Failed to save blacklist: ${e.message}")
        }
    }
    
    /**
     * Load blacklist from SharedPreferences
     */
    fun loadBlacklist() {
        try {
            val prefs = context.getSharedPreferences("ai_keyboard_prefs", Context.MODE_PRIVATE)
            val data = prefs.getString("rejection_blacklist", null) ?: return
            val arr = JSONArray(data)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                rejectionBlacklist.add(Pair(obj.getString("o"), obj.getString("c")))
            }
            LogUtil.d(TAG, "🧠 Loaded ${rejectionBlacklist.size} rejected corrections from prefs")
        } catch (e: Exception) {
            LogUtil.e(TAG, "⚠️ Error loading blacklist: ${e.message}")
        }
    }
    
    /**
     * Clear all blacklisted corrections
     */
    fun clearBlacklist() {
        rejectionBlacklist.clear()
        val prefs = context.getSharedPreferences("ai_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("rejection_blacklist").apply()
        LogUtil.d(TAG, "🗑️ Cleared all rejected corrections")
    }
    
    /**
     * Get blacklist size for debugging
     */
    fun getBlacklistSize(): Int = rejectionBlacklist.size
}
