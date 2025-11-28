package com.kvive.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.*

class SwipeKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : KeyboardView(context, attrs, defStyleAttr) {
    
    companion object {
        private const val MIN_SWIPE_TIME = 300L // Minimum time for swipe (ms)
        private const val MIN_SWIPE_DISTANCE = 100f // Minimum distance for swipe (pixels)
        private const val SWIPE_START_THRESHOLD = 50f // Movement threshold to start swipe
        
        // Adaptive sizing constants
        private const val SCREEN_SIZE_SMALL = 720
        private const val SCREEN_SIZE_NORMAL = 1080
        private const val SCREEN_SIZE_LARGE = 1440
        
        // Spacebar gesture constants
        private const val SPACEBAR_GESTURE_THRESHOLD = 50f
        private const val CURSOR_CONTROL_SENSITIVITY = 15f
        private const val WORD_DELETE_VELOCITY_THRESHOLD = -500f
        private const val PERIOD_INSERT_VELOCITY_THRESHOLD = 500f
        
        // 🔥 FIX: Swipe processing throttle to prevent lag
        private const val SWIPE_PROCESS_THROTTLE = 40L // Process every 40ms (~25fps) max
        
        // Real-time preview throttling  
        private const val PREVIEW_UPDATE_DELAY = 80L // Update suggestions every 80ms (~12fps) while swiping
    }
    
    interface SwipeListener {
        fun onSwipeDetected(
            swipedKeys: List<Int>, 
            swipePattern: String, 
            keySequence: List<Int> = swipedKeys,
            swipePath: List<Pair<Float, Float>> = emptyList(), // actual finger coordinates
            isPreview: Boolean = false // ✅ NEW: Flag to distinguish preview vs final
        )
        fun onSwipeStarted()
        fun onSwipeEnded()
    }
    
    private var swipeEnabled = true
    private var isSwipeInProgress = false
    private val swipePoints = mutableListOf<FloatArray>()
    private var lastSwipeProcessTime = 0L // 🔥 FIX: Throttle swipe processing
    private var lastPreviewTime = 0L // Real-time preview throttling
    private val swipePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val swipePath = Path()
    private var swipeListener: SwipeListener? = null
    private var swipeStartTime = 0L
    
    // Theme manager integration
    private var themeManager: ThemeManager? = null
    
    // Theme-aware paint objects (will be created by ThemeManager)
    private var keyTextPaint: Paint? = null
    private var suggestionTextPaint: Paint? = null
    private var spaceLabelPaint: Paint? = null
    
    // Enhanced features
    private var isAdaptiveSizingEnabled = true
    private var isFloatingMode = false
    private var isSplitMode = false
    private var isSmallScreenOptimized = false
    private var spacebarGestureEnabled = true
    private var tapEffectStyle: String = "ripple"
    private var tapEffectsEnabled: Boolean = true
    private var previewTapEffectType: String = "ripple"
    private var previewTapEffectOpacity: Float = 0f
    private var tapEffectProgress: Float = 0f
    private var tapEffectRadius: Float = 0f
    private var tapEffectX: Float = -1f
    private var tapEffectY: Float = -1f
    private val tapEffectPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var tapEffectAnimator: ValueAnimator? = null
    
    // Adaptive sizing
    private var adaptiveKeyWidth = 0f
    private var adaptiveKeyHeight = 0f
    private var touchTargetExpansion = 0f
    
    // Spacebar gesture detection
    private var isSpacebarPressed = false
    private var spacebarStartX = 0f
    private var spacebarStartY = 0f
    private var lastSpacebarX = 0f
    
    // Floating mode properties
    private var floatingX = 0f
    private var floatingY = 0f
    private var isDragging = false
    
    // Theme change listener removed - using static default colors
    
    // Enhanced special key state tracking
    private var isVoiceKeyActive = false
    private var isEmojiKeyActive = false
    private var specialKeyHighlightPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#1A73E8") // Default, will be updated by refreshTheme()
    }
    
    // Enhanced gesture recognition
    private var backspaceSlideStartX = 0f
    private var isSlideToDeleteActive = false
    private var spacebarSwipeStartX = 0f
    private var isCursorControlActive = false
    private var gestureStartTime = 0L
    private val SLIDE_THRESHOLD = 80f
    private val CURSOR_THRESHOLD = 30f
    
    // Special key detection and theming
    private fun isSpecialKey(code: Int): Boolean = when (code) {
        Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_DONE,
        -10 /* ?123 */, -11 /* ABC */, -12 /* ?123 */, -13 /* Mic */, 
        -14 /* Globe */, -15 /* Emoji */, -16 /* Voice */, 
        10 /* Enter */, -4 /* Enter variant */ -> true
        else -> false
    }
    
    // Clipboard layout state
    private var isClipboardMode = false
    private var clipboardItems = listOf<ClipboardItem>()
    private var clipboardKeyRects = mutableListOf<RectF>()
    private var clipboardService: AIKeyboardService? = null
    
    // Dynamic layout state
    private var isDynamicLayoutMode = false
    private var dynamicKeys = mutableListOf<DynamicKey>()
    private var currentLayoutModel: LanguageLayoutAdapter.LayoutModel? = null
    private var currentNumberRowEnabled = false
    
    // Dynamic layout long-press handling
    private var dynamicLongPressKey: DynamicKey? = null
    private var dynamicLongPressHandler: Handler = Handler(Looper.getMainLooper())
    private var dynamicLongPressRunnable: Runnable? = null
    private val DYNAMIC_LONG_PRESS_TIMEOUT = 500L
    private var dynamicAccentPopup: PopupWindow? = null
    
    // Track last applied shift visuals to prevent redundant redraws
    private var lastShiftUppercase = false
    private var lastShiftCapsLock = false
    
    // ✅ FIXED: Track current keyboard mode for proper key code mapping
    var currentKeyboardMode: LanguageLayoutAdapter.KeyboardMode = LanguageLayoutAdapter.KeyboardMode.LETTERS
    var currentLangCode: String = "en"
    
    /**
     * Dynamic key model for programmatic layouts
     */
    data class DynamicKey(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val label: String,
        val code: Int,
        val longPressOptions: List<String>? = null,
        val hintLabel: String? = null
    )
    
    init {
        // Initialize with ThemeManager V2 - no hardcoded colors
        initializeFromTheme()
        setupInsetHandling()
    }
    
    /**
     * Set the theme manager V2 for dynamic theming
     */
    fun setThemeManager(manager: ThemeManager) {
        themeManager = manager
        initializeFromTheme()
        manager.addThemeChangeListener(object : ThemeManager.ThemeChangeListener {
            override fun onThemeChanged(theme: com.kvive.keyboard.themes.KeyboardThemeV2, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
                refreshTheme()
            }
        })
    }
    
    /**
     * Refresh theme colors and invalidate view for live theme updates
     */
    fun refreshTheme() {
        initializeFromTheme()
        updateSwipePaint()
        
        // Explicitly update background color from current palette
        val manager = themeManager
        if (manager != null) {
            val palette = manager.getCurrentPalette()
            this.setBackgroundColor(palette.keyboardBg)
            android.util.Log.d("SwipeKeyboardView", "[AIKeyboard] Swipe background updated with current palette color: ${Integer.toHexString(palette.keyboardBg)}")
        }
        
        invalidateAllKeys()
        invalidate()
        requestLayout()
    }
    
    /**
     * Lightweight shift update for legacy controllers (avoids rebuilds)
     */
    fun applyShiftState(isUpperCase: Boolean, isCapsLock: Boolean): Boolean {
        val kb = keyboard ?: return false
        
        // Skip if nothing changed to avoid redundant invalidations
        if (isUpperCase == lastShiftUppercase && isCapsLock == lastShiftCapsLock) {
            return true
        }
        
        kb.setShifted(isUpperCase)
        
        // Update shift key visual toggles (covers layouts using -1 for shift)
        kb.keys.forEach { key ->
            if (key.codes.contains(Keyboard.KEYCODE_SHIFT) || key.codes.contains(-1)) {
                key.on = isUpperCase
                key.pressed = isCapsLock
            }
        }
        
        lastShiftUppercase = isUpperCase
        lastShiftCapsLock = isCapsLock
        
        invalidateAllKeys()
        return true
    }
    
    // ========================================
    // CleverType Configuration Methods
    // ========================================
    
    private var labelScaleMultiplier = 1.0f
    private var borderlessMode = false
    private var hintedNumberRow = false
    private var hintedSymbols = true
    private var showLanguageOnSpace = true
    private var currentLanguageLabel = "English"
    private var previewEnabled = true
    // Match Gboard-esque density with comfortable row separation
    private var keySpacingVerticalDp = 3   // Vertical gap between rows in dp
    private var keySpacingHorizontalDp = 3 // Horizontal gap between keys in dp
    private var edgePaddingDp = 8          // Left/right edge margin in dp
    private var soundEnabled = true
    private var soundIntensityLevel = 1
    private var hapticIntensityLevel = 2
    private var longPressDelayMs = 200
    
    // One-handed mode state
    private var oneHandedModeEnabled = false
    private var oneHandedModeSide = "right"
    private var oneHandedModeWidthPct = 0.75f
    
    /**
     * Set the font scale multiplier for key labels
     */
    fun setLabelScale(multiplier: Float) {
        labelScaleMultiplier = multiplier.coerceIn(0.8f, 1.3f)
        android.util.Log.d("SwipeKeyboardView", "Label scale set to: $labelScaleMultiplier")
        // Recreate paint with new scale from theme manager
        themeManager?.let { manager ->
            keyTextPaint = manager.createKeyTextPaint()
            spaceLabelPaint = manager.createSpaceLabelPaint()
        }
        invalidateAllKeys()
        invalidate()
    }

    fun updateTextPaints(fontFamily: String, fontScale: Float) {
        themeManager?.let { manager ->
            labelScaleMultiplier = fontScale.coerceIn(0.8f, 1.3f)
            keyTextPaint = manager.createKeyTextPaint()
            suggestionTextPaint = manager.createSuggestionTextPaint()
            spaceLabelPaint = manager.createSpaceLabelPaint()
            android.util.Log.d(
                "SwipeKeyboardView",
                "updateTextPaints → family=$fontFamily scale=$labelScaleMultiplier"
            )
            invalidateAllKeys()
            invalidate()
        }
    }
    
    /**
     * Enable or disable borderless key mode
     */
    fun setBorderless(enabled: Boolean) {
        borderlessMode = enabled
        android.util.Log.d("SwipeKeyboardView", "Borderless mode set to: $enabled")
        // Borderless mode now removes padding in drawThemedKey
        invalidate()
        requestLayout()
    }

    fun setHintedNumberRow(enabled: Boolean) {
        hintedNumberRow = enabled
        invalidateAllKeys()
        invalidate()
    }

    fun setHintedSymbols(enabled: Boolean) {
        hintedSymbols = enabled
        invalidateAllKeys()
        invalidate()
    }
    
    /**
     * Show or hide language label on spacebar
     */
    fun setShowLanguageOnSpace(enabled: Boolean) {
        showLanguageOnSpace = enabled
        android.util.Log.d("SwipeKeyboardView", "Show language on space set to: $enabled")
        invalidate()
    }
    
    /**
     * Set the current language label to display on spacebar
     */
    fun setCurrentLanguage(languageLabel: String) {
        currentLanguageLabel = languageLabel
        invalidate()
    }
    
    /**
     * Enable or disable one-handed mode with Gboard-style behavior
     * @param enabled Whether one-handed mode is active
     * @param side "left" or "right" - which side to dock the keyboard
     * @param widthPct Percentage of screen width to use (0.6 - 0.9)
     */
    fun setOneHandedMode(enabled: Boolean, side: String = "right", widthPct: Float = 0.75f) {
        val normalizedSide = if (side.equals("left", ignoreCase = true)) "left" else "right"
        val clampedPct = widthPct.coerceIn(0.6f, 0.9f)
        oneHandedModeEnabled = enabled
        oneHandedModeSide = normalizedSide
        oneHandedModeWidthPct = clampedPct

        android.util.Log.d(
            "SwipeKeyboardView",
            "One-handed mode set to: enabled=$enabled, side=$normalizedSide, width=${(clampedPct * 100).toInt()}%"
        )

        val params = (layoutParams as? android.view.ViewGroup.MarginLayoutParams)
            ?: android.view.ViewGroup.MarginLayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )

        if (!enabled) {
            params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            params.marginStart = 0
            params.marginEnd = 0
            layoutParams = params
            translationX = 0f
            translationY = 0f
            requestLayout()
            invalidate()
            android.util.Log.d("SwipeKeyboardView", "Reset to full-width mode")
            return
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val targetWidth = (screenWidth * clampedPct).toInt()
        val gutter = (screenWidth - targetWidth).coerceAtLeast(0)

        params.width = targetWidth
        params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        when (normalizedSide) {
            "left" -> {
                params.marginStart = 0
                params.marginEnd = gutter
            }
            else -> {
                params.marginStart = gutter
                params.marginEnd = 0
            }
        }

        layoutParams = params
        translationX = 0f
        translationY = 0f
        requestLayout()
        invalidate()

        android.util.Log.d(
            "SwipeKeyboardView",
            "Applied one-handed: width=${targetWidth}px, marginStart=${params.marginStart}, marginEnd=${params.marginEnd}"
        )
    }
    
    /**
     * Enable or disable key preview popups
     */
    override fun setPreviewEnabled(enabled: Boolean) {
        super.setPreviewEnabled(enabled)
        previewEnabled = enabled
    }
    
    /**
     * Set key spacing (vertical and horizontal)
     */
    fun setKeySpacing(verticalDp: Int, horizontalDp: Int) {
        val clampedVertical = max(0, verticalDp)
        val clampedHorizontal = max(0, horizontalDp)
        if (clampedVertical == keySpacingVerticalDp && clampedHorizontal == keySpacingHorizontalDp) return

        keySpacingVerticalDp = clampedVertical
        keySpacingHorizontalDp = clampedHorizontal
        android.util.Log.d("SwipeKeyboardView", "Key spacing set to: V=${clampedVertical}dp, H=${clampedHorizontal}dp")
        currentLayoutModel?.let { layout ->
            setDynamicLayout(layout, currentNumberRowEnabled)
        } ?: run {
            invalidate()
            requestLayout()
        }
    }

    private fun shouldShowHint(key: DynamicKey): Boolean {
        val hint = key.hintLabel?.trim() ?: return false
        if (hint.isEmpty()) return false
        if (isSpecialKey(key.code)) return false
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

    /**
     * Adjust left/right gutters to avoid edge-to-edge keys
     */
    fun setEdgePadding(dp: Int) {
        val clamped = max(0, dp)
        if (clamped == edgePaddingDp) return

        edgePaddingDp = clamped
        android.util.Log.d("SwipeKeyboardView", "Edge padding set to ${clamped}dp")
        currentLayoutModel?.let { layout ->
            setDynamicLayout(layout, currentNumberRowEnabled)
        } ?: run {
            invalidate()
            requestLayout()
        }
    }
    
    /**
     * Set long press delay in milliseconds
     */
    fun setLongPressDelay(delayMs: Int) {
        longPressDelayMs = delayMs
        // Long press delay will be used in touch event handling
    }
    
    /**
     * Enable/disable sound feedback with intensity level
     */
    fun setSoundEnabled(enabled: Boolean, intensityLevel: Int) {
        soundEnabled = enabled
        soundIntensityLevel = intensityLevel
        // Intensity: 0=off, 1=light, 2=medium, 3=strong
        // This would be used when playing click sounds
    }

    fun setTapEffectStyle(style: String, enabled: Boolean) {
        tapEffectStyle = style
        tapEffectsEnabled = enabled && !style.equals("none", ignoreCase = true)
    }

    fun applyTapEffect(type: String, opacity: Float) {
        previewTapEffectType = type
        previewTapEffectOpacity = opacity.coerceIn(0f, 1f)
        if (width == 0 || height == 0) {
            tapEffectX = -1f
            tapEffectY = -1f
        } else {
            if (tapEffectX < 0f || tapEffectY < 0f) {
                tapEffectX = width / 2f
                tapEffectY = height / 2f
            }
        }
        startTapEffectAnimation()
    }
    
    /**
     * Set haptic feedback intensity level
     */
    fun setHapticIntensity(intensityLevel: Int) {
        hapticIntensityLevel = intensityLevel
        // Intensity: 0=off, 1=light, 2=medium, 3=strong
        // This would be used when performing haptic feedback
        // Stored for use in onKey events
    }
    
    /**
     * Refresh suggestions strip (called after config changes)
     */
    fun refresh() {
        invalidate()
        requestLayout()
    }
    
    /**
     * Initialize all theme-dependent objects from ThemeManager V2
     */
    private fun initializeFromTheme() {
        val manager = themeManager
        if (manager == null) {
            // Fallback to default theme color (light gray)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            return
        }
        
        val palette = manager.getCurrentPalette()
        
        // Initialize cached paints from factory
        keyTextPaint = manager.createKeyTextPaint()
        suggestionTextPaint = manager.createSuggestionTextPaint() 
        spaceLabelPaint = manager.createSpaceLabelPaint()
        
        // Set background from theme - use solid color for consistency
        setBackgroundColor(palette.keyboardBg)
        
        // Update swipe trail paint
        updateSwipePaint()
    }
    
    /**
     * Update swipe trail paint with theme colors
     */
    private fun updateSwipePaint() {
        val manager = themeManager
        if (manager != null) {
            val palette = manager.getCurrentPalette()
            swipePaint.apply {
                color = palette.specialAccent
                strokeWidth = 8f * context.resources.displayMetrics.density
                alpha = (palette.rippleAlpha * 255 * 2).toInt() // Make swipe trail more visible
            }
        } else {
            // Fallback to default blue
            swipePaint.apply {
                color = Color.parseColor("#2196F3") // Default blue for swipe trail
                strokeWidth = 8f * context.resources.displayMetrics.density
                alpha = 180
            }
        }
    }
    
    
    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
    }
    
    fun setSwipeListener(listener: SwipeListener?) {
        swipeListener = listener
    }
    
    // Theme application methods removed - using single default theme only
    
    /**
     * Set voice key active state for visual feedback
     */
    fun setVoiceKeyActive(active: Boolean) {
        isVoiceKeyActive = active
        android.util.Log.d("SwipeKeyboardView", "Voice key active: $active")
        invalidate()
    }
    
    /**
     * Set emoji key active state for visual feedback
     */
    fun setEmojiKeyActive(active: Boolean) {
        isEmojiKeyActive = active
        android.util.Log.d("SwipeKeyboardView", "Emoji key active: $active")
        invalidate()
    }

    private fun startTapEffectAnimation() {
        tapEffectAnimator?.cancel()
        if (previewTapEffectOpacity <= 0f) {
            tapEffectProgress = 0f
            tapEffectRadius = 0f
            invalidate()
            return
        }

        if (tapEffectX < 0f || tapEffectY < 0f) {
            tapEffectX = width / 2f
            tapEffectY = height / 2f
        }

        tapEffectAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (tapEffectStyle.equals("glow", ignoreCase = true)) 360L else 220L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                tapEffectProgress = animator.animatedValue as Float
                val maxRadius = (width.coerceAtLeast(height) * 0.65f)
                tapEffectRadius = maxRadius * tapEffectProgress
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tapEffectProgress = 0f
                    tapEffectRadius = 0f
                    tapEffectX = -1f
                    tapEffectY = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun drawTapEffect(canvas: Canvas) {
        if (tapEffectProgress <= 0f || previewTapEffectOpacity <= 0f) {
            return
        }

        val palette = themeManager?.getCurrentPalette()
        val baseColor = when (previewTapEffectType.lowercase()) {
            "glow" -> palette?.specialAccent ?: Color.WHITE
            "sparkles", "sparkle" -> Color.WHITE
            "wave" -> palette?.keyText ?: Color.WHITE
            else -> palette?.specialAccent ?: Color.WHITE
        }

        val alpha = (previewTapEffectOpacity * (1f - tapEffectProgress) * 255).toInt().coerceIn(0, 255)
        if (alpha <= 0) return

        tapEffectPaint.color = baseColor
        tapEffectPaint.alpha = alpha

        val cx = if (tapEffectX >= 0f) tapEffectX else width / 2f
        val cy = if (tapEffectY >= 0f) tapEffectY else height / 2f

        canvas.drawCircle(cx, cy, tapEffectRadius.coerceAtLeast(0f), tapEffectPaint)
        postInvalidateOnAnimation()
    }
    
    
    
    override fun onDraw(canvas: Canvas) {
        if (isClipboardMode) {
            drawClipboardLayout(canvas)
        } else if (isDynamicLayoutMode) {
            drawDynamicLayout(canvas)
        } else {
            try {
                // Draw custom themed keys (legacy XML mode)
                keyboard?.let { kbd ->
                    val keys = kbd.keys
                    keys?.forEach { key ->
                        try {
                            drawThemedKey(canvas, key)
                        } catch (e: StringIndexOutOfBoundsException) {
                            // Skip keys that cause issues with empty labels
                        }
                    }
                }
                
                // Draw swipe trail if in progress
                if (isSwipeInProgress && swipePoints.isNotEmpty()) {
                    canvas.drawPath(swipePath, swipePaint)
                }
            } catch (e: Exception) {
                // General drawing exception handling
                // Continue with basic functionality
            }
        }
        drawTapEffect(canvas)
    }
    
    /**
     * Draw dynamic layout (modern JSON-based approach)
     */
    private fun drawDynamicLayout(canvas: Canvas) {
        val manager = themeManager
        if (manager == null) {
            canvas.drawColor(Color.parseColor("#F5F5F5"))
            return
        }
        
        val palette = manager.getCurrentPalette()
        canvas.drawColor(palette.keyboardBg)
        
        // Draw each dynamic key
        dynamicKeys.forEach { key ->
            drawDynamicKey(canvas, key, manager, palette)
        }
        
        // Draw swipe trail if in progress
        if (isSwipeInProgress && swipePoints.isNotEmpty()) {
            canvas.drawPath(swipePath, swipePaint)
        }
    }
    
    /**
     * Draw a single dynamic key
     */
    private fun drawDynamicKey(
        canvas: Canvas,
        key: DynamicKey,
        manager: ThemeManager,
        palette: com.kvive.keyboard.themes.ThemePaletteV2
    ) {
        val basePadding = if (borderlessMode) 0f else dpToPx(0.5f)
        val horizontalInset = if (borderlessMode) 0f else dpToPx(0.5f)
        val verticalInset = if (borderlessMode) 0f else dpToPx(0.5f)
        
        val keyRect = RectF(
            key.x.toFloat() + basePadding + horizontalInset,
            key.y.toFloat() + basePadding + verticalInset,
            (key.x + key.width).toFloat() - basePadding - horizontalInset,
            (key.y + key.height).toFloat() - basePadding - verticalInset
        )
        
        // Determine key type
        val keyType = when (key.code) {
            32 -> "space"
            Keyboard.KEYCODE_SHIFT, -1 -> "shift"
            Keyboard.KEYCODE_DELETE, -5 -> "backspace"
            Keyboard.KEYCODE_DONE, 10, -4 -> "enter"
            -13, -16 -> "mic"
            -15 -> "emoji"
            -14 -> "globe"
            -10, -11, -12 -> "symbols"
            else -> "regular"
        }
        
        // Get appropriate drawable
        val useNeutralBackground = keyType == "enter" || keyType == "shift"
        val shouldDrawBackground = !borderlessMode
        val keyDrawable = if (shouldDrawBackground) {
            when {
                keyType in listOf("voice", "emoji") && isKeyActive(keyType) -> manager.createSpecialKeyDrawable()
                !useNeutralBackground && manager.shouldUseAccentForKey(keyType) -> manager.createSpecialKeyDrawable()
                else -> manager.createKeyDrawable()
            }
        } else null
        
        keyDrawable?.let { drawable ->
            drawable.setBounds(keyRect.left.toInt(), keyRect.top.toInt(), keyRect.right.toInt(), keyRect.bottom.toInt())
            drawable.draw(canvas)
        }
        
        // Check if this key should use an icon
        val iconResId = getIconForKeyType(keyType, key.label)
        
        if (iconResId != null) {
            // Draw icon for special keys
            val iconDrawable = try {
                ContextCompat.getDrawable(context, iconResId)?.mutate()
            } catch (e: Exception) {
                null
            }
            
            if (iconDrawable != null) {
                val desiredSizePx = dpToPx(if (isSpecialKey(key.code)) 32 else 28).toFloat()
                val maxDrawableExtent = (min(key.width, key.height) - dpToPx(6)).coerceAtLeast(dpToPx(20))
                val iconSize = min(desiredSizePx, maxDrawableExtent.toFloat())
                val centerX = keyRect.centerX()
                val centerY = keyRect.centerY()
                
                // Apply tint based on key type and state
                val tintColor = when {
                    keyType == "space" && showLanguageOnSpace -> palette.spaceLabelColor
                    keyType == "enter" || keyType == "shift" -> palette.specialAccent
                    manager.shouldUseAccentForKey(keyType) ||
                    isKeyActive(keyType) -> if (borderlessMode) palette.specialAccent else Color.WHITE
                    else -> palette.keyText
                }
                
                iconDrawable.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                iconDrawable.setBounds(
                    (centerX - iconSize/2).toInt(),
                    (centerY - iconSize/2).toInt(),
                    (centerX + iconSize/2).toInt(),
                    (centerY + iconSize/2).toInt()
                )
                iconDrawable.draw(canvas)
                
                // Draw language label on space key if enabled
                if (keyType == "space" && showLanguageOnSpace && currentLanguageLabel.isNotEmpty()) {
                    val textPaint = spaceLabelPaint ?: manager.createSpaceLabelPaint()
                    val basePaint = Paint(textPaint)
                    // Use theme font instead of hardcoding
                    basePaint.textSize = textPaint.textSize * labelScaleMultiplier * 0.7f
                    basePaint.color = palette.spaceLabelColor
                    basePaint.textAlign = Paint.Align.CENTER
                    val textY = centerY + iconSize/2 + basePaint.textSize + dpToPx(1f)
                    canvas.drawText(currentLanguageLabel, centerX, textY, basePaint)
                }
            }
        } else {
            // Draw text label for regular keys
            val textPaint = when (keyType) {
                "space" -> spaceLabelPaint ?: manager.createSpaceLabelPaint()
                else -> keyTextPaint ?: manager.createKeyTextPaint()
            }
            
            val basePaint = Paint(textPaint)
            // ✅ Use theme font (already set in textPaint) instead of hardcoding
            // Apply label scale to respect user's font size preference
            basePaint.textSize = textPaint.textSize * labelScaleMultiplier
            
            basePaint.color = when {
                keyType == "space" && showLanguageOnSpace -> palette.spaceLabelColor
                keyType == "enter" || keyType == "shift" -> palette.specialAccent
                manager.shouldUseAccentForKey(keyType) ||
                isKeyActive(keyType) -> if (borderlessMode) palette.specialAccent else Color.WHITE
                else -> palette.keyText
            }
            
            val text = if (keyType == "space" && showLanguageOnSpace) {
                currentLanguageLabel
            } else {
                key.label
            }
            
            // Center the text
            val centerX = keyRect.centerX()
            val centerY = keyRect.centerY()
            val textHeight = basePaint.descent() - basePaint.ascent()
            val textOffset = (textHeight / 2) - basePaint.descent()
            basePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, centerX, centerY + textOffset + dpToPx(1f), basePaint)
            drawHintLabel(canvas, key, keyRect, basePaint)
        }
    }
    
    private fun drawThemedKey(canvas: Canvas, key: Keyboard.Key) {
        val manager = themeManager ?: return // Theme manager required for dynamic layout
        
        val palette = manager.getCurrentPalette()
        
        // Key rectangle with padding - apply custom spacing if set
        val basePadding = if (borderlessMode) 0f else dpToPx(0.5f)
        val horizontalInset = if (borderlessMode) 0f else dpToPx(0.5f)
        val verticalInset = if (borderlessMode) 0f else dpToPx(0.5f)
        
        val keyRect = RectF(
            key.x.toFloat() + basePadding + horizontalInset,
            key.y.toFloat() + basePadding + verticalInset,
            (key.x + key.width).toFloat() - basePadding - horizontalInset,
            (key.y + key.height).toFloat() - basePadding - verticalInset
        )
        
        // Identify key type using centralized logic
        val keyCode = key.codes[0]
        val keyType = getKeyType(keyCode)
        
        // Get appropriate drawable from factory
        val useNeutralBackground = keyType == "enter" || keyType == "shift"
        val shouldDrawBackground = !borderlessMode
        val keyDrawable = if (shouldDrawBackground) {
            when {
                keyType in listOf("voice", "emoji") && isKeyActive(keyType) -> manager.createSpecialKeyDrawable()
                !useNeutralBackground && manager.shouldUseAccentForKey(keyType) -> manager.createSpecialKeyDrawable()
                else -> manager.createKeyDrawable()
            }
        } else null

        keyDrawable?.let { drawable ->
            drawable.setBounds(keyRect.left.toInt(), keyRect.top.toInt(), keyRect.right.toInt(), keyRect.bottom.toInt())
            drawable.draw(canvas)
        }
        
        // Draw key content (icon or text)
        val centerX = keyRect.centerX()
        val centerY = keyRect.centerY()
        
        if (key.icon != null) {
            // Draw icon with proper tinting
            drawKeyIcon(canvas, key, centerX, centerY, keyType)
        } else if (key.label != null) {
            // Draw text using themed paint
            drawKeyText(canvas, key, centerX, centerY, keyType)
        }
    }
    
    /**
     * Get key type for theme application
     */
    private fun getKeyType(keyCode: Int): String = when (keyCode) {
        Keyboard.KEYCODE_SHIFT, -1 -> "shift"
        Keyboard.KEYCODE_DELETE, -5 -> "backspace"
        Keyboard.KEYCODE_DONE, 10, -4 -> "enter"
        32 -> "space"
        -13, -16 -> "mic"
        -15 -> "emoji"
        -14 -> "globe"
        -10, -11, -12 -> "symbols"
        else -> "regular"
    }
    
    /**
     * Check if a special key is currently active
     */
    private fun isKeyActive(keyType: String): Boolean = when (keyType) {
        "voice", "mic" -> isVoiceKeyActive
        "emoji" -> isEmojiKeyActive
        else -> false
    }
    
    /**
     * Draw key icon with proper theming
     */
    private fun drawKeyIcon(canvas: Canvas, key: Keyboard.Key, centerX: Float, centerY: Float, keyType: String) {
        val manager = themeManager ?: return
        val palette = manager.getCurrentPalette()
        
        val iconDrawable = key.icon.mutate()
        val iconSize = minOf(key.width, key.height) * 0.28f
        
        // Apply tint based on key type and state
        val tintColor = when {
            keyType == "space" -> palette.spaceLabelColor
            keyType == "enter" || keyType == "shift" -> palette.specialAccent
            manager.shouldUseAccentForKey(keyType) || isKeyActive(keyType) -> Color.WHITE
            else -> palette.keyText
        }
        
        iconDrawable.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        iconDrawable.setBounds(
            (centerX - iconSize/2).toInt(),
            (centerY - iconSize/2).toInt(),
            (centerX + iconSize/2).toInt(),
            (centerY + iconSize/2).toInt()
        )
        iconDrawable.draw(canvas)
    }
        
    /**
     * Draw key text with proper theming
     */
    private fun drawKeyText(canvas: Canvas, key: Keyboard.Key, centerX: Float, centerY: Float, keyType: String) {
        val manager = themeManager ?: return
        val palette = manager.getCurrentPalette()
        
        val textPaint = when (keyType) {
            "space" -> spaceLabelPaint ?: manager.createSpaceLabelPaint()
            else -> keyTextPaint ?: manager.createKeyTextPaint()
        }
        
        val basePaint = Paint(textPaint)
        // ✅ Use theme font (already set in textPaint) instead of hardcoding
        // Apply label scale to respect user's font size preference
        basePaint.textSize = textPaint.textSize * labelScaleMultiplier
        
        basePaint.color = when {
            keyType == "space" && showLanguageOnSpace -> palette.spaceLabelColor
            keyType == "enter" || keyType == "shift" -> palette.specialAccent
            manager.shouldUseAccentForKey(keyType) || isKeyActive(keyType) -> Color.WHITE
            else -> palette.keyText
        }
        
        val text = if (keyType == "space") {
            // Show language label on spacebar if enabled
            if (showLanguageOnSpace) {
                currentLanguageLabel
            } else {
                "" // Don't show any label
            }
        } else {
            key.label?.toString() ?: ""
        }
        
        // Center the text
        val textHeight = basePaint.descent() - basePaint.ascent()
        val textOffset = (textHeight / 2) - basePaint.descent()
        basePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, centerX, centerY + textOffset + dpToPx(1f), basePaint)
        
        // Draw popup hint for number keys
        if (key.popupCharacters != null && key.popupCharacters.isNotEmpty()) {
            val hintPaint = Paint(basePaint).apply {
                textSize = basePaint.textSize * 0.5f
                alpha = (255 * 0.7f).toInt()
                textAlign = Paint.Align.LEFT
            }
            val hintX = centerX - (key.width * 0.3f)
            val hintY = centerY - (key.height * 0.2f)
            canvas.drawText(key.popupCharacters[0].toString(), hintX, hintY, hintPaint)
        }
    }
    
    override fun onTouchEvent(me: MotionEvent): Boolean {
        // Handle clipboard mode first
        if (isClipboardMode) {
            when (me.action) {
                MotionEvent.ACTION_UP -> {
                    handleClipboardTouch(me.x, me.y)
                    return true
                }
            }
            return true
        }
        
        // ✅ CRITICAL FIX: Handle dynamic layout mode touches
        if (isDynamicLayoutMode) {
            return handleDynamicLayoutTouch(me)
        }

        if (me.action == MotionEvent.ACTION_DOWN) {
            tapEffectX = me.x
            tapEffectY = me.y
        }
        
        return try {
            // Initialize adaptive sizing on first touch
            if (isAdaptiveSizingEnabled && adaptiveKeyWidth == 0f) {
                initializeAdaptiveSizing()
            }
            
            // Handle spacebar gestures first
            val spacebarHandled = handleSpacebarGesture(me)
            if (spacebarHandled) return true
            
            // Handle enhanced gestures first
            val gestureHandled = handleEnhancedGestures(me)
            if (gestureHandled) return true
            
            // Handle shift key long press detection
            val shiftKeyHandled = handleShiftKeyTouch(me)
            if (shiftKeyHandled) return true
            
            if (!swipeEnabled) {
                return super.onTouchEvent(me)
            }
            
            val handled = handleSwipeTouch(me)
            if (handled) true else super.onTouchEvent(me)
        } catch (e: StringIndexOutOfBoundsException) {
            // Handle the case where KeyboardView tries to access empty key labels
            // This prevents crashes when caps lock is pressed
            false
        } catch (e: Exception) {
            // General exception handling for touch events
            false
        }
    }
    
    /**
     * ✅ CRITICAL FIX: Handle touch events for dynamic layout mode
     * This ensures the correct key codes are sent for the current mode
     */
    private fun handleDynamicLayoutTouch(event: MotionEvent): Boolean {
        // 🔧 FIX: Handle swipe gestures first if swipe typing is enabled
        if (swipeEnabled) {
            val swipeHandled = handleSwipeTouch(event)
            if (swipeHandled) return true
        }
        
        // Handle taps for dynamic keys
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                tapEffectX = event.x
                tapEffectY = event.y
                val x = event.x.toInt()
                val y = event.y.toInt()
                
                // Find which dynamic key was tapped
                val tappedKey = dynamicKeys.firstOrNull { key ->
                    x >= key.x && x < (key.x + key.width) &&
                    y >= key.y && y < (key.y + key.height)
                }
                
                if (tappedKey != null) {
                    // Check if this key has long-press variants
                    if (!tappedKey.longPressOptions.isNullOrEmpty()) {
                        dynamicLongPressKey = tappedKey
                        dynamicLongPressRunnable = Runnable {
                            // ✅ FIX: Add sound and vibration to long-press before showing popup
                            val service = (context as? AIKeyboardService)
                            service?.let {
                                // Trigger sound
                                KeyboardSoundManager.play()
                                // Trigger vibration with long-press flag
                                it.triggerLongPressVibration()
                            }
                            showDynamicAccentOptions(tappedKey)
                        }
                        dynamicLongPressHandler.postDelayed(dynamicLongPressRunnable!!, DYNAMIC_LONG_PRESS_TIMEOUT)
                    }
                    
                    // Provide haptic and visual feedback
                    (context as? AIKeyboardService)?.onPress(tappedKey.code)
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                val x = event.x.toInt()
                val y = event.y.toInt()
                
                // Find which dynamic key was tapped
                val tappedKey = dynamicKeys.firstOrNull { key ->
                    x >= key.x && x < (key.x + key.width) &&
                    y >= key.y && y < (key.y + key.height)
                }
                
                // Clean up long-press detection
                dynamicLongPressRunnable?.let { runnable ->
                    dynamicLongPressHandler.removeCallbacks(runnable)
                    dynamicLongPressRunnable = null
                }
                
                if (tappedKey != null) {
                    // Only send key if we're not showing long-press options
                    if (dynamicLongPressKey == null || !isDynamicAccentPopupShowing()) {
                        // Send the key code from the dynamic key
                        (context as? AIKeyboardService)?.onKey(tappedKey.code, intArrayOf(tappedKey.code))
                        android.util.Log.d("SwipeKeyboardView", 
                            "✅ Dynamic key tapped: ${tappedKey.label} | Code: ${tappedKey.code} | Mode: $currentKeyboardMode")
                    }
                    
                    // Provide release feedback
                    (context as? AIKeyboardService)?.onRelease(tappedKey.code)
                }
                
                dynamicLongPressKey = null
                return true
            }
            
            MotionEvent.ACTION_CANCEL -> {
                // Clean up on cancel
                dynamicLongPressRunnable?.let { runnable ->
                    dynamicLongPressHandler.removeCallbacks(runnable)
                    dynamicLongPressRunnable = null
                }
                dynamicLongPressKey = null
                return true
            }
            
            else -> return false
        }
    }
    
    /**
     * Handle enhanced gestures for backspace slide-to-delete and spacebar cursor control
     */
    private fun handleEnhancedGestures(event: MotionEvent): Boolean {
        val keys = keyboard?.keys ?: return false
        val key = getKeyAtPosition(event.x, event.y, keys)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartTime = System.currentTimeMillis()
                when (key?.codes?.firstOrNull()) {
                    Keyboard.KEYCODE_DELETE -> {
                        backspaceSlideStartX = event.x
                        isSlideToDeleteActive = false
                    }
                    32 -> { // Space key
                        spacebarSwipeStartX = event.x
                        isCursorControlActive = false
                    }
                }
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                when (key?.codes?.firstOrNull()) {
                    Keyboard.KEYCODE_DELETE -> {
                        val deltaX = kotlin.math.abs(event.x - backspaceSlideStartX)
                        if (deltaX > SLIDE_THRESHOLD && !isSlideToDeleteActive && 
                            System.currentTimeMillis() - gestureStartTime > 200) {
                            isSlideToDeleteActive = true
                            (context as? AIKeyboardService)?.activateSlideToDelete()
                            return true
                        }
                    }
                    32 -> { // Space key
                        val deltaX = kotlin.math.abs(event.x - spacebarSwipeStartX)
                        if (deltaX > CURSOR_THRESHOLD && !isCursorControlActive) {
                            isCursorControlActive = true
                            return true
                        } else if (isCursorControlActive) {
                            handleSpacebarCursorControl(event.x - spacebarSwipeStartX)
                            return true
                        }
                    }
                }
                return false
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSlideToDeleteActive) {
                    isSlideToDeleteActive = false
                    (context as? AIKeyboardService)?.deactivateSlideToDelete()
                    return true
                }
                if (isCursorControlActive) {
                    isCursorControlActive = false
                    return true
                }
                return false
            }
        }
        
        return false
    }
    

    /**
     * Handle shift key touch events for long press detection
     */
    private fun handleShiftKeyTouch(event: MotionEvent): Boolean {
        val keys = keyboard?.keys ?: return false
        val key = getKeyAtPosition(event.x, event.y, keys)
        
        // Check if this is the shift key
        if (key?.codes?.firstOrNull() == Keyboard.KEYCODE_SHIFT) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Notify AIKeyboardService to start long press detection
                    (context as? AIKeyboardService)?.let { service ->
                        service.startShiftKeyLongPressDetection()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press detection
                    (context as? AIKeyboardService)?.let { service ->
                        service.cancelShiftKeyLongPressDetection()
                    }
                }
            }
        }
        
        return false // Don't consume the event, let normal processing continue
    }
    
    /**
     * Get the key at the specified position
     */
    private fun getKeyAtPosition(x: Float, y: Float, keys: List<Keyboard.Key>): Keyboard.Key? {
        for (key in keys) {
            if (key.isInside(x.toInt(), y.toInt())) {
                return key
            }
        }
        return null
    }
    
    private fun handleSwipeTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isSwipeBlockedArea(x, y)) {
                    resetSwipe()
                    return false
                }
                startSwipe(x, y)
                false // Let normal key press handling occur
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isSwipeInProgress) {
                    val now = System.currentTimeMillis()
                    
                    // 🔥 FIX: Throttle point collection to prevent lag
                    if (now - lastSwipeProcessTime > SWIPE_PROCESS_THROTTLE) {
                        continueSwipe(x, y)
                        lastSwipeProcessTime = now
                    }
                    
                    // ✅ FIX: Send Real-time Preview INDEPENDENTLY of point collection
                    // This ensures smooth suggestion updates while swiping
                    if (now - lastPreviewTime > PREVIEW_UPDATE_DELAY && swipePoints.size >= 3) {
                        val normalizedPath = buildNormalizedPath(swipePoints)
                        // Notify service this is just a PREVIEW (finger still down)
                        swipeListener?.onSwipeDetected(
                            emptyList(),
                            "preview",
                            emptyList(),
                            normalizedPath,
                            isPreview = true
                        )
                        lastPreviewTime = now
                    }
                    
                    true // Consume the event
                } else {
                    // Check if user has moved enough to start swipe
                    if (swipePoints.isNotEmpty()) {
                        val startPoint = swipePoints[0]
                        val distance = sqrt(
                            (x - startPoint[0]).pow(2) + (y - startPoint[1]).pow(2)
                        )
                        
                        if (distance > SWIPE_START_THRESHOLD) {
                            isSwipeInProgress = true
                            lastSwipeProcessTime = System.currentTimeMillis()
                            lastPreviewTime = System.currentTimeMillis()
                            swipeListener?.onSwipeStarted()
                            continueSwipe(x, y)
                            return true
                        }
                    }
                    false
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (isSwipeInProgress) {
                    endSwipe(x, y)
                    true
                } else {
                    // Reset swipe data for normal key press
                    resetSwipe()
                    false
                }
            }
            
            MotionEvent.ACTION_CANCEL -> {
                resetSwipe()
                isSwipeInProgress
            }
            
            else -> false
        }
    }
    
    private fun isSwipeBlockedArea(x: Float, y: Float): Boolean {
        if (isDynamicLayoutMode && dynamicKeys.isNotEmpty()) {
            val key = findDynamicKeyAtPoint(x, y) ?: return true
            return !isLetterKeyCode(key.code)
        }
        val keys = keyboard?.keys ?: return true
        val key = getKeyAtPosition(x, y, keys) ?: return true
        val code = key.codes.firstOrNull() ?: return true
        return !isLetterKeyCode(code)
    }
    
    private fun isLetterKeyCode(code: Int): Boolean {
        if (code < 0) return false
        return try {
            val ch = code.toChar()
            ch.isLetter()
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    private fun startSwipe(x: Float, y: Float) {
        swipePoints.clear()
        swipePoints.add(floatArrayOf(x, y))
        swipeStartTime = System.currentTimeMillis()
        swipePath.reset()
        swipePath.moveTo(x, y)
        isSwipeInProgress = false // Will be set to true when movement is detected
    }
    
    private fun continueSwipe(x: Float, y: Float) {
        swipePoints.add(floatArrayOf(x, y))
        swipePath.lineTo(x, y)
        invalidate() // Redraw to show swipe path
    }
    
    private fun endSwipe(x: Float, y: Float) {
        if (!isSwipeInProgress) return
        
        swipePoints.add(floatArrayOf(x, y))
        val swipeDuration = System.currentTimeMillis() - swipeStartTime
        
        // Calculate total swipe distance
        val totalDistance = calculateTotalDistance()
        
        // Only process as swipe if it meets minimum criteria
        if (swipeDuration >= MIN_SWIPE_TIME && totalDistance >= MIN_SWIPE_DISTANCE) {
            processSwipe()
        }
        
        resetSwipe()
        swipeListener?.onSwipeEnded()
    }
    
    private fun calculateTotalDistance(): Float {
        if (swipePoints.size < 2) return 0f
        
        var totalDistance = 0f
        for (i in 1 until swipePoints.size) {
            val prev = swipePoints[i - 1]
            val curr = swipePoints[i]
            totalDistance += sqrt(
                (curr[0] - prev[0]).pow(2) + (curr[1] - prev[1]).pow(2)
            )
        }
        return totalDistance
    }
    
    private fun processSwipe() {
        if (swipeListener == null || swipePoints.isEmpty()) return
        
        // Enhanced swipe processing for better gesture recognition
        val swipedKeys = mutableListOf<Int>()
        val swipePattern = generateSwipePattern()
        
        // Use path sampling for better accuracy - sample every 10-15 pixels
        val sampledPoints = sampleSwipePath(swipePoints)
        
        // Convert sampled points to key sequence
        val keySequence = mutableListOf<Int>()
        var lastKeyCode = -1
        
        sampledPoints.forEach { point ->
            resolveKeyCode(point[0], point[1])?.let { keyCode ->
                if (Character.isLetter(keyCode)) {
                    if (keyCode != lastKeyCode) {
                        keySequence.add(keyCode)
                        lastKeyCode = keyCode
                    }
                }
            }
        }
        
        // Also provide unique keys for backward compatibility
        swipedKeys.addAll(keySequence.distinct())
        
        // Convert swipePoints to normalized coordinates before passing to listener
        val normalizedPath = buildNormalizedPath(swipePoints)
        
        // ✅ FINAL swipe event (finger lifted) - isPreview = false
        swipeListener?.onSwipeDetected(swipedKeys, swipePattern, keySequence, normalizedPath, isPreview = false)
    }
    
    /**
     * Sample swipe path for better gesture recognition
     * Reduces noise and provides more accurate key sequence
     */
    private fun sampleSwipePath(points: List<FloatArray>): List<FloatArray> {
        if (points.size <= 2) return points
        
        val sampledPoints = mutableListOf<FloatArray>()
        sampledPoints.add(points.first()) // Always include start point
        
        var totalDistance = 0f
        val samplingDistance = getSamplingDistance()
        
        for (i in 1 until points.size) {
            val prevPoint = points[i - 1]
            val currPoint = points[i]
            
            val segmentDistance = sqrt(
                (currPoint[0] - prevPoint[0]).pow(2) + (currPoint[1] - prevPoint[1]).pow(2)
            )
            
            totalDistance += segmentDistance
            
            // Sample point if we've traveled enough distance
            if (totalDistance >= samplingDistance) {
                sampledPoints.add(currPoint)
                totalDistance = 0f
            }
        }
        
        // Always include end point
        if (sampledPoints.last() != points.last()) {
            sampledPoints.add(points.last())
        }
        
        return sampledPoints
    }
    
    private fun generateSwipePattern(): String {
        if (swipePoints.size < 2) return ""
        
        val start = swipePoints[0]
        val end = swipePoints[swipePoints.size - 1]
        
        // Simple pattern based on start and end positions
        val deltaX = end[0] - start[0]
        val deltaY = end[1] - start[1]
        
        // Determine general direction
        return if (abs(deltaX) > abs(deltaY)) {
            if (deltaX > 0) "right" else "left"
        } else {
            if (deltaY > 0) "down" else "up"
        }
    }
    
    private fun resetSwipe() {
        isSwipeInProgress = false
        swipePoints.clear()
        swipePath.reset()
        invalidate() // Clear the drawn path
    }
    
    private fun resolveKeyCode(x: Float, y: Float): Int? {
        return if (isDynamicLayoutMode && dynamicKeys.isNotEmpty()) {
            findDynamicKeyAtPoint(x, y)?.code
        } else {
            keyboard?.keys?.let { keys ->
                getKeyAtPosition(x, y, keys)?.codes?.firstOrNull()
            }
        }
    }
    
    private fun findDynamicKeyAtPoint(x: Float, y: Float): DynamicKey? {
        val direct = dynamicKeys.firstOrNull { key ->
            x >= key.x && x < (key.x + key.width) &&
            y >= key.y && y < (key.y + key.height)
        }
        if (direct != null) return direct
        return nearestDynamicKey(x, y)
    }
    
    private fun nearestDynamicKey(x: Float, y: Float): DynamicKey? {
        if (dynamicKeys.isEmpty()) return null
        var closest: DynamicKey? = null
        var minDistance = Float.MAX_VALUE
        dynamicKeys.forEach { key ->
            val centerX = key.x + key.width / 2f
            val centerY = key.y + key.height / 2f
            val distance = sqrt((centerX - x).pow(2) + (centerY - y).pow(2))
            if (distance < minDistance) {
                minDistance = distance
                closest = key
            }
        }
        return closest
    }
    
    private fun buildNormalizedPath(points: List<FloatArray>): List<Pair<Float, Float>> {
        if (points.isEmpty() || width == 0 || height == 0) return emptyList()
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        
        return points.map { point ->
            val (centerX, centerY) = when {
                isDynamicLayoutMode && dynamicKeys.isNotEmpty() -> {
                    val key = findDynamicKeyAtPoint(point[0], point[1])
                    if (key != null) {
                        Pair(key.x + key.width / 2f, key.y + key.height / 2f)
                    } else {
                        Pair(point[0], point[1])
                    }
                }
                keyboard?.keys?.isNotEmpty() == true -> {
                    val key = getKeyAtPosition(point[0], point[1], keyboard!!.keys)
                    if (key != null) {
                        Pair(key.x + key.width / 2f, key.y + key.height / 2f)
                    } else {
                        Pair(point[0], point[1])
                    }
                }
                else -> Pair(point[0], point[1])
            }
            
            val normalizedX = (centerX / widthF).coerceIn(0f, 1f)
            val normalizedY = (centerY / heightF).coerceIn(0f, 1f)
            Pair(normalizedX, normalizedY)
        }
    }
    
    private fun getSamplingDistance(): Float {
        if (isDynamicLayoutMode && dynamicKeys.isNotEmpty()) {
            val avgWidth = dynamicKeys.map { it.width }.filter { it > 0 }.average().toFloat()
            if (avgWidth > 0f) {
                return (avgWidth * 0.35f).coerceIn(8f, 40f)
            }
        }
        val legacyKeys = keyboard?.keys
        if (!legacyKeys.isNullOrEmpty()) {
            val avgWidth = legacyKeys.map { it.width }.filter { it > 0 }.average().toFloat()
            if (avgWidth > 0f) {
                return (avgWidth * 0.35f).coerceIn(8f, 40f)
            }
        }
        return 15f
    }
    
    private fun publishSwipeGeometry() {
        val service = context as? AIKeyboardService ?: return
        if (width == 0 || height == 0) return
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val positions = mutableMapOf<Char, Pair<Float, Float>>()
        
        if (isDynamicLayoutMode && dynamicKeys.isNotEmpty()) {
            dynamicKeys.forEach { key ->
                codePointToChar(key.code)?.let { ch ->
                    val centerX = (key.x + key.width / 2f) / widthF
                    val centerY = (key.y + key.height / 2f) / heightF
                    positions[ch.lowercaseChar()] = Pair(centerX, centerY)
                }
            }
        } else {
            keyboard?.keys?.forEach { key ->
                val code = key.codes.firstOrNull() ?: return@forEach
                codePointToChar(code)?.let { ch ->
                    val centerX = (key.x + key.width / 2f) / widthF
                    val centerY = (key.y + key.height / 2f) / heightF
                    positions[ch.lowercaseChar()] = Pair(centerX, centerY)
                }
            }
        }
        
        if (positions.isNotEmpty()) {
            service.updateSwipeGeometry(currentLangCode, positions)
        }
    }
    
    private fun codePointToChar(codePoint: Int): Char? {
        if (codePoint < 0) return null
        val chars = Character.toChars(codePoint)
        if (chars.size != 1) return null
        val ch = chars[0]
        return if (Character.isLetterOrDigit(ch)) ch else null
    }
    
    
    /**
     * Setup window insets handling for navigation bar and system UI
     * ✅ Auto-adjusts for gesture navigation and traditional nav bar
     */
    private fun setupInsetHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            // Don't apply any padding - this causes white space at bottom
            // The keyboard service handles positioning
            
            android.util.Log.d(
                "SwipeKeyboardView",
                "[InsetsFix] Window insets received but not applying padding to prevent white space"
            )

            insets
        }
    }
    
    /**
     * Cleanup when view is detached
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Theme listener removed - using static default colors
    }
    
    private fun findKeyAtPoint(x: Int, y: Int): Int {
        keyboard?.let { kbd ->
            try {
                kbd.keys.forEachIndexed { index, key ->
                    if (x >= key.x && x < key.x + key.width && 
                        y >= key.y && y < key.y + key.height) {
                        return index
                    }
                }
            } catch (e: Exception) {
                // Ignore errors in key detection
            }
        }
        return -1
    }
    
    /**
     * Set the keyboard service reference for clipboard functionality
     */
    fun setKeyboardService(service: AIKeyboardService) {
        clipboardService = service
    }
    
    /**
     * Show clipboard layout
     */
    fun showClipboardLayout(items: List<ClipboardItem>) {
        isClipboardMode = true
        clipboardItems = items
        calculateClipboardKeyLayout()
        invalidate()
    }
    
    /**
     * Show normal layout
     */
    fun showNormalLayout() {
        isClipboardMode = false
        clipboardItems = emptyList()
        clipboardKeyRects.clear()
        invalidate()
    }
    
    /**
     * Refresh clipboard UI
     */
    fun refreshClipboardUI() {
        if (isClipboardMode) {
            calculateClipboardKeyLayout()
            invalidate()
        }
    }
    
    /**
     * Set dynamic layout from LanguageLayoutAdapter with number row support
     * This replaces the traditional Keyboard XML approach
     * @param layout The layout model from LanguageLayoutAdapter
     * @param showNumberRow Whether to display the number row at the top
     */
    fun setDynamicLayout(layout: LanguageLayoutAdapter.LayoutModel, showNumberRow: Boolean = false) {
        // Guard against premature layout before measurement
        if (width == 0 || height == 0) {
            post { setDynamicLayout(layout, showNumberRow) }
            return
        }
        
        isDynamicLayoutMode = true
        dynamicKeys.clear()
        currentLayoutModel = layout
        currentNumberRowEnabled = showNumberRow
        
        // Apply RTL layout direction if needed
        val isRTL = layout.direction.equals("RTL", ignoreCase = true)
        layoutDirection = if (isRTL) {
            android.util.Log.d("SwipeKeyboardView", "✅ Applying RTL layout direction for ${layout.languageCode}")
            android.view.View.LAYOUT_DIRECTION_RTL
        } else {
            android.view.View.LAYOUT_DIRECTION_LTR
        }
        
        val screenWidth = width
        val screenHeight = height
        if (screenWidth <= 0 || screenHeight <= 0) {
            android.util.Log.w("SwipeKeyboardView", "⚠️ Cannot build layout with zero dimensions")
            return
        }
        
        val horizontalSpacingPx = dpToPx(keySpacingHorizontalDp)
        val verticalSpacingPx = dpToPx(keySpacingVerticalDp)
        val edgePaddingPx = dpToPx(edgePaddingDp)
        val allRows = layout.rows
        val numRows = allRows.size
        if (numRows == 0) {
            return
        }
        
        val totalSpacingY = max(0, numRows - 1) * verticalSpacingPx
        val usableHeight = (screenHeight.toFloat() - totalSpacingY).coerceAtLeast(0f)
        
        val explicitHeights = if (layout.rowHeightsDp.isNotEmpty()) {
            val heights = layout.rowHeightsDp.map { dpToPx(it).roundToInt() }.toMutableList()
            while (heights.size < numRows) {
                heights.add(heights.lastOrNull() ?: dpToPx(60).roundToInt())
            }
            if (heights.size > numRows) {
                heights.subList(numRows, heights.size).clear()
            }
            val usableHeightInt = usableHeight.roundToInt()
            val heightSum = heights.sum()
            val diff = usableHeightInt - heightSum
            if (diff != 0 && heights.isNotEmpty()) {
                heights[heights.lastIndex] = (heights.last() + diff).coerceAtLeast(1)
            }
            heights
        } else null
        
        val rowHeights: List<Int> = explicitHeights ?: run {
            val totalHeightInt = usableHeight.roundToInt().coerceAtLeast(numRows)
            val baseHeight = (totalHeightInt / numRows).coerceAtLeast(1)
            val remainder = totalHeightInt - (baseHeight * numRows)
            MutableList(numRows) { index ->
                val extra = if (index < remainder) 1 else 0
                baseHeight + extra
            }
        }
        
        var currentY = 0f
        
        allRows.forEachIndexed { rowIndex, row ->
            val rowHeight = max(rowHeights.getOrElse(rowIndex) { 0 }, 1)
            
            val totalWidthUnits = row.sumOf { keyModel ->
                getKeyWidthFactor(keyModel.label).toDouble()
            }.toFloat().coerceAtLeast(1f)
            
            val spacingTotal = if (row.size > 1) horizontalSpacingPx * (row.size - 1) else 0f
            val usableWidth = (screenWidth.toFloat() - (edgePaddingPx * 2f)).coerceAtLeast(0f)
            val contentWidth = (usableWidth - spacingTotal).coerceAtLeast(0f)
            val indentRatio = resolveIndentRatio(rowIndex, row, allRows)
            val indentUnits = (indentRatio * 2f).coerceAtLeast(0f)
            val denominator = (totalWidthUnits + indentUnits).coerceAtLeast(1f)
            val unitWidth = if (denominator > 0f) contentWidth / denominator else 0f
            val indentPx = indentRatio * unitWidth
            val rowWidth = totalWidthUnits * unitWidth + spacingTotal
            val extraSpace = (usableWidth - (indentPx * 2f) - rowWidth).coerceAtLeast(0f)
            val startX = edgePaddingPx + indentPx + (extraSpace / 2f)
            
            var currentX = startX
            
            row.forEachIndexed { keyIndex, keyModel ->
                var keyWidth = unitWidth * getKeyWidthFactor(keyModel.label)
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
                
                val dynamicKey = DynamicKey(
                    x = resolvedX.roundToInt(),
                    y = currentY.roundToInt(),
                    width = keyWidth.roundToInt().coerceAtLeast(1),
                    height = rowHeight,
                    label = keyModel.label,
                    code = keyModel.code,
                    longPressOptions = keyModel.longPress,
                    hintLabel = keyModel.altLabel
                )
                dynamicKeys.add(dynamicKey)
            }
            
            currentY += rowHeight
            if (rowIndex < numRows - 1) {
                currentY += verticalSpacingPx
            }
        }
        
        android.util.Log.d("SwipeKeyboardView", "✅ Dynamic layout set: ${dynamicKeys.size} keys (${layout.direction}, numberRow: $showNumberRow)")
        publishSwipeGeometry()
        invalidate()
        requestLayout()
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
        
        val containsAnchors = row.any { key ->
            when (key.code) {
                Keyboard.KEYCODE_SHIFT,
                Keyboard.KEYCODE_ALT,
                Keyboard.KEYCODE_DELETE,
                -1, -5 -> true
                else -> false
            }
        }
        if (containsAnchors) return 0f
        
        return 0.5f
    }
    
    /**
     * Toggle number row visibility without reloading the entire layout
     * @param enable Whether to show or hide the number row
     */
    fun toggleNumberRow(enable: Boolean) {
        currentNumberRowEnabled = enable
        currentLayoutModel?.let { layout ->
            setDynamicLayout(layout, enable)
            android.util.Log.d("SwipeKeyboardView", "✅ Number row toggled: $enable")
        }
    }
    
    /**
     * Show accent options popup for dynamic key long-press
     */
    private fun showDynamicAccentOptions(key: DynamicKey) {
        val longPressOptions = key.longPressOptions ?: return
        if (longPressOptions.isEmpty()) return
        
        try {
            // Hide any existing popups first
            hideDynamicAccentOptions()
            
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(themeManager?.getKeyColor() ?: Color.WHITE)
                setPadding(8, 8, 8, 8)
            }
            
            // Add original character first
            addDynamicAccentOption(container, key.label)
            
            // Add long-press variants
            longPressOptions.forEach { variant ->
                addDynamicAccentOption(container, variant)
            }
            
            dynamicAccentPopup = PopupWindow(
                container,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setBackgroundDrawable(ColorDrawable(
                    themeManager?.getKeyColor() ?: Color.WHITE
                ))
                
                isFocusable = false
                isOutsideTouchable = true
                isTouchable = true
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                elevation = 8f
                
                setOnDismissListener {
                    try {
                        dynamicLongPressKey = null
                        dynamicLongPressRunnable?.let { runnable: Runnable ->
                            dynamicLongPressHandler.removeCallbacks(runnable)
                        }
                        dynamicLongPressRunnable = null
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
            
            // Measure the popup to get its dimensions
            container.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth = container.measuredWidth
            val popupHeight = container.measuredHeight
            
            // Calculate position: center horizontally above the pressed key
            val location = IntArray(2)
            this.getLocationInWindow(location)
            
            val popupX = location[0] + key.x + (key.width / 2) - (popupWidth / 2)
            val popupY = location[1] + key.y - popupHeight - 10 // 10px above the key
            
            // Show popup above the pressed key
            try {
                dynamicAccentPopup?.showAtLocation(this, Gravity.NO_GRAVITY, popupX, popupY)
            } catch (e: Exception) {
                // Fallback positioning
                dynamicAccentPopup?.showAtLocation(this, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 150)
            }
            
        } catch (e: Exception) {
            // Ignore accent popup errors and clean up
            hideDynamicAccentOptions()
        }
    }
    
    /**
     * Add accent option to container for dynamic keys
     */
    private fun addDynamicAccentOption(container: LinearLayout, accent: String) {
        val button = Button(context).apply {
            text = accent
            textSize = 18f
            setPadding(16, 8, 16, 8)
            background = null
            setTextColor(themeManager?.getTextColor() ?: Color.BLACK)
            
            setOnClickListener {
                // Send the selected accent character
                val accentCode = accent.codePointAt(0)
                (context as? AIKeyboardService)?.onKey(accentCode, intArrayOf(accentCode))
                hideDynamicAccentOptions()
                android.util.Log.d("SwipeKeyboardView", "✅ Dynamic accent selected: $accent | Code: $accentCode")
            }
        }
        container.addView(button)
    }
    
    /**
     * Check if dynamic accent popup is showing
     */
    private fun isDynamicAccentPopupShowing(): Boolean {
        return dynamicAccentPopup?.isShowing == true
    }
    
    /**
     * Hide dynamic accent options popup
     */
    private fun hideDynamicAccentOptions() {
        try {
            dynamicAccentPopup?.dismiss()
            dynamicAccentPopup = null
        } catch (e: Exception) {
            // Ignore dismissal errors
        }
    }

    /**
     * ✅ FIXED: Set keyboard mode and rebuild layout with correct key codes
     * This ensures symbol mode sends correct key codes, not letter codes
     * 
     * @param mode The keyboard mode to switch to (LETTERS, SYMBOLS, etc.)
     * @param layoutAdapter The layout adapter to build the new layout
     * @param showNumberRow Whether to show the number row (only for LETTERS mode)
     */
    fun setKeyboardMode(
        mode: LanguageLayoutAdapter.KeyboardMode, 
        layoutAdapter: LanguageLayoutAdapter, 
        showNumberRow: Boolean = false
    ) {
        // Guard against premature calls before view is measured
        if (width == 0 || height == 0) {
            post { setKeyboardMode(mode, layoutAdapter, showNumberRow) }
            return
        }
        
        currentKeyboardMode = mode
        android.util.Log.d("SwipeKeyboardView", "✅ setKeyboardMode: $mode for language: $currentLangCode")
        
        // Launch coroutine to build layout asynchronously using proper scope
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                // Build layout for the specified mode - this generates correct key codes
                val layout = layoutAdapter.buildLayoutFor(currentLangCode, mode, showNumberRow)
                
                // Apply the layout with correct key mappings
                setDynamicLayout(layout, showNumberRow)
                
                android.util.Log.d("SwipeKeyboardView", 
                    "✅ Layout rebuilt for mode: $mode, keys: ${layout.rows.flatten().size}")
                
                // 🎯 AUTO-ADJUST: Notify parent containers AFTER layout is built
                // This ensures keyboard height is recalculated with the new layout
                post {
                    (parent as? android.view.View)?.requestLayout()
                    (parent?.parent as? android.view.View)?.requestLayout()
                    android.util.Log.d("SwipeKeyboardView", "🎯 Auto-adjust triggered after layout build")
                }
                
                // Force refresh to update display
                invalidate()
            } catch (e: Exception) {
                android.util.Log.e("SwipeKeyboardView", "❌ Failed to set keyboard mode: $mode", e)
            }
        }
    }
    
    /**
     * Get width factor for a key based on its label
     * This allows special keys to be wider or narrower than standard keys
     */
    private fun getKeyWidthFactor(label: String): Float {
        return when {
            // Space bar - extra wide (BIGGER) - check for actual space character
            label == " " || label == "SPACE" || label.startsWith("space") -> 2.5f
            
            // Return/Enter key - wider (BIGGER) - check for return symbol
            label == "⏎" || label == "RETURN" || label == "sym_keyboard_return" -> 1.5f
            
            // Mode switches - smaller
            label == "?123" || label == "ABC" || label == "=<" || label == "123" -> 1.1f
            
            // Globe key - smaller - check for globe emoji
            label == "🌐" || label == "GLOBE" -> 1f
            
            // Comma and period - smaller
            label == "," || label == "." -> 1f
            
            // Special function keys - moderately wider
            label == "⇧" || label == "SHIFT" -> 1.5f
            label == "⌫" || label == "DELETE" -> 1.5f
            
            // Standard keys
            else -> 1.0f
        }
    }
    
    /**
     * 🔍 AUDIT: Get drawable resource ID for special key labels
     * Maps key labels to their corresponding Android drawable icons
     */
    private fun getDrawableForKey(label: String): Int? {
        return when (label.uppercase()) {
            "SHIFT", "⇧" -> R.drawable.sym_keyboard_shift
            "DELETE", "⌫" -> R.drawable.sym_keyboard_delete
            "RETURN", "SYM_KEYBOARD_RETURN" -> R.drawable.sym_keyboard_return
            "GLOBE", "🌐" -> R.drawable.sym_keyboard_globe
            "SPACE" -> R.drawable.sym_keyboard_space
            // CRITICAL FIX: Add missing mode switch key icons (using return icon as placeholder)
            "?123", "ABC", "=<", "123" -> R.drawable.sym_keyboard_return
            else -> null
        }
    }

    private fun dpToPx(dp: Int): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
    }
    
    /**
     * Get icon resource ID based on key type and label for dynamic layouts
     */
    private fun getIconForKeyType(keyType: String, label: String): Int? {
        return when (keyType) {
            "shift" -> R.drawable.sym_keyboard_shift
            "backspace" -> R.drawable.sym_keyboard_delete
            "enter" -> R.drawable.sym_keyboard_return
            "globe" -> R.drawable.sym_keyboard_globe
            "space" -> R.drawable.sym_keyboard_space
            else -> {
                // Fallback: check label for special keys
                when (label.uppercase()) {
                    "SHIFT", "⇧" -> R.drawable.sym_keyboard_shift
                    "DELETE", "⌫" -> R.drawable.sym_keyboard_delete
                    "RETURN", "SYM_KEYBOARD_RETURN" -> R.drawable.sym_keyboard_return
                    "GLOBE", "🌐" -> R.drawable.sym_keyboard_globe
                    "SPACE" -> R.drawable.sym_keyboard_space
                    else -> null
                }
            }
        }
    }
    
    /**
     * Switch back to traditional Keyboard XML mode
     */
    fun useLegacyKeyboardMode() {
        isDynamicLayoutMode = false
        dynamicKeys.clear()
        android.util.Log.d("SwipeKeyboardView", "Switched to legacy Keyboard XML mode")
        publishSwipeGeometry()
        invalidate()
    }
    
    override fun setKeyboard(keyboard: Keyboard?) {
        super.setKeyboard(keyboard)
        if (!isDynamicLayoutMode) {
            post { publishSwipeGeometry() }
        }
    }
    
    /**
     * Calculate clipboard key layout
     */
    private fun calculateClipboardKeyLayout() {
        clipboardKeyRects.clear()
        
        val padding = 16f
        val keyMargin = 8f
        val backButtonHeight = 80f
        val availableWidth = width - (padding * 2)
        val availableHeight = height - (padding * 2) - backButtonHeight - keyMargin
        
        // Calculate grid layout
        val columns = 2
        val rows = minOf(((clipboardItems.size + columns - 1) / columns), 5) // Max 5 rows
        
        val keyWidth = (availableWidth - (keyMargin * (columns - 1))) / columns
        val keyHeight = if (rows > 0) (availableHeight - (keyMargin * (rows - 1))) / rows else 0f
        
        // Create key rectangles
        for (i in clipboardItems.indices) {
            val row = i / columns
            val col = i % columns
            
            if (row < 5) { // Only show first 10 items (5 rows × 2 columns)
                val left = padding + (col * (keyWidth + keyMargin))
                val top = padding + (row * (keyHeight + keyMargin))
                val right = left + keyWidth
                val bottom = top + keyHeight
                
                clipboardKeyRects.add(RectF(left, top, right, bottom))
            }
        }
        
        // Add back button rectangle at the bottom
        val backButtonTop = height - backButtonHeight - padding
        clipboardKeyRects.add(RectF(
            padding, 
            backButtonTop, 
            padding + availableWidth, 
            backButtonTop + backButtonHeight
        ))
    }
    
    
    /**
     * Draw clipboard layout
     */
    private fun drawClipboardLayout(canvas: Canvas) {
        
        val manager = themeManager
        if (manager == null) {
            // Fallback colors if no theme manager
            canvas.drawColor(Color.parseColor("#F5F5F5"))
            return
        }
        val palette = manager.getCurrentPalette()
        val backgroundColor = palette.keyboardBg
        val keyBackgroundColor = palette.keyBg
        val keyTextColor = palette.keyText
        val accentColor = palette.specialAccent
        
        // Draw background
        canvas.drawColor(backgroundColor)
        
        // Draw clipboard items
        for (i in clipboardItems.indices.take(clipboardKeyRects.size - 1)) {
            val item = clipboardItems[i]
            val rect = clipboardKeyRects[i]
            
            // Choose background color (accent for pinned/template items)
            val bgColor = if (item.isPinned || item.isTemplate) {
                adjustColorAlpha(accentColor, 0.1f)
            } else {
                keyBackgroundColor
            }
            
            // Draw key background
            val keyPaint = Paint().apply {
                color = bgColor
                isAntiAlias = true
            }
            val cornerRadius = 8f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)
            
            // Draw key border
            val borderPaint = Paint().apply {
                color = adjustColorAlpha(keyTextColor, 0.2f)
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
            
            // Draw text
            val textPaint = Paint().apply {
                color = keyTextColor
                textSize = palette.keyFontSize
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            
            // Prepare text (truncate if needed)
            val prefix = if (item.isOTP()) "🔢 " else if (item.isTemplate) "📌 " else ""
            val displayText = prefix + item.getPreview(20)
            
            // Draw text centered in rect
            val textX = rect.centerX()
            val textY = rect.centerY() + (textPaint.textSize / 3)
            canvas.drawText(displayText, textX, textY, textPaint)
            
            // Draw category for templates
            if (item.isTemplate && item.category != null) {
                val categoryPaint = Paint().apply {
                    color = adjustColorAlpha(keyTextColor, 0.6f)
                    textSize = palette.keyFontSize * 0.75f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                val categoryY = rect.bottom - 12f
                canvas.drawText(item.category, textX, categoryY, categoryPaint)
            }
        }
        
        // Draw back button
        if (clipboardKeyRects.isNotEmpty()) {
            val backRect = clipboardKeyRects.last()
            
            // Draw back button background
            val backPaint = Paint().apply {
                color = accentColor
                isAntiAlias = true
            }
            val cornerRadius = 8f
            canvas.drawRoundRect(backRect, cornerRadius, cornerRadius, backPaint)
            
            // Draw back button text
            val backTextPaint = Paint().apply {
                color = Color.WHITE // Intentional: White text for delete key icon contrast
                textSize = palette.keyFontSize * 1.2f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            
            val backTextX = backRect.centerX()
            val backTextY = backRect.centerY() + (backTextPaint.textSize / 3)
            canvas.drawText("⬅ Back to Keyboard", backTextX, backTextY, backTextPaint)
        }
    }
    
    
    /**
     * Handle touch in clipboard mode
     */
    private fun handleClipboardTouch(x: Float, y: Float) {
        // Check clipboard item touches
        for (i in clipboardItems.indices.take(clipboardKeyRects.size - 1)) {
            val rect = clipboardKeyRects[i]
            if (rect.contains(x, y)) {
                val item = clipboardItems[i]
                // Call back to service to handle clipboard key tap
                (context as? AIKeyboardService)?.let { service ->
                    try {
                        val method = service.javaClass.getDeclaredMethod("handleClipboardKeyTap", ClipboardItem::class.java)
                        method.isAccessible = true
                        method.invoke(service, item)
                    } catch (e: Exception) {
                        android.util.Log.e("SwipeKeyboardView", "Error calling handleClipboardKeyTap", e)
                    }
                }
                return
            }
        }
        
        // Check back button touch
        if (clipboardKeyRects.isNotEmpty()) {
            val backRect = clipboardKeyRects.last()
            if (backRect.contains(x, y)) {
                // Call back to service to handle back button
                (context as? AIKeyboardService)?.let { service ->
                    try {
                        val method = service.javaClass.getDeclaredMethod("handleClipboardBackTap")
                        method.isAccessible = true
                        method.invoke(service)
                    } catch (e: Exception) {
                        android.util.Log.e("SwipeKeyboardView", "Error calling handleClipboardBackTap", e)
                    }
                }
                return
            }
        }
    }
    
    /**
     * Utility method to adjust color alpha
     */
    private fun adjustColorAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return Color.argb(a, r, g, b)
    }
    
    /**
     * Initialize adaptive sizing based on screen dimensions
     */
    private fun initializeAdaptiveSizing() {
        if (!isAdaptiveSizingEnabled) return
        
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density
        
        // Determine screen size category
        val screenWidthDp = screenWidth / density
        
        when {
            screenWidthDp < SCREEN_SIZE_SMALL -> {
                isSmallScreenOptimized = true
                touchTargetExpansion = context.resources.getDimension(R.dimen.small_screen_touch_expansion)
            }
            else -> {
                touchTargetExpansion = context.resources.getDimension(R.dimen.touch_target_expansion)
            }
        }
    }
    
    /**
     * Enable floating keyboard mode
     */
    fun enableFloatingMode(enable: Boolean) {
        isFloatingMode = enable
        if (enable) {
            elevation = 12f
        } else {
            elevation = 0f
        }
        invalidate()
    }
    
    /**
     * Handle spacebar gesture detection
     */
    private fun handleSpacebarGesture(event: MotionEvent): Boolean {
        if (!spacebarGestureEnabled) return false
        
        val key = getKeyAtPosition(event.x, event.y)
        if (key?.codes?.firstOrNull() != 32) { // Not spacebar
            isSpacebarPressed = false
            return false
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isSpacebarPressed = true
                spacebarStartX = event.x
                lastSpacebarX = event.x
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!isSpacebarPressed) return false
                
                val deltaX = event.x - lastSpacebarX
                val totalDeltaX = event.x - spacebarStartX
                
                if (Math.abs(totalDeltaX) > SPACEBAR_GESTURE_THRESHOLD) {
                    handleSpacebarCursorControl(deltaX)
                    lastSpacebarX = event.x
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (!isSpacebarPressed) return false
                
                val deltaX = event.x - spacebarStartX
                val distance = Math.abs(deltaX)
                
                if (distance > SPACEBAR_GESTURE_THRESHOLD) {
                    when {
                        deltaX < WORD_DELETE_VELOCITY_THRESHOLD -> {
                            handleSpacebarSwipeLeft()
                            return true
                        }
                        deltaX > PERIOD_INSERT_VELOCITY_THRESHOLD -> {
                            handleSpacebarSwipeRight()
                            return true
                        }
                    }
                }
                
                isSpacebarPressed = false
            }
        }
        
        return false
    }
    
    /**
     * Handle spacebar swipe left - delete word
     */
    private fun handleSpacebarSwipeLeft() {
        (context as? AIKeyboardService)?.let { service ->
            service.currentInputConnection?.let { ic ->
                val beforeCursor = ic.getTextBeforeCursor(50, 0)
                if (!beforeCursor.isNullOrEmpty()) {
                    val words = beforeCursor.toString().split(Regex("\\s+"))
                    if (words.isNotEmpty()) {
                        val lastWord = words.last()
                        if (lastWord.isNotEmpty()) {
                            ic.deleteSurroundingText(lastWord.length, 0)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Handle spacebar swipe right - insert period and space
     */
    private fun handleSpacebarSwipeRight() {
        (context as? AIKeyboardService)?.let { service ->
            service.currentInputConnection?.commitText(". ", 1)
        }
    }
    
    /**
     * Handle spacebar cursor control
     */
    private fun handleSpacebarCursorControl(deltaX: Float) {
        val cursorMoves = (deltaX / CURSOR_CONTROL_SENSITIVITY).toInt()
        
        (context as? AIKeyboardService)?.let { service ->
            service.currentInputConnection?.let { ic ->
                try {
                    val extractedText = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    val currentPos = extractedText?.selectionStart ?: 0
                    val textLength = extractedText?.text?.length ?: 0
                    val newPos = (currentPos + cursorMoves).coerceIn(0, textLength)
                    
                    ic.setSelection(newPos, newPos)
                } catch (e: Exception) {
                    // Handle cursor control error
                }
            }
        }
    }
    
    /**
     * Get key at specific position with enhanced touch target detection
     */
    private fun getKeyAtPosition(x: Float, y: Float): Keyboard.Key? {
        keyboard?.keys?.forEach { key ->
            val expandedBounds = if (isSmallScreenOptimized) {
                RectF(
                    key.x.toFloat() - touchTargetExpansion,
                    key.y.toFloat() - touchTargetExpansion,
                    (key.x + key.width).toFloat() + touchTargetExpansion,
                    (key.y + key.height).toFloat() + touchTargetExpansion
                )
            } else {
                RectF(
                    key.x.toFloat(),
                    key.y.toFloat(),
                    (key.x + key.width).toFloat(),
                    (key.y + key.height).toFloat()
                )
            }
            
            if (expandedBounds.contains(x, y)) {
                return key
            }
        }
        return null
    }
}
