import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:ai_keyboard/theme/Custom_theme.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

final List<_SoundOption> _soundOptions = [
  _SoundOption(
    id: 'ball',
    name: 'Ball',
    fileName: 'ball.mp3',
    icon: Icons.sports_soccer,
    gradientColors: [
      Color(0xFFFF6B6B),
      Color(0xFFFF8E53),
    ],
  ),
  _SoundOption(
    id: 'bell',
    name: 'Bell',
    fileName: 'bell.mp3',
    icon: Icons.notifications,
    gradientColors: [
      Color(0xFFFFD93D),
      Color(0xFFFFB800),
    ],
  ),
  _SoundOption(
    id: 'click',
    name: 'Click',
    fileName: 'click.mp3',
    icon: Icons.touch_app,
    gradientColors: [
      Color(0xFF4ECDC4),
      Color(0xFF44A08D),
    ],
  ),
  _SoundOption(
    id: 'message',
    name: 'Message',
    fileName: 'Message.mp3',
    icon: Icons.message,
    gradientColors: [
      Color(0xFF667EEA),
      Color(0xFF764BA2),
    ],
  ),
  _SoundOption(
    id: 'heartbeat',
    name: 'Heartbeat',
    fileName: 'heartbeat.mp3',
    icon: Icons.favorite,
    gradientColors: [
      Color(0xFFFF6B9D),
      Color(0xFFFF3D68),
    ],
  ),
  _SoundOption(
    id: 'water_drop',
    name: 'Water Drop',
    fileName: 'water_drop.mp3',
    icon: Icons.water_drop,
    gradientColors: [
      Color(0xFF56E4FF),
      Color(0xFF5B9DF9),
    ],
  ),
  _SoundOption(
    id: 'enter_press',
    name: 'Enter Press',
    fileName: 'enter_press.wav',
    icon: Icons.keyboard_return,
    gradientColors: [
      Color(0xFFA8EDEA),
      Color(0xFFFED6E3),
    ],
  ),
  _SoundOption(
    id: 'key_press',
    name: 'Key Press',
    fileName: 'key_press.wav',
    icon: Icons.keyboard,
    gradientColors: [
      Color(0xFFE0C3FC),
      Color(0xFF8EC5FC),
    ],
  ),
  _SoundOption(
    id: 'space_press',
    name: 'Space Press',
    fileName: 'space_press.wav',
    icon: Icons.space_bar,
    gradientColors: [
      Color(0xFFFFE0B2),
      Color(0xFFFFB74D),
    ],
  ),
  _SoundOption(
    id: 'special_key_press',
    name: 'Special Key',
    fileName: 'special_key_press.wav',
    icon: Icons.star,
    gradientColors: [
      Color(0xFFFFD700),
      Color(0xFFFFA500),
    ],
  ),
];

/// Visual sound selector aligned with the new theme editor look.
/// Presents a grid of sound pack options.
class SoundStyleSelectorScreen extends StatefulWidget {
  final KeyboardThemeV2 currentTheme;
  final Function(KeyboardThemeV2) onThemeUpdated;
  final bool showAppBar;

  const SoundStyleSelectorScreen({
    super.key,
    required this.currentTheme,
    required this.onThemeUpdated,
    this.showAppBar = true,
  });

  @override
  State<SoundStyleSelectorScreen> createState() => _SoundStyleSelectorScreenState();
}

class _SoundStyleSelectorScreenState extends State<SoundStyleSelectorScreen> {
  late KeyboardThemeV2 _currentTheme;
  late String _selectedSoundId;
  static const MethodChannel _soundChannel = MethodChannel('keyboard.sound');

  @override
  void initState() {
    super.initState();
    _currentTheme = widget.currentTheme;
    _loadSelectedSound();
  }

  Future<void> _loadSelectedSound() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final selectedSoundFile = prefs.getString('selected_sound') ?? 'click.mp3';
      
      // Find matching sound option by fileName
      final matchingOption = _soundOptions.firstWhere(
        (option) => option.fileName == selectedSoundFile,
        orElse: () => _soundOptions.first,
      );
      
      setState(() {
        _selectedSoundId = matchingOption.id;
      });
    } catch (e) {
      setState(() {
        _selectedSoundId = _soundOptions.first.id;
      });
    }
  }

  Future<void> _selectSoundOption(_SoundOption option) async {
    setState(() {
      _selectedSoundId = option.id;
    });

    try {
      // Save to SharedPreferences (same key as Custom_theme.dart uses)
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('selected_sound', option.fileName);
      
      // Call native method to apply the sound
      await _soundChannel.invokeMethod('setKeyboardSound', {'file': option.fileName});
      
      // Also update theme for consistency
      _updateTheme(_currentTheme.copyWith(
        sounds: _currentTheme.sounds.copyWith(
          pack: 'custom',
          customUris: {
            'regular': 'assets/sounds/${option.fileName}',
            'space': 'assets/sounds/${option.fileName}',
            'enter': 'assets/sounds/${option.fileName}',
            'backspace': 'assets/sounds/${option.fileName}',
          },
        ),
      ));
    } catch (e) {
      debugPrint('Error setting keyboard sound: $e');
    }
  }

  void _updateTheme(KeyboardThemeV2 newTheme) {
    setState(() {
      _currentTheme = newTheme;
    });
    widget.onThemeUpdated(newTheme);
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final crossAxisCount = screenWidth < 360 ? 4 : (screenWidth < 520 ? 5 : 5);

    final content = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Choose Sound',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Tap a sound to preview it on the keyboard.',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 24),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _soundOptions.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: crossAxisCount,
              mainAxisSpacing: 20,
              crossAxisSpacing: 12,
              childAspectRatio: 0.85,
            ),
            itemBuilder: (context, index) {
              final option = _soundOptions[index];
              final isSelected = option.id == _selectedSoundId;
              return _buildSoundCard(option, isSelected);
            },
          ),
        ],
      ),
    );

    if (widget.showAppBar) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Sounds'),
          backgroundColor: AppColors.primary,
          foregroundColor: AppColors.white,
          elevation: 0,
        ),
        body: SingleChildScrollView(child: content),
      );
    }

    return SingleChildScrollView(child: content);
  }

  Widget _buildSoundCard(_SoundOption option, bool isSelected) {
    final gradient = option.gradientColors.length > 1
        ? LinearGradient(
            colors: option.gradientColors,
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          )
        : null;

    return GestureDetector(
      onTap: () => _selectSoundOption(option),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: gradient,
                  color: gradient == null ? option.gradientColors.first : null,
                  border: Border.all(
                    color: isSelected
                        ? AppColors.secondary
                        : Colors.transparent,
                    width: isSelected ? 3 : 0,
                  ),
                ),
                child: Center(
                  child: Icon(
                    option.icon,
                    color: Colors.white,
                    size: 28,
                  ),
                ),
              ),
              if (isSelected)
                Positioned(
                  bottom: -6,
                  right: -6,
                  child: Container(
                    width: 24,
                    height: 24,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.secondary,
                    ),
                    child: const Icon(Icons.check, size: 16, color: Colors.white),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SoundOption {
  final String id;
  final String name;
  final String fileName;
  final IconData icon;
  final List<Color> gradientColors;

  const _SoundOption({
    required this.id,
    required this.name,
    required this.fileName,
    required this.icon,
    required this.gradientColors,
  });
}

