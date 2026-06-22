/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.admin.DeviceAdminReceiver

/**
 * Minimal device-admin receiver. A normal app can't turn the Portal's screen off
 * (PowerManager.goToSleep is a signature permission), so Immortal registers as a
 * device admin with only the FORCE_LOCK policy and uses
 * DevicePolicyManager.lockNow() for its idle and overnight screen-off features.
 *
 * Provisioning activates it with `dpm set-active-admin com.immortal.launcher/.AdminReceiver`;
 * if it isn't active, [ScreenControl.sleep] is a harmless no-op.
 */
class AdminReceiver : DeviceAdminReceiver()
