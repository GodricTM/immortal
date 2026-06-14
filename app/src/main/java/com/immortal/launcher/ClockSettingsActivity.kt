/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.immortal.launcher.ui.theme.SampleAppTheme

/**
 * Dedicated settings screen for the digital clock. Opened from a "Clock" tile
 * in the launcher's Settings folder. All clock-related preferences live here:
 * enable, style, color, font, size, layout, background, glow, show date,
 * show seconds, and the screensaver-activation toggles.
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
  var settings by remember { mutableStateOf(ImmortalSettings.load(context)) }
  var clockConfig by remember { mutableStateOf(DigitalClockConfig.load(context)) }

  // Re-read on resume so a change in another screen (e.g. toggle from the
  // home screen clock icon) is reflected here.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) {
        settings = ImmortalSettings.load(context)
        clockConfig = DigitalClockConfig.load(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  // Remote support: focus the first control on open; Back exits the screen.
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

      // Enable toggle
      SectionLabel("Enable")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Digital clock", color = Color.White, fontSize = 17.sp)
            Text(
                "Use a large clock instead of the photo frame screensaver.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (clockConfig.enabled) "on" else "off",
              onSelect = {
                val enabled = it == "on"
                DigitalClockConfig.setEnabled(context, enabled)
                clockConfig = DigitalClockConfig.load(context)
                SettingsGuard.reaffirmScreensaver(context)
              },
          )
        }
        Divider()
        // Preview button — opens fullscreen preview
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp).clickable {
              context.startActivity(
                  Intent(context, DigitalClockPreviewActivity::class.java)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              )
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
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
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

      // Style picker
      Spacer(Modifier.size(26.dp))
      SectionLabel("Clock style")
      Card {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
              "Choose how the clock looks.",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(bottom = 10.dp),
          )
          Segmented(
              options =
                  listOf(
                      "Classic" to DigitalClockConfig.STYLE_CLASSIC,
                      "Flip" to DigitalClockConfig.STYLE_FLIP,
                      "Bold" to DigitalClockConfig.STYLE_BOLD,
                  ),
              selected = clockConfig.style,
              onSelect = {
                DigitalClockConfig.setStyle(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.size(8.dp))
          Segmented(
              options =
                  listOf(
                      "Neon" to DigitalClockConfig.STYLE_NEON,
                      "Segment" to DigitalClockConfig.STYLE_SEGMENT,
                      "Analog" to DigitalClockConfig.STYLE_ANALOG,
                  ),
              selected = clockConfig.style,
              onSelect = {
                DigitalClockConfig.setStyle(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
              modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      // Color, font, size
      Spacer(Modifier.size(26.dp))
      SectionLabel("Appearance")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Color", color = Color.White, fontSize = 17.sp)
          }
          Segmented(
              options =
                  listOf(
                      "White" to DigitalClockConfig.COLOR_WHITE,
                      "Red" to DigitalClockConfig.COLOR_RED,
                      "Green" to DigitalClockConfig.COLOR_GREEN,
                      "Blue" to DigitalClockConfig.COLOR_BLUE,
                      "Yellow" to DigitalClockConfig.COLOR_YELLOW,
                  ),
              selected = clockConfig.color,
              onSelect = {
                DigitalClockConfig.setColor(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Font", color = Color.White, fontSize = 17.sp)
          }
          Segmented(
              options =
                  listOf(
                      "Light" to DigitalClockConfig.FONT_LIGHT,
                      "Normal" to DigitalClockConfig.FONT_NORMAL,
                      "Bold" to DigitalClockConfig.FONT_BOLD,
                      "Mono" to DigitalClockConfig.FONT_MONO,
                      "Serif" to DigitalClockConfig.FONT_SERIF,
                  ),
              selected = clockConfig.font,
              onSelect = {
                DigitalClockConfig.setFont(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "Bundled fonts — drop the .ttf into app/src/main/assets/fonts/ to enable (falls back to the default font if missing).",
            color = Color(0xFF7C7C7C),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
          Segmented(
              options =
                  listOf(
                      "LED" to DigitalClockConfig.FONT_SEGMENT_LED,
                      "Digital" to DigitalClockConfig.FONT_DIGITAL_7,
                      "Tech" to DigitalClockConfig.FONT_TECHNOLOGY,
                  ),
              selected = clockConfig.font,
              onSelect = {
                DigitalClockConfig.setFont(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
              modifier = Modifier.fillMaxWidth(),
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Size", color = Color.White, fontSize = 17.sp)
          }
          Segmented(
              options =
                  listOf(
                      "Small" to DigitalClockConfig.SIZE_SMALL,
                      "Medium" to DigitalClockConfig.SIZE_MEDIUM,
                      "Large" to DigitalClockConfig.SIZE_LARGE,
                      "XL" to DigitalClockConfig.SIZE_XL,
                  ),
              selected = clockConfig.size,
              onSelect = {
                DigitalClockConfig.setSize(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
      }

      // Layout, background, glow
      Spacer(Modifier.size(26.dp))
      SectionLabel("Layout & background")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Position", color = Color.White, fontSize = 17.sp)
            Text(
                "Where the clock sits on screen.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Center" to DigitalClockConfig.LAYOUT_CENTER,
                      "Top" to DigitalClockConfig.LAYOUT_TOP,
                      "Bottom" to DigitalClockConfig.LAYOUT_BOTTOM,
                      "Minimal" to DigitalClockConfig.LAYOUT_MINIMAL,
                  ),
              selected = clockConfig.layout,
              onSelect = {
                DigitalClockConfig.setLayout(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Background", color = Color.White, fontSize = 17.sp)
          }
          Segmented(
              options =
                  listOf(
                      "Black" to DigitalClockConfig.BG_BLACK,
                      "Gradient" to DigitalClockConfig.BG_GRADIENT,
                      "Red" to DigitalClockConfig.BG_RED,
                  ),
              selected = clockConfig.background,
              onSelect = {
                DigitalClockConfig.setBackground(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Glow", color = Color.White, fontSize = 17.sp)
            Text(
                "Text shadow / glow effect (matches the selected color).",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "None" to DigitalClockConfig.GLOW_NONE,
                      "Soft" to DigitalClockConfig.GLOW_SOFT,
                      "Strong" to DigitalClockConfig.GLOW_STRONG,
                  ),
              selected = clockConfig.glow,
              onSelect = {
                DigitalClockConfig.setGlow(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
      }

      // Show date, show seconds
      Spacer(Modifier.size(26.dp))
      SectionLabel("Extras")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Show date", color = Color.White, fontSize = 17.sp)
            Text(
                "Display the date below the clock.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Switch(
              checked = clockConfig.showDate,
              onCheckedChange = {
                DigitalClockConfig.setShowDate(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Show seconds", color = Color.White, fontSize = 17.sp)
            Text(
                "Display seconds in the clock.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Switch(
              checked = clockConfig.showSeconds,
              onCheckedChange = {
                DigitalClockConfig.setShowSeconds(context, it)
                clockConfig = DigitalClockConfig.load(context)
              },
          )
        }
      }

      // Screensaver activation
      Spacer(Modifier.size(26.dp))
      SectionLabel("Screensaver activation")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Activate on sleep", color = Color.White, fontSize = 17.sp)
            Text(
                "Show the screensaver when the screen turns off. Turn off to let the Portal sleep normally.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Switch(
              checked = settings.activateOnSleep,
              onCheckedChange = {
                ImmortalSettings.setActivateOnSleep(context, it)
                settings = ImmortalSettings.load(context)
                SettingsGuard.applyActivationSettings(context)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Activate on dock", color = Color.White, fontSize = 17.sp)
            Text(
                "Show the screensaver when the Portal is docked/plugged in.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
           Switch(
              checked = settings.activateOnDock,
              onCheckedChange = {
                ImmortalSettings.setActivateOnDock(context, it)
                settings = ImmortalSettings.load(context)
                SettingsGuard.applyActivationSettings(context)
              },
          )
        }
      }

      // System-wide back shortcut via accessibility service
      Spacer(Modifier.size(26.dp))
      SectionLabel("Back shortcut")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Immortal Back", color = Color.White, fontSize = 17.sp)
            Text(
                "Enables Immortal's accessibility BACK service. The Portal Settings app may hide third-party accessibility services, so the Enable button writes the secure setting directly.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Surface(
              color = MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                // The Portal's stock Settings app hides third-party
                // accessibility services from the menu, so we enable the
                // service directly via the secure setting instead.
                if (BackHelper.isBackServiceEnabled(context)) {
                  Toast.makeText(context, "Immortal Back is already enabled", Toast.LENGTH_SHORT).show()
                } else {
                  val resolver = context.contentResolver
                  val current = android.provider.Settings.Secure.getString(
                      resolver,
                      android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                  ) ?: ""
                  val service = "${context.packageName}/${ImmortalBackGestureService::class.java.name}"
                  val services = current.split(':').filter { it.isNotBlank() && it != service }
                  val newValue = (services + service).joinToString(":")
                  android.provider.Settings.Secure.putString(
                      resolver,
                      android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                      newValue
                  )
                  android.provider.Settings.Secure.putInt(
                      resolver,
                      android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                      1
                  )
                  Toast.makeText(context, "Enabled Immortal Back. Swipe-from-right still needs overlay permission.", Toast.LENGTH_LONG).show()
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
                          .setPackage(context.packageName)
                  )
                  Toast.makeText(context, "Back action sent", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(
                      context,
                      "Enable Immortal Back first so system-wide BACK works",
                      Toast.LENGTH_LONG
                  ).show()
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
        // System-wide back-swipe gesture via a transparent overlay
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Swipe-from-right back", color = Color.White, fontSize = 17.sp)
            Text(
                "Swipe left from the right edge of the screen in any app to go back. Requires " +
                    "\"Draw over other apps\" permission (granted below).",
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
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                      android.net.Uri.parse("package:${context.packageName}")
                  ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
  }
  FolderBackButton(onClick = { activity?.finish() })
}

@Composable
private fun SectionLabel(text: String) {
  Text(
      text.uppercase(),
      color = Color(0xFF7C7C7C),
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
  )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
  Surface(
      color = Color(0xFF1C1C1E),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column { content() }
  }
}

@Composable
private fun Divider() {
  Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.background(Color(0xFF2A2A2C), RoundedCornerShape(12.dp)).padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    options.forEach { (label, value) ->
      val on = value == selected
      Surface(
          color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.clickable { onSelect(value) },
      ) {
        Text(
            label,
            color = if (on) Color.White else Color(0xFFBBBBBB),
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
      }
    }
  }
}

/**
 * Returns true if the user has enabled Immortal's accessibility service in
 * Android Settings → Accessibility. We check by looking for our service
 * component in the list of enabled accessibility services.
 */
private fun isBackServiceEnabled(context: android.content.Context): Boolean {
  return runCatching {
    val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as android.view.accessibility.AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    enabled.any {
      it.resolveInfo.serviceInfo.packageName == context.packageName &&
          it.resolveInfo.serviceInfo.name ==
              "com.immortal.launcher.ImmortalBackGestureService"
    }
  }.getOrDefault(false)
}

/** Returns true if the overlay back-gesture service is running. */
private fun isOverlayRunning(context: android.content.Context): Boolean {
  return runCatching {
    val am = context.getSystemService(android.app.ActivityManager::class.java)
    am.getRunningServices(Int.MAX_VALUE).any { it.service.className == SystemBackGestureService::class.java.name }
  }.getOrDefault(false)
}

/** Start the system-wide back-swipe overlay service. */
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

/** Stop the system-wide back-swipe overlay service. */
private fun stopSystemBackGesture(context: android.content.Context) {
  runCatching {
    context.stopService(Intent(context, SystemBackGestureService::class.java))
  }
}
