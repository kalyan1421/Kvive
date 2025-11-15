import 'package:ai_keyboard/screens/login/user_information_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/back_button.dart';
import 'package:ai_keyboard/widgets/orange_button.dart';
import 'package:ai_keyboard/widgets/otp_input.dart';
import 'package:flutter/material.dart';

class OTPVerificationScreen extends StatefulWidget {
  final String phoneNumber;
  final String countryCode;

  const OTPVerificationScreen({
    super.key,
    required this.phoneNumber,
    required this.countryCode,
  });

  @override
  State<OTPVerificationScreen> createState() => _OTPVerificationScreenState();
}

class _OTPVerificationScreenState extends State<OTPVerificationScreen> {
  bool _isOTPValid = false;
  bool _isResendEnabled = true;
  int _resendCountdown = 0;

  // Template onTap handlers for this page
  void onTapSubmit(BuildContext context) {
    if (_isOTPValid) {
      // Navigate to user information screen
      Navigator.push(
        context,
        MaterialPageRoute(builder: (context) => UserInformationScreen()),
      );
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Please enter a valid 6-digit OTP'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  void onTapResend(BuildContext context) {
    if (_isResendEnabled) {
      // TODO: Implement resend OTP logic
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'OTP resent to ${widget.countryCode} ${widget.phoneNumber}',
          ),
          backgroundColor: Colors.blue,
        ),
      );

      // Start countdown
      _startResendCountdown();
    }
  }

  void _startResendCountdown() {
    setState(() {
      _isResendEnabled = false;
      _resendCountdown = 30; // 30 seconds countdown
    });

    Future.doWhile(() async {
      await Future.delayed(Duration(seconds: 1));
      if (mounted) {
        setState(() {
          _resendCountdown--;
        });
        return _resendCountdown > 0;
      }
      return false;
    }).then((_) {
      if (mounted) {
        setState(() {
          _isResendEnabled = true;
          _resendCountdown = 0;
        });
      }
    });
  }

  void onOTPChanged(String otp) {
    setState(() {
      _isOTPValid = otp.length == 6;
    });
  }

  void onOTPCompleted(String otp) {
    setState(() {
      _isOTPValid = true;
    });
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
              _buildOTPInput(),
              SizedBox(height: 24),
              _buildSubmitButton(),
              SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    'Didn’t receive a code? ',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.black,
                      fontSize: 16,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                  _buildResendSection(),
                ],
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
          'OTP Verification',
          style: AppTextStyle.headlineLarge.copyWith(
            fontWeight: FontWeight.w600,
            color: AppColors.secondary,
            fontSize: 24,
          ),
        ),
        SizedBox(height: 8),
        Text(
          'Enter the verification code we just sent to your mobile number (${widget.phoneNumber}',
          style: AppTextStyle.bodyMedium.copyWith(
            fontWeight: FontWeight.normal,
            color: AppColors.black,
            fontSize: 16,
          ),
        ),
      ],
    );
  }

  Widget _buildOTPInput() {
    return Center(
      child: OTPInput(
        length: 6,
        onChanged: onOTPChanged,
        onCompleted: onOTPCompleted,
      ),
    );
  }

  Widget _buildSubmitButton() {
    return OrangeButton(
      text: 'Submit',
      onTap: _isOTPValid ? () => onTapSubmit(context) : null,
    );
  }

  Widget _buildResendSection() {
    return Center(
      child: GestureDetector(
        onTap: _isResendEnabled ? () => onTapResend(context) : null,
        child: Text(
          _isResendEnabled ? 'Resend' : 'Resend in ${_resendCountdown}s',
          style: AppTextStyle.bodyMedium.copyWith(
            color: _isResendEnabled ? AppColors.secondary : Colors.grey[400],
            fontSize: 16,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
    );
  }
}
