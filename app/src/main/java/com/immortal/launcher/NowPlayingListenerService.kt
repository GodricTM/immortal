/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.service.notification.NotificationListenerService

/**
 * A do-nothing notification listener whose only purpose is to be an *enabled*
 * listener: [android.media.session.MediaSessionManager.getActiveSessions] requires
 * the caller to either hold a privileged permission or pass the component of an
 * enabled NotificationListenerService. The user enables this once in
 * Settings → Notifications → Device & app notifications; until then [NowPlaying]
 * simply reports nothing.
 */
class NowPlayingListenerService : NotificationListenerService()
