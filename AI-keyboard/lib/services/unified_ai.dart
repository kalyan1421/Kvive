import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// Processing modes
enum AIMode {
  tone('TONE'),
  grammar('GRAMMAR'),
  custom('CUSTOM'),
  rewrite('REWRITE'),
  feature('FEATURE');

  const AIMode(this.value);
  final String value;
}

/// Tone types (must match Kotlin enum)
enum AIToneType {
  formal('FORMAL'),
  casual('CASUAL'),
  funny('FUNNY'),
  angry('ANGRY'),
  enthusiastic('ENTHUSIASTIC'),
  polite('POLITE'),
  confident('CONFIDENT'),
  empathetic('EMPATHETIC');

  const AIToneType(this.value);
  final String value;
}

/// Processing features (must match Kotlin enum)
enum AIProcessingFeature {
  grammarFix('GRAMMAR_FIX'),
  simplify('SIMPLIFY'),
  expand('EXPAND'),
  shorten('SHORTEN'),
  translateToEnglish('TRANSLATE_TO_ENGLISH'),
  makeBulletPoints('MAKE_BULLET_POINTS');

  const AIProcessingFeature(this.value);
  final String value;
}

/// AI Result data class
class AIResult {
  final bool success;
  final String text;
  final String? error;
  final bool fromCache;
  final int time;
  final bool isComplete;

  const AIResult({
    required this.success,
    required this.text,
    this.error,
    this.fromCache = false,
    this.time = 0,
    this.isComplete = true,
  });

  factory AIResult.fromMap(Map<String, dynamic> map) {
    return AIResult(
      success: map['success'] ?? false,
      text: map['text'] ?? '',
      error: map['error'],
      fromCache: map['fromCache'] ?? false,
      time: map['time'] ?? 0,
      isComplete: map['isComplete'] ?? true,
    );
  }

  @override
  String toString() {
    return 'AIResult(success: $success, text: ${text.length > 50 ? '${text.substring(0, 50)}...' : text}, '
        'error: $error, fromCache: $fromCache, time: ${time}ms)';
  }
}

/// Tone information
class AIToneInfo {
  final String name;
  final String displayName;
  final String icon;
  final String color;

  const AIToneInfo({
    required this.name,
    required this.displayName,
    required this.icon,
    required this.color,
  });

  factory AIToneInfo.fromMap(Map<String, dynamic> map) {
    return AIToneInfo(
      name: map['name'] ?? '',
      displayName: map['displayName'] ?? '',
      icon: map['icon'] ?? '',
      color: map['color'] ?? '',
    );
  }
}

/// Feature information
class AIFeatureInfo {
  final String name;
  final String displayName;
  final String icon;

  const AIFeatureInfo({
    required this.name,
    required this.displayName,
    required this.icon,
  });

  factory AIFeatureInfo.fromMap(Map<String, dynamic> map) {
    return AIFeatureInfo(
      name: map['name'] ?? '',
      displayName: map['displayName'] ?? '',
      icon: map['icon'] ?? '',
    );
  }
}

/// Unified AI service for Flutter - communicates with Kotlin UnifiedAIService
class UnifiedAI {
  static const _channel = MethodChannel('ai_keyboard/unified_ai');

  /// Process text with AI
  static Future<AIResult> processText({
    required String text,
    AIMode mode = AIMode.grammar,
    AIToneType? tone,
    AIProcessingFeature? feature,
    bool stream = false,
    String? customPrompt,
  }) async {
    try {
      debugPrint('🧠 UnifiedAI: Processing text (mode: ${mode.value}, stream: $stream)');
      
      final args = <String, dynamic>{
        'text': text,
        'mode': mode.value,
        'stream': stream,
      };

      // Add optional parameters
      if (tone != null) args['tone'] = tone.value;
      if (feature != null) args['feature'] = feature.value;
      if (customPrompt != null && customPrompt.trim().isNotEmpty) {
        args['customPrompt'] = customPrompt;
      }

      final result = await _channel.invokeMethod('processAIText', args);
      final aiResult = AIResult.fromMap(Map<String, dynamic>.from(result));
      
      debugPrint('🧠 UnifiedAI: Result - success: ${aiResult.success}, '
          'fromCache: ${aiResult.fromCache}, time: ${aiResult.time}ms');
      
      return aiResult;
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error processing text - $e');
      return AIResult(
        success: false,
        text: '',
        error: 'Failed to process text: $e',
      );
    }
  }

  /// Adjust tone of text
  static Future<AIResult> adjustTone({
    required String text,
    required AIToneType tone,
    bool stream = false,
  }) async {
    return processText(
      text: text,
      mode: AIMode.tone,
      tone: tone,
      stream: stream,
    );
  }

  /// Fix grammar in text
  static Future<AIResult> fixGrammar({
    required String text,
    bool stream = false,
  }) async {
    return processText(
      text: text,
      mode: AIMode.grammar,
      stream: stream,
    );
  }

  /// Process text with specific feature
  static Future<AIResult> processFeature({
    required String text,
    required AIProcessingFeature feature,
    bool stream = false,
  }) async {
    return processText(
      text: text,
      mode: AIMode.feature,
      feature: feature,
      stream: stream,
    );
  }

  /// Process text with custom prompt
  static Future<AIResult> processCustom({
    required String text,
    required String prompt,
    bool stream = false,
  }) async {
    return processText(
      text: text,
      mode: AIMode.custom,
      stream: stream,
      customPrompt: prompt,
    );
  }

  /// Rewrite text
  static Future<AIResult> rewriteText({
    required String text,
    bool stream = false,
    String? prompt,
  }) async {
    return processText(
      text: text,
      mode: AIMode.rewrite,
      stream: stream,
      customPrompt: prompt,
    );
  }

  /// Generate smart replies
  static Future<AIResult> generateSmartReplies({
    required String message,
    String context = 'general',
    int count = 3,
    bool stream = false,
  }) async {
    try {
      debugPrint('🧠 UnifiedAI: Generating smart replies (context: $context, count: $count)');
      
      final result = await _channel.invokeMethod('generateSmartReplies', {
        'message': message,
        'context': context,
        'count': count,
        'stream': stream,
      });
      
      return AIResult.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error generating smart replies - $e');
      return AIResult(
        success: false,
        text: '',
        error: 'Failed to generate smart replies: $e',
      );
    }
  }

  /// Test AI connection
  static Future<AIResult> testConnection() async {
    try {
      debugPrint('🧠 UnifiedAI: Testing connection...');
      
      final result = await _channel.invokeMethod('testConnection');
      return AIResult.fromMap(Map<String, dynamic>.from(result));
    } catch (e) {
      debugPrint('❌ UnifiedAI: Connection test failed - $e');
      return AIResult(
        success: false,
        text: '',
        error: 'Connection test failed: $e',
      );
    }
  }

  /// Get available tones
  static Future<List<AIToneInfo>> getAvailableTones() async {
    try {
      final result = await _channel.invokeMethod('getAvailableTones');
      final List<dynamic> tonesList = result;
      return tonesList
          .map((tone) => AIToneInfo.fromMap(Map<String, dynamic>.from(tone)))
          .toList();
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error getting available tones - $e');
      return [];
    }
  }

  /// Get available features
  static Future<List<AIFeatureInfo>> getAvailableFeatures() async {
    try {
      final result = await _channel.invokeMethod('getAvailableFeatures');
      final List<dynamic> featuresList = result;
      return featuresList
          .map((feature) => AIFeatureInfo.fromMap(Map<String, dynamic>.from(feature)))
          .toList();
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error getting available features - $e');
      return [];
    }
  }

  /// Get service status
  static Future<Map<String, dynamic>> getServiceStatus() async {
    try {
      final result = await _channel.invokeMethod('getServiceStatus');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error getting service status - $e');
      return {
        'isReady': false,
        'hasApiKey': false,
        'aiEnabled': false,
        'error': e.toString(),
      };
    }
  }

  /// Get cache statistics
  static Future<Map<String, dynamic>> getCacheStats() async {
    try {
      final result = await _channel.invokeMethod('getCacheStats');
      return Map<String, dynamic>.from(result);
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error getting cache stats - $e');
      return {};
    }
  }

  /// Clear AI cache
  static Future<bool> clearCache() async {
    try {
      debugPrint('🧠 UnifiedAI: Clearing cache...');
      
      await _channel.invokeMethod('clearCache');
      return true;
    } catch (e) {
      debugPrint('❌ UnifiedAI: Error clearing cache - $e');
      return false;
    }
  }

  /// Check if AI service is ready
  static Future<bool> isReady() async {
    final status = await getServiceStatus();
    return status['isReady'] ?? false;
  }

  /// Convenience methods for common operations

  /// Make text more professional
  static Future<AIResult> makeFormal(String text, {bool stream = false}) =>
      adjustTone(text: text, tone: AIToneType.formal, stream: stream);

  /// Make text more casual  
  static Future<AIResult> makeCasual(String text, {bool stream = false}) =>
      adjustTone(text: text, tone: AIToneType.casual, stream: stream);

  /// Make text shorter
  static Future<AIResult> makeShort(String text, {bool stream = false}) =>
      processFeature(text: text, feature: AIProcessingFeature.shorten, stream: stream);

  /// Expand text with more details
  static Future<AIResult> expandText(String text, {bool stream = false}) =>
      processFeature(text: text, feature: AIProcessingFeature.expand, stream: stream);

  /// Simplify text
  static Future<AIResult> simplifyText(String text, {bool stream = false}) =>
      processFeature(text: text, feature: AIProcessingFeature.simplify, stream: stream);

  /// Convert to bullet points
  static Future<AIResult> makeBulletPoints(String text, {bool stream = false}) =>
      processFeature(text: text, feature: AIProcessingFeature.makeBulletPoints, stream: stream);

}
