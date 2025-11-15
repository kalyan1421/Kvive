import 'package:flutter/material.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/screens/main screens/custom_image_theme_flow_screen.dart';

/// Step 1: Choose Base Theme (Light or Dark)
/// User selects which keyboard style to use as base for custom image theme
class ChooseBaseThemeScreen extends StatefulWidget {
  const ChooseBaseThemeScreen({super.key});

  @override
  State<ChooseBaseThemeScreen> createState() => _ChooseBaseThemeScreenState();
}

class _ChooseBaseThemeScreenState extends State<ChooseBaseThemeScreen> {
  String? selectedBaseTheme; // 'light' or 'dark'

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
          'Choose Base Theme',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Instructions
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              color: AppColors.lightGrey.withOpacity(0.3),
              child: Text(
                'Select a base keyboard style for your custom image theme',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.grey,
                  fontWeight: FontWeight.w500,
                ),
                textAlign: TextAlign.center,
              ),
            ),

            const SizedBox(height: 24),

            // Theme previews
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(
                  children: [
                    // Light Theme
                    _buildThemePreview(
                      theme: 'light',
                      title: 'Light',
                      description: 'Clean white keys with dark text',
                      keyboardPreview: _buildLightKeyboardPreview(),
                      isSelected: selectedBaseTheme == 'light',
                      onTap: () {
                        setState(() {
                          selectedBaseTheme = 'light';
                        });
                      },
                    ),

                    const SizedBox(height: 20),

                    // Dark Theme
                    _buildThemePreview(
                      theme: 'dark',
                      title: 'Dark',
                      description: 'Dark keys with white text',
                      keyboardPreview: _buildDarkKeyboardPreview(),
                      isSelected: selectedBaseTheme == 'dark',
                      onTap: () {
                        setState(() {
                          selectedBaseTheme = 'dark';
                        });
                      },
                    ),

                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),

            // Continue Button
            Container(
              padding: const EdgeInsets.all(20),
              child: GestureDetector(
                onTap: selectedBaseTheme != null
                    ? () {
                        // Navigate to image selection
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => CustomImageThemeFlowScreen(
                              baseTheme: selectedBaseTheme!,
                            ),
                          ),
                        );
                      }
                    : null,
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  decoration: BoxDecoration(
                    gradient: selectedBaseTheme != null
                        ? const LinearGradient(
                            colors: [Color(0xFF7B2CBF), Color(0xFF5A189A)],
                            begin: Alignment.centerLeft,
                            end: Alignment.centerRight,
                          )
                        : null,
                    color: selectedBaseTheme == null
                        ? AppColors.grey.withOpacity(0.3)
                        : null,
                    borderRadius: BorderRadius.circular(30),
                    boxShadow: selectedBaseTheme != null
                        ? [
                            BoxShadow(
                              color: AppColors.secondary.withOpacity(0.3),
                              blurRadius: 12,
                              offset: const Offset(0, 4),
                            ),
                          ]
                        : null,
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'Continue',
                        style: AppTextStyle.titleMedium.copyWith(
                          color: selectedBaseTheme != null
                              ? AppColors.white
                              : AppColors.grey,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.arrow_forward,
                        color: selectedBaseTheme != null
                            ? AppColors.white
                            : AppColors.grey,
                        size: 20,
                      ),
                    ],
                  ),
                ),
              ),
            ),

            const SizedBox(height: 250),
          ],
        ),
      ),
    );
  }

  Widget _buildThemePreview({
    required String theme,
    required String title,
    required String description,
    required Widget keyboardPreview,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected ? AppColors.secondary : AppColors.lightGrey,
            width: isSelected ? 3 : 1,
          ),
          boxShadow: [
            if (isSelected)
              BoxShadow(
                color: AppColors.secondary.withOpacity(0.3),
                blurRadius: 12,
                offset: const Offset(0, 4),
              )
            else
              BoxShadow(
                color: Colors.black.withOpacity(0.05),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Keyboard Preview
            Container(
              height: 160,
              decoration: BoxDecoration(
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(16),
                  topRight: Radius.circular(16),
                ),
                color: theme == 'dark' ? AppColors.black : AppColors.lightGrey,
              ),
              child: ClipRRect(
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(16),
                  topRight: Radius.circular(16),
                ),
                child: keyboardPreview,
              ),
            ),

            // Theme Info
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  // Selection indicator
                  Container(
                    width: 24,
                    height: 24,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      border: Border.all(
                        color:
                            isSelected ? AppColors.secondary : AppColors.grey,
                        width: 2,
                      ),
                      color: isSelected ? AppColors.secondary : Colors.transparent,
                    ),
                    child: isSelected
                        ? const Icon(
                            Icons.check,
                            size: 16,
                            color: AppColors.white,
                          )
                        : null,
                  ),

                  const SizedBox(width: 12),

                  // Text
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: AppTextStyle.titleLarge.copyWith(
                            color: AppColors.black,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          description,
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.grey,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLightKeyboardPreview() {
    // Show suggestion bar with sample suggestions
    return Container(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // Suggestion bar
          Container(
            height: 30,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: ['I', 'the', 'you']
                  .map((suggestion) => Expanded(
                        child: Center(
                          child: Text(
                            suggestion,
                            style: AppTextStyle.bodyMedium.copyWith(
                              color: AppColors.black,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ))
                  .toList(),
            ),
          ),

          const SizedBox(height: 4),

          // Keyboard keys
          Expanded(
            child: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppColors.lightGrey,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _buildKeyRow(['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'], false),
                  _buildKeyRow(['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'], false),
                  _buildKeyRow(['Z', 'X', 'C', 'V', 'B', 'N', 'M'], false),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDarkKeyboardPreview() {
    // Show suggestion bar with sample suggestions
    return Container(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // Suggestion bar
          Container(
            height: 30,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: const Color(0xFF2D2D2D),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: ['I', 'the', 'you']
                  .map((suggestion) => Expanded(
                        child: Center(
                          child: Text(
                            suggestion,
                            style: AppTextStyle.bodyMedium.copyWith(
                              color: AppColors.white,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ))
                  .toList(),
            ),
          ),

          const SizedBox(height: 4),

          // Keyboard keys
          Expanded(
            child: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFF1E1E1E),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  _buildKeyRow(['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'], true),
                  _buildKeyRow(['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'], true),
                  _buildKeyRow(['Z', 'X', 'C', 'V', 'B', 'N', 'M'], true),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildKeyRow(List<String> keys, bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: keys
          .map((key) => Expanded(
                child: Container(
                  margin: const EdgeInsets.all(2),
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  decoration: BoxDecoration(
                    color: isDark ? const Color(0xFF2D2D2D) : AppColors.white,
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Center(
                    child: Text(
                      key,
                      style: AppTextStyle.bodySmall.copyWith(
                        color: isDark ? AppColors.white : AppColors.black,
                        fontWeight: FontWeight.w600,
                        fontSize: 10,
                      ),
                    ),
                  ),
                ),
              ))
          .toList(),
    );
  }
}

