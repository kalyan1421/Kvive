package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML V6 - Anchored Spatial Model
 * 
 * Fixes:
 * 1. "Missing First Letter": Added START_RADIUS and heavy START_PENALTY to force immediate anchoring.
 * 2. "Common Word Pruning": Increased BEAM_WIDTH to 60 and added boosts for common words like "test".
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        private const val BEAM_WIDTH = 60 // Increased to keep words like "test" alive
        private const val SIGMA = 0.11f   
        private const val START_RADIUS = 0.16f // Strict radius for first letter (approx 1.5 key width)
        private const val START_PENALTY = -10.0 // Heavy penalty to prevent skipping start points
    }

    data class Hypothesis(
        val text: String,
        val score: Double, 
        val nodeOffset: Int, 
        val lastChar: Char?
    )

    fun decode(path: SwipePath): List<Pair<String, Double>> {
        if (path.points.size < 3) return emptyList()

        try {
            LogUtil.d(TAG, "🔍 V6 Decode: ${path.points.size} points (Anchored)")
            
            // 1. Start with root hypothesis
            var beam = listOf(Hypothesis("", 0.0, 0, null))

            // 2. Iterate points
            for (i in path.points.indices) {
                val (touchX, touchY) = path.points[i]
                val nextBeam = mutableListOf<Hypothesis>()

                for (hyp in beam) {
                    // OPTION A: Move to a new letter
                    val children = dictionary.getChildren(hyp.nodeOffset)
                    for ((char, childOffset) in children) {
                        val keyPos = keyLayout[char] ?: continue
                        
                        // Score: How close is this NEW key?
                        val dist = calculateDistance(touchX, touchY, keyPos.first, keyPos.second)
                        
                        // 🔴 FIX: Start Anchoring
                        // If we are starting a new word (at root), the key MUST be close to the touch point.
                        if (hyp.text.isEmpty() && dist > START_RADIUS) {
                            continue 
                        }

                        val spatialScore = -(dist * dist) / (2 * SIGMA * SIGMA)

                        nextBeam.add(Hypothesis(
                            text = hyp.text + char,
                            score = hyp.score + spatialScore,
                            nodeOffset = childOffset,
                            lastChar = char
                        ))
                    }
                    
                    // OPTION B: Stay on current letter (Self-Loop)
                    if (hyp.lastChar != null) {
                        val keyPos = keyLayout[hyp.lastChar]
                        if (keyPos != null) {
                            val dist = calculateDistance(touchX, touchY, keyPos.first, keyPos.second)
                            val spatialScore = -(dist * dist) / (2 * SIGMA * SIGMA)
                            nextBeam.add(hyp.copy(score = hyp.score + spatialScore))
                        }
                    } else {
                        // 🔴 FIX: Heavy penalty to force the engine to pick a start letter immediately.
                        // Prevents "hovering" at root and skipping the first letter (e.g. 'w' in 'want').
                        nextBeam.add(hyp.copy(score = hyp.score + START_PENALTY))
                    }
                }

                // 3. Prune & Merge
                val merged = HashMap<Int, Hypothesis>()
                for (h in nextBeam) {
                    val existing = merged[h.nodeOffset]
                    if (existing == null || h.score > existing.score) {
                        merged[h.nodeOffset] = h
                    }
                }
                
                beam = merged.values.sortedByDescending { it.score }.take(BEAM_WIDTH)
            }

            // 4. Finalize & Rank
            val results = beam.mapNotNull { hyp ->
                val freq = dictionary.getFrequencyAtNode(hyp.nodeOffset)
                if (freq > 0) {
                    val freqScore = ln(freq.toDouble().coerceAtLeast(1.0))
                    val lengthBonus = hyp.text.length * 0.1
                    val wordPenalty = calculateWordPenalty(hyp.text)
                    val commonBoost = getCommonWordBoost(hyp.text) // Apply common word boost
                    
                    val finalScore = hyp.score + freqScore + lengthBonus - wordPenalty + commonBoost
                    
                    LogUtil.d(TAG, "📊 ${hyp.text} | path=${String.format("%.2f", hyp.score)} freq=$freq pen=$wordPenalty boost=$commonBoost final=${String.format("%.2f", finalScore)}")
                    
                    Pair(hyp.text, finalScore)
                } else {
                    null
                }
            }

            return results.sortedByDescending { it.second }.take(10)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error decoding", e)
            return emptyList()
        }
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
    
    private fun getCommonWordBoost(word: String): Double {
        val topTier = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", 
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "test", "try", "want", "what", "there", "their", "about", "which", "get", "go"
        )
        return if (topTier.contains(word.lowercase())) 1.5 else 0.0
    }

    private fun calculateWordPenalty(word: String): Double {
        if (word.length < 2) return 0.0
        val lower = word.lowercase()
        var penalty = 0.0
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        
        if (lower.length >= 3 && lower.none { it in vowels }) penalty += 5.0
        
        var repeatCount = 0
        for (i in 1 until lower.length) {
            if (lower[i] == lower[i-1]) repeatCount++
        }
        penalty += repeatCount * 1.5
        
        // 🔴 FIX: Don't penalize common words even if they have weird patterns
        if (getCommonWordBoost(word) > 0) return 0.0

        return penalty.coerceAtLeast(0.0)
    }
}
