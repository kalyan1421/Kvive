package com.kvive.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.json.JSONArray


/**
 * Comprehensive media cache manager for optimizing performance
 */
class MediaCacheManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaCacheManager"
        private const val MAX_EMOJI_CACHE = 1000 // Number of recent emojis
        private const val MAX_GIF_CACHE = 50 * 1024 * 1024L // 50MB for GIFs
        private const val MAX_STICKER_CACHE = 100 * 1024 * 1024L // 100MB for stickers
        private const val MAX_MEMORY_CACHE = 20 * 1024 * 1024 // 20MB memory cache for thumbnails
    }
    
    private val emojiCacheDir = File(context.cacheDir, "emojis")
    private val gifCacheDir = File(context.cacheDir, "gifs")
    private val stickerCacheDir = File(context.cacheDir, "stickers")
    
    // Memory cache for frequently used thumbnails and small images
    private val memoryCache = LruCache<String, Bitmap>(MAX_MEMORY_CACHE / (4 * 1024)) // Assuming 4KB per bitmap on average
    
    // Executor for background cache operations
    private val cacheExecutor = Executors.newFixedThreadPool(2)
    
    init {
        createCacheDirectories()
        // Schedule periodic cleanup
        scheduleCleanup()
    }
    
    private fun createCacheDirectories() {
        listOf(emojiCacheDir, gifCacheDir, stickerCacheDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
                Log.d(TAG, "Created cache directory: ${dir.name}")
            }
        }
    }
    
    private fun scheduleCleanup() {
        // Schedule cleanup to run every hour
        cacheExecutor.submit {
            while (true) {
                try {
                    Thread.sleep(3600000) // 1 hour
                    performCleanup()
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Cleanup thread interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scheduled cleanup", e)
                }
            }
        }
    }
    
    /**
     * Perform comprehensive cache cleanup
     */
    fun performCleanup() {
        cacheExecutor.submit {
            try {
                Log.d(TAG, "Starting cache cleanup")
                
                cleanupGifCache()
                cleanupStickerCache()
                cleanupEmojiUsageData()
                cleanupMemoryCache()
                
                Log.d(TAG, "Cache cleanup completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cache cleanup", e)
            }
        }
    }
    
    private fun cleanupGifCache() {
        val files = gifCacheDir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        
        if (totalSize > MAX_GIF_CACHE) {
            Log.d(TAG, "GIF cache size exceeded: ${totalSize / 1024 / 1024}MB")
            
            // Remove least recently used files (40% of total)
            val filesToDelete = files.sortedBy { it.lastModified() }
                .take((files.size * 0.4).toInt())
            
            var deletedSize = 0L
            filesToDelete.forEach { file ->
                deletedSize += file.length()
                file.delete()
            }
            
            Log.d(TAG, "Deleted ${filesToDelete.size} GIF files (${deletedSize / 1024 / 1024}MB)")
        }
    }
    
    private fun cleanupStickerCache() {
        val files = stickerCacheDir.listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        
        if (totalSize > MAX_STICKER_CACHE) {
            Log.d(TAG, "Sticker cache size exceeded: ${totalSize / 1024 / 1024}MB")
            
            // Remove oldest sticker files (30% of total)
            val filesToDelete = files.sortedBy { it.lastModified() }
                .take((files.size * 0.3).toInt())
            
            var deletedSize = 0L
            filesToDelete.forEach { file ->
                deletedSize += file.length()
                file.delete()
            }
            
            Log.d(TAG, "Deleted ${filesToDelete.size} sticker files (${deletedSize / 1024 / 1024}MB)")
        }
    }
    
    private fun cleanupEmojiUsageData() {
        try {
            val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
            val history = loadEmojiHistory(prefs)
            
            while (history.size > MAX_EMOJI_CACHE) {
                history.removeAt(history.lastIndex)
            }
            
            saveEmojiHistory(prefs, history)
            Log.d(TAG, "Trimmed emoji history to ${history.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning emoji usage data", e)
        }
    }
    
    private fun cleanupMemoryCache() {
        // Memory cache automatically manages itself via LRU, but we can trim if needed
        val maxSize = memoryCache.maxSize()
        val currentSize = memoryCache.size()
        
        if (currentSize > maxSize * 0.8) {
            memoryCache.trimToSize(maxSize / 2)
            Log.d(TAG, "Trimmed memory cache from $currentSize to ${memoryCache.size()}")
        }
    }
    
    /**
     * Get comprehensive cache statistics
     */
    fun getCacheStats(): CacheStats {
        val gifFiles = gifCacheDir.listFiles() ?: emptyArray()
        val stickerFiles = stickerCacheDir.listFiles() ?: emptyArray()
        val emojiFiles = emojiCacheDir.listFiles() ?: emptyArray()
        
        return CacheStats(
            gifCacheSize = gifFiles.sumOf { it.length() },
            gifFileCount = gifFiles.size,
            stickerCacheSize = stickerFiles.sumOf { it.length() },
            stickerFileCount = stickerFiles.size,
            emojiCacheSize = emojiFiles.sumOf { it.length() },
            emojiFileCount = emojiFiles.size,
            memoryCacheSize = memoryCache.size(),
            memoryCacheHitCount = memoryCache.hitCount().toLong(),
            memoryCacheMissCount = memoryCache.missCount().toLong(),
            totalCacheSize = getTotalCacheSize()
        )
    }
    
    private fun getTotalCacheSize(): Long {
        return listOf(gifCacheDir, stickerCacheDir, emojiCacheDir)
            .sumOf { dir -> 
                dir.listFiles()?.sumOf { it.length() } ?: 0L 
            }
    }
    
    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        cacheExecutor.submit {
            try {
                Log.d(TAG, "Clearing all caches")
                
                // Clear file caches
                listOf(gifCacheDir, stickerCacheDir, emojiCacheDir).forEach { dir ->
                    dir.listFiles()?.forEach { it.delete() }
                }
                
                // Clear memory cache
                memoryCache.evictAll()
                
                // Clear emoji usage history stored in SharedPreferences
                val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
                saveEmojiHistory(prefs, mutableListOf())
                
                Log.d(TAG, "All caches cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing caches", e)
            }
        }
    }
    
    /**
     * Preload frequently used content for better performance
     */
    fun preloadFrequentContent() {
        cacheExecutor.submit {
            try {
                Log.d(TAG, "Preloading frequent content")
                
                // Preload frequently used emojis from shared preferences or fall back to popular list
                val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
                val frequentEmojis = loadEmojiHistory(prefs).take(20).ifEmpty {
                    EmojiRepository.ensureLoaded(context)
                    EmojiRepository.getPopular(20, context).map { it.char }
                }
                
                // Preload recent stickers
                val stickerManager = StickerManager(context)
                val recentStickers = stickerManager.getRecentStickers()
                
                Log.d(TAG, "Preloaded ${frequentEmojis.size} emojis and ${recentStickers.size} stickers")
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading content", e)
            }
        }
    }
    
    /**
     * Get memory cache for thumbnails
     */
    fun getMemoryCache(): LruCache<String, Bitmap> {
        return memoryCache
    }
    
    /**
     * Optimize cache based on available memory
     */
    fun optimizeForMemory() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsagePercent = (usedMemory * 100) / maxMemory
        
        Log.d(TAG, "Memory usage: $memoryUsagePercent%")
        
        when {
            memoryUsagePercent > 85 -> {
                // High memory usage - aggressive cleanup
                memoryCache.trimToSize(memoryCache.maxSize() / 4)
                performCleanup()
            }
            memoryUsagePercent > 70 -> {
                // Medium memory usage - moderate cleanup
                memoryCache.trimToSize(memoryCache.maxSize() / 2)
            }
            memoryUsagePercent < 50 -> {
                // Low memory usage - preload content
                preloadFrequentContent()
            }
        }
    }

    private fun loadEmojiHistory(prefs: android.content.SharedPreferences): MutableList<String> {
        val historyJson = prefs.getString("emoji_history", "[]") ?: "[]"
        val historyArray = try {
            JSONArray(historyJson)
        } catch (_: Exception) {
            JSONArray()
        }
        val history = mutableListOf<String>()
        for (i in 0 until historyArray.length()) {
            val emoji = historyArray.optString(i)
            if (!emoji.isNullOrEmpty()) {
                history.add(emoji)
            }
        }
        return history
    }

    private fun saveEmojiHistory(prefs: android.content.SharedPreferences, history: List<String>) {
        val array = JSONArray()
        history.forEach { array.put(it) }
        prefs.edit().putString("emoji_history", array.toString()).apply()
    }
}

/**
 * Data class for cache statistics
 */
data class CacheStats(
    val gifCacheSize: Long,
    val gifFileCount: Int,
    val stickerCacheSize: Long,
    val stickerFileCount: Int,
    val emojiCacheSize: Long,
    val emojiFileCount: Int,
    val memoryCacheSize: Int,
    val memoryCacheHitCount: Long,
    val memoryCacheMissCount: Long,
    val totalCacheSize: Long
) {
    fun getFormattedSummary(): String {
        val totalMB = totalCacheSize / 1024 / 1024
        val hitRate = if (memoryCacheHitCount + memoryCacheMissCount > 0) {
            (memoryCacheHitCount * 100) / (memoryCacheHitCount + memoryCacheMissCount)
        } else {
            0
        }
        
        return """
            Cache Summary:
            Total Size: ${totalMB}MB
            GIFs: ${gifFileCount} files (${gifCacheSize / 1024 / 1024}MB)
            Stickers: ${stickerFileCount} files (${stickerCacheSize / 1024 / 1024}MB)
            Emojis: ${emojiFileCount} files (${emojiCacheSize / 1024}KB)
            Memory Cache Hit Rate: ${hitRate}%
        """.trimIndent()
    }
}

/**
 * Lazy media loader for efficient thumbnail loading
 */
class LazyMediaLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "LazyMediaLoader"
    }
    
    private val cacheManager = MediaCacheManager(context)
    private val loadingTasks = mutableMapOf<String, Future<Bitmap>>()
    private val executor = Executors.newFixedThreadPool(3)
    
    fun loadThumbnail(
        url: String,
        targetWidth: Int,
        targetHeight: Int,
        callback: (Bitmap?) -> Unit
    ) {
        // Check memory cache first
        val memoryCache = cacheManager.getMemoryCache()
        val cacheKey = "${url}_${targetWidth}x${targetHeight}"
        val cached = memoryCache.get(cacheKey)
        
        if (cached != null) {
            callback(cached)
            return
        }
        
        // Check if already loading
        loadingTasks[cacheKey]?.let { task ->
            executor.submit {
                try {
                    val bitmap = task.get()
                    callback(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting bitmap from task", e)
                    callback(null)
                }
            }
            return
        }
        
        // Start new loading task
        val future = executor.submit<Bitmap> {
            try {
                val bitmap = loadBitmapFromUrl(url, targetWidth, targetHeight)
                bitmap?.let { 
                    memoryCache.put(cacheKey, it)
                }
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error loading thumbnail", e)
                null
            } finally {
                loadingTasks.remove(cacheKey)
            }
        }
        
        loadingTasks[cacheKey] = future
        
        executor.submit {
            try {
                val bitmap = future.get()
                callback(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error in thumbnail callback", e)
                callback(null)
            }
        }
    }
    
    private fun loadBitmapFromUrl(url: String, width: Int, height: Int): Bitmap? {
        return try {
            when {
                url.startsWith("file:///android_asset/") -> {
                    // Load from assets
                    val assetPath = url.removePrefix("file:///android_asset/")
                    val inputStream = context.assets.open(assetPath)
                    decodeBitmapWithSampling(inputStream, width, height)
                }
                url.startsWith("file://") -> {
                    // Load from local file
                    val filePath = url.removePrefix("file://")
                    decodeBitmapFromFile(filePath, width, height)
                }
                url.startsWith("http") -> {
                    // Load from network (should be avoided in system keyboards)
                    Log.w(TAG, "Network loading not recommended for system keyboards")
                    null
                }
                else -> {
                    Log.w(TAG, "Unsupported URL format: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URL: $url", e)
            null
        }
    }
    
    private fun decodeBitmapWithSampling(inputStream: java.io.InputStream, width: Int, height: Int): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            val bufferedStream = inputStream.buffered()
            bufferedStream.mark(inputStream.available())
            BitmapFactory.decodeStream(bufferedStream, null, options)
            bufferedStream.reset()
            
            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, width, height)
            options.inJustDecodeBounds = false
            
            // Decode with sampling
            BitmapFactory.decodeStream(bufferedStream, null, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap with sampling", e)
            null
        }
    }
    
    private fun decodeBitmapFromFile(filePath: String, width: Int, height: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            BitmapFactory.decodeFile(filePath, options)
            
            options.inSampleSize = calculateInSampleSize(options, width, height)
            options.inJustDecodeBounds = false
            
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from file: $filePath", e)
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    fun clearCache() {
        cacheManager.getMemoryCache().evictAll()
        loadingTasks.clear()
    }
}
