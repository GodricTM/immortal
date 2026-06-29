/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import java.io.File

/**
 * The home-screen "fridge notes": one typed sticky note and one recorded voice memo
 * that sit on the launcher for whoever walks up next. Contact-free and local — the
 * text lives in SharedPrefs, the audio in a single file in [audioFile].
 */
object NotesConfig {

  private const val PREFS = "immortal_notes"
  private const val KEY_TEXT = "text_note"

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun loadText(context: Context): String = prefs(context).getString(KEY_TEXT, "") ?: ""

  fun saveText(context: Context, text: String) =
      prefs(context).edit().putString(KEY_TEXT, text).apply()

  fun clearText(context: Context) = prefs(context).edit().remove(KEY_TEXT).apply()

  /** Single voice-memo file. */
  fun audioFile(context: Context): File = File(context.filesDir, "leave_note.m4a")

  fun hasAudioNote(context: Context): Boolean = audioFile(context).let { it.exists() && it.length() > 0 }

  fun clearAudio(context: Context) {
    runCatching { audioFile(context).delete() }
  }
}
