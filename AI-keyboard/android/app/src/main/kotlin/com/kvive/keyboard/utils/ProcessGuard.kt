package com.kvive.keyboard.utils

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Detects the dedicated IME process and blocks Firebase/GMS initialization there
 * to avoid GoogleApiManager/Phenotype/FCM retries inside the keyboard.
 */
object ProcessGuard {
    private const val TAG = "ImeProcessGuard"

    @Volatile
    private var imeProcess = false

    @Volatile
    private var firebaseBlocked = false

    fun markProcess(application: Application) {
        val processName = currentProcessName(application)
        imeProcess = processName.endsWith(":ime")

        if (imeProcess) {
            blockFirebaseInIme(application)
        } else {
            Log.d(TAG, "Running in app process ($processName) - Firebase allowed")
        }
    }

    private fun currentProcessName(context: Context): String {
        return try {
            Application.getProcessName().orEmpty()
        } catch (_: Throwable) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val pid = android.os.Process.myPid()
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
        }
    }

    private fun blockFirebaseInIme(context: Context) {
        if (firebaseBlocked) return

        firebaseBlocked = true
        Log.i(TAG, "IME process detected (${currentProcessName(context)}); blocking Firebase/GMS hooks")

        try {
            // Tear down any Firebase apps that FirebaseInitProvider created for this process.
            FirebaseApp.getApps(context).forEach { app ->
                try {
                    app.delete()
                    Log.d(TAG, "Deleted Firebase app '${app.name}' in IME process")
                } catch (deleteError: Exception) {
                    Log.w(TAG, "Failed to delete Firebase app '${app.name}'", deleteError)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase not present or already removed in IME process", e)
        }
    }

    fun isImeProcess(): Boolean = imeProcess

    fun isFirebaseBlocked(): Boolean = firebaseBlocked
}
