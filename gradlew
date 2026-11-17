#!/bin/bash
exec gradle -Dorg.gradle.jvmargs= --no-daemon --max-workers=2 --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 "$@"
