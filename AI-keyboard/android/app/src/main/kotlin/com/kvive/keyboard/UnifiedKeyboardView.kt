package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.graphics.ColorUtils
import com.kvive.keyboard.themes.ThemePaletteV2
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

import com.kvive.keyboard.GestureAction
import com.kvive.keyboard.GestureSettings
import com.kvive.keyboard.GestureSource

/**
 * Manages keyboard height calculations and navigation bar detection
 * Provides consistent keyboard height across all panels (letters, symbols, emojis, grammar)
 * Handles system UI insets for Android 11+ and fallback for older versions
 */
class KeyboardHeightManager(private val context: Context) {
    
    companion object {
        private const val TAG = "KeyboardHeightManager"
        private const val KEYBOARD_HEIGHT_RATIO = 0.32f  // Increased to match Gboard height
        private const val DEFAULT_MIN_GRID_HEIGHT_DP = 280 // Match dimens.xml (5×57dp + gaps)
        private const val DEFAULT_MAX_GRID_HEIGHT_DP = 520
        private const val STRUCTURAL_MIN_GRID_HEIGHT_DP = 280  // Match new minimum
        private const val USER_MIN_GRID_HEIGHT_DP = 140
        private const val USER_MAX_GRID_HEIGHT_DP = 520
        private const val HEIGHT_PERCENT_MIN = 20
        private const val HEIGHT_PERCENT_MAX = 150
        private const val PREF_MIN_GRID_HEIGHT = "flutter.keyboard.minGridHeightDp"
        private const val PREF_MIN_GRID_HEIGHT_LEGACY = "keyboard.minGridHeightDp"
        private const val NUMBER_ROW_EXTRA_DP = 44
        private const val TOOLBAR_HEIGHT_DP = 64
        private const val SUGGESTION_BAR_HEIGHT_DP = 40
        private var lastHeight = 0
        private var cachedHeight = 0
        @Volatile
        private var isReturningFromVoicePanel = false

        fun applyKeyboardHeight(context: Context, newHeight: Int) {
            // Prevent height restoration when returning from voice panel
            if (isReturningFromVoicePanel) {
                Log.d(TAG, "⏸️ Skipping height update (returning from voice panel)")
                isReturningFromVoicePanel = false
                return
            }
            
            val prefs = context.getSharedPreferences("KeyboardHeightPrefs", Context.MODE_PRIVATE)
            val displayMetrics = context.resources.displayMetrics
            val minHeight = (displayMetrics.heightPixels * 0.32).toInt()

            var finalHeight = newHeight
            if (newHeight < minHeight && cachedHeight > 0) {
                Log.w(TAG, "⚠️ Ignoring low height $newHeight, using cached $cachedHeight")
                finalHeight = cachedHeight
            }

            if (abs(finalHeight - lastHeight) < 5) {
                Log.d(TAG, "⏸️ Skipping height update (diff < 5px)")
                return
            }

            lastHeight = finalHeight
            cachedHeight = finalHeight
            prefs.edit().putInt("last_keyboard_height", finalHeight).apply()
            Log.d(TAG, "📐 Applied keyboard height: $finalHeight px")
        }
        
        fun markReturningFromVoicePanel() {
            isReturningFromVoicePanel = true
            Log.d(TAG, "🏷️ Marked as returning from voice panel - will skip next height update")
        }
        
        fun invalidateCache() {
            cachedHeight = 0
            lastHeight = 0
            Log.d(TAG, "🔄 Keyboard height cache invalidated")
        }

        fun getSavedHeight(context: Context): Int {
            val prefs = context.getSharedPreferences("KeyboardHeightPrefs", Context.MODE_PRIVATE)
            val saved = prefs.getInt("last_keyboard_height", 0)
            if (saved > 0) {
                cachedHeight = saved
                lastHeight = saved
                Log.d(TAG, "📦 Restoring saved height: $saved px")
            }
            return saved
        }
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }
    private var numberRowEnabled = false
    
    /**
     * Calculates the baseline keyboard height using Gboard geometry
     * @param includeToolbar Whether to include toolbar height in calculation
     * @param includeSuggestions Whether to include suggestion bar height
     * @return Total keyboard height in pixels
     */
    fun calculateKeyboardHeight(
        includeToolbar: Boolean = false,
        includeSuggestions: Boolean = false,
        includeNavigationInset: Boolean = true
    ): Int {
        val toolbarHeight = resolveToolbarHeight()
        val suggestionHeight = resolveSuggestionBarHeight()
        val navigationInset = if (includeNavigationInset) getNavigationBarHeight() else 0
        
        val structuralMinPx = dpToPx(STRUCTURAL_MIN_GRID_HEIGHT_DP)
        val minGridPx = resolveMinGridHeightPx()
        val maxGridPx = dpToPx(DEFAULT_MAX_GRID_HEIGHT_DP).coerceAtLeast(minGridPx)
        val availableDisplayHeight = computeUsableDisplayHeightPx().coerceAtLeast(structuralMinPx)
        val ratioTargetPx = (availableDisplayHeight * KEYBOARD_HEIGHT_RATIO).roundToInt()
        val percentTargetPx = resolveHeightPercentPx(availableDisplayHeight)
        
        var gridHeight = (percentTargetPx ?: ratioTargetPx).coerceIn(minGridPx, maxGridPx).coerceAtLeast(structuralMinPx)

        if (numberRowEnabled) {
            val extraPx = dpToPx(NUMBER_ROW_EXTRA_DP)
            val maxWithNumberRow = maxGridPx + extraPx
            gridHeight = (gridHeight + extraPx).coerceAtMost(maxWithNumberRow)
        }

        var totalHeight = gridHeight
        
        if (includeToolbar) {
            totalHeight += toolbarHeight
        }
        if (includeSuggestions) {
            totalHeight += suggestionHeight
        }
        
        totalHeight += navigationInset
        
        return totalHeight
    }

    private fun resolveHeightPercentPx(availableDisplayHeight: Int): Int? {
        val percent = resolveHeightPercent() ?: return null
        return (availableDisplayHeight * (percent / 100f)).roundToInt()
    }

    private fun resolveHeightPercent(): Int? {
        val orientation = context.resources.configuration.orientation
        val orientationKeys = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            listOf("flutter.keyboard.heightPercentLandscape", "keyboard.heightPercentLandscape")
        } else {
            listOf("flutter.keyboard.heightPercentPortrait", "keyboard.heightPercentPortrait")
        }
        val genericKeys = listOf(
            "flutter.keyboard.heightPercent",
            "keyboard.heightPercent",
            "keyboard_height_percent"
        )
        val keysToCheck = orientationKeys + genericKeys
        for (key in keysToCheck) {
            val value = readPercentPreference(key)
            if (value != null && value > 0) {
                return value.coerceIn(HEIGHT_PERCENT_MIN, HEIGHT_PERCENT_MAX)
            }
        }
        return null
    }

    private fun readPercentPreference(key: String): Int? {
        if (!sharedPrefs.contains(key)) return null
        val value = sharedPrefs.all[key] ?: return null
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Float -> value.roundToInt()
            is Double -> value.roundToInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun resolveMinGridHeightPx(): Int {
        val dpValue = listOf(PREF_MIN_GRID_HEIGHT, PREF_MIN_GRID_HEIGHT_LEGACY)
            .firstNotNullOfOrNull { key ->
                readFloatPreference(key)?.roundToInt()
            }
            ?.coerceIn(USER_MIN_GRID_HEIGHT_DP, USER_MAX_GRID_HEIGHT_DP)
            ?: DEFAULT_MIN_GRID_HEIGHT_DP
        return dpToPx(dpValue)
    }

    private fun readFloatPreference(key: String): Float? {
        if (!sharedPrefs.contains(key)) return null
        val value = sharedPrefs.all[key] ?: return null
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }
    
    /**
     * Gets the navigation bar height if present
     * @return Navigation bar height in pixels, 0 if not present
     */
    fun getNavigationBarHeight(): Int {
        // First check if navigation bar is present
        if (!hasNavigationBar()) {
            return 0
        }
        
        // Try to get from resources
        val resourceId = context.resources.getIdentifier(
            "navigation_bar_height", "dimen", "android"
        )
        
        if (resourceId > 0) {
            val navBarHeight = context.resources.getDimensionPixelSize(resourceId)
            return navBarHeight
        }
        
        // Fallback to default navigation bar height (48dp)
        return dpToPx(48)
    }
    
    /**
     * Checks if the device has a navigation bar
     * @return true if navigation bar is present
     */
    fun hasNavigationBar(): Boolean {
        // Check for physical navigation keys
        val hasMenuKey = android.view.ViewConfiguration.get(context).hasPermanentMenuKey()
        val hasBackKey = android.view.KeyCharacterMap.deviceHasKey(android.view.KeyEvent.KEYCODE_BACK)
        
        if (hasMenuKey || hasBackKey) {
            // Physical keys present, no navigation bar
            return false
        }
        
        // Use Android 11+ insets API or fallback to display metrics
        return hasNavigationBarAndroid11()
    }
    
    /**
     * Applies system UI insets to a view (handles navigation bar and status bar)
     * @param view The view to apply insets to
     * @param applyBottom Whether to apply bottom insets (navigation bar)
     * @param applyTop Whether to apply top insets (status bar)
     */
    fun applySystemInsets(
        view: View,
        applyBottom: Boolean = true,
        applyTop: Boolean = false,
        onInsetsApplied: ((Int, Int) -> Unit)? = null
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            val topInset = if (applyTop) systemBars.top else 0
            val bottomInset = if (applyBottom) navBars.bottom else 0
            
            v.setPadding(
                v.paddingLeft,
                topInset,
                v.paddingRight,
                bottomInset
            )
            
            onInsetsApplied?.invoke(topInset, bottomInset)
            
            insets
        }
    }
    
    /**
     * Adjusts a keyboard panel's height to account for navigation bar
     * @param panel The panel view to adjust
     * @param baseHeight The base height in pixels (without navigation bar)
     */
    fun adjustPanelForNavigationBar(panel: View, baseHeight: Int) {
        val navBarHeight = getNavigationBarHeight()
        
        // ✅ KEEP the base height, but add bottom padding for nav bar
        // This ensures keys aren't compressed and keyboard sits above nav bar
        panel.layoutParams = panel.layoutParams?.apply {
            height = baseHeight
        } ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, baseHeight)
        
        // Add bottom padding equal to navigation bar height to push content up
        panel.setPadding(
            panel.paddingLeft,
            panel.paddingTop,
            panel.paddingRight,
            navBarHeight
        )
        
        // Ensure content doesn't get clipped
        if (panel is ViewGroup) {
            panel.clipToPadding = false
            panel.clipChildren = false
        }
    }
    
    // Private helper methods
    
    private fun computeUsableDisplayHeightPx(): Int {
        return Resources.getSystem().displayMetrics.heightPixels
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * density).roundToInt()
    }
    
    private fun hasNavigationBarAndroid11(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
            val navBarInsets = insets.getInsets(WindowInsets.Type.navigationBars())
            navBarInsets.bottom > 0
        } else {
            // Fallback for pre-Android 11: Check display dimensions
            val display = windowManager.defaultDisplay
            val realSize = Point()
            val size = Point()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                display.getRealSize(realSize)
                display.getSize(size)
                
                // Navigation bar is present if real size differs from display size
                realSize.y > size.y || realSize.x > size.x
            } else {
                false
            }
        }
    }
    
    /**
     * Helper method for unified layout controller
     * Applies calculated height directly to a ViewGroup
     * 
     * @param view The view to apply height to
     */
    fun applyHeightTo(view: ViewGroup) {
        val newHeight = calculateKeyboardHeight(
            includeToolbar = true,
            includeSuggestions = true
        )
        view.layoutParams = view.layoutParams?.apply {
            height = newHeight
        } ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, newHeight)
        view.requestLayout()
    }
    
    /**
     * Get panel height for dynamic panel creation
     * Returns height suitable for panels (without toolbar and suggestions)
     * 
     * @return Panel height in pixels
     */
    fun getPanelHeight(): Int {
        val baseHeight = calculateKeyboardHeight(
            includeToolbar = false,
            includeSuggestions = false,
            includeNavigationInset = true
        )
        return baseHeight
    }

    fun setNumberRowEnabled(enabled: Boolean) {
        numberRowEnabled = enabled
    }
    
    private fun resolveToolbarHeight(): Int {
        return safeDimensionPixelSize(R.dimen.toolbar_height, TOOLBAR_HEIGHT_DP)
    }
    
    private fun resolveSuggestionBarHeight(): Int {
        return safeDimensionPixelSize(R.dimen.suggestion_bar_height, SUGGESTION_BAR_HEIGHT_DP)
    }
    
    private fun safeDimensionPixelSize(resId: Int, fallbackDp: Int): Int {
        return try {
            context.resources.getDimensionPixelSize(resId)
        } catch (_: Resources.NotFoundException) {
            dpToPx(fallbackDp)
        }
    }
}

/**
 * 🎯 UNIFIED KEYBOARD VIEW V2 - Complete Modernization
 * 
 * Single source of truth for:
 * ✅ Swipe gesture detection and path normalization
 * ✅ Toolbar rendering with full icon parity (AI, Grammar, Tone, Clipboard, Emoji, Voice, Translate, GIF, Sticker, Incognito)
 * ✅ Suggestion bar with auto-commit styling and theming
 * ✅ Spacebar cursor gestures and backspace swipe delete
 * ✅ One-handed and floating keyboard modes
 * ✅ Firebase language readiness indicators
 * ✅ Haptic feedback integration
 * 
 * Architecture:
 * ┌──────────────────────────────────────────┐
 * │ toolbarContainer                         │ ← AI/Emoji/Settings/Modes buttons
 * ├──────────────────────────────────────────┤
 * │ suggestionContainer                      │ ← Suggestions with auto-commit styling
 * ├──────────────────────────────────────────┤
 * │ bodyContainer (FrameLayout)              │
 * │  ├─ keyboardGrid (typing mode)           │
 * │  └─ panelView (panel mode)               │
 * └──────────────────────────────────────────┘
 */
class UnifiedKeyboardView @JvmOverloads constructor(
    context: Context,
    private val themeManager: ThemeManager,
    private val heightManager: KeyboardHeightManager,
    private val onKeyCallback: ((Int, IntArray) -> Unit)? = null,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    
    companion object {
        private const val TAG = "UnifiedKeyboardView"
        
        // Touch event constants
        private const val LONG_PRESS_TIMEOUT = 500L
        private const val MIN_SWIPE_DISTANCE = 50f
        
        // Swipe detection thresholds
        // Increased from 12f to 45f to prevent fast taps from being misinterpreted as swipes
        private const val SWIPE_START_THRESHOLD = 45f  // Increased from 12f - allows sloppy fast taps
        private const val MIN_SWIPE_TIME_MS = 100L    // Increased from 80L - gives more time for valid swipes
        private const val MIN_SWIPE_DISTANCE_PX = 45f // Increased from 40f - matches start threshold
    }
    
    /**
     * Callback interfaces for integration with AIKeyboardService
     */
    interface SwipeListener {
        fun onSwipeDetected(sequence: List<Int>, normalizedPath: List<Pair<Float, Float>>, isPreview: Boolean = false)
        fun onSwipeStarted()
        fun onSwipeEnded()
    }
    
    interface SuggestionUpdateListener {
        fun onSuggestionsRequested(prefix: String)
    }
    
    interface AutocorrectListener {
        fun onAutocorrectCommit(original: String, corrected: String)
    }
    
    interface InputConnectionProvider {
        fun getCurrentInputConnection(): android.view.inputmethod.InputConnection?
    }

    /**
     * Display mode enum
     */
    enum class DisplayMode {
        TYPING,      // Show keyboard grid
        PANEL        // Show feature panel
    }

    /**
     * Keyboard layout modes for adaptive UI
     */
    enum class LayoutMode { 
        NORMAL, 
        ONE_HANDED_LEFT, 
        ONE_HANDED_RIGHT, 
        FLOATING 
    }
    
    enum class TapEffectStyle {
        NONE,
        RIPPLE,
        GLOW,
        BOUNCE;

        companion object {
            fun fromPreference(value: String): TapEffectStyle {
                val normalized = value.lowercase()
                if (normalized == "none") return NONE
                return values().firstOrNull { it != NONE && it.name.equals(value, ignoreCase = true) } ?: RIPPLE
            }
        }
    }

    /**
     * Dynamic key model
     * ⚡ PERFORMANCE: Includes cached RectF to avoid object allocation during onDraw
     */
    data class DynamicKey(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val label: String,
        val code: Int,
        val longPressOptions: List<String>? = null,
        val keyType: String = "regular",
        val hintLabel: String? = null,
        // ⚡ Cached rect to eliminate GC stutter - calculated once during buildKeys()
        val rect: RectF = RectF()
    )

    // UI containers
    private val toolbarContainer: LinearLayout
    private val suggestionContainer: LinearLayout
    private val bodyContainer: FrameLayout

    // Current state
    private var currentMode = DisplayMode.TYPING
    private var currentLayoutMode = LayoutMode.NORMAL
    private var currentLayout: LanguageLayoutAdapter.LayoutModel? = null
    private var dynamicKeys = mutableListOf<DynamicKey>()
    private var currentPanelView: View? = null
    
    // Panel manager for toolbar functionality
    private var panelManager: UnifiedPanelManager? = null
    
    // Listeners for integration
    private var swipeListener: SwipeListener? = null
    private var swipeEnabled = true
    private var suggestionUpdateListener: SuggestionUpdateListener? = null
    private var autocorrectListener: AutocorrectListener? = null
    private var inputConnectionProvider: InputConnectionProvider? = null
    private var suggestionController: UnifiedSuggestionController? = null
    
    // Current word tracking for suggestions
    private var currentWord = StringBuilder()
    private var lastProvidedSuggestions = emptyList<String>()
    
    // Suggestion display count (3 or 4 based on user preference)
    private var suggestionDisplayCount = 3
    
    private val suggestionViews = mutableListOf<TextView>()
    private val suggestionSeparators = mutableListOf<View>()
    private var suggestionSlotState: List<SuggestionSlotState> = emptyList()
    private var suggestionsEnabled: Boolean = true
    private val toolbarButtons = mutableListOf<ImageButton>()
    private var clipboardToolbarButton: ImageButton? = null
    private var clipboardButtonVisible: Boolean = true
    private var tapEffectStyle: TapEffectStyle = TapEffectStyle.NONE
    private var tapEffectsEnabled: Boolean = false
    private var lastEditorText: String = ""

    private var gestureSettings: GestureSettings = GestureSettings.DEFAULT
    private var gestureHandler: ((GestureSource) -> Unit)? = null
    private val density = context.resources.displayMetrics.density
    private var swipeDistanceThresholdPx: Float = MIN_SWIPE_DISTANCE_PX
    private var swipeVelocityThresholdPxPerSec: Float = 1900f * density
    private var showGlideTrailSetting: Boolean = true
    private var glideTrailFadeMs: Long = 200L
    private val trailHandler = Handler(Looper.getMainLooper())
    private var trailRunnable: Runnable? = null

    // Current keyboard mode and language
    var currentKeyboardModeEnum = LanguageLayoutAdapter.KeyboardMode.LETTERS
    var currentLangCode = "en"
    
    // Public accessor for layout controller compatibility
    var currentKeyboardMode: LanguageLayoutAdapter.KeyboardMode
        get() = currentKeyboardModeEnum
        set(value) { currentKeyboardModeEnum = value }
    
    // Handler for UI operations
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    // Theme paints (cached)
    private var keyTextPaint: Paint = themeManager.createKeyTextPaint()
    private var spaceLabelPaint: Paint = themeManager.createSpaceLabelPaint()

    // Configuration
    private var showLanguageOnSpace = true
    private var currentLanguageLabel = "English"
    private var labelScaleMultiplier = 1.0f
    private var borderlessMode = false
    private var hintedNumberRow = false
    private var hintedSymbols = true
    private var oneHandedModeEnabled = false
    private var oneHandedSide: String = "right"
    private var oneHandedWidthPct: Float = 0.75f
    private var numberRowActive = false
    private var instantLongPressSelectFirst = true
    private var longPressDelayMs: Long = 200L // Configurable long press delay
    // Tuned to mirror CleverType row density and gutters
    private var keySpacingVerticalDp = 1
    private var keySpacingHorizontalDp = 0
    private var edgePaddingDp = 4
    private var verticalPaddingDp = 2

    // Touch handling
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressKey: DynamicKey? = null
    private var accentPopup: PopupWindow? = null

    // Keyboard grid view (child of bodyContainer)
    private var keyboardGridView: KeyboardGridView? = null
    private var oneHandedControlsContainer: LinearLayout? = null
    private var oneHandedCollapseButton: ImageButton? = null
    private var oneHandedSwapButton: ImageButton? = null
    private var emojiSearchOverlayActive: Boolean = false
    
    // Swipe gesture tracking
    private val fingerPoints = mutableListOf<FloatArray>()
    private var isSwipeInProgress = false
    private var swipeStartTime = 0L
    
    // Spacebar and backspace gesture tracking
    private var spacebarDownX = 0f
    private var spacebarDownY = 0f
    private var backspaceDownX = 0f
    
    // Firebase language readiness
    private var isLanguageReady = true

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Apply initial background from theme
        background = themeManager.createKeyboardBackground()

        // ✅ CORRECT ORDER: 1. Toolbar, 2. Suggestions, 3. Keyboard
        
        // 1. Create toolbar container with modern icons
        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.toolbar_height)
        toolbarContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                toolbarHeight
            )
            gravity = Gravity.CENTER_VERTICAL
            background = themeManager.createToolbarBackground()
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
        }
        addView(toolbarContainer)
        
        // Create modern toolbar with full icon parity
        createModernToolbar()

        // 2. Create suggestion container - TRANSPARENT, text only
        val suggestionHeight = resources.getDimensionPixelSize(R.dimen.suggestion_bar_height)
        val suggestionPadding = resources.getDimensionPixelSize(R.dimen.suggestion_padding)
        suggestionContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                suggestionHeight
            )
            gravity = Gravity.CENTER_VERTICAL
            visibility = VISIBLE
            if (themeManager.isImageBackground()) {
                setBackgroundColor(Color.TRANSPARENT)
            } else {
                background = themeManager.createSuggestionBarBackground()
            }
            val verticalPadding = dpToPx(2)
            setPadding(suggestionPadding, verticalPadding, suggestionPadding, verticalPadding)
        }
        addView(suggestionContainer)
        
        // Add default suggestion items with auto-commit styling
        createDefaultSuggestions()

        // 3. Create body container (keyboard or panel)
        bodyContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f // Fill remaining space
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        addView(bodyContainer)

        // Register for theme changes
        themeManager.addThemeChangeListener(object : ThemeManager.ThemeChangeListener {
            override fun onThemeChanged(theme: com.kvive.keyboard.themes.KeyboardThemeV2, palette: ThemePaletteV2) {
                onThemeChangedInternal(palette)
            }
        })

        Log.d(TAG, "✅ UnifiedKeyboardView V2 initialized with full swipe pipeline")
    }

    // ========================================
    // PUBLIC API: Mode Switching
    // ========================================

    /**
     * Show typing layout (keyboard grid)
     */
    fun showTypingLayout(model: LanguageLayoutAdapter.LayoutModel) {
        panelManager?.markPanelClosed()
        currentMode = DisplayMode.TYPING
        currentLayout = model
        currentLangCode = model.languageCode
        emojiSearchOverlayActive = false

        toolbarContainer.visibility = VISIBLE
        suggestionContainer.visibility = if (suggestionsEnabled) VISIBLE else GONE

        // Hide panel if visible
        currentPanelView?.visibility = GONE

        // Build keyboard grid
        buildKeyboardGrid(model)

        // Reset suggestion state for new language/layout
        lastProvidedSuggestions = emptyList()
        updateSuggestions(emptyList())

        // Recalculate height
        recalcHeight()

        Log.d(TAG, "✅ Showing typing layout: ${model.languageCode} [${currentKeyboardModeEnum}]")
    }
    
    /**
     * ⚡ PERFORMANCE: Update key labels for shift state without rebuilding entire layout
     * This is much faster than rebuilding the entire keyboard grid
     * 
     * @param isUpperCase Whether keys should be uppercase
     * @param isCapsLock Whether caps lock is enabled (affects shift key appearance)
     * @return true if update was successful (lightweight path), false if fallback needed
     */
    fun updateKeyLabelsForShiftState(isUpperCase: Boolean, isCapsLock: Boolean): Boolean {
        // ⚡ PERFORMANCE: Only update if keyboard grid exists (UnifiedKeyboardView)
        // SwipeKeyboardView handles shift state visually without needing label updates
        return keyboardGridView?.updateKeyLabelsForShift(isUpperCase, isCapsLock) ?: false
    }
    
    /**
     * ⚡ PERFORMANCE: Check if this view can do lightweight shift updates
     * Returns false for SwipeKeyboardView (which handles shift differently)
     */
    fun supportsLightweightShiftUpdate(): Boolean {
        return keyboardGridView != null
    }

    /**
     * Show feature panel (AI, Emoji, Clipboard, etc.)
     */
    fun showPanel(panelView: View) {
        currentMode = DisplayMode.PANEL

        // Clear keyboard grid
        dynamicKeys.clear()

        toolbarContainer.visibility = GONE
        val currentPanelType = panelManager?.getCurrentPanelType()
        val isEmojiPanel = currentPanelType == UnifiedPanelManager.PanelType.EMOJI
        val isSettingsPanel = currentPanelType == UnifiedPanelManager.PanelType.SETTINGS
        val isAIPanel = currentPanelType == UnifiedPanelManager.PanelType.AI_ASSISTANT
        val isGrammarPanel = currentPanelType == UnifiedPanelManager.PanelType.GRAMMAR
        val isTonePanel = currentPanelType == UnifiedPanelManager.PanelType.TONE
        val isClipboardPanel = currentPanelType == UnifiedPanelManager.PanelType.CLIPBOARD
        val overlayActive = emojiSearchOverlayActive && isEmojiPanel
        // Hide suggestion strip when showing emoji, settings, AI, grammar, or tone panels
        suggestionContainer.visibility = if (
            isEmojiPanel ||
            isSettingsPanel ||
            isAIPanel ||
            isGrammarPanel ||
            isTonePanel ||
            isClipboardPanel ||
            overlayActive
        ) GONE else if (suggestionsEnabled) VISIBLE else GONE
        panelManager?.setInputText(lastEditorText)

        keyboardGridView?.visibility = if (overlayActive) VISIBLE else GONE

        // Remove old panel if any
        currentPanelView?.let { bodyContainer.removeView(it) }

        // Add new panel with one-handed mode support
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Apply one-handed mode sizing if enabled
        if (oneHandedModeEnabled) {
            val panelWidth = oneHandPanelWidthPx()
            val gap = dpToPx(8)
            val screenWidth = resources.displayMetrics.widthPixels
            val targetWidth = (screenWidth * oneHandedWidthPct).roundToInt()
            val containerWidth = if (bodyContainer.width > 0) bodyContainer.width else screenWidth
            val maxPanelWidth = (containerWidth - (panelWidth + gap * 2)).coerceAtLeast((containerWidth * 0.5f).toInt())
            val desiredWidth = targetWidth.coerceAtMost(maxPanelWidth)
            
            panelParams.width = desiredWidth
            panelParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            if (oneHandedSide == "left") {
                panelParams.gravity = Gravity.START or Gravity.BOTTOM
                panelParams.marginStart = gap
                panelParams.marginEnd = panelWidth + gap
            } else {
                panelParams.gravity = Gravity.END or Gravity.BOTTOM
                panelParams.marginStart = panelWidth + gap
                panelParams.marginEnd = gap
            }
            panelParams.topMargin = gap
            panelParams.bottomMargin = gap
        }
        
        bodyContainer.addView(panelView, panelParams)
        panelView.visibility = VISIBLE

        currentPanelView = panelView

        // Recalculate height
        recalcHeight()

        Log.d(TAG, "✅ Showing panel (keyboard grid hidden)${if (oneHandedModeEnabled) " [One-handed: ${oneHandedSide}]" else ""}")
    }

    /**
     * Return to typing mode
     */
    /**
     * Return to typing mode
     * ⚡ PERFORMANCE: Only rebuilds if layout changed, otherwise just switches visibility
     */
    fun backToTyping() {
        // ⚡ PERFORMANCE: If already in typing mode with same layout, just ensure visibility
        if (currentMode == DisplayMode.TYPING && currentLayout != null) {
            toolbarContainer.visibility = VISIBLE
            suggestionContainer.visibility = if (suggestionsEnabled) VISIBLE else GONE
            currentPanelView?.visibility = GONE
            keyboardGridView?.visibility = VISIBLE
            Log.d(TAG, "⚡ Fast path: Already in typing mode, just updated visibility")
            return
        }
        
        // Full rebuild only if needed
        currentLayout?.let { showTypingLayout(it) }
    }

    fun setEmojiSearchActive(active: Boolean) {
        val isEmojiPanel = panelManager?.getCurrentPanelType() == UnifiedPanelManager.PanelType.EMOJI
        emojiSearchOverlayActive = active
        if (currentMode == DisplayMode.PANEL && isEmojiPanel) {
            val grid = keyboardGridView
            if (grid != null) {
                if (active) {
                    // Move keyboard grid to search container
                    val searchContainer = panelManager?.getEmojiSearchKeyboardContainer()
                    if (searchContainer != null) {
                        // Remove from current parent
                        (grid.parent as? ViewGroup)?.removeView(grid)
                        
                        // Add to search container with MATCH_PARENT layout params
                        val params = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        searchContainer.addView(grid, params)
                        grid.visibility = VISIBLE
                        
                        Log.d(TAG, "✅ Moved keyboard grid to emoji search container")
                    }
                } else {
                    // Move keyboard grid back to body container
                    (grid.parent as? ViewGroup)?.removeView(grid)
                    val params = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    bodyContainer.addView(grid, params)
                    grid.visibility = GONE
                    
                    Log.d(TAG, "✅ Moved keyboard grid back to body container")
                }
                
                // Refresh keyboard layout if showing
                if (active) {
                    currentLayout?.let { showTypingLayout(it) }
                }
            }
            
            // Always hide suggestions when emoji panel is active
            suggestionContainer.visibility = GONE
        }
    }
    
    /**
     * Toggle keyboard layout mode (normal, one-handed, floating)
     */
    fun toggleMode(newMode: LayoutMode) {
        if (newMode == LayoutMode.FLOATING) {
            Log.d(TAG, "ℹ️ Floating keyboard mode is disabled")
            return
        }

        if (newMode == LayoutMode.NORMAL || newMode == currentLayoutMode) {
            setOneHandedMode(false, oneHandedSide, oneHandedWidthPct)
            Log.d(TAG, "✅ Keyboard mode changed to: NORMAL")
            return
        }

        when (newMode) {
            LayoutMode.ONE_HANDED_LEFT -> setOneHandedMode(true, "left", oneHandedWidthPct)
            LayoutMode.ONE_HANDED_RIGHT -> setOneHandedMode(true, "right", oneHandedWidthPct)
            else -> setOneHandedMode(false, oneHandedSide, oneHandedWidthPct)
        }
    }

    // ========================================
    // PUBLIC API: Configuration
    // ========================================

    /**
     * Set current language label and readiness status
     */
    fun setCurrentLanguage(label: String, isReady: Boolean = true) {
        currentLanguageLabel = label
        isLanguageReady = isReady
        updateLanguageBadge()
        invalidate()
    }

    /**
     * Enable/disable language label on space
     */
    fun setShowLanguageOnSpace(enabled: Boolean) {
        showLanguageOnSpace = enabled
        invalidate()
    }

    /**
     * Set label scale multiplier
     */
    fun setLabelScale(multiplier: Float) {
        labelScaleMultiplier = multiplier.coerceIn(0.8f, 1.3f)
        keyboardGridView?.setLabelScale(labelScaleMultiplier)
        invalidate()
    }

    /**
     * Enable/disable borderless key mode
     */
    /**
     * ⚡ PERFORMANCE: Only rebuilds if structure changes, otherwise just invalidates
     */
    fun setBorderless(enabled: Boolean) {
        if (borderlessMode == enabled) return
        borderlessMode = enabled
        // ⚡ Borderless mode only affects visual appearance, not structure
        keyboardGridView?.invalidate() // Just redraw, don't rebuild
    }

    /**
     * Toggle hinted number row (numeric hints on top row)
     * ⚡ PERFORMANCE: Only affects visual hints, not structure
     */
    fun setHintedNumberRow(enabled: Boolean) {
        if (hintedNumberRow == enabled) return
        hintedNumberRow = enabled
        // ⚡ Hints only affect visual display, not key positions
        keyboardGridView?.invalidate() // Just redraw, don't rebuild
    }

    /**
     * Toggle hinted symbols (alternate character hints)
     * ⚡ PERFORMANCE: Only affects visual hints, not structure
     */
    fun setHintedSymbols(enabled: Boolean) {
        if (hintedSymbols == enabled) return
        hintedSymbols = enabled
        // ⚡ Hints only affect visual display, not key positions
        keyboardGridView?.invalidate() // Just redraw, don't rebuild
    }

    /**
     * ⚡ PERFORMANCE: Number row change requires rebuild (structure changes)
     */
    fun setNumberRowEnabled(enabled: Boolean) {
        if (numberRowActive == enabled) return
        numberRowActive = enabled
        heightManager.setNumberRowEnabled(enabled)
        // ⚡ Number row changes structure, so rebuild is necessary
        rebuildKeyboardGrid()
    }

    fun setInstantLongPressSelectFirst(enabled: Boolean) {
        if (instantLongPressSelectFirst == enabled) return
        instantLongPressSelectFirst = enabled
        keyboardGridView?.setInstantLongPressSelectFirst(enabled)
    }

    /**
     * Set key spacing
     */
    /**
     * ⚡ PERFORMANCE: Key spacing changes require rebuild (affects key positions)
     */
    fun setKeySpacing(verticalDp: Int, horizontalDp: Int) {
        val clampedVertical = verticalDp.coerceAtLeast(0)
        val clampedHorizontal = horizontalDp.coerceAtLeast(0)
        if (clampedVertical == keySpacingVerticalDp && clampedHorizontal == keySpacingHorizontalDp) return

        keySpacingVerticalDp = clampedVertical
        keySpacingHorizontalDp = clampedHorizontal
        // ⚡ Spacing affects key positions, rebuild is necessary
        rebuildKeyboardGrid()
    }

    fun setOneHandedMode(enabled: Boolean, side: String = "right", widthPct: Float = 0.75f) {
        val clampedPct = widthPct.coerceIn(0.6f, 0.9f)
        val normalizedSide = if (side.equals("left", ignoreCase = true)) "left" else "right"
        if (keyboardGridView == null) {
            oneHandedModeEnabled = enabled
            oneHandedSide = normalizedSide
            oneHandedWidthPct = clampedPct
            currentLayoutMode = if (enabled) {
                if (normalizedSide == "left") LayoutMode.ONE_HANDED_LEFT else LayoutMode.ONE_HANDED_RIGHT
            } else {
                LayoutMode.NORMAL
            }
            return
        }

        if (!enabled) {
            oneHandedModeEnabled = false
            oneHandedSide = "right"
            oneHandedWidthPct = 0.75f
            currentLayoutMode = LayoutMode.NORMAL
            updateOneHandedChrome(false, normalizedSide, 0)
            recalcHeight()
            return
        }

        oneHandedModeEnabled = true
        oneHandedSide = normalizedSide
        oneHandedWidthPct = clampedPct
        currentLayoutMode = if (normalizedSide == "left") LayoutMode.ONE_HANDED_LEFT else LayoutMode.ONE_HANDED_RIGHT

        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = (screenWidth * clampedPct).roundToInt()
        updateOneHandedChrome(true, normalizedSide, targetWidth)
        recalcHeight()
    }

    private fun updateOneHandedChrome(enabled: Boolean, side: String, targetWidth: Int) {
        val grid = keyboardGridView ?: return
        val panelWidth = oneHandPanelWidthPx()
        val gap = dpToPx(8)

        val params = grid.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

        if (enabled) {
            val containerWidth = if (bodyContainer.width > 0) bodyContainer.width else resources.displayMetrics.widthPixels
            val maxGridWidth = (containerWidth - (panelWidth + gap * 2)).coerceAtLeast((containerWidth * 0.5f).toInt())
            val desiredWidth = targetWidth.coerceAtMost(maxGridWidth)

            params.width = desiredWidth
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            if (side == "left") {
                params.gravity = Gravity.START or Gravity.BOTTOM
                params.marginStart = gap
                params.marginEnd = panelWidth + gap
            } else {
                params.gravity = Gravity.END or Gravity.BOTTOM
                params.marginStart = panelWidth + gap
                params.marginEnd = gap
            }
            params.topMargin = gap
            params.bottomMargin = gap
            grid.layoutParams = params

            // ✅ FIX: Also resize toolbar and suggestions to match keyboard width
            val toolbarParams = toolbarContainer.layoutParams as? LayoutParams
            if (toolbarParams != null) {
                toolbarParams.width = desiredWidth
                toolbarParams.gravity = if (side == "left") Gravity.START else Gravity.END
                if (side == "left") {
                    toolbarParams.leftMargin = gap
                    toolbarParams.rightMargin = panelWidth + gap
                } else {
                    toolbarParams.leftMargin = panelWidth + gap
                    toolbarParams.rightMargin = gap
                }
                toolbarContainer.layoutParams = toolbarParams
            }

            val suggestionParams = suggestionContainer.layoutParams as? LayoutParams
            if (suggestionParams != null) {
                suggestionParams.width = desiredWidth
                suggestionParams.gravity = if (side == "left") Gravity.START else Gravity.END
                if (side == "left") {
                    suggestionParams.leftMargin = gap
                    suggestionParams.rightMargin = panelWidth + gap
                } else {
                    suggestionParams.leftMargin = panelWidth + gap
                    suggestionParams.rightMargin = gap
                }
                suggestionContainer.layoutParams = suggestionParams
            }
            
            // ✅ FIX: Resize current panel if visible (emoji, clipboard, AI, etc.)
            currentPanelView?.let { panel ->
                val panelParams = panel.layoutParams as? FrameLayout.LayoutParams
                if (panelParams != null) {
                    panelParams.width = desiredWidth
                    panelParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                    if (side == "left") {
                        panelParams.gravity = Gravity.START or Gravity.BOTTOM
                        panelParams.marginStart = gap
                        panelParams.marginEnd = panelWidth + gap
                    } else {
                        panelParams.gravity = Gravity.END or Gravity.BOTTOM
                        panelParams.marginStart = panelWidth + gap
                        panelParams.marginEnd = gap
                    }
                    panelParams.topMargin = gap
                    panelParams.bottomMargin = gap
                    panel.layoutParams = panelParams
                    panel.requestLayout()
                }
            }

            // Use theme's keyboard background for clean, unified UI
            bodyContainer.background = themeManager.createKeyboardBackground()

            val controls = ensureOneHandedControls()
            val controlsParams = controls.layoutParams as FrameLayout.LayoutParams
            controlsParams.width = panelWidth
            controlsParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            controlsParams.gravity = if (side == "left") Gravity.END else Gravity.START
            controls.layoutParams = controlsParams
            controls.visibility = View.VISIBLE
            controls.bringToFront()

            updateOneHandedButtons(side)
        } else {
            // Reset keyboard grid
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            params.marginStart = 0
            params.marginEnd = 0
            params.topMargin = 0
            params.bottomMargin = 0
            grid.layoutParams = params

            // ✅ FIX: Reset toolbar and suggestions to full width
            val toolbarParams = toolbarContainer.layoutParams as? LayoutParams
            if (toolbarParams != null) {
                toolbarParams.width = LayoutParams.MATCH_PARENT
                toolbarParams.gravity = Gravity.NO_GRAVITY
                toolbarParams.leftMargin = 0
                toolbarParams.rightMargin = 0
                toolbarContainer.layoutParams = toolbarParams
            }

            val suggestionParams = suggestionContainer.layoutParams as? LayoutParams
            if (suggestionParams != null) {
                suggestionParams.width = LayoutParams.MATCH_PARENT
                suggestionParams.gravity = Gravity.NO_GRAVITY
                suggestionParams.leftMargin = 0
                suggestionParams.rightMargin = 0
                suggestionContainer.layoutParams = suggestionParams
            }
            
            // ✅ FIX: Reset current panel to full width
            currentPanelView?.let { panel ->
                val panelParams = panel.layoutParams as? FrameLayout.LayoutParams
                if (panelParams != null) {
                    panelParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                    panelParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                    panelParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    panelParams.marginStart = 0
                    panelParams.marginEnd = 0
                    panelParams.topMargin = 0
                    panelParams.bottomMargin = 0
                    panel.layoutParams = panelParams
                    panel.requestLayout()
                }
            }

            bodyContainer.setBackgroundColor(Color.TRANSPARENT)
            oneHandedControlsContainer?.visibility = View.GONE
        }

        bodyContainer.invalidate()
        bodyContainer.requestLayout()
        toolbarContainer.invalidate()
        toolbarContainer.requestLayout()
        suggestionContainer.invalidate()
        suggestionContainer.requestLayout()
    }

    private fun ensureOneHandedControls(): LinearLayout {
        if (oneHandedControlsContainer != null) return oneHandedControlsContainer!!

        val panelPadding = dpToPx(12)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Use theme's keyboard background for clean, unified UI
            background = themeManager.createKeyboardBackground()
            setPadding(panelPadding, panelPadding, panelPadding, panelPadding)
            visibility = View.GONE
        }

        val collapseBtn = createOneHandedButton(
            iconRes = R.drawable.ic_one_hand_full,
            contentDesc = context.getString(R.string.one_hand_expand)
        ) {
            AIKeyboardService.getInstance()?.applyOneHandedMode(false, oneHandedSide, oneHandedWidthPct)
                ?: setOneHandedMode(false, oneHandedSide, oneHandedWidthPct)
        }

        val switchBtn = createOneHandedButton(
            iconRes = R.drawable.ic_one_hand_switch,
            contentDesc = context.getString(R.string.one_hand_switch_side)
        ) {
            val newSide = if (oneHandedSide == "left") "right" else "left"
            AIKeyboardService.getInstance()?.applyOneHandedMode(true, newSide, oneHandedWidthPct)
                ?: setOneHandedMode(true, newSide, oneHandedWidthPct)
        }

        panel.addView(collapseBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        panel.addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        panel.addView(switchBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        panel.addView(View(context), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        oneHandedControlsContainer = panel
        oneHandedCollapseButton = collapseBtn
        oneHandedSwapButton = switchBtn
        bodyContainer.addView(panel, FrameLayout.LayoutParams(
            oneHandPanelWidthPx(),
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return panel
    }

    private fun updateOneHandedButtons(side: String) {
        // Use theme's text color for icons for clean, unified UI
        val tint = themeManager.getTextColor()
        oneHandedCollapseButton?.imageTintList = ColorStateList.valueOf(tint)
        oneHandedSwapButton?.apply {
            imageTintList = ColorStateList.valueOf(tint)
            scaleX = if (side == "left") -1f else 1f
        }
        oneHandedControlsContainer?.let { container ->
            // Use theme's keyboard background for clean, unified UI
            val radius = dpToPx(16).toFloat()
            val bgColor = themeManager.getKeyboardBackgroundColor()
            val drawable = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = if (side == "left") {
                    floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                } else {
                    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                }
            }
            container.background = drawable
        }
    }

    private fun createOneHandedButton(
        @DrawableRes iconRes: Int,
        contentDesc: String,
        onClick: () -> Unit
    ): ImageButton {
        // Create ripple drawable using theme colors for clean, unified UI
        val palette = themeManager.getCurrentPalette()
        val rippleColor = ColorUtils.setAlphaComponent(
            if (palette.usesImageBackground) palette.specialAccent else palette.keyText,
            51 // ~20% opacity for subtle ripple
        )
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val rippleDrawable = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            null, // No background - transparent
            mask
        )
        
        val button = ImageButton(context).apply {
            setImageResource(iconRes)
            background = rippleDrawable
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            contentDescription = contentDesc
            isFocusable = true
            isClickable = true
        }
        button.setOnClickListener { onClick.invoke() }
        return button
    }

    private fun oneHandPanelWidthPx(): Int = dpToPx(64)

    /**
     * Adjust horizontal gutters at the screen edges (left/right)
     */
    /**
     * ⚡ PERFORMANCE: Edge padding changes require rebuild (affects key positions)
     */
    fun setEdgePadding(dp: Int) {
        val clamped = dp.coerceAtLeast(0)
        if (clamped == edgePaddingDp) return

        edgePaddingDp = clamped
        // ⚡ Padding affects key positions, rebuild is necessary
        rebuildKeyboardGrid()
    }

    /**
     * Rebuild the grid so spacing/padding changes are reflected immediately
     */
    private fun rebuildKeyboardGrid() {
        currentLayout?.let { buildKeyboardGrid(it) } ?: run {
            keyboardGridView?.invalidate()
        }
        requestLayout()
    }

    // ========================================
    // PUBLIC API: Listeners
    // ========================================

    fun setSwipeListener(listener: SwipeListener?) {
        this.swipeListener = listener
    }

    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
        if (!enabled) {
            resetSwipe()
        }
    }
    
    fun setSuggestionUpdateListener(listener: SuggestionUpdateListener?) {
        this.suggestionUpdateListener = listener
    }
    
    fun setAutocorrectListener(listener: AutocorrectListener?) {
        this.autocorrectListener = listener
    }
    
    fun setInputConnectionProvider(provider: InputConnectionProvider?) {
        this.inputConnectionProvider = provider
    }
    
    fun setPanelManager(manager: UnifiedPanelManager?) {
        this.panelManager = manager
        manager?.setInputText(lastEditorText)
    }

    fun setClipboardButtonVisible(visible: Boolean) {
        clipboardButtonVisible = visible
        mainHandler.post { applyClipboardButtonVisibility() }
    }
    
    fun setSuggestionController(controller: UnifiedSuggestionController?) {
        this.suggestionController = controller
    }

    fun updateEditorTextSnapshot(text: String) {
        lastEditorText = text
        panelManager?.setInputText(text)
    }
    
    /**
     * Set the number of suggestions to display (3 or 4)
     */
    /**
     * ✅ NEW: Enable or disable key preview popups
     */
    fun setPreviewEnabled(enabled: Boolean) {
        keyboardGridView?.setPreviewEnabled(enabled)
        Log.d(TAG, "✅ Key preview set to: $enabled")
    }
    
    /**
     * Set long press delay in milliseconds
     */
    fun setLongPressDelay(delayMs: Int) {
        val clampedDelay = delayMs.coerceIn(150, 600).toLong()
        if (longPressDelayMs == clampedDelay) return
        longPressDelayMs = clampedDelay
        keyboardGridView?.setLongPressDelay(clampedDelay)
        Log.d(TAG, "✅ Long press delay set to: ${clampedDelay}ms")
    }
    
    fun setSuggestionDisplayCount(count: Int) {
        val clamped = count.coerceIn(1, 4)
        if (clamped == suggestionDisplayCount) return
        
        suggestionDisplayCount = clamped
        mainHandler.post {
            ensureSuggestionSlots(forceRebuild = true)
            val slotData = buildSuggestionSlotState(lastProvidedSuggestions)
            suggestionSlotState = slotData
            if (suggestionsEnabled) {
                renderSuggestionSlots(slotData)
            }
        }
    }

    fun setTapEffectStyle(style: String, enabled: Boolean) {
        val parsed = TapEffectStyle.fromPreference(style)
        val shouldEnable = enabled && parsed != TapEffectStyle.NONE
        if (tapEffectStyle == parsed && tapEffectsEnabled == shouldEnable) return

        tapEffectStyle = parsed
        tapEffectsEnabled = shouldEnable
        keyboardGridView?.setTapEffectStyle(parsed, shouldEnable)
        if (!shouldEnable) {
            keyboardGridView?.clearTapEffect()
        }
        invalidate()
    }

    private fun clearSwipeTrail() {
        keyboardGridView?.clearSwipeTrail()
    }

    fun updateGestureSettings(settings: GestureSettings, handler: ((GestureSource) -> Unit)?) {
        gestureSettings = settings
        gestureHandler = handler
        swipeDistanceThresholdPx = (settings.swipeDistanceThreshold * density).coerceAtLeast(5f)
        swipeVelocityThresholdPxPerSec = (settings.swipeVelocityThreshold * density).coerceAtLeast(100f)
        showGlideTrailSetting = settings.showGlideTrail
        glideTrailFadeMs = settings.glideTrailFadeMs.toLong().coerceAtLeast(0L)
        if (!showGlideTrailSetting) {
            clearSwipeTrail()
        }
    }

    // ========================================
    // PUBLIC API: Suggestions
    // ========================================
    
    /**
     * Update suggestions with auto-commit styling
     * Now enforces count and never creates ghost 4th slot
     */
    fun updateSuggestions(suggestions: List<String>) {
        try {
            val sanitized = suggestions.filter { it.isNotBlank() }
            lastProvidedSuggestions = sanitized
            val slotData = buildSuggestionSlotState(sanitized)
            suggestionSlotState = slotData

            mainHandler.post {
                try {
                    if (suggestionsEnabled) {
                        renderSuggestionSlots(slotData)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating suggestion container", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateSuggestions", e)
        }
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        if (suggestionsEnabled == enabled) return

        suggestionsEnabled = enabled

        mainHandler.post {
            suggestionContainer.visibility = if (enabled) VISIBLE else GONE
            if (!enabled) {
                suggestionViews.forEach { view ->
                    view.text = ""
                    view.isEnabled = false
                }
            } else {
                val slotData = buildSuggestionSlotState(lastProvidedSuggestions)
                suggestionSlotState = slotData
                renderSuggestionSlots(slotData)
            }
            recalcHeight()
        }
    }

    // ========================================
    // PRIVATE: Theme & Height Management
    // ========================================

    private fun onThemeChangedInternal(palette: ThemePaletteV2) {
        // Update cached paints
        keyTextPaint = themeManager.createKeyTextPaint()
        spaceLabelPaint = themeManager.createSpaceLabelPaint()

        // Update background
        background = themeManager.createKeyboardBackground()

        // Update toolbar background
        toolbarContainer.background = themeManager.createToolbarBackground()
        
        // Update suggestion bar background
        if (themeManager.isImageBackground()) {
            suggestionContainer.setBackgroundColor(Color.TRANSPARENT)
        } else {
            suggestionContainer.background = themeManager.createSuggestionBarBackground()
        }
        // Update toolbar buttons
        toolbarButtons.forEach { styleToolbarButton(it) }
        
        // Rebuild suggestions with new theme
        val refreshedSlots = buildSuggestionSlotState(lastProvidedSuggestions)
        suggestionSlotState = refreshedSlots
        renderSuggestionSlots(refreshedSlots)
        updateSuggestionSeparatorsColor(palette)

        // ⚡ PERFORMANCE: Only invalidate keyboard grid, don't rebuild structure
        // Theme changes don't require rebuilding key positions, just redrawing
        when (currentMode) {
            DisplayMode.TYPING -> {
                // Just invalidate - KeyboardGridView will redraw with new theme colors
                keyboardGridView?.invalidate()
            }
            DisplayMode.PANEL -> { 
                /* Panel already uses ThemeManager */ 
            }
        }

        invalidate()
        requestLayout()

        Log.d(TAG, "✅ Theme applied (lightweight update)")
    }

    /**
     * Recalculate and apply height
     */
    fun recalcHeight() {
        val includeExtras = currentMode != DisplayMode.PANEL
        val newHeight = heightManager.calculateKeyboardHeight(
            includeToolbar = includeExtras,
            includeSuggestions = includeExtras && suggestionsEnabled
        )

        layoutParams = layoutParams?.apply {
            height = newHeight
        } ?: LayoutParams(
            LayoutParams.MATCH_PARENT,
            newHeight
        )

        requestLayout()
        Log.d(TAG, "📐 Height recalculated: ${newHeight}px")
    }

    // ========================================
    // PRIVATE: Toolbar Creation
    // ========================================

    /**
     * Create modern toolbar with full icon parity
     */
    private fun createModernToolbar() {
        toolbarContainer.removeAllViews()
        toolbarButtons.clear()

        val toolbarView = LayoutInflater.from(context).inflate(R.layout.keyboard_toolbar_unified, toolbarContainer, false)

        val settingsButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_settings)
        val voiceButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_voice)
        val emojiButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_emoji)
        val clipboardButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_clipboard).also {
            clipboardToolbarButton = it
        }
        val aiButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_ai)
        val grammarButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_grammar)
        val toneButton = toolbarView.findViewById<ImageButton>(R.id.button_toolbar_tone)

        // Style left side buttons without boxes (plain icons)
        listOfNotNull(settingsButton, voiceButton, emojiButton, clipboardButton).forEach { button ->
            toolbarButtons.add(button)
            styleToolbarButton(button, withBox = false)
        }
        
        // Style right side buttons with boxes
        listOfNotNull(aiButton, grammarButton, toneButton).forEach { button ->
            toolbarButtons.add(button)
            styleToolbarButton(button, withBox = true)
        }

        settingsButton?.setOnClickListener {
            panelManager?.let { manager ->
                val settingsPanel = manager.buildPanel(UnifiedPanelManager.PanelType.SETTINGS)
                showPanel(settingsPanel)
            }
        }

        voiceButton?.setOnClickListener {
            val service = AIKeyboardService.getInstance()
            if (service != null) {
                service.startVoiceInputFromToolbar()
            } else {
                // Toast removed - voice input error logged only
                Log.d(TAG, "Voice input not available")
            }
        }

        emojiButton?.setOnClickListener {
            panelManager?.let { manager ->
                val emojiPanel = manager.buildPanel(UnifiedPanelManager.PanelType.EMOJI)
                showPanel(emojiPanel)
            }
        }

        aiButton?.setOnClickListener {
            panelManager?.let { manager ->
                val aiPanel = manager.buildPanel(UnifiedPanelManager.PanelType.AI_ASSISTANT)
                showPanel(aiPanel)
            }
        }
        
        clipboardButton?.setOnClickListener {
            panelManager?.let { manager ->
                val clipboardPanel = manager.buildPanel(UnifiedPanelManager.PanelType.CLIPBOARD)
                showPanel(clipboardPanel)
            }
        }

        grammarButton?.setOnClickListener {
            panelManager?.let { manager ->
                val grammarPanel = manager.buildPanel(UnifiedPanelManager.PanelType.GRAMMAR)
                showPanel(grammarPanel)
            }
        }

        toneButton?.setOnClickListener {
            panelManager?.let { manager ->
                val tonePanel = manager.buildPanel(UnifiedPanelManager.PanelType.TONE)
                showPanel(tonePanel)
            }
        }

        toolbarContainer.addView(toolbarView)
        applyClipboardButtonVisibility()
    }

    private fun styleToolbarButton(button: ImageButton, withBox: Boolean = false) {
        val padding = resources.getDimensionPixelSize(R.dimen.toolbar_button_padding)
        if (withBox) {
            // Right side icons: Use themed box background
            button.background = themeManager.createToolbarButtonDrawable()
            val tintColor = Color.WHITE
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(tintColor))
        } else {
            // Left side icons: No background, plain icons
            button.background = null
            val tintColor = themeManager.getToolbarTextColor()
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(tintColor))
        }
        
        // Adjust padding for better touch target
        button.setPadding(padding, padding, padding, padding)
    }

    private fun applyClipboardButtonVisibility() {
        clipboardToolbarButton?.visibility = if (clipboardButtonVisible) View.VISIBLE else View.GONE
    }

    // ========================================
    // PRIVATE: Suggestion Bar Creation
    // ========================================

    private data class SuggestionSlotState(
        val text: String,
        val isPrimary: Boolean,
        val fromResults: Boolean
    )
    
    /**
     * Create default suggestion items (shown on keyboard open)
     */
    private fun createDefaultSuggestions() {
        updateSuggestions(emptyList()) // Will trigger default suggestions
    }
    
    /**
     * Create a suggestion item - TRANSPARENT, text only
     */
    private fun createSuggestionItem(text: String, isPrimary: Boolean, onClick: () -> Unit): TextView {
        val palette = themeManager.getCurrentPalette()
        return TextView(context).apply {
            this.text = text
            // ✅ Use theme font for suggestions
            typeface = themeManager.createTypeface(palette.suggestionFontFamily, palette.suggestionFontBold, false)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.suggestion_text_size))
            gravity = Gravity.CENTER
            setTextColor(palette.suggestionText)
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.suggestion_padding) / 2
            val verticalPadding = dpToPx(2)
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            
            // ✅ No background - transparent
            background = null
            
            // ✅ No elevation
            elevation = 0f
            
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                val margin = resources.getDimensionPixelSize(R.dimen.suggestion_margin)
                setMargins(margin, 0, margin, 0)
            }
            
            isClickable = true
            isFocusable = true
            
            setOnClickListener { onClick() }
        }
    }

    private fun ensureSuggestionSlots(forceRebuild: Boolean = false) {
        if (!forceRebuild && suggestionViews.size == suggestionDisplayCount) {
            updateSuggestionSeparatorsColor()
            return
        }
        
        val palette = themeManager.getCurrentPalette()
        suggestionContainer.removeAllViews()
        suggestionViews.clear()
        suggestionSeparators.clear()
        
        repeat(suggestionDisplayCount) { index ->
            val slotView = createSuggestionItem("", false) {}
            slotView.isEnabled = false
            slotView.alpha = 1f
            suggestionContainer.addView(slotView)
            suggestionViews.add(slotView)
            
            if (index < suggestionDisplayCount - 1) {
                val separator = createSuggestionSeparator(palette)
                suggestionContainer.addView(separator)
                suggestionSeparators.add(separator)
            }
        }
        
        updateSuggestionSeparatorsColor(palette)
    }

    private fun extractSentenceStarterWords(maxWords: Int): List<String> {
        val snapshot = lastEditorText.ifBlank { currentWord.toString() }
        if (snapshot.isBlank()) return emptyList()
        
        // Look at the active sentence (after the last sentence-ending punctuation)
        val trimmed = snapshot.trimEnd()
        val lastDelimiterIndex = trimmed.indexOfLast { it == '.' || it == '!' || it == '?' || it == '\n' }
        val candidate = if (lastDelimiterIndex >= 0 && lastDelimiterIndex < trimmed.lastIndex) {
            trimmed.substring(lastDelimiterIndex + 1)
        } else {
            trimmed
        }
        
        val words = candidate
            .trim()
            .split(Regex("\\s+"))
            .map { it.trim('\"', '\'', '“', '”', '(', ')', ',', ';', ':', '.', '!', '?') }
            .filter { it.isNotBlank() }
        
        return words.take(maxWords)
    }

    private fun buildSuggestionSlotState(suggestions: List<String>): List<SuggestionSlotState> {
        val maxSlots = suggestionDisplayCount
        val slots = mutableListOf<SuggestionSlotState>()
        val hasResults = suggestions.isNotEmpty()
        val currentPrefix = currentWord.toString()
        val fallbackWords = if (hasResults) emptyList() else generateFallbackSuggestions(currentPrefix, maxSlots)
        var fallbackIndex = 0
        
        suggestions.take(maxSlots).forEachIndexed { index, text ->
            slots.add(
                SuggestionSlotState(
                    text = text,
                    isPrimary = hasResults && index == 0,
                    fromResults = true
                )
            )
        }
        
        while (slots.size < maxSlots) {
            val fallbackText = if (fallbackIndex < fallbackWords.size) {
                fallbackWords[fallbackIndex++]
            } else {
                ""
            }
            val isInteractive = fallbackText.isNotBlank()
            slots.add(
                SuggestionSlotState(
                    text = fallbackText,
                    isPrimary = fallbackIndex == 1 && isInteractive,
                    fromResults = isInteractive
                )
            )
        }
        
        return slots
    }

    private fun renderSuggestionSlots(slots: List<SuggestionSlotState>) {
        if (!suggestionsEnabled) {
            return
        }
        ensureSuggestionSlots()
        val palette = themeManager.getCurrentPalette()
        
        slots.forEachIndexed { index, slot ->
            val view = suggestionViews.getOrNull(index) ?: return@forEachIndexed
            view.text = slot.text
            val isInteractive = slot.fromResults && slot.text.isNotEmpty()
            
            // ✅ Simple text color - no theming, no accent colors
            view.setTextColor(palette.suggestionText)
            view.alpha = if (isInteractive) 1f else 0.5f  // Dim inactive suggestions
            view.isEnabled = isInteractive
            view.visibility = View.VISIBLE
            view.setCompoundDrawables(null, null, null, null)
            
            // ✅ No background - transparent
            view.background = null
            
            if (isInteractive) {
                view.setOnClickListener { commitSuggestionText(slot.text) }
            } else {
                view.setOnClickListener(null)
            }
        }
        
        Log.d(TAG, "✅ Updated suggestions: ${slots.count { it.fromResults }} active out of $suggestionDisplayCount slots")
    }

    private fun createSuggestionSeparator(palette: ThemePaletteV2): View {
        val separatorColor = ColorUtils.setAlphaComponent(palette.suggestionText, 48)
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                max(1, dpToPx(1)),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
            setBackgroundColor(separatorColor)
        }
    }

    private fun updateSuggestionSeparatorsColor(palette: ThemePaletteV2 = themeManager.getCurrentPalette()) {
        val separatorColor = ColorUtils.setAlphaComponent(palette.suggestionText, 48)
        suggestionSeparators.forEach { separator ->
            separator.setBackgroundColor(separatorColor)
        }
    }

    private fun generateFallbackSuggestions(prefix: String, maxSlots: Int): List<String> {
        if (prefix.isBlank()) return extractSentenceStarterWords(maxSlots)
        val normalized = prefix.trim().lowercase()
        val firstChar = normalized.firstOrNull() ?: return extractSentenceStarterWords(maxSlots)
        val contextSource = StringBuilder()
            .append(lastEditorText)
            .append(' ')
            .append(currentWord)
            .toString()

        if (contextSource.isBlank()) return extractSentenceStarterWords(maxSlots)

        val words = contextSource
            .split(Regex("\\s+"))
            .map { it.trim('"', '\'', '“', '”', '(', ')', ',', ';', ':', '.', '!', '?') }
            .filter { it.isNotBlank() }

        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (word in words.asReversed()) {
            val lower = word.lowercase()
            if (lower == normalized) continue
            if (lower.startsWith(normalized) || lower.firstOrNull() == firstChar) {
                if (seen.add(lower)) {
                    results.add(word)
                    if (results.size >= maxSlots) break
                }
            }
        }

        if (results.size < maxSlots) {
            val heuristics = mutableListOf(prefix)
            if (prefix.length > 1) {
                heuristics.add(prefix + "ing")
                heuristics.add(prefix + "ed")
                heuristics.add(prefix + "s")
            }
            for (candidate in heuristics) {
                val lower = candidate.lowercase()
                if (candidate.isNotBlank() && seen.add(lower)) {
                    results.add(candidate)
                    if (results.size >= maxSlots) break
                }
            }
        }

        return if (results.isNotEmpty()) results.take(maxSlots) else extractSentenceStarterWords(maxSlots)
    }

    private fun commitSuggestionText(suggestion: String) {
        // ✅ FIX: Call service's applySuggestion() to properly replace current word
        // Instead of adding characters one by one
        val service = AIKeyboardService.getInstance()
        if (service != null) {
            service.applySuggestion(suggestion)
            Log.d(TAG, "✅ Applied suggestion via service: '$suggestion'")
        } else {
            // Fallback: simulate typing (old behavior)
            suggestion.forEach { char ->
                onKeyCallback?.invoke(char.code, intArrayOf(char.code))
            }
            onKeyCallback?.invoke(32, intArrayOf(32))
            Log.d(TAG, "⚠️ Applied suggestion via fallback (service unavailable)")
        }
        
        currentWord.clear()
    }

    // ========================================
    // PRIVATE: Keyboard Grid Building
    // ========================================

    private fun buildKeyboardGrid(model: LanguageLayoutAdapter.LayoutModel) {
        // Remove old grid view if any
        keyboardGridView?.let { bodyContainer.removeView(it) }

        // Update height manager with number row state
        heightManager.setNumberRowEnabled(model.numberRow.isNotEmpty())

        // Create new keyboard grid view with swipe support
        keyboardGridView = KeyboardGridView(
            context = context,
            model = model,
            themeManager = themeManager,
            heightManager = heightManager,
            showLanguageOnSpace = showLanguageOnSpace,
            currentLanguageLabel = currentLanguageLabel,
            labelScaleMultiplier = labelScaleMultiplier,
            borderlessMode = borderlessMode,
            hintedNumberRow = hintedNumberRow,
            hintedSymbols = hintedSymbols,
            numberRowActive = numberRowActive,
            instantLongPressSelectFirst = instantLongPressSelectFirst,
            keySpacingVerticalDp = keySpacingVerticalDp,
            keySpacingHorizontalDp = keySpacingHorizontalDp,
            edgePaddingDp = edgePaddingDp,
            verticalEdgePaddingDp = verticalPaddingDp,
            onKeyCallback = onKeyCallback,
            parentView = this
        )
        keyboardGridView?.setTapEffectStyle(tapEffectStyle, tapEffectsEnabled)

        // Add to body container
        bodyContainer.addView(keyboardGridView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // Attach gesture controls
        attachGestureControls()

        // Apply RTL layout direction if needed
        val isRTL = model.direction.equals("RTL", ignoreCase = true)
        layoutDirection = if (isRTL) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR

        Log.d(TAG, "✅ Keyboard grid view created with swipe support")

        // Adjust overall height to reflect current layout (number row, etc.)
        recalcHeight()

        if (oneHandedModeEnabled) {
            setOneHandedMode(true, oneHandedSide, oneHandedWidthPct)
        }
    }

    // ========================================
    // PRIVATE: Swipe Gesture Pipeline
    // ========================================
    
    /**
     * Handle swipe touch events with normalized path generation
     */
    private fun handleSwipeTouch(event: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startSwipe(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                if (!isSwipeInProgress && fingerPoints.isNotEmpty()) {
                    val st = fingerPoints.first()
                    val dx = event.x - st[0]
                    val dy = event.y - st[1]
                    if (sqrt(dx * dx + dy * dy) > SWIPE_START_THRESHOLD && swipeEnabled) {
                        isSwipeInProgress = true
                        swipeListener?.onSwipeStarted()
                    }
                }
                if (isSwipeInProgress && swipeEnabled) continueSwipe(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> if (isSwipeInProgress) endSwipe()
            MotionEvent.ACTION_CANCEL -> resetSwipe()
        }
        return false
    }

    private fun startSwipe(x: Float, y: Float) {
        fingerPoints.clear()
        fingerPoints.add(floatArrayOf(x, y))
        swipeStartTime = System.currentTimeMillis()
        isSwipeInProgress = false
    }

    private fun continueSwipe(x: Float, y: Float) {
        fingerPoints.add(floatArrayOf(x, y))
    }

    private fun resetSwipe() {
        fingerPoints.clear()
        isSwipeInProgress = false
        if (swipeEnabled) {
            swipeListener?.onSwipeEnded()
        }
    }

    private fun endSwipe() {
        if (!swipeEnabled) {
            resetSwipe()
            return
        }
        val dur = System.currentTimeMillis() - swipeStartTime
        val dist = totalDistance()
        
        if (dur >= MIN_SWIPE_TIME_MS && dist >= MIN_SWIPE_DISTANCE_PX) {
            val normalized = normalizePath(fingerPoints)
            val keySeq = keyboardGridView?.resolveKeySequence(normalized) ?: emptyList()
            
            if (swipeEnabled) {
                swipeListener?.onSwipeDetected(keySeq, normalized, isPreview = false) // Final swipe
                handleSwipeSuggestions(keySeq, normalized)
            }
        }
        
        resetSwipe()
    }

    private fun totalDistance(): Float {
        var d = 0f
        for (i in 1 until fingerPoints.size) {
            val a = fingerPoints[i - 1]
            val b = fingerPoints[i]
            d += sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]))
        }
        return d
    }

    private fun normalizePath(points: List<FloatArray>): List<Pair<Float, Float>> {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        return points.map { Pair(it[0] / w, it[1] / h) }
    }

    private fun handleSwipeSuggestions(seq: List<Int>, path: List<Pair<Float, Float>>) {
        // Swipe suggestions are provided by AIKeyboardService via the swipe listener.
        // This helper is retained for future hook-ins (e.g. local previews) but is a no-op for now.
    }

    // ========================================
    // PRIVATE: Advanced Gestures
    // ========================================
    
    /**
     * Attach spacebar cursor and backspace swipe gestures
     */
    private fun attachGestureControls() {
        keyboardGridView?.let { gridView ->
            // Find spacebar and backspace keys
            val spacebar = gridView.findViewWithTag<View>("spacebar")
            val backspace = gridView.findViewWithTag<View>("key_backspace")

            val spaceLongPressHandler = Handler(Looper.getMainLooper())
            var spaceLongPressRunnable: Runnable? = null
            var spacebarGestureTriggered = false

            fun cancelSpaceLongPress() {
                spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
                spaceLongPressRunnable = null
            }

            spacebar?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        spacebarDownX = event.x
                        spacebarDownY = event.y
                        spacebarGestureTriggered = false
                        spaceLongPressRunnable = Runnable {
                            spacebarGestureTriggered = true
                            gestureHandler?.invoke(GestureSource.SPACE_LONG_PRESS)
                        }
                        spaceLongPressHandler.postDelayed(
                            spaceLongPressRunnable!!,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - spacebarDownX
                        val dy = event.y - spacebarDownY
                        val absDx = abs(dx)
                        val absDy = abs(dy)
                        val threshold = swipeDistanceThresholdPx

                        if (!spacebarGestureTriggered && gestureHandler != null) {
                            if (absDx > threshold && absDx > absDy) {
                                spacebarGestureTriggered = true
                                cancelSpaceLongPress()
                                val direction = if (dx > 0) GestureSource.SPACE_SWIPE_RIGHT else GestureSource.SPACE_SWIPE_LEFT
                                gestureHandler?.invoke(direction)
                            } else if (dy > threshold && absDy > absDx) {
                                spacebarGestureTriggered = true
                                cancelSpaceLongPress()
                                gestureHandler?.invoke(GestureSource.SPACE_SWIPE_DOWN)
                            } else if (absDx > threshold || absDy > threshold) {
                                cancelSpaceLongPress()
                            }
                        } else if (absDx > threshold || absDy > threshold) {
                            cancelSpaceLongPress()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTriggered = spacebarGestureTriggered
                        cancelSpaceLongPress()
                        spacebarGestureTriggered = false
                        if (event.action == MotionEvent.ACTION_UP && !wasTriggered) {
                            val spaceCode = ' '.code
                            onKeyCallback?.invoke(spaceCode, intArrayOf(spaceCode))
                        }
                        true
                    }
                    else -> true
                }
            }

            val backspaceLongPressHandler = Handler(Looper.getMainLooper())
            var backspaceLongPressRunnable: Runnable? = null
            var backspaceGestureTriggered = false

            fun cancelBackspaceLongPress() {
                backspaceLongPressRunnable?.let { backspaceLongPressHandler.removeCallbacks(it) }
                backspaceLongPressRunnable = null
            }

            backspace?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        backspaceDownX = event.x
                        backspaceGestureTriggered = false
                        backspaceLongPressRunnable = Runnable {
                            backspaceGestureTriggered = true
                            gestureHandler?.invoke(GestureSource.DELETE_LONG_PRESS)
                        }
                        backspaceLongPressHandler.postDelayed(
                            backspaceLongPressRunnable!!,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - backspaceDownX
                        val threshold = swipeDistanceThresholdPx
                        if (!backspaceGestureTriggered && dx < -threshold && gestureHandler != null) {
                            backspaceGestureTriggered = true
                            cancelBackspaceLongPress()
                            gestureHandler?.invoke(GestureSource.DELETE_SWIPE_LEFT)
                        } else if (abs(dx) > threshold) {
                            cancelBackspaceLongPress()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val wasTriggered = backspaceGestureTriggered
                        cancelBackspaceLongPress()
                        backspaceGestureTriggered = false
                        if (event.action == MotionEvent.ACTION_UP && !wasTriggered) {
                            inputConnectionProvider?.getCurrentInputConnection()?.let { ic ->
                                CursorAwareTextHandler.performBackspace(ic)
                            }
                        }
                        true
                    }
                    else -> true
                }
            }
        }
    }

    // ========================================
    // PRIVATE: Firebase Language Readiness
    // ========================================
    
    /**
     * Update language readiness indicator on spacebar
     */
    private fun updateLanguageBadge() {
        mainHandler.post {
            keyboardGridView?.let { gridView ->
                val spacebarView = gridView.findViewWithTag<TextView>("spacebar")
                spacebarView?.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0,
                    if (isLanguageReady) 0 else android.R.drawable.stat_sys_download,
                    0
                )
            }
        }
    }

    // ========================================
    // PRIVATE: Touch Event Handling
    // ========================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (currentMode) {
            DisplayMode.TYPING -> {
                // Handle swipe gestures for typing mode
                handleSwipeTouch(event)
                super.onTouchEvent(event)
            }
            DisplayMode.PANEL -> super.onTouchEvent(event)
        }
    }
    
    // ========================================
    // PRIVATE: Utility Methods
    // ========================================

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getKeyWidthFactor(label: String, hasUtilityKey: Boolean = true): Float {
        return when {
            // ✅ Dynamic space bar width: Increase when utility key is hidden
            label == " " || label == "SPACE" || label.startsWith("space") -> {
                if (hasUtilityKey) 4.0f else 5.0f  // Increased space bar width
            }
            label == "⏎" || label == "RETURN" || label == "sym_keyboard_return" -> 1.3f  // Return button
            label == "?123" || label == "ABC" || label == "=<" || label == "123" -> 1.3f  // Mode switch buttons
            label == "🌐" || label == "GLOBE" -> 1f
            label == "," || label == "." -> 0.8f  // Smaller comma/period buttons
            label == "⇧" || label == "SHIFT" -> 1.3f  // Increased to match return/?123
            label == "⌫" || label == "DELETE" -> 1.3f  // Increased to match return/?123
            else -> 1.0f
        }
    }

    private fun getKeyTypeFromCode(code: Int): String = when (code) {
        32 -> "space"
        -1, android.inputmethodservice.Keyboard.KEYCODE_SHIFT -> "shift"
        -5, android.inputmethodservice.Keyboard.KEYCODE_DELETE -> "backspace"
        10, -4, android.inputmethodservice.Keyboard.KEYCODE_DONE -> "enter"
        -13, -16 -> "mic"
        -15 -> "emoji"
        -14 -> "globe"
        -10, -11, -12 -> "symbols"
        else -> "regular"
    }

    private fun getIconForKeyType(keyType: String, label: String): Int? {
        return when (keyType) {
            "shift" -> R.drawable.sym_keyboard_shift
            "backspace" -> R.drawable.sym_keyboard_delete
            "enter" -> R.drawable.button_return  // ✅ Use Button_return.xml with smart behavior
            "globe" -> R.drawable.sym_keyboard_globe
            "symbols" -> R.drawable.number_123  // ✅ Use XML icon for 123/symbols button
            else -> when (label.uppercase()) {
                "SHIFT", "⇧" -> R.drawable.sym_keyboard_shift
                "DELETE", "⌫" -> R.drawable.sym_keyboard_delete
                "RETURN", "SYM_KEYBOARD_RETURN", "⏎" -> R.drawable.button_return  // ✅ Use Button_return.xml
                "GLOBE", "🌐" -> R.drawable.sym_keyboard_globe
                "?123", "123" -> R.drawable.number_123  // ✅ Use XML icon for 123 button
                "ABC" -> R.drawable.abc  // ✅ Use XML icon for ABC button
                else -> null
            }
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelLongPressInternal()
        hideAccentOptions()
        currentPanelView?.let { bodyContainer.removeView(it) }
        currentPanelView = null
        dynamicKeys.clear()
        keyboardGridView = null
        mainScope.cancel()
        Log.d(TAG, "✅ UnifiedKeyboardView cleaned up")
    }
    
    private fun cancelLongPressInternal() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        longPressKey = null
    }

    private fun hideAccentOptions() {
        try {
            accentPopup?.dismiss()
            accentPopup = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    // ========================================
    // INNER CLASS: Enhanced KeyboardGridView
    // ========================================

    /**
     * Enhanced KeyboardGridView with full swipe support and gesture recognition
     */
    private class KeyboardGridView(
        context: Context,
        private val model: LanguageLayoutAdapter.LayoutModel,
        private val themeManager: ThemeManager,
        private val heightManager: KeyboardHeightManager,
        private val showLanguageOnSpace: Boolean,
        private val currentLanguageLabel: String,
        private var labelScaleMultiplier: Float,
        private val borderlessMode: Boolean,
        private val hintedNumberRow: Boolean,
        private val hintedSymbols: Boolean,
        private val numberRowActive: Boolean,
        private var instantLongPressSelectFirst: Boolean,
        private val keySpacingVerticalDp: Int,
        private val keySpacingHorizontalDp: Int,
        private val edgePaddingDp: Int,
        private val verticalEdgePaddingDp: Int,
        private val onKeyCallback: ((Int, IntArray) -> Unit)?,
        private val parentView: UnifiedKeyboardView
    ) : View(context) {

        private val TAG = "KeyboardGridView"
        private val dynamicKeys = mutableListOf<DynamicKey>()
        private val largeIconKeyTypes = setOf("emoji", "mic", "symbols")
        
        // ✅ NEW: Key preview popup settings
        private var keyPreviewEnabled = false // ✅ DISABLED: Popup preview permanently disabled (user request)
        private var keyPreviewPopup: PopupWindow? = null
        private var keyPreviewText: TextView? = null
        
        // Swipe trail for visual feedback
        private val swipeTrailPaint = Paint().apply {
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
            alpha = 180
        }
        
        // Touch handling
        private var longPressHandler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null
        private var longPressDelayMs: Long = 200L // Configurable long press delay
        private var accentPopup: PopupWindow? = null
        private var stickyFirstPopupSelection = false
        private var accentOptionViews = mutableListOf<TextView>()
        private var selectedAccentIndex = -1
        private var activeAccentKey: DynamicKey? = null
        private var activeAccentOptions: List<String> = emptyList()
        
        // Key preview popup handling
        private var keyPreviewHandler = Handler(Looper.getMainLooper())
        private var keyPreviewRunnable: Runnable? = null
        private var currentPreviewKey: DynamicKey? = null
        private val POPUP_SHOW_DELAY_MS = 5L // Small delay before showing popup (like Gboard)

        fun setInstantLongPressSelectFirst(enabled: Boolean) {
            instantLongPressSelectFirst = enabled
        }
        
        fun setLongPressDelay(delayMs: Long) {
            longPressDelayMs = delayMs.coerceIn(150L, 600L)
            Log.d(TAG, "KeyboardGridView: Long press delay set to ${longPressDelayMs}ms")
        }

        fun setLabelScale(multiplier: Float) {
            val coerced = multiplier.coerceIn(0.8f, 1.3f)
            if (coerced == labelScaleMultiplier) return
            labelScaleMultiplier = coerced
            invalidate()
        }
        
        /**
         * ✅ NEW: Enable or disable key preview popups
         */
        fun setPreviewEnabled(enabled: Boolean) {
            keyPreviewEnabled = enabled
            if (!enabled) {
                cancelKeyPreview()
                hideKeyPreview()
            }
            Log.d(TAG, "Key preview enabled: $enabled")
        }
        
        /**
         * Cancel scheduled key preview
         */
        private fun cancelKeyPreview() {
            keyPreviewRunnable?.let { keyPreviewHandler.removeCallbacks(it) }
            keyPreviewRunnable = null
            currentPreviewKey = null
        }
        
        /**
         * ✅ NEW: Schedule key preview popup with delay (like Gboard)
         */
        private fun scheduleKeyPreview(key: DynamicKey) {
            if (!keyPreviewEnabled) return
            
            // Skip preview for special keys
            val keyType = getKeyTypeFromCode(key.code)
            if (keyType in setOf("space", "enter", "shift", "backspace", "symbols", "emoji", "mic", "globe")) {
                return
            }
            
            // Cancel any existing preview
            cancelKeyPreview()
            currentPreviewKey = key
            
            // Schedule preview with small delay
            keyPreviewRunnable = Runnable {
                if (currentPreviewKey == key && keyPreviewEnabled) {
                    showKeyPreviewNow(key)
                }
            }
            keyPreviewHandler.postDelayed(keyPreviewRunnable!!, POPUP_SHOW_DELAY_MS)
        }
        
        /**
         * ✅ NEW: Show key preview popup for a key (actual implementation)
         */
        private fun showKeyPreviewNow(key: DynamicKey) {
            if (!keyPreviewEnabled) return
            
            // Skip preview for special keys
            val keyType = getKeyTypeFromCode(key.code)
            if (keyType in setOf("space", "enter", "shift", "backspace", "symbols", "emoji", "mic", "globe")) {
                return
            }
            
            try {
                val palette = themeManager.getCurrentPalette()
                
                // Create popup window if needed or update theme if changed
                if (keyPreviewPopup == null) {
                    keyPreviewText = TextView(context).apply {
                        textSize = 28f // Slightly larger for clarity
                        gravity = Gravity.CENTER
                        setTextColor(palette.keyText)
                        setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(16))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    
                    // Create rounded background drawable (like Gboard)
                    val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dpToPx(16).toFloat()
                        setColor(palette.keyBg)
                        setStroke(dpToPx(2), palette.keyBorderColor)
                    }
                    
                    keyPreviewPopup = PopupWindow(
                        keyPreviewText,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        isClippingEnabled = false
                        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                        isTouchable = false
                        isFocusable = false
                        elevation = 16f // Higher elevation for better visibility
                        setBackgroundDrawable(backgroundDrawable)
                    }
                } else {
                    // Update theme colors if popup already exists
                    keyPreviewText?.setTextColor(palette.keyText)
                    val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dpToPx(16).toFloat()
                        setColor(palette.keyBg)
                        setStroke(dpToPx(2), palette.keyBorderColor)
                    }
                    keyPreviewPopup?.setBackgroundDrawable(backgroundDrawable)
                }
                
                // Update preview text - just use the key label as-is
                keyPreviewText?.text = key.label
                
                // Calculate position - center above the key, closer (like Gboard)
                val location = IntArray(2)
                this.getLocationInWindow(location)
                
                // Measure popup to get actual size
                keyPreviewText?.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val popupWidth = keyPreviewText?.measuredWidth ?: dpToPx(64)
                val popupHeight = keyPreviewText?.measuredHeight ?: dpToPx(72)
                
                // Position closer to key (like Gboard) - only 4dp above
                val popupX = location[0] + key.x + (key.width / 2) - (popupWidth / 2)
                val popupY = location[1] + key.y - popupHeight - dpToPx(4)
                
                // Show popup
                if (!keyPreviewPopup!!.isShowing) {
                    keyPreviewPopup?.width = popupWidth
                    keyPreviewPopup?.height = popupHeight
                    keyPreviewPopup?.showAtLocation(this, Gravity.NO_GRAVITY, popupX, popupY)
                } else {
                    keyPreviewPopup?.update(popupX, popupY, popupWidth, popupHeight)
                }
                
                Log.d(TAG, "Key preview shown for: ${key.label}")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing key preview", e)
            }
        }
        
        /**
         * ✅ NEW: Hide key preview popup
         */
        private fun hideKeyPreview() {
            try {
                cancelKeyPreview()
                keyPreviewPopup?.dismiss()
                currentPreviewKey = null
            } catch (e: Exception) {
                // Ignore dismissal errors
            }
        }
        
        // Swipe state
        private val fingerPoints = mutableListOf<FloatArray>()
        private var isSwipeActive = false
        private var swipeEligibleForCurrentGesture = false
        private var swipeStartTime = 0L
        
        // ✅ Multi-touch support: Track active pointers and their associated keys
        // This map stores pointerId -> DynamicKey to properly handle rollover typing
        private val activePointers = mutableMapOf<Int, DynamicKey>()
        private val nonSwipeKeyTypes = setOf(
            "space",
            "enter",
            "shift",
            "backspace",
            "symbols",
            "emoji",
            "mic",
            "globe",
            "voice"
        )
        private val trailHandler = Handler(Looper.getMainLooper())
        private var trailRunnable: Runnable? = null
        
        // Continuous delete state
        private var deleteRepeatHandler = Handler(Looper.getMainLooper())
        private var deleteRepeatRunnable: Runnable? = null
        private var isDeleteRepeating = false
        private val tapRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val tapGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var tapEffectStyle: UnifiedKeyboardView.TapEffectStyle = UnifiedKeyboardView.TapEffectStyle.NONE
        private var tapEffectsEnabled: Boolean = false
        private var tapEffectState: TapEffectState? = null
        
        // ⚡ PERFORMANCE: Cached Path objects to prevent GC churn during drawing
        private val cachedStarPath = Path()
        private val cachedSparklePath = Path()
        private val cachedHeartPath = Path()
        private val cachedLeafPath = Path()
        private val cachedLightningPath = Path()
        private val cachedGenericPath = Path()
        
        // ⚡ PERFORMANCE: Cached RectF objects for arc drawing
        private val cachedArcRectLeft = RectF()
        private val cachedArcRectRight = RectF()
        private val cachedTempRect = RectF()

        private data class TapEffectState(
            val key: DynamicKey,
            val startTime: Long,
            val duration: Long,
            val overlays: Map<String, List<OverlayElement>> = emptyMap()
        ) {
            fun progress(): Float {
                val elapsed = (SystemClock.uptimeMillis() - startTime).coerceAtLeast(0L)
                return (elapsed / duration.toFloat()).coerceIn(0f, 1f)
            }

            fun isFinished(): Boolean = SystemClock.uptimeMillis() - startTime >= duration
        }

        private data class OverlayElement(
            val dx: Float,
            val dy: Float,
            val size: Float,
            val rotation: Float,
            val color: Int? = null,
            val extra: FloatArray? = null
        )

        init {
            setWillNotDraw(false) // Enable onDraw
            setBackgroundColor(Color.TRANSPARENT)
            // ⚡ PERFORMANCE FIX: Removed LAYER_TYPE_SOFTWARE to enable GPU hardware acceleration
            // This allows Android to use the GPU for rendering instead of CPU, dramatically
            // improving frame rates and reducing touch input latency

            if (width > 0 && height > 0) {
                buildKeys()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                buildKeys()
            }
        }

        private fun buildKeys() {
            dynamicKeys.clear()
            tapEffectState = null

            val screenWidth = width
            val screenHeight = height
            if (screenWidth <= 0 || screenHeight <= 0) {
                Log.w(TAG, "⚠️ Unable to build keys without valid dimensions")
                return
            }

            val horizontalSpacingPx = dpToPx(keySpacingHorizontalDp).toFloat()
            val verticalSpacingPx = dpToPx(keySpacingVerticalDp).toFloat()
            val edgePaddingPx = dpToPx(edgePaddingDp).toFloat()
            val verticalPaddingPx = dpToPx(verticalEdgePaddingDp).toFloat()

            val rows = model.rows
            val numRows = rows.size
            if (numRows == 0) {
                return
            }

            val totalSpacingY = max(0, numRows - 1) * verticalSpacingPx
            val totalVerticalPadding = verticalPaddingPx * 2f
            val usableHeight = (screenHeight.toFloat() - totalSpacingY - totalVerticalPadding).coerceAtLeast(0f)
            val explicitHeights = if (model.rowHeightsDp.isNotEmpty()) {
                val heights = model.rowHeightsDp.map { dpToPx(it) }.toMutableList()
                while (heights.size < numRows) {
                    heights.add(heights.lastOrNull() ?: dpToPx(60))
                }
                if (heights.size > numRows) {
                    heights.subList(numRows, heights.size).clear()
                }
                val usableHeightInt = usableHeight.roundToInt()
                val heightSum = heights.sum()
                val diff = usableHeightInt - heightSum
                if (diff != 0 && heights.isNotEmpty()) {
                    val perRow = diff / heights.size
                    val remainder = diff % heights.size
                    heights.indices.forEach { index ->
                        var adjustment = perRow
                        if (remainder != 0) {
                            if (diff > 0 && index < remainder) {
                                adjustment += 1
                            } else if (diff < 0 && index < -remainder) {
                                adjustment -= 1
                            }
                        }
                        heights[index] = (heights[index] + adjustment).coerceAtLeast(1)
                    }
                }
                heights
            } else null

            val rowHeights: List<Int> = explicitHeights ?: run {
                val ratioSequence = MutableList(numRows) { 1f }
                val ratioSum = ratioSequence.sum().takeIf { it > 0f } ?: numRows.toFloat()

                val heights = MutableList(numRows) { 0 }
                var accumulatedHeight = 0
                for (index in 0 until numRows) {
                    val computedHeight = ((ratioSequence[index] / ratioSum) * usableHeight).roundToInt()
                    heights[index] = computedHeight
                    accumulatedHeight += computedHeight
                }
                val heightAdjustment = usableHeight.roundToInt() - accumulatedHeight
                if (heightAdjustment != 0 && heights.isNotEmpty()) {
                    val lastIndex = heights.lastIndex
                    heights[lastIndex] = (heights[lastIndex] + heightAdjustment).coerceAtLeast(0)
                }
                heights
            }

            var currentY = verticalPaddingPx
            val isRTL = model.direction.equals("RTL", ignoreCase = true)

            rows.forEachIndexed { rowIndex, row ->
                val rowHeight = max(rowHeights.getOrElse(rowIndex) { 0 }, 1)

                // Check if this row has a utility key (globe button)
                val hasUtilityKey = row.any { it.code == -14 }
                
                val totalWidthUnits = row.sumOf { key ->
                    getKeyWidthFactor(key.label, hasUtilityKey).toDouble()
                }.toFloat().coerceAtLeast(1f)

                val spacingTotal = if (row.size > 1) horizontalSpacingPx * (row.size - 1) else 0f
                val usableWidth = (screenWidth.toFloat() - (edgePaddingPx * 2f)).coerceAtLeast(0f)
                val contentWidth = (usableWidth - spacingTotal).coerceAtLeast(0f)
                val indentRatio = resolveIndentRatio(rowIndex, row, rows)
                val indentUnits = (indentRatio * 2f).coerceAtLeast(0f)
                val denominator = (totalWidthUnits + indentUnits).coerceAtLeast(1f)
                val unitWidth = if (denominator > 0f) contentWidth / denominator else 0f
                val indentPx = indentRatio * unitWidth
                val rowWidth = totalWidthUnits * unitWidth + spacingTotal
                val extraSpace = (usableWidth - (indentPx * 2f) - rowWidth).coerceAtLeast(0f)
                val startX = edgePaddingPx + indentPx + (extraSpace / 2f)

                var currentX = startX

                row.forEachIndexed { keyIndex, keyModel ->
                    var keyWidth = unitWidth * getKeyWidthFactor(keyModel.label, hasUtilityKey)
                    if (keyIndex == row.lastIndex) {
                        val expectedEnd = startX + rowWidth
                        val actualEnd = currentX + keyWidth
                        keyWidth += (expectedEnd - actualEnd)
                    }

                    val keyX = currentX
                    currentX += keyWidth
                    if (keyIndex < row.lastIndex) {
                        currentX += horizontalSpacingPx
                    }

                    val resolvedX = if (isRTL) {
                        (screenWidth.toFloat() - keyX - keyWidth)
                    } else {
                        keyX
                    }

                    // ⚡ PERFORMANCE: Pre-calculate and cache the key rect to avoid GC during onDraw
                    val keyX_int = resolvedX.roundToInt()
                    val keyY_int = currentY.roundToInt()
                    val keyWidth_int = keyWidth.roundToInt().coerceAtLeast(1)
                    
                    val basePadding = if (borderlessMode) 0f else dpToPx(0.5f)
                    val horizontalInset = if (borderlessMode) 0f else dpToPx(0.5f)
                    val verticalInset = if (borderlessMode) 0f else dpToPx(0.5f)
                    
                    val cachedRect = RectF(
                        keyX_int.toFloat() + basePadding + horizontalInset,
                        keyY_int.toFloat() + basePadding + verticalInset,
                        (keyX_int + keyWidth_int).toFloat() - basePadding - horizontalInset,
                        (keyY_int + rowHeight).toFloat() - basePadding - verticalInset
                    )

                    val dynamicKey = DynamicKey(
                        x = keyX_int,
                        y = keyY_int,
                        width = keyWidth_int,
                        height = rowHeight,
                        label = keyModel.label,
                        code = keyModel.code,
                        longPressOptions = keyModel.longPress,
                        keyType = getKeyTypeFromCode(keyModel.code),
                        hintLabel = keyModel.altLabel,
                        rect = cachedRect
                    )
                    dynamicKeys.add(dynamicKey)
                }

                currentY += rowHeight
                if (rowIndex < numRows - 1) {
                    currentY += verticalSpacingPx
                }
            }

            invalidate()
            Log.d(TAG, "✅ Built ${dynamicKeys.size} keys with swipe support")
        }

        fun setTapEffectStyle(style: UnifiedKeyboardView.TapEffectStyle, enabled: Boolean) {
            tapEffectStyle = style
            tapEffectsEnabled = enabled && style != UnifiedKeyboardView.TapEffectStyle.NONE
            if (!tapEffectsEnabled) {
                tapEffectState = null
                invalidate()
            }
        }

        fun clearTapEffect() {
            tapEffectState = null
            invalidate()
        }
        
        /**
         * ⚡ PERFORMANCE: Update key labels for shift state without rebuilding entire layout
         * This is much faster than calling buildKeys() again
         * 
         * @param isUpperCase Whether keys should be uppercase
         * @param isCapsLock Whether caps lock is enabled
         * @return true if update was successful, false if keys haven't been built yet
         */
        fun updateKeyLabelsForShift(isUpperCase: Boolean, isCapsLock: Boolean): Boolean {
            if (dynamicKeys.isEmpty()) {
                Log.w(TAG, "⚠️ Cannot update labels - keys not built yet")
                return false
            }
            
            // Update labels in-place (create new DynamicKey instances with updated labels)
            val updatedKeys = dynamicKeys.map { key ->
                // Skip special keys that shouldn't change case
                val keyType = getKeyTypeFromCode(key.code)
                if (keyType in setOf("space", "enter", "backspace", "symbols", "emoji", "mic", "globe", "shift")) {
                    // For shift key, update visual state but don't change label
                    key
                } else {
                    // Update label case for letter keys
                    val newLabel = if (isUpperCase && key.label.length == 1 && key.label[0].isLetter()) {
                        key.label.uppercase()
                    } else if (!isUpperCase && key.label.length == 1 && key.label[0].isLetter()) {
                        key.label.lowercase()
                    } else {
                        key.label // Keep as-is for non-letter keys
                    }
                    
                    if (newLabel != key.label) {
                        key.copy(label = newLabel)
                    } else {
                        key
                    }
                }
            }
            
            // Replace the list (this is fast - just reference swap)
            dynamicKeys.clear()
            dynamicKeys.addAll(updatedKeys)
            
            // Invalidate to redraw with new labels
            invalidate()
            
            Log.d(TAG, "⚡ Updated key labels for shift state (uppercase=$isUpperCase, capsLock=$isCapsLock)")
            return true
        }
        
        private fun resolveIndentRatio(
            rowIndex: Int,
            row: List<LanguageLayoutAdapter.KeyModel>,
            totalRows: List<List<LanguageLayoutAdapter.KeyModel>>
        ): Float {
            if (rowIndex <= 0 || rowIndex >= totalRows.lastIndex) return 0f
            if (row.isEmpty()) return 0f
            
            val referenceRowSize = totalRows.firstOrNull { it.isNotEmpty() }?.size ?: return 0f
            if (referenceRowSize - row.size < 1) return 0f
            
            val containsAnchorKey = row.any { key ->
                when (key.code) {
                    -1,
                    android.inputmethodservice.Keyboard.KEYCODE_SHIFT,
                    -5,
                    android.inputmethodservice.Keyboard.KEYCODE_DELETE -> true
                    else -> false
                }
            }
            if (containsAnchorKey) return 0f
            
            return 0.5f
        }

        // ⚡ PERFORMANCE FIX: Return the pre-cached RectF from DynamicKey to eliminate GC stutter
        // The rect is calculated once during buildKeys() instead of on every draw call
        private fun getKeyRect(key: DynamicKey): RectF = key.rect

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val palette = themeManager.getCurrentPalette()
            canvas.drawColor(palette.keyboardBg)

            dynamicKeys.forEach { key ->
                drawKey(canvas, key, palette)
            }
            
            // Draw swipe trail if active
            if (isSwipeActive && fingerPoints.size > 1) {
                drawSwipeTrail(canvas, palette)
            }
        }
        
        private fun drawSwipeTrail(canvas: Canvas, palette: ThemePaletteV2) {
            if (fingerPoints.size < 2) return
            
            swipeTrailPaint.color = palette.specialAccent
            
            val path = Path()
            val firstPoint = fingerPoints[0]
            path.moveTo(firstPoint[0], firstPoint[1])
            
            for (i in 1 until fingerPoints.size) {
                val point = fingerPoints[i]
                    path.lineTo(point[0], point[1])
            }
            
            canvas.drawPath(path, swipeTrailPaint)
        }

        private fun drawKey(canvas: Canvas, key: DynamicKey, palette: ThemePaletteV2) {
            val keyRect = getKeyRect(key)
            val effect = tapEffectState
            val hasTapVisuals = tapEffectsEnabled && tapEffectStyle != UnifiedKeyboardView.TapEffectStyle.NONE && effect?.key == key
            val hasOverlay = effect?.key == key && (effect.overlays.isNotEmpty())
            val shouldAnimate = hasTapVisuals || hasOverlay
            val progress = if (shouldAnimate) effect!!.progress() else 0f

            var didSaveCanvas = false
            if (hasTapVisuals && tapEffectStyle == UnifiedKeyboardView.TapEffectStyle.BOUNCE) {
                val scale = computeBounceScale(progress)
                canvas.save()
                canvas.scale(scale, scale, keyRect.centerX(), keyRect.centerY())
                didSaveCanvas = true
            }

            // ✅ Draw background with per-key customization support
            val keyIdentifier = getKeyIdentifier(key)
            val shouldDrawBackground = !borderlessMode
            val shouldUseAccentBackground = !borderlessMode && themeManager.shouldUseAccentForKey(key.keyType)
            val keyDrawable = if (shouldDrawBackground) {
                if (shouldUseAccentBackground) themeManager.createSpecialKeyDrawable()
                else themeManager.createKeyDrawable(keyIdentifier)
            } else null

            keyDrawable?.let { drawable ->
                drawable.setBounds(keyRect.left.toInt(), keyRect.top.toInt(), keyRect.right.toInt(), keyRect.bottom.toInt())
                drawable.draw(canvas)
            }

            // Draw icon or text content
            val overlayIconId = palette.keyOverlayIcon
            val overlayTargets = palette.keyOverlayTargets
            var overlayConsumed = false
            if (overlayIconId != null && overlayTargets.contains(key.keyType)) {
                overlayConsumed = drawKeyOverlayIcon(
                    canvas,
                    keyRect,
                    overlayIconId,
                    palette.keyOverlayIconColor ?: palette.keyText
                )
            }

            if (!overlayConsumed) {
                val iconResId = getIconForKeyType(key.keyType, key.label)
                if (iconResId != null) {
                    drawKeyIcon(canvas, key, keyRect, iconResId, palette)
                } else {
                    drawKeyText(canvas, key, keyRect, palette)
                }
            }

            if (didSaveCanvas) {
                canvas.restore()
            }

            if (shouldAnimate && effect != null) {
                drawTapEffectOverlay(canvas, keyRect, palette, effect, hasTapVisuals)
                if (effect.isFinished()) {
                    tapEffectState = null
                } else {
                    postInvalidateOnAnimation()
                }
            }
        }

        private fun drawTapEffectOverlay(
            canvas: Canvas,
            keyRect: RectF,
            palette: ThemePaletteV2,
            state: TapEffectState,
            drawPrimary: Boolean
        ) {
            val progress = state.progress()
            if (drawPrimary) {
                when (tapEffectStyle) {
                    UnifiedKeyboardView.TapEffectStyle.NONE -> {
                        // No tap animation; overlays handled separately below
                    }
                    UnifiedKeyboardView.TapEffectStyle.RIPPLE -> {
                        val maxDimension = max(keyRect.width(), keyRect.height())
                        val radius = maxDimension * (0.25f + 0.75f * progress)
                        val alpha = (150 * (1f - progress)).toInt().coerceIn(0, 180)
                        tapRipplePaint.color = ColorUtils.setAlphaComponent(palette.specialAccent, alpha)
                        tapRipplePaint.style = Paint.Style.FILL
                        canvas.drawCircle(keyRect.centerX(), keyRect.centerY(), radius, tapRipplePaint)
                    }
                    UnifiedKeyboardView.TapEffectStyle.GLOW -> {
                        val alpha = (160 * (1f - progress)).toInt().coerceIn(0, 200)
                        tapGlowPaint.color = ColorUtils.setAlphaComponent(palette.specialAccent, alpha)
                        tapGlowPaint.style = Paint.Style.STROKE
                        tapGlowPaint.strokeWidth = dpToPx(3f) * (1f - 0.4f * progress)
                        tapGlowPaint.setShadowLayer(
                            dpToPx(6f),
                            0f,
                            0f,
                            ColorUtils.setAlphaComponent(palette.specialAccent, (alpha * 0.9f).toInt().coerceAtLeast(0))
                        )
                        val cornerRadius = dpToPx(16f)
                        canvas.drawRoundRect(keyRect, cornerRadius, cornerRadius, tapGlowPaint)
                        tapGlowPaint.clearShadowLayer()
                    }
                    UnifiedKeyboardView.TapEffectStyle.BOUNCE -> {
                        val alpha = (120 * (1f - progress)).toInt().coerceIn(0, 160)
                        tapRipplePaint.color = ColorUtils.setAlphaComponent(palette.specialAccent, alpha)
                        tapRipplePaint.style = Paint.Style.FILL
                        canvas.drawCircle(keyRect.centerX(), keyRect.centerY(), keyRect.width() * 0.35f, tapRipplePaint)
                    }
                }
            }

            drawOverlayEffects(canvas, keyRect, palette, state, progress)
        }

        /**
         * ⚡ PERFORMANCE: Draw overlay effects (stars, hearts, snow, etc.)
         * Has early returns to skip drawing when effects are disabled
         */
        private fun drawOverlayEffects(
            canvas: Canvas,
            keyRect: RectF,
            palette: ThemePaletteV2,
            state: TapEffectState,
            progress: Float
        ) {
            // ⚡ PERFORMANCE: Early returns avoid expensive drawing operations
            if (state.overlays.isEmpty()) return
            val opacity = palette.globalEffectsOpacity.coerceIn(0f, 1f)
            if (opacity <= 0f) return
            state.overlays.forEach { (effect, elements) ->
                when (effect) {
                    "stars" -> drawStarOverlay(canvas, keyRect, palette.specialAccent, elements, progress, sparkle = false, opacity = opacity)
                    "sparkles" -> drawStarOverlay(canvas, keyRect, Color.WHITE, elements, progress, sparkle = true, opacity = opacity)
                    "hearts" -> drawHeartOverlay(canvas, keyRect, elements, progress, opacity)
                    "bubbles" -> drawBubbleOverlay(canvas, keyRect, elements, progress, opacity)
                    "leaves" -> drawLeafOverlay(canvas, keyRect, elements, progress, opacity)
                    "snow" -> drawSnowOverlay(canvas, keyRect, elements, progress, opacity)
                    "lightning" -> drawLightningOverlay(canvas, keyRect, elements, progress, opacity)
                    "confetti" -> drawConfettiOverlay(canvas, keyRect, elements, progress, opacity)
                    "butterflies" -> drawButterflyOverlay(canvas, keyRect, elements, progress, opacity)
                    "rainbow" -> drawRainbowOverlay(canvas, keyRect, elements, progress, opacity)
                }
            }
        }

        private fun drawStarOverlay(
            canvas: Canvas,
            keyRect: RectF,
            baseColor: Int,
            elements: List<OverlayElement>,
            progress: Float,
            sparkle: Boolean,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            val baseAlpha = if (sparkle) 170 else 210
            val alpha = (baseAlpha * (1f - progress)).toInt().coerceIn(0, baseAlpha)
            val scaledAlpha = (alpha * opacity).toInt().coerceIn(0, alpha)
            if (scaledAlpha <= 0) return

            elements.forEach { element ->
                val tint = ColorUtils.setAlphaComponent(element.color ?: baseColor, scaledAlpha)
                overlayPaint.color = tint
                canvas.save()
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                val size = element.size * (1f + progress * 0.25f)
                canvas.translate(cx, cy)
                canvas.rotate(element.rotation)
                canvas.drawPath(createStarPath(size, sparkle), overlayPaint)
                canvas.restore()
            }
        }

        private fun drawHeartOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            val alpha = (200 * (1f - progress)).toInt().coerceIn(0, 200)
            val scaledAlpha = (alpha * opacity).toInt().coerceIn(0, alpha)
            if (scaledAlpha <= 0) return

            elements.forEach { element ->
                val color = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FF7AAE"), scaledAlpha)
                overlayPaint.color = color
                canvas.save()
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                val size = element.size * (1f + progress * 0.2f)
                canvas.translate(cx, cy)
                canvas.rotate(element.rotation)
                canvas.drawPath(createHeartPath(size), overlayPaint)
                canvas.restore()
            }
        }

        private fun drawBubbleOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            elements.forEach { element ->
                val baseAlpha = (150 * (1f - progress)).toInt().coerceIn(0, 170)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                val color = ColorUtils.setAlphaComponent(element.color ?: ColorUtils.setAlphaComponent(Color.WHITE, 200), alpha)
                overlayPaint.color = color
                val radius = element.size * (1f + progress * 0.18f)
                canvas.drawCircle(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy, radius, overlayPaint)
            }
        }

        private fun drawLeafOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            // ⚡ PERFORMANCE: Reuse cached path instead of allocating new one
            elements.forEach { element ->
                val baseColor = element.color ?: Color.parseColor("#4CAF50")
                val baseAlpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                overlayPaint.style = Paint.Style.FILL
                overlayPaint.color = ColorUtils.setAlphaComponent(baseColor, alpha)
                canvas.save()
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                canvas.translate(cx, cy)
                canvas.rotate(element.rotation)
                val size = element.size * (1f + progress * 0.15f)
                cachedLeafPath.reset()
                cachedLeafPath.moveTo(0f, -size * 0.6f)
                cachedLeafPath.quadTo(size * 0.55f, -size * 0.3f, size * 0.2f, size * 0.6f)
                cachedLeafPath.quadTo(0f, size * 0.3f, -size * 0.2f, size * 0.6f)
                cachedLeafPath.quadTo(-size * 0.55f, -size * 0.3f, 0f, -size * 0.6f)
                canvas.drawPath(cachedLeafPath, overlayPaint)
                canvas.restore()
            }
        }

        private fun drawSnowOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            val strokePaint = Paint(overlayPaint).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            elements.forEach { element ->
                val baseColor = element.color ?: Color.WHITE
                val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 220)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                overlayPaint.style = Paint.Style.FILL
                overlayPaint.color = ColorUtils.setAlphaComponent(baseColor, alpha)
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                val radius = element.size * 0.35f * (1f + progress * 0.1f)
                canvas.drawCircle(cx, cy, radius, overlayPaint)

                strokePaint.color = overlayPaint.color
                strokePaint.strokeWidth = radius * 0.6f
                canvas.drawLine(cx - radius, cy, cx + radius, cy, strokePaint)
                canvas.drawLine(cx, cy - radius, cx, cy + radius, strokePaint)
                canvas.drawLine(cx - radius * 0.7f, cy - radius * 0.7f, cx + radius * 0.7f, cy + radius * 0.7f, strokePaint)
                canvas.drawLine(cx + radius * 0.7f, cy - radius * 0.7f, cx - radius * 0.7f, cy + radius * 0.7f, strokePaint)
            }
        }

        private fun drawLightningOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            // ⚡ PERFORMANCE: Reuse cached path instead of allocating new one
            overlayPaint.style = Paint.Style.STROKE
            overlayPaint.strokeCap = Paint.Cap.ROUND
            overlayPaint.strokeJoin = Paint.Join.ROUND

            elements.forEach { element ->
                val baseAlpha = (235 * (1f - progress)).toInt().coerceIn(0, 235)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                overlayPaint.color = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FFD740"), alpha)
                overlayPaint.strokeWidth = element.size * 0.18f

                val coords = element.extra ?: floatArrayOf(
                    0f, -element.size * 0.6f,
                    element.size * 0.25f, -element.size * 0.15f,
                    -element.size * 0.15f, element.size * 0.1f,
                    element.size * 0.2f, element.size * 0.55f
                )

                cachedLightningPath.reset()
                cachedLightningPath.moveTo(coords[0], coords[1])
                var idx = 2
                while (idx < coords.size) {
                    cachedLightningPath.lineTo(coords[idx], coords[idx + 1])
                    idx += 2
                }

                canvas.save()
                canvas.translate(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy)
                canvas.rotate(element.rotation)
                canvas.drawPath(cachedLightningPath, overlayPaint)
                canvas.restore()
            }
            overlayPaint.style = Paint.Style.FILL
        }

        private fun drawConfettiOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            elements.forEach { element ->
                val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 220)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                val color = ColorUtils.setAlphaComponent(element.color ?: Color.WHITE, alpha)
                overlayPaint.style = Paint.Style.FILL
                overlayPaint.color = color

                val width = (element.extra?.getOrNull(0) ?: element.size * 0.4f) * (1f + progress * 0.15f)
                val height = (element.extra?.getOrNull(1) ?: element.size * 0.15f) * (1f + progress * 0.05f)

                canvas.save()
                canvas.translate(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy)
                canvas.rotate(element.rotation)
                canvas.drawRoundRect(
                    -width / 2f,
                    -height / 2f,
                    width / 2f,
                    height / 2f,
                    height / 2f,
                    height / 2f,
                    overlayPaint
                )
                canvas.restore()
            }
        }

        private fun drawButterflyOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            val wingPaint = Paint(overlayPaint)
            val bodyPaint = Paint(overlayPaint).apply {
                style = Paint.Style.FILL
                color = ColorUtils.setAlphaComponent(Color.DKGRAY, 180)
            }
            val wingPath = Path()

            elements.forEach { element ->
                val baseAlpha = (200 * (1f - progress)).toInt().coerceIn(0, 200)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                val wingColor = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FFB6C1"), alpha)
                wingPaint.style = Paint.Style.FILL
                wingPaint.color = wingColor

                canvas.save()
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                canvas.translate(cx, cy)
                canvas.rotate(element.rotation)
                val size = element.size * (1f + progress * 0.12f)

                // Left wing
                wingPath.reset()
                wingPath.moveTo(0f, 0f)
                wingPath.cubicTo(-size * 0.9f, -size * 0.2f, -size * 0.9f, -size, -size * 0.1f, -size * 0.9f)
                wingPath.cubicTo(-size * 0.8f, -size * 0.3f, -size * 0.8f, size * 0.3f, -size * 0.1f, size * 0.9f)
                wingPath.close()
                canvas.drawPath(wingPath, wingPaint)

                // Right wing
                wingPath.reset()
                wingPath.moveTo(0f, 0f)
                wingPath.cubicTo(size * 0.9f, -size * 0.2f, size * 0.9f, -size, size * 0.1f, -size * 0.9f)
                wingPath.cubicTo(size * 0.8f, -size * 0.3f, size * 0.8f, size * 0.3f, size * 0.1f, size * 0.9f)
                wingPath.close()
                canvas.drawPath(wingPath, wingPaint)

                // Body
                canvas.drawRoundRect(
                    -size * 0.1f,
                    -size * 0.8f,
                    size * 0.1f,
                    size * 0.8f,
                    size * 0.1f,
                    size * 0.1f,
                    bodyPaint
                )

                canvas.restore()
            }
        }

        private fun drawRainbowOverlay(
            canvas: Canvas,
            keyRect: RectF,
            elements: List<OverlayElement>,
            progress: Float,
            opacity: Float
        ) {
            if (elements.isEmpty()) return
            val colors = intArrayOf(
                Color.parseColor("#FF6F61"),
                Color.parseColor("#FDB045"),
                Color.parseColor("#F9ED69"),
                Color.parseColor("#9ADBCB"),
                Color.parseColor("#62B0E8"),
                Color.parseColor("#A685E2")
            )

            val strokePaint = Paint(overlayPaint).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            elements.forEach { element ->
                val cx = keyRect.centerX() + element.dx
                val cy = keyRect.centerY() + element.dy
                val baseRadius = element.size * (1f + progress * 0.1f)
                val thickness = (element.extra?.getOrNull(0) ?: element.size * 0.12f)

                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(element.rotation)

                colors.forEachIndexed { index, color ->
                    val radius = baseRadius - index * thickness
                    if (radius <= 0f) return@forEachIndexed

                    strokePaint.strokeWidth = thickness * 0.8f
                    val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 210)
                    val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                    strokePaint.color = ColorUtils.setAlphaComponent(color, alpha)
                    val arcRect = RectF(-radius, -radius, radius, radius)
                    canvas.drawArc(arcRect, 200f, 140f, false, strokePaint)
                }

                canvas.restore()
            }
        }

        private fun drawKeyOverlayIcon(
            canvas: Canvas,
            keyRect: RectF,
            iconId: String,
            iconColor: Int
        ): Boolean {
            val size = min(keyRect.width(), keyRect.height())
            overlayPaint.color = iconColor
            overlayPaint.style = Paint.Style.FILL

            canvas.save()
            canvas.translate(keyRect.centerX(), keyRect.centerY())

            val consumed = when (iconId.lowercase()) {
                "heart" -> {
                    val heartPath = createHeartPath(size * 0.5f)
                    canvas.drawPath(heartPath, overlayPaint)
                    true
                }
                "ball" -> {
                    val radius = size * 0.38f
                    canvas.drawCircle(0f, 0f, radius, overlayPaint)

                    val seamPaint = Paint(overlayPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = radius * 0.18f
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 220)
                    }
                    val seamRect = RectF(-radius, -radius, radius, radius)
                    canvas.drawArc(seamRect, 35f, 110f, false, seamPaint)
                    canvas.drawArc(seamRect, 215f, 110f, false, seamPaint)
                    true
                }
                "watermelon" -> {
                    val sliceWidth = size * 0.82f
                    val sliceHeight = size * 0.62f
                    val topY = -sliceHeight / 2f
                    val baseY = sliceHeight / 2.2f

                    val fleshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = iconColor
                    }
                    val rindPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#2ECC71")
                    }
                    val pithPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = Color.parseColor("#F4FFD1")
                    }
                    val seedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = ColorUtils.setAlphaComponent(Color.BLACK, 210)
                    }

                    val slicePath = Path()
                    slicePath.moveTo(0f, topY)
                    slicePath.lineTo(sliceWidth / 2f, baseY)
                    slicePath.lineTo(-sliceWidth / 2f, baseY)
                    slicePath.close()
                    canvas.drawPath(slicePath, fleshPaint)

                    val rindHeight = sliceHeight * 0.22f
                    val rindRect = RectF(
                        -sliceWidth / 2f,
                        baseY - rindHeight,
                        sliceWidth / 2f,
                        baseY + rindHeight * 0.6f
                    )
                    canvas.drawRoundRect(rindRect, rindHeight, rindHeight, rindPaint)

                    val pithRect = RectF(
                        -sliceWidth / 2f,
                        baseY - rindHeight * 1.15f,
                        sliceWidth / 2f,
                        baseY - rindHeight * 0.25f
                    )
                    canvas.drawRoundRect(pithRect, rindHeight * 0.9f, rindHeight * 0.9f, pithPaint)

                    val seedHeight = sliceHeight * 0.22f
                    val seedWidth = seedHeight * 0.45f
                    val seedBaseY = topY + sliceHeight * 0.58f
                    val seedOffsets = listOf(-sliceWidth * 0.28f, 0f, sliceWidth * 0.28f)
                    seedOffsets.forEach { offsetX ->
                        canvas.save()
                        canvas.translate(offsetX, seedBaseY)
                        val tilt = when {
                            offsetX < 0f -> -14f
                            offsetX > 0f -> 14f
                            else -> 0f
                        }
                        canvas.rotate(tilt)
                        canvas.drawOval(
                            RectF(
                                -seedWidth / 2f,
                                -seedHeight / 2f,
                                seedWidth / 2f,
                                seedHeight / 2f
                            ),
                            seedPaint
                        )
                        canvas.restore()
                    }
                    false
                }
                "butterfly" -> {
                    val wingWidth = size * 0.52f
                    val wingHeight = size * 0.46f
                    val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = iconColor
                        alpha = (Color.alpha(iconColor) * 0.9f).toInt()
                    }
                    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 90)
                    }
                    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = ColorUtils.blendARGB(iconColor, Color.BLACK, 0.55f)
                    }
                    val antennaPaint = Paint(bodyPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = size * 0.045f
                        strokeCap = Paint.Cap.ROUND
                    }

                    val innerOffset = size * 0.22f
                    canvas.drawOval(
                        RectF(
                            -wingWidth - innerOffset,
                            -wingHeight,
                            -innerOffset,
                            wingHeight
                        ),
                        wingPaint
                    )
                    canvas.drawOval(
                        RectF(
                            innerOffset,
                            -wingHeight,
                            wingWidth + innerOffset,
                            wingHeight
                        ),
                        wingPaint
                    )

                    val highlightWidth = size * 0.36f
                    val highlightHeight = wingHeight * 0.65f
                    listOf(-wingWidth * 0.7f, wingWidth * 0.7f).forEach { offsetX ->
                        canvas.drawOval(
                            RectF(
                                offsetX - highlightWidth / 2f,
                                -highlightHeight,
                                offsetX + highlightWidth / 2f,
                                highlightHeight
                            ),
                            highlightPaint
                        )
                    }

                    val bodyRect = RectF(
                        -size * 0.09f,
                        -size * 0.38f,
                        size * 0.09f,
                        size * 0.38f
                    )
                    canvas.drawRoundRect(bodyRect, size * 0.12f, size * 0.12f, bodyPaint)

                    canvas.drawLine(0f, -size * 0.38f, -size * 0.22f, -size * 0.6f, antennaPaint)
                    canvas.drawLine(0f, -size * 0.38f, size * 0.22f, -size * 0.6f, antennaPaint)
                    false
                }
                "snowcap" -> {
                    val capWidth = size * 0.9f
                    val capHeight = size * 0.42f
                    val top = -size * 0.5f
                    val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = iconColor
                    }
                    val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
                    }
                    val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 210)
                        strokeWidth = size * 0.05f
                        strokeCap = Paint.Cap.ROUND
                    }

                    val capRect = RectF(-capWidth / 2f, top, capWidth / 2f, top + capHeight)
                    canvas.drawRoundRect(capRect, capHeight * 0.6f, capHeight * 0.6f, snowPaint)

                    val driftRadius = capHeight * 0.36f
                    val driftCenters = listOf(
                        PointF(-capWidth * 0.3f, top + capHeight * 0.65f),
                        PointF(0f, top + capHeight * 0.78f),
                        PointF(capWidth * 0.28f, top + capHeight * 0.62f)
                    )
                    driftCenters.forEachIndexed { index, center ->
                        val radius = driftRadius * (0.95f + index * 0.05f)
                        canvas.drawCircle(center.x, center.y, radius, snowPaint)
                        canvas.drawCircle(center.x, center.y - radius * 0.35f, radius * 0.55f, shadePaint)
                    }

                    val flakeRadius = size * 0.12f
                    val flakeCenters = listOf(
                        PointF(-capWidth * 0.28f, size * 0.12f),
                        PointF(capWidth * 0.3f, size * 0.05f)
                    )
                    flakeCenters.forEach { center ->
                        canvas.save()
                        canvas.translate(center.x, center.y)
                        canvas.drawLine(-flakeRadius, 0f, flakeRadius, 0f, flakePaint)
                        canvas.drawLine(0f, -flakeRadius, 0f, flakeRadius, flakePaint)
                        canvas.drawLine(
                            -flakeRadius * 0.7f,
                            -flakeRadius * 0.7f,
                            flakeRadius * 0.7f,
                            flakeRadius * 0.7f,
                            flakePaint
                        )
                        canvas.drawLine(
                            -flakeRadius * 0.7f,
                            flakeRadius * 0.7f,
                            flakeRadius * 0.7f,
                            -flakeRadius * 0.7f,
                            flakePaint
                        )
                        canvas.restore()
                    }
                    false
                }
                "star" -> {
                    val starPath = createStarPath(size * 0.45f, sparkle = false)
                    canvas.drawPath(starPath, overlayPaint)
                    true
                }
                "bolt" -> {
                    val boltPath = Path()
                    boltPath.moveTo(-size * 0.12f, -size * 0.5f)
                    boltPath.lineTo(size * 0.04f, -size * 0.12f)
                    boltPath.lineTo(-size * 0.08f, -size * 0.12f)
                    boltPath.lineTo(size * 0.12f, size * 0.5f)
                    boltPath.lineTo(-size * 0.04f, size * 0.18f)
                    boltPath.lineTo(size * 0.08f, size * 0.18f)
                    boltPath.close()
                    canvas.drawPath(boltPath, overlayPaint)
                    true
                }
                "download" -> {
                    val shaftWidth = size * 0.18f
                    val shaftHeight = size * 0.48f
                    val shaftRect = RectF(
                        -shaftWidth / 2f,
                        -shaftHeight / 2f,
                        shaftWidth / 2f,
                        shaftHeight * 0.1f
                    )
                    canvas.drawRoundRect(shaftRect, shaftWidth, shaftWidth, overlayPaint)

                    val arrowPath = Path()
                    arrowPath.moveTo(-size * 0.3f, shaftHeight * 0.05f)
                    arrowPath.lineTo(0f, shaftHeight * 0.52f)
                    arrowPath.lineTo(size * 0.3f, shaftHeight * 0.05f)
                    arrowPath.close()
                    canvas.drawPath(arrowPath, overlayPaint)

                    val barRect = RectF(
                        -size * 0.32f,
                        -shaftHeight * 0.65f,
                        size * 0.32f,
                        -shaftHeight * 0.5f
                    )
                    canvas.drawRoundRect(barRect, size * 0.12f, size * 0.12f, overlayPaint)
                    true
                }
                "chat" -> {
                    val bubbleWidth = size * 0.62f
                    val bubbleHeight = size * 0.46f
                    val rect = RectF(
                        -bubbleWidth / 2f,
                        -bubbleHeight / 2f,
                        bubbleWidth / 2f,
                        bubbleHeight / 2f
                    )
                    canvas.drawRoundRect(rect, bubbleHeight * 0.6f, bubbleHeight * 0.6f, overlayPaint)

                    val tailPath = Path()
                    tailPath.moveTo(rect.right - bubbleWidth * 0.28f, rect.bottom)
                    tailPath.lineTo(rect.right - bubbleWidth * 0.46f, rect.bottom + bubbleHeight * 0.34f)
                    tailPath.lineTo(rect.right - bubbleWidth * 0.62f, rect.bottom)
                    tailPath.close()
                    canvas.drawPath(tailPath, overlayPaint)

                    val linePaint = Paint(overlayPaint).apply {
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 210)
                        style = Paint.Style.STROKE
                        strokeWidth = bubbleHeight * 0.08f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val centerY = rect.centerY()
                    canvas.drawLine(
                        rect.left + bubbleWidth * 0.18f,
                        centerY - bubbleHeight * 0.12f,
                        rect.right - bubbleWidth * 0.2f,
                        centerY - bubbleHeight * 0.12f,
                        linePaint
                    )
                    canvas.drawLine(
                        rect.left + bubbleWidth * 0.18f,
                        centerY + bubbleHeight * 0.05f,
                        rect.right - bubbleWidth * 0.38f,
                        centerY + bubbleHeight * 0.05f,
                        linePaint
                    )
                    true
                }
                "leaf" -> {
                    val leafWidth = size * 0.6f
                    val leafHeight = size * 0.7f
                    val leafPath = Path()
                    leafPath.moveTo(0f, -leafHeight / 2f)
                    leafPath.cubicTo(
                        leafWidth / 2f,
                        -leafHeight / 3f,
                        leafWidth / 2f,
                        leafHeight / 3f,
                        0f,
                        leafHeight / 2f
                    )
                    leafPath.cubicTo(
                        -leafWidth / 2f,
                        leafHeight / 3f,
                        -leafWidth / 2f,
                        -leafHeight / 3f,
                        0f,
                        -leafHeight / 2f
                    )
                    leafPath.close()
                    canvas.drawPath(leafPath, overlayPaint)

                    val veinPaint = Paint(overlayPaint).apply {
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 190)
                        style = Paint.Style.STROKE
                        strokeWidth = leafWidth * 0.06f
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(0f, -leafHeight * 0.35f, 0f, leafHeight * 0.35f, veinPaint)
                    canvas.drawLine(0f, -leafHeight * 0.05f, leafWidth * 0.24f, leafHeight * 0.18f, veinPaint)
                    canvas.drawLine(0f, -leafHeight * 0.05f, -leafWidth * 0.26f, leafHeight * 0.06f, veinPaint)
                    true
                }
                "bell" -> {
                    val bellWidth = size * 0.58f
                    val bellHeight = size * 0.66f
                    val bellPath = Path()
                    bellPath.moveTo(-bellWidth / 2f, bellHeight * 0.1f)
                    bellPath.quadTo(-bellWidth / 2f, -bellHeight / 2f, 0f, -bellHeight / 2f)
                    bellPath.quadTo(bellWidth / 2f, -bellHeight / 2f, bellWidth / 2f, bellHeight * 0.1f)
                    bellPath.lineTo(bellWidth * 0.34f, bellHeight * 0.55f)
                    bellPath.lineTo(-bellWidth * 0.34f, bellHeight * 0.55f)
                    bellPath.close()
                    canvas.drawPath(bellPath, overlayPaint)

                    val handleRect = RectF(
                        -bellWidth * 0.16f,
                        -bellHeight * 0.78f,
                        bellWidth * 0.16f,
                        -bellHeight * 0.55f
                    )
                    canvas.drawRoundRect(handleRect, bellWidth * 0.12f, bellWidth * 0.12f, overlayPaint)

                    val clapperPaint = Paint(overlayPaint).apply {
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 215)
                    }
                    canvas.drawCircle(0f, bellHeight * 0.55f, size * 0.09f, clapperPaint)
                    true
                }
                "note" -> {
                    val headRadius = size * 0.2f
                    val stemHeight = size * 0.6f
                    val stemWidth = headRadius * 0.4f
                    val primaryHead = PointF(headRadius * 0.55f, stemHeight / 2f - headRadius * 0.1f)
                    val secondaryHead = PointF(
                        primaryHead.x - headRadius * 0.95f,
                        primaryHead.y + headRadius * 0.7f
                    )

                    val stemRect = RectF(
                        primaryHead.x - stemWidth / 2f,
                        -stemHeight / 2f,
                        primaryHead.x + stemWidth / 2f,
                        primaryHead.y
                    )
                    canvas.drawRoundRect(stemRect, stemWidth, stemWidth, overlayPaint)
                    canvas.drawCircle(primaryHead.x, primaryHead.y, headRadius, overlayPaint)
                    canvas.drawCircle(secondaryHead.x, secondaryHead.y, headRadius * 0.95f, overlayPaint)

                    val beamPath = Path()
                    beamPath.moveTo(primaryHead.x + stemWidth / 2f, -stemHeight / 2f)
                    beamPath.lineTo(primaryHead.x + stemWidth * 1.6f, -stemHeight / 3f)
                    beamPath.lineTo(primaryHead.x + stemWidth * 1.6f, -stemHeight / 4f)
                    beamPath.lineTo(primaryHead.x + stemWidth / 2f, -stemHeight / 6f)
                    beamPath.close()
                    canvas.drawPath(beamPath, overlayPaint)
                    true
                }
                "ghost" -> {
                    val ghostWidth = size * 0.6f
                    val ghostHeight = size * 0.7f
                    val ghostPath = Path()
                    ghostPath.moveTo(-ghostWidth / 2f, ghostHeight * 0.3f)
                    ghostPath.quadTo(-ghostWidth / 2f, -ghostHeight / 2f, 0f, -ghostHeight / 2f)
                    ghostPath.quadTo(ghostWidth / 2f, -ghostHeight / 2f, ghostWidth / 2f, ghostHeight * 0.3f)
                    ghostPath.lineTo(ghostWidth * 0.3f, ghostHeight / 2f)
                    ghostPath.lineTo(0f, ghostHeight * 0.35f)
                    ghostPath.lineTo(-ghostWidth * 0.3f, ghostHeight / 2f)
                    ghostPath.close()
                    canvas.drawPath(ghostPath, overlayPaint)

                    val eyePaint = Paint().apply { color = Color.WHITE }
                    val pupilPaint = Paint().apply { color = ColorUtils.blendARGB(iconColor, Color.BLACK, 0.55f) }
                    val eyeRadius = ghostWidth * 0.12f
                    val pupilRadius = eyeRadius * 0.45f
                    val eyeY = -ghostHeight * 0.12f
                    canvas.drawCircle(-ghostWidth * 0.18f, eyeY, eyeRadius, eyePaint)
                    canvas.drawCircle(ghostWidth * 0.18f, eyeY, eyeRadius, eyePaint)
                    canvas.drawCircle(-ghostWidth * 0.18f, eyeY, pupilRadius, pupilPaint)
                    canvas.drawCircle(ghostWidth * 0.18f, eyeY, pupilRadius, pupilPaint)
                    true
                }
                "cat" -> {
                    val headRadius = size * 0.36f

                    val leftEar = Path()
                    leftEar.moveTo(-headRadius * 0.6f, -headRadius * 0.2f)
                    leftEar.lineTo(-headRadius * 1.2f, -headRadius * 0.95f)
                    leftEar.lineTo(-headRadius * 0.2f, -headRadius * 0.65f)
                    leftEar.close()

                    val rightEar = Path()
                    rightEar.moveTo(headRadius * 0.6f, -headRadius * 0.2f)
                    rightEar.lineTo(headRadius * 1.2f, -headRadius * 0.95f)
                    rightEar.lineTo(headRadius * 0.2f, -headRadius * 0.65f)
                    rightEar.close()

                    canvas.drawPath(leftEar, overlayPaint)
                    canvas.drawPath(rightEar, overlayPaint)
                    canvas.drawCircle(0f, 0f, headRadius, overlayPaint)

                    val eyePaint = Paint().apply { color = Color.WHITE }
                    val detailColor = ColorUtils.blendARGB(iconColor, Color.BLACK, 0.55f)
                    val pupilPaint = Paint().apply { color = detailColor }
                    val eyeRadius = headRadius * 0.18f
                    val pupilRadius = eyeRadius * 0.55f
                    val eyeY = -headRadius * 0.05f
                    canvas.drawCircle(-headRadius * 0.4f, eyeY, eyeRadius, eyePaint)
                    canvas.drawCircle(headRadius * 0.4f, eyeY, eyeRadius, eyePaint)
                    canvas.drawCircle(-headRadius * 0.4f, eyeY, pupilRadius, pupilPaint)
                    canvas.drawCircle(headRadius * 0.4f, eyeY, pupilRadius, pupilPaint)

                    val nosePaint = Paint().apply { color = detailColor }
                    val nosePath = Path()
                    nosePath.moveTo(0f, headRadius * 0.18f)
                    nosePath.lineTo(-headRadius * 0.12f, headRadius * 0.05f)
                    nosePath.lineTo(headRadius * 0.12f, headRadius * 0.05f)
                    nosePath.close()
                    canvas.drawPath(nosePath, nosePaint)

                    val whiskerPaint = Paint().apply {
                        color = ColorUtils.setAlphaComponent(detailColor, 200)
                        strokeWidth = headRadius * 0.08f
                        strokeCap = Paint.Cap.ROUND
                        style = Paint.Style.STROKE
                    }
                    canvas.drawLine(-headRadius * 0.5f, headRadius * 0.2f, -headRadius * 1.0f, headRadius * 0.1f, whiskerPaint)
                    canvas.drawLine(-headRadius * 0.5f, headRadius * 0.3f, -headRadius * 1.0f, headRadius * 0.35f, whiskerPaint)
                    canvas.drawLine(headRadius * 0.5f, headRadius * 0.2f, headRadius * 1.0f, headRadius * 0.1f, whiskerPaint)
                    canvas.drawLine(headRadius * 0.5f, headRadius * 0.3f, headRadius * 1.0f, headRadius * 0.35f, whiskerPaint)
                    true
                }
                "candy" -> {
                    val candyRadius = size * 0.26f
                    canvas.drawCircle(0f, 0f, candyRadius, overlayPaint)

                    val leftWrapper = Path()
                    leftWrapper.moveTo(-candyRadius, -candyRadius * 0.7f)
                    leftWrapper.lineTo(-candyRadius - candyRadius * 0.9f, 0f)
                    leftWrapper.lineTo(-candyRadius, candyRadius * 0.7f)
                    leftWrapper.close()
                    canvas.drawPath(leftWrapper, overlayPaint)

                    val rightWrapper = Path()
                    rightWrapper.moveTo(candyRadius, -candyRadius * 0.7f)
                    rightWrapper.lineTo(candyRadius + candyRadius * 0.9f, 0f)
                    rightWrapper.lineTo(candyRadius, candyRadius * 0.7f)
                    rightWrapper.close()
                    canvas.drawPath(rightWrapper, overlayPaint)

                    val swirlPaint = Paint(overlayPaint).apply {
                        color = ColorUtils.setAlphaComponent(Color.WHITE, 200)
                        style = Paint.Style.STROKE
                        strokeWidth = candyRadius * 0.22f
                        strokeCap = Paint.Cap.ROUND
                    }
                    val swirlRect = RectF(
                        -candyRadius * 0.55f,
                        -candyRadius * 0.55f,
                        candyRadius * 0.55f,
                        candyRadius * 0.55f
                    )
                    canvas.drawArc(swirlRect, 210f, 210f, false, swirlPaint)
                    true
                }
                else -> false
            }

            canvas.restore()
            return consumed
        }

        /**
         * ⚡ PERFORMANCE: Reuses cached path to prevent GC allocation during draw
         */
        private fun createStarPath(radius: Float, sparkle: Boolean): Path {
            val path = if (sparkle) cachedSparklePath else cachedStarPath
            path.reset()
            val points = if (sparkle) 4 else 5
            val innerRadius = radius * if (sparkle) 0.42f else 0.5f
            val totalPoints = points * 2
            var angle = -PI.toFloat() / 2
            val step = PI.toFloat() / points
            path.moveTo(0f, -radius)
            for (i in 1 until totalPoints) {
                angle += step
                val currentRadius = if (i % 2 == 0) radius else innerRadius
                val x = (cos(angle.toDouble()) * currentRadius).toFloat()
                val y = (sin(angle.toDouble()) * currentRadius).toFloat()
                path.lineTo(x, y)
            }
            path.close()
            return path
        }

        /**
         * ⚡ PERFORMANCE: Reuses cached path and RectF to prevent GC allocation during draw
         */
        private fun createHeartPath(size: Float): Path {
            val radius = size / 2f
            cachedArcRectLeft.set(-radius, -radius, 0f, 0f)
            cachedArcRectRight.set(0f, -radius, radius, 0f)
            cachedHeartPath.reset()
            cachedHeartPath.addArc(cachedArcRectLeft, 180f, 180f)
            cachedHeartPath.addArc(cachedArcRectRight, 180f, 180f)
            cachedHeartPath.lineTo(0f, radius * 1.6f)
            cachedHeartPath.close()
            return cachedHeartPath
        }

        private fun buildOverlayState(palette: ThemePaletteV2, key: DynamicKey): Map<String, List<OverlayElement>> {
            // ⚡ PERFORMANCE: Skip overlay generation entirely if no effects are enabled
            if (palette.globalEffects.isEmpty()) return emptyMap()
            if (!tapEffectsEnabled && palette.globalEffectsOpacity <= 0f) return emptyMap()
            val keyRect = getKeyRect(key)
            val overlays = mutableMapOf<String, List<OverlayElement>>()

            palette.globalEffects.forEach { effect ->
                when (effect.lowercase()) {
                    "stars" -> {
                        val elements = generateStarOverlay(keyRect, sparkle = false)
                        if (elements.isNotEmpty()) overlays["stars"] = elements
                    }
                    "sparkles" -> {
                        val elements = generateStarOverlay(keyRect, sparkle = true)
                        if (elements.isNotEmpty()) overlays["sparkles"] = elements
                    }
                    "hearts" -> {
                        val elements = generateHeartOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["hearts"] = elements
                    }
                    "bubbles" -> {
                        val elements = generateBubbleOverlay(keyRect, palette)
                        if (elements.isNotEmpty()) overlays["bubbles"] = elements
                    }
                    "leaves" -> {
                        val elements = generateLeafOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["leaves"] = elements
                    }
                    "snow" -> {
                        val elements = generateSnowOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["snow"] = elements
                    }
                    "lightning" -> {
                        val elements = generateLightningOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["lightning"] = elements
                    }
                    "confetti" -> {
                        val elements = generateConfettiOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["confetti"] = elements
                    }
                    "butterflies" -> {
                        val elements = generateButterflyOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["butterflies"] = elements
                    }
                    "rainbow" -> {
                        val elements = generateRainbowOverlay(keyRect)
                        if (elements.isNotEmpty()) overlays["rainbow"] = elements
                    }
                }
            }

            return overlays
        }

        private fun generateStarOverlay(keyRect: RectF, sparkle: Boolean): List<OverlayElement> {
            val count = if (sparkle) 11 else 8
            val spread = max(keyRect.width(), keyRect.height())
            val minSize = min(keyRect.width(), keyRect.height()) * (if (sparkle) 0.12f else 0.18f)
            val maxSize = min(keyRect.width(), keyRect.height()) * (if (sparkle) 0.22f else 0.28f)
            val radiusRange = spread * (if (sparkle) 0.65f else 0.85f)

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * radiusRange
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minSize + Random.nextFloat() * (maxSize - minSize)
                val rotation = Random.nextFloat() * 360f
                OverlayElement(dx, dy, size, rotation)
            }
        }

        private fun generateHeartOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 9
            val spread = max(keyRect.width(), keyRect.height())
            val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
            val maxSize = min(keyRect.width(), keyRect.height()) * 0.3f
            val radiusRange = spread * 0.75f
            val palette = intArrayOf(
                Color.parseColor("#FF7AAE"),
                Color.parseColor("#FF4F93"),
                Color.parseColor("#FF9FC5")
            )

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * radiusRange
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minSize + Random.nextFloat() * (maxSize - minSize)
                val rotation = Random.nextFloat() * 30f - 15f
                val color = palette[Random.nextInt(palette.size)]
                OverlayElement(dx, dy, size, rotation, color)
            }
        }

        private fun generateBubbleOverlay(keyRect: RectF, palette: ThemePaletteV2): List<OverlayElement> {
            val count = 9
            val spread = max(keyRect.width(), keyRect.height())
            val minRadius = min(keyRect.width(), keyRect.height()) * 0.18f
            val maxRadius = min(keyRect.width(), keyRect.height()) * 0.26f
            val radiusRange = spread * 0.9f

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * radiusRange
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minRadius + Random.nextFloat() * (maxRadius - minRadius)
                val rotation = 0f
                val accent = ColorUtils.setAlphaComponent(palette.specialAccent, 160 + Random.nextInt(60))
                OverlayElement(dx, dy, size, rotation, accent)
            }
        }

        private fun generateLeafOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 9
            val spread = max(keyRect.width(), keyRect.height())
            val minSize = min(keyRect.width(), keyRect.height()) * 0.2f
            val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
            val radiusRange = spread * 0.85f
            val palette = intArrayOf(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#81C784"),
                Color.parseColor("#66BB6A"),
                Color.parseColor("#A5D6A7")
            )

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * radiusRange
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minSize + Random.nextFloat() * (maxSize - minSize)
                val rotation = Random.nextFloat() * 360f
                val color = palette[Random.nextInt(palette.size)]
                OverlayElement(dx, dy, size, rotation, color)
            }
        }

        private fun generateSnowOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 8
            val spread = max(keyRect.width(), keyRect.height())
            val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
            val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
            val radiusRange = spread * 0.8f

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * radiusRange
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minSize + Random.nextFloat() * (maxSize - minSize)
                OverlayElement(dx, dy, size, 0f, Color.WHITE)
            }
        }

        private fun generateLightningOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 5
            val spread = max(keyRect.width(), keyRect.height()) * 0.5f
            val palette = intArrayOf(
                Color.parseColor("#FFD740"),
                Color.parseColor("#FFEA00"),
                Color.parseColor("#FFC400")
            )

            return List(count) {
                val dx = Random.nextFloat() * spread - spread / 2f
                val dy = Random.nextFloat() * spread - spread / 2f
                val size = min(keyRect.width(), keyRect.height()) * (0.25f + Random.nextFloat() * 0.15f)
                val rotation = Random.nextFloat() * 40f - 20f

                val segments = FloatArray(8).apply {
                    this[0] = 0f
                    this[1] = -size * 0.6f
                    this[2] = size * (0.15f + Random.nextFloat() * 0.2f)
                    this[3] = -size * 0.15f
                    this[4] = -size * (0.2f + Random.nextFloat() * 0.1f)
                    this[5] = size * (0.1f + Random.nextFloat() * 0.1f)
                    this[6] = size * (0.18f + Random.nextFloat() * 0.15f)
                    this[7] = size * 0.6f
                }

                OverlayElement(dx, dy, size, rotation, palette[Random.nextInt(palette.size)], segments)
            }
        }

        private fun generateConfettiOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 14
            val spread = max(keyRect.width(), keyRect.height())
            val palette = intArrayOf(
                Color.parseColor("#FF6F61"),
                Color.parseColor("#F7B32B"),
                Color.parseColor("#4ECDC4"),
                Color.parseColor("#845EC2"),
                Color.parseColor("#FF9671")
            )

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * spread * 0.95f
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = min(keyRect.width(), keyRect.height()) * (0.18f + Random.nextFloat() * 0.1f)
                val rotation = Random.nextFloat() * 360f
                val color = palette[Random.nextInt(palette.size)]
                val width = size * (0.8f + Random.nextFloat() * 0.4f)
                val height = size * (0.35f + Random.nextFloat() * 0.25f)
                OverlayElement(dx, dy, size, rotation, color, floatArrayOf(width, height))
            }
        }

        private fun generateButterflyOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 6
            val spread = max(keyRect.width(), keyRect.height()) * 0.9f
            val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
            val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
            val palette = intArrayOf(
                Color.parseColor("#FFB6C1"),
                Color.parseColor("#FFCCBC"),
                Color.parseColor("#B39DDB"),
                Color.parseColor("#90CAF9")
            )

            return List(count) {
                val angle = Random.nextFloat() * (PI.toFloat() * 2f)
                val distance = Random.nextFloat() * spread
                val dx = (cos(angle.toDouble()) * distance).toFloat()
                val dy = (sin(angle.toDouble()) * distance).toFloat()
                val size = minSize + Random.nextFloat() * (maxSize - minSize)
                val rotation = Random.nextFloat() * 360f
                val color = palette[Random.nextInt(palette.size)]
                OverlayElement(dx, dy, size, rotation, color)
            }
        }

        private fun generateRainbowOverlay(keyRect: RectF): List<OverlayElement> {
            val count = 3
            val spread = max(keyRect.width(), keyRect.height()) * 0.6f

            return List(count) {
                val dx = Random.nextFloat() * spread - spread / 2f
                val dy = Random.nextFloat() * spread / 1.2f - spread / 2.4f
                val size = min(keyRect.width(), keyRect.height()) * (0.35f + Random.nextFloat() * 0.1f)
                val rotation = Random.nextFloat() * 40f - 20f
                val thickness = size * (0.1f + Random.nextFloat() * 0.05f)
                OverlayElement(dx, dy, size, rotation, null, floatArrayOf(thickness))
            }
        }

        private fun computeBounceScale(progress: Float): Float {
            return if (progress < 0.5f) {
                1f - 0.08f * (progress / 0.5f)
            } else {
                0.92f + 0.08f * ((progress - 0.5f) / 0.5f)
            }
        }

        private fun startTapEffect(key: DynamicKey) {
            val palette = themeManager.getCurrentPalette()
            val overlays = buildOverlayState(palette, key)
            if (!tapEffectsEnabled && overlays.isEmpty()) return
            val duration = when (tapEffectStyle) {
                UnifiedKeyboardView.TapEffectStyle.NONE -> 520L
                UnifiedKeyboardView.TapEffectStyle.RIPPLE -> 520L
                UnifiedKeyboardView.TapEffectStyle.GLOW -> 560L
                UnifiedKeyboardView.TapEffectStyle.BOUNCE -> 540L
            }
            tapEffectState = TapEffectState(
                key = key,
                startTime = SystemClock.uptimeMillis(),
                duration = duration,
                overlays = overlays
            )
            postInvalidateOnAnimation()
        }
        
        /**
         * Get key identifier for per-key customization lookup
         * Converts key label to a standardized identifier
         */
        private fun getKeyIdentifier(key: DynamicKey): String {
            // For special keys, use their key type
            return when (key.keyType) {
                "space" -> "space"
                "enter" -> "enter"
                "shift" -> "shift"
                "backspace" -> "backspace"
                "globe" -> "globe"
                "emoji" -> "emoji"
                "mic" -> "mic"
                "symbols" -> "symbols"
                else -> {
                    // For letter/number keys, use the lowercase label
                    key.label.lowercase().take(1) // Take first character
                }
            }
        }

        private fun drawKeyIcon(canvas: Canvas, key: DynamicKey, keyRect: RectF, iconResId: Int, palette: ThemePaletteV2) {
            val iconDrawable = ContextCompat.getDrawable(context, iconResId)?.mutate() ?: return
            val centerX = keyRect.centerX()
            val centerY = keyRect.centerY()
            val targetSizeDp = if (largeIconKeyTypes.contains(key.keyType)) 36 else 28
            val desiredSizePx = dpToPx(targetSizeDp)
            val maxDrawableExtent = (min(key.width, key.height) - dpToPx(6)).coerceAtLeast(dpToPx(20))
            val iconSize = min(desiredSizePx.toFloat(), maxDrawableExtent.toFloat())

            val accentForeground = if (borderlessMode) palette.specialAccent else getAccentForegroundColor(palette)
            val shouldAccent = themeManager.shouldUseAccentForKey(key.keyType)
            val tintColor = when {
                key.keyType == "space" && showLanguageOnSpace -> palette.spaceLabelColor
                shouldAccent -> accentForeground
                key.keyType == "enter" || key.keyType == "shift" -> palette.specialAccent
                else -> palette.keyText
            }

            iconDrawable.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            iconDrawable.setBounds(
                (centerX - iconSize / 2f).roundToInt(),
                (centerY - iconSize / 2f).roundToInt(),
                (centerX + iconSize / 2f).roundToInt(),
                (centerY + iconSize / 2f).roundToInt()
            )
            iconDrawable.draw(canvas)

            // Language label on space
            if (key.keyType == "space" && showLanguageOnSpace && currentLanguageLabel.isNotEmpty()) {
                val textPaint = Paint(parentView.spaceLabelPaint).apply {
                    // ✅ Use theme font (already set in spaceLabelPaint)
                    textSize = parentView.spaceLabelPaint.textSize * labelScaleMultiplier * 0.7f
                    color = palette.spaceLabelColor
                    textAlign = Paint.Align.CENTER
                }
                val baselineShift = dpToPx(1).toFloat()
                canvas.drawText(currentLanguageLabel, centerX, centerY + iconSize/2 + textPaint.textSize + baselineShift, textPaint)
            }
        }

        private fun drawKeyText(canvas: Canvas, key: DynamicKey, keyRect: RectF, palette: ThemePaletteV2) {
            // ✅ Get key identifier for per-key customization
            val keyIdentifier = getKeyIdentifier(key)
            
            // ✅ Use per-key customized text paint
            val textPaint = if (key.keyType == "space") {
                Paint(parentView.spaceLabelPaint)
            } else {
                themeManager.createKeyTextPaint(keyIdentifier) // ✅ Use per-key font customization
            }
            
            // ✅ Use theme font (already set in textPaint) instead of hardcoding
            // Apply label scale to respect user's font size preference
            textPaint.textSize = textPaint.textSize * labelScaleMultiplier

            val accentForeground = if (borderlessMode) palette.specialAccent else getAccentForegroundColor(palette)
            val shouldAccent = themeManager.shouldUseAccentForKey(key.keyType)
            textPaint.color = when {
                key.keyType == "space" && showLanguageOnSpace -> palette.spaceLabelColor
                shouldAccent -> accentForeground
                key.keyType == "enter" || key.keyType == "shift" -> palette.specialAccent
                else -> themeManager.getTextColor(keyIdentifier)
            }

            val text = if (key.keyType == "space" && showLanguageOnSpace) currentLanguageLabel else key.label

            val centerX = keyRect.centerX()
            val centerY = keyRect.centerY()
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textOffset = (textHeight / 2) - textPaint.descent()
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, centerX, centerY + textOffset + dpToPx(1).toFloat(), textPaint)

            drawHintLabel(canvas, key, keyRect, textPaint)
        }

        private fun shouldShowHint(key: DynamicKey): Boolean {
            val hint = key.hintLabel?.trim() ?: return false
            if (hint.isEmpty()) return false
            if (key.keyType != "regular") return false
            val isDigitHint = hint.all { it.isDigit() }
            return (isDigitHint && hintedNumberRow) || (!isDigitHint && hintedSymbols)
        }

        private fun drawHintLabel(canvas: Canvas, key: DynamicKey, keyRect: RectF, basePaint: Paint) {
            if (!shouldShowHint(key)) return
            val hint = key.hintLabel?.trim().orEmpty()
            if (hint.isEmpty()) return

            val hintPaint = Paint(basePaint).apply {
                textSize = (basePaint.textSize * 0.45f).coerceAtLeast(dpToPx(6f))
                typeface = Typeface.create(basePaint.typeface, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
                color = ColorUtils.setAlphaComponent(basePaint.color, (basePaint.alpha * 0.75f).toInt().coerceIn(0, 255))
            }

            val hintX = keyRect.right - dpToPx(4f)
            val hintY = keyRect.top + hintPaint.textSize + dpToPx(1f)
            canvas.drawText(hint.take(2), hintX, hintY, hintPaint)
        }

        private fun getAccentForegroundColor(palette: ThemePaletteV2): Int {
            val luminance = ColorUtils.calculateLuminance(palette.specialAccent)
            return if (luminance > 0.5) Color.BLACK else Color.WHITE
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Handle accent popup sliding selection
            if (accentPopup?.isShowing == true) {
                return handleAccentPopupTouch(event)
            }
            
            // ✅ MULTI-TOUCH FIX: Use actionMasked to detect secondary pointers
            val action = event.actionMasked
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            
            return when (action) {
                MotionEvent.ACTION_DOWN -> {
                    // Primary pointer down - handles first finger touch
                    val x = event.getX(actionIndex).toInt()
                    val y = event.getY(actionIndex).toInt()
                    val key = findKeyAtPosition(x, y)
                    swipeEligibleForCurrentGesture = key?.let {
                        val normalizedType = it.keyType.lowercase()
                        !nonSwipeKeyTypes.contains(normalizedType)
                    } ?: false

                    if (key != null) {
                        // ✅ Track this pointer -> key association for multi-touch
                        activePointers[pointerId] = key
                        handleKeyDown(key)
                        if (parentView.swipeEnabled) {
                            startSwipeTracking(event.getX(actionIndex), event.getY(actionIndex))
                            if (!swipeEligibleForCurrentGesture) {
                                cancelTrailFade()
                                fingerPoints.clear()
                                isSwipeActive = false
                                invalidate()
                            }
                        } else {
                            cancelTrailFade()
                            fingerPoints.clear()
                            isSwipeActive = false
                            invalidate()
                        }
                        true
                    } else {
                        swipeEligibleForCurrentGesture = false
                        false
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // ✅ MULTI-TOUCH FIX: Secondary pointer down - handles additional fingers
                    // This is critical for rollover typing where you press the next key
                    // before lifting the previous finger
                    val x = event.getX(actionIndex).toInt()
                    val y = event.getY(actionIndex).toInt()
                    val key = findKeyAtPosition(x, y)
                    
                    if (key != null) {
                        // Track this pointer -> key association
                        activePointers[pointerId] = key
                        handleKeyDown(key)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // ✅ Hide key preview on move (like Gboard)
                    hideKeyPreview()
                    
                    if (!swipeEligibleForCurrentGesture || !parentView.swipeEnabled) {
                        return true
                    }
                    // Continue swipe tracking (only for primary pointer)
                    if (!isSwipeActive && fingerPoints.isNotEmpty()) {
                        val st = fingerPoints[0]
                        val dx = event.getX(0) - st[0]
                        val dy = event.getY(0) - st[1]
                        val dist = sqrt(dx*dx + dy*dy)
                        if (dist > SWIPE_START_THRESHOLD) {
                            isSwipeActive = true
                            // Cancel long press when swipe starts
                            cancelLongPressInternal()
                            parentView.swipeListener?.onSwipeStarted()
                        }
                    }
                    
                    if (isSwipeActive) {
                        fingerPoints.add(floatArrayOf(event.getX(0), event.getY(0)))
                        invalidate() // Redraw for swipe trail
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Primary pointer up - last finger lifted
                    // ✅ NEW: Hide key preview popup
                    hideKeyPreview()
                    
                    val x = event.getX(actionIndex).toInt()
                    val y = event.getY(actionIndex).toInt()
                    val key = findKeyAtPosition(x, y)
                    
                    // Stop delete repeat
                    stopDeleteRepeat()
                    
                    // ✅ FIX: Track if swipe was successfully handled
                    var handledAsSwipe = false
                    
                    if (swipeEligibleForCurrentGesture && isSwipeActive && fingerPoints.size > 1) {
                        // Try to complete swipe - returns false if swipe was too short/invalid
                        handledAsSwipe = completeSwipe()
                    }
                    
                    // ✅ FIX: If it wasn't a valid swipe (or wasn't a swipe at all), treat as TAP
                    // This prevents "dead zone" bug where fast taps are lost
                    if (!handledAsSwipe) {
                        // ✅ MULTI-TOUCH: Try to get key from tracked pointer first
                        val trackedKey = activePointers[pointerId]
                        val keyToUse = trackedKey ?: key
                        
                        if (keyToUse != null) {
                            // Handle tap - handleKeyUp will check if popup is showing and insert selected option
                            handleKeyUp(keyToUse)
                            fingerPoints.clear()
                            invalidate()
                        } else if (accentPopup?.isShowing == true && activeAccentKey != null) {
                            // If popup is showing but key is null, still handle release
                            handleKeyUp(activeAccentKey!!)
                            fingerPoints.clear()
                            invalidate()
                        } else {
                            fingerPoints.clear()
                            invalidate()
                        }
                    }

                    // ✅ Clean up this pointer from tracking
                    activePointers.remove(pointerId)
                    
                    // Only cancel long press if popup is not showing (popup handles its own cleanup)
                    if (accentPopup?.isShowing != true) {
                        cancelLongPressInternal()
                    }
                    isSwipeActive = false
                    swipeEligibleForCurrentGesture = false
                    // ✅ Clear all pointers on final UP (all fingers lifted)
                    activePointers.clear()
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // ✅ MULTI-TOUCH FIX: Secondary pointer up - one of multiple fingers lifted
                    // This handles the key release for rollover typing
                    val x = event.getX(actionIndex).toInt()
                    val y = event.getY(actionIndex).toInt()
                    
                    // ✅ Cancel long press timer when any finger is lifted during multi-touch
                    // This prevents accidental long press popups during fast rollover typing
                    cancelLongPressTimer()
                    
                    // Get the key that was tracked for this pointer
                    val trackedKey = activePointers[pointerId]
                    val key = trackedKey ?: findKeyAtPosition(x, y)
                    
                    if (key != null) {
                        // Stop delete repeat if this was the delete key
                        if (key.keyType == "backspace") {
                            stopDeleteRepeat()
                        } else {
                            // Handle key up for this specific pointer's key
                            handleKeyUp(key)
                        }
                    }
                    
                    // Clean up this pointer from tracking
                    activePointers.remove(pointerId)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // ✅ NEW: Hide key preview popup
                    hideKeyPreview()
                    
                    stopDeleteRepeat()
                    cancelLongPressInternal()
                    isSwipeActive = false
                    fingerPoints.clear()
                    swipeEligibleForCurrentGesture = false
                    // ✅ MULTI-TOUCH: Clear all tracked pointers on cancel
                    activePointers.clear()
                    invalidate()
                    true
                }
                else -> super.onTouchEvent(event)
            }
        }
        
        private fun startSwipeTracking(x: Float, y: Float) {
            cancelTrailFade()
            fingerPoints.clear()
            fingerPoints.add(floatArrayOf(x, y))
            swipeStartTime = System.currentTimeMillis()
            isSwipeActive = false
        }

        private fun cancelTrailFade() {
            trailRunnable?.let { trailHandler.removeCallbacks(it) }
            trailRunnable = null
        }

        private fun scheduleTrailFade() {
            cancelTrailFade()
            if (!parentView.showGlideTrailSetting || parentView.glideTrailFadeMs <= 0L) {
                fingerPoints.clear()
                invalidate()
                return
            }
            trailRunnable = Runnable {
                fingerPoints.clear()
                invalidate()
            }
            trailHandler.postDelayed(trailRunnable!!, parentView.glideTrailFadeMs)
        }

        fun clearSwipeTrail() {
            cancelTrailFade()
            fingerPoints.clear()
            invalidate()
        }

        /**
         * Completes the swipe gesture and returns whether it was successfully handled as a swipe.
         * @return true if the swipe was valid and processed, false if it should be treated as a tap instead
         */
        private fun completeSwipe(): Boolean {
            if (fingerPoints.size < 2) {
                fingerPoints.clear()
                invalidate()
                return false // Not a valid swipe - fallback to tap
            }

            val durationMs = (System.currentTimeMillis() - swipeStartTime).coerceAtLeast(1L)
            val distancePx = computeTotalDistance()
            val velocity = (distancePx / durationMs) * 1000f
            val meetsDistance = distancePx >= parentView.swipeDistanceThresholdPx
            val meetsVelocity = velocity >= parentView.swipeVelocityThresholdPxPerSec

            val normalized = fingerPoints.map { p ->
                Pair(p[0] / width.coerceAtLeast(1).toFloat(), p[1] / height.coerceAtLeast(1).toFloat())
            }
            val keySeq = resolveKeySequence(normalized)

            val treatAsTyping = parentView.gestureSettings.glideTyping &&
                keySeq.size > 1 &&
                (meetsDistance || meetsVelocity)

            if (treatAsTyping) {
                parentView.swipeListener?.onSwipeDetected(keySeq, normalized, isPreview = false) // Final swipe
                parentView.handleSwipeSuggestions(keySeq, normalized)
                if (parentView.showGlideTrailSetting) {
                    scheduleTrailFade()
                } else {
                    fingerPoints.clear()
                    invalidate()
                }
                parentView.swipeListener?.onSwipeEnded()
                isSwipeActive = false
                return true // Successfully handled as swipe
            } else {
                // Check if it's a valid gesture (like directional swipe)
                val source = determineGestureSource()
                if (source != null) {
                    parentView.gestureHandler?.invoke(source)
                    if (parentView.showGlideTrailSetting) {
                        scheduleTrailFade()
                    } else {
                        fingerPoints.clear()
                        invalidate()
                    }
                    parentView.swipeListener?.onSwipeEnded()
                    isSwipeActive = false
                    return true // Successfully handled as gesture
                }
                
                // Not a valid swipe or gesture - clean up and return false so it can be treated as tap
                if (parentView.showGlideTrailSetting) {
                    scheduleTrailFade()
                } else {
                    fingerPoints.clear()
                    invalidate()
                }
                parentView.swipeListener?.onSwipeEnded()
                isSwipeActive = false
                return false // Not a valid swipe - fallback to tap
            }
        }

        private fun computeTotalDistance(): Float {
            if (fingerPoints.size < 2) return 0f
            var total = 0f
            for (i in 1 until fingerPoints.size) {
                val prev = fingerPoints[i - 1]
                val curr = fingerPoints[i]
                val dx = curr[0] - prev[0]
                val dy = curr[1] - prev[1]
                total += sqrt(dx * dx + dy * dy)
            }
            return total
        }

        private fun determineGestureSource(): GestureSource? {
            val start = fingerPoints.firstOrNull() ?: return null
            val end = fingerPoints.lastOrNull() ?: return null
            val dx = end[0] - start[0]
            val dy = end[1] - start[1]
            val absDx = abs(dx)
            val absDy = abs(dy)

            val threshold = parentView.swipeDistanceThresholdPx
            if (absDx < threshold && absDy < threshold) {
                return null
            }

            return if (absDx > absDy) {
                if (dx > 0) GestureSource.GENERAL_SWIPE_RIGHT else GestureSource.GENERAL_SWIPE_LEFT
            } else {
                if (dy > 0) GestureSource.GENERAL_SWIPE_DOWN else GestureSource.GENERAL_SWIPE_UP
            }
        }

        private fun handleAccentPopupTouch(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    updateAccentSelection(event.rawX)
                }
                MotionEvent.ACTION_UP -> {
                    val selectedOption = activeAccentOptions.getOrNull(selectedAccentIndex)
                    val charCode = selectedOption?.firstOrNull()?.code

                    if (charCode != null) {
                        onKeyCallback?.invoke(charCode, intArrayOf(charCode))
                    } else {
                        activeAccentKey?.let { key ->
                            onKeyCallback?.invoke(key.code, intArrayOf(key.code))
                        }
                    }
                    hideAccentPopup()
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideAccentPopup()
                }
            }
            return true
        }

        private fun updateAccentSelection(rawX: Float) {
            if (accentOptionViews.isEmpty()) return

            val location = IntArray(2)
            var closestIndex = -1
            var closestDistance = Float.MAX_VALUE

            accentOptionViews.forEachIndexed { index, view ->
                view.getLocationOnScreen(location)
                val centerX = location[0] + view.width / 2f
                val distance = abs(rawX - centerX)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closestIndex = index
                }
            }

            if (closestIndex != -1) {
                updateAccentHighlight(closestIndex)
            }
        }

        private fun updateAccentHighlight(newIndex: Int, force: Boolean = false) {
            if (!force && newIndex == selectedAccentIndex) return

            val palette = themeManager.getCurrentPalette()

            accentOptionViews.forEachIndexed { index, view ->
                val isSelected = index == newIndex
                val background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(if (isSelected) palette.specialAccent else palette.keyboardBg)
                }
                view.background = background
                view.setTextColor(if (isSelected) Color.WHITE else palette.keyText)
            }

            if (newIndex != selectedAccentIndex && newIndex in accentOptionViews.indices) {
                this@KeyboardGridView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }

            selectedAccentIndex = newIndex
        }
        
        private fun endSwipe() {
            if (fingerPoints.isEmpty()) return
            
            val normalized = fingerPoints.map { p ->
                Pair(p[0] / width.coerceAtLeast(1).toFloat(), p[1] / height.coerceAtLeast(1).toFloat())
            }
            
            val keySeq = resolveKeySequence(normalized)
            if (parentView.swipeEnabled) {
                parentView.swipeListener?.onSwipeDetected(keySeq, normalized, isPreview = false) // Final swipe
                parentView.swipeListener?.onSwipeEnded()
            }
            fingerPoints.clear()
            isSwipeActive = false
        }
        
        fun resolveKeySequence(normalizedPath: List<Pair<Float, Float>>): List<Int> {
            val keySeq = mutableListOf<Int>()
            var lastCode = -1
            
            normalizedPath.forEach { (nx, ny) ->
                val x = (nx * width).toInt()
                val y = (ny * height).toInt()
                val key = findKeyAtPosition(x, y)
                if (key != null && key.code != lastCode && key.code > 0) {
                    keySeq.add(key.code)
                    lastCode = key.code
                }
            }
            
            return keySeq
        }

        private fun handleKeyDown(key: DynamicKey) {
            // ✅ MULTI-TOUCH FIX: Cancel any existing long press timer before starting a new one
            // This prevents unwanted long press popups during fast/rollover typing
            cancelLongPressTimer()
            
            // ✅ NEW: Schedule key preview popup with delay (like Gboard)
            scheduleKeyPreview(key)
            
            // Special handling for delete key - enable continuous repeat
            if (key.keyType == "backspace") {
                // Initial delete
                onKeyCallback?.invoke(key.code, intArrayOf(key.code))
                
                // Start repeat after delay
                deleteRepeatRunnable = object : Runnable {
                    override fun run() {
                        if (isDeleteRepeating) {
                            // ✅ PERFORMANCE: Reduced vibration frequency for better performance
                            // Only vibrate every 3rd repeat to avoid lag
                            val service = (context as? AIKeyboardService)
                            service?.let {
                                // Play sound if enabled
                                KeyboardSoundManager.play()
                                // Trigger vibration less frequently (every ~240ms instead of every 80ms)
                                if (System.currentTimeMillis() % 3 == 0L) {
                                    it.triggerRepeatedKeyVibration()
                                }
                            }
                            onKeyCallback?.invoke(key.code, intArrayOf(key.code))
                            deleteRepeatHandler.postDelayed(this, 80L) // ✅ PERFORMANCE: Increased from 50ms to 80ms
                        }
                    }
                }
                deleteRepeatHandler.postDelayed({
                    isDeleteRepeating = true
                    deleteRepeatRunnable?.run()
                }, 500L) // Start repeating after 500ms
                
                return
            }
            
            // Long press handling for accent options and number keys
            val hasLongPressOptions = !key.longPressOptions.isNullOrEmpty()
            val hasNumberHint = !key.hintLabel.isNullOrEmpty() && key.hintLabel?.trim()?.all { it.isDigit() } == true
            
            if (hasLongPressOptions || hasNumberHint) {
                longPressRunnable = Runnable { 
                    // ✅ Hide key preview when longpress triggers
                    hideKeyPreview()
                    showAccentOptions(key) 
                }
                longPressHandler.postDelayed(longPressRunnable!!, longPressDelayMs)
            }
        }
        
        private fun stopDeleteRepeat() {
            isDeleteRepeating = false
            deleteRepeatRunnable?.let { deleteRepeatHandler.removeCallbacks(it) }
            deleteRepeatRunnable = null
            deleteRepeatHandler.removeCallbacksAndMessages(null)
        }

        private fun handleKeyUp(key: DynamicKey) {
            // Stop delete repeat if it's the delete key
            if (key.keyType == "backspace") {
                stopDeleteRepeat()
                startTapEffect(key)
                return
            }
            
            // ✅ If popup is showing, insert the selected option (or number if auto-selected)
            if (accentPopup?.isShowing == true && activeAccentKey == key) {
                val selectedOption = activeAccentOptions.getOrNull(selectedAccentIndex)
                val charCode = selectedOption?.firstOrNull()?.code
                
                if (charCode != null) {
                    onKeyCallback?.invoke(charCode, intArrayOf(charCode))
                    hideAccentPopup()
                    startTapEffect(key)
                } else {
                    // Fallback to default key if no selection
                    onKeyCallback?.invoke(key.code, intArrayOf(key.code))
                    hideAccentPopup()
                    startTapEffect(key)
                }
            } else {
                // Normal tap - insert the letter
                cancelLongPressInternal()
                onKeyCallback?.invoke(key.code, intArrayOf(key.code))
                startTapEffect(key)
            }
        }

        private fun cancelLongPressInternal() {
            cancelLongPressTimer()
            hideAccentPopup()
            // ✅ Also hide key preview when longpress is cancelled
            hideKeyPreview()
        }

        private fun cancelLongPressTimer() {
            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
            longPressRunnable = null
        }

        private fun hideAccentPopup() {
            try {
                accentPopup?.dismiss()
                accentPopup = null
                activeAccentKey = null
                activeAccentOptions = emptyList()
                accentOptionViews.clear()
                selectedAccentIndex = -1
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing accent popup", e)
            }
        }

        private fun shouldAutoSelectFirstOption(key: DynamicKey, options: List<String>): Boolean {
            return instantLongPressSelectFirst
        }

        private fun showAccentOptions(key: DynamicKey) {
            // Build list of all available options: letter + number (if exists) + longPressOptions
            val allOptions = mutableListOf<String>()
            
            // Add the main letter/label first
            allOptions.add(key.label)
            
            // Add number (hintLabel) if it exists
            val numberHint = key.hintLabel?.trim()
            if (!numberHint.isNullOrEmpty() && numberHint.all { it.isDigit() }) {
                allOptions.add(numberHint)
            }
            
            // Add long-press options (accent variants, etc.)
            key.longPressOptions?.forEach { option ->
                if (!allOptions.contains(option)) {
                    allOptions.add(option)
                }
            }
            
            // If no options to show, return
            if (allOptions.isEmpty()) return

            // Dismiss any existing popup
            hideAccentPopup()
            activeAccentKey = key
            activeAccentOptions = allOptions

            val palette = themeManager.getCurrentPalette()
            
            // Create horizontal layout for accent options
            val popupBackground = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(palette.keyBg)
                setStroke(dpToPx(1), palette.keyBorderColor)
            }
            
            val optionsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = popupBackground
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                elevation = dpToPx(8).toFloat()
            }

            // Clear and prepare for new options
            accentOptionViews.clear()
            
            // ✅ Auto-select number (hintLabel) if it exists, otherwise select first option if enabled
            val numberIndex = if (!numberHint.isNullOrEmpty() && numberHint.all { it.isDigit() }) {
                allOptions.indexOfFirst { it == numberHint }
            } else {
                -1
            }
            
            val preselectFirst = shouldAutoSelectFirstOption(key, allOptions)
            selectedAccentIndex = when {
                numberIndex >= 0 -> numberIndex  // Auto-select number if available
                preselectFirst -> 0  // Otherwise select first if enabled
                else -> -1
            }

            // Add each option as a button
            allOptions.forEachIndexed { index, option ->
                val optionBackground = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(palette.keyboardBg)
                }
                
                val optionView = TextView(context).apply {
                    text = option
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(palette.keyText)
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    minWidth = dpToPx(44)
                    minHeight = dpToPx(44)
                    background = optionBackground
                    
                    setOnClickListener {
                        updateAccentHighlight(index, force = true)
                        // Insert the selected character
                        val charCode = option.firstOrNull()?.code ?: return@setOnClickListener
                        onKeyCallback?.invoke(charCode, intArrayOf(charCode))
                        hideAccentPopup()
                    }
                }
                
                accentOptionViews.add(optionView)
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) leftMargin = dpToPx(4)
                }
                optionsContainer.addView(optionView, params)
            }

            if (selectedAccentIndex >= 0) {
                updateAccentHighlight(selectedAccentIndex, force = true)
            } else {
                updateAccentHighlight(-1, force = true)
            }

            // Create and show popup
            accentPopup = PopupWindow(
                optionsContainer,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                isFocusable = false
                
                // Calculate position above the key
                val location = IntArray(2)
                this@KeyboardGridView.getLocationInWindow(location)
                
                val xPos = location[0] + key.x + (key.width / 2) - (optionsContainer.measuredWidth / 2)
                val yPos = location[1] + key.y - dpToPx(60)
                
                // Measure the content
                optionsContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                
                try {
                    showAtLocation(this@KeyboardGridView, Gravity.NO_GRAVITY, xPos, yPos)
                    Log.d(TAG, "✅ Accent popup shown with ${allOptions.size} options (number auto-selected: ${numberIndex >= 0})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing accent popup", e)
                }
            }
            
            // Provide haptic feedback
            this@KeyboardGridView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }

        private fun findKeyAtPosition(x: Int, y: Int): DynamicKey? {
            return dynamicKeys.firstOrNull { key ->
                x >= key.x && x < (key.x + key.width) && y >= key.y && y < (key.y + key.height)
            }
        }

        private fun getKeyWidthFactor(label: String, hasUtilityKey: Boolean = true): Float = when {
            label == " " || label == "SPACE" || label.startsWith("space") -> {
                if (hasUtilityKey) 4.0f else 5.0f  // Increased space bar width
            }
            label == "⏎" || label == "RETURN" || label == "sym_keyboard_return" -> 1.3f  // Return button
            label == "⇧" || label == "SHIFT" -> 1.3f  // Increased to match return/?123
            label == "⌫" || label == "DELETE" -> 1.3f  // Increased to match return/?123
            label == "?123" || label == "ABC" || label == "=<" || label == "123" -> 1.3f  // Mode switch buttons
            label == "🌐" || label == "GLOBE" -> 1f
            label == "," || label == "." -> 0.8f  // Smaller comma/period buttons
            else -> 1.0f
        }

        private fun getKeyTypeFromCode(code: Int): String = when (code) {
            32 -> "space"
            -1 -> "shift"
            -5 -> "backspace"
            10, -4 -> "enter"
            -13, -16 -> "mic"
            -15 -> "emoji"
            -14 -> "globe"
            -10, -11, -12 -> "symbols"
            else -> "regular"
        }

        private fun getIconForKeyType(keyType: String, label: String): Int? = when (keyType) {
            "shift" -> R.drawable.sym_keyboard_shift
            "backspace" -> R.drawable.sym_keyboard_delete
            "enter" -> R.drawable.sym_keyboard_return
            "globe" -> R.drawable.sym_keyboard_globe
            else -> when (label.uppercase()) {
                "SHIFT", "⇧" -> R.drawable.sym_keyboard_shift
                "DELETE", "⌫" -> R.drawable.sym_keyboard_delete
                "RETURN", "SYM_KEYBOARD_RETURN" -> R.drawable.sym_keyboard_return
                "GLOBE", "🌐" -> R.drawable.sym_keyboard_globe
                else -> null
            }
        }
        
        private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

        private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )

        private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}
