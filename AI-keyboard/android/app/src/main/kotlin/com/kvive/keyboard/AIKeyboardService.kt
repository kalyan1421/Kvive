package com.kvive.keyboard
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.json.JSONArray
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibrationEffect
import android.text.TextUtils
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.ExtractedTextRequest
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupWindow
import android.view.Gravity
import android.graphics.drawable.ColorDrawable
import java.util.Locale
import com.kvive.keyboard.utils.*
import com.kvive.keyboard.utils.ProcessGuard
import android.inputmethodservice.InputMethodService.Insets
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.kvive.keyboard.GestureAction
import com.kvive.keyboard.GestureSettings
import com.kvive.keyboard.GestureSource
import com.kvive.keyboard.VoiceInputManager

class AIKeyboardService : InputMethodService(), 
    KeyboardView.OnKeyboardActionListener, 
    SwipeKeyboardView.SwipeListener {
    
    companion object {
        private const val TAG = "AIKeyboardService"
        
        // Singleton instance for external access
        private var instance: AIKeyboardService? = null
        
        fun getInstance(): AIKeyboardService? = instance
        
        // Keyboard layouts
        private const val KEYBOARD_LETTERS = 1
        private const val KEYBOARD_SYMBOLS = 2
        private const val KEYBOARD_NUMBERS = 3
        
        // Custom key codes
        private const val KEYCODE_SPACE = 32
        private const val KEYCODE_SYMBOLS = -10
        private const val KEYCODE_LETTERS = -11
        private const val KEYCODE_NUMBERS = -12
        private const val KEYCODE_VOICE = -13
        private const val KEYCODE_GLOBE = -14
        private const val KEYCODE_EMOJI = -15
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_DELETE = -5
        
        // Swipe settings
        private const val SWIPE_START_THRESHOLD = 100f
        private const val MIN_SWIPE_TIME = 300L
        private const val DOUBLE_SPACE_TIMEOUT_MS = 500L  // Gboard-style timeout
        private const val MIN_SWIPE_DISTANCE = 100f
        private const val SWIPE_CONFIDENCE_THRESHOLD = 0.55
        private const val SWIPE_CONFIDENCE_GAP = 0.2
        
        // Advanced keyboard settings
        private const val LONG_PRESS_TIMEOUT = 500L
        private const val DOUBLE_TAP_TIMEOUT = 300L

        private const val VOICE_RESULT_FLUSH_INTERVAL_MS = 150L
        
        // Shift states
        private const val SHIFT_OFF = 0
        private const val SHIFT_ON = 1 
        private const val SHIFT_CAPS = 2
    }
    
    /**
     * Keyboard mode enum for multi-mode layout system
     * Letters → Symbols → Extended Symbols → Dialer
     */
    enum class KeyboardMode {
        LETTERS,
        NUMBERS,
        SYMBOLS,
        EXTENDED_SYMBOLS,
        DIALER,
        EMOJI
    }

    private fun KeyboardMode.toLayoutMode(): LanguageLayoutAdapter.KeyboardMode = when (this) {
        KeyboardMode.LETTERS -> LanguageLayoutAdapter.KeyboardMode.LETTERS
        KeyboardMode.NUMBERS, KeyboardMode.SYMBOLS -> LanguageLayoutAdapter.KeyboardMode.SYMBOLS
        KeyboardMode.EXTENDED_SYMBOLS -> LanguageLayoutAdapter.KeyboardMode.EXTENDED_SYMBOLS
        KeyboardMode.DIALER -> LanguageLayoutAdapter.KeyboardMode.DIALER
        KeyboardMode.EMOJI -> LanguageLayoutAdapter.KeyboardMode.LETTERS
    }
    
    /**
     * Unified feature panel types
     * Single dynamic panel for all toolbar features
     */
    enum class PanelType {
        GRAMMAR_FIX,
        WORD_TONE,
        AI_ASSISTANT,
        CLIPBOARD,
        QUICK_SETTINGS,
        EMOJI
    }
    
    /**
     * AI Panel Type enum
     */
    
    /**
     * Unified settings container for all keyboard configuration
     */
    private data class UnifiedSettings(
        val vibrationEnabled: Boolean,
        val soundEnabled: Boolean,
        val keyPreviewEnabled: Boolean,
        val showNumberRow: Boolean,
        val swipeTypingEnabled: Boolean,
        val aiSuggestionsEnabled: Boolean,
        val currentLanguage: String,
        val enabledLanguages: List<String>,
        val autocorrectEnabled: Boolean,
        val autoCapitalization: Boolean,
        val autoFillSuggestion: Boolean,
        val rememberCapsState: Boolean,
        val doubleSpacePeriod: Boolean,
        val popupEnabled: Boolean,
        val soundType: String,
        val effectType: String,
        val soundVolume: Float,
        val soundCustomUri: String?
    )
    
    /**
     * Internal SettingsManager - consolidates reads from multiple SharedPreferences sources
     * Eliminates redundant I/O by reading once per load cycle
     */
    private class SettingsManager(private val context: Context) {
        private val flutterPrefs by lazy {
            context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        }
        private val nativePrefs by lazy {
            context.getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
        }
        
        /**
         * Load all settings from both preference sources in a single pass
         * @return UnifiedSettings with all keyboard configuration
         */
        fun loadAll(): UnifiedSettings {
            // ✅ CRITICAL: Sound and vibration ONLY from Flutter preferences - NO fallback to native
            val sound = when {
                flutterPrefs.contains("flutter.sound_enabled") ->
                    flutterPrefs.getBoolean("flutter.sound_enabled", false)
                flutterPrefs.contains("flutter.soundEnabled") ->
                    flutterPrefs.getBoolean("flutter.soundEnabled", false)
                else -> false // Default to false (sound off) if not set in Flutter
            }
            val vibration = when {
                flutterPrefs.contains("flutter.vibration_enabled") ->
                    flutterPrefs.getBoolean("flutter.vibration_enabled", false)
                else -> false // ✅ Default to false (vibration OFF) if not set in Flutter
            }
            
            // ✅ DISABLED: Popup preview permanently disabled (user request)
            val keyPreview = false  // Always disabled
            
            val showNumberRow = nativePrefs.getBoolean("show_number_row", false)
            val swipeTyping = nativePrefs.getBoolean("swipe_typing", true)
            val aiSuggestions = nativePrefs.getBoolean("ai_suggestions", true)
            val autocorrect = nativePrefs.getBoolean("auto_correct", true)  // ✅ Fixed: Match key used by MainActivity and isAutoCorrectEnabled()

            val autoCapNative = nativePrefs.getBoolean("auto_capitalization", true)
            val autoCap = when {
                flutterPrefs.contains("flutter.auto_capitalization") ->
                    flutterPrefs.getBoolean("flutter.auto_capitalization", autoCapNative)
                flutterPrefs.contains("flutter.autoCapitalization") ->
                    flutterPrefs.getBoolean("flutter.autoCapitalization", autoCapNative)
                else -> autoCapNative
            }

            val autoFillNative = nativePrefs.getBoolean("auto_fill_suggestion", true)
            val autoFill = when {
                flutterPrefs.contains("flutter.auto_fill_suggestion") ->
                    flutterPrefs.getBoolean("flutter.auto_fill_suggestion", autoFillNative)
                flutterPrefs.contains("flutter.autoFillSuggestion") ->
                    flutterPrefs.getBoolean("flutter.autoFillSuggestion", autoFillNative)
                else -> autoFillNative
            }

            val rememberCapsNative = nativePrefs.getBoolean("remember_caps_state", false)
            val rememberCaps = when {
                flutterPrefs.contains("flutter.remember_caps_state") ->
                    flutterPrefs.getBoolean("flutter.remember_caps_state", rememberCapsNative)
                else -> rememberCapsNative
            }

            val doubleSpaceNative = nativePrefs.getBoolean("double_space_period", true)
            val doubleSpace = when {
                flutterPrefs.contains("flutter.double_space_period") ->
                    flutterPrefs.getBoolean("flutter.double_space_period", doubleSpaceNative)
                flutterPrefs.contains("flutter.doubleSpacePeriod") ->
                    flutterPrefs.getBoolean("flutter.doubleSpacePeriod", doubleSpaceNative)
                else -> doubleSpaceNative
            }
            val popup = nativePrefs.getBoolean("popup_enabled", false)
            val soundType = flutterPrefs.getString("flutter.sound.type", "default") ?: "default"
            val effectType = flutterPrefs.getString("flutter.effect.type", "none") ?: "none"
            // ✅ CRITICAL: Read sound volume ONLY from Flutter preferences (0-100 scale, convert to 0-1) - NO fallback
            val soundVolumePercent = flutterPrefs.getIntCompat("flutter.sound_volume", 50) // Default 50%
            val soundVolume = (soundVolumePercent / 100.0f).coerceIn(0f, 1f)
            val soundCustomUri = nativePrefs.getString("sound_custom_uri", null)
            
            // Read language settings from Flutter preferences
            val currentLang = flutterPrefs.getString("flutter.current_language", "en") ?: "en"
            val enabledLangsStr = flutterPrefs.getString("flutter.enabled_languages", "en") ?: "en"
            val enabledLangs = try {
                enabledLangsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } catch (_: Exception) {
                listOf("en")
            }
            
            return UnifiedSettings(
                vibrationEnabled = vibration,
                soundEnabled = sound,
                keyPreviewEnabled = keyPreview,
                showNumberRow = showNumberRow,
                swipeTypingEnabled = swipeTyping,
                aiSuggestionsEnabled = aiSuggestions,
                currentLanguage = currentLang,
                enabledLanguages = enabledLangs,
                autocorrectEnabled = autocorrect,
                autoCapitalization = autoCap,
                autoFillSuggestion = autoFill,
                rememberCapsState = rememberCaps,
                doubleSpacePeriod = doubleSpace,
                popupEnabled = popup,
                soundType = soundType,
                effectType = effectType,
                soundVolume = soundVolume,
                soundCustomUri = soundCustomUri
            )
        }
    }
    
    // UI Components
    private var keyboardView: SwipeKeyboardView? = null // Legacy keyboard view (still actively used)
    private var unifiedKeyboardView: UnifiedKeyboardView? = null // ✅ NEW: Unified view for keyboard + panels
    private var unifiedViewReady = false // Track if view is fully initialized
    private var pendingSuggestions: List<String>? = null // Buffer suggestions before view is ready
    private var pendingInputSnapshot: String? = null
    private var initialInsetsApplied = false
    private var lastAppliedLang: String? = null
    private var lastAppliedMode: KeyboardMode? = null
    private var lastNumberRowState: Boolean? = null
    private var keyboard: Keyboard? = null
    private lateinit var keyboardHeightManager: KeyboardHeightManager
    private var suggestionContainer: LinearLayout? = null
    private var topContainer: LinearLayout? = null // Container for suggestions + language switch
    internal var keyboardContainer: LinearLayout? = null
    internal var mainKeyboardLayout: LinearLayout? = null // Main layout containing toolbar + keyboard
    
    // ✅ UNIFIED PANEL MANAGER - Single source for all panels
    private var unifiedPanelManager: UnifiedPanelManager? = null
    private var currentAiSourceText: String = ""
    
    // Emoji panel visibility helper (delegates to UnifiedPanelManager)
    private var isEmojiPanelVisible: Boolean
        get() = unifiedPanelManager?.isPanelVisible() == true && unifiedPanelManager?.getCurrentPanelType() == UnifiedPanelManager.PanelType.EMOJI
        set(value) { /* Handled by UnifiedPanelManager */ }
    
    private var emojiSearchActive: Boolean = false
    
    // Enhancements: Simple tracking (KeyboardEnhancements.kt removed as unused)
    private var lastSettingsApply = 0L
    
    // Prompt management broadcast receiver
    private val promptReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.kvive.keyboard.PROMPTS_UPDATED") {
                reloadAIPrompts()
                Log.d(TAG, "♻️ PromptManager sync triggered from PROMPTS_UPDATED broadcast")
            }
        }
    }
    
    /**
     * Reload AI prompts after update broadcast
     * ✅ Updated to use UnifiedPanelManager
     */
    private fun reloadAIPrompts() {
        try {
            val shouldReopen = unifiedPanelManager?.getCurrentPanelType() == UnifiedPanelManager.PanelType.AI_ASSISTANT
            unifiedPanelManager?.refreshAIPrompts()

            if (shouldReopen && unifiedKeyboardView != null) {
                mainHandler.post {
                    showFeaturePanel(PanelType.AI_ASSISTANT)
                }
            }

            Log.d(TAG, "✅ AI prompts refreshed via UnifiedPanelManager")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reloading AI prompts", e)
        }
    }
    
    // Keyboard state
    private var caps = false
    private var isShifted = false
    private var currentKeyboard = KEYBOARD_LETTERS
    
    // CleverType keyboard mode cycling
    private var currentKeyboardMode = KeyboardMode.LETTERS
    private var previousKeyboardMode = KeyboardMode.LETTERS  // For emoji panel return
    private var pendingVoiceResult: String? = null
    private val pendingVoiceFlushRunnable = object : Runnable {
        override fun run() {
            val pending = pendingVoiceResult?.takeIf { it.isNotBlank() } ?: return
            val committed = commitVoiceResultInternal(pending)
            if (committed) {
                pendingVoiceResult = null
            } else {
                mainHandler.postDelayed(this, VOICE_RESULT_FLUSH_INTERVAL_MS)
            }
        }
    }
    
    // Advanced keyboard state
    private var shiftState = SHIFT_OFF
    private var lastShiftPressTime = 0L
    private var lastLegacyShiftVisualState = CapsShiftManager.STATE_NORMAL
    
    // Long-press detection for accent characters (spacebar long-press removed)
    private var currentLongPressKey: Int = -1
    private var currentLongPressKeyObject: Keyboard.Key? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (hasAccentVariants(currentLongPressKey)) {
            showAccentOptions(currentLongPressKey)
        }
    }
    
    private var keyPreviewPopup: PopupWindow? = null
    private var accentPopup: PopupWindow? = null
    private var vibrator: Vibrator? = null
    
    // Enhanced Caps/Shift Management
    private lateinit var capsShiftManager: CapsShiftManager
    private var shiftOptionsMenu: ShiftOptionsMenu? = null
    
    // Enhanced gesture support
    private var isSlideToDeleteModeActive = false
    
    // Advanced keyboard settings from Flutter
    private lateinit var keyboardSettings: KeyboardSettings
    
    // Unified layout orchestrator (centralizes all layout loading)
    private lateinit var unifiedController: UnifiedLayoutController
    
    // Accent mappings for long-press functionality
    private val accentMap = mapOf(
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
    
    // Keyboard settings
    private var showNumberRow = false
    private var swipeEnabled = true
    
    // Unified Sound & Vibration Settings
    private var vibrationEnabled = false
    private var vibrationMs = 50
    private var useHapticInterface = false
    private var keyPressVibration = false
    private var longPressVibration = false
    private var repeatedActionVibration = false
    private var soundEnabled = false
    private var soundVolume = 0.65f
    private var keyPressSounds = false
    private var longPressSounds = false
    private var repeatedActionSounds = false
    // ✅ Removed instance variable - now using KeyboardSoundManager singleton object
    private var selectedSoundProfile: String = "default"
    private var customSoundUri: String? = null
    private var selectedTapEffectStyle: String = "ripple"
    private var lastSoundConfigSignature: String? = null
    
    // ✅ Cached sound settings to prevent repeated loading
    private var cachedSoundEnabled: Boolean? = null
    private var cachedSoundVolume: Float? = null
    private var cachedSoundIntensity: Int? = null
    
    // Language cycling - Now managed by SharedPreferences
    private var enabledLanguages = listOf("en")
    private var currentLanguage = "en"
    private var multilingualEnabled = false
    private var currentLanguageIndex = 0
    
    // Transliteration support for Indic languages (Phase 1)
    private var transliterationEngine: TransliterationEngine? = null
    // indicScriptHelper removed - was never used (dead code cleanup)  
    private var transliterationEnabled = true
    private val romanBuffer = StringBuilder()
    
    // Phase 2: Feature flags
    private var reverseTransliterationEnabled = false
    
    // AI suggestion debouncing
    // PERFORMANCE: Debounced suggestion updates with caching
    private var suggestionUpdateJob: Job? = null
    private val suggestionDebounceMs = 180L  // ✅ FIX 5: Increased from 100ms to 180ms (Gboard uses ~200ms)
    private val suggestionCache = mutableMapOf<String, List<String>>()
    
    // Swipe typing state
    private val swipeBuffer = StringBuilder()
    private val swipePath = mutableListOf<Int>()
    private var swipeStartTime = 0L
    private var isCurrentlySwiping = false
    
    private fun isSwipeAllowedForCurrentState(): Boolean {
        if (!swipeEnabled || !swipeTypingEnabled) return false
        if (currentKeyboardMode != KeyboardMode.LETTERS) return false
        if (!::autocorrectEngine.isInitialized) return false
        if (!autocorrectEngine.isLanguageLoaded(currentLanguage)) return false
        return true
    }

    private fun refreshSwipeCapability(reason: String = "") {
        val canSwipe = isSwipeAllowedForCurrentState()
        keyboardView?.setSwipeEnabled(canSwipe)
        unifiedKeyboardView?.setSwipeEnabled(canSwipe)
        if (!canSwipe) {
            lastCommittedSwipeWord = ""
            lastSwipeAutoInsertedSpace = true
        }
        Log.d(TAG, "Swipe capability → $canSwipe (reason=$reason, mode=$currentKeyboardMode, lang=$currentLanguage)")
    }
    
    // AI and suggestion components
    private val wordHistory = mutableListOf<String>()
    private var currentWord = ""
    private var isAIReady = false
    
    // ✅ SIMPLIFIED: Autocorrect undo and rejection tracking
    // If lastCorrection is non-null, undo is available - no need for separate flag
    private var lastCorrection: Pair<String, String>? = null // (original, corrected) - null means no undo available
    private var correctionRejected: Boolean = false
    // ✅ Explicitly track the word text that was rejected to prevent re-correction loops
    private var rejectedOriginal: String? = null
    
    // ✅ FIX 9: Flag to prevent suggestion triggers during programmatic commits
    private var isCommittingText: Boolean = false
    
    // ✅ PERFORMANCE FIX: Throttle onUpdateSelection to prevent 25-40 calls per keystroke
    private var lastUpdateSelectionTime: Long = 0L
    
    // Dictionary expansion undo tracking
    private var lastExpansion: Pair<String, String>? = null // (shortcut, expansion)
    
    // Dictionary data loaded from assets
    private var commonWords = listOf<String>()
    private var wordFrequencies = mapOf<String, Int>()
    private var corrections = mapOf<String, String>()
    private var contractions = mapOf<String, List<String>>()
    private var technologyWords = listOf<String>()
    private var businessWords = listOf<String>()
    private var allWords = listOf<String>()
    
    // Multilingual components
    private lateinit var languageManager: LanguageManager
    // languageDetector removed - was never used (dead code cleanup)
    
    // Unified suggestion system
    private lateinit var unifiedSuggestionController: UnifiedSuggestionController
    
    // Settings and Theme
    private lateinit var settings: SharedPreferences
    lateinit var themeManager: ThemeManager
    private lateinit var settingsManager: SettingsManager
    private var lastLoadedSettingsHash: Int = 0
    
    // Method channels removed for compatibility - using SharedPreferences only for theme updates
    
    private lateinit var languageLayoutAdapter: LanguageLayoutAdapter
    private lateinit var multilingualDictionary: MultilingualDictionaryImpl
    private lateinit var autocorrectEngine: UnifiedAutocorrectEngine
    private var languageSwitchView: LanguageSwitchView? = null
    
    // Enhanced prediction system
    // User dictionary sync
    private var syncHandler: Handler? = null
    private var syncRunnable: Runnable? = null
    private val syncInterval = 10 * 60 * 1000L // 10 minutes
    
    // Settings (using existing declarations above)
    // Theme variable (managed by themeManager)
    private var currentTheme = "default_theme" // Legacy compatibility (used in polling)
    private var aiSuggestionsEnabled = true
    private var autoFillSuggestionsEnabled = true
    private var autoCapitalizationEnabled = true
    private var rememberCapsLockState = false
    private var doubleSpacePeriodEnabled = true
    private var lastSpaceTimestamp = 0L
    private var swipeTypingEnabled = true
    // vibrationEnabled already declared above with new settings
    private var keyPreviewEnabled = false
    private var suggestionCount = 3  // Number of suggestions to display (3 or 4)
    private var suggestionsEnabled = true
    private var gestureSettings: GestureSettings = GestureSettings.DEFAULT
    
    // Advanced feedback settings
    private var hapticIntensity = 2 // 0=off, 1=light, 2=medium, 3=strong
    private var soundIntensity = 2
    private var visualIntensity = 0
    // soundVolume removed - now declared in Unified Sound & Vibration Settings section (line 413)
    
    // Settings polling
    private var settingsPoller: Runnable? = null
    private var lastSettingsCheck = 0L
    
    // Theme update management
    private var pendingThemeUpdate = false
    
    // Clipboard history management
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private var clipboardPanel: ClipboardPanel? = null
    private var clipboardSuggestionEnabled = true
    private var pendingClipboardSuggestionText: String? = null
    private var clipboardSuggestionConsumed = true
    private var clipboardStripView: ClipboardStripView? = null
    private var showUtilityKeyEnabled = true
    
    // Dictionary management
    private lateinit var dictionaryManager: DictionaryManager
    private var dictionaryEnabled = true
    
    // User dictionary management (personalized word learning)
    private lateinit var userDictionaryManager: UserDictionaryManager
    
    // AI Services
    private lateinit var advancedAIService: AdvancedAIService
    private lateinit var unifiedAIService: UnifiedAIService
    
    
    
    // Main handler for UI operations
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Coroutine scope for async operations
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val firebaseBlockedInIme = ProcessGuard.isFirebaseBlocked()
    
    // Clipboard history listener
    private val clipboardHistoryListener = object : ClipboardHistoryManager.ClipboardHistoryListener {
        override fun onHistoryUpdated(items: List<ClipboardItem>) {
            // Update clipboard panel if visible
            clipboardPanel?.updateItems(items)
            // Update clipboard strip
            updateClipboardStrip()
            notifyClipboardHistoryChanged(items.size)
            refreshUnifiedClipboardPanel()
        }
        
        override fun onNewClipboardItem(item: ClipboardItem) {
            clipboardSuggestionConsumed = false
            pendingClipboardSuggestionText = null
            // ✅ Notify UnifiedSuggestionController about new clipboard item
            if (clipboardSuggestionEnabled && clipboardHistoryManager.isEnabled()) {
                if (::unifiedSuggestionController.isInitialized) {
                    unifiedSuggestionController.onNewClipboardItem()
                    // Trigger suggestion update to show clipboard item
                    updateAISuggestions()
                }
            }
            // Update clipboard strip
            updateClipboardStrip()
            notifyClipboardNewItem(item)
            refreshUnifiedClipboardPanel()
        }
    }
    
    // Dictionary listener
    private val dictionaryListener = object : DictionaryManager.DictionaryListener {
        override fun onDictionaryUpdated(entries: List<DictionaryEntry>) {
            Log.d(TAG, "Dictionary updated with ${entries.size} entries")
        }
        
        override fun onExpansionTriggered(shortcut: String, expansion: String) {
            Log.d(TAG, "Dictionary expansion: $shortcut -> $expansion")
        }
    }
    
    // Broadcast receiver for settings changes and AI prompt updates
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    "com.kvive.keyboard.SETTINGS_CHANGED" -> {
                        Log.d(TAG, "SETTINGS_CHANGED broadcast received!")
                        
                        // Simple debouncing to avoid spam (replaces SettingsDebouncer)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSettingsApply < 250) {
                            Log.d(TAG, "⏳ Settings change debounced")
                            return
                        }
                        
                        // Reload settings immediately on main thread
                        mainHandler.post {
                            try {
                                Log.d(TAG, "📥 Loading settings from broadcast...")
                                lastSettingsApply = currentTime
                                
                                // ✅ FIX: Explicitly reload sound settings when settings change
                                onSettingsChanged()
                                
                                // UNIFIED SETTINGS LOAD - single read from all prefs
                                applyLoadedSettings(settingsManager.loadAll(), logSuccess = false)
                                
                                // Apply CleverType config
                                applyConfig()
                                
                                // Reload theme from Flutter SharedPreferences
                                themeManager.reload()
                                applyTheme()
                                
                                // Check if number row setting changed and reload layout
                                val numberRowEnabled = getNumberRowEnabled()
                                if (currentKeyboardMode == KeyboardMode.LETTERS) {
                                    coroutineScope.launch {
                                        loadLanguageLayout(currentLanguage)
                                        Log.d(TAG, "✅ Layout reloaded with numberRow=$numberRowEnabled")
                                    }
                                }
                                
                                // ✅ Update UnifiedSuggestionController settings
                                updateSuggestionControllerSettings()
                                
                                Log.d(TAG, "✅ Settings applied successfully")
                                applySettingsImmediately()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error applying settings from broadcast", e)
                            }
                        }
                    }
                    "com.kvive.keyboard.THEME_CHANGED" -> {
                        val themeId = intent?.getStringExtra("theme_id")
                        val themeName = intent?.getStringExtra("theme_name") ?: "Unknown"
                        val hasThemeData = intent?.getBooleanExtra("has_theme_data", false) ?: false
                        val isV2Theme = intent?.getBooleanExtra("is_v2_theme", false) ?: false
                        
                        Log.d(TAG, "🎨 THEME_CHANGED broadcast received! Theme: $themeName ($themeId), V2: $isV2Theme, Has data: $hasThemeData")
                        
                        // ✅ FIX: Replace Thread.sleep with proper async delay
                        mainHandler.postDelayed({
                            // Force reload theme from SharedPreferences
                            themeManager.reload()
                            
                            // Verify theme was actually loaded
                            val loadedTheme = themeManager.getCurrentTheme()
                            Log.d(TAG, "Loaded theme after reload: ${loadedTheme.name} (${loadedTheme.id})")
                            
                            // Check if keyboard view is ready
                            if (keyboardView != null) {
                                // Apply immediately with full refresh
                                mainHandler.post {
                                    Log.d(TAG, "⚡ Applying theme update immediately - V2: $isV2Theme")
                                    applyThemeImmediately() // Use the comprehensive theme application
                                    
                                    // Additional refresh for V2 themes
                                    if (isV2Theme) {
                                        mainHandler.postDelayed({
                                            keyboardView?.let { view ->
                                                if (view is SwipeKeyboardView) {
                                                    view.refreshTheme()
                                                    view.invalidate()
                                                }
                                            }
                                            Log.d(TAG, "🔄 V2 theme additional refresh completed")
                                        }, 100)
                                    }
                                }
                            } else {
                                // Queue for later application
                                pendingThemeUpdate = true
                                Log.d(TAG, "Keyboard view not ready, queuing V2 theme update for later")
                            }
                        }, 50) // Delay 50ms to ensure SharedPreferencesa    are written
                    }
                    "com.kvive.keyboard.CLIPBOARD_CHANGED" -> {
                        Log.d(TAG, "CLIPBOARD_CHANGED broadcast received!")
                        mainHandler.post {
                            try {
                                Log.d(TAG, "Reloading clipboard settings from broadcast...")
                                reloadClipboardSettings()
                                Log.d(TAG, "Clipboard settings reloaded successfully!")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reloading clipboard settings from broadcast", e)
                            }
                        }
                    }
                    "com.kvive.keyboard.DICTIONARY_CHANGED" -> {
                        Log.d(TAG, "DICTIONARY_CHANGED broadcast received!")
                        mainHandler.post {
                            try {
                                Log.d(TAG, "Reloading dictionary settings from broadcast...")
                                reloadDictionarySettings()
                                
                                // ✅ Reload dictionary entries and refresh LanguageResources
                                if (::dictionaryManager.isInitialized) {
                                    dictionaryManager.reloadFromFlutterPrefs()
                                    Log.d(TAG, "✅ Dictionary entries reloaded from Flutter!")
                                    
                                    // Refresh LanguageResources with updated user data (NEW UNIFIED APPROACH)
                                    refreshLanguageResourcesAsync()
                                } else {
                                    Log.w(TAG, "⚠️ DictionaryManager not initialized yet")
                                }
                                
                                Log.d(TAG, "Dictionary settings reloaded successfully!")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reloading dictionary settings from broadcast", e)
                            }
                        }
                    }
                    "com.kvive.keyboard.EMOJI_SETTINGS_CHANGED" -> {
                        Log.d(TAG, "EMOJI_SETTINGS_CHANGED broadcast received!")
                        mainHandler.post {
                            try {
                                Log.d(TAG, "Reloading emoji settings from broadcast...")
                                reloadEmojiSettings()
                                Log.d(TAG, "Emoji settings reloaded successfully!")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reloading emoji settings from broadcast", e)
                            }
                        }
                    }
                    "com.kvive.keyboard.CLEAR_USER_WORDS" -> {
                        Log.d(TAG, "CLEAR_USER_WORDS broadcast received!")
                        mainHandler.post {
                            try {
                                Log.d(TAG, "Clearing learned words...")
                                if (::userDictionaryManager.isInitialized) {
                                    userDictionaryManager.clearAllWords()
                                    Log.d(TAG, "✅ Learned words cleared successfully!")
                                } else {
                                    Log.w(TAG, "⚠️ UserDictionaryManager not initialized")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error clearing learned words from broadcast", e)
                            }
                        }
                    }
                    "com.kvive.keyboard.LANGUAGE_CHANGED" -> {
                        val language = intent?.getStringExtra("language")
                        val multiEnabled = intent?.getBooleanExtra("multilingual_enabled", false)
                        Log.d(TAG, "LANGUAGE_CHANGED broadcast received! Language: $language, Multi: $multiEnabled")
                        mainHandler.post {
                            try {
                                // Reload language preferences from SharedPreferences
                                loadLanguagePreferences()
                                
                                // If we're in LETTERS mode, reload the keyboard layout
                                if (currentKeyboardMode == KeyboardMode.LETTERS) {
                                    switchKeyboardMode(KeyboardMode.LETTERS)
                                    Log.d(TAG, "✅ Keyboard layout reloaded after language change")
                                }
                                
                                if (language != null) {
                                    showLanguageToast(language)
                                }
                                Log.d(TAG, "✅ Language settings reloaded! Current: $currentLanguage, Enabled: $enabledLanguages")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Error reloading language settings", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcast receiver", e)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🚀 onCreate() started")
        
        // Start profiling onCreate
        com.kvive.keyboard.utils.StartupProfiler.startOperation("AIKeyboardService.onCreate")
        com.kvive.keyboard.utils.StartupProfiler.milestone("onCreate() start")
        
        // ✅ Set instance for UnifiedKeyboardView to access
        instance = this
        
        // 🔥 PERFORMANCE: Minimize main thread work - move to background
        // Initialize only critical components synchronously
        keyboardHeightManager = KeyboardHeightManager(this)
        settings = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
        settingsManager = SettingsManager(this)
        themeManager = ThemeManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        keyboardSettings = KeyboardSettings()
        
        Log.d(TAG, "✅ Critical components initialized in ${System.currentTimeMillis() - startTime}ms")
        
        // Register theme listener (lightweight)
        themeManager.addThemeChangeListener(object : ThemeManager.ThemeChangeListener {
            override fun onThemeChanged(theme: com.kvive.keyboard.themes.KeyboardThemeV2, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
                Log.d(TAG, "🎨 Theme changed: ${theme.name}, applying to keyboard...")
                mainHandler.post {
                    applyThemeImmediately()
                    unifiedPanelManager?.applyTheme(theme)
                }
            }
        })
        
        // Register broadcast receiver (lightweight)
        try {
            val filter = IntentFilter().apply {
                addAction("com.kvive.keyboard.SETTINGS_CHANGED")
                addAction("com.kvive.keyboard.THEME_CHANGED")
                addAction("com.kvive.keyboard.CLIPBOARD_CHANGED")
                addAction("com.kvive.keyboard.DICTIONARY_CHANGED")
                addAction("com.kvive.keyboard.EMOJI_SETTINGS_CHANGED")
                addAction("com.kvive.keyboard.CLEAR_USER_WORDS")
                addAction("com.kvive.keyboard.LANGUAGE_CHANGED")
            }
            com.kvive.keyboard.utils.BroadcastUtils.safeRegisterReceiver(this, settingsReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering broadcast receiver", e)
        }
        
        // ⚡ DEFER HEAVY INITIALIZATION - Run immediately after onCreate returns
        // This allows the UI to appear quickly while initialization continues
        mainHandler.post {
            initializeDeferredComponents()
        }
        
        Log.d(TAG, "✅ onCreate() completed in ${System.currentTimeMillis() - startTime}ms")
        
        // End profiling onCreate
        com.kvive.keyboard.utils.StartupProfiler.endOperation("AIKeyboardService.onCreate")
        com.kvive.keyboard.utils.StartupProfiler.milestone("onCreate() end")
    }
    
    /**
     * 🚀 PERFORMANCE OPTIMIZATION: Deferred initialization
     * Runs immediately after onCreate() returns, allowing UI to appear first
     */
    private fun initializeDeferredComponents() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "⏳ Starting deferred initialization...")
        com.kvive.keyboard.utils.StartupProfiler.startOperation("DeferredComponents")
        
        // Step 1: Load settings asynchronously (non-blocking)
        coroutineScope.launch(Dispatchers.IO) {
            com.kvive.keyboard.utils.StartupProfiler.startOperation("LoadSoundSettings")
            try {
                // Sound/vibration settings
                val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                val hasResetFlag = flutterPrefs.getBoolean("_sound_vibration_reset_v2", false)
                
                if (!hasResetFlag) {
                    flutterPrefs.edit().apply {
                        putBoolean("flutter.sound_enabled", false)
                        putBoolean("flutter.vibration_enabled", false)
                        putBoolean("flutter.key_press_sounds", false)
                        putBoolean("flutter.long_press_sounds", false)
                        putBoolean("flutter.repeated_action_sounds", false)
                        putBoolean("flutter.key_press_vibration", false)
                        putBoolean("flutter.long_press_vibration", false)
                        putBoolean("flutter.repeated_action_vibration", false)
                        putBoolean("_sound_vibration_reset_v2", true)
                        apply()
                    }
                }
                
                // Sound manager initialization
                withContext(Dispatchers.Main) {
                    val prefs = getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
                    val selectedSound = prefs.getString("selected_sound", null)
                    val initialSoundProfile = if (selectedSound != null) "custom" else "default"
                    val initialCustomSoundUri = selectedSound?.let { "sounds/$it" }
                    
                    KeyboardSoundManager.init(
                        context = this@AIKeyboardService,
                        initialProfile = initialSoundProfile,
                        initialCustomUri = initialCustomSoundUri
                    )
                    KeyboardSoundManager.isEnabled = false
                }
                
                Log.d(TAG, "✅ Sound settings loaded")
                com.kvive.keyboard.utils.StartupProfiler.endOperation("LoadSoundSettings")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading sound settings", e)
                com.kvive.keyboard.utils.StartupProfiler.endOperation("LoadSoundSettings")
            }
        }
        
        // Step 2: Initialize core components in background
        coroutineScope.launch(Dispatchers.IO) {
            com.kvive.keyboard.utils.StartupProfiler.startOperation("InitCoreComponents")
            try {
                initializeCoreComponents()
                Log.d(TAG, "✅ Core components initialized")
                com.kvive.keyboard.utils.StartupProfiler.endOperation("InitCoreComponents")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing core components", e)
                com.kvive.keyboard.utils.StartupProfiler.endOperation("InitCoreComponents")
            }
        }
        
        // Step 3: Load settings and apply them
        coroutineScope.launch(Dispatchers.IO) {
            com.kvive.keyboard.utils.StartupProfiler.startOperation("LoadAndApplySettings")
            try {
                val loadedSettings = settingsManager.loadAll()
                withContext(Dispatchers.Main) {
                    applyLoadedSettings(loadedSettings, logSuccess = true)
                    updateSuggestionControllerSettings()
                }
                Log.d(TAG, "✅ Settings loaded and applied")
                com.kvive.keyboard.utils.StartupProfiler.endOperation("LoadAndApplySettings")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading settings", e)
                com.kvive.keyboard.utils.StartupProfiler.endOperation("LoadAndApplySettings")
            }
        }
        
        // Step 4: Initialize multilingual components
        coroutineScope.launch(Dispatchers.IO) {
            com.kvive.keyboard.utils.StartupProfiler.startOperation("InitMultilingualComponents")
            try {
                loadLanguagePreferences()
                withContext(Dispatchers.Main) {
                    initializeMultilingualComponents()
                    initializeThemeChannel()
                }
                Log.d(TAG, "✅ Multilingual components initialized")
                com.kvive.keyboard.utils.StartupProfiler.endOperation("InitMultilingualComponents")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing multilingual components", e)
                com.kvive.keyboard.utils.StartupProfiler.endOperation("InitMultilingualComponents")
            }
        }
        
        // Step 5: Start settings polling and preload keymaps
        coroutineScope.launch(Dispatchers.IO) {
            com.kvive.keyboard.utils.StartupProfiler.startOperation("PreloadKeymaps")
            try {
                withContext(Dispatchers.Main) {
                    startSettingsPolling()
                }
                
                languageLayoutAdapter.preloadKeymaps(enabledLanguages)
                Log.d(TAG, "✅ Keymaps preloaded")
                com.kvive.keyboard.utils.StartupProfiler.endOperation("PreloadKeymaps")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to preload keymaps", e)
                com.kvive.keyboard.utils.StartupProfiler.endOperation("PreloadKeymaps")
            }
        }
        
        // Step 6: Initialize async components (heaviest work)
        mainHandler.postDelayed({
            initializeAsyncComponents()
        }, 100) // Small delay to ensure UI is fully rendered
        
        Log.d(TAG, "✅ Deferred initialization kicked off in ${System.currentTimeMillis() - startTime}ms")
        com.kvive.keyboard.utils.StartupProfiler.endOperation("DeferredComponents")
        
        // Print profiling summary after 5 seconds (when all initialization should be done)
        mainHandler.postDelayed({
            com.kvive.keyboard.utils.StartupProfiler.printSummary()
        }, 5000)
    }
    
    /**
     * Heavy initialization staged off the main thread to avoid onCreate jank.
     * Runs after the keyboard UI is ready.
     */
    private fun initializeAsyncComponents() = coroutineScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "⏳ Starting async initialization pipeline")

            // Prompt loading and receiver registration (reads large assets)
            try {
                PromptManager.init(this@AIKeyboardService)
                withContext(Dispatchers.Main) {
                    val intentFilter = IntentFilter("com.kvive.keyboard.PROMPTS_UPDATED")
                    com.kvive.keyboard.utils.BroadcastUtils.safeRegisterReceiver(
                        this@AIKeyboardService,
                        promptReceiver,
                        intentFilter
                    )
                }
                Log.d(TAG, "✅ PromptManager initialized asynchronously")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing PromptManager asynchronously", e)
            }

            // OpenAI and AI service warmup
            try {
                withContext(Dispatchers.Default) {
                    val config = OpenAIConfig.getInstance(this@AIKeyboardService)
                    val prefs = getSharedPreferences("openai_secure_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("backend_proxy_enabled", true).apply()
                    config.setAIFeaturesEnabled(true)
                }
                withContext(Dispatchers.Default) {
                    advancedAIService = AdvancedAIService(this@AIKeyboardService)
                    advancedAIService.preloadWarmup()
                }
                withContext(Dispatchers.Default) {
                    unifiedAIService = UnifiedAIService(this@AIKeyboardService)
                }
                withContext(Dispatchers.Main) {
                    initializeAIBridge()
                    checkAIReadiness()
                }
                Log.d(TAG, "🧠 AI services initialized off main thread")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error initializing AI services asynchronously", e)
            }

            // Dictionary + clipboard initialization and cloud sync moved off main
            withContext(Dispatchers.Default) {
                if (::clipboardHistoryManager.isInitialized) {
                    clipboardHistoryManager.initialize()
                }
                if (::dictionaryManager.isInitialized) {
                    dictionaryManager.initialize()
                }
            }
            withContext(Dispatchers.Main) {
                if (::clipboardHistoryManager.isInitialized) {
                    clipboardHistoryManager.addListener(clipboardHistoryListener)
                    reloadClipboardSettings()
                }
                if (::dictionaryManager.isInitialized) {
                    dictionaryManager.addListener(dictionaryListener)
                }
            }

            if (::userDictionaryManager.isInitialized && ::dictionaryManager.isInitialized) {
                if (firebaseBlockedInIme) {
                    Log.d(TAG, "☁️ Cloud sync skipped in IME process (Firebase blocked)")
                } else {
                    val currentLang = dictionaryManager.getCurrentLanguage()
                    try {
                        userDictionaryManager.syncFromCloud(currentLang)
                        dictionaryManager.setCloudSyncCallback { shortcuts ->
                            userDictionaryManager.syncShortcutsToCloud(shortcuts, currentLang)
                            Log.d(TAG, "☁️ Synced ${shortcuts.size} shortcuts for $currentLang")
                        }
                        userDictionaryManager.loadShortcutsFromCloud(currentLang) { cloudShortcuts ->
                            dictionaryManager.importFromCloud(cloudShortcuts)
                            Log.d(TAG, "✅ Imported ${cloudShortcuts.size} shortcuts from cloud for $currentLang")
                        }
                        withContext(Dispatchers.Main) {
                            setupPeriodicSync()
                        }
                        Log.d(TAG, "✅ Custom shortcuts cloud sync enabled asynchronously for $currentLang")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error syncing user dictionary from cloud", e)
                    }
                }
            }

            // Kick off language activation and transliteration outside onCreate
            try {
                val initialLanguage = currentLanguage
                activateLanguage(initialLanguage)

                val enabled = languageManager.getEnabledLanguages()
                val otherLanguages = enabled.filter { it != initialLanguage }
                if (otherLanguages.isNotEmpty()) {
                    otherLanguages.forEach { lang ->
                        launch {
                            try {
                                activateLanguage(lang)
                                Log.d(TAG, "✅ Background activated language: $lang")
                            } catch (langError: Exception) {
                                Log.w(TAG, "⚠️ Failed background activation for $lang", langError)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during async language activation", e)
            }

            withContext(Dispatchers.Main) {
                initializeTransliteration()
            }

            if (::autocorrectEngine.isInitialized) {
                val lang = currentLanguage
                autocorrectEngine.preloadLanguages(listOf(lang))
                Log.d(TAG, "📊 Autocorrect preload triggered asynchronously for $lang")
            }

            Log.d(TAG, "✅ Heavy async components initialized safely")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in async init pipeline", e)
        }
    }
    
    /**
     * Override to explicitly control keyboard height including number row
     * Ensures keyboard grows upward when number row is active, regardless of navigation bar
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        
        outInsets?.let { insets ->
            // Get the main input view
            val inputView = mainKeyboardLayout ?: return
            
            // Read the current number row setting to ensure consistency
            val numberRowEnabled = getNumberRowEnabled()
            
            // Calculate the desired keyboard height including number row if active
            val desiredHeight = keyboardHeightManager.calculateKeyboardHeight(
                includeToolbar = true,
                includeSuggestions = true
            )
            
            // ✅ FIX: Always set all insets to 0 to prevent white space gaps
            // The keyboard view uses weight layout param to fill available space
            insets.contentTopInsets = 0
            insets.visibleTopInsets = 0
            
            // ✅ FIX: Remove bottom gap by ensuring touch region extends to bottom edge
            // Set input view height to exactly fill the available space 
            val navBarHeight = getNavigationBarHeight()
            
            // Make sure keyboard view fills entire bottom area (no gaps)
            inputView.layoutParams = inputView.layoutParams.apply {
                height = desiredHeight
            }
            if (inputView.paddingBottom != navBarHeight) {
                inputView.setPadding(
                    inputView.paddingLeft,
                    inputView.paddingTop,
                    inputView.paddingRight,
                    navBarHeight
                )
            }
            inputView.clipToPadding = false
            
            // Ensure touch region covers the entire keyboard
            insets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            insets.touchableRegion.setEmpty()
            insets.touchableRegion.op(
                0, 
                0,  // Start from top
                inputView.width, 
                inputView.height,
                android.graphics.Region.Op.UNION
            )
            
        }
    }
    
    /**
     * Initialize core components FIRST to prevent UninitializedPropertyAccessException
     * This must be called before any async operations that might access these components
     */
    private fun initializeCoreComponents() {
        try {
            Log.d(TAG, "🔧 Initializing core components...")
            
            // Initialize language manager FIRST (needed for language preferences)
            languageManager = LanguageManager(this)
            Log.d(TAG, "✅ LanguageManager initialized")
            
            // 🔍 AUDIT: Add language change listener to sync layout/dictionary
            languageManager.addLanguageChangeListener(object : LanguageManager.LanguageChangeListener {
                override fun onLanguageChanged(oldLanguage: String, newLanguage: String) {
                    Log.d("LangSwitch", "🌐 Switching from $oldLanguage → $newLanguage")
                    
                    // Update current language tracking
                    currentLanguage = newLanguage
                    refreshSwipeCapability("languageSwitch:$newLanguage")
                    
                            // Use unified activation function for language switching
                            coroutineScope.launch {
                                try {
                                    // Switch user managers first
                                    if (::dictionaryManager.isInitialized) {
                                        dictionaryManager.switchLanguage(newLanguage)
                                    }
                                    
                                    if (::userDictionaryManager.isInitialized) {
                                        userDictionaryManager.switchLanguage(newLanguage)
                                    }
                                    
                                    // Use unified activation function
                                    activateLanguage(newLanguage)
                                    
                                } catch (e: Exception) {
                                    Log.e("LangSwitch", "❌ Error in unified language switching", e)
                                }
                            }
                    
                    Log.d("LangSwitch", "✅ Language switch initiated: $oldLanguage → $newLanguage")
                }
                
                override fun onEnabledLanguagesChanged(enabledLanguages: Set<String>) {
                    Log.d("LangSwitch", "🌐 Enabled languages updated: $enabledLanguages")
                }
            })
            
            // Initialize clipboard history manager (needed for SuggestionsPipeline)
        clipboardHistoryManager = ClipboardHistoryManager(this)
        Log.d(TAG, "✅ ClipboardHistoryManager initialized")
            
            // Initialize dictionary manager (for shortcuts/custom mappings)
            dictionaryManager = DictionaryManager(this)
            Log.d(TAG, "✅ DictionaryManager initialized")
            
            // ✅ Load dictionary enabled state from FlutterSharedPreferences
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            dictionaryEnabled = flutterPrefs.getBoolean("flutter.dictionary_enabled", true)
            dictionaryManager.setEnabled(dictionaryEnabled)
            Log.d(TAG, "✅ Dictionary enabled state loaded: $dictionaryEnabled")
            
            // Initialize user dictionary manager
            userDictionaryManager = UserDictionaryManager(this)
            Log.d(TAG, "✅ UserDictionaryManager initialized")
            
            // Initialize multilingual dictionary (NEW UNIFIED ARCHITECTURE)
            multilingualDictionary = MultilingualDictionaryImpl(this)
            Log.d(TAG, "✅ MultilingualDictionary initialized")
            
            // 2️⃣ Set up enhanced autocorrect reload hook
            multilingualDictionary.setOnLanguageReadyListener { lang ->
                Log.d("AIKeyboardService", "🔁 Autocorrect engine reloaded for $lang")
                try {
                    val resources = multilingualDictionary.get(lang)
                    if (resources != null && ::autocorrectEngine.isInitialized) {
                        // 🔥 FIX 3.3 - Only set language if it actually changed
                        if (autocorrectEngine.currentLanguage != lang) {
                            autocorrectEngine.setLanguage(lang, resources)
                            Log.d(TAG, "✅ UnifiedAutocorrectEngine reloaded for language: $lang [words=${resources.words.size}, bigrams=${resources.bigrams.size}, trigrams=${resources.trigrams.size}]")
                            Log.d(TAG, "🔥 AUTOCORRECT ENGINE NOW READY - Next-word prediction should work!")
                        } else {
                            Log.d(TAG, "⚡ Language already loaded: $lang - skipping reload")
                        }
                        
                        // Verify the engine is actually ready
                        val isReady = autocorrectEngine.hasLanguage(lang)
                        Log.d(TAG, "🔍 Post-reload verification - Engine ready: $isReady")
                        
                    } else {
                        Log.e(TAG, "❌ CRITICAL: Resources null or engine not initialized - lang=$lang, resources=${resources != null}, engineReady=${::autocorrectEngine.isInitialized}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to reload engine for language: $lang", e)
                }
            }
            Log.d(TAG, "✅ Enhanced language readiness callback registered")
            
            // Wire managers to feed into MultilingualDictionary
            multilingualDictionary.setUserDictionaryManager(userDictionaryManager)
            multilingualDictionary.setDictionaryManager(dictionaryManager)
            Log.d(TAG, "✅ Dictionary managers wired to MultilingualDictionary")
            
            // Wire observers to refresh LanguageResources when user data changes
            userDictionaryManager.setChangeObserver {
                refreshLanguageResourcesAsync()
            }
            dictionaryManager.setChangeObserver {
                refreshLanguageResourcesAsync()
            }
            Log.d(TAG, "✅ Change observers wired for LanguageResources refresh")

            // Initialize unified autocorrect engine (NEW UNIFIED API)
            autocorrectEngine = UnifiedAutocorrectEngine(
                context = this,
                multilingualDictionary = multilingualDictionary,
                transliterationEngine = transliterationEngine,
                userDictionaryManager = userDictionaryManager
            )
            Log.d(TAG, "✅ UnifiedAutocorrectEngine initialized")
            
            // Attach suggestion callback for real-time updates
            autocorrectEngine.attachSuggestionCallback { suggestions ->
                updateSuggestionUI(suggestions)
            }
            Log.d(TAG, "✅ Suggestion callback attached to autocorrect engine")

            // ✅ Initialize UnifiedSuggestionController (replaces SuggestionsPipeline)
            unifiedSuggestionController = UnifiedSuggestionController(
                context = this,
                unifiedAutocorrectEngine = autocorrectEngine,
                clipboardHistoryManager = clipboardHistoryManager,
                languageManager = languageManager
            )
            Log.d(TAG, "✅ UnifiedSuggestionController initialized with all engines")
            Log.d(TAG, "✅ SuggestionsPipeline dependencies configured")

            // Preload all enabled languages into RAM (dictionaries + engine) at boot
            coroutineScope.launch {
                val enabled = languageManager.getEnabledLanguages().toList()
                enabled.forEach { lang ->
                    try {
                        multilingualDictionary.preload(lang)
                        multilingualDictionary.get(lang)?.let { resources ->
                            // 🔥 FIX 3.3 - Only set language if it actually changed
                            if (autocorrectEngine.currentLanguage != lang) {
                                autocorrectEngine.setLanguage(lang, resources)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to warm language $lang", e)
                    }
                }
                Log.d(TAG, "🔥 Warm preload complete for languages: $enabled")
            }
            
            // Wire language activation callback for automatic engine setup after download
            multilingualDictionary.setOnLanguageActivatedListener { lang ->
                Log.d(TAG, "🎯 Language activation callback received for $lang")
                coroutineScope.launch {
                    try {
                        onLanguageFullyActivated(lang)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error in language activation callback for $lang", e)
                    }
                }
            }
            
            // Verify loading status asynchronously (don't block onCreate)
            coroutineScope.launch {
                delay(1000) // Wait for async loads to complete
                
                if (autocorrectEngine.hasLanguage(currentLanguage)) {
                    Log.i(TAG, "✅ UnifiedAutocorrectEngine ready for language: $currentLanguage")
                } else {
                    Log.w(TAG, "⚠️ UnifiedAutocorrectEngine not ready for language: $currentLanguage")
                }
            }
            
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing core components", e)
            throw e
        }
    }
    
    /**
     * STEP 2: AI readiness check with unified state management
     */
    private fun checkAIReadiness() {
        // Check if AI bridge is actually ready (not just initialized)
        if (::unifiedAIService.isInitialized && unifiedAIService.isReady()) {
            isAIReady = true
            Log.d(TAG, "🟢 AI service confirmed ready")
            
            // Prewarm AI models in background
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (::advancedAIService.isInitialized) {
                        // Preload AI engines if method exists
                        Log.d(TAG, "🔥 Preloading AI engines in background...")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AI preload failed: ${e.message}")
                }
            }
        } else if (::advancedAIService.isInitialized) {
            // Preload advanced AI service asynchronously with warm-up wait
            coroutineScope.launch(Dispatchers.Default) {
                try {
                    Log.d(TAG, "🧠 Waiting for AdvancedAIService warm-up...")
                    
                    // Wait for AdvancedAIService to fully initialize
                    var initialized = false
                    repeat(5) { attempt ->
                        if (advancedAIService.isInitialized()) {
                            initialized = true
                            
                            // Preload/warm up the AI service
                            advancedAIService.preloadWarmup()
                            
                            withContext(Dispatchers.Main) {
                                // UnifiedAI Service already initialized
                                Log.d(TAG, "🧠 AI Bridge linked successfully on attempt ${attempt + 1}")
                                isAIReady = true
                                Log.i(TAG, "🟢 AdvancedAIService ready before first key input")
                            }
                            return@launch
                        }
                        delay(400L)
                    }
                    
                    // Timeout - proceed with fallback
                    if (!initialized) {
                        Log.w(TAG, "⚠️ AdvancedAIService warm-up timeout, proceeding with fallback")
                        withContext(Dispatchers.Main) {
                            isAIReady = false
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AI preload failed, using fallback: ${e.message}")
                    withContext(Dispatchers.Main) {
                        isAIReady = false
                    }
                }
            }
        } else {
            // Retry after delay, but don't block autocorrect - it works independently
            Log.d(TAG, "⏳ AI services still initializing... (autocorrect available)")
            mainHandler.postDelayed({ checkAIReadiness() }, 500)
        }
    }
    
    private fun initializeAIBridge() {
        if (!::autocorrectEngine.isInitialized) return
        
        try {
            // Initialize the enhanced AI bridge with context
            // AIServiceBridge removed - UnifiedAI Service handles this
            Log.d(TAG, "AI Bridge initialized successfully")
            
            // Check AI readiness periodically - retry up to 5 times only if dictionaries are not ready
            coroutineScope.launch {
                delay(1000)
                var retryCount = 0
                val currentLang = currentLanguage
                
                while (!isAIReady && retryCount < 5) {
                    // Check if dictionaries are ready first
                    val dictionariesReady = autocorrectEngine.hasLanguage(currentLang)
                    
                    if (!dictionariesReady) {
                        Log.d(TAG, "🔵 [AI] Waiting for dictionaries to load for $currentLang, retry $retryCount/5")
                    } else {
                        isAIReady = unifiedAIService.isReady()
                        if (isAIReady) {
                            Log.i(TAG, "✅ AI initialized for $currentLang with dictionary + bigrams")
                            withContext(Dispatchers.Main) {
                              //  Toast.makeText(this@AIKeyboardService, "🤖 AI Keyboard Ready", Toast.LENGTH_SHORT).show()
                            }
                            break
                        } else {
                            Log.d(TAG, "🔵 [AI] AI service not ready yet, retry $retryCount/5")
                        }
                    }
                    
                    delay(2000) // Retry after 2 seconds
                    retryCount++
                }
                
                if (!isAIReady) {
                    Log.w(TAG, "⚠️ AI unavailable, running in enhanced basic mode")
                    withContext(Dispatchers.Main) {
                     //   Toast.makeText(this@AIKeyboardService, "📝 Keyboard Ready (Basic Mode)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI bridge", e)
        }
    }
    
    /**
     * Apply keyboard configuration from CleverType-standardized preferences
     * Called on startup and when notifyConfigChange is received
     */
    private fun applyConfig() {
        try {
            val p = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            
            // ✅ Determine orientation early for orientation-specific settings
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            
            // Read all configuration values with CleverType keys
            val numberRow = p.getBoolean("flutter.keyboard.numberRow", false)
            val hintedNumberRow = p.getBoolean("flutter.keyboard.hintedNumberRow", false) && !numberRow
            val hintedSymbols = p.getBoolean("flutter.keyboard.hintedSymbols", false)
            val utilityAction = p.getStringCompat("flutter.keyboard.utilityKeyAction", "emoji")
            // ✅ FIX: Check both utility action AND show_utility_key setting
            var showUtilityKeyFromSettings = p.getBooleanCompat("flutter.keyboard_settings.show_utility_key", true)
            if (!p.contains("flutter.keyboard_settings.show_utility_key")) {
                showUtilityKeyFromSettings = p.getBooleanCompat(
                    "flutter.flutter.keyboard_settings.show_utility_key",
                    showUtilityKeyFromSettings
                )
            }
            val showUtilityKey = showUtilityKeyFromSettings && utilityAction != "none"
            Log.d(TAG, "🔧 Utility key settings: showUtilityKeyFromSettings=$showUtilityKeyFromSettings, action=$utilityAction, final=$showUtilityKey")
            val showLangOnSpace = p.getBoolean("flutter.keyboard.showLanguageOnSpace", true)
            runCatching {
                val rawPortrait = p.all["flutter.keyboard.fontScalePortrait"]
                val rawLandscape = p.all["flutter.keyboard.fontScaleLandscape"]
                val rawPortraitUi = p.all["flutter.keyboard_settings.portrait_font_size"]
                val rawLandscapeUi = p.all["flutter.keyboard_settings.landscape_font_size"]
                Log.d(TAG, "📦 Raw font prefs → portrait=$rawPortrait landscape=$rawLandscape uiPortrait=$rawPortraitUi uiLandscape=$rawLandscapeUi")
            }
            // ✅ FIX: Read font scale from the correct keys
            val fontScaleP = p.getFloatCompat("flutter.keyboard.fontScalePortrait", 3.0f)
            val fontScaleL = p.getFloatCompat("flutter.keyboard.fontScaleLandscape", 1.0f)
            Log.d(TAG, "📝 Font scale settings: portrait=$fontScaleP, landscape=$fontScaleL")
            val borderless = p.getBoolean("flutter.keyboard.borderlessKeys", false)
            val ohEnabled = p.getBoolean("flutter.keyboard.oneHanded.enabled", false)
            val ohSide = p.getString("flutter.keyboard.oneHanded.side", "right") ?: "right"
            val ohWidth = p.getFloatCompat("flutter.keyboard.oneHanded.widthPct", 0.87f)
            val landscapeFull = p.getBoolean("flutter.keyboard.landscapeFullscreen", true)
            // ✅ FIX: Read orientation-specific height scaling
            var scaleYPortrait = p.getFloatCompat("flutter.keyboard.scaleYPortrait", 1.0f)
            var scaleYLandscape = p.getFloatCompat("flutter.keyboard.scaleYLandscape", 1.0f)
            val hasPortraitScale = p.contains("flutter.keyboard.scaleYPortrait")
            val hasLandscapeScale = p.contains("flutter.keyboard.scaleYLandscape")
            if (!hasPortraitScale || !hasLandscapeScale) {
                val legacyScalePrefixed = p.getFloatCompat("flutter.keyboard.scaleY", 1.0f)
                val legacyScale = if (p.contains("flutter.keyboard.scaleY")) {
                    legacyScalePrefixed
                } else {
                    p.getFloatCompat("keyboard.scaleY", legacyScalePrefixed)
                }
                if (!hasPortraitScale) {
                    scaleYPortrait = legacyScale
                }
                if (!hasLandscapeScale) {
                    scaleYLandscape = legacyScale
                }
            }
            val scaleX = 1.0f // Always keep width at 100%
            val scaleY = if (isLandscape) scaleYLandscape else scaleYPortrait
            Log.d(TAG, "📏 Keyboard scale settings: orientation=${if (isLandscape) "LANDSCAPE" else "PORTRAIT"}, scaleY=$scaleY (portrait=$scaleYPortrait, landscape=$scaleYLandscape)")
            val spaceVdp = p.getIntCompat("flutter.keyboard.keySpacingVdp", 7)
            val spaceHdp = p.getIntCompat("flutter.keyboard.keySpacingHdp", 3)
            val edgePaddingDp = max(8, spaceHdp * 2)
            val bottomP = p.getIntCompat("flutter.keyboard.bottomOffsetPortraitDp", 1)
            val bottomL = p.getIntCompat("flutter.keyboard.bottomOffsetLandscapeDp", 2)
            val popupPreview = false // ✅ DISABLED: Key preview popup permanently removed from project
/*
            val popupPreview = when {
                p.contains("flutter.keyboard_settings.popup_visibility") ->
                    p.getBoolean("flutter.keyboard_settings.popup_visibility", false)
                p.contains("flutter.keyboard.popupPreview") ->
                    p.getBoolean("flutter.keyboard.popupPreview", false)
                else -> false
            }
*/
            
            // ✅ FIX: Update keyPreviewEnabled to sync with Flutter popup_visibility setting
            keyPreviewEnabled = popupPreview
            
            val longPressDelay = p.getIntCompat("flutter.keyboard.longPressDelayMs", 200)
            var instantLongPressSelect = p.getBooleanCompat("flutter.keyboard.instantLongPressSelectFirst", true)
            if (!p.contains("flutter.keyboard.instantLongPressSelectFirst")) {
                instantLongPressSelect = p.getBooleanCompat("keyboard.instantLongPressSelectFirst", instantLongPressSelect)
            }
            
            // ✅ FIX: Load sound settings only when they change (prevents log spam)
            loadSoundSettingsIfNeeded()
            
            // Load vibration settings (still needed for vibrationEnabled variable)
            val hapticIntensity = p.getIntCompat("flutter.haptic_intensity", 2)
            val vibrationEnabledPref = p.getBoolean("flutter.vibration_enabled", false)  // ✅ Changed default to FALSE
            this.vibrationEnabled = vibrationEnabledPref
            
            if (vibrationEnabledPref) {
                keyPressVibration = p.getBoolean("flutter.key_press_vibration", false)  // ✅ Changed default to FALSE
                longPressVibration = p.getBoolean("flutter.long_press_vibration", false)  // ✅ Changed default to FALSE
                repeatedActionVibration = p.getBoolean("flutter.repeated_action_vibration", false)  // ✅ Changed default to FALSE
            } else {
                keyPressVibration = false
                longPressVibration = false
                repeatedActionVibration = false
            }
            
            // Log vibration settings only when they change (optional - can be removed if still spamming)
            // Log.d(TAG, "📳 Vibration settings: enabled=$vibrationEnabledPref, keyPress=$keyPressVibration, longPress=$longPressVibration, repeated=$repeatedActionVibration")

            val gesturePrefs = readGestureSettings(p)
            applyGestureSettings(gesturePrefs)
            
            // Check if number row setting changed - need to reload keyboard layout
            val numberRowChanged = showNumberRow != numberRow
            if (numberRowChanged) {
                showNumberRow = numberRow
                Log.d(TAG, "✅ Number row changed to $numberRow, keyboard height updated")
            }

            keyboardHeightManager.setNumberRowEnabled(numberRow)
            if (showUtilityKeyEnabled != showUtilityKey) {
                showUtilityKeyEnabled = showUtilityKey
                languageLayoutAdapter.setShowUtilityKey(showUtilityKey)
                if (::unifiedController.isInitialized) {
                    val targetMode = currentKeyboardMode.toLayoutMode()
                    val enableNumberRow = showNumberRow && currentKeyboardMode == KeyboardMode.LETTERS
                    unifiedController.buildAndRender(currentLanguage, targetMode, enableNumberRow)
                } else {
                    try {
                        switchKeyboardMode(currentKeyboardMode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error rebuilding keyboard after utility key change", e)
                    }
                }
            } else {
                languageLayoutAdapter.setShowUtilityKey(showUtilityKey)
            }

            unifiedKeyboardView?.let { view ->
                view.setNumberRowEnabled(numberRow)
                view.setInstantLongPressSelectFirst(instantLongPressSelect)
                view.recalcHeight()
            }
            
            // Determine font scale and bottom offset based on orientation (isLandscape already defined above)
            val fontScale = if (isLandscape) fontScaleL else fontScaleP
            // ✅ FIX: NO bottom padding - keys now auto-fill available space
            val hasNavBar = keyboardHeightManager.hasNavigationBar()
            val navBarHeight = if (hasNavBar) keyboardHeightManager.getNavigationBarHeight() else 0
            val bottom = 0  // Always 0 - keys will auto-size to fill space
            Log.d(TAG, "⚙️ Bottom padding: hasNavBar=$hasNavBar, navBarHeight=$navBarHeight, bottomPadding=$bottom (keys auto-fill)")
            
            // Reload keyboard if number row changed (requires different XML layout)
            if (numberRowChanged) {
                try {
                    // Reload current keyboard mode with new settings
                    val currentMode = when (currentKeyboard) {
                        KEYBOARD_LETTERS -> KeyboardMode.LETTERS
                        KEYBOARD_NUMBERS -> KeyboardMode.NUMBERS
                        KEYBOARD_SYMBOLS -> KeyboardMode.SYMBOLS
                        else -> KeyboardMode.LETTERS
                    }
                    switchKeyboardMode(currentMode)
                    Log.d(TAG, "✓ Keyboard reloaded with numberRow=$showNumberRow")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠ Error reloading keyboard", e)
                }
            }
            
        // Apply to keyboard view if available
        keyboardView?.let { view ->
            if (view is SwipeKeyboardView) {
                // CRITICAL: Apply ALL settings to the view in proper order
                view.setLabelScale(fontScale)
                view.setBorderless(borderless)
                view.setHintedNumberRow(hintedNumberRow)
                view.setHintedSymbols(hintedSymbols)
                view.setShowLanguageOnSpace(showLangOnSpace)
                view.setCurrentLanguage(currentLanguage.uppercase())
                view.setPreviewEnabled(popupPreview)
                
                // One-handed mode with Gboard-style behavior
                view.setOneHandedMode(ohEnabled, ohSide, ohWidth)
                
                // Spacing and sizing
                view.setKeySpacing(spaceVdp, spaceHdp)
                view.scaleX = scaleX
                view.scaleY = scaleY
                
                // Interaction settings
                view.setLongPressDelay(longPressDelay)
                view.setSoundEnabled(soundEnabled, soundIntensity)
                view.setHapticIntensity(hapticIntensity)
                
                // CRITICAL: Force complete redraw and layout recalculation
                view.invalidateAllKeys()
                view.invalidate()
                view.requestLayout()
                
                Log.d(TAG, "✓ SwipeKeyboardView settings applied: " +
                    "fontScale=$fontScale, borderless=$borderless, showLang=$showLangOnSpace, " +
                    "preview=$popupPreview, oneHanded=$ohEnabled@$ohSide(${(ohWidth * 100).toInt()}%), " +
                    "spacing=$spaceVdp/$spaceHdp, scale=$scaleX×$scaleY, longPress=${longPressDelay}ms, " +
                    "sound=$soundEnabled@$soundIntensity, haptic=$hapticIntensity")
            }
        }

        unifiedKeyboardView?.let { view ->
            view.setLabelScale(fontScale)
            view.setBorderless(borderless)
            view.setHintedNumberRow(hintedNumberRow)
            view.setHintedSymbols(hintedSymbols)
            view.setShowLanguageOnSpace(showLangOnSpace)
            view.setOneHandedMode(ohEnabled, ohSide, ohWidth)
            view.setNumberRowEnabled(showNumberRow)
            view.setKeySpacing(spaceVdp, spaceHdp)
            view.setEdgePadding(edgePaddingDp)
            view.setLongPressDelay(longPressDelay)
            view.scaleX = scaleX
            view.scaleY = scaleY
            view.invalidate()
            view.requestLayout()
            Log.d(TAG, "✓ UnifiedKeyboardView settings applied: " +
                "fontScale=$fontScale, borderless=$borderless, showLang=$showLangOnSpace, " +
                "spacing=$spaceVdp/$spaceHdp, edgePadding=$edgePaddingDp, scale=$scaleX×$scaleY, longPress=${longPressDelay}ms")
        }
        
        // Apply bottom offset to container
        keyboardContainer?.setPadding(
            keyboardContainer?.paddingLeft ?: 0,
            keyboardContainer?.paddingTop ?: 0,
            keyboardContainer?.paddingRight ?: 0,
            bottom
        )
        configureFeedbackModules()
        } catch (e: Exception) {
            Log.e(TAG, "⚠ Error applying config", e)
        }
    }

    private fun parseGestureAction(code: String?, default: GestureAction): GestureAction {
        return GestureAction.fromCode(code) ?: default
    }

    private fun readGestureSettings(p: SharedPreferences): GestureSettings {
        val glideTyping = p.getBooleanCompat("flutter.gestures.glide_typing", true)
        val showTrailPref = p.getBooleanCompat("flutter.gestures.show_glide_trail", true)
        val effectiveShowTrail = glideTyping && showTrailPref
        val fadeMs = if (effectiveShowTrail) {
            p.getFloatCompat("flutter.gestures.glide_trail_fade_time", 200f).toInt().coerceAtLeast(0)
        } else 0
        val alwaysDeleteWord = p.getBooleanCompat("flutter.gestures.always_delete_word", true)
        val velocityThreshold = p.getFloatCompat("flutter.gestures.swipe_velocity_threshold", 1900f)
        val distanceThreshold = p.getFloatCompat("flutter.gestures.swipe_distance_threshold", 20f)

        val swipeUp = parseGestureAction(p.getString("flutter.gestures.swipe_up_action", GestureAction.SHIFT.code), GestureAction.SHIFT)
        val swipeDown = parseGestureAction(p.getString("flutter.gestures.swipe_down_action", GestureAction.HIDE_KEYBOARD.code), GestureAction.HIDE_KEYBOARD)
        val swipeLeft = parseGestureAction(p.getString("flutter.gestures.swipe_left_action", GestureAction.DELETE_CHARACTER_BEFORE_CURSOR.code), GestureAction.DELETE_CHARACTER_BEFORE_CURSOR)
        val swipeRight = parseGestureAction(p.getString("flutter.gestures.swipe_right_action", GestureAction.INSERT_SPACE.code), GestureAction.INSERT_SPACE)

        val spaceLongPress = parseGestureAction(p.getString("flutter.gestures.space_long_press_action", GestureAction.SHOW_INPUT_METHOD_PICKER.code), GestureAction.SHOW_INPUT_METHOD_PICKER)
        val spaceSwipeDown = parseGestureAction(p.getString("flutter.gestures.space_swipe_down_action", GestureAction.NONE.code), GestureAction.NONE)
        val spaceSwipeLeft = parseGestureAction(p.getString("flutter.gestures.space_swipe_left_action", GestureAction.MOVE_CURSOR_LEFT.code), GestureAction.MOVE_CURSOR_LEFT)
        val spaceSwipeRight = parseGestureAction(p.getString("flutter.gestures.space_swipe_right_action", GestureAction.MOVE_CURSOR_RIGHT.code), GestureAction.MOVE_CURSOR_RIGHT)

        val deleteSwipeLeft = parseGestureAction(p.getString("flutter.gestures.delete_swipe_left_action", GestureAction.DELETE_WORD_BEFORE_CURSOR.code), GestureAction.DELETE_WORD_BEFORE_CURSOR)
        val deleteLongPress = parseGestureAction(p.getString("flutter.gestures.delete_long_press_action", GestureAction.DELETE_CHARACTER_BEFORE_CURSOR.code), GestureAction.DELETE_CHARACTER_BEFORE_CURSOR)

        return GestureSettings(
            glideTyping = glideTyping,
            showGlideTrail = effectiveShowTrail,
            glideTrailFadeMs = fadeMs,
            alwaysDeleteWord = alwaysDeleteWord,
            swipeVelocityThreshold = velocityThreshold,
            swipeDistanceThreshold = distanceThreshold,
            swipeUpAction = swipeUp,
            swipeDownAction = swipeDown,
            swipeLeftAction = swipeLeft,
            swipeRightAction = swipeRight,
            spaceLongPressAction = spaceLongPress,
            spaceSwipeDownAction = spaceSwipeDown,
            spaceSwipeLeftAction = spaceSwipeLeft,
            spaceSwipeRightAction = spaceSwipeRight,
            deleteSwipeLeftAction = deleteSwipeLeft,
            deleteLongPressAction = deleteLongPress
        )
    }

    private fun applyGestureSettings(settings: GestureSettings) {
        if (gestureSettings == settings) {
            return
        }

        gestureSettings = settings
        swipeTypingEnabled = settings.glideTyping

        unifiedKeyboardView?.updateGestureSettings(settings) { source ->
            handleGestureInvocation(source)
        }

        refreshSwipeCapability("gestureSettings")
        Log.d(TAG, "🎯 Gesture settings applied: glide=${settings.glideTyping}, trail=${settings.showGlideTrail}, swipeUp=${settings.swipeUpAction}")
    }

    private fun handleGestureInvocation(source: GestureSource) {
        val action = when (source) {
            GestureSource.GENERAL_SWIPE_UP -> gestureSettings.swipeUpAction
            GestureSource.GENERAL_SWIPE_DOWN -> gestureSettings.swipeDownAction
            GestureSource.GENERAL_SWIPE_LEFT -> gestureSettings.swipeLeftAction
            GestureSource.GENERAL_SWIPE_RIGHT -> gestureSettings.swipeRightAction
            GestureSource.SPACE_LONG_PRESS -> gestureSettings.spaceLongPressAction
            GestureSource.SPACE_SWIPE_DOWN -> gestureSettings.spaceSwipeDownAction
            GestureSource.SPACE_SWIPE_LEFT -> gestureSettings.spaceSwipeLeftAction
            GestureSource.SPACE_SWIPE_RIGHT -> gestureSettings.spaceSwipeRightAction
            GestureSource.DELETE_SWIPE_LEFT -> gestureSettings.deleteSwipeLeftAction
            GestureSource.DELETE_LONG_PRESS -> gestureSettings.deleteLongPressAction
        }

        if (action == GestureAction.NONE) {
            return
        }

        val handled = performGestureAction(action, source)
    }

    private fun performGestureAction(action: GestureAction, source: GestureSource): Boolean {
        val ic = currentInputConnection
        return when (action) {
            GestureAction.NONE -> false
            GestureAction.CYCLE_PREV_MODE -> {
                cycleKeyboardModeReverse()
                true
            }
            GestureAction.CYCLE_NEXT_MODE -> {
                cycleKeyboardMode()
                true
            }
            GestureAction.DELETE_WORD_BEFORE_CURSOR, GestureAction.DELETE_WORD -> deleteWordBeforeCursor()
            GestureAction.HIDE_KEYBOARD -> {
                handleClose()
                true
            }
            GestureAction.INSERT_SPACE -> {
                ic?.let {
                    commitSpaceWithDoublePeriod(it)
                    true
                } ?: false
            }
            GestureAction.MOVE_CURSOR_UP -> sendDirectionalKey(KeyEvent.KEYCODE_DPAD_UP)
            GestureAction.MOVE_CURSOR_DOWN -> sendDirectionalKey(KeyEvent.KEYCODE_DPAD_DOWN)
            GestureAction.MOVE_CURSOR_LEFT -> sendDirectionalKey(KeyEvent.KEYCODE_DPAD_LEFT)
            GestureAction.MOVE_CURSOR_RIGHT -> sendDirectionalKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            GestureAction.MOVE_CURSOR_LINE_START -> sendDirectionalKey(KeyEvent.KEYCODE_MOVE_HOME)
            GestureAction.MOVE_CURSOR_LINE_END -> sendDirectionalKey(KeyEvent.KEYCODE_MOVE_END)
            GestureAction.MOVE_CURSOR_PAGE_START -> moveCursorToPageEdge(start = true)
            GestureAction.MOVE_CURSOR_PAGE_END -> moveCursorToPageEdge(start = false)
            GestureAction.SHIFT -> {
                handleShift()
                true
            }
            GestureAction.REDO -> sendCtrlCombination(KeyEvent.KEYCODE_Z, withShift = true)
            GestureAction.UNDO -> sendCtrlCombination(KeyEvent.KEYCODE_Z, withShift = false)
            GestureAction.OPEN_CLIPBOARD -> {
                mainHandler.post { showFeaturePanel(PanelType.CLIPBOARD) }
                true
            }
           
            GestureAction.SWITCH_PREV_LANGUAGE -> {
                cycleLanguageReverse()
                true
            }
            GestureAction.SWITCH_NEXT_LANGUAGE -> {
                cycleLanguage()
                true
            }
            GestureAction.SHOW_INPUT_METHOD_PICKER -> showInputMethodPicker()
            GestureAction.TOGGLE_SMARTBAR -> toggleSmartbarVisibility()
            GestureAction.DELETE_CHARACTERS_PRECISELY, GestureAction.DELETE_CHARACTER_BEFORE_CURSOR -> {
                ic ?: return false
                CursorAwareTextHandler.performBackspace(ic)
                true
            }
            GestureAction.DELETE_LINE -> deleteLineAtCursor()
        }
    }

    private fun sendDirectionalKey(keyCode: Int): Boolean {
        val ic = currentInputConnection ?: return false
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return true
    }

    private fun sendCtrlCombination(keyCode: Int, withShift: Boolean): Boolean {
        val ic = currentInputConnection ?: return false
        val meta = if (withShift) KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON else KeyEvent.META_CTRL_ON
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta))
        return true
    }

    private fun moveCursorToPageEdge(start: Boolean): Boolean {
        val ic = currentInputConnection ?: return false
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val textLength = extracted?.text?.length ?: return false
        val target = if (start) 0 else textLength
        ic.setSelection(target, target)
        return true
    }

    private fun showInputMethodPicker(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        mainHandler.post { imm.showInputMethodPicker() }
        return true
    }

    private fun deleteWordBeforeCursor(): Boolean {
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return false
        if (before.isEmpty()) return false

        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return false

        val lastSeparatorIndex = trimmed.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
        val deleteCount = if (lastSeparatorIndex == -1) trimmed.length else trimmed.length - lastSeparatorIndex - 1
        if (deleteCount <= 0) return false

        ic.deleteSurroundingText(deleteCount, 0)
        return true
    }

    private fun deleteLineAtCursor(): Boolean {
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(1000, 0)?.toString() ?: ""

        val startIndex = before.lastIndexOf('\n') + 1
        val deleteBefore = before.length - startIndex

        val nextNewline = after.indexOf('\n')
        val deleteAfter = if (nextNewline == -1) after.length else nextNewline

        if (deleteBefore <= 0 && deleteAfter <= 0) {
            return false
        }

        ic.deleteSurroundingText(deleteBefore, deleteAfter)
        return true
    }

    private fun toggleSmartbarVisibility(): Boolean {
        suggestionsEnabled = !suggestionsEnabled
        unifiedKeyboardView?.setSuggestionsEnabled(suggestionsEnabled)

        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("flutter.display_suggestions", suggestionsEnabled).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist smartbar toggle", e)
        }

        return true
    }
    
    private fun configureFeedbackModules() {
        configureSoundManager()
        applyTapEffectStyle()
    }

    /**
     * Load sound settings only when they actually change.
     * This prevents log spam from repeated keyboard rebuilds.
     * Call this explicitly when settings change, not on every keyboard refresh.
     */
    private fun loadSoundSettingsIfNeeded(): Boolean {
        val p = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val enabled = p.getBoolean("flutter.sound_enabled", false)  // ✅ Changed default to FALSE
        val soundVolumePercent = p.getIntCompat("flutter.sound_volume", 50)
        val volume = (soundVolumePercent / 100.0f).coerceIn(0f, 1f)
        val intensity = p.getIntCompat("flutter.sound_intensity", 2)
        
        // Check if settings actually changed
        if (cachedSoundEnabled == enabled && 
            cachedSoundVolume == volume && 
            cachedSoundIntensity == intensity) {
            // Settings unchanged, no need to reload
            return false
        }
        
        // Settings changed - update cache and apply
        cachedSoundEnabled = enabled
        cachedSoundVolume = volume
        cachedSoundIntensity = intensity
        
        // Update instance variables
        this.soundEnabled = enabled
        this.soundVolume = volume
        this.soundIntensity = intensity
        
        // ✅ FIX: Update global sound manager enabled flag
        KeyboardSoundManager.isEnabled = enabled
        
        // Update granular sound settings
        if (enabled) {
            keyPressSounds = p.getBoolean("flutter.key_press_sounds", false)  // ✅ Changed default to FALSE
            longPressSounds = p.getBoolean("flutter.long_press_sounds", false)  // ✅ Changed default to FALSE
            repeatedActionSounds = p.getBoolean("flutter.repeated_action_sounds", false)  // ✅ Changed default to FALSE
        } else {
            keyPressSounds = false
            longPressSounds = false
            repeatedActionSounds = false
        }
        
        // Log only when settings actually change
        Log.d(TAG, "🔊 Sound settings UPDATED: enabled=$enabled, volume=$volume (${soundVolumePercent}%), intensity=$intensity")
        
        // Configure sound manager with updated settings
        configureSoundManager()
        
        return true
    }
    
    /**
     * Explicitly called when settings change from Flutter.
     * Invalidates cache and forces reload of sound settings.
     */
    private fun onSettingsChanged() {
        // Invalidate cache to force reload
        cachedSoundEnabled = null
        cachedSoundVolume = null
        cachedSoundIntensity = null
        
        // Load settings (will now reload since cache is invalidated)
        loadSoundSettingsIfNeeded()
    }

    private fun configureSoundManager() {
        // Avoid reapplying identical sound configuration on every layout refresh
        val soundConfigSignature = listOf(
            soundEnabled,
            soundVolume,
            soundIntensity,
            selectedSoundProfile,
            customSoundUri,
            keyPressSounds,
            longPressSounds,
            repeatedActionSounds
        ).joinToString("|")
        if (soundConfigSignature == lastSoundConfigSignature) {
            return
        }
        lastSoundConfigSignature = soundConfigSignature

        // ✅ CRITICAL: Always check for user's selected sound first before configuring
        val keyboardPrefs = getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
        val userSelectedSound = keyboardPrefs.getString("selected_sound", null)
        
        if (userSelectedSound != null && (selectedSoundProfile != "custom" || customSoundUri != "sounds/$userSelectedSound")) {
            // User has selected a sound - ensure we use it
            selectedSoundProfile = "custom"
            customSoundUri = "sounds/$userSelectedSound"
            Log.d(TAG, "🔊 Overriding sound with user selection: $userSelectedSound")
        }
        
        if (soundEnabled) {
            // ✅ CRITICAL: Ensure soundIntensity is not 0 when sound is enabled
            if (soundIntensity == 0) {
                soundIntensity = 2 // Default to medium intensity
                Log.d(TAG, "🔊 Auto-corrected soundIntensity from 0 to 2 (medium)")
            }
            // ✅ Update singleton with current preferences
            // SoundPool already initialized in onCreate() - just update settings
            val effectiveVolume = computeEffectiveSoundVolume()
            // ✅ CRITICAL: Ensure volume is not 0 when sound is enabled
            val finalVolume = if (effectiveVolume <= 0f && soundVolume > 0f) {
                // If computed volume is 0 but base volume > 0, use base volume with medium intensity
                (soundVolume.coerceIn(0f, 1f) * 0.8f).coerceIn(0f, 1f)
            } else {
                effectiveVolume
            }
            KeyboardSoundManager.update(
                selectedSoundProfile,
                finalVolume,
                context = this,
                customUri = customSoundUri
            )
            Log.d(TAG, "🔊 SoundManager configured: profile=$selectedSoundProfile, volume=$finalVolume (base=$soundVolume, intensity=$soundIntensity), uri=$customSoundUri")
        } else {
            // Even when disabled, don't release - just set volume to 0
            
            Log.d(TAG, "🔇 Sound disabled (volume set to 0)")
        }
    }

    private fun computeEffectiveSoundVolume(): Float {
        var effective = soundVolume.coerceIn(0f, 1f)
        // ✅ CRITICAL: If sound is enabled but intensity is 0, default to medium (2) to prevent volume=0
        val intensity = if (soundEnabled && soundIntensity == 0) 2 else soundIntensity
        effective *= when (intensity) {
            0 -> 0f
            1 -> 0.5f
            2 -> 0.8f
            3 -> 1.0f
            else -> 0.8f
        }
        return effective.coerceIn(0f, 1f)
    }

    private fun applyTapEffectStyle() {
        val enabled = visualIntensity > 0
        keyboardView?.let { view ->
            if (view is SwipeKeyboardView) {
                view.setTapEffectStyle(selectedTapEffectStyle, enabled)
            }
        }
        unifiedKeyboardView?.setTapEffectStyle(selectedTapEffectStyle, enabled)
    }

    private fun getUserBottomOffsetPx(): Int {
        val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val orientation = resources.configuration.orientation

        val genericKey = "flutter.keyboard.bottomOffsetDp"
        val orientedKey = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "flutter.keyboard.bottomOffsetLandscapeDp"
        } else {
            "flutter.keyboard.bottomOffsetPortraitDp"
        }
        val legacyOrientedKey = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "keyboard.bottomOffsetLandscapeDp"
        } else {
            "keyboard.bottomOffsetPortraitDp"
        }

        val offsetDp = when {
            prefs.contains(genericKey) -> prefs.getIntCompat(genericKey, 0)
            prefs.contains(orientedKey) -> prefs.getIntCompat(orientedKey, prefs.getIntCompat(legacyOrientedKey, 0))
            prefs.contains(legacyOrientedKey) -> prefs.getIntCompat(legacyOrientedKey, 0)
            else -> 0
        }.coerceIn(0, 40)

        return dpToPx(offsetDp)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
    


    private fun SharedPreferences.getBooleanCompat(k: String, def: Boolean): Boolean {
        val value = all[k]
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || (value.equals("1"))
            is Int -> value != 0
            is Long -> value != 0L
            else -> def
        }
    }

    private fun SharedPreferences.getStringCompat(k: String, def: String): String {
        return try {
            getString(k, null) ?: all[k]?.toString() ?: def
        } catch (e: ClassCastException) {
            all[k]?.toString() ?: def
        }
    }
    
    private val Int.dp: Int get() = (resources.displayMetrics.density * this).toInt()
    
    /**
     * Initialize multilingual keyboard components
     */
    private fun initializeMultilingualComponents() {
        Log.d(TAG, "🚀 initializeMultilingualComponents() called")
        try {
            // LanguageManager already initialized in initializeCoreComponents()
            // No need to reinitialize
            
            // LanguageDetector removed - was never used (dead code cleanup)
            
            // Initialize language layout adapter (JSON-based dynamic layouts)
            languageLayoutAdapter = LanguageLayoutAdapter(this)
            languageLayoutAdapter.setShowUtilityKey(showUtilityKeyEnabled)
            Log.d(TAG, "✓ LanguageLayoutAdapter initialized")
            
            // 🔍 AUDIT: Verify all key mappings at startup (after languageLayoutAdapter is ready)
            Log.d(TAG, "🔍 Running key mapping verification audit...")
            try {
                languageLayoutAdapter.verifyAllMappings()
                
                // 🔍 AUDIT: Compare all template mappings
                listOf("qwerty_template.json", "symbols_template.json", "extended_symbols_template.json", "dialer_template.json")
                    .forEach { templateName ->
                        languageLayoutAdapter.compareKeyMappings(templateName)
                    }
                Log.d(TAG, "✅ Key mapping audit complete")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Key mapping audit failed (non-fatal)", e)
            }
            
            // ✅ Swipe functionality is built into UnifiedAutocorrectEngine (no separate initialization needed)
            Log.d(TAG, "🔧 Swipe functionality available in UnifiedAutocorrectEngine")
            
            // Core components already initialized in initializeCoreComponents()
            // Language loading happens asynchronously in background
            
            // Connect user dictionary manager to autocorrect engine (if initialized)
            if (::userDictionaryManager.isInitialized) {
                // User dictionary integrated in UnifiedAutocorrectEngine
                Log.d(TAG, "Connected user dictionary manager to autocorrect engine")
            }
            
            // Phase 2: User dictionary integration handled by UnifiedAutocorrectEngine
            Log.d(TAG, "User dictionary integration complete")
            
            Log.d(TAG, "Enhanced prediction system initialized (swipe built-in)")
            
            // Run validation tests for enhanced autocorrect
            coroutineScope.launch {
                delay(2000) // Wait for dictionary to load
                val testResults = autocorrectEngine.getStats()
                testResults.forEach { result ->
                    Log.d(TAG, "Autocorrect Test: $result")
                }
                
                // Dictionary loading handled by MultilingualDictionary system
                // loadDictionariesAsync() // DISABLED: Unified system handles this
                Log.d(TAG, "Dictionary initialization completed via unified system")
            }
            
            // Set up language change listener
            languageManager.addLanguageChangeListener(object : LanguageManager.LanguageChangeListener {
                override fun onLanguageChanged(oldLanguage: String, newLanguage: String) {
                    handleLanguageChange(oldLanguage, newLanguage)
                }
                
                override fun onEnabledLanguagesChanged(enabledLanguages: Set<String>) {
                    handleEnabledLanguagesChange(enabledLanguages)
                }
            })
            
            // Initialize with current enabled languages
            val enabledLanguages = languageManager.getEnabledLanguages()
            if (::autocorrectEngine.isInitialized) {
                coroutineScope.launch {
                    autocorrectEngine.preloadLanguages(enabledLanguages.toList())
                }
            }
            
            Log.d(TAG, "Multilingual components initialized successfully")
            logEngineStatus()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing multilingual components", e)
        }
    }
    
    /**
     * Handle language change
     */
    // STREAMLINED: Language change handling with proper logging
    private fun handleLanguageChange(oldLanguage: String, newLanguage: String) {
        try {
            Log.i(TAG, "[AIKeyboard] Language: $oldLanguage → $newLanguage")
            
            // Update unified autocorrect engine locale
            autocorrectEngine.setLocale(newLanguage)
            
            // Update keyboard view with new layout
            // Reload dynamic JSON-based layout for new language
            if (currentKeyboard == KEYBOARD_LETTERS) {
                // ✅ Use unified controller for consistent layout loading
                unifiedController.buildAndRender(newLanguage, LanguageLayoutAdapter.KeyboardMode.LETTERS, showNumberRow)
            }
            
            // Update language switch view
            languageSwitchView?.refreshDisplay()
            
            // Clear current word and update suggestions
            currentWord = ""
            updateAISuggestions()
            
            // Show confirmation toast
            //Toast.makeText(this, "Language: ${languageManager.getLanguageDisplayName(newLanguage)}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling language change", e)
        }
    }
    
    /**
     * Handle enabled languages change
     */
    private fun handleEnabledLanguagesChange(enabledLanguages: Set<String>) {
        try {
            Log.d(TAG, "Enabled languages changed: $enabledLanguages")
            
            // Update autocorrect engine
            if (::autocorrectEngine.isInitialized) {
                coroutineScope.launch {
                    autocorrectEngine.preloadLanguages(enabledLanguages.toList())
                }
            }
            
            // Preload keyboard layouts via LanguageLayoutAdapter
            coroutineScope.launch {
                enabledLanguages.forEach { language ->
                    try {
                        languageLayoutAdapter.preloadKeymap(language)
                        Log.d(TAG, "Preloaded keymap for $language")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not preload keymap for $language", e)
                    }
                }
            }
            
            // Update language switch view visibility
            languageSwitchView?.refreshDisplay()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling enabled languages change", e)
        }
    }
    
    override fun onCreateInputView(): View {
        Log.d(TAG, "🚀 Creating UnifiedKeyboardView")
        initialInsetsApplied = false
        
        // ✅ UNIFIED KEYBOARD VIEW: Single root view for keyboard + panels
        unifiedKeyboardView = UnifiedKeyboardView(
            context = this,
            themeManager = themeManager,
            heightManager = keyboardHeightManager,
            onKeyCallback = { code, codes ->
                // Forward key events to service
                onKey(code, codes)
            }
        )
        
        // Set suggestion display count from settings
        unifiedKeyboardView?.setSuggestionDisplayCount(suggestionCount)
        unifiedKeyboardView?.setSuggestionsEnabled(suggestionsEnabled)
        
        // ✅ NEW: Apply key preview setting
        unifiedKeyboardView?.setPreviewEnabled(keyPreviewEnabled)
        Log.d(TAG, "✅ Key preview applied on view creation: $keyPreviewEnabled")
        
        // ✅ NEW: Apply long press delay setting
        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val longPressDelay = flutterPrefs.getIntCompat("flutter.keyboard.longPressDelayMs", 200)
        unifiedKeyboardView?.setLongPressDelay(longPressDelay)
        Log.d(TAG, "✅ Long press delay applied on view creation: ${longPressDelay}ms")

        unifiedKeyboardView?.setClipboardButtonVisible(isClipboardHistoryEnabled())
        
        // Set suggestion controller for clipboard suggestion management
        if (::unifiedSuggestionController.isInitialized) {
            unifiedKeyboardView?.setSuggestionController(unifiedSuggestionController)
        }
        
        // Store reference for backward compatibility
        mainKeyboardLayout = unifiedKeyboardView as LinearLayout
        
        // Legacy container wrapper for compatibility - NO padding, margins, or gaps
        // ✅ TRANSPARENT: Let parent's unified background show through
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,  // Fill remaining space
                1.0f  // Weight to fill remaining space after toolbar/suggestions
            )
            setPadding(0, 0, 0, 0)  // Explicitly remove all padding
            minimumHeight = 0  // No minimum height constraint
            setBackgroundColor(Color.TRANSPARENT)  // Transparent to show unified background
        }
        
        // ✅ REMOVED: Legacy SwipeKeyboardView setup - UnifiedKeyboardView handles everything
        // Legacy keyboardView reference kept for compatibility but not used
        keyboardView = null
        
        // Initialize unified layout controller (centralized layout orchestrator)
        unifiedController = UnifiedLayoutController(
            context = this,
            service = this,
            adapter = languageLayoutAdapter,
            keyboardView = unifiedKeyboardView!!,  // ✅ Use unified view
            heightManager = keyboardHeightManager
        )
        
        // Initialize caps manager first, then integrate with unified controller
        initializeCapsShiftManagerDirect()
        
        // Initialize unified controller with all components
        unifiedController.initialize(languageManager, capsShiftManager, languageSwitchView)
        
        Log.d(TAG, "✅ Unified layout controller initialized with all components")
        
        // ✅ Initialize UnifiedPanelManager - Single manager for all panels
        unifiedPanelManager = UnifiedPanelManager(
            context = this,
            themeManager = themeManager,
            keyboardHeightManager = keyboardHeightManager,
            inputConnectionProvider = { currentInputConnection },
            onBackToKeyboard = { restoreKeyboardFromPanel() }
        )
        // ✅ No container attachment needed - UnifiedKeyboardView manages display

        EmojiRepository.ensureLoaded(this)

        // Setup emoji search mode listener
        unifiedPanelManager?.setEmojiSearchModeListener { active ->
            emojiSearchActive = active
            Log.d(TAG, "Emoji search mode toggled: $active")
        }
        
        // Set callback for processed text
        unifiedPanelManager?.setOnTextProcessedListener { processedText ->
            if (processedText.isBlank()) return@setOnTextProcessedListener

            val original = when {
                currentAiSourceText.isNotBlank() -> currentAiSourceText
                else -> getCurrentInputText()
            }

            if (original.isNotBlank()) {
                replaceTextWithResult(original, processedText)
            } else {
                currentInputConnection?.commitText(processedText, 1)
            }

            restoreKeyboardFromPanel()
            currentAiSourceText = ""
        }
        
        Log.d(TAG, "✅ UnifiedPanelManager initialized and attached")
        
        // Connect panel manager to unified view
        unifiedKeyboardView?.setPanelManager(unifiedPanelManager!!)
        
        // ✅ Setup UnifiedKeyboardView listeners and integrations
        setupKeyboardViewIntegration()
        
        Log.d(TAG, "✅ All listeners connected to UnifiedKeyboardView")
        
        // ✅ Load initial typing layout
        unifiedKeyboardView?.post {
            unifiedController.buildAndRender(
                currentLanguage,
                LanguageLayoutAdapter.KeyboardMode.LETTERS,
                showNumberRow
            )
        }
        
        // Apply theme (UnifiedKeyboardView auto-listens to ThemeManager)
        applyTheme()
        if (pendingThemeUpdate) {
            unifiedKeyboardView?.postDelayed({
                applyThemeFromBroadcast()
                pendingThemeUpdate = false
            }, 100)
        }
        
        Log.d(TAG, "✅ UnifiedKeyboardView initialized and ready")
        
        // Mark view as ready and flush pending suggestions
        unifiedViewReady = true
        pendingInputSnapshot?.let { snapshot ->
            unifiedKeyboardView?.updateEditorTextSnapshot(snapshot)
        }
        pendingSuggestions?.let { suggestions ->
            updateSuggestionUI(suggestions)
        }
        pendingSuggestions = null
        pendingInputSnapshot = null

        // ✅ CRITICAL FIX: Apply window insets to handle navigation bar
        unifiedKeyboardView?.let { view ->
            // Make view fit system windows
            view.fitsSystemWindows = false
            view.clipToPadding = false
            view.clipChildren = false
            
            // Apply bottom padding for navigation bar
            keyboardHeightManager.applySystemInsets(
                view = view,
                applyBottom = true,
                applyTop = false
            ) { topInset, bottomInset ->
                val bottomOffsetPx = getUserBottomOffsetPx()
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    bottomInset + bottomOffsetPx
                )
                Log.d(
                    TAG,
                    "🔧 Navigation bar insets applied - Top: $topInset, Bottom: $bottomInset, userOffset=$bottomOffsetPx"
                )
            }
            
            // Additional fallback: Add bottom padding manually if insets not applied
            view.post {
                val navBarHeight = keyboardHeightManager.getNavigationBarHeight()
                val bottomOffsetPx = getUserBottomOffsetPx()
                val targetBottomPadding = navBarHeight + bottomOffsetPx
                if (navBarHeight > 0 && view.paddingBottom < targetBottomPadding) {
                    view.setPadding(
                        view.paddingLeft,
                        view.paddingTop,
                        view.paddingRight,
                        targetBottomPadding
                    )
                    Log.d(
                        TAG,
                        "🔧 Manual navigation bar padding applied: navBar=$navBarHeight px, userOffset=$bottomOffsetPx"
                    )
                }
            }
        }

        unifiedKeyboardView?.let { view ->
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                if (!initialInsetsApplied) {
                    initialInsetsApplied = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "✅ Deferred keyboard build after insets available")
                        reloadKeyboard(force = true)
                    }, 60)
                }
                insets
            }
        }

        // Ensure CleverType configuration (spacing, sizing, etc.) is applied once the view exists
        mainHandler.post {
            try {
                applyConfig()
                Log.d(TAG, "✅ Applied keyboard config immediately after view creation")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying keyboard config after view creation", e)
            }
        }

        return unifiedKeyboardView!!
    }
    
    /**
     * Setup UnifiedKeyboardView integration with service listeners
     * Centralizes all listener setup for cleaner code organization
     */
    private fun setupKeyboardViewIntegration() {
        unifiedKeyboardView?.apply {
            // Set InputConnection provider for gestures
            setInputConnectionProvider(object : UnifiedKeyboardView.InputConnectionProvider {
                override fun getCurrentInputConnection(): InputConnection? {
                    return this@AIKeyboardService.currentInputConnection
                }
            })
            
            // Connect suggestion update listener
            setSuggestionUpdateListener(object : UnifiedKeyboardView.SuggestionUpdateListener {
                override fun onSuggestionsRequested(prefix: String) {
                    // Debounced suggestion update
                    updateAISuggestions()
                }
            })
            
            // Connect swipe listener with UnifiedAutocorrectEngine integration
            setSwipeListener(object : UnifiedKeyboardView.SwipeListener {
                override fun onSwipeDetected(sequence: List<Int>, normalizedPath: List<Pair<Float, Float>>, isPreview: Boolean) {
                    // Use UnifiedAutocorrectEngine for swipe suggestions
                    if (::autocorrectEngine.isInitialized) {
                        val swipeSuggestions = autocorrectEngine.suggestForSwipe(sequence, normalizedPath)
                        if (swipeSuggestions.isNotEmpty()) {
                            updateSuggestionUI(swipeSuggestions)
                        }
                    }
                    
                    // Pass to main handler with isPreview flag
                    this@AIKeyboardService.onSwipeDetected(sequence, "", sequence, normalizedPath, isPreview)
                }
                
                override fun onSwipeStarted() {
                    this@AIKeyboardService.onSwipeStarted()
                }
                
                override fun onSwipeEnded() {
                    this@AIKeyboardService.onSwipeEnded()
                }
            })
            
            // Connect autocorrect listener
            setAutocorrectListener(object : UnifiedKeyboardView.AutocorrectListener {
                override fun onAutocorrectCommit(original: String, corrected: String) {
                    // Commit the corrected text
                    currentInputConnection?.commitText(corrected, 1)
                }
            })
            
            // Set up keyboard mode toggles (called from toolbar buttons)
            // These are now handled directly by UnifiedKeyboardView.toggleMode()
        }

        refreshSwipeCapability("integrationSetup")
    }
    
    /**
     * Get navigation bar height from system resources
     * Note: This is now only used for reference/debugging, not for height calculations
     */
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
    
    /**
     * Get status bar height from system resources
     */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
    
    /**
     * Get current text from input field for AI processing
     */
    private fun getCurrentInputText(): String {
        return try {
            val ic = currentInputConnection ?: return ""
            
            // Try to get selected text first
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrBlank()) {
                Log.d(TAG, "Found selected text: '${selectedText.take(100)}...'")
                return selectedText.toString()
            }
            
            // If no selection, get text around cursor (last sentence/paragraph)
            val beforeCursor = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
            val afterCursor = ic.getTextAfterCursor(100, 0)?.toString() ?: ""
            
            // Try to extract the current sentence or paragraph
            val currentText = when {
                // If there's substantial text before cursor, get the last sentence/paragraph
                beforeCursor.isNotEmpty() -> {
                    val sentences = beforeCursor.split(Regex("[.!?]\\s+"))
                    val lastSentence = sentences.lastOrNull()?.trim() ?: ""
                    
                    // If last sentence is too short, get more context
                    if (lastSentence.length < 10 && sentences.size > 1) {
                        sentences.takeLast(2).joinToString(". ").trim()
                    } else {
                        lastSentence
                    }
                }
                // If there's text after cursor, include it
                afterCursor.isNotEmpty() -> {
                    val nextWords = afterCursor.split("\\s+".toRegex()).take(10).joinToString(" ")
                    "$beforeCursor$nextWords".trim()
                }
                else -> beforeCursor.trim()
            }
            
            // Clean up the text
            val cleanText = currentText.replace(Regex("\\s+"), " ").trim()
            
            Log.d(TAG, "Retrieved input text (${cleanText.length} chars): '${cleanText.take(100)}${if (cleanText.length > 100) "..." else ""}'")
            
            cleanText
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current input text", e)
            ""
        }
    }
    
    /**
     * Rebind OnKeyboardActionListener after layout changes
     * NOTE: Always call this after setKeyboard() or layout changes to keep spacebar long-press working
     */
    private fun rebindKeyboardListener() {
        keyboardView?.apply {
            setOnKeyboardActionListener(this@AIKeyboardService)
            setSwipeListener(this@AIKeyboardService)
        }
        // Reset long-press state
        longPressHandler.removeCallbacks(longPressRunnable)
        currentLongPressKey = -1
    }
    
    /**
     * Load language preferences from SharedPreferences
     */
    private fun loadLanguagePreferences() {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            
            // Load enabled languages (comma-separated string)
            val enabledLangsStr = prefs.getString("flutter.enabled_languages", null)
            enabledLanguages = if (enabledLangsStr != null && enabledLangsStr.isNotEmpty()) {
                enabledLangsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf("en") // Default to English
            }
            
            // CRITICAL: Ensure enabled list is never empty
            if (enabledLanguages.isEmpty()) {
                enabledLanguages = listOf("en")
                Log.w(TAG, "⚠️ Enabled languages was empty, defaulting to English")
            }
            
            // Load current language
            var loadedLanguage = prefs.getString("flutter.current_language", null)?.takeIf { it.isNotEmpty() }
            
            // CRITICAL: Validate current language is in enabled list
            if (loadedLanguage != null && !enabledLanguages.contains(loadedLanguage)) {
                Log.w(TAG, "⚠️ Current language '$loadedLanguage' not in enabled list $enabledLanguages")
                loadedLanguage = null  // Force fallback
            }
            
            // Set current language with fallback
            currentLanguage = loadedLanguage ?: enabledLanguages.firstOrNull() ?: "en"
            
            // Save corrected current language back to preferences
            if (loadedLanguage == null || loadedLanguage != currentLanguage) {
                prefs.edit().putString("flutter.current_language", currentLanguage).apply()
                Log.d(TAG, "✅ Corrected current language to: $currentLanguage")
            }
            
        // Load multilingual mode
        multilingualEnabled = prefs.getBoolean("flutter.multilingual_enabled", false)
        
        // Phase 2: Load feature flags
        transliterationEnabled = prefs.getBoolean("flutter.transliteration_enabled", true)
        reverseTransliterationEnabled = prefs.getBoolean("flutter.reverse_transliteration_enabled", false)
        
        // Update current language index
        currentLanguageIndex = enabledLanguages.indexOf(currentLanguage).coerceAtLeast(0)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading language preferences", e)
            enabledLanguages = listOf("en")
            currentLanguage = "en"
            currentLanguageIndex = 0
            multilingualEnabled = false
        }
    }
    
    /**
     * Initialize transliteration engine for Indic languages (Phase 1)
     */
    private fun initializeTransliteration() {
        try {
            if (currentLanguage in listOf("hi", "te", "ta")) {
                transliterationEngine = TransliterationEngine(this, currentLanguage)
                // IndicScriptHelper removed - functionality integrated elsewhere
                
                // Phase 2: Dictionary and autocorrect integration
                // TODO: Integrate with existing systems
                // Load dictionary if not already loaded
                // if (multilingualDictionary.isLoaded(currentLanguage) == false) {
                //     multilingualDictionary.loadLanguage(currentLanguage, coroutineScope)
                // }
                
                applyLanguageSpecificFont(currentLanguage)
                updateSpacebarLanguageLabel(currentLanguage)
                Log.d(TAG, "✅ Transliteration initialized for $currentLanguage")
            } else {
                // Clean up for non-Indic languages
                transliterationEngine = null
                romanBuffer.clear()
                // Font will be handled by system default
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing transliteration for $currentLanguage", e)
        }
    }
    
    /**
     * Clear transliteration suggestions from UI (Phase 2)
     */
    private fun clearTransliterationSuggestions() {
        try {
            // TODO: Clear suggestion strip
            Log.d(TAG, "Cleared transliteration suggestions")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear transliteration suggestions", e)
        }
    }
    
    /**
     * Apply language-specific fonts (Noto fonts for Indic scripts)
     * Phase 2: Now uses FontManager
     */
    private fun applyLanguageSpecificFont(language: String) {
        try {
            val typeface = FontManager.getTypefaceFor(language, assets)
            if (typeface != null) {
                // TODO: Apply to keyboard view when API is available
                // keyboardView?.setTypeface(typeface)
                Log.d(TAG, "✅ Custom font loaded for $language")
            } else {
                Log.d(TAG, "Using system font for $language")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Font loading error, using system font", e)
        }
    }
    
    /**
     * Update spacebar to show language name in native script
     */
    private fun updateSpacebarLanguageLabel(language: String) {
        val label = when (language) {
            "hi" -> "हिन्दी"
            "te" -> "తెలుగు"
            "ta" -> "தமிழ்"
            "en" -> "English"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            else -> language.uppercase()
        }
        
        try {
            keyboardView?.keyboard?.keys?.find { it.codes.firstOrNull() == KEYCODE_SPACE }?.let { spaceKey ->
                spaceKey.label = label
                val keyIndex = keyboardView?.keyboard?.keys?.indexOf(spaceKey) ?: 0
                keyboardView?.invalidateKey(keyIndex)
                Log.d(TAG, "✅ Spacebar label updated: $label")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not update spacebar label", e)
        }
    }
    
    /**
     * Cycle to next language in enabled list
     */
    private fun cycleLanguage() {
        // CRITICAL: Reload language preferences first to catch any changes from the app
        loadLanguagePreferences()
        
        if (enabledLanguages.isEmpty() || enabledLanguages.size < 1) {
            Log.w(TAG, "⚠️ No enabled languages to cycle")
            return
        }
        
        if (enabledLanguages.size == 1) {
            Log.d(TAG, "Only one language enabled: ${enabledLanguages[0]}")
            return
        }
        
        // CRITICAL: Validate current language is still in the list
        val currentIndex = enabledLanguages.indexOf(currentLanguage)
        if (currentIndex == -1) {
            // Current language was removed, start from first language
            Log.w(TAG, "⚠️ Current language '$currentLanguage' no longer enabled, resetting to first")
            currentLanguageIndex = 0
            currentLanguage = enabledLanguages[0]
        } else {
            currentLanguageIndex = currentIndex
        }
        
        // Cycle to next language
        val oldLanguage = currentLanguage
        currentLanguageIndex = (currentLanguageIndex + 1) % enabledLanguages.size
        currentLanguage = enabledLanguages[currentLanguageIndex]

        // Save to SharedPreferences
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit().putString("flutter.current_language", currentLanguage).apply()
            Log.d(TAG, "✅ Language cycled to: $currentLanguage (${currentLanguageIndex + 1}/${enabledLanguages.size})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving language cycle", e)
        }
        
        // CRITICAL: Activate the new language in UnifiedAutocorrectEngine
        coroutineScope.launch {
            try {
                Log.d(TAG, "🔄 Activating switched language: $oldLanguage → $currentLanguage")
                activateLanguage(currentLanguage)
                Log.d(TAG, "✅ Language switch activation complete for $currentLanguage")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error activating switched language $currentLanguage", e)
            }
        }
        
        // Reinitialize transliteration for the new language
        initializeTransliteration()
        
        // Reload keyboard layout for the new language
        if (currentKeyboardMode == KeyboardMode.LETTERS) {
            switchKeyboardMode(KeyboardMode.LETTERS)  // This will rebind listener
            Log.d(TAG, "✅ Keyboard layout reloaded for $currentLanguage")
        } else {
            Log.d(TAG, "⚠️ Not in LETTERS mode, skipping layout reload. Mode: $currentKeyboardMode")
        }
        
        // Phase 3: Sync dictionary managers to new language
        try {
            if (::dictionaryManager.isInitialized) {
                dictionaryManager.switchLanguage(currentLanguage)
                Log.d(TAG, "✅ Dictionary manager switched to $currentLanguage")
            }
            
            if (::userDictionaryManager.isInitialized) {
                userDictionaryManager.switchLanguage(currentLanguage)
                if (firebaseBlockedInIme) {
                    Log.d(TAG, "☁️ Cloud sync skipped for $currentLanguage (IME process)")
                } else {
                    // Sync from cloud for new language
                    userDictionaryManager.syncFromCloud(currentLanguage)
                    
                    // Load shortcuts for new language
                    userDictionaryManager.loadShortcutsFromCloud(currentLanguage) { cloudShortcuts ->
                        dictionaryManager.importFromCloud(cloudShortcuts)
                        Log.d(TAG, "✅ Loaded ${cloudShortcuts.size} shortcuts for $currentLanguage")
                    }
                }
                
                Log.d(TAG, "✅ User dictionary manager switched to $currentLanguage")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing managers on language switch", e)
        }
        
        // Show toast notification
        showLanguageToast(currentLanguage)
    }
    
    /**
     * Show language switch toast
     */
    private fun showLanguageToast(langCode: String) {
        try {
            val langNames = mapOf(
                "en" to "English",
                "hi" to "हिन्दी",
                "te" to "తెలుగు",
                "ta" to "தமிழ்",
                "mr" to "मराठी",
                "bn" to "বাংলা",
                "gu" to "ગુજરાતી",
                "kn" to "ಕನ್ನಡ",
                "ml" to "മലയാളം",
                "pa" to "ਪੰਜਾਬੀ",
                "ur" to "اردو",
                "es" to "Español",
                "fr" to "Français",
                "de" to "Deutsch"
            )
            val displayName = langNames[langCode] ?: langCode.uppercase()
            // Toast removed - language change logged only
        } catch (e: Exception) {
            Log.e(TAG, "Error showing language toast", e)
        }
    }

    private fun cycleLanguageReverse() {
        loadLanguagePreferences()
        if (enabledLanguages.size <= 1) {
            Log.d(TAG, "No alternate language available for reverse cycle")
            return
        }

        if (!enabledLanguages.contains(currentLanguage)) {
            currentLanguage = enabledLanguages.first()
            currentLanguageIndex = 0
        }

        val oldLanguage = currentLanguage
        currentLanguageIndex = if (currentLanguageIndex - 1 < 0) enabledLanguages.lastIndex else currentLanguageIndex - 1
        currentLanguage = enabledLanguages[currentLanguageIndex]

        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.edit().putString("flutter.current_language", currentLanguage).apply()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving reverse language cycle", e)
        }

        coroutineScope.launch {
            try {
                Log.d(TAG, "🔄 Activating switched language (reverse): $oldLanguage → $currentLanguage")
                activateLanguage(currentLanguage)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error activating reverse language switch", e)
            }
        }

        showLanguageToast(currentLanguage)
    }
    
    /**
     * Apply unified settings loaded from SettingsManager
     * Replaces loadSettings(), loadEnhancedSettings(), and loadKeyboardSettings()
     * @param unified The consolidated settings object
     * @param logSuccess If true, logs "Settings loaded" message
     */
    private fun applyLoadedSettings(unified: UnifiedSettings, logSuccess: Boolean = false) {
        try {
            // Apply core settings to service fields
            vibrationEnabled = unified.vibrationEnabled
            soundEnabled = unified.soundEnabled
            keyPreviewEnabled = unified.keyPreviewEnabled
            showNumberRow = unified.showNumberRow
            swipeTypingEnabled = unified.swipeTypingEnabled
            autoFillSuggestionsEnabled = unified.autoFillSuggestion
            autoCapitalizationEnabled = unified.autoCapitalization
            rememberCapsLockState = unified.rememberCapsState
            doubleSpacePeriodEnabled = unified.doubleSpacePeriod
            aiSuggestionsEnabled = unified.aiSuggestionsEnabled
            lastSpaceTimestamp = 0L
            currentLanguage = unified.currentLanguage
            
            // ✅ FIX: Update global sound manager enabled flag
            KeyboardSoundManager.isEnabled = unified.soundEnabled
            enabledLanguages = unified.enabledLanguages
            
            // ✅ FIX: Load granular sound & vibration settings from Flutter preferences
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val prefs = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
            
            // ✅ CRITICAL: Read vibration duration ONLY from Flutter preferences - NO fallback
            vibrationMs = flutterPrefs.getInt("flutter.vibration_ms", 50) // Default 50ms
            
            // Read haptic intensity from Flutter (already loaded earlier as hapticIntensity variable)
            // No need to reload here as it's already in unified settings
            
            // ✅ CRITICAL: Read useHapticInterface ONLY from Flutter preferences - NO fallback
            useHapticInterface = flutterPrefs.getBoolean("flutter.use_haptic_interface", true)
            
            // ✅ CRITICAL: Initialize granular vibration settings - ONLY from Flutter preferences, NO fallback
            keyPressVibration = flutterPrefs.getBoolean("flutter.key_press_vibration", unified.vibrationEnabled)
            longPressVibration = flutterPrefs.getBoolean("flutter.long_press_vibration", unified.vibrationEnabled)
            repeatedActionVibration = flutterPrefs.getBoolean("flutter.repeated_action_vibration", unified.vibrationEnabled)
            
            // If main vibration is disabled, disable all granular settings
            if (!unified.vibrationEnabled) {
                keyPressVibration = false
                longPressVibration = false
                repeatedActionVibration = false
            }
            
            // ✅ FIX: Load sound settings only when they change (prevents log spam)
            // Note: soundEnabled was already set from unified.soundEnabled above
            // This will check Flutter preferences and update if changed
            loadSoundSettingsIfNeeded()
            
            visualIntensity = prefs.getInt("visual_intensity", visualIntensity)
            
            // ✅ FIX: Prioritize user's saved sound selection over default
            // Check keyboard_prefs first (set by setKeyboardSound method)
            val keyboardPrefs = getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            val selectedSound = keyboardPrefs.getString("selected_sound", null)
            
            if (selectedSound != null) {
                // User has selected a custom sound - use it
                selectedSoundProfile = "custom"
                customSoundUri = "sounds/$selectedSound"
                Log.d(TAG, "🔊 Using user-selected sound from keyboard_prefs: $selectedSound")
            } else {
                // Check Flutter preferences for sound type and asset
                val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                val flutterSoundType = flutterPrefs.getString("flutter.sound.type", null)
                val flutterSoundAsset = flutterPrefs.getString("flutter.sound.asset", null)
                
                if (flutterSoundType != null && flutterSoundType != "default") {
                    // Use Flutter preference if it's not default
                    selectedSoundProfile = flutterSoundType
                    customSoundUri = flutterSoundAsset ?: prefs.getString("sound_custom_uri", unified.soundCustomUri)
                    Log.d(TAG, "🔊 Using sound from Flutter preferences: type=$flutterSoundType, asset=$flutterSoundAsset")
                } else {
                    // Fall back to unified settings
                    // If unified.soundType is "default", use "bubble" directly to avoid alias resolution
                    selectedSoundProfile = if (unified.soundType == "default") "bubble" else unified.soundType
                    customSoundUri = prefs.getString("sound_custom_uri", unified.soundCustomUri)
                    Log.d(TAG, "🔊 Using sound from unified settings: $selectedSoundProfile")
                }
            }
            
            selectedTapEffectStyle = unified.effectType
            
            // ✅ CRITICAL: Configure sound manager after all settings are loaded
            configureSoundManager()
            
            // Note: autocorrect/autoCap/doubleSpace/popup fields may not exist as direct properties
            // They are read from settings but may be handled differently in the service
            
            // Compute hash to detect actual changes
            val newHash = listOf(
                unified.vibrationEnabled, unified.soundEnabled, unified.keyPreviewEnabled,
                unified.showNumberRow, unified.swipeTypingEnabled, unified.aiSuggestionsEnabled,
                unified.currentLanguage, unified.enabledLanguages.joinToString(","),
                unified.autocorrectEnabled, unified.autoCapitalization, unified.autoFillSuggestion,
                unified.rememberCapsState, unified.doubleSpacePeriod,
                unified.soundType, unified.effectType, unified.soundVolume, unified.soundCustomUri
            ).hashCode()
            
            val settingsChanged = newHash != lastLoadedSettingsHash
            if (settingsChanged) {
                lastLoadedSettingsHash = newHash
                // Apply side effects: theme, layout updates, etc.
                keyboardView?.isPreviewEnabled = keyPreviewEnabled
                // ✅ NEW: Apply to UnifiedKeyboardView as well
                unifiedKeyboardView?.setPreviewEnabled(keyPreviewEnabled)
                Log.d(TAG, "✅ Key preview enabled: $keyPreviewEnabled")
                
                // ✅ NEW: Update long press delay when settings change
                val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                val longPressDelay = flutterPrefs.getIntCompat("flutter.keyboard.longPressDelayMs", 200)
                unifiedKeyboardView?.setLongPressDelay(longPressDelay)
                Log.d(TAG, "✅ Long press delay updated: ${longPressDelay}ms")
                
                refreshSwipeCapability("settingsLoaded")
            }
            
            if (logSuccess) {
                Log.i(TAG, "Settings loaded")
            }

            if (::capsShiftManager.isInitialized) {
                capsShiftManager.updateSettings()
                if (!rememberCapsLockState && capsShiftManager.isCapsLockActive()) {
                    capsShiftManager.setCapsLock(false)
                }
            }

            configureFeedbackModules()
            refreshSwipeCapability("applyLoadedSettings")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying loaded settings", e)
        }
    }
    
    internal fun applyTheme() {
        keyboardView?.let { view ->
            // Apply theme using comprehensive ThemeManager
            val theme = themeManager.getCurrentTheme()
            val palette = themeManager.getCurrentPalette()
            
            // Set keyboard background
            val backgroundDrawable = themeManager.createKeyboardBackground()
            view.background = backgroundDrawable
            
            // The SwipeKeyboardView will request paint objects from ThemeManager
            if (view is SwipeKeyboardView) {
                view.setThemeManager(themeManager)
                view.refreshTheme()
                // Explicitly set background color to ensure consistency
                if (palette.usesImageBackground) {
                    view.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    view.setBackgroundColor(palette.keyboardBg)
                }
                Log.d(TAG, "[AIKeyboard] Theme applied to keyboard and swipe view - Keyboard BG: ${Integer.toHexString(palette.keyboardBg)}")
            }
            
            // Force redraw with new theme
            view.invalidateAllKeys()
            view.invalidate()
            view.requestLayout()
            
            // Streamlined theme application log
            Log.d(TAG, "Applied theme: ${theme.name} (${theme.id})")
        } ?: run {
            Log.w(TAG, "[AIKeyboard] keyboardView is null, cannot apply theme")
        }
    }

    // Enhanced comprehensive theme application with unified palette and smooth transitions
    private fun applyThemeImmediately() {
        try {
            val theme = themeManager.getCurrentTheme()
            val palette = themeManager.getCurrentPalette()
            val enableAnimations = true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP // Always enable animations for V2
            
            Log.d(TAG, "🎨 Applying comprehensive theme: ${theme.name}${if (enableAnimations) " (animated)" else ""}")
            
            // 1. Update keyboard view with unified palette
            keyboardView?.let { view ->
                val backgroundDrawable = themeManager.createKeyboardBackground()
                view.background = backgroundDrawable
                
                if (view is SwipeKeyboardView) {
                    view.setThemeManager(themeManager)
                    view.refreshTheme()
                    // Explicitly set background color to ensure consistency
                    if (palette.usesImageBackground) {
                        view.setBackgroundColor(Color.TRANSPARENT)
                    } else {
                        view.setBackgroundColor(palette.keyboardBg)
                    }
                    val fontScale = computeFontScale(palette)
                    view.updateTextPaints(palette.keyFontFamily, fontScale)
                    
                    // Force complete repaint of all keys
                    view.invalidateAllKeys()
                    view.invalidate()
                    view.requestLayout()
                    
                    // Additional force refresh to ensure all components update
                    mainHandler.postDelayed({
                        view.invalidate()
                        Log.d(TAG, "🔄 Additional keyboard invalidation completed")
                    }, 50)
                }
                
                Log.d(TAG, "✅ Keyboard view themed with V2 system - Keyboard BG: ${Integer.toHexString(palette.keyboardBg)}")
            }
            
            // 2. ✅ UNIFIED THEMING: Update main layout background (covers toolbar + keyboard)
            mainKeyboardLayout?.let { layout ->
                layout.background = themeManager.createKeyboardBackground()
                Log.d(TAG, "✅ Main layout themed with unified background")
            }
            
            // 3. Update suggestion bar text colors (keep transparent background)
            suggestionContainer?.let { container ->
                // Keep transparent background for unified theming
                container.setBackgroundColor(Color.TRANSPARENT)
                container.elevation = 0f
                
                // Update each suggestion - text-only, NO chip backgrounds
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    if (child is TextView) {
                        // Use keyText color for better visibility
                        child.setTextColor(palette.keyText)
                        child.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                Log.d(TAG, "✅ Suggestion bar themed - text colors updated")
            }
            
            // 4. ✅ Keep topContainer transparent for unified background
            topContainer?.let { container ->
                container.setBackgroundColor(Color.TRANSPARENT)
                Log.d(TAG, "✅ Top container transparent")
            }
            
            // 5. ✅ Keep keyboardContainer transparent for unified background
            keyboardContainer?.setBackgroundColor(Color.TRANSPARENT)
            Log.d(TAG, "✅ Keyboard container transparent")
            
            // ✅ All panel theming now handled by UnifiedPanelManager automatically
            // Old panel theming code removed
            
            // 8. Update toolbar icons with new theme colors
            topContainer?.findViewById<LinearLayout>(R.id.keyboard_toolbar_simple)?.let { toolbar ->
                loadToolbarIcons(toolbar, palette)
                Log.d(TAG, "✅ Toolbar icons re-themed")
            }
            
            // 8. Update AI feature panels to match keyboard theme
            applyThemeToPanels()

            // 🎵 Apply theme-defined sound pack / volume
            applyThemeSoundSettings(theme.sounds)
            
            Log.d(TAG, "🎨 Complete theme application finished successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "🔴 Error in comprehensive theme application", e)
        }
    }

    private fun computeFontScale(palette: com.kvive.keyboard.themes.ThemePaletteV2): Float {
        return (palette.keyFontSize / 18f).coerceIn(0.8f, 1.3f)
    }

    fun applyThemeFromFlutter(themeMap: Map<String, Any?>) {
        try {
            val applied = themeManager.applyThemeFromFlutter(themeMap, false)
            val requestedFontScale = (themeMap["fontScale"] as? Number)?.toFloat()
            if (requestedFontScale != null) {
                (keyboardView as? SwipeKeyboardView)?.setLabelScale(requestedFontScale)
            }
            if (applied) {
                Log.d(TAG, "🎨 Theme applied from Flutter payload")
                // Show toast for theme application (only toast kept in app)
                val themeName = themeManager.getCurrentTheme().name
                Toast.makeText(this, "✨ Theme applied: $themeName", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "⚠️ ThemeManager rejected theme payload from Flutter")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error applying theme from Flutter", e)
        }
    }

    fun applySoundSelection(soundName: String?, asset: String?) {
        val normalized = soundName?.lowercase()?.ifBlank { "default" } ?: "default"
        selectedSoundProfile = normalized
        customSoundUri = asset

        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit().putString("flutter.sound.type", normalized).apply()
        if (!asset.isNullOrBlank()) {
            flutterPrefs.edit().putString("flutter.sound.asset", asset).apply()
        }

        configureSoundManager()
        Log.d(TAG, "🔊 Sound selection updated from Flutter: profile=$normalized, asset=$asset")
    }

    fun setKeyboardSound(fileName: String): Boolean {
        return try {
            val prefs = getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_sound", fileName).apply()
            
            // Update service variables to persist the selection
            selectedSoundProfile = "custom"
            val assetPath = "sounds/$fileName"
            customSoundUri = assetPath
            
            // Also save to FlutterSharedPreferences for consistency
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            flutterPrefs.edit().putString("flutter.sound.type", "custom").apply()
            flutterPrefs.edit().putString("flutter.sound.asset", assetPath).apply()
            
            // Update sound manager with the asset file - this loads it into SoundPool
            KeyboardSoundManager.update("custom", computeEffectiveSoundVolume(), this, assetPath)
            
            // Configure sound manager to ensure it's active
            configureSoundManager()
            
            Log.d(TAG, "🔊 Keyboard sound set to: $fileName (path: $assetPath)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting keyboard sound: $fileName", e)
            false
        }
    }

    fun playSoundSample(asset: String?) {
        val previousProfile = selectedSoundProfile
        val previousUri = customSoundUri

        if (!asset.isNullOrBlank()) {
            KeyboardSoundManager.update("custom", computeEffectiveSoundVolume(), this, asset)
        }
        KeyboardSoundManager.play()

        if (!asset.isNullOrBlank()) {
            KeyboardSoundManager.update(previousProfile, computeEffectiveSoundVolume(), this, previousUri)
        }
    }

    fun previewTapEffect(type: String, opacity: Float) {
        (keyboardView as? SwipeKeyboardView)?.applyTapEffect(type, opacity)
    }

    fun syncClipboardFromSystem(): Boolean {
        return try {
            if (::clipboardHistoryManager.isInitialized) {
                clipboardHistoryManager.syncFromSystemClipboard()
                Log.d(TAG, "📋 Clipboard synced from system clipboard")
                true
            } else {
                Log.w(TAG, "⚠️ Clipboard manager not initialized; cannot sync from system")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing clipboard from system", e)
            false
        }
    }

    fun syncClipboardToCloud(): Boolean {
        if (firebaseBlockedInIme) {
            Log.d(TAG, "☁️ Clipboard cloud sync disabled in IME process")
            return false
        }
        return try {
            if (::clipboardHistoryManager.isInitialized) {
                clipboardHistoryManager.syncToCloud()
                Log.d(TAG, "☁️ Clipboard synced to cloud")
                true
            } else {
                Log.w(TAG, "⚠️ Clipboard manager not initialized; cannot sync to cloud")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing clipboard to cloud", e)
            false
        }
    }

    fun syncClipboardFromCloud(): Boolean {
        if (firebaseBlockedInIme) {
            Log.d(TAG, "☁️ Clipboard cloud pull disabled in IME process")
            return false
        }
        return try {
            if (::clipboardHistoryManager.isInitialized) {
                clipboardHistoryManager.syncFromCloud()
                Log.d(TAG, "☁️ Clipboard synced from cloud")
                true
            } else {
                Log.w(TAG, "⚠️ Clipboard manager not initialized; cannot sync from cloud")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing clipboard from cloud", e)
            false
        }
    }
    
    private fun applyThemeSoundSettings(sounds: com.kvive.keyboard.themes.KeyboardThemeV2.Sounds) {
        val newProfile = sounds.pack.ifBlank { "default" }.lowercase()
        val newVolume = sounds.volume.coerceIn(0f, 1f)
        val newCustomUri = sounds.customUris["primary"]

        val profileChanged = newProfile != selectedSoundProfile
        val volumeChanged = newVolume != soundVolume
        val customChanged = newCustomUri != customSoundUri

        if (!profileChanged && !volumeChanged && !customChanged) {
            return
        }

        selectedSoundProfile = newProfile
        soundVolume = newVolume
        customSoundUri = newCustomUri

        // Persist new sound configuration for future sessions
        settings.edit().apply {
            putFloat("sound_volume", soundVolume)
            if (customSoundUri.isNullOrBlank()) {
                remove("sound_custom_uri")
            } else {
                putString("sound_custom_uri", customSoundUri)
            }
            apply()
        }

        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit().putString("flutter.sound.type", selectedSoundProfile).apply()

        configureSoundManager()
        saveSettings()
    }
    
    /**
     * Apply theme to emoji panel recursively
     */
    private fun applyThemeToEmojiPanel(panel: android.view.View, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
        try {
            if (panel is android.view.ViewGroup) {
                val panelBg = if (palette.usesImageBackground) palette.panelSurface else palette.keyboardBg
                panel.setBackgroundColor(panelBg)
                
                for (i in 0 until panel.childCount) {
                    val child = panel.getChildAt(i)
                    when (child) {
                        is android.widget.LinearLayout -> {
                            // Apply to category tabs and toolbar
                            if (child.tag == "emoji_categories" || child.tag == "emoji_header") {
                                val headerColor = if (palette.usesImageBackground) ColorUtils.blendARGB(panelBg, Color.BLACK, 0.2f) else palette.toolbarBg
                                child.setBackgroundColor(headerColor)
                            }
                            applyThemeToEmojiPanel(child, palette)
                        }
                        is android.widget.TextView -> {
                            // Theme category text
                            if (child.tag?.toString()?.startsWith("category_") == true) {
                                child.setTextColor(palette.keyText)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error theming emoji panel", e)
        }
    }

    /**
     * Lightweight helper to refresh toolbar icon assets and tint with the active palette.
     */
    private fun loadToolbarIcons(toolbar: LinearLayout, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
        val iconMap = listOf(
            R.id.btn_ai_assistant to R.drawable.chatgpt_icon,
            R.id.btn_grammar_fix to R.drawable.grammar_icon,
            R.id.btn_word_tone to R.drawable.tone_icon,
            R.id.btn_clipboard to R.drawable.clipboard,
            R.id.btn_emoji to R.drawable.emoji_icon,
            R.id.btn_voice to R.drawable.voice
        )

        iconMap.forEach { (viewId, drawableRes) ->
            toolbar.findViewById<ImageView>(viewId)?.let { image ->
                image.setImageResource(drawableRes)
                image.setColorFilter(palette.keyText)
            }
        }
    }
    
    /**
     * Apply theme to media panel (GIF/Stickers)
     */
    private fun applyThemeToMediaPanel(panel: android.view.View, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
        try {
            if (panel is android.view.ViewGroup) {
                val panelBg = if (palette.usesImageBackground) palette.panelSurface else palette.keyboardBg
                panel.setBackgroundColor(panelBg)
                
                for (i in 0 until panel.childCount) {
                    val child = panel.getChildAt(i)
                    when (child) {
                        is android.widget.LinearLayout -> {
                            if (child.tag == "media_header") {
                                val headerColor = if (palette.usesImageBackground) ColorUtils.blendARGB(panelBg, Color.BLACK, 0.25f) else palette.toolbarBg
                                child.setBackgroundColor(headerColor)
                            }
                            applyThemeToMediaPanel(child, palette)
                        }
                        is android.widget.EditText -> {
                            // Theme search box
                            child.setBackgroundColor(palette.keyBg)
                            child.setTextColor(palette.keyText)
                            child.setHintTextColor(adjustColorAlpha(palette.keyText, 0.6f))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error theming media panel", e)
        }
    }
    
    /**
     * Adjust color alpha for hint text, etc.
     */
    private fun adjustColorAlpha(color: Int, alpha: Float): Int {
        val a = (android.graphics.Color.alpha(color) * alpha).toInt()
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        return android.graphics.Color.argb(a, r, g, b)
    }
    
    /**
     * Apply unified theme colors to all AI feature panels
     * Called when theme changes to ensure panels match keyboard background
     */
    private fun applyThemeToPanels() {
        // Stub method - all theming now handled by UnifiedPanelManager automatically
        return
    }
    // Add visual confirmation of theme change
    private fun showThemeChangeConfirmation() {
        try {
            val themeName = themeManager.getCurrentTheme().name
            val palette = themeManager.getCurrentPalette()
            
            // Show toast for theme application (only toast kept in app)
            Toast.makeText(this, "✨ Theme applied: $themeName", Toast.LENGTH_SHORT).show()
            
            suggestionContainer?.let { container ->
                if (container.childCount > 0) {
                    val firstSuggestion = container.getChildAt(0) as? TextView
                    firstSuggestion?.apply {
                        text = "✨ Theme: $themeName"
                        setTextColor(palette.specialAccent)
                        visibility = View.VISIBLE
                    }
                    
                    // Reset after 2 seconds
                    mainHandler.postDelayed({
                        try {
                            updateAISuggestions()
                        } catch (e: Exception) {
                            // Ignore UI update errors
                        }
                    }, 2000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing theme confirmation", e)
        }
    }

    // Apply theme from broadcast with comprehensive updates
    private fun applyThemeFromBroadcast() {
        try {
            Log.d(TAG, "Applying theme from broadcast...")
            
            // Reload theme from SharedPreferences
            themeManager.reload()
            val reloadSuccess = true // V2 always succeeds with fallback to default
            if (!reloadSuccess) {
                Log.w(TAG, "Failed to reload theme from SharedPreferences")
                return
            }
            
            // Apply comprehensive theme updates
            applyThemeImmediately()
            
            // Show visual confirmation
            showThemeChangeConfirmation()
            
            Log.d(TAG, "Theme successfully applied from broadcast")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying theme from broadcast", e)
        }
    }
    
    // Theme data verification for debugging

    // Update suggestion bar theme (SIMPLIFIED: text-only, no chips)

    // Show theme update confirmation
    
    /**
     * 🔍 DEEP DIAGNOSTIC: Log every key press with full context for audit
     * ⚡ PERFORMANCE: Only enabled in debug builds to avoid slowing down typing
     */
    private fun logKeyDiagnostics(primaryCode: Int, keyCodes: IntArray?) {
        if (BuildConfig.DEBUG) {
            val keyLabel = when (primaryCode) {
                Keyboard.KEYCODE_SHIFT, -1 -> "SHIFT"
                Keyboard.KEYCODE_DELETE, -5 -> "DELETE"
                Keyboard.KEYCODE_DONE, -4 -> "RETURN"
                32 -> "SPACE"
                -14 -> "GLOBE"
                -10 -> "?123"
                -11 -> "ABC"
                -20 -> "=<"
                -21 -> "123"
                else -> if (primaryCode > 0 && primaryCode < 128) primaryCode.toChar().toString() else "CODE_$primaryCode"
            }
            
            Log.d("KeyAudit", "🔍 Key pressed: $keyLabel | Code: $primaryCode | Mode: $currentKeyboardMode | Lang: $currentLanguage")
        }
    }
    
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        // 🔍 DIAGNOSTIC: Log every key press with full context (only in DEBUG)
        if (BuildConfig.DEBUG) {
            logKeyDiagnostics(primaryCode, keyCodes)
        }

        // Handle emoji search input
        if (emojiSearchActive && isEmojiPanelVisible) {
            when (primaryCode) {
                Keyboard.KEYCODE_DELETE -> {
                    playClick(primaryCode)
                    unifiedPanelManager?.removeEmojiSearchCharacter()
                    updateKeyboardState()
                    return
                }
                KEYCODE_SPACE -> {
                    playClick(primaryCode)
                    unifiedPanelManager?.appendEmojiSearchCharacter(' ')
                    updateKeyboardState()
                    return
                }
                Keyboard.KEYCODE_DONE, -4 -> {
                    playClick(primaryCode)
                    unifiedPanelManager?.clearEmojiSearch()
                    updateKeyboardState()
                    return
                }
            }
        }
        
        // ✅ FIX: DISABLE autocorrect on separators (period, comma, etc.)
        // Autocorrect should ONLY happen on SPACE button (handled in handleSpace method)
        // For other punctuation, just commit them without autocorrect
        val isEnterKey = (primaryCode == Keyboard.KEYCODE_DONE || primaryCode == -4)
        
        if (isSeparator(primaryCode) && !isEnterKey && primaryCode != KEYCODE_SPACE) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🔍 Non-space separator detected: code=$primaryCode - committing WITHOUT autocorrect")
            }
            playClick(primaryCode)
            
            val ic = currentInputConnection
            if (ic != null && primaryCode > 0) {
                // Just commit the punctuation mark as-is, no autocorrect
                ic.commitText(String(Character.toChars(primaryCode)), 1)
                currentWord = ""
                
                // Update suggestions for next word
                if (areSuggestionsActive()) {
                    updateAISuggestions()
                } else {
                    clearSuggestions()
                }
            }
            return
        }
        
        val ic = currentInputConnection ?: return
        
        playClick(primaryCode)
        
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)
            Keyboard.KEYCODE_SHIFT -> handleShift()
            Keyboard.KEYCODE_DONE, -4 -> {
                // ✅ FIX: DISABLE autocorrect on ENTER key
                // Autocorrect should ONLY happen on SPACE button
                // Just clear the word and handle enter normally
                currentWord = ""
                
                // Context-aware enter key behavior (Gboard-style)
                // -4 is sym_keyboard_return from dynamic layouts
                handleEnterKey(ic)
                
                // CRITICAL FIX: Clear suggestions after sentence completion
                updateAISuggestions() // This will clear suggestions for empty word
            }
            KEYCODE_SPACE -> handleSpace(ic)
            KEYCODE_SYMBOLS -> switchKeyboardMode(KeyboardMode.SYMBOLS)  // ?123 key
            KEYCODE_LETTERS -> returnToLetters()    // ABC key returns to letters
            KEYCODE_NUMBERS -> cycleKeyboardMode()  // Also cycle
            -20 -> switchKeyboardMode(KeyboardMode.EXTENDED_SYMBOLS)  // =< key
            -21 -> switchKeyboardMode(KeyboardMode.DIALER)  // 123 key
            KEYCODE_VOICE -> startVoiceInput()
            KEYCODE_GLOBE -> {
                // Language switching - cycle through enabled languages
                cycleLanguage()
                
                // CRITICAL FIX: Notify AI services about language change
                if (::unifiedAIService.isInitialized) {
                    try {
                        // Reset suggestion context for new language
                        currentWord = ""
                        updateAISuggestions()
                        Log.d(TAG, "✅ AI services notified of language change to: $currentLanguage")
                    } catch (e: Exception) {
                        Log.e(TAG, "⚠️ Error notifying AI services of language change", e)
                    }
                }
                
            }
            KEYCODE_EMOJI -> {
                // Enhanced emoji panel with visual feedback
                switchKeyboardMode(KeyboardMode.EMOJI)
            }
            else -> handleCharacter(primaryCode, ic)
        }
        
        // Update AI suggestions after key press
        if (areSuggestionsActive() && primaryCode != Keyboard.KEYCODE_DELETE) {
            updateAISuggestions()
        }
    }
    
    private fun handleCharacter(primaryCode: Int, ic: InputConnection) {
        var code = primaryCode.toChar()
        
        // ✅ FIX: Auto-space before typing if following a swipe
        if (lastInputWasSwipe && Character.isLetter(primaryCode)) {
            ic.commitText(" ", 1)
            // Commit the swiped word to history and learn it
            if (currentWord.isNotEmpty()) {
                onWordCommitted(currentWord)
                wordHistory.add(currentWord)
                if (wordHistory.size > 20) wordHistory.removeAt(0)
            }
            currentWord = "" // Reset for new word
            lastInputWasSwipe = false
            lastCommittedSwipeWord = ""
        }
        
        // Enhanced character handling with CapsShiftManager (via unified controller)
        if (::unifiedController.isInitialized) {
            // Process through unified controller which delegates to caps manager
            code = unifiedController.processCharacterInput(code)
        } else if (::capsShiftManager.isInitialized) {
            code = capsShiftManager.processCharacterInput(code)
        } else {
            // Fallback to old implementation
            if (Character.isLetter(code)) {
                code = when (shiftState) {
                    SHIFT_OFF -> Character.toLowerCase(code)
                    SHIFT_ON, SHIFT_CAPS -> Character.toUpperCase(code)
                    else -> Character.toLowerCase(code)
                }
            }
            
            // Auto-reset shift state after character input (except for caps lock)
            if (shiftState == SHIFT_ON) {
                shiftState = SHIFT_OFF
                keyboardView?.let {
                    it.isShifted = false
                    it.invalidateAllKeys()
                }
                caps = false
                isShifted = false
            }
        }

        // Handle emoji search character input
        if (emojiSearchActive && isEmojiPanelVisible) {
            val code = primaryCode.toChar()
            unifiedPanelManager?.appendEmojiSearchCharacter(code)
            updateKeyboardState()
            return
        }
        
        // Update current word
        if (Character.isLetter(code)) {
            // ✅ NEW: If we are starting a fresh word (not an edit), clear rejection memory
            if (currentWord.isEmpty()) {
                rejectedOriginal = null
            }
            
            currentWord += Character.toLowerCase(code)
            Log.d(TAG, "Updated currentWord: '$currentWord'")
            
            // ========== TIMING TRACKING FOR GBOARD-STYLE AUTOCORRECT ==========
            // Record keypress timing for fast-typing detection
            if (::autocorrectEngine.isInitialized) {
                autocorrectEngine.recordKeypress()
            }
            
            // ✅ FIX: Update suggestions DURING typing (show suggestions, no autocorrect)
            if (areSuggestionsActive()) {
                updateAISuggestions()
            }
            
            // Disable dictionary expansion undo once user types new characters
            lastExpansion?.let { (shortcut, _) ->
                // If user is typing something that doesn't match the shortcut, clear expansion tracking
                if (!shortcut.startsWith(currentWord, ignoreCase = true)) {
                    lastExpansion = null
                    Log.d(TAG, "🔒 Dictionary undo disabled - user typed new characters")
                }
            }
            
            // Disable undo once user continues typing (accepts the correction)
            lastCorrection?.let { (_, corrected) ->
                if (!corrected.startsWith(currentWord, ignoreCase = true)) {
                    lastCorrection = null // Clear to disable undo
                    Log.d(TAG, "🔒 Undo disabled - user accepted correction and continued typing")
                }
            }
        } else if (Character.isSpaceChar(code) || code == ' ' || isPunctuation(code)) {
            // Non-letter character ends current word
            if (currentWord.isNotEmpty()) {
                Log.d(TAG, "Non-letter character '$code' - finishing word: '$currentWord'")
                finishCurrentWord()
            }
        }
        
        // Insert character with enhanced text processing
        insertCharacterWithProcessing(ic, code)
        
        // ✅ Auto-capitalize after sentence-ending punctuation (., !, ?)
        if (code == '.' || code == '!' || code == '?') {
            if (::unifiedController.isInitialized) {
                // Set shift state for next character
                unifiedController.handlePunctuationEnd()
            }
        }
        
        // Enhanced auto-shift logic
        updateCapsState(code)
        
        // Update keyboard visual state
        updateKeyboardState()
        
        // Update suggestions in real-time as user types
        updateAISuggestions()
    }
    
    private fun insertCharacterWithProcessing(ic: InputConnection, code: Char) {
        // Get current text context for smart processing
        val textBefore = ic.getTextBeforeCursor(10, 0)
        val context = textBefore?.toString() ?: ""
        
        // Handle smart punctuation
        if (isSmartPunctuationEnabled() && isPunctuation(code)) {
            handleSmartPunctuation(ic, code, context)
        } else {
            // Standard character insertion
            ic.commitText(code.toString(), 1)
        }
        
    }
    
    private fun updateCapsState(code: Char) {
        if (!autoCapitalizationEnabled) {
            return
        }
        val wasCapitalized = caps
        
        // Auto-shift after sentence end
        when (code) {
            '.', '!', '?' -> caps = true
            else -> {
                if (caps && Character.isLetter(code) && !isCapslockEnabled()) {
                    // Turn off caps after typing a letter (unless caps lock is on)
                    caps = false
                }
            }
        }
        
        // Update visual state if changed
        if (wasCapitalized != caps) {
            keyboardView?.isShifted = caps
        }
    }
    
    private fun updateKeyboardState() {
        keyboardView?.invalidateAllKeys()
    }
    
    private fun isSmartPunctuationEnabled(): Boolean = settings.getBoolean("smart_punctuation", true)
    fun isAutoCorrectEnabled(): Boolean = settings.getBoolean("auto_correct", true)
    private fun isCapslockEnabled(): Boolean = settings.getBoolean("caps_lock_active", false)
    private fun isPunctuation(c: Char): Boolean = ".,!?;:".contains(c)
    
    /**
     * Check if a primary code represents a separator (space, punctuation, or enter).
     * Uses actual character codes, not KeyEvent constants.
     */
    private fun isSeparator(code: Int): Boolean {
        // Space + common punctuation + ENTER/DONE
        return code == 32 || code == 46 || code == 44 || code == 33 || code == 63 || // space . , ! ?
               code == 58 || code == 59 || code == 39 || code == 10 ||               // : ; ' \n
               code == -4                                                             // DONE/ENTER (Keyboard.KEYCODE_DONE)
    }
    
    /**
     * Preserve the capitalization pattern of the original word in the suggestion
     */
    private fun preserveCase(suggestion: String, original: String): String {
        return when {
            original.isEmpty() -> suggestion
            original.all { it.isUpperCase() || !it.isLetter() } -> suggestion.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> suggestion.replaceFirstChar { it.uppercase() }
            else -> suggestion.lowercase()
        }
    }
    
    /**
     * Try to auto-correct the word immediately before the cursor and then commit the separator.
     * Reads the last word from InputConnection instead of relying on currentWord.
     * @return true if we handled the key (committed text); false to let normal flow continue.
     */
    private fun applyAutocorrectOnSeparator(code: Int): Boolean {
        val ic = currentInputConnection ?: run {
            Log.w(TAG, "⚠️ No InputConnection available")
            return false
        }
        
        // Look back to capture the last token (64 chars is plenty for a single word)
        val before = ic.getTextBeforeCursor(64, 0) ?: run {
            Log.w(TAG, "⚠️ Could not get text before cursor")
            // Still commit the separator
            if (code > 0) ic.commitText(String(Character.toChars(code)), 1)
            return true
        }
        
        // Last run of letters/apostrophes (works well for English and Latin-script languages)
        val match = Regex("([\\p{L}']+)$").find(before)
        if (match == null) {
            Log.d(TAG, "🔍 No word found before cursor: '$before'")
            if (code == KEYCODE_SPACE) {
                // ✅ Use double-space period logic for space even when no word found
                commitSpaceWithDoublePeriod(ic)
            } else if (code > 0) {
                val charStr = String(Character.toChars(code))
                Log.d(TAG, "💾 Committing separator: '$charStr' (code=$code)")
                ic.commitText(charStr, 1)
                lastSpaceTimestamp = 0L
            }
            return true
        }
        
        val original = match.groupValues[1]
        Log.d(TAG, "🔍 Found word: '$original' (length=${original.length})")
        
        // ✅ FIX 8: Enhanced loop prevention - Check explicitly against the persisted rejected word
        // This prevents the "Undo -> Type Space -> Re-autocorrect" loop
        if (original.equals(rejectedOriginal, ignoreCase = true)) {
            Log.d(TAG, "🚫 FIX 8: Skipping autocorrect - word was previously rejected: '$original'")
            
            // Just commit the separator (Space/Punctuation)
            if (code == KEYCODE_SPACE) {
                commitSpaceWithDoublePeriod(ic)
                applyPostSpaceEffects(ic)
            } else if (code > 0) {
                ic.commitText(String(Character.toChars(code)), 1)
                lastSpaceTimestamp = 0L
            }
            
            // Add to history as-is
            wordHistory.add(original)
            if (wordHistory.size > 20) wordHistory.removeAt(0)
            return true
        }
        
        // ✅ CHECK FOR CUSTOM DICTIONARY EXPANSION FIRST (before autocorrect!)
        if (dictionaryEnabled) {
            val expansion = dictionaryManager.getExpansion(original)
            if (expansion != null) {
                val expandedText = expansion.expansion
                val separator = if (code > 0) String(Character.toChars(code)) else " "
                
                ic.beginBatchEdit()
                ic.deleteSurroundingText(original.length, 0)
                ic.commitText("$expandedText$separator", 1)
                ic.endBatchEdit()
                
                // ✅ Track expansion for backspace undo
                lastExpansion = original to expandedText
                
                dictionaryManager.incrementUsage(original)
                
                // ✅ Add expanded text to word history for next-word prediction
                wordHistory.add(expandedText)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
                
                Log.d(TAG, "⚙️ Shortcut expanded: '$original' → '$expandedText'")
                Log.d(TAG, "📚 Added '$expandedText' to word history (size=${wordHistory.size})")
                lastSpaceTimestamp = if (code == KEYCODE_SPACE) SystemClock.uptimeMillis() else 0L
                return true  // Expansion done, don't run autocorrect
            }
        }
        
        // ✅ FIX 7: Disable autocorrect on 1-2 letter words
        if (original.isEmpty() || original.length < 3) {
            // Too short to correct, just commit separator
            Log.d(TAG, "🔍 Word too short to correct (< 3 letters): '$original'")
            if (code == KEYCODE_SPACE) {
                commitSpaceWithDoublePeriod(ic)
            } else if (code > 0) {
                ic.commitText(String(Character.toChars(code)), 1)
                lastSpaceTimestamp = 0L
            } else {
                lastSpaceTimestamp = 0L
            }
            return true
        }
        
        val autocorrectEnabled = isAutoCorrectEnabled()
        Log.d(TAG, "🔍 applyAutocorrectOnSeparator: autocorrect=$autocorrectEnabled, code=$code")
        
            if (!autocorrectEnabled) {
                Log.w(TAG, "⚠️ Autocorrect is DISABLED in settings")
                // Still commit the separator character
                if (code == KEYCODE_SPACE) {
                    Log.d(TAG, "💾 Committing separator (autocorrect OFF): ' ' (space)")
                    commitSpaceWithDoublePeriod(ic)
                } else if (code > 0) {
                    val charStr = String(Character.toChars(code))
                    Log.d(TAG, "💾 Committing separator (autocorrect OFF): '$charStr' (code=$code)")
                    ic.commitText(charStr, 1)
                    lastSpaceTimestamp = 0L
                } else {
                    lastSpaceTimestamp = 0L
                }
                return true
            }
        
        // Check if unified autocorrect engine is ready (Firebase data loaded)
        if (!autocorrectEngine.hasLanguage(currentLanguage)) {
            Log.w(TAG, "⚠️ UnifiedAutocorrectEngine not ready for $currentLanguage - Firebase data not loaded")
            // Engine not ready, just commit separator
            if (code == KEYCODE_SPACE) {
                commitSpaceWithDoublePeriod(ic)
            } else if (code > 0) {
                ic.commitText(String(Character.toChars(code)), 1)
                lastSpaceTimestamp = 0L
            } else {
                lastSpaceTimestamp = 0L
            }
            return true
        }
        
        Log.d(TAG, "🔍 Getting best suggestion for: '$original'")
        
        try {
            // ========== GBOARD-STYLE TIMING CHECK ==========
            // Skip autocorrect if user typed fast or pressed space quickly
            val skipDueToTiming = autocorrectEngine.isTypingFast() || autocorrectEngine.isSpacePressedFast()
            if (skipDueToTiming) {
                Log.d(TAG, "⚡ Fast typing detected for '$original' → committing as-is")
                autocorrectEngine.clearTimingHistory()
                wordHistory.add(original)
                if (wordHistory.size > 20) wordHistory.removeAt(0)
                if (code == KEYCODE_SPACE) {
                    commitSpaceWithDoublePeriod(ic)
                    applyPostSpaceEffects(ic)
                } else if (code > 0) {
                    ic.commitText(String(Character.toChars(code)), 1)
                    lastSpaceTimestamp = 0L
                }
                return true
            }
            
            val best = autocorrectEngine.getBestSuggestion(original, currentLanguage)
            Log.d(TAG, "🔍 Best suggestion: '$best' for '$original'")
            
            if (best != null && ::userDictionaryManager.isInitialized) {
                if (userDictionaryManager.isBlacklisted(original, best)) {
                    Log.d(TAG, "🚫 Suggestion '$best' for '$original' is blacklisted; skipping autocorrect")
                    if (code == KEYCODE_SPACE) {
                        commitSpaceWithDoublePeriod(ic)
                        applyPostSpaceEffects(ic)
                    } else if (code > 0) {
                        ic.commitText(String(Character.toChars(code)), 1)
                        lastSpaceTimestamp = 0L
                    } else {
                        lastSpaceTimestamp = 0L
                    }
                    correctionRejected = true
                    lastCorrection = null // Clear to disable undo
                    wordHistory.add(original)
                    if (wordHistory.size > 20) {
                        wordHistory.removeAt(0)
                    }
                    return true
                }
            }
            
            if (best == null) {
                Log.d(TAG, "🔍 No suggestion available")
                
                // ✅ Add original word to history for next-word prediction
                wordHistory.add(original)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
                Log.d(TAG, "📚 Added '$original' to word history (size=${wordHistory.size})")
                
                // No suggestion; just commit the separator
                if (code == KEYCODE_SPACE) {
                    commitSpaceWithDoublePeriod(ic)
                    applyPostSpaceEffects(ic)
                } else if (code > 0) {
                    ic.commitText(String(Character.toChars(code)), 1)
                    lastSpaceTimestamp = 0L
                } else {
                    lastSpaceTimestamp = 0L
                }
                return true
            }
            
            // Check if user previously rejected this correction
            if (correctionRejected && lastCorrection?.first.equals(original, ignoreCase = true)) {
                Log.d(TAG, "🚫 Skipping autocorrect — user rejected previous correction for '$original'")
                correctionRejected = false
                lastCorrection = null
                
                // ✅ Add original word to history for next-word prediction
                wordHistory.add(original)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
                Log.d(TAG, "📚 Added '$original' to word history (size=${wordHistory.size})")
                
                // Just commit the separator
                if (code == KEYCODE_SPACE) {
                    commitSpaceWithDoublePeriod(ic)
                    applyPostSpaceEffects(ic)
                } else if (code > 0) {
                    ic.commitText(String(Character.toChars(code)), 1)
                    lastSpaceTimestamp = 0L
                } else {
                    lastSpaceTimestamp = 0L
                }
                return true
            }
            
            val confidence = autocorrectEngine.getConfidence(original, best)
            // ✅ PATCH 4: Dynamic confidence threshold based on word length (Gboard V3)
            val requiredConf = autocorrectEngine.requiredConfidence(original)
            val shouldReplace = confidence >= requiredConf && !best.equals(original, ignoreCase = true)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🔍 Confidence: $confidence, shouldReplace: $shouldReplace (threshold: $requiredConf for '${original}')")
            }
            
            ic.beginBatchEdit()
            if (shouldReplace) {
                val replaced = preserveCase(best, original)
                ic.deleteSurroundingText(original.length, 0)
                ic.commitText(replaced, 1)
                
                // Enhanced single-line logging
                Log.d(TAG, "⚙️ Applying correction: '$original'→'$replaced' (conf=$confidence, lang=$currentLanguage)")
                
                // Store correction for undo functionality (non-null = undo available)
                lastCorrection = Pair(original, replaced)
                correctionRejected = false
                Log.d(TAG, "💾 Stored last correction for undo: '$original' → '$replaced'")
                
                // ✅ Add corrected word to history for next-word prediction
                wordHistory.add(replaced)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
                Log.d(TAG, "📚 Added '$replaced' to word history (size=${wordHistory.size})")
                
                // 🔥 CRITICAL: Learn from this correction for adaptive improvement
                try {
                    autocorrectEngine.onCorrectionAccepted(original, best, currentLanguage)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error learning from correction", e)
                }
            } else {
                // ✅ No replacement, but still add original word to history
                wordHistory.add(original)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
                Log.d(TAG, "📚 Added '$original' to word history (size=${wordHistory.size})")
            }
            // Always commit the separator if it's a printable character
            if (code == KEYCODE_SPACE) {
                commitSpaceWithDoublePeriod(ic)
                applyPostSpaceEffects(ic)
            } else if (code > 0) {
                ic.commitText(String(Character.toChars(code)), 1)
                lastSpaceTimestamp = 0L
            } else {
                lastSpaceTimestamp = 0L
            }
            ic.endBatchEdit()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyAutocorrectOnSeparator", e)
            // Fallback: just commit separator
            if (code == KEYCODE_SPACE) {
                commitSpaceWithDoublePeriod(ic)
                applyPostSpaceEffects(ic)
            } else if (code > 0) {
                ic.commitText(String(Character.toChars(code)), 1)
                lastSpaceTimestamp = 0L
            }
            return true
        }
    }
    
    private fun shouldCapitalizeAfterSpace(ic: InputConnection): Boolean {
        if (!autoCapitalizationEnabled) return false
        val textBefore = ic.getTextBeforeCursor(10, 0)?.toString() ?: ""
        
        // Capitalize at start of input
        if (textBefore.isEmpty()) return true
        
        // Capitalize after sentence-ending punctuation
        val trimmed = textBefore.trimEnd()
        return trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")
    }
    
    private fun handleSmartPunctuation(ic: InputConnection, code: Char, context: String) {
        // Smart spacing around punctuation
        when (code) {
            '.', '!', '?' -> {
                // Remove extra spaces before sentence-ending punctuation
                if (context.endsWith(" ")) {
                    ic.deleteSurroundingText(1, 0)
                }
                ic.commitText(code.toString(), 1)
            }
            ',' -> {
                // Handle comma spacing
                ic.commitText(code.toString(), 1)
            }
            else -> ic.commitText(code.toString(), 1)
        }
    }
    
    // ❌ REMOVED: performAutoCorrection() - Dead code that could accidentally trigger autocorrect during typing
    // Autocorrect ONLY happens on SPACE press via handleSpace() -> runAutocorrectOnLastWord()
    
    
    // For double-backspace revert functionality
    private var lastBackspaceTime = 0L

    private fun tryDeleteLastSwipeCommit(ic: InputConnection): Boolean {
        if (lastCommittedSwipeWord.isBlank()) return false

        val swipeWord = lastCommittedSwipeWord
        val expectedTail = if (lastSwipeAutoInsertedSpace) {
            "$swipeWord "
        } else {
            swipeWord
        }

        val buffer = ic.getTextBeforeCursor(expectedTail.length + 1, 0)?.toString() ?: return false
        if (!buffer.endsWith(expectedTail)) return false

        var batchStarted = false
        return try {
            batchStarted = ic.beginBatchEdit()
            ic.deleteSurroundingText(expectedTail.length, 0)
            if (batchStarted) {
                ic.endBatchEdit()
            }

            // Remove swipe word from history if it was the last entry
            if (wordHistory.isNotEmpty() && wordHistory.lastOrNull()?.equals(swipeWord, ignoreCase = true) == true) {
                wordHistory.removeAt(wordHistory.lastIndex)
            }

            lastCommittedSwipeWord = ""
            lastSwipeAutoInsertedSpace = true
            currentWord = ""
            Log.d(TAG, "🧼 Deleted swipe commit with single backspace: '$swipeWord'")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting swipe commit", e)
            if (batchStarted) {
                try {
                    ic.endBatchEdit()
                } catch (_: Exception) {
                    // Ignore secondary failures
                }
            }
            false
        }
    }
    
    private fun handleBackspace(ic: InputConnection) {
        // Handle emoji search backspace
        if (emojiSearchActive && isEmojiPanelVisible) {
            unifiedPanelManager?.removeEmojiSearchCharacter()
            updateKeyboardState()
            return
        }
        
        // ✅ FIX: Delete entire swiped word on backspace (Gboard behavior)
        if (lastInputWasSwipe && lastCommittedSwipeWord.isNotEmpty()) {
            val wordLength = lastCommittedSwipeWord.length
            ic.deleteSurroundingText(wordLength, 0)
            Log.d(TAG, "🗑️ Deleted entire swipe word: '$lastCommittedSwipeWord' ($wordLength chars)")
            
            // Reset swipe state
            lastInputWasSwipe = false
            lastCommittedSwipeWord = ""
            currentWord = ""
            
            // Update suggestions
            if (areSuggestionsActive()) {
                updateAISuggestions()
            }
            return
        }
        
        // ✅ Reset swipe flag on backspace
        lastInputWasSwipe = false

        val currentTime = System.currentTimeMillis()
        
        // Handle swipe commit deletion shortcut
        if (tryDeleteLastSwipeCommit(ic)) {
            if (areSuggestionsActive()) {
                updateAISuggestions()
            }
            return
        }

        // Handle dictionary expansion undo on backspace (priority over autocorrect undo)
        lastExpansion?.let { (shortcut, expansion) ->
            val textBefore = ic.getTextBeforeCursor(expansion.length + 5, 0)?.toString() ?: ""
            // Check if expansion is still present (with or without trailing space/punctuation)
            if (textBefore.endsWith(expansion) || textBefore.endsWith("$expansion ") || 
                textBefore.matches(Regex(".*${Regex.escape(expansion)}[\\s,.!?;:]$"))) {
                ic.beginBatchEdit()
                // Calculate how much to delete (expansion + any trailing character)
                val deleteLength = when {
                    textBefore.endsWith("$expansion ") -> expansion.length + 1
                    textBefore.matches(Regex(".*${Regex.escape(expansion)}[\\s,.!?;:]$")) -> expansion.length + 1
                    else -> expansion.length
                }
                ic.deleteSurroundingText(deleteLength, 0)
                // Restore original shortcut
                ic.commitText(shortcut, 1)
                ic.endBatchEdit()
                
                Log.d(TAG, "↩️ Undo dictionary expansion: reverted '$expansion' → '$shortcut'")
                
                // Clear expansion tracking
                lastExpansion = null
                currentWord = shortcut
                
                // Update suggestions
                if (areSuggestionsActive()) {
                    updateAISuggestions()
                }
                return
            }
        }
        
        // Handle Gboard-style undo autocorrect on first backspace
        // ✅ SIMPLIFIED: Check lastCorrection directly (non-null = undo available)
        lastCorrection?.let { (original, corrected) ->
            // Check if the corrected word is still present (user hasn't typed anything else)
            val textBefore = ic.getTextBeforeCursor(corrected.length + 5, 0)?.toString() ?: ""
            if (textBefore.endsWith(corrected) || textBefore.endsWith("$corrected ")) {
                ic.beginBatchEdit()
                // Delete the corrected word (and trailing space if present)
                val deleteLength = if (textBefore.endsWith(" ")) corrected.length + 1 else corrected.length
                ic.deleteSurroundingText(deleteLength, 0)
                // Restore original word
                ic.commitText(original, 1)
                ic.endBatchEdit()
                
                Log.d(TAG, "↩️ Undo autocorrect: reverted '$corrected' → '$original'")
                
                // Mark as rejected and clear undo (set to null to disable)
                correctionRejected = true
                lastCorrection = null
                currentWord = original
                
                // ✅ Persist the rejection so hitting space again doesn't re-trigger it
                rejectedOriginal = original
                
                // Phase 5: Learn from rejection
                onCorrectionRejected(original, corrected)
                
                // ✅ ENHANCEMENT: After backspace undo, show ONLY the original word - no other suggestions
                // User wants their typed word back, not dictionary alternatives
                if (areSuggestionsActive()) {
                    // Show only the original word in suggestion bar, no alternatives
                    val originalWordOnly = listOf(original)
                    updateSuggestionUI(originalWordOnly)
                    Log.d(TAG, "📝 Showing only original word after undo: '$original'")
                } else {
                    clearSuggestions()
                }
                
                // ✅ CRITICAL FIX: Explicit return to prevent falling through to standard backspace
                return
            }
        }
        
        // Handle slide-to-delete mode
        if (isSlideToDeleteModeActive) {
            deleteLastWord(ic)
            return
        }
        
        lastBackspaceTime = System.currentTimeMillis()
        
        val selectedText = ic.getSelectedText(0)
        if (TextUtils.isEmpty(selectedText)) {
            // Enhanced backspace - handle emoji clusters and surrogate pairs
            val deletedLength = deleteCharacterOrCluster(ic)
            
            // Update current word tracking
            if (currentWord.isNotEmpty()) {
                // Handle multi-byte character deletion
                if (deletedLength > 1) {
                    // Likely deleted an emoji or special character, clear current word
                    currentWord = ""
                } else {
                    currentWord = currentWord.dropLast(1)
                }
            } else {
                // If no current word, rebuild from text
                rebuildCurrentWord(ic)
            }
        } else {
            // Delete selected text
            ic.commitText("", 1)
            currentWord = ""
        }
        
        // Check if user is manually editing a corrected word (rejection signal)
        if (!correctionRejected) {
            lastCorrection?.let { (original, corrected) ->
                val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                
                // If user is deleting characters from the corrected word, mark as rejected
                if (textBefore.contains(original.take(2)) || currentWord.startsWith(original.take(2))) {
                    correctionRejected = true
                    lastCorrection = null // Clear to disable undo
                    // ✅ Persist rejection here too
                    rejectedOriginal = original
                    
                    Log.d(TAG, "🚫 User manually rejected autocorrect '$original' → '$corrected'")
                    
                    // Phase 5: Learn from rejection
                    onCorrectionRejected(original, corrected)
                }
            }
        }
        
        // Update suggestions after backspace
        if (areSuggestionsActive()) {
            updateAISuggestions()
        }
    }
    
    /**
     * Delete the last word (used in slide-to-delete mode)
     */
    private fun deleteLastWord(ic: InputConnection) {
        try {
            val textBeforeCursor = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val wordBoundaryRegex = "\\S+\\s*$".toRegex()
            val lastWordMatch = wordBoundaryRegex.find(textBeforeCursor)
            
            if (lastWordMatch != null) {
                val deleteLength = lastWordMatch.value.length
                ic.deleteSurroundingText(deleteLength, 0)
                Log.d(TAG, "Slide-to-delete removed: '${lastWordMatch.value.trim()}'")
                
                // Clear current word
                currentWord = ""
                
                // Provide haptic feedback
                performAdvancedHapticFeedback(Keyboard.KEYCODE_DELETE)
                
                // Update suggestions
                if (areSuggestionsActive()) {
                    updateAISuggestions()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in slide-to-delete", e)
            // Fallback to normal backspace
            ic.deleteSurroundingText(1, 0)
        }
    }
    
    /**
     * Activate slide-to-delete mode
     */
    fun activateSlideToDelete() {
        isSlideToDeleteModeActive = true
        Log.d(TAG, "Slide-to-delete mode activated")
    }
    
    /**
     * Deactivate slide-to-delete mode
     */
    fun deactivateSlideToDelete() {
        isSlideToDeleteModeActive = false
        Log.d(TAG, "Slide-to-delete mode deactivated")
    }
    
    /**
     * Enhanced backspace that properly handles emoji clusters and surrogate pairs
     */
    private fun deleteCharacterOrCluster(ic: InputConnection): Int {
        try {
            // Use the cursor-aware text handler for consistent backspace behavior
            return CursorAwareTextHandler.performBackspace(ic)
        } catch (e: Exception) {
            Log.e(TAG, "Error in enhanced backspace", e)
            // Fallback to simple deletion
            ic.deleteSurroundingText(1, 0)
            return 1
        }
    }
    
    private fun rebuildCurrentWord(ic: InputConnection) {
        try {
            val textBefore = ic.getTextBeforeCursor(50, 0)
            if (textBefore != null) {
                val text = textBefore.toString()
                val words = text.split("\\s+".toRegex())
                if (words.isNotEmpty() && words.last().isNotEmpty()) {
                    // Check if the last "word" contains only letters
                    val lastWord = words.last()
                    if (lastWord.matches("[a-zA-Z]+".toRegex())) {
                        currentWord = lastWord.lowercase()
                    }
                }
            }
        } catch (e: Exception) {
            // If rebuilding fails, just clear current word
            currentWord = ""
        }
    }
    
    /**
     * ✅ SPACE BUTTON HANDLER
     * 
     * AUTOCORRECT BEHAVIOR:
     * - Autocorrection ONLY happens when user presses SPACE button
     * - During typing, only suggestions are shown (no auto-replacement)
     * - If user presses BACKSPACE after autocorrect, original typed word is restored
     * - No other dictionary words are suggested after undo - user gets exactly what they typed
     */
    private fun handleSpace(ic: InputConnection) {
        var dictionaryExpanded = false
        
        // ✅ FIX: If we just finished a swipe (currentWord is set), finalize it
        if (currentWord.isNotEmpty() && lastCommittedSwipeWord == currentWord) {
            // Space finalizes the swipe word - learn it and add to history
            onWordCommitted(currentWord)
            wordHistory.add(currentWord)
            if (wordHistory.size > 20) {
                wordHistory.removeAt(0)
            }
            if (::unifiedSuggestionController.isInitialized) {
                unifiedSuggestionController.onWordCommitted()
            }
            currentWord = ""
            lastCommittedSwipeWord = ""
            lastInputWasSwipe = false
            
            // Commit the space
            commitSpaceWithDoublePeriod(ic)
            applyPostSpaceEffects(ic)
            
            // ✅ This triggers Next Word Prediction
            updateAISuggestions()
            return
        }
        
        // ✅ Reset swipe flag on any space press
        lastInputWasSwipe = false
        lastCommittedSwipeWord = ""
        
        // Check for dictionary expansion FIRST (before processing word)
        if (currentWord.isNotEmpty() && dictionaryEnabled) {
            val expansion = dictionaryManager.getExpansion(currentWord)
            if (expansion != null) {
                checkDictionaryExpansion(currentWord)
                dictionaryExpanded = true
            }
            // Clear current word after expansion attempt
            currentWord = ""
        } else {
            // Process current word normally if no expansion
            if (currentWord.isNotEmpty()) {
                finishCurrentWord()
            }
        }
        
        // ✅ FIX 2: Run autocorrect on SPACE press only
        // This is the ONLY place where autocorrect is triggered during typing
        if (!dictionaryExpanded && isAutoCorrectEnabled()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🔍 SPACE pressed - running autocorrect on last word")
            }
            runAutocorrectOnLastWord(ic)
        }
        
        // Only add space if dictionary didn't expand (expansion includes space already)
        if (!dictionaryExpanded) {
            commitSpaceWithDoublePeriod(ic)
        }
        
        applyPostSpaceEffects(ic)
    }
    
    // ✅ FIX 2: Helper method to run autocorrect on the last word before cursor
    private fun runAutocorrectOnLastWord(ic: InputConnection) {
        try {
            // Get text before cursor
            val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
            
            // Extract last word (same logic as applyAutocorrectOnSeparator)
            val match = Regex("([\\p{L}']+)$").find(before) ?: return
            val original = match.groupValues[1]
            
            // ✅ FIX 7: Skip autocorrect on 1-2 letter words
            if (original.length < 3) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 Skipping autocorrect - word too short: '$original' (${original.length} chars)")
                }
                return
            }
            
            // ✅ FIX 8: Check if word was previously rejected
            if (original.equals(rejectedOriginal, ignoreCase = true)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🚫 Skipping autocorrect - word was previously rejected: '$original'")
                }
                return
            }
            
            // Check if engine is ready
            if (!::autocorrectEngine.isInitialized || !autocorrectEngine.hasLanguage(currentLanguage)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 Autocorrect engine not ready for $currentLanguage")
                }
                return
            }
            
            // Get best suggestion
            val best = autocorrectEngine.getBestSuggestion(original, currentLanguage)
            if (best == null || best.equals(original, ignoreCase = true)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 No autocorrect needed for: '$original'")
                }
                return
            }
            
            // Check blacklist
            if (::userDictionaryManager.isInitialized && userDictionaryManager.isBlacklisted(original, best)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🚫 Autocorrect blocked by blacklist: '$original' → '$best'")
                }
                return
            }
            
            // Apply the correction
            Log.d(TAG, "✏️ Autocorrecting: '$original' → '$best'")
            
            // ✅ FIX 9: Mark as committing text to prevent suggestion triggers
            isCommittingText = true
            ic.beginBatchEdit()
            ic.deleteSurroundingText(original.length, 0)
            ic.commitText(best, 1)
            ic.endBatchEdit()
            isCommittingText = false
            
            // Store for undo (non-null = undo available)
            lastCorrection = Pair(original, best)
            correctionRejected = false
            
            // Update word history
            wordHistory.add(best)
            if (wordHistory.size > 20) wordHistory.removeAt(0)
            
            Log.d(TAG, "✅ Autocorrect applied on SPACE: '$original' → '$best'")
        } catch (e: Exception) {
            isCommittingText = false
            Log.e(TAG, "❌ Error in runAutocorrectOnLastWord", e)
        }
    }
    
    // ✅ FIX 4: Helper method to run autocorrect on a specific committed word (e.g., after swipe)
    private fun runAutocorrectOnCommittedWord(ic: InputConnection, word: String) {
        try {
            // ✅ FIX 7: Skip autocorrect on 1-2 letter words
            if (word.length < 3) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 Skipping swipe autocorrect - word too short: '$word'")
                }
                return
            }
            
            // ✅ FIX 8: Check if word was previously rejected
            if (word.equals(rejectedOriginal, ignoreCase = true)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🚫 Skipping swipe autocorrect - word was previously rejected: '$word'")
                }
                return
            }
            
            // Check if engine is ready
            if (!::autocorrectEngine.isInitialized || !autocorrectEngine.hasLanguage(currentLanguage)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 Swipe autocorrect engine not ready for $currentLanguage")
                }
                return
            }
            
            // Get best suggestion
            val best = autocorrectEngine.getBestSuggestion(word, currentLanguage)
            if (best == null || best.equals(word, ignoreCase = true)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🔍 No swipe autocorrect needed for: '$word'")
                }
                return
            }
            
            // Check blacklist
            if (::userDictionaryManager.isInitialized && userDictionaryManager.isBlacklisted(word, best)) {
                Log.d(TAG, "🚫 Swipe autocorrect blocked by blacklist: '$word' → '$best'")
                return
            }
            
            // Apply the correction
            Log.d(TAG, "✏️ Swipe autocorrecting: '$word' → '$best'")
            
            // ✅ FIX 9: Mark as committing text to prevent suggestion triggers
            isCommittingText = true
            ic.beginBatchEdit()
            // Delete the word and the trailing space
            ic.deleteSurroundingText(word.length + 1, 0)
            // Commit corrected word with space
            ic.commitText("$best ", 1)
            ic.endBatchEdit()
            isCommittingText = false
            
            // Update tracking variables
            lastCommittedSwipeWord = best
            
            // Store for undo (non-null = undo available)
            lastCorrection = Pair(word, best)
            correctionRejected = false
            
            Log.d(TAG, "✅ Swipe autocorrect applied: '$word' → '$best'")
        } catch (e: Exception) {
            isCommittingText = false
            Log.e(TAG, "❌ Error in runAutocorrectOnCommittedWord", e)
        }
    }
    
    // ==================== PHASE 3: WORD LEARNING INTEGRATION ====================
    
    /**
     * Called when a word is committed (typed and accepted)
     * Phase 3: Automatic word learning for personalization
     */
    private fun onWordCommitted(word: String) {
        if (word.isBlank() || word.length < 2) return
        
        try {
            if (::userDictionaryManager.isInitialized) {
                userDictionaryManager.learnWord(word)
                Log.d(TAG, "✨ Learned word: '$word'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to learn word", e)
        }
    }
    
    /**
     * Phase 5: Called when user accepts an autocorrect suggestion
     * Positive learning for personalization
     */
    private fun onCorrectionAccepted(original: String, corrected: String) {
        try {
            if (::userDictionaryManager.isInitialized) {
                // Learn the corrected word
                userDictionaryManager.learnWord(corrected)
                
                // Also tell autocorrect engine
                if (::autocorrectEngine.isInitialized) {
                    autocorrectEngine.learnFromUser(original, corrected, currentLanguage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process accepted correction", e)
        }
    }
    
    /**
     * ✅ V3 PACK Section 5: Enhanced rejection handling
     * - Case-insensitive blacklisting
     * - Context-aware learning
     * - Weighted negative feedback
     */
    private fun onCorrectionRejected(original: String, corrected: String) {
        try {
            if (::userDictionaryManager.isInitialized) {
                // ✅ V3: Case-insensitive blacklist (handles "The" vs "the", etc.)
                userDictionaryManager.blacklistCorrection(
                    original.lowercase(), 
                    corrected.lowercase()
                )
                
                // Learn the original word as valid (preserve original case)
                userDictionaryManager.learnWord(original)
                
                // Tell autocorrect engine with weighted negative feedback
                if (::autocorrectEngine.isInitialized) {
                    autocorrectEngine.learnFromUser(original, original, currentLanguage)
                    
                    // ✅ V3: Clear accept counter for this correction (if any)
                    // This prevents auto-promotion of rejected corrections
                    autocorrectEngine.clearCache()
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🚫 Rejected correction: '$original' ≠ '$corrected' (blacklisted case-insensitively)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process rejected correction", e)
        }
    }
    
    // ==================== END PHASE 3 & 5 ====================
    
    private fun finishCurrentWord() {
        if (currentWord.isEmpty()) return
        
        // ✅ PERFORMANCE FIX: No correction logic during typing
        // Autocorrect ONLY happens on SPACE press via handleSpace() -> runAutocorrectOnLastWord()
        
        // Just add original word to history (no auto-correction during typing)
        wordHistory.add(currentWord)
        
        // Learn original word for user dictionary
        onWordCommitted(currentWord)
        
        // Clear timing history to avoid memory buildup
        if (::autocorrectEngine.isInitialized) {
            autocorrectEngine.clearTimingHistory()
        }
        
        // Clear current word
        currentWord = ""
        
        // Keep word history manageable
        if (wordHistory.size > 20) {
            wordHistory.removeAt(0)
        }
    }
    
    private fun getRecentContext(): List<String> {
        val start = max(0, wordHistory.size - 3)
        val context = wordHistory.subList(start, wordHistory.size)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔍 getRecentContext: wordHistory.size=${wordHistory.size}, context=$context")
        }
        return context
    }
    
    /**
     * Show visual feedback for auto-correction with underline effect
     */
    private fun showAutocorrectFeedback(correctedWord: String) {
        try {
            // Create a temporary visual indicator for the correction
            // This could be enhanced with actual underline rendering in the future
            Log.d(TAG, "Showing autocorrect feedback for: $correctedWord")
            
            // For now, just log the correction - UI feedback could be added later
            // In a full implementation, this might highlight the corrected word briefly
        } catch (e: Exception) {
            Log.e(TAG, "Error showing autocorrect feedback", e)
        }
    }
    
    
    private fun handleShift() {
        // Delegate to unified controller for shift handling
        if (::unifiedController.isInitialized) {
            unifiedController.handleShiftPress()
        } else if (::capsShiftManager.isInitialized) {
            capsShiftManager.handleShiftPress()
        } else {
            // Fallback to old implementation if manager not initialized
            handleShiftFallback()
        }
    }
    
    /**
     * Fallback shift handling for backward compatibility
     */
    private fun handleShiftFallback() {
        val now = System.currentTimeMillis()
        
        // Enhanced 3-State Shift Management: OFF -> ON -> CAPS -> OFF
        when (shiftState) {
            SHIFT_OFF -> {
                // Single tap: Activate shift for next character only
                shiftState = SHIFT_ON
                lastShiftPressTime = now
                showShiftFeedback("Shift ON - Next character uppercase")
            }
            SHIFT_ON -> {
                if (now - lastShiftPressTime < DOUBLE_TAP_TIMEOUT) {
                    // Double tap detected within timeout - activate caps lock
                    shiftState = SHIFT_CAPS
                    showShiftFeedback("CAPS LOCK - All characters uppercase")
                } else {
                    // Single tap after timeout - turn off shift
                    shiftState = SHIFT_OFF
                    lastShiftPressTime = now
                    showShiftFeedback("Shift OFF - Lowercase mode")
                }
            }
            SHIFT_CAPS -> {
                // Any tap from caps lock - turn off completely
                shiftState = SHIFT_OFF
                showShiftFeedback("CAPS LOCK OFF - Lowercase mode")
            }
        }
        
        // Update keyboard view with enhanced visual feedback
        updateShiftVisualState()
        
        // Provide haptic feedback for shift state changes
        performShiftHapticFeedback()
        
        // Maintain backward compatibility with existing caps variable
        caps = (shiftState != SHIFT_OFF)
        isShifted = caps
    }
    
    private fun updateShiftVisualState() {
        keyboardView?.let { view ->
            // Update the shift key visual state
            view.isShifted = (shiftState != SHIFT_OFF)
            
            // Key labels are now handled by SwipeKeyboardView drawing override
            view.invalidateAllKeys()
        }
    }
    
    private fun showShiftFeedback(message: String) {
        // Toast removed - shift feedback logged only
        Log.d(TAG, "Shift feedback: $message")
    }
    
    private fun performShiftHapticFeedback() {
        if (vibrationEnabled && vibrator != null) {
            try {
                val intensity = when (shiftState) {
                    SHIFT_OFF -> 15L      // Light vibration for turning off
                    SHIFT_ON -> 25L       // Medium vibration for shift on
                    SHIFT_CAPS -> 50L     // Strong vibration for caps lock
                    else -> 20L
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(intensity, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(intensity)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to provide shift haptic feedback: ${e.message}")
            }
        }
    }
    
    
    /**
     * Switch keyboard mode with CleverType-style cycling
     * Letters → Numbers → Symbols → Letters
     */
    private fun switchKeyboardMode(targetMode: KeyboardMode) {
        Log.d(TAG, "🔄 Switching from $currentKeyboardMode to $targetMode")
        
        // Save previous mode for emoji panel return
        if (currentKeyboardMode != KeyboardMode.EMOJI) {
            previousKeyboardMode = currentKeyboardMode
        }
        
        when (targetMode) {
            KeyboardMode.LETTERS -> {
                // ✅ Use unified controller for consistent layout loading
                unifiedController.buildAndRender(currentLanguage, LanguageLayoutAdapter.KeyboardMode.LETTERS, showNumberRow)
            }
            
            KeyboardMode.NUMBERS -> {
                // LEGACY: Old XML-based system - no longer used
                // Now handled by UnifiedKeyboardView with JSON layouts
                // Use SYMBOLS mode instead
                unifiedController.buildAndRender(currentLanguage, LanguageLayoutAdapter.KeyboardMode.SYMBOLS, false)
            }
            
            KeyboardMode.SYMBOLS -> {
                // ✅ Use unified controller for consistent layout loading
                unifiedController.buildAndRender(currentLanguage, LanguageLayoutAdapter.KeyboardMode.SYMBOLS, false)
            }
            
            KeyboardMode.EXTENDED_SYMBOLS -> {
                // ✅ Use unified controller for consistent layout loading
                unifiedController.buildAndRender(currentLanguage, LanguageLayoutAdapter.KeyboardMode.EXTENDED_SYMBOLS, false)
            }
            
            KeyboardMode.DIALER -> {
                // ✅ Use unified controller for consistent layout loading
                unifiedController.buildAndRender(currentLanguage, LanguageLayoutAdapter.KeyboardMode.DIALER, false)
            }
            
            KeyboardMode.EMOJI -> {
                handleEmojiToggle()
                return  // Don't update currentKeyboardMode yet, handleEmojiToggle will do it
            }
        }
        
        currentKeyboardMode = targetMode
        rebindKeyboardListener()  // Ensure listener is bound after layout change
        refreshSwipeCapability("mode=$targetMode")
    }
    
    /**
     * ⚡ PERFORMANCE: Use cached value instead of reading SharedPreferences every time
     * The cache is updated in applyLoadedSettings() when settings change
     */
    private fun getNumberRowEnabled(): Boolean {
        return showNumberRow
    }
    
    /**
     * ✅ FIXED: Load language layout with number row support
     * Enhanced version that respects user settings and properly synchronizes mode
     */
    private suspend fun loadLanguageLayout(langCode: String) {
        val showNumberRow = getNumberRowEnabled()
        
        // ✅ CRITICAL FIX: Use setKeyboardMode to ensure proper key code mapping
        withContext(Dispatchers.Main) {
            keyboardView?.let { view ->
                if (view is SwipeKeyboardView) {
                    view.currentLangCode = langCode
                    view.setKeyboardMode(LanguageLayoutAdapter.KeyboardMode.LETTERS, languageLayoutAdapter, showNumberRow)
                    view.setCurrentLanguage(languageManager.getLanguageDisplayName(langCode))
                    view.refreshTheme()
                    
                    // 🔧 FIX: Rebind swipe listener after layout rebuild
                    rebindKeyboardListener()
                    
                    // Force the parent container to remeasure and relayout
                    keyboardContainer?.requestLayout()
                    mainKeyboardLayout?.requestLayout()
                    
                    // Update the IME window to recalculate insets with new height
                    updateInputViewShown()
                    
                    Log.d(TAG, "✅ Layout loaded for $langCode with numberRow=$showNumberRow")
                }
            }
        }

        autocorrectEngine.preloadLanguages(listOf(langCode))
        Log.d(TAG, "✅ Layout loaded for $langCode with numberRow=$showNumberRow")
    }
    
    /**
     * Cycle to next keyboard mode (CleverType behavior)
     * Letters → Symbols → Extended Symbols → Dialer → Letters
     */
    private fun cycleKeyboardMode() {
        val nextMode = when (currentKeyboardMode) {
            KeyboardMode.LETTERS -> KeyboardMode.SYMBOLS
            KeyboardMode.NUMBERS -> KeyboardMode.SYMBOLS
            KeyboardMode.SYMBOLS -> KeyboardMode.EXTENDED_SYMBOLS
            KeyboardMode.EXTENDED_SYMBOLS -> KeyboardMode.DIALER
            KeyboardMode.DIALER -> KeyboardMode.LETTERS
            KeyboardMode.EMOJI -> previousKeyboardMode
        }
        Log.d(TAG, "⚡ Cycling keyboard: $currentKeyboardMode → $nextMode")
        switchKeyboardMode(nextMode)
    }

    private fun cycleKeyboardModeReverse() {
        val previous = when (currentKeyboardMode) {
            KeyboardMode.LETTERS -> KeyboardMode.DIALER
            KeyboardMode.NUMBERS -> KeyboardMode.DIALER
            KeyboardMode.SYMBOLS -> KeyboardMode.LETTERS
            KeyboardMode.EXTENDED_SYMBOLS -> KeyboardMode.SYMBOLS
            KeyboardMode.DIALER -> KeyboardMode.EXTENDED_SYMBOLS
            KeyboardMode.EMOJI -> previousKeyboardMode
        }
        Log.d(TAG, "⚡ Cycling keyboard (reverse): $currentKeyboardMode → $previous")
        switchKeyboardMode(previous)
    }
    
    /**
     * Return to letters mode (ABC button)
     */
    private fun returnToLetters() {
        Log.d(TAG, "🔤 Returning to letters mode")
        switchKeyboardMode(KeyboardMode.LETTERS)
    }
    
    /**
     * LEGACY FUNCTION - NO LONGER USED
     * Old XML-based keyboard resource mapping
     * Replaced by JSON-based LanguageLayoutAdapter system
     */
    @Deprecated("Use LanguageLayoutAdapter with JSON keymaps instead")
    private fun getKeyboardResourceForLanguage(language: String, withNumbers: Boolean): Int {
        // Return dummy value - this function is no longer called
        // All layouts now loaded via UnifiedKeyboardView + JSON
        return 0
    }
    
    private fun shouldInsertDoubleSpacePeriod(ic: InputConnection, now: Long): Boolean {
        if (!doubleSpacePeriodEnabled) {
            Log.d(TAG, "🔍 Double-space period: DISABLED")
            return false
        }
        
        if (lastSpaceTimestamp == 0L) {
            Log.d(TAG, "🔍 Double-space period: No previous space timestamp")
            return false
        }
        
        val timeDiff = now - lastSpaceTimestamp
        if (timeDiff > DOUBLE_SPACE_TIMEOUT_MS) {
            Log.d(TAG, "🔍 Double-space period: Timeout exceeded (${timeDiff}ms > ${DOUBLE_SPACE_TIMEOUT_MS}ms)")
            return false
        }
        
        // Get more context to check for valid double-space scenario
        val before = ic.getTextBeforeCursor(20, 0)?.toString()
        if (before == null) {
            Log.d(TAG, "🔍 Double-space period: Could not get text before cursor")
            return false
        }
        
        Log.d(TAG, "🔍 Double-space period: Checking text before cursor: '$before'")
        
        // Check if text ends with at least one space
        if (!before.endsWith(" ")) {
            Log.d(TAG, "🔍 Double-space period: Text does not end with space")
            return false
        }
        
        // Remove trailing spaces to find the preceding character
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) {
            Log.d(TAG, "🔍 Double-space period: Text is empty after trimming spaces")
            return false
        }
        
        val precedingChar = trimmed.last()
        
        // Only insert period if preceding character is a letter or digit
        // This prevents inserting period after punctuation or other characters
        if (!precedingChar.isLetterOrDigit()) {
            Log.d(TAG, "🔍 Double-space period: Preceding char '$precedingChar' is not letter/digit")
            return false
        }
        
        Log.d(TAG, "✅ Double-space period: All checks passed! Time diff: ${timeDiff}ms")
        return true
    }
    
    private fun commitSpaceWithDoublePeriod(ic: InputConnection) {
        val now = SystemClock.uptimeMillis()
        val shouldConvert = shouldInsertDoubleSpacePeriod(ic, now)
        
        if (shouldConvert) {
            Log.d(TAG, "✍️ ✅ Double-space detected → inserting period")
            // Delete the last space and replace with period + space
            val deleted = ic.deleteSurroundingText(1, 0)
            if (deleted) {
                ic.commitText(". ", 1)
                Log.d(TAG, "✍️ ✅ Period inserted: '. '")
            } else {
                // Fallback: just commit period + space
                ic.commitText(". ", 1)
                Log.d(TAG, "✍️ ⚠️ Could not delete previous space, inserted '. ' anyway")
            }
            lastSpaceTimestamp = 0L  // Reset timer after conversion
        } else {
            // Commit space normally and arm the timer for next space
            ic.commitText(" ", 1)
            lastSpaceTimestamp = now
            Log.d(TAG, "✍️ Space committed (timer armed: ${now}ms, enabled=$doubleSpacePeriodEnabled)")
        }
    }

    private fun applyPostSpaceEffects(ic: InputConnection) {
        if (::unifiedController.isInitialized) {
            unifiedController.handleSpacePress()
        } else if (::capsShiftManager.isInitialized) {
            val inputType = currentInputEditorInfo?.inputType ?: 0
            capsShiftManager.handleSpacePress(ic, inputType)
        } else {
            caps = shouldCapitalizeAfterSpace(ic)
            keyboardView?.isShifted = caps
        }

        if (areSuggestionsActive()) {
            updateAISuggestions()
        } else {
            clearSuggestions()
        }
    }
    
    private fun areSuggestionsActive(): Boolean {
        return suggestionsEnabled && (aiSuggestionsEnabled || autoFillSuggestionsEnabled || clipboardSuggestionEnabled)
    }
    
    // ✅ FIX 1: Helper function to get current text from input connection
    private fun getCurrentText(): String? {
        val ic = currentInputConnection ?: return null
        val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        return textBefore
    }
    
    // ✅ FIX 1: Helper function to detect if last character is space or punctuation
    private fun lastCharIsSpaceOrPunctuation(): Boolean {
        val text = getCurrentText() ?: return false
        if (text.isEmpty()) return false
        val lastChar = text.last()
        return lastChar.isWhitespace() || ".,!?;:".contains(lastChar)
    }
    
    // PERFORMANCE: Debounced wrapper for suggestion updates
    private fun updateAISuggestions() {
        // ✅ FIX 9: Do not trigger suggestions during programmatic text commits
        if (isCommittingText) {
            Log.d(TAG, "🚫 Skipping suggestion update - programmatic text commit in progress")
            return
        }
        
        // Guard: Check if UnifiedKeyboardView is ready
        if (!unifiedViewReady || unifiedKeyboardView == null) {
            Log.d(TAG, "🕐 Deferring AI suggestions until UnifiedKeyboardView is ready")
            return
        }
        
        if (!areSuggestionsActive()) {
            suggestionUpdateJob?.cancel()
            clearSuggestions()
            return
        }
        
        // ✅ FIX: Always fetch suggestions, even if last char is space
        // The logic inside fetchUnifiedSuggestions handles empty words correctly (next-word prediction)
        suggestionUpdateJob?.cancel()
        suggestionUpdateJob = coroutineScope.launch {
            delay(suggestionDebounceMs)
            if (isActive) {
                fetchUnifiedSuggestions()
            }
        }
    }
    
    
    /**
     * Load dictionaries from assets
     */
    private fun loadDictionaries() {
        try {
            // Load common words
            val commonWordsJson = assets.open("dictionaries/common_words.json").bufferedReader().use { it.readText() }
            val commonWordsData = org.json.JSONObject(commonWordsJson)
            
            // Extract words array (check for both old and new format)
            val wordsList = mutableListOf<String>()
            val frequencyMap = mutableMapOf<String, Int>()
            
            if (commonWordsData.has("basic_words")) {
                // New format with categories
                val basicWordsArray = commonWordsData.getJSONArray("basic_words")
                for (i in 0 until basicWordsArray.length()) {
                    val wordObj = basicWordsArray.getJSONObject(i)
                    val word = wordObj.getString("word")
                    val frequency = wordObj.getInt("frequency")
                    wordsList.add(word)
                    frequencyMap[word] = frequency
                }
                
                // Load business words if available
                if (commonWordsData.has("business_words")) {
                    val businessWordsArray = commonWordsData.getJSONArray("business_words")
                    val businessList = mutableListOf<String>()
                    for (i in 0 until businessWordsArray.length()) {
                        val wordObj = businessWordsArray.getJSONObject(i)
                        val word = wordObj.getString("word")
                        val frequency = wordObj.getInt("frequency")
                        businessList.add(word)
                        wordsList.add(word)
                        frequencyMap[word] = frequency
                    }
                    businessWords = businessList
                }
            } else {
                // Old format - simple array
                val wordsArray = commonWordsData.getJSONArray("words")
                for (i in 0 until wordsArray.length()) {
                    wordsList.add(wordsArray.getString(i))
                }
            }
            commonWords = wordsList
            
            // Extract frequency data if available in old format
            if (commonWordsData.has("frequency")) {
                val frequencyData = commonWordsData.getJSONObject("frequency")
                val keys = frequencyData.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    frequencyMap[key] = frequencyData.getInt(key)
                }
            }
            wordFrequencies = frequencyMap
            
            // Load corrections
            val correctionsJson = assets.open("dictionaries/corrections.json").bufferedReader().use { it.readText() }
            val correctionsData = org.json.JSONObject(correctionsJson)
            
            // Extract corrections
            val correctionsObj = correctionsData.getJSONObject("corrections")
            val correctionsMap = mutableMapOf<String, String>()
            val correctionKeys = correctionsObj.keys()
            while (correctionKeys.hasNext()) {
                val key = correctionKeys.next()
                correctionsMap[key] = correctionsObj.getString(key)
            }
            corrections = correctionsMap
            
            // Extract contractions
            val contractionsObj = correctionsData.getJSONObject("contractions")
            val contractionsMap = mutableMapOf<String, List<String>>()
            val contractionKeys = contractionsObj.keys()
            while (contractionKeys.hasNext()) {
                val key = contractionKeys.next()
                val array = contractionsObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                contractionsMap[key] = list
            }
            contractions = contractionsMap
            
            // Load technology words
            try {
                val techWordsJson = assets.open("dictionaries/technology_words.json").bufferedReader().use { it.readText() }
                val techWordsData = org.json.JSONObject(techWordsJson)
                val techWordsArray = techWordsData.getJSONArray("technology_words")
                val techList = mutableListOf<String>()
                for (i in 0 until techWordsArray.length()) {
                    val wordObj = techWordsArray.getJSONObject(i)
                    val word = wordObj.getString("word")
                    val frequency = wordObj.getInt("frequency")
                    techList.add(word)
                    wordsList.add(word)
                    frequencyMap[word] = frequency
                }
                technologyWords = techList
            } catch (e: Exception) {
                Log.w(TAG, "Could not load technology words: ${e.message}")
            }
            
            // Combine all words for comprehensive suggestions
            allWords = (commonWords + businessWords + technologyWords).distinct()
            
            Log.d(TAG, "Loaded ${commonWords.size} common words, ${businessWords.size} business words, ${technologyWords.size} tech words, ${corrections.size} corrections, ${contractions.size} contractions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dictionaries from assets", e)
            // Fallback to basic words
            commonWords = listOf("the", "and", "to", "of", "in", "is", "it", "you", "that", "he", "was", "for", "on", "are", "as", "with", "his", "they", "i", "at", "be", "this", "have", "from", "or", "one", "had", "by", "word", "but", "not", "what", "all", "were", "we", "when", "your", "can", "said")
            corrections = mapOf("teh" to "the", "adn" to "and", "hte" to "the", "nad" to "and", "yuo" to "you", "taht" to "that")
        }
    }
    
    /**
     * Load dictionaries asynchronously to prevent UI jank
     */
    private fun loadDictionariesAsync() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔵 [Dictionary] Starting async dictionary load...")
                
                // Show loading indicator on main thread
                withContext(Dispatchers.Main) {
                    // On first launch, show async loading spinner until ready
                    // Toast removed - dictionary loading logged only
                }
                
                // Load dictionaries in background
                loadDictionaries()
                
                // Update UI on main thread when complete
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "🟢 [Dictionary] Async dictionary load completed")
                    // Toast removed - dictionary loaded logged only
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "🔴 [Dictionary] Error in async dictionary load", e)
                withContext(Dispatchers.Main) {
                    // Toast removed - dictionary load failure logged only
                }
            }
        }
    }
    
    /**
     * 🎯 NEW UNIFIED SUGGESTION METHOD
     * Simplified suggestion fetching using UnifiedSuggestionController
     * Replaces the complex updateAISuggestionsImmediate logic
     */
    private fun fetchUnifiedSuggestions() {
        // Guard: Check if controller is ready
        if (!::unifiedSuggestionController.isInitialized) {
            Log.w(TAG, "⚠️ UnifiedSuggestionController not initialized")
            return
        }
        
        // Guard: Check if UI is ready
        if (!unifiedViewReady || unifiedKeyboardView == null) {
            Log.d(TAG, "🕐 Deferring suggestions until UnifiedKeyboardView is ready")
            return
        }
        
        val word = currentWord.trim()
        val context = getRecentContext()
        
        // Launch coroutine to fetch suggestions
        coroutineScope.launch {
            try {
                // ✅ FIX: Allow single letter suggestions (e.g. 't' -> 'the', 'to', 'that')
                if (word.isNotEmpty()) {
                    // User is typing - show typing suggestions (works for single letters too)
                    if (::autocorrectEngine.isInitialized) {
                        val typingSuggestions = autocorrectEngine.suggestForTyping(word, context)
                        val suggestionTexts = typingSuggestions.take(suggestionCount).map { it.text }
                        
                        withContext(Dispatchers.Main) {
                            updateSuggestionUI(suggestionTexts)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateSuggestionUI(emptyList())
                        }
                    }
                    return@launch
                }
                
                // Word is empty - get full unified suggestions (includes next-word)
                val unifiedSuggestions = unifiedSuggestionController.getUnifiedSuggestions(
                    prefix = word,
                    context = context,
                    includeEmoji = true,
                    includeClipboard = word.isEmpty()  // Only show clipboard when no word typed
                )
                
                // Convert to simple string list for UI (take 3 or 4 based on settings)
                val suggestionTexts = unifiedSuggestions.take(suggestionCount).map { it.text }
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // ✅ Always update (even if empty) - UI will show defaults
                    updateSuggestionUI(suggestionTexts)
                }
                
        } catch (e: Exception) {
                Log.e(TAG, "❌ Error fetching unified suggestions", e)
                withContext(Dispatchers.Main) {
                    // Show defaults on error
                    updateSuggestionUI(emptyList())
                }
            }
        }
    }
    
    // OPTIMIZED: Fast suggestion UI update (main-thread safe)
    private fun updateSuggestionUI(suggestions: List<String>) {
        try {
            // Ensure UI updates happen on main thread to avoid "Posting sync barrier on non-owner thread" warnings
            mainHandler.post {
                val currentPrefix = currentWord
                val contextSnapshot = getRecentContext()
                val ensuredSuggestions = if (suggestions.isEmpty()) {
                    generateFallbackSuggestionTexts(currentPrefix, contextSnapshot)
                } else {
                    suggestions
                }
                val finalSuggestions = injectClipboardSuggestion(ensuredSuggestions)
                // ✅ Update UnifiedKeyboardView suggestions (primary method)
                if (!unifiedViewReady || unifiedKeyboardView == null) {
                    // Queue suggestions until view is ready
                    pendingSuggestions = finalSuggestions
                    pendingInputSnapshot = getCurrentInputText()
                    Log.d(TAG, "🕐 Queuing suggestions until UnifiedKeyboardView is ready")
                    return@post
                }

                val inputSnapshot = getCurrentInputText()
                unifiedKeyboardView?.updateEditorTextSnapshot(inputSnapshot)
                pendingInputSnapshot = null
                
                // UnifiedKeyboardView will handle visibility, count, and defaults internally
                unifiedKeyboardView?.updateSuggestions(finalSuggestions)
                val displayType = if (finalSuggestions.isEmpty()) "defaults" else "computed"
                Log.d(TAG, "✅ Updated UnifiedKeyboardView suggestions: ${finalSuggestions.size} ($displayType), displaying up to $suggestionCount")
                
                // ✅ Also update legacy suggestionContainer if present (backward compatibility)
                suggestionContainer?.let { container ->
                    val maxSlots = container.childCount
                    val count = suggestionCount.coerceIn(1, maxSlots)
                    for (i in 0 until maxSlots) {
                        val tv = container.getChildAt(i) as? TextView
                        if (i < finalSuggestions.size && i < count) {
                            tv?.text = finalSuggestions[i]
                            tv?.visibility = View.VISIBLE
                        } else if (i < count) {
                            tv?.text = ""
                            tv?.visibility = View.INVISIBLE // Keep space but hide
                        } else {
                            tv?.text = ""
                            tv?.visibility = View.GONE // Remove from layout
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to update suggestion UI", e)
        }
    }

    private fun generateFallbackSuggestionTexts(prefix: String, context: List<String>): List<String> {
        val limit = suggestionCount.coerceAtLeast(3)
        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun append(values: List<String>) {
            for (value in values) {
                if (value.isNotBlank() && seen.add(value.lowercase())) {
                    results.add(value)
                    if (results.size >= limit) return
                }
            }
        }

        if (::autocorrectEngine.isInitialized && autocorrectEngine.isLanguageLoaded(currentLanguage)) {
            val contextList = context
            if (prefix.isNotBlank()) {
                val typed = autocorrectEngine.suggestForTyping(prefix, contextList)
                    .map { it.text }
                append(typed)
            }

            if (results.size < limit) {
                val ngramFallback = autocorrectEngine.fallbackSuggestions(contextList, limit * 2, seen)
                    .map { it.text }
                val filtered = if (prefix.isNotBlank()) {
                    ngramFallback.filter { it.startsWith(prefix, ignoreCase = true) }
                } else ngramFallback
                append(filtered)
            }

            if (results.size < limit) {
                val topWords = autocorrectEngine.getTopWords(limit * 2, seen)
                    .map { it.text }
                val filteredTop = if (prefix.isNotBlank()) {
                    topWords.filter { it.startsWith(prefix, ignoreCase = true) }
                } else topWords
                append(filteredTop)
            }
        }

        if (results.size < limit) {
            append(listOf("I", "The", "You", "We", "It", "Thanks"))
        }

        return injectClipboardSuggestion(results).take(limit)
    }
    
    private fun clearSuggestions() {
        safeMain {
            // ✅ Show default suggestions (never truly "clear")
            // UnifiedKeyboardView will automatically show defaults when given empty list
            unifiedKeyboardView?.updateSuggestions(emptyList())
            if (suggestionsEnabled) {
                Log.d(TAG, "🗑️ Showing default suggestions")
            } else {
                Log.d(TAG, "🗑️ Suggestions hidden (toggle disabled)")
            }
        }
    }
    
    fun applySuggestion(suggestion: String) {
        Log.d(TAG, "applySuggestion called with: '$suggestion', currentWord: '$currentWord'")
        
        val ic = currentInputConnection ?: run {
            Log.e(TAG, "No input connection available")
            return
        }
        
        // Clean suggestion text (remove correction indicators)
        val cleanSuggestion = suggestion.replace("✓ ", "").trim()
        Log.d(TAG, "Clean suggestion: '$cleanSuggestion'")
        val wasClipboardSuggestion = pendingClipboardSuggestionText?.let { it == cleanSuggestion } == true
        
        // Check if suggestion is an emoji
        val isEmoji = cleanSuggestion.length <= 8 && cleanSuggestion.matches(Regex(".*[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Cn}].*"))
        
        if (isEmoji) {
            // Handle emoji suggestion - insert with proper cursor handling
            insertEmojiWithCursor(cleanSuggestion)
            
            // Log emoji usage for learning
            EmojiSuggestionEngine.logEmojiUsage(cleanSuggestion, getCurrentInputText())
            
            Log.d(TAG, "Applied emoji suggestion: '$cleanSuggestion'")
        } else {
            // Handle word suggestion - replace current word
            // ✅ FIX: Handle swipe word replacement - check if we're replacing a swipe word
            val isReplacingSwipeWord = lastCommittedSwipeWord.isNotEmpty() && currentWord == lastCommittedSwipeWord
            
            if (currentWord.isNotEmpty()) {
                Log.d(TAG, "Deleting current word of length: ${currentWord.length}")
                ic.deleteSurroundingText(currentWord.length, 0)
            }
            
            // ✅ FIX: Add space automatically after suggestion
            Log.d(TAG, "Committing text: '$cleanSuggestion' with trailing space")
            ic.commitText("$cleanSuggestion ", 1)
            
            // Enhanced learning from user selection 
            coroutineScope.launch {
                if (currentWord.isNotEmpty()) {
                    // Learn from user choice using enhanced autocorrect
                    autocorrectEngine.learnFromUser(currentWord, cleanSuggestion, currentLanguage)
                    
                    // Legacy AI bridge learning if available
                    if (isAIReady) {
                        val context = getRecentContext()
                        // Learning moved to UnifiedAI internal processing
                        Log.d(TAG, "AI correction learning: $currentWord -> $cleanSuggestion")
                    }
                }
            }
            
            // Update word history
            if (cleanSuggestion.isNotEmpty()) {
                wordHistory.add(cleanSuggestion)
                if (wordHistory.size > 20) {
                    wordHistory.removeAt(0)
                }
            }
            
            // ✅ FIX: Clear swipe state and current word
            lastCommittedSwipeWord = ""
            currentWord = ""
            
            // ✅ FIX: Clear cache and update suggestions for NEXT word
            if (::unifiedSuggestionController.isInitialized) {
                unifiedSuggestionController.onWordCommitted()
            }
            updateAISuggestions()
        }
        
        if (wasClipboardSuggestion) {
            clipboardSuggestionConsumed = true
            pendingClipboardSuggestionText = null
            if (::unifiedSuggestionController.isInitialized) {
                unifiedSuggestionController.clearClipboardSuggestion()
            }
        }
        
        // Clear current word and update suggestions
        currentWord = ""
        if (::unifiedSuggestionController.isInitialized) {
            unifiedSuggestionController.onWordCommitted()
        }
        updateAISuggestions()
    }
    
    /**
     * Unified key feedback handler - combines sound and vibration
     * Called for ALL key presses to provide consistent haptic and audio feedback
     * 
     * @param keyCode The key code that was pressed
     * @param isLongPress Whether this is a long-press action
     */
    private fun handleKeyFeedback(keyCode: Int = 0, isLongPress: Boolean = false) {
        // Determine feedback type
        val isRepeatedAction = (keyCode == Keyboard.KEYCODE_DELETE || keyCode == KEYCODE_SPACE)

        // Play sound if enabled
        if (soundEnabled) {
            val shouldPlaySound = when {
                isLongPress -> longPressSounds
                isRepeatedAction -> repeatedActionSounds
                else -> keyPressSounds
            }
            
            if (shouldPlaySound) {
                playKeyClickSound(keyCode)
            }
        }
        
        // Vibrate if enabled
        if (vibrationEnabled) {
            val vibrationChannel = when {
                isLongPress -> VibrationChannel.LONG_PRESS
                isRepeatedAction -> VibrationChannel.REPEATED
                else -> VibrationChannel.KEY_PRESS
            }

            val shouldVibrate = when (vibrationChannel) {
                VibrationChannel.LONG_PRESS -> longPressVibration
                VibrationChannel.REPEATED -> repeatedActionVibration
                VibrationChannel.KEY_PRESS -> keyPressVibration // ✅ FIX: Always check keyPressVibration for key presses
            }

            if (shouldVibrate) {
                // ✅ PERFORMANCE: Removed excessive logging (was firing on every key press)
                vibrateKeyPress(keyCode, vibrationChannel)
            } else {
                // ✅ PERFORMANCE: Removed excessive logging
            }
        } else {
            // ✅ PERFORMANCE: Removed vibration disabled log (was firing on every key press)
        }
    }
    
    /**
     * Play key click sound with volume control
     */
    private fun playKeyClickSound(@Suppress("UNUSED_PARAMETER") keyCode: Int) {
        try {
            // Ensure sound manager is configured with current settings
            KeyboardSoundManager.update(
                selectedSoundProfile,
                computeEffectiveSoundVolume(),
                context = this,
                customUri = customSoundUri
            )
            // ✅ Use singleton - NO MORE MediaCodec!
            KeyboardSoundManager.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing key sound", e)
        }
    }
    
    /**
     * Vibrate for key press with duration control
     */
    private fun vibrateKeyPress(keyCode: Int = 0, channel: VibrationChannel = VibrationChannel.KEY_PRESS) {
        try {
            if (!canVibrate(channel)) {
                // ✅ PERFORMANCE: Removed log that was firing on every key press
                return
            }
            
            // ✅ CRITICAL: Check vibration permission before attempting to vibrate
            // On Android 13+ (API 33+), VIBRATE permission needs to be granted at runtime
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = checkSelfPermission(android.Manifest.permission.VIBRATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.w(TAG, "⚠️ VIBRATE permission not granted (Android 13+) - skipping vibration but keeping user preference")
                    // Fallback to haptic feedback interface if available
                    keyboardView?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    return
                }
            }

            val safeDuration = computeVibrationDuration(channel)
            if (safeDuration <= 0) {
                // ✅ PERFORMANCE: Removed log that was firing on every key press
                return
            }

            val amplitudeStrength = when (channel) {
                VibrationChannel.KEY_PRESS -> when (keyCode) {
                    Keyboard.KEYCODE_DELETE, KEYCODE_SHIFT -> 210
                    KEYCODE_SPACE, Keyboard.KEYCODE_DONE -> 150
                    else -> 170
                }
                VibrationChannel.LONG_PRESS -> 230
                VibrationChannel.REPEATED -> 140
            }
            val amplitude = amplitudeFromStrength(amplitudeStrength)

            val vib = vibrator
            if (vib == null) {
                keyboardView?.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && useHapticInterface) {
                keyboardView?.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(safeDuration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(safeDuration)
            }
        } catch (e: SecurityException) {
            // ✅ CRITICAL: Don't disable vibration setting when permission denied - just skip this vibration
            // User's preference should remain, they can grant permission later
            Log.w(TAG, "⚠️ Vibration permission denied - skipping vibration but keeping user preference", e)
            keyboardView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
            Log.e(TAG, "Haptic feedback error", e)
            keyboardView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }
    
    /**
     * Legacy playClick function - now delegates to handleKeyFeedback
     * Kept for backwards compatibility
     */
    private fun playClick(keyCode: Int) {
        handleKeyFeedback(keyCode)
    }
    
    private fun playKeySound(@Suppress("UNUSED_PARAMETER") primaryCode: Int) {
        try {
            // ✅ Use singleton with updated preferences - NO MORE MediaCodec!
            KeyboardSoundManager.update(
                selectedSoundProfile,
                computeEffectiveSoundVolume(),
                context = this,
                customUri = customSoundUri
            )
            KeyboardSoundManager.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error in playKeySound", e)
        }
    }
    
    override fun onPress(primaryCode: Int) {
        // Handle key press feedback with advanced features
        keyboardView?.let { view ->
            // Show key preview popup
            showKeyPreview(primaryCode)
            
            // Enhanced haptic feedback
            performAdvancedHapticFeedback(primaryCode)
            
            // ✅ FIX: Sound feedback should use soundEnabled instead of soundIntensity
            if (soundEnabled && soundIntensity > 0) {
                playKeySound(primaryCode)
            }
            
            // Setup long-press detection for accent characters only
            // NOTE: Spacebar long-press removed - use globe button for language switching
            if (hasAccentVariants(primaryCode)) {
                currentLongPressKey = primaryCode
                // Find and store the key object for positioning the popup
                currentLongPressKeyObject = view.keyboard?.keys?.find { key ->
                    key.codes.firstOrNull() == primaryCode
                }
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
            }
            
            // Check if this could be the start of swipe typing
            if (swipeTypingEnabled && Character.isLetter(primaryCode) && !isCurrentlySwiping) {
                // Potential start of swipe typing - wait for movement or release
                swipeStartTime = System.currentTimeMillis()
            }
        }
    }

    /**
     * Overload to apply unified suggestions with auto-commit metadata.
     */
    fun applySuggestion(suggestion: UnifiedSuggestionController.UnifiedSuggestion) {
        val ic = currentInputConnection ?: run {
            Log.e(TAG, "No input connection available for unified suggestion")
            return
        }
        if (suggestion.isAutoCommit) {
            if (currentWord.isNotEmpty()) {
                ic.deleteSurroundingText(currentWord.length, 0)
            }
            ic.commitText(suggestion.text, 1)
            ic.commitText(" ", 1)
            Log.d(TAG, "Auto-committed unified suggestion '${suggestion.text}' with trailing space")
            return
        }
        applySuggestion(suggestion.text)
    }
    
    override fun onRelease(primaryCode: Int) {
        // Clean up long-press detection
        if (currentLongPressKey == primaryCode) {
            longPressHandler.removeCallbacks(longPressRunnable)
            currentLongPressKey = -1
            currentLongPressKeyObject = null
        }
        
        // Hide key preview but keep accent options if they're showing
        if (accentPopup?.isShowing != true) {
            hideKeyPreview()
        }
        
        // Don't hide accent popup here - let user tap to select or tap outside to dismiss
    }
    
    
    override fun onText(text: CharSequence?) {
        val ic = currentInputConnection ?: return
        
        // Provide haptic and audio feedback for text input
        handleKeyFeedback()
        
        text?.let {
            // **PHASE 2: Check reverse transliteration first (Native → Roman)**
            if (reverseTransliterationEnabled &&
                !transliterationEnabled && // Only one direction at a time
                currentLanguage in listOf("hi", "te", "ta") &&
                transliterationEngine != null) {
                
                val isIndicInput = it.all { char -> char.code > 127 }
                
                if (isIndicInput) {
                    // Buffer native characters
                    romanBuffer.append(it)
                    
                    val lastChar = it.last()
                    if (lastChar.code in listOf(32, 46, 44, 33, 63, 10, 59, 58)) { // Space, punctuation
                        val nativeText = romanBuffer.toString().dropLast(1).trim()
                        
                        if (nativeText.isNotEmpty()) {
                            try {
                                // ✅ FIX: Use safe call instead of !!
                                val romanText = transliterationEngine?.reverseTransliterate(nativeText)
                                if (romanText != null) {
                                    // Delete buffered native text
                                    ic.deleteSurroundingText(romanBuffer.length, 0)
                                    
                                    // Insert Roman text + punctuation
                                    ic.commitText("$romanText${lastChar}", 1)
                                    
                                    Log.d(TAG, "🔄 Reverse transliterated: '$nativeText' → '$romanText'")
                                } else {
                                    ic.commitText(it, 1)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Reverse transliteration error", e)
                                ic.commitText(it, 1)
                            }
                        } else {
                            ic.commitText(it, 1)
                        }
                        
                        romanBuffer.clear()
                        return
                    } else {
                        ic.commitText(it, 1)
                        return
                    }
                } else {
                    romanBuffer.clear()
                }
            }
            
            // **PHASE 1+2: Forward transliteration (Roman → Native) with autocorrect**
            // ✅ FIX: Use safe call instead of !!
            if (transliterationEnabled && 
                currentLanguage in listOf("hi", "te", "ta") &&
                transliterationEngine != null) {
                
                val isRomanInput = transliterationEngine?.isRomanInput(it.toString()) ?: false
                
                if (isRomanInput) {
                    // Buffer Roman characters
                    romanBuffer.append(it)
                    
                    // **PHASE 2: Show real-time transliteration suggestions**
                    // Temporarily disabled - pending full integration
                    // if (romanBuffer.length >= 2) {
                    //     try {
                    //         val suggestions = transliterationEngine!!.getSuggestions(romanBuffer.toString())
                    //         updateTransliterationSuggestions(suggestions)
                    //     } catch (e: Exception) {
                    //         Log.w(TAG, "Could not get real-time suggestions", e)
                    //     }
                    // }
                    
                    // Check if we hit a word boundary (space, punctuation, newline)
                    val lastChar = it.last()
                    if (lastChar in listOf(' ', '.', ',', '!', '?', '\n', ';', ':')) {
                        // Transliterate the buffered text
                        val romanText = romanBuffer.toString().dropLast(1).trim()
                        
                        if (romanText.isNotEmpty()) {
                            try {
                                // Transliterate using Phase 1 engine
                                // Phase 2 autocorrect integration pending
                                // ✅ FIX: Use safe call instead of !!
                                val nativeText = transliterationEngine?.transliterate(romanText)
                                
                                if (nativeText != null) {
                                    // Delete the buffered Roman text
                                    ic.deleteSurroundingText(romanBuffer.length, 0)
                                    
                                    // Insert transliterated text + punctuation
                                    ic.commitText("$nativeText$lastChar", 1)
                                    
                                    Log.d(TAG, "🔤 Transliterated: '$romanText' → '$nativeText'")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Transliteration error", e)
                                // Fallback: just commit the original text
                                ic.commitText(it, 1)
                            }
                        } else {
                            // Just punctuation, commit as-is
                            ic.commitText(it, 1)
                        }
                        
                        romanBuffer.clear()
                        clearTransliterationSuggestions()
                    } else {
                        // Still buffering - commit character but keep buffering
                        ic.commitText(it, 1)
                    }
                    return
                } else {
                    // Native script input detected - clear buffer and pass through
                    romanBuffer.clear()
                    clearTransliterationSuggestions()
                }
            }
            
            // Default behavior: no transliteration
            romanBuffer.clear()
            ic.commitText(it, 1)
        }
    }
    
    override fun swipeDown() {
        if (swipeTypingEnabled && isCurrentlySwiping) {
            finishSwipeTyping()
        } else {
            handleClose()
        }
    }
    
    override fun swipeLeft() {
        if (swipeTypingEnabled && isCurrentlySwiping) {
            // Continue swipe typing
            processSwipeMovement(-1) // Left direction
        } else {
            // ✅ FIX: Use safe call instead of !!
            currentInputConnection?.let { handleBackspace(it) }
        }
    }
    
    override fun swipeRight() {
        if (swipeTypingEnabled && isCurrentlySwiping) {
            // Continue swipe typing
            processSwipeMovement(1) // Right direction
        } else {
            // Swipe right for space
            currentInputConnection?.let { commitSpaceWithDoublePeriod(it) }
        }
    }
    
    override fun swipeUp() {
        if (swipeTypingEnabled && isCurrentlySwiping) {
            // Continue swipe typing
            processSwipeMovement(0) // Up direction
        } else {
            // Swipe up for shift
            handleShift()
        }
    }
    
    private fun processSwipeMovement(direction: Int) {
        if (!isCurrentlySwiping || !swipeTypingEnabled || !isSwipeAllowedForCurrentState()) return
        
        // Add direction to swipe path for processing
        swipePath.add(direction)
        
        // Process swipe path to predict words
        val predictedWord = processSwipePath(swipePath)
        if (predictedWord.isNotEmpty()) {
            // Update swipe buffer
            swipeBuffer.setLength(0)
            swipeBuffer.append(predictedWord)
            
            // Show prediction in suggestion bar
            showSwipePrediction(predictedWord)
        }
    }
    
    private fun finishSwipeTyping() {
        if (!isCurrentlySwiping || !swipeTypingEnabled || !isSwipeAllowedForCurrentState()) return
        
        isCurrentlySwiping = false
        
        // Process final swipe path
        val finalWord = processSwipePath(swipePath)
        
        if (finalWord.isNotEmpty()) {
            // Commit the swiped word
            currentInputConnection?.let { ic ->
                ic.commitText("$finalWord ", 1)
                updateAISuggestions()
            }
        }
        
        // Reset visual feedback
        keyboardView?.background = themeManager.createKeyboardBackground()
        
        // Clear swipe data
        swipePath.clear()
        swipeBuffer.setLength(0)
    }
    
    private fun processSwipePath(path: List<Int>): String {
        if (path.isEmpty()) return ""
        
        // Simple swipe-to-word mapping (basic implementation)
        // In a real implementation, this would use advanced algorithms
        val word = StringBuilder()
        
        path.forEach { code ->
            if (code > 0 && code < 256) {
                val c = code.toChar()
                if (Character.isLetter(c)) {
                    word.append(c)
                }
            }
        }
        
        // Apply basic swipe word corrections
        val result = word.toString().lowercase()
        return applySwipeCorrections(result)
    }
    
    /**
     * Generate a simple fallback when swipe decoder returns nothing
     */
    private fun generateSwipeFallback(swipePath: List<Pair<Float, Float>>): String {
        if (swipePath.isEmpty()) return "swipe"
        
        // Simple heuristic: map to rough QWERTY regions
        val letters = swipePath.mapNotNull { (x, y) ->
            when {
                y < 0.33f -> { // Top row
                    when {
                        x < 0.2f -> 'q'
                        x < 0.4f -> 'w' 
                        x < 0.6f -> 'e'
                        x < 0.8f -> 'r'
                        else -> 't'
                    }
                }
                y < 0.66f -> { // Middle row
                    when {
                        x < 0.25f -> 'a'
                        x < 0.5f -> 's'
                        x < 0.75f -> 'd'
                        else -> 'f'
                    }
                }
                else -> { // Bottom row
                    when {
                        x < 0.33f -> 'z'
                        x < 0.66f -> 'x'
                        else -> 'c'
                    }
                }
            }
        }
        
        // Collapse consecutive same letters
        val collapsed = StringBuilder()
        var prev: Char? = null
        for (c in letters) {
            if (c != prev) {
                collapsed.append(c)
                prev = c
            }
        }
        
        return if (collapsed.length >= 2) collapsed.toString() else "swipe"
    }
    
    fun updateSwipeGeometry(language: String, positions: Map<Char, Pair<Float, Float>>) {
        if (!::autocorrectEngine.isInitialized) return
        autocorrectEngine.updateKeyLayout(language, positions)
    }
    
    private fun applySwipeCorrections(swipeWord: String): String {
        // Enhanced swipe pattern to word mapping
        return when (swipeWord.lowercase()) {
            "hello", "helo", "hllo" -> "hello"
            "and", "adn", "nad" -> "and"
            "the", "teh", "hte" -> "the"
            "you", "yuo", "oyu" -> "you"
            "are", "aer", "rae" -> "are"
            "to", "ot" -> "to"
            "for", "fro", "ofr" -> "for"
            "with", "wiht", "whit" -> "with"
            "that", "taht", "htat" -> "that"
            "this", "tihs", "htis" -> "this"
            "have", "ahve", "haev" -> "have"
            "from", "form", "fomr" -> "from"
            "they", "tehy", "yhte" -> "they"
            "know", "konw", "nkow" -> "know"
            "want", "wnat", "awnt" -> "want"
            "been", "eben", "neeb" -> "been"
            "good", "godo", "ogod" -> "good"
            "much", "muhc", "mcuh" -> "much"
            "some", "soem", "mose" -> "some"
            "time", "tmie", "itme" -> "time"
            "very", "vrey", "yrev" -> "very"
            "when", "wehn", "hwne" -> "when"
            "come", "coem", "moce" -> "come"
            "here", "hree", "ehre" -> "here"
            "just", "jsut", "ujst" -> "just"
            "like", "lkie", "ilke" -> "like"
            "over", "ovre", "roev" -> "over"
            "also", "aslo", "laso" -> "also"
            "back", "bakc", "cabk" -> "back"
            "after", "afetr", "atfer" -> "after"
            "use", "ues", "seu" -> "use"
            "two", "tow", "wto" -> "two"
            "how", "hwo", "ohw" -> "how"
            "our", "oru", "uro" -> "our"
            "work", "wokr", "rwok" -> "work"
            "first", "frist", "fisrt" -> "first"
            "well", "wlel", "ewll" -> "well"
            "way", "wya", "awy" -> "way"
            "even", "eevn", "nev" -> "even"
            "new", "nwe", "enw" -> "new"
            "year", "yaer", "yrea" -> "year"
            "would", "woudl", "wolud" -> "would"
            "people", "poeple", "peolpe" -> "people"
            "think", "thinl", "htink" -> "think"
            "where", "wheer", "hwere" -> "where"
            "being", "beinf", "beign" -> "being"
            "now", "nwo", "onw" -> "now"
            "make", "amke", "meak" -> "make"
            "most", "mots", "omst" -> "most"
            "get", "gte", "teg" -> "get"
            "see", "ese", "ees" -> "see"
            "him", "hmi", "ihm" -> "him"
            "has", "ahs", "sha" -> "has"
            "had", "ahd", "dha" -> "had"
            else -> if (swipeWord.length > 1) swipeWord else ""
        }
    }
    
    // Swipe state tracking (using UnifiedAutocorrectEngine directly)
    private var lastCommittedSwipeWord = ""
    private var lastSwipeAutoInsertedSpace = true
    private var lastInputWasSwipe = false // ✅ Track if last input was a swipe for auto-spacing
    
    // Implement SwipeListener interface methods with enhanced autocorrection
    override fun onSwipeDetected(
        swipedKeys: List<Int>, 
        swipePattern: String, 
        keySequence: List<Int>,
        swipePath: List<Pair<Float, Float>>,
        isPreview: Boolean // ✅ NEW: Flag to distinguish preview vs final (default in interface)
    ) {
        // ✅ Safety check: Ensure unified engine is initialized
        if (!::autocorrectEngine.isInitialized) {
            Log.w(TAG, "⚠️ UnifiedAutocorrectEngine not initialized yet - ignoring swipe gesture")
            return
        }
        if (!isSwipeAllowedForCurrentState()) {
            return
        }
        
        // Skip if path is too short (accidental taps)
        if (swipePath.size < 2) {
            return
        }
        
        // Debug log for preview tracking
        if (isPreview) {
            Log.d(TAG, "👆 Swipe PREVIEW: ${swipePath.size} points")
        } else {
            Log.d(TAG, "✋ Swipe FINAL: ${swipePath.size} points")
        }
        
        coroutineScope.launch {
            try {
                // Get context only if not preview (optimization)
                val contextWords = if (isPreview) emptyList() else wordHistory.takeLast(3)
                val swipePathObj = SwipePath(swipePath)
                val swipeSuggestions = autocorrectEngine.suggestForSwipe(swipePathObj, contextWords)
                
                // Extract unique strings
                val candidates = swipeSuggestions.map { it.text }.distinct().filter { it.isNotBlank() }
                
                // Generate fallback only for final swipes with no results
                val finalCandidates = if (candidates.isEmpty() && !isPreview) {
                    val fallback = generateSwipeFallback(swipePath)
                    if (fallback.isNotBlank()) listOf(fallback) else emptyList()
                } else {
                    candidates
                }
                
                withContext(Dispatchers.Main) {
                    if (isPreview) {
                        // ===========================
                        // 🔵 PREVIEW MODE (Moving)
                        // ===========================
                        if (finalCandidates.isNotEmpty()) {
                            Log.d(TAG, "👁️ Preview: ${finalCandidates.take(3)}")
                            updateSuggestionUI(finalCandidates.take(suggestionCount))
                        }
                    } else {
                        // ===========================
                        // 🟢 FINAL MODE (Lifted)
                        // ===========================
                        handleFinalSwipeCommit(finalCandidates)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in swipe path processing", e)
                withContext(Dispatchers.Main) {
                    lastInputWasSwipe = false
                    updateAISuggestions()
                }
            }
        }
    }
    
    /**
     * Handle final swipe commit when finger is lifted
     * - Auto-spaces between consecutive swipes
     * - Shows alternatives (excluding committed word) in suggestion bar
     */
    private fun handleFinalSwipeCommit(candidates: List<String>) {
        val bestCandidate = candidates.firstOrNull()
        if (bestCandidate.isNullOrBlank()) {
            lastCommittedSwipeWord = ""
            lastInputWasSwipe = false
            currentWord = ""
            updateAISuggestions()
            return
        }
        
        val ic = currentInputConnection ?: return
        
        // 1. AUTO-SPACE LOGIC (Swipe -> Swipe)
        // If we just swiped a word previously, add a space before this new one
        if (lastInputWasSwipe && currentWord.isNotEmpty()) {
            ic.commitText(" ", 1)
            // Commit the PREVIOUS word to history now that it's confirmed
            onWordCommitted(currentWord)
            wordHistory.add(currentWord)
            if (wordHistory.size > 20) wordHistory.removeAt(0)
        }
        
        // 2. COMMIT WORD (No trailing space yet)
        ic.commitText(bestCandidate, 1)
        
        // 3. UPDATE STATE
        currentWord = bestCandidate
        lastCommittedSwipeWord = bestCandidate
        lastInputWasSwipe = true
        
        Log.d(TAG, "✅ Swipe committed: '$bestCandidate'")
        
        // 4. Learn from swipe
        if (::autocorrectEngine.isInitialized) {
            autocorrectEngine.recordSwipeAcceptance(bestCandidate)
        }
        userDictionaryManager?.learnFromSwipe(bestCandidate)
        
        // 5. SMART SUGGESTIONS - Filter out the committed word
        // User wants alternatives, not the word they just entered
        val alternatives = candidates
            .filter { !it.equals(bestCandidate, ignoreCase = true) }
            .take(suggestionCount)
        
        if (alternatives.isNotEmpty()) {
            updateSuggestionUI(alternatives)
        } else {
            // If no alternatives, show next word predictions
            updateAISuggestions()
        }
    }
    
    override fun onSwipeStarted() {
        if (!isSwipeAllowedForCurrentState()) return
        try {
            // Hide any popups that might interfere with swipe
            hideAccentOptions()
            hideKeyPreview()
            
            // ✅ UNIFIED THEMING: Don't change background during swipe
            // Visual feedback handled by swipe trail, not background color
            
            // Show swipe mode indicator
            showSwipeIndicator(true)
            
            // Clear any pending long-press actions
            longPressHandler.removeCallbacks(longPressRunnable)
            currentLongPressKey = -1
        } catch (e: Exception) {
            // Ignore swipe start errors
        }
    }
    
    override fun onSwipeEnded() {
        if (!isSwipeAllowedForCurrentState()) return
        try {
            // ✅ UNIFIED THEMING: Restore transparent background
            keyboardView?.setBackgroundColor(Color.TRANSPARENT)
            
            // Hide swipe mode indicator
            showSwipeIndicator(false)
        } catch (e: Exception) {
            // Ignore swipe end errors
        }
    }
    
    private fun showSwipeIndicator(show: Boolean) {
        // Don't show swipe indicator icons, but keep middle suggestion visible
        // This function no longer sets any swipe indicators to keep suggestions clean
        // Middle suggestion will be handled by normal suggestion update process
    }
    
    private fun showSwipePrediction(prediction: String) {
        suggestionContainer?.let { container ->
            if (container.childCount > 0) {
                val firstSuggestion = container.getChildAt(0) as TextView
                firstSuggestion.apply {
                    text = prediction
                    setTextColor(getSwipeTextColor())
                    visibility = View.VISIBLE
                }
            }
        }
    }
    private fun getSwipeTextColor(): Int {
        return themeManager.getCurrentPalette().specialAccent
    }
    
    private fun handleClose() {
        requestHideSelf(0)
    }
    
    /**
     * Data class for keyboard settings from Flutter
     */
    data class KeyboardSettings(
        // General Settings
        val numberRow: Boolean = false,
        val hintedNumberRow: Boolean = false,
        val hintedSymbols: Boolean = false,
        val showUtilityKey: Boolean = true,
        val displayLanguageOnSpace: Boolean = true,
        val portraitFontSize: Double = 100.0,
        val landscapeFontSize: Double = 100.0,
        
        // Layout Settings
        val borderlessKeys: Boolean = false,
        val oneHandedMode: Boolean = false,
        val oneHandedModeWidth: Double = 87.0,
        val landscapeFullScreenInput: Boolean = true,
        val keyboardWidth: Double = 100.0,
        val keyboardHeight: Double = 100.0,
        val verticalKeySpacing: Double = 5.0,
        val horizontalKeySpacing: Double = 2.0,
        val portraitBottomOffset: Double = 1.0,
        val landscapeBottomOffset: Double = 2.0,
        
        // Key Press Settings
        val popupVisibility: Boolean = true,
        val longPressDelay: Double = 200.0,
        val instantLongPressSelectFirst: Boolean = true
    )
    
    /**
     * Load keyboard settings from SharedPreferences
     */
    private fun loadKeyboardSettings() {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            
            keyboardSettings = KeyboardSettings(
                // General Settings
                numberRow = prefs.getBoolean("flutter.keyboard_settings.number_row", false),
                hintedNumberRow = prefs.getBoolean("flutter.keyboard_settings.hinted_number_row", false),
                hintedSymbols = prefs.getBoolean("flutter.keyboard_settings.hinted_symbols", false),
                showUtilityKey = prefs.getBoolean("flutter.keyboard_settings.show_utility_key", true),
                displayLanguageOnSpace = prefs.getBoolean("flutter.keyboard_settings.display_language_on_space", true),
                portraitFontSize = prefs.getFloat("flutter.keyboard_settings.portrait_font_size", 100.0f).toDouble(),
                landscapeFontSize = prefs.getFloat("flutter.keyboard_settings.landscape_font_size", 100.0f).toDouble(),
                
                // Layout Settings
                borderlessKeys = prefs.getBoolean("flutter.keyboard_settings.borderless_keys", false),
                oneHandedMode = prefs.getBoolean("flutter.keyboard_settings.one_handed_mode", false),
                oneHandedModeWidth = prefs.getFloat("flutter.keyboard_settings.one_handed_mode_width", 87.0f).toDouble(),
                landscapeFullScreenInput = prefs.getBoolean("flutter.keyboard_settings.landscape_full_screen_input", true),
                keyboardWidth = prefs.getFloat("flutter.keyboard_settings.keyboard_width", 100.0f).toDouble(),
                keyboardHeight = prefs.getFloat("flutter.keyboard_settings.keyboard_height", 100.0f).toDouble(),
                verticalKeySpacing = prefs.getFloat("flutter.keyboard_settings.vertical_key_spacing", 5.0f).toDouble(),
                horizontalKeySpacing = prefs.getFloat("flutter.keyboard_settings.horizontal_key_spacing", 2.0f).toDouble(),
                portraitBottomOffset = prefs.getFloat("flutter.keyboard_settings.portrait_bottom_offset", 1.0f).toDouble(),
                landscapeBottomOffset = prefs.getFloat("flutter.keyboard_settings.landscape_bottom_offset", 2.0f).toDouble(),
                
                // Key Press Settings
                popupVisibility = prefs.getBoolean("flutter.keyboard_settings.popup_visibility", true),
                longPressDelay = prefs.getFloat("flutter.keyboard_settings.long_press_delay", 200.0f).toDouble(),
                instantLongPressSelectFirst = prefs.getBoolean(
                    "flutter.keyboard.instantLongPressSelectFirst",
                    prefs.getBoolean("keyboard.instantLongPressSelectFirst", true)
                )
            )
            
            // Apply settings immediately
            applyKeyboardSettings()
            
            Log.d(TAG, "Keyboard settings loaded: numberRow=${keyboardSettings.numberRow}, " +
                    "borderlessKeys=${keyboardSettings.borderlessKeys}, " +
                    "oneHandedMode=${keyboardSettings.oneHandedMode}")
                    
        } catch (e: Exception) {
            Log.e(TAG, "Error loading keyboard settings", e)
        }
    }
    
    /**
     * Apply loaded keyboard settings to the keyboard view and layout
     */
    private fun applyKeyboardSettings() {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            // Apply number row setting
            showNumberRow = keyboardSettings.numberRow
            keyboardHeightManager.setNumberRowEnabled(keyboardSettings.numberRow)
            if (showUtilityKeyEnabled != keyboardSettings.showUtilityKey) {
                showUtilityKeyEnabled = keyboardSettings.showUtilityKey
                languageLayoutAdapter.setShowUtilityKey(keyboardSettings.showUtilityKey)
                if (::unifiedController.isInitialized) {
                    val targetMode = currentKeyboardMode.toLayoutMode()
                    val enableNumberRow = showNumberRow && currentKeyboardMode == KeyboardMode.LETTERS
                    unifiedController.buildAndRender(currentLanguage, targetMode, enableNumberRow)
                }
                keyboardView?.let { legacyView ->
                    if (legacyView is SwipeKeyboardView) {
                        legacyView.setKeyboardMode(legacyView.currentKeyboardMode, languageLayoutAdapter, showNumberRow)
                    }
                }
            } else {
                languageLayoutAdapter.setShowUtilityKey(keyboardSettings.showUtilityKey)
            }

            unifiedKeyboardView?.let { view ->
                view.setNumberRowEnabled(keyboardSettings.numberRow)
                view.recalcHeight()
            }
            
            // Apply font size settings (multiplier as percentage)
            val fontMultiplier = (keyboardSettings.portraitFontSize / 100.0).toFloat()
            
            // Apply keyboard dimensions and spacing
            keyboardView?.let { view ->
                // Apply one-handed mode if enabled
                if (keyboardSettings.oneHandedMode) {
                    val layoutParams = view.layoutParams
                    if (layoutParams != null) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val newWidth = (screenWidth * (keyboardSettings.oneHandedModeWidth / 100.0)).toInt()
                        layoutParams.width = newWidth
                        view.layoutParams = layoutParams
                    }
                }
                
                // Apply spacing settings via keyboard padding
                val horizontalPadding = (keyboardSettings.horizontalKeySpacing * resources.displayMetrics.density).toInt()
                val verticalPadding = (keyboardSettings.verticalKeySpacing * resources.displayMetrics.density).toInt()
                view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

                if (view is SwipeKeyboardView) {
                    val numberHintsEnabled = keyboardSettings.hintedNumberRow && !keyboardSettings.numberRow
                    view.setHintedNumberRow(numberHintsEnabled)
                    view.setHintedSymbols(keyboardSettings.hintedSymbols)
                }

                // Invalidate to redraw with new settings
                view.invalidate()
            }

            unifiedKeyboardView?.let { view ->
                val numberHintsEnabled = keyboardSettings.hintedNumberRow && !keyboardSettings.numberRow
                view.setHintedNumberRow(numberHintsEnabled)
                view.setHintedSymbols(keyboardSettings.hintedSymbols)
                view.setInstantLongPressSelectFirst(keyboardSettings.instantLongPressSelectFirst)
                val widthPct = (keyboardSettings.oneHandedModeWidth / 100.0).toFloat().coerceIn(0.6f, 0.9f)
                val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
                val sidePref = prefs.getString("flutter.keyboard.oneHanded.side", "right") ?: "right"
                view.setOneHandedMode(keyboardSettings.oneHandedMode, sidePref, widthPct)
            }
            
            // Apply long press delay
            // Note: This would typically be applied to the KeyboardView's long press detection
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying keyboard settings", e)
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // 🔥 CRITICAL PERFORMANCE FIX: Throttle to prevent 25-40 calls per keystroke
        // onUpdateSelection fires excessively - only process once every 120ms
        val currentTime = android.os.SystemClock.elapsedRealtime()
        if (currentTime - lastUpdateSelectionTime < 120) {
            return  // ✅ Kill 99% of lag by throttling cursor events
        }
        lastUpdateSelectionTime = currentTime

        // ✅ PERFORMANCE: Only mark cursor moved, no logging (this fires very frequently)
        if (::autocorrectEngine.isInitialized && (oldSelStart != newSelStart || oldSelEnd != newSelEnd)) {
            autocorrectEngine.markCursorMoved()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        
        // Always reset panels so keyboard opens in typing mode
        unifiedKeyboardView?.backToTyping()
        unifiedPanelManager?.markPanelClosed()

        // Flush any pending voice input when keyboard becomes visible
        flushPendingVoiceResult()

        if (::unifiedController.isInitialized && currentKeyboardMode != KeyboardMode.LETTERS) {
            switchKeyboardMode(KeyboardMode.LETTERS)
        }

        val cachedHeight = KeyboardHeightManager.getSavedHeight(this)

        // Use KeyboardHeightManager to ensure consistent height
        val computedHeight = keyboardHeightManager.calculateKeyboardHeight(
            includeToolbar = true,
            includeSuggestions = true
        )
        val resolvedHeight = if (cachedHeight > 0 && abs(cachedHeight - computedHeight) < 12) {
            cachedHeight
        } else {
            computedHeight
        }

        KeyboardHeightManager.applyKeyboardHeight(this, resolvedHeight)

        if (cachedHeight > 0 && resolvedHeight == cachedHeight) {
            Log.d(TAG, "📏 Restored cached keyboard height: $cachedHeight")
        }

        // Apply consistent height to main layout
        mainKeyboardLayout?.post {
            mainKeyboardLayout?.layoutParams?.height = resolvedHeight
            mainKeyboardLayout?.requestLayout()
            Log.d(TAG, "[KeyboardHeightManager] Applied keyboard height: ${resolvedHeight}px")
            
            // ✅ Fetch intelligent suggestions (sentence starters) after view is ready
            mainHandler.postDelayed({
                try {
                    if (unifiedKeyboardView != null && ::unifiedSuggestionController.isInitialized) {
                        // Trigger suggestion fetch to show sentence starters
                        updateAISuggestions()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing default suggestions on start", e)
                }
            }, 100) // Small delay to ensure view is fully initialized
        }
        
        // Height is now managed by keyboard container - no manual adjustment needed
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        initialInsetsApplied = false
        unifiedPanelManager?.markPanelClosed()
    }
    
    override fun onEvaluateFullscreenMode(): Boolean {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) {
            return false
        }
        return if (::keyboardSettings.isInitialized) {
            keyboardSettings.landscapeFullScreenInput
        } else {
            true
        }
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastSpaceTimestamp = 0L
        
        // Apply CleverType config on keyboard activation
        applyConfig()

        flushPendingVoiceResult()
        
        // 🔁 Trigger Unified AI Suggestion update on typing start
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val beforeCursor = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val words = beforeCursor.split(" ").filter { it.isNotBlank() }
                val currentWord = words.lastOrNull() ?: ""
                val contextWords = if (words.size > 1) words.dropLast(1) else emptyList()
                updateAISuggestions(currentWord, contextWords)
            } catch (e: Exception) {
                Log.e("AIKeyboardService", "⚠️ Failed to trigger updateAISuggestions", e)
            }
        }
        
        // Reset keyboard state with enhanced CapsShiftManager
        if (::capsShiftManager.isInitialized) {
            capsShiftManager.resetToNormal()
            
            // Apply auto-capitalization based on context
            attribute?.let { info ->
                val inputType = info.inputType
                capsShiftManager.applyAutoCapitalization(currentInputConnection, inputType)
            }
        } else {
            // Fallback to old implementation
            caps = false
            keyboardView?.isShifted = caps
            
            // Auto-capitalize for sentence start
            attribute?.let { info ->
                if (info.inputType != 0) {
                    val inputType = info.inputType and EditorInfo.TYPE_MASK_CLASS
                    if (inputType == EditorInfo.TYPE_CLASS_TEXT) {
                        val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
                        if (variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL ||
                            variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                            caps = true
                            keyboardView?.isShifted = caps
                        }
                    }
                }
            }
        }
        
        // Reset current word
        currentWord = ""
        
        var suggestionsDeferred = false

        // Ensure dictionaries are loaded before showing suggestions
        if (::autocorrectEngine.isInitialized) {
            val currentLang = currentLanguage
            if (!autocorrectEngine.hasLanguage(currentLang)) {
                suggestionsDeferred = true
                Log.w(TAG, "⚠️ Dictionary for $currentLang still loading, deferring suggestions")
                coroutineScope.launch {
                    val maxWaitMs = 3000
                    val intervalMs = 100L
                    var waitedMs = 0

                    while (!autocorrectEngine.hasLanguage(currentLang) && waitedMs < maxWaitMs) {
                        delay(intervalMs)
                        waitedMs += intervalMs.toInt()
                    }

                    if (autocorrectEngine.hasLanguage(currentLang)) {
                        Log.d(TAG, "✅ Dictionary for $currentLang became ready after ${waitedMs}ms")
                        updateAISuggestions()
                    } else {
                        Log.w(TAG, "⚠️ Dictionary for $currentLang still initializing after ${maxWaitMs}ms; suggestions will refresh once activation completes")
                    }
                }
            }
        }
        
        if (!suggestionsDeferred) {
            // Dictionary is ready, show suggestions immediately
            Log.d(TAG, "onStartInput - showing initial suggestions")
            updateAISuggestions()
        }
        
        // Force load fresh settings when keyboard becomes active
        checkAndUpdateSettings()
        
        // Clear suggestions
        clearSuggestions()
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        stopVoiceInput()
        clearSuggestions()
        currentKeyboardMode = KeyboardMode.LETTERS
        previousKeyboardMode = KeyboardMode.LETTERS
        lastSpaceTimestamp = 0L
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Recalculate keyboard height for new configuration
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val newHeight = keyboardHeightManager.calculateKeyboardHeight()
        
        // Update keyboard height
        mainKeyboardLayout?.layoutParams?.height = newHeight
        mainKeyboardLayout?.requestLayout()
        
        // Height is now managed by keyboard container - no manual adjustment needed
        
        Log.d(TAG, "[KeyboardHeightManager] Configuration changed - Landscape: $isLandscape, Height: $newHeight")
        
        // Reinitialize keyboard for new configuration
        // LEGACY: Old XML-based reload removed
        // UnifiedKeyboardView handles configuration changes automatically
        // No manual reload needed with JSON-based system
    }
    
    // Method to update settings from Flutter
    fun updateSettings(
        theme: String,
        aiSuggestions: Boolean,
        swipeTyping: Boolean,
        vibration: Boolean,
        keyPreview: Boolean
    ) {
        currentTheme = theme // Legacy variable for compatibility
        // Theme switching removed - using default theme only
        aiSuggestionsEnabled = aiSuggestions
        swipeTypingEnabled = swipeTyping
        vibrationEnabled = vibration
        keyPreviewEnabled = keyPreview
        
        // Load advanced feedback settings from preferences
        hapticIntensity = settings.getInt("haptic_intensity", 2)
        soundIntensity = settings.getInt("sound_intensity", 2)
        visualIntensity = settings.getInt("visual_intensity", 0)
        soundVolume = settings.getFloat("sound_volume", 0.65f)
        customSoundUri = settings.getString("sound_custom_uri", customSoundUri)

        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        selectedSoundProfile = flutterPrefs.getString("flutter.sound.type", selectedSoundProfile) ?: selectedSoundProfile
        selectedTapEffectStyle = flutterPrefs.getString("flutter.effect.type", selectedTapEffectStyle) ?: selectedTapEffectStyle
        
        // Save to preferences
        settings.edit().apply {
            putString("keyboard_theme", theme)
            putBoolean("ai_suggestions", aiSuggestions)
            putBoolean("swipe_typing", swipeTyping)
            putBoolean("vibration_enabled", vibration)
            putBoolean("key_preview_enabled", keyPreview)
            apply()
        }
        
        // Apply settings immediately
        applyTheme()
        keyboardView?.let { view ->
            view.isPreviewEnabled = false // Always disable key preview for stable keys
        }
        configureFeedbackModules()
        refreshSwipeCapability("updateSettings")
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        stopVoiceInput()
        mainHandler.removeCallbacks(pendingVoiceFlushRunnable)

        // ✅ Release SoundPool singleton
        KeyboardSoundManager.release()
        Log.d(TAG, "✅ KeyboardSoundManager released")
        
        // Clear singleton instance
        instance = null
        
        // Cleanup AI service
        try {
            if (::advancedAIService.isInitialized) {
                advancedAIService.cleanup()
                Log.d(TAG, "AI service cleaned up")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AI service", e)
        }
        
        // Cancel coroutine scope
        coroutineScope.cancel()
        
        // Cleanup unified layout controller
        try {
            if (::unifiedController.isInitialized) {
                unifiedController.clear()
                Log.d(TAG, "Unified layout controller cleaned up")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up unified layout controller", e)
        }
        
        // Unregister broadcast receivers
        try {
            unregisterReceiver(promptReceiver)
            Log.d(TAG, "Prompt receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Prompt receiver was not registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering prompt receiver", e)
        }
        
        // Flush user dictionary before closing
        if (::userDictionaryManager.isInitialized) {
            try {
                userDictionaryManager.flush()
                Log.d(TAG, "✅ User dictionary flushed on destroy")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error flushing user dictionary", e)
            }
        }
        
        // Clean up AI bridge
        // Clean up UnifiedAI service
        if (::unifiedAIService.isInitialized) {
            unifiedAIService.cleanup()
        }
        
        coroutineScope.cancel()
        
        // Stop settings polling
        stopSettingsPolling()
        
        // Receiver already unregistered above - removed duplicate
        
        // Cleanup theme manager
        try {
            themeManager.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up theme manager", e)
        }
        
        // Cleanup clipboard history manager
        try {
            clipboardHistoryManager.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up clipboard history manager", e)
        }
        
        // Stop periodic user dictionary sync
        syncRunnable?.let { runnable ->
            syncHandler?.removeCallbacks(runnable)
        }
        syncHandler = null
        syncRunnable = null
        
        // Clean up advanced keyboard resources
        longPressHandler.removeCallbacks(longPressRunnable)
        hideKeyPreview()
        hideAccentOptions()
        
        
        // Clear data
        wordHistory.clear()
        currentWord = ""
        isAIReady = false
    }
    
    private fun applySettingsImmediately() {
        try {
            // Apply theme changes
            applyTheme()
            
            // Reload keyboard layout if needed (for number row changes)
            if (currentKeyboard == KEYBOARD_LETTERS) {
                switchKeyboardMode(KeyboardMode.LETTERS)  // This will apply number row and language changes
                Log.d(TAG, "Keyboard layout reloaded - NumberRow: $showNumberRow, Language: $currentLanguage")
            }
            
            
            
            // Update keyboard view settings
            keyboardView?.let { view ->
                view.isPreviewEnabled = false // Always disable key preview for stable keys
                // Force refresh of the keyboard view
                view.invalidateAllKeys()
            }
            refreshSwipeCapability("settingsImmediate")
            
            // Show feedback to user that settings were applied
            suggestionContainer?.let { container ->
                if (container.childCount > 0) {
                    val firstSuggestion = container.getChildAt(0) as TextView
                    firstSuggestion.apply {
                        text = "⚙️ Settings Updated"
                        val palette = themeManager.getCurrentPalette()
                        setTextColor(palette.specialAccent)
                        visibility = View.VISIBLE
                    }
                    
                    // Reset after 2 seconds
                    mainHandler.postDelayed({
                        try {
                            suggestionContainer?.let { cont ->
                                if (cont.childCount > 0) {
                                    val suggestion = cont.getChildAt(0) as TextView
                                    suggestion.visibility = View.INVISIBLE
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore UI update errors
                        }
                    }, 2000)
                }
            }
        } catch (e: Exception) {
            // Prevent crashes from settings application
        }
    }
    
    /**
     * Settings polling - DISABLED by default (BroadcastReceiver is authoritative)
     * Only runs in DEBUG builds as a safety net with 15s interval to reduce I/O cost
     */
    private fun startSettingsPolling() {
        // Only enable polling in debug builds as a fallback mechanism
        // BuildConfig.DEBUG check disabled - always disable polling (BroadcastReceiver is authoritative)
        Log.d(TAG, "Settings polling disabled (using BroadcastReceiver as authoritative source)")
        return
        
        /* Commented out DEBUG-only polling - uncomment if needed for debugging
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "Settings polling disabled in release build")
            return
        }*/
        
        if (settingsPoller != null) return
        
        settingsPoller = object : Runnable {
            override fun run() {
                try {
                    checkAndUpdateSettings()
                    // Poll every 15 seconds (was 2s - reduced to minimize I/O churn)
                    settingsPoller?.let { mainHandler.postDelayed(it, 15000) }
                } catch (e: Exception) {
                    // Ignore polling errors
                }
            }
        }
        
        // Start polling after 15 seconds (delayed first check)
        settingsPoller?.let { mainHandler.postDelayed(it, 15000) }
        Log.d(TAG, "⚠️ Settings polling enabled (DEBUG mode only, 15s interval)")
    }
    
    private fun stopSettingsPolling() {
        settingsPoller?.let { poller ->
            mainHandler.removeCallbacks(poller)
            settingsPoller = null
        }
    }
    
    private fun checkAndUpdateSettings() {
        try {
            // Check if SharedPreferences file was modified
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSettingsCheck > 1000) { // Check at most once per second
                lastSettingsCheck = currentTime
                
                // Store current settings
                val oldTheme = currentTheme
                val oldVibration = vibrationEnabled
                val oldSwipeTyping = swipeTypingEnabled
                val oldKeyPreview = keyPreviewEnabled
                val oldAISuggestions = aiSuggestionsEnabled
                val oldNumberRow = showNumberRow
                val oldLanguageIndex = currentLanguageIndex
                
                // Reload settings using unified settings loader
                applyLoadedSettings(settingsManager.loadAll())
                
                // Check if any settings changed
                val settingsChanged = oldTheme != currentTheme ||
                        oldVibration != vibrationEnabled ||
                        oldSwipeTyping != swipeTypingEnabled ||
                        oldKeyPreview != keyPreviewEnabled ||
                        oldAISuggestions != aiSuggestionsEnabled ||
                        oldNumberRow != showNumberRow ||
                        oldLanguageIndex != currentLanguageIndex
                
                if (settingsChanged) {
                    Log.d(TAG, "Settings change detected via polling - NumberRow: $oldNumberRow->$showNumberRow, Language: $oldLanguageIndex->$currentLanguageIndex")
                    applySettingsImmediately()
                }
            }
        } catch (e: Exception) {
            // Ignore polling errors
        }
    }
    
    // ===== ADVANCED KEYBOARD FEATURES =====
    
    /**
     * Enhanced haptic feedback based on key type and intensity
     */
    private fun performAdvancedHapticFeedback(primaryCode: Int) {
        if (!canVibrate(VibrationChannel.KEY_PRESS) || vibrator == null) return

        try {
            val amplitudeStrength = when (primaryCode) {
                Keyboard.KEYCODE_DELETE, KEYCODE_SHIFT -> 210
                KEYCODE_SPACE, Keyboard.KEYCODE_DONE -> 150
                else -> 170
            }

            val duration = computeVibrationDuration(VibrationChannel.KEY_PRESS)
            if (duration <= 0) return
            val amplitude = amplitudeFromStrength(amplitudeStrength)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            // Fallback to keyboard view haptic feedback
            keyboardView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }
    
    /**
     * ✅ FIX: Public method for long-press vibration (called from SwipeKeyboardView)
     * Provides stronger haptic feedback for long-press actions
     */
    fun triggerLongPressVibration() {
        if (!canVibrate(VibrationChannel.LONG_PRESS) || vibrator == null) return

        try {
            // Long press gets slightly longer and stronger vibration
            val duration = computeVibrationDuration(VibrationChannel.LONG_PRESS)
            if (duration <= 0) return

            val amplitude = amplitudeFromStrength(230)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Long press vibration failed: ${e.message}")
        }
    }
    
    /**
     * ✅ FIX: Public method for repeated key vibration (called from UnifiedKeyboardView)
     * Provides lighter haptic feedback for repeated actions like backspace hold
     */
    fun triggerRepeatedKeyVibration() {
        if (!canVibrate(VibrationChannel.REPEATED) || vibrator == null) return

        try {
            // Repeated actions get shorter, lighter vibration to avoid overwhelming
            val duration = computeVibrationDuration(VibrationChannel.REPEATED)
            if (duration <= 0) return

            val amplitude = amplitudeFromStrength(140)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Repeated key vibration failed: ${e.message}")
        }
    }

    private fun amplitudeFromStrength(base: Int): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return VibrationEffect.DEFAULT_AMPLITUDE
        }

        val vib = vibrator
        if (vib == null || !vib.hasAmplitudeControl()) {
            return VibrationEffect.DEFAULT_AMPLITUDE
        }

        val scaled = when (hapticIntensity) {
            0 -> 0
            1 -> (base * 0.7f).roundToInt()
            2 -> base
            3 -> (base * 1.3f).roundToInt()
            else -> base
        }

        if (scaled <= 0) {
            return VibrationEffect.DEFAULT_AMPLITUDE
        }

        return scaled.coerceIn(1, 255)
    }

    private enum class VibrationChannel {
        KEY_PRESS,
        LONG_PRESS,
        REPEATED
    }

    private fun canVibrate(channel: VibrationChannel): Boolean {
        if (!vibrationEnabled) {
            // ✅ PERFORMANCE: Removed log that was firing on every key press
            return false
        }
        if (hapticIntensity <= 0) {
            // ✅ PERFORMANCE: Removed log that was firing on every key press
            return false
        }
        val channelAllowed = when (channel) {
            VibrationChannel.KEY_PRESS -> keyPressVibration
            VibrationChannel.LONG_PRESS -> longPressVibration
            VibrationChannel.REPEATED -> repeatedActionVibration
        }
        if (!channelAllowed) {
            // ✅ PERFORMANCE: Removed log that was firing on every key press
        }
        return channelAllowed
    }

    private fun computeVibrationDuration(channel: VibrationChannel): Long {
        if (hapticIntensity <= 0) return 0

        val base = vibrationMs.coerceAtLeast(10)
        val channelScale = when (channel) {
            VibrationChannel.KEY_PRESS -> 1f
            VibrationChannel.LONG_PRESS -> 1.5f
            VibrationChannel.REPEATED -> 0.6f
        }

        val intensityScale = when (hapticIntensity) {
            1 -> 0.7f
            2 -> 1.0f
            3 -> 1.4f
            else -> 1.0f
        }

        val duration = base * channelScale * intensityScale
        return duration.roundToLong().coerceAtLeast(1L)
    }
    
    /**
     * Check if a key has accent variants for long-press
     */
    private fun hasAccentVariants(primaryCode: Int): Boolean {
        return accentMap.containsKey(primaryCode)
    }
    
    /**
     * Show key preview popup above pressed key
     */
    private fun showKeyPreview(primaryCode: Int) {
        if (!keyPreviewEnabled) return
        
        try {
            // Hide any existing preview first
            hideKeyPreview()
            
            val previewText = when (primaryCode) {
                KEYCODE_SPACE -> "space"
                Keyboard.KEYCODE_DELETE -> "⌫"
                Keyboard.KEYCODE_DONE -> "↵"
                KEYCODE_SHIFT -> "⇧"
                else -> {
                    val char = primaryCode.toChar()
                    if (Character.isLetter(char)) {
                        when (shiftState) {
                            SHIFT_OFF -> char.lowercaseChar().toString()
                            SHIFT_ON, SHIFT_CAPS -> char.uppercaseChar().toString()
                            else -> char.toString()
                        }
                    } else {
                        char.toString()
                    }
                }
            }
            
            // Create preview popup with proper focus handling
            val previewView = TextView(this).apply {
                text = previewText
                textSize = 24f
                setTextColor(themeManager.getTextColor())
                setBackgroundColor(themeManager.getKeyColor()) // Use theme key background
                setPadding(16, 8, 16, 8)
            }
            
            keyPreviewPopup = PopupWindow(
                previewView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Critical: Don't steal focus from keyboard
                isFocusable = false
                isOutsideTouchable = false
                isTouchable = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                
                // Prevent input method interference
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            }
            
            // Show popup above keyboard with safe positioning
            keyboardView?.let { view ->
                try {
                    keyPreviewPopup?.showAsDropDown(view, 0, -view.height - 100, Gravity.CENTER_HORIZONTAL)
                } catch (e: Exception) {
                    // Fallback: try showing at location
                    keyPreviewPopup?.showAtLocation(view, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 100)
                }
            }
            
        } catch (e: Exception) {
            // Ignore preview errors and clean up
            hideKeyPreview()
        }
    }
    
    /**
     * Hide key preview popup
     */
    private fun hideKeyPreview() {
        try {
            keyPreviewPopup?.dismiss()
            keyPreviewPopup = null
        } catch (e: Exception) {
            // Ignore dismissal errors
        }
    }
    
    /**
     * Show accent options popup for long-press
     */
    private fun showAccentOptions(primaryCode: Int) {
        val accents = accentMap[primaryCode] ?: return
        if (accents.isEmpty()) return
        
        try {
            // Hide any existing popups first
            hideAccentOptions()
            hideKeyPreview()
            
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(themeManager.getKeyboardBackgroundColor()) // Use theme background
                setPadding(8, 8, 8, 8)
            }
            
            // Add original character first
            val originalChar = when (shiftState) {
                SHIFT_OFF -> primaryCode.toChar().lowercaseChar().toString()
                SHIFT_ON, SHIFT_CAPS -> primaryCode.toChar().uppercaseChar().toString()
                else -> primaryCode.toChar().toString()
            }
            addAccentOption(container, originalChar)
            
            // Add accent variants (apply shift state to them too)
            accents.forEach { accent ->
                val adjustedAccent = if (shiftState != SHIFT_OFF && accent.length == 1) {
                    accent.uppercase()
                } else {
                    accent
                }
                addAccentOption(container, adjustedAccent)
            }
            
            accentPopup = PopupWindow(
                container,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setBackgroundDrawable(ColorDrawable(themeManager.getKeyboardBackgroundColor())) // Use theme background
                
                // Critical: Prevent focus stealing that causes keyboard to close
                isFocusable = false  // Changed from true to false
                isOutsideTouchable = true
                isTouchable = true
                
                // Prevent input method interference
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                
                elevation = 8f
                
                // Handle outside touch to dismiss popup without affecting keyboard
                setOnDismissListener {
                    try {
                        // Clean up but don't affect keyboard focus
                        currentLongPressKey = -1
                        currentLongPressKeyObject = null
                        longPressHandler.removeCallbacks(longPressRunnable)
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                }
            }
            
            // Measure the popup to get its dimensions
            container.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth = container.measuredWidth
            val popupHeight = container.measuredHeight
            
            // Show popup above the pressed key if we have its position
            keyboardView?.let { view ->
                try {
                    val pressedKey = currentLongPressKeyObject
                    if (pressedKey != null) {
                        // Calculate position: center horizontally above the pressed key
                        val location = IntArray(2)
                        view.getLocationInWindow(location)
                        
                        val popupX = location[0] + pressedKey.x + (pressedKey.width / 2) - (popupWidth / 2)
                        val popupY = location[1] + pressedKey.y - popupHeight - 10 // 10px above the key
                        
                        accentPopup?.showAtLocation(view, Gravity.NO_GRAVITY, popupX, popupY)
                    } else {
                        // Fallback: show at top center if key position unknown
                        accentPopup?.showAsDropDown(view, 0, -view.height - 150, Gravity.CENTER_HORIZONTAL)
                    }
                } catch (e: Exception) {
                    // Fallback positioning
                    accentPopup?.showAtLocation(view, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 150)
                }
            }
            
        } catch (e: Exception) {
            // Ignore accent popup errors and clean up
            hideAccentOptions()
        }
    }
    
    /**
     * Add accent option to container
     */
    private fun addAccentOption(container: LinearLayout, accent: String) {
        val textView = TextView(this).apply {
            text = accent
            textSize = 20f
            setTextColor(themeManager.getTextColor())
            setPadding(16, 12, 16, 12)
            setBackgroundColor(themeManager.getKeyColor()) // Use theme key color for buttons
            
            // Use touch listener instead of click listener for better control
            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        setBackgroundColor(themeManager.getAccentColor()) // Visual feedback with accent
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        setBackgroundColor(themeManager.getKeyColor()) // Reset to theme key color
                        
                        // Insert the selected accent
                        try {
                            currentInputConnection?.commitText(accent, 1)
                            
                            // Handle shift state after character input
                            if (shiftState == SHIFT_ON) {
                                shiftState = SHIFT_OFF
                                keyboardView?.let {
                                    it.isShifted = false
                                    it.invalidateAllKeys()
                                }
                                caps = false
                                isShifted = false
                            }
                            
                            // Hide popup after selection
                            hideAccentOptions()
                            
                            // Update AI suggestions
                            if (areSuggestionsActive()) {
                                updateAISuggestions()
                            }
                        } catch (e: Exception) {
                            // Handle any input errors gracefully
                            hideAccentOptions()
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        setBackgroundColor(Color.LTGRAY) // Reset background
                        true
                    }
                    else -> false
                }
            }
        }
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(4, 0, 4, 0)
        }
        
        textView.layoutParams = params
        container.addView(textView)
    }
    
    /**
     * Hide accent options popup
     */
    private fun hideAccentOptions() {
        try {
            accentPopup?.dismiss()
            accentPopup = null
        } catch (e: Exception) {
            // Ignore dismissal errors
        }
    }
    
    /**
     * Initialize CleverType AI Service
     */
    
    /**
     * Initialize Enhanced Caps/Shift Manager (direct method for compatibility)
     */
    private fun initializeCapsShiftManagerDirect() {
        try {
            capsShiftManager = CapsShiftManager(this, settings)
            
            // Set up state change listener
            capsShiftManager.setOnStateChangedListener { newState ->
                updateShiftVisualState(newState)
                updateBackwardCompatibilityState(newState)
            }
            
            // Set up haptic feedback listener
            capsShiftManager.setOnHapticFeedbackListener { state ->
                performEnhancedShiftHapticFeedback(state)
            }
            
            // Set up long press menu listener
            capsShiftManager.setOnLongPressMenuListener {
                showShiftOptionsMenu()
            }
            
            // Initialize shift options menu
            shiftOptionsMenu = ShiftOptionsMenu(this, capsShiftManager).apply {
                setOnMenuItemClickListener { action ->
                    handleShiftMenuAction(action)
                }
            }
            
            Log.d(TAG, "Enhanced Caps/Shift Manager initialized successfully (direct)")

            KeyboardStateManager.updateShiftState(
                capsShiftManager.currentState != CapsShiftManager.STATE_NORMAL,
                capsShiftManager.isCapsLockActive()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Caps/Shift Manager", e)
        }
    }
    
    /**
     * Initialize Enhanced Caps/Shift Manager (legacy method - kept for compatibility)
     */
    private fun initializeCapsShiftManager() {
        initializeCapsShiftManagerDirect()
    }
    
    /**
     * Update visual state based on new caps/shift state
     */
    private fun updateShiftVisualState(newState: Int) {
        val isUpperCase = newState != CapsShiftManager.STATE_NORMAL
        val isCapsLock = newState == CapsShiftManager.STATE_CAPS_LOCK
        
        // Update legacy keyboard view (if used) with lightweight path to avoid rebuilds
        keyboardView?.let { view ->
            if (view is SwipeKeyboardView) {
                if (view.applyShiftState(isUpperCase, isCapsLock)) {
                    lastLegacyShiftVisualState = newState
                }
            } else if (newState != lastLegacyShiftVisualState) {
                view.isShifted = isUpperCase
                view.invalidateAllKeys()
                lastLegacyShiftVisualState = newState
            }
        }
        
        // ✅ Update UnifiedKeyboardView with new shift state
        KeyboardStateManager.updateShiftState(isUpperCase, isCapsLock)

        val unifiedViewActive = ::unifiedController.isInitialized &&
            unifiedKeyboardView?.isAttachedToWindow == true &&
            keyboardView !is SwipeKeyboardView

        if (unifiedViewActive) {
            unifiedController.refreshKeyboardForShiftState(newState)
        }
    }
    
    /**
     * Update backward compatibility state variables
     */
    private fun updateBackwardCompatibilityState(newState: Int) {
        // Update legacy state variables for backward compatibility
        shiftState = when (newState) {
            CapsShiftManager.STATE_NORMAL -> SHIFT_OFF
            CapsShiftManager.STATE_SHIFT -> SHIFT_ON
            CapsShiftManager.STATE_CAPS_LOCK -> SHIFT_CAPS
            else -> SHIFT_OFF
        }
        
        caps = (newState != CapsShiftManager.STATE_NORMAL)
        isShifted = caps
    }
    
    /**
     * Perform enhanced haptic feedback based on shift state
     */
    private fun performEnhancedShiftHapticFeedback(state: Int) {
        if (vibrationEnabled && vibrator != null) {
            try {
                val intensity = when (state) {
                    CapsShiftManager.STATE_NORMAL -> 15L      // Light vibration for turning off
                    CapsShiftManager.STATE_SHIFT -> 25L       // Medium vibration for shift on
                    CapsShiftManager.STATE_CAPS_LOCK -> 50L   // Strong vibration for caps lock
                    else -> 20L
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(intensity, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(intensity)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to provide enhanced shift haptic feedback: ${e.message}")
            }
        }
    }
    
    /**
     * Show shift options menu
     */
    private fun showShiftOptionsMenu() {
        keyboardView?.let { view ->
            // Find shift key position (approximate)
            val shiftKeyX = view.width / 8  // Approximate position
            val shiftKeyY = view.height - 100  // Bottom row
            
            shiftOptionsMenu?.show(view, shiftKeyX, shiftKeyY)
        }
    }
    
    /**
     * Handle shift menu actions
     */
    private fun handleShiftMenuAction(action: String) {
        when (action) {
            "caps_lock_toggle" -> {
                // Already handled by the menu
                Log.d(TAG, "Caps lock toggled via menu")
            }
            "alternate_layout" -> {
                // Switch to alternate keyboard layout (symbols/numbers)
                when (currentKeyboard) {
                    KEYBOARD_LETTERS -> switchKeyboardMode(KeyboardMode.SYMBOLS)
                    KEYBOARD_SYMBOLS -> switchKeyboardMode(KeyboardMode.NUMBERS)
                    KEYBOARD_NUMBERS -> switchKeyboardMode(KeyboardMode.LETTERS)
                }
            }
            "language_switch" -> {
                // Switch to next language
                languageManager?.switchToNextLanguage()
            }
        }
    }
    
    /**
     * Start shift key long press detection (called from SwipeKeyboardView)
     */
    fun startShiftKeyLongPressDetection() {
        if (::capsShiftManager.isInitialized) {
            capsShiftManager.startLongPressDetection()
        }
    }
    
    /**
     * Cancel shift key long press detection (called from SwipeKeyboardView)
     */
    fun cancelShiftKeyLongPressDetection() {
        if (::capsShiftManager.isInitialized) {
            capsShiftManager.cancelLongPressDetection()
        }
    }
    
    private fun startVoiceInput() {
        // Prevent duplicate starts - check if voice input is already active
        if (VoiceInputManager.isListening()) {
            Log.w(TAG, "Voice input already active; ignoring duplicate start request")
            return
        }
        
        try {
            val intent = VoiceInputActivity.createIntent(this, currentLanguage)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            showVoiceInputFeedback(true)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch voice input activity", e)
            // Toast removed - voice input error logged only
            showVoiceInputFeedback(false)
        }
    }

    fun startVoiceInputFromToolbar() {
        startVoiceInput()
    }

    private fun stopVoiceInput() {
        showVoiceInputFeedback(false)
    }

    fun handleVoiceInputResult(spokenText: String) {
        val text = spokenText.trim()
        if (text.isEmpty()) {
            Log.w(TAG, "❌ Voice input result is empty, skipping")
            return
        }
        
        Log.d(TAG, "📝 Received voice input result: '$text' (${text.length} chars)")
        
        // Move heavy operations off UI thread
        CoroutineScope(Dispatchers.Default).launch {
            // Check connection availability on background thread
            val hasConnection = withContext(Dispatchers.Main) {
                currentInputConnection != null
            }
            
            Log.d(TAG, "Input connection available: $hasConnection")
            
            if (!hasConnection) {
                // No connection yet, queue and retry
                withContext(Dispatchers.Main) {
                    pendingVoiceResult = text
                    Log.w(TAG, "⏳ Voice result queued (no active input connection) - will retry")
                    schedulePendingVoiceFlush()
                }
                return@launch
            }
            
            // Commit on main thread (InputConnection must be accessed on main thread)
            val committed = withContext(Dispatchers.Main) {
                commitVoiceResultInternal(text)
            }
            
            if (!committed) {
                withContext(Dispatchers.Main) {
                    pendingVoiceResult = text
                    Log.w(TAG, "⏳ Voice result failed to commit - will retry")
                    schedulePendingVoiceFlush()
                }
            } else {
                withContext(Dispatchers.Main) {
                    pendingVoiceResult = null
                    Log.d(TAG, "✅ Voice result committed successfully - panel stays open for next input")
                }
            }
        }
    }

    fun onVoiceInputClosed() {
        mainHandler.post {
            showVoiceInputFeedback(false)
            // Clear any pending voice results when panel is explicitly closed
            pendingVoiceResult = null
            mainHandler.removeCallbacks(pendingVoiceFlushRunnable)
            
            // Mark that we're returning from voice panel to prevent height reset
            KeyboardHeightManager.markReturningFromVoicePanel()
            
            // Force keyboard height recalculation instead of restoring cached value
            CoroutineScope(Dispatchers.Default).launch {
                delay(100) // Small delay to ensure voice panel is fully closed
                withContext(Dispatchers.Main) {
                    val computedHeight = keyboardHeightManager.calculateKeyboardHeight(
                        includeToolbar = true,
                        includeSuggestions = true
                    )
                    KeyboardHeightManager.applyKeyboardHeight(this@AIKeyboardService, computedHeight)
                }
            }
        }
    }

    private fun commitVoiceResultInternal(text: String): Boolean {
        // Get a fresh InputConnection reference
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TAG, "❌ Cannot commit voice input: no input connection available")
            return false
        }
        
        try {
            // Verify the InputConnection is still valid by testing it
            val testText = ic.getTextBeforeCursor(1, 0)
            if (testText == null) {
                Log.w(TAG, "❌ InputConnection appears to be stale or invalid")
                return false
            }
            
            Log.d(TAG, "InputConnection validated, committing text...")
            
            // Use batch edit for more reliable text insertion
            ic.beginBatchEdit()
            try {
                val textToCommit = "$text "
                
                // Alternative approach: Use finishComposingText + commitText
                ic.finishComposingText()
                val committed = ic.commitText(textToCommit, 1)
                
                Log.d(TAG, "✅ Voice input committed: $text (commitText returned=$committed)")
                
                // Verify the text was actually inserted
                val afterText = ic.getTextBeforeCursor(textToCommit.length + 10, 0)?.toString() ?: ""
                val cursorPos = afterText.length
                Log.d(TAG, "After commit: cursor position=$cursorPos, recent text='${afterText.takeLast(50)}'")
                
                // Check if our text is in the editor
                if (afterText.contains(text)) {
                    Log.d(TAG, "✅ Verified: Text successfully inserted into editor")
                    return true
                } else {
                    Log.w(TAG, "⚠️ Warning: Text may not have been inserted (verification failed)")
                    return false
                }
            } finally {
                ic.endBatchEdit()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error committing voice input: ${e.message}", e)
            return false
        } finally {
            // Refresh suggestions after voice commit once the connection settles
            CoroutineScope(Dispatchers.Default).launch {
                delay(50)
                updateAISuggestions()
            }
        }
    }

    private fun flushPendingVoiceResult() {
        val pending = pendingVoiceResult?.takeIf { it.isNotBlank() } ?: return
        val committed = commitVoiceResultInternal(pending)
        if (committed) {
            pendingVoiceResult = null
        } else {
            schedulePendingVoiceFlush()
        }
    }

    private fun schedulePendingVoiceFlush() {
        mainHandler.removeCallbacks(pendingVoiceFlushRunnable)
        if (!pendingVoiceResult.isNullOrBlank()) {
            mainHandler.postDelayed(pendingVoiceFlushRunnable, VOICE_RESULT_FLUSH_INTERVAL_MS)
        }
    }

    /**
     * Show visual feedback for voice input state
     */
    fun showVoiceInputFeedback(isActive: Boolean) {
        keyboardView?.let { view ->
            // Update voice key appearance
            view.setVoiceKeyActive(isActive)
            
        }
    }
    
    /**
     * Enhanced emoji panel toggle with state management
     */
    private fun handleEmojiToggle() {
        try {
            val wasVisible = isEmojiPanelVisible
            toggleEmojiPanel()
            
            // Update emoji key visual state
            keyboardView?.setEmojiKeyActive(!wasVisible)
            
            Log.d(TAG, "Comprehensive emoji panel toggled: ${!wasVisible}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling emoji panel", e)
        }
    }
    
    // ✅ Updated to use UnifiedPanelManager
    private fun toggleEmojiPanel() {
        try {
            val wasVisible = unifiedPanelManager?.isPanelVisible() == true && 
                           unifiedPanelManager?.getCurrentPanelType() == UnifiedPanelManager.PanelType.EMOJI
            
            if (wasVisible) {
                // ✅ Return to typing mode
                unifiedKeyboardView?.backToTyping()
            } else {
                // ✅ Show emoji panel
                val emojiPanel = unifiedPanelManager?.buildPanel(UnifiedPanelManager.PanelType.EMOJI)
                emojiPanel?.let { unifiedKeyboardView?.showPanel(it) }
            }
            
            keyboardView?.setEmojiKeyActive(!wasVisible)
            Log.d(TAG, "Emoji panel toggled: visible=${!wasVisible}")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling emoji panel", e)
        }
    }
    
    /**
     * Enhanced context-aware enter key handler (Gboard-style)
     */
    private fun handleEnterKey(ic: InputConnection) {
        try {
            // Determine enter key behavior based on input context
            val inputType = currentInputEditorInfo?.inputType ?: 0
            val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
            val imeAction = imeOptions and EditorInfo.IME_MASK_ACTION
            val isMultiline = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
            
            // Smart return button behavior based on app context
            when {
                // For messaging apps or multiline fields - insert newline
                isMultiline || imeAction == EditorInfo.IME_ACTION_NONE -> {
                    ic.commitText("\n", 1)
                    Log.d(TAG, "Enter key: Newline inserted (multiline/messaging app)")
                }
                // For send action (common in messaging apps)
                imeAction == EditorInfo.IME_ACTION_SEND -> {
                    ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    performAdvancedHapticFeedback(Keyboard.KEYCODE_DONE)
                    Log.d(TAG, "Enter key: Send action")
                }
                // For go action (URLs, search bars)
                imeAction == EditorInfo.IME_ACTION_GO -> {
                    ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                    performAdvancedHapticFeedback(Keyboard.KEYCODE_DONE)
                    Log.d(TAG, "Enter key: Go action")
                }
                // For search action
                imeAction == EditorInfo.IME_ACTION_SEARCH -> {
                    ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                    performAdvancedHapticFeedback(Keyboard.KEYCODE_DONE)
                    Log.d(TAG, "Enter key: Search action")
                }
                // For next field action
                imeAction == EditorInfo.IME_ACTION_NEXT -> {
                    ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
                    performAdvancedHapticFeedback(Keyboard.KEYCODE_DONE)
                    Log.d(TAG, "Enter key: Next field action")
                }
                // For done action (default for single line)
                imeAction == EditorInfo.IME_ACTION_DONE -> {
                    ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                    performAdvancedHapticFeedback(Keyboard.KEYCODE_DONE)
                    Log.d(TAG, "Enter key: Done action")
                }
                // Default fallback - insert newline
                else -> {
                    ic.commitText("\n", 1)
                    Log.d(TAG, "Enter key: Fallback newline")
                }
            }
            
            // Clear current word after enter
            currentWord = ""
            
            // Enhanced auto-capitalization after enter via unified controller
            if (::unifiedController.isInitialized) {
                unifiedController.handleEnterPress()
            } else if (::capsShiftManager.isInitialized) {
                capsShiftManager.handleEnterPress(ic, inputType)
            }
            
            // Update suggestions
            if (areSuggestionsActive()) {
                updateAISuggestions()
            }
            
            Log.d(TAG, "Enter key handled - inputType: $inputType, imeOptions: $imeOptions")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling enter key", e)
            // Fallback to basic newline
            ic.commitText("\n", 1)
        }
    }
    
    /**
     * Enhanced emoji insertion with proper cursor positioning
     */
    private fun insertEmojiWithCursor(emoji: String) {
        try {
            val ic = currentInputConnection ?: return
            
            // Use the cursor-aware text handler for consistent emoji insertion
            if (CursorAwareTextHandler.insertEmoji(ic, emoji)) {
                Log.d(TAG, "Successfully inserted emoji '$emoji' using CursorAwareTextHandler")
            } else {
                // Fallback to simple insertion
                ic.commitText(emoji, 1)
                Log.d(TAG, "Used fallback emoji insertion for '$emoji'")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting emoji with cursor", e)
            // Fallback to simple insertion
            currentInputConnection?.commitText(emoji, 1)
        }
    }
    
    /**
     * Replace text with AI result
     */
    private fun replaceTextWithResult(originalText: String, newText: String) {
        try {
            val ic = currentInputConnection ?: return
            
            // Check if there was a selection
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrEmpty()) {
                // Replace selected text
                ic.commitText(newText, 1)
                Log.d(TAG, "Replaced selected text with AI result")
            } else {
                // Replace all text before cursor
                val extractedText = ic.getExtractedText(ExtractedTextRequest(), 0)
                if (extractedText != null) {
                    // Delete old text and insert new text
                    ic.deleteSurroundingText(originalText.length, 0)
                    ic.commitText(newText, 1)
                    Log.d(TAG, "Replaced full text with AI result")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing text with result", e)
        }
    }
    
    /**
     * Update UnifiedSuggestionController with current settings from SharedPreferences
     * Called when settings change from Flutter
     */
    private fun updateSuggestionControllerSettings() {
        try {
            // Read suggestion settings from SharedPreferences
            val prefs = getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            
            // ✅ FIXED: Use correct Flutter SharedPreferences keys (with underscores, not camelCase!)
            val displaySuggestions = flutterPrefs.getBoolean("flutter.display_suggestions", true)
            val displayMode = flutterPrefs.getString("flutter.display_mode", "3")
            val internalClipboard = flutterPrefs.getBoolean("flutter.internal_clipboard", true)
            val aiSuggestions = prefs.getBoolean("ai_suggestions", true)
            val autoFill = when {
                flutterPrefs.contains("flutter.auto_fill_suggestion") ->
                    flutterPrefs.getBoolean("flutter.auto_fill_suggestion", true)
                flutterPrefs.contains("flutter.autoFillSuggestion") ->
                    flutterPrefs.getBoolean("flutter.autoFillSuggestion", true)
                else -> prefs.getBoolean("auto_fill_suggestion", true)
            }
            autoFillSuggestionsEnabled = autoFill
            aiSuggestionsEnabled = aiSuggestions
            val effectiveAiSuggestions = aiSuggestions && displaySuggestions
            
            // ✅ Parse display mode to get suggestion count (3 or 4)
            suggestionCount = when (displayMode) {
                "4" -> 4
                else -> 3  // Default to 3 for any other value including "3", "dynamic", "scrollable"
            }
            
            // Update UnifiedKeyboardView with the new suggestion count
            unifiedKeyboardView?.setSuggestionDisplayCount(suggestionCount)
            suggestionsEnabled = displaySuggestions
            unifiedKeyboardView?.setSuggestionsEnabled(displaySuggestions)
            
            Log.d(TAG, "📱 Updating suggestion controller: DisplaySuggestions=$displaySuggestions, DisplayMode=$displayMode, Count=$suggestionCount, AI=$effectiveAiSuggestions (raw=$aiSuggestions), AutoFill=$autoFill, Clipboard=$internalClipboard")
            
            // ✅ Update UnifiedSuggestionController with settings
            if (::unifiedSuggestionController.isInitialized) {
                unifiedSuggestionController.updateSettings(
                    aiEnabled = effectiveAiSuggestions,
                    emojiEnabled = displaySuggestions,
                    clipboardEnabled = internalClipboard,
                    nextWordEnabled = displaySuggestions && autoFill  // Controlled by auto-fill toggle
                )
                Log.d(TAG, "✅ UnifiedSuggestionController settings updated")
            }
            
            // 🔥 FIX 3.2 - Don't clear cache on settings change, let it naturally expire
            // suggestionCache.clear() - REMOVED for better performance
            
            // Force refresh suggestions if keyboard is active
            if (suggestionContainer != null) {
                clearSuggestions()
                Log.d(TAG, "✅ Cleared suggestions after settings change")
            }
            
            if (!areSuggestionsActive()) {
                suggestionUpdateJob?.cancel()
                clearSuggestions()
            }
            
            // Update UnifiedSuggestionController (new system)
            // Note: Initialize this in initializeCoreComponents() when ready to use
            // if (::unifiedSuggestionController.isInitialized) {
            //     unifiedSuggestionController.updateSettings(
            //         aiEnabled = aiSuggestions,
            //         emojiEnabled = emojiSuggestions,
            //         clipboardEnabled = clipboardSuggestions,
            //         nextWordEnabled = nextWordPrediction
            //     )
            //     Log.d(TAG, "✅ UnifiedSuggestionController settings updated")
            // }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating suggestion controller settings", e)
        }
    }
    
    // ========================================
    // UNIFIED FEATURE PANEL SYSTEM
    // ========================================

    fun showUnifiedPanel(panelType: UnifiedPanelManager.PanelType) {
        try {
            val manager = unifiedPanelManager ?: return
            val panel = manager.buildPanel(panelType)
            unifiedKeyboardView?.showPanel(panel)
            Log.d(TAG, "✅ Unified panel displayed: $panelType")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unable to display unified panel $panelType", e)
            // Toast removed - panel error logged only
        }
    }
    
    /**
     * Show unified feature panel (replaces keyboard with dynamic panel)
     * ✅ UPDATED TO USE UnifiedPanelManager - 100% Dynamic, Zero XML
     * Shows ONLY the panel - no suggestion bar, no toolbar
     */
    private fun showFeaturePanel(type: PanelType) {
        try {
            Log.d(TAG, "✅ Opening panel via UnifiedPanelManager: $type")
            
            // Hide keyboard view
            keyboardView?.visibility = View.GONE
            
            // Hide suggestion bar
            suggestionContainer?.visibility = View.GONE
            
            // Hide toolbar
            topContainer?.findViewById<LinearLayout>(R.id.keyboard_toolbar_simple)?.visibility = View.GONE
            
            // Get current input text for AI processing
            val inputText = getCurrentInputText()
            currentAiSourceText = inputText
            unifiedPanelManager?.setInputText(inputText)
            
            // Map old PanelType to UnifiedPanelManager.PanelType
            val unifiedPanelType = when (type) {
                PanelType.GRAMMAR_FIX -> UnifiedPanelManager.PanelType.GRAMMAR
                PanelType.WORD_TONE -> UnifiedPanelManager.PanelType.TONE
                PanelType.AI_ASSISTANT -> UnifiedPanelManager.PanelType.AI_ASSISTANT
                PanelType.CLIPBOARD -> UnifiedPanelManager.PanelType.CLIPBOARD
                PanelType.QUICK_SETTINGS -> UnifiedPanelManager.PanelType.SETTINGS
                PanelType.EMOJI -> UnifiedPanelManager.PanelType.EMOJI
                else -> {
                    Log.w(TAG, "Unknown panel type: $type, defaulting to AI_ASSISTANT")
                    UnifiedPanelManager.PanelType.AI_ASSISTANT
                }
            }
            
            // ✅ Build and show panel via UnifiedKeyboardView
            val panel = unifiedPanelManager?.buildPanel(unifiedPanelType)
            panel?.let { unifiedKeyboardView?.showPanel(it) }
            
            Log.d(TAG, "✅ Panel displayed successfully: $unifiedPanelType (toolbar & suggestions hidden)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing feature panel", e)
            // Toast removed - panel error logged only
        }
    }
    
    // ========================================
    // LEGACY METHODS REMOVED - All panel functionality moved to UnifiedPanelManager
    // Use: unifiedPanelManager?.showPanel(UnifiedPanelManager.PanelType.XXX)
    // ========================================
    
    /**
     * Restore keyboard from feature panel
     * ✅ REFACTORED: Now handled by UnifiedKeyboardView.backToTyping()
     */
    private fun restoreKeyboardFromPanel() {
        try {
            // ✅ Return to typing mode in unified view
            unifiedKeyboardView?.backToTyping()
            
            // Show suggestions and toolbar when returning from panel
            suggestionContainer?.visibility = View.VISIBLE
            topContainer?.findViewById<LinearLayout>(R.id.keyboard_toolbar_simple)?.visibility = View.VISIBLE
            
            
            currentAiSourceText = ""
            Log.d(TAG, "✅ Keyboard restored from panel (toolbar shown)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring keyboard from panel", e)
        }
    }
    
    /**
     * Get selected text or text around cursor
     */
    private fun getSelectedText(): String {
        val ic = currentInputConnection ?: return ""
        
        try {
            // Try to get selected text first
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrEmpty()) {
                return selectedText.toString()
            }
            
            // If no selection, get text around cursor (sentence)
            val beforeCursor = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val afterCursor = ic.getTextAfterCursor(100, 0)?.toString() ?: ""
            
            // Find sentence boundaries
            val sentences = (beforeCursor + afterCursor).split(Regex("[.!?]\\s*"))
            if (sentences.isNotEmpty()) {
                // Return the sentence that contains the cursor
                val cursorPos = beforeCursor.length
                var charCount = 0
                for (sentence in sentences) {
                    charCount += sentence.length + 1
                    if (charCount > cursorPos) {
                        return sentence.trim()
                    }
                }
            }
            
            // Fallback: return text around cursor
            return (beforeCursor + afterCursor).trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting selected text", e)
            return ""
        }
    }
    
    /**
     * Save keyboard settings to SharedPreferences
     */
    private fun saveSettings() {
        try {
            settings.edit().apply {
                putBoolean("show_number_row", showNumberRow)
                putBoolean("swipe_enabled", swipeEnabled)
                putBoolean("vibration_enabled", vibrationEnabled)
                putBoolean("sound_enabled", soundEnabled)
                putBoolean("key_press_sounds", keyPressSounds)
                putBoolean("long_press_sounds", longPressSounds)
                putBoolean("repeated_action_sounds", repeatedActionSounds)
                putFloat("sound_volume", soundVolume)
                putInt("sound_intensity", soundIntensity)
                if (customSoundUri.isNullOrBlank()) {
                    remove("sound_custom_uri")
                } else {
                    putString("sound_custom_uri", customSoundUri)
                }
                putBoolean("key_press_vibration", keyPressVibration)
                putBoolean("long_press_vibration", longPressVibration)
                putBoolean("repeated_action_vibration", repeatedActionVibration)
                putInt("vibration_ms", vibrationMs)
                putBoolean("use_haptic_interface", useHapticInterface)
                apply()
            }
            Log.d(TAG, "Settings saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings", e)
        }
    }
    
    /**
     * Toggle number row setting
     */
    fun toggleNumberRow() {
        showNumberRow = !showNumberRow
        saveSettings()
        
        // Reload keyboard with/without number row
        reloadKeyboard()
        
        // Toast removed - number row toggle logged only
        Log.d(TAG, "Number row toggled: $showNumberRow")
    }
    
    /**
     * Toggle swipe typing
     */
    fun toggleSwipeTyping() {
        swipeEnabled = !swipeEnabled
        saveSettings()
        // Toast removed - swipe typing toggle logged only
        Log.d(TAG, "Swipe typing toggled: $swipeEnabled")
        refreshSwipeCapability("toggleSwipeTyping")
    }
    
    /**
     * Toggle vibration feedback
     */
    fun toggleVibration() {
        vibrationEnabled = !vibrationEnabled
        keyPressVibration = vibrationEnabled
        longPressVibration = vibrationEnabled
        repeatedActionVibration = vibrationEnabled
        if (vibrationEnabled && vibrationMs <= 0) {
            vibrationMs = 50
        }
        saveSettings()
        // Toast removed - vibration toggle logged only
        Log.d(TAG, "Vibration toggled: $vibrationEnabled")
    }
    
    /**
     * Toggle sound feedback
     */
    fun toggleSound() {
        soundEnabled = !soundEnabled
        keyPressSounds = soundEnabled
        longPressSounds = soundEnabled
        repeatedActionSounds = soundEnabled
        if (soundEnabled && soundIntensity == 0) {
            soundIntensity = 2
        }
        if (soundEnabled && soundVolume <= 0f) {
            soundVolume = 0.65f
        }
        
        // ✅ FIX: Update global sound manager enabled flag
        KeyboardSoundManager.isEnabled = soundEnabled
        configureSoundManager()
        saveSettings()
        // Toast removed - sound toggle logged only
        Log.d(TAG, "Sound toggled: $soundEnabled")
    }

    fun isNumberRowEnabled(): Boolean = showNumberRow

    fun isSoundEnabled(): Boolean = soundEnabled

    fun isVibrationEnabled(): Boolean = vibrationEnabled

    fun toggleAutoCorrect() {
        val newState = !isAutoCorrectEnabled()
        settings.edit().putBoolean("auto_correct", newState).apply()
        // Toast removed - auto-correct toggle logged only
        Log.d(TAG, "Auto-correct toggled: $newState")
    }

    fun toggleOneHandedMode() {
        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val current = flutterPrefs.getBoolean("flutter.keyboard_settings.one_handed_mode", false)
        val newState = !current
        flutterPrefs.edit().putBoolean("flutter.keyboard_settings.one_handed_mode", newState).apply()

        val widthPct = flutterPrefs
            .getFloat("flutter.keyboard_settings.one_handed_mode_width", 87.0f)
            .coerceIn(60f, 90f) / 100f
        val side = flutterPrefs.getString("flutter.keyboard.oneHanded.side", "right") ?: "right"

        unifiedKeyboardView?.setOneHandedMode(newState, side, widthPct)
        // Toast removed - one-handed mode toggle logged only
        Log.d(TAG, "One-handed mode toggled: $newState@$side(${(widthPct * 100).toInt()}%)")
    }

    fun applyOneHandedMode(enabled: Boolean, side: String, widthFraction: Float) {
        val normalizedSide = if (side.equals("left", ignoreCase = true)) "left" else "right"
        val clampedWidth = widthFraction.coerceIn(0.6f, 0.9f)
        val widthPercent = (clampedWidth * 100f).roundToInt()

        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit()
            .putBoolean("flutter.keyboard_settings.one_handed_mode", enabled)
            .putString("flutter.keyboard.oneHanded.side", normalizedSide)
            .putFloat("flutter.keyboard_settings.one_handed_mode_width", widthPercent.toFloat())
            .putFloat("flutter.keyboard.oneHanded.widthPct", clampedWidth)
            .apply()

        if (::keyboardSettings.isInitialized) {
            keyboardSettings = keyboardSettings.copy(
                oneHandedMode = enabled,
                oneHandedModeWidth = widthPercent.toDouble()
            )
        }

        unifiedKeyboardView?.setOneHandedMode(enabled, normalizedSide, clampedWidth)
    }

    fun isOneHandedModeEnabled(): Boolean {
        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return flutterPrefs.getBoolean("flutter.keyboard_settings.one_handed_mode", false)
    }
    
    /**
     * Reload keyboard with current settings
     */
    private fun reloadKeyboard(force: Boolean = false) {
        try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val newLang = currentLanguage
            val newMode = currentKeyboardMode
            val numberRowEnabled = prefs.getBoolean("flutter.keyboard_settings.number_row", showNumberRow)

            if (!force &&
                lastAppliedLang == newLang &&
                lastAppliedMode == newMode &&
                lastNumberRowState == numberRowEnabled
            ) {
                Log.d(TAG, "⏸️ Skipping redundant keyboard reload (no effective change)")
                return
            }

            lastAppliedLang = newLang
            lastAppliedMode = newMode
            lastNumberRowState = numberRowEnabled

            showNumberRow = numberRowEnabled
            keyboardHeightManager.setNumberRowEnabled(numberRowEnabled)
            unifiedKeyboardView?.setNumberRowEnabled(numberRowEnabled)

            val canReuse = unifiedKeyboardView != null && ::unifiedController.isInitialized
            val enableNumberRow = numberRowEnabled && newMode == KeyboardMode.LETTERS

            if (canReuse && !force) {
                unifiedController.buildAndRender(
                    newLang,
                    newMode.toLayoutMode(),
                    enableNumberRow
                )
                rebindKeyboardListener()
                refreshSwipeCapability("reloadKeyboardReuse")
                Log.d(TAG, "♻️ Reusing existing UnifiedKeyboardView")
                return
            }

            Log.d(TAG, "🚀 Reloading keyboard layout (force=$force)")

            if (canReuse) {
                unifiedController.buildAndRender(
                    newLang,
                    newMode.toLayoutMode(),
                    enableNumberRow
                )
                rebindKeyboardListener()
                refreshSwipeCapability("reloadKeyboardForce")
            } else {
                requestShowSelf(InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading keyboard", e)
        }
    }
    
    /**
     * Update settings from main app
     */
    fun updateSettingsFromApp(settingsMap: Map<String, Any>) {
        try {
            settingsMap["show_number_row"]?.let { 
                showNumberRow = it as Boolean
                reloadKeyboard()
            }
            settingsMap["swipe_enabled"]?.let { 
                swipeEnabled = it as Boolean
                refreshSwipeCapability("appSettings")
            }
            settingsMap["vibration_enabled"]?.let { 
                vibrationEnabled = it as Boolean
            }
            settingsMap["sound_enabled"]?.let { 
                soundEnabled = it as Boolean
            }
            settingsMap["ai_suggestions"]?.let { 
                aiSuggestionsEnabled = it as Boolean
            }
            settingsMap["key_preview_enabled"]?.let { 
                keyPreviewEnabled = it as Boolean
                keyboardView?.isPreviewEnabled = keyPreviewEnabled
            }
            
            // Save settings
            saveSettings()
            
            Log.d(TAG, "Settings updated from app: $settingsMap")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating settings from app", e)
        }
    }
    
    /**
     * Get current settings for app
     */
    fun getCurrentSettings(): Map<String, Any> {
        return mapOf(
            "show_number_row" to showNumberRow,
            "swipe_enabled" to swipeEnabled,
            "vibration_enabled" to vibrationEnabled,
            "sound_enabled" to soundEnabled,
            "ai_suggestions" to aiSuggestionsEnabled,
            "key_preview_enabled" to keyPreviewEnabled,
            "current_language" to currentLanguage
        )
    }
    
    /**
     * Initialize theme MethodChannel for Flutter communication
     */
    private fun initializeThemeChannel() {
        try {
            // Theme updates are currently handled via broadcast receiver
            // This provides a foundation for future MethodChannel integration when needed
            Log.d(TAG, "Theme communication ready (broadcast-based)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up theme communication", e)
        }
    }
    private fun buildClipboardSuggestionText(): String? {
        if (!::clipboardHistoryManager.isInitialized) return null
        if (!clipboardSuggestionEnabled || !clipboardHistoryManager.isEnabled()) return null
        if (clipboardSuggestionConsumed) return null
        if (currentWord.isNotEmpty()) return null
        val item = clipboardHistoryManager.getMostRecentItem() ?: return null
        val sanitized = item.text.replace("\\s+".toRegex(), " ").trim()
        if (sanitized.isEmpty()) return null
        return sanitized.take(60)
    }

    private fun injectClipboardSuggestion(base: List<String>): List<String> {
        val clipText = buildClipboardSuggestionText()
        if (clipText == null) {
            pendingClipboardSuggestionText = null
            return base
        }
        pendingClipboardSuggestionText = clipText
        val max = suggestionCount.coerceAtLeast(1)
        val result = mutableListOf<String>()
        result.add(clipText)
        base.forEach { suggestion ->
            if (!suggestion.equals(clipText, ignoreCase = true) && result.size < max) {
                result.add(suggestion)
            }
        }
        if (result.size < max) {
            base.forEach { suggestion ->
                if (!result.contains(suggestion) && result.size < max) {
                    result.add(suggestion)
                }
            }
        }
        return result.take(max)
    }

    /**
     * Reload clipboard settings from SharedPreferences
     */
    private fun reloadEmojiSettings() {
        try {
            // ✅ Emoji settings now handled by UnifiedPanelManager
            // Emoji panel is recreated when shown, so no need to reload settings explicitly
            Log.d(TAG, "Emoji settings reload request received (handled by UnifiedPanelManager)")
            return
            
            /* OLD CODE:
            // Reload emoji panel settings (legacy)
            // gboardEmojiPanel removed - using EmojiPanelController only
            
            // Reload settings in new controller (includes theme)
            emojiPanelController?.reloadEmojiSettings()
            
            Log.d(TAG, "Emoji settings reloaded successfully")
            */
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading emoji settings", e)
        }
    }
    
    fun reloadClipboardSettings() {
        try {
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val legacyPrefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)

            val enabled = when {
                flutterPrefs.contains("flutter.clipboard_history") ->
                    flutterPrefs.getBoolean("flutter.clipboard_history", true)
                else -> legacyPrefs.getBoolean("clipboard_enabled", true)
            }

            val suggestionEnabled = when {
                flutterPrefs.contains("flutter.clipboard_suggestion_enabled") ->
                    flutterPrefs.getBoolean("flutter.clipboard_suggestion_enabled", true)
                else -> enabled
            }

            val maxHistorySize = when {
                flutterPrefs.contains("flutter.history_size") ->
                    flutterPrefs.getFloatCompat("flutter.history_size", 20f).roundToInt().coerceAtLeast(1)
                else -> legacyPrefs.getInt("max_history_size", 20)
            }

            val cleanOldHistoryMinutes = when {
                flutterPrefs.contains("flutter.clean_old_history_minutes") ->
                    flutterPrefs.getFloatCompat("flutter.clean_old_history_minutes", 60f).toLong()
                else -> legacyPrefs.getLong("expiry_duration_minutes", 60L)
            }

            val autoExpiryEnabled = cleanOldHistoryMinutes > 0
            val expiryDurationMinutes = if (autoExpiryEnabled) cleanOldHistoryMinutes else 60L

            clipboardSuggestionEnabled = suggestionEnabled
            clipboardHistoryManager.updateSettings(
                enabled = enabled,
                maxHistorySize = maxHistorySize,
                autoExpiryEnabled = autoExpiryEnabled,
                expiryDurationMinutes = expiryDurationMinutes
            )
            unifiedKeyboardView?.setClipboardButtonVisible(enabled)

            Log.d(TAG, "Clipboard settings reloaded: enabled=$enabled, suggest=$suggestionEnabled, maxSize=$maxHistorySize, autoExpiry=$autoExpiryEnabled")

            updateClipboardStrip()
            // ✅ Clipboard suggestions now handled by UnifiedSuggestionController

        } catch (e: Exception) {
            Log.e(TAG, "Error reloading clipboard settings", e)
        }
    }
    
    
   
  
    
    
    // ====== CLIPBOARD & DICTIONARY ENHANCEMENTS ======
    
    /**
     * Update clipboard strip with recent/pinned items
     */
    private fun updateClipboardStrip() {
        try {
            mainHandler.post {
                val items = clipboardHistoryManager.getHistoryForUI(5)
                clipboardStripView?.updateItems(items)
                Log.d(TAG, "Updated clipboard strip with ${items.size} items")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating clipboard strip", e)
        }
    }

    private fun notifyClipboardHistoryChanged(count: Int) {
        try {
            val intent = Intent(ClipboardBroadcasts.ACTION_CLIPBOARD_HISTORY_UPDATED).apply {
                setPackage(packageName)
                putExtra("count", count)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast clipboard history update", e)
        }
    }

    private fun notifyClipboardNewItem(item: ClipboardItem) {
        try {
            val intent = Intent(ClipboardBroadcasts.ACTION_CLIPBOARD_NEW_ITEM).apply {
                setPackage(packageName)
                putExtra("id", item.id)
                putExtra("text", item.text)
                putExtra("timestamp", item.timestamp)
                putExtra("isOTP", item.isOTP())
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast new clipboard item", e)
        }
    }
    
    private fun refreshUnifiedClipboardPanel() {
        val manager = unifiedPanelManager ?: return
        manager.invalidateClipboardPanel()
        if (manager.getCurrentPanelType() == UnifiedPanelManager.PanelType.CLIPBOARD) {
            val panelView = manager.buildPanel(UnifiedPanelManager.PanelType.CLIPBOARD)
            unifiedKeyboardView?.showPanel(panelView)
        }
    }

    fun getClipboardHistoryItems(limit: Int = 20): List<ClipboardItem> {
        return if (::clipboardHistoryManager.isInitialized) {
            clipboardHistoryManager.getHistoryForUI(limit)
        } else {
            loadClipboardItemsFallback(limit)
        }
    }

    fun deleteClipboardItem(itemId: String): Boolean {
        if (!::clipboardHistoryManager.isInitialized) return false
        val deleted = clipboardHistoryManager.deleteItem(itemId)
        if (deleted) {
            updateClipboardStrip()
        }
        return deleted
    }

    fun clearClipboardHistory(): Boolean {
        if (!::clipboardHistoryManager.isInitialized) return false
        val cleared = clipboardHistoryManager.clearNonPinnedItems()
        if (cleared) {
            updateClipboardStrip()
        }
        return cleared
    }

    fun isClipboardHistoryEnabled(): Boolean {
        return when {
            ::clipboardHistoryManager.isInitialized -> clipboardHistoryManager.isEnabled()
            else -> {
                val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                when {
                    flutterPrefs.contains("flutter.clipboard_history") ->
                        flutterPrefs.getBoolean("flutter.clipboard_history", true)
                    else -> getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
                        .getBoolean("clipboard_enabled", true)
                }
            }
        }
    }

    fun toggleClipboardHistory(): Boolean {
        if (!::clipboardHistoryManager.isInitialized) {
            val current = isClipboardHistoryEnabled()
            val newState = !current
            persistClipboardEnabledState(newState)
            unifiedKeyboardView?.setClipboardButtonVisible(newState)
            // Toast removed - clipboard toggle logged only
            return newState
        }

        val newState = !clipboardHistoryManager.isEnabled()
        clipboardHistoryManager.updateSettings(enabled = newState)
        persistClipboardEnabledState(newState)
        unifiedKeyboardView?.setClipboardButtonVisible(newState)

        if (::unifiedSuggestionController.isInitialized) {
            unifiedSuggestionController.updateSettings(clipboardEnabled = newState)
        }

        clipboardSuggestionConsumed = clipboardSuggestionConsumed || !newState
        updateClipboardStrip()

        // Toast removed - clipboard toggle logged only

        return newState
    }

    private fun loadClipboardItemsFallback(limit: Int): List<ClipboardItem> {
        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val flutterItems = flutterPrefs.getString("flutter.clipboard_items", null)
        parseClipboardItemsJson(flutterItems, limit)?.let { return it }

        val legacyPrefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val legacyItems = legacyPrefs.getString("history_items", null)
        parseClipboardItemsJson(legacyItems, limit)?.let { return it }

        return emptyList()
    }

    private fun parseClipboardItemsJson(json: String?, limit: Int): List<ClipboardItem>? {
        if (json.isNullOrBlank()) return null
        return try {
            val items = mutableListOf<ClipboardItem>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(ClipboardItem.fromJson(obj))
                if (items.size >= limit) break
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clipboard fallback items", e)
            null
        }
    }

    private fun persistClipboardEnabledState(enabled: Boolean) {
        val legacyPrefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        legacyPrefs.edit().putBoolean("clipboard_enabled", enabled).apply()

        val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        flutterPrefs.edit().putBoolean("flutter.clipboard_history", enabled).apply()
    }
    
    /**
     * Check if current word should be auto-expanded from dictionary
     * Called on space/punctuation
     */
    private fun checkDictionaryExpansion(word: String) {
        if (!dictionaryEnabled || word.isBlank()) return
        
        try {
            val expansion = dictionaryManager.getExpansion(word)
            if (expansion != null) {
                val ic = currentInputConnection ?: return
                
                // Get text before cursor to verify the word is there
                val textBefore = ic.getTextBeforeCursor(word.length + 10, 0)?.toString() ?: ""
                Log.d(TAG, "🔍 Checking expansion for '$word', text before: '$textBefore'")
                
                // Verify the word actually exists at the end of the text
                val wordInText = textBefore.takeLast(word.length).lowercase()
                if (wordInText == word.lowercase()) {
                    // Delete the shortcut that was just typed
                    ic.deleteSurroundingText(word.length, 0)
                    
                    // Insert the expansion with a space
                    ic.commitText("${expansion.expansion} ", 1)
                    
                    // ✅ Set space timestamp for double-space period detection
                    lastSpaceTimestamp = SystemClock.uptimeMillis()
                    
                    // ✅ Track expansion for backspace undo
                    lastExpansion = word to expansion.expansion
                    
                    // Increment usage count
                    dictionaryManager.incrementUsage(word)
                    
                    Log.d(TAG, "✅ Dictionary expansion: $word -> ${expansion.expansion} (space timestamp set)")
                } else {
                    Log.w(TAG, "⚠️ Word mismatch: expected '$word' but found '$wordInText' in text field")
                    // Try alternative: finish composing and then replace
                    ic.finishComposingText()
                    ic.deleteSurroundingText(word.length, 0)
                    ic.commitText("${expansion.expansion} ", 1)
                    
                    // ✅ Track expansion for backspace undo
                    lastExpansion = word to expansion.expansion
                    
                    dictionaryManager.incrementUsage(word)
                    Log.d(TAG, "✅ Dictionary expansion (alternative method): $word -> ${expansion.expansion}")
                }
            } else {
                Log.d(TAG, "No expansion found for: $word")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking dictionary expansion", e)
        }
    }
    
    /**
     * Reload dictionary settings from Flutter
     */
    private fun reloadDictionarySettings() {
        try {
            // ✅ FIX: Read from FlutterSharedPreferences where the UI saves settings
            val flutterPrefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            dictionaryEnabled = flutterPrefs.getBoolean("flutter.dictionary_enabled", true)
            
            // Also update the DictionaryManager's enabled state
            if (::dictionaryManager.isInitialized) {
                dictionaryManager.setEnabled(dictionaryEnabled)
            }
            
            Log.d(TAG, "✅ Dictionary settings reloaded: enabled=$dictionaryEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading dictionary settings", e)
        }
    }

    /**
     * Set up periodic cloud sync for user dictionary
     */
    private fun setupPeriodicSync() {
        if (firebaseBlockedInIme) {
            Log.d(TAG, "Periodic cloud sync disabled in IME process")
            return
        }
        syncHandler = Handler(Looper.getMainLooper())
        syncRunnable = object : Runnable {
            override fun run() {
                try {
                    if (::userDictionaryManager.isInitialized) {
                        userDictionaryManager.syncToCloud()
                        Log.d(TAG, "Periodic user dictionary sync completed")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during periodic sync: ${e.message}")
                }
                
                // Schedule next sync
                syncHandler?.postDelayed(this, syncInterval)
            }
        }
        
        // Start initial sync after 2 minutes
        // ✅ FIX: Use safe call instead of !!
        syncRunnable?.let { runnable ->
            syncHandler?.postDelayed(runnable, 2 * 60 * 1000L)
            Log.d(TAG, "Periodic sync scheduled every ${syncInterval / 1000 / 60} minutes")
        }
    }
    
    /**
     * Suspend helper to wait until a specific language dictionary is ready
     */
    private suspend fun UnifiedAutocorrectEngine.waitUntilReady(lang: String) {
        repeat(10) {
            if (hasLanguage(lang)) {
                return
            }
            delay(300)
        }
        Log.w("UnifiedAutocorrectEngine", "⚠️ Timeout waiting for $lang dictionary")
    }
    
    /**
     * 🔄 Unified AI suggestion updater
     * Combines offline dictionary + emoji + AI pipeline
     */
    private suspend fun updateAISuggestions(currentWord: String, contextWords: List<String>) {
        try {
            if (!areSuggestionsActive()) {
                clearSuggestions()
                return
            }
            if (!::autocorrectEngine.isInitialized) return
            
            // Get suggestions from unified autocorrect engine
            // Note: contextWords not directly used by current API, but kept for future enhancement
            val suggestions = autocorrectEngine.getSuggestions(currentWord, currentLanguage, 5)
            
            // updateSuggestionUI() now handles main thread posting internally
            updateSuggestionUI(suggestions)
        } catch (e: Exception) {
            Log.e("AIKeyboardService", "Error updating AI suggestions", e)
        }
    }
    
    /**
     * 🧩 Log engine readiness status for debugging
     */
    private fun logEngineStatus() {
        val langReady = if (::autocorrectEngine.isInitialized) autocorrectEngine.hasLanguage("en") else false
        Log.d("AIKeyboardService", "🧩 Engine readiness check — Unified=$langReady (swipe built-in)")
    }
    
    /**
     * Thread-safe helper for posting UI updates on the main thread.
     */
    private inline fun safeMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post { block() }
        }
    }
    
    /**
     * UNIFIED LANGUAGE ACTIVATION FUNCTION
     * Single entry point for activating any language with Firebase-only data
     * 
     * This function:
     * 1. Downloads language data from Firebase if needed
     * 2. Preloads the language into MultilingualDictionary
     * 3. Sets the language in UnifiedAutocorrectEngine
     * 4. Updates all dependent components
     * 
     * @param lang Language code to activate (e.g., "en", "hi")
     */
    private suspend fun applyLanguageResources(
        lang: String,
        resources: LanguageResources,
        updateCurrentLanguage: Boolean
    ) {
        autocorrectEngine.setLanguage(lang, resources)

        var isReady = autocorrectEngine.hasLanguage(lang)
        var readinessAttempts = 0
        val maxReadinessAttempts = 3
        while (!isReady && readinessAttempts < maxReadinessAttempts) {
            readinessAttempts++
            Log.w(TAG, "⏳ Engine readiness check failed, retrying... $readinessAttempts/$maxReadinessAttempts")
            delay(200)
            isReady = autocorrectEngine.hasLanguage(lang)
        }

        if (!isReady) {
            throw IllegalStateException("Engine not ready after activation for $lang")
        }

        if (updateCurrentLanguage) {
            currentLanguage = lang
        }

        if (updateCurrentLanguage || lang == currentLanguage) {
            withContext(Dispatchers.Main) {
                if (currentKeyboardMode == KeyboardMode.LETTERS) {
                    loadLanguageLayout(lang)
                }
                languageSwitchView?.refreshDisplay()
                currentWord = ""
                updateAISuggestions()
                refreshSwipeCapability("languageActivated=$lang")
            }
        }
    }

    // Thread-safe activation tracking to prevent duplicate calls
    private val activatingLanguages = mutableSetOf<String>()
    
    suspend fun activateLanguage(lang: String) {
        // Prevent concurrent activation of same language
        synchronized(activatingLanguages) {
            if (activatingLanguages.contains(lang)) {
                Log.d(TAG, "⏳ Language $lang already being activated, waiting...")
                return
            }
            activatingLanguages.add(lang)
        }
        
        try {
            Log.d(TAG, "📦 Activating offline language: $lang")
            
            // Check if already activated to prevent duplicate work
            if (autocorrectEngine.hasLanguage(lang)) {
                Log.d(TAG, "✅ Language $lang already activated, skipping")
                return
            }
            
            // Step 1: Preload language into memory from assets
            multilingualDictionary.preload(lang)
            
            // Step 2: Wait for resources with retry logic
            var resources: LanguageResources? = null
            var attempts = 0
            val maxAttempts = 10
            
            while (resources == null && attempts < maxAttempts) {
                resources = multilingualDictionary.get(lang)
                if (resources == null) {
                    attempts++
                    Log.w(TAG, "⏳ Waiting for offline resources to load... attempt $attempts/$maxAttempts")
                    delay(300) // Wait 300ms between attempts
                }
            }
            
            if (resources != null) {
                // Step 3: Apply language resources once and update UI
                applyLanguageResources(lang, resources, updateCurrentLanguage = true)
                Log.d(TAG, "🔁 Language switched to $lang (Firebase data)")
            } else {
                Log.e(TAG, "❌ Failed to get Firebase resources for language: $lang after $maxAttempts attempts")
                throw IllegalStateException("Failed to load Firebase resources for $lang")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error activating Firebase language $lang", e)
            throw e
        } finally {
            // Always remove from activating set
            synchronized(activatingLanguages) {
                activatingLanguages.remove(lang)
            }
        }
    }
    
    /**
     * PUBLIC API: Handle language download completion from MainActivity
     * This method should be called after MainActivity successfully downloads language files
     * 
     * @param lang Language code that was downloaded (e.g., "hi", "te", "ta")
     */
    fun onLanguageDownloaded(lang: String) {
        Log.d(TAG, "🎯 onLanguageDownloaded() called for $lang from MainActivity")
        
        coroutineScope.launch {
            try {
                // Activate the downloaded language in unified engine
                activateLanguage(lang)
                
                // If this is the current language, ensure UI is updated
                if (lang == currentLanguage) {
                    withContext(Dispatchers.Main) {
                        if (currentKeyboardMode == KeyboardMode.LETTERS) {
                            loadLanguageLayout(lang)
                        }
                        updateAISuggestions()
                    }
                }
                
                Log.d(TAG, "✅ Language $lang fully activated after download")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error activating downloaded language $lang", e)
            }
        }
    }
    
    /**
     * Handle language fully activated callback (download + preload + engine setup)
     * This is called automatically when a language completes its full initialization
     */
    private suspend fun onLanguageFullyActivated(lang: String) {
        try {
            LogUtil.d(TAG, "🎯 onLanguageFullyActivated() called for $lang")
            
            // Get the loaded resources from MultilingualDictionary
            val resources = multilingualDictionary.get(lang)
            if (resources != null) {
                if (autocorrectEngine.hasLanguage(lang)) {
                    LogUtil.d(TAG, "⏭️ Language $lang already active; skipping duplicate activation")
                    return
                }

                // Set language in unified engine
                applyLanguageResources(lang, resources, updateCurrentLanguage = false)
                LogUtil.d(TAG, "✅ UnifiedAutocorrectEngine activated for $lang: words=${resources.words.size}")
                
                LogUtil.d(TAG, "✅ Language $lang fully activated and ready for suggestions")
                
            } else {
                LogUtil.e(TAG, "❌ No resources found for activated language: $lang")
            }
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error in onLanguageFullyActivated for $lang", e)
        }
    }
    
    
    
    /**
     * Refresh LanguageResources after user data changes (NEW UNIFIED APPROACH)
     * Called when user dictionary or shortcuts are updated
     */
    private fun refreshLanguageResourcesAsync() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "🔄 Refreshing Firebase resources for $currentLanguage with updated user data")
                
                // Only refresh if the language is already loaded to prevent excessive activations
                if (autocorrectEngine.hasLanguage(currentLanguage)) {
                    // Just reload the current language to pick up user data changes
                    multilingualDictionary.preload(currentLanguage)
                    val resources = multilingualDictionary.get(currentLanguage)
                    if (resources != null) {
                        // 🔥 FIX 3.3 - Only set language if it actually changed
                        if (autocorrectEngine.currentLanguage != currentLanguage) {
                            autocorrectEngine.setLanguage(currentLanguage, resources)
                        }
                        Log.d(TAG, "✅ Firebase resources refreshed: ${resources.userWords.size} user words, ${resources.shortcuts.size} shortcuts")
                    }
                } else {
                    Log.w(TAG, "⚠️ Language $currentLanguage not loaded, skipping refresh")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error refreshing Firebase resources", e)
            }
        }
    }
}

// Helper extensions for preference reading (handles all Flutter SharedPreferences types)
// Note: Pass key WITH "flutter." prefix already included
private fun SharedPreferences.getFloatCompat(k: String, def: Float): Float {
    val value = all[k]
    return when (value) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is String -> {
            val decoded = decodeFlutterDouble(value)
            decoded?.toFloat() ?: value.toFloatOrNull() ?: def
        }
        else -> def
    }
}

private fun SharedPreferences.getIntCompat(k: String, def: Int): Int {
    val value = all[k]
    return when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Float -> value.toInt()
        is Double -> value.toInt()
        is String -> {
            value.toIntOrNull()
                ?: value.toFloatOrNull()?.roundToInt()
                ?: decodeFlutterDouble(value)?.roundToInt()
                ?: def
        }
        else -> def
    }
}

private fun decodeFlutterDouble(raw: String): Double? {
    val prefixes = listOf(
        "This is the prefix for double.",
        "This is the prefix for Double.",
        "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBkb3VibGUu",
        "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    )
    prefixes.forEach { prefix ->
        if (raw.startsWith(prefix)) {
            return raw.removePrefix(prefix).toDoubleOrNull()
        }
    }
    return raw.toDoubleOrNull()
}
