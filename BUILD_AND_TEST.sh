#!/bin/bash

# WhatsApp Auto-Reply App - Build and Test Script
# This script helps verify the app can be built

echo "🔍 Checking project structure..."

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Error: build.gradle.kts not found. Are you in the project root?"
    exit 1
fi

echo "✅ Project structure looks good"
echo ""
echo "📱 To build and test the app:"
echo ""
echo "Option 1: Using Android Studio (Recommended)"
echo "  1. Open this project in Android Studio"
echo "  2. Wait for Gradle sync to complete"
echo "  3. Connect your Android device or start an emulator"
echo "  4. Click the 'Run' button (▶️) or press Shift+F10"
echo "  5. Select your device/emulator"
echo ""
echo "Option 2: Using Command Line"
echo "  First, ensure you have the Gradle wrapper:"
echo "    gradle wrapper"
echo "  Then build:"
echo "    ./gradlew assembleDebug"
echo "  Install to device:"
echo "    ./gradlew installDebug"
echo ""
echo "📋 Testing Checklist:"
echo "  [ ] App installs successfully"
echo "  [ ] Grant notification access permission"
echo "  [ ] Add Groq API key in Settings"
echo "  [ ] Generate a reply suggestion"
echo "  [ ] Test copy functionality"
echo "  [ ] Test edit functionality"
echo "  [ ] Test feedback (upvote/downvote)"
echo "  [ ] View LLM Debug Panel"
echo ""
echo "📖 For detailed testing instructions, see:"
echo "  - TESTING_PHASE2.md (comprehensive guide)"
echo "  - QUICK_TEST_PHASE2.md (quick checklist)"
echo ""

