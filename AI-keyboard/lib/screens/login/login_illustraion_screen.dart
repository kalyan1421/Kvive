import 'package:ai_keyboard/screens/login/mobile_login_screen.dart';
import 'package:ai_keyboard/screens/login/success_screen.dart';
import 'package:ai_keyboard/screens/keyboard_setup/keyboard_setup_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/orange_button.dart';
import 'package:ai_keyboard/services/firebase_auth_service.dart';
import 'package:flutter/material.dart';

// Tap handler templates for this page
void onTapContinueWithMobile(BuildContext context) {
  Navigator.push(
    context,
    MaterialPageRoute(builder: (context) => MobileLoginScreen()),
  );
}

// This function will be moved inside the StatefulWidget class

void onTapDoItLater(BuildContext context) {
  Navigator.push(
    context,
    MaterialPageRoute(builder: (context) => const KeyboardSetupScreen()),
  );
}

class LoginIllustraionScreen extends StatefulWidget {
  const LoginIllustraionScreen({super.key});

  @override
  State<LoginIllustraionScreen> createState() => _LoginIllustraionScreenState();
}

class _LoginIllustraionScreenState extends State<LoginIllustraionScreen> {
  final FirebaseAuthService _authService = FirebaseAuthService();
  bool _isLoading = false;

  Future<void> _signInWithGoogle() async {
    setState(() {
      _isLoading = true;
    });

    try {
      print('🔵 [LoginIllustraionScreen] Initiating Google Sign-In...');
      final userCredential = await _authService.signInWithGoogle();
      
      if (userCredential == null) {
        // User cancelled the sign-in
        print('🟡 [LoginIllustraionScreen] User cancelled Google Sign-In');
        return;
      }
      
      print('🟢 [LoginIllustraionScreen] Google Sign-In successful');
      if (mounted) {
        final isNewUser = userCredential.additionalUserInfo?.isNewUser ?? false;
        
        // Navigate to success screen
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (context) => const SuccessScreen()),
        );
        
        // Show success message
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(isNewUser 
              ? 'Welcome ${userCredential.user?.displayName ?? 'User'}! Account created successfully.'
              : 'Welcome back ${userCredential.user?.displayName ?? 'User'}!'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      print('🔴 [LoginIllustraionScreen] Google Sign-In error: $e');
      if (mounted) {
        String errorMessage = e.toString();
        // Clean up error message for better user experience
        if (errorMessage.startsWith('Exception: ')) {
          errorMessage = errorMessage.substring(11);
        }
        
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      body: SafeArea(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Column(
            children: [
              Spacer(flex: 3),
              Image.asset(AppAssets.loginIllustration),
              Spacer(),

              Text(
                'Log in to Kivive',
                style: AppTextStyle.bodyMedium.copyWith(
                  color: AppColors.black,
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
                ),
              ),
              SizedBox(height: 16),
              
              SizedBox(height: 16),
              GestureDetector(
                onTap: _isLoading ? null : _signInWithGoogle,
                child: Container(
                  width: double.infinity,
                  height: 50,
                  decoration: BoxDecoration(
                    color: _isLoading ? Colors.grey.shade300 : Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(100),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (_isLoading)
                        SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: AlwaysStoppedAnimation<Color>(AppColors.black),
                          ),
                        )
                      else
                        Image.asset(AppIcons.google, width: 24, height: 24),
                      SizedBox(width: 10),
                      Text(
                        _isLoading ? 'Signing in...' : 'Continue with Google',
                        style: AppTextStyle.buttonPrimary.copyWith(
                          color: AppColors.black,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SizedBox(height: 32),
              GestureDetector(
                onTap: () => onTapDoItLater(context),
                child: Text(
                  'I will do it later',
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.black,
                  ),
                ),
              ),
              Spacer(flex: 1),
            ],
          ),
        ),
      ),
    );
  }
}
