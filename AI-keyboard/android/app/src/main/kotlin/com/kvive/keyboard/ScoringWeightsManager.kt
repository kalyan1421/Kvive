package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import com.kvive.keyboard.utils.LogUtil

/**
 * ScoringWeightsManager - Tunable scoring weights for autocorrect and suggestions
 * 
 * Allows dynamic adjustment of suggestion ranking weights without code changes.
 * Can be exposed to Flutter UI for user customization of keyboard aggressiveness.
 * 
 * Features:
 * - Persistent weight storage via SharedPreferences
 * - Live weight updates without keyboard restart
 * - Preset profiles (Conservative, Balanced, Aggressive)
 * - Export/import for backup and sync
 */
class ScoringWeightsManager(context: Context) {
    companion object {
        private const val TAG = "ScoringWeightsManager"
        private const val PREFS_NAME = "scoring_weights"
        
        // Default weights (balanced profile)
        private const val DEFAULT_EDIT_WEIGHT = 1.0f
        private const val DEFAULT_LM_WEIGHT = 2.0f
        private const val DEFAULT_SWIPE_WEIGHT = 1.5f
        private const val DEFAULT_USER_WEIGHT = 3.0f
        private const val DEFAULT_CORRECTION_WEIGHT = 4.0f
        private const val DEFAULT_FREQUENCY_WEIGHT = 1.0f
        private const val DEFAULT_CONTEXT_WEIGHT = 1.5f
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Scoring weight profiles for different user preferences
     */
    enum class Profile {
        CONSERVATIVE,  // Minimal autocorrect interference
        BALANCED,      // Moderate autocorrect (default)
        AGGRESSIVE     // Strong autocorrect and suggestions
    }
    
    // ========== WEIGHT PROPERTIES ==========
    
    /**
     * Edit distance weight - Higher = stronger penalty for typos
     */
    var editWeight: Float
        get() = prefs.getFloat("edit_weight", DEFAULT_EDIT_WEIGHT)
        set(value) {
            prefs.edit().putFloat("edit_weight", value.coerceIn(0.1f, 5.0f)).apply()
            LogUtil.d(TAG, "✏️ Edit weight updated: $value")
        }
    
    /**
     * Language model weight - Higher = stronger preference for common word patterns
     */
    var lmWeight: Float
        get() = prefs.getFloat("lm_weight", DEFAULT_LM_WEIGHT)
        set(value) {
            prefs.edit().putFloat("lm_weight", value.coerceIn(0.1f, 5.0f)).apply()
            LogUtil.d(TAG, "📚 LM weight updated: $value")
        }
    
    /**
     * Swipe path weight - Higher = stronger confidence in swipe gestures
     */
    var swipeWeight: Float
        get() = prefs.getFloat("swipe_weight", DEFAULT_SWIPE_WEIGHT)
        set(value) {
            prefs.edit().putFloat("swipe_weight", value.coerceIn(0.1f, 5.0f)).apply()
            LogUtil.d(TAG, "🔄 Swipe weight updated: $value")
        }
    
    /**
     * User dictionary weight - Higher = stronger preference for learned words
     */
    var userWeight: Float
        get() = prefs.getFloat("user_weight", DEFAULT_USER_WEIGHT)
        set(value) {
            prefs.edit().putFloat("user_weight", value.coerceIn(0.1f, 10.0f)).apply()
            LogUtil.d(TAG, "👤 User weight updated: $value")
        }
    
    /**
     * Correction weight - Higher = stronger confidence in predefined corrections
     */
    var correctionWeight: Float
        get() = prefs.getFloat("correction_weight", DEFAULT_CORRECTION_WEIGHT)
        set(value) {
            prefs.edit().putFloat("correction_weight", value.coerceIn(0.1f, 10.0f)).apply()
            LogUtil.d(TAG, "🔧 Correction weight updated: $value")
        }
    
    /**
     * Frequency weight - Higher = stronger preference for common words
     */
    var frequencyWeight: Float
        get() = prefs.getFloat("frequency_weight", DEFAULT_FREQUENCY_WEIGHT)
        set(value) {
            prefs.edit().putFloat("frequency_weight", value.coerceIn(0.1f, 5.0f)).apply()
            LogUtil.d(TAG, "📊 Frequency weight updated: $value")
        }
    
    /**
     * Context weight - Higher = stronger preference for contextually relevant words
     */
    var contextWeight: Float
        get() = prefs.getFloat("context_weight", DEFAULT_CONTEXT_WEIGHT)
        set(value) {
            prefs.edit().putFloat("context_weight", value.coerceIn(0.1f, 5.0f)).apply()
            LogUtil.d(TAG, "🔮 Context weight updated: $value")
        }
    
    // ========== PROFILE MANAGEMENT ==========
    
    /**
     * Get current profile
     */
    fun getCurrentProfile(): Profile {
        val profileName = prefs.getString("current_profile", Profile.BALANCED.name)
        return try {
            Profile.valueOf(profileName ?: Profile.BALANCED.name)
        } catch (e: Exception) {
            Profile.BALANCED
        }
    }
    
    /**
     * Apply a preset profile
     */
    fun applyProfile(profile: Profile) {
        when (profile) {
            Profile.CONSERVATIVE -> applyConservativeProfile()
            Profile.BALANCED -> applyBalancedProfile()
            Profile.AGGRESSIVE -> applyAggressiveProfile()
        }
        
        prefs.edit().putString("current_profile", profile.name).apply()
        LogUtil.i(TAG, "🎯 Applied profile: $profile")
    }
    
    /**
     * Conservative profile - Minimal interference
     * Good for users who want full control and minimal autocorrect
     */
    private fun applyConservativeProfile() {
        editWeight = 0.5f          // Low edit distance penalty
        lmWeight = 1.0f            // Low language model influence
        swipeWeight = 1.0f         // Standard swipe confidence
        userWeight = 5.0f          // High user dictionary preference
        correctionWeight = 2.0f    // Low correction aggressiveness
        frequencyWeight = 0.5f     // Low frequency bias
        contextWeight = 0.8f       // Low context influence
    }
    
    /**
     * Balanced profile - Moderate autocorrect (DEFAULT)
     * Good balance between assistance and user control
     */
    private fun applyBalancedProfile() {
        editWeight = DEFAULT_EDIT_WEIGHT
        lmWeight = DEFAULT_LM_WEIGHT
        swipeWeight = DEFAULT_SWIPE_WEIGHT
        userWeight = DEFAULT_USER_WEIGHT
        correctionWeight = DEFAULT_CORRECTION_WEIGHT
        frequencyWeight = DEFAULT_FREQUENCY_WEIGHT
        contextWeight = DEFAULT_CONTEXT_WEIGHT
    }
    
    /**
     * Aggressive profile - Strong autocorrect
     * Good for users who want maximum assistance and correction
     */
    private fun applyAggressiveProfile() {
        editWeight = 2.0f          // High edit distance penalty
        lmWeight = 3.0f            // High language model influence
        swipeWeight = 2.5f         // High swipe confidence
        userWeight = 4.0f          // Moderate user dictionary preference
        correctionWeight = 6.0f    // High correction aggressiveness
        frequencyWeight = 2.0f     // High frequency bias
        contextWeight = 2.5f       // High context influence
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Reset all weights to defaults (balanced profile)
     */
    fun resetToDefaults() {
        applyProfile(Profile.BALANCED)
        LogUtil.i(TAG, "🔄 Reset all weights to defaults")
    }
    
    /**
     * Get all weights as a map (for debugging and export)
     */
    fun getAllWeights(): Map<String, Float> {
        return mapOf(
            "edit" to editWeight,
            "lm" to lmWeight,
            "swipe" to swipeWeight,
            "user" to userWeight,
            "correction" to correctionWeight,
            "frequency" to frequencyWeight,
            "context" to contextWeight
        )
    }
    
    /**
     * Import weights from a map (for restore and sync)
     */
    fun importWeights(weights: Map<String, Float>) {
        try {
            weights["edit"]?.let { editWeight = it }
            weights["lm"]?.let { lmWeight = it }
            weights["swipe"]?.let { swipeWeight = it }
            weights["user"]?.let { userWeight = it }
            weights["correction"]?.let { correctionWeight = it }
            weights["frequency"]?.let { frequencyWeight = it }
            weights["context"]?.let { contextWeight = it }
            
            LogUtil.i(TAG, "✅ Imported ${weights.size} weight settings")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error importing weights", e)
        }
    }
    
    /**
     * Get statistics about current configuration
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "profile" to getCurrentProfile().name,
            "weights" to getAllWeights(),
            "total_adjustments" to prefs.getInt("adjustment_count", 0)
        )
    }
    
    /**
     * Track weight adjustments for analytics
     */
    fun recordAdjustment() {
        val count = prefs.getInt("adjustment_count", 0) + 1
        prefs.edit().putInt("adjustment_count", count).apply()
    }
    
    /**
     * Validate that all weights are in acceptable ranges
     */
    fun validateWeights(): Boolean {
        val weights = getAllWeights()
        
        return weights.values.all { weight ->
            weight in 0.1f..10.0f
        }
    }
}

