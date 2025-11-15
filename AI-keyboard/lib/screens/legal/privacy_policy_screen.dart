import 'package:flutter/material.dart';

class PrivacyPolicyScreen extends StatelessWidget {
  const PrivacyPolicyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Privacy Policy'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Kvive Privacy Policy',
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
              title: '1. Information We Collect',
              body:
                  'We collect information you provide voluntarily, such as your account details and feedback. '
                  'We also collect limited usage analytics to improve suggestion quality and keyboard performance. '
                  'Kvive does not store what you type outside of optional cloud sync features you enable.',
            ),
            _buildSection(
              context,
              title: '2. How We Use Your Information',
              body:
                  'Your information helps us deliver core keyboard features, personalise language models, and respond '
                  'to support requests. Aggregated analytics help us understand feature adoption and reliability.',
            ),
            _buildSection(
              context,
              title: '3. Data Storage and Security',
              body:
                  'We apply encryption in transit and at rest where available. Sensitive authentication data is managed '
                  'through trusted providers such as Firebase. You can request deletion of synced preferences at any time.',
            ),
            _buildSection(
              context,
              title: '4. Third-Party Services',
              body:
                  'Kvive integrates with services like Firebase Authentication, Cloud Firestore, and Google Sign-In. '
                  'These providers have their own privacy policies which govern the handling of your data on their platforms.',
            ),
            _buildSection(
              context,
              title: '5. Your Choices',
              body:
                  'You may disable analytics, revoke keyboard permissions, or delete your account from the in-app settings. '
                  'Uninstalling the application removes locally stored preferences.',
            ),
            _buildSection(
              context,
              title: '6. Contact Us',
              body:
                  'If you have questions about this policy or how your data is handled, contact us at support@kvive.app.',
            ),
            const SizedBox(height: 24),
            Text(
              'We may update this policy from time to time. Material changes will be communicated in-app or on our website.',
              style: textTheme.bodyMedium,
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
