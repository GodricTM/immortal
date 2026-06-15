/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context

/**
 * "Did you know" discoverability tips. Immortal packs in a lot; a device that quietly
 * teaches its own depth beats one that hides it. One tip surfaces per day (deterministic
 * by epoch-day so it's stable across a day and rotates predictably), and the user can
 * dismiss the day's tip — it won't reappear until tomorrow's.
 */
object Tips {

  private const val PREFS = "immortal_tips"
  private const val KEY_DISMISSED_DAY = "dismissed_day"

  /** Curated, each pointing at a real feature. Keep them short — they're a glance. */
  val TIPS =
      listOf(
          "Set several kitchen timers at once — tap the timer icon and name each one (pasta, oven, tea).",
          "The ISS Pass tile tells you when the space station flies over, and whether it'll be bright enough to spot.",
          "The Aurora tile lights up green only when there's a real chance of northern (or southern) lights at your location.",
          "Long-press any app to hide it, see app info, or uninstall — no need to enter Manage mode.",
          "Add countdowns for birthdays and trips in Settings → Countdowns; they show as chips on the home screen.",
          "Turn on the hourly chime or spoken time in Settings → Sounds, with quiet hours overnight.",
          "Leave a note — typed or a quick voice memo — and it pins to the home screen for the household.",
          "The screensaver can play rain, ocean or a fireplace; pick a soundscape in Screensaver settings.",
          "Pick a photo folder for the frame, or let it pull in a free art feed in Screensaver settings.",
          "The Converter tile does length, weight, volume, temperature — and live currency.",
          "Lamp mode fills the screen with warm light — an instant nightlight or reading light.",
          "Swipe in from the right edge to go back, anywhere on the device.",
          "Set a sunrise alarm and the screen brightens gradually to wake you, with an optional chime.",
          "Open the Cameras tile to watch an RTSP camera — handy for a door or driveway feed.",
          "The Intercom tile turns two Portals into a one-way baby monitor over your home Wi-Fi.",
      )

  /** Today's tip text (deterministic per day). */
  fun todaysTip(): String {
    val day = System.currentTimeMillis() / 86_400_000L
    return TIPS[(day % TIPS.size).toInt()]
  }

  /** Has the user dismissed today's tip already? */
  fun isDismissedToday(context: Context): Boolean {
    val day = System.currentTimeMillis() / 86_400_000L
    return prefs(context).getLong(KEY_DISMISSED_DAY, -1L) == day
  }

  fun dismissToday(context: Context) {
    val day = System.currentTimeMillis() / 86_400_000L
    prefs(context).edit().putLong(KEY_DISMISSED_DAY, day).apply()
  }

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
