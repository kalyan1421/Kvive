import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';
import 'package:ai_keyboard/services/keyboard_cloud_sync.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';

// Removed _SoundOption class - sound selection is now handled in Custom_theme.dart

class SoundsVibrationScreen extends StatefulWidget {
  const SoundsVibrationScreen({super.key});

  @override
  State<SoundsVibrationScreen> createState() => _SoundsVibrationScreenState();
}

class _SoundsVibrationScreenState extends State<SoundsVibrationScreen> {
  // MethodChannel for communication with native Kotlin keyboard
  static const _channel = MethodChannel('ai_keyboard/config');
  static const _soundChannel = MethodChannel('keyboard.sound');
  
  // Debounce timers
  Timer? _saveDebounceTimer;
  Timer? _notifyDebounceTimer;
  
  // Sounds Settings
  bool audioFeedback = false; // Default to false (sound off)
  double soundVolume = 50.0; // Default 50%
  bool keyPressSounds = true;
  bool longPressKeySounds = true;
  bool repeatedActionKeySounds = true;
  String _selectedSound = 'click.mp3'; // Default sound file name

  // Haptic feedback & Vibration Settings
  bool hapticFeedback = false; // ✅ Default to false (OFF) - user must enable it
  String vibrationMode = 'Use haptic feedback interface'; // Default mode
  double vibrationDuration = 50.0; // Default 50ms
  bool keyPressVibration = false; // ✅ Default to false
  bool longPressKeyVibration = false; // ✅ Default to false
  bool repeatedActionKeyVibration = false; // ✅ Default to false

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
    
    // 🔥 FORCE RESET: Ensure sound/vibration are OFF by default (one-time reset)
    final hasReset = prefs.getBool('_sound_vibration_reset_v2') ?? false;
    if (!hasReset) {
      // Force all sound/vibration settings to FALSE
      await prefs.setBool('audio_feedback', false);
      await prefs.setBool('sound_enabled', false);
      await prefs.setBool('flutter.sound_enabled', false);
      await prefs.setBool('haptic_feedback', false);
      await prefs.setBool('vibration_enabled', false);
      await prefs.setBool('flutter.vibration_enabled', false);
      await prefs.setBool('key_press_sounds', false);
      await prefs.setBool('flutter.key_press_sounds', false);
      await prefs.setBool('long_press_key_sounds', false);
      await prefs.setBool('flutter.long_press_key_sounds', false);
      await prefs.setBool('repeated_action_key_sounds', false);
      await prefs.setBool('flutter.repeated_action_key_sounds', false);
      await prefs.setBool('key_press_vibration', false);
      await prefs.setBool('flutter.key_press_vibration', false);
      await prefs.setBool('long_press_key_vibration', false);
      await prefs.setBool('flutter.long_press_key_vibration', false);
      await prefs.setBool('repeated_action_key_vibration', false);
      await prefs.setBool('flutter.repeated_action_key_vibration', false);
      await prefs.setBool('_sound_vibration_reset_v2', true);  // Mark as reset
      debugPrint('✅ Sound/Vibration forced to OFF (one-time reset)');
    }
    
    setState(() {
      // Sound Settings - use same key as Custom_theme.dart
      // ✅ CRITICAL: Read from multiple keys for compatibility
      audioFeedback = prefs.getBool('audio_feedback') ?? 
                     prefs.getBool('sound_enabled') ?? 
                     prefs.getBool('flutter.sound_enabled') ?? 
                     false;
      // ✅ CRITICAL: Read from flutter.sound_volume (0-100 INT) first, then fallback to sound_volume
      final flutterVolume = prefs.getInt('flutter.sound_volume');
      if (flutterVolume != null) {
        soundVolume = flutterVolume.toDouble();
      } else {
        // Fallback: read sound_volume and handle both 0-1 and 0-100 scales
        final volumeValue = prefs.getDouble('sound_volume');
        if (volumeValue != null) {
          soundVolume = volumeValue > 1.0 ? volumeValue : (volumeValue * 100.0);
        } else {
          soundVolume = 50.0; // Default 50%
        }
      }
      keyPressSounds = prefs.getBool('key_press_sounds') ?? 
                      prefs.getBool('flutter.key_press_sounds') ?? false;  // ✅ Changed default to FALSE
      longPressKeySounds = prefs.getBool('long_press_key_sounds') ?? 
                          prefs.getBool('flutter.long_press_key_sounds') ?? false;  // ✅ Changed default to FALSE
      repeatedActionKeySounds = prefs.getBool('repeated_action_key_sounds') ?? 
                                prefs.getBool('flutter.repeated_action_key_sounds') ?? false;  // ✅ Changed default to FALSE
      // Use same key as Custom_theme.dart: 'selected_sound'
      _selectedSound = prefs.getString('selected_sound') ?? 'click.mp3';
      
      // Vibration Settings
      // ✅ CRITICAL: Read from multiple keys for compatibility - default to false (OFF)
      hapticFeedback = prefs.getBool('haptic_feedback') ?? 
                      prefs.getBool('vibration_enabled') ?? 
                      prefs.getBool('flutter.vibration_enabled') ?? 
                      false; // ✅ Default to false (OFF)
      vibrationMode = prefs.getString('vibration_mode') ?? 'Use haptic feedback interface';
      // ✅ CRITICAL: Read vibration duration from flutter.vibration_ms (INT) first, then fallback
      final flutterVibrationMs = prefs.getInt('flutter.vibration_ms');
      if (flutterVibrationMs != null) {
        vibrationDuration = flutterVibrationMs.toDouble();
      } else {
        vibrationDuration = prefs.getDouble('vibration_duration') ?? 50.0;
      }
      keyPressVibration = prefs.getBool('key_press_vibration') ?? 
                         prefs.getBool('flutter.key_press_vibration') ?? false; // ✅ Default to false
      longPressKeyVibration = prefs.getBool('long_press_key_vibration') ?? 
                             prefs.getBool('flutter.long_press_key_vibration') ?? false; // ✅ Default to false
      repeatedActionKeyVibration = prefs.getBool('repeated_action_key_vibration') ?? 
                                  prefs.getBool('flutter.repeated_action_key_vibration') ?? false; // ✅ Default to false
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
    
    // Save Sound Settings - use same key as Custom_theme.dart
    await prefs.setBool('audio_feedback', audioFeedback);
    // ✅ CRITICAL: Also save to sound_enabled for Android service compatibility
    await prefs.setBool('sound_enabled', audioFeedback);
    await prefs.setBool('flutter.sound_enabled', audioFeedback);
    await prefs.setDouble('sound_volume', soundVolume); // 0-100 scale
    // ✅ CRITICAL: Also save to flutter.sound_volume as INT for Android service
    await prefs.setInt('flutter.sound_volume', soundVolume.toInt());
    await prefs.setBool('key_press_sounds', keyPressSounds);
    await prefs.setBool('flutter.key_press_sounds', keyPressSounds);
    await prefs.setBool('long_press_key_sounds', longPressKeySounds);
    await prefs.setBool('flutter.long_press_key_sounds', longPressKeySounds);
    await prefs.setBool('repeated_action_key_sounds', repeatedActionKeySounds);
    await prefs.setBool('flutter.repeated_action_key_sounds', repeatedActionKeySounds);
    // Save selected sound using same key as Custom_theme.dart
    await prefs.setString('selected_sound', _selectedSound);
    
    // Save Vibration Settings
    await prefs.setBool('haptic_feedback', hapticFeedback);
    // ✅ CRITICAL: Also save to flutter.vibration_enabled for Android service compatibility
    await prefs.setBool('vibration_enabled', hapticFeedback);
    await prefs.setBool('flutter.vibration_enabled', hapticFeedback);
    await prefs.setString('vibration_mode', vibrationMode);
    await prefs.setDouble('vibration_duration', vibrationDuration);
    // ✅ CRITICAL: Also save to flutter.vibration_ms for Android service compatibility
    await prefs.setInt('flutter.vibration_ms', vibrationDuration.toInt());
    await prefs.setBool('key_press_vibration', keyPressVibration);
    await prefs.setBool('flutter.key_press_vibration', keyPressVibration);
    await prefs.setBool('long_press_key_vibration', longPressKeyVibration);
    await prefs.setBool('flutter.long_press_key_vibration', longPressKeyVibration);
    await prefs.setBool('repeated_action_key_vibration', repeatedActionKeyVibration);
    await prefs.setBool('flutter.repeated_action_key_vibration', repeatedActionKeyVibration);
    // ✅ CRITICAL: Save use_haptic_interface for Android service
    await prefs.setBool('flutter.use_haptic_interface', vibrationMode == 'Use haptic feedback interface');
    
    debugPrint('✅ Sound & Vibration settings saved (selected_sound: $_selectedSound)');
    
    // Send to native keyboard - use same method as Custom_theme.dart
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
        'soundEnabled': audioFeedback,
        'soundVolume': soundVolume / 100.0,
        'vibrationEnabled': hapticFeedback,
        'vibrationMs': vibrationDuration.toInt(),
      });
      debugPrint('✅ Sound & Vibration settings synced to Firebase');
    } catch (e) {
      debugPrint('⚠ Failed to sync to Firebase: $e');
      // Don't block user if Firebase fails
    }
  }
  
  /// Send settings to native keyboard
  Future<void> _sendSettingsToKeyboard() async {
    try {
      // ✅ CRITICAL: soundEnabled should be based on audioFeedback only
      // Granular settings (keyPressSounds, etc.) control individual sound types, not main enabled state
      await _channel.invokeMethod('updateSettings', {
        'soundEnabled': audioFeedback, // Use audioFeedback directly, not && keyPressSounds
        'soundVolume': soundVolume / 100.0, // Convert from 0-100 to 0-1 scale
        'keyPressSounds': keyPressSounds,
        'longPressSounds': longPressKeySounds,
        'repeatedActionSounds': repeatedActionKeySounds,
        'vibrationEnabled': hapticFeedback, // Use hapticFeedback directly, not && keyPressVibration
        'vibrationMs': vibrationDuration.toInt(),
        'useHapticInterface': vibrationMode == 'Use haptic feedback interface',
        'keyPressVibration': keyPressVibration,
        'longPressVibration': longPressKeyVibration,
        'repeatedActionVibration': repeatedActionKeyVibration,
      });
      
      // Use same method as Custom_theme.dart to set keyboard sound
      if (audioFeedback && _selectedSound.isNotEmpty) {
        await _soundChannel.invokeMethod('setKeyboardSound', {'file': _selectedSound});
        debugPrint('📤 Sound set to native keyboard: $_selectedSound');
      }
      
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

  // Removed sound pack selection - sounds are now managed in Custom_theme.dart
  // This screen only controls sound enable/disable and volume settings
  
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
          'Sounds & Vibration',
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

            // Sounds Settings Section
            _buildSectionHeader(
              title: 'Sounds Settings',
              isEnabled: audioFeedback,
              onToggle: (value) {
                setState(() => audioFeedback = value);
                _saveSettings();
              },
            ),
            const SizedBox(height: 16),

            // Sound pack selection removed - managed in Custom Theme Editor
            // Users can select sounds from the Theme Editor's Sound tab

            const SizedBox(height: 12),

            // Sound volume for input events
            _buildSliderSetting(
              title: 'Sound volume for input events',
              portraitValue: soundVolume,
              onPortraitChanged: (value) {
                setState(() => soundVolume = value);
                _saveSettings();
              },
              min: 0.0,
              max: 100.0,
              unit: '%',
              portraitLabel: 'Sounds',
              showLandscape: false,
            ),

            const SizedBox(height: 12),

            // Key press sounds
            _buildToggleSetting(
              title: 'Key press sounds',
              description: keyPressSounds ? 'Enabled' : 'Disabled',
              value: keyPressSounds,
              onChanged: (value) {
                setState(() => keyPressSounds = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Long press key sounds
            _buildToggleSetting(
              title: 'Long press key sounds',
              description: longPressKeySounds ? 'Enabled' : 'Disabled',
              value: longPressKeySounds,
              onChanged: (value) {
                setState(() => longPressKeySounds = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Repeated action key sounds
            _buildToggleSetting(
              title: 'Repeated action key sounds',
              description: repeatedActionKeySounds ? 'Enabled' : 'Disabled',
              value: repeatedActionKeySounds,
              onChanged: (value) {
                setState(() => repeatedActionKeySounds = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 32),

            // Haptic feedback & Vibration Section
            _buildSectionHeader(
              title: 'Haptic feedback & Vibration',
              isEnabled: hapticFeedback,
              onToggle: (value) {
                setState(() => hapticFeedback = value);
                _saveSettings();
              },
            ),
            const SizedBox(height: 16),

            const SizedBox(height: 12),

            // Vibration mode
            _buildVibrationModeCard(
              title: 'Vibration mode',
              subtitle: vibrationMode,
              onTap: () => _showVibrationModeDialog(),
            ),

            const SizedBox(height: 12),

            // Vibration duration
            _buildSliderSetting(
              title: 'Vibration duration',
              portraitValue: vibrationDuration,
              onPortraitChanged: (value) {
                setState(() => vibrationDuration = value);
                _saveSettings();
              },
              min: 10.0,
              max: 200.0,
              unit: 'ms',
              portraitLabel: 'Vibration',
              showLandscape: false,
            ),

            const SizedBox(height: 12),

            // // Vibration mode (disabled)
            // _buildDisabledVibrationModeCard(
            //   title: 'Vibration mode',
            //   description:
            //       'Hardware is missing in your device, Need hardware for use features',
            // ),

            const SizedBox(height: 12),

            // Key press vibration
            _buildToggleSetting(
              title: 'Key press vibration',
              description: keyPressVibration ? 'Enabled' : 'Disabled',
              value: keyPressVibration,
              onChanged: (value) {
                setState(() => keyPressVibration = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Long press key vibration
            _buildToggleSetting(
              title: 'Long press key vibration',
              description: longPressKeyVibration ? 'Enabled' : 'Disabled',
              value: longPressKeyVibration,
              onChanged: (value) {
                setState(() => longPressKeyVibration = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Repeated action key vibration
            _buildToggleSetting(
              title: 'Repeated action key vibration',
              description: repeatedActionKeyVibration ? 'Enabled' : 'Disabled',
              value: repeatedActionKeyVibration,
              onChanged: (value) {
                setState(() => repeatedActionKeyVibration = value);
                _saveSettings();
              },
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

  /// Build section header with prominent toggle switch
  Widget _buildSectionHeader({
    required String title,
    required bool isEnabled,
    required ValueChanged<bool> onToggle,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isEnabled 
            ? [AppColors.secondary.withOpacity(0.1), AppColors.secondary.withOpacity(0.05)]
            : [AppColors.grey.withOpacity(0.1), AppColors.grey.withOpacity(0.05)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isEnabled ? AppColors.secondary.withOpacity(0.3) : AppColors.grey.withOpacity(0.2),
          width: 2,
        ),
      ),
      child: Row(
        children: [
          // Icon indicator
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: isEnabled ? AppColors.secondary : AppColors.grey,
              shape: BoxShape.circle,
            ),
            child: Icon(
              title.contains('Sound') ? Icons.volume_up : Icons.vibration,
              color: AppColors.white,
              size: 24,
            ),
          ),
          const SizedBox(width: 16),
          
          // Title and status
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
                Row(
                  children: [
                    Container(
                      width: 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: isEnabled ? Colors.green : Colors.red,
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Text(
                      isEnabled ? 'Enabled' : 'Disabled',
                      style: AppTextStyle.bodySmall.copyWith(
                        color: isEnabled ? Colors.green : Colors.red,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          
          // Large toggle switch
          Transform.scale(
            scale: 1.2,
            child: CustomToggleSwitch(
              value: isEnabled,
              onChanged: onToggle,
              width: 56.0,
              height: 20.0,
              knobSize: 28.0,
            ),
          ),
        ],
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

  Widget _buildVibrationModeCard({
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

  Widget _buildDisabledVibrationModeCard({
    required String title,
    required String description,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey.withOpacity(0.5),
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
                    color: AppColors.grey,
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
          Icon(Icons.block, color: AppColors.grey, size: 16),
        ],
      ),
    );
  }

  void _showVibrationModeDialog() {
    String tempVibrationMode = vibrationMode; // Temporary selection
    
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return Dialog(
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
              child: Container(
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
                      'Vibration Mode',
                      style: AppTextStyle.titleLarge.copyWith(
                        color: AppColors.primary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Select how vibration feedback is triggered',
                      style: AppTextStyle.bodySmall.copyWith(
                        color: AppColors.grey,
                      ),
                    ),
                    Divider(color: AppColors.lightGrey, thickness: 1),

                    // Vibration Mode Options
                    _buildVibrationModeOptionInDialog(
                      'Use vibrator directly',
                      tempVibrationMode,
                      (value) {
                        setDialogState(() {
                          tempVibrationMode = value;
                        });
                      },
                    ),
                    const SizedBox(height: 12),
                    _buildVibrationModeOptionInDialog(
                      'Use haptic feedback interface',
                      tempVibrationMode,
                      (value) {
                        setDialogState(() {
                          tempVibrationMode = value;
                        });
                      },
                    ),

                    const SizedBox(height: 20),

                    // Apply Button
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton(
                            onPressed: () => Navigator.of(context).pop(),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppColors.lightGrey,
                              foregroundColor: AppColors.primary,
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
                        const SizedBox(width: 8),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: () {
                              setState(() {
                                vibrationMode = tempVibrationMode;
                              });
                              _saveSettings();
                              Navigator.of(context).pop();
                            },
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
                              'Apply',
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
      },
    );
  }

  Widget _buildVibrationModeOptionInDialog(
    String mode,
    String currentMode,
    Function(String) onChanged,
  ) {
    bool isSelected = currentMode == mode;
    
    return GestureDetector(
      onTap: () {
        onChanged(mode);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.secondary.withOpacity(0.1) : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: isSelected ? AppColors.secondary : AppColors.lightGrey,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            // Radio Button
            Radio<String>(
              value: mode,
              groupValue: currentMode,
              onChanged: (String? value) {
                if (value != null) {
                  onChanged(value);
                }
              },
              activeColor: AppColors.secondary,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
            const SizedBox(width: 8),

            // Content
            Expanded(
              child: Text(
                mode,
                style: AppTextStyle.titleMedium.copyWith(
                  color: isSelected ? AppColors.secondary : AppColors.primary,
                  fontWeight: isSelected ? FontWeight.w800 : FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
