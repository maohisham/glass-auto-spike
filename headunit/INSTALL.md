# Installing Glass Panel on a head unit

The APK is `docs/GlassHeadUnit-0.1-headunit.apk`. It is always signed with the same key, so a newer APK installs over an older one without uninstalling (keep `headunit/keystore/` safe: a different key forces an uninstall).

## Put the APK on the unit

- **Carlinkit Ultra 2 (adb):** `adb install -r docs/GlassHeadUnit-0.1-headunit.apk`
- **Eonon UX7ST (no adb):** copy the APK to Google Drive from the Mac, open Drive on the unit, tap the file, and allow "install unknown apps" for Drive the first time.

## Turn things on (once per unit, by hand in Settings)

1. **Notification access** for Glass Panel, so it can read what is playing (the panel shows a button for this).
2. **Location**, so the prayer zone and weather follow the car.
3. **Window helper** (Accessibility → Glass Panel), only if you want split-screen to start by itself at boot.
4. **Display over other apps**, only if you want the floating bubble. It also lets the app open itself at boot if the window helper is off.
5. If the unit has an auto-start or "allow background start" list, add Glass Panel to it.

Then open Glass Panel → Settings. The "Setup" list at the bottom shows what is still missing.

## First run on each unit

Open Settings → "Split-screen test", press each button and then "Open the log" → "Copy the log". The positions the divider reaches tell us what ratios that unit allows. Put the answers in the table below.

| | Carlinkit Ultra 2 | Eonon UX7ST | Xiaomi Pad Mini (test tablet, Android 16) |
|---|---|---|---|
| Split-screen toggles from the helper | not tested yet | not tested yet | **Refused** (4 of 4 tries, helper on). Split it by hand instead. |
| Maps opens beside the panel | not tested yet | not tested yet | **Works:** the panel's Open Maps button makes the system split (no toggle needed), then the helper moves the divider. Shows 50:50 for a moment, then the set ratio. |
| Divider positions it reaches | not tested yet | not tested yet | About 33%, 50%, 67% (first, noisy run; a clean re-run is still open). 70:30 is not reachable; nearest is 67:33. |
| Auto-start at boot | not tested yet | not tested yet | not tested |
| Which players allow browsing | not tested yet | not tested yet | VRadio: refused. YouTube Music: not checked yet. |

## Notes from the Xiaomi tablet

- After every reinstall the window helper is switched off by the system: turn it on again in Accessibility → Downloaded apps → Glass Panel.
- The helper disconnected and reconnected on its own a few times within a minute; on a unit that does this, auto-start may need the unit's "keep running in background" setting for Glass Panel.
