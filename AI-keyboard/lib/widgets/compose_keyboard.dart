// Flutter wrapper for the Compose keyboard
// This would be used for in-app keyboard scenarios

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ComposeKeyboardWidget extends StatefulWidget {
  final Function(String) onKeyPressed;
  final VoidCallback onBackspace;
  final VoidCallback onEnter;
  final VoidCallback onSpace;
  final KeyboardType keyboardType;
  
  const ComposeKeyboardWidget({
    Key? key,
    required this.onKeyPressed,
    required this.onBackspace,
    required this.onEnter,
    required this.onSpace,
    this.keyboardType = KeyboardType.qwerty,
  }) : super(key: key);

  @override
  State<ComposeKeyboardWidget> createState() => _ComposeKeyboardWidgetState();
}

class _ComposeKeyboardWidgetState extends State<ComposeKeyboardWidget> {
  static const platform = MethodChannel('ai_keyboard/compose');

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 280,
      child: AndroidView(
        viewType: 'compose_keyboard_view',
        creationParams: {
          'keyboardType': widget.keyboardType.name,
        },
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: (id) {
          _setupMethodChannel(id);
        },
      ),
    );
  }

  void _setupMethodChannel(int id) {
    platform.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onKeyPressed':
          widget.onKeyPressed(call.arguments as String);
          break;
        case 'onBackspace':
          widget.onBackspace();
          break;
        case 'onEnter':
          widget.onEnter();
          break;
        case 'onSpace':
          widget.onSpace();
          break;
      }
    });
  }
}

enum KeyboardType {
  qwerty,
  numeric,
}

