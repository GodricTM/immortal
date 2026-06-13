/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Handles the idle and overnight alarms from [SleepScheduler]. */
class SleepReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      SleepScheduler.ACTION_SLEEP_TIMER -> {
        Log.i(TAG, "sleep timer reached")
        val settings = ScreensaverConfig.load(context)
        SleepScheduler.sleepNow(
            context,
            pauseAudio = settings.pauseAudioOnSleep,
            closeApp = settings.closeAppOnSleep,
        )
      }
      SleepScheduler.ACTION_IDLE -> {
        Log.i(TAG, "idle timeout reached → sleep")
        SleepScheduler.sleepNow(context, pauseAudio = false, closeApp = false)
      }
      SleepScheduler.ACTION_OVERNIGHT_START -> {
        Log.i(TAG, "overnight start → sleep")
        // Disable the screensaver for the window so nothing re-lights the screen,
        // then blank it. HomeActivity.onResume re-sleeps any accidental wake.
        SettingsGuard.reaffirmScreensaver(context)
        ScreenControl.sleep(context)
        SleepScheduler.scheduleOvernight(context) // arm tomorrow
      }
      SleepScheduler.ACTION_OVERNIGHT_END -> {
        Log.i(TAG, "overnight end → restore screensaver")
        SettingsGuard.reaffirmScreensaver(context) // restores enabled per user setting
        SleepScheduler.scheduleOvernight(context) // arm tomorrow
      }
    }
  }

  private companion object {
    const val TAG = "ImmortalSleep"
  }
}
