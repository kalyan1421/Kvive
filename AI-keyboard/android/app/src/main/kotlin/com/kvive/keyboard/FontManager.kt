package com.kvive.keyboard

import android.content.res.AssetManager
import android.graphics.Typeface
import android.util.Log
import com.kvive.keyboard.utils.LogUtil

/**
 * FontManager - Custom Noto font loader for Indic scripts
 * 
 * Phase 2: Runtime font application
 */
object FontManager {
    private const val TAG = "FontManager"
    
    private val fontCache = mutableMapOf<String, Typeface?>()
    
    /**
     * Get typeface for a language
     * Returns cached typeface or loads from assets
     * Returns null if font not found (fallback to system)
     */
    fun getTypefaceFor(language: String, assets: AssetManager): Typeface? {
        // Check cache first
        if (fontCache.containsKey(language)) {
            return fontCache[language]
        }
        
        val fontPath = when (language) {
            "hi" -> "fonts/NotoSansDevanagari-Regular.ttf"
            "te" -> "fonts/NotoSansTelugu-Regular.ttf"
            "ta" -> "fonts/NotoSansTamil-Regular.ttf"
            "bn" -> "fonts/NotoSansBengali-Regular.ttf"
            "gu" -> "fonts/NotoSansGujarati-Regular.ttf"
            "kn" -> "fonts/NotoSansKannada-Regular.ttf"
            "ml" -> "fonts/NotoSansMalayalam-Regular.ttf"
            "pa" -> "fonts/NotoSansGurmukhi-Regular.ttf"
            else -> null
        }
        
        val typeface = if (fontPath != null) {
            try {
                val tf = Typeface.createFromAsset(assets, fontPath)
                LogUtil.d(TAG, "✅ Loaded font for $language: $fontPath")
                tf
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Font not found for $language: $fontPath, using system font", e)
                null
            }
        } else {
            LogUtil.d(TAG, "Using default system font for $language")
            null
        }
        
        // Cache result (even if null)
        fontCache[language] = typeface
        return typeface
    }
    
    /**
     * Get bold typeface variant
     */
    fun getBoldTypefaceFor(language: String, assets: AssetManager): Typeface? {
        val fontPath = when (language) {
            "hi" -> "fonts/NotoSansDevanagari-Bold.ttf"
            "te" -> "fonts/NotoSansTelugu-Bold.ttf"
            "ta" -> "fonts/NotoSansTamil-Bold.ttf"
            else -> null
        }
        
        return if (fontPath != null) {
            try {
                Typeface.createFromAsset(assets, fontPath)
            } catch (e: Exception) {
                LogUtil.w(TAG, "⚠️ Bold font not found for $language", e)
                // Fallback to regular + fake bold
                getTypefaceFor(language, assets)?.let {
                    Typeface.create(it, Typeface.BOLD)
                }
            }
        } else {
            null
        }
    }
    
    /**
     * Clear font cache
     */
    fun clearCache() {
        fontCache.clear()
        LogUtil.d(TAG, "Font cache cleared")
    }
    
    /**
     * Check if custom font is available for language
     */
    fun hasCustomFont(language: String): Boolean {
        return language in listOf("hi", "te", "ta", "bn", "gu", "kn", "ml", "pa")
    }
}

