import UIKit
import Flutter

@main
@objc class AppDelegate: FlutterAppDelegate {
    
    // ✅ Explicit Flutter engine management
    var flutterEngine: FlutterEngine?
    
    private let CHANNEL = "ai_keyboard/config"
    
    private var keyboardExtensionBundleIds: [String] {
        var identifiers: [String] = []
        
        if let configured = Bundle.main.object(forInfoDictionaryKey: "AIKeyboardExtensionBundleId") as? String,
           !configured.isEmpty {
            identifiers.append(configured)
        }
        
        if let baseBundleId = Bundle.main.bundleIdentifier, !baseBundleId.isEmpty {
            identifiers.append("\(baseBundleId).keyboard")
            identifiers.append("\(baseBundleId).KeyboardExtension")
        }
        
        // Fallback to known identifiers used across release/dev builds
        identifiers.append("com.kvive.aikeyboard.keyboard")
        
        return Array(Set(identifiers))
    }
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        
        print("🚀 App launching - AppDelegate didFinishLaunching")
        
        // ✅ Call super FIRST to let Flutter framework initialize properly
        let result = super.application(application, didFinishLaunchingWithOptions: launchOptions)
        print("✅ Flutter super.application completed")
        
        // ✅ Now access the Flutter engine and register plugins
        guard let window = self.window else {
            print("❌ Window is nil")
            return result
        }
        
        guard let controller = window.rootViewController as? FlutterViewController else {
            print("❌ Failed to get FlutterViewController")
            return result
        }
        
        // ✅ Store reference to the engine
        flutterEngine = controller.engine
        
        // ✅ Register plugins with the controller's engine
        GeneratedPluginRegistrant.register(with: controller.engine)
        print("✅ Plugins registered with FlutterViewController's engine")
        
        // ✅ Setup method channel with error handling
        setupMethodChannel(controller: controller)
        setupAIStubChannels(controller: controller)
        
        // Setup shortcuts for easier access (commented out until ShortcutsManager is added to Runner target)
        // if #available(iOS 12.0, *) {
        //     ShortcutsManager.shared.setupKeyboardShortcuts()
        // }
        
        return result
    }
    
    // MARK: - Method Channel Setup
    private func setupMethodChannel(controller: FlutterViewController) {
        do {
            let keyboardChannel = FlutterMethodChannel(name: CHANNEL, binaryMessenger: controller.binaryMessenger)
            print("✅ Method channel created successfully")
            
            keyboardChannel.setMethodCallHandler({ [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
                guard let self = self else {
                    result(FlutterError(code: "NO_SELF", message: "AppDelegate instance is nil", details: nil))
                    return
                }
                
                print("📞 Method channel call received: \(call.method)")
                
                switch call.method {
                case "isKeyboardEnabled":
                    result(self.isKeyboardEnabled())
                case "isKeyboardActive":
                    result(self.isKeyboardActive())
                case "openKeyboardSettings":
                    self.openKeyboardSettings()
                    result(true)
                case "openInputMethodPicker":
                    // iOS doesn't have an input method picker like Android
                    result(false)
                case "updateSettings":
                    if let args = call.arguments as? [String: Any] {
                        self.updateKeyboardSettings(args)
                    }
                    result(true)
                case "showKeyboardTutorial":
                    self.showKeyboardTutorial()
                    result(true)
                case "openKeyboardsDirectly":
                    let opened = self.openKeyboardsDirectly()
                    result(opened)
                case "checkKeyboardPermissions":
                    result(self.checkKeyboardPermissions())
                default:
                    print("⚠️ Unknown method: \(call.method)")
                    result(FlutterMethodNotImplemented)
                }
            })
            
            print("✅ Method channel handler set successfully")
        } catch {
            print("❌ Failed to setup method channel: \(error)")
        }
    }
    
    private func setupAIStubChannels(controller: FlutterViewController) {
        let aiChannel = FlutterMethodChannel(
            name: "ai_keyboard/unified_ai",
            binaryMessenger: controller.binaryMessenger
        )
        
        aiChannel.setMethodCallHandler { [weak self] call, result in
            guard self != nil else {
                result(FlutterError(code: "NO_SELF", message: "AppDelegate deallocated", details: nil))
                return
            }
            
            switch call.method {
            case "getServiceStatus":
                result([
                    "isReady": false,
                    "hasApiKey": false,
                    "aiEnabled": false,
                    "fromStub": true
                ])
            case "getCacheStats":
                result([
                    "cachedItems": 0,
                    "memoryUsage": 0,
                    "fromStub": true
                ])
            case "clearCache":
                result(true)
            case "getAvailableTones", "getAvailableFeatures":
                result([])
            case "generateSmartReplies", "processText", "testConnection":
                result(FlutterError(code: "UNAVAILABLE", message: "AI service not available on iOS yet", details: call.method))
            default:
                result(FlutterMethodNotImplemented)
            }
        }
        
        let promptsChannel = FlutterMethodChannel(
            name: "ai_keyboard/prompts",
            binaryMessenger: controller.binaryMessenger
        )
        
        promptsChannel.setMethodCallHandler { [weak self] call, result in
            guard self != nil else {
                result(FlutterError(code: "NO_SELF", message: "AppDelegate deallocated", details: nil))
                return
            }
            
            switch call.method {
            case "getPrompts":
                result([])
            case "savePrompt":
                result(true)
            case "deletePrompt":
                result(true)
            default:
                result(FlutterMethodNotImplemented)
            }
        }
    }
    
    private func isKeyboardEnabled() -> Bool {
        // Check if the keyboard extension is enabled
        // Check if keyboard is in the list of enabled keyboards
        guard let keyboards = UserDefaults.standard.object(forKey: "AppleKeyboards") as? [String] else {
            return false
        }
        
        for candidate in keyboardExtensionBundleIds {
            if keyboards.contains(candidate) {
                return true
            }
        }
        
        return false
    }
    
    private func isKeyboardActive() -> Bool {
        // iOS doesn't provide easy way to check if keyboard is currently active
        // Return true if enabled for simplicity
        return isKeyboardEnabled()
    }
    
    private func openKeyboardSettings() {
        // Try to open directly to Keyboard settings with deep link
        let keyboardSettingsUrl = "App-prefs:General&path=Keyboard"
        
        if let url = URL(string: keyboardSettingsUrl),
           UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, completionHandler: nil)
        } else {
            // Fallback to general settings
            if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                if UIApplication.shared.canOpenURL(settingsUrl) {
                    UIApplication.shared.open(settingsUrl, completionHandler: nil)
                }
            }
        }
    }
    
    private func updateKeyboardSettings(_ settings: [String: Any]) {
        // Share settings with keyboard extension using App Groups
        if let userDefaults = UserDefaults(suiteName: "group.com.kvive.aikeyboard.shared") {
            userDefaults.set(settings["theme"] as? String ?? "default_theme", forKey: "keyboard_theme")
            userDefaults.set(settings["aiSuggestions"] as? Bool ?? true, forKey: "ai_suggestions")
            userDefaults.set(settings["swipeTyping"] as? Bool ?? true, forKey: "swipe_typing")
            userDefaults.set(settings["voiceInput"] as? Bool ?? true, forKey: "voice_input")
            userDefaults.synchronize()
            
            // Notify keyboard extension of settings change
            let notificationCenter = CFNotificationCenterGetDarwinNotifyCenter()
            let notificationName = "com.example.aiKeyboard.settingsChanged" as CFString
            CFNotificationCenterPostNotification(notificationCenter, CFNotificationName(notificationName), nil, nil, true)
        }
    }
    
    private func showKeyboardTutorial() {
        // Show interactive tutorial for keyboard setup
        guard let rootViewController = window?.rootViewController else { return }
        
        let alert = UIAlertController(
            title: "🎯 Quick Setup Guide",
            message: "Follow these steps to enable AI Keyboard:",
            preferredStyle: .alert
        )
        
        let tutorialMessage = """
        Step 1: Tap 'Go to Settings' below
        Step 2: Scroll to 'Keyboards' section
        Step 3: Tap 'Add New Keyboard...'
        Step 4: Find 'AI Keyboard' in list
        Step 5: Tap to enable it
        Step 6: Return to any app and test!
        
        💡 Tip: Long-press the 🌐 key to switch keyboards quickly!
        """
        
        alert.message = tutorialMessage
        
        alert.addAction(UIAlertAction(title: "Go to Settings", style: .default) { _ in
            self.openKeyboardsDirectly()
        })
        
        alert.addAction(UIAlertAction(title: "Later", style: .cancel))
        
        rootViewController.present(alert, animated: true)
    }
    
    @discardableResult
    private func openKeyboardsDirectly() -> Bool {
        // Multiple attempts to open keyboard settings directly
        let keyboardUrls = [
            "App-prefs:General&path=Keyboard",
            "prefs:General&path=Keyboard",
            "App-prefs:General&path=KEYBOARD",
            UIApplication.openSettingsURLString
        ]
        
        for urlString in keyboardUrls {
            if let url = URL(string: urlString),
               UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url, completionHandler: nil)
                return true
            }
        }
        
        return false
    }
    
    private func checkKeyboardPermissions() -> Bool {
        // Enhanced keyboard permission checking
        // Check multiple sources for keyboard status
        let sources = [
            UserDefaults.standard.object(forKey: "AppleKeyboards") as? [String],
            UserDefaults.standard.object(forKey: "AddedKeyboards") as? [String]
        ]
        
        for keyboards in sources {
            if let keyboards = keyboards {
                let hasMatch = keyboards.contains { entry in
                    keyboardExtensionBundleIds.contains { candidate in
                        entry == candidate || entry.contains(candidate)
                    }
                }
                
                if hasMatch {
                    return true
                }
            }
        }
        
        return false
    }
}

// MARK: - Firebase Configuration
// ✅ Firebase is initialized by Flutter's firebase_core plugin
// No manual configuration needed here

// MARK: - Keyboard Extension Manager
class KeyboardExtensionManager {
    static let shared = KeyboardExtensionManager()
    
    private init() {}
    
    func sendSettingsUpdate(theme: String, aiSuggestions: Bool, swipeTyping: Bool, voiceInput: Bool) {
        guard let userDefaults = UserDefaults(suiteName: "group.com.kvive.aikeyboard.shared") else { return }
        
        userDefaults.set(theme, forKey: "keyboard_theme")
        userDefaults.set(aiSuggestions, forKey: "ai_suggestions")
        userDefaults.set(swipeTyping, forKey: "swipe_typing")
        userDefaults.set(voiceInput, forKey: "voice_input")
        userDefaults.synchronize()
        
        // Post notification to keyboard extension
        let notification = CFNotificationCenterGetDarwinNotifyCenter()
        let name = "com.example.aiKeyboard.settingsChanged" as CFString
        CFNotificationCenterPostNotification(notification, CFNotificationName(name), nil, nil, true)
    }
    
    // Handle Siri shortcuts and user activities (TODO: Implement after adding ShortcutsManager)
    // func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
    //     if #available(iOS 12.0, *) {
    //         if ShortcutsManager.shared.handleShortcut(userActivity: userActivity) {
    //             return true
    //         }
    //     }
    //     return false
    // }
}
