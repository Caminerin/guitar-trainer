# Guitar Trainer 🎸

Android app for guitar practice — detect notes in real time using your phone's microphone.

## Features

Three modes in a single screen:

- **Verificar (Verify)**: Select a target note, play it on your guitar, and see if you nailed it.
- **Libre (Free)**: Play anything and see which note the app detects in real time.
- **Afinar (Tuner)**: Select a string (standard EADGBE tuning) and tune it using the visual cents indicator.

## Tech Stack

- **Kotlin + Jetpack Compose** (Material 3)
- **YIN pitch detection algorithm** for real-time note recognition
- **AudioRecord API** for low-latency microphone input
- Min SDK 26 (Android 8.0+)

## Build

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## CI

GitHub Actions automatically builds and uploads the debug APK on every push to `main` and on pull requests. Download it from the workflow artifacts.

## Permissions

The app requires **microphone access** (`RECORD_AUDIO`) to detect what you play.
