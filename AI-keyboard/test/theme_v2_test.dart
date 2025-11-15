import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'dart:convert';
import '../lib/theme/theme_v2.dart';

void main() {
  group('KeyboardThemeV2 Tests', () {
    test('creates default theme correctly', () {
      final theme = KeyboardThemeV2.createDefault();
      
      expect(theme.id, 'default_theme');
      expect(theme.name, 'Default Dark');
      expect(theme.mode, 'unified');
      expect(theme.background.type, 'solid');
      expect(theme.background.color, const Color(0xFF121212));
      expect(theme.keys.preset, 'rounded');
      expect(theme.keys.bg, const Color(0xFF2C2C2C));
      expect(theme.specialKeys.useAccentForEnter, true);
      expect(theme.specialKeys.accent, const Color(0xFFFF9F1A));
    });

    test('serializes and deserializes JSON correctly', () {
      final originalTheme = KeyboardThemeV2.createDefault();
      final json = originalTheme.toJson();
      final jsonString = jsonEncode(json);
      final parsedJson = jsonDecode(jsonString) as Map<String, dynamic>;
      final restoredTheme = KeyboardThemeV2.fromJson(parsedJson);
      
      expect(restoredTheme.id, originalTheme.id);
      expect(restoredTheme.name, originalTheme.name);
      expect(restoredTheme.mode, originalTheme.mode);
      expect(restoredTheme.keys.bg, originalTheme.keys.bg);
      expect(restoredTheme.specialKeys.accent, originalTheme.specialKeys.accent);
    });

    test('preserves alpha channel when serializing colors', () {
      final pictureTheme = KeyboardThemeV2.createPictureTheme();
      final json = pictureTheme.toJson();
      
      expect(json['keys']['bg'], '#80FFFFFF');
      
      final restoredTheme = KeyboardThemeV2.fromJson(json);
      expect(restoredTheme.keys.bg, pictureTheme.keys.bg);
    });

    test('handles invalid JSON gracefully', () {
      // Empty JSON should return default theme
      final emptyTheme = KeyboardThemeV2.fromJson({});
      expect(emptyTheme.id, 'default_theme');
      
      // Partial JSON should fill in defaults
      final partialTheme = KeyboardThemeV2.fromJson({
        'id': 'test_id',
        'name': 'Test Theme',
      });
      expect(partialTheme.id, 'test_id');
      expect(partialTheme.name, 'Test Theme');
      expect(partialTheme.mode, 'unified'); // Default value
    });

    test('color parsing works correctly', () {
      final theme = KeyboardThemeV2.fromJson({
        'keys': {
          'bg': '#FF0000',
          'text': '#00FF00', 
          'pressed': '#0000FF',
        }
      });
      
      expect(theme.keys.bg, const Color(0xFFFF0000));
      expect(theme.keys.text, const Color(0xFF00FF00));
      expect(theme.keys.pressed, const Color(0xFF0000FF));
    });

    test('creates light theme variant correctly', () {
      final lightTheme = ThemeManagerV2.createLightTheme();
      
      expect(lightTheme.name, 'Default Light');
      expect(lightTheme.background.color, const Color(0xFFF8F9FA));
      expect(lightTheme.keys.bg, const Color(0xFFFFFFFF));
      expect(lightTheme.keys.text, const Color(0xFF3C4043));
      expect(lightTheme.specialKeys.accent, const Color(0xFF1A73E8));
    });

    test('theme validation works', () {
      final validTheme = KeyboardThemeV2.createDefault();
      expect(ThemeManagerV2.validateTheme(validTheme), true);
      
      final invalidTheme = validTheme.copyWith(id: '', name: '');
      expect(ThemeManagerV2.validateTheme(invalidTheme), false);
    });

    test('export and import theme works', () {
      final originalTheme = KeyboardThemeV2.createDefault();
      final exportedJson = ThemeManagerV2.exportTheme(originalTheme);
      final importedTheme = ThemeManagerV2.importTheme(exportedJson);
      
      expect(importedTheme, isNotNull);
      expect(importedTheme!.id, originalTheme.id);
      expect(importedTheme.name, originalTheme.name);
      
      // Test invalid JSON returns null
      final invalidImport = ThemeManagerV2.importTheme('invalid json');
      expect(invalidImport, isNull);
    });

    test('copyWith functionality works', () {
      final originalTheme = KeyboardThemeV2.createDefault();
      final modifiedTheme = originalTheme.copyWith(
        name: 'Modified Theme',
        mode: 'split',
      );
      
      expect(modifiedTheme.name, 'Modified Theme');
      expect(modifiedTheme.mode, 'split');
      expect(modifiedTheme.id, originalTheme.id); // Unchanged
      expect(modifiedTheme.keys, originalTheme.keys); // Unchanged
    });

    test('inheritance logic works correctly', () {
      final theme = KeyboardThemeV2.createDefault();
      
      // Default theme should use unified inheritance
      expect(theme.toolbar.inheritFromKeys, true);
      expect(theme.suggestions.inheritFromKeys, true);
      
      // In unified mode, components should inherit from keys
      expect(theme.mode, 'unified');
    });

    test('special keys configuration is correct', () {
      final theme = KeyboardThemeV2.createDefault();
      
      expect(theme.specialKeys.applyTo, contains('enter'));
      expect(theme.specialKeys.applyTo, contains('globe'));
      expect(theme.specialKeys.applyTo, contains('emoji'));
      expect(theme.specialKeys.applyTo, contains('mic'));
      expect(theme.specialKeys.applyTo, contains('symbols'));
      expect(theme.specialKeys.applyTo, contains('backspace'));
      expect(theme.specialKeys.useAccentForEnter, true);
    });

    test('legacy special key lists are upgraded with new defaults', () {
      final special = ThemeSpecialKeys.fromJson({
        'accent': '#FFFF9F1A',
        'useAccentForEnter': true,
        'applyTo': ['enter', 'globe', 'emoji', 'mic'],
      });

      expect(special.applyTo, contains('symbols'));
      expect(special.applyTo, contains('backspace'));
    });

    test('sound and effects have valid defaults', () {
      final theme = KeyboardThemeV2.createDefault();
      
      expect(theme.sounds.pack, 'default');
      expect(theme.sounds.volume, 0.6);
      expect(theme.sounds.volume >= 0 && theme.sounds.volume <= 1, true);
      
      expect(theme.effects.pressAnimation, 'ripple');
      expect(['ripple', 'bounce', 'glow', 'none'], contains(theme.effects.pressAnimation));
    });
  });

  group('Theme Component Tests', () {
    test('background component handles all types', () {
      // Solid background
      final solidBg = ThemeBackground.fromJson({
        'type': 'solid',
        'color': '#FF0000',
      });
      expect(solidBg.type, 'solid');
      expect(solidBg.color, const Color(0xFFFF0000));
      
      // Gradient background
      final gradientBg = ThemeBackground.fromJson({
        'type': 'gradient',
        'gradient': {
          'colors': ['#FF0000', '#0000FF'],
          'orientation': 'LEFT_RIGHT',
        }
      });
      expect(gradientBg.type, 'gradient');
      expect(gradientBg.gradient!.colors.length, 2);
      expect(gradientBg.gradient!.orientation, 'LEFT_RIGHT');
      
      // Image background
      final imageBg = ThemeBackground.fromJson({
        'type': 'image',
        'imagePath': 'assets/bg.png',
        'imageOpacity': 0.8,
      });
      expect(imageBg.type, 'image');
      expect(imageBg.imagePath, 'assets/bg.png');
      expect(imageBg.imageOpacity, 0.8);
    });

    test('keys component validates properly', () {
      final keys = ThemeKeys.fromJson({
        'preset': 'floating',
        'bg': '#123456',
        'text': '#FFFFFF',
        'radius': 15.0,
        'border': {
          'enabled': false,
          'widthDp': 2.0,
        }
      });
      
      expect(keys.preset, 'floating');
      expect(keys.bg, const Color(0xFF123456));
      expect(keys.radius, 15.0);
      expect(keys.border.enabled, false);
      expect(keys.border.widthDp, 2.0);
    });
  });
}
