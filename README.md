# Waktu Sholat

Offline Indonesian prayer times for Android. Everything is computed on the device from a built-in
table of ~490 kota/kabupaten using the Kemenag method, so it needs no internet, no account and no
location permission. The release APK is about 130 KB.

## Features

- Today's schedule with a live countdown, Hijri date and interval progress
- Monthly timetable, Qibla compass, adhan notifications with optional reminder lead time
- Five home-screen widgets: **Sholat berikutnya**, **Jadwal hari ini**, **Jadwal lengkap**,
  **Hitung mundur** and **Minimalis**. Countdowns are ticked by the launcher, so the app does no
  work between prayers; widgets follow the wallpaper colours on Android 12+
- Light and dark theme, Android 7.0+ (API 24)

## Build

```sh
./gradlew :core:test :app:assembleDebug     # debug build
./gradlew :app:assembleRelease              # release build (R8 + resource shrinking)
```

Release signing reads `keystore.properties` locally (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) or `SIGNING_*` environment variables in CI. Without either it falls back to the
debug key.

## Modules

| Module | Contents |
|---|---|
| `core` | Pure Kotlin: prayer-time calculator, Umm al-Qura Hijri calendar, Qibla, city table generated from `data/cities.csv` |
| `app`  | Framework-only Android UI (no AppCompat/Material), notifications and widgets |

## CI/CD

GitHub Actions on [Blacksmith](https://blacksmith.sh) runners:

- **CI** (`.github/workflows/ci.yml`): tests, lint and a release build on every push and PR.
- **Release** (`.github/workflows/release.yml`): push a tag `vX.Y.Z` and a signed APK is published
  as a GitHub Release. `versionCode` is derived from the tag (`1.2.3` → `10203`).

Required secrets for releases: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

Runner: jobs use `blacksmith-4vcpu-ubuntu-2404` unless the repository variable `CI_RUNNER` is set
(e.g. `ubuntu-latest`). Blacksmith has to be installed for the repository's owner at
app.blacksmith.sh; without it, set `CI_RUNNER` so jobs do not wait for a runner that never comes.
