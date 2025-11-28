// main.dart
import 'package:ai_keyboard/screens/main%20screens/home_screen.dart';
import 'package:ai_keyboard/screens/login/login_illustraion_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/clipboard_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/language_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/dictionary_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/mainscreen.dart';
import 'package:ai_keyboard/screens/keyboard_setup/keyboard_setup_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:firebase_core/firebase_core.dart';
import 'firebase_options.dart';
import 'keyboard_feedback_system.dart';
import 'theme_manager.dart';
import 'theme/theme_editor_v2.dart';
import 'services/firebase_auth_service.dart';
import 'services/clipboard_service.dart';
import 'services/keyboard_settings_bootstrapper.dart';
import 'services/fcm_token_service.dart';
import 'widgets/account_section.dart';
import 'screens/auth_wrapper.dart';
import 'screens/main%20screens/mainscreen.dart';
import 'screens/main screens/keyboard_settings.dart';
import 'theme/theme_editor_v2.dart';
import 'screens/main screens/emoji_settings_screen.dart';
import 'screens/main screens/dictionary_screen.dart';
import 'screens/main screens/clipboard_screen.dart';
import 'screens/main screens/ai_writing_assistance_screen.dart';
import 'screens/main screens/ai_rewriting_screen.dart';
import 'package:ai_keyboard/theme/Custom_theme.dart';
import 'package:ai_keyboard/screens/main%20screens/setting_screen.dart';
// In-app keyboard widgets removed - using system-wide keyboard only

/// Language Cache Manager for handling downloaded languages
class LanguageCacheManager {
  static const String _cachedLanguagesKey = 'cached_languages';
  static const String _languageMetadataPrefix = 'lang_meta_';
  static const MethodChannel _platform = MethodChannel('com.kvive.keyboard/language');
  
  /// Get list of languages that have been cached locally
  static Future<List<String>> getCachedLanguages() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getStringList(_cachedLanguagesKey) ?? [];
  }
  
  /// Add a language to the cached languages list
  static Future<void> addCachedLanguage(String languageCode) async {
    final prefs = await SharedPreferences.getInstance();
    final cachedLanguages = prefs.getStringList(_cachedLanguagesKey) ?? [];
    
    if (!cachedLanguages.contains(languageCode)) {
      cachedLanguages.add(languageCode);
      await prefs.setStringList(_cachedLanguagesKey, cachedLanguages);
      
      // Store download metadata
      await prefs.setString(
        '$_languageMetadataPrefix$languageCode',
        json.encode({
          'downloadedAt': DateTime.now().millisecondsSinceEpoch,
          'version': 1,
          'languageCode': languageCode,
        }),
      );
      
      debugPrint('✅ Added $languageCode to cached languages');
    }
  }
  
  /// Remove a language from cache and delete local files
  static Future<void> removeCachedLanguage(String languageCode) async {
    final prefs = await SharedPreferences.getInstance();
    final cachedLanguages = prefs.getStringList(_cachedLanguagesKey) ?? [];
    
    cachedLanguages.remove(languageCode);
    await prefs.setStringList(_cachedLanguagesKey, cachedLanguages);
    
    // Remove metadata
    await prefs.remove('$_languageMetadataPrefix$languageCode');
    
    // Call native method to delete cache files
    try {
      await _platform.invokeMethod('deleteCachedLanguageData', {'lang': languageCode});
      debugPrint('🗑️ Removed $languageCode from cache');
    } catch (e) {
      debugPrint('Error deleting cached files for $languageCode: $e');
    }
  }
  
  /// Get metadata for a cached language
  static Future<Map<String, dynamic>?> getLanguageMetadata(String languageCode) async {
    final prefs = await SharedPreferences.getInstance();
    final metadataString = prefs.getString('$_languageMetadataPrefix$languageCode');
    
    if (metadataString != null) {
      try {
        return json.decode(metadataString) as Map<String, dynamic>;
      } catch (e) {
        debugPrint('Error parsing metadata for $languageCode: $e');
      }
    }
    return null;
  }
  
  /// Check if a language is cached
  static Future<bool> isLanguageCached(String languageCode) async {
    final cachedLanguages = await getCachedLanguages();
    return cachedLanguages.contains(languageCode);
  }
  
  /// Get cache statistics
  static Future<Map<String, dynamic>> getCacheStats() async {
    final cachedLanguages = await getCachedLanguages();
    final stats = <String, dynamic>{
      'totalCachedLanguages': cachedLanguages.length,
      'cachedLanguages': cachedLanguages,
      'cacheSize': 0, // Could be expanded to calculate actual file sizes
    };
    
    // Add individual language metadata
    for (final lang in cachedLanguages) {
      final metadata = await getLanguageMetadata(lang);
      if (metadata != null) {
        stats['${lang}_metadata'] = metadata;
      }
    }
    
    return stats;
  }
  
  /// Clear all cached languages (for reset functionality)
  static Future<void> clearAllCache() async {
    final cachedLanguages = await getCachedLanguages();
    
    // Remove each language individually to clean up files
    for (final lang in cachedLanguages) {
      await removeCachedLanguage(lang);
    }
    
    debugPrint('🗑️ Cleared all language cache');
  }
  
  /// Initialize cache management - called at app startup
  static Future<void> initialize() async {
    try {
      // Get cached languages and ensure they're still valid
      final cachedLanguages = await getCachedLanguages();
      
      if (cachedLanguages.isNotEmpty) {
        debugPrint('📋 Found ${cachedLanguages.length} cached languages: $cachedLanguages');
        
        // Could add validation here to check if cached files still exist
        // and remove entries for missing files
        
        // Notify native side about cached languages for optimization
        try {
          await _platform.invokeMethod('updateCachedLanguagesList', {
            'cachedLanguages': cachedLanguages,
          });
        } catch (e) {
          debugPrint('Error updating native cached languages list: $e');
        }
      }
    } catch (e) {
      debugPrint('Error initializing language cache manager: $e');
    }
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Firebase with robust duplicate app handling (CRITICAL - must complete)
  try {
    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp(
        options: DefaultFirebaseOptions.currentPlatform,
      );
    } else {
      debugPrint("Firebase already initialized, continuing...");
    }
  } catch (e) {
    // Handle duplicate app error gracefully - common during hot restart
    if (e.toString().contains('duplicate-app')) {
      debugPrint("Firebase already initialized, continuing...");
    } else {
      // Re-throw other Firebase errors as they might be configuration issues
      debugPrint("Firebase initialization error: $e");
      rethrow;
    }
  }

  // ⚡ START APP IMMEDIATELY - Don't block UI initialization
  runApp(const AIKeyboardApp());
  
  // 🔄 Initialize services in background (non-blocking)
  _initializeServicesInBackground();
}

/// Initialize services asynchronously in the background without blocking UI
Future<void> _initializeServicesInBackground() async {
  try {
    debugPrint('🚀 Starting background service initialization...');
    
    // Initialize services in parallel for faster startup
    await Future.wait([
      // Keyboard settings bootstrapper
      KeyboardSettingsBootstrapper.ensureBootstrapped().catchError((e) {
        debugPrint('⚠️ KeyboardSettingsBootstrapper error: $e');
        return null;
      }),
      
      // Theme manager
      FlutterThemeManager.instance.initialize().catchError((e) {
        debugPrint('⚠️ FlutterThemeManager error: $e');
        return null;
      }),
      
      // Language cache manager
      LanguageCacheManager.initialize().catchError((e) {
        debugPrint('⚠️ LanguageCacheManager error: $e');
        return null;
      }),
      
      // Clipboard service
      ClipboardService.initialize().then((_) {
        debugPrint('✅ ClipboardService initialized');
      }).catchError((e) {
        debugPrint('⚠️ ClipboardService error: $e');
        return null;
      }),
      
      // FCM token service
      FCMTokenService.initialize().then((_) {
        debugPrint('✅ FCMTokenService initialized');
      }).catchError((e) {
        debugPrint('⚠️ FCMTokenService error: $e');
        return null;
      }),
    ]);
    
    debugPrint('✅ All background services initialized successfully');
  } catch (e) {
    debugPrint('⚠️ Error during background initialization: $e');
    // Don't crash the app - services will retry/recover as needed
  }
}

// Global navigator key for deep linking
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

class AIKeyboardApp extends StatefulWidget {
  const AIKeyboardApp({super.key});

  @override
  State<AIKeyboardApp> createState() => _AIKeyboardAppState();
}

class _AIKeyboardAppState extends State<AIKeyboardApp> {
  static const platform = MethodChannel('ai_keyboard/config');

  @override
  void initState() {
    super.initState();
    _setupNavigationListener();
  }

  void _setupNavigationListener() {
    platform.setMethodCallHandler((call) async {
      if (call.method == 'navigate') {
        final route = call.arguments['route'] as String?;
        debugPrint('🧭 Flutter received navigation: $route');
        if (route != null) {
          _handleNavigation(route);
        }
      }
    });
  }

  void _handleNavigation(String route) {
    final context = navigatorKey.currentContext;
    if (context == null) {
      debugPrint('⚠️ Navigator context not available');
      return;
    }

    debugPrint('✅ Navigating to: $route');

    switch (route) {
      case 'ai_writing_custom':
        // Navigate to AI Writing Assistance, Custom Assistance tab
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => const AIWritingAssistanceScreen(initialTabIndex: 1),
          ),
        );
        break;
      case 'custom_grammar':
        // Navigate to Custom Grammar
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => const CustomGrammarScreen(),
          ),
        );
        break;
      case 'custom_tones':
        // Navigate to Custom Tones
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => const CustomTonesScreen(),
          ),
        );
        break;
      case 'theme_editor':
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => const ThemeEditorScreenV2(isCreatingNew: true),
          ),
        );
        break;
      case 'settings_screen':
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => const SettingScreen(),
          ),
        );
        break;
      default:
        debugPrint('⚠️ Unknown navigation route: $route');
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: navigatorKey,  // ✅ Add global key for deep linking
      debugShowCheckedModeBanner: false,
      title: 'AI Keyboard',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
        fontFamily: 'noto_sans',
      ),
      home:  AuthWrapper(), 
      // home: AnimatedOnboardingScreen(),
    );
  }
}

/// Utility function to check keyboard status and navigate to config screen
/// Usage: await openKeyboardConfigIfReady(context);
Future<void> openKeyboardConfigIfReady(BuildContext context) async {
  const platform = MethodChannel('ai_keyboard/config');
  try {
    final enabled = await platform.invokeMethod<bool>('isKeyboardEnabled') ?? false;
    final active = await platform.invokeMethod<bool>('isKeyboardActive') ?? false;
    
    if (!context.mounted) return;
    
    if (enabled && active) {
      // Keyboard is ready - open config screen
      Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => const HomeScreen())
      );
    } else {
      // Guide user to enable/select keyboard
      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Setup Required'),
          content: Text(
            enabled 
              ? 'Please select AI Keyboard as your active keyboard.'
              : 'Please enable AI Keyboard in system settings first.'
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: () async {
                Navigator.pop(ctx);
                try {
                  if (enabled) {
                    await platform.invokeMethod('openInputMethodPicker');
                  } else {
                    await platform.invokeMethod('openKeyboardSettings');
                  }
                } catch (e) {
                  debugPrint('Error opening settings: $e');
                }
              },
              child: const Text('Open Settings'),
            ),
          ],
        ),
      );
    }
  } catch (e) {
    debugPrint('Error checking keyboard status: $e');
    // Fallback: just open config screen
    if (!context.mounted) return;
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const HomeScreen())
    );
  }
}

// class KeyboardConfigScreen extends StatefulWidget {
//   const KeyboardConfigScreen({super.key});

//   @override
//   State<KeyboardConfigScreen> createState() => _KeyboardConfigScreenState();
// }

// class _KeyboardConfigScreenState extends State<KeyboardConfigScreen> {
//   static const platform = MethodChannel('ai_keyboard/config');
//   bool _isKeyboardEnabled = false;
//   bool _isKeyboardActive = false;
//   bool _aiSuggestionsEnabled = true;
//   bool _swipeTypingEnabled = true;
//   bool _vibrationEnabled = true;
//   bool _keyPreviewEnabled = false;
//   bool _shiftFeedbackEnabled = false;
//   bool _showNumberRow = false;
//   bool _soundEnabled = true;
//   String _currentLanguage = "EN";

//   // Advanced feedback settings
//   FeedbackIntensity _hapticIntensity = FeedbackIntensity.medium;
//   FeedbackIntensity _soundIntensity = FeedbackIntensity.light;
//   FeedbackIntensity _visualIntensity = FeedbackIntensity.medium;
//   double _soundVolume = 0.3;

//   // Firebase Auth Service
//   final FirebaseAuthService _authService = FirebaseAuthService();

//   final List<String> _themes = [
//     'gboard',
//     'gboard_dark',
//     'default',
//     'dark',
//     'material_you',
//     'professional',
//     'colorful',
//   ];
//   @override
//   void initState() {
//     super.initState();
//     _loadSettings();
//     _checkKeyboardStatus();

//     // Show setup reminder for iOS users if keyboard is not enabled
//     if (Platform.isIOS) {
//       _checkAndShowSetupReminder();
//     }
//   }

//   Future<void> _checkAndShowSetupReminder() async {
//     // Wait a bit for the UI to settle
//     await Future.delayed(const Duration(seconds: 2));

//     if (!_isKeyboardEnabled && mounted) {
//       _showSetupReminder();
//     }
//   }

//   void _showSetupReminder() {
//     showDialog(
//       context: context,
//       builder: (BuildContext context) {
//         return AlertDialog(
//           title: const Row(
//             children: [
//               Icon(Icons.info_outline, color: Colors.blue),
//               SizedBox(width: 8),
//               Text('Setup Required'),
//             ],
//           ),
//           content: const Text(
//             'AI Keyboard needs to be enabled in iOS Settings to work. Would you like to set it up now?',
//           ),
//           actions: [
//             TextButton(
//               onPressed: () => Navigator.of(context).pop(),
//               child: const Text('Later'),
//             ),
//             TextButton(
//               onPressed: () {
//                 Navigator.of(context).pop();
//                 _openInputMethodPicker();
//               },
//               child: const Text('Setup Now'),
//             ),
//           ],
//         );
//       },
//     );
//   }

//   void _showSettingsUpdatedSnackBar() {
//     if (mounted) {
//       ScaffoldMessenger.of(context).showSnackBar(
//         SnackBar(
//           content: const Row(
//             children: [
//               Icon(Icons.check_circle, color: Colors.white, size: 20),
//               SizedBox(width: 8),
//               Text('Settings saved! Switch to keyboard to see changes.'),
//             ],
//           ),
//           backgroundColor: Colors.green,
//           duration: const Duration(seconds: 3),
//           behavior: SnackBarBehavior.floating,
//           shape: RoundedRectangleBorder(
//             borderRadius: BorderRadius.circular(10),
//           ),
//           action: SnackBarAction(
//             label: 'Test Keyboard',
//             textColor: Colors.white,
//             onPressed: () {
//               // Show dialog to test keyboard
//               _showTestKeyboardDialog();
//             },
//           ),
//         ),
//       );
//     }
//   }

//   void _showTestKeyboardDialog() {
//     showDialog(
//       context: context,
//       barrierDismissible: true,
//       builder: (BuildContext context) {
//         return Dialog(
//           child: Container(
//             constraints: const BoxConstraints(maxWidth: 600, maxHeight: 700),
//             padding: const EdgeInsets.all(20),
//             child: Column(
//               mainAxisSize: MainAxisSize.min,
//               crossAxisAlignment: CrossAxisAlignment.start,
//               children: [
//                 Row(
//                   children: [
//                     const Icon(
//                       Icons.rocket_launch,
//                       color: Colors.blue,
//                       size: 28,
//                     ),
//                     const SizedBox(width: 12),
//                     const Text(
//                       'Advanced Feedback Testing',
//                       style: TextStyle(
//                         fontSize: 20,
//                         fontWeight: FontWeight.bold,
//                       ),
//                     ),
//                     const Spacer(),
//                     IconButton(
//                       onPressed: () => Navigator.of(context).pop(),
//                       icon: const Icon(Icons.close),
//                     ),
//                   ],
//                 ),
//                 const SizedBox(height: 20),

//                 // System keyboard status panel
//                 const SystemKeyboardStatusPanel(),
//                 const SizedBox(height: 20),

//                 // AI Service Information
//                 const Expanded(
//                   child: SingleChildScrollView(child: AIServiceInfoWidget()),
//                 ),

//                 const SizedBox(height: 16),

//                 // Instructions
//                 Container(
//                   padding: const EdgeInsets.all(12),
//                   decoration: BoxDecoration(
//                     color: Colors.blue.withOpacity(0.1),
//                     borderRadius: BorderRadius.circular(8),
//                     border: Border.all(color: Colors.blue.withOpacity(0.3)),
//                   ),
//                   child: const Column(
//                     crossAxisAlignment: CrossAxisAlignment.start,
//                     children: [
//                       Text(
//                         '💡 How to test:',
//                         style: TextStyle(
//                           fontWeight: FontWeight.bold,
//                           color: Colors.blue,
//                         ),
//                       ),
//                       SizedBox(height: 8),
//                       Text(
//                         '• Tap demo keys above to test current feedback settings',
//                       ),
//                       Text(
//                         '• Use quick test buttons for individual feedback types',
//                       ),
//                       Text('• Adjust settings and see changes instantly'),
//                       Text(
//                         '• For real keyboard: Open any text app and switch to AI Keyboard',
//                       ),
//                     ],
//                   ),
//                 ),

//                 const SizedBox(height: 16),

//                 // Action buttons
//                 Row(
//                   mainAxisAlignment: MainAxisAlignment.end,
//                   children: [
//                     TextButton(
//                       onPressed: () => Navigator.of(context).pop(),
//                       child: const Text('Close'),
//                     ),
//                     const SizedBox(width: 8),
//                     ElevatedButton.icon(
//                       onPressed: () {
//                         Navigator.of(context).pop();
//                         _openInputMethodPicker();
//                       },
//                       icon: const Icon(Icons.keyboard),
//                       label: const Text('Switch to AI Keyboard'),
//                       style: ElevatedButton.styleFrom(
//                         backgroundColor: Colors.blue,
//                         foregroundColor: Colors.white,
//                       ),
//                     ),
//                   ],
//                 ),
//               ],
//             ),
//           ),
//         );
//       },
//     );
//   }

//   Future<void> _loadSettings() async {
//     final prefs = await SharedPreferences.getInstance();
//     setState(() {
//       // Theme removed - using default keyboard styling
//       _aiSuggestionsEnabled = prefs.getBool('ai_suggestions') ?? true;
//       _swipeTypingEnabled = prefs.getBool('swipe_typing') ?? true;
//       _vibrationEnabled = prefs.getBool('vibration_enabled') ?? true;
//       _keyPreviewEnabled = prefs.getBool('key_preview_enabled') ?? false;
//       _shiftFeedbackEnabled = prefs.getBool('show_shift_feedback') ?? false;
//       _showNumberRow = prefs.getBool('show_number_row') ?? false;
//       _soundEnabled = prefs.getBool('sound_enabled') ?? true;
//       _currentLanguage = prefs.getString('current_language') ?? "EN";

//       // Load advanced feedback settings
//       _hapticIntensity = FeedbackIntensity
//           .values[prefs.getInt('haptic_intensity') ?? 2]; // medium
//       _soundIntensity = FeedbackIntensity
//           .values[prefs.getInt('sound_intensity') ?? 1]; // light
//       _visualIntensity = FeedbackIntensity
//           .values[prefs.getInt('visual_intensity') ?? 2]; // medium
//       _soundVolume = prefs.getDouble('sound_volume') ?? 0.3;
//     });

//     // Update feedback system with loaded settings
//     KeyboardFeedbackSystem.updateSettings(
//       haptic: _hapticIntensity,
//       sound: _soundIntensity,
//       visual: _visualIntensity,
//       volume: _soundVolume,
//     );
//   }

//   Future<void> _saveSettings() async {
//     final prefs = await SharedPreferences.getInstance();
//     // Theme removed - using default styling only
//     await prefs.setBool('ai_suggestions', _aiSuggestionsEnabled);
//     await prefs.setBool('swipe_typing', _swipeTypingEnabled);
//     await prefs.setBool('vibration_enabled', _vibrationEnabled);
//     await prefs.setBool('key_preview_enabled', _keyPreviewEnabled);
//     await prefs.setBool('show_shift_feedback', _shiftFeedbackEnabled);
//     await prefs.setBool('show_number_row', _showNumberRow);
//     await prefs.setBool('sound_enabled', _soundEnabled);
//     await prefs.setString('current_language', _currentLanguage);

//     // Save advanced feedback settings
//     await prefs.setInt('haptic_intensity', _hapticIntensity.index);
//     await prefs.setInt('sound_intensity', _soundIntensity.index);
//     await prefs.setInt('visual_intensity', _visualIntensity.index);
//     await prefs.setDouble('sound_volume', _soundVolume);

//     // Update feedback system with new settings
//     KeyboardFeedbackSystem.updateSettings(
//       haptic: _hapticIntensity,
//       sound: _soundIntensity,
//       visual: _visualIntensity,
//       volume: _soundVolume,
//     );

//     // Send settings to native keyboard
//     await _sendSettingsToKeyboard();

//     // Show success feedback
//     _showSettingsUpdatedSnackBar();
//   }

//   Future<void> _sendSettingsToKeyboard() async {
//     try {
//       await platform.invokeMethod('updateSettings', {
//         'theme': 'default',
//         'aiSuggestions': _aiSuggestionsEnabled,
//         'swipeTyping': _swipeTypingEnabled,
//         'vibration': _vibrationEnabled,
//         'keyPreview': _keyPreviewEnabled,
//         'shiftFeedback': _shiftFeedbackEnabled,
//         'showNumberRow': _showNumberRow,
//         'soundEnabled': _soundEnabled,
//       });
//     } catch (e) {
//       print('Error sending settings: $e');
//     }
//   }

//   Future<void> _checkKeyboardStatus() async {
//     try {
//       final bool enabled = await platform.invokeMethod('isKeyboardEnabled');
//       final bool active = await platform.invokeMethod('isKeyboardActive');
//       setState(() {
//         _isKeyboardEnabled = enabled;
//         _isKeyboardActive = active;
//       });
//     } catch (e) {
//       print('Error checking keyboard status: $e');
//     }
//   }

//   Future<void> _openKeyboardSettings() async {
//     try {
//       await platform.invokeMethod('openKeyboardSettings');
//     } catch (e) {
//       print('Error opening keyboard settings: $e');
//     }
//   }

//   Future<void> _openInputMethodPicker() async {
//     try {
//       if (Platform.isAndroid) {
//         await platform.invokeMethod('openInputMethodPicker');
//       } else if (Platform.isIOS) {
//         // Show interactive tutorial for iOS
//         await platform.invokeMethod('showKeyboardTutorial');
//       }
//     } catch (e) {
//       print('Error opening input method picker: $e');
//     }
//   }

//   Future<void> _openKeyboardsDirectly() async {
//     try {
//       await platform.invokeMethod('openKeyboardsDirectly');
//     } catch (e) {
//       print('Error opening keyboards directly: $e');
//     }
//   }

//   Future<void> _showQuickSwitchGuide() async {
//     if (Platform.isIOS) {
//       showDialog(
//         context: context,
//         builder: (BuildContext context) {
//           return AlertDialog(
//             title: const Row(children: [Text('🚀 Quick Switch Guide')]),
//             content: const Column(
//               mainAxisSize: MainAxisSize.min,
//               crossAxisAlignment: CrossAxisAlignment.start,
//               children: [
//                 Text('Once AI Keyboard is enabled, switch quickly by:'),
//                 SizedBox(height: 12),
//                 Text('🌐 Tap globe icon to cycle keyboards'),
//                 Text('🌐 Long-press globe for keyboard list'),
//                 Text('⌨️ Or go to any text field and tap keyboard icon'),
//                 SizedBox(height: 12),
//                 Text('💡 Pro tip: Set AI Keyboard as default in Settings!'),
//               ],
//             ),
//             actions: [
//               TextButton(
//                 onPressed: () => Navigator.of(context).pop(),
//                 child: const Text('Got it!'),
//               ),
//               TextButton(
//                 onPressed: () {
//                   Navigator.of(context).pop();
//                   _openKeyboardsDirectly();
//                 },
//                 child: const Text('Open Settings'),
//               ),
//             ],
//           );
//         },
//       );
//     }
//   }

//   // Theme methods removed - using single default keyboard
//   String _getThemeDisplayName(String theme) {
//     switch (theme) {
//       case 'gboard':
//         return '🎯 Gboard (Recommended)';
//       case 'gboard_dark':
//         return '🌙 Gboard Dark';
//       case 'material_you':
//         return 'Material You';
//       default:
//         return theme
//             .replaceAll('_', ' ')
//             .split(' ')
//             .map((word) => word[0].toUpperCase() + word.substring(1))
//             .join(' ');
//     }
//   }

//   void _showIOSInstructions() {
//     showDialog(
//       context: context,
//       builder: (BuildContext context) {
//         return AlertDialog(
//           title: const Text('Enable AI Keyboard on iOS'),
//           content: const Column(
//             mainAxisSize: MainAxisSize.min,
//             crossAxisAlignment: CrossAxisAlignment.start,
//             children: [
//               Text('To enable AI Keyboard on iOS:'),
//               SizedBox(height: 12),
//               Text('1. Open Settings app'),
//               Text('2. Go to General → Keyboard'),
//               Text('3. Tap "Keyboards"'),
//               Text('4. Tap "Add New Keyboard..."'),
//               Text('5. Select "AI Keyboard" from Third-Party Keyboards'),
//               Text('6. Enable "Allow Full Network Access" if needed'),
//             ],
//           ),
//           actions: [
//             TextButton(
//               onPressed: () => Navigator.of(context).pop(),
//               child: const Text('Got it'),
//             ),
//             TextButton(
//               onPressed: () {
//                 Navigator.of(context).pop();
//                 _openKeyboardSettings();
//               },
//               child: const Text('Open Settings'),
//             ),
//           ],
//         );
//       },
//     );
//   }

//   /// Show system keyboard instructions (in-app keyboard removed)
//   void _showSystemKeyboardInstructions() {
//     showDialog(
//       context: context,
//       builder: (context) => AlertDialog(
//         title: const Text('System Keyboard Only'),
//         content: const Text(
//           'This app now uses only the system-wide keyboard.\n\n'
//           'To test all features:\n'
//           '1. Enable the AI Keyboard in system settings\n'
//           '2. Set it as your default keyboard\n'
//           '3. Use it in any app (SMS, email, etc.)\n\n'
//           'All advanced features like long-press accents, '
//           'swipe typing, and AI suggestions work system-wide.',
//         ),
//         actions: [
//           TextButton(
//             onPressed: () => Navigator.of(context).pop(),
//             child: const Text('OK'),
//           ),
//         ],
//       ),
//     );
//   }

//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       appBar: AppBar(
//         title: const Text('AI Keyboard Settings'),
//         backgroundColor: Theme.of(context).colorScheme.inversePrimary,
//         actions: [
//           IconButton(
//             onPressed: () {
//               Navigator.of(context).push(MaterialPageRoute(builder: (context) => const mainscreen()));
//             },
//             icon: const Icon(Icons.home),
//           ),
//         ],
//       ),
//       body: RefreshIndicator(
//         onRefresh: _checkKeyboardStatus,
//         child: ListView(
//           padding: const EdgeInsets.all(16.0),
//           children: [
//             GestureDetector(
//               onTap: () {
//                 Navigator.of(context).push(MaterialPageRoute(builder: (context) => const mainscreen()));
//               },
//               child: Container(
//                 height: 100,
//                 width: double.infinity,
//                 decoration: BoxDecoration(
//                   color: Colors.blue.shade50,
//                   borderRadius: BorderRadius.circular(10),
//                 ),
//                 child: Center(
//                   child: Row(
//                     mainAxisAlignment: MainAxisAlignment.center,
//                     children: [
//                       Text('Try UI', style: Theme.of(context).textTheme.headlineLarge),
//                       IconButton(
//                         onPressed: () {
//                           Navigator.of(context).push(MaterialPageRoute(builder: (context) => const mainscreen()));
//                         },
//                         icon: const Icon(Icons.home, color: Colors.blue),
//                       )
//                     ],
//                   ),
//                 ),)),
//             // Container(
//             //   height: 100,
//             //   width: double.infinity,
//             //   decoration: BoxDecoration(
//             //     color: Colors.blue.shade50,
//             //     borderRadius: BorderRadius.circular(10),
//             //   ),
//             //   child:  Center(
//             //     child: Row(
//             //       mainAxisAlignment: MainAxisAlignment.center,
//             //       children: [
//             //         Text('Try UI', style: Theme.of(context).textTheme.headlineLarge),IconButton(
//             // onPressed: () {
//             //   Navigator.of(context).push(MaterialPageRoute(builder: (context) => const mainscreen()));
//             // },
//             // icon: const Icon(Icons.home, color: Colors.blue),
//             //    ) ]))
                    
//             // ),
//             _buildKeyboardStatusCard(),
//             const SizedBox(height: 20),
//             _buildPlatformInfoCard(),
//             const SizedBox(height: 20),
//             if (Platform.isIOS) ...[
//               _buildIOSSetupCard(),
//               const SizedBox(height: 20),
//             ],
//             // Theme selection removed - using single default keyboard
//             const SizedBox(height: 20),
//             // _buildFeaturesCard(),
//             const SizedBox(height: 20),
//             // _buildThemeSection(),
//             const SizedBox(height: 20),
//             _buildTestKeyboardCard(),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildKeyboardStatusCard() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Text(
//               'Keyboard Status',
//               style: Theme.of(context).textTheme.headlineSmall,
//             ),
//             const SizedBox(height: 16),
//             _buildStatusRow('Enabled', _isKeyboardEnabled),
//             const SizedBox(height: 8),
//             _buildStatusRow('Active', _isKeyboardActive),
//             const SizedBox(height: 16),
//             if (Platform.isIOS) ...[
//               // iOS-specific enhanced buttons
//               ElevatedButton.icon(
//                 onPressed: _openInputMethodPicker,
//                 icon: const Icon(Icons.help_outline),
//                 label: const Text('Quick Setup Guide'),
//                 style: ElevatedButton.styleFrom(
//                   backgroundColor: Colors.blue,
//                   foregroundColor: Colors.white,
//                 ),
//               ),
//               const SizedBox(height: 8),
//               Row(
//                 children: [
//                   Expanded(
//                     child: ElevatedButton(
//                       onPressed: _openKeyboardsDirectly,
//                       child: const Text('Go to Settings'),
//                     ),
//                   ),
//                   const SizedBox(width: 8),
//                   Expanded(
//                     child: ElevatedButton(
//                       onPressed: _showQuickSwitchGuide,
//                       child: const Text('Switch Guide'),
//                     ),
//                   ),
//                 ],
//               ),
//             ] else ...[
//               // Android buttons
//               Row(
//                 children: [
//                   Expanded(
//                     child: ElevatedButton(
//                       onPressed: _openKeyboardSettings,
//                       child: const Text('Enable Keyboard'),
//                     ),
//                   ),
//                   const SizedBox(width: 12),
//                   Expanded(
//                     child: ElevatedButton(
//                       onPressed: _openInputMethodPicker,
//                       child: const Text('Select Keyboard'),
//                     ),
//                   ),
//                 ],
//               ),
//             ],
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildStatusRow(String label, bool status) {
//     return Row(
//       mainAxisAlignment: MainAxisAlignment.spaceBetween,
//       children: [
//         Text(label),
//         Container(
//           padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
//           decoration: BoxDecoration(
//             color: status ? Colors.green : Colors.red,
//             borderRadius: BorderRadius.circular(12),
//           ),
//           child: Text(
//             status ? 'Active' : 'Inactive',
//             style: const TextStyle(color: Colors.white, fontSize: 12),
//           ),
//         ),
//       ],
//     );
//   }

//   Widget _buildPlatformInfoCard() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Text(
//               'Platform Information',
//               style: Theme.of(context).textTheme.headlineSmall,
//             ),
//             const SizedBox(height: 12),
//             Row(
//               mainAxisAlignment: MainAxisAlignment.spaceBetween,
//               children: [
//                 const Text('Platform:'),
//                 Text(
//                   Platform.isIOS ? 'iOS' : 'Android',
//                   style: const TextStyle(fontWeight: FontWeight.bold),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 8),
//             Row(
//               mainAxisAlignment: MainAxisAlignment.spaceBetween,
//               children: [
//                 const Text('Keyboard Type:'),
//                 Text(
//                   Platform.isIOS ? 'Extension' : 'InputMethodService',
//                   style: const TextStyle(fontWeight: FontWeight.bold),
//                 ),
//               ],
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildIOSSetupCard() {
//     return Card(
//       color: Colors.blue.shade50,
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Row(
//               children: [
//                 Icon(Icons.phone_iphone, color: Colors.blue.shade700),
//                 const SizedBox(width: 8),
//                 Text(
//                   'iOS Setup Made Easy',
//                   style: Theme.of(context).textTheme.headlineSmall?.copyWith(
//                     color: Colors.blue.shade700,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             Container(
//               padding: const EdgeInsets.all(12),
//               decoration: BoxDecoration(
//                 color: Colors.white,
//                 borderRadius: BorderRadius.circular(8),
//                 border: Border.all(color: Colors.blue.shade200),
//               ),
//               child: Column(
//                 crossAxisAlignment: CrossAxisAlignment.start,
//                 children: [
//                   const Text(
//                     '📱 Quick Steps:',
//                     style: TextStyle(fontWeight: FontWeight.bold),
//                   ),
//                   const SizedBox(height: 8),
//                   _buildStepRow('1', 'Tap "Quick Setup Guide" above'),
//                   _buildStepRow('2', 'Follow the interactive tutorial'),
//                   _buildStepRow('3', 'Add AI Keyboard in Settings'),
//                   _buildStepRow('4', 'Use 🌐 key to switch keyboards'),
//                 ],
//               ),
//             ),
//             const SizedBox(height: 12),
//             Row(
//               children: [
//                 Icon(
//                   Icons.lightbulb_outline,
//                   color: Colors.amber.shade600,
//                   size: 20,
//                 ),
//                 const SizedBox(width: 8),
//                 const Expanded(
//                   child: Text(
//                     'Pro tip: Long-press the 🌐 globe key in any app to see all available keyboards!',
//                     style: TextStyle(fontStyle: FontStyle.italic),
//                   ),
//                 ),
//               ],
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildStepRow(String number, String description) {
//     return Padding(
//       padding: const EdgeInsets.symmetric(vertical: 2),
//       child: Row(
//         crossAxisAlignment: CrossAxisAlignment.start,
//         children: [
//           Container(
//             width: 20,
//             height: 20,
//             decoration: BoxDecoration(
//               color: Colors.blue.shade600,
//               shape: BoxShape.circle,
//             ),
//             child: Center(
//               child: Text(
//                 number,
//                 style: const TextStyle(
//                   color: Colors.white,
//                   fontSize: 12,
//                   fontWeight: FontWeight.bold,
//                 ),
//               ),
//             ),
//           ),
//           const SizedBox(width: 8),
//           Expanded(child: Text(description)),
//         ],
//       ),
//     );
//   }

//   // Theme selection UI removed - using single default keyboard

//   Widget _buildFeaturesCard() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Text('Features', style: Theme.of(context).textTheme.headlineSmall),
//             const SizedBox(height: 16),
//             _buildFeatureSwitch(
//               'AI Suggestions',
//               'Get smart text predictions and corrections',
//               _aiSuggestionsEnabled,
//               (value) {
//                 setState(() {
//                   _aiSuggestionsEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Swipe Typing',
//               'Swipe across letters to form words instantly! Try swiping "hello" or "the"',
//               _swipeTypingEnabled,
//               (value) {
//                 setState(() {
//                   _swipeTypingEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Vibration Feedback',
//               'Haptic feedback when typing (Recommended: ON)',
//               _vibrationEnabled,
//               (value) {
//                 setState(() {
//                   _vibrationEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Key Preview',
//               'Show letter popup when typing (Recommended: OFF for cleaner experience)',
//               _keyPreviewEnabled,
//               (value) {
//                 setState(() {
//                   _keyPreviewEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Shift State Feedback',
//               'Show messages when shift state changes (OFF/ON/CAPS)',
//               _shiftFeedbackEnabled,
//               (value) {
//                 setState(() {
//                   _shiftFeedbackEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Number Row',
//               'Show number row (1234567890) above letters for faster numeric input',
//               _showNumberRow,
//               (value) {
//                 setState(() {
//                   _showNumberRow = value;
//                 });
//                 _saveSettings();
//               },
//             ),
//             _buildFeatureSwitch(
//               'Sound Feedback',
//               'Play typing sounds when pressing keys',
//               _soundEnabled,
//               (value) {
//                 setState(() {
//                   _soundEnabled = value;
//                 });
//                 _saveSettings();
//               },
//             ),

//             // Language Status Section
//             const SizedBox(height: 24),
//             _buildSectionHeader('🌐 Language Status'),
//             const SizedBox(height: 16),

//             Container(
//               padding: const EdgeInsets.all(16),
//               decoration: BoxDecoration(
//                 color: Colors.blue.withOpacity(0.1),
//                 borderRadius: BorderRadius.circular(12),
//                 border: Border.all(color: Colors.blue.withOpacity(0.3)),
//               ),
//               child: Row(
//                 children: [
//                   const Icon(Icons.language, color: Colors.blue, size: 32),
//                   const SizedBox(width: 16),
//                   Expanded(
//                     child: Column(
//                       crossAxisAlignment: CrossAxisAlignment.start,
//                       children: [
//                         Text(
//                           'Current Language: $_currentLanguage',
//                           style: const TextStyle(
//                             fontSize: 16,
//                             fontWeight: FontWeight.bold,
//                           ),
//                         ),
//                         const SizedBox(height: 4),
//                         const Text(
//                           'Tap 🌐 on keyboard to cycle through languages',
//                           style: TextStyle(fontSize: 14, color: Colors.grey),
//                         ),
//                       ],
//                     ),
//                   ),
//                 ],
//               ),
//             ),

//             // User Account Section
//             const SizedBox(height: 24),
//             _buildSectionHeader('👤 Account & Sync'),
//             const SizedBox(height: 16),
//             AccountSection(
//               keyboardSettings: {
//                 'aiSuggestionsEnabled': _aiSuggestionsEnabled,
//                 'swipeTypingEnabled': _swipeTypingEnabled,
//                 'vibrationEnabled': _vibrationEnabled,
//                 'keyPreviewEnabled': _keyPreviewEnabled,
//                 'shiftFeedbackEnabled': _shiftFeedbackEnabled,
//                 'showNumberRow': _showNumberRow,
//                 'soundEnabled': _soundEnabled,
//                 'currentLanguage': _currentLanguage,
//                 'hapticIntensity': _hapticIntensity.toString(),
//                 'soundIntensity': _soundIntensity.toString(),
//                 'visualIntensity': _visualIntensity.toString(),
//                 'soundVolume': _soundVolume,
//               },
//             ),

//             // Advanced Feedback Settings Section
//             const SizedBox(height: 24),
//             _buildSectionHeader('🎯 Advanced Feedback Settings'),
//             const SizedBox(height: 16),

//             _buildIntensitySelector(
//               'Haptic Feedback Intensity',
//               'Control the strength of touch vibrations',
//               _hapticIntensity,
//               (value) {
//                 setState(() {
//                   _hapticIntensity = value;
//                 });
//                 _saveSettings();
//               },
//             ),

//             _buildIntensitySelector(
//               'Sound Feedback Intensity',
//               'Control keyboard typing sounds',
//               _soundIntensity,
//               (value) {
//                 setState(() {
//                   _soundIntensity = value;
//                 });
//                 _saveSettings();
//               },
//             ),

//             _buildIntensitySelector(
//               'Visual Effects Intensity',
//               'Control animations, particles, and ripple effects',
//               _visualIntensity,
//               (value) {
//                 setState(() {
//                   _visualIntensity = value;
//                 });
//                 _saveSettings();
//               },
//             ),

//             _buildVolumeSlider(),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildFeatureSwitch(
//     String title,
//     String subtitle,
//     bool value,
//     ValueChanged<bool> onChanged,
//   ) {
//     return ListTile(
//       contentPadding: EdgeInsets.zero,
//       title: Text(title),
//       subtitle: Text(subtitle),
//       trailing: Switch(value: value, onChanged: onChanged),
//     );
//   }

//   Widget _buildSectionHeader(String title) {
//     return Text(
//       title,
//       style: const TextStyle(
//         fontSize: 18,
//         fontWeight: FontWeight.bold,
//         color: Colors.blue,
//       ),
//     );
//   }

//   Widget _buildIntensitySelector(
//     String title,
//     String subtitle,
//     FeedbackIntensity value,
//     ValueChanged<FeedbackIntensity> onChanged,
//   ) {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Text(
//               title,
//               style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
//             ),
//             const SizedBox(height: 4),
//             Text(
//               subtitle,
//               style: TextStyle(fontSize: 14, color: Colors.grey[600]),
//             ),
//             const SizedBox(height: 16),
//             Row(
//               mainAxisAlignment: MainAxisAlignment.spaceEvenly,
//               children: FeedbackIntensity.values.map((intensity) {
//                 final isSelected = value == intensity;
//                 return GestureDetector(
//                   onTap: () => onChanged(intensity),
//                   child: AnimatedContainer(
//                     duration: const Duration(milliseconds: 200),
//                     padding: const EdgeInsets.symmetric(
//                       horizontal: 16,
//                       vertical: 8,
//                     ),
//                     decoration: BoxDecoration(
//                       color: isSelected ? Colors.blue : Colors.transparent,
//                       borderRadius: BorderRadius.circular(20),
//                       border: Border.all(
//                         color: isSelected ? Colors.blue : Colors.grey,
//                         width: 1,
//                       ),
//                     ),
//                     child: Text(
//                       _getIntensityLabel(intensity),
//                       style: TextStyle(
//                         color: isSelected ? Colors.white : Colors.grey[700],
//                         fontWeight: isSelected
//                             ? FontWeight.w600
//                             : FontWeight.normal,
//                       ),
//                     ),
//                   ),
//                 );
//               }).toList(),
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildVolumeSlider() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             const Text(
//               'Sound Volume',
//               style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
//             ),
//             const SizedBox(height: 4),
//             Text(
//               'Adjust keyboard sound volume level',
//               style: TextStyle(fontSize: 14, color: Colors.grey[600]),
//             ),
//             const SizedBox(height: 16),
//             Row(
//               children: [
//                 const Icon(Icons.volume_down, color: Colors.grey),
//                 Expanded(
//                   child: Slider(
//                     value: _soundVolume,
//                     min: 0.0,
//                     max: 1.0,
//                     divisions: 10,
//                     label: '${(_soundVolume * 100).round()}%',
//                     onChanged: (value) {
//                       setState(() {
//                         _soundVolume = value;
//                       });
//                       _saveSettings();
//                     },
//                   ),
//                 ),
//                 const Icon(Icons.volume_up, color: Colors.grey),
//               ],
//             ),
//             Text(
//               'Current: ${(_soundVolume * 100).round()}%',
//               style: TextStyle(fontSize: 12, color: Colors.grey[600]),
//               textAlign: TextAlign.center,
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   String _getIntensityLabel(FeedbackIntensity intensity) {
//     switch (intensity) {
//       case FeedbackIntensity.off:
//         return 'OFF';
//       case FeedbackIntensity.light:
//         return 'Light';
//       case FeedbackIntensity.medium:
//         return 'Medium';
//       case FeedbackIntensity.strong:
//         return 'Strong';
//     }
//   }
  
//   /// Build theme customization section
//   Widget _buildThemeSection() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Row(
//               children: [
//                 const Icon(Icons.palette, color: Colors.deepPurple, size: 28),
//                 const SizedBox(width: 12),
//                 Text(
//                   'Keyboard Themes',
//                   style: Theme.of(context).textTheme.headlineSmall?.copyWith(
//                     color: Colors.deepPurple,
//                     fontWeight: FontWeight.bold,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             const Text(
//               'Customize your keyboard appearance with themes, colors, fonts, and backgrounds.',
//               style: TextStyle(fontSize: 16, color: Colors.grey),
//             ),
//             const SizedBox(height: 20),
            
//             // Theme Preview
//             ListenableBuilder(
//               listenable: FlutterThemeManager.instance,
//               builder: (context, _) {
//                 final currentTheme = FlutterThemeManager.instance.currentTheme;
//                 return _buildCurrentThemePreview(currentTheme);
//               },
//             ),
            
//             const SizedBox(height: 16),
            
//             // Theme Buttons
//             Row(
//               children: [
//                 Expanded(
//                   child: ElevatedButton.icon(
//                     onPressed: () {
//                     },
//                     // onPressed: _openThemeEditor,
//                     icon: const Icon(Icons.edit),
//                     label: const Text('Customize Theme'),
//                     style: ElevatedButton.styleFrom(
//                       backgroundColor: Colors.deepPurple,
//                       foregroundColor: Colors.white,
//                       padding: const EdgeInsets.symmetric(vertical: 12),
//                     ),
//                   ),
//                 ),
//                 const SizedBox(width: 12),
//                 Expanded(
//                   child: ElevatedButton.icon(
//                     onPressed: _showThemeGallery,
//                     icon: const Icon(Icons.photo_library),
//                     label: const Text('Theme Gallery'),
//                     style: ElevatedButton.styleFrom(
//                       backgroundColor: Colors.indigo,
//                       foregroundColor: Colors.white,
//                       padding: const EdgeInsets.symmetric(vertical: 12),
//                     ),
//                   ),
//                 ),
//               ],
//             ),
//           ],
//         ),
//       ),
//     );
//   }
  
//   Widget _buildCurrentThemePreview(KeyboardThemeData theme) {
//     return Container(
//       padding: const EdgeInsets.all(16.0),
//       decoration: BoxDecoration(
//         borderRadius: BorderRadius.circular(12),
//         border: Border.all(color: Colors.grey.shade300),
//         gradient: theme.backgroundType == 'gradient'
//             ? LinearGradient(
//                 colors: theme.gradientColors,
//                 begin: Alignment.topLeft,
//                 end: Alignment.bottomRight,
//               )
//             : null,
//         color: theme.backgroundType == 'solid' ? theme.backgroundColor : null,
//       ),
//       child: Column(
//         crossAxisAlignment: CrossAxisAlignment.start,
//         children: [
//           Row(
//             mainAxisAlignment: MainAxisAlignment.spaceBetween,
//             children: [
//               Text(
//                 'Current: ${theme.name}',
//                 style: const TextStyle(
//                   fontWeight: FontWeight.bold,
//                   fontSize: 16,
//                 ),
//               ),
//               Container(
//                 padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
//                 decoration: BoxDecoration(
//                   color: theme.accentColor,
//                   borderRadius: BorderRadius.circular(12),
//                 ),
//                 child: const Text(
//                   'ACTIVE',
//                   style: TextStyle(
//                     color: Colors.white,
//                     fontSize: 10,
//                     fontWeight: FontWeight.bold,
//                   ),
//                 ),
//               ),
//             ],
//           ),
//           const SizedBox(height: 12),
          
//           // Mini keyboard preview
//           Row(
//             mainAxisAlignment: MainAxisAlignment.spaceEvenly,
//             children: [
//               _buildPreviewKey('Q', theme),
//               _buildPreviewKey('W', theme),
//               _buildPreviewKey('E', theme),
//               _buildPreviewKey('R', theme),
//               _buildPreviewKey('T', theme),
//             ],
//           ),
//           const SizedBox(height: 8),
          
//           // Theme description
//           Text(
//             theme.description.isNotEmpty ? theme.description : 'Custom keyboard theme',
//             style: TextStyle(
//               fontSize: 14,
//               color: Colors.grey.shade600,
//             ),
//           ),
//         ],
//       ),
//     );
//   }
  
//   Widget _buildPreviewKey(String letter, KeyboardThemeData theme) {
//     return Container(
//       width: 40,
//       height: 40,
//       decoration: BoxDecoration(
//         color: theme.keyBackgroundColor,
//         borderRadius: BorderRadius.circular(theme.keyCornerRadius),
//         border: theme.keyBorderWidth > 0
//             ? Border.all(
//                 color: theme.keyBorderColor,
//                 width: theme.keyBorderWidth,
//               )
//             : null,
//         boxShadow: theme.showKeyShadows
//             ? [
//                 BoxShadow(
//                   color: theme.shadowColor,
//                   blurRadius: theme.shadowDepth,
//                   offset: Offset(0, theme.shadowDepth / 2),
//                 ),
//               ]
//             : null,
//       ),
//       child: Center(
//         child: Text(
//           letter,
//           style: TextStyle(
//             color: theme.keyTextColor,
//             fontSize: 14,
//             fontFamily: theme.fontFamily,
//             fontWeight: theme.isBold ? FontWeight.bold : FontWeight.normal,
//             fontStyle: theme.isItalic ? FontStyle.italic : FontStyle.normal,
//           ),
//         ),
//       ),
//     );
//   }
  
//   // void _openThemeEditor() {
//   //   Navigator.push(
//   //     context,
//   //     MaterialPageRoute(
//   //       builder: (context) => const ThemeEditorScreen(isCreatingNew: true),
//   //     ),
//   //   ).then((newTheme) {
//   //     if (newTheme != null) {
//   //       setState(() {
//   //         // Theme has been updated, UI will rebuild automatically
//   //       });
//   //     }
//   //   });
//   // }
  
//   void _showThemeGallery() {
//     showModalBottomSheet(
//       context: context,
//       builder: (context) => _buildThemeGalleryModal(),
//       backgroundColor: Colors.transparent,
//       isScrollControlled: true,
//     );
//   }
  
//   Widget _buildThemeGalleryModal() {
//     return Container(
//       height: MediaQuery.of(context).size.height * 0.8,
//       decoration: const BoxDecoration(
//         color: Colors.white,
//         borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
//       ),
//       child: Column(
//         children: [
//           // Modal header
//           Container(
//             padding: const EdgeInsets.all(16.0),
//             decoration: const BoxDecoration(
//               borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
//               color: Colors.deepPurple,
//             ),
//             child: Row(
//               children: [
//                 const Icon(Icons.photo_library, color: Colors.white),
//                 const SizedBox(width: 12),
//                 const Text(
//                   'Theme Gallery',
//                   style: TextStyle(
//                     color: Colors.white,
//                     fontSize: 20,
//                     fontWeight: FontWeight.bold,
//                   ),
//                 ),
//                 const Spacer(),
//                 IconButton(
//                   onPressed: () => Navigator.pop(context),
//                   icon: const Icon(Icons.close, color: Colors.white),
//                 ),
//               ],
//             ),
//           ),
          
//           // Theme list
//           Expanded(
//             child: ListView.builder(
//               padding: const EdgeInsets.all(16.0),
//               itemCount: FlutterThemeManager.instance.availableThemes.length,
//               itemBuilder: (context, index) {
//                 final theme = FlutterThemeManager.instance.availableThemes[index];
//                 final isActive = theme.id == FlutterThemeManager.instance.currentTheme.id;
                
//                 return Card(
//                   margin: const EdgeInsets.only(bottom: 12),
//                   child: ListTile(
//                     contentPadding: const EdgeInsets.all(16),
//                     leading: Container(
//                       width: 50,
//                       height: 50,
//                       decoration: BoxDecoration(
//                         color: theme.backgroundColor,
//                         borderRadius: BorderRadius.circular(8),
//                         border: Border.all(color: theme.keyBorderColor),
//                       ),
//                     ),
//                     title: Text(
//                       theme.name,
//                       style: TextStyle(
//                         fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
//                         color: isActive ? Colors.deepPurple : null,
//                       ),
//                     ),
//                     subtitle: Text(theme.description),
//                     trailing: isActive
//                         ? const Icon(Icons.check_circle, color: Colors.green)
//                         : ElevatedButton(
//                             onPressed: () async {
//                               await FlutterThemeManager.instance.applyTheme(theme);
//                               if (context.mounted) {
//                                 Navigator.pop(context);
//                                 setState(() {
//                                   // Theme updated, rebuild UI
//                                 });
//                               }
//                             },
//                             child: const Text('Apply'),
//                           ),
//                   ),
//                 );
//               },
//             ),
//           ),
//         ],
//       ),
//     );
//   }

//   Widget _buildTestKeyboardCard() {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             Text(
//               'System-Wide Keyboard',
//               style: Theme.of(context).textTheme.headlineSmall,
//             ),
//             const SizedBox(height: 8),
//             IconButton(
//               onPressed: () {
//                 Navigator.of(context).push(MaterialPageRoute(builder: (context) => const KeyboardSettingsScreen()));
//               },
//               icon: const Icon(Icons.settings),
//             ),
//             IconButton(
//               onPressed: () {
//                   Navigator.of(context).push(MaterialPageRoute(builder: (context) => const ThemeGalleryScreen()));
//               },
//               icon: const Icon(Icons.color_lens),
//             ),
//             IconButton(
//               onPressed: () {
//                 if (Platform.isIOS) {
//                   Text('Emoji settings are not available on iOS');
//                   // showToast('Emoji settings are not available on iOS');
//                 } else {
//                   Navigator.of(context).push(MaterialPageRoute(builder: (context) =>  EmojiSettingsScreen()));
//                 }
//               },
//               icon: const Icon(Icons.emoji_emotions),
//             ),
//             IconButton(
//               onPressed: () {
//                 Navigator.of(context).push(MaterialPageRoute(builder: (context) => const DictionaryScreen()));
//               },
//               icon: const Icon(Icons.book),
//             ),
//             Row(
//               children: [
//                 IconButton(
//               onPressed: () {
//                 Navigator.of(context).push(MaterialPageRoute(builder: (context) => const ClipboardScreen()));
//               },
//               icon: const Icon(Icons.person),
//             ),
//                 Text('Clipboard'),

//               ],
//             ),
            
//             Row(
//               children: [
//                 IconButton(
//                   onPressed: () {
//                     Navigator.of(context).push(MaterialPageRoute(builder: (context) => const LanguageScreen()));
//                   },
//                   icon: const Icon(Icons.book),
//                 ),
//                 Text('Language'),
                
//               ],
//             ),
//             const SizedBox(height: 16),
//             const TextField(
//               decoration: InputDecoration(
//                 hintText: 'Tap here to test your AI keyboard...',
//                 border: OutlineInputBorder(),
//               ),
//               maxLines: 3,
//             ),
//             const SizedBox(height: 12),
//             Text(
//               Platform.isIOS
//                   ? 'Make sure AI Keyboard is enabled in iOS Settings → General → Keyboard → Keyboards, then tap the text field above to test.'
//                   : 'Make sure AI Keyboard is selected as your input method, then tap the text field above to test.',
//               style: Theme.of(context).textTheme.bodySmall,
//             ),
//             const SizedBox(height: 12),
//             Container(
//               padding: const EdgeInsets.all(12),
//               decoration: BoxDecoration(
//                 color: Colors.blue.withOpacity(0.1),
//                 borderRadius: BorderRadius.circular(8),
//                 border: Border.all(color: Colors.blue.withOpacity(0.3)),
//               ),
//               child: Column(
//                 crossAxisAlignment: CrossAxisAlignment.start,
//                 children: [
//                   Text(
//                     '💡 Features to try:',
//                     style: TextStyle(
//                       fontWeight: FontWeight.bold,
//                       color: Colors.blue[800],
//                     ),
//                   ),
//                   const SizedBox(height: 8),
//                   const Text('• Use swipe gestures for typing'),
//                   const Text('• Use swipe gestures for quick actions'),
//                   const Text('• Test AI suggestions while typing'),
//                   const Text('• Try caps lock (double-tap shift)'),
//                   const Text('• Switch between letter/symbol/number layouts'),
//                 ],
//               ),
//             ),
//           ],
//         ),
//       ),
//     );
//   }
// }

// // AI Service for text suggestions
// class AIService {
//   static const String _baseUrl = 'https://api.openai.com/v1';
//   static const String _apiKey = 'YOUR_API_KEY_HERE'; // Replace with actual key

//   static Future<List<String>> getTextSuggestions(String currentText) async {
//     try {
//       final response = await http.post(
//         Uri.parse('$_baseUrl/completions'),
//         headers: {
//           'Authorization': 'Bearer $_apiKey',
//           'Content-Type': 'application/json',
//         },
//         body: json.encode({
//           'model': 'gpt-3.5-turbo-instruct',
//           'prompt':
//               'Complete this text with 3 short suggestions: "$currentText"',
//           'max_tokens': 50,
//           'n': 3,
//           'temperature': 0.7,
//         }),
//       );

//       if (response.statusCode == 200) {
//         final data = json.decode(response.body);
//         return (data['choices'] as List)
//             .map((choice) => choice['text'].toString().trim())
//             .toList();
//       }
//     } catch (e) {
//       print('AI Service error: $e');
//     }

//     // Fallback suggestions
//     return _getFallbackSuggestions(currentText);
//   }

//   static List<String> _getFallbackSuggestions(String text) {
//     final words = text.split(' ');
//     final lastWord = words.isNotEmpty ? words.last.toLowerCase() : '';

//     // Simple word completion suggestions
//     final Map<String, List<String>> suggestions = {
//       'the': ['the quick', 'the best', 'the most'],
//       'how': ['how are you', 'how to', 'how much'],
//       'what': ['what is', 'what are', 'what time'],
//       'when': ['when is', 'when are', 'when will'],
//       'where': ['where is', 'where are', 'where to'],
//       'good': ['good morning', 'good night', 'good job'],
//       'thank': ['thank you', 'thank you so much', 'thanks'],
//       'please': ['please help', 'please let me know', 'please send'],
//     };

//     if (suggestions.containsKey(lastWord)) {
//       return suggestions[lastWord]!;
//     }

//     return ['and', 'the', 'to'];
//   }

//   static Future<String> correctGrammar(String text) async {
//     // Simplified grammar correction
//     return text
//         .replaceAll(' i ', ' I ')
//         .replaceAll(' im ', ' I\'m ')
//         .replaceAll(' dont ', ' don\'t ')
//         .replaceAll(' cant ', ' can\'t ')
//         .replaceAll(' wont ', ' won\'t ');
//   }
// }

// // Theme configuration removed - using single default keyboard style
// // Keyboard theme configuration
// class KeyboardTheme {
//   final String name;
//   final Color backgroundColor;
//   final Color keyColor;
//   final Color textColor;
//   final Color accentColor;

//   KeyboardTheme({
//     required this.name,
//     required this.backgroundColor,
//     required this.keyColor,
//     required this.textColor,
//     required this.accentColor,
//   });

//   static KeyboardTheme getTheme(String themeName) {
//     switch (themeName) {
//       case 'dark':
//         return KeyboardTheme(
//           name: 'Dark',
//           backgroundColor: const Color(0xFF1E1E1E),
//           keyColor: const Color(0xFF2D2D2D),
//           textColor: Colors.white,
//           accentColor: Colors.blue,
//         );
//       case 'material_you':
//         return KeyboardTheme(
//           name: 'Material You',
//           backgroundColor: const Color(0xFF6750A4),
//           keyColor: const Color(0xFF7C4DFF),
//           textColor: Colors.white,
//           accentColor: const Color(0xFFBB86FC),
//         );
//       case 'professional':
//         return KeyboardTheme(
//           name: 'Professional',
//           backgroundColor: const Color(0xFF37474F),
//           keyColor: const Color(0xFF455A64),
//           textColor: Colors.white,
//           accentColor: const Color(0xFF26A69A),
//         );
//       case 'colorful':
//         return KeyboardTheme(
//           name: 'Colorful',
//           backgroundColor: const Color(0xFFE1F5FE),
//           keyColor: const Color(0xFF81D4FA),
//           textColor: const Color(0xFF0D47A1),
//           accentColor: const Color(0xFFFF6B35),
//         );
//       default:
//         return KeyboardTheme(
//           name: 'Default',
//           backgroundColor: const Color(0xFFF5F5F5),
//           keyColor: Colors.white,
//           textColor: Colors.black87,
//           accentColor: Colors.blue,
//         );
//     }
//   }

//   Map<String, dynamic> toMap() {
//     return {
//       'name': name,
//       'backgroundColor': backgroundColor.value,
//       'keyColor': keyColor.value,
//       'textColor': textColor.value,
//       'accentColor': accentColor.value,
//     };
//   }
// }

// /// System keyboard status panel
// class SystemKeyboardStatusPanel extends StatelessWidget {
//   const SystemKeyboardStatusPanel({super.key});

//   @override
//   Widget build(BuildContext context) {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             const Row(
//               children: [
//                 Icon(Icons.keyboard, color: Colors.blue),
//                 SizedBox(width: 8),
//                 Text(
//                   'System Keyboard Status',
//                   style: TextStyle(
//                     fontSize: 16,
//                     fontWeight: FontWeight.bold,
//                     color: Colors.blue,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             const Text('✅ AI Keyboard Service: Ready'),
//             const Text('🤖 Autocorrect Engine: Active'),
//             const Text('🧠 Predictive Text: Learning'),
//             const Text('📱 System Integration: Complete'),
//             const SizedBox(height: 16),
//             ElevatedButton.icon(
//               onPressed: () {
//                 showDialog(
//                   context: context,
//                   builder: (context) => AlertDialog(
//                     title: const Text('System Keyboard'),
//                     content: const Text(
//                       'Go to Android Settings > System > Languages & input > Virtual keyboard to enable AI Keyboard',
//                     ),
//                     actions: [
//                       TextButton(
//                         onPressed: () => Navigator.pop(context),
//                         child: const Text('OK'),
//                       ),
//                     ],
//                   ),
//                 );
//               },
//               icon: const Icon(Icons.settings),
//               label: const Text('Enable System Keyboard'),
//             ),
//           ],
//         ),
//       ),
//     );
//   }

// }

// /// AI Service information widget
// class AIServiceInfoWidget extends StatelessWidget {
//   const AIServiceInfoWidget({super.key});

//   @override
//   Widget build(BuildContext context) {
//     return Card(
//       child: Padding(
//         padding: const EdgeInsets.all(16.0),
//         child: Column(
//           crossAxisAlignment: CrossAxisAlignment.start,
//           children: [
//             const Row(
//               children: [
//                 Icon(Icons.psychology, color: Colors.green),
//                 SizedBox(width: 8),
//                 Text(
//                   'AI Features Now Available System-Wide',
//                   style: TextStyle(
//                     fontSize: 16,
//                     fontWeight: FontWeight.bold,
//                     color: Colors.green,
//                   ),
//                 ),
//               ],
//             ),
//             const SizedBox(height: 16),
//             const Text('🔧 Smart Autocorrect'),
//             const Text('   • Typo detection and correction'),
//             const Text('   • Context-aware suggestions'),
//             const SizedBox(height: 8),
//             const Text('🧠 Predictive Text'),
//             const Text('   • Word completion'),
//             const Text('   • Context predictions'),
//             const SizedBox(height: 8),
//             const Text('✨ Learning System'),
//             const Text('   • Adapts to your typing style'),
//             const Text('   • Improves over time'),
//             const SizedBox(height: 16),
//             Container(
//               padding: const EdgeInsets.all(12),
//               decoration: BoxDecoration(
//                 color: Colors.blue.shade50,
//                 borderRadius: BorderRadius.circular(8),
//                 border: Border.all(color: Colors.blue.shade200),
//               ),
//               child: const Column(
//                 crossAxisAlignment: CrossAxisAlignment.start,
//                 children: [
//                   Text(
//                     '🎯 How to Use:',
//                     style: TextStyle(fontWeight: FontWeight.bold),
//                   ),
//                   SizedBox(height: 8),
//                   Text('1. Enable AI Keyboard in Android Settings'),
//                   Text('2. Open any app (WhatsApp, Gmail, etc.)'),
//                   Text('3. Tap in text field → Select AI Keyboard'),
//                   Text('4. Start typing → See AI suggestions!'),
//                 ],
//               ),
//             ),
//           ],
//         ),
//       ),
//     );
//   }
// }
