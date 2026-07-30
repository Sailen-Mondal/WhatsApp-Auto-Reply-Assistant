# Quick Phase 2 Test Checklist

## 🚀 Quick Start

1. **Build & Install**
   ```bash
   # In Android Studio: Click Run (▶️) or press Shift+F10
   # Or via command line:
   ./gradlew installDebug
   ```

2. **Grant Permissions**
   - Open app → Tap "Open Settings" → Enable notification access

3. **Add API Keys** (via `local.properties` — Gemini, OpenRouter, and/or NVIDIA NIM)
   - See `TESTING_PHASE2.md` for full setup instructions

## ✅ Quick Test Flow

### 1. Basic Functionality (2 min)
- [ ] Open app, see chat list
- [ ] Open a chat, see messages
- [ ] Tap "Suggest reply" → See AI suggestion

### 2. Copy/Edit (1 min)
- [ ] Tap copy icon (📋) → See "Copied to clipboard!"
- [ ] Tap "Edit" → Modify text → Tap "Done"

### 3. Feedback (30 sec)
- [ ] Tap 👍 or 👎 → See icon change color

### 4. Debug Panel (1 min)
- [ ] Settings → "View LLM Debug Panel" → See logs

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| "Could not generate reply" | Check API key in Settings |
| No messages appear | Grant notification access permission |
| App crashes | Check logcat for errors |
| Copy doesn't work | Test on different device/emulator |

## 📝 Test Results Template

```
Date: ___________
Device: ___________
Android Version: ___________

✅ Basic suggestion: PASS / FAIL
✅ Copy functionality: PASS / FAIL  
✅ Edit functionality: PASS / FAIL
✅ Feedback system: PASS / FAIL
✅ Debug panel: PASS / FAIL

Notes:
_________________________________
_________________________________
```

---

**Full testing guide:** See `TESTING_PHASE2.md` for detailed instructions.

