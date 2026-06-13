/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import java.util.Calendar

/**
 * Schedules the two presence-free screen-off features via AlarmManager + lockNow():
 *  - Idle timeout: turn the screen off after the screensaver has run for N minutes
 *    with no interaction. The alarm is armed when the screensaver starts and
 *    cancelled when the user returns to Immortal, so it measures one continuous
 *    idle session.
 *  - Overnight window: keep the screen off between two times each night.
 *
 * Both are off by default (see [ScreensaverConfig]).
 */
object SleepScheduler {
  private const val TAG = "ImmortalSleep"

  const val ACTION_IDLE = "com.immortal.launcher.SLEEP_IDLE"
  const val ACTION_SLEEP_TIMER = "com.immortal.launcher.SLEEP_TIMER"
  const val ACTION_OVERNIGHT_START = "com.immortal.launcher.OVERNIGHT_START"
  const val ACTION_OVERNIGHT_END = "com.immortal.launcher.OVERNIGHT_END"

  private const val RC_IDLE = 1001
  private const val RC_SLEEP_TIMER = 1004
  private const val RC_OVERNIGHT_START = 1002
  private const val RC_OVERNIGHT_END = 1003

  private fun alarms(c: Context) = c.getSystemService(AlarmManager::class.java)

  private fun pi(c: Context, action: String, rc: Int, create: Boolean): PendingIntent? {
    val flags =
        (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(
        c, rc, Intent(c, SleepReceiver::class.java).setAction(action), flags)
  }

  private fun setAlarm(c: Context, atMs: Long, action: String, rc: Int) {
    val p = pi(c, action, rc, create = true) ?: return
    runCatching { alarms(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p) }
        .onFailure {
          // No exact-alarm permission: an inexact alarm is fine for these features.
          runCatching { alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p) }
        }
  }

  private fun cancel(c: Context, action: String, rc: Int) {
    pi(c, action, rc, create = false)?.let { alarms(c).cancel(it); it.cancel() }
  }

  // ----- idle timeout --------------------------------------------------------

  /** Arm the idle alarm when a screensaver session begins (no-op if one is pending). */
  fun armIdle(context: Context) {
    val cfg = ScreensaverConfig.load(context)
    if (!cfg.enabled || !cfg.idleSleepOn) return
    if (pi(context, ACTION_IDLE, RC_IDLE, create = false) != null) return // already counting
    val at = System.currentTimeMillis() + cfg.idleSleepMin * 60_000L
    setAlarm(context, at, ACTION_IDLE, RC_IDLE)
    Log.i(TAG, "idle sleep armed for ${cfg.idleSleepMin} min")
  }

  /** Cancel the idle alarm — the user is interacting again. */
  fun cancelIdle(context: Context) = cancel(context, ACTION_IDLE, RC_IDLE)

  // ----- one-shot sleep timer ------------------------------------------------

  /** Arm a one-shot sleep timer. */
  fun armSleepTimer(context: Context) {
    val cfg = ScreensaverConfig.load(context)
    if (!cfg.sleepTimerEnabled) {
      cancelSleepTimer(context)
      return
    }
    val minutes = ScreensaverConfig.clampSleepTimer(cfg.sleepTimerMin)
    val at = System.currentTimeMillis() + minutes * 60_000L
    setAlarm(context, at, ACTION_SLEEP_TIMER, RC_SLEEP_TIMER)
    Log.i(TAG, "sleep timer armed for $minutes min")
  }

  /** Cancel the one-shot sleep timer. */
  fun cancelSleepTimer(context: Context) = cancel(context, ACTION_SLEEP_TIMER, RC_SLEEP_TIMER)

  // ----- overnight window ----------------------------------------------------

  fun isOvernightNow(context: Context): Boolean {
    val cfg = ScreensaverConfig.load(context)
    if (!cfg.overnightEnabled) return false
    return inWindow(nowMinuteOfDay(), cfg.overnightStartMin, cfg.overnightEndMin)
  }

  /** Pure (unit-tested): is [now] inside the [start,end) window, handling wrap past midnight? */
  fun inWindow(now: Int, start: Int, end: Int): Boolean =
      if (start == end) false
      else if (start < end) now in start until end
      else now >= start || now < end // wraps midnight

  /** (Re)schedule the daily start/end alarms, or clear them if disabled. */
  fun scheduleOvernight(context: Context) {
    val cfg = ScreensaverConfig.load(context)
    if (!cfg.overnightEnabled) {
      cancel(context, ACTION_OVERNIGHT_START, RC_OVERNIGHT_START)
      cancel(context, ACTION_OVERNIGHT_END, RC_OVERNIGHT_END)
      return
    }
    setAlarm(context, nextOccurrence(cfg.overnightStartMin), ACTION_OVERNIGHT_START, RC_OVERNIGHT_START)
    setAlarm(context, nextOccurrence(cfg.overnightEndMin), ACTION_OVERNIGHT_END, RC_OVERNIGHT_END)
    Log.i(TAG, "overnight scheduled ${cfg.overnightStartMin}→${cfg.overnightEndMin}")
  }

  fun sleepNow(context: Context, pauseAudio: Boolean = true, closeApp: Boolean = true) {
    if (pauseAudio) pauseAudio(context)
    if (closeApp) closeCurrentApp(context)
    SettingsGuard.setSystemScreensaverEnabled(context, false)
    ScreenControl.sleep(context)
  }

  /** Apply the right state immediately (on boot, app start, or a settings change). */
  fun applyOvernightNow(context: Context) {
    scheduleOvernight(context)
    if (isOvernightNow(context)) {
      SettingsGuard.reaffirmScreensaver(context) // forces screensaver off inside the window
      ScreenControl.sleep(context)
    }
  }

  private fun pauseAudio(context: Context) {
    runCatching {
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      am.dispatchMediaKeyEvent(
          android.view.KeyEvent(
              android.view.KeyEvent.ACTION_DOWN,
              android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
          )
      )
      am.dispatchMediaKeyEvent(
          android.view.KeyEvent(
              android.view.KeyEvent.ACTION_UP,
              android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
          )
      )
      Log.i(TAG, "media pause dispatched")
    }.onFailure { Log.w(TAG, "failed to dispatch media pause", it) }
  }

  private fun closeCurrentApp(context: Context) {
    val intent =
        Intent(context, HomeActivity::class.java)
            .setAction("com.immortal.launcher.CLOSE_CURRENT_APP")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onSuccess { Log.i(TAG, "close-app intent sent") }
        .onFailure { Log.w(TAG, "failed to send close-app intent", it) }
  }

  private fun nowMinuteOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
  }

  private fun nextOccurrence(minuteOfDay: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
    c.set(Calendar.MINUTE, minuteOfDay % 60)
    if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
  }
}
