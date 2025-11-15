# Keystore Setup for Play Store Release

## Step 1: Generate a Keystore

Run the following command in the `android` directory:

```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

You will be prompted to enter:
- A password for the keystore (save this securely!)
- A password for the key alias (can be the same as keystore password)
- Your name, organization, city, state, and country code

**IMPORTANT**: Keep the keystore file (`keystore.jks`) and passwords secure. You'll need them for all future updates to your app on the Play Store.

## Step 2: Update key.properties

Edit `android/key.properties` and replace the placeholder values:

```
storePassword=YOUR_ACTUAL_KEYSTORE_PASSWORD
keyPassword=YOUR_ACTUAL_KEY_PASSWORD
keyAlias=upload
storeFile=../keystore.jks
```

## Step 3: Add keystore.jks to .gitignore

Make sure `android/keystore.jks` is in your `.gitignore` file to prevent accidentally committing your keystore to version control.

## Step 4: Build Release APK/AAB

Once the keystore is set up, you can build a release bundle:

```bash
flutter build appbundle --release
```

Or for an APK:

```bash
flutter build apk --release
```

The output will be in:
- AAB: `build/app/outputs/bundle/release/app-release.aab`
- APK: `build/app/outputs/flutter-apk/app-release.apk`

## Security Notes

- Never share your keystore file or passwords
- Store backups of your keystore in a secure location
- If you lose your keystore, you cannot update your app on the Play Store
- Consider using Google Play App Signing for additional security

