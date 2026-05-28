# Phase 2 Testing Guide

This guide will help you test all Phase 2 features: Manual AI Suggestions, Copy/Edit functionality, Feedback system, and Debug Panel.

## Prerequisites

1. **Android Device or Emulator** (Android 8.0+)
2. **WhatsApp installed** on the device
3. **Groq API Key** (optional - can be added via Settings)

## Setup Steps

### 1. Build and Install the App

**Option A: Using Android Studio**
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Connect your device or start an emulator
4. Click "Run" (green play button) or press `Shift+F10`
5. Select your device/emulator

**Option B: Using Command Line**
```bash
cd /home/sailen/Projects
./gradlew installDebug
```

### 2. Grant Notification Access Permission

1. When the app opens, you'll see a permission screen
2. Tap "Open Settings"
3. Find "WhatsApp Auto Reply" in the list
4. Toggle it ON
5. Return to the app and tap "I've granted the permission"

### 3. Configure Groq API Key (Optional)

You can add the API key in two ways:

**Method 1: Via Settings Screen (Recommended)**
1. Open the app
2. Tap the Settings icon (⚙️) in the top-right
3. Enter your Groq API key in the "Groq API Key" field
4. Tap "Save Settings"
5. You should see "Settings saved successfully!"

**Method 2: Via local.properties (For Development)**
1. Create/edit `local.properties` in the project root
2. Add: `GROQ_API_KEY=your_api_key_here`
3. Rebuild the app

**Get API Key:**
- Visit https://console.groq.com
- Sign up/login
- Create an API key
- Copy the key (starts with `gsk_`)

## Testing Phase 2 Features

### Test 1: Basic Notification Capture (Phase 1 Verification)

1. **Send/Receive WhatsApp Messages**
   - Send a message from another device to your WhatsApp
   - Or send yourself a message from WhatsApp Web
   - Wait a few seconds

2. **Verify Messages Appear in App**
   - Open the WhatsApp Auto Reply app
   - You should see the chat in the chat list
   - Tap on the chat to see messages
   - Verify messages are displayed correctly

**Expected Result:** All WhatsApp messages should appear in the app's chat list and detail screens.

---

### Test 2: AI Reply Suggestion (Core Feature)

1. **Open a Chat with Messages**
   - Navigate to a chat that has at least one incoming message
   - Tap to open the chat detail screen

2. **Generate a Suggestion**
   - Scroll down to the "AI suggestion" section
   - Tap "Suggest reply" button
   - Wait for the AI to generate a reply (should take 2-5 seconds)

3. **Verify Suggestion Appears**
   - The suggested reply should appear in a colored box
   - It should be contextually relevant to the conversation
   - The reply should be in Hinglish/Benglish style (if applicable)

**Expected Result:** 
- Button shows "Generating..." while processing
- A relevant reply suggestion appears
- Reply is under 150 characters (typically)

**Troubleshooting:**
- If you see "Could not generate reply. Check API key or try again."
  - Verify API key is saved in Settings
  - Check internet connection
  - Verify API key is valid at https://console.groq.com

---

### Test 3: Copy to Clipboard

1. **Generate a Suggestion** (follow Test 2 steps)

2. **Copy the Suggestion**
   - Tap the copy icon (📋) next to the suggestion
   - You should see a snackbar message: "Copied to clipboard!"

3. **Verify Copy Works**
   - Open WhatsApp
   - Long-press in the message input field
   - Tap "Paste"
   - The suggested reply should appear

**Expected Result:** The suggested text is successfully copied to clipboard and can be pasted in WhatsApp.

---

### Test 4: Edit Functionality

1. **Generate a Suggestion** (follow Test 2 steps)

2. **Edit the Suggestion**
   - Tap the "Edit" button
   - The suggestion should become an editable text field
   - Modify the text as desired
   - Tap "Done" to finish editing

3. **Verify Edit Persists**
   - The edited text should replace the original suggestion
   - You can copy the edited version
   - You can edit again if needed

**Expected Result:** 
- Edit button toggles to editable text field
- Changes are saved when "Done" is tapped
- Edited text can be copied

---

### Test 5: User Feedback (Upvote/Downvote)

1. **Generate a Suggestion** (follow Test 2 steps)

2. **Provide Feedback**
   - Tap the thumbs up (👍) icon to upvote
   - The icon should change color to indicate selection
   - Or tap thumbs down (👎) to downvote

3. **Verify Feedback is Saved**
   - Generate another suggestion
   - The previous feedback should be remembered (if viewing same log)
   - Check the Debug Panel (Test 6) to verify feedback is stored

**Expected Result:**
- Icons change color when selected
- Feedback is saved to database
- Can switch between upvote/downvote

---

### Test 6: LLM Debug Panel

1. **Generate Some Suggestions**
   - Generate at least 2-3 suggestions in different chats
   - This creates log entries

2. **Open Debug Panel**
   - Go to Settings (⚙️ icon)
   - Scroll down to "Developer Tools" section
   - Tap "View LLM Debug Panel"

3. **Verify Logs Display**
   - You should see a list of recent LLM interactions
   - Each log shows:
     - Chat ID (truncated)
     - Timestamp
     - Tone used
     - Generated reply
     - Input context (last 10 messages)
     - User feedback (if provided)
     - Auto-sent indicator (if applicable)

4. **Test Log Details**
   - Scroll through the logs
   - Verify all information is displayed correctly
   - Check that feedback status is shown

**Expected Result:**
- All LLM interactions are logged
- Logs show complete information
- Recent logs appear first
- Empty state shows if no logs exist

---

### Test 7: Settings Screen

1. **Access Settings**
   - Tap Settings icon (⚙️) from chat list

2. **Test API Key Management**
   - Enter/update API key
   - Toggle show/hide to verify password masking
   - Save settings
   - Verify success message appears

3. **Test Default Tone Selection**
   - Change default tone from dropdown
   - Save settings
   - Verify tone is saved

4. **Test Debug Panel Access**
   - Tap "View LLM Debug Panel" button
   - Verify navigation works

**Expected Result:**
- All settings can be modified and saved
- Success/error messages appear appropriately
- Navigation to debug panel works

---

## Edge Cases to Test

### 1. No API Key
- Try generating a suggestion without API key
- Should show error message

### 2. No Context Available
- Try generating suggestion in a chat with no messages
- Should show "No context available yet."

### 3. Network Error
- Turn off internet
- Try generating suggestion
- Should handle gracefully

### 4. Empty Chat
- Open a chat with no messages
- Verify empty state is shown

### 5. Multiple Quick Suggestions
- Generate suggestions quickly in succession
- Verify each one works independently

---

## Success Criteria

✅ All tests pass without crashes
✅ UI is responsive and intuitive
✅ Copy functionality works correctly
✅ Edit functionality allows text modification
✅ Feedback system saves user preferences
✅ Debug panel displays all logs correctly
✅ Settings can be saved and loaded
✅ Error messages are clear and helpful

---

## Known Issues to Watch For

1. **First-time API call may be slow** - This is normal, subsequent calls should be faster
2. **Logs may take a moment to appear** - The debug panel uses Flow, so it updates automatically
3. **Clipboard may not work on some devices** - Test on multiple devices if possible

---

## Next Steps After Testing

Once Phase 2 is verified working:
- Proceed to Phase 3: Auto-Reply Engine
- Report any bugs or issues
- Suggest improvements if needed

---

## Quick Test Checklist

- [ ] App installs and runs
- [ ] Notification permission granted
- [ ] Messages appear in chat list
- [ ] Chat detail shows messages correctly
- [ ] API key can be saved in Settings
- [ ] Reply suggestions generate successfully
- [ ] Copy to clipboard works
- [ ] Edit functionality works
- [ ] Upvote/downvote feedback works
- [ ] Debug panel shows logs
- [ ] Settings save correctly

---

Happy Testing! 🚀

