package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML V7 - Robust Anchored Model
 * 
 * Fixes:
 * 1. "Detour" Bug (Money vs Monterey): Added spatial score clamping. Bad points don't kill good words.
 * 2. "Read" Bug: Relaxed START_RADIUS and replaced static list with dynamic frequency boosting.
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        private const val BEAM_WIDTH = 60 
        private const val SIGMA = 0.12f   // Slightly relaxed sigma
        private const val START_RADIUS = 0.21f // Relaxed start radius (approx 2 key widths)
        private const val START_PENALTY = -8.0 // Strong anchoring but slightly more forgiving
        private const val SPATIAL_CLAMP = -4.0 // Maximum penalty per point (Robustness fix)
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
            LogUtil.d(TAG, "🔍 V7 Decode: ${path.points.size} points (Robust)")
            
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
                        
                        // Anchoring Check
                        if (hyp.text.isEmpty() && dist > START_RADIUS) {
                            continue 
                        }

                        // 🔴 FIX: Clamped Spatial Score (Robustness)
                        // If dist is huge (detour), penalty stops at -4.0 instead of -100.0
                        // This allows "money" to survive even if you swiped over "t" and "r".
                        val rawSpatial = -(dist * dist) / (2 * SIGMA * SIGMA)
                        val spatialScore = rawSpatial.toDouble().coerceAtLeast(SPATIAL_CLAMP)

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
                            val rawSpatial = -(dist * dist) / (2 * SIGMA * SIGMA)
                            val spatialScore = rawSpatial.toDouble().coerceAtLeast(SPATIAL_CLAMP)
                            
                            nextBeam.add(hyp.copy(score = hyp.score + spatialScore))
                        }
                    } else {
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
                    
                    // 🔴 FIX: Dynamic Frequency Boost
                    // Boost ANY word with high frequency (common words like "read", "money")
                    val freqBoost = if (freq > 50000) 2.5 else 0.0
                    
                    val finalScore = hyp.score + freqScore + lengthBonus - wordPenalty + freqBoost
                    
                    // Log only top candidates to reduce noise
                    if (finalScore > -20) {
                         LogUtil.d(TAG, "📊 ${hyp.text} | path=${String.format("%.2f", hyp.score)} freq=$freq boost=$freqBoost final=${String.format("%.2f", finalScore)}")
                    }
                    
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

    private fun calculateWordPenalty(word: String): Double {
        if (word.length < 2) return 0.0
        val lower = word.lowercase()
        var penalty = 0.0
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
        
        if (lower.length >= 3 && lower.none { it in vowels }) penalty += 5.0
        
        var repeatCount = 0
        for (i in 1 until lower.length) {
            if (lower[i] == lower[i-1]) repeatCount++
        }
        penalty += repeatCount * 1.5
        
        // Don't penalize very frequent words
        // This logic is now handled primarily by freqBoost in main loop
        return penalty.coerceAtLeast(0.0)
    }
}
