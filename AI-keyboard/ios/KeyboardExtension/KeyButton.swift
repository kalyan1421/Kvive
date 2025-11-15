//
//  KeyButton.swift
//  KeyboardExtension
//
//  Reusable UIButton subclass for keyboard keys with enhanced styling and interaction
//

import UIKit
import AudioToolbox

class KeyButton: UIButton {
    
    // MARK: - Properties
    var keyValue: String
    var keyType: KeyType = .character
    private var settingsManager = SettingsManager.shared
    
    // Key preview properties
    private var previewView: UIView?
    private var previewLabel: UILabel?
    
    // MARK: - Key Types
    enum KeyType: Equatable {
        case character
        case shift
        case delete
        case space
        case returnKey
        case number
        case globe
        case special(String)
    }
    
    // MARK: - Initialization
    init(_ keyValue: String, type: KeyType = .character) {
        self.keyValue = keyValue
        self.keyType = type
        super.init(frame: .zero)
        setupButton()
        setupActions()
    }
    
    required init?(coder: NSCoder) {
        self.keyValue = ""
        super.init(coder: coder)
        setupButton()
        setupActions()
    }
    
    // MARK: - Setup Methods
    private func setupButton() {
        translatesAutoresizingMaskIntoConstraints = false
        
        // Basic styling
        layer.cornerRadius = 6
        layer.borderWidth = 1
        titleLabel?.font = UIFont.systemFont(ofSize: getFontSize(), weight: .medium)
        
        // Set title based on key type
        setTitle(getDisplayTitle(), for: .normal)
        
        // Apply theme
        updateAppearance()
        
        // Enable user interaction
        isUserInteractionEnabled = true
        
        // Add accessibility
        setupAccessibility()
    }
    
    private func setupActions() {
        addTarget(self, action: #selector(keyTouchDown), for: .touchDown)
        addTarget(self, action: #selector(keyTouchUpInside), for: .touchUpInside)
        addTarget(self, action: #selector(keyTouchUpOutside), for: .touchUpOutside)
        addTarget(self, action: #selector(keyTouchCancel), for: .touchCancel)
    }
    
    // MARK: - Display Configuration
    private func getDisplayTitle() -> String {
        switch keyType {
        case .character:
            return keyValue
        case .shift:
            return "⇧"
        case .delete:
            return "⌫"
        case .space:
            return "space"
        case .returnKey:
            return "return"
        case .number:
            return "123"
        case .globe:
            return "🌐"
        case .special(let title):
            return title
        }
    }
    
    private func getFontSize() -> CGFloat {
        switch keyType {
        case .character, .number:
            return 18
        case .shift, .delete:
            return 16
        case .space, .returnKey:
            return 14
        case .globe:
            return 16
        case .special:
            return 16
        }
    }
    
    // MARK: - Appearance Updates
    func updateAppearance(isDarkMode: Bool? = nil) {
        let darkMode = isDarkMode ?? (traitCollection.userInterfaceStyle == .dark)
        
        // Background colors
        switch keyType {
        case .character, .number:
            backgroundColor = darkMode ? UIColor.systemGray4 : UIColor.white
        case .shift, .delete, .returnKey:
            backgroundColor = darkMode ? UIColor.systemGray3 : UIColor.systemGray5
        case .space:
            backgroundColor = darkMode ? UIColor.systemGray3 : UIColor.systemGray5
        case .globe:
            backgroundColor = darkMode ? UIColor.systemGray4 : UIColor.white
        case .special:
            backgroundColor = darkMode ? UIColor.systemGray3 : UIColor.systemGray5
        }
        
        // Text colors
        setTitleColor(darkMode ? UIColor.white : UIColor.black, for: .normal)
        setTitleColor(darkMode ? UIColor.lightGray : UIColor.darkGray, for: .highlighted)
        
        // Border colors
        layer.borderColor = darkMode ? UIColor.systemGray3.cgColor : UIColor.systemGray4.cgColor
        
        // Shadow for depth (optional)
        if settingsManager.visualIntensity >= 2 {
            layer.shadowColor = UIColor.black.cgColor
            layer.shadowOffset = CGSize(width: 0, height: 1)
            layer.shadowOpacity = darkMode ? 0.3 : 0.1
            layer.shadowRadius = 1
        } else {
            layer.shadowOpacity = 0
        }
    }
    
    // MARK: - Special State Updates
    func updateForShiftState(_ shiftState: ShiftState) {
        guard keyType == .shift else { return }
        
        let isDarkMode = traitCollection.userInterfaceStyle == .dark
        
        switch shiftState {
        case .normal:
            backgroundColor = isDarkMode ? UIColor.systemGray4 : UIColor.systemGray5
            tintColor = UIColor.label
            alpha = 0.7
            layer.borderWidth = 1
            layer.borderColor = isDarkMode ? UIColor.systemGray3.cgColor : UIColor.systemGray4.cgColor
            
        case .shift:
            backgroundColor = UIColor.systemBlue
            tintColor = UIColor.white
            alpha = 1.0
            layer.borderWidth = 1
            layer.borderColor = UIColor.systemBlue.cgColor
            
        case .capsLock:
            backgroundColor = UIColor.systemOrange
            tintColor = UIColor.white
            alpha = 1.0
            layer.borderWidth = 2.0
            layer.borderColor = UIColor.systemOrange.cgColor
        }
    }
    
    // MARK: - Touch Handling
    @objc private func keyTouchDown() {
        // Visual feedback on press
        animatePress(down: true)
        
        // Show key preview if enabled
        if settingsManager.keyPreviewEnabled {
            showKeyPreview()
        }
        
        // Haptic feedback
        if settingsManager.vibrationEnabled && settingsManager.hapticIntensity > 0 {
            let feedbackStyle = getHapticStyle()
            let impactFeedback = UIImpactFeedbackGenerator(style: feedbackStyle)
            impactFeedback.impactOccurred()
        }
    }
    
    @objc private func keyTouchUpInside() {
        // Release animation
        animatePress(down: false)
        
        // Hide key preview
        hideKeyPreview()
        
        // Sound feedback
        if settingsManager.soundIntensity > 0 {
            playKeySound()
        }
        
        // Notify parent controller - directly call the appropriate action
        if let keyboardController = findKeyboardViewController() {
            handleKeyAction(keyboardController)
        }
    }
    
    // MARK: - Key Action Handling
    private func handleKeyAction(_ controller: KeyboardViewController) {
        switch keyType {
        case .character:
            // Apply shift state
            let character: String
            switch controller.shiftState {
            case .normal:
                character = keyValue.lowercased()
            case .shift:
                character = keyValue.uppercased()
                controller.shiftState = .normal  // Auto-reset after one character
            case .capsLock:
                character = keyValue.uppercased()
            }
            controller.safeInsertText(character)
            
        case .shift:
            controller.shiftPressed()
            
        case .delete:
            controller.safeDeleteBackward()
            
        case .space:
            controller.safeInsertText(" ")
            
        case .returnKey:
            controller.safeInsertText("\n")
            
        case .number:
            controller.numbersPressed()
            
        case .globe:
            controller.advanceToNextInputMode()
            
        case .special(let action):
            // Handle layout switching keys
            switch action {
            case "ABC":
                controller.switchToLayout(.alphabetic)
            case "123":
                controller.switchToLayout(.numeric)
            case "#+=":
                controller.switchToLayout(.symbols)
            default:
                print("Unknown special action: \(action)")
            }
        }
    }
    
    @objc private func keyTouchUpOutside() {
        animatePress(down: false)
        hideKeyPreview()
    }
    
    @objc private func keyTouchCancel() {
        animatePress(down: false)
        hideKeyPreview()
    }
    
    // MARK: - Animation Methods
    private func animatePress(down: Bool) {
        guard settingsManager.visualIntensity > 0 else { return }
        
        let animationIntensity = Double(settingsManager.visualIntensity) / 3.0
        let scaleDown = 0.95 - (0.05 * (1.0 - animationIntensity))
        let duration = 0.1 + (0.05 * animationIntensity)
        
        if down {
            // Press down animation
            UIView.animate(withDuration: duration) {
                self.transform = CGAffineTransform(scaleX: scaleDown, y: scaleDown)
                self.alpha = 0.8
            }
        } else {
            // Release animation with spring effect
            UIView.animate(withDuration: duration * 1.5,
                          delay: 0,
                          usingSpringWithDamping: 0.6,
                          initialSpringVelocity: 0.8,
                          options: .curveEaseOut) {
                self.transform = CGAffineTransform.identity
                self.alpha = 1.0
            }
        }
        
        // Additional brightness effect for higher intensities
        if settingsManager.visualIntensity >= 3 && down {
            UIView.animate(withDuration: duration) {
                self.backgroundColor = self.backgroundColor?.withAlphaComponent(0.7)
            } completion: { _ in
                UIView.animate(withDuration: duration) {
                    self.backgroundColor = self.backgroundColor?.withAlphaComponent(1.0)
                }
            }
        }
    }
    
    // MARK: - Feedback Methods
    private func getHapticStyle() -> UIImpactFeedbackGenerator.FeedbackStyle {
        switch settingsManager.hapticIntensity {
        case 1:
            return .light
        case 2:
            return .medium
        case 3:
            return .heavy
        default:
            return .medium
        }
    }
    
    private func playKeySound() {
        let soundID = getSoundID()
        AudioServicesPlaySystemSound(soundID)
    }
    
    private func getSoundID() -> SystemSoundID {
        switch keyType {
        case .space:
            return 1104 // Spacebar sound
        case .delete:
            return 1155 // Delete sound
        case .returnKey:
            return 1156 // Return sound
        default:
            return 1103 // Standard key sound
        }
    }
    
    // MARK: - Accessibility
    private func setupAccessibility() {
        isAccessibilityElement = true
        
        switch keyType {
        case .character:
            accessibilityLabel = "Key \(keyValue)"
            accessibilityHint = "Double tap to type \(keyValue)"
        case .shift:
            accessibilityLabel = "Shift"
            accessibilityHint = "Double tap to toggle case"
        case .delete:
            accessibilityLabel = "Delete"
            accessibilityHint = "Double tap to delete previous character"
        case .space:
            accessibilityLabel = "Space"
            accessibilityHint = "Double tap to insert space"
        case .returnKey:
            accessibilityLabel = "Return"
            accessibilityHint = "Double tap to insert new line"
        case .number:
            accessibilityLabel = "Numbers"
            accessibilityHint = "Double tap to switch to number keyboard"
        case .globe:
            accessibilityLabel = "Next keyboard"
            accessibilityHint = "Double tap to switch to next keyboard"
        case .special(let title):
            accessibilityLabel = title
            accessibilityHint = "Double tap to activate \(title)"
        }
    }
    
    // MARK: - Key Preview Methods
    
    private func showKeyPreview() {
        // Only show preview for character and number keys
        guard keyType == .character || keyType == .number else { return }
        guard let currentTitle = currentTitle else { return }
        
        // Remove existing preview if any
        hideKeyPreview()
        
        // Create preview container
        let preview = UIView()
        preview.backgroundColor = UIColor.systemBackground
        preview.layer.cornerRadius = 8
        preview.layer.borderWidth = 1
        preview.layer.borderColor = UIColor.systemGray3.cgColor
        preview.layer.shadowColor = UIColor.black.cgColor
        preview.layer.shadowOffset = CGSize(width: 0, height: 2)
        preview.layer.shadowRadius = 4
        preview.layer.shadowOpacity = 0.3
        preview.translatesAutoresizingMaskIntoConstraints = false
        
        // Create label
        let label = UILabel()
        label.text = currentTitle
        label.font = UIFont.systemFont(ofSize: 32, weight: .regular)
        label.textAlignment = .center
        label.textColor = UIColor.label
        label.translatesAutoresizingMaskIntoConstraints = false
        
        preview.addSubview(label)
        
        // Add to superview (keyboard container)
        guard let container = superview else { return }
        container.addSubview(preview)
        
        // Setup constraints
        NSLayoutConstraint.activate([
            preview.centerXAnchor.constraint(equalTo: centerXAnchor),
            preview.bottomAnchor.constraint(equalTo: topAnchor, constant: -8),
            preview.widthAnchor.constraint(equalToConstant: 60),
            preview.heightAnchor.constraint(equalToConstant: 70),
            
            label.centerXAnchor.constraint(equalTo: preview.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: preview.centerYAnchor)
        ])
        
        // Animate appearance
        preview.alpha = 0
        preview.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        UIView.animate(withDuration: 0.1) {
            preview.alpha = 1.0
            preview.transform = CGAffineTransform.identity
        }
        
        previewView = preview
        previewLabel = label
    }
    
    private func hideKeyPreview() {
        guard let preview = previewView else { return }
        
        UIView.animate(withDuration: 0.1, animations: {
            preview.alpha = 0
            preview.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        }) { _ in
            preview.removeFromSuperview()
        }
        
        previewView = nil
        previewLabel = nil
    }
    
    // MARK: - Helper Methods
    private func findKeyboardViewController() -> KeyboardViewController? {
        var responder: UIResponder? = self
        while let nextResponder = responder?.next {
            if let keyboardVC = nextResponder as? KeyboardViewController {
                return keyboardVC
            }
            responder = nextResponder
        }
        return nil
    }
}

// MARK: - Shift State Enum
enum ShiftState {
    case normal
    case shift
    case capsLock
}

