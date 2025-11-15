import 'dart:convert';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const Object _noChange = Object();

/// CleverType Theme Engine V2 - Flutter Models
/// Mirrors the Android ThemeModels.kt for consistency
/// Single source of truth for theme data across platforms

@immutable
class KeyboardThemeV2 {
  final String id;
  final String name;
  final String mode; // "unified" or "split"
  final ThemeBackground background;
  final ThemeKeys keys;
  final ThemeSpecialKeys specialKeys;
  final ThemeToolbar toolbar;
  final ThemeSuggestions suggestions;
  final ThemeEffects effects;
  final ThemeSounds sounds;
  final ThemeStickers stickers;
  final ThemeAdvanced advanced;

  const KeyboardThemeV2({
    required this.id,
    required this.name,
    required this.mode,
    required this.background,
    required this.keys,
    required this.specialKeys,
    required this.toolbar,
    required this.suggestions,
    required this.effects,
    required this.sounds,
    required this.stickers,
    required this.advanced,
  });

  /// Create default theme (primary fallback)
  static KeyboardThemeV2 createDefault() {
    final dark = createDarkTheme();
    return dark.copyWith(
      id: 'default_theme',
      name: 'Default Dark',
    );
  }

  /// Create default light theme
  static KeyboardThemeV2 createDefaultLight() {
    return KeyboardThemeV2(
      id: 'default_light_theme',
      name: 'Default Light',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFFFFFF),
        imagePath: null,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFFFFF),
        text: Color(0xFF3C4043),
        pressed: Color(0xFFE8EAED),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(
          enabled: true,
          color: Color(0xFFDADCE0),
          widthDp: 1.0,
        ),
        radius: 8.0,
        shadow: ThemeKeysShadow(
          enabled: true,
          elevationDp: 1.0,
          glow: false,
        ),
        font: ThemeKeysFont(
          family: 'Roboto',
          sizeSp: 18.0,
          bold: false,
          italic: false,
        ),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF1A73E8),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF5F6368),
      ),
      toolbar: const ThemeToolbar(
        inheritFromKeys: true,
        bg: Color(0xFFFFFFFF),
        icon: Color(0xFF5F6368),
        heightDp: 44.0,
        activeAccent: Color(0xFF1A73E8),
        iconPack: 'default',
      ),
      suggestions: const ThemeSuggestions(
        inheritFromKeys: true,
        bg: Color(0xFFFFFFFF),
        text: Color(0xFF3C4043),
        chip: ThemeChip(
          bg: Color(0xFFF1F3F4),
          text: Color(0xFF3C4043),
          pressed: Color(0xFFE8EAED),
          radius: 14.0,
          spacingDp: 6.0,
        ),
        font: ThemeSuggestionsFont(
          family: 'Roboto',
          sizeSp: 15.0,
          bold: false,
        ),
      ),
      effects: const ThemeEffects(
        pressAnimation: 'none',
        globalEffects: [],
      ),
      sounds: const ThemeSounds(
        pack: 'silent',
        customUris: {},
        volume: 0.0,
      ),
      stickers: const ThemeStickers(
        enabled: false,
        pack: '',
        position: 'behind',
        opacity: 0.9,
        animated: false,
      ),
      advanced: const ThemeAdvanced(
        livePreview: true,
        galleryEnabled: true,
        shareEnabled: true,
        dynamicTheme: 'none',
        seasonalPack: 'none',
        materialYouExtract: false,
      ),
    );
  }

  /// Create Valentine's Day theme
  static KeyboardThemeV2 createValentineTheme() {
    return KeyboardThemeV2(
      id: 'valentine_theme',
      name: 'Valentine\'s Day',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: null,
        imagePath: null,
        imageOpacity: 0.85,
        gradient: ThemeGradient(
          colors: [Color(0xFFFF6B9D), Color(0xFFFF8A80)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFFFFF),
        text: Color(0xFFE91E63),
        pressed: Color(0xFFFC9FF2),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(
          enabled: true,
          color: Color(0xFFFF6B9D),
          widthDp: 2.0,
        ),
        radius: 12.0,
        shadow: ThemeKeysShadow(
          enabled: true,
          elevationDp: 3.0,
          glow: true,
        ),
        font: ThemeKeysFont(
          family: 'Roboto',
          sizeSp: 18.0,
          bold: false,
          italic: false,
        ),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFE91E63),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(
        inheritFromKeys: false,
        bg: Color(0xFFFF6B9D),
        icon: Color(0xFFFFFFFF),
        heightDp: 44.0,
        activeAccent: Color(0xFFE91E63),
        iconPack: 'default',
      ),
      suggestions: const ThemeSuggestions(
        inheritFromKeys: false,
        bg: Color(0xFFFFFFFF),
        text: Color(0xFFE91E63),
        chip: ThemeChip(
          bg: Color(0xFFFC9FF2),
          text: Color(0xFFFFFFFF),
          pressed: Color(0xFFE91E63),
          radius: 16.0,
          spacingDp: 8.0,
        ),
        font: ThemeSuggestionsFont(
          family: 'Roboto',
          sizeSp: 15.0,
          bold: true,
        ),
      ),
      effects: const ThemeEffects(
        pressAnimation: 'glow',
        globalEffects: ['hearts'],
      ),
      sounds: const ThemeSounds(
        pack: 'soft',
        customUris: {},
        volume: 0.8,
      ),
      stickers: const ThemeStickers(
        enabled: true,
        pack: 'valentine',
        position: 'above',
        opacity: 0.7,
        animated: true,
      ),
      advanced: const ThemeAdvanced(
        livePreview: true,
        galleryEnabled: true,
        shareEnabled: true,
        dynamicTheme: 'seasonal',
        seasonalPack: 'valentine',
        materialYouExtract: false,
      ),
    );
  }

  /// Create Adaptive theme
  static KeyboardThemeV2 createAdaptiveTheme() {
    return KeyboardThemeV2(
      id: 'adaptive_theme',
      name: 'Adaptive',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'adaptive',
        color: Color(0xFF1B1B1F),
        imagePath: null,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: ThemeAdaptive(
          enabled: true,
          source: 'wallpaper',
          materialYou: true,
        ),
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFF3A3A3F),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF505056),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(
          enabled: true,
          color: Color(0xFF636366),
          widthDp: 1.0,
        ),
        radius: 10.0,
        shadow: ThemeKeysShadow(
          enabled: true,
          elevationDp: 2.0,
          glow: false,
        ),
        font: ThemeKeysFont(
          family: 'Roboto',
          sizeSp: 18.0,
          bold: false,
          italic: false,
        ),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF4285F4),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(
        inheritFromKeys: true,
        bg: Color(0xFF3A3A3F),
        icon: Color(0xFFFFFFFF),
        heightDp: 44.0,
        activeAccent: Color(0xFF4285F4),
        iconPack: 'default',
      ),
      suggestions: const ThemeSuggestions(
        inheritFromKeys: true,
        bg: Color(0xFF3A3A3F),
        text: Color(0xFFFFFFFF),
        chip: ThemeChip(
          bg: Color(0xFF4A4A50),
          text: Color(0xFFFFFFFF),
          pressed: Color(0xFF5A5A60),
          radius: 14.0,
          spacingDp: 6.0,
        ),
        font: ThemeSuggestionsFont(
          family: 'Roboto',
          sizeSp: 15.0,
          bold: false,
        ),
      ),
      effects: const ThemeEffects(
        pressAnimation: 'ripple',
        globalEffects: [],
      ),
      sounds: const ThemeSounds(
        pack: 'soft',
        customUris: {},
        volume: 0.6,
      ),
      stickers: const ThemeStickers(
        enabled: false,
        pack: '',
        position: 'behind',
        opacity: 0.9,
        animated: false,
      ),
      advanced: const ThemeAdvanced(
        livePreview: true,
        galleryEnabled: true,
        shareEnabled: true,
        dynamicTheme: 'wallpaper',
        seasonalPack: 'none',
        materialYouExtract: true,
      ),
    );
  }

  /// Create White theme (CleverType style)
  static KeyboardThemeV2 createWhiteTheme() {
    return KeyboardThemeV2(
      id: 'theme_white',
      name: 'White',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFFFFFF),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFF2F2F2),
        text: Color(0xFF000000),
        pressed: Color(0xFFDDDDDD),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF000000), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF007AFF),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFFFFF), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF007AFF), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFFFFF), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF2F2F2), text: Color(0xFF000000), pressed: Color(0xFFDDDDDD), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Dark theme
  static KeyboardThemeV2 createDarkTheme() {
    return KeyboardThemeV2(
      id: 'theme_dark',
      name: 'Dark',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF121212),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF2C2C2C),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF3A3A3A),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFFFFFFF), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFF9F1A),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF121212), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF121212), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFF2C2C2C), text: Color(0xFFFFFFFF), pressed: Color(0xFF3A3A3A), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: ['glow']),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Yellow theme
  static KeyboardThemeV2 createYellowTheme() {
    return KeyboardThemeV2(
      id: 'theme_yellow',
      name: 'Yellow',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFFD54F),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFF176),
        text: Color(0xFF000000),
        pressed: Color(0xFFFBC02D),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFFFB300), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFFB300),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFD54F), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFFB300), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFD54F), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFFFF176), text: Color(0xFF000000), pressed: Color(0xFFFBC02D), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: []),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Red theme
  static KeyboardThemeV2 createRedTheme() {
    return KeyboardThemeV2(
      id: 'theme_red',
      name: 'Red',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFE53935),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'flat',
        bg: Color(0xFFFFCDD2),
        text: Color(0xFF000000),
        pressed: Color(0xFFD32F2F),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF000000), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFC62828),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFE53935), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFC62828), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFE53935), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFFFCDD2), text: Color(0xFF000000), pressed: Color(0xFFD32F2F), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Gradient theme
  static KeyboardThemeV2 createGradientTheme() {
    return KeyboardThemeV2(
      id: 'theme_gradient',
      name: 'Gradient',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: Color(0xFFFFB347),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: ThemeGradient(
          colors: [Color(0xFFFFB347), Color(0xFFFFCC33), Color(0xFFFF6B6B)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFF5F5F5),
        text: Color(0xFF222222),
        pressed: Color(0xFFE0E0E0),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF222222), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFF7043),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF222222),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFB347), icon: Color(0xFF222222), heightDp: 44.0, activeAccent: Color(0xFFFF7043), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFB347), text: Color(0xFF222222), chip: ThemeChip(bg: Color(0xFFF5F5F5), text: Color(0xFF222222), pressed: Color(0xFFE0E0E0), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['particles']),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Picture theme (with image background)
  static KeyboardThemeV2 createPictureTheme() {
    return KeyboardThemeV2(
      id: 'theme_picture',
      name: 'Picture',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'image',
        color: Color(0x00000000),
        imagePath: 'user_upload.jpg',
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'transparent',
        bg: Color(0x80FFFFFF), // Semi-transparent white
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFFDDDDDD),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFFFFFFF), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFF9F1A),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0x80000000), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0x80000000), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0x80FFFFFF), text: Color(0xFFFFFFFF), pressed: Color(0xFFDDDDDD), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Blue theme
  static KeyboardThemeV2 createBlueTheme() {
    return KeyboardThemeV2(
      id: 'theme_blue',
      name: 'Blue',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF2196F3),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF64B5F6),
        text: Color(0xFF000000),
        pressed: Color(0xFF1976D2),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF1565C0), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF0D47A1),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF2196F3), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF0D47A1), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF2196F3), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFF64B5F6), text: Color(0xFF000000), pressed: Color(0xFF1976D2), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Green theme
  static KeyboardThemeV2 createGreenTheme() {
    return KeyboardThemeV2(
      id: 'theme_green',
      name: 'Green',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF4CAF50),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF81C784),
        text: Color(0xFF000000),
        pressed: Color(0xFF388E3C),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF2E7D32), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF1B5E20),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF4CAF50), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF1B5E20), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF4CAF50), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFF81C784), text: Color(0xFF000000), pressed: Color(0xFF388E3C), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Purple theme
  static KeyboardThemeV2 createPurpleTheme() {
    return KeyboardThemeV2(
      id: 'theme_purple',
      name: 'Purple',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF9C27B0),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFBA68C8),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF7B1FA2),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF4A148C), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF4A148C),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF9C27B0), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF4A148C), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF9C27B0), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFFBA68C8), text: Color(0xFFFFFFFF), pressed: Color(0xFF7B1FA2), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Orange theme  
  static KeyboardThemeV2 createOrangeTheme() {
    return KeyboardThemeV2(
      id: 'theme_orange',
      name: 'Orange',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFF9800),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFB74D),
        text: Color(0xFF000000),
        pressed: Color(0xFFF57C00),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFE65100), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFE65100),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFF9800), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFE65100), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFF9800), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFFFB74D), text: Color(0xFF000000), pressed: Color(0xFFF57C00), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: []),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Pink theme
  static KeyboardThemeV2 createPinkTheme() {
    return KeyboardThemeV2(
      id: 'theme_pink',
      name: 'Pink',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFE91E63),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFF48FB1),
        text: Color(0xFF000000),
        pressed: Color(0xFFC2185B),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF880E4F), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF880E4F),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFE91E63), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF880E4F), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFE91E63), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF48FB1), text: Color(0xFF000000), pressed: Color(0xFFC2185B), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['hearts']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: true, pack: 'valentine', position: 'behind', opacity: 0.7, animated: true),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'valentine', materialYouExtract: false),
    );
  }

  /// Create Cyan theme
  static KeyboardThemeV2 createCyanTheme() {
    return KeyboardThemeV2(
      id: 'theme_cyan',
      name: 'Cyan',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF00BCD4),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'flat',
        bg: Color(0xFF4DD0E1),
        text: Color(0xFF000000),
        pressed: Color(0xFF0097A7),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF006064), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF006064),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF00BCD4), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF006064), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF00BCD4), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFF4DD0E1), text: Color(0xFF000000), pressed: Color(0xFF0097A7), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Light Blue theme
  static KeyboardThemeV2 createLightBlueTheme() {
    return KeyboardThemeV2(
      id: 'theme_light_blue',
      name: 'Light Blue',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF03A9F4),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFF4FC3F7),
        text: Color(0xFF000000),
        pressed: Color(0xFF0288D1),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFF01579B), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF01579B),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF03A9F4), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF01579B), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF03A9F4), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFF4FC3F7), text: Color(0xFF000000), pressed: Color(0xFF0288D1), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Dark Blue theme
  static KeyboardThemeV2 createDarkBlueTheme() {
    return KeyboardThemeV2(
      id: 'theme_dark_blue',
      name: 'Dark Blue',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF1565C0),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF1E88E5),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF0D47A1),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF0D47A1), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF0D47A1),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF1565C0), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF0D47A1), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF1565C0), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFF1E88E5), text: Color(0xFFFFFFFF), pressed: Color(0xFF0D47A1), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Lime theme
  static KeyboardThemeV2 createLimeTheme() {
    return KeyboardThemeV2(
      id: 'theme_lime',
      name: 'Lime',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFCDDC39),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFD4E157),
        text: Color(0xFF000000),
        pressed: Color(0xFF9E9D24),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF827717), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF827717),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFCDDC39), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF827717), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFCDDC39), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFD4E157), text: Color(0xFF000000), pressed: Color(0xFF9E9D24), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: []),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Amber theme
  static KeyboardThemeV2 createAmberTheme() {
    return KeyboardThemeV2(
      id: 'theme_amber',
      name: 'Amber',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFFC107),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFD54F),
        text: Color(0xFF000000),
        pressed: Color(0xFFFF8F00),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFFF6F00), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFF6F00),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFC107), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFF6F00), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFC107), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFFFD54F), text: Color(0xFF000000), pressed: Color(0xFFFF8F00), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: []),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Teal theme
  static KeyboardThemeV2 createTealTheme() {
    return KeyboardThemeV2(
      id: 'theme_teal',
      name: 'Teal',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF009688),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF4DB6AC),
        text: Color(0xFF000000),
        pressed: Color(0xFF00695C),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF004D40), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF004D40),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF009688), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF004D40), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF009688), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFF4DB6AC), text: Color(0xFF000000), pressed: Color(0xFF00695C), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Indigo theme
  static KeyboardThemeV2 createIndigoTheme() {
    return KeyboardThemeV2(
      id: 'theme_indigo',
      name: 'Indigo',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF3F51B5),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF7986CB),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF303F9F),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF1A237E), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF1A237E),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF3F51B5), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF1A237E), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF3F51B5), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFF7986CB), text: Color(0xFFFFFFFF), pressed: Color(0xFF303F9F), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Brown theme
  static KeyboardThemeV2 createBrownTheme() {
    return KeyboardThemeV2(
      id: 'theme_brown',
      name: 'Brown',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF795548),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFA1887F),
        text: Color(0xFF000000),
        pressed: Color(0xFF5D4037),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFF3E2723), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF3E2723),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF795548), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF3E2723), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF795548), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFA1887F), text: Color(0xFF000000), pressed: Color(0xFF5D4037), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Deep Purple theme
  static KeyboardThemeV2 createDeepPurpleTheme() {
    return KeyboardThemeV2(
      id: 'theme_deep_purple',
      name: 'Deep Purple',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF673AB7),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF9575CD),
        text: Color(0xFFFFFFFF),
        pressed: Color(0xFF512DA8),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF311B92), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF311B92),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF673AB7), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF311B92), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF673AB7), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFF9575CD), text: Color(0xFFFFFFFF), pressed: Color(0xFF512DA8), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['sparkles']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Light Green theme
  static KeyboardThemeV2 createLightGreenTheme() {
    return KeyboardThemeV2(
      id: 'theme_light_green',
      name: 'Light Green',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF8BC34A),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFAED581),
        text: Color(0xFF000000),
        pressed: Color(0xFF689F38),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF33691E), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF33691E),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFF8BC34A), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFF33691E), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFF8BC34A), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFAED581), text: Color(0xFF000000), pressed: Color(0xFF689F38), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Deep Orange theme
  static KeyboardThemeV2 createDeepOrangeTheme() {
    return KeyboardThemeV2(
      id: 'theme_deep_orange',
      name: 'Deep Orange',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFF5722),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'flat',
        bg: Color(0xFFFF8A65),
        text: Color(0xFF000000),
        pressed: Color(0xFFE64A19),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFBF360C), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFBF360C),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFF5722), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFBF360C), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFF5722), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFFF8A65), text: Color(0xFF000000), pressed: Color(0xFFE64A19), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: []),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Love Hearts theme (based on heart-shaped keys screenshot)
  static KeyboardThemeV2 createLoveHeartsTheme() {
    return KeyboardThemeV2(
      id: 'theme_love_hearts',
      name: 'Love Hearts',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: Color(0xFFE91E63),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: ThemeGradient(
          colors: [Color(0xFFE91E63), Color(0xFFAD1457)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: ['hearts'],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFF8BBD9),
        text: Color(0xFF4A148C),
        pressed: Color(0xFFE91E63),
        rippleAlpha: 0.15,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF880E4F), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF880E4F),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF4A148C),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFFE91E63), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF880E4F), iconPack: 'valentine'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFFF8BBD9), text: Color(0xFF4A148C), chip: ThemeChip(bg: Color(0xFFE91E63), text: Color(0xFFFFFFFF), pressed: Color(0xFF880E4F), radius: 20.0, spacingDp: 8.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['hearts', 'sparkles']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.8),
      stickers: const ThemeStickers(enabled: true, pack: 'valentine', position: 'above', opacity: 0.8, animated: true),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'seasonal', seasonalPack: 'valentine', materialYouExtract: false),
    );
  }

  /// Create Warning/Alert theme (based on triangular warning keys screenshot)
  static KeyboardThemeV2 createWarningTheme() {
    return KeyboardThemeV2(
      id: 'theme_warning',
      name: 'Alert',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFF5722),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'flat',
        bg: Color(0xFFFFCC80),
        text: Color(0xFF000000),
        pressed: Color(0xFFE64A19),
        rippleAlpha: 0.15,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFBF360C), widthDp: 2.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFBF360C),
        useAccentForEnter: true,
        applyTo: ['enter', 'delete', 'shift'],
        spaceLabelColor: Color(0xFF000000),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFFFF5722), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFFBF360C), iconPack: 'alert'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFFFF5722), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFFFFCC80), text: Color(0xFF000000), pressed: Color(0xFFE64A19), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'bounce', globalEffects: ['glow']),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.7),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Galaxy theme (dark with bright accents)
  static KeyboardThemeV2 createGalaxyTheme() {
    return KeyboardThemeV2(
      id: 'theme_galaxy',
      name: 'Galaxy',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: Color(0xFF0D1421),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: ThemeGradient(
          colors: [Color(0xFF0D1421), Color(0xFF1A237E), Color(0xFF4A148C)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: ['sparkles'],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFF263238),
        text: Color(0xFFE1F5FE),
        pressed: Color(0xFF37474F),
        rippleAlpha: 0.2,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFF00E5FF), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF00E5FF),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFE1F5FE),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFF0D1421), icon: Color(0xFF00E5FF), heightDp: 44.0, activeAccent: Color(0xFF00E5FF), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFF0D1421), text: Color(0xFFE1F5FE), chip: ThemeChip(bg: Color(0xFF263238), text: Color(0xFFE1F5FE), pressed: Color(0xFF37474F), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['sparkles', 'glow']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: true, pack: 'space', position: 'behind', opacity: 0.9, animated: true),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'time_of_day', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Sunset theme (warm gradient)
  static KeyboardThemeV2 createSunsetTheme() {
    return KeyboardThemeV2(
      id: 'theme_sunset',
      name: 'Sunset',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: Color(0xFFFF7043),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: ThemeGradient(
          colors: [Color(0xFFFF7043), Color(0xFFFF9800), Color(0xFFFFD54F)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: ['glow'],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFFF3E0),
        text: Color(0xFFBF360C),
        pressed: Color(0xFFFFCC80),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFE65100), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFBF360C),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFBF360C),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFFFF7043), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFFBF360C), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFFFF9800), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFFFFF3E0), text: Color(0xFFBF360C), pressed: Color(0xFFFFCC80), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['glow']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'time_of_day', seasonalPack: 'summer', materialYouExtract: false),
    );
  }

  /// Create Ocean theme (blue gradient)
  static KeyboardThemeV2 createOceanTheme() {
    return KeyboardThemeV2(
      id: 'theme_ocean',
      name: 'Ocean',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'gradient',
        color: Color(0xFF006064),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: ThemeGradient(
          colors: [Color(0xFF006064), Color(0xFF0097A7), Color(0xFF00BCD4)],
          orientation: 'TOP_BOTTOM',
        ),
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFB2EBF2),
        text: Color(0xFF006064),
        pressed: Color(0xFF4DD0E1),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFF006064), widthDp: 0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF006064),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF006064),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFF006064), icon: Color(0xFFFFFFFF), heightDp: 44.0, activeAccent: Color(0xFF00BCD4), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFF00BCD4), text: Color(0xFFFFFFFF), chip: ThemeChip(bg: Color(0xFFB2EBF2), text: Color(0xFF006064), pressed: Color(0xFF4DD0E1), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Neon theme (bright colors on dark)
  static KeyboardThemeV2 createNeonTheme() {
    return KeyboardThemeV2(
      id: 'theme_neon',
      name: 'Neon',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFF0A0A0A),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: ['glow'],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFF1A1A1A),
        text: Color(0xFF00E5FF),
        pressed: Color(0xFF2A2A2A),
        rippleAlpha: 0.2,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFF00E5FF), widthDp: 1.5),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFFF1744),
        useAccentForEnter: true,
        applyTo: ['enter', 'delete', 'shift'],
        spaceLabelColor: Color(0xFF00E5FF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: false, bg: Color(0xFF0A0A0A), icon: Color(0xFF00E5FF), heightDp: 44.0, activeAccent: Color(0xFFFF1744), iconPack: 'neon'),
      suggestions: const ThemeSuggestions(inheritFromKeys: false, bg: Color(0xFF0A0A0A), text: Color(0xFF00E5FF), chip: ThemeChip(bg: Color(0xFF1A1A1A), text: Color(0xFF00E5FF), pressed: Color(0xFF2A2A2A), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['glow']),
      sounds: const ThemeSounds(pack: 'clicky', customUris: {}, volume: 0.7),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Pastel Pink theme (soft and light)
  static KeyboardThemeV2 createPastelPinkTheme() {
    return KeyboardThemeV2(
      id: 'theme_pastel_pink',
      name: 'Pastel Pink',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFF8BBD9),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFCE4EC),
        text: Color(0xFF880E4F),
        pressed: Color(0xFFF48FB1),
        rippleAlpha: 0.1,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFAD1457), widthDp: 0),
        radius: 14.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFFAD1457),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF880E4F),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFF8BBD9), icon: Color(0xFF880E4F), heightDp: 44.0, activeAccent: Color(0xFFAD1457), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFF8BBD9), text: Color(0xFF880E4F), chip: ThemeChip(bg: Color(0xFFFCE4EC), text: Color(0xFF880E4F), pressed: Color(0xFFF48FB1), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.4),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Gold Star theme (based on star-shaped keys screenshot)
  static KeyboardThemeV2 createGoldStarTheme() {
    return KeyboardThemeV2(
      id: 'theme_gold_star',
      name: 'Gold Star',
      mode: 'unified',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFFFD700),
        imagePath: null,
        imageOpacity: 1.0,
        gradient: null,
        overlayEffects: ['sparkles'],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFFF59D),
        text: Color(0xFF6C4400),
        pressed: Color(0xFFFFC107),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: false, color: Color(0xFFE65100), widthDp: 0),
        radius: 20.0, // Extra rounded for star effect
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 6.0, glow: true),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: true, italic: false),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF6C4400),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF6C4400),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFD700), icon: Color(0xFF6C4400), heightDp: 44.0, activeAccent: Color(0xFF6C4400), iconPack: 'star'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFD700), text: Color(0xFF6C4400), chip: ThemeChip(bg: Color(0xFFFFF59D), text: Color(0xFF6C4400), pressed: Color(0xFFFFC107), radius: 18.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: true)),
      effects: const ThemeEffects(pressAnimation: 'glow', globalEffects: ['sparkles']),
      sounds: const ThemeSounds(pack: 'soft', customUris: {}, volume: 0.6),
      stickers: const ThemeStickers(enabled: true, pack: 'celebration', position: 'above', opacity: 0.8, animated: true),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Bench Image theme
  static KeyboardThemeV2 createBenchImageTheme(String localImagePath) {
    return KeyboardThemeV2(
      id: 'theme_bench_image',
      name: 'Bench',
      mode: 'unified',
      background: ThemeBackground(
        type: 'image',
        color: const Color(0xFFFFFFFF).withOpacity(0.0),
        imagePath: localImagePath,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Colors.transparent, // Solid white for better visibility on image
        text: Color(0xFF000000),
        pressed: Color(0xFFE8E8E8),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFDADCE0), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys:  ThemeSpecialKeys(
        accent:  Color(0xFFFFFFFF).withOpacity(0.0),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFFFFF), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFFFFF), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF2F2F2), text: Color(0xFF000000), pressed: Color(0xFFE8E8E8), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Circle Design Image theme
  static KeyboardThemeV2 createCircleDesignImageTheme(String localImagePath) {
    return KeyboardThemeV2(
      id: 'theme_circle_design',
      name: 'Circle Design',
      mode: 'unified',
      background: ThemeBackground(
        type: 'image',
        color: const Color(0xFFFFFFFF).withOpacity(0.0),
        imagePath: localImagePath,
        imageOpacity: 0.88,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFFFFFF), // Solid white for better visibility
        text: Color(0xFF000000),
        pressed: Color(0xFFE8E8E8),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFDADCE0), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys:  ThemeSpecialKeys(
        accent: const Color(0xFFFFFFFF).withOpacity(0.0),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFFFFF), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFFFFF), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF2F2F2), text: Color(0xFF000000), pressed: Color(0xFFE8E8E8), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Sunset Image theme
  static KeyboardThemeV2 createSunsetImageTheme(String localImagePath) {
    return KeyboardThemeV2(
      id: 'theme_sunset_image',
      name: 'Sunset',
      mode: 'unified',
      background: ThemeBackground(
        type: 'image',
        color: const Color(0xFFFFFFFF).withOpacity(0.0),
        imagePath: localImagePath,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFFFFFF), // Solid white for better visibility
        text: Color(0xFF000000),
        pressed: Color(0xFFE8E8E8),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFDADCE0), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys:  ThemeSpecialKeys(
        accent: const Color(0xFFFFFFFF).withOpacity(0.0),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFFFFF), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFFFFF), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF2F2F2), text: Color(0xFF000000), pressed: Color(0xFFE8E8E8), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Create Sun Moon Image theme
  static KeyboardThemeV2 createSunMoonImageTheme(String localImagePath) {
    return KeyboardThemeV2(
      id: 'theme_sun_moon',
      name: 'Sun & Moon',
      mode: 'unified',
      background: ThemeBackground(
        type: 'image',
        color: const Color(0xFFFFFFFF).withOpacity(0.0),
        imagePath: localImagePath,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
        adaptive: null,
      ),
      keys: const ThemeKeys(
        preset: 'rounded',
        bg: Color(0xFFFFFFFF), // Solid white for better visibility
        text: Color(0xFF000000),
        pressed: Color(0xFFE8E8E8),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(enabled: true, color: Color(0xFFDADCE0), widthDp: 1.0),
        radius: 4.0,
        shadow: ThemeKeysShadow(enabled: true, elevationDp: 1.0, glow: false),
        font: ThemeKeysFont(family: 'Roboto', sizeSp: 16.0, bold: false, italic: false),
      ),
      specialKeys:  ThemeSpecialKeys(
        accent:  Color(0xFFFFFFFF).withOpacity(0.0),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFFFFFFFF),
      ),
      toolbar: const ThemeToolbar(inheritFromKeys: true, bg: Color(0xFFFFFFFF), icon: Color(0xFF000000), heightDp: 44.0, activeAccent: Color(0xFFFF9F1A), iconPack: 'default'),
      suggestions: const ThemeSuggestions(inheritFromKeys: true, bg: Color(0xFFFFFFFF), text: Color(0xFF000000), chip: ThemeChip(bg: Color(0xFFF2F2F2), text: Color(0xFF000000), pressed: Color(0xFFE8E8E8), radius: 14.0, spacingDp: 6.0), font: ThemeSuggestionsFont(family: 'Roboto', sizeSp: 15.0, bold: false)),
      effects: const ThemeEffects(pressAnimation: 'ripple', globalEffects: []),
      sounds: const ThemeSounds(pack: 'default', customUris: {}, volume: 0.5),
      stickers: const ThemeStickers(enabled: false, pack: '', position: 'behind', opacity: 0.9, animated: false),
      advanced: const ThemeAdvanced(livePreview: true, galleryEnabled: true, shareEnabled: true, dynamicTheme: 'none', seasonalPack: 'none', materialYouExtract: false),
    );
  }

  /// Get all available preset themes (CleverType style)
  static List<KeyboardThemeV2> getPresetThemes() {
    return [
      createWhiteTheme(),
      createDarkTheme(),
      createYellowTheme(),
      createRedTheme(),
      createGradientTheme(),
      createPictureTheme(),
      createBlueTheme(),
      createGreenTheme(),
      createPurpleTheme(),
      createOrangeTheme(),
      createPinkTheme(),
      createCyanTheme(),
      createLightBlueTheme(),
      createDarkBlueTheme(),
      createLimeTheme(),
      createAmberTheme(),
      createTealTheme(),
      createIndigoTheme(),
      createBrownTheme(),
      createDeepPurpleTheme(),
      createLightGreenTheme(),
      createDeepOrangeTheme(),
      createLoveHeartsTheme(),
      createWarningTheme(),
      createGalaxyTheme(),
      createSunsetTheme(),
      createOceanTheme(),
      createNeonTheme(),
      createPastelPinkTheme(),
      createGoldStarTheme(),
    ];
  }

  /// Get themes by category
  static Map<String, List<KeyboardThemeV2>> getThemesByCategory() {
    return {
      'Popular': [createWhiteTheme(), createDarkTheme(), createBlueTheme(), createPinkTheme(), createGoldStarTheme()],
      'Vibrant': [createYellowTheme(), createRedTheme(), createOrangeTheme(), createLimeTheme(), createNeonTheme()],
      'Cool': [createBlueTheme(), createCyanTheme(), createTealTheme(), createLightBlueTheme(), createDarkBlueTheme()],
      'Warm': [createAmberTheme(), createOrangeTheme(), createDeepOrangeTheme(), createBrownTheme(), createSunsetTheme()],
      'Purple': [createPurpleTheme(), createDeepPurpleTheme(), createIndigoTheme(), createPastelPinkTheme()],
      'Green': [createGreenTheme(), createLightGreenTheme(), createTealTheme()],
      'Gradients': [createGradientTheme(), createValentineTheme(), createGalaxyTheme(), createSunsetTheme(), createOceanTheme()],
      'Special': [createLoveHeartsTheme(), createWarningTheme(), createNeonTheme(), createGoldStarTheme()],
      'Professional': [createWhiteTheme(), createDarkTheme(), createBrownTheme(), createIndigoTheme()],
      'Fun': [createPictureTheme(), createAdaptiveTheme(), createLoveHeartsTheme(), createGoldStarTheme()],
    };
  }

  /// Parse theme from JSON with comprehensive defaults
  factory KeyboardThemeV2.fromJson(Map<String, dynamic> json) {
    try {
      return KeyboardThemeV2(
        id: json['id'] ?? 'default_theme',
        name: json['name'] ?? 'Default Theme',
        mode: json['mode'] ?? 'unified',
        background: ThemeBackground.fromJson(json['background'] ?? {}),
        keys: ThemeKeys.fromJson(json['keys'] ?? {}),
        specialKeys: ThemeSpecialKeys.fromJson(json['specialKeys'] ?? {}),
        toolbar: ThemeToolbar.fromJson(json['toolbar'] ?? {}),
        suggestions: ThemeSuggestions.fromJson(json['suggestions'] ?? {}),
        effects: ThemeEffects.fromJson(json['effects'] ?? {}),
        sounds: ThemeSounds.fromJson(json['sounds'] ?? {}),
        stickers: ThemeStickers.fromJson(json['stickers'] ?? {}),
        advanced: ThemeAdvanced.fromJson(json['advanced'] ?? {}),
      );
    } catch (e) {
      // Return default theme if parsing fails
      return createDefault();
    }
  }

  /// Convert theme to JSON
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'name': name,
      'mode': mode,
      'background': background.toJson(),
      'keys': keys.toJson(),
      'specialKeys': specialKeys.toJson(),
      'toolbar': toolbar.toJson(),
      'suggestions': suggestions.toJson(),
      'effects': effects.toJson(),
      'sounds': sounds.toJson(),
      'stickers': stickers.toJson(),
      'advanced': advanced.toJson(),
    };
  }

  KeyboardThemeV2 copyWith({
    String? id,
    String? name,
    String? mode,
    ThemeBackground? background,
    ThemeKeys? keys,
    ThemeSpecialKeys? specialKeys,
    ThemeToolbar? toolbar,
    ThemeSuggestions? suggestions,
    ThemeEffects? effects,
    ThemeSounds? sounds,
    ThemeStickers? stickers,
    ThemeAdvanced? advanced,
  }) {
    return KeyboardThemeV2(
      id: id ?? this.id,
      name: name ?? this.name,
      mode: mode ?? this.mode,
      background: background ?? this.background,
      keys: keys ?? this.keys,
      specialKeys: specialKeys ?? this.specialKeys,
      toolbar: toolbar ?? this.toolbar,
      suggestions: suggestions ?? this.suggestions,
      effects: effects ?? this.effects,
      sounds: sounds ?? this.sounds,
      stickers: stickers ?? this.stickers,
      advanced: advanced ?? this.advanced,
    );
  }
}

// Background theme data
@immutable
class ThemeBackground {
  final String type; // "solid", "image", "gradient", "adaptive"
  final Color? color;
  final String? imagePath;
  final double imageOpacity;
  final ThemeGradient? gradient;
  final List<String> overlayEffects;
  final ThemeAdaptive? adaptive;
  final double brightness;

  const ThemeBackground({
    required this.type,
    this.color,
    this.imagePath,
    required this.imageOpacity,
    this.gradient,
    required this.overlayEffects,
    this.adaptive,
    this.brightness = 1.0,
  });

  factory ThemeBackground.fromJson(Map<String, dynamic> json) {
    return ThemeBackground(
      type: json['type'] ?? 'solid',
      color: _parseColor(json['color']),
      imagePath: json['imagePath'],
      imageOpacity: (json['imageOpacity'] ?? 0.85).toDouble(),
      gradient: json['gradient'] != null ? ThemeGradient.fromJson(json['gradient']) : null,
      overlayEffects: List<String>.from(json['overlayEffects'] ?? []),
      adaptive: json['adaptive'] != null ? ThemeAdaptive.fromJson(json['adaptive']) : null,
      brightness: (json['brightness'] ?? 1.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'type': type,
      if (color != null) 'color': _colorToHex(color!),
      if (imagePath != null) 'imagePath': imagePath,
      'imageOpacity': imageOpacity,
      if (gradient != null) 'gradient': gradient!.toJson(),
      'overlayEffects': overlayEffects,
      if (adaptive != null) 'adaptive': adaptive!.toJson(),
      'brightness': brightness,
    };
  }
}

// Adaptive background configuration
@immutable
class ThemeAdaptive {
  final bool enabled;
  final String source; // "wallpaper", "system", "app"
  final bool materialYou;

  const ThemeAdaptive({
    required this.enabled,
    required this.source,
    required this.materialYou,
  });

  factory ThemeAdaptive.fromJson(Map<String, dynamic> json) {
    return ThemeAdaptive(
      enabled: json['enabled'] ?? false,
      source: json['source'] ?? 'wallpaper',
      materialYou: json['materialYou'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'source': source,
      'materialYou': materialYou,
    };
  }
}

@immutable
class ThemeGradient {
  final List<Color> colors;
  final String orientation;
  final List<double>? stops;

  const ThemeGradient({
    required this.colors,
    required this.orientation,
    this.stops,
  });

  factory ThemeGradient.fromJson(Map<String, dynamic> json) {
    final rawStops = (json['stops'] as List?)
        ?.map((value) {
          if (value is num) {
            return value.toDouble().clamp(0.0, 1.0);
          }
          return null;
        })
        .whereType<double>()
        .toList();
    return ThemeGradient(
      colors: (json['colors'] as List?)?.map((c) => _parseColor(c) ?? Colors.black).toList() ?? 
              [const Color(0xFF2B2B2B), const Color(0xFF1B1B1F)],
      orientation: json['orientation'] ?? 'TOP_BOTTOM',
      stops: rawStops != null && rawStops.isNotEmpty ? rawStops : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'colors': colors.map(_colorToHex).toList(),
      'orientation': orientation,
      if (stops != null && stops!.isNotEmpty) 'stops': stops,
    };
  }
}

// Keys theme data
@immutable
class ThemeKeys {
  final String preset;
  final Color bg;
  final Color text;
  final Color pressed;
  final double rippleAlpha;
  final ThemeKeysBorder border;
  final double radius;
  final ThemeKeysShadow shadow;
  final ThemeKeysFont font;
  final String styleId;
  final List<Color> gradient;
  final String? overlayIcon;
  final Color? overlayIconColor;
  final List<String> overlayIconTargets;

  const ThemeKeys({
    required this.preset,
    required this.bg,
    required this.text,
    required this.pressed,
    required this.rippleAlpha,
    required this.border,
    required this.radius,
    required this.shadow,
    required this.font,
    this.styleId = 'default',
    this.gradient = const [],
    this.overlayIcon,
    this.overlayIconColor,
    this.overlayIconTargets = const [],
  });

  factory ThemeKeys.fromJson(Map<String, dynamic> json) {
    return ThemeKeys(
      preset: json['preset'] ?? 'bordered',
      bg: _parseColor(json['bg']) ?? const Color(0xFF3A3A3F),
      text: _parseColor(json['text']) ?? const Color(0xFFFFFFFF),
      pressed: _parseColor(json['pressed']) ?? const Color(0xFF505056),
      rippleAlpha: (json['rippleAlpha'] ?? 0.12).toDouble(),
      border: ThemeKeysBorder.fromJson(json['border'] ?? {}),
      radius: (json['radius'] ?? 10.0).toDouble(),
      shadow: ThemeKeysShadow.fromJson(json['shadow'] ?? {}),
      font: ThemeKeysFont.fromJson(json['font'] ?? {}),
      styleId: json['styleId'] ?? 'default',
      gradient: (json['gradient'] as List<dynamic>?)
              ?.map((value) => _parseColor(value) ?? const Color(0x00000000))
              .where((color) => color.alpha > 0)
              .toList() ??
          const [],
      overlayIcon: json['overlayIcon'],
      overlayIconColor: _parseColor(json['overlayIconColor']),
      overlayIconTargets: (json['overlayIconTargets'] as List<dynamic>?)
              ?.map((value) => value.toString())
              .toList() ??
          const [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'preset': preset,
      'bg': _colorToHex(bg),
      'text': _colorToHex(text),
      'pressed': _colorToHex(pressed),
      'rippleAlpha': rippleAlpha,
      'border': border.toJson(),
      'radius': radius,
      'shadow': shadow.toJson(),
      'font': font.toJson(),
      'styleId': styleId,
      'gradient': gradient.map(_colorToHex).toList(),
      'overlayIcon': overlayIcon,
      'overlayIconColor': overlayIconColor != null ? _colorToHex(overlayIconColor!) : null,
      'overlayIconTargets': overlayIconTargets,
    };
  }
}

@immutable
class ThemeKeysBorder {
  final bool enabled;
  final Color color;
  final double widthDp;

  const ThemeKeysBorder({
    required this.enabled,
    required this.color,
    required this.widthDp,
  });

  factory ThemeKeysBorder.fromJson(Map<String, dynamic> json) {
    return ThemeKeysBorder(
      enabled: json['enabled'] ?? true,
      color: _parseColor(json['color']) ?? const Color(0xFF636366),
      widthDp: (json['widthDp'] ?? 1.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'color': _colorToHex(color),
      'widthDp': widthDp,
    };
  }
}

@immutable
class ThemeKeysShadow {
  final bool enabled;
  final double elevationDp;
  final bool glow;

  const ThemeKeysShadow({
    required this.enabled,
    required this.elevationDp,
    required this.glow,
  });

  factory ThemeKeysShadow.fromJson(Map<String, dynamic> json) {
    return ThemeKeysShadow(
      enabled: json['enabled'] ?? true,
      elevationDp: (json['elevationDp'] ?? 2.0).toDouble(),
      glow: json['glow'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'elevationDp': elevationDp,
      'glow': glow,
    };
  }
}

@immutable
class ThemeKeysFont {
  final String family;
  final double sizeSp;
  final bool bold;
  final bool italic;

  const ThemeKeysFont({
    required this.family,
    required this.sizeSp,
    required this.bold,
    required this.italic,
  });

  factory ThemeKeysFont.fromJson(Map<String, dynamic> json) {
    return ThemeKeysFont(
      family: json['family'] ?? 'Roboto',
      sizeSp: (json['sizeSp'] ?? 18.0).toDouble(),
      bold: json['bold'] ?? false,
      italic: json['italic'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'family': family,
      'sizeSp': sizeSp,
      'bold': bold,
      'italic': italic,
    };
  }
}

// Special keys theme data
@immutable
class ThemeSpecialKeys {
  final Color accent;
  final bool useAccentForEnter;
  final List<String> applyTo;
  final Color spaceLabelColor;

  const ThemeSpecialKeys({
    required this.accent,
    required this.useAccentForEnter,
    required this.applyTo,
    required this.spaceLabelColor,
  });

  factory ThemeSpecialKeys.fromJson(Map<String, dynamic> json) {
    return ThemeSpecialKeys(
      accent: _parseColor(json['accent']) ?? const Color(0xFFFF9F1A),
      useAccentForEnter: json['useAccentForEnter'] ?? true,
      applyTo: _normalizeSpecialKeyApplyList(json['applyTo']),
      spaceLabelColor: _parseColor(json['spaceLabelColor']) ?? const Color(0xFFFFFFFF),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'accent': _colorToHex(accent),
      'useAccentForEnter': useAccentForEnter,
      'applyTo': applyTo,
      'spaceLabelColor': _colorToHex(spaceLabelColor),
    };
  }
}

// Toolbar theme data
@immutable
class ThemeToolbar {
  final bool inheritFromKeys;
  final Color bg;
  final Color icon;
  final double heightDp;
  final Color activeAccent;
  final String iconPack;

  const ThemeToolbar({
    required this.inheritFromKeys,
    required this.bg,
    required this.icon,
    required this.heightDp,
    required this.activeAccent,
    required this.iconPack,
  });

  factory ThemeToolbar.fromJson(Map<String, dynamic> json) {
    return ThemeToolbar(
      inheritFromKeys: json['inheritFromKeys'] ?? true,
      bg: _parseColor(json['bg']) ?? const Color(0xFF3A3A3F),
      icon: _parseColor(json['icon']) ?? const Color(0xFFFFFFFF),
      heightDp: (json['heightDp'] ?? 44.0).toDouble(),
      activeAccent: _parseColor(json['activeAccent']) ?? const Color(0xFFFF9F1A),
      iconPack: json['iconPack'] ?? 'default',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'inheritFromKeys': inheritFromKeys,
      'bg': _colorToHex(bg),
      'icon': _colorToHex(icon),
      'heightDp': heightDp,
      'activeAccent': _colorToHex(activeAccent),
      'iconPack': iconPack,
    };
  }
}

// Suggestions theme data
@immutable
class ThemeSuggestions {
  final bool inheritFromKeys;
  final Color bg;
  final Color text;
  final ThemeChip chip;
  final ThemeSuggestionsFont font;

  const ThemeSuggestions({
    required this.inheritFromKeys,
    required this.bg,
    required this.text,
    required this.chip,
    required this.font,
  });

  factory ThemeSuggestions.fromJson(Map<String, dynamic> json) {
    return ThemeSuggestions(
      inheritFromKeys: json['inheritFromKeys'] ?? true,
      bg: _parseColor(json['bg']) ?? const Color(0xFF3A3A3F),
      text: _parseColor(json['text']) ?? const Color(0xFFFFFFFF),
      chip: ThemeChip.fromJson(json['chip'] ?? {}),
      font: ThemeSuggestionsFont.fromJson(json['font'] ?? {}),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'inheritFromKeys': inheritFromKeys,
      'bg': _colorToHex(bg),
      'text': _colorToHex(text),
      'chip': chip.toJson(),
      'font': font.toJson(),
    };
  }
}

@immutable
class ThemeChip {
  final Color bg;
  final Color text;
  final Color pressed;
  final double radius;
  final double spacingDp;

  const ThemeChip({
    required this.bg,
    required this.text,
    required this.pressed,
    required this.radius,
    required this.spacingDp,
  });

  factory ThemeChip.fromJson(Map<String, dynamic> json) {
    return ThemeChip(
      bg: _parseColor(json['bg']) ?? const Color(0xFF4A4A50),
      text: _parseColor(json['text']) ?? const Color(0xFFFFFFFF),
      pressed: _parseColor(json['pressed']) ?? const Color(0xFF5A5A60),
      radius: (json['radius'] ?? 14.0).toDouble(),
      spacingDp: (json['spacingDp'] ?? 6.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'bg': _colorToHex(bg),
      'text': _colorToHex(text),
      'pressed': _colorToHex(pressed),
      'radius': radius,
      'spacingDp': spacingDp,
    };
  }
}

@immutable
class ThemeSuggestionsFont {
  final String family;
  final double sizeSp;
  final bool bold;

  const ThemeSuggestionsFont({
    required this.family,
    required this.sizeSp,
    required this.bold,
  });

  factory ThemeSuggestionsFont.fromJson(Map<String, dynamic> json) {
    return ThemeSuggestionsFont(
      family: json['family'] ?? 'Roboto',
      sizeSp: (json['sizeSp'] ?? 15.0).toDouble(),
      bold: json['bold'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'family': family,
      'sizeSp': sizeSp,
      'bold': bold,
    };
  }
  }

// Effects theme data
@immutable
class ThemeEffects {
  final String pressAnimation; // "ripple", "bounce", "glow", "none"
  final List<String> globalEffects; // "snow", "hearts", "sparkles", "rain", "leaves"
  final double opacity;

  const ThemeEffects({
    required this.pressAnimation,
    required this.globalEffects,
    this.opacity = 1.0,
  });

  factory ThemeEffects.fromJson(Map<String, dynamic> json) {
    return ThemeEffects(
      pressAnimation: json['pressAnimation'] ?? 'none',
      globalEffects: List<String>.from(json['globalEffects'] ?? []),
      opacity: ((json['opacity'] ?? 1.0) as num).toDouble().clamp(0.0, 1.0),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'pressAnimation': pressAnimation,
      'globalEffects': globalEffects,
      'opacity': opacity,
    };
  }
}

// Sounds theme data
@immutable
class ThemeSounds {
  final String pack;
  final Map<String, String> customUris;
  final double volume;

  const ThemeSounds({
    required this.pack,
    required this.customUris,
    required this.volume,
  });

  factory ThemeSounds.fromJson(Map<String, dynamic> json) {
    return ThemeSounds(
      pack: json['pack'] ?? 'silent',
      customUris: Map<String, String>.from(json['customUris'] ?? {}),
      volume: (json['volume'] ?? 0.0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'pack': pack,
      'customUris': customUris,
      'volume': volume,
    };
  }
}

// Stickers theme data
@immutable
class ThemeStickers {
  final bool enabled;
  final String pack;
  final String position; // "above", "below", "behind"
  final double opacity;
  final bool animated;

  const ThemeStickers({
    required this.enabled,
    required this.pack,
    required this.position,
    required this.opacity,
    required this.animated,
  });

  factory ThemeStickers.fromJson(Map<String, dynamic> json) {
    return ThemeStickers(
      enabled: json['enabled'] ?? false,
      pack: json['pack'] ?? '',
      position: json['position'] ?? 'behind',
      opacity: (json['opacity'] ?? 0.9).toDouble(),
      animated: json['animated'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'enabled': enabled,
      'pack': pack,
      'position': position,
      'opacity': opacity,
      'animated': animated,
    };
  }
}

// Advanced theme data
@immutable
class ThemeAdvanced {
  final bool livePreview;
  final bool galleryEnabled;
  final bool shareEnabled;
  final String dynamicTheme; // "none", "wallpaper", "time_of_day", "seasonal"
  final String seasonalPack; // "none", "valentine", "halloween", "christmas", "spring", "summer"
  final bool materialYouExtract;

  const ThemeAdvanced({
    required this.livePreview,
    required this.galleryEnabled,
    required this.shareEnabled,
    required this.dynamicTheme,
    required this.seasonalPack,
    required this.materialYouExtract,
  });

  factory ThemeAdvanced.fromJson(Map<String, dynamic> json) {
    return ThemeAdvanced(
      livePreview: json['livePreview'] ?? true,
      galleryEnabled: json['galleryEnabled'] ?? true,
      shareEnabled: json['shareEnabled'] ?? true,
      dynamicTheme: json['dynamicTheme'] ?? 'none',
      seasonalPack: json['seasonalPack'] ?? 'none',
      materialYouExtract: json['materialYouExtract'] ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'livePreview': livePreview,
      'galleryEnabled': galleryEnabled,
      'shareEnabled': shareEnabled,
      'dynamicTheme': dynamicTheme,
      'seasonalPack': seasonalPack,
      'materialYouExtract': materialYouExtract,
    };
  }
}

/// Utility functions for color parsing
Color? _parseColor(String? colorStr) {
  if (colorStr == null || colorStr.isEmpty) return null;
  try {
    if (colorStr.startsWith('#')) {
      final hex = colorStr.substring(1);
      if (hex.length == 6) {
        return Color(int.parse('FF$hex', radix: 16));
      } else if (hex.length == 8) {
        return Color(int.parse(hex, radix: 16));
      }
    }
  } catch (e) {
    // Fallback to null
  }
  return null;
}

String _colorToHex(Color color) {
  final hex = color.value.toRadixString(16).padLeft(8, '0').toUpperCase();
  return '#$hex';
}

List<String> _normalizeSpecialKeyApplyList(dynamic raw) {
  const requiredKeys = ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'];
  final result = <String>[];

  if (raw is Iterable) {
    for (final value in raw) {
      final str = value?.toString();
      if (str != null && str.isNotEmpty && !result.contains(str)) {
        result.add(str);
      }
    }
  }

  if (result.isEmpty) {
    result.addAll(requiredKeys);
  } else {
    for (final key in requiredKeys) {
      if (!result.contains(key)) {
        result.add(key);
      }
    }
  }

  return result;
}

/// Theme management functions
class ThemeManagerV2 {
  // CRITICAL: Don't add "flutter." prefix - SharedPreferences plugin adds it automatically!
  // So 'theme.v2.json' becomes 'flutter.theme.v2.json' in native Android
  static const String _themeKey = 'theme.v2.json';
  static const String _settingsChangedKey = 'keyboard_settings.settings_changed';
  static const MethodChannel _configChannel = MethodChannel('ai_keyboard/config');
  static const MethodChannel _themeChannel = MethodChannel('keyboard.theme');
  static const MethodChannel _soundChannel = MethodChannel('keyboard.sound');
  static const MethodChannel _effectsChannel = MethodChannel('keyboard.effects');

  /// Save theme to SharedPreferences
  static Future<void> saveThemeV2(KeyboardThemeV2 theme) async {
    final prefs = await SharedPreferences.getInstance();
    final jsonMap = theme.toJson();
    final jsonStr = jsonEncode(jsonMap);
    
    // Save to SharedPreferences with immediate write
    await prefs.setString(_themeKey, jsonStr);
    await prefs.setBool(_settingsChangedKey, true);
    
    await prefs.reload();
    
    await _applyThemeViaChannel(theme);
  }

  static Future<void> _applyThemeViaChannel(KeyboardThemeV2 theme) async {
    try {
      final payload = Map<String, dynamic>.from(theme.toJson());
      payload['fontScale'] = theme.keys.font.sizeSp / 18.0;
      await _themeChannel.invokeMethod('applyTheme', payload);
    } catch (_) {
      await _sendThemeBroadcast(theme);
    }
  }
  
  /// Send broadcast to Android keyboard service
  static Future<void> _sendThemeBroadcast(KeyboardThemeV2 theme) async {
    try {
      await _configChannel.invokeMethod('themeChanged', {
        'themeId': theme.id,
        'themeName': theme.name,
        'hasThemeData': true,
      });
    } catch (_) {
      // Fallback: Try to trigger through settings broadcast
      await _triggerSettingsBroadcast();
    }
  }
  
  /// Fallback broadcast method
  static Future<void> _triggerSettingsBroadcast() async {
    try {
      await _configChannel.invokeMethod('settingsChanged');
    } catch (_) {
      // Silently fail
    }
  }

  static Future<void> notifySoundSelection(String name, {String? asset}) async {
    try {
      final payload = <String, dynamic>{'name': name};
      if (asset != null && asset.isNotEmpty) {
        payload['asset'] = asset;
      }
      await _soundChannel.invokeMethod('setSound', payload);
    } catch (_) {
      // Ignore failures; sound preview is optional.
    }
  }

  static Future<void> playSoundSample(String? asset) async {
    if (asset == null || asset.isEmpty) {
      return;
    }
    try {
      await _soundChannel.invokeMethod('playSample', {'asset': asset});
    } catch (_) {
      // Ignore failures; sound preview is optional.
    }
  }

  static Future<void> applyEffectPreview(String type, double opacity) async {
    try {
      await _effectsChannel.invokeMethod('applyEffect', {
        'type': type,
        'opacity': opacity,
      });
    } catch (_) {
      // Ignore failures; preview is optional.
    }
  }

  /// Load theme from SharedPreferences
  static Future<KeyboardThemeV2> loadThemeV2() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = prefs.getString(_themeKey);
    
    if (jsonStr != null) {
      try {
        final json = jsonDecode(jsonStr) as Map<String, dynamic>;
        return KeyboardThemeV2.fromJson(json);
      } catch (e) {
        // Return default theme if parsing fails
        return KeyboardThemeV2.createDefault();
      }
    }
    
    return KeyboardThemeV2.createDefault();
  }

  /// Export theme as JSON string (for sharing)
  static String exportTheme(KeyboardThemeV2 theme) {
    return jsonEncode(theme.toJson());
  }

  /// Import theme from JSON string
  static KeyboardThemeV2? importTheme(String jsonStr) {
    try {
      final json = jsonDecode(jsonStr) as Map<String, dynamic>;
      return KeyboardThemeV2.fromJson(json);
    } catch (e) {
      return null; // Invalid JSON
    }
  }

  /// Create light theme variant
  static KeyboardThemeV2 createLightTheme() {
    return KeyboardThemeV2.createDefault().copyWith(
      name: 'Default Light',
      background: const ThemeBackground(
        type: 'solid',
        color: Color(0xFFF8F9FA),
        imagePath: null,
        imageOpacity: 0.85,
        gradient: null,
        overlayEffects: [],
      ),
      keys: const ThemeKeys(
        preset: 'bordered',
        bg: Color(0xFFFFFFFF),
        text: Color(0xFF3C4043),
        pressed: Color(0xFFE8F0FE),
        rippleAlpha: 0.12,
        border: ThemeKeysBorder(
          enabled: true,
          color: Color(0xFFDADCE0),
          widthDp: 1.0,
        ),
        radius: 10.0,
        shadow: ThemeKeysShadow(
          enabled: true,
          elevationDp: 2.0,
          glow: false,
        ),
        font: ThemeKeysFont(
          family: 'Roboto',
          sizeSp: 18.0,
          bold: false,
          italic: false,
        ),
      ),
      specialKeys: const ThemeSpecialKeys(
        accent: Color(0xFF1A73E8),
        useAccentForEnter: true,
        applyTo: ['enter', 'globe', 'emoji', 'mic', 'symbols', 'backspace'],
        spaceLabelColor: Color(0xFF3C4043),
      ),
    );
  }

  /// Validate theme against schema (basic validation)
  static bool validateTheme(KeyboardThemeV2 theme) {
    // Basic validation - check required fields exist
    return theme.id.isNotEmpty &&
           theme.name.isNotEmpty &&
           (theme.mode == 'unified' || theme.mode == 'split');
  }
}

/// Extension methods for copyWith functionality
extension ThemeBackgroundCopyWith on ThemeBackground {
  ThemeBackground copyWith({
    String? type,
    Color? color,
    String? imagePath,
    double? imageOpacity,
    ThemeGradient? gradient,
    List<String>? overlayEffects,
    ThemeAdaptive? adaptive,
    double? brightness,
  }) {
    return ThemeBackground(
      type: type ?? this.type,
      color: color ?? this.color,
      imagePath: imagePath ?? this.imagePath,
      imageOpacity: imageOpacity ?? this.imageOpacity,
      gradient: gradient ?? this.gradient,
      overlayEffects: overlayEffects ?? this.overlayEffects,
      adaptive: adaptive ?? this.adaptive,
      brightness: brightness ?? this.brightness,
    );
  }
}

extension ThemeAdaptiveCopyWith on ThemeAdaptive {
  ThemeAdaptive copyWith({
    bool? enabled,
    String? source,
    bool? materialYou,
  }) {
    return ThemeAdaptive(
      enabled: enabled ?? this.enabled,
      source: source ?? this.source,
      materialYou: materialYou ?? this.materialYou,
    );
  }
}

extension ThemeKeysCopyWith on ThemeKeys {
  ThemeKeys copyWith({
    String? preset,
    Color? bg,
    Color? text,
    Color? pressed,
    double? rippleAlpha,
    ThemeKeysBorder? border,
    double? radius,
    ThemeKeysShadow? shadow,
    ThemeKeysFont? font,
    String? styleId,
    List<Color>? gradient,
    Object? overlayIcon = _noChange,
    Object? overlayIconColor = _noChange,
    List<String>? overlayIconTargets,
  }) {
    return ThemeKeys(
      preset: preset ?? this.preset,
      bg: bg ?? this.bg,
      text: text ?? this.text,
      pressed: pressed ?? this.pressed,
      rippleAlpha: rippleAlpha ?? this.rippleAlpha,
      border: border ?? this.border,
      radius: radius ?? this.radius,
      shadow: shadow ?? this.shadow,
      font: font ?? this.font,
      styleId: styleId ?? this.styleId,
      gradient: gradient != null ? List<Color>.from(gradient) : this.gradient,
      overlayIcon: identical(overlayIcon, _noChange) ? this.overlayIcon : overlayIcon as String?,
      overlayIconColor: identical(overlayIconColor, _noChange)
          ? this.overlayIconColor
          : overlayIconColor as Color?,
      overlayIconTargets: overlayIconTargets != null
          ? List<String>.from(overlayIconTargets)
          : this.overlayIconTargets,
    );
  }
}

extension ThemeKeysFontCopyWith on ThemeKeysFont {
  ThemeKeysFont copyWith({
    String? family,
    double? sizeSp,
    bool? bold,
    bool? italic,
  }) {
    return ThemeKeysFont(
      family: family ?? this.family,
      sizeSp: sizeSp ?? this.sizeSp,
      bold: bold ?? this.bold,
      italic: italic ?? this.italic,
    );
  }
}

extension ThemeSpecialKeysCopyWith on ThemeSpecialKeys {
  ThemeSpecialKeys copyWith({
    Color? accent,
    bool? useAccentForEnter,
    List<String>? applyTo,
    Color? spaceLabelColor,
  }) {
    return ThemeSpecialKeys(
      accent: accent ?? this.accent,
      useAccentForEnter: useAccentForEnter ?? this.useAccentForEnter,
      applyTo: _normalizeSpecialKeyApplyList(applyTo ?? this.applyTo),
      spaceLabelColor: spaceLabelColor ?? this.spaceLabelColor,
    );
  }
}
