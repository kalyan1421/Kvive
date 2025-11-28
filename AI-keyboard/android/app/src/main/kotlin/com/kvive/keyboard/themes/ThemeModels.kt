package com.kvive.keyboard.themes

import android.graphics.*
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject

private fun formatColor(color: Int): String {
    val unsigned = color.toLong() and 0xFFFFFFFFL
    return String.format("#%08X", unsigned)
}

/**
 * CleverType Theme Engine V2 - Core Models
 * Single source of truth for all keyboard theming
 * Replaces old ThemeManager with centralized JSON-based system
 */

data class KeyboardThemeV2(
    val id: String,
    val name: String,
    val mode: String, // "unified" or "split"
    val background: Background,
    val keys: Keys,
    val specialKeys: SpecialKeys,
    val effects: Effects,
    val sounds: Sounds,
    val stickers: Stickers,
    val advanced: Advanced
) {
    data class Background(
        val type: String, // "solid", "image", "gradient", "adaptive"
        val color: Int?,
        val imagePath: String?,
        val imageOpacity: Float,
        val gradient: Gradient?,
        val overlayEffects: List<String>,
        val adaptive: Adaptive?,
        val brightness: Float
    ) {
        data class Gradient(
            val colors: List<Int>,
            val orientation: String, // "TOP_BOTTOM", "TL_BR", etc.
            val stops: List<Float>? = null
        )
        
        data class Adaptive(
            val enabled: Boolean,
            val source: String, // "wallpaper", "system", "app"
            val materialYou: Boolean
        )
    }
    
    data class Keys(
        val preset: String, // "flat", "bordered", "floating", "3d", "transparent"
        val bg: Int,
        val text: Int, 
        val pressed: Int,
        val rippleAlpha: Float,
        val border: Border,
        val radius: Float,
        val shadow: Shadow,
        val font: Font,
        val styleId: String = "default",
        val gradient: List<Int> = emptyList(),
        val overlayIcon: String? = null,
        val overlayIconColor: Int? = null,
        val overlayIconTargets: List<String> = emptyList(),
        val perKeyCustomization: Map<String, KeyCustomization> = emptyMap() // Per-key customization by key identifier
    ) {
        data class Border(
            val enabled: Boolean,
            val color: Int,
            val widthDp: Float
        )
        
        data class Shadow(
            val enabled: Boolean,
            val elevationDp: Float,
            val glow: Boolean
        )
        
        data class Font(
            val family: String,
            val sizeSp: Float,
            val bold: Boolean,
            val italic: Boolean
        )
        
        /**
         * Per-key customization for individual keys
         * Allows setting custom fonts and button styles for specific keys
         */
        data class KeyCustomization(
            val font: Font? = null,          // Custom font for this key
            val bg: Int? = null,             // Custom background color
            val text: Int? = null,           // Custom text color
            val pressed: Int? = null,        // Custom pressed color
            val border: Border? = null,      // Custom border
            val radius: Float? = null,       // Custom radius
            val shadow: Shadow? = null       // Custom shadow
        )
    }

    data class SpecialKeys(
        val accent: Int,
        val useAccentForEnter: Boolean,
        val applyTo: List<String>, // ["enter", "shift", "globe", "emoji", "mic", etc.]
        val spaceLabelColor: Int
    )

    data class Effects(
        val pressAnimation: String, // "ripple", "bounce", "glow", "none"
        val globalEffects: List<String>, // "snow", "hearts", "sparkles", "rain", "leaves"
        val opacity: Float = 1.0f
    )

    data class Sounds(
        val pack: String, // "classic", "soft", "mechanical", "clicky", "silent", "custom"
        val customUris: Map<String, String>,
        val volume: Float
    )

    data class Stickers(
        val enabled: Boolean,
        val pack: String,
        val position: String, // "above", "below", "behind"
        val opacity: Float,
        val animated: Boolean
    )

    data class Advanced(
        val livePreview: Boolean,
        val galleryEnabled: Boolean,
        val shareEnabled: Boolean,
        val dynamicTheme: String, // "none", "wallpaper", "time_of_day", "seasonal"
        val seasonalPack: String, // "none", "valentine", "halloween", "christmas", "spring", "summer"
        val materialYouExtract: Boolean
    )

    companion object {
        /**
         * Parse theme from JSON with comprehensive defaults
         */
        fun fromJson(json: String): KeyboardThemeV2 {
            return try {
                val obj = JSONObject(json)
                parseFromJsonObject(obj)
            } catch (e: Exception) {
                createDefault()
            }
        }

        fun parseFromJsonObject(obj: JSONObject): KeyboardThemeV2 {
            return KeyboardThemeV2(
                id = obj.optString("id", "default_theme"),
                name = obj.optString("name", "Default Theme"),
                mode = obj.optString("mode", "unified"),
                background = parseBackground(obj.optJSONObject("background")),
                keys = parseKeys(obj.optJSONObject("keys")),
                specialKeys = parseSpecialKeys(obj.optJSONObject("specialKeys")),
                effects = parseEffects(obj.optJSONObject("effects")),
                sounds = parseSounds(obj.optJSONObject("sounds")),
                stickers = parseStickers(obj.optJSONObject("stickers")),
                advanced = parseAdvanced(obj.optJSONObject("advanced"))
            )
        }

        private fun parseBackground(obj: JSONObject?): Background {
            if (obj == null) return Background(
                type = "solid",
                color = Color.parseColor("#1B1B1F"),
                imagePath = null,
                imageOpacity = 0.85f,
                gradient = null,
                overlayEffects = emptyList(),
                adaptive = null,
                brightness = 1.0f
            )
            
            val gradient = obj.optJSONObject("gradient")?.let {
                Background.Gradient(
                    colors = parseColorArray(it.optJSONArray("colors")) ?: listOf(
                        Color.parseColor("#2B2B2B"),
                        Color.parseColor("#1B1B1F")
                    ),
                    orientation = it.optString("orientation", "TOP_BOTTOM"),
                    stops = parseFloatArray(it.optJSONArray("stops"))
                )
            }
            
            val adaptive = obj.optJSONObject("adaptive")?.let {
                Background.Adaptive(
                    enabled = it.optBoolean("enabled", false),
                    source = it.optString("source", "wallpaper"),
                    materialYou = it.optBoolean("materialYou", false)
                )
            }
            
            return Background(
                type = obj.optString("type", "solid"),
                color = parseColor(obj.optString("color", "#1B1B1F")),
                imagePath = obj.optString("imagePath", "").takeIf { it.isNotEmpty() },
                imageOpacity = obj.optDouble("imageOpacity", 0.85).toFloat(),
                gradient = gradient,
                overlayEffects = parseStringArray(obj.optJSONArray("overlayEffects")),
                adaptive = adaptive,
                brightness = obj.optDouble("brightness", 1.0).toFloat().coerceIn(0.2f, 2.0f)
            )
        }

        private fun parseKeys(obj: JSONObject?): Keys {
            if (obj == null) return createDefaultKeys()
            
            return Keys(
                preset = obj.optString("preset", "bordered"),
                bg = parseColor(obj.optString("bg", "#3A3A3F")),
                text = parseColor(obj.optString("text", "#FFFFFF")),
                pressed = parseColor(obj.optString("pressed", "#505056")),
                rippleAlpha = obj.optDouble("rippleAlpha", 0.12).toFloat(),
                border = parseBorder(obj.optJSONObject("border")),
                radius = obj.optDouble("radius", 12.0).toFloat(),
                shadow = parseShadow(obj.optJSONObject("shadow")),
                font = parseFont(obj.optJSONObject("font")),
                styleId = obj.optString("styleId", "default"),
                gradient = parseColorArray(obj.optJSONArray("gradient")) ?: emptyList(),
                overlayIcon = obj.optString("overlayIcon", "").takeIf { it.isNotEmpty() },
                overlayIconColor = obj.optString("overlayIconColor", "").takeIf { it.isNotEmpty() }?.let { parseColor(it) },
                overlayIconTargets = parseStringArray(obj.optJSONArray("overlayIconTargets")),
                perKeyCustomization = parsePerKeyCustomization(obj.optJSONObject("perKeyCustomization"))
            )
        }
        
        private fun parsePerKeyCustomization(obj: JSONObject?): Map<String, Keys.KeyCustomization> {
            if (obj == null) return emptyMap()
            
            val customizations = mutableMapOf<String, Keys.KeyCustomization>()
            obj.keys().forEach { key ->
                val customObj = obj.optJSONObject(key)
                if (customObj != null) {
                    customizations[key] = Keys.KeyCustomization(
                        font = customObj.optJSONObject("font")?.let { parseFont(it) },
                        bg = customObj.optString("bg", null)?.let { parseColor(it) },
                        text = customObj.optString("text", null)?.let { parseColor(it) },
                        pressed = customObj.optString("pressed", null)?.let { parseColor(it) },
                        border = customObj.optJSONObject("border")?.let { parseBorder(it) },
                        radius = if (customObj.has("radius")) customObj.optDouble("radius").toFloat() else null,
                        shadow = customObj.optJSONObject("shadow")?.let { parseShadow(it) }
                    )
                }
            }
            return customizations
        }

        private fun parseBorder(obj: JSONObject?): Keys.Border {
            if (obj == null) return Keys.Border(
                enabled = true,
                color = Color.parseColor("#636366"),
                widthDp = 1.0f
            )
            
            return Keys.Border(
                enabled = obj.optBoolean("enabled", true),
                color = parseColor(obj.optString("color", "#636366")),
                widthDp = obj.optDouble("widthDp", 1.0).toFloat()
            )
        }

        private fun parseShadow(obj: JSONObject?): Keys.Shadow {
            if (obj == null) return Keys.Shadow(
                enabled = true,
                elevationDp = 2.0f,
                glow = false
            )
            
            return Keys.Shadow(
                enabled = obj.optBoolean("enabled", true),
                elevationDp = obj.optDouble("elevationDp", 2.0).toFloat(),
                glow = obj.optBoolean("glow", false)
            )
        }

        private fun parseFont(obj: JSONObject?): Keys.Font {
            if (obj == null) return Keys.Font(
                family = "Roboto",
                sizeSp = 24.0f,
                bold = true,
                italic = false
            )
            
            return Keys.Font(
                family = obj.optString("family", "Roboto"),
                sizeSp = obj.optDouble("sizeSp", 24.0).toFloat(),
                bold = obj.optBoolean("bold", true),
                italic = obj.optBoolean("italic", false)
            )
        }

        private fun parseSpecialKeys(obj: JSONObject?): SpecialKeys {
            val requiredKeys = listOf("enter", "globe", "emoji", "mic", "symbols", "backspace")
            
            if (obj == null) {
                return SpecialKeys(
                    accent = Color.parseColor("#FF9F1A"),
                    useAccentForEnter = true,
                    applyTo = requiredKeys,
                    spaceLabelColor = Color.parseColor("#FFFFFF")
                )
            }
            
            val parsedApplyTo = parseStringArray(obj.optJSONArray("applyTo"))?.toMutableList() ?: mutableListOf()
            requiredKeys.forEach { key ->
                if (!parsedApplyTo.contains(key)) {
                    parsedApplyTo.add(key)
                }
            }
            if (parsedApplyTo.isEmpty()) {
                parsedApplyTo.addAll(requiredKeys)
            }
            
            return SpecialKeys(
                accent = parseColor(obj.optString("accent", "#FF9F1A")),
                useAccentForEnter = obj.optBoolean("useAccentForEnter", true),
                applyTo = parsedApplyTo.toList(),
                spaceLabelColor = parseColor(obj.optString("spaceLabelColor", "#FFFFFF"))
            )
        }

        private fun parseEffects(obj: JSONObject?): Effects {
            return Effects(
                pressAnimation = obj?.optString("pressAnimation", "none") ?: "none",
                globalEffects = parseStringArray(obj?.optJSONArray("globalEffects")) ?: emptyList(),
                opacity = (obj?.optDouble("opacity", 1.0)?.toFloat() ?: 1.0f).coerceIn(0f, 1f)
            )
        }

        private fun parseSounds(obj: JSONObject?): Sounds {
            if (obj == null) return Sounds(
                pack = "silent",
                customUris = emptyMap(),
                volume = 0f
            )
            
            val customUris = mutableMapOf<String, String>()
            obj.optJSONObject("customUris")?.let { uris ->
                uris.keys().forEach { key ->
                    customUris[key] = uris.optString(key)
                }
            }
            
            return Sounds(
                pack = obj.optString("pack", "silent"),
                customUris = customUris,
                volume = obj.optDouble("volume", 0.0).toFloat().coerceIn(0f, 1f)
            )
        }

        private fun parseStickers(obj: JSONObject?): Stickers {
            if (obj == null) return Stickers(
                enabled = false,
                pack = "",
                position = "behind",
                opacity = 0.9f,
                animated = false
            )
            
            return Stickers(
                enabled = obj.optBoolean("enabled", false),
                pack = obj.optString("pack", ""),
                position = obj.optString("position", "behind"),
                opacity = obj.optDouble("opacity", 0.9).toFloat(),
                animated = obj.optBoolean("animated", false)
            )
        }

        private fun parseAdvanced(obj: JSONObject?): Advanced {
            if (obj == null) return Advanced(
                livePreview = true,
                galleryEnabled = true,
                shareEnabled = true,
                dynamicTheme = "none",
                seasonalPack = "none",
                materialYouExtract = false
            )
            
            return Advanced(
                livePreview = obj.optBoolean("livePreview", true),
                galleryEnabled = obj.optBoolean("galleryEnabled", true),
                shareEnabled = obj.optBoolean("shareEnabled", true),
                dynamicTheme = obj.optString("dynamicTheme", "none"),
                seasonalPack = obj.optString("seasonalPack", "none"),
                materialYouExtract = obj.optBoolean("materialYouExtract", false)
            )
        }

        // Utility functions
        private fun parseColor(colorStr: String?): Int {
            return try {
                if (colorStr.isNullOrEmpty()) Color.TRANSPARENT
                else Color.parseColor(colorStr)
            } catch (e: Exception) {
                Color.TRANSPARENT
            }
        }

        private fun parseColorArray(array: JSONArray?): List<Int>? {
            if (array == null) return null
            val colors = mutableListOf<Int>()
            for (i in 0 until array.length()) {
                colors.add(parseColor(array.optString(i)))
            }
            return colors.takeIf { it.isNotEmpty() }
        }

        private fun parseFloatArray(array: JSONArray?): List<Float>? {
            if (array == null) return null
            val positions = mutableListOf<Float>()
            for (i in 0 until array.length()) {
                val value = array.optDouble(i, Double.NaN)
                if (!value.isNaN()) {
                    positions.add(value.toFloat())
                }
            }
            return positions.takeIf { it.isNotEmpty() }
        }

        private fun parseStringArray(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val strings = mutableListOf<String>()
            for (i in 0 until array.length()) {
                strings.add(array.optString(i))
            }
            return strings
        }

        // Default theme creators
        fun createDefault(): KeyboardThemeV2 {
            return KeyboardThemeV2(
                id = "default_theme",
                name = "Default Light",
                mode = "unified",
                background = Background(
                    type = "solid",
                    color = Color.parseColor("#FFFFFFFF"),
                    imagePath = null,
                    imageOpacity = 1.0f,
                    gradient = null,
                    overlayEffects = emptyList(),
                    adaptive = null,
                    brightness = 1.0f
                ),
                keys = createDefaultKeys(),
                specialKeys = SpecialKeys(
                    accent = Color.parseColor("#FF007AFF"),
                    useAccentForEnter = true,
                    applyTo = listOf("enter", "globe", "emoji", "mic", "symbols", "backspace"),
                    spaceLabelColor = Color.parseColor("#FF000000")
                ),
                effects = Effects(
                    pressAnimation = "ripple",
                    globalEffects = emptyList()
                ),
                sounds = Sounds(
                    pack = "default",
                    customUris = emptyMap(),
                    volume = 0.5f
                ),
                stickers = Stickers(
                    enabled = false,
                    pack = "",
                    position = "behind",
                    opacity = 0.9f,
                    animated = false
                ),
                advanced = Advanced(
                    livePreview = true,
                    galleryEnabled = true,
                    shareEnabled = true,
                    dynamicTheme = "none",
                    seasonalPack = "none",
                    materialYouExtract = false
                )
            )
        }

        private fun createDefaultKeys(): Keys {
            return Keys(
                preset = "rounded",
                bg = Color.parseColor("#FFF2F2F2"),
                text = Color.parseColor("#FF000000"),
                pressed = Color.parseColor("#FFDDDDDD"),
                rippleAlpha = 0.12f,
                border = Keys.Border(
                    enabled = false,
                    color = Color.parseColor("#FF000000"),
                    widthDp = 0f
                ),
                radius = 12.0f,
                shadow = Keys.Shadow(
                    enabled = true,
                    elevationDp = 1.0f,
                    glow = false
                ),
                font = Keys.Font(
                    family = "Roboto",
                    sizeSp = 24.0f,
                    bold = true,
                    italic = false
                )
            )
        }
    }

    /**
     * Convert theme to JSON string
     */
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("mode", mode)
        
        // Background
        val bgObj = JSONObject()
        bgObj.put("type", background.type)
        background.color?.let { bgObj.put("color", formatColor(it)) }
        background.imagePath?.let { bgObj.put("imagePath", it) }
        bgObj.put("imageOpacity", background.imageOpacity)
        bgObj.put("brightness", background.brightness)
        background.gradient?.let { gradient ->
            val gradObj = JSONObject()
            val colorsArray = JSONArray()
            gradient.colors.forEach { color ->
                colorsArray.put(formatColor(color))
            }
            gradObj.put("colors", colorsArray)
            gradObj.put("orientation", gradient.orientation)
            gradient.stops?.let { stops ->
                val stopsArray = JSONArray()
                stops.forEach { stopsArray.put(it.toDouble()) }
                gradObj.put("stops", stopsArray)
            }
            bgObj.put("gradient", gradObj)
        }
        if (background.overlayEffects.isNotEmpty()) {
            val effectsArray = JSONArray()
            background.overlayEffects.forEach { effectsArray.put(it) }
            bgObj.put("overlayEffects", effectsArray)
        }
        background.adaptive?.let { adaptive ->
            val adaptiveObj = JSONObject()
            adaptiveObj.put("enabled", adaptive.enabled)
            adaptiveObj.put("source", adaptive.source)
            adaptiveObj.put("materialYou", adaptive.materialYou)
            bgObj.put("adaptive", adaptiveObj)
        }
        obj.put("background", bgObj)
        
        // Keys
        val keysObj = JSONObject()
        keysObj.put("preset", keys.preset)
        keysObj.put("bg", formatColor(keys.bg))
        keysObj.put("text", formatColor(keys.text))
        keysObj.put("pressed", formatColor(keys.pressed))
        keysObj.put("rippleAlpha", keys.rippleAlpha)
        keysObj.put("radius", keys.radius)
        keysObj.put("styleId", keys.styleId)
        if (keys.gradient.isNotEmpty()) {
            val gradientArray = JSONArray()
            keys.gradient.forEach { gradientArray.put(formatColor(it)) }
            keysObj.put("gradient", gradientArray)
        } else {
            keysObj.remove("gradient")
        }
        keys.overlayIcon?.let { keysObj.put("overlayIcon", it) }
        keys.overlayIconColor?.let { keysObj.put("overlayIconColor", formatColor(it)) }
        if (keys.overlayIconTargets.isNotEmpty()) {
            val targetsArray = JSONArray()
            keys.overlayIconTargets.forEach { targetsArray.put(it) }
            keysObj.put("overlayIconTargets", targetsArray)
        }
        
        // Keys border
        val borderObj = JSONObject()
        borderObj.put("enabled", keys.border.enabled)
        borderObj.put("color", formatColor(keys.border.color))
        borderObj.put("widthDp", keys.border.widthDp)
        keysObj.put("border", borderObj)
        
        // Keys shadow
        val shadowObj = JSONObject()
        shadowObj.put("enabled", keys.shadow.enabled)
        shadowObj.put("elevationDp", keys.shadow.elevationDp)
        shadowObj.put("glow", keys.shadow.glow)
        keysObj.put("shadow", shadowObj)
        
        // Keys font
        val fontObj = JSONObject()
        fontObj.put("family", keys.font.family)
        fontObj.put("sizeSp", keys.font.sizeSp)
        fontObj.put("bold", keys.font.bold)
        fontObj.put("italic", keys.font.italic)
        keysObj.put("font", fontObj)
        
        // Per-key customization
        if (keys.perKeyCustomization.isNotEmpty()) {
            val perKeyObj = JSONObject()
            keys.perKeyCustomization.forEach { (keyId, customization) ->
                val customObj = JSONObject()
                customization.font?.let { font ->
                    val customFontObj = JSONObject()
                    customFontObj.put("family", font.family)
                    customFontObj.put("sizeSp", font.sizeSp)
                    customFontObj.put("bold", font.bold)
                    customFontObj.put("italic", font.italic)
                    customObj.put("font", customFontObj)
                }
                customization.bg?.let { customObj.put("bg", formatColor(it)) }
                customization.text?.let { customObj.put("text", formatColor(it)) }
                customization.pressed?.let { customObj.put("pressed", formatColor(it)) }
                customization.border?.let { border ->
                    val customBorderObj = JSONObject()
                    customBorderObj.put("enabled", border.enabled)
                    customBorderObj.put("color", formatColor(border.color))
                    customBorderObj.put("widthDp", border.widthDp)
                    customObj.put("border", customBorderObj)
                }
                customization.radius?.let { customObj.put("radius", it) }
                customization.shadow?.let { shadow ->
                    val customShadowObj = JSONObject()
                    customShadowObj.put("enabled", shadow.enabled)
                    customShadowObj.put("elevationDp", shadow.elevationDp)
                    customShadowObj.put("glow", shadow.glow)
                    customObj.put("shadow", customShadowObj)
                }
                perKeyObj.put(keyId, customObj)
            }
            keysObj.put("perKeyCustomization", perKeyObj)
        }
        
        obj.put("keys", keysObj)
        
        // Special Keys
        val specialObj = JSONObject()
        specialObj.put("accent", formatColor(specialKeys.accent))
        specialObj.put("useAccentForEnter", specialKeys.useAccentForEnter)
        val applyToArray = JSONArray()
        specialKeys.applyTo.forEach { applyToArray.put(it) }
        specialObj.put("applyTo", applyToArray)
        specialObj.put("spaceLabelColor", formatColor(specialKeys.spaceLabelColor))
        obj.put("specialKeys", specialObj)
        
        // Effects
        val effectsObj = JSONObject()
        effectsObj.put("pressAnimation", effects.pressAnimation)
        if (effects.globalEffects.isNotEmpty()) {
            val globalEffectsArray = JSONArray()
            effects.globalEffects.forEach { globalEffectsArray.put(it) }
            effectsObj.put("globalEffects", globalEffectsArray)
        }
        effectsObj.put("opacity", effects.opacity)
        obj.put("effects", effectsObj)
        
        // Sounds
        val soundsObj = JSONObject()
        soundsObj.put("pack", sounds.pack)
        soundsObj.put("volume", sounds.volume)
        val customUrisObj = JSONObject()
        sounds.customUris.forEach { (key, value) ->
            customUrisObj.put(key, value)
        }
        soundsObj.put("customUris", customUrisObj)
        obj.put("sounds", soundsObj)
        
        // Stickers
        val stickersObj = JSONObject()
        stickersObj.put("enabled", stickers.enabled)
        stickersObj.put("pack", stickers.pack)
        stickersObj.put("position", stickers.position)
        stickersObj.put("opacity", stickers.opacity)
        stickersObj.put("animated", stickers.animated)
        obj.put("stickers", stickersObj)
        
        // Advanced
        val advancedObj = JSONObject()
        advancedObj.put("livePreview", advanced.livePreview)
        advancedObj.put("galleryEnabled", advanced.galleryEnabled)
        advancedObj.put("shareEnabled", advanced.shareEnabled)
        advancedObj.put("dynamicTheme", advanced.dynamicTheme)
        advancedObj.put("seasonalPack", advanced.seasonalPack)
        advancedObj.put("materialYouExtract", advanced.materialYouExtract)
        obj.put("advanced", advancedObj)
        
        return obj.toString(2)
    }
}

/**
 * Unified theme palette - derived colors for all UI elements
 * Single source of truth for runtime theming
 */
data class ThemePaletteV2(
    val theme: KeyboardThemeV2
) {
    // Resolved colors based on inheritance rules
    private val brightnessMultiplier: Float = theme.background.brightness.coerceIn(0.2f, 2.0f)
    val keyboardBg: Int = resolveKeyboardBackground()
    val usesImageBackground: Boolean = theme.background.type == "image" && !theme.background.imagePath.isNullOrEmpty()
    
    // Adaptive and seasonal features
    val isAdaptive: Boolean = theme.background.type == "adaptive" && theme.background.adaptive?.enabled == true
    val adaptiveSource: String = theme.background.adaptive?.source ?: "wallpaper"
    val isMaterialYou: Boolean = theme.background.adaptive?.materialYou == true
    val isSeasonalActive: Boolean = theme.advanced.seasonalPack != "none"
    val currentSeasonalPack: String = theme.advanced.seasonalPack
    val hasGlobalEffects: Boolean = theme.effects.globalEffects.isNotEmpty()
    val globalEffects: List<String> = theme.effects.globalEffects
    val globalEffectsOpacity: Float = theme.effects.opacity.coerceIn(0f, 1f)
    val hasStickers: Boolean = theme.stickers.enabled
    val stickerOpacity: Float = theme.stickers.opacity
    
    private fun resolveKeyboardBackground(): Int {
        val base = when (theme.background.type) {
            "adaptive" -> theme.background.color ?: Color.parseColor("#1B1B1F")
            else -> theme.background.color ?: Color.parseColor("#1B1B1F")
        }
        return applyBrightness(base)
    }
    
    // Keys - primary theme source
    val keyBg: Int = applyBrightness(theme.keys.bg)
    val keyText: Int = applyBrightness(theme.keys.text, inverse = true)
    val keyPressed: Int = applyBrightness(theme.keys.pressed)
    val keyBorder: Int = theme.keys.border.color
    val keyRadius: Float = theme.keys.radius.coerceIn(4f, 16f)
    val keyShadowEnabled: Boolean = theme.keys.shadow.enabled
    val isTransparentPreset: Boolean = theme.keys.preset.equals("transparent", ignoreCase = true)
    
    val specialAccent: Int = theme.specialKeys.accent
    val spaceLabelColor: Int = theme.specialKeys.spaceLabelColor
    
    // Toolbar & Suggestion Bar: Always match keyboard background (SIMPLIFIED)
    val toolbarBg: Int = keyboardBg
    val suggestionBg: Int = keyboardBg
    val panelSurface: Int = if (usesImageBackground) adjustForImageSurface() else keyboardBg
    
    // Toolbar icons: Use PNGs directly (no tint applied in code)
    val toolbarIcon: Int? = null  // null = no tint
    val toolbarHeight: Float = 64f // dp baseline
    val toolbarActiveAccent: Int = theme.specialKeys.accent

    // Suggestion text: Auto-contrast from background (SIMPLIFIED: no chips)
    val suggestionText: Int = getContrastColor(keyboardBg)
    
    // Font properties
    val keyFontSize: Float = theme.keys.font.sizeSp
    val keyFontFamily: String = theme.keys.font.family
    val keyFontBold: Boolean = theme.keys.font.bold
    val keyFontItalic: Boolean = theme.keys.font.italic
    
    val suggestionFontSize: Float = 14.0f // Fixed size for suggestions
    val suggestionFontFamily: String = theme.keys.font.family // Inherit from keys
    val suggestionFontBold: Boolean = false
    
    // Key styling properties - needed by ThemeManager
    val keyBorderEnabled: Boolean = theme.keys.border.enabled
    val keyBorderColor: Int = theme.keys.border.color
    val keyBorderWidth: Float = theme.keys.border.widthDp
    val keyShadowElevation: Float = theme.keys.shadow.elevationDp
    val keyShadowGlow: Boolean = theme.keys.shadow.glow
    val keyStyleId: String = theme.keys.styleId
    val keyGradientColors: List<Int> = theme.keys.gradient
    val keyOverlayIcon: String? = theme.keys.overlayIcon
    val keyOverlayIconColor: Int? = theme.keys.overlayIconColor
    val keyOverlayTargets: List<String> = theme.keys.overlayIconTargets
    
    // Effects
    val pressAnimation: String = theme.effects.pressAnimation
    val rippleAlpha: Float = theme.keys.rippleAlpha
    
    // Special key rules
    fun shouldApplyAccentTo(keyType: String): Boolean {
        return when (keyType) {
            "enter" -> theme.specialKeys.useAccentForEnter && theme.specialKeys.applyTo.contains(keyType)
            else -> theme.specialKeys.applyTo.contains(keyType)
        }
    }
    
    fun shouldUseAccentForEnter(): Boolean {
        return theme.specialKeys.useAccentForEnter
    }

    // Helper: Auto-contrast color (black or white based on background luminance)
    private fun getContrastColor(bgColor: Int): Int {
        val r = Color.red(bgColor)
        val g = Color.green(bgColor)
        val b = Color.blue(bgColor)
        
        // Calculate perceived luminance (ITU-R BT.709)
        val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
        
        // Return white for dark backgrounds, black for light backgrounds
        return if (luminance < 0.5) Color.WHITE else Color.BLACK
    }

    // Helper: Lighten or darken a color
    private fun lightenOrDarken(color: Int, delta: Float): Int {
        // delta > 0 → lighten, < 0 → darken
        // Used to create subtle variations from background color
        val a = Color.alpha(color)
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        fun adj(c: Int) = (c + (255 - c) * delta).coerceIn(0f, 255f).toInt()
        return Color.argb(a, adj(r), adj(g), adj(b))
    }

    private fun applyBrightness(color: Int, inverse: Boolean = false): Int {
        if (brightnessMultiplier == 1.0f) return color
        val factor = if (inverse) {
            (1f / brightnessMultiplier).coerceIn(0.2f, 5.0f)
        } else {
            brightnessMultiplier
        }
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] * factor).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun adjustForImageSurface(): Int {
        val base = Color.parseColor("#16171B")
        return applyBrightness(base)
    }
}
