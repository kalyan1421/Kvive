import 'package:flutter/material.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'info_app_screen.dart';
import 'package:ai_keyboard/services/firebase_auth_service.dart';
import 'package:ai_keyboard/screens/login/login_illustraion_screen.dart';
import 'package:ai_keyboard/screens/main screens/home_screen.dart';
import 'package:ai_keyboard/screens/main screens/mainscreen.dart';
import 'package:shared_preferences/shared_preferences.dart';
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final FirebaseAuthService _authService = FirebaseAuthService();
  String _displayName = 'User';
  String _userEmail = '';

  @override
  void initState() {
    super.initState();
    _loadUserInfo();
  }

  Future<void> _loadUserInfo() async {
    final user = _authService.currentUser;
    final prefs = await SharedPreferences.getInstance();
    
    if (user != null) {
      // If logged in, use Firebase displayName, fallback to saved name, then email
      setState(() {
        _displayName = user.displayName ?? 
                      prefs.getString('user_display_name') ?? 
                      user.email?.split('@').first ?? 
                      'User';
        _userEmail = user.email ?? '';
      });
    } else {
      // If not logged in, use saved name from SharedPreferences
      setState(() {
        _displayName = prefs.getString('user_display_name') ?? 'User';
        _userEmail = '';
      });
    }
  }

  bool get _isUserLoggedIn => _authService.currentUser != null;

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600; // Base scale factor
    
    return Scaffold(
      backgroundColor: AppColors.white,
      body: SingleChildScrollView(
        child: Padding(
          padding: EdgeInsets.all(16.0 * scaleFactor),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // _ReminderCard(
              //   onUpgradeTap: () {
              //     if (!_isUserLoggedIn) {
              //       // Navigate to login screen if user is not logged in
              //       Navigator.push(
              //         context,
              //         MaterialPageRoute(
              //           builder: (context) => const LoginIllustraionScreen(),
              //         ),
              //       );
              //     } else {
              //       // TODO: Navigate to upgrade/premium screen for logged in users
              //     }
              //   },
              // ),
              SizedBox(height: 24 * scaleFactor),
              Text(
                'Profile',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.secondary,
                ),
              ),
              SizedBox(height: 12 * scaleFactor),
              // Always show Change Profile Name option
              _TileOption(
                title: 'Change Profile Name',
                subtitle: _isUserLoggedIn ? 'Edit or change profile name' : 'Set your display name',
                icon: AppIcons.profile_color,
                onTap: () => _showChangeNameDialog(context),
              ),
              SizedBox(height: 12 * scaleFactor),
              _TileOption(
                title: 'Theme',
                subtitle: 'Edit or change theme',
                icon: AppIcons.theme_color,
                onTap: () {
                   Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const mainscreen(initialIndex: 1),
      ),
    );
                },
                isSvgIcon: false,
              ),
              SizedBox(height: 12 * scaleFactor),
              // _TileOption(
              //   title: 'Your Plan',
              //   subtitle: 'Premium plan was expired 9 day ago',
              //   icon: AppIcons.crown_color,
              //   onTap: () {},
              // ),
              SizedBox(height: 24 * scaleFactor),
              Text(
                'Other',
                style: AppTextStyle.titleMedium.copyWith(
                  color: AppColors.secondary,
                ),
              ),
              SizedBox(height: 12 * scaleFactor),
              _TileOption(
                title: 'Help Center',
                subtitle: '24/7 Customer service available',
                icon: AppIcons.help_center_icon,
                onTap: () {},
              ),
              SizedBox(height: 12 * scaleFactor),
              _TileOption(
                title: 'Info App',
                subtitle: 'Edit or change theme',
                icon: AppIcons.info_app_icon,
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const InfoAppScreen(),
                  ),
                ),
              ),
              // Only show Log out and Delete Account if user is logged in
              if (_isUserLoggedIn) ...[
                SizedBox(height: 12 * scaleFactor),
                _TileOption(
                  title: 'Log out',
                  subtitle: _userEmail,
                  icon: AppIcons.logout_icon,
                  onTap: () => _showLogoutDialog(context),
                ),
                SizedBox(height: 12 * scaleFactor),
                _TileOption(
                  title: 'Delete Account',
                  subtitle: 'Delete permanently account',
                  icon: AppIcons.Delete_icon,
                  onTap: () => _showDeleteDialog(context),
                ),
              ],
              SizedBox(height: 24 * scaleFactor),
            ],
          ),
        ),
      ),
    );
  }

  void _showLogoutDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      enableDrag: false,

      builder: (BuildContext context) {
        return _LogoutConfirmationDialog();
      },
    );
  }

  void _showDeleteDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      enableDrag: false,
      builder: (BuildContext context) {
        return _DeleteConfirmationDialog();
      },
    );
  }

  void _showChangeNameDialog(BuildContext context) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return _ChangeNameDialog();
      },
    );
  }
}

class _ReminderCard extends StatelessWidget {
  final VoidCallback onUpgradeTap;
  const _ReminderCard({required this.onUpgradeTap});

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600;
    
    return Container(
      width: double.infinity,
      padding: EdgeInsets.all(16 * scaleFactor),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(12 * scaleFactor),
        border: Border(bottom: BorderSide(color: AppColors.secondary)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 40 * scaleFactor,
                    height: 40 * scaleFactor,
                    decoration: BoxDecoration(
                      color: AppColors.secondary.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(16 * scaleFactor),
                    ),
                    alignment: Alignment.center,
                    child: Icon(
                      Icons.notifications_none,
                      color: AppColors.secondary,
                      size: 24 * scaleFactor,
                    ),
                  ),
                  SizedBox(width: 8 * scaleFactor),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Reminder for your Premium Expired',
                        style: AppTextStyle.titleSmall,
                      ),
                      SizedBox(height: 4 * scaleFactor),
                      SizedBox(
                        width: MediaQuery.of(context).size.width * 0.5,
                        child: Text(
                          'Your premium was expired, Renew or upgrade premium  for better experience.',
                          style: AppTextStyle.bodySmall,
                        ),
                      ),
                      SizedBox(height: 12 * scaleFactor),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: SizedBox(
                          height: 40 * scaleFactor,
                          child: ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              elevation: 0,
                              backgroundColor: Color(0xffFFF4DE),
                              foregroundColor: AppColors.secondary,
                              padding: EdgeInsets.symmetric(
                                horizontal: 16 * scaleFactor,
                                vertical: 8 * scaleFactor,
                              ),
                              shape: RoundedRectangleBorder(
                                side: BorderSide(color: AppColors.secondary),
                                borderRadius: BorderRadius.circular(8 * scaleFactor),
                              ),
                            ),
                            onPressed: onUpgradeTap,
                            child: Text(
                              'Upgrade Now',
                              style: AppTextStyle.buttonPrimary.copyWith(
                                color: AppColors.secondary,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              Text(
                '9min ago',
                style: AppTextStyle.bodySmall.copyWith(
                  color: AppColors.primary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TileOption extends StatelessWidget {
  final String title;
  final String subtitle;
  final String icon;
  final VoidCallback onTap;
  final bool isSvgIcon;

  const _TileOption({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.onTap,
    this.isSvgIcon = false,
  });

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600;
    
    return ListTile(
      onTap: onTap,
      contentPadding: EdgeInsets.symmetric(
        horizontal: 24 * scaleFactor,
        vertical: 4 * scaleFactor,
      ),
      minTileHeight: 72 * scaleFactor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12 * scaleFactor),
      ),
      tileColor: AppColors.lightGrey,
      leading: isSvgIcon
          ? SizedBox(
              width: 28 * scaleFactor,
              height: 28 * scaleFactor,
              child: _SvgIcon(path: icon),
            )
          : Image.asset(
              icon,
              width: 24 * scaleFactor,
              height: 28 * scaleFactor,
            ),
      title: Text(title, style: AppTextStyle.headlineSmall),
      subtitle: Text(subtitle),
      trailing: Icon(Icons.chevron_right, size: 24 * scaleFactor),
    );
  }
}

class _SvgIcon extends StatelessWidget {
  final String path;
  const _SvgIcon({required this.path});

  @override
  Widget build(BuildContext context) {
    // Fallback to Image.asset for simplicity if SVG package not used on this widget
    return SvgPicture.asset(path);
  }
}

class _LogoutConfirmationDialog extends StatelessWidget {
  const _LogoutConfirmationDialog();

  Future<void> _performLogout(BuildContext context) async {
    final authService = FirebaseAuthService();
    try {
      await authService.signOut();
      if (context.mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (context) => const LoginIllustraionScreen()),
          (route) => false,
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to logout: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600;
    
    return Container(
      // height: 200,
      width: double.infinity,

      padding: EdgeInsets.all(24 * scaleFactor),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(16 * scaleFactor),
          topRight: Radius.circular(16 * scaleFactor),
        ),
      ),
      child: Column(
        spacing: 24 * scaleFactor,
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // Title
          Text(
            'Log Out ?',
            style: AppTextStyle.headlineMedium.copyWith(
              fontWeight: FontWeight.bold,
              color: AppColors.black,
            ),
          ),
          // SizedBox(height: 16 * scaleFactor),
          // Confirmation message
          Text(
            'Are you sure want to log out?',
            style: AppTextStyle.bodyLarge.copyWith(color: AppColors.secondary),
            textAlign: TextAlign.center,
          ),
          // SizedBox(height: 24 * scaleFactor),
          // Buttons
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Cancel button
              Container(
                height: 48 * scaleFactor,
                width: 120 * scaleFactor,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      Color(0xff002E6C),
                      Color(0xff023170),
                      Color(0xff0145A0),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    // stops: [0.5, 1.0],
                  ),
                  borderRadius: BorderRadius.circular(24 * scaleFactor),
                ),
                child: TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  style: TextButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(24 * scaleFactor),
                    ),
                    padding: EdgeInsets.zero,
                  ),
                  child: Text('Cancel', style: AppTextStyle.buttonPrimary),
                ),
              ),
              SizedBox(width: 12 * scaleFactor),
              // Log out button
              Container(
                height: 48 * scaleFactor,
                width: 120 * scaleFactor,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      AppColors.secondary,
                      AppColors.secondary.withOpacity(0.8),
                    ],
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                  ),
                  borderRadius: BorderRadius.circular(24 * scaleFactor),
                ),
                child: TextButton(
                  onPressed: () async {
                    Navigator.of(context).pop();
                    await _performLogout(context);
                  },
                  style: TextButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(24 * scaleFactor),
                    ),
                    padding: EdgeInsets.zero,
                  ),
                  child: Text('Log out', style: AppTextStyle.buttonPrimary),
                ),
              ),
            ],
          ),
          SizedBox(height: 24 * scaleFactor),
        ],
      ),
    );
  }
}

class _DeleteConfirmationDialog extends StatelessWidget {
  const _DeleteConfirmationDialog();

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600;
    
    return Container(
      // height: 200,
      width: double.infinity,
      padding: EdgeInsets.all(24 * scaleFactor),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(16 * scaleFactor),
          topRight: Radius.circular(16 * scaleFactor),
        ),
      ),
      child: Column(
        spacing: 24 * scaleFactor,
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          // Title
          Text(
            'Delete Account ?',
            style: AppTextStyle.headlineMedium.copyWith(
              fontWeight: FontWeight.bold,
              color: AppColors.black,
            ),
          ),
          // SizedBox(height: 16 * scaleFactor),
          // Confirmation message
          Text(
            'Are you sure want to delete account?',
            style: AppTextStyle.bodyLarge.copyWith(color: AppColors.grey),
            textAlign: TextAlign.center,
          ),
          // SizedBox(height: 24 * scaleFactor),
          // Buttons
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Cancel button
              Container(
                height: 48 * scaleFactor,
                width: 120 * scaleFactor,
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.black),
                  borderRadius: BorderRadius.circular(8 * scaleFactor), // Square corners
                ),
                child: TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  style: TextButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8 * scaleFactor), // Square corners
                    ),
                    padding: EdgeInsets.zero,
                  ),
                  child: Text(
                    'Cancel',
                    style: AppTextStyle.buttonPrimary.copyWith(
                      color: AppColors.black,
                    ),
                  ),
                ),
              ),
              SizedBox(width: 12 * scaleFactor),
              // Delete button
              Container(
                height: 48 * scaleFactor,
                width: 120 * scaleFactor,
                decoration: BoxDecoration(
                  color: AppColors.secondary,
                  borderRadius: BorderRadius.circular(8 * scaleFactor), // Square corners
                ),
                child: TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                    // TODO: Implement actual delete account logic here
                    // For now, just close the dialog
                  },
                  style: TextButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8 * scaleFactor), // Square corners
                    ),
                    padding: EdgeInsets.zero,
                  ),
                  child: Text('Delete', style: AppTextStyle.buttonPrimary),
                ),
              ),
            ],
          ),

          SizedBox(height: 24 * scaleFactor),
        ],
      ),
    );
  }
}

class _ChangeNameDialog extends StatefulWidget {
  const _ChangeNameDialog();

  @override
  State<_ChangeNameDialog> createState() => _ChangeNameDialogState();
}

class _ChangeNameDialogState extends State<_ChangeNameDialog> {
  final TextEditingController _nameController = TextEditingController();
  final FirebaseAuthService _authService = FirebaseAuthService();

  @override
  void initState() {
    super.initState();
    _loadInitialName();
  }

  Future<void> _loadInitialName() async {
    final prefs = await SharedPreferences.getInstance();
    final user = _authService.currentUser;
    
    // Load name: Firebase displayName > Saved name > Email > Default
    if (user != null) {
      _nameController.text = user.displayName ?? 
                            prefs.getString('user_display_name') ?? 
                            user.email?.split('@').first ?? 
                            'User';
    } else {
      _nameController.text = prefs.getString('user_display_name') ?? 'User';
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _saveName(BuildContext context) async {
    final newName = _nameController.text.trim();
    if (newName.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Name cannot be empty'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }

    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Always save to SharedPreferences for local storage
      await prefs.setString('user_display_name', newName);
      
      // If user is logged in, also update Firebase
      final user = _authService.currentUser;
      if (user != null) {
        await user.updateDisplayName(newName);
      }
      
      if (context.mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Display name updated successfully'),
            backgroundColor: Colors.green,
          ),
        );
        // Refresh the profile screen to show new name
        if (context.mounted) {
          (context.findAncestorStateOfType<_ProfileScreenState>())?._loadUserInfo();
        }
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to update name: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final scaleFactor = (screenSize.width + screenSize.height) / 2 / 600;
    
    return Dialog(
      backgroundColor: Colors.transparent,
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        padding: EdgeInsets.all(24 * scaleFactor),
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(16 * scaleFactor),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header with title and close button
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Flexible(
                  child: Text(
                    'Change Profile Name',
                    style: AppTextStyle.headlineMedium.copyWith(
                      fontWeight: FontWeight.bold,
                      fontSize: 20 * scaleFactor,
                      color: AppColors.black,
                    ),
                  ),
                ),
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(),
                  child: Container(
                    width: 32 * scaleFactor,
                    height: 32 * scaleFactor,
                    decoration: BoxDecoration(
                      color: AppColors.lightGrey,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.close,
                      color: AppColors.black,
                      size: 20 * scaleFactor,
                    ),
                  ),
                ),
              ],
            ),
            Divider(color: AppColors.lightGrey),
            // Input field label
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                'Enter Name',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
            SizedBox(height: 8 * scaleFactor),
            // Input field
            TextField(
              controller: _nameController,
              decoration: InputDecoration(
                hintText: 'Enter your name',
                hintStyle: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.grey,
                ),
                filled: true,
                fillColor: AppColors.lightGrey,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8 * scaleFactor),
                  borderSide: BorderSide.none,
                ),
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 16 * scaleFactor,
                  vertical: 12 * scaleFactor,
                ),
              ),
              style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
            ),
            SizedBox(height: 24 * scaleFactor),
            // Buttons
            Row(
              children: [
                // Cancel button
                Expanded(
                  child: Container(
                    height: 48 * scaleFactor,
                    decoration: BoxDecoration(
                      color: AppColors.white,
                      border: Border.all(color: AppColors.grey),
                      borderRadius: BorderRadius.circular(24 * scaleFactor),
                    ),
                    child: TextButton(
                      onPressed: () => Navigator.of(context).pop(),
                      style: TextButton.styleFrom(
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(24 * scaleFactor),
                        ),
                        padding: EdgeInsets.zero,
                      ),
                      child: Text(
                        'Cancel',
                        style: AppTextStyle.buttonPrimary.copyWith(
                          color: AppColors.black,
                        ),
                      ),
                    ),
                  ),
                ),
                SizedBox(width: 12 * scaleFactor),
                // Save button
                Expanded(
                  child: Container(
                    height: 48 * scaleFactor,
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(24 * scaleFactor),
                    ),
                    child: TextButton(
                      onPressed: () => _saveName(context),
                      style: TextButton.styleFrom(
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(24 * scaleFactor),
                        ),
                        padding: EdgeInsets.zero,
                      ),
                      child: Text('Save', style: AppTextStyle.buttonSecondary),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
