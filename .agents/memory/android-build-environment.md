---
name: Android build environment
description: Environment constraint affecting local Android Gradle verification.
---

Android Gradle builds require a writable Android SDK containing the configured compile SDK and build tools. In this workspace, the available sdkmanager is pinned to `/opt/android-sdk`, which is read-only, so APK compilation can be blocked even when Gradle and the project wrapper are present.

**Why:** A build attempt can fail before Kotlin compilation with “SDK location not found,” and attempting to redirect the pinned manager still targets the read-only system path.

**How to apply:** Check for a configured writable SDK before treating `assembleDebug` as a valid verification step; if unavailable, use source/diff checks and report the limitation clearly.