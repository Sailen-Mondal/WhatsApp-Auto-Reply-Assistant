# Phase 1 Testing Guide

## Prerequisites

1. **Android Device/Emulator** (Android 8.0+ / API 26+)
2. **WhatsApp installed** on the device
3. **Android Studio** (Hedgehog or later recommended)

## Step 1: Build the Project

### Option A: Using Android Studio (Recommended)

1. Open Android Studio
2. Click "Open" and select the `/home/sailen/Projects` directory
3. Wait for Gradle sync to complete
4. If prompted, accept the Gradle wrapper download
5. Build the project: `Build > Make Project` (or `Ctrl+F9` / `Cmd+F9`)

### Option B: Using Command Line

```bash
cd /home/sailen/Projects
./gradlew build
```

**Note**: If you get permission errors, make the gradlew script executable:
```bash
chmod +x gradlew
```

## Step 2: Install on Device

1. Connect your Android device via USB (or use an emulator)
2. Enable USB debugging on your device
3. In Android Studio: `Run > Run 'app'` (or `Shift+F10`)
4. Or from command line: `./gradlew installDebug`

## Step 3: Grant Notification Access Permission

**This is critical!** The app won't work without this permission.

1. After installing, the app will show a permission screen
2. Click "Open Settings"
3. In the Notification access settings:
   - Find "WhatsApp Auto Reply" in the list
   - Toggle it **ON**
4. Go back to the app
5. Click "I've granted the permission"

**Alternative method:**
- Go to: Settings → Apps → Special app access → Notification access
- Enable "WhatsApp Auto Reply"

## Step 4: Testing Scenarios

### Test 1: Basic Text Message (Incoming)

1. Have someone send you a WhatsApp message (or use another device)
2. Wait a few seconds
3. Open the app
4. **Expected**: 
   - Chat should appear in the chat list
   - Chat title should match the contact name
   - Last message timestamp should be recent
5. Tap on the chat
6. **Expected**:
   - Message should appear in the chat detail screen
   - Message should be on the left (incoming)
   - Text should match the WhatsApp message

### Test 2: Outgoing Message

1. Send a message from WhatsApp
2. Open the app
3. **Expected**:
   - If detected, outgoing message should appear on the right side
   - Note: Outgoing detection may not always work perfectly

### Test 3: Multiple Messages

1. Have someone send you 2-3 messages quickly
2. Open the app
3. **Expected**:
   - All messages should appear in the chat detail
   - Messages should be in chronological order

### Test 4: Group Chat

1. Receive a message in a WhatsApp group
2. Open the app
3. **Expected**:
   - Group chat should appear in the list
   - Group name should be displayed
   - Sender name should be shown in message bubbles

### Test 5: Media Messages

1. Receive a photo, video, or voice message on WhatsApp
2. Open the app
3. **Expected**:
   - Message should appear with media type indicator: `[IMAGE]`, `[VIDEO]`, `[AUDIO]`, etc.
   - Caption (if any) should be displayed

### Test 6: App Persistence

1. Capture some messages
2. Close the app completely
3. Receive more WhatsApp messages
4. Reopen the app
5. **Expected**:
   - Previous messages should still be there
   - New messages should be added
   - Chat list should update

### Test 7: Multiple Chats

1. Receive messages from 2-3 different contacts
2. Open the app
3. **Expected**:
   - All chats should appear in the list
   - Chats should be sorted by last message time (newest first)
   - Each chat should show correct contact name

## Step 5: Check Logs (Debugging)

If something doesn't work, check the logs:

### In Android Studio:
1. Open Logcat (View → Tool Windows → Logcat)
2. Filter by tag: `WhatsAppNotificationListener` or `NotificationProcessor`
3. Look for:
   - "Notification listener connected" - confirms service is running
   - "WhatsApp notification received" - confirms notifications are being captured
   - Any error messages

### Using ADB:
```bash
adb logcat | grep -E "WhatsAppNotificationListener|NotificationProcessor"
```

## Common Issues & Solutions

### Issue: No chats appearing

**Possible causes:**
1. Notification access not granted
   - **Solution**: Re-check notification access in settings
2. WhatsApp notifications disabled
   - **Solution**: Enable WhatsApp notifications in system settings
3. Service not running
   - **Solution**: Restart the app, check logs

### Issue: Messages not appearing

**Possible causes:**
1. Notification was cleared before processing
   - **Solution**: This is expected - only notifications that are active can be captured
2. Notification format not recognized
   - **Solution**: Check logs to see what data is being extracted

### Issue: Wrong chat names

**Possible causes:**
1. Notification title format varies
   - **Solution**: This is a limitation - chat names come from notification titles

### Issue: App crashes

**Possible causes:**
1. Database migration issues
   - **Solution**: Uninstall and reinstall the app (database will be recreated)
2. Missing dependencies
   - **Solution**: Clean and rebuild: `Build > Clean Project`, then `Build > Rebuild Project`

## Verification Checklist

Before considering Phase 1 complete, verify:

- [ ] App installs successfully
- [ ] Notification permission can be granted
- [ ] Incoming text messages are captured
- [ ] Messages appear in chat list
- [ ] Messages appear in chat detail screen
- [ ] Multiple chats are displayed correctly
- [ ] Group chats are detected (at least heuristically)
- [ ] Media messages show type indicators
- [ ] App persists data after closing
- [ ] New messages are added to existing chats
- [ ] No crashes during normal use

## Next Steps After Testing

> **Note:** Phase 1 has been verified and all subsequent phases are now complete.
> See [TESTING_PHASE2.md](TESTING_PHASE2.md) for Phase 2 testing, and [README.md](README.md) for overall project status.

## Quick Test Script

For a quick verification:

```bash
# 1. Build
./gradlew assembleDebug

# 2. Install
./gradlew installDebug

# 3. Check logs (in another terminal)
adb logcat -c  # Clear logs
adb logcat | grep -E "WhatsApp|Notification"

# 4. Send a test WhatsApp message
# 5. Check if it appears in the app
```

## Notes

- **Notification-based capture is lossy**: Some messages may not be captured if notifications are cleared quickly
- **Heuristics**: Group detection and message direction use heuristics - may not be 100% accurate
- **Media**: Only metadata is captured, not actual media files
- **Real-time**: Messages appear when you open the app (not live updates yet - can be added later)

