# Waktu Sholat

Prayer times for Android, computed entirely on the device. Pick any of ~490 Indonesian
kota/kabupaten offline, use the phone's precise GPS position anywhere in the world, or search any
place on Earth. English and Bahasa Indonesia. The release APK is under 200 KB.

## Features

- Today's schedule with a relative countdown ("in 7 h 33 min"), Hijri date and interval progress
- Illustrated sky that follows the part of the day: sun or moon on its arc, stars, clouds, skyline
- Worldwide: precise GPS fix with the phone's time zone (half-hour zones and DST included), or a
  worldwide place search; the time zone of a searched place is chosen offline from ICU data
- English and Bahasa Indonesia, switchable in Settings (and in the system's per-app language
  screen on Android 13+)
- Launcher icon themes, fixed or automatic (follows the time of day)
- In-app updates from GitHub Releases: daily check, download, SHA-256 verification, install
- Monthly timetable, Qibla compass, prayer-time notifications with optional reminder lead time
- Five home-screen widgets: **Next prayer**, **Today's times**, **Full schedule**, **Countdown**
  and **Minimal**. Relative times refresh each minute with a non-waking alarm (nothing runs while
  the screen is off); widgets follow the wallpaper colours on Android 12+
- Light and dark theme, Android 7.0+ (API 24)

## Build

```sh
./gradlew :core:test :app:assembleDebug     # debug build
./gradlew :app:assembleRelease              # release build (R8 + resource shrinking)
```

Release signing reads `keystore.properties` locally (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) or `SIGNING_*` environment variables in CI. Without either it falls back to the
debug key.

## Website

The landing page lives in [`site/`](site/) (Astro) and deploys to Cloudflare Pages (root `site`,
build `npm run build`, output `dist`). Run it locally with `cd site && npm install && npm run dev`. See [site/README.md](site/README.md).

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

Runner: jobs use `blacksmith-4vcpu-ubuntu-2404` (the Blacksmith app is installed on the
`rinci-labs` organization). Setting the repository variable `CI_RUNNER` (e.g. `ubuntu-latest`)
switches to GitHub-hosted runners, for forks or accounts without Blacksmith.
