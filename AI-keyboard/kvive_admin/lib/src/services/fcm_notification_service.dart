import 'dart:convert';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:http/http.dart' as http;

/// Service for sending FCM notifications to all devices or specific tokens
class FCMNotificationService {
  static const String _devicesCollection = 'devices';
  static const String _notificationsCollection = 'broadcastNotifications';
  
  /// Send notification to all devices using topic
  /// Note: This requires devices to subscribe to the topic "all_users"
  static Future<bool> sendToTopic({
    required String title,
    required String body,
    String? link,
    String topic = 'all_users',
  }) async {
    try {
      // Save to Firestore for tracking
      await FirebaseFirestore.instance.collection(_notificationsCollection).add({
        'title': title,
        'body': body,
        'link': link,
        'target': 'topic:$topic',
        'createdAt': FieldValue.serverTimestamp(),
        'createdBy': FirebaseAuth.instance.currentUser?.uid,
        'status': 'sent',
      });

      // Note: To send via topic, you need to use Firebase Admin SDK or Cloud Functions
      // For now, we'll save to Firestore and let Cloud Function handle it
      // OR use HTTP v1 API if you have a server key
      
      return true;
    } catch (e) {
      print('Error sending notification to topic: $e');
      return false;
    }
  }

  /// Send notification to all devices by fetching all tokens from Firestore
  static Future<Map<String, dynamic>> sendToAllDevices({
    required String title,
    required String body,
    String? link,
  }) async {
    try {
      // Fetch all device tokens
      final devicesSnapshot = await FirebaseFirestore.instance
          .collection(_devicesCollection)
          .get();

      if (devicesSnapshot.docs.isEmpty) {
        return {
          'success': false,
          'message': 'No devices found',
          'sent': 0,
          'failed': 0,
        };
      }

      final tokens = devicesSnapshot.docs
          .map((doc) => doc.data()['token'] as String?)
          .where((token) => token != null && token.isNotEmpty)
          .toList();

      if (tokens.isEmpty) {
        return {
          'success': false,
          'message': 'No valid tokens found',
          'sent': 0,
          'failed': 0,
        };
      }

      // Save notification to Firestore with status 'queued' and tokens
      // The Cloud Function will trigger on document creation and process it
      final notificationRef = await FirebaseFirestore.instance
          .collection(_notificationsCollection)
          .add({
        'title': title,
        'body': body,
        'link': link,
        'target': 'all_devices',
        'targetCount': tokens.length,
        'createdAt': FieldValue.serverTimestamp(),
        'createdBy': FirebaseAuth.instance.currentUser?.uid,
        'status': 'queued', // Set to 'queued' immediately so Cloud Function processes it
        'tokens': tokens, // Include tokens in initial document
      });

      return {
        'success': true,
        'message': 'Notification queued for ${tokens.length} devices',
        'sent': tokens.length,
        'failed': 0,
        'notificationId': notificationRef.id,
      };
    } catch (e) {
      print('Error sending notification to all devices: $e');
      return {
        'success': false,
        'message': 'Error: $e',
        'sent': 0,
        'failed': 0,
      };
    }
  }

  /// Send notification to specific device tokens
  static Future<Map<String, dynamic>> sendToSpecificDevices({
    required String title,
    required String body,
    required List<String> tokens,
    String? link,
  }) async {
    try {
      if (tokens.isEmpty) {
        return {
          'success': false,
          'message': 'No tokens provided',
          'sent': 0,
          'failed': 0,
        };
      }

      // Save notification to Firestore
      final notificationRef = await FirebaseFirestore.instance
          .collection(_notificationsCollection)
          .add({
        'title': title,
        'body': body,
        'link': link,
        'target': 'specific_devices',
        'targetCount': tokens.length,
        'createdAt': FieldValue.serverTimestamp(),
        'createdBy': FirebaseAuth.instance.currentUser?.uid,
        'status': 'queued',
        'tokens': tokens,
      });

      return {
        'success': true,
        'message': 'Notification queued for ${tokens.length} devices',
        'sent': tokens.length,
        'failed': 0,
        'notificationId': notificationRef.id,
      };
    } catch (e) {
      print('Error sending notification to specific devices: $e');
      return {
        'success': false,
        'message': 'Error: $e',
        'sent': 0,
        'failed': 0,
      };
    }
  }

  /// Get device count
  static Future<int> getDeviceCount() async {
    try {
      final snapshot = await FirebaseFirestore.instance
          .collection(_devicesCollection)
          .count()
          .get();
      return snapshot.count ?? 0;
    } catch (e) {
      print('Error getting device count: $e');
      return 0;
    }
  }
}


