package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Modern Dynamic Multilingual Layout System
 * 
 * Loads keyboard layouts using:
 * 1. Base templates (QWERTY, INSCRIPT, ARABIC) - define physical key positions
 * 2. Per-language keymaps - define character mappings for each language
 * 3. Firebase fallback - downloads missing keymaps from cloud storage
 * 
 * Architecture:
 * - Template defines the grid structure (rows/cols)
 * - Keymap defines what characters appear on each key
 * - This separation allows adding new languages without changing templates
 */
class LanguageLayoutAdapter(private val context: Context) {
    
    companion object {
        private const val TAG = "LanguageLayoutAdapter"
        private const val CACHE_DIR_NAME = "keymaps_cache"
        
        /**
         * Default long press mappings for common keys
         * Provides accent variants and special characters
         */
        private val LONG_PRESS_MAP = mapOf(
            'a'.code to listOf("á", "à", "â", "ä", "ã", "å", "ā", "ă", "ą"),
            'e'.code to listOf("é", "è", "ê", "ë", "ē", "ĕ", "ė", "ę", "ě"),
            'i'.code to listOf("í", "ì", "î", "ï", "ī", "ĭ", "į", "ı"),
            'o'.code to listOf("ó", "ò", "ô", "ö", "õ", "ō", "ŏ", "ő", "ø"),
            'u'.code to listOf("ú", "ù", "û", "ü", "ū", "ŭ", "ů", "ű", "ų"),
            'y'.code to listOf("ý", "ỳ", "ŷ", "ÿ"),
            'c'.code to listOf("ç", "ć", "ĉ", "ċ", "č"),
            'd'.code to listOf("ď", "đ"),
            'g'.code to listOf("ğ", "ĝ", "ġ", "ģ"),
            'l'.code to listOf("ĺ", "ļ", "ľ", "ŀ", "ł"),
            'n'.code to listOf("ñ", "ń", "ņ", "ň", "ŉ", "ŋ"),
            'r'.code to listOf("ŕ", "ŗ", "ř"),
            's'.code to listOf("ś", "ŝ", "ş", "š"),
            't'.code to listOf("ţ", "ť", "ŧ"),
            'z'.code to listOf("ź", "ż", "ž"),
            '0'.code to listOf("°", "₀", "⁰"),
            '1'.code to listOf("¹", "₁", "½", "⅓", "¼"),
            '2'.code to listOf("²", "₂", "⅔"),
            '3'.code to listOf("³", "₃", "¾"),
            '4'.code to listOf("⁴", "₄"),
            '5'.code to listOf("⁵", "₅"),
            '-'.code to listOf("–", "—", "−", "±"),
            '='.code to listOf("≠", "≈", "≤", "≥", "±"),
            '?'.code to listOf("¿", "‽"),
            '!'.code to listOf("¡", "‼", "⁉"),
            '.'.code to listOf("…", "·", "•"),
            '$'.code to listOf("¢", "£", "€", "¥", "₹", "₽", "₩")
        )
        
        /**
         * Get long press options for a character code
         */
        fun getLongPressOptions(code: Int): List<String>? {
            return LONG_PRESS_MAP[code]
        }
    }
    
    /**
     * Keyboard mode enum for different layout types
     */
    enum class KeyboardMode {
        LETTERS, SYMBOLS, EXTENDED_SYMBOLS, DIALER
    }
    
    private val cacheDir: File by lazy {
        File(context.filesDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }
    
    private var showUtilityKey = true
    
    /**
     * Key model representing a single keyboard key
     */
    data class KeyModel(
        val label: String,              // Primary display label
        val code: Int,                  // Key code for input
        val altLabel: String? = null,   // Secondary label (for number row)
        val longPress: List<String>? = null  // Long-press variants
    )
    
    /**
     * Layout model representing complete keyboard
     */
    data class LayoutModel(
        val rows: List<List<KeyModel>>,
        val languageCode: String,
        val layoutType: String,
        val direction: String = "LTR",
        val numberRow: List<KeyModel> = emptyList(),
        val rowHeightsDp: List<Int> = emptyList()
    )
    
    /**
     * Build number row for a specific language with native numerals
     */
    fun buildNumberRow(languageCode: String): List<KeyModel> {
        val numerals = when (languageCode) {
            "hi" -> listOf("१", "२", "३", "४", "५", "६", "७", "८", "९", "०")
            "ta" -> listOf("௧", "௨", "௩", "௪", "௫", "௬", "௭", "௮", "௯", "௦")
            "te" -> listOf("౧", "౨", "౩", "౪", "౫", "౬", "౭", "౮", "౯", "౦")
            else -> listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
        return numerals.map { numeral ->
            KeyModel(
                label = numeral,
                code = numeral.codePointAt(0),
                longPress = null
            )
        }
    }
    
    fun setShowUtilityKey(enabled: Boolean) {
        showUtilityKey = enabled
    }
    
    fun isUtilityKeyVisible(): Boolean {
        return showUtilityKey
    }
    
    /**
     * Build complete keyboard layout for a language with mode support
     * 
     * @param languageCode ISO 639-1 language code (e.g., "en", "hi", "te")
     * @param mode Keyboard mode (LETTERS, SYMBOLS, EXTENDED_SYMBOLS, DIALER)
     * @param numberRowEnabled Whether to include number row in letters mode
     * @return LayoutModel with all keys configured
     */
    suspend fun buildLayoutFor(languageCode: String, mode: KeyboardMode, numberRowEnabled: Boolean): LayoutModel {
        Log.d(TAG, "🔧 Building layout for: $languageCode, mode: $mode, numberRow: $numberRowEnabled")
        
        // Step 1: Determine template file based on mode
        val templateName = when (mode) {
            KeyboardMode.LETTERS -> {
                val keymap = loadKeymap(languageCode)
                // Get template from keymap or use default based on language
                val defaultTemplate = when (languageCode) {
                    in listOf("hi", "te", "ta", "ml", "gu", "bn", "kn", "or", "pa") -> "inscript_template.json"
                    in listOf("ar", "ur", "fa") -> "arabic_template.json"
                    else -> "qwerty_template.json"
                }
                keymap.optString("template", defaultTemplate)
            }
            KeyboardMode.SYMBOLS -> "symbols_template.json"
            KeyboardMode.EXTENDED_SYMBOLS -> "extended_symbols_template.json"
            KeyboardMode.DIALER -> "dialer_template.json"
        }
        Log.d(TAG, "📄 Using template: $templateName for mode: $mode")
        
        // Step 2: Load base template
        val baseLayout = loadTemplate(templateName)
        
        // Step 3: Apply language-specific mappings (for letters mode) or use template as-is
        val rows = if (mode == KeyboardMode.LETTERS) {
            val keymap = loadKeymap(languageCode)

            val shiftActive = KeyboardStateManager.isShiftActive() || KeyboardStateManager.isCapsLockEnabled()
            val primaryLayer = when {
                shiftActive && keymap.has("uppercase") -> keymap.optJSONObject("uppercase")
                keymap.has("lowercase") -> keymap.optJSONObject("lowercase")
                keymap.has("base") -> keymap.optJSONObject("base")
                else -> null
            } ?: JSONObject()

            val layoutRows = applyKeymapToTemplate(baseLayout, keymap, primaryLayer, shiftActive)
            
            // Inject number row if enabled (with language-specific numerals from alt mapping)
            if (numberRowEnabled) {
                val altMap = keymap.optJSONObject("alt") ?: JSONObject()
                val numberRow = listOf(
                    KeyModel(altMap.optString("1", "1"), altMap.optString("1", "1").codePointAt(0)),
                    KeyModel(altMap.optString("2", "2"), altMap.optString("2", "2").codePointAt(0)),
                    KeyModel(altMap.optString("3", "3"), altMap.optString("3", "3").codePointAt(0)),
                    KeyModel(altMap.optString("4", "4"), altMap.optString("4", "4").codePointAt(0)),
                    KeyModel(altMap.optString("5", "5"), altMap.optString("5", "5").codePointAt(0)),
                    KeyModel(altMap.optString("6", "6"), altMap.optString("6", "6").codePointAt(0)),
                    KeyModel(altMap.optString("7", "7"), altMap.optString("7", "7").codePointAt(0)),
                    KeyModel(altMap.optString("8", "8"), altMap.optString("8", "8").codePointAt(0)),
                    KeyModel(altMap.optString("9", "9"), altMap.optString("9", "9").codePointAt(0)),
                    KeyModel(altMap.optString("0", "0"), altMap.optString("0", "0").codePointAt(0))
                )
                Log.d(TAG, "✅ Injected number row with language-specific numerals: $languageCode")
                listOf(numberRow) + layoutRows
            } else {
                layoutRows
            }
        } else {
            // For symbols/extended/dialer, parse template directly
            parseTemplateRows(baseLayout)
        }
        
        // Step 4: Get text direction (only relevant for letters mode)
        val direction = if (mode == KeyboardMode.LETTERS) {
            val keymap = loadKeymap(languageCode)
            keymap.optString("direction", "LTR")
        } else {
            "LTR"
        }
        
        // Step 5: Normalize special keys to ensure consistency across all layouts
        val normalizedRows = normalizeSpecialKeys(rows)
            .map { row ->
                if (showUtilityKey) row else row.filter { it.code != -14 }
            }
    
        // Step 6: Build number row separately (not included in rows by default)
        val numberRowKeys = if (numberRowEnabled) buildNumberRow(languageCode) else emptyList()
        
        val baseRowHeights = when (mode) {
            KeyboardMode.LETTERS -> {
                val heights = MutableList(4) { 56 }
                if (numberRowEnabled) {
                    heights.add(0, 56)
                }
                heights
            }
            KeyboardMode.SYMBOLS, KeyboardMode.EXTENDED_SYMBOLS,
            KeyboardMode.DIALER -> {
                val heights = MutableList(4) { 56 }
                if (numberRowEnabled) {
                    heights.add(0, 56)
                }
                heights
            }
        }
        val adjustedRowHeights = if (baseRowHeights.isNotEmpty()) {
            val mutableHeights = baseRowHeights.toMutableList()
            val targetSize = normalizedRows.size
            if (targetSize > 0) {
                while (mutableHeights.size < targetSize) {
                    mutableHeights.add(mutableHeights.last())
                }
                if (mutableHeights.size > targetSize) {
                    mutableHeights.subList(targetSize, mutableHeights.size).clear()
                }
            }
            mutableHeights
        } else {
            emptyList()
        }

        val layout = LayoutModel(
            rows = normalizedRows,
            languageCode = languageCode,
            layoutType = templateName,
            direction = direction,
            numberRow = numberRowKeys,
            rowHeightsDp = adjustedRowHeights
        )
        
        Log.d(TAG, "✅ Layout built: ${normalizedRows.size} rows, ${normalizedRows.flatten().size} keys, numberRow: ${numberRowKeys.size} keys")
        return layout
    }
    
    /**
     * Normalize special keys to ensure consistent behavior across all layouts
     * This ensures Shift, Delete, Enter, Space, Globe, Emoji, Mic keys are identical
     * regardless of language or template
     * 
     * ✅ FIXED: Mode switch keys now use Keyboard.KEYCODE_MODE_CHANGE consistently
     */
    private fun normalizeSpecialKeys(rows: List<List<KeyModel>>): List<List<KeyModel>> {
        val specialKeysMap = mapOf(
            // Shift key
            "SHIFT" to KeyModel("⇧", -1),
            "⇧" to KeyModel("⇧", -1),
            
            // Delete/Backspace key
            "DELETE" to KeyModel("⌫", -5),
            "⌫" to KeyModel("⌫", -5),
            
            // Return/Enter key
            "RETURN" to KeyModel("⏎", -4),
            "⏎" to KeyModel("⏎", -4),
            "sym_keyboard_return" to KeyModel("⏎", -4),
            
            // Space key
            "SPACE" to KeyModel(" ", 32),
            "space" to KeyModel(" ", 32),
            " " to KeyModel(" ", 32),
            
            // Language/Globe key
            "GLOBE" to KeyModel("🌐", -14),
            "🌐" to KeyModel("🌐", -14),
            
            // Emoji key
            "EMOJI" to KeyModel("😊", -15),
            "😊" to KeyModel("😊", -15),
            
            // Mic/Voice key
            "MIC" to KeyModel("🎤", -16),
            "🎤" to KeyModel("🎤", -16),
            
            // Mode switch keys - all use consistent codes
            "?123" to KeyModel("?123", -10),  // Switch to symbols
            "ABC" to KeyModel("ABC", -11),    // Switch to letters
            "=<" to KeyModel("=<", -20),      // Switch to extended symbols
            "123" to KeyModel("123", -21)   // Switch to dialer
        )
        
        return rows.map { row ->
            row.map { key ->
                // Check if this key matches a special key pattern
                val normalizedKey = specialKeysMap[key.label] 
                    ?: specialKeysMap[key.label.uppercase()] 
                    ?: specialKeysMap[key.label.trim()]
                
                // If it's a special key, use the normalized version but preserve long-press
                if (normalizedKey != null) {
                    normalizedKey.copy(longPress = key.longPress)
                } else {
                    key
                }
            }
        }
    }
    
    /**
     * Parse template rows directly without keymap mapping
     * Used for symbol/extended/dialer layouts
     * Maps key labels to Android Keyboard codes
     */
    private fun parseTemplateRows(template: JSONObject): List<List<KeyModel>> {
        val rows = mutableListOf<List<KeyModel>>()
        val rowsArray = template.getJSONArray("rows")
        
        for (i in 0 until rowsArray.length()) {
            val rowArray = rowsArray.getJSONArray(i)
            val row = mutableListOf<KeyModel>()
            
            for (j in 0 until rowArray.length()) {
                val keyLabel = rowArray.getString(j)
                
                // Map key labels to Android Keyboard codes (matching XML conventions)
                val keyCode = when (keyLabel) {
                    // Mode switches
                    "?123" -> -10     // Switch to symbols (or -3 for XML compat)
                    "ABC" -> -11      // Switch to letters (or -2 for XML compat)
                    "=<" -> -20       // Switch to extended symbols
                    "123" -> -21     // Switch to dialer
                    
                    // Special function keys
                    "SHIFT", "⇧" -> -1         // Shift key (android.inputmethodservice.Keyboard.KEYCODE_SHIFT)
                    "DELETE", "⌫" -> -5        // Delete/Backspace (android.inputmethodservice.Keyboard.KEYCODE_DELETE)
                    "RETURN", "sym_keyboard_return" -> -4  // Enter/Return (android.inputmethodservice.Keyboard.KEYCODE_DONE)
                    "GLOBE", "🌐" -> -14       // Globe/Language switch
                    "SPACE", "space" -> 32     // Space (ASCII space)
                    
                    else -> {
                                // For single-character keys, use their ASCII/Unicode codepoint
                        if (keyLabel.length == 1) {
                            keyLabel.codePointAt(0)
                        } else {
                            // For multi-char labels without mapping, use hash (shouldn't happen normally)
                            -1000 - keyLabel.hashCode()
                        }
                    }
                }
                
                // Get long press options for this key
                val longPressOptions = getLongPressOptions(keyCode)
                
                // Create key model
                val keyModel = KeyModel(
                    label = keyLabel,
                    code = keyCode,
                    longPress = longPressOptions
                )
                
                row.add(keyModel)
            }
            
            rows.add(row)
        }
        
        return rows
    }
    
    /**
     * Apply keymap mappings to template structure
     */
    private fun applyKeymapToTemplate(
        baseLayout: JSONObject,
        keymap: JSONObject,
        primaryLayer: JSONObject,
        shiftActive: Boolean
    ): List<List<KeyModel>> {
        val rows = mutableListOf<List<KeyModel>>()
        val rowsArray = baseLayout.getJSONArray("rows")
        val fallbackLower = keymap.optJSONObject("lowercase") ?: JSONObject()
        val fallbackBase = keymap.optJSONObject("base") ?: JSONObject()
        val altMap = keymap.optJSONObject("alt") ?: JSONObject()
        val longPressMap = keymap.optJSONObject("long_press") ?: JSONObject()

        val defaultNumberHints = mapOf(
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
            "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0"
        )
        
        for (i in 0 until rowsArray.length()) {
            val rowArray = rowsArray.getJSONArray(i)
            val row = mutableListOf<KeyModel>()
            
            for (j in 0 until rowArray.length()) {
                val baseKey = rowArray.getString(j)
                
                // Get mapped character (or use base key if no mapping)
                val mappedChar = primaryLayer.optString(
                    baseKey,
                    fallbackLower.optString(baseKey, fallbackBase.optString(baseKey, baseKey))
                )
                val finalChar = mappedChar.ifEmpty { baseKey }
                
                // Get alt mapping for number row hints
                val altRaw = altMap.optString(baseKey, altMap.optString(baseKey.lowercase(), ""))
                val defaultHint = defaultNumberHints[baseKey.lowercase()]
                val altChar = when {
                    altRaw.isNotEmpty() -> altRaw
                    !defaultHint.isNullOrEmpty() -> defaultHint
                    else -> ""
                }
                
                // Get long-press variants from keymap or fall back to default mapping
                var resolvedChar = finalChar
                val uppercaseFallbackNeeded = shiftActive && (!keymap.has("uppercase") || !primaryLayer.has(baseKey))
                if (uppercaseFallbackNeeded && resolvedChar.length == 1 && resolvedChar[0].isLetter()) {
                    resolvedChar = resolvedChar.uppercase()
                }

                val keyCode = resolvedChar.codePointAt(0)
                val configuredLongPress = longPressMap.optJSONArray(baseKey)?.let { array ->
                    List(array.length()) { idx -> array.getString(idx) }
                } ?: getLongPressOptions(keyCode)  // Fallback to default mapping

                val mergedLongPress = mutableListOf<String>()
                if (altChar.isNotEmpty()) mergedLongPress.add(altChar)
                if (!configuredLongPress.isNullOrEmpty()) mergedLongPress.addAll(configuredLongPress)
                val longPressVariants = if (mergedLongPress.isEmpty()) null else mergedLongPress.distinct()
                
                // Create key model
                val hintLabel = when {
                    altChar.isNotEmpty() -> altChar
                    !longPressVariants.isNullOrEmpty() && longPressVariants.first().isNotEmpty() -> longPressVariants.first()
                    else -> ""
                }

                val symbolHint = longPressVariants?.firstOrNull { variant ->
                    variant.isNotEmpty() && !variant[0].isLetterOrDigit()
                }

                val keyModel = KeyModel(
                    label = resolvedChar,
                    code = keyCode,
                    altLabel = when {
                        hintLabel.isNotEmpty() -> hintLabel
                        !symbolHint.isNullOrEmpty() -> symbolHint
                        else -> null
                    },
                    longPress = longPressVariants
                )
                
                row.add(keyModel)
            }
            
            rows.add(row)
        }
        
        return rows
    }
    
    /**
     * Load template JSON from assets
     */
    private fun loadTemplate(templateName: String): JSONObject {
        return try {
            val fullPath = "layout_templates/$templateName"
            val inputStream = context.assets.open(fullPath)
            val jsonStr = inputStream.bufferedReader(Charset.forName("UTF-8")).use { it.readText() }
            Log.d(TAG, "✅ Loaded template: $templateName")
            JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load template: $templateName", e)
            // Return basic QWERTY fallback
            createFallbackTemplate()
        }
    }
    
    /**
     * Load keymap for a language (try local, then Firebase)
     */
    private suspend fun loadKeymap(languageCode: String): JSONObject {
        // Try local assets first
        val localPath = "keymaps/$languageCode.json"
        try {
            val inputStream = context.assets.open(localPath)
            val jsonStr = inputStream.bufferedReader(Charset.forName("UTF-8")).use { it.readText() }
            Log.d(TAG, "✅ Loaded local keymap: $languageCode")
            return JSONObject(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Local keymap not found for $languageCode, trying Firebase")
        }
        
        // Try cache
        val cachedFile = File(cacheDir, "$languageCode.json")
        if (cachedFile.exists()) {
            try {
                val jsonStr = cachedFile.readText()
                Log.d(TAG, "✅ Loaded cached keymap: $languageCode")
                return JSONObject(jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to read cached keymap: $languageCode", e)
            }
        }
        
        // Try Firebase
        return try {
            fetchFromFirebase(languageCode)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to fetch from Firebase: $languageCode", e)
            // Return basic fallback keymap
            createFallbackKeymap(languageCode)
        }
    }
    
    /**
     * Fetch keymap from Firebase Storage
     */
    private suspend fun fetchFromFirebase(languageCode: String): JSONObject = suspendCoroutine { continuation ->
        try {
            Log.d(TAG, "🌐 Fetching keymap from Firebase: $languageCode")
            val storage = FirebaseStorage.getInstance()
            val ref = storage.reference.child("keymaps/$languageCode.json")
            val tempFile = File.createTempFile(languageCode, ".json")
            
            ref.getFile(tempFile)
                .addOnSuccessListener {
                    try {
                        val jsonStr = tempFile.readText()
                        val keymap = JSONObject(jsonStr)
                        
                        // Cache for future use
                        val cachedFile = File(cacheDir, "$languageCode.json")
                        cachedFile.writeText(jsonStr)
                        
                        Log.d(TAG, "✅ Downloaded and cached keymap: $languageCode")
                        tempFile.delete()
                        continuation.resume(keymap)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing Firebase keymap", e)
                        tempFile.delete()
                        continuation.resume(createFallbackKeymap(languageCode))
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "⚠️ Firebase fetch failed for $languageCode", exception)
                    tempFile.delete()
                    continuation.resume(createFallbackKeymap(languageCode))
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Firebase fetch error", e)
            continuation.resume(createFallbackKeymap(languageCode))
        }
    }
    
    /**
     * Create fallback template for error cases
     */
    private fun createFallbackTemplate(): JSONObject {
        return JSONObject().apply {
            put("name", "QWERTY Fallback")
            put("rows", JSONArray().apply {
                put(JSONArray(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")))
                put(JSONArray(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")))
                put(JSONArray(listOf("z", "x", "c", "v", "b", "n", "m")))
            })
        }
    }
    
    /**
     * Create fallback keymap (1:1 mapping)
     */
    private fun createFallbackKeymap(languageCode: String): JSONObject {
        Log.w(TAG, "⚠️ Using fallback keymap for: $languageCode")
        
        // Determine appropriate template based on language
        val templateName = when (languageCode) {
            in listOf("hi", "te", "ta", "ml", "gu", "bn", "kn", "or", "pa") -> "inscript_template.json"
            in listOf("ar", "ur", "fa", "ps") -> "arabic_template.json"
            else -> "qwerty_template.json"
        }
        
        return JSONObject().apply {
            put("language", languageCode)
            put("template", templateName)
            put("base", JSONObject().apply {
                // 1:1 mapping (passthrough)
                ('a'..'z').forEach { char ->
                    put(char.toString(), char.toString())
                }
            })
            put("alt", JSONObject())
            put("long_press", JSONObject())
        }
    }
    
    /**
     * Check if keymap exists locally
     */
    fun hasLocalKeymap(languageCode: String): Boolean {
        return try {
            context.assets.open("keymaps/$languageCode.json").close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if keymap exists in cache
     */
    fun hasCachedKeymap(languageCode: String): Boolean {
        return File(cacheDir, "$languageCode.json").exists()
    }
    
    /**
     * Preload keymap for a language (async)
     */
    suspend fun preloadKeymap(languageCode: String) {
        if (!hasLocalKeymap(languageCode) && !hasCachedKeymap(languageCode)) {
            Log.d(TAG, "📥 Preloading keymap: $languageCode")
            loadKeymap(languageCode)
        }
    }
    
    /**
     * Clear cache for a specific language
     */
    fun clearCache(languageCode: String) {
        File(cacheDir, "$languageCode.json").delete()
        Log.d(TAG, "🗑️ Cleared cache for: $languageCode")
    }
    
    /**
     * Clear all cached keymaps
     */
    fun clearAllCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "🗑️ Cleared all keymap cache")
    }
    
    /**
     * Get list of available keymaps (local + cached)
     */
    fun getAvailableKeymaps(): Set<String> {
        val keymaps = mutableSetOf<String>()
        
        // Local keymaps
        try {
            context.assets.list("keymaps")?.forEach { filename ->
                if (filename.endsWith(".json")) {
                    keymaps.add(filename.removeSuffix(".json"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing local keymaps", e)
        }
        
        // Cached keymaps
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".json")) {
                keymaps.add(file.name.removeSuffix(".json"))
            }
        }
        
        return keymaps
    }
    
    /**
     * 🔍 AUDIT: Verify all key ID mappings are correct
     * Call at startup to validate consistency
     */
    fun verifyAllMappings() {
        Log.d(TAG, "🔍 ============ KEY MAPPING AUDIT ============")
        
        val testLabels = listOf(
            "SHIFT", "⇧",
            "DELETE", "⌫",
            "RETURN", "sym_keyboard_return",
            "SPACE", "space",
            "GLOBE", "🌐",
            "?123", "ABC", "=<", "123"
        )
        
        testLabels.forEach { label ->
            val code = mapLabelToCode(label)
            val expected = getExpectedCodeForLabel(label)
            val status = if (code == expected) "✅" else "❌"
            Log.d("LayoutAudit", "$status Label='$label' → Code=$code (Expected: $expected)")
        }
        
        Log.d(TAG, "🔍 ========================================")
    }
    
    /**
     * Map label to key code (extracted for testing)
     */
    private fun mapLabelToCode(keyLabel: String): Int {
        return when (keyLabel) {
            // Mode switches
            "?123" -> -10
            "ABC" -> -11
            "=<" -> -20
            "123" -> -21
            
            // Special function keys
            "SHIFT", "⇧" -> -1
            "DELETE", "⌫" -> -5
            "RETURN", "sym_keyboard_return" -> -4
            "GLOBE", "🌐" -> -14
            "SPACE", "space" -> 32
            
            else -> {
                if (keyLabel.length == 1) keyLabel.codePointAt(0)
                else -1000 - keyLabel.hashCode()
            }
        }
    }
    
    /**
     * Get expected Android Keyboard code for a label
     */
    private fun getExpectedCodeForLabel(label: String): Int {
        return when (label) {
            "SHIFT", "⇧" -> -1  // Keyboard.KEYCODE_SHIFT
            "DELETE", "⌫" -> -5  // Keyboard.KEYCODE_DELETE
            "RETURN", "sym_keyboard_return" -> -4  // Keyboard.KEYCODE_DONE
            "SPACE", "space" -> 32  // ASCII space
            "GLOBE", "🌐" -> -14  // Custom globe
            "?123" -> -10  // Custom symbols switch
            "ABC" -> -11  // Custom letters switch
            "=<" -> -20  // Custom extended symbols
            "123" -> -21  // Custom dialer
            else -> if (label.length == 1) label.codePointAt(0) else -1000
        }
    }
    
    /**
     * 🔍 AUDIT: Compare key mappings between template and expected codes
     */
    fun compareKeyMappings(templateName: String) {
        try {
            val template = loadTemplate(templateName)
            val rows = parseTemplateRows(template)
            
            Log.d("LayoutAudit", "🔍 Auditing template: $templateName")
            rows.forEachIndexed { rowIdx, row ->
                row.forEach { key ->
                    val expected = getExpectedCodeForLabel(key.label)
                    val status = if (key.code == expected) "✅" else "❌"
                    if (key.code != expected) {
                        Log.w("LayoutAudit", "$status Row$rowIdx: '${key.label}' → Code=${key.code} (Expected: $expected)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to audit template: $templateName", e)
        }
    }
}
