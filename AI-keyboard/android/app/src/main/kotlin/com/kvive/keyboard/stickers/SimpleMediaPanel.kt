package com.kvive.keyboard.stickers

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kvive.keyboard.EmojiPanelController
import com.kvive.keyboard.ThemeManager
import com.kvive.keyboard.themes.KeyboardThemeV2
import com.kvive.keyboard.themes.ThemePaletteV2

/**
 * Simple tabbed media tray that lets users toggle between emoji and sticker surfaces.
 * Emoji rendering is delegated to EmojiPanelController, while stickers are rendered via StickerPanel.
 */
class SimpleMediaPanel(
    context: Context,
    private val themeManager: ThemeManager,
    private val emojiPanelController: EmojiPanelController
) : LinearLayout(context), ThemeManager.ThemeChangeListener {

    private val palette: ThemePaletteV2
        get() = themeManager.getCurrentPalette()

    private val tabContainer: LinearLayout
    private val tabEmoji: TextView
    private val tabStickers: TextView
    private val contentFrame: FrameLayout
    private val emojiView: View
    private val stickerPanel: StickerPanel

    private var activeTab: Tab = Tab.EMOJI

    private enum class Tab { EMOJI, STICKERS }

    init {
        orientation = VERTICAL
        themeManager.addThemeChangeListener(this)
        setPadding(0, 0, 0, 0)

        tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            gravity = Gravity.CENTER
        }

        tabEmoji = createTab("Emoji") { showTab(Tab.EMOJI) }
        tabStickers = createTab("Stickers") { showTab(Tab.STICKERS) }

        tabContainer.addView(tabEmoji, tabLayoutParams())
        tabContainer.addView(tabStickers, tabLayoutParams())
        addView(tabContainer)

        contentFrame = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(contentFrame)

        stickerPanel = StickerPanel(context, themeManager)
        emojiPanelController.useStickerPanel(stickerPanel)
        emojiView = emojiPanelController.inflate(contentFrame)
        contentFrame.addView(emojiView)

        applyTheme()
        showTab(Tab.EMOJI)
    }

    fun show(forceRefresh: Boolean = false) {
        if (activeTab == Tab.STICKERS) {
            emojiPanelController.requestStickerSurface(forceRefresh)
        } else {
            emojiPanelController.requestEmojiSurface()
        }
    }

    fun onDestroy() {
        themeManager.removeThemeChangeListener(this)
        stickerPanel.onDestroy()
    }

    private fun showTab(tab: Tab, forceRefresh: Boolean = false) {
        activeTab = tab
        when (tab) {
            Tab.EMOJI -> {
                emojiPanelController.requestEmojiSurface()
            }
            Tab.STICKERS -> {
                emojiPanelController.requestStickerSurface(forceRefresh)
            }
        }
        updateTabStyles()
    }

    private fun createTab(title: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = title
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
        }
    }

    private fun tabLayoutParams(): LayoutParams {
        return LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }
    }

    private fun updateTabStyles() {
        val palette = palette
        fun TextView.applyStyle(isActive: Boolean) {
            val bgColor = if (isActive) palette.specialAccent else palette.keyBg
            val textColor = if (isActive) palette.panelSurface else palette.keyText
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(18).toFloat()
                setColor(bgColor)
            }
            setTextColor(textColor)
            alpha = if (isActive) 1f else 0.8f
        }

        tabEmoji.applyStyle(activeTab == Tab.EMOJI)
        tabStickers.applyStyle(activeTab == Tab.STICKERS)
    }

    private fun applyTheme() {
        setBackgroundColor(palette.panelSurface)
        tabContainer.setBackgroundColor(palette.panelSurface)
        updateTabStyles()
    }

    override fun onThemeChanged(theme: KeyboardThemeV2, palette: ThemePaletteV2) {
        applyTheme()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
