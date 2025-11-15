import 'package:flutter/material.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

class InfoAppScreen extends StatelessWidget {
  const InfoAppScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          'Info App',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      backgroundColor: AppColors.white,
      body:  SafeArea(child: Padding(padding: const EdgeInsets.all(16.0), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
            children: [
          // Header Section
          // _buildHeader(context),
          // App Info Card
          _buildAppInfoCard(),
          // Main Content
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // About Kvive Section
                  _buildAboutSection(),
                  const SizedBox(height: 24),
                  // What Makes Kvive Unique Section
                  _buildUniqueFeaturesSection(),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
          // Bottom Navigation
          // _buildBottomNavigation(context),
          ],),
      ))
    
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top + 16,
        left: 16,
        right: 16,
        bottom: 16,
      ),
      decoration: BoxDecoration(
        color: AppColors.primary,
        borderRadius: const BorderRadius.only(
          bottomLeft: Radius.circular(20),
          bottomRight: Radius.circular(20),
        ),
      ),
      child: Column(
        children: [
          // Status Bar
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '9:41',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.white,
                  fontWeight: FontWeight.w600,
                ),
              ),
              Row(
                children: [
                  Icon(
                    Icons.signal_cellular_4_bar,
                    color: AppColors.white,
                    size: 16,
                  ),
                  const SizedBox(width: 4),
                  Icon(Icons.wifi, color: AppColors.white, size: 16),
                  const SizedBox(width: 4),
                  Icon(Icons.battery_full, color: AppColors.white, size: 16),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          // User Profile Bar
          Row(
            children: [
              Container(
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: AppColors.secondary,
                  shape: BoxShape.circle,
                ),
                child: Icon(Icons.person, color: AppColors.white, size: 30),
              ),
              const SizedBox(width: 12),
              Text(
                'James Canes',
                style: AppTextStyle.headlineSmall.copyWith(
                  color: AppColors.white,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Spacer(),
              Stack(
                children: [
                  Icon(Icons.notifications, color: AppColors.white, size: 24),
                  Positioned(
                    right: 0,
                    top: 0,
                    child: Container(
                      width: 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAppInfoCard() {
    return Container(
      margin: const EdgeInsets.all(16),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.primary,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Container(
            width: 60,
            height: 60,
            decoration: BoxDecoration(
              color: AppColors.white,
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.secondary, width: 2),
            ),
            child: Image.asset(
              AppIcons.crown_image,
              width: 16,
              height: 16,
              fit: BoxFit.contain,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Kvive- Ai Keyboard',
                  style: AppTextStyle.headlineMedium.copyWith(
                    color: AppColors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Version 1.0',
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.white.withOpacity(0.8),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAboutSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'About Kvive',
          style: AppTextStyle.headlineSmall.copyWith(
            color: AppColors.black,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          "Kvive, the AI-powered keyboard app that’s designed to transform the way you type. At Kvive, we believe that technology should work seamlessly with your everyday tasks to make life simpler, faster, and more efficient. That’s why we’ve created an intelligent, adaptive keyboard that goes beyond just typing. With its advanced AI algorithms, Kvive predicts your next word, auto-corrects with precision, and even learns your unique writing style to provide a truly personalized typing experience.\nWhether you're texting, emailing, or creating social media content, Kvive boosts your productivity by enabling smoother, faster, and smarter communication. It doesn’t just save time – it empowers you to express yourself more fluidly and accurately.",
          // Whether you're texting, emailing, or creating social media content, Kvive boosts your productivity by enabling smoother, faster, and smarter communication. It doesn’t just save time – it empowers you to express yourself more fluidly and accurately.',
          style: AppTextStyle.bodyMedium.copyWith(
            color: AppColors.black,
            height: 1.5,
          ),
        ),
      ],
    );
  }

  Widget _buildUniqueFeaturesSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'What Makes Kvive Unique?',
          style: AppTextStyle.headlineSmall.copyWith(
            color: AppColors.black,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 16),
        _buildFeatureCard(
          title: 'ChatGPT',
          description:
              'Our keyboard uses state-of-the-art artificial intelligence to anticipate your next word or sentence, reducing the number of keystrokes required.',
        ),
        const SizedBox(height: 12),
        _buildFeatureCard(
          title: 'Smart Ai Rewriting',
          description:
              'Our keyboard uses state-of-the-art artificial intelligence to anticipate your next word or sentence, reducing the number of keystrokes required.',
        ),
        const SizedBox(height: 12),
        _buildFeatureCard(
          title: 'Smart Auto-Correction',
          description:
              'Our intelligent auto-correction ensures that your messages are always clear, concise, and error-free.',
        ),
        const SizedBox(height: 12),
        _buildFeatureCard(
          title: 'Translator',
          description:
              'Communicate effortlessly in multiple languages with our smart language-switching features.',
        ),
      ],
    );
  }

  Widget _buildFeatureCard({
    required String title,
    required String description,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.secondary.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: AppColors.secondary.withOpacity(0.2),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: AppTextStyle.bodySmall.copyWith(
              color: AppColors.black,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNavigation(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.white,
        border: Border(top: BorderSide(color: AppColors.lightGrey, width: 1)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _buildNavItem(Icons.home, 'Home', false),
          _buildNavItem(Icons.palette, 'Themes', false),
          _buildNavItem(Icons.settings, 'Settings', false),
          _buildNavItem(Icons.person, 'Profile', true),
        ],
      ),
    );
  }

  Widget _buildNavItem(IconData icon, String label, bool isActive) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          icon,
          color: isActive ? AppColors.secondary : AppColors.grey,
          size: 24,
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: AppTextStyle.labelSmall.copyWith(
            color: isActive ? AppColors.secondary : AppColors.grey,
            fontWeight: isActive ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ],
    );
  }
}
