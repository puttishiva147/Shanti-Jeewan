# Shanti Jeewan

A private, personal Android app by EZflexz. It automatically switches the phone to Silent when the user enters their saved home area, speaks a customizable reminder at a chosen interval, and restores the previous ringer mode when the user leaves.

## Current project
- Native Android app (Kotlin), not a web app.
- Package: `com.ezflexz.shantijeevan`
- Target device: Android 16 / Realme C71.
- Home geofence, configurable radius, spoken reminder, and restore-on-exit.
- New Shanti Jeewan vector logo and EZflexz brand lockup.

## Build
Open in Android Studio, sync Gradle, then run **Build > Build Bundle(s) / APK(s) > Build APK(s)**. GitHub Actions also builds a debug APK on pushes and uploads it as a workflow artifact.

## Permissions and setup
1. Grant location permission; allow background location for reliable geofence detection.
2. While at home, save the current location as Home.
3. Grant Android's Notification Policy / Do Not Disturb access.
4. Activate automation.
5. Set battery usage to Unrestricted if the phone's vendor battery manager delays background work.

## Behaviour and limitations
- Reminder interval choices: 15, 20, 30, 45, 60 minutes. WorkManager's periodic interval is approximate and has a 15-minute minimum.
- Geofence delivery can be delayed by Android, GPS, network, and battery optimization.
- TTS voice/language depends on the device's installed speech engine.
- The app does not force maximum volume.
- This is a personal-use starter implementation. Test home entry/exit, call handling, and restoration on the actual device before relying on it.
