package com.kvive.keyboard

/**
 * Language configuration data classes for multilingual keyboard support
 */
data class LanguageConfig(
    val code: String,
    val name: String,
    val nativeName: String,
    val layoutType: LayoutType,
    val script: Script,
    val direction: TextDirection,
    val hasAccents: Boolean,
    val dictionaryFile: String,
    val correctionRules: String,
    val flag: String,
    val source: Source = Source.LOCAL,
    val version: Int = 1
)

enum class LayoutType {
    QWERTY, AZERTY, QWERTZ, DEVANAGARI, INSCRIPT, CUSTOM
}

enum class Script {
    LATIN, DEVANAGARI, ARABIC, CYRILLIC, TELUGU, TAMIL, MALAYALAM
}

enum class TextDirection {
    LTR, RTL
}

enum class Source {
    LOCAL,   // Bundled with app
    REMOTE   // Downloaded from Firebase
}

data class Correction(
    val originalWord: String,
    val correctedWord: String,
    val confidence: Double,
    val language: String
)

/**
 * Predefined language configurations with hybrid local/remote support
 */
object LanguageConfigs {
    
    // Base languages bundled with the app
    private val LOCAL_LANGUAGES = mapOf(
        "en" to LanguageConfig(
            code = "en",
            name = "English",
            nativeName = "English",
            layoutType = LayoutType.QWERTY,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "en_words.txt",
            correctionRules = "en_corrections.txt",
            flag = "🇺🇸"
        ),
        "es" to LanguageConfig(
            code = "es",
            name = "Spanish",
            nativeName = "Español",
            layoutType = LayoutType.QWERTY,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "es_words.txt",
            correctionRules = "es_corrections.txt",
            flag = "🇪🇸"
        ),
        "fr" to LanguageConfig(
            code = "fr",
            name = "French",
            nativeName = "Français",
            layoutType = LayoutType.AZERTY,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "fr_words.txt",
            correctionRules = "fr_corrections.txt",
            flag = "🇫🇷"
        ),
        "de" to LanguageConfig(
            code = "de",
            name = "German",
            nativeName = "Deutsch",
            layoutType = LayoutType.QWERTZ,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "de_words.txt",
            correctionRules = "de_corrections.txt",
            flag = "🇩🇪"
        ),
        "hi" to LanguageConfig(
            code = "hi",
            name = "Hindi",
            nativeName = "हिन्दी",
            layoutType = LayoutType.INSCRIPT,
            script = Script.DEVANAGARI,
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "hi_words.txt",
            correctionRules = "hi_corrections.txt",
            flag = "🇮🇳",
            source = Source.LOCAL
        ),
        "te" to LanguageConfig(
            code = "te",
            name = "Telugu",
            nativeName = "తెలుగు",
            layoutType = LayoutType.INSCRIPT,
            script = Script.TELUGU,
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "te_words.txt",
            correctionRules = "te_corrections.txt",
            flag = "🇮🇳",
            source = Source.LOCAL
        ),
        "ta" to LanguageConfig(
            code = "ta",
            name = "Tamil",
            nativeName = "தமிழ்",
            layoutType = LayoutType.INSCRIPT,
            script = Script.TAMIL,
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "ta_words.txt",
            correctionRules = "ta_corrections.txt",
            flag = "🇮🇳",
            source = Source.LOCAL
        ),
        "ml" to LanguageConfig(
            code = "ml",
            name = "Malayalam",
            nativeName = "മലയാളം",
            layoutType = LayoutType.INSCRIPT,
            script = Script.MALAYALAM,
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "ml_words.txt",
            correctionRules = "ml_corrections.txt",
            flag = "🇮🇳",
            source = Source.LOCAL
        ),
        "ar" to LanguageConfig(
            code = "ar",
            name = "Arabic",
            nativeName = "العربية",
            layoutType = LayoutType.CUSTOM,
            script = Script.ARABIC,
            direction = TextDirection.RTL,
            hasAccents = false,
            dictionaryFile = "ar_dict.db",
            correctionRules = "ar_rules.json",
            flag = "🇸🇦",
            source = Source.LOCAL
        ),
        "ru" to LanguageConfig(
            code = "ru",
            name = "Russian",
            nativeName = "Русский",
            layoutType = LayoutType.CUSTOM,
            script = Script.CYRILLIC,
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "ru_dict.db",
            correctionRules = "ru_rules.json",
            flag = "🇷🇺",
            source = Source.LOCAL
        ),
        "pt" to LanguageConfig(
            code = "pt",
            name = "Portuguese",
            nativeName = "Português",
            layoutType = LayoutType.QWERTY,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "pt_dict.db",
            correctionRules = "pt_rules.json",
            flag = "🇵🇹",
            source = Source.LOCAL
        ),
        "it" to LanguageConfig(
            code = "it",
            name = "Italian",
            nativeName = "Italiano",
            layoutType = LayoutType.QWERTY,
            script = Script.LATIN,
            direction = TextDirection.LTR,
            hasAccents = true,
            dictionaryFile = "it_dict.db",
            correctionRules = "it_rules.json",
            flag = "🇮🇹",
            source = Source.LOCAL
        ),
        "ja" to LanguageConfig(
            code = "ja",
            name = "Japanese",
            nativeName = "日本語",
            layoutType = LayoutType.CUSTOM,
            script = Script.LATIN, // For romaji input
            direction = TextDirection.LTR,
            hasAccents = false,
            dictionaryFile = "ja_dict.db",
            correctionRules = "ja_rules.json",
            flag = "🇯🇵",
            source = Source.LOCAL
        )
    )
    
    // Mutable map that combines local and remote languages
    val SUPPORTED_LANGUAGES: MutableMap<String, LanguageConfig> = LOCAL_LANGUAGES.toMutableMap()
    
    /**
     * Merge remote languages from Firebase into supported languages
     * This allows dynamic addition of 40+ languages without app update
     */
    @Synchronized
    fun mergeRemoteLanguages(remote: Map<String, LanguageConfig>) {
        SUPPORTED_LANGUAGES.putAll(remote)
        android.util.Log.d("LanguageConfigs", "✅ Merged ${remote.size} remote languages. Total: ${SUPPORTED_LANGUAGES.size}")
    }
    
    /**
     * Get language config by code
     */
    fun getLanguageConfig(code: String): LanguageConfig? {
        return SUPPORTED_LANGUAGES[code]
    }
    
    /**
     * Get all enabled language codes
     */
    fun getEnabledLanguages(enabledCodes: Set<String>): List<LanguageConfig> {
        return enabledCodes.mapNotNull { SUPPORTED_LANGUAGES[it] }
    }
    
    /**
     * Get languages by layout type
     */
    fun getLanguagesByLayout(layoutType: LayoutType): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.layoutType == layoutType }
    }
    
    /**
     * Get languages by script
     */
    fun getLanguagesByScript(script: Script): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.script == script }
    }
    
    /**
     * Get local (bundled) languages
     */
    fun getLocalLanguages(): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.source == Source.LOCAL }
    }
    
    /**
     * Get remote (downloadable) languages
     */
    fun getRemoteLanguages(): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.source == Source.REMOTE }
    }
    
    /**
     * Check if language is bundled locally
     */
    fun isLocalLanguage(code: String): Boolean {
        return LOCAL_LANGUAGES.containsKey(code)
    }
}
