/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aurora outlook logic — geomagnetic-latitude conversion, the Kp→oval-edge table,
 * and the chance classification — verified offline (no device, no SWPC network).
 */
class AuroraTest {

  @Test
  fun `geomagnetic latitude lifts high-latitude Europe toward the pole`() {
    // Tromsø (69.65N) sits under the auroral oval: its geomagnetic latitude is even
    // higher than its geographic one because it's near the magnetic pole's meridian.
    val tromso = Aurora.geomagLatitude(69.65, 18.96)
    assertTrue("Tromsø geomag should be ~66-67°, was $tromso", tromso in 64.0..69.0)

    // Reykjavík is geomagnetically very high too.
    val reykjavik = Aurora.geomagLatitude(64.13, -21.90)
    assertTrue("Reykjavík geomag should be high, was $reykjavik", reykjavik > 63.0)
  }

  @Test
  fun `southern hemisphere yields negative geomagnetic latitude`() {
    // Hobart, Tasmania — aurora australis territory.
    val hobart = Aurora.geomagLatitude(-42.88, 147.33)
    assertTrue("Hobart geomag should be negative, was $hobart", hobart < 0.0)
  }

  @Test
  fun `boundary marches equatorward as Kp climbs`() {
    assertEquals(66.5, Aurora.boundaryLat(0.0), 1e-9)
    assertEquals(48.1, Aurora.boundaryLat(9.0), 1e-9)
    // Monotonic decrease.
    assertTrue(Aurora.boundaryLat(3.0) > Aurora.boundaryLat(6.0))
    // Fractional interpolation lands between the table rows.
    val mid = Aurora.boundaryLat(2.5)
    assertTrue(mid < Aurora.boundaryLat(2.0) && mid > Aurora.boundaryLat(3.0))
  }

  @Test
  fun `low geomagnetic latitude is never a chance`() {
    // Madrid (40.4N) is ~43° geomagnetic — below even the Kp-9 oval edge (48°), so
    // the aurora can't reach it at any storm strength.
    val s = Aurora.classify(40.4, -3.7, kpNow = 5.0, kpView = 9.0)
    assertEquals(Aurora.Chance.NONE, s.chance)
    assertEquals("N", s.lookToward)
  }

  @Test
  fun `quiet sun leaves mid-latitude with only a slim chance`() {
    // Dublin (~56° geomagnetic) with Kp 1: oval edge ~64.5°, far poleward — but Dublin
    // is above the Kp-9 limit, so it's SLIM (strong-storm-only), not impossible.
    val s = Aurora.classify(53.35, -6.26, kpNow = 1.0, kpView = 1.0)
    assertEquals(Aurora.Chance.SLIM, s.chance)
  }

  @Test
  fun `big storm brings a real chance to mid-latitudes`() {
    // Same Dublin Portal, but a severe Kp 8 storm pushes the oval down to ~50° geomag.
    val s = Aurora.classify(53.35, -6.26, kpNow = 5.0, kpView = 8.0)
    assertTrue(
        "Kp8 should give Dublin a real chance",
        s.chance == Aurora.Chance.POSSIBLE || s.chance == Aurora.Chance.LIKELY)
  }

  @Test
  fun `high latitude sees aurora even on a calm day`() {
    // Tromsø under Kp 2 is already at/over the oval edge.
    val s = Aurora.classify(69.65, 18.96, kpNow = 2.0, kpView = 2.0)
    assertTrue(s.chance == Aurora.Chance.LIKELY || s.chance == Aurora.Chance.POSSIBLE)
  }

  @Test
  fun `kp formatting drops the decimal for whole numbers`() {
    assertEquals("5", Aurora.fmtKp(5.0))
    assertEquals("3.7", Aurora.fmtKp(3.67))
  }
}
