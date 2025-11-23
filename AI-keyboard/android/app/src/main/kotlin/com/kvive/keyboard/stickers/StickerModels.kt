package com.kvive.keyboard.stickers

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sticker Pack data model - compatible with both JSON cache and Firestore
 */
data class StickerPack(
    val id: String,
    val name: String,
    val author: String,
    val thumbnailUrl: String,
    val category: String,
    val version: String = "1.0",
    val stickerCount: Int = 0,
    val isInstalled: Boolean = false,
    val installProgress: Int = 0,
    val description: String = "",
    val featured: Boolean = false,
    val tags: List<String> = emptyList(),
    val storagePath: String? = null
) {
    
    /**
     * Convert to JSON for local caching
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("author", author)
        put("thumbnailUrl", thumbnailUrl)
        put("category", category)
        put("version", version)
        put("stickerCount", stickerCount)
        put("isInstalled", isInstalled)
        put("installProgress", installProgress)
        put("description", description)
        put("featured", featured)
        put("tags", JSONArray(tags))
        put("storagePath", storagePath ?: "")
    }

    companion object {
        /**
         * Create from JSON cache
         */
        fun fromJson(obj: JSONObject): StickerPack {
            val tags = obj.optJSONArray("tags").toStringList()

            return StickerPack(
                id = obj.getString("id"),
                name = obj.getString("name"),
                author = obj.optString("author", "Unknown"),
                thumbnailUrl = obj.getString("thumbnailUrl"),
                category = obj.optString("category", "general"),
                version = obj.optString("version", "1.0"),
                stickerCount = obj.optInt("stickerCount", 0),
                isInstalled = obj.optBoolean("isInstalled", false),
                installProgress = obj.optInt("installProgress", 0),
                description = obj.optString("description", ""),
                featured = obj.optBoolean("featured", false),
                tags = tags,
                storagePath = obj.optString("storagePath").takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Convert from legacy assets format
         */
        fun fromLegacyJson(obj: JSONObject): StickerPack {
            val stickersArray = obj.optJSONArray("stickers")
            val stickerCount = stickersArray?.length() ?: 0
            
            return StickerPack(
                id = obj.getString("id"),
                name = obj.getString("name"),
                author = obj.optString("author", "AI Keyboard Team"),
                thumbnailUrl = obj.optString("thumbnail", obj.optString("thumbnailUrl", "")),
                category = obj.optString("category", "general"),
                version = obj.optString("version", "1.0"),
                stickerCount = stickerCount
            )
        }

        /**
         * Create from Firebase Storage pack.json manifest
         */
        fun fromManifest(
            packId: String,
            manifest: JSONObject,
            thumbnailUrl: String,
            storagePath: String?
        ): StickerPack {
            return StickerPack(
                id = packId,
                name = manifest.optString("name", packId),
                author = manifest.optString("author", "Kvīve Studio"),
                thumbnailUrl = thumbnailUrl,
                category = manifest.optString("category", "general"),
                version = manifest.optString("version", "1.0"),
                stickerCount = manifest.optJSONArray("stickers")?.length()
                    ?: manifest.optInt("stickerCount", 0),
                isInstalled = manifest.optBoolean("isInstalled", false),
                installProgress = manifest.optInt("installProgress", 0),
                description = manifest.optString("description", ""),
                featured = manifest.optBoolean("featured", false),
                tags = manifest.optJSONArray("tags").toStringList(),
                storagePath = storagePath
            )
        }

    }
}

/**
 * Individual Sticker data model - compatible with both JSON cache and Firestore  
 */
data class StickerData(
    val id: String,
    val packId: String,
    val imageUrl: String,
    val tags: List<String> = emptyList(),
    val emojis: List<String> = emptyList(),
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val usageCount: Int = 0,
    val lastUsed: Long = 0L,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val storagePath: String? = null
) {

    /**
     * Convert to JSON for local caching
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("packId", packId)
        put("imageUrl", imageUrl)
        put("tags", JSONArray(tags))
        put("emojis", JSONArray(emojis))
        put("localPath", localPath ?: "")
        put("isDownloaded", isDownloaded)
        put("usageCount", usageCount)
        put("lastUsed", lastUsed)
        put("fileSize", fileSize)
        put("width", width)
        put("height", height)
        put("storagePath", storagePath ?: "")
    }

    companion object {
        /**
         * Create from JSON cache
         */
        fun fromJson(obj: JSONObject): StickerData {
            val tags = obj.optJSONArray("tags").toStringList()
            val emojis = obj.optJSONArray("emojis").toStringList()

            return StickerData(
                id = obj.getString("id"),
                packId = obj.getString("packId"),
                imageUrl = obj.getString("imageUrl"),
                tags = tags,
                emojis = emojis,
                localPath = obj.optString("localPath").takeIf { it.isNotEmpty() },
                isDownloaded = obj.optBoolean("isDownloaded", false),
                usageCount = obj.optInt("usageCount", 0),
                lastUsed = obj.optLong("lastUsed", 0L),
                fileSize = obj.optLong("fileSize", 0L),
                width = obj.optInt("width", 0),
                height = obj.optInt("height", 0),
                storagePath = obj.optString("storagePath").takeIf { it.isNotEmpty() }
            )
        }

        /**
         * Convert from legacy assets format
         */
        fun fromLegacyJson(obj: JSONObject, packId: String): StickerData {
            val tags = obj.optJSONArray("tags").toStringList()
            val emojis = obj.optJSONArray("emojis").toStringList()

            // Handle legacy 'file' field vs 'imageUrl'
            val imageUrl = obj.optString("imageUrl").takeIf { it.isNotEmpty() }
                ?: obj.optString("file", "")

            return StickerData(
                id = obj.getString("id"),
                packId = packId,
                imageUrl = imageUrl,
                tags = tags,
                emojis = emojis
            )
        }

        /**
         * Create from Firebase Storage pack manifest
         */
        fun fromManifest(
            packId: String,
            stickerObj: JSONObject,
            imageUrl: String,
            storagePath: String?
        ): StickerData {
            return StickerData(
                id = stickerObj.optString("id")
                    .takeIf { it.isNotEmpty() }
                    ?: stickerObj.optString("file")
                    ?: "${packId}_${System.currentTimeMillis()}",
                packId = packId,
                imageUrl = imageUrl,
                tags = stickerObj.optJSONArray("tags").toStringList(),
                emojis = stickerObj.optJSONArray("emojis").toStringList(),
                width = stickerObj.optInt("width", 0),
                height = stickerObj.optInt("height", 0),
                fileSize = stickerObj.optLong("fileSize", 0L),
                storagePath = storagePath
            )
        }
    }
}

/**
 * Sticker search result with relevance scoring
 */
data class StickerSearchResult(
    val sticker: StickerData,
    val relevanceScore: Float,
    val matchType: MatchType
)

enum class MatchType {
    EXACT_TAG,
    PARTIAL_TAG,
    EMOJI,
    ID_MATCH
}

/**
 * Sticker pack category for organization
 */
enum class StickerCategory(val displayName: String) {
    ANIMALS("Animals"),
    EMOTIONS("Emotions"), 
    BUSINESS("Business"),
    FOOD("Food"),
    SPORTS("Sports"),
    TRAVEL("Travel"),
    TECHNOLOGY("Technology"),
    GENERAL("General"),
    FEATURED("Featured"),
    RECENT("Recently Used");
    
    companion object {
        fun fromString(category: String): StickerCategory {
            return values().find { 
                it.name.equals(category, ignoreCase = true) || 
                it.displayName.equals(category, ignoreCase = true)
            } ?: GENERAL
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        val value = optString(i)
        if (!value.isNullOrEmpty()) {
            list.add(value)
        }
    }
    return list
}
