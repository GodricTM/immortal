/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gentle anti-burn-in for the always-on surfaces (the digital-clock screensaver, etc.).
 * A screen that's never off needs its bright pixels to move, or they age unevenly and
 * ghost. This nudges content along a slow Lissajous path so over minutes every pixel
 * shares the load — slow enough that it's invisible, wide enough to matter.
 *
 * Pure + testable: [offsetX]/[offsetY] are a function of the clock only.
 */
object AntiBurnIn {

  // Two near-but-unequal periods (seconds) trace a slowly-precessing loop rather than a
  // repeating line, so no pixel sits on the same track for long.
  private const val PERIOD_X_S = 97.0
  private const val PERIOD_Y_S = 131.0

  /** Horizontal shift in pixels at [nowMs], within ±[maxPx]. */
  fun offsetX(nowMs: Long, maxPx: Float): Float {
    val t = nowMs / 1000.0
    return (sin(2 * PI * t / PERIOD_X_S) * maxPx).toFloat()
  }

  /** Vertical shift in pixels at [nowMs], within ±[maxPx]. */
  fun offsetY(nowMs: Long, maxPx: Float): Float {
    val t = nowMs / 1000.0
    return (cos(2 * PI * t / PERIOD_Y_S) * maxPx).toFloat()
  }
}
