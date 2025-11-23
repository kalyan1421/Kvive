package com.kvive.keyboard.stickers

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.text.Charsets

/**
 * StickerRepository — Firebase + JSON Cache replacement for StickerDatabase
 * 
 * This implementation provides:
 * - Firebase Firestore for cloud synchronization
 * - Local JSON cache for fast offline access
 * - Firebase Storage for sticker image files
 * - Real-time updates and multi-device sync
 */
class StickerRepository(private val context: Context) {

    companion object {
        private const val TAG = "StickerRepository"
        private const val CACHE_DIR_NAME = "stickers_cache"
        private const val PACKS_CACHE_FILE = "packs.json"
        private const val COLLECTION_STICKER_PACKS = "sticker_packs"
        private const val COLLECTION_STICKERS = "stickers"
        private const val MANIFEST_SUFFIX = "_manifest.json"
        private const val MANIFEST_FILE_NAME = "pack.json"
        private const val STICKERS_FOLDER = "stickers"
    }

    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME)

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "Created sticker cache directory")
        }
    }

    private suspend fun fetchPacksFromStorage(): List<StickerPack> {
        Log.d(TAG, "Offline mode: skipping Firebase Storage fetch")
        return emptyList()
    }

    private suspend fun fetchPacksFromFirestore(): List<StickerPack> {
        Log.d(TAG, "Offline mode: skipping Firestore fetch")
        return emptyList()
    }

    // -----------------------------------------------------
    // 1️⃣ Get all sticker packs (cached + Firebase sync)
    // -----------------------------------------------------
    suspend fun getStickerPacks(forceRefresh: Boolean = false): List<StickerPack> {
        val cached = if (!forceRefresh) loadPacksFromCache() else null
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            Log.d(TAG, "Serving ${cached.size} sticker packs from cache")
            return cached
        }

        val packs = fetchPacksFromStorage()
        if (packs.isNotEmpty()) {
            savePacksToCache(packs)
            return packs
        }
        val firestorePacks = fetchPacksFromFirestore()
        if (firestorePacks.isNotEmpty()) {
            savePacksToCache(firestorePacks)
            return firestorePacks
        }
        return cached ?: emptyList()
    }

    // -----------------------------------------------------
    // 2️⃣ Get stickers for a specific pack
    // -----------------------------------------------------
    suspend fun getStickersForPack(packId: String, forceRefresh: Boolean = false): List<StickerData> {
        if (!forceRefresh) {
            loadStickersFromCache(packId)?.let { cached ->
                if (cached.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${cached.size} stickers for $packId from cache")
                    return cached
                }
            }
        }

        // Offline mode: serve cached stickers only
        return loadStickersFromCache(packId) ?: emptyList()
    }

    // -----------------------------------------------------
    // 3️⃣ Download sticker image to local cache
    // -----------------------------------------------------
    suspend fun downloadSticker(sticker: StickerData): String? {
        val fileName = "${sticker.id}.${getFileExtension(sticker.imageUrl)}"
        val file = File(cacheDir, fileName)
        
        // Check if already downloaded
        if (file.exists()) {
            Log.d(TAG, "Sticker ${sticker.id} already cached")
            return file.absolutePath
        }

        Log.d(TAG, "Offline mode: skipping sticker download for ${sticker.id}")
        return null
    }

    // -----------------------------------------------------
    // 4️⃣ Search stickers by tags or emojis
    // -----------------------------------------------------
    suspend fun searchStickers(query: String): List<StickerData> {
        val searchQuery = query.lowercase().trim()
        val results = mutableListOf<StickerData>()

        try {
            // Get all packs and search through their stickers
            val packs = getStickerPacks()
            for (pack in packs) {
                val stickers = getStickersForPack(pack.id)
                stickers.forEach { sticker ->
                    if (matchesSearchQuery(sticker, searchQuery)) {
                        results.add(sticker)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching stickers", e)
        }

        return results
    }

    // -----------------------------------------------------
    // 5️⃣ Update sticker usage statistics
    // -----------------------------------------------------
    suspend fun recordStickerUsage(stickerId: String) {
        try {
            // This would typically update Firestore analytics or local usage tracking
            // For now, just log the usage
            Log.d(TAG, "Recorded usage for sticker: $stickerId")
            
            // Could implement usage tracking in Firestore:
            // firestore.collection("sticker_usage")
            //     .document(stickerId)
            //     .update("usageCount", FieldValue.increment(1))
        } catch (e: Exception) {
            Log.e(TAG, "Error recording sticker usage", e)
        }
    }

    // -----------------------------------------------------
    // 6️⃣ Private helper methods
    // -----------------------------------------------------
    private fun syncPacksInBackground() {
        // Offline mode: background sync disabled
    }

    private fun savePacksToCache(packs: List<StickerPack>) {
        try {
            val jsonArray = JSONArray()
            packs.forEach { pack ->
                jsonArray.put(pack.toJson())
            }
            
            val cacheObject = JSONObject().apply {
                put("packs", jsonArray)
                put("lastUpdated", System.currentTimeMillis())
                put("version", "2.0")
            }
            
            File(cacheDir, PACKS_CACHE_FILE).writeText(cacheObject.toString())
            Log.d(TAG, "Saved ${packs.size} packs to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving packs to cache", e)
        }
    }

    private fun saveStickersToCache(packId: String, stickers: List<StickerData>) {
        try {
            val jsonArray = JSONArray()
            stickers.forEach { sticker ->
                jsonArray.put(sticker.toJson())
            }
            
            File(cacheDir, "$packId.json").writeText(jsonArray.toString())
            Log.d(TAG, "Saved ${stickers.size} stickers to cache for pack $packId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving stickers to cache for pack $packId", e)
        }
    }

    private fun loadStickersFromCache(packId: String): List<StickerData>? {
        val cacheFile = File(cacheDir, "$packId.json")
        if (!cacheFile.exists()) return null

        return try {
            val jsonArray = JSONArray(cacheFile.readText())
            val stickers = mutableListOf<StickerData>()
            for (i in 0 until jsonArray.length()) {
                stickers.add(StickerData.fromJson(jsonArray.getJSONObject(i)))
            }
            stickers
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached stickers for pack $packId", e)
            null
        }
    }

    private suspend fun ensureManifest(
        packId: String,
        forceDownload: Boolean
    ): Pair<StickerPack, List<StickerData>>? {
        val manifestFile = File(cacheDir, "$packId$MANIFEST_SUFFIX")
        if (!forceDownload && manifestFile.exists()) {
            parseManifestFile(manifestFile, packId)?.let { return it }
        }
        Log.d(TAG, "Offline mode: manifest download skipped for $packId")
        return null
    }

    private fun parseManifestFile(
        manifestFile: File,
        packId: String
    ): Pair<StickerPack, List<StickerData>>? {
        return try {
            val manifestContent = manifestFile.readText()
            Log.d(TAG, "Parsing manifest for pack $packId")
            Log.d(TAG, "Manifest content: $manifestContent")
            
            val json = JSONObject(manifestContent)
            val stickersArray = json.optJSONArray("stickers") ?: JSONArray()
            Log.d(TAG, "Found ${stickersArray.length()} stickers in manifest array")
            
            val basePath = "$STICKERS_FOLDER/$packId"
            val thumbnailFile = json.optString("thumbnail").takeIf { it.isNotEmpty() }
            val thumbnailPath = thumbnailFile?.let { "$basePath/$it" }
            val thumbnailUrl = json.optString("thumbnailUrl", thumbnailPath ?: "")

            val pack = StickerPack.fromManifest(
                packId = packId,
                manifest = json,
                thumbnailUrl = thumbnailUrl,
                storagePath = null
            )

            val stickers = mutableListOf<StickerData>()
            for (i in 0 until stickersArray.length()) {
                try {
                    val stickerObj = stickersArray.getJSONObject(i)
                    val fileName = stickerObj.optString("file", "")
                    Log.d(TAG, "Processing sticker $i: file='$fileName'")
                    
                    if (fileName.isEmpty()) {
                        Log.w(TAG, "Sticker $i has empty 'file' field, skipping")
                        continue
                    }
                    
                    val storagePath = "$basePath/$fileName"
                    val imageUrl = stickerObj.optString("url", storagePath)
                    stickers.add(
                        StickerData.fromManifest(
                            packId = packId,
                            stickerObj = stickerObj,
                            imageUrl = imageUrl,
                            storagePath = null
                        )
                    )
                    Log.d(TAG, "✅ Added sticker: $fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing sticker $i in pack $packId", e)
                }
            }

            Log.d(TAG, "✅ Parsed ${stickers.size} stickers from manifest for pack $packId")
            pack.copy(stickerCount = stickers.size) to stickers
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manifest for pack $packId", e)
            e.printStackTrace()
            null
        }
    }

    private fun loadPacksFromCache(): List<StickerPack>? {
        return try {
            val cacheFile = File(cacheDir, PACKS_CACHE_FILE)
            if (!cacheFile.exists()) return null
            
            val jsonObject = JSONObject(cacheFile.readText())
            val packsArray = jsonObject.optJSONArray("packs") ?: return null
            val packs = mutableListOf<StickerPack>()
            
            for (i in 0 until packsArray.length()) {
                try {
                    packs.add(StickerPack.fromJson(packsArray.getJSONObject(i)))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing cached pack at index $i", e)
                }
            }
            
            packs
        } catch (e: Exception) {
            Log.e(TAG, "Error loading packs from cache", e)
            null
        }
    }

    private fun matchesSearchQuery(sticker: StickerData, query: String): Boolean {
        // Search in tags
        if (sticker.tags.any { it.lowercase().contains(query) }) return true
        
        // Search in emojis
        if (sticker.emojis.any { it.contains(query) }) return true
        
        // Search in sticker ID
        if (sticker.id.lowercase().contains(query)) return true
        
        return false
    }

    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".jpg", ignoreCase = true) || url.contains(".jpeg", ignoreCase = true) -> "jpg"
            url.contains(".gif", ignoreCase = true) -> "gif"
            url.contains(".webp", ignoreCase = true) -> "webp"
            else -> "png" // Default to PNG
        }
    }

    private suspend fun downloadFromUrl(url: String, file: File) {
        // Simple HTTP download implementation
        // In production, you might want to use a more robust HTTP client
        val connection = java.net.URL(url).openConnection()
        connection.doInput = true
        connection.connect()

        connection.getInputStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    // -----------------------------------------------------
    // 7️⃣ Cache management methods
    // -----------------------------------------------------
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "Cleared sticker cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    fun getCacheSize(): Long {
        return try {
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
            0L
        }
    }
}
