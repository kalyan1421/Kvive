import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';
import 'package:ai_keyboard/services/keyboard_cloud_sync.dart';
import 'dart:async';

class KeyboardSettingsScreen extends StatefulWidget {
  const KeyboardSettingsScreen({super.key});

  @override
  State<KeyboardSettingsScreen> createState() => _KeyboardSettingsScreenState();
}

class _KeyboardSettingsScreenState extends State<KeyboardSettingsScreen> {
  // MethodChannel for keyboard communication
  static const _channel = MethodChannel('ai_keyboard/config');

  // Debounce timers
  Timer? _saveDebounceTimer;
  Timer? _notifyDebounceTimer;

  // General Settings
  bool numberRow = false;
  bool hintedNumberRow = false;
  bool hintedSymbols = false;
  bool showUtilityKey = true;
  String utilityKeyAction = 'emoji';
  bool displayLanguageOnSpace = true;
  double portraitFontSize = 100.0;
  double landscapeFontSize = 100.0;

  // Layout Settings
  bool borderlessKeys = false;
  bool oneHandedMode = false;
  String oneHandedSide = 'right';
  double oneHandedModeWidth = 87.0;
  bool landscapeFullScreenInput = true;
  double keyboardWidth = 28.0;
  double keyboardHeight = 28.0;
  double verticalKeySpacing = 5.0;
  double horizontalKeySpacing = 2.0;
  double portraitBottomOffset = 1.0;
  double landscapeBottomOffset = 2.0;

  // Key Press Settings
  bool popupVisibility = false; // ✅ Default OFF as requested
  double longPressDelay = 200.0;

  // Feature Settings
  bool _aiSuggestionsEnabled = true;
  bool _swipeTypingEnabled = true;
  bool _keyPreviewEnabled = false;
  bool _shiftFeedbackEnabled = false;
  bool _personalizedSuggestionsEnabled = true;
  bool _autoCorrectEnabled = true; // ✅ NEW: Auto-Correct toggle
  static const double _defaultPortraitHeightPercent = 28.0;
  static const double _defaultMinGridHeightDp = 234.0;
  static const double _minGridHeightDpFloor = 140.0;
  static const double _minGridHeightDpCeil = 520.0;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _saveDebounceTimer?.cancel();
    _notifyDebounceTimer?.cancel();
    super.dispose();
  }

  /// Load settings from SharedPreferences
  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();

    setState(() {
      // General Settings
      numberRow = prefs.getBool('keyboard.numberRow') ?? false;
      hintedNumberRow = prefs.getBool('keyboard.hintedNumberRow') ?? false;
      hintedSymbols = prefs.getBool('keyboard.hintedSymbols') ?? false;
      showUtilityKey = prefs.getBool('keyboard.showUtilityKey') ?? true;
      utilityKeyAction =
          prefs.getString('keyboard.utilityKeyAction') ?? 'emoji';
      displayLanguageOnSpace =
          prefs.getBool('keyboard.showLanguageOnSpace') ?? true;
      portraitFontSize =
          (prefs.getDouble('keyboard.fontScalePortrait') ?? 1.0) * 100.0;
      landscapeFontSize =
          (prefs.getDouble('keyboard.fontScaleLandscape') ?? 1.0) * 100.0;

      // Layout Settings
      borderlessKeys = prefs.getBool('keyboard.borderlessKeys') ?? false;
      oneHandedMode = prefs.getBool('keyboard.oneHanded.enabled') ?? false;
      oneHandedSide = prefs.getString('keyboard.oneHanded.side') ?? 'right';
      oneHandedModeWidth =
          (prefs.getDouble('keyboard.oneHanded.widthPct') ?? 0.87) * 100.0;
      landscapeFullScreenInput =
          prefs.getBool('keyboard.landscapeFullscreen') ?? true;
      final portraitPercent =
          prefs.getInt('flutter.keyboard.heightPercentPortrait') ??
          prefs.getInt('keyboard_height_percent') ??
          28;
      keyboardWidth = portraitPercent.toDouble();

      final landscapePercent =
          prefs.getInt('flutter.keyboard.heightPercentLandscape') ??
          portraitPercent;
      keyboardHeight = landscapePercent.toDouble();
      verticalKeySpacing =
          prefs.getInt('keyboard.keySpacingVdp')?.toDouble() ?? 5.0;
      horizontalKeySpacing =
          prefs.getInt('keyboard.keySpacingHdp')?.toDouble() ?? 2.0;
      portraitBottomOffset =
          prefs.getInt('keyboard.bottomOffsetPortraitDp')?.toDouble() ?? 1.0;
      landscapeBottomOffset =
          prefs.getInt('keyboard.bottomOffsetLandscapeDp')?.toDouble() ?? 2.0;

      // Key Press Settings
      popupVisibility =
          prefs.getBool('keyboard.popupPreview') ?? false; // ✅ Default OFF
      longPressDelay =
          prefs.getInt('keyboard.longPressDelayMs')?.toDouble() ?? 200.0;

      // Feature Settings
      _aiSuggestionsEnabled = prefs.getBool('ai_suggestions') ?? true;
      _swipeTypingEnabled = prefs.getBool('swipe_typing') ?? true;
      _keyPreviewEnabled = prefs.getBool('key_preview_enabled') ?? false;
      _shiftFeedbackEnabled = prefs.getBool('show_shift_feedback') ?? false;
      _personalizedSuggestionsEnabled =
          prefs.getBool('personalized_enabled') ?? true;
      _autoCorrectEnabled =
          prefs.getBool('auto_correct') ?? true; // ✅ Load auto-correct setting
    });
  }

  /// Save settings with proper debouncing
  Future<void> _saveSettings({bool immediate = false}) async {
    // Cancel existing timer
    _saveDebounceTimer?.cancel();

    if (immediate) {
      await _performSave();
    } else {
      // Debounce for 500ms
      _saveDebounceTimer = Timer(const Duration(milliseconds: 500), () {
        _performSave();
      });
    }
  }

  /// Actually perform the save operation
  Future<void> _performSave() async {
    final prefs = await SharedPreferences.getInstance();

    // Enforce mutual exclusivity
    if (numberRow && hintedNumberRow) {
      hintedNumberRow = false;
    }

    // Clamp ranges
    portraitFontSize = portraitFontSize.clamp(80.0, 130.0);
    landscapeFontSize = landscapeFontSize.clamp(80.0, 130.0);
    keyboardWidth = keyboardWidth.clamp(20.0, 40.0);
    keyboardHeight = keyboardHeight.clamp(20.0, 40.0);
    verticalKeySpacing = verticalKeySpacing.clamp(0.0, 8.0);
    horizontalKeySpacing = horizontalKeySpacing.clamp(0.0, 8.0);
    longPressDelay = longPressDelay.clamp(150.0, 600.0);
    oneHandedModeWidth = oneHandedModeWidth.clamp(70.0, 100.0);
    final portraitMinHeightDp = _computePortraitMinHeightDp();

    // Save all settings
    await prefs.setBool('keyboard.numberRow', numberRow);
    await prefs.setBool('keyboard.hintedNumberRow', hintedNumberRow);
    await prefs.setBool('keyboard.hintedSymbols', hintedSymbols);
    await prefs.setBool('keyboard.showUtilityKey', showUtilityKey);
    // Also save utility key action settings for Android to read
    await prefs.setBool('keyboard_settings.show_utility_key', showUtilityKey);
    await prefs.setString(
      'flutter.keyboard.utilityKeyAction',
      utilityKeyAction,
    );
    await prefs.setBool(
      'flutter.keyboard.showLanguageOnSpace',
      displayLanguageOnSpace,
    );
    await prefs.setString('keyboard.utilityKeyAction', utilityKeyAction);
    await prefs.setBool('keyboard.showLanguageOnSpace', displayLanguageOnSpace);
    await prefs.setBool('keyboard_settings.number_row', numberRow);
    await prefs.setBool('keyboard_settings.hinted_number_row', hintedNumberRow);
    await prefs.setBool('keyboard_settings.hinted_symbols', hintedSymbols);
    await prefs.setBool('keyboard_settings.show_utility_key', showUtilityKey);
    await prefs.setBool(
      'keyboard_settings.display_language_on_space',
      displayLanguageOnSpace,
    );
    await prefs.setBool('flutter.keyboard_settings.number_row', numberRow);
    await prefs.setBool(
      'flutter.keyboard_settings.hinted_number_row',
      hintedNumberRow,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.hinted_symbols',
      hintedSymbols,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.show_utility_key',
      showUtilityKey,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.display_language_on_space',
      displayLanguageOnSpace,
    );
    // Save font scale (Flutter plugin prepends "flutter." automatically)
    final portraitScale = (portraitFontSize / 100.0);
    final landscapeScale = (landscapeFontSize / 100.0);
    await prefs.setDouble('keyboard.fontScalePortrait', portraitScale);
    await prefs.setDouble('keyboard.fontScaleLandscape', landscapeScale);
    await prefs.setDouble(
      'keyboard_settings.portrait_font_size',
      portraitFontSize,
    );
    await prefs.setDouble(
      'keyboard_settings.landscape_font_size',
      landscapeFontSize,
    );
    await prefs.setDouble('flutter.keyboard.fontScalePortrait', portraitScale);
    await prefs.setDouble(
      'flutter.keyboard.fontScaleLandscape',
      landscapeScale,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.portrait_font_size',
      portraitFontSize,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.landscape_font_size',
      landscapeFontSize,
    );

    await prefs.setBool('keyboard.borderlessKeys', borderlessKeys);
    await prefs.setBool('keyboard.oneHanded.enabled', oneHandedMode);
    await prefs.setString('keyboard.oneHanded.side', oneHandedSide);
    await prefs.setDouble(
      'keyboard.oneHanded.widthPct',
      oneHandedModeWidth / 100.0,
    );
    await prefs.setBool(
      'keyboard.landscapeFullscreen',
      landscapeFullScreenInput,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.borderless_keys',
      borderlessKeys,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.one_handed_mode',
      oneHandedMode,
    );
    await prefs.setString(
      'flutter.keyboard_settings.one_handed_side',
      oneHandedSide,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.one_handed_mode_width',
      oneHandedModeWidth,
    );
    await prefs.setBool(
      'flutter.keyboard_settings.landscape_full_screen_input',
      landscapeFullScreenInput,
    );
    // ✅ FIX: Save keyboard height with orientation-specific keys
    // Note: keyboardWidth = Portrait height, keyboardHeight = Landscape height (confusing naming in UI)
    await prefs.setDouble('keyboard.scaleX', 1.0); // Keep width at 100%
    await prefs.setDouble(
      'keyboard.scaleY',
      1.0,
    ); // Legacy clients expect normalized scale
    await prefs.setDouble('keyboard_settings.keyboard_width', keyboardWidth);
    await prefs.setDouble('keyboard_settings.keyboard_height', keyboardHeight);
    await prefs.setDouble('flutter.keyboard.scaleYPortrait', 1.0);
    await prefs.setDouble('flutter.keyboard.scaleYLandscape', 1.0);
    await prefs.setDouble(
      'flutter.keyboard_settings.keyboard_width',
      keyboardWidth,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.keyboard_height',
      keyboardHeight,
    );
    await prefs.setDouble('keyboard.minGridHeightDp', portraitMinHeightDp);
    await prefs.setDouble(
      'flutter.keyboard.minGridHeightDp',
      portraitMinHeightDp,
    );
    await prefs.setInt('keyboard.keySpacingVdp', verticalKeySpacing.round());
    await prefs.setInt('keyboard.keySpacingHdp', horizontalKeySpacing.round());
    await prefs.setInt(
      'flutter.keyboard.keySpacingVdp',
      verticalKeySpacing.round(),
    );
    await prefs.setInt(
      'flutter.keyboard.keySpacingHdp',
      horizontalKeySpacing.round(),
    );
    await prefs.setDouble(
      'keyboard_settings.vertical_key_spacing',
      verticalKeySpacing,
    );
    await prefs.setDouble(
      'keyboard_settings.horizontal_key_spacing',
      horizontalKeySpacing,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.vertical_key_spacing',
      verticalKeySpacing,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.horizontal_key_spacing',
      horizontalKeySpacing,
    );
    await prefs.setInt(
      'keyboard.bottomOffsetPortraitDp',
      portraitBottomOffset.round(),
    );
    await prefs.setInt(
      'keyboard.bottomOffsetLandscapeDp',
      landscapeBottomOffset.round(),
    );
    await prefs.setInt(
      'flutter.keyboard.bottomOffsetPortraitDp',
      portraitBottomOffset.round(),
    );
    await prefs.setInt(
      'flutter.keyboard.bottomOffsetLandscapeDp',
      landscapeBottomOffset.round(),
    );
    await prefs.setInt(
      'flutter.keyboard.bottomOffsetDp',
      portraitBottomOffset.round(),
    );
    await prefs.setDouble(
      'keyboard_settings.portrait_bottom_offset',
      portraitBottomOffset,
    );
    await prefs.setDouble(
      'keyboard_settings.landscape_bottom_offset',
      landscapeBottomOffset,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.portrait_bottom_offset',
      portraitBottomOffset,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.landscape_bottom_offset',
      landscapeBottomOffset,
    );
    await prefs.setInt(
      'flutter.keyboard.heightPercentPortrait',
      keyboardWidth.round(),
    );
    await prefs.setInt(
      'flutter.keyboard.heightPercentLandscape',
      keyboardHeight.round(),
    );
    await prefs.setInt('flutter.keyboard.heightPercent', keyboardWidth.round());
    await prefs.setInt('keyboard_height_percent', keyboardWidth.round());
    await prefs.setBool('keyboard.instantLongPressSelectFirst', true);
    await prefs.setBool('flutter.keyboard.instantLongPressSelectFirst', true);

    await prefs.setBool('keyboard.popupPreview', popupVisibility);
    await prefs.setInt('keyboard.longPressDelayMs', longPressDelay.round());
    await prefs.setInt(
      'flutter.keyboard.longPressDelayMs',
      longPressDelay.round(),
    );
    await prefs.setBool('keyboard_settings.popup_visibility', popupVisibility);
    await prefs.setDouble('keyboard_settings.long_press_delay', longPressDelay);
    await prefs.setBool(
      'flutter.keyboard_settings.popup_visibility',
      popupVisibility,
    );
    await prefs.setDouble(
      'flutter.keyboard_settings.long_press_delay',
      longPressDelay,
    );

    await prefs.setBool('ai_suggestions', _aiSuggestionsEnabled);
    await prefs.setBool('swipe_typing', _swipeTypingEnabled);
    await prefs.setBool('key_preview_enabled', _keyPreviewEnabled);
    await prefs.setBool('show_shift_feedback', _shiftFeedbackEnabled);
    await prefs.setBool(
      'personalized_enabled',
      _personalizedSuggestionsEnabled,
    );
    await prefs.setBool(
      'auto_correct',
      _autoCorrectEnabled,
    ); // ✅ Save auto-correct setting

    debugPrint(
      '✅ Keyboard settings saved (fontScale portrait=${portraitScale.toStringAsFixed(2)}, landscape=${landscapeScale.toStringAsFixed(2)})',
    );

    await _applyOneHandedModeNative();

    // Send to native keyboard
    await _sendSettingsToKeyboard();

    // Sync to Firebase for cross-device sync
    await _syncToFirebase();

    // Notify keyboard (debounced)
    _debouncedNotifyKeyboard();
  }

  double _computePortraitMinHeightDp() {
    final basePercent =
        _defaultPortraitHeightPercent == 0 ? 1.0 : _defaultPortraitHeightPercent;
    final effectivePercent = keyboardWidth <= 0 ? basePercent : keyboardWidth;
    final rawDp = _defaultMinGridHeightDp * (effectivePercent / basePercent);
    final num clamped = rawDp.clamp(
      _minGridHeightDpFloor,
      _minGridHeightDpCeil,
    );
    return clamped.toDouble();
  }

  /// Sync settings to Firebase for cross-device sync
  Future<void> _syncToFirebase() async {
    try {
      await KeyboardCloudSync.upsert({
        'popupEnabled': popupVisibility,
        'aiSuggestions': _aiSuggestionsEnabled,
        'autocorrect': _autoCorrectEnabled,
        'emojiSuggestions': true,
        'nextWordPrediction': true,
        'clipboardSuggestions': {
          'enabled': true, // ✅ Clipboard ON
          'windowSec': 60,
          'historyItems': 20, // ✅ Min 20 items
        },
        'dictionaryEnabled': true, // ✅ Dictionary ON
        'autoCapitalization': true,
        'doubleSpacePeriod': true,
        // Advanced settings
        'numberRow': numberRow,
        'hintedSymbols': hintedSymbols,
        'showUtilityKey': showUtilityKey,
        'swipeTyping': _swipeTypingEnabled,
        'keyPreview': _keyPreviewEnabled,
        'personalizedSuggestions': _personalizedSuggestionsEnabled,
      });
      debugPrint('✅ Settings synced to Firebase for cross-device sync');
    } catch (e) {
      debugPrint('⚠ Failed to sync to Firebase: $e');
      // Don't block user if Firebase fails
    }
  }

  /// Send settings to native keyboard
  Future<void> _sendSettingsToKeyboard() async {
    try {
      await _channel.invokeMethod('updateSettings', {
        'theme': 'default',
        'popupEnabled': popupVisibility,
        'aiSuggestions': _aiSuggestionsEnabled,
        'autoCorrect': _autoCorrectEnabled, // ✅ Send auto-correct setting
        'swipeTyping': _swipeTypingEnabled,
        'keyPreview': _keyPreviewEnabled,
        'shiftFeedback': _shiftFeedbackEnabled,
        'showNumberRow': numberRow,
        'showUtilityKey': showUtilityKey, // ✅ Send utility key setting
      });
    } catch (e) {
      debugPrint('⚠ Error sending settings: $e');
    }
  }

  Future<void> _applyOneHandedModeNative() async {
    final enabled = oneHandedMode && oneHandedSide != 'off';
    final side = enabled ? oneHandedSide : 'right';
    final widthFraction = ((oneHandedModeWidth / 100.0).clamp(
      0.6,
      0.9,
    )).toDouble();

    try {
      await _channel.invokeMethod('setOneHandedMode', {
        'enabled': enabled,
        'side': side,
        'width': widthFraction,
      });
    } catch (e) {
      debugPrint('⚠ Error applying one-handed mode: $e');
    }
  }

  /// Notify keyboard with debounce
  void _debouncedNotifyKeyboard() {
    _notifyDebounceTimer?.cancel();
    _notifyDebounceTimer = Timer(const Duration(milliseconds: 300), () {
      _notifyKeyboard();
    });
  }

  /// Notify keyboard via MethodChannel
  Future<void> _notifyKeyboard() async {
    try {
      await _channel.invokeMethod('notifyConfigChange');
      debugPrint('✓ Notified keyboard');
      _showSuccessSnackBar();
    } catch (e) {
      debugPrint('⚠ Failed to notify: $e');
    }
  }

  /// Show success snackbar
  void _showSuccessSnackBar() {
    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: const [
            Icon(Icons.check_circle, color: Colors.white, size: 20),
            SizedBox(width: 8),
            Expanded(child: Text('Settings saved successfully!')),
          ],
        ),
        backgroundColor: Colors.green,
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  /// Show dialog to confirm clearing learned words
  void _showClearWordsDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          title: Row(
            children: [
              const Icon(Icons.warning_amber, color: Colors.orange, size: 24),
              const SizedBox(width: 8),
              Text(
                'Clear Learned Words',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          content: Text(
            'This will permanently delete all words the keyboard has learned from your typing patterns. This action cannot be undone.\n\nAre you sure you want to continue?',
            style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(
                'Cancel',
                style: AppTextStyle.labelLarge.copyWith(color: AppColors.grey),
              ),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.of(context).pop();
                _clearLearnedWords();
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              child: Text(
                'Clear All',
                style: AppTextStyle.labelLarge.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        );
      },
    );
  }

  /// Clear all learned words via MethodChannel
  Future<void> _clearLearnedWords() async {
    try {
      await _channel.invokeMethod('clearLearnedWords');

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: const [
              Icon(Icons.check_circle, color: Colors.white, size: 20),
              SizedBox(width: 8),
              Expanded(child: Text('Learned words cleared successfully!')),
            ],
          ),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 2),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    } catch (e) {
      debugPrint('⚠ Error clearing learned words: $e');

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Row(
            children: const [
              Icon(Icons.error, color: Colors.white, size: 20),
              SizedBox(width: 8),
              Expanded(child: Text('Failed to clear learned words')),
            ],
          ),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Keyboard Settings',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        actionsPadding: const EdgeInsets.only(right: 16),
        actions: [
          Stack(
            children: [
              const Icon(Icons.notifications, color: AppColors.white, size: 24),
              Positioned(
                right: 0,
                top: 0,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: AppColors.secondary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      backgroundColor: AppColors.white,

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // _buildToggleSetting(
            //   title: 'Auto-Correct',
            //   description: 'Automatically fix typos as you type (e.g., "teh" → "the")',
            //   value: _autoCorrectEnabled,
            //   onChanged: (value) {
            //     setState(() => _autoCorrectEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),
            // General Section
            _buildSectionHeader('General'),
            const SizedBox(height: 12),
            _buildToggleSetting(
              title: 'Number row',
              description: 'Show a number row above the character layout',
              value: numberRow,
              onChanged: (value) {
                setState(() {
                  numberRow = value;
                  if (value) hintedNumberRow = false;
                });
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildToggleSetting(
              title: 'Hinted number row',
              description: numberRow
                  ? 'Disabled (conflicts with number row)'
                  : 'Show number hints on letter keys',
              value: hintedNumberRow,
              onChanged: numberRow
                  ? null
                  : (value) {
                      setState(() => hintedNumberRow = value);
                      _saveSettings(immediate: true);
                    },
            ),
            const SizedBox(height: 8),
            _buildToggleSetting(
              title: 'Hinted symbols',
              description: 'Show symbol hints on letter keys',
              value: hintedSymbols,
              onChanged: (value) {
                setState(() => hintedSymbols = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildToggleSetting(
              title: 'Show utility key',
              description: 'Shows a configurable utility key next to space bar',
              value: showUtilityKey,
              onChanged: (value) {
                setState(() => showUtilityKey = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildToggleSetting(
              title: 'Display language on spacebar',
              description: 'Show current language name on spacebar',
              value: displayLanguageOnSpace,
              onChanged: (value) {
                setState(() => displayLanguageOnSpace = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildDialogSetting(
              title: 'Key Font Size',
              portraitValue: portraitFontSize,
              landscapeValue: landscapeFontSize,
              portraitLabel: 'Portrait',
              landscapeLabel: 'Landscape',
              onTap: () => _showFontSizeDialog(),
            ),
            const SizedBox(height: 24),

            // Layout Section
            _buildSectionHeader('Layout'),
            const SizedBox(height: 12),
            _buildToggleSetting(
              title: 'Borderless keys',
              description: 'Remove key borders for cleaner look',
              value: borderlessKeys,
              onChanged: (value) {
                setState(() => borderlessKeys = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildToggleSetting(
              title: 'One-handed mode',
              description: 'Dock keyboard to left or right side',
              value: oneHandedMode,
              onChanged: (value) {
                setState(() => oneHandedMode = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            if (oneHandedMode) ...[
              _buildDialogSetting(
                title: 'One-handed mode',
                description: oneHandedSide == 'left'
                    ? 'Left-handed mode'
                    : oneHandedSide == 'right'
                    ? 'Right-handed mode'
                    : 'Off',
                onTap: () => _showOneHandedModeDialog(),
              ),
              const SizedBox(height: 8),
              _buildSliderSetting(
                title: 'One-handed mode keyboard width',
                portraitValue: oneHandedModeWidth,
                onPortraitChanged: (value) {
                  setState(() => oneHandedModeWidth = value);
                  _saveSettings();
                },
                min: 60.0,
                max: 95.0,
                unit: '%',
                portraitLabel: 'Width',
                showLandscape: false,
              ),
              const SizedBox(height: 8),
            ],
            _buildToggleSetting(
              title: 'Landscape full screen input',
              description: 'Expand keyboard to full height in landscape',
              value: landscapeFullScreenInput,
              onChanged: (value) {
                setState(() => landscapeFullScreenInput = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildDialogSetting(
              title: 'Keyboard Height',
              portraitValue: keyboardWidth,
              landscapeValue: keyboardHeight,
              portraitLabel: 'Portrait',
              landscapeLabel: 'Landscape',
              onTap: () => _showKeyboardHeightDialog(),
            ),
            const SizedBox(height: 8),
            _buildDialogSetting(
              title: 'Key spacing',
              portraitValue: verticalKeySpacing,
              landscapeValue: horizontalKeySpacing,
              portraitLabel: 'Vertical',
              landscapeLabel: 'Horizontal',
              onTap: () => _showKeySpacingDialog(),
            ),
            const SizedBox(height: 8),
            _buildSliderSetting(
              title: 'Bottom offset',
              portraitValue: portraitBottomOffset,
              landscapeValue: landscapeBottomOffset,
              onPortraitChanged: (value) {
                setState(() => portraitBottomOffset = value);
                _saveSettings();
              },
              onLandscapeChanged: (value) {
                setState(() => landscapeBottomOffset = value);
                _saveSettings();
              },
              min: 0.0,
              max: 10.0,
              unit: ' dp',
            ),
            const SizedBox(height: 24),

            // Key Press Section
            _buildSectionHeader('Key Press'),
            const SizedBox(height: 12),
            _buildToggleSetting(
              title: 'Popup Visibility',
              description: 'Show popup preview when pressing keys',
              value: popupVisibility,
              onChanged: (value) {
                setState(() => popupVisibility = value);
                _saveSettings(immediate: true);
              },
            ),
            const SizedBox(height: 8),
            _buildSliderSetting(
              title: 'Long Press Delay',
              portraitValue: longPressDelay,
              onPortraitChanged: (value) {
                setState(() => longPressDelay = value);
                _saveSettings();
              },
              min: 100.0,
              max: 1000.0,
              unit: 'ms',
              portraitLabel: 'Delay',
              showLandscape: false,
            ),
            const SizedBox(height: 24),

            // Features Section
            // _buildSectionHeader('Features'),
            // const SizedBox(height: 12),
            // _buildToggleSetting(
            //   title: 'AI Suggestions',
            //   description: 'Get smart text predictions and corrections',
            //   value: _aiSuggestionsEnabled,
            //   onChanged: (value) {
            //     setState(() => _aiSuggestionsEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),
            // const SizedBox(height: 8),

            // const SizedBox(height: 8),
            // _buildToggleSetting(
            //   title: 'Swipe Typing',
            //   description: 'Swipe across letters to form words',
            //   value: _swipeTypingEnabled,
            //   onChanged: (value) {
            //     setState(() => _swipeTypingEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),
            // const SizedBox(height: 8),
            // _buildToggleSetting(
            //   title: 'Vibration Feedback',
            //   description: 'Haptic feedback when typing',
            //   value: _vibrationEnabled,
            //   onChanged: (value) {
            //     setState(() => _vibrationEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),
            // const SizedBox(height: 8),
            // _buildToggleSetting(
            //   title: 'Sound Feedback',
            //   description: 'Play typing sounds when pressing keys',
            //   value: _soundEnabled,
            //   onChanged: (value) {
            //     setState(() => _soundEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),

            // const SizedBox(height: 8),
            // _buildToggleSetting(
            //   title: 'Personalized Suggestions',
            //   description: 'Learn from your typing to provide better suggestions',
            //   value: _personalizedSuggestionsEnabled,
            //   onChanged: (value) {
            //     setState(() => _personalizedSuggestionsEnabled = value);
            //     _saveSettings(immediate: true);
            //   },
            // ),
            // const SizedBox(height: 8),
            // _buildClearWordsButton(),
            // const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: AppTextStyle.titleMedium.copyWith(
        color: AppColors.secondary,
        fontWeight: FontWeight.w600,
      ),
    );
  }

  Widget _buildToggleSetting({
    required String title,
    required String description,
    required bool value,
    required ValueChanged<bool>? onChanged,
  }) {
    final isEnabled = onChanged != null;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isEnabled
            ? AppColors.lightGrey
            : AppColors.lightGrey.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.titleLarge.copyWith(
                    color: isEnabled ? AppColors.primary : AppColors.grey,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: AppTextStyle.bodySmall.copyWith(
                    color: isEnabled
                        ? AppColors.grey
                        : AppColors.grey.withValues(alpha: 0.6),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8), // Fix overflow issue
          CustomToggleSwitch(
            value: value,
            onChanged: isEnabled ? onChanged : (_) {},
            width: 52.0,
            height: 20.0,
            knobSize: 24.0,
          ),
        ],
      ),
    );
  }

  Widget _buildSliderSetting({
    required String title,
    required double portraitValue,
    double? landscapeValue,
    required ValueChanged<double> onPortraitChanged,
    ValueChanged<double>? onLandscapeChanged,
    required double min,
    required double max,
    required String unit,
    String portraitLabel = 'Portrait',
    String? landscapeLabel,
    bool showLandscape = true,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: AppTextStyle.titleSmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              SizedBox(
                width: 80,
                child: Text(
                  portraitLabel,
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
              ),
              Expanded(
                child: Slider(
                  thumbColor: AppColors.white,
                  value: portraitValue,
                  min: min,
                  max: max,
                  onChanged: onPortraitChanged,
                  activeColor: AppColors.secondary,
                  inactiveColor: AppColors.white,
                ),
              ),
              SizedBox(
                width: 50,
                child: Text(
                  '${portraitValue.toInt()}$unit',
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                  textAlign: TextAlign.right,
                ),
              ),
            ],
          ),
          if (showLandscape &&
              landscapeValue != null &&
              onLandscapeChanged != null) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                SizedBox(
                  width: 80,
                  child: Text(
                    landscapeLabel ?? 'Landscape',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                Expanded(
                  child: Slider(
                    thumbColor: AppColors.white,
                    value: landscapeValue,
                    min: min,
                    max: max,
                    onChanged: onLandscapeChanged,
                    activeColor: AppColors.secondary,
                    inactiveColor: AppColors.white,
                  ),
                ),
                SizedBox(
                  width: 50,
                  child: Text(
                    '${landscapeValue.toInt()}$unit',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                    textAlign: TextAlign.right,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildValueDisplay({required String title, required String value}) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              title,
              style: AppTextStyle.titleSmall.copyWith(
                color: AppColors.black,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Text(
            value,
            style: AppTextStyle.bodySmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }


  Widget _buildChipSelector({
    required String title,
    required String description,
    required List<Map<String, String>> options,
    required String selectedValue,
    required ValueChanged<String> onChanged,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: AppTextStyle.titleSmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            description,
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: options.map((option) {
              final value = option['value'] ?? '';
              final label = option['label'] ?? value;
              final isSelected = value == selectedValue;
              return GestureDetector(
                onTap: () => onChanged(value),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? AppColors.secondary
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: isSelected ? AppColors.secondary : AppColors.grey,
                      width: 1,
                    ),
                  ),
                  child: Text(
                    label,
                    style: AppTextStyle.bodySmall.copyWith(
                      color: isSelected ? AppColors.white : AppColors.grey,
                      fontWeight: isSelected
                          ? FontWeight.w600
                          : FontWeight.normal,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }



  Widget _buildSideSelector() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Keyboard Side',
            style: AppTextStyle.titleSmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Choose which side to dock the keyboard',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: GestureDetector(
                  onTap: () {
                    setState(() => oneHandedSide = 'left');
                    _saveSettings(immediate: true);
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    decoration: BoxDecoration(
                      color: oneHandedSide == 'left'
                          ? AppColors.secondary
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: oneHandedSide == 'left'
                            ? AppColors.secondary
                            : AppColors.grey,
                        width: 2,
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.keyboard_arrow_left,
                          color: oneHandedSide == 'left'
                              ? AppColors.white
                              : AppColors.grey,
                        ),
                        Text(
                          'Left',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: oneHandedSide == 'left'
                                ? AppColors.white
                                : AppColors.grey,
                            fontWeight: oneHandedSide == 'left'
                                ? FontWeight.w600
                                : FontWeight.normal,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: GestureDetector(
                  onTap: () {
                    setState(() => oneHandedSide = 'right');
                    _saveSettings(immediate: true);
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    decoration: BoxDecoration(
                      color: oneHandedSide == 'right'
                          ? AppColors.secondary
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: oneHandedSide == 'right'
                            ? AppColors.secondary
                            : AppColors.grey,
                        width: 2,
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          'Right',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: oneHandedSide == 'right'
                                ? AppColors.white
                                : AppColors.grey,
                            fontWeight: oneHandedSide == 'right'
                                ? FontWeight.w600
                                : FontWeight.normal,
                          ),
                        ),
                        Icon(
                          Icons.keyboard_arrow_right,
                          color: oneHandedSide == 'right'
                              ? AppColors.white
                              : AppColors.grey,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildClearWordsButton() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Learned Words',
            style: AppTextStyle.titleSmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Clear all words learned from your typing patterns',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _showClearWordsDialog,
              icon: const Icon(
                Icons.delete_outline,
                color: Colors.red,
                size: 20,
              ),
              label: Text(
                'Clear Learned Words',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: Colors.red,
                  fontWeight: FontWeight.w600,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red.withOpacity(0.1),
                foregroundColor: Colors.red,
                elevation: 0,
                side: BorderSide(color: Colors.red.withOpacity(0.3), width: 1),
                padding: const EdgeInsets.symmetric(
                  vertical: 12,
                  horizontal: 16,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDialogSetting({
    required String title,
    String? description,
    double? portraitValue,
    double? landscapeValue,
    String? portraitLabel,
    String? landscapeLabel,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTextStyle.titleSmall.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (description != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: AppTextStyle.bodySmall.copyWith(
                        color: AppColors.grey,
                      ),
                    ),
                  ],
                  if (portraitValue != null && landscapeValue != null) ...[
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Icon(
                          Icons.smartphone,
                          size: 16,
                          color: AppColors.grey,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${portraitLabel ?? "Portrait"} : ${portraitValue.toInt()}%',
                          style: AppTextStyle.bodySmall.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(
                          Icons.tablet_android,
                          size: 16,
                          color: AppColors.grey,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${landscapeLabel ?? "Landscape"} : ${landscapeValue.toInt()}%',
                          style: AppTextStyle.bodySmall.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            const Icon(
              Icons.arrow_forward_ios,
              size: 16,
              color: AppColors.grey,
            ),
          ],
        ),
      ),
    );
  }

  // Dialog for Font Size
  void _showFontSizeDialog() {
    double tempPortrait = portraitFontSize;
    double tempLandscape = landscapeFontSize;

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              title: Text(
                'Key Font Size',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Portrait slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Portrait',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempPortrait.toInt()}%',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempPortrait,
                              min: 80.0,
                              max: 130.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempPortrait = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '80%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '130%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  // Landscape slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Landscape',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempLandscape.toInt()}%',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempLandscape,
                              min: 80.0,
                              max: 130.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempLandscape = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '80%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '130%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    tempPortrait = 100.0;
                    tempLandscape = 100.0;
                    setDialogState(() {});
                  },
                  child: Text(
                    'Reset',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      portraitFontSize = tempPortrait;
                      landscapeFontSize = tempLandscape;
                    });
                    _saveSettings(immediate: true);
                    Navigator.of(context).pop();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    'Apply',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // Dialog for Keyboard Height
  void _showKeyboardHeightDialog() {
    double tempPortrait = keyboardWidth;
    double tempLandscape = keyboardHeight;

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              title: Text(
                'Keyboard Height',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Portrait slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Portrait',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempPortrait.toInt()}%',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempPortrait,
                              min: 20.0,
                              max: 40.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempPortrait = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '20%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '40%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  // Landscape slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Landscape',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempLandscape.toInt()}%',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempLandscape,
                              min: 20.0,
                              max: 40.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempLandscape = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '20%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '40%',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    tempPortrait = 28.0;
                    tempLandscape = 28.0;
                    setDialogState(() {});
                  },
                  child: Text(
                    'Reset',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      keyboardWidth = tempPortrait;
                      keyboardHeight = tempLandscape;
                    });
                    _saveSettings(immediate: true);
                    Navigator.of(context).pop();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    'Apply',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // Dialog for Key Spacing
  void _showKeySpacingDialog() {
    double tempVertical = verticalKeySpacing;
    double tempHorizontal = horizontalKeySpacing;

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              title: Text(
                'Key spacing',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // Vertical slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Vertical',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempVertical.toStringAsFixed(1)}',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempVertical,
                              min: 0.0,
                              max: 10.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempVertical = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '0.0',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '10.0',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  // Horizontal slider
                  Row(
                    children: [
                      SizedBox(
                        width: 70,
                        child: Text(
                          'Horizontal',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Column(
                          children: [
                            Text(
                              '${tempHorizontal.toStringAsFixed(1)}',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Slider(
                              value: tempHorizontal,
                              min: 0.0,
                              max: 10.0,
                              activeColor: AppColors.secondary,
                              inactiveColor: AppColors.lightGrey,
                              onChanged: (value) {
                                setDialogState(() => tempHorizontal = value);
                              },
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const SizedBox(width: 70),
                      Expanded(
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              '0.0',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                            Text(
                              '10.0',
                              style: AppTextStyle.bodySmall.copyWith(
                                color: AppColors.grey,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    tempVertical = 5.0;
                    tempHorizontal = 2.0;
                    setDialogState(() {});
                  },
                  child: Text(
                    'Reset',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      verticalKeySpacing = tempVertical;
                      horizontalKeySpacing = tempHorizontal;
                    });
                    _saveSettings(immediate: true);
                    Navigator.of(context).pop();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    'Apply',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  // Dialog for One-handed mode
  void _showOneHandedModeDialog() {
    String tempSide = oneHandedSide;

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
              title: Text(
                'One-handed mode',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w600,
                ),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildRadioOption(
                    value: 'off',
                    groupValue: tempSide,
                    label: 'Off',
                    onChanged: (value) {
                      setDialogState(() => tempSide = value!);
                    },
                  ),
                  const SizedBox(height: 8),
                  _buildRadioOption(
                    value: 'left',
                    groupValue: tempSide,
                    label: 'Left-handed mode',
                    onChanged: (value) {
                      setDialogState(() => tempSide = value!);
                    },
                  ),
                  const SizedBox(height: 8),
                  _buildRadioOption(
                    value: 'right',
                    groupValue: tempSide,
                    label: 'Right-handed mode',
                    onChanged: (value) {
                      setDialogState(() => tempSide = value!);
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                ElevatedButton(
                  onPressed: () {
                    setState(() {
                      oneHandedSide = tempSide;
                      if (tempSide == 'off') {
                        oneHandedMode = false;
                      }
                    });
                    _saveSettings(immediate: true);
                    Navigator.of(context).pop();
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.secondary,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    'Apply',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildRadioOption({
    required String value,
    required String groupValue,
    required String label,
    required ValueChanged<String?> onChanged,
  }) {
    final isSelected = value == groupValue;
    return InkWell(
      onTap: () => onChanged(value),
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.secondary.withOpacity(0.1)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isSelected ? AppColors.secondary : AppColors.lightGrey,
            width: 2,
          ),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                label,
                style: AppTextStyle.bodyMedium.copyWith(
                  color: isSelected ? AppColors.secondary : AppColors.black,
                  fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
                ),
              ),
            ),
            if (isSelected)
              const Icon(
                Icons.check_circle,
                color: AppColors.secondary,
                size: 20,
              ),
          ],
        ),
      ),
    );
  }
}
