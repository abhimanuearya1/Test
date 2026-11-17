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
  mkdir -p screenshots
  python3 ci/summarize-test-failures.py screenshots/failures.txt
  echo "===== App crashes and test runner messages from the device log ====="
  adb logcat -d -v brief AndroidRuntime:E TestRunner:I '*:S' | tail -n 200
  exit "$status"
fi

# The tests above use the debug build. The APK people install is the optimized
# release build, so start that one too and check that its first screen appears
# with the classifications loaded from the database.
APP=io.github.abhimanuearya1.expensetracker
./gradlew assembleRelease || exit 1
adb install -r app/build/outputs/apk/release/app-release.apk || exit 1
adb logcat -c
adb shell am start -W -n "$APP/.MainActivity"
sleep 10
adb shell uiautomator dump /sdcard/release-screen.xml >/dev/null
screen=$(adb shell cat /sdcard/release-screen.xml)
if adb shell pidof "$APP" >/dev/null \
    && echo "$screen" | grep -q "Add expense" \
    && echo "$screen" | grep -q "Bills"; then
  echo "Release build starts and shows its first screen."
  adb shell screencap -p /sdcard/release-build.png && adb pull /sdcard/release-build.png screenshots/10-release-build.png
else
  echo "===== The release build did not start correctly ====="
  mkdir -p screenshots
  { echo "The release build did not start correctly."; adb logcat -d -v brief AndroidRuntime:E '*:S' | tail -n 60; echo "$screen" | head -c 1500; } | tee screenshots/failures.txt
  exit 1
fi
