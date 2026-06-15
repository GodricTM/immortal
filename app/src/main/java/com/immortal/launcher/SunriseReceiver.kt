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

/** Fires at the sunrise-alarm time: launches the full-screen wake light, then arms the
 *  next occurrence. */
class SunriseReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action != SunriseScheduler.ACTION_FIRE) return
    val cfg = SunriseConfig.load(context)
    if (cfg.enabled) {
      runCatching {
        context.startActivity(
            Intent(context, WakeLightActivity::class.java)
                .putExtra(WakeLightActivity.EXTRA_RAMP_MIN, cfg.rampMinutes)
                .putExtra(WakeLightActivity.EXTRA_CHIME, cfg.chime)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }
    // Arm the next day (or next matching weekday).
    SunriseScheduler.reschedule(context)
  }
}
