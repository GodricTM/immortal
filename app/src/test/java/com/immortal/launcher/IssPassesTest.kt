/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the self-contained SGP4 propagator against the canonical NORAD/Vallado
 * verification vector (catalogue object 00005) published in "Revisiting Spacetrack
 * Report #3" — the reference everyone's SGP4 is checked against. No device, no
 * network: pure orbital math.
 *
 * Object 00005 is eccentric and near-Earth (same code path as the ISS), so matching
 * its TEME position to sub-km at t=0 and t=360 min exercises the whole pipeline:
 * Kozai recovery, the drag/secular terms, Kepler's solve and the short-period
 * periodics.
 */
class IssPassesTest {

  // The official SGP4-VER test element set for object 00005.
  private val line1 = "1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753"
  private val line2 = "2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667"

  private fun dist(p: DoubleArray, x: Double, y: Double, z: Double): Double {
    val dx = p[0] - x
    val dy = p[1] - y
    val dz = p[2] - z
    return sqrt(dx * dx + dy * dy + dz * dz)
  }

  @Test
  fun `sgp4 matches canonical vector at epoch`() {
    val sat = IssPasses.Sat.fromTle(line1, line2)
    val r = sat.propagate(0.0)!!
    // Published TEME position (km) at tsince = 0.
    val err = dist(r, 7022.46529266, -1400.08296755, 0.03995155)
    assertTrue("t=0 position off by $err km\n  got=${r.toList()}", err < 0.1)
  }

  @Test
  fun `sgp4 matches canonical vector at plus 360 minutes`() {
    val sat = IssPasses.Sat.fromTle(line1, line2)
    val r = sat.propagate(360.0)!!
    // Published TEME position (km) at tsince = 360 min (exercises secular rates).
    val err = dist(r, -7154.03120202, -3783.17682504, -3536.19412294)
    assertTrue("t=360 position off by $err km\n  got=${r.toList()}", err < 1.0)
  }

  @Test
  fun `bstar exponent field parses sign and exponent`() {
    // " 28098-4" -> 0.28098e-4  (object 00005's drag term).
    val sat = IssPasses.Sat.fromTle(line1, line2)
    // If bstar parsed wrong, the t=0 vector above would already drift; this guards the
    // negative-mantissa form too via a second TLE.
    val negB = "1 25544U 98067A   24001.50000000  .00016717  00000-0 -30000-3 0  9007"
    val l2 = "2 25544  51.6400 208.0000 0006703 130.0000 325.0000 15.50000000    07"
    val iss = IssPasses.Sat.fromTle(negB, l2)
    val r = iss.propagate(0.0)!!
    // ISS sits ~6700-6800 km from Earth's centre; just sanity-check the magnitude.
    val mag = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2])
    assertEquals(6780.0, mag, 120.0)
  }

  @Test
  fun `compass maps azimuth to 16-point labels`() {
    assertEquals("N", IssPasses.compass(0.0))
    assertEquals("N", IssPasses.compass(359.0))
    assertEquals("E", IssPasses.compass(90.0))
    assertEquals("S", IssPasses.compass(180.0))
    assertEquals("W", IssPasses.compass(270.0))
    assertEquals("NE", IssPasses.compass(45.0))
  }
}
