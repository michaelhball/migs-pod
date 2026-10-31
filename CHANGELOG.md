# Changelog

All notable changes to MigsPod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Custom launcher icon: bunny ears over headphones, a cool-gradient sibling to the migs-music icon (replaces the default placeholder)
- One-time prompt to exempt the app from battery optimization, so background playback survives the app being swiped from recents on aggressive OEMs
- Release signing configuration
- ProGuard/R8 rules for dependencies
- GitHub Actions CI (unit tests, lint, build)
- Pre-push git hook for unit tests
- Firebase Crashlytics for crash reporting
- CHANGELOG.md, README.md, TODO.md

### Changed
- Updated Compose BOM 2024.12.01 → 2026.02.01
- Updated Room 2.6.1 → 2.8.4
- Updated Media3 1.5.1 → 1.9.2
- Updated Navigation 2.8.5 → 2.9.7
- Updated Lifecycle 2.8.7 → 2.10.0
- Updated Activity Compose 1.9.3 → 1.12.3
- Updated Core KTX 1.15.0 → 1.17.0
- Updated WorkManager 2.10.0 → 2.11.1
- Updated Coroutines 1.9.0 → 1.10.2

### Fixed
- Episode sheet showed time listened as "remaining"
- Episode dates in many common formats (no weekday, wrong weekday, no seconds, named or `+01:00` time zones, ISO 8601 with `Z`) were dropped, so those episodes showed no date, sank to the bottom of lists and fell out of the New tab
- Show notes: HTML entities (`&amp;`, `&rsquo;`, `&nbsp;`…) are decoded, paragraphs/line breaks/list items are kept instead of running together, and link targets are kept so they can be tapped
- CI: lint errors (Media3 `UnstableApi` opt-in, stale transitive Fragment version) and an invalid `release.yml` (secrets used in a step `if:`)
- Lint: String.format without Locale parameter
- Lint: Exported service without permission
- Lint: Uri.parse instead of toUri() KTX extension

## [0.1.0] - Unreleased

### Added
- Search podcasts via iTunes Search API
- Subscribe/unsubscribe to podcasts
- Episode listing with playback progress
- Background audio playback with media notification
- Playback position saving (every 5s + on pause)
- Mini player bar with progress indicator
- Full Now Playing screen with artwork, seek bar, skip controls
- Episode detail bottom sheet
- Episode downloads for offline listening
- New Episodes feed across all subscriptions
- Downloads tab with grouped/date sort
- Pull-to-refresh on feeds
- Dark theme (always-on)
