/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Small icon loader for the App Store: memory LRU + disk cache under
 * cacheDir/store_icons, decoded at ~128px. Catalog entries without an iconUrl
 * (some community submissions) simply get null — the UI draws a monogram tile.
 */
object StoreIcons {
  private val mem = LruCache<String, Bitmap>(64)
  private val io = Executors.newFixedThreadPool(3)
  private val main by lazy { Handler(Looper.getMainLooper()) }

  fun cached(pkg: String): Bitmap? = mem.get(pkg)

  fun load(context: Context, app: CatalogApp, onLoaded: (Bitmap?) -> Unit) {
    val url = app.iconUrl
    if (url == null) {
      onLoaded(null)
      return
    }
    mem.get(app.packageName)?.let {
      onLoaded(it)
      return
    }
    val appCtx = context.applicationContext
    io.execute {
      val f = File(File(appCtx.cacheDir, "store_icons"), "${app.packageName}.png")
      f.parentFile?.mkdirs()
      val bmp =
          runCatching {
                if (!f.exists() || f.length() == 0L) {
                  val c = URL(url).openConnection() as HttpURLConnection
                  c.connectTimeout = 8000
                  c.readTimeout = 15000
                  c.instanceFollowRedirects = true
                  c.setRequestProperty("User-Agent", "PortalStore/1.0")
                  c.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
                }
                decodeAt(f, 128)
              }
              .getOrNull()
      if (bmp != null) mem.put(app.packageName, bmp) else runCatching { f.delete() }
      main.post { onLoaded(bmp) }
    }
  }

  /** Decode [f] downsampled to roughly [px] on its longest side. */
  private fun decodeAt(f: File, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    return BitmapFactory.decodeFile(
        f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
  }
}
