package com.kvive.keyboard.stickers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kvive.keyboard.MediaCacheManager
import com.kvive.keyboard.LazyMediaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service adapter that bridges StickerRepository with MediaCacheManager
 * 
 * This class provides a unified interface for sticker management that combines:
 * - Firebase + JSON cache for metadata (StickerRepository) 
 * - Bitmap caching and media handling (MediaCacheManager)
 * - Backwards compatibility with existing keyboard service
 */
class StickerServiceAdapter(private val context: Context) {

    companion object {
        private const val TAG = "StickerServiceAdapter"
    }

    private val stickerRepository = StickerRepository(context)
    private val mediaCacheManager = MediaCacheManager(context)
    private val mediaLoader = LazyMediaLoader(context)

    // -----------------------------------------------------
    // 1️⃣ Pack Management (replaces StickerManager methods)
    // -----------------------------------------------------
    
    suspend fun getInstalledPacks(): List<StickerPack> {
        return withContext(Dispatchers.IO) {
            try {
                val allPacks = stickerRepository.getStickerPacks()
                // Filter for installed packs or mark as installed if stickers are locally cached
                allPacks.map { pack ->
                    val stickers = stickerRepository.getStickersForPack(pack.id)
                    val downloadedCount = stickers.count { sticker ->
                        isLocallyAvailable(sticker.id)
                    }
                    val progress = if (stickers.isNotEmpty()) {
                        (downloadedCount * 100) / stickers.size
                    } else 0
                    
                    pack.copy(
                        isInstalled = progress == 100,
                        installProgress = progress,
                        stickerCount = stickers.size
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting installed packs", e)
                emptyList()
            }
        }
    }

    suspend fun getAvailablePacks(forceRefresh: Boolean = false): List<StickerPack> {
        return withContext(Dispatchers.IO) {
            try {
                stickerRepository.getStickerPacks(forceRefresh)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting available packs", e)
                emptyList()
            }
        }
    }

    suspend fun getStickersFromPack(packId: String): List<StickerData> {
        return withContext(Dispatchers.IO) {
            try {
                val stickers = stickerRepository.getStickersForPack(packId)
                // Update local availability status
                stickers.map { sticker ->
                    sticker.copy(
                        isDownloaded = isLocallyAvailable(sticker.id),
                        localPath = getLocalPath(sticker.id)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting stickers for pack $packId", e)
                emptyList()
            }
        }
    }

    // -----------------------------------------------------
    // 2️⃣ Sticker Installation & Download
    // -----------------------------------------------------
    
    suspend fun installStickerPack(
        pack: StickerPack, 
        progressCallback: (Int) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Installing sticker pack: ${pack.name}")
                
                val stickers = stickerRepository.getStickersForPack(pack.id)
                var downloadedCount = 0
                val totalStickers = stickers.size
                
                stickers.forEach { sticker ->
                    try {
                        val localPath = downloadStickerIfNeeded(sticker)
                        if (localPath != null) {
                            downloadedCount++
                            val progress = (downloadedCount * 100) / totalStickers
                            progressCallback(progress)
                            Log.d(TAG, "Downloaded sticker ${sticker.id}, progress: $progress%")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download sticker ${sticker.id}", e)
                    }
                }
                
                if (downloadedCount == totalStickers) {
                    Log.d(TAG, "Successfully installed sticker pack: ${pack.name}")
                } else {
                    Log.w(TAG, "Partial installation: $downloadedCount/$totalStickers stickers")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error installing sticker pack: ${pack.name}", e)
            }
        }
    }

    suspend fun downloadStickerIfNeeded(sticker: StickerData): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Check if already available locally
                val existingPath = getLocalPath(sticker.id)
                if (existingPath != null) {
                    return@withContext existingPath
                }

                // Download using StickerRepository
                val downloadedPath = stickerRepository.downloadSticker(sticker)
                if (downloadedPath != null) {
                    Log.d(TAG, "Downloaded sticker ${sticker.id} to $downloadedPath")
                }
                downloadedPath
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading sticker ${sticker.id}", e)
                null
            }
        }
    }

    // -----------------------------------------------------  
    // 3️⃣ Search & Usage Tracking
    // -----------------------------------------------------
    
    suspend fun searchStickers(query: String): List<StickerData> {
        return withContext(Dispatchers.IO) {
            try {
                stickerRepository.searchStickers(query)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching stickers", e)
                emptyList()
            }
        }
    }

    suspend fun getRecentStickers(limit: Int = 20): List<StickerData> {
        return withContext(Dispatchers.IO) {
            try {
                // Get all installed stickers and sort by last used
                val allPacks = getInstalledPacks()
                val recentStickers = mutableListOf<StickerData>()
                
                allPacks.forEach { pack ->
                    val stickers = getStickersFromPack(pack.id)
                    recentStickers.addAll(stickers.filter { it.lastUsed > 0 })
                }
                
                recentStickers.sortedByDescending { it.lastUsed }.take(limit)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting recent stickers", e)
                emptyList()
            }
        }
    }

    suspend fun recordStickerUsage(stickerId: String) {
        try {
            stickerRepository.recordStickerUsage(stickerId)
        } catch (e: Exception) {
            Log.e(TAG, "Error recording sticker usage", e)
        }
    }

    // -----------------------------------------------------
    // 4️⃣ Thumbnail & Bitmap Loading (integrating MediaCacheManager)
    // -----------------------------------------------------
    
    fun loadStickerThumbnail(
        sticker: StickerData,
        targetWidth: Int = 120,
        targetHeight: Int = 120,
        callback: (Bitmap?) -> Unit
    ) {
        try {
            // Use the existing MediaCacheManager for efficient thumbnail loading
            mediaLoader.loadThumbnail(
                url = sticker.imageUrl,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                callback = callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail for ${sticker.id}", e)
            callback(null)
        }
    }

    fun loadPackThumbnail(
        pack: StickerPack,
        targetWidth: Int = 80,
        targetHeight: Int = 80,
        callback: (Bitmap?) -> Unit
    ) {
        try {
            mediaLoader.loadThumbnail(
                url = pack.thumbnailUrl,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                callback = callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pack thumbnail for ${pack.id}", e)
            callback(null)
        }
    }

    // -----------------------------------------------------
    // 5️⃣ Local File Management  
    // -----------------------------------------------------
    
    private fun isLocallyAvailable(stickerId: String): Boolean {
        return getLocalPath(stickerId) != null
    }

    private fun getLocalPath(stickerId: String): String? {
        // Check cache directories for the sticker file
        val extensions = listOf("png", "jpg", "jpeg", "gif", "webp")
        val cacheDir = java.io.File(context.filesDir, "stickers_cache")
        val mediaCacheDir = java.io.File(context.cacheDir, "stickers")

        val cacheDirectories = listOf(cacheDir, mediaCacheDir)

        cacheDirectories.forEach { dir ->
            if (dir.exists()) {
                extensions.forEach { ext ->
                    val file = java.io.File(dir, "$stickerId.$ext") 
                    if (file.exists() && file.length() > 0) {
                        return file.absolutePath
                    }
                }
            }
        }
        return null
    }

    // -----------------------------------------------------
    // 6️⃣ Cache Management
    // -----------------------------------------------------
    
    fun clearAllStickerCache() {
        try {
            stickerRepository.clearCache()
            mediaCacheManager.performCleanup()
            Log.d(TAG, "Cleared all sticker caches")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing sticker cache", e)
        }
    }

    fun getCacheStats(): StickerCacheStats {
        return try {
            val repoSize = stickerRepository.getCacheSize()
            val mediaSize = mediaCacheManager.getCacheStats().stickerCacheSize
            
            StickerCacheStats(
                metadataCacheSize = repoSize,
                imageCacheSize = mediaSize,
                totalSize = repoSize + mediaSize,
                memoryUsage = mediaCacheManager.getMemoryCache().size()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache stats", e)
            StickerCacheStats()
        }
    }

    // -----------------------------------------------------
    // 7️⃣ Lifecycle Management
    // -----------------------------------------------------
    
    fun onLowMemory() {
        try {
            mediaCacheManager.optimizeForMemory()
            Log.d(TAG, "Optimized sticker cache for low memory")
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing for low memory", e)
        }
    }

    fun preloadFrequentContent() {
        try {
            mediaCacheManager.preloadFrequentContent()
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading content", e)
        }
    }
}

/**
 * Data class for sticker cache statistics
 */
data class StickerCacheStats(
    val metadataCacheSize: Long = 0L,
    val imageCacheSize: Long = 0L, 
    val totalSize: Long = 0L,
    val memoryUsage: Int = 0
)
