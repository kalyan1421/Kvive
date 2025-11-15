import 'package:ai_keyboard/screens/main%20screens/ai_rewriting_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/clipboard_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/dictionary_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/gestures_glide_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/sounds_vibration_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/typing_suggestion_screen.dart';
import 'package:flutter/material.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'keyboard_settings.dart';
import 'emoji_settings_screen.dart';

class SettingScreen extends StatelessWidget {
  const SettingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      body: Column(
        children: [
          // Settings Options
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  _buildSettingsOption(
                    icon: AppIcons.keyboard_icon,
                    title: 'Keyboard ',
                    subtitle: 'Adjust keyboard layout & other Options',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const KeyboardSettingsScreen(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.emojis_settings_icon,
                    title: 'Emojis Settings',
                    subtitle: 'Adjust Emojis Skin Tone & Size',
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => const EmojiSettingsScreen(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.report_icon,
                    title: 'Clipboard',
                    subtitle: 'Adjust Clipboard Setings & History',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const ClipboardScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.typing_suggestions_icon,
                    title: 'Typing & Suggestions',
                    subtitle: 'Word Corrections, Suggestion & Grammer',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const TypingSuggestionScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.AI_writing_assistant,
                    title: 'Customize AI',
                    subtitle: 'Rewrite Prompt and Add Custom Tone',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const AiRewritingScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.sound_vibration_icon,
                    title: 'Sound & Vibration',
                    subtitle: 'Adjust Audio Volume & Vibration option',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const SoundsVibrationScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.dictionary_icon,
                    title: 'Dictionary',
                    subtitle: 'Add, View & Remove word in the dictionary',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const DictionaryScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  _buildSettingsOption(
                    icon: AppIcons.gestures_glide_icon,
                    title: 'Gestures & Glide typing',
                    subtitle: 'Manage gestures & glide trail',
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const GesturesGlideScreen(),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 80), // Space for FAB
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettingsOption({
    required String icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: AppColors.white,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Image.asset(icon, width: 24, height: 24),
            ),
            const SizedBox(width: 16),
            Expanded(
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
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: AppColors.grey, size: 20),
          ],
        ),
      ),
    );
  }
}
