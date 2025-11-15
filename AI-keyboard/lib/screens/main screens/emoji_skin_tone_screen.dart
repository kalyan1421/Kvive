import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:shared_preferences/shared_preferences.dart';

class EmojiSkinToneScreen extends StatefulWidget {
  const EmojiSkinToneScreen({super.key});

  @override
  State<EmojiSkinToneScreen> createState() => _EmojiSkinToneScreenState();
}

class _EmojiSkinToneScreenState extends State<EmojiSkinToneScreen> {
  static const platform = MethodChannel('ai_keyboard/config');
  
  int selectedSkinTone = 0; // 0 = Default, 1-5 = Light to Dark
  bool _isLoading = true;

  // Skin tone modifiers matching Kotlin side
  final List<String> skinToneModifiers = ['', '🏻', '🏼', '🏽', '🏾', '🏿'];
  
  final List<Map<String, dynamic>> skinToneOptions = [
    {
      'name': 'Default',
      'emojis': [
        '👋',
        '🙏',
        '👍',
        '🤝',
        '👏',
        '💪',
        '👱',
        '👨',
        '🧑‍🦯',
        '🎓',
        '👨‍🎓',
      ],
      'description': 'Default yellow skin tone',
    },
    {
      'name': 'Light Skin',
      'emojis': [
        '👋🏻',
        '🙏🏻',
        '👍🏻',
        '🤝🏻',
        '👏🏻',
        '💪🏻',
        '👱🏻',
        '👨🏻',
        '🧑‍🦯🏻',
        '🎓🏻',
      ],
      'description': 'Very light skin tone',
    },
    {
      'name': 'Medium Light Skin',
      'emojis': [
        '👋🏼',
        '🙏🏼',
        '👍🏼',
        '🤝🏼',
        '👏🏼',
        '💪🏼',
        '👱🏼',
        '👨🏼',
        '🧑‍🦯🏼',
        '🎓🏼',
      ],
      'description': 'Light beige skin tone',
    },
    {
      'name': 'Medium Skin',
      'emojis': [
        '👋🏽',
        '🙏🏽',
        '👍🏽',
        '🤝🏽',
        '👏🏽',
        '💪🏽',
        '👱🏽',
        '👨🏽',
        '🧑‍🦯🏽',
        '🎓🏽',
      ],
      'description': 'Medium brown skin tone',
    },
    {
      'name': 'Medium Dark Skin',
      'emojis': [
        '👋🏾',
        '🙏🏾',
        '👍🏾',
        '🤝🏾',
        '👏🏾',
        '💪🏾',
        '👱🏾',
        '👨🏾',
        '🧑‍🦯🏾',
        '🎓🏾',
      ],
      'description': 'Darker brown skin tone',
    },
    {
      'name': 'Dark Skin',
      'emojis': [
        '👋🏿',
        '🙏🏿',
        '👍🏿',
        '🤝🏿',
        '👏🏿',
        '💪🏿',
        '👱🏿',
        '👨🏿',
        '🧑‍🦯🏿',
        '🎓🏿',
      ],
      'description': 'Very dark skin tone',
    },
  ];

  @override
  void initState() {
    super.initState();
    _loadSkinToneSettings();
  }

  Future<void> _loadSkinToneSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedTone = prefs.getString('emoji_skin_tone') ?? '';
      
      // Map saved tone modifier to index
      setState(() {
        selectedSkinTone = skinToneModifiers.indexOf(savedTone);
        if (selectedSkinTone == -1) selectedSkinTone = 0;
        _isLoading = false;
      });
    } catch (e) {
      print('Error loading skin tone settings: $e');
      setState(() => _isLoading = false);
    }
  }

  Future<void> _saveSkinToneSettings(int index) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final skinTone = skinToneModifiers[index];
      await prefs.setString('emoji_skin_tone', skinTone);
      
      // Get history max size
      final historyMaxSize = prefs.getDouble('emoji_history_max_size')?.toInt() ?? 90;
      
      // Notify keyboard service via MethodChannel
      try {
        await platform.invokeMethod('updateEmojiSettings', {
          'skinTone': skinTone,
          'historyMaxSize': historyMaxSize,
        });
        print('✓ Skin tone sent to keyboard: $skinTone (index $index)');
      } catch (e) {
        print('⚠️ Error calling updateEmojiSettings MethodChannel: $e');
      }
      
      if (mounted) {
        final toneName = _getSkinToneName(index);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Skin tone set to: $toneName'),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      print('Error saving skin tone settings: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error saving settings: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  String _getSkinToneName(int index) {
    switch (index) {
      case 0: return 'Default';
      case 1: return 'Light Skin';
      case 2: return 'Medium Light Skin';
      case 3: return 'Medium Skin';
      case 4: return 'Medium Dark Skin';
      case 5: return 'Dark Skin';
      default: return 'Default';
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.white),
            onPressed: () => Navigator.pop(context),
          ),
          title: Text(
            'Emojis Skin Tone',
            style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
          ),
          centerTitle: false,
          backgroundColor: AppColors.primary,
          elevation: 0,
        ),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Emojis Skin Tone',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        actionsPadding: const EdgeInsets.only(right: 16),
        actions: [
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
      backgroundColor: AppColors.white,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),
            // Skin Tone Options
            ...skinToneOptions.asMap().entries.map((entry) {
              int index = entry.key;
              Map<String, dynamic> option = entry.value;
              bool isSelected = selectedSkinTone == index;

              return Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _buildSkinToneCard(
                  name: option['name'],
                  emojis: List<String>.from(option['emojis']),
                  isSelected: isSelected,
                  onTap: () {
                    setState(() => selectedSkinTone = index);
                    _saveSkinToneSettings(index);
                  },
                ),
              );
            }),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildSkinToneCard({
    required String name,
    required List<String> emojis,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          // color: AppColors.lightGrey,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppColors.secondary : AppColors.lightGrey,
            width: 2,
          ),
        ),
        child: Column(
          children: [
            // Emoji Grid
            _buildEmojiGrid(emojis),
            Divider(color: AppColors.lightGrey, thickness: 1),
            // Name and Selection Indicator
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  name,
                  style: AppTextStyle.titleMedium.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                _buildSelectionIndicator(isSelected),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmojiGrid(List<String> emojis) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          // Hand emoji section - 30% of width
          Expanded(
            flex: 3,
            child: Container(
              height: 120, // Fixed height for hand emoji container
              decoration: BoxDecoration(
                // color: AppColors.lightGrey.withOpacity(0.3),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Center(
                child: Text(
                  emojis[0], // First emoji (hand emoji)
                  style: const TextStyle(fontSize: 48),
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          // Grid view section - 70% of width
          Expanded(
            flex: 7,
            child: SizedBox(
              height: 120, // Same height as hand emoji container
              child: GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 4, // Reduced to 4 to fit better
                  crossAxisSpacing: 4,
                  mainAxisSpacing: 4,
                  childAspectRatio: 1,
                ),
                itemCount:
                    emojis.length - 1, // Exclude the first emoji (hand emoji)
                itemBuilder: (context, index) {
                  return Container(
                    decoration: BoxDecoration(
                      color: AppColors.lightGrey.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Center(
                      child: Text(
                        emojis[index + 1], // Skip first emoji (hand emoji)
                        style: const TextStyle(fontSize: 20),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSelectionIndicator(bool isSelected) {
    return Container(
      width: 24,
      height: 24,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: isSelected ? AppColors.secondary : AppColors.white,
        border: Border.all(
          color: isSelected ? AppColors.secondary : AppColors.grey,
          width: 2,
        ),
      ),
      child: isSelected
          ? const Icon(Icons.check, color: AppColors.white, size: 16)
          : null,
    );
  }
}
