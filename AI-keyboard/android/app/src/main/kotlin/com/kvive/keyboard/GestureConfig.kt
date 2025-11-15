package com.kvive.keyboard

/**
 * Canonical gesture configuration shared between Flutter UI and native keyboard.
 */
data class GestureSettings(
    val glideTyping: Boolean = true,
    val showGlideTrail: Boolean = true,
    val glideTrailFadeMs: Int = 200,
    val alwaysDeleteWord: Boolean = true,
    val swipeVelocityThreshold: Float = 1900f,
    val swipeDistanceThreshold: Float = 20f,
    val swipeUpAction: GestureAction = GestureAction.SHIFT,
    val swipeDownAction: GestureAction = GestureAction.HIDE_KEYBOARD,
    val swipeLeftAction: GestureAction = GestureAction.DELETE_CHARACTER_BEFORE_CURSOR,
    val swipeRightAction: GestureAction = GestureAction.INSERT_SPACE,
    val spaceLongPressAction: GestureAction = GestureAction.SHOW_INPUT_METHOD_PICKER,
    val spaceSwipeDownAction: GestureAction = GestureAction.NONE,
    val spaceSwipeLeftAction: GestureAction = GestureAction.MOVE_CURSOR_LEFT,
    val spaceSwipeRightAction: GestureAction = GestureAction.MOVE_CURSOR_RIGHT,
    val deleteSwipeLeftAction: GestureAction = GestureAction.DELETE_WORD_BEFORE_CURSOR,
    val deleteLongPressAction: GestureAction = GestureAction.DELETE_CHARACTER_BEFORE_CURSOR
) {
    companion object {
        val DEFAULT = GestureSettings()
    }
}

enum class GestureSource {
    GENERAL_SWIPE_UP,
    GENERAL_SWIPE_DOWN,
    GENERAL_SWIPE_LEFT,
    GENERAL_SWIPE_RIGHT,
    SPACE_LONG_PRESS,
    SPACE_SWIPE_DOWN,
    SPACE_SWIPE_LEFT,
    SPACE_SWIPE_RIGHT,
    DELETE_SWIPE_LEFT,
    DELETE_LONG_PRESS
}

enum class GestureAction(val code: String) {
    NONE("none"),
    CYCLE_PREV_MODE("cycle_prev_mode"),
    CYCLE_NEXT_MODE("cycle_next_mode"),
    DELETE_WORD_BEFORE_CURSOR("delete_word_before_cursor"),
    HIDE_KEYBOARD("hide_keyboard"),
    INSERT_SPACE("insert_space"),
    MOVE_CURSOR_UP("move_cursor_up"),
    MOVE_CURSOR_DOWN("move_cursor_down"),
    MOVE_CURSOR_LEFT("move_cursor_left"),
    MOVE_CURSOR_RIGHT("move_cursor_right"),
    MOVE_CURSOR_LINE_START("move_cursor_line_start"),
    MOVE_CURSOR_LINE_END("move_cursor_line_end"),
    MOVE_CURSOR_PAGE_START("move_cursor_page_start"),
    MOVE_CURSOR_PAGE_END("move_cursor_page_end"),
    SHIFT("shift"),
    REDO("redo"),
    UNDO("undo"),
    OPEN_CLIPBOARD("open_clipboard"),
    SHOW_INPUT_METHOD_PICKER("show_input_method_picker"),
    SWITCH_PREV_LANGUAGE("switch_prev_language"),
    SWITCH_NEXT_LANGUAGE("switch_next_language"),
    TOGGLE_SMARTBAR("toggle_smartbar"),
    DELETE_CHARACTERS_PRECISELY("delete_characters_precisely"),
    DELETE_CHARACTER_BEFORE_CURSOR("delete_character_before_cursor"),
    DELETE_WORD("delete_word"),
    DELETE_LINE("delete_line");

    companion object {
        fun fromCode(code: String?): GestureAction? = values().firstOrNull { it.code == code }
    }
}
