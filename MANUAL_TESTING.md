# MigsPod Manual Testing Plan

Things automation can't verify reliably. Run end-to-end on a real device before the first Play Store push. Tick each box; note pass/fail beside it.

## Changes since the last pass

- Bottom nav order is now **Search · Library · New · Downloads**; "New" tab icon is an RSS feed
- Podcast description in detail header is **tap-to-expand**
- Episode detail bottom sheet now **fills the screen** so the description has space to scroll; URLs in descriptions are **clickable**
- "Delete download" button is hidden in the episode sheet when opened from the **Downloads** tab (the list already has its own delete)

## 0. Setup

- [x] Install fresh debug build: `./gradlew installDebug`
- [x] First launch: app opens without crash, lands on Library tab (intentional — Library is the default)
- [x] Notification permission prompt (Android 13+) — accept _(fixed: now requested at startup)_
- [x] Verify dark theme is applied (no white flashes anywhere)

## 1. Discovery & subscribe

- [x] **Search tab → empty query**: no spurious requests, no error toast
- [x] **Search "Radiolab"**: results appear within ~2s, artwork loads
- [x] **Search a non-existent term** (e.g. "zzzqqqxyz"): empty state, no crash
- [x] **Tap a result** → subscribes (if not already) + navigates to PodcastDetail _(fixed: tap row now opens detail; + button is subscribe-only)_
- [x] **Subscribe** from podcast detail → confirmation, appears in Library
- [x] **Subscribe to a feed with HTML in description** → no raw `<p>` or `&amp;` rendered
- [x] **Search a 2nd podcast and subscribe** → Library now shows two, sorted alphabetically by title
- [x] **Background search** (turn airplane mode on, then search) → graceful error, no crash

## 2. Library

- [x] Library grid renders artwork crisply (no blur, no aspect-ratio stretching)
- [x] Tap a podcast tile → detail screen loads instantly (no flash of empty state)
- [x] **Unsubscribe** via overflow (⋮) menu in the podcast detail screen → tile removed, episodes disappear from New / Downloads _(long-press is not supported; canonical path is the kebab in detail view)_
- [x] **Episode-row overflow (⋮) menu** in podcast detail: Play / Download / Delete download / Mark played–unplayed _(added)_
- [x] **Download from episode row** survives navigation _(fixed: downloads run on the application scope, not the composition scope)_

## 3. Episode list & RSS edge cases

Pick a real, high-volume show (e.g. "The Daily" or "BBC Global News"):

- [x] Episode list loads in under 1s after subscribe
- [x] Newest episode is at the top
- [x] Durations are formatted correctly (`H:MM:SS` or `M:SS`, no `0:00` for valid episodes)
- [x] Episode description is plain text — no HTML tags
- [x] **Pull-to-refresh** on podcast detail: spinner appears, new episodes appear if available, refresh ends within ~5s
- [x] Pull-to-refresh on "New" tab: same behavior across all subs

## 4. Playback — the critical path

- [x] Tap an episode → bottom-sheet (EpisodeDetail) slides up
- [x] Tap Play → audio starts within ~2s
- [~] **Media notification** — confirmed posted at the framework level (`adb dumpsys` shows the foreground notification `channel=default_channel_id, category=transport, 2 actions`) and lockscreen controls work, so the session/notification objectively exist. On this ColorOS/OPPO device the shade media card is suppressed by the OEM even though the notification is posted — not an app bug. Nothing more to do in code.
- [ ] Notification play/pause toggles audio — _OEM-dependent; lockscreen controls confirmed working_
- [~] **Lockscreen controls** work — yes; **now with -10s/+30s buttons** _(fixed: declared seek increments on the player)_. ⚠️ album art not shown on this device's lockscreen — appears OEM-dependent (artwork URI is set on the session metadata).
- [ ] **Bluetooth headphone play/pause button** toggles audio — _not yet tested_
- [x] **Audio focus**: starting playback while a YouTube/Spotify track is playing → other app pauses
- [ ] **Becoming-noisy**: pull out headphones during playback → playback pauses (not continues out loud) — _can't test right now_
- [ ] **Phone call** during playback → playback pauses, resumes (or stays paused) after call — _can't test right now_

## 5. Now Playing screen

- [x] Tap mini-player → Now Playing slides up
- [x] Artwork shows as a contained, rounded album-art image above the title/controls _(fixed: was a zoomed-in full-bleed background)_
- [x] Seek bar tracks position smoothly (no jitter every 250ms)
- [x] **Scrub the seek bar** to ~50% → position jumps, audio resumes at that point
- [x] **-10s button** seeks backward exactly 10s (not more, not less)
- [x] **+30s button** seeks forward exactly 30s
- [x] Time labels show current / total, formatted as `0:00` or `0:00:00` correctly
- [x] Play/pause button stays stable when tapping ±10/30s _(fixed: icon now tracks playWhenReady, no flicker on seek)_
- [x] Back arrow (now a chevron-down) returns to previous screen, audio keeps playing
- [x] **Swipe-down to dismiss** → drag the screen down to pop back _(fixed: previously did nothing)_
- [x] **"Episode details" button** at the bottom opens the full episode detail sheet _(added)_

## 6. Position persistence (the headline feature)

_Original pass: BROKEN — after quitting, the mini-player was gone on relaunch (PlaybackState is in-memory and was never restored). Fixed: the last-played episode is now saved and restored on launch (paused, at its saved position), so the mini-player returns and resumes. **Re-test these:**_

- [ ] Play an episode for ~30s, pause, kill the app from recents
- [x] Re-open app → mini-player is back, pointing at that episode _(fixed: restore-on-launch)_
- [ ] Resume → audio starts at ~30s, not 0:00 _(2nd fix: resume was restarting at 0 because seekTo was dropped on the unprepared item; now passes the start position into setMediaItem — re-test)_
- [ ] **Force-quit via Settings → Apps → Force stop** → re-open → mini-player restored, resumes at saved position
- [ ] Listen for ~5 minutes, swipe app away from recents *while playing* — playback continues in the background — _⚠️ on this ColorOS/OPPO device the OS kills the swiped app's service even though it's a proper foreground service. Background playback can't be guaranteed without exempting MigsPod from battery optimization (Settings → Battery → MigsPod → Allow background activity / don't optimize). After a kill, relaunch now restores the session so nothing is lost._
- [ ] After ~10s in background, pull notification → media card present with working controls _(confirmed at framework level: foreground notification `channel=default_channel_id, category=transport`; ColorOS shows it in the shade's media area, not as a banner)_

## 7. Episode completion

- [ ] Skip to last ~5 seconds of an episode using +30 button repeatedly
- [ ] Let it finish naturally
- [x] **No autoplay** — playback stops, no next episode starts
- [x] **Player closes when the episode finishes** — mini-player disappears at end-of-episode _(fixed: was staying open on a finished item)_
- [x] Episode is marked **✓ Played** and dimmed in the podcast list, New feed, **and Downloads** _(fixed: now consistent across all three lists with a "✓ Played" chip + 50% dimmed title)_
- [ ] Episode is **NOT** deleted (per design)
- [x] Completed episode does NOT appear in "in progress" sections

## 8. Downloads

- [x] From a podcast episode row, tap **Download** (kebab) → row shows a "Downloading…" chip, then "Downloaded" when done _(fixed: there was no in-progress indicator in this list)_
- [ ] From Episode Detail, tap **Download** → progress feedback (or button switches to "Delete download" when done)
- [ ] Downloaded episode shows in **Downloads** tab
- [x] **Default sort is now By date**, not grouped by podcast _(changed per feedback; toggle to "By podcast" still available)_
- [x] **Airplane mode → play downloaded episode** → audio plays from disk (no network needed)
- [ ] **Delete download** → file removed, episode disappears from Downloads tab — _note: the file IS deleted (unlinked). Previously, deleting while that episode was playing left audio going from the still-open file handle, which looked like "not deleted". Now deleting the currently-playing episode stops playback so it's unambiguous._
- [ ] Verify file actually deleted: `adb shell run-as com.migspod ls files/downloads/` (should not list the deleted file)
- [ ] Download → unsubscribe from podcast → download is removed (cascade) and Downloads tab updates

## 9. New Episodes feed — ✅ user confirmed looks good

- [x] "New" tab aggregates across all subscriptions, newest first
- [x] Episodes from different podcasts interleave by date
- [x] Tap → opens EpisodeDetail bottom sheet (not full route)
- [x] Pull-to-refresh triggers `refreshAllPodcasts`

## 10. Mini-player behavior — ✅ user confirmed looks good

- [x] Mini-player only appears when an episode is loaded
- [x] After tapping Stop (or never starting playback) → mini-player hidden
- [x] Mini-player shows correct title, podcast, artwork
- [x] Progress bar at the top updates every ~500ms
- [x] Tap play/pause from mini-player → toggles
- [x] Tap body of mini-player → opens Now Playing

## 11. Navigation edge cases

- [ ] Switch tabs while playing → audio doesn't interrupt
- [ ] Open Now Playing, rotate device (if rotation enabled) → state preserved
- [ ] System back from Now Playing → returns, doesn't kill audio
- [ ] System back from PodcastDetail → returns to Library/Search

## 12. Network failure modes

- [ ] **Disable Wi-Fi mid-stream** during playback of a non-downloaded episode → buffer drains, eventually pauses or surfaces an error (no crash)
- [ ] Re-enable Wi-Fi → resume works (or user can re-tap play)
- [ ] **Subscribe to an invalid URL** (e.g. `https://example.com/notafeed.xml`) → error, no crash, no half-subscribed podcast in Library
- [ ] **Subscribe to a 404 feed** → same

## 13. Background refresh (WorkManager)

This is hard to trigger naturally — it's a 1-hour periodic worker. To verify manually:

```bash
adb shell cmd jobscheduler run -f com.migspod $(adb shell dumpsys jobscheduler | grep "feed_refresh" | head -1 | grep -oE '#[0-9]+' | tr -d '#')
```

Or simpler: wait ~1h with the app installed and Wi-Fi available, then check that `lastRefreshed` advanced for each podcast (via SQLite browser or just open the app and pull-to-refresh — it should be near-instant if the worker already ran).

- [ ] Worker fires hourly and updates `lastRefreshed`
- [ ] New episodes appear in "New" without opening the app

## 14. Permissions & system integration

- [ ] **Notification posture**: notification appears low-priority (no sound/vibration)
- [ ] **Notification dismissal**: cannot be dismissed while playing (foreground service requirement) — verify swiping it does nothing during playback
- [ ] **App in Recents**: shows correct app name "MigsPod" and icon
- [ ] **App icon on launcher**: rendered correctly (note: custom icon still TODO per `TODO.md`)
- [ ] **Themed icon** (Android 13+, Settings → Wallpaper & style → Themed icons): currently NOT supported — note as known issue until monochrome layer added

## 15. Memory & battery sanity

- [ ] Play continuously for 30 minutes → memory stable (use Android Studio Profiler or `dumpsys meminfo com.migspod`)
- [ ] Battery usage screen shows MigsPod ≲ similar to other media apps for the same playback duration

## 16. Upgrade path

Since v0.1.0 has never shipped, there's no real upgrade scenario yet. After first release, before any v0.2.0:

- [ ] Install old version (`adb install -r app-debug-0.1.0.apk`)
- [ ] Subscribe to 2 podcasts, listen to 30s of one, download one
- [ ] Install new version on top
- [ ] Verify: subscriptions intact, playback position preserved, download still works offline

## 17. Release-build smoke (R8 / ProGuard)

R8 minification can break things automated unit tests don't catch (reflection, serialization). Do this on the **release** build, not debug:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

- [ ] Release build installs and launches
- [ ] Search returns results (iTunes JSON parsing still works)
- [ ] Subscribe + play one episode end-to-end
- [ ] Media notification still appears (notification building uses Media3 internals — R8 sometimes strips these)
- [ ] Force-quit then reopen → DB state intact (Room is a common R8 victim — verify `migspod.db` still reads)
- [ ] No crash for 10 minutes of regular use

## 18. Crashlytics (once Firebase project is created)

After dropping a real `google-services.json` into `app/`:

- [ ] Build release → Crashlytics SDK initialized at startup (check `adb logcat | grep Crashlytics`)
- [ ] Force a crash (e.g. add a `throw RuntimeException()` button temporarily) → crash appears in Firebase console within ~1 hour

---

## Quick sanity checklist (5-minute smoke test before each release)

If short on time, do at least these:

1. Cold launch → no crash
2. Search "BBC" → results
3. Subscribe → episode list loads
4. Play an episode → audio starts, notification appears
5. Lock the phone → controls work on lockscreen
6. Kill app → reopen → position preserved
7. Download an episode → play in airplane mode
8. Pull-to-refresh on New tab → completes
