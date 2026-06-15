/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context

/**
 * User configuration for the photo-frame screensaver, persisted across restarts.
 *
 * The default source is Immortal's built-in photo feed; the user can instead point
 * the screensaver at a local folder of photos/videos — including one on a USB-C or
 * SD card plugged into the Portal (any folder reachable through the system file
 * picker). If the chosen folder can't be read (e.g. the drive is unplugged) the
 * screensaver falls back to the default feed, so it's never blank.
 */
object ScreensaverConfig {

  private const val PREFS = "immortal_screensaver"

  const val SOURCE_DEFAULT = "default"
  const val SOURCE_FOLDER = "folder"
  const val FIT_FILL = "fill" // crop to fill the screen
  const val FIT_FIT = "fit" // letterbox to show the whole image
  const val DEFAULT_INTERVAL = 30

  // Ambient soundscape played while the screensaver is showing. All are synthesized
  // on-device (no audio assets, no streaming), so they work offline on the Portal.
  const val SOUND_OFF = "off"
  const val SOUND_RAIN = "rain"
  const val SOUND_OCEAN = "ocean"
  const val SOUND_FIREPLACE = "fireplace"
  const val SOUND_WHITE = "white"
  const val SOUND_PINK = "pink"
  const val SOUND_BROWN = "brown"
  val SOUNDSCAPES =
      listOf(SOUND_OFF, SOUND_RAIN, SOUND_OCEAN, SOUND_FIREPLACE, SOUND_WHITE, SOUND_PINK, SOUND_BROWN)
  fun soundscapeLabel(s: String): String = when (s) {
    SOUND_RAIN -> "Rain"
    SOUND_OCEAN -> "Ocean waves"
    SOUND_FIREPLACE -> "Fireplace"
    SOUND_WHITE -> "White noise"
    SOUND_PINK -> "Pink noise"
    SOUND_BROWN -> "Brown noise"
    else -> "Off"
  }

  // Online photo feeds (used when source == SOURCE_DEFAULT). All keyless.
  const val FEED_PICSUM = "picsum" // Lorem Picsum random photos (current default)
  const val FEED_MET = "met" // The Met Museum Open Access
  const val FEED_ARTIC = "artic" // Art Institute of Chicago
  const val FEED_WIKIMEDIA = "wikimedia" // Wikimedia Picture of the Day
  const val FEED_APOD = "apod" // NASA Astronomy Picture of the Day (DEMO_KEY)
  val FEEDS = listOf(FEED_PICSUM, FEED_MET, FEED_ARTIC, FEED_WIKIMEDIA, FEED_APOD)
  fun feedLabel(feed: String): String = when (feed) {
    FEED_MET -> "The Met — art"
    FEED_ARTIC -> "Art Institute of Chicago"
    FEED_WIKIMEDIA -> "Wikimedia Picture of the Day"
    FEED_APOD -> "NASA Astronomy Picture"
    else -> "Random photos (Picsum)"
  }

  data class Settings(
      // Master on/off for Immortal's photo-frame screensaver. When off, Immortal
      // stops asserting itself as the system Dream and lets the Portal sleep / lets
      // the user run their own screensaver (e.g. Home Assistant + Immich frame).
      val enabled: Boolean = true,
      val source: String = SOURCE_DEFAULT,
      // Which online feed to use when source == SOURCE_DEFAULT.
      val feed: String = FEED_PICSUM,
      val folderPath: String? = null,
      val fit: String = FIT_FILL,
      val intervalSec: Int = DEFAULT_INTERVAL,
      val shuffle: Boolean = false,
      val includeVideo: Boolean = true,
      // Battery models (Portal Go) only: pause the screensaver while unplugged so
      // the device can actually sleep, instead of showing photos until empty.
      val batterySaver: Boolean = true,
      // Idle screen-off (off by default): minutes the screensaver runs before the
      // screen turns off. 0 = never (Immortal's always-on photo frame).
      val idleSleepMin: Int = 0,
      // Sleep timer: a one-shot countdown before sleep.
      val sleepTimerEnabled: Boolean = false,
      val sleepTimerMin: Int = 30,
      val pauseAudioOnSleep: Boolean = true,
      val closeAppOnSleep: Boolean = true,
      // Overnight screen-off (off by default): keep the screen off between two times
      // each night. Times are minutes-from-midnight (e.g. 22:00 = 1320).
      val overnightEnabled: Boolean = false,
      val overnightStartMin: Int = 22 * 60,
      val overnightEndMin: Int = 7 * 60,
      // Ambient soundscape (synthesized) played while the screensaver shows. Off by
      // default; [soundscapeVolume] is 0..100.
      val soundscape: String = SOUND_OFF,
      val soundscapeVolume: Int = 40,
      // Ambient dashboard: periodically interrupt the photos with a full-screen
      // glanceable info card (clock, weather, next event). Off by default.
      val ambientDashboard: Boolean = false,
      // Experimental: wave a hand in front of the camera to advance the photo frame
      // (Camera2 motion detection, never the gated Smart Camera SDK). Off by default;
      // no-ops without the CAMERA permission.
      val gestureWave: Boolean = false,
      // Welcome-back overlay: shown for ~3s when the screensaver first starts
      // (i.e. when presence is detected and the Portal wakes from sleep). Shows
      // a greeting, the time, and the date. Dismissed by tap or auto-fade.
      val welcomeEnabled: Boolean = true,
  ) {
    /** True when the idle screen-off timeout is active. */
    val idleSleepOn: Boolean
      get() = idleSleepMin > 0
    /** True when the user has chosen a local folder for us to read. */
    val usesFolder: Boolean
      get() = source == SOURCE_FOLDER && !folderPath.isNullOrBlank()
  }

  /** Keep the slideshow interval sane (5s … 10min). */
  fun clampInterval(sec: Int): Int = sec.coerceIn(5, 600)

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context): Settings {
    val p = prefs(context)
    return Settings(
        enabled = p.getBoolean("enabled", true),
        source = p.getString("source", SOURCE_DEFAULT) ?: SOURCE_DEFAULT,
        feed = p.getString("feed", FEED_PICSUM) ?: FEED_PICSUM,
        folderPath = p.getString("folder_path", null),
        fit = p.getString("fit", FIT_FILL) ?: FIT_FILL,
        intervalSec = clampInterval(p.getInt("interval_sec", DEFAULT_INTERVAL)),
        shuffle = p.getBoolean("shuffle", false),
        includeVideo = p.getBoolean("include_video", true),
        batterySaver = p.getBoolean("battery_saver", true),
        idleSleepMin = p.getInt("idle_sleep_min", 0),
        sleepTimerEnabled = p.getBoolean("sleep_timer_enabled", false),
        sleepTimerMin = p.getInt("sleep_timer_min", 30),
        pauseAudioOnSleep = p.getBoolean("pause_audio_on_sleep", true),
        closeAppOnSleep = p.getBoolean("close_app_on_sleep", true),
        overnightEnabled = p.getBoolean("overnight_enabled", false),
        overnightStartMin = p.getInt("overnight_start_min", 22 * 60),
        overnightEndMin = p.getInt("overnight_end_min", 7 * 60),
        soundscape = p.getString("soundscape", SOUND_OFF) ?: SOUND_OFF,
        soundscapeVolume = p.getInt("soundscape_volume", 40).coerceIn(0, 100),
        ambientDashboard = p.getBoolean("ambient_dashboard", false),
        gestureWave = p.getBoolean("gesture_wave", false),
        welcomeEnabled = p.getBoolean("welcome_enabled", true),
    )
  }

  fun setSoundscape(c: Context, s: String) = prefs(c).edit().putString("soundscape", s).apply()

  fun setSoundscapeVolume(c: Context, v: Int) =
      prefs(c).edit().putInt("soundscape_volume", v.coerceIn(0, 100)).apply()

  fun setAmbientDashboard(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("ambient_dashboard", on).apply()

  fun setGestureWave(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("gesture_wave", on).apply()

  /** Keep the idle timeout sane (0 = off, else 1…120 min). */
  fun clampIdle(min: Int): Int = if (min <= 0) 0 else min.coerceIn(1, 120)

  /** Minutes-from-midnight wrapped into 0…1439. */
  fun wrapMinuteOfDay(min: Int): Int = ((min % 1440) + 1440) % 1440

  fun setFolder(c: Context, path: String) =
      prefs(c).edit().putString("folder_path", path).putString("source", SOURCE_FOLDER).apply()

  fun useDefault(c: Context) = prefs(c).edit().putString("source", SOURCE_DEFAULT).apply()

  /** Pick the online feed and switch the source back to the default (online) feed. */
  fun setFeed(c: Context, feed: String) =
      prefs(c).edit().putString("feed", feed).putString("source", SOURCE_DEFAULT).apply()

  fun setFit(c: Context, fit: String) = prefs(c).edit().putString("fit", fit).apply()

  fun setInterval(c: Context, sec: Int) =
      prefs(c).edit().putInt("interval_sec", clampInterval(sec)).apply()

  fun setShuffle(c: Context, on: Boolean) = prefs(c).edit().putBoolean("shuffle", on).apply()

  fun setIncludeVideo(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("include_video", on).apply()

  fun setBatterySaver(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("battery_saver", on).apply()

  fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean("enabled", on).apply()

  fun setIdleSleepMin(c: Context, min: Int) =
      prefs(c).edit().putInt("idle_sleep_min", clampIdle(min)).apply()

  fun setSleepTimerEnabled(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("sleep_timer_enabled", on).apply()

  fun setSleepTimerMin(c: Context, min: Int) =
      prefs(c).edit().putInt("sleep_timer_min", clampSleepTimer(min)).apply()

  fun setPauseAudioOnSleep(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("pause_audio_on_sleep", on).apply()

  fun setCloseAppOnSleep(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("close_app_on_sleep", on).apply()

  fun clampSleepTimer(min: Int): Int = min.coerceIn(1, 240)

  fun setWelcomeEnabled(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("welcome_enabled", on).apply()

  fun setOvernightEnabled(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("overnight_enabled", on).apply()

  fun setOvernightStartMin(c: Context, min: Int) =
      prefs(c).edit().putInt("overnight_start_min", wrapMinuteOfDay(min)).apply()

  fun setOvernightEndMin(c: Context, min: Int) =
      prefs(c).edit().putInt("overnight_end_min", wrapMinuteOfDay(min)).apply()
}
