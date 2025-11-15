# Duplicate Notification Fix

## Problem
Notifications were appearing twice in the notification screen.

## Root Cause
The `processFCMMessage` method was being called multiple times for the same notification:
1. When notification is received in foreground (`onMessage`)
2. When user taps notification and app opens (`onMessageOpenedApp`)
3. When app is opened from terminated state (`getInitialMessage`)
4. When notification is received in background (`firebaseMessagingBackgroundHandler`)

Each call was saving the notification to Firestore without checking if it already existed, causing duplicates.

## Solution
Added duplicate checking in `saveNotification` method:

1. **Check for existing notification**: Before saving, the code now checks if a notification with the same `notificationId` (FCM `messageId`) already exists in Firestore
2. **Skip if duplicate**: If a notification with the same ID exists, it skips saving and logs a message
3. **Use messageId as unique identifier**: FCM provides a unique `messageId` for each notification, which is used to prevent duplicates

## Changes Made

### `lib/services/notification_service.dart`
- Added duplicate check before saving notifications
- Query Firestore to check if `notificationId` already exists
- Skip saving if duplicate is found
- Generate fallback ID if `messageId` is not available

## Testing
After this fix:
1. Receive a notification → Should save once
2. Tap the notification → Should not create duplicate
3. Open app from terminated state → Should not create duplicate
4. Receive notification in background → Should not create duplicate

## Firestore Index (Optional)
For better performance with large notification collections, you can add a composite index:

```json
{
  "indexes": [
    {
      "collectionGroup": "notifications",
      "queryScope": "COLLECTION",
      "fields": [
        {
          "fieldPath": "notificationId",
          "order": "ASCENDING"
        },
        {
          "fieldPath": "receivedAt",
          "order": "DESCENDING"
        }
      ]
    }
  ]
}
```

However, the current implementation should work fine without an index for most use cases since we're only querying by `notificationId` with a limit of 1.

## Notes
- The duplicate check uses the FCM `messageId` which is unique per notification
- If `messageId` is not available, a fallback ID is generated using timestamp and title hash
- This ensures each notification is only saved once, regardless of how many times `processFCMMessage` is called

