package com.kvive.keyboard.utils

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.kvive.keyboard.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Startup profiler to measure initialization performance
 * Helps identify bottlenecks during keyboard startup
 */
object StartupProfiler {
    private const val TAG = "StartupProfiler"
    
    private val startTime = SystemClock.elapsedRealtime()
    private val measurements = ConcurrentHashMap<String, Long>()
    private val milestones = mutableListOf<Pair<String, Long>>()
    private var isEnabled = try { BuildConfig.ENABLE_PROFILING } catch (e: Exception) { false }
    
    /**
     * Mark the start of an operation
     */
    fun startOperation(name: String) {
        if (!isEnabled) return
        measurements[name] = SystemClock.elapsedRealtime()
    }
    
    /**
     * Mark the end of an operation and log duration
     */
    fun endOperation(name: String) {
        if (!isEnabled) return
        
        val startTime = measurements.remove(name) ?: return
        val duration = SystemClock.elapsedRealtime() - startTime
        
        Log.d(TAG, "⏱️ $name: ${duration}ms")
        
        // Add to milestones for summary
        synchronized(milestones) {
            milestones.add(name to duration)
        }
    }
    
    /**
     * Log a milestone with time since startup
     */
    fun milestone(name: String) {
        if (!isEnabled) return
        
        val elapsed = SystemClock.elapsedRealtime() - startTime
        Log.d(TAG, "📍 $name at ${elapsed}ms since startup")
    }
    
    /**
     * Print a summary of all measurements
     */
    fun printSummary() {
        if (!isEnabled) return
        
        val totalTime = SystemClock.elapsedRealtime() - startTime
        
        Log.d(TAG, "")
        Log.d(TAG, "==================== STARTUP PROFILING SUMMARY ====================")
        Log.d(TAG, "Total startup time: ${totalTime}ms")
        Log.d(TAG, "")
        
        synchronized(milestones) {
            milestones.sortedByDescending { it.second }.forEachIndexed { index, (name, duration) ->
                val percentage = (duration.toFloat() / totalTime * 100).toInt()
                Log.d(TAG, "${index + 1}. $name: ${duration}ms ($percentage%)")
            }
        }
        
        Log.d(TAG, "===================================================================")
        Log.d(TAG, "")
    }
    
    /**
     * Reset all measurements (useful for testing)
     */
    fun reset() {
        measurements.clear()
        synchronized(milestones) {
            milestones.clear()
        }
    }
    
    /**
     * Enable or disable profiling
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}

