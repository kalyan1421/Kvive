import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';

class TermsOfUseScreen extends StatelessWidget {
  const TermsOfUseScreen({super.key});

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
          'Terms of Use',
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

            // Acceptance of Terms Section
            _buildAcceptanceSection(),
            const SizedBox(height: 24),

            // License Section
            _buildLicenseSection(),
            const SizedBox(height: 24),

            // User Responsibilities Section
            _buildUserResponsibilitiesSection(),
            const SizedBox(height: 24),

            // Paid Features Section
            _buildPaidFeaturesSection(),
            const SizedBox(height: 24),

            // Termination Section
            _buildTerminationSection(),
            const SizedBox(height: 24),

            // Disclaimers Section
            _buildDisclaimersSection(),
            const SizedBox(height: 24),

            // Limitation of Liability Section
            _buildLiabilitySection(),
            const SizedBox(height: 24),

            // Changes to Terms Section
            _buildChangesSection(),
            const SizedBox(height: 24),

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
                  Icons.description_outlined,
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
                      'Terms of Use',
                      style: AppTextStyle.headlineMedium.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Legal Agreement',
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

  Widget _buildAcceptanceSection() {
    return _buildSectionCard(
      icon: Icons.check_circle_outline,
      iconColor: Colors.green,
      title: '1. Acceptance of Terms',
      subtitle: 'By using Kvīve, you agree to these terms',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.green.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: Colors.green.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.info_outline, color: Colors.green, size: 24),
            const SizedBox(width: 16),
            Expanded(
              child: Text(
                'By installing or using Kvīve, you agree to these Terms of Use. If you do not agree, remove the application and discontinue use immediately.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLicenseSection() {
    return _buildSectionCard(
      icon: Icons.verified_user_outlined,
      iconColor: AppColors.secondary,
      title: '2. License',
      subtitle: 'Your rights to use the application',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildLicenseItem(
              Icons.person_outline,
              'Personal License',
              'For personal and non-commercial purposes',
            ),
            const SizedBox(height: 16),
            _buildLicenseItem(
              Icons.block,
              'Non-Transferable',
              'License cannot be transferred to others',
            ),
            const SizedBox(height: 16),
            _buildLicenseItem(
              Icons.warning_amber_outlined,
              'Revocable',
              'License can be revoked if terms are violated',
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.secondary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                'Kvīve grants you a personal, non-transferable, revocable license to use the application for personal and non-commercial purposes.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLicenseItem(IconData icon, String title, String description) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppColors.secondary.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: AppColors.secondary, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: AppTextStyle.titleSmall.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppColors.black,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                description,
                style: AppTextStyle.bodySmall.copyWith(
                  color: AppColors.grey,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildUserResponsibilitiesSection() {
    return _buildSectionCard(
      icon: Icons.account_circle_outlined,
      iconColor: AppColors.primary,
      title: '3. User Responsibilities',
      subtitle: 'Your obligations when using Kvīve',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            _buildResponsibilityItem(
              'Do not misuse the app',
              Icons.block_outlined,
            ),
            const SizedBox(height: 12),
            _buildResponsibilityItem(
              'Do not attempt to access restricted services',
              Icons.lock_outline,
            ),
            const SizedBox(height: 12),
            _buildResponsibilityItem(
              'Do not violate applicable laws',
              Icons.gavel_outlined,
            ),
            const SizedBox(height: 12),
            _buildResponsibilityItem(
              'Maintain confidentiality of account credentials',
              Icons.security_outlined,
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                'You are responsible for maintaining the confidentiality of your account credentials.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildResponsibilityItem(String text, IconData icon) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppColors.primary.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: AppColors.primary, size: 20),
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

  Widget _buildPaidFeaturesSection() {
    return _buildSectionCard(
      icon: Icons.payment_outlined,
      iconColor: AppColors.secondary,
      title: '4. Paid Features',
      subtitle: 'Subscription and payment terms',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              AppColors.secondary.withOpacity(0.1),
              AppColors.secondary.withOpacity(0.05),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: AppColors.secondary.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.info_outline, color: AppColors.secondary, size: 24),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Some functionality may require a subscription or one-time purchase.',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w500,
                      height: 1.5,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            _buildPaidFeatureItem(
              'Pricing and benefits',
              'Described inside the app',
              Icons.attach_money,
            ),
            const SizedBox(height: 12),
            _buildPaidFeatureItem(
              'Payment processing',
              'Handled through platform store',
              Icons.shopping_cart_outlined,
            ),
            const SizedBox(height: 12),
            _buildPaidFeatureItem(
              'Store policies',
              'Subject to platform policies',
              Icons.policy_outlined,
            ),
            const SizedBox(height: 16),
            Text(
              'Payments are handled through the platform store and may be subject to their policies.',
              style: AppTextStyle.bodySmall.copyWith(
                color: AppColors.grey,
                fontStyle: FontStyle.italic,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPaidFeatureItem(String title, String subtitle, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(icon, color: AppColors.secondary, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.bodyMedium.copyWith(
                    fontWeight: FontWeight.w500,
                    color: AppColors.black,
                  ),
                ),
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
    );
  }

  Widget _buildTerminationSection() {
    return _buildSectionCard(
      icon: Icons.cancel_outlined,
      iconColor: Colors.red,
      title: '5. Termination',
      subtitle: 'When access may be suspended or terminated',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.red.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: Colors.red.withOpacity(0.3),
            width: 1,
          ),
        ),
        child: Column(
          children: [
            _buildTerminationItem(
              Icons.block,
              'We may suspend or terminate access',
              'If you breach these terms or misuse the services',
              Colors.red,
            ),
            const SizedBox(height: 16),
            _buildTerminationItem(
              Icons.exit_to_app,
              'You may stop using Kvīve',
              'At any time by uninstalling the app',
              Colors.orange,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTerminationItem(
    IconData icon,
    String title,
    String description,
    Color color,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppColors.black,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
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

  Widget _buildDisclaimersSection() {
    return _buildSectionCard(
      icon: Icons.warning_amber_outlined,
      iconColor: Colors.orange,
      title: '6. Disclaimers',
      subtitle: 'Service provided "as is"',
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
        child: Column(
          children: [
            Row(
              children: [
                Icon(Icons.info_outline, color: Colors.orange, size: 32),
                const SizedBox(width: 16),
                Expanded(
                  child: Text(
                    'Kvīve is provided "as is" without warranties of any kind.',
                    style: AppTextStyle.bodyMedium.copyWith(
                      fontWeight: FontWeight.w600,
                      color: AppColors.black,
                      height: 1.5,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.orange.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                'We do not guarantee that suggestions will be error-free or suitable for every purpose.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLiabilitySection() {
    return _buildSectionCard(
      icon: Icons.shield_outlined,
      iconColor: AppColors.primary,
      title: '7. Limitation of Liability',
      subtitle: 'Legal limitations on our responsibility',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.primary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.gavel, color: AppColors.primary, size: 24),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'To the fullest extent permitted by law, Kvīve shall not be liable for any indirect, incidental, or consequential damages arising from use of the app.',
                      style: AppTextStyle.bodyMedium.copyWith(
                        color: AppColors.black,
                        height: 1.5,
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

  Widget _buildChangesSection() {
    return _buildSectionCard(
      icon: Icons.update_outlined,
      iconColor: AppColors.secondary,
      title: '8. Changes to Terms',
      subtitle: 'How we update these terms',
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              AppColors.secondary.withOpacity(0.1),
              AppColors.secondary.withOpacity(0.05),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          children: [
            _buildChangeItem(
              Icons.edit_outlined,
              'We may modify these terms',
              'As the product evolves',
            ),
            const SizedBox(height: 16),
            _buildChangeItem(
              Icons.check_circle_outline,
              'Continued use constitutes acceptance',
              'After an update is released',
            ),
            const SizedBox(height: 16),
            _buildChangeItem(
              Icons.notifications_outlined,
              'Material changes will be communicated',
              'In-app notifications',
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.secondary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                'We may modify these terms as the product evolves. Continued use after an update constitutes acceptance. Material changes will be communicated in-app.',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  height: 1.5,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChangeItem(IconData icon, String title, String subtitle) {
    return Row(
      children: [
        Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: AppColors.secondary.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: AppColors.secondary, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: AppTextStyle.bodyMedium.copyWith(
                  fontWeight: FontWeight.w500,
                  color: AppColors.black,
                ),
              ),
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
    );
  }

  Widget _buildContactSection(BuildContext context) {
    return _buildSectionCard(
      icon: Icons.contact_support_outlined,
      iconColor: AppColors.primary,
      title: 'Contact',
      subtitle: 'Questions about these terms?',
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
        child: _buildContactItem(
          Icons.email_outlined,
          'legal@kvive.app',
          () async {
            final Uri emailUri = Uri(
              scheme: 'mailto',
              path: 'legal@kvive.app',
            );
            try {
              if (await canLaunchUrl(emailUri)) {
                await launchUrl(emailUri);
              } else {
                Clipboard.setData(const ClipboardData(text: 'legal@kvive.app'));
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
              Clipboard.setData(const ClipboardData(text: 'legal@kvive.app'));
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
