#!/bin/bash
set -e

echo "=== COMMERCE OS DUAL STANDALONE APK BUILDER & AUTO-LAUNCHER ==="

export ANDROID_HOME="/Users/jigar/Library/Android/sdk"
export ANDROID_SDK_ROOT="/Users/jigar/Library/Android/sdk"

if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@21" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
elif [ -d "/usr/local/opt/openjdk@21" ]; then
    export JAVA_HOME="/usr/local/opt/openjdk@21"
fi

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Using ANDROID_HOME=$ANDROID_HOME"

cd /Users/jigar/Desktop/new-project/commerce-os/apps/android

echo "Building Standalone Customer App & Standalone Rider App Debug APKs..."
./gradlew :app:assembleDebug :rider-app:assembleDebug

APP_APK_PATH="/Users/jigar/Desktop/new-project/commerce-os/apps/android/app/build/outputs/apk/debug/app-debug.apk"
RIDER_APK_PATH="/Users/jigar/Desktop/new-project/commerce-os/apps/android/rider-app/build/outputs/apk/debug/rider-app-debug.apk"

echo "=== BOTH STANDALONE APKS BUILT SUCCESSFULLY! ==="
echo "Customer App APK: $APP_APK_PATH"
echo "Rider App APK: $RIDER_APK_PATH"

if adb devices | grep -q "device$"; then
    echo "Installing Standalone Customer App (com.commerceos.android)..."
    adb install -r "$APP_APK_PATH"

    echo "Installing Standalone Rider App (com.commerceos.rider)..."
    adb install -r "$RIDER_APK_PATH"

    echo "Launching Standalone Customer App (com.commerceos.android) on Samsung Galaxy device..."
    adb shell am start -n com.commerceos.android/.MainActivity
    echo "=== STANDALONE CUSTOMER APP LAUNCHED ON DEVICE SCREEN! ==="
fi
