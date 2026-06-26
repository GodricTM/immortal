# Immortal — Custom Features Documentation

This folder documents all the custom features and changes added to the
upstream [starbrightlab/immortal](https://github.com/starbrightlab/immortal)
project. Immortal revives discontinued Meta Portal devices as smart displays
— this fork adds a rich set of customization and usability features on top.

## Quick links

- **[CHANGES.md](CHANGES.md)** — Full changelog of all modifications
- **[FEATURES.md](FEATURES.md)** — Detailed feature documentation
- **[CUSTOMIZATION.md](CUSTOMIZATION.md)** — Theming and configuration guide
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — Code structure and design notes
- **[images/](images/)** — Screenshots and preview images

## At a glance

What's been added on top of the upstream Immortal:

| Area | Features |
|---|---|
| **Launcher** | 16 accent colors, custom background image, gesture-to-go-back, right-edge swipe for folders, New Folder button in Manage mode, ForkHome tool-folder system, daily content tile, time progress cards, sky/stars/gradient backgrounds |
| **Screensaver** | Digital clock (6 styles, 8 colors, 8 fonts, 4 sizes, layouts, glow), ambient soundscape (6 procedural sounds), anti-burn-in pixel-shift, gesture wave-to-advance, welcome-back overlay with TTS, ambient dashboard |
| **Sound & voice** | Hourly chimes (chime/spoken-time/golden-hour), per-cue volume, quiet hours, LAN ping, companion sherpa-onnx TTS engine (28 English + Romanian voices, offline) |
| **Alarms & timers** | Sunrise alarm / wake light, kitchen timers (home chips), countdown event chips, sleep timer with live countdown |
| **Sky tools** | ISS overhead predictor (offline SGP4), aurora forecast (NOAA Kp), star-field night background, unit + currency converter |
| **Camera & comms** | RTSP camera viewer, LAN intercom (PCM over TCP), audio notes / fridge notes |
| **Content** | Bedtime stories with TTS, daily quote/word/trivia, Dublin transit departures, "What's new" from GitHub, "Did you know" tips |
| **Calendar & locale** | Romanian name days, Orthodox feast calendar, Irish holidays, Islamic prayer times, calendar agenda widget |
| **System-wide** | Accessibility "go back" shortcut, overlay-based back swipe gesture, back-to-Immortal routing |
| **Settings** | All fork features registry-native (4 new domains, tripwire tests, `onApplied` side effects) |
| **App store** | Portal Overlays listed in catalog (Utilities & Power Tools) |
