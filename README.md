# Glass Auto

A panel for Android head units that sits beside Google Maps in split-screen: lyrics for whatever is playing,
weather and prayer times, a player shortcut, and a floating bubble. It targets Android 12+ units
(tested on a Xiaomi Pad Mini; meant for a Carlinkit Ultra 2 and an Eonon UX7ST).

## Layout

| Folder | What it is |
|---|---|
| `headunit/` | The app (package `com.glass.headunit`). |
| `legacy/` | The earlier Android Auto experiments, kept for reference. Not built by default. |
| `docs/superpowers/` | Design specs, implementation plans and the progress ledger. |

## Build

Needs JDK 17 and the Android SDK (put its path in `local.properties` as `sdk.dir=...`).

```bash
./gradlew :headunit:testDebugUnitTest   # unit tests
./gradlew :headunit:assembleDebug       # headunit/build/outputs/apk/debug/headunit-debug.apk
```

## Signing

Builds are signed with a key in `headunit/keystore/` (not in git). To make one:

```bash
keytool -genkeypair -keystore headunit/keystore/headunit.jks -alias headunit -keyalg RSA -keysize 2048 -validity 10000
```

Then create `headunit/keystore/keystore.properties`:

```
storeFile=keystore/headunit.jks
storePassword=...
keyAlias=headunit
keyPassword=...
```

Back both files up somewhere safe outside this folder. Without that file the build falls back to the debug key.
Never commit them. APKs are shared as downloads, not stored in git.

## Install on a unit

See `headunit/INSTALL.md`.
