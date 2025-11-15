import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

/// Comprehensive theme data for the AI Keyboard
/// Supports Gboard baseline features + CleverType advanced customizations
class KeyboardThemeData {
  // Basic Theme Properties
  final String id;
  final String name;
  final String description;
  
  // Background Properties
  final Color backgroundColor;
  final Color keyBackgroundColor;
  final Color keyPressedColor;
  final Color keyDisabledColor;
  
  // Text Properties
  final Color keyTextColor;
  final Color keyPressedTextColor;
  final double fontSize;
  final String fontFamily;
  final bool isBold;
  final bool isItalic;
  
  // Accent & Special Keys
  final Color accentColor;
  final Color specialKeyColor; // Enter, Shift, Space
  final Color deleteKeyColor;
  
  // Suggestion Bar
  final Color suggestionBarColor;
  final Color suggestionTextColor;
  final Color suggestionHighlightColor;
  final double suggestionFontSize;
  final bool suggestionBold;
  final bool suggestionItalic;
  
  // Key Appearance
  final double keyCornerRadius;
  final bool showKeyShadows;
  final double shadowDepth;
  final Color shadowColor;
  final double keyBorderWidth;
  final Color keyBorderColor;
  
  // Key Sizing & Spacing
  final double keyHeight;
  final double keyWidth;
  final double keySpacing;
  final double rowSpacing;
  
  // Advanced Background Options
  final String backgroundType; // 'solid', 'gradient', 'image'
  final List<Color> gradientColors;
  final double gradientAngle;
  final String? backgroundImagePath;
  final double backgroundOpacity;
  final String imageScaleType; // 'cover', 'contain', 'fill'
  
  // Material You Integration
  final bool useMaterialYou;
  final bool followSystemTheme;
  
  // Swipe Typing
  final Color swipeTrailColor;
  final double swipeTrailWidth;
  final double swipeTrailOpacity;
  
  // Performance Optimizations
  final bool enableAnimations;
  final int animationDuration;

  const KeyboardThemeData({
    required this.id,
    required this.name,
    this.description = '',
    
    // Background
    this.backgroundColor = const Color(0xFFF5F5F5),
    this.keyBackgroundColor = const Color(0xFFFFFFFF),
    this.keyPressedColor = const Color(0xFFE3F2FD),
    this.keyDisabledColor = const Color(0xFFEEEEEE),
    
    // Text
    this.keyTextColor = const Color(0xFF212121),
    this.keyPressedTextColor = const Color(0xFF1976D2),
    this.fontSize = 18.0,
    this.fontFamily = 'Roboto',
    this.isBold = false,
    this.isItalic = false,
    
    // Accents
    this.accentColor = const Color(0xFF2196F3),
    this.specialKeyColor = const Color(0xFFE0E0E0),
    this.deleteKeyColor = const Color(0xFFF44336),
    
    // Suggestions
    this.suggestionBarColor = const Color(0xFFFAFAFA),
    this.suggestionTextColor = const Color(0xFF424242),
    this.suggestionHighlightColor = const Color(0xFF2196F3),
    this.suggestionFontSize = 16.0,
    this.suggestionBold = false,
    this.suggestionItalic = false,
    
    // Key Appearance
    this.keyCornerRadius = 6.0,
    this.showKeyShadows = true,
    this.shadowDepth = 2.0,
    this.shadowColor = const Color(0x1A000000),
    this.keyBorderWidth = 0.5,
    this.keyBorderColor = const Color(0xFFE0E0E0),
    
    // Sizing
    this.keyHeight = 48.0,
    this.keyWidth = 32.0,
    this.keySpacing = 4.0,
    this.rowSpacing = 8.0,
    
    // Advanced Background
    this.backgroundType = 'solid',
    this.gradientColors = const [Color(0xFFF5F5F5), Color(0xFFE0E0E0)],
    this.gradientAngle = 45.0,
    this.backgroundImagePath,
    this.backgroundOpacity = 1.0,
    this.imageScaleType = 'cover',
    
    // Material You
    this.useMaterialYou = false,
    this.followSystemTheme = false,
    
    // Swipe Typing
    this.swipeTrailColor = const Color(0xFF2196F3),
    this.swipeTrailWidth = 8.0,
    this.swipeTrailOpacity = 0.7,
    
    // Performance
    this.enableAnimations = true,
    this.animationDuration = 150,
  });

  /// Convert to JSON for storage
  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'description': description,
    'backgroundColor': backgroundColor.value,
    'keyBackgroundColor': keyBackgroundColor.value,
    'keyPressedColor': keyPressedColor.value,
    'keyDisabledColor': keyDisabledColor.value,
    'keyTextColor': keyTextColor.value,
    'keyPressedTextColor': keyPressedTextColor.value,
    'fontSize': fontSize,
    'fontFamily': fontFamily,
    'isBold': isBold,
    'isItalic': isItalic,
    'accentColor': accentColor.value,
    'specialKeyColor': specialKeyColor.value,
    'deleteKeyColor': deleteKeyColor.value,
    'suggestionBarColor': suggestionBarColor.value,
    'suggestionTextColor': suggestionTextColor.value,
    'suggestionHighlightColor': suggestionHighlightColor.value,
    'suggestionFontSize': suggestionFontSize,
    'suggestionBold': suggestionBold,
    'suggestionItalic': suggestionItalic,
    'keyCornerRadius': keyCornerRadius,
    'showKeyShadows': showKeyShadows,
    'shadowDepth': shadowDepth,
    'shadowColor': shadowColor.value,
    'keyBorderWidth': keyBorderWidth,
    'keyBorderColor': keyBorderColor.value,
    'keyHeight': keyHeight,
    'keyWidth': keyWidth,
    'keySpacing': keySpacing,
    'rowSpacing': rowSpacing,
    'backgroundType': backgroundType,
    'gradientColors': gradientColors.map((c) => c.value).toList(),
    'gradientAngle': gradientAngle,
    'backgroundImagePath': backgroundImagePath,
    'backgroundOpacity': backgroundOpacity,
    'imageScaleType': imageScaleType,
    'useMaterialYou': useMaterialYou,
    'followSystemTheme': followSystemTheme,
    'swipeTrailColor': swipeTrailColor.value,
    'swipeTrailWidth': swipeTrailWidth,
    'swipeTrailOpacity': swipeTrailOpacity,
    'enableAnimations': enableAnimations,
    'animationDuration': animationDuration,
  };

  /// Create from JSON
  static KeyboardThemeData fromJson(Map<String, dynamic> json) => KeyboardThemeData(
    id: json['id'] ?? '',
    name: json['name'] ?? 'Custom Theme',
    description: json['description'] ?? '',
    backgroundColor: Color(json['backgroundColor'] ?? 0xFFF5F5F5),
    keyBackgroundColor: Color(json['keyBackgroundColor'] ?? 0xFFFFFFFF),
    keyPressedColor: Color(json['keyPressedColor'] ?? 0xFFE3F2FD),
    keyDisabledColor: Color(json['keyDisabledColor'] ?? 0xFFEEEEEE),
    keyTextColor: Color(json['keyTextColor'] ?? 0xFF212121),
    keyPressedTextColor: Color(json['keyPressedTextColor'] ?? 0xFF1976D2),
    fontSize: (json['fontSize'] ?? 18.0).toDouble(),
    fontFamily: json['fontFamily'] ?? 'Roboto',
    isBold: json['isBold'] ?? false,
    isItalic: json['isItalic'] ?? false,
    accentColor: Color(json['accentColor'] ?? 0xFF2196F3),
    specialKeyColor: Color(json['specialKeyColor'] ?? 0xFFE0E0E0),
    deleteKeyColor: Color(json['deleteKeyColor'] ?? 0xFFF44336),
    suggestionBarColor: Color(json['suggestionBarColor'] ?? 0xFFFAFAFA),
    suggestionTextColor: Color(json['suggestionTextColor'] ?? 0xFF424242),
    suggestionHighlightColor: Color(json['suggestionHighlightColor'] ?? 0xFF2196F3),
    suggestionFontSize: (json['suggestionFontSize'] ?? 16.0).toDouble(),
    suggestionBold: json['suggestionBold'] ?? false,
    suggestionItalic: json['suggestionItalic'] ?? false,
    keyCornerRadius: (json['keyCornerRadius'] ?? 6.0).toDouble(),
    showKeyShadows: json['showKeyShadows'] ?? true,
    shadowDepth: (json['shadowDepth'] ?? 2.0).toDouble(),
    shadowColor: Color(json['shadowColor'] ?? 0x1A000000),
    keyBorderWidth: (json['keyBorderWidth'] ?? 0.5).toDouble(),
    keyBorderColor: Color(json['keyBorderColor'] ?? 0xFFE0E0E0),
    keyHeight: (json['keyHeight'] ?? 48.0).toDouble(),
    keyWidth: (json['keyWidth'] ?? 32.0).toDouble(),
    keySpacing: (json['keySpacing'] ?? 4.0).toDouble(),
    rowSpacing: (json['rowSpacing'] ?? 8.0).toDouble(),
    backgroundType: json['backgroundType'] ?? 'solid',
    gradientColors: (json['gradientColors'] as List<dynamic>?)
        ?.map((c) => Color(c as int))
        .toList() ?? [const Color(0xFFF5F5F5), const Color(0xFFE0E0E0)],
    gradientAngle: (json['gradientAngle'] ?? 45.0).toDouble(),
    backgroundImagePath: json['backgroundImagePath'],
    backgroundOpacity: (json['backgroundOpacity'] ?? 1.0).toDouble(),
    imageScaleType: json['imageScaleType'] ?? 'cover',
    useMaterialYou: json['useMaterialYou'] ?? false,
    followSystemTheme: json['followSystemTheme'] ?? false,
    swipeTrailColor: Color(json['swipeTrailColor'] ?? json['accentColor'] ?? 0xFF2196F3),
    swipeTrailWidth: (json['swipeTrailWidth'] ?? 8.0).toDouble(),
    swipeTrailOpacity: (json['swipeTrailOpacity'] ?? 0.7).toDouble(),
    enableAnimations: json['enableAnimations'] ?? true,
    animationDuration: json['animationDuration'] ?? 150,
  );

  /// Create a copy with modified properties
  KeyboardThemeData copyWith({
    String? id,
    String? name,
    String? description,
    Color? backgroundColor,
    Color? keyBackgroundColor,
    Color? keyPressedColor,
    Color? keyDisabledColor,
    Color? keyTextColor,
    Color? keyPressedTextColor,
    double? fontSize,
    String? fontFamily,
    bool? isBold,
    bool? isItalic,
    Color? accentColor,
    Color? specialKeyColor,
    Color? deleteKeyColor,
    Color? suggestionBarColor,
    Color? suggestionTextColor,
    Color? suggestionHighlightColor,
    double? suggestionFontSize,
    bool? suggestionBold,
    bool? suggestionItalic,
    double? keyCornerRadius,
    bool? showKeyShadows,
    double? shadowDepth,
    Color? shadowColor,
    double? keyBorderWidth,
    Color? keyBorderColor,
    double? keyHeight,
    double? keyWidth,
    double? keySpacing,
    double? rowSpacing,
    String? backgroundType,
    List<Color>? gradientColors,
    double? gradientAngle,
    String? backgroundImagePath,
    double? backgroundOpacity,
    String? imageScaleType,
    bool? useMaterialYou,
    bool? followSystemTheme,
    Color? swipeTrailColor,
    double? swipeTrailWidth,
    double? swipeTrailOpacity,
    bool? enableAnimations,
    int? animationDuration,
  }) => KeyboardThemeData(
    id: id ?? this.id,
    name: name ?? this.name,
    description: description ?? this.description,
    backgroundColor: backgroundColor ?? this.backgroundColor,
    keyBackgroundColor: keyBackgroundColor ?? this.keyBackgroundColor,
    keyPressedColor: keyPressedColor ?? this.keyPressedColor,
    keyDisabledColor: keyDisabledColor ?? this.keyDisabledColor,
    keyTextColor: keyTextColor ?? this.keyTextColor,
    keyPressedTextColor: keyPressedTextColor ?? this.keyPressedTextColor,
    fontSize: fontSize ?? this.fontSize,
    fontFamily: fontFamily ?? this.fontFamily,
    isBold: isBold ?? this.isBold,
    isItalic: isItalic ?? this.isItalic,
    accentColor: accentColor ?? this.accentColor,
    specialKeyColor: specialKeyColor ?? this.specialKeyColor,
    deleteKeyColor: deleteKeyColor ?? this.deleteKeyColor,
    suggestionBarColor: suggestionBarColor ?? this.suggestionBarColor,
    suggestionTextColor: suggestionTextColor ?? this.suggestionTextColor,
    suggestionHighlightColor: suggestionHighlightColor ?? this.suggestionHighlightColor,
    suggestionFontSize: suggestionFontSize ?? this.suggestionFontSize,
    suggestionBold: suggestionBold ?? this.suggestionBold,
    suggestionItalic: suggestionItalic ?? this.suggestionItalic,
    keyCornerRadius: keyCornerRadius ?? this.keyCornerRadius,
    showKeyShadows: showKeyShadows ?? this.showKeyShadows,
    shadowDepth: shadowDepth ?? this.shadowDepth,
    shadowColor: shadowColor ?? this.shadowColor,
    keyBorderWidth: keyBorderWidth ?? this.keyBorderWidth,
    keyBorderColor: keyBorderColor ?? this.keyBorderColor,
    keyHeight: keyHeight ?? this.keyHeight,
    keyWidth: keyWidth ?? this.keyWidth,
    keySpacing: keySpacing ?? this.keySpacing,
    rowSpacing: rowSpacing ?? this.rowSpacing,
    backgroundType: backgroundType ?? this.backgroundType,
    gradientColors: gradientColors ?? this.gradientColors,
    gradientAngle: gradientAngle ?? this.gradientAngle,
    backgroundImagePath: backgroundImagePath ?? this.backgroundImagePath,
    backgroundOpacity: backgroundOpacity ?? this.backgroundOpacity,
    imageScaleType: imageScaleType ?? this.imageScaleType,
    useMaterialYou: useMaterialYou ?? this.useMaterialYou,
    followSystemTheme: followSystemTheme ?? this.followSystemTheme,
    swipeTrailColor: swipeTrailColor ?? this.swipeTrailColor,
    swipeTrailWidth: swipeTrailWidth ?? this.swipeTrailWidth,
    swipeTrailOpacity: swipeTrailOpacity ?? this.swipeTrailOpacity,
    enableAnimations: enableAnimations ?? this.enableAnimations,
    animationDuration: animationDuration ?? this.animationDuration,
  );
}

/// Comprehensive Flutter Theme Manager
/// Handles Gboard baseline features + CleverType advanced customizations
class FlutterThemeManager extends ChangeNotifier {
  static final FlutterThemeManager _instance = FlutterThemeManager._internal();
  static FlutterThemeManager get instance => _instance;
  FlutterThemeManager._internal();

  // Theme communication via SharedPreferences only (MethodChannel removed for compatibility)

  // Current Theme State
  KeyboardThemeData _currentTheme = _defaultTheme;
  KeyboardThemeData get currentTheme => _currentTheme;

  // Available Themes
  final Map<String, KeyboardThemeData> _availableThemes = {};
  List<KeyboardThemeData> get availableThemes => _availableThemes.values.toList();

  // Cache for background images
  final Map<String, Uint8List> _imageCache = {};

  // Built-in Gboard baseline themes
  static const KeyboardThemeData _gboardLight = KeyboardThemeData(
    id: 'gboard_light',
    name: 'Gboard Light',
    description: 'Clean, minimal design inspired by Google Keyboard',
    backgroundColor: Color(0xFFF5F5F5),
    keyBackgroundColor: Color(0xFFFFFFFF),
    keyPressedColor: Color(0xFFE3F2FD),
    keyTextColor: Color(0xFF212121),
    accentColor: Color(0xFF1A73E8),
    suggestionBarColor: Color(0xFFFAFAFA),
    keyCornerRadius: 6.0,
    showKeyShadows: true,
    swipeTrailColor: Color(0xFF1A73E8), // Match accent
  );

  static const KeyboardThemeData _gboardDark = KeyboardThemeData(
    id: 'gboard_dark',
    name: 'Gboard Dark',
    description: 'Dark theme for low-light environments',
    backgroundColor: Color(0xFF1E1E1E),
    keyBackgroundColor: Color(0xFF2D2D2D),
    keyPressedColor: Color(0xFF3C3C3C),
    keyTextColor: Color(0xFFFFFFFF),
    accentColor: Color(0xFF8AB4F8),
    suggestionBarColor: Color(0xFF2D2D2D),
    suggestionTextColor: Color(0xFFFFFFFF),
    shadowColor: Color(0x40000000),
  );

  static const KeyboardThemeData _defaultTheme = _gboardDark;

  /// Initialize theme manager
  Future<void> initialize() async {
    await _loadBuiltInThemes();
    await _loadCustomThemes();
    await _loadCurrentTheme();
  }

  /// Load built-in themes (Gboard baseline)
  Future<void> _loadBuiltInThemes() async {
    // Gboard Light
    _availableThemes['gboard_light'] = _gboardLight;

    // Gboard Dark (Default)
    _availableThemes['gboard_dark'] = _gboardDark;

    // Material You Theme (Dynamic Colors)
    _availableThemes['material_you'] = const KeyboardThemeData(
      id: 'material_you',
      name: 'Material You',
      description: 'Adaptive colors from your wallpaper',
      backgroundColor: Color(0xFF6750A4),
      keyBackgroundColor: Color(0xFF7C4DFF),
      keyTextColor: Color(0xFFFFFFFF),
      accentColor: Color(0xFFBB86FC),
      swipeTrailColor: Color(0xFFBB86FC), // Match accent
      useMaterialYou: true,
      followSystemTheme: true,
    );

    // High Contrast Theme
    _availableThemes['high_contrast'] = const KeyboardThemeData(
      id: 'high_contrast',
      name: 'High Contrast',
      description: 'Maximum readability and accessibility',
      backgroundColor: Color(0xFF000000),
      keyBackgroundColor: Color(0xFFFFFFFF),
      keyPressedColor: Color(0xFFFFFF00),
      keyTextColor: Color(0xFF000000),
      accentColor: Color(0xFF0000FF),
      keyBorderWidth: 2.0,
      keyBorderColor: Color(0xFF000000),
      fontSize: 20.0,
      isBold: true,
    );

    // Professional Blue
    _availableThemes['professional'] = const KeyboardThemeData(
      id: 'professional',
      name: 'Professional',
      description: 'Elegant blue theme for business use',
      backgroundColor: Color(0xFF37474F),
      keyBackgroundColor: Color(0xFF455A64),
      keyTextColor: Color(0xFFFFFFFF),
      accentColor: Color(0xFF26A69A),
      showKeyShadows: false,
    );

    // Gradient Theme
    _availableThemes['gradient_sunset'] = const KeyboardThemeData(
      id: 'gradient_sunset',
      name: 'Sunset Gradient',
      description: 'Beautiful gradient from orange to purple',
      backgroundType: 'gradient',
      gradientColors: [Color(0xFFFF6B35), Color(0xFF6750A4)],
      gradientAngle: 135.0,
      keyBackgroundColor: Color(0x80FFFFFF),
      keyTextColor: Color(0xFFFFFFFF),
      accentColor: Color(0xFFFFD54F),
    );
  }

  /// Load custom user themes
  Future<void> _loadCustomThemes() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final customThemesJson = prefs.getStringList('custom_themes') ?? [];
      
      for (final themeJson in customThemesJson) {
        final themeData = KeyboardThemeData.fromJson(jsonDecode(themeJson));
        _availableThemes[themeData.id] = themeData;
      }
    } catch (e) {
      debugPrint('Error loading custom themes: $e');
    }
  }

  /// Load current theme from preferences
  Future<void> _loadCurrentTheme() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final currentThemeId = prefs.getString('current_theme_id') ?? 'gboard_light';
      
      if (_availableThemes.containsKey(currentThemeId)) {
        _currentTheme = _availableThemes[currentThemeId]!;
      } else {
        _currentTheme = _defaultTheme;
      }
      
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading current theme: $e');
      _currentTheme = _defaultTheme;
    }
  }

  /// Apply theme and notify Android keyboard
  Future<void> applyTheme(KeyboardThemeData theme) async {
    _currentTheme = theme;
    
    // Save to preferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('current_theme_id', theme.id);
    await prefs.setString('current_theme_data', jsonEncode(theme.toJson()));
    
    // Ensure data is persisted to disk
    await prefs.reload();
    
    // Notify Android keyboard service immediately via MethodChannel
    try {
      await _notifyAndroidKeyboardThemeChange();
    } catch (e) {
      debugPrint('Failed to notify Android keyboard of theme change: $e');
    }
    
    notifyListeners();
  }

  /// Notify Android keyboard service of theme change with retry logic
  Future<void> _notifyAndroidKeyboardThemeChange() async {
    const maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        const platform = MethodChannel('ai_keyboard/config');
        
        // Send unified theme update with specific color values for panels
        await platform.invokeMethod('updateTheme', {
          'keyboard_theme_bg': '#${_currentTheme.backgroundColor.value.toRadixString(16).padLeft(8, '0')}',
          'keyboard_key_color': '#${_currentTheme.keyBackgroundColor.value.toRadixString(16).padLeft(8, '0')}',
        });
        
        // Also send general config change notification for other components
        await platform.invokeMethod('notifyConfigChange');
        
        debugPrint('✓ Successfully notified Android of theme change with unified colors (attempt $attempt)');
        return; // Success, exit retry loop
      } catch (e) {
        debugPrint('⚠ Failed to notify theme change (attempt $attempt): $e');
        if (attempt < maxRetries) {
          // Wait before retry with exponential backoff
          await Future.delayed(Duration(milliseconds: 100 * attempt));
        }
      }
    }
  }
  
  /// Test connection to Android keyboard service
  Future<bool> testAndroidConnection() async {
    try {
      const platform = MethodChannel('ai_keyboard/config');
      await platform.invokeMethod('notifyConfigChange');
      debugPrint('✓ Android connection test successful');
      return true;
    } catch (e) {
      debugPrint('⚠ Android connection test failed: $e');
      return false;
    }
  }

  /// Save custom theme
  Future<void> saveCustomTheme(KeyboardThemeData theme) async {
    _availableThemes[theme.id] = theme;
    
    try {
      final prefs = await SharedPreferences.getInstance();
      final customThemes = _availableThemes.values
          .where((t) => !_isBuiltInTheme(t.id))
          .map((t) => jsonEncode(t.toJson()))
          .toList();
      
      await prefs.setStringList('custom_themes', customThemes);
    } catch (e) {
      debugPrint('Error saving custom theme: $e');
    }
  }

  /// Delete custom theme
  Future<void> deleteCustomTheme(String themeId) async {
    if (_isBuiltInTheme(themeId)) return;
    
    _availableThemes.remove(themeId);
    
    if (_currentTheme.id == themeId) {
      await applyTheme(_defaultTheme);
    }
    
    try {
      final prefs = await SharedPreferences.getInstance();
      final customThemes = _availableThemes.values
          .where((t) => !_isBuiltInTheme(t.id))
          .map((t) => jsonEncode(t.toJson()))
          .toList();
      
      await prefs.setStringList('custom_themes', customThemes);
    } catch (e) {
      debugPrint('Error deleting custom theme: $e');
    }
    
    notifyListeners();
  }

  /// Export theme to JSON string
  String exportTheme(KeyboardThemeData theme) {
    return jsonEncode(theme.toJson());
  }

  /// Import theme from JSON string
  Future<KeyboardThemeData?> importTheme(String themeJson) async {
    try {
      final themeData = KeyboardThemeData.fromJson(jsonDecode(themeJson));
      await saveCustomTheme(themeData);
      return themeData;
    } catch (e) {
      debugPrint('Error importing theme: $e');
      return null;
    }
  }

  /// Apply Material You colors (requires Android 12+)
  /// Note: Simplified implementation without MethodChannel
  Future<void> applyMaterialYouColors() async {
    // For now, apply a Material You-style theme with predefined colors
    // Full dynamic color extraction would require platform channels
    final materialYouTheme = _availableThemes['material_you'];
    if (materialYouTheme != null) {
      await applyTheme(materialYouTheme);
    }
  }

  /// Cache background image
  Future<void> cacheBackgroundImage(String imagePath, Uint8List imageData) async {
    _imageCache[imagePath] = imageData;
  }

  /// Get cached background image
  Uint8List? getCachedImage(String imagePath) {
    return _imageCache[imagePath];
  }

  bool _isBuiltInTheme(String themeId) {
    return [
      'gboard_light',
      'gboard_dark', 
      'material_you',
      'high_contrast',
      'professional',
      'gradient_sunset'
    ].contains(themeId);
  }

  /// Get theme by ID
  KeyboardThemeData? getTheme(String themeId) {
    return _availableThemes[themeId];
  }

  /// Create theme from current system theme
  Future<KeyboardThemeData> createSystemTheme() async {
    // This would integrate with system theme detection
    final brightness = WidgetsBinding.instance.platformDispatcher.platformBrightness;
    
    if (brightness == Brightness.dark) {
      return _availableThemes['gboard_dark']!;
    } else {
      return _availableThemes['gboard_light']!;
    }
  }
}
