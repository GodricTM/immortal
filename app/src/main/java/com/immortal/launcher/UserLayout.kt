/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import org.json.JSONObject

/**
 * User-created folder assignments, persisted across restarts.
 *
 * Maps a package id to the folder the user dropped it into (by dragging one app
 * onto another in Manage mode). This overlays the static [Curation.folders]
 * defaults: an entry here wins, so users can both create new folders and move
 * apps out of the curated Settings folder.
 */
object UserLayout {

  private const val PREFS = "immortal_layout"
  private const val KEY = "assignments"

  /** package id -> folder name (user override). */
  fun load(context: Context): Map<String, String> {
    val raw =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyMap()
    return deserialize(raw)
  }

  fun save(context: Context, assignments: Map<String, String>) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, serialize(assignments))
        .apply()
  }

  /** JSON encode of the assignment map (extracted for testing). */
  internal fun serialize(assignments: Map<String, String>): String {
    val obj = JSONObject()
    assignments.forEach { (k, v) -> obj.put(k, v) }
    return obj.toString()
  }

  /** Tolerant decode — malformed/garbage input yields an empty map, never throws. */
  internal fun deserialize(raw: String): Map<String, String> =
      runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { k -> put(k, obj.getString(k)) } }
          }
          .getOrDefault(emptyMap())

  /** A folder name not already used by either the user map or the curated set. */
  fun nextFolderName(existing: Set<String>): String {
    if ("Folder" !in existing) return "Folder"
    var i = 2
    while ("Folder $i" in existing) i++
    return "Folder $i"
  }

  private const val EMPTY_FOLDERS_KEY = "empty_folders"

  fun loadEmptyFolders(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(EMPTY_FOLDERS_KEY, null) ?: return emptySet()
    return deserializeSet(raw)
  }

  fun saveEmptyFolder(context: Context, folderName: String) {
    val existing = loadEmptyFolders(context).toMutableSet()
    existing.add(folderName)
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(EMPTY_FOLDERS_KEY, serializeSet(existing))
        .apply()
  }

  fun removeEmptyFolder(context: Context, folderName: String) {
    val existing = loadEmptyFolders(context).toMutableSet()
    existing.remove(folderName)
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(EMPTY_FOLDERS_KEY, serializeSet(existing))
        .apply()
  }

  private fun serializeSet(set: Set<String>): String {
    val obj = JSONObject()
    obj.put("folders", org.json.JSONArray(set.toList()))
    return obj.toString()
  }

  private fun deserializeSet(raw: String): Set<String> =
      runCatching {
        val obj = JSONObject(raw)
        val arr = obj.getJSONArray("folders")
        buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
      }.getOrDefault(emptySet())
}
