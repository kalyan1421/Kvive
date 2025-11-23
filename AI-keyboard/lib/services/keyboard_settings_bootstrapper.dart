import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../keyboard_feedback_system.dart';
import '../theme/theme_v2.dart';

/// Ensures keyboard preferences are restored and pushed to the native IME
/// when the Flutter shell starts. Without this bootstrap the Android service
/// only receives updates after opening the settings screen, which causes the
/// keyboard to revert to defaults (font scale, height, feedback toggles) on
/// app relaunch.
class KeyboardSettingsBootstrapper {
  KeyboardSettingsBootstrapper._();

  static const MethodChannel _channel = MethodChannel('ai_keyboard/config');
  static bool _bootstrapped = false;

  /// Restore saved preferences, refresh feedback intensity, and notify the
  /// native keyboard to re-read its configuration. Safe to call multiple times.
  static Future<void> ensureBootstrapped() async {
    if (_bootstrapped) return;

    try {
      final prefs = await SharedPreferences.getInstance();

      // Ensure persisted font and height scale values exist (defaults mimic
      // the on-device sliders).
      final portraitScale = _readDouble(prefs, 'keyboard.fontScalePortrait', 1.0)
          .clamp(0.8, 1.3);
      final landscapeScale = _readDouble(prefs, 'keyboard.fontScaleLandscape', 1.0)
          .clamp(0.8, 1.3);
      final portraitHeight =
          _readInt(prefs, 'flutter.keyboard.heightPercentPortrait', 28)
              .clamp(20, 40);
      final landscapeHeight =
          _readInt(prefs, 'flutter.keyboard.heightPercentLandscape', portraitHeight)
              .clamp(20, 40);

      await prefs.setDouble('keyboard.fontScalePortrait', portraitScale);
      await prefs.setDouble('keyboard.fontScaleLandscape', landscapeScale);
      await prefs.setInt('flutter.keyboard.heightPercentPortrait', portraitHeight);
      await prefs.setInt('flutter.keyboard.heightPercentLandscape', landscapeHeight);
      await prefs.setInt('keyboard_height_percent', portraitHeight);
      await prefs.setDouble('keyboard.scaleY', 1.0);
      await prefs.setDouble('flutter.keyboard.scaleYPortrait', 1.0);
      await prefs.setDouble('flutter.keyboard.scaleYLandscape', 1.0);

      KeyboardThemeV2? defaultTheme;
      String? themeJson = prefs.getString('theme.v2.json');
      Map<String, dynamic>? themeMap;

      if (themeJson != null && themeJson.isNotEmpty) {
        try {
          themeMap = jsonDecode(themeJson) as Map<String, dynamic>;
        } catch (_) {
          themeJson = null;
        }
      }

      if (themeJson == null || themeJson.isEmpty) {
        defaultTheme = KeyboardThemeV2.createDefault();
        await ThemeManagerV2.saveThemeV2(defaultTheme);
        themeMap = defaultTheme.toJson();
        themeJson = jsonEncode(themeMap);
      } else if (themeMap != null && _isLegacyDefaultLight(themeMap)) {
        defaultTheme = KeyboardThemeV2.createDefault();
        await ThemeManagerV2.saveThemeV2(defaultTheme);
        themeMap = defaultTheme.toJson();
        themeJson = jsonEncode(themeMap);
      }
      final storedThemeId = prefs.getString('keyboard.theme');
      if (storedThemeId == null ||
          storedThemeId.isEmpty ||
          storedThemeId == 'default' ||
          storedThemeId == 'default_dark') {
        defaultTheme ??= (() {
          if (themeMap != null) {
            return KeyboardThemeV2.fromJson(themeMap);
          }
          return KeyboardThemeV2.createDefault();
        })();
        await prefs.setString('keyboard.theme', defaultTheme.id);
      }

      // Rehydrate feedback system so haptics obey saved intensity on launch.
      // final haptic = _intensityFor(
      //   prefs.getInt('haptic_intensity'),
      //   FeedbackIntensity.medium,
      // );
      // final visual = _intensityFor(
      //   prefs.getInt('visual_intensity'),
      //   FeedbackIntensity.off,
      // );

      // KeyboardFeedbackSystem.updateSettings(
      //   haptic: haptic,
      //   visual: visual,
      // );

      // Ensure popup preview preference is consistently stored under the legacy key
      // that the Android service reads on boot.
      final popupEnabled =
          prefs.getBool('keyboard_settings.popup_visibility') ??
              prefs.getBool('keyboard.popupPreview') ??
              false;
      await prefs.setBool('keyboard.popupPreview', popupEnabled);

      // Prepare payload for native keyboard service.
      final settingsPayload = <String, dynamic>{
        'theme': prefs.getString('keyboard.theme') ?? 'default_theme',
        'popupEnabled': popupEnabled,
        'aiSuggestions': prefs.getBool('ai_suggestions') ?? true,
        'autoCorrect': prefs.getBool('auto_correct') ?? true,
        'swipeTyping': prefs.getBool('swipe_typing') ?? true,
        'vibration': prefs.getBool('vibration_enabled') ?? true,
        'keyPreview': prefs.getBool('key_preview_enabled') ?? false,
        'shiftFeedback': prefs.getBool('show_shift_feedback') ?? false,
        'showNumberRow': prefs.getBool('keyboard.numberRow') ?? false,
        'showUtilityKey': prefs.getBool('keyboard.showUtilityKey') ?? true,
        'effectType': prefs.getString('flutter.effect.type') ?? 'none',
      };

      try {
        await _channel.invokeMethod('updateSettings', settingsPayload);
      } catch (e, stack) {
        debugPrint('KeyboardSettingsBootstrapper: updateSettings failed → $e');
        debugPrint('$stack');
      }

      try {
        await _channel.invokeMethod('notifyConfigChange');
      } catch (e, stack) {
        debugPrint('KeyboardSettingsBootstrapper: notifyConfigChange failed → $e');
        debugPrint('$stack');
      }

      _bootstrapped = true;
    } catch (e, stack) {
      debugPrint('KeyboardSettingsBootstrapper: bootstrap failed → $e');
      debugPrint('$stack');
    }
  }

  static double _readDouble(SharedPreferences prefs, String key, double fallback) {
    final value = prefs.get(key);
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String) return double.tryParse(value) ?? fallback;
    return fallback;
  }

  static int _readInt(SharedPreferences prefs, String key, int fallback) {
    final value = prefs.get(key);
    if (value is int) return value;
    if (value is double) return value.round();
    if (value is String) return int.tryParse(value) ?? fallback;
    return fallback;
  }

  // static FeedbackIntensity _intensityFor(int? index, FeedbackIntensity fallback) {
  //   if (index == null) return fallback;
  //   if (index < 0 || index >= FeedbackIntensity.values.length) {
  //     return fallback;
  //   }
  //   return FeedbackIntensity.values[index];
  // }

  static bool _isLegacyDefaultLight(Map<String, dynamic> themeJson) {
    final id = (themeJson['id'] as String?)?.toLowerCase();
    if (id != 'default_theme') return false;

    final name = (themeJson['name'] as String?)?.toLowerCase();
    if (name != null && name != 'default light') return false;

    final background = themeJson['background'];
    if (background is Map<String, dynamic>) {
      final color = (background['color'] as String?)?.toLowerCase();
      if (color == '#ffffffff' || color == '#fff8f9fa') return true;
    }

    final keys = themeJson['keys'];
    if (keys is Map<String, dynamic>) {
      final keyBg = (keys['bg'] as String?)?.toLowerCase();
      if (keyBg == '#fff2f2f2' || keyBg == '#ffffffff') return true;
    }

    return false;
  }
}
