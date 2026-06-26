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
├── SkyColors.kt                # Time-of-day sun-driven sky gradient background

# Kitchen & quick tools
├── Converter.kt                # Offline unit + keyless ECB currency conversion
├── Transit.kt                  # Dublin departures (SmartDublin RTPI)
├── DailyContent.kt             # Bundled daily quote/word/trivia
├── TimeProgress.kt             # Week/month/year progress bars + year dots
#   (Stopwatch lives in HomeActivity as StopwatchOverlay)

# Bedroom
├── SunriseConfig/Scheduler/Receiver.kt   # Sunrise-alarm schedule + alarm plumbing
├── WakeLightActivity.kt        # Brightness-ramp wake light
├── SunriseSettingsActivity.kt  # Sunrise-alarm settings
├── LampActivity.kt             # Warm-white lamp mode
├── Stories.kt                  # Public-domain bedtime stories
├── BedtimeStoryActivity.kt     # Big-text reader + TTS narration
├── AntiBurnIn.kt               # Slow pixel-shift for always-on surfaces

# Sound & voice
├── ChimeConfig.kt              # Chime settings (registry-native, 11 specs)
├── ChimePlayer.kt              # Plays chime/golden/ping sounds
├── ChimeScheduler.kt           # Arms hourly + golden-hour alarms
├── ChimeReceiver.kt            # Fires on alarm, calls ChimePlayer
├── ChimeSettingsActivity.kt    # Chime settings (renders via SettingsList)
├── SoundscapePlayer.kt         # Procedural rain/ocean/fire/noise (no assets)
├── WelcomeConfig.kt            # Welcome overlay settings (registry-native, 5 specs)
├── WelcomeSettingsActivity.kt  # Welcome settings (toggles via domain, bespoke pickers)

# Timers & alarms
├── TimerConfig.kt              # Named kitchen timers (JSON list)
├── TimerScheduler.kt           # Arms timer alarms
├── TimerReceiver.kt            # Fires on timer elapse
├── CountdownConfig.kt          # Countdown event chips (JSON list)
├── CountdownSettingsActivity.kt # Add/remove countdown events
├── SleepSettingsActivity.kt    # Sleep timer UI (routes through screensaver domain)

# Camera & communication
├── CameraConfig.kt             # Saved RTSP camera streams (JSON list)
├── CameraViewerActivity.kt     # Camera list
├── CameraPreviewActivity.kt    # RTSP playback via MediaPlayer
├── IntercomActivity.kt         # LAN intercom UI
├── LanAudio.kt                 # PCM-over-TCP one-way audio
├── PingService.kt              # Serverless LAN UDP "ping the other room"

# Content & notes
├── AudioNote.kt                # Voice memo recording/playback
├── NotesConfig.kt              # Typed sticky note (SharedPrefs)

# Calendar & locale
├── CalendarPacks.kt            # Installable calendar packs (header lines)
├── IrishHolidays.kt            # Irish bank holidays + saints (computed Easter)
├── PrayerTimes.kt              # Islamic prayer times computed by location
├── NameDays.kt                 # Romanian name-day table
├── FeastDays.kt                # Orthodox feast calendar (computed Pascha)
├── CalendarHelper.kt           # Calendar provider query helper

# Launcher & system
├── ForkHome.kt                 # Fork's expanded home (tool-folder system, chips, overlays)
├── ImmortalBackGestureService.kt # Accessibility service for global BACK
├── SystemBackGestureService.kt # Overlay-based back-swipe foreground service
├── BackHelper.kt               # Shared back-action + Immortal-routing helper
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
on resume and saved via a typed setter. The `ScreensaverConfig`,
`DigitalClockConfig`, `ChimeConfig`, `SunriseConfig`, and `WelcomeConfig`
classes follow the same pattern: immutable snapshot + `load()` + clamping
`(Context, value)` setters.

### Declarative settings registry (1.52)

All fork features now flow through upstream's declarative registry
(`SettingSpec` / `SettingsDomain` in `com.immortal.launcher.settings`).
A setting is defined once as a `SettingSpec` and that single definition drives
three consumers: persistence (the existing `*Config` setters), on-device UI
(`SettingsRenderer.SettingsList`), and the phone-remote PWA.

Four new domains added to `SettingsDomains.kt`:

| Domain | Config | Specs | Bespoke |
|--------|--------|-------|---------|
| `chime` | `ChimeConfig` | 11 | `spokenVoice` (TTS voice picker) |
| `digitalclock` | `DigitalClockConfig` | 10 | none |
| `sunrise` | `SunriseConfig` | 5 | `days` (day-of-week picker) |
| `welcome` | `WelcomeConfig` | 5 | Float/Color/String fields + voice picker |

Key patterns:
- **Constraints belong on the spec** (`IntSpec` min/max/step/wrap, `EnumSpec`
  options/coerce). The apply boundary validates and rejects out-of-range input.
- **Clamping lives in the setter** (`setChimeVolume(c, v.coerceIn(0,100))`),
  not in the UI — so storage rules have one home regardless of write path.
- **Side effects go in `onApplied`** — `ChimeScheduler.reschedule`,
  `SettingsGuard.reaffirmScreensaver`, `SunriseScheduler.reschedule` fire once
  per batch, not per toggle.
- **Bespoke Activities route through `domain.apply()`** — not direct
  `SharedPreferences` writes — so the `onApplied` hook fires on-device too.
- **Tripwire tests** (`*Registry_coversEveryPersistedField`) break the build
  if a new `*Config.Settings` field ships without a spec.

Collection-type configs (`CountdownConfig`, `TimerConfig`, `CameraConfig`,
`NotesConfig`) store runtime objects (JSON lists of events/timers/cameras) and
stay bespoke — the registry models scalars, not collections.

### Compose-based settings activities

All settings activities use the same layout: a `Box` wrapper with a scrollable
`Column` for the content and a `FolderBackButton` overlay anchored to
`Alignment.BottomEnd`. Scalar controls (toggles, steppers, enums) render from
the registry via `SettingsList(domain, snapshot) { k, v -> domain.apply(...) }`;
bespoke sections (voice pickers, test buttons, color pickers, day-of-week
pickers) are hand-built Compose rows alongside the registry rows.

## Where to add new features

- **New launcher feature** → `ForkHome.kt` (tool-folder system, chips, overlays)
- **New setting** → add field to the `*Config.Settings` data class, add a
  clamping setter, add a `SettingSpec` to the matching domain in
  `SettingsDomains.kt`, add a tripwire entry if needed. The spec then appears
  on-device (via `SettingsList`), on the phone-remote PWA, and in the legacy
  wire format — all from one declaration. **Never read/write prefs directly
  from UI or routes.**
- **New screensaver/clock style** → add constant to `DigitalClockConfig.STYLE_*`,
  add rendering in `DigitalClockView.build()`, add option in
  `DigitalClockConfig` enum spec (already in the `digitalclock` domain).
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
