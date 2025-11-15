import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_cropper/image_cropper.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'dart:io';

class ImageCropScreen extends StatefulWidget {
  final File imageFile;

  const ImageCropScreen({super.key, required this.imageFile});

  @override
  State<ImageCropScreen> createState() => _ImageCropScreenState();
}

class _ImageCropScreenState extends State<ImageCropScreen> {
  File? croppedImage;
  bool isProcessing = false;
  bool hasStartedCropping = false;

  @override
  void initState() {
    super.initState();
    // Delay the cropping to ensure the screen is fully loaded
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!hasStartedCropping) {
        _cropImage();
      }
    });
  }

  Future<void> _cropImage() async {
    // Prevent multiple simultaneous cropping operations
    if (hasStartedCropping || isProcessing) {
      return;
    }

    setState(() {
      isProcessing = true;
      hasStartedCropping = true;
    });

    try {
      // Request permissions first
      await _requestPermissions();

      // Add timeout to prevent hanging
      final croppedFile = await Future.any([
        ImageCropper().cropImage(
          sourcePath: widget.imageFile.path,
          aspectRatio: const CropAspectRatio(
            ratioX: 16,
            ratioY: 9,
          ), // Keyboard aspect ratio
          compressFormat: ImageCompressFormat.jpg,
          compressQuality: 95, // Higher quality to preserve detail
          maxWidth: 2400,  // Ensure high resolution output
          maxHeight: 1350, // 16:9 ratio
          uiSettings: [
            AndroidUiSettings(
              toolbarTitle: 'Crop Image',
              toolbarColor: AppColors.primary,
              toolbarWidgetColor: AppColors.white,
              initAspectRatio: CropAspectRatioPreset.ratio16x9,
              lockAspectRatio: true,
              backgroundColor: AppColors.black,
              activeControlsWidgetColor: AppColors.secondary,
              cropFrameColor: AppColors.white,
              cropGridColor: AppColors.white.withOpacity(0.5),
              cropFrameStrokeWidth: 2,
              cropGridStrokeWidth: 1,
              hideBottomControls: false,
              statusBarColor: AppColors.primary,
              // SafeArea settings for proper display
              dimmedLayerColor: Colors.black.withOpacity(0.5),
              showCropGrid: true,
              cropGridRowCount: 3,
              cropGridColumnCount: 3,
            ),
            IOSUiSettings(
              title: 'Crop Image',
              doneButtonTitle: 'Done',
              cancelButtonTitle: 'Back',
              aspectRatioLockEnabled: true,
              resetAspectRatioEnabled: false,
              aspectRatioPickerButtonHidden: true,
              rotateButtonsHidden: false,
              rotateClockwiseButtonHidden: false,
              hidesNavigationBar: false,
              minimumAspectRatio: 1.0,
              // Ensure proper safe area handling on iOS
              rectX: 0,
              rectY: 0,
              rectWidth: 0,
              rectHeight: 0,
            ),
          ],
        ),
        Future.delayed(
          const Duration(minutes: 2),
          () => null,
        ), // 2 minute timeout
      ]);

      if (croppedFile != null) {
        setState(() {
          croppedImage = File(croppedFile.path);
          isProcessing = false;
        });
      } else {
        // User cancelled cropping, go back
        Navigator.of(context).pop();
      }
    } catch (e) {
      setState(() {
        isProcessing = false;
      });

      // Show detailed error message with fallback options
      String errorMessage = 'Error cropping image';
      bool isCompatibilityIssue = false;

      if (e.toString().contains('permission')) {
        errorMessage =
            'Permission denied. Please allow storage access in settings.';
      } else if (e.toString().contains('file')) {
        errorMessage =
            'File access error. Please try selecting the image again.';
      } else if (e.toString().contains('compile') ||
          e.toString().contains('symbol')) {
        errorMessage =
            'Image cropper compatibility issue. Using original image instead.';
        isCompatibilityIssue = true;
      } else if (e.toString().contains('Reply already submitted')) {
        errorMessage =
            'Cropping operation already in progress. Please try again.';
        isCompatibilityIssue = true;
      } else if (e.toString().contains('TimeoutException') ||
          e.toString().contains('timeout')) {
        errorMessage =
            'Cropping took too long. Please try again or use the original image.';
        isCompatibilityIssue = true;
      } else if (e.toString().contains('ActivityNotFoundException') ||
          e.toString().contains('UCropActivity')) {
        errorMessage =
            'Image cropping activity not found. Using original image instead.';
        isCompatibilityIssue = true;
        // Automatically use original image for this specific error
        setState(() {
          croppedImage = widget.imageFile;
          isProcessing = false;
        });
        return; // Skip showing dialog, just use original image
      } else {
        errorMessage = 'Error cropping image: ${e.toString()}';
      }

      // Show error dialog with options
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => SafeArea(
          child: AlertDialog(
            title: const Text('Cropping Error'),
            content: SingleChildScrollView(
              child: Text(errorMessage),
            ),
            actions: [
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop(); // Close dialog
                  Navigator.of(context).pop(); // Go back to previous screen
                },
                child: const Text('Cancel'),
              ),
              TextButton(
                onPressed: () {
                  Navigator.of(context).pop(); // Close dialog
                  // Use original image as fallback
                  setState(() {
                    croppedImage = widget.imageFile;
                    isProcessing = false;
                  });
                },
                child: const Text('Use Original'),
              ),
              if (!isCompatibilityIssue)
                TextButton(
                  onPressed: () {
                    Navigator.of(context).pop(); // Close dialog
                    // Reset the flag and retry
                    hasStartedCropping = false;
                    _cropImage(); // Retry
                  },
                  child: const Text('Retry'),
                ),
            ],
          ),
        ),
      );
    }
  }

  @override
  void dispose() {
    // Clean up any ongoing operations
    super.dispose();
  }

  Future<void> _requestPermissions() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Check if permissions have been previously granted and stored
      final hasStoredPermission = prefs.getBool('image_permissions_granted') ?? false;
      
      // If we've already stored that permissions were granted, check if they're still valid
      if (hasStoredPermission) {
        // Verify current permission status without requesting
        final storageStatus = await Permission.storage.status;
        final photosStatus = await Permission.photos.status;
        
        if (storageStatus.isGranted || photosStatus.isGranted) {
          // Permissions still valid, no need to ask again
          return;
        }
      }

      // Check if this is the first time asking
      final hasAskedBefore = prefs.getBool('image_permissions_asked') ?? false;
      
      if (!hasAskedBefore) {
        // First time - request permissions
        Map<Permission, PermissionStatus> statuses = await [
          Permission.storage,
          Permission.photos,
          Permission.camera,
        ].request();

        // Mark that we've asked
        await prefs.setBool('image_permissions_asked', true);

        // Check if permissions were granted
        bool anyGranted = statuses.values.any((status) => 
          status.isGranted || status.isLimited
        );

        if (anyGranted) {
          // Store that permissions were granted
          await prefs.setBool('image_permissions_granted', true);
          return;
        }
      } else {
        // We've asked before - check current status without requesting again
        final storageStatus = await Permission.storage.status;
        final photosStatus = await Permission.photos.status;
        
        if (storageStatus.isGranted || photosStatus.isGranted) {
          // Permissions granted, store it
          await prefs.setBool('image_permissions_granted', true);
          return;
        } else if (storageStatus.isDenied || photosStatus.isDenied) {
          // User previously denied, show settings dialog
          if (mounted) {
            _showPermissionSettingsDialog();
          }
          return;
        }
      }

      // If we get here and permissions aren't granted, show settings dialog
      if (mounted) {
        final storageStatus = await Permission.storage.status;
        final photosStatus = await Permission.photos.status;
        
        if (!storageStatus.isGranted && !photosStatus.isGranted) {
          _showPermissionSettingsDialog();
        }
      }
    } catch (e) {
      // If there's any error with SharedPreferences, just try to request permissions
      Map<Permission, PermissionStatus> statuses = await [
        Permission.storage,
        Permission.photos,
      ].request();
      
      bool anyGranted = statuses.values.any((status) => status.isGranted);
      if (!anyGranted && mounted) {
        _showPermissionSettingsDialog();
      }
    }
  }

  void _showPermissionSettingsDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => SafeArea(
        child: AlertDialog(
          title: const Text('Permissions Required'),
          content: SingleChildScrollView(
            child: const Text(
              'Storage or photo access is needed to crop images. '
              'Please grant permission in your device settings.\n\n'
              'Go to: Settings → Apps → AI Keyboard → Permissions',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                openAppSettings();
              },
              child: const Text('Open Settings'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[600],
      body: Column(
        children: [
          // Header with SafeArea for top only
          SafeArea(
            bottom: false,
            child: Container(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.of(context).pop(),
                    child: Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppColors.white.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(
                        Icons.arrow_back,
                        color: AppColors.white,
                        size: 24,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Text(
                      'Crop Image for Keyboard',
                      style: AppTextStyle.titleLarge.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Instructions
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(
              'Adjust the crop area to fit your keyboard. The image will be resized to 16:9 aspect ratio.',
              style: AppTextStyle.bodyMedium.copyWith(
                color: AppColors.white.withOpacity(0.8),
              ),
              textAlign: TextAlign.center,
            ),
          ),

          const SizedBox(height: 20),

          // Processing indicator or cropped image preview
          Expanded(
            child: Center(
              child: isProcessing
                  ? Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const CircularProgressIndicator(
                          color: AppColors.secondary,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'Processing image...',
                          style: AppTextStyle.bodyLarge.copyWith(
                            color: AppColors.white,
                          ),
                        ),
                      ],
                    )
                  : croppedImage != null
                  ? Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // Cropped image preview - 80% of screen width, centered
                        FractionallySizedBox(
                          widthFactor: 0.8, // 80% of screen width
                          child: Container(
                            margin: const EdgeInsets.symmetric(vertical: 16),
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(12),
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.black.withOpacity(0.3),
                                  blurRadius: 10,
                                  offset: const Offset(0, 5),
                                ),
                              ],
                            ),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(12),
                              child: AspectRatio(
                                aspectRatio: 16 / 9,
                                child: Image.file(
                                  croppedImage!,
                                  fit: BoxFit.cover,
                                ),
                              ),
                            ),
                          ),
                        ),

                        // Success message
                        Text(
                          'Image cropped successfully!',
                          style: AppTextStyle.bodyLarge.copyWith(
                            color: AppColors.white,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Your keyboard background is ready',
                          style: AppTextStyle.bodyMedium.copyWith(
                            color: AppColors.white.withOpacity(0.8),
                          ),
                        ),
                      ],
                    )
                  : const SizedBox(),
            ),
          ),

          // Action buttons with SafeArea for bottom only - 80% width centered
          if (croppedImage != null)
            SafeArea(
              top: false,
              child: Center(
                child: FractionallySizedBox(
                  widthFactor: 0.8, // 80% of screen width
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Row(
                      children: [
                        // Back button
                        Expanded(
                          child: GestureDetector(
                            onTap: () => Navigator.of(context).pop(),
                            child: Container(
                              height: 48,
                              decoration: BoxDecoration(
                                color: AppColors.white.withOpacity(0.2),
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(
                                  color: AppColors.white.withOpacity(0.3),
                                ),
                              ),
                              child: Center(
                                child: Text(
                                  'Back',
                                  style: AppTextStyle.bodyMedium.copyWith(
                                    color: AppColors.white,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),

                        const SizedBox(width: 12),

                        // Done button
                        Expanded(
                          child: GestureDetector(
                            onTap: () {
                              // Return the cropped image to the previous screen
                              Navigator.of(context).pop(croppedImage);
                            },
                            child: Container(
                              height: 48,
                              decoration: BoxDecoration(
                                color: AppColors.secondary,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Center(
                                child: Text(
                                  'Done',
                                  style: AppTextStyle.bodyMedium.copyWith(
                                    color: AppColors.white,
                                    fontWeight: FontWeight.w600,
                                  ),
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
            ),
        ],
      ),
    );
  }
}
