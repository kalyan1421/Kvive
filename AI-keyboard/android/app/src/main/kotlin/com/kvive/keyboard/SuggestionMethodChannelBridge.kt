package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.utils.LogUtil
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * 🌉 SUGGESTION METHOD CHANNEL BRIDGE
 * 
 * Bridges Flutter settings to UnifiedSuggestionController via MethodChannel
 * 
 * Handles:
 * - AI suggestions toggle
 * - Emoji suggestions toggle
 * - Clipboard suggestions toggle
 * - Next-word prediction toggle
 * - Real-time settings sync
 * 
 * Flutter Side Example:
 * ```dart
 * static const MethodChannel _channel = MethodChannel('ai_keyboard/suggestions');
 * 
 * await _channel.invokeMethod('updateSettings', {
 *   'aiSuggestions': true,
 *   'emojiSuggestions': true,
 *   'clipboardSuggestions': false,
 *   'nextWordPrediction': true,
 * });
 * ```
 */
class SuggestionMethodChannelBridge(
    private val context: Context,
    private val suggestionController: UnifiedSuggestionController
) : MethodChannel.MethodCallHandler {
    
    companion object {
        private const val TAG = "SuggestionBridge"
        private const val CHANNEL_NAME = "ai_keyboard/suggestions"
    }
    
    private var methodChannel: MethodChannel? = null
    
    /**
     * Initialize the method channel with Flutter engine
     * Call this from MainActivity.configureFlutterEngine()
     * 
     * @param flutterEngine The Flutter engine instance
     */
    fun initialize(flutterEngine: FlutterEngine) {
        try {
            methodChannel = MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                CHANNEL_NAME
            )
            methodChannel?.setMethodCallHandler(this)
            LogUtil.d(TAG, "✅ Suggestion MethodChannel initialized: $CHANNEL_NAME")
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error initializing suggestion method channel", e)
        }
    }
    
    /**
     * Handle method calls from Flutter
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "updateSettings" -> {
                handleUpdateSettings(call, result)
            }
            
            "getSettings" -> {
                handleGetSettings(result)
            }
            
            "clearCache" -> {
                handleClearCache(result)
            }
            
            "getStats" -> {
                handleGetStats(result)
            }
            
            else -> {
                result.notImplemented()
            }
        }
    }
    
    /**
     * Handle settings update from Flutter
     */
    private fun handleUpdateSettings(call: MethodCall, result: MethodChannel.Result) {
        try {
            val aiEnabled = call.argument<Boolean>("aiSuggestions")
            val emojiEnabled = call.argument<Boolean>("emojiSuggestions")
            val clipboardEnabled = call.argument<Boolean>("clipboardSuggestions")
            val nextWordEnabled = call.argument<Boolean>("nextWordPrediction")
            
            LogUtil.d(TAG, "📥 Settings update from Flutter: AI=$aiEnabled, Emoji=$emojiEnabled, Clipboard=$clipboardEnabled, NextWord=$nextWordEnabled")
            
            // Update controller settings
            suggestionController.updateSettings(
                aiEnabled = aiEnabled,
                emojiEnabled = emojiEnabled,
                clipboardEnabled = clipboardEnabled,
                nextWordEnabled = nextWordEnabled
            )

            clipboardEnabled?.let { enabled ->
                try {
                    val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
                    flutterPrefs.edit().putBoolean("flutter.clipboard_suggestion_enabled", enabled).apply()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error persisting clipboard suggestion setting", e)
                }
            }

            if (clipboardEnabled != null && context is AIKeyboardService) {
                context.reloadClipboardSettings()
            }

            result.success(true)
            LogUtil.d(TAG, "✅ Settings updated successfully")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error updating settings", e)
            result.error("UPDATE_ERROR", "Failed to update settings: ${e.message}", null)
        }
    }
    
    /**
     * Get current settings
     */
    private fun handleGetSettings(result: MethodChannel.Result) {
        try {
            val stats = suggestionController.getStats()
            
            val settings = mapOf(
                "aiEnabled" to stats["aiEnabled"],
                "emojiEnabled" to stats["emojiEnabled"],
                "clipboardEnabled" to stats["clipboardEnabled"],
                "nextWordEnabled" to stats["nextWordEnabled"]
            )
            
            result.success(settings)
            LogUtil.d(TAG, "📤 Settings sent to Flutter: $settings")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error getting settings", e)
            result.error("GET_ERROR", "Failed to get settings: ${e.message}", null)
        }
    }
    
    /**
     * Clear suggestion cache
     */
    private fun handleClearCache(result: MethodChannel.Result) {
        try {
            suggestionController.clearCache()
            result.success(true)
            LogUtil.d(TAG, "🗑️ Suggestion cache cleared")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error clearing cache", e)
            result.error("CLEAR_ERROR", "Failed to clear cache: ${e.message}", null)
        }
    }
    
    /**
     * Get controller statistics
     */
    private fun handleGetStats(result: MethodChannel.Result) {
        try {
            val stats = suggestionController.getStats()
            result.success(stats)
            LogUtil.d(TAG, "📊 Stats sent to Flutter: $stats")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "❌ Error getting stats", e)
            result.error("STATS_ERROR", "Failed to get stats: ${e.message}", null)
        }
    }
    
    /**
     * Notify Flutter of suggestion update (optional, for real-time sync)
     */
    fun notifySuggestionsUpdated(suggestions: List<UnifiedSuggestionController.UnifiedSuggestion>) {
        try {
            val data = suggestions.map { suggestion ->
                mapOf(
                    "text" to suggestion.text,
                    "type" to suggestion.type.name,
                    "score" to suggestion.score
                )
            }
            
            methodChannel?.invokeMethod("onSuggestionsUpdated", data)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error notifying Flutter of suggestions", e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            methodChannel?.setMethodCallHandler(null)
            methodChannel = null
            LogUtil.d(TAG, "✅ Suggestion bridge cleaned up")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error cleaning up bridge", e)
        }
    }
}
