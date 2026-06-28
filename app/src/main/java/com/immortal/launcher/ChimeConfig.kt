/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context

/**
 * Gentle ambient audio cues, all off by default:
 *  - [hourlyChimeOn]   a soft chime on the hour (res/raw/chime.mp3)
 *  - [spokenTimeOn]    spoken time on the hour ("It's three o'clock"), via TTS
 *  - [goldenHourOn]    a single tone at sunrise and sunset (golden-hour marker)
 *
 * [Quiet hours][quietHoursOn] mute ALL of the above inside a nightly window
 * (default 22:00 → 08:00), so nothing chimes while the household sleeps.
 *
 * Scheduling lives in [ChimeScheduler]; playback in [ChimePlayer].
 */
object ChimeConfig {

  private const val PREFS = "immortal_chime"

  data class Settings(
      val hourlyChimeOn: Boolean = false,
      val spokenTimeOn: Boolean = false,
      val goldenHourOn: Boolean = false,
      val quietHoursOn: Boolean = true,
      val quietStartMin: Int = 22 * 60, // 22:00
      val quietEndMin: Int = 8 * 60, // 08:00
      // Playback volume per cue (0..100). Default below full so cues are gentle.
      val chimeVolume: Int = 60,
      val spokenVolume: Int = 70,
      val goldenVolume: Int = 60,
      // "Ping the other room" ring volume (0..100). Louder by default — it's a doorbell.
      val pingVolume: Int = 85,
      // TTS voice name for spoken time (TextToSpeech.Voice.getName()); "" = engine default.
      val spokenVoice: String = "",
      // Which sunrise sound to play: 0 = "Morning", 1 = "Rooster".
      val sunriseVariant: Int = 0,
  )

  /** True if any audible hourly feature is enabled (so the scheduler should run). */
  fun Settings.anyHourlyOn(): Boolean = hourlyChimeOn || spokenTimeOn

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context): Settings {
    val p = prefs(context)
    return Settings(
        hourlyChimeOn = p.getBoolean("hourly_chime_on", false),
        spokenTimeOn = p.getBoolean("spoken_time_on", false),
        goldenHourOn = p.getBoolean("golden_hour_on", false),
        quietHoursOn = p.getBoolean("quiet_hours_on", true),
        quietStartMin = p.getInt("quiet_start_min", 22 * 60),
        quietEndMin = p.getInt("quiet_end_min", 8 * 60),
        chimeVolume = p.getInt("chime_volume", 60).coerceIn(0, 100),
        spokenVolume = p.getInt("spoken_volume", 70).coerceIn(0, 100),
        goldenVolume = p.getInt("golden_volume", 60).coerceIn(0, 100),
        pingVolume = p.getInt("ping_volume", 85).coerceIn(0, 100),
        spokenVoice = p.getString("spoken_voice", "") ?: "",
        sunriseVariant = p.getInt("sunrise_variant", 0).coerceIn(0, 1),
    )
  }

  fun setSpokenVoice(c: Context, name: String) =
      prefs(c).edit().putString("spoken_voice", name).apply()

  fun setSunriseVariant(c: Context, variant: Int) =
      prefs(c).edit().putInt("sunrise_variant", variant.coerceIn(0, 1)).apply()

  fun setChimeVolume(c: Context, v: Int) =
      prefs(c).edit().putInt("chime_volume", v.coerceIn(0, 100)).apply()

  fun setSpokenVolume(c: Context, v: Int) =
      prefs(c).edit().putInt("spoken_volume", v.coerceIn(0, 100)).apply()

  fun setGoldenVolume(c: Context, v: Int) =
      prefs(c).edit().putInt("golden_volume", v.coerceIn(0, 100)).apply()

  fun setPingVolume(c: Context, v: Int) =
      prefs(c).edit().putInt("ping_volume", v.coerceIn(0, 100)).apply()

  fun setHourlyChime(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("hourly_chime_on", on).apply()

  fun setSpokenTime(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("spoken_time_on", on).apply()

  fun setGoldenHour(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("golden_hour_on", on).apply()

  fun setQuietHours(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("quiet_hours_on", on).apply()

  fun setQuietStart(c: Context, min: Int) =
      prefs(c).edit().putInt("quiet_start_min", wrapMinuteOfDay(min)).apply()

  fun setQuietEnd(c: Context, min: Int) =
      prefs(c).edit().putInt("quiet_end_min", wrapMinuteOfDay(min)).apply()

  /** Minutes-from-midnight wrapped into 0…1439 (matches [ScreensaverConfig.wrapMinuteOfDay]). */
  fun wrapMinuteOfDay(min: Int): Int = ((min % 1440) + 1440) % 1440

  /** Is [nowMin] (minute-of-day) inside the quiet window? Pure + unit-testable.
   * Handles the usual case where the window wraps past midnight. */
  fun inQuietWindow(nowMin: Int, startMin: Int, endMin: Int): Boolean =
      if (startMin == endMin) false
      else if (startMin < endMin) nowMin in startMin until endMin
      else nowMin >= startMin || nowMin < endMin

  /** Should cues be silenced right now? True when quiet hours are on and we're inside. */
  fun isQuietNow(context: Context, nowMin: Int): Boolean {
    val s = load(context)
    return s.quietHoursOn && inQuietWindow(nowMin, s.quietStartMin, s.quietEndMin)
  }
}
