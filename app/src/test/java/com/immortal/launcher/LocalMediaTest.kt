/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaTest {

  @Test
  fun summarize_countsRecursivelyWithSamples() {
    val tmp = File(System.getProperty("java.io.tmpdir"), "imm-sum-${System.nanoTime()}")
    try {
      File(tmp, "sub").mkdirs()
      File(tmp, "a.jpg").writeText("x")
      File(tmp, "b.png").writeText("x")
      File(tmp, "notes.txt").writeText("x")
      File(tmp, "sub/d.mp4").writeText("x")
      File(tmp, "sub/e.jpeg").writeText("x")
      val s = LocalMedia.summarize(tmp.absolutePath)
      assertEquals(3, s.photos) // a.jpg, b.png, e.jpeg
      assertEquals(1, s.videos) // d.mp4
      assertEquals(4, s.total)
      assertFalse(s.capped)
      assertTrue(s.samples.isNotEmpty())
    } finally {
      tmp.deleteRecursively()
    }
  }

  @Test
  fun classify_byExtension_caseInsensitive() {
    assertEquals(LocalMedia.Kind.IMAGE, LocalMedia.classify("beach.jpg"))
    assertEquals(LocalMedia.Kind.IMAGE, LocalMedia.classify("SUNSET.JPEG"))
    assertEquals(LocalMedia.Kind.IMAGE, LocalMedia.classify("pic.png"))
    assertEquals(LocalMedia.Kind.VIDEO, LocalMedia.classify("clip.mp4"))
    assertEquals(LocalMedia.Kind.VIDEO, LocalMedia.classify("home.MOV"))
    assertEquals(LocalMedia.Kind.OTHER, LocalMedia.classify("notes.pdf"))
    assertEquals(LocalMedia.Kind.OTHER, LocalMedia.classify("README"))
  }

  @Test
  fun displayName_takesTheLeafFolder() {
    assertEquals("Trip", LocalMedia.displayName("/storage/emulated/0/DCIM/Trip"))
    assertEquals("Trip", LocalMedia.displayName("/storage/emulated/0/DCIM/Trip/"))
    assertEquals("0", LocalMedia.displayName("/storage/emulated/0"))
    assertEquals("1A2B-3C4D", LocalMedia.displayName("/storage/1A2B-3C4D"))
  }
}
