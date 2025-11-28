package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML - V3 (Gboard-Quality)
 * 
 * Major Upgrades:
 * 1. Directional Scoring: Compares finger vector vs. key-to-key vector
 * 2. Duplicate Suppression: Prevents "rtti" when you meant "ratio"
 * 3. Frequency Boosting: Common words overpower perfect geometric matches
 * 4. Corner Cutting: Higher Sigma (0.18) allows cutting corners
 * 
 * Algorithm: Beam Search with spatial + directional + frequency scoring
 * - Maintains beam of top 30 hypotheses
 * - Scores based on:
 *   * Spatial proximity (Gaussian)
 *   * Directional alignment (vector dot product)
 *   * Dictionary frequency (log-scaled)
 * - Suppresses duplicate letters unless intentional dwell
 * - Length-normalized scoring for fair comparison
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        
        // 🔥 REBALANCED PARAMETERS (Physics Over Frequency)
        private const val BEAM_WIDTH = 25        // Focused search width
        
        // STRICTER GEOMETRY: 0.10 means you must be within ~10% of key width
        // This forces the engine to actually follow your finger
        private const val SIGMA = 0.10f         // Tightened from 0.18 (was too forgiving)
        
        private const val WAIT_COST = -0.15     // Slight penalty for dwelling
        private const val DIRECTION_WEIGHT = 1.5 // Reduced directional influence (was 4.0)
        
        // 🔥 CRITICAL FIX: MASSIVELY REDUCED FREQUENCY WEIGHT
        // Was 2.5 (giving +13.8 bonus for freq=255) -> Now 0.5 (gives +2.75 bonus)
        // Frequency should be a TIE-BREAKER, not the primary driver
        // This prevents "pikkujoulut" from beating "the" just because it's in dictionary
        private const val FREQ_WEIGHT = 0.5     // Reduced from 2.5
        
        // Path score threshold for garbage filtering
        private const val MIN_PATH_SCORE = -10.0 // If score is worse, completely off-path
    }

    /**
     * Hypothesis represents a partial word being formed during beam search
     * @param text Current text of this hypothesis
     * @param score Accumulated score (higher is better)
     * @param nodeOffset Position in the dictionary Trie
     * @param lastChar Last character added (for duplicate suppression)
     * @param pathIndex Current index in the swipe path
     */
    data class Hypothesis(
        val text: String,
        val score: Double,
        val nodeOffset: Int,
        val lastChar: Char?,
        val pathIndex: Int
    )

    /**
     * Decode swipe path to candidate words using advanced Beam Search
     * 
     * @param path SwipePath containing normalized touch points
     * @return List of (word, score) pairs sorted by confidence (higher is better)
     */
    fun decode(path: SwipePath): List<Pair<String, Double>> {
        if (path.points.size < 3) {
            LogUtil.w(TAG, "Path too short for decoding: ${path.points.size} points")
            return emptyList()
        }
        
        try {
            LogUtil.d(TAG, "🔍 V3 Decoding swipe path with ${path.points.size} points")
            
            // 1. Initialize Beam with empty hypothesis at Trie root
            var beam = listOf(Hypothesis("", 0.0, 0, null, 0))
            
            // 2. Iterate through path (process every 2nd point for performance)
            for (i in 1 until path.points.size step 2) {
                val currPoint = path.points[i]
                val prevPoint = path.points[i - 1] // Use actual previous point for vector
                
                val nextBeam = mutableListOf<Hypothesis>()

                // Calculate finger direction vector (for directional scoring)
                val fingerDx = currPoint.first - prevPoint.first
                val fingerDy = currPoint.second - prevPoint.second
                val fingerLen = sqrt(fingerDx * fingerDx + fingerDy * fingerDy)

                for (hyp in beam) {
                    // --- STRATEGY 1: STAY (Dwell) ---
                    // User is still on the same key
                    // Slight penalty to encourage movement, but allow for intentional long presses
                    nextBeam.add(hyp.copy(
                        score = hyp.score + WAIT_COST,  // WAIT_COST is negative (-0.15)
                        pathIndex = i
                    ))

                    // --- STRATEGY 2: MOVE (Next Letter) ---
                    val children = dictionary.getChildren(hyp.nodeOffset)

                    for ((char, childOffset) in children) {
                        val keyPos = keyLayout[char] ?: continue
                        
                        // 1. Spatial Score (Distance)
                        // How close is the touch point to this key?
                        val dist = calculateDistance(currPoint.first, currPoint.second, keyPos.first, keyPos.second)
                        val spatialScore = -(dist * dist) / (2 * SIGMA * SIGMA)

                        // 2. Directional Score (Alignment)
                        // Does finger movement align with expected key-to-key movement?
                        var directionScore = 0.0
                        if (hyp.lastChar != null && fingerLen > 0.01) {
                            val lastKeyPos = keyLayout[hyp.lastChar]
                            if (lastKeyPos != null) {
                                // Ideal vector from Last Key -> Candidate Key
                                val idealDx = keyPos.first - lastKeyPos.first
                                val idealDy = keyPos.second - lastKeyPos.second
                                val idealLen = sqrt(idealDx * idealDx + idealDy * idealDy)
                                
                                if (idealLen > 0) {
                                    // Dot product for alignment (-1.0 to 1.0)
                                    val dot = (fingerDx * idealDx + fingerDy * idealDy) / (fingerLen * idealLen)
                                    // Reward positive alignment, slightly penalize negative
                                    directionScore = if (dot > 0) dot * DIRECTION_WEIGHT else dot * DIRECTION_WEIGHT * 0.5
                                }
                            }
                        }

                        // 3. Duplicate Suppression
                        // Don't allow adding the same letter again unless we moved away significantly or dwelled long
                        // This prevents "rtti" when you mean "rt" or "ratio"
                        if (char == hyp.lastChar) {
                            // Only allow double letters if spatial score is very high (strong dwell)
                            // Otherwise heavily penalize "jitter" repeats
                            if (spatialScore < -0.5) continue 
                        }

                        val newScore = hyp.score + spatialScore + directionScore

                        // Pruning: fast fail for low scores
                        if (nextBeam.isNotEmpty() && newScore < nextBeam[0].score - 15.0) continue

                        nextBeam.add(Hypothesis(
                            text = hyp.text + char,
                            score = newScore,
                            nodeOffset = childOffset,
                            lastChar = char,
                            pathIndex = i
                        ))
                    }
                }

                // Prune Beam
                // Group by NodeOffset to merge identical paths (e.g. distinct paths to same word prefix)
                val uniqueBeam = nextBeam
                    .groupBy { it.nodeOffset }
                    .map { (_, list) -> list.maxByOrNull { it.score }!! }
                
                beam = uniqueBeam.sortedByDescending { it.score }.take(BEAM_WIDTH)
            }

            // 3. Finalize and Rank Results
            val results = beam.mapNotNull { hyp ->
                val freq = dictionary.getFrequencyAtNode(hyp.nodeOffset)
                if (freq > 0) {
                    // 🔥 CRITICAL FIX: LOGARITHMIC DAMPENING
                    // Instead of massive multiplier, use small boost as tie-breaker
                    // freq (0-255) -> ln(freq+1) (0-5.5) -> scaled (0-2.75)
                    val freqScore = ln(freq.toDouble() + 1) * FREQ_WEIGHT
                    
                    // 🔥 REMOVED LENGTH NORMALIZATION
                    // Length normalization was hiding bad paths by averaging out poor scores
                    // Now: Path score directly reflects how well finger followed keys
                    val finalScore = hyp.score + freqScore
                    
                    // 🔥 FILTRATION: PRUNE GARBAGE
                    // If path score is too low (< -10.0), user didn't touch these keys
                    // Throw it away even if it's a dictionary word
                    // This kills "pikkujoulut" when you swipe "the"
                    if (hyp.score < MIN_PATH_SCORE) {
                        LogUtil.d(TAG, "❌ Filtered garbage: '${hyp.text}' (pathScore=${String.format("%.2f", hyp.score)} < $MIN_PATH_SCORE)")
                        null
                    } else {
                        Pair(hyp.text, finalScore)
                    }
                } else {
                    null
                }
            }

            val topResults = results.sortedByDescending { it.second }.distinctBy { it.first }.take(10)
            
            LogUtil.d(TAG, "✅ V3 Generated ${topResults.size} candidates: ${topResults.take(3).map { "${it.first}(${String.format("%.2f", it.second)})" }}")
            
            return topResults

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in V3 swipe decoding", e)
            return emptyList()
        }
    }
    
    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    /**
     * Compute path score for a candidate word (for compatibility with existing code)
     * This is kept for backward compatibility but isn't used in the new V3 Beam Search
     * 
     * @param word Candidate word to score
     * @param path SwipePath with touch points
     * @return Confidence score (0.0-1.0)
     */
    fun computePathScore(word: String, path: SwipePath): Double {
        if (word.isEmpty() || path.points.isEmpty()) return 0.0
        
        try {
            // Simple distance-based scoring for compatibility
            var totalDistance = 0.0
            var count = 0
        
        val wordChars = word.lowercase().toCharArray()
            val sampleIndices = (0 until wordChars.size).map { i ->
                (i * (path.points.size - 1).toFloat() / (wordChars.size - 1).coerceAtLeast(1)).toInt()
        }
        
        for (i in sampleIndices.indices) {
                if (i >= wordChars.size || sampleIndices[i] >= path.points.size) continue
            
                val point = path.points[sampleIndices[i]]
            val expectedChar = wordChars[i]
                val expectedPos = keyLayout[expectedChar] ?: continue
            
                val distance = calculateDistance(
                    point.first, point.second,
                    expectedPos.first, expectedPos.second
                )
                
                totalDistance += distance
                count++
        }
        
        if (count == 0) return 0.0
        
        val avgDistance = totalDistance / count
            // Convert to 0-1 score (closer = higher score)
            return max(0.0, 1.0 - (avgDistance / 0.5))
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error computing path score for '$word'", e)
            return 0.0
        }
    }
}
