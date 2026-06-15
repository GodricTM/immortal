# Changelog

## 1.40 (2026-06-15)

A large batch of ambient, kitchen, bedroom and "go outside and look" features.
Authored by **@GodricTM**. Everything here is keyless and works offline-first (no
Google services), per Portal constraints.

**Sky & "go outside" tiles**

- **ISS Pass predictor** — a tile that tells you when the International Space Station
  flies over *your* location ("Visible pass Fri 9:42 PM ✨"), the direction it rises
  and sets, how high it climbs, and whether it'll be bright enough to actually spot.
  The orbit is propagated on-device with a self-contained SGP4 model (validated against
  the standard NORAD test vector); only a one-off keyless orbital-element fetch needs the
  network, then it works offline for days.
- **Aurora tile** — lights up green *only when there's a real chance of aurora at this
  device's location*, in any country and either hemisphere. It compares the live
  planetary K-index (NOAA SWPC) to your geomagnetic latitude and the auroral oval, and
  shows the Kp number and which horizon to face.
- **Constellation night background** — a new "Stars" background that shows the real night
  sky for your location and time behind the app grid after dark, fading in through dusk.

**Kitchen & quick tools**

- **Stopwatch / count-up** with lap marks, for workouts, steeping, anything.
- **Converter** — length, weight, volume, speed and temperature offline, plus live
  currency (European Central Bank rates, cached so it keeps working offline).
- **Wave to advance (experimental):** wave a hand in front of the camera to move to the
  next photo in the frame — no touch needed for floury or wet hands. Uses the standard
  camera only; **off by default** (Screensaver settings → Gestures).

**Bedroom**

- **Sunrise alarm / wake light** — at the time you set, the screen brightens gradually
  from a deep ember to daylight over a chosen ramp, with an optional gentle chime at the
  end. Turns a bedroom Portal into a wake light.
- **Lamp mode** — fills the screen with warm white at a chosen brightness and warmth: an
  instant nightlight, reading light, or video-call fill light.
- **Bedtime stories** — a small library of public-domain children's tales shown in big,
  calm text and read aloud through the device's voice.
- **Anti-burn-in** — the always-on digital clock now drifts along a slow, invisible path
  so a screen that's never off ages evenly.

**Household & connected**

- **Ping the other room** — tap to light up every other Portal in the house with a tone
  and a spoken room name. A contact-free intercom-lite over your Wi-Fi, with nothing to
  sign in to and no server.
- **Calendar packs** — add the calendar that fits your household: Irish bank holidays and
  saints' days, or daily Islamic prayer times computed for your location. These sit
  alongside the existing Romanian name-days / Orthodox feast lines.

**Knowing your device**

- **What's New** — a tile showing the launcher's recent self-updates, so a device that
  improves itself quietly isn't a black box.
- **Request an app** — opens a prefilled request so the household can signal which apps
  they'd like added.
- **"Did you know" tips** — an occasional card surfacing a feature you might not have
  found yet.

## 1.39 (2026-06-14)

One fix from a user bug report (merged from upstream starbrightlab).

- **Photo-frame screensaver: the built-in web photos are sharp on every Portal.** The default photo feed was requesting a fixed 1280×800 image, so on any display with a different resolution the picture was scaled to fit and looked blurry — notably the portrait **Portal Mini** (800×1280) and the higher-resolution **Portal+ 2nd gen** (2160×1440). It now requests photos at the device's actual screen resolution and orientation, so they fill the screen crisply. When an Unsplash key is configured, the feed also now picks a portrait or landscape crop to match the screen instead of always asking for landscape.

## 1.38 (2026-06-14)

Two fixes from user bug reports.

- **Photo-frame screensaver: photos display the right way up.** Photos served from a folder are now rotated to match the orientation they were taken in. Portrait shots from phone cameras were showing sideways (and some landscapes upside-down) because phones store the raw sensor image plus a rotation tag in the file's EXIF metadata, and that tag wasn't being read — so the picture was drawn unrotated. The built-in web photo feed was never affected (those images carry no rotation tag).
- **Overnight sleep: tapping the screen lets you use the device.** With Overnight sleep enabled, tapping the screen inside the overnight window woke it for a moment and then immediately locked again, on every tap. A deliberate tap now wakes the device and lets you use it normally; it returns to sleep a short while after you stop interacting. A stray wake with no interaction still goes back to sleep promptly.

## 1.36 (2026-06-13)

Refinements to the "hey" voice button.

- **Long-press the "hey" button** to pick *which* assistant to talk to, instead of just the default. The chooser is shown by the Millennium voice app, which unlocks more than the built-in Alexa with Premium.
- **Hardened the trigger:** the "hey" trigger can now only be fired by the launcher itself (a signature-permission guard), not by other apps on the device.

## 1.35 (2026-06-13)

A push-to-talk voice button in the header, and a tidier top bar.

- **New "hey" button** in the home header — a mic button that wakes your voice assistant on tap (push-to-talk), so you don't have to say the wake word. It appears only when the companion **Millennium** voice app is installed; the launcher just sends the request, Millennium picks the active assistant and handles the rest.
- **Clock moved to the top-left corner**, with the screensaver and "hey" buttons grouped to its right — cleaner now that there's more than one button up there.
- **Provisioning kit:** Shizuku is now installed + started on **every** Portal (not just the Gen-1), as a generally useful privileged broker. New **opt-in "Restore Amazon Alexa"** step revives the original on-device Alexa client (`./provision.sh --alexa`, or answer the prompt during setup).

## 1.37 (2026-06-14)

A major expansion of the screensaver, back navigation, home screen widgets, and
Piper neural TTS. Authored by **@GodricTM**.

- **Welcome-back overlay:** when the Portal's presence sensor wakes the screensaver,
  a brief (3 s) overlay fades in over the photo frame showing a time-of-day greeting
  ("Good morning / afternoon / evening"), the current time, and the date. Tapping
  dismisses it early. Shown on every presence-triggered start (`PhotoDreamService` and
  `DreamPolicy` relaunch); skipped for the manual preview. Togglable via
  `ScreensaverConfig.welcomeEnabled` (on by default).
- **Piper neural TTS:** the welcome overlay can speak the greeting via `PiperTTS`,
  a Sherpa-ONNX wrapper using the `en_US-lessac-medium` voice (~16 MB download on
  first use). Android system TTS starts immediately as a fast-path fallback; Piper
  wins if it's ready first. Off by default; enabled in Welcome settings.
- **Digital clock screensaver:** a second Dream service (`DigitalClockDreamService`)
  shows a configurable full-screen digital or analog clock. Clock settings tile with
  font, color, size, layout, background, glow, and style controls; optional
  seconds display and date line; full-screen preview; screensaver activation controls.
- **Sleep timer:** a dedicated Sleep tile and settings page with a live countdown,
  start/stop controls, pause-audio option, close-app option, and a sleep flow that
  disables Immortal's screensaver before locking the screen.
- **System-wide back shortcut:** `ImmortalBackGestureService` (accessibility) plus an
  optional right-edge swipe overlay (`SystemBackGestureService`). Fixes the
  back-action recursion so the accessibility service fires Android's real
  `GLOBAL_ACTION_BACK`.
- **Launcher dashboard:** optional Calendar agenda widget (next 7 days), system stats
  widget (RAM / storage / uptime), custom accent colors (16 options), custom
  image/blur background, explicit new-folder button, persisted empty folders,
  bottom-right folder back button, and a Portal-home shortcut tile.
- **Settings polish:** expanded Immortal settings for weather, calendar, stats, clock
  format, accent color, background, and screensaver activation; moved sleep controls
  into their own page.
- **Easter Egg tile:** injects the Android 9 Pie Easter Egg
  (`com.android.systemui/.egg.MLandActivity` — the cat-herding / MLand game) as a
  persistent home-grid tile on any Portal where it's present.
- **Dual NIU overlay fix (provisioner):** the `--overlay-fix` step now disables both
  `com.facebook.aloha.rro.niu.android` *and* `com.facebook.aloha.rro.niu.settings`,
  fixing Settings white-on-white in addition to the installer dialog on Gen-1.
- **OFL font compliance:** replaced the four personal-use-only TTF fonts with
  redistribution-safe OFL-1.1 alternatives (DSEG7Classic, DSEG14Classic, Orbitron).
  Added `assets/fonts/OFL.txt`. The build is now clean for open-source redistribution.
- **CLAUDE.md:** AI agent navigation guide documenting hardware targets, architecture,
  key constraints, and build/deploy workflow — for automated coding assistance in
  future sessions.
- **`deploy.bat`:** Windows one-shot build-and-deploy helper (wraps `gradlew
  assembleDebug` + `adb install -r`).
- **Build/runtime plumbing:** calendar, overlay, foreground-service, and accessibility
  metadata; `ui-android` Compose dependency; Sherpa-ONNX AAR via local `libs/`.

## 1.34 (2026-06-09)

Calls bridge fix, a real screensaver off-switch, and practical screen-off controls.

- **Calls bridge:** routes through the stock launcher's `portal://launcher/home` deep link where available and suppresses Immortal's aggressive photo-frame relaunch during the handoff, fixing the issue where opening Calls could immediately kick the user back into Immortal.
- **Screensaver off-switch:** the photo-frame master toggle now sticks. When disabled, Immortal stops forcing its screensaver back on, so the Portal can sleep normally or the user can use another setup.
- **Idle screen-off:** added an optional timer that turns the screen off after the screensaver has been running for a chosen period with no interaction.
- **Overnight sleep:** added an optional daily sleep window so the screen stays off between configured times.
- **Provisioning:** activates Immortal's force-lock device admin during setup and removes it during restore. This is used only for the new screen-off features.
- **Release:** bumped the app to `versionCode` 35 / `versionName` 1.34 and pointed the OTA manifest at the new build.

## 1.30 (2026-06-08)

A 24-hour clock option — by community request.

- New **Clock ▸ Time format** control in Settings folder → Immortal: **Auto** (follows the Portal's system 24-hour setting), **12h**, or **24h**. Auto by default, so existing setups are unchanged.
- The choice applies everywhere the time is shown: the home-screen header clock, the screensaver/photo-frame clock, and the hourly forecast labels (e.g. `13` instead of `1 PM`). The launcher and screensaver re-read the setting on resume, so it takes effect as soon as you go back home.

## 1.29 (2026-06-08)

Optional home-screen weather forecast widget.

- New **Home-screen forecast** control in Settings folder -> Immortal -> Weather: Off, Hourly, or 7-day. Off by default.
- The launcher can render a full-width forecast below the app grid, with swipeable hourly and 7-day pages.
- Forecast data comes from one keyless Open-Meteo request and respects the user's Fahrenheit/Celsius setting.
- Failure handling is quiet: the widget retries when unavailable and keeps the last good forecast rather than blanking on a failed refresh.
- Added focused tests around forecast URL construction, parsing, time cutoff behavior, and edge cases.

## 1.28 (2026-06-07)

Gen-1 installs now survive a reboot — the provisioning kit repairs the Portal's own installer dialog.

- The blank "no buttons" install dialog on the Gen-1 Portal+ is caused by a Meta display overlay (`com.facebook.aloha.rro.niu.android`) re-theming it white-on-white. Provisioning now disables that overlay (`cmd overlay disable`), restoring the normal dialog. Unlike the silent-install daemon, this **persists across reboots**, so a rebooted Gen-1 with the daemon down falls back to the now-visible system dialog instead of pausing new installs. Community find via Reddit (**u/keremimo**); thanks also to **u/TheMaddis**.
- `cmd overlay` is applied immediately with no reboot, so it doesn't disturb the running daemon or Shizuku. Gated to API < 29; skipped on newer Portals. Reversible with `--restore`; run on its own with `--overlay-fix` / `-OverlayFix`. New `DISABLE_INSTALLER_OVERLAY` flag in `config.env` (default on).
- The store, "Install with Immortal," and APK sideloads no longer report "paused" on a Gen-1 once the overlay fix is in place — they use the system dialog when the daemon isn't running. `--status` now reports installer-dialog state.

## 1.27 (2026-06-06)

Extra large app icons — by community request, for the Portal+'s big screen.

- A third **App icon size** in Settings folder → Immortal: Extra large (4 columns of 140dp tiles). Standard and Large are unchanged.
- Folder panels now grow with the chosen icon size so nothing clips at the bigger sizes.

## 1.26 (2026-06-06)

Provisioning-kit fixes for Windows — the app is unchanged from 1.25.

- Shell scripts now stay LF on Windows checkouts (`.gitattributes`), so `installd.sh` no longer dies on the Portal's shell with a CRLF syntax error — this was why Gen-1 devices showed "Installing new apps is paused" after a Windows-run setup. Thanks to **@tgnm** for the diagnosis and fix (our first outside contribution!).
- `provision.ps1 -Status` now actually prints the current home launcher (also @tgnm).
- README: documented Windows' "Unblock" step for downloaded files (community tip from Reddit).

## 1.25 (2026-06-06)

Immortal settings — the first community-requested customizations.

- New **Immortal** tile in the Settings folder: a settings screen for the launcher itself (remote-friendly, like the screensaver settings).
- **Weather in °F or °C** — Auto follows your Portal's language & region; or pick one explicitly. The home-screen header updates the moment you go back.
- **App icon size** — Standard (the original compact grid) or Large (5 columns of bigger tiles, closer to the stock Portal launcher).

## 1.24 (2026-06-06)

Ship the dark-theme fix that landed in source after the 1.23 release was cut.

- The app's XML theme is now dark (`Material.NoActionBar` with a transparent status bar and black window background), so the status bar no longer flashes white before Compose renders or when it transiently reappears on a swipe from the top. Cosmetic only; no behaviour changes.

## 1.21 (2026-06-06)

A friendly, non-technical Help tour — so anyone can pick up a Portal running Immortal.

- New **Help** tile on the home screen, and a one-time welcome that opens automatically the first time Immortal starts (it won't nag you again).
- Eight short, plain-language cards: getting around (and how the home button always brings you back), making video calls via the Calls tile, using the photo frame and your own photos, adding apps from the App Store and Aurora, what to do after a restart to re-enable installs, an honest note on what Immortal can't do, and an invitation to join the community on GitHub.
- Works with a finger (swipe or tap Next) and with the Portal TV remote (D-pad on Back/Next, BACK to leave).
## 1.20 (2026-06-06)

Work WITH Meta's presence detection instead of disabling it.

- v1.19 disabled the presence service to stop the screensaver bouncing and let the Go sleep — effective, but it removed stock functionality with possible unseen consumers. 1.20 re-enables presence and cooperates with it instead. Measured behavior: the system decides ambient-vs-sleep at the screen timeout from presence (someone nearby → screensaver, empty room → sleep), and presence activity extends the dream's force-wake deadline — so while someone is around, the frame runs uninterrupted with no bounce at all.
- When the system does bounce the dream (room recently emptied, etc.), Immortal still relaunches the frame seamlessly. On mains-powered Portals the frame holds the screen → permanent photo frame. On the Portal Go with the (renamed) "Sleep on battery when nobody's around" setting on — the default — the frame doesn't hold the screen, so each timeout is a fresh presence decision: photos while occupied, real sleep when the room is empty.
- The provisioning kit no longer disables the presence detector (DISABLE_PRESENCE now defaults to false; the option remains for camera-averse users, and restore re-enables it). If you ran the v1.19 kit, re-enable with: adb shell pm enable com.facebook.alohaservices.presence

## 1.19 (2026-06-06)

Fix the screensaver bouncing back to the launcher (and devices never sleeping).

- Root cause, in two parts. (1) Meta's modified power manager force-wakes ANY third-party screensaver about 2 minutes after idle — a dream can never run indefinitely and is never allowed to hand off to sleep (verified to the millisecond on device). The stock Portal hid this: its SuperFrame app caught the wake, and (2) Meta's camera-based presence service poked the power manager every ~20 seconds, so the device never slept at all — which is also why a Portal Go drains overnight.
- **Permanent frame:** when the system force-wakes the screensaver (not a tap, not the power button), Immortal instantly puts the same frame back up as a screen-on activity. One brief flicker ~2 minutes in, then the photo frame runs forever — like a stock Portal, on every model.
- **Portal Go battery saver:** a new "Pause screensaver on battery" setting (Settings → Screensaver → Power, battery models only, on by default). Unplugged, the Go now sleeps at the screen timeout instead of showing photos until the battery dies; plugged in, the frame runs permanently. Toggle it off for stock always-on behaviour.
- **Provisioning kit:** now disables Meta's presence detector (reversible via restore) and grants the overlay permission Immortal uses to bring the frame back from the background. Re-run the kit once on already-provisioned devices to pick these up.

## 1.18 (2026-06-06)

The App Store, redesigned — built to be the centerpiece, and ready for community submissions.

- **Browse:** every app now shows its real icon (with a monogram tile for entries without one), author, and a one-line description, with search across name/description/author and an "Updates available" section for installed catalog apps.
- **Detail pages:** tap any app for its full description, source (F-Droid or direct), version, website link, device-compatibility badge, and community credit ("Submitted by") — with contextual Install / Open / Update / Reinstall actions.
- **Compatibility badges:** apps whose `minSdk` exceeds the device (e.g. an Android-11 app on a Portal) show "Needs Android X+" instead of a doomed Install button.
- **Catalog schema v2** (additive — older clients keep working): `iconUrl`, `author`, `homepage`, `longDescription`, `submittedBy`, `devices`, with `minSdk` now driving the compatibility badge.
- **Community submissions:** an "App submission" issue template, an updated SUBMISSIONS.md, and CI that validates every catalog PR (schema, duplicates, https, F-Droid ids and download/icon URLs actually resolving) — `scripts/validate_catalog.py`.

## 1.17 (2026-06-06)

Make the Android-10 install experience honest when the silent helper is down.

- Background: Immortal installs apps silently through a small shell-privileged helper (the install daemon). Like all such helpers it does not survive a reboot. On Android-10 Portals (Go/Mini/gen-2) the store was silently falling back to the system installer dialog when the helper was down — and that dialog parse-fails ("There was a problem parsing the package") on some apps (notably Play-store split APKs). It looked like a regression because installs had been silent right after provisioning.
- The App Store and the "Install an APK" browser now detect the helper being down on Android 10 and show a clear note: installs have dropped to the system dialog, some apps may fail to parse there, and reconnecting + re-running the installer restores silent, one-tap installs that work for every app. (First-gen Portals keep their existing "installs paused" banner.)
- No change to the happy path: with the helper running, installs are silent and parse-error-free on every model.

## 1.16 (2026-06-06)

Make Immortal appear as a tile on the Portal TV's stock home (ripleyhome).

- ripleyhome is an Android-TV (Leanback) launcher: it lists apps that declare `CATEGORY_LEANBACK_LAUNCHER` (VLC, the Vewd browser, …), not plain `LAUNCHER` apps — which is why Immortal, Jellyfin, Transistor and Shizuku never showed there. Immortal now declares a Leanback launcher entry, with a 320x180 TV banner, via a launcher-only `activity-alias` (no `HOME` category, so it isn't filtered out as a home app). It opens the normal launcher. Together with the Calls→stock-home bridge, you can hop between Immortal and the stock home in both directions.
- Declared `leanback` and `touchscreen` as optional features so the app installs cleanly on every Portal, touch or TV.

## 1.15 (2026-06-05)

Fix the screensaver's time-per-item control so it works with both touch and the remote.

- In 1.14 the control adjusted only via D-pad left/right, so on touchscreen Portals there was no way to change it. The ◀ / ▶ arrows are now real tap targets, while the remote keeps adjusting with left/right and moving focus away with up/down. The arrows are excluded from D-pad traversal so they don't add extra stops for the remote.

## 1.14 (2026-06-05)

Portal TV polish, from end-to-end testing on the device with its remote.

- **Calls tile** now bridges to the stock Portal TV home (`ripleyhome`) when the touchscreen call UI isn't available, so the remote can still reach calls and the stock app grid.
- **Grid curation:** hide internal/non-user entries (the TV boot flow `rcbootflow` and the stock home), and fold the TV's **Picture Mode** into the Settings folder.
- **Browser:** Chrome ships on every Portal but renders blank and is undriveable by the remote on the TV, so it's hidden there (device `ripley`) in favour of the working Vewd browser. It stays available on the touch models where it works.
- **Screensaver settings on the remote:** focused rows no longer grow outside their card (filled highlight instead of scale), and the time-per-item control is now a left/right stepper you can navigate past — previously the slider trapped focus.

## 1.9 (2026-06-05)

Clearer messaging when app installs are paused on a first-gen Portal.

- Rewrote the paused-install copy across the store banner, the per-app status, the "Install with Immortal" card, the "Install an APK" browser, and the self-updater. The old wording ("reinstall Immortal to restore") was misleading — reinstalling the app doesn't restart the install helper. The new copy is accurate and consistent: installing new apps is paused after a reboot; reconnect to a computer and re-run the installer to add apps; everything else keeps working.
- No functional change — installs, the daemon, and Shizuku support are unchanged from 1.8.

## 1.8 (2026-06-05)

Universal installer — make sideloading work on the Gen-1 Portal+, whose built-in Android installer dialog is broken (renders with no buttons).

- **"Open with Immortal"** (`ApkInstallActivity`): opening any APK — from Chrome, a file manager, or a third-party store like Aurora ("Session/Native" installer mode) — routes through Immortal's silent daemon. Shows an "Install <app>? via Immortal" card; installs with no system dialog.
- **"Install an APK"** browser (`ApkBrowserActivity`): lists APKs in Downloads and installs them on a tap, from the App Store header.
- **Shizuku** (optional): `provision.sh --shizuku` / `-Shizuku` installs Shizuku (if `SHIZUKU_APK_URL` set) and starts its server over ADB, for apps that use the Shizuku API directly. Resolves the version/ABI-specific starter at runtime. Like the daemon, restart after a reboot.
- **Paused banner** copy clarified for Gen-1 ("reinstall Immortal to restore").

## 1.0 (2026-06-05)

Initial public release of Immortal — a custom home-screen layer for discontinued Meta Portal devices.

- **Launcher**: fullscreen app grid, clock/date/weather header, optional Portal Go charge indicator.
- **Folders**: drag one app onto another to create a folder; name, rename, and drag apps back out.
- **Manage mode**: remove apps with a tap; special tiles stay put and non-uninstallable.
- **Calls bridge**: one-tap route to the stock dialer/contacts for WhatsApp and Messenger calling.
- **Screensaver**: photo frame with stock-style widgets, swipe navigation, tap to exit.
- **App Store**: hosted JSON catalog, on-device install via PackageInstaller, live F-Droid resolution.
- **Self-update**: polls a hosted manifest and installs new builds over itself — no cable.
- **Provisioning kit**: one double-click to install, set home + screensaver, and freeze OS updates. Fully reversible.
