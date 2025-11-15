package com.kvive.keyboard.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.CallSuper
import com.kvive.keyboard.utils.LogUtil

/**
 * Base class for all manager classes in AI Keyboard
 * Provides common functionality for preferences, logging, and initialization
 */
abstract class BaseManager(protected val context: Context) {
    
    /**
     * Lazy-initialized SharedPreferences for this manager
     */
    protected val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(getPreferencesName(), Context.MODE_PRIVATE)
    }
    
    /**
     * Get the SharedPreferences file name for this manager
     * @return Preferences file name
     */
    protected abstract fun getPreferencesName(): String
    
    /**
     * Initialize the manager
     * Override this method to perform setup operations
     */
    @CallSuper
    open fun initialize() {
        logD("Initialized")
    }
    
    /**
     * Log debug message using class name as tag
     * @param message Message to log
     */
    protected fun logD(message: String) {
        LogUtil.d(javaClass.simpleName, message)
    }
    
    /**
     * Log error message using class name as tag
     * @param message Error message
     * @param throwable Optional exception
     */
    protected fun logE(message: String, throwable: Throwable? = null) {
        LogUtil.e(javaClass.simpleName, message, throwable)
    }
    
    /**
     * Log warning message using class name as tag
     * @param message Warning message
     */
    protected fun logW(message: String) {
        LogUtil.w(javaClass.simpleName, message)
    }
    
    /**
     * Log info message using class name as tag
     * @param message Info message
     */
    protected fun logI(message: String) {
        LogUtil.i(javaClass.simpleName, message)
    }
}

