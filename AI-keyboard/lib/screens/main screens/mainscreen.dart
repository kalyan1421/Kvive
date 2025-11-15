import 'package:ai_keyboard/screens/main%20screens/chat_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/home_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/profile_screen.dart';
import 'package:ai_keyboard/screens/main%20screens/setting_screen.dart';
import 'package:ai_keyboard/theme/theme_editor_v2.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/rate_app_modal.dart';
import 'package:ai_keyboard/screens/main%20screens/notification_screen.dart';
import 'package:ai_keyboard/screens/keyboard_setup/keyboard_setup_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/svg.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:async';
import 'package:ai_keyboard/services/firebase_auth_service.dart';
import 'package:ai_keyboard/services/notification_service.dart';
import 'package:flutter/services.dart';

class mainscreen extends StatefulWidget {
  final int initialIndex;
  
  const mainscreen({super.key, this.initialIndex = 0});


  @override
  State<mainscreen> createState() => _mainscreenState();
}

class _mainscreenState extends State<mainscreen> with TickerProviderStateMixin {
  static const platform = MethodChannel('ai_keyboard/config');
  int selectedIndex = 0;
  AnimationController? _fabAnimationController;
  Animation<double>? _fabAnimation;
  Timer? _animationTimer;
  bool _isExtended = false;
  bool _hasShownRateModal = false;
  int _unreadNotificationCount = 0;
  final FirebaseAuthService _authService = FirebaseAuthService();
  String _userName = 'User';
  bool _isKeyboardEnabled = false;
  bool _isKeyboardActive = false;
  Timer? _keyboardCheckTimer;
  StreamSubscription? _notificationStreamSubscription;

  final List<Widget> _pages = [
    const HomeScreen(),
    // const ThemeScreen(),
    ThemeGalleryScreen(),
    // const SettingScreen(),
    SettingScreen(),
    const ProfileScreen(),
  ];

  @override
  void initState() {
    super.initState();
    selectedIndex = widget.initialIndex;
    _loadUserInfo();
    _checkKeyboardStatus();
    _startKeyboardStatusChecking();
    _fabAnimationController = AnimationController(
      duration: const Duration(milliseconds: 500),
      vsync: this,
    );
    _fabAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(
        parent: _fabAnimationController!,
        curve: Curves.easeInOut,
      ),
    );

    _startAnimationTimer();
    _checkAndShowRateModal();
    _checkUnreadNotifications();
    _listenToNotifications();
  }

  Future<void> _loadUserInfo() async {
    final prefs = await SharedPreferences.getInstance();
    final user = _authService.currentUser;
    
    if (user != null) {
      // If logged in, prioritize Firebase displayName, then saved name, then email
      setState(() {
        _userName = user.displayName ?? 
                    prefs.getString('user_display_name') ?? 
                    user.email?.split('@').first ?? 
                    'User';
      });
    } else {
      // If not logged in, use saved name from SharedPreferences
      final savedName = prefs.getString('user_display_name');
      setState(() {
        _userName = savedName ?? 'Hello User';
      });
    }
  }

  @override
  void dispose() {
    _fabAnimationController?.dispose();
    _animationTimer?.cancel();
    _keyboardCheckTimer?.cancel();
    _notificationStreamSubscription?.cancel();
    super.dispose();
  }

  /// Check unread notification count
  Future<void> _checkUnreadNotifications() async {
    try {
      final count = await NotificationService.getUnreadCount();
      if (mounted) {
        setState(() {
          _unreadNotificationCount = count;
        });
      }
    } catch (e) {
      debugPrint('Error checking unread notifications: $e');
    }
  }

  /// Listen to notification changes in real-time
  void _listenToNotifications() {
    try {
      _notificationStreamSubscription = NotificationService
          .getNotificationsStreamForDevice()
          .listen((snapshot) {
        // Count unread notifications
        final unreadCount = snapshot.docs
            .where((doc) => (doc.data()['isRead'] ?? false) == false)
            .length;
        
        if (mounted) {
          setState(() {
            _unreadNotificationCount = unreadCount;
          });
        }
      });
    } catch (e) {
      debugPrint('Error listening to notifications: $e');
      // Fallback to periodic check if stream fails
      Timer.periodic(const Duration(seconds: 30), (timer) {
        if (mounted) {
          _checkUnreadNotifications();
        } else {
          timer.cancel();
        }
      });
    }
  }

  Future<void> _checkKeyboardStatus() async {
    try {
      final enabled = await platform.invokeMethod<bool>('isKeyboardEnabled') ?? false;
      final active = await platform.invokeMethod<bool>('isKeyboardActive') ?? false;
      
      if (mounted) {
        setState(() {
          _isKeyboardEnabled = enabled;
          _isKeyboardActive = active;
        });
      }
    } catch (e) {
      print('Error checking keyboard status: $e');
    }
  }

  void _startKeyboardStatusChecking() {
    // Check keyboard status periodically
    _keyboardCheckTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      _checkKeyboardStatus();
    });
  }

  Future<void> _openKeyboardSettings() async {
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const KeyboardSetupScreen()),
    );
    if (mounted) {
      _checkKeyboardStatus();
    }
  }

  void _startAnimationTimer() {
    _animationTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
      if (_isExtended) {
        _fabAnimationController?.reverse();
        _isExtended = false;
      } else {
        _fabAnimationController?.forward();
        _isExtended = true;
      }
    });
  }

  Future<void> _checkAndShowRateModal() async {
    if (_hasShownRateModal) return;

    final prefs = await SharedPreferences.getInstance();
    final hasShownRateModal = prefs.getBool('has_shown_rate_modal') ?? false;
    final launchCount = (prefs.getInt('app_launch_count') ?? 0) + 1;
    await prefs.setInt('app_launch_count', launchCount);

    if (!hasShownRateModal && launchCount >= 3) {
      // Wait for the screen to be fully loaded
      await Future.delayed(const Duration(seconds: 2));

      if (mounted) {
        _showRateModal();
        await prefs.setBool('has_shown_rate_modal', true);
        _hasShownRateModal = true;
      }
    } else {
      _hasShownRateModal = true;
    }
  }

  void _showRateModal() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext context) {
        return const RateAppModal();
      },
    );
  }

  Future<void> _showNotification() async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => const NotificationScreen()),
    );
    // Refresh unread count when returning from notification screen
    _checkUnreadNotifications();
  }

  @override
  Widget build(BuildContext context) {
    final mediaSize = MediaQuery.of(context).size;
    final shortestSide = mediaSize.shortestSide;
    final double scaleFactor = (shortestSide / 375).clamp(0.85, 1.25).toDouble();
    final appBarIconSize = (32 * scaleFactor).clamp(24.0, 36.0);
    final navHeight =
        (mediaSize.height * 0.14).clamp(78.0, mediaSize.height * 0.18).toDouble();
    final navRadius = (20 * scaleFactor).clamp(16.0, 26.0);
    final navIconSize = (24 * scaleFactor).clamp(20.0, 32.0);
    final warningPadding = (16 * scaleFactor).clamp(12.0, 20.0);
    final warningVerticalPadding = (12 * scaleFactor).clamp(8.0, 16.0);
    final fabHeight = (56 * scaleFactor).clamp(48.0, 76.0);
    final fabWidthDelta = (120 * scaleFactor).clamp(90.0, 160.0);
    final fabTextWidth = (100 * scaleFactor).clamp(80.0, 140.0);

    return Scaffold(
      appBar: AppBar(
        surfaceTintColor: selectedIndex == 0
            ? AppColors.white
            : AppColors.primary,

        backgroundColor: selectedIndex == 0
            ? AppColors.white
            : AppColors.primary,
        leading: Padding(
          padding: const EdgeInsets.only(left: 16),
          child: Image.asset(AppAssets.userIcon),
        ),
        title: Text(
          _userName,
          style: AppTextStyle.headlineLarge.copyWith(
            color: selectedIndex == 0 ? AppColors.black : AppColors.white,
          ),
        ),
        actions: [
          IconButton(
            onPressed: () {
              _showNotification();
            },
            icon: Stack(
              children: [
                Icon(
                  _unreadNotificationCount > 0
                      ? Icons.notifications
                      : Icons.notifications_outlined,
                  size: appBarIconSize,
                  color: selectedIndex == 0 ? AppColors.black : AppColors.white,
                ),
                if (_unreadNotificationCount > 0)
                  Positioned(
                    top: 0,
                    right: 0,
                    child: Container(
                      width: 16,
                      height: 16,
                      decoration: BoxDecoration(
                        color: AppColors.secondary,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: selectedIndex == 0 ? AppColors.white : AppColors.primary,
                          width: 2,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          SizedBox(width: 16),
        ],
      ),
      body: Column(
        children: [
          // Keyboard not enabled warning banner
          if (!_isKeyboardEnabled || !_isKeyboardActive)
            Material(
              color: const Color(0xFFFF4444),
              child: InkWell(
                onTap: _openKeyboardSettings,
                child: Container(
                  width: double.infinity,
                  padding: EdgeInsets.symmetric(
                    vertical: warningVerticalPadding,
                    horizontal: warningPadding,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.warning_rounded,
                        color: Colors.white,
                        size: (20 * scaleFactor).clamp(16.0, 26.0),
                      ),
                      SizedBox(width: (12 * scaleFactor).clamp(8.0, 16.0)),
                      Expanded(
                        child: Text(
                          'Keyboard not selected. Click here to Enable',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: (14 * scaleFactor).clamp(12.0, 18.0),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      Icon(
                        Icons.arrow_forward_ios,
                        color: Colors.white,
                        size: (16 * scaleFactor).clamp(12.0, 20.0),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          // Main page content
          Expanded(
            child: _pages[selectedIndex],
          ),
        ],
      ),
      floatingActionButton: _fabAnimation != null
          ? AnimatedBuilder(
              animation: _fabAnimation!,
              builder: (context, child) {
                return Container(
                  height: fabHeight,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 500),
                        curve: Curves.easeInOut,
                        width: fabHeight + (_fabAnimation!.value * fabWidthDelta),
                        height: fabHeight,
                        decoration: BoxDecoration(
                          color: AppColors.secondary,
                          borderRadius: BorderRadius.circular(12),
                          boxShadow: [
                            BoxShadow(
                              color: AppColors.secondary.withOpacity(0.3),
                              spreadRadius: 2,
                              blurRadius: 8,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Material(
                          color: Colors.transparent,
                          child: InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (context) => const ChatScreen(),
                                ),
                              );
                            },
                            child: Stack(
                              children: [
                                // Icon positioned at the right side (stays in place)
                                Positioned(
                                  right: 16,
                                  top: 0,
                                  bottom: 0,
                                  child: const Icon(
                                    Icons.keyboard,
                                    size: 24,
                                    color: AppColors.white,
                                  ),
                                ),
                                // Text that extends from the left
                                Positioned(
                                  left: 16,
                                  top: 0,
                                  bottom: 0,
                                  child: AnimatedOpacity(
                                    opacity: _fabAnimation!.value,
                                    duration: const Duration(milliseconds: 300),
                                    child: AnimatedContainer(
                                      duration: const Duration(
                                        milliseconds: 500,
                                      ),
                                      curve: Curves.easeInOut,
                                      width: _fabAnimation!.value * fabTextWidth,
                                      child: const Center(
                                        child: Text(
                                          'Try Keyboard',
                                          style: TextStyle(
                                            color: AppColors.white,
                                            fontSize: 14,
                                            fontWeight: FontWeight.w500,
                                          ),
                                          overflow: TextOverflow.ellipsis,
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
                    ],
                  ),
                );
              },
            )
          : FloatingActionButton(
              foregroundColor: AppColors.white,
              backgroundColor: AppColors.secondary,
              onPressed: () {
                // TODO: Add keyboard functionality
              },
              child: const Icon(Icons.keyboard, size: 32),
            ),
      bottomNavigationBar: 
      
        
      Container(
        height: navHeight,
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(navRadius),
            topRight: Radius.circular(navRadius),
          ),
          boxShadow: [
            BoxShadow(
              color: AppColors.grey.withOpacity(0.1),
              spreadRadius: 1,
              blurRadius: 10,
            ),
            BoxShadow(
              color: AppColors.grey.withOpacity(0.1),
              spreadRadius: 1,
              blurRadius: 10,
            ),
          ],
        ),
        child: BottomNavigationBar(
          elevation: 0,
          backgroundColor: Colors.transparent,
          selectedItemColor: AppColors.secondary,
          unselectedItemColor: AppColors.grey,
          type: BottomNavigationBarType.fixed,
          currentIndex: selectedIndex,
          onTap: (index) {
            setState(() => this.selectedIndex = index);
            // Refresh user info when switching to profile tab or returning to home
            if (index == 3 || index == 0) {
              _loadUserInfo();
            }
          },
          items: [
            BottomNavigationBarItem(
              icon: SvgPicture.asset(
                AppIcons.home,
                width: navIconSize,
                height: navIconSize,
                colorFilter: ColorFilter.mode(
                  selectedIndex == 0 ? AppColors.secondary : AppColors.grey,
                  BlendMode.srcIn,
                ),
              ),
              label: 'Home',
            ),
            BottomNavigationBarItem(
              icon: SvgPicture.asset(
                AppIcons.theme,
                width: navIconSize,
                height: navIconSize,
                colorFilter: ColorFilter.mode(
                  selectedIndex == 1 ? AppColors.secondary : AppColors.grey,
                  BlendMode.srcIn,
                ),
              ),
              label: 'Theme',
            ),
            BottomNavigationBarItem(
              icon: SvgPicture.asset(
                AppIcons.settings,
                width: navIconSize,
                height: navIconSize,
                colorFilter: ColorFilter.mode(
                  selectedIndex == 2 ? AppColors.secondary : AppColors.grey,
                  BlendMode.srcIn,
                ),
              ),
              label: 'Settings',
            ),
            BottomNavigationBarItem(
              icon: SvgPicture.asset(
                AppIcons.profile,
                width: navIconSize,
                height: navIconSize,
                colorFilter: ColorFilter.mode(
                  selectedIndex == 3 ? AppColors.secondary : AppColors.grey,
                  BlendMode.srcIn,
                ),
              ),
              label: 'Profile',
            ),
          ],
        ),
      ),
    
    );
  }
}
