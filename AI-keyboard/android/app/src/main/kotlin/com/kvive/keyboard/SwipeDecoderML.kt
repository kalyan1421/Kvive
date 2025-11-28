package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML V5 - Spatial Self-Loop Model
 * 
 * Fixes:
 * 1. "2-Letter" Bug: Replaces fixed WAIT_PENALTY with spatial scoring for staying on a key.
 * 2. "Long Word" Bug: Removed aggressive length bonus that favored "terrestres".
 * 3. Beam Diversity: collapses duplicate paths to the same node to keep search wide.
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        private const val BEAM_WIDTH = 40 // Increased beam width for better coverage
        private const val SIGMA = 0.11f   // Spatial tolerance
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
            LogUtil.d(TAG, "🔍 V5 Decode: ${path.points.size} points (Spatial Self-Loop)")
            
            // 1. Start with root hypothesis
            var beam = listOf(Hypothesis("", 0.0, 0, null))

            // 2. Iterate points (Process every point for accuracy)
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
                        val spatialScore = -(dist * dist) / (2 * SIGMA * SIGMA)

                        nextBeam.add(Hypothesis(
                            text = hyp.text + char,
                            score = hyp.score + spatialScore,
                            nodeOffset = childOffset,
                            lastChar = char
                        ))
                    }
                    
                    // OPTION B: Stay on current letter (Self-Loop)
                    // This fixes the "2-letter" bug by allowing the finger to linger
                    // without an arbitrary penalty.
                    if (hyp.lastChar != null) {
                        val keyPos = keyLayout[hyp.lastChar]
                        if (keyPos != null) {
                            // Score: How close are we still to the CURRENT key?
                            val dist = calculateDistance(touchX, touchY, keyPos.first, keyPos.second)
                            val spatialScore = -(dist * dist) / (2 * SIGMA * SIGMA)
                            
                            // Keep same text, same node, update score
                            nextBeam.add(hyp.copy(score = hyp.score + spatialScore))
                        }
                    } else {
                        // If haven't started yet, small penalty to encourage starting
                        nextBeam.add(hyp.copy(score = hyp.score - 0.5))
                    }
                }

                // 3. Prune & Merge
                // Critical: If multiple paths lead to the same dictionary node, keep only the best one.
                // This prevents the beam from filling up with "he", "he", "he" (variations of staying).
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
                    // Log-probability for frequency (handles flat 255 gracefully)
                    val freqScore = ln(freq.toDouble().coerceAtLeast(1.0))
                    
                    // Small length bias just to break ties (e.g. "is" vs "i")
                    val lengthBonus = hyp.text.length * 0.1
                    
                    // 🔴 WORKAROUND: Penalize words with uncommon patterns
                    // Until dictionary frequencies are fixed, use heuristic
                    val wordPenalty = calculateWordPenalty(hyp.text)
                    
                    val finalScore = hyp.score + freqScore + lengthBonus - wordPenalty
                    
                    LogUtil.d(TAG, "📊 ${hyp.text} | path=${String.format("%.2f", hyp.score)} freq=$freq pen=${String.format("%.1f", wordPenalty)} final=${String.format("%.2f", finalScore)}")
                    
                    Pair(hyp.text, finalScore)
                } else {
                    null
                }
            }

            // Return top results
            val finalResults = results.sortedByDescending { it.second }.take(10)
            LogUtil.d(TAG, "✅ V5 Results: ${finalResults.take(5).map { "${it.first}(${String.format("%.1f", it.second)})" }}")
            
            return finalResults

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error decoding", e)
            return emptyList()
        }
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
    
    /**
     * 🔴 WORKAROUND: Calculate penalty for words with uncommon patterns
     * This heuristic helps rank real words higher than junk when freq=255 for all
     * 
     * Penalizes:
     * - Consecutive consonant clusters (e.g., "thtr", "htrw")
     * - Repeated letters (e.g., "tthhee", "wass")
     * - No vowels in 3+ letter words
     * - Unusual letter combinations
     */
    private fun calculateWordPenalty(word: String): Double {
        if (word.length < 2) return 0.0
        
        val lower = word.lowercase()
        var penalty = 0.0
        
        // Set of common vowels
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        
        // 1. Penalize words without vowels (if 3+ letters)
        if (lower.length >= 3 && lower.none { it in vowels }) {
            penalty += 5.0
        }
        
        // 2. Penalize consecutive repeated letters (e.g., "tthhee")
        var repeatCount = 0
        for (i in 1 until lower.length) {
            if (lower[i] == lower[i-1]) {
                repeatCount++
            }
        }
        penalty += repeatCount * 1.5
        
        // 3. Penalize long consonant clusters (3+ consonants in a row)
        var consonantRun = 0
        var maxConsonantRun = 0
        for (c in lower) {
            if (c !in vowels && c.isLetter()) {
                consonantRun++
                maxConsonantRun = maxOf(maxConsonantRun, consonantRun)
            } else {
                consonantRun = 0
            }
        }
        if (maxConsonantRun >= 3) {
            penalty += (maxConsonantRun - 2) * 2.0
        }
        
        // 4. Boost common short words (these should never be penalized)
        val commonWords = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their",
            "what", "so", "up", "out", "if", "about", "who", "get", "which", "go",
            "me", "when", "make", "can", "like", "time", "no", "just", "him", "know",
            "take", "people", "into", "year", "your", "good", "some", "could", "them",
            "see", "other", "than", "then", "now", "look", "only", "come", "its", "over",
            "think", "also", "back", "after", "use", "two", "how", "our", "work",
            "first", "well", "way", "even", "new", "want", "because", "any", "these",
            "give", "day", "most", "us", "is", "are", "was", "were", "been", "has", "had",
            "hello", "world", "thanks", "please", "yes", "no", "okay", "right", "left",
            "three", "four", "five", "six", "seven", "eight", "nine", "ten"
        )
        
        if (lower in commonWords) {
            penalty -= 3.0 // Boost common words
        }
        
        return penalty.coerceAtLeast(0.0)
    }
}
