package com.kvive.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import java.util.regex.Pattern

/**
 * Comprehensive Caps/Shift Management System
 * Implements a finite state machine with three functional states and smart auto-capitalization
 * Similar to Google Keyboard (Gboard) behavior
 */
class CapsShiftManager(
    private val context: Context,
    private val settings: SharedPreferences
) {
    companion object {
        private const val TAG = "CapsShiftManager"
        
        // Shift States (Finite State Machine)
        const val STATE_NORMAL = 0      // Default lowercase
        const val STATE_SHIFT = 1       // Single uppercase (next character only)
        const val STATE_CAPS_LOCK = 2   // Continuous uppercase
        
        // Timing constants
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 500L
        
        // Auto-capitalization patterns
        private val SENTENCE_END_PATTERN = Pattern.compile("[.!?]\\s*$")
        private val PARAGRAPH_START_PATTERN = Pattern.compile("^\\s*$")
        
        // Input field types that should auto-capitalize
        private val AUTO_CAPITALIZE_FIELDS = setOf(
            EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME,
            EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        )
    }
    
    // State variables
    var currentState = STATE_NORMAL
        internal set  // Allow UnifiedLayoutController to set it directly
    private var lastShiftPressTime = 0L
    private var longPressHandler: Handler? = null
    private var isAutoCapitalizationEnabled = true
    private var isContextAwareCapitalizationEnabled = true
    private var isCapsLockMemoryEnabled = false
    
    // Callbacks
    private var onStateChangedListener: ((Int) -> Unit)? = null
    private var onLongPressMenuListener: (() -> Unit)? = null
    private var onHapticFeedbackListener: ((Int) -> Unit)? = null
    
    init {
        loadSettings()
        longPressHandler = Handler(Looper.getMainLooper())
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        isAutoCapitalizationEnabled = settings.getBoolean("auto_capitalization", true)
        isContextAwareCapitalizationEnabled = settings.getBoolean("context_aware_capitalization", true)
        isCapsLockMemoryEnabled = settings.getBoolean("remember_caps_state", false)
        if (!isCapsLockMemoryEnabled && currentState == STATE_CAPS_LOCK) {
            currentState = STATE_NORMAL
            onStateChangedListener?.invoke(currentState)
        }
    }
    
    /**
     * Set callback listeners
     */
    fun setOnStateChangedListener(listener: (Int) -> Unit) {
        onStateChangedListener = listener
    }
    
    fun setOnLongPressMenuListener(listener: () -> Unit) {
        onLongPressMenuListener = listener
    }
    
    fun setOnHapticFeedbackListener(listener: (Int) -> Unit) {
        onHapticFeedbackListener = listener
    }
    
    /**
     * Handle shift key press with finite state machine logic
     */
    fun handleShiftPress(): Boolean {
        val now = System.currentTimeMillis()
        val previousState = currentState
        
        when (currentState) {
            STATE_NORMAL -> {
                // Single tap: Normal -> Shift (next character uppercase)
                currentState = STATE_SHIFT
                lastShiftPressTime = now
                showFeedback("Shift ON")
            }
            STATE_SHIFT -> {
                val isDoubleTap = now - lastShiftPressTime < DOUBLE_TAP_TIMEOUT
                lastShiftPressTime = now
                if (isDoubleTap && isCapsLockMemoryEnabled) {
                    // Double tap detected: Shift -> Caps Lock
                    currentState = STATE_CAPS_LOCK
                    showFeedback("CAPS LOCK")
                } else {
                    // Either caps lock memory disabled or timeout elapsed: Shift -> Normal
                    currentState = STATE_NORMAL
                    showFeedback("Shift OFF")
                }
            }
            STATE_CAPS_LOCK -> {
                // Any tap from caps lock: Caps Lock -> Normal
                currentState = STATE_NORMAL
                showFeedback("CAPS LOCK OFF")
            }
        }
        
        // Notify state change
        if (previousState != currentState) {
            onStateChangedListener?.invoke(currentState)
            onHapticFeedbackListener?.invoke(currentState)
            Log.d(TAG, "State changed from ${getStateName(previousState)} to ${getStateName(currentState)}")
        }
        
        return true
    }
    
    /**
     * Handle shift key long press
     */
    fun handleShiftLongPress(): Boolean {
        Log.d(TAG, "Shift long press detected")
        
        // Cancel any pending long press
        longPressHandler?.removeCallbacksAndMessages(null)
        
        // Show shift options menu
        onLongPressMenuListener?.invoke()
        
        return true
    }
    
    /**
     * Start long press detection
     */
    fun startLongPressDetection() {
        longPressHandler?.removeCallbacksAndMessages(null)
        longPressHandler?.postDelayed({
            handleShiftLongPress()
        }, LONG_PRESS_TIMEOUT)
    }
    
    /**
     * Cancel long press detection
     */
    fun cancelLongPressDetection() {
        longPressHandler?.removeCallbacksAndMessages(null)
    }
    
    /**
     * Process character input and update shift state accordingly
     */
    fun processCharacterInput(char: Char): Char {
        val processedChar = when {
            Character.isLetter(char) -> {
                when (currentState) {
                    STATE_NORMAL -> char.lowercaseChar()
                    STATE_SHIFT -> {
                        // Auto-reset to normal after single character
                        currentState = STATE_NORMAL
                        onStateChangedListener?.invoke(currentState)
                        char.uppercaseChar()
                    }
                    STATE_CAPS_LOCK -> char.uppercaseChar()
                    else -> char.lowercaseChar()
                }
            }
            else -> char
        }
        
        return processedChar
    }
    
    /**
     * Check if auto-capitalization should be triggered based on context
     */
    fun shouldAutoCapitalize(inputConnection: InputConnection?, inputType: Int): Boolean {
        if (!isAutoCapitalizationEnabled) return false
        
        // Check input field type
        if (isContextAwareCapitalizationEnabled && shouldCapitalizeBasedOnInputType(inputType)) {
            return true
        }
        
        // Check text context
        return shouldCapitalizeBasedOnContext(inputConnection)
    }
    
    /**
     * Apply auto-capitalization if conditions are met
     */
    fun applyAutoCapitalization(inputConnection: InputConnection?, inputType: Int) {
        if (shouldAutoCapitalize(inputConnection, inputType) && currentState == STATE_NORMAL) {
            currentState = STATE_SHIFT
            onStateChangedListener?.invoke(currentState)
            Log.d(TAG, "Auto-capitalization applied")
        }
    }
    
    /**
     * Reset shift state to normal (used when switching keyboards, etc.)
     */
    fun resetToNormal() {
        if (currentState != STATE_NORMAL) {
            currentState = STATE_NORMAL
            onStateChangedListener?.invoke(currentState)
        }
    }
    
    /**
     * Force caps lock state (used by long press menu)
     */
    fun setCapsLock(enabled: Boolean) {
        val newState = if (enabled) STATE_CAPS_LOCK else STATE_NORMAL
        if (currentState != newState) {
            currentState = newState
            onStateChangedListener?.invoke(currentState)
            showFeedback(if (enabled) "CAPS LOCK" else "CAPS LOCK OFF")
            Log.d(TAG, "Caps lock ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Check if shift is active (either SHIFT or CAPS_LOCK)
     */
    fun isShiftActive(): Boolean = currentState != STATE_NORMAL
    
    /**
     * Check if caps lock is active
     */
    fun isCapsLockActive(): Boolean = currentState == STATE_CAPS_LOCK
    
    /**
     * Get human-readable state name
     */
    fun getStateName(state: Int = currentState): String = when (state) {
        STATE_NORMAL -> "Normal (lowercase)"
        STATE_SHIFT -> "Shift (next char uppercase)"
        STATE_CAPS_LOCK -> "Caps Lock (all uppercase)"
        else -> "Unknown"
    }
    
    /**
     * Check if should capitalize based on input field type
     */
    private fun shouldCapitalizeBasedOnInputType(inputType: Int): Boolean {
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        return AUTO_CAPITALIZE_FIELDS.contains(variation)
    }
    
    /**
     * Check if should capitalize based on text context
     */
    private fun shouldCapitalizeBasedOnContext(inputConnection: InputConnection?): Boolean {
        inputConnection ?: return true // Capitalize at start if no context
        
        val textBefore = inputConnection.getTextBeforeCursor(50, 0)?.toString() ?: ""
        
        // Capitalize at start of input
        if (textBefore.isEmpty()) return true
        
        // Capitalize after sentence-ending punctuation followed by space(s)
        if (SENTENCE_END_PATTERN.matcher(textBefore).find()) return true
        
        // Capitalize at start of new paragraph (after newline)
        if (textBefore.endsWith("\n") || textBefore.matches(Regex(".*\\n\\s*$"))) return true
        
        return false
    }
    
    /**
     * Show user feedback for state changes
     */
    private fun showFeedback(message: String) {
        // Toast removed - shift feedback logged only
        Log.d(TAG, "Shift feedback: $message")
    }
    
    /**
     * Handle space key press with auto-capitalization logic
     */
    fun handleSpacePress(inputConnection: InputConnection?, inputType: Int) {
        // Check if we should auto-capitalize after this space
        inputConnection?.let { ic ->
            // Get text before cursor to check for sentence endings
            val textBefore = ic.getTextBeforeCursor(10, 0)?.toString() ?: ""
            val trimmed = textBefore.trimEnd()
            
            // If previous text ends with sentence punctuation, prepare for capitalization
            if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
                if (isAutoCapitalizationEnabled && currentState == STATE_NORMAL) {
                    currentState = STATE_SHIFT
                    onStateChangedListener?.invoke(currentState)
                    Log.d(TAG, "Auto-capitalization triggered after sentence end")
                }
            }
        }
    }
    
    /**
     * Handle enter key press with auto-capitalization logic
     */
    fun handleEnterPress(inputConnection: InputConnection?, inputType: Int) {
        // Auto-capitalize after new line if enabled
        if (isAutoCapitalizationEnabled && currentState == STATE_NORMAL) {
            currentState = STATE_SHIFT
            onStateChangedListener?.invoke(currentState)
            Log.d(TAG, "Auto-capitalization triggered after enter")
        }
    }
    
    /**
     * Update settings
     */
    fun updateSettings() {
        loadSettings()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        longPressHandler?.removeCallbacksAndMessages(null)
        longPressHandler = null
    }
}
