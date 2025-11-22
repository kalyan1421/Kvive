package com.kvive.keyboard

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 🚀 UNIFIED LAYOUT CONTROLLER V2
 * 
 * Centralized orchestrator that unifies:
 * ✅ Layout loading (JSON templates + keymaps)
 * ✅ Language management (switching, preferences, display names)
 * ✅ Height adjustment (auto-resize, orientation handling)
 * ✅ Caps/Shift management (auto-capitalization, state tracking)
 * ✅ Language switching UI (display names, flags, notifications)
 * ✅ Script detection and handling (RTL, Indic, Latin)
 * 
 * ⚡ PERFORMANCE OPTIMIZATIONS:
 * ✅ Layout caching to avoid redundant rebuilds
 * ✅ Debouncing/throttling for rapid calls
 * ✅ Shift state updates without full rebuild
 * ✅ Duplicate call prevention
 * ✅ Smart invalidation (only when needed)
 * 
 * Replaces scattered logic from:
 * - loadDynamicLayout() / loadLanguageLayout()
 * - LanguageManager direct calls
 * - Manual height adjustments
 * - Separate caps initialization
 * 
 * Benefits:
 * - Single entry point for all layout operations
 * - No race conditions (proper async sequencing)
 * - Integrated language + layout management
 * - Consistent auto-adjust behavior
 * - Simplified debugging (centralized logs)
 * - ⚡ Reduced typing lag from unnecessary rebuilds
 */
class UnifiedLayoutController(
    private val context: Context,
    private val service: AIKeyboardService,
    private val adapter: LanguageLayoutAdapter,
    private val keyboardView: UnifiedKeyboardView,  // ✅ Changed to UnifiedKeyboardView
    private val heightManager: KeyboardHeightManager
) {
    companion object {
        private const val TAG = "UnifiedLayout"
        private const val DEBOUNCE_DELAY_MS = 50L // Debounce rapid calls
        private const val MAX_CACHE_SIZE = 20 // Limit cache size
        private const val SHIFT_STATE_THROTTLE_MS = 100L // Throttle rapid shift state changes
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Integrated components (consolidated from multiple files)
    private var languageManager: LanguageManager? = null
    private var capsShiftManager: CapsShiftManager? = null
    private var languageSwitchView: LanguageSwitchView? = null
    
    // State tracking
    private var isInitialized = false
    private var currentLanguage = "en"
    private var currentMode = LanguageLayoutAdapter.KeyboardMode.LETTERS
    private var numberRowEnabled = false
    
    // ⚡ PERFORMANCE: Layout caching
    // Note: Shift state is NOT in cache key because it only affects labels, not structure
    // We handle shift state separately via lightweight label updates
    private data class LayoutCacheKey(
        val language: String,
        val mode: LanguageLayoutAdapter.KeyboardMode,
        val numberRow: Boolean
    )
    private val layoutCache = ConcurrentHashMap<LayoutCacheKey, LanguageLayoutAdapter.LayoutModel>()
    
    // ⚡ PERFORMANCE: Debouncing/throttling
    private var pendingBuildJob: Job? = null
    private var lastBuildParams: LayoutCacheKey? = null
    private var lastBuildTime = 0L
    
    // ⚡ PERFORMANCE: Track current shift state to avoid unnecessary rebuilds
    private var currentShiftState = CapsShiftManager.STATE_NORMAL
    private var lastShiftStateChangeTime = 0L

    /**
     * Initialize the unified controller with all language-related components.
     * This centralizes initialization that was scattered across AIKeyboardService.
     * Note: CapsShiftManager should be initialized externally before calling this.
     */
    fun initialize(
        languageManager: LanguageManager,
        capsShiftManager: CapsShiftManager,
        languageSwitchView: LanguageSwitchView? = null
    ) {
        this.languageManager = languageManager
        this.capsShiftManager = capsShiftManager
        this.languageSwitchView = languageSwitchView
        
        // Set up language change listener for automatic layout updates
        languageManager.addLanguageChangeListener(object : LanguageManager.LanguageChangeListener {
            override fun onLanguageChanged(oldLanguage: String, newLanguage: String) {
                Log.d(TAG, "🌐 Language changed: $oldLanguage → $newLanguage")
                handleLanguageSwitch(oldLanguage, newLanguage)
            }
            
            override fun onEnabledLanguagesChanged(enabledLanguages: Set<String>) {
                Log.d(TAG, "🌐 Enabled languages updated: $enabledLanguages")
            }
        })
        
        currentLanguage = languageManager.getCurrentLanguage()
        isInitialized = true
        
        Log.d(TAG, "✅ Unified controller initialized with language: $currentLanguage")
    }

    /**
     * Main entry point for all layout loading.
     * Handles async layout building, rendering, and auto-adjust in correct sequence.
     * 
     * ⚡ PERFORMANCE OPTIMIZATIONS:
     * - Caches layouts to avoid redundant rebuilds
     * - Debounces rapid calls
     * - Prevents duplicate builds with same parameters
     * 
     * @param language ISO language code (e.g., "en", "hi", "es")
     * @param mode Keyboard mode (LETTERS, SYMBOLS, EXTENDED_SYMBOLS, DIALER)
     * @param numberRow Whether to show number row
     * @param force Force rebuild even if cached (default: false)
     */
    fun buildAndRender(
        language: String, 
        mode: LanguageLayoutAdapter.KeyboardMode, 
        numberRow: Boolean = false,
        force: Boolean = false
    ) {
        val cacheKey = LayoutCacheKey(language, mode, numberRow)
        
        // ⚡ PERFORMANCE: Check if this is a duplicate call
        val now = System.currentTimeMillis()
        if (!force && lastBuildParams == cacheKey && (now - lastBuildTime) < 100) {
            Log.d(TAG, "⏸️ Skipping duplicate buildAndRender call (same params within 100ms)")
            return
        }
        
        // ⚡ PERFORMANCE: Cancel pending debounced call if parameters changed
        pendingBuildJob?.cancel()
        
        // ⚡ PERFORMANCE: Debounce rapid calls (e.g., rapid shift presses)
        pendingBuildJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            
            // Check again after delay - another call might have come in
            if (lastBuildParams == cacheKey && !force) {
                Log.d(TAG, "⏸️ Debounced duplicate call cancelled")
                return@launch
            }
            
            lastBuildParams = cacheKey
            lastBuildTime = System.currentTimeMillis()
            
            Log.d(TAG, "🚀 Building layout for $language [$mode], numberRow=$numberRow")
            
            // Update state
            currentLanguage = language
            currentMode = mode
            numberRowEnabled = numberRow

            try {
                // ⚡ PERFORMANCE: Check cache first
                val layoutModel = if (!force) {
                    layoutCache[cacheKey] ?: run {
                        // Build layout model asynchronously (off main thread)
                        val built = withContext(Dispatchers.IO) {
                            adapter.buildLayoutFor(language, mode, numberRow)
                        }
                        // Cache it (with size limit)
                        if (layoutCache.size >= MAX_CACHE_SIZE) {
                            val firstKey = layoutCache.keys.first()
                            layoutCache.remove(firstKey)
                        }
                        layoutCache[cacheKey] = built
                        built
                    }
                } else {
                    // Force rebuild - update cache
                    val built = withContext(Dispatchers.IO) {
                        adapter.buildLayoutFor(language, mode, numberRow)
                    }
                    layoutCache[cacheKey] = built
                    built
                }
                
                Log.d(TAG, "📦 Layout model ${if (force) "built" else "retrieved"}: ${layoutModel.rows.size} rows, ${layoutModel.rows.flatten().size} keys")

                // Step 2: Apply layout to unified view (on main thread)
                withContext(Dispatchers.Main) {
                    // ✅ Update unified keyboard view with new layout
                    keyboardView.currentLangCode = language
                    keyboardView.currentKeyboardMode = mode
                    keyboardView.showTypingLayout(layoutModel)  // ✅ New unified API
                    
                    // 🌐 LANGUAGE UI UPDATE: Update language display
                    updateLanguageDisplay(language)
                    
                    // ✅ HEIGHT & THEME: Handled automatically by UnifiedKeyboardView
                    // No manual triggerAutoAdjust() or applyOptimalHeight() needed
                    // ThemeManager listener already updates view automatically
                    
                    // ⇧ CAPS STATE: Apply auto-capitalization for new language
                    applyCapsStateForLanguage(language)
                    
                    Log.d(TAG, "✅ Layout rendered for $language [$mode]")
                    
                    // Show user feedback for language switches
                    if (mode == LanguageLayoutAdapter.KeyboardMode.LETTERS && isInitialized) {
                        showLanguageSwitchToast(language)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to build layout for $language [$mode]", e)
            }
        }
    }
    
    /**
     * Switch to next enabled language (unified from LanguageManager)
     */
    fun switchToNextLanguage() {
        languageManager?.let { manager ->
            val oldLanguage = currentLanguage
            manager.switchToNextLanguage()
            val newLanguage = manager.getCurrentLanguage()
            
            if (oldLanguage != newLanguage) {
                // Rebuild layout for new language
                buildAndRender(newLanguage, currentMode, numberRowEnabled)
            }
        }
    }
    
    /**
     * Toggle number row on/off and rebuild layout
     */
    fun toggleNumberRow() {
        val newState = !numberRowEnabled
        Log.d(TAG, "🔢 Toggling number row: $numberRowEnabled → $newState")
        buildAndRender(currentLanguage, currentMode, newState)
    }
    
    /**
     * Handle language switch from external trigger
     */
    private fun handleLanguageSwitch(oldLanguage: String, newLanguage: String) {
        if (oldLanguage != newLanguage && currentMode == LanguageLayoutAdapter.KeyboardMode.LETTERS) {
            buildAndRender(newLanguage, currentMode, numberRowEnabled)
        }
    }
    
    /**
     * Update language display elements (consolidated from multiple places)
     */
    private fun updateLanguageDisplay(language: String) {
        val displayName = languageManager?.getLanguageDisplayName(language) ?: language.uppercase()
        
        // Update keyboard view
        keyboardView.setCurrentLanguage(displayName)
        
        // Update language switch view
        languageSwitchView?.refreshDisplay()
        
        Log.d(TAG, "🏷️ Updated language display: $displayName")
    }
    
    /**
     * Trigger auto-adjust sequence (centralized from multiple methods)
     */
    private fun triggerAutoAdjust() {
        service.keyboardContainer?.requestLayout()
        service.mainKeyboardLayout?.requestLayout()
        // Note: updateInputViewShown() is a parent class method, available publicly
        try {
            service.updateInputViewShown()
        } catch (e: Exception) {
            Log.w(TAG, "updateInputViewShown() not available, using alternative", e)
            // Alternative: trigger invalidation
            keyboardView.invalidate()
            keyboardView.requestLayout()
        }
        
        Log.d(TAG, "🔄 Auto-adjust sequence triggered")
    }
    
    /**
     * Apply optimal height based on current state
     */
    private fun applyOptimalHeight(_hasNumberRow: Boolean) {
        val adjustedHeight = heightManager.calculateKeyboardHeight(
            includeToolbar = true,
            includeSuggestions = true
        )
        
        service.mainKeyboardLayout?.layoutParams?.let { params ->
            params.height = adjustedHeight
            service.mainKeyboardLayout?.requestLayout()
        }
        
        Log.d(TAG, "📐 Applied height: ${adjustedHeight}px (numberRow=$_hasNumberRow)")
    }
    
    /**
     * Apply caps state for new language (unified caps management)
     */
    private fun applyCapsStateForLanguage(language: String) {
        capsShiftManager?.let { manager ->
            try {
                // Apply auto-capitalization based on input field
                val ic = service.currentInputConnection
                val inputType = service.currentInputEditorInfo?.inputType ?: 0
                
                if (ic != null) {
                    manager.applyAutoCapitalization(ic, inputType)
                    Log.d(TAG, "⇧ Applied auto-capitalization for $language")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to apply caps state for $language", e)
            }
        }
    }
    
    /**
     * Show user-friendly language switch notification
     */
    private fun showLanguageSwitchToast(language: String) {
        val config = LanguageConfigs.getLanguageConfig(language)
        val displayName = config?.let { "${it.flag} ${it.nativeName}" } ?: language.uppercase()
        
        // Toast removed - language switch logged only
    }
    
    /**
     * Get current language configuration
     */
    fun getCurrentLanguageConfig(): LanguageConfig? {
        return LanguageConfigs.getLanguageConfig(currentLanguage)
    }
    
    /**
     * Check if current language uses RTL layout
     */
    fun isRTLLanguage(): Boolean {
        return getCurrentLanguageConfig()?.direction == TextDirection.RTL
    }
    
    /**
     * Check if current language uses Indic script
     */
    fun isIndicLanguage(): Boolean {
        val config = getCurrentLanguageConfig()
        return config?.script in listOf(
            Script.DEVANAGARI, Script.TELUGU, Script.TAMIL, Script.MALAYALAM
        )
    }
    
    /**
     * Get enabled languages for UI
     */
    fun getEnabledLanguages(): Set<String> {
        return languageManager?.getEnabledLanguages() ?: setOf("en")
    }
    
    /**
     * Handle caps/shift key press (unified from multiple places)
     */
    fun handleShiftPress() {
        capsShiftManager?.handleShiftPress()
    }
    
    /**
     * Process character input through caps/shift manager
     * Converts character case based on current shift state
     */
    fun processCharacterInput(char: Char): Char {
        return capsShiftManager?.processCharacterInput(char) ?: char
    }
    
    /**
     * Handle sentence-ending punctuation (., !, ?)
     * Activates shift state for next character
     */
    fun handlePunctuationEnd() {
        capsShiftManager?.let { manager ->
            if (manager.currentState == CapsShiftManager.STATE_NORMAL) {
                // Set to shift state for auto-capitalization
                manager.currentState = CapsShiftManager.STATE_SHIFT
                // Trigger visual update
                refreshKeyboardForShiftState(CapsShiftManager.STATE_SHIFT)
                Log.d(TAG, "⇧ Auto-capitalization activated after punctuation")
            }
        }
    }
    
    /**
     * Refresh keyboard layout to reflect current shift/caps state
     * 
     * ⚡ PERFORMANCE: Optimized to update labels only, not rebuild entire layout
     * Only rebuilds if shift state actually changed or layout structure changed
     * 
     * ⚡ CRITICAL FIX: Throttles rapid shift state changes from gesture handlers
     */
    fun refreshKeyboardForShiftState(shiftState: Int) {
        val now = System.currentTimeMillis()
        
        // ⚡ PERFORMANCE: Skip if shift state hasn't changed
        if (currentShiftState == shiftState) {
            Log.d(TAG, "⏸️ Skipping shift refresh - state unchanged: $shiftState")
            return
        }
        
        // ⚡ PERFORMANCE: Throttle rapid shift state changes (e.g., from gesture misdetection)
        if ((now - lastShiftStateChangeTime) < SHIFT_STATE_THROTTLE_MS) {
            Log.d(TAG, "⏸️ Throttling rapid shift state change: $currentShiftState → $shiftState (${now - lastShiftStateChangeTime}ms since last)")
            // Schedule delayed update instead of ignoring
            scope.launch {
                delay(SHIFT_STATE_THROTTLE_MS - (now - lastShiftStateChangeTime))
                // Re-check state after delay - might have changed again
                if (currentShiftState != shiftState) {
                    refreshKeyboardForShiftStateInternal(shiftState)
                }
            }
            return
        }
        
        lastShiftStateChangeTime = now
        refreshKeyboardForShiftStateInternal(shiftState)
    }
    
    /**
     * Internal implementation of shift state refresh
     */
    private fun refreshKeyboardForShiftStateInternal(shiftState: Int) {
        currentShiftState = shiftState
        
        // ⚡ PERFORMANCE: Try to update labels only first (fast path)
        val mode = keyboardView.currentKeyboardMode
        val lang = keyboardView.currentLangCode
        
        // For shift state changes, we can often just update labels without rebuilding
        // Only rebuild if we're switching modes or language changed
        val isUpperCase = shiftState != CapsShiftManager.STATE_NORMAL
        
        scope.launch {
            try {
                // ⚡ PERFORMANCE: Try lightweight label update first
                val updated = keyboardView.updateKeyLabelsForShiftState(isUpperCase, shiftState == CapsShiftManager.STATE_CAPS_LOCK)
                
                if (!updated) {
                    // Fallback: Check if view supports lightweight updates
                    if (!keyboardView.supportsLightweightShiftUpdate()) {
                        // SwipeKeyboardView - it handles shift state visually, no rebuild needed
                        Log.d(TAG, "⚡ SwipeKeyboardView detected - shift handled visually, no rebuild needed")
                        keyboardView.invalidate() // Just invalidate for visual update
                    } else {
                        // UnifiedKeyboardView but update failed - this shouldn't happen often
                        Log.d(TAG, "⚠️ Lightweight update failed, invalidating only to avoid lag")
                        keyboardView.invalidate()
                    }
                } else {
                    Log.d(TAG, "⚡ Shift state updated via lightweight method (no rebuild)")
                }
                
                Log.d(TAG, "✅ Keyboard refreshed for shift state: $shiftState (uppercase=$isUpperCase)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to refresh keyboard for shift", e)
            }
        }
    }
    
    /**
     * Handle space press for auto-capitalization
     */
    fun handleSpacePress() {
        capsShiftManager?.let { manager ->
            val ic = service.currentInputConnection
            val inputType = service.currentInputEditorInfo?.inputType ?: 0
            if (ic != null) {
                manager.handleSpacePress(ic, inputType)
            }
        }
    }
    
    /**
     * Handle enter press for auto-capitalization
     */
    fun handleEnterPress() {
        capsShiftManager?.let { manager ->
            val ic = service.currentInputConnection
            val inputType = service.currentInputEditorInfo?.inputType ?: 0
            if (ic != null) {
                manager.handleEnterPress(ic, inputType)
            }
        }
    }
    
    /**
     * Clear layout cache (useful for memory management or forced refresh)
     */
    fun clearCache() {
        layoutCache.clear()
        Log.d(TAG, "🧹 Layout cache cleared")
    }
    
    /**
     * Cancel all pending layout operations and cleanup.
     */
    fun clear() {
        pendingBuildJob?.cancel()
        scope.cancel()
        layoutCache.clear()
        capsShiftManager?.cleanup()
        languageManager?.cleanup()
        Log.d(TAG, "🧹 Unified layout controller cleared")
    }
}
