package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.trie.MappedTrieDictionary
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.*

/**
 * SwipeDecoderML - V4 (Penalty-Based Scoring + Aspect Ratio Correction)
 * 
 * CRITICAL FIXES:
 * 1. Penalty-Based Scoring: Uses negative costs instead of positive rewards
 *    - More intuitive: 0.0 = perfect, negative = accumulated errors
 *    - Frequency becomes a small positive offset to negative scores
 * 
 * 2. Aspect Ratio Correction: Adjusts Y-distance to match screen physics
 *    - Phones are taller than wide (~2:1 aspect ratio)
 *    - Without correction, vertical swipes appear "longer" than horizontal
 *    - Now: 1cm horizontal = 1cm vertical in scoring
 * 
 * 3. Auto-Normalization Detection: Handles pixel vs normalized coordinates
 *    - Detects if coordinates > 1.0 (pixels) and auto-normalizes
 *    - Works with both coordinate systems transparently
 * 
 * 4. Stricter Filtering: Path threshold -15.0 (was -10.0)
 *    - More aggressive garbage filtering
 * 
 * Algorithm: Beam Search with penalty accumulation
 * - Start at 0.0 (perfect score)
 * - Accumulate penalties for spatial errors, direction misalignment
 * - Add small frequency boost at end
 * - Filter words with excessive penalties (< -15.0)
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>,
    private val dictionary: MappedTrieDictionary
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        
        // Beam search parameters
        private const val BEAM_WIDTH = 30       
        private const val SIGMA = 0.10f         // Stricter spatial tolerance
        
        // PENALTY WEIGHTS (Negative = Cost)
        private const val WAIT_COST = -0.35     // Penalty for dwelling without moving
        private const val DIRECTION_WEIGHT = 2.5 // Weight for directional misalignment penalty
        private const val FREQ_WEIGHT = 0.5      // Frequency boost (small positive offset)
        
        // Path quality threshold
        private const val MIN_PATH_SCORE = -15.0 // More aggressive than V3.1 (-10.0)
    }

    /**
     * Lazy-loaded aspect ratio (height/width)
     * Typical phone: ~2.0 (e.g., 1080x2400)
     * Used to correct Y-axis distance calculations
     */
    private val aspectRatio: Float by lazy {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels > 0) {
            val ratio = metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat()
            LogUtil.d(TAG, "📐 Aspect ratio: ${String.format("%.2f", ratio)} (${metrics.widthPixels}x${metrics.heightPixels})")
            ratio
        } else {
            2.0f // Default fallback
        }
    }

    /**
     * Hypothesis represents a partial word being formed
     * @param score Accumulated penalty (0.0 = perfect, negative = errors)
     */
    data class Hypothesis(
        val text: String,
        val score: Double,      // Penalty score (0.0 best, negative = accumulated errors)
        val nodeOffset: Int,
        val lastChar: Char?,
        val pathIndex: Int
    )

    /**
     * Decode swipe path to candidate words using penalty-based Beam Search
     * 
     * @param path SwipePath with touch points (can be pixel or normalized coordinates)
     * @return List of (word, score) pairs - higher score is better (less negative)
     */
    fun decode(path: SwipePath): List<Pair<String, Double>> {
        if (path.points.size < 3) {
            LogUtil.w(TAG, "Path too short: ${path.points.size} points")
            return emptyList()
        }
        
        try {
            LogUtil.d(TAG, "🔍 V4 Decoding swipe path with ${path.points.size} points")
            
            // 1. AUTO-DETECT and normalize coordinates if needed
            val firstPoint = path.points[0]
            val needsNormalization = firstPoint.first > 1.0f || firstPoint.second > 1.0f
            
            val workingPoints = if (needsNormalization) {
                val metrics = context.resources.displayMetrics
                LogUtil.d(TAG, "🔄 Auto-normalizing pixel coordinates (${firstPoint.first.toInt()}, ${firstPoint.second.toInt()}) → (0-1 range)")
                path.points.map { 
                    Pair(
                        it.first / metrics.widthPixels, 
                        it.second / metrics.heightPixels
                    ) 
                }
            } else {
                path.points
            }

            // 2. Initialize Beam with perfect score (0.0)
            var beam = listOf(Hypothesis("", 0.0, 0, null, 0))
            
            // 3. Iterate through path points (skip every 2nd for performance)
            for (i in 1 until workingPoints.size step 2) {
                val currPoint = workingPoints[i]
                val prevPoint = workingPoints[i - 1]
                
                val nextBeam = mutableListOf<Hypothesis>()

                // Calculate finger direction vector (with aspect ratio correction)
                val fingerDx = currPoint.first - prevPoint.first
                val fingerDy = (currPoint.second - prevPoint.second) * aspectRatio // Fix Y-axis
                val fingerLen = sqrt(fingerDx * fingerDx + fingerDy * fingerDy)

                for (hyp in beam) {
                    // OPTION A: STAY (Dwell on current letter)
                    // Small penalty to encourage movement
                    nextBeam.add(hyp.copy(
                        score = hyp.score + WAIT_COST,  // Add negative penalty
                        pathIndex = i
                    ))

                    // OPTION B: MOVE (Add next letter)
                    val children = dictionary.getChildren(hyp.nodeOffset)

                    for ((char, childOffset) in children) {
                        val keyPos = keyLayout[char] ?: continue
                        
                        // 1. SPATIAL PENALTY (Gaussian log-probability)
                        // Distance = how far touch is from key center
                        val dist = calculateDistance(currPoint.first, currPoint.second, keyPos.first, keyPos.second)
                        // Penalty = -dist² / (2σ²)
                        // Perfect (dist=0): penalty = 0.0
                        // Far (dist=0.2): penalty = -2.0
                        val spatialPenalty = -(dist * dist) / (2 * SIGMA * SIGMA)

                        // 2. DIRECTIONAL PENALTY
                        // Penalize moving away from the target key
                        var directionPenalty = 0.0
                        if (hyp.lastChar != null && fingerLen > 0.02) {
                            val lastKeyPos = keyLayout[hyp.lastChar]
                            if (lastKeyPos != null) {
                                // Ideal vector: lastKey → candidateKey
                                val idealDx = keyPos.first - lastKeyPos.first
                                val idealDy = (keyPos.second - lastKeyPos.second) * aspectRatio // Fix Y-axis
                                val idealLen = sqrt(idealDx * idealDx + idealDy * idealDy)
                                
                                if (idealLen > 0) {
                                    // Dot product: -1.0 (opposite) to +1.0 (aligned)
                                    val dot = (fingerDx * idealDx + fingerDy * idealDy) / (fingerLen * idealLen)
                                    // Convert to penalty: 
                                    // dot = +1.0 (aligned) → penalty = 0.0
                                    // dot = -1.0 (opposite) → penalty = -5.0
                                    directionPenalty = (dot - 1.0) * DIRECTION_WEIGHT
                                }
                            }
                        }

                        // 3. DUPLICATE SUPPRESSION
                        // Don't allow same letter unless very close (strong dwell)
                        if (char == hyp.lastChar && spatialPenalty < -0.2) continue

                        // 4. ACCUMULATE PENALTIES
                        val newScore = hyp.score + spatialPenalty + directionPenalty

                        // Fast pruning: skip if way behind
                        if (nextBeam.isNotEmpty() && newScore < nextBeam[0].score - 20.0) continue

                        nextBeam.add(Hypothesis(
                            text = hyp.text + char,
                            score = newScore,
                            nodeOffset = childOffset,
                            lastChar = char,
                            pathIndex = i
                        ))
                    }
                }

                // 5. PRUNE BEAM
                // Group by nodeOffset to merge identical prefixes
                val uniqueBeam = nextBeam
                    .groupBy { it.nodeOffset }
                    .map { (_, list) -> list.maxByOrNull { it.score }!! }
                
                beam = uniqueBeam.sortedByDescending { it.score }.take(BEAM_WIDTH)
            }

            // 6. FINALIZE & RANK
            val results = beam.mapNotNull { hyp ->
                val freq = dictionary.getFrequencyAtNode(hyp.nodeOffset)
                if (freq > 0) {
                    // FREQUENCY BOOST (small positive offset to negative penalty)
                    // freq=255 → ln(256)*0.5 = +2.77
                    // This moves score from (e.g.) -2.0 to +0.77
                    val freqBoost = ln(freq.toDouble() + 1) * FREQ_WEIGHT
                    val finalScore = hyp.score + freqBoost
                    
                    // AGGRESSIVE FILTERING
                    // If path penalty too high (< -15.0), filter even with frequency boost
                    if (hyp.score < MIN_PATH_SCORE) {
                        LogUtil.d(TAG, "❌ Filtered: '${hyp.text}' (penalty=${String.format("%.2f", hyp.score)} < $MIN_PATH_SCORE)")
                        null
                    } else {
                        Pair(hyp.text, finalScore)
                    }
                } else {
                    null // Not a complete word
                }
            }

            val topResults = results.sortedByDescending { it.second }
                .distinctBy { it.first }
                .take(10)
            
            LogUtil.d(TAG, "✅ V4 Generated ${topResults.size} candidates: ${topResults.take(3).map { "${it.first}(${String.format("%.2f", it.second)})" }}")
            
            return topResults

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in V4 swipe decoding", e)
            return emptyList()
        }
    }

    /**
     * Calculate distance with aspect ratio correction
     * Ensures vertical and horizontal distances are weighted equally
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = (y1 - y2) * aspectRatio // Apply aspect ratio correction to Y-axis
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Compute path score (backward compatibility)
     * Not used in V4 Beam Search but kept for compatibility
     */
    fun computePathScore(word: String, path: SwipePath): Double {
        if (word.isEmpty() || path.points.isEmpty()) return 0.0
        
        try {
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
            return max(0.0, 1.0 - (avgDistance / 0.5))
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error computing path score for '$word'", e)
            return 0.0
        }
    }
}
