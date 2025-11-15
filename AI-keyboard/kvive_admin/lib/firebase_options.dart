// ignore_for_file: require_trailing_commas, lines_longer_than_80_chars

import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;

class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      case TargetPlatform.iOS:
        return ios;
      case TargetPlatform.macOS:
        return macos;
      case TargetPlatform.windows:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for windows.',
        );
      case TargetPlatform.linux:
        throw UnsupportedError(
          'DefaultFirebaseOptions have not been configured for linux.',
        );
      default:
        throw UnsupportedError(
          'DefaultFirebaseOptions are not supported for this platform.',
        );
    }
  }

  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyBRciqSEqv99adE8jNbjp-QUxPRau_LhBY',
    appId: '1:621863637081:web:9a31548e579e7c8bbd2148',
    messagingSenderId: '621863637081',
    projectId: 'aikeyboard-18ed9',
    authDomain: 'aikeyboard-18ed9.firebaseapp.com',
    storageBucket: 'aikeyboard-18ed9.firebasestorage.app',
    measurementId: 'G-8YP9B82RTW',
  );

  /// Update these values with the app ids created for KVive Admin.

  static const FirebaseOptions android = FirebaseOptions(
    apiKey: 'AIzaSyB9sxIJOWwgc1K-vMXgultq3C-2izZOjjA',
    appId: '1:621863637081:android:58116233c5b060dbbd2148',
    messagingSenderId: '621863637081',
    projectId: 'aikeyboard-18ed9',
    storageBucket: 'aikeyboard-18ed9.firebasestorage.app',
  );

  static const FirebaseOptions ios = FirebaseOptions(
    apiKey: 'AIzaSyBycMQekSaAYjot2NzTh0aJL_SfEjkrwTU',
    appId: '1:621863637081:ios:7e7e5b15e9c6cac8bd2148',
    messagingSenderId: '621863637081',
    projectId: 'aikeyboard-18ed9',
    storageBucket: 'aikeyboard-18ed9.firebasestorage.app',
    iosBundleId: 'com.example.aiKeyboard',
  );

  static const FirebaseOptions macos = FirebaseOptions(
    apiKey: 'AIzaSyBycMQekSaAYjot2NzTh0aJL_SfEjkrwTU',
    appId: '1:621863637081:ios:7e7e5b15e9c6cac8bd2148',
    messagingSenderId: '621863637081',
    projectId: 'aikeyboard-18ed9',
    storageBucket: 'aikeyboard-18ed9.firebasestorage.app',
    iosBundleId: 'com.example.aiKeyboard',
  );
}