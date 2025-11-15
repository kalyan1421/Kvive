import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class OpenAIChatService {
  static const String _baseUrl = 'https://api.openai.com/v1/chat/completions';
  static const String _model = 'gpt-3.5-turbo';
  static const String _apiKeyPref = 'openai_api_key';
  
  // Default API key - should be configured in settings
  static const String _defaultApiKey = 'YOUR_OPENAI_API_KEY_HERE';
  
  // System prompt to restrict responses to keyboard-related topics
  static const String _systemPrompt = '''You are a helpful AI assistant for Kvive Keyboard app. 
Your role is to help users with questions about the keyboard features, settings, and functionality.

You should ONLY answer questions related to:
- Keyboard themes and customization
- Typing features (autocorrect, predictions, suggestions)
- Language support and switching
- Keyboard settings and configuration
- AI-powered features (smart suggestions, AI writing assistance)
- Emoji, GIFs, and special characters
- Sound and haptic feedback
- Premium features and upgrades
- Troubleshooting keyboard issues

If a user asks about topics unrelated to the keyboard (like general knowledge, weather, news, etc.), 
politely redirect them by saying: "I'm here to help with keyboard-related questions. Could you ask me about keyboard features, themes, settings, or typing assistance?"

Be friendly, concise, and helpful. Keep responses clear and to the point.''';

  /// Get the API key from SharedPreferences
  static Future<String?> _getApiKey() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final apiKey = prefs.getString(_apiKeyPref);
      
      // Return stored key or default key
      if (apiKey != null && apiKey.isNotEmpty && apiKey != _defaultApiKey) {
        return apiKey;
      }
      
      // Check if default key has been set
      if (_defaultApiKey != 'YOUR_OPENAI_API_KEY_HERE') {
        return _defaultApiKey;
      }
      
      return null;
    } catch (e) {
      print('Error getting API key: $e');
      return null;
    }
  }

  /// Save API key to SharedPreferences
  static Future<void> saveApiKey(String apiKey) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_apiKeyPref, apiKey);
    } catch (e) {
      print('Error saving API key: $e');
    }
  }

  /// Check if API key is configured
  static Future<bool> isApiKeyConfigured() async {
    final apiKey = await _getApiKey();
    return apiKey != null && apiKey.isNotEmpty;
  }

  /// Send a chat message and get AI response
  static Future<String> sendMessage({
    required String userMessage,
    List<Map<String, String>>? conversationHistory,
  }) async {
    try {
      final apiKey = await _getApiKey();
      
      if (apiKey == null || apiKey.isEmpty) {
        return "I'm currently not configured to respond. Please ask the app administrator to set up the OpenAI API key in the settings.";
      }

      // Build messages array with system prompt and conversation history
      final messages = <Map<String, String>>[
        {'role': 'system', 'content': _systemPrompt},
      ];

      // Add conversation history if provided (keep last 10 messages for context)
      if (conversationHistory != null && conversationHistory.isNotEmpty) {
        final recentHistory = conversationHistory.length > 10 
            ? conversationHistory.sublist(conversationHistory.length - 10)
            : conversationHistory;
        messages.addAll(recentHistory);
      }

      // Add current user message
      messages.add({'role': 'user', 'content': userMessage});

      // Make API request
      final response = await http.post(
        Uri.parse(_baseUrl),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $apiKey',
        },
        body: jsonEncode({
          'model': _model,
          'messages': messages,
          'max_tokens': 300,
          'temperature': 0.7,
        }),
      ).timeout(
        const Duration(seconds: 30),
        onTimeout: () {
          throw Exception('Request timeout. Please check your internet connection.');
        },
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final content = data['choices'][0]['message']['content'] as String;
        return content.trim();
      } else if (response.statusCode == 401) {
        return "API key is invalid. Please check your OpenAI API key configuration.";
      } else if (response.statusCode == 429) {
        return "Too many requests. Please wait a moment and try again.";
      } else {
        print('OpenAI API error: ${response.statusCode} - ${response.body}');
        return "I'm having trouble connecting right now. Please try again in a moment.";
      }
    } catch (e) {
      print('Error sending message to OpenAI: $e');
      
      if (e.toString().contains('SocketException') || 
          e.toString().contains('timeout')) {
        return "I can't reach the server right now. Please check your internet connection.";
      }
      
      return "Sorry, I encountered an error. Please try again.";
    }
  }

  /// Get a quick response for common keyboard questions (fallback)
  static String getQuickResponse(String userMessage) {
    final message = userMessage.toLowerCase();

    if (message.contains('theme') || message.contains('color')) {
      return "You can customize your keyboard themes from the Theme section. We have various beautiful themes like Natural, Red Rose, Pink Rose, and many more! You can also create your own custom themes.";
    } else if (message.contains('language')) {
      return "Kvive Keyboard supports multiple languages! Go to Settings > Languages to add or switch between languages. We support English, Hindi, Gujarati, Bengali, Tamil, Telugu, and many more.";
    } else if (message.contains('autocorrect') || message.contains('correction')) {
      return "Autocorrect helps fix typos as you type. You can enable or disable it in Settings > Typing. You can also adjust the correction level to suit your preference.";
    } else if (message.contains('emoji') || message.contains('emoticon')) {
      return "Access emojis by tapping the emoji button on your keyboard. We have a wide variety of emojis, GIFs, and stickers to make your messages more expressive!";
    } else if (message.contains('prediction') || message.contains('suggestion')) {
      return "Smart predictions appear above your keyboard as you type. The AI learns from your typing style to provide better suggestions over time. Enable it in Settings > AI Features.";
    } else if (message.contains('premium') || message.contains('upgrade')) {
      return "Premium features include unlimited themes, advanced AI suggestions, priority support, and more! Tap the crown icon to see all premium benefits and upgrade.";
    } else if (message.contains('sound') || message.contains('haptic') || message.contains('vibrat')) {
      return "Customize keyboard sounds and haptic feedback in Settings > Feedback. You can choose different sound effects and adjust vibration intensity.";
    } else if (message.contains('hello') || message.contains('hi') || message.contains('hey')) {
      return "Hello! I'm here to help you with anything related to Kvive Keyboard. Ask me about themes, typing features, settings, or any other keyboard functionality!";
    } else {
      return "I'm here to help with keyboard-related questions! You can ask me about themes, typing features, settings, languages, AI assistance, or premium features.";
    }
  }
}

