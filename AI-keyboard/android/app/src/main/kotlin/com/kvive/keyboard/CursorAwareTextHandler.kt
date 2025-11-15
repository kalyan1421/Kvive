package com.kvive.keyboard

import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import android.view.inputmethod.InputConnection
import java.util.regex.Pattern

/**
 * Utility class for cursor-aware text manipulation operations
 * Ensures proper cursor positioning for all text operations
 */
class CursorAwareTextHandler {
    companion object {
        private const val TAG = "CursorAwareTextHandler"
        
        /**
         * Insert text at cursor position while preserving cursor behavior
         */
        fun insertText(ic: InputConnection, text: String, moveCursor: Boolean = true): Boolean {
            return try {
                // commitText automatically moves cursor after the inserted text when newCursorPosition = 1
                val cursorPosition = if (moveCursor) 1 else 0
                ic.commitText(text, cursorPosition)
                Log.d(TAG, "Inserted text: '$text' with cursor movement: $moveCursor")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting text", e)
                false
            }
        }
        
        /**
         * Insert emoji with proper Unicode cluster handling
         */
        fun insertEmoji(ic: InputConnection, emoji: String): Boolean {
            return try {
                // Emoji insertion always moves cursor after the emoji
                ic.commitText(emoji, 1)
                Log.d(TAG, "Inserted emoji: '$emoji' (${emoji.length} Unicode units)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting emoji", e)
                false
            }
        }
        
        /**
         * Enhanced backspace that handles emoji clusters and surrogate pairs
         */
        fun performBackspace(ic: InputConnection): Int {
            return try {
                val selectedText = ic.getSelectedText(0)
                
                if (selectedText.isNullOrEmpty()) {
                    // No selection - delete character/cluster before cursor
                    val textBefore = ic.getTextBeforeCursor(10, 0)?.toString() ?: ""
                    if (textBefore.isEmpty()) return 0
                    
                    val deleteLength = calculateDeleteLength(textBefore)
                    ic.deleteSurroundingText(deleteLength, 0)
                    Log.d(TAG, "Backspace deleted $deleteLength characters")
                    deleteLength
                } else {
                    // Selection exists - delete selected text
                    ic.commitText("", 1)
                    Log.d(TAG, "Backspace deleted selected text: '${selectedText.take(20)}...'")
                    selectedText.length
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in backspace operation", e)
                // Fallback to simple deletion
                ic.deleteSurroundingText(1, 0)
                1
            }
        }
        
        /**
         * Calculate how many characters to delete for proper emoji cluster handling
         */
        private fun calculateDeleteLength(textBefore: String): Int {
            if (textBefore.isEmpty()) return 0
            
            // Define emoji patterns in order of complexity (most complex first)
            val emojiPatterns = listOf(
                // Family emojis and complex ZWJ sequences
                Pattern.compile("👨‍👩‍👧‍👦$"),
                Pattern.compile("👨‍👩‍👧$"),
                Pattern.compile("👨‍👩‍👦$"),
                Pattern.compile("👩‍👩‍👧‍👦$"),
                Pattern.compile("👩‍👩‍👧$"),
                Pattern.compile("👩‍👩‍👦$"),
                Pattern.compile("👨‍👨‍👧‍👦$"),
                Pattern.compile("👨‍👨‍👧$"),
                Pattern.compile("👨‍👨‍👦$"),
                
                // Heart on fire and other compound emojis
                Pattern.compile("❤️‍🔥$"),
                Pattern.compile("💑🏻$|💑🏼$|💑🏽$|💑🏾$|💑🏿$"),
                
                // General ZWJ sequences (zero-width joiner)
                Pattern.compile("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF](?:\\u200D[\\uD800-\\uDBFF][\\uDC00-\\uDFFF])+$"),
                
                // Emoji with skin tone modifiers
                Pattern.compile("[\\uD83C\\uDFFB-\\uD83C\\uDFFF][\\uD800-\\uDBFF][\\uDC00-\\uDFFF]$"),
                Pattern.compile("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF][\\uD83C\\uDFFB-\\uD83C\\uDFFF]$"),
                
                // Emoji with variation selectors
                Pattern.compile("[\\u2600-\\u27BF]\\uFE0F$"),
                Pattern.compile("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]\\uFE0F$"),
                
                // Basic surrogate pairs (standard emoji)
                Pattern.compile("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]$"),
                
                // Regional indicator sequences (flag emojis)
                Pattern.compile("[\\uD83C\\uDDE6-\\uD83C\\uDDFF][\\uD83C\\uDDE6-\\uD83C\\uDDFF]$")
            )
            
            // Check each pattern to find the longest match
            for (pattern in emojiPatterns) {
                val matcher = pattern.matcher(textBefore)
                if (matcher.find()) {
                    val matchLength = matcher.group().length
                    Log.d(TAG, "Found emoji pattern of length $matchLength: '${matcher.group()}'")
                    return matchLength
                }
            }
            
            // Check for basic surrogate pair manually
            if (textBefore.length >= 2) {
                val lastChar = textBefore[textBefore.length - 1]
                val secondLastChar = textBefore[textBefore.length - 2]
                
                if (Character.isLowSurrogate(lastChar) && Character.isHighSurrogate(secondLastChar)) {
                    Log.d(TAG, "Found basic surrogate pair")
                    return 2
                }
            }
            
            // Default: delete 1 character
            return 1
        }
        
        /**
         * Get cursor position information for debugging
         */
        fun getCursorInfo(ic: InputConnection): String {
            return try {
                val beforeCursor = ic.getTextBeforeCursor(1000, 0)?.length ?: 0
                val afterCursor = ic.getTextAfterCursor(1000, 0)?.length ?: 0
                val selection = ic.getSelectedText(0)
                
                if (selection.isNullOrEmpty()) {
                    "Cursor at position $beforeCursor (${beforeCursor + afterCursor} total chars)"
                } else {
                    "Selection: '${selection.take(20)}...' at position $beforeCursor"
                }
            } catch (e: Exception) {
                "Cursor info unavailable: ${e.message}"
            }
        }
        
        /**
         * Preserve cursor position during keyboard state changes
         */
        fun preserveCursorPosition(ic: InputConnection, operation: () -> Unit) {
            try {
                val beforeCursor = ic.getTextBeforeCursor(1000, 0)?.length ?: 0
                val afterCursor = ic.getTextAfterCursor(1000, 0)?.length ?: 0
                
                // Perform the operation
                operation()
                
                // Cursor position is naturally preserved by Android's InputConnection
                // when we don't call setSelection() or other cursor-moving methods
                Log.d(TAG, "Cursor position preserved during operation at $beforeCursor")
            } catch (e: Exception) {
                Log.e(TAG, "Error preserving cursor position", e)
                // Still perform the operation even if cursor tracking fails
                operation()
            }
        }
        
        /**
         * Check if the cursor is at the beginning of text
         */
        fun isCursorAtStart(ic: InputConnection): Boolean {
            return try {
                val textBefore = ic.getTextBeforeCursor(1, 0)
                textBefore.isNullOrEmpty()
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Check if the cursor is at the end of text
         */
        fun isCursorAtEnd(ic: InputConnection): Boolean {
            return try {
                val textAfter = ic.getTextAfterCursor(1, 0)
                textAfter.isNullOrEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }
}
