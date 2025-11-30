package com.kvive.keyboard

/**
 * Language configuration model for keyboard layouts
 * Provides metadata about supported languages including:
 * - Display name and native name
 * - Flag emoji
 * - Text direction (LTR/RTL)
 * - Script type (Latin, Devanagari, Arabic, etc.)
 * - Layout type (QWERTY, AZERTY, etc.)
 */
data class LanguageConfig(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
    val direction: TextDirection = TextDirection.LTR,
    val script: Script = Script.LATIN,
    val layoutType: LayoutType = LayoutType.QWERTY,
    val hasTransliteration: Boolean = false
)

/**
 * Text direction for keyboard layouts
 */
enum class TextDirection {
    LTR,  // Left-to-Right (English, Spanish, etc.)
    RTL   // Right-to-Left (Arabic, Hebrew, etc.)
}

/**
 * Script types for different writing systems
 */
enum class Script {
    LATIN,       // English, Spanish, French, etc.
    CYRILLIC,    // Russian, Ukrainian, etc.
    ARABIC,      // Arabic, Persian, Urdu
    HEBREW,      // Hebrew
    DEVANAGARI,  // Hindi, Marathi, Sanskrit
    TELUGU,      // Telugu
    TAMIL,       // Tamil
    MALAYALAM,   // Malayalam
    KANNADA,     // Kannada
    BENGALI,     // Bengali
    GUJARATI,    // Gujarati
    PUNJABI,     // Punjabi (Gurmukhi)
    ODIA,        // Odia
    GREEK,       // Greek
    THAI,        // Thai
    KOREAN,      // Korean (Hangul)
    JAPANESE,    // Japanese (Hiragana/Katakana)
    CHINESE      // Chinese (Simplified/Traditional)
}

/**
 * Keyboard layout types
 */
enum class LayoutType {
    QWERTY,    // Standard QWERTY
    QWERTZ,    // German layout
    AZERTY,    // French layout
    PHONETIC,  // Phonetic mapping for non-Latin scripts
    NATIVE,    // Native script layout
    INSCRIPT   // Indian government standard layout
}

/**
 * Repository of supported language configurations
 */
object LanguageConfigs {
    
    /**
     * Map of all supported languages with their configurations
     */
    val SUPPORTED_LANGUAGES: Map<String, LanguageConfig> = mapOf(
        // Latin script languages
        "en" to LanguageConfig("en", "English", "English", "🇺🇸"),
        "es" to LanguageConfig("es", "Spanish", "Español", "🇪🇸"),
        "fr" to LanguageConfig("fr", "French", "Français", "🇫🇷", layoutType = LayoutType.AZERTY),
        "de" to LanguageConfig("de", "German", "Deutsch", "🇩🇪", layoutType = LayoutType.QWERTZ),
        "it" to LanguageConfig("it", "Italian", "Italiano", "🇮🇹"),
        "pt" to LanguageConfig("pt", "Portuguese", "Português", "🇵🇹"),
        "nl" to LanguageConfig("nl", "Dutch", "Nederlands", "🇳🇱"),
        "pl" to LanguageConfig("pl", "Polish", "Polski", "🇵🇱"),
        "tr" to LanguageConfig("tr", "Turkish", "Türkçe", "🇹🇷"),
        "vi" to LanguageConfig("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        "id" to LanguageConfig("id", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        "ms" to LanguageConfig("ms", "Malay", "Bahasa Melayu", "🇲🇾"),
        "fil" to LanguageConfig("fil", "Filipino", "Filipino", "🇵🇭"),
        "sw" to LanguageConfig("sw", "Swahili", "Kiswahili", "🇰🇪"),
        
        // Cyrillic script languages
        "ru" to LanguageConfig("ru", "Russian", "Русский", "🇷🇺", script = Script.CYRILLIC),
        "uk" to LanguageConfig("uk", "Ukrainian", "Українська", "🇺🇦", script = Script.CYRILLIC),
        
        // RTL languages
        "ar" to LanguageConfig("ar", "Arabic", "العربية", "🇸🇦", TextDirection.RTL, Script.ARABIC),
        "he" to LanguageConfig("he", "Hebrew", "עברית", "🇮🇱", TextDirection.RTL, Script.HEBREW),
        "fa" to LanguageConfig("fa", "Persian", "فارسی", "🇮🇷", TextDirection.RTL, Script.ARABIC),
        "ur" to LanguageConfig("ur", "Urdu", "اردو", "🇵🇰", TextDirection.RTL, Script.ARABIC),
        
        // Indian languages (Indic scripts)
        "hi" to LanguageConfig("hi", "Hindi", "हिन्दी", "🇮🇳", script = Script.DEVANAGARI, hasTransliteration = true),
        "te" to LanguageConfig("te", "Telugu", "తెలుగు", "🇮🇳", script = Script.TELUGU, hasTransliteration = true),
        "ta" to LanguageConfig("ta", "Tamil", "தமிழ்", "🇮🇳", script = Script.TAMIL, hasTransliteration = true),
        "ml" to LanguageConfig("ml", "Malayalam", "മലയാളം", "🇮🇳", script = Script.MALAYALAM, hasTransliteration = true),
        "kn" to LanguageConfig("kn", "Kannada", "ಕನ್ನಡ", "🇮🇳", script = Script.KANNADA, hasTransliteration = true),
        "bn" to LanguageConfig("bn", "Bengali", "বাংলা", "🇮🇳", script = Script.BENGALI, hasTransliteration = true),
        "gu" to LanguageConfig("gu", "Gujarati", "ગુજરાતી", "🇮🇳", script = Script.GUJARATI, hasTransliteration = true),
        "pa" to LanguageConfig("pa", "Punjabi", "ਪੰਜਾਬੀ", "🇮🇳", script = Script.PUNJABI, hasTransliteration = true),
        "mr" to LanguageConfig("mr", "Marathi", "मराठी", "🇮🇳", script = Script.DEVANAGARI, hasTransliteration = true),
        "or" to LanguageConfig("or", "Odia", "ଓଡ଼ିଆ", "🇮🇳", script = Script.ODIA, hasTransliteration = true),
        
        // East Asian languages
        "zh" to LanguageConfig("zh", "Chinese", "中文", "🇨🇳", script = Script.CHINESE),
        "ja" to LanguageConfig("ja", "Japanese", "日本語", "🇯🇵", script = Script.JAPANESE),
        "ko" to LanguageConfig("ko", "Korean", "한국어", "🇰🇷", script = Script.KOREAN),
        
        // Other scripts
        "th" to LanguageConfig("th", "Thai", "ไทย", "🇹🇭", script = Script.THAI),
        "el" to LanguageConfig("el", "Greek", "Ελληνικά", "🇬🇷", script = Script.GREEK)
    )
    
    /**
     * Get configuration for a specific language
     */
    fun getLanguageConfig(languageCode: String): LanguageConfig? {
        return SUPPORTED_LANGUAGES[languageCode.lowercase()]
    }
    
    /**
     * Get configurations for a set of enabled languages
     */
    fun getEnabledLanguages(enabledCodes: Set<String>): List<LanguageConfig> {
        return enabledCodes.mapNotNull { getLanguageConfig(it) }
    }
    
    /**
     * Check if a language is supported
     */
    fun isSupported(languageCode: String): Boolean {
        return SUPPORTED_LANGUAGES.containsKey(languageCode.lowercase())
    }
    
    /**
     * Get all supported language codes
     */
    fun getAllSupportedCodes(): Set<String> {
        return SUPPORTED_LANGUAGES.keys
    }
    
    /**
     * Get languages by script type
     */
    fun getLanguagesByScript(script: Script): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.script == script }
    }
    
    /**
     * Get RTL languages
     */
    fun getRTLLanguages(): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.direction == TextDirection.RTL }
    }
    
    /**
     * Get languages with transliteration support
     */
    fun getTransliterationLanguages(): List<LanguageConfig> {
        return SUPPORTED_LANGUAGES.values.filter { it.hasTransliteration }
    }
}

