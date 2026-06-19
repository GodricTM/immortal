/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.graphics.drawable.toBitmap
import com.immortal.launcher.ui.theme.SampleAppTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immortal's own settings (weather unit, home-screen tile size), reached from the
 * "Immortal" tile in the launcher's Settings folder. The launcher re-reads these
 * on resume, so changes apply the moment the user returns home.
 */
class ImmortalSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val context = this
    var accentKey by mutableStateOf(ImmortalSettings.load(context).accentColor)
    setContent {
      SampleAppTheme(darkTheme = true, accentKey = accentKey) {
        ImmortalSettingsScreen(accentKey = accentKey, onAccentChange = { accentKey = it })
      }
    }
  }
}

@Composable
private fun ImmortalSettingsScreen(
    accentKey: String? = null,
    onAccentChange: ((String) -> Unit)? = null,
) {
  val context = LocalContext.current
  var showBootApps by remember { mutableStateOf(false) }
  var showMultiRoom by remember { mutableStateOf(false) }
  var bootSelected by remember { mutableStateOf(BootLaunch.packages(context).toSet()) }

  if (showBootApps) {
    BootAppsScreen(
        selected = bootSelected,
        onToggle = { pkg ->
          bootSelected = if (pkg in bootSelected) bootSelected - pkg else bootSelected + pkg
          BootLaunch.setPackages(context, bootSelected.toList())
        },
        onBack = { showBootApps = false },
    )
    return
  }
  if (showMultiRoom) {
    MultiRoomScreen(onBack = { showMultiRoom = false })
    return
  }

  SettingsMain(
      bootCount = bootSelected.size,
      onOpenBootApps = { showBootApps = true },
      onOpenMultiRoom = { showMultiRoom = true },
      onAccentChange = onAccentChange,
  )
}

@Composable
private fun SettingsMain(
    bootCount: Int,
    onOpenBootApps: () -> Unit,
    onOpenMultiRoom: () -> Unit,
    onAccentChange: ((String) -> Unit)? = null,
) {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(ImmortalSettings.load(context)) }
  var clockConfig by remember { mutableStateOf(DigitalClockConfig.load(context)) }

  // File picker for background image
  val imagePickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent()
  ) { uri: android.net.Uri? ->
      uri?.let {
          ImmortalSettings.setBackgroundImagePath(context, it.toString())
          settings = ImmortalSettings.load(context)
      }
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
      Text("Immortal", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
      Text(
          "Tune how the launcher looks and what it shows.",
          color = Color(0xFF9A9A9A),
          fontSize = 16.sp,
          modifier = Modifier.padding(top = 6.dp),
      )
      Spacer(Modifier.size(26.dp))

      SectionLabel("Weather")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Temperature", color = Color.White, fontSize = 17.sp)
            Text(
                "Auto follows your Portal's language & region setting.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Auto" to ImmortalSettings.UNIT_AUTO,
                      "°F" to ImmortalSettings.UNIT_F,
                      "°C" to ImmortalSettings.UNIT_C,
                  ),
              selected = settings.weatherUnit,
              onSelect = {
                ImmortalSettings.setWeatherUnit(context, it)
                settings = settings.copy(weatherUnit = it)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Home-screen forecast", color = Color.White, fontSize = 17.sp)
            Text(
                "Show a forecast below your apps. Off by default.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to ImmortalSettings.WIDGET_OFF,
                      "Hourly" to ImmortalSettings.WIDGET_HOURLY,
                      "7-day" to ImmortalSettings.WIDGET_DAILY,
                  ),
              selected = settings.weatherWidget,
              onSelect = {
                ImmortalSettings.setWeatherWidget(context, it)
                settings = settings.copy(weatherWidget = it)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Calendar agenda", color = Color.White, fontSize = 17.sp)
            Text(
                "Show upcoming events below your apps. Requires calendar permission.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to ImmortalSettings.CALENDAR_OFF,
                      "On" to ImmortalSettings.CALENDAR_ON,
                  ),
              selected = settings.calendarWidget,
              onSelect = {
                ImmortalSettings.setCalendarWidget(context, it)
                settings = settings.copy(calendarWidget = it)
              },
          )
        }
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Home screen")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("App icon size", color = Color.White, fontSize = 17.sp)
            Text(
                "Large is closer to the stock Portal launcher.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Standard" to ImmortalSettings.SIZE_STANDARD,
                      "Large" to ImmortalSettings.SIZE_LARGE,
                      "Extra large" to ImmortalSettings.SIZE_XL,
                  ),
              selected = settings.tileSize,
              onSelect = {
                ImmortalSettings.setTileSize(context, it)
                settings = settings.copy(tileSize = it)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Now-playing mini-player", color = Color.White, fontSize = 17.sp)
            Text(
                "Show the current track, cover art and controls in the header while music is playing.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (settings.showMiniPlayer) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowMiniPlayer(context, on)
                settings = settings.copy(showMiniPlayer = on)
              },
          )
        }
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Status bar", color = Color.White, fontSize = 17.sp)
            Text(
                "Hidden by default for a cleaner full-screen look. Swipe down from the top " +
                    "to reveal it briefly.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Show" to "show", "Hide" to "hide"),
              selected = if (settings.hideStatusBar) "hide" else "show",
              onSelect = {
                val hide = it == "hide"
                ImmortalSettings.setHideStatusBar(context, hide)
                settings = settings.copy(hideStatusBar = hide)
                SettingsGuard.applyStatusBar(context)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Text("Sort apps by", color = Color.White, fontSize = 17.sp)
          Text(
              "Manual keeps your drag order. Others reorder the grid automatically.",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
          )
          Segmented(
              options =
                  listOf(
                      "Manual" to ImmortalSettings.SORT_MANUAL,
                      "A→Z" to ImmortalSettings.SORT_AZ,
                      "Most used" to ImmortalSettings.SORT_USED,
                      "Recent" to ImmortalSettings.SORT_RECENT,
                  ),
              selected = settings.sortMode,
              onSelect = {
                ImmortalSettings.setSortMode(context, it)
                settings = settings.copy(sortMode = it)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Category tabs", color = Color.White, fontSize = 17.sp)
            Text(
                "Show a row of tabs (All + your folders) above the grid to filter apps.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (settings.showTabs) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowTabs(context, on)
                settings = settings.copy(showTabs = on)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Dashboard page", color = Color.White, fontSize = 17.sp)
            Text(
                "Add a second swipeable page with a big clock and your glanceable widgets.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (settings.dashboardPage) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setDashboardPage(context, on)
                settings = settings.copy(dashboardPage = on)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Next event in header", color = Color.White, fontSize = 17.sp)
            Text(
                "Show your next calendar event under the clock (needs calendar permission).",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (settings.showNextEvent) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowNextEvent(context, on)
                settings = settings.copy(showNextEvent = on)
              },
          )
        }
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Home widgets")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("System statistics", color = Color.White, fontSize = 17.sp)
            Text(
                "Show RAM, storage, battery, and temperature on the Immortal homepage.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to ImmortalSettings.STATS_OFF,
                      "On" to ImmortalSettings.STATS_ON,
                  ),
              selected = settings.statsMode,
              onSelect = {
                ImmortalSettings.setStatsMode(context, it)
                settings = settings.copy(statsMode = it)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Sunrise & sunset", color = Color.White, fontSize = 17.sp)
            Text(
                "Show today's sunrise and sunset under the header weather.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to "off",
                      "On" to "on",
                  ),
              selected = if (settings.showSunTimes) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowSunTimes(context, on)
                settings = settings.copy(showSunTimes = on)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Name-day", color = Color.White, fontSize = 17.sp)
            Text(
                "Show today's Romanian name-day (onomastica) in the header.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to "off",
                      "On" to "on",
                  ),
              selected = if (settings.showNameDay) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowNameDay(context, on)
                settings = settings.copy(showNameDay = on)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Orthodox feast-day", color = Color.White, fontSize = 17.sp)
            Text(
                "Show today's Orthodox feast (great feasts and Pascha) in the header.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Off" to "off",
                      "On" to "on",
                  ),
              selected = if (settings.showFeastDay) "on" else "off",
              onSelect = {
                val on = it == "on"
                ImmortalSettings.setShowFeastDay(context, on)
                settings = settings.copy(showFeastDay = on)
              },
          )
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Text("Calendar packs", color = Color.White, fontSize = 17.sp)
          Text(
              "Add the calendar that fits your household. These show in the header alongside " +
                  "the name-day / feast lines above.",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
          )
          CalendarPacks.AVAILABLE.forEach { pack ->
            var on by remember { mutableStateOf(CalendarPacks.isEnabled(context, pack.id)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(pack.title, color = Color.White, fontSize = 16.sp)
                Text(pack.blurb, color = Color(0xFF9A9A9A), fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp))
              }
              Switch(checked = on, onCheckedChange = {
                on = it
                CalendarPacks.setEnabled(context, pack.id, it)
              })
            }
          }
        }
      }

      Spacer(Modifier.size(14.dp))

      Card {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Text("Daily tile", color = Color.White, fontSize = 17.sp)
          Text(
              "Show a home-screen tile with a fresh quote, word, or trivia question each day.",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
          )
          Segmented(
              options =
                  listOf(
                      "Off" to "off",
                      "Quote" to "quote",
                      "Word" to "word",
                      "Trivia" to "trivia",
                  ),
              selected = settings.dailyTileMode,
              onSelect = {
                ImmortalSettings.setDailyTileMode(context, it)
                settings = settings.copy(dailyTileMode = it)
              },
          )
        }
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Clock")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Time format", color = Color.White, fontSize = 17.sp)
            Text(
                "Applies to the home screen, screensaver, and forecast. Auto follows your Portal's system setting.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Auto" to ImmortalSettings.CLOCK_AUTO,
                      "12h" to ImmortalSettings.CLOCK_12,
                      "24h" to ImmortalSettings.CLOCK_24,
                  ),
              selected = settings.clockFormat,
              onSelect = {
                ImmortalSettings.setClockFormat(context, it)
                settings = settings.copy(clockFormat = it)
              },
          )
        }
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Accent color")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Theme color", color = Color.White, fontSize = 17.sp)
            Text(
                "Choose the accent color for buttons and highlights.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
        }
        AccentColorGrid(
            selected = settings.accentColor,
              onSelect = {
                ImmortalSettings.setAccentColor(context, it)
                settings = settings.copy(accentColor = it)
                onAccentChange?.invoke(it)
              },
        )
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Background")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Background style", color = Color.White, fontSize = 17.sp)
            Text(
                "Choose a background for the home screen.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options =
                  listOf(
                      "Dark" to ImmortalSettings.BG_DARK,
                      "Gradient" to ImmortalSettings.BG_GRADIENT,
                      "Sky" to ImmortalSettings.BG_SKY,
                      "Stars" to ImmortalSettings.BG_STARS,
                      "Image" to ImmortalSettings.BG_IMAGE,
                      "Blur" to ImmortalSettings.BG_BLUR,
                  ),
              selected = settings.backgroundType,
              onSelect = {
                ImmortalSettings.setBackgroundType(context, it)
                settings = settings.copy(backgroundType = it)
              },
          )
        }
        if (settings.backgroundType == ImmortalSettings.BG_GRADIENT) {
          Divider()
          Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Gradient", color = Color.White, fontSize = 17.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            ImmortalSettings.GRADIENTS.chunked(4).forEach { rowItems ->
              Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { g ->
                  val selected = settings.backgroundGradient == g.first
                  Surface(
                      shape = RoundedCornerShape(12.dp),
                      modifier =
                          Modifier.size(64.dp)
                              .then(
                                  if (selected)
                                      Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                  else Modifier)
                              .tvFocusable(RoundedCornerShape(12.dp), focusScale = 1.05f) {
                                ImmortalSettings.setBackgroundGradient(context, g.first)
                                settings = settings.copy(backgroundGradient = g.first)
                              },
                  ) {
                    Box(
                        modifier =
                            Modifier.background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color(g.second), Color(g.third)))))
                  }
                }
              }
            }
          }
        }
        if (settings.backgroundType == ImmortalSettings.BG_SKY) {
          Divider()
          Text(
              "The background tracks the real sun: dawn pinks, midday blue, dusk " +
                  "orange, night dark — based on today's sunrise and sunset.",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(18.dp),
          )
          Divider()
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Day progress bar", color = Color.White, fontSize = 17.sp)
              Text(
                  "A thin bar across the top showing how far through the day it is, " +
                      "tinted to match the live sky.",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Segmented(
                options =
                    listOf(
                        "Off" to "off",
                        "On" to "on",
                    ),
                selected = if (settings.showDayProgress) "on" else "off",
                onSelect = {
                  val on = it == "on"
                  ImmortalSettings.setShowDayProgress(context, on)
                  settings = settings.copy(showDayProgress = on)
                },
            )
          }
        }
        if (settings.backgroundType == ImmortalSettings.BG_IMAGE ||
            settings.backgroundType == ImmortalSettings.BG_BLUR) {
          Divider()
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Background image", color = Color.White, fontSize = 17.sp)
              Text(
                  if (settings.backgroundImagePath != null)
                      "Image set: ${settings.backgroundImagePath}"
                  else
                      "No image selected",
                  color = Color(0xFF9A9A9A),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 2.dp),
              )
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                  imagePickerLauncher.launch("image/*")
                },
            ) {
              Text(
                  "Choose",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
              )
            }
          }
        }
      }

      Spacer(Modifier.size(26.dp))

      Spacer(Modifier.size(26.dp))

      SectionLabel("System sounds")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          var soundsOn by remember { mutableStateOf(SystemSounds.touchSoundsEnabled(context)) }
          var needsGrant by remember { mutableStateOf(false) }
          // Re-read on resume: the value may have changed on the grant screen.
          val lifecycleOwner = LocalLifecycleOwner.current
          DisposableEffect(lifecycleOwner) {
            val obs = LifecycleEventObserver { _, event ->
              if (event == Lifecycle.Event.ON_RESUME) {
                soundsOn = SystemSounds.touchSoundsEnabled(context)
                if (SystemSounds.canWrite(context)) needsGrant = false
              }
            }
            lifecycleOwner.lifecycle.addObserver(obs)
            onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("Touch & keypress sounds", color = Color.White, fontSize = 17.sp)
            Text(
                "The system-wide click sounds (keyboard taps, lock, dock). Portal ships " +
                    "these off, which also mutes any click the launcher plays.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (needsGrant) {
              Text(
                  "Needs \"Modify system settings\" — opening that screen so you can allow it.",
                  color = Color(0xFFE0A030),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
          Segmented(
              options =
                  listOf(
                      "Off" to "off",
                      "On" to "on",
                  ),
              selected = if (soundsOn) "on" else "off",
              onSelect = {
                val on = it == "on"
                if (!SystemSounds.canWrite(context)) {
                  needsGrant = true
                  SystemSounds.requestWriteAccess(context)
                } else if (SystemSounds.setTouchSounds(context, on)) {
                  soundsOn = on
                }
              },
          )
        }
      }

      Spacer(Modifier.size(26.dp))

      SectionLabel("Hidden apps")
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Restore hidden apps", color = Color.White, fontSize = 17.sp)
            Text(
                "Apps hidden via the edit (✎) mode are restored to the grid.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          var restored by remember { mutableStateOf(false) }
          Surface(
              color = if (restored) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                UserLayout.unhideAllPackages(context)
                restored = true
              },
          ) {
            Text(
                if (restored) "Restored" else "Restore all",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
          }
        }
      }

      Spacer(Modifier.size(26.dp))
      MultiRoomNavRow(onOpen = onOpenMultiRoom)

      Spacer(Modifier.size(26.dp))
      BootAppsNavRow(count = bootCount, onOpen = onOpenBootApps)

      DeviceAdminRow()

      Text(
          "Changes apply as soon as you go back to the home screen.",
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

@Composable
private fun AccentColorGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
  val colors = listOf(
      ImmortalSettings.ACCENT_BLUE to Color(0xFF2196F3),
      ImmortalSettings.ACCENT_RED to Color(0xFFF44336),
      ImmortalSettings.ACCENT_GREEN to Color(0xFF4CAF50),
      ImmortalSettings.ACCENT_PURPLE to Color(0xFF9C27B0),
      ImmortalSettings.ACCENT_ORANGE to Color(0xFFFF9800),
      ImmortalSettings.ACCENT_PINK to Color(0xFFE91E63),
      ImmortalSettings.ACCENT_TEAL to Color(0xFF009688),
      ImmortalSettings.ACCENT_YELLOW to Color(0xFFFFEB3B),
      ImmortalSettings.ACCENT_INDIGO to Color(0xFF3F51B5),
      ImmortalSettings.ACCENT_CYAN to Color(0xFF00BCD4),
      ImmortalSettings.ACCENT_LIME to Color(0xFFCDDC39),
      ImmortalSettings.ACCENT_AMBER to Color(0xFFFFC107),
      ImmortalSettings.ACCENT_DEEP_PURPLE to Color(0xFF673AB7),
      ImmortalSettings.ACCENT_BROWN to Color(0xFF795548),
      ImmortalSettings.ACCENT_CORAL to Color(0xFFFF6F61),
      ImmortalSettings.ACCENT_MINT to Color(0xFF98FF98),
  )
  LazyVerticalGrid(
      columns = GridCells.Fixed(8),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp).height(120.dp),
  ) {
    items(colors.size) { index ->
      val (key, color) = colors[index]
      val isSelected = key == selected
      Box(
          modifier = Modifier
              .size(48.dp)
              .background(color, CircleShape)
              .then(
                  if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                  else Modifier
              )
              .clickable { onSelect(key) },
      )
    }
  }
}

/**
 * Nav row on the main settings page that opens the Multi-room audio subpage. Install-
 * context-aware: shown only when the Snapcast player ([MultiRoomService.SNAPCAST_PACKAGE])
 * is installed — i.e. when this Portal is set up as a synced speaker.
 */
@Composable
private fun MultiRoomNavRow(onOpen: () -> Unit) {
  val context = LocalContext.current
  val installed = remember { StoreCatalog.isInstalled(context, MultiRoomService.SNAPCAST_PACKAGE) }
  if (!installed) return

  Spacer(Modifier.size(26.dp))
  SectionLabel("Multi-room audio")
  Card {
    Row(
        modifier = Modifier.fillMaxWidth().tvFocusableRow { onOpen() }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Multi-room audio", color = Color.White, fontSize = 17.sp)
        Text(
            if (ImmortalSettings.multiRoomEnabled(context)) MultiRoomStatus.text.ifBlank { "On" }
            else "Off",
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
      Text("›", color = Color(0xFF7C7C7C), fontSize = 26.sp)
    }
  }
}

/**
 * The Multi-room audio subpage (reached from [MultiRoomNavRow]): surface the Snapcast
 * group's track on the now-playing card and device media controls, with play/pause/skip
 * forwarded to Music Assistant. The in-launcher relay reads the snapserver for metadata;
 * the MA login is needed only to send transport. Back returns to the main settings page.
 */
/** A numbered step in the multi-room setup guide. */
@Composable
private fun MultiRoomStep(n: String, text: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Text(
        "$n.",
        color = Color(0xFF8AB4F8),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(end = 10.dp),
    )
    Text(text, color = Color(0xFFB8B8B8), fontSize = 14.sp, lineHeight = 20.sp)
  }
}

@Composable
private fun MultiRoomScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var enabled by remember { mutableStateOf(ImmortalSettings.multiRoomEnabled(context)) }
  var host by remember { mutableStateOf(ImmortalSettings.snapcastHost(context)) }
  var maUser by remember { mutableStateOf(ImmortalSettings.maUser(context)) }
  var maPass by remember { mutableStateOf(ImmortalSettings.maPass(context)) }

  val firstFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
  // Chain the text fields so the keyboard's "Next" jumps to the following field instead of
  // closing — the IP field focuses the username, which focuses the password.
  val focusManager = LocalFocusManager.current
  val userFocus = remember { FocusRequester() }
  val passFocus = remember { FocusRequester() }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .onPreviewKeyEvent { e ->
                if (e.key == Key.Back) {
                  if (e.type == KeyEventType.KeyUp) onBack()
                  true
                } else false
              }
              .background(Color(0xFF101012))
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 28.dp, vertical = 32.dp),
  ) {
    Column(modifier = Modifier.widthIn(max = 1100.dp).focusGroup()) {
      Surface(
          color = Color(0xFF1C1C1E),
          shape = RoundedCornerShape(12.dp),
          modifier =
              Modifier.focusRequester(firstFocus).tvFocusable(RoundedCornerShape(12.dp)) { onBack() },
      ) {
        Text(
            "‹  Back",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
      }
      Spacer(Modifier.size(18.dp))

      Text(
          "Multi-room audio",
          color = Color.White,
          fontSize = 34.sp,
          fontWeight = FontWeight.SemiBold)
      Text(
          "Show the Snapcast group's track on the now-playing card, with play/pause/skip.",
          color = Color(0xFF9A9A9A),
          fontSize = 16.sp,
          modifier = Modifier.padding(top = 6.dp),
      )
      Spacer(Modifier.size(22.dp))
      Card {
        Column(modifier = Modifier.padding(18.dp)) {
          Text("Setting it up", color = Color.White, fontSize = 17.sp)
          Text(
              "Group your Portals into one perfectly in-sync speaker system:",
              color = Color(0xFF9A9A9A),
              fontSize = 13.sp,
              modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
          )
          MultiRoomStep(
              "1",
              "Install and set up Music Assistant as a server on your home network. New to Music " +
                  "Assistant? Learn more at music-assistant.io")
          MultiRoomStep(
              "2",
              "Install Snapcast from the Immortal App Store, and point it at your Music Assistant " +
                  "server.")
          MultiRoomStep(
              "3", "Turn on the toggle below, then enter your Music Assistant server's address.")
        }
      }
      Spacer(Modifier.size(26.dp))
      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Show what the group is playing", color = Color.White, fontSize = 17.sp)
            Text(
                "Surfaces the Snapcast group's track on the now-playing card — even when the " +
                    "Music Assistant app isn't open on this Portal.",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Segmented(
              options = listOf("Off" to "off", "On" to "on"),
              selected = if (enabled) "on" else "off",
              onSelect = {
                val on = it == "on"
                enabled = on
                ImmortalSettings.setMultiRoomEnabled(context, on)
                MultiRoomService.sync(context)
              },
          )
        }
        if (enabled) {
          Divider()
          Row(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
                value = host,
                onValueChange = {
                  host = it
                  ImmortalSettings.setSnapcastHost(context, it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() }),
                label = { Text("Music Assistant / Snapcast server IP") },
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = Color(0xFF2E6BE6),
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier.padding(start = 12.dp).tvFocusable(RoundedCornerShape(10.dp)) {
                      ImmortalSettings.setSnapcastHost(context, host)
                      MultiRoomService.sync(context)
                    },
            ) {
              Text(
                  "Apply",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
              )
            }
          }
          // Music Assistant login — only used to forward play/pause/skip to MA.
          OutlinedTextField(
              value = maUser,
              onValueChange = {
                maUser = it
                ImmortalSettings.setMaUsername(context, it)
              },
              singleLine = true,
              // No auto-capitalize: a lowercase MA username must stay lowercase.
              keyboardOptions =
                  KeyboardOptions(
                      capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
              keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() }),
              label = { Text("Music Assistant username") },
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(start = 18.dp, end = 18.dp, top = 4.dp)
                      .focusRequester(userFocus),
          )
          OutlinedTextField(
              value = maPass,
              onValueChange = {
                maPass = it
                ImmortalSettings.setMaPassword(context, it)
              },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions =
                  KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
              label = { Text("Music Assistant password") },
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(start = 18.dp, end = 18.dp, top = 8.dp)
                      .focusRequester(passFocus),
          )
          Row(
              modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
                color = Color(0xFF2E6BE6),
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier.tvFocusable(RoundedCornerShape(10.dp)) { MaControl.testLogin(context) },
            ) {
              Text(
                  "Sign in",
                  color = Color.White,
                  fontSize = 15.sp,
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
              )
            }
            val auth = MultiRoomStatus.maAuth
            if (auth.isNotBlank()) {
              Text(
                  auth,
                  color = if (auth.endsWith("✓")) Color(0xFF66BB6A) else Color(0xFFE57373),
                  fontSize = 13.sp,
                  modifier = Modifier.padding(start = 14.dp),
              )
            }
          }
          Text(
              "Sends play/pause/skip to Music Assistant, and shows now-playing for AirPlay " +
                  "sources (which don't carry it over Snapcast). Library/radio now-playing works " +
                  "without it.",
              color = Color(0xFF7C7C7C),
              fontSize = 12.sp,
              modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp),
          )

          // Live relay status — gives Apply visible feedback (Connecting… → Connected).
          Text(
              MultiRoomStatus.text.ifBlank { "Starting…" },
              color = Color(0xFF8AB4F8),
              fontSize = 13.sp,
              modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
          )
        }
      }
      Text(
          "Join this Portal to the same Snapcast group as your other rooms, then point it " +
              "at your Music Assistant server. The synced audio is played by the Snapcast app.",
          color = Color(0xFF7C7C7C),
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
      )
    }
  }
}

private data class BootAppOption(val pkg: String, val label: String, val icon: ImageBitmap)

/** Every launchable app except our own launcher, for the boot-launch picker. */
private fun loadLaunchableApps(context: Context): List<BootAppOption> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm.queryIntentActivities(intent, 0)
      .filter { it.activityInfo.packageName != context.packageName }
      .mapNotNull { ri ->
        runCatching {
              BootAppOption(
                  pkg = ri.activityInfo.packageName,
                  label = ri.loadLabel(pm).toString(),
                  icon = ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap())
            }
            .getOrNull()
      }
      .distinctBy { it.pkg }
      .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

/**
 * Shown only when Immortal's screen-off device admin is active. Deactivating it turns off
 * the idle / overnight screen-off features AND lets Immortal be uninstalled — the shell
 * can't force-remove a non-test admin, so this in-app action is the clean path.
 */
@Composable
private fun DeviceAdminRow() {
  val context = LocalContext.current
  var active by remember { mutableStateOf(ScreenControl.isAdminActive(context)) }
  if (!active) return

  Spacer(Modifier.size(26.dp))
  SectionLabel("Device admin")
  Card {
    Row(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Screen-off control", color = Color.White, fontSize = 17.sp)
        Text(
            "Lets Immortal turn the screen off for idle and overnight sleep. Turning it off " +
                "also allows Immortal to be uninstalled.",
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
      Surface(
          color = Color(0xFF3A3A3C),
          shape = RoundedCornerShape(10.dp),
          modifier =
              Modifier.padding(start = 12.dp).tvFocusable(RoundedCornerShape(10.dp)) {
                ScreenControl.deactivateAdmin(context)
                active = ScreenControl.isAdminActive(context)
              },
      ) {
        Text(
            "Turn off",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
      }
    }
  }
}

/**
 * Row on the main settings page that opens the "Start on boot" subpage. The full app
 * list lives on its own page so it doesn't flood the main settings screen; here we just
 * summarise how many apps are set to relaunch after a reboot.
 */
@Composable
private fun BootAppsNavRow(count: Int, onOpen: () -> Unit) {
  SectionLabel("Start on boot")
  Card {
    Row(
        modifier = Modifier.fillMaxWidth().tvFocusableRow { onOpen() }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Apps that start on boot", color = Color.White, fontSize = 17.sp)
        Text(
            if (count == 0) "None — pick apps that relaunch after a reboot"
            else "$count app${if (count == 1) "" else "s"} relaunch after a reboot",
            color = Color(0xFF9A9A9A),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
      }
      Text("›", color = Color(0xFF7C7C7C), fontSize = 26.sp)
    }
  }
  Text(
      "Handy for players like Music Assistant that don't restart themselves after a reboot.",
      color = Color(0xFF7C7C7C),
      fontSize = 13.sp,
      modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
  )
}

/**
 * The "Start on boot" subpage: pick which installed apps Immortal relaunches after a
 * reboot — the same per-device list provisioning seeds (so e.g. the Music Assistant /
 * Sendspin player, which has no boot receiver, comes back on its own). Toggling a row
 * writes the list straight to the file [BootLaunch] reads on boot. Back returns to the
 * main settings page (handled by the parent via [onBack]).
 */
@Composable
private fun BootAppsScreen(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<BootAppOption>?>(null) }
  LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) } }

  // Open with the Back control focused so the remote can leave the subpage immediately.
  val firstFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .onPreviewKeyEvent { e ->
                if (e.key == Key.Back) {
                  if (e.type == KeyEventType.KeyUp) onBack()
                  true
                } else false
              }
              .background(Color(0xFF101012))
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 28.dp, vertical = 32.dp),
  ) {
    Column(modifier = Modifier.widthIn(max = 1100.dp).focusGroup()) {
      Surface(
          color = Color(0xFF1C1C1E),
          shape = RoundedCornerShape(12.dp),
          modifier =
              Modifier.focusRequester(firstFocus).tvFocusable(RoundedCornerShape(12.dp)) { onBack() },
      ) {
        Text(
            "‹  Back",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
      }
      Spacer(Modifier.size(18.dp))

      Text("Start on boot", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
      Text(
          "Pick which installed apps Immortal relaunches after a reboot.",
          color = Color(0xFF9A9A9A),
          fontSize = 16.sp,
          modifier = Modifier.padding(top = 6.dp),
      )
      Spacer(Modifier.size(26.dp))

      Card {
        val list = apps
        if (list == null) {
          Text(
              "Loading apps…",
              color = Color(0xFF9A9A9A),
              fontSize = 14.sp,
              modifier = Modifier.padding(18.dp),
          )
        } else if (list.isEmpty()) {
          Text(
              "No other apps installed yet.",
              color = Color(0xFF9A9A9A),
              fontSize = 14.sp,
              modifier = Modifier.padding(18.dp),
          )
        } else {
          list.forEachIndexed { i, app ->
            if (i > 0) Divider()
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .tvFocusableRow { onToggle(app.pkg) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(34.dp))
              Text(
                  app.label,
                  color = Color.White,
                  fontSize = 16.sp,
                  modifier = Modifier.weight(1f).padding(start = 14.dp),
              )
              // Visual only — the row toggles it (so the remote's center button works).
              Switch(checked = app.pkg in selected, onCheckedChange = null)
            }
          }
        }
      }
      Text(
          "These apps relaunch automatically after a reboot — handy for players like Music " +
              "Assistant that don't restart themselves.",
          color = Color(0xFF7C7C7C),
          fontSize = 13.sp,
          modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
      )
    }
  }
}
