package com.kvive.keyboard

/**
 * Lightweight singleton for sharing keyboard state (shift / caps) across components.
 *
 * LanguageLayoutAdapter can query this to decide which keymap layer (lowercase / uppercase)
 * should be rendered, while services like AIKeyboardService push state updates when the user
 * toggles Shift or Caps Lock.
 */
object KeyboardStateManager {

    private val listeners = mutableSetOf<(Boolean, Boolean) -> Unit>()

    @Volatile
    private var shiftActive: Boolean = false

    @Volatile
    private var capsLockEnabled: Boolean = false

    fun isShiftActive(): Boolean = shiftActive

    fun isCapsLockEnabled(): Boolean = capsLockEnabled

    fun updateShiftState(isShiftActive: Boolean, isCapsLockEnabled: Boolean) {
        val changed = shiftActive != isShiftActive || capsLockEnabled != isCapsLockEnabled
        shiftActive = isShiftActive
        capsLockEnabled = isCapsLockEnabled

        if (changed) {
            listeners.forEach { listener ->
                listener.invoke(isShiftActive, isCapsLockEnabled)
            }
        }
    }

    fun addListener(listener: (Boolean, Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Boolean, Boolean) -> Unit) {
        listeners.remove(listener)
    }
}
