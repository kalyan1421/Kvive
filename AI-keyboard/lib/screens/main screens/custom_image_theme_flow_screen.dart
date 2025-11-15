import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/screens/main screens/image_crop_screen.dart';
import 'package:ai_keyboard/theme/theme_v2.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'package:path_provider/path_provider.dart';

/// Complete custom image theme creation flow
/// Steps: Select Image → Crop → Adjust Brightness → Enter Name → Save
class CustomImageThemeFlowScreen extends StatefulWidget {
  final String baseTheme; // 'light' or 'dark'

  const CustomImageThemeFlowScreen({
    super.key,
    required this.baseTheme,
  });

  @override
  State<CustomImageThemeFlowScreen> createState() =>
      _CustomImageThemeFlowScreenState();
}

class _CustomImageThemeFlowScreenState
    extends State<CustomImageThemeFlowScreen> {
  File? selectedImage;
  File? croppedImage;
  double brightnessValue = 0.85; // Default brightness
  String themeName = '';
  bool isSaving = false;

  @override
  void initState() {
    super.initState();
    // Show image picker immediately
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _showImageSourcePicker();
    });
  }

  Future<void> _showImageSourcePicker() async {
    final ImageSource? source = await showModalBottomSheet<ImageSource>(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        decoration: const BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(24),
            topRight: Radius.circular(24),
          ),
        ),
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Handle bar
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.grey.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Title
            Text(
              'Choose Image Source',
              style: AppTextStyle.headlineMedium.copyWith(
                color: AppColors.black,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 24),

            // Gallery option
            _buildSourceOption(
              icon: Icons.photo_library,
              title: 'Gallery',
              subtitle: 'Choose from your photos',
              color: AppColors.secondary,
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),

            const SizedBox(height: 16),

            // Camera option
            _buildSourceOption(
              icon: Icons.camera_alt,
              title: 'Camera',
              subtitle: 'Take a new photo',
              color: AppColors.tertiary,
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),

            const SizedBox(height: 16),

            // Cancel button
            GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: BoxDecoration(
                  color: AppColors.lightGrey,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Center(
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.titleMedium.copyWith(
                      color: AppColors.grey,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );

    if (source != null) {
      await _pickImage(source);
    } else {
      // User cancelled, go back
      Navigator.pop(context);
    }
  }

  Widget _buildSourceOption({
    required IconData icon,
    required String title,
    required String subtitle,
    required Color color,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.3)),
        ),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color,
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
                    style: AppTextStyle.titleMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w700,
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
            Icon(Icons.arrow_forward_ios, color: color, size: 20),
          ],
        ),
      ),
    );
  }

  Future<void> _pickImage(ImageSource source) async {
    try {
      final ImagePicker picker = ImagePicker();
      final XFile? image = await picker.pickImage(
        source: source,
        maxWidth: 2400,  // Higher resolution for better quality
        maxHeight: 1350, // 16:9 aspect ratio
        imageQuality: 95, // Higher quality to avoid blur
      );

      if (image != null) {
        setState(() {
          selectedImage = File(image.path);
        });

        // Navigate to crop screen
        await _cropImage();
      } else {
        // User cancelled image picker, go back
        Navigator.pop(context);
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Error selecting image: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
      Navigator.pop(context);
    }
  }

  Future<void> _cropImage() async {
    if (selectedImage == null) return;

    try {
      final croppedFile = await Navigator.of(context).push<File>(
        MaterialPageRoute(
          builder: (context) => ImageCropScreen(
            imageFile: selectedImage!,
          ),
        ),
      );

      if (croppedFile != null) {
        setState(() {
          croppedImage = croppedFile;
        });
      } else {
        // User cancelled cropping, go back
        Navigator.pop(context);
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Error cropping image: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
      Navigator.pop(context);
    }
  }

  void _showBrightnessDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => Dialog(
          backgroundColor: Colors.transparent,
          insetPadding: const EdgeInsets.all(16),
          child: Container(
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Title
                Text(
                  'Adjust Brightness',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 24),

                // Keyboard Preview with Image
                Container(
                  height: 180,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.1),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        // Background image
                        if (croppedImage != null)
                          ColorFiltered(
                            colorFilter: ColorFilter.mode(
                              Colors.black.withOpacity(1 - brightnessValue),
                              BlendMode.darken,
                            ),
                            child: Image.file(
                              croppedImage!,
                              fit: BoxFit.cover,
                            ),
                          ),

                        // Keyboard overlay preview
                        _buildKeyboardPreview(widget.baseTheme),
                      ],
                    ),
                  ),
                ),

                const SizedBox(height: 24),

                // Brightness Slider
                Row(
                  children: [
                    const Icon(Icons.brightness_low, color: AppColors.grey),
                    Expanded(
                      child: SliderTheme(
                        data: SliderTheme.of(context).copyWith(
                          activeTrackColor: AppColors.secondary,
                          inactiveTrackColor: AppColors.lightGrey,
                          thumbColor: AppColors.secondary,
                          thumbShape:
                              const RoundSliderThumbShape(enabledThumbRadius: 10),
                          overlayColor: AppColors.secondary.withOpacity(0.2),
                          trackHeight: 4,
                        ),
                        child: Slider(
                          value: brightnessValue,
                          min: 0.3,
                          max: 1.0,
                          onChanged: (value) {
                            setDialogState(() {
                              brightnessValue = value;
                            });
                            setState(() {
                              brightnessValue = value;
                            });
                          },
                        ),
                      ),
                    ),
                    const Icon(Icons.brightness_high, color: AppColors.secondary),
                  ],
                ),

                const SizedBox(height: 24),

                // Action Button
                GestureDetector(
                  onTap: () {
                    Navigator.pop(context);
                    _showThemeNameDialog();
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [Color(0xFF7B2CBF), Color(0xFF5A189A)],
                        begin: Alignment.centerLeft,
                        end: Alignment.centerRight,
                      ),
                      borderRadius: BorderRadius.circular(30),
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.secondary.withOpacity(0.3),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Center(
                      child: Text(
                        'Set',
                        style: AppTextStyle.titleMedium.copyWith(
                          color: AppColors.white,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showThemeNameDialog() {
    final TextEditingController nameController = TextEditingController();

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Title
              Text(
                'Enter Theme Name',
                style: AppTextStyle.titleLarge.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 24),

              // Input field
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.lightGrey.withOpacity(0.5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: TextField(
                  controller: nameController,
                  autofocus: true,
                  decoration: InputDecoration(
                    hintText: 'Theme Name',
                    hintStyle:
                        AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                    border: InputBorder.none,
                  ),
                  style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                  onSubmitted: (value) {
                    if (value.trim().isNotEmpty) {
                      Navigator.pop(context);
                      _saveTheme(value.trim());
                    }
                  },
                ),
              ),

              const SizedBox(height: 24),

              // Save Button
              GestureDetector(
                onTap: () {
                  if (nameController.text.trim().isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Please enter a theme name'),
                        backgroundColor: Colors.orange,
                      ),
                    );
                    return;
                  }

                  Navigator.pop(context);
                  _saveTheme(nameController.text.trim());
                },
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF7B2CBF), Color(0xFF5A189A)],
                      begin: Alignment.centerLeft,
                      end: Alignment.centerRight,
                    ),
                    borderRadius: BorderRadius.circular(30),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.secondary.withOpacity(0.3),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Center(
                    child: Text(
                      'Save',
                      style: AppTextStyle.titleMedium.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _saveTheme(String name) async {
    if (croppedImage == null) return;

    setState(() {
      isSaving = true;
      themeName = name;
    });

    try {
      // Show loading indicator
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => Center(
          child: Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(color: AppColors.secondary),
                const SizedBox(height: 16),
                Text(
                  'Saving theme...',
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      );

      // Copy image to permanent location
      final String savedPath = await _saveImageForKeyboard(croppedImage!);

      // Create theme
      final themeId = 'custom_${DateTime.now().millisecondsSinceEpoch}';
      final theme = widget.baseTheme == 'light'
          ? KeyboardThemeV2.createWhiteTheme()
          : KeyboardThemeV2.createDarkTheme();

      final customTheme = theme.copyWith(
        id: themeId,
        name: themeName,
        background: ThemeBackground(
          type: 'image',
          color: Colors.transparent,
          imagePath: savedPath,
          imageOpacity: brightnessValue,
          gradient: null,
          overlayEffects: const [],
          adaptive: null,
        ),
      );

      // Save to preferences
      await _saveCustomTheme(customTheme);

      // Apply theme
      await ThemeManagerV2.saveThemeV2(customTheme);

      // Close loading dialog
      Navigator.pop(context);

      // Show success and go back
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('✅ Theme "$themeName" saved successfully!'),
          backgroundColor: Colors.green,
          duration: const Duration(seconds: 2),
        ),
      );

      // Go back to home
      Navigator.pop(context, customTheme);
    } catch (e) {
      // Close loading dialog
      Navigator.pop(context);

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Error saving theme: ${e.toString()}'),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      setState(() {
        isSaving = false;
      });
    }
  }

  Future<String> _saveImageForKeyboard(File imageFile) async {
    try {
      // Use external storage directory (accessible by keyboard service)
      final Directory? externalDir = await getExternalStorageDirectory();
      if (externalDir == null) {
        throw Exception('External storage not available');
      }

      // Create keyboard_themes directory in external storage
      final String themeImagesDir = '${externalDir.path}/keyboard_themes';
      final Directory themesDir = Directory(themeImagesDir);

      if (!await themesDir.exists()) {
        await themesDir.create(recursive: true);
      }

      // Save with timestamp for unique filename
      final String fileName =
          'theme_bg_${DateTime.now().millisecondsSinceEpoch}.jpg';
      final String savedPath = '$themeImagesDir/$fileName';
      
      // Copy the image to external storage
      final File savedFile = await imageFile.copy(savedPath);

      debugPrint('✅ Image saved to: $savedPath');
      debugPrint('📁 File exists: ${await savedFile.exists()}');
      debugPrint('📊 File size: ${await savedFile.length()} bytes');

      return savedFile.path;
    } catch (e) {
      debugPrint('❌ Error saving image: $e');
      throw Exception('Failed to save image: $e');
    }
  }

  Future<void> _saveCustomTheme(KeyboardThemeV2 theme) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final List<String> customThemes =
          prefs.getStringList('custom_themes_v2') ?? [];

      // Add new theme
      customThemes.add(json.encode(theme.toJson()));

      // Save
      await prefs.setStringList('custom_themes_v2', customThemes);
      debugPrint('✅ Custom theme saved to preferences');
    } catch (e) {
      throw Exception('Failed to save custom theme: $e');
    }
  }

  Widget _buildKeyboardPreview(String baseTheme) {
    final bool isDark = baseTheme == 'dark';

    return Container(
      padding: const EdgeInsets.all(8),
      child: Column(
        children: [
          // Suggestion bar
          Container(
            height: 32,
            margin: const EdgeInsets.only(bottom: 4),
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: isDark
                  ? Colors.black.withOpacity(0.6)
                  : Colors.white.withOpacity(0.6),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: ['I', 'the', 'you']
                  .map((suggestion) => Expanded(
                        child: Center(
                          child: Text(
                            suggestion,
                            style: TextStyle(
                              color: isDark ? Colors.white : Colors.black,
                              fontSize: 12,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ))
                  .toList(),
            ),
          ),

          // Keyboard keys
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _buildPreviewKeyRow(
                    ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'], isDark),
                _buildPreviewKeyRow(['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'], isDark),
                _buildPreviewKeyRow(['Z', 'X', 'C', 'V', 'B', 'N', 'M'], isDark),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewKeyRow(List<String> keys, bool isDark) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: keys
          .map((key) => Expanded(
                child: Container(
                  margin: const EdgeInsets.all(1),
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  decoration: BoxDecoration(
                    color: isDark
                        ? Colors.grey[800]!.withOpacity(0.7)
                        : Colors.white.withOpacity(0.7),
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Center(
                    child: Text(
                      key,
                      style: TextStyle(
                        color: isDark ? Colors.white : Colors.black,
                        fontWeight: FontWeight.w600,
                        fontSize: 9,
                      ),
                    ),
                  ),
                ),
              ))
          .toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.black,
      body: SafeArea(
        child: croppedImage != null
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Show cropped image
                    Container(
                      margin: const EdgeInsets.all(24),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.3),
                            blurRadius: 20,
                            offset: const Offset(0, 10),
                          ),
                        ],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child: AspectRatio(
                          aspectRatio: 16 / 9,
                          child: Image.file(
                            croppedImage!,
                            fit: BoxFit.cover,
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 32),

                    // Success message
                    Text(
                      'Image Ready!',
                      style: AppTextStyle.headlineMedium.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Now let\'s adjust the brightness',
                      style: AppTextStyle.bodyMedium.copyWith(
                        color: AppColors.white.withOpacity(0.8),
                      ),
                    ),

                    const SizedBox(height: 32),

                    // Continue button
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                      child: GestureDetector(
                        onTap: _showBrightnessDialog,
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [Color(0xFF7B2CBF), Color(0xFF5A189A)],
                              begin: Alignment.centerLeft,
                              end: Alignment.centerRight,
                            ),
                            borderRadius: BorderRadius.circular(30),
                            boxShadow: [
                              BoxShadow(
                                color: AppColors.secondary.withOpacity(0.3),
                                blurRadius: 12,
                                offset: const Offset(0, 4),
                              ),
                            ],
                          ),
                          child: Center(
                            child: Text(
                              'Continue',
                              style: AppTextStyle.titleMedium.copyWith(
                                color: AppColors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              )
            : const Center(
                child: CircularProgressIndicator(color: AppColors.secondary),
              ),
      ),
    );
  }
}

