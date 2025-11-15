package com.kvive.keyboard.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Centralized broadcast management for AI Keyboard
 * Consolidates all broadcast sending logic to keyboard service
 */
object BroadcastManager {
    /**
     * Send broadcast to keyboard service
     * @param context Application context
     * @param action Broadcast action string
     * @param extras Optional bundle with extra data
     */
    fun sendToKeyboard(context: Context, action: String, extras: Bundle? = null) {
        try {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                extras?.let { putExtras(it) }
            }
            context.sendBroadcast(intent)
            LogUtil.d("BroadcastManager", "Broadcast sent: $action")
        } catch (e: Exception) {
            LogUtil.e("BroadcastManager", "Failed to send broadcast: $action", e)
        }
    }
}

