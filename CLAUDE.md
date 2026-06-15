# Immortal — Agent Navigation Guide

Immortal is an Android launcher for Meta Portal devices. Meta discontinued Portal
in 2023 and removed it from app stores; Immortal replaces the stock launcher and
app ecosystem so the hardware stays useful.

## Hardware targets

| Device         | Android | API | Notes                              |
| -------------- | ------- | --- | ---------------------------------- |
| Portal / Portal+ (gen-1) | 9  | 28  | Broken system installer; daemon required for silent install |
| Portal Go / Portal Mini / Portal+ (gen-2) | 10 | 29 | Working installer; daemon optional |
| Portal TV      | 10      | 29  | D-pad only, no touchscreen, Leanback launcher |

**Critical constraints:**
- **No Google Mobile Services (GMS).** No Play Store, no `com.google.android.*` APIs.
  Use open-source alternatives or plain AOSP APIs only. Never add a GMS dependency.
- `minSdk = 24` in build.gradle but devices are API 28/29 in practice.
- No signature-level or privileged permissions. Anything requiring those silently
  fails. Presence detection via Smart Camera SDK falls into this category.
- Landscape-only form factor. All layouts designed for horizontal orientation.
- No Contacts/Account APIs — `READ_CONTACTS` is denied at runtime.
- Microphone: standard `RECORD_AUDIO` captures single-channel handset mic only.
  Far-field beamformed array requires `com.facebook.portal.alohasdk.permission.RECORD_AUDIO_PRIVILEGED`
  (Meta-signed; unavailable to sideloaded apps). Wake-word and room-distance pickup
  are inaccessible.

## Architecture overview

```
HomeActivity          — main launcher grid (Compose), weather, calendar widget
├── StoreActivity     — curated app catalog (catalog.json) + APK browser
├── ScreensaverSettingsActivity / ClockSettingsActivity / SleepSettingsActivity
├── WelcomeSettingsActivity    — welcome overlay greetings, colors, sizes, TTS
├── ImmortalSettingsActivity   — tile size, weather unit, accent, sort, tabs, dashboard page, name-day/feast/next-event toggles, gradient/sky background, daily tile
├── ChimeSettingsActivity      — "Sounds": hourly chime / spoken time / golden-hour tone (per-cue volume) + quiet hours
├── CountdownSettingsActivity  — add/remove countdown chips
├── CameraViewerActivity       — RTSP camera viewer (VideoView, native RTSP)
├── IntercomActivity           — LAN one-way intercom / baby monitor (LanAudio)
└── HelpActivity      — guided tour (first-launch + manual)

PhotoDreamService     — photo screensaver (DreamService)
DigitalClockDreamService — digital clock screensaver (DreamService)
DreamPolicy           — decides whether to hold screen on / relaunch frame
PhotoFramePreviewActivity — full-screen holding Activity (keeps screen awake)
PhotoFrameController  — shared photo-frame UI + logic (dream + preview). Also hosts
                        the now-playing strip, the synthesized soundscape, and the
                        cycling ambient-dashboard info card.
(Welcome-overlay TTS uses Android's built-in TextToSpeech — Piper/Sherpa-ONNX was removed)

— Ambient / home-screen features (all added on the godric fork) —
ChimeConfig/ChimeScheduler/ChimeReceiver/ChimePlayer — hourly chime, spoken time,
                        golden-hour tone, quiet hours; AlarmManager-driven, re-armed
                        in ImmortalApp + BootReceiver. chime.mp3 in res/raw.
SoundscapePlayer      — procedural rain/ocean/fireplace/white/pink/brown noise via
                        AudioTrack (offline, no assets). Used by PhotoFrameController.
SkyColors             — time-of-day → sky gradient (sun-driven home background).
NameDays / FeastDays  — Romanian name-day table; Orthodox feast calendar (computed
                        Pascha). Surfaced as header lines (toggles in ImmortalSettings).
DailyContent          — bundled daily quote / word / trivia (deterministic by epoch day).
NowPlayingListenerService + NowPlaying — read the active MediaSession (track + art)
                        for the now-playing screensaver. Needs notification-listener
                        access enabled once by the user.
NotesConfig / AudioNote — "leave a note": typed sticky + voice memo (MediaRecorder/Player).
Transit               — Dublin departures via SmartDublin RTPI (keyless).
CameraConfig          — saved rtsp:// camera URLs for CameraViewerActivity.
LanAudio              — server-less LAN PCM-over-TCP audio for IntercomActivity.
TimeProgress          — week/month/year progress bars, 365-day year dots, fading
                        month grid (dashboard card); plus the live SunArc in the home header.

InstallDaemon         — client for the provisioning-kit shell daemon (silent install)
UpdateManager         — self-update: fetches release JSON, downloads APK, installs
StoreCatalog          — parses catalog.json, fetches latest APK URLs

SleepScheduler        — idle-timeout + overnight window via AlarmManager + lockNow()
BootReceiver          — reaffirms screensaver settings + re-arms chime alarms after boot
AdminReceiver         — device admin (force-lock only), activated by provisioning

BackHelper            — shared "perform BACK" logic
ImmortalBackGestureService  — accessibility service back gesture
SystemBackGestureService    — overlay back-swipe (SYSTEM_ALERT_WINDOW)
```

## Key configuration objects

| Object              | SharedPrefs key      | What it stores                        |
| ------------------- | -------------------- | ------------------------------------- |
| `ImmortalSettings`  | `immortal_settings`  | tile size, weather unit, accent, clock format, calendar/stats widgets, sort mode, tabs, dashboard page + its widget toggles, name-day/feast/next-event/sun-times flags, daily-tile mode, background mode (image/blur/gradient/sky) |
| `ScreensaverConfig` | `immortal_screensaver` | enabled, source folder, interval, fit mode, welcome enabled, art feed, soundscape + volume, ambient dashboard |
| `WelcomeConfig`     | `immortal_welcome`   | greeting text per time-of-day, user name, colors, sizes, duration, TTS on/off |
| `DigitalClockConfig`| `digital_clock_config` | style, font, color, size, layout, glow |
| `ChimeConfig`       | `immortal_chime`     | hourly chime / spoken time / golden-hour toggles, per-cue volume, spoken-voice id, quiet-hours window |
| `CountdownConfig`   | `immortal_countdown` | countdown chips (label, emoji, date) |
| `NotesConfig`       | `immortal_notes`     | typed sticky note text + voice-memo file pointer |
| `CameraConfig`      | `immortal_cameras`   | saved rtsp:// camera URLs |
| `Transit`           | `immortal_transit`   | saved transit provider + stop/station id |
| `Weather`           | `immortal_weather`   | cached location, weather, sun times, air quality |

## App catalog (`app/src/main/assets/catalog.json`)

Structure:
```json
{
  "categories": [
    {
      "name": "Category Name",
      "apps": [
        {
          "name": "App Name",
          "packageName": "com.example.app",
          "description": "...",
          "apkUrl": "https://...",
          "versionName": "1.0",
          "iconUrl": "https://..."
        }
      ]
    }
  ]
}
```

Current categories: Media & Entertainment, Smart Home & Ambient, Productivity,
Utilities & Power Tools, Portal Originals.

When adding apps: prefer F-Droid releases (stable URLs), open-source only,
no GMS dependency, tested on Portal hardware.

## Silent install daemon

The provisioning kit starts `installd.sh` as the `shell` user via ADB. It watches
`$externalFiles/installq/` and installs any `.apk` dropped there, renaming to
`.apk.done` or `.apk.failed`. Immortal checks for a `.heartbeat` file (freshness
window: 20s) to decide if the daemon is alive.

Gen-1 Portal (API 28) **requires** the daemon — the stock system installer dialog
is broken on that firmware. `InstallDaemon.legacyInstaller()` returns `true` on
API < 29. Gen-2 models fall back gracefully to `PackageInstaller`.

## Screensaver / presence interaction

Portal's PowerManager intercepts the screen timeout and decides AMBIENT vs SLEEP
based on a presence service. This presence service uses the Smart Camera SDK, which
is gated behind `signature|privileged` permissions Immortal cannot hold.

What this means in practice:
- **We cannot read presence directly.** `DreamPolicy` works around this by letting
  the system make the ambient/sleep decision, then re-launching `PhotoFramePreviewActivity`
  when the dream stops (if it wasn't a user exit).
- `SleepScheduler` offers an idle-timeout and overnight window using device-admin
  `lockNow()` — no presence API needed.
- Any feature requiring "is someone in the room?" must use the system's ambient/sleep
  signal indirectly (dream start/stop lifecycle), not the Smart Camera SDK directly.

## Smart Camera SDK (future)

Meta has published the Smart Camera API surface. The binary is **not yet available**
(`com.facebook.portal:smartcamera:1.1.+` — pending publication), but the API is
stable enough to design against.

**Permission required:** `com.facebook.portal.permission.SMART_CAMERA_CONTROL`
(not signature-level — watch for this becoming grantable via provisioning once binary ships)

**Key classes:**
```
SmartCameraControlConnectionFactory(context).connect()
  → ControlConnection.requestControls()
    → ControlSession.setMode(ModeSpec)
    → ControlSession.close()
  → ControlConnection.close()
```

**Framing modes:**

| ModeSpec | Best for |
| -------- | -------- |
| `DefaultAuto` | General use; pan, zoom, smooth subject transitions |
| `BasicSpotlight` | Single-subject tracking |
| `Desk` | Fixed-distance single person; tunable sensitivity/speed/tightness |
| `Meeting` | Wide group framing |
| `Fixed` | Manual crop via center coords + scale factor |

`Desk` mode is the one to use for the "someone is home" idle frame.
Always release the session (`close()`) when backgrounding — it's a shared system resource.

**Exceptions to handle:** `ServiceOutOfDateException`, `OwnershipRevokedException`,
`ConnectionClosedException`, `SmartCameraAccessException`.

Camera pixel data is still accessed via the standard Camera2 API; the SDK controls
framing only.

## Portal TV specifics

- `HomeActivity` has D-pad focus logic via `TvFocus.kt`.
- The activity-alias `.ImmortalAppEntry` carries `LEANBACK_LAUNCHER` so it appears
  in Portal TV's `ripleyhome` grid. The main `HomeActivity` carries only `HOME` —
  if it also had `LEANBACK_LAUNCHER`, ripleyhome would filter it out.
- A 320×180 banner (`@drawable/immortal_banner`) is required for the TV card.
- Touch events in `HomeActivity` are guarded — D-pad devices have no touch.
- **TODO:** Portal TV deserves a dedicated 10-foot layout (bigger tiles, no
  small-target UI). Currently it uses the same touch layout with D-pad bolt-on.

## Fonts (assets/fonts/)

All fonts are OFL-1.1 licensed (see `assets/fonts/OFL.txt`):

| File               | Font                  | Source                              |
| ------------------ | --------------------- | ----------------------------------- |
| `digital_7.ttf`    | DSEG7Classic-Regular  | github.com/keshikan/DSEG            |
| `segment_led.ttf`  | DSEG14Classic-Regular | github.com/keshikan/DSEG            |
| `technology.ttf`   | Orbitron Regular      | github.com/googlefonts/orbitron     |
| `technology_bold.ttf` | Orbitron Bold      | github.com/googlefonts/orbitron     |

Do NOT replace these with personal-use-only fonts (Digital-7, Technology by
V.Nikolic, etc.) — they cannot be bundled in a redistributed open-source APK.

## Build & deploy

```bash
# Build debug APK (Windows)
.\gradlew.bat :app:assembleDebug

# Install via ADB (device connected via USB-C, ADB enabled in Portal dev settings)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# metavr CLI (recommended — install once with: npm install -g @meta-quest/metavr)
metavr app install -r app/build/outputs/apk/debug/app-debug.apk
metavr app launch com.immortal.launcher
metavr log --tag ImmortalDream --tag ImmortalStore --level D
metavr capture screenshot -o screen.png

# Or use hzdb MCP (if installed in Claude Code — gives in-session deploy + logcat)
hzdb app install app/build/outputs/apk/debug/app-debug.apk
hzdb app launch com.immortal.launcher
hzdb log --tag ImmortalDream --tag ImmortalStore --level D
```

metavr and hzdb are from the same Meta ecosystem (`@meta-quest/*`). metavr is the
standalone CLI; hzdb is the MCP bridge for in-session use from Claude Code.
Install hzdb MCP with: `npx -y @meta-quest/hzdb mcp install claude-code`

**Native libs & APK size:** there are now **no native dependencies** — the debug
APK is ~31 MB. (Vosk offline STT was removed: on Portal it fought the always-on
listeners for the single near-field mic and needed a runtime model download, so it
couldn't be used; it had bloated the APK to ~190 MB with its per-ABI `.so` files.)
`app/build.gradle.kts` still keeps `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }`
as a guard so any future native dep can't drag x86/x86_64 in — all Portal hardware
is ARM (gen-1/gen-2 Snapdragon, Portal TV Amlogic). Do not add x86 back.

**Runtime permissions / access added for ambient features:** `RECORD_AUDIO`
(voice notes, intercom); now-playing screensaver needs the user to enable
`NowPlayingListenerService` once in notification-listener access (no manifest grant
can force it).

**Microphone reality on Portal:** a sideloaded app only reaches the single
near-field handset mic (the far-field beamformed array is behind a Meta-signed
permission), and that mic is shared with the device's always-on listeners. So
`AudioNote` (leave-a-note voice memo) records usable audio only when the user
speaks close to the device — the overlay shows a live input meter + "speak closer"
hint to make that obvious. A voice from across the room reads as near-silence.

Release builds require `keystore.properties` at repo root or
`~/.immortal-signing/keystore.properties`. See `app/build.gradle.kts` for details.
The same key must be used for every release — self-update (`UpdateManager`) verifies
the signature before installing.

## Design guidelines (Portal-native)

From Meta's official Portal design requirements (June 2026):
- Dark theme: background `#1A1A1A`, surfaces `#2B2B2B`
- Primary actions: Meta blue `#0866FF`, near-white text `#F0F0F0`
- Body text: `#DADADA` on dark; never pure black or pure white
- Typography: **Inter** Normal/Medium/Bold (Graphik is the platform face; Inter is
  the accepted substitute); body 18sp, headings 24sp Bold, min 14sp
- **Touch targets: min 64dp tall, primary actions 96dp; 16dp between targets**
  (Note: 52dp is the Android minimum; Portal spec is 64dp for the 50–100 cm
  viewing distance. Primary action buttons must be 96dp.)
- Layout: reserve top 64dp for system overlay, 16dp padding all sides,
  max-width content column ~760dp (centre, don't stretch full-width)
- Icons: 512×512px PNG in `mipmap-xxxhdpi/` only (adaptive icons not supported)
- Spacing baseline grid: 4dp; page margins 36–48dp

**Viewing distance:** Portal sits 50–100 cm away. All sizing decisions should be
made for that distance — not phone distances.

**Top system overlay:** The back/home/Wi-Fi pill floats above content as **white
icons with no background**. Light content in the top 64dp is invisible against it.
Always use dark backgrounds or a semi-transparent dark scrim in that zone.

## Welcome-overlay TTS (Android TextToSpeech)

The welcome greeting speaks through Android's built-in `TextToSpeech` engine.
**Piper neural TTS (Sherpa-ONNX) was removed** — its voice model is a ~63 MB download
that the Portal's connection truncates, and a truncated `.onnx` makes onnxruntime
abort *natively* (uncatchable `SIGABRT`), which took the whole dream/launcher process
down (it presented as "screensaver/photo feed crashes"). Removing it also dropped the
APK from ~88 MB to ~31 MB (the bundled sherpa-onnx AAR was 56 MB).

**Flow in `PhotoFrameController.start()`:** TTS is only initialized when the welcome
overlay is shown *and* `WelcomeConfig.enableTts` is on. On init it applies the user's
chosen voice (`WelcomeConfig.ttsVoice`); if none is chosen it auto-selects the
**highest-quality** non-network voice the device has (`Voice.getQuality()`).

**Voice picker:** Welcome settings lists the device's installed TTS voices (sorted
highest-quality first, with an `HQ`/`HQ+` hint), each with an instant Test button.
The Sounds screen's spoken-time picker works the same way. No download, no native
crash. (Quality is bounded by whatever TTS engine the Portal has installed.)

**TTS is off by default.** Users enable it in Welcome settings (`WelcomeConfig.enableTts`).

## What hzdb / metavr give you (in Claude Code sessions)

With hzdb MCP installed (`npx -y @meta-quest/hzdb mcp install claude-code`):
- Deploy, launch, stop apps directly from the coding session
- Live logcat filtered by tag
- Screenshots to verify UI changes
- `hzdb docs search "smart camera"` for Smart Camera SDK API reference
- The `portal-development` skill auto-loads when minSdkVersion 28-29 is detected

metavr MCP can be installed with: `metavr mcp install claude-code`
