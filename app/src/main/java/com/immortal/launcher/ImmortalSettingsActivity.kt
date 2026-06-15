/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortal.launcher.ui.theme.SampleAppTheme

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
