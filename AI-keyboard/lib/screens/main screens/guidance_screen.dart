import 'package:ai_keyboard/screens/main%20screens/chatgpt_guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/autocorrect_guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/ai_rewriting_guidance_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/set_keyboard_guidance_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter/material.dart';

class GuidanceScreen extends StatefulWidget {
  const GuidanceScreen({Key? key}) : super(key: key);

  @override
  State<GuidanceScreen> createState() => _GuidanceScreenState();
}

class _GuidanceScreenState extends State<GuidanceScreen> {
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
          'Guidance',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        actions: [
          Stack(
            children: [
              IconButton(
                icon: const Icon(
                  Icons.notifications_outlined,
                  color: AppColors.white,
                ),
                onPressed: () {},
              ),
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: AppColors.secondary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Expanded(
              child: ListView(
                children: [
                  _buildGuidanceCard(
                    icon: AppIcons.chatgpt_icon,
                    title: 'ChatGPT',
                    subtitle: 'How to use chatgpt ?',
                    onTap: () => _navigateToChatGPT(),
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    icon: AppIcons.spell_check_icon,
                    title: 'Auto Correction',
                    subtitle: 'How to use auto-correction?',
                    onTap: () => _navigateToAutoCorrection(),
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    icon: AppIcons.report_icon,
                    title: 'AI Rewriting',
                    subtitle: 'How to use Ai Rewriting',
                    onTap: () => _navigateToAIRewriting(),
                  ),
                  const SizedBox(height: 16),
                  _buildGuidanceCard(
                    icon: AppIcons.report_icon,
                    title: 'Set Keyboard',
                    subtitle: 'How to set up Keyboard?',
                    onTap: () => _navigateToSetKeyboard(),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildGuidanceCard({
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
                borderRadius: BorderRadius.circular(12),
              ),
              child: Image.asset(icon, height: 16, width: 16),
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
            const Icon(Icons.chevron_right, color: AppColors.black, size: 24),
          ],
        ),
      ),
    );
  }

  void _navigateToChatGPT() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const ChatGPTGuidanceScreen()),
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
