package com.kvive.keyboard.utils

import android.content.*
import android.os.Build

/**
 * BroadcastUtils - Android 13+ (API 33+) safe broadcast receiver registration
 * 
 * Handles the RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED flag requirement
 * introduced in Android 13 to prevent SecurityException on receiver registration.
 */
object BroadcastUtils {
    /**
     * Safely register a broadcast receiver with proper flags for Android 13+
     * 
     * @param ctx Context to register receiver with
     * @param receiver BroadcastReceiver to register
     * @param filter IntentFilter defining what intents to receive
     * @return Intent from registerReceiver() or null
     */
    @JvmStatic
    fun safeRegisterReceiver(
        ctx: Context, 
        receiver: BroadcastReceiver, 
        filter: IntentFilter
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires explicit EXPORTED/NOT_EXPORTED flag
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Pre-Android 13 uses standard registration
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
    }
    
    /**
     * Safely register a broadcast receiver with permission check
     * 
     * @param ctx Context to register receiver with
     * @param receiver BroadcastReceiver to register
     * @param filter IntentFilter defining what intents to receive
     * @param broadcastPermission Permission required for sender
     * @param scheduler Handler for receiver callbacks
     * @return Intent from registerReceiver() or null
     */
    @JvmStatic
    fun safeRegisterReceiver(
        ctx: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: android.os.Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(
                receiver, 
                filter, 
                broadcastPermission, 
                scheduler,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }
}

