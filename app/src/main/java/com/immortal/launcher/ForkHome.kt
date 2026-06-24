/*
 * Fork home-screen extras (godric fork) extracted from the pre-1.49 HomeActivity when
 * upstream's free-placement grid was adopted. Tiles/overlays/tool-folders/backgrounds
 * that upstream's HomeActivity does not provide, wired back in from HomeActivity.
 */
package com.immortal.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.StatFs
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageInstaller
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.immortal.launcher.ui.theme.SampleAppTheme
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.app.ActivityManager
import android.media.AudioManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.view.KeyEvent
import android.view.SoundEffectConstants
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import java.io.File
import kotlinx.coroutines.launch

@Composable
private fun ClockIcon() {
  Canvas(modifier = Modifier.size(30.dp)) {
    val w = size.minDimension
    val s = w * 0.075f
    val stroke =
        Stroke(
            width = s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    // Clock face
    drawCircle(
        color = Color.White,
        radius = w * 0.42f,
        center = Offset(w * 0.5f, w * 0.5f),
        style = stroke,
    )
    // Hour hand (pointing to 12)
    drawLine(
        color = Color.White,
        start = Offset(w * 0.5f, w * 0.5f),
        end = Offset(w * 0.5f, w * 0.22f),
        strokeWidth = s,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    // Minute hand (pointing to 3)
    drawLine(
        color = Color.White,
        start = Offset(w * 0.5f, w * 0.5f),
        end = Offset(w * 0.72f, w * 0.5f),
        strokeWidth = s,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
  }
}

/** White crescent moon glyph for the header sleep button. */
@Composable
private fun MoonIcon() {
  val path = remember { PathParser().parsePathString(ICON_MOON).toPath() }
  Canvas(modifier = Modifier.size(28.dp)) {
    scale(size.minDimension / 24f) {
      drawPath(path, color = Color.White)
    }
  }
}

/** White sun glyph for the header brightness button. */
@Composable
private fun SunIcon() {
  Canvas(modifier = Modifier.size(28.dp)) {
    val w = size.minDimension
    val s = w * 0.08f
    val stroke = Stroke(width = s, cap = StrokeCap.Round)
    drawCircle(Color.White, radius = w * 0.20f, center = Offset(w * 0.5f, w * 0.5f), style = stroke)
    for (i in 0..7) {
      val a = Math.toRadians(i * 45.0)
      val cos = kotlin.math.cos(a).toFloat()
      val sin = kotlin.math.sin(a).toFloat()
      drawLine(Color.White,
          Offset(w * 0.5f + cos * w * 0.30f, w * 0.5f + sin * w * 0.30f),
          Offset(w * 0.5f + cos * w * 0.44f, w * 0.5f + sin * w * 0.44f),
          strokeWidth = s, cap = StrokeCap.Round)
    }
  }
}

/** White speaker glyph for the header volume button. */
@Composable
private fun VolumeIcon() {
  Canvas(modifier = Modifier.size(28.dp)) {
    val w = size.minDimension
    val s = w * 0.08f
    val stroke = Stroke(width = s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val body = Path().apply {
      moveTo(w * 0.46f, w * 0.22f)
      lineTo(w * 0.24f, w * 0.37f)
      lineTo(w * 0.08f, w * 0.37f)
      lineTo(w * 0.08f, w * 0.63f)
      lineTo(w * 0.24f, w * 0.63f)
      lineTo(w * 0.46f, w * 0.78f)
      close()
    }
    drawPath(body, Color.White, style = stroke)
    drawArc(Color.White, -35f, 70f, false,
        topLeft = Offset(w * 0.50f, w * 0.31f), size = Size(w * 0.20f, w * 0.38f), style = stroke)
    drawArc(Color.White, -50f, 100f, false,
        topLeft = Offset(w * 0.55f, w * 0.18f), size = Size(w * 0.34f, w * 0.64f), style = stroke)
  }
}

/** Bottom-right Manage / Done toggle. */
@Composable

private fun DashboardPage() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val use24 = remember { ImmortalSettings.use24HourClock(context) }
  var now by remember { mutableStateOf(java.util.Date()) }
  LaunchedEffect(Unit) {
    while (true) {
      now = java.util.Date()
      delay(10_000)
    }
  }
  val timeFmt = remember(use24) { SimpleDateFormat(if (use24) "HH:mm" else "h:mm", Locale.getDefault()) }
  // Live widget toggles: the chooser at the bottom flips these and persists them, so
  // the page updates instantly without a trip to Settings. Weather is intentionally
  // absent here — it lives at the bottom of the main page.
  var calendarOn by remember { mutableStateOf(ImmortalSettings.load(context).calendarWidget == ImmortalSettings.CALENDAR_ON) }
  var statsOn by remember { mutableStateOf(ImmortalSettings.load(context).statsMode == ImmortalSettings.STATS_ON) }
  var timeProgressOn by remember { mutableStateOf(ImmortalSettings.load(context).showTimeProgress) }
  var clockOn by remember { mutableStateOf(ImmortalSettings.load(context).showDashClock) }
  var countdownsOn by remember { mutableStateOf(ImmortalSettings.load(context).showDashCountdowns) }
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.size(8.dp))
    if (clockOn) {
      Text(timeFmt.format(now), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
      Text(
          SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
          color = Color(0xFFDADADA),
          fontSize = 22.sp,
      )
    }
    Spacer(Modifier.size(20.dp))
    if (countdownsOn) CountdownChips()
    if (timeProgressOn) {
      Spacer(Modifier.size(12.dp))
      TimeProgressCard()
    }
    if (calendarOn) {
      Spacer(Modifier.size(12.dp))
      CalendarWidget()
    }
    if (statsOn) {
      Spacer(Modifier.size(12.dp))
      SystemStatsWidget()
    }
    Spacer(Modifier.size(20.dp))
    // Widget chooser: add/remove the dashboard widgets right here.
    DashboardWidgetChooser(
        clockOn = clockOn,
        countdownsOn = countdownsOn,
        calendarOn = calendarOn,
        statsOn = statsOn,
        timeProgressOn = timeProgressOn,
        onClock = {
          clockOn = it
          ImmortalSettings.setShowDashClock(context, it)
        },
        onCountdowns = {
          countdownsOn = it
          ImmortalSettings.setShowDashCountdowns(context, it)
        },
        onCalendar = {
          calendarOn = it
          ImmortalSettings.setCalendarWidget(context, if (it) ImmortalSettings.CALENDAR_ON else ImmortalSettings.CALENDAR_OFF)
        },
        onStats = {
          statsOn = it
          ImmortalSettings.setStatsMode(context, if (it) ImmortalSettings.STATS_ON else ImmortalSettings.STATS_OFF)
        },
        onTimeProgress = {
          timeProgressOn = it
          ImmortalSettings.setShowTimeProgress(context, it)
        },
    )
    Spacer(Modifier.size(20.dp))
  }
}

/** Inline chooser at the bottom of the dashboard page for toggling its widgets. */
@Composable
private fun DashboardWidgetChooser(
    clockOn: Boolean,
    countdownsOn: Boolean,
    calendarOn: Boolean,
    statsOn: Boolean,
    timeProgressOn: Boolean,
    onClock: (Boolean) -> Unit,
    onCountdowns: (Boolean) -> Unit,
    onCalendar: (Boolean) -> Unit,
    onStats: (Boolean) -> Unit,
    onTimeProgress: (Boolean) -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Edit this page", color = Color(0xFF8A8A8A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.size(4.dp))
    Text("Tap to show or hide each widget", color = Color(0xFF6A6A6A), fontSize = 12.sp)
    Spacer(Modifier.size(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(0.9f),
    ) {
      WidgetToggleChip("🕑  Clock", clockOn) { onClock(!clockOn) }
      WidgetToggleChip("🎈  Countdowns", countdownsOn) { onCountdowns(!countdownsOn) }
      WidgetToggleChip("📅  Calendar", calendarOn) { onCalendar(!calendarOn) }
      WidgetToggleChip("📊  System stats", statsOn) { onStats(!statsOn) }
      WidgetToggleChip("⏳  Time progress", timeProgressOn) { onTimeProgress(!timeProgressOn) }
    }
  }
}

@Composable
private fun WidgetToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
  Surface(
      color = if (active) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.tvFocusable(RoundedCornerShape(20.dp), focusScale = 1f) { onClick() },
  ) {
    Text(
        (if (active) "✓  " else "+  ") + label,
        color = if (active) Color.White else Color(0xFFDADADA),
        fontSize = 15.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
  }
}

/** Category filter tabs: "All" plus each folder. Selecting one filters the grid to
 * that folder's apps; "All" restores the normal home view. */
@Composable

private fun CategoryTabs(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TabChip("All", selected == null) { onSelect(null) }
    categories.forEach { name -> TabChip(name, selected == name) { onSelect(name) } }
  }
}

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
  Surface(
      color = if (active) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.tvFocusable(RoundedCornerShape(20.dp), focusScale = 1f) { onClick() },
  ) {
    Text(
        label,
        color = if (active) Color.White else Color(0xFFDADADA),
        fontSize = 15.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
    )
  }
}

/** A horizontally-scrolling row of countdown chips, soonest first. Hidden when the
 * user hasn't created any. Re-read on each composition of the home screen. */
@Composable
private fun CountdownChips(version: Int = 0) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val events = remember(version) { CountdownConfig.loadSorted(context) }
  if (events.isEmpty()) return
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 18.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    events.forEach { e ->
      Surface(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
          shape = RoundedCornerShape(14.dp),
      ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("${e.emoji} ", fontSize = 16.sp)
          Text(e.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
          Text("  ·  ${e.phrase()}", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
        }
      }
    }
  }
  Spacer(Modifier.size(14.dp))
}

/** Internal tab keys for the built-in "Apps" and "Tools" category chips. These
 *  are filtered out of the dynamic [tabs] list so they aren't rendered twice. */
private const val TAB_APPS = "Apps"
private const val TAB_TOOLS = "Tools"

/** The single home control/info strip: category tabs, then countdown chips, then
 *  live kitchen timers + a "+ Timer" chip — all on one horizontally-scrolling line
 *  so nothing stacks. Tapping a timer cancels it. Leans on [CountdownConfig],
 *  [TimerConfig], [ChimePlayer]. */
@Composable
internal fun HomeControlStrip(
    showTabs: Boolean,
    tabs: List<String>,
    selectedTab: String?,
    onSelectTab: (String?) -> Unit,
    countdownVersion: Int,
    timerVersion: Int,
    onTimerChanged: () -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) { while (true) { nowTick = System.currentTimeMillis(); delay(1000) } }
  val countdowns = remember(countdownVersion) { CountdownConfig.loadSorted(context) }
  // Re-read once per second so timers the background receiver removed disappear too.
  val timers = remember(timerVersion, nowTick / 1000) {
    TimerConfig.load(context).sortedBy { it.endAtMillis }
  }
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 18.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showTabs) {
      TabChip("All", selectedTab == null) { onSelectTab(null) }
      TabChip("Apps", selectedTab == TAB_APPS) { onSelectTab(TAB_APPS) }
      TabChip("Tools", selectedTab == TAB_TOOLS) { onSelectTab(TAB_TOOLS) }
      tabs.filter { it != TAB_APPS && it != TAB_TOOLS }
          .forEach { name -> TabChip(name, selectedTab == name) { onSelectTab(name) } }
      // Thin separator between navigation tabs and the info/timer chips.
      Box(Modifier.size(width = 1.dp, height = 22.dp).background(Color(0x33FFFFFF)))
    }
    countdowns.forEach { e ->
      Surface(
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
          shape = RoundedCornerShape(14.dp),
      ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("${e.emoji} ", fontSize = 16.sp)
          Text(e.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
          Text("  ·  ${e.phrase()}", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
        }
      }
    }
    timers.forEach { t ->
      val rem = (t.endAtMillis - nowTick).coerceAtLeast(0)
      Surface(
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
            TimerConfig.remove(context, t.id); onTimerChanged()
          },
      ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("⏲ ", fontSize = 16.sp)
          Text(t.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
          Text("  ${formatTimerRemaining(rem)}", color = Color(0xFFEAF1FF), fontSize = 15.sp)
          Text("   ✕", color = Color(0xCCFFFFFF), fontSize = 14.sp)
        }
      }
    }
  }
  Spacer(Modifier.size(12.dp))
}

/** mm:ss, or h:mm:ss past an hour. */
private fun formatTimerRemaining(ms: Long): String {
  val total = (ms / 1000).toInt()
  val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Modal to add a named kitchen timer: preset names (each with a sensible default
 *  duration) + a minute picker. */
@Composable
private fun AddTimerOverlay(onDismiss: () -> Unit, onAdd: (String, Long) -> Unit) {
  var label by remember { mutableStateOf("") }
  var minutes by remember { mutableStateOf(5) }
  // Common kitchen presets → (name, default minutes).
  val presets = listOf("Pasta" to 10, "Eggs" to 7, "Tea" to 4, "Oven" to 20, "Rice" to 15)
  val quick = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 640.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("New timer", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Name (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          presets.forEach { (name, mins) ->
            WidgetToggleChip(name, label == name) { label = name; minutes = mins }
          }
        }
        Text("Duration", color = Color(0xFF9A9A9A), fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          quick.forEach { m -> WidgetToggleChip("${m}m", minutes == m) { minutes = m } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(12.dp),
              modifier = Modifier.tvFocusable(RoundedCornerShape(12.dp), focusScale = 1f) {
                minutes = (minutes - 1).coerceAtLeast(1) }) {
            Text("−", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
          }
          Text("$minutes min", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
          Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(12.dp),
              modifier = Modifier.tvFocusable(RoundedCornerShape(12.dp), focusScale = 1f) {
                minutes = (minutes + 1).coerceAtMost(600) }) {
            Text("+", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
                onAdd(label.ifBlank { "Timer" }, minutes * 60_000L)
              }) {
            Text("Start", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth())
          }
          Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
            Text("Cancel", color = Color.White, fontSize = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth())
          }
        }
      }
    }
  }
}

private data class SystemStats(
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val storageUsedGb: Float,
    val storageTotalGb: Float,
    val uptimeHours: Float,
    val rxKBs: Float = 0f,
    val txKBs: Float = 0f,
    val ip: String = "",
    val cpuPercent: Int = 0,
    val tempC: Float = 0f,
)

@Composable
private fun SystemStatsWidget() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val reading = remember { mutableStateOf(SystemStats(0, 0, 0f, 0f, 0f)) }

  LaunchedEffect(Unit) {
    var prevRx = TrafficStats.getTotalRxBytes()
    var prevTx = TrafficStats.getTotalTxBytes()
    var prevTime = System.currentTimeMillis()
    var prevCpuIdle = 0L; var prevCpuTotal = 0L
    while (true) {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val mem = ActivityManager.MemoryInfo()
      am.getMemoryInfo(mem)
      val usedMb = ((mem.totalMem - mem.availMem) / 1_048_576L).toInt()
      val totalMb = (mem.totalMem / 1_048_576L).toInt()
      val uptimeHours =
          runCatching { android.os.SystemClock.elapsedRealtime() / 3_600_000f }.getOrDefault(0f)

      val stat = StatFs(context.filesDir.absolutePath)
      val totalBytes = stat.totalBytes.toDouble()
      val freeBytes = stat.availableBytes.toDouble()
      val totalGb = (totalBytes / 1_073_741_824.0).toFloat()
      val usedGb = ((totalBytes - freeBytes) / 1_073_741_824.0).toFloat().coerceAtLeast(0f)

      val nowRx = TrafficStats.getTotalRxBytes()
      val nowTx = TrafficStats.getTotalTxBytes()
      val nowTime = System.currentTimeMillis()
      val elapsedSec = ((nowTime - prevTime) / 1000f).coerceAtLeast(1f)
      val rxKBs = ((nowRx - prevRx).coerceAtLeast(0L) / 1024f) / elapsedSec
      val txKBs = ((nowTx - prevTx).coerceAtLeast(0L) / 1024f) / elapsedSec
      prevRx = nowRx; prevTx = nowTx; prevTime = nowTime

      // CPU %: delta of /proc/stat idle vs total between polls.
      val cpuPercent = withContext(Dispatchers.IO) {
        runCatching {
          val parts = File("/proc/stat").bufferedReader().use { it.readLine() }
              .trim().split("\\s+".toRegex()).drop(1)
          val idle = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L
          val total = parts.take(8).sumOf { it.toLongOrNull() ?: 0L }
          val cpu = if (prevCpuTotal > 0) {
            val dIdle = (idle - prevCpuIdle).coerceAtLeast(0L)
            val dTotal = (total - prevCpuTotal).coerceAtLeast(1L)
            ((1.0 - dIdle.toDouble() / dTotal.toDouble()) * 100).toInt().coerceIn(0, 100)
          } else 0
          prevCpuIdle = idle; prevCpuTotal = total
          cpu
        }.getOrDefault(0)
      }

      // Device temp from thermal zone (millidegrees on most kernels).
      val tempC = withContext(Dispatchers.IO) {
        runCatching {
          val raw = File("/sys/class/thermal/thermal_zone0/temp").readText().trim().toFloat()
          if (raw > 1000f) raw / 1000f else raw
        }.getOrDefault(0f)
      }

      // Wi-Fi IP address.
      @Suppress("DEPRECATION")
      val ip = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wm.connectionInfo.ipAddress
        if (ipInt == 0) "" else String.format("%d.%d.%d.%d",
            ipInt and 0xff, (ipInt shr 8) and 0xff, (ipInt shr 16) and 0xff, (ipInt shr 24) and 0xff)
      }.getOrDefault("")

      reading.value = SystemStats(usedMb, totalMb, usedGb, totalGb, uptimeHours, rxKBs, txKBs, ip, cpuPercent, tempC)
      delay(10_000)
    }
  }

  val r = reading.value
  Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      val ramPercent =
          if (r.ramTotalMb > 0) (r.ramUsedMb * 100 / r.ramTotalMb).coerceIn(0, 100) else 0
      val storagePercent =
          if (r.storageTotalGb > 0f) (r.storageUsedGb * 100 / r.storageTotalGb).coerceIn(0f, 100f)
          else 0f

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("System", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Text(
            String.format("Uptime %.1fh", r.uptimeHours),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("RAM", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        Text(
            "${r.ramUsedMb}/${r.ramTotalMb} MB · $ramPercent%",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Storage", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        Text(
            String.format("%.1f/%.1f GB · %.0f%%", r.storageUsedGb, r.storageTotalGb, storagePercent),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Network", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        Text(
            "↓ ${formatNetRate(r.rxKBs)}  ↑ ${formatNetRate(r.txKBs)}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
      }
      if (r.ip.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("IP", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
          Text(r.ip, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        }
      }
      if (r.cpuPercent > 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("CPU", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
          Text("${r.cpuPercent}%", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        }
      }
      if (r.tempC > 0f) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Temp", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
          Text(String.format("%.1f °C", r.tempC), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f))
        }
      }
    }
  }
}

@Composable
private fun NewFolderButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
  Surface(
      color = MaterialTheme.colorScheme.primary,
      shape = androidx.compose.foundation.shape.CircleShape,
      modifier =
          modifier.size(60.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
            onClick()
          },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    }
  }
}

internal data class BatteryReading(val present: Boolean, val percent: Int, val charging: Boolean)

/** Reads the device battery, updating live. Returns present=false on devices
 * without a battery (Portal+, Portal TV, Portal Mini), so callers can hide it. */
@Composable
private fun formatNetRate(kbs: Float): String = when {
  kbs >= 1024f -> String.format("%.1f MB/s", kbs / 1024f)
  kbs >= 1f    -> String.format("%.0f KB/s", kbs)
  else         -> "0 KB/s"
}

@Composable
private fun batteryState(): BatteryReading {
  val context = androidx.compose.ui.platform.LocalContext.current
  var reading by remember { mutableStateOf(BatteryReading(false, 0, false)) }
  DisposableEffect(Unit) {
    fun parse(i: Intent?): BatteryReading {
      if (i == null) return BatteryReading(false, 0, false)
      val present = i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
      val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
      val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
      val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
      val charging =
          status == BatteryManager.BATTERY_STATUS_CHARGING ||
              status == BatteryManager.BATTERY_STATUS_FULL
      return BatteryReading(present && pct >= 0, pct.coerceIn(0, 100), charging)
    }
    val receiver =
        object : android.content.BroadcastReceiver() {
          override fun onReceive(c: Context, i: Intent) {
            reading = parse(i)
          }
        }
    // Registering for the sticky ACTION_BATTERY_CHANGED returns the current value.
    val sticky =
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    reading = parse(sticky)
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }
  return reading
}

/** Minimal drawn battery glyph + percent, green while charging, red when low. */
@Composable
private fun BatteryIndicator(percent: Int, charging: Boolean) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Canvas(modifier = Modifier.size(width = 36.dp, height = 18.dp)) {
      val cap = 3.dp.toPx()
      val bodyW = size.width - cap
      val stroke = 2.dp.toPx()
      drawRoundRect(
          color = Color.White,
          topLeft = Offset(stroke / 2, stroke / 2),
          size = Size(bodyW - stroke, size.height - stroke),
          cornerRadius = CornerRadius(4f, 4f),
          style = Stroke(width = stroke),
      )
      drawRoundRect(
          color = Color.White,
          topLeft = Offset(bodyW, size.height * 0.3f),
          size = Size(cap, size.height * 0.4f),
          cornerRadius = CornerRadius(2f, 2f),
      )
      val inset = stroke + 2.dp.toPx()
      val fillColor =
          when {
            charging -> Color(0xFF4CAF50)
            percent <= 15 -> Color(0xFFE53935)
            else -> Color.White
          }
      drawRoundRect(
          color = fillColor,
          topLeft = Offset(inset, inset),
          size =
              Size(
                  ((bodyW - inset * 2) * (percent / 100f)).coerceAtLeast(0f),
                  size.height - inset * 2,
              ),
          cornerRadius = CornerRadius(2f, 2f),
      )
    }
    Text("$percent%", color = Color.White, fontSize = 22.sp)
    if (charging) Text("⚡", color = Color(0xFF4CAF50), fontSize = 16.sp)
  }
}


@Composable
private fun PortalHomeShortcutTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "Portal",
      background = Color(0xFF1877F2),
      glyph = ICON_PORTAL,
      onClick = onClick,
  )
}

/**
 * Camera tile: opens a full-screen camera preview showing the Portal's
 * front-facing camera feed. Tap anywhere to exit.
 */
@Composable
private fun CameraTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "Camera",
      background = Color(0xFF9C27B0),
      glyph = ICON_CAMERA,
      onClick = onClick,
  )
}

@Composable
private fun SpeedTestTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "Speed Test",
      background = Color(0xFF1565C0),
      glyph = ICON_SPEEDTEST,
      onClick = onClick,
  )
}

@Composable
private fun TransitTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "Transit",
      background = Color(0xFF00695C),
      glyph = ICON_BUS,
      onClick = onClick,
  )
}

/** Opens myNoise.net (Stéphane Pigeon's online ambient-sound generators) in the
 *  browser. We can't bundle/stream its (copyrighted) audio, but a link tile lets the
 *  user reach its full library directly. */
@Composable
private fun MyNoiseTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  BuiltInTile(
      label = "myNoise",
      background = Color(0xFF37474F),
      glyph = ICON_HEADPHONES,
      onClick = {
        runCatching {
          context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("https://mynoise.net/noiseMachines.php"))
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
      },
  )
}

/** Worldwide real-time departures board (Transitous / MOTIS). The user searches for a
 *  stop by name anywhere in the world, picks it, and sees the next departures,
 *  auto-refreshing while open. */
@Composable
private fun TransitOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var stopId by remember { mutableStateOf(Transit.savedStopId(context)) }
  var stopName by remember { mutableStateOf(Transit.savedStopName(context)) }
  var editing by remember { mutableStateOf(Transit.savedStopId(context).isBlank()) }
  var query by remember { mutableStateOf("") }
  var results by remember { mutableStateOf<List<Transit.Stop>>(emptyList()) }
  var searching by remember { mutableStateOf(false) }
  var departures by remember { mutableStateOf<List<Transit.Departure>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }

  // Debounced stop search while typing.
  LaunchedEffect(query, editing) {
    if (editing && query.trim().length >= 2) {
      delay(350)
      searching = true
      results = withContext(Dispatchers.IO) { Transit.searchStops(query) }
      searching = false
    } else if (query.trim().length < 2) {
      results = emptyList()
    }
  }

  LaunchedEffect(stopId, editing) {
    if (!editing && stopId.isNotBlank()) {
      while (true) {
        loading = true
        departures = withContext(Dispatchers.IO) { Transit.fetch(stopId) }
        loading = false
        delay(30_000)
      }
    }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("🚌 Next departures", color = Color.White, fontSize = 22.sp,
              fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          if (!editing) {
            Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp), focusScale = 1f) {
                  editing = true; query = ""; results = emptyList()
                }) {
              Text("Change stop", color = Color.White, fontSize = 13.sp,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
          }
        }
        if (editing) {
          androidx.compose.material3.OutlinedTextField(
              value = query,
              onValueChange = { query = it },
              label = { Text("Search stop or station") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Text(
              "Type a stop or station name anywhere in the world — e.g. \"Connolly\", " +
                  "\"Alexanderplatz\", \"Times Sq 42 St\".",
              color = Color(0xFF9A9A9A), fontSize = 13.sp,
          )
          when {
            searching -> Text("Searching…", color = Color(0xFFB0B0B0), fontSize = 15.sp)
            results.isEmpty() && query.trim().length >= 2 ->
                Text("No stops found. Try a different name.", color = Color(0xFFB0B0B0), fontSize = 15.sp)
            else ->
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  results.forEach { stop ->
                    Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                            .tvFocusableRow {
                              Transit.saveStop(context, stop)
                              stopId = stop.id; stopName = stop.name; editing = false
                            }) {
                      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(stop.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (stop.region.isNotBlank())
                            Text(stop.region, color = Color(0xFF9A9A9A), fontSize = 13.sp, maxLines = 1)
                      }
                    }
                  }
                }
          }
        } else {
          Text(stopName.ifBlank { "Selected stop" }, color = Color(0xFF9A9A9A), fontSize = 14.sp)
          if (departures.isEmpty()) {
            Text(if (loading) "Loading…" else "No departures right now.",
                color = Color(0xFFB0B0B0), fontSize = 16.sp)
          } else {
            departures.forEach { d ->
              Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                  Text(d.route, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                      maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text("  ${d.destination}", color = Color.White, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(d.due, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun IssTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "ISS Pass",
      background = Color(0xFF1A237E),
      glyph = ICON_SATELLITE,
      onClick = onClick,
  )
}

/** "When does the space station fly over?" overlay. Lists the next visible/overhead
 *  passes for the device's location — start time, the direction it rises and sets,
 *  how high it climbs, and a ✨ badge when it'll be bright enough to actually spot.
 *  All computed on-device (SGP4) after a single keyless TLE fetch. */
@Composable
private fun IssOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var passes by remember { mutableStateOf<List<IssPasses.Pass>?>(null) }

  LaunchedEffect(Unit) {
    passes = withContext(Dispatchers.IO) { IssPasses.predict(context) }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🛰️ Space station overhead", color = Color.White, fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
        val p = passes
        when {
          p == null ->
              Text("Finding passes…", color = Color(0xFFB0B0B0), fontSize = 16.sp)
          p.isEmpty() ->
              Text(
                  "No passes found. Check the device is online so it can fetch the latest " +
                      "orbit, and that your location is set (it follows the weather tile).",
                  color = Color(0xFFB0B0B0), fontSize = 15.sp)
          else -> {
            // Lead with the headline the way you'd say it out loud.
            val first = p.first()
            Text(
                buildString {
                  append(if (first.visible) "Visible pass " else "Passes over ")
                  append(IssPasses.timeLabel(first.startMillis))
                  append(if (first.visible) " ✨" else "")
                },
                color = if (first.visible) Color(0xFFFFD54F) else Color.White,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              p.forEach { pass ->
                Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                  Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(IssPasses.timeLabel(pass.startMillis), color = Color.White,
                          fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                          modifier = Modifier.weight(1f))
                      if (pass.visible) {
                        Surface(color = Color(0x33FFD54F), shape = RoundedCornerShape(8.dp)) {
                          Text("✨ visible", color = Color(0xFFFFD54F), fontSize = 13.sp,
                              fontWeight = FontWeight.SemiBold,
                              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                      }
                    }
                    Text(
                        "Rises ${pass.startDir} · peak ${pass.maxElevationDeg}° to the " +
                            "${pass.peakDir} · sets ${pass.endDir}",
                        color = Color(0xFFB8B8B8), fontSize = 14.sp)
                  }
                }
              }
            }
            Text(
                "Times are local. ✨ means it should be bright enough to see — go outside " +
                    "and look up to the listed direction.",
                color = Color(0xFF8A8A8A), fontSize = 12.sp)
          }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

/** Aurora outlook tile — it stays a dim slate when there's nothing to see, and lights
 *  up green (with the Kp number) when the oval is reaching, or near, your latitude.
 *  Reads the outlook once in the background so the home grid never blocks on network. */
@Composable
private fun AuroraTile(onClick: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var status by remember { mutableStateOf<Aurora.Status?>(null) }
  LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { Aurora.status(context) } }
  val st = status
  val background =
      when (st?.chance) {
        Aurora.Chance.LIKELY -> Color(0xFF00C853) // vivid: go outside
        Aurora.Chance.POSSIBLE -> Color(0xFF2E7D32)
        Aurora.Chance.SLIM -> Color(0xFF37474F)
        else -> Color(0xFF263238) // none / unknown: dim
      }
  val label =
      when (st?.chance) {
        Aurora.Chance.LIKELY,
        Aurora.Chance.POSSIBLE -> "Aurora Kp${Aurora.fmtKp(st.kpForecast)}"
        else -> "Aurora"
      }
  BuiltInTile(label = label, background = background, glyph = ICON_AURORA, onClick = onClick)
}

/** "Any chance of northern/southern lights here tonight?" overlay — the Kp now + the
 *  24 h forecast peak, what it means at this device's geomagnetic latitude, and which
 *  way to look. Computed on-device after the keyless NOAA SWPC fetch. */
@Composable
private fun AuroraOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var status by remember { mutableStateOf<Aurora.Status?>(null) }
  var loaded by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    status = withContext(Dispatchers.IO) { Aurora.status(context) }
    loaded = true
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🌌 Aurora outlook", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val st = status
        when {
          !loaded -> Text("Checking the K-index…", color = Color(0xFFB0B0B0), fontSize = 16.sp)
          st == null ->
              Text(
                  "Couldn't fetch the K-index. Check the device is online and that your " +
                      "location is set (it follows the weather tile).",
                  color = Color(0xFFB0B0B0), fontSize = 15.sp)
          else -> {
            val accent =
                when (st.chance) {
                  Aurora.Chance.LIKELY -> Color(0xFF69F0AE)
                  Aurora.Chance.POSSIBLE -> Color(0xFFB9F6CA)
                  Aurora.Chance.SLIM -> Color(0xFFB0BEC5)
                  Aurora.Chance.NONE -> Color(0xFF90A4AE)
                }
            Text(st.headline, color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(st.detail, color = Color(0xFFCFCFCF), fontSize = 15.sp, lineHeight = 21.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
              AuroraStat("Kp now", Aurora.fmtKp(st.kpNow), Modifier.weight(1f))
              AuroraStat("Next 24 h peak", Aurora.fmtKp(st.kpForecast), Modifier.weight(1f))
              AuroraStat("Look", st.lookToward, Modifier.weight(1f))
            }
            Text(
                "Source: NOAA SWPC planetary K-index. Best after dark, away from city lights, " +
                    "with a clear ${if (st.lookToward == "N") "northern" else "southern"} horizon.",
                color = Color(0xFF8A8A8A), fontSize = 12.sp)
          }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun AuroraStat(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp), modifier = modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
    ) {
      Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      Text(label, color = Color(0xFF9A9A9A), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun StopwatchTile(onClick: () -> Unit) {
  BuiltInTile(label = "Stopwatch", background = Color(0xFF455A64), glyph = ICON_STOPWATCH, onClick = onClick)
}

/** A count-up stopwatch with lap marks — for workouts, steeping, anything. Runs while
 *  the overlay is open; it's a quick tool, not a persisted timer (that's the chips). */
@Composable
private fun StopwatchOverlay(onDismiss: () -> Unit) {
  var running by remember { mutableStateOf(false) }
  // accumulated = time banked from previous runs; startedAt = when the current run began.
  var accumulated by remember { mutableStateOf(0L) }
  var startedAt by remember { mutableStateOf(0L) }
  var nowTick by remember { mutableStateOf(0L) }
  val laps = remember { mutableStateListOf<Long>() }

  val elapsed = accumulated + if (running) (nowTick - startedAt).coerceAtLeast(0L) else 0L

  LaunchedEffect(running) {
    while (running) {
      nowTick = System.currentTimeMillis()
      delay(31)
    }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 520.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⏱ Stopwatch", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(formatStopwatch(elapsed), color = Color.White, fontSize = 56.sp,
            fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          StopwatchButton(if (running) "Pause" else "Start",
              if (running) Color(0xFFB0BEC5) else Color(0xFF00C853)) {
            if (running) {
              accumulated = elapsed
              running = false
            } else {
              startedAt = System.currentTimeMillis()
              nowTick = startedAt
              running = true
            }
          }
          StopwatchButton(if (running) "Lap" else "Reset", Color(0xFF455A64)) {
            if (running) laps.add(0, elapsed) else { accumulated = 0L; laps.clear() }
          }
        }
        if (laps.isNotEmpty()) {
          Column(
              modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                  .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            laps.forEachIndexed { i, lap ->
              Row(modifier = Modifier.fillMaxWidth()) {
                Text("Lap ${laps.size - i}", color = Color(0xFF9A9A9A), fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Text(formatStopwatch(lap), color = Color.White, fontSize = 15.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
              }
            }
          }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun StopwatchButton(label: String, color: Color, onClick: () -> Unit) {
  Surface(color = color, shape = RoundedCornerShape(14.dp),
      modifier = Modifier.width(130.dp).tvFocusable(RoundedCornerShape(14.dp), focusScale = 1.04f) { onClick() }) {
    Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth())
  }
}

private fun formatStopwatch(ms: Long): String {
  val totalCs = ms / 10
  val cs = totalCs % 100
  val totalSec = totalCs / 100
  val s = totalSec % 60
  val m = totalSec / 60
  return "%02d:%02d.%02d".format(m, s, cs)
}

@Composable
private fun ConverterTile(onClick: () -> Unit) {
  BuiltInTile(label = "Convert", background = Color(0xFF00838F), glyph = ICON_CONVERT, onClick = onClick)
}

/** Quick unit + currency converter. Units convert instantly offline; the Currency
 *  category uses the keyless ECB feed (cached). */
@Composable
private fun ConverterOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val categories = remember { Converter.UNIT_CATEGORIES.map { it.first } + "Currency" }
  var category by remember { mutableStateOf("Length") }
  val units: List<String> =
      remember(category) {
        if (category == "Currency") Converter.CURRENCIES
        else Converter.UNIT_CATEGORIES.first { it.first == category }.second.keys.toList()
      }
  var from by remember(category) { mutableStateOf(units.first()) }
  var to by remember(category) { mutableStateOf(units.getOrElse(1) { units.first() }) }
  var input by remember { mutableStateOf("1") }
  var result by remember { mutableStateOf("") }

  // Recompute on any change. Currency is async (network/cache); units are instant.
  LaunchedEffect(category, from, to, input) {
    val v = input.toDoubleOrNull()
    if (v == null) { result = ""; return@LaunchedEffect }
    result =
        if (category == "Currency") {
          val r = withContext(Dispatchers.IO) { Converter.convertCurrency(context, from, to, v) }
          if (r == null) "—" else trimNum(r)
        } else {
          trimNum(Converter.convert(category, from, to, v))
        }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🔁 Converter", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        ChipRow(categories, category) { category = it }
        androidx.compose.material3.OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
            label = { Text("Value") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("From", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        ChipRow(units, from) { from = it }
        Text("To", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        ChipRow(units, to) { to = it }
        Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("$input $from =", color = Color(0xFF9A9A9A), fontSize = 14.sp)
            Text(if (result.isBlank()) "…" else "$result $to", color = Color.White,
                fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (category == "Currency")
                Text("ECB reference rate (cached).", color = Color(0xFF8A8A8A), fontSize = 12.sp)
          }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

/** A horizontally-scrolling single-select chip row. */
@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    options.forEach { opt ->
      val on = opt == selected
      Surface(
          color = if (on) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.tvFocusable(RoundedCornerShape(12.dp), focusScale = 1.04f) { onSelect(opt) },
      ) {
        Text(opt, color = Color.White, fontSize = 15.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
      }
    }
  }
}

private fun trimNum(d: Double): String {
  if (d.isNaN() || d.isInfinite()) return "—"
  val r = if (kotlin.math.abs(d) >= 1000 || d == d.toLong().toDouble()) "%,.2f".format(d)
          else "%.4g".format(d)
  return r.trimEnd('0').trimEnd('.').ifBlank { "0" }
}

@Composable
private fun WhatChangedTile(onClick: () -> Unit) {
  BuiltInTile(label = "What's New", background = Color(0xFF5D4037), glyph = ICON_HISTORY, onClick = onClick)
}

/** Recent self-updates, from the launcher's GitHub releases — so a device that improves
 *  itself isn't a black box. */
@Composable
private fun WhatChangedOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var releases by remember { mutableStateOf<List<WhatChanged.Release>?>(null) }
  val installedVersion = remember {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "?"
  }
  LaunchedEffect(Unit) { releases = withContext(Dispatchers.IO) { WhatChanged.recent() } }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 600.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🗒 What's new", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("This Immortal: $installedVersion", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        val r = releases
        when {
          r == null -> Text("Loading…", color = Color(0xFFB0B0B0), fontSize = 16.sp)
          r.isEmpty() ->
              Text("Couldn't load the changelog (offline, or no releases yet).",
                  color = Color(0xFFB0B0B0), fontSize = 15.sp)
          else ->
              Column(
                  modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                      .verticalScroll(rememberScrollState()),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                r.forEach { rel ->
                  Surface(color = Color(0x18FFFFFF), shape = RoundedCornerShape(12.dp),
                      modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rel.version, color = Color.White, fontSize = 17.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(rel.date, color = Color(0xFF9A9A9A), fontSize = 13.sp)
                      }
                      Text(rel.notes, color = Color(0xFFCFCFCF), fontSize = 14.sp,
                          lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                  }
                }
              }
        }
        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun LampTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  BuiltInTile(label = "Lamp", background = Color(0xFFFF8F00), glyph = ICON_LAMP, onClick = {
    runCatching { context.startActivity(Intent(context, LampActivity::class.java)) }
  })
}

@Composable
private fun BedtimeTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  BuiltInTile(label = "Story", background = Color(0xFF512DA8), glyph = ICON_BOOK, onClick = {
    runCatching { context.startActivity(Intent(context, BedtimeStoryActivity::class.java)) }
  })
}

@Composable
private fun SunriseTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  BuiltInTile(label = "Sunrise", background = Color(0xFFEF6C00), glyph = ICON_SUNRISE, onClick = {
    runCatching { context.startActivity(Intent(context, SunriseSettingsActivity::class.java)) }
  })
}

/** Opens a prefilled GitHub issue so the household can signal which apps they want —
 *  the demand board, with no server to run. */
@Composable
private fun RequestAppTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  BuiltInTile(label = "Request App", background = Color(0xFF263238), glyph = ICON_INBOX, onClick = {
    val url =
        "https://github.com/starbrightlab/immortal/issues/new?labels=app-request&title=" +
            Uri.encode("App request: ") +
            "&body=" +
            Uri.encode("Which app would you like on Portal?\n\nApp name:\nWhy:\n")
    runCatching {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  })
}

/** Ping another Portal in the house — taps send a chime + spoken room name to every
 *  Immortal device on the LAN. Shows a brief "Pinged!" confirmation. */
@Composable
private fun PingTile() {
  val context = androidx.compose.ui.platform.LocalContext.current
  var pinged by remember { mutableStateOf(false) }
  LaunchedEffect(pinged) {
    if (pinged) { delay(1500); pinged = false }
  }
  BuiltInTile(
      label = if (pinged) "Pinged!" else "Ping Room",
      background = if (pinged) Color(0xFF00C853) else Color(0xFF00897B),
      glyph = ICON_PING,
      onClick = {
        val name = ImmortalSettings.deviceRoomName(context)
        PingService.send(context, name)
        pinged = true
      },
  )
}

@Composable
private fun NoteTile(onClick: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val hasNote = remember { NotesConfig.loadText(context).isNotBlank() || NotesConfig.hasAudioNote(context) }
  BuiltInTile(
      label = if (hasNote) "Note •" else "Leave Note",
      background = Color(0xFFD4A017),
      glyph = ICON_NOTE,
      onClick = onClick,
  )
}

/** A pinned sticky-note card on the home screen, shown when a text and/or voice note
 * exists. Tapping the text opens the editor; the audio note plays inline. */
@Composable
private fun HomeNoteCard(version: Int, onEdit: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val text = remember(version) { NotesConfig.loadText(context) }
  val hasAudio = remember(version) { NotesConfig.hasAudioNote(context) }
  if (text.isBlank() && !hasAudio) return
  val audio = remember { AudioNote(context) }
  DisposableEffect(Unit) { onDispose { audio.release() } }
  var playing by remember { mutableStateOf(false) }
  Surface(
      color = Color(0xFFFFF3C4),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)
          .tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onEdit() },
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("📌", fontSize = 18.sp)
      Spacer(Modifier.size(10.dp))
      Text(
          text.ifBlank { "Voice note" },
          color = Color(0xFF3A2E00),
          fontSize = 16.sp,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      if (hasAudio) {
        Surface(
            color = Color(0xFFD4A017),
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(40.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape, focusScale = 1.05f) {
              if (playing) { audio.stopPlaying(); playing = false }
              else { playing = true; audio.play { playing = false } }
            },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 16.sp)
          }
        }
      }
    }
  }
  Spacer(Modifier.size(8.dp))
}

/** "Did you know" discoverability card — one tip a day, dismissible. Teaches the
 *  device's depth instead of hiding it. Hidden once dismissed until tomorrow's tip. */
@Composable
private fun DidYouKnowCard(version: Int) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var dismissed by remember(version) { mutableStateOf(Tips.isDismissedToday(context)) }
  if (dismissed) return
  val tip = remember { Tips.todaysTip() }
  Surface(
      color = Color(0xFF1C2A3A),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("💡", fontSize = 18.sp)
      Spacer(Modifier.size(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text("Did you know?", color = Color(0xFF8AB4F8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(tip, color = Color(0xFFDADADA), fontSize = 15.sp, lineHeight = 20.sp)
      }
      Spacer(Modifier.size(10.dp))
      Surface(
          color = Color(0x22FFFFFF),
          shape = androidx.compose.foundation.shape.CircleShape,
          modifier = Modifier.size(36.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape, focusScale = 1.05f) {
            Tips.dismissToday(context); dismissed = true
          },
      ) {
        Box(contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 15.sp) }
      }
    }
  }
  Spacer(Modifier.size(8.dp))
}

/** Editor overlay for the fridge note: a typed message plus a single voice memo. */
@Composable
private fun NoteOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var text by remember { mutableStateOf(NotesConfig.loadText(context)) }
  var hasAudio by remember { mutableStateOf(NotesConfig.hasAudioNote(context)) }
  val audio = remember { AudioNote(context) }
  var recording by remember { mutableStateOf(false) }
  var playing by remember { mutableStateOf(false) }
  var level by remember { mutableStateOf(0f) }
  var heardSignal by remember { mutableStateOf(false) }
  DisposableEffect(Unit) { onDispose { audio.release() } }

  // While recording, poll the mic peak so the user can see they're being heard.
  // The Portal only exposes the near-field handset mic, so a voice from across
  // the room reads as near-silence — this makes that visible instead of mysterious.
  LaunchedEffect(recording) {
    if (recording) {
      heardSignal = false
      while (recording) {
        val peak = (audio.peakLevel() / 12000f).coerceIn(0f, 1f)
        level = peak
        if (peak > 0.08f) heardSignal = true
        delay(120)
      }
      level = 0f
    }
  }

  // Runtime mic permission; start recording once granted.
  val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) { recording = audio.startRecording() }
  }
  fun toggleRecord() {
    if (recording) {
      audio.stopRecording(); recording = false; hasAudio = NotesConfig.hasAudioNote(context)
    } else {
      val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
          context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
      if (granted) recording = audio.startRecording()
      else permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Leave a note", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Type a note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(
              color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { toggleRecord() },
          ) {
            Text(if (recording) "■ Stop recording" else "● Record voice", color = Color.White,
                fontSize = 16.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
          }
          if (recording) {
            // Live input meter — fills as the mic hears you. Stays empty = speak closer.
            Box(
                modifier = Modifier.weight(1f).height(12.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(6.dp)),
            ) {
              Box(
                  modifier = Modifier.fillMaxHeight().fillMaxWidth(level)
                      .background(
                          if (level > 0.08f) Color(0xFF34C759) else Color(0xFFFF9F0A),
                          RoundedCornerShape(6.dp)),
              )
            }
          }
          if (hasAudio && !recording) {
            Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
                  if (playing) { audio.stopPlaying(); playing = false }
                  else { playing = true; audio.play { playing = false } }
                }) {
              Text(if (playing) "⏸ Stop" else "▶ Play", color = Color.White, fontSize = 16.sp,
                  modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
            Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
                  NotesConfig.clearAudio(context); hasAudio = false
                }) {
              Text("🗑", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
          }
        }
        if (recording && !heardSignal) {
          Text(
              "Speak close to the device — Portal only gives apps the near-field mic, " +
                  "so a voice from across the room barely registers.",
              color = Color(0xFFFF9F0A), fontSize = 13.sp,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
                NotesConfig.saveText(context, text)
                if (recording) { audio.stopRecording(); recording = false }
                onDismiss()
              }) {
            Text("Save", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth())
          }
          Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) {
                NotesConfig.clearText(context); NotesConfig.clearAudio(context)
                text = ""; hasAudio = false; onDismiss()
              }) {
            Text("Clear all", color = Color.White, fontSize = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth())
          }
        }
      }
    }
  }
}

@Composable
private fun DailyTile(mode: String, onClick: () -> Unit) {
  val label = when (DailyContent.modeOf(mode)) {
    DailyContent.Mode.WORD -> "Word"
    DailyContent.Mode.TRIVIA -> "Trivia"
    else -> "Quote"
  }
  BuiltInTile(
      label = "Daily $label",
      background = Color(0xFF7B5BD6),
      glyph = ICON_LIGHTBULB,
      onClick = onClick,
  )
}

/** Pop-out overlay for the daily quote / word / trivia. Trivia hides the answer
 *  behind a Reveal button; tapping the scrim or Back dismisses it. */
@Composable
private fun DailyOverlay(mode: String, onDismiss: () -> Unit) {
  var revealed by remember { mutableStateOf(false) }
  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxSize()
              .background(Color(0xCC000000))
              .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
    ) {
      Column(
          modifier = Modifier.padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when (DailyContent.modeOf(mode)) {
          DailyContent.Mode.WORD -> {
            val w = remember { DailyContent.wordOfDay() }
            Text("Word of the day", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            Text(w.word, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("/${w.pronunciation}/", color = Color(0xFFB0B0B0), fontSize = 16.sp)
            Text(w.definition, color = Color(0xFFDADADA), fontSize = 18.sp, textAlign = TextAlign.Center)
          }
          DailyContent.Mode.TRIVIA -> {
            val t = remember { DailyContent.triviaOfDay() }
            Text("Trivia of the day", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            Text(t.question, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (revealed) {
              Text(t.answer, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            } else {
              Surface(
                  color = MaterialTheme.colorScheme.primary,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { revealed = true },
              ) {
                Text("Reveal answer", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
              }
            }
          }
          else -> {
            val q = remember { DailyContent.quoteOfDay() }
            Text("Quote of the day", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
            Text("“${q.text}”", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text("— ${q.author}", color = Color(0xFFB0B0B0), fontSize = 16.sp)
          }
        }
        Spacer(Modifier.size(4.dp))
        Surface(
            color = Color(0x22FFFFFF),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() },
        ) {
          Text("Close", color = Color.White, fontSize = 16.sp,
              modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp))
        }
      }
    }
  }
}

private enum class SpeedPhase { PING, DOWNLOAD, UPLOAD, DONE }

private class SpeedResult {
  var server by mutableStateOf("")
  var pingMs by mutableStateOf(0.0)
  var jitterMs by mutableStateOf(0.0)
  var downMbps by mutableStateOf(0.0)
  var upMbps by mutableStateOf(0.0)
  fun reset() { server = ""; pingMs = 0.0; jitterMs = 0.0; downMbps = 0.0; upMbps = 0.0 }
}

/** Pop-out window: full ping / download / upload test against Cloudflare's open speed
 *  endpoints, with the serving data-centre shown, live updates, an X to close, and a
 *  Run-again button. Each leg runs independently so one failing doesn't sink the rest.
 *  Rendered as a top-level scrim overlay (not a Compose Dialog) so it composites into
 *  the launcher's own window — Portal's window manager doesn't always show separate
 *  Dialog windows. Auto-closes 3s after the run completes. */
@Composable
private fun SpeedTestOverlay(onDismiss: () -> Unit) {
  var phase by remember { mutableStateOf(SpeedPhase.PING) }
  val r = remember { SpeedResult() }
  var runId by remember { mutableStateOf(0) }

  LaunchedEffect(runId) {
    r.reset()
    phase = SpeedPhase.PING
    runSpeedTest(
        result = r,
        onPhase = { phase = it },
    )
    phase = SpeedPhase.DONE
  }

  // Auto-dismiss a short while after the test finishes, unless the user re-runs it.
  LaunchedEffect(phase, runId) {
    if (phase == SpeedPhase.DONE) {
      delay(3_000)
      onDismiss()
    }
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxSize()
              .background(Color(0xCC000000))
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
              ) { onDismiss() },
  ) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1C1C1E),
        modifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
    ) {
      Column(modifier = Modifier.width(380.dp).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Speed Test", color = Color.White, fontSize = 22.sp)
            Text(
                if (r.server.isNotEmpty()) "via ${r.server}"
                else if (phase == SpeedPhase.DONE) "Cloudflare" else "Finding server…",
                color = Color(0xFF9A9A9A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
          }
          Surface(
              color = Color(0x22FFFFFF),
              shape = androidx.compose.foundation.shape.CircleShape,
              modifier =
                  Modifier.size(40.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
                    onDismiss()
                  },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Canvas(Modifier.size(16.dp)) {
                val w = size.minDimension
                val s = w * 0.13f
                drawLine(Color.White, Offset(w * 0.18f, w * 0.18f), Offset(w * 0.82f, w * 0.82f),
                    strokeWidth = s, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.82f, w * 0.18f), Offset(w * 0.18f, w * 0.82f),
                    strokeWidth = s, cap = StrokeCap.Round)
              }
            }
          }
        }
        Spacer(Modifier.size(22.dp))
        Row(Modifier.fillMaxWidth()) {
          SpeedMetric("Ping", r.pingMs, "ms", 0, active = phase == SpeedPhase.PING,
              accent = Color(0xFFFFCA28), modifier = Modifier.weight(1f))
          SpeedMetric("Jitter", r.jitterMs, "ms", 1, active = phase == SpeedPhase.PING,
              accent = Color(0xFFFFCA28), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.size(20.dp))
        SpeedMetric("↓  Download", r.downMbps, "Mbps", 1, active = phase == SpeedPhase.DOWNLOAD,
            accent = Color(0xFF42A5F5), big = true)
        Spacer(Modifier.size(16.dp))
        SpeedMetric("↑  Upload", r.upMbps, "Mbps", 1, active = phase == SpeedPhase.UPLOAD,
            accent = Color(0xFF66BB6A), big = true)
        Spacer(Modifier.size(26.dp))
        val busy = phase != SpeedPhase.DONE
        val label =
            when (phase) {
              SpeedPhase.PING -> "Pinging…"
              SpeedPhase.DOWNLOAD -> "Testing download…"
              SpeedPhase.UPLOAD -> "Testing upload…"
              SpeedPhase.DONE -> "Run again"
            }
        Surface(
            color = if (busy) Color(0x22FFFFFF) else MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(14.dp),
            modifier =
                Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp)) {
                  if (!busy) runId++
                },
        ) {
          Text(label, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp))
        }
      }
    }
  }
}

@Composable
private fun SpeedMetric(
    label: String,
    value: Double,
    unit: String,
    decimals: Int,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
  val shown = if (value > 0.0) String.format("%.${decimals}f", value) else "—"
  if (big) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
      Text(label, color = Color(0xFFDADADA), fontSize = 17.sp, modifier = Modifier.weight(1f))
      Text(shown, color = if (active) accent else Color.White,
          fontSize = 30.sp, fontWeight = FontWeight.Medium, lineHeight = 30.sp)
      Spacer(Modifier.size(6.dp))
      Text(unit, color = Color(0xFF9A9A9A), fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
  } else {
    Column(modifier) {
      Text(label.uppercase(), color = Color(0xFF8A8A8A), fontSize = 12.sp, letterSpacing = 0.5.sp)
      Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
        Text(shown, color = if (active) accent else Color.White,
            fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
        Spacer(Modifier.size(4.dp))
        Text(unit, color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
      }
    }
  }
}

/** Cloudflare speed test: serving data-centre, latency + jitter, download, upload.
 *  Each leg is wrapped independently — a failure leaves that metric blank but lets the
 *  others complete. Emits live via the [result] snapshot-state holder; runs on IO. */
private suspend fun runSpeedTest(result: SpeedResult, onPhase: (SpeedPhase) -> Unit) {
  withContext(Dispatchers.IO) {
    // ---- serving data-centre (cdn-cgi/trace: colo=IATA, loc=country) ----
    runCatching {
      val conn =
          java.net.URL("https://speed.cloudflare.com/cdn-cgi/trace").openConnection()
              as java.net.HttpURLConnection
      conn.connectTimeout = 6_000
      conn.readTimeout = 6_000
      val text = conn.inputStream.bufferedReader().use { it.readText() }
      conn.disconnect()
      val kv = text.lineSequence().mapNotNull {
        val i = it.indexOf('='); if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
      }.toMap()
      result.server =
          listOfNotNull("Cloudflare", kv["colo"], kv["loc"]).filter { it.isNotBlank() }
              .joinToString(" · ")
    }

    // ---- ping: TTFB of a 0-byte download, keep-alive reused; drop the warm-up ----
    onPhase(SpeedPhase.PING)
    runCatching {
      val samples = ArrayList<Double>()
      repeat(6) { i ->
        val t0 = System.nanoTime()
        val conn =
            java.net.URL("https://speed.cloudflare.com/__down?bytes=0").openConnection()
                as java.net.HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.inputStream.use { it.read() }
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        if (i > 0) samples.add(ms) // first request pays TLS setup
      }
      if (samples.isNotEmpty()) {
        val avg = samples.average()
        result.pingMs = avg
        result.jitterMs = samples.map { kotlin.math.abs(it - avg) }.average()
      }
    }

    // ---- download: Cloudflare caps __down at <100 MB (100 MB → 403), so pull 50 MB
    //      segments back-to-back until the 10s budget is spent. This also keeps the
    //      pipe full on fast links where a single segment finishes in well under 1s. ----
    onPhase(SpeedPhase.DOWNLOAD)
    runCatching {
      val t0 = System.currentTimeMillis()
      var bytes = 0L
      var lastEmit = 0L
      val buf = ByteArray(65_536)
      while (System.currentTimeMillis() - t0 < 10_000) {
        val conn =
            java.net.URL("https://speed.cloudflare.com/__down?bytes=52428800").openConnection()
                as java.net.HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 20_000
        try {
          conn.inputStream.use { s ->
            while (true) {
              val n = s.read(buf)
              if (n == -1) break
              bytes += n
              val now = System.currentTimeMillis()
              val sec = (now - t0) / 1000.0
              if (now - lastEmit > 200 && sec > 0) {
                result.downMbps = bytes * 8.0 / 1_000_000.0 / sec
                lastEmit = now
              }
              if (now - t0 > 10_000) break
            }
          }
        } finally {
          conn.disconnect()
        }
      }
      val sec = (System.currentTimeMillis() - t0) / 1000.0
      if (sec > 0) result.downMbps = bytes * 8.0 / 1_000_000.0 / sec
    }

    // ---- upload: stream random bytes for up to 10s; measured during the write so a
    //      truncated-response close doesn't lose the reading ----
    onPhase(SpeedPhase.UPLOAD)
    runCatching {
      val conn =
          java.net.URL("https://speed.cloudflare.com/__up").openConnection()
              as java.net.HttpURLConnection
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.connectTimeout = 8_000
      conn.readTimeout = 20_000
      conn.setChunkedStreamingMode(0)
      val chunk = ByteArray(65_536).also { java.util.Random().nextBytes(it) }
      val t0 = System.currentTimeMillis()
      var sent = 0L
      var lastEmit = 0L
      runCatching {
        conn.outputStream.use { o ->
          while (System.currentTimeMillis() - t0 < 10_000) {
            o.write(chunk)
            sent += chunk.size
            val now = System.currentTimeMillis()
            val sec = (now - t0) / 1000.0
            if (now - lastEmit > 200 && sec > 0) {
              result.upMbps = sent * 8.0 / 1_000_000.0 / sec
              lastEmit = now
            }
          }
          o.flush()
        }
        runCatching { conn.responseCode }
      }
      conn.disconnect()
      val sec = (System.currentTimeMillis() - t0) / 1000.0
      if (sec > 0 && sent > 0) result.upMbps = sent * 8.0 / 1_000_000.0 / sec
    }
  }
}

@Composable
private fun NowPlayingTile(onClick: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  var playing by remember { mutableStateOf(audioMgr.isMusicActive) }
  LaunchedEffect(Unit) {
    while (true) { playing = audioMgr.isMusicActive; delay(1_500) }
  }
  BuiltInTile(
      label = if (playing) "Now Playing" else "Media",
      background = if (playing) Color(0xFF1B5E20) else Color(0xFF2B2B2B),
      glyph = if (playing) ICON_PAUSE else ICON_PLAY,
      onClick = onClick,
  )
}

/** Full Now Playing panel: album art + track from the active media session, with
 *  transport controls. If notification-listener access isn't granted, it can't read
 *  the track — so it shows a one-tap button to enable it (still offers play/pause). */
@Composable
private fun NowPlayingOverlay(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  var listenerOn by remember { mutableStateOf(SettingsGuard.isMediaListenerEnabled(context)) }
  var track by remember { mutableStateOf<NowPlayingState?>(null) }
  var playing by remember { mutableStateOf(audioMgr.isMusicActive) }

  // Re-check the listener grant on resume (the user may have just enabled it) and poll
  // the current track while open.
  LaunchedEffect(Unit) {
    while (true) {
      listenerOn = SettingsGuard.isMediaListenerEnabled(context)
      track = if (listenerOn) NowPlayingHub.current else null
      playing = audioMgr.isMusicActive
      delay(1_500)
    }
  }

  fun mediaKey(code: Int) {
    audioMgr.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    audioMgr.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
  }

  BackHandler { onDismiss() }
  Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
          .tvFocusable(RoundedCornerShape(0.dp), focusScale = 1f) { onDismiss() },
  ) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.widthIn(max = 560.dp).padding(24.dp)) {
      Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Now Playing", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        val art = track?.artBitmap
        if (art != null) {
          Image(bitmap = art.asImageBitmap(), contentDescription = null,
              modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)))
        } else {
          Box(modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)),
              contentAlignment = Alignment.Center) {
            Text("♪", color = Color(0x66FFFFFF), fontSize = 72.sp)
          }
        }

        when {
          track != null -> {
            Text(track!!.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track!!.artist.isNotBlank())
                Text(track!!.artist, color = Color(0xFFBFBFBF), fontSize = 15.sp, maxLines = 1)
          }
          !listenerOn -> {
            Text("To show the track name and album art, give Immortal permission to read " +
                "media notifications.", color = Color(0xFF9A9A9A), fontSize = 14.sp,
                textAlign = TextAlign.Center)
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.tvFocusable(RoundedCornerShape(12.dp), focusScale = 1f) {
                  // Prefer flipping the secure setting ourselves (Portal's listener
                  // settings UI is unreliable); fall back to it only if we can't.
                  SettingsGuard.enableMediaListener(context)
                  if (SettingsGuard.isMediaListenerEnabled(context)) {
                    listenerOn = true
                  } else {
                    runCatching {
                      context.startActivity(
                          Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                  }
                }) {
              Text("Enable now-playing access",
                  color = Color.White, fontSize = 15.sp,
                  fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
          }
          else -> Text(if (playing) "Playing" else "Nothing playing", color = Color(0xFF9A9A9A), fontSize = 15.sp)
        }

        // Transport controls (work via media keys regardless of listener access).
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
          TransportButton("⏮") { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
          TransportButton(if (playing) "⏸" else "▶", big = true) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); playing = !playing
          }
          TransportButton("⏭") { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        }

        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().tvFocusable(RoundedCornerShape(14.dp), focusScale = 1f) { onDismiss() }) {
          Text("Close", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
              modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun TransportButton(glyph: String, big: Boolean = false, onClick: () -> Unit) {
  val sz = if (big) 64.dp else 52.dp
  Surface(color = if (big) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
      shape = androidx.compose.foundation.shape.CircleShape,
      modifier = Modifier.size(sz).tvFocusable(androidx.compose.foundation.shape.CircleShape) { onClick() }) {
    Box(contentAlignment = Alignment.Center) {
      Text(glyph, color = Color.White, fontSize = if (big) 26.sp else 20.sp)
    }
  }
}

// Material-style glyph paths (24x24 viewport), rendered crisply as vectors.

private const val ICON_CALL =
    "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"
private const val ICON_DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"
private const val ICON_REFRESH =
    "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
private const val ICON_IMAGE =
    "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"
private const val ICON_CAMERA =
    "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"
private const val ICON_SPEEDTEST =
    "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z"
private const val ICON_MOON =
    "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"
private const val ICON_PLAY = "M5 3l14 9-14 9V3z"
private const val ICON_PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
private const val ICON_WAVING_HAND =
    "M23 17c0 3.31-2.69 6-6 6v-1.5c2.48 0 4.5-2.02 4.5-4.5H23zM1 7c0-3.31 2.69-6 6-6v1.5C4.52 2.5 2.5 4.52 2.5 7H1zm7.01-1.5L7 7v3.5l1.01 1.5L10 13.5l-1 1.5v3.5l1.01 1.5 7 .01L18 18.5v-3.5l-1-1.5 1.99-1.5L18 10v-3.5l-1.01-1.5-7-.01zM13 7h2.5l1.5 1.5-1.5 1.5H13V7z"
private const val ICON_HELP =
    "M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z"
// Contact / address-book icon for the Contacts tile in Settings.
private const val ICON_CONTACT =
    "M20 0H4v2h16V0zM4 24h16v-2H4v2zM20 4H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 2.5c1.93 0 3.5 1.57 3.5 3.5s-1.57 3.5-3.5 3.5-3.5-1.57-3.5-3.5 1.57-3.5 3.5-3.5zM18 18H6v-1c0-2 4-3.1 6-3.1s6 1.1 6 3.1v1z"
private const val ICON_GEAR =
    "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
// Simple clock face: circle with hour and minute hands.
private const val ICON_TIME =
    "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"
// Simple home icon for the Portal home shortcut tile.
private const val ICON_PORTAL =
    "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"
// Bell icon for the Sounds (ambient chime) tile in Settings.
private const val ICON_BELL =
    "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"
// Hourglass icon for the Countdowns tile in Settings.
private const val ICON_HOURGLASS =
    "M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zM12 11.5l-4-4V4h8v3.5l-4 4z"
// Lightbulb icon for the Daily quote/word/trivia tile.
private const val ICON_LIGHTBULB =
    "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"
// Sticky-note icon for the Leave-a-note tile.
private const val ICON_NOTE =
    "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"
// Bus icon for the Transit tile.
private const val ICON_BUS =
    "M4 16c0 .88.39 1.67 1 2.22V20c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h8v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1.78c.61-.55 1-1.34 1-2.22V6c0-3.5-3.58-4-8-4s-8 .5-8 4v10zm3.5 1c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm9 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6H6V6h12v5z"
private const val ICON_HEADPHONES =
    "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-3v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z"
// Stopwatch / timer icon.
private const val ICON_STOPWATCH =
    "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"
// Swap/convert arrows icon.
private const val ICON_CONVERT =
    "M7.5 21.5L4 18l3.5-3.5 1.42 1.42L7.83 17H16v2H7.83l1.09 1.08L7.5 21.5zM16.5 2.5L20 6l-3.5 3.5-1.42-1.42L16.17 7H8V5h8.17l-1.09-1.08L16.5 2.5z"
// Lamp / nightlight (wb_incandescent) icon.
private const val ICON_LAMP =
    "M3.55 18.54l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8zM11 22.45h2V19.5h-2v2.95zM4 10.5H1v2h3v-2zm11-4.19V1.5H9v4.81C7.21 7.35 6 9.28 6 11.5c0 3.31 2.69 6 6 6s6-2.69 6-6c0-2.22-1.21-4.15-3-5.19zM20 10.5v2h3v-2h-3zm-2.76 7.66l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4z"
// Book (menu_book) icon for the bedtime story tile.
private const val ICON_BOOK =
    "M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z"
// Sunrise (wb_twilight) icon.
private const val ICON_SUNRISE =
    "M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z"
// Inbox / request board icon.
private const val ICON_INBOX =
    "M19 3H4.99c-1.11 0-1.98.9-1.98 2L3 19c0 1.1.88 2 1.99 2H19c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z"
// Notifications/bell-ring icon for the Ping tile.
private const val ICON_PING =
    "M7.58 4.08L6.15 2.65C3.75 4.48 2.17 7.3 2.03 10.5h2c.15-2.65 1.51-4.97 3.55-6.42zm12.39 6.42h2c-.15-3.2-1.73-6.02-4.12-7.85l-1.42 1.43c2.02 1.45 3.39 3.77 3.54 6.42zM18 11c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2v-5zm-6 11c.14 0 .27-.01.4-.04.65-.14 1.18-.58 1.44-1.18.1-.24.15-.5.15-.78h-4c.01 1.1.9 2 2.01 2z"
// History / changelog icon.
private const val ICON_HISTORY =
    "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
// Waves icon (aurora curtains) for the Aurora tile.
private const val ICON_AURORA =
    "M17 16.99c-1.35 0-2.2.42-2.95.8-.65.33-1.18.6-2.05.6-.9 0-1.4-.25-2.05-.6-.75-.38-1.57-.8-2.95-.8s-2.2.42-2.95.8c-.65.33-1.17.6-2.05.6v1.95c1.35 0 2.2-.42 2.95-.8.65-.33 1.17-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.42 2.95-.8c.65-.33 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8v-1.95c-.9 0-1.4-.25-2.05-.6-.75-.38-1.6-.8-2.95-.8zm0-4.45c-1.35 0-2.2.43-2.95.8-.65.32-1.18.6-2.05.6-.9 0-1.4-.25-2.05-.6-.75-.37-1.57-.8-2.95-.8s-2.2.43-2.95.8c-.65.32-1.17.6-2.05.6v1.95c1.35 0 2.2-.43 2.95-.8.65-.33 1.17-.6 2.05-.6s1.4.25 2.05.6c.75.37 1.57.8 2.95.8s2.2-.43 2.95-.8c.65-.33 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8v-1.95c-.9 0-1.4-.25-2.05-.6-.75-.37-1.6-.8-2.95-.8zM17 8.09c-1.35 0-2.2.43-2.95.8-.65.32-1.18.61-2.05.61-.9 0-1.4-.25-2.05-.61-.75-.37-1.57-.8-2.95-.8s-2.2.43-2.95.8c-.65.33-1.17.61-2.05.61v1.95c1.35 0 2.2-.43 2.95-.8.65-.32 1.17-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.43 2.95-.8c.65-.32 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8V8.9c-.9 0-1.4-.25-2.05-.61-.75-.37-1.6-.8-2.95-.8z"
// Satellite (satellite_alt) icon for the ISS pass tile.
private const val ICON_SATELLITE =
    "M11.62 1.99l-3.83 3.83c-.78.78-.78 2.05 0 2.83l1.41 1.41-1.42 1.42-1.41-1.41c-.78-.78-2.05-.78-2.83 0L1.99 13.4c-.79.78-.79 2.05 0 2.83l5.78 5.78c.78.78 2.05.78 2.83 0l3.55-3.55c.78-.78.78-2.05 0-2.83l-1.41-1.41 1.42-1.42 1.41 1.41c.78.78 2.05.78 2.83 0l3.83-3.83c.78-.78.78-2.05 0-2.83l-5.78-5.78c-.79-.78-2.06-.78-2.84 0zm-4.2 16.6l-4.4-4.4 2.13-2.13 4.4 4.4-2.13 2.13zm9.2-9.2l-4.4-4.4 2.13-2.13 4.4 4.4-2.13 2.13z"

/** A non-app tile injected into a folder (e.g. the Screensaver settings entry). */
internal data class FolderExtra(val label: String, val glyph: String, val onClick: () -> Unit)

// ---- Tool category folders --------------------------------------------------
// The built-in "tool" tiles (ISS, Aurora, Stopwatch, Lamp, Ping, …) are grouped
// into these few category folders on the home grid. The folder tile opens a
// ToolCategoryOverlay that renders the real tiles, so dynamic ones (Aurora lighting
// up, ISS pass count) keep working inside the folder. Membership is wired where the
// overlay is built in HomeScreen; this list only drives the folder tiles shown.
private const val TOOLCAT_SKY = "Sky & Outdoors"
private const val TOOLCAT_KITCHEN = "Kitchen & Tools"
private const val TOOLCAT_AMBIENT = "Ambient & Sleep"
private const val TOOLCAT_HOME = "Home & LAN"
private const val TOOLCAT_MORE = "More"

internal data class ToolCat(val id: String, val glyph: String)

internal val TOOL_CATEGORIES =
    listOf(
        ToolCat(TOOLCAT_SKY, ICON_SATELLITE),
        ToolCat(TOOLCAT_KITCHEN, ICON_STOPWATCH),
        ToolCat(TOOLCAT_AMBIENT, ICON_MOON),
        ToolCat(TOOLCAT_HOME, ICON_PING),
        ToolCat(TOOLCAT_MORE, ICON_INBOX),
    )

// Canonical tool ids — persisted in UserLayout tool-category overrides, so keep stable.
private const val TOOL_ISS = "iss"
private const val TOOL_AURORA = "aurora"
private const val TOOL_TRANSIT = "transit"
private const val TOOL_STOPWATCH = "stopwatch"
private const val TOOL_CONVERTER = "converter"
private const val TOOL_SPEEDTEST = "speedtest"
private const val TOOL_LAMP = "lamp"
private const val TOOL_MYNOISE = "mynoise"
private const val TOOL_BEDTIME = "bedtime"
private const val TOOL_SUNRISE = "sunrise"
private const val TOOL_PING = "ping"
private const val TOOL_NOTE = "note"
private const val TOOL_NOWPLAYING = "nowplaying"
private const val TOOL_CAMERA = "camera"
private const val TOOL_DAILY = "daily"
private const val TOOL_WHATSNEW = "whatsnew"
private const val TOOL_REQUEST = "request"
private const val TOOL_REPORTAL = "reportal"

/** A built-in tool: its stable id, a label + glyph for the folder editor's static chip,
 *  and the category it lives in by default (a user override in UserLayout wins). */
internal data class ToolDef(
    val id: String,
    val label: String,
    val glyph: String,
    val defaultCat: String,
)

internal val ALL_TOOLS =
    listOf(
        ToolDef(TOOL_ISS, "Space station", ICON_SATELLITE, TOOLCAT_SKY),
        ToolDef(TOOL_AURORA, "Aurora", ICON_AURORA, TOOLCAT_SKY),
        ToolDef(TOOL_TRANSIT, "Transit", ICON_BUS, TOOLCAT_SKY),
        ToolDef(TOOL_STOPWATCH, "Stopwatch", ICON_STOPWATCH, TOOLCAT_KITCHEN),
        ToolDef(TOOL_CONVERTER, "Converter", ICON_CONVERT, TOOLCAT_KITCHEN),
        ToolDef(TOOL_SPEEDTEST, "Speed test", ICON_SPEEDTEST, TOOLCAT_KITCHEN),
        ToolDef(TOOL_LAMP, "Lamp", ICON_LAMP, TOOLCAT_AMBIENT),
        ToolDef(TOOL_MYNOISE, "Soundscapes", ICON_HEADPHONES, TOOLCAT_AMBIENT),
        ToolDef(TOOL_BEDTIME, "Bedtime", ICON_BOOK, TOOLCAT_AMBIENT),
        ToolDef(TOOL_SUNRISE, "Sunrise", ICON_SUNRISE, TOOLCAT_AMBIENT),
        ToolDef(TOOL_PING, "Ping", ICON_PING, TOOLCAT_HOME),
        ToolDef(TOOL_NOTE, "Note", ICON_NOTE, TOOLCAT_HOME),
        ToolDef(TOOL_NOWPLAYING, "Now playing", ICON_PLAY, TOOLCAT_HOME),
        ToolDef(TOOL_CAMERA, "Cameras", ICON_CAMERA, TOOLCAT_HOME),
        ToolDef(TOOL_DAILY, "Daily", ICON_LIGHTBULB, TOOLCAT_MORE),
        ToolDef(TOOL_WHATSNEW, "What's new", ICON_HISTORY, TOOLCAT_MORE),
        ToolDef(TOOL_REQUEST, "Request app", ICON_INBOX, TOOLCAT_MORE),
        ToolDef(TOOL_REPORTAL, "Reportal", ICON_GLOBE, TOOLCAT_MORE),
    )

private fun toolDefOf(id: String): ToolDef? = ALL_TOOLS.firstOrNull { it.id == id }

/** Home-grid tile for a tool category. Styled like a folder (neutral slate) with the
 *  category's glyph, to read as a container rather than a single action. */
@Composable
internal fun ToolFolderTile(label: String, glyph: String, onClick: () -> Unit) {
  BuiltInTile(label = label, background = Color(0xFF3A3A3A), glyph = glyph, onClick = onClick)
}

private const val ICON_GLOBE =
    "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"

/** Web shortcut to reportal.dev — the community directory of Portal apps/widgets. Opens
 *  in the device browser (Chromium handles https). Lives in the tools folders (movable). */
@Composable
private fun ReportalTile(onClick: () -> Unit) {
  BuiltInTile(label = "Reportal", background = Color(0xFF1565C0), glyph = ICON_GLOBE, onClick = onClick)
}

/** Overlay listing one tool category's tiles in a grid. Lighter than [FolderOverlay]
 *  (these aren't app folders). The ✎ button toggles edit mode: each tool becomes a chip
 *  whose tap menu moves it to another folder, and an Add tile pulls tools in from other
 *  folders. Back / tap-outside dismiss; the grid takes focus on open so the D-pad works. */
@Composable
private fun ToolCategoryOverlay(
    title: String,
    toolIds: List<String>,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    renderLive: @Composable (String) -> Unit,
    categories: List<String>,
    addable: List<ToolDef>,
    onMove: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
  val noRipple = remember { MutableInteractionSource() }
  BackHandler { onDismiss() }
  val gridFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

  val count = toolIds.size + if (editMode) 1 else 0
  val cols = when {
    count <= 3 -> 3
    count <= 8 -> 4
    else -> 5
  }
  val tileScale = LocalTileDp.current / 88.dp
  val desiredWidth = (420.dp + 126.dp * (cols - 3)) * tileScale
  val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
  val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxSize()
              .onPreviewKeyEvent { e ->
                if (e.key == Key.Back || e.key == Key.Escape) {
                  if (e.type == KeyEventType.KeyUp) onDismiss()
                  true
                } else false
              }
              .background(Color(0xCC000000))
              .clickable(interactionSource = noRipple, indication = null) { onDismiss() },
  ) {
    Surface(
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(28.dp),
        modifier =
            Modifier.width(desiredWidth)
                .widthIn(max = screenWidth * 0.94f)
                .clickable(interactionSource = noRipple, indication = null) {},
    ) {
      Column(modifier = Modifier.padding(28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f))
          Surface(
              color = if (editMode) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
              shape = androidx.compose.foundation.shape.CircleShape,
              modifier =
                  Modifier.size(40.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
                    onToggleEdit()
                  },
          ) {
            Box(contentAlignment = Alignment.Center) { Text("✎", color = Color.White, fontSize = 18.sp) }
          }
        }
        Spacer(Modifier.size(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier.heightIn(max = screenHeight * 0.62f).focusRequester(gridFocus).focusGroup(),
        ) {
          items(toolIds, key = { it }) { id ->
            if (!editMode) {
              renderLive(id)
            } else {
              val def = toolDefOf(id)
              if (def != null) {
                EditableToolTile(
                    def = def,
                    currentCat = title,
                    categories = categories,
                    onMove = { target -> onMove(id, target) },
                )
              }
            }
          }
          if (editMode) {
            item(key = "__add__") {
              AddToolTile(addable = addable, onAdd = { d -> onMove(d.id, title) })
            }
          }
        }
        if (editMode) {
          Spacer(Modifier.size(10.dp))
          Text(
              "Tap a tool to move it to another folder, or use Add to bring one in.",
              color = Color(0xFF8A8A8A), fontSize = 13.sp,
              modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
      }
    }
  }
}

/** Edit-mode representation of a tool: a static glyph+label tile with a menu to move it
 *  to a different category folder. */
@Composable
private fun EditableToolTile(
    def: ToolDef,
    currentCat: String,
    categories: List<String>,
    onMove: (String) -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  Box {
    BuiltInTile(label = def.label, background = Color(0xFF4A3B57), glyph = def.glyph) { menu = true }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      Text("  Move to…", color = Color(0xFF9A9A9A), fontSize = 13.sp,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
      categories.filter { it != currentCat }.forEach { cat ->
        DropdownMenuItem(text = { Text(cat) }, onClick = { menu = false; onMove(cat) })
      }
    }
  }
}

/** Edit-mode "Add" tile: a menu of tools currently living in other folders; picking one
 *  moves it into this folder. */
@Composable
private fun AddToolTile(addable: List<ToolDef>, onAdd: (ToolDef) -> Unit) {
  var menu by remember { mutableStateOf(false) }
  Box {
    BuiltInTile(label = "Add", background = Color(0xFF2E7D32), glyph = ICON_DOWNLOAD) { menu = true }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      if (addable.isEmpty()) {
        Text("  All tools are here", color = Color(0xFF9A9A9A), fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
      }
      addable.forEach { d ->
        DropdownMenuItem(text = { Text(d.label) }, onClick = { menu = false; onAdd(d) })
      }
    }
  }
}

/** A built-in launcher tile: a rounded colour tile with a centered white vector
 * glyph, styled to sit naturally beside real app icons. */

@Composable
private fun BackgroundImage(uriString: String, blur: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uriString) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark overlay for readability
            Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))
        }
    }
}

/** A built-in gradient wallpaper from [ImmortalSettings.GRADIENTS]. */
@Composable
private fun GradientBackground(key: String) {
  val preset = ImmortalSettings.GRADIENTS.firstOrNull { it.first == key } ?: ImmortalSettings.GRADIENTS.first()
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  androidx.compose.ui.graphics.Brush.verticalGradient(
                      listOf(Color(preset.second), Color(preset.third)))))
}

/** A sun-driven sky background: the gradient tracks the real time of day, derived
 * from today's sunrise/sunset (keyless Open-Meteo). Dawn pinks, midday blue, dusk
 * orange, night near-black — telling time ambiently on an always-on screen. */
@Composable
private fun SkyBackground() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val sun by produceState<Weather.SunTimes?>(initialValue = null) {
    value = withContext(Dispatchers.IO) { Weather.fetchSunTimes(context) }
  }
  // Recompute the gradient every few minutes so it drifts through the day.
  var nowMin by remember { mutableStateOf(currentMinuteOfDay()) }
  LaunchedEffect(Unit) {
    while (true) { nowMin = currentMinuteOfDay(); delay(5L * 60 * 1000) }
  }
  val sr = sun?.let { minuteOfDay(it.sunriseMillis) } ?: 6 * 60
  val ss = sun?.let { minuteOfDay(it.sunsetMillis) } ?: 20 * 60
  val (top, bottom) = SkyColors.gradientFor(nowMin, sr, ss)
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom))))
}

/** A thin progress line at the very top of the screen showing how far through the day
 *  we are (midnight → midnight). The fill is tinted with the current sky colour so it
 *  matches the Sky background — pink at dawn, blue midday, orange at dusk, dim at night. */
@Composable
private fun DayProgressBar(modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val sun by produceState<Weather.SunTimes?>(initialValue = null) {
    value = withContext(Dispatchers.IO) { Weather.fetchSunTimes(context) }
  }
  var nowMin by remember { mutableStateOf(currentMinuteOfDay()) }
  LaunchedEffect(Unit) {
    while (true) { nowMin = currentMinuteOfDay(); delay(60L * 1000) }
  }
  val sr = sun?.let { minuteOfDay(it.sunriseMillis) } ?: 6 * 60
  val ss = sun?.let { minuteOfDay(it.sunsetMillis) } ?: 20 * 60
  // Colour-aware: use the vivid (bottom) colour of the live sky gradient.
  val fill = SkyColors.gradientFor(nowMin, sr, ss).second
  val progress = (nowMin / 1440f).coerceIn(0f, 1f)
  Box(
      modifier = modifier.fillMaxWidth().height(4.dp).background(fill.copy(alpha = 0.18f)),
  ) {
    Box(
        modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(fill),
    )
  }
}

/** The real night sky behind the grid after dark: the brightest stars projected to the
 *  device's horizon for the current time + location, with a few asterism lines. By day
 *  it's just a deep dark panel; stars fade in through twilight. */
@Composable
private fun StarFieldBackground() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val coords by produceState<Pair<Double, Double>?>(initialValue = null) {
    value = withContext(Dispatchers.IO) { Weather.coordinates(context) }
  }
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) { now = System.currentTimeMillis(); delay(30L * 1000) }
  }

  Box(modifier = Modifier.fillMaxSize().background(
      androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF05060F), Color(0xFF0C1430))))) {
    val loc = coords ?: return@Box
    val (lat, lon) = loc
    val lst = StarField.localSiderealTime(now, lon)
    val sunAlt = StarField.sunAltitude(now, lat, lon)
    // Stars are invisible in daylight; fade in across twilight (0° → −12°).
    val nightFactor = ((-sunAlt) / 12.0).coerceIn(0.0, 1.0).toFloat()
    if (nightFactor <= 0.01f) return@Box

    // Project once; reused for stars + lines.
    val projected = remember(now, lat, lon) {
      StarField.STARS.map { StarField.project(it, lat, lst) }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      fun screenX(az: Double) = (az / 360.0 * w).toFloat()
      fun screenY(alt: Double) = (h * (1.0 - alt / 90.0)).toFloat()

      // Asterism lines first (under the stars).
      StarField.LINES.forEach { (a, b) ->
        val pa = projected[a]
        val pb = projected[b]
        if (pa.alt > 0 && pb.alt > 0) {
          val xa = screenX(pa.az)
          val xb = screenX(pb.az)
          // Skip the wrap-around seam.
          if (kotlin.math.abs(xa - xb) < w / 2f) {
            drawLine(
                color = Color(0xFF6E8BD8).copy(alpha = 0.35f * nightFactor),
                start = androidx.compose.ui.geometry.Offset(xa, screenY(pa.alt)),
                end = androidx.compose.ui.geometry.Offset(xb, screenY(pb.alt)),
                strokeWidth = 2f,
            )
          }
        }
      }

      // Stars: brighter (lower mag) → bigger + more opaque.
      StarField.STARS.forEachIndexed { i, star ->
        val p = projected[i]
        if (p.alt <= 0) return@forEachIndexed
        val radius = (2.6f - star.mag.toFloat() * 0.55f).coerceIn(0.8f, 4.5f)
        val alpha = ((1.8f - star.mag.toFloat() * 0.32f).coerceIn(0.35f, 1f)) * nightFactor
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(screenX(p.az), screenY(p.alt)),
        )
      }
    }
  }
}

private fun currentMinuteOfDay(): Int {
  val c = java.util.Calendar.getInstance()
  return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}

private fun minuteOfDay(epochMillis: Long): Int {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
  return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}


@Composable
private fun QuickSettingsPanel(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var brightness by remember { mutableStateOf(50) }
    var volume by remember { mutableStateOf(50) }
    var wifiOn by remember { mutableStateOf(false) }
    var bluetoothOn by remember { mutableStateOf(false) }

    // Load initial values
    LaunchedEffect(Unit) {
        try {
            val sysBrightness = android.provider.Settings.System.getInt(
                context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            brightness = (sysBrightness * 100 / 255).coerceIn(0, 100)
        } catch (_: Exception) {}
        try {
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 /
                    audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        } catch (_: Exception) {}
        try {
            wifiOn = android.net.wifi.WifiManager::class.java.let {
                val wm = context.applicationContext.getSystemService(it) as android.net.wifi.WifiManager
                wm.isWifiEnabled
            }
        } catch (_: Exception) {}
        try {
            bluetoothOn = android.bluetooth.BluetoothAdapter::class.java.let {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                adapter?.isEnabled == true
            }
        } catch (_: Exception) {}
    }

    fun setBrightness(value: Int) {
        brightness = value
        runCatching {
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                (value * 255 / 100).coerceIn(0, 255)
            )
        }
    }

    fun setVolume(value: Int) {
        volume = value
        runCatching {
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVol = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value * maxVol / 100, 0)
        }
    }

    fun toggleWifi() {
        runCatching {
            val wm = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiOn = !wifiOn
            wm.isWifiEnabled = wifiOn
        }
    }

    fun toggleBluetooth() {
        runCatching {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
                bluetoothOn = !bluetoothOn
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onDismiss() }
    ) {
        Surface(
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Quick settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

                // Brightness slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☀", color = Color.White, fontSize = 20.sp)
                    Spacer(Modifier.size(12.dp))
                    Text("Brightness", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("$brightness%", color = Color(0xFF9A9A9A), fontSize = 14.sp)
                }
                androidx.compose.material3.Slider(
                    value = brightness.toFloat(),
                    onValueChange = { setBrightness(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Volume slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊", color = Color.White, fontSize = 20.sp)
                    Spacer(Modifier.size(12.dp))
                    Text("Volume", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("$volume%", color = Color(0xFF9A9A9A), fontSize = 14.sp)
                }
                androidx.compose.material3.Slider(
                    value = volume.toFloat(),
                    onValueChange = { setVolume(it.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                // WiFi and Bluetooth toggles
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickToggle(
                        label = "WiFi",
                        icon = "📶",
                        isOn = wifiOn,
                        onToggle = { toggleWifi() },
                        modifier = Modifier.weight(1f)
                    )
                    QuickToggle(
                        label = "Bluetooth",
                        icon = "🔵",
                        isOn = bluetoothOn,
                        onToggle = { toggleBluetooth() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToggle(
    label: String,
    icon: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isOn) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2C),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(70.dp)
            .clickable { onToggle() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.size(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable

private fun CalendarWidget() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(CalendarHelper.hasPermission(context)) }

    LaunchedEffect(Unit) {
        // Request permission if not granted
        if (!hasPermission) {
            val activity = context as? android.app.Activity
            activity?.requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 2001)
        }
        events = CalendarHelper.upcoming(context)
        hasPermission = CalendarHelper.hasPermission(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, _ ->
            hasPermission = CalendarHelper.hasPermission(context)
            if (hasPermission) {
                events = CalendarHelper.upcoming(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Surface(
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Upcoming events",
                color = Color(0xFFBFBFBF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (!hasPermission) {
                Text(
                    "Calendar permission needed. Grant it in Android Settings to see events here.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 14.sp,
                )
            } else if (events.isEmpty()) {
                Text(
                    "No upcoming events in the next 7 days.",
                    color = Color(0xFF9A9A9A),
                    fontSize = 14.sp,
                )
            } else {
                events.take(5).forEach { event ->
                    CalendarEventRow(event)
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(event: CalendarEvent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeText = remember(event.begin) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = event.begin }
        if (event.allDay) {
            val dayFmt = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            "All day · ${dayFmt.format(cal.time)}"
        } else {
            val timeFmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            "Today · ${timeFmt.format(cal.time)}"
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(timeText, color = Color(0xFF9A9A9A), fontSize = 12.sp)
        }
    }
}

// ---- Fork home integration points (called from HomeActivity) -----------------

@Composable
internal fun ForkToolTile(toolId: String, onShowOverlay: (String) -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  when (toolId) {
    TOOL_ISS -> IssTile(onClick = { onShowOverlay(TOOL_ISS) })
    TOOL_AURORA -> AuroraTile(onClick = { onShowOverlay(TOOL_AURORA) })
    TOOL_TRANSIT -> TransitTile(onClick = { onShowOverlay(TOOL_TRANSIT) })
    TOOL_STOPWATCH -> StopwatchTile(onClick = { onShowOverlay(TOOL_STOPWATCH) })
    TOOL_CONVERTER -> ConverterTile(onClick = { onShowOverlay(TOOL_CONVERTER) })
    TOOL_SPEEDTEST -> SpeedTestTile(onClick = { onShowOverlay(TOOL_SPEEDTEST) })
    TOOL_LAMP -> LampTile()
    TOOL_MYNOISE -> MyNoiseTile()
    TOOL_BEDTIME -> BedtimeTile()
    TOOL_SUNRISE -> SunriseTile()
    TOOL_PING -> PingTile()
    TOOL_NOTE -> NoteTile(onClick = { onShowOverlay(TOOL_NOTE) })
    TOOL_NOWPLAYING -> NowPlayingTile(onClick = { onShowOverlay(TOOL_NOWPLAYING) })
    TOOL_CAMERA -> CameraTile(onClick = {
      runCatching { context.startActivity(Intent(context, CameraViewerActivity::class.java)) }
    })
    TOOL_DAILY -> {
      val mode = remember { ImmortalSettings.load(context).dailyTileMode }
      DailyTile(mode = mode, onClick = { onShowOverlay(TOOL_DAILY) })
    }
    TOOL_WHATSNEW -> WhatChangedTile(onClick = { onShowOverlay(TOOL_WHATSNEW) })
    TOOL_REPORTAL -> ReportalTile(onClick = {
      runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://reportal.dev"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    })
  }
}

@Composable
internal fun ForkToolOverlay(toolId: String, onDismiss: () -> Unit) {
  when (toolId) {
    TOOL_ISS -> IssOverlay(onDismiss = onDismiss)
    TOOL_AURORA -> AuroraOverlay(onDismiss = onDismiss)
    TOOL_TRANSIT -> TransitOverlay(onDismiss = onDismiss)
    TOOL_STOPWATCH -> StopwatchOverlay(onDismiss = onDismiss)
    TOOL_CONVERTER -> ConverterOverlay(onDismiss = onDismiss)
    TOOL_SPEEDTEST -> SpeedTestOverlay(onDismiss = onDismiss)
    TOOL_NOTE -> NoteOverlay(onDismiss = onDismiss)
    TOOL_NOWPLAYING -> NowPlayingOverlay(onDismiss = onDismiss)
    TOOL_DAILY -> {
      val context = androidx.compose.ui.platform.LocalContext.current
      val mode = remember { ImmortalSettings.load(context).dailyTileMode }
      DailyOverlay(mode = mode, onDismiss = onDismiss)
    }
    TOOL_WHATSNEW -> WhatChangedOverlay(onDismiss = onDismiss)
  }
}

@Composable
internal fun ForkToolCategoryOverlay(
    category: String,
    editMode: Boolean,
    onToggleEdit: () -> Unit,
    onShowOverlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val overrides = remember { UserLayout.loadToolCategories(context) }
  val toolsInCat = remember(overrides) {
    ALL_TOOLS.filter { (overrides[it.id] ?: it.defaultCat) == category }
  }
  val addable = remember(overrides) {
    ALL_TOOLS.filter { (overrides[it.id] ?: it.defaultCat) != category }
  }
  val categories = TOOL_CATEGORIES.map { it.id }
  ToolCategoryOverlay(
      title = category,
      toolIds = toolsInCat.map { it.id },
      editMode = editMode,
      onToggleEdit = onToggleEdit,
      renderLive = { id -> ForkToolTile(id, onShowOverlay) },
      categories = categories,
      addable = addable,
      onMove = { id, cat -> UserLayout.setToolCategory(context, id, cat) },
      onDismiss = onDismiss,
  )
}

@Composable
internal fun ForkHomeBackground(backgroundType: String) {
  val context = androidx.compose.ui.platform.LocalContext.current
  when (backgroundType) {
    ImmortalSettings.BG_GRADIENT -> {
      val key = remember { ImmortalSettings.load(context).backgroundGradient }
      GradientBackground(key)
    }
    ImmortalSettings.BG_SKY -> SkyBackground()
    ImmortalSettings.BG_STARS -> StarFieldBackground()
    ImmortalSettings.BG_IMAGE, ImmortalSettings.BG_BLUR -> {
      val path = remember { ImmortalSettings.load(context).backgroundImagePath }
      if (path != null) {
        BackgroundImage(uriString = path, blur = backgroundType == ImmortalSettings.BG_BLUR)
      }
    }
  }
}


