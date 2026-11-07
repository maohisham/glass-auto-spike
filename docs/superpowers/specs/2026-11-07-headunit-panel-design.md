# Head unit panel: design

Date: 2026-11-07. Status: draft for review.

## Purpose

Replace the Android Auto approach (Glass Auto media card, Prayer Live) with one native app installed on the car's own head unit. The screen is split: Google Maps on the left, a panel on the right showing lyrics for whatever media is playing, the weather, and the next prayer time. The goal is to keep Google Maps turn-by-turn fully usable while the extras sit beside it, and to stop the phone overheating from projecting to the car.

## Confirmed facts (from the user)

- Head unit: aftermarket Android unit, Android 12 or newer, can install APKs.
- Internet: the unit's own SIM, always on.
- Music sources: Spotify, YouTube Music, Bitchord, VRadio, local files or USB.
- Startup: the app starts by itself when the car turns on.
- Maps: Google Maps stays a normal app, with real turn-by-turn. Building our own navigation is out of scope.
- Location: the panel's prayer zone, city and weather follow the car as it travels.
- Approach: split-screen (A), with floating windows (B) as the fallback.
- Two target devices, both with working split-screen today (the user already uses a split-screen launcher on both): a Carlinkit Ultra 2 (an Android box) and an Eonon UX7ST (an Android head unit). One APK must run on both. The firmware risk for approach A is therefore largely removed.

- Split ratio: the units launch split-screen at 50:50. The user wants a customizable setting, default 70:30 (70 map, 30 panel), depending on screen size.
- Install: the user sideloads the APK, copying it onto each device through Google Drive. The Carlinkit has adb access. The Eonon has no adb. Nothing may depend on an adb-only grant, and the app keeps an on-screen log with a copy button so the Eonon can be debugged without adb.

## Split ratio setting

- A settings screen in the app sets the map/panel ratio; default 70:30.
- The effective ratio is adapted to the screen: if the panel's share would be narrower than a readable minimum (about 320 dp wide), the ratio is widened toward the panel and the app says so. Wide screens can take 70:30 or more; small screens may need 60:40.
- The panel's layout switches to a compact arrangement when narrow, so the same APK works on both devices.
- The helper sets the ratio by moving the split divider (an accessibility gesture). Android's divider may snap to fixed positions, so each unit's achievable positions are measured first and the setting is rounded to the nearest one the unit offers. If a unit cannot hold a custom ratio, the setting tells the user so and the unit keeps its own 50:50.
- Without the helper, the ratio setting only influences the panel's layout; the user drags the divider by hand.

## Player shortcut

A player button on the panel's now-playing area opens a picker over the panel. An optional floating bubble opens the same picker from anywhere (see below).

**Floating bubble (optional, off by default, a setting).** A small round draggable bubble drawn above other apps. It needs the "display over other apps" permission, which the user grants by hand in Settings (no adb needed, so it works on the Eonon). It starts docked inside the panel's half so it does not cover Google Maps, remembers where the user drags it, and can be moved over the map if wanted. Tapping it opens the same picker as an overlay window sized to the panel's half, so the map stays visible. The bubble is static (no animation), so it adds no heat. If the permission is missing, the setting shows a button that opens the right Settings page instead of failing quietly.

1. **Pick the player:** lists the supported players that are installed (Spotify, YouTube Music, Bitchord, VRadio, the unit's local-files player). Choosing one pauses the current player and starts the chosen one, reusing the earlier switching logic (it wakes a player that has no session yet).
2. **Browse:** where a player's media browser service accepts our app, show its real library as a list and play the chosen item.
3. **Where browsing is refused** (expected for Spotify and YouTube Music, whose libraries are closed to third-party apps): a search box, voice search if the unit supports it, and a list of recently played songs. Playing starts by asking that app to play from a search, as the earlier work did.

Which players allow browsing is not known yet: it is checked with a probe on the phone and on each unit before the browse list is built, and the picker shows browse only for players that allow it.

## Settings (the full list)

The settings screen holds exactly these, and nothing else for now:
1. **Split ratio** (above), default 70:30.
2. **Text size and theme:** lyrics and panel text size (small, medium, large) and light, dark or follow the unit.
3. **Swap sides:** panel on the left and map on the right. The helper places the two apps accordingly; without the helper it only changes which side the panel's layout assumes.
4. **Manual prayer zone:** a JAKIM zone picker. When set, the zone no longer follows the GPS; a switch returns it to automatic.
5. **Floating bubble:** on or off (default off), with a "reset position" action.

Not included: showing, hiding or reordering panel sections.

## Not yet known

- (Answered) The user uses each unit's built-in split feature, not a separate launcher app. Whether our own helper can trigger that feature at boot is still to be tested per device.
- Whether a boot-time helper may open apps on Android 12 without a prompt, if we do ship our own.
- Whether each device has its own GPS, or gets location another way (the Carlinkit box is attached to the car's own screen).
- The unit's screen resolution and orientation (decides a 50/50 or 60/40 split).
- How APKs get onto the unit (USB stick, adb, store). Needed before the on-unit test.
- Whether Google Maps already shows a steady position on the unit today (a hardware matter we cannot fix).

## Scope

In scope: a new module `headunit` (package `com.glass.headunit`) with the panel screen, media reader, window helper and boot trigger below.

Out of scope: our own map or routing, Android Auto or CarPlay support, animated weather, the old `app`, `prayer` and `prayerlive` modules (left as they are, not deleted).

## Components

1. **Panel screen (right half).** A normal Android activity using ordinary views, not a drawing loop.
   - Top strip: weather icon, temperature and city; next prayer name, time and a countdown.
   - Below: song title, artist and controls (previous, play/pause, next).
   - Rest of the space: synced lyrics, current line large and highlighted, scrolling only when the line changes.
   - When nothing is playing: the next prayer and weather shown larger instead.
2. **Media reader.** A notification listener service (the user grants access once in Settings). It follows whichever app is playing and exposes title, artist, position, state and controls. It reuses the existing `Proxy` logic, including VRadio handling.
3. **Lyrics.** Reuse `Lyrics` unchanged: LRCLIB, then NetEase, then lyrics.ovh.
4. **Prayer and weather.** Reuse `Prayer`, `Zones`, `Weather` and `Where`, adding a cache and the location rules below.
5. **Window helper.** An accessibility service that Android starts at boot. It turns on split-screen, launches Google Maps on the left and the panel on the right.
6. **Boot trigger.** A boot-completed receiver that wakes the helper if the unit does not start it by itself.

## Location updates

- Listen to the passive location source, so fixes Google Maps already requested are delivered at no extra GPS cost. Never switch the GPS receiver on ourselves.
- When Maps is not running, read the unit's last known position every 10 minutes.
- Refresh triggers:
  - Moved about 5 km or more since the last fix: re-check the zone (the waktusolat GPS lookup), refresh weather and city.
  - Zone code changed: fetch that zone's times from JAKIM and switch the countdown.
  - New day: fetch the day's prayer times.
  - Weather: every 30 minutes, or on the move trigger.
- Cache the last zone, prayer times and weather, and show them at once at boot, then refresh when the SIM connects.
- With no fix at all: show the last known city with a small "location unavailable" note; a manual zone picker is the last fallback.
- Verify on the unit that a mock-location app, if the unit uses one, still reaches the passive listener.

## Failure handling

- Split-screen will not start: the panel fills the screen with a one-tap "Open Maps" button. If this is common on the unit, move to approach B.
- Google Maps missing or disabled: the panel says so and keeps working alone.
- Media access not granted: a banner with a button to the right Settings page.
- No lyrics from any source: show title and artist large, with controls.
- Nothing playing: show the next prayer and weather larger.
- No internet: keep the cached data with a small "offline" mark; retry every couple of minutes, not in a tight loop.

## Heat and battery

No continuous animation and no extra GPS. The countdown redraws once a second; lyrics change every few seconds; network refreshes follow the rules above.

## Build order and testing

The user's units (Carlinkit Ultra 2 and Eonon UX7ST) both have a working built-in split-screen feature, which they use today. So the panel comes first and relies on it; the window helper is a second step.

1. **Right panel on the phone.** Built and checked with fake "now playing" data and preview images, with no car needed: lyrics scroll, the countdown ticks, the layout fits. The panel must be resizable (works at any half-screen width and in both orientations) so the units' built-in split-screen accepts it.
2. **Panel on the units.** Install on both units, open it beside Google Maps with the unit's own split feature, and check layout, media reading, lyrics and location following on each.
3. **Auto-start.** The boot trigger and a window helper that turns split-screen on by itself and opens Maps and the panel. A small on-unit test first confirms the helper can trigger the unit's split feature on each device (they may differ).
4. **Heat check.** With the unit running, watch CPU use and temperature with the panel open for a long stretch.

## Decision log

- Chose split-screen (A) over floating windows (B) and a self-built map (C): keeps real Google Maps and turn-by-turn, and Android 12+ supports it well.
- Chose passive location over holding GPS: Maps already runs the receiver while navigating.
- Chose a plain view-based panel over a canvas animation: heat was the reason for the change.
