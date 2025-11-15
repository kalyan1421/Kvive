import 'package:flutter/material.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

const List<_EffectOption> _effectOptions = [
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

/// Visual effect selector aligned with the new theme editor look.
/// Presents a grid of effect options.
class EffectStyleSelectorScreen extends StatefulWidget {
  final KeyboardThemeV2 currentTheme;
  final Function(KeyboardThemeV2) onThemeUpdated;
  final bool showAppBar;

  const EffectStyleSelectorScreen({
    super.key,
    required this.currentTheme,
    required this.onThemeUpdated,
    this.showAppBar = true,
  });

  @override
  State<EffectStyleSelectorScreen> createState() => _EffectStyleSelectorScreenState();
}

class _EffectStyleSelectorScreenState extends State<EffectStyleSelectorScreen> {
  late KeyboardThemeV2 _currentTheme;

  @override
  void initState() {
    super.initState();
    _currentTheme = widget.currentTheme;
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
        effects: ThemeEffects(
          pressAnimation: _currentTheme.effects.pressAnimation,
          globalEffects: const [],
          opacity: _currentTheme.effects.opacity,
        ),
      ));
      return;
    }

    if (wasSelected) {
      // Tapping an active effect clears back to none for quick disable.
      _updateTheme(_currentTheme.copyWith(
        effects: ThemeEffects(
          pressAnimation: _currentTheme.effects.pressAnimation,
          globalEffects: const [],
          opacity: _currentTheme.effects.opacity,
        ),
      ));
      return;
    }

    _updateTheme(_currentTheme.copyWith(
      effects: ThemeEffects(
        pressAnimation: _currentTheme.effects.pressAnimation,
        globalEffects: [effectId],
        opacity: _currentTheme.effects.opacity,
      ),
    ));
  }

  void _updateTheme(KeyboardThemeV2 newTheme) {
    setState(() {
      _currentTheme = newTheme;
    });
    widget.onThemeUpdated(newTheme);
  }

  @override
  Widget build(BuildContext context) {
    final activeEffects = _currentTheme.effects.globalEffects;
    final screenWidth = MediaQuery.of(context).size.width;
    final crossAxisCount = screenWidth < 360 ? 4 : (screenWidth < 520 ? 5 : 5);

    final content = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Choose Effect',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Tap an effect to preview it on the keyboard. Choose "None" to keep the keyboard clean.',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 24),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _effectOptions.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: crossAxisCount,
              mainAxisSpacing: 20,
              crossAxisSpacing: 12,
              childAspectRatio: 0.85,
            ),
            itemBuilder: (context, index) {
              final option = _effectOptions[index];
              final isSelected = _isEffectSelected(option.id, activeEffects);
              return _buildEffectCard(option, isSelected);
            },
          ),
        ],
      ),
    );

    if (widget.showAppBar) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Effects'),
          backgroundColor: AppColors.primary,
          foregroundColor: AppColors.white,
          elevation: 0,
        ),
        body: SingleChildScrollView(child: content),
      );
    }

    return SingleChildScrollView(child: content);
  }

  Widget _buildEffectCard(_EffectOption option, bool isSelected) {
    final gradient = option.gradientColors.length > 1
        ? LinearGradient(
            colors: option.gradientColors,
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          )
        : null;

    return GestureDetector(
      onTap: () => _handleEffectSelection(option.id, isSelected),
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
                  child: option.assetIcon != null
                      ? Image.asset(
                          option.assetIcon!,
                          width: 28,
                          height: 28,
                          color: option.iconColor ?? Colors.white,
                        )
                      : Icon(
                          option.icon,
                          color: option.iconColor ?? Colors.white,
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

class _EffectOption {
  final String id;
  final String label;
  final IconData icon;
  final List<Color> gradientColors;
  final Color? iconColor;
  final String? assetIcon;

  const _EffectOption({
    required this.id,
    required this.label,
    required this.icon,
    required this.gradientColors,
    this.iconColor,
    this.assetIcon,
  });
}

