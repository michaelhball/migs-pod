# MigsPod - Claude Code Instructions

## Git Workflow
- This repo has a remote (`origin`, GitHub: `michaelhball/migs-pod`). After committing, push to `origin main` to keep the remote current (batch pushes at the end of a turn are fine).
- A pre-push hook runs unit tests; if it fails, the push is rejected — fix the tests, don't bypass with `--no-verify`.
- Keep making small, focused, per-change commits on `main`.

## Android Lint Rules

Follow these patterns to avoid common lint warnings:

### Locale
- Always use `String.format(Locale.ROOT, ...)` instead of `String.format(...)` for non-user-facing strings (timestamps, sizes, debug output)
- Use `String.format(Locale.getDefault(), ...)` only for strings shown to users in localized contexts
- Import: `java.util.Locale`

### KTX Extensions
- Use `string.toUri()` instead of `Uri.parse(string)` — import `androidx.core.net.toUri`
- Use `string.toColorInt()` instead of `Color.parseColor(string)` if applicable
- Prefer KTX extension functions over static Java utility methods when available

### Services & Security
- A `MediaSessionService` must be `exported="true"` with the `androidx.media3.session.MediaSessionService` intent-filter. Do NOT add `android:permission` to it — gating it on `MEDIA_CONTENT_CONTROL` stops the OEM media UI from surfacing the notification/lockscreen art (this broke media notifications; verified). Suppress the lint warning with `tools:ignore="ExportedService"` instead.
- Media notification artwork: attach raw bytes via `MediaMetadata.setArtworkData(...)`, NOT `setArtworkUri(...)` — OEM bitmap loaders often can't resolve a remote artwork URI for the lockscreen/notification.
- Let Media3 own the media notification (eagerly install `DefaultMediaNotificationProvider`); never post a bootstrap foreground notification from `onStartCommand` — it clobbers Media3's MediaStyle notification.

### General
- Match the project's existing code style (no extra comments, no docstrings unless complex)
- Dark theme only — never add light theme logic
- Keep it simple — no over-engineering, no unnecessary abstractions

## Releases & Changelog
- When making notable changes, update `CHANGELOG.md` under the `[Unreleased]` section
- Group entries under: Added, Changed, Fixed, Removed
- When preparing a release:
  1. Move `[Unreleased]` entries to a new version heading `[X.Y.Z] - YYYY-MM-DD`
  2. Increment `versionCode` (integer, +1 each release) and `versionName` in `app/build.gradle.kts`
  3. Build and test: `./gradlew testDebugUnitTest && ./gradlew assembleRelease`
