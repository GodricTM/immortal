/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline checks for the pure logic added across the backlog build. */
class BacklogLogicTest {

  // ---- SunriseConfig.nextTrigger ----

  @Test
  fun `sunrise next trigger picks the next matching weekday`() {
    // A Wednesday at 06:00; alarm 07:00 on weekdays → same day 07:00.
    val from = GregorianCalendar(2026, Calendar.JUNE, 17, 6, 0) // Wed 17 Jun 2026
    val cfg = SunriseConfig.Config(
        enabled = true, hour = 7, minute = 0, rampMinutes = 20, chime = true,
        days = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
    val next = SunriseConfig.nextTrigger(cfg, from)!!
    val c = GregorianCalendar().apply { timeInMillis = next }
    assertEquals(Calendar.WEDNESDAY, c.get(Calendar.DAY_OF_WEEK))
    assertEquals(7, c.get(Calendar.HOUR_OF_DAY))
  }

  @Test
  fun `sunrise after the time rolls to the next eligible day`() {
    // Friday 08:00, alarm 07:00 weekdays → next is Monday (skips the weekend).
    val from = GregorianCalendar(2026, Calendar.JUNE, 19, 8, 0) // Fri
    val cfg = SunriseConfig.Config(
        enabled = true, hour = 7, minute = 0, rampMinutes = 20, chime = true,
        days = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
    val c = GregorianCalendar().apply { timeInMillis = SunriseConfig.nextTrigger(cfg, from)!! }
    assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
  }

  @Test
  fun `disabled sunrise has no trigger`() {
    val cfg = SunriseConfig.Config(false, 7, 0, 20, true, emptySet())
    assertEquals(null, SunriseConfig.nextTrigger(cfg))
  }

  // ---- IrishHolidays ----

  @Test
  fun `irish easter and st patricks are detected`() {
    // Easter Sunday 2026 = 5 April.
    val easter = IrishHolidays.easterSunday(2026)
    assertEquals(Calendar.APRIL, easter.get(Calendar.MONTH))
    assertEquals(5, easter.get(Calendar.DAY_OF_MONTH))

    val patrick = GregorianCalendar(2026, Calendar.MARCH, 17)
    assertEquals("St Patrick's Day", IrishHolidays.forToday(patrick))

    val ordinary = GregorianCalendar(2026, Calendar.MARCH, 18)
    assertEquals("", IrishHolidays.forToday(ordinary))
  }

  // ---- StarField ----

  @Test
  fun `star straight overhead reads near the zenith`() {
    // A star whose declination equals the latitude is at the zenith when on the
    // meridian (hour angle 0 → LST == RA).
    val lat = 40.0
    val star = StarField.Star(raHours = 6.0, dec = 40.0, mag = 1.0)
    val altAz = StarField.project(star, lat, lst = 6.0)
    assertTrue("alt should be ~90°, was ${altAz.alt}", altAz.alt > 89.0)
  }

  @Test
  fun `local sidereal time stays in range`() {
    val lst = StarField.localSiderealTime(System.currentTimeMillis(), -6.26)
    assertTrue(lst in 0.0..24.0)
  }

  // ---- PrayerTimes ----

  private fun toMin(hhmm: String): Int {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    return h * 60 + m
  }

  @Test
  fun `prayer times are ordered and dhuhr is near solar noon`() {
    // Mecca, +3, mid-June — all five prayers exist at this latitude.
    val date = GregorianCalendar(2026, Calendar.JUNE, 15, 12, 0)
    val t = PrayerTimes.compute(21.4225, 39.8262, 3.0, date)
    val fajr = toMin(t["Fajr"]!!)
    val sunrise = toMin(t["Sunrise"]!!)
    val dhuhr = toMin(t["Dhuhr"]!!)
    val asr = toMin(t["Asr"]!!)
    val maghrib = toMin(t["Maghrib"]!!)
    val isha = toMin(t["Isha"]!!)
    // Strictly increasing through the day.
    assertTrue("order: $t", fajr < sunrise && sunrise < dhuhr && dhuhr < asr && asr < maghrib && maghrib < isha)
    // Dhuhr is solar noon — around the middle of the day, not some wild value from an
    // un-normalized mean longitude. Mecca (+3) noon lands ~12:20–12:40.
    assertTrue("dhuhr was ${t["Dhuhr"]}", dhuhr in (12 * 60)..(13 * 60))
  }

  // ---- AntiBurnIn ----

  @Test
  fun `anti burn-in offset stays within bounds and moves`() {
    val max = 14f
    val a = AntiBurnIn.offsetX(0L, max)
    val b = AntiBurnIn.offsetX(40_000L, max)
    assertTrue(kotlin.math.abs(a) <= max + 1e-3)
    assertTrue(kotlin.math.abs(b) <= max + 1e-3)
    assertTrue("offset should change over time", a != b)
  }
}
