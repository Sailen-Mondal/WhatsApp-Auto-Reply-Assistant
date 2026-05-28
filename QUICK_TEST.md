# Quick Testing Reference

## 🚀 Quick Start

### 1. Build & Install (Terminal)
```bash
cd /home/sailen/Projects
./test_phase1.sh
```

### 2. Build & Install (Android Studio)
- Open project in Android Studio
- Click "Run" (green play button) or press `Shift+F10`

### 3. Grant Permission
1. App opens → Shows permission screen
2. Click "Open Settings"
3. Toggle ON "WhatsApp Auto Reply"
4. Go back to app → Click "I've granted the permission"

## ✅ Quick Verification

1. **Send yourself a WhatsApp message** (from another device/number)
2. **Open the app**
3. **Expected**: Chat appears in list with the message

## 🔍 Debug Commands

### View Logs
```bash
# Filter for notification-related logs
adb logcat | grep -E "WhatsAppNotificationListener|NotificationProcessor"

# Or view all app logs
adb logcat | grep "com.whatsappautoreply"
```

### Check Notification Access
```bash
adb shell settings get secure enabled_notification_listeners
```

### Reinstall App
```bash
./gradlew uninstallDebug
./gradlew installDebug
```

## 📋 Test Checklist

- [ ] App installs
- [ ] Permission screen appears
- [ ] Can grant notification access
- [ ] Incoming message appears in chat list
- [ ] Can open chat and see message
- [ ] Multiple chats work
- [ ] App persists after closing

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| No chats appearing | Check notification access is ON |
| App crashes | Check logs, try uninstall/reinstall |
| Messages not showing | Ensure WhatsApp notifications are enabled |
| Permission not working | Manually enable in Settings → Apps → Special access |

## 📱 Testing Tips

1. **Use two devices** - Send messages from one to another
2. **Check immediately** - Don't clear notifications too quickly
3. **Test different types** - Text, media, groups
4. **Monitor logs** - Keep logcat open to see what's happening

## 🎯 Success Criteria

Phase 1 is successful if:
- ✅ App captures WhatsApp notifications
- ✅ Chats appear in the list
- ✅ Messages are stored and displayed
- ✅ No crashes during normal use

---

**Ready to test?** Run `./test_phase1.sh` and follow the prompts!

