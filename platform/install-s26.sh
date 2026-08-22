#!/usr/bin/env bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

DEVICE="192.168.1.3:5555"
APK="/Users/jigar/Desktop/new-project/commerce-os/apps/android/app/build/outputs/apk/debug/app-debug.apk"

echo "Connecting to $DEVICE..."
adb connect "$DEVICE"

for i in {1..60}; do
  STATE=$(adb devices | grep "$DEVICE" | awk '{print $2}')
  if [ "$STATE" = "device" ]; then
    echo "✅ Device authorized! Installing CommerceOS Customer App..."
    adb -s "$DEVICE" install -r -d -g "$APK"
    echo "🚀 Launching CommerceOS on S26..."
    adb -s "$DEVICE" shell am start -n com.commerceos.android/.MainActivity
    echo "🎉 SUCCESS: App installed and launched on S26!"
    exit 0
  fi
  echo "[$i/60] Waiting for 'Allow USB debugging' prompt on S26 ($STATE)..."
  sleep 2
done

echo "⚠️ Timeout waiting for prompt. Please check your phone screen."
exit 1
