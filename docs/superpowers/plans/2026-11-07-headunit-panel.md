# Head Unit Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One native app for the Carlinkit Ultra 2 and Eonon UX7ST head units: a right-hand panel (lyrics for whatever is playing, weather, prayer countdown, player shortcut) that sits beside Google Maps in the unit's split-screen, with a customizable split ratio (default 70:30) and auto-start.

**Architecture:** A new Gradle module `headunit` (package `com.glass.headunit`). It copies the proven data classes from the `prayerlive` module (the repo's existing pattern: each module carries its own package-renamed copies) and adds plain-view UI, a media follower, a location follower, a settings store, an optional overlay bubble, and an accessibility "window helper" that turns split-screen on at boot. Pure logic (ratio maths, move trigger, cache JSON, panel mode) is unit-tested with JUnit; Android-facing parts are verified on the phone and then on the two units.

**Tech Stack:** Java 17, Android Gradle Plugin 8.2.2, Gradle 8.9, `androidx.media:media:1.7.0` (MediaBrowserCompat), plain Android views (no Compose, no RecyclerView), JUnit 4 + `org.json:json` for local tests.

**Spec:** `docs/superpowers/specs/2026-11-07-headunit-panel-design.md`

**Build commands used throughout:**
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.2/libexec/openjdk.jdk/Contents/Home
./gradlew :headunit:testDebugUnitTest     # unit tests
./gradlew :headunit:assembleDebug         # APK at headunit/build/outputs/apk/debug/headunit-debug.apk
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb; S=10AFAQ1DFH002K9   # the phone
```

**No git:** this folder is not a git repository, so there are no commit steps. Each task ends with a **Checkpoint**: tests pass and the module builds.

## Global Constraints

- Android 12 or newer on the units: `minSdk 31`; `compileSdk 34`; `targetSdk 33`.
- One APK must run on both the Carlinkit Ultra 2 and the Eonon UX7ST. The Eonon has **no adb**: nothing may depend on an adb-only grant, and the app keeps an on-screen log with a copy button.
- Sideloaded: the APK is signed with one fixed keystore so later copies install over the earlier ones.
- Default split ratio 70:30 (map 70, panel 30), adapted to screen size; panel readable minimum about 320 dp wide.
- Location: passive listener plus a 10-minute last-known fallback; **never** switch the GPS on ourselves. Refresh when moved about 5 km or more, on zone change, on a new day; weather every 30 minutes.
- Offline retries every couple of minutes (120 s), never a tight loop.
- No continuous animation: the countdown redraws once a second, lyrics change only when the line changes.
- Settings are exactly: split ratio, text size and theme, swap sides, manual prayer zone, floating bubble on/off with reset position. No section show/hide.
- Floating bubble is off by default and needs "display over other apps", granted by hand.
- Lyrics sources in order: LRCLIB, NetEase, lyrics.ovh (the existing `Lyrics.fetch`).
- Supported players: Spotify, YouTube Music, Bitchord, VRadio, the unit's local-files player.

## Review Focus

1. **Lyrics before the first line or in an instrumental gap** (`indexAt` returns -1, empty lines): the panel must show the title and artist, not crash or show a blank line.
2. **A player quits or two report "playing" at once:** the panel must drop to its idle state (prayer and weather larger) with no stale lyrics from the previous track.
3. **A bad or stale location fix** (0,0, NaN, or a mock provider repeating one point) or a car sitting near the 5 km line: must not trigger a refetch loop.
4. **Midnight rollover and cached data from yesterday:** the panel must not show yesterday's times as today's; after Isyak the next prayer is tomorrow's Subuh.
5. **Narrow or rotated screens and the largest text size:** the panel must not overflow, and the ratio must widen the panel rather than crush it.

---

### Task 1: Module scaffold, copied data classes, on-screen log

**Files:**
- Modify: `settings.gradle` (add `include ':headunit'`)
- Create: `headunit/build.gradle`, `headunit/src/main/AndroidManifest.xml`, `headunit/src/main/res/values/strings.xml`, `headunit/src/main/res/mipmap-anydpi-v26/` (copy from `prayerlive/src/main/res/`), `headunit/keystore/README.txt`
- Create (copied from `app/src/main/java/com/glass/aaspike/`, package line changed to `com.glass.headunit`): `Spike.java`, `Lyrics.java`, `Prayer.java`, `Weather.java`, `Where.java`, `Zones.java`, `Proxy.java`, `BrowseProbe.java`, `History.java`, `SpikeListener.java` (rename to `MediaListener.java`, class `MediaListener`)
- Modify (the copies): `Proxy.java` uses `MediaListener.class`; `Weather.java` adds `int code;` and sets it in `fetch` (as `prayerlive` does); `Prayer.java` adds `static Data recompute(Data d, long nowMs)` and makes the old `recompute(Data d)` call it with `System.currentTimeMillis()`.
- Create: `headunit/src/main/java/com/glass/headunit/LogActivity.java`
- Test: `headunit/src/test/java/com/glass/headunit/PrayerRolloverTest.java`

**Interfaces:**
- Produces: all of the copied classes with their existing package-private signatures; `Prayer.recompute(Data, long nowMs): Data`; `LogActivity` (shows `Spike.dump()` refreshed every second, with a "Copy" button that puts it on the clipboard).

- [ ] **Step 1: Write the failing test** `PrayerRolloverTest` with `Prayer.Data` holding Subuh 05:48, Zohor 13:10, Asar 16:25, Maghrib 19:15, Isyak 20:27:
  - `afterIsyakNextIsTomorrowsSubuh`: `recompute(d, <today 22:00>)` gives `nextName == "Subuh"` and `nextMs` is after midnight of the following day.
  - `beforeSubuhCurrentIsLastNightsIsyak`: at 04:00, `currentName == "Isyak"`, `nextName == "Subuh"`, `nextMs` is today 05:48.
  - `justStartedForThreeMinutes`: at 13:11 `justStarted` is true; at 13:14 it is false.
- [ ] **Step 2: Run** `./gradlew :headunit:testDebugUnitTest --tests '*PrayerRolloverTest'` — Expected: FAIL (module or overload missing).
- [ ] **Step 3:** Create the module files and copies above. `build.gradle`: namespace and applicationId `com.glass.headunit`, `versionName '0.1-headunit'`, release/debug both signed with `headunit/keystore/headunit.jks` once generated (Step 4); dependencies as in the Tech Stack line; `testImplementation 'junit:junit:4.13.2'` and `'org.json:json:20231013'`. Manifest: permissions INTERNET, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION; the `<queries>` block from `app/src/main/AndroidManifest.xml`; the `MediaListener` service (exported, `BIND_NOTIFICATION_LISTENER_SERVICE`); `LogActivity`. Add `recompute(Data, long)` by moving the body of the old method and replacing `Calendar.getInstance()` with one set to `nowMs`.
- [ ] **Step 4:** Generate the signing key once: `keytool -genkeypair -keystore headunit/keystore/headunit.jks -alias headunit -keyalg RSA -keysize 2048 -validity 10000` (password stored by the user; `README.txt` says never to lose it, because a different key forces an uninstall on the units).
- [ ] **Step 5: Run** the test again — Expected: PASS. Then `./gradlew :headunit:assembleDebug` — Expected: BUILD SUCCESSFUL.
- [ ] **Checkpoint:** tests pass, APK builds, `LogActivity` opens on the phone (`$ADB -s $S install -r ...` then `$ADB -s $S shell am start -n com.glass.headunit/.LogActivity`).

### Task 2: Settings store and split-ratio maths

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/Settings.java`, `SplitMath.java`, `PanelMode.java`
- Test: `headunit/src/test/java/com/glass/headunit/SplitMathTest.java`, `PanelModeTest.java`

**Interfaces:**
- Produces:
  - `SplitMath.Result { float mapShare; boolean widened; }`
  - `static SplitMath.Result SplitMath.effective(float requestedMapShare, int screenWidthPx, float density)` — panel width in dp = `screenWidthPx / density * (1 - share)`; below 320 dp the share is lowered to `1 - 320/widthDp`, floored at 0.5; `widened` is true when it was lowered.
  - `static float SplitMath.snap(float share, float[] offered)` — nearest offered position.
  - `enum PanelMode { COMPACT, REGULAR }` with `static PanelMode forWidthDp(int dp)` (COMPACT below 400 dp).
  - `Settings` (backed by SharedPreferences `"settings"`): `float mapShare()` default 0.70f; `int textSize()` 0/1/2 (small/medium/large) default 1; `int theme()` 0 follow-unit, 1 light, 2 dark default 0; `boolean swapSides()` default false; `String manualZone()` empty = automatic; `boolean bubble()` default false; matching setters; `static Settings of(Context)`.
- [ ] **Step 1: Write the failing tests:**
  - `wideScreenKeepsSeventyThirty`: 1280 px at density 1 and share 0.7 gives 0.7, `widened` false.
  - `narrowScreenWidensPanel`: 800 px at density 1 and 0.7 gives 0.6, `widened` true.
  - `tinyScreenFallsToFiftyFifty`: 600 px at density 1 and 0.7 gives 0.5, `widened` true.
  - `snapPicksNearestOffered`: `snap(0.68f, {0.5f, 0.667f, 0.8f})` returns 0.667f.
  - `compactBelow400dp`: 399 gives COMPACT, 400 gives REGULAR.
- [ ] **Step 2: Run** `./gradlew :headunit:testDebugUnitTest --tests '*SplitMathTest' --tests '*PanelModeTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** the signatures above (`Settings` is trivial SharedPreferences, no test).
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Checkpoint:** tests pass, module builds.

### Task 3: Location following

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/MoveTrigger.java`, `Cache.java`, `LocationFollower.java`
- Test: `headunit/src/test/java/com/glass/headunit/MoveTriggerTest.java`, `CacheTest.java`

**Interfaces:**
- Produces:
  - `MoveTrigger.isUsable(double lat, double lon): boolean` — false for NaN or exactly (0,0).
  - `MoveTrigger.shouldRefresh(double lastLat, double lastLon, double lat, double lon): boolean` — great-circle distance ≥ 5000 m. The caller compares against the last position that actually **caused a refresh**, not the last fix seen.
  - `Cache.toJson(Prayer.Data): String`, `Cache.prayerFromJson(String): Prayer.Data`, `Cache.toJson(Weather.Data): String`, `Cache.weatherFromJson(String): Weather.Data`; and Context wrappers `Cache.savePrayer/loadPrayer/saveWeather/loadWeather(Context, ...)` that also store the date of saving; `Cache.prayerIsFromToday(Context): boolean`.
  - `LocationFollower`: `LocationFollower(Context, Listener)`, `start()`, `stop()`, with `interface Listener { void onMoved(double lat, double lon); }`. It registers `requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000L, 0f, ...)` only (never GPS or network), and every 10 minutes reads `Where.get(ctx)` as the fallback. It ignores unusable fixes and only calls `onMoved` when `shouldRefresh` is true against the last refresh position (the first usable fix always calls it).
- [ ] **Step 1: Write the failing tests:**
  - `MoveTriggerTest`: Kuala Lumpur (3.1478, 101.6953) to Sepang (2.7, 101.7) is true; 1 km apart is false; 4.9 km false, 5.1 km true; `isUsable(0,0)` false; `isUsable(Double.NaN, 1)` false.
  - `CacheTest`: a `Prayer.Data` with five times, `zoneCode "SGR01"`, `syuruk "07:00"` survives `toJson`→`prayerFromJson` with equal times, zone and syuruk; a `Weather.Data` (tempC 31, code 53, city "Sepang") likewise.
- [ ] **Step 2: Run** `./gradlew :headunit:testDebugUnitTest --tests '*MoveTriggerTest' --tests '*CacheTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** the signatures (Haversine for distance; `org.json` for the cache).
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Checkpoint:** tests pass and the module builds. `LocationFollower` is exercised in Task 5.

### Task 4: Media follower and lyric timeline

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/NowPlaying.java`, `MediaFollower.java`
- Test: `headunit/src/test/java/com/glass/headunit/LyricLineTest.java`

**Interfaces:**
- Produces:
  - `NowPlaying` (immutable snapshot): `String pkg, title, artist, playerLabel; boolean playing; long positionMs, durationMs; Lyrics lyrics; boolean lyricsLoading; int line;` (`line` is -1 before the first line; `lyrics` null when none) and `static NowPlaying idle()`.
  - `MediaFollower(Context, Listener)`, `start()`, `stop()`, `interface Listener { void onChanged(NowPlaying n); }`, plus `play()`, `pause()`, `next()`, `previous()`, `seekTo(long ms)`.
  - Behaviour: every 2 s `Proxy.findActive`; attach/detach as in `SpikeService.pickTick` (`app/.../SpikeService.java:207`); on a title/artist change fetch lyrics off the main thread via `Lyrics.fetch`, setting `lyricsLoading`; every 500 ms recompute `line` from the interpolated position (`SpikeService.position()` logic) and call `onChanged` only when something visible changed (line, title, playing, or loading). When the session disappears, emit `NowPlaying.idle()` and drop the lyrics.
- [ ] **Step 1: Write the failing test** `LyricLineTest` using `Lyrics` with times `[1000, 5000, 9000]`: `indexAt(0)` is -1; `indexAt(1000)` is 0; `indexAt(8999)` is 1; `indexAt(60000)` is 2; and a helper `NowPlaying`-free function `Lyrics.nextNonEmpty(int from)` (add to the copy: first index ≥ `from` whose line is non-empty, else -1) returns 2 for lines `["a","","c"]` and `from = 1`.
- [ ] **Step 2: Run** `./gradlew :headunit:testDebugUnitTest --tests '*LyricLineTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** the signatures and the `nextNonEmpty` addition.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Checkpoint:** tests pass and builds; media reading is exercised on the phone in Task 5.

### Task 5: Panel screen and its refresh controller

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/PanelActivity.java`, `PanelController.java`, `PanelView.java`, `res/layout/panel_regular.xml`, `res/layout/panel_compact.xml`, `res/values/themes.xml`
- Modify: `AndroidManifest.xml` (PanelActivity is the LAUNCHER activity, `android:resizeableActivity="true"`, `configChanges="screenSize|smallestScreenSize|screenLayout|orientation|density"` so a split resize does not restart it)

**Interfaces:**
- Consumes: `NowPlaying`, `MediaFollower`, `LocationFollower`, `Cache`, `Settings`, `PanelMode`, `Prayer.fetch/recompute`, `Weather.fetch`, `Zones.byCode`.
- Produces:
  - `PanelController(Context, Listener)` with `start()`, `stop()`, `refreshNow()`, `interface Listener { void onData(Prayer.Data p, Weather.Data w, boolean offline, boolean locationMissing); }`. It: shows cached data at once; fetches on `LocationFollower.onMoved`, on zone change, on a new day (`Cache.prayerIsFromToday` false), and weather every 30 min; on failure sets `offline` and retries every 120 s; uses `Settings.manualZone()` when set (fetch prayer for that zone's centroid via `Zones.byCode`, weather from the last fix).
  - `PanelView` binds a `NowPlaying` + data to the views: top strip (weather emoji, °C, city; next prayer name, time, countdown redrawn every 1 s); song title, artist, player button, previous/play-pause/next; lyrics list (current line large and highlighted, previous and next lines dimmer, scrolled only when `line` changes; empty instrumental lines are skipped with `Lyrics.nextNonEmpty`, and while `line == -1` the title and artist are shown in the lyrics area instead of a line). Idle (`pkg == null`): hide lyrics and enlarge the prayer and weather block. No lyrics: show title and artist large with controls. Banners (each with its button): "Allow media access" → notification-listener settings; "Google Maps isn't installed" when `com.google.android.apps.maps` is missing; "Location unavailable" (small); "Offline" mark.
  - Layout chosen by `PanelMode.forWidthDp(...)` on every configuration change; text sizes and theme from `Settings`; if `swapSides()` is true nothing in the panel changes (the helper handles sides).
  - Demo mode for checking without a car: `--ez demo true` feeds a fixed `NowPlaying` (a song with 12 sample lyric lines advancing every 3 s, a sample `Prayer.Data` and `Weather.Data`).
- [ ] **Step 1:** Implement the controller, view and layouts per the signatures above.
- [ ] **Step 2: Build** `./gradlew :headunit:assembleDebug` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Verify on the phone** (unlock it; install with `$ADB -s $S install -r headunit/build/outputs/apk/debug/headunit-debug.apk`): start with `am start -n com.glass.headunit/.PanelActivity --ez demo true`, capture `$ADB -s $S exec-out screencap -p > <scratchpad>/panel_demo.png`, and read the image: the countdown ticks once a second, the highlighted line advances every 3 s without the list jumping, and at the narrowest the compact layout shows no clipped text. Repeat with `wm size 540x960` and `wm size reset`, and with text size large.
- [ ] **Step 4: Verify real data on the phone:** with a real song playing in a supported player and notification access granted to the app, the panel shows its title, artist, lyrics and controls; pausing, switching tracks and closing the player each update it (idle on close). Verify offline: switch the phone to airplane mode, the cached data stays with the "Offline" mark; turn it off and it refreshes within about 2 minutes.
- [ ] **Checkpoint:** builds; demo and real-data checks pass on the phone; banner buttons open the right Settings pages.

### Task 6: Settings screen

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/SettingsActivity.java`, `ZonePickerActivity.java`, `res/layout/settings.xml`
- Modify: `AndroidManifest.xml` (activities), `PanelView`/`PanelActivity` (a settings entry on the panel)

**Interfaces:**
- Consumes: `Settings`, `SplitMath.effective`, `Zones` (needs a new `static List<Zone> Zones.all()` added to the copy), `LogActivity`.
- Produces: `SettingsActivity` with: map/panel ratio slider 50–80% showing the effective ratio and a "widened for this screen" note from `SplitMath.effective`; text size and theme choices; swap-sides switch; manual prayer zone row opening `ZonePickerActivity` (a list of `Zones.all()` plus "Automatic"); floating-bubble switch with "reset position"; a setup checklist (notification access, accessibility helper, display-over-other-apps, location) each showing granted or not with a button to the right Settings page; a "Log" button opening `LogActivity`.
- [ ] **Step 1:** Implement `Zones.all()` and the activities per the signatures.
- [ ] **Step 2: Build** — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Verify on the phone:** change each setting and see it apply to the panel (text size, theme, manual zone changes the prayer times and zone code in the log; "Automatic" returns to the GPS zone); the slider shows the widened note at a narrow `wm size`.
- [ ] **Checkpoint:** builds; settings persist after the app is closed and reopened.

### Task 7: Player picker (switch, browse, search, recent)

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/PlayerSwitcher.java`, `PlayerPicker.java`, `res/layout/picker.xml`
- Modify: `PanelView` (player button opens `PlayerPicker` over the panel)

**Interfaces:**
- Consumes: `Proxy.players/find/wake/label`, `BrowseProbe.load(ctx, pkg, parentId, cb)`, `History`, `MediaFollower`.
- Produces:
  - `PlayerSwitcher(Context, MediaFollower)`: `void switchTo(String pkg)` (pause the current, play the chosen; if it has no session send the media Play button via `Proxy.wake(ctx, pkg, false)`, and after 3 s open its screen via `Proxy.wake(ctx, pkg, true)`; give up after 15 s with a log line, as in `SpikeService.waitForPlayer`), `void playSong(String pkg, String title, String artist)` (search-based play as in `SpikeService.searchNow`), `void playItem(String pkg, String mediaId)` (play a browsed item).
  - `PlayerPicker`: a view with (1) the installed supported players (filtered from `Proxy.players` to the five supported ones) with the current one marked; (2) per player, a "Browse" entry shown **only after** a `BrowseProbe.load` for that player succeeded, opening its list (folders open further, playable items call `playItem`); (3) for every player a search box (and the keyboard's voice key) that calls `playSong` and a "Recent" list from `History.recent`; recording each played track with `History.record` happens in `MediaFollower` on a track change.
- [ ] **Step 1:** Implement per the signatures.
- [ ] **Step 2: Build** — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Verify on the phone:** which of the installed players accept `BrowseProbe` (the log shows `connected` versus `connection REFUSED`); the picker offers browse only for those; switching between two players pauses the first and starts the second; a search for a known song starts it; recent songs appear after playing a few.
- [ ] **Checkpoint:** builds; switching and search work on the phone; the browse results for each player are noted in the log for the units.

### Task 8: Floating bubble

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/BubbleService.java`
- Modify: `AndroidManifest.xml` (`SYSTEM_ALERT_WINDOW`, the service), `PanelController` or `PanelActivity` (start or stop the service when `Settings.bubble()` changes), `SettingsActivity` (switch and permission button)

**Interfaces:**
- Consumes: `Settings.bubble()`, `PlayerPicker`, `Settings` position keys (add `bubbleX(): int`, `bubbleY(): int`, `setBubble(int x, int y)`, `resetBubble()`).
- Produces: `BubbleService` — a `TYPE_APPLICATION_OVERLAY` window with a small round static icon; drag moves it and saves the position; tap opens the `PlayerPicker` as an overlay window sized to the panel's half of the screen (using the effective ratio and `swapSides`); first position is inside the panel's half. If the overlay permission is missing the service does not start and `SettingsActivity` shows a button to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- [ ] **Step 1:** Implement per the signatures.
- [ ] **Step 2: Build** — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Verify on the phone:** with the permission granted, enabling the setting shows the bubble over another app; drag, then kill and restart the service and it returns to the saved place; tap opens the picker overlay on the panel's side; "reset position" works; revoking the permission stops it with a Settings button shown, not a crash.
- [ ] **Checkpoint:** builds; bubble checks pass on the phone.

### Task 9: Window helper, boot start and divider ratio

**Files:**
- Create: `headunit/src/main/java/com/glass/headunit/SplitHelperService.java`, `BootReceiver.java`, `SplitProbeActivity.java`, `res/xml/split_helper.xml`
- Modify: `AndroidManifest.xml` (accessibility service, `RECEIVE_BOOT_COMPLETED`, `BootReceiver`, `SplitProbeActivity`, `<queries>` entry for `com.google.android.apps.maps`)

**Interfaces:**
- Consumes: `Settings.mapShare()`, `Settings.swapSides()`, `SplitMath.effective`, `SplitMath.snap`.
- Produces:
  - `SplitHelperService extends AccessibilityService` (`canPerformGestures="true"`): `static boolean isRunning()`; `void startLayout()` — if the panel is not already in split-screen, launch the panel, perform `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN`, launch Google Maps (`com.google.android.apps.maps`) adjacent with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT`, honouring `swapSides`; then move the divider toward `snap(effective(...).mapShare, offered)` using `dispatchGesture` (the divider is found as the window of type `TYPE_SPLIT_SCREEN_DIVIDER` from `getWindows()`). `offered` comes from the measured positions (Step 3). Failure at any step logs why and leaves the panel full-screen with its "Open Maps" button (Task 5 banner behaviour extended with that button).
  - `BootReceiver` (BOOT_COMPLETED and QUICKBOOT_POWERON): if the helper is enabled call `startLayout()`; if not, log that the helper is off.
  - `SplitProbeActivity`: an on-unit test screen with buttons "Toggle split", "Open Maps beside", "Measure divider", writing to the log: whether each step happened, the divider's window bounds, and the positions the divider reaches after a gesture drag to 30%, 50%, 70% and 80% of the width.
- [ ] **Step 1:** Implement `SplitProbeActivity` and the helper's individual steps first (toggle, adjacent launch, divider measure), then `startLayout()` and `BootReceiver`.
- [ ] **Step 2: Build** — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Measure on each unit** (see Task 10 for installing): run the probe screen on the Carlinkit and the Eonon, copy each log out (adb on the Carlinkit, the Copy button on the Eonon), and put the measured divider positions into a `static final float[] OFFERED_CARLINKIT / OFFERED_EONON` table keyed by `Build.MODEL` in `SplitHelperService`, with 0.5f as the default for an unknown model. Note any behaviour that differs between the two units.
- [ ] **Step 4: Verify on each unit:** reboot, the helper (if enabled) puts the panel and Google Maps side by side at the nearest offered ratio, with `swapSides` mirroring them; with the helper disabled the panel still opens manually and works.
- [ ] **Checkpoint:** builds; the probe logs from both units are saved to the plan's scratchpad notes; auto-start works or fails with a clear log line on each unit.

### Task 10: Release APK, install and heat check

**Files:**
- Create: `headunit/INSTALL.md` (short: how to put the APK on a unit)
- Modify: `headunit/build.gradle` (release signing, `minifyEnabled false`)

- [ ] **Step 1: Build** `./gradlew :headunit:assembleRelease` — Expected: `headunit/build/outputs/apk/release/headunit-release.apk` signed with `headunit.jks`. Copy it to `docs/GlassHeadUnit-<versionName>.apk`.
- [ ] **Step 2: Install on the Carlinkit** over adb: `adb install -r docs/GlassHeadUnit-0.1-headunit.apk`. **Install on the Eonon** by copying the APK through Google Drive, opening it from the unit's file manager or Drive app, and allowing "install unknown apps" for that app once. Then, on each unit, enable by hand: notification access, accessibility helper, display over other apps (if the bubble is wanted), and location. `INSTALL.md` lists these.
- [ ] **Step 3: Verify the real-car behaviour on both units:** panel beside Maps, lyrics for each of Spotify, YouTube Music, Bitchord, VRadio and local files; the weather and zone follow a drive of more than 5 km (the log shows `moved` and the new zone code); the player picker works; settings apply; the log copies out.
- [ ] **Step 4: Heat check:** with the panel open and a song playing for 30 minutes, on the Carlinkit read `adb shell dumpsys cpuinfo | grep headunit` and `adb shell dumpsys battery` (temperature), expecting the app near 0–2% CPU; on the Eonon (no adb) note how warm the unit feels against the earlier setup and report it.
- [ ] **Checkpoint:** both units run the same APK; the list of per-unit differences is written to `INSTALL.md`.
