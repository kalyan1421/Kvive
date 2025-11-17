package com.kvive.keyboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Advanced AI Service with caching, enhanced tone adjustment, and sophisticated text processing
 */
class AdvancedAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedAIService"
        private const val REQUEST_TIMEOUT = 30000
        private const val CONNECT_TIMEOUT = 10000
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_EXPIRY_HOURS = 24
        
        // Rate limiting constants
        private const val MAX_REQUESTS_PER_MINUTE = 3  // Conservative limit for free tier
        private const val RATE_LIMIT_WINDOW_MS = 60_000L  // 1 minute
        private const val MIN_REQUEST_INTERVAL_MS = 2_000L  // 2 seconds between requests
    }
    
    private val config = OpenAIConfig.getInstance(context)
    private val responseCache = AIResponseCache(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Rate limiting tracking
    private val requestTimestamps = mutableListOf<Long>()
    private var lastRequestTime = 0L
    private var isRateLimited = false
    private var rateLimitResetTime = 0L
    
    /**
     * Check if we can make a request (rate limiting) - DISABLED
     */
    private fun canMakeRequest(): Boolean {
        // Rate limiting disabled - always allow requests
        return true
    }
    
    /**
     * Record a successful request - DISABLED
     */
    private fun recordRequest() {
        // Rate limiting disabled - no need to record requests
    }
    
    /**
     * Get time until next request is allowed
     */
    private fun getTimeUntilNextRequest(): Long {
        val currentTime = System.currentTimeMillis()
        
        if (isRateLimited) {
            return maxOf(0L, rateLimitResetTime - currentTime)
        }
        
        val timeSinceLastRequest = currentTime - lastRequestTime
        return maxOf(0L, MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest)
    }
    
    /**
     * Enhanced Tone Adjustment Options
     */
    enum class ToneType(
        val displayName: String,
        val icon: String,
        val systemPrompt: String,
        val color: String
    ) {
        FORMAL(
            "Professional + blur",
            "🎩",
            "Rewrite this text in a highly professional, formal tone suitable for business communications, official documents, or academic writing. Use sophisticated vocabulary and proper grammar.",
            "#1a73e8"
        ),
        CASUAL(
            "Casual",
            "😊", 
            "Rewrite this text in a relaxed, friendly, conversational tone. Use simple words, contractions, and a warm, approachable style as if talking to a friend.",
            "#34a853"
        ),
        FUNNY(
            "Humorous",
            "😂",
            "Rewrite this text to be funny and entertaining while keeping the core message. Add humor, witty remarks, or playful language to make it more engaging and amusing.",
            "#fbbc04"
        ),
        ANGRY(
            "Assertive",
            "😤",
            "Rewrite this text with a strong, assertive tone that expresses frustration or displeasure while remaining professional. Make it firm and direct without being offensive.",
            "#ea4335"
        ),
        ENTHUSIASTIC(
            "Excited",
            "🎉",
            "Rewrite this text with high energy and enthusiasm. Use exclamation points, positive language, and expressions that convey excitement and passion about the topic.",
            "#9c27b0"
        ),
        POLITE(
            "Polite",
            "🙏",
            "Rewrite this text to be extremely polite and courteous. Use please, thank you, and other courteous expressions. Make it respectful and considerate.",
            "#00bcd4"
        ),
        CONFIDENT(
            "Confident",
            "💪",
            "Rewrite this text with a confident, authoritative tone. Use strong, decisive language that conveys expertise and self-assurance without being arrogant.",
            "#ff5722"
        ),
        EMPATHETIC(
            "Caring",
            "❤️",
            "Rewrite this text with empathy and understanding. Use compassionate language that shows care, concern, and emotional intelligence.",
            "#e91e63"
        )
    }
    
    /**
     * Text Processing Features
     */
    enum class ProcessingFeature(
        val displayName: String,
        val icon: String,
        val systemPrompt: String
    ) {
        GRAMMAR_FIX(
            "Fix Grammar",
            "✅",
            "Fix all grammar, spelling, punctuation, and syntax errors in this text. Return only the corrected version without explanations."
        ),
        SIMPLIFY(
            "Simplify",
            "🔤",
            "Simplify this text using basic vocabulary and shorter sentences. Make it easy to understand for a general audience."
        ),
        EXPAND(
            "Add Details",
            "📝",
            "Expand this text with more details, examples, and explanations while maintaining the original tone and message."
        ),
        SHORTEN(
            "Make Concise",
            "✂️",
            "Make this text more concise and to the point while preserving all important information and the original meaning."
        ),
        TRANSLATE_TO_ENGLISH(
            "To English",
            "🇺🇸",
            "Translate this text to clear, natural English. If it's already in English, improve the clarity and naturalness."
        ),
        MAKE_BULLET_POINTS(
            "Bullet Points",
            "• ",
            "Convert this text into clear, well-organized bullet points that capture all the main ideas."
        )
    }
    
    /**
     * AI Request Result with caching info
     */
    data class AIResult(
        val success: Boolean,
        val text: String,
        val fromCache: Boolean = false,
        val error: String? = null,
        val processingTimeMs: Long = 0
    )
    
    /**
     * Process text with tone adjustment
     */
    suspend fun adjustTone(text: String, tone: ToneType): AIResult {
        return processWithAI(text, tone.systemPrompt, "tone_${tone.name}")
    }
    
    /**
     * Process text with feature
     */
    suspend fun processText(text: String, feature: ProcessingFeature): AIResult {
        return processWithAI(text, feature.systemPrompt, "feature_${feature.name}")
    }
    
    /**
     * Translate text to a target language
     */
    suspend fun translateText(text: String, targetLanguage: String): AIResult {
        val normalizedTarget = targetLanguage.trim()
        if (normalizedTarget.equals("english", ignoreCase = true)) {
            return processText(text, ProcessingFeature.TRANSLATE_TO_ENGLISH)
        }
        
        val systemPrompt = """
            Translate the following text into $normalizedTarget.
            Detect the source language automatically.
            Return only the translated text without additional commentary, notes, or quotation marks.
        """.trimIndent()
        val cacheSuffix = "translate_${normalizedTarget.lowercase().replace("\\s+".toRegex(), "_")}"
        return processWithAI(text, systemPrompt, cacheSuffix)
    }
    
    /**
     * Generate smart replies with context
     */
    suspend fun generateSmartReplies(
        message: String, 
        context: String = "general",
        count: Int = 3
    ): AIResult {
        val systemPrompt = """
            Generate exactly $count brief, appropriate responses to the following message.
            Context: $context
            
            Make each response different in tone and style:
            1. Friendly and warm
            2. Professional and neutral
            3. Enthusiastic and positive
            
            Format each response on a new line starting with "•"
        """.trimIndent()
        
        return processWithAI(message, systemPrompt, "replies_${context}_$count")
    }

    suspend fun processCustomPrompt(
        text: String,
        systemPrompt: String,
        cacheKeySuffix: String = "custom_prompt"
    ): AIResult {
        val sanitizedPrompt = if (systemPrompt.isBlank()) {
            PromptManager.getDefaultPrompt("assistant")
        } else {
            systemPrompt
        }
        return processWithAI(text, sanitizedPrompt, cacheKeySuffix)
    }
    
    
    /**
     * Core AI processing with caching
     */
    private suspend fun processWithAI(
        text: String,
        systemPrompt: String,
        cacheKey: String
    ): AIResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Check network availability
                if (!isNetworkAvailable()) {
                    return@withContext AIResult(
                        success = false,
                        text = "",
                        error = "No internet connection available"
                    )
                }
                
                // Check if AI features are enabled
                if (!config.isAIFeaturesEnabled()) {
                    return@withContext AIResult(
                        success = false,
                        text = "",
                        error = "AI features are disabled"
                    )
                }
                
                // Generate cache key
                val fullCacheKey = generateCacheKey(text, systemPrompt, cacheKey)
                
                // Check cache first
                val cachedResponse = responseCache.get(fullCacheKey)
                if (cachedResponse != null) {
                    val processingTime = System.currentTimeMillis() - startTime
                    return@withContext AIResult(
                        success = true,
                        text = cachedResponse,
                        fromCache = true,
                        processingTimeMs = processingTime
                    )
                }
                
                // Make API request
                val response = makeOpenAIRequest(systemPrompt, text)
                val processingTime = System.currentTimeMillis() - startTime
                
                // Cache the response
                responseCache.put(fullCacheKey, response)
                
                AIResult(
                    success = true,
                    text = response,
                    fromCache = false,
                    processingTimeMs = processingTime
                )
                
            } catch (e: Exception) {
                val processingTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "Error processing text with AI", e)
                
                AIResult(
                    success = false,
                    text = "",
                    error = e.message ?: "Unknown error occurred",
                    processingTimeMs = processingTime
                )
            }
        }
    }
    
    /**
     * Make OpenAI API request with enhanced error handling
     */
    private suspend fun makeOpenAIRequest(systemPrompt: String, userText: String): String {
        return withContext(Dispatchers.IO) {
            // Check rate limiting first
            if (!canMakeRequest()) {
                val waitTime = getTimeUntilNextRequest()
                if (waitTime > 0) {
                    throw Exception("Rate limit: Please wait ${waitTime / 1000}s before next request")
                }
            }
            
            // Use Firebase Function proxy instead of OpenAI directly
            val backendProxyUrl = OpenAIConfig.getBackendProxyUrl(context)
            val url = URL(backendProxyUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    // No Authorization header needed - API key is on the server
                    setRequestProperty("User-Agent", "AI-Keyboard/1.0")
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout = REQUEST_TIMEOUT
                    doOutput = true
                }
                
                val requestBody = createChatCompletionRequest(systemPrompt, userText)
                
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                            reader.readText()
                        }
                        // Record successful request for rate limiting
                        recordRequest()
                        return@withContext parseOpenAIResponse(response)
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        throw Exception("Server configuration error. Please contact support.")
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        throw Exception("Access forbidden. Please check server configuration.")
                    }
                    429 -> {
                        // Rate limit hit, set our internal rate limit
                        isRateLimited = true
                        rateLimitResetTime = System.currentTimeMillis() + RATE_LIMIT_WINDOW_MS
                        Log.w(TAG, "Rate limit hit, internal rate limiting activated")
                        throw Exception("Rate limit exceeded. Please wait 60s before next request.")
                    }
                    500, 502, 503, 504 -> {
                        // Server errors from Firebase Function
                        val errorResponse = try {
                            BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            "Server error"
                        }
                        throw Exception("Server error: $errorResponse")
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST -> {
                        val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                            reader.readText()
                        }
                        throw Exception("Bad request: $errorResponse")
                    }
                    else -> {
                        val errorResponse = try {
                            BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                                reader.readText()
                            }
                        } catch (e: Exception) {
                            "Unknown error"
                        }
                        throw Exception("API error ($responseCode): $errorResponse")
                    }
                }
                
            } finally {
                connection.disconnect()
            }
        }
    }
    
    /**
     * Create optimized chat completion request
     */
    private fun createChatCompletionRequest(systemPrompt: String, userText: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userText)
            })
        }
        
        return JSONObject().apply {
            put("model", OpenAIConfig.DEFAULT_MODEL)
            put("messages", messages)
            put("max_tokens", OpenAIConfig.MAX_TOKENS)
            put("temperature", OpenAIConfig.TEMPERATURE)
            put("top_p", 0.9)
            put("frequency_penalty", 0.1)
            put("presence_penalty", 0.1)
            put("stream", false)
        }.toString()
    }
    
    /**
     * Parse OpenAI response with better error handling
     */
    private fun parseOpenAIResponse(response: String): String {
        try {
            val jsonResponse = JSONObject(response)
            
            // Check for API errors
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                val errorMessage = error.optString("message", "Unknown API error")
                throw Exception("OpenAI API error: $errorMessage")
            }
            
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val content = message.getString("content").trim()
                
                if (content.isEmpty()) {
                    throw Exception("Empty response from OpenAI")
                }
                
                return content
            }
            
            throw Exception("No response content from OpenAI")
            
        } catch (e: Exception) {
            if (e.message?.contains("OpenAI API error") == true) {
                throw e
            }
            throw Exception("Failed to parse OpenAI response: ${e.message}")
        }
    }
    
    /**
     * Generate cache key for request
     */
    private fun generateCacheKey(text: String, systemPrompt: String, prefix: String): String {
        val combined = "$prefix|$systemPrompt|$text"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Check network availability with detailed info
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Get network type for analytics
     */
    fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "none"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
            
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            "error"
        }
    }
    
    /**
     * Test API connectivity with detailed diagnostics
     */
    suspend fun testConnection(): AIResult {
        return processWithAI(
            "Hello",
            "Respond with exactly: 'API connection successful'",
            "test_connection"
        )
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return responseCache.getStats()
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        responseCache.clear()
    }
    
    /**
     * Get available tone types
     */
    fun getAvailableTones(): List<ToneType> = ToneType.values().toList()
    
    /**
     * Get available processing features
     */
    fun getAvailableFeatures(): List<ProcessingFeature> = ProcessingFeature.values().toList()
    
    /**
     * Check if the service is fully initialized and ready to use
     */
    fun isInitialized(): Boolean {
        return config.getApiKey() != null && config.getApiKey()?.isNotEmpty() == true
    }
    
    /**
     * Preload/warm up the AI service to prevent cold-start delays
     * This is a lightweight operation that validates configuration without making API calls
     */
    fun preloadWarmup() {
        scope.launch {
            try {
                // Validate configuration
                if (!isInitialized()) {
                    Log.d(TAG, "⚠️ AI service not configured, skipping warm-up")
                    return@launch
                }
                
                // Check network availability
                if (!isNetworkAvailable()) {
                    Log.d(TAG, "⚠️ No network available for AI warm-up")
                    return@launch
                }
                
                Log.d(TAG, "🧠 AI service preloaded and ready (API key configured, network available)")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ AI warm-up failed: ${e.message}")
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        responseCache.cleanup()
    }
}
