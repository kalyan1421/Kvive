package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.kvive.keyboard.Suggestion
import com.kvive.keyboard.SuggestionSource
import com.kvive.keyboard.utils.LogUtil
import kotlinx.coroutines.*

/**
 * 🧠 UNIFIED SUGGESTION CONTROLLER
 * 
 * Central hub for ALL suggestion types:
 * - Text suggestions (UnifiedAutocorrectEngine)
 * - Emoji suggestions (EmojiSuggestionEngine)
 * - Clipboard suggestions (ClipboardHistoryManager)
 * - Next-word predictions
 * - AI-powered rewrites
 * 
 * Features:
 * - Unified scoring and ranking across all sources
 * - Settings-aware suggestion filtering
 * - LRU caching for performance
 * - Real-time updates via coroutines
 * - Thread-safe suggestion delivery
 * 
 * This replaces fragmented suggestion logic across AIKeyboardService
 */
class UnifiedSuggestionController(
    private val context: Context,
    private val unifiedAutocorrectEngine: UnifiedAutocorrectEngine,
    private val clipboardHistoryManager: ClipboardHistoryManager,
    private val languageManager: LanguageManager
) {
    companion object {
        private const val TAG = "UnifiedSuggestionCtrl"
        private const val MAX_SUGGESTIONS = 5
        private const val MAX_EMOJI_SUGGESTIONS = 2
        private const val MAX_CLIPBOARD_SUGGESTIONS = 1
        private const val CACHE_SIZE = 100
        
        // Scoring weights for unified ranking
        private const val WEIGHT_TEXT_SUGGESTION = 1.0
        private const val WEIGHT_EMOJI = 0.7
        private const val WEIGHT_CLIPBOARD = 0.8
        private const val WEIGHT_NEXT_WORD = 0.9
        private const val WEIGHT_AI_REWRITE = 1.2
        private const val WEIGHT_SENTENCE_STARTER = 0.85
        
        // Common sentence starter words
        private val SENTENCE_STARTERS = listOf(
            "I", "The", "A", "We", "You", "It", "They", "This", "That",
            "My", "What", "How", "When", "Where", "Why", "Who", "Can",
            "Will", "Would", "Should", "Could", "Have", "Has", "Do",
            "Does", "Did", "Is", "Are", "Was", "Were", "Be", "Been"
        )

        private val DEFAULT_FALLBACKS = listOf("I", "The", "You", "We", "It", "Thanks")
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Settings (loaded from SharedPreferences)
    private val prefs: SharedPreferences = context.getSharedPreferences("unified_suggestions", Context.MODE_PRIVATE)
    
    @Volatile private var aiSuggestionsEnabled = true
    @Volatile private var emojiSuggestionsEnabled = true
    @Volatile private var clipboardSuggestionsEnabled = true
    @Volatile private var nextWordPredictionEnabled = true
    
    // Track if a new clipboard item was just copied
    @Volatile private var hasNewClipboardItem = false
    
    // LRU Cache for recent suggestions
    private val suggestionCache = object : android.util.LruCache<String, List<UnifiedSuggestion>>(CACHE_SIZE) {}
    
    // Suggestion listeners for real-time updates
    private val suggestionListeners = mutableListOf<SuggestionListener>()
    
    /**
     * Unified suggestion data class
     */
    data class UnifiedSuggestion(
        val text: String,
        val displayText: String = text,
        val type: SuggestionType,
        val score: Double,
        val source: String,
        val metadata: Map<String, Any> = emptyMap(),
        val isAutoCommit: Boolean = false
    ) {
        /**
         * Get formatted display text (truncate if too long)
         */
        fun getDisplayText(maxLength: Int = 30): String {
            return if (displayText.length > maxLength) {
                "${displayText.take(maxLength - 3)}..."
            } else {
                displayText
            }
        }
    }
    
    /**
     * Suggestion types for UI differentiation
     */
    enum class SuggestionType {
        TYPING,          // Regular typing suggestion
        NEXT_WORD,       // Next word prediction
        EMOJI,           // Emoji suggestion
        CLIPBOARD,       // Clipboard quick paste
        AI_REWRITE,      // AI-powered rewrite
        AUTOCORRECT,     // Autocorrect suggestion (auto-commit)
        USER_LEARNED     // User dictionary word
    }
    
    /**
     * Listener interface for suggestion updates
     */
    interface SuggestionListener {
        fun onSuggestionsUpdated(suggestions: List<UnifiedSuggestion>)
        fun onError(error: Exception)
    }
    
    init {
        loadSettings()
        LogUtil.d(TAG, "✅ UnifiedSuggestionController initialized")
        LogUtil.d(TAG, "Settings: AI=$aiSuggestionsEnabled, Emoji=$emojiSuggestionsEnabled, Clipboard=$clipboardSuggestionsEnabled, NextWord=$nextWordPredictionEnabled")
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        aiSuggestionsEnabled = prefs.getBoolean("ai_suggestions", true)
        emojiSuggestionsEnabled = prefs.getBoolean("emoji_suggestions", true)
        clipboardSuggestionsEnabled = prefs.getBoolean("clipboard_suggestions", true)
        nextWordPredictionEnabled = prefs.getBoolean("next_word_prediction", true)
    }
    
    /**
     * Update settings (called from Flutter via MethodChannel)
     */
    fun updateSettings(
        aiEnabled: Boolean? = null,
        emojiEnabled: Boolean? = null,
        clipboardEnabled: Boolean? = null,
        nextWordEnabled: Boolean? = null
    ) {
        aiEnabled?.let { 
            aiSuggestionsEnabled = it
            prefs.edit().putBoolean("ai_suggestions", it).apply()
        }
        emojiEnabled?.let { 
            emojiSuggestionsEnabled = it
            prefs.edit().putBoolean("emoji_suggestions", it).apply()
        }
        clipboardEnabled?.let { 
            clipboardSuggestionsEnabled = it
            prefs.edit().putBoolean("clipboard_suggestions", it).apply()
        }
        nextWordEnabled?.let { 
            nextWordPredictionEnabled = it
            prefs.edit().putBoolean("next_word_prediction", it).apply()
        }
        
        // Clear cache when settings change
        suggestionCache.evictAll()
        
        LogUtil.d(TAG, "⚙️ Settings updated: AI=$aiSuggestionsEnabled, Emoji=$emojiSuggestionsEnabled, Clipboard=$clipboardSuggestionsEnabled, NextWord=$nextWordPredictionEnabled")
    }
    
    /**
     * 🎯 MAIN API: Get unified suggestions for current typing context
     * 
     * This is the single entry point for all suggestion requests
     * 
     * @param prefix Current word being typed (empty for next-word prediction)
     * @param context Previous words for context-aware predictions
     * @param callback Callback for async results (optional)
     * @return List of unified suggestions, ranked by relevance
     */
    suspend fun getUnifiedSuggestions(
        prefix: String,
        context: List<String> = emptyList(),
        includeEmoji: Boolean = true,
        includeClipboard: Boolean = true
    ): List<UnifiedSuggestion> = withContext(Dispatchers.Default) {
        try {
            val cacheKey = "${prefix}:${context.joinToString(",")}:$includeEmoji:$includeClipboard"
            
            // Check cache first
            suggestionCache.get(cacheKey)?.let { 
                LogUtil.d(TAG, "💾 Cache hit for: '$prefix'")
                return@withContext it 
            }
            
            LogUtil.d(TAG, "🔍 Getting unified suggestions: prefix='$prefix', context=$context")
            
            val allSuggestions = mutableListOf<UnifiedSuggestion>()
            
            // 1️⃣ Text Suggestions (Typing or Next-Word)
            val textSuggestions = getTextSuggestions(prefix, context)
            allSuggestions.addAll(textSuggestions)
            LogUtil.d(TAG, "✍️ Text suggestions: ${textSuggestions.size}")
            
            // 2️⃣ Emoji Suggestions (if enabled)
            if (includeEmoji && emojiSuggestionsEnabled && prefix.length >= 2) {
                val emojiSuggestions = getEmojiSuggestions(prefix)
                allSuggestions.addAll(emojiSuggestions)
                LogUtil.d(TAG, "😊 Emoji suggestions: ${emojiSuggestions.size}")
            }
            
            // 3️⃣ Clipboard Suggestions (if enabled and new item available)
            if (includeClipboard && clipboardSuggestionsEnabled && prefix.isEmpty() && hasNewClipboardItem) {
                val clipboardSuggestions = getClipboardSuggestions()
                allSuggestions.addAll(clipboardSuggestions)
                LogUtil.d(TAG, "📋 Clipboard suggestions: ${clipboardSuggestions.size}")
            }
            
            // 4️⃣ Unified Ranking & De-duplication
            val rankedSuggestions = rankAndDeduplicate(allSuggestions)
                .take(MAX_SUGGESTIONS)
            
            // Cache the results
            suggestionCache.put(cacheKey, rankedSuggestions)
            
            LogUtil.d(TAG, "✅ Final suggestions: ${rankedSuggestions.map { "${it.text}(${it.type})" }}")
            
            // Notify listeners
            withContext(Dispatchers.Main) {
                notifySuggestionListeners(rankedSuggestions)
            }
            
            return@withContext rankedSuggestions
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error getting unified suggestions", e)
            withContext(Dispatchers.Main) {
                notifyError(e)
            }
            return@withContext emptyList()
        }
    }
    
    /**
     * Get text suggestions (typing or next-word)
     */
    private suspend fun getTextSuggestions(prefix: String, context: List<String>): List<UnifiedSuggestion> {
        return try {
            val trimmedPrefix = prefix.trim()
            val baseSuggestions = when {
                trimmedPrefix.isEmpty() && nextWordPredictionEnabled && context.isNotEmpty() -> {
                    if (isStartOfSentence(context)) {
                        getSentenceStarterSuggestions()
                    } else {
                        unifiedAutocorrectEngine.nextWord(context, MAX_SUGGESTIONS)
                            .map { it.toUnifiedSuggestion(WEIGHT_NEXT_WORD, SuggestionType.NEXT_WORD) }
                    }
                }
                trimmedPrefix.isNotEmpty() -> {
                    unifiedAutocorrectEngine.suggestForTyping(trimmedPrefix, context)
                        .map { suggestion ->
                            val defaultType = if (suggestion.isAutoCommit) SuggestionType.AUTOCORRECT else SuggestionType.TYPING
                            suggestion.toUnifiedSuggestion(WEIGHT_TEXT_SUGGESTION, defaultType)
                        }
                }
                trimmedPrefix.isEmpty() && context.isEmpty() -> {
                    getSentenceStarterSuggestions()
                }
                else -> emptyList()
            }

            val resolved = if (baseSuggestions.isNotEmpty()) {
                baseSuggestions
            } else {
                buildFallbackTextSuggestions(trimmedPrefix, context)
            }

            if (resolved.isEmpty()) getSentenceStarterSuggestions() else resolved.take(MAX_SUGGESTIONS)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting text suggestions", e)
            getSentenceStarterSuggestions()
        }
    }

    private fun buildFallbackTextSuggestions(prefix: String, context: List<String>): List<UnifiedSuggestion> {
        val results = mutableListOf<UnifiedSuggestion>()
        val limit = MAX_SUGGESTIONS

        fun MutableList<UnifiedSuggestion>.appendUnique(candidates: List<UnifiedSuggestion>) {
            val seen = this.map { it.text.lowercase() }.toMutableSet()
            candidates.forEach { candidate ->
                if (candidate.text.isNotBlank() && seen.add(candidate.text.lowercase())) {
                    this.add(candidate)
                }
            }
        }

        try {
            if (prefix.isEmpty()) {
                val fallback = unifiedAutocorrectEngine.fallbackSuggestions(context, limit)
                    .map { it.toUnifiedSuggestion(WEIGHT_NEXT_WORD, SuggestionType.NEXT_WORD) }
                results.appendUnique(fallback)
            } else {
                val ngramFallback = unifiedAutocorrectEngine.fallbackSuggestions(context, limit * 2)
                    .filter { it.text.startsWith(prefix, ignoreCase = true) }
                    .map { it.toUnifiedSuggestion(WEIGHT_TEXT_SUGGESTION) }
                results.appendUnique(ngramFallback)

                if (results.size < limit) {
                    val topMatches = unifiedAutocorrectEngine.getTopWords(limit * 2)
                        .filter { it.text.startsWith(prefix, ignoreCase = true) }
                        .map { it.toUnifiedSuggestion(WEIGHT_TEXT_SUGGESTION) }
                    results.appendUnique(topMatches)
                }
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "Fallback suggestion generation error", e)
        }

        if (results.size < limit) {
            val starters = if (prefix.isEmpty()) {
                getSentenceStarterSuggestions()
            } else {
                getSentenceStarterSuggestions().filter { it.text.startsWith(prefix, ignoreCase = true) }
            }
            results.appendUnique(starters)
        }

        if (results.isEmpty() && prefix.isNotEmpty()) {
            results.appendUnique(getSentenceStarterSuggestions())
        }

        if (results.isEmpty()) {
            DEFAULT_FALLBACKS.take(limit).map { word ->
                UnifiedSuggestion(
                    text = word,
                    type = SuggestionType.NEXT_WORD,
                    score = WEIGHT_SENTENCE_STARTER,
                    source = "Fallback"
                )
            }.let { results.appendUnique(it) }
        }

        return results.take(limit)
    }
    
    /**
     * Check if we're at the start of a new sentence
     */
    private fun isStartOfSentence(context: List<String>): Boolean {
        if (context.isEmpty()) return true
        
        // Check if the last word ends with sentence-ending punctuation
        val lastWord = context.lastOrNull() ?: return true
        return lastWord.endsWith(".") || lastWord.endsWith("!") || 
               lastWord.endsWith("?") || lastWord.endsWith("\n")
    }
    
    /**
     * Get sentence starter suggestions
     */
    private fun getSentenceStarterSuggestions(): List<UnifiedSuggestion> {
        return SENTENCE_STARTERS.take(MAX_SUGGESTIONS).mapIndexed { index, word ->
            UnifiedSuggestion(
                text = word,
                type = SuggestionType.NEXT_WORD,
                score = WEIGHT_SENTENCE_STARTER * (1.0 - index * 0.05), // Gradually decrease score
                source = "SentenceStarter",
                metadata = mapOf("category" to "sentence_starter")
            )
        }
    }

    private fun Suggestion.toUnifiedSuggestion(
        weight: Double,
        defaultType: SuggestionType = SuggestionType.TYPING
    ): UnifiedSuggestion {
        val derivedType = when {
            isAutoCommit -> SuggestionType.AUTOCORRECT
            source == SuggestionSource.CORRECTION -> SuggestionType.TYPING
            source == SuggestionSource.LM_BIGRAM || source == SuggestionSource.LM_TRIGRAM || source == SuggestionSource.LM_QUADGRAM -> SuggestionType.NEXT_WORD
            else -> defaultType
        }
        val effectiveScore = if (score == 0.0) weight else score * weight
        return UnifiedSuggestion(
            text = text,
            type = derivedType,
            score = effectiveScore,
            source = "AutocorrectEngine",
            metadata = mapOf("source" to source.name),
            isAutoCommit = derivedType == SuggestionType.AUTOCORRECT || isAutoCommit
        )
    }
    
    /**
     * Get emoji suggestions
     */
    private fun getEmojiSuggestions(prefix: String): List<UnifiedSuggestion> {
        return try {
            // Use EmojiSuggestionEngine object directly (singleton)
            val emojis = EmojiSuggestionEngine.getEmojiSuggestions(prefix)
            emojis.take(MAX_EMOJI_SUGGESTIONS).map { emoji ->
                UnifiedSuggestion(
                    text = emoji,
                    type = SuggestionType.EMOJI,
                    score = 0.7 * WEIGHT_EMOJI, // Fixed score for emojis
                    source = "EmojiEngine"
                )
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting emoji suggestions", e)
            emptyList()
        }
    }
    
    /**
     * Get clipboard suggestions (only if new item was copied)
     */
    private fun getClipboardSuggestions(): List<UnifiedSuggestion> {
        return try {
            if (!hasNewClipboardItem) return emptyList()
            
            val recentItems = clipboardHistoryManager.getHistoryItems()
            if (recentItems.isEmpty()) return emptyList()
            
            val item = recentItems.firstOrNull() ?: return emptyList()
            val ageSeconds = (System.currentTimeMillis() - item.timestamp) / 1000
            
            // Only suggest if within time window (120 seconds)
            if (ageSeconds > 120) {
                hasNewClipboardItem = false
                return emptyList()
            }
            
            // Truncate long text for display
            val displayText = if (item.text.length > 30) {
                "${item.text.take(27)}..."
            } else {
                item.text
            }
            
            listOf(
                UnifiedSuggestion(
                    text = item.text,
                    displayText = displayText,
                    type = SuggestionType.CLIPBOARD,
                    score = 0.75 * WEIGHT_CLIPBOARD,
                    source = "Clipboard",
                    metadata = mapOf(
                        "ageSeconds" to ageSeconds,
                        "fullText" to item.text
                    )
                )
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting clipboard suggestions", e)
            emptyList()
        }
    }
    
    /**
     * Rank and deduplicate suggestions
     */
    private fun rankAndDeduplicate(suggestions: List<UnifiedSuggestion>): List<UnifiedSuggestion> {
        return suggestions
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { it.score }
    }
    
    /**
     * Get autocorrect suggestion (for space-bar press)
     */
    suspend fun getAutocorrect(input: String, context: List<String>): UnifiedSuggestion? = withContext(Dispatchers.Default) {
        try {
            val suggestion = unifiedAutocorrectEngine.autocorrect(input, context)
            suggestion?.let {
                UnifiedSuggestion(
                    text = it.text,
                    type = SuggestionType.AUTOCORRECT,
                    score = it.score * WEIGHT_TEXT_SUGGESTION,
                    source = "Autocorrect",
                    isAutoCommit = true,
                    metadata = mapOf("source" to it.source.name)
                )
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting autocorrect", e)
            null
        }
    }
    
    /**
     * Get swipe suggestions
     */
    suspend fun getSwipeSuggestions(path: SwipePath, context: List<String>): List<UnifiedSuggestion> = withContext(Dispatchers.Default) {
        try {
            val suggestions = unifiedAutocorrectEngine.suggestForSwipe(path, context)
            suggestions.map { suggestion ->
                UnifiedSuggestion(
                    text = suggestion.text,
                    type = SuggestionType.TYPING,
                    score = suggestion.score * WEIGHT_TEXT_SUGGESTION,
                    source = "SwipeEngine",
                    metadata = mapOf("source" to suggestion.source.name)
                )
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error getting swipe suggestions", e)
            emptyList()
        }
    }
    
    /**
     * Add suggestion listener
     */
    fun addSuggestionListener(listener: SuggestionListener) {
        if (!suggestionListeners.contains(listener)) {
            suggestionListeners.add(listener)
            LogUtil.d(TAG, "Added suggestion listener")
        }
    }
    
    /**
     * Remove suggestion listener
     */
    fun removeSuggestionListener(listener: SuggestionListener) {
        suggestionListeners.remove(listener)
        LogUtil.d(TAG, "Removed suggestion listener")
    }
    
    /**
     * Notify all listeners of suggestion updates
     */
    private fun notifySuggestionListeners(suggestions: List<UnifiedSuggestion>) {
        suggestionListeners.forEach { listener ->
            try {
                listener.onSuggestionsUpdated(suggestions)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying listener", e)
            }
        }
    }
    
    /**
     * Notify all listeners of errors
     */
    private fun notifyError(error: Exception) {
        suggestionListeners.forEach { listener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying listener of error", e)
            }
        }
    }
    
    /**
     * Clear suggestion cache
     */
    fun clearCache() {
        suggestionCache.evictAll()
        LogUtil.d(TAG, "Suggestion cache cleared")
    }
    
    /**
     * Notify that a new clipboard item was copied
     * Call this when user copies something to show it in suggestions
     */
    fun onNewClipboardItem() {
        hasNewClipboardItem = true
        clearCache() // Clear cache to force new suggestions
        LogUtil.d(TAG, "📋 New clipboard item - will show in suggestions")
    }
    
    /**
     * Clear clipboard suggestion flag (call when user uses the clipboard suggestion)
     */
    fun clearClipboardSuggestion() {
        hasNewClipboardItem = false
        clearCache() // Clear cache to update suggestions
        LogUtil.d(TAG, "📋 Clipboard suggestion cleared")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        suggestionListeners.clear()
        suggestionCache.evictAll()
        LogUtil.d(TAG, "UnifiedSuggestionController cleaned up")
    }
    
    /**
     * Get statistics for debugging
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "cacheSize" to suggestionCache.size(),
            "listenerCount" to suggestionListeners.size,
            "aiEnabled" to aiSuggestionsEnabled,
            "emojiEnabled" to emojiSuggestionsEnabled,
            "clipboardEnabled" to clipboardSuggestionsEnabled,
            "nextWordEnabled" to nextWordPredictionEnabled
        )
    }
}
