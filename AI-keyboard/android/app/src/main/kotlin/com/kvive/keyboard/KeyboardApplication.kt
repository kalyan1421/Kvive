package com.kvive.keyboard

import android.util.Log
import com.kvive.keyboard.utils.ProcessGuard
import io.flutter.app.FlutterApplication

/**
 * Custom application to isolate the IME process from Firebase/GMS work.
 * Only the keyboard process is blocked; the main Flutter app remains unchanged.
 */
class KeyboardApplication : FlutterApplication() {
    override fun onCreate() {
        super.onCreate()
        try {
            ProcessGuard.markProcess(this)
        } catch (e: Exception) {
            Log.w("KeyboardApplication", "Unable to enforce IME process guard", e)
        }
    }
}
