/*
 * Copyright (c) 2026 Starbright Lab.
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
  private const val KEY_ORDER = "home_order"
  private const val KEY_GRID_ORDER = "grid_order"
  private const val KEY_GRID_SLOTS = "grid_slots"

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

  /** User-defined order of ungrouped home-screen app package IDs. */
  fun loadOrder(context: Context): List<String> {
    val raw =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ORDER, null)
            ?: return emptyList()
    return deserializeOrder(raw)
  }

  fun saveOrder(context: Context, order: List<String>) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ORDER, serializeOrder(order))
        .apply()
  }

  /**
   * Unified top-level home-grid order: stable keys for every movable tile — built-ins
   * ("builtin:calls" etc.), widgets ("widget-tile:…"), folders ("folder:…"), and ungrouped apps
   * ("app:…"). Lets the user drag ANY tile, not just apps. Reuses the tolerant order codec.
   */
  fun loadGridOrder(context: Context): List<String> {
    val raw =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_GRID_ORDER, null)
            ?: return emptyList()
    return deserializeOrder(raw)
  }

  fun saveGridOrder(context: Context, order: List<String>) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GRID_ORDER, serializeOrder(order))
        .apply()
  }

  /**
   * Free-placement home grid: a flat list of slots where each entry is a tile key or `null` (a
   * blank cell the user left). Stored as a JSON array with "" standing in for a blank.
   */
  fun loadGridSlots(context: Context): List<String?> {
    val raw =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_GRID_SLOTS, null)
            ?: return emptyList()
    return deserializeSlots(raw)
  }

  fun saveGridSlots(context: Context, slots: List<String?>) {
    context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_GRID_SLOTS, serializeSlots(slots))
        .apply()
  }

  internal fun serializeSlots(slots: List<String?>): String =
      org.json.JSONArray(slots.map { it ?: "" }).toString()

  internal fun deserializeSlots(raw: String): List<String?> =
      runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
              for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                add(if (s.isNullOrBlank()) null else s)
              }
            }
          }
          .getOrDefault(emptyList())

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

  internal fun serializeOrder(order: List<String>): String =
      org.json.JSONArray(order.distinct()).toString()

  internal fun deserializeOrder(raw: String): List<String> =
      runCatching {
            val arr = org.json.JSONArray(raw)
            buildList {
                  for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank() && id !in this) add(id)
                  }
                }
          }
          .getOrDefault(emptyList())

  fun <T> applyOrder(items: List<T>, order: List<String>, idOf: (T) -> String): List<T> {
    if (order.isEmpty()) return items
    val byId = items.associateBy(idOf)
    val ordered = order.mapNotNull { byId[it] }
    val orderedIds = order.toSet()
    val remaining = items.filter { idOf(it) !in orderedIds }
    return ordered + remaining
  }

  fun moveOrder(order: List<String>, source: String, target: String): List<String> {
    if (source == target) return order
    val mutable = order.toMutableList()
    val from = mutable.indexOf(source)
    val to = mutable.indexOf(target)
    if (from < 0 || to < 0) return order
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
  }

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

  // ----- hidden apps (eye-slash in edit mode) -----------------------------------

  private const val HIDDEN_KEY = "hidden_packages"

  fun loadHiddenPackages(context: Context): Set<String> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(HIDDEN_KEY, null) ?: return emptySet()
    return deserializeSet(raw)
  }

  fun hidePackage(context: Context, pkg: String) {
    val updated = loadHiddenPackages(context) + pkg
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(HIDDEN_KEY, serializeSet(updated)).apply()
  }

  fun unhidePackage(context: Context, pkg: String) {
    val updated = loadHiddenPackages(context) - pkg
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(HIDDEN_KEY, serializeSet(updated)).apply()
  }

  fun unhideAllPackages(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().remove(HIDDEN_KEY).apply()
  }

  // ----- launch counts (for the "Most used" sort, permission-free) --------------

  private const val LAUNCH_COUNTS_KEY = "launch_counts"

  /** package id -> number of times launched from Immortal. */
  fun loadLaunchCounts(context: Context): Map<String, Int> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LAUNCH_COUNTS_KEY, null) ?: return emptyMap()
    return runCatching {
      val obj = JSONObject(raw)
      buildMap { obj.keys().forEach { k -> put(k, obj.getInt(k)) } }
    }.getOrDefault(emptyMap())
  }

  /** Increment the launch counter for [pkg]. */
  fun recordLaunch(context: Context, pkg: String) {
    val counts = loadLaunchCounts(context).toMutableMap()
    counts[pkg] = (counts[pkg] ?: 0) + 1
    val obj = JSONObject()
    counts.forEach { (k, v) -> obj.put(k, v) }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(LAUNCH_COUNTS_KEY, obj.toString()).apply()
  }

  // ----- tool-folder membership (which category each built-in tool tile lives in) ---
  // Maps a tool id (see ALL_TOOLS in HomeActivity) -> category id. Overlays the per-tool
  // default category, so the user can move tools between folders via the folder edit mode.

  private const val TOOL_CATEGORIES_KEY = "tool_categories"

  fun loadToolCategories(context: Context): Map<String, String> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(TOOL_CATEGORIES_KEY, null) ?: return emptyMap()
    return deserialize(raw)
  }

  fun setToolCategory(context: Context, toolId: String, category: String) {
    val updated = loadToolCategories(context).toMutableMap().apply { put(toolId, category) }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(TOOL_CATEGORIES_KEY, serialize(updated)).apply()
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
