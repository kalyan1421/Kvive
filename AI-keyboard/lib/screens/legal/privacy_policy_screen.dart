import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

class PrivacyPolicyScreen extends StatelessWidget {
  const PrivacyPolicyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      appBar: AppBar(
        toolbarHeight: 70,
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Privacy Policy',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header Section
            _buildHeader(),
            const SizedBox(height: 32),

            // What We DO NOT Collect Section
            _buildDoNotCollectSection(),
            const SizedBox(height: 32),

            // What We Access Section
            _buildWhatWeAccessSection(),
            const SizedBox(height: 32),

            // Permissions Section
            _buildPermissionsSection(),
            const SizedBox(height: 32),

            // Data Protection Section
            _buildDataProtectionSection(),
            const SizedBox(height: 32),

            // Your Control Section
            _buildYourControlSection(),
            const SizedBox(height: 32),

            // Child Safety Section
            _buildChildSafetySection(),
            const SizedBox(height: 32),

            // Contact Section
            // _buildContactSection(context),
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [AppColors.primary, AppColors.primary.withOpacity(0.8)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withOpacity(0.3),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.white.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.privacy_tip_outlined,
                  color: AppColors.white,
                  size: 32,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Privacy Policy',
                      style: AppTextStyle.headlineMedium.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'In-App Short Version',
                      style: AppTextStyle.bodyMedium.copyWith(
                        color: AppColors.white.withOpacity(0.9),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.secondary,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              'Kvīve AI Keyboard',
              style: AppTextStyle.bodySmall.copyWith(
                color: AppColors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Icon(
                Icons.calendar_today,
                size: 16,
                color: AppColors.white.withOpacity(0.8),
              ),
              const SizedBox(width: 8),
            Text(
                'Last updated: 16 November 2025',
                style: AppTextStyle.bodySmall.copyWith(
                  color: AppColors.white.withOpacity(0.9),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildDoNotCollectSection() {
    return _buildSectionCard(
      icon: Icons.block,
      iconColor: Colors.red,
      title: '1. What We DO NOT Collect',
      subtitle: 'Your private typing stays on your device',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.red.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: Colors.red.withOpacity(0.3),
                width: 1,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(Icons.shield_outlined, color: Colors.red, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      'Kvīve AI Keyboard is not a keylogger.',
                      style: AppTextStyle.bodyMedium.copyWith(
                        fontWeight: FontWeight.w600,
                        color: Colors.red.shade700,
                      ),
                    ),
                  ],
            ),
            const SizedBox(height: 16),
                _buildDoNotCollectItem('Passwords'),
                _buildDoNotCollectItem('OTPs'),
                _buildDoNotCollectItem('Credit/Debit card numbers'),
                _buildDoNotCollectItem('Bank details'),
                _buildDoNotCollectItem('Sensitive personal messages'),
                _buildDoNotCollectItem('Any keystrokes typed in password fields'),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDoNotCollectItem(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            margin: const EdgeInsets.only(top: 4, right: 12),
            child: Icon(
              Icons.close_rounded,
              color: Colors.red,
              size: 20,
            ),
          ),
          Expanded(
            child: Text(
              text,
              style: AppTextStyle.bodyMedium.copyWith(
                color: AppColors.black,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWhatWeAccessSection() {
    return _buildSectionCard(
      icon: Icons.info_outline,
      iconColor: AppColors.secondary,
      title: '2. What We Access',
      subtitle: 'Only what\'s needed to make the keyboard work',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildAccessSubsection(
            'a) Text You Type',
            'Used only for:',
            [
              'Autocorrect',
              'Next-word prediction',
              'Grammar & tone improvements',
              'Transliteration',
              'Emoji/GIF/sticker search',
            ],
            note: 'This text is not stored and not uploaded unless you use AI features.',
          ),
          const SizedBox(height: 20),
          _buildAccessSubsection(
            'b) AI Features (optional)',
            'If you use features like Rewrite, Grammar Fix, Tone Change, or Smart Replies:',
            [
              'The text you select is sent securely to an AI service (OpenAI or your chosen provider).',
              'We do not store this text on our servers.',
              'If you use your own API key, processing happens under your own account.',
            ],
            isOptional: true,
          ),
          const SizedBox(height: 20),
          _buildAccessSubsection(
            'c) Cloud Sync (optional)',
            'If you sign in with Firebase, we sync:',
            [
              'Learned words',
              'User dictionary',
              'Language settings',
              'Theme preferences',
            ],
            note: 'We do not sync your typed content or messages.',
            isOptional: true,
          ),
          const SizedBox(height: 20),
          _buildAccessSubsection(
            'd) Clipboard Access',
            'We access clipboard content ONLY when you tap "Paste."',
            [
              'We do not automatically read or save your clipboard.',
            ],
          ),
          const SizedBox(height: 20),
          _buildAccessSubsection(
            'e) Voice Typing (optional)',
            'If enabled, audio is processed locally or by your selected speech engine.',
            [
              'Audio is not stored by us.',
            ],
            isOptional: true,
          ),
        ],
      ),
    );
  }

  Widget _buildAccessSubsection(
    String title,
    String description,
    List<String> items, {
    String? note,
    bool isOptional = false,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isOptional
            ? AppColors.secondary.withOpacity(0.05)
            : AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isOptional
              ? AppColors.secondary.withOpacity(0.2)
              : AppColors.grey.withOpacity(0.2),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: AppTextStyle.titleMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppColors.primary,
                  ),
                ),
              ),
              if (isOptional)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    'OPTIONAL',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.secondary,
                      fontWeight: FontWeight.w600,
                      fontSize: 10,
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: AppTextStyle.bodyMedium.copyWith(
              color: AppColors.black,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 12),
          ...items.map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      margin: const EdgeInsets.only(top: 6, right: 12),
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        shape: BoxShape.circle,
                      ),
                    ),
                    Expanded(
                      child: Text(
                        item,
                        style: AppTextStyle.bodyMedium.copyWith(
                          color: AppColors.black,
                          height: 1.5,
                        ),
                      ),
                    ),
                  ],
                ),
              )),
          if (note != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.secondary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 18,
                    color: AppColors.secondary,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      note,
                      style: AppTextStyle.bodySmall.copyWith(
                        color: AppColors.black,
                        fontStyle: FontStyle.italic,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildPermissionsSection() {
    return _buildSectionCard(
      icon: Icons.security,
      iconColor: AppColors.primary,
      title: '3. Permissions We Use',
      subtitle: 'Only what\'s needed for features you choose',
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            _buildPermissionRow(
              'Full Keyboard Access',
              'For typing, autocorrect, and suggestions',
            ),
            _buildDivider(),
            _buildPermissionRow(
              'Network Access',
              'For AI features, emoji/GIF packs, and cloud sync',
            ),
            _buildDivider(),
            _buildPermissionRow(
              'Record Audio (optional)',
              'For voice typing',
              isOptional: true,
            ),
            _buildDivider(),
            _buildPermissionRow(
              'Clipboard Access (manual only)',
              'When you tap "Paste"',
            ),
            _buildDivider(),
            _buildPermissionRow(
              'Storage (optional)',
              'For custom themes and sticker packs',
              isOptional: true,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionRow(String permission, String reason, {bool isOptional = false}) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.secondary.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              Icons.check_circle_outline,
              color: AppColors.secondary,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        permission,
                        style: AppTextStyle.titleSmall.copyWith(
                          fontWeight: FontWeight.w600,
                          color: AppColors.black,
                        ),
                      ),
                    ),
                    if (isOptional)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.secondary.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'OPTIONAL',
                          style: AppTextStyle.bodySmall.copyWith(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.w600,
                            fontSize: 9,
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  reason,
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.grey,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDivider() {
    return Divider(
      height: 1,
      thickness: 1,
      color: AppColors.grey.withOpacity(0.2),
      indent: 16,
      endIndent: 16,
    );
  }

  Widget _buildDataProtectionSection() {
    return _buildSectionCard(
      icon: Icons.lock_outline,
      iconColor: Colors.green,
      title: '4. How Your Data Is Protected',
      subtitle: 'Enterprise-grade security measures',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              Colors.green.withOpacity(0.1),
              Colors.green.withOpacity(0.05),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: Colors.green.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Column(
          children: [
            _buildProtectionItem(Icons.lock, 'Secure HTTPS encryption'),
            const SizedBox(height: 16),
            _buildProtectionItem(Icons.phone_android, 'Local device storage'),
            const SizedBox(height: 16),
            _buildProtectionItem(Icons.cloud_done, 'Google Firebase secure servers'),
            const SizedBox(height: 16),
            _buildProtectionItem(Icons.block, 'No advertising SDKs'),
            const SizedBox(height: 16),
            _buildProtectionItem(Icons.visibility_off, 'No third-party trackers'),
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.green.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.verified_user, color: Colors.green, size: 24),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'We never sell or share your data with advertisers.',
                      style: AppTextStyle.bodyMedium.copyWith(
                        fontWeight: FontWeight.w600,
                        color: Colors.green.shade700,
                      ),
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

  Widget _buildProtectionItem(IconData icon, String text) {
    return Row(
      children: [
        Icon(icon, color: Colors.green, size: 24),
        const SizedBox(width: 16),
        Expanded(
          child: Text(
            text,
            style: AppTextStyle.bodyMedium.copyWith(
              color: AppColors.black,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildYourControlSection() {
    return _buildSectionCard(
      icon: Icons.settings_outlined,
      iconColor: AppColors.secondary,
      title: '5. Your Control',
      subtitle: 'You\'re in charge of your data',
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            _buildControlItem('Disable AI features'),
            const SizedBox(height: 12),
            _buildControlItem('Delete your saved dictionary'),
            const SizedBox(height: 12),
            _buildControlItem('Turn off cloud sync'),
            const SizedBox(height: 12),
            _buildControlItem('Delete your account'),
            const SizedBox(height: 12),
            _buildControlItem('Revoke permissions anytime'),
            const SizedBox(height: 12),
            _buildControlItem('Uninstall the app to delete all local data'),
          ],
        ),
      ),
    );
  }

  Widget _buildControlItem(String text) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppColors.secondary,
            shape: BoxShape.circle,
          ),
          child: const Icon(
            Icons.check,
            color: AppColors.white,
            size: 16,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            text,
            style: AppTextStyle.bodyMedium.copyWith(
              color: AppColors.black,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildChildSafetySection() {
    return _buildSectionCard(
      icon: Icons.child_care_outlined,
      iconColor: Colors.orange,
      title: '6. Child Safety',
      subtitle: 'Age restrictions and safety',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.orange.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: Colors.orange.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Row(
          children: [
            Icon(Icons.info_outline, color: Colors.orange, size: 32),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                'Kvīve AI Keyboard is not designed for children under 13 years.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w500,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContactSection(BuildContext context) {
    return _buildSectionCard(
      icon: Icons.contact_support_outlined,
      iconColor: AppColors.primary,
      title: '7. Contact Us',
      subtitle: 'Have questions? We\'re here to help',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              AppColors.primary.withOpacity(0.1),
              AppColors.secondary.withOpacity(0.1),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            _buildContactItem(
              Icons.email_outlined,
              'support@kvive.app',
              () async {
                final Uri emailUri = Uri(
                  scheme: 'mailto',
                  path: 'support@kvive.app',
                );
                try {
                  if (await canLaunchUrl(emailUri)) {
                    await launchUrl(emailUri);
                  } else {
                    Clipboard.setData(const ClipboardData(text: 'support@kvive.app'));
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Email address copied to clipboard'),
                          backgroundColor: AppColors.secondary,
                        ),
                      );
                    }
                  }
                } catch (e) {
                  Clipboard.setData(const ClipboardData(text: 'support@kvive.app'));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Email address copied to clipboard'),
                        backgroundColor: AppColors.secondary,
                      ),
                    );
                  }
                }
              },
            ),
            const SizedBox(height: 16),
            _buildContactItem(
              Icons.language_outlined,
              'https://kvive.app',
              () async {
                final Uri url = Uri.parse('https://kvive.app');
                try {
                  if (await canLaunchUrl(url)) {
                    await launchUrl(url, mode: LaunchMode.externalApplication);
                  } else {
                    Clipboard.setData(const ClipboardData(text: 'https://kvive.app'));
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Website URL copied to clipboard'),
                          backgroundColor: AppColors.secondary,
                        ),
                      );
                    }
                  }
                } catch (e) {
                  Clipboard.setData(const ClipboardData(text: 'https://kvive.app'));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Website URL copied to clipboard'),
                        backgroundColor: AppColors.secondary,
                      ),
                    );
                  }
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContactItem(IconData icon, String text, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: AppColors.grey.withOpacity(0.2),
            width: 1,
          ),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppColors.secondary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(icon, color: AppColors.secondary, size: 24),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                text,
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.primary,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
            Icon(
              Icons.arrow_forward_ios,
              color: AppColors.grey,
              size: 16,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionCard({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String subtitle,
    required Widget child,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: AppColors.grey.withOpacity(0.1),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: iconColor.withOpacity(0.1),
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(16),
                topRight: Radius.circular(16),
              ),
            ),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: iconColor,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, color: AppColors.white, size: 24),
                ),
                const SizedBox(width: 16),
                Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
                        style: AppTextStyle.titleLarge.copyWith(
                          fontWeight: FontWeight.w600,
                          color: AppColors.black,
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
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: child,
          ),
        ],
      ),
    );
  }
}
