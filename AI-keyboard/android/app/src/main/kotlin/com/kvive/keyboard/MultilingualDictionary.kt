package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


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
 * Fully offline, RAM-cached.
 */
class MultilingualDictionaryImpl(private val context: Context) : MultilingualDictionary {

    companion object {
        private const val TAG = "MultilingualDict"
        private const val MAX_WORDS_PER_LANGUAGE = 50000
        private const val MAX_BIGRAMS_PER_LANGUAGE = 100000
        private const val MAX_TRIGRAMS_PER_LANGUAGE = 50000
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // RAM caches (never re-read disk while typing)
    private val cachedWords = HashMap<String, HashMap<String, Int>>()
    private val cachedBigrams = HashMap<String, HashMap<Pair<String, String>, Int>>()
    private val cachedTrigrams = HashMap<String, HashMap<Triple<String, String, String>, Int>>()
    private val cachedCorrections = HashMap<String, Map<String, String>>()

    // Thread-safe resource storage
    private val languageResources = ConcurrentHashMap<String, LanguageResources>()

    // Track which languages are loaded
    private val loadedLanguages = ConcurrentHashMap.newKeySet<String>()

    // Loading jobs for async operations
    private val loadingJobs = ConcurrentHashMap<String, Job>()

    // Performance lookup caches
    private val bigramLookupCache = ConcurrentHashMap<String, Map<String, List<String>>>()
    private val trigramLookupCache = ConcurrentHashMap<String, Map<String, List<String>>>()

    // Callbacks
    var onLanguageReady: ((String) -> Unit)? = null
    var onLanguageActivated: ((String) -> Unit)? = null

    private var userDictionaryManager: UserDictionaryManager? = null
    private var dictionaryManager: DictionaryManager? = null

    fun setOnLanguageReadyListener(listener: (String) -> Unit) {
        onLanguageReady = listener
        LogUtil.d(TAG, "✅ Language readiness listener registered")
    }

    fun setOnLanguageActivatedListener(listener: (String) -> Unit) {
        onLanguageActivated = listener
        LogUtil.d(TAG, "✅ Language activation listener registered")
    }

    fun setUserDictionaryManager(manager: UserDictionaryManager) {
        this.userDictionaryManager = manager
    }

    fun setDictionaryManager(manager: DictionaryManager) {
        this.dictionaryManager = manager
    }

    override fun isLoaded(lang: String): Boolean = loadedLanguages.contains(lang)

    override fun get(lang: String): LanguageResources? = languageResources[lang]

    override suspend fun preload(lang: String) {
        // 🔥 FIX 3.1 - COMPLETE prevention of multiple loads
        if (languageResources.containsKey(lang)) {
            LogUtil.d(TAG, "⚡ Language already cached in RAM: $lang - skipping reload")
            return
        }
        
        loadingJobs[lang]?.let { existing ->
            existing.join()
            return
        }
        if (isLoaded(lang)) return

        LogUtil.d(TAG, "📦 Preloading offline language data for $lang")

        val job = ioScope.launch {
            try {
                // Words
                val words = cachedWords[lang] ?: loadWordsFromAssets(lang).also { cachedWords[lang] = it }
                val bigrams = cachedBigrams[lang] ?: loadBigramsFromAssets(lang).also { cachedBigrams[lang] = it }
                val trigrams = cachedTrigrams[lang] ?: loadTrigramsFromAssets(lang).also { cachedTrigrams[lang] = it }
                val corrections = cachedCorrections[lang] ?: loadCorrectionsFromAssets(lang).also { cachedCorrections[lang] = it }
                val quadgrams: NGram4? = null

                val userWords = getUserWords(lang)
                val shortcuts = getShortcuts(lang)
                val mergedWords = HashMap(words).apply {
                    userWords.forEach { putIfAbsent(it, 1) }
                }

                val resources = LanguageResources(
                    lang = lang,
                    words = mergedWords,
                    bigrams = bigrams,
                    trigrams = trigrams,
                    quadgrams = quadgrams,
                    corrections = corrections,
                    userWords = userWords,
                    shortcuts = shortcuts
                )

                languageResources[lang] = resources
                loadedLanguages.add(lang)
                loadingJobs.remove(lang)

                buildLookupCaches(lang, resources)

                LogUtil.d(
                    TAG,
                    "✅ Preloaded $lang (words=${words.size}, bigrams=${bigrams.size}, trigrams=${trigrams.size})"
                )

                onLanguageReady?.invoke(lang)
                onLanguageActivated?.invoke(lang)
            } catch (e: Exception) {
                loadingJobs.remove(lang)
                LogUtil.e(TAG, "❌ Error preloading language $lang", e)
            }
        }

        loadingJobs[lang] = job
        job.join()
    }

    // Legacy entry point – now fully offline
    suspend fun ensureLanguageAvailable(lang: String) = preload(lang)

    override fun getNextWordCandidates(context: List<String>, language: String, limit: Int): List<String> {
        val resources = languageResources[language] ?: return emptyList()
        if (context.isEmpty()) return emptyList()

        val candidates = mutableListOf<Pair<String, Int>>()

        try {
            val cachedTrigramLookup = trigramLookupCache[language]
            val cachedBigramLookup = bigramLookupCache[language]

            if (context.size >= 2 && cachedTrigramLookup != null) {
                val w1 = context[context.size - 2].lowercase()
                val w2 = context.last().lowercase()
                val key = "$w1 $w2"
                cachedTrigramLookup[key]?.forEach { next ->
                    val freq = resources.trigrams[Triple(w1, w2, next)] ?: 0
                    candidates.add(next to freq)
                }
            }

            if (cachedBigramLookup != null) {
                val last = context.last().lowercase()
                cachedBigramLookup[last]?.forEach { next ->
                    if (candidates.none { it.first == next }) {
                        val freq = resources.bigrams[Pair(last, next)] ?: 0
                        candidates.add(next to freq)
                    }
                }
            }

            return candidates.sortedByDescending { it.second }.take(limit).map { it.first }
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error in dictionary fallback for $context", e)
            return emptyList()
        }
    }

    private fun buildLookupCaches(lang: String, resources: LanguageResources) {
        try {
            val bigramLookup = mutableMapOf<String, MutableList<String>>()
            resources.bigrams.forEach { (pair, _) ->
                val (first, second) = pair
                bigramLookup.getOrPut(first) { mutableListOf() }.add(second)
            }
            bigramLookupCache[lang] = bigramLookup.mapValues { it.value.toList() }

            val trigramLookup = mutableMapOf<String, MutableList<String>>()
            resources.trigrams.forEach { (tri, _) ->
                val key = "${tri.first} ${tri.second}"
                trigramLookup.getOrPut(key) { mutableListOf() }.add(tri.third)
            }
            trigramLookupCache[lang] = trigramLookup.mapValues { it.value.toList() }
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error building lookup caches for $lang", e)
        }
    }

    // === Asset loaders (offline only) ===

    private suspend fun loadWordsFromAssets(language: String): HashMap<String, Int> = withContext(Dispatchers.IO) {
        val map = HashMap<String, Int>()
        val path = "dictionaries/${language}_words.txt"
        try {
            context.assets.open(path).bufferedReader().useLines { lines ->
                var count = 0
                lines.forEach { raw ->
                    if (count >= MAX_WORDS_PER_LANGUAGE) return@forEach
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val parts = line.split(Regex("\\s+"), 2)
                    val word = parts[0]
                    val freq = if (parts.size > 1) parts[1].toIntOrNull() ?: (1000 + count) else (1000 + count)
                    map[word] = freq
                    count++
                }
            }
            LogUtil.d(TAG, "📖 Loaded $language words from assets: ${map.size}")
        } catch (e: Exception) {
            LogUtil.w(TAG, "⚠️ Missing words asset for $language at $path, using empty map")
        }
        map
    }

    private suspend fun loadBigramsFromAssets(language: String): HashMap<Pair<String, String>, Int> = withContext(Dispatchers.IO) {
        val map = HashMap<Pair<String, String>, Int>()
        val path = "dictionaries/${language}_bigrams.txt"
        try {
            context.assets.open(path).bufferedReader().useLines { lines ->
                var count = 0
                lines.forEach { raw ->
                    if (count >= MAX_BIGRAMS_PER_LANGUAGE) return@forEach
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val parts = if (line.contains(",")) line.split(",") else line.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val freq = parts.getOrNull(2)?.toIntOrNull() ?: 10
                        map[Pair(parts[0], parts[1])] = freq
                        count++
                    }
                }
            }
            LogUtil.d(TAG, "📊 Loaded $language bigrams from assets: ${map.size}")
        } catch (e: Exception) {
            LogUtil.w(TAG, "⚠️ Missing bigrams asset for $language at $path, using empty map")
        }
        map
    }

    private suspend fun loadTrigramsFromAssets(language: String): HashMap<Triple<String, String, String>, Int> = withContext(Dispatchers.IO) {
        val map = HashMap<Triple<String, String, String>, Int>()
        val path = "dictionaries/${language}_trigrams.txt"
        try {
            context.assets.open(path).bufferedReader().useLines { lines ->
                var count = 0
                lines.forEach { raw ->
                    if (count >= MAX_TRIGRAMS_PER_LANGUAGE) return@forEach
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEach
                    val parts = if (line.contains(",")) line.split(",") else line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val freq = parts.getOrNull(3)?.toIntOrNull() ?: 1
                        map[Triple(parts[0], parts[1], parts[2])] = freq
                        count++
                    }
                }
            }
            LogUtil.d(TAG, "📊 Loaded $language trigrams from assets: ${map.size}")
        } catch (e: Exception) {
            LogUtil.w(TAG, "⚠️ Missing trigrams asset for $language at $path, using empty map")
        }
        map
    }

    /**
     * ✅ UPDATED: Loads corrections from the optimized binary file (.bin)
     * Replaces the old text parser for 50x faster loading.
     */
    private suspend fun loadCorrectionsFromAssets(language: String): Map<String, String> = withContext(Dispatchers.IO) {
        val map = HashMap<String, String>()
        
        // 1. Change target file to .bin
        val path = "dictionaries/${language}_corrections.bin" 
        
        try {
            // Optional: Check if file exists in assets (avoids crash if missing)
            val assets = context.assets.list("dictionaries")
            if (assets == null || !assets.contains("${language}_corrections.bin")) {
                LogUtil.w(TAG, "⚠️ Binary corrections not found for $language, return empty.")
                return@withContext emptyMap()
            }

            context.assets.open(path).use { stream ->
                val dis = DataInputStream(stream)
                
                // 2. Validate Header (Must match Python script 'CORR')
                val magic = ByteArray(4)
                dis.read(magic)
                if (String(magic) != "CORR") {
                    LogUtil.e(TAG, "❌ Invalid binary corrections format")
                    return@withContext emptyMap()
                }
                
                // 3. Read Version (Skip for now, or check if needed)
                val version = dis.readShort()
                
                // 4. Read Total Count
                val count = dis.readInt()
                
                // 5. Fast Loop to Read All Pairs
                for (i in 0 until count) {
                    // Read Typo
                    val typoLen = dis.readUnsignedByte() // 1 byte length
                    val typoBytes = ByteArray(typoLen)
                    dis.readFully(typoBytes)
                    val typo = String(typoBytes, Charsets.UTF_8)
                    
                    // Read Correction
                    val fixLen = dis.readUnsignedByte() // 1 byte length
                    val fixBytes = ByteArray(fixLen)
                    dis.readFully(fixBytes)
                    val fix = String(fixBytes, Charsets.UTF_8)
                    
                    // Add to map
                    map[typo] = fix
                }
            }
            LogUtil.d(TAG, "⚡ Fast-loaded ${map.size} corrections from binary for $language")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error loading binary corrections: ${e.message}")
        }
        return@withContext map
    }

    // === User data helpers ===

    private suspend fun getUserWords(language: String): Set<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            userDictionaryManager?.getTopWords(1000)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error loading user words: ${e.message}")
            emptySet()
        }
    }

    private suspend fun getShortcuts(language: String): Map<String, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            dictionaryManager?.getAllEntriesAsMap() ?: emptyMap()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error loading shortcuts: ${e.message}")
            emptyMap()
        }
    }

    // === Legacy compatibility helpers ===

    fun getCandidates(prefix: String, language: String, limit: Int = 64): List<String> {
        val resources = get(language) ?: return emptyList()
        val prefixLower = prefix.lowercase()

        return resources.words.entries
            .filter { it.key.lowercase().startsWith(prefixLower) }
            .sortedBy { it.value }
            .take(limit)
            .map { it.key }
    }

    fun getAllWords(language: String): List<String> = get(language)?.words?.keys?.toList() ?: emptyList()

    fun getFrequency(language: String, word: String): Int = get(language)?.words?.get(word) ?: Int.MAX_VALUE

    fun getBigramFrequency(language: String, w1: String, w2: String): Int =
        get(language)?.bigrams?.get(Pair(w1, w2)) ?: 0

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

    fun getTrigramFrequency(language: String, w1: String, w2: String, w3: String): Int =
        get(language)?.trigrams?.get(Triple(w1, w2, w3)) ?: 0

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

    fun contains(language: String, word: String): Boolean = get(language)?.words?.containsKey(word) ?: false

    fun getLoadedLanguages(): List<String> = loadedLanguages.toList()

    fun getLoadedWordCount(): Int = languageResources.values.sumOf { it.words.size }

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

    fun unloadLanguage(language: String) {
        languageResources.remove(language)
        loadedLanguages.remove(language)
        loadingJobs[language]?.cancel()
        loadingJobs.remove(language)
        LogUtil.d(TAG, "🗑️ Unloaded language: $language")
    }

    fun clear() {
        languageResources.clear()
        loadedLanguages.clear()
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        LogUtil.d(TAG, "🗑️ Cleared all dictionaries")
    }

    fun updateLanguageResources(lang: String, newResources: LanguageResources) {
        languageResources[lang] = newResources
        LogUtil.d(TAG, "🔄 Updated resources for $lang")
    }

    fun loadLanguage(language: String, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            try {
                preload(language)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in legacy loadLanguage for $language", e)
            }
        }
    }

    // Versioning/network stubs kept for compatibility (no-op offline mode)
    suspend fun checkAndUpdateVersion(lang: String, type: String) {}
    suspend fun batchVersionCheck() {}
    suspend fun forceUpdateLanguage(lang: String) {}
    fun getLanguageVersionInfo(lang: String): Map<String, Int> = emptyMap()
    suspend fun schedulePeriodicVersionCheck() {}
}
