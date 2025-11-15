package com.kvive.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import android.Manifest
import com.kvive.keyboard.managers.BaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

private fun SharedPreferences.getFloatCompat(key: String, defaultValue: Float): Float {
    val raw = all[key] ?: return defaultValue
    return when (raw) {
        is Float -> raw
        is Double -> raw.toFloat()
        is Int -> raw.toFloat()
        is Long -> raw.toFloat()
        is String -> decodeFlutterDouble(raw)?.toFloat()
            ?: raw.toFloatOrNull()
            ?: defaultValue
        else -> defaultValue
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
    return null
}

/**
 * 🎯 UNIFIED CLIPBOARD MANAGER - ALL-IN-ONE FILE
 * 
 * This file contains ALL clipboard functionality:
 * - ClipboardItem: Data model
 * - ClipboardHistoryManager: Capture & storage
 * - ClipboardPanel: Popup UI
 * - ClipboardStripView: Quick access strip
 * - UnifiedClipboardManager: Main coordinator + MethodChannel
 * 
 * Single file for easy maintenance and deployment
 */

// ============================================================================
// DATA MODEL: ClipboardItem
// ============================================================================

/**
 * Represents a clipboard history item with metadata
 */
data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isTemplate: Boolean = false,
    val category: String? = null
) {
    
    fun isOTP(): Boolean {
        return text.matches(Regex("\\b\\d{4,8}\\b"))
    }
    
    fun isExpired(expiryDurationMs: Long): Boolean {
        if (isPinned || isTemplate) return false
        return System.currentTimeMillis() - timestamp > expiryDurationMs
    }
    
    fun getPreview(maxLength: Int = 50): String {
        return if (text.length <= maxLength) {
            text
        } else {
            "${text.take(maxLength - 3)}..."
        }
    }
    
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("text", text)
            put("timestamp", timestamp)
            put("isPinned", isPinned)
            put("isTemplate", isTemplate)
            put("category", category)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): ClipboardItem {
            return ClipboardItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                text = json.optString("text", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isPinned = json.optBoolean("isPinned", false),
                isTemplate = json.optBoolean("isTemplate", false),
                category = json.optString("category", null).takeIf { !it.isNullOrEmpty() }
            )
        }
        
        fun createTemplate(text: String, category: String? = null): ClipboardItem {
            return ClipboardItem(
                text = text,
                isPinned = true,
                isTemplate = true,
                category = category
            )
        }
    }
}

// ============================================================================
// HISTORY MANAGER: Capture, Store, Sync
// ============================================================================

/**
 * Manages clipboard history with automatic cleanup, persistence, and template support
 */
class ClipboardHistoryManager(context: Context) : BaseManager(context) {
    
    companion object {
        private const val TAG = "[Clipboard]"
        private const val KEY_HISTORY = "history_items"
        private const val KEY_TEMPLATES = "template_items"
        private const val KEY_CLIPBOARD_ENABLED = "clipboard_enabled"
        private const val KEY_MAX_HISTORY_SIZE = "max_history_size"
        private const val KEY_AUTO_EXPIRY_ENABLED = "auto_expiry_enabled"
        private const val KEY_EXPIRY_DURATION_MINUTES = "expiry_duration_minutes"
        
        private const val DEFAULT_MAX_HISTORY_SIZE = 20
        private const val DEFAULT_EXPIRY_DURATION_MINUTES = 60L
    }
    
    override fun getPreferencesName() = "clipboard_history"
    
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val historyItems = CopyOnWriteArrayList<ClipboardItem>()
    private val templateItems = CopyOnWriteArrayList<ClipboardItem>()
    
    private var enabled = true  // Track if clipboard is enabled
    private var maxHistorySize = DEFAULT_MAX_HISTORY_SIZE
    private var autoExpiryEnabled = true
    private var expiryDurationMinutes = DEFAULT_EXPIRY_DURATION_MINUTES
    private var vibratePermissionWarningLogged = false
    
    private val listeners = mutableListOf<ClipboardHistoryListener>()
    
    // Firebase for cloud sync
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Vibrator for copy feedback
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    
    interface ClipboardHistoryListener {
        fun onHistoryUpdated(items: List<ClipboardItem>)
        fun onNewClipboardItem(item: ClipboardItem)
    }
    
    private val clipboardChangeListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            // Only capture if clipboard is enabled
            if (!enabled) {
                Log.d(TAG, "⚠️ Clipboard is disabled, skipping capture")
                return@OnPrimaryClipChangedListener
            }
            
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    addClipboardItem(text)
                    showCopyEffect(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling clipboard change", e)
        }
    }
    
    override fun initialize() {
        Log.d(TAG, "🚀 Initializing ClipboardHistoryManager")
        loadSettings()
        loadHistoryFromPrefs()
        loadTemplatesFromPrefs()
        clipboardManager.addPrimaryClipChangedListener(clipboardChangeListener)
        cleanupExpiredItems()
        Log.d(TAG, "✅ ClipboardHistoryManager initialized with ${historyItems.size} history items")
    }
    
    fun cleanup() {
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardChangeListener)
            listeners.clear()
            Log.d(TAG, "✅ ClipboardHistoryManager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during cleanup", e)
        }
    }
    
    private fun hasVibratePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Show visual and haptic feedback when text is copied
     */
    private fun showCopyEffect(text: String) {
        try {
            // Vibrate for 50ms if permission granted
            vibrator?.let {
                if (hasVibratePermission()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(50)
                    }
                } else if (!vibratePermissionWarningLogged) {
                    vibratePermissionWarningLogged = true
                    Log.w(TAG, "VIBRATE permission missing; skipping clipboard haptics")
                }
            }
            
            // Log the copy action
            val preview = if (text.length > 30) "${text.take(30)}..." else text
            Log.d(TAG, "✅ Copied to Kvīve Clipboard: $preview")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing copy effect", e)
        }
    }
    
    /**
     * Manually sync from system clipboard
     */
    fun syncFromSystemClipboard() {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    // Check if not already in history
                    if (historyItems.isEmpty() || historyItems[0].text != text.trim()) {
                        addClipboardItem(text)
                        Log.d(TAG, "✅ Synced from system clipboard: ${text.take(30)}...")
                    } else {
                        Log.d(TAG, "⚠️ Skipped duplicate from system clipboard")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing from system clipboard", e)
        }
    }
    
    /**
     * Sync clipboard history to Firestore (Kvīve Cloud)
     */
    fun syncToCloud() {
        try {
            val user = auth.currentUser
            if (user == null || user.isAnonymous) {
                Log.d(TAG, "⚠️ Cloud sync skipped: User not authenticated")
                return
            }
            
            val userId = user.uid
            val clipboardCollection = firestore.collection("users")
                .document(userId)
                .collection("clipboard")
            
            // Upload recent non-template items (last 20)
            val itemsToSync = historyItems.filter { !it.isTemplate }.take(20)
            
            itemsToSync.forEach { item ->
                val data = hashMapOf(
                    "text" to item.text,
                    "timestamp" to item.timestamp,
                    "isPinned" to item.isPinned,
                    "id" to item.id
                )
                
                clipboardCollection.document(item.id)
                    .set(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "☁️ Synced item to cloud: ${item.getPreview(30)}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to sync item to cloud", e)
                    }
            }
            
            Log.d(TAG, "☁️ Cloud sync initiated for ${itemsToSync.size} items")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing to cloud", e)
        }
    }
    
    /**
     * Sync clipboard history from Firestore (Kvīve Cloud)
     */
    fun syncFromCloud() {
        try {
            val user = auth.currentUser
            if (user == null || user.isAnonymous) {
                Log.d(TAG, "⚠️ Cloud sync skipped: User not authenticated")
                return
            }
            
            val userId = user.uid
            firestore.collection("users")
                .document(userId)
                .collection("clipboard")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener { documents ->
                    var syncedCount = 0
                    for (document in documents) {
                        try {
                            val text = document.getString("text") ?: continue
                            val timestamp = document.getLong("timestamp") ?: System.currentTimeMillis()
                            val isPinned = document.getBoolean("isPinned") ?: false
                            val id = document.getString("id") ?: document.id
                            
                            // Check if item already exists
                            val exists = historyItems.any { it.id == id }
                            if (!exists) {
                                val item = ClipboardItem(
                                    id = id,
                                    text = text,
                                    timestamp = timestamp,
                                    isPinned = isPinned
                                )
                                historyItems.add(item)
                                syncedCount++
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing cloud item", e)
                        }
                    }
                    
                    if (syncedCount > 0) {
                        // Sort by timestamp
                        historyItems.sortByDescending { it.timestamp }
                        // Trim to max size
                        while (historyItems.size > maxHistorySize) {
                            historyItems.removeAt(historyItems.size - 1)
                        }
                        saveHistoryToPrefs()
                        notifyHistoryUpdated()
                        Log.d(TAG, "☁️ Synced $syncedCount items from cloud")
                    } else {
                        Log.d(TAG, "☁️ No new items to sync from cloud")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to sync from cloud", e)
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing from cloud", e)
        }
    }
    
    private fun addClipboardItem(text: String) {
        try {
            val trimmedText = text.trim()
            if (trimmedText.isEmpty()) return
            
            if (historyItems.isNotEmpty() && historyItems[0].text == trimmedText) {
                Log.d(TAG, "⚠️ Skipped duplicate")
                return
            }
            
            val newItem = ClipboardItem(text = trimmedText)
            historyItems.add(0, newItem)
            
            while (historyItems.size > maxHistorySize) {
                val removed = historyItems.removeAt(historyItems.size - 1)
                Log.d(TAG, "🗑️ Removed old item: ${removed.getPreview()}")
            }
            
            saveHistoryToPrefs()
            notifyHistoryUpdated()
            notifyNewItem(newItem)
            Log.d(TAG, "✅ Saved: ${newItem.getPreview()}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding item", e)
        }
    }
    
    fun getAllItems(): List<ClipboardItem> {
        cleanupExpiredItems()
        return (templateItems + historyItems).distinctBy { it.text }
    }
    
    fun getHistoryItems(): List<ClipboardItem> {
        cleanupExpiredItems()
        return historyItems.toList()
    }
    
    fun getTemplateItems(): List<ClipboardItem> = templateItems.toList()
    
    fun getHistoryForUI(maxItems: Int): List<ClipboardItem> {
        cleanupExpiredItems()
        val allItems = mutableListOf<ClipboardItem>()
        allItems.addAll(templateItems)
        val templateTexts = templateItems.map { it.text }.toSet()
        val uniqueHistoryItems = historyItems.filter { it.text !in templateTexts }
        allItems.addAll(uniqueHistoryItems)
        return allItems.take(maxItems)
    }
    
    fun getMostRecentItem(): ClipboardItem? {
        cleanupExpiredItems()
        return historyItems.firstOrNull()
    }
    
    fun getOTPItems(): List<ClipboardItem> {
        cleanupExpiredItems()
        return historyItems.filter { it.isOTP() }
    }
    
    fun togglePin(itemId: String): Boolean {
        val item = historyItems.find { it.id == itemId }
        if (item != null) {
            val updatedItem = item.copy(isPinned = !item.isPinned)
            val index = historyItems.indexOf(item)
            historyItems[index] = updatedItem
            saveHistoryToPrefs()
            notifyHistoryUpdated()
            val pinIcon = if (updatedItem.isPinned) "📌" else "📍"
            Log.d(TAG, "$pinIcon Toggled pin: ${updatedItem.getPreview()}")
            return updatedItem.isPinned
        }
        return false
    }
    
    fun deleteItem(itemId: String): Boolean {
        val historyRemoved = historyItems.removeIf { it.id == itemId }
        val templateRemoved = templateItems.removeIf { it.id == itemId }
        
        if (historyRemoved || templateRemoved) {
            if (historyRemoved) saveHistoryToPrefs()
            if (templateRemoved) saveTemplatesToPrefs()
            notifyHistoryUpdated()
            Log.d(TAG, "🗑️ Deleted item: $itemId")
            return true
        }
        return false
    }
    
    fun clearNonPinnedItems(): Boolean {
        val removable = historyItems.filter { !it.isPinned && !it.isTemplate }
        if (removable.isEmpty()) return false
        removable.forEach { historyItems.remove(it) }
        saveHistoryToPrefs()
        notifyHistoryUpdated()
        Log.d(TAG, "🧹 Cleared ${removable.size} non-pinned clipboard items")
        return true
    }
    
    fun addTemplate(text: String, category: String? = null): ClipboardItem {
        val template = ClipboardItem.createTemplate(text, category)
        templateItems.add(template)
        saveTemplatesToPrefs()
        notifyHistoryUpdated()
        Log.d(TAG, "📝 Added template: ${template.getPreview()}")
        return template
    }
    
    fun updateSettings(
        enabled: Boolean = this.enabled,
        maxHistorySize: Int = this.maxHistorySize,
        autoExpiryEnabled: Boolean = this.autoExpiryEnabled,
        expiryDurationMinutes: Long = this.expiryDurationMinutes
    ) {
        this.enabled = enabled
        this.maxHistorySize = maxHistorySize
        this.autoExpiryEnabled = autoExpiryEnabled
        this.expiryDurationMinutes = expiryDurationMinutes
        
        saveSettings()
        loadFromFlutterPrefs()
        cleanupExpiredItems()
        
        while (historyItems.size > maxHistorySize) {
            historyItems.removeAt(historyItems.size - 1)
        }
        
        if (historyItems.size != getHistoryItems().size) {
            saveHistoryToPrefs()
            notifyHistoryUpdated()
        }
        
        Log.d(TAG, "⚙️ Updated settings: enabled=$enabled, maxSize=$maxHistorySize, autoExpiry=$autoExpiryEnabled")
    }
    
    fun isEnabled(): Boolean = enabled
    
    private fun cleanupExpiredItems() {
        if (!autoExpiryEnabled) return
        
        val expiryDurationMs = expiryDurationMinutes * 60 * 1000
        val initialSize = historyItems.size
        
        historyItems.removeIf { it.isExpired(expiryDurationMs) }
        
        if (historyItems.size != initialSize) {
            saveHistoryToPrefs()
            notifyHistoryUpdated()
            Log.d(TAG, "🧹 Cleaned up ${initialSize - historyItems.size} expired items")
        }
    }
    
    fun addListener(listener: ClipboardHistoryListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: ClipboardHistoryListener) {
        listeners.remove(listener)
    }
    
    private fun notifyHistoryUpdated() {
        listeners.forEach { it.onHistoryUpdated(getAllItems()) }
    }
    
    private fun notifyNewItem(item: ClipboardItem) {
        listeners.forEach { it.onNewClipboardItem(item) }
    }
    
    private fun loadSettings() {
        enabled = prefs.getBoolean(KEY_CLIPBOARD_ENABLED, true)
        maxHistorySize = prefs.getInt(KEY_MAX_HISTORY_SIZE, DEFAULT_MAX_HISTORY_SIZE)
        autoExpiryEnabled = prefs.getBoolean(KEY_AUTO_EXPIRY_ENABLED, true)
        expiryDurationMinutes = prefs.getLong(KEY_EXPIRY_DURATION_MINUTES, DEFAULT_EXPIRY_DURATION_MINUTES)
        Log.d(TAG, "⚙️ Settings loaded: enabled=$enabled, maxSize=$maxHistorySize, autoExpiry=$autoExpiryEnabled")
    }
    
    private fun saveSettings() {
        prefs.edit()
            .putBoolean(KEY_CLIPBOARD_ENABLED, enabled)
            .putInt(KEY_MAX_HISTORY_SIZE, maxHistorySize)
            .putBoolean(KEY_AUTO_EXPIRY_ENABLED, autoExpiryEnabled)
            .putLong(KEY_EXPIRY_DURATION_MINUTES, expiryDurationMinutes)
            .commit()
        Log.d(TAG, "💾 Settings saved: enabled=$enabled, maxSize=$maxHistorySize, autoExpiry=$autoExpiryEnabled")
    }
    
    private fun loadHistoryFromPrefs() {
        try {
            val historyJson = prefs.getString(KEY_HISTORY, null)
            if (historyJson != null) {
                val jsonArray = JSONArray(historyJson)
                historyItems.clear()
                for (i in 0 until jsonArray.length()) {
                    val item = ClipboardItem.fromJson(jsonArray.getJSONObject(i))
                    historyItems.add(item)
                }
                Log.d(TAG, "✅ Loaded ${historyItems.size} history items")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading history", e)
        }
    }
    
    private fun saveHistoryToPrefs() {
        try {
            val jsonArray = JSONArray()
            historyItems.forEach { item -> jsonArray.put(item.toJson()) }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).commit()
            syncToFlutterPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving history", e)
        }
    }
    
    private fun syncToFlutterPrefs() {
        try {
            val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            historyItems.forEach { item -> jsonArray.put(item.toJson()) }
            flutterPrefs.edit().putString("flutter.clipboard_items", jsonArray.toString()).commit()
            Log.d(TAG, "🔄 Synced ${historyItems.size} items to Flutter")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error syncing to Flutter", e)
        }
    }
    
    private fun loadFromFlutterPrefs() {
        try {
            val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            val itemsJson = flutterPrefs.getString("flutter.clipboard_items", null)
            if (itemsJson.isNullOrEmpty()) return
            
            val jsonArray = JSONArray(itemsJson)
            val flutterItems = mutableListOf<ClipboardItem>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val item = ClipboardItem.fromJson(jsonArray.getJSONObject(i))
                    if (item.text.isNotEmpty()) {
                        flutterItems.add(item)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Failed to parse item at index $i")
                }
            }
            
            flutterItems.forEach { flutterItem ->
                val existingIndex = historyItems.indexOfFirst { it.id == flutterItem.id }
                if (existingIndex >= 0) {
                    historyItems[existingIndex] = flutterItem
                } else {
                    historyItems.add(flutterItem)
                }
            }
            
            Log.d(TAG, "📲 Loaded ${flutterItems.size} items from Flutter")
            if (flutterItems.isNotEmpty()) {
                saveHistoryToPrefs()
            }
            notifyHistoryUpdated()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading from Flutter", e)
        }
    }
    
    private fun loadTemplatesFromPrefs() {
        try {
            val templatesJson = prefs.getString(KEY_TEMPLATES, null)
            if (templatesJson != null) {
                val jsonArray = JSONArray(templatesJson)
                templateItems.clear()
                for (i in 0 until jsonArray.length()) {
                    val item = ClipboardItem.fromJson(jsonArray.getJSONObject(i))
                    templateItems.add(item)
                }
                Log.d(TAG, "📝 Loaded ${templateItems.size} templates")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading templates", e)
        }
    }
    
    private fun saveTemplatesToPrefs() {
        try {
            val jsonArray = JSONArray()
            templateItems.forEach { item -> jsonArray.put(item.toJson()) }
            prefs.edit().putString(KEY_TEMPLATES, jsonArray.toString()).commit()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving templates", e)
        }
    }
}

// ============================================================================
// UI COMPONENT: ClipboardPanel (Popup)
// ============================================================================

/**
 * Clipboard panel UI that shows clipboard history in a popup overlay
 */
class ClipboardPanel(
    private val context: Context,
    private val themeManager: ThemeManager
) {
    
    companion object {
        private const val TAG = "ClipboardPanel"
    }
    
    private var popupWindow: PopupWindow? = null
    private var onItemSelected: ((ClipboardItem) -> Unit)? = null
    private var onItemPinToggled: ((ClipboardItem) -> Unit)? = null
    private var onItemDeleted: ((ClipboardItem) -> Unit)? = null
    
    fun show(anchorView: View, items: List<ClipboardItem>, isEnabled: Boolean = true) {
        dismiss()
        
        try {
            val contentView = createContentView(items, isEnabled)
            val bgColor = themeManager.getKeyboardBackgroundColor()
            
            popupWindow = PopupWindow(
                contentView,
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.4).toInt(),
                true
            ).apply {
                inputMethodMode = PopupWindow.INPUT_METHOD_FROM_FOCUSABLE
                isOutsideTouchable = true
                isFocusable = false
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
                elevation = 8f
                showAsDropDown(anchorView, 0, -anchorView.height - height)
            }
            
            Log.d(TAG, "Clipboard panel shown: enabled=$isEnabled, items=${items.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing clipboard panel", e)
        }
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    fun updateItems(items: List<ClipboardItem>) {
        if (popupWindow?.isShowing == true) {
            dismiss()
        }
    }
    
    fun setCallbacks(
        onItemSelected: (ClipboardItem) -> Unit,
        onItemPinToggled: (ClipboardItem) -> Unit,
        onItemDeleted: (ClipboardItem) -> Unit
    ) {
        this.onItemSelected = onItemSelected
        this.onItemPinToggled = onItemPinToggled
        this.onItemDeleted = onItemDeleted
    }
    
    private fun createContentView(items: List<ClipboardItem>, isEnabled: Boolean = true): View {
        val bgColor = themeManager.getKeyboardBackgroundColor()
        val textColor = themeManager.getTextColor()
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val header = TextView(context).apply {
            text = if (isEnabled) "Clipboard History" else "Clipboard Disabled"
            textSize = 18f
            setTextColor(textColor)
            setPadding(0, 0, 0, 16)
            gravity = android.view.Gravity.CENTER
        }
        container.addView(header)
        
        // Show disabled message if clipboard is off
        if (!isEnabled) {
            val disabledText = TextView(context).apply {
                text = "📋 Clipboard is currently disabled\n\nOpen Settings to enable clipboard history"
                textSize = 14f
                setTextColor(textColor)
                gravity = android.view.Gravity.CENTER
                setPadding(32, 32, 32, 32)
                setTypeface(null, Typeface.NORMAL)
            }
            container.addView(disabledText)
            
            // Add "Open Settings" button
            val settingsButton = android.widget.Button(context).apply {
                text = "Open Settings"
                textSize = 14f
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    // Launch settings screen
                    try {
                        val intent = android.content.Intent(context, MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("navigate_to", "clipboard_settings")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening settings", e)
                    }
                }
            }
            container.addView(settingsButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = 16
            })
            
            return container
        }
        
        if (items.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "No clipboard history yet.\nCopy some text to get started!"
                textSize = 14f
                setTextColor(textColor)
                gravity = android.view.Gravity.CENTER
                setPadding(32, 32, 32, 32)
            }
            container.addView(emptyText)
        } else {
            val scrollView = android.widget.ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val itemsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            items.take(8).forEachIndexed { index, item ->
                val itemView = createItemView(item, textColor)
                itemsContainer.addView(itemView)
                
                if (index < items.size - 1) {
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        val dividerColor = android.graphics.Color.argb(
                            32,
                            android.graphics.Color.red(textColor),
                            android.graphics.Color.green(textColor),
                            android.graphics.Color.blue(textColor)
                        )
                        setBackgroundColor(dividerColor)
                    }
                    itemsContainer.addView(divider)
                }
            }
            
            scrollView.addView(itemsContainer)
            container.addView(scrollView)
        }
        
        return container
    }
    
    private fun createItemView(item: ClipboardItem, textColor: Int): View {
        val itemLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            setOnClickListener {
                onItemSelected?.invoke(item)
                dismiss()
            }
        }
        
        val contentText = TextView(context).apply {
            val prefix = if (item.isOTP()) "🔢 OTP: " else "📋 "
            text = "$prefix${item.getPreview(40)}"
            textSize = 14f
            setTextColor(textColor)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        itemLayout.addView(contentText)
        
        if (!item.isTemplate) {
            val pinButton = TextView(context).apply {
                text = if (item.isPinned) "📌" else "📍"
                textSize = 16f
                setPadding(8, 0, 8, 0)
                isClickable = true
                setOnClickListener {
                    onItemPinToggled?.invoke(item)
                }
            }
            itemLayout.addView(pinButton)
            
            val deleteButton = TextView(context).apply {
                text = "🗑️"
                textSize = 16f
                setPadding(8, 0, 0, 0)
                isClickable = true
                setOnClickListener {
                    onItemDeleted?.invoke(item)
                }
            }
            itemLayout.addView(deleteButton)
        }
        
        return itemLayout
    }
}

// ============================================================================
// UI COMPONENT: ClipboardStripView (Quick Access)
// ============================================================================

/**
 * Clipboard strip view that displays recent/pinned clipboard items
 */
class ClipboardStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {
    
    companion object {
        private const val MAX_VISIBLE_ITEMS = 5
        private const val ITEM_MAX_CHARS = 20
    }
    
    private val itemsContainer: LinearLayout
    private val items = mutableListOf<ClipboardItem>()
    private var onItemClickListener: ((ClipboardItem) -> Unit)? = null
    private var onItemLongClickListener: ((ClipboardItem) -> Unit)? = null
    private var themeManager: ThemeManager? = null
    
    init {
        itemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
        }
        
        addView(itemsContainer)
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setBackgroundColor(Color.parseColor("#F5F5F5"))
        visibility = View.GONE
    }
    
    fun setThemeManager(themeManager: ThemeManager) {
        this.themeManager = themeManager
        applyTheme()
    }
    
    fun updateItems(clipboardItems: List<ClipboardItem>) {
        items.clear()
        items.addAll(clipboardItems.take(MAX_VISIBLE_ITEMS))
        renderItems()
        visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }
    
    fun setOnItemClickListener(listener: (ClipboardItem) -> Unit) {
        onItemClickListener = listener
    }
    
    fun setOnItemLongClickListener(listener: (ClipboardItem) -> Unit) {
        onItemLongClickListener = listener
    }
    
    fun clear() {
        items.clear()
        itemsContainer.removeAllViews()
        visibility = View.GONE
    }
    
    private fun renderItems() {
        itemsContainer.removeAllViews()
        
        items.forEach { item ->
            val itemView = createItemView(item)
            itemsContainer.addView(itemView)
        }
    }
    
    private fun createItemView(item: ClipboardItem): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 8.dp
            }
            setPadding(12.dp, 6.dp, 12.dp, 6.dp)
            
            val bgColor = when {
                item.isPinned -> Color.parseColor("#E3F2FD")
                item.isOTP() -> Color.parseColor("#FFF3E0")
                else -> Color.parseColor("#FFFFFF")
            }
            setBackgroundColor(bgColor)
            
            background = ContextCompat.getDrawable(context, android.R.drawable.dialog_holo_light_frame)
            background?.alpha = 50
            
            setOnClickListener {
                onItemClickListener?.invoke(item)
            }
            
            setOnLongClickListener {
                onItemLongClickListener?.invoke(item)
                true
            }
            
            isClickable = true
            isFocusable = true
        }
        
        if (item.isPinned) {
            val pinIcon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(16.dp, 16.dp).apply {
                    marginEnd = 4.dp
                }
                setImageResource(android.R.drawable.star_on)
                setColorFilter(Color.parseColor("#1976D2"))
            }
            container.addView(pinIcon)
        }
        
        val textView = TextView(context).apply {
            val displayText = if (item.text.length > ITEM_MAX_CHARS) {
                "${item.text.take(ITEM_MAX_CHARS)}..."
            } else {
                item.text
            }
            
            text = displayText
            textSize = 14f
            setTextColor(Color.parseColor("#212121"))
            typeface = Typeface.DEFAULT
            setSingleLine(true)
            
            if (item.isOTP()) {
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#E65100"))
            }
        }
        container.addView(textView)
        
        return container
    }
    
    private fun applyTheme() {
        themeManager?.let { theme ->
            try {
                val palette = theme.getCurrentPalette()
                setBackgroundColor(palette.keyboardBg)
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }
    }
    
    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}

// ============================================================================
// MAIN COORDINATOR: UnifiedClipboardManager + MethodChannel
// ============================================================================

/**
 * 🎯 MAIN CLIPBOARD MANAGER - Single entry point for all clipboard operations
 * Coordinates all clipboard components and handles Flutter communication
 */
class UnifiedClipboardManager(
    private val context: Context,
    private val themeManager: ThemeManager
) : MethodChannel.MethodCallHandler {
    
    companion object {
        private const val TAG = "UnifiedClipboardMgr"
        const val CHANNEL_NAME = "ai_keyboard/clipboard"
    }
    
    // Core components
    private val historyManager: ClipboardHistoryManager = ClipboardHistoryManager(context)
    private val clipboardPanel: ClipboardPanel = ClipboardPanel(context, themeManager)
    private var clipboardStripView: ClipboardStripView? = null
    
    // Settings (synced with Flutter)
    private var clipboardEnabled = true
    private var clipboardSuggestionEnabled = true
    private var clearPrimaryClipAffects = true
    private var internalClipboard = true
    private var syncFromSystem = true
    private var syncToFivive = true
    
    // Callbacks
    private var onHistoryUpdated: ((List<ClipboardItem>) -> Unit)? = null
    private var onNewItem: ((ClipboardItem) -> Unit)? = null
    private var onItemSelectedCallback: ((ClipboardItem) -> Unit)? = null
    
    // MethodChannel for Flutter communication
    private var methodChannel: MethodChannel? = null
    
    /**
     * Get the underlying ClipboardHistoryManager (for SuggestionsPipeline)
     */
    fun getClipboardHistoryManager(): ClipboardHistoryManager = historyManager
    
    /**
     * Initialize the unified clipboard manager
     */
    fun initialize() {
        Log.d(TAG, "🚀 Initializing UnifiedClipboardManager")
        
        historyManager.initialize()
        
        historyManager.addListener(object : ClipboardHistoryManager.ClipboardHistoryListener {
            override fun onHistoryUpdated(items: List<ClipboardItem>) {
                onHistoryUpdated?.invoke(items)
                updateStripView()
                notifyFlutterHistoryChanged(items)
            }
            
            override fun onNewClipboardItem(item: ClipboardItem) {
                onNewItem?.invoke(item)
                updateStripView()
                notifyFlutterNewItem(item)
            }
        })
        
        clipboardPanel.setCallbacks(
            onItemSelected = { item ->
                Log.d(TAG, "Clipboard item selected: ${item.getPreview()}")
                onItemSelected(item)
            },
            onItemPinToggled = { item ->
                togglePin(item.id)
            },
            onItemDeleted = { item ->
                deleteItem(item.id)
            }
        )
        
        loadSettings()
        Log.d(TAG, "✅ UnifiedClipboardManager initialized")
    }
    
    /**
     * Set up MethodChannel for Flutter communication
     */
    fun setupMethodChannel(channel: MethodChannel) {
        this.methodChannel = channel
        channel.setMethodCallHandler(this)
        Log.d(TAG, "✅ MethodChannel set up for clipboard communication")
    }
    
    /**
     * Handle MethodChannel calls from Flutter
     */
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "getHistory" -> {
                    val maxItems = call.argument<Int>("maxItems") ?: 20
                    val items = historyManager.getHistoryForUI(maxItems)
                    val itemsJson = items.map { it.toJson().toString() }
                    result.success(itemsJson)
                }
                
                "togglePin" -> {
                    val itemId = call.argument<String>("itemId")
                    if (itemId != null) {
                        val pinned = togglePin(itemId)
                        result.success(pinned)
                    } else {
                        result.error("INVALID_ARGUMENT", "Item ID cannot be null", null)
                    }
                }
                
                "deleteItem" -> {
                    val itemId = call.argument<String>("itemId")
                    if (itemId != null) {
                        val deleted = deleteItem(itemId)
                        result.success(deleted)
                    } else {
                        result.error("INVALID_ARGUMENT", "Item ID cannot be null", null)
                    }
                }
                
                "clearAll" -> {
                    clearNonPinnedItems()
                    result.success(true)
                }
                
                "updateSettings" -> {
                    val settings = call.arguments as? Map<String, Any>
                    if (settings != null) {
                        updateSettings(settings)
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGUMENT", "Settings cannot be null", null)
                    }
                }
                
                "getSettings" -> {
                    val settings = getSettings()
                    result.success(settings)
                }
                
                "syncFromSystem" -> {
                    historyManager.syncFromSystemClipboard()
                    result.success(true)
                }
                
                "syncToCloud" -> {
                    historyManager.syncToCloud()
                    result.success(true)
                }
                
                "syncFromCloud" -> {
                    historyManager.syncFromCloud()
                    result.success(true)
                }
                
                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling MethodChannel call: ${call.method}", e)
            result.error("ERROR", e.message, null)
        }
    }
    
    fun showPanel(anchorView: View) {
        if (!clipboardEnabled) {
            Log.w(TAG, "Clipboard is disabled")
            return
        }
        
        val items = historyManager.getHistoryForUI(8)
        clipboardPanel.show(anchorView, items)
        Log.d(TAG, "📋 Clipboard panel shown with ${items.size} items")
    }
    
    fun dismissPanel() {
        clipboardPanel.dismiss()
    }
    
    fun setStripView(stripView: ClipboardStripView) {
        this.clipboardStripView = stripView
        stripView.setThemeManager(themeManager)
        
        stripView.setOnItemClickListener { item ->
            onItemSelected(item)
        }
        
        stripView.setOnItemLongClickListener { item ->
            togglePin(item.id)
        }
        
        updateStripView()
        Log.d(TAG, "✅ Clipboard strip view set up")
    }
    
    private fun updateStripView() {
        clipboardStripView?.let { strip ->
            val items = historyManager.getHistoryForUI(5)
            strip.updateItems(items)
        }
    }
    
    fun getAllItems(): List<ClipboardItem> = historyManager.getAllItems()
    
    fun getHistoryForUI(maxItems: Int): List<ClipboardItem> = historyManager.getHistoryForUI(maxItems)
    
    fun getOTPItems(): List<ClipboardItem> = historyManager.getOTPItems()
    
    fun getMostRecentItem(): ClipboardItem? = historyManager.getMostRecentItem()
    
    fun togglePin(itemId: String): Boolean {
        val pinned = historyManager.togglePin(itemId)
        Log.d(TAG, "Toggled pin for item $itemId -> $pinned")
        return pinned
    }
    
    fun deleteItem(itemId: String): Boolean {
        val deleted = historyManager.deleteItem(itemId)
        if (deleted) {
            Log.d(TAG, "Deleted clipboard item: $itemId")
        }
        return deleted
    }
    
    fun clearNonPinnedItems(): Boolean {
        val toRemove = historyManager.getHistoryItems().filter { !it.isPinned && !it.isTemplate }
        if (toRemove.isEmpty()) {
            Log.d(TAG, "No clipboard items to clear")
            return false
        }
        toRemove.forEach { historyManager.deleteItem(it.id) }
        Log.d(TAG, "Cleared ${toRemove.size} non-pinned clipboard items")
        return true
    }
    
    private fun onItemSelected(item: ClipboardItem) {
        onItemSelectedCallback?.invoke(item)
        dismissPanel()
    }
    
    fun setOnItemSelectedCallback(callback: (ClipboardItem) -> Unit) {
        this.onItemSelectedCallback = callback
    }
    
    fun setOnHistoryUpdatedCallback(callback: (List<ClipboardItem>) -> Unit) {
        this.onHistoryUpdated = callback
    }
    
    fun setOnNewItemCallback(callback: (ClipboardItem) -> Unit) {
        this.onNewItem = callback
    }
    
    private fun loadSettings() {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        
        clipboardEnabled = prefs.getBoolean("flutter.clipboard_history", true)
        clearPrimaryClipAffects = prefs.getBoolean("flutter.clear_primary_clip_affects", true)
        internalClipboard = prefs.getBoolean("flutter.internal_clipboard", true)
        syncFromSystem = prefs.getBoolean("flutter.sync_from_system", true)
        syncToFivive = prefs.getBoolean("flutter.sync_to_fivive", true)
        clipboardSuggestionEnabled = prefs.getBoolean("flutter.clipboard_suggestion_enabled", true)
        
        val cleanOldHistoryMinutes = prefs.getFloatCompat("flutter.clean_old_history_minutes", 0f).toLong()
        val historySize = prefs.getFloatCompat("flutter.history_size", 20f).toInt()
        
        historyManager.updateSettings(
            maxHistorySize = historySize,
            autoExpiryEnabled = cleanOldHistoryMinutes > 0,
            expiryDurationMinutes = if (cleanOldHistoryMinutes > 0) cleanOldHistoryMinutes else 60L
        )
        
        Log.d(TAG, "✅ Settings loaded: enabled=$clipboardEnabled, historySize=$historySize, expiry=${cleanOldHistoryMinutes}min")
    }
    
    private fun updateSettings(settings: Map<String, Any>) {
        settings["clipboard_history"]?.let {
            clipboardEnabled = it as? Boolean ?: clipboardEnabled
        }
        settings["clear_primary_clip_affects"]?.let {
            clearPrimaryClipAffects = it as? Boolean ?: clearPrimaryClipAffects
        }
        settings["internal_clipboard"]?.let {
            internalClipboard = it as? Boolean ?: internalClipboard
        }
        settings["sync_from_system"]?.let {
            syncFromSystem = it as? Boolean ?: syncFromSystem
        }
        settings["sync_to_fivive"]?.let {
            syncToFivive = it as? Boolean ?: syncToFivive
        }
        settings["clipboard_suggestion_enabled"]?.let {
            clipboardSuggestionEnabled = it as? Boolean ?: clipboardSuggestionEnabled
        }
        
        val maxHistorySize = (settings["history_size"] as? Number)?.toInt() ?: 20
        val cleanOldHistoryMinutes = (settings["clean_old_history_minutes"] as? Number)?.toLong() ?: 0L
        
        historyManager.updateSettings(
            maxHistorySize = maxHistorySize,
            autoExpiryEnabled = cleanOldHistoryMinutes > 0,
            expiryDurationMinutes = if (cleanOldHistoryMinutes > 0) cleanOldHistoryMinutes else 60L
        )
        
        // Save to SharedPreferences
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("flutter.clipboard_history", clipboardEnabled)
            putBoolean("flutter.clear_primary_clip_affects", clearPrimaryClipAffects)
            putBoolean("flutter.internal_clipboard", internalClipboard)
            putBoolean("flutter.sync_from_system", syncFromSystem)
            putBoolean("flutter.sync_to_fivive", syncToFivive)
            putBoolean("flutter.clipboard_suggestion_enabled", clipboardSuggestionEnabled)
            putString("flutter.clean_old_history_minutes", cleanOldHistoryMinutes.toString())
            putString("flutter.history_size", maxHistorySize.toString())
        }.apply()
        
        Log.d(TAG, "✅ Settings updated and synced: $settings")
    }
    
    private fun getSettings(): Map<String, Any> {
        return mapOf(
            "clipboard_history" to clipboardEnabled,
            "clear_primary_clip_affects" to clearPrimaryClipAffects,
            "internal_clipboard" to internalClipboard,
            "sync_from_system" to syncFromSystem,
            "sync_to_fivive" to syncToFivive,
            "clipboard_suggestion_enabled" to clipboardSuggestionEnabled
        )
    }
    
    fun reloadSettings() {
        loadSettings()
        Log.d(TAG, "✅ Settings reloaded from SharedPreferences")
    }
    
    fun isEnabled(): Boolean = clipboardEnabled
    
    fun isSuggestionEnabled(): Boolean = clipboardSuggestionEnabled
    
    private fun notifyFlutterHistoryChanged(items: List<ClipboardItem>) {
        try {
            methodChannel?.invokeMethod("onHistoryChanged", items.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying Flutter of history change", e)
        }
    }
    
    private fun notifyFlutterNewItem(item: ClipboardItem) {
        try {
            val itemData = mapOf(
                "text" to item.text,
                "isOTP" to item.isOTP(),
                "timestamp" to item.timestamp
            )
            methodChannel?.invokeMethod("onNewItem", itemData)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying Flutter of new item", e)
        }
    }
    
    fun cleanup() {
        try {
            historyManager.cleanup()
            clipboardPanel.dismiss()
            clipboardStripView?.clear()
            methodChannel?.setMethodCallHandler(null)
            methodChannel = null
            Log.d(TAG, "✅ UnifiedClipboardManager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
