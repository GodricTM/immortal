/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Process-wide hooks. As the home app our process is effectively persistent, so a
 * runtime-registered receiver here behaves like a manifest one without the
 * background-broadcast restrictions: DREAMING_STOPPED → [DreamPolicy.onDreamingStopped]
 * (keep the photo frame up when the system force-wakes the screensaver).
 */
class ImmortalApp : Application() {
  override fun onCreate() {
    super.onCreate()
    val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_DREAMING_STOPPED) {
              DreamPolicy.onDreamingStopped(c)
            }
          }
        }
    registerReceiver(receiver, IntentFilter(Intent.ACTION_DREAMING_STOPPED))

    // Arm the overnight screen-off window (and apply it if we're inside it now).
    SleepScheduler.applyOvernightNow(this)
    // Arm the ambient chime / spoken-time / golden-hour alarms per the user's config.
    ChimeScheduler.reschedule(this)
  }
}
