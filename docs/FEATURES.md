# Features — Detailed Documentation

This document describes each user-facing feature in detail, including where
to find it, what it does, and how it works under the hood.

---

## 1. Custom Accent Colors

**Where:** Settings → Immortal → "Accent color"

Pick from 16 colors (Blue, Red, Green, Purple, Orange, Pink, Teal, Yellow,
Indigo, Cyan, Lime, Amber, Deep Purple, Brown, Coral, Mint). The selected
color is applied to every Material 3 component in the app — buttons,
segmented controls, switches, and highlights — so the change is visible
immediately without restarting.

**How it works:** the choice is persisted in `ImmortalSettings` under
the `accent_color` key. `SampleAppTheme` reads the key on every
composition and builds the `ColorScheme` using the selected color as the
primary. Because the settings screen is a child of the theme, the
`accentKey` state is hoisted to the activity so changing it triggers
a recomposition of the entire theme.

---

## 2. Custom Background Image

**Where:** Settings → Immortal → "Background"

Choose from three modes:

- **Dark** (default) — solid dark background
- **Image** — pick any image from the device via the system file picker;
  the image is loaded and rendered full-screen behind the app grid with
  a dark overlay for readability
- **Blur** — same as Image but with a blur effect (reserved for future
  use)

**How it works:** the selected mode and the image URI are stored in
`ImmortalSettings`. `BackgroundImage` in `HomeActivity` loads the image
asynchronously via `produceState`, decodes it with `BitmapFactory`, and
renders it with `ContentScale.Crop`. The dark overlay (`Color(0x99000000)`)
ensures the app grid stays readable on any image.

---

## 3. New Folder Button

**Where:** Home screen → Manage mode (tap the pencil icon)

A blue "+" button appears next to the Done button in Manage mode. Tap it
to name and create a new empty folder. The folder appears in the app
grid immediately.

**How it works:** `UserLayout.nextFolderName()` generates a unique
default name (Folder, Folder 2, Folder 3…). The name is stored in
`UserLayout.saveEmptyFolder()` and the folder is displayed in the grid
via `folderNames` in `HomeActivity`.

---

## 4. Right-Edge Back Gesture (Launcher)

**Where:** Home screen, when a folder is open

Swipe left from the right 64dp of the screen to close the open folder
overlay. Useful on touch-only Portals without a hardware back button.

**How it works:** `pointerInput` + `awaitEachGesture` in `HomeActivity`
detects a down event within 64dp of the right edge, then tracks the
movement. A leftward drag of at least 120px with limited vertical
movement triggers `openFolder = null`.

---

## 5. Digital Clock Screensaver

**Where:** Settings → Clock → all options, or Screensaver settings

Replace the photo frame with a customizable digital clock. The clock
appears when the device's screensaver activates (screen timeout, dock,
or power button).

### Options

#### Style

- **Classic** — large time + date below, uses the selected font
- **Flip** — retro flip clock with digit cards
- **Bold** — extra-bold, thick numbers
- **Neon** — bright neon glow in the selected color
- **Segment** — 7-segment display style with monospace font
- **Analog** — high-quality analog clock face (all 12 numbers, proper markers, elegant hands)

#### Color

8 options: White, Red, Green, Blue, Yellow, Cyan, Pink, Orange.

#### Font

- **Light / Normal / Bold / Mono / Serif** — built-in fonts, all
  visually distinct on Android 9
- **LED / Digital / Tech** — bundled .ttf fonts loaded from
  `assets/fonts/`. Drop additional fonts there and update
  `DigitalClockConfig.FONT_*` constants to add more

#### Size, Layout, Background, Glow

All customizable. Glow always tints with the selected clock color.

#### Show date / Show seconds

Toggles for the date line and seconds display.

### Preview

The **Preview** button at the top of the Clock settings opens a
full-screen preview (`DigitalClockPreviewActivity`) so you can see your
changes immediately.

### How it works

`DigitalClockView.build()` is a shared builder used by both
`DigitalClockDreamService` (the actual screensaver) and
`DigitalClockPreviewActivity` (the preview). It returns a `Controller`
with references to all the views that need to be updated. The
`DigitalClockView.update()` function is called every tick (500ms when
seconds are shown, 1s otherwise) to refresh the time.

---

## 6. System-wide Back Gesture

**Where:** Settings → Clock → Back shortcut

Two complementary mechanisms give you a "go back" gesture anywhere on
the Portal:

### Accessibility service (Immortal Back)

Enable once in Android Settings → Accessibility. Once enabled, an
Immortal tile appears in the accessibility navigation bar. Tapping it
performs a global BACK action system-wide — same as the system back
button, but always available.

On the Portal, the stock Settings app hides third-party accessibility
services, so the Clock settings page enables the service directly via
`settings put secure enabled_accessibility_services`. A "Test" button
lets you verify it works.

### Overlay swipe (Swipe-from-right back)

Toggle on in the Clock settings. This starts a foreground service
(`SystemBackGestureService`) that draws a transparent 40dp strip on the
right edge of the screen. Swipe leftward from the right edge to go
back. Requires "Draw over other apps" permission (a Grant button
appears if not granted).

### Back-to-Immortal routing

`BackHelper.performBack()` is the shared entry point for both
mechanisms. After performing the back action, it checks whether the
user landed on the home screen. If so, it brings `HomeActivity` to
the front instead of the Portal's stock Aloha home — so swiping back
always returns to Immortal.

---

## 7. Calendar Agenda Widget

**Where:** Settings → Immortal → "Calendar agenda"

Show upcoming events on the home screen below the app grid. Reads from
the system calendar provider. Shows the next 5 events. Requires
`READ_CALENDAR` permission (requested on first enable).

---

## 8. Screensaver Activation

**Where:** Settings → Clock → "Screensaver activation"

- **Activate on sleep** — show the screensaver when the screen turns off
- **Activate on dock** — show the screensaver when the Portal is plugged in

These control the system `screensaver_activate_on_sleep` and
`screensaver_activate_on_dock` secure settings. Required for the
digital clock to show when the screen turns off (the Portal's stock
behavior is to go straight to Asleep state, bypassing the screensaver).
The `SettingsGuard.applyActivationSettings()` helper applies them
immediately and re-asserts them on every HomeActivity resume.

---

## 9. Floating Back Button

**Where:** All settings activities (Immortal, Clock, Screensaver)

A circular "←" button in the bottom-right corner of every settings
screen. Tap to close the screen. Works alongside the system back button
and the on-screen back swipe — useful on touch-only Portals.

---

## 10. Bundled LED Fonts

**Where:** `app/src/main/assets/fonts/`

Four true-type fonts bundled with the app:

- `segment_led.ttf` — 14-segment LED style
- `digital_7.ttf` — Digital 7 LED style
- `technology.ttf` — Technology style
- `technology_bold.ttf` — Technology bold style

A `bundledFontCache` in `DigitalClockView` memoizes loads so each font
is only read once. Missing files silently fall back to the default
font. To add more fonts, drop a `.ttf` into `assets/fonts/`, add a
constant to `DigitalClockConfig.FONT_*`, and add an option to the
font picker in `ClockSettingsActivity`.

---

## 11. ISS Overhead Pass Predictor

**Where:** Home grid → Tools → "ISS Pass"

Tells you when the International Space Station will fly over your location —
start time, the compass direction it rises and sets, how high it climbs, and a
✨ "visible" badge when the station will be sunlit while your sky is dark (i.e.
actually spottable with the naked eye).

**How it works:** `IssPasses.kt` contains a self-contained near-Earth **SGP4**
propagator (WGS-72 constants). It fetches the ISS two-line element set once
(keyless — Celestrak, falling back to wheretheiss.at), caches it for 12 hours,
then runs entirely offline: it propagates the orbit minute-by-minute, converts
each position to a topocentric look angle for your latitude/longitude, and
collects the above-horizon passes. Visibility also checks the sun's position
(observer in twilight + satellite out of Earth's shadow). The SGP4 math is
validated offline against the canonical Vallado test vector in `IssPassesTest`.

---

## 12. Aurora (Kp-index) Outlook

**Where:** Home grid → Tools → "Aurora" (lights up green when there's a chance)

A location-aware aurora check that works in any country and either hemisphere.
The tile stays a dim slate normally and turns green (showing the Kp number) only
when the auroral oval is reaching, or near, your latitude. The overlay shows the
current Kp, the next-24h forecast peak, and which horizon to face.

**How it works:** `Aurora.kt` pulls the planetary K-index (now + 3-day forecast)
from NOAA SWPC (keyless), converts your geographic latitude/longitude to
**geomagnetic latitude** via a tilted-dipole model, and compares it to the
Kp-driven equatorward edge of the oval. Levels: None / Slim (only a major storm
could reach you) / Possible / Likely. The geomagnetic and classification logic is
unit-tested offline in `AuroraTest`.

---

## 13. Constellation Night Background

**Where:** Settings → Immortal → Background → "Stars"

Shows the real night sky for your location and time behind the app grid after
dark. Stars fade in through twilight and out at dawn; a few familiar asterisms
(Orion, the Big Dipper, Cassiopeia) are joined with lines.

**How it works:** `StarField.kt` holds a compact catalogue of the brightest stars
(RA/Dec/magnitude) and projects each to local horizon coordinates (altitude /
azimuth) using the Local Sidereal Time for your longitude. `StarFieldBackground`
in `HomeActivity` renders them on a Compose `Canvas`, with brightness and size
scaled by magnitude, and only when the sun is far enough below the horizon. The
projection and sidereal-time math are unit-tested.

---

## 14. Stopwatch / Count-up

**Where:** Home grid → Tools → "Stopwatch"

A count-up stopwatch with start/pause, reset, and lap marks — for workouts,
steeping tea, or anything. It runs while the overlay is open (the persistent,
named kitchen timers remain a separate feature with home-screen chips).

---

## 15. Unit & Currency Converter

**Where:** Home grid → Tools → "Convert"

Converts length, mass, volume, speed and temperature entirely offline, plus live
currency. Pick a category, type a value, choose the "from" and "to" units.

**How it works:** `Converter.kt` does unit math from factor tables (temperature is
handled affinely). Currency uses the keyless Frankfurter API (European Central
Bank reference rates), cached for six hours so it keeps working offline at the
last-known rate. The unit math is unit-tested in `ConverterTest`.

---

## 16. Sunrise Alarm / Wake Light

**Where:** Home grid → Tools → "Sunrise" (settings); fires full-screen at the set time

At the time and on the days you choose, the screen brightens gradually from a deep
ember through warm amber to bright daylight over a chosen ramp (1–45 min), with an
optional gentle chime as it reaches full light. A "Preview (1-min ramp)" button
lets you try it.

**How it works:** `SunriseConfig` stores the schedule; `SunriseScheduler` arms an
exact `AlarmManager` alarm; `SunriseReceiver` launches `WakeLightActivity`, which
ramps the window brightness and screen colour (and triggers the `ChimePlayer`
sunrise tone). The alarm is re-armed on boot and app start, and reschedules itself
after each fire. `SunriseConfig.nextTrigger` is unit-tested.

---

## 17. Lamp Mode

**Where:** Home grid → Tools → "Lamp"

Fills the screen with warm white at a chosen brightness and warmth — an instant
nightlight, reading light, or video-call fill light. Tap to hide the controls;
back to exit. `LampActivity` drives the window brightness directly so it ignores
the system auto-dim.

---

## 18. Bedtime Stories

**Where:** Home grid → Tools → "Story"

A small library of public-domain children's tales (Aesop's fables and traditional
stories) shown in big, calm text and read aloud via Android's built-in
text-to-speech at a slow, gentle pace. `Stories.kt` holds the text;
`BedtimeStoryActivity` renders and narrates it. No downloads, no network.

---

## 19. Ping the Other Room

**Where:** Home grid → Tools → "Ping Room"

Tap to light up every other Portal in the house with a tone and a spoken room
name — a contact-free intercom-lite with nothing to sign in to and no server.

**How it works:** `PingService` runs a tiny UDP listener on every device (started
from `ImmortalApp`); a tap broadcasts a ping to the LAN, and each receiver plays
the chime and announces who called. The room name is the device's hardware model
(`ImmortalSettings.deviceRoomName`, ready for a future rename setting).

---

## 20. Calendar Packs

**Where:** Settings → Immortal → "Calendar packs"

Installable calendar add-ons that contribute lines to the home header, alongside
the existing Romanian name-day / Orthodox feast lines:

- **Irish holidays** — bank holidays and saints' days (St Patrick's, St Brigid's,
  Easter Monday computed, etc.)
- **Prayer times** — daily Islamic prayer times computed for your location

**How it works:** `CalendarPacks` tracks which packs are on; `IrishHolidays.kt`
(with a computed Gregorian Easter) and `PrayerTimes.kt` (standard angle-based solar
calculation for your latitude/longitude) generate today's line on demand.

---

## 21. What's New, Request an App, and Tips

**Where:** Home grid → Tools

- **What's New** (`WhatChanged.kt`) — lists the launcher's recent self-updates from
  its public GitHub releases, so a device that improves itself isn't a black box.
- **Request an App** — opens a prefilled GitHub issue so the household can signal
  which apps they'd like added.
- **"Did you know" tips** (`Tips.kt`) — an occasional home card that surfaces a
  feature you might not have found yet (one per day, dismissible).

---

## 22. Wave to Advance (Experimental)

**Where:** Settings → Screensaver → "Gestures" → "Wave to advance"

Wave a hand in front of the camera to move to the next photo in the frame — no
touch needed for floury or wet hands. **Off by default.**

**How it works:** `GestureCamera.kt` opens the front camera via the standard
**Camera2** API (never the gated Smart Camera SDK), reads a low-resolution
luminance stream, and fires when a broad burst of motion crosses the view
(debounced so one wave is one event). It needs the camera permission and no-ops
without it. Every code path is guarded so any failure self-disables the feature
rather than risking the always-on screensaver process. Feasibility on real Portal
hardware is unverified — keep it opt-in.

---

## 23. Anti-burn-in

The always-on digital clock screensaver now drifts along a slow, invisible
Lissajous path (`AntiBurnIn.kt`) so the lit pixels of a screen that's never off
share the load over time and don't ghost in. Pure and unit-tested.
