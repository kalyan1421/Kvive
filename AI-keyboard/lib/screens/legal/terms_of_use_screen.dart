import 'package:flutter/material.dart';

class TermsOfUseScreen extends StatelessWidget {
  const TermsOfUseScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Terms of Use'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Kvive Terms of Use',
              style: textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Text(
              'Last updated: October 2024',
              style: textTheme.bodyMedium?.copyWith(color: Colors.grey[600]),
            ),
            const SizedBox(height: 24),
            _buildSection(
              context,
              title: '1. Acceptance of Terms',
              body:
                  'By installing or using Kvive, you agree to these Terms of Use. If you do not agree, remove the application '
                  'and discontinue use immediately.',
            ),
            _buildSection(
              context,
              title: '2. License',
              body:
                  'Kvive grants you a personal, non-transferable, revocable license to use the application for personal and non-commercial purposes.',
            ),
            _buildSection(
              context,
              title: '3. User Responsibilities',
              body:
                  'You agree not to misuse the app, attempt to access restricted services, or violate applicable laws. '
                  'You are responsible for maintaining the confidentiality of your account credentials.',
            ),
            _buildSection(
              context,
              title: '4. Paid Features',
              body:
                  'Some functionality may require a subscription or one-time purchase. Pricing and available benefits are described inside the app. '
                  'Payments are handled through the platform store and may be subject to their policies.',
            ),
            _buildSection(
              context,
              title: '5. Termination',
              body:
                  'We may suspend or terminate access if you breach these terms or misuse the services. You may stop using Kvive at any time by uninstalling it.',
            ),
            _buildSection(
              context,
              title: '6. Disclaimers',
              body:
                  'Kvive is provided “as is” without warranties of any kind. We do not guarantee that suggestions will be error-free or suitable for every purpose.',
            ),
            _buildSection(
              context,
              title: '7. Limitation of Liability',
              body:
                  'To the fullest extent permitted by law, Kvive shall not be liable for any indirect, incidental, or consequential damages arising from use of the app.',
            ),
            _buildSection(
              context,
              title: '8. Changes to Terms',
              body:
                  'We may modify these terms as the product evolves. Continued use after an update constitutes acceptance. '
                  'Material changes will be communicated in-app.',
            ),
            _buildSection(
              context,
              title: '9. Contact',
              body:
                  'For questions about these terms, reach us at legal@kvive.app.',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSection(BuildContext context, {required String title, required String body}) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          Text(
            body,
            style: textTheme.bodyMedium?.copyWith(height: 1.5),
          ),
        ],
      ),
    );
  }
}
