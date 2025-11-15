import 'package:ai_keyboard/screens/main%20screens/autocorrect_guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/ai_rewriting_guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/set_keyboard_guidance_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter/material.dart';

class ChatGPTGuidanceScreen extends StatefulWidget {
  const ChatGPTGuidanceScreen({Key? key}) : super(key: key);

  @override
  State<ChatGPTGuidanceScreen> createState() => _ChatGPTGuidanceScreenState();
}

class _ChatGPTGuidanceScreenState extends State<ChatGPTGuidanceScreen> {
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
          'ChatGPT',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Video Thumbnail Section
            _buildVideoThumbnail(),

            // How to use ChatGPT section
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'How to use Chatgpt?',
                    style: AppTextStyle.headlineSmall.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.black,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'How can you access ChatGPT? To access ChatGPT, create an OpenAI account. Go to chat.openai.com and then select "Sign Up" and enter an email address, or use a Google or Microsoft account to log in. After signing up, type a prompt or question in the message box on the ChatGPT homepage.',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.black,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),

            // Similar More section
            _buildSimilarMoreSection(),
          ],
        ),
      ),
    );
  }

  Widget _buildVideoThumbnail() {
    return Container(
      height: 200,
      margin: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey[800],
        borderRadius: BorderRadius.circular(12),
      ),
      child: Stack(
        children: [
          // Background gradient
          Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Colors.grey[800]!, Colors.grey[700]!],
              ),
            ),
          ),

          // Play button
          Center(
            child: Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                color: AppColors.secondary,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.play_arrow,
                color: AppColors.white,
                size: 30,
              ),
            ),
          ),

          // Video duration
          Positioned(
            bottom: 12,
            left: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.7),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                '8:12 m',
                style: AppTextStyle.bodySmall.copyWith(
                  color: AppColors.white,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),

          // ChatGPT text overlay
          Positioned(
            top: 20,
            right: 20,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  'ChatGPT',
                  style: AppTextStyle.headlineMedium.copyWith(
                    color: AppColors.white,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                RichText(
                  text: TextSpan(
                    children: [
                      TextSpan(
                        text: 'IN ',
                        style: AppTextStyle.titleLarge.copyWith(
                          color: AppColors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      TextSpan(
                        text: '2',
                        style: AppTextStyle.titleLarge.copyWith(
                          color: AppColors.secondary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      TextSpan(
                        text: ' MINS',
                        style: AppTextStyle.titleLarge.copyWith(
                          color: AppColors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // Arrow icon
          Positioned(
            top: 20,
            right: 12,
            child: Icon(
              Icons.chevron_right,
              color: AppColors.white.withOpacity(0.7),
              size: 20,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSimilarMoreSection() {
    return Container(
      margin: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Similar More',
            style: AppTextStyle.titleLarge.copyWith(
              color: AppColors.secondary,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          _buildSimilarCard(
            icon: AppIcons.spell_check_icon,
            title: 'Auto Correction',
            subtitle: 'How to use auto-correction?',
            onTap: () => _navigateToAutoCorrection(),
          ),
          const SizedBox(height: 12),
          _buildSimilarCard(
            icon: AppIcons.report_icon,
            title: 'AI Rewriting',
            subtitle: 'How to use Ai Rewriting',
            onTap: () => _navigateToAIRewriting(),
          ),
          const SizedBox(height: 12),
          _buildSimilarCard(
            icon: AppIcons.report_icon,
            title: 'Set Keyboard',
            subtitle: 'How to set up Keyboard?',
            onTap: () => _navigateToSetKeyboard(),
          ),
        ],
      ),
    );
  }

  Widget _buildSimilarCard({
    required String icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: AppColors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Image.asset(
                icon,
                height: 16,
                width: 16,
                // color: AppColors.secondary,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTextStyle.titleMedium.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.black,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.grey, size: 20),
          ],
        ),
      ),
    );
  }

  void _navigateToAutoCorrection() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const AutoCorrectGuidanceScreen(),
      ),
    );
  }

  void _navigateToAIRewriting() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const AIRewritingGuidanceScreen(),
      ),
    );
  }

  void _navigateToSetKeyboard() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const SetKeyboardGuidanceScreen(),
      ),
    );
  }
}
