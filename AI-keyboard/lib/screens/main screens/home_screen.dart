import 'package:ai_keyboard/screens/main%20screens/ai_writing_assistance_screen.dart';
import 'package:ai_keyboard/screens/legal/privacy_policy_screen.dart';
import 'package:ai_keyboard/screens/legal/terms_of_use_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/language_screen.dart';

import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/screens/main%20screens/upgrade_pro_screen.dart';
import 'package:flutter/material.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:ai_keyboard/screens/main%20screens/mainscreen.dart';
import 'package:ai_keyboard/services/firebase_auth_service.dart';
import 'package:ai_keyboard/screens/login/login_illustraion_screen.dart';
import 'package:ai_keyboard/services/fcm_token_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final FirebaseAuthService _authService = FirebaseAuthService();

  @override
  void initState() {
    super.initState();
    // Request notification permission when user reaches home screen
    WidgetsBinding.instance.addPostFrameCallback((_) {
      FCMTokenService.requestNotificationPermission();
    });
  }

  Future<void> _applyTheme(KeyboardThemeV2 theme) async {
    try {
      await ThemeManagerV2.saveThemeV2(theme);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Applied ${theme.name} theme'),
            backgroundColor: theme.specialKeys.accent,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to apply theme: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  void _navigateToThemeGallery() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const mainscreen(initialIndex: 1),
      ),
    );
  }
  

  void _showUpgradeProBottomSheet() {
    // Check if user is logged in
    if (_authService.currentUser == null) {
      // User is not logged in, navigate to login screen
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => const LoginIllustraionScreen(),
        ),
      );
      return;
    }
    
    // User is logged in, show upgrade pro screen
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.95,
        decoration: const BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(20),
            topRight: Radius.circular(20),
          ),
        ),
        child: const UpgradeProScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaSize = MediaQuery.of(context).size;
    final shortestSide = mediaSize.shortestSide;
    final double scaleFactor =
        (shortestSide / 375).clamp(0.85, 1.2).toDouble();
    final verticalGap = (24 * scaleFactor).clamp(16.0, 32.0);
    final sectionSpacing = (12 * scaleFactor).clamp(8.0, 18.0);

    final String wordcount = '21,485';
    
    return Scaffold(
      backgroundColor: AppColors.white,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final bool isWide = constraints.maxWidth >= 500;
          final EdgeInsets pagePadding = EdgeInsets.symmetric(
            horizontal: isWide
                ? (24 * scaleFactor).clamp(20.0, 40.0)
                : (16 * scaleFactor).clamp(12.0, 24.0),
            vertical: (16 * scaleFactor).clamp(12.0, 24.0),
          );

          return SingleChildScrollView(
            child: Padding(
              padding: pagePadding,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // _buildUpdatePROcARD(context, wordcount),
                  SizedBox(height: verticalGap),
                  _buildThemes(context, scaleFactor, mediaSize),
                  SizedBox(height: verticalGap),
                  Column(
                    spacing: sectionSpacing,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Our Features',
                        style: TextStyle(
                          fontSize: (16 * scaleFactor).clamp(14.0, 22.0),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Ai Writing Assistance',
                        subtitle: 'Checkout prompts for a varity of uses',
                        icon: AppIcons.AI_writing_assistant,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => AIWritingAssistanceScreen(),
                            ),
                          );
                        },
                      ),
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Languages',
                        subtitle: 'Choose your preferred language',
                        icon: AppIcons.languages,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => LanguageScreen(),
                            ),
                          );
                        },
                      ),
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Guide',
                        subtitle: 'Get a guide to help you use the app',
                        icon: AppIcons.guide,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => GuidanceScreen(),
                            ),
                          );
                        },
                      ),
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Mail Us',
                        subtitle: 'Send us an email if you have any issues',
                        icon: AppIcons.mail_us_icon,
                        onTap: () {},
                      ),
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Privacy Policy',
                        subtitle: 'Read our privacy policy',
                        icon: AppIcons.privacy_policy,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const PrivacyPolicyScreen(),
                            ),
                          );
                        },
                      ),
                      _buildtileOption(
                        scaleFactor: scaleFactor,
                        title: 'Terms of Service',
                        subtitle: 'Read our terms of service',
                        icon: AppIcons.terms_of_service,
                        onTap: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const TermsOfUseScreen(),
                            ),
                          );
                        },
                      ),
                      Center(
                        child: Text(
                          'version 1.0.0',
                          style: TextStyle(
                            fontSize: (16 * scaleFactor).clamp(14.0, 20.0),
                            color: AppColors.grey,
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
      ),
    );
  }

  SizedBox _buildThemes(BuildContext context, double scaleFactor, Size mediaSize) {
    final double sectionSpacing = (8 * scaleFactor).clamp(6.0, 16.0);
    final double cardPadding = (12 * scaleFactor).clamp(10.0, 18.0);
    // ✅ FIX: Ensure min <= max for clamp - handle edge cases
    final double calculatedHeight = mediaSize.height * 0.18;
    final double minHeight = 120.0;
    final double maxHeight = (mediaSize.height * 0.22).clamp(minHeight, double.infinity);
    final double cardHeight = calculatedHeight.clamp(minHeight, maxHeight);
    final double borderRadius = (12 * scaleFactor).clamp(10.0, 18.0);

    Widget buildThemeCard({
      required VoidCallback onTap,
      required String title,
      required String asset,
      required TextStyle titleStyle,
      required Color background,
      required Color priceColor,
    }) {
      return GestureDetector(
        onTap: onTap,
        child: Container(
          padding: EdgeInsets.all(cardPadding),
          height: cardHeight,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(borderRadius),
            color: background,
          ),
          child: Column(
            children: [
              Expanded(
                child: FittedBox(
                  fit: BoxFit.contain,
                  child: Image.asset(asset),
                ),
              ),
              SizedBox(height: (8 * scaleFactor).clamp(4.0, 12.0)),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    title,
                    style: titleStyle.copyWith(
                      fontSize: (20 * scaleFactor).clamp(16.0, 26.0),
                    ),
                  ),
                  Text(
                    'Free',
                    style: AppTextStyle.buttonSecondary.copyWith(
                      fontSize: (20 * scaleFactor).clamp(16.0, 26.0),
                      color: priceColor,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    }

    return SizedBox(
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Themes',
                style: TextStyle(
                  fontSize: (16 * scaleFactor).clamp(14.0, 22.0),
                ),
              ),
              GestureDetector(
                onTap: _navigateToThemeGallery,
                child: Text(
                  'See All',
                  style: TextStyle(
                    color: AppColors.secondary,
                    fontSize: (16 * scaleFactor).clamp(14.0, 22.0),
                  ),
                ),
              ),
            ],
          ),
          SizedBox(height: sectionSpacing),
          LayoutBuilder(
            builder: (context, constraints) {
              final screenWidth = constraints.maxWidth;
              final screenHeight = MediaQuery.of(context).size.height;
              
              // Calculate responsive spacing based on screen size
              final horizontalSpacing = (screenWidth * 0.04).clamp(12.0, 20.0);
              final verticalSpacing = (screenHeight * 0.02).clamp(12.0, 20.0);
              
              // Determine if cards should be side-by-side based on available width
              final bool canShowSideBySide = screenWidth >= 360;
              
              // Calculate responsive text size based on screen width
              final double titleFontSize = (screenWidth * 0.055).clamp(14.0, 28.0);
              final double priceFontSize = (screenWidth * 0.055).clamp(14.0, 28.0);
              
              // Calculate responsive card height based on screen size
              // ✅ FIX: Ensure min <= max for clamp to prevent ArgumentError
              final double sideBySideCalculated = screenHeight * 0.18;
              final double sideBySideMin = 120.0;
              final double sideBySideMax = (screenHeight * 0.22).clamp(sideBySideMin, double.infinity);
              
              final double stackedCalculated = screenHeight * 0.16;
              final double stackedMin = 110.0;
              final double stackedMax = (screenHeight * 0.20).clamp(stackedMin, double.infinity);
              
              final double responsiveCardHeight = canShowSideBySide
                  ? sideBySideCalculated.clamp(sideBySideMin, sideBySideMax)
                  : stackedCalculated.clamp(stackedMin, stackedMax);
              
              // Calculate responsive padding based on screen size
              final double responsivePadding = (screenWidth * 0.032).clamp(10.0, 18.0);
              final double responsiveImageSpacing = (screenHeight * 0.01).clamp(4.0, 12.0);
              
              Widget buildResponsiveThemeCard({
                required VoidCallback onTap,
                required String title,
                required String asset,
                required TextStyle titleStyle,
                required Color background,
                required Color priceColor,
              }) {
                return GestureDetector(
                  onTap: onTap,
                  child: Container(
                    padding: EdgeInsets.all(responsivePadding),
                    height: responsiveCardHeight,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(borderRadius),
                      color: background,
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: FittedBox(
                            fit: BoxFit.contain,
                            child: Image.asset(asset),
                          ),
                        ),
                        SizedBox(height: responsiveImageSpacing),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Flexible(
                              child: Text(
                                title,
                                style: titleStyle.copyWith(
                                  fontSize: titleFontSize,
                                  fontWeight: FontWeight.w600,
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            SizedBox(width: (screenWidth * 0.02).clamp(4.0, 8.0)),
                            Text(
                              'Free',
                              style: AppTextStyle.buttonSecondary.copyWith(
                                fontSize: priceFontSize,
                                color: priceColor,
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
              
              final cards = [
                buildResponsiveThemeCard(
                  onTap: () => _applyTheme(KeyboardThemeV2.createWhiteTheme()),
                  title: 'Light',
                  asset: Appkeyboards.keyboard_white,
                  titleStyle: AppTextStyle.buttonSecondary,
                  background: AppColors.grey.withOpacity(0.2),
                  priceColor: AppColors.secondary,
                ),
                buildResponsiveThemeCard(
                  onTap: () => _applyTheme(KeyboardThemeV2.createDarkTheme()),
                  title: 'Dark',
                  asset: Appkeyboards.keyboard_black,
                  titleStyle: AppTextStyle.buttonPrimary,
                  background: AppColors.grey,
                  priceColor: AppColors.secondary,
                ),
              ];

              if (canShowSideBySide) {
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: cards[0]),
                    SizedBox(width: horizontalSpacing),
                    Expanded(child: cards[1]),
                  ],
                );
              }

              return Column(
                children: [
                  cards[0],
                  SizedBox(height: verticalSpacing),
                  cards[1],
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildUpdatePROcARD(BuildContext context, String wordcount) {
    return GestureDetector(
      onTap: _showUpgradeProBottomSheet,
      child: Container(
        width: double.infinity,
        height: MediaQuery.of(context).size.height * 0.2,
        decoration: BoxDecoration(
          color: AppColors.primary,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Expanded(
              child: Container(
                alignment: Alignment.center,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Text(
                      'Free Trial',
                      style: AppTextStyle.bodyLarge.copyWith(
                        color: AppColors.secondary,
                      ),
                    ),
                    Text(
                      '$wordcount Word Left',
                      style: AppTextStyle.headlineSmall.copyWith(
                        color: Colors.white,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: Container(
                width: double.infinity,
                height: MediaQuery.of(context).size.height * 0.06,
                decoration: BoxDecoration(
                  color: AppColors.secondary,
                  borderRadius: BorderRadius.only(
                    bottomLeft: Radius.circular(10),
                    bottomRight: Radius.circular(10),
                  ),
                ),
                child: Center(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Image.asset(AppIcons.crown, width: 20, height: 20),
                      SizedBox(width: 8),
                      Text(
                        'upgrade pro',
                        style: AppTextStyle.headlineSmall.copyWith(
                          color: AppColors.white,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _buildtileOption extends StatelessWidget {
  final String title;
  final String subtitle;
  final String icon;
  final VoidCallback onTap;
  final double scaleFactor;
  const _buildtileOption({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.onTap,
    required this.scaleFactor,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.symmetric(
        horizontal: (24 * scaleFactor).clamp(16.0, 32.0),
        vertical: (4 * scaleFactor).clamp(2.0, 8.0),
      ),
      minTileHeight: (72 * scaleFactor).clamp(64.0, 96.0),
      onTap: onTap,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      tileColor: AppColors.grey.withOpacity(0.2),

      leading: Image.asset(
        icon,
        width: (24 * scaleFactor).clamp(20.0, 32.0),
        height: (28 * scaleFactor).clamp(22.0, 36.0),
      ),
      title: Text(
        title,
        style: AppTextStyle.headlineSmall.copyWith(
          fontSize: (18 * scaleFactor).clamp(16.0, 24.0),
        ),
      ),
      subtitle: Text(
        subtitle,
        style: TextStyle(
          fontSize: (14 * scaleFactor).clamp(12.0, 18.0),
        ),
      ),
    );
  }
}
