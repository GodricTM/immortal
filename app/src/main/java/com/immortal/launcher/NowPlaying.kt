/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log

/**
 * Reads the currently-playing media (title, artist, album art) from the active media
 * session, for the now-playing screensaver. Works without GMS by going through
 * [MediaSessionManager] with our [NowPlayingListenerService] as the enabled listener.
 * Returns null when nothing is playing or the listener isn't enabled yet.
 */
object NowPlaying {
  private const val TAG = "ImmortalNowPlaying"

  data class Track(
      val title: String,
      val artist: String,
      val art: Bitmap?,
      val isPlaying: Boolean,
  )

  /** Is our notification-listener enabled (required to read media sessions)? */
  fun listenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val me = ComponentName(context, NowPlayingListenerService::class.java).flattenToString()
    return flat.split(":").any { it.equals(me, ignoreCase = true) }
  }

  /** The current track from the most relevant active session, or null. */
  fun current(context: Context): Track? {
    if (!listenerEnabled(context)) return null
    return runCatching {
      val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
      val component = ComponentName(context, NowPlayingListenerService::class.java)
      val sessions = msm.getActiveSessions(component)
      // Prefer a session that is actually playing; otherwise the first one.
      val controller =
          sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
              ?: sessions.firstOrNull()
          ?: return null
      val md = controller.metadata ?: return null
      val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
          ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
      val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
          ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
      val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
          ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
          ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
      val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
      Track(title, artist, art, playing)
    }.onFailure { Log.w(TAG, "current() failed", it) }.getOrNull()
  }
}
