package com.kvive.keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UnifiedAIService: Combines tone adjustment, grammar fixing, text rewriting, and streaming
 * into one unified interface for Flutter communication.
 */
class UnifiedAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedAIService"
    }
    
    private val advanced = AdvancedAIService(context)
    private val streaming = StreamingAIService(context)
    private val cache = AIResponseCache(context)
    private val config = OpenAIConfig.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Processing modes for unified API
     */
    enum class Mode {
        TONE,          // Tone adjustment
        GRAMMAR,       // Grammar correction
        CUSTOM,        // Custom prompt processing
        REWRITE,       // Text rewriting
        FEATURE        // Specific feature processing
    }
    
    /**
     * Unified result data class
     */
    data class UnifiedResult(
        val success: Boolean,
        val text: String,
        val error: String? = null,
        val fromCache: Boolean = false,
        val time: Long = 0L,
        val isComplete: Boolean = true
    )
    
    /**
     * Unified processing entrypoint
     */
    fun processText(
        text: String,
        mode: Mode,
        tone: AdvancedAIService.ToneType? = null,
        feature: AdvancedAIService.ProcessingFeature? = null,
        stream: Boolean = false,
        customPrompt: String? = null
    ): Flow<UnifiedResult> = flow {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Processing text with mode: $mode, stream: $stream")
            
            PromptManager.init(context)
            // Get active prompt for this mode before processing
            val activePrompt = PromptManager.getActivePrompt(mode.name.lowercase())
            Log.d(TAG, "🧩 Active prompt refreshed for mode=$mode")
            Log.d(TAG, "📝 Using prompt for $mode: ${activePrompt.take(50)}...")

            val resolvedCustomPrompt = when (mode) {
                Mode.CUSTOM, Mode.REWRITE -> {
                    val provided = customPrompt?.trim()
                    when {
                        !provided.isNullOrEmpty() -> provided
                        activePrompt.isNotBlank() -> activePrompt
                        else -> PromptManager.getDefaultPrompt(mode.name.lowercase())
                    }
                }
                else -> null
            }
            
            // Check if AI features are enabled
            if (!config.isAIFeaturesEnabled()) {
                emit(UnifiedResult(
                    success = false,
                    text = "",
                    error = "AI features disabled. Please enable them in settings."
                ))
                return@flow
            }
            
            // Check if API key is configured
            if (!config.hasApiKey()) {
                emit(UnifiedResult(
                    success = false,
                    text = "",
                    error = "AI API key not configured. Please set up your OpenAI API key."
                ))
                return@flow
            }
            
            if (stream) {
                // Handle streaming responses
                handleStreamingRequest(text, mode, tone, feature, startTime, resolvedCustomPrompt)
                    .collect { result ->
                        emit(result)
                    }
            } else {
                // Handle non-streaming responses
                val result = handleNonStreamingRequest(text, mode, tone, feature, startTime, resolvedCustomPrompt)
                emit(result)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in unified text processing", e)
            emit(UnifiedResult(
                success = false,
                text = "",
                error = "Processing error: ${e.message}",
                time = System.currentTimeMillis() - startTime
            ))
        }
    }.flowOn(Dispatchers.IO)

    fun processCustomPrompt(
        text: String,
        prompt: String,
        stream: Boolean = true
    ): Flow<UnifiedResult> = flow {
        val startTime = System.currentTimeMillis()
        val systemPrompt = prompt.ifBlank { PromptManager.getDefaultPrompt("assistant") }
        val cacheSuffix = "custom_${systemPrompt.hashCode()}"

        try {
            if (!config.isAIFeaturesEnabled()) {
                emit(
                    UnifiedResult(
                        success = false,
                        text = "",
                        error = "AI features disabled. Please enable them in settings."
                    )
                )
                return@flow
            }

            if (!config.hasApiKey()) {
                emit(
                    UnifiedResult(
                        success = false,
                        text = "",
                        error = "AI API key not configured. Please set up your OpenAI API key."
                    )
                )
                return@flow
            }

            if (stream) {
                streaming.processTextStreaming(text, systemPrompt, cacheSuffix)
                    .collect { chunk ->
                        emit(
                            UnifiedResult(
                                success = true,
                                text = chunk.currentText,
                                fromCache = chunk.fromCache,
                                time = System.currentTimeMillis() - startTime,
                                isComplete = chunk.isComplete
                            )
                        )
                    }
            } else {
                val result = advanced.processCustomPrompt(text, systemPrompt, cacheSuffix)
                emit(
                    UnifiedResult(
                        success = result.success,
                        text = result.text,
                        error = result.error,
                        fromCache = result.fromCache,
                        time = result.processingTimeMs
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing custom prompt", e)
            emit(
                UnifiedResult(
                    success = false,
                    text = "",
                    error = "Processing error: ${e.message}",
                    time = System.currentTimeMillis() - startTime
                )
            )
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Handle streaming AI requests
     */
    private fun handleStreamingRequest(
        text: String,
        mode: Mode,
        tone: AdvancedAIService.ToneType?,
        feature: AdvancedAIService.ProcessingFeature?,
        startTime: Long,
        customPrompt: String?
    ): Flow<UnifiedResult> = flow {
        
        val streamFlow = when (mode) {
            Mode.TONE -> {
                // Use default tone adjustment
                tone?.let { streaming.adjustToneStreaming(text, it) }
                    ?: throw IllegalArgumentException("Tone type required for TONE mode")
            }
            Mode.GRAMMAR -> {
                // Use default grammar fix
                streaming.processFeatureStreaming(text, AdvancedAIService.ProcessingFeature.GRAMMAR_FIX)
            }
            Mode.FEATURE -> {
                // Use default feature processing
                feature?.let { streaming.processFeatureStreaming(text, it) }
                    ?: throw IllegalArgumentException("Feature type required for FEATURE mode")
            }
            Mode.CUSTOM -> {
                val prompt = customPrompt ?: PromptManager.getDefaultPrompt("assistant")
                streaming.processTextStreaming(
                    text,
                    prompt,
                    buildCustomCacheKey(mode, prompt)
                )
            }
            Mode.REWRITE -> {
                val prompt = customPrompt ?: PromptManager.getDefaultPrompt("rewrite")
                streaming.processTextStreaming(
                    text,
                    prompt,
                    buildCustomCacheKey(mode, prompt)
                )
            }
        }
        
        streamFlow.collect { chunk ->
            emit(UnifiedResult(
                success = true,
                text = chunk.currentText,
                fromCache = chunk.fromCache,
                time = System.currentTimeMillis() - startTime,
                isComplete = chunk.isComplete
            ))
        }
    }
    
    /**
     * Handle non-streaming AI requests
     */
    private suspend fun handleNonStreamingRequest(
        text: String,
        mode: Mode,
        tone: AdvancedAIService.ToneType?,
        feature: AdvancedAIService.ProcessingFeature?,
        startTime: Long,
        customPrompt: String?
    ): UnifiedResult {
        
        val result = when (mode) {
            Mode.TONE -> {
                tone?.let { advanced.adjustTone(text, it) }
                    ?: throw IllegalArgumentException("Tone type required for TONE mode")
            }
            Mode.GRAMMAR -> {
                advanced.processText(text, AdvancedAIService.ProcessingFeature.GRAMMAR_FIX)
            }
            Mode.FEATURE -> {
                feature?.let { advanced.processText(text, it) }
                    ?: throw IllegalArgumentException("Feature type required for FEATURE mode")
            }
            Mode.CUSTOM -> {
                val prompt = customPrompt ?: PromptManager.getDefaultPrompt("assistant")
                advanced.processCustomPrompt(
                    text,
                    prompt,
                    buildCustomCacheKey(mode, prompt)
                )
            }
            Mode.REWRITE -> {
                val prompt = customPrompt ?: PromptManager.getDefaultPrompt("rewrite")
                advanced.processCustomPrompt(
                    text,
                    prompt,
                    buildCustomCacheKey(mode, prompt)
                )
            }
        }
        
        return UnifiedResult(
            success = result.success,
            text = result.text,
            error = result.error,
            fromCache = result.fromCache,
            time = result.processingTimeMs
        )
    }
    
    /**
     * Generate smart replies with unified interface
     */
    fun generateSmartReplies(
        message: String,
        context: String = "general",
        count: Int = 3,
        stream: Boolean = false
    ): Flow<UnifiedResult> = flow {
        val startTime = System.currentTimeMillis()
        
        try {
            if (!config.isAIFeaturesEnabled()) {
                emit(UnifiedResult(false, "", "AI features disabled"))
                return@flow
            }
            
            if (stream) {
                streaming.generateSmartRepliesStreaming(message, context, count)
                    .collect { chunk ->
                        emit(UnifiedResult(
                            success = true,
                            text = chunk.currentText,
                            fromCache = chunk.fromCache,
                            time = System.currentTimeMillis() - startTime,
                            isComplete = chunk.isComplete
                        ))
                    }
            } else {
                val result = advanced.generateSmartReplies(message, context, count)
                emit(UnifiedResult(
                    success = result.success,
                    text = result.text,
                    error = result.error,
                    fromCache = result.fromCache,
                    time = result.processingTimeMs
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating smart replies", e)
            emit(UnifiedResult(
                success = false,
                text = "",
                error = "Smart replies error: ${e.message}",
                time = System.currentTimeMillis() - startTime
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Test API connectivity
     */
    suspend fun testConnection(): UnifiedResult {
        return try {
            val result = advanced.testConnection()
            UnifiedResult(
                success = result.success,
                text = result.text,
                error = result.error,
                fromCache = result.fromCache,
                time = result.processingTimeMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection", e)
            UnifiedResult(
                success = false,
                text = "",
                error = "Connection test failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get available tone types
     */
    fun getAvailableTones(): List<AdvancedAIService.ToneType> = advanced.getAvailableTones()
    
    /**
     * Get available processing features
     */
    fun getAvailableFeatures(): List<AdvancedAIService.ProcessingFeature> = advanced.getAvailableFeatures()
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> = cache.getStats()
    
    /**
     * Clear cache
     */
    fun clearCache() {
        cache.clear()
        advanced.clearCache()
    }

    private fun buildCustomCacheKey(mode: Mode, prompt: String): String {
        val prefix = when (mode) {
            Mode.REWRITE -> "rewrite_prompt"
            Mode.CUSTOM -> "custom_prompt"
            else -> "custom_prompt"
        }
        return "${prefix}_${prompt.hashCode()}"
    }
    
    /**
     * Check if service is ready
     */
    fun isReady(): Boolean {
        return config.hasApiKey() && config.isAIFeaturesEnabled()
    }
    
    /**
     * Get service status information
     */
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "isReady" to isReady(),
            "hasApiKey" to config.hasApiKey(),
            "aiEnabled" to config.isAIFeaturesEnabled(),
            "cacheStats" to getCacheStats()
        )
    }
    
    /**
     * Get current prompt information for a specific mode (now stubbed)
     * @param mode The processing mode (TONE, GRAMMAR, CUSTOM, etc.)
     * @return Map containing prompt information
     */
    fun getPromptInfo(mode: Mode): Map<String, String> {
        val promptInfo = PromptManager.getPromptInfo(mode.name.lowercase())
        val hasPrompt = promptInfo["hasPrompt"] as? Boolean ?: false
        val title = promptInfo["title"] as? String ?: "Default"
        val prompt = promptInfo["prompt"] as? String ?: ""
        val count = promptInfo["count"] as? Int ?: 0
        
        return mapOf(
            "title" to title,
            "prompt" to prompt,
            "mode" to mode.name,
            "hasCustomPrompt" to hasPrompt.toString(),
            "displayText" to if (hasPrompt) "Using custom prompt: $title" else "Using default prompts",
            "promptCount" to count.toString()
        )
    }
    
    /**
     * Get prompt display text for UI
     * @param mode The processing mode
     * @return Formatted display string
     */
    fun getPromptDisplayText(mode: Mode): String {
        return PromptManager.getPromptDisplayText(mode.name.lowercase())
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        advanced.cleanup()
        streaming.cleanup()
        cache.cleanup()
    }
}
