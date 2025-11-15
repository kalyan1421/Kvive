package com.kvive.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.kvive.keyboard.themes.KeyboardThemeV2
import com.kvive.keyboard.themes.PanelTheme
import com.kvive.keyboard.themes.ThemePaletteV2

/**
 * CleverType AI Features Panel
 * - Inflates XML layout for AI actions and streaming results
 * - Handles shimmer placeholder while UnifiedAIService streams responses
 * - Applies ThemeManager palette updates dynamically
 * - Emits processed text via listener for AIKeyboardService to consume
 */
class AIFeaturesPanel @JvmOverloads constructor(
    context: Context,
    private val themeManager: ThemeManager,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    sealed class ChipOption {
        object ChatGpt : ChipOption()
        object Humanise : ChipOption()
        object Reply : ChipOption()
        object Idioms : ChipOption()
        data class SavedPrompt(val title: String, val prompt: String) : ChipOption()

        val displayLabel: String
            get() = when (this) {
                ChatGpt -> "ChatGpt"
                Humanise -> "Humanise"
                Reply -> "Reply"
                Idioms -> "Idioms"
                is SavedPrompt -> title
            }
    }

    enum class ReplyToneFilter(val label: String) {
        POSITIVE("Positive"),
        NEUTRAL("Neutral"),
        NEGATIVE("Negative");
    }

    private val density = context.resources.displayMetrics.density

    private val rootView: View
    private val header: LinearLayout
    private val backBtn: ImageView
    private val title: TextView
    private val langContainer: LinearLayout
    private val langChip: TextView
    private val chipRow: LinearLayout
    private val replyToneRow: LinearLayout
    private val emptyState: LinearLayout
    private val heroTitle: TextView
    private val heroSubtitle: TextView
    private val statusText: TextView
    private val resultList: LinearLayout
    private val shimmerContainer: LinearLayout
    private val btnReplace: Button
    private val guideLink: TextView
    private val btnKeyboard: ImageButton

    private val chipViews = linkedMapOf<ChipOption, TextView>()
    private val replyToneChips = linkedMapOf<ReplyToneFilter, TextView>()
    private val availableChips = mutableListOf<ChipOption>()
    private val btnAddMore: TextView

    private var selectedChip: ChipOption = ChipOption.ChatGpt
    private var selectedReplyTone: ReplyToneFilter = ReplyToneFilter.POSITIVE
    private var shimmerAnimation: Animation? = null
    private var onBackPressed: (() -> Unit)? = null
    private var onTextProcessed: ((String) -> Unit)? = null
    private var onChipSelected: ((ChipOption) -> Unit)? = null
    private var onReplyToneSelected: ((ReplyToneFilter) -> Unit)? = null
    private var lastResult: String = ""
    private var selectedResult: String? = null

    private val themeListener = object : ThemeManager.ThemeChangeListener {
        override fun onThemeChanged(theme: KeyboardThemeV2, palette: ThemePaletteV2) {
            applyTheme(PanelTheme.palette)
        }
    }

    init {
        orientation = VERTICAL
        rootView = LayoutInflater.from(context).inflate(R.layout.panel_ai_writing, this, true)
        header = rootView.findViewById(R.id.header)
        backBtn = rootView.findViewById(R.id.btnBack)
        title = rootView.findViewById(R.id.title)
        langContainer = rootView.findViewById(R.id.langContainer)
        langChip = rootView.findViewById(R.id.langChip)
        chipRow = rootView.findViewById(R.id.chipRow)
        replyToneRow = rootView.findViewById(R.id.replyToneRow)
        emptyState = rootView.findViewById(R.id.emptyState)
        heroTitle = rootView.findViewById(R.id.heroTitle)
        heroSubtitle = rootView.findViewById(R.id.heroSubtitle)
        statusText = rootView.findViewById(R.id.status)
        resultList = rootView.findViewById(R.id.resultList)
        shimmerContainer = rootView.findViewById(R.id.shimmerContainer)
        btnReplace = rootView.findViewById(R.id.btnReplace)
        guideLink = rootView.findViewById(R.id.guideLink)
        btnKeyboard = rootView.findViewById(R.id.btnKeyboard)
        btnAddMore = createAddMoreChip()

        applyTheme(PanelTheme.palette)
        wireClicks()
        setStatus(context.getString(R.string.ai_panel_status_prompt))
        updateInputState(false)
        buildReplyToneChips()

        // Keep focus within the panel when it is visible
        isClickable = true
        isFocusable = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerContainer.clearAnimation()
        themeManager.removeThemeChangeListener(themeListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyTheme(PanelTheme.palette)
        themeManager.addThemeChangeListener(themeListener)
    }

    fun setOnBackPressedListener(listener: (() -> Unit)?) {
        onBackPressed = listener
    }

    fun setOnTextProcessedListener(listener: ((String) -> Unit)?) {
        onTextProcessed = listener
    }

    fun setOnChipSelectedListener(listener: ((ChipOption) -> Unit)?) {
        onChipSelected = listener
    }

    fun setOnReplyToneSelectedListener(listener: ((ReplyToneFilter) -> Unit)?) {
        onReplyToneSelected = listener
    }

    fun showReplyToneFilters(visible: Boolean, selected: ReplyToneFilter) {
        selectedReplyTone = selected
        replyToneRow.visibility = if (visible) View.VISIBLE else View.GONE
        updateReplyToneVisuals()
    }

    fun setTitle(text: CharSequence) {
        title.text = text
    }

    fun setLanguage(text: CharSequence) {
        langChip.text = text
    }

    fun setStatus(message: CharSequence) {
        statusText.text = message
    }

    fun updateChipOptions(
        baseChips: List<ChipOption>,
        savedPrompts: List<ChipOption.SavedPrompt>,
        selectedOverride: ChipOption? = null
    ) {
        availableChips.clear()
        availableChips.addAll(baseChips)
        availableChips.addAll(savedPrompts)

        if (availableChips.isEmpty()) {
            availableChips.add(ChipOption.ChatGpt)
        }

        selectedOverride?.let { selectedChip = it }
        if (availableChips.none { it == selectedChip }) {
            selectedChip = availableChips.first()
        }

        renderChips()
    }

    fun selectChip(option: ChipOption) {
        if (availableChips.none { it == option }) return
        selectedChip = option
        updateChipVisuals()
    }

    fun updateInputState(hasInput: Boolean) {
        if (hasInput) {
            setEmptyStateVisible(false)
            // When there is input: hide back button and guideline, show keyboard icon
            btnReplace.visibility = View.GONE
            guideLink.visibility = View.GONE
            btnKeyboard.visibility = View.VISIBLE
        } else {
            hideLoading()
            clearResults()
            setEmptyStateVisible(false)  // Keep empty state hidden
            statusText.text = context.getString(R.string.ai_panel_status_prompt)
            replyToneRow.visibility = View.GONE
            // When no input: show back button, hide keyboard icon and guideline
            btnReplace.visibility = View.VISIBLE
            btnKeyboard.visibility = View.GONE
            guideLink.visibility = View.GONE
            updatePrimaryButtonLabel()
        }
    }

    fun showLoading(message: String) {
        statusText.text = message
        setEmptyStateVisible(false)
        shimmerContainer.visibility = View.VISIBLE
        shimmerAnimation?.cancel()
        shimmerAnimation = AnimationUtils.loadAnimation(context, R.anim.shimmer_alpha)
        shimmerContainer.startAnimation(shimmerAnimation)
    }

    fun hideLoading() {
        shimmerContainer.clearAnimation()
        shimmerContainer.visibility = View.GONE
    }

    fun clearResults() {
        resultList.removeAllViews()
        lastResult = ""
        selectedResult = null
        updatePrimaryButtonLabel(false)
    }

    fun renderResults(replies: List<String>) {
        val sanitized = replies.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        resultList.removeAllViews()

        if (sanitized.isEmpty()) {
            lastResult = ""
            selectedResult = null
            updatePrimaryButtonLabel(false)
            return
        }

        setEmptyStateVisible(false)
        val palette = PanelTheme.palette
        sanitized.forEachIndexed { index, text ->
            val card = createResultCard(text, palette)
            val params = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                if (index < sanitized.lastIndex) {
                    bottomMargin = dpToPx(8f)
                }
            }
            resultList.addView(card, params)
        }

        lastResult = sanitized.first()
        selectedResult = sanitized.first()
        updateResultSelection(palette)
        updatePrimaryButtonLabel(true)
        // Show replace button when results are available
        btnReplace.visibility = View.VISIBLE
    }

    private fun buildReplyToneChips() {
        replyToneRow.removeAllViews()
        replyToneChips.clear()
        val palette = PanelTheme.palette
        ReplyToneFilter.values().forEach { tone ->
            val chip = TextView(context).apply {
                text = tone.label
                setPadding(dpToPx(10f), dpToPx(5f), dpToPx(10f), dpToPx(5f))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = createOutlinedChipDrawable(palette)
                setTextColor(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(6f)
                }
                setOnClickListener {
                    if (selectedReplyTone != tone) {
                        selectedReplyTone = tone
                        updateReplyToneVisuals()
                        onReplyToneSelected?.invoke(tone)
                    }
                }
            }
            replyToneRow.addView(chip)
            replyToneChips[tone] = chip
        }
        updateReplyToneVisuals()
    }

    private fun updateReplyToneVisuals() {
        val palette = PanelTheme.palette
        replyToneChips.forEach { (tone, chip) ->
            val selected = tone == selectedReplyTone && replyToneRow.visibility == View.VISIBLE
            chip.background = if (selected) {
                createFilledChipDrawable(palette, true)
            } else {
                createOutlinedChipDrawable(palette)
            }
            chip.setTextColor(
                if (selected) getContrastColor(palette.specialAccent) else palette.keyText
            )
        }
    }

    private fun renderChips() {
        chipRow.removeAllViews()
        chipViews.clear()

        val palette = PanelTheme.palette

        availableChips.forEach { option ->
            val chipView = createChipView(option, palette)
            chipRow.addView(chipView)
            chipViews[option] = chipView
        }

        chipRow.addView(btnAddMore)
        updateChipVisuals()
    }

    private fun wireClicks() {
        val backAction = { onBackPressed?.invoke() }
        backBtn.setOnClickListener { backAction() }
        btnKeyboard.setOnClickListener { backAction() }

        btnReplace.setOnClickListener {
            val candidate = selectedResult ?: lastResult
            if (!candidate.isNullOrBlank()) {
                onTextProcessed?.invoke(candidate)
            }
            backAction()
        }

        guideLink.setOnClickListener {
            // Toast removed - guide message logged only
        }

        btnAddMore.setOnClickListener {
            // Toast removed - guide message logged only
        }
    }

    private fun createChipView(option: ChipOption, palette: ThemePaletteV2): TextView {
        return TextView(context).apply {
            text = option.displayLabel
            setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            minHeight = dpToPx(32f)
            gravity = android.view.Gravity.CENTER
            background = createFilledChipDrawable(palette, option == selectedChip)
            setTextColor(if (option == selectedChip) getContrastColor(palette.specialAccent) else palette.keyText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val spacing = dpToPx(4f)
                setMargins(spacing, 0, spacing, 0)
            }
            setOnClickListener {
                val changed = selectedChip != option
                selectedChip = option
                updateChipVisuals()
                if (changed) {
                    onChipSelected?.invoke(option)
                } else {
                    // Even if same chip tapped, re-trigger processing to allow refresh
                    onChipSelected?.invoke(option)
                }
            }
        }
    }

    private fun createAddMoreChip(): TextView {
        return TextView(context).apply {
            text = "+  Add More To Keyboard"
            setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
            textSize = 11f
            minHeight = dpToPx(32f)
            gravity = android.view.Gravity.CENTER
            background = createOutlinedChipDrawable(PanelTheme.palette)
            setTextColor(PanelTheme.palette.keyText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val spacing = dpToPx(4f)
                setMargins(spacing, 0, spacing, 0)
            }
        }
    }

    private fun updateChipVisuals() {
        val palette = PanelTheme.palette
        chipViews.forEach { (option, view) ->
            val selected = option == selectedChip
            view.background = createFilledChipDrawable(palette, selected)
            view.setTextColor(
                if (selected) getContrastColor(palette.specialAccent) else palette.keyText
            )
        }
        btnAddMore.background = createOutlinedChipDrawable(palette)
        btnAddMore.setTextColor(palette.keyText)
    }

    private fun createResultCard(text: String, palette: ThemePaletteV2): TextView {
        return TextView(context).apply {
            this.text = text
            setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
            textSize = 13f
            setLineSpacing(0f, 1.1f)
            setTextColor(Color.WHITE)
            background = createResultDrawable(palette, text == selectedResult)
            setOnClickListener {
                selectedResult = text
                lastResult = text
                updateResultSelection(palette)
                onTextProcessed?.invoke(text)
            }
        }
    }

    private fun updateResultSelection(palette: ThemePaletteV2) {
        for (i in 0 until resultList.childCount) {
            val child = resultList.getChildAt(i) as? TextView ?: continue
            val isSelected = child.text.toString() == selectedResult
            child.background = createResultDrawable(palette, isSelected)
            child.setTextColor(
                if (isSelected) getContrastColor(palette.specialAccent) else Color.WHITE
            )
        }
    }

    private fun applyTheme(palette: ThemePaletteV2) {
        rootView.background = createPanelBackground(palette)
        header.setBackgroundColor(Color.TRANSPARENT)
        heroTitle.setTextColor(Color.WHITE)
        heroSubtitle.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 200))
        statusText.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 180))
        langContainer.background = createLanguageChipDrawable(palette)
        langChip.setTextColor(Color.WHITE)
        guideLink.setTextColor(palette.specialAccent)

        btnReplace.background = createAccentButtonDrawable(palette)
        btnReplace.setTextColor(getContrastColor(palette.specialAccent))
        ImageViewCompat.setImageTintList(btnKeyboard, ColorStateList.valueOf(palette.specialAccent))

        updateChipVisuals()
        updateResultSelection(palette)
        updateReplyToneVisuals()
    }

    private fun setEmptyStateVisible(visible: Boolean) {
        emptyState.visibility = if (visible) View.VISIBLE else View.GONE
        resultList.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun updatePrimaryButtonLabel(forceHasResult: Boolean? = null) {
        val hasResult = forceHasResult ?: hasRenderableResult()
        val labelRes = if (hasResult) {
            R.string.ai_panel_button_replace
        } else {
            R.string.ai_panel_button_back
        }
        btnReplace.text = context.getString(labelRes)
    }

    private fun hasRenderableResult(): Boolean {
        return lastResult.isNotBlank() || !selectedResult.isNullOrBlank()
    }

    private fun createPanelBackground(palette: ThemePaletteV2): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(palette.keyboardBg)
        }
    }

    private fun createLanguageChipDrawable(palette: ThemePaletteV2): GradientDrawable {
        val fillColor = ColorUtils.blendARGB(palette.keyboardBg, Color.WHITE, 0.12f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPxF(18f)
            setColor(fillColor)
            setStroke(
                maxOf(1, (palette.keyBorderWidth * density).toInt()),
                ColorUtils.setAlphaComponent(palette.specialAccent, 200)
            )
        }
    }

    private fun createFilledChipDrawable(palette: ThemePaletteV2, selected: Boolean): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dpToPxF(18f)
        val baseColor = if (selected) palette.specialAccent else lighten(palette.keyBg, 0.12f)
        drawable.setColor(baseColor)
        if (!selected && palette.keyBorderEnabled) {
            drawable.setStroke(
                maxOf(1, (palette.keyBorderWidth * density).toInt()),
                palette.keyBorderColor
            )
        }
        return drawable
    }

    private fun createOutlinedChipDrawable(palette: ThemePaletteV2): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dpToPxF(18f)
        drawable.setColor(Color.TRANSPARENT)
        drawable.setStroke(maxOf(1, (palette.keyBorderWidth * density).toInt()), palette.keyText)
        return drawable
    }

    private fun createResultDrawable(palette: ThemePaletteV2, selected: Boolean): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dpToPxF(12f)
        val baseColor = if (selected) palette.specialAccent else lighten(palette.keyBg, 0.06f)
        drawable.setColor(baseColor)
        if (!selected && palette.keyBorderEnabled) {
            drawable.setStroke(
                maxOf(1, (palette.keyBorderWidth * density).toInt()),
                palette.keyBorderColor
            )
        }
        return drawable
    }

    private fun createAccentButtonDrawable(palette: ThemePaletteV2): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(palette.specialAccent)
            cornerRadius = dpToPxF(24f)
        }
    }

    private fun lighten(color: Int, fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        return ColorUtils.blendARGB(color, Color.WHITE, clamped * 0.5f)
    }

    private fun dpToPx(value: Float): Int = (value * density + 0.5f).toInt()
    private fun dpToPxF(value: Float): Float = value * density

    private fun getContrastColor(color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color)
        return if (luminance < 0.5f) Color.WHITE else Color.BLACK
    }
    
    /**
     * Override onTouchEvent to consume all touch events and prevent them from
     * passing through to the keyboard underneath
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        // Consume all touch events to prevent keyboard input
        performClick()
        return true
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
