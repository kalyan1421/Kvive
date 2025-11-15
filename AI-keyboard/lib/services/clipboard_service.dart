import 'package:flutter/services.dart';
import 'dart:convert';
import 'dart:async';

/// Service for communicating with Android keyboard clipboard manager
class ClipboardService {
  static const MethodChannel _channel = MethodChannel('ai_keyboard/clipboard');
  
  // Stream controller for clipboard updates
  static final StreamController<List<ClipboardItemData>> _historyController =
      StreamController<List<ClipboardItemData>>.broadcast();
  
  static final StreamController<ClipboardItemData> _newItemController =
      StreamController<ClipboardItemData>.broadcast();
  
  // Streams for listening to clipboard changes
  static Stream<List<ClipboardItemData>> get onHistoryChanged => _historyController.stream;
  static Stream<ClipboardItemData> get onNewItem => _newItemController.stream;
  
  /// Initialize the clipboard service
  static Future<void> initialize() async {
    // Set up method call handler for callbacks from Kotlin
    _channel.setMethodCallHandler(_handleMethodCall);
  }
  
  /// Handle method calls from Kotlin
  static Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onHistoryChanged':
        final int count = call.arguments as int;
        print('📋 Clipboard history changed: $count items');
        // Fetch the updated history
        final history = await getHistory();
        _historyController.add(history);
        break;
        
      case 'onNewItem':
        final Map<String, dynamic> itemData = Map<String, dynamic>.from(call.arguments);
        print('📋 New clipboard item: ${itemData['text']}');
        final item = ClipboardItemData(
          id: (itemData['id'] as String?) ?? '',
          text: itemData['text'] as String? ?? '',
          timestamp: _parseTimestamp(itemData['timestamp']),
          isPinned: false,
          isOTP: itemData['isOTP'] as bool? ?? false,
        );
        _newItemController.add(item);
        break;
        
      default:
        print('⚠️ Unknown method call from Kotlin: ${call.method}');
    }
  }
  
  /// Get clipboard history from keyboard
  static Future<List<ClipboardItemData>> getHistory({int maxItems = 20}) async {
    try {
      final List<dynamic>? result = await _channel.invokeMethod('getHistory', {
        'maxItems': maxItems,
      });
      
      if (result == null) return [];
      
      return result.map<ClipboardItemData>((payload) {
        if (payload is String) {
          final json = jsonDecode(payload) as Map<String, dynamic>;
          return ClipboardItemData.fromJson(json);
        } else if (payload is Map) {
          return ClipboardItemData.fromJson(Map<String, dynamic>.from(payload as Map));
        } else {
          throw StateError('Unsupported clipboard payload: ${payload.runtimeType}');
        }
      }).toList();
    } catch (e) {
      print('❌ Error getting clipboard history: $e');
      return [];
    }
  }
  
  /// Toggle pin status of a clipboard item
  static Future<bool> togglePin(String itemId) async {
    try {
      final bool? result = await _channel.invokeMethod('togglePin', {
        'itemId': itemId,
      });
      return result ?? false;
    } catch (e) {
      print('❌ Error toggling pin: $e');
      return false;
    }
  }
  
  /// Delete a clipboard item
  static Future<bool> deleteItem(String itemId) async {
    try {
      final bool? result = await _channel.invokeMethod('deleteItem', {
        'itemId': itemId,
      });
      return result ?? false;
    } catch (e) {
      print('❌ Error deleting item: $e');
      return false;
    }
  }
  
  /// Clear all non-pinned clipboard items
  static Future<bool> clearAll() async {
    try {
      final bool? result = await _channel.invokeMethod('clearAll');
      return result ?? false;
    } catch (e) {
      print('❌ Error clearing clipboard: $e');
      return false;
    }
  }
  
  /// Update clipboard settings in keyboard
  static Future<bool> updateSettings(Map<String, dynamic> settings) async {
    try {
      final bool? result = await _channel.invokeMethod('updateSettings', settings);
      return result ?? false;
    } catch (e) {
      print('❌ Error updating clipboard settings: $e');
      return false;
    }
  }
  
  /// Get current clipboard settings from keyboard
  static Future<Map<String, dynamic>> getSettings() async {
    try {
      final Map<dynamic, dynamic>? result = await _channel.invokeMethod('getSettings');
      if (result == null) return {};
      return Map<String, dynamic>.from(result);
    } catch (e) {
      print('❌ Error getting clipboard settings: $e');
      return {};
    }
  }
  
  /// Sync from system clipboard
  static Future<bool> syncFromSystem() async {
    try {
      final bool? result = await _channel.invokeMethod('syncFromSystem');
      return result ?? false;
    } catch (e) {
      print('❌ Error syncing from system: $e');
      return false;
    }
  }
  
  /// Sync clipboard to Kvīve Cloud (Firestore)
  static Future<bool> syncToCloud() async {
    try {
      final bool? result = await _channel.invokeMethod('syncToCloud');
      return result ?? false;
    } catch (e) {
      print('❌ Error syncing to cloud: $e');
      return false;
    }
  }
  
  /// Sync clipboard from Kvīve Cloud (Firestore)
  static Future<bool> syncFromCloud() async {
    try {
      final bool? result = await _channel.invokeMethod('syncFromCloud');
      return result ?? false;
    } catch (e) {
      print('❌ Error syncing from cloud: $e');
      return false;
    }
  }
  
  /// Dispose resources
  static void dispose() {
    _historyController.close();
    _newItemController.close();
  }
}

/// Data model for clipboard items (matches Kotlin ClipboardItem)
class ClipboardItemData {
  final String id;
  final String text;
  final int timestamp;
  final bool isPinned;
  final bool isTemplate;
  final String? category;
  final bool isOTP;
  
  ClipboardItemData({
    required this.id,
    required this.text,
    required this.timestamp,
    this.isPinned = false,
    this.isTemplate = false,
    this.category,
    this.isOTP = false,
  });
  
  factory ClipboardItemData.fromJson(Map<String, dynamic> json) {
    return ClipboardItemData(
      id: json['id'] as String? ?? '',
      text: json['text'] as String? ?? '',
      timestamp: _parseTimestamp(json['timestamp']),
      isPinned: json['isPinned'] as bool? ?? false,
      isTemplate: json['isTemplate'] as bool? ?? false,
      category: json['category'] as String?,
      isOTP: json['isOTP'] as bool? ?? false,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'text': text,
      'timestamp': timestamp,
      'isPinned': isPinned,
      'isTemplate': isTemplate,
      'category': category,
      'isOTP': isOTP,
    };
  }
  
  String getPreview(int maxLength) {
    if (text.length <= maxLength) return text;
    return '${text.substring(0, maxLength)}...';
  }
  
  String getFormattedTime() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final diff = now - timestamp;
    final minutes = diff ~/ 60000;
    final hours = diff ~/ 3600000;
    final days = diff ~/ 86400000;
    
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return '${minutes}m ago';
    if (hours < 24) return '${hours}h ago';
    return '${days}d ago';
  }
}

int _parseTimestamp(dynamic value) {
  if (value is int) return value;
  if (value is double) return value.toInt();
  if (value is String) return int.tryParse(value) ?? DateTime.now().millisecondsSinceEpoch;
  return DateTime.now().millisecondsSinceEpoch;
}
