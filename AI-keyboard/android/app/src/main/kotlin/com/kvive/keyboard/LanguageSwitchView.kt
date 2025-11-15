package com.kvive.keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.util.Log
import com.kvive.keyboard.utils.LogUtil

/**
 * Simple language switch button for multilingual keyboard
 */
class LanguageSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "LanguageSwitchView"
    }

    private var languageManager: LanguageManager? = null
    private var onLanguageChangeListener: ((String) -> Unit)? = null

    init {
        setupView()
    }

    private fun setupView() {
        text = "🇺🇸 EN"
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(16, 8, 16, 8)
        
        setOnClickListener { handleQuickSwitch() }
        setOnLongClickListener { 
            handleLongPress()
            true
        }
        
        // Set background and text colors
        try {
            setBackgroundResource(R.drawable.key_background_default)
            setTextColor(context.resources.getColor(R.color.language_switch_text_color))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set themed colors, using defaults", e)
            setBackgroundColor(0xFF4CAF50.toInt()) // Green background
            setTextColor(0xFFFFFFFF.toInt()) // White text
        }
    }

    fun setLanguageManager(manager: LanguageManager) {
        this.languageManager = manager
        updateDisplay()
    }

    fun setOnLanguageChangeListener(listener: (String) -> Unit) {
        this.onLanguageChangeListener = listener
    }

    private fun updateDisplay() {
        languageManager?.let { manager ->
            val currentLang = manager.getCurrentLanguage()
            val config = manager.getCurrentLanguageConfig()
            val flag = config?.flag ?: "🌐"
            val enabledCount = manager.getEnabledLanguages().size
            
            text = "$flag ${currentLang.uppercase()}"
            contentDescription = if (enabledCount > 1) {
                "Current language: ${config?.nativeName ?: currentLang}. Tap to cycle or long press for menu. ($enabledCount languages enabled)"
            } else {
                "Current language: ${config?.nativeName ?: currentLang} (only language enabled)"
            }
            
            Log.d(TAG, "Language display updated: $text, enabled languages: ${manager.getEnabledLanguages()}")
        }
    }

    private fun handleQuickSwitch() {
        try {
            languageManager?.let { manager ->
                when (manager.getTapBehavior()) {
                    LanguageManager.TapBehavior.CYCLE -> {
                        // Default behavior: cycle through languages
                        manager.switchToNextLanguage()
                        updateDisplay()
                        onLanguageChangeListener?.invoke(manager.getCurrentLanguage())
                        Log.d(TAG, "Quick switched to: ${manager.getCurrentLanguage()}")
                    }
                    LanguageManager.TapBehavior.POPUP -> {
                        // New behavior: show popup on single tap
                        val enabledLanguages = manager.getEnabledLanguages()
                        if (enabledLanguages.size > 1) {
                            showLanguageSelectionMenu(enabledLanguages.toList())
                        } else {
                            Log.d(TAG, "Only one language enabled, no popup needed")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during quick switch", e)
        }
    }

    private fun handleLongPress() {
        try {
            languageManager?.let { manager ->
                when (manager.getTapBehavior()) {
                    LanguageManager.TapBehavior.CYCLE -> {
                        // When cycling is default, long press shows popup
                        val enabledLanguages = manager.getEnabledLanguages()
                        if (enabledLanguages.size > 1) {
                            showLanguageSelectionMenu(enabledLanguages.toList())
                        } else {
                            Log.d(TAG, "Only one language enabled, no menu needed")
                        }
                    }
                    LanguageManager.TapBehavior.POPUP -> {
                        // When popup is default, long press cycles through languages
                        manager.switchToNextLanguage()
                        updateDisplay()
                        onLanguageChangeListener?.invoke(manager.getCurrentLanguage())
                        Log.d(TAG, "Long press switched to: ${manager.getCurrentLanguage()}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during long press", e)
        }
    }

    private fun showLanguageSelectionMenu(availableLanguages: List<String>) {
        try {
            val popupWindow = PopupWindow(context)
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                try {
                    setBackgroundResource(R.drawable.popup_background)
                } catch (e: Exception) {
                    setBackgroundColor(0xFFFFFFFF.toInt()) // White background
                }
            }

            availableLanguages.forEach { languageCode ->
                val config = LanguageConfigs.getLanguageConfig(languageCode)
                val displayName = config?.nativeName ?: languageCode.uppercase()
                val flag = config?.flag ?: "🌐"
                val isCurrentLanguage = languageManager?.getCurrentLanguage() == languageCode
                
                val button = Button(context).apply {
                    text = "$flag $displayName${if (isCurrentLanguage) " ✓" else ""}"
                    setOnClickListener {
                        languageManager?.switchToLanguage(languageCode)
                        updateDisplay()
                        onLanguageChangeListener?.invoke(languageCode)
                        popupWindow.dismiss()
                    }
                    setPadding(24, 16, 24, 16)
                    
                    // Highlight current language
                    if (isCurrentLanguage) {
                        try {
                            setBackgroundResource(R.drawable.key_background_default)
                        } catch (e: Exception) {
                            setBackgroundColor(0xFFE3F2FD.toInt()) // Light blue background
                        }
                    }
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                }
                layout.addView(button)
            }
            
            // Add separator
            val separator = View(context).apply {
                setBackgroundColor(0xFFCCCCCC.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }
            layout.addView(separator)
            
            // Add tap behavior toggle button
            val tapBehaviorButton = Button(context).apply {
                val currentBehavior = languageManager?.getTapBehavior()
                text = when (currentBehavior) {
                    LanguageManager.TapBehavior.CYCLE -> "⚙️ Switch to: Tap for popup"
                    LanguageManager.TapBehavior.POPUP -> "⚙️ Switch to: Tap to cycle"
                    null -> "⚙️ Tap behavior settings"
                }
                setOnClickListener {
                    languageManager?.toggleTapBehavior()
                    popupWindow.dismiss()
                    
                    // Show a brief indication of the change
                    val newBehavior = languageManager?.getTapBehavior()
                    Log.d(TAG, "Tap behavior changed to: $newBehavior")
                }
                setPadding(24, 16, 24, 16)
                setBackgroundColor(0xFFF5F5F5.toInt()) // Light gray background
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            layout.addView(tapBehaviorButton)

            popupWindow.contentView = layout
            popupWindow.width = LinearLayout.LayoutParams.WRAP_CONTENT
            popupWindow.height = LinearLayout.LayoutParams.WRAP_CONTENT
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true

            // Show popup below the button
            popupWindow.showAsDropDown(this, 0, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing language selection menu", e)
        }
    }

    /**
     * Update display when language changes externally
     */
    fun refreshDisplay() {
        updateDisplay()
    }
}