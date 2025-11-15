import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/screens/keyboard_setup/keyboard_setup_screen.dart';
import 'package:flutter/material.dart';

class SuccessScreen extends StatelessWidget {
  const SuccessScreen({super.key});

  // Template onTap handlers for this page
  void onTapGoHome(BuildContext context) {
    // Navigate to keyboard setup screen
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => const KeyboardSetupScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      body: SafeArea(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Column(
            children: [
              Spacer(),
              // _buildIllustration(),
              Image.asset(AppAssets.successIllustration, fit: BoxFit.cover),
              SizedBox(height: 32),

              Text(
                'Congratualtion !',
                style: AppTextStyle.headlineLarge.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.secondary,
                  fontSize: 28,
                ),
              ),
              SizedBox(height: 32),
              Text(
                'Your Sign in successfully',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  fontSize: 16,
                ),
              ),
              SizedBox(height: 32),
              _buildGoHomeButton(context),
              Spacer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGoHomeButton(BuildContext context) {
      return GestureDetector(
        onTap: () => onTapGoHome(context),
      child: Container(
        width: MediaQuery.of(context).size.width * 0.7,
        height: 50,
        decoration: BoxDecoration(
          color: AppColors.primary, // Dark blue color
          borderRadius: BorderRadius.circular(0),
        ),
        child: Center(
          child: Text(
            'Go Home',
            style: AppTextStyle.buttonPrimary.copyWith(
              color: AppColors.white,
              fontSize: 16,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }
}
