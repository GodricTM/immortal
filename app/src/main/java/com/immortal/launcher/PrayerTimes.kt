/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Daily Islamic prayer times computed on-device for the household that wants them — a
 * calendar pack like the name-day/feast packs. Uses the Muslim World League convention
 * (Fajr 18°, Isha 17°, standard Asr shadow factor) and the device's location + time
 * zone. Pure astronomy; no network, no key.
 */
object PrayerTimes {

  private const val DEG = PI / 180.0
  private const val FAJR_ANGLE = 18.0
  private const val ISHA_ANGLE = 17.0

  /** The five times as "HH:mm" strings for [date], or null if location is unknown. */
  fun forToday(context: Context, date: Calendar = Calendar.getInstance()): Map<String, String>? {
    val (lat, lon) = Weather.coordinates(context) ?: return null
    val tzHours = TimeZone.getDefault().getOffset(date.timeInMillis) / 3_600_000.0
    return compute(lat, lon, tzHours, date)
  }

  /** Today's next (or current) prayer as a header line, e.g. "🕌 Maghrib 21:34". */
  fun nextLine(context: Context, now: Calendar = Calendar.getInstance()): String {
    val times = forToday(context, now) ?: return ""
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val order = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    for (name in order) {
      val t = times[name] ?: continue
      val (h, m) = t.split(":").map { it.toInt() }
      if (h * 60 + m >= nowMin) return "🕌 $name $t"
    }
    // All passed today → first prayer tomorrow.
    return times["Fajr"]?.let { "🕌 Fajr $it (tomorrow)" } ?: ""
  }

  /** Pure computation, exposed for tests. [tzHours] is the UTC offset in hours. */
  fun compute(lat: Double, lon: Double, tzHours: Double, date: Calendar): Map<String, String> {
    val jd = julianDay(date) - lon / 360.0 // approximate; refined per-iteration below
    // Sun declination + equation of time for this day.
    val d = jd - 2451545.0
    val g = (357.529 + 0.98560028 * d) * DEG
    val q = (280.459 + 0.98564736 * d) * DEG
    val l = q + (1.915 * sin(g) + 0.020 * sin(2 * g)) * DEG
    val e = (23.439 - 0.00000036 * d) * DEG
    val decl = kotlin.math.asin(sin(e) * sin(l)) // radians
    val ra = atan2(cos(e) * sin(l), cos(l)) / DEG / 15.0
    val eqt = (q / DEG / 15.0 - fixHours(ra)) // equation of time in hours

    // Solar noon (Dhuhr) in local time.
    val dhuhr = 12.0 + tzHours - lon / 15.0 - eqt

    fun hourAngle(angleDeg: Double): Double {
      val latR = lat * DEG
      val cosH = (-sin(angleDeg * DEG) - sin(latR) * sin(decl)) / (cos(latR) * cos(decl))
      return acos(cosH.coerceIn(-1.0, 1.0)) / DEG / 15.0 // hours
    }

    // Asr: shadow length factor 1 (Shafi'i).
    val latR = lat * DEG
    val asrAngle = -atan2(1.0, 1.0 + tan(abs(latR - decl))) / DEG // altitude of sun at Asr
    val asrHA = run {
      val cosH = (sin(asrAngle * DEG) - sin(latR) * sin(decl)) / (cos(latR) * cos(decl))
      acos(cosH.coerceIn(-1.0, 1.0)) / DEG / 15.0
    }

    val sunrise = dhuhr - hourAngle(0.833)
    val sunset = dhuhr + hourAngle(0.833)
    val fajr = dhuhr - hourAngle(FAJR_ANGLE)
    val asr = dhuhr + asrHA
    val isha = dhuhr + hourAngle(ISHA_ANGLE)

    return linkedMapOf(
        "Fajr" to fmt(fajr),
        "Sunrise" to fmt(sunrise),
        "Dhuhr" to fmt(dhuhr),
        "Asr" to fmt(asr),
        "Maghrib" to fmt(sunset),
        "Isha" to fmt(isha),
    )
  }

  private fun fixHours(h: Double): Double {
    var x = h % 24.0
    if (x < 0) x += 24.0
    return x
  }

  private fun fmt(hours: Double): String {
    val h = fixHours(hours)
    var hh = h.toInt()
    var mm = ((h - hh) * 60).toInt()
    if (mm >= 60) { mm -= 60; hh = (hh + 1) % 24 }
    return "%02d:%02d".format(hh, mm)
  }

  private fun julianDay(c: Calendar): Double {
    val year = c.get(Calendar.YEAR)
    val month = c.get(Calendar.MONTH) + 1
    val day = c.get(Calendar.DAY_OF_MONTH)
    val a = (14 - month) / 12
    val y = year + 4800 - a
    val m = month + 12 * a - 3
    val jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    return jdn - 0.5
  }
}
