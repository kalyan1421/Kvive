package com.kvive.keyboard

import android.content.Context
import android.util.Log
import com.kvive.keyboard.stickers.StickerData
import com.kvive.keyboard.stickers.StickerPack
import com.kvive.keyboard.stickers.StickerServiceAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Lightweight facade that preserves the legacy StickerManager API while routing all
 * operations through the modern Firebase-backed StickerServiceAdapter/Repository stack.
 *
 * This manager keeps the rest of the keyboard codebase decoupled from repository details,
 * offers simple synchronous helpers (used by MediaCacheManager preloading), and exposes
 * async helpers for long–running work like pack installation or cache maintenance.
 */
class StickerManager(private val context: Context) {

    companion object {
        private const val TAG = "StickerManager"
    }

    private val serviceAdapter = StickerServiceAdapter(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getInstalledPacks(): List<StickerPack> = runBlocking {
        serviceAdapter.getInstalledPacks()
    }

    fun getAvailablePacks(): List<StickerPack> = runBlocking {
        serviceAdapter.getAvailablePacks()
    }

    fun installStickerPack(pack: StickerPack, progressCallback: (Int) -> Unit) {
        scope.launch {
            try {
                serviceAdapter.installStickerPack(pack, progressCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install sticker pack ${pack.id}", e)
            }
        }
    }

    fun getStickersFromPack(packId: String): List<StickerData> = runBlocking {
        serviceAdapter.getStickersFromPack(packId)
    }

    fun searchStickers(query: String): List<StickerData> = runBlocking {
        serviceAdapter.searchStickers(query)
    }

    fun getRecentStickers(limit: Int = 20): List<StickerData> = runBlocking {
        serviceAdapter.getRecentStickers(limit)
    }

    fun recordStickerUsage(stickerId: String) {
        scope.launch {
            serviceAdapter.recordStickerUsage(stickerId)
        }
    }

    fun getCacheSize(): Long {
        return serviceAdapter.getCacheStats().totalSize
    }

    fun clearCache() {
        scope.launch {
            serviceAdapter.clearAllStickerCache()
        }
    }

    fun onLowMemory() {
        serviceAdapter.onLowMemory()
    }

    fun preloadFrequentlyUsed() {
        scope.launch {
            serviceAdapter.preloadFrequentContent()
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
