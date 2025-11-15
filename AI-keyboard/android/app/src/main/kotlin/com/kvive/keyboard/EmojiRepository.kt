package com.kvive.keyboard

import android.content.Context
import com.kvive.keyboard.utils.LogUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * Centralized emoji data loader that reads the canonical dataset from assets.
 * Provides category/group lookups, popularity ordering, and lightweight search.
 */
object EmojiRepository {
    private const val TAG = "EmojiRepository"
    private const val ASSET_PATH = "emoji/emojis.json"

    data class EmojiInfo(
        val char: String,
        val unified: String,
        val name: String?,
        val shortNames: List<String>,
        val category: String,
        val sortOrder: Int
    )

    private var isLoaded = false
    private var allEmojis: List<EmojiInfo> = emptyList()
    private var emojisByCategory: Map<String, List<EmojiInfo>> = emptyMap()
    private var appContext: Context? = null

    // Alias map from UI labels to dataset categories
    private val categoryAliases: Map<String, List<String>> = mapOf(
        "Recent" to emptyList(),
        "Smileys" to listOf("Smileys & Emotion"),
        "People" to listOf("People & Body"),
        "Animals" to listOf("Animals & Nature"),
        "Food" to listOf("Food & Drink"),
        "Activities" to listOf("Activities"),
        "Travel" to listOf("Travel & Places"),
        "Objects" to listOf("Objects"),
        "Symbols" to listOf("Symbols", "Skin Tones"),
        "Flags" to listOf("Flags")
    )

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (isLoaded) return

        try {
            appContext = context.applicationContext
            val jsonText = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val array = JSONArray(jsonText)
            val entries = ArrayList<EmojiInfo>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val unified = obj.getString("unified")
                val char = unifiedToEmoji(unified)
                if (char.isEmpty()) continue

                val name = obj.optStringOrNull("name")
                val shortNamesArray = obj.optJSONArray("short_names") ?: JSONArray()
                val shortNames = buildList(shortNamesArray.length()) {
                    for (j in 0 until shortNamesArray.length()) {
                        add(shortNamesArray.getString(j))
                    }
                }
                val category = obj.optString("category", "Misc")
                val sortOrder = obj.optInt("sort_order", Int.MAX_VALUE)
                entries.add(
                    EmojiInfo(
                        char = char,
                        unified = unified,
                        name = name,
                        shortNames = shortNames,
                        category = category,
                        sortOrder = sortOrder
                    )
                )
            }

            allEmojis = entries.sortedBy { it.sortOrder }
            emojisByCategory = allEmojis.groupBy { it.category }
            isLoaded = true
            LogUtil.d(TAG, "✅ Loaded ${allEmojis.size} emojis from assets")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Failed to load emoji dataset", e)
            allEmojis = emptyList()
            emojisByCategory = emptyMap()
            isLoaded = false
        }
    }

    fun isReady(): Boolean = isLoaded

    fun getPopular(limit: Int = 50, context: Context? = null): List<EmojiInfo> {
        context?.let { ensureLoaded(it) }
        if (!isLoaded) {
            contextFromCache()?.let { ensureLoaded(it) }
        }
        return allEmojis.take(limit)
    }

    fun getByAlias(alias: String, context: Context, limit: Int? = null): List<EmojiInfo> {
        ensureLoaded(context)
        val categories = categoryAliases[alias] ?: listOf(alias)
        if (categories.isEmpty()) {
            // Recent has no category mapping; caller handles separately.
            return emptyList()
        }
        val results = categories.flatMap { cat ->
            emojisByCategory[cat].orEmpty()
        }.sortedBy { it.sortOrder }
        return if (limit != null) results.take(limit) else results
    }

    fun search(query: String, limit: Int = 40, context: Context? = null): List<EmojiInfo> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return getPopular(limit, context)
        }

        val ctx = context ?: contextFromCache()
        if (ctx != null) {
            ensureLoaded(ctx)
        }
        if (!isLoaded) return emptyList()

        val lower = trimmed.lowercase()
        return allEmojis
            .asSequence()
            .map { entry ->
                val score = when {
                    entry.shortNames.any { it.contains(lower) } -> 3
                    entry.name?.lowercase()?.contains(lower) == true -> 2
                    entry.category.lowercase().contains(lower) -> 1
                    else -> 0
                }
                score to entry
            }
            .filter { it.first > 0 }
            .sortedWith(
                compareByDescending<Pair<Int, EmojiInfo>> { it.first }
                    .thenBy { it.second.sortOrder }
            )
            .map { it.second }
            .distinct()
            .take(limit)
            .toList()
    }

    fun findByChar(emoji: String, context: Context? = null): EmojiInfo? {
        if (!isLoaded) {
            val ctx = context ?: contextFromCache()
            if (ctx != null) ensureLoaded(ctx)
        }
        return allEmojis.firstOrNull { it.char == emoji }
    }

    fun getAliasKeys(): Set<String> = categoryAliases.keys

    private fun contextFromCache(): Context? = appContext

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private fun unifiedToEmoji(unified: String): String {
        return try {
            val builder = StringBuilder()
            unified.split("-").forEach { hex ->
                val codePoint = hex.toInt(16)
                builder.append(String(Character.toChars(codePoint)))
            }
            builder.toString()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert unified '$unified' to emoji", e)
            ""
        }
    }
}
