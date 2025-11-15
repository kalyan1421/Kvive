import Foundation

/// Manages settings synchronization between the main app and keyboard extension
class SettingsManager {
    static let shared = SettingsManager()
    
    private let appGroupIdentifier = "group.com.kvive.aikeyboard.shared"
    
    private var userDefaults: UserDefaults? {
        // Try to use App Groups, fallback to standard UserDefaults if not available
        return groupDefaults() ?? UserDefaults.standard
    }
    
    // MARK: - Safe App Groups Access
    private func groupDefaults() -> UserDefaults? {
        guard let defaults = UserDefaults(suiteName: appGroupIdentifier) else {
            print("Warning: App Group '\(appGroupIdentifier)' not available, using standard UserDefaults")
            return nil
        }
        return defaults
    }
    
    private func groupContainerURL() -> URL? {
        guard let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier) else {
            print("Warning: App Group container URL not available for '\(appGroupIdentifier)'")
            return nil
        }
        return url
    }
    
    init() {
        // Simple initialization without complex notifications for now
    }
    
    // MARK: - Settings Properties
    
    var keyboardTheme: String {
        get { userDefaults?.string(forKey: "keyboard_theme") ?? "default_theme" }
        set { 
            userDefaults?.set(newValue, forKey: "keyboard_theme")
            userDefaults?.synchronize()
        }
    }
    
    var aiSuggestionsEnabled: Bool {
        get { userDefaults?.bool(forKey: "ai_suggestions") ?? true }
        set { 
            userDefaults?.set(newValue, forKey: "ai_suggestions")
            userDefaults?.synchronize()
        }
    }
    
    var swipeTypingEnabled: Bool {
        get { userDefaults?.bool(forKey: "swipe_typing") ?? true }
        set { 
            userDefaults?.set(newValue, forKey: "swipe_typing")
            userDefaults?.synchronize()
        }
    }
    
    var voiceInputEnabled: Bool {
        get { userDefaults?.bool(forKey: "voice_input") ?? true }
        set { 
            userDefaults?.set(newValue, forKey: "voice_input")
            userDefaults?.synchronize()
        }
    }
    
    var vibrationEnabled: Bool {
        get { userDefaults?.bool(forKey: "vibration_enabled") ?? true }
        set { 
            userDefaults?.set(newValue, forKey: "vibration_enabled")
            userDefaults?.synchronize()
        }
    }
    
    var keyPreviewEnabled: Bool {
        get { userDefaults?.bool(forKey: "key_preview_enabled") ?? false }
        set {
            userDefaults?.set(newValue, forKey: "key_preview_enabled")
            userDefaults?.synchronize()
        }
    }
    
    var autoCapitalizationEnabled: Bool {
        get { userDefaults?.bool(forKey: "auto_capitalization") ?? true }
        set {
            userDefaults?.set(newValue, forKey: "auto_capitalization")
            userDefaults?.synchronize()
        }
    }
    
    var contextAwareCapitalizationEnabled: Bool {
        get { userDefaults?.bool(forKey: "context_aware_capitalization") ?? true }
        set {
            userDefaults?.set(newValue, forKey: "context_aware_capitalization")
            userDefaults?.synchronize()
        }
    }
    
    var shiftFeedbackEnabled: Bool {
        get { userDefaults?.bool(forKey: "show_shift_feedback") ?? false }
        set {
            userDefaults?.set(newValue, forKey: "show_shift_feedback")
            userDefaults?.synchronize()
        }
    }
    
    // MARK: - Advanced Feedback Settings
    
    var hapticIntensity: Int {
        get { userDefaults?.integer(forKey: "haptic_intensity") ?? 2 } // medium by default
        set {
            userDefaults?.set(newValue, forKey: "haptic_intensity")
            userDefaults?.synchronize()
        }
    }
    
    var soundIntensity: Int {
        get { userDefaults?.integer(forKey: "sound_intensity") ?? 0 }
        set {
            userDefaults?.set(newValue, forKey: "sound_intensity")
            userDefaults?.synchronize()
        }
    }
    
    var visualIntensity: Int {
        get { userDefaults?.integer(forKey: "visual_intensity") ?? 0 }
        set {
            userDefaults?.set(newValue, forKey: "visual_intensity")
            userDefaults?.synchronize()
        }
    }
    
    var soundVolume: Float {
        get { userDefaults?.float(forKey: "sound_volume") ?? 0.3 }
        set {
            userDefaults?.set(newValue, forKey: "sound_volume")
            userDefaults?.synchronize()
        }
    }
    
    // Convenience property for current theme access (matching Android implementation)
    var currentTheme: String {
        get { return keyboardTheme }
        set { keyboardTheme = newValue }
    }
    
    // MARK: - Bulk Operations
    
    func loadSettings() {
        // Force synchronize to get latest settings from shared storage
        userDefaults?.synchronize()
    }
    
    func loadAllSettings() -> KeyboardSettings {
        return KeyboardSettings(
            theme: keyboardTheme,
            aiSuggestions: aiSuggestionsEnabled,
            swipeTyping: swipeTypingEnabled,
            voiceInput: voiceInputEnabled,
            vibration: vibrationEnabled,
            keyPreview: keyPreviewEnabled,
            autoCapitalization: autoCapitalizationEnabled,
            contextAwareCapitalization: contextAwareCapitalizationEnabled,
            shiftFeedback: shiftFeedbackEnabled,
            hapticIntensity: hapticIntensity,
            soundIntensity: soundIntensity,
            visualIntensity: visualIntensity,
            soundVolume: soundVolume
        )
    }
    
    func saveAllSettings(_ settings: KeyboardSettings) {
        keyboardTheme = settings.theme
        aiSuggestionsEnabled = settings.aiSuggestions
        swipeTypingEnabled = settings.swipeTyping
        voiceInputEnabled = settings.voiceInput
        vibrationEnabled = settings.vibration
        keyPreviewEnabled = settings.keyPreview
        autoCapitalizationEnabled = settings.autoCapitalization
        contextAwareCapitalizationEnabled = settings.contextAwareCapitalization
        shiftFeedbackEnabled = settings.shiftFeedback
        hapticIntensity = settings.hapticIntensity
        soundIntensity = settings.soundIntensity
        visualIntensity = settings.visualIntensity
        soundVolume = settings.soundVolume
    }
}

// MARK: - Settings Model

struct KeyboardSettings {
    let theme: String
    let aiSuggestions: Bool
    let swipeTyping: Bool
    let voiceInput: Bool
    let vibration: Bool
    let keyPreview: Bool
    let autoCapitalization: Bool
    let contextAwareCapitalization: Bool
    let shiftFeedback: Bool
    
    // Advanced feedback settings
    let hapticIntensity: Int
    let soundIntensity: Int
    let visualIntensity: Int
    let soundVolume: Float
}

// MARK: - Notification Extension

extension Notification.Name {
    static let settingsDidChange = Notification.Name("settingsDidChange")
}
