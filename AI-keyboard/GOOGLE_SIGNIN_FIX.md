# Fix Google Sign-In Error Code 10 (DEVELOPER_ERROR)

## Problem
After changing the package name from `com.example.ai_keyboard` to `com.kvive.keyboard`, Google Sign-In fails with error code 10.

## Root Cause
Error code 10 (`DEVELOPER_ERROR`) typically means:
1. SHA-1 fingerprint not registered in Firebase Console
2. Package name mismatch in Firebase configuration
3. OAuth client ID not properly configured

## Solution

### Step 1: Get Your Debug Keystore SHA-1 Fingerprint

✅ **Your SHA-1 fingerprint has been retrieved:**

```
SHA1: 92:EE:F9:D9:B3:10:84:04:1E:5B:8B:DA:49:C3:18:D3:32:0F:FD:6F
```

**Copy this SHA-1 fingerprint** - you'll need it in the next steps.

To get it again manually, run:
```bash
cd /Users/kalyan/Kvive/AI-keyboard/android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew signingReport
```

Or using keytool directly:
```bash
/opt/homebrew/opt/openjdk@17/bin/keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

### Step 2: Update Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `aikeyboard-18ed9`
3. Go to **Project Settings** (gear icon) → **Your apps**
4. Find your Android app or add a new one:
   - If adding new: Click "Add app" → Android
   - Package name: `com.kvive.keyboard`
5. **Add SHA certificate fingerprint**:
   - Scroll down to "SHA certificate fingerprints"
   - Click "Add fingerprint"
   - Paste your SHA-1 fingerprint from Step 1
   - Click "Save"
6. **Download the updated `google-services.json`**:
   - Click the download icon next to your Android app
   - Replace `android/app/google-services.json` with the new file

### Step 3: Update OAuth Client Configuration

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select project: `aikeyboard-18ed9`
3. Go to **APIs & Services** → **Credentials**
4. Find your OAuth 2.0 Client ID (or create one):
   - Application type: **Android**
   - Package name: `com.kvive.keyboard`
   - SHA-1 certificate fingerprint: (paste from Step 1)
5. Save the configuration

### Step 4: Update Flutter Code (Optional - Fix Warning)

The warning says to use `serverClientId` instead of `clientId` for Android. Update `lib/services/firebase_auth_service.dart`:

```dart
final GoogleSignIn _googleSignIn = GoogleSignIn(
  // Use serverClientId for Android (Web client ID from Firebase)
  serverClientId: "621863637081-glee7m3vo4e73g84lss259507bklkm2b.apps.googleusercontent.com",
);
```

**Note**: The `clientId` you're using is actually the Web client ID, which should be used as `serverClientId` on Android.

### Step 5: Clean and Rebuild

```bash
cd /Users/kalyan/Kvive/AI-keyboard
flutter clean
flutter pub get
flutter run
```

## For Release Builds

When you create your release keystore, you'll need to:

1. Get the release keystore SHA-1:
   ```bash
   keytool -list -v -keystore android/keystore.jks -alias upload
   ```

2. Add the release SHA-1 to Firebase Console (same process as Step 2)

3. Add the release SHA-1 to Google Cloud Console OAuth client

## Verification

After completing these steps:
1. The Google Sign-In should work without error code 10
2. The warning about `clientId` vs `serverClientId` should disappear
3. Users should be able to sign in with their Google accounts

## Troubleshooting

If it still doesn't work:

1. **Verify package name matches**:
   - Check `android/app/build.gradle.kts`: `applicationId = "com.kvive.keyboard"`
   - Check `google-services.json`: `"package_name": "com.kvive.keyboard"`

2. **Verify SHA-1 is correct**:
   - Make sure you're using the SHA-1 from the keystore you're actually using
   - Debug builds use `~/.android/debug.keystore`
   - Release builds use your custom `keystore.jks`

3. **Wait a few minutes**:
   - Firebase/Google Cloud changes can take a few minutes to propagate

4. **Check OAuth consent screen**:
   - Make sure your OAuth consent screen is configured in Google Cloud Console

## Quick Command Reference

```bash
# Get debug keystore SHA-1
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1

# Get release keystore SHA-1 (after creating it)
keytool -list -v -keystore android/keystore.jks -alias upload | grep SHA1
```

