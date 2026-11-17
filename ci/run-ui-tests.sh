#!/usr/bin/env bash
# Runs the on-device tests on the emulator started by the workflow and collects
# the screenshots the tests take. Called from .github/workflows/build-apk.yml.
set -u

SHOTS=/sdcard/Download/expense-tracker-screenshots

# 360dp wide: the narrowest common phone, so layouts are checked where space is tightest.
adb shell wm density 480
adb shell rm -rf "$SHOTS"
adb logcat -c

./gradlew connectedDebugAndroidTest --stacktrace
status=$?

rm -rf screenshots
adb pull "$SHOTS" screenshots || echo "No screenshots were taken"

if [ "$status" -ne 0 ]; then
  echo "===== App crashes and test runner messages from the device log ====="
  adb logcat -d -v brief AndroidRuntime:E TestRunner:I '*:S' | tail -n 200
fi
exit "$status"
