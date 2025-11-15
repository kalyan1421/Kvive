package com.kvive.keyboard

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import com.google.firebase.storage.FirebaseStorage
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import android.content.SharedPreferences

/**
 * Data structures for unified language resources
 */
typealias Lexicon = Map<String, Int>  // word → frequency
typealias NGram2 = Map<Pair<String, String>, Int>  // bigrams
typealias NGram3 = Map<Triple<String, String, String>, Int>  // trigrams
typealias NGram4 = Map<List<String>, Int>  // quadgrams (optional)

/**
 * LanguageResources DTO - Single container for all language data
 * Used by UnifiedAutocorrectEngine as single source of truth
 */
data class LanguageResources(
    val lang: String,
    val words: Lexicon,                 // Trie/DAWG set + freq
    val bigrams: NGram2,                // Map<Pair<String,String>, Int>
    val trigrams: NGram3,               // Map<Triple<String,String,String>, Int>
    val quadgrams: NGram4?,             // Map<List<String>, Int> (optional)
    val corrections: Map<String, String>,
    val userWords: Set<String>,
    val shortcuts: Map<String, String>
)

/**
 * Interface for MultilingualDictionary as specified
 */
interface MultilingualDictionary {
    suspend fun preload(lang: String)
    fun get(lang: String): LanguageResources?
    fun isLoaded(lang: String): Boolean
    
    /**
     * Get next-word candidates using direct n-gram lookup (fallback for when engine isn't ready)
     */
    fun getNextWordCandidates(context: List<String>, language: String = "en", limit: Int = 5): List<String>
}

/**
 * MultilingualDictionary - Single source of truth for language data
 * Refactored to use LanguageResources DTO and eliminate duplicate loading
 * 
 * Features:
 * - LanguageResources DTO with all n-gram data
 * - Thread-safe resource management
 * - User dictionary and corrections integration
 * - Lazy loading with atomic updates
 */
class MultilingualDictionaryImpl(private val context: Context) : MultilingualDictionary {
    
    // SharedPreferences for caching metadata
    private val cachePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("dictionary_cache", Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val TAG = "MultilingualDict"
        private const val MAX_WORDS_PER_LANGUAGE = 50000
        private const val MAX_BIGRAMS_PER_LANGUAGE = 100000
        private const val MAX_TRIGRAMS_PER_LANGUAGE = 50000
    }
    
    // Thread-safe resource storage
    private val languageResources = ConcurrentHashMap<String, LanguageResources>()
    
    // Track which languages are loaded
    private val loadedLanguages = ConcurrentHashMap.newKeySet<String>()
    
    // Loading jobs for async operations
    private val loadingJobs = ConcurrentHashMap<String, Job>()
    
    // 6️⃣ Performance optimization: Cache bigram/trigram lookup maps
    private val bigramLookupCache = ConcurrentHashMap<String, Map<String, List<String>>>()
    private val trigramLookupCache = ConcurrentHashMap<String, Map<String, List<String>>>()
    
    // Language readiness callback
    var onLanguageReady: ((String) -> Unit)? = null
    
    // Language activation callback (triggered after successful download + preload)
    var onLanguageActivated: ((String) -> Unit)? = null
    
    /**
     * Set callback listener for when language becomes ready
     */
    fun setOnLanguageReadyListener(listener: (String) -> Unit) {
        onLanguageReady = listener
        LogUtil.d(TAG, "✅ Language readiness listener registered")
    }
    
    /**
     * Set callback listener for when language is fully activated (download + preload + engine ready)
     */
    fun setOnLanguageActivatedListener(listener: (String) -> Unit) {
        onLanguageActivated = listener
        LogUtil.d(TAG, "✅ Language activation listener registered")
    }
    
    /**
     * Download language from Firebase and automatically activate it
     * This is the PUBLIC API for triggering language downloads with full activation
     * 
     * @param lang Language code (e.g., "hi", "te", "ta")
     * @param onComplete Optional callback when download and activation is complete
     */
    suspend fun downloadLanguage(lang: String, onComplete: (() -> Unit)? = null) {
        try {
            LogUtil.d(TAG, "🌐 Starting Firebase download and activation for $lang")
            
            // Step 1: Ensure Firebase files are downloaded
            ensureLanguageAvailable(lang)
            
            // Step 2: The preload() call in ensureLanguageAvailable() will trigger callbacks
            // onLanguageReady and onLanguageActivated will be called automatically
            
            LogUtil.i(TAG, "🎉 Language download and activation completed for $lang")
            
            // Step 3: Notify caller
            onComplete?.invoke()
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Failed to download and activate language: $lang", e)
            throw e
        }
    }
    
    // REMOVED: assetExists() - no longer using assets, Firebase-only approach
    
    /**
     * Unified helper to get cloud cache file path
     */
    private fun getCacheFile(lang: String, type: String): File {
        return File(context.filesDir, "cloud_cache/dictionaries/$lang/${lang}_${type}.txt")
    }
    
    /**
     * Check if language is cached (downloaded from Firebase)
     */
    fun isLanguageCached(lang: String): Boolean {
        val wordsFile = getCacheFile(lang, "words")
        val bigramsFile = getCacheFile(lang, "bigrams")
        return wordsFile.exists() && wordsFile.length() > 0 && 
               bigramsFile.exists() && bigramsFile.length() > 0
    }
    
    /**
     * Auto-preload English on first initialization
     */
    suspend fun autoPreloadEnglish() {
        if (!isLoaded("en")) {
            LogUtil.d(TAG, "🌐 Auto-preloading English dictionary from Firebase...")
            try {
                ensureLanguageAvailable("en")
                preload("en")
                LogUtil.d(TAG, "✅ English dictionary auto-preloaded successfully")
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Failed to auto-preload English, using assets: ${e.message}")
                preload("en") // Will use assets fallback
            }
        }
    }
    
    // User dictionary and corrections integration
    private var userDictionaryManager: UserDictionaryManager? = null
    private var dictionaryManager: DictionaryManager? = null
    
    /**
     * Set user dictionary manager for integration
     */
    fun setUserDictionaryManager(manager: UserDictionaryManager) {
        this.userDictionaryManager = manager
    }
    
    /**
     * Set dictionary manager for integration
     */
    fun setDictionaryManager(manager: DictionaryManager) {
        this.dictionaryManager = manager
    }

    /**
     * Check if a language is loaded (interface method)
     */
    override fun isLoaded(lang: String): Boolean {
        return loadedLanguages.contains(lang)
    }
    
    /**
     * Get LanguageResources for a language (interface method)
     * Returns immutable snapshot of language data
     */
    override fun get(lang: String): LanguageResources? {
        return languageResources[lang]
    }
    
    /**
     * 3️⃣ Get next-word candidates using direct n-gram lookup (fallback implementation)
     */
    override fun getNextWordCandidates(context: List<String>, language: String, limit: Int): List<String> {
        val resources = languageResources[language] ?: run {
            LogUtil.w(TAG, "⚠️ No resources for language $language in dictionary fallback")
            return emptyList()
        }
        
        if (context.isEmpty()) return emptyList()
        
        val candidates = mutableListOf<Pair<String, Int>>()
        
        try {
            // 6️⃣ Use cached lookup maps for better performance
            val cachedTrigramLookup = trigramLookupCache[language]
            val cachedBigramLookup = bigramLookupCache[language]
            
            // Try trigram predictions first (if we have 2+ words context)
            if (context.size >= 2 && cachedTrigramLookup != null) {
                val word1 = context[context.size-2].lowercase()
                val word2 = context.last().lowercase()
                val trigramKey = "$word1 $word2"
                
                cachedTrigramLookup[trigramKey]?.forEach { nextWord ->
                    val freq = resources.trigrams[Triple(word1, word2, nextWord)] ?: 0
                    candidates.add(Pair(nextWord, freq))
                }
                LogUtil.d(TAG, "📊 Cached trigram lookup for ($word1, $word2): ${candidates.size} matches")
            }
            
            // Try bigram predictions (always, as fallback or primary)  
            if (cachedBigramLookup != null) {
                val lastWord = context.last().lowercase()
                
                cachedBigramLookup[lastWord]?.forEach { nextWord ->
                    if (!candidates.any { it.first == nextWord }) {
                        val freq = resources.bigrams[Pair(lastWord, nextWord)] ?: 0
                        candidates.add(Pair(nextWord, freq))
                    }
                }
                LogUtil.d(TAG, "📊 Cached bigram lookup for '$lastWord': ${candidates.size} total candidates")
            }
            
            // Sort by frequency and take top candidates
            val result = candidates
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
                
            LogUtil.d(TAG, "📊 Dictionary fallback candidates for $context: $result")
            return result
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error in dictionary fallback for $context", e)
            return emptyList()
        }
    }
    
    /**
     * 6️⃣ Build performance lookup caches for fast n-gram access
     */
    private fun buildLookupCaches(lang: String, resources: LanguageResources) {
        try {
            // Build bigram lookup cache: lastWord -> List<nextWords>
            val bigramLookup = mutableMapOf<String, MutableList<String>>()
            resources.bigrams.forEach { (bigram, _) ->
                val (first, second) = bigram
                bigramLookup.getOrPut(first) { mutableListOf() }.add(second)
            }
            bigramLookupCache[lang] = bigramLookup.mapValues { it.value.toList() }
            
            // Build trigram lookup cache: "word1 word2" -> List<nextWords>
            val trigramLookup = mutableMapOf<String, MutableList<String>>()
            resources.trigrams.forEach { (trigram, _) ->
                val key = "${trigram.first} ${trigram.second}"
                trigramLookup.getOrPut(key) { mutableListOf() }.add(trigram.third)
            }
            trigramLookupCache[lang] = trigramLookup.mapValues { it.value.toList() }
            
            LogUtil.d(TAG, "📈 Performance caches built for $lang: bigrams=${bigramLookup.size} keys, trigrams=${trigramLookup.size} keys")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error building lookup caches for $lang", e)
        }
    }
    
    /**
     * Preload a language dictionary (interface method)
     * This is the single entry point for loading language data
     */
    override suspend fun preload(lang: String) {
        if (isLoaded(lang) || loadingJobs.containsKey(lang)) {
            LogUtil.d(TAG, "Language $lang already loaded or loading")
            return
        }
        
        LogUtil.d(TAG, "🌐 Firebase preload started for $lang")
        
        val job = GlobalScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Load base dictionary data
                val words = loadWordsFromAsset(lang)
                val bigrams = loadBigramsFromAsset(lang)
                val trigrams = loadTrigramsFromAsset(lang)
                val quadgrams = loadQuadgramsFromAsset(lang) // Optional
                
                // Load corrections (per-language)
                val corrections = loadCorrections(lang)
                
                // Get user data
                val userWords = getUserWords(lang)
                val shortcuts = getShortcuts(lang)
                
                // Create immutable LanguageResources
                val resources = LanguageResources(
                    lang = lang,
                    words = words,
                    bigrams = bigrams,
                    trigrams = trigrams,
                    quadgrams = quadgrams,
                    corrections = corrections,
                    userWords = userWords,
                    shortcuts = shortcuts
                )
                
                // Atomically update resources BEFORE callback
                languageResources[lang] = resources
                loadedLanguages.add(lang)
                loadingJobs.remove(lang)
                
                // 6️⃣ Build performance lookup caches
                buildLookupCaches(lang, resources)
                
                val duration = System.currentTimeMillis() - startTime
                val wordCount = words.size
                val bigramCount = bigrams.size
                val trigramCount = trigrams.size
                
                // Enhanced logging with Firebase source confirmation
                LogUtil.d(TAG, "🌍 Firebase preload complete for $lang")
                LogUtil.d(TAG, "📖 Loaded $lang: words=$wordCount, bigrams=$bigramCount, trigrams=$trigramCount (Firebase cache) (${duration}ms)")
                LogUtil.d(TAG, "✅ UnifiedAutocorrectEngine ready for $lang")
                
                // CRITICAL: Verify resources are accessible before callback
                val verifyResources = languageResources[lang]
                if (verifyResources != null) {
                    LogUtil.d(TAG, "🔍 Resources verified for $lang - calling readiness callback")
                    LogUtil.d(TAG, "✅ SuggestionsPipeline: Using unified Firebase data")
                    onLanguageReady?.invoke(lang)
                    
                    // NEW: Also trigger activation callback for complete setup
                    onLanguageActivated?.invoke(lang)
                    LogUtil.d(TAG, "🎯 Language activation callback triggered for $lang")
                } else {
                    LogUtil.e(TAG, "❌ CRITICAL: Resources not accessible after load for $lang")
                }
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "❌ Error preloading language $lang", e)
                loadingJobs.remove(lang)
            }
        }
        
        loadingJobs[lang] = job
        job.join() // Wait for completion
    }
    
    /**
     * Ensure language data is available, downloading from Firebase if needed
     * This is the main entry point for cloud-first language loading
     * ALSO PRELOADS the language into memory after download
     */
    suspend fun ensureLanguageAvailable(lang: String) = withContext(Dispatchers.IO) {
        try {
            LogUtil.d(TAG, "🌐 Ensuring language data available for $lang")
            
            // Check and download dictionary files if needed
            val wordFile = ensureDictionaryFile(lang, "words")
            val bigramFile = ensureDictionaryFile(lang, "bigrams") 
            val trigramFile = ensureDictionaryFile(lang, "trigrams")
            val correctionsFile = ensureDictionaryFile(lang, "corrections") // Optional but important
            val quadgramsFile = ensureDictionaryFile(lang, "quadgrams") // Optional
            
            LogUtil.d(TAG, "✅ Language files ready for $lang (cached: ${wordFile?.exists()})")
            
            // CRITICAL: Actually preload the language into memory after download
            if (!isLoaded(lang)) {
                LogUtil.d(TAG, "⚙️ Preloading language data into memory: $lang")
                preload(lang) // This is async but we join() so it waits
                LogUtil.d(TAG, "✅ Language preloaded successfully: $lang")
            } else {
                LogUtil.d(TAG, "✅ Language already loaded in memory: $lang")
                // Still notify readiness - but verify resources exist first
                val resources = languageResources[lang]
                if (resources != null) {
                    LogUtil.d(TAG, "🔍 Notifying readiness for already-loaded $lang")
                    onLanguageReady?.invoke(lang)
                    onLanguageActivated?.invoke(lang) // Also trigger activation callback
                } else {
                    LogUtil.e(TAG, "❌ Language marked loaded but no resources found: $lang")
                }
            }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error ensuring language data for $lang", e)
            throw e
        }
    }
    
    /**
     * Load words from cached file first, assets second, Firebase third
     * Returns immutable Lexicon map
     */
    private suspend fun loadWordsFromAsset(language: String): Lexicon = withContext(Dispatchers.IO) {
        return@withContext loadWords(language)
    }
    
    private fun loadWords(language: String): Lexicon {
        val wordMap = mutableMapOf<String, Int>()
        
        // Load EXCLUSIVELY from Firebase cache
        val cacheFile = getCacheFile(language, "words")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            LogUtil.d(TAG, "📁 Loading from Firebase cache: ${language}_words.txt")
            return loadWordsFromFile(cacheFile)
        } else {
            // No Firebase cache available - trigger download
            LogUtil.e(TAG, "❌ Firebase cache not found for ${language}_words.txt - please ensure language is downloaded")
            throw IllegalStateException("Language $language not downloaded from Firebase. Call ensureLanguageAvailable() first.")
        }
    }
    
    /**
     * Load words from a File (both cached and downloaded files)
     */
    private fun loadWordsFromFile(file: File): Lexicon {
        val wordMap = mutableMapOf<String, Int>()
        var count = 0
        
        try {
            file.bufferedReader().use { br ->
                br.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEachIndexed { index, line ->
                        if (count >= MAX_WORDS_PER_LANGUAGE) return@forEachIndexed
                        val parts = line.split(Regex("\\s+"), 2)
                        if (parts.isNotEmpty()) {
                            val word = parts[0]
                            val freq = if (parts.size > 1) {
                                parts[1].toIntOrNull() ?: (1000 + count)
                            } else {
                                1000 + count
                            }
                            wordMap[word] = freq
                            count++
                        }
                    }
            }
            LogUtil.d(TAG, "📖 Loaded $count words from file: ${file.name}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error loading words from file: ${file.name}", e)
        }
        
        return wordMap.toMap()
    }
    
    
    
    /**
     * Load bigrams from assets/dictionaries/{lang}_bigrams.txt
     * Returns immutable NGram2 map
     */
    private suspend fun loadBigramsFromAsset(language: String): NGram2 = withContext(Dispatchers.IO) {
        return@withContext loadBigrams(language)
    }
    
    private fun loadBigrams(language: String): NGram2 {
        val bigramMap = mutableMapOf<Pair<String, String>, Int>()
        
        // Load EXCLUSIVELY from Firebase cache
        val cacheFile = getCacheFile(language, "bigrams")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            LogUtil.d(TAG, "📁 Loading from Firebase cache: ${language}_bigrams.txt")
            return loadBigramsFromFile(cacheFile)
        } else {
            // No Firebase cache available - trigger download
            LogUtil.e(TAG, "❌ Firebase cache not found for ${language}_bigrams.txt - please ensure language is downloaded")
            throw IllegalStateException("Language $language not downloaded from Firebase. Call ensureLanguageAvailable() first.")
        }
    }
    
    /**
     * Load bigrams from a File (both cached and downloaded files)
     */
    private fun loadBigramsFromFile(file: File): NGram2 {
        val bigramMap = mutableMapOf<Pair<String, String>, Int>()
        var count = 0
        
        try {
            file.bufferedReader().use { br ->
                br.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { line ->
                        if (count >= MAX_BIGRAMS_PER_LANGUAGE) return@forEach
                        val parts = when {
                            "," in line -> line.split(",")
                            else -> line.split(Regex("\\s+"))
                        }
                        if (parts.size >= 2) {
                            val freq = if (parts.size > 2) {
                                parts[2].toIntOrNull() ?: 10
                            } else {
                                10
                            }
                            bigramMap[Pair(parts[0], parts[1])] = freq
                            count++
                        }
                    }
            }
            LogUtil.d(TAG, "📊 Loaded $count bigrams from file: ${file.name}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error loading bigrams from file: ${file.name}", e)
        }
        
        return bigramMap.toMap()
    }
    
    
    
    // === LEGACY COMPATIBILITY METHODS ===
    // These methods provide backward compatibility for existing code
    
    /**
     * Get word candidates that start with prefix (legacy compatibility)
     * Delegates to LanguageResources data
     */
    fun getCandidates(prefix: String, language: String, limit: Int = 64): List<String> {
        val resources = get(language) ?: return emptyList()
        val prefixLower = prefix.lowercase()
        
        return resources.words.entries
            .filter { it.key.lowercase().startsWith(prefixLower) }
            .sortedBy { it.value } // Lower frequency rank = more common
            .take(limit)
            .map { it.key }
    }
    
    /**
     * Get all words for a language (legacy compatibility)
     */
    fun getAllWords(language: String): List<String> {
        val resources = get(language) ?: return emptyList()
        return resources.words.keys.toList()
    }
    
    /**
     * Get frequency rank for a word (legacy compatibility)
     */
    fun getFrequency(language: String, word: String): Int {
        val resources = get(language) ?: return Int.MAX_VALUE
        return resources.words[word] ?: Int.MAX_VALUE
    }
    
    /**
     * Get bigram frequency (legacy compatibility)
     */
    fun getBigramFrequency(language: String, w1: String, w2: String): Int {
        val resources = get(language) ?: return 0
        return resources.bigrams[Pair(w1, w2)] ?: 0
    }
    
    /**
     * Get next word predictions based on bigram frequency (legacy compatibility)
     */
    fun getBigramNextWords(language: String, previousWord: String, limit: Int = 5): List<String> {
        val resources = get(language) ?: return emptyList()
        val normalizedPrev = previousWord.lowercase().trim()
        
        return try {
            resources.bigrams
                .filterKeys { it.first == normalizedPrev }
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key.second }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting bigram next words for '$previousWord'", e)
            emptyList()
        }
    }
    
    /**
     * Get trigram frequency (legacy compatibility)
     */
    fun getTrigramFrequency(language: String, w1: String, w2: String, w3: String): Int {
        val resources = get(language) ?: return 0
        return resources.trigrams[Triple(w1, w2, w3)] ?: 0
    }
    
    /**
     * Load trigrams from assets/dictionaries/{lang}_trigrams.txt
     * Returns immutable NGram3 map
     */
    private suspend fun loadTrigramsFromAsset(language: String): NGram3 = withContext(Dispatchers.IO) {
        return@withContext loadTrigrams(language)
    }
    
    private fun loadTrigrams(language: String): NGram3 {
        val trigramMap = mutableMapOf<Triple<String, String, String>, Int>()
        
        // Load EXCLUSIVELY from Firebase cache
        val cacheFile = getCacheFile(language, "trigrams")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            LogUtil.d(TAG, "📁 Loading from Firebase cache: ${language}_trigrams.txt")
            return loadTrigramsFromFile(cacheFile)
        } else {
            // No Firebase cache available - trigger download
            LogUtil.e(TAG, "❌ Firebase cache not found for ${language}_trigrams.txt - please ensure language is downloaded")
            throw IllegalStateException("Language $language not downloaded from Firebase. Call ensureLanguageAvailable() first.")
        }
    }
    
    /**
     * Load trigrams from a File (both cached and downloaded files)
     */
    private fun loadTrigramsFromFile(file: File): NGram3 {
        val trigramMap = mutableMapOf<Triple<String, String, String>, Int>()
        var count = 0
        
        try {
            file.bufferedReader().use { br ->
                br.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { line ->
                        if (count >= MAX_TRIGRAMS_PER_LANGUAGE) return@forEach
                        val parts = when {
                            "," in line -> line.split(",")
                            else -> line.split(Regex("\\s+"))
                        }
                        if (parts.size >= 3) {
                            val freq = parts.getOrNull(3)?.toIntOrNull() ?: 1
                            trigramMap[Triple(parts[0], parts[1], parts[2])] = freq
                            count++
                        }
                    }
            }
            LogUtil.d(TAG, "📊 Loaded $count trigrams from file: ${file.name}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error loading trigrams from file: ${file.name}", e)
        }
        
        return trigramMap.toMap()
    }
    
    
    
    /**
     * Load quadgrams - gracefully returns empty since we don't have quadgram files
     */
    private suspend fun loadQuadgramsFromAsset(language: String): NGram4? = withContext(Dispatchers.IO) {
        return@withContext loadQuadgrams(language)
    }
    
    private fun loadQuadgrams(language: String): NGram4? {
        // You don't have any *_quadgrams.txt. Make this a no-op that gracefully returns empty
        LogUtil.d(TAG, "📊 Quadgrams not available for $language (no quadgram files in assets)")
        return null
    }
    
    /**
     * Load corrections from Firebase cache (language-specific corrections)
     * Note: Corrections are now per-language and loaded from Firebase cache
     */
    private suspend fun loadCorrections(language: String = "en"): Map<String, String> = withContext(Dispatchers.IO) {
        val correctionsMap = mutableMapOf<String, String>()
        
        // Load EXCLUSIVELY from Firebase cache
        val cacheFile = getCacheFile(language, "corrections")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            LogUtil.d(TAG, "📁 Loading from Firebase cache: ${language}_corrections.txt")
            
            try {
                cacheFile.bufferedReader().use { br ->
                    br.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .forEach { line ->
                            val parts = line.split("\t", ",", ":").map { it.trim() }
                            if (parts.size >= 2) {
                                correctionsMap[parts[0].lowercase()] = parts[1]
                            }
                        }
                }
                LogUtil.d(TAG, "📝 Loaded ${correctionsMap.size} corrections from Firebase cache for $language")
                
                // 🔍 DEBUG: Log first 10 corrections for verification
                if (correctionsMap.isNotEmpty()) {
                    val samples = correctionsMap.entries.take(10).joinToString(", ") { "${it.key}→${it.value}" }
                    LogUtil.d(TAG, "🔍 Sample corrections: $samples")
                    
                    // 🔍 Check for common test corrections
                    val testWords = listOf("teh", "adn", "hte", "yuo", "recieve")
                    testWords.forEach { word ->
                        val correction = correctionsMap[word]
                        if (correction != null) {
                            LogUtil.d(TAG, "✅ Test correction found: '$word' → '$correction'")
                        } else {
                            LogUtil.w(TAG, "⚠️ Test correction MISSING: '$word'")
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "❌ Error loading corrections from cache: ${e.message}")
            }
        } else {
            // No Firebase cache available - return empty (corrections are optional)
            LogUtil.w(TAG, "⚠️ Firebase cache not found for ${language}_corrections.txt (optional)")
        }
        
        return@withContext correctionsMap.toMap()
    }
    
    /**
     * Get user words from UserDictionaryManager
     */
    private suspend fun getUserWords(language: String): Set<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            userDictionaryManager?.getTopWords(1000)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error loading user words: ${e.message}")
            emptySet()
        }
    }
    
    /**
     * Get shortcuts from DictionaryManager
     */
    private suspend fun getShortcuts(language: String): Map<String, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            dictionaryManager?.getAllEntriesAsMap() ?: emptyMap()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error loading shortcuts: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Get next word predictions based on trigram context (legacy compatibility)
     */
    fun getTrigramNextWords(language: String, previousWord1: String, previousWord2: String, limit: Int = 5): List<String> {
        val resources = get(language) ?: return emptyList()
        val normalized1 = previousWord1.lowercase().trim()
        val normalized2 = previousWord2.lowercase().trim()
        
        return try {
            resources.trigrams
                .filterKeys { it.first == normalized1 && it.second == normalized2 }
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key.third }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting trigram next words for '$normalized1 $normalized2'", e)
            emptyList()
        }
    }
    
    /**
     * Check if a word exists in the dictionary (legacy compatibility)
     */
    fun contains(language: String, word: String): Boolean {
        val resources = get(language) ?: return false
        return resources.words.containsKey(word)
    }
    
    /**
     * Get list of currently loaded languages
     */
    fun getLoadedLanguages(): List<String> {
        return loadedLanguages.toList()
    }
    
    /**
     * Get total word count across all loaded languages
     */
    fun getLoadedWordCount(): Int {
        return languageResources.values.sumOf { it.words.size }
    }
    
    /**
     * Get statistics for loaded languages
     */
    fun getStats(): Map<String, Map<String, Int>> {
        val stats = mutableMapOf<String, Map<String, Int>>()
        
        languageResources.forEach { (lang, resources) ->
            stats[lang] = mapOf(
                "words" to resources.words.size,
                "bigrams" to resources.bigrams.size,
                "trigrams" to resources.trigrams.size,
                "corrections" to resources.corrections.size,
                "userWords" to resources.userWords.size,
                "shortcuts" to resources.shortcuts.size
            )
        }
        
        return stats
    }
    
    /**
     * Unload a language to free memory
     */
    fun unloadLanguage(language: String) {
        languageResources.remove(language)
        loadedLanguages.remove(language)
        loadingJobs[language]?.cancel()
        loadingJobs.remove(language)
        LogUtil.d(TAG, "🗑️ Unloaded language: $language")
    }
    
    /**
     * Clear all dictionaries
     */
    fun clear() {
        languageResources.clear()
        loadedLanguages.clear()
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        LogUtil.d(TAG, "🗑️ Cleared all dictionaries")
    }
    
    /**
     * Update resources for a language (for dynamic user word updates)
     */
    fun updateLanguageResources(lang: String, newResources: LanguageResources) {
        languageResources[lang] = newResources
        LogUtil.d(TAG, "🔄 Updated resources for $lang")
    }
    
    /**
     * Legacy compatibility method for loadLanguage
     * Delegates to preload method
     */
    fun loadLanguage(language: String, scope: CoroutineScope) {
        scope.launch {
            try {
                preload(language)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in legacy loadLanguage for $language", e)
            }
        }
    }
    
    /**
     * Ensure dictionary file exists, downloading from Firebase if needed with retry logic
     * This enables hybrid offline-first with CDN fallback approach
     * 
     * @param lang Language code (e.g., "en", "hi")
     * @param type Dictionary type (e.g., "words", "bigrams", "trigrams")
     * @return File if available, null if not found anywhere
     */
    private suspend fun ensureDictionaryFile(lang: String, type: String): File? = withContext(Dispatchers.IO) {
        // Check if file exists in Firebase cache
        val cachedFile = getCacheFile(lang, type)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            LogUtil.d(TAG, "✅ Using Firebase cache: ${cachedFile.name}")
            return@withContext cachedFile
        }
        
        // NO ASSET FALLBACK - Firebase-only approach
        LogUtil.d(TAG, "🌐 Firebase cache not found for ${lang}_${type}.txt - downloading...")
        
        // Attempt to download from Firebase Storage with retry logic
        return@withContext downloadFromFirebaseWithRetry(lang, type, cachedFile, maxRetries = 3)
    }
    
    /**
     * Download dictionary file from Firebase Storage with exponential backoff retry
     */
    private suspend fun downloadFromFirebaseWithRetry(
        lang: String, 
        type: String, 
        targetFile: File, 
        maxRetries: Int = 3
    ): File? = withContext(Dispatchers.IO) {
        repeat(maxRetries) { attempt ->
            try {
                val storage = Firebase.storage
                val storageRef = storage.reference.child("dictionaries/$lang/${lang}_${type}.txt")
                
                // Create cache directory if needed
                targetFile.parentFile?.mkdirs()
                
                // Download with coroutine-friendly await()
                LogUtil.d(TAG, "🌐 Downloading $type dictionary for $lang (attempt ${attempt + 1}/$maxRetries)")
                storageRef.getFile(targetFile).await()
                
                // Verify download success
                if (targetFile.exists() && targetFile.length() > 0) {
                    LogUtil.d(TAG, "🌐 Downloaded $type for $lang from Firebase (${targetFile.length()} bytes)")
                    
                    // Store version info for future updates
                    cachePrefs.edit()
                        .putLong("dict_${lang}_${type}_downloaded", System.currentTimeMillis())
                        .putInt("dict_${lang}_${type}_version", 1)
                        .apply()
                        
                    return@withContext targetFile
                } else {
                    LogUtil.w(TAG, "⚠️ Downloaded file is empty: $lang $type")
                }
                
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Download attempt ${attempt + 1} failed for $lang $type: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    // Exponential backoff: 1s, 2s, 4s
                    val delayMs = 1000L * (1 shl attempt)
                    LogUtil.d(TAG, "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    LogUtil.e(TAG, "❌ All download attempts failed for $lang/$type", e)
                }
            }
        }
        
        return@withContext null
    }
    
    /**
     * Check and update dictionary version if needed
     * Downloads newer versions from Firebase automatically
     */
    suspend fun checkAndUpdateVersion(lang: String, type: String) = withContext(Dispatchers.IO) {
        try {
            // Get current version
            val currentVersion = cachePrefs.getInt("dict_${lang}_${type}_version", 0)
            
            // Fetch latest version metadata from Firebase
            val latestVersion = fetchLatestVersion(lang, type)
            
            if (latestVersion > currentVersion) {
                LogUtil.d(TAG, "🔄 Update available for $lang $type: v$currentVersion → v$latestVersion")
                
                // Download updated version
                val targetFile = File(context.filesDir, "cloud_cache/dictionaries/$lang/${lang}_${type}.txt")
                val success = downloadFromFirebaseWithRetry(lang, type, targetFile, maxRetries = 3)
                
                if (success != null) {
                    // Update version in cache
                    cachePrefs.edit()
                        .putInt("dict_${lang}_${type}_version", latestVersion)
                        .putLong("dict_${lang}_${type}_updated", System.currentTimeMillis())
                        .apply()
                        
                    LogUtil.i(TAG, "🔁 Updated $lang $type dictionary to version $latestVersion")
                    
                    // Notify that language needs reloading
                    onLanguageReady?.invoke(lang)
                } else {
                    LogUtil.w(TAG, "⚠️ Failed to update $lang $type to version $latestVersion")
                }
            } else {
                LogUtil.d(TAG, "✅ Dictionary $lang $type is up to date (v$currentVersion)")
            }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error checking version for $lang $type", e)
        }
    }
    
    /**
     * Fetch latest version number from Firebase Storage metadata
     */
    private suspend fun fetchLatestVersion(lang: String, type: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val storage = Firebase.storage
            val metadataRef = storage.reference.child("metadata/${lang}_${type}_version.json")
            
            // Create temp file for metadata
            val tempFile = File.createTempFile("version_${lang}_${type}", ".json", context.cacheDir)
            
            // Download version metadata
            metadataRef.getFile(tempFile).await()
            
            // Parse version from JSON
            val versionJson = tempFile.readText()
            val versionData = org.json.JSONObject(versionJson)
            val version = versionData.getInt("version")
            
            // Clean up temp file
            tempFile.delete()
            
            LogUtil.d(TAG, "📋 Latest version for $lang $type: v$version")
            version
            
        } catch (e: Exception) {
            LogUtil.w(TAG, "⚠️ Could not fetch latest version for $lang $type, assuming current", e)
            // Return current version to avoid unnecessary downloads
            cachePrefs.getInt("dict_${lang}_${type}_version", 1)
        }
    }
    
    /**
     * Batch version check for all cached languages
     * Called periodically to check for updates
     */
    suspend fun batchVersionCheck() = withContext(Dispatchers.IO) {
        LogUtil.d(TAG, "🔍 Running batch version check...")
        
        // Get all cached languages
        val cachedLanguages = getCachedLanguages()
        
        val types = listOf("words", "bigrams", "trigrams")
        
        cachedLanguages.forEach { lang ->
            types.forEach { type ->
                try {
                    checkAndUpdateVersion(lang, type)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "❌ Error in batch version check for $lang $type", e)
                }
                
                // Add small delay between checks to avoid overwhelming Firebase
                delay(100)
            }
        }
        
        LogUtil.d(TAG, "✅ Batch version check completed")
    }
    
    /**
     * Get list of cached languages from SharedPreferences
     */
    private fun getCachedLanguages(): List<String> {
        return try {
            val prefs = context.getSharedPreferences("dictionary_cache", Context.MODE_PRIVATE)
            val cachedLangs = mutableSetOf<String>()
            
            // Extract language codes from cache keys
            prefs.all.keys.forEach { key ->
                if (key.startsWith("dict_") && key.endsWith("_version")) {
                    val parts = key.split("_")
                    if (parts.size >= 2) {
                        cachedLangs.add(parts[1]) // Extract language code
                    }
                }
            }
            
            cachedLangs.toList()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting cached languages", e)
            emptyList()
        }
    }
    
    /**
     * Force update a specific language to latest version
     */
    suspend fun forceUpdateLanguage(lang: String) = withContext(Dispatchers.IO) {
        LogUtil.i(TAG, "🔄 Force updating all dictionaries for $lang")
        
        val types = listOf("words", "bigrams", "trigrams")
        
        types.forEach { type ->
            try {
                // Reset current version to 0 to force download
                cachePrefs.edit()
                    .putInt("dict_${lang}_${type}_version", 0)
                    .apply()
                
                // Check and update (will download latest)
                checkAndUpdateVersion(lang, type)
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "❌ Error force updating $lang $type", e)
            }
        }
        
        LogUtil.i(TAG, "✅ Force update completed for $lang")
    }
    
    /**
     * Get version info for a specific language
     */
    fun getLanguageVersionInfo(lang: String): Map<String, Int> {
        val types = listOf("words", "bigrams", "trigrams")
        val versionInfo = mutableMapOf<String, Int>()
        
        types.forEach { type ->
            val version = cachePrefs.getInt("dict_${lang}_${type}_version", 0)
            versionInfo[type] = version
        }
        
        return versionInfo
    }
    
    /**
     * Schedule periodic version checks (called from app initialization)
     */
    suspend fun schedulePeriodicVersionCheck() = withContext(Dispatchers.IO) {
        // Check if we should run version check (once per day)
        val lastCheck = cachePrefs.getLong("last_version_check", 0)
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        
        if (now - lastCheck > dayInMs) {
            LogUtil.d(TAG, "📅 Running daily version check...")
            
            batchVersionCheck()
            
            // Update last check timestamp
            cachePrefs.edit()
                .putLong("last_version_check", now)
                .apply()
                
            LogUtil.d(TAG, "✅ Daily version check completed")
        } else {
            val hoursUntilNext = ((dayInMs - (now - lastCheck)) / (60 * 60 * 1000L))
            LogUtil.d(TAG, "⏰ Next version check in ~$hoursUntilNext hours")
        }
    }
}
