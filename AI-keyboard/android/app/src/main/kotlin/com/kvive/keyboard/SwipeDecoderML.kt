package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.utils.LogUtil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SwipeDecoderML - ML-based swipe path decoder for better gesture recognition
 * 
 * Phase 1: Implements geometric path scoring with keyboard proximity
 * Phase 2: Will integrate lightweight on-device ML model (ExecuTorch/TFLite)
 * 
 * Features:
 * - Path-to-word confidence scoring
 * - Keyboard proximity weighting
 * - Length normalization
 * - Gaussian distance modeling
 */
class SwipeDecoderML(
    private val context: Context,
    private val keyLayout: Map<Char, Pair<Float, Float>>
) {
    companion object {
        private const val TAG = "SwipeDecoderML"
        
        // Scoring weights
        private const val PATH_ALIGNMENT_WEIGHT = 0.4
        private const val LENGTH_MATCH_WEIGHT = 0.3
        private const val PROXIMITY_WEIGHT = 0.3
        
        // Distance thresholds (normalized coordinates 0-1)
        private const val KEY_RADIUS = 0.12f
        private const val MAX_DISTANCE_PENALTY = 0.5
    }
    
    /**
     * Decode swipe path to candidate words with confidence scores
     * Returns list of (word, confidence) pairs sorted by confidence
     * 
     * @param path SwipePath containing normalized touch points
     * @return List of word candidates with confidence scores (0.0-1.0)
     */
    fun decode(path: SwipePath): List<Pair<String, Double>> {
        if (path.points.size < 2) {
            LogUtil.w(TAG, "Path too short for decoding: ${path.points.size} points")
            return emptyList()
        }
        
        try {
            LogUtil.d(TAG, "🔍 Decoding swipe path with ${path.points.size} points")
            
            // Extract key sequence from path points
            val keySequence = extractKeySequence(path.points)
            LogUtil.d(TAG, "📝 Key sequence: ${keySequence.joinToString("")}")
            
            // TODO Phase 2: Replace with TFLite/ExecuTorch model inference
            // For now, use geometric scoring as fallback
            val candidates = generateCandidates(keySequence, path)
            
            LogUtil.d(TAG, "✅ Generated ${candidates.size} candidates")
            return candidates.take(10) // Return top 10
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error decoding swipe path", e)
            return emptyList()
        }
    }
    
    /**
     * Extract most likely key sequence from touch points
     * Uses nearest-key detection with proximity weighting
     */
    private fun extractKeySequence(points: List<Pair<Float, Float>>): List<Char> {
        val sequence = mutableListOf<Char>()
        var lastKey: Char? = null
        
        for (point in points) {
            val nearestKey = findNearestKey(point.first, point.second)
            
            // Only add if different from last key (collapse repeats)
            if (nearestKey != null && nearestKey != lastKey) {
                sequence.add(nearestKey)
                lastKey = nearestKey
            }
        }
        
        return sequence
    }
    
    /**
     * Find nearest key to a touch point
     */
    private fun findNearestKey(x: Float, y: Float): Char? {
        var nearestKey: Char? = null
        var minDistance = Float.MAX_VALUE
        
        for ((key, pos) in keyLayout) {
            val distance = calculateDistance(x, y, pos.first, pos.second)
            
            if (distance < minDistance) {
                minDistance = distance
                nearestKey = key
            }
        }
        
        return if (minDistance <= KEY_RADIUS) nearestKey else null
    }
    
    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Generate candidate words from key sequence using geometric path scoring
     * This is the Phase 1 fallback; will be replaced with ML model in Phase 2
     */
    private fun generateCandidates(
        keySequence: List<Char>,
        path: SwipePath
    ): List<Pair<String, Double>> {
        if (keySequence.isEmpty()) return emptyList()
        
        // For Phase 1, just return the collapsed sequence with confidence
        val word = keySequence.joinToString("")
        val confidence = computePathScore(word, path)
        
        return listOf(Pair(word, confidence))
    }
    
    /**
     * Compute path score for a candidate word
     * Uses Gaussian distance model with keyboard proximity
     * 
     * @param word Candidate word to score
     * @param path SwipePath with touch points
     * @return Confidence score (0.0-1.0)
     */
    fun computePathScore(word: String, path: SwipePath): Double {
        if (word.isEmpty() || path.points.isEmpty()) return 0.0
        
        try {
            // 1. Length matching score
            val lengthScore = computeLengthScore(word.length, path.points.size)
            
            // 2. Path alignment score (how well path aligns with word keys)
            val alignmentScore = computeAlignmentScore(word, path.points)
            
            // 3. Proximity score (average distance to expected keys)
            val proximityScore = computeProximityScore(word, path.points)
            
            // Weighted combination
            val finalScore = (PATH_ALIGNMENT_WEIGHT * alignmentScore +
                            LENGTH_MATCH_WEIGHT * lengthScore +
                            PROXIMITY_WEIGHT * proximityScore)
            
            LogUtil.d(TAG, "📊 Path score for '$word': length=$lengthScore, align=$alignmentScore, prox=$proximityScore → $finalScore")
            
            return finalScore.coerceIn(0.0, 1.0)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error computing path score for '$word'", e)
            return 0.0
        }
    }
    
    /**
     * Compute length matching score using Gaussian model
     * Penalizes mismatch between word length and path length
     */
    private fun computeLengthScore(wordLen: Int, pathLen: Int): Double {
        val lenDiff = kotlin.math.abs(wordLen - pathLen).toDouble()
        
        // Gaussian penalty: exp(-0.5 * (diff/sigma)^2)
        val sigma = 2.0 // Allow ~2 character deviation
        return exp(-0.5 * (lenDiff / sigma).pow(2.0))
    }
    
    /**
     * Compute how well the path aligns with the word's key positions
     * Samples path points and measures distance to expected key positions
     */
    private fun computeAlignmentScore(word: String, points: List<Pair<Float, Float>>): Double {
        if (word.isEmpty() || points.isEmpty()) return 0.0
        
        val wordChars = word.lowercase().toCharArray()
        var totalAlignment = 0.0
        var validSamples = 0
        
        // Sample points evenly across the word length
        val sampleIndices = if (points.size <= wordChars.size) {
            points.indices.toList()
        } else {
            (0 until wordChars.size).map { i ->
                (i * (points.size - 1).toFloat() / (wordChars.size - 1).coerceAtLeast(1)).toInt()
            }
        }
        
        for (i in sampleIndices.indices) {
            if (i >= wordChars.size || sampleIndices[i] >= points.size) continue
            
            val point = points[sampleIndices[i]]
            val expectedChar = wordChars[i]
            val expectedPos = keyLayout[expectedChar]
            
            if (expectedPos != null) {
                val distance = calculateDistance(
                    point.first, point.second,
                    expectedPos.first, expectedPos.second
                )
                
                // Convert distance to alignment score (closer = higher score)
                val alignment = kotlin.math.max(0.0, 1.0 - (distance / MAX_DISTANCE_PENALTY))
                totalAlignment += alignment
                validSamples++
            }
        }
        
        return if (validSamples > 0) totalAlignment / validSamples else 0.0
    }
    
    /**
     * Compute average proximity to expected keys
     * Lower average distance = higher proximity score
     */
    private fun computeProximityScore(word: String, points: List<Pair<Float, Float>>): Double {
        if (word.isEmpty() || points.isEmpty()) return 0.0
        
        val wordChars = word.lowercase().toCharArray()
        var totalDistance = 0.0
        var count = 0
        
        // Match each character to closest touch point
        for (char in wordChars) {
            val expectedPos = keyLayout[char] ?: continue
            
            // Find closest point to this key
            var minDist = Float.MAX_VALUE
            for (point in points) {
                val dist = calculateDistance(
                    point.first, point.second,
                    expectedPos.first, expectedPos.second
                )
                if (dist < minDist) minDist = dist
            }
            
            totalDistance += minDist
            count++
        }
        
        if (count == 0) return 0.0
        
        val avgDistance = totalDistance / count
        
        // Convert average distance to proximity score (inverse relationship)
        return kotlin.math.max(0.0, 1.0 - (avgDistance / MAX_DISTANCE_PENALTY))
    }
    
    /**
     * Phase 2 placeholder: Load TFLite/ExecuTorch model
     * Will replace geometric scoring with neural network inference
     */
    @Suppress("unused")
    private fun loadMLModel() {
        // TODO Phase 2: Implement TFLite model loading
        // Model should take path embedding (N x 2 coordinates) and output word probabilities
        LogUtil.d(TAG, "ML model loading not yet implemented (Phase 2)")
    }
    
    /**
     * Phase 2 placeholder: Run ML inference
     * @param pathEmbedding Normalized path coordinates
     * @return Map of word candidates to confidence scores
     */
    @Suppress("unused")
    private fun runMLInference(pathEmbedding: FloatArray): Map<String, Float> {
        // TODO Phase 2: Implement ML inference
        // Input: path embedding (flattened coordinates)
        // Output: word probabilities
        LogUtil.d(TAG, "ML inference not yet implemented (Phase 2)")
        return emptyMap()
    }
}

