# Changelog — All Custom Changes

This is a comprehensive list of every modification, new feature, and bug fix
applied on top of the upstream Immortal v1.34 codebase. Each entry includes
the file(s) touched and a brief description.

## 1. Custom accent colors

**Files:** `Color.kt`, `Theme.kt`, `ImmortalSettings.kt`, `ImmortalSettingsActivity.kt`, `HomeActivity.kt`, `FolderPickerActivity.kt`, `HelpActivity.kt`, `StoreActivity.kt`, `ScreensaverSettingsActivity.kt`

Added 16 accent color options (blue, red, green, purple, orange, pink, teal,
yellow, indigo, cyan, lime, amber, deep purple, brown, coral, mint). The
selected color is persisted in `ImmortalSettings` and applied through
`SampleAppTheme` so the change propagates instantly to every Material 3
component in the app. Replaced all hardcoded `Color(0xFF2E6BE6)` references
with `MaterialTheme.colorScheme.primary` so the accent color actually
takes effect across all UI elements.

## 2. Custom background image

**Files:** `ImmortalSettings.kt`, `ImmortalSettingsActivity.kt`, `HomeActivity.kt`

Added a background image setting with three modes: Dark (default solid
background), Image (user-chosen image via system file picker), and Blur.
The image is loaded asynchronously in `BackgroundImage` and rendered
behind the app grid with a dark overlay for readability. URI permissions
are persisted via the picker contract.

## 3. New Folder button in Manage mode

**Files:** `HomeActivity.kt`, `UserLayout.kt`

Added a blue "+" button next to the Manage/Done toggle. Tap it to name and
create a new empty folder. The folder is persisted via `UserLayout` and
appears in the grid immediately.

## 4. Right-edge back gesture (launcher)

**Files:** `HomeActivity.kt`

Swipe left from the right 64dp of the screen to close any open folder
overlay. Uses Compose `awaitEachGesture` for reliable detection.

## 5. Floating back button on all settings activities

**Files:** `ImmortalSettingsActivity.kt`, `ClockSettingsActivity.kt`, `ScreensaverSettingsActivity.kt`

Added a `FloatingBackButton` composable in the bottom-right corner of
every settings screen. Tap to close the screen. (The launcher folder
overlay no longer shows this button — it was distracting.)

## 6. Digital clock screensaver (complete subsystem)

**Files:** `DigitalClockDreamService.kt`, `DigitalClockView.kt`, `DigitalClockConfig.kt`, `DigitalClockPreviewActivity.kt`, `ClockSettingsActivity.kt`, `ScreensaverSettingsActivity.kt`, `AndroidManifest.xml`

A full-featured digital clock as an alternative to the photo frame
screensaver, with:

- **6 styles:** Classic, Flip, Bold, Neon, Segment, Analog
- **8 colors:** White, Red, Green, Blue, Yellow, Cyan, Pink, Orange
- **5 built-in fonts:** Light, Normal, Bold, Mono, Serif (all distinct on Android 9)
- **3 bundled LED fonts** loaded from `assets/fonts/`: segment_led.ttf (14-segment LED), digital_7.ttf, technology.ttf + technology_bold.ttf
- **4 sizes:** Small, Medium, Large, XL
- **4 layouts:** Center, Top, Bottom, Minimal
- **4 backgrounds:** Black, Gray, Gradient, Transparent
- **3 glow levels:** None, Soft, Strong (glow color matches the selected clock color)
- **Show date / show seconds toggles**
- **High-quality analog clock:** clean face, all 12 numbers, cardinals bolder, proper hour markers, refined hour/minute hands, optional red second hand

A `DigitalClockPreviewActivity` provides a full-screen preview so the
user can see changes before activating the screensaver.

## 7. System-wide back gesture (accessibility + overlay)

**Files:** `ImmortalBackGestureService.kt`, `SystemBackGestureService.kt`, `BackHelper.kt`, `back_gesture_service_config.xml`, `AndroidManifest.xml`, `ClockSettingsActivity.kt`, `strings.xml`

Two complementary mechanisms:

- **`ImmortalBackGestureService`** — An accessibility service that
  exposes a global BACK action. User enables once in Android Settings →
  Accessibility, then it appears in the accessibility navigation bar and
  performs `GLOBAL_ACTION_BACK` system-wide.
- **`SystemBackGestureService`** — A foreground service that draws a
  transparent strip on the right edge of the screen. Swipe leftward
  anywhere to go back. Requires "Draw over other apps" permission.

`BackHelper` is a shared utility that performs the back action and then
ensures the user lands on the Immortal launcher (not the Portal's stock
Aloha home screen) by detecting when the back action would exit to the
home screen and re-routing to `HomeActivity`.

The user enables the system-wide swipe from **Settings → Clock → Back
shortcut → Swipe-from-right back** toggle. A direct `settings put secure`
fallback is used to enable the accessibility service on the Portal since
the stock Settings app hides third-party accessibility services.

## 8. Clock settings activity (new dedicated page)

**Files:** `ClockSettingsActivity.kt`, `AndroidManifest.xml`, `HomeActivity.kt`

Moved all digital clock settings out of the Immortal settings page into
a new dedicated `ClockSettingsActivity`, reachable from a new "Clock"
tile in the launcher's Settings folder. The Screensaver settings page
now only handles the photo frame (Source/Display/Power) — the digital
clock options were removed from there.

## 9. Calendar agenda widget

**Files:** `CalendarHelper.kt`, `ImmortalSettings.kt`, `HomeActivity.kt`, `AndroidManifest.xml`

New optional widget on the home screen that shows upcoming calendar
events. Reads from the system calendar provider. Requires
`READ_CALENDAR` permission. Toggle on/off in **Settings → Immortal →
Calendar agenda**.

## 10. Screensaver activation toggles

**Files:** `ImmortalSettings.kt`, `SettingsGuard.kt`, `ClockSettingsActivity.kt`

Added "Activate on sleep" and "Activate on dock" toggles that control
the system `screensaver_activate_on_sleep` and `screensaver_activate_on_dock`
secure settings. Required for the digital clock to show when the screen
turns off (the Portal's stock behavior is to go straight to Asleep state).
The `SettingsGuard.applyActivationSettings()` helper applies them
immediately and re-asserts them on resume so a stock-launcher reset
won't undo the user's choice.

## 11. Bundled LED fonts

**Files:** `assets/fonts/segment_led.ttf`, `assets/fonts/digital_7.ttf`, `assets/fonts/technology.ttf`, `assets/fonts/technology_bold.ttf`

Four true-type fonts bundled into the APK and loaded via
`Typeface.createFromAsset`. A `bundledFontCache` memoizes the load so
we only read each font once. If a font file is missing, the option
silently falls back to the default font.

## 12. Build / infrastructure changes

**Files:** `app/build.gradle.kts`

Removed the `.debug` applicationId suffix so debug builds overwrite the
release package directly (needed to replace the original `com.immortal.launcher`
on the device). The user had to disable the package via `pm disable-user`
and clear it before the new debug build could install.

## 13. Bug fixes

- **Overnight sleep re-locks screen after every tap** — original bug where
  `HomeActivity.onResume` unconditionally called `ScreenControl.sleep()` when
  inside the overnight window. Documented as an issue for the upstream
  repo.
- **Flip clock seconds stuck** — seconds card views were captured at
  `buildUi()` time and never updated. Fixed by storing `flipHour`,
  `flipMinute`, `flipSecond` as fields and updating them in `tick`.
- **Flip clock grey background** — removed the semi-transparent white card
  background so the clock looks clean on dark backdrops.
- **Glow not matching color** — glow was hardcoded black for soft and only
  used color for strong. Now both modes tint with the selected clock color.
- **Settings UI not updating on selection** — `Segmented` controls used
  `tvFocusable` which doesn't respond reliably to touch. Replaced with
  `clickable` so selections update instantly.
- **Digital clock screensaver overrides kept reverting** — added a
  `SettingsGuard.reaffirmScreensaver()` self-heal that runs on every
  HomeActivity resume to re-assert Immortal's dream component after the
  stock Portal launcher overwrites it.

## 14. Permissions added

**Files:** `AndroidManifest.xml`

- `READ_CALENDAR` — for the calendar agenda widget
- `SYSTEM_ALERT_WINDOW` — for the system-wide back-swipe overlay
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` — for the
  overlay back-gesture foreground service

## 15. Ambient / "go outside and look" tiles (1.40)

**Files:** `IssPasses.kt`, `Aurora.kt`, `StarField.kt`, `HomeActivity.kt`,
`ImmortalSettings.kt`, `ImmortalSettingsActivity.kt`, `Weather.kt`,
`IssPassesTest.kt`, `AuroraTest.kt`, `BacklogLogicTest.kt`

- **ISS pass predictor** — self-contained near-Earth SGP4 propagator + topocentric
  look angles + sun/shadow visibility; one keyless TLE fetch (Celestrak →
  wheretheiss.at), cached, then offline. `IssTile` + `IssOverlay`. Validated against
  the canonical Vallado vector.
- **Aurora** — NOAA SWPC planetary K-index vs geomagnetic latitude (tilted dipole)
  and the Kp-driven oval edge; `AuroraTile` lights up only on a real chance.
- **Constellation background** — `StarField` bright-star catalogue projected to the
  horizon via LST; new `BG_STARS` background mode + `StarFieldBackground`.
- Added `Weather.coordinates()` to expose the cached device location to these.

## 16. Kitchen & quick tools (1.40)

**Files:** `Converter.kt`, `HomeActivity.kt`, `ConverterTest.kt`

- **Stopwatch / count-up** with laps (`StopwatchOverlay`).
- **Converter** — offline unit math (length/mass/volume/speed/temp) + keyless ECB
  currency via Frankfurter (cached). `ConverterTile` + `ConverterOverlay`.

## 17. Bedroom: sunrise alarm, lamp, bedtime stories, anti-burn-in (1.40)

**Files:** `SunriseConfig.kt`, `SunriseScheduler.kt`, `SunriseReceiver.kt`,
`WakeLightActivity.kt`, `SunriseSettingsActivity.kt`, `LampActivity.kt`,
`Stories.kt`, `BedtimeStoryActivity.kt`, `AntiBurnIn.kt`,
`DigitalClockDreamService.kt`, `ImmortalApp.kt`, `BootReceiver.kt`,
`AndroidManifest.xml`, `BacklogLogicTest.kt`

- **Sunrise alarm / wake light** — AlarmManager → `WakeLightActivity` brightness ramp
  + optional `ChimePlayer` crescendo; re-armed in `ImmortalApp` + `BootReceiver`.
- **Lamp mode** — full-screen warm-white panel (`LampActivity`).
- **Bedtime stories** — public-domain tales (`Stories.kt`) read aloud via TTS.
- **Anti-burn-in** — `AntiBurnIn` slow pixel-shift applied to the clock dream.

## 18. Household, calendar packs, self-knowledge, gestures (1.40)

**Files:** `PingService.kt`, `CalendarPacks.kt`, `IrishHolidays.kt`,
`PrayerTimes.kt`, `WhatChanged.kt`, `Tips.kt`, `GestureCamera.kt`,
`ScreensaverConfig.kt`, `ScreensaverSettingsActivity.kt`, `HomeActivity.kt`,
`ImmortalSettingsActivity.kt`

- **Ping the other room** — serverless LAN UDP (`PingService`) + `PingTile`.
- **Calendar packs** — `CalendarPacks` + `IrishHolidays` + `PrayerTimes`; header lines
  + settings toggles.
- **What's New / Request an app / Tips** — `WhatChanged` (GitHub releases), prefilled
  issue tile, `Tips` daily card.
- **Wave to advance (experimental)** — `GestureCamera` Camera2 frame-differencing wired
  into `PhotoFrameController` behind `ScreensaverConfig.gestureWave` (off by default).
