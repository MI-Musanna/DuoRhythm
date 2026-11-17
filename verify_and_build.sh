#!/bin/bash
set -e

echo ">>> [1/3] Running Static Structural & UI Regression Scanner..."
python3 scripts/duorhythm_gatekeeper.py

echo ">>> [2/3] Running Full-App Robolectric JVM Audit Suite..."
./gradlew testDebugUnitTest --continue

echo ">>> [3/3] All Automated Checks PASSED! Building Verified APK..."
./gradlew assembleDebug
echo ">>> VERIFIED BUILD SUCCESSFUL: app/build/outputs/apk/debug/app-debug.apk"
