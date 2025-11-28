package com.kvive.keyboard.symspell

import android.content.Context
import com.kvive.keyboard.utils.LogUtil
import java.util.concurrent.ConcurrentHashMap

object SymSpellLoader {
    private const val TAG = "SymSpellLoader"
    private const val DEFAULT_LANG = "en"
    private const val DEFAULT_ASSET = "symspell/frequency_dictionary_en_82_765.txt"
    private const val DEFAULT_MAX_EDIT = 2
    private const val DEFAULT_PREFIX_LEN = 7

    private val cache = ConcurrentHashMap<String, SymSpell>()

    /**
    * Load (or reuse) a SymSpell instance for the given language.
    * Merges the SymSpell asset with the in-memory language words so both stay in sync.
    */
    fun load(
        context: Context,
        language: String = DEFAULT_LANG,
        languageWords: Map<String, Int> = emptyMap(),
        maxEditDistance: Int = DEFAULT_MAX_EDIT,
        prefixLength: Int = DEFAULT_PREFIX_LEN
    ): SymSpell {
        cache[language]?.let { return it }

        val sym = SymSpell(maxEditDistance, prefixLength)
        val assetPath = resolveAssetPath(language)

        try {
            val assetLines = readAssetLines(context, assetPath)
            if (assetLines.isNotEmpty()) {
                sym.loadDictionary(assetLines)
                LogUtil.d(TAG, "Loaded ${assetLines.size} SymSpell entries for $language from $assetPath")
            } else {
                LogUtil.w(TAG, "⚠️ SymSpell asset missing for $language at $assetPath, using language words only")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to load SymSpell dictionary for $language", e)
        }

        // Merge language resources so SymSpell and LM share the same vocabulary
        languageWords.forEach { (word, freq) ->
            sym.addWord(word, freq)
        }

        cache[language] = sym
        return sym
    }

    private fun resolveAssetPath(language: String): String {
        return if (language == DEFAULT_LANG) {
            DEFAULT_ASSET
        } else {
            "symspell/frequency_dictionary_${language}.txt"
        }
    }

    private fun readAssetLines(context: Context, path: String): List<String> {
        return try {
            context.assets.open(path).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearCache() = cache.clear()
}
