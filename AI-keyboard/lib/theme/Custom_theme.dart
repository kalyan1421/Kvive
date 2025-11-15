
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'dart:io';
import 'theme_v2.dart';
import 'package:ai_keyboard/screens/main screens/mainscreen.dart';
import 'package:ai_keyboard/screens/main screens/choose_base_theme_screen.dart';
import 'package:ai_keyboard/screens/main screens/button_style_selector_screen.dart'
    as button_styles;
import 'package:ai_keyboard/screens/main screens/effect_style_selector_screen.dart'
    as effect_styles;
import 'package:ai_keyboard/screens/main screens/font_style_selector_screen.dart'
    as font_styles;
import 'package:ai_keyboard/screens/main screens/sound_style_selector_screen.dart'
    as sound_styles;
import 'package:ai_keyboard/widgets/font_picker.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/theme/Custom_theme.dart';
import 'package:ai_keyboard/widgets/keyboard_snapshot.dart';
import 'package:google_fonts/google_fonts.dart';

/// CleverType Theme Editor V2
/// Complete theme editing experience with live preview and all V2 features
class ThemeEditorScreenV2 extends StatefulWidget {
  final KeyboardThemeV2? initialTheme;
  final bool isCreatingNew;

  const ThemeEditorScreenV2({
    super.key,
    this.initialTheme,
    this.isCreatingNew = false,
  });

  @override
  State<ThemeEditorScreenV2> createState() => _ThemeEditorScreenV2State();
}

class _ThemeEditorScreenV2State extends State<ThemeEditorScreenV2>
    with TickerProviderStateMixin {
  late KeyboardThemeV2 _currentTheme;
  final _themeNotifier = ValueNotifier<KeyboardThemeV2?>(null);
  final _nameController = TextEditingController();
  final FocusNode _keyboardFocusNode = FocusNode();
  late final List<_EditorTab> _tabs;
  OverlayEntry? _previewOverlayEntry;
  KeyboardThemeV2? _overlayTheme;
  static const List<_EffectOption> _effectOptions = [
    _EffectOption(
      id: 'none',
      label: 'None',
      icon: Icons.close,
      gradientColors: [
        Color(0xFFE6EBF1),
        Color(0xFFD5DAE2),
      ],
      iconColor: Color(0xFF5F6A7A),
    ),
    _EffectOption(
      id: 'sparkles',
      label: 'Sparkle',
      icon: Icons.auto_awesome,
      gradientColors: [
        Color(0xFFFFF6C3),
        Color(0xFFFFD452),
      ],
      assetIcon: AppIcons.sparkle_icon,
    ),
    _EffectOption(
      id: 'stars',
      label: 'Starfall',
      icon: Icons.star,
      gradientColors: [
        Color(0xFF89F7FE),
        Color(0xFF66A6FF),
      ],
      assetIcon: AppIcons.diamond_image,
    ),
    _EffectOption(
      id: 'hearts',
      label: 'Hearts',
      icon: Icons.favorite,
      gradientColors: [
        Color(0xFFFF7EB3),
        Color(0xFFFF3D68),
      ],
    ),
    _EffectOption(
      id: 'bubbles',
      label: 'Bubbles',
      icon: Icons.bubble_chart,
      gradientColors: [
        Color(0xFF56E4FF),
        Color(0xFF5B9DF9),
      ],
      assetIcon: AppIcons.image_icon,
    ),
    _EffectOption(
      id: 'leaves',
      label: 'Leaves',
      icon: Icons.eco,
      gradientColors: [
        Color(0xFF7BC74D),
        Color(0xFF28A745),
      ],
    ),
    _EffectOption(
      id: 'snow',
      label: 'Snowfall',
      icon: Icons.ac_unit,
      gradientColors: [
        Color(0xFF8EC5FC),
        Color(0xFFE0C3FC),
      ],
    ),
    _EffectOption(
      id: 'lightning',
      label: 'Bolt',
      icon: Icons.bolt,
      gradientColors: [
        Color(0xFFFFF000),
        Color(0xFFFFA500),
      ],
      assetIcon: AppIcons.sound_vibration_icon,
    ),
    _EffectOption(
      id: 'confetti',
      label: 'Confetti',
      icon: Icons.celebration,
      gradientColors: [
        Color(0xFFFF6CAB),
        Color(0xFF7366FF),
      ],
      assetIcon: AppIcons.crown_color,
    ),
    _EffectOption(
      id: 'butterflies',
      label: 'Butterfly',
      icon: Icons.flutter_dash,
      gradientColors: [
        Color(0xFFFFB6C1),
        Color(0xFFF8BBD0),
      ],
    ),
    _EffectOption(
      id: 'rainbow',
      label: 'Rainbow',
      icon: Icons.wb_sunny,
      gradientColors: [
        Color(0xFFFF9A9E),
        Color(0xFFFAD0C4),
      ],
    ),
  ];

  static final List<_FontOption> _fontOptions = [
    _FontOption(
      id: 'font_default',
      displayName: 'Default',
      previewText: 'F',
      themeFamily: 'Roboto',
      previewStyleBuilder: (selected) => GoogleFonts.roboto(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_clean',
      displayName: 'Clean',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.nunito(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_serif',
      displayName: 'Serif',
      previewText: 'Aa',
      themeFamily: 'Serif',
      previewStyleBuilder: (selected) => GoogleFonts.playfairDisplay(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_script',
      displayName: 'Script',
      previewText: 'Aa',
      themeFamily: 'Cursive',
      previewStyleBuilder: (selected) => GoogleFonts.dancingScript(
        fontSize: selected ? 26 : 24,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_modern',
      displayName: 'Modern',
      previewText: 'Aa',
      themeFamily: 'Roboto',
      previewStyleBuilder: (selected) => GoogleFonts.robotoCondensed(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_mono',
      displayName: 'Mono',
      previewText: 'Aa',
      themeFamily: 'RobotoMono',
      previewStyleBuilder: (selected) => GoogleFonts.robotoMono(
        fontSize: selected ? 22 : 20,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_casual',
      displayName: 'Casual',
      previewText: 'Aa',
      themeFamily: 'Casual',
      previewStyleBuilder: (selected) => GoogleFonts.comfortaa(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_round',
      displayName: 'Rounded',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.quicksand(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_noto',
      displayName: 'Noto',
      previewText: 'Aa',
      themeFamily: 'NotoSans-VariableFont_wdth,wght.ttf',
      previewStyleBuilder: (selected) => GoogleFonts.notoSans(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_open_sans',
      displayName: 'Open Sans',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.openSans(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_lato',
      displayName: 'Lato',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.lato(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_poppins',
      displayName: 'Poppins',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.poppins(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_montserrat',
      displayName: 'Montserrat',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.montserrat(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_raleway',
      displayName: 'Raleway',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.raleway(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_ubuntu',
      displayName: 'Ubuntu',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.ubuntu(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_oswald',
      displayName: 'Oswald',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.oswald(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_inter',
      displayName: 'Inter',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.inter(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_source_sans',
      displayName: 'Source Sans',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.sourceSans3(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_work_sans',
      displayName: 'Work Sans',
      previewText: 'Aa',
      themeFamily: 'SansSerif',
      previewStyleBuilder: (selected) => GoogleFonts.workSans(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_merriweather',
      displayName: 'Merriweather',
      previewText: 'Aa',
      themeFamily: 'Serif',
      previewStyleBuilder: (selected) => GoogleFonts.merriweather(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_lora',
      displayName: 'Lora',
      previewText: 'Aa',
      themeFamily: 'Serif',
      previewStyleBuilder: (selected) => GoogleFonts.lora(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_pt_serif',
      displayName: 'PT Serif',
      previewText: 'Aa',
      themeFamily: 'Serif',
      previewStyleBuilder: (selected) => GoogleFonts.ptSerif(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_pacifico',
      displayName: 'Pacifico',
      previewText: 'Aa',
      themeFamily: 'Cursive',
      previewStyleBuilder: (selected) => GoogleFonts.pacifico(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w400,
      ),
    ),
    _FontOption(
      id: 'font_lobster',
      displayName: 'Lobster',
      previewText: 'Aa',
      themeFamily: 'Cursive',
      previewStyleBuilder: (selected) => GoogleFonts.lobster(
        fontSize: selected ? 24 : 22,
        fontWeight: FontWeight.w400,
      ),
    ),
    _FontOption(
      id: 'font_caveat',
      displayName: 'Caveat',
      previewText: 'Aa',
      themeFamily: 'Cursive',
      previewStyleBuilder: (selected) => GoogleFonts.caveat(
        fontSize: selected ? 26 : 24,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_jetbrains_mono',
      displayName: 'JetBrains Mono',
      previewText: 'Aa',
      themeFamily: 'RobotoMono',
      previewStyleBuilder: (selected) => GoogleFonts.jetBrainsMono(
        fontSize: selected ? 22 : 20,
        fontWeight: FontWeight.w600,
      ),
    ),
    _FontOption(
      id: 'font_source_code',
      displayName: 'Source Code',
      previewText: 'Aa',
      themeFamily: 'RobotoMono',
      previewStyleBuilder: (selected) => GoogleFonts.sourceCodePro(
        fontSize: selected ? 22 : 20,
        fontWeight: FontWeight.w600,
      ),
    ),
  ];

  static const String _customFontId = 'font_custom';
  
  late String _selectedFontId;
  String? _selectedSound;
  
  // Animation controllers for live preview
  late AnimationController _previewController;
  late Animation<double> _previewAnimation;
  
  // Method channel for keyboard sound
  static const MethodChannel _soundChannel = MethodChannel('keyboard.sound');

  int _currentTabIndex = 0;

  @override
  void initState() {
    super.initState();
    // Initialize with provided theme or create new one
    _currentTheme = widget.initialTheme ?? KeyboardThemeV2.createDefault();
    _themeNotifier.value = _currentTheme;
    _overlayTheme = _currentTheme;
    _nameController.text = _currentTheme.name;
    _selectedFontId = _resolveFontOptionId(
      _currentTheme.keys.font.family,
    );
    // Load selected sound from preferences
    _loadSelectedSound();
    _tabs = [
      _EditorTab(
        icon: Icons.camera_alt_outlined,
        label: 'Image',
        builder: _buildImageTab,
      ),
      _EditorTab(
        icon: Icons.format_color_text_rounded,
        label: 'Button',
        builder: _buildButtonTab,
      ),
      _EditorTab(
        icon: Icons.auto_awesome,
        label: 'Effect',
        builder: _buildEffectsTab,
      ),
      _EditorTab(
        icon: Icons.font_download,
        label: 'Font',
        builder: _buildFontTab,
      ),
      _EditorTab(
        icon: Icons.music_note_rounded,
        label: 'Sound',
        builder: _buildSoundTab,
      ),
      _EditorTab(
        icon: Icons.emoji_emotions_outlined,
        label: 'Stickers',
      builder: _buildStickersTab,
      ),
    ];

    // Setup preview animation
    _previewController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _previewAnimation = CurvedAnimation(
      parent: _previewController,
      curve: Curves.easeInOut,
    );
    
    _previewController.forward();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _keyboardFocusNode.requestFocus();
        if (_currentTheme.advanced.livePreview) {
          _ensurePreviewOverlay();
        } else {
          _removePreviewOverlay();
        }
      }
    });
  }

  @override
  void dispose() {
    _removePreviewOverlay();
    _nameController.dispose();
    _previewController.dispose();
    _themeNotifier.dispose();
    _keyboardFocusNode.dispose();
    super.dispose();
  }

  Future<void> _saveTheme() async {
    if (_nameController.text.trim().isEmpty) {
      _showError('Theme name cannot be empty');
      return;
    }

    // Convert font option ID back to themeFamily for Android compatibility
    final fontFamily = _currentTheme.keys.font.family;
    final androidFontFamily = fontFamily.startsWith('font_') 
        ? _getThemeFamilyForFontOptionId(fontFamily)
        : fontFamily;

    // Force toolbar and suggestions to inherit from keys (CleverType style)
    final updatedTheme = _currentTheme.copyWith(
      name: _nameController.text.trim(),
      id: _currentTheme.id.isEmpty ? 'custom_${DateTime.now().millisecondsSinceEpoch}' : _currentTheme.id,
      toolbar: _currentTheme.toolbar.copyWith(inheritFromKeys: true),
      suggestions: _currentTheme.suggestions.copyWith(inheritFromKeys: true),
      keys: _currentTheme.keys.copyWith(
        font: _currentTheme.keys.font.copyWith(
          family: androidFontFamily, // Convert to themeFamily for Android
        ),
      ),
    );

    try {
      await ThemeManagerV2.saveThemeV2(updatedTheme);
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Theme saved successfully!')),
        );
        Navigator.of(context).pop(updatedTheme);
      }
    } catch (e) {
      _showError('Failed to save theme: $e');
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: Colors.red),
    );
  }

  void _updateTheme(KeyboardThemeV2 newTheme) {
    setState(() {
      _currentTheme = newTheme;
      _selectedFontId = _resolveFontOptionId(
        newTheme.keys.font.family,
      );
    });

    // Notify theme notifier to trigger preview rebuild
    _themeNotifier.value = newTheme;

    _refreshPreviewOverlay(newTheme);

    // Animate preview update
    _previewController.reset();
    _previewController.forward();
    
    // Apply theme immediately to system keyboard if live preview is enabled
    if (_currentTheme.advanced.livePreview) {
      _applyThemeToKeyboard(newTheme);
    }
  }
  
  /// Apply theme to system keyboard immediately
  Future<void> _applyThemeToKeyboard(KeyboardThemeV2 theme) async {
    try {
      // Force inheritance for seamless CleverType experience
      final seamlessTheme = theme.copyWith(
        toolbar: theme.toolbar.copyWith(inheritFromKeys: true),
        suggestions: theme.suggestions.copyWith(inheritFromKeys: true),
      );
      await ThemeManagerV2.saveThemeV2(seamlessTheme);
    } catch (e) {
      // Silently fail
    }
  }

  void _ensurePreviewOverlay() {
    if (!mounted) return;
    final overlay = Overlay.of(context);
    if (overlay == null) {
      return;
    }

    if (_previewOverlayEntry == null) {
      _previewOverlayEntry = OverlayEntry(
        builder: (context) {
          final theme = _overlayTheme ?? _currentTheme;
          final controls =
              _buildStickyControls(context: context, floating: true);
          return Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (controls != null) controls,
                _RealKeyboardWidget(
                  key: ValueKey(
                    'keyboard_preview_${theme.id}_${theme.keys.font.family}',
                  ),
                  theme: theme,
                  onThemeChanged: (newTheme) {
                    // Apply theme when it changes
                    ThemeManagerV2.saveThemeV2(newTheme);
                  },
                ),
              ],
            ),
          );
        },
      );
      overlay.insert(_previewOverlayEntry!);
    } else {
      _previewOverlayEntry!.markNeedsBuild();
    }
  }

  void _refreshPreviewOverlay(KeyboardThemeV2 theme) {
    _overlayTheme = theme;
    if (!theme.advanced.livePreview) {
      _removePreviewOverlay();
      return;
    }
    _ensurePreviewOverlay();
  }

  void _removePreviewOverlay() {
    _previewOverlayEntry?.remove();
    _previewOverlayEntry = null;
  }

  Future<void> _exportTheme() async {
    final jsonString = ThemeManagerV2.exportTheme(_currentTheme);
    await Clipboard.setData(ClipboardData(text: jsonString));
    
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Theme exported to clipboard!')),
    );
  }

  Future<void> _importTheme() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['json'],
      );
      
      if (result != null) {
        final file = File(result.files.single.path!);
        final jsonString = await file.readAsString();
        final theme = ThemeManagerV2.importTheme(jsonString);
        
        if (theme != null) {
          _updateTheme(theme);
          _nameController.text = theme.name;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Theme imported successfully!')),
          );
        } else {
          _showError('Invalid theme file');
        }
      }
    } catch (e) {
      _showError('Failed to import theme: $e');
    }
  }

  // Helper method to calculate responsive sizes
  double _getResponsiveSize(BuildContext context, double baseSize, {double min = 0, double max = double.infinity}) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final scaleFactor = (screenWidth / 360).clamp(0.8, 1.5); // Base on 360dp width
    final responsiveSize = baseSize * scaleFactor;
    return responsiveSize.clamp(min, max);
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final bool isKeyboardVisible = mediaQuery.viewInsets.bottom > 0;
    final bool canShowSticky =
        !_currentTheme.advanced.livePreview && isKeyboardVisible;

    final Widget? inlineControls = canShowSticky
        ? _buildStickyControls(context: context, floating: true)
        : null;
    final double contentBottomPadding = inlineControls == null ? 32 : 180;
    final Widget paddedTabContent = Padding(
      padding: EdgeInsets.only(bottom: contentBottomPadding),
      child: _tabs[_currentTabIndex].builder(),
    );

    return Scaffold(
      backgroundColor: AppColors.lightGrey,
      appBar: AppBar(
        toolbarHeight: 100,
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.of(context).pop(),
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 16),
            child: ElevatedButton(
              onPressed: _saveTheme,
              style: _noShadowButtonStyle(
                backgroundColor: AppColors.white,
                foregroundColor: AppColors.black,
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              ),
              child: Text(
                'Save',
                style: AppTextStyle.bodyLarge.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
        ],
        title: Text(
          'Customize Theme',
          style: AppTextStyle.titleLarge.copyWith(color: AppColors.white),
        ),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(72),
          child: _buildTabNavigation(),
        ),
      ),
      body: Stack(
        children: [
          Column(
            children: [
              SizedBox(
                height: 1,
                child: TextField(
                  focusNode: _keyboardFocusNode,
                  autofocus: true,
                  readOnly: true,
                  decoration: const InputDecoration(
                    border: InputBorder.none,
                    contentPadding: EdgeInsets.zero,
                  ),
                  style: const TextStyle(color: Colors.transparent, fontSize: 1),
                ),
              ),
              Expanded(
                child: DecoratedBox(
                  decoration: const BoxDecoration(color: AppColors.lightGrey),
                  child: paddedTabContent,
                ),
              ),
            ],
          ),
          if (inlineControls != null)
            Align(
              alignment: Alignment.bottomCenter,
              child: inlineControls,
            ),
        ],
      ),
    );
  }

  Widget _buildTabNavigation() {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final scaleFactor = (screenWidth / 360).clamp(0.8, 1.3);
    
    final iconSize = (50 * scaleFactor).clamp(40.0, 60.0);
    final iconInnerSize = (24 * scaleFactor).clamp(20.0, 28.0);
    final borderRadius = (8 * scaleFactor).clamp(6.0, 12.0);
    final horizontalPadding = (16 * scaleFactor).clamp(12.0, 20.0);
    final verticalPadding = (12 * scaleFactor).clamp(8.0, 16.0);
    final spacing = (4 * scaleFactor).clamp(2.0, 6.0);
    final indicatorWidth = (24 * scaleFactor).clamp(20.0, 30.0);
    final textSize = (12 * scaleFactor).clamp(10.0, 14.0);
    
    return Container(
      padding: EdgeInsets.symmetric(horizontal: horizontalPadding, vertical: verticalPadding),
      color: AppColors.primary,
      child: Row(
        children: List.generate(_tabs.length, (index) {
          final tab = _tabs[index];
          final isSelected = _currentTabIndex == index;
          return Expanded(
            child: GestureDetector(
              onTap: () {
                if (_currentTabIndex != index) {
                  setState(() {
                    _currentTabIndex = index;
                  });
                  _previewOverlayEntry?.markNeedsBuild();
                }
              },
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: iconSize,
                    height: iconSize,
                    decoration: BoxDecoration(
                      color: isSelected ? AppColors.secondary : const Color(0xFF1C3453),
                      borderRadius: BorderRadius.circular(borderRadius),
                    ),
                    child: Icon(
                      tab.icon,
                      color: isSelected ? AppColors.primary : AppColors.white,
                      size: iconInnerSize,
                    ),
                  ),
                  SizedBox(height: spacing),
                  Text(
                    tab.label,
                    style: AppTextStyle.bodySmall.copyWith(
                      color: isSelected ? AppColors.secondary : AppColors.white,
                      fontWeight: FontWeight.w500,
                      fontSize: textSize,
                    ),
                  ),
                  if (isSelected)
                    Container(
                      margin: EdgeInsets.only(top: spacing),
                      height: 2,
                      width: indicatorWidth,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        borderRadius: BorderRadius.circular(1),
                      ),
                    ),
                ],
              ),
            ),
          );
        }),
      ),
    );
  }

  Widget? _buildStickyControls({
    required BuildContext context,
    required bool floating,
  }) {
    final Widget? content = _buildControlContent(context);
    if (content == null) {
      return null;
    }
    final bottomInset = MediaQuery.of(context).padding.bottom;
    final verticalPadding = floating ? 8.0 : 20.0;
    final bottomPadding = bottomInset + (floating ? 16.0 : 24.0);
    return SafeArea(
      top: false,
      child: Padding(
        padding: EdgeInsets.fromLTRB(5, verticalPadding, 5, bottomPadding),
        child: content,
      ),
    );
  }

  Widget? _buildControlContent(BuildContext context) {
    if (_tabs.isEmpty) return null;
    final label = _tabs[_currentTabIndex].label;
    switch (label) {
      case 'Image':
        return _buildSliderControl(
          context: context,
          icon: Icons.photo_outlined,
          title: 'Brightness',
          value: _currentTheme.background.imageOpacity,
          min: 0.3,
          max: 1.0,
          divisions: 14,
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              background: _currentTheme.background.copyWith(imageOpacity: value),
            ));
          },
          valueLabel:
              '${(_currentTheme.background.imageOpacity * 100).round()}%',
        );
      case 'Button':
        final keyOpacity = _currentTheme.keys.bg.opacity.clamp(0.2, 1.0);
        return _buildSliderControl(
          context: context,
          icon: Icons.opacity_rounded,
          title: 'Button \nOpacity',
          value: keyOpacity,
          min: 0.2,
          max: 1.0,
          divisions: 16,
          onChanged: (value) {
            final bgColor = _currentTheme.keys.bg.withOpacity(value);
            _updateTheme(_currentTheme.copyWith(
              keys: _currentTheme.keys.copyWith(bg: bgColor),
            ));
          },
          valueLabel: '${(keyOpacity * 100).round()}%',
        );
      case 'Effect':
        return _buildSliderControl(
          context: context,
          icon: Icons.auto_awesome,
          title: 'Effect \nOpacity',
          value: _currentTheme.effects.opacity.clamp(0.0, 1.0),
          min: 0.0,
          max: 1.0,
          divisions: 20,
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              effects: _currentTheme.effects.copyWith(opacity: value),
            ));
          },
          valueLabel: '${(_currentTheme.effects.opacity * 100).round()}%',
        );
      case 'Font':
        return _buildFontColorControl(context);
      case 'Sound':
        return _buildSliderControl(
          context: context,
          icon: Icons.volume_up_rounded,
          title: 'Key Volume',
          value: _currentTheme.sounds.volume.clamp(0.0, 1.0),
          min: 0.0,
          max: 1.0,
          divisions: 10,
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              sounds: _currentTheme.sounds.copyWith(volume: value),
            ));
          },
          valueLabel: '${(_currentTheme.sounds.volume * 100).round()}%',
        );
      case 'Stickers':
        return _buildSliderControl(
          context: context,
          icon: Icons.filter_none,
          title: 'Sticker Opacity',
          value: _currentTheme.stickers.opacity.clamp(0.0, 1.0),
          min: 0.0,
          max: 1.0,
          divisions: 20,
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              stickers: _currentTheme.stickers.copyWith(opacity: value),
            ));
          },
          valueLabel: '${(_currentTheme.stickers.opacity * 100).round()}%',
        );
      default:
        return null;
    }
  }

  Widget _buildSliderControl({
    required BuildContext context,
    required IconData icon,
    required String title,
    required double value,
    required double min,
    required double max,
    required ValueChanged<double> onChanged,
    int? divisions,
    String? valueLabel,
    Color? accentColor,
  }) {
    final normalizedValue = value.isNaN ? min : value.clamp(min, max);
    final displayValue = valueLabel ?? '${(normalizedValue * 100).round()}%';
    final color = accentColor ?? AppColors.secondary;

    return _buildControlSurface(
      child: 
         
          Row(
            children: [Text(
                  title,
                  style: AppTextStyle.titleMedium.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: color,
              inactiveTrackColor: color.withOpacity(0.2),
              thumbColor: color,
              trackHeight: 4,
            ),
            child: Slider(
              value: normalizedValue,
              onChanged: onChanged,
              min: min,
              max: max,
              divisions: divisions,
            ),
          ),
              Text(
                displayValue,
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.grey,
                  fontWeight: FontWeight.w500,
                ),
              ),]),
          
      
      
    );
  }

  Widget _buildFontColorControl(BuildContext context) {
    final currentColor = _currentTheme.keys.text;
    final hsv = HSVColor.fromColor(currentColor);
    final sliderValue = (hsv.hue.isNaN ? 0.0 : hsv.hue / 360).clamp(0.0, 1.0);
    const gradientColors = [
      Color(0xFFFF4E50),
      Color(0xFFF9D423),
      Color(0xFF24FE41),
      Color(0xFF24C6DC),
      Color(0xFF0099F7),
      Color(0xFF8A2BE2),
      Color(0xFFFF4E50),
    ];

    return _buildControlSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: currentColor.withOpacity(0.16),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Icon(Icons.text_fields_rounded,
                    color: AppColors.primary),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Color',
                  style: AppTextStyle.titleMedium.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: Colors.black12),
                  color: currentColor,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Stack(
            alignment: Alignment.center,
            children: [
              Container(
                height: 6,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(999),
                  gradient: const LinearGradient(colors: gradientColors),
                ),
              ),
              SliderTheme(
                data: SliderTheme.of(context).copyWith(
                  trackHeight: 0,
                  inactiveTrackColor: Colors.transparent,
                  activeTrackColor: Colors.transparent,
                  thumbColor: currentColor,
                  overlayShape: SliderComponentShape.noOverlay,
                ),
                child: Slider(
                  min: 0,
                  max: 1,
                  value: sliderValue,
                  onChanged: (position) {
                    final hue =
                        (position * 360).clamp(0.0, 360.0).toDouble();
                    final saturation =
                        hsv.saturation.clamp(0.4, 1.0).toDouble();
                    final brightness =
                        hsv.value.clamp(0.4, 1.0).toDouble();
                    final newColor = HSVColor.fromAHSV(
                      1,
                      hue,
                      saturation,
                      brightness,
                    ).toColor();
                    _updateTheme(_currentTheme.copyWith(
                      keys: _currentTheme.keys.copyWith(text: newColor),
                    ));
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            _colorToHex(currentColor),
            style: AppTextStyle.labelMedium.copyWith(
              color: AppColors.grey,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildControlSurface({required Widget child}) {
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        width: double.infinity,
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: AppColors.black.withOpacity(0.08),
              blurRadius: 24,
              offset: const Offset(0, 12),
            ),
          ],
        ),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
        child: child,
      ),
    );
  }

  String _colorToHex(Color color) {
    final value = color.value & 0xFFFFFF;
    return '#${value.toRadixString(16).padLeft(6, '0').toUpperCase()}';
  }

  Widget _buildPreviewSection() {
    return Container(
      width: double.infinity,
      decoration: const BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Live Preview',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          _buildLivePreview(),
        ],
      ),
    );
  }

  ButtonStyle _noShadowButtonStyle({
    Color? backgroundColor,
    Color? foregroundColor,
    EdgeInsetsGeometry? padding,
    BorderRadiusGeometry borderRadius = const BorderRadius.all(Radius.circular(8)),
    BorderSide? side,
  }) {
    return ElevatedButton.styleFrom(
      elevation: 0,
      shadowColor: Colors.transparent,
      backgroundColor: backgroundColor,
      foregroundColor: foregroundColor,
      padding: padding,
      shape: RoundedRectangleBorder(
        borderRadius: borderRadius,
        side: side ?? BorderSide.none,
      ),
    );
  }

  Widget _buildBottomKeyboardPreview() {
    // Use theme-aware preview methods to ensure proper font rendering
    final theme = _currentTheme;
    return Container(
      decoration: BoxDecoration(
        color: theme.background.color ?? Colors.grey[900],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildPreviewToolbarForTheme(theme),
          _buildPreviewSuggestionsForTheme(theme),
          _buildPreviewKeyboardForTheme(theme),
        ],
      ),
    );
  }

  Widget _buildLivePreview() {
    // Use AnimatedBuilder with both animation and theme notifier to ensure rebuilds
    return AnimatedBuilder(
      animation: Listenable.merge([_previewAnimation, _themeNotifier]),
      builder: (context, child) {
        // Use current theme from notifier or fallback to _currentTheme
        final theme = _themeNotifier.value ?? _currentTheme;
        return Transform.scale(
          scale: 0.7 + (0.3 * _previewAnimation.value),
          child: Container(
            margin: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: theme.background.color ?? Colors.grey[900],
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _buildPreviewToolbarForTheme(theme),
                _buildPreviewSuggestionsForTheme(theme),
                _buildPreviewKeyboardForTheme(theme),
              ],
            ),
          ),
        );
      },
    );
  }
  
  Widget _buildPreviewToolbarForTheme(KeyboardThemeV2 theme) {
    final toolbarBg = theme.toolbar.inheritFromKeys 
        ? theme.keys.bg 
        : theme.toolbar.bg;
    final toolbarIcon = theme.toolbar.inheritFromKeys 
        ? theme.keys.text 
        : theme.toolbar.icon;

    return Container(
      height: theme.toolbar.heightDp * 0.8,
      decoration: BoxDecoration(
        color: toolbarBg,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          Icon(Icons.mic, color: toolbarIcon, size: 16),
          Icon(Icons.emoji_emotions, color: theme.toolbar.activeAccent, size: 16),
          Icon(Icons.gif_box, color: toolbarIcon, size: 16),
          Icon(Icons.more_horiz, color: toolbarIcon, size: 16),
        ],
      ),
    );
  }

  Widget _buildPreviewSuggestionsForTheme(KeyboardThemeV2 theme) {
    final suggestionBg = theme.suggestions.inheritFromKeys 
        ? theme.keys.bg 
        : theme.suggestions.bg;
    final suggestionText = theme.suggestions.inheritFromKeys 
        ? theme.keys.text 
        : theme.suggestions.text;

    return Container(
      height: 32,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      color: suggestionBg,
      child: Row(
        children: [
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: theme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(theme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'hello',
                  style: getTextStyleForFontOptionId(
                    theme.suggestions.font.family,
                    10,
                    theme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: theme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: theme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(theme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'world',
                  style: getTextStyleForFontOptionId(
                    theme.suggestions.font.family,
                    10,
                    theme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: theme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: theme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(theme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'test',
                  style: getTextStyleForFontOptionId(
                    theme.suggestions.font.family,
                    10,
                    theme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: theme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewKeyboardForTheme(KeyboardThemeV2 theme) {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // First row
          Row(
            children: 'QWERTYUIOP'.split('').map((letter) => _buildPreviewKeyForTheme(letter, false, theme)).toList(),
          ),
          const SizedBox(height: 2),
          // Second row
          Row(
            children: [
              ...('ASDFGHJKL'.split('').map((letter) => _buildPreviewKeyForTheme(letter, false, theme))),
              _buildPreviewKeyForTheme('⌫', true, theme),
            ],
          ),
          const SizedBox(height: 2),
          // Third row
          Row(
            children: [
              _buildPreviewKeyForTheme('⇧', true, theme),
              ...('ZXCVBNM'.split('').map((letter) => _buildPreviewKeyForTheme(letter, false, theme))),
              _buildPreviewKeyForTheme('⏎', theme.specialKeys.useAccentForEnter, theme),
            ],
          ),
          const SizedBox(height: 2),
          // Space row
          Row(
            children: [
              _buildPreviewKeyForTheme('123', false, theme),
              Expanded(child: _buildPreviewKeyForTheme('space', false, theme, isWide: true)),
              _buildPreviewKeyForTheme('🌐', theme.specialKeys.applyTo.contains('globe'), theme),
              _buildPreviewKeyForTheme('😀', theme.specialKeys.applyTo.contains('emoji'), theme),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewKeyForTheme(String text, bool isSpecial, KeyboardThemeV2 theme, {bool isWide = false}) {
    final keyColor = isSpecial 
        ? theme.specialKeys.accent 
        : theme.keys.bg;
    final textColor = isSpecial 
        ? Colors.white 
        : theme.keys.text;

    return Expanded(
      flex: isWide ? 4 : 1,
      child: Container(
        height: 24,
        margin: const EdgeInsets.all(0.5),
        decoration: BoxDecoration(
          color: keyColor,
          borderRadius: BorderRadius.circular(theme.keys.radius / 3),
          border: theme.keys.border.enabled 
              ? Border.all(color: theme.keys.border.color, width: 0.5)
              : null,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.15),
              blurRadius: 3,
              spreadRadius: 0,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Center(
          child: Text(
            text,
            style: getTextStyleForFontOptionId(
              theme.keys.font.family,
              8,
              theme.keys.font.bold ? FontWeight.bold : FontWeight.normal,
            ).copyWith(
              color: text == 'space' ? theme.specialKeys.spaceLabelColor : textColor,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPreviewToolbar() {
    final toolbarBg = _currentTheme.toolbar.inheritFromKeys 
        ? _currentTheme.keys.bg 
        : _currentTheme.toolbar.bg;
    final toolbarIcon = _currentTheme.toolbar.inheritFromKeys 
        ? _currentTheme.keys.text 
        : _currentTheme.toolbar.icon;

    return Container(
      height: _currentTheme.toolbar.heightDp * 0.8,
      decoration: BoxDecoration(
        color: toolbarBg,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(12)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          Icon(Icons.mic, color: toolbarIcon, size: 16),
          Icon(Icons.emoji_emotions, color: _currentTheme.toolbar.activeAccent, size: 16),
          Icon(Icons.gif_box, color: toolbarIcon, size: 16),
          Icon(Icons.more_horiz, color: toolbarIcon, size: 16),
        ],
      ),
    );
  }

  Widget _buildPreviewSuggestions() {
    final suggestionBg = _currentTheme.suggestions.inheritFromKeys 
        ? _currentTheme.keys.bg 
        : _currentTheme.suggestions.bg;
    final suggestionText = _currentTheme.suggestions.inheritFromKeys 
        ? _currentTheme.keys.text 
        : _currentTheme.suggestions.text;

    return Container(
      height: 32,
      padding: const EdgeInsets.symmetric(horizontal: 8),
      color: suggestionBg,
      child: Row(
        children: [
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: _currentTheme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(_currentTheme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'hello',
                  style: getTextStyleForFontOptionId(
                    _currentTheme.suggestions.font.family,
                    10,
                    _currentTheme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: _currentTheme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: _currentTheme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(_currentTheme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'world',
                  style: getTextStyleForFontOptionId(
                    _currentTheme.suggestions.font.family,
                    10,
                    _currentTheme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: _currentTheme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 2),
              decoration: BoxDecoration(
                color: _currentTheme.suggestions.chip.bg,
                borderRadius: BorderRadius.circular(_currentTheme.suggestions.chip.radius / 2),
              ),
              child: Center(
                child: Text(
                  'test',
                  style: getTextStyleForFontOptionId(
                    _currentTheme.suggestions.font.family,
                    10,
                    _currentTheme.suggestions.font.bold ? FontWeight.bold : FontWeight.normal,
                  ).copyWith(
                    color: _currentTheme.suggestions.chip.text,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewKeyboard() {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // First row
          Row(
            children: 'QWERTYUIOP'.split('').map((letter) => _buildPreviewKey(letter, false)).toList(),
          ),
          const SizedBox(height: 2),
          // Second row
          Row(
            children: [
              ...('ASDFGHJKL'.split('').map((letter) => _buildPreviewKey(letter, false))),
              _buildPreviewKey('⌫', true),
            ],
          ),
          const SizedBox(height: 2),
          // Third row
          Row(
            children: [
              _buildPreviewKey('⇧', true),
              ...('ZXCVBNM'.split('').map((letter) => _buildPreviewKey(letter, false))),
              _buildPreviewKey('⏎', _currentTheme.specialKeys.useAccentForEnter),
            ],
          ),
          const SizedBox(height: 2),
          // Space row
          Row(
            children: [
              _buildPreviewKey('123', false),
              Expanded(child: _buildPreviewKey('space', false, isWide: true)),
              _buildPreviewKey('🌐', _currentTheme.specialKeys.applyTo.contains('globe')),
              _buildPreviewKey('😀', _currentTheme.specialKeys.applyTo.contains('emoji')),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewKey(String text, bool isSpecial, {bool isWide = false}) {
    final keyColor = isSpecial 
        ? _currentTheme.specialKeys.accent 
        : _currentTheme.keys.bg;
    final textColor = isSpecial 
        ? Colors.white 
        : _currentTheme.keys.text;

    return Expanded(
      flex: isWide ? 4 : 1,
      child: Container(
        height: 24,
        margin: const EdgeInsets.all(0.5),
        decoration: BoxDecoration(
          color: keyColor,
          borderRadius: BorderRadius.circular(_currentTheme.keys.radius / 3),
          border: _currentTheme.keys.border.enabled 
              ? Border.all(color: _currentTheme.keys.border.color, width: 0.5)
              : null,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.15),
              blurRadius: 3,
              spreadRadius: 0,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Center(
          child: Text(
            text,
            style: _getTextStyleForFontOptionId(
              _currentTheme.keys.font.family,
              8,
              _currentTheme.keys.font.bold ? FontWeight.bold : FontWeight.normal,
            ).copyWith(
              color: text == 'space' ? _currentTheme.specialKeys.spaceLabelColor : textColor,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBasicTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Theme Information',
            [
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Theme Name',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _currentTheme.mode,
                decoration: const InputDecoration(
                  labelText: 'Theme Mode',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'unified', child: Text('Unified (Same colors)')),
                  DropdownMenuItem(value: 'split', child: Text('Split (Custom colors)')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(mode: value));
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Quick Themes',
            [
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createDefault()),
                      style: _noShadowButtonStyle(),
                      child: const Text('Dark Theme'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(ThemeManagerV2.createLightTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('Light Theme'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createBlueTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('Blue'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createPinkTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('Pink'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createGreenTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('Green'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createLoveHeartsTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('💕 Hearts'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createGoldStarTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('⭐ Stars'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createNeonTheme()),
                      style: _noShadowButtonStyle(),
                      child: const Text('✨ Neon'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _generateRandomTheme,
                      icon: const Icon(Icons.shuffle),
                      label: const Text('Random Theme'),
                      style: _noShadowButtonStyle(
                        backgroundColor: Colors.purple.shade100,
                        foregroundColor: Colors.purple.shade800,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: () => _updateTheme(KeyboardThemeV2.createGalaxyTheme()),
                      icon: const Icon(Icons.auto_awesome),
                      label: const Text('🌌 Galaxy'),
                      style: _noShadowButtonStyle(),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// Generate a random theme with random colors and effects
  void _generateRandomTheme() {
    final random = DateTime.now().millisecondsSinceEpoch;
    final allColors = [
      Colors.red, Colors.pink, Colors.purple, Colors.blue,
      Colors.cyan, Colors.teal, Colors.green, Colors.yellow,
      Colors.orange, Colors.brown, Colors.grey,
      const Color(0xFF2196F3), const Color(0xFF4CAF50), const Color(0xFF9C27B0),
      const Color(0xFFFF9800), const Color(0xFFE91E63), const Color(0xFF00BCD4),
      const Color(0xFF03A9F4), const Color(0xFFCDDC39), const Color(0xFFFFC107),
    ];
    
    final bgColor = allColors[random % allColors.length];
    final keyColor = Color.lerp(bgColor, Colors.white, 0.3) ?? bgColor;
    final accentColor = Color.lerp(bgColor, Colors.black, 0.5) ?? bgColor;
    
    final presets = ['rounded', 'bordered', 'flat'];
    final animations = ['ripple', 'glow', 'bounce'];
    final effects = [<String>[], ['glow'], ['sparkles'], ['hearts'], ['sparkles', 'glow']];
    
    final randomTheme = _currentTheme.copyWith(
      id: 'random_theme_$random',
      name: 'Random ${random.toString().substring(random.toString().length - 4)}',
      background: _currentTheme.background.copyWith(color: bgColor),
      keys: _currentTheme.keys.copyWith(
        preset: presets[random % presets.length],
        bg: keyColor,
        radius: 4.0 + (random % 16).toDouble(),
      ),
      specialKeys: _currentTheme.specialKeys.copyWith(accent: accentColor),
      effects: _currentTheme.effects.copyWith(
        pressAnimation: animations[random % animations.length],
        globalEffects: effects[random % effects.length],
      ),
    );
    
    _updateTheme(randomTheme);
    _nameController.text = randomTheme.name;
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Random theme generated! 🎲'),
        backgroundColor: accentColor,
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Widget _buildImageTab() {
    final List<String> imageThemes = [
      'assets/image_theme/Sky1.png',
      'assets/image_theme/Sky2.jpg',
      'assets/image_theme/Sky3.jpg',
      'assets/image_theme/Sky4.jpg',
      'assets/image_theme/bench.png',
      'assets/image_theme/circldesign.jpg',
      'assets/image_theme/perosn_with sunset.png',
      'assets/image_theme/sun_moon.jpg',
    ];

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Upload Photo Section
          GestureDetector(
            onTap: _uploadCustomImageForTheme,
            child: Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: Colors.grey[50],
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.grey[300]!, width: 2, style: BorderStyle.solid),
              ),
              child: Row(
                children: [
                  Container(
                    width: 60,
                    height: 60,
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.camera_alt,
                      color: Colors.white,
                      size: 32,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Upload Photo',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Upload High Quality Photo',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    width: 50,
                    height: 50,
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.add_photo_alternate,
                      color: Colors.white,
                      size: 28,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          
          // Recently Uploaded Section
          Text(
            'Recently Uploaded',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              crossAxisSpacing: 12,
              mainAxisSpacing: 12,
              childAspectRatio: 1.2,
            ),
            itemCount: imageThemes.length,
            itemBuilder: (context, index) {
              return _buildImageThemeTile(imageThemes[index]);
            },
          ),
          const SizedBox(height: 250),
        
          
        ],
      ),
    );
  }

  Widget _buildImageThemeTile(String imagePath) {
    // Check if this image is currently selected by checking if the background path contains the image name
    final imageName = imagePath.split('/').last;
    final currentImagePath = _currentTheme.background.imagePath ?? '';
    final isSelected = _currentTheme.background.type == 'image' && 
                      currentImagePath.contains(imageName.replaceAll('.png', '').replaceAll('.jpg', ''));
    
    return GestureDetector(
      onTap: () => _applyImageTheme(imagePath),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppColors.secondary : Colors.grey[300]!,
            width: isSelected ? 3 : 1,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: AppColors.secondary.withOpacity(0.3),
                    blurRadius: 8,
                    offset: const Offset(0, 4),
                  ),
                ]
              : [],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(11),
          child: Stack(
            fit: StackFit.expand,
            children: [
              Image.asset(
                imagePath,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) {
                  return Container(
                    color: Colors.grey[200],
                    child: const Icon(Icons.image, size: 40, color: Colors.grey),
                  );
                },
              ),
              if (isSelected)
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withOpacity(0.3),
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.check_circle,
                      color: Colors.white,
                      size: 32,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _applyImageTheme(String assetPath) async {
    try {
      // Copy asset image to local storage so Android keyboard can access it
      final String localPath = await _copyAssetToLocal(assetPath);
      
      // Create proper image theme based on which image was selected
      KeyboardThemeV2 imageTheme;
      if (assetPath.contains('bench.png')) {
        imageTheme = KeyboardThemeV2.createBenchImageTheme(localPath);
      } else if (assetPath.contains('circldesign.jpg')) {
        imageTheme = KeyboardThemeV2.createCircleDesignImageTheme(localPath);
      } else if (assetPath.contains('perosn_with sunset.png')) {
        imageTheme = KeyboardThemeV2.createSunsetImageTheme(localPath);
      } else if (assetPath.contains('sun_moon.jpg')) {
        imageTheme = KeyboardThemeV2.createSunMoonImageTheme(localPath);
      } else {
        // Fallback: use generic image theme with white keys
        imageTheme = KeyboardThemeV2.createBenchImageTheme(localPath);
      }
      
      _updateTheme(imageTheme);
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Image theme applied!'),
            backgroundColor: AppColors.secondary,
            duration: const Duration(seconds: 1),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to apply image: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    }
  }

  Future<String> _copyAssetToLocal(String assetPath) async {
    try {
      // Load the asset as bytes
      final ByteData data = await rootBundle.load(assetPath);
      final List<int> bytes = data.buffer.asUint8List();
      
      // Get the application documents directory
      final Directory appDocDir = await getApplicationDocumentsDirectory();
      final String imagesDir = '${appDocDir.path}/keyboard_images';
      
      // Create the directory if it doesn't exist
      final Directory dir = Directory(imagesDir);
      if (!dir.existsSync()) {
        dir.createSync(recursive: true);
      }
      
      // Extract the file name from the asset path
      final String fileName = assetPath.split('/').last;
      final String localPath = '$imagesDir/$fileName';
      
      // Write the bytes to the local file
      final File file = File(localPath);
      await file.writeAsBytes(bytes);
      
      return localPath;
    } catch (e) {
      throw Exception('Failed to copy asset to local storage: $e');
    }
  }

  Widget _buildImageThumbnail(int index) {
    final sampleImages = [
      'https://picsum.photos/200/150?random=$index',
    ];
    
    return GestureDetector(
      onTap: () => _applySampleImage(sampleImages[0]),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.grey[300]!),
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: Image.network(
            sampleImages[0],
            fit: BoxFit.cover,
            errorBuilder: (context, error, stackTrace) {
              return Container(
                color: Colors.grey[200],
                child: const Icon(Icons.image, size: 40),
              );
            },
            loadingBuilder: (context, child, loadingProgress) {
              if (loadingProgress == null) return child;
              return Container(
                color: Colors.grey[200],
                child: const Center(
                  child: CircularProgressIndicator(),
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Future<void> _uploadCustomImageForTheme() async {
    // Navigate to custom image flow
    final customTheme = await Navigator.push<KeyboardThemeV2>(
      context,
      MaterialPageRoute(
        builder: (context) => const ChooseBaseThemeScreen(),
      ),
    );
    
    if (customTheme != null) {
      _updateTheme(customTheme);
    }
  }

  Future<void> _applySampleImage(String imageUrl) async {
    _updateTheme(_currentTheme.copyWith(
      background: _currentTheme.background.copyWith(
        type: 'image',
        imagePath: imageUrl,
      ),
    ));
  }

  Widget _buildBackgroundTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Background Type',
            [
              DropdownButtonFormField<String>(
                value: _currentTheme.background.type,
                decoration: const InputDecoration(
                  labelText: 'Background Type',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'solid', child: Text('Solid Color')),
                  DropdownMenuItem(value: 'gradient', child: Text('Gradient')),
                  DropdownMenuItem(value: 'image', child: Text('Image')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      background: _currentTheme.background.copyWith(type: value),
                    ));
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          if (_currentTheme.background.type == 'solid') _buildSolidBackgroundSection(),
          if (_currentTheme.background.type == 'gradient') _buildGradientBackgroundSection(),
          if (_currentTheme.background.type == 'image') _buildImageBackgroundSection(),
        ],
      ),
    );
  }

  Widget _buildSolidBackgroundSection() {
    return _buildSection(
      'Solid Color',
      [
        _buildColorPicker(
          'Background Color',
          _currentTheme.background.color ?? Colors.black,
          (color) {
            _updateTheme(_currentTheme.copyWith(
              background: _currentTheme.background.copyWith(color: color),
            ));
          },
        ),
      ],
    );
  }

  Widget _buildGradientBackgroundSection() {
    return _buildSection(
      'Gradient Settings',
      [
        DropdownButtonFormField<String>(
          value: _currentTheme.background.gradient?.orientation ?? 'TOP_BOTTOM',
          decoration: const InputDecoration(
            labelText: 'Gradient Direction',
            border: OutlineInputBorder(),
          ),
          items: const [
            DropdownMenuItem(value: 'TOP_BOTTOM', child: Text('Top to Bottom')),
            DropdownMenuItem(value: 'LEFT_RIGHT', child: Text('Left to Right')),
            DropdownMenuItem(value: 'TL_BR', child: Text('Top-Left to Bottom-Right')),
            DropdownMenuItem(value: 'TR_BL', child: Text('Top-Right to Bottom-Left')),
          ],
          onChanged: (value) {
            if (value != null) {
              final gradient = _currentTheme.background.gradient?.copyWith(orientation: value) ??
                  ThemeGradient(colors: [Colors.blue, Colors.purple], orientation: value);
              _updateTheme(_currentTheme.copyWith(
                background: _currentTheme.background.copyWith(gradient: gradient),
              ));
            }
          },
        ),
        const SizedBox(height: 16),
        _buildColorPicker(
          'Start Color',
          _currentTheme.background.gradient?.colors.first ?? Colors.blue,
          (color) {
            final colors = [...(_currentTheme.background.gradient?.colors ?? [Colors.blue, Colors.purple])];
            if (colors.isNotEmpty) colors[0] = color;
            final gradient = ThemeGradient(
              colors: colors,
              orientation: _currentTheme.background.gradient?.orientation ?? 'TOP_BOTTOM',
            );
            _updateTheme(_currentTheme.copyWith(
              background: _currentTheme.background.copyWith(gradient: gradient),
            ));
          },
        ),
        const SizedBox(height: 16),
        _buildColorPicker(
          'End Color',
          _currentTheme.background.gradient?.colors.last ?? Colors.purple,
          (color) {
            final colors = [...(_currentTheme.background.gradient?.colors ?? [Colors.blue, Colors.purple])];
            if (colors.length > 1) colors[1] = color;
            final gradient = ThemeGradient(
              colors: colors,
              orientation: _currentTheme.background.gradient?.orientation ?? 'TOP_BOTTOM',
            );
            _updateTheme(_currentTheme.copyWith(
              background: _currentTheme.background.copyWith(gradient: gradient),
            ));
          },
        ),
      ],
    );
  }

  Widget _buildImageBackgroundSection() {
    return _buildSection(
      'Image Settings',
      [
        TextFormField(
          initialValue: _currentTheme.background.imagePath ?? '',
          decoration: const InputDecoration(
            labelText: 'Image Path',
            border: OutlineInputBorder(),
            hintText: 'assets/images/background.png',
          ),
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              background: _currentTheme.background.copyWith(imagePath: value),
            ));
          },
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            const Text('Opacity:'),
            const SizedBox(width: 16),
            Expanded(
              child: Slider(
                value: _currentTheme.background.imageOpacity,
                min: 0.0,
                max: 1.0,
                divisions: 20,
                label: '${(_currentTheme.background.imageOpacity * 100).round()}%',
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    background: _currentTheme.background.copyWith(imageOpacity: value),
                  ));
                },
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildKeysTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Key Appearance',
            [
              DropdownButtonFormField<String>(
                value: _currentTheme.keys.preset,
                decoration: const InputDecoration(
                  labelText: 'Key Style Preset',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'flat', child: Text('Flat')),
                  DropdownMenuItem(value: 'bordered', child: Text('Bordered')),
                  DropdownMenuItem(value: 'floating', child: Text('Floating')),
                  DropdownMenuItem(value: '3d', child: Text('3D')),
                  DropdownMenuItem(value: 'transparent', child: Text('Transparent')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      keys: _currentTheme.keys.copyWith(preset: value),
                    ));
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Key Colors',
            [
              _buildColorPicker('Key Background', _currentTheme.keys.bg, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(bg: color),
                ));
              }),
              const SizedBox(height: 16),
              _buildColorPicker('Key Text', _currentTheme.keys.text, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(text: color),
                ));
              }),
              const SizedBox(height: 16),
              _buildColorPicker('Key Pressed', _currentTheme.keys.pressed, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(pressed: color),
                ));
              }),
              const SizedBox(height: 16),
              _buildColorPicker('Accent Color', _currentTheme.specialKeys.accent, (color) {
                _updateTheme(_currentTheme.copyWith(
                  specialKeys: _currentTheme.specialKeys.copyWith(accent: color),
                ));
              }),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Key Shape',
            [
              Row(
                children: [
                  const Text('Corner Radius:'),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Slider(
                      value: _currentTheme.keys.radius,
                      min: 0.0,
                      max: 20.0,
                      divisions: 20,
                      label: '${_currentTheme.keys.radius.round()}dp',
                      onChanged: (value) {
                        _updateTheme(_currentTheme.copyWith(
                          keys: _currentTheme.keys.copyWith(radius: value),
                        ));
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildToolbarTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Toolbar Settings',
            [
              SwitchListTile(
                title: const Text('Inherit from Keys'),
                subtitle: const Text('Use same colors as keyboard keys'),
                value: _currentTheme.toolbar.inheritFromKeys,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    toolbar: _currentTheme.toolbar.copyWith(inheritFromKeys: value),
                  ));
                },
              ),
            ],
          ),
          if (!_currentTheme.toolbar.inheritFromKeys) ...[
            const SizedBox(height: 24),
            _buildSection(
              'Custom Toolbar Colors',
              [
                _buildColorPicker('Background', _currentTheme.toolbar.bg, (color) {
                  _updateTheme(_currentTheme.copyWith(
                    toolbar: _currentTheme.toolbar.copyWith(bg: color),
                  ));
                }),
                const SizedBox(height: 16),
                _buildColorPicker('Icon Color', _currentTheme.toolbar.icon, (color) {
                  _updateTheme(_currentTheme.copyWith(
                    toolbar: _currentTheme.toolbar.copyWith(icon: color),
                  ));
                }),
              ],
            ),
          ],
          const SizedBox(height: 24),
          _buildSection(
            'Toolbar Layout',
            [
              Row(
                children: [
                  const Text('Height:'),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Slider(
                      value: _currentTheme.toolbar.heightDp,
                      min: 32.0,
                      max: 64.0,
                      divisions: 16,
                      label: '${_currentTheme.toolbar.heightDp.round()}dp',
                      onChanged: (value) {
                        _updateTheme(_currentTheme.copyWith(
                          toolbar: _currentTheme.toolbar.copyWith(heightDp: value),
                        ));
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSuggestionsTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Suggestion Bar Settings',
            [
              SwitchListTile(
                title: const Text('Inherit from Keys'),
                subtitle: const Text('Use same colors as keyboard keys'),
                value: _currentTheme.suggestions.inheritFromKeys,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    suggestions: _currentTheme.suggestions.copyWith(inheritFromKeys: value),
                  ));
                },
              ),
            ],
          ),
          if (!_currentTheme.suggestions.inheritFromKeys) ...[
            const SizedBox(height: 24),
            _buildSection(
              'Custom Suggestion Colors',
              [
                _buildColorPicker('Background', _currentTheme.suggestions.bg, (color) {
                  _updateTheme(_currentTheme.copyWith(
                    suggestions: _currentTheme.suggestions.copyWith(bg: color),
                  ));
                }),
                const SizedBox(height: 16),
                _buildColorPicker('Text Color', _currentTheme.suggestions.text, (color) {
                  _updateTheme(_currentTheme.copyWith(
                    suggestions: _currentTheme.suggestions.copyWith(text: color),
                  ));
                }),
              ],
            ),
          ],
          const SizedBox(height: 24),
          _buildSection(
            'Suggestion Chips',
            [
              _buildColorPicker('Chip Background', _currentTheme.suggestions.chip.bg, (color) {
                _updateTheme(_currentTheme.copyWith(
                  suggestions: _currentTheme.suggestions.copyWith(
                    chip: _currentTheme.suggestions.chip.copyWith(bg: color),
                  ),
                ));
              }),
              const SizedBox(height: 16),
              Row(
                children: [
                  const Text('Chip Radius:'),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Slider(
                      value: _currentTheme.suggestions.chip.radius,
                      min: 0.0,
                      max: 20.0,
                      divisions: 20,
                      label: '${_currentTheme.suggestions.chip.radius.round()}dp',
                      onChanged: (value) {
                        _updateTheme(_currentTheme.copyWith(
                          suggestions: _currentTheme.suggestions.copyWith(
                            chip: _currentTheme.suggestions.chip.copyWith(radius: value),
                          ),
                        ));
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAdvancedTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection(
            'Preview & Gallery',
            [
              SwitchListTile(
                title: const Text('Live Preview'),
                subtitle: const Text('Show keyboard preview while editing'),
                value: _currentTheme.advanced.livePreview,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    advanced: _currentTheme.advanced.copyWith(livePreview: value),
                  ));
                },
              ),
              SwitchListTile(
                title: const Text('Gallery Enabled'),
                subtitle: const Text('Allow sharing to theme gallery'),
                value: _currentTheme.advanced.galleryEnabled,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    advanced: _currentTheme.advanced.copyWith(galleryEnabled: value),
                  ));
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Effects & Animation',
            [
              DropdownButtonFormField<String>(
                value: _currentTheme.effects.pressAnimation,
                decoration: const InputDecoration(
                  labelText: 'Press Animation',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'ripple', child: Text('Ripple Effect')),
                  DropdownMenuItem(value: 'bounce', child: Text('Bounce Effect')),
                  DropdownMenuItem(value: 'glow', child: Text('Glow Effect')),
                  DropdownMenuItem(value: 'none', child: Text('No Animation')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      effects: _currentTheme.effects.copyWith(pressAnimation: value),
                    ));
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Sound Pack',
            [
              DropdownButtonFormField<String>(
                value: _currentTheme.sounds.pack,
                decoration: const InputDecoration(
                  labelText: 'Sound Pack',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'default', child: Text('Default')),
                  DropdownMenuItem(value: 'soft', child: Text('Soft Clicks')),
                  DropdownMenuItem(value: 'clicky', child: Text('Clicky')),
                  DropdownMenuItem(value: 'mechanical', child: Text('Mechanical')),
                  DropdownMenuItem(value: 'typewriter', child: Text('Typewriter')),
                  DropdownMenuItem(value: 'piano', child: Text('Piano Keys')),
                  DropdownMenuItem(value: 'pop', child: Text('Pop Sound')),
                  DropdownMenuItem(value: 'silent', child: Text('Silent')),
                  DropdownMenuItem(value: 'custom', child: Text('Custom (import)')),
                ],
                onChanged: (value) async {
                  if (value != null) {
                    if (value == 'custom') {
                      await _pickCustomSound();
                    } else {
                      _updateTheme(_currentTheme.copyWith(
                        sounds: _currentTheme.sounds.copyWith(
                          pack: value,
                          customUris: {},
                        ),
                      ));
                    }
                  }
                },
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  const Text('Volume:'),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Slider(
                      value: _currentTheme.sounds.volume,
                      min: 0.0,
                      max: 1.0,
                      divisions: 10,
                      label: '${(_currentTheme.sounds.volume * 100).round()}%',
                      onChanged: (value) {
                        _updateTheme(_currentTheme.copyWith(
                          sounds: _currentTheme.sounds.copyWith(volume: value),
                        ));
                      },
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 24),
          _buildSection(
            'Seasonal & Dynamic',
            [
              DropdownButtonFormField<String>(
                value: _currentTheme.advanced.seasonalPack,
                decoration: const InputDecoration(
                  labelText: 'Seasonal Pack',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'none', child: Text('None')),
                  DropdownMenuItem(value: 'valentine', child: Text('Valentine\'s Day')),
                  DropdownMenuItem(value: 'halloween', child: Text('Halloween')),
                  DropdownMenuItem(value: 'christmas', child: Text('Christmas')),
                  DropdownMenuItem(value: 'spring', child: Text('Spring')),
                  DropdownMenuItem(value: 'summer', child: Text('Summer')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      advanced: _currentTheme.advanced.copyWith(seasonalPack: value),
                    ));
                  }
                },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _currentTheme.advanced.dynamicTheme,
                decoration: const InputDecoration(
                  labelText: 'Dynamic Theme',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'none', child: Text('None')),
                  DropdownMenuItem(value: 'time_of_day', child: Text('Time of Day')),
                  DropdownMenuItem(value: 'wallpaper', child: Text('Wallpaper')),
                  DropdownMenuItem(value: 'seasonal', child: Text('Seasonal')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      advanced: _currentTheme.advanced.copyWith(dynamicTheme: value),
                    ));
                  }
                },
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ===== SIMPLIFIED TABS (CleverType Style) =====

  Widget _buildButtonTab() {
    // Use the new visual button style selector without AppBar (already in a tab)
    return button_styles.ButtonStyleSelectorScreen(
      currentTheme: _currentTheme,
      onThemeUpdated: (theme) {
        _updateTheme(theme);
      },
      showAppBar: false,
    );
  }

  String _resolveFontOptionId(String family) {
    // First, check if the family is already a font option ID (for new selections)
    for (final option in _fontOptions) {
      if (option.id == family) {
        return option.id;
      }
    }
    // Fall back to themeFamily matching (for backward compatibility)
    for (final option in _fontOptions) {
      if (option.themeFamily == family) {
        return option.id;
      }
    }
    return _customFontId;
  }

  Widget _buildEffectsTab() {
    // Use the new visual effect selector without AppBar (already in a tab)
    return effect_styles.EffectStyleSelectorScreen(
      currentTheme: _currentTheme,
      onThemeUpdated: (theme) {
        _updateTheme(theme);
      },
      showAppBar: false,
    );
  }

  Widget _buildEffectGrid() {
    final screenWidth = MediaQuery.of(context).size.width;
    final scaleFactor = (screenWidth / 240).clamp(0.8, 1.3);
    
    final gridSpacing = (12 * scaleFactor).clamp(4.0, 8.0);
    final crossAxisCount = screenWidth < 360 ? 4 : (screenWidth < 520 ? 5 : 5);
    
    final activeEffects = _currentTheme.effects.globalEffects;
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.zero,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: crossAxisCount,
        mainAxisSpacing: gridSpacing,
        crossAxisSpacing: gridSpacing,
        childAspectRatio: .55,
      ),
      itemCount: _effectOptions.length,
      itemBuilder: (context, index) {
        final option = _effectOptions[index];
        final isSelected = _isEffectSelected(option.id, activeEffects);
        return _EffectOptionTile(
          option: option,
          isSelected: isSelected,
          onTap: () => _handleEffectSelection(option.id, isSelected),
        );
      },
    );
  }

  bool _isEffectSelected(String effectId, List<String> activeEffects) {
    if (effectId == 'none') {
      return activeEffects.isEmpty;
    }
    return activeEffects.contains(effectId);
  }

  void _handleEffectSelection(String effectId, bool wasSelected) {
    if (effectId == 'none') {
      if (_currentTheme.effects.globalEffects.isEmpty) {
        return;
      }
      _updateTheme(_currentTheme.copyWith(
        effects: _currentTheme.effects.copyWith(globalEffects: const []),
      ));
      return;
    }

    if (wasSelected) {
      // Tapping an active effect clears back to none for quick disable.
      _updateTheme(_currentTheme.copyWith(
        effects: _currentTheme.effects.copyWith(globalEffects: const []),
      ));
      return;
    }

    _updateTheme(_currentTheme.copyWith(
      effects: _currentTheme.effects.copyWith(globalEffects: [effectId]),
    ));
  }


  Widget _buildFontTab() {
    // Use the new visual font selector without AppBar (already in a tab)
    return font_styles.FontStyleSelectorScreen(
      currentTheme: _currentTheme,
      onThemeUpdated: (theme) {
        _updateTheme(theme);
        // Update the selected font ID to match the new theme
        _selectedFontId = _resolveFontOptionId(theme.keys.font.family);
      },
      showAppBar: false,
    );
  }

  Widget _buildFontOptionTile(_FontOption option, bool isSelected) {
    final screenWidth = MediaQuery.of(context).size.width;
    final scaleFactor = (screenWidth / 360).clamp(0.8, 1.3);
    
    final circleSize = (68 * scaleFactor).clamp(56.0, 80.0);
    final borderWidth = isSelected ? (3 * scaleFactor).clamp(2.5, 4.0) : (2 * scaleFactor).clamp(1.5, 3.0);
    final checkSize = (22 * scaleFactor).clamp(18.0, 26.0);
    final checkIconSize = (14 * scaleFactor).clamp(12.0, 16.0);
    final checkOffset = (-6 * scaleFactor).clamp(-8.0, -4.0);
    final shadowBlur = (12 * scaleFactor).clamp(8.0, 16.0);
    final shadowOffset = (6 * scaleFactor).clamp(4.0, 8.0);
    final spacing = (6 * scaleFactor).clamp(4.0, 8.0);
    
    final previewStyle = option.previewStyleBuilder(isSelected).copyWith(
      color: AppColors.black,
    );
    return GestureDetector(
      onTap: () => _selectFontOption(option),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                width: circleSize,
                height: circleSize,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.white,
                  border: Border.all(
                    color: isSelected ? AppColors.secondary : Colors.transparent,
                    width: borderWidth,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.08),
                      blurRadius: shadowBlur,
                      offset: Offset(0, shadowOffset),
                    ),
                  ],
                ),
                child: Center(
                  child: Text(
                    option.previewText,
                    style: previewStyle,
                  ),
                ),
              ),
              if (isSelected)
                Positioned(
                  bottom: checkOffset,
                  right: checkOffset,
                  child: Container(
                    width: checkSize,
                    height: checkSize,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.secondary,
                    ),
                    child: Icon(Icons.check, color: Colors.white, size: checkIconSize),
                  ),
                ),
            ],
          ),
          SizedBox(height: spacing),
          // SizedBox(
          //   width: 68,
          //   child: Text(
          //     option.displayName,
          //     style: AppTextStyle.bodySmall.copyWith(
          //       color: isSelected ? AppColors.secondary : AppColors.grey,
          //       fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
          //     ),
          //     maxLines: 1,
          //     textAlign: TextAlign.center,
          //     overflow: TextOverflow.ellipsis,
          //   ),
          // ),
        ],
      ),
    );
  }

  /// Get themeFamily for a font option ID (for Android compatibility)
  String _getThemeFamilyForFontOptionId(String fontOptionId) {
    for (final option in _fontOptions) {
      if (option.id == fontOptionId) {
        return option.themeFamily;
      }
    }
    // Fallback for custom fonts or unknown IDs
    return fontOptionId.startsWith('font_') ? 'Roboto' : fontOptionId;
  }

  /// Get TextStyle for a font option ID (for Flutter preview rendering)
  static TextStyle getTextStyleForFontOptionId(String fontOptionId, double fontSize, FontWeight fontWeight) {
    for (final option in _fontOptions) {
      if (option.id == fontOptionId) {
        // Use the previewStyleBuilder to get the correct Google Font
        final baseStyle = option.previewStyleBuilder(false);
        return baseStyle.copyWith(
          fontSize: fontSize,
          fontWeight: fontWeight,
        );
      }
    }
    // Fallback to Roboto if font option not found
    return GoogleFonts.roboto(
      fontSize: fontSize,
      fontWeight: fontWeight,
    );
  }

  /// Instance method wrapper for convenience
  TextStyle _getTextStyleForFontOptionId(String fontOptionId, double fontSize, FontWeight fontWeight) {
    return getTextStyleForFontOptionId(fontOptionId, fontSize, fontWeight);
  }

  void _selectFontOption(_FontOption option) {
    // Store the font option ID instead of themeFamily to ensure unique identification
    // This allows proper selection indicator display in Flutter UI
    _updateTheme(_currentTheme.copyWith(
      keys: _currentTheme.keys.copyWith(
        font: _currentTheme.keys.font.copyWith(
          family: option.id, // Store unique font option ID for Flutter UI
          bold: false,
          italic: false,
        ),
      ),
    ));
  }

  Widget _buildFontSizeControl() {
    final fontSize = _currentTheme.keys.font.sizeSp.clamp(12.0, 24.0);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Font Size',
          style: AppTextStyle.bodySmall.copyWith(
            color: AppColors.black,
            fontWeight: FontWeight.w600,
          ),
        ),
        Slider(
          value: fontSize,
          min: 12.0,
          max: 24.0,
          divisions: 12,
          label: '${fontSize.round()}sp',
          onChanged: (value) {
            _updateTheme(_currentTheme.copyWith(
              keys: _currentTheme.keys.copyWith(
                font: _currentTheme.keys.font.copyWith(sizeSp: value),
              ),
            ));
          },
        ),
        Align(
          alignment: Alignment.centerRight,
          child: Text(
            '${fontSize.round()} sp',
            style: AppTextStyle.bodySmall.copyWith(
              color: AppColors.grey,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      ],
    );
  }


  void _showFullFontPicker() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return SizedBox(
          height: MediaQuery.of(context).size.height * 0.7,
          child: FontPicker(
            currentFont: _currentTheme.keys.font.family,
            availableFonts: _fontOptions.map((option) => option.themeFamily).toSet().toList(),
            onFontSelected: (font) {
              _updateTheme(_currentTheme.copyWith(
                keys: _currentTheme.keys.copyWith(
                  font: ThemeKeysFont(
                    family: font,
                    sizeSp: _currentTheme.keys.font.sizeSp,
                    bold: _currentTheme.keys.font.bold,
                    italic: _currentTheme.keys.font.italic,
                  ),
                ),
              ));
              Navigator.pop(context);
            },
          ),
        );
      },
    );
  }

  String? _customSoundName() {
    final path = _currentTheme.sounds.customUris['primary'];
    if (path == null || path.isEmpty) {
      return null;
    }
    final parts = path.split(Platform.pathSeparator);
    return parts.isNotEmpty ? parts.last : path;
  }

  Future<void> _pickCustomSound() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['wav', 'mp3', 'ogg', 'm4a'],
      );

      if (result == null || result.files.single.path == null) {
        return;
      }

      final docsDir = await getApplicationDocumentsDirectory();
      final soundsDir = Directory('${docsDir.path}/theme_sounds');
      if (!soundsDir.existsSync()) {
        soundsDir.createSync(recursive: true);
      }

      final originalName = result.files.single.name;
      final sanitizedName = originalName.replaceAll(RegExp(r'[^a-zA-Z0-9._-]'), '_');
      final destPath = '${soundsDir.path}/${DateTime.now().millisecondsSinceEpoch}_$sanitizedName';

      await File(result.files.single.path!).copy(destPath);

      _updateTheme(_currentTheme.copyWith(
        sounds: _currentTheme.sounds.copyWith(
          pack: 'custom',
          customUris: {
            ..._currentTheme.sounds.customUris,
            'primary': destPath,
          },
        ),
      ));

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Custom sound added')),
        );
      }
    } catch (e) {
      if (mounted) {
        _showError('Failed to import sound: $e');
      }
    }
  }

  void _clearCustomSound() {
    _updateTheme(_currentTheme.copyWith(
      sounds: _currentTheme.sounds.copyWith(
        pack: 'default',
        customUris: {},
      ),
    ));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Custom sound removed')),
      );
    }
  }

  Widget _buildCustomSoundPreview() {
    final fileName = _customSoundName() ?? 'No file selected';
    final fullPath = _currentTheme.sounds.customUris['primary'];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Custom sound: $fileName',
          style: AppTextStyle.bodyMedium.copyWith(fontWeight: FontWeight.w600),
        ),
        if (fullPath != null) ...[
          const SizedBox(height: 4),
          Text(
            fullPath,
            style: AppTextStyle.bodySmall.copyWith(color: Colors.grey[600]),
          ),
        ],
        const SizedBox(height: 12),
        Row(
          children: [
            ElevatedButton.icon(
              onPressed: _pickCustomSound,
              icon: const Icon(Icons.library_music),
              label: const Text('Replace sound'),
            ),
            const SizedBox(width: 12),
            TextButton(
              onPressed: _clearCustomSound,
              child: const Text('Remove'),
            ),
          ],
        ),
      ],
    );
  }
  Widget _buildSoundTab() {
    // Use the new visual sound selector without AppBar (already in a tab)
    return sound_styles.SoundStyleSelectorScreen(
      currentTheme: _currentTheme,
      onThemeUpdated: (theme) {
        _updateTheme(theme);
      },
      showAppBar: false,
    );
  }

  Future<void> _loadSelectedSound() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _selectedSound = prefs.getString('selected_sound') ?? 'click.mp3';
    } catch (e) {
      _selectedSound = 'click.mp3';
    }
  }

  Future<void> _setKeyboardSound(String file) async {
    try {
      // Save to SharedPreferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('selected_sound', file);
      
      // Call native method
      await _soundChannel.invokeMethod('setKeyboardSound', {'file': file});
    } catch (e) {
      debugPrint('Error setting keyboard sound: $e');
    }
  }

  Widget _buildAdaptiveTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection('Adaptive Background', [
            SwitchListTile(
              title: const Text('Enable Adaptive Background'),
              subtitle: const Text('Automatically adapt colors to your wallpaper'),
              value: _currentTheme.background.type == 'adaptive',
              onChanged: (value) {
                if (value) {
                  _updateTheme(_currentTheme.copyWith(
                    background: _currentTheme.background.copyWith(
                      type: 'adaptive',
                      adaptive: ThemeAdaptive(
                        enabled: true,
                        source: 'wallpaper',
                        materialYou: false,
                      ),
                    ),
                  ));
                } else {
                  _updateTheme(_currentTheme.copyWith(
                    background: _currentTheme.background.copyWith(
                      type: 'solid',
                      adaptive: null,
                    ),
                  ));
                }
              },
            ),
            if (_currentTheme.background.type == 'adaptive' && _currentTheme.background.adaptive != null) ...[
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _currentTheme.background.adaptive!.source,
                decoration: const InputDecoration(
                  labelText: 'Color Source',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'wallpaper', child: Text('Wallpaper')),
                  DropdownMenuItem(value: 'system', child: Text('System Theme')),
                  DropdownMenuItem(value: 'app', child: Text('App Theme')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      background: _currentTheme.background.copyWith(
                        adaptive: _currentTheme.background.adaptive!.copyWith(source: value),
                      ),
                    ));
                  }
                },
              ),
              const SizedBox(height: 16),
              SwitchListTile(
                title: const Text('Material You'),
                subtitle: const Text('Use Material You dynamic theming (Android 12+)'),
                value: _currentTheme.background.adaptive!.materialYou,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    background: _currentTheme.background.copyWith(
                      adaptive: _currentTheme.background.adaptive!.copyWith(materialYou: value),
                    ),
                  ));
                },
              ),
            ],
          ]),
        ],
      ),
    );
  }

  Widget _buildStickersTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildSection('Sticker Settings', [
            SwitchListTile(
              title: const Text('Enable Stickers'),
              subtitle: const Text('Add fun sticker overlays to your keyboard'),
              value: _currentTheme.stickers.enabled,
              onChanged: (value) {
                _updateTheme(_currentTheme.copyWith(
                  stickers: _currentTheme.stickers.copyWith(enabled: value),
                ));
              },
            ),
            if (_currentTheme.stickers.enabled) ...[
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _currentTheme.stickers.pack.isEmpty ? 'cute_animals' : _currentTheme.stickers.pack,
                decoration: const InputDecoration(
                  labelText: 'Sticker Pack',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'cute_animals', child: Text('🐱 Cute Animals')),
                  DropdownMenuItem(value: 'valentine', child: Text('💕 Valentine\'s Day')),
                  DropdownMenuItem(value: 'halloween', child: Text('🎃 Halloween')),
                  DropdownMenuItem(value: 'christmas', child: Text('🎄 Christmas')),
                  DropdownMenuItem(value: 'nature', child: Text('🌿 Nature')),
                  DropdownMenuItem(value: 'space', child: Text('🚀 Space')),
                  DropdownMenuItem(value: 'celebration', child: Text('🎉 Celebration')),
                  DropdownMenuItem(value: 'flowers', child: Text('🌸 Flowers')),
                  DropdownMenuItem(value: 'food', child: Text('🍕 Food')),
                  DropdownMenuItem(value: 'sports', child: Text('⚽ Sports')),
                  DropdownMenuItem(value: 'music', child: Text('🎵 Music')),
                  DropdownMenuItem(value: 'travel', child: Text('✈️ Travel')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      stickers: _currentTheme.stickers.copyWith(pack: value),
                    ));
                  }
                },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _currentTheme.stickers.position,
                decoration: const InputDecoration(
                  labelText: 'Position',
                  border: OutlineInputBorder(),
                ),
                items: const [
                  DropdownMenuItem(value: 'above', child: Text('Above Keyboard')),
                  DropdownMenuItem(value: 'below', child: Text('Below Keyboard')),
                  DropdownMenuItem(value: 'behind', child: Text('Behind Keys')),
                ],
                onChanged: (value) {
                  if (value != null) {
                    _updateTheme(_currentTheme.copyWith(
                      stickers: _currentTheme.stickers.copyWith(position: value),
                    ));
                  }
                },
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  const Text('Opacity:'),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Slider(
                      value: _currentTheme.stickers.opacity,
                      min: 0.1,
                      max: 1.0,
                      divisions: 9,
                      label: '${(_currentTheme.stickers.opacity * 100).round()}%',
                      onChanged: (value) {
                        _updateTheme(_currentTheme.copyWith(
                          stickers: _currentTheme.stickers.copyWith(opacity: value),
                        ));
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SwitchListTile(
                title: const Text('Animated'),
                subtitle: const Text('Enable sticker animations'),
                value: _currentTheme.stickers.animated,
                onChanged: (value) {
                  _updateTheme(_currentTheme.copyWith(
                    stickers: _currentTheme.stickers.copyWith(animated: value),
                  ));
                },
              ),
            ],
          ]),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _buildColorPicker(String label, Color color, ValueChanged<Color> onChanged) {
    return Row(
      children: [
        Expanded(
          child: Text(label),
        ),
        GestureDetector(
          onTap: () => _showColorPicker(color, onChanged),
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: color,
              border: Border.all(color: Colors.grey),
              borderRadius: BorderRadius.circular(8),
            ),
          ),
        ),
      ],
    );
  }

  void _showColorPicker(Color currentColor, ValueChanged<Color> onChanged) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Pick a Color'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Predefined colors (enhanced with theme-matching colors)
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  // Basic colors
                  Colors.red, Colors.pink, Colors.purple, Colors.blue,
                  Colors.cyan, Colors.teal, Colors.green, Colors.yellow,
                  Colors.orange, Colors.brown, Colors.grey, Colors.black,
                  Colors.white,
                  // Enhanced palette colors from themes
                  const Color(0xFF2196F3), const Color(0xFF4CAF50), const Color(0xFF9C27B0),
                  const Color(0xFFFF9800), const Color(0xFFE91E63), const Color(0xFF00BCD4),
                  const Color(0xFF03A9F4), const Color(0xFF1565C0), const Color(0xFFCDDC39),
                  const Color(0xFFFFC107), const Color(0xFF009688), const Color(0xFF3F51B5),
                  const Color(0xFF795548), const Color(0xFF673AB7), const Color(0xFF8BC34A),
                  const Color(0xFFFF5722), const Color(0xFFFFD700), const Color(0xFF0A0A0A),
                ].map((color) {
                  return GestureDetector(
                    onTap: () {
                      onChanged(color);
                      Navigator.of(context).pop();
                    },
                    child: Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: color,
                        border: Border.all(
                          color: color == currentColor ? Colors.blue : Colors.grey,
                          width: color == currentColor ? 3 : 1,
                        ),
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }
}

class _EffectOption {
  final String id;
  final String label;
  final IconData icon;
  final List<Color> gradientColors;
  final Color? iconColor;
  final String? assetIcon;
  final Color? backgroundColor;
  final Color? borderColor;

  const _EffectOption({
    required this.id,
    required this.label,
    required this.icon,
    required this.gradientColors,
    this.iconColor,
    this.assetIcon,
    this.backgroundColor,
    this.borderColor,
  });
}

class _FontOption {
  final String id;
  final String displayName;
  final String themeFamily;
  final String previewText;
  final TextStyle Function(bool isSelected) previewStyleBuilder;
  final bool bold;
  final bool italic;

  const _FontOption({
    required this.id,
    required this.displayName,
    required this.themeFamily,
    required this.previewText,
    required this.previewStyleBuilder,
    this.bold = false,
    this.italic = false,
  });
}

class _EffectOptionTile extends StatelessWidget {
  final _EffectOption option;
  final bool isSelected;
  final VoidCallback onTap;

  const _EffectOptionTile({
    required this.option,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final scaleFactor = (screenWidth / 360).clamp(0.8, 1.3);
    
    final circleSize = (72 * scaleFactor).clamp(60.0, 88.0);
    final borderWidth = (3 * scaleFactor).clamp(2.5, 4.0);
    final borderRadius = (20 * scaleFactor).clamp(16.0, 24.0);
    final iconSize = (28 * scaleFactor).clamp(22.0, 34.0);
    final shadowBlur = (16 * scaleFactor).clamp(12.0, 20.0);
    final shadowOffset = (8 * scaleFactor).clamp(6.0, 10.0);
    final checkSize = (14 * scaleFactor).clamp(12.0, 16.0);
    final checkPadding = (3 * scaleFactor).clamp(2.0, 4.0);
    final checkPosition = (6 * scaleFactor).clamp(4.0, 8.0);
    final spacing = (8 * scaleFactor).clamp(6.0, 10.0);
    
    final colorScheme = Theme.of(context).colorScheme;
    final gradient = option.gradientColors;
    final gradientPainter = gradient.length > 1 ? LinearGradient(colors: gradient) : null;
    final fallbackColor = option.backgroundColor ?? gradient.first;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(borderRadius),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            alignment: Alignment.center,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                curve: Curves.easeInOut,
                width: circleSize,
                height: circleSize,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: gradientPainter,
                  color: gradientPainter == null ? fallbackColor : null,
                  border: Border.all(
                    color: isSelected
                        ? colorScheme.primary
                        : (option.borderColor ?? Colors.transparent),
                    width: borderWidth,
                  ),
                  boxShadow: isSelected
                      ? [
                          BoxShadow(
                            color: colorScheme.primary.withOpacity(0.28),
                            blurRadius: shadowBlur,
                            offset: Offset(0, shadowOffset),
                          ),
                        ]
                      : [],
                ),
                child: option.assetIcon != null
                    ? Padding(
                        padding: const EdgeInsets.all(12),
                        child: Image.asset(
                          option.assetIcon!,
                          fit: BoxFit.contain,
                        ),
                      )
                    : Icon(
                        option.icon,
                        color: option.iconColor ?? Colors.white,
                        size: iconSize,
                      ),
              ),
              if (isSelected)
                Positioned(
                  right: checkPosition,
                  top: checkPosition,
                  child: Container(
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                    padding: EdgeInsets.all(checkPadding),
                    child: Icon(
                      Icons.check,
                      size: checkSize,
                      color: colorScheme.primary,
                    ),
                  ),
                ),
            ],
          ),
          SizedBox(height: spacing),
          // Text(
          //   option.label,
          //   maxLines: 1,
          //   overflow: TextOverflow.ellipsis,
          //   style: Theme.of(context).textTheme.bodySmall?.copyWith(
          //         fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
          //         color: isSelected
          //             ? colorScheme.primary
          //             : AppColors.black.withOpacity(0.8),
          //       ),
          //   textAlign: TextAlign.center,
          // ),
        ],
      ),
    );
  }
}

class _EditorTab {
  final IconData icon;
  final String label;
  final Widget Function() builder;

  const _EditorTab({
    required this.icon,
    required this.label,
    required this.builder,
  });
}

// Extension methods for copyWith functionality
extension ThemeGradientCopyWith on ThemeGradient {
  ThemeGradient copyWith({
    List<Color>? colors,
    String? orientation,
  }) {
    return ThemeGradient(
      colors: colors ?? this.colors,
      orientation: orientation ?? this.orientation,
    );
  }
}

extension ThemeToolbarCopyWith on ThemeToolbar {
  ThemeToolbar copyWith({
    bool? inheritFromKeys,
    Color? bg,
    Color? icon,
    double? heightDp,
    Color? activeAccent,
    String? iconPack,
  }) {
    return ThemeToolbar(
      inheritFromKeys: inheritFromKeys ?? this.inheritFromKeys,
      bg: bg ?? this.bg,
      icon: icon ?? this.icon,
      heightDp: heightDp ?? this.heightDp,
      activeAccent: activeAccent ?? this.activeAccent,
      iconPack: iconPack ?? this.iconPack,
    );
  }
}

extension ThemeSuggestionsCopyWith on ThemeSuggestions {
  ThemeSuggestions copyWith({
    bool? inheritFromKeys,
    Color? bg,
    Color? text,
    ThemeChip? chip,
    ThemeSuggestionsFont? font,
  }) {
    return ThemeSuggestions(
      inheritFromKeys: inheritFromKeys ?? this.inheritFromKeys,
      bg: bg ?? this.bg,
      text: text ?? this.text,
      chip: chip ?? this.chip,
      font: font ?? this.font,
    );
  }
}

extension ThemeChipCopyWith on ThemeChip {
  ThemeChip copyWith({
    Color? bg,
    Color? text,
    Color? pressed,
    double? radius,
    double? spacingDp,
  }) {
    return ThemeChip(
      bg: bg ?? this.bg,
      text: text ?? this.text,
      pressed: pressed ?? this.pressed,
      radius: radius ?? this.radius,
      spacingDp: spacingDp ?? this.spacingDp,
    );
  }
}

extension ThemeEffectsCopyWith on ThemeEffects {
  ThemeEffects copyWith({
    String? pressAnimation,
    List<String>? globalEffects,
    double? opacity,
  }) {
    return ThemeEffects(
      pressAnimation: pressAnimation ?? this.pressAnimation,
      globalEffects: globalEffects ?? this.globalEffects,
      opacity: opacity ?? this.opacity,
    );
  }
}

extension ThemeSoundsCopyWith on ThemeSounds {
  ThemeSounds copyWith({
    String? pack,
    Map<String, String>? customUris,
    double? volume,
  }) {
    return ThemeSounds(
      pack: pack ?? this.pack,
      customUris: customUris ?? this.customUris,
      volume: volume ?? this.volume,
    );
  }
}

extension ThemeStickersCopyWith on ThemeStickers {
  ThemeStickers copyWith({
    bool? enabled,
    String? pack,
    String? position,
    double? opacity,
    bool? animated,
  }) {
    return ThemeStickers(
      enabled: enabled ?? this.enabled,
      pack: pack ?? this.pack,
      position: position ?? this.position,
      opacity: opacity ?? this.opacity,
      animated: animated ?? this.animated,
    );
  }
}

extension ThemeAdvancedCopyWith on ThemeAdvanced {
  ThemeAdvanced copyWith({
    bool? livePreview,
    bool? galleryEnabled,
    bool? shareEnabled,
    String? dynamicTheme,
    String? seasonalPack,
    bool? materialYouExtract,
  }) {
    return ThemeAdvanced(
      livePreview: livePreview ?? this.livePreview,
      galleryEnabled: galleryEnabled ?? this.galleryEnabled,
      shareEnabled: shareEnabled ?? this.shareEnabled,
      dynamicTheme: dynamicTheme ?? this.dynamicTheme,
      seasonalPack: seasonalPack ?? this.seasonalPack,
      materialYouExtract: materialYouExtract ?? this.materialYouExtract,
    );
  }
}

/// Background Image model for theme gallery
class BackgroundImage {
  final String id;
  final String category;
  final String imageUrl;
  final IconData icon;

  BackgroundImage({
    required this.id,
    required this.category,
    required this.imageUrl,
    required this.icon,
  });
}

/// Real functional keyboard widget that shows the actual system keyboard
class _RealKeyboardWidget extends StatefulWidget {
  final KeyboardThemeV2 theme;
  final ValueChanged<KeyboardThemeV2> onThemeChanged;

  const _RealKeyboardWidget({
    super.key,
    required this.theme,
    required this.onThemeChanged,
  });

  @override
  State<_RealKeyboardWidget> createState() => _RealKeyboardWidgetState();
}

class _RealKeyboardWidgetState extends State<_RealKeyboardWidget> {
  late TextEditingController _textController;
  late FocusNode _focusNode;
  bool _keyboardVisible = false;

  @override
  void initState() {
    super.initState();
    _textController = TextEditingController();
    _focusNode = FocusNode();
    
    // Apply theme immediately
    _applyTheme();
    
    // Auto-focus to show keyboard
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _focusNode.requestFocus();
        setState(() {
          _keyboardVisible = true;
        });
      }
    });
    
    _focusNode.addListener(() {
      setState(() {
        _keyboardVisible = _focusNode.hasFocus;
      });
    });
  }

  @override
  void didUpdateWidget(_RealKeyboardWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.theme != widget.theme || 
        oldWidget.theme.keys.font.family != widget.theme.keys.font.family) {
      // Force rebuild when theme or font changes
      if (mounted) {
        setState(() {});
      }
      _applyTheme();
    }
  }

  Future<void> _applyTheme() async {
    try {
      // Convert font option ID back to themeFamily for Android compatibility
      final fontFamily = widget.theme.keys.font.family;
      String androidFontFamily = fontFamily;
      if (fontFamily.startsWith('font_')) {
        // Look up themeFamily for font option ID
        for (final option in _ThemeEditorScreenV2State._fontOptions) {
          if (option.id == fontFamily) {
            androidFontFamily = option.themeFamily;
            break;
          }
        }
        // Fallback if not found
        if (androidFontFamily == fontFamily) {
          androidFontFamily = 'Roboto';
        }
      }
      
      // Force inheritance for seamless experience
      final seamlessTheme = widget.theme.copyWith(
        toolbar: widget.theme.toolbar.copyWith(inheritFromKeys: true),
        suggestions: widget.theme.suggestions.copyWith(inheritFromKeys: true),
        keys: widget.theme.keys.copyWith(
          font: widget.theme.keys.font.copyWith(
            family: androidFontFamily, // Convert to themeFamily for Android
          ),
        ),
      );
      await ThemeManagerV2.saveThemeV2(seamlessTheme);
      widget.onThemeChanged(seamlessTheme);
      
      // Force widget rebuild to show updated theme
      if (mounted) {
        setState(() {});
      }
    } catch (e) {
      debugPrint('Error applying theme: $e');
    }
  }

  @override
  void dispose() {
    _textController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      color: widget.theme.background.color ?? Colors.grey[900],
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Input field to trigger keyboard
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: widget.theme.keys.bg,
                border: Border(
                  top: BorderSide(
                    color: widget.theme.keys.border.enabled 
                        ? widget.theme.keys.border.color 
                        : Colors.transparent,
                    width: widget.theme.keys.border.widthDp,
                  ),
                ),
              ),
              child: TextField(
                controller: _textController,
                focusNode: _focusNode,
                autofocus: true,
                style: _ThemeEditorScreenV2State.getTextStyleForFontOptionId(
                  widget.theme.keys.font.family,
                  widget.theme.keys.font.sizeSp,
                  widget.theme.keys.font.bold 
                      ? FontWeight.bold 
                      : FontWeight.normal,
                ).copyWith(
                  color: widget.theme.keys.text,
                  fontStyle: widget.theme.keys.font.italic 
                      ? FontStyle.italic 
                      : FontStyle.normal,
                ),
                decoration: InputDecoration(
                  hintText: 'Try your keyboard here...',
                  hintStyle: _ThemeEditorScreenV2State.getTextStyleForFontOptionId(
                    widget.theme.keys.font.family,
                    widget.theme.keys.font.sizeSp,
                    widget.theme.keys.font.bold 
                        ? FontWeight.bold 
                        : FontWeight.normal,
                  ).copyWith(
                    color: widget.theme.keys.text.withOpacity(0.5),
                    fontStyle: widget.theme.keys.font.italic 
                        ? FontStyle.italic 
                        : FontStyle.normal,
                  ),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(vertical: 8),
                ),
                maxLines: null,
                textCapitalization: TextCapitalization.sentences,
              ),
            ),
            // Spacer to push keyboard to bottom
            if (!_keyboardVisible)
              Row(children: [
                Text('Hello'),
                SizedBox(
                height: MediaQuery.of(context).viewInsets.bottom > 0 
                    ? 0 
                    : 300, // Approximate keyboard height when not visible
              ),])
          ],
        ),
      ),
    );
  }
}
