/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.immortal.launcher.settings.SettingsDomains
import com.immortal.launcher.ui.theme.SampleAppTheme
import org.json.JSONObject

/**
 * Dedicated settings screen for the digital clock. Opened from a "Clock" tile
 * in the launcher's Settings folder. The clock-specific preferences (enable, style,
 * color, font, size, layout, background, glow, show date, show seconds) render from
 * the `digitalclock` registry domain — the same specs the phone remote uses. The
 * screensaver-activation toggles route through the `immortal` domain so its `onApplied`
 * fires. The back-gesture section (accessibility service, overlay permission, test) is
 * genuinely bespoke system-action UI the registry can't model, so it stays hand-built.
 */
class ClockSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SampleAppTheme(darkTheme = true) { ClockSettingsScreen() } }
  }
}

@Composable
private fun ClockSettingsScreen() {
  val context = LocalContext.current
  var clockConfig by remember { mutableStateOf(DigitalClockConfig.load(context)) }
  var settings by remember { mutableStateOf(ImmortalSettings.load(context)) }

  // Re-read on resume so a change in another screen is reflected here.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) {
        clockConfig = DigitalClockConfig.load(context)
        settings = ImmortalSettings.load(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  val activity = context as? Activity
  val firstFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .onPreviewKeyEvent { e ->
                  if (e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyUp) activity?.finish()
                    true
                  } else false
                }
                .background(Color(0xFF101012))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
      Column(modifier = Modifier.widthIn(max = 1100.dp).focusRequester(firstFocus).focusGroup()) {
        Text("Clock", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose the digital clock and how it looks on the screensaver.",
            color = Color(0xFF9A9A9A),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.size(26.dp))

        // Clock preferences render from the `digitalclock` registry domain. Apply routes through
        // the domain so its onApplied (reaffirm the dream when `enabled` toggles) fires here too.
        SettingsList(SettingsDomains.digitalclock, clockConfig) { k, v ->
          SettingsDomains.digitalclock.apply(context, JSONObject().put(k, v))
          clockConfig = DigitalClockConfig.load(context)
        }

        // Preview button — opens fullscreen preview (a bespoke action, not a setting).
        SectionLabel("Preview")
        Card {
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp).clickable {
                context.startActivity(
                    Intent(context, DigitalClockPreviewActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              },
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Preview", color = Color.White, fontSize = 17.sp)
              Text(
                  "See the clock full-screen with your current settings.",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                  context.startActivity(
                      Intent(context, DigitalClockPreviewActivity::class.java)
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ) {
              Text(
                  "Preview",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
              )
            }
          }
        }

        // Screensaver activation — these are `immortal` domain specs routed through the registry
        // so the domain's onApplied fires (the on-device screen and the remote share the same path).
        Spacer(Modifier.size(26.dp))
        SectionLabel("Screensaver activation")
        Card {
          ActivationToggleRow(
              "Activate on sleep",
              "Show the screensaver when the screen turns off. Turn off to let the Portal sleep normally.",
              settings.activateOnSleep) {
            SettingsDomains.immortal.apply(context, JSONObject().put("activateOnSleep", it))
            settings = ImmortalSettings.load(context)
          }
          Divider()
          ActivationToggleRow(
              "Activate on dock",
              "Show the screensaver when the Portal is docked/plugged in.",
              settings.activateOnDock) {
            SettingsDomains.immortal.apply(context, JSONObject().put("activateOnDock", it))
            settings = ImmortalSettings.load(context)
          }
        }

        // Back gesture — genuinely bespoke system-action UI (accessibility, overlay permission).
        Spacer(Modifier.size(26.dp))
        SectionLabel("Back gesture")
        Card {
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Immortal Back service", color = Color.White, fontSize = 17.sp)
              Text(
                  "Required for the right-edge back gesture. This replaces the old on-screen back buttons on Immortal pages.",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                  if (BackHelper.isBackServiceEnabled(context)) {
                    Toast.makeText(context, "Immortal Back is already enabled", Toast.LENGTH_SHORT).show()
                  } else {
                    val resolver = context.contentResolver
                    val current = android.provider.Settings.Secure.getString(
                        resolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    val service = "${context.packageName}/${ImmortalBackGestureService::class.java.name}"
                    val services = current.split(':').filter { it.isNotBlank() && it != service }
                    val newValue = (services + service).joinToString(":")
                    android.provider.Settings.Secure.putString(
                        resolver,
                        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        newValue)
                    android.provider.Settings.Secure.putInt(
                        resolver,
                        android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                        1)
                    Toast.makeText(context, "Enabled Immortal Back. Turn on the swipe gesture below to use it.", Toast.LENGTH_LONG).show()
                  }
                },
            ) {
              Text(
                  if (BackHelper.isBackServiceEnabled(context)) "Enabled" else "Enable",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
              )
            }
          }
          Divider()
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Test back action", color = Color.White, fontSize = 17.sp)
              Text(
                  "Sends a BACK broadcast to the accessibility service. If it does not navigate back, Immortal Back is enabled but Android is not allowing this service to perform global BACK here.",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                  if (BackHelper.isBackServiceEnabled(context)) {
                    context.sendBroadcast(
                        Intent(ImmortalBackGestureService.ACTION_BACK)
                            .setPackage(context.packageName))
                    Toast.makeText(context, "Back action sent", Toast.LENGTH_SHORT).show()
                  } else {
                    Toast.makeText(
                        context,
                        "Enable Immortal Back first so system-wide BACK works",
                        Toast.LENGTH_LONG).show()
                  }
                },
            ) {
              Text(
                  "Test",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
              )
            }
          }
          Divider()
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Right-edge back gesture", color = Color.White, fontSize = 17.sp)
              Text(
                  "Enable or disable the gesture here. When enabled, swipe left from the right edge in any app to go back.",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Switch(
                checked = isOverlayRunning(context),
                onCheckedChange = { enabled ->
                  if (enabled) {
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                      Toast.makeText(
                          context,
                          "Please grant \"Draw over other apps\" first",
                          Toast.LENGTH_LONG).show()
                      val intent = Intent(
                          android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                          android.net.Uri.parse("package:${context.packageName}"))
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      runCatching { context.startActivity(intent) }
                    } else {
                      startSystemBackGesture(context)
                      Toast.makeText(context, "Swipe-from-right back enabled", Toast.LENGTH_SHORT).show()
                    }
                  } else {
                    stopSystemBackGesture(context)
                    Toast.makeText(context, "Swipe-from-right back disabled", Toast.LENGTH_SHORT).show()
                  }
                },
            )
          }
          if (!android.provider.Settings.canDrawOverlays(context)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  "Grant \"Draw over other apps\" to enable the swipe gesture.",
                  color = Color(0xFFE53935),
                  fontSize = 13.sp,
                  modifier = Modifier.weight(1f),
              )
              Surface(
                  color = MaterialTheme.colorScheme.primary,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                  },
              ) {
                Text(
                    "Grant",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
              }
            }
          }
        }

        Text(
            "Changes apply immediately. Tap the clock icon on the home screen to start the screensaver.",
            color = Color(0xFF7C7C7C),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )
      }
    }
    FolderBackButton(onClick = { activity?.finish() })
  }
}

@Composable
private fun ActivationToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth().tvFocusableRow { onChange(!checked) }
              .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, color = Color.White, fontSize = 17.sp)
      Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

private fun isOverlayRunning(context: android.content.Context): Boolean =
    runCatching {
      val am = context.getSystemService(android.app.ActivityManager::class.java)
      am.getRunningServices(Int.MAX_VALUE).any { it.service.className == SystemBackGestureService::class.java.name }
    }.getOrDefault(false)

private fun startSystemBackGesture(context: android.content.Context) {
  runCatching {
    val intent = Intent(context, SystemBackGestureService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(intent)
    } else {
      context.startService(intent)
    }
  }
}

private fun stopSystemBackGesture(context: android.content.Context) {
  runCatching { context.stopService(Intent(context, SystemBackGestureService::class.java)) }
}
