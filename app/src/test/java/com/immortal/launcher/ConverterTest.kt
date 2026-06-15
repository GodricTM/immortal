/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** Offline unit-conversion math (currency needs the network and isn't covered here). */
class ConverterTest {

  @Test
  fun `length round-trips through the base unit`() {
    assertEquals(1609.344, Converter.convert("Length", "mi", "m", 1.0), 1e-6)
    assertEquals(1.0, Converter.convert("Length", "m", "mi", 1609.344), 1e-9)
    assertEquals(12.0, Converter.convert("Length", "ft", "in", 1.0), 1e-9)
    assertEquals(100.0, Converter.convert("Length", "m", "cm", 1.0), 1e-9)
  }

  @Test
  fun `mass converts kg to pounds`() {
    assertEquals(2.20462262, Converter.convert("Mass", "kg", "lb", 1.0), 1e-6)
    assertEquals(16.0, Converter.convert("Mass", "lb", "oz", 1.0), 1e-6)
  }

  @Test
  fun `temperature is affine, not a factor`() {
    assertEquals(32.0, Converter.convertTemp("°C", "°F", 0.0), 1e-9)
    assertEquals(212.0, Converter.convertTemp("°C", "°F", 100.0), 1e-9)
    assertEquals(0.0, Converter.convertTemp("°F", "°C", 32.0), 1e-9)
    assertEquals(273.15, Converter.convertTemp("°C", "K", 0.0), 1e-9)
    assertEquals(37.0, Converter.convertTemp("°F", "°C", 98.6), 1e-9)
  }

  @Test
  fun `speed converts kmh to mph`() {
    assertEquals(62.137119, Converter.convert("Speed", "km/h", "mph", 100.0), 1e-4)
  }

  @Test
  fun `same unit is identity`() {
    assertEquals(5.0, Converter.convert("Volume", "L", "L", 5.0), 1e-9)
  }
}
