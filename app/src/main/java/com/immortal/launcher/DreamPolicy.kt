/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

/**
 * Cooperates with the Portal build's presence-driven power policy.
 *
 * How this build behaves (measured on device):
 *  - At the screen timeout, Meta's PowerManager decides AMBIENT vs SLEEP from the
 *    presence service: someone around → start the dream; empty room → real sleep.
 *    (`screensaver_activate_on_sleep` is ignored.)
 *  - A running dream is force-woken at `last-activity + min(screen_off + 120s,
 *    sleep_timeout)` — dreams are transient by design; stock SuperFrame caught
 *    that wake, so we must too.
 *
 * Immortal's policy:
 *  - When the system bounces the dream (not a user tap, not a power-button
 *    sleep), instantly relaunch the same frame as [PhotoFramePreviewActivity].
 *  - The frame HOLDS the screen on mains-powered Portals (and on the Go while
 *    charging, or with the battery saver off) → permanent photo frame.
 *  - On the Go on battery with the saver on, the frame does NOT hold the screen:
 *    each screen timeout becomes a fresh presence decision — someone around →
 *    the dream takes over again (visually identical, so it reads as one
 *    continuous frame); empty room → the device truly sleeps.
 */
object DreamPolicy {
  private const val TAG = "ImmortalDream"

  /** Set by [PhotoDreamService] just before finish() on a user tap. */
  @Volatile var userExitAt: Long = 0L

  /**
   * Set by [HomeActivity] when it deliberately bridges to the stock launcher (the
   * Calls tile). The stock launcher cold-starts into its own idle "dream" face and
   * immediately stops it, firing ACTION_DREAMING_STOPPED — which would otherwise
   * make us relaunch our photo frame over the top of the stock home and trap the
   * user there (the reported "Calls kicks me back into Immortal"). While a bridge
   * is in flight we suppress that relaunch.
   */
  @Volatile var bridgeAt: Long = 0L

  /**
   * True from the moment the Calls tile bridges to the stock launcher until the
   * user comes back to Immortal (cleared in [HomeActivity.onResume]). While set, we
   * never relaunch the holding photo-frame Activity, so it can't slam over the stock
   * home while the user is in a call flow. The system Dream still shows photos on
   * idle as usual — we only skip the aggressive "permanent frame" relaunch.
   */
  @Volatile var inStockHandoff: Boolean = false

  /** How long after a deliberate bridge we keep suppressing the frame relaunch. */
  private const val BRIDGE_GRACE_MS = 8000L

  fun hasBattery(context: Context): Boolean =
      runCatching {
            context
                .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
          }
          .getOrDefault(false)

  fun isPowered(context: Context): Boolean =
      runCatching {
            (context
                .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
          }
          .getOrDefault(true)

  /**
   * Pure decision (unit-tested): should the frame hold the screen on?
   * Holding means a permanent frame; not holding hands control back to the
   * system's presence policy at every screen timeout.
   *
   * Note: presence-driven "sleep when the room's empty" is NOT achievable for us —
   * that decision was made by Meta's SuperFrame, which Immortal replaces, and the
   * presence signal is gated behind signature|privileged permissions we can't hold.
   * Instead, [SleepScheduler] offers an idle timeout and an overnight window that
   * turn the screen off via device-admin lockNow().
   */
  internal fun holdScreenOn(
      hasBattery: Boolean,
      batterySaver: Boolean,
      powered: Boolean,
  ): Boolean = !hasBattery || !batterySaver || powered

  /** Pure decision (unit-tested): should a dream-stop relaunch the frame? */
  internal fun shouldRelaunch(userExitAgoMs: Long, interactive: Boolean): Boolean {
    if (userExitAgoMs in 0..4000) return false // user tapped out of the dream
    return interactive // power button / real sleep — leave it be
  }

  /** Pure decision (unit-tested): is a deliberate stock-home bridge still in flight? */
  internal fun bridging(bridgeAgoMs: Long): Boolean = bridgeAgoMs in 0..BRIDGE_GRACE_MS

  /** Called on ACTION_DREAMING_STOPPED: continue the frame unless the user ended it. */
  fun onDreamingStopped(context: Context) {
    if (!ScreensaverConfig.load(context).enabled) {
      Log.i(TAG, "screensaver disabled; not relaunching frame")
      return
    }
    if (inStockHandoff || bridging(System.currentTimeMillis() - bridgeAt)) {
      Log.i(TAG, "stock-launcher handoff; suppressing frame relaunch")
      return
    }
    val pm = context.getSystemService(PowerManager::class.java)
    val relaunch =
        shouldRelaunch(
            userExitAgoMs = System.currentTimeMillis() - userExitAt,
            interactive = pm?.isInteractive == true,
        )
    Log.i(TAG, "dream stopped; relaunch frame = $relaunch")
    if (!relaunch) return
    runCatching {
      context.startActivity(
          Intent(context, PhotoFramePreviewActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              // The dream bounced = Portal's presence sensor saw someone arrive.
              // Show the welcome-back overlay on this relaunch.
              .putExtra(PhotoFramePreviewActivity.EXTRA_SHOW_WELCOME, true))
    }
        .onFailure { Log.w(TAG, "frame relaunch failed", it) }
  }
}
