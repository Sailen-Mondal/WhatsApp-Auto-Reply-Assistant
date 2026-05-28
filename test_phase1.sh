#!/bin/bash

# Phase 1 Testing Script
# This script helps build, install, and monitor the app for testing

set -e

echo "🚀 WhatsApp Auto-Reply - Phase 1 Testing Script"
echo "================================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo -e "${YELLOW}⚠️  ADB not found. Some features will be disabled.${NC}"
    ADB_AVAILABLE=false
else
    ADB_AVAILABLE=true
    echo -e "${GREEN}✓ ADB found${NC}"
fi

# Function to check if device is connected
check_device() {
    if [ "$ADB_AVAILABLE" = true ]; then
        DEVICES=$(adb devices | grep -v "List" | grep "device" | wc -l)
        if [ "$DEVICES" -eq 0 ]; then
            echo -e "${RED}❌ No Android device connected${NC}"
            echo "Please connect a device or start an emulator"
            return 1
        else
            echo -e "${GREEN}✓ Android device connected${NC}"
            return 0
        fi
    fi
    return 0
}

# Build the project
echo ""
echo "📦 Building project..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

# Install if device is connected
if check_device; then
    echo ""
    echo "📱 Installing app..."
    ./gradlew installDebug
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Installation successful${NC}"
    else
        echo -e "${RED}❌ Installation failed${NC}"
        exit 1
    fi
    
    # Clear logcat
    if [ "$ADB_AVAILABLE" = true ]; then
        echo ""
        echo "🧹 Clearing logcat..."
        adb logcat -c
        echo -e "${GREEN}✓ Logcat cleared${NC}"
    fi
    
    echo ""
    echo "================================================"
    echo -e "${GREEN}✅ App is ready for testing!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Open the app on your device"
    echo "2. Grant notification access permission"
    echo "3. Send/receive WhatsApp messages"
    echo "4. Check the app to see captured messages"
    echo ""
    echo "To monitor logs, run:"
    echo "  adb logcat | grep -E 'WhatsAppNotificationListener|NotificationProcessor'"
    echo ""
    echo "Or use:"
    echo "  ./gradlew installDebug && adb logcat -c && adb logcat | grep WhatsApp"
    echo "================================================"
else
    echo ""
    echo "================================================"
    echo -e "${YELLOW}⚠️  No device connected${NC}"
    echo ""
    echo "APK built successfully at:"
    echo "  app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "You can:"
    echo "1. Connect a device and run: ./gradlew installDebug"
    echo "2. Or manually install the APK"
    echo "================================================"
fi

