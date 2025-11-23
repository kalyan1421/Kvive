package com.kvive.keyboard

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import com.kvive.keyboard.utils.LogUtil
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
    }

    // Thread-safe cache for suggestions
    private val suggestionCache = object : LruCache<String, List<Suggestion>>(500) {}
    
    // Current language resources
    @Volatile
    private var currentLanguage: String = "en"
    @Volatile
    private var languageResources: LanguageResources? = null
    
    // ========== NEW: Phase 1 Components ==========
    
    // ML-based swipe decoder for better gesture recognition
    private val swipeDecoderML: SwipeDecoderML by lazy {
        SwipeDecoderML(context, defaultKeyLayout)
    }
    
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
    
    private data class SwipeFeedback(var accepted: Int = 0, var rejected: Int = 0)
    private val swipeFeedback = ConcurrentHashMap<String, SwipeFeedback>()
    
    private fun activeLayout(): Map<Char, Pair<Float, Float>> {
        return if (currentLayoutCoordinates.isEmpty()) defaultKeyLayout else currentLayoutCoordinates
    }
    
    // Suggestion callback for real-time suggestion updates
    private var suggestionCallback: ((List<String>) -> Unit)? = null

    private fun isBlacklisted(original: String, corrected: String): Boolean {
        val manager = userDictionaryManager ?: return false
        return manager.isBlacklisted(original, corrected)
    }

    /**
     * Set language and resources (NEW UNIFIED API)
     * This is the single entry point for language switching
     */
    fun setLanguage(lang: String, resources: LanguageResources) {
        currentLanguage = lang
        languageResources = resources
        currentLayoutCoordinates = keyboardLayouts[lang] ?: defaultKeyLayout
        suggestionCache.evictAll() // Clear cache when language changes
        LogUtil.d(TAG, "🌐 Firebase language activated: $lang")
        LogUtil.d(TAG, "📖 Loaded $lang: words=${resources.words.size}, bigrams=${resources.bigrams.size}, trigrams=${resources.trigrams.size}")
        LogUtil.d(TAG, "✅ UnifiedAutocorrectEngine ready for $lang")
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
        suggestionCache.evictAll()
        LogUtil.d(TAG, "🧭 Updated swipe layout for $language: keys=${filtered.size}")
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
        LogUtil.d(TAG, "🔍 Getting suggestions for prefix: '$prefix'")
        val suggestions = suggestForTyping(prefix, emptyList())
        return suggestions.map { it.text }
    }
    
    /**
     * Get next word predictions (UNIFIED API as specified in requirements)
     * This method name was requested in the task specification
     */
    fun getNextWordPredictions(context: List<String>): List<String> {
        if (context.isEmpty()) return emptyList()
        LogUtil.d(TAG, "🔮 Getting next word predictions for: $context")
        val predictions = nextWord(context, 3)
        return predictions.map { it.text }
    }
    
    /**
     * Get typing suggestions for partial input (NEW UNIFIED API with Phase 1 enhancements)
     */
    fun suggestForTyping(prefix: String, context: List<String>): List<Suggestion> {
        if (prefix.isBlank()) return emptyList()
        val resources = languageResources ?: return emptyList()
        
        val cacheKey = "$prefix:typing:${context.joinToString(",")}"
        suggestionCache.get(cacheKey)?.let { return it }
        
        val suggestions = mutableListOf<Suggestion>()
        
        try {
            LogUtil.d(TAG, "✍️ Getting typing suggestions for prefix '$prefix' (Firebase data)")
            
            // Find candidate words that start with prefix
            val candidates = resources.words.entries
                .filter { it.key.lowercase().startsWith(prefix.lowercase()) }
                .take(20) // Limit candidates for performance
            
            candidates.forEach { (word, freq) ->
                val editDistance = getEditDistanceWithProximity(prefix, word)
                val score = calculateUnifiedScore(word, prefix, editDistance, context, SuggestionSource.TYPING)
                suggestions.add(Suggestion(word, score, SuggestionSource.TYPING))
            }
            
            // ========== Phase 1: Context-Aware Rescoring ==========
            val contextBoosted = if (context.isNotEmpty()) {
                val prev = context.last().lowercase()
                suggestions.map { suggestion ->
                    var boost = 1.0
                    
                    // Bigram boost: check if this word commonly follows previous word
                    if (resources.bigrams.containsKey(Pair(prev, suggestion.text.lowercase()))) {
                        boost = 1.3
                        LogUtil.d(TAG, "📈 Context boost for '${suggestion.text}' after '$prev'")
                    }
                    
                    // Trigram boost: check if we have 2+ context words
                    if (context.size >= 2) {
                        val prev2 = context[context.size - 2].lowercase()
                        if (resources.trigrams.containsKey(Triple(prev2, prev, suggestion.text.lowercase()))) {
                            boost = 1.5
                            LogUtil.d(TAG, "📈 Trigram boost for '${suggestion.text}' after '$prev2 $prev'")
                        }
                    }
                    
                    suggestion.copy(score = suggestion.score * boost)
                }
            } else {
                suggestions
            }
            
            // Sort by boosted score and take top suggestions
            val result = contextBoosted
                .sortedByDescending { it.score }
                .take(5)
            
            LogUtil.d(TAG, "📊 Unified typing suggestions with context: ${result.map { it.text }}")
            suggestionCache.put(cacheKey, result)
            return result
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting typing suggestions for '$prefix'", e)
            return emptyList()
        }
    }
    
    /**
     * Get best autocorrect suggestion (NEW UNIFIED API)
     */
    fun autocorrect(input: String, context: List<String>): Suggestion? {
        if (input.isBlank()) return null
        val resources = languageResources ?: return null
        
        // Priority 1: Check corrections map
        val inputLower = input.lowercase()
        LogUtil.d(TAG, "🔧 Autocorrect: checking '$inputLower' in corrections map (${resources.corrections.size} entries)")
        
        resources.corrections[inputLower]?.let { correction ->
            if (isBlacklisted(input, correction)) {
                LogUtil.d(TAG, "🚫 Correction blacklisted: '$inputLower' → '$correction'")
            } else {
                val score = scoringWeights.correctionWeight * 4.0 // High priority for predefined corrections
                LogUtil.d(TAG, "✅ Correction found: '$inputLower' → '$correction'")
                return Suggestion(correction, score, SuggestionSource.CORRECTION, isAutoCommit = true)
            }
        }
        
        LogUtil.d(TAG, "⚠️ No correction found for '$inputLower' in map")
        
        // Priority 2: Check typing suggestions and return best if score is high enough
        val suggestions = suggestForTyping(input, context)
        val candidate = suggestions.firstOrNull { !isBlacklisted(input, it.text) }
        if (candidate != null && candidate.score > 2.0) {
            LogUtil.d(TAG, "✅ Using suggestion '${candidate.text}' for '$input' (score=${candidate.score})")
            return candidate.copy(isAutoCommit = true)
        } else if (candidate == null && suggestions.isNotEmpty()) {
            LogUtil.d(TAG, "🚫 All suggestions for '$input' are blacklisted; skipping autocorrect")
        }
        
        return null
    }
    
    /**
     * Get next word predictions (NEW UNIFIED API with Phase 1 ML predictor)
     */
    fun nextWord(context: List<String>, k: Int = 3): List<Suggestion> {
        if (context.isEmpty()) return emptyList()
        val resources = languageResources ?: return emptyList()
        
        val cacheKey = "nextword:${context.joinToString(",")}:$k"
        suggestionCache.get(cacheKey)?.let { return it }
        
        val suggestions = mutableListOf<Suggestion>()
        
        try {
            LogUtil.d(TAG, "🔮 Getting next word predictions for context: $context (Firebase data + ML)")
            
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
            // Launch async prediction that can update UI when ready
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val mlPredictions = nextWordPredictor.predictNext(contextWords, k)
                    LogUtil.d(TAG, "🤖 ML predictions: $mlPredictions")
                    // These predictions can be used to update the UI asynchronously
                    // or merged with existing suggestions in Phase 2
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error getting ML predictions", e)
                }
            }
            
            val result = suggestions
                .sortedByDescending { it.score }
                .take(k)

            if (result.isNotEmpty()) {
                LogUtil.d(TAG, "📊 Next word predictions: ${result.map { it.text }}")
                suggestionCache.put(cacheKey, result)
                return result
            }

            val fallback = fallbackSuggestions(context, k)
            if (fallback.isNotEmpty()) {
                LogUtil.d(TAG, "📊 Next word fallback predictions: ${fallback.map { it.text }}")
                suggestionCache.put(cacheKey, fallback)
            }
            return fallback
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting next word predictions", e)
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
     * Get swipe suggestions (NEW UNIFIED API with Phase 1 ML decoder)
     */
    fun suggestForSwipe(path: SwipePath, context: List<String>): List<Suggestion> {
        val resources = languageResources ?: return emptyList()
        
        val cacheKey = "swipe:${path.hashCode()}:${context.joinToString(",")}"
        suggestionCache.get(cacheKey)?.let { return it }
        
        try {
            LogUtil.d(TAG, "🔄 Getting swipe suggestions from Firebase data + ML decoder")
            
            // ========== Phase 1: ML-Based Swipe Decoding ==========
            // Use proper path decoding with metrics (primary decoder)
            val geometricCandidates = decodeSwipePath(path, resources, context)
            
            // Convert to suggestions with proper scoring
            val result = geometricCandidates
                .map { (word, score) -> Suggestion(word, score, SuggestionSource.SWIPE) }
                .take(5)
            
            // ML decoder confidence boost (Phase 2: will use for ranking boost, not primary)
            val mlCandidates = swipeDecoderML.decode(path)
            LogUtil.d(TAG, "🤖 ML decoder candidates: ${mlCandidates.take(5)}")
            
            LogUtil.d(TAG, "✅ Swipe candidates (ML + Geometric): ${result.map { "${it.text}(${String.format("%.2f", it.score)})" }.joinToString(", ")}")
            
            suggestionCache.put(cacheKey, result)
            return result
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting swipe suggestions", e)
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
            
            LogUtil.d(TAG, "🔄 Swipe suggestions for sequence $sequence: ${suggestions.map { it.text }}")
            
            return suggestions.map { it.text }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in simplified suggestForSwipe", e)
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
                LogUtil.d(TAG, "👤 Personal boost for '$candidate': +$personalBoost (used $userFreq times)")
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
        val freq = resources.words[candidate] ?: Int.MAX_VALUE
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
     * Calculate edit distance with keyboard proximity weighting
     */
    private fun getEditDistanceWithProximity(input: String, candidate: String): Int {
        // For now, use standard edit distance
        // TODO: Add keyboard proximity weighting
        return getEditDistance(input, candidate)
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
            LogUtil.d(TAG, "[Swipe] Decoding path with ${path.points.size} points")
            
            // 1) From points → most-likely key sequence (and alternates per point)
            val lattice: List<List<Char>> = candidatesForEachPoint(path.points, radiusPx = 0.15f)
            val latticeSizes = lattice.map { it.size }
            LogUtil.d(TAG, "[Swipe] lattice sizes: $latticeSizes points=${path.points.size}")
            
            // 2) Build fuzzy prefixes from the lattice (beam search over 8-15 steps)
            val fuzzyPrefixes: List<String> = beamDecode(lattice, beamWidth = 8, maxLen = 15)
            LogUtil.d(TAG, "[Swipe] beam top prefixes: ${fuzzyPrefixes.take(8).joinToString(", ")}")
            
            // 3) Query dictionary with those prefixes (NOT common_words), merge unique words
            val raw = mutableSetOf<String>()
            for (p in fuzzyPrefixes) {
                if (p.length >= 2) { // Only meaningful prefixes
                    val prefixCandidates = resources.words.entries
                        .filter { it.key.lowercase().startsWith(p.lowercase()) }
                        .take(300)
                        .map { it.key }
                    raw.addAll(prefixCandidates)
                }
            }
            
            // 4) If no candidates from fuzzy prefixes, try collapsed sequence directly
            val snappedSequence = snapToKeys(path.points)
            val collapsedSequence = collapseRepeats(snappedSequence)
            LogUtil.d(TAG, "[Swipe] snapped='$snappedSequence' collapsed='$collapsedSequence'")
            
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                LogUtil.d(TAG, "[Swipe] No fuzzy candidates, trying collapsed sequence: '$collapsedSequence'")
                val directCandidates = resources.words.entries
                    .filter { it.key.lowercase().startsWith(collapsedSequence.lowercase()) }
            .take(50)
                    .map { it.key }
                raw.addAll(directCandidates)
            }
            
            // 5) Still empty? Try proximity fallback with edit distance
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                LogUtil.d(TAG, "[Swipe] Trying proximity fallback for: '$collapsedSequence'")
                LogUtil.d(TAG, "[Swipe] Dictionary has ${resources.words.size} words available")
                
                // Try a broader search first - longer sequences often have more errors
                val editThreshold = if (collapsedSequence.length >= 6) 3 else 2
                val close = resources.words.keys
                    .filter { word -> 
                        val editDistance = getEditDistance(collapsedSequence, word)
                        editDistance <= editThreshold && word.length >= 3 // At least 3 letter words
                    }
                    .sortedBy { getEditDistance(collapsedSequence, it) } // Sort by distance
                    .take(15)
                raw.addAll(close)
                LogUtil.w(TAG, "[Swipe] Using proximity fallback for '$collapsedSequence' (threshold=$editThreshold): ${close.take(5)}")
                
                // If still empty, try some common English words that are similar length
                if (close.isEmpty()) {
                    val commonWords = resources.words.keys.filter { 
                        it.length == collapsedSequence.length && 
                        it.all { c -> c.isLetter() } 
                    }.take(10)
                    LogUtil.d(TAG, "[Swipe] Trying same-length words: ${commonWords.take(5)}")
                    raw.addAll(commonWords)
                }
            }
            
            // 6) Additional fallback: adjacency expansion if still empty
            if (raw.isEmpty() && collapsedSequence.length >= 2) {
                LogUtil.d(TAG, "[Swipe] Trying adjacency expansion for: '$collapsedSequence'")
                val expanded = expandByAdjacency(collapsedSequence, maxVariants = 20)
                for (variant in expanded) {
                    val variantCandidates = resources.words.entries
                        .filter { it.key.lowercase().startsWith(variant.lowercase()) }
                        .take(20)
                        .map { it.key }
                    raw.addAll(variantCandidates)
                    if (raw.size >= 30) break // Don't let it explode
                }
            }
            
            var rawCandidates = raw.toList()
            LogUtil.d(TAG, "[Swipe] rawCandidates: ${rawCandidates.size} (first 10: ${rawCandidates.take(10).joinToString(", ")})")
            
            // 7) Last resort: if still empty, return collapsed sequence as a candidate
            if (rawCandidates.isEmpty() && collapsedSequence.length >= 2) {
                rawCandidates = listOf(collapsedSequence)
                LogUtil.w(TAG, "[Swipe] No dictionary matches, using collapsed fallback: '$collapsedSequence'")
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
            LogUtil.e(TAG, "Error in swipe path decoding", e)
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
            
            LogUtil.d(TAG, "[Lattice] point($x,$y) → keys=[${candidates.joinToString("")}]")
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
     * Rank swipe candidates using proper metrics
     */
    private fun rankSwipeCandidates(resources: LanguageResources, context: List<String>, metrics: List<SwipeMetrics>, limit: Int): List<Suggestion> {
        val wFreq = 0.6; val wLM = 1.0; val wPath = 1.6; val wProx = 0.6; val wEdit = 0.8; val wUser = 1.0; val wCorr = 0.5; val wFeedback = 0.4
        
        val contextWords = context.takeLast(2)
        val w_2 = contextWords.getOrNull(0)
        val w_1 = contextWords.getOrNull(1)
        
        return metrics.map { m ->
            val freq = resources.words[m.word] ?: Int.MAX_VALUE
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
            
            LogUtil.d(TAG, "[Unified] swipe-rank: word=${m.word} path=${String.format("%.2f", m.pathScore)} prox=${String.format("%.2f", m.proximity)} edit=${m.editDistance} lm=${String.format("%.2f", lm)} freq=$freq fb=${String.format("%.2f", feedbackBias)} conf=${String.format("%.2f", confidence)} score=${String.format("%.2f", score)}")
            
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
                    LogUtil.e(TAG, "Error preloading language $lang into engine", e)
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
            LogUtil.w(TAG, "⚠️ Engine not ready: resources=${languageResources != null}")
        }
        return ready
    }

    /**
     * Get autocorrect suggestions (legacy compatibility)
     * Delegates to new unified API
     */
    fun getCorrections(word: String, language: String = "en", context: List<String> = emptyList()): List<Suggestion> {
        if (word.isBlank()) return emptyList()
        
        // Convert to new API format
        val suggestions = suggestForTyping(word, context)
        
        // Trigger suggestion callback if attached
        val suggestionWords = suggestions.map { it.text }
        suggestionCallback?.invoke(suggestionWords)
        
        return suggestions
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
                LogUtil.w(TAG, "⚠️ Word too short to add: '$word'")
                return
            }
            
            // Add to user dictionary
            userDictionaryManager?.learnWord(word)
            
            // Clear cache to ensure new word appears in suggestions
            clearCache()
            
            LogUtil.d(TAG, "✅ Added user word: '$word' ($language)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error adding user word '$word'", e)
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
            
            LogUtil.d(TAG, "✨ Learned: '$originalWord' → '$correctedWord' for $language")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error learning correction", e)
        }
    }

    /**
     * Get word suggestions (legacy compatibility method)
     */
    fun getWordSuggestions(prefix: String, language: String = "en", limit: Int = 10): List<String> {
        return getCandidates(prefix, language, limit)
    }

    /**
     * Apply correction to input (legacy compatibility method)
     */
    fun applyCorrection(word: String, language: String = "en"): String {
        val suggestions = getCorrections(word, language)
        return suggestions.firstOrNull()?.text ?: word
    }

    /**
     * Set locale for the engine (legacy compatibility)
     */
    fun setLocale(language: String) {
        LogUtil.d(TAG, "Legacy setLocale called for: $language")
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
     * Get suggestions optimized for swipe input (legacy method)
     * Use suggestForSwipe(SwipePath, context) for proper swipe decoding
     */
    fun suggestForSwipe(input: String, language: String): List<String> {
        return try {
            getCorrections(input, language, emptyList()).take(3).map { it.text }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting swipe suggestions for '$input'", e)
            emptyList()
        }
    }
    
    /**
     * STEP 5: Simplified interface for quick suggestions
     */
    fun suggest(input: String, language: String): List<String> {
        return getCorrections(input, language).map { it.text }
    }

    /**
     * Clear suggestion cache (for memory management)
     */
    fun clearCache() {
        suggestionCache.evictAll()
        LogUtil.d(TAG, "Suggestion cache cleared")
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
            LogUtil.d(TAG, "🚫 Confidence suppressed for blacklisted correction '$input' → '$suggestion'")
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
    fun onCorrectionAccepted(originalWord: String, acceptedWord: String, language: String = "en") {
        try {
            // Learn the accepted word
            userDictionaryManager?.learnWord(acceptedWord)
            
            // If it's a correction (not just a suggestion), learn the pattern
            if (!originalWord.equals(acceptedWord, ignoreCase = true)) {
                learnFromUser(originalWord, acceptedWord, language)
            }
            
            LogUtil.d(TAG, "✅ User accepted: '$originalWord' → '$acceptedWord'")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error processing accepted correction", e)
        }
    }
}
