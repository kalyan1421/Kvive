package com.kvive.keyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.*
import android.text.TextUtils
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.core.content.ContextCompat
import androidx.annotation.DrawableRes
import android.os.SystemClock
import com.kvive.keyboard.stickers.SimpleMediaPanel
import com.kvive.keyboard.themes.PanelTheme
import com.kvive.keyboard.themes.ThemePaletteV2
import com.kvive.keyboard.utils.LogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Unified Panel Manager V2 - 100% Dynamic, Zero XML
 * Single Source of Truth for All Panels
 * Consolidates: Grammar, Tone, AI Assistant, Emoji, Clipboard, and Settings
 * 
 * Key Features:
 * - Pure programmatic UI (no XML layouts)
 * - Lazy loading of panels (only create when needed)
 * - Consistent theming via ThemeManager
 * - Unified height management via KeyboardHeightManager
 * - Dynamic panel switching with smooth transitions
 * - Broadcast integration for theme/prompt updates
 * - Theme change listener for live rebuilds
 */
class UnifiedPanelManager(
    private val context: Context,
    private val themeManager: ThemeManager,
    private val keyboardHeightManager: KeyboardHeightManager? = null,
    private val inputConnectionProvider: () -> InputConnection? = { null },
    private val onBackToKeyboard: () -> Unit = {}
) {
    
    companion object {
        private const val TAG = "UnifiedPanelManager"
        private const val PANEL_HEIGHT_DP = 280
    }
    
    /**
     * Panel types supported by UnifiedPanelManager
     */
    enum class PanelType {
        GRAMMAR,
        TONE,
        AI_ASSISTANT,
        TRANSLATE,
        CLIPBOARD,
        EMOJI,
        SETTINGS
    }

    private data class LanguageOption(val label: String, val value: String)

    private data class LanguageConfig(
        val options: List<LanguageOption>,
        var current: LanguageOption,
        val onChanged: (LanguageOption) -> Unit
    )

    private data class GrammarResultViews(
        val container: LinearLayout,
        val primary: TextView,
        val translation: TextView,
        val replaceButton: Button
    )

    private data class ToneResultViews(
        val container: LinearLayout,
        val primary: TextView,
        val translation: TextView
    )

    private enum class QuickSettingType {
        ACTION,
        TOGGLE
    }

    private data class QuickSettingItem(
        val id: String,
        val label: String,
        @DrawableRes val iconRes: Int,
        val type: QuickSettingType,
        var isActive: Boolean = false,
        val handler: (QuickSettingItem) -> Unit
    )
// ——— Better, production-grade prompts ———

private enum class GrammarAction(
    val label: String,
    val description: String,
    val feature: AdvancedAIService.ProcessingFeature? = null,
    val customPrompt: String? = null
) {
    REPHRASE(
        label = "Rephrase",
        description = "Say the same thing more naturally",
        customPrompt =
            """
            You are a writing assistant. Rewrite the user's text in the **same language** it was provided.
            Goals:
            • Keep the original meaning and facts exactly.
            • Improve clarity, flow, and naturalness (concise, readable).
            • Preserve any structure (line breaks, lists, bullets, punctuation style).

            Hard rules:
            • Do NOT add or remove facts.
            • Do NOT translate to another language.
            • Do NOT include quotes, code fences, or explanations.
            • Output only the rewritten text.
            """.trimIndent()
    ),

    GRAMMAR_FIX(
        label = "Grammar Fix",
        description = "Fix grammar, spelling, and punctuation",
        feature = AdvancedAIService.ProcessingFeature.GRAMMAR_FIX
    ),

    ADD_EMOJIS(
        label = "Add emojis",
        description = "Add relevant emojis inline",
        customPrompt =
            """
            Rewrite the user's text in the **same language** it was provided and keep the original meaning.
            Add 1–3 contextually relevant emojis **inline** (place them near the words they enhance, not all at the end).
            Preserve names, numbers, URLs, hashtags, and formatting.

            Hard rules:
            • Do NOT overuse emojis (max 3).
            • Do NOT translate to another language.
            • Do NOT add or remove facts.
            • Output only the revised text (no quotes or explanations).
            """.trimIndent()
    )
}

private enum class ToneAction(
    val label: String,
    val prompt: String
) {
    FUNNY(
        "Funny",
        """
        Rewrite in the **same language** with light, witty humor. Keep the original meaning and facts.
        Use playful phrasing or a subtle joke or two; avoid sarcasm unless the context clearly supports it.
        Do NOT add emojis unless already present. Output text only (no quotes/explanations).
        """.trimIndent()
    ),

    POETIC(
        "Poetic",
        """
        Rewrite in the **same language** with a lyrical, imagery-rich style while preserving the original meaning.
        Aim for elegant rhythm and vivid but tasteful metaphors. Avoid clichés. Output text only.
        """.trimIndent()
    ),

    SHORTEN(
        "Shorten",
        """
        Condense the text in the **same language** by ~30–40% while preserving all key information and intent.
        Remove filler words, redundancies, and hedging. Keep lists/bullets if present. Output text only.
        """.trimIndent()
    ),

    SARCASTIC(
        "Sarcastic",
        """
        Rewrite in the **same language** with mild, witty sarcasm while maintaining facts and intent.
        Keep it clever (not hostile). Be concise. Do NOT add emojis. Output text only.
        """.trimIndent()
    )
}

    // ✅ REFACTORED: No container management - caller handles display
    private var currentPanelType: PanelType? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val unifiedAIService = UnifiedAIService(context)
    
    // Panel views (lazy initialization)
    private var grammarPanelView: View? = null
    private var tonePanelView: View? = null
    private var aiAssistantPanelView: View? = null
    private var translatePanelView: View? = null
    private var clipboardPanelView: View? = null
    private var emojiPanelController: EmojiPanelController? = null
    private var emojiPanelView: View? = null
    private var settingsPanelView: View? = null
    private var simpleMediaPanel: SimpleMediaPanel? = null
    private var emojiSearchModeListener: ((Boolean) -> Unit)? = null
    
    // Services
    private val advancedAIService = AdvancedAIService(context)
    
    // Callbacks
    private var onTextProcessedCallback: ((String) -> Unit)? = null
    
    // Input text for AI processing
    private var currentInputText: String = ""
    private var grammarHeroView: View? = null
    private var toneHeroView: View? = null
    private var grammarResultCard: GrammarResultViews? = null
    private var grammarDescriptionView: TextView? = null
    private var grammarShimmerContainer: LinearLayout? = null
    private var toneResultCards: List<ToneResultViews> = emptyList()
    private var toneShimmerContainer: LinearLayout? = null
    private var aiAssistantStatusText: TextView? = null
    private var aiAssistantResultContainer: LinearLayout? = null
    private var aiAssistantReplaceButton: Button? = null
    private var aiAssistantEmptyState: View? = null
    private var aiAssistantShimmerContainer: LinearLayout? = null
    private var aiAssistantChipRow: LinearLayout? = null
    private var aiAssistantReplyToneRow: LinearLayout? = null
    private var grammarChipScroll: HorizontalScrollView? = null
    private var toneChipScroll: HorizontalScrollView? = null
    private var aiChipScroll: HorizontalScrollView? = null
    private var aiAssistantPrompts: List<PromptManager.PromptItem> = emptyList()
    private var aiAssistantChipViews: MutableList<TextView> = mutableListOf()
    private var aiSelectedReplyTone: String = "Positive"
    private var lastAiResult: String = ""
    private var grammarEmptyState: View? = null
    private var toneEmptyState: View? = null
    private var grammarKeyboardButton: View? = null
    private var toneKeyboardButton: View? = null
    private var aiKeyboardButton: View? = null
    private val grammarChipViews: MutableList<TextView> = mutableListOf()
    private val toneChipViews: MutableList<TextView> = mutableListOf()
    private val grammarChipActions = mutableMapOf<TextView, () -> Unit>()
    private val toneChipActions = mutableMapOf<TextView, () -> Unit>()
    private var selectedGrammarChip: TextView? = null
    private var selectedToneChip: TextView? = null
    
    private val supportedLanguages = listOf(
        LanguageOption("English", "English"),
        LanguageOption("Hindi", "Hindi"),
        LanguageOption("Telugu", "Telugu"),
        LanguageOption("Spanish", "Spanish"),
        LanguageOption("French", "French"),
        LanguageOption("German", "German"),
        LanguageOption("Japanese", "Japanese")
    )
    private var grammarLanguage = supportedLanguages.first()
    private var toneLanguage = supportedLanguages.first()
    private val translateLanguageOptions = listOf(
        LanguageOption("Arabic", "Arabic"),
        LanguageOption("Bosnian", "Bosnian"),
        LanguageOption("Bulgarian", "Bulgarian"),
        LanguageOption("Bangla", "Bangla"),
        LanguageOption("Catalan", "Catalan"),
        LanguageOption("Chinese", "Chinese"),
        LanguageOption("Dutch", "Dutch"),
        LanguageOption("English", "English"),
        LanguageOption("French", "French"),
        LanguageOption("German", "German"),
        LanguageOption("Hindi", "Hindi"),
        LanguageOption("Italian", "Italian"),
        LanguageOption("Japanese", "Japanese"),
        LanguageOption("Korean", "Korean"),
        LanguageOption("Portuguese", "Portuguese"),
        LanguageOption("Russian", "Russian"),
        LanguageOption("Spanish", "Spanish"),
        LanguageOption("Telugu", "Telugu"),
        LanguageOption("Turkish", "Turkish")
    )
    private var translateLanguage = translateLanguageOptions.first()
    private var translateEmptyState: View? = null
    private var translateResultContainer: LinearLayout? = null
    private var translateStatusText: TextView? = null
    private var translateOutputView: TextView? = null
    private var translateReplaceButton: Button? = null
    private var translateCopyButton: ImageButton? = null
    private var translateLanguageLabel: TextView? = null
    private var translateGuideLink: TextView? = null
    private var translateKeyboardButton: ImageButton? = null
    private var grammarTranslationJob: Job? = null
    private val toneTranslationJobs = mutableListOf<Job>()
    private var translateJob: Job? = null
    private var lastGrammarResult: String = ""
    private val lastToneResults = mutableListOf<String>()
    private var lastGrammarRequestedInput: String = ""
    private var lastToneRequestedInput: String = ""
    private var lastTranslateRequestedInput: String = ""
    
    init {
        // Register theme change listener to rebuild panels dynamically
        themeManager.addThemeChangeListener(object : ThemeManager.ThemeChangeListener {
            override fun onThemeChanged(theme: com.kvive.keyboard.themes.KeyboardThemeV2, palette: com.kvive.keyboard.themes.ThemePaletteV2) {
                LogUtil.d(TAG, "Theme changed - rebuilding panels")
                rebuildDynamicPanelsFromPrompts()
            }
        })
    }
    
    /**
     * ✅ NEW API: Build panel view (caller manages display)
     * Returns a panel view ready to be displayed by UnifiedKeyboardView
     * 
     * @param type The panel type to build
     * @return Panel view ready for display
     */
    fun buildPanel(type: PanelType): View {
        LogUtil.d(TAG, "Building panel: $type")
        
        currentPanelType = type
        
        // Get or create the requested panel
        val panelView = when (type) {
            PanelType.GRAMMAR -> getOrCreateGrammarPanel()
            PanelType.TONE -> getOrCreateTonePanel()
            PanelType.AI_ASSISTANT -> getOrCreateAIAssistantPanel()
            PanelType.TRANSLATE -> getOrCreateTranslatePanel()
            PanelType.CLIPBOARD -> getOrCreateClipboardPanel()
            PanelType.EMOJI -> getOrCreateEmojiPanel()
            PanelType.SETTINGS -> getOrCreateSettingsPanel()
        }
        
        LogUtil.d(TAG, "✅ Panel $type built successfully")
        return panelView
    }
    
    /**
     * Check if a panel is currently visible
     */
    fun isPanelVisible(): Boolean {
        return currentPanelType != null
    }
    
    
   
    
    /**
     * Get current panel type
     */
    fun getCurrentPanelType(): PanelType? = currentPanelType
    
    /**
     * Set listener for emoji search mode changes
     */
    fun setEmojiSearchModeListener(listener: (Boolean) -> Unit) {
        emojiSearchModeListener = listener
    }
    
    fun markPanelClosed() {
        if (currentPanelType != null) {
            LogUtil.d(TAG, "Panel closed: $currentPanelType")
        }
        currentPanelType = null
    }

    fun invalidateClipboardPanel() {
        clipboardPanelView = null
    }
    
    /**
     * Append character to emoji search query
     */
    fun appendEmojiSearchCharacter(char: Char) {
        emojiPanelController?.appendToSearchQuery(char)
    }
    
    /**
     * Remove last character from emoji search query
     */
    fun removeEmojiSearchCharacter() {
        emojiPanelController?.removeLastFromSearchQuery()
    }
    
    /**
     * Clear emoji search query
     */
    fun clearEmojiSearch() {
        emojiPanelController?.clearSearchQuery()
    }
    
    /**
     * Check if emoji panel is in search mode
     */
    fun isEmojiSearchMode(): Boolean {
        return emojiPanelController?.isInSearchMode() ?: false
    }
    
    /**
     * Get the emoji search keyboard container
     * Returns the FrameLayout where UnifiedKeyboardView should render the keyboard grid
     */
    fun getEmojiSearchKeyboardContainer(): FrameLayout? {
        return emojiPanelController?.getSearchKeyboardContainer()
    }
    
    /**
     * Refresh AI prompts (called when broadcast received)
     */
    fun refreshAIPrompts() {
        LogUtil.d(TAG, "Refreshing AI prompts - rebuilding dynamic panels")
        rebuildDynamicPanelsFromPrompts()
    }
    
    /**
     * Rebuild all dynamic panels from scratch (for theme changes and prompt updates)
     * ✅ REFACTORED: Clears cached panels - caller must re-request panel if needed
     */
    fun rebuildDynamicPanelsFromPrompts() {
        // Clear cached panels (will be rebuilt on next buildPanel() call)
        grammarPanelView = null
        tonePanelView = null
        aiAssistantPanelView = null
        translatePanelView = null
        clipboardPanelView = null
        settingsPanelView = null
        grammarHeroView = null
        toneHeroView = null
        grammarResultCard = null
        grammarDescriptionView = null
        toneResultCards = emptyList()
        grammarTranslationJob?.cancel()
        grammarTranslationJob = null
        toneTranslationJobs.forEach { it.cancel() }
        toneTranslationJobs.clear()
        lastGrammarResult = ""
        lastToneResults.clear()
        aiAssistantStatusText = null
        aiAssistantResultContainer = null
        aiAssistantReplaceButton = null
        aiAssistantEmptyState = null
        aiAssistantShimmerContainer = null
        aiAssistantChipRow = null
        aiAssistantReplyToneRow = null
        aiAssistantPrompts = emptyList()
        aiAssistantChipViews.clear()
        aiSelectedReplyTone = "Positive"
        lastAiResult = ""
        grammarEmptyState = null
        toneEmptyState = null
        grammarKeyboardButton = null
        toneKeyboardButton = null
        aiKeyboardButton = null
        translateEmptyState = null
        translateResultContainer = null
        translateStatusText = null
        translateOutputView = null
        translateReplaceButton = null
        translateCopyButton = null
        translateLanguageLabel = null
        translateGuideLink = null
        translateKeyboardButton = null
        lastGrammarRequestedInput = ""
        lastToneRequestedInput = ""
        translateJob?.cancel()
        translateJob = null
        lastTranslateRequestedInput = ""
        translateLanguage = translateLanguageOptions.first()
        grammarChipViews.clear()
        toneChipViews.clear()
        grammarChipActions.clear()
        toneChipActions.clear()
        selectedGrammarChip = null
        selectedToneChip = null
        
        // Rebuild emoji panel controller if it exists
        emojiPanelController?.applyTheme()
        
        // ✅ Note: Caller (UnifiedKeyboardView) will need to rebuild current panel if visible
        
        LogUtil.d(TAG, "✅ Dynamic panels rebuilt (cached cleared)")
    }
    
    /**
     * Apply theme to panel (called when theme changes)
     */
    fun applyTheme(theme: com.kvive.keyboard.themes.KeyboardThemeV2) {
        LogUtil.d(TAG, "Applying theme to all panels...")
        rebuildDynamicPanelsFromPrompts()
        emojiPanelController?.applyTheme()
        LogUtil.d(TAG, "✅ Theme applied to panels")
    }
    
    /**
     * Set input text for AI panels
     */
    fun setInputText(text: String) {
        currentInputText = text
        updateTextDependentPanels()
        LogUtil.d(TAG, "Input text set: ${text.take(50)}...")
    }

    private fun updateTextDependentPanels() {
        val hasInput = currentInputText.trim().isNotEmpty()

        // Update AI Assistant state
        updateAIAssistantState()

        if (!hasInput) {
            grammarEmptyState?.visibility = View.VISIBLE
            grammarChipScroll?.visibility = View.GONE
            grammarDescriptionView?.visibility = View.GONE
            grammarHeroView?.visibility = View.GONE
            grammarResultCard?.let { card ->
                card.container.visibility = View.GONE
                card.primary.text = context.getString(R.string.panel_prompt_grammar)
                card.translation.visibility = View.GONE
                card.translation.text = ""
                card.replaceButton.isEnabled = false
            }
            grammarShimmerContainer?.clearAnimation()
            grammarShimmerContainer?.visibility = View.GONE
            grammarKeyboardButton?.visibility = View.GONE
            grammarTranslationJob?.cancel()
            grammarTranslationJob = null
            lastGrammarResult = ""
            lastGrammarRequestedInput = ""

            toneEmptyState?.visibility = View.VISIBLE
            toneChipScroll?.visibility = View.GONE
            toneHeroView?.visibility = View.GONE
            toneResultCards.forEach { card ->
                card.container.visibility = View.GONE
                card.primary.text = context.getString(R.string.panel_prompt_tone)
                card.translation.visibility = View.GONE
                card.translation.text = ""
            }
            toneShimmerContainer?.clearAnimation()
            toneShimmerContainer?.visibility = View.GONE
            toneKeyboardButton?.visibility = View.GONE
            toneTranslationJobs.forEach { it.cancel() }
            toneTranslationJobs.clear()
            lastToneResults.clear()
            lastToneRequestedInput = ""
        } else {
            grammarEmptyState?.visibility = View.GONE
            grammarChipScroll?.visibility = View.VISIBLE
            grammarDescriptionView?.visibility = View.VISIBLE
            grammarKeyboardButton?.visibility = View.VISIBLE
            grammarResultCard?.let { card ->
                if (card.primary.text.isNullOrBlank() ||
                    card.primary.text.toString() == context.getString(R.string.panel_prompt_grammar)
                ) {
                    card.primary.text = context.getString(R.string.panel_result_pending)
                }
            }

            toneEmptyState?.visibility = View.GONE
            toneChipScroll?.visibility = View.VISIBLE
            toneKeyboardButton?.visibility = View.VISIBLE
            toneResultCards.forEachIndexed { index, card ->
                if (card.primary.text.isNullOrBlank() ||
                    card.primary.text.toString() == context.getString(R.string.panel_prompt_tone)
                ) {
                    card.primary.text = "Variation ${index + 1} will appear here..."
                }
            }

            val trimmed = currentInputText.trim()
            if (trimmed.isNotEmpty() && trimmed != lastGrammarRequestedInput) {
                triggerDefaultGrammarAction()
            }
            if (trimmed.isNotEmpty() && trimmed != lastToneRequestedInput) {
                triggerDefaultToneAction()
            }
        }

        updateTranslateState(hasInput)
    }

    private fun updateTranslateState(hasInput: Boolean) {
        if (!hasInput) {
            translateEmptyState?.visibility = View.VISIBLE
            translateGuideLink?.visibility = View.VISIBLE
            translateResultContainer?.visibility = View.GONE
            translateReplaceButton?.visibility = View.GONE
            translateReplaceButton?.isEnabled = false
            translateCopyButton?.isEnabled = false
            translateCopyButton?.alpha = 0.4f
            translateStatusText?.text = "Select text to translate"
            translateOutputView?.text = "Translation will appear here..."
            translateJob?.cancel()
            translateJob = null
            lastTranslateRequestedInput = ""
            return
        }

        translateEmptyState?.visibility = View.GONE
        translateGuideLink?.visibility = View.GONE
        translateResultContainer?.visibility = View.VISIBLE
        translateReplaceButton?.visibility = View.VISIBLE

        val currentText = translateOutputView?.text?.toString()?.trim().orEmpty()
        val hasResult = currentText.isNotBlank() &&
            !currentText.startsWith("Translation", ignoreCase = true) &&
            !currentText.startsWith("⏳") &&
            !currentText.startsWith("❌")
        translateReplaceButton?.isEnabled = hasResult
        translateCopyButton?.isEnabled = hasResult
        translateCopyButton?.alpha = if (hasResult) 1f else 0.4f

        if (currentPanelType == PanelType.TRANSLATE && translatePanelView != null) {
            triggerTranslateForCurrentLanguage(force = !hasResult)
        }
    }

    private fun triggerTranslateForCurrentLanguage(force: Boolean = false) {
        val trimmed = currentInputText.trim()
        if (trimmed.isEmpty()) return
        if (!force && trimmed == lastTranslateRequestedInput) return

        lastTranslateRequestedInput = trimmed
        processTranslateAction(translateLanguage.value)
    }

    private fun copyTranslateResult() {
        val text = translateOutputView?.text?.toString()?.trim().orEmpty()
        if (text.isBlank() ||
            text.startsWith("Translation", ignoreCase = true) ||
            text.startsWith("⏳") ||
            text.startsWith("❌")
        ) {
            // Toast removed - copy error logged only
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Translation", text))
        // Toast removed - copy success logged only
    }
    
    private fun triggerDefaultGrammarAction() {
        val palette = PanelTheme.palette
        val firstChip = grammarChipViews.firstOrNull() ?: return
        updateChipGroupSelection(grammarChipViews, firstChip, palette)
        selectedGrammarChip = firstChip
        grammarChipActions[firstChip]?.invoke()
    }

    private fun triggerDefaultToneAction() {
        val palette = PanelTheme.palette
        val firstChip = toneChipViews.firstOrNull() ?: return
        updateChipGroupSelection(toneChipViews, firstChip, palette)
        selectedToneChip = firstChip
        toneChipActions[firstChip]?.invoke()
    }
    
    /**
     * Set callback for processed text
     */
    fun setOnTextProcessedListener(listener: (String) -> Unit) {
        onTextProcessedCallback = listener
    }
    
    // ========================================
    // PRIVATE: Panel Creation Methods
    // ========================================
    // ✅ REMOVED: hideCurrentPanel() and getCurrentPanelView()
    // UnifiedKeyboardView now manages panel visibility
    
    private fun getOrCreateGrammarPanel(): View {
        if (grammarPanelView == null) {
            grammarPanelView = createGrammarPanel()
        }
        return grammarPanelView!!
    }
    
    private fun getOrCreateTonePanel(): View {
        if (tonePanelView == null) {
            tonePanelView = createTonePanel()
        }
        return tonePanelView!!
    }
    
    private fun getOrCreateAIAssistantPanel(): View {
        if (aiAssistantPanelView == null) {
            aiAssistantPanelView = createAIAssistantPanel()
        }
        return aiAssistantPanelView!!
    }

    private fun getOrCreateTranslatePanel(): View {
        if (translatePanelView == null) {
            translatePanelView = createTranslatePanel()
        }
        return translatePanelView!!
    }
    
    private fun getOrCreateClipboardPanel(): View {
        if (clipboardPanelView == null) {
            clipboardPanelView = createClipboardPanel()
        }
        return clipboardPanelView!!
    }
    
    private fun getOrCreateEmojiPanel(): View {
        if (simpleMediaPanel == null) {
            emojiPanelController = EmojiPanelController(
                context,
                themeManager,
                onBackToKeyboard,
                inputConnectionProvider
            ) { active ->
                emojiSearchModeListener?.invoke(active)
            }
            simpleMediaPanel = SimpleMediaPanel(context, themeManager, emojiPanelController!!)
            emojiPanelView = simpleMediaPanel
        }
        simpleMediaPanel?.show()
        return emojiPanelView!!
    }
    
    private fun getOrCreateSettingsPanel(): View {
        if (settingsPanelView == null) {
            settingsPanelView = createSettingsPanel()
        }
        return settingsPanelView!!
    }
    
    // ========================================
    // PROGRAMMATIC PANEL BUILDERS (NO XML!)
    // ========================================
    
    /**
     * Create Grammar Panel - Pure Kotlin UI
     */
    private fun createGrammarPanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        val languageConfig = LanguageConfig(supportedLanguages, grammarLanguage) { option ->
            grammarLanguage = option
            refreshGrammarTranslation()
        }

        val contentRoot = createPanelRoot(palette, height)
        contentRoot.addView(createPanelHeader("Fix Grammar", languageConfig))

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        scrollView.addView(content)
        contentRoot.addView(scrollView)

        grammarHeroView = null
        val emptyState = createEmptyState(
            title = "Type Something",
            subtitle = "Tap the Grammar toolbar icon to fix your text",
            palette = palette,
            iconRes = R.drawable.grammar_icon
        )
        grammarEmptyState = emptyState
        content.addView(emptyState)

        val chipScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dpToPx(8))
            visibility = View.GONE
        }
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipScroll.addView(chipRow)
        content.addView(chipScroll)
        grammarChipScroll = chipScroll
        grammarChipViews.clear()
        grammarChipActions.clear()
        selectedGrammarChip = null

        val actionDescription = TextView(context).apply {
            text = GrammarAction.REPHRASE.description
            setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 180))
            textSize = 11f
            setPadding(0, 0, 0, dpToPx(12))
            visibility = View.GONE
        }
        grammarDescriptionView = actionDescription
        content.addView(actionDescription)

        val grammarShimmer = createShimmerContainer(palette)
        grammarShimmerContainer = grammarShimmer
        content.addView(grammarShimmer)

        val resultCard = createGrammarResultCard(palette)
        resultCard.container.visibility = View.GONE
        grammarResultCard = resultCard
        resultCard.replaceButton.isEnabled = false
        resultCard.replaceButton.setOnClickListener {
            val resolved = resolveTextForLanguage(
                resultCard.primary.text.toString(),
                resultCard.translation.text,
                grammarLanguage
            )
            if (resolved.isNotBlank() && !resolved.startsWith("Result") && !resolved.startsWith("⏳")) {
                onTextProcessedCallback?.invoke(resolved)
                onBackToKeyboard()
            }
        }
        content.addView(resultCard.container)

        GrammarAction.values().forEach { action ->
            val isFirst = grammarChipViews.isEmpty()
            val chip = createSelectableChipPill(action.label, palette, isFirst)
            registerGrammarChip(chip, palette, isFirst) {
                grammarDescriptionView?.text = action.description
                processGrammarAction(action)
            }
            chipRow.addView(chip)
        }

        val customPrompts = PromptManager.getPrompts("grammar")
        customPrompts.forEach { prompt ->
            val chip = createSelectableChipPill(prompt.title, palette, false)
            registerGrammarChip(chip, palette, false) {
                grammarDescriptionView?.text = prompt.title
                processGrammarCustomPrompt(prompt.prompt, prompt.title)
            }
            chipRow.addView(chip)
        }
        chipRow.addView(createAddPromptChip(palette, "grammar"))

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        contentRoot.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(contentRoot)

        val keyboardButton = createFloatingKeyboardButton(palette).apply {
            visibility = View.GONE
        }
        grammarKeyboardButton = keyboardButton
        container.addView(keyboardButton, createKeyboardButtonLayoutParams())

        updateTextDependentPanels()
        return container
    }
    
    /**
     * Create Tone Panel - Pure Kotlin UI
     */
    private fun createTonePanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        val languageConfig = LanguageConfig(supportedLanguages, toneLanguage) { option ->
            toneLanguage = option
            refreshToneTranslations()
        }

        val contentRoot = createPanelRoot(palette, height)
        contentRoot.addView(createPanelHeader("Word Tone", languageConfig))

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        scrollView.addView(content)
        contentRoot.addView(scrollView)

        toneHeroView = null
        val emptyState = createEmptyState(
            title = "Type Something",
            subtitle = "Tap the Tone toolbar icon for rewrites",
            palette = palette,
            iconRes = R.drawable.tone_icon
        )
        toneEmptyState = emptyState
        content.addView(emptyState)

        val chipScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            setPadding(0, dpToPx(12), 0, dpToPx(8))
            visibility = View.GONE
        }
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipScroll.addView(chipRow)
        content.addView(chipScroll)
        toneChipScroll = chipScroll
        toneChipViews.clear()
        toneChipActions.clear()
        selectedToneChip = null

        val toneShimmer = createShimmerContainer(palette)
        toneShimmerContainer = toneShimmer
        content.addView(toneShimmer)

        val cards = mutableListOf<ToneResultViews>()
        repeat(3) {
            val card = createToneResultCard(palette)
            card.container.visibility = View.GONE
            cards.add(card)
            content.addView(card.container)
            content.addView(spacerView(dpToPx(8)))
        }
        if (content.childCount > 0) {
            content.removeViewAt(content.childCount - 1)
        }
        toneResultCards = cards
        lastToneResults.clear()
        repeat(cards.size) { lastToneResults.add("") }
        cards.forEachIndexed { index, card ->
            card.container.setOnClickListener {
                val resolved = resolveTextForLanguage(
                    card.primary.text.toString(),
                    card.translation.text,
                    toneLanguage
                )
                if (resolved.isNotBlank() && !resolved.startsWith("Variation")) {
                    onTextProcessedCallback?.invoke(resolved)
                    onBackToKeyboard()
                }
            }
        }

        ToneAction.values().forEach { action ->
            val isFirst = toneChipViews.isEmpty()
            val chip = createSelectableChipPill(action.label, palette, isFirst)
            registerToneChip(chip, palette, isFirst) {
                processToneAction(action)
            }
            chipRow.addView(chip)
        }

        val customPrompts = PromptManager.getPrompts("tone")
        customPrompts.forEach { prompt ->
            val chip = createSelectableChipPill(prompt.title, palette, false)
            registerToneChip(chip, palette, false) {
                processCustomTonePrompt(prompt.prompt, prompt.title)
            }
            chipRow.addView(chip)
        }
        chipRow.addView(createAddPromptChip(palette, "tone"))

        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        contentRoot.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(contentRoot)

        val keyboardButton = createFloatingKeyboardButton(palette).apply {
            visibility = View.GONE
        }
        toneKeyboardButton = keyboardButton
        container.addView(keyboardButton, createKeyboardButtonLayoutParams())

        updateTextDependentPanels()
        return container
    }
    
    /**
     * Create AI Assistant Panel - Pure Kotlin UI matching reference images
     */
    private fun createAIAssistantPanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        
        val panelBg = if (themeManager.isImageBackground()) palette.panelSurface else palette.keyboardBg
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(panelBg)  // Solid color instead of gradient
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(14))
        }
        root.addView(mainLayout)

        // Header
        mainLayout.addView(createPanelHeader("AI Writing Assistance"))

        // Chip row for AI prompts
        val chipScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dpToPx(8))
        }
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipScroll.addView(chipRow)
        mainLayout.addView(chipScroll)
        aiChipScroll = chipScroll

        // Reply tone filters row (hidden by default)
        val replyToneRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dpToPx(8))
            visibility = View.GONE
        }
        mainLayout.addView(replyToneRow)

        // Scrollable content area
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(content)
        mainLayout.addView(scrollView)

        // Empty state (Type Something)
        val emptyState = createEmptyState(
            title = "Type Something",
            subtitle = "Tap the AI toolbar icon to ask for help",
            palette = palette,
            iconRes = R.drawable.chatgpt_icon
        )
        content.addView(emptyState)

        // Status text
        val statusText = TextView(context).apply {
            textSize = 11f
            setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 180))
            setPadding(0, dpToPx(4), 0, dpToPx(12))
            visibility = View.GONE
        }
        content.addView(statusText)

        // Shimmer container (for loading state)
        val shimmerContainer = createShimmerContainer(palette)
        content.addView(shimmerContainer)

        // Result container
        val resultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(resultContainer)

        // Replace button
        val replaceButton = Button(context).apply {
            text = "Replace Text"
            isAllCaps = false
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createAccentButtonBackground(palette, 24)
            setTextColor(getContrastColor(palette.specialAccent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            ).apply {
                topMargin = dpToPx(12)
            }
            visibility = View.GONE
            setOnClickListener {
                val result = lastAiResult
                if (result.isNotBlank()) {
                    onTextProcessedCallback?.invoke(result)
                    onBackToKeyboard()
                }
            }
        }
        content.addView(replaceButton)

        // Removed guide link per user request

        // Load prompts and setup chips
        val customPrompts = PromptManager.getPrompts("assistant")
        var selectedPromptIndex = 0
        val chipViews = mutableListOf<TextView>()
        
        if (customPrompts.isEmpty()) {
            val noPromptsMessage = TextView(context).apply {
                text = "No AI prompts added. Add from app."
                textSize = 11f
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 160))
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            }
            chipRow.addView(noPromptsMessage)
        } else {
            customPrompts.forEachIndexed { index, prompt ->
                val chip = createSelectableChipPill(prompt.title, palette, index == 0)
                chip.setOnClickListener {
                    selectedPromptIndex = index
                    // Update chip selection visuals
                    chipViews.forEachIndexed { i, view ->
                        updateChipSelection(view, i == selectedPromptIndex, palette)
                    }
                    // Show/hide reply tone filters
                    replyToneRow.visibility = if (prompt.title.equals("Reply", ignoreCase = true)) {
                        View.VISIBLE
        } else {
                        View.GONE
                    }
                    // Auto-process if there's input
                    if (currentInputText.trim().isNotEmpty()) {
                        processAIPromptWithShimmer(
                            prompt.prompt,
                            prompt.title,
                            statusText,
                            shimmerContainer,
                            resultContainer,
                            replaceButton,
                            emptyState
                        )
                    }
                }
                chipRow.addView(chip)
                chipViews.add(chip)
            }
        }
        chipRow.addView(createAddPromptChip(palette, "assistant"))

        // Setup reply tone filters
        setupReplyToneFilters(replyToneRow, palette)

        // Store references
        aiAssistantStatusText = statusText
        aiAssistantResultContainer = resultContainer
        aiAssistantReplaceButton = replaceButton
        aiAssistantEmptyState = emptyState
        aiAssistantShimmerContainer = shimmerContainer
        aiAssistantChipRow = chipRow
        aiAssistantReplyToneRow = replyToneRow
        aiAssistantPrompts = customPrompts
        aiAssistantChipViews = chipViews
        val keyboardButton = createFloatingKeyboardButton(palette).apply {
            visibility = View.GONE
        }
        aiKeyboardButton = keyboardButton
        root.addView(keyboardButton, createKeyboardButtonLayoutParams())

        // Update initial state
        updateAIAssistantState()

        return root
    }

    private fun createEmptyState(
        title: String,
        subtitle: String,
        palette: ThemePaletteV2,
        actionLabel: String = "⌨️ Back to Keyboard",
        @DrawableRes iconRes: Int? = null
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(32), 0, dpToPx(32))
            
            addView(TextView(context).apply {
                text = title
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.keyText)
                gravity = Gravity.CENTER
            })
            
            addView(TextView(context).apply {
                text = subtitle
                textSize = 14f
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 180))
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(8), 0, dpToPx(24))
                iconRes?.let { icon ->
                    val drawable = ContextCompat.getDrawable(context, icon)
                    setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
                    compoundDrawablePadding = dpToPx(8)
                }
            })
            
            addView(Button(context).apply {
                text = actionLabel
                isAllCaps = false
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = createAccentButtonBackground(palette, 24)
                setTextColor(getContrastColor(palette.specialAccent))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                )
                setOnClickListener { onBackToKeyboard() }
            })
        }
    }

    private fun createShimmerContainer(palette: ThemePaletteV2): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            
            repeat(3) { index ->
                addView(View(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(12).toFloat()
                        setColor(ColorUtils.blendARGB(palette.keyBg, Color.WHITE, 0.08f))
                        setStroke(1, ColorUtils.setAlphaComponent(palette.keyBorderColor, 80))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(60)
                    ).apply {
                        if (index < 2) bottomMargin = dpToPx(8)
                    }
                })
            }
        }
    }

    private fun createSelectableChipPill(label: String, palette: ThemePaletteV2, selected: Boolean): TextView {
        return TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(if (selected) {
                if (ColorUtils.calculateLuminance(palette.specialAccent) > 0.5)
                    Color.BLACK else Color.WHITE
            } else palette.keyText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                if (selected) {
                    setColor(palette.specialAccent)
                } else {
                    setColor(ColorUtils.blendARGB(palette.keyBg, Color.BLACK, 0.5f))
                    setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 180))
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
        }
    }

    private fun registerGrammarChip(
        chip: TextView,
        palette: ThemePaletteV2,
        selected: Boolean,
        action: () -> Unit
    ) {
        grammarChipViews.add(chip)
        grammarChipActions[chip] = action
        if (selected || selectedGrammarChip == null) {
            selectedGrammarChip = chip
            updateChipGroupSelection(grammarChipViews, chip, palette)
        }
        chip.setOnClickListener {
            updateChipGroupSelection(grammarChipViews, chip, palette)
            selectedGrammarChip = chip
            action()
        }
    }

    private fun registerToneChip(
        chip: TextView,
        palette: ThemePaletteV2,
        selected: Boolean,
        action: () -> Unit
    ) {
        toneChipViews.add(chip)
        toneChipActions[chip] = action
        if (selected || selectedToneChip == null) {
            selectedToneChip = chip
            updateChipGroupSelection(toneChipViews, chip, palette)
        }
        chip.setOnClickListener {
            updateChipGroupSelection(toneChipViews, chip, palette)
            selectedToneChip = chip
            action()
        }
    }

    private fun updateChipSelection(chip: TextView, selected: Boolean, palette: ThemePaletteV2) {
        chip.background = GradientDrawable().apply {
            cornerRadius = dpToPx(20).toFloat()
            if (selected) {
                setColor(palette.specialAccent)
            } else {
                setColor(ColorUtils.blendARGB(palette.keyBg, Color.BLACK, 0.5f))
                setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 180))
            }
        }
        chip.setTextColor(if (selected) {
            if (ColorUtils.calculateLuminance(palette.specialAccent) > 0.5)
                Color.BLACK else Color.WHITE
        } else palette.keyText)
    }

    private fun updateChipGroupSelection(
        chips: List<TextView>,
        selectedChip: TextView,
        palette: ThemePaletteV2
    ) {
        chips.forEach { chip ->
            updateChipSelection(chip, chip == selectedChip, palette)
        }
    }

    private fun setupReplyToneFilters(replyToneRow: LinearLayout, palette: ThemePaletteV2) {
        replyToneRow.removeAllViews()
        val tones = listOf("Positive", "Negative", "Neutral")
        tones.forEach { tone ->
            val chip = TextView(context).apply {
                text = tone
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(16).toFloat()
                    if (tone == aiSelectedReplyTone) {
                        setColor(palette.specialAccent)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke(1, palette.keyText)
                    }
                }
                setTextColor(if (tone == aiSelectedReplyTone) {
                    if (ColorUtils.calculateLuminance(palette.specialAccent) > 0.5)
                        Color.BLACK else Color.WHITE
                } else palette.keyText)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dpToPx(8)
                }
                setOnClickListener {
                    aiSelectedReplyTone = tone
                    setupReplyToneFilters(replyToneRow, palette)
                    // Reprocess with new tone if there's input
                    if (currentInputText.trim().isNotEmpty()) {
                        val selectedPrompt = aiAssistantPrompts.firstOrNull { 
                            it.title.equals("Reply", ignoreCase = true) 
                        }
                        selectedPrompt?.let { prompt ->
                            processAIPromptWithShimmer(
                                prompt.prompt,
                                prompt.title,
                                aiAssistantStatusText!!,
                                aiAssistantShimmerContainer!!,
                                aiAssistantResultContainer!!,
                                aiAssistantReplaceButton!!,
                                aiAssistantEmptyState!!
                            )
                        }
                    }
                }
            }
            replyToneRow.addView(chip)
        }
    }

    private fun processAIPromptWithShimmer(
        prompt: String,
        title: String,
        statusText: TextView,
        shimmerContainer: LinearLayout,
        resultContainer: LinearLayout,
        replaceButton: Button,
        emptyState: View
    ) {
        val inputText = currentInputText.trim()
        if (inputText.isEmpty()) return

        // Show shimmer, hide results
        shimmerContainer.visibility = View.VISIBLE
        resultContainer.visibility = View.GONE
        replaceButton.visibility = View.GONE
        statusText.visibility = View.VISIBLE
        statusText.text = "Processing…"
        lastAiResult = ""

        // Start shimmer animation
        startShimmerAnimation(shimmerContainer)

        scope.launch {
            try {
                // Modify prompt for Reply with tone
                val modifiedPrompt = if (title.equals("Reply", ignoreCase = true)) {
                    "Generate 3 short reply options to the following message in a $aiSelectedReplyTone tone. Keep each under 25 words. Return each on its own line without numbering."
                } else {
                    prompt
                }

                val result = unifiedAIService.processCustomPrompt(
                    text = inputText,
                    prompt = modifiedPrompt,
                    stream = false
                ).first()

                withContext(Dispatchers.Main) {
                    shimmerContainer.clearAnimation()
                    shimmerContainer.visibility = View.GONE
                    
                    if (result.success && result.text.isNotBlank()) {
                        displayAIResultsInPanel(
                            result.text,
                            statusText,
                            resultContainer,
                            replaceButton,
                            PanelTheme.palette
                        )
        } else {
                        statusText.text = "❌ Error: ${result.error ?: "No response"}"
                        resultContainer.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    shimmerContainer.clearAnimation()
                    shimmerContainer.visibility = View.GONE
                    statusText.text = "❌ Error: ${e.message}"
                    resultContainer.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startShimmerAnimation(shimmerContainer: LinearLayout) {
        val shimmerAnim = android.view.animation.AlphaAnimation(0.3f, 1.0f).apply {
            duration = 1000
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
        }
        shimmerContainer.startAnimation(shimmerAnim)
    }

    private fun displayAIResultsInPanel(
        text: String,
        statusText: TextView,
        resultContainer: LinearLayout,
        replaceButton: Button,
        palette: ThemePaletteV2
    ) {
        resultContainer.removeAllViews()
        resultContainer.visibility = View.VISIBLE
        
        // Strip numbering (1., 2., 3., etc.) and bullet points
        val results = text.split("\n")
            .map { line ->
                line.trim()
                    .replace(Regex("^\\d+[.)\\s]+"), "") // Remove "1. ", "1) ", "1 ", etc.
                    .trimStart('-', '•', '*', '·')
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .take(3)

        if (results.isEmpty()) {
            statusText.text = "No results available"
            return
        }

        lastAiResult = results.first()
        statusText.text = "Tap a suggestion to use it"

        results.forEach { result ->
            val card = TextView(context).apply {
                this.text = result
                textSize = 13f
                setLineSpacing(0f, 1.2f)
                setTextColor(palette.keyText)
                setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(ColorUtils.blendARGB(palette.keyBg, Color.BLACK, 0.4f))
                    setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 100))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8)
                }
                setOnClickListener {
                    lastAiResult = result
                    onTextProcessedCallback?.invoke(result)
                    onBackToKeyboard()
                }
            }
            resultContainer.addView(card)
        }

        replaceButton.visibility = View.VISIBLE
    }

    private fun updateAIAssistantState() {
        val hasInput = currentInputText.trim().isNotEmpty()
        val palette = PanelTheme.palette
        
        aiAssistantEmptyState?.visibility = if (hasInput) View.GONE else View.VISIBLE
        aiChipScroll?.visibility = if (hasInput) View.VISIBLE else View.GONE
        aiAssistantReplyToneRow?.visibility = View.GONE  // Hide by default
        aiKeyboardButton?.visibility = if (hasInput) View.VISIBLE else View.GONE
        
        if (!hasInput) {
            // Clear all processed results when no input
            aiAssistantResultContainer?.removeAllViews()
            aiAssistantResultContainer?.visibility = View.GONE
            aiAssistantReplaceButton?.visibility = View.GONE
            aiAssistantShimmerContainer?.clearAnimation()
            aiAssistantShimmerContainer?.visibility = View.GONE
            aiAssistantStatusText?.visibility = View.GONE
            lastAiResult = ""
        } else if (
            aiAssistantPrompts.isNotEmpty() &&
            aiAssistantStatusText != null &&
            aiAssistantShimmerContainer != null &&
            aiAssistantResultContainer != null &&
            aiAssistantReplaceButton != null &&
            aiAssistantEmptyState != null
        ) {
            // Auto-select first prompt and process
            aiAssistantChipViews.forEachIndexed { index, view ->
                updateChipSelection(view, index == 0, palette)
            }
            val firstPrompt = aiAssistantPrompts.first()
            processAIPromptWithShimmer(
                firstPrompt.prompt,
                firstPrompt.title,
                aiAssistantStatusText!!,
                aiAssistantShimmerContainer!!,
                aiAssistantResultContainer!!,
                aiAssistantReplaceButton!!,
                aiAssistantEmptyState!!
            )
        }
    }

    
    /**
     * Create Translate Panel - Pure Kotlin UI
     */
    private fun createTranslatePanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        val root = createPanelRoot(palette, height)
        root.addView(createPanelHeader("Translate"))

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(32))
        }
        scrollView.addView(content)
        root.addView(scrollView)

        content.addView(createTranslateLanguageSelector(palette))
        content.addView(spacerView(dpToPx(12)))

        val emptyState = createTranslateEmptyState(palette)
        translateEmptyState = emptyState
        content.addView(emptyState)

        val guideLink = TextView(context).apply {
            text = "How to Use CleverType Guide"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(palette.specialAccent)
            paint.isUnderlineText = true
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(16))
            setOnClickListener {
                // Toast removed - guide message logged only
            }
        }
        translateGuideLink = guideLink
        content.addView(guideLink)

        val resultCard = createTranslateResultCard(palette).apply {
            visibility = View.GONE
        }
        translateResultContainer = resultCard
        content.addView(resultCard)

        val replaceButton = Button(context).apply {
            text = "Replace Text"
            isAllCaps = false
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createAccentButtonBackground(palette, 24)
            setTextColor(getContrastColor(palette.specialAccent))
            visibility = View.GONE
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(46)
            )
            setOnClickListener {
                val text = translateOutputView?.text?.toString()?.trim().orEmpty()
                if (text.isBlank() ||
                    text.startsWith("Translation", ignoreCase = true) ||
                    text.startsWith("⏳") ||
                    text.startsWith("❌")
                ) {
                    // Toast removed - translate validation logged only
                    return@setOnClickListener
                }
                onTextProcessedCallback?.invoke(text)
                onBackToKeyboard()
            }
        }
        translateReplaceButton = replaceButton
        content.addView(replaceButton)

        val keyboardButton = createFloatingKeyboardButton(palette).apply {
            visibility = View.VISIBLE
        }
        translateKeyboardButton = keyboardButton
        root.addView(keyboardButton, createKeyboardButtonLayoutParams())

        updateTranslateState(currentInputText.trim().isNotEmpty())

        return root
    }
    private fun createTranslateLanguageSelector(palette: ThemePaletteV2): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            addView(TextView(context).apply {
                text = "Translate to"
                textSize = 12f
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
                setPadding(0, 0, 0, dpToPx(6))
            })

            val selector = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createRoundedDrawable(palette.keyBg, dpToPx(18))
                setPadding(dpToPx(14), dpToPx(10), dpToPx(12), dpToPx(10))
            }

            val icon = ImageView(context).apply {
                setImageResource(R.drawable.sym_keyboard_globe)
                setColorFilter(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(dpToPx(18), dpToPx(18))
            }
            selector.addView(icon)

            val value = TextView(context).apply {
                text = translateLanguage.label
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.keyText)
                setPadding(dpToPx(8), 0, dpToPx(4), 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            selector.addView(value)
            translateLanguageLabel = value

            val chevron = ImageView(context).apply {
                setImageResource(R.drawable.ic_back_chevron)
                rotation = 90f
                setColorFilter(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
            }
            selector.addView(chevron)

            selector.setOnClickListener {
                val popup = PopupMenu(context, selector)
                translateLanguageOptions.forEach { option ->
                    popup.menu.add(option.label)
                }
                popup.setOnMenuItemClickListener { item ->
                    val selected = translateLanguageOptions.firstOrNull { it.label == item.title }
                    if (selected != null) {
                        translateLanguage = selected
                        translateLanguageLabel?.text = selected.label
                        triggerTranslateForCurrentLanguage(force = currentInputText.trim().isNotEmpty())
                    }
                    true
                }
                popup.show()
            }

            addView(selector)
        }
    }

    private fun createTranslateEmptyState(palette: ThemePaletteV2): LinearLayout {
        val highlight = "Something"
        val baseText = "Select $highlight and tap Translate to convert instantly"
        val spannable = SpannableString(baseText)
        val start = baseText.indexOf(highlight)
        if (start >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#FF7AD9")),
                start,
                start + highlight.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(48), dpToPx(16), dpToPx(48))

            addView(TextView(context).apply {
                text = spannable
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.keyText)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "Highlight text, tap the Translate key, and we'll do the rest."
                textSize = 13f
                setPadding(0, dpToPx(12), 0, dpToPx(20))
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 180))
                gravity = Gravity.CENTER
            })

            addView(Button(context).apply {
                text = "Back to Keyboard"
                isAllCaps = false
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = createAccentButtonBackground(palette, 24)
                setTextColor(getContrastColor(palette.specialAccent))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                )
                setOnClickListener { onBackToKeyboard() }
            })
        }
    }

    private fun createTranslateResultCard(palette: ThemePaletteV2): LinearLayout {
        val bgColor = if (themeManager.isImageBackground()) {
            ColorUtils.blendARGB(palette.panelSurface, Color.BLACK, 0.2f)
        } else {
            palette.keyBg
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(bgColor)
                setStroke(1, palette.keyBorderColor)
            }
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))

            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val status = TextView(context).apply {
                text = "Select text to get started"
                textSize = 12f
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            translateStatusText = status
            header.addView(status)

            val copy = ImageButton(context).apply {
                setImageResource(R.drawable.ic_qs_copy)
                background = null
                setColorFilter(palette.keyText)
                isEnabled = false
                alpha = 0.4f
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                setOnClickListener { copyTranslateResult() }
            }
            translateCopyButton = copy
            header.addView(copy)

            addView(header)

            val output = TextView(context).apply {
                text = "Translation will appear here..."
                textSize = 20f
                setLineSpacing(0f, 1.2f)
                setTextColor(palette.keyText)
                setPadding(0, dpToPx(12), 0, 0)
            }
            output.setOnClickListener { copyTranslateResult() }
            output.setOnLongClickListener {
                copyTranslateResult()
                true
            }
            translateOutputView = output
            addView(output)
        }
    }

    /**
     * Create Clipboard Panel - Pure Kotlin UI
     */
    private fun createClipboardPanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        val service = AIKeyboardService.getInstance()
        val clipboardItems = service?.getClipboardHistoryItems(40) ?: emptyList()
        val clipboardEnabled = service?.isClipboardHistoryEnabled() ?: true
        val root = createPanelRoot(palette, height)
        root.addView(createPanelHeader("Clipboard"))

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(4), 0, dpToPx(12))
        }
        scrollView.addView(content)
        root.addView(scrollView)

        content.addView(createHeroBlock("Clipboard", "Tap an entry to paste instantly"))

        if (!clipboardEnabled) {
            content.addView(TextView(context).apply {
                text = "Clipboard history is turned off.\nUse Quick Settings to enable it."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 200))
                setPadding(dpToPx(16), dpToPx(28), dpToPx(16), dpToPx(28))
            })
        } else if (clipboardItems.isEmpty()) {
            content.addView(TextView(context).apply {
                text = "No clipboard items yet"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 200))
                setPadding(0, dpToPx(32), 0, dpToPx(32))
            })
        } else {
            val grid = GridLayout(context).apply {
                columnCount = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            clipboardItems.take(20).forEach { item ->
                grid.addView(createClipboardTile(item, palette, service))
            }
            content.addView(grid)
        }

        if (clipboardEnabled && clipboardItems.isNotEmpty()) {
            val clearLink = TextView(context).apply {
                text = "Clear non-pinned items"
                textSize = 13f
                setPadding(0, dpToPx(12), 0, dpToPx(4))
                setTextColor(palette.specialAccent)
                setOnClickListener {
                    if (service?.clearClipboardHistory() == true) {
                        // Toast removed - clipboard cleared logged only
                    } else {
                        // Toast removed - clipboard error logged only
                    }
                    rebuildDynamicPanelsFromPrompts()
                }
            }
            content.addView(clearLink)
        }

        
        

        return root
    }
    
    /**
     * Create Settings Panel - Pure Kotlin UI
     */
    private fun createSettingsPanel(): View {
        val palette = PanelTheme.palette
        val height = keyboardHeightManager?.getPanelHeight() ?: dpToPx(PANEL_HEIGHT_DP)
        val prefs = context.getSharedPreferences("ai_keyboard_settings", Context.MODE_PRIVATE)
        val quickSettings = loadQuickSettings(prefs)
        val panelBg = if (themeManager.isImageBackground()) palette.panelSurface else palette.keyboardBg

        val navBarHeight = keyboardHeightManager?.getNavigationBarHeight() ?: 0

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(panelBg)
            val horizontalPadding = dpToPx(16)
            val topPadding = dpToPx(16)
            val bottomPadding = dpToPx(14) + navBarHeight
            setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
            clipToPadding = false
            clipChildren = false
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
        }

        container.addView(createSettingsPanelToolbar(palette))

        // Create horizontally scrollable features panel with 2 rows
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            )
            overScrollMode = HorizontalScrollView.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(panelBg)
        }

        // Vertical container for 2 rows
        val rowsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        // Row 1 - Top row
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0,
                1f
            )
        }

        // Row 2 - Bottom row
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0,
                1f
            )
        }

        // Create feature items with refresh support
        fun buildItems() {
            row1.removeAllViews()
            row2.removeAllViews()
            quickSettings.forEachIndexed { index, item ->
                val featureItem = createFeatureItem(item, palette) {
                    try {
                        item.handler(item)
                        // Refresh UI for toggle items to show updated state
                        if (item.type == QuickSettingType.TOGGLE) {
                            buildItems()
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error executing quick setting ${item.id}", e)
                        // Toast removed - action error logged only
                    }
                }
                // Alternate between row 1 and row 2
                if (index % 2 == 0) {
                    row1.addView(featureItem)
                } else {
                    row2.addView(featureItem)
                }
            }
        }
        buildItems()

        rowsContainer.addView(row1)
        rowsContainer.addView(row2)
        scrollView.addView(rowsContainer)
        container.addView(scrollView)
        
        return container
    }
    
    // ========================================
    // HELPER FUNCTIONS FOR PROGRAMMATIC UI
    // ========================================
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Create individual feature item for 2-row horizontal scroll
     * Bigger icons and text for better visibility
     */
    private fun createFeatureItem(
        item: QuickSettingItem,
        palette: ThemePaletteV2,
        onClick: () -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(120),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            // Add ripple feedback by resolving the attribute
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
            setOnClickListener { onClick() }
        }

        // Icon - larger size
        val iconView = ImageView(context).apply {
            val iconSize = dpToPx(56)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(item.iconRes)
            
            // Apply tint based on toggle state
            val tintColor = if (item.type == QuickSettingType.TOGGLE && item.isActive) {
                palette.specialAccent
            } else {
                palette.keyText
            }
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tintColor))
        }
        container.addView(iconView)

        // Label - larger text
        val labelView = TextView(context).apply {
            text = item.label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
            }
        }
        container.addView(labelView)

        return container
    }
    
    /**
     * Helper: Create compact header bar with back button and title
     * ✅ REFINED: Minimal padding, no extra top space
     */
    private fun createPanelHeader(title: String, languageConfig: LanguageConfig? = null): View {
        val palette = PanelTheme.palette
        val headerTextColor = palette.keyText
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )
            setPadding(0, dpToPx(4), 0, dpToPx(8))
            setBackgroundColor(Color.TRANSPARENT)

            val backButton = ImageView(context).apply {
                setImageResource(R.drawable.ic_back_chevron)
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(headerTextColor)
                )
                layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36))
                setOnClickListener { onBackToKeyboard() }
            }
            addView(backButton)

            addView(TextView(context).apply {
                text = title
                setTextColor(headerTextColor)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            })

            languageConfig?.let {
                addView(createLanguageChipView(palette, it))
            }
        }
    }

    private fun createPanelRoot(palette: ThemePaletteV2, height: Int): LinearLayout {
        val panelBg = if (themeManager.isImageBackground()) palette.panelSurface else palette.keyboardBg
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            setBackgroundColor(panelBg)  // Solid color instead of gradient
            
            // ✅ CRITICAL FIX: Add navigation bar padding at bottom
            val navBarHeight = keyboardHeightManager?.getNavigationBarHeight() ?: 0
            val basePaddingH = dpToPx(16)
            val basePaddingTop = dpToPx(10)
            val basePaddingBottom = dpToPx(14)
            
            setPadding(
                basePaddingH,
                basePaddingTop,
                basePaddingH,
                basePaddingBottom + navBarHeight  // Add nav bar height to bottom padding
            )
            
            clipToPadding = false
            clipChildren = false
            
            // ✅ Consume all touch events to prevent keyboard keys from being triggered
            isClickable = true
            isFocusable = true
            setOnTouchListener { _, _ -> true }
            
            LogUtil.d(TAG, "🔧 Panel created with nav bar padding: $navBarHeight px")
        }
    }

    private fun createLanguageChipView(palette: ThemePaletteV2, config: LanguageConfig): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val baseColor = if (themeManager.isImageBackground()) palette.panelSurface else palette.keyboardBg
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(ColorUtils.blendARGB(baseColor, Color.WHITE, 0.12f))
                setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 160))
            }
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))

            val icon = ImageView(context).apply {
                setImageResource(R.drawable.sym_keyboard_globe)
                setColorFilter(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(dpToPx(18), dpToPx(18))
            }
            addView(icon)

            val labelView = TextView(context).apply {
                text = config.current.label
                setTextColor(palette.keyText)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(6), 0, dpToPx(4), 0)
            }
            addView(labelView)

            val chevron = ImageView(context).apply {
                setImageResource(R.drawable.ic_back_chevron)
                rotation = 90f
                setColorFilter(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16))
            }
            addView(chevron)

            setOnClickListener {
                val popup = PopupMenu(context, this)
                config.options.forEach { option ->
                    popup.menu.add(option.label)
                }
                popup.setOnMenuItemClickListener { item ->
                    val selected = config.options.firstOrNull { it.label == item.title }
                    if (selected != null) {
                        config.current = selected
                        labelView.text = selected.label
                        config.onChanged(selected)
                    }
                    true
                }
                popup.show()
            }
        }
    }

    private fun createHeroBlock(title: String, subtitle: String): LinearLayout {
        val palette = PanelTheme.palette
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(4), 0, dpToPx(12))
            visibility = View.GONE  // Hidden by default
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.keyText)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
                setPadding(0, dpToPx(4), 0, 0)
            })
        }
    }

    private fun createPrimaryButton(label: String, palette: ThemePaletteV2, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 16f
            setTextColor(getContrastColor(palette.specialAccent))
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    ColorUtils.blendARGB(palette.specialAccent, Color.WHITE, 0.12f),
                    palette.specialAccent
                )
            ).apply { cornerRadius = dpToPx(24).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )
            setOnClickListener { onClick() }
        }
    }

    private fun createGuideLink(palette: ThemePaletteV2): TextView {
        return TextView(context).apply {
            text = "How to Use Kvive Guide"
            gravity = Gravity.CENTER
            textSize = 13f
            setPadding(0, dpToPx(10), 0, 0)
            setTextColor(palette.specialAccent)
            setOnClickListener {
                // Toast removed - guide message logged only
            }
        }
    }

    private fun createAddPromptChip(palette: ThemePaletteV2, category: String): TextView {
        return TextView(context).apply {
            text = "+ Add More To Keyboard"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(palette.specialAccent)
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 200))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
            setOnClickListener { launchPromptManager(category) }
        }
    }

    private fun createPanelCardView(placeholder: String, palette: ThemePaletteV2): TextView {
        val baseKeyColor = if (themeManager.isImageBackground()) {
            ColorUtils.setAlphaComponent(Color.BLACK, 160)
        } else {
            palette.keyBg
        }
        val blended = ColorUtils.blendARGB(baseKeyColor, palette.keyboardBg, 0.25f)
        return TextView(context).apply {
            text = placeholder
            textSize = 15f
            setLineSpacing(0f, 1.15f)
            setTextColor(palette.keyText)
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14).toFloat()
                setColor(blended)
                setStroke(1, ColorUtils.setAlphaComponent(palette.keyBorderColor, 140))
            }
            setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(14))
        }
    }

    private fun getContrastColor(color: Int): Int {
        return if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
    }

    private fun createAccentButtonBackground(
        palette: ThemePaletteV2,
        cornerRadiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(cornerRadiusDp).toFloat()
            setColor(palette.specialAccent)
        }
    }

    private fun createGrammarResultCard(palette: ThemePaletteV2): GrammarResultViews {
        val baseKeyColor = if (themeManager.isImageBackground()) {
            ColorUtils.setAlphaComponent(Color.BLACK, 160)
        } else {
            palette.keyBg
        }
        val blended = ColorUtils.blendARGB(baseKeyColor, palette.keyboardBg, 0.25f)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14).toFloat()
                setColor(blended)
                setStroke(1, ColorUtils.setAlphaComponent(palette.keyBorderColor, 140))
            }
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val primary = TextView(context).apply {
            text = context.getString(R.string.panel_result_pending)
            textSize = 13f
            setLineSpacing(0f, 1.2f)
            setTextColor(palette.keyText)
        }
        container.addView(primary)

        val translation = TextView(context).apply {
            visibility = View.GONE
            textSize = 11f
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
        }
        container.addView(translation)

        val replaceButton = Button(context).apply {
            text = context.getString(R.string.ai_panel_button_replace)
            isAllCaps = false
            textSize = 12f
            background = createAccentButtonBackground(palette, 20)
            setTextColor(getContrastColor(palette.specialAccent))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            ).apply {
                gravity = Gravity.END
            }
        }
        container.addView(replaceButton)

        return GrammarResultViews(container, primary, translation, replaceButton)
    }

    private fun createToneResultCard(palette: ThemePaletteV2): ToneResultViews {
        val baseKeyColor = if (themeManager.isImageBackground()) {
            ColorUtils.setAlphaComponent(Color.BLACK, 160)
        } else {
            palette.keyBg
        }
        val blended = ColorUtils.blendARGB(baseKeyColor, palette.keyboardBg, 0.25f)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(14).toFloat()
                setColor(blended)
                setStroke(1, ColorUtils.setAlphaComponent(palette.keyBorderColor, 140))
            }
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val primary = TextView(context).apply {
            text = context.getString(R.string.panel_prompt_tone)
            textSize = 13f
            setLineSpacing(0f, 1.2f)
            setTextColor(palette.keyText)
        }
        container.addView(primary)

        val translation = TextView(context).apply {
            visibility = View.GONE
            textSize = 11f
            setPadding(0, dpToPx(6), 0, 0)
            setTextColor(ColorUtils.setAlphaComponent(palette.keyText, 200))
        }
        container.addView(translation)

        return ToneResultViews(container, primary, translation)
    }

    private fun createFloatingKeyboardButton(palette: ThemePaletteV2): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.keyboard_icon)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.specialAccent)
            }
            ImageViewCompat.setImageTintList(
                this,
                ColorStateList.valueOf(getContrastColor(palette.specialAccent))
            )
            contentDescription = context.getString(R.string.keyboard_button_back)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            elevation = dpToPx(6).toFloat()
            setOnClickListener { onBackToKeyboard() }
        }
    }

    private fun createKeyboardButtonLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            dpToPx(52),
            dpToPx(52),
            Gravity.END or Gravity.BOTTOM
        ).apply {
            rightMargin = dpToPx(16)
            bottomMargin = dpToPx(16)
        }
    }

    private fun spacerView(heightPx: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightPx
            )
        }
    }

    private fun launchPromptManager(category: String) {
        try {
            // Map category to Flutter navigation routes
            val navigationRoute = when (category) {
                "assistant" -> "ai_writing_custom"  // AI Writing Assistance -> Custom Assistance tab
                "grammar" -> "custom_grammar"        // Custom Grammar screen
                "tone" -> "custom_tones"             // Custom Tones screen
                else -> "prompts_$category"          // Fallback to old format
            }
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", navigationRoute)
            }
            context.startActivity(intent)
            Log.d(TAG, "✅ Launching Flutter app: category=$category, route=$navigationRoute")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching prompt manager for $category", e)
            // Toast removed - app launch error logged only
        }
    }
    
    private fun themedTextView(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val palette = PanelTheme.palette
        return TextView(context).apply {
            this.text = text
            this.textSize = size
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(palette.keyText)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    private fun themedButton(label: String, onClick: () -> Unit): Button {
        val palette = PanelTheme.palette
        return Button(context).apply {
            text = label
            textSize = 13f
            background = createRoundedDrawable(palette.keyBg, dpToPx(12))
            setTextColor(palette.keyText)
            setOnClickListener { onClick() }
            isAllCaps = false
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(40)
            ).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
        }
    }
    
    private fun createRoundedDrawable(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
            val palette = PanelTheme.palette
            setStroke(dpToPx(1), palette.keyBorderColor)
        }
    }
    
    private fun createHorizontalScrollButtonContainer(): LinearLayout {
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }
        
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        scrollView.addView(buttonContainer)
        
        // Return the LinearLayout so buttons can be added to it
        return buttonContainer
    }
    
    private fun createSettingToggle(
        label: String,
        key: String,
        prefs: android.content.SharedPreferences
    ): View {
        val palette = PanelTheme.palette
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(12), 0, dpToPx(12))
            background = createRoundedDrawable(PanelTheme.palette.keyBg, dpToPx(8))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            
            val labelView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(palette.keyText)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            addView(labelView)
            
            val toggle = Switch(context).apply {
                isChecked = prefs.getBoolean(key, true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean(key, isChecked).apply()
                    
                    // Broadcast settings change
                    val intent = android.content.Intent("com.kvive.keyboard.SETTINGS_CHANGED")
                    intent.setPackage(context.packageName)
                    context.sendBroadcast(intent)
                    
                    LogUtil.d(TAG, "Setting changed: $key = $isChecked")
                }
            }
            addView(toggle)
        }.apply {
            val outerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            outerParams.setMargins(0, 0, 0, dpToPx(8))
            layoutParams = outerParams
        }
    }

    private fun createSettingsPanelToolbar(palette: ThemePaletteV2): View {
        val service = AIKeyboardService.getInstance()

        fun addButton(
            parent: LinearLayout,
            @DrawableRes iconRes: Int,
            withBox: Boolean = false,
            onClick: () -> Unit
        ) {
            val button = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    rightMargin = dpToPx(8)
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                background = createToolbarButtonBackground(palette, withBox)
                setImageResource(iconRes)
                val tintColor = if (withBox) {
                    val drawableColor = (background as? GradientDrawable)?.color?.defaultColor
                        ?: palette.specialAccent
                    getContrastColor(drawableColor)
                } else {
                    getContrastColor(palette.toolbarBg)
                }
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tintColor))
                setOnClickListener { onClick() }
            }
            parent.addView(button)
        }

        val leftGroup = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        addButton(leftGroup, R.drawable.keyboard_icon, withBox = true) { onBackToKeyboard() }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 0)
            addView(leftGroup)
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
    }

    private fun createToolbarButtonBackground(
        palette: ThemePaletteV2,
        withBox: Boolean
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(18).toFloat()
            if (withBox) {
                setColor(palette.specialAccent)
            } else {
                setColor(ColorUtils.setAlphaComponent(palette.keyBg, 160))
                setStroke(dpToPx(1), ColorUtils.setAlphaComponent(palette.keyBorderColor, 120))
            }
        }
    }

    private fun loadQuickSettings(prefs: SharedPreferences): MutableList<QuickSettingItem> {
        val defaultsList = createDefaultQuickSettings(prefs)
        val defaultsMap = LinkedHashMap<String, QuickSettingItem>()
        defaultsList.forEach { defaultsMap[it.id] = it }

        val stored = prefs.getString("quick_settings_order_v2", null)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        if (stored.isEmpty()) {
            return defaultsList.toMutableList()
        }

        val ordered = mutableListOf<QuickSettingItem>()
        stored.forEach { id ->
            defaultsMap.remove(id)?.let { ordered.add(it) }
        }

        if (defaultsMap.isNotEmpty()) {
            ordered.addAll(defaultsMap.values)
        }

        return if (ordered.isEmpty()) defaultsList.toMutableList() else ordered
    }

    private fun createDefaultQuickSettings(prefs: SharedPreferences): List<QuickSettingItem> {
        val service = AIKeyboardService.getInstance()
        val flutterPrefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

        val soundFallback = prefs.getBoolean("sound_enabled", true)
        val vibrationFallback = prefs.getBoolean("vibration_enabled", true)
        val numberRowFallback = prefs.getBoolean("show_number_row", false)
        val autoCorrectFallback = prefs.getBoolean("auto_correct", true)
        val oneHandedFallback = flutterPrefs.getBoolean("flutter.keyboard_settings.one_handed_mode", false)
        val clipboardPrefs = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
        val clipboardEnabledFallback = when {
            flutterPrefs.contains("flutter.clipboard_history") ->
                flutterPrefs.getBoolean("flutter.clipboard_history", true)
            else -> clipboardPrefs.getBoolean("clipboard_enabled", true)
        }

        return listOf(
            QuickSettingItem(
                id = "themes",
                label = "Themes",
                iconRes = R.drawable.ic_qs_themes,
                type = QuickSettingType.ACTION
            ) {
                openFlutterRoute("theme_editor")
            },
            QuickSettingItem(
                id = "number_row",
                label = "Number Row",
                iconRes = R.drawable.number_123,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isNumberRowEnabled() ?: numberRowFallback
            ) { item ->
                if (service != null) {
                    service.toggleNumberRow()
                    item.isActive = service.isNumberRowEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("show_number_row", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "clipboard_history",
                label = "Clipboard",
                iconRes = R.drawable.clipboard,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isClipboardHistoryEnabled() ?: clipboardEnabledFallback
            ) { item ->
                if (service != null) {
                    item.isActive = service.toggleClipboardHistory()
                } else {
                    val newState = !item.isActive
                    clipboardPrefs.edit().putBoolean("clipboard_enabled", newState).apply()
                    flutterPrefs.edit().putBoolean("flutter.clipboard_history", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "sound",
                label = "Sound",
                iconRes = R.drawable.ic_qs_sound,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isSoundEnabled() ?: soundFallback
            ) { item ->
                if (service != null) {
                    service.toggleSound()
                    item.isActive = service.isSoundEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("sound_enabled", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "vibration",
                label = "Vibration",
                iconRes = R.drawable.ic_qs_vibration,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isVibrationEnabled() ?: vibrationFallback
            ) { item ->
                if (service != null) {
                    service.toggleVibration()
                    item.isActive = service.isVibrationEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("vibration_enabled", newState).apply()
                    item.isActive = newState
                }
            },
             QuickSettingItem(
                id = "vibration",
                label = "Vibration",
                iconRes = R.drawable.ic_qs_vibration,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isVibrationEnabled() ?: vibrationFallback
            ) { item ->
                if (service != null) {
                    service.toggleVibration()
                    item.isActive = service.isVibrationEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("vibration_enabled", newState).apply()
                    item.isActive = newState
                }
            },
             QuickSettingItem(
                id = "vibration",
                label = "Vibration",
                iconRes = R.drawable.ic_qs_vibration,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isVibrationEnabled() ?: vibrationFallback
            ) { item ->
                if (service != null) {
                    service.toggleVibration()
                    item.isActive = service.isVibrationEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("vibration_enabled", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "undo",
                label = "Undo",
                iconRes = R.drawable.ic_qs_undo,
                type = QuickSettingType.ACTION
            ) {
                performEditorCommand(EditorCommand.UNDO)
            },
            QuickSettingItem(
                id = "redo",
                label = "Redo",
                iconRes = R.drawable.ic_qs_redo,
                type = QuickSettingType.ACTION
            ) {
                performEditorCommand(EditorCommand.REDO)
            },
            QuickSettingItem(
                id = "copy",
                label = "Copy",
                iconRes = R.drawable.ic_qs_copy,
                type = QuickSettingType.ACTION
            ) {
                performEditorCommand(EditorCommand.COPY)
            },
            QuickSettingItem(
                id = "paste",
                label = "Paste",
                iconRes = R.drawable.ic_qs_paste,
                type = QuickSettingType.ACTION
            ) {
                performEditorCommand(EditorCommand.PASTE)
            },
            QuickSettingItem(
                id = "translate",
                label = "Translator",
                iconRes = R.drawable.sym_keyboard_globe,
                type = QuickSettingType.ACTION
            ) {
                if (service != null) {
                    service.showUnifiedPanel(UnifiedPanelManager.PanelType.TRANSLATE)
                } else {
                    showToast("Translator not available")
                }
            },
            QuickSettingItem(
                id = "auto_correct",
                label = "Auto-Correct",
                iconRes = R.drawable.ic_qs_spellcheck,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isAutoCorrectEnabled() ?: autoCorrectFallback
            ) { item ->
                if (service != null) {
                    service.toggleAutoCorrect()
                    item.isActive = service.isAutoCorrectEnabled()
                } else {
                    val newState = !item.isActive
                    prefs.edit().putBoolean("auto_correct", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "one_handed",
                label = "One Handed",
                iconRes = R.drawable.ic_qs_one_handed,
                type = QuickSettingType.TOGGLE,
                isActive = service?.isOneHandedModeEnabled() ?: oneHandedFallback
            ) { item ->
                if (service != null) {
                    service.toggleOneHandedMode()
                    item.isActive = service.isOneHandedModeEnabled()
                } else {
                    val newState = !item.isActive
                    flutterPrefs.edit().putBoolean("flutter.keyboard_settings.one_handed_mode", newState).apply()
                    item.isActive = newState
                }
            },
            QuickSettingItem(
                id = "settings",
                label = "Settings",
                iconRes = R.drawable.setting,
                type = QuickSettingType.ACTION
            ) {
                openFlutterRoute("settings_screen")
            }
        )
    }

    private fun persistQuickSettingsOrder(prefs: SharedPreferences, items: List<QuickSettingItem>) {
        val order = items.joinToString(",") { it.id }
        prefs.edit().putString("quick_settings_order_v2", order).apply()
    }

    private fun openFlutterRoute(route: String? = null) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                route?.let { putExtra("navigate_to", it) }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unable to open Flutter route: $route", e)
            showToast("Unable to open app")
        }
    }

    private enum class EditorCommand {
        UNDO,
        REDO,
        COPY,
        PASTE
    }

    private fun performEditorCommand(command: EditorCommand) {
        val inputConnection = inputConnectionProvider()
        if (inputConnection == null) {
            showToast("No text field active")
            return
        }
        when (command) {
            EditorCommand.UNDO -> sendKeyCombination(inputConnection, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            EditorCommand.REDO -> sendKeyCombination(
                inputConnection,
                KeyEvent.KEYCODE_Z,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
            )
            EditorCommand.COPY -> inputConnection.performContextMenuAction(android.R.id.copy)
            EditorCommand.PASTE -> inputConnection.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun sendKeyCombination(
        inputConnection: InputConnection,
        keyCode: Int,
        metaState: Int
    ) {
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        inputConnection.sendKeyEvent(down)
        inputConnection.sendKeyEvent(up)
    }

    private fun showToast(message: String) {
        // Toast removed - message logged only
        Log.d(TAG, "Toast message: $message")
    }

    private fun openPanelFromToolbar(type: PanelType) {
        val service = AIKeyboardService.getInstance()
        if (service != null) {
            service.showUnifiedPanel(type)
        } else {
            showToast("Keyboard not available")
        }
    }

    private fun createClipboardTile(
        item: ClipboardItem,
        palette: com.kvive.keyboard.themes.ThemePaletteV2,
        service: AIKeyboardService?
    ): View {
        val text = item.text
        val display = text.take(80) + if (text.length > 80) "…" else ""
        val baseColor = if (themeManager.isImageBackground()) palette.panelSurface else palette.keyboardBg
        val background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                ColorUtils.blendARGB(baseColor, Color.WHITE, 0.08f),
                ColorUtils.blendARGB(baseColor, Color.BLACK, 0.5f)
            )
        ).apply {
            cornerRadius = dpToPx(16).toFloat()
            setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 180))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            }
            this.background = background
            setPadding(dpToPx(14), dpToPx(12), dpToPx(12), dpToPx(12))

            val textView = TextView(context).apply {
                this.text = if (item.isPinned) "📌 $display" else display
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            addView(textView)

            val deleteBtn = ImageView(context).apply {
                setImageResource(R.drawable.sym_keyboard_delete)
                setColorFilter(ColorUtils.setAlphaComponent(Color.WHITE, 200))
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
                setOnClickListener {
                    try {
                        // Check if service is still available
                        if (service == null) {
                            android.util.Log.w("UnifiedPanelManager", "Cannot delete clipboard item: service is null (keyboard may be closing)")
                            return@setOnClickListener
                        }
                        
                        val deleted = service.deleteClipboardItem(item.id)
                        if (deleted) {
                            // Only rebuild if keyboard is still active
                            try {
                                rebuildDynamicPanelsFromPrompts()
                            } catch (e: Exception) {
                                android.util.Log.w("UnifiedPanelManager", "Cannot rebuild panels (keyboard may be closing): ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("UnifiedPanelManager", "Error deleting clipboard item: ${e.message}", e)
                    }
                }
            }
            addView(deleteBtn)

            setOnClickListener {
                try {
                    // Get input connection - may be null if keyboard is closing
                    val ic = inputConnectionProvider()
                    if (ic == null) {
                        android.util.Log.w("UnifiedPanelManager", "Cannot insert clipboard text: input connection is null (keyboard may be closing)")
                        return@setOnClickListener
                    }
                    
                    // Validate connection is still active by testing it
                    try {
                        val testText = ic.getTextBeforeCursor(1, 0)
                        if (testText == null) {
                            android.util.Log.w("UnifiedPanelManager", "Input connection is stale or invalid")
                            return@setOnClickListener
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("UnifiedPanelManager", "Input connection validation failed: ${e.message}")
                        return@setOnClickListener
                    }
                    
                    // Commit text with error handling
                    try {
                        ic.commitText(text, 1)
                    } catch (e: Exception) {
                        android.util.Log.e("UnifiedPanelManager", "Error committing clipboard text: ${e.message}", e)
                        return@setOnClickListener
                    }
                    
                    // Only navigate back if commit succeeded
                    try {
                        onBackToKeyboard()
                    } catch (e: Exception) {
                        android.util.Log.w("UnifiedPanelManager", "Error navigating back to keyboard: ${e.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UnifiedPanelManager", "Unexpected error handling clipboard item tap: ${e.message}", e)
                }
            }
        }
    }
    
    // ========================================
    // AI PROCESSING METHODS
    // ========================================
    
    private fun processGrammarAction(action: GrammarAction) {
        val inputText = currentInputText
        if (inputText.isBlank()) {
            // Toast removed - input validation logged only
            return
        }
        lastGrammarRequestedInput = inputText.trim()
        val card = grammarResultCard ?: return
        val shimmer = grammarShimmerContainer
        
        // Show shimmer, hide card
        card.container.visibility = View.GONE
        shimmer?.visibility = View.VISIBLE
        shimmer?.let { startShimmerAnimation(it) }
        
        card.translation.visibility = View.GONE
        card.translation.text = ""
        card.replaceButton.isEnabled = false
        grammarTranslationJob?.cancel()
        grammarTranslationJob = null

        scope.launch {
            val result = try {
                when {
                    action.feature != null -> advancedAIService.processText(inputText, action.feature)
                    !action.customPrompt.isNullOrBlank() -> advancedAIService.processCustomPrompt(
                        inputText,
                        action.customPrompt,
                        "grammar_${action.name.lowercase()}"
                    )
                    else -> advancedAIService.processText(
                        inputText,
                        AdvancedAIService.ProcessingFeature.GRAMMAR_FIX
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Hide shimmer, show card
                    shimmer?.clearAnimation()
                    shimmer?.visibility = View.GONE
                    card.container.visibility = View.VISIBLE
                    
                    card.primary.text = "❌ Error: ${e.message}"
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (result.success) {
                    displayGrammarResult(result.text)
                } else {
                    // Hide shimmer, show card
                    shimmer?.clearAnimation()
                    shimmer?.visibility = View.GONE
                    card.container.visibility = View.VISIBLE
                    
                    card.primary.text = "❌ Error: ${result.error}"
                }
            }
        }
    }

    private fun processGrammarCustomPrompt(prompt: String, title: String) {
        val inputText = currentInputText
        if (inputText.isBlank()) {
            // Toast removed - input validation logged only
            return
        }
        lastGrammarRequestedInput = inputText.trim()
        val card = grammarResultCard ?: return
        val shimmer = grammarShimmerContainer
        
        // Show shimmer, hide card
        card.container.visibility = View.GONE
        shimmer?.visibility = View.VISIBLE
        shimmer?.let { startShimmerAnimation(it) }
        
        card.translation.visibility = View.GONE
        card.translation.text = ""
        card.replaceButton.isEnabled = false
        grammarTranslationJob?.cancel()
        grammarTranslationJob = null

        scope.launch {
            val result = try {
                advancedAIService.processCustomPrompt(
                    inputText,
                    prompt,
                    "grammar_custom_${title.hashCode()}"
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Hide shimmer, show card
                    shimmer?.clearAnimation()
                    shimmer?.visibility = View.GONE
                    card.container.visibility = View.VISIBLE
                    
                    card.primary.text = "❌ Error: ${e.message}"
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (result.success) {
                    displayGrammarResult(result.text)
                } else {
                    // Hide shimmer, show card
                    shimmer?.clearAnimation()
                    shimmer?.visibility = View.GONE
                    card.container.visibility = View.VISIBLE
                    
                    card.primary.text = "❌ Error: ${result.error}"
                }
            }
        }
    }

    private fun displayGrammarResult(text: String) {
        val card = grammarResultCard ?: return
        val shimmer = grammarShimmerContainer
        
        // Hide shimmer, show card
        shimmer?.clearAnimation()
        shimmer?.visibility = View.GONE
        card.container.visibility = View.VISIBLE
        
        lastGrammarResult = text
        lastGrammarRequestedInput = currentInputText.trim()
        card.primary.text = text
        card.replaceButton.isEnabled = true
        refreshGrammarTranslation()
    }

    private fun refreshGrammarTranslation() {
        val card = grammarResultCard ?: return
        grammarTranslationJob?.cancel()
        card.translation.text = ""
        card.translation.visibility = View.GONE

        if (!shouldTranslate(grammarLanguage) || lastGrammarResult.isBlank()) {
            return
        }

        card.translation.visibility = View.VISIBLE
        card.translation.text = context.getString(R.string.translation_loading, grammarLanguage.label)
        grammarTranslationJob = scope.launch {
            val result = advancedAIService.translateText(lastGrammarResult, grammarLanguage.value)
            withContext(Dispatchers.Main) {
                if (result.success && result.text.isNotBlank()) {
                    card.translation.text = result.text
                } else {
                    card.translation.text = context.getString(R.string.translation_error, grammarLanguage.label)
                }
            }
        }
    }

    private fun processToneAction(action: ToneAction) {
        requestToneVariants(action.prompt, "tone_${action.name.lowercase()}")
    }

    private fun processCustomTonePrompt(prompt: String, title: String) {
        requestToneVariants(prompt, "tone_custom_${title.hashCode()}")
    }

    private fun requestToneVariants(instruction: String, cacheKey: String) {
        val inputText = currentInputText
        if (inputText.isBlank()) {
            // Toast removed - input validation logged only
            return
        }
        lastToneRequestedInput = inputText.trim()
        if (toneResultCards.isEmpty()) return
        val shimmer = toneShimmerContainer

        // Show shimmer, hide cards
        toneResultCards.forEach { it.container.visibility = View.GONE }
        shimmer?.visibility = View.VISIBLE
        shimmer?.let { startShimmerAnimation(it) }

        toneTranslationJobs.forEach { it.cancel() }
        toneTranslationJobs.clear()
        toneResultCards.forEachIndexed { index, card ->
            card.translation.visibility = View.GONE
            card.translation.text = ""
            if (index >= lastToneResults.size) {
                lastToneResults.add("")
            } else {
                lastToneResults[index] = ""
            }
        }

        scope.launch {
            val systemPrompt = """
                You are rewriting the user's message using this instruction:
                $instruction

                Produce exactly ${toneResultCards.size} distinct variations.
                
                Formatting rules (all are mandatory):
                • Each variation must be a single paragraph (no manual line breaks).
                • Separate variations with a line that contains exactly three dashes (---).
                • Do not number, label, or explain the variations.
                • Avoid repeating the original text verbatim; each variation should feel unique while honoring the instruction.

                Example format (do NOT include the words “Variation 1” etc.):
                <rewrite option 1>
                ---
                <rewrite option 2>
                ---
                <rewrite option 3>
            """.trimIndent()

            val result = try {
                advancedAIService.processCustomPrompt(inputText, systemPrompt, cacheKey)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Hide shimmer, show cards
                    shimmer?.clearAnimation()
                    shimmer?.visibility = View.GONE
                    toneResultCards.forEach { it.container.visibility = View.VISIBLE }
                    
                    toneResultCards.firstOrNull()?.primary?.text = "❌ Error: ${e.message}"
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                // Hide shimmer, show cards
                shimmer?.clearAnimation()
                shimmer?.visibility = View.GONE
                toneResultCards.forEach { it.container.visibility = View.VISIBLE }
                
                if (result.success) {
                    val variants = parseVariants(result.text, toneResultCards.size)
                    toneResultCards.forEachIndexed { index, card ->
                        val text = variants.getOrNull(index).orEmpty()
                        card.primary.text = if (text.isBlank()) {
                            "⚠️ Couldn't create variation ${index + 1}"
                        } else {
                            text
                        }
                        if (index >= lastToneResults.size) {
                            lastToneResults.add(text)
                        } else {
                            lastToneResults[index] = text
                        }
                    }
                    refreshToneTranslations()
                    lastToneRequestedInput = currentInputText.trim()
                } else {
                    toneResultCards.firstOrNull()?.primary?.text = "❌ Error: ${result.error}"
                }
            }
        }
    }

    private fun refreshToneTranslations() {
        toneTranslationJobs.forEach { it.cancel() }
        toneTranslationJobs.clear()

        if (!shouldTranslate(toneLanguage)) {
            toneResultCards.forEach { it.translation.visibility = View.GONE }
            return
        }

        toneResultCards.forEachIndexed { index, card ->
            val text = lastToneResults.getOrNull(index).orEmpty()
            if (text.isBlank()) {
                card.translation.visibility = View.GONE
            } else {
                card.translation.visibility = View.VISIBLE
                card.translation.text = context.getString(R.string.translation_loading, toneLanguage.label)
                val job = scope.launch {
                    val translation = advancedAIService.translateText(text, toneLanguage.value)
                    withContext(Dispatchers.Main) {
                        if (translation.success && translation.text.isNotBlank()) {
                            card.translation.text = translation.text
                        } else {
                            card.translation.text = context.getString(R.string.translation_error, toneLanguage.label)
                        }
                    }
                }
                toneTranslationJobs.add(job)
            }
        }
    }

    private fun shouldTranslate(language: LanguageOption): Boolean {
        return !language.value.equals("English", ignoreCase = true)
    }

    private fun resolveTextForLanguage(
        primary: String,
        translation: CharSequence?,
        language: LanguageOption
    ): String {
        if (shouldTranslate(language)) {
            val loading = context.getString(R.string.translation_loading, language.label)
            val error = context.getString(R.string.translation_error, language.label)
            val candidate = translation?.toString()?.trim().orEmpty()
            if (candidate.isNotEmpty() && candidate != loading && candidate != error) {
                return candidate
            }
        }
        return primary
    }

    private fun parseVariants(raw: String, expected: Int): List<String> {
        val normalized = raw.replace("\r", "").trim()
        if (normalized.isBlank()) return emptyList()

        fun cleanVariant(text: String): String {
            return text.trim()
                .replace(Regex("^(?i)(variation\\s*\\d+[:.)-]?\\s+)"), "")
                .replace(Regex("^\\d+[.)\\-\\s]+"), "")
                .trimStart('-', '•', '*', '·')
                .trim()
        }

        val dashSplit = normalized
            .split(Regex("\\n+\\s*---+\\s*\\n+"))
            .map { cleanVariant(it) }
            .filter { it.isNotEmpty() }

        if (dashSplit.size >= expected) {
            return dashSplit.take(expected)
        }

        val variants = mutableListOf<String>()
        val current = StringBuilder()
        val variantStartPattern = Regex("^(?i)(variation\\s*\\d+|var\\.?\\s*\\d+|\\d+)[).\\-:]?\\s+")

        fun flush() {
            if (current.isNotEmpty()) {
                val cleaned = cleanVariant(current.toString())
                if (cleaned.isNotEmpty()) variants.add(cleaned)
                current.clear()
            }
        }

        normalized.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            if (variantStartPattern.containsMatchIn(trimmed) && current.isNotEmpty()) {
                flush()
            }

            val sanitized = trimmed.replace(variantStartPattern, "").trim()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sanitized)
        }
        flush()

        if (variants.size >= expected) {
            return variants.take(expected)
        }

        return if (dashSplit.isNotEmpty()) dashSplit else variants
    }
    
    private fun processAIAction(prompt: String, outputView: TextView) {
        val inputText = currentInputText
        if (inputText.isBlank()) {
            // Toast removed - input validation logged only
            return
        }
        
        outputView.text = "⏳ Processing..."
        
        scope.launch {
            try {
                val result = advancedAIService.processText(
                    inputText,
                    AdvancedAIService.ProcessingFeature.SIMPLIFY
                )
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        outputView.text = result.text
                        onTextProcessedCallback?.invoke(result.text)
                    } else {
                        outputView.text = "❌ Error: ${result.error}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputView.text = "❌ Error: ${e.message}"
                }
            }
        }
    }
    
    private fun processTranslateAction(targetLanguage: String) {
        val outputView = translateOutputView ?: return
        val inputText = currentInputText.trim()
        if (inputText.isBlank()) {
            // Toast removed - translate validation logged only
            updateTranslateState(false)
            return
        }

        translateJob?.cancel()
        translateJob = null
        translateReplaceButton?.isEnabled = false
        translateCopyButton?.isEnabled = false
        translateCopyButton?.alpha = 0.4f
        translateStatusText?.text = "Translating to $targetLanguage..."
        outputView.text = "⏳ Translating to $targetLanguage..."

        translateJob = scope.launch {
            try {
                val result = advancedAIService.translateText(inputText, targetLanguage)
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        outputView.text = result.text
                        translateStatusText?.text = "Translated to $targetLanguage"
                        translateReplaceButton?.isEnabled = true
                        translateCopyButton?.isEnabled = true
                        translateCopyButton?.alpha = 1f
                    } else {
                        outputView.text = "❌ Error: ${result.error}"
                        translateStatusText?.text = "Translation failed"
                        translateReplaceButton?.isEnabled = false
                        translateCopyButton?.isEnabled = false
                        translateCopyButton?.alpha = 0.4f
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputView.text = "❌ Error: ${e.message}"
                    translateStatusText?.text = "Translation failed"
                    translateReplaceButton?.isEnabled = false
                    translateCopyButton?.isEnabled = false
                    translateCopyButton?.alpha = 0.4f
                }
            }
        }
    }

    private fun processCustomAIPrompt(
        prompt: String,
        title: String,
        statusText: TextView,
        resultContainer: LinearLayout,
        replaceButton: Button
    ) {
        val inputText = currentInputText
        if (inputText.isBlank()) {
            statusText.text = "Please type something first"
            return
        }

        statusText.text = "⏳ Processing with $title..."
        resultContainer.removeAllViews()
        replaceButton.visibility = View.GONE
        lastAiResult = ""

        scope.launch {
            try {
                val result = unifiedAIService.processCustomPrompt(
                    text = inputText,
                    prompt = prompt,
                    stream = false
                ).first()

                withContext(Dispatchers.Main) {
                    if (result.success && result.text.isNotBlank()) {
                        displayAIResults(result.text, statusText, resultContainer, replaceButton)
                    } else {
                        statusText.text = "❌ Error: ${result.error ?: "No response"}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "❌ Error: ${e.message}"
                }
            }
        }
    }

    private fun displayAIResults(
        text: String,
        statusText: TextView,
        resultContainer: LinearLayout,
        replaceButton: Button
    ) {
        val palette = PanelTheme.palette
        
        // Parse multiple results if newline-separated
        val results = text.split("\n")
            .map { it.trim().trimStart('-', '•', '*').trim() }
            .filter { it.isNotEmpty() }
            .take(3)

        if (results.isEmpty()) {
            statusText.text = "No results available"
            return
        }

        lastAiResult = results.first()
        statusText.text = "Tap a suggestion to use it"

        results.forEach { result ->
            val card = TextView(context).apply {
                this.text = result
                textSize = 13f
                setLineSpacing(0f, 1.1f)
                setTextColor(Color.WHITE)
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
                background = GradientDrawable().apply {
                    cornerRadius = dpToPx(14).toFloat()
                    setColor(ColorUtils.blendARGB(palette.keyBg, Color.BLACK, 0.45f))
                    setStroke(1, ColorUtils.setAlphaComponent(palette.specialAccent, 120))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(8)
                }
                setOnClickListener {
                    lastAiResult = result
                    onTextProcessedCallback?.invoke(result)
                    onBackToKeyboard()
                }
            }
            resultContainer.addView(card)
        }

        replaceButton.visibility = View.VISIBLE
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        advancedAIService.cleanup()
        unifiedAIService.cleanup()
        emojiPanelController = null
        grammarPanelView = null
        tonePanelView = null
        aiAssistantPanelView = null
        translatePanelView = null
        clipboardPanelView = null
        emojiPanelView = null
        settingsPanelView = null
        LogUtil.d(TAG, "UnifiedPanelManager cleaned up")
    }
}
