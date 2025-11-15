import 'package:flutter/services.dart';
import 'dart:developer' as developer;

/// 🧠 Unified Suggestion Service
/// 
/// Flutter service for communicating with UnifiedSuggestionController in Kotlin
/// 
/// Features:
/// - Toggle AI suggestions
/// - Toggle emoji suggestions
/// - Toggle clipboard suggestions
/// - Toggle next-word prediction
/// - Get current settings
/// - Clear suggestion cache
/// - Get controller statistics
class UnifiedSuggestionService {
  static const MethodChannel _channel = MethodChannel('ai_keyboard/suggestions');
  
  /// Update suggestion settings
  /// 
  /// Example:
  /// ```dart
  /// await UnifiedSuggestionService.updateSettings(
  ///   aiSuggestions: true,
  ///   emojiSuggestions: true,
  ///   clipboardSuggestions: false,
  ///   nextWordPrediction: true,
  /// );
  /// ```
  static Future<bool> updateSettings({
    bool? aiSuggestions,
    bool? emojiSuggestions,
    bool? clipboardSuggestions,
    bool? nextWordPrediction,
  }) async {
    try {
      final Map<String, dynamic> params = {};
      
      if (aiSuggestions != null) params['aiSuggestions'] = aiSuggestions;
      if (emojiSuggestions != null) params['emojiSuggestions'] = emojiSuggestions;
      if (clipboardSuggestions != null) params['clipboardSuggestions'] = clipboardSuggestions;
      if (nextWordPrediction != null) params['nextWordPrediction'] = nextWordPrediction;
      
      final result = await _channel.invokeMethod('updateSettings', params);
      developer.log('✅ Suggestion settings updated: $params', name: 'UnifiedSuggestionService');
      return result == true;
    } catch (e) {
      developer.log('❌ Error updating suggestion settings: $e', name: 'UnifiedSuggestionService');
      return false;
    }
  }
  
  /// Get current suggestion settings
  static Future<SuggestionSettings?> getSettings() async {
    try {
      final result = await _channel.invokeMethod<Map>('getSettings');
      if (result != null) {
        return SuggestionSettings.fromMap(Map<String, dynamic>.from(result));
      }
      return null;
    } catch (e) {
      developer.log('❌ Error getting suggestion settings: $e', name: 'UnifiedSuggestionService');
      return null;
    }
  }
  
  /// Clear suggestion cache
  static Future<bool> clearCache() async {
    try {
      final result = await _channel.invokeMethod('clearCache');
      developer.log('🗑️ Suggestion cache cleared', name: 'UnifiedSuggestionService');
      return result == true;
    } catch (e) {
      developer.log('❌ Error clearing cache: $e', name: 'UnifiedSuggestionService');
      return false;
    }
  }
  
  /// Get controller statistics
  static Future<SuggestionStats?> getStats() async {
    try {
      final result = await _channel.invokeMethod<Map>('getStats');
      if (result != null) {
        return SuggestionStats.fromMap(Map<String, dynamic>.from(result));
      }
      return null;
    } catch (e) {
      developer.log('❌ Error getting stats: $e', name: 'UnifiedSuggestionService');
      return null;
    }
  }
  
  /// Set up listener for suggestion updates (optional)
  /// This allows real-time suggestion sync from Kotlin to Flutter
  static void setMethodCallHandler(Future<dynamic> Function(MethodCall call)? handler) {
    _channel.setMethodCallHandler(handler);
  }
}

/// Suggestion settings data class
class SuggestionSettings {
  final bool aiEnabled;
  final bool emojiEnabled;
  final bool clipboardEnabled;
  final bool nextWordEnabled;
  
  SuggestionSettings({
    required this.aiEnabled,
    required this.emojiEnabled,
    required this.clipboardEnabled,
    required this.nextWordEnabled,
  });
  
  factory SuggestionSettings.fromMap(Map<String, dynamic> map) {
    return SuggestionSettings(
      aiEnabled: map['aiEnabled'] == true,
      emojiEnabled: map['emojiEnabled'] == true,
      clipboardEnabled: map['clipboardEnabled'] == true,
      nextWordEnabled: map['nextWordEnabled'] == true,
    );
  }
  
  Map<String, dynamic> toMap() {
    return {
      'aiEnabled': aiEnabled,
      'emojiEnabled': emojiEnabled,
      'clipboardEnabled': clipboardEnabled,
      'nextWordEnabled': nextWordEnabled,
    };
  }
  
  @override
  String toString() {
    return 'SuggestionSettings(ai: $aiEnabled, emoji: $emojiEnabled, clipboard: $clipboardEnabled, nextWord: $nextWordEnabled)';
  }
}

/// Suggestion statistics data class
class SuggestionStats {
  final int cacheSize;
  final int listenerCount;
  final bool aiEnabled;
  final bool emojiEnabled;
  final bool clipboardEnabled;
  final bool nextWordEnabled;
  
  SuggestionStats({
    required this.cacheSize,
    required this.listenerCount,
    required this.aiEnabled,
    required this.emojiEnabled,
    required this.clipboardEnabled,
    required this.nextWordEnabled,
  });
  
  factory SuggestionStats.fromMap(Map<String, dynamic> map) {
    return SuggestionStats(
      cacheSize: map['cacheSize'] ?? 0,
      listenerCount: map['listenerCount'] ?? 0,
      aiEnabled: map['aiEnabled'] == true,
      emojiEnabled: map['emojiEnabled'] == true,
      clipboardEnabled: map['clipboardEnabled'] == true,
      nextWordEnabled: map['nextWordEnabled'] == true,
    );
  }
  
  @override
  String toString() {
    return 'SuggestionStats(cache: $cacheSize, listeners: $listenerCount, ai: $aiEnabled, emoji: $emojiEnabled, clipboard: $clipboardEnabled, nextWord: $nextWordEnabled)';
  }
}

/// Example: Suggestion settings screen
/// 
/// ```dart
/// class SuggestionSettingsScreen extends StatefulWidget {
///   @override
///   _SuggestionSettingsScreenState createState() => _SuggestionSettingsScreenState();
/// }
/// 
/// class _SuggestionSettingsScreenState extends State<SuggestionSettingsScreen> {
///   bool _aiEnabled = true;
///   bool _emojiEnabled = true;
///   bool _clipboardEnabled = true;
///   bool _nextWordEnabled = true;
///   
///   @override
///   void initState() {
///     super.initState();
///     _loadSettings();
///   }
///   
///   Future<void> _loadSettings() async {
///     final settings = await UnifiedSuggestionService.getSettings();
///     if (settings != null) {
///       setState(() {
///         _aiEnabled = settings.aiEnabled;
///         _emojiEnabled = settings.emojiEnabled;
///         _clipboardEnabled = settings.clipboardEnabled;
///         _nextWordEnabled = settings.nextWordEnabled;
///       });
///     }
///   }
///   
///   Future<void> _updateSetting(String setting, bool value) async {
///     await UnifiedSuggestionService.updateSettings(
///       aiSuggestions: setting == 'ai' ? value : null,
///       emojiSuggestions: setting == 'emoji' ? value : null,
///       clipboardSuggestions: setting == 'clipboard' ? value : null,
///       nextWordPrediction: setting == 'nextWord' ? value : null,
///     );
///   }
///   
///   @override
///   Widget build(BuildContext context) {
///     return Scaffold(
///       appBar: AppBar(title: Text('Suggestion Settings')),
///       body: ListView(
///         children: [
///           SwitchListTile(
///             title: Text('AI Suggestions'),
///             subtitle: Text('Enable AI-powered suggestions'),
///             value: _aiEnabled,
///             onChanged: (value) {
///               setState(() => _aiEnabled = value);
///               _updateSetting('ai', value);
///             },
///           ),
///           SwitchListTile(
///             title: Text('Emoji Suggestions'),
///             subtitle: Text('Show emoji based on typed words'),
///             value: _emojiEnabled,
///             onChanged: (value) {
///               setState(() => _emojiEnabled = value);
///               _updateSetting('emoji', value);
///             },
///           ),
///           SwitchListTile(
///             title: Text('Clipboard Suggestions'),
///             subtitle: Text('Quick paste from clipboard'),
///             value: _clipboardEnabled,
///             onChanged: (value) {
///               setState(() => _clipboardEnabled = value);
///               _updateSetting('clipboard', value);
///             },
///           ),
///           SwitchListTile(
///             title: Text('Next-Word Prediction'),
///             subtitle: Text('Predict next word based on context'),
///             value: _nextWordEnabled,
///             onChanged: (value) {
///               setState(() => _nextWordEnabled = value);
///               _updateSetting('nextWord', value);
///             },
///           ),
///           ListTile(
///             title: Text('Clear Cache'),
///             subtitle: Text('Clear suggestion cache'),
///             trailing: Icon(Icons.delete),
///             onTap: () async {
///               await UnifiedSuggestionService.clearCache();
///               ScaffoldMessenger.of(context).showSnackBar(
///                 SnackBar(content: Text('Cache cleared')),
///               );
///             },
///           ),
///         ],
///       ),
///     );
///   }
/// }
/// ```

