package com.kvive.keyboard.utils

import android.util.Log

/**
 * Centralized logging utility for AI Keyboard
 * ALL LOGS ENABLED - No conditions, all logs print directly
 */
object LogUtil {
    
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    fun e(tag: String, message: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.e(tag, message, tr)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun w(tag: String, message: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.w(tag, message, tr)
        } else {
            Log.w(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }
    
    fun v(tag: String, message: String) {
        Log.v(tag, message)
    }
}

