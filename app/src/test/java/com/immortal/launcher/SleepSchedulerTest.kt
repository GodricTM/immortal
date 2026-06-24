/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The overnight-window membership test, including the wrap past midnight. */
class SleepSchedulerTest {

  @Test
  fun window_wrappingMidnight_includesLateNightAndEarlyMorning() {
    val start = 22 * 60 // 22:00
    val end = 7 * 60 // 07:00
    assertTrue(SleepScheduler.inWindow(22 * 60, start, end)) // 22:00 (start, inclusive)
    assertTrue(SleepScheduler.inWindow(23 * 60 + 30, start, end)) // 23:30
    assertTrue(SleepScheduler.inWindow(2 * 60, start, end)) // 02:00
    assertTrue(SleepScheduler.inWindow(6 * 60 + 59, start, end)) // 06:59
    assertFalse(SleepScheduler.inWindow(7 * 60, start, end)) // 07:00 (end, exclusive)
    assertFalse(SleepScheduler.inWindow(12 * 60, start, end)) // midday
    assertFalse(SleepScheduler.inWindow(21 * 60 + 59, start, end)) // 21:59
  }

  @Test
  fun window_sameDay_doesNotWrap() {
    val start = 9 * 60 // 09:00
    val end = 17 * 60 // 17:00
    assertTrue(SleepScheduler.inWindow(9 * 60, start, end))
    assertTrue(SleepScheduler.inWindow(13 * 60, start, end))
    assertFalse(SleepScheduler.inWindow(17 * 60, start, end)) // end exclusive
    assertFalse(SleepScheduler.inWindow(8 * 60, start, end))
    assertFalse(SleepScheduler.inWindow(23 * 60, start, end))
  }

  @Test
  fun window_startEqualsEnd_isNeverActive() {
    assertFalse(SleepScheduler.inWindow(0, 600, 600))
    assertFalse(SleepScheduler.inWindow(600, 600, 600))
  }
}
