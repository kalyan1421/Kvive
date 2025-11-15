import 'package:flutter/material.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

class ViewAllThemesScreen extends StatefulWidget {
  final String category;

  const ViewAllThemesScreen({super.key, required this.category});

  @override
  State<ViewAllThemesScreen> createState() => _ViewAllThemesScreenState();
}

class _ViewAllThemesScreenState extends State<ViewAllThemesScreen> {
  String selectedTheme = '';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      appBar: AppBar(
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          '${widget.category} Themes',
          style: AppTextStyle.titleLarge.copyWith(
            color: AppColors.white,
            fontWeight: FontWeight.w600,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications, color: AppColors.white),
            onPressed: () {},
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Choose your favorite ${widget.category.toLowerCase()} theme',
              style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
            ),
            const SizedBox(height: 20),
            _buildThemesGrid(),
          ],
        ),
      ),
    );
  }

  Widget _buildThemesGrid() {
    List<ThemeItem> themes = _getThemesForCategory(widget.category);

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: 12,
        mainAxisSpacing: 12,
        childAspectRatio: 0.99,
      ),
      itemCount: themes.length,
      itemBuilder: (context, index) {
        final theme = themes[index];
        return _buildThemeCard(
          theme.name,
          theme.status,
          theme.preview,
          isSelected: selectedTheme == theme.name,
          onTap: () => setState(() => selectedTheme = theme.name),
        );
      },
    );
  }

  List<ThemeItem> _getThemesForCategory(String category) {
    switch (category) {
      case 'Popular':
        return [
          ThemeItem('White', 'Owned', _buildWhiteKeyboard()),
          ThemeItem('Dark', 'Owned', _buildDarkKeyboard()),
          ThemeItem('Yellow', 'Free', _buildYellowKeyboard()),
          ThemeItem('Red', 'Free', _buildRedKeyboard()),
          ThemeItem('Blue', 'Free', _buildBlueKeyboard()),
          ThemeItem('Green', 'Free', _buildGreenKeyboard()),
          ThemeItem('Purple', 'Free', _buildPurpleKeyboard()),
          ThemeItem('Orange', 'Free', _buildOrangeKeyboard()),
          ThemeItem('Pink', 'Free', _buildPinkKeyboard()),
          ThemeItem('Teal', 'Free', _buildTealKeyboard()),
          ThemeItem('Cyan', 'Free', _buildCyanKeyboard()),
          ThemeItem('Lime', 'Free', _buildLimeKeyboard()),
          ThemeItem('Indigo', 'Free', _buildIndigoKeyboard()),
          ThemeItem('Brown', 'Free', _buildBrownKeyboard()),
          ThemeItem('Grey', 'Free', _buildGreyKeyboard()),
          ThemeItem('Amber', 'Free', _buildAmberKeyboard()),
        ];
      case 'Colour':
        return [
          ThemeItem('Yellow', 'Free', _buildYellowKeyboard()),
          ThemeItem('Red', 'Free', _buildRedKeyboard()),
          ThemeItem('Blue', 'Free', _buildBlueKeyboard()),
          ThemeItem('Green', 'Free', _buildGreenKeyboard()),
          ThemeItem('Purple', 'Free', _buildPurpleKeyboard()),
          ThemeItem('Orange', 'Free', _buildOrangeKeyboard()),
          ThemeItem('Pink', 'Free', _buildPinkKeyboard()),
          ThemeItem('Teal', 'Free', _buildTealKeyboard()),
          ThemeItem('Cyan', 'Free', _buildCyanKeyboard()),
          ThemeItem('Lime', 'Free', _buildLimeKeyboard()),
          ThemeItem('Indigo', 'Free', _buildIndigoKeyboard()),
          ThemeItem('Brown', 'Free', _buildBrownKeyboard()),
          ThemeItem('Grey', 'Free', _buildGreyKeyboard()),
          ThemeItem('Amber', 'Free', _buildAmberKeyboard()),
        ];
      case 'Gradients':
        return [
          ThemeItem('Blue Gradient', 'Free', _buildBlueGradient()),
          ThemeItem('Purple Gradient', 'Free', _buildPurpleGradient()),
          ThemeItem('Sunset Gradient', 'Free', _buildSunsetGradient()),
          ThemeItem('Ocean Gradient', 'Free', _buildOceanGradient()),
          ThemeItem('Forest Gradient', 'Free', _buildForestGradient()),
          ThemeItem('Fire Gradient', 'Free', _buildFireGradient()),
          ThemeItem('Cosmic Gradient', 'Free', _buildCosmicGradient()),
          ThemeItem('Aurora Gradient', 'Free', _buildAuroraGradient()),
        ];
      case 'Picture':
        return [
          ThemeItem(
            'Bench',
            'Free',
            _buildAssetImage('assets/image_theme/bench.png'),
          ),
          ThemeItem(
            'Circle Design',
            'Free',
            _buildAssetImage('assets/image_theme/circldesign.jpg'),
          ),
          ThemeItem(
            'Sunset',
            'Free',
            _buildAssetImage('assets/image_theme/perosn_with sunset.png'),
          ),
          ThemeItem(
            'Sun & Moon',
            'Free',
            _buildAssetImage('assets/image_theme/sun_moon.jpg'),
          ),
        ];
      default:
        return [];
    }
  }

  Widget _buildThemeCard(
    String title,
    String status,
    Widget preview, {
    bool isSelected = false,
    VoidCallback? onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        height: 100,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
          border: isSelected
              ? Border.all(color: AppColors.secondary, width: 2)
              : null,
        ),
        child: Column(
          children: [
            // Preview
            Expanded(child: preview),
            const SizedBox(height: 8),

            // Title and Status
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Text(
                  status,
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.secondary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // Keyboard Themes using assets
  Widget _buildWhiteKeyboard() {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          Appkeyboards.keyboard_white,
          fit: BoxFit.contain,
          width: double.infinity,
          height: double.infinity,
        ),
      ),
    );
  }

  Widget _buildDarkKeyboard() {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          Appkeyboards.keyboard_black,
          fit: BoxFit.contain,
          width: double.infinity,
          height: double.infinity,
        ),
      ),
    );
  }

  Widget _buildYellowKeyboard() {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          Appkeyboards.keyboard_yellow,
          fit: BoxFit.contain,
          width: double.infinity,
          height: double.infinity,
        ),
      ),
    );
  }

  Widget _buildRedKeyboard() {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          Appkeyboards.keyboard_red,
          fit: BoxFit.contain,
          width: double.infinity,
          height: double.infinity,
        ),
      ),
    );
  }

  Widget _buildBlueKeyboard() {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          Appkeyboards.keyboard_blue,
          fit: BoxFit.contain,
          width: double.infinity,
          height: double.infinity,
        ),
      ),
    );
  }

  Widget _buildGreenKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.green[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.green[700], size: 40),
      ),
    );
  }

  Widget _buildPurpleKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.purple[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.purple[700], size: 40),
      ),
    );
  }

  Widget _buildOrangeKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.orange[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.orange[700], size: 40),
      ),
    );
  }

  Widget _buildPinkKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.pink[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.pink[700], size: 40),
      ),
    );
  }

  Widget _buildTealKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.teal[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.teal[700], size: 40),
      ),
    );
  }

  Widget _buildCyanKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.cyan[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.cyan[700], size: 40),
      ),
    );
  }

  Widget _buildLimeKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.lime[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.lime[700], size: 40),
      ),
    );
  }

  Widget _buildIndigoKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.indigo[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.indigo[700], size: 40),
      ),
    );
  }

  Widget _buildBrownKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.brown[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.brown[700], size: 40),
      ),
    );
  }

  Widget _buildGreyKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey[300],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.grey[700], size: 40),
      ),
    );
  }

  Widget _buildAmberKeyboard() {
    return Container(
      decoration: BoxDecoration(
        color: Colors.amber[100],
        borderRadius: BorderRadius.circular(6),
      ),
      child: Center(
        child: Icon(Icons.keyboard, color: Colors.amber[800], size: 40),
      ),
    );
  }

  // Gradient Themes with Keyboard
  Widget _buildBlueGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.blue[200]!, Colors.blue[800]!],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPurpleGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.purple[200]!, Colors.purple[800]!],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSunsetGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.orange[300]!, Colors.pink[400]!, Colors.purple[600]!],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOceanGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.cyan[200]!, Colors.blue[600]!, Colors.indigo[800]!],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildForestGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.green[200]!, Colors.green[600]!, Colors.green[800]!],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFireGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.red[300]!, Colors.orange[500]!, Colors.yellow[600]!],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCosmicGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.purple[800]!, Colors.indigo[900]!, Colors.black],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAuroraGradient() {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [Colors.green[300]!, Colors.blue[400]!, Colors.purple[500]!],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: Icon(
              Icons.keyboard,
              color: Colors.white.withOpacity(0.3),
              size: 40,
            ),
          ),
        ],
      ),
    );
  }

  // Asset Images
  Widget _buildAssetImage(String imagePath) {
    return Container(
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(6)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Image.asset(
          imagePath,
          fit: BoxFit.cover,
          width: double.infinity,
          height: double.infinity,
          errorBuilder: (context, error, stackTrace) {
            return Container(
              color: Colors.grey[200],
              child: const Center(
                child: Icon(Icons.image, color: Colors.grey, size: 40),
              ),
            );
          },
        ),
      ),
    );
  }
}

class ThemeItem {
  final String name;
  final String status;
  final Widget preview;

  ThemeItem(this.name, this.status, this.preview);
}
