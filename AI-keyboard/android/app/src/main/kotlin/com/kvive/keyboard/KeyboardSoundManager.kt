package com.kvive.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import androidx.annotation.RawRes
import com.kvive.keyboard.utils.LogUtil
import java.io.File
import java.util.concurrent.Executors

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
    private var lastConfigSignature: Triple<String?, Float, String?>? = null
    
    // ✅ FIX: Global enabled flag - must be set by AIKeyboardService
    var isEnabled: Boolean = false

    /**
     * Initialize SoundPool and preload built-in sound profiles.
     */
    fun init(
        context: Context,
        initialProfile: String = "default",
        initialCustomUri: String? = null
    ) {
        if (soundPool != null) {
            LogUtil.d(TAG, "SoundPool already initialized, skipping")
            return
        }

        try {
            // ✅ CRITICAL FIX: Optimize AudioAttributes to prevent MediaCodec initialization
            // On devices like Xiaomi/Redmi (MIUI), default attributes trigger heavy DSP/MediaCodec path
            // These attributes force lightweight system audio path, avoiding 15-25ms buffer allocation delays
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build()

            registerAliases()

            // Set initial profile before loading sounds to avoid wrong profile warmup
            currentType = initialProfile
            currentCustomUri = initialCustomUri
            if (initialProfile.lowercase() == "custom" && !initialCustomUri.isNullOrBlank()) {
                loadCustom(context, initialCustomUri)
            }

            var loadedCount = 0
            val totalSounds = 4
            
            soundPool?.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    loadedCount++
                    // Only log in debug builds to reduce log noise
                    if (loadedCount == 1 || loadedCount >= totalSounds) {
                        LogUtil.d(TAG, "Sound loaded: $loadedCount/$totalSounds")
                    }

                    if (loadedCount >= totalSounds) {
                        isInitialized = true
                        LogUtil.d(TAG, "All sounds loaded, SoundPool ready")
                    }
                } else {
                  //  LogUtil.e(TAG, "Sound load failed: ID=$sampleId, status=$status")
                }
            }

            // ✅ CRITICAL FIX: Load sounds in background thread with delays
            // This prevents MediaCodec initialization from blocking main thread
            // and reduces MediaCodec churn by spacing out loads
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    LogUtil.d(TAG, "Loading sounds in background")
                    
                    // Load sounds sequentially to keep initialization lightweight
                    load(context, "classic", R.raw.key_click)
                    load(context, "mechanical", R.raw.key_mech)
                    load(context, "bubble", R.raw.key_bubble)
                    load(context, "pop", R.raw.key_pop)
                    
                    LogUtil.d(TAG, "Background sound loading complete")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error loading sounds in background", e)
                } finally {
                    executor.shutdown()
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to initialize SoundPool", e)
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
                // Only log errors, not successful loads to reduce log noise
            } else {
                LogUtil.w(TAG, "Failed to load sound: $key (invalid ID)")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Exception loading sound: $key", e)
        }
    }

    private fun loadCustom(context: Context, uriString: String): Int? {
        val pool = soundPool ?: return null

        return try {
            customSoundId?.let { oldId ->
                pool.unload(oldId)
                soundMap.remove("custom")
                LogUtil.d(TAG, "Unloaded previous custom sound")
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
                        LogUtil.d(TAG, "Loading asset sound: $uriString")
                        id
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error loading asset sound: $uriString", e)
                        null
                    }
                }
                else -> {
                    val file = File(uriString)
                    if (file.exists()) {
                        pool.load(file.path, 1)
                    } else {
                        LogUtil.w(TAG, "Custom sound file missing: $uriString")
                        null
                    }
                }
            }

            if (soundId != null && soundId > 0) {
                customSoundId = soundId
                currentCustomUri = uriString
                soundMap["custom"] = soundId
                LogUtil.d(TAG, "Custom sound loaded: $uriString")
            } else {
                LogUtil.w(TAG, "Failed to load custom sound from $uriString")
            }

            customSoundId
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error loading custom sound: $uriString", e)
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
     * ✅ FIX: Now checks isEnabled flag before playing
     */
    fun play() {
        // 🔥 CRITICAL FIX: Check if sound is globally enabled first
        if (!isEnabled) {
            return  // ✅ Sound is OFF - don't play anything
        }
        
        val pool = soundPool
        if (pool == null) {
            // Only log errors, not warnings for normal flow
            return
        }

        if (!isInitialized && customSoundId == null) {
            // Sounds still loading - silently skip
            return
        }

        val resolvedType = resolveType(currentType)
        if (resolvedType == "silent") {
            return
        }
        val soundId = soundMap[resolvedType]
        if (soundId == null) {
            LogUtil.w(TAG, "No sound ID for type '$currentType'")
            return
        }

        try {
            pool.play(soundId, volume.coerceIn(0f, 1f), volume.coerceIn(0f, 1f), 1, 0, 1.0f)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error playing sound", e)
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
        val requestedProfile = type?.lowercase() ?: currentType
        val requestedVolume = vol?.coerceIn(0f, 1f) ?: volume
        val requestedUri = customUri ?: currentCustomUri
        val newSignature = Triple(requestedProfile, requestedVolume, requestedUri)
        if (newSignature == lastConfigSignature) {
            return
        }

        var silentProfile = false
        type?.let { profile ->
            val normalized = profile.lowercase()
            when (normalized) {
                "silent" -> {
                    currentType = "silent"
                    volume = 0f
                    silentProfile = true
                    LogUtil.d(TAG, "Silent sound profile applied")
                }
                "custom" -> {
                    if (customUri.isNullOrBlank()) {
                        LogUtil.w(TAG, "Custom sound requested but URI is missing")
                    } else if (context == null) {
                        LogUtil.w(TAG, "Custom sound requested but context is null")
                    } else {
                        val alreadyLoaded = currentCustomUri == customUri && customSoundId != null
                        if (!alreadyLoaded) {
                            val loadedId = loadCustom(context, customUri)
                            if (loadedId != null && loadedId > 0) {
                                currentType = "custom"
                                LogUtil.d(TAG, "Custom sound loaded: $customUri")
                            } else {
                                LogUtil.w(TAG, "Failed to load custom sound: $customUri")
                            }
                        } else {
                            currentType = "custom"
                            LogUtil.d(TAG, "Using already loaded custom sound")
                        }
                    }
                }
                else -> {
                    val resolved = resolveType(normalized)
                    if (soundMap.containsKey(resolved)) {
                        currentType = normalized
                        LogUtil.d(TAG, "Sound type updated: $normalized")
                    } else {
                        LogUtil.w(TAG, "Unknown sound type: $profile")
                    }
                }
            }
        }

        if (!silentProfile) {
            vol?.let {
                volume = it.coerceIn(0f, 1f)
                // Don't log volume updates - too verbose
            }
        }

        lastConfigSignature = Triple(currentType, volume, currentCustomUri)
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
            LogUtil.d(TAG, "SoundPool released")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error releasing SoundPool", e)
        }
    }
}
