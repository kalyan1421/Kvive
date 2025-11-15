package com.kvive.keyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * PromptManager v2: Multi-category dynamic prompt storage and retrieval
 * Categories: grammar, tone, assistant
 * Supports dynamic prompt management for AI keyboard features
 */
object PromptManager {
    private const val TAG = "PromptManager"
    private const val PREFS_NAME = "ai_keyboard_prompts_v2"
    private const val OLD_PREFS_NAME = "ai_keyboard_prompts"
    
    // New multi-category keys
    private const val KEY_GRAMMAR_PROMPTS = "prompts_grammar"
    private const val KEY_TONE_PROMPTS = "prompts_tone"
    private const val KEY_ASSISTANT_PROMPTS = "prompts_assistant"
    
    // Legacy keys for backward compatibility
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"
    private const val KEY_PROMPT_TITLE = "prompt_title"
    private const val KEY_PROMPT_CATEGORY = "prompt_category"
    
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private var initialized = false
    private const val SEED_VERSION_KEY = "prompt_seed_version"
    private const val CURRENT_SEED_VERSION = 1

    /**
     * Data class representing a single prompt item
     */
    data class PromptItem(
        val title: String,
        val prompt: String,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJSON(): JSONObject {
            return JSONObject().apply {
                put("title", title)
                put("prompt", prompt)
                put("timestamp", timestamp)
            }
        }
        
        companion object {
            fun fromJSON(json: JSONObject): PromptItem {
                return PromptItem(
                    title = json.optString("title", "Untitled"),
                    prompt = json.optString("prompt", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            }
        }
    }

    /**
     * Initialize PromptManager with context
     */
    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            initialized = true
            
            // Migrate old single prompt if exists
            migrateOldPrompts(appContext)
            
            Log.d(TAG, "✅ PromptManager v2 initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing PromptManager", e)
        }
    }

    /**
     * Migrate old single-prompt storage to new multi-category system
     */
    private fun migrateOldPrompts(context: Context) {
        try {
            val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
            val oldPrompt = oldPrefs.getString(KEY_CUSTOM_PROMPT, null)
            val oldTitle = oldPrefs.getString(KEY_PROMPT_TITLE, "Migrated Prompt")
            val oldCategory = oldPrefs.getString(KEY_PROMPT_CATEGORY, "assistant")
            
            if (!oldPrompt.isNullOrBlank()) {
                // Save to new system
                savePrompt(oldCategory ?: "assistant", oldTitle ?: "Migrated Prompt", oldPrompt)
                Log.d(TAG, "🔄 Migrated old prompt to category: ${oldCategory}")
                
                // Clear old storage
                oldPrefs.edit().clear().apply()
                prefs.edit().remove(KEY_CUSTOM_PROMPT).apply()
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error during migration (non-critical)", e)
        }
    }

    /**
     * Save prompt to a specific category
     */
    fun savePrompt(category: String, title: String, prompt: String): Boolean {
        if (!initialized) {
            Log.w(TAG, "❌ PromptManager not initialized")
            return false
        }
        
        if (prompt.isBlank()) {
            Log.w(TAG, "❌ Cannot save blank prompt")
            return false
        }
        
        return try {
            val key = getKeyForCategory(category)
            val existingJson = prefs.getString(key, "[]") ?: "[]"
            val promptArray = JSONArray(existingJson)
            
            // Check for duplicate titles and replace
            var replaced = false
            for (i in 0 until promptArray.length()) {
                val existingObj = promptArray.getJSONObject(i)
                if (existingObj.optString("title") == title) {
                    // Replace existing prompt with same title
                    promptArray.put(i, PromptItem(title, prompt).toJSON())
                    replaced = true
                    break
                }
            }
            
            // Add new prompt if not replaced
            if (!replaced) {
                promptArray.put(PromptItem(title, prompt).toJSON())
            }
            
            prefs.edit().putString(key, promptArray.toString()).apply()
            notifyPromptsUpdated()
            
            Log.d(TAG, "✅ Prompt saved [$category]: '$title' (${prompt.length} chars)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving prompt", e)
            false
        }
    }

    /**
     * Retrieve all prompts for a category
     */
    fun getPrompts(category: String): List<PromptItem> {
        if (!initialized) {
            Log.w(TAG, "❌ PromptManager not initialized")
            return emptyList()
        }
        
        return try {
            val key = getKeyForCategory(category)
            val jsonString = prefs.getString(key, "[]") ?: "[]"
            val promptArray = JSONArray(jsonString)
            val prompts = mutableListOf<PromptItem>()
            val seedVersion = prefs.getInt(SEED_VERSION_KEY, 0)
            
            for (i in 0 until promptArray.length()) {
                try {
                    val promptObj = promptArray.getJSONObject(i)
                    prompts.add(PromptItem.fromJSON(promptObj))
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Skipping malformed prompt at index $i", e)
                }
            }
            
            val sortedPrompts = prompts.sortedByDescending { it.timestamp }
            if (sortedPrompts.isNotEmpty()) {
                return sortedPrompts
            }

            // Seed defaults if none stored or seed version changed
            val seedPrompts = getSeedPrompts(category)
            if (seedPrompts.isNotEmpty() && seedVersion < CURRENT_SEED_VERSION) {
                persistSeedPrompts(key, seedPrompts)
                return seedPrompts
            }

            return seedPrompts
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading prompts for category: $category", e)
            emptyList()
        }
    }

    /**
     * Retrieve all categories and their prompts
     */
    fun getAllPrompts(): Map<String, List<PromptItem>> {
        val categories = listOf("grammar", "tone", "assistant")
        val allPrompts = mutableMapOf<String, List<PromptItem>>()
        
        categories.forEach { category ->
            allPrompts[category] = getPrompts(category)
        }
        
        return allPrompts
    }

    /**
     * Delete prompt by title from a category
     */
    fun deletePrompt(category: String, title: String): Boolean {
        return removePrompt(category, title)
    }

    /**
     * Remove prompt by title from a category (alias for deletePrompt with broadcast)
     */
    fun removePrompt(category: String, title: String): Boolean {
        if (!initialized) {
            Log.w(TAG, "❌ PromptManager not initialized")
            return false
        }
        
        return try {
            val key = getKeyForCategory(category)
            val jsonString = prefs.getString(key, "[]") ?: "[]"
            val promptArray = JSONArray(jsonString)
            val newArray = JSONArray()
            var found = false
            
            for (i in 0 until promptArray.length()) {
                val promptObj = promptArray.getJSONObject(i)
                val promptTitle = promptObj.optString("title")
                
                if (promptTitle != title) {
                    newArray.put(promptObj)
                } else {
                    found = true
                }
            }
            
            if (found) {
                prefs.edit().putString(key, newArray.toString()).apply()
                Log.d(TAG, "🗑️ Deleted prompt: '$title' from [$category]")
                notifyPromptsUpdated()
            } else {
                Log.w(TAG, "⚠️ Prompt not found for deletion: '$title' in [$category]")
            }
            
            found
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting prompt", e)
            false
        }
    }

    /**
     * Get active prompt text for AI processing (most recent prompt in category)
     */
    fun getActivePrompt(category: String): String {
        val prompts = getPrompts(category)
        return if (prompts.isNotEmpty()) {
            prompts.first().prompt // First is newest due to sorting
        } else {
            getDefaultPrompt(category)
        }
    }

    /**
     * Get fallback default prompts
     */
    fun getDefaultPrompt(category: String): String {
        return when (category.lowercase()) {
            "grammar" -> "Fix grammar and spelling errors while keeping the original meaning and tone intact."
            "tone" -> "Adjust the tone to be more formal, professional, and appropriate for the context."
            "assistant" -> "Improve the text clarity, structure, and impact while maintaining the original meaning."
            else -> "Enhance and improve the text quality."
        }
    }
    
    private fun getSeedPrompts(category: String): List<PromptItem> {
        return when (category.lowercase()) {
            "assistant" -> {
                val now = System.currentTimeMillis()
                listOf(
                    PromptItem(
                        title = "Humanise",
                        prompt = "Rewrite this text to sound more human and natural while maintaining the original meaning.",
                        timestamp = now
                    ),
                    PromptItem(
                        title = "Reply",
                        prompt = "Generate a thoughtful and appropriate reply to this message.",
                        timestamp = now - 1
                    )
                )
            }
            else -> emptyList()
        }
    }

    private fun persistSeedPrompts(key: String, prompts: List<PromptItem>) {
        val array = JSONArray()
        prompts.forEach { array.put(it.toJSON()) }
        prefs.edit()
            .putString(key, array.toString())
            .putInt(SEED_VERSION_KEY, CURRENT_SEED_VERSION)
            .apply()
    }

    /**
     * Get prompt info for UI display
     */
    fun getPromptInfo(category: String): Map<String, Any> {
        val prompts = getPrompts(category)
        val activePrompt = if (prompts.isNotEmpty()) prompts.first() else null
        
        return mapOf(
            "hasPrompt" to (activePrompt != null),
            "title" to (activePrompt?.title ?: "Default"),
            "prompt" to (activePrompt?.prompt ?: getDefaultPrompt(category)),
            "category" to category,
            "count" to prompts.size,
            "timestamp" to (activePrompt?.timestamp ?: 0L)
        )
    }

    /**
     * Get display text for UI
     */
    fun getPromptDisplayText(category: String): String {
        val prompts = getPrompts(category)
        return if (prompts.isNotEmpty()) {
            "🧠 Using custom prompts (${prompts.size} saved)"
        } else {
            "🧠 Using default prompts"
        }
    }

    /**
     * Clear all prompts for a category
     */
    fun clearCategory(category: String): Boolean {
        if (!initialized) return false
        
        return try {
            val key = getKeyForCategory(category)
            prefs.edit().putString(key, "[]").apply()
            Log.d(TAG, "🗑️ Cleared all prompts from category: $category")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing category: $category", e)
            false
        }
    }

    /**
     * Get SharedPreferences key for category
     */
    private fun getKeyForCategory(category: String): String {
        return when (category.lowercase()) {
            "grammar" -> KEY_GRAMMAR_PROMPTS
            "tone" -> KEY_TONE_PROMPTS
            "assistant" -> KEY_ASSISTANT_PROMPTS
            else -> "prompts_${category.lowercase()}"
        }
    }

    /**
     * Get total prompt count across all categories
     */
    fun getTotalPromptCount(): Int {
        return getAllPrompts().values.sumOf { it.size }
    }

    /**
     * Check if any prompts exist
     */
    fun hasAnyPrompts(): Boolean {
        return getTotalPromptCount() > 0
    }

    private fun notifyPromptsUpdated() {
        if (!::appContext.isInitialized) {
            Log.w(TAG, "⚠️ App context unavailable; skipping prompts broadcast")
            return
        }

        try {
            val intent = Intent("com.kvive.keyboard.PROMPTS_UPDATED")
            appContext.sendBroadcast(intent)
            Log.d(TAG, "📢 Broadcasted PROMPTS_UPDATED")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error broadcasting prompt update", e)
        }
    }
}
