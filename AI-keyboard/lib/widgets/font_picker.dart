import 'package:flutter/material.dart';

/// Font picker widget for selecting keyboard fonts
class FontPicker extends StatelessWidget {
  final String currentFont;
  final ValueChanged<String> onFontSelected;
  final List<String>? availableFonts;

  const FontPicker({
    super.key,
    required this.currentFont,
    required this.onFontSelected,
    this.availableFonts,
  });

  static const List<String> defaultFonts = [
    'Roboto',
    'RobotoMono',
    'Serif',
    'SansSerif',
    'Monospace',
    'Cursive',
    'Casual',
    'NotoSans-VariableFont_wdth,wght.ttf',
    'NotoSansDevanagari-Regular.ttf',
    'NotoSansTamil-Regular.ttf',
    'NotoSansTelugu-Regular.ttf',
  ];

  @override
  Widget build(BuildContext context) {
    final fonts = availableFonts ?? defaultFonts;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            'Select Font',
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: fonts.length,
            itemBuilder: (context, index) {
              final font = fonts[index];
              final isSelected = font == currentFont;

              return Card(
                margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                color: isSelected ? Theme.of(context).primaryColor.withOpacity(0.1) : null,
                child: ListTile(
                  title: Text(
                    font,
                    style: TextStyle(
                      fontFamily: _getFontFamilyForPreview(font),
                      fontSize: 18,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                    ),
                  ),
                  subtitle: Text(
                    'AaBbCc 123',
                    style: TextStyle(
                      fontFamily: _getFontFamilyForPreview(font),
                      fontSize: 14,
                    ),
                  ),
                  trailing: isSelected
                      ? Icon(Icons.check_circle, color: Theme.of(context).primaryColor)
                      : null,
                  onTap: () => onFontSelected(font),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  String _getFontFamilyForPreview(String fontName) {
    // Map font names to Flutter font families
    switch (fontName.toLowerCase()) {
      case 'roboto':
        return 'Roboto';
      case 'robotomono':
        return 'Courier';
      case 'serif':
        return 'Serif';
      case 'sansserif':
        return 'Sans-Serif';
      case 'monospace':
        return 'Courier';
      case 'cursive':
        return 'Cursive';
      case 'casual':
        return 'Casual';
      default:
        // Try to use the font name as-is
        return fontName.replaceAll(' ', '').replaceAll('_', '');
    }
  }
}

/// Compact font selector dropdown
class FontSelectorDropdown extends StatelessWidget {
  final String currentFont;
  final ValueChanged<String> onFontSelected;
  final List<String>? availableFonts;

  const FontSelectorDropdown({
    super.key,
    required this.currentFont,
    required this.onFontSelected,
    this.availableFonts,
  });

  @override
  Widget build(BuildContext context) {
    final fonts = availableFonts ?? FontPicker.defaultFonts;
    
    // Create a clean list and ensure current font is included
    final Set<String> fontSet = {...fonts};
    
    // Always add current font to ensure it exists
    fontSet.add(currentFont);
    
    // Convert to sorted list
    final uniqueFonts = fontSet.toList()..sort();
    
    // Verify current font is in the list (safety check)
    final String safeCurrentFont = uniqueFonts.contains(currentFont) 
        ? currentFont 
        : (uniqueFonts.isNotEmpty ? uniqueFonts.first : 'Roboto');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey),
        borderRadius: BorderRadius.circular(8),
      ),
      child: DropdownButton<String>(
        value: safeCurrentFont,
        isExpanded: true,
        underline: const SizedBox(),
        items: uniqueFonts.map((font) {
          return DropdownMenuItem<String>(
            value: font,
            child: Text(
              font,
              style: TextStyle(
                fontFamily: _getFontFamilyForPreview(font),
                fontSize: 16,
              ),
            ),
          );
        }).toList(),
        onChanged: (value) {
          if (value != null) {
            onFontSelected(value);
          }
        },
      ),
    );
  }

  String _getFontFamilyForPreview(String fontName) {
    switch (fontName.toLowerCase()) {
      case 'roboto':
        return 'Roboto';
      case 'robotomono':
        return 'Courier';
      case 'serif':
        return 'Serif';
      case 'sansserif':
        return 'Sans-Serif';
      case 'monospace':
        return 'Courier';
      case 'cursive':
        return 'Cursive';
      case 'casual':
        return 'Casual';
      default:
        return fontName.replaceAll(' ', '').replaceAll('_', '');
    }
  }
}
