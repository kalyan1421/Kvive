import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class CountryCode {
  final String code;
  final String name;
  final String flag;

  CountryCode({required this.code, required this.name, required this.flag});
}

class PhoneNumberInput extends StatefulWidget {
  final String? initialCountryCode;
  final String? initialPhoneNumber;
  final Function(String countryCode, String phoneNumber)? onChanged;
  final Function(String countryCode, String phoneNumber)? onValidated;
  final String? errorText;
  final bool enabled;

  const PhoneNumberInput({
    super.key,
    this.initialCountryCode,
    this.initialPhoneNumber,
    this.onChanged,
    this.onValidated,
    this.errorText,
    this.enabled = true,
  });

  @override
  State<PhoneNumberInput> createState() => _PhoneNumberInputState();
}

class _PhoneNumberInputState extends State<PhoneNumberInput> {
  late TextEditingController _phoneController;
  late String _selectedCountryCode;
  String? _errorText;

  // Common country codes
  final List<CountryCode> _countryCodes = [
    CountryCode(code: '+91', name: 'India', flag: '🇮🇳'),
    CountryCode(code: '+1', name: 'United States', flag: '🇺🇸'),
    CountryCode(code: '+44', name: 'United Kingdom', flag: '🇬🇧'),
    CountryCode(code: '+86', name: 'China', flag: '🇨🇳'),
    CountryCode(code: '+81', name: 'Japan', flag: '🇯🇵'),
    CountryCode(code: '+49', name: 'Germany', flag: '🇩🇪'),
    CountryCode(code: '+33', name: 'France', flag: '🇫🇷'),
    CountryCode(code: '+61', name: 'Australia', flag: '🇦🇺'),
    CountryCode(code: '+55', name: 'Brazil', flag: '🇧🇷'),
    CountryCode(code: '+7', name: 'Russia', flag: '🇷🇺'),
    CountryCode(code: '+82', name: 'South Korea', flag: '🇰🇷'),
    CountryCode(code: '+39', name: 'Italy', flag: '🇮🇹'),
    CountryCode(code: '+34', name: 'Spain', flag: '🇪🇸'),
    CountryCode(code: '+31', name: 'Netherlands', flag: '🇳🇱'),
    CountryCode(code: '+46', name: 'Sweden', flag: '🇸🇪'),
    CountryCode(code: '+47', name: 'Norway', flag: '🇳🇴'),
    CountryCode(code: '+45', name: 'Denmark', flag: '🇩🇰'),
    CountryCode(code: '+41', name: 'Switzerland', flag: '🇨🇭'),
    CountryCode(code: '+43', name: 'Austria', flag: '🇦🇹'),
    CountryCode(code: '+32', name: 'Belgium', flag: '🇧🇪'),
  ];

  @override
  void initState() {
    super.initState();
    _selectedCountryCode = widget.initialCountryCode ?? '+91';
    _phoneController = TextEditingController(
      text: widget.initialPhoneNumber ?? '',
    );
    // _validatePhoneNumber();
  }

  @override
  void dispose() {
    _phoneController.dispose();
    super.dispose();
  }

  void _validatePhoneNumber() {
    final phoneNumber = _phoneController.text.trim();
    final countryCode = _selectedCountryCode;

    setState(() {
      _errorText = null;
    });

    if (phoneNumber.isEmpty) {
      setState(() {
        _errorText = 'Phone number is required';
      });
      return;
    }

    // Basic validation based on country code
    String? error = _validatePhoneForCountry(phoneNumber, countryCode);

    if (error != null) {
      setState(() {
        _errorText = error;
      });
    } else {
      widget.onValidated?.call(countryCode, phoneNumber);
    }

    widget.onChanged?.call(countryCode, phoneNumber);
  }

  String? _validatePhoneForCountry(String phoneNumber, String countryCode) {
    // Remove all non-digit characters for validation
    final digitsOnly = phoneNumber.replaceAll(RegExp(r'[^\d]'), '');

    switch (countryCode) {
      case '+91': // India
        if (digitsOnly.length != 10) {
          return 'Indian mobile number must be 10 digits';
        }
        if (!digitsOnly.startsWith(RegExp(r'[6-9]'))) {
          return 'Indian mobile number must start with 6, 7, 8, or 9';
        }
        break;
      case '+1': // US/Canada
        if (digitsOnly.length != 10) {
          return 'US/Canada number must be 10 digits';
        }
        break;
      case '+44': // UK
        if (digitsOnly.length != 10) {
          return 'UK mobile number must be 10 digits';
        }
        break;
      case '+86': // China
        if (digitsOnly.length != 11) {
          return 'Chinese mobile number must be 11 digits';
        }
        break;
      case '+81': // Japan
        if (digitsOnly.length != 10 || digitsOnly.length != 11) {
          return 'Japanese mobile number must be 10-11 digits';
        }
        break;
      default:
        if (digitsOnly.length < 7 || digitsOnly.length > 15) {
          return 'Phone number must be 7-15 digits';
        }
    }

    return null;
  }

  String _formatPhoneNumber(String phoneNumber) {
    final digitsOnly = phoneNumber.replaceAll(RegExp(r'[^\d]'), '');

    if (_selectedCountryCode == '+91' && digitsOnly.length <= 10) {
      // Format Indian number: 98765 43210
      if (digitsOnly.length <= 5) {
        return digitsOnly;
      } else {
        return '${digitsOnly.substring(0, 5)} ${digitsOnly.substring(5)}';
      }
    } else if (_selectedCountryCode == '+1' && digitsOnly.length <= 10) {
      // Format US number: (987) 654-3210
      if (digitsOnly.length <= 3) {
        return digitsOnly;
      } else if (digitsOnly.length <= 6) {
        return '(${digitsOnly.substring(0, 3)}) ${digitsOnly.substring(3)}';
      } else {
        return '(${digitsOnly.substring(0, 3)}) ${digitsOnly.substring(3, 6)}-${digitsOnly.substring(6)}';
      }
    }

    return phoneNumber;
  }

  void _showCountryCodePicker() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.6,
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          children: [
            Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                'Select Country',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
              ),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: _countryCodes.length,
                itemBuilder: (context, index) {
                  final country = _countryCodes[index];
                  final isSelected = country.code == _selectedCountryCode;

                  return ListTile(
                    leading: Text(
                      country.flag,
                      style: const TextStyle(fontSize: 24),
                    ),
                    title: Text(country.name),
                    trailing: Text(
                      country.code,
                      style: TextStyle(
                        fontWeight: isSelected
                            ? FontWeight.bold
                            : FontWeight.normal,
                        color: isSelected ? Colors.blue : Colors.grey[600],
                      ),
                    ),
                    selected: isSelected,
                    onTap: () {
                      setState(() {
                        _selectedCountryCode = country.code;
                      });
                      _validatePhoneNumber();
                      Navigator.pop(context);
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          height: 50,
          decoration: BoxDecoration(
            color: Colors.grey[100],
            borderRadius: BorderRadius.circular(120),
            border: Border.all(
              color: _errorText != null ? Colors.red : Colors.grey[300]!,
              width: 1,
            ),
          ),
          child: Row(
            children: [
              SizedBox(width: 12),
              // Country code selector
              GestureDetector(
                onTap: widget.enabled ? _showCountryCodePicker : null,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        _selectedCountryCode,
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w500,
                          color: widget.enabled
                              ? Colors.grey[800]
                              : Colors.grey[400],
                        ),
                      ),
                      const SizedBox(width: 4),
                      Icon(
                        Icons.keyboard_arrow_down,
                        size: 20,
                        color: widget.enabled ? Colors.black : Colors.grey[400],
                      ),
                    ],
                  ),
                ),
              ),
              // Separator
              Container(width: 1, height: 30, color: Colors.grey[300]),
              // Phone number input
              Expanded(
                child: TextField(
                  controller: _phoneController,
                  enabled: widget.enabled,
                  keyboardType: TextInputType.phone,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(15),
                  ],
                  onChanged: (value) {
                    final formatted = _formatPhoneNumber(value);
                    if (formatted != value) {
                      _phoneController.value = TextEditingValue(
                        text: formatted,
                        selection: TextSelection.collapsed(
                          offset: formatted.length,
                        ),
                      );
                    }
                    _validatePhoneNumber();
                  },
                  decoration: InputDecoration(
                    hintText: _selectedCountryCode == '+91'
                        ? '98765 43210'
                        : 'Enter phone number',
                    hintStyle: TextStyle(color: Colors.grey[400], fontSize: 16),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                  style: TextStyle(
                    fontSize: 16,
                    color: widget.enabled ? Colors.grey[800] : Colors.grey[400],
                  ),
                ),
              ),
            ],
          ),
        ),
        // Error text
        if (_errorText != null || widget.errorText != null)
          Padding(
            padding: const EdgeInsets.only(top: 8, left: 4),
            child: Text(
              _errorText ?? widget.errorText!,
              style: const TextStyle(color: Colors.red, fontSize: 12),
            ),
          ),
      ],
    );
  }
}
