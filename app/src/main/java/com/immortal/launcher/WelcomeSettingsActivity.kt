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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortal.launcher.ui.theme.SampleAppTheme

/**
 * Settings screen for the welcome-back overlay. Customize greeting messages,
 * colors, sizes, duration, and visibility of individual elements.
 */
class WelcomeSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SampleAppTheme(darkTheme = true) { WelcomeSettingsScreen() } }
  }
}

@Composable
private fun WelcomeSettingsScreen() {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(WelcomeConfig.load(context)) }

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
        Text("Welcome Overlay", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Customize the greeting shown when the screensaver wakes up.",
            color = Color(0xFF9A9A9A),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.size(26.dp))

        Card {
          ToggleRow("Show greeting message", settings.showGreeting) {
            WelcomeConfig.setShowGreeting(context, it)
            settings = settings.copy(showGreeting = it)
          }
          Divider()
          ToggleRow("Show clock", settings.showClock) {
            WelcomeConfig.setShowClock(context, it)
            settings = settings.copy(showClock = it)
          }
          Divider()
          ToggleRow("Show date", settings.showDate) {
            WelcomeConfig.setShowDate(context, it)
            settings = settings.copy(showDate = it)
          }
          Divider()
          ToggleRow("Speak greeting (TTS)", settings.enableTts) {
            WelcomeConfig.setEnableTts(context, it)
            settings = settings.copy(enableTts = it)
          }
        }

        Spacer(Modifier.size(26.dp))

        SectionLabel("GREETINGS")
        Card {
          EditableTextRow("Your name", settings.userName) { text ->
            WelcomeConfig.setUserName(context, text)
            settings = settings.copy(userName = text)
          }
          Divider()
          EditableTextRow("Night (22:00-04:59)", settings.greetingNight) { text ->
            WelcomeConfig.setGreetingNight(context, text)
            settings = settings.copy(greetingNight = text)
          }
          Divider()
          EditableTextRow("Morning (05:00-11:59)", settings.greetingMorning) { text ->
            WelcomeConfig.setGreetingMorning(context, text)
            settings = settings.copy(greetingMorning = text)
          }
          Divider()
          EditableTextRow("Afternoon (12:00-16:59)", settings.greetingAfternoon) { text ->
            WelcomeConfig.setGreetingAfternoon(context, text)
            settings = settings.copy(greetingAfternoon = text)
          }
          Divider()
          EditableTextRow("Evening (17:00-21:59)", settings.greetingEvening) { text ->
            WelcomeConfig.setGreetingEvening(context, text)
            settings = settings.copy(greetingEvening = text)
          }
        }

        Spacer(Modifier.size(26.dp))

        SectionLabel("TIMING")
        Card {
          DurationStepper(settings.durationMs / 1000) { sec ->
            val ms = WelcomeConfig.clampDuration(sec * 1000)
            WelcomeConfig.setDuration(context, ms)
            settings = settings.copy(durationMs = ms)
          }
        }
        Text(
            "How long the welcome overlay displays before auto-dismissing.",
            color = Color(0xFF7C7C7C),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )

        Spacer(Modifier.size(26.dp))

        SectionLabel("TEXT SIZES")
        Card {
          SizeStepper("Greeting", settings.greetingSize) { size ->
            val clamped = WelcomeConfig.clampTextSize(size)
            WelcomeConfig.setGreetingSize(context, clamped)
            settings = settings.copy(greetingSize = clamped)
          }
          Divider()
          SizeStepper("Clock", settings.clockSize) { size ->
            val clamped = WelcomeConfig.clampTextSize(size)
            WelcomeConfig.setClockSize(context, clamped)
            settings = settings.copy(clockSize = clamped)
          }
          Divider()
          SizeStepper("Date", settings.dateSize) { size ->
            val clamped = WelcomeConfig.clampTextSize(size)
            WelcomeConfig.setDateSize(context, clamped)
            settings = settings.copy(dateSize = clamped)
          }
        }

        Spacer(Modifier.size(26.dp))

        SectionLabel("BACKGROUND")
        Card {
          OpacityStepper(settings.backgroundOpacity) { opacity ->
            val clamped = WelcomeConfig.clampOpacity(opacity)
            WelcomeConfig.setBackgroundOpacity(context, clamped)
            settings = settings.copy(backgroundOpacity = clamped)
          }
        }
        Text(
            "Background darkness behind the welcome text (0% = transparent, 100% = opaque).",
            color = Color(0xFF7C7C7C),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
        )

        Spacer(Modifier.size(28.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp),
            modifier =
                Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(16.dp), focusScale = 1f) {
                  val intent = Intent(context, PhotoFramePreviewActivity::class.java)
                  intent.putExtra(PhotoFramePreviewActivity.EXTRA_SHOW_WELCOME, true)
                  context.startActivity(intent)
                },
        ) {
          Text(
              "Preview welcome overlay",
              color = Color.White,
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
          )
        }

        Spacer(Modifier.size(16.dp))

        Text(
            "Tip: The welcome overlay appears when the screensaver starts from sleep. " +
                "Tap anywhere on the overlay to dismiss it early, or wait for it to auto-fade.",
            color = Color(0xFF7C7C7C),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
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
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth().tvFocusableRow { onChange(!checked) }
              .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun EditableTextRow(label: String, value: String, onChange: (String) -> Unit) {
  var editing by remember { mutableStateOf(false) }
  var tempValue by remember(value) { mutableStateOf(value) }

  Row(
      modifier =
          Modifier.fillMaxWidth().tvFocusableRow {
                if (!editing) {
                  editing = true
                  tempValue = value
                }
              }
              .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    if (editing) {
      OutlinedTextField(
          value = tempValue,
          onValueChange = { tempValue = it },
          singleLine = true,
          modifier = Modifier.width(200.dp),
          colors = TextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedContainerColor = Color(0xFF2A2A2C),
              unfocusedContainerColor = Color(0xFF2A2A2C),
          ),
      )
      Spacer(Modifier.width(8.dp))
      TextButton(onClick = {
        onChange(tempValue)
        editing = false
      }) {
        Text("Save", color = Color(0xFF8AB4F8))
      }
    } else {
      Text(
          value,
          color = Color(0xFFDDDDDD),
          fontSize = 15.sp,
          modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}

@Composable
private fun DurationStepper(seconds: Int, onChange: (Int) -> Unit) {
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                  when (e.key) {
                    Key.DirectionLeft -> { onChange(seconds - 1); true }
                    Key.DirectionRight -> { onChange(seconds + 1); true }
                    else -> false
                  }
                } else false
              }
              .focusable(interactionSource = src)
              .background(if (focused) Color(0x402E6BE6) else Color.Transparent)
              .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Display duration", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    ArrowButton("◀", focused) { onChange(seconds - 1) }
    Text(
        "${seconds}s",
        color = if (focused) Color.White else Color(0xFFDDDDDD),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 52.dp),
    )
    ArrowButton("▶", focused) { onChange(seconds + 1) }
  }
}

@Composable
private fun SizeStepper(label: String, size: Float, onChange: (Float) -> Unit) {
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                  when (e.key) {
                    Key.DirectionLeft -> { onChange(size - 2); true }
                    Key.DirectionRight -> { onChange(size + 2); true }
                    else -> false
                  }
                } else false
              }
              .focusable(interactionSource = src)
              .background(if (focused) Color(0x402E6BE6) else Color.Transparent)
              .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    ArrowButton("◀", focused) { onChange(size - 2) }
    Text(
        "${size.toInt()}sp",
        color = if (focused) Color.White else Color(0xFFDDDDDD),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 64.dp),
    )
    ArrowButton("▶", focused) { onChange(size + 2) }
  }
}

@Composable
private fun OpacityStepper(opacity: Float, onChange: (Float) -> Unit) {
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  val percent = (opacity * 100).toInt()
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                  when (e.key) {
                    Key.DirectionLeft -> { onChange(opacity - 0.05f); true }
                    Key.DirectionRight -> { onChange(opacity + 0.05f); true }
                    else -> false
                  }
                } else false
              }
              .focusable(interactionSource = src)
              .background(if (focused) Color(0x402E6BE6) else Color.Transparent)
              .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Opacity", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    ArrowButton("◀", focused) { onChange(opacity - 0.05f) }
    Text(
        "$percent%",
        color = if (focused) Color.White else Color(0xFFDDDDDD),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 64.dp),
    )
    ArrowButton("▶", focused) { onChange(opacity + 0.05f) }
  }
}

@Composable
private fun ArrowButton(glyph: String, rowFocused: Boolean, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .focusProperties { canFocus = false }
              .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        glyph,
        color = if (rowFocused) Color.White else Color(0xFFBBBBBB),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
  }
}
