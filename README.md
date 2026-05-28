# MigsPod

A personal, ad-free podcast player for Android. Built as a clean alternative to Castbox — no social features, no bloat, just podcasts.

## Features

- **Search & subscribe** to podcasts via iTunes Search API
- **Background playback** with media notification controls
- **Offline downloads** — download episodes for listening without internet
- **New episodes feed** — see latest episodes from all subscriptions in one place
- **Playback progress** — position saved automatically, resume where you left off
- **Dark theme** — always-on dark UI, no light mode

## Screenshots

_Coming soon_

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Media3** (ExoPlayer) for audio playback + background service
- **Room** for local database
- **OkHttp** for networking
- **Coil** for image loading
- **WorkManager** for background tasks

## Building

### Prerequisites

- Android Studio Ladybug or later
- JDK 17
- Android SDK 35

### Setup

After cloning, enable the git hooks:

```bash
git config core.hooksPath scripts/hooks
```

### Debug build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Release build

1. Generate a signing key:
   ```bash
   keytool -genkey -v -keystore migspod-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias migspod
   ```

2. Create `keystore.properties` in the project root (see `keystore.properties.example`):
   ```properties
   storeFile=migspod-release.jks
   storePassword=your_store_password
   keyAlias=migspod
   keyPassword=your_key_password
   ```

3. Build the release bundle:
   ```bash
   ./gradlew bundleRelease
   ```

## Testing

```bash
# Unit tests (JVM)
./gradlew testDebugUnitTest

# Instrumented tests (requires emulator or device)
./gradlew connectedDebugAndroidTest

# Lint checks
./gradlew lintDebug
```

## Project Structure

```
app/src/main/java/com/migspod/
├── data/           # Models, DAOs, database, repository, RSS parser
├── player/         # Media3 playback service and controller
├── ui/
│   ├── components/ # Reusable UI (MiniPlayer)
│   ├── screens/    # All app screens
│   └── theme/      # Material 3 dark theme
├── MainActivity.kt
└── MigsPodApp.kt   # Application class
```

## CI

GitHub Actions runs on every push and PR to `main`:
- Unit tests
- Lint checks
- Debug build verification
- Release bundle build (on main pushes)

## License

Private project. All rights reserved.
