import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';
import 'package:ai_keyboard/services/keyboard_cloud_sync.dart';

class TypingSuggestionScreen extends StatefulWidget {
  const TypingSuggestionScreen({super.key});

  @override
  State<TypingSuggestionScreen> createState() => _TypingSuggestionScreenState();
}

class _TypingSuggestionScreenState extends State<TypingSuggestionScreen> {
  // MethodChannel for communication with native Kotlin keyboard
  static const _channel = MethodChannel('ai_keyboard/config');
  
  // Debounce timers
  Timer? _saveDebounceTimer;
  Timer? _notifyDebounceTimer;
  
  // Suggestion & correction settings
  bool displaySuggestions = true;
  double displayModeCount = 3;
  double historySize = 20;
  bool autoCorrectionEnabled = true;
  bool clipboardSuggestionsEnabled = true;
  double clipboardWindowSec = 60;
  bool dictionaryEnabled = true;
  bool autoFillSuggestionEnabled = true;
  bool autoCapitalizationEnabled = true;
  bool rememberCapsState = false;
  bool doubleSpacePeriodEnabled = true;

  // Clipboard sync / internal settings
  bool clearPrimaryClipAffects = true;
  bool syncFromSystem = true;
  bool syncToFivive = true;

  @override
  void dispose() {
    _saveDebounceTimer?.cancel();
    _notifyDebounceTimer?.cancel();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  /// Load settings from SharedPreferences
  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      displaySuggestions = prefs.getBool('display_suggestions') ?? true;

      final savedDisplayMode =
          prefs.getString('display_mode') ?? prefs.getString('displayMode') ?? '3';
      final parsedDisplayMode = double.tryParse(savedDisplayMode) ?? 3;
      displayModeCount = parsedDisplayMode.clamp(3, 4).toDouble();

      final rawHistorySize = prefs.getDouble('history_size') ??
          prefs.getInt('history_size')?.toDouble() ??
          prefs.getInt('clipboard_history_items')?.toDouble() ??
          20.0;
      historySize = rawHistorySize.clamp(1, 100).toDouble();

      autoCorrectionEnabled = prefs.getBool('auto_correction') ??
          prefs.getBool('autocorrect') ??
          true;

      clipboardSuggestionsEnabled = prefs.getBool('clipboard_suggestions') ??
          prefs.getBool('internal_clipboard') ??
          prefs.getBool('clipboardSuggestions') ??
          true;

      final rawWindowSec = (prefs.getInt('clipboard_window_sec') ??
              prefs.getDouble('clipboard_window_sec')?.round() ??
              60)
          .toDouble();
      clipboardWindowSec = rawWindowSec.clamp(0, 600);

      dictionaryEnabled = prefs.getBool('dictionary_enabled') ??
          prefs.getBool('dictionaryEnabled') ??
          true;

      autoFillSuggestionEnabled = prefs.getBool('auto_fill_suggestion') ??
          prefs.getBool('autoFillSuggestion') ??
          true;

      autoCapitalizationEnabled = prefs.getBool('auto_capitalization') ??
          prefs.getBool('autoCapitalization') ??
          true;

      rememberCapsState = prefs.getBool('remember_caps_state') ?? false;

      doubleSpacePeriodEnabled = prefs.getBool('double_space_period') ??
          prefs.getBool('doubleSpacePeriod') ??
          true;

      clearPrimaryClipAffects =
          prefs.getBool('clear_primary_clip_affects') ?? true;
      syncFromSystem = prefs.getBool('sync_from_system') ?? true;
      syncToFivive = prefs.getBool('sync_to_fivive') ?? true;
    });
  }

  /// Save settings with debouncing
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
    
    // Save all settings
    await prefs.setBool('display_suggestions', displaySuggestions);
    await prefs.setString('display_mode', displayModeCount.toInt().toString());
    await prefs.setString('displayMode', displayModeCount.toInt().toString());
    await prefs.setDouble('history_size', historySize);
    await prefs.setInt('clipboard_history_items', historySize.toInt());

    await prefs.setBool('auto_correction', autoCorrectionEnabled);
    await prefs.setBool('autocorrect', autoCorrectionEnabled);

    await prefs.setBool('clipboard_suggestions', clipboardSuggestionsEnabled);
    await prefs.setBool('internal_clipboard', clipboardSuggestionsEnabled);
    await prefs.setBool('clipboardSuggestions', clipboardSuggestionsEnabled);

    await prefs.setInt('clipboard_window_sec', clipboardWindowSec.toInt());

    await prefs.setBool('dictionary_enabled', dictionaryEnabled);
    await prefs.setBool('dictionaryEnabled', dictionaryEnabled);

    await prefs.setBool('auto_fill_suggestion', autoFillSuggestionEnabled);
    await prefs.setBool('autoFillSuggestion', autoFillSuggestionEnabled);

    await prefs.setBool('auto_capitalization', autoCapitalizationEnabled);
    await prefs.setBool('autoCapitalization', autoCapitalizationEnabled);

    await prefs.setBool('remember_caps_state', rememberCapsState);

    await prefs.setBool('double_space_period', doubleSpacePeriodEnabled);
    await prefs.setBool('doubleSpacePeriod', doubleSpacePeriodEnabled);

    await prefs.setBool('clear_primary_clip_affects', clearPrimaryClipAffects);
    await prefs.setBool('sync_from_system', syncFromSystem);
    await prefs.setBool('sync_to_fivive', syncToFivive);
    
    debugPrint('✅ Typing & Suggestion settings saved');
    
    // Send to native keyboard
    await _sendSettingsToKeyboard();
    
    // Sync to Firebase for cross-device sync
    await _syncToFirebase();
    
    // Notify keyboard (debounced)
    _debouncedNotifyKeyboard();
  }
  
  /// Sync settings to Firebase for cross-device sync
  Future<void> _syncToFirebase() async {
    try {
      await KeyboardCloudSync.upsert({
        'displaySuggestions': displaySuggestions,
        'displayMode': displayModeCount.toInt(),
        'autoCorrection': autoCorrectionEnabled,
        'clipboardSuggestions': {
          'enabled': clipboardSuggestionsEnabled,
          'windowSec': clipboardWindowSec.toInt(),
          'historyItems': historySize.toInt(),
        },
        'dictionaryEnabled': dictionaryEnabled,
        'autoFillSuggestion': autoFillSuggestionEnabled,
        'autoCapitalization': autoCapitalizationEnabled,
        'doubleSpacePeriod': doubleSpacePeriodEnabled,
        'rememberCapsState': rememberCapsState,
      });
      debugPrint('✅ Typing & Suggestion settings synced to Firebase');
    } catch (e) {
      debugPrint('⚠ Failed to sync to Firebase: $e');
      // Don't block user if Firebase fails
    }
  }
  
  /// Send settings to native keyboard
  Future<void> _sendSettingsToKeyboard() async {
    try {
      await _channel.invokeMethod('updateSettings', {
        'displaySuggestions': displaySuggestions,
        'displayMode': displayModeCount.toInt().toString(),
        'display_mode': displayModeCount.toInt().toString(),
        'displayModeCount': displayModeCount.toInt(),
        'clipboardHistorySize': historySize.toInt(),
        'historySize': historySize.toInt(),
        'clipboardWindowSec': clipboardWindowSec.toInt(),
        'clipboardSuggestions': clipboardSuggestionsEnabled,
        'clipboardSuggestionsEnabled': clipboardSuggestionsEnabled,
        'internalClipboard': clipboardSuggestionsEnabled,
        'autoCorrection': autoCorrectionEnabled,
        'autocorrect': autoCorrectionEnabled,
        'dictionaryEnabled': dictionaryEnabled,
        'autoFillSuggestion': autoFillSuggestionEnabled,
        'autoCapitalization': autoCapitalizationEnabled,
        'rememberCapsState': rememberCapsState,
        'doubleSpacePeriod': doubleSpacePeriodEnabled,
        'syncFromSystem': syncFromSystem,
        'syncToFivive': syncToFivive,
        'clearPrimaryClipAffects': clearPrimaryClipAffects,
      });
      debugPrint('📤 Settings sent to native keyboard');
    } catch (e) {
      debugPrint('⚠ Error sending settings: $e');
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
      await _channel.invokeMethod('broadcastSettingsChanged');
      debugPrint('✅ Keyboard notified - settings updated immediately');
      _showSuccessSnackBar();
    } catch (e) {
      debugPrint('⚠ Failed to notify: $e');
      _showErrorSnackBar(e.toString());
    }
  }
  
  /// Show success snackbar
  void _showSuccessSnackBar() {
    if (!mounted) return;
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Row(
          children: [
            Icon(Icons.check_circle, color: Colors.white, size: 20),
            SizedBox(width: 8),
            Expanded(child: Text('Settings saved! Keyboard updated immediately.')),
          ],
        ),
        backgroundColor: Colors.green,
        duration: const Duration(seconds: 2),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }
  
  /// Show error snackbar
  void _showErrorSnackBar(String error) {
    if (!mounted) return;
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Error updating keyboard: $error'),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
      ),
    );
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
          'Typing & Suggestion',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        actionsPadding: const EdgeInsets.only(right: 16),
        actions: [
          Stack(
            children: [
              Icon(Icons.notifications, color: AppColors.white, size: 24),
              Positioned(
                right: 0,
                top: 0,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
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
            const SizedBox(height: 8),

            _buildSectionTitle('Suggestion'),
            const SizedBox(height: 16),

            _buildToggleSetting(
              title: 'Display suggestions',
              description: displaySuggestions ? 'Enabled' : 'Disabled',
              value: displaySuggestions,
              onChanged: (value) {
                setState(() => displaySuggestions = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildSliderTile(
              title: 'Display mode',
              subtitle: '${displayModeCount.toInt()} suggestions',
              value: displayModeCount,
              min: 3,
              max: 4,
              divisions: 1,
              leadingLabel: 'Suggestions',
              valueFormatter: (v) => '${v.toInt()}',
              onChanged: (value) {
                final nextValue = value.round().clamp(3, 4).toDouble();
                setState(() => displayModeCount = nextValue);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildSliderTile(
              title: 'History Size',
              subtitle: '${historySize.toInt()} items',
              value: historySize,
              min: 10,
              max: 50,
              divisions: 8,
              leadingLabel: 'Items',
              valueFormatter: (v) => '${v.toInt()}',
              onChanged: (value) {
                final rounded = (value / 5).round() * 5;
                setState(() => historySize = rounded.clamp(10, 50).toDouble());
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Auto Correction',
              description: autoCorrectionEnabled ? 'Enabled' : 'Disabled',
              value: autoCorrectionEnabled,
              onChanged: (value) {
                setState(() => autoCorrectionEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Clipboard content suggestion',
              description: clipboardSuggestionsEnabled ? 'Enabled' : 'Disabled',
              value: clipboardSuggestionsEnabled,
              onChanged: (value) {
                setState(() => clipboardSuggestionsEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Dictionary',
              description: dictionaryEnabled ? 'Enabled' : 'Disabled',
              value: dictionaryEnabled,
              onChanged: (value) {
                setState(() => dictionaryEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildSliderTile(
              title: 'Limit clipboard suggestion to',
              subtitle: _clipboardWindowSubtitle(),
              value: clipboardWindowSec,
              min: 0,
              max: 300,
              divisions: 30,
              leadingLabel: 'Time',
              valueFormatter: _formatClipboardWindowValue,
              onChanged: (value) {
                final rounded = (value / 10).round() * 10;
                setState(
                  () => clipboardWindowSec = rounded.clamp(0, 300).toDouble(),
                );
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Auto fill suggestion',
              description: autoFillSuggestionEnabled ? 'Enabled' : 'Disabled',
              value: autoFillSuggestionEnabled,
              onChanged: (value) {
                setState(() => autoFillSuggestionEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 32),

            _buildSectionTitle('Corrections'),
            const SizedBox(height: 16),

            _buildToggleSetting(
              title: 'Auto capitalization',
              description: autoCapitalizationEnabled ? 'Enabled' : 'Disabled',
              value: autoCapitalizationEnabled,
              onChanged: (value) {
                setState(() => autoCapitalizationEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Remember caps lock state',
              description: rememberCapsState ? 'Enabled' : 'Disabled',
              value: rememberCapsState,
              onChanged: (value) {
                setState(() => rememberCapsState = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            _buildToggleSetting(
              title: 'Double space period',
              description: doubleSpacePeriodEnabled ? 'Enabled' : 'Disabled',
              value: doubleSpacePeriodEnabled,
              onChanged: (value) {
                setState(() => doubleSpacePeriodEnabled = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 32),

           
           

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
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
    required ValueChanged<bool> onChanged,
  }) {
    return Container(
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
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          ),
          CustomToggleSwitch(
            value: value,
            onChanged: onChanged,
            width: 48.0,
            height: 16.0,
            knobSize: 24.0,
          ),
        ],
      ),
    );
  }

  Widget _buildSliderTile({
    required String title,
    required String subtitle,
    required double value,
    required double min,
    required double max,
    required ValueChanged<double> onChanged,
    int? divisions,
    String? leadingLabel,
    String Function(double)? valueFormatter,
  }) {
    final formatter = valueFormatter ?? (v) => v.toStringAsFixed(0);
    final sliderValue = value.clamp(min, max).toDouble();

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
            style: AppTextStyle.titleLarge.copyWith(
              color: AppColors.primary,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            subtitle,
            style: AppTextStyle.bodySmall.copyWith(
              color: AppColors.grey,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              if (leadingLabel != null)
                SizedBox(
                  width: 80,
                  child: Text(
                    leadingLabel,
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
              Expanded(
                child: Slider(
                  value: sliderValue,
                  min: min,
                  max: max,
                  divisions: divisions,
                  onChanged: onChanged,
                  activeColor: AppColors.secondary,
                  inactiveColor: AppColors.white,
                  thumbColor: AppColors.white,
                ),
              ),
              SizedBox(
                width: 60,
                child: Text(
                  formatter(sliderValue),
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                  textAlign: TextAlign.right,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _formatClipboardWindowValue(double value) {
    if (value <= 0) {
      return 'Off';
    }
    if (value % 60 == 0) {
      final minutes = (value / 60).round();
      return minutes == 1 ? '1m' : '${minutes}m';
    }
    return '${value.toInt()}s';
  }

  String _clipboardWindowSubtitle() {
    final formatted = _formatClipboardWindowValue(clipboardWindowSec);
    if (formatted == 'Off') {
      return 'Clipboard suggestions never expire';
    }
    return 'Items copied within the last $formatted';
  }
}
