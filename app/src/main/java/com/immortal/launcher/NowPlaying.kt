/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Intent
import android.graphics.Bitmap

/**
 * Now-playing is sourced from the device's own media session (see
 * [MediaSessionReader]) — whatever app is playing (the Music Assistant companion,
 * Spotify, podcasts, …) — and held by [NowPlayingHub]. That works for everyone with
 * no companion required.
 *
 * The broadcast constants below are the legacy ImmortalCast intent contract; they're
 * no longer consumed by the launcher (the native source replaced them) but are kept
 * for now since ImmortalCast's repo mirrors them.
 */
object NowPlaying {
  const val ACTION_NOW_PLAYING = "com.immortal.launcher.NOW_PLAYING"

  /** The ImmortalCast companion's package — present only if the user installed it. */
  const val COMPANION_PACKAGE = "com.immortalcast"

  const val EXTRA_STATE = "state" // PlaybackState.name
  const val EXTRA_TITLE = "title"
  const val EXTRA_ARTIST = "artist"
  const val EXTRA_ALBUM = "album"
  const val EXTRA_ART_URL = "artUrl" // absolute cover-art URL (Music Assistant on the LAN)
  const val EXTRA_ART = "art" // downscaled JPEG cover-art bytes, pre-fetched by ImmortalCast
  const val EXTRA_DURATION_MS = "durationMs"
  const val EXTRA_POSITION_MS = "positionMs"
  const val EXTRA_GROUP = "group"
  const val EXTRA_SOURCE = "source"
  const val EXTRA_AT_MS = "atMs"
}

enum class PlaybackState {
  PLAYING,
  PAUSED,
  STOPPED,
  IDLE,
}

/** Immutable now-playing snapshot, parsed from the companion's broadcast. */
data class NowPlayingState(
    val state: PlaybackState,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUrl: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val group: String = "",
    val source: String = "",
    val atMs: Long = 0L,
    /** Pre-fetched cover-art JPEG bytes (legacy broadcast path; LAN fetch). */
    val art: ByteArray? = null,
    /**
     * Cover art straight from the device's media session, already downscaled. The
     * native source fills this so the screensaver/header render with no decode hop;
     * [art]/[artUrl] remain the fallbacks for URI-only metadata. In-process only —
     * never put a [Bitmap] in a broadcast / make this Parcelable.
     */
    val artBitmap: Bitmap? = null,
) {
  /** True when there's an actual track to show (playing or paused, with a title). */
  val active: Boolean
    get() = (state == PlaybackState.PLAYING || state == PlaybackState.PAUSED) && title.isNotBlank()

  companion object {
    /** Parse a received broadcast. Tolerant of missing/garbled extras. */
    fun fromIntent(intent: Intent): NowPlayingState =
        NowPlayingState(
            state =
                runCatching {
                      PlaybackState.valueOf(intent.getStringExtra(NowPlaying.EXTRA_STATE) ?: "")
                    }
                    .getOrDefault(PlaybackState.IDLE),
            title = intent.getStringExtra(NowPlaying.EXTRA_TITLE).orEmpty(),
            artist = intent.getStringExtra(NowPlaying.EXTRA_ARTIST).orEmpty(),
            album = intent.getStringExtra(NowPlaying.EXTRA_ALBUM).orEmpty(),
            artUrl = intent.getStringExtra(NowPlaying.EXTRA_ART_URL).orEmpty(),
            durationMs = intent.getLongExtra(NowPlaying.EXTRA_DURATION_MS, 0L),
            positionMs = intent.getLongExtra(NowPlaying.EXTRA_POSITION_MS, 0L),
            group = intent.getStringExtra(NowPlaying.EXTRA_GROUP).orEmpty(),
            source = intent.getStringExtra(NowPlaying.EXTRA_SOURCE).orEmpty(),
            atMs = intent.getLongExtra(NowPlaying.EXTRA_AT_MS, 0L),
            art = intent.getByteArrayExtra(NowPlaying.EXTRA_ART),
        )
  }
}
