import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';

const Map<String, String> _gestureActionLabels = {
  'none': 'No action',
  'cycle_prev_mode': 'Cycle to previous keyboard mode',
  'cycle_next_mode': 'Cycle to next keyboard mode',
  'delete_word_before_cursor': 'Delete word before cursor',
  'hide_keyboard': 'Hide keyboard',
  'insert_space': 'Insert space',
  'move_cursor_up': 'Move cursor up',
  'move_cursor_down': 'Move cursor down',
  'move_cursor_left': 'Move cursor left',
  'move_cursor_right': 'Move cursor right',
  'move_cursor_line_start': 'Move cursor to start of line',
  'move_cursor_line_end': 'Move cursor to end of line',
  'move_cursor_page_start': 'Move cursor to start of page',
  'move_cursor_page_end': 'Move cursor to end of page',
  'shift': 'Shift',
  'redo': 'Redo',
  'undo': 'Undo',
  'open_clipboard': 'Open clipboard manager/history',
  'show_input_method_picker': 'Show input method picker',
  'switch_prev_language': 'Switch to previous Language',
  'switch_next_language': 'Switch to next Language',
  'toggle_smartbar': 'Toggle Smartbar visibility',
  'delete_characters_precisely': 'Delete characters precisely',
  'delete_character_before_cursor': 'Delete character before cursor',
  'delete_word': 'Delete word',
  'delete_line': 'Delete line',
};

const List<String> _generalGestureOptions = [
  'none',
  'cycle_prev_mode',
  'cycle_next_mode',
  'delete_word_before_cursor',
  'hide_keyboard',
  'insert_space',
  'move_cursor_up',
  'move_cursor_down',
  'move_cursor_left',
  'move_cursor_right',
  'move_cursor_line_start',
  'move_cursor_line_end',
  'move_cursor_page_start',
  'move_cursor_page_end',
  'shift',
  'redo',
  'undo',
  'open_clipboard',
  'show_input_method_picker',
  'switch_prev_language',
  'switch_next_language',
  'toggle_smartbar',
];

const List<String> _spaceGestureOptions = [
  'none',
  'move_cursor_up',
  'move_cursor_down',
  'move_cursor_left',
  'move_cursor_right',
  'move_cursor_line_start',
  'move_cursor_line_end',
  'move_cursor_page_start',
  'move_cursor_page_end',
  'insert_space',
  'hide_keyboard',
  'open_clipboard',
  'show_input_method_picker',
  'switch_prev_language',
  'switch_next_language',
  'toggle_smartbar',
];

const List<String> _deleteGestureOptions = [
  'none',
  'delete_characters_precisely',
  'delete_character_before_cursor',
  'delete_word',
  'delete_line',
  'delete_word_before_cursor',
  'undo',
  'redo',
  'hide_keyboard',
  'open_clipboard',
];

class GesturesGlideScreen extends StatefulWidget {
  const GesturesGlideScreen({super.key});

  @override
  State<GesturesGlideScreen> createState() => _GesturesGlideScreenState();
}

class _GesturesGlideScreenState extends State<GesturesGlideScreen> {
  static const MethodChannel _channel = MethodChannel('ai_keyboard/config');

  Timer? _saveDebounceTimer;
  Timer? _notifyDebounceTimer;

  // Glide Typing Settings
  bool glideTyping = true;
  bool showGlideTrail = true;
  double glideTrailFadeTime = 200.0;
  bool alwaysDeleteWord = true;

  // General Settings
  String swipeUpAction = 'shift';
  String swipeDownAction = 'hide_keyboard';
  String swipeLeftAction = 'delete_character_before_cursor';
  String swipeRightAction = 'insert_space';

  // Space Bar Settings
  String spaceBarLongPress = 'show_input_method_picker';
  String spaceBarSwipeDown = 'none';
  String spaceBarSwipeLeft = 'move_cursor_left';
  String spaceBarSwipeRight = 'move_cursor_right';

  // Other Gestures Settings
  String deleteKeySwipeLeft = 'delete_word_before_cursor';
  String deleteKeyLongPress = 'delete_character_before_cursor';
  double swipeVelocityThreshold = 1900.0;
  double swipeDistanceThreshold = 20.0;

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

  String _validateAction(String? code, String fallback) {
    if (code != null && _gestureActionLabels.containsKey(code)) {
      return code;
    }
    return fallback;
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      glideTyping = prefs.getBool('gestures.glide_typing') ?? glideTyping;
      showGlideTrail = prefs.getBool('gestures.show_glide_trail') ?? showGlideTrail;
      glideTrailFadeTime = prefs.getDouble('gestures.glide_trail_fade_time') ?? glideTrailFadeTime;
      alwaysDeleteWord = prefs.getBool('gestures.always_delete_word') ?? alwaysDeleteWord;
      swipeVelocityThreshold = prefs.getDouble('gestures.swipe_velocity_threshold') ?? swipeVelocityThreshold;
      swipeDistanceThreshold = prefs.getDouble('gestures.swipe_distance_threshold') ?? swipeDistanceThreshold;

      // ✅ If glide typing is off, turn off show glide trail
      if (!glideTyping) {
        showGlideTrail = false;
      }

      swipeUpAction = _validateAction(prefs.getString('gestures.swipe_up_action'), swipeUpAction);
      swipeDownAction = _validateAction(prefs.getString('gestures.swipe_down_action'), swipeDownAction);
      swipeLeftAction = _validateAction(prefs.getString('gestures.swipe_left_action'), swipeLeftAction);
      swipeRightAction = _validateAction(prefs.getString('gestures.swipe_right_action'), swipeRightAction);

      spaceBarLongPress = _validateAction(prefs.getString('gestures.space_long_press_action'), spaceBarLongPress);
      spaceBarSwipeDown = _validateAction(prefs.getString('gestures.space_swipe_down_action'), spaceBarSwipeDown);
      spaceBarSwipeLeft = _validateAction(prefs.getString('gestures.space_swipe_left_action'), spaceBarSwipeLeft);
      spaceBarSwipeRight = _validateAction(prefs.getString('gestures.space_swipe_right_action'), spaceBarSwipeRight);

      deleteKeySwipeLeft = _validateAction(prefs.getString('gestures.delete_swipe_left_action'), deleteKeySwipeLeft);
      deleteKeyLongPress = _validateAction(prefs.getString('gestures.delete_long_press_action'), deleteKeyLongPress);
    });
  }

  void _saveSettings({bool immediate = false}) {
    _saveDebounceTimer?.cancel();
    if (immediate) {
      _performSave();
    } else {
      _saveDebounceTimer = Timer(const Duration(milliseconds: 500), () {
        _performSave();
      });
    }
  }

  Future<void> _performSave() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('gestures.glide_typing', glideTyping);
    await prefs.setBool('gestures.show_glide_trail', showGlideTrail);
    await prefs.setDouble('gestures.glide_trail_fade_time', glideTrailFadeTime);
    await prefs.setBool('gestures.always_delete_word', alwaysDeleteWord);
    await prefs.setDouble('gestures.swipe_velocity_threshold', swipeVelocityThreshold);
    await prefs.setDouble('gestures.swipe_distance_threshold', swipeDistanceThreshold);

    await prefs.setString('gestures.swipe_up_action', swipeUpAction);
    await prefs.setString('gestures.swipe_down_action', swipeDownAction);
    await prefs.setString('gestures.swipe_left_action', swipeLeftAction);
    await prefs.setString('gestures.swipe_right_action', swipeRightAction);

    await prefs.setString('gestures.space_long_press_action', spaceBarLongPress);
    await prefs.setString('gestures.space_swipe_down_action', spaceBarSwipeDown);
    await prefs.setString('gestures.space_swipe_left_action', spaceBarSwipeLeft);
    await prefs.setString('gestures.space_swipe_right_action', spaceBarSwipeRight);

    await prefs.setString('gestures.delete_swipe_left_action', deleteKeySwipeLeft);
    await prefs.setString('gestures.delete_long_press_action', deleteKeyLongPress);

    await _sendSettingsToKeyboard();
    _debouncedNotifyKeyboard();
  }

  Future<void> _sendSettingsToKeyboard() async {
    try {
      await _channel.invokeMethod('updateGestureSettings', {
        'glideTyping': glideTyping,
        'showGlideTrail': showGlideTrail,
        'glideTrailFadeTime': glideTrailFadeTime.round(),
        'alwaysDeleteWord': alwaysDeleteWord,
        'swipeVelocityThreshold': swipeVelocityThreshold,
        'swipeDistanceThreshold': swipeDistanceThreshold,
        'swipeUpAction': swipeUpAction,
        'swipeDownAction': swipeDownAction,
        'swipeLeftAction': swipeLeftAction,
        'swipeRightAction': swipeRightAction,
        'spaceLongPressAction': spaceBarLongPress,
        'spaceSwipeDownAction': spaceBarSwipeDown,
        'spaceSwipeLeftAction': spaceBarSwipeLeft,
        'spaceSwipeRightAction': spaceBarSwipeRight,
        'deleteSwipeLeftAction': deleteKeySwipeLeft,
        'deleteLongPressAction': deleteKeyLongPress,
      });
    } catch (e) {
      debugPrint('⚠️ Failed to send gesture settings: $e');
    }
  }

  void _debouncedNotifyKeyboard() {
    _notifyDebounceTimer?.cancel();
    _notifyDebounceTimer = Timer(const Duration(milliseconds: 300), () {
      _notifyKeyboard();
    });
  }

  Future<void> _notifyKeyboard() async {
    try {
      await _channel.invokeMethod('notifyConfigChange');
      await _channel.invokeMethod('broadcastSettingsChanged');
    } catch (e) {
      debugPrint('⚠️ Failed to notify keyboard: $e');
    }
  }

  String _actionLabel(String code) => _gestureActionLabels[code] ?? code;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Gestures & Glide typing',
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

            // Glide Typing Section
            _buildSectionTitle('Glide typing'),
            const SizedBox(height: 16),

            // Glide typing
            _buildToggleSetting(
              title: 'Glide typing',
              description: 'Enabled',
              value: glideTyping,
              onChanged: (value) {
                setState(() {
                  glideTyping = value;
                  // ✅ If glide typing is turned off, automatically turn off show glide trail
                  if (!value) {
                    showGlideTrail = false;
                  }
                });
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Show glide trail
            _buildToggleSetting(
              title: 'Show glide trail',
              description: glideTyping ? 'Enabled' : 'Disabled (requires glide typing)',
              value: showGlideTrail,
              onChanged: glideTyping ? (value) {
                setState(() => showGlideTrail = value);
                _saveSettings();
              } : null, // Disable when glide typing is off
            ),

            const SizedBox(height: 12),

            // Glide trail fade time
            Opacity(
              opacity: glideTyping ? 1.0 : 0.5,
              child: AbsorbPointer(
                absorbing: !glideTyping,
                child: _buildSliderSetting(
                  title: 'Glide trail fade time',
                  portraitValue: glideTrailFadeTime,
                  onPortraitChanged: glideTyping ? (value) {
                    setState(() => glideTrailFadeTime = value);
                    _saveSettings();
                  } : null, // Disable when glide typing is off
                  min: 100.0,
                  max: 1000.0,
                  unit: 'ms',
                  portraitLabel: 'Time',
                  showLandscape: false,
                ),
              ),
            ),

            const SizedBox(height: 12),

            // Always delete word
            _buildToggleSetting(
              title: 'Always delete word',
              description: 'Enabled',
              value: alwaysDeleteWord,
              onChanged: (value) {
                setState(() => alwaysDeleteWord = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 32),

            // General Section
            _buildSectionTitle('General'),
            const SizedBox(height: 16),

            // Swipe up
            _buildSwipeActionCard(
              title: 'Swipe up',
              subtitle: _actionLabel(swipeUpAction),
              onTap: () => _showSwipeActionDialog('Swipe up', swipeUpAction, (action) {
                setState(() => swipeUpAction = action);
              }),
            ),

            const SizedBox(height: 12),

            // Swipe down
            _buildSwipeActionCard(
              title: 'Swipe down',
              subtitle: _actionLabel(swipeDownAction),
              onTap: () => _showSwipeActionDialog('Swipe down', swipeDownAction, (action) {
                setState(() => swipeDownAction = action);
              }),
            ),

            const SizedBox(height: 12),

            // Swipe left
            _buildSwipeActionCard(
              title: 'Swipe left',
              subtitle: _actionLabel(swipeLeftAction),
              onTap: () => _showSwipeActionDialog('Swipe left', swipeLeftAction, (action) {
                setState(() => swipeLeftAction = action);
              }),
            ),

            const SizedBox(height: 12),

            // Swipe right
            _buildSwipeActionCard(
              title: 'Swipe right',
              subtitle: _actionLabel(swipeRightAction),
              onTap: () => _showSwipeActionDialog('Swipe right', swipeRightAction, (action) {
                setState(() => swipeRightAction = action);
              }),
            ),

            const SizedBox(height: 32),

            // Space Bar Section
            _buildSectionTitle('Space Bar'),
            const SizedBox(height: 16),

            // Space bar long press
            _buildSwipeActionCard(
              title: 'Space bar long press',
              subtitle: _actionLabel(spaceBarLongPress),
              onTap: () =>
                  _showSpaceBarActionDialog('Space bar long press', spaceBarLongPress, (action) {
                    setState(() => spaceBarLongPress = action);
                  }),
            ),

            const SizedBox(height: 12),

            // Space bar Swipe down
            _buildSwipeActionCard(
              title: 'Space bar Swipe down',
              subtitle: _actionLabel(spaceBarSwipeDown),
              onTap: () =>
                  _showSpaceBarActionDialog('Space bar Swipe down', spaceBarSwipeDown, (action) {
                    setState(() => spaceBarSwipeDown = action);
                  }),
            ),

            const SizedBox(height: 12),

            // Space bar Swipe left
            _buildSwipeActionCard(
              title: 'Space bar Swipe left',
              subtitle: _actionLabel(spaceBarSwipeLeft),
              onTap: () =>
                  _showSpaceBarActionDialog('Space bar Swipe left', spaceBarSwipeLeft, (action) {
                    setState(() => spaceBarSwipeLeft = action);
                  }),
            ),

            const SizedBox(height: 12),

            // Space bar Swipe right
            _buildSwipeActionCard(
              title: 'Space bar Swipe right',
              subtitle: _actionLabel(spaceBarSwipeRight),
              onTap: () =>
                  _showSpaceBarActionDialog('Space bar Swipe right', spaceBarSwipeRight, (action) {
                    setState(() => spaceBarSwipeRight = action);
                  }),
            ),

            const SizedBox(height: 32),

            // Other gestures Section
            _buildSectionTitle('Other gestures'),
            const SizedBox(height: 16),

            // Delete key swipe left
            _buildSwipeActionCard(
              title: 'Delete key swipe left',
              subtitle: _actionLabel(deleteKeySwipeLeft),
              onTap: () =>
                  _showDeleteKeyActionDialog('Delete key swipe left', deleteKeySwipeLeft, (action) {
                    setState(() => deleteKeySwipeLeft = action);
                  }),
            ),

            const SizedBox(height: 12),

            // Delete key long press
            _buildSwipeActionCard(
              title: 'Delete key long press',
              subtitle: _actionLabel(deleteKeyLongPress),
              onTap: () =>
                  _showDeleteKeyActionDialog('Delete key long press', deleteKeyLongPress, (action) {
                    setState(() => deleteKeyLongPress = action);
                  }),
            ),

            const SizedBox(height: 12),

            // Swipe velocity threshold
            _buildSliderSetting(
              title: 'Swipe velocity threshold',
              portraitValue: swipeVelocityThreshold,
              onPortraitChanged: (value) {
                setState(() => swipeVelocityThreshold = value);
                _saveSettings();
              },
              min: 1000.0,
              max: 3000.0,
              unit: ' dp/s',
              portraitLabel: 'dp/s',
              showLandscape: false,
            ),

            const SizedBox(height: 12),

            // Swipe distance threshold
            _buildSliderSetting(
              title: 'Swipe distance threshold',
              portraitValue: swipeDistanceThreshold,
              onPortraitChanged: (value) {
                setState(() => swipeDistanceThreshold = value);
                _saveSettings();
              },
              min: 10.0,
              max: 50.0,
              unit: ' dp/s',
              portraitLabel: 'dp/s',
              showLandscape: false,
            ),

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
    ValueChanged<bool>? onChanged,
  }) {
    final isEnabled = onChanged != null;
    return Opacity(
      opacity: isEnabled ? 1.0 : 0.5,
      child: AbsorbPointer(
        absorbing: !isEnabled,
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
                onChanged: onChanged ?? (_) {}, // Provide no-op callback when disabled
                width: 48.0,
                height: 16.0,
                knobSize: 24.0,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSliderSetting({
    required String title,
    required double portraitValue,
    double? landscapeValue,
    required ValueChanged<double>? onPortraitChanged,
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
            style: AppTextStyle.titleLarge.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          // Portrait Slider
          Row(
            children: [
              SizedBox(
                width: 80,
                child: Text(
                  portraitLabel,
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.grey,
                  ),
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
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                  textAlign: TextAlign.right,
                ),
              ),
            ],
          ),
          // Landscape Slider (if enabled)
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
                    style: AppTextStyle.bodyMedium.copyWith(
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

  Widget _buildSwipeActionCard({
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
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
                ],
              ),
            ),
            Icon(Icons.arrow_forward_ios, color: AppColors.grey, size: 16),
          ],
        ),
      ),
    );
  }

  
  
  
void _showSwipeActionDialog(
  String gesture,
  String currentAction,
  ValueChanged<String> onActionSelected,
) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.8,
            ),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Text(
                  '$gesture Action',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Divider(color: AppColors.lightGrey, thickness: 1),

                // Scrollable Action Options
                Flexible(
                  child: SingleChildScrollView(
                    child: Column(
                      children: [
                        for (final code in _generalGestureOptions) ...[
                          _buildActionOption(
                            code,
                            currentAction,
                            _actionLabel(code),
                            onActionSelected,
                          ),
                          const SizedBox(height: 8),
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 20),

                // Apply Button
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Cancel',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'OK',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

Widget _buildActionOption(
  String actionCode,
  String currentCode,
  String label,
  ValueChanged<String> onActionSelected,
) {
    return GestureDetector(
      onTap: () {
        onActionSelected(actionCode);
        Navigator.of(context).pop();
      },
      child: Row(
        children: [
          // Radio Button
          Radio<String>(
            value: actionCode,
            groupValue: currentCode,
            onChanged: (String? value) {
              if (value != null) {
                onActionSelected(value);
                Navigator.of(context).pop();
              }
            },
            activeColor: AppColors.secondary,
            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
          const SizedBox(width: 8),

          // Content
          Expanded(
            child: Text(
              label,
              style: AppTextStyle.titleMedium.copyWith(
                color: AppColors.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

void _showSpaceBarActionDialog(
  String gesture,
  String currentAction,
  ValueChanged<String> onActionSelected,
) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.8,
            ),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Text(
                  '$gesture Action',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Divider(color: AppColors.lightGrey, thickness: 1),

                // Scrollable Action Options
                Flexible(
                  child: SingleChildScrollView(
                    child: Column(
                      children: [
                        for (final code in _spaceGestureOptions) ...[
                          _buildActionOption(
                            code,
                            currentAction,
                            _actionLabel(code),
                            onActionSelected,
                          ),
                          const SizedBox(height: 8),
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 20),

                // Apply Button
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Cancel',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'OK',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showDeleteKeyActionDialog(
    String gesture,
    String currentAction,
    ValueChanged<String> onActionSelected,
  ) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.8,
            ),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Text(
                  '$gesture Action',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Divider(color: AppColors.lightGrey, thickness: 1),

                // Scrollable Action Options
                Flexible(
                  child: SingleChildScrollView(
                    child: Column(
                      children: [
                        for (final code in _deleteGestureOptions) ...[
                          _buildActionOption(
                            code,
                            currentAction,
                            _actionLabel(code),
                            onActionSelected,
                          ),
                          const SizedBox(height: 8),
                        ],
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 20),

                // Apply Button
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Cancel',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'OK',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
