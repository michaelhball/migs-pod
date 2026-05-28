# MigsPod - Claude Code Instructions

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
- Never export a service (`exported="true"`) without an `android:permission` attribute
- MediaSessionService requires `exported="true"` — always pair with `android:permission="android.permission.MEDIA_CONTENT_CONTROL"`

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
