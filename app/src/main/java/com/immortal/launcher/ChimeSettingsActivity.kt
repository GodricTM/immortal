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
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.immortal.launcher.ui.theme.SampleAppTheme

class ChimeSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { SampleAppTheme(darkTheme = true) { ChimeSettingsScreen() } }
  }
}

@Composable
private fun ChimeSettingsScreen() {
  val context = LocalContext.current
  var settings by remember { mutableStateOf(ChimeConfig.load(context)) }
  val activity = context as? Activity

  // Re-arm alarms to match the current config after any change.
  fun apply() = ChimeScheduler.reschedule(context)

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(Color(0xFF111111))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Sounds", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
      Text(
          "Gentle ambient cues. All off by default; nothing plays during quiet hours.",
          color = Color(0xFF9A9A9A),
          fontSize = 15.sp,
          textAlign = TextAlign.Center,
      )

      Card {
        ChimeToggleRow("Hourly chime", "A soft chime on the hour", settings.hourlyChimeOn) {
          ChimeConfig.setHourlyChime(context, it)
          settings = settings.copy(hourlyChimeOn = it)
          apply()
        }
        if (settings.hourlyChimeOn) {
          VolumeStepper("Volume", settings.chimeVolume) { v ->
            ChimeConfig.setChimeVolume(context, v)
            settings = settings.copy(chimeVolume = v)
          }
          TestButton("Play chime") { ChimePlayer.playChime(context) }
        }
        ChimeToggleRow("Spoken time", "\"It's three o'clock\" on the hour", settings.spokenTimeOn) {
          ChimeConfig.setSpokenTime(context, it)
          settings = settings.copy(spokenTimeOn = it)
          apply()
        }
        if (settings.spokenTimeOn) {
          VolumeStepper("Volume", settings.spokenVolume) { v ->
            ChimeConfig.setSpokenVolume(context, v)
            settings = settings.copy(spokenVolume = v)
          }
          VoicePicker(settings.spokenVoice) { name ->
            ChimeConfig.setSpokenVoice(context, name)
            settings = settings.copy(spokenVoice = name)
          }
          TestButton("Test voice") { ChimePlayer.testVoice(context, settings.spokenVoice) }
        }
        ChimeToggleRow("Golden-hour tone", "A sound at sunrise and sunset", settings.goldenHourOn) {
          ChimeConfig.setGoldenHour(context, it)
          settings = settings.copy(goldenHourOn = it)
          apply()
        }
        if (settings.goldenHourOn) {
          VolumeStepper("Volume", settings.goldenVolume) { v ->
            ChimeConfig.setGoldenVolume(context, v)
            settings = settings.copy(goldenVolume = v)
          }
          // Two sunrise sounds to choose between; sunset is always "Goodnight".
          SunriseVariantPicker(settings.sunriseVariant) { v ->
            ChimeConfig.setSunriseVariant(context, v)
            settings = settings.copy(sunriseVariant = v)
          }
          TestButton("Test sunrise") { ChimePlayer.playSunriseTone(context) }
          TestButton("Test sunset") { ChimePlayer.playSunsetTone(context) }
        }
      }

      Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Ping the other room", color = Color.White, fontSize = 17.sp)
            Text("Ring + spoken name when another Portal pings this one",
                color = Color(0xFF9A9A9A), fontSize = 13.sp)
          }
        }
        VolumeStepper("Volume", settings.pingVolume) { v ->
          ChimeConfig.setPingVolume(context, v)
          settings = settings.copy(pingVolume = v)
        }
        TestButton("Play ping") { ChimePlayer.playPing(context, repeats = 1) }
      }

      Card {
        ChimeToggleRow("Quiet hours", "Silence all cues overnight", settings.quietHoursOn) {
          ChimeConfig.setQuietHours(context, it)
          settings = settings.copy(quietHoursOn = it)
          apply()
        }
        if (settings.quietHoursOn) {
          TimeStepper("Quiet from", settings.quietStartMin) { v ->
            ChimeConfig.setQuietStart(context, v)
            settings = settings.copy(quietStartMin = v)
          }
          TimeStepper("Quiet until", settings.quietEndMin) { v ->
            ChimeConfig.setQuietEnd(context, v)
            settings = settings.copy(quietEndMin = v)
          }
        }
      }
    }
  }
  FolderBackButton(onClick = { activity?.finish() })
}

@Composable
private fun ChimeToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth().tvFocusableRow { onChange(!checked) }
              .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, color = Color.White, fontSize = 17.sp)
      Text(subtitle, color = Color(0xFF9A9A9A), fontSize = 13.sp)
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun TestButton(label: String, onClick: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
      shape = RoundedCornerShape(12.dp),
      modifier =
          Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
              .tvFocusable(RoundedCornerShape(12.dp), focusScale = 1f) { onClick() },
  ) {
    Text(
        "▶  $label",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
  }
}

/** Dropdown of the TTS engine's installed voices for the chosen locale. The empty
 *  name means "engine default". Voices are enumerated from a throwaway TextToSpeech. */
@Composable
private fun VoicePicker(current: String, onPick: (String) -> Unit) {
  val context = LocalContext.current
  // name -> friendly label; first entry is always the engine default.
  var voices by remember { mutableStateOf<List<Pair<String, String>>>(listOf("" to "Default voice")) }
  var expanded by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    var tts: android.speech.tts.TextToSpeech? = null
    tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
      if (status == android.speech.tts.TextToSpeech.SUCCESS) {
        val found = runCatching {
          tts?.voices
              ?.filter { !it.isNetworkConnectionRequired }
              ?.sortedWith(chimeTtsVoiceSort())
              ?.map { it.name to prettyVoice(it) }
              ?.distinctBy { it.first }
              ?: emptyList()
        }.getOrDefault(emptyList())
        if (found.isNotEmpty()) voices = listOf("" to "Default voice") + found
      }
    }
    onDispose { runCatching { tts?.shutdown() } }
  }

  val label = voices.firstOrNull { it.first == current }?.second ?: "Default voice"
  Box {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .tvFocusableRow { expanded = true }
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Voice", color = Color(0xFFDDDDDD), fontSize = 15.sp, modifier = Modifier.weight(1f))
      Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
      Text("  ▾", color = Color(0xFFDDDDDD), fontSize = 15.sp)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      voices.forEach { (name, friendly) ->
        DropdownMenuItem(
            text = { Text(friendly) },
            onClick = { onPick(name); expanded = false })
      }
    }
  }
}

private fun chimeTtsVoiceSort(): Comparator<android.speech.tts.Voice> =
    compareBy<android.speech.tts.Voice> { chimeTtsVoiceGroup(it) }
        .thenByDescending { it.quality }
        .thenBy { it.locale.displayLanguage }
        .thenBy { it.name }

private fun chimeTtsVoiceGroup(v: android.speech.tts.Voice): Int {
  val n = v.name.lowercase()
  return when {
    n.startsWith("sherpa-kokoro-") -> 0
    n.startsWith("sherpa-piper-ro_ro-") -> 1
    n.startsWith("sherpa-piper-") -> 2
    else -> 3
  }
}

private fun chimeTtsLocaleLabel(v: android.speech.tts.Voice): String {
  val language = v.locale.getDisplayLanguage(java.util.Locale.US).ifBlank { v.locale.language }
  val country = v.locale.country
  return if (country.isBlank()) language else "$language (${country.uppercase()})"
}

private fun chimeTtsQualityLabel(v: android.speech.tts.Voice): String? =
    when {
      v.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> "HQ+"
      v.quality >= android.speech.tts.Voice.QUALITY_HIGH -> "HQ"
      else -> null
    }

/** Turn an engine voice name like "en-us-x-sfg#female_1-local" into something readable. */
private fun prettyVoice(v: android.speech.tts.Voice): String {
  val n = v.name.lowercase()
  if (n.startsWith("sherpa-kokoro-")) {
    val voice = v.name.removePrefix("sherpa-kokoro-")
    return listOfNotNull("Kokoro $voice", chimeTtsLocaleLabel(v), chimeTtsQualityLabel(v)).joinToString(" - ")
  }
  if (n == "sherpa-piper-ro_ro-mihai-medium") {
    return listOfNotNull("Romanian Mihai", chimeTtsLocaleLabel(v), chimeTtsQualityLabel(v)).joinToString(" - ")
  }
  if (n.startsWith("sherpa-piper-")) {
    val voice = v.name.removePrefix("sherpa-piper-").replace('-', ' ')
    return listOfNotNull("Piper $voice", chimeTtsLocaleLabel(v), chimeTtsQualityLabel(v)).joinToString(" - ")
  }
  val gender = when {
    n.contains("female") -> "Female"
    n.contains("male") -> "Male"
    else -> null
  }
  val num = Regex("(\\d+)").find(n)?.value
  val base = listOfNotNull(gender, num?.let { "voice $it" }).joinToString(" ").ifBlank { v.name }
  val region = v.locale.country.ifBlank { v.locale.language }.uppercase()
  return if (region.isNotBlank()) "$base · $region" else base
}

/** Two-option chooser for the sunrise sound ("Morning" / "Rooster"). */
@Composable
private fun SunriseVariantPicker(current: Int, onPick: (Int) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Sunrise sound", color = Color(0xFFDDDDDD), fontSize = 15.sp, modifier = Modifier.weight(1f))
    SunsetChip("Morning", current == 0) { onPick(0) }
    Spacer(Modifier.size(8.dp))
    SunsetChip("Rooster", current == 1) { onPick(1) }
  }
}

@Composable
private fun SunsetChip(label: String, active: Boolean, onClick: () -> Unit) {
  Surface(
      color = if (active) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.tvFocusable(RoundedCornerShape(12.dp), focusScale = 1f) { onClick() },
  ) {
    Text(
        label,
        color = if (active) Color.White else Color(0xFFDADADA),
        fontSize = 14.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
    )
  }
}

@Composable
private fun VolumeStepper(label: String, volume: Int, onChange: (Int) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = Color(0xFFDDDDDD), fontSize = 15.sp, modifier = Modifier.weight(1f))
    StepArrow("◀") { onChange((volume - 10).coerceIn(0, 100)) }
    Text(
        "$volume%",
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 56.dp),
    )
    StepArrow("▶") { onChange((volume + 10).coerceIn(0, 100)) }
  }
}

@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
  fun fmt(m: Int): String {
    val h = (m / 60) % 24
    val mm = m % 60
    return String.format("%02d:%02d", h, mm)
  }
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
    StepArrow("◀") { onChange(((minuteOfDay - 30 + 1440) % 1440)) }
    Text(
        fmt(minuteOfDay),
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(min = 72.dp),
    )
    StepArrow("▶") { onChange(((minuteOfDay + 30) % 1440)) }
  }
}

@Composable
private fun StepArrow(glyph: String, onClick: () -> Unit) {
  Box(
      modifier = Modifier.size(48.dp).clickable { onClick() },
      contentAlignment = Alignment.Center,
  ) {
    Text(glyph, color = Color(0xFFDDDDDD), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
  }
}
