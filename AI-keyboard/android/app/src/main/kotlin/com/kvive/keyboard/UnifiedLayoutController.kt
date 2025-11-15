package com.kvive.keyboard

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.coroutines.*

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
     * @param language ISO language code (e.g., "en", "hi", "es")
     * @param mode Keyboard mode (LETTERS, SYMBOLS, EXTENDED_SYMBOLS, DIALER)
     * @param numberRow Whether to show number row
     */
    fun buildAndRender(
        language: String, 
        mode: LanguageLayoutAdapter.KeyboardMode, 
        numberRow: Boolean = false
    ) {
        Log.d(TAG, "🚀 Building layout for $language [$mode], numberRow=$numberRow")
        
        // Update state
        currentLanguage = language
        currentMode = mode
        numberRowEnabled = numberRow

        scope.launch {
            try {
                // Step 1: Build layout model asynchronously (off main thread)
                val layoutModel = withContext(Dispatchers.IO) {
                    adapter.buildLayoutFor(language, mode, numberRow)
                }
                
                Log.d(TAG, "📦 Layout model built: ${layoutModel.rows.size} rows, ${layoutModel.rows.flatten().size} keys")

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
     */
    fun refreshKeyboardForShiftState(shiftState: Int) {
        // Reload the current layout with updated case
        val mode = keyboardView.currentKeyboardMode
        val lang = keyboardView.currentLangCode
        
        scope.launch {
            try {
                val layoutModel = adapter.buildLayoutFor(lang, mode, numberRowEnabled = numberRowEnabled)
                keyboardView.showTypingLayout(layoutModel)
                val isUpperCase = shiftState != CapsShiftManager.STATE_NORMAL
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
     * Cancel all pending layout operations and cleanup.
     */
    fun clear() {
        scope.cancel()
        capsShiftManager?.cleanup()
        languageManager?.cleanup()
        Log.d(TAG, "🧹 Unified layout controller cleared")
    }
}
