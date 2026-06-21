# Architecture & Code Structure

This document describes how the custom features are organized in the
codebase. It complements the upstream Immortal architecture with notes on
the new modules added in this fork.

## Module layout

```
app/src/main/java/com/immortal/launcher/
├── ImmortalApp.kt              # Application class — registers receivers
├── ImmortalSettings.kt         # User preferences (accent, background, calendar, ...)
├── DigitalClockConfig.kt       # All digital clock settings
├── DigitalClockView.kt         # Shared clock UI builder + AnalogClockView
├── DigitalClockDreamService.kt # The actual screensaver (DreamService)
├── DigitalClockPreviewActivity.kt # Full-screen clock preview
├── ClockSettingsActivity.kt    # Dedicated "Clock" settings screen
├── ImmortalBackGestureService.kt # Accessibility service for global BACK
├── SystemBackGestureService.kt # Overlay-based back-swipe foreground service
├── BackHelper.kt               # Shared back-action + Immortal-routing helper
├── CalendarHelper.kt           # Calendar provider query helper
├── HomeActivity.kt             # The launcher (main UI)
├── ScreensaverSettingsActivity.kt # Photo frame settings
├── ImmortalSettingsActivity.kt  # Immortal launcher settings
├── ...
```

### New modules in 1.40

```
# Sky / "go outside" tiles
├── IssPasses.kt                # ISS overhead predictor (self-contained SGP4)
├── Aurora.kt                   # Location-driven aurora chance (Kp + geomagnetic lat)
├── StarField.kt                # Bright-star catalogue + horizon projection (Stars background)

# Kitchen & quick tools
├── Converter.kt                # Offline unit + keyless ECB currency conversion
#   (Stopwatch lives in HomeActivity as StopwatchOverlay)

# Bedroom
├── SunriseConfig/Scheduler/Receiver.kt   # Sunrise-alarm schedule + alarm plumbing
├── WakeLightActivity.kt        # Brightness-ramp wake light
├── SunriseSettingsActivity.kt  # Sunrise-alarm settings
├── LampActivity.kt             # Warm-white lamp mode
├── Stories.kt                  # Public-domain bedtime stories
├── BedtimeStoryActivity.kt     # Big-text reader + TTS narration
├── AntiBurnIn.kt               # Slow pixel-shift for always-on surfaces

# Household & connected
├── PingService.kt              # Serverless LAN UDP "ping the other room"
├── CalendarPacks.kt            # Installable calendar packs (header lines)
├── IrishHolidays.kt            # Irish bank holidays + saints (computed Easter)
├── PrayerTimes.kt              # Islamic prayer times computed by location
├── WhatChanged.kt              # "What's new" from GitHub releases
├── Tips.kt                     # "Did you know" daily tips
├── GestureCamera.kt            # EXPERIMENTAL Camera2 wave-to-advance (off by default)
```

These follow the established patterns: keyless networked features are `object`
singletons doing `HttpURLConnection` GETs with SharedPrefs caching; pure logic
(SGP4, geomagnetic/Kp, conversions, sunrise scheduling, star projection, Irish
Easter, anti-burn-in) is split out and unit-tested offline (`IssPassesTest`,
`AuroraTest`, `ConverterTest`, `BacklogLogicTest`); alarm-driven features
(sunrise) re-arm in `ImmortalApp` + `BootReceiver` like the chime and timers.

## Key design patterns

### Shared `build()` / `update()` for the clock UI

`DigitalClockView.build()` is the single source of truth for the clock
interface. It returns a `Controller` with typed references to every view
that needs to change at runtime (`clockText`, `dateText`, `flipHour`,
`flipMinute`, `flipSecond`, `analog`). Both the screensaver and the
preview use the same builder.

`DigitalClockView.update(controller, settings)` is called by the tick
runnable to refresh the time. It reads the current time, formats it
according to the user's settings, and writes it to the views.

This means adding a new style, font, or background is a single-file
change — no duplication between the screensaver and the preview.

### BackHelper — shared back-action routing

`BackHelper.performBack(context)` is the only place that performs a
back action. It:
1. Dispatches the back (via the accessibility service broadcast, or a
   shell keyevent fallback)
2. After a short delay, checks the top activity
3. If the top is the home screen, re-routes to `HomeActivity`

This prevents the user from being dumped onto the Portal's stock Aloha
launcher when they swipe back from an app.

### ImmortalSettings as a single source of truth

`ImmortalSettings.kt` is a `data class Settings` with persistent getter/
setter functions. Every setting is read via `ImmortalSettings.load(context)`
on resume and saved via a typed setter. The `ScreensaverConfig` and
`DigitalClockConfig` classes follow the same pattern.

### Compose-based settings activities

All three settings activities (`ImmortalSettingsActivity`,
`ClockSettingsActivity`, `ScreensaverSettingsActivity`) use the same
layout: a `Box` wrapper with a scrollable `Column` for the content and
a `FloatingBackButton` overlay anchored to `Alignment.BottomEnd`. They
share a `Segmented` composable for radio-button-like option pickers and
a `Card` / `Divider` helper for the settings card layout.

## Where to add new features

- **New launcher feature** → `HomeActivity.kt`
- **New setting** → add constant to `ImmortalSettings.kt` / `DigitalClockConfig.kt`,
  add getter/setter, add UI in the appropriate `*SettingsActivity.kt`
- **New screensaver/clock style** → add constant to `DigitalClockConfig.STYLE_*`,
  add rendering in `DigitalClockView.build()`, add option in `ClockSettingsActivity`
- **New accessibility action** → add handler to `ImmortalBackGestureService`
  or new method in `BackHelper`
- **New bundled font** → drop .ttf into `assets/fonts/`, add constant
  to `DigitalClockConfig.FONT_*`, add `loadBundledFont` case

## Threading

- `HomeActivity` uses `produceState` + `Dispatchers.IO` for background
  loading (app list, background image, calendar events)
- `DigitalClockView.update()` runs on the main thread, driven by a
  `Handler` posted at 500ms (seconds shown) or 1s intervals
- `BackHelper` uses `Handler.postDelayed` with a 200ms delay to let the
  activity stack settle after a back action before checking the top
  activity
- `CalendarHelper.upcoming()` runs on a background coroutine via
  `LaunchedEffect`
