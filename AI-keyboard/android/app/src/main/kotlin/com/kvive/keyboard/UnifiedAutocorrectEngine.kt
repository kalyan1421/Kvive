package com.kvive.keyboard

import android.content.Context
import android.util.LruCache
import com.kvive.keyboard.symspell.SymSpell
import com.kvive.keyboard.symspell.SymSpellLoader
import com.kvive.keyboard.symspell.Verbosity
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kvive.keyboard.trie.MappedTrieDictionary
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Data class for swipe path (for swipe input processing)
 */
data class SwipePath(val points: List<Pair<Float, Float>>)

/**
 * Swipe metrics for proper ranking
 */
data class SwipeMetrics(
    val word: String,
    val pathScore: Double,   // likelihood of this path generating word
    val proximity: Double,   // avg nearest-key distance (smaller = better)
    val editDistance: Int    // Levenshtein between snapped key sequence and word
)

/**
 * Suggestion sources for tracking and debugging
 */
enum class SuggestionSource {
    TYPING, SWIPE, LM_BIGRAM, LM_TRIGRAM, LM_QUADGRAM, USER, CORRECTION
}

/**
 * Unified Suggestion data class as specified
 */
data class Suggestion(
    val text: String,
    val score: Double,
    val source: SuggestionSource,
    val isAutoCommit: Boolean = false,
    val confidence: Double = 0.0
)

/**
 * UnifiedAutocorrectEngine - Single logic layer for typing + swipe + suggestions
 * Refactored to use LanguageResources DTO as single source of truth
 * 
 * Features:
 * - Unified scoring with Katz-backoff (quad→tri→bi→uni)
 * - Keyboard proximity weighting for edit distance
 * - Single API for all prediction types
 * - Thread-safe LanguageResources consumption
 * - Injectable scoring weights
 */
class UnifiedAutocorrectEngine(
    private val context: Context,
    private val multilingualDictionary: MultilingualDictionary,
    private val transliterationEngine: TransliterationEngine? = null,
    private val userDictionaryManager: UserDictionaryManager? = null
) {
    companion object {
        private const val TAG = "UnifiedAutocorrectEngine"
        private val INDIC_LANGUAGES = listOf("hi", "te", "ta", "ml", "bn", "gu", "kn", "pa", "ur")
        private val DEFAULT_FALLBACKS = listOf("I", "The", "You", "We", "It", "Thanks")
        
        // ========== TIMING-BASED AUTOCORRECT THRESHOLDS (Gboard Rule 2) ==========
        const val FAST_TYPING_THRESHOLD_MS = 100L  // Skip autocorrect if faster than this per key
        const val KEYPRESS_HISTORY_SIZE = 5        // Look at last 5 keypresses
        const val SPACE_COMMIT_THRESHOLD_MS = 80L  // If space pressed within 80ms of last key → commit typed word
    }

    // Thread-safe cache for suggestions
    private val suggestionCache = object : LruCache<String, List<Suggestion>>(500) {}
    
    // Current language resources
    @Volatile
    var currentLanguage: String = "en"  // ✅ Made public for FIX 3.3 language comparison
        private set  // Only this class can modify it
    @Volatile
    private var languageResources: LanguageResources? = null
    @Volatile
    private var trieDictionary: MappedTrieDictionary? = null
    
    // ========== TIMING-BASED AUTOCORRECT (Gboard Rule 2) ==========
    // Track keypress timing for fast-typing detection
    @Volatile
    private var lastKeypressTime: Long = 0L
    
    @Volatile
    private var recentKeypressTimes: MutableList<Long> = mutableListOf()
    
    // ✅ V3 PACK Section 5: Word Learning & Auto-Promotion
    // Track correction acceptance counts for auto-promotion (case-insensitive)
    private val correctionAcceptCounts = ConcurrentHashMap<String, Int>()  // Key: "original→corrected" (lowercase)
    
    // ========== NEW: Phase 1 Components ==========
    
    // ML-based swipe decoder for better gesture recognition
    // Note: SwipeDecoderML is now created on-demand in getSwipeDecoder() to ensure dictionary is available
    private var swipeDecoderML: SwipeDecoderML? = null
    
    // Next-word predictor for context-aware suggestions (public for learning integration)
    val nextWordPredictor: NextWordPredictor by lazy {
        NextWordPredictor(context)
    }
    
    // Tunable scoring weights manager
    private val scoringWeights: ScoringWeightsManager by lazy {
        ScoringWeightsManager(context)
    }
    
    // Default QWERTY layout for keyboard proximity scoring (normalized 0.0-1.0 coordinates)
    // Updated to match Android keyboard layout more accurately  
    private val defaultKeyLayout = mapOf(
        'q' to Pair(0.05f, 0.17f), 'w' to Pair(0.15f, 0.17f), 'e' to Pair(0.25f, 0.17f), 'r' to Pair(0.35f, 0.17f),
        't' to Pair(0.45f, 0.17f), 'y' to Pair(0.55f, 0.17f), 'u' to Pair(0.65f, 0.17f), 'i' to Pair(0.75f, 0.17f),
        'o' to Pair(0.85f, 0.17f), 'p' to Pair(0.95f, 0.17f),
        'a' to Pair(0.075f, 0.44f), 's' to Pair(0.175f, 0.44f), 'd' to Pair(0.275f, 0.44f), 'f' to Pair(0.375f, 0.44f),
        'g' to Pair(0.475f, 0.44f), 'h' to Pair(0.575f, 0.44f), 'j' to Pair(0.675f, 0.44f), 'k' to Pair(0.775f, 0.44f),
        'l' to Pair(0.875f, 0.44f),
        'z' to Pair(0.15f, 0.71f), 'x' to Pair(0.25f, 0.71f), 'c' to Pair(0.35f, 0.71f), 'v' to Pair(0.45f, 0.71f),
        'b' to Pair(0.55f, 0.71f), 'n' to Pair(0.65f, 0.71f), 'm' to Pair(0.75f, 0.71f)
    )
    private val keyboardLayouts = ConcurrentHashMap<String, Map<Char, Pair<Float, Float>>>()
    @Volatile
    private var currentLayoutCoordinates: Map<Char, Pair<Float, Float>> = defaultKeyLayout

    private val symSpellCache = ConcurrentHashMap<String, SymSpell>()
    
    private data class SwipeFeedback(var accepted: Int = 0, var rejected: Int = 0)
    private val swipeFeedback = ConcurrentHashMap<String, SwipeFeedback>()
    
    private fun activeLayout(): Map<Char, Pair<Float, Float>> {
        return if (currentLayoutCoordinates.isEmpty()) defaultKeyLayout else currentLayoutCoordinates
    }

    /**
     * Get or create SwipeDecoderML instance with the dictionary
     * This ensures the decoder always has access to the current language's dictionary
     */
    private fun getSwipeDecoder(): SwipeDecoderML? {
        val dict = trieDictionary ?: return null
        
        // Create new decoder if not exists or if dictionary changed
        if (swipeDecoderML == null) {
            swipeDecoderML = SwipeDecoderML(context, activeLayout(), dict)
            Log.d(TAG, "✅ SwipeDecoderML initialized with Beam Search for language: $currentLanguage")
        }
        
        return swipeDecoderML
    }

    private fun loadMappedTrie(lang: String) {
        trieDictionary = try {
            MappedTrieDictionary(context, lang)
        } catch (e: Exception) {
            Log.e(TAG, "Binary dictionary not found for $lang, falling back to LanguageResources maps", e)
            null
        }
        
        // Reset swipeDecoderML so it gets recreated with the new dictionary
        swipeDecoderML = null
    }

    private fun getWordFrequency(word: String, resources: LanguageResources? = languageResources): Int {
        trieDictionary?.let { return it.getFrequency(word) }
        return resources?.words?.get(word) ?: 0
    }

    private fun hasWord(word: String, resources: LanguageResources? = languageResources): Boolean =
        getWordFrequency(word, resources) > 0

    private fun getPrefixCandidates(prefix: String, limit: Int, resources: LanguageResources? = languageResources): List<Pair<String, Int>> {
        val normalized = prefix.lowercase()
        trieDictionary?.let { trie ->
            val matches = trie.getSuggestions(normalized, limit)
            return matches.map { it to trie.getFrequency(it) }
        }

        val words = resources?.words ?: return emptyList()
        return words.entries
            .asSequence()
            .filter { it.key.startsWith(normalized) }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }
    
    // Suggestion callback for real-time suggestion updates
    private var suggestionCallback: ((List<String>) -> Unit)? = null

    @Volatile
    private var cursorMoved = false

    private fun isBlacklisted(original: String, corrected: String): Boolean {
        val manager = userDictionaryManager ?: return false
        return manager.isBlacklisted(original, corrected)
    }

    /**
     * Set language and resources (NEW UNIFIED API)
     * This is the single entry point for language switching
     * 🔥 PERFORMANCE: Prevents reloading same language multiple times
     */
    fun setLanguage(lang: String, resources: LanguageResources) {
        // 🔥 CRITICAL PERFORMANCE FIX: Don't reload if already loaded
        if (currentLanguage == lang && languageResources != null) {
            return  // ✅ Prevents dictionary reload spam
        }
        
        currentLanguage = lang
        languageResources = resources
        loadMappedTrie(lang)
        currentLayoutCoordinates = keyboardLayouts[lang] ?: defaultKeyLayout
        
        // 🔥 PERFORMANCE FIX: Only load SymSpell if NOT already cached
        if (!symSpellCache.containsKey(lang)) {
            symSpellCache[lang] = SymSpellLoader.load(context, lang, mergeSymSpellWords(resources))
            Log.d(TAG, "✅ SymSpell loaded for '$lang' (${resources.words.size} words)")
        } else {
            Log.d(TAG, "✅ SymSpell already cached for '$lang' - reusing")
        }
        
        // 🔥 FIX 3.2 - Only clear cache when language ACTUALLY changes (not on reload)
        // suggestionCache.evictAll() - REMOVED, let cache persist for better performance
        
        // ✅ PERFORMANCE: Reduced logging to once per language load
        Log.d(TAG, "✅ Language '$lang' loaded: ${resources.words.size} words, trie=${trieDictionary != null}")
    }
    
    /**
     * Update the swipe geometry for a given language
     */
    fun updateKeyLayout(language: String, positions: Map<Char, Pair<Float, Float>>) {
        if (positions.isEmpty()) return
        val filtered = positions.filterKeys { it.isLetterOrDigit() }
        if (filtered.isEmpty()) return
        keyboardLayouts[language] = filtered
        if (language == currentLanguage) {
            currentLayoutCoordinates = filtered
        }
        // 🔥 FIX 3.2 - Don't clear cache on layout update, not necessary
        // suggestionCache.evictAll() - REMOVED
        Log.d(TAG, "🧭 Updated swipe layout for $language: keys=${filtered.size}")
    }

    // In UnifiedAutocorrectEngine.kt

private fun mergeSymSpellWords(resources: LanguageResources): Map<String, Int> {
    val merged = HashMap<String, Int>()
    merged.putAll(resources.words)

    // 🚀 FORCE HYDRATION: Ensure we get words even if .txt is missing
    if (merged.isEmpty()) {
        if (trieDictionary != null) {
             Log.d(TAG, "⚠️ .txt dictionary missing. Extracting 15,000 words from .bin...")
             val topWords = trieDictionary?.getTopFrequentWords(15000) ?: emptyMap()
             merged.putAll(topWords)
             
             // If getTopFrequentWords doesn't exist, use the deeper a-z iteration
             if (merged.isEmpty()) {
                Log.d(TAG, "⚠️ Using fallback a-z iteration from Binary Trie...")
                val seed = mutableMapOf<String, Int>()
                // Iterate a-z but deeper, and don't break early
                for (ch in 'a'..'z') {
                    // Fetch top 500 words per letter instead of 16
                    val words = trieDictionary?.getSuggestions(ch.toString(), 500) ?: emptyList()
                    words.forEach { word ->
                        val freq = trieDictionary?.getFrequency(word) ?: 1
                        seed[word] = freq
                    }
                }
                merged.putAll(seed)
             }
        } else {
             // 🚨 EMERGENCY FALLBACK: If both .txt and .bin are missing, add basic words so engine doesn't die
             Log.e(TAG, "❌ NO DICTIONARY FOUND! Using emergency fallback words.")
             listOf("the", "and", "hello", "this", "is", "test", "that", "you", "for", "are", "with", "have", "will", "can", "from", "they", "been", "would", "there", "could").forEach { merged[it] = 255 }
        }
    }

    resources.userWords.forEach { merged.putIfAbsent(it, 1) }
    
    // ✅ PERFORMANCE: Log removed (was printing on every suggestion update)
    return merged
}

    private fun currentSymSpell(): SymSpell? {
        // 🔥 CRITICAL PERFORMANCE FIX: NEVER load SymSpell here - only return cached
        // SymSpell should ONLY be loaded in setLanguage(), not during typing
        val cached = symSpellCache[currentLanguage]
        if (cached == null) {
            Log.w(TAG, "⚠️ SymSpell not cached for $currentLanguage - should have been loaded in setLanguage()")
        }
        return cached
    }
    
    /**
     * Check if engine has a specific language loaded
     */
    fun hasLanguage(lang: String): Boolean {
        return currentLanguage == lang && languageResources != null
    }
    
    /**
     * Get suggestions for prefix (UNIFIED API as specified in requirements)
     * This method name was requested in the task specification
     */
    fun getSuggestionsFor(prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔍 Getting suggestions for prefix: '$prefix'")
        }
        val suggestions = suggestForTyping(prefix, emptyList())
        return suggestions.map { it.text }
    }
    
    /**
     * Get next word predictions (UNIFIED API as specified in requirements)
     * This method name was requested in the task specification
     */
    fun getNextWordPredictions(context: List<String>): List<String> {
        if (context.isEmpty()) return emptyList()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔮 Getting next word predictions for: $context")
        }
        val predictions = nextWord(context, 3)
        return predictions.map { it.text }
    }
    
    /**
     * Get typing suggestions for partial input (NEW UNIFIED API with Phase 1 enhancements)
     */
    fun suggestForTyping(prefix: String, contextWords: List<String>): List<Suggestion> {
        if (prefix.isBlank()) return emptyList()
        val resources = languageResources ?: return emptyList()

        val lower = prefix.lowercase()
        val suggestions = mutableListOf<Suggestion>()

        // 1 - SymSpell candidates (fast corrections only)
        val symResults = currentSymSpell()?.lookup(lower, Verbosity.TOP, maxEditDistance = 2) ?: emptyList()
        val correctionSuggestions = buildSymSpellSuggestions(prefix, lower, contextWords, resources, symResults)
        suggestions.addAll(correctionSuggestions)

        // 2 - Dictionary prefix matches (typing suggestions, no auto-commit)
        val prefixMatches = getPrefixCandidates(lower, 12, resources)
            .map { (candidate, freq) ->
                val score = computeFinalScore(
                    symspellScore = 0.0,
                    frequency = freq,
                    distance = getEditDistance(lower, candidate),
                    candidate = candidate,
                    input = lower,
                    contextWords = contextWords,
                    resources = resources
                )
                Suggestion(
                    text = candidate,
                    score = score,
                    source = SuggestionSource.TYPING,
                    isAutoCommit = false
                )
            }
            .toList()
        suggestions.addAll(prefixMatches)

        // 3 - User dictionary (keep as typing suggestions)
        userDictionaryManager?.getTopWords(20)?.forEach { word ->
            if (word.startsWith(lower)) {
                val freq = getWordFrequency(word, resources).coerceAtLeast(1)
                val score = computeFinalScore(
                    symspellScore = 0.0,
                    frequency = freq,
                    distance = 0,
                    candidate = word,
                    input = lower,
                    contextWords = contextWords,
                    resources = resources
                )
                suggestions.add(
                    Suggestion(
                        text = word,
                        score = score,
                        source = SuggestionSource.USER
                    )
                )
            }
        }

        // 4 - Next word predictor (LM) - simplified for performance
        if (contextWords.isNotEmpty()) {
            val lastWord = contextWords.lastOrNull()?.lowercase()
            if (lastWord != null) {
                val bigramPredictions = resources.bigrams
                    .filterKeys { it.first == lastWord }
                    .entries
                    .sortedByDescending { it.value }
                    .take(2)
                    .map { it.key.second }
                
                bigramPredictions.forEach { word ->
                    val score = computeFinalScore(
                        symspellScore = 0.0,
                        frequency = getWordFrequency(word, resources),
                        distance = getEditDistance(lower, word),
                        candidate = word,
                        input = lower,
                        contextWords = contextWords,
                        resources = resources
                    )
                    suggestions.add(
                        Suggestion(
                            text = word,
                            score = score,
                            source = SuggestionSource.LM_BIGRAM
                        )
                    )
                }
            }
        }

        // 5 - Sort and limit
        return suggestions
            .distinctBy { it.text }
            .sortedByDescending { it.score }
            .take(6)
    }

    private fun buildSymSpellSuggestions(
        originalInput: String,
        lower: String,
        contextWords: List<String>,
        resources: LanguageResources,
        symResults: List<SymSpell.SymSpellResult>
    ): List<Suggestion> {
        if (symResults.isEmpty()) return emptyList()
        if (shouldBlockAutocorrect(originalInput, resources, contextWords)) return emptyList()

        // ✅ GBOARD-LIKE BEHAVIOR: Filter suggestions based on input word status
        val isInCorrections = resources.corrections.containsKey(lower)
        val isInDictionary = hasWord(lower, resources)
        
        // Filter symResults based on Gboard rules
        val filteredResults = symResults.filter { res ->
            when {
                // Rule 1: If word is in corrections list, allow all autocorrect suggestions
                isInCorrections -> true
                // Rule 2: If word is in dictionary, only allow corrections with editDistance > 1 (context-based)
                isInDictionary -> res.distance > 1
                // Rule 3: If word not in dictionary, allow all corrections
                else -> true
            }
        }
        
        if (filteredResults.isEmpty()) return emptyList()

        val inputFreq = getWordFrequency(lower, resources)
        val nextScore = filteredResults.getOrNull(1)?.score ?: Double.NEGATIVE_INFINITY
        val top = filteredResults.first()

        val autoCommitAllowed = top.distance == 1 &&
            top.frequency >= (if (inputFreq <= 0) 2 else inputFreq * 2) &&
            top.score >= nextScore + 0.15 &&
            !(originalInput.firstOrNull()?.isUpperCase() ?: false)

        return filteredResults.take(3).mapIndexed { index, res ->
            val finalScore = computeFinalScore(
                symspellScore = res.score,
                frequency = res.frequency,
                distance = res.distance,
                candidate = res.term,
                input = lower,
                contextWords = contextWords,
                resources = resources
            )
            Suggestion(
                text = res.term,
                score = finalScore,
                source = SuggestionSource.CORRECTION,
                isAutoCommit = index == 0 && autoCommitAllowed,
                confidence = res.score
            )
        }
    }

    /**
     * ✅ V3 PACK Section 4: Gboard V3 Scoring Matrix
     * 
     * This scoring matrix produces the most human-like corrections by balancing:
     * - SymSpell score (spelling similarity): 54%
     * - Keyboard distance (typo proximity): 20%
     * - Frequency (word commonness): 15%
     * - Context (bigram/trigram): 11%
     * 
     * This ensures corrections feel natural and match user intent.
     */
    private fun computeFinalScore(
        symspellScore: Double,
        frequency: Int,
        distance: Int,
        candidate: String,
        input: String,
        contextWords: List<String>,
        resources: LanguageResources
    ): Double {
        val freqScore = ln(frequency.toDouble() + 1)
        val maxLen = max(input.length, candidate.length).coerceAtLeast(1)
        val spatialDist = getEditDistanceWithProximity(input, candidate)
        val keyboardDistanceScore = 1.0 - (spatialDist / maxLen.toDouble())
        val bigramScore = contextWords.lastOrNull()?.let { prev ->
            val freq = resources.bigrams[Pair(prev.lowercase(), candidate.lowercase())] ?: 0
            ln(freq.toDouble() + 1)
        } ?: 0.0

        // ✅ Gboard V3 Weights: 0.54 + 0.20 + 0.15 + 0.11 = 1.00
        return (symspellScore * 0.54) +
            (keyboardDistanceScore * 0.20) +
            (freqScore * 0.15) +
            (bigramScore * 0.11)
    }
    
    private fun hasSymbols(word: String): Boolean {
        return word.any { !it.isLetter() && it != '\'' }
    }

    private fun containsUrlOrEmail(word: String): Boolean {
        val lowered = word.lowercase()
        return lowered.contains("@") ||
            lowered.contains(".com") ||
            lowered.contains(".net") ||
            lowered.contains(".in") ||
            lowered.contains("://")
    }

    private fun containsEmoji(word: String): Boolean {
        return word.any { ch ->
            val type = Character.getType(ch)
            type == Character.SURROGATE.toInt() ||
                type == Character.OTHER_SYMBOL.toInt() ||
                runCatching { Character.getName(ch.code)?.contains("EMOJI", ignoreCase = true) == true }.getOrDefault(false)
        }
    }

    private fun shouldBlockAutocorrect(
        word: String,
        resources: LanguageResources,
        contextWords: List<String>
    ): Boolean {
        val lower = word.lowercase()
        if (word.length < 2) return true
        
        // ✅ GBOARD V3: Improved blocking logic
        // 1. If word is in corrections list → DON'T block (allow autocorrect even if real word)
        val isInCorrections = resources.corrections.containsKey(lower)
        if (isInCorrections) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Allowing autocorrect: '$word' is in corrections list")
            }
            return false  // Don't block
        }
        
        // 2. If word is in dictionary → DON'T block here (will be handled by editDistance check in buildSymSpellSuggestions)
        //    This allows context-based corrections for real words with editDistance > 1
        val isInDictionary = hasWord(lower, resources)
        if (isInDictionary) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ Word '$word' is in dictionary - will check editDistance for context-based correction")
            }
            return false  // Don't block yet, let editDistance check decide
        }
        
        // 3. Block special cases
        if (hasSymbols(word) || word.any { it.isDigit() }) return true
        // ... other checks ...

        return false
    }

    /**
     * Get best autocorrect suggestion (NEW UNIFIED API)
     * Implements Gboard-style safety filters and Explicit Corrections first.
     */
    fun autocorrect(input: String, contextWords: List<String>): Suggestion? {
        val resources = languageResources ?: return null
        val lower = input.lowercase()
        
        // ==============================================================================
        // 🚀 CRITICAL UPDATE: EXPLICIT CORRECTIONS (Your "Clean & Perfect" Logic)
        // This block forces the engine to respect your cleaned binary file above all else.
        // ==============================================================================
        val explicitFix = resources.corrections[lower]
        if (explicitFix != null) {
            // Logic: User typed "teh". Found "the" in safe file.
            // Action: Force auto-commit immediately.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "⚡ Fast-track correction found: '$input' -> '$explicitFix'")
            }
            return Suggestion(
                text = explicitFix,
                score = 1.0,             // Max score to ensure it wins
                source = SuggestionSource.CORRECTION,
                isAutoCommit = true,     // Force the replacement
                confidence = 1.0
            )
        }
        // ==============================================================================
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔍 Autocorrect called for: '$input'")
        }

        if (shouldBlockAutocorrect(input, resources, contextWords)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🚫 Autocorrect blocked for: '$input'")
            }
            return null
        }
        if (userDictionaryManager?.isBlacklisted(input, input) == true) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🚫 Word blacklisted: '$input'")
            }
            return null
        }

        val symResults = currentSymSpell()?.lookup(lower, Verbosity.TOP, maxEditDistance = 2) ?: emptyList()
        if (symResults.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🔍 No SymSpell results for: '$input'")
            }
            return null
        }

        val inputFreq = getWordFrequency(lower, resources)
        val top = symResults.first()
        val nextScore = symResults.getOrNull(1)?.score ?: Double.NEGATIVE_INFINITY

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔍 Best suggestion: '${top.term}' (distance=${top.distance}, freq=${top.frequency}, score=${top.score})")
        }

        // ✅ PATCH 3: Improved frequency threshold (Gboard V3)
        // Old: top.frequency >= inputFreq * 2 (too strict)
        // New: top.frequency > inputFreq (more lenient, allows valid corrections)
        val frequencySatisfied = top.frequency > inputFreq
        val autoCommitAllowed = top.distance == 1 &&
            frequencySatisfied &&
            top.score >= nextScore + 0.15 &&
            !input.firstOrNull()?.isUpperCase().orFalse()

        val finalScore = computeFinalScore(
            symspellScore = top.score,
            frequency = top.frequency,
            distance = top.distance,
            candidate = top.term,
            input = lower,
            contextWords = contextWords,
            resources = resources
        )

        val suggestion = Suggestion(
            text = top.term,
            score = finalScore,
            source = SuggestionSource.CORRECTION,
            isAutoCommit = autoCommitAllowed,
            confidence = top.score
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "✅ Autocorrect suggestion: '$input' → '${suggestion.text}' (autoCommit=${suggestion.isAutoCommit})")
        }
        return suggestion
    }
    
    /**
     * ✅ PATCH 4: Dynamic confidence threshold based on word length (Gboard V3)
     * Shorter words require higher confidence to avoid false corrections
     * Longer words can use lower confidence (more room for typos)
     */
    fun requiredConfidence(word: String): Double {
        return when {
            word.length <= 3 -> 0.85  // Short words: very high confidence (e.g., "the", "is", "to")
            word.length <= 6 -> 0.75  // Medium words: high confidence (e.g., "hello", "world")
            else -> 0.65              // Long words: moderate confidence (e.g., "keyboard", "autocorrect")
        }
    }
    
    // ========== TIMING-BASED AUTOCORRECT METHODS (Gboard Rule 2) ==========
    
    /**
     * Record a keypress event (call this on each key typed)
     * Used for fast-typing detection
     */
    fun recordKeypress() {
        val now = System.currentTimeMillis()
        lastKeypressTime = now
        
        synchronized(recentKeypressTimes) {
            recentKeypressTimes.add(now)
            // Keep only recent keypresses
            while (recentKeypressTimes.size > KEYPRESS_HISTORY_SIZE) {
                recentKeypressTimes.removeAt(0)
            }
        }
    }

    fun markCursorMoved() {
        cursorMoved = true
    }

    fun resetCursorMovedFlag() {
        cursorMoved = false
    }
    
    /**
     * Check if user is typing fast (Gboard behavior: skip autocorrect for fast typing)
     * @return true if user is typing fast and autocorrect should be skipped
     */
    fun isTypingFast(): Boolean {
        synchronized(recentKeypressTimes) {
            if (recentKeypressTimes.size < 3) return false  // Need at least 3 keypresses to determine
            
            // Calculate average interval between keypresses
            var totalInterval = 0L
            for (i in 1 until recentKeypressTimes.size) {
                totalInterval += recentKeypressTimes[i] - recentKeypressTimes[i - 1]
            }
            val avgInterval = totalInterval / (recentKeypressTimes.size - 1)
            
            val isFast = avgInterval < FAST_TYPING_THRESHOLD_MS
            if (isFast && BuildConfig.DEBUG) {
                Log.d(TAG, "⚡ Fast typing detected: avg interval=${avgInterval}ms < ${FAST_TYPING_THRESHOLD_MS}ms")
            }
            return isFast
        }
    }
    
    /**
     * Check if space was pressed quickly after last character (commit typed word, no correction)
     * @return true if space was pressed fast and autocorrect should be skipped
     */
    fun isSpacePressedFast(): Boolean {
        val now = System.currentTimeMillis()
        val interval = now - lastKeypressTime
        val isFast = interval < SPACE_COMMIT_THRESHOLD_MS
        if (isFast && BuildConfig.DEBUG) {
            Log.d(TAG, "⚡ Fast space: ${interval}ms < ${SPACE_COMMIT_THRESHOLD_MS}ms → skip autocorrect")
        }
        return isFast
    }
    
    /**
     * Autocorrect with timing check (Gboard-style)
     * Use this instead of autocorrect() to respect fast-typing behavior
     */
    fun autocorrectWithTiming(input: String, context: List<String>): Suggestion? {
        // If user is typing fast or pressed space quickly, skip autocorrect
        if (isTypingFast() || isSpacePressedFast()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "⚡ Skipping autocorrect for '$input' due to fast typing")
            }
            return null
        }
        return autocorrect(input, context)
    }
    
    /**
     * Clear timing history (call when input field changes or word is committed)
     */
    fun clearTimingHistory() {
        synchronized(recentKeypressTimes) {
            recentKeypressTimes.clear()
        }
        lastKeypressTime = 0L
        cursorMoved = false
    }
    
    // OLD findSpellingCorrections method removed - now using SymSpell for fast corrections
    
    /**
     * Get next word predictions (NEW UNIFIED API with Phase 1 ML predictor)
     * 🔥 PERFORMANCE: This is expensive - should only run after SPACE, not during typing
     */
    fun nextWord(context: List<String>, k: Int = 3): List<Suggestion> {
        if (context.isEmpty()) return emptyList()
        val resources = languageResources ?: return emptyList()
        
        val cacheKey = "nextword:${context.joinToString(",")}:$k"
        suggestionCache.get(cacheKey)?.let { return it }
        
        val suggestions = mutableListOf<Suggestion>()
        
        try {
            // Log removed for performance (was firing too frequently)
            
            // Use Katz-backoff: quad→tri→bi→uni
            val contextWords = context.takeLast(3) // Max 3-word context for quadgrams
            
            // Try quadgrams if available and we have 3+ context words
            if (resources.quadgrams != null && contextWords.size >= 3) {
                val quadCandidates = getQuadgramPredictions(resources, contextWords, k * 2)
                quadCandidates.forEach { (word, freq) ->
                    val score = scoringWeights.lmWeight * ln(freq.toDouble() + 1) * 1.2 // Bonus for 4-grams
                    suggestions.add(Suggestion(word, score, SuggestionSource.LM_QUADGRAM))
                }
            }
            
            // Try trigrams if we have 2+ context words and need more suggestions
            if (contextWords.size >= 2 && suggestions.size < k) {
                val triCandidates = getTrigramPredictions(resources, contextWords, k * 2)
                triCandidates.forEach { (word, freq) ->
                    // Only add if not already from quadgrams
                    if (!suggestions.any { it.text == word }) {
                        val score = scoringWeights.lmWeight * ln(freq.toDouble() + 1) * 1.1
                        suggestions.add(Suggestion(word, score, SuggestionSource.LM_TRIGRAM))
                    }
                }
            }
            
            // Try bigrams as fallback
            if (suggestions.size < k) {
                val lastWord = contextWords.last()
                val biCandidates = getBigramPredictions(resources, lastWord, k * 2)
                biCandidates.forEach { (word, freq) ->
                    if (!suggestions.any { it.text == word }) {
                        val score = scoringWeights.lmWeight * ln(freq.toDouble() + 1)
                        suggestions.add(Suggestion(word, score, SuggestionSource.LM_BIGRAM))
                    }
                }
            }
            
            // ========== Phase 1: Async NextWordPredictor Integration ==========
            // 🔥 PERFORMANCE FIX: Disabled ML predictor during typing - too expensive
            // This should only run after SPACE key, not during word typing
            // Launch async prediction that can update UI when ready
            /* DISABLED FOR PERFORMANCE
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val mlPredictions = nextWordPredictor.predictNext(contextWords, k)
                    // These predictions can be used to update the UI asynchronously
                    // or merged with existing suggestions in Phase 2
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting ML predictions", e)
                }
            }
            */
            
            val result = suggestions
                .sortedByDescending { it.score }
                .take(k)

            if (result.isNotEmpty()) {
                // Log removed for performance
                suggestionCache.put(cacheKey, result)
                return result
            }

            val fallback = fallbackSuggestions(context, k)
            if (fallback.isNotEmpty()) {
                // Log removed for performance
                suggestionCache.put(cacheKey, fallback)
            }
            return fallback
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting next word predictions", e)
            return fallbackSuggestions(context, k)
        }
    }

    /**
     * Provide fallback suggestions leveraging loaded n-gram data.
     * Used when primary pipelines (Firebase predictions / typed completions) are empty.
     */
    fun fallbackSuggestions(
        context: List<String>,
        limit: Int = 3,
        exclude: Set<String> = emptySet()
    ): List<Suggestion> {
        val resources = languageResources ?: return DEFAULT_FALLBACKS.take(limit).map {
            Suggestion(it, 0.0, SuggestionSource.LM_BIGRAM)
        }

        val normalizedContext = context.map { it.lowercase() }.filter { it.isNotBlank() }
        val suggestions = mutableListOf<Suggestion>()
        val seen = exclude.map { it.lowercase() }.toMutableSet()

        fun appendCandidates(candidates: List<Pair<String, Int>>, source: SuggestionSource, boost: Double = 0.0) {
            candidates.forEach { (word, freq) ->
                if (seen.add(word.lowercase())) {
                    val score = (ln(freq.toDouble() + 1) * scoringWeights.lmWeight) + boost
                    suggestions.add(Suggestion(word, score, source))
                }
            }
        }

        if (normalizedContext.size >= 2) {
            appendCandidates(
                getTrigramPredictions(resources, normalizedContext, limit * 2),
                SuggestionSource.LM_TRIGRAM,
                boost = 0.2
            )
        }

        if (suggestions.size < limit && normalizedContext.isNotEmpty()) {
            val lastWord = normalizedContext.last()
            appendCandidates(
                getBigramPredictions(resources, lastWord, limit * 2),
                SuggestionSource.LM_BIGRAM
            )
        }

        if (suggestions.size < limit) {
            val topWords = resources.words.entries
                .asSequence()
                .filter { it.key.length > 1 }
                .filter { seen.add(it.key.lowercase()) }
                .sortedBy { it.value }
                .take(limit * 2)
                .map { entry ->
                    Suggestion(entry.key, ln(entry.value.toDouble() + 1), SuggestionSource.TYPING)
                }
            suggestions.addAll(topWords)
        }

        if (suggestions.size < limit) {
            suggestions.addAll(
                DEFAULT_FALLBACKS
                    .asSequence()
                    .filter { seen.add(it.lowercase()) }
                    .map { Suggestion(it, 0.0, SuggestionSource.LM_BIGRAM) }
            )
        }

        return suggestions.take(limit)
    }

    /**
     * Get global top words for the active language.
     */
    fun getTopWords(limit: Int = 5, exclude: Set<String> = emptySet()): List<Suggestion> {
        val resources = languageResources ?: return DEFAULT_FALLBACKS.take(limit).map {
            Suggestion(it, 0.0, SuggestionSource.TYPING)
        }
        val excludeLower = exclude.map { it.lowercase() }.toSet()
        return resources.words.entries
            .asSequence()
            .filter { it.key.length > 1 }
            .filter { !excludeLower.contains(it.key.lowercase()) }
            .sortedBy { it.value }
            .take(limit)
            .map { entry -> Suggestion(entry.key, ln(entry.value.toDouble() + 1), SuggestionSource.TYPING) }
            .toList()
    }
    
    /**
     * Get swipe suggestions (V6 - ML decoder + Contextual Re-ranking)
     * Takes ML results and re-ranks using N-gram context (bigrams/trigrams)
     * This fixes "I love tui" -> "I love you" by using context
     */
    fun suggestForSwipe(path: SwipePath, context: List<String>): List<Suggestion> {
        val cacheKey = "swipe:${path.hashCode()}:${context.joinToString(",")}"
        suggestionCache.get(cacheKey)?.let { return it }
        
        try {
            val decoder = getSwipeDecoder()
            val beamSearchCandidates = decoder?.decode(path) ?: emptyList()
            
            // FIXED: Takes ML candidates and Re-ranks them using Context (Bigrams/Trigrams)
            if (beamSearchCandidates.isNotEmpty()) {
                val resources = languageResources ?: return emptyList()
                
                Log.d(TAG, "🚀 V6 Beam Search Raw: ${beamSearchCandidates.take(5)}")
                
                // 1. Convert ML results into Metrics for Ranking
                val metrics = beamSearchCandidates.map { (word, score) ->
                    SwipeMetrics(
                        word = word,
                        pathScore = score, // This is the spatial score from SwipeDecoderML
                        proximity = 0.0,   // Already handled inside ML score
                        editDistance = 0   // Not needed for ML candidates
                    )
                }
                
                // 2. Apply Contextual Re-ranking (Uses "I love" -> "you")
                // This applies the weights: wLM (Context), wFreq, etc.
                val rankedSuggestions = rankSwipeCandidates(resources, context, metrics, limit = 10)
                
                Log.d(TAG, "✅ V6 Re-ranked Contextual: ${rankedSuggestions.take(3).map { "${it.text}(${String.format("%.2f", it.score)})" }}")
                
                suggestionCache.put(cacheKey, rankedSuggestions)
                return rankedSuggestions
            }
            
            // No fallback - if decoder fails, return empty
            Log.d(TAG, "⚠️ V6 Beam Search returned empty")
            return emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting swipe suggestions", e)
            return emptyList()
        }
    }
    
    /**
     * NEW: Simplified suggestForSwipe interface for UnifiedKeyboardView
     * Takes key sequence and normalized path directly
     */
    fun suggestForSwipe(sequence: List<Int>, normalizedPath: List<Pair<Float, Float>>): List<String> {
        try {
            // Convert to SwipePath format
            val swipePath = SwipePath(normalizedPath)
            
            // Get suggestions using existing logic
            val suggestions = suggestForSwipe(swipePath, emptyList())
            
            Log.d(TAG, "🔄 Swipe suggestions for sequence $sequence: ${suggestions.map { it.text }}")
            
            return suggestions.map { it.text }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in simplified suggestForSwipe", e)
            return emptyList()
        }
    }
    
    // ========== UNIFIED SCORING SYSTEM ==========
    
    /**
     * Calculate unified score with Katz-backoff and all factors (Phase 1: Using dynamic weights)
     * Final: score = w1*edit + w2*lm + w3*user + w4*correction + w5*swipe + w6*context
     */
    private fun calculateUnifiedScore(
        candidate: String,
        input: String,
        editDistance: Int,
        context: List<String>,
        source: SuggestionSource,
        swipePathScore: Double = 0.0
    ): Double {
        val resources = languageResources ?: return 0.0
        
        var score = 0.0
        
        // Edit distance penalty (with keyboard proximity) - using dynamic weight
        val editPenalty = editDistance * scoringWeights.editWeight
        score -= editPenalty
        
        // Language model score (Katz-backoff) - using dynamic weight
        val lmScore = getLMScore(candidate, context, resources)
        score += lmScore * scoringWeights.lmWeight
        
        // User dictionary boost - using dynamic weight
        if (resources.userWords.contains(candidate)) {
            score += scoringWeights.userWeight
            
            // Phase 1: Personalized frequency from UserDictionaryManager
            val userFreq = userDictionaryManager?.getWordCount(candidate) ?: 0
            if (userFreq > 0) {
                val personalBoost = ln(userFreq.toDouble() + 1) * 0.5
                score += personalBoost
                Log.d(TAG, "👤 Personal boost for '$candidate': +$personalBoost (used $userFreq times)")
            }
        }
        
        // Correction boost - using dynamic weight
        if (resources.corrections.containsValue(candidate)) {
            score += scoringWeights.correctionWeight
        }
        
        // Swipe path likelihood - using dynamic weight
        if (source == SuggestionSource.SWIPE) {
            score += swipePathScore * scoringWeights.swipeWeight
        }
        
        // Word frequency boost (base signal) - using dynamic weight
        val freq = getWordFrequency(candidate, resources).let { if (it == 0) Int.MAX_VALUE else it }
        score += ln(1.0 / (freq + 1)) * scoringWeights.frequencyWeight // Lower frequency rank = higher score
        
        // Phase 1: Context-aware boost
        if (context.isNotEmpty()) {
            val contextBoost = getContextualBoost(candidate, context, resources)
            score += contextBoost * scoringWeights.contextWeight
        }
        
        return score
    }
    
    /**
     * Calculate contextual boost based on n-gram evidence
     * Higher boost if word fits well in current context
     */
    private fun getContextualBoost(candidate: String, context: List<String>, resources: LanguageResources): Double {
        if (context.isEmpty()) return 0.0
        
        val candidateLower = candidate.lowercase()
        var boost = 0.0
        
        // Bigram boost
        if (context.isNotEmpty()) {
            val prev = context.last().lowercase()
            val bigramFreq = resources.bigrams[Pair(prev, candidateLower)] ?: 0
            if (bigramFreq > 0) {
                boost += ln(bigramFreq.toDouble() + 1) * 0.5
            }
        }
        
        // Trigram boost (stronger than bigram)
        if (context.size >= 2) {
            val prev2 = context[context.size - 2].lowercase()
            val prev1 = context[context.size - 1].lowercase()
            val trigramFreq = resources.trigrams[Triple(prev2, prev1, candidateLower)] ?: 0
            if (trigramFreq > 0) {
                boost += ln(trigramFreq.toDouble() + 1) * 0.8
            }
        }
        
        return boost
    }
    
    /**
     * Get language model score using Katz-backoff
     */
    private fun getLMScore(candidate: String, context: List<String>, resources: LanguageResources): Double {
        if (context.isEmpty()) return 0.0
        
        val contextWords = context.takeLast(3)
        
        // Try quadgrams first (if available)
        if (resources.quadgrams != null && contextWords.size >= 3) {
            val quadgramKey = listOf(contextWords[0], contextWords[1], contextWords[2], candidate)
            resources.quadgrams[quadgramKey]?.let { freq ->
                return ln(freq.toDouble() + 1) * 1.2 // Bonus for 4-grams
            }
        }
        
        // Try trigrams
        if (contextWords.size >= 2) {
            val trigramKey = Triple(contextWords[contextWords.size-2], contextWords.last(), candidate)
            resources.trigrams[trigramKey]?.let { freq ->
                return ln(freq.toDouble() + 1) * 1.1
            }
        }
        
        // Fall back to bigrams
        val bigramKey = Pair(contextWords.last(), candidate)
        resources.bigrams[bigramKey]?.let { freq ->
            return ln(freq.toDouble() + 1)
        }
        
        return 0.0 // No n-gram evidence
    }
    
    /**
     * Calculate Euclidean distance between two keys on the normalized layout.
     * Returns 0.0 if same key, 1.0 if far, ~0.1-0.2 if neighbors.
     */
    private fun getKeyDistance(char1: Char, char2: Char): Double {
        if (char1 == char2) return 0.0

        val layout = activeLayout()
        // High penalty (1.0) if key is not in layout (e.g., symbols vs letters)
        val pos1 = layout[char1] ?: return 1.0
        val pos2 = layout[char2] ?: return 1.0

        val dx = pos1.first - pos2.first
        val dy = pos1.second - pos2.second
        return sqrt(dx * dx + dy * dy).toDouble()
    }

    /**
     * Spatial Edit Distance.
     * Costs are determined by physical distance on the keyboard.
     */
    private fun getSpatialEditDistance(source: String, target: String): Double {
        val n = source.length
        val m = target.length
        if (n == 0) return m.toDouble()
        if (m == 0) return n.toDouble()

        // Use 2 rows to save memory
        var prevRow = DoubleArray(m + 1)
        var currRow = DoubleArray(m + 1)

        for (j in 0..m) prevRow[j] = j.toDouble()

        for (i in 1..n) {
            currRow[0] = i.toDouble()
            val sChar = source[i - 1]

            for (j in 1..m) {
                val tChar = target[j - 1]

                // COST LOGIC:
                // If keys are physically close (distance < 0.18), substitution cost is LOW (0.4).
                // If keys are far, substitution cost is HIGH (1.0).
                val cost = if (sChar == tChar) {
                    0.0
                } else {
                    val dist = getKeyDistance(sChar, tChar)
                    if (dist < 0.18) 0.4 else 1.0
                }

                currRow[j] = minOf(
                    prevRow[j] + 1.0,       // Deletion
                    currRow[j - 1] + 1.0,   // Insertion
                    prevRow[j - 1] + cost   // Substitution (Spatial)
                )

                // Transposition Check (swapping adjacent chars: 'hte' -> 'the')
                if (i > 1 && j > 1 && source[i-1] == target[j-2] && source[i-2] == target[j-1]) {
                     currRow[j] = minOf(currRow[j], prevRow[j-2] + 0.4) // Low cost for transposition
                }
            }
            // Swap rows
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[m]
    }

    private fun getEditDistanceWithProximity(input: String, candidate: String): Double {
        // If no layout loaded, fallback to standard int distance
        if (currentLayoutCoordinates.isEmpty()) {
            return getEditDistance(input, candidate).toDouble()
        }
        return getSpatialEditDistance(input, candidate)
    }
    
    // ========== N-GRAM PREDICTION HELPERS ==========
    
    /**
     * Get quadgram predictions
     */
    private fun getQuadgramPredictions(resources: LanguageResources, context: List<String>, limit: Int): List<Pair<String, Int>> {
        val quadgrams = resources.quadgrams ?: return emptyList()
        if (context.size < 3) return emptyList()
        
        val prefix = listOf(context[0], context[1], context[2])
        
        return quadgrams
            .filterKeys { it.take(3) == prefix }
            .entries
            .map { Pair(it.key[3], it.value) }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Get trigram predictions
     */
    private fun getTrigramPredictions(resources: LanguageResources, context: List<String>, limit: Int): List<Pair<String, Int>> {
        if (context.size < 2) return emptyList()
        
        val prefix = Pair(context[context.size-2], context.last())
        
        return resources.trigrams
            .filterKeys { it.first == prefix.first && it.second == prefix.second }
            .entries
            .map { Pair(it.key.third, it.value) }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Get bigram predictions
     */
    private fun getBigramPredictions(resources: LanguageResources, lastWord: String, limit: Int): List<Pair<String, Int>> {
        return resources.bigrams
            .filterKeys { it.first == lastWord }
            .entries
            .map { Pair(it.key.second, it.value) }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Decode swipe path to candidate words with proper lattice decoding
     */
    private fun decodeSwipePath(path: SwipePath, resources: LanguageResources, context: List<String>): List<Pair<String, Double>> {
        if (path.points.size < 2) return emptyList()
        
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "[Swipe] Decoding path with ${path.points.size} points")
            }
            
            // 1) From points → most-likely key sequence (and alternates per point)
            val lattice: List<List<Char>> = candidatesForEachPoint(path.points, radiusPx = 0.15f)
            if (BuildConfig.DEBUG) {
                val latticeSizes = lattice.map { it.size }
                Log.d(TAG, "[Swipe] lattice sizes: $latticeSizes points=${path.points.size}")
            }
            
            // 2) Build fuzzy prefixes from the lattice (beam search over 8-15 steps)
            val fuzzyPrefixes: List<String> = beamDecode(lattice, beamWidth = 8, maxLen = 15)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "[Swipe] beam top prefixes: ${fuzzyPrefixes.take(8).joinToString(", ")}")
            }
            
            // 3) Query dictionary with those prefixes (NOT common_words), merge unique words
            val raw = mutableSetOf<String>()
            for (p in fuzzyPrefixes) {
                if (p.length >= 2) { // Only meaningful prefixes
                    val prefixCandidates = getPrefixCandidates(p.lowercase(), 300, resources).map { it.first }
                    raw.addAll(prefixCandidates)
                }
            }
            
            // 4) If no candidates from fuzzy prefixes, try collapsed sequence directly
            val snappedSequence = snapToKeys(path.points)
            val collapsedSequence = collapseRepeats(snappedSequence)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "[Swipe] snapped='$snappedSequence' collapsed='$collapsedSequence'")
            }
            
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "[Swipe] No fuzzy candidates, trying collapsed sequence: '$collapsedSequence'")
                }
                val directCandidates = getPrefixCandidates(collapsedSequence.lowercase(), 50, resources)
                    .map { it.first }
                raw.addAll(directCandidates)
            }
            
            // 5) Still empty? Try proximity fallback with edit distance
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "[Swipe] Trying proximity fallback for: '$collapsedSequence'")
                    Log.d(TAG, "[Swipe] Dictionary source = ${if (trieDictionary != null) "binary trie" else "in-memory map (${resources.words.size})"}")
                }
                
                // Try a broader search first - longer sequences often have more errors
                val editThreshold = if (collapsedSequence.length >= 6) 3 else 2
                val close = if (trieDictionary != null) {
                    getPrefixCandidates(collapsedSequence.take(2).lowercase(), 120, resources)
                        .map { it.first }
                        .filter { word ->
                            val editDistance = getEditDistance(collapsedSequence, word)
                            editDistance <= editThreshold && word.length >= 3
                        }
                        .sortedBy { getEditDistance(collapsedSequence, it) }
                        .take(15)
                } else {
                    resources.words.keys
                        .filter { word -> 
                            val editDistance = getEditDistance(collapsedSequence, word)
                            editDistance <= editThreshold && word.length >= 3 // At least 3 letter words
                        }
                        .sortedBy { getEditDistance(collapsedSequence, it) } // Sort by distance
                        .take(15)
                }
                raw.addAll(close)
                Log.w(TAG, "[Swipe] Using proximity fallback for '$collapsedSequence' (threshold=$editThreshold): ${close.take(5)}")
                
                // If still empty, try some common English words that are similar length
                if (close.isEmpty()) {
                    val commonWords = if (trieDictionary != null) {
                        getPrefixCandidates(collapsedSequence.first().toString(), 40, resources)
                            .map { it.first }
                            .filter { it.length == collapsedSequence.length && it.all { c -> c.isLetter() } }
                            .take(10)
                    } else {
                        resources.words.keys.filter { 
                            it.length == collapsedSequence.length && 
                            it.all { c -> c.isLetter() } 
                        }.take(10)
                    }
                    Log.d(TAG, "[Swipe] Trying same-length words: ${commonWords.take(5)}")
                    raw.addAll(commonWords)
                }
            }
            
            // 6) Additional fallback: adjacency expansion if still empty
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                Log.d(TAG, "[Swipe] Trying adjacency expansion for: '$collapsedSequence'")
                val expanded = expandByAdjacency(collapsedSequence, maxVariants = 20)
                for (variant in expanded) {
                    val variantCandidates = getPrefixCandidates(variant.lowercase(), 20, resources)
                        .map { it.first }
                    raw.addAll(variantCandidates)
                    if (raw.size >= 30) break // Don't let it explode
                }
            }
            
            var rawCandidates = raw.toList()
            Log.d(TAG, "[Swipe] rawCandidates: ${rawCandidates.size} (first 10: ${rawCandidates.take(10).joinToString(", ")})")
            
            // 7) Last resort: if still empty, return collapsed sequence as a candidate
            if (rawCandidates.isEmpty() && collapsedSequence.length >= 2) {
                rawCandidates = listOf(collapsedSequence)
                Log.w(TAG, "[Swipe] No dictionary matches, using collapsed fallback: '$collapsedSequence'")
            }
            
            // 8) Calculate metrics for each candidate
            val metrics: List<SwipeMetrics> = rawCandidates.map { word ->
                SwipeMetrics(
                    word = word,
                    pathScore = pathLikelihood(path.points, word),
                    proximity = avgKeyDistance(path.points, word),
                    editDistance = getEditDistance(collapsedSequence, word)  // Compare against collapsed, not raw snapped
                )
            }
            
            // 9) Use unified ranking with proper metrics
            val final = rankSwipeCandidates(resources, context, metrics, limit = 10)
            
            return final.map { Pair(it.text, it.score) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in swipe path decoding", e)
            return emptyList()
        }
    }
    
    /**
     * Estimate letter from normalized point on keyboard
     */
    private fun estimateLetterFromPoint(point: Pair<Float, Float>): String {
        // Simplified keyboard layout estimation
        val (x, y) = point
        val layout = activeLayout()
        
        // Find closest key
        var closestKey = layout.keys.firstOrNull() ?: 'a'
        var minDistance = Float.MAX_VALUE
        
        layout.forEach { (key, pos) ->
            val distance = sqrt((x - pos.first).pow(2) + (y - pos.second).pow(2))
            if (distance < minDistance) {
                minDistance = distance
                closestKey = key
            }
        }
        
        return closestKey.toString()
    }
    
    /**
     * Find candidate keys for each touch point (lattice decoding)
     * Improved to use normalized coordinates and proper radius
     */
    private fun candidatesForEachPoint(points: List<Pair<Float, Float>>, radiusPx: Float): List<List<Char>> {
        val radius = 0.15f // Normalized radius - increased for better key detection
        val layout = activeLayout().ifEmpty { defaultKeyLayout }
        
        return points.map { point ->
            val (x, y) = point
            val candidates = mutableListOf<Char>()
            
            // Find keys within radius using Euclidean distance on normalized coordinates
            layout.forEach { (key, pos) ->
                val distance = sqrt((x - pos.first).pow(2) + (y - pos.second).pow(2))
                if (distance <= radius) {
                    candidates.add(key)
                }
            }
            
            // If no keys in radius, find closest + nearby keys
            if (candidates.isEmpty()) {
                val distances = layout.map { (key, pos) ->
                    val distance = sqrt((x - pos.first).pow(2) + (y - pos.second).pow(2))
                    Pair(key, distance)
                }.sortedBy { it.second }
                
                // Add closest key
                candidates.add(distances.first().first)
                
                // Add any other keys within 1.5x the closest distance  
                val threshold = distances.first().second * 1.5f
                distances.drop(1).forEach { (key, distance) ->
                    if (distance <= threshold && candidates.size < 3) {
                        candidates.add(key)
                    }
                }
            }
            
            Log.d(TAG, "[Lattice] point($x,$y) → keys=[${candidates.joinToString("")}]")
            candidates.distinct()
        }
    }
    
    /**
     * Beam search decoding over key lattice with improved filtering
     */
    private fun beamDecode(lattice: List<List<Char>>, beamWidth: Int, maxLen: Int): List<String> {
        var beam = listOf("" to 0.0) // (prefix, logProb)
        
        for (choices in lattice.take(maxLen)) {
            val next = mutableListOf<Pair<String, Double>>()
            for ((prefix, logProb) in beam) {
                for (char in choices) {
                    val newPrefix = prefix + char
                    // Add penalty for excessive repetition (like "rrrrr")
                    val repetitionPenalty = if (newPrefix.length >= 3 && 
                        newPrefix.takeLast(3).toSet().size == 1) -2.0 else 0.0
                    
                    next.add(newPrefix to (logProb - 0.1 + repetitionPenalty))
                }
            }
            beam = next.sortedByDescending { it.second }.take(beamWidth)
        }
        
        // Filter out candidates with excessive repetition and return diverse options
        return beam.map { it.first }
            .distinct()
            .filter { candidate ->
                candidate.length >= 2 && 
                // Filter out strings with >50% repeated characters
                candidate.length <= 2 || candidate.toSet().size.toDouble() / candidate.length > 0.5
            }
            .take(beamWidth)
    }
    
    /**
     * Snap path to most likely key sequence
     */
    private fun snapToKeys(points: List<Pair<Float, Float>>): String {
        return points.map { point ->
            estimateLetterFromPoint(point)[0]
        }.joinToString("")
    }
    
    /**
     * Calculate path likelihood for a word
     */
    private fun pathLikelihood(points: List<Pair<Float, Float>>, word: String): Double {
        if (points.isEmpty() || word.isEmpty()) return 0.0
        
        // Simple scoring based on how well the word letters align with touch points
        val wordChars = word.lowercase().toCharArray()
        val layout = activeLayout()
        var totalScore = 0.0
        var alignedPoints = 0
        
        // Sample points evenly across the word length
        val sampleIndices = if (points.size <= wordChars.size) {
            points.indices.toList()
        } else {
            (0 until wordChars.size).map { i ->
                (i * (points.size - 1).toFloat() / (wordChars.size - 1)).toInt()
            }
        }
        
        for (i in sampleIndices.indices) {
            if (i < wordChars.size && sampleIndices[i] < points.size) {
                val point = points[sampleIndices[i]]
                val expectedChar = wordChars[i]
                val expectedPos = layout[expectedChar]
                
                if (expectedPos != null) {
                    val distance = sqrt((point.first - expectedPos.first).pow(2) + (point.second - expectedPos.second).pow(2))
                    val score = maxOf(0.0, 1.0 - distance * 2.0) // Closer = higher score
                    totalScore += score
                    alignedPoints++
                }
            }
        }
        
        return if (alignedPoints > 0) totalScore / alignedPoints else 0.0
    }
    
    /**
     * Calculate average key distance for proximity scoring
     */
    private fun avgKeyDistance(points: List<Pair<Float, Float>>, word: String): Double {
        if (points.isEmpty() || word.isEmpty()) return 1.0
        
        val wordChars = word.lowercase().toCharArray()
        val layout = activeLayout()
        var totalDistance = 0.0
        var count = 0
        
        // Sample points across word
        val sampleIndices = if (points.size <= wordChars.size) {
            points.indices.toList()
        } else {
            (0 until wordChars.size).map { i ->
                (i * (points.size - 1).toFloat() / (wordChars.size - 1)).toInt()
            }
        }
        
        for (i in sampleIndices.indices) {
            if (i < wordChars.size && sampleIndices[i] < points.size) {
                val point = points[sampleIndices[i]]
                val expectedChar = wordChars[i]
                val expectedPos = layout[expectedChar]
                
                if (expectedPos != null) {
                    val distance = sqrt((point.first - expectedPos.first).pow(2) + (point.second - expectedPos.second).pow(2))
                    totalDistance += distance
                    count++
                }
            }
        }
        
        return if (count > 0) totalDistance / count else 1.0
    }
    
    /**
     * Rank swipe candidates using proper metrics + Contextual Re-ranking (V6)
     * 
     * Weight tuning rationale:
     * - wLM = 2.5: HEAVILY increased to let context "I love..." strongly predict "you"
     * - wFreq = 0.8: Increased to boost common words like "you" over gibberish
     * - wPath = 1.2: Reduced to allow sloppier swiping if context matches
     * - wProx/wEdit = 0.0: ML decoder already calculated spatial scores
     * - wUser = 1.5: Boost user dictionary words
     */
    private fun rankSwipeCandidates(resources: LanguageResources, context: List<String>, metrics: List<SwipeMetrics>, limit: Int): List<Suggestion> {
        val wFreq = 0.8; val wLM = 2.5; val wPath = 1.2; val wProx = 0.0; val wEdit = 0.0; val wUser = 1.5; val wCorr = 0.5; val wFeedback = 0.4
        
        val contextWords = context.takeLast(2)
        val w_2 = contextWords.getOrNull(0)
        val w_1 = contextWords.getOrNull(1)
        
        return metrics.map { m ->
            val freq = getWordFrequency(m.word, resources).let { if (it == 0) Int.MAX_VALUE else it }
            val bi = if (w_1 != null) resources.bigrams[Pair(w_1, m.word)] ?: 0 else 0
            val tri = if (w_2 != null && w_1 != null) resources.trigrams[Triple(w_2, w_1, m.word)] ?: 0 else 0
            val lm = getLMBackoff(tri, bi, freq)
            val feedbackBias = getFeedbackBias(m.word)
            val baseConfidence = computeSwipeConfidence(m)
            val confidence = (baseConfidence + (feedbackBias * 0.1)).coerceIn(0.0, 1.0)
            
            val score = wFreq * ln(1.0 / (freq + 1)) +
                       wLM * lm +
                       wPath * m.pathScore +
                       wProx * (1.0 / (1.0 + m.proximity)) +
                       wEdit * (-m.editDistance.toDouble()) +
                       wUser * (if (resources.userWords.contains(m.word)) 1.0 else 0.0) +
                       wCorr * (if (resources.corrections.containsValue(m.word)) 1.0 else 0.0) +
                       wFeedback * feedbackBias
            
            Log.d(TAG, "[Unified] swipe-rank: word=${m.word} path=${String.format("%.2f", m.pathScore)} prox=${String.format("%.2f", m.proximity)} edit=${m.editDistance} lm=${String.format("%.2f", lm)} freq=$freq fb=${String.format("%.2f", feedbackBias)} conf=${String.format("%.2f", confidence)} score=${String.format("%.2f", score)}")
            
            Suggestion(m.word, score, SuggestionSource.SWIPE, confidence = confidence)
        }.sortedByDescending { it.score }.take(limit)
    }
    
    private fun computeSwipeConfidence(metrics: SwipeMetrics): Double {
        val pathComponent = metrics.pathScore.coerceIn(0.0, 1.0)
        val proximityComponent = (1.0 / (1.0 + metrics.proximity)).coerceIn(0.0, 1.0)
        val editComponent = when {
            metrics.editDistance <= 0 -> 1.0
            metrics.editDistance == 1 -> 0.7
            metrics.editDistance == 2 -> 0.4
            else -> 0.1
        }
        return (pathComponent * 0.5) + (proximityComponent * 0.3) + (editComponent * 0.2)
    }
    
    private fun getFeedbackBias(word: String): Double {
        val feedback = swipeFeedback[word.lowercase()] ?: return 0.0
        val total = (feedback.accepted + feedback.rejected).coerceAtLeast(1)
        return (feedback.accepted - feedback.rejected).toDouble() / total.toDouble()
    }
    
    private fun recordSwipeFeedback(word: String, accepted: Boolean) {
        if (word.isBlank()) return
        swipeFeedback.compute(word.lowercase()) { _, existing ->
            val feedback = existing ?: SwipeFeedback()
            if (accepted) {
                feedback.accepted++
            } else {
                feedback.rejected++
            }
            feedback
        }
    }
    
    /**
     * Language model backoff scoring
     */
    private fun getLMBackoff(tri: Int, bi: Int, freq: Int): Double {
        return when {
            tri > 0 -> ln(tri.toDouble() + 1) * 1.2
            bi > 0 -> ln(bi.toDouble() + 1) * 1.1  
            else -> ln(1.0 / (freq + 1)) * 0.5
        }
    }
    
    /**
     * Collapse consecutive repeated characters
     */
    private fun collapseRepeats(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder()
        var prev = s[0]
        sb.append(prev)
        for (i in 1 until s.length) {
            val c = s[i]
            if (c != prev) sb.append(c)
            prev = c
        }
        return sb.toString()
    }
    
    /**
     * Keyboard adjacency map for expanding swipe patterns
     */
    private val adjacency: Map<Char, List<Char>> by lazy {
        mapOf(
            'q' to listOf('w','a'),
            'w' to listOf('q','e','s'),
            'e' to listOf('w','r','d'),
            'r' to listOf('e','t','f'),
            't' to listOf('r','y','g'),
            'y' to listOf('t','u','h'),
            'u' to listOf('y','i','j'),
            'i' to listOf('u','o','k'),
            'o' to listOf('i','p','l'),
            'p' to listOf('o'),
            'a' to listOf('q','s','z'),
            's' to listOf('a','w','d','x'),
            'd' to listOf('s','e','f','c'),
            'f' to listOf('d','r','g','v'),
            'g' to listOf('f','t','h','b'),
            'h' to listOf('g','y','j','n'),
            'j' to listOf('h','u','k','m'),
            'k' to listOf('j','i','l'),
            'l' to listOf('k','o'),
            'z' to listOf('a','x'),
            'x' to listOf('z','s','c'),
            'c' to listOf('x','d','v'),
            'v' to listOf('c','f','b'),
            'b' to listOf('v','g','n'),
            'n' to listOf('b','h','m'),
            'm' to listOf('n','j')
        )
    }
    
    /**
     * Expand collapsed sequence using keyboard adjacency
     */
    private fun expandByAdjacency(token: String, maxVariants: Int = 30): Set<String> {
        if (token.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        
        fun dfs(idx: Int, sb: StringBuilder) {
            if (out.size >= maxVariants) return
            if (idx == token.length) {
                out.add(sb.toString())
                return
            }
            val base = token[idx]
            
            // Try base character
            sb.append(base)
            dfs(idx + 1, sb)
            sb.deleteCharAt(sb.lastIndex)
            
            // Try adjacent keys
            for (neighbor in adjacency[base].orEmpty()) {
                if (out.size >= maxVariants) break
                sb.append(neighbor)
                dfs(idx + 1, sb)
                sb.deleteCharAt(sb.lastIndex)
            }
        }
        
        dfs(0, StringBuilder(token.length))
        return out
    }
    
    /**
     * Attach suggestion callback for real-time suggestion updates
     */
    fun attachSuggestionCallback(callback: (List<String>) -> Unit) {
        suggestionCallback = callback
    }
    
    fun recordSwipeAcceptance(word: String) {
        recordSwipeFeedback(word, true)
    }
    
    fun recordSwipeRejection(word: String) {
        recordSwipeFeedback(word, false)
    }
    
    fun recordSwipeCorrection(previousWord: String, replacement: String) {
        recordSwipeFeedback(previousWord, false)
        recordSwipeFeedback(replacement, true)
    }
    
    // ========== LEGACY COMPATIBILITY METHODS ==========
    
    /**
     * Preload dictionaries for specified languages (legacy compatibility)
     */
    fun preloadLanguages(languages: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            languages.forEach { lang ->
                try {
                    multilingualDictionary.preload(lang)
                    val resources = multilingualDictionary.get(lang)
                    if (resources != null) {
                        // Warm engine for first language or if current not set
                        if (!hasLanguage(lang) || languageResources == null) {
                            setLanguage(lang, resources)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preloading language $lang into engine", e)
                }
            }
        }
    }

    /**
     * Check if language dictionary is loaded (legacy compatibility)
     */
    fun isLanguageLoaded(language: String): Boolean {
        return hasLanguage(language)
    }
    
    /**
     * Check if engine is ready (legacy compatibility)
     */
    fun isReady(): Boolean {
        val ready = languageResources != null
        if (!ready) {
            Log.w(TAG, "⚠️ Engine not ready: resources=${languageResources != null}")
        }
        return ready
    }

    /**
     * ⚠️ LEGACY METHOD - DO NOT USE IN SUGGESTION FLOW
     * This method mixes autocorrect with typing suggestions which causes:
     * - Suggestion ranking confusion
     * - UI flicker
     * - Extra CPU usage during typing
     * 
     * Use suggestForTyping() for real-time suggestions during typing instead.
     * Use autocorrect() directly for space-triggered correction only.
     */
    @Deprecated("Use suggestForTyping() for typing or autocorrect() for space-triggered correction", ReplaceWith("suggestForTyping()"))
    fun getCorrections(word: String, language: String = "en", context: List<String> = emptyList()): List<Suggestion> {
        if (word.isBlank()) return emptyList()
        
        val result = mutableListOf<Suggestion>()
        
        // STEP 1: Check for true autocorrect (spelling correction)
        // This uses Gboard rules: only correct invalid words with confidence ≥ 0.72
        val correction = autocorrect(word, context)
        if (correction != null) {
            // Put the correction first with CORRECTION source
            result.add(correction)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "📝 getCorrections: Found autocorrect '${word}' → '${correction.text}'")
            }
        }
        
        // STEP 2: Add prefix suggestions (word completions)
        // These have TYPING source, not CORRECTION
        val typingSuggestions = suggestForTyping(word, context)
            .filter { it.text != correction?.text } // Don't duplicate the correction
            .take(4) // Limit to avoid too many suggestions
        
        result.addAll(typingSuggestions)
        
        // Trigger suggestion callback if attached
        val suggestionWords = result.map { it.text }
        suggestionCallback?.invoke(suggestionWords)
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "📝 getCorrections('$word'): ${result.map { "${it.text}(${it.source})" }}")
        }
        return result
    }

    /**
     * Get suggestions as simple list of words (legacy compatibility)
     */
    fun getSuggestions(input: String, language: String = "en", limit: Int = 3): List<String> {
        if (input.isBlank()) return emptyList()
        val suggestions = suggestForTyping(input, emptyList())
        return suggestions.take(limit).map { it.text }
    }

    /**
     * Get best autocorrect suggestion (legacy compatibility)
     */
    fun getBestSuggestion(input: String, language: String = "en"): String? {
        val suggestion = autocorrect(input, emptyList())
        return suggestion?.text
    }

    // Old methods removed - now using unified API

    // Old calculateScore method removed - using unified scoring now

    /**
     * Get word candidates by prefix (legacy compatibility)
     */
    fun getCandidates(prefix: String, language: String = "en", limit: Int = 10): List<String> {
        if (prefix.isBlank()) return emptyList()
        val suggestions = suggestForTyping(prefix, emptyList())
        return suggestions.take(limit).map { it.text }
    }

    /**
     * Get next word predictions (legacy compatibility)
     */
    fun getNextWordPredictions(
        previousWord: String, 
        language: String = "en", 
        limit: Int = 5,
        context: List<String> = emptyList()
    ): List<String> {
        if (previousWord.isBlank()) return emptyList()
        
        val fullContext = if (context.isEmpty()) listOf(previousWord) else context + previousWord
        val suggestions = nextWord(fullContext, limit)
        return suggestions.map { it.text }
    }

    /**
     * Add word to user dictionary
     */
    fun addUserWord(word: String, language: String = "en", frequency: Int = 1) {
        try {
            if (word.isBlank() || word.length < 2) {
                Log.w(TAG, "⚠️ Word too short to add: '$word'")
                return
            }
            
            // Add to user dictionary
            userDictionaryManager?.learnWord(word)
            
            // Clear cache to ensure new word appears in suggestions
            clearCache()
            
            Log.d(TAG, "✅ Added user word: '$word' ($language)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding user word '$word'", e)
        }
    }

    /**
     * Learn from user input (for adaptive corrections)
     */
    fun learnFromUser(originalWord: String, correctedWord: String, language: String = "en") {
        try {
            if (originalWord.equals(correctedWord, ignoreCase = true)) {
                // User kept original word - don't learn
                return
            }
            
            // Learn the corrected word
            userDictionaryManager?.learnWord(correctedWord)
            
            // Note: corrections are now handled via LanguageResources
            // Dynamic learning patterns could be added to user data
            
            Log.d(TAG, "✨ Learned: '$originalWord' → '$correctedWord' for $language")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error learning correction", e)
        }
    }

    /**
     * Get word suggestions (legacy compatibility method)
     */
    fun getWordSuggestions(prefix: String, language: String = "en", limit: Int = 10): List<String> {
        return getCandidates(prefix, language, limit)
    }

    /**
     * ⚠️ LEGACY METHOD - DO NOT USE
     * Apply correction to input (legacy compatibility method)
     */
    @Deprecated("Use autocorrect() directly for space-triggered correction", ReplaceWith("autocorrect()"))
    fun applyCorrection(word: String, language: String = "en"): String {
        val suggestions = getCorrections(word, language)
        return suggestions.firstOrNull()?.text ?: word
    }

    /**
     * Set locale for the engine (legacy compatibility)
     */
    fun setLocale(language: String) {
        Log.d(TAG, "Legacy setLocale called for: $language")
        // Note: Language setting now handled via setLanguage(lang, resources)
    }

    /**
     * Simple Levenshtein distance calculation
     */
    private fun getEditDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) {
            for (j in 0..len2) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[len1][len2]
    }

    /**
     * ⚠️ LEGACY METHOD - DO NOT USE
     * Get suggestions optimized for swipe input (legacy method)
     * Use suggestForSwipe(SwipePath, context) for proper swipe decoding
     */
    @Deprecated("Use suggestForSwipe(SwipePath, context) for proper swipe decoding", ReplaceWith("suggestForSwipe(swipePath, context)"))
    fun suggestForSwipe(input: String, language: String): List<String> {
        return try {
            getCorrections(input, language, emptyList()).take(3).map { it.text }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting swipe suggestions for '$input'", e)
            emptyList()
        }
    }
    
    /**
     * ⚠️ LEGACY METHOD - DO NOT USE IN SUGGESTION FLOW
     * STEP 5: Simplified interface for quick suggestions
     */
    @Deprecated("Use suggestForTyping() for typing suggestions", ReplaceWith("suggestForTyping()"))
    fun suggest(input: String, language: String): List<String> {
        return getCorrections(input, language).map { it.text }
    }

    /**
     * Clear suggestion cache (for memory management)
     * 🔥 FIX 3.2 - Only call this when truly necessary (language change, dictionary update)
     */
    fun clearCache() {
        suggestionCache.evictAll()
        Log.d(TAG, "⚠️ Suggestion cache cleared (should be rare)")
    }

    /**
     * Get engine statistics (for debugging)
     * Returns actual loaded data from LanguageResources
     */
    fun getStats(): Map<String, Any> {
        val resources = languageResources
        
        return if (resources != null) {
            mapOf(
                "cacheSize" to suggestionCache.size(),
                "currentLanguage" to currentLanguage,
                "totalWords" to resources.words.size,
                "bigrams" to resources.bigrams.size,
                "trigrams" to resources.trigrams.size,
                "userWords" to resources.userWords.size,
                "shortcuts" to resources.shortcuts.size,
                "corrections" to resources.corrections.size
            )
        } else {
            mapOf(
                "cacheSize" to suggestionCache.size(),
                "currentLanguage" to currentLanguage,
                "status" to "no_resources_loaded"
            )
        }
    }

    /**
     * Calculate confidence score for an autocorrect suggestion
     * Returns value between 0.0 and 1.0, where higher = more confident
     * 
     * @param input The typed word
     * @param suggestion The suggested correction
     * @return Confidence score (0.0 to 1.0)
     */
    fun getConfidence(input: String, suggestion: String): Float {
        if (input.isEmpty() || suggestion.isEmpty()) return 0f

        if (isBlacklisted(input, suggestion)) {
            Log.d(TAG, "🚫 Confidence suppressed for blacklisted correction '$input' → '$suggestion'")
            return 0f
        }
        
        // Exact match = perfect confidence
        if (input.equals(suggestion, ignoreCase = true)) return 1.0f
        
        val inputLower = input.lowercase()
        val suggestionLower = suggestion.lowercase()
        
        // 🔥 HIGH PRIORITY: corrections.json matches get high confidence (0.8)
        // This ensures predefined corrections like "plz→please" always apply
        val resources = languageResources
        if (resources != null && resources.corrections.containsKey(inputLower) && resources.corrections[inputLower] == suggestionLower) {
            return 0.8f
        }
        
        // 🔥 HIGH-PRIORITY: Detect transpositions (adjacent character swaps)
        // Examples: "teh" → "the", "hte" → "the", "taht" → "that"
        if (inputLower.length == suggestionLower.length) {
            var diffCount = 0
            var transpositionFound = false
            
            for (i in inputLower.indices) {
                if (inputLower[i] != suggestionLower[i]) {
                    diffCount++
                    // Check if next character is swapped
                    if (i < inputLower.length - 1 &&
                        inputLower[i] == suggestionLower[i + 1] &&
                        inputLower[i + 1] == suggestionLower[i]) {
                        transpositionFound = true
                    }
                }
            }
            
            // If it's a single transposition, give very high confidence
            if (transpositionFound && diffCount == 2) {
                return 0.85f  // High confidence for transpositions
            }
        }
        
        // Calculate edit distance based confidence
        val maxLen = maxOf(inputLower.length, suggestionLower.length).toFloat()
        val distance = getEditDistance(inputLower, suggestionLower)
        
        // Base confidence from edit distance
        val editDistanceConfidence = 1f - (distance / maxLen)
        
        // Bonus for common typo patterns (higher confidence)
        val typoBonus = when {
            // Single character difference
            distance == 1 && inputLower.length == suggestionLower.length -> 0.3f
            // Single insertion/deletion
            distance == 1 -> 0.2f
            // Two character difference in longer words
            distance == 2 && inputLower.length >= 4 -> 0.15f
            else -> 0f
        }
        
        // Penalty for length mismatch (less confident if lengths differ a lot)
        val lengthDiff = kotlin.math.abs(inputLower.length - suggestionLower.length)
        val lengthPenalty = if (lengthDiff > 2) 0.1f else 0f
        
        // Final confidence score
        return (editDistanceConfidence + typoBonus - lengthPenalty).coerceIn(0f, 1f)
    }
    
    // Old corrections loading removed - now handled by LanguageResources
    
    /**
     * Call this when user accepts an autocorrect suggestion
     * This helps the system learn user preferences
     */
    /**
     * ✅ V3 PACK Section 5: Enhanced word learning with auto-promotion
     * 
     * Tracks correction acceptance and automatically promotes corrections
     * after 3 accepts (Gboard-style personalization)
     */
    fun onCorrectionAccepted(originalWord: String, acceptedWord: String, language: String = "en") {
        try {
            // Learn the accepted word
            userDictionaryManager?.learnWord(acceptedWord)
            
            // If it's a correction (not just a suggestion), learn the pattern
            if (!originalWord.equals(acceptedWord, ignoreCase = true)) {
                learnFromUser(originalWord, acceptedWord, language)
                
                // ✅ V3: Track acceptance count for auto-promotion (case-insensitive)
                val correctionKey = "${originalWord.lowercase()}→${acceptedWord.lowercase()}"
                val currentCount = correctionAcceptCounts.getOrDefault(correctionKey, 0)
                val newCount = currentCount + 1
                correctionAcceptCounts[correctionKey] = newCount
                
                // ✅ V3: Auto-promote after 3 accepts (Gboard Rule)
                // After 3 accepts, the correction becomes strongly weighted in the learning system
                if (newCount >= 3) {
                    // Learn with extra weight to boost this correction
                    repeat(3) {
                        userDictionaryManager?.learnWord(acceptedWord)
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "🎖️ Auto-promoted correction: '$originalWord' → '$acceptedWord' (accepted $newCount times, boosted learning)")
                    }
                    // Reset counter after promotion
                    correctionAcceptCounts.remove(correctionKey)
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "✅ User accepted: '$originalWord' → '$acceptedWord' (count: $newCount/3)")
                    }
                }
            }
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "✅ User accepted: '$originalWord' → '$acceptedWord'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing accepted correction", e)
        }
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false
}
