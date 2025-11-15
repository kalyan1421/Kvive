# Quick Setup: Add SHA-1 to Firebase for Google Sign-In

## Your SHA-1 Fingerprint

**Debug Keystore SHA-1:**
```
92:EE:F9:D9:B3:10:84:04:1E:5B:8B:DA:49:C3:18:D3:32:0F:FD:6F
```

## Steps to Fix Google Sign-In

### 1. Add SHA-1 to Firebase Console

1. Go to: https://console.firebase.google.com/project/aikeyboard-18ed9/settings/general
2. Scroll down to **"Your apps"** section
3. Find your Android app with package name `com.kvive.keyboard`
   - If it doesn't exist, click **"Add app"** → Android → Package name: `com.kvive.keyboard`
4. Click on the Android app
5. Scroll to **"SHA certificate fingerprints"**
6. Click **"Add fingerprint"**
7. Paste: `92:EE:F9:D9:B3:10:84:04:1E:5B:8B:DA:49:C3:18:D3:32:0F:FD:6F`
8. Click **"Save"**
9. **Download the updated `google-services.json`**:
   - Click the download icon (⬇️) next to your Android app
   - Replace `android/app/google-services.json` with the downloaded file

### 2. Update Google Cloud Console OAuth Client

1. Go to: https://console.cloud.google.com/apis/credentials?project=aikeyboard-18ed9
2. Find your OAuth 2.0 Client ID (or create a new Android OAuth client)
3. Click on it to edit
4. Under **"Authorized redirect URIs"** or **"Android"** section:
   - **Package name**: `com.kvive.keyboard`
   - **SHA-1 certificate fingerprint**: `92:EE:F9:D9:B3:10:84:04:1E:5B:8B:DA:49:C3:18:D3:32:0F:FD:6F`
5. Click **"Save"**

### 3. Test

After completing steps 1 and 2, wait 2-3 minutes for changes to propagate, then:

```bash
cd /Users/kalyan/Kvive/AI-keyboard
flutter clean
flutter run
```

Try Google Sign-In again - it should work now!

## For Release Builds

When you create your release keystore, you'll need to add its SHA-1 fingerprint as well:

```bash
# After creating release keystore
keytool -list -v -keystore android/keystore.jks -alias upload | grep SHA1
```

Then add that SHA-1 to Firebase Console and Google Cloud Console as well.

