import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:flutter/foundation.dart';

class FirebaseAuthService {
  static final FirebaseAuthService _instance = FirebaseAuthService._internal();
  factory FirebaseAuthService() => _instance;
  FirebaseAuthService._internal();

  final FirebaseAuth _firebaseAuth = FirebaseAuth.instance;
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn(
    // Use serverClientId for Android (Web client ID from Firebase Console)
    // This is the OAuth 2.0 Web client ID, not the Android client ID
    serverClientId: "621863637081-glee7m3vo4e73g84lss259507bklkm2b.apps.googleusercontent.com",
  );

  // Get current user
  User? get currentUser => _firebaseAuth.currentUser;

  // Auth state stream
  Stream<User?> get authStateChanges => _firebaseAuth.authStateChanges();

  // Sign up with email and password
  Future<UserCredential?> signUpWithEmailPassword({
    required String email,
    required String password,
    required String displayName,
  }) async {
    try {
      print('🔵 [EmailAuth] Starting email signup for: $email');
      
      UserCredential userCredential = await _firebaseAuth.createUserWithEmailAndPassword(
        email: email,
        password: password,
      );

      print('🟢 [EmailAuth] User created successfully: ${userCredential.user?.uid}');

      // Update display name
      await userCredential.user?.updateDisplayName(displayName);
      print('🔵 [EmailAuth] Display name updated to: $displayName');

      // Save user data to Firestore
      await _saveUserToFirestore(userCredential.user!, displayName, isNewUser: true);

      return userCredential;
    } on FirebaseAuthException catch (e) {
      print('🔴 [EmailAuth] Signup failed with code: ${e.code}');
      throw _handleAuthException(e);
    } catch (e) {
      print('🔴 [EmailAuth] Unexpected signup error: $e');
      rethrow;
    }
  }

  // Sign in with email and password
  Future<UserCredential?> signInWithEmailPassword({
    required String email,
    required String password,
  }) async {
    try {
      print('🔵 [EmailAuth] Starting email signin for: $email');
      
      UserCredential userCredential = await _firebaseAuth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );
      
      print('🟢 [EmailAuth] User signed in successfully: ${userCredential.user?.uid}');
      
      // Update last sign-in time
      if (userCredential.user != null) {
        await _saveUserToFirestore(
          userCredential.user!, 
          userCredential.user!.displayName ?? userCredential.user!.email?.split('@').first ?? 'User',
          isNewUser: false,
        );
      }
      
      return userCredential;
    } on FirebaseAuthException catch (e) {
      print('🔴 [EmailAuth] Signin failed with code: ${e.code}');
      throw _handleAuthException(e);
    } catch (e) {
      print('🔴 [EmailAuth] Unexpected signin error: $e');
      rethrow;
    }
  }

  // Sign in with Google
  Future<UserCredential?> signInWithGoogle() async {
    try {
      // Firebase already initialized in main.dart, so just trust it
      print('🔵 [GoogleAuth] Starting Google Sign-In flow...');
      
      // Step 1: Trigger Google account selection
      print('🔵 [GoogleAuth] Step 1: Triggering Google account selection...');
      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();
      
      // Enhanced null-safety handling for GoogleSignInAccount
      if (googleUser == null) {
        print('🟡 [GoogleAuth] Step 1 Result: User cancelled Google account selection');
        return null; // User canceled the sign-in
      }
      
      print('🟢 [GoogleAuth] Step 1 Success: Google account selected');
      print('🔵 [GoogleAuth] Selected account: ${googleUser.email}');
      print('🔵 [GoogleAuth] Account ID: ${googleUser.id}');
      print('🔵 [GoogleAuth] Display Name: ${googleUser.displayName ?? 'No display name'}');

      // Step 2: Obtain authentication tokens
      print('🔵 [GoogleAuth] Step 2: Retrieving authentication tokens...');
      GoogleSignInAuthentication? googleAuth;
      
      try {
        googleAuth = await googleUser.authentication;
        print('🟢 [GoogleAuth] Step 2 Success: Authentication tokens retrieved');
      } catch (e) {
        print('🔴 [GoogleAuth] Step 2 Failed: Error retrieving tokens - $e');
        throw Exception('Failed to retrieve Google authentication tokens: $e');
      }
      
      // GoogleSignInAuthentication is non-nullable, so this check is unnecessary
      // but we keep it for explicit validation in case of unexpected behavior
      
      // Validate tokens with detailed logging
      print('🔵 [GoogleAuth] Validating authentication tokens...');
      final accessToken = googleAuth.accessToken;
      final idToken = googleAuth.idToken;
      
      print('🔵 [GoogleAuth] Access Token: ${accessToken != null ? 'Present (${accessToken.length} chars)' : 'NULL'}');
      print('🔵 [GoogleAuth] ID Token: ${idToken != null ? 'Present (${idToken.length} chars)' : 'NULL'}');
      
      if (accessToken == null || idToken == null) {
        print('🔴 [GoogleAuth] Step 2 Failed: Missing authentication tokens');
        print('🔴 [GoogleAuth] Access Token: $accessToken');
        print('🔴 [GoogleAuth] ID Token: $idToken');
        throw Exception('Failed to obtain Google authentication tokens - accessToken: ${accessToken != null}, idToken: ${idToken != null}');
      }
      
      // Step 3: Create Firebase credential
      print('🔵 [GoogleAuth] Step 3: Creating Firebase credential...');
      late final AuthCredential credential;
      
      try {
        credential = GoogleAuthProvider.credential(
          accessToken: accessToken,
          idToken: idToken,
        );
        print('🟢 [GoogleAuth] Step 3 Success: Firebase credential created');
      } catch (e) {
        print('🔴 [GoogleAuth] Step 3 Failed: Error creating credential - $e');
        throw Exception('Failed to create Firebase credential: $e');
      }

      // Step 4: Sign in to Firebase
      print('🔵 [GoogleAuth] Step 4: Signing in to Firebase...');
      late final UserCredential userCredential;
      
      try {
        userCredential = await _firebaseAuth.signInWithCredential(credential);
        print('🟢 [GoogleAuth] Step 4 Success: Firebase sign-in completed');
      } catch (e) {
        print('🔴 [GoogleAuth] Step 4 Failed: Firebase sign-in error - $e');
        if (e is FirebaseAuthException) {
          print('🔴 [GoogleAuth] Firebase Auth Error Code: ${e.code}');
          print('🔴 [GoogleAuth] Firebase Auth Error Message: ${e.message}');
        }
        rethrow;
      }
      
      // Enhanced null-safety handling for Firebase User
      final User? firebaseUser = userCredential.user;
      if (firebaseUser == null) {
        print('🔴 [GoogleAuth] Step 4 Failed: Firebase user is null after sign-in');
        throw Exception('Firebase authentication succeeded but user object is null');
      }

      print('🟢 [GoogleAuth] Step 4 Success: Firebase User object validated');
      print('🔵 [GoogleAuth] Firebase User UID: ${firebaseUser.uid}');
      print('🔵 [GoogleAuth] Firebase User Email: ${firebaseUser.email ?? 'No email'}');
      print('🔵 [GoogleAuth] Firebase User DisplayName: ${firebaseUser.displayName ?? 'No display name'}');
      print('🔵 [GoogleAuth] Firebase User PhotoURL: ${firebaseUser.photoURL ?? 'No photo URL'}');
      print('🔵 [GoogleAuth] Is New User: ${userCredential.additionalUserInfo?.isNewUser ?? 'Unknown'}');

      // Step 5: Save user data to Firestore (only after successful Firebase sign-in)
      print('🔵 [GoogleAuth] Step 5: Saving user data to Firestore...');
      
      // Prepare safe display name with null-safety
      final String safeDisplayName = firebaseUser.displayName?.trim().isNotEmpty == true
          ? firebaseUser.displayName!
          : firebaseUser.email?.split('@').first ?? 'User';
      
      print('🔵 [GoogleAuth] Using display name: "$safeDisplayName"');
      
      try {
        await _saveUserToFirestore(
          firebaseUser,
          safeDisplayName,
          isNewUser: userCredential.additionalUserInfo?.isNewUser ?? false,
        );
        print('🟢 [GoogleAuth] Step 5 Success: User data saved to Firestore');
      } catch (e) {
        print('🔴 [GoogleAuth] Step 5 Warning: Firestore save failed - $e');
        // Don't throw here - user is still authenticated even if Firestore fails
        print('🟡 [GoogleAuth] Continuing with authentication despite Firestore error');
      }

      print('🟢 [GoogleAuth] Google Sign-In flow completed successfully');
      print('🟢 [GoogleAuth] User ${firebaseUser.email} is now authenticated');
      
      return userCredential;
      
    } on FirebaseAuthException catch (e) {
      print('🔴 [GoogleAuth] Firebase Auth Exception:');
      print('🔴 [GoogleAuth] Error Code: ${e.code}');
      print('🔴 [GoogleAuth] Error Message: ${e.message}');
      print('🔴 [GoogleAuth] Error Details: ${e.toString()}');
      throw _handleAuthException(e);
    } on Exception catch (e) {
      print('🔴 [GoogleAuth] General Exception: ${e.toString()}');
      print('🔴 [GoogleAuth] Exception Type: ${e.runtimeType}');
      throw Exception('Google sign-in failed: ${e.toString()}');
    } catch (e) {
      print('🔴 [GoogleAuth] Unexpected Error: ${e.toString()}');
      print('🔴 [GoogleAuth] Error Type: ${e.runtimeType}');
      throw Exception('An unexpected error occurred during Google sign-in: ${e.toString()}');
    }
  }

  // Sign out
  Future<void> signOut() async {
    try {
      print('🔵 [Auth] Starting sign out process...');
      
      await Future.wait([
        _firebaseAuth.signOut(),
        _googleSignIn.signOut(),
      ]);
      
      print('🟢 [Auth] Sign out completed successfully');
    } catch (e) {
      print('🔴 [Auth] Sign out failed: $e');
      throw Exception('Sign out failed: $e');
    }
  }

  // Reset password
  Future<void> resetPassword(String email) async {
    try {
      await _firebaseAuth.sendPasswordResetEmail(email: email);
    } on FirebaseAuthException catch (e) {
      throw _handleAuthException(e);
    }
  }

  // Save user data to Firestore
  Future<void> _saveUserToFirestore(User user, String displayName, {required bool isNewUser}) async {
    try {
      // Validate inputs before Firestore operations
      if (user.uid.isEmpty) {
        print('🔴 [Firestore] Error: User UID is empty');
        throw Exception('User UID is empty - cannot save to Firestore');
      }
      
      print('🔵 [Firestore] Starting Firestore write operation...');
      print('🔵 [Firestore] User UID: ${user.uid}');
      print('🔵 [Firestore] Display Name: "$displayName"');
      print('🔵 [Firestore] Email: ${user.email ?? 'No email'}');
      print('🔵 [Firestore] Photo URL: ${user.photoURL ?? 'No photo URL'}');
      print('🔵 [Firestore] Is New User: $isNewUser');
      
      final userDoc = _firestore.collection('users').doc(user.uid);
      
      if (isNewUser) {
        // For new users, create complete profile with null-safe data
        print('🔵 [Firestore] Creating new user profile...');
        
        final userData = {
          'uid': user.uid,
          'email': user.email ?? '', // Handle null email
          'displayName': displayName.isNotEmpty ? displayName : 'User',
          'photoURL': user.photoURL, // Can be null, Firestore handles it
          'createdAt': FieldValue.serverTimestamp(),
          'lastSignIn': FieldValue.serverTimestamp(),
          'provider': 'google.com',
          'emailVerified': user.emailVerified,
          'keyboardSettings': {
            'theme': 'default',
            'soundEnabled': true,
            'hapticEnabled': true,
            'autoCorrectEnabled': true,
            'predictiveTextEnabled': true,
            'aiSuggestionsEnabled': true,
            'swipeTypingEnabled': true,
            'vibrationEnabled': false, // ✅ Default OFF until user enables
            'keyPreviewEnabled': false,
            'shiftFeedbackEnabled': false,
            'showNumberRow': false,
            'currentLanguage': 'EN',
            'hapticIntensity': 'medium',
            'soundIntensity': 'light',
            'visualIntensity': 'medium',
            'soundVolume': 0.3,
          },
        };
        
        print('🔵 [Firestore] Writing new user document...');
        await userDoc.set(userData);
        print('🟢 [Firestore] New user profile created successfully');
        print('🔵 [Firestore] Document path: users/${user.uid}');
        
      } else {
        // For existing users, only update sign-in info and profile data
        print('🔵 [Firestore] Updating existing user profile...');
        
        final updateData = {
          'displayName': user.displayName ?? (displayName.isNotEmpty ? displayName : 'User'),
          'email': user.email,
          'photoURL': user.photoURL,
          'lastLogin': FieldValue.serverTimestamp(),
          'emailVerified': user.emailVerified,
        };
        
        print('🔵 [Firestore] Writing user update...');
        await userDoc.set(updateData, SetOptions(merge: true));
        print('🟢 [Firestore] Existing user sign-in updated successfully');
        print('🔵 [Firestore] Document path: users/${user.uid}');
      }
      
      print('🟢 [Firestore] Firestore write operation completed successfully');
      
    } catch (e) {
      print('🔴 [Firestore] Firestore write operation failed:');
      print('🔴 [Firestore] Error Type: ${e.runtimeType}');
      print('🔴 [Firestore] User UID: ${user.uid}');
      print('🔴 [Firestore] Display Name: "$displayName"');
      print('🔴 [Firestore] Is New User: $isNewUser');
      debugPrint('🔴 [Firestore] Full error details: $e');
      
      // Don't throw here to avoid breaking the auth flow
      // The user is still authenticated even if Firestore fails
      print('🟡 [Firestore] Authentication will continue despite Firestore error');
    }
  }

  // Get user data from Firestore
  Future<Map<String, dynamic>?> getUserData(String uid) async {
    try {
      DocumentSnapshot doc = await _firestore.collection('users').doc(uid).get();
      if (doc.exists) {
        return doc.data() as Map<String, dynamic>?;
      }
      return null;
    } catch (e) {
      print('Error getting user data: $e');
      return null;
    }
  }

  // Update user keyboard settings
  Future<void> updateKeyboardSettings(String uid, Map<String, dynamic> settings) async {
    try {
      print('🔵 [Firestore] Updating keyboard settings...');
      await _firestore.collection('users').doc(uid).set({
        'keyboardSettings': settings,
        'updatedAt': FieldValue.serverTimestamp(),
      }, SetOptions(merge: true));
      print('🟢 [Firestore] Keyboard settings updated successfully');
      print('🔵 [Firestore] Document path: users/$uid');
    } catch (e) {
      print('🔴 [Firestore] Error updating keyboard settings:');
      debugPrint('🔴 [Firestore] Full error details: $e');
    }
  }

  // Save user typing data for analytics
  Future<void> saveTypingData({
    required String uid,
    required Map<String, dynamic> typingData,
  }) async {
    try {
      await _firestore.collection('users').doc(uid).collection('typingData').add({
        ...typingData,
        'timestamp': FieldValue.serverTimestamp(),
      });
    } catch (e) {
      print('Error saving typing data: $e');
    }
  }

  // Handle Firebase Auth exceptions
  String _handleAuthException(FirebaseAuthException e) {
    print('🔴 [FirebaseAuth] Error code: ${e.code}, message: ${e.message}');
    
    switch (e.code) {
      case 'weak-password':
        return 'The password provided is too weak.';
      case 'email-already-in-use':
        return 'The account already exists for that email.';
      case 'user-not-found':
        return 'No user found for that email.';
      case 'wrong-password':
        return 'Wrong password provided for that user.';
      case 'invalid-email':
        return 'The email address is not valid.';
      case 'user-disabled':
        return 'This user account has been disabled.';
      case 'too-many-requests':
        return 'Too many requests. Try again later.';
      case 'operation-not-allowed':
        return 'Signing in with Email and Password is not enabled.';
      case 'account-exists-with-different-credential':
        return 'An account already exists with the same email address but different sign-in credentials.';
      case 'invalid-credential':
        return 'The supplied auth credential is malformed or has expired.';
      case 'credential-already-in-use':
        return 'This credential is already associated with a different user account.';
      case 'requires-recent-login':
        return 'This operation requires recent authentication. Please sign in again.';
      case 'provider-already-linked':
        return 'The provider has already been linked to the user.';
      case 'no-such-provider':
        return 'The provider is not linked to the user.';
      case 'invalid-user-token':
        return 'The user token is invalid.';
      case 'network-request-failed':
        return 'A network error occurred. Please check your connection.';
      case 'app-not-authorized':
        return 'This app is not authorized to use Firebase Authentication.';
      default:
        return e.message ?? 'An unexpected authentication error occurred.';
    }
  }
}
