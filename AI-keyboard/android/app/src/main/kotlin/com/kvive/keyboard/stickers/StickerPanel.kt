package com.kvive.keyboard.stickers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kvive.keyboard.ThemeManager
import com.kvive.keyboard.themes.KeyboardThemeV2
import com.kvive.keyboard.themes.ThemePaletteV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Firebase-backed sticker browser that powers both the emoji panel and the media tray.
 * Packs are fetched from Firebase Storage via StickerServiceAdapter/Repository, thumbnails
 * are rendered with Glide, and the entire panel stays in sync with the active keyboard theme.
 */
class StickerPanel(
    context: Context,
    private val themeManager: ThemeManager
) : LinearLayout(context), ThemeManager.ThemeChangeListener {

    companion object {
        private const val TAG = "StickerPanel"
        private const val PANEL_HEIGHT_DP = 280
        private const val STICKERS_PER_ROW = 6
        private const val STICKER_PRELOAD_COUNT = 12
    }

    private val stickerService = StickerServiceAdapter(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var palette: ThemePaletteV2 = themeManager.getCurrentPalette()
    private var currentPackId: String? = null
    private var packsLoaded = false

    private var onStickerSelected: ((StickerData, String) -> Unit)? = null
    private var onPackLoaded: ((String) -> Unit)? = null

    private lateinit var headerLayout: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var packRecyclerView: RecyclerView
    private lateinit var stickerRecyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorMessage: TextView

    private val packAdapter = StickerPackAdapter(
        paletteProvider = { palette },
        onPackSelected = { loadStickersForPack(it) }
    )

    private val stickerAdapter = StickerAdapter(
        paletteProvider = { palette },
        onStickerSelected = { handleStickerClick(it) }
    )

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            dpToPx(PANEL_HEIGHT_DP)
        )
        themeManager.addThemeChangeListener(this)
        setupLayout()
        applyTheme(palette)
        loadStickerPacks()
    }

    // region Public API -------------------------------------------------------------------------

    fun show(forceRefresh: Boolean = false) {
        if (!packsLoaded || forceRefresh) {
            loadStickerPacks(forceRefresh)
        }
    }

    fun setOnStickerSelectedListener(listener: (StickerData, String) -> Unit) {
        onStickerSelected = listener
    }

    fun setOnPackLoadedListener(listener: (String) -> Unit) {
        onPackLoaded = listener
    }

    fun onDestroy() {
        themeManager.removeThemeChangeListener(this)
        coroutineScope.cancel()
    }

    // endregion ---------------------------------------------------------------------------------

    // region Layout -----------------------------------------------------------------------------

    private fun setupLayout() {
        setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(10))
        headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
        }

        titleText = TextView(context).apply {
            text = "Stickers"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        refreshButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_rotate)
            background = createRoundedBackground(Color.TRANSPARENT)
            setOnClickListener { show(forceRefresh = true) }
        }

        headerLayout.addView(titleText)
        headerLayout.addView(refreshButton, LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)))
        addView(headerLayout)

        packRecyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(72))
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = packAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(4))
        }
        addView(packRecyclerView)

        val contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        stickerRecyclerView = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(context, STICKERS_PER_ROW)
            adapter = stickerAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            clipToPadding = false
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        }

        loadingIndicator = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.GONE
        }

        errorMessage = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "Unable to load stickers"
            visibility = View.GONE
        }

        contentContainer.addView(stickerRecyclerView)
        contentContainer.addView(loadingIndicator)
        contentContainer.addView(errorMessage)
        addView(contentContainer)
    }

    private fun applyTheme(newPalette: ThemePaletteV2) {
        palette = newPalette
        setBackgroundColor(palette.panelSurface)
        headerLayout.setBackgroundColor(palette.toolbarBg)
        titleText.setTextColor(palette.keyText)
        refreshButton.setColorFilter(palette.keyText)
        packRecyclerView.setBackgroundColor(ColorUtils.setAlphaComponent(palette.keyboardBg, 235))
        stickerRecyclerView.setBackgroundColor(Color.TRANSPARENT)
        errorMessage.setTextColor(palette.keyText)

        DrawableCompat.setTint(loadingIndicator.indeterminateDrawable, palette.specialAccent)
        packAdapter.notifyDataSetChanged()
        stickerAdapter.notifyDataSetChanged()
    }

    // endregion ---------------------------------------------------------------------------------

    // region Data loading -----------------------------------------------------------------------

    private fun loadStickerPacks(forceRefresh: Boolean = false) {
        showLoading(true)
        coroutineScope.launch {
            try {
                val packs = withContext(Dispatchers.IO) {
                    stickerService.getAvailablePacks(forceRefresh)
                }
                if (packs.isEmpty()) {
                    packsLoaded = false
                    showError("No sticker packs available yet")
                    return@launch
                }
                packsLoaded = true
                packAdapter.updatePacks(packs)

                val initialPack = packs.find { it.id == currentPackId } ?: packs.first()
                loadStickersForPack(initialPack)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sticker packs", e)
                packsLoaded = false
                showError("Unable to load stickers")
            }
        }
    }

    private fun loadStickersForPack(pack: StickerPack) {
        currentPackId = pack.id
        packAdapter.setSelectedPack(pack.id)
        showLoading(true)

        coroutineScope.launch {
            try {
                val stickers = withContext(Dispatchers.IO) {
                    stickerService.getStickersFromPack(pack.id)
                }
                stickerAdapter.updateStickers(stickers)
                preloadStickers(stickers)
                showLoading(false)
                onPackLoaded?.invoke(pack.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stickers for pack ${pack.id}", e)
                showError("Unable to load stickers")
            }
        }
    }

    private fun handleStickerClick(sticker: StickerData) {
        coroutineScope.launch {
            try {
                val localPath = withContext(Dispatchers.IO) {
                    stickerService.downloadStickerIfNeeded(sticker)
                } ?: sticker.imageUrl

                withContext(Dispatchers.IO) {
                    stickerService.recordStickerUsage(sticker.id)
                }

                onStickerSelected?.invoke(sticker, localPath)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling sticker click for ${sticker.id}", e)
                onStickerSelected?.invoke(sticker, sticker.imageUrl)
            }
        }
    }

    private fun preloadStickers(stickers: List<StickerData>) {
        stickers.take(STICKER_PRELOAD_COUNT).forEach { sticker ->
            Glide.with(this)
                .load(sticker.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        stickerRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
        errorMessage.visibility = View.GONE
    }

    private fun showError(message: String) {
        loadingIndicator.visibility = View.GONE
        stickerRecyclerView.visibility = View.GONE
        errorMessage.visibility = View.VISIBLE
        errorMessage.text = message
    }

    // endregion ---------------------------------------------------------------------------------

    override fun onThemeChanged(theme: KeyboardThemeV2, palette: ThemePaletteV2) {
        applyTheme(palette)
    }

    private fun createRoundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(color)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

// region Adapters ------------------------------------------------------------------------------

private class StickerPackAdapter(
    private val paletteProvider: () -> ThemePaletteV2,
    private val onPackSelected: (StickerPack) -> Unit
) : RecyclerView.Adapter<StickerPackAdapter.PackViewHolder>() {

    private var packs: List<StickerPack> = emptyList()
    private var selectedPackId: String? = null

    fun updatePacks(newPacks: List<StickerPack>) {
        packs = newPacks
        notifyDataSetChanged()
    }

    fun setSelectedPack(packId: String) {
        val oldSelection = selectedPackId
        selectedPackId = packId
        packs.forEachIndexed { index, pack ->
            if (pack.id == packId || pack.id == oldSelection) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(dpToPx(parent.context, 64), ViewGroup.LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(parent.context, 6), dpToPx(parent.context, 4), dpToPx(parent.context, 6), dpToPx(parent.context, 4))
        }

        val thumbnail = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(parent.context, 48), dpToPx(parent.context, 48))
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(parent.context, 12).toFloat()
            }
        }

        val label = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
        }

        container.addView(thumbnail)
        container.addView(label)
        return PackViewHolder(container, thumbnail, label, paletteProvider)
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
        val pack = packs[position]
        holder.bind(pack, pack.id == selectedPackId) { onPackSelected(pack) }
    }

    override fun getItemCount(): Int = packs.size

    class PackViewHolder(
        itemView: LinearLayout,
        private val imageView: ImageView,
        private val textView: TextView,
        private val paletteProvider: () -> ThemePaletteV2
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(pack: StickerPack, isSelected: Boolean, onClick: () -> Unit) {
            val palette = paletteProvider()
            textView.text = pack.name
            textView.setTextColor(
                if (isSelected) palette.specialAccent else palette.keyText
            )

            val bgColor = if (isSelected) {
                ColorUtils.setAlphaComponent(palette.specialAccent, 40)
            } else {
                Color.TRANSPARENT
            }
            itemView.background = GradientDrawable().apply {
                cornerRadius = dpToPx(itemView.context, 14).toFloat()
                setColor(bgColor)
            }

            Glide.with(imageView)
                .load(pack.thumbnailUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(imageView)

            itemView.setOnClickListener { onClick() }
        }
    }
}

private class StickerAdapter(
    private val paletteProvider: () -> ThemePaletteV2,
    private val onStickerSelected: (StickerData) -> Unit
) : RecyclerView.Adapter<StickerAdapter.StickerViewHolder>() {

    private var stickers: List<StickerData> = emptyList()

    fun updateStickers(newStickers: List<StickerData>) {
        stickers = newStickers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(dpToPx(parent.context, 54), dpToPx(parent.context, 54))
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        return StickerViewHolder(imageView, paletteProvider)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        holder.bind(stickers[position], onStickerSelected)
    }

    override fun getItemCount(): Int = stickers.size

    class StickerViewHolder(
        private val imageView: ImageView,
        private val paletteProvider: () -> ThemePaletteV2
    ) : RecyclerView.ViewHolder(imageView) {

        fun bind(sticker: StickerData, onClick: (StickerData) -> Unit) {
            val palette = paletteProvider()
            imageView.background = GradientDrawable().apply {
                cornerRadius = dpToPx(imageView.context, 12).toFloat()
                setColor(ColorUtils.setAlphaComponent(palette.keyboardBg, 180))
            }

            Glide.with(imageView)
                .load(sticker.imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(imageView)

            imageView.setOnClickListener { onClick(sticker) }
        }
    }
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}

// endregion ------------------------------------------------------------------------------------
