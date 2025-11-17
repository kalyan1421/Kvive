package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Secure OpenAI API configuration and key management
 */
class OpenAIConfig private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "OpenAIConfig"
        private const val PREFS_NAME = "openai_secure_prefs"
        private const val API_KEY_PREF = "encrypted_api_key"
        private const val ENCRYPTION_KEY_PREF = "encryption_key"
        private const val API_ENABLED_PREF = "ai_features_enabled"
        
        // OpenAI API Configuration
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val CHAT_COMPLETIONS_ENDPOINT = "$OPENAI_BASE_URL/chat/completions"
        const val DEFAULT_MODEL = "gpt-3.5-turbo"
        const val MAX_TOKENS = 150
        const val TEMPERATURE = 0.7f
        
        // Backend Proxy Configuration
        // Firebase Function URL - will be constructed from project ID
        fun getBackendProxyUrl(context: Context): String {
            // Use hardcoded project ID (matches firebase_options.dart)
            val projectId = "aikeyboard-18ed9"
            return "https://us-central1-$projectId.cloudfunctions.net/openaiChat"
        }
        
        @Volatile
        private var INSTANCE: OpenAIConfig? = null
        
        fun getInstance(context: Context): OpenAIConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OpenAIConfig(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedApiKey: String? = null
    
    init {
        Log.d(TAG, "OpenAIConfig initializing...")
        // API key must be set by user through Flutter app settings
        // Do not hardcode API keys in production builds
    }
    
    /**
     * Securely store the API key with encryption
     */
    fun setApiKey(apiKey: String) {
        try {
            val encryptedKey = encryptApiKey(apiKey)
            prefs.edit()
                .putString(API_KEY_PREF, encryptedKey)
                .apply()
            cachedApiKey = apiKey
            Log.d(TAG, "API key stored securely")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing API key", e)
        }
    }
    
    /**
     * Retrieve and decrypt the API key
     */
    fun getApiKey(): String? {
        return getApiKeyWithFallback() 
    }
    
    /**
     * Check if API key is configured
     * Returns true if local API key exists OR if backend proxy is enabled
     */
    fun hasApiKey(): Boolean {
        val hasEncrypted = prefs.contains(API_KEY_PREF)
        val hasDirect = prefs.contains("direct_api_key")
        val hasBackendProxy = prefs.getBoolean("backend_proxy_enabled", true) // Default to true (using backend proxy)
        val result = hasEncrypted || hasDirect || hasBackendProxy
        Log.d(TAG, "hasApiKey: encrypted=$hasEncrypted, direct=$hasDirect, backendProxy=$hasBackendProxy, result=$result")
        return result
    }
    
    /**
     * Enable/disable AI features
     */
    fun setAIFeaturesEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(API_ENABLED_PREF, enabled)
            .apply()
    }
    
    /**
     * Check if AI features are enabled
     * Defaults to true when using backend proxy (no local API key required)
     */
    fun isAIFeaturesEnabled(): Boolean {
        // Check if backend proxy is enabled (defaults to true)
        val backendProxyEnabled = prefs.getBoolean("backend_proxy_enabled", true)
        // If using backend proxy, enable AI features by default
        val enabled = prefs.getBoolean(API_ENABLED_PREF, backendProxyEnabled)
        Log.d(TAG, "isAIFeaturesEnabled: $enabled (backendProxy=$backendProxyEnabled)")
        return enabled
    }
    
    /**
     * Clear stored API key
     */
    fun clearApiKey() {
        prefs.edit()
            .remove(API_KEY_PREF)
            .remove(ENCRYPTION_KEY_PREF)
            .remove("direct_api_key")
            .apply()
        cachedApiKey = null
        Log.d(TAG, "All API keys cleared")
    }
    
    /**
     * Get authorization header for API requests
     */
    fun getAuthorizationHeader(): String? {
        val apiKey = getApiKey()
        return if (apiKey != null && apiKey.isNotBlank()) {
            Log.d(TAG, "Authorization header created successfully")
            "Bearer $apiKey"
        } else {
            Log.e(TAG, "Cannot create authorization header - API key is null or blank")
            null
        }
    }
    
    /**
     * Force reinitialize API key (for troubleshooting)
     * NOTE: API key must be provided as parameter - never hardcode in production
     */
    fun forceReinitializeApiKey(apiKey: String) {
        Log.d(TAG, "Force reinitializing API key")
        clearApiKey()
        
        // Try encrypted storage first
        try {
            setApiKey(apiKey)
            Log.d(TAG, "API key set via encrypted storage")
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted storage failed, using direct storage", e)
            setApiKeyDirect(apiKey)
        }
        
        setAIFeaturesEnabled(true)
        
        // Test retrieval
        val testKey = getApiKey()
        val authHeader = getAuthorizationHeader()
        
        Log.d(TAG, "API Key length: ${apiKey.length}")
        Log.d(TAG, "Retrieved key length: ${testKey?.length ?: 0}")
        Log.d(TAG, "Auth header: ${authHeader?.take(20)}...")
        Log.d(TAG, "Force reinitialization result: ${if (testKey != null && authHeader != null) "SUCCESS" else "FAILED"}")
        
        // Additional validation
        if (testKey != apiKey) {
            Log.e(TAG, "API key mismatch! Stored key doesn't match input key")
            // Try direct storage bypass
            setApiKeyDirect(apiKey)
        }
    }
    
    /**
     * Test API key with a simple API call
     */
    suspend fun testApiKey(): Boolean {
        return try {
            val authHeader = getAuthorizationHeader()
            if (authHeader == null) {
                Log.e(TAG, "No authorization header available for API test")
                return false
            }
            
            Log.d(TAG, "Testing API key with OpenAI API...")
            
            // Make a simple API call to test the key
            val url = java.net.URL("https://api.openai.com/v1/models")
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "API test response code: $responseCode")
            
            when (responseCode) {
                200 -> {
                    Log.d(TAG, "API key test successful!")
                    true
                }
                401 -> {
                    Log.e(TAG, "API key test failed: Unauthorized (invalid API key)")
                    false
                }
                else -> {
                    Log.w(TAG, "API key test returned unexpected code: $responseCode")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key test failed with exception", e)
            false
        }
    }
    
    /**
     * Direct API key storage (bypasses encryption for troubleshooting)
     */
    fun setApiKeyDirect(apiKey: String) {
        Log.w(TAG, "Using direct API key storage (bypassing encryption)")
        
        // Store directly without encryption as fallback
        prefs.edit()
            .putString("direct_api_key", apiKey)
            .putBoolean(API_ENABLED_PREF, true)
            .apply()
            
        cachedApiKey = apiKey
        Log.d(TAG, "Direct API key storage completed")
    }
    
    /**
     * Get API key with fallback to direct storage
     */
    private fun getApiKeyWithFallback(): String? {
        // Try encrypted first
        var apiKey = getApiKeyEncrypted()
        
        if (apiKey == null) {
            // Fallback to direct storage
            apiKey = prefs.getString("direct_api_key", null)
            Log.d(TAG, "Using direct API key fallback: ${apiKey != null}")
        }
        
        return apiKey
    }
    
    /**
     * Original encrypted API key retrieval
     */
    private fun getApiKeyEncrypted(): String? {
        if (cachedApiKey != null) {
            Log.d(TAG, "Returning cached API key")
            return cachedApiKey
        }
        
        return try {
            val encryptedKey = prefs.getString(API_KEY_PREF, null)
            Log.d(TAG, "Encrypted key exists: ${encryptedKey != null}")
            
            if (encryptedKey != null) {
                cachedApiKey = decryptApiKey(encryptedKey)
                Log.d(TAG, "API key decrypted successfully: ${cachedApiKey?.take(10)}...")
                cachedApiKey
            } else {
                Log.w(TAG, "No encrypted API key found in preferences")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving encrypted API key", e)
            null
        }
    }
    
    /**
     * Encrypt API key for secure storage
     */
    private fun encryptApiKey(apiKey: String): String {
        val secretKey = getOrCreateEncryptionKey()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(apiKey.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }
    
    /**
     * Decrypt API key from secure storage
     */
    private fun decryptApiKey(encryptedKey: String): String {
        val secretKey = getOrCreateEncryptionKey()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val encryptedBytes = Base64.decode(encryptedKey, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }
    
    /**
     * Get or create encryption key for API key storage
     */
    private fun getOrCreateEncryptionKey(): SecretKey {
        val existingKey = prefs.getString(ENCRYPTION_KEY_PREF, null)
        
        return if (existingKey != null) {
            val keyBytes = Base64.decode(existingKey, Base64.DEFAULT)
            SecretKeySpec(keyBytes, "AES")
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()
            
            val encodedKey = Base64.encodeToString(secretKey.encoded, Base64.DEFAULT)
            prefs.edit()
                .putString(ENCRYPTION_KEY_PREF, encodedKey)
                .apply()
            
            secretKey
        }
    }
    
    /**
     * Validate API key format
     */
    fun isValidApiKey(apiKey: String): Boolean {
        return apiKey.startsWith("sk-") && apiKey.length > 20
    }
    
    /**
     * Get API request timeout settings
     */
    fun getRequestTimeoutMs(): Long = 30000L // 30 seconds
    
    /**
     * Get retry configuration
     */
    fun getMaxRetries(): Int = 3
    fun getRetryDelayMs(): Long = 1000L // 1 second
}
