package com.kvive.keyboard.stickers

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
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

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME)
    private val storageBucket: String? get() = storage.app.options.storageBucket

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(TAG, "Created sticker cache directory")
        }
    }

    private suspend fun fetchPacksFromStorage(): List<StickerPack> {
        val bucket = storageBucket
        if (bucket.isNullOrEmpty()) {
            Log.e(TAG, "Storage bucket is null or empty! Cannot fetch packs.")
            return emptyList()
        }
        Log.d(TAG, "Fetching packs from storage bucket: $bucket")
        val listResult = storage.reference.child(STICKERS_FOLDER).listAll().await()
        val packs = mutableListOf<StickerPack>()

        listResult.prefixes.forEach { prefix ->
            val packId = prefix.name
            Log.d(TAG, "Processing pack: $packId")
            val manifest = ensureManifest(packId, bucket, forceDownload = true)
            if (manifest != null) {
                val (pack, stickers) = manifest
                Log.d(TAG, "✅ Manifest for $packId loaded (${stickers.size} stickers)")
                packs.add(pack)
                saveStickersToCache(packId, stickers)
            } else {
                Log.w(TAG, "Manifest missing for pack $packId")
            }
        }

        return packs.sortedBy { it.name.lowercase() }
    }

    private suspend fun fetchPacksFromFirestore(): List<StickerPack> {
        return try {
            val snapshot = firestore.collection(COLLECTION_STICKER_PACKS).get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    StickerPack.fromFirestore(doc)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing pack ${doc.id} from Firestore", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch packs from Firestore", e)
            emptyList()
        }
    }

    // -----------------------------------------------------
    // 1️⃣ Get all sticker packs (cached + Firebase sync)
    // -----------------------------------------------------
    suspend fun getStickerPacks(forceRefresh: Boolean = false): List<StickerPack> {
        val cached = if (!forceRefresh) loadPacksFromCache() else null
        if (!forceRefresh && !cached.isNullOrEmpty()) {
            Log.d(TAG, "Serving ${cached.size} sticker packs from cache")
            syncPacksInBackground()
            return cached
        }

        return try {
            val packs = fetchPacksFromStorage()
            savePacksToCache(packs)
            packs
        } catch (storageError: Exception) {
            Log.e(TAG, "Failed to load sticker packs from Firebase Storage, falling back to Firestore", storageError)
            if (!cached.isNullOrEmpty()) {
                return cached
            }
            val firestorePacks = fetchPacksFromFirestore()
            if (firestorePacks.isNotEmpty()) {
                savePacksToCache(firestorePacks)
            }
            firestorePacks
        }
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

        val bucket = storageBucket
        if (bucket.isNullOrEmpty()) {
            Log.e(TAG, "Storage bucket is null or empty! Cannot fetch stickers for pack $packId")
            return loadStickersFromCache(packId) ?: emptyList()
        }
        val manifest = ensureManifest(packId, bucket, forceDownload = forceRefresh)
        if (manifest != null) {
            val (pack, stickers) = manifest
            saveStickersToCache(packId, stickers)
            Log.d(TAG, "Loaded ${stickers.size} stickers from manifest for pack ${pack.id}")
            return stickers
        }

        // Final fallback to Firestore
        return try {
            val snapshot = firestore.collection(COLLECTION_STICKER_PACKS)
                .document(packId)
                .collection(COLLECTION_STICKERS)
                .get()
                .await()

            val stickers = snapshot.documents.mapNotNull { doc ->
                try {
                    StickerData.fromFirestore(doc, packId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing sticker ${doc.id} from Firestore", e)
                    null
                }
            }

            saveStickersToCache(packId, stickers)
            stickers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch stickers for $packId via Firestore fallback", e)
            loadStickersFromCache(packId) ?: emptyList()
        }
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

        return try {
            when {
                sticker.storagePath != null -> {
                    val ref = storage.reference.child(sticker.storagePath)
                    ref.getFile(file).await()
                }
                sticker.imageUrl.startsWith("gs://") || sticker.imageUrl.contains("firebasestorage") -> {
                    val ref = storage.getReferenceFromUrl(sticker.imageUrl)
                    ref.getFile(file).await()
                }
                sticker.imageUrl.startsWith("http") -> {
                    downloadFromUrl(sticker.imageUrl, file)
                }
                else -> {
                    Log.e(TAG, "Unsupported URL format for sticker ${sticker.id}: ${sticker.imageUrl}")
                    return null
                }
            }

            Log.d(TAG, "Successfully downloaded sticker ${sticker.id}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download sticker ${sticker.id}: ${sticker.imageUrl}", e)
            null
        }
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
        // Launch background sync - in a real implementation, 
        // you'd use a coroutine scope tied to application lifecycle
        Thread {
            try {
                val packs = runBlocking { fetchPacksFromStorage() }
                savePacksToCache(packs)
                Log.d(TAG, "Background sync completed for ${packs.size} packs")
            } catch (e: Exception) {
                Log.e(TAG, "Background sync failed", e)
            }
        }.start()
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
        bucket: String,
        forceDownload: Boolean
    ): Pair<StickerPack, List<StickerData>>? {
        val manifestFile = File(cacheDir, "$packId$MANIFEST_SUFFIX")
        if (!forceDownload && manifestFile.exists()) {
            parseManifestFile(manifestFile, packId, bucket)?.let { return it }
        }

        return try {
            val ref = storage.reference.child("$STICKERS_FOLDER/$packId/$MANIFEST_FILE_NAME")
            val tempFile = File(cacheDir, "${packId}_manifest.tmp")
            ref.getFile(tempFile).await()
            tempFile.copyTo(manifestFile, overwrite = true)
            tempFile.delete()
            parseManifestFile(manifestFile, packId, bucket)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download manifest for $packId", e)
            if (manifestFile.exists()) {
                parseManifestFile(manifestFile, packId, bucket)
            } else {
                null
            }
        }
    }

    private fun parseManifestFile(
        manifestFile: File,
        packId: String,
        bucket: String
    ): Pair<StickerPack, List<StickerData>>? {
        return try {
            val manifestContent = manifestFile.readText()
            Log.d(TAG, "Parsing manifest for pack $packId, bucket: $bucket")
            Log.d(TAG, "Manifest content: $manifestContent")
            
            val json = JSONObject(manifestContent)
            val stickersArray = json.optJSONArray("stickers") ?: JSONArray()
            Log.d(TAG, "Found ${stickersArray.length()} stickers in manifest array")
            
            if (bucket.isNullOrEmpty()) {
                Log.e(TAG, "Bucket is null or empty! Cannot build download URLs for pack $packId")
                return null
            }
            
            val basePath = "$STICKERS_FOLDER/$packId"
            val thumbnailFile = json.optString("thumbnail").takeIf { it.isNotEmpty() }
            val thumbnailPath = thumbnailFile?.let { "$basePath/$it" }
            val thumbnailUrl = thumbnailPath?.let { buildDownloadUrl(bucket, it) }
                ?.takeIf { it.isNotEmpty() }
                ?: json.optString("thumbnailUrl", "")

            val pack = StickerPack.fromManifest(
                packId = packId,
                manifest = json,
                thumbnailUrl = thumbnailUrl,
                storagePath = basePath
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
                    val imageUrl = buildDownloadUrl(bucket, storagePath)
                    
                    Log.d(TAG, "Built URL for $fileName: $imageUrl")
                    
                    if (imageUrl.isNotEmpty()) {
                        stickers.add(
                            StickerData.fromManifest(
                                packId = packId,
                                stickerObj = stickerObj,
                                imageUrl = imageUrl,
                                storagePath = storagePath
                            )
                        )
                        Log.d(TAG, "✅ Added sticker: $fileName")
                    } else {
                        Log.w(TAG, "Failed to build URL for sticker $fileName, skipping")
                    }
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

    private fun buildDownloadUrl(bucket: String?, relativePath: String?): String {
        if (bucket.isNullOrEmpty() || relativePath.isNullOrEmpty()) {
            Log.w(TAG, "buildDownloadUrl: bucket or path is empty. bucket='$bucket', path='$relativePath'")
            return ""
        }
        val cleanPath = relativePath.trimStart('/')
        val encoded = cleanPath.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name())
        }
        return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encoded?alt=media"
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
