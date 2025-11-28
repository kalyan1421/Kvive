package com.kvive.keyboard

import android.os.StrictMode
import android.util.Log
import com.kvive.keyboard.utils.ProcessGuard
import com.kvive.keyboard.utils.StartupProfiler
import io.flutter.app.FlutterApplication

/**
 * Custom application to isolate the IME process from Firebase/GMS work.
 * Only the keyboard process is blocked; the main Flutter app remains unchanged.
 */
class KeyboardApplication : FlutterApplication() {
    override fun onCreate() {
        super.onCreate()
        
        // Enable StrictMode in profileable builds to detect performance issues
        if (BuildConfig.ENABLE_PROFILING) {
            enableStrictMode()
        }
        
        // Start startup profiling
        StartupProfiler.milestone("Application.onCreate() start")
        
        try {
            ProcessGuard.markProcess(this)
        } catch (e: Exception) {
            Log.w("KeyboardApplication", "Unable to enforce IME process guard", e)
        }
        
        StartupProfiler.milestone("Application.onCreate() end")
    }
    
    /**
     * Enable StrictMode to detect performance issues during development
     * DISABLED: StrictMode causes performance issues during startup
     */
    private fun enableStrictMode() {
        // ⚠️ DISABLED: StrictMode disk read violations are slowing down app startup
        // The violations are from Firebase/SharedPreferences which are necessary
        // Re-enable this only for specific performance profiling sessions
        
        if (false && BuildConfig.DEBUG && BuildConfig.ENABLE_PROFILING) {
            try {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .detectDiskWrites()
                        .detectNetwork()
                        .penaltyLog()
                        .build()
                )
                
                StrictMode.setVmPolicy(
                    StrictMode.VmPolicy.Builder()
                        .detectLeakedSqlLiteObjects()
                        .detectLeakedClosableObjects()
                        .penaltyLog()
                        .build()
                )
                
                Log.d("KeyboardApplication", "✅ StrictMode enabled for performance profiling")
            } catch (e: Exception) {
                Log.w("KeyboardApplication", "Failed to enable StrictMode", e)
            }
        }
    }
}
