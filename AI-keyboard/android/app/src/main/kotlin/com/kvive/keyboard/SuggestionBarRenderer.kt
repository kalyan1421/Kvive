package com.kvive.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kvive.keyboard.utils.LogUtil

/**
 * 🎨 UNIFIED SUGGESTION BAR RENDERER
 * 
 * Renders all suggestion types in a unified, beautiful suggestion bar:
 * - Text suggestions (with auto-commit indicator)
 * - Next-word predictions
 * - Emoji suggestions
 * - Clipboard quick-paste chips
 * - AI rewrite suggestions
 * 
 * Features:
 * - Type-aware styling (colors, icons, emphasis)
 * - Auto-commit highlighting for first suggestion
 * - Smooth animations and transitions
 * - Responsive layout with horizontal scrolling
 * - Accessibility support
 */
class SuggestionBarRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "SuggestionBarRenderer"
        
        // Dimensions (in dp)
        private const val SUGGESTION_PADDING_HORIZONTAL = 16
        private const val SUGGESTION_PADDING_VERTICAL = 8
        private const val SUGGESTION_MARGIN = 6
        private const val SUGGESTION_MIN_WIDTH = 60
        private const val SUGGESTION_CORNER_RADIUS = 20f
        
        // Text sizes (in sp)
        private const val TEXT_SIZE_NORMAL = 14f
        private const val TEXT_SIZE_AUTOCORRECT = 15f
        private const val TEXT_SIZE_EMOJI = 18f
        
        // Colors (will be overridden by theme)
        private const val COLOR_BACKGROUND_DEFAULT = 0xFF3C3C3C.toInt()
        private const val COLOR_BACKGROUND_AUTOCORRECT = 0xFF4A90E2.toInt()
        private const val COLOR_BACKGROUND_EMOJI = 0xFF5C5C5C.toInt()
        private const val COLOR_BACKGROUND_CLIPBOARD = 0xFF6B4A9E.toInt()
        private const val COLOR_TEXT_DEFAULT = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_AUTOCORRECT = 0xFFFFFFFF.toInt()
    }
    
    private var onSuggestionClickListener: ((UnifiedSuggestionController.UnifiedSuggestion) -> Unit)? = null
    private var currentThemeColors: ThemeColors? = null
    
    /**
     * Theme colors for suggestion bar
     */
    data class ThemeColors(
        val backgroundDefault: Int = COLOR_BACKGROUND_DEFAULT,
        val backgroundAutocorrect: Int = COLOR_BACKGROUND_AUTOCORRECT,
        val backgroundEmoji: Int = COLOR_BACKGROUND_EMOJI,
        val backgroundClipboard: Int = COLOR_BACKGROUND_CLIPBOARD,
        val textDefault: Int = COLOR_TEXT_DEFAULT,
        val textAutocorrect: Int = COLOR_TEXT_AUTOCORRECT
    )
    
    /**
     * Set theme colors
     */
    fun setThemeColors(colors: ThemeColors) {
        currentThemeColors = colors
    }
    
    /**
     * Set suggestion click listener
     */
    fun setOnSuggestionClickListener(listener: (UnifiedSuggestionController.UnifiedSuggestion) -> Unit) {
        onSuggestionClickListener = listener
    }
    
    /**
     * 🎯 MAIN API: Render suggestions in a container
     * 
     * @param container The LinearLayout to render suggestions into
     * @param suggestions List of unified suggestions to render
     */
    fun renderSuggestions(
        container: LinearLayout,
        suggestions: List<UnifiedSuggestionController.UnifiedSuggestion>
    ) {
        try {
            LogUtil.d(TAG, "🎨 Rendering ${suggestions.size} suggestions")
            
            // Clear existing views
            container.removeAllViews()
            
            if (suggestions.isEmpty()) {
                LogUtil.d(TAG, "No suggestions to render")
                return
            }
            
            // Set container properties for horizontal scrolling
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            
            val scrollView = if (container.parent is HorizontalScrollView) {
                container.parent as HorizontalScrollView
            } else {
                null
            }
            
            // Render each suggestion
            suggestions.forEachIndexed { index, suggestion ->
                val chip = createSuggestionChip(suggestion, index == 0)
                container.addView(chip)
            }
            
            // Scroll to start
            scrollView?.post {
                scrollView.scrollTo(0, 0)
            }
            
            LogUtil.d(TAG, "✅ Rendered ${suggestions.size} suggestion chips")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error rendering suggestions", e)
        }
    }
    
    /**
     * Create a suggestion chip view
     */
    private fun createSuggestionChip(
        suggestion: UnifiedSuggestionController.UnifiedSuggestion,
        isFirst: Boolean
    ): TextView {
        val chip = TextView(context).apply {
            // Text
            text = suggestion.getDisplayText()
            
            // Text appearance
            setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextSize(suggestion))
            setTextColor(getTextColor(suggestion))
            typeface = if (isFirst && suggestion.type == UnifiedSuggestionController.SuggestionType.AUTOCORRECT) {
                Typeface.DEFAULT_BOLD
            } else {
                Typeface.DEFAULT
            }
            
            // Padding
            val paddingH = dpToPx(SUGGESTION_PADDING_HORIZONTAL)
            val paddingV = dpToPx(SUGGESTION_PADDING_VERTICAL)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            
            // Layout params
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(SUGGESTION_MARGIN)
                marginEnd = dpToPx(SUGGESTION_MARGIN)
                minWidth = dpToPx(SUGGESTION_MIN_WIDTH)
            }
            
            // Alignment
            gravity = Gravity.CENTER
            
            // Background
            background = createChipBackground(suggestion, isFirst)
            
            // Click listener
            setOnClickListener {
                LogUtil.d(TAG, "🔘 Suggestion clicked: ${suggestion.text} (${suggestion.type})")
                onSuggestionClickListener?.invoke(suggestion)
            }
            
            // Accessibility
            contentDescription = getContentDescription(suggestion)
        }
        
        return chip
    }
    
    /**
     * Create background drawable for chip
     */
    private fun createChipBackground(
        suggestion: UnifiedSuggestionController.UnifiedSuggestion,
        isFirst: Boolean
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = dpToPx(SUGGESTION_CORNER_RADIUS).toFloat()
        
        // Background color based on type
        val backgroundColor = getBackgroundColor(suggestion, isFirst)
        drawable.setColor(backgroundColor)
        
        // Add stroke for autocorrect
        if (isFirst && suggestion.type == UnifiedSuggestionController.SuggestionType.AUTOCORRECT) {
            drawable.setStroke(dpToPx(2), Color.parseColor("#6BA3E8"))
        }
        
        return drawable
    }
    
    /**
     * Get text size based on suggestion type
     */
    private fun getTextSize(suggestion: UnifiedSuggestionController.UnifiedSuggestion): Float {
        return when (suggestion.type) {
            UnifiedSuggestionController.SuggestionType.EMOJI -> TEXT_SIZE_EMOJI
            UnifiedSuggestionController.SuggestionType.AUTOCORRECT -> TEXT_SIZE_AUTOCORRECT
            else -> TEXT_SIZE_NORMAL
        }
    }
    
    /**
     * Get text color based on suggestion type
     */
    private fun getTextColor(suggestion: UnifiedSuggestionController.UnifiedSuggestion): Int {
        return when (suggestion.type) {
            UnifiedSuggestionController.SuggestionType.AUTOCORRECT -> 
                currentThemeColors?.textAutocorrect ?: COLOR_TEXT_AUTOCORRECT
            else -> 
                currentThemeColors?.textDefault ?: COLOR_TEXT_DEFAULT
        }
    }
    
    /**
     * Get background color based on suggestion type
     */
    private fun getBackgroundColor(
        suggestion: UnifiedSuggestionController.UnifiedSuggestion,
        isFirst: Boolean
    ): Int {
        val colors = currentThemeColors
        
        return when (suggestion.type) {
            UnifiedSuggestionController.SuggestionType.AUTOCORRECT -> 
                colors?.backgroundAutocorrect ?: COLOR_BACKGROUND_AUTOCORRECT
                
            UnifiedSuggestionController.SuggestionType.EMOJI -> 
                colors?.backgroundEmoji ?: COLOR_BACKGROUND_EMOJI
                
            UnifiedSuggestionController.SuggestionType.CLIPBOARD -> 
                colors?.backgroundClipboard ?: COLOR_BACKGROUND_CLIPBOARD
                
            else -> 
                colors?.backgroundDefault ?: COLOR_BACKGROUND_DEFAULT
        }
    }
    
    /**
     * Get content description for accessibility
     */
    private fun getContentDescription(suggestion: UnifiedSuggestionController.UnifiedSuggestion): String {
        return when (suggestion.type) {
            UnifiedSuggestionController.SuggestionType.AUTOCORRECT -> 
                "Autocorrect suggestion: ${suggestion.text}"
                
            UnifiedSuggestionController.SuggestionType.TYPING -> 
                "Suggestion: ${suggestion.text}"
                
            UnifiedSuggestionController.SuggestionType.NEXT_WORD -> 
                "Next word: ${suggestion.text}"
                
            UnifiedSuggestionController.SuggestionType.EMOJI -> 
                "Emoji: ${suggestion.text}"
                
            UnifiedSuggestionController.SuggestionType.CLIPBOARD -> 
                "Clipboard: ${suggestion.getDisplayText()}"
                
            UnifiedSuggestionController.SuggestionType.AI_REWRITE -> 
                "AI suggestion: ${suggestion.text}"
                
            UnifiedSuggestionController.SuggestionType.USER_LEARNED -> 
                "Your word: ${suggestion.text}"
        }
    }
    
    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
    
    /**
     * Convert dp to pixels (float)
     */
    private fun dpToPx(dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
    
    /**
     * Clear the suggestion bar
     */
    fun clearSuggestions(container: LinearLayout) {
        try {
            container.removeAllViews()
            LogUtil.d(TAG, "Cleared suggestion bar")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error clearing suggestions", e)
        }
    }
    
    /**
     * Show loading state
     */
    fun showLoadingState(container: LinearLayout) {
        try {
            container.removeAllViews()
            
            val loadingText = TextView(context).apply {
                text = "..."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_NORMAL)
                setTextColor(COLOR_TEXT_DEFAULT)
                gravity = Gravity.CENTER
                
                val paddingH = dpToPx(SUGGESTION_PADDING_HORIZONTAL)
                val paddingV = dpToPx(SUGGESTION_PADDING_VERTICAL)
                setPadding(paddingH, paddingV, paddingH, paddingV)
            }
            
            container.addView(loadingText)
            LogUtil.d(TAG, "Showing loading state")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error showing loading state", e)
        }
    }
    
    /**
     * Animate suggestion update
     */
    fun animateSuggestionUpdate(container: LinearLayout, suggestions: List<UnifiedSuggestionController.UnifiedSuggestion>) {
        try {
            // Simple fade-in animation
            container.alpha = 0f
            renderSuggestions(container, suggestions)
            container.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error animating suggestions", e)
            // Fallback to non-animated render
            renderSuggestions(container, suggestions)
        }
    }
}

