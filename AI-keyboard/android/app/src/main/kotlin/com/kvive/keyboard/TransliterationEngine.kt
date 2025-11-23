package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import android.util.LruCache
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.*

/**
 * Transliteration Engine for Indian Languages
 * 
 * Converts Roman/Latin text to native Indic scripts (Devanagari, Telugu, Tamil)
 * Uses phoneme-based mapping with greedy longest-match algorithm
 * 
 * Algorithm:
 * 1. Load phoneme mappings from JSON (e.g., "namaste" → "नमस्ते")
 * 2. Buffer input characters
 * 3. Match longest phoneme sequences first (4 chars → 1 char)
 * 4. Apply contextual rules for ambiguous cases
 * 5. Cache recent conversions for performance
 * 
 * Supports: Hindi (hi), Telugu (te), Tamil (ta)
 * Standard: ITRANS-based with extensions
 */
class TransliterationEngine(
    private val context: Context,
    private val language: String
) {
    
    companion object {
        private const val TAG = "TransliterationEngine"
        private const val CACHE_SIZE = 500
        private const val MAX_PHONEME_LENGTH = 4 // Maximum chars in a phoneme (e.g., "chh", "ksh")
        
        // Supported languages
        private val SUPPORTED_LANGUAGES = setOf("hi", "te", "ta")
    }
    
    // Phoneme mappings loaded from JSON
    private val vowelMap = mutableMapOf<String, String>()
    private val consonantMap = mutableMapOf<String, String>()
    private val matraMap = mutableMapOf<String, String>()
    private val specialMap = mutableMapOf<String, String>()
    private val commonWordsMap = mutableMapOf<String, String>()
    
    // Reverse mappings for reverse transliteration
    private val reverseMap = mutableMapOf<String, String>()
    
    // LRU cache for frequently transliterated text
    private val cache = LruCache<String, String>(CACHE_SIZE)
    private val reverseCache = LruCache<String, String>(CACHE_SIZE)
    
    // Statistics
    private var cacheHits = 0
    private var cacheMisses = 0
    
    init {
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            LogUtil.w(TAG, "Unsupported language: $language")
        } else {
            loadPhonemeMap()
            LogUtil.d(TAG, "TransliterationEngine initialized for $language")
            LogUtil.d(TAG, "Loaded: ${vowelMap.size} vowels, ${consonantMap.size} consonants, ${matraMap.size} matras")
        }
    }
    
    /** Ensure transliteration map is available (offline assets only). */
    suspend fun ensureMapAvailable(lang: String) = withContext(Dispatchers.IO) {
        try {
            context.assets.open("transliteration/${lang}_map.json").close()
            LogUtil.d(TAG, "✅ Transliteration map available for $lang (assets)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Transliteration map missing for $lang", e)
            throw e
        }
    }
    
    /**
     * Transliterate Roman text to native Indic script
     * Example: "namaste" → "नमस्ते"
     */
    fun transliterate(romanText: String): String {
        if (romanText.isEmpty()) return ""
        
        // Check cache first
        cache.get(romanText)?.let {
            cacheHits++
            return it
        }
        
        cacheMisses++
        
        // Check if it's a common word (direct mapping)
        commonWordsMap[romanText.lowercase()]?.let { common ->
            cache.put(romanText, common)
            return common
        }
        
        val result = StringBuilder()
        var i = 0
        val text = romanText.lowercase()
        
        while (i < text.length) {
            var matched = false
            
            // Try longest match first (4 chars → 3 → 2 → 1)
            for (len in MAX_PHONEME_LENGTH downTo 1) {
                if (i + len <= text.length) {
                    val substring = text.substring(i, i + len)
                    
                    // Try consonant map first (most common)
                    consonantMap[substring]?.let { native ->
                        result.append(native)
                        i += len
                        matched = true
                        return@let
                    }
                    
                    // Try vowel map
                    if (!matched) {
                        vowelMap[substring]?.let { native ->
                            result.append(native)
                            i += len
                            matched = true
                            return@let
                        }
                    }
                    
                    // Try matra map (for standalone matras)
                    if (!matched) {
                        matraMap[substring]?.let { native ->
                            result.append(native)
                            i += len
                            matched = true
                            return@let
                        }
                    }
                    
                    // Try special characters
                    if (!matched) {
                        specialMap[substring]?.let { native ->
                            result.append(native)
                            i += len
                            matched = true
                            return@let
                        }
                    }
                    
                    if (matched) break
                }
            }
            
            // If no match found, keep the original character
            if (!matched) {
                result.append(text[i])
                i++
            }
        }
        
        val transliterated = result.toString()
        cache.put(romanText, transliterated)
        
        return transliterated
    }
    
    /**
     * Reverse transliterate native script to Roman
     * Example: "नमस्ते" → "namaste"
     * (Approximate - may not be exact due to phoneme ambiguity)
     */
    fun reverseTransliterate(nativeText: String): String {
        if (nativeText.isEmpty()) return ""
        
        // Check cache
        reverseCache.get(nativeText)?.let {
            return it
        }
        
        val result = StringBuilder()
        var i = 0
        
        while (i < nativeText.length) {
            var matched = false
            
            // Try to match native characters (including combining marks)
            val char = nativeText[i].toString()
            
            reverseMap[char]?.let { roman ->
                result.append(roman)
                matched = true
            }
            
            if (!matched) {
                result.append(char)
            }
            
            i++
        }
        
        val reversed = result.toString()
        reverseCache.put(nativeText, reversed)
        
        return reversed
    }
    
    /**
     * Get transliteration suggestions for partial input
     * Returns multiple possibilities for ambiguous phonemes
     */
    fun getSuggestions(partial: String): List<TranslitSuggestion> {
        if (partial.isEmpty()) return emptyList()
        
        val suggestions = mutableListOf<TranslitSuggestion>()
        
        // Get primary transliteration
        val primary = transliterate(partial)
        suggestions.add(TranslitSuggestion(primary, confidence = 1.0, isPrimary = true))
        
        // Check for ambiguous phonemes and provide alternatives
        // For example, "sh" could be "श" or "ष" in Hindi
        val alternatives = getAlternativeTransliterations(partial)
        suggestions.addAll(alternatives)
        
        return suggestions.take(5) // Return top 5 suggestions
    }
    
    /**
     * Get alternative transliterations for ambiguous input
     */
    private fun getAlternativeTransliterations(input: String): List<TranslitSuggestion> {
        val alternatives = mutableListOf<TranslitSuggestion>()
        
        when (language) {
            "hi" -> {
                // Hindi-specific alternatives
                if (input.contains("sh")) {
                    // "sh" could be "श" or "ष"
                    val alt1 = input.replace("sh", "shh")
                    alternatives.add(TranslitSuggestion(transliterate(alt1), 0.7, false))
                }
                if (input.contains("n")) {
                    // "n" could be "न" or "ण"
                    val alt2 = input.replace("n", "N")
                    alternatives.add(TranslitSuggestion(transliterate(alt2), 0.6, false))
                }
            }
            "te", "ta" -> {
                // Telugu/Tamil-specific alternatives
                if (input.contains("r")) {
                    val alt = input.replace("r", "rr")
                    alternatives.add(TranslitSuggestion(transliterate(alt), 0.7, false))
                }
            }
        }
        
        return alternatives.distinctBy { it.text }
    }
    
    /**
     * Check if input is likely Roman text (for Indic language)
     */
    fun isRomanInput(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Count Latin characters
        val latinCount = text.count { it.code in 97..122 || it.code in 65..90 }
        val total = text.filter { !it.isWhitespace() }.length
        
        return if (total == 0) false else (latinCount.toFloat() / total) > 0.5
    }
    
    /**
     * Load phoneme mappings from assets
     */
    private fun loadPhonemeMap() {
        try {
            val filename = "transliteration/${language}_map.json"
            val inputStream = context.assets.open(filename)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val jsonString = reader.use { it.readText() }
            
            val json = JSONObject(jsonString)
            
            // Load vowels
            if (json.has("vowels")) {
                val vowels = json.getJSONObject("vowels")
                vowels.keys().forEach { key ->
                    val value = vowels.getString(key)
                    vowelMap[key] = value
                    reverseMap[value] = key
                }
            }
            
            // Load consonants
            if (json.has("consonants")) {
                val consonants = json.getJSONObject("consonants")
                consonants.keys().forEach { key ->
                    val value = consonants.getString(key)
                    consonantMap[key] = value
                    reverseMap[value] = key
                }
            }
            
            // Load matras (vowel signs)
            if (json.has("matras")) {
                val matras = json.getJSONObject("matras")
                matras.keys().forEach { key ->
                    val value = matras.getString(key)
                    matraMap[key] = value
                    reverseMap[value] = key
                }
            }
            
            // Load special characters
            if (json.has("special")) {
                val special = json.getJSONObject("special")
                special.keys().forEach { key ->
                    val value = special.getString(key)
                    specialMap[key] = value
                    reverseMap[value] = key
                }
            }
            
            // Load common words (direct mappings)
            if (json.has("common_words")) {
                val commonWords = json.getJSONObject("common_words")
                commonWords.keys().forEach { key ->
                    val value = commonWords.getString(key)
                    commonWordsMap[key] = value
                }
            }
            
            LogUtil.d(TAG, "✅ Loaded transliteration map for $language from assets")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error loading transliteration map for $language", e)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        val hitRate = if (cacheHits + cacheMisses > 0) {
            (cacheHits.toFloat() / (cacheHits + cacheMisses) * 100).toInt()
        } else 0
        
        return "Cache: $cacheHits hits, $cacheMisses misses ($hitRate% hit rate)"
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        cache.evictAll()
        reverseCache.evictAll()
        cacheHits = 0
        cacheMisses = 0
        LogUtil.d(TAG, "Cache cleared")
    }
}

/**
 * Represents a transliteration suggestion
 */
data class TranslitSuggestion(
    val text: String,
    val confidence: Double,
    val isPrimary: Boolean
)
