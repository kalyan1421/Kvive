# Play Store Deployment Checklist

## Pre-Deployment Requirements

### 1. Application ID
**Current**: `com.example.ai_keyboard`

⚠️ **IMPORTANT**: The application ID is still using `com.example`. For production, you should change this to your own domain (e.g., `com.kvive.keyboard`). However, this requires:
- Updating Firebase configuration (`google-services.json`)
- Updating the package name in all Kotlin files
- Creating a new Firebase Android app with the new package name
- Updating all references in the codebase

**Recommendation**: Keep the current ID for now if Firebase is already configured, but plan to change it before first release.

### 2. Keystore Setup
- [ ] Generate keystore file (see `android/KEYSTORE_SETUP.md`)
- [ ] Update `android/key.properties` with your keystore credentials
- [ ] Verify keystore is NOT committed to git (already in `.gitignore`)

### 3. Version Information
**Current Version**: `1.0.0+1` (from `pubspec.yaml`)
- Version Name: `1.0.0` (user-facing version)
- Version Code: `1` (internal build number, must increment for each release)

### 4. App Information
- **App Name**: Kvive - Ai Writing Keyboard
- **Package Name**: com.example.ai_keyboard
- **Min SDK**: 23 (Android 6.0)
- **Target SDK**: 35 (Android 15)

### 5. Permissions Review
The app requests the following permissions:
- ✅ INTERNET
- ✅ ACCESS_NETWORK_STATE
- ✅ RECORD_AUDIO (for voice input)
- ✅ BLUETOOTH_CONNECT (for Bluetooth devices)
- ✅ READ_EXTERNAL_STORAGE
- ✅ WRITE_EXTERNAL_STORAGE
- ✅ CAMERA (for image cropping)
- ✅ READ_MEDIA_IMAGES (Android 13+)
- ✅ VIBRATE (for haptic feedback)

**Action Required**: Ensure all permissions have proper justification in Play Store listing.

### 6. Build Configuration
✅ Release signing configured
✅ ProGuard rules added
✅ Code shrinking enabled
✅ Resource shrinking enabled
✅ MultiDex enabled

### 7. Testing Checklist
- [ ] Test release build on multiple devices
- [ ] Test keyboard functionality
- [ ] Test voice input
- [ ] Test image cropping
- [ ] Test Firebase authentication
- [ ] Test notifications
- [ ] Test all permissions
- [ ] Verify no debug logs in release build
- [ ] Test app performance and memory usage

### 8. Play Store Assets Required
- [ ] App icon (512x512 PNG)
- [ ] Feature graphic (1024x500 PNG)
- [ ] Screenshots (at least 2, up to 8)
  - Phone: 16:9 or 9:16, min 320px, max 3840px
  - Tablet: 16:9 or 9:16, min 320px, max 3840px
- [ ] Short description (80 characters max)
- [ ] Full description (4000 characters max)
- [ ] Privacy Policy URL (required)
- [ ] Content rating questionnaire

### 9. Build Release Bundle

```bash
# Navigate to project root
cd /Users/kalyan/Kvive/AI-keyboard

# Clean previous builds
flutter clean

# Get dependencies
flutter pub get

# Build release bundle (recommended for Play Store)
flutter build appbundle --release

# Or build APK for testing
flutter build apk --release
```

Output location:
- AAB: `build/app/outputs/bundle/release/app-release.aab`
- APK: `build/app/outputs/flutter-apk/app-release.apk`

### 10. Upload to Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Create a new app or select existing app
3. Complete store listing information
4. Upload the AAB file
5. Complete content rating
6. Set up pricing and distribution
7. Review and publish

### 11. Post-Deployment

- [ ] Monitor crash reports in Play Console
- [ ] Monitor user reviews
- [ ] Set up staged rollouts (recommended: start with 20% of users)
- [ ] Monitor analytics

## Security Checklist

✅ **CRITICAL**: Hardcoded API keys have been removed from the codebase
✅ **CRITICAL**: Debug logging is now disabled in release builds (uses BuildConfig.DEBUG)
- [ ] Verify no API keys or secrets are hardcoded in the codebase
- [ ] Ensure all sensitive data is stored securely (SharedPreferences with encryption)
- [ ] Review all permissions and ensure they're necessary
- [ ] Test that debug logs don't appear in release builds

## Important Notes

1. **Keystore Security**: Never lose your keystore file or passwords. You cannot update your app without them.

2. **Version Code**: Each release must have a higher version code than the previous one.

3. **Testing**: Always test release builds thoroughly before publishing.

4. **Privacy Policy**: Required for apps that collect user data (Firebase, etc.)

5. **Content Rating**: Complete the questionnaire honestly - it affects app visibility.

6. **Staged Rollout**: Start with a small percentage of users to catch issues early.

7. **API Keys**: The app no longer hardcodes API keys. Users must configure their own OpenAI API key through the app settings.

## Troubleshooting

### Build Errors
- Ensure keystore file exists and `key.properties` is correctly configured
- Check that all dependencies are compatible
- Verify ProGuard rules don't break functionality

### Signing Issues
- Verify keystore passwords are correct
- Ensure keystore file path is correct in `key.properties`
- Check that keystore alias matches (`upload`)

### Size Issues
- Use AAB format (smaller than APK)
- Enable code and resource shrinking (already configured)
- Consider using Android App Bundle for dynamic delivery

