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

- [ ] Tap an episode → bottom-sheet (EpisodeDetail) slides up
- [ ] Tap Play → audio starts within ~2s
- [ ] **Media notification** appears with title, podcast name, artwork
- [ ] Notification play/pause toggles audio
- [ ] **Lockscreen controls** work (Android lockscreen media controls)
- [ ] **Bluetooth headphone play/pause button** toggles audio
- [ ] **Audio focus**: starting playback while a YouTube/Spotify track is playing → other app pauses
- [ ] **Becoming-noisy**: pull out headphones during playback → playback pauses (not continues out loud)
- [ ] **Phone call** during playback → playback pauses, resumes (or stays paused) after call

## 5. Now Playing screen

- [ ] Tap mini-player → Now Playing slides up
- [ ] Artwork shows full-bleed with gradient overlay
- [ ] Seek bar tracks position smoothly (no jitter every 250ms)
- [ ] **Scrub the seek bar** to ~50% → position jumps, audio resumes at that point
- [ ] **-10s button** seeks backward exactly 10s (not more, not less)
- [ ] **+30s button** seeks forward exactly 30s
- [ ] Time labels show current / total, formatted as `0:00` or `0:00:00` correctly
- [ ] Back arrow returns to previous screen, audio keeps playing
- [ ] Swipe-down (system gesture) — verify expected behavior

## 6. Position persistence (the headline feature)

- [ ] Play an episode for ~30s, pause, kill the app from recents
- [ ] Re-open app → episode shows progress indicator at ~30s
- [ ] Resume → audio starts at ~30s, not 0:00
- [ ] **Force-quit via Settings → Apps → Force stop** → re-open → progress still saved
- [ ] Listen for ~5 minutes, swipe app away from recents *while playing* — playback continues in the background
- [ ] After ~10s in background, pull notification — controls still work

## 7. Episode completion

- [ ] Skip to last ~5 seconds of an episode using +30 button repeatedly
- [ ] Let it finish naturally
- [ ] **No autoplay** — playback stops, no next episode starts
- [ ] Episode appears greyed out / marked complete in the podcast list
- [ ] Episode is **NOT** deleted (per design)
- [ ] Completed episode does NOT appear in "in progress" sections

## 8. Downloads

- [ ] From Episode Detail, tap **Download** → progress feedback (or button switches to "Delete download" when done)
- [ ] Downloaded episode shows in **Downloads** tab
- [ ] Toggle **grouped / by date** sort — both work
- [ ] **Airplane mode → play downloaded episode** → audio plays from disk (no network needed)
- [ ] **Delete download** → file removed, episode disappears from Downloads tab
- [ ] Verify file actually deleted: `adb shell run-as com.migspod ls files/downloads/` (should not list the deleted file)
- [ ] Download → unsubscribe from podcast → download is removed (cascade) and Downloads tab updates

## 9. New Episodes feed

- [ ] "New" tab aggregates across all subscriptions, newest first
- [ ] Episodes from different podcasts interleave by date
- [ ] Tap → opens EpisodeDetail bottom sheet (not full route)
- [ ] Pull-to-refresh triggers `refreshAllPodcasts` — wait, verify both subs updated

## 10. Mini-player behavior

- [ ] Mini-player only appears when an episode is loaded
- [ ] After tapping Stop (or never starting playback) → mini-player hidden
- [ ] Mini-player shows correct title, podcast, artwork
- [ ] Progress bar at the top updates every ~500ms
- [ ] Tap play/pause from mini-player → toggles
- [ ] Tap body of mini-player → opens Now Playing

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
