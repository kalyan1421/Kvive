package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streaming AI Service for real-time text processing with progressive updates
 */
class StreamingAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "StreamingAIService"
        private const val REQUEST_TIMEOUT = 45000 // Longer for streaming
        private const val CONNECT_TIMEOUT = 10000
        private const val CHUNK_BUFFER_SIZE = 1024
    }
    
    private val config = OpenAIConfig.getInstance(context)
    private val responseCache = AIResponseCache(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Streaming result with progressive updates
     */
    data class StreamingResult(
        val isComplete: Boolean,
        val currentText: String,
        val fullText: String,
        val fromCache: Boolean = false,
        val error: String? = null,
        val processingTimeMs: Long = 0
    )
    
    /**
     * Process text with streaming updates
     */
    fun processTextStreaming(
        text: String,
        systemPrompt: String,
        cacheKey: String
    ): Flow<StreamingResult> = flow {
        val startTime = System.currentTimeMillis()
        
        try {
            // Check cache first
            val cachedResponse = responseCache.get(generateCacheKey(text, systemPrompt, cacheKey))
            if (cachedResponse != null) {
                // Simulate streaming for cached responses
                val words = cachedResponse.split(" ")
                var currentText = ""
                
                for (i in words.indices) {
                    currentText += words[i] + " "
                    delay(50) // Simulate typing effect
                    
                    emit(StreamingResult(
                        isComplete = i == words.size - 1,
                        currentText = currentText.trim(),
                        fullText = cachedResponse,
                        fromCache = true,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    ))
                }
                return@flow
            }
            
            // Make streaming API request
            val streamingResponse = makeStreamingRequest(systemPrompt, text)
            var fullText = ""
            
            streamingResponse.collect { chunk ->
                fullText += chunk
                emit(StreamingResult(
                    isComplete = false,
                    currentText = fullText,
                    fullText = fullText,
                    fromCache = false,
                    processingTimeMs = System.currentTimeMillis() - startTime
                ))
            }
            
            // Final result
            if (fullText.isNotEmpty()) {
                responseCache.put(generateCacheKey(text, systemPrompt, cacheKey), fullText)
                emit(StreamingResult(
                    isComplete = true,
                    currentText = fullText,
                    fullText = fullText,
                    fromCache = false,
                    processingTimeMs = System.currentTimeMillis() - startTime
                ))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming processing", e)
            emit(StreamingResult(
                isComplete = true,
                currentText = "",
                fullText = "",
                error = e.message ?: "Streaming error occurred",
                processingTimeMs = System.currentTimeMillis() - startTime
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Make streaming OpenAI API request
     */
    private suspend fun makeStreamingRequest(
        systemPrompt: String,
        userText: String
    ): Flow<String> = flow {
        val authHeader = config.getAuthorizationHeader()
            ?: throw Exception("API key not configured")
        
        val url = URL(OpenAIConfig.CHAT_COMPLETIONS_ENDPOINT)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Accept", "text/event-stream")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = REQUEST_TIMEOUT
                doOutput = true
            }
            
            val requestBody = createStreamingRequest(systemPrompt, userText)
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { 
                            if (it.startsWith("data: ")) {
                                val data = it.substring(6)
                                if (data.trim() != "[DONE]") {
                                    try {
                                        val chunk = parseStreamingChunk(data)
                                        if (chunk.isNotEmpty()) {
                                            emit(chunk)
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error parsing chunk: $data", e)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val errorResponse = BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                    reader.readText()
                }
                throw Exception("API error ($responseCode): $errorResponse")
            }
            
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Create streaming chat completion request
     */
    private fun createStreamingRequest(systemPrompt: String, userText: String): String {
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
            put("stream", true) // Enable streaming
            put("top_p", 0.9)
            put("frequency_penalty", 0.1)
            put("presence_penalty", 0.1)
        }.toString()
    }
    
    /**
     * Parse streaming chunk from OpenAI response
     */
    private fun parseStreamingChunk(data: String): String {
        return try {
            val json = JSONObject(data)
            val choices = json.getJSONArray("choices")
            
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val delta = choice.getJSONObject("delta")
                
                if (delta.has("content")) {
                    delta.getString("content")
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing streaming chunk", e)
            ""
        }
    }
    
    /**
     * Process tone adjustment with streaming
     */
    fun adjustToneStreaming(
        text: String,
        tone: AdvancedAIService.ToneType
    ): Flow<StreamingResult> {
        return processTextStreaming(text, tone.systemPrompt, "tone_${tone.name}")
    }
    
    /**
     * Process feature with streaming
     */
    fun processFeatureStreaming(
        text: String,
        feature: AdvancedAIService.ProcessingFeature
    ): Flow<StreamingResult> {
        return processTextStreaming(text, feature.systemPrompt, "feature_${feature.name}")
    }
    
    /**
     * Generate smart replies with streaming
     */
    fun generateSmartRepliesStreaming(
        message: String,
        context: String = "general",
        count: Int = 3
    ): Flow<StreamingResult> {
        val systemPrompt = """
            Generate exactly $count brief, appropriate responses to the following message.
            Context: $context
            
            Make each response different in tone and style:
            1. Friendly and warm
            2. Professional and neutral
            3. Enthusiastic and positive
            
            Format each response on a new line starting with "•"
        """.trimIndent()
        
        return processTextStreaming(message, systemPrompt, "replies_${context}_$count")
    }
    
    /**
     * Generate cache key for request
     */
    private fun generateCacheKey(text: String, systemPrompt: String, prefix: String): String {
        val combined = "$prefix|$systemPrompt|$text"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        responseCache.cleanup()
    }
}
