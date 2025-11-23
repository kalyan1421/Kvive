import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../firebase_options.dart';
import 'notification_service.dart';

/// Service to manage FCM token collection and storage
/// Saves tokens to Firestore so admin can send notifications to all devices
class FCMTokenService {
  static const String _devicesCollection = 'devices';
  static const String _prefsKey = 'fcm_token_saved';
  static final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  static final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  
  // Local notifications plugin for foreground notifications
  static final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();
  
  static bool _localNotificationsInitialized = false;

  /// Initialize FCM token collection
  /// Call this when the app starts
  static Future<void> initialize() async {
    try {
      // Initialize local notifications for foreground display
      await _initializeLocalNotifications();
      
      // Set background message handler
      FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

      // Permission request moved to requestNotificationPermission()


      // Subscribe to topic for broadcast notifications
      await _messaging.subscribeToTopic('all_users');
      debugPrint('✅ FCM: Subscribed to topic "all_users"');

      // Get and save token
      await _saveTokenToFirestore();

      // Listen for token refresh
      _messaging.onTokenRefresh.listen((newToken) {
        debugPrint('🔄 FCM: Token refreshed: $newToken');
        _saveTokenToFirestore(token: newToken);
      });

      // Handle foreground messages
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('📨 FCM: Received foreground message: ${message.notification?.title}');
        // Save notification to Firestore
        NotificationService.processFCMMessage(message);
        // Show local notification for foreground messages
        _showForegroundNotification(message);
      });

      // Handle background messages (when app is in background)
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('📨 FCM: Notification opened app: ${message.notification?.title}');
        // Save notification if not already saved
        NotificationService.processFCMMessage(message);
        // Handle navigation or deep linking here
        _handleNotificationTap(message);
      });

      // Check if app was opened from a terminated state
      final initialMessage = await _messaging.getInitialMessage();
      if (initialMessage != null) {
        debugPrint('📨 FCM: App opened from terminated state');
        // Save notification if not already saved
        NotificationService.processFCMMessage(initialMessage);
        _handleNotificationTap(initialMessage);
      }
    } catch (e) {
      debugPrint('❌ FCM: Error initializing: $e');
    }
  }

  /// Save FCM token to Firestore
  static Future<void> _saveTokenToFirestore({String? token}) async {
    try {
      final fcmToken = token ?? await _messaging.getToken();
      if (fcmToken == null) {
        debugPrint('⚠️ FCM: No token available');
        return;
      }

      // Check if we already saved this token
      final prefs = await SharedPreferences.getInstance();
      final savedToken = prefs.getString(_prefsKey);
      if (savedToken == fcmToken) {
        debugPrint('ℹ️ FCM: Token already saved, skipping');
        return;
      }

      // Get device info
      final deviceInfo = await _getDeviceInfo();

      // Save to Firestore
      await _firestore.collection(_devicesCollection).doc(fcmToken).set({
        'token': fcmToken,
        'createdAt': FieldValue.serverTimestamp(),
        'updatedAt': FieldValue.serverTimestamp(),
        'platform': deviceInfo['platform'],
        'appVersion': deviceInfo['appVersion'],
        'lastActive': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));

      // Save to local prefs
      await prefs.setString(_prefsKey, fcmToken);
      debugPrint('✅ FCM: Token saved to Firestore: $fcmToken');
    } catch (e) {
      debugPrint('❌ FCM: Error saving token: $e');
    }
  }

  /// Get device information
  static Future<Map<String, String>> _getDeviceInfo() async {
    // You can use device_info_plus package for more detailed info
    return {
      'platform': defaultTargetPlatform.name,
      'appVersion': '1.0.0', // Replace with actual version
    };
  }

  /// Handle notification tap
  static void _handleNotificationTap(RemoteMessage message) {
    final data = message.data;
    final link = data['link'] as String?;
    
    if (link != null && link.isNotEmpty) {
      debugPrint('🔗 FCM: Opening deep link: $link');
      // Handle deep linking here
      // You can use go_router, navigator, or any routing solution
    }
  }

  /// Unsubscribe from topic (if needed)
  static Future<void> unsubscribeFromTopic(String topic) async {
    try {
      await _messaging.unsubscribeFromTopic(topic);
      debugPrint('✅ FCM: Unsubscribed from topic: $topic');
    } catch (e) {
      debugPrint('❌ FCM: Error unsubscribing: $e');
    }
  }

  /// Delete token from Firestore (e.g., on logout)
  static Future<void> deleteToken() async {
    try {
      final fcmToken = await _messaging.getToken();
      if (fcmToken != null) {
        await _firestore.collection(_devicesCollection).doc(fcmToken).delete();
        final prefs = await SharedPreferences.getInstance();
        await prefs.remove(_prefsKey);
        debugPrint('✅ FCM: Token deleted from Firestore');
      }
    } catch (e) {
      debugPrint('❌ FCM: Error deleting token: $e');
    }
  }
  
  /// Initialize local notifications plugin
  static Future<void> _initializeLocalNotifications() async {
    if (_localNotificationsInitialized) return;
    
    try {
      const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
      const iosSettings = DarwinInitializationSettings(
        requestAlertPermission: true,
        requestBadgePermission: true,
        requestSoundPermission: true,
      );
      
      const initSettings = InitializationSettings(
        android: androidSettings,
        iOS: iosSettings,
      );
      
      await _localNotifications.initialize(
        initSettings,
        onDidReceiveNotificationResponse: (details) {
          debugPrint('📨 Local notification tapped: ${details.payload}');
          // Handle notification tap if needed
        },
      );
      
      _localNotificationsInitialized = true;
      debugPrint('✅ FCM: Local notifications initialized');
    } catch (e) {
      debugPrint('❌ FCM: Error initializing local notifications: $e');
    }
  }
  
  /// Show local notification for foreground messages
  static Future<void> _showForegroundNotification(RemoteMessage message) async {
    if (!_localNotificationsInitialized) {
      await _initializeLocalNotifications();
    }
    
    try {
      final notification = message.notification;
      if (notification == null) return;
      
      const androidDetails = AndroidNotificationDetails(
        'default',
        'Default Notifications',
        channelDescription: 'Default notification channel for app notifications',
        importance: Importance.high,
        priority: Priority.high,
        showWhen: true,
      );
      
      const iosDetails = DarwinNotificationDetails(
        presentAlert: true,
        presentBadge: true,
        presentSound: true,
      );
      
      const notificationDetails = NotificationDetails(
        android: androidDetails,
        iOS: iosDetails,
      );
      
      await _localNotifications.show(
        message.hashCode,
        notification.title ?? 'Notification',
        notification.body ?? '',
        notificationDetails,
        payload: message.data['link'] as String?,
      );
      
      debugPrint('✅ FCM: Foreground notification displayed');
    } catch (e) {
      debugPrint('❌ FCM: Error showing foreground notification: $e');
    }
  }


  /// Request permission for notifications
  /// Call this when you want to ask the user for permission (e.g., on Home Screen)
  static Future<void> requestNotificationPermission() async {
    if (kIsWeb) return;

    try {
      final settings = await _messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        provisional: false,
      );

      if (settings.authorizationStatus == AuthorizationStatus.authorized) {
        debugPrint('✅ FCM: User granted notification permission');
      } else if (settings.authorizationStatus == AuthorizationStatus.provisional) {
        debugPrint('⚠️ FCM: User granted provisional notification permission');
      } else {
        debugPrint('❌ FCM: User declined or has not accepted notification permission');
      }
    } catch (e) {
      debugPrint('❌ FCM: Error requesting permission: $e');
    }
  }
}

/// Background message handler (must be top-level function)
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  debugPrint('📨 FCM: Background message received: ${message.notification?.title}');
  
  // Initialize Firebase in the background isolate before using Firestore
  // In background isolates, Firebase is not initialized by default
  try {
    // Check if Firebase is already initialized
    try {
      Firebase.app();
      debugPrint('✅ FCM: Firebase already initialized in background handler');
    } catch (e) {
      // Firebase not initialized, initialize it now
      try {
        await Firebase.initializeApp(
          options: DefaultFirebaseOptions.currentPlatform,
        );
        debugPrint('✅ FCM: Firebase initialized in background handler');
      } catch (initError) {
        // Handle duplicate app error gracefully
        if (initError.toString().contains('duplicate-app')) {
          debugPrint('✅ FCM: Firebase already initialized (duplicate-app caught)');
        } else {
          debugPrint('❌ FCM: Failed to initialize Firebase: $initError');
          return;
        }
      }
    }
  } catch (e) {
    debugPrint('❌ FCM: Unexpected error checking Firebase: $e');
    // Try to proceed anyway - Firebase might be initialized
  }
  
  // Save notification to Firestore (now that Firebase is guaranteed to be initialized)
  try {
    await NotificationService.processFCMMessage(message);
  } catch (e) {
    debugPrint('❌ FCM: Error processing notification: $e');
  }
}

