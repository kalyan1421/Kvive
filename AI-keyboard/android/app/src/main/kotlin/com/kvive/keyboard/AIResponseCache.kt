package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Local caching system for AI responses to improve performance and reduce API calls
 */
class AIResponseCache(private val context: Context) {
    
    companion object {
        private const val TAG = "AIResponseCache"
        private const val CACHE_PREFS_NAME = "ai_response_cache"
        private const val CACHE_METADATA_PREFS = "ai_cache_metadata"
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val CLEANUP_THRESHOLD = 120 // Cleanup when cache exceeds this
    }
    
    private val cachePrefs: SharedPreferences = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
    private val metadataPrefs: SharedPreferences = context.getSharedPreferences(CACHE_METADATA_PREFS, Context.MODE_PRIVATE)
    
    // In-memory cache for faster access
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    
    // Cache statistics
    private var hitCount = 0
    private var missCount = 0
    private var totalRequests = 0
    
    data class CacheEntry(
        val response: String,
        val timestamp: Long,
        val accessCount: Int = 1,
        val lastAccessed: Long = System.currentTimeMillis()
    )
    
    init {
        loadMemoryCache()
        performMaintenanceIfNeeded()
    }
    
    /**
     * Get cached response
     */
    fun get(key: String): String? {
        totalRequests++
        
        try {
            // Check memory cache first
            val memoryEntry = memoryCache[key]
            if (memoryEntry != null && !isExpired(memoryEntry.timestamp)) {
                hitCount++
                // Update access info
                val updatedEntry = memoryEntry.copy(
                    accessCount = memoryEntry.accessCount + 1,
                    lastAccessed = System.currentTimeMillis()
                )
                memoryCache[key] = updatedEntry
                updateMetadata(key, updatedEntry)
                
                Log.d(TAG, "Cache HIT (memory): $key")
                return memoryEntry.response
            }
            
            // Check persistent cache
            val cachedResponse = cachePrefs.getString(key, null)
            if (cachedResponse != null) {
                val metadata = getMetadata(key)
                if (metadata != null && !isExpired(metadata.timestamp)) {
                    hitCount++
                    
                    // Load into memory cache
                    val entry = CacheEntry(
                        response = cachedResponse,
                        timestamp = metadata.timestamp,
                        accessCount = metadata.accessCount + 1,
                        lastAccessed = System.currentTimeMillis()
                    )
                    memoryCache[key] = entry
                    updateMetadata(key, entry)
                    
                    Log.d(TAG, "Cache HIT (disk): $key")
                    return cachedResponse
                }
            }
            
            missCount++
            Log.d(TAG, "Cache MISS: $key")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached response", e)
            missCount++
            return null
        }
    }
    
    /**
     * Put response in cache
     */
    fun put(key: String, response: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val entry = CacheEntry(
                response = response,
                timestamp = timestamp,
                accessCount = 1,
                lastAccessed = timestamp
            )
            
            // Store in memory cache
            memoryCache[key] = entry
            
            // Store in persistent cache
            cachePrefs.edit()
                .putString(key, response)
                .apply()
            
            // Store metadata
            updateMetadata(key, entry)
            
            Log.d(TAG, "Cache PUT: $key (size: ${response.length} chars)")
            
            // Cleanup if needed
            if (memoryCache.size > CLEANUP_THRESHOLD) {
                performCleanup()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error caching response", e)
        }
    }
    
    /**
     * Update cache entry metadata
     */
    private fun updateMetadata(key: String, entry: CacheEntry) {
        val metadata = JSONObject().apply {
            put("timestamp", entry.timestamp)
            put("accessCount", entry.accessCount)
            put("lastAccessed", entry.lastAccessed)
            put("responseLength", entry.response.length)
        }
        
        metadataPrefs.edit()
            .putString(key, metadata.toString())
            .apply()
    }
    
    /**
     * Get cache entry metadata
     */
    private fun getMetadata(key: String): CacheEntry? {
        return try {
            val metadataJson = metadataPrefs.getString(key, null) ?: return null
            val metadata = JSONObject(metadataJson)
            
            CacheEntry(
                response = "", // Response is stored separately
                timestamp = metadata.getLong("timestamp"),
                accessCount = metadata.getInt("accessCount"),
                lastAccessed = metadata.getLong("lastAccessed")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing metadata for key: $key", e)
            null
        }
    }
    
    /**
     * Check if cache entry is expired
     */
    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
    }
    
    /**
     * Load memory cache from persistent storage
     */
    private fun loadMemoryCache() {
        try {
            val allEntries = cachePrefs.all
            var loadedCount = 0
            
            for ((key, value) in allEntries) {
                if (value is String) {
                    val metadata = getMetadata(key)
                    if (metadata != null && !isExpired(metadata.timestamp)) {
                        memoryCache[key] = CacheEntry(
                            response = value,
                            timestamp = metadata.timestamp,
                            accessCount = metadata.accessCount,
                            lastAccessed = metadata.lastAccessed
                        )
                        loadedCount++
                    }
                }
            }
            
            Log.d(TAG, "Loaded $loadedCount cache entries into memory")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading memory cache", e)
        }
    }
    
    /**
     * Perform cache cleanup to maintain size limits
     */
    private fun performCleanup() {
        try {
            if (memoryCache.size <= MAX_CACHE_SIZE) return
            
            Log.d(TAG, "Performing cache cleanup. Current size: ${memoryCache.size}")
            
            // Sort by access frequency and recency (LFU + LRU hybrid)
            val sortedEntries = memoryCache.entries.sortedWith(compareBy<Map.Entry<String, CacheEntry>> { 
                it.value.accessCount 
            }.thenBy { 
                it.value.lastAccessed 
            })
            
            // Remove least frequently/recently used entries
            val entriesToRemove = sortedEntries.take(memoryCache.size - MAX_CACHE_SIZE)
            val keysToRemove = entriesToRemove.map { it.key }
            
            // Remove from all caches
            keysToRemove.forEach { key ->
                memoryCache.remove(key)
                cachePrefs.edit().remove(key).apply()
                metadataPrefs.edit().remove(key).apply()
            }
            
            Log.d(TAG, "Cache cleanup completed. Removed ${keysToRemove.size} entries. New size: ${memoryCache.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
        }
    }
    
    /**
     * Perform maintenance tasks
     */
    private fun performMaintenanceIfNeeded() {
        try {
            // Remove expired entries
            val expiredKeys = mutableListOf<String>()
            
            for ((key, entry) in memoryCache) {
                if (isExpired(entry.timestamp)) {
                    expiredKeys.add(key)
                }
            }
            
            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { key ->
                    memoryCache.remove(key)
                    cachePrefs.edit().remove(key).apply()
                    metadataPrefs.edit().remove(key).apply()
                }
                
                Log.d(TAG, "Removed ${expiredKeys.size} expired cache entries")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache maintenance", e)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): Map<String, Any> {
        val hitRate = if (totalRequests > 0) (hitCount.toDouble() / totalRequests * 100) else 0.0
        val totalSizeBytes = memoryCache.values.sumOf { it.response.length * 2 } // Rough UTF-16 estimate
        
        return mapOf(
            "hitCount" to hitCount,
            "missCount" to missCount,
            "totalRequests" to totalRequests,
            "hitRate" to "%.2f%%".format(hitRate),
            "cacheSize" to memoryCache.size,
            "maxCacheSize" to MAX_CACHE_SIZE,
            "totalSizeBytes" to totalSizeBytes,
            "totalSizeKB" to "%.2f KB".format(totalSizeBytes / 1024.0),
            "cacheExpiryHours" to CACHE_EXPIRY_MS / (60 * 60 * 1000),
            "oldestEntryAge" to getOldestEntryAge(),
            "mostAccessedKey" to getMostAccessedKey(),
            "averageResponseLength" to getAverageResponseLength()
        )
    }
    
    /**
     * Get age of oldest cache entry in hours
     */
    private fun getOldestEntryAge(): String {
        val oldestTimestamp = memoryCache.values.minOfOrNull { it.timestamp }
        return if (oldestTimestamp != null) {
            val ageMs = System.currentTimeMillis() - oldestTimestamp
            val ageHours = ageMs / (60 * 60 * 1000.0)
            "%.1f hours".format(ageHours)
        } else {
            "N/A"
        }
    }
    
    /**
     * Get most accessed cache key
     */
    private fun getMostAccessedKey(): String {
        val mostAccessed = memoryCache.maxByOrNull { it.value.accessCount }
        return if (mostAccessed != null) {
            "${mostAccessed.key.take(20)}... (${mostAccessed.value.accessCount} hits)"
        } else {
            "N/A"
        }
    }
    
    /**
     * Get average response length
     */
    private fun getAverageResponseLength(): String {
        val avgLength = if (memoryCache.isNotEmpty()) {
            memoryCache.values.map { it.response.length }.average()
        } else {
            0.0
        }
        return "%.0f chars".format(avgLength)
    }
    
    /**
     * Clear all cache data
     */
    fun clear() {
        try {
            memoryCache.clear()
            cachePrefs.edit().clear().apply()
            metadataPrefs.edit().clear().apply()
            
            // Reset statistics
            hitCount = 0
            missCount = 0
            totalRequests = 0
            
            Log.d(TAG, "Cache cleared completely")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    /**
     * Check if cache contains key
     */
    fun contains(key: String): Boolean {
        return memoryCache.containsKey(key) || cachePrefs.contains(key)
    }
    
    /**
     * Get cache size
     */
    fun size(): Int = memoryCache.size
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        performCleanup()
    }
}
