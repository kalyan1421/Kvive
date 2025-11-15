import 'dart:async';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart';

/// DictionaryCloudSync: Sync user's personal dictionary across devices
/// Implements local + cloud dictionary sync with debouncing
class DictionaryCloudSync {
  static final _firestore = FirebaseFirestore.instance;
  static const String _tag = 'DictionaryCloudSync';
  
  // Debouncing: batch writes to avoid excessive Firestore writes
  static Timer? _writeDebounceTimer;
  static const _writeDebounceDuration = Duration(seconds: 5);
  static final _pendingWrites = <String, List<String>>{};
  
  /// Sync user dictionary for a specific locale to Firestore
  static Future<void> syncDictionary(String locale, List<String> words) async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) {
      debugPrint('$_tag: Cannot sync - no user logged in');
      return;
    }
    
    // Add to pending writes
    _pendingWrites[locale] = words;
    
    // Cancel existing timer and restart
    _writeDebounceTimer?.cancel();
    _writeDebounceTimer = Timer(_writeDebounceDuration, () {
      _flushPendingWrites();
    });
  }
  
  /// Flush all pending dictionary writes to Firestore
  static Future<void> _flushPendingWrites() async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null || _pendingWrites.isEmpty) return;
    
    try {
      final batch = _firestore.batch();
      
      _pendingWrites.forEach((locale, words) {
        final docRef = _firestore
            .collection('users')
            .doc(user.uid)
            .collection('dictionary')
            .doc(locale);
        
        batch.set(docRef, {
          'locale': locale,
          'words': words,
          'updatedAt': FieldValue.serverTimestamp(),
        });
      });
      
      await batch.commit();
      debugPrint('$_tag: ✓ Synced ${_pendingWrites.length} locale(s) to Firestore');
      _pendingWrites.clear();
      
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to sync dictionary: $e');
    }
  }
  
  /// Add a single word to dictionary and sync
  static Future<void> addWord(String locale, String word) async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return;
    
    try {
      final docRef = _firestore
          .collection('users')
          .doc(user.uid)
          .collection('dictionary')
          .doc(locale);
      
      await docRef.set({
        'locale': locale,
        'words': FieldValue.arrayUnion([word]),
        'updatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));
      
      debugPrint('$_tag: ✓ Added "$word" to $locale dictionary');
      
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to add word: $e');
    }
  }
  
  /// Remove a word from dictionary and sync
  static Future<void> removeWord(String locale, String word) async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return;
    
    try {
      final docRef = _firestore
          .collection('users')
          .doc(user.uid)
          .collection('dictionary')
          .doc(locale);
      
      await docRef.update({
        'words': FieldValue.arrayRemove([word]),
        'updatedAt': FieldValue.serverTimestamp(),
      });
      
      debugPrint('$_tag: ✓ Removed "$word" from $locale dictionary');
      
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to remove word: $e');
    }
  }
  
  /// Load dictionary for a locale from Firestore
  static Future<List<String>?> loadDictionary(String locale) async {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return null;
    
    try {
      final doc = await _firestore
          .collection('users')
          .doc(user.uid)
          .collection('dictionary')
          .doc(locale)
          .get();
      
      if (!doc.exists) {
        debugPrint('$_tag: No dictionary found for locale: $locale');
        return null;
      }
      
      final data = doc.data();
      final words = (data?['words'] as List<dynamic>?)
          ?.map((e) => e.toString())
          .toList();
      
      debugPrint('$_tag: ✓ Loaded ${words?.length ?? 0} words for $locale');
      return words;
      
    } catch (e) {
      debugPrint('$_tag: ✗ Failed to load dictionary: $e');
      return null;
    }
  }
  
  /// Listen for remote dictionary changes for a locale
  static StreamSubscription<DocumentSnapshot>? listenToDictionary(
    String locale,
    Function(List<String>) onUpdate,
  ) {
    final user = FirebaseAuth.instance.currentUser;
    if (user == null) return null;
    
    return _firestore
        .collection('users')
        .doc(user.uid)
        .collection('dictionary')
        .doc(locale)
        .snapshots()
        .listen(
          (doc) {
            if (!doc.exists) return;
            
            final data = doc.data();
            final words = (data?['words'] as List<dynamic>?)
                ?.map((e) => e.toString())
                .toList() ?? [];
            
            debugPrint('$_tag: Remote dictionary update for $locale: ${words.length} words');
            onUpdate(words);
          },
          onError: (error) {
            debugPrint('$_tag: Error listening to dictionary: $error');
          },
        );
  }
  
  /// Merge local and remote dictionaries (union)
  static List<String> mergeDictionaries(
    List<String> local,
    List<String> remote,
  ) {
    return {...local, ...remote}.toList()..sort();
  }
  
  /// Force flush any pending writes immediately
  static Future<void> flush() async {
    _writeDebounceTimer?.cancel();
    await _flushPendingWrites();
  }
}

