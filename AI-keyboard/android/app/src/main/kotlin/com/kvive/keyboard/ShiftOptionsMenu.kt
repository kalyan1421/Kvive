package com.kvive.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Shift Options Menu that appears on long press of the Shift/Caps key
 * Provides options for Caps Lock toggle and alternate layouts
 */
class ShiftOptionsMenu(
    private val context: Context,
    private val capsShiftManager: CapsShiftManager
) {
    
    companion object {
        private const val TAG = "ShiftOptionsMenu"
    }
    
    private var popupWindow: PopupWindow? = null
    private var onMenuItemClickListener: ((String) -> Unit)? = null
    
    /**
     * Set callback for menu item clicks
     */
    fun setOnMenuItemClickListener(listener: (String) -> Unit) {
        onMenuItemClickListener = listener
    }
    
    /**
     * Show the shift options menu at the specified location
     */
    fun show(anchorView: View, x: Int, y: Int) {
        dismiss() // Dismiss any existing menu
        
        val menuView = createMenuView()
        
        popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            elevation = 8f
        }
        
        // Show popup at specified coordinates
        popupWindow?.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)
    }
    
    /**
     * Dismiss the menu
     */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    /**
     * Check if menu is currently showing
     */
    fun isShowing(): Boolean = popupWindow?.isShowing == true
    
    /**
     * Create the menu view with options
     */
    private fun createMenuView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.popup_background)
            setPadding(16, 8, 16, 8)
        }
        
        // Caps Lock Toggle Option
        val capsLockOption = createMenuOption(
            if (capsShiftManager.isCapsLockActive()) "Turn Off Caps Lock" else "Turn On Caps Lock",
            0 // No icon resource
        ) {
            val newCapsLockState = !capsShiftManager.isCapsLockActive()
            capsShiftManager.setCapsLock(newCapsLockState)
            onMenuItemClickListener?.invoke("caps_lock_toggle")
            dismiss()
        }
        
        // Separator
        val separator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(8, 4, 8, 4)
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.menu_separator))
        }
        
        // Alternate Layout Options (if available)
        val alternateLayoutOption = createMenuOption(
            "Switch Layout",
            0 // No icon resource
        ) {
            onMenuItemClickListener?.invoke("alternate_layout")
            dismiss()
        }
        
        // Language Switch Option
        val languageOption = createMenuOption(
            "Switch Language",
            0 // No icon resource
        ) {
            onMenuItemClickListener?.invoke("language_switch")
            dismiss()
        }
        
        // Add all options to container
        container.addView(capsLockOption)
        container.addView(separator)
        container.addView(alternateLayoutOption)
        container.addView(languageOption)
        
        return container
    }
    
    /**
     * Create a menu option view
     */
    private fun createMenuOption(
        text: String,
        iconRes: Int,
        onClick: () -> Unit
    ): View {
        val optionView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12, 12, 12, 12)
            isClickable = true
            isFocusable = true
            
            // Add ripple effect
            setBackgroundResource(R.drawable.menu_item_background)
            
            setOnClickListener { onClick() }
        }
        
        // Icon (if available)
        try {
            val iconView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.menu_icon_color))
                
                // Set icon based on text content
                when {
                    text.contains("Caps Lock") -> setText("⇪")
                    text.contains("Layout") -> setText("⌨")
                    text.contains("Language") -> setText("🌐")
                    else -> setText("•")
                }
            }
            optionView.addView(iconView)
        } catch (e: Exception) {
            // If icon resources don't exist, continue without icons
        }
        
        // Text
        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setText(text)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.menu_text_color))
        }
        
        optionView.addView(textView)
        
        return optionView
    }
}
