# MigsPod - Production TODO

## Before First Release

### Build & Signing (DONE)
- [x] Enable R8 minification + resource shrinking
- [x] Generate release signing keystore
- [x] Configure signing in build.gradle.kts
- [x] Add proguard rules for dependencies
- [x] Set up GitHub Actions CI

### App Identity
- [ ] **Custom app icon** — design a launcher icon (adaptive icon with foreground + background layers). Use Android Studio's Image Asset tool to generate all mipmap sizes.
- [ ] **Monochrome icon variant** — needed for themed icons on Android 13+. Add a `<monochrome>` layer to `ic_launcher.xml`.
- [ ] **Feature graphic** — 1024x500 banner image for Play Store listing.
- [ ] **Screenshots** — at least 2 phone screenshots for the Play Store. Pixel 6/7 frames look clean.

### Play Store Setup
- [ ] Create Google Play Developer account ($25 one-time fee)
- [ ] Choose final `applicationId` — currently `com.migspod`. Can't change after first publish.
- [ ] Write Play Store listing (short description, full description, category)
- [ ] Set content rating (fill out the questionnaire in Play Console)
- [ ] Create a privacy policy (required even for personal apps if you use INTERNET permission). Can be a simple GitHub Pages site or a Google Doc.
- [ ] Upload to internal testing track first
- [ ] Promote to production after testing

### Code Quality
- [x] Fix lint warnings (DefaultLocale, ExportedService, UseKtx)
- [x] Update stale dependencies (Compose BOM, Room, Media3, Navigation, etc.)
- [ ] Bump `targetSdk` to 36 when ready (test on Android 16 emulator first)

### Crash Reporting
- [x] Add Firebase Crashlytics Gradle config
- [ ] **Create Firebase project** at https://console.firebase.google.com — add an Android app with package `com.migspod`, download `google-services.json`, place in `app/`

### Nice to Have
- [x] **Versioning strategy** — semver (1.0.0, 1.1.0, etc.), increment `versionCode` with each release
- [x] **Changelog** — CHANGELOG.md using Keep a Changelog format
- [ ] **Automated Play Store deployment** — can set up Gradle Play Publisher plugin to upload AABs directly from CI. Overkill for now but nice later.

## GitHub Repository Setup
- [ ] Create private repo on GitHub (`gh repo create migs-pod --private`)
- [ ] Initial push
- [ ] Add GitHub Secrets for CI release signing:
  - `KEYSTORE_BASE64` — run `base64 -i migspod-release.jks | pbcopy` and paste as secret
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

## Post-Launch
- [ ] Monitor crash reports (if Crashlytics added)
- [ ] Respond to Play Store review feedback if any
- [ ] Keep dependencies updated periodically
