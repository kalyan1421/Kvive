import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Service to manage user notifications received from FCM
class NotificationService {
  static const String _userNotificationsCollection = 'userNotifications';
  
  // Lazy getter to ensure Firebase is initialized before accessing Firestore
  static FirebaseFirestore get _firestore {
    try {
      // Ensure Firebase is initialized
      Firebase.app();
      return FirebaseFirestore.instance;
    } catch (e) {
      throw StateError(
        'Firebase must be initialized before using NotificationService. '
        'Error: $e',
      );
    }
  }

  /// Save a received notification to Firestore
  /// This is called when a notification is received (foreground or background)
  /// Prevents duplicates by checking if notificationId already exists
  static Future<void> saveNotification({
    required String title,
    required String body,
    String? link,
    String? notificationId,
    Map<String, dynamic>? data,
  }) async {
    try {
      // Ensure Firebase is initialized before proceeding
      try {
        Firebase.app();
      } catch (e) {
        debugPrint('❌ NotificationService: Firebase not initialized: $e');
        return;
      }
      
      // Get device token to identify the device
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) {
        debugPrint('⚠️ NotificationService: No device token found');
        return;
      }

      // Generate a unique notification ID if not provided
      final finalNotificationId = notificationId ?? 
          '${DateTime.now().millisecondsSinceEpoch}_${title.hashCode}';

      // Check if notification with this ID already exists to prevent duplicates
      if (finalNotificationId.isNotEmpty) {
        final existingQuery = await _firestore
            .collection(_userNotificationsCollection)
            .doc(deviceToken)
            .collection('notifications')
            .where('notificationId', isEqualTo: finalNotificationId)
            .limit(1)
            .get();

        if (existingQuery.docs.isNotEmpty) {
          debugPrint('ℹ️ NotificationService: Notification already exists, skipping duplicate: $title');
          return;
        }
      }

      debugPrint('📝 NotificationService: Saving notification for device: ${deviceToken.substring(0, 20)}...');

      // Create notification document
      final notificationData = {
        'title': title,
        'body': body,
        'link': link,
        'isRead': false,
        'receivedAt': FieldValue.serverTimestamp(),
        'deviceToken': deviceToken,
        'notificationId': finalNotificationId,
        ...?data,
      };

      // Save to Firestore under device token (no user login required)
      await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .add(notificationData);

      debugPrint('✅ NotificationService: Notification saved: $title');
    } catch (e, stackTrace) {
      debugPrint('❌ NotificationService: Error saving notification: $e');
      debugPrint('❌ NotificationService: Stack trace: $stackTrace');
    }
  }

  /// Mark notification as read
  static Future<void> markAsRead(String notificationDocId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) return;

      await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .doc(notificationDocId)
          .update({'isRead': true});

      debugPrint('✅ NotificationService: Notification marked as read');
    } catch (e) {
      debugPrint('❌ NotificationService: Error marking as read: $e');
    }
  }

  /// Mark all notifications as read
  static Future<void> markAllAsRead() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) return;

      final snapshot = await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .where('isRead', isEqualTo: false)
          .get();

      final batch = _firestore.batch();
      for (var doc in snapshot.docs) {
        batch.update(doc.reference, {'isRead': true});
      }
      await batch.commit();

      debugPrint('✅ NotificationService: All notifications marked as read');
    } catch (e) {
      debugPrint('❌ NotificationService: Error marking all as read: $e');
    }
  }

  /// Get stream of notifications for current device
  static Stream<QuerySnapshot<Map<String, dynamic>>> getNotificationsStream() {
    try {
      return _firestore
          .collection(_userNotificationsCollection)
          .doc('current_device') // Will be replaced with actual token
          .collection('notifications')
          .orderBy('receivedAt', descending: true)
          .snapshots();
    } catch (e) {
      debugPrint('❌ NotificationService: Error getting notifications stream: $e');
      // Return empty stream on error
      return const Stream.empty();
    }
  }

  /// Get notifications for current device (async)
  static Future<List<Map<String, dynamic>>> getNotifications() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) {
        return [];
      }

      final snapshot = await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .orderBy('receivedAt', descending: true)
          .get();

      return snapshot.docs.map((doc) {
        final data = doc.data();
        return {
          'id': doc.id,
          'title': data['title'] ?? '',
          'body': data['body'] ?? '',
          'link': data['link'],
          'isRead': data['isRead'] ?? false,
          'receivedAt': data['receivedAt'],
          'iconType': getIconType(data),
        };
      }).toList();
    } catch (e) {
      debugPrint('❌ NotificationService: Error getting notifications: $e');
      return [];
    }
  }

  /// Get notifications stream for current device
  static Stream<QuerySnapshot<Map<String, dynamic>>> getNotificationsStreamForDevice() async* {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) {
        yield* const Stream<QuerySnapshot<Map<String, dynamic>>>.empty();
        return;
      }

      yield* _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .orderBy('receivedAt', descending: true)
          .snapshots();
    } catch (e) {
      debugPrint('❌ NotificationService: Error getting notifications stream: $e');
      yield* const Stream<QuerySnapshot<Map<String, dynamic>>>.empty();
    }
  }

  /// Get unread count
  static Future<int> getUnreadCount() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) return 0;

      final snapshot = await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .where('isRead', isEqualTo: false)
          .count()
          .get();

      return snapshot.count ?? 0;
    } catch (e) {
      debugPrint('❌ NotificationService: Error getting unread count: $e');
      return 0;
    }
  }

  /// Delete notification
  static Future<void> deleteNotification(String notificationDocId) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final deviceToken = prefs.getString('fcm_token_saved');
      
      if (deviceToken == null) return;

      await _firestore
          .collection(_userNotificationsCollection)
          .doc(deviceToken)
          .collection('notifications')
          .doc(notificationDocId)
          .delete();

      debugPrint('✅ NotificationService: Notification deleted');
    } catch (e) {
      debugPrint('❌ NotificationService: Error deleting notification: $e');
    }
  }

  /// Determine icon type based on notification data
  static String getIconType(Map<String, dynamic> data) {
    final title = (data['title'] ?? '').toString().toLowerCase();
    final body = (data['body'] ?? '').toString().toLowerCase();
    
    if (title.contains('premium') || body.contains('premium')) {
      return 'premium';
    } else if (title.contains('profile') || body.contains('profile')) {
      return 'profile';
    } else if (title.contains('theme') || body.contains('theme')) {
      return 'theme';
    }
    return 'default';
  }

  /// Process FCM message and save notification
  static Future<void> processFCMMessage(RemoteMessage message) async {
    final notification = message.notification;
    final data = message.data;

    if (notification != null) {
      await saveNotification(
        title: notification.title ?? 'Notification',
        body: notification.body ?? '',
        link: data['link'] as String?,
        notificationId: message.messageId,
        data: data,
      );
    }
  }
}

