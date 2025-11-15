import 'package:flutter/material.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

const List<String> _letterAndSpaceTargets = [
  'regular',
  'space',
];

const List<String> _snowDecorTargets = [
  'regular',
  'space',
  'enter',
  'shift',
  'backspace',
  'symbols',
  'emoji',
  'mic',
  'globe',
  'voice',
];

const List<String> _allPrimaryKeyTargets = [
  'regular',
  'space',
  'enter',
  'shift',
  'backspace',
  'symbols',
  'emoji',
  'mic',
  'globe',
  'voice',
];

/// Visual button selector aligned with the new theme editor look.
/// Presents a grid of ready-made button styles plus a custom option.
class ButtonStyleSelectorScreen extends StatefulWidget {
  final KeyboardThemeV2 currentTheme;
  final Function(KeyboardThemeV2) onThemeUpdated;
  final bool showAppBar;

  const ButtonStyleSelectorScreen({
    super.key,
    required this.currentTheme,
    required this.onThemeUpdated,
    this.showAppBar = true,
  });

  @override
  State<ButtonStyleSelectorScreen> createState() => _ButtonStyleSelectorScreenState();
}

class _ButtonStyleSelectorScreenState extends State<ButtonStyleSelectorScreen> {
  late KeyboardThemeV2 _currentTheme;
  late String _selectedStyleId;
  bool _showCustomPalette = false;

  final List<_ButtonStyleOption> _buttonStyles = [
    _ButtonStyleOption(
      id: 'custom',
      name: 'Custom',
      description: 'Design your own look',
      preset: 'rounded',
      radius: 14,
      backgroundColor: Colors.white,
      icon: Icons.add,
      iconColor: AppColors.secondary,
      showBorder: true,
      borderColor: AppColors.secondary,
      borderWidth: 2,
      enableShadow: false,
      textColor: AppColors.black,
      accentColor: AppColors.secondary,
      enableColorPalette: true,
      isCustom: true,
    ),
    _ButtonStyleOption(
      id: 'download_ocean',
      name: 'Ocean Drop',
      description: 'Cool blue gradient with download icon',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFF6DD5FA), Color(0xFF2193B0)],
      icon: Icons.download,
      iconColor: Colors.white,
      accentColor: const Color(0xFF1F8DD6),
    ),
    _ButtonStyleOption(
      id: 'download_sunrise',
      name: 'Sunrise',
      description: 'Warm gradient download button',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFFFC371), Color(0xFFFF5F6D)],
      icon: Icons.download,
      iconColor: Colors.white,
      accentColor: const Color(0xFFFF5F6D),
    ),
    _ButtonStyleOption(
      id: 'download_mint',
      name: 'Mint Drop',
      description: 'Mint gradient download button',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFB2FEFA), Color(0xFF0ED2F7)],
      icon: Icons.download,
      iconColor: const Color(0xFF015B7E),
      accentColor: const Color(0xFF0AA6D4),
      textColor: Colors.white,
    ),
    _ButtonStyleOption(
      id: 'letter_navy',
      name: 'Navy A',
      description: 'Solid navy letter key',
      preset: 'bordered',
      radius: 16,
      backgroundColor: const Color(0xFF002B5B),
      label: 'A',
      labelColor: Colors.white,
      accentColor: const Color(0xFF002B5B),
    ),
    _ButtonStyleOption(
      id: 'letter_crimson',
      name: 'Crimson A',
      description: 'Bold crimson letter',
      preset: 'rounded',
      radius: 16,
      backgroundColor: const Color(0xFFFF3A5A),
      label: 'A',
      labelColor: Colors.white,
      accentColor: const Color(0xFFFF3A5A),
    ),
    _ButtonStyleOption(
      id: 'letter_outline',
      name: 'Outline',
      description: 'Outlined letter with accent ring',
      preset: 'bordered',
      radius: 18,
      backgroundColor: Colors.white,
      label: 'A',
      labelColor: const Color(0xFF0D47A1),
      showBorder: true,
      borderColor: const Color(0xFF0D47A1),
      textColor: const Color(0xFF0D47A1),
      accentColor: const Color(0xFF0D47A1),
    ),
    _ButtonStyleOption(
      id: 'letter_gold',
      name: 'Golden',
      description: 'Gold gradient letter button',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFFFE082), Color(0xFFFFC107)],
      label: 'A',
      labelColor: const Color(0xFF6F4E00),
      accentColor: const Color(0xFFFFB300),
      badgeColor: Colors.white,
    ),
    _ButtonStyleOption(
      id: 'letter_violet',
      name: 'Violet',
      description: 'Violet gradient letter button',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFB24592), Color(0xFFF15F79)],
      label: 'A',
      labelColor: Colors.white,
      accentColor: const Color(0xFFE2547D),
    ),
    _ButtonStyleOption(
      id: 'letter_sky',
      name: 'Sky',
      description: 'Sky blue letter button',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFD0E7FF), Color(0xFF64B5F6)],
      label: 'A',
      labelColor: Colors.white,
      accentColor: const Color(0xFF64B5F6),
    ),
    _ButtonStyleOption(
      id: 'bubble_mint',
      name: 'Mint',
      description: 'Soft mint bubble with arrow',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFD7FFF3), Color(0xFF56E0A0)],
      icon: Icons.file_download,
      iconColor: const Color(0xFF0F996A),
      accentColor: const Color(0xFF0F996A),
      textColor: const Color(0xFF0F996A),
      overlayIcon: 'download',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_peach',
      name: 'Peach',
      description: 'Peach bubble with heart',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFD2E5), Color(0xFFF48FB1)],
      icon: Icons.favorite,
      iconColor: Colors.white,
      accentColor: const Color(0xFFF06292),
      overlayIcon: 'heart',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_blue',
      name: 'Azure',
      description: 'Azure bubble with chat icon',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFE3F2FD), Color(0xFF64B5F6)],
      icon: Icons.chat_bubble,
      iconColor: Colors.white,
      accentColor: const Color(0xFF64B5F6),
      overlayIcon: 'chat',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_green',
      name: 'Forest',
      description: 'Forest bubble with tree',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFE8F5E9), Color(0xFF81C784)],
      icon: Icons.park,
      iconColor: const Color(0xFF2E7D32),
      accentColor: const Color(0xFF4CAF50),
      textColor: const Color(0xFF2E7D32),
      overlayIcon: 'leaf',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_cat',
      name: 'Kitty',
      description: 'Cute cat bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFF0F6), Color(0xFFFFC1E3)],
      icon: Icons.pets,
      iconColor: const Color(0xFFF06292),
      accentColor: const Color(0xFFF06292),
      overlayIcon: 'cat',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_bell',
      name: 'Bell',
      description: 'Notification bell bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFF9C4), Color(0xFFFFF176)],
      icon: Icons.notifications,
      iconColor: const Color(0xFFFBC02D),
      accentColor: const Color(0xFFFBC02D),
      overlayIcon: 'bell',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_chat',
      name: 'Bubble',
      description: 'Blue messaging bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFB3E5FC), Color(0xFF29B6F6)],
      icon: Icons.chat,
      iconColor: Colors.white,
      accentColor: const Color(0xFF29B6F6),
      overlayIcon: 'chat',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_lollipop',
      name: 'Lollipop',
      description: 'Sweet treat bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFE0F7), Color(0xFFFF8AC9)],
      icon: Icons.icecream,
      iconColor: Colors.white,
      accentColor: const Color(0xFFFF6BB5),
      overlayIcon: 'candy',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_music',
      name: 'Melody',
      description: 'Musical note bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFE0F7FA), Color(0xFF00BCD4)],
      icon: Icons.music_note,
      iconColor: Colors.white,
      accentColor: const Color(0xFF0097A7),
      overlayIcon: 'note',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_ghost',
      name: 'Ghost',
      description: 'Playful ghost bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFF7F0FF), Color(0xFFD1C4E9)],
      icon: Icons.emoji_emotions,
      iconColor: const Color(0xFF7E57C2),
      accentColor: const Color(0xFF7E57C2),
      badgeColor: Colors.white,
      overlayIcon: 'ghost',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_candy',
      name: 'Candy',
      description: 'Candy swirl bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFD6E1), Color(0xFFFF8A80)],
      icon: Icons.cake,
      iconColor: Colors.white,
      accentColor: const Color(0xFFFF6F61),
      overlayIcon: 'candy',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_heart',
      name: 'Love',
      description: 'Love bubble with heart',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFCDD2), Color(0xFFE57373)],
      icon: Icons.favorite,
      iconColor: Colors.white,
      accentColor: const Color(0xFFE53935),
      overlayIcon: 'heart',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_star',
      name: 'Starry',
      description: 'Star bubble with highlight',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFF8E1), Color(0xFFFFD54F)],
      icon: Icons.star,
      iconColor: Colors.white,
      accentColor: const Color(0xFFFFB300),
      overlayIcon: 'star',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_snap',
      name: 'Snap',
      description: 'Snap style yellow bubble',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFF59D), Color(0xFFFFEE58)],
      icon: Icons.bolt,
      iconColor: const Color(0xFFF9A825),
      accentColor: const Color(0xFFFDD835),
      overlayIcon: 'bolt',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'bubble_ball',
      name: 'Sports',
      description: 'Playful sports ball',
      preset: 'bubble',
      radius: 999,
      gradient: const [Color(0xFFFFE0B2), Color(0xFFF57C00)],
      icon: Icons.sports_baseball,
      iconColor: Colors.white,
      accentColor: const Color(0xFFEF6C00),
      overlayIcon: 'ball',
      overlayTargets: const ['space'],
    ),
    _ButtonStyleOption(
      id: 'watermelon_slice',
      name: 'Watermelon',
      description: 'Juicy slice with seeds',
      preset: 'slice',
      radius: 999,
      gradient: const [Color(0xFFFF6B6B), Color(0xFFFE4A49)],
      icon: Icons.change_history,
      iconColor: const Color(0xFFFF6B6B),
      textColor: Colors.white,
      accentColor: const Color(0xFF2ECC71),
      overlayIcon: 'watermelon',
      overlayTargets: _allPrimaryKeyTargets,
      showBorder: false,
      enableShadow: true,
    ),
    _ButtonStyleOption(
      id: 'violet_butterfly',
      name: 'Butterfly',
      description: 'Fluttering wing keys',
      preset: 'rounded',
      radius: 20,
      gradient: const [Color(0xFF9C27B0), Color(0xFFE040FB)],
      icon: Icons.flutter_dash,
      iconColor: const Color(0xFFE3B8FF),
      textColor: Colors.white,
      accentColor: const Color(0xFFCE93D8),
      overlayIcon: 'butterfly',
      overlayTargets: const ['regular'],
      enableShadow: true,
    ),
    _ButtonStyleOption(
      id: 'star_fiesta',
      name: 'Starburst',
      description: 'Bright golden stars',
      preset: 'star',
      radius: 18,
      gradient: const [Color(0xFFFFD740), Color(0xFFFFA000)],
      icon: Icons.star,
      iconColor: const Color(0xFFFFF59D),
      textColor: const Color(0xFF4E342E),
      accentColor: const Color(0xFFFFC107),
      enableShadow: true,
    ),
    _ButtonStyleOption(
      id: 'heart_bliss',
      name: 'Hearts',
      description: 'Sweet pink hearts',
      preset: 'heart',
      radius: 22,
      gradient: const [Color(0xFFFF5EBE), Color(0xFFE91E63)],
      icon: Icons.favorite,
      iconColor: Colors.white,
      textColor: Colors.white,
      accentColor: const Color(0xFFF06292),
      enableShadow: true,
    ),
    _ButtonStyleOption(
      id: 'snow_caps',
      name: 'Snowcap',
      description: 'Frosted winter keys',
      preset: 'rounded',
      radius: 18,
      gradient: const [Color(0xFFFFC0E6), Color(0xFFFF9ED1)],
      icon: Icons.ac_unit,
      iconColor: Colors.white,
      textColor: Colors.white,
      accentColor: const Color(0xFFE57373),
      overlayIcon: 'snowcap',
      overlayTargets: _snowDecorTargets,
      enableShadow: true,
    ),
  ];

  @override
  void initState() {
    super.initState();
    _currentTheme = widget.currentTheme;
    final initial = _findMatchingStyle(widget.currentTheme);
    _selectedStyleId = initial?.id ?? _buttonStyles.first.id;
    _showCustomPalette = initial?.enableColorPalette ?? _buttonStyles.first.enableColorPalette;
  }

  @override
  Widget build(BuildContext context) {
    final content = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Choose Button Style',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Tap a style to instantly preview it on the keyboard.',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 24),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _buttonStyles.length,
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 5,
              mainAxisSpacing: 20,
              crossAxisSpacing: 12,
              childAspectRatio: 0.85,
            ),
            itemBuilder: (context, index) {
              final option = _buttonStyles[index];
              final isSelected = option.id == _selectedStyleId;
              return _buildButtonStyleCard(option, isSelected);
            },
          ),
          if (_showCustomPalette) ...[
            const SizedBox(height: 28),
            _buildColorCustomizationSection(),
          ],
        ],
      ),
    );

    if (widget.showAppBar) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Button Style'),
          backgroundColor: AppColors.primary,
          foregroundColor: AppColors.white,
          elevation: 0,
        ),
        body: SingleChildScrollView(child: content),
      );
    }

    return SingleChildScrollView(child: content);
  }

  _ButtonStyleOption? _findMatchingStyle(KeyboardThemeV2 theme) {
    for (final option in _buttonStyles) {
      if (option.isCustom) {
        continue;
      }
      if (option.preset == theme.keys.preset &&
          option.themeKeyColor.value == theme.keys.bg.value) {
        return option;
      }
    }
    // fallback to custom option if available
    return _buttonStyles.firstWhere((option) => option.isCustom, orElse: () => _buttonStyles.first);
  }

  Widget _buildButtonStyleCard(_ButtonStyleOption option, bool isSelected) {
    final gradient = option.gradient;

    return GestureDetector(
      onTap: () => _selectButtonStyle(option),
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
                  gradient: gradient != null
                      ? LinearGradient(
                          colors: gradient,
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        )
                      : null,
                  color: gradient == null ? option.backgroundColor : null,
                  border: Border.all(
                    color: isSelected
                        ? AppColors.secondary
                        : (option.showBorder ? option.borderColor : Colors.transparent),
                    width: isSelected ? 3 : (option.showBorder ? option.borderWidth : 0),
                  ),
                  boxShadow: option.enableShadow
                      ? [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.18),
                            blurRadius: option.shadowBlur,
                            offset: Offset(0, option.shadowOffsetY),
                          ),
                        ]
                      : [],
                ),
                child: Center(
                  child: option.label != null
                      ? Text(
                          option.label!,
                          style: TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                            color: option.labelColor,
                          ),
                        )
                      : Icon(
                          option.icon,
                          color: option.iconColor,
                          size: 28,
                        ),
                ),
              ),
              if (option.badgeColor != null)
                Positioned(
                  top: -2,
                  right: -2,
                  child: Container(
                    width: 16,
                    height: 16,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: option.badgeColor,
                      border: Border.all(color: Colors.white, width: 2),
                    ),
                    child: option.badgeIcon != null
                        ? Icon(option.badgeIcon, size: 10, color: Colors.white)
                        : null,
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
          const SizedBox(height: 8),
          // Text(
          //   option.name,
          //   style: AppTextStyle.bodySmall.copyWith(
          //     color: AppColors.black,
          //     fontWeight: FontWeight.w500,
          //   ),
          //   maxLines: 1,
          //   overflow: TextOverflow.ellipsis,
          // ),
        ],
      ),
    );
  }

  void _selectButtonStyle(_ButtonStyleOption option) {
    setState(() {
      _selectedStyleId = option.id;
      _showCustomPalette = option.enableColorPalette;
    });

    if (option.isCustom) {
      // Still update preset and radius so custom palettes preview correctly.
      final updatedKeys = _currentTheme.keys.copyWith(
        preset: option.preset,
        radius: option.radius,
        styleId: option.id,
        gradient: const [],
        overlayIcon: null,
        overlayIconColor: null,
        overlayIconTargets: const [],
      );
      _updateTheme(_currentTheme.copyWith(keys: updatedKeys));
      return;
    }

    _applyStyleToTheme(option);
  }

  void _applyStyleToTheme(_ButtonStyleOption option) {
    final updatedKeys = _currentTheme.keys.copyWith(
      preset: option.preset,
      radius: option.radius,
      bg: option.themeKeyColor,
      text: option.textColor,
      pressed: _derivePressedColor(option),
      border: _buildBorder(option),
      shadow: _buildShadow(option),
      styleId: option.id,
      gradient: option.gradient ?? const [],
      overlayIcon: option.overlayIcon,
      overlayIconColor: option.overlayIcon != null ? option.iconColor : null,
      overlayIconTargets: option.overlayIcon != null ? option.overlayTargets : const [],
    );

    final updatedTheme = _currentTheme.copyWith(
      keys: updatedKeys,
      specialKeys: _currentTheme.specialKeys.copyWith(
        accent: option.accentColor,
      ),
    );

    _updateTheme(updatedTheme);
  }

  Color _derivePressedColor(_ButtonStyleOption option) {
    if (option.gradient != null && option.gradient!.isNotEmpty) {
      final base = option.gradient!.last;
      return _darken(base, 0.12);
    }
    return _darken(option.themeKeyColor, 0.08);
  }

  Color _darken(Color color, double amount) {
    final hsl = HSLColor.fromColor(color);
    final adjusted = hsl.withLightness((hsl.lightness - amount).clamp(0.0, 1.0));
    return adjusted.toColor();
  }

  ThemeKeysBorder _buildBorder(_ButtonStyleOption option) {
    if (option.showBorder) {
      return ThemeKeysBorder(
        enabled: true,
        color: option.borderColor,
        widthDp: option.borderWidth,
      );
    }
    return ThemeKeysBorder(
      enabled: false,
      color: _currentTheme.keys.border.color,
      widthDp: _currentTheme.keys.border.widthDp,
    );
  }

  ThemeKeysShadow _buildShadow(_ButtonStyleOption option) {
    if (!option.enableShadow) {
      return ThemeKeysShadow(
        enabled: false,
        elevationDp: 0,
        glow: false,
      );
    }
    return ThemeKeysShadow(
      enabled: true,
      elevationDp: option.shadowElevation,
      glow: false,
    );
  }

  Widget _buildColorCustomizationSection() {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.06),
            blurRadius: 16,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Fine-tune Colors',
            style: AppTextStyle.titleSmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 18,
            runSpacing: 18,
            children: [
              _buildColorOption('Key BG', _currentTheme.keys.bg, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(bg: color),
                ));
              }),
              _buildColorOption('Key Text', _currentTheme.keys.text, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(text: color),
                ));
              }),
              _buildColorOption('Pressed', _currentTheme.keys.pressed, (color) {
                _updateTheme(_currentTheme.copyWith(
                  keys: _currentTheme.keys.copyWith(pressed: color),
                ));
              }),
              _buildColorOption('Accent', _currentTheme.specialKeys.accent, (color) {
                _updateTheme(_currentTheme.copyWith(
                  specialKeys: _currentTheme.specialKeys.copyWith(accent: color),
                ));
              }),
            ],
          ),
          const SizedBox(height: 20),
          Text(
            'Corner Radius',
            style: AppTextStyle.bodySmall.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w500,
            ),
          ),
          Slider(
            value: _currentTheme.keys.radius.clamp(0, 24),
            min: 0,
            max: 24,
            divisions: 24,
            label: _currentTheme.keys.radius.toStringAsFixed(0),
            onChanged: (value) {
              _updateTheme(_currentTheme.copyWith(
                keys: _currentTheme.keys.copyWith(radius: value),
              ));
            },
          ),
        ],
      ),
    );
  }

  Widget _buildColorOption(String label, Color color, ValueChanged<Color> onColorSelected) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTap: () => _showColorPicker(color, onColorSelected),
          child: Container(
            width: 58,
            height: 58,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: color,
              border: Border.all(
                color: Colors.white,
                width: 3,
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.15),
                  blurRadius: 10,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 6),
        Text(
          label,
          style: AppTextStyle.bodySmall.copyWith(
            color: AppColors.grey,
            fontWeight: FontWeight.w500,
          ),
        ),
      ],
    );
  }

  void _updateTheme(KeyboardThemeV2 newTheme) {
    setState(() {
      _currentTheme = newTheme;
    });
    widget.onThemeUpdated(newTheme);
  }

  void _showColorPicker(Color currentColor, ValueChanged<Color> onColorSelected) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Pick a Color'),
        content: SingleChildScrollView(
          child: Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              Colors.red,
              Colors.pink,
              Colors.purple,
              Colors.deepPurple,
              Colors.indigo,
              Colors.blue,
              Colors.lightBlue,
              Colors.cyan,
              Colors.teal,
              Colors.green,
              Colors.lightGreen,
              Colors.lime,
              Colors.yellow,
              Colors.amber,
              Colors.orange,
              Colors.deepOrange,
              Colors.brown,
              Colors.grey,
              Colors.blueGrey,
              Colors.black,
              Colors.white,
              const Color(0xFFFF6B9D),
              const Color(0xFF4CAF50),
              const Color(0xFF9C27B0),
              const Color(0xFFFF9800),
              const Color(0xFF00BCD4),
              const Color(0xFF2196F3),
              const Color(0xFFFFC107),
              const Color(0xFFE91E63),
              const Color(0xFF3F51B5),
              const Color(0xFF009688),
              const Color(0xFF8BC34A),
              const Color(0xFFFF5722),
            ].map((color) {
              return GestureDetector(
                onTap: () {
                  onColorSelected(color);
                  Navigator.of(context).pop();
                },
                child: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: color == currentColor ? AppColors.secondary : Colors.white,
                      width: 3,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

class _ButtonStyleOption {
  final String id;
  final String name;
  final String description;
  final String preset;
  final double radius;
  final IconData? icon;
  final Color iconColor;
  final String? label;
  final Color labelColor;
  final Color backgroundColor;
  final List<Color>? gradient;
  final bool showBorder;
  final Color borderColor;
  final double borderWidth;
  final bool enableShadow;
  final double shadowBlur;
  final double shadowOffsetY;
  final double shadowElevation;
  final bool enableColorPalette;
  final bool isCustom;
  final Color accentColor;
  final Color textColor;
  final Color? badgeColor;
  final IconData? badgeIcon;
  final List<String> overlayTargets;
  final String? overlayIcon;

  const _ButtonStyleOption({
    required this.id,
    required this.name,
    required this.description,
    required this.preset,
    required this.radius,
    this.icon,
    this.iconColor = Colors.white,
    this.label,
    this.labelColor = Colors.white,
    this.backgroundColor = Colors.white,
    this.gradient,
    this.showBorder = false,
    this.borderColor = Colors.transparent,
    this.borderWidth = 2,
    this.enableShadow = true,
    this.shadowBlur = 12,
    this.shadowOffsetY = 6,
    this.shadowElevation = 4,
    this.enableColorPalette = false,
    this.isCustom = false,
    this.accentColor = AppColors.secondary,
    this.textColor = Colors.white,
    this.badgeColor,
    this.badgeIcon,
    this.overlayTargets = const [],
    this.overlayIcon,
  });

  Color get themeKeyColor =>
      gradient != null && gradient!.isNotEmpty ? gradient!.last : backgroundColor;
}
