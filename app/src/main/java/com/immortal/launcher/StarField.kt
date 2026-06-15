/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The real night sky behind the home grid after dark. A compact catalogue of the
 * brightest stars (RA/Dec/magnitude) is projected to the device's local horizon using
 * the observer's latitude/longitude and the current time, so the constellations on
 * screen are the ones actually overhead. A few familiar asterisms are joined with lines.
 *
 * All math is local and pure ([project] is unit-tested); no catalogue download, no
 * network. Accuracy is "looks like the sky," not observatory-grade.
 */
object StarField {

  /** A catalogue star. [raHours] 0–24, [dec] degrees, [mag] visual magnitude (lower =
   * brighter), [name] for the handful that get labelled. */
  data class Star(val raHours: Double, val dec: Double, val mag: Double, val name: String = "")

  /** Where a star lands: [az] degrees from north, [alt] degrees above the horizon. */
  data class AltAz(val az: Double, val alt: Double)

  private const val DEG = PI / 180.0

  /**
   * ~60 of the brightest stars, enough to read the sky. RA in hours, Dec in degrees
   * (J2000). Named ones anchor the asterisms in [LINES].
   */
  val STARS =
      listOf(
          // Orion
          Star(5.2423, -8.2017, 0.18, "Rigel"), // 0
          Star(5.9195, 7.4071, 0.45, "Betelgeuse"), // 1
          Star(5.6035, -1.2019, 1.69, "Alnilam"), // 2 (belt centre)
          Star(5.5334, -0.2991, 2.23, "Mintaka"), // 3 (belt)
          Star(5.6793, -1.9426, 1.74, "Alnitak"), // 4 (belt)
          Star(5.4188, 6.3497, 1.64, "Bellatrix"), // 5
          Star(5.7959, -9.6696, 2.07, "Saiph"), // 6
          // Big Dipper (Ursa Major)
          Star(11.0621, 61.7510, 1.81, "Dubhe"), // 7
          Star(11.0307, 56.3824, 2.34, "Merak"), // 8
          Star(11.8972, 53.6948, 2.41, "Phecda"), // 9
          Star(12.2571, 57.0326, 3.31, "Megrez"), // 10
          Star(12.9005, 55.9598, 1.76, "Alioth"), // 11
          Star(13.3987, 54.9254, 2.23, "Mizar"), // 12
          Star(13.7923, 49.3133, 1.85, "Alkaid"), // 13
          // Cassiopeia
          Star(0.6751, 56.5373, 2.24, "Caph"), // 14
          Star(0.9451, 60.7167, 2.24, "Schedar"), // 15
          Star(1.4302, 60.2353, 2.47, "Gamma Cas"), // 16
          Star(1.9066, 63.6701, 2.68, "Ruchbah"), // 17
          Star(1.4307, 59.1498, 3.35, "Segin"), // 18
          // Bright scattered stars for context
          Star(6.7525, -16.7161, -1.46, "Sirius"),
          Star(7.6550, 5.2250, 0.34, "Procyon"),
          Star(7.5766, 31.8884, 1.14, "Pollux"),
          Star(7.4763, 28.0262, 1.58, "Castor"),
          Star(4.5987, 16.5093, 0.87, "Aldebaran"),
          Star(5.2782, 45.9980, 0.08, "Capella"),
          Star(14.2610, 19.1824, -0.05, "Arcturus"),
          Star(18.6156, 38.7837, 0.03, "Vega"),
          Star(19.8464, 8.8683, 0.77, "Altair"),
          Star(20.6905, 45.2803, 1.25, "Deneb"),
          Star(22.9608, -29.6222, 1.17, "Fomalhaut"),
          Star(13.4199, -11.1614, 0.98, "Spica"),
          Star(16.4901, -26.4320, 1.06, "Antares"),
          Star(10.1395, 11.9672, 1.35, "Regulus"),
          Star(1.6286, -57.2367, 0.45, "Achernar"),
          Star(0.1398, 29.0904, 2.07, "Alpheratz"),
          Star(3.4054, 49.8612, 1.79, "Mirfak"),
          Star(2.1191, 23.4624, 2.01, "Hamal"),
          Star(9.4597, -8.6586, 1.99, "Alphard"),
          Star(17.5822, 12.5600, 2.08, "Rasalhague"),
          Star(21.7364, 9.8750, 2.38, "Enif"),
          Star(23.0629, 28.0828, 2.42, "Scheat"),
          Star(23.0793, 15.2053, 2.49, "Markab"),
      )

  /** Index pairs joined into recognizable asterism lines. */
  val LINES =
      listOf(
          // Orion: belt + shoulders/knees
          3 to 2, 2 to 4, 1 to 5, 5 to 3, 4 to 6, 6 to 0, 0 to 3, 1 to 4,
          // Big Dipper
          7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12, 12 to 13, 10 to 7,
          // Cassiopeia "W"
          14 to 15, 15 to 16, 16 to 17, 17 to 18,
      )

  /**
   * Project a star to local horizon coordinates. [lst] is Local Sidereal Time in hours.
   * Pure + unit-tested.
   */
  fun project(star: Star, latDeg: Double, lst: Double): AltAz {
    val ha = ((lst - star.raHours) * 15.0) * DEG // hour angle in radians
    val dec = star.dec * DEG
    val lat = latDeg * DEG
    val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(ha)
    val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
    val cosA = (sin(dec) - sin(alt) * sin(lat)) / (cos(alt) * cos(lat))
    val a = acosSafe(cosA)
    val az = if (sin(ha) > 0) 2 * PI - a else a
    return AltAz(az / DEG, alt / DEG)
  }

  private fun acosSafe(x: Double): Double = kotlin.math.acos(x.coerceIn(-1.0, 1.0))

  /** Local Sidereal Time (hours, 0–24) for [lonDeg] at epoch [millis]. */
  fun localSiderealTime(millis: Long, lonDeg: Double): Double {
    val jd = 2440587.5 + millis / 86_400_000.0
    val d = jd - 2451545.0
    // Greenwich mean sidereal time in degrees (IAU approx), then add longitude.
    var gmst = 280.46061837 + 360.98564736629 * d
    gmst %= 360.0
    if (gmst < 0) gmst += 360.0
    var lst = (gmst + lonDeg) / 15.0
    lst %= 24.0
    if (lst < 0) lst += 24.0
    return lst
  }

  /** Approximate sun altitude (deg) — used to decide if it's dark enough for stars. */
  fun sunAltitude(millis: Long, latDeg: Double, lonDeg: Double): Double {
    val jd = 2440587.5 + millis / 86_400_000.0
    val n = jd - 2451545.0
    val l = (280.460 + 0.9856474 * n) * DEG
    val g = (357.528 + 0.9856003 * n) * DEG
    val lambda = l + (1.915 * sin(g) + 0.020 * sin(2 * g)) * DEG
    val eps = (23.439 - 0.0000004 * n) * DEG
    val ra = atan2(cos(eps) * sin(lambda), cos(lambda)) / DEG / 15.0 // hours
    val dec = asin(sin(eps) * sin(lambda)) / DEG
    return project(Star(ra, dec, 0.0), latDeg, localSiderealTime(millis, lonDeg)).alt
  }
}
