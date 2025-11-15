import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter/material.dart';

class UpgradeProScreen extends StatefulWidget {
  const UpgradeProScreen({super.key});

  @override
  State<UpgradeProScreen> createState() => _UpgradeProScreenState();
}

class _UpgradeProScreenState extends State<UpgradeProScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.primary,

      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Align(
              alignment: Alignment.topRight,
              child: IconButton(
                onPressed: () {
                  Navigator.pop(context);
                },
                icon: Icon(Icons.close, color: AppColors.white),
              ),
            ),
            _buildHeader(),
            Spacer(),
            Column(
              spacing: 24,
              children: [
                _upgradeTileOption(
                  title: 'Basic Plan',
                  subtitle: '14 days free trial',
                  trailing: Text(
                    'Free',
                    style: AppTextStyle.headlineMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                  icon: AppIcons.person_icon,
                  bgcolor: AppColors.grey,
                ),
                _upgradeTileOption(
                  title: 'Yearly Plan',
                  subtitle: '14 days free trial',
                  trailing: Text(
                    'Rs 10/mo',
                    style: AppTextStyle.headlineMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                  icon: AppIcons.crown_image,
                  bgcolor: AppColors.secondary,
                ),
                _upgradeTileOption(
                  title: 'Unlimited',
                  subtitle: 'One time purchas',
                  trailing: Text(
                    'Rs 750.00',
                    style: AppTextStyle.headlineMedium.copyWith(
                      color: AppColors.white,
                    ),
                  ),
                  icon: AppIcons.diamond_image,
                  bgcolor: AppColors.tertiary,
                ),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  // spacing: 16,
                  children: [
                    TextButton(
                      onPressed: () {},
                      child: Text(
                        'Restore Purchase',
                        style: AppTextStyle.bodyMedium.copyWith(
                          color: AppColors.grey,
                        ),
                      ),
                    ),
                    CircleAvatar(radius: 4, backgroundColor: AppColors.grey),
                    TextButton(
                      onPressed: () {},
                      child: Text(
                        'Terms and conditions',
                        style: AppTextStyle.bodyMedium.copyWith(
                          color: AppColors.grey,
                        ),
                      ),
                    ),
                  ],
                ),
                SizedBox(height: 16),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        // color: AppColors.secondary,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          Image.asset(
            AppIcons.pro_icon,
            width: 130,
            height: 130,
            // color: AppColors.secondary,
          ),
          const SizedBox(height: 16),
          Text(
            'Upgrade to',
            style: AppTextStyle.headlineLarge.copyWith(color: AppColors.white),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            'Premium',
            style: AppTextStyle.headlineSmall.copyWith(
              color: AppColors.secondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 24),
          Text(
            'Upgrade you subscript',
            style: AppTextStyle.bodyLarge.copyWith(color: AppColors.grey),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildPricingCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.secondary.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.secondary.withOpacity(0.3),
          width: 2,
        ),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                '\$9.99',
                style: AppTextStyle.headlineLarge.copyWith(
                  fontSize: 36,
                  color: AppColors.secondary,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(width: 8),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '/month',
                    style: AppTextStyle.bodyLarge.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                  Text(
                    'Cancel anytime',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ],
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
              '7-Day Free Trial',
              style: AppTextStyle.bodySmall.copyWith(
                color: AppColors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFeaturesList() {
    final List<Map<String, dynamic>> features = [
      {
        'title': 'Unlimited AI Suggestions',
        'subtitle': 'Get smart text predictions without limits',
        'icon': Icons.auto_awesome,
      },
      {
        'title': 'Advanced Autocorrect',
        'subtitle': 'AI-powered spelling and grammar correction',
        'icon': Icons.spellcheck,
      },
      {
        'title': 'Multiple Languages',
        'subtitle': 'Support for 50+ languages',
        'icon': Icons.language,
      },
      {
        'title': 'Custom Themes',
        'subtitle': 'Access to premium keyboard themes',
        'icon': Icons.palette,
      },
      {
        'title': 'Priority Support',
        'subtitle': 'Get help faster with priority support',
        'icon': Icons.support_agent,
      },
      {
        'title': 'Cloud Sync',
        'subtitle': 'Sync your preferences across devices',
        'icon': Icons.cloud_sync,
      },
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'What\'s Included',
          style: AppTextStyle.headlineSmall.copyWith(
            color: AppColors.grey,
            fontSize: 20,
          ),
        ),
        const SizedBox(height: 16),
        ...features.map(
          (feature) => _buildFeatureItem(
            title: feature['title'],
            subtitle: feature['subtitle'],
            icon: feature['icon'],
          ),
        ),
      ],
    );
  }

  Widget _buildFeatureItem({
    required String title,
    required String subtitle,
    required IconData icon,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.grey.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.secondary.withOpacity(0.1),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(icon, color: AppColors.secondary, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.headlineSmall.copyWith(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
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
          Icon(Icons.check_circle, color: AppColors.secondary, size: 20),
        ],
      ),
    );
  }

  Widget _buildUpgradeButton() {
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: () {
          // TODO: Implement upgrade functionality
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Upgrade functionality coming soon!'),
              backgroundColor: AppColors.secondary,
            ),
          );
        },
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.secondary,
          foregroundColor: AppColors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          elevation: 0,
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Image.asset(
              AppIcons.crown,
              width: 20,
              height: 20,
              color: AppColors.white,
            ),
            const SizedBox(width: 8),
            Text(
              'Start Free Trial',
              style: AppTextStyle.headlineSmall.copyWith(
                color: AppColors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTermsText() {
    return Center(
      child: RichText(
        textAlign: TextAlign.center,
        text: TextSpan(
          style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
          children: [
            const TextSpan(text: 'By subscribing, you agree to our '),
            TextSpan(
              text: 'Terms of Service',
              style: AppTextStyle.bodySmall.copyWith(
                color: AppColors.secondary,
                decoration: TextDecoration.underline,
              ),
            ),
            const TextSpan(text: ' and '),
            TextSpan(
              text: 'Privacy Policy',
              style: AppTextStyle.bodySmall.copyWith(
                color: AppColors.secondary,
                decoration: TextDecoration.underline,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _upgradeTileOption extends StatelessWidget {
  final String title;
  final String subtitle;
  final Widget trailing;
  final String icon;
  final Color bgcolor;

  const _upgradeTileOption({
    super.key,
    required this.title,
    required this.subtitle,
    required this.trailing,
    required this.icon,
    required this.bgcolor,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: 100,
      contentPadding: EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      trailing: trailing,
      dense: true,
      leading: Container(
        width: 52,
        height: 52,
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: AppColors.white,
          border: Border.all(color: bgcolor.withOpacity(0.2)),
          shape: BoxShape.circle,
        ),

        child: Image.asset(icon, width: 36, height: 36),
      ),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      tileColor: bgcolor,
      title: Text(
        title,
        style: AppTextStyle.headlineSmall.copyWith(color: AppColors.white),
      ),
      subtitle: Text(
        subtitle,
        style: AppTextStyle.bodyMedium.copyWith(color: AppColors.white),
      ),
    );
  }
}
