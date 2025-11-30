package com.kvive.keyboard

import android.graphics.*
import androidx.core.graphics.ColorUtils
import com.kvive.keyboard.themes.ThemePaletteV2
import kotlin.math.*
import kotlin.random.Random

/**
 * KeyEffectRenderer - Extracted particle effect rendering from UnifiedKeyboardView
 * 
 * ⚡ PERFORMANCE: Centralized effect rendering with early-exit optimizations
 * Handles all tap effects (stars, hearts, bubbles, leaves, snow, lightning, confetti, butterflies, rainbow)
 */
class KeyEffectRenderer {
    
    /**
     * Element representing a single particle in an overlay effect
     */
    data class OverlayElement(
        val dx: Float,
        val dy: Float,
        val size: Float,
        val rotation: Float,
        val color: Int? = null,
        val extra: FloatArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as OverlayElement
            return dx == other.dx && dy == other.dy && size == other.size && 
                   rotation == other.rotation && color == other.color && 
                   extra.contentEquals(other.extra)
        }
        
        override fun hashCode(): Int {
            var result = dx.hashCode()
            result = 31 * result + dy.hashCode()
            result = 31 * result + size.hashCode()
            result = 31 * result + rotation.hashCode()
            result = 31 * result + (color ?: 0)
            result = 31 * result + (extra?.contentHashCode() ?: 0)
            return result
        }
    }
    
    // Reusable paint for overlay drawing
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    
    // ========== OVERLAY GENERATION METHODS ==========
    
    /**
     * Build overlay state map for a key tap
     * @param palette Theme palette for colors
     * @param keyRect Rectangle bounds of the key
     * @param tapEffectsEnabled Whether tap effects are enabled
     * @return Map of effect name to list of overlay elements
     */
    fun buildOverlayState(
        palette: ThemePaletteV2,
        keyRect: RectF,
        tapEffectsEnabled: Boolean
    ): Map<String, List<OverlayElement>> {
        // ⚡ PERFORMANCE: Skip overlay generation entirely if no effects are enabled
        if (palette.globalEffects.isEmpty()) return emptyMap()
        if (!tapEffectsEnabled && palette.globalEffectsOpacity <= 0f) return emptyMap()
        
        val overlays = mutableMapOf<String, List<OverlayElement>>()
        
        palette.globalEffects.forEach { effect ->
            when (effect.lowercase()) {
                "stars" -> {
                    val elements = generateStarOverlay(keyRect, sparkle = false)
                    if (elements.isNotEmpty()) overlays["stars"] = elements
                }
                "sparkles" -> {
                    val elements = generateStarOverlay(keyRect, sparkle = true)
                    if (elements.isNotEmpty()) overlays["sparkles"] = elements
                }
                "hearts" -> {
                    val elements = generateHeartOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["hearts"] = elements
                }
                "bubbles" -> {
                    val elements = generateBubbleOverlay(keyRect, palette)
                    if (elements.isNotEmpty()) overlays["bubbles"] = elements
                }
                "leaves" -> {
                    val elements = generateLeafOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["leaves"] = elements
                }
                "snow" -> {
                    val elements = generateSnowOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["snow"] = elements
                }
                "lightning" -> {
                    val elements = generateLightningOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["lightning"] = elements
                }
                "confetti" -> {
                    val elements = generateConfettiOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["confetti"] = elements
                }
                "butterflies" -> {
                    val elements = generateButterflyOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["butterflies"] = elements
                }
                "rainbow" -> {
                    val elements = generateRainbowOverlay(keyRect)
                    if (elements.isNotEmpty()) overlays["rainbow"] = elements
                }
            }
        }
        
        return overlays
    }
    
    fun generateStarOverlay(keyRect: RectF, sparkle: Boolean): List<OverlayElement> {
        val count = if (sparkle) 11 else 8
        val spread = max(keyRect.width(), keyRect.height())
        val minSize = min(keyRect.width(), keyRect.height()) * (if (sparkle) 0.12f else 0.18f)
        val maxSize = min(keyRect.width(), keyRect.height()) * (if (sparkle) 0.22f else 0.28f)
        val radiusRange = spread * (if (sparkle) 0.65f else 0.85f)
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * radiusRange
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minSize + Random.nextFloat() * (maxSize - minSize)
            val rotation = Random.nextFloat() * 360f
            OverlayElement(dx, dy, size, rotation)
        }
    }
    
    fun generateHeartOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 9
        val spread = max(keyRect.width(), keyRect.height())
        val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
        val maxSize = min(keyRect.width(), keyRect.height()) * 0.3f
        val radiusRange = spread * 0.75f
        val palette = intArrayOf(
            Color.parseColor("#FF7AAE"),
            Color.parseColor("#FF4F93"),
            Color.parseColor("#FF9FC5")
        )
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * radiusRange
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minSize + Random.nextFloat() * (maxSize - minSize)
            val rotation = Random.nextFloat() * 30f - 15f
            val color = palette[Random.nextInt(palette.size)]
            OverlayElement(dx, dy, size, rotation, color)
        }
    }
    
    fun generateBubbleOverlay(keyRect: RectF, palette: ThemePaletteV2): List<OverlayElement> {
        val count = 9
        val spread = max(keyRect.width(), keyRect.height())
        val minRadius = min(keyRect.width(), keyRect.height()) * 0.18f
        val maxRadius = min(keyRect.width(), keyRect.height()) * 0.26f
        val radiusRange = spread * 0.9f
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * radiusRange
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minRadius + Random.nextFloat() * (maxRadius - minRadius)
            val rotation = 0f
            val accent = ColorUtils.setAlphaComponent(palette.specialAccent, 160 + Random.nextInt(60))
            OverlayElement(dx, dy, size, rotation, accent)
        }
    }
    
    fun generateLeafOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 9
        val spread = max(keyRect.width(), keyRect.height())
        val minSize = min(keyRect.width(), keyRect.height()) * 0.2f
        val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
        val radiusRange = spread * 0.85f
        val palette = intArrayOf(
            Color.parseColor("#4CAF50"),
            Color.parseColor("#81C784"),
            Color.parseColor("#66BB6A"),
            Color.parseColor("#A5D6A7")
        )
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * radiusRange
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minSize + Random.nextFloat() * (maxSize - minSize)
            val rotation = Random.nextFloat() * 360f
            val color = palette[Random.nextInt(palette.size)]
            OverlayElement(dx, dy, size, rotation, color)
        }
    }
    
    fun generateSnowOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 8
        val spread = max(keyRect.width(), keyRect.height())
        val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
        val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
        val radiusRange = spread * 0.8f
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * radiusRange
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minSize + Random.nextFloat() * (maxSize - minSize)
            OverlayElement(dx, dy, size, 0f, Color.WHITE)
        }
    }
    
    fun generateLightningOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 5
        val spread = max(keyRect.width(), keyRect.height()) * 0.5f
        val palette = intArrayOf(
            Color.parseColor("#FFD740"),
            Color.parseColor("#FFEA00"),
            Color.parseColor("#FFC400")
        )
        
        return List(count) {
            val dx = Random.nextFloat() * spread - spread / 2f
            val dy = Random.nextFloat() * spread - spread / 2f
            val size = min(keyRect.width(), keyRect.height()) * (0.25f + Random.nextFloat() * 0.15f)
            val rotation = Random.nextFloat() * 40f - 20f
            
            val segments = FloatArray(8).apply {
                this[0] = 0f
                this[1] = -size * 0.6f
                this[2] = size * (0.15f + Random.nextFloat() * 0.2f)
                this[3] = -size * 0.15f
                this[4] = -size * (0.2f + Random.nextFloat() * 0.1f)
                this[5] = size * (0.1f + Random.nextFloat() * 0.1f)
                this[6] = size * (0.18f + Random.nextFloat() * 0.15f)
                this[7] = size * 0.6f
            }
            
            OverlayElement(dx, dy, size, rotation, palette[Random.nextInt(palette.size)], segments)
        }
    }
    
    fun generateConfettiOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 14
        val spread = max(keyRect.width(), keyRect.height())
        val palette = intArrayOf(
            Color.parseColor("#FF6F61"),
            Color.parseColor("#F7B32B"),
            Color.parseColor("#4ECDC4"),
            Color.parseColor("#845EC2"),
            Color.parseColor("#FF9671")
        )
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * spread * 0.95f
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = min(keyRect.width(), keyRect.height()) * (0.18f + Random.nextFloat() * 0.1f)
            val rotation = Random.nextFloat() * 360f
            val color = palette[Random.nextInt(palette.size)]
            val width = size * (0.8f + Random.nextFloat() * 0.4f)
            val height = size * (0.35f + Random.nextFloat() * 0.25f)
            OverlayElement(dx, dy, size, rotation, color, floatArrayOf(width, height))
        }
    }
    
    fun generateButterflyOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 6
        val spread = max(keyRect.width(), keyRect.height()) * 0.9f
        val minSize = min(keyRect.width(), keyRect.height()) * 0.22f
        val maxSize = min(keyRect.width(), keyRect.height()) * 0.32f
        val palette = intArrayOf(
            Color.parseColor("#FFB6C1"),
            Color.parseColor("#FFCCBC"),
            Color.parseColor("#B39DDB"),
            Color.parseColor("#90CAF9")
        )
        
        return List(count) {
            val angle = Random.nextFloat() * (PI.toFloat() * 2f)
            val distance = Random.nextFloat() * spread
            val dx = (cos(angle.toDouble()) * distance).toFloat()
            val dy = (sin(angle.toDouble()) * distance).toFloat()
            val size = minSize + Random.nextFloat() * (maxSize - minSize)
            val rotation = Random.nextFloat() * 360f
            val color = palette[Random.nextInt(palette.size)]
            OverlayElement(dx, dy, size, rotation, color)
        }
    }
    
    fun generateRainbowOverlay(keyRect: RectF): List<OverlayElement> {
        val count = 3
        val spread = max(keyRect.width(), keyRect.height()) * 0.6f
        
        return List(count) {
            val dx = Random.nextFloat() * spread - spread / 2f
            val dy = Random.nextFloat() * spread / 1.2f - spread / 2.4f
            val size = min(keyRect.width(), keyRect.height()) * (0.35f + Random.nextFloat() * 0.1f)
            val rotation = Random.nextFloat() * 40f - 20f
            val thickness = size * (0.1f + Random.nextFloat() * 0.05f)
            OverlayElement(dx, dy, size, rotation, null, floatArrayOf(thickness))
        }
    }
    
    // ========== OVERLAY DRAWING METHODS ==========
    
    /**
     * Draw all overlay effects for a key tap
     * @param canvas Canvas to draw on
     * @param keyRect Key rectangle bounds
     * @param palette Theme palette for colors
     * @param overlays Map of effect name to elements
     * @param progress Animation progress (0 to 1)
     */
    fun drawOverlayEffects(
        canvas: Canvas,
        keyRect: RectF,
        palette: ThemePaletteV2,
        overlays: Map<String, List<OverlayElement>>,
        progress: Float
    ) {
        // ⚡ PERFORMANCE: Early returns avoid expensive drawing operations
        if (overlays.isEmpty()) return
        val opacity = palette.globalEffectsOpacity.coerceIn(0f, 1f)
        if (opacity <= 0f) return
        
        overlays.forEach { (effect, elements) ->
            when (effect) {
                "stars" -> drawStarOverlay(canvas, keyRect, palette.specialAccent, elements, progress, sparkle = false, opacity = opacity)
                "sparkles" -> drawStarOverlay(canvas, keyRect, Color.WHITE, elements, progress, sparkle = true, opacity = opacity)
                "hearts" -> drawHeartOverlay(canvas, keyRect, elements, progress, opacity)
                "bubbles" -> drawBubbleOverlay(canvas, keyRect, elements, progress, opacity)
                "leaves" -> drawLeafOverlay(canvas, keyRect, elements, progress, opacity)
                "snow" -> drawSnowOverlay(canvas, keyRect, elements, progress, opacity)
                "lightning" -> drawLightningOverlay(canvas, keyRect, elements, progress, opacity)
                "confetti" -> drawConfettiOverlay(canvas, keyRect, elements, progress, opacity)
                "butterflies" -> drawButterflyOverlay(canvas, keyRect, elements, progress, opacity)
                "rainbow" -> drawRainbowOverlay(canvas, keyRect, elements, progress, opacity)
            }
        }
    }
    
    fun drawStarOverlay(
        canvas: Canvas,
        keyRect: RectF,
        baseColor: Int,
        elements: List<OverlayElement>,
        progress: Float,
        sparkle: Boolean,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val baseAlpha = if (sparkle) 170 else 210
        val alpha = (baseAlpha * (1f - progress)).toInt().coerceIn(0, baseAlpha)
        val scaledAlpha = (alpha * opacity).toInt().coerceIn(0, alpha)
        if (scaledAlpha <= 0) return
        
        elements.forEach { element ->
            val tint = ColorUtils.setAlphaComponent(element.color ?: baseColor, scaledAlpha)
            overlayPaint.color = tint
            canvas.save()
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            val size = element.size * (1f + progress * 0.25f)
            canvas.translate(cx, cy)
            canvas.rotate(element.rotation)
            canvas.drawPath(createStarPath(size, sparkle), overlayPaint)
            canvas.restore()
        }
    }
    
    fun drawHeartOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val alpha = (200 * (1f - progress)).toInt().coerceIn(0, 200)
        val scaledAlpha = (alpha * opacity).toInt().coerceIn(0, alpha)
        if (scaledAlpha <= 0) return
        
        elements.forEach { element ->
            val color = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FF7AAE"), scaledAlpha)
            overlayPaint.color = color
            canvas.save()
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            val size = element.size * (1f + progress * 0.2f)
            canvas.translate(cx, cy)
            canvas.rotate(element.rotation)
            canvas.drawPath(createHeartPath(size), overlayPaint)
            canvas.restore()
        }
    }
    
    fun drawBubbleOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        elements.forEach { element ->
            val baseAlpha = (150 * (1f - progress)).toInt().coerceIn(0, 170)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            val color = ColorUtils.setAlphaComponent(element.color ?: ColorUtils.setAlphaComponent(Color.WHITE, 200), alpha)
            overlayPaint.color = color
            val radius = element.size * (1f + progress * 0.18f)
            canvas.drawCircle(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy, radius, overlayPaint)
        }
    }
    
    fun drawLeafOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val path = Path()
        elements.forEach { element ->
            val baseColor = element.color ?: Color.parseColor("#4CAF50")
            val baseAlpha = (200 * (1f - progress)).toInt().coerceIn(0, 255)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.color = ColorUtils.setAlphaComponent(baseColor, alpha)
            canvas.save()
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            canvas.translate(cx, cy)
            canvas.rotate(element.rotation)
            val size = element.size * (1f + progress * 0.15f)
            path.reset()
            path.moveTo(0f, -size * 0.6f)
            path.quadTo(size * 0.55f, -size * 0.3f, size * 0.2f, size * 0.6f)
            path.quadTo(0f, size * 0.3f, -size * 0.2f, size * 0.6f)
            path.quadTo(-size * 0.55f, -size * 0.3f, 0f, -size * 0.6f)
            canvas.drawPath(path, overlayPaint)
            canvas.restore()
        }
    }
    
    fun drawSnowOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val strokePaint = Paint(overlayPaint).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        elements.forEach { element ->
            val baseColor = element.color ?: Color.WHITE
            val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 220)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.color = ColorUtils.setAlphaComponent(baseColor, alpha)
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            val radius = element.size * 0.35f * (1f + progress * 0.1f)
            canvas.drawCircle(cx, cy, radius, overlayPaint)
            
            strokePaint.color = overlayPaint.color
            strokePaint.strokeWidth = radius * 0.6f
            canvas.drawLine(cx - radius, cy, cx + radius, cy, strokePaint)
            canvas.drawLine(cx, cy - radius, cx, cy + radius, strokePaint)
            canvas.drawLine(cx - radius * 0.7f, cy - radius * 0.7f, cx + radius * 0.7f, cy + radius * 0.7f, strokePaint)
            canvas.drawLine(cx + radius * 0.7f, cy - radius * 0.7f, cx - radius * 0.7f, cy + radius * 0.7f, strokePaint)
        }
    }
    
    fun drawLightningOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val path = Path()
        overlayPaint.style = Paint.Style.STROKE
        overlayPaint.strokeCap = Paint.Cap.ROUND
        overlayPaint.strokeJoin = Paint.Join.ROUND
        
        elements.forEach { element ->
            val baseAlpha = (235 * (1f - progress)).toInt().coerceIn(0, 235)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            overlayPaint.color = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FFD740"), alpha)
            overlayPaint.strokeWidth = element.size * 0.18f
            
            val coords = element.extra ?: floatArrayOf(
                0f, -element.size * 0.6f,
                element.size * 0.25f, -element.size * 0.15f,
                -element.size * 0.15f, element.size * 0.1f,
                element.size * 0.2f, element.size * 0.55f
            )
            
            path.reset()
            path.moveTo(coords[0], coords[1])
            var idx = 2
            while (idx < coords.size) {
                path.lineTo(coords[idx], coords[idx + 1])
                idx += 2
            }
            
            canvas.save()
            canvas.translate(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy)
            canvas.rotate(element.rotation)
            canvas.drawPath(path, overlayPaint)
            canvas.restore()
        }
        overlayPaint.style = Paint.Style.FILL
    }
    
    fun drawConfettiOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        elements.forEach { element ->
            val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 220)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            val color = ColorUtils.setAlphaComponent(element.color ?: Color.WHITE, alpha)
            overlayPaint.style = Paint.Style.FILL
            overlayPaint.color = color
            
            val width = (element.extra?.getOrNull(0) ?: element.size * 0.4f) * (1f + progress * 0.15f)
            val height = (element.extra?.getOrNull(1) ?: element.size * 0.15f) * (1f + progress * 0.05f)
            
            canvas.save()
            canvas.translate(keyRect.centerX() + element.dx, keyRect.centerY() + element.dy)
            canvas.rotate(element.rotation)
            canvas.drawRoundRect(
                -width / 2f,
                -height / 2f,
                width / 2f,
                height / 2f,
                height / 2f,
                height / 2f,
                overlayPaint
            )
            canvas.restore()
        }
    }
    
    fun drawButterflyOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val wingPaint = Paint(overlayPaint)
        val bodyPaint = Paint(overlayPaint).apply {
            style = Paint.Style.FILL
            color = ColorUtils.setAlphaComponent(Color.DKGRAY, 180)
        }
        val wingPath = Path()
        
        elements.forEach { element ->
            val baseAlpha = (200 * (1f - progress)).toInt().coerceIn(0, 200)
            val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
            val wingColor = ColorUtils.setAlphaComponent(element.color ?: Color.parseColor("#FFB6C1"), alpha)
            wingPaint.style = Paint.Style.FILL
            wingPaint.color = wingColor
            
            canvas.save()
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            canvas.translate(cx, cy)
            canvas.rotate(element.rotation)
            val size = element.size * (1f + progress * 0.12f)
            
            // Left wing
            wingPath.reset()
            wingPath.moveTo(0f, 0f)
            wingPath.cubicTo(-size * 0.9f, -size * 0.2f, -size * 0.9f, -size, -size * 0.1f, -size * 0.9f)
            wingPath.cubicTo(-size * 0.8f, -size * 0.3f, -size * 0.8f, size * 0.3f, -size * 0.1f, size * 0.9f)
            wingPath.close()
            canvas.drawPath(wingPath, wingPaint)
            
            // Right wing
            wingPath.reset()
            wingPath.moveTo(0f, 0f)
            wingPath.cubicTo(size * 0.9f, -size * 0.2f, size * 0.9f, -size, size * 0.1f, -size * 0.9f)
            wingPath.cubicTo(size * 0.8f, -size * 0.3f, size * 0.8f, size * 0.3f, size * 0.1f, size * 0.9f)
            wingPath.close()
            canvas.drawPath(wingPath, wingPaint)
            
            // Body
            canvas.drawRoundRect(
                -size * 0.1f,
                -size * 0.8f,
                size * 0.1f,
                size * 0.8f,
                size * 0.1f,
                size * 0.1f,
                bodyPaint
            )
            
            canvas.restore()
        }
    }
    
    fun drawRainbowOverlay(
        canvas: Canvas,
        keyRect: RectF,
        elements: List<OverlayElement>,
        progress: Float,
        opacity: Float
    ) {
        if (elements.isEmpty()) return
        val colors = intArrayOf(
            Color.parseColor("#FF6F61"),
            Color.parseColor("#FDB045"),
            Color.parseColor("#F9ED69"),
            Color.parseColor("#9ADBCB"),
            Color.parseColor("#62B0E8"),
            Color.parseColor("#A685E2")
        )
        
        val strokePaint = Paint(overlayPaint).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        elements.forEach { element ->
            val cx = keyRect.centerX() + element.dx
            val cy = keyRect.centerY() + element.dy
            val baseRadius = element.size * (1f + progress * 0.1f)
            val thickness = (element.extra?.getOrNull(0) ?: element.size * 0.12f)
            
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(element.rotation)
            
            colors.forEachIndexed { index, color ->
                val radius = baseRadius - index * thickness
                if (radius <= 0f) return@forEachIndexed
                
                strokePaint.strokeWidth = thickness * 0.8f
                val baseAlpha = (210 * (1f - progress)).toInt().coerceIn(0, 210)
                val alpha = (baseAlpha * opacity).toInt().coerceIn(0, baseAlpha)
                strokePaint.color = ColorUtils.setAlphaComponent(color, alpha)
                val arcRect = RectF(-radius, -radius, radius, radius)
                canvas.drawArc(arcRect, 200f, 140f, false, strokePaint)
            }
            
            canvas.restore()
        }
    }
    
    // ========== PATH CREATION HELPERS ==========
    
    fun createStarPath(radius: Float, sparkle: Boolean): Path {
        val path = Path()
        val points = if (sparkle) 4 else 5
        val innerRadius = radius * if (sparkle) 0.42f else 0.5f
        val totalPoints = points * 2
        var angle = -PI.toFloat() / 2
        val step = PI.toFloat() / points
        path.moveTo(0f, -radius)
        for (i in 1 until totalPoints) {
            angle += step
            val currentRadius = if (i % 2 == 0) radius else innerRadius
            val x = (cos(angle.toDouble()) * currentRadius).toFloat()
            val y = (sin(angle.toDouble()) * currentRadius).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        return path
    }
    
    fun createHeartPath(size: Float): Path {
        val radius = size / 2f
        val left = RectF(-radius, -radius, 0f, 0f)
        val right = RectF(0f, -radius, radius, 0f)
        val path = Path()
        path.addArc(left, 180f, 180f)
        path.addArc(right, 180f, 180f)
        path.lineTo(0f, radius * 1.6f)
        path.close()
        return path
    }
}
