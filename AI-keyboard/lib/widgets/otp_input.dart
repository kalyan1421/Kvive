import 'package:ai_keyboard/utils/appassets.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class OTPInput extends StatefulWidget {
  final int length;
  final Function(String)? onChanged;
  final Function(String)? onCompleted;
  final bool enabled;
  final String? errorText;

  const OTPInput({
    super.key,
    this.length = 6,
    this.onChanged,
    this.onCompleted,
    this.enabled = true,
    this.errorText,
  });

  @override
  State<OTPInput> createState() => _OTPInputState();
}

class _OTPInputState extends State<OTPInput> {
  late List<TextEditingController> _controllers;
  late List<FocusNode> _focusNodes;
  late List<String> _otpValues;

  @override
  void initState() {
    super.initState();
    _controllers = List.generate(
      widget.length,
      (index) => TextEditingController(),
    );
    _focusNodes = List.generate(widget.length, (index) => FocusNode());
    _otpValues = List.filled(widget.length, '');
  }

  @override
  void dispose() {
    for (var controller in _controllers) {
      controller.dispose();
    }
    for (var focusNode in _focusNodes) {
      focusNode.dispose();
    }
    super.dispose();
  }

  void _onTextChanged(String value, int index) {
    if (value.length > 1) {
      // Handle paste or multiple characters
      _handlePaste(value, index);
      return;
    }

    setState(() {
      _otpValues[index] = value;
    });

    // Move to next field if current field is filled
    if (value.isNotEmpty && index < widget.length - 1) {
      _focusNodes[index + 1].requestFocus();
    }

    // Move to previous field if current field is empty and backspace was pressed
    if (value.isEmpty && index > 0) {
      _focusNodes[index - 1].requestFocus();
    }

    _notifyChange();
  }

  void _handlePaste(String value, int startIndex) {
    final digits = value.replaceAll(RegExp(r'[^\d]'), '');
    final remainingLength = widget.length - startIndex;
    final digitsToUse = digits.substring(
      0,
      digits.length > remainingLength ? remainingLength : digits.length,
    );

    for (int i = 0; i < digitsToUse.length; i++) {
      final index = startIndex + i;
      if (index < widget.length) {
        _controllers[index].text = digitsToUse[i];
        _otpValues[index] = digitsToUse[i];
      }
    }

    // Focus the last filled field or the next empty field
    final lastFilledIndex = startIndex + digitsToUse.length - 1;
    final nextEmptyIndex = lastFilledIndex + 1;

    if (nextEmptyIndex < widget.length) {
      _focusNodes[nextEmptyIndex].requestFocus();
    } else {
      _focusNodes[lastFilledIndex].unfocus();
    }

    _notifyChange();
  }

  void _notifyChange() {
    final otp = _otpValues.join('');
    widget.onChanged?.call(otp);

    if (otp.length == widget.length) {
      widget.onCompleted?.call(otp);
    }
  }

  void clear() {
    setState(() {
      for (int i = 0; i < widget.length; i++) {
        _controllers[i].clear();
        _otpValues[i] = '';
      }
    });
    _focusNodes[0].requestFocus();
  }

  String get otp => _otpValues.join('');

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: List.generate(
            widget.length,
            (index) => _buildOTPField(index),
          ),
        ),
        if (widget.errorText != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(
              widget.errorText!,
              style: const TextStyle(color: Colors.red, fontSize: 12),
            ),
          ),
      ],
    );
  }

  Widget _buildOTPField(int index) {
    final hasValue = _otpValues[index].isNotEmpty;
    final isFocused = _focusNodes[index].hasFocus;

    return Container(
      width: 50,
      height: 50,
      decoration: BoxDecoration(
        color: hasValue ? AppColors.secondary : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: isFocused || hasValue
              ? AppColors.secondary
              : Colors.grey[300]!,
          width: 2,
        ),
      ),
      child: TextField(
        controller: _controllers[index],
        focusNode: _focusNodes[index],
        enabled: widget.enabled,
        textAlign: TextAlign.center,
        keyboardType: TextInputType.number,
        inputFormatters: [
          FilteringTextInputFormatter.digitsOnly,
          LengthLimitingTextInputFormatter(1),
        ],
        onChanged: (value) => _onTextChanged(value, index),
        style: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: hasValue ? Colors.white : Colors.black,
        ),
        decoration: const InputDecoration(
          border: InputBorder.none,
          counterText: '',
        ),
      ),
    );
  }
}
