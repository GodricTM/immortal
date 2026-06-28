/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * Schedules the ambient audio cues via AlarmManager:
 *  - a top-of-the-hour alarm for the hourly chime and/or spoken time, which
 *    re-arms itself for the next hour each time it fires;
 *  - one-shot alarms at today's sunrise and sunset for the golden-hour tone,
 *    re-armed (for tomorrow) when they fire.
 *
 * Call [reschedule] on boot, app start, and whenever the chime settings change;
 * it arms exactly the alarms the current config needs and cancels the rest.
 */
object ChimeScheduler {
  private const val TAG = "ImmortalChime"

  const val ACTION_HOURLY = "com.immortal.launcher.CHIME_HOURLY"
  const val ACTION_GOLDEN_SUNRISE = "com.immortal.launcher.CHIME_SUNRISE"
  const val ACTION_GOLDEN_SUNSET = "com.immortal.launcher.CHIME_SUNSET"

  private const val RC_HOURLY = 2001
  private const val RC_SUNRISE = 2002
  private const val RC_SUNSET = 2003

  private fun alarms(c: Context) = c.getSystemService(AlarmManager::class.java)

  private fun pi(c: Context, action: String, rc: Int, create: Boolean): PendingIntent? {
    val flags =
        (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(
        c, rc, Intent(c, ChimeReceiver::class.java).setAction(action), flags)
  }

  private fun setAlarm(c: Context, atMs: Long, action: String, rc: Int) {
    val p = pi(c, action, rc, create = true) ?: return
    runCatching { alarms(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p) }
        .onFailure {
          runCatching { alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p) }
        }
  }

  private fun cancel(c: Context, action: String, rc: Int) {
    pi(c, action, rc, create = false)?.let { alarms(c).cancel(it); it.cancel() }
  }

  /** Arm/cancel every cue alarm to match the current [ChimeConfig]. */
  fun reschedule(context: Context) {
    val cfg = ChimeConfig.load(context)

    if (cfg.hourlyChimeOn || cfg.spokenTimeOn) {
      setAlarm(context, nextTopOfHour(), ACTION_HOURLY, RC_HOURLY)
    } else {
      cancel(context, ACTION_HOURLY, RC_HOURLY)
    }

    if (cfg.goldenHourOn) {
      // fetchSunTimes hits the network — never on the caller's (often main) thread.
      Thread { runCatching { scheduleGoldenHour(context) } }.start()
    } else {
      cancel(context, ACTION_GOLDEN_SUNRISE, RC_SUNRISE)
      cancel(context, ACTION_GOLDEN_SUNSET, RC_SUNSET)
    }
  }

  /** Re-arm the hourly alarm for the next top of the hour (called from the receiver). */
  fun rearmHourly(context: Context) {
    val cfg = ChimeConfig.load(context)
    if (cfg.hourlyChimeOn || cfg.spokenTimeOn) {
      setAlarm(context, nextTopOfHour(), ACTION_HOURLY, RC_HOURLY)
    }
  }

  /** Arm sunrise/sunset alarms from the cached Open-Meteo times. If a time has
   * already passed today, arm it 24h out so it fires tomorrow. */
  fun scheduleGoldenHour(context: Context) {
    val sun = Weather.fetchSunTimes(context) ?: return
    val now = System.currentTimeMillis()
    val sr = if (sun.sunriseMillis > now) sun.sunriseMillis else sun.sunriseMillis + DAY_MS
    val ss = if (sun.sunsetMillis > now) sun.sunsetMillis else sun.sunsetMillis + DAY_MS
    setAlarm(context, sr, ACTION_GOLDEN_SUNRISE, RC_SUNRISE)
    setAlarm(context, ss, ACTION_GOLDEN_SUNSET, RC_SUNSET)
    Log.i(TAG, "golden-hour armed sunrise=$sr sunset=$ss")
  }

  private const val DAY_MS = 24L * 60 * 60 * 1000

  private fun nextTopOfHour(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.HOUR_OF_DAY, 1)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
  }
}
