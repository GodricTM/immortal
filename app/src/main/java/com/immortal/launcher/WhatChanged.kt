/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray

/**
 * "What changed" — a device that improves itself silently shouldn't be a black box.
 * Reads the launcher's own GitHub releases (keyless) so the user can see what each
 * self-update brought: version, date, and the release notes.
 *
 * Repo is the same one [UpdateManager] self-updates from; kept here as a single source
 * so the changelog and the updater never disagree about where releases live.
 */
object WhatChanged {

  // Public releases feed for the launcher (keyless, 60 req/h/IP unauthenticated).
  private const val RELEASES_URL =
      "https://api.github.com/repos/starbrightlab/immortal/releases?per_page=15"

  data class Release(val version: String, val date: String, val notes: String)

  /** Recent releases, newest first (empty on failure / offline). */
  fun recent(): List<Release> =
      runCatching {
            val arr = JSONArray(httpGet(RELEASES_URL))
            (0 until arr.length()).map { i ->
              val o = arr.getJSONObject(i)
              Release(
                  version = o.optString("tag_name").ifBlank { o.optString("name") },
                  date = prettyDate(o.optString("published_at")),
                  notes = o.optString("body").trim().ifBlank { "No notes." },
              )
            }
          }
          .getOrDefault(emptyList())

  /** "2026-06-15T10:30:00Z" -> "15 Jun 2026" (or the raw string on parse failure). */
  private fun prettyDate(iso: String): String =
      runCatching {
            val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            parse.timeZone = TimeZone.getTimeZone("UTC")
            val out = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            out.format(parse.parse(iso)!!)
          }
          .getOrDefault(iso.take(10))

  private fun httpGet(spec: String): String {
    val c = URL(spec).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 10000
    c.setRequestProperty("User-Agent", "Immortal/1.0")
    c.setRequestProperty("Accept", "application/vnd.github+json")
    return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
  }
}
