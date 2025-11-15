package com.kvive.keyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import com.kvive.keyboard.utils.LogUtil
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kvive.keyboard.themes.PanelTheme
import com.kvive.keyboard.themes.ThemePaletteV2
import com.kvive.keyboard.stickers.StickerData
import com.kvive.keyboard.stickers.StickerPanel
import kotlin.math.abs

/**
 * Modern emoji panel controller matching Gboard/CleverType UX
 * - Fixed toolbar (never scrolls)
 * - Themable background matching keyboard
 * - Minimal footer with ABC, Space, Delete
 */
class EmojiPanelController(
    private val context: Context,
    private val themeManager: ThemeManager,
    private val onBackToLetters: () -> Unit,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onSearchModeChanged: (Boolean) -> Unit = {}
) {
    
    private enum class PanelMode { NORMAL, SEARCH }
    
    companion object {
        private const val TAG = "EmojiPanelController"
        private const val SPAN_COUNT = 8
        private const val DELETE_REPEAT_DELAY = 50L
        private val CATEGORY_ORDER = listOf(
            "Recent",
            "Smileys",
            "People",
            "Animals",
            "Food",
            "Activities",
            "Travel",
            "Objects",
            "Symbols",
            "Stickers",
            "Flags"
        )
        private val CATEGORY_DISPLAY_NAMES = mapOf(
            "Recent" to "Recently Used",
            "Smileys" to "Smiles & People",
            "People" to "People & Body",
            "Animals" to "Animals & Nature",
            "Food" to "Food & Drink",
            "Activities" to "Activities",
            "Travel" to "Travel & Places",
            "Objects" to "Objects",
            "Symbols" to "Symbols",
            "Stickers" to "Stickers",
            "Flags" to "Flags"
        )
        private val CATEGORY_ICONS = mapOf(
            "Recent" to "\u23F2\uFE0F", // 🕒 Timer clock
            "Smileys" to "\uD83D\uDE0A", // 😊 Smiling face
            "People" to "\uD83D\uDC6B", // 👫
            "Animals" to "\uD83D\uDC3B", // 🐻 Bear face
            "Food" to "\uD83C\uDF54", // 🍔 Hamburger
            "Activities" to "\u26BD\uFE0F", // ⚽️ Soccer ball
            "Travel" to "\uD83D\uDE97", // 🚗 Car
            "Objects" to "\uD83D\uDCA1", // 💡 Light bulb
            "Symbols" to "%&!", // %&! Symbols
            "Stickers" to "\uD83C\uDF81", // 🎁
            "Flags" to "\uD83D\uDEA9" // 🚩 Flag
        )
    }
    
    private var root: View? = null
    private var emojiGrid: RecyclerView? = null
    private var btnABC: TextView? = null
    private var btnDelete: ImageView? = null
    private var bottomBar: LinearLayout? = null
    private var emojiCategories: LinearLayout? = null
    private var categoryScrollView: HorizontalScrollView? = null
    private var searchBarContainer: LinearLayout? = null
    private var searchBarIcon: ImageView? = null
    private var searchBarText: TextView? = null
    private var categoryTitleView: TextView? = null
    private var searchOverlayContainer: LinearLayout? = null
    private var searchResultsRow: RecyclerView? = null
    private var searchInputField: TextView? = null
    private var searchKeyboardContainer: FrameLayout? = null
    private var keyboardHeightPx: Int = 0
    private var panelMode: PanelMode = PanelMode.NORMAL
    private var searchQuery: String = ""
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var deleteRepeatRunnable: Runnable? = null
    private var emojiGridSwipeHandled = false
    private var emojiGridInitialX = 0f
    private var emojiGridInitialY = 0f
    private var lastEmojiCategorySwipe = 0L
    private val scrollSnapRunnable = Runnable {
        smoothScrollToNearestCategory()
    }
    
    // Sticker integration
    private var stickerPanel: StickerPanel? = null
    private var panelSwitcher: FrameLayout? = null
    
    // Integrated emoji panel (reuse existing GboardEmojiPanel logic)
    // gboardEmojiPanel removed - using direct emoji management
    
    // Category selection state
    private var selectedCategoryView: TextView? = null
    private var currentCategory = "Recent"
    
    fun inflate(parent: ViewGroup): View {
        if (root != null) return root!!
        
        LogUtil.d(TAG, "Building emoji panel programmatically (100% dynamic, no XML)")
        
        EmojiRepository.ensureLoaded(context)

        val palette = PanelTheme.palette
        val keyboardHeight = getKeyboardHeight()
        keyboardHeightPx = keyboardHeight
        
        // Create root layout programmatically
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                keyboardHeight,
                Gravity.BOTTOM
            )
            background = buildPanelGradient(palette)
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(10))
            isScrollContainer = false
            overScrollMode = View.OVER_SCROLL_NEVER
            
            // Make clickable but don't consume all touches - let children handle their clicks
            isClickable = true
            isFocusable = true
        }
        
        val rootLinearLayout = root as LinearLayout
        
        // 1. Search trigger styled like a compact text field
        searchBarContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            background = createSearchFieldBackground(palette, isActive = false)
            isClickable = true
            isFocusable = true
            contentDescription = "Search emoji"
            setOnClickListener {
                if (panelMode == PanelMode.SEARCH) {
                    exitSearchMode()
                } else {
                    enterSearchMode()
                }
            }
        }
        
        searchBarIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(getContrastColor(palette.keyboardBg))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(20),
                dpToPx(20)
            )
        }
        searchBarContainer?.addView(searchBarIcon)
        
        searchBarText = TextView(context).apply {
            text = "Search Emoji"
            textSize = 16f
            setTextColor(getContrastColor(palette.keyboardBg))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(dpToPx(12), 0, 0, 0)
            }
        }
        searchBarContainer?.addView(searchBarText)
        rootLinearLayout.addView(searchBarContainer)
        
        // Section label (e.g., SMILES & PEOPLE)
        categoryTitleView = TextView(context).apply {
            text = getCategoryTitle(currentCategory)
            textSize = 12f
            setTextColor(ColorUtils.setAlphaComponent(getContrastColor(palette.keyboardBg), 200))
            setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(4))
            letterSpacing = 0.05f
        }
        rootLinearLayout.addView(categoryTitleView)
        
        // 2. Emoji grid / Sticker panel host
        emojiGrid = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, dpToPx(4), 0, dpToPx(8))
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = true
            setHasFixedSize(true)
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
            isClickable = true
            isFocusable = true
        }

        panelSwitcher = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        panelSwitcher?.addView(emojiGrid)
        rootLinearLayout.addView(panelSwitcher)
        attachStickerPanelIfReady()
        
        // 3. Bottom Bar (Footer) with ABC and Delete buttons - matching reference image
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )
            background = createBottomBarDrawable(palette)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // ABC Button (Left side) - white color, no background fill
        btnABC = TextView(context).apply {
            text = "ABC"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(30),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            background = null // No background fill
        }
        bottomBar.addView(btnABC)
        
        // Scrollable category icons next to ABC button - improved smooth scrolling
        categoryScrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ).apply { setMargins(dpToPx(8), 0, 0, 0) }
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // Use scroll listener for smooth snapping to nearest category
            viewTreeObserver.addOnScrollChangedListener {
                // Debounce scroll snapping to avoid excessive calls
                mainHandler.removeCallbacks(scrollSnapRunnable)
                mainHandler.postDelayed(scrollSnapRunnable, 150)
            }
        }
        
        emojiCategories = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        categoryScrollView?.addView(emojiCategories)
        bottomBar.addView(categoryScrollView)
        
        // Delete button on the right - white color, no background fill
        btnDelete = ImageView(context).apply {
            contentDescription = "Backspace"
            setImageResource(android.R.drawable.ic_input_delete)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            background = null // No background fill
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        bottomBar.addView(btnDelete)
        
        rootLinearLayout.addView(bottomBar)
        this.bottomBar = bottomBar
        
        // Setup functionality
        setupEmojiGrid()
        setupFooterButtons()
        setupToolbar()
        applyTheme()
        
        val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        
        return root!!
    }
    
    private fun setupEmojiGrid() {
        emojiGrid?.apply {
            val gridLayoutManager = GridLayoutManager(context, SPAN_COUNT)
            layoutManager = gridLayoutManager
            
            // CRITICAL: Ensure ONLY this RecyclerView scrolls, not the entire panel
            isNestedScrollingEnabled = true // Enable scrolling for this RecyclerView
            overScrollMode = View.OVER_SCROLL_NEVER // Remove overscroll bounce effects
            setHasFixedSize(true) // Optimize performance
            isScrollbarFadingEnabled = true // Fade scrollbar for cleaner look
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            
            // Enable smooth scrolling with optimized settings
            setItemAnimator(null) // Disable item animations for smoother scrolling
            setRecycledViewPool(RecyclerView.RecycledViewPool().apply {
                setMaxRecycledViews(0, 20) // Cache more views for smoother scrolling
            })
            
            // Add smooth scroll listener for better performance
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    // Optimize scrolling performance
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            // When scrolling stops, ensure smooth state
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            // User is dragging - optimize for responsiveness
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            // Smooth scrolling animation in progress
                        }
                    }
                }
            })
            
            // Extract emoji grid from GboardEmojiPanel and wire to our RecyclerView
            val adapter = EmojiAdapter(::onEmojiClicked, ::onEmojiLongClicked)
            this.adapter = adapter
            
            LogUtil.d(TAG, "✅ Emoji grid ONLY scrolls - panel header/footer fixed with smooth scrolling")
        }
        
        attachEmojiGridSwipeGestures()
        
        // Emoji data served from EmojiRepository (loaded from emojis.json)
    }

    private fun attachEmojiGridSwipeGestures() {
        emojiGrid?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    emojiGridInitialX = event.x
                    emojiGridInitialY = event.y
                    emojiGridSwipeHandled = false
                }
                MotionEvent.ACTION_MOVE -> handleEmojiGridSwipe(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    emojiGridSwipeHandled = false
                }
            }
            false
        }
    }

    private fun handleEmojiGridSwipe(event: MotionEvent) {
        if (panelMode == PanelMode.SEARCH) return
        if (emojiGridSwipeHandled) return
        
        val dx = event.x - emojiGridInitialX
        val dy = event.y - emojiGridInitialY
        val horizontalDominant = abs(dx) > abs(dy) * 1.2f
        val thresholdPassed = abs(dx) > dpToPx(32).toFloat()
        val now = SystemClock.elapsedRealtime()
        val cooldownElapsed = now - lastEmojiCategorySwipe > 180
        
        if (horizontalDominant && thresholdPassed && cooldownElapsed) {
            if (dx < 0) {
                navigateToAdjacentCategory(forward = true)
            } else {
                navigateToAdjacentCategory(forward = false)
            }
            emojiGridSwipeHandled = true
            lastEmojiCategorySwipe = now
        }
    }

    private fun navigateToAdjacentCategory(forward: Boolean) {
        val currentIndex = CATEGORY_ORDER.indexOf(currentCategory)
        if (currentIndex == -1) return
        val nextIndex = if (forward) currentIndex + 1 else currentIndex - 1
        if (nextIndex !in CATEGORY_ORDER.indices) return
        val targetCategory = CATEGORY_ORDER[nextIndex]
        val targetView = findCategoryView(targetCategory)
        if (targetView != null) {
            selectCategory(targetCategory, targetView)
        } else {
            currentCategory = targetCategory
            loadCategoryEmojis(targetCategory)
        }
    }

    private fun findCategoryView(category: String): TextView? {
        val container = emojiCategories ?: return null
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            if (child.tag == category) return child
        }
        return null
    }
    
    private fun setupFooterButtons() {
        // ABC button - return to normal keyboard (transparent background)
        btnABC?.setOnClickListener {
            LogUtil.d(TAG, "ABC button clicked - returning to letters")
            onBackToLetters()
        }
        // Delete button - backspace with long-press repeat
        btnDelete?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    backspaceOnce()
                    startDeleteRepeat()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopDeleteRepeat()
                    true
                }
                else -> false
            }
        }
        
        LogUtil.d(TAG, "Footer buttons wired successfully (ABC + delete)")
    }
    
    private fun setupToolbar() {
        // Setup category tabs (CleverType style)
        setupCategories()
        
        LogUtil.d(TAG, "Toolbar setup complete (CleverType style)")
    }
    
    private fun setupCategories() {
        emojiCategories?.removeAllViews()
        
        CATEGORY_ORDER.forEach { name ->
            val icon = CATEGORY_ICONS[name] ?: "•"
            val categoryBtn = TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                val size = dpToPx(36)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                tag = name
                alpha = if (name == currentCategory) 1.0f else 0.6f
                
                setOnClickListener {
                    LogUtil.d(TAG, "Category clicked: $name")
                    selectCategory(name, this)
                }
            }
            emojiCategories?.addView(categoryBtn)
            
            if (name == currentCategory) {
                selectedCategoryView = categoryBtn
            }
        }
        
        // Ensure emojis are loaded for the current category
        loadCategoryEmojis(currentCategory)
        updateCategoryTitle()
    }
    
    private fun selectCategory(category: String, view: TextView) {
        // Update visual selection
        selectedCategoryView = view
        currentCategory = category
        updateCategoryTitle()
        
        // Apply theme to update selected/unselected states
        applyCategoryTheme()
        
        // Auto-scroll to selected category if it's off screen
        scrollToSelectedCategory(view)
        
        // Load emojis
        loadCategoryEmojis(category)
    }

    private fun updateCategoryTitle() {
        if (panelMode == PanelMode.SEARCH) {
            categoryTitleView?.visibility = View.GONE
            return
        }
        categoryTitleView?.visibility = View.VISIBLE
        categoryTitleView?.text = getCategoryTitle(currentCategory)
    }

    private fun getCategoryTitle(category: String): String {
        val label = CATEGORY_DISPLAY_NAMES[category] ?: category
        return label.uppercase()
    }

    private fun updateSearchFieldState() {
        val palette = PanelTheme.palette
        val isActive = panelMode == PanelMode.SEARCH
        searchBarContainer?.background = createSearchFieldBackground(palette, isActive)
        val textColor = ColorUtils.setAlphaComponent(getContrastColor(palette.keyboardBg), if (isActive) 255 else 210)
        searchBarText?.setTextColor(textColor)
        searchBarIcon?.setColorFilter(getContrastColor(palette.keyboardBg))
    }
    
    /**
     * Scroll the category bar to ensure selected category is visible - improved smooth scrolling
     */
    private fun scrollToSelectedCategory(selectedView: TextView) {
        try {
            // Find the HorizontalScrollView parent of emojiCategories
            var parent = emojiCategories?.parent
            while (parent != null && parent !is HorizontalScrollView) {
                parent = (parent as? View)?.parent
            }
            
            (parent as? HorizontalScrollView)?.let { scrollView ->
                // Calculate the position of the selected category to center it
                val scrollViewWidth = scrollView.width
                val selectedViewLeft = selectedView.left
                val selectedViewWidth = selectedView.width
                val scrollX = selectedViewLeft - (scrollViewWidth / 2) + (selectedViewWidth / 2)
                
                // Use smooth scroll with animation
                scrollView.post {
                    scrollView.smoothScrollTo(
                        maxOf(0, scrollX),
                        0
                    )
                }
                
                LogUtil.d(TAG, "📍 Auto-scrolled to selected category: $currentCategory")
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "Could not auto-scroll to category", e)
        }
    }
    
    /**
     * Smooth scroll to nearest category when fling ends
     */
    private fun smoothScrollToNearestCategory() {
        categoryScrollView?.let { scrollView ->
            emojiCategories?.let { container ->
                val scrollX = scrollView.scrollX
                val scrollViewWidth = scrollView.width
                val centerX = scrollX + scrollViewWidth / 2
                
                // Find the category closest to center
                var closestView: TextView? = null
                var minDistance = Int.MAX_VALUE
                
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i) as? TextView ?: continue
                    val childCenterX = child.left + child.width / 2
                    val distance = abs(childCenterX - centerX)
                    
                    if (distance < minDistance) {
                        minDistance = distance
                        closestView = child
                    }
                }
                
                // Smooth scroll to closest category
                closestView?.let { view ->
                    val targetScrollX = view.left - (scrollViewWidth / 2) + (view.width / 2)
                    scrollView.post {
                        scrollView.smoothScrollTo(
                            maxOf(0, targetScrollX),
                            0
                        )
                    }
                }
            }
        }
    }
    
    fun applyTheme() {
        try {
            val palette = PanelTheme.palette

            root?.background = buildPanelGradient(palette)
            emojiCategories?.setBackgroundColor(Color.TRANSPARENT)
            emojiGrid?.setBackgroundColor(Color.TRANSPARENT)
            bottomBar?.background = createBottomBarDrawable(palette)
            categoryTitleView?.setTextColor(ColorUtils.setAlphaComponent(getContrastColor(palette.keyboardBg), 200))
            updateSearchFieldState()

            btnABC?.apply {
                setTextColor(Color.WHITE)
                background = null // No background fill
            }

            btnDelete?.apply {
                setColorFilter(Color.WHITE)
                background = null // No background fill
            }

            applyCategoryTheme()

            LogUtil.d(TAG, "✅ Complete theme applied to emoji panel (100% programmatic)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error applying theme", e)
        }
    }
    
    private fun applyCategoryTheme() {
        val palette = PanelTheme.palette
        
        // Force override container backgrounds to match keyboard theme
        emojiCategories?.setBackgroundColor(Color.TRANSPARENT)
        
        emojiCategories?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i) as? TextView ?: continue
                val isSelected = child == selectedCategoryView
                
                child.background = if (isSelected) createCategoryChipDrawable(palette) else null
                child.alpha = if (isSelected) 1.0f else 0.6f
            }
        }
        
        LogUtil.d(TAG, "✅ Category theme applied - forcing keyboard background colors")
    }
    
    private fun onEmojiClicked(emoji: String) {
        LogUtil.d(TAG, "Emoji clicked: $emoji")
        
        // Apply skin tone if supported
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val preferredTone = prefs.getString("preferred_skin_tone", "") ?: ""
        
        // Get base emoji (without tone)
        val baseEmoji = getBaseEmoji(emoji)
        
        // Check if there's a per-emoji default tone (like Gboard)
        val defaultTone = getDefaultEmojiTone(prefs, baseEmoji)
        
        // Apply tone: per-emoji default > global preferred > no tone
        val finalEmoji = when {
            defaultTone != null -> defaultTone
            preferredTone.isNotEmpty() && supportsSkinTone(baseEmoji) -> baseEmoji + preferredTone
            else -> emoji
        }
        
        // Log detailed emoji information for debugging
        LogUtil.d(TAG, "About to insert emoji: '$finalEmoji'")
        LogUtil.d(TAG, "  - Emoji length: ${finalEmoji.length}")
        LogUtil.d(TAG, "  - Emoji codepoints: ${finalEmoji.codePoints().toArray().joinToString(",") { "U+${it.toString(16).uppercase()}" }}")
        LogUtil.d(TAG, "  - Emoji bytes: ${finalEmoji.toByteArray(Charsets.UTF_8).joinToString(",") { it.toString() }}")
        
        val ic = inputConnectionProvider()
        if (ic != null) {
            // Commit as CharSequence to ensure proper encoding
            ic.commitText(finalEmoji as CharSequence, 1)
            LogUtil.d(TAG, "✅ Successfully committed emoji via InputConnection")
        } else {
            LogUtil.e(TAG, "❌ InputConnection is null, cannot insert emoji")
        }
        
        // Update emoji history (LRU)
        updateEmojiHistory(prefs, finalEmoji)
        
        // Reload Recent category if currently viewing it
        if (currentCategory == "Recent") {
            loadCategoryEmojis("Recent")
        }
        
        LogUtil.d(TAG, "Inserted emoji: $finalEmoji (base: $baseEmoji, tone: ${if (defaultTone != null) "default" else preferredTone})")
    }
    
    private fun getBaseEmoji(emojiWithTone: String): String {
        val skinToneModifiers = listOf("🏻", "🏼", "🏽", "🏾", "🏿")
        skinToneModifiers.forEach { modifier ->
            if (emojiWithTone.contains(modifier)) {
                return emojiWithTone.replace(modifier, "")
            }
        }
        return emojiWithTone
    }
    
    private fun getDefaultEmojiTone(prefs: SharedPreferences, baseEmoji: String): String? {
        val defaultTonesJson = prefs.getString("default_emoji_tones", "{}") ?: "{}"
        try {
            if (defaultTonesJson != "{}") {
                // Simple JSON parsing for key-value pairs
                defaultTonesJson.removeSurrounding("{", "}").split(", ").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim('"')
                        val value = parts[1].trim('"')
                        if (key == baseEmoji) {
                            return value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "Error parsing default emoji tones: ${e.message}")
        }
        return null
    }
    
    private fun updateEmojiHistory(prefs: SharedPreferences, emoji: String) {
        try {
            // Load current history
            val historyJson = prefs.getString("emoji_history", "[]") ?: "[]"
            val history = mutableListOf<String>()
            
            val cleanJson = historyJson.trim('[', ']')
            if (cleanJson.isNotEmpty()) {
                cleanJson.split(",").forEach { e ->
                    val cleanEmoji = e.trim().trim('"')
                    if (cleanEmoji.isNotEmpty()) {
                        history.add(cleanEmoji)
                    }
                }
            }
            
            // Remove if already exists (move to front)
            history.remove(emoji)
            
            // Add to front
            history.add(0, emoji)
            
            // Trim to max size
            val maxSize = prefs.getInt("emoji_history_max_size", 90)
            while (history.size > maxSize) {
                history.removeAt(history.size - 1)
            }
            
            // Save back to preferences
            val newHistoryJson = history.joinToString(",") { "\"$it\"" }
            prefs.edit().putString("emoji_history", "[$newHistoryJson]").apply()
            
            LogUtil.d(TAG, "Updated emoji history: added '$emoji' (total: ${history.size})")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error updating emoji history", e)
        }
    }
    
    private fun onEmojiLongClicked(emoji: String, anchorView: View): Boolean {
        if (!supportsSkinTone(emoji)) return false
        showEmojiVariants(emoji, anchorView)
        return true
    }
    
    private fun showEmojiVariants(emoji: String, anchorView: View) {
        val baseEmoji = getBaseEmoji(emoji)
        val tones = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")
        val variants = tones.map { if (it.isEmpty()) baseEmoji else baseEmoji + it }
        
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val currentDefault = getDefaultEmojiTone(prefs, baseEmoji)
        
        val popup = PopupWindow(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val palette = PanelTheme.palette
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(palette.keyBg)
            }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            elevation = dpToPx(8).toFloat()
        }
        
        variants.forEach { variant ->
            val isDefault = (variant == currentDefault) || (currentDefault == null && variant == baseEmoji)
            
            val btn = TextView(context).apply {
                text = variant
                textSize = 28f
                gravity = Gravity.CENTER
                val size = dpToPx(48)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                
                // Highlight current default (like Gboard)
                if (isDefault) {
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(PanelTheme.palette.specialAccent)
                    }
                    setTextColor(Color.WHITE)
                }
                
                setOnClickListener {
                    // Save as default for this emoji (like Gboard)
                    saveDefaultEmojiTone(prefs, baseEmoji, variant)
                    
                    // Insert the selected emoji
                    onEmojiClicked(variant)
                    popup.dismiss()
                    
                    // Reload category to show updated defaults in grid
                    loadCategoryEmojis(currentCategory)
                    
                    LogUtil.d(TAG, "Set default tone for $baseEmoji to $variant")
                }
            }
            layout.addView(btn)
        }
        
        popup.contentView = layout
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.width = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dpToPx(8).toFloat()
        
        // Show popup above the emoji
        popup.showAsDropDown(anchorView, 0, -dpToPx(60))
        
        LogUtil.d(TAG, "Showing skin tone variants for: $baseEmoji (current default: $currentDefault)")
    }
    
    private fun saveDefaultEmojiTone(prefs: SharedPreferences, baseEmoji: String, toneVariant: String) {
        try {
            // Load existing defaults
            val defaultTonesJson = prefs.getString("default_emoji_tones", "{}") ?: "{}"
            val defaultTones = mutableMapOf<String, String>()
            
            if (defaultTonesJson != "{}") {
                defaultTonesJson.removeSurrounding("{", "}").split(", ").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) {
                        defaultTones[parts[0].trim('"')] = parts[1].trim('"')
                    }
                }
            }
            
            // Update or add this emoji's default
            defaultTones[baseEmoji] = toneVariant
            
            // Save back
            val jsonString = defaultTones.entries.joinToString(", ") { "\"${it.key}\"=\"${it.value}\"" }
            prefs.edit().putString("default_emoji_tones", "{$jsonString}").apply()
            
            LogUtil.d(TAG, "Saved default tone for '$baseEmoji' → '$toneVariant'")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error saving default emoji tone", e)
        }
    }
    
    private fun supportsSkinTone(emoji: String): Boolean {
        // Emojis that support Fitzpatrick modifiers
        val skinToneSupportedEmojis = setOf(
            "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌", "🤞", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝", "👍", "👎", "✊",
            "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍", "💅",
            "🤳", "💪", "🦵", "🦶", "👂", "🦻", "👃", "👶", "👧", "🧒", "👦",
            "👩", "🧑", "👨", "👩‍🦱", "🧑‍🦱", "👨‍🦱", "👩‍🦰", "🧑‍🦰", "👨‍🦰",
            "👱‍♀️", "👱", "👱‍♂️", "👩‍🦳", "🧑‍🦳", "👨‍🦳", "👩‍🦲", "🧑‍🦲", "👨‍🦲",
            "🧔", "👵", "🧓", "👴", "👲", "👳‍♀️", "👳", "👳‍♂️", "🧕", "👮‍♀️",
            "👮", "👮‍♂️", "👷‍♀️", "👷", "👷‍♂️", "💂‍♀️", "💂", "💂‍♂️"
        )
        return emoji in skinToneSupportedEmojis
    }

    private fun backspaceOnce() {
        try {
            inputConnectionProvider()?.deleteSurroundingText(1, 0)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unable to delete character", e)
        }
    }
    
    private fun startDeleteRepeat() {
        stopDeleteRepeat()
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                backspaceOnce()
                mainHandler.postDelayed(this, DELETE_REPEAT_DELAY)
            }
        }
        deleteRepeatRunnable?.let { mainHandler.postDelayed(it, DELETE_REPEAT_DELAY) }
    }
    
    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
        deleteRepeatRunnable = null
    }
    
    private fun loadDefaultEmojis() {
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val preferredTone = prefs.getString("preferred_skin_tone", "") ?: ""
        val defaultEmojis = EmojiRepository.getPopular(40, context)
            .map { applyPreferredSkinTone(it.char, preferredTone) }
        updateEmojiGrid(defaultEmojis)
    }
    
    private fun loadCategoryEmojis(category: String) {
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val preferredTone = prefs.getString("preferred_skin_tone", "") ?: ""

        if (category != "Stickers") {
            hideStickerPanel()
        }

        val emojis = when (category) {
            "Recent" -> {
                val historyJson = prefs.getString("emoji_history", "[]") ?: "[]"
                val history = mutableListOf<String>()
                try {
                    val cleanJson = historyJson.trim('[', ']')
                    if (cleanJson.isNotEmpty()) {
                        cleanJson.split(",").forEach { emoji ->
                            val cleanEmoji = emoji.trim().trim('"')
                            if (cleanEmoji.isNotEmpty()) {
                                history.add(cleanEmoji)
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error loading emoji history", e)
                }
                if (history.isEmpty()) {
                    EmojiRepository.getPopular(40, context).map { applyPreferredSkinTone(it.char, preferredTone) }
                } else {
                    history
                }
            }
            "Stickers" -> {
                showStickerPanel()
                return
            }
            else -> EmojiRepository.getByAlias(category, context)
                .map { applyPreferredSkinTone(it.char, preferredTone) }
        }
        updateEmojiGrid(emojis)
    }
    
    private fun applyPreferredSkinTone(baseEmoji: String, preferredTone: String): String {
        val cleanBase = getBaseEmoji(baseEmoji)
        
        // Check for per-emoji default first
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val defaultTone = getDefaultEmojiTone(prefs, cleanBase)
        if (defaultTone != null) {
            return defaultTone
        }
        
        // Apply global preferred tone if applicable
        return if (preferredTone.isNotEmpty() && supportsSkinTone(cleanBase)) {
            cleanBase + preferredTone
        } else {
            baseEmoji
        }
    }
    
    private fun preferredSkinTone(): String {
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        return prefs.getString("preferred_skin_tone", "") ?: ""
    }
    
    private fun updateEmojiGrid(emojis: List<String>) {
        (emojiGrid?.adapter as? EmojiAdapter)?.updateEmojis(emojis)
    }
    
    private fun enterSearchMode() {
        LogUtil.d(TAG, "🔍 enterSearchMode() called, current panelMode=$panelMode")
        hideStickerPanel()
        if (panelMode == PanelMode.SEARCH) {
            LogUtil.d(TAG, "Already in search mode, skipping")
            return
        }
        panelMode = PanelMode.SEARCH
        searchQuery = ""
        updateSearchFieldState()
        updateCategoryTitle()
        
        LogUtil.d(TAG, "Notifying search mode changed: true")
        onSearchModeChanged(true)
        
        // Hide category bar and emoji grid, keep bottom bar visible
        LogUtil.d(TAG, "Hiding category bar and emoji grid")
        categoryScrollView?.visibility = View.GONE
        emojiGrid?.visibility = View.GONE
        categoryTitleView?.visibility = View.GONE
        
        // Show search overlay
        val rootLayout = root as? LinearLayout
        if (rootLayout == null) {
            LogUtil.e(TAG, "❌ Root layout is null, cannot create search overlay")
            return
        }
        
        LogUtil.d(TAG, "Creating search overlay...")
        createSearchOverlay(rootLayout)
        
        LogUtil.d(TAG, "Applying category theme and updating results")
        applyCategoryTheme()
        updateSearchResults()
        
        // Verify keyboard container is ready
        searchKeyboardContainer?.let { container ->
            LogUtil.d(TAG, "📱 Keyboard container ready:")
            LogUtil.d(TAG, "   - Container ID: ${container.id}")
            LogUtil.d(TAG, "   - Visibility: ${if (container.visibility == View.VISIBLE) "VISIBLE" else "HIDDEN"}")
            LogUtil.d(TAG, "   - Size: ${container.width}x${container.height}px")
            LogUtil.d(TAG, "   - Child count: ${container.childCount}")
        } ?: LogUtil.e(TAG, "❌ Keyboard container is NULL!")
        
        LogUtil.d(TAG, "✅ Entered search mode successfully")
    }
    
    private fun exitSearchMode() {
        if (panelMode == PanelMode.NORMAL) return
        panelMode = PanelMode.NORMAL
        searchQuery = ""
        updateSearchFieldState()
        updateCategoryTitle()
        
        onSearchModeChanged(false)
        
        // Hide search overlay
        searchOverlayContainer?.visibility = View.GONE
        
        // Show category bar and emoji grid
        categoryScrollView?.visibility = View.VISIBLE
        emojiGrid?.visibility = View.VISIBLE
        categoryTitleView?.visibility = View.VISIBLE
        
        applyCategoryTheme()
        
        LogUtil.d(TAG, "Exited search mode")
    }
    
    fun appendToSearchQuery(char: Char) {
        if (panelMode != PanelMode.SEARCH) return
        searchQuery += char
        updateSearchField()
        updateSearchResults()
    }
    
    fun removeLastFromSearchQuery() {
        if (panelMode != PanelMode.SEARCH) return
        if (searchQuery.isNotEmpty()) {
            searchQuery = searchQuery.dropLast(1)
            updateSearchField()
            updateSearchResults()
        }
    }
    
    fun clearSearchQuery() {
        if (panelMode != PanelMode.SEARCH) return
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            updateSearchField()
            updateSearchResults()
        }
    }
    
    fun isInSearchMode(): Boolean = panelMode == PanelMode.SEARCH
    
    /**
     * Get the keyboard container for search mode
     * UnifiedKeyboardView will render the keyboard grid into this container
     */
    fun getSearchKeyboardContainer(): FrameLayout? = searchKeyboardContainer
    
    private fun updateSearchField() {
        searchInputField?.text = if (searchQuery.isEmpty()) "Search emoji" else searchQuery
    }
    
    private fun updateSearchResults() {
        val preferredTone = preferredSkinTone()
        
        // If query is empty, show popular emojis
        val results = if (searchQuery.isBlank()) {
            EmojiRepository.getPopular(20, context).map { it.char }
        } else {
            val searchResults = EmojiRepository.search(searchQuery, 40, context)
            searchResults.map { it.char }
        }
        
        val displayResults = results.map { applyPreferredSkinTone(it, preferredTone) }
        (searchResultsRow?.adapter as? EmojiSearchAdapter)?.updateResults(displayResults)
    }

    fun useStickerPanel(panel: StickerPanel) {
        stickerPanel = panel.apply {
            visibility = View.GONE
            setOnStickerSelectedListener { sticker, content ->
                commitStickerFromPanel(sticker, content)
            }
            setOnPackLoadedListener { packId ->
                LogUtil.d(TAG, "Sticker pack loaded: $packId")
            }
        }
        attachStickerPanelIfReady()
    }

    fun commitStickerFromPanel(stickerData: StickerData, content: String) {
        val ic = inputConnectionProvider()
        if (ic == null) {
            LogUtil.w(TAG, "No input connection available for sticker insertion")
            return
        }

        try {
            when {
                content.startsWith("/") && java.io.File(content).exists() -> {
                    if (stickerData.emojis.isNotEmpty()) {
                        ic.commitText(stickerData.emojis.first(), 1)
                        saveEmojiToHistory(stickerData.emojis.first())
                    } else {
                        ic.commitText("[Sticker: ${stickerData.id}]", 1)
                    }
                }
                content.startsWith("http") -> {
                    if (stickerData.emojis.isNotEmpty()) {
                        ic.commitText(stickerData.emojis.first(), 1)
                        saveEmojiToHistory(stickerData.emojis.first())
                    } else {
                        ic.commitText("[Sticker]", 1)
                    }
                }
                else -> {
                    if (stickerData.emojis.isNotEmpty()) {
                        insertEmojiDirectly(stickerData.emojis.first())
                    } else {
                        ic.commitText(content, 1)
                    }
                }
            }

            LogUtil.d(TAG, "Inserted sticker content: $content")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error inserting sticker content", e)
            ic.commitText("🖼️", 1)
        }
    }

    fun requestStickerSurface(forceRefresh: Boolean = false) {
        showStickerPanel(forceRefresh)
    }

    fun requestEmojiSurface() {
        hideStickerPanel()
    }

    private fun attachStickerPanelIfReady() {
        val host = panelSwitcher ?: return
        val panel = stickerPanel ?: return
        if (panel.parent !== host) {
            (panel.parent as? ViewGroup)?.removeView(panel)
            host.addView(panel)
        }
        panel.visibility = View.GONE
    }

    private fun showStickerPanel(forceRefresh: Boolean = false) {
        stickerPanel?.let { panel ->
            emojiGrid?.visibility = View.GONE
            panel.visibility = View.VISIBLE
            panel.show(forceRefresh)
        } ?: LogUtil.w(TAG, "Sticker panel requested but not attached")
    }

    private fun hideStickerPanel() {
        stickerPanel?.visibility = View.GONE
        emojiGrid?.visibility = View.VISIBLE
    }

    private fun insertEmojiDirectly(emoji: String) {
        val ic = inputConnectionProvider()
        ic?.let {
            it.commitText(emoji, 1)
            saveEmojiToHistory(emoji)
            LogUtil.d(TAG, "Inserted emoji: $emoji")
        }
    }

    private fun saveEmojiToHistory(emoji: String) {
        try {
            val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
            val currentHistory = prefs.getString("emoji_history", "[]") ?: "[]"

            val history = mutableListOf<String>()
            val cleanJson = currentHistory.trim('[', ']')
            if (cleanJson.isNotEmpty()) {
                cleanJson.split(",").forEach { item ->
                    val cleanItem = item.trim().trim('"')
                    if (cleanItem.isNotEmpty() && cleanItem != emoji) {
                        history.add(cleanItem)
                    }
                }
            }

            history.add(0, emoji)

            if (history.size > 50) {
                history.subList(50, history.size).clear()
            }

            val historyJson = history.joinToString(",") { "\"$it\"" }
            prefs.edit().putString("emoji_history", "[$historyJson]").apply()

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error saving emoji to history", e)
        }
    }
    
    private fun createSimpleKeyboardLayout(): View {
        val palette = PanelTheme.palette
        
        val keyboardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, 0)
            gravity = Gravity.BOTTOM
        }
        
        // Define keyboard rows
        val rows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        )
        
        // Create rows
        rows.forEachIndexed { rowIndex, keys ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
            }
            
            // Add shift key for third row (left side)
            if (rowIndex == 2) {
                rowLayout.addView(createSpecialKey("⇧", 1.5f, palette))
            }
            
            // Add letter keys
            keys.forEach { key ->
                rowLayout.addView(createKey(key, palette))
            }
            
            // Add backspace key for third row (right side)
            if (rowIndex == 2) {
                rowLayout.addView(createSpecialKey("⌫", 1.5f, palette))
            }
            
            keyboardLayout.addView(rowLayout)
        }
        
        // Bottom row with space bar
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        
        bottomRow.addView(createSpecialKey("123", 1.2f, palette))
        bottomRow.addView(createKey(",", palette))
        bottomRow.addView(createSpaceKey(palette))
        bottomRow.addView(createKey(".", palette))
        bottomRow.addView(createSpecialKey("↵", 1.2f, palette))
        
        keyboardLayout.addView(bottomRow)
        
        return keyboardLayout
    }
    
    private fun createKey(label: String, palette: ThemePaletteV2): View {
        return TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.keyText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(palette.keyBg)
                setStroke(1, palette.keyBorderColor)
            }
            
            setOnClickListener {
                appendToSearchQuery(label[0])
            }
        }
    }
    
    private fun createSpecialKey(label: String, weight: Float, palette: ThemePaletteV2): View {
        return TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.keyText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                weight
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(ColorUtils.blendARGB(palette.keyBg, palette.specialAccent, 0.2f))
                setStroke(1, palette.keyBorderColor)
            }
            
            setOnClickListener {
                when (label) {
                    "⌫" -> removeLastFromSearchQuery()
                    "↵" -> {
                        // Enter key - could trigger search or dismiss
                        exitSearchMode()
                    }
                    "⇧" -> {
                        // Shift key - could toggle case
                        LogUtil.d(TAG, "Shift key pressed")
                    }
                    "123" -> {
                        // Number mode - could show number keyboard
                        LogUtil.d(TAG, "123 key pressed")
                    }
                    else -> appendToSearchQuery(label[0])
                }
            }
        }
    }
    
    private fun createSpaceKey(palette: ThemePaletteV2): View {
        return TextView(context).apply {
            text = "space"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(palette.keyText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                5f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(palette.keyBg)
                setStroke(1, palette.keyBorderColor)
            }
            
            setOnClickListener {
                appendToSearchQuery(' ')
            }
        }
    }
    
    private fun createSearchOverlay(rootLayout: LinearLayout) {
        LogUtil.d(TAG, "📋 createSearchOverlay() called")
        if (searchOverlayContainer != null) {
            LogUtil.d(TAG, "Search overlay already exists, making visible")
            searchOverlayContainer?.visibility = View.VISIBLE
            return
        }
        
        LogUtil.d(TAG, "Creating new search overlay container...")
        val palette = PanelTheme.palette
        
        searchOverlayContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(palette.keyboardBg)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(0))
            
            // Make clickable but let keyboard container handle its own touches
            isClickable = true
            isFocusable = true
        }
        
        // 1. First row: Horizontal scrolling emoji results
        searchResultsRow = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = EmojiSearchAdapter { emoji ->
                onEmojiClicked(emoji)
            }
            setPadding(0, 0, 0, dpToPx(4))
        }
        searchOverlayContainer?.addView(searchResultsRow)
        
        // 2. Second row: Back button + Search text display
        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
            setPadding(0, 0, 0, dpToPx(4))
        }
        
        val backButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_back_chevron)
            ImageViewCompat.setImageTintList(
                this,
                ColorStateList.valueOf(palette.keyText)
            )
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            )
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { exitSearchMode() }
        }
        searchRow.addView(backButton)
        
        searchInputField = TextView(context).apply {
            text = "Search emoji"
            textSize = 16f
            setTextColor(palette.keyText)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(ColorUtils.setAlphaComponent(palette.keyBg, 120))
            }
        }
        searchRow.addView(searchInputField)
        
        searchOverlayContainer?.addView(searchRow)
        
        // 3. Keyboard View (Simple QWERTY layout without suggestions/toolbar)
        searchKeyboardContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take ALL remaining space
            )
            setBackgroundColor(palette.keyboardBg)
            id = View.generateViewId()
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            
            // Add keyboard layout
            addView(createSimpleKeyboardLayout())
            
            LogUtil.d(TAG, "📱 Keyboard view created with keys")
        }
        searchOverlayContainer?.addView(searchKeyboardContainer)
        
        // Add to root at index 1 (after category bar)
        LogUtil.d(TAG, "Adding search overlay to root layout at index 1...")
        rootLayout.addView(searchOverlayContainer, 1)
        LogUtil.d(TAG, "✅ Search overlay created successfully")
        LogUtil.d(TAG, "   - Keyboard container ID: ${searchKeyboardContainer?.id}")
        LogUtil.d(TAG, "   - Keyboard container dimensions: ${searchKeyboardContainer?.layoutParams?.width}x${searchKeyboardContainer?.layoutParams?.height}")
        LogUtil.d(TAG, "   - 📱 Keyboard will be rendered in this container by UnifiedKeyboardView")
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getContrastColor(color: Int): Int {
        return if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
    }

    private fun buildPanelGradient(palette: ThemePaletteV2): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(palette.keyboardBg)
        }
    }

    private fun createBottomBarDrawable(palette: ThemePaletteV2): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(16).toFloat()
            setColor(palette.keyboardBg)
            setStroke(1, ColorUtils.setAlphaComponent(palette.keyBorderColor, 100))
        }
    }

    private fun createSearchFieldBackground(palette: ThemePaletteV2, isActive: Boolean): GradientDrawable {
        val baseColor = if (isActive) {
            ColorUtils.blendARGB(palette.specialAccent, Color.BLACK, 0.4f)
        } else {
            ColorUtils.setAlphaComponent(palette.keyBg, 140)
        }
        return GradientDrawable().apply {
            cornerRadius = dpToPx(14).toFloat()
            setColor(baseColor)
            setStroke(
                1,
                if (isActive) palette.specialAccent else ColorUtils.setAlphaComponent(palette.keyBorderColor, 90)
            )
        }
    }
    
    private fun createCategoryChipDrawable(palette: ThemePaletteV2): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(12).toFloat()
            setColor(ColorUtils.setAlphaComponent(palette.specialAccent, 180))
        }
    }
    
    /**
     * Helper to create rounded drawable for buttons
     */
    private fun createRoundedDrawable(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }
    }
    
    private fun getKeyboardHeight(): Int {
        // Match the letters keyboard height exactly
        // Letters keyboard: 5 rows (or 6 with number row) × 50dp key_height + gaps + padding
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val keyHeight = context.resources.getDimensionPixelSize(R.dimen.key_height)
        val rows = 5 // Standard: 4 letter rows + 1 bottom row
        val verticalGap = context.resources.getDimensionPixelSize(R.dimen.keyboard_vertical_gap)
        val paddingTop = context.resources.getDimensionPixelSize(R.dimen.keyboard_padding_top)
        val paddingBottom = context.resources.getDimensionPixelSize(R.dimen.keyboard_padding_bottom)
        val suggestionBarHeight = context.resources.getDimensionPixelSize(R.dimen.suggestion_bar_height)
        
        // Calculate total keyboard height = suggestion bar + (rows × key height) + (rows-1 × gaps) + padding
        val calculatedHeight = suggestionBarHeight + (rows * keyHeight) + ((rows - 1) * verticalGap) + paddingTop + paddingBottom
        
        LogUtil.d(TAG, "Keyboard height calculation:")
        LogUtil.d(TAG, "  - Key height: ${keyHeight}px")
        LogUtil.d(TAG, "  - Rows: $rows")
        LogUtil.d(TAG, "  - Vertical gap: ${verticalGap}px")
        LogUtil.d(TAG, "  - Padding top: ${paddingTop}px, bottom: ${paddingBottom}px")
        LogUtil.d(TAG, "  - Suggestion bar: ${suggestionBarHeight}px")
        LogUtil.d(TAG, "  - Calculated: ${calculatedHeight}px")
        
        // Use calculated height or fallback to hardcoded values
        val finalHeight = if (isLandscape) {
            minOf(calculatedHeight, dpToPx(220)) // Landscape: smaller
        } else {
            calculatedHeight // Portrait: full height
        }
        
        LogUtil.d(TAG, "  - Final height (${if (isLandscape) "landscape" else "portrait"}): ${finalHeight}px")
        return finalHeight
    }
    
    private fun showSkinToneBottomSheet() {
        val tones = listOf(
            "" to "Default (Yellow)",
            "🏻" to "Light",
            "🏼" to "Medium-Light",
            "🏽" to "Medium",
            "🏾" to "Medium-Dark",
            "🏿" to "Dark"
        )
        
        val prefs = context.getSharedPreferences("emoji_preferences", Context.MODE_PRIVATE)
        val currentTone = prefs.getString("preferred_skin_tone", "🏽") ?: "🏽"
        
        // Create popup window
        val popup = PopupWindow(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val palette = PanelTheme.palette
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(palette.keyBg)
            }
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(8))
            elevation = dpToPx(8).toFloat()
        }
        
        // Add title
        val title = TextView(context).apply {
            text = "Select Skin Tone"
            textSize = 16f
            setTextColor(PanelTheme.palette.keyText)
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(16))
            gravity = Gravity.CENTER
        }
        layout.addView(title)
        
        tones.forEach { (modifier, name) ->
            val option = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                
                // Highlight current selection
                if (modifier == currentTone) {
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(ColorUtils.blendARGB(
                            PanelTheme.palette.specialAccent, 
                            Color.WHITE, 
                            0.2f
                        ))
                    }
                }
                
                setOnClickListener {
                    // Save skin tone preference
                    prefs.edit().putString("preferred_skin_tone", modifier).apply()
                    
                    // Notify via MainActivity's broadcast system
                    val intent = Intent("com.kvive.keyboard.EMOJI_SETTINGS_CHANGED").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                    
                    popup.dismiss()
                    
                    LogUtil.d(TAG, "Skin tone changed to: $modifier ($name)")
                    
                    // Reload current category to show updated emojis
                    loadCategoryEmojis(currentCategory)
                }
            }
            
            // Emoji preview
            val emojiPreview = TextView(context).apply {
                text = "👋$modifier"
                textSize = 24f
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
                gravity = Gravity.CENTER
            }
            option.addView(emojiPreview)
            
            // Tone name
            val toneName = TextView(context).apply {
                text = name
                textSize = 14f
                setTextColor(PanelTheme.palette.keyText)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(dpToPx(12), 0, 0, 0)
            }
            option.addView(toneName)
            
            layout.addView(option)
        }
        
        popup.contentView = layout
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.width = dpToPx(280)
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dpToPx(8).toFloat()
        
        
        
        LogUtil.d(TAG, "Skin tone selector opened")
    }
    
    /**
     * Reload emoji settings and refresh UI
     */
    fun reloadEmojiSettings() {
            // gboardEmojiPanel removed - handle settings directly
        loadCategoryEmojis(currentCategory)
        applyTheme()
        LogUtil.d(TAG, "Emoji settings reloaded")
    }
    
    /**
     * Simple emoji adapter for RecyclerView
     */
    class EmojiAdapter(
        private val onEmojiClick: (String) -> Unit,
        private val onEmojiLongClick: ((String, View) -> Boolean)? = null
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {
        
        private val emojis = mutableListOf<String>()
        
        init {
            val popular = EmojiRepository.getPopular(40)
            if (popular.isNotEmpty()) {
                emojis.addAll(popular.map { it.char })
            }
        }
        
        fun updateEmojis(newEmojis: List<String>) {
            emojis.clear()
            emojis.addAll(newEmojis)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val textView = TextView(parent.context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                val size = (parent.context.resources.displayMetrics.density * 40).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size)
                
                // Explicitly use system emoji font
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                
                // Enable emoji rendering optimizations
                includeFontPadding = false
            }
            return EmojiViewHolder(textView)
        }
        
        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.bind(emojis[position], onEmojiClick, onEmojiLongClick)
        }
        
        override fun getItemCount() = emojis.size
        
        /**
         * Set a custom click handler for stickers
         */
        fun setCustomClickHandler(handler: (Int) -> Unit) {
            customClickHandler = handler
        }
        
        private var customClickHandler: ((Int) -> Unit)? = null
        
        class EmojiViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
            fun bind(
                emoji: String, 
                onClick: (String) -> Unit,
                onLongClick: ((String, View) -> Boolean)? = null
            ) {
                textView.text = emoji
                textView.setOnClickListener { 
                    // Check if we have a custom click handler (for stickers)
                    val adapter = bindingAdapter as? EmojiAdapter
                    adapter?.customClickHandler?.let { handler ->
                        handler(adapterPosition)
                    } ?: run {
                        onClick(emoji)
                    }
                }
                textView.setOnLongClickListener { view ->
                    onLongClick?.invoke(emoji, view) ?: false
                }
            }
        }
    }
    
    /**
     * Search adapter for horizontal emoji results
     */
    private class EmojiSearchAdapter(
        private val onEmojiClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiSearchAdapter.SearchViewHolder>() {
        
        private val emojis = mutableListOf<String>()
        
        fun updateResults(results: List<String>) {
            emojis.clear()
            emojis.addAll(results)
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
            val textView = TextView(parent.context).apply {
                textSize = 24f // Smaller emojis for horizontal scroll
                gravity = Gravity.CENTER
                val size = (parent.context.resources.displayMetrics.density * 40).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size).apply {
                    if (this is ViewGroup.MarginLayoutParams) {
                        val margin = (parent.context.resources.displayMetrics.density * 1).toInt()
                        setMargins(margin, 0, margin, 0)
                    }
                }
                includeFontPadding = false
                setPadding(2, 2, 2, 2)
            }
            return SearchViewHolder(textView)
        }
        
        override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
            holder.bind(emojis[position], onEmojiClick)
        }
        
        override fun getItemCount(): Int = emojis.size
        
        class SearchViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
            fun bind(emoji: String, onClick: (String) -> Unit) {
                textView.text = emoji
                textView.setOnClickListener { onClick(emoji) }
            }
        }
    }
    
}
