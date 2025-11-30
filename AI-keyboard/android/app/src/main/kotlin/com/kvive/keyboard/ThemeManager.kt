package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.kvive.keyboard.managers.BaseManager
import com.kvive.keyboard.themes.KeyboardThemeV2
import com.kvive.keyboard.themes.ThemePaletteV2
import org.json.JSONObject
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import kotlin.math.*
import android.view.Gravity
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper

/**
 * CleverType Theme Engine V2 - Single Source of Truth
 * Replaces old theme system with centralized JSON-based theming
 * All colors, drawables, and styling come from KeyboardThemeV2
 * 
 * ⚡ PERFORMANCE: Async background image loading prevents UI thread blocking
 */
class ThemeManager(context: Context) : BaseManager(context) {
    
    companion object {
        private const val TAG = "ThemeManager"
        
        // CRITICAL: Flutter plugin adds "flutter." prefix automatically!
        // So we access "flutter.theme.v2.json" which matches Flutter's 'theme.v2.json' key
        private const val THEME_V2_KEY = "flutter.theme.v2.json"
        private const val SETTINGS_CHANGED_KEY = "flutter.keyboard_settings.settings_changed"
        
        // Cache sizes
        private const val DRAWABLE_CACHE_SIZE = 50
        private const val IMAGE_CACHE_SIZE = 10
    }
    
    override fun getPreferencesName() = "FlutterSharedPreferences"
    private var currentTheme: KeyboardThemeV2? = null
    private var currentPalette: ThemePaletteV2? = null
    private var themeHash: String = ""
    
    // LRU Caches for performance
    private val drawableCache = LruCache<String, Drawable>(DRAWABLE_CACHE_SIZE)
    private val imageCache = LruCache<String, Drawable>(IMAGE_CACHE_SIZE)
    
    // ⚡ PERFORMANCE: Coroutine scope for async image loading
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Track pending async loads to avoid duplicate work
    private val pendingImageLoads = mutableSetOf<String>()
    
    // Theme change listeners
    private val listeners = mutableListOf<ThemeChangeListener>()
    
    // SharedPreferences listener for automatic theme updates
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == THEME_V2_KEY || key == SETTINGS_CHANGED_KEY) {
            loadThemeFromPrefs()
            notifyThemeChanged()
        }
    }
    
    interface ThemeChangeListener {
        fun onThemeChanged(theme: KeyboardThemeV2, palette: ThemePaletteV2)
    }
    
    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadThemeFromPrefs()
    }
    
    fun addThemeChangeListener(listener: ThemeChangeListener) {
        listeners.add(listener)
    }
    
    fun removeThemeChangeListener(listener: ThemeChangeListener) {
        listeners.remove(listener)
    }
    
    private fun notifyThemeChanged() {
        val theme = currentTheme
        val palette = currentPalette
        if (theme != null && palette != null) {
            listeners.forEach { it.onThemeChanged(theme, palette) }
        }
    }
    
    /**
     * Load theme from SharedPreferences
     * Migrates old themes to V2 format automatically
     */
    private fun loadThemeFromPrefs() {
        // Always read a fresh snapshot; Flutter writes from another process (:app → :ime)
        val prefsSnapshot = try {
            context.getSharedPreferences(getPreferencesName(), Context.MODE_MULTI_PROCESS)
        } catch (_: Exception) {
            prefs
        }
        val themeJson = prefsSnapshot.getString(THEME_V2_KEY, null)
        var changed = false
        
        if (themeJson != null) {
            // Load V2 theme
            val theme = KeyboardThemeV2.fromJson(themeJson)
            val newHash = themeJson.hashCode().toString()
            
            // Only update if theme actually changed
            if (newHash != themeHash) {
                currentTheme = theme
                currentPalette = ThemePaletteV2(theme)
                themeHash = newHash
                
                android.util.Log.d(
                    "ThemeManager",
                    "Loaded theme: ${theme.name}, backgroundType=${theme.background.type}, preset=${theme.keys.preset}, usesImage=${currentPalette?.usesImageBackground}, keyBg=0x${Integer.toHexString(theme.keys.bg)}"
                )
                
                // Clear caches on theme change
                drawableCache.evictAll()
                imageCache.evictAll()
                changed = true
            }
        } else {
            // No V2 theme found - load default
            loadDefaultTheme()
            changed = true
        }

        if (changed) {
            notifyThemeChanged()
        }
    }
    
    // ✅ CLEANUP: migrateOldTheme() and createMigratedTheme() removed
    // Migration has been completed - new installs use V2 format directly
    
    private fun loadDefaultTheme() {
        val defaultTheme = KeyboardThemeV2.createDefault()
        saveTheme(defaultTheme)
    }
    
    /**
     * Save theme to SharedPreferences
     */
    fun saveTheme(theme: KeyboardThemeV2) {
        val json = theme.toJson()
        prefs.edit()
            .putString(THEME_V2_KEY, json)
            .putBoolean(SETTINGS_CHANGED_KEY, true)
            .apply()
    }

    fun applyThemeFromFlutter(themeMap: Map<String, Any?>, persist: Boolean = false): Boolean {
        return try {
            val jsonObject = JSONObject(themeMap)
            val theme = KeyboardThemeV2.parseFromJsonObject(jsonObject)
            currentTheme = theme
            currentPalette = ThemePaletteV2(theme)
            themeHash = jsonObject.toString().hashCode().toString()

            if (persist) {
                prefs.edit()
                    .putString(THEME_V2_KEY, jsonObject.toString())
                    .putBoolean(SETTINGS_CHANGED_KEY, true)
                    .apply()
            }

            drawableCache.evictAll()
            imageCache.evictAll()
            notifyThemeChanged()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme from Flutter", e)
            false
        }
    }
    
    /**
     * Get current theme (never null)
     */
    fun getCurrentTheme(): KeyboardThemeV2 {
        return currentTheme ?: KeyboardThemeV2.createDefault()
    }
    
    /**
     * Get current palette (derived colors)
     */
    fun getCurrentPalette(): ThemePaletteV2 {
        return currentPalette ?: ThemePaletteV2(KeyboardThemeV2.createDefault())
    }
    
    /**
     * Force reload theme from preferences
     */
    fun reload() {
        loadThemeFromPrefs()
    }
    
    // ===== DRAWABLE FACTORY METHODS =====
    
    /**
     * Create cached key background drawable
     */
    fun createKeyDrawable(): Drawable {
        val cacheKey = "key_${themeHash}"
        return drawableCache.get(cacheKey) ?: run {
            val drawable = buildKeyDrawable()
            drawableCache.put(cacheKey, drawable)
            drawable
        }
    }
    
    /**
     * Create key background drawable with per-key customization
     * @param keyIdentifier The key identifier (e.g., "a", "enter", "space", etc.)
     */
    fun createKeyDrawable(keyIdentifier: String): Drawable {
        val theme = getCurrentTheme()
        val customization = theme.keys.perKeyCustomization[keyIdentifier]
        
        // If no customization for this key, return default
        if (customization == null) {
            return createKeyDrawable()
        }
        
        val cacheKey = "key_${keyIdentifier}_${themeHash}"
        return drawableCache.get(cacheKey) ?: run {
            val drawable = buildKeyDrawable(customization)
            drawableCache.put(cacheKey, drawable)
            drawable
        }
    }
    
    /**
     * Create cached key pressed drawable
     */
    fun createKeyPressedDrawable(): Drawable {
        val cacheKey = "key_pressed_${themeHash}"
        return drawableCache.get(cacheKey) ?: run {
            val drawable = buildKeyPressedDrawable()
            drawableCache.put(cacheKey, drawable)
            drawable
        }
    }
    
    /**
     * Create cached special key drawable (with accent)
     */
    fun createSpecialKeyDrawable(): Drawable {
        val cacheKey = "special_key_${themeHash}"
        return drawableCache.get(cacheKey) ?: run {
            val drawable = buildSpecialKeyDrawable()
            drawableCache.put(cacheKey, drawable)
            drawable
        }
    }
    
    /**
     * Create cached toolbar background drawable
     */
    fun createToolbarBackground(): Drawable {
        val palette = getCurrentPalette()
        if (palette.usesImageBackground) {
            return ColorDrawable(Color.TRANSPARENT)
        }
        val cacheKey = "toolbar_bg_$themeHash"
        drawableCache.get(cacheKey)?.let { return it }
        val drawable = GradientDrawable().apply {
            setColor(palette.toolbarBg)
            cornerRadius = 4f // No corner radius for seamless connection
        }
        drawableCache.put(cacheKey, drawable)
        return drawable
    }

    fun createSuggestionBarBackground(): Drawable {
        val palette = getCurrentPalette()
        if (palette.usesImageBackground) {
            return ColorDrawable(Color.TRANSPARENT)
        }
        val cacheKey = "suggestion_bg_$themeHash"
        drawableCache.get(cacheKey)?.let { return it }
        val drawable = GradientDrawable().apply {
            setColor(palette.suggestionBg)
            cornerRadius = 0f // No corner radius for seamless connection
        }
        drawableCache.put(cacheKey, drawable)
        return drawable
    }
    
    /**
     * Create cached keyboard background drawable
     */
    fun createKeyboardBackground(): Drawable {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val cacheKey = "keyboard_bg_${theme.background.type}_${palette.isSeasonalActive}_${themeHash}"
        
        return drawableCache.get(cacheKey) ?: run {
            val drawable = when (theme.background.type) {
                "adaptive" -> buildAdaptiveBackground()
                "gradient" -> buildGradientBackground(theme)
                "image" -> buildImageBackground(theme)
                else -> buildSolidDrawable(palette.keyboardBg)
            }
            
            // Apply seasonal overlay if active
            val finalDrawable = if (palette.isSeasonalActive) {
                applySeasonalOverlay(drawable, palette.currentSeasonalPack)
            } else {
                drawable
            }
            
            drawableCache.put(cacheKey, finalDrawable)
            finalDrawable
        }
    }
    
    /**
     * Create sticker overlay drawable
     */
    fun createStickerOverlay(): Drawable? {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        
        if (!palette.hasStickers) return null
        
        val cacheKey = "sticker_${theme.stickers.pack}_${theme.stickers.position}_${themeHash}"
        
        return drawableCache.get(cacheKey) ?: run {
            val drawable = loadStickerDrawable(theme.stickers.pack)
            drawable?.let { 
                it.alpha = (palette.stickerOpacity * 255).toInt()
                drawableCache.put(cacheKey, it)
            }
            drawable
        }
    }

    // ===== PRIVATE DRAWABLE BUILDERS =====
    
    private fun buildKeyDrawable(): Drawable {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val preset = theme.keys.preset
        
        // If custom shape preset, use custom drawable
        if (preset in listOf("star", "heart", "hexagon", "cone", "gem", "slice")) {
            return createCustomShapeDrawable(preset, palette)
        }
        
        // Otherwise use standard GradientDrawable
        val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
        val density = context.resources.displayMetrics.density
        val radiusPx = palette.keyRadius * density
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val gradientColors = palette.keyGradientColors.takeIf { it.isNotEmpty() }?.toIntArray()
            if (gradientColors != null) {
                colors = gradientColors
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            } else {
                val fillColor = if (isTransparentStyle) Color.TRANSPARENT else palette.keyBg
                setColor(fillColor)
            }
            cornerRadius = if (preset == "square") 0f else radiusPx
        }
        
        val strokeWidth = (max(1f, palette.keyBorderWidth) * density).toInt().coerceAtLeast(1)
        if (isTransparentStyle) {
            val strokeColor = if (palette.keyBorderEnabled) {
                palette.keyBorderColor
            } else {
                ColorUtils.setAlphaComponent(palette.keyText, 170)
            }
            drawable.setStroke(strokeWidth, strokeColor)
        } else if (palette.keyBorderEnabled) {
            drawable.setStroke(strokeWidth, palette.keyBorderColor)
        }
        
        return drawable
    }
    
    /**
     * Build key drawable with per-key customization
     */
    private fun buildKeyDrawable(customization: com.kvive.keyboard.themes.KeyboardThemeV2.Keys.KeyCustomization): Drawable {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
        val density = context.resources.displayMetrics.density
        
        // Use custom radius if specified, otherwise use global
        val radius = customization.radius ?: palette.keyRadius
        val radiusPx = radius * density
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val gradientColors = if (customization.bg == null) {
                palette.keyGradientColors.takeIf { it.isNotEmpty() }?.toIntArray()
            } else null
            if (gradientColors != null) {
                colors = gradientColors
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            } else {
                val bgColor = customization.bg ?: (if (isTransparentStyle) Color.TRANSPARENT else palette.keyBg)
                setColor(bgColor)
            }
            cornerRadius = radiusPx
        }
        
        // Use custom border if specified, otherwise use global
        val borderEnabled = customization.border?.enabled ?: palette.keyBorderEnabled
        val borderColor = customization.border?.color ?: palette.keyBorderColor
        val borderWidth = customization.border?.widthDp ?: palette.keyBorderWidth
        
        val strokeWidth = (max(1f, borderWidth) * density).toInt().coerceAtLeast(1)
        if (isTransparentStyle) {
            val strokeColor = if (borderEnabled) {
                borderColor
            } else {
                ColorUtils.setAlphaComponent(palette.keyText, 170)
            }
            drawable.setStroke(strokeWidth, strokeColor)
        } else if (borderEnabled) {
            drawable.setStroke(strokeWidth, borderColor)
        }
        
        return drawable
    }
    
    private fun buildKeyPressedDrawable(): Drawable {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val preset = theme.keys.preset
        
        // If custom shape preset, use custom drawable
        if (preset in listOf("star", "heart", "hexagon", "cone", "gem", "slice")) {
            val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
            val bgColor = if (isTransparentStyle) {
                ColorUtils.setAlphaComponent(palette.specialAccent, 200)
            } else {
                palette.keyPressed
            }
            val borderColor = if (palette.keyBorderEnabled) {
                palette.keyBorderColor
            } else if (isTransparentStyle) {
                ColorUtils.setAlphaComponent(palette.keyText, 190)
            } else {
                Color.TRANSPARENT
            }
            val density = context.resources.displayMetrics.density
            val borderWidth = if (isTransparentStyle || palette.keyBorderEnabled) {
                (max(1f, palette.keyBorderWidth) * density)
            } else {
                0f
            }
            return CustomShapeDrawable(preset, bgColor, borderColor, borderWidth)
        }
        
        // Standard rounded rectangle
        val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
        val density = context.resources.displayMetrics.density
        val radiusPx = palette.keyRadius * density
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val baseGradient = palette.keyGradientColors.takeIf { it.isNotEmpty() }
            if (baseGradient != null) {
                val pressedGradient = baseGradient.map { adjustColorBrightness(it, -0.12f) }.toIntArray()
                colors = pressedGradient
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            } else {
                val pressedColor = if (isTransparentStyle) {
                    ColorUtils.setAlphaComponent(palette.specialAccent, 200)
                } else {
                    palette.keyPressed
                }
                setColor(pressedColor)
            }
            cornerRadius = if (preset == "square") 0f else radiusPx
        }
        
        val strokeWidth = (max(1f, palette.keyBorderWidth) * density).toInt().coerceAtLeast(1)
        if (isTransparentStyle) {
            val strokeColor = if (palette.keyBorderEnabled) {
                palette.keyBorderColor
            } else {
                ColorUtils.setAlphaComponent(palette.keyText, 190)
            }
            drawable.setStroke(strokeWidth, strokeColor)
        } else if (palette.keyBorderEnabled) {
            drawable.setStroke(strokeWidth, palette.keyBorderColor)
        }
        
        return drawable
    }
    
    private fun buildSpecialKeyDrawable(): Drawable {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val preset = theme.keys.preset
        
        // If custom shape preset, use custom drawable
        if (preset in listOf("star", "heart", "hexagon", "cone", "gem")) {
            val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
            val bgColor = if (isTransparentStyle) {
                ColorUtils.setAlphaComponent(palette.specialAccent, 220)
            } else {
                palette.specialAccent
            }
            val borderColor = if (palette.keyBorderEnabled) {
                palette.keyBorderColor
            } else if (isTransparentStyle) {
                ColorUtils.setAlphaComponent(palette.keyText, 170)
            } else {
                Color.TRANSPARENT
            }
            val density = context.resources.displayMetrics.density
            val borderWidth = if (isTransparentStyle || palette.keyBorderEnabled) {
                (max(1f, palette.keyBorderWidth) * density)
            } else {
                0f
            }
            return CustomShapeDrawable(preset, bgColor, borderColor, borderWidth)
        }
        
        // Standard rounded rectangle
        val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
        val density = context.resources.displayMetrics.density
        val radiusPx = palette.keyRadius * density
        
        val styleId = theme.keys.styleId
        val matchKeyPalette = styleId == "watermelon_slice"

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val gradientColors = palette.keyGradientColors.takeIf { it.isNotEmpty() }?.toIntArray()
            val baseColor = when {
                matchKeyPalette -> palette.keyBg
                isTransparentStyle -> ColorUtils.setAlphaComponent(palette.specialAccent, 220)
                else -> palette.specialAccent
            }

            if (gradientColors != null) {
                if (matchKeyPalette) {
                    colors = gradientColors
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                } else {
                    colors = gradientColors
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            } else {
                setColor(baseColor)
            }
            cornerRadius = if (preset == "square") 0f else radiusPx
        }
        
        val strokeWidth = (max(1f, palette.keyBorderWidth) * density).toInt().coerceAtLeast(1)
        if (isTransparentStyle) {
            val strokeColor = if (palette.keyBorderEnabled) {
                palette.keyBorderColor
            } else {
                ColorUtils.setAlphaComponent(palette.keyText, 170)
            }
            drawable.setStroke(strokeWidth, strokeColor)
        } else if (palette.keyBorderEnabled) {
            drawable.setStroke(strokeWidth, palette.keyBorderColor)
        }
        
        return drawable
    }
    
    private fun buildSolidDrawable(color: Int): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(color)
        return drawable
    }
    
    private fun buildGradientBackground(theme: com.kvive.keyboard.themes.KeyboardThemeV2): Drawable {
        val gradient = theme.background.gradient ?: return buildSolidDrawable(Color.BLACK)

        val orientation = when (gradient.orientation) {
            "TOP_BOTTOM" -> GradientDrawable.Orientation.TOP_BOTTOM
            "TL_BR" -> GradientDrawable.Orientation.TL_BR
            "TR_BL" -> GradientDrawable.Orientation.TR_BL
            "LEFT_RIGHT" -> GradientDrawable.Orientation.LEFT_RIGHT
            "BR_TL" -> GradientDrawable.Orientation.BR_TL
            "BL_TR" -> GradientDrawable.Orientation.BL_TR
            else -> GradientDrawable.Orientation.TOP_BOTTOM
        }

        val brightness = theme.background.brightness.coerceIn(0.2f, 2.0f)
        val adjustedColors = gradient.colors.map { applyBrightnessMultiplier(it, brightness) }
        val colorsArray = adjustedColors.toIntArray()

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.orientation = orientation
            val stops = gradient.stops
            if (stops != null && stops.isNotEmpty() && stops.size == colorsArray.size && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                setColors(colorsArray, stops.toFloatArray())
            } else {
                colors = colorsArray
            }
        }

        return drawable
    }

    private fun adjustColorBrightness(color: Int, delta: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun applyBrightnessMultiplier(color: Int, multiplier: Float): Int {
        if (multiplier == 1.0f) return color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = (hsl[2] * multiplier).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }
    
    /**
     * ⚡ PERFORMANCE: Async image background loading
     * Returns a placeholder immediately if image not cached, then loads asynchronously
     * and notifies listeners when the real image is ready
     */
    private fun buildImageBackground(theme: com.kvive.keyboard.themes.KeyboardThemeV2): Drawable {
        val imagePath = theme.background.imagePath

        if (imagePath.isNullOrEmpty()) {
            return buildSolidDrawable(applyBrightnessMultiplier(theme.background.color ?: Color.TRANSPARENT, theme.background.brightness))
        }
        
        val cacheKey = "bg_image_layer_$imagePath"
        
        // ⚡ PERFORMANCE: Return cached image immediately if available
        imageCache.get(cacheKey)?.let { return it }
        
        // Return placeholder immediately, load image asynchronously
        val placeholderColor = theme.background.color ?: getCurrentPalette().keyboardBg
        val placeholder = buildSolidDrawable(applyBrightnessMultiplier(placeholderColor, theme.background.brightness))
        
        // Start async load if not already pending
        if (!pendingImageLoads.contains(cacheKey)) {
            pendingImageLoads.add(cacheKey)
            asyncScope.launch {
                try {
                    val drawable = loadImageBackgroundAsync(theme, cacheKey)
                    mainHandler.post {
                        pendingImageLoads.remove(cacheKey)
                        if (drawable != null) {
                            imageCache.put(cacheKey, drawable)
                            Log.d(TAG, "⚡ Async image loaded, notifying listeners")
                            notifyThemeChanged() // Trigger view refresh with real image
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        pendingImageLoads.remove(cacheKey)
                        Log.e(TAG, "❌ Async image load failed: $imagePath", e)
                    }
                }
            }
        }
        
        return placeholder
    }
    
    /**
     * ⚡ PERFORMANCE: Load image background on IO thread
     * Called asynchronously to avoid blocking UI thread
     */
    private suspend fun loadImageBackgroundAsync(
        theme: com.kvive.keyboard.themes.KeyboardThemeV2,
        cacheKey: String
    ): Drawable? = withContext(Dispatchers.IO) {
        val imagePath = theme.background.imagePath ?: return@withContext null
        
        try {
            val originalBitmap = loadImageBitmap(imagePath)
            Log.d(TAG, "✅ Loaded image bitmap async: ${originalBitmap.width}x${originalBitmap.height}")
            
            // Create a scaled bitmap that maintains aspect ratio and fills the view
            val scaledBitmap = createScaledBitmapForKeyboard(originalBitmap)
            
            // Recycle original bitmap if we created a new scaled one
            if (scaledBitmap !== originalBitmap) {
                originalBitmap.recycle()
            }
            
            val brightness = theme.background.brightness.coerceIn(0.2f, 2.0f)
            val bitmapDrawable = BitmapDrawable(context.resources, scaledBitmap).apply {
                alpha = (theme.background.imageOpacity * 255).toInt().coerceIn(0, 255)
                isFilterBitmap = true
                setAntiAlias(true)
                gravity = Gravity.FILL
                tileModeX = Shader.TileMode.CLAMP
                tileModeY = Shader.TileMode.CLAMP
                if (brightness != 1.0f) {
                    colorFilter = ColorMatrixColorFilter(
                        ColorMatrix(
                            floatArrayOf(
                                brightness, 0f, 0f, 0f, 0f,
                                0f, brightness, 0f, 0f, 0f,
                                0f, 0f, brightness, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                }
            }

            val baseColor = when {
                theme.background.color == null -> Color.TRANSPARENT
                theme.background.color == Color.BLACK && theme.background.overlayEffects.isEmpty() -> Color.TRANSPARENT
                else -> applyBrightnessMultiplier(theme.background.color ?: Color.TRANSPARENT, brightness)
            }
            val layers = mutableListOf<Drawable>().apply {
                if (baseColor != Color.TRANSPARENT) {
                    add(ColorDrawable(baseColor))
                }
                add(bitmapDrawable)
                if (theme.background.overlayEffects.contains("darken")) {
                    add(
                        GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                ColorUtils.setAlphaComponent(Color.BLACK, 0),
                                ColorUtils.setAlphaComponent(Color.BLACK, 120)
                            )
                        )
                    )
                }
            }

            LayerDrawable(layers.toTypedArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load background image async: $imagePath", e)
            null
        }
    }
    
    private fun loadImageBitmap(path: String): Bitmap {
        // BitmapFactory options for high quality loading
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888  // High quality color
            inScaled = false  // Don't scale during decode
            inDither = false  // No dithering for better quality
            inPreferQualityOverSpeed = true  // Prioritize quality
        }

        return when {
            path.startsWith("http://") || path.startsWith("https://") -> {
                // Network URL - download and cache
                loadNetworkImage(path)
            }
            path.startsWith("file://") -> {
                // File URI - remove file:// prefix
                val filePath = path.substring(7)
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "❌ Image file not found: $filePath")
                    throw FileNotFoundException("Image not found: $filePath")
                }
                Log.d(TAG, "📷 Loading image from file: $filePath (size: ${file.length()} bytes)")
                BitmapFactory.decodeFile(filePath, options) ?: throw Exception("Failed to decode image")
            }
            path.startsWith("/") -> {
                // Absolute path
                val file = File(path)
                if (!file.exists()) {
                    Log.e(TAG, "❌ Image file not found: $path")
                    throw FileNotFoundException("Image not found: $path")
                }
                Log.d(TAG, "📷 Loading image from absolute path: $path (size: ${file.length()} bytes)")
                BitmapFactory.decodeFile(path, options) ?: throw Exception("Failed to decode image")
            }
            else -> {
                // Asset path
                Log.d(TAG, "📷 Loading image from assets: $path")
                val inputStream = context.assets.open(path)
                BitmapFactory.decodeStream(inputStream, null, options) ?: throw Exception("Failed to decode asset image")
            }
        }
    }
    
    /**
     * Scale bitmap to fill keyboard dimensions using CENTER_CROP logic
     * This prevents tiling/distortion issues with portrait or mismatched aspect ratio images
     */
    private fun createScaledBitmapForKeyboard(source: Bitmap): Bitmap {
        // Get display metrics for keyboard dimensions
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Keyboard typically occupies about 40-50% of screen height in portrait
        // and about 50-60% in landscape
        val targetWidth = screenWidth
        val targetHeight = (screenHeight * 0.45).toInt()
        
        Log.d(TAG, "🖼️ Scaling image from ${source.width}x${source.height} to fit ${targetWidth}x${targetHeight}")
        
        // If image already matches dimensions well, return as-is
        val widthRatio = source.width.toFloat() / targetWidth
        val heightRatio = source.height.toFloat() / targetHeight
        
        if (widthRatio in 0.9f..1.1f && heightRatio in 0.9f..1.1f) {
            Log.d(TAG, "✅ Image dimensions already optimal, no scaling needed")
            return source
        }
        
        // Calculate scale to fill the target area (CENTER_CROP behavior)
        val scale = maxOf(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height
        )
        
        val scaledWidth = (source.width * scale).toInt()
        val scaledHeight = (source.height * scale).toInt()
        
        // Create scaled bitmap with high quality
        val scaledBitmap = Bitmap.createScaledBitmap(
            source,
            scaledWidth,
            scaledHeight,
            true // Use bilinear filtering for quality
        )
        
        Log.d(TAG, "✅ Scaled bitmap to ${scaledBitmap.width}x${scaledBitmap.height}, scale=$scale")
        
        return scaledBitmap
    }
    
    private fun loadNetworkImage(url: String): Bitmap {
        // Check cache first
        val cacheKey = "net_${url.hashCode()}"
        val cacheDir = context.cacheDir
        val cacheFile = File(cacheDir, cacheKey)
        
        if (cacheFile.exists()) {
            return BitmapFactory.decodeFile(cacheFile.absolutePath)
        }
        
        // Download from network
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        val inputStream = connection.getInputStream()
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        
        // Cache to file
        try {
            val outputStream = FileOutputStream(cacheFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
        } catch (e: Exception) {
            // Ignore cache errors
        }
        
        return bitmap
    }
    
    // ===== RIPPLE DRAWABLE FACTORY =====
    
    /**
     * Create ripple drawable for key press effect
     */
    fun createKeyRippleDrawable(): RippleDrawable? {
        val palette = getCurrentPalette()
        
        if (palette.pressAnimation != "ripple") {
            return null
        }
        
        val isImageTheme = palette.usesImageBackground
        val rippleColor = ColorStateList.valueOf(
            if (isImageTheme) {
                ColorUtils.setAlphaComponent(palette.specialAccent, (palette.rippleAlpha * 255).toInt())
            } else {
                Color.argb(
                    (palette.rippleAlpha * 255).toInt(),
                    Color.red(palette.keyText),
                    Color.green(palette.keyText),
                    Color.blue(palette.keyText)
                )
            }
        )
        
        val mask = GradientDrawable()
        mask.shape = GradientDrawable.RECTANGLE
        mask.setColor(Color.WHITE)
        mask.cornerRadius = palette.keyRadius * context.resources.displayMetrics.density
        
        return RippleDrawable(rippleColor, createKeyDrawable(), mask)
    }
    
    // ===== PAINT FACTORY METHODS =====
    
    /**
     * Create text paint for keys
     */
    fun createKeyTextPaint(): Paint {
        val palette = getCurrentPalette()
        
        return Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = palette.keyFontSize * context.resources.displayMetrics.scaledDensity
            color = palette.keyText
            typeface = createTypeface(palette.keyFontFamily, palette.keyFontBold, palette.keyFontItalic)
        }
    }
    
    /**
     * Create text paint for a specific key with per-key customization
     * @param keyIdentifier The key identifier (e.g., "a", "enter", "space", etc.)
     */
    fun createKeyTextPaint(keyIdentifier: String): Paint {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val customization = theme.keys.perKeyCustomization[keyIdentifier]
        
        return Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            
            // Use custom font if specified, otherwise use global font
            val fontSize = customization?.font?.sizeSp ?: palette.keyFontSize
            textSize = fontSize * context.resources.displayMetrics.scaledDensity
            
            // Use custom text color if specified, otherwise use global color
            color = customization?.text ?: palette.keyText
            
            val fontFamily = customization?.font?.family ?: palette.keyFontFamily
            val bold = customization?.font?.bold ?: palette.keyFontBold
            val italic = customization?.font?.italic ?: palette.keyFontItalic
            typeface = createTypeface(fontFamily, bold, italic)
        }
    }
    
    /**
     * Create text paint for suggestions
     */
    fun createSuggestionTextPaint(): Paint {
        val palette = getCurrentPalette()
        
        return Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = palette.suggestionFontSize * context.resources.displayMetrics.scaledDensity
            color = palette.suggestionText
            typeface = createTypeface(palette.suggestionFontFamily, palette.suggestionFontBold, false)
        }
    }
    
    /**
     * Create text paint for space label
     */
    fun createSpaceLabelPaint(): Paint {
        val palette = getCurrentPalette()
        
        return Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = (palette.keyFontSize * 0.75f) * context.resources.displayMetrics.scaledDensity
            color = palette.spaceLabelColor
            typeface = createTypeface(palette.keyFontFamily, false, false)
        }
    }
    
    // Font cache for performance
    private val fontCache = mutableMapOf<String, Typeface>()
    
    internal fun createTypeface(family: String, bold: Boolean, italic: Boolean): Typeface {
        val cacheKey = "${family}_${bold}_${italic}"
        
        // Return cached font if available
        fontCache[cacheKey]?.let { return it }
        
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        
        val typeface = try {
            when {
                // Try loading from assets/fonts folder first (custom fonts)
                family.endsWith(".ttf") || family.endsWith(".otf") -> {
                    loadTypefaceFromAssets(family)
                }
                // Try system font names
                family.equals("Roboto", ignoreCase = true) -> Typeface.create("sans-serif", style)
                family.equals("RobotoMono", ignoreCase = true) -> Typeface.create("monospace", style)
                family.equals("Serif", ignoreCase = true) -> Typeface.create("serif", style)
                family.equals("SansSerif", ignoreCase = true) -> Typeface.create("sans-serif", style)
                family.equals("Monospace", ignoreCase = true) -> Typeface.create("monospace", style)
                family.equals("Cursive", ignoreCase = true) -> Typeface.create("cursive", style)
                family.equals("Casual", ignoreCase = true) -> Typeface.create("casual", style)
                // Try loading from Android resources
                else -> {
                    val resId = context.resources.getIdentifier(
                        family.lowercase().replace(" ", "_"), 
                        "font", 
                        context.packageName
                    )
                    if (resId != 0) {
                        ResourcesCompat.getFont(context, resId)
                    } else {
                        // Try loading from assets with common font file patterns
                        loadTypefaceFromAssets("$family.ttf") 
                            ?: loadTypefaceFromAssets("${family}-Regular.ttf")
                            ?: loadTypefaceFromAssets("${family}Regular.ttf")
                            ?: Typeface.create(Typeface.DEFAULT, style)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load font: $family", e)
            Typeface.create(Typeface.DEFAULT, style)
        }
        
        // Apply style if needed and cache
        val styledTypeface = if (typeface != null && style != Typeface.NORMAL) {
            Typeface.create(typeface, style)
        } else {
            typeface ?: Typeface.create(Typeface.DEFAULT, style)
        }
        
        fontCache[cacheKey] = styledTypeface
        return styledTypeface
    }
    
    /**
     * Load typeface from assets/fonts folder
     */
    private fun loadTypefaceFromAssets(fontFileName: String): Typeface? {
        return try {
            val fontPath = "fonts/$fontFileName"
            Typeface.createFromAsset(context.assets, fontPath)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get list of available custom fonts from assets
     */
    fun getAvailableFonts(): List<String> {
        val fonts = mutableListOf(
            "Roboto",
            "RobotoMono", 
            "Serif",
            "SansSerif",
            "Monospace",
            "Cursive",
            "Casual"
        )
        
        try {
            // Add custom fonts from assets/fonts folder
            val fontFiles = context.assets.list("fonts") ?: emptyArray()
            fontFiles.forEach { fileName ->
                if (fileName.endsWith(".ttf") || fileName.endsWith(".otf")) {
                    // Extract font name without extension
                    val fontName = fileName.substringBeforeLast(".")
                        .replace("-Regular", "")
                        .replace("Regular", "")
                        .replace("-", " ")
                    if (!fonts.contains(fontName)) {
                        fonts.add(fontName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list fonts", e)
        }
        
        return fonts.sorted()
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if a key should use accent color
     */
    fun shouldUseAccentForKey(keyType: String): Boolean {
        return getCurrentPalette().shouldApplyAccentTo(keyType)
    }
    
    /**
     * Check if Enter key should use accent
     */
    fun shouldUseAccentForEnter(): Boolean {
        return getCurrentPalette().shouldUseAccentForEnter()
    }
    
    /**
     * Get contrast ratio for accessibility
     */
    fun getContrastRatio(foreground: Int, background: Int): Float {
        val fgLum = calculateLuminance(foreground)
        val bgLum = calculateLuminance(background)
        
        val lighter = maxOf(fgLum, bgLum)
        val darker = minOf(fgLum, bgLum)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    private fun calculateLuminance(color: Int): Float {
        val r = Color.red(color) / 255.0f
        val g = Color.green(color) / 255.0f
        val b = Color.blue(color) / 255.0f
        
        val rLum = if (r <= 0.03928f) r / 12.92f else ((r + 0.055f) / 1.055f).pow(2.4f)
        val gLum = if (g <= 0.03928f) g / 12.92f else ((g + 0.055f) / 1.055f).pow(2.4f)
        val bLum = if (b <= 0.03928f) b / 12.92f else ((b + 0.055f) / 1.055f).pow(2.4f)
        
        return 0.2126f * rLum + 0.7152f * gLum + 0.0722f * bLum
    }
    
    /**
     * Auto-adjust text color for better contrast
     */
    fun getContrastAdjustedTextColor(backgroundColor: Int): Int {
        val whiteContrast = getContrastRatio(Color.WHITE, backgroundColor)
        val blackContrast = getContrastRatio(Color.BLACK, backgroundColor)
        
        return if (whiteContrast > blackContrast) Color.WHITE else Color.BLACK
    }
    
    // ===== NEW ADAPTIVE & SEASONAL METHODS =====
    
    private fun buildAdaptiveBackground(): Drawable {
        val theme = getCurrentTheme()
        val adaptiveConfig = theme.background.adaptive
        
        return when (adaptiveConfig?.source) {
            "wallpaper" -> extractWallpaperColors()
            "system" -> extractSystemColors()
            else -> buildSolidDrawable(theme.background.color ?: Color.BLACK)
        }
    }
    
    private fun extractWallpaperColors(): Drawable {
        // For now, return a fallback. In production, this would extract dominant colors from wallpaper
        val theme = getCurrentTheme()
        return buildSolidDrawable(theme.background.color ?: Color.BLACK)
    }
    
    private fun extractSystemColors(): Drawable {
        // For now, return a fallback. In production, this would use Material You system colors
        val theme = getCurrentTheme()
        return buildSolidDrawable(theme.background.color ?: Color.BLACK)
    }
    
    private fun applySeasonalOverlay(baseDrawable: Drawable, seasonalPack: String): Drawable {
        // Create a layer drawable with the base and seasonal overlay
        val layerDrawable = LayerDrawable(arrayOf(
            baseDrawable,
            createSeasonalOverlay(seasonalPack)
        ))
        return layerDrawable
    }
    
    private fun createSeasonalOverlay(seasonalPack: String): Drawable {
        // For now, create a simple tinted overlay. In production, load seasonal resources
        val overlayColor = when (seasonalPack) {
            "valentine" -> Color.parseColor("#33FF6B9D") // Pink tint
            "halloween" -> Color.parseColor("#33FF8C00") // Orange tint
            "christmas" -> Color.parseColor("#3300FF00") // Green tint
            else -> Color.TRANSPARENT
        }
        
        val drawable = GradientDrawable()
        drawable.setColor(overlayColor)
        return drawable
    }
    
    private fun loadStickerDrawable(pack: String): Drawable? {
        // For now, return null. In production, load sticker assets from pack
        // This would load animated or static drawables based on pack name
        return try {
            // Example: context.getDrawable(R.drawable.sticker_pack_name)
            null
        } catch (e: Exception) {
            null
        }
    }
    
    fun cleanup() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        drawableCache.evictAll()
        imageCache.evictAll()
        pendingImageLoads.clear()
        asyncScope.cancel() // ⚡ PERFORMANCE: Cancel pending async loads
        listeners.clear()
    }
    
    // ===== UNIFIED THEME COLOR ACCESSORS FOR PANELS =====
    
    /**
     * Get keyboard background color for panels to match main keyboard
     * Single source of truth for all panel backgrounds
     */
    fun getKeyboardBackgroundColor(): Int {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        if (palette.usesImageBackground) {
            return palette.panelSurface
        }
        return theme.background.color ?: palette.keyboardBg
    }
    
    /**
     * Get key background color for panel UI elements
     */
    fun getKeyColor(): Int {
        val palette = getCurrentPalette()
        return if (palette.usesImageBackground) {
            ColorUtils.setAlphaComponent(Color.WHITE, 40)
        } else {
            palette.keyBg
        }
    }
    
    /**
     * Get text color for panel content
     */
    fun getTextColor(): Int {
        return getCurrentPalette().keyText
    }
    
    /**
     * Get text color for a specific key with per-key customization
     * @param keyIdentifier The key identifier (e.g., "a", "enter", "space", etc.)
     */
    fun getTextColor(keyIdentifier: String): Int {
        val theme = getCurrentTheme()
        val palette = getCurrentPalette()
        val customization = theme.keys.perKeyCustomization[keyIdentifier]
        return customization?.text ?: palette.keyText
    }
    
    /**
     * Get pressed/accent color for panel interactions
     */
    fun getAccentColor(): Int {
        return getCurrentPalette().specialAccent
    }
    
    /**
     * Get toolbar background color
     */
    fun getToolbarBackgroundColor(): Int {
        val palette = getCurrentPalette()
        return if (palette.usesImageBackground) Color.TRANSPARENT else palette.toolbarBg
    }
    
    /**
     * Get suggestion bar background color
     */
    fun getSuggestionBackgroundColor(): Int {
        val palette = getCurrentPalette()
        return if (palette.usesImageBackground) Color.TRANSPARENT else palette.suggestionBg
    }
    
    // ===== UNIFIED KEYBOARD VIEW THEMING EXTENSIONS =====
    
    /**
     * Apply toolbar theme to container
     */
    fun applyToolbarTheme(toolbar: LinearLayout) {
        val palette = getCurrentPalette()
        toolbar.background = createToolbarBackground()
        
        // Apply theme to all child buttons
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(palette.keyText)
                child.background = createKeyDrawable()
            }
        }
    }
    
    /**
     * Apply suggestion bar theme to container
     */
    fun applySuggestionBarTheme(container: LinearLayout) {
        val palette = getCurrentPalette()
        container.background = createSuggestionBarBackground()
        
        // Apply theme to all child suggestion items
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(palette.suggestionText)
                child.background = createKeyDrawable()
            }
        }
    }
    
    /**
     * Get toolbar button background drawable resource ID
     */
    fun getToolbarButtonBackground(): Int {
        // Return a themed drawable resource ID
        return android.R.drawable.btn_default
    }
    
    /**
     * Get suggestion chip background drawable resource ID
     */
    fun getSuggestionChipBackground(): Int {
        // Return a themed drawable resource ID for regular suggestions
        return android.R.drawable.btn_default
    }
    
    /**
     * Get primary suggestion background drawable resource ID (auto-commit)
     */
    fun getPrimarySuggestionBackground(): Int {
        // Return a themed drawable resource ID for primary suggestions
        return android.R.drawable.btn_default
    }
    
    /**
     * Get floating keyboard background drawable resource ID
     */
    fun getFloatingKeyboardBackground(): Int {
        // Return a themed drawable resource ID for floating mode
        return android.R.drawable.dialog_frame
    }
    
    /**
     * Create suggestion chip drawable with proper styling
     */
    fun createSuggestionChipDrawable(isActive: Boolean = false): Drawable {
        val palette = getCurrentPalette()
        val density = context.resources.displayMetrics.density
        val radiusPx = palette.keyRadius * density * 0.6f

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isActive) palette.specialAccent else palette.suggestionBg)
            cornerRadius = radiusPx
        }

        return drawable
    }
    
    /**
     * Create toolbar button drawable with proper styling
     */
    fun createToolbarButtonDrawable(): Drawable {
        val palette = getCurrentPalette()
        val isImageTheme = palette.usesImageBackground
        val density = context.resources.displayMetrics.density
        val radiusDp = palette.keyRadius.coerceAtMost(8f)
        val radiusPx = radiusDp * density
        
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.setColor(
            if (isImageTheme) ColorUtils.setAlphaComponent(palette.specialAccent, 220)
            else palette.specialAccent
        )
        drawable.cornerRadius = radiusPx
        
        // Apply border if enabled
        if (palette.keyBorderEnabled && !isImageTheme) {
            drawable.setStroke(
                (palette.keyBorderWidth * density * 0.5f).toInt(),
                palette.specialAccent
            )
        }
        
        return drawable
    }
    
    /**
     * Style auto-commit chip with check icon and accent color
     */
    fun styleAutoCommitChip(view: TextView, isPrimary: Boolean) {
        val palette = getCurrentPalette()
        
        view.background = createSuggestionChipDrawable(isPrimary)
        view.setTextColor(if (isPrimary) getAccentOnColor() else palette.suggestionText)
        view.setCompoundDrawables(null, null, null, null)
    }
    
    /**
     * Get theme-aware text color for toolbar buttons
     */
    fun getToolbarTextColor(): Int {
        val palette = getCurrentPalette()
        if (palette.usesImageBackground) {
            return Color.WHITE
        }
        val luminance = ColorUtils.calculateLuminance(palette.toolbarBg)
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
    
    /**
     * Get theme-aware text color for suggestions
     */
    fun getSuggestionTextColor(): Int {
        return getCurrentPalette().suggestionText
    }

    fun getAccentOnColor(): Int {
        val palette = getCurrentPalette()
        val luminance = ColorUtils.calculateLuminance(palette.specialAccent)
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
    
    /**
     * Get theme-aware background color for floating keyboard
     */
    fun getFloatingBackgroundColor(): Int {
        val palette = getCurrentPalette()
        // Slightly darker/lighter than main background for floating effect
        return when {
            palette.keyboardBg == Color.BLACK -> Color.parseColor("#1A1A1A")
            palette.keyboardBg == Color.WHITE -> Color.parseColor("#F5F5F5")
            else -> {
                val hsv = FloatArray(3)
                Color.colorToHSV(palette.keyboardBg, hsv)
                hsv[2] = (hsv[2] * 0.9f).coerceIn(0f, 1f) // Slightly darker
                Color.HSVToColor(hsv)
            }
        }
    }

    fun isImageBackground(): Boolean {
        return getCurrentPalette().usesImageBackground
    }
    
    // ===== CUSTOM SHAPE DRAWABLES =====
    
    /**
     * Create custom shape drawable for non-rectangular keys
     */
    private fun createCustomShapeDrawable(preset: String, palette: ThemePaletteV2): Drawable {
        val isTransparentStyle = palette.usesImageBackground || palette.isTransparentPreset
        val bgColor = if (isTransparentStyle) Color.TRANSPARENT else palette.keyBg
        val borderColor = if (palette.keyBorderEnabled) {
            palette.keyBorderColor
        } else if (isTransparentStyle) {
            ColorUtils.setAlphaComponent(palette.keyText, 170)
        } else {
            Color.TRANSPARENT
        }
        val density = context.resources.displayMetrics.density
        val borderWidth = if (isTransparentStyle || palette.keyBorderEnabled) {
            (max(1f, palette.keyBorderWidth) * density)
        } else {
            0f
        }
        
        return CustomShapeDrawable(preset, bgColor, borderColor, borderWidth)
    }
    
    /**
     * Custom drawable that renders different key shapes
     */
    private class CustomShapeDrawable(
        private val preset: String,
        private val bgColor: Int,
        private val borderColor: Int,
        private val borderWidth: Float
    ) : Drawable() {
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bgColor
        }
        
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = borderWidth
        }
        
        private val path = Path()
        
        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (bounds.isEmpty) return
            
            path.reset()
            
            when (preset) {
                "star" -> drawStar(canvas, bounds)
                "heart" -> drawHeart(canvas, bounds)
                "hexagon" -> drawHexagon(canvas, bounds)
                "cone" -> drawCone(canvas, bounds)
                "gem" -> drawGem(canvas, bounds)
                "slice" -> drawSlice(canvas, bounds)
                else -> drawRoundedRect(canvas, bounds)
            }
            
            // Draw fill
            canvas.drawPath(path, paint)
            
            // Draw border if enabled
            if (borderWidth > 0) {
                canvas.drawPath(path, borderPaint)
            }
        }
        
        private fun drawStar(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val outerRadius = min(bounds.width(), bounds.height()) * 0.45f
            val innerRadius = outerRadius * 0.4f
            
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val angle = (i * 36 - 90) * Math.PI / 180
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
        }
        
        private fun drawHeart(canvas: Canvas, bounds: Rect) {
            val width = bounds.width() * 0.9f
            val height = bounds.height() * 0.9f
            val offsetX = bounds.left + bounds.width() * 0.05f
            val offsetY = bounds.top + bounds.height() * 0.15f
            
            path.moveTo(offsetX + width / 2, offsetY + height)
            
            // Left side of heart
            path.cubicTo(
                offsetX + width / 2, offsetY + height * 0.7f,
                offsetX, offsetY + height * 0.4f,
                offsetX, offsetY + height * 0.25f
            )
            path.cubicTo(
                offsetX, offsetY,
                offsetX + width * 0.25f, offsetY,
                offsetX + width / 2, offsetY + height * 0.25f
            )
            
            // Right side of heart
            path.cubicTo(
                offsetX + width * 0.75f, offsetY,
                offsetX + width, offsetY,
                offsetX + width, offsetY + height * 0.25f
            )
            path.cubicTo(
                offsetX + width, offsetY + height * 0.4f,
                offsetX + width / 2, offsetY + height * 0.7f,
                offsetX + width / 2, offsetY + height
            )
            path.close()
        }
        
        private fun drawHexagon(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val radius = min(bounds.width(), bounds.height()) * 0.45f
            
            for (i in 0 until 6) {
                val angle = (i * 60 - 90) * Math.PI / 180
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
        }
        
        private fun drawCone(canvas: Canvas, bounds: Rect) {
            val topX = bounds.exactCenterX()
            val topY = bounds.top + bounds.height() * 0.15f
            val bottomY = bounds.bottom - bounds.height() * 0.15f
            val bottomLeft = bounds.left + bounds.width() * 0.2f
            val bottomRight = bounds.right - bounds.width() * 0.2f
            
            path.moveTo(topX, topY)
            path.lineTo(bottomRight, bottomY)
            path.lineTo(bottomLeft, bottomY)
            path.close()
        }
        
        private fun drawGem(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.exactCenterX()
            val topY = bounds.top + bounds.height() * 0.2f
            val middleY = bounds.exactCenterY()
            val bottomY = bounds.bottom - bounds.height() * 0.1f
            val leftX = bounds.left + bounds.width() * 0.15f
            val rightX = bounds.right - bounds.width() * 0.15f
            val farLeftX = bounds.left + bounds.width() * 0.05f
            val farRightX = bounds.right - bounds.width() * 0.05f
            
            // Top facet
            path.moveTo(centerX, topY)
            path.lineTo(rightX, middleY)
            path.lineTo(centerX, bottomY)
            path.lineTo(leftX, middleY)
            path.close()
            
            // Add gem outline for more complexity
            path.moveTo(farLeftX, middleY)
            path.lineTo(leftX, middleY)
            path.lineTo(centerX, topY)
            path.lineTo(rightX, middleY)
            path.lineTo(farRightX, middleY)
        }
        
        private fun drawSlice(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.exactCenterX()
            val topY = bounds.top + bounds.height() * 0.08f
            val bottomY = bounds.bottom - bounds.height() * 0.12f
            val halfWidth = bounds.width() * 0.45f
            val curveDepth = bounds.height() * 0.08f
            
            path.moveTo(centerX, topY)
            path.lineTo(centerX + halfWidth, bottomY - curveDepth)
            path.quadTo(centerX, bottomY + curveDepth, centerX - halfWidth, bottomY - curveDepth)
            path.close()
        }

        private fun drawRoundedRect(canvas: Canvas, bounds: Rect) {
            path.addRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                10f,
                10f,
                Path.Direction.CW
            )
        }
        
        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            borderPaint.alpha = alpha
        }
        
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            borderPaint.colorFilter = colorFilter
        }
        
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    
    // ===== LEGACY COMPATIBILITY =====
    
}
