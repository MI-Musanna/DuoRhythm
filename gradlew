#!/bin/bash
exec gradle -Dorg.gradle.jvmargs= --no-daemon --init-script /root/.gradle/init.d/pocketdev-android.gradle -Pandroid.aapt2FromMavenOverride=/root/android-sdk/build-tools/35.0.0/aapt2 "$@"
