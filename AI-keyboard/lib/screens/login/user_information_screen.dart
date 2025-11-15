import 'package:ai_keyboard/screens/login/success_screen.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/back_button.dart';
import 'package:ai_keyboard/widgets/orange_button.dart';
import 'package:flutter/material.dart';

class UserInformationScreen extends StatefulWidget {
  const UserInformationScreen({super.key});

  @override
  State<UserInformationScreen> createState() => _UserInformationScreenState();
}

class _UserInformationScreenState extends State<UserInformationScreen> {
  final TextEditingController _nameController = TextEditingController();
  bool _isNameValid = false;
  String? _errorText;

  // Template onTap handlers for this page
  void onTapSubmit(BuildContext context) {
    if (_isNameValid) {
      // Navigate to success screen
      Navigator.push(
        context,
        MaterialPageRoute(builder: (context) => SuccessScreen()),
      );
    } else {
      setState(() {
        _errorText = 'Please enter your name';
      });
    }
  }

  void onNameChanged(String value) {
    setState(() {
      _errorText = null;
      _isNameValid = value.trim().isNotEmpty;
    });
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
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
              SizedBox(height: 48),
              _buildNameInput(),
              SizedBox(height: 24),
              _buildSubmitButton(),
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
          'Fill your information',
          style: AppTextStyle.headlineLarge.copyWith(
            fontWeight: FontWeight.w600,
            color: AppColors.secondary,
            fontSize: 24,
          ),
        ),
        Text('Enter your correct information '),
      ],
    );
  }

  Widget _buildNameInput() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          height: 50,
          decoration: BoxDecoration(
            color: Colors.grey[100],
            borderRadius: BorderRadius.circular(100),
            border: Border.all(
              color: _errorText != null ? Colors.red : Colors.grey[300]!,
              width: 1,
            ),
          ),
          child: TextField(
            controller: _nameController,
            onChanged: onNameChanged,
            decoration: InputDecoration(
              hintText: 'Enter Your Name',
              hintStyle: TextStyle(color: Colors.grey[400], fontSize: 16),
              border: InputBorder.none,
              contentPadding: EdgeInsets.symmetric(horizontal: 20),
            ),
            style: TextStyle(fontSize: 16, color: Colors.grey[800]),
          ),
        ),
        if (_errorText != null)
          Padding(
            padding: const EdgeInsets.only(top: 8, left: 4),
            child: Text(
              _errorText!,
              style: const TextStyle(color: Colors.red, fontSize: 12),
            ),
          ),
      ],
    );
  }

  Widget _buildSubmitButton() {
    return OrangeButton(
      text: 'Submit',
      onTap: _isNameValid ? () => onTapSubmit(context) : null,
    );
  }
}
