# Changelog

All notable changes to MigsPod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Downloads opens scrolled to the episode that is loaded in the player and highlights it
- The player holds a wake lock and Wi-Fi lock while streaming, so playback with the screen off does not stall when the phone goes into power saving
- The feed parser accepts only http and https addresses for audio and artwork, ignores entities declared inside a feed, and caps a description at 500,000 characters

### Fixed
- Every screen's top bar sits directly under the status bar; the status bar height was being added twice, which left an empty band above each title
- A show whose feed URL contains `+` or `%` (common in private feeds with tokens) opens its podcast screen; the screen was empty
- Episodes whose audio link redirects between http and https (Libsyn, Megaphone and several tracking prefixes do this) stream; they failed with "This episode's audio isn't available" while Download worked
- A feed with unescaped markup inside a title or description loads; one such element made the whole feed fail with "That doesn't look like a podcast feed"
- Durations with fractional seconds ("1234.5") are shown

## [0.1.2] - 2026-11-11

### Changed
- Downloads remembers whether you chose "By date" or "By podcast", across tab switches and app restarts

## [0.1.1] - 2026-11-08

### Added
- Unsubscribe asks for confirmation first (it removes the podcast's episodes, progress and downloads); unsubscribing from the podcast that's playing stops it
- Close the mini-player: a ✕ appears next to play while paused; it puts the player away and it won't be restored on next launch (progress is kept)
- Add a podcast by its RSS URL ("Add by URL" on the Search tab), for feeds that aren't in search such as private or members-only feeds; pasting a feed URL into the search box offers the same; `feed://`, `itpc://` and scheme-less URLs are accepted
- Headphone / Bluetooth play button (and the system's media resume card) restarts the last unfinished episode even after the app was closed or killed
- Custom launcher icon: bunny ears over headphones, a cool-gradient sibling to the migs-music icon (replaces the default placeholder)
- One-time prompt to exempt the app from battery optimization, so background playback survives the app being swiped from recents on aggressive OEMs
- Release signing configuration
- ProGuard/R8 rules for dependencies
- GitHub Actions CI (unit tests, lint, build)
- Pre-push git hook for unit tests
- Firebase Crashlytics for crash reporting
- CHANGELOG.md, README.md, TODO.md

### Changed
- Targets Android 16 (API 36), which Google Play requires for new apps
- New tab groups episodes under Today, This Week and Earlier, and lists up to 500 episodes
- Library tiles are smaller, four across on a typical phone
- Tabs run Search, New, Downloads, Library from left to right
- App icon background is a midnight blue to violet gradient
- Lockscreen/notification artwork is decoded at 512px rather than full size (feed art is often 3000px, ~36 MB per play)
- The mini-player now also shows on a podcast's page, so playback started from its ⋮ menu is visible and reachable
- Lists no longer re-run their database query on every recomposition
- Feed refresh sends ETag / Last-Modified, so unchanged feeds come back as a tiny 304 instead of re-downloading the whole feed every hour (DB v4)
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
- Tapping the media card in the notification shade or on the lock screen opens the app
- Now Playing keeps its seek bar and buttons on screen when the phone is turned sideways
- A failed search shows a plain reason such as "No internet connection", not the raw technical error
- A second feed of the same show (for example a members-only feed next to the public one) lists its episodes; those sharing an id with the first feed no longer go missing
- The episode sheet no longer says "0:00 remaining" when inserted ads make the audio longer than the feed states
- An episode that fails to load shows a Play button that retries, not a Pause button with nothing playing
- The first episode sheet opened after a cold start no longer stays as a thin strip at the bottom of the screen on phones with system animations turned off
- Downloads are excluded from cloud backup — they pushed backups over Android's 25 MB quota, which stops subscriptions being backed up at all
- Podcasts whose feed, audio or artwork use plain `http://` couldn't be subscribed to, played, downloaded or shown (cleartext was blocked)
- Playback failures (no connection, missing audio) now show a message instead of silently doing nothing
- Position wasn't saved after seeking while paused; the final save when the player service shut down was usually cancelled; switching episodes could drop up to 5s of the previous one's progress; a late periodic save could land after an episode was marked played
- The player connection leaked the activity on rotation; it now follows the activity's start/stop
- Now Playing → Episode details → Resume opened a second Now Playing on top and restarted the episode (jumping back up to 5s); double-tapping the mini-player stacked two Now Playing screens; a double back could pop the screen underneath too
- Switching tabs lost search results and scroll positions; coming back from a podcast to Search lost the results
- Library, New and Downloads briefly flashed their empty state while loading
- Downloads: an HTTP error or a Wi-Fi login page is no longer saved as the episode and marked "Downloaded"; failures show a message instead of a spinner that never stops; partial files are cleaned up; the same episode can't be downloaded twice at once; "Downloading…" now shows on every screen
- Unsubscribing deletes that podcast's downloaded files (they were left on disk forever); leftover files from earlier unsubscribes and interrupted downloads are cleaned up on launch
- Deleting a download from the episode sheet or ⋮ menu now stops playback if it's the playing episode (previously only the Downloads tab did)
- A failed subscribe in Search left that row spinning and disabled forever with no explanation; it now resets and says why
- Subscribing to a URL that isn't a feed (404, web page) no longer adds a blank, untitled podcast
- Re-subscribing to a podcast could wipe its episodes' progress, played state and downloads (`REPLACE` + cascade)
- Feed refresh now updates existing episodes' title, description, audio URL, date and duration (keeping progress/played/downloads), so a feed that moves its audio no longer leaves dead links
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
