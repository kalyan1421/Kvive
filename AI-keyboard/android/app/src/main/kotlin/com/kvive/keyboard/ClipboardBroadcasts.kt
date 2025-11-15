package com.kvive.keyboard

/**
 * Shared clipboard broadcast actions so the keyboard service and Flutter host
 * stay in sync when history changes.
 */
object ClipboardBroadcasts {
    const val ACTION_CLIPBOARD_HISTORY_UPDATED = "com.kvive.keyboard.CLIPBOARD_HISTORY_UPDATED"
    const val ACTION_CLIPBOARD_NEW_ITEM = "com.kvive.keyboard.CLIPBOARD_NEW_ITEM"
}
