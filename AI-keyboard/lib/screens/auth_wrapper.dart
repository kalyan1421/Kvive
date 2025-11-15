import 'dart:async';
import 'package:flutter/material.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';
import '../services/firebase_auth_service.dart';
import '../services/keyboard_cloud_sync.dart';
import 'package:ai_keyboard/screens/onboarding/onboarding_view.dart';
import 'package:ai_keyboard/screens/main screens/mainscreen.dart';
import 'package:ai_keyboard/screens/keyboard_setup/keyboard_setup_screen.dart';


class AuthWrapper extends StatefulWidget {
  const AuthWrapper({super.key});

  @override
  State<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends State<AuthWrapper> {
  static const platform = MethodChannel('ai_keyboard/config');
  final FirebaseAuthService _authService = FirebaseAuthService();
  bool? _isFirstLaunch;
  bool _cloudSyncStarted = false;
  StreamSubscription<User?>? _authSub;
  bool? _isKeyboardSetup;

  @override
  void initState() {
    super.initState();
    _checkFirstLaunch();
    _listenToAuthChanges();
    _checkKeyboardStatus();
  }
  
  /// Listen to auth changes to start/stop cloud sync
  /// Fixed: Prevent race condition by ensuring sync starts only once per session
  void _listenToAuthChanges() {
    _authSub?.cancel();
    _authSub = FirebaseAuth.instance.authStateChanges().listen((user) async {
      if (user == null) {
        // User logged out - stop sync
        if (_cloudSyncStarted) {
          print('🔵 [AuthWrapper] User logged out, stopping cloud sync...');
          _cloudSyncStarted = false;
          await KeyboardCloudSync.stop();
          if (mounted) setState(() {});
          print('✅ [AuthWrapper] Cloud sync stopped');
        }
        return;
      }
      
      // User logged in - start sync only once
      if (!_cloudSyncStarted) {
        print('🔵 [AuthWrapper] User logged in (${user.email}), starting cloud sync...');
        _cloudSyncStarted = true;
        await KeyboardCloudSync.start();
        await KeyboardCloudSync.initializeDefaultSettings();
        if (mounted) setState(() {});
        print('✅ [AuthWrapper] Cloud sync started successfully');
      }
    });
  }
  
  @override
  void dispose() {
    // Clean up auth subscription
    _authSub?.cancel();
    // Stop sync when widget is disposed
    if (_cloudSyncStarted) {
      KeyboardCloudSync.stop();
    }
    super.dispose();
  }

  Future<void> _checkFirstLaunch() async {    
    print('🔵 [AuthWrapper] Checking if this is first app launch...');
    
    final prefs = await SharedPreferences.getInstance();
    final isFirstLaunch = prefs.getBool('is_first_launch') ?? true;
    
    print('🔵 [AuthWrapper] First launch: $isFirstLaunch');
    
    setState(() {
      _isFirstLaunch = isFirstLaunch;
    });

    // Mark that the app has been launched
    if (isFirstLaunch) {
      await prefs.setBool('is_first_launch', false);
      print('🔵 [AuthWrapper] Marked first launch as complete');
    }
  }

  Future<void> _checkKeyboardStatus() async {
    try {
      print('🔵 [AuthWrapper] Checking keyboard setup status...');
      final enabled = await platform.invokeMethod<bool>('isKeyboardEnabled') ?? false;
      
      // Only check if keyboard is ADDED to system (enabled in settings)
      // Not checking if it's the active input method
      final isSetup = enabled;
      print('🔵 [AuthWrapper] Keyboard - Added to system: $enabled, Setup complete: $isSetup');
      
      if (mounted) {
        setState(() {
          _isKeyboardSetup = isSetup;
        });
      }
    } catch (e) {
      print('🔴 [AuthWrapper] Error checking keyboard status: $e');
      // On error, assume not setup to be safe
      if (mounted) {
        setState(() {
          _isKeyboardSetup = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // Show loading while checking first launch status
    if (_isFirstLaunch == null) {
      return const Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Loading...'),
            ],
          ),
        ),
      );
    }

    // If it's first launch, show onboarding screen first
    if (_isFirstLaunch!) {
      print('🔵 [AuthWrapper] Showing onboarding screen for first launch');
      return const OnboardingView();
    }

    if (_isKeyboardSetup == null) {
      return const Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Checking keyboard setup...'),
            ],
          ),
        ),
      );
    }

    if (!_isKeyboardSetup!) {
      return const KeyboardSetupScreen();
    }

    // For subsequent launches, check authentication status
    return StreamBuilder<User?>(
      stream: _authService.authStateChanges,
      builder: (context, snapshot) {
        print('🔵 [AuthWrapper] Auth state changed - User: ${snapshot.data?.email ?? 'Not signed in'}');

        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 16),
                  Text('Checking authentication...'),
                ],
              ),
            ),
          );
        }

        // Always show main screen, regardless of authentication status
        // Users can access the app without logging in
        // Login will be prompted when they try to use premium features
        print(snapshot.hasData && snapshot.data != null 
          ? '🟢 [AuthWrapper] User authenticated, showing home screen'
          : '🟡 [AuthWrapper] User not authenticated, showing home screen (guest mode)');
        return const mainscreen();
      },
    );
  }
}
