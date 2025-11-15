import 'package:ai_keyboard/screens/login/otp_verification_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/back_button.dart';
import 'package:ai_keyboard/widgets/orange_button.dart';
import 'package:ai_keyboard/widgets/phone_number_input.dart';
import 'package:flutter/material.dart';

class MobileLoginScreen extends StatefulWidget {
  const MobileLoginScreen({super.key});

  @override
  State<MobileLoginScreen> createState() => _MobileLoginScreenState();
}

class _MobileLoginScreenState extends State<MobileLoginScreen> {
  String _selectedCountryCode = '+91';
  String _phoneNumber = '';
  bool _isPhoneValid = false;

  // Template onTap handlers for this page
  void onTapSendOTP(BuildContext context) {
    if (_isPhoneValid) {
      // Navigate to OTP verification screen
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (context) => OTPVerificationScreen(
            phoneNumber: _phoneNumber,
            countryCode: _selectedCountryCode,
          ),
        ),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Please enter a valid phone number'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  void onPhoneChanged(String countryCode, String phoneNumber) {
    setState(() {
      _selectedCountryCode = countryCode;
      _phoneNumber = phoneNumber;
    });
  }

  void onPhoneValidated(String countryCode, String phoneNumber) {
    setState(() {
      _isPhoneValid = true;
    });
  }

  void onTapContinueWithGoogle(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Coming soon'), backgroundColor: Colors.blue),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      body: SafeArea(
        child: Padding(
          padding: EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(height: 24),
              AppBackButton(),
              Spacer(),
              _buildHeading(),
              SizedBox(height: 32),
              _buildPhoneInput(),
              SizedBox(height: 24),
              _buildSendOTPButton(),
              SizedBox(height: 24),
              Center(
                child: Text(
                  'or sign in with',
                  style: AppTextStyle.bodyMedium.copyWith(
                    fontWeight: FontWeight.normal,
                    color: AppColors.black,
                    fontSize: 16,
                  ),
                ),
              ),
              SizedBox(height: 24),
              GestureDetector(
                onTap: () => onTapContinueWithGoogle(context),
                child: Container(
                  width: double.infinity,
                  height: 50,
                  decoration: BoxDecoration(
                    color: Colors.grey.shade200,
                    borderRadius: BorderRadius.circular(100),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Image.asset(AppIcons.google, width: 24, height: 24),
                      SizedBox(width: 10),
                      Text(
                        'Continue with Google',
                        style: AppTextStyle.buttonPrimary.copyWith(
                          color: AppColors.black,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Spacer(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeading() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Log in with Mobile Number',
          style: AppTextStyle.headlineLarge.copyWith(
            fontWeight: FontWeight.w600,
            color: AppColors.secondary,
            fontSize: 24,
          ),
        ),
        SizedBox(height: 8),
        Text(
          'Enter your mobile number',
          style: AppTextStyle.bodyMedium.copyWith(
            fontWeight: FontWeight.normal,
            color: AppColors.black,
            fontSize: 16,
          ),
        ),
      ],
    );
  }

  Widget _buildPhoneInput() {
    return PhoneNumberInput(
      initialCountryCode: _selectedCountryCode,
      onChanged: onPhoneChanged,
      onValidated: onPhoneValidated,
    );
  }

  Widget _buildSendOTPButton() {
    return OrangeButton(
      text: 'Send OTP',
      icon: Icons.message_outlined,
      onTap: _isPhoneValid ? () => onTapSendOTP(context) : null,
    );
  }
}
