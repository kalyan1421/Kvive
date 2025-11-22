package com.kvive.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.annotation.RawRes
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ✅ LIGHTWEIGHT SOUNDPOOL MANAGER - Singleton Pattern
 *
 * Replaces heavy MediaCodec audio pipeline with ultra-fast SoundPool.
 * Adds support for theme-aware sound packs and custom audio imports.
 *
 * Usage:
 *   KeyboardSoundManager.init(context)
 *   KeyboardSoundManager.update(type, volume, context, customUri)
 *   KeyboardSoundManager.play()
 *   KeyboardSoundManager.release()
 */
object KeyboardSoundManager {

    private const val TAG = "KeyboardSoundManager"
    private const val MAX_STREAMS = 8

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<String, Int>()
    private val aliasMap = mutableMapOf<String, String>()
    private var currentType: String = "default"
    private var volume: Float = 1.0f
    private var isInitialized = false
    private var customSoundId: Int? = null
    private var currentCustomUri: String? = null

    /**
     * Initialize SoundPool and preload built-in sound profiles.
     */
    fun init(context: Context) {
        if (soundPool != null) {
            Log.d(TAG, "SoundPool already initialized, skipping")
            return
        }

        try {
            // ✅ CRITICAL FIX: Optimize AudioAttributes to prevent MediaCodec initialization
            // On devices like Xiaomi/Redmi (MIUI), default attributes trigger heavy DSP/MediaCodec path
            // These attributes force lightweight system audio path, avoiding 15-25ms buffer allocation delays
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build()

            registerAliases()

            var loadedCount = 0
            val totalSounds = 4
            
            soundPool?.setOnLoadCompleteListener { pool, sampleId, status ->
                if (status == 0) {
                    loadedCount++
                    Log.d(TAG, "✅ Sound loaded successfully: ID=$sampleId ($loadedCount/$totalSounds)")
                    
                    // ✅ CRITICAL: Warm up audio system immediately after first sound loads
                    // This prevents MediaCodec initialization delays on first key press
                    // Play at volume 0 to warm up without audible sound
                    if (loadedCount == 1) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                pool.play(sampleId, 0f, 0f, 1, 0, 1.0f)
                                Log.d(TAG, "🔥 Audio system warmed up (silent play)")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Warm-up play failed", e)
                            }
                        }, 50)
                    }
                    
                    if (loadedCount >= totalSounds) {
                        isInitialized = true
                        Log.d(TAG, "✅ All sounds loaded, SoundPool ready")
                    }
                } else {
                    Log.e(TAG, "❌ Sound load failed: ID=$sampleId, status=$status")
                }
            }

            // ✅ CRITICAL FIX: Load sounds in background thread with delays
            // This prevents MediaCodec initialization from blocking main thread
            // and reduces MediaCodec churn by spacing out loads
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    Log.d(TAG, "✅ SoundPool initialized, loading ${totalSounds} base sounds in background")
                    
                    // Load sounds sequentially with small delays to reduce MediaCodec churn
                    load(context, "classic", R.raw.key_click)
                    Thread.sleep(50) // Small delay to let MediaCodec finish
                    
                    load(context, "mechanical", R.raw.key_mech)
                    Thread.sleep(50)
                    
                    load(context, "bubble", R.raw.key_bubble)
                    Thread.sleep(50)
                    
                    load(context, "pop", R.raw.key_pop)
                    
                    Log.d(TAG, "✅ Background sound loading complete")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error loading sounds in background", e)
                } finally {
                    executor.shutdown()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize SoundPool", e)
        }
    }

    private fun registerAliases() {
        aliasMap.clear()
        aliasMap["classic"] = "classic"
        aliasMap["clicky"] = "classic"
        aliasMap["mechanical"] = "mechanical"
        aliasMap["mech"] = "mechanical"
        aliasMap["typewriter"] = "mechanical"
        aliasMap["bubble"] = "bubble"
        aliasMap["soft"] = "bubble"
        aliasMap["piano"] = "bubble"
        aliasMap["default"] = "bubble"
        aliasMap["pop"] = "pop"
        aliasMap["custom"] = "custom"
        aliasMap["silent"] = "silent"
    }

    /**
     * Load a raw resource into the pool.
     */
    private fun load(context: Context, key: String, @RawRes resId: Int) {
        try {
            val id = soundPool?.load(context, resId, 1)
            if (id != null && id > 0) {
                soundMap[key.lowercase()] = id
                Log.d(TAG, "Loaded sound: ${key.lowercase()} → ID=$id")
            } else {
                Log.w(TAG, "⚠️ Failed to load sound: $key (invalid ID)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception loading sound: $key", e)
        }
    }

    private fun loadCustom(context: Context, uriString: String): Int? {
        val pool = soundPool ?: return null

        return try {
            customSoundId?.let { oldId ->
                pool.unload(oldId)
                soundMap.remove("custom")
                Log.d(TAG, "🔁 Unloaded previous custom sound")
            }

            val soundId = when {
                uriString.startsWith("content://") -> {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uriString), "r")?.use { afd ->
                        pool.load(afd, 1)
                    }
                }
                uriString.startsWith("sounds/") -> {
                    // Load from assets folder
                    try {
                        val afd = context.assets.openFd(uriString)
                        // SoundPool.load() reads the file descriptor immediately, so we can close it after
                        val id = pool.load(afd, 1)
                        afd.close()
                        Log.d(TAG, "✅ Loading asset sound: $uriString (ID=$id)")
                        id
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error loading asset sound: $uriString", e)
                        null
                    }
                }
                else -> {
                    val file = File(uriString)
                    if (file.exists()) {
                        pool.load(file.path, 1)
                    } else {
                        Log.w(TAG, "⚠️ Custom sound file missing: $uriString")
                        null
                    }
                }
            }

            if (soundId != null && soundId > 0) {
                customSoundId = soundId
                currentCustomUri = uriString
                soundMap["custom"] = soundId
                Log.d(TAG, "🎶 Custom sound loaded from $uriString (ID=$soundId)")
            } else {
                Log.w(TAG, "⚠️ Failed to load custom sound from $uriString")
            }

            customSoundId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading custom sound: $uriString", e)
            null
        }
    }

    private fun resolveType(type: String): String {
        val normalized = type.lowercase()
        return when {
            normalized == "custom" && customSoundId != null -> "custom"
            else -> aliasMap[normalized] ?: normalized
        }
    }

    /**
     * Play the currently selected sound with configured volume.
     */
    fun play() {
        val pool = soundPool
        if (pool == null) {
            Log.w(TAG, "⚠️ Cannot play: SoundPool not initialized (call init() first)")
            return
        }

        if (!isInitialized && customSoundId == null) {
            Log.w(TAG, "⚠️ Cannot play: Sounds still loading")
            return
        }

        val resolvedType = resolveType(currentType)
        if (resolvedType == "silent") {
            return
        }
        val soundId = soundMap[resolvedType]
        if (soundId == null) {
            Log.w(TAG, "⚠️ Cannot play: No sound ID for type '$currentType' (resolved=$resolvedType)")
            return
        }

        try {
            pool.play(soundId, volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f), 1, 0, 1.0f)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing sound", e)
        }
    }

    /**
     * Play a preview of a sound file from assets folder.
     * @param fileName The sound file name (e.g., "click.mp3")
     * @param context Context to access assets
     */
    fun playPreview(fileName: String, context: Context) {
        try {
            val assetPath = "sounds/$fileName"
            val afd = context.assets.openFd(assetPath)
            val previewPlayer = android.media.MediaPlayer()
            previewPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            previewPlayer.prepare()
            previewPlayer.setOnCompletionListener { mp ->
                mp.release()
            }
            previewPlayer.start()
            afd.close()
            Log.d(TAG, "🔊 Preview playing: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error playing preview: $fileName", e)
        }
    }

    /**
     * Update sound profile and volume without reinitializing the pool.
     *
     * @param type Sound profile identifier (supports aliases + "custom" + "silent")
     * @param vol Desired volume (0f-1f)
     * @param context Required when loading custom sounds
     * @param customUri Optional URI/path for custom sound
     */
    fun update(type: String?, vol: Float?, context: Context? = null, customUri: String? = null) {
        var silentProfile = false
        type?.let { profile ->
            val normalized = profile.lowercase()
            when (normalized) {
                "silent" -> {
                    currentType = "silent"
                    volume = 0f
                    silentProfile = true
                    Log.d(TAG, "🔇 Silent sound profile applied")
                }
                "custom" -> {
                    if (customUri.isNullOrBlank()) {
                        Log.w(TAG, "⚠️ Custom sound requested but URI is missing")
                    } else if (context == null) {
                        Log.w(TAG, "⚠️ Custom sound requested but context is null")
                    } else {
                        val alreadyLoaded = currentCustomUri == customUri && customSoundId != null
                        if (!alreadyLoaded) {
                            val loadedId = loadCustom(context, customUri)
                            if (loadedId != null && loadedId > 0) {
                                currentType = "custom"
                                Log.d(TAG, "🎧 Custom sound loaded and set: $customUri (ID=$loadedId)")
                            } else {
                                Log.w(TAG, "⚠️ Failed to load custom sound: $customUri")
                            }
                        } else {
                            currentType = "custom"
                            Log.d(TAG, "🎧 Using already loaded custom sound profile")
                        }
                    }
                }
                else -> {
                    val resolved = resolveType(normalized)
                    if (soundMap.containsKey(resolved)) {
                        currentType = normalized
                        Log.d(TAG, "🔊 Sound type updated: $normalized (resolved=$resolved)")
                    } else {
                        Log.w(TAG, "⚠️ Unknown sound type: $profile (keeping '$currentType')")
                    }
                }
            }
        }

        if (!silentProfile) {
            vol?.let {
                volume = it.coerceIn(0f, 1f)
                Log.d(TAG, "🔊 Volume updated: $volume")
            }
        }
    }

    /**
     * Release the SoundPool and free all resources.
     */
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            aliasMap.clear()
            customSoundId = null
            currentCustomUri = null
            isInitialized = false
            Log.d(TAG, "🔇 SoundPool released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SoundPool", e)
        }
    }
}
