package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.utils.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NextWordPredictor - Context-aware next word prediction engine
 * 
 * Phase 1: N-gram based predictions with contextual fallback
 * Phase 2: Will integrate lightweight transformer model (TinyLM/ExecuTorch)
 * 
 * Features:
 * - Context-aware predictions using bigrams/trigrams
 * - Personalized frequency boosting
 * - Fallback to common words
 * - Async prediction for non-blocking UI
 */
class NextWordPredictor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NextWordPredictor"
        
        // Common English words for fallback
        private val COMMON_WORDS = listOf(
            "the", "to", "and", "a", "of", "in", "is", "you", "that", "it",
            "for", "on", "with", "as", "was", "at", "be", "this", "have", "from"
        )
        
        // Context-aware boosted words by previous word
        private val CONTEXTUAL_PAIRS = mapOf(
            "how" to listOf("are", "to", "do", "can", "about"),
            "what" to listOf("is", "are", "do", "does", "about"),
            "when" to listOf("is", "are", "do", "does", "will"),
            "where" to listOf("is", "are", "do", "can", "to"),
            "i" to listOf("am", "have", "will", "can", "think"),
            "you" to listOf("are", "can", "will", "have", "know"),
            "he" to listOf("is", "was", "has", "will", "can"),
            "she" to listOf("is", "was", "has", "will", "can"),
            "they" to listOf("are", "were", "have", "will", "can"),
            "we" to listOf("are", "have", "will", "can", "should"),
            "thank" to listOf("you", "god", "goodness", "heavens"),
            "thanks" to listOf("for", "to", "a", "so"),
            "good" to listOf("morning", "afternoon", "evening", "night", "day"),
            "have" to listOf("a", "to", "been", "you", "not"),
            "can" to listOf("you", "i", "we", "be", "not"),
            "will" to listOf("be", "you", "i", "not", "have"),
            "would" to listOf("you", "be", "like", "have", "not"),
            "should" to listOf("be", "i", "you", "have", "not"),
            "could" to listOf("you", "be", "have", "not", "i")
        )
    }
    
    // User-specific prediction cache (will be populated from user history in Phase 2)
    private val userPredictionCache = mutableMapOf<String, MutableList<String>>()
    
    /**
     * Predict next words based on context
     * 
     * @param contextWords List of previous words (last 1-3 words)
     * @param topK Number of predictions to return
     * @return List of predicted words sorted by confidence
     */
    suspend fun predictNext(contextWords: List<String>, topK: Int = 3): List<String> =
        withContext(Dispatchers.Default) {
            if (contextWords.isEmpty()) {
                LogUtil.d(TAG, "No context provided, returning common words")
                return@withContext COMMON_WORDS.shuffled().take(topK)
            }
            
            try {
                val lastWord = contextWords.last().lowercase().trim()
                LogUtil.d(TAG, "🔮 Predicting next words after: '$lastWord'")
                
                // 1. Try user-specific predictions first
                val userPredictions = getUserPredictions(lastWord)
                if (userPredictions.isNotEmpty()) {
                    LogUtil.d(TAG, "✅ User predictions found: $userPredictions")
                    return@withContext userPredictions.take(topK)
                }
                
                // 2. Try contextual pairs (common bigrams)
                val contextualPredictions = getContextualPredictions(lastWord)
                if (contextualPredictions.isNotEmpty()) {
                    LogUtil.d(TAG, "✅ Contextual predictions: $contextualPredictions")
                    return@withContext contextualPredictions.take(topK)
                }
                
                // 3. Fallback to common words with slight randomization
                val fallback = COMMON_WORDS.shuffled().take(topK)
                LogUtil.d(TAG, "⚠️ Using fallback predictions: $fallback")
                return@withContext fallback
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error predicting next words", e)
                return@withContext COMMON_WORDS.take(topK)
            }
        }
    
    /**
     * Get user-specific predictions from learned patterns
     * Phase 2: Will integrate with UserDictionaryManager for personalized predictions
     */
    private fun getUserPredictions(lastWord: String): List<String> {
        return userPredictionCache[lastWord]?.take(5) ?: emptyList()
    }
    
    /**
     * Get contextual predictions from common bigram patterns
     */
    private fun getContextualPredictions(lastWord: String): List<String> {
        return CONTEXTUAL_PAIRS[lastWord] ?: emptyList()
    }
    
    /**
     * Learn from user input to improve predictions
     * Called when user accepts a prediction or completes a phrase
     * 
     * @param previousWord The word before the current word
     * @param currentWord The word that was actually typed/selected
     */
    fun learnFromInput(previousWord: String, currentWord: String) {
        try {
            val prevLower = previousWord.lowercase().trim()
            val currLower = currentWord.lowercase().trim()
            
            if (prevLower.isEmpty() || currLower.isEmpty()) return
            
            // Add to user cache
            val predictions = userPredictionCache.getOrPut(prevLower) { mutableListOf() }
            
            // Move to front if exists, otherwise add
            predictions.remove(currLower)
            predictions.add(0, currLower)
            
            // Keep only top 10 predictions per word
            if (predictions.size > 10) {
                predictions.removeAt(predictions.lastIndex)
            }
            
            LogUtil.d(TAG, "✨ Learned pattern: '$prevLower' → '$currLower'")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error learning from input", e)
        }
    }
    
    /**
     * Learn from a sequence of words
     * Useful for learning from swipe or voice input
     * 
     * @param words List of consecutive words
     */
    fun learnFromSequence(words: List<String>) {
        if (words.size < 2) return
        
        try {
            for (i in 0 until words.size - 1) {
                learnFromInput(words[i], words[i + 1])
            }
            
            LogUtil.d(TAG, "✨ Learned sequence of ${words.size} words")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error learning from sequence", e)
        }
    }
    
    /**
     * Clear all learned user patterns
     */
    fun clearLearned() {
        userPredictionCache.clear()
        LogUtil.d(TAG, "🗑️ Cleared all learned predictions")
    }
    
    /**
     * Get statistics about learned patterns
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "learned_patterns" to userPredictionCache.size,
            "total_predictions" to userPredictionCache.values.sumOf { it.size }
        )
    }
    
    /**
     * Phase 2 placeholder: Load TinyLM/ExecuTorch model
     * Will replace n-gram predictions with transformer-based predictions
     */
    @Suppress("unused")
    private fun loadTransformerModel() {
        // TODO Phase 2: Implement TinyLM/ExecuTorch loading
        // Model should be quantized and optimized for mobile (< 5MB)
        LogUtil.d(TAG, "Transformer model loading not yet implemented (Phase 2)")
    }
    
    /**
     * Phase 2 placeholder: Run transformer inference
     * @param contextTokens Tokenized context words
     * @return Map of word candidates to probabilities
     */
    @Suppress("unused")
    private fun runTransformerInference(contextTokens: IntArray): Map<String, Float> {
        // TODO Phase 2: Implement transformer inference
        // Input: token IDs for context words
        // Output: next word probabilities
        LogUtil.d(TAG, "Transformer inference not yet implemented (Phase 2)")
        return emptyMap()
    }
}

