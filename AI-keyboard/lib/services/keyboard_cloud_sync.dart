import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';

/// KeyboardCloudSync: Bi-directional sync between local prefs, Firestore, and native keyboard
/// Implements Gboard + CleverType features with cloud sync across devices
class KeyboardCloudSync {
  static const _channel = MethodChannel('ai_keyboard/config');
  static final _firestore = FirebaseFirestore.instance;
  static StreamSubscription<DocumentSnapshot>? _sub;
  
  static const String _tag = 'KeyboardCloudSync';
  
  /// Start listening for remote settings changes
  static Future<void> start() async {
    // Guard against re-entry
    if (_sub != null) {
      debugPrint('$_tag: Sync already active, skipping');
      return;
    }
    
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) {
      debugPrint('$_tag: No user logged in, skipping sync');
      return;
    }
    
    final doc = _firestore
        .collection('users')
        .doc(user.uid)
        .collection('settings')
        .doc('keyboard');
    
    debugPrint('$_tag: Starting cloud sync for user ${user.uid}');
    
    // Create defaults if missing
    final snap = await doc.get();
    if (!snap.exists) {
      debugPrint('$_tag: No remote settings found, creating defaults');
      await doc.set(_getDefaultSettings(), SetOptions(merge: true));
    }
    
    // Live listener: apply remote to local prefs + notify keyboard
    _sub?.cancel();
    _sub = doc.snapshots().listen(
      (d) async {
        if (!d.exists) {
          debugPrint('$_tag: Settings document deleted remotely');
          return;
        }
        
        try {
          final data = d.data() as Map<String, dynamic>;
          debugPrint('$_tag: Remote settings received, applying locally...');
          await _applyRemoteSettings(data);
          debugPrint('$_tag: ✓ Settings applied and keyboard notified');
        } catch (e) {
          debugPrint('$_tag: Error applying remote settings: $e');
        }
      },
      onError: (error) {
        debugPrint('$_tag: Error in settings listener: $error');
      },
    );
  }
  
  /// Stop listening for changes
  static Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    debugPrint('$_tag: Cloud sync stopped');
  }
  
  /// Call whenever user toggles a setting locally
  /// Writes to Firestore, which will trigger the listener to update local + native
  static Future<void> upsert(Map<String, dynamic> partial) async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) {
      debugPrint('$_tag: Cannot upsert - no user logged in');
      return;
    }
    
    final doc = _firestore
        .collection('users')
        .doc(user.uid)
        .collection('settings')
        .doc('keyboard');
    
    partial['updatedAt'] = FieldValue.serverTimestamp();
    
    try {
      await doc.set(partial, SetOptions(merge: true));
      debugPrint('$_tag: ✓ Settings upserted to Firestore: ${partial.keys.toList()}');
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to upsert settings: $e');
    }
  }
  
  /// Get default settings (Gboard + CleverType baseline)
  static Map<String, dynamic> _getDefaultSettings() {
    return {
      "version": 1,
      "theme": "default_theme",
      "popupEnabled": false, // ✅ Popup preview OFF by default
      "aiSuggestions": true,
      "autocorrect": true,
      "emojiSuggestions": true,
      "nextWordPrediction": true,
      "clipboardSuggestions": {
        "enabled": true, // ✅ Clipboard history ON by default
        "windowSec": 60,
        "historyItems": 20, // ✅ Minimum 20 history items by default
      },
      "dictionaryEnabled": true, // ✅ Dictionary ON by default
      "autoCapitalization": true,
      "autoFillSuggestion": true,
      "rememberCapsState": false,
      "doubleSpacePeriod": true,
      "soundEnabled": false, // ✅ Sound OFF by default
      "soundVolume": 0.5,
      "vibrationEnabled": false, // ✅ Vibration OFF by default until user enables
      "vibrationMs": 50,
      "updatedAt": FieldValue.serverTimestamp(),
    };
  }
  
  /// Apply remote settings to local prefs and notify native keyboard
  static Future<void> _applyRemoteSettings(Map<String, dynamic> data) async {
    final prefs = await SharedPreferences.getInstance();
    
    // Persist to SharedPreferences
    await prefs.setBool('popupEnabled', data['popupEnabled'] ?? false);
    await prefs.setBool('ai_suggestions', data['aiSuggestions'] ?? true);
    await prefs.setBool('autocorrect', data['autocorrect'] ?? true);
    await prefs.setBool('emojiSuggestions', data['emojiSuggestions'] ?? true);
    await prefs.setBool('nextWordPrediction', data['nextWordPrediction'] ?? true);
    
    // Clipboard settings
    final clipboardSettings = data['clipboardSuggestions'] as Map<String, dynamic>? ?? {};
    await prefs.setBool('clipboardSuggestions', clipboardSettings['enabled'] ?? true);
    await prefs.setInt('clipboard_window_sec', clipboardSettings['windowSec'] ?? 60);
    await prefs.setInt('clipboard_history_items', clipboardSettings['historyItems'] ?? 20);
    
    await prefs.setBool('dictionaryEnabled', data['dictionaryEnabled'] ?? true);
    await prefs.setBool('autoCapitalization', data['autoCapitalization'] ?? true);
    final autoFill = data['autoFillSuggestion'] ?? true;
    final rememberCaps = data['rememberCapsState'] ?? false;
    await prefs.setBool('autoFillSuggestion', autoFill);
    await prefs.setBool('auto_fill_suggestion', autoFill);
    await prefs.setBool('rememberCapsState', rememberCaps);
    await prefs.setBool('remember_caps_state', rememberCaps);
    await prefs.setBool('doubleSpacePeriod', data['doubleSpacePeriod'] ?? true);
    await prefs.setBool('sound_enabled', data['soundEnabled'] ?? false);  // ✅ Default FALSE
    // ✅ CRITICAL: Convert from 0-1 scale to 0-100 scale for consistency
    final soundVolumePercent = ((data['soundVolume'] ?? 0.5) as double) * 100.0;
    await prefs.setDouble('sound_volume', soundVolumePercent);
    await prefs.setInt('flutter.sound_volume', soundVolumePercent.toInt());
    await prefs.setBool('vibration_enabled', data['vibrationEnabled'] ?? false);  // ✅ Changed default to FALSE
    await prefs.setInt('vibration_ms', data['vibrationMs'] ?? 50);
    await prefs.setString('theme', data['theme'] ?? 'default_theme');
    
    debugPrint('$_tag: ✓ Settings persisted to SharedPreferences');
    
    // Notify native keyboard via MethodChannel
    try {
      await _channel.invokeMethod('updateSettings', {
        'theme': data['theme'] ?? 'default_theme',
        'popupEnabled': data['popupEnabled'] ?? false,
        'aiSuggestions': data['aiSuggestions'] ?? true,
        'autocorrect': data['autocorrect'] ?? true,
        'emojiSuggestions': data['emojiSuggestions'] ?? true,
        'nextWordPrediction': data['nextWordPrediction'] ?? true,
        'clipboardEnabled': clipboardSettings['enabled'] ?? true,
        'clipboardWindowSec': clipboardSettings['windowSec'] ?? 60,
        'clipboardHistoryItems': clipboardSettings['historyItems'] ?? 20,
        'dictionaryEnabled': data['dictionaryEnabled'] ?? true,
        'autoCapitalization': data['autoCapitalization'] ?? true,
        'autoFillSuggestion': data['autoFillSuggestion'] ?? true,
        'rememberCapsState': data['rememberCapsState'] ?? false,
        'doubleSpacePeriod': data['doubleSpacePeriod'] ?? true,
        'soundEnabled': data['soundEnabled'] ?? false,
        'soundVolume': (data['soundVolume'] ?? 0.5).toDouble(), // Keep 0-1 scale for updateSettings
        'vibrationEnabled': data['vibrationEnabled'] ?? false, // ✅ Default OFF
        'vibrationMs': data['vibrationMs'] ?? 50,
      });
      debugPrint('$_tag: ✓ Native keyboard notified via MethodChannel');
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to notify native keyboard: $e');
    }
    
    // Also send broadcast for backwards compatibility
    try {
      await _channel.invokeMethod('sendBroadcast', {
        'action': 'com.kvive.keyboard.SETTINGS_CHANGED'
      });
      debugPrint('$_tag: ✓ Settings broadcast sent');
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to send broadcast: $e');
    }
  }
  
  /// Convenience method to read current settings from Firestore
  static Future<Map<String, dynamic>?> getCurrentSettings() async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return null;
    
    try {
      final doc = await _firestore
          .collection('users')
          .doc(user.uid)
          .collection('settings')
          .doc('keyboard')
          .get();
      
      return doc.exists ? doc.data() : null;
    } catch (e) {
      debugPrint('$_tag: Error reading settings: $e');
      return null;
    }
  }
  
  /// Initialize settings on first run (called after successful login/signup)
  static Future<void> initializeDefaultSettings() async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return;
    
    final doc = _firestore
        .collection('users')
        .doc(user.uid)
        .collection('settings')
        .doc('keyboard');
    
    final snap = await doc.get();
    if (!snap.exists) {
      debugPrint('$_tag: Initializing default settings for new user');
      await doc.set(_getDefaultSettings());
    }
  }
}
