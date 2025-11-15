import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../theme/theme_v2.dart';

class KeyboardSnapshot extends StatelessWidget {
  const KeyboardSnapshot({
    super.key,
    required this.theme,
    this.aspectRatio = 1.9,
    this.padding = const EdgeInsets.all(12),
    this.showShadow = true,
    this.overlayOpacity,
  });

  final KeyboardThemeV2 theme;
  final double aspectRatio;
  final EdgeInsets padding;
  final bool showShadow;
  final double? overlayOpacity;

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: aspectRatio,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(theme.keys.radius.clamp(8.0, 24.0)),
        child: DecoratedBox(
          decoration: const BoxDecoration(color: Colors.black12),
          child: Stack(
            fit: StackFit.expand,
            children: [
              _KeyboardBackground(theme: theme),
              if (overlayOpacity != null)
                Container(
                  color: Colors.black.withOpacity(overlayOpacity!.clamp(0.0, 1.0)),
                ),
              Padding(
                padding: padding,
                child: Column(
                  children: [
                    _SuggestionBar(theme: theme),
                    const SizedBox(height: 6),
                    Expanded(child: _KeyArea(theme: theme, showShadow: showShadow)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _KeyboardBackground extends StatelessWidget {
  const _KeyboardBackground({required this.theme});

  final KeyboardThemeV2 theme;

  @override
  Widget build(BuildContext context) {
    final background = theme.background;
    final brightness = background.brightness.clamp(0.2, 2.0);

    Widget? backgroundWidget;
    if (background.type == 'gradient' && background.gradient != null) {
      final gradient = background.gradient!;
      backgroundWidget = DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: gradient.colors.map((c) => _applyBrightness(c, brightness)).toList(),
            begin: _gradientBegin(gradient.orientation),
            end: _gradientEnd(gradient.orientation),
            stops: gradient.stops != null && gradient.stops!.length == gradient.colors.length
                ? gradient.stops
                : null,
          ),
        ),
      );
    } else if (background.type == 'image' && background.imagePath != null && background.imagePath!.isNotEmpty) {
      backgroundWidget = _KeyboardImage(
        path: background.imagePath!,
        opacity: background.imageOpacity,
        brightness: brightness,
      );
    }

    backgroundWidget ??= DecoratedBox(
      decoration: BoxDecoration(
        color: _applyBrightness(background.color ?? theme.keys.bg, brightness),
      ),
    );

    return backgroundWidget;
  }

  Alignment _gradientBegin(String orientation) {
    switch (orientation) {
      case 'LEFT_RIGHT':
        return Alignment.centerLeft;
      case 'TL_BR':
        return Alignment.topLeft;
      case 'TR_BL':
        return Alignment.topRight;
      default:
        return Alignment.topCenter;
    }
  }

  Alignment _gradientEnd(String orientation) {
    switch (orientation) {
      case 'LEFT_RIGHT':
        return Alignment.centerRight;
      case 'TL_BR':
        return Alignment.bottomRight;
      case 'TR_BL':
        return Alignment.bottomLeft;
      default:
        return Alignment.bottomCenter;
    }
  }

  Color _applyBrightness(Color color, double multiplier) {
    if (multiplier == 1.0) return color;
    final hsl = HSLColor.fromColor(color);
    final adjustedLightness = (hsl.lightness * multiplier).clamp(0.0, 1.0);
    return hsl.withLightness(adjustedLightness).toColor();
  }
}

class _KeyboardImage extends StatelessWidget {
  const _KeyboardImage({
    required this.path,
    required this.opacity,
    required this.brightness,
  });

  final String path;
  final double opacity;
  final double brightness;

  @override
  Widget build(BuildContext context) {
    Widget image;
    if (path.startsWith('http')) {
      image = Image.network(path, fit: BoxFit.cover);
    } else if (path.startsWith('asset:/')) {
      image = Image.asset(path.replaceFirst('asset:/', ''), fit: BoxFit.cover);
    } else if (!kIsWeb && File(path).existsSync()) {
      image = Image.file(File(path), fit: BoxFit.cover);
    } else if (path.startsWith('file://') && !kIsWeb) {
      final file = File(Uri.parse(path).toFilePath());
      image = Image.file(file, fit: BoxFit.cover);
    } else {
      image = Image.asset(path, fit: BoxFit.cover, errorBuilder: (_, __, ___) => const SizedBox());
    }

    final clampedOpacity = opacity.clamp(0.0, 1.0);
    final multiplier = brightness.clamp(0.2, 2.0);

    final filtered = multiplier == 1.0
        ? image
        : ColorFiltered(
            colorFilter: ColorFilter.matrix(<double>[
              multiplier, 0, 0, 0, 0,
              0, multiplier, 0, 0, 0,
              0, 0, multiplier, 0, 0,
              0, 0, 0, 1, 0,
            ]),
            child: image,
          );

    return Opacity(
      opacity: clampedOpacity,
      child: filtered,
    );
  }
}

class _SuggestionBar extends StatelessWidget {
  const _SuggestionBar({required this.theme});

  final KeyboardThemeV2 theme;

  @override
  Widget build(BuildContext context) {
    final suggestions = theme.suggestions;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: suggestions.inheritFromKeys ? theme.keys.bg : suggestions.bg,
        borderRadius: BorderRadius.circular(theme.keys.radius),
      ),
      child: Row(
        children: [
          _SuggestionChip(theme: theme, text: 'Hello'),
          const SizedBox(width: 8),
          _SuggestionChip(theme: theme, text: 'Thanks'),
          const SizedBox(width: 8),
          _SuggestionChip(theme: theme, text: 'Welcome'),
        ],
      ),
    );
  }
}

class _SuggestionChip extends StatelessWidget {
  const _SuggestionChip({required this.theme, required this.text});

  final KeyboardThemeV2 theme;
  final String text;

  @override
  Widget build(BuildContext context) {
    final chip = theme.suggestions.chip;
    return Expanded(
      child: Container(
        height: 30,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: chip.bg,
          borderRadius: BorderRadius.circular(chip.radius),
        ),
        child: Text(
          text,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontFamily: theme.suggestions.font.family,
            fontSize: theme.suggestions.font.sizeSp,
            fontWeight: theme.suggestions.font.bold ? FontWeight.bold : FontWeight.w500,
            color: theme.suggestions.text,
          ),
        ),
      ),
    );
  }
}

class _KeyArea extends StatelessWidget {
  const _KeyArea({required this.theme, required this.showShadow});

  final KeyboardThemeV2 theme;
  final bool showShadow;

  @override
  Widget build(BuildContext context) {
    final rows = [
      const ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
      const ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
      const ['shift', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', 'back'],
      const ['123', 'emoji', 'space', 'enter'],
    ];

    return Column(
      children: [
        for (var i = 0; i < rows.length; i++) ...[
          Expanded(child: _KeyRow(theme: theme, labels: rows[i], showShadow: showShadow)),
          if (i != rows.length - 1) const SizedBox(height: 6),
        ],
      ],
    );
  }
}

class _KeyRow extends StatelessWidget {
  const _KeyRow({required this.theme, required this.labels, required this.showShadow});

  final KeyboardThemeV2 theme;
  final List<String> labels;
  final bool showShadow;

  @override
  Widget build(BuildContext context) {
    final keyWidgets = <Widget>[];
    for (var i = 0; i < labels.length; i++) {
      final label = labels[i];
      keyWidgets.add(_SnapshotKey(theme: theme, label: label, showShadow: showShadow));
      if (i != labels.length - 1) {
        keyWidgets.add(const SizedBox(width: 6));
      }
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: keyWidgets,
    );
  }
}

class _SnapshotKey extends StatelessWidget {
  const _SnapshotKey({required this.theme, required this.label, required this.showShadow});

  final KeyboardThemeV2 theme;
  final String label;
  final bool showShadow;

  @override
  Widget build(BuildContext context) {
    final isAccent = _accentLabels.contains(label);
    final isSpace = label == 'space';
    final isWide = isSpace || label == 'enter';
    final keyTheme = theme.keys;

    final Widget child = Container(
      decoration: BoxDecoration(
        color: isAccent ? theme.specialKeys.accent : keyTheme.bg,
        borderRadius: BorderRadius.circular(keyTheme.radius),
        border: keyTheme.border.enabled
            ? Border.all(
                color: keyTheme.border.color,
                width: keyTheme.border.widthDp,
              )
            : null,
        boxShadow: keyTheme.shadow.enabled && showShadow
            ? [
                BoxShadow(
                  color: Colors.black.withOpacity(0.08),
                  blurRadius: keyTheme.shadow.elevationDp * 2,
                  offset: const Offset(0, 2),
                ),
              ]
            : null,
      ),
      alignment: Alignment.center,
      child: Text(
        label == 'space' ? '' : _displayLabel(label),
        style: TextStyle(
          fontFamily: keyTheme.font.family,
          fontSize: keyTheme.font.sizeSp,
          fontWeight: keyTheme.font.bold ? FontWeight.bold : FontWeight.w500,
          fontStyle: keyTheme.font.italic ? FontStyle.italic : FontStyle.normal,
          color: isAccent ? Colors.white : keyTheme.text,
        ),
        textAlign: TextAlign.center,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );

    if (!isWide) {
      return Expanded(child: child);
    }
    return Expanded(flex: label == 'space' ? 4 : 2, child: child);
  }

  String _displayLabel(String label) {
    switch (label) {
      case 'shift':
        return '⇧';
      case 'back':
        return '⌫';
      case 'enter':
        return '⏎';
      case '123':
        return '123';
      case 'emoji':
        return '😊';
      default:
        return label;
    }
  }
}

const Set<String> _accentLabels = {'enter', 'emoji', 'shift', 'back'};
