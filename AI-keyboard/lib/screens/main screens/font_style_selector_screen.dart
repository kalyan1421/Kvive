import 'package:flutter/material.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:google_fonts/google_fonts.dart';

final List<_FontOption> _fontOptions = [
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

/// Visual font selector aligned with the new theme editor look.
/// Presents a grid of font options.
class FontStyleSelectorScreen extends StatefulWidget {
  final KeyboardThemeV2 currentTheme;
  final Function(KeyboardThemeV2) onThemeUpdated;
  final bool showAppBar;

  const FontStyleSelectorScreen({
    super.key,
    required this.currentTheme,
    required this.onThemeUpdated,
    this.showAppBar = true,
  });

  @override
  State<FontStyleSelectorScreen> createState() => _FontStyleSelectorScreenState();
}

class _FontStyleSelectorScreenState extends State<FontStyleSelectorScreen> {
  late KeyboardThemeV2 _currentTheme;
  late String _selectedFontId;

  @override
  void initState() {
    super.initState();
    _currentTheme = widget.currentTheme;
    _selectedFontId = _resolveFontOptionId(_currentTheme.keys.font.family);
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
    return _fontOptions.first.id;
  }

  void _selectFontOption(_FontOption option) {
    setState(() {
      _selectedFontId = option.id;
    });
    
    _updateTheme(_currentTheme.copyWith(
      keys: _currentTheme.keys.copyWith(
        font: _currentTheme.keys.font.copyWith(
          family: option.id,
          bold: false,
          italic: false,
        ),
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
    final screenWidth = MediaQuery.of(context).size.width;
    final crossAxisCount = screenWidth < 400 ? 4 : (screenWidth < 600 ? 5 : 6);

    final content = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Choose Font Style',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Tap a font to preview it on the keyboard.',
            style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          ),
          const SizedBox(height: 24),
          GridView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _fontOptions.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: crossAxisCount,
              mainAxisSpacing: 20,
              crossAxisSpacing: 12,
              childAspectRatio: 0.85,
            ),
            itemBuilder: (context, index) {
              final option = _fontOptions[index];
              final isSelected = option.id == _selectedFontId;
              return _buildFontCard(option, isSelected);
            },
          ),
        ],
      ),
    );

    if (widget.showAppBar) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Fonts'),
          backgroundColor: AppColors.primary,
          foregroundColor: AppColors.white,
          elevation: 0,
        ),
        body: SingleChildScrollView(child: content),
      );
    }

    return SingleChildScrollView(child: content);
  }

  Widget _buildFontCard(_FontOption option, bool isSelected) {
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
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.white,
                  border: Border.all(
                    color: isSelected ? AppColors.secondary : Colors.transparent,
                    width: isSelected ? 3 : 0,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.08),
                      blurRadius: 12,
                      offset: const Offset(0, 6),
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

class _FontOption {
  final String id;
  final String displayName;
  final String previewText;
  final String themeFamily;
  final TextStyle Function(bool isSelected) previewStyleBuilder;

  const _FontOption({
    required this.id,
    required this.displayName,
    required this.previewText,
    required this.themeFamily,
    required this.previewStyleBuilder,
  });
}

