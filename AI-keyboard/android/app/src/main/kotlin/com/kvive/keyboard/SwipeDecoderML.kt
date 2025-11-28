package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML V8 - Fast-Swipe Interpolation & Scaled Frequency
 * 
 * Fixes:
 * 1. "Fast Swipe" Bug: Added interpolatePath() to fill gaps between fast points (e.g. Y..E..S).
 * 2. "Frequency Bug": Adjusted boost thresholds to match 0-255 dictionary scale.
 * 3. "Common Word Priority": Tiered boosting ensures "yes" (201) beats "tyre" (154).
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        private const val BEAM_WIDTH = 60 
        private const val SIGMA = 0.14f   // Relaxed Sigma for faster/sloppier swipes
        private const val START_RADIUS = 0.18f 
        private const val START_PENALTY = -8.0
        private const val SPATIAL_CLAMP = -3.5 // Caps penalty for a single bad point
        private const val INTERPOLATION_STEP = 0.05f // Distance to fill gaps in fast swipes
    }

    data class Hypothesis(
        val text: String,
        val score: Double, 
        val nodeOffset: Int, 
        val lastChar: Char?
    )

    fun decode(rawPath: SwipePath): List<Pair<String, Double>> {
        // 1. Interpolate path to handle fast swipes (prevents skipping letters)
        val path = interpolatePath(rawPath)
        
        if (path.points.size < 3) return emptyList()

        try {
            LogUtil.d(TAG, "🔍 V8 Decode: ${path.points.size} points (Interpolated)")
            
            var beam = listOf(Hypothesis("", 0.0, 0, null))

            for (i in path.points.indices) {
                // Explicit Cast to Float to fix type mismatch
                val point = path.points[i]
                val touchX = point.first.toFloat()
                val touchY = point.second.toFloat()
                
                val nextBeam = mutableListOf<Hypothesis>()

                for (hyp in beam) {
                    val children = dictionary.getChildren(hyp.nodeOffset)
                    for ((char, childOffset) in children) {
                        val keyPos = keyLayout[char] ?: continue
                        val keyX = keyPos.first.toFloat()
                        val keyY = keyPos.second.toFloat()
                        
                        val dist = calculateDistance(touchX, touchY, keyX, keyY)
                        
                        if (hyp.text.isEmpty() && dist > START_RADIUS) {
                            continue 
                        }

                        // Clamped spatial score allows for one bad point (corner cutting)
                        val rawSpatial = -(dist * dist) / (2 * SIGMA * SIGMA)
                        val spatialScore = rawSpatial.toDouble().coerceAtLeast(SPATIAL_CLAMP)

                        nextBeam.add(Hypothesis(
                            text = hyp.text + char,
                            score = hyp.score + spatialScore,
                            nodeOffset = childOffset,
                            lastChar = char
                        ))
                    }
                    
                    // Self-Loop (Staying on the same key)
                    if (hyp.lastChar != null) {
                        val keyPos = keyLayout[hyp.lastChar]
                        if (keyPos != null) {
                            val keyX = keyPos.first.toFloat()
                            val keyY = keyPos.second.toFloat()
                            val dist = calculateDistance(touchX, touchY, keyX, keyY)
                            val rawSpatial = -(dist * dist) / (2 * SIGMA * SIGMA)
                            val spatialScore = rawSpatial.toDouble().coerceAtLeast(SPATIAL_CLAMP)
                            
                            nextBeam.add(hyp.copy(score = hyp.score + spatialScore))
                        }
                    } else {
                        nextBeam.add(hyp.copy(score = hyp.score + START_PENALTY))
                    }
                }

                // Prune
                val merged = HashMap<Int, Hypothesis>()
                for (h in nextBeam) {
                    val existing = merged[h.nodeOffset]
                    if (existing == null || h.score > existing.score) {
                        merged[h.nodeOffset] = h
                    }
                }
                
                beam = merged.values.sortedByDescending { it.score }.take(BEAM_WIDTH)
            }

            // Final Ranking
            val results = beam.mapNotNull { hyp ->
                val freq = dictionary.getFrequencyAtNode(hyp.nodeOffset)
                if (freq > 0) {
                    val freqScore = ln(freq.toDouble().coerceAtLeast(1.0))
                    val lengthBonus = hyp.text.length * 0.1
                    val wordPenalty = calculateWordPenalty(hyp.text)
                    
                    // 🔴 FIX: Tiered Boosting for 0-255 scale
                    // freq 201 ("yes") -> +4.0
                    // freq 154 ("tyre") -> +1.0
                    // Result: "yes" gains +3.0 advantage over "tyre"
                    val freqBoost = when {
                        freq >= 180 -> 4.0
                        freq >= 120 -> 1.0
                        else -> 0.0
                    }
                    
                    val finalScore = hyp.score + freqScore + lengthBonus - wordPenalty + freqBoost
                    
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

    /**
     * Fills gaps in fast swipes. If points are too far apart, generates intermediate points.
     */
    private fun interpolatePath(original: SwipePath): SwipePath {
        if (original.points.size < 2) return original
        
        val newPoints = mutableListOf<Pair<Float, Float>>()
        newPoints.add(original.points[0])
        
        for (i in 0 until original.points.size - 1) {
            val p1 = original.points[i]
            val p2 = original.points[i+1]
            
            // Calculate distances using Doubles for precision before casting
            val dx = p2.first - p1.first
            val dy = p2.second - p1.second
            val dist = sqrt((dx * dx).toDouble() + (dy * dy).toDouble()).toFloat()
            
            // If gap is large (fast swipe), insert points
            if (dist > INTERPOLATION_STEP) {
                val steps = (dist / INTERPOLATION_STEP).toInt()
                for (j in 1..steps) {
                    val fraction = j.toFloat() / (steps + 1)
                    val nx = p1.first + dx * fraction
                    val ny = p1.second + dy * fraction
                    newPoints.add(Pair(nx, ny))
                }
            }
            newPoints.add(p2)
        }
        
        return SwipePath(newPoints)
    }

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx).toDouble() + (dy * dy).toDouble()).toFloat()
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
        
        return penalty.coerceAtLeast(0.0)
    }
}
