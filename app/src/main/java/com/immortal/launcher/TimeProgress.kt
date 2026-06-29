/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * "How far through the day/week/month/year are we" — pure date math plus the
 * glanceable visualisations that sit on the dashboard and (the sun arc) in the
 * home header. Deliberately offline and locale-aware; everything is derived from
 * the device clock so it works on a Portal with no network.
 */
object TimeProgress {

  /** A snapshot of where "now" sits inside the current week, month and year. */
  data class Spans(
      val year: Int,
      val yearFraction: Float, // 0f..1f through the calendar year
      val monthFraction: Float,
      val weekFraction: Float,
      val dayOfYear: Int, // 1-based
      val daysInYear: Int,
      val dayOfMonth: Int, // 1-based
      val daysInMonth: Int,
      val monthName: String,
      val firstDayOffset: Int, // weekday column (0=Mon..6=Sun) that day 1 falls on
  )

  fun compute(nowMillis: Long = System.currentTimeMillis()): Spans {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val year = now.get(Calendar.YEAR)

    val startOfYear = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_YEAR, 1); clearTime() }
    val startOfNextYear = (startOfYear.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
    val yearFraction = fraction(startOfYear, startOfNextYear, nowMillis)

    val startOfMonth = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); clearTime() }
    val startOfNextMonth = (startOfMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    val monthFraction = fraction(startOfMonth, startOfNextMonth, nowMillis)

    // Week runs Monday..Sunday regardless of locale first-day so the bar reads
    // the way most people picture "the week".
    val startOfWeek = (now.clone() as Calendar).apply {
      clearTime()
      val dow = get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
      val backToMonday = (dow + 5) % 7 // Mon->0, Sun->6
      add(Calendar.DAY_OF_MONTH, -backToMonday)
    }
    val startOfNextWeek = (startOfWeek.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
    val weekFraction = fraction(startOfWeek, startOfNextWeek, nowMillis)

    val day1 = (startOfMonth.clone() as Calendar)
    val firstDayOffset = (day1.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6

    return Spans(
        year = year,
        yearFraction = yearFraction,
        monthFraction = monthFraction,
        weekFraction = weekFraction,
        dayOfYear = now.get(Calendar.DAY_OF_YEAR),
        daysInYear = now.getActualMaximum(Calendar.DAY_OF_YEAR),
        dayOfMonth = now.get(Calendar.DAY_OF_MONTH),
        daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH),
        monthName = now.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
            ?: "",
        firstDayOffset = firstDayOffset,
    )
  }

  private fun fraction(start: Calendar, end: Calendar, nowMillis: Long): Float {
    val span = (end.timeInMillis - start.timeInMillis).toFloat()
    if (span <= 0f) return 0f
    return ((nowMillis - start.timeInMillis) / span).coerceIn(0f, 1f)
  }

  private fun Calendar.clearTime() {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard card
// ─────────────────────────────────────────────────────────────────────────────

private val accent = Color(0xFF0866FF)
private val track = Color(0x22FFFFFF)
private val past = Color(0xFF5A5A5E)
private val future = Color(0x33FFFFFF)

/** The "your year is N% gone" card: week / month / year bars, a 365-dot year
 *  strip with today lit, and the current month as a fading calendar. */
@Composable
fun TimeProgressCard(modifier: Modifier = Modifier) {
  var spans by remember { mutableStateOf(TimeProgress.compute()) }
  LaunchedEffect(Unit) {
    while (true) {
      spans = TimeProgress.compute()
      delay(60_000)
    }
  }
  Column(
      modifier = modifier.fillMaxWidth(0.92f)
          .background(Color(0x14FFFFFF), RoundedCornerShape(18.dp))
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
        "${spans.year} is ${(spans.yearFraction * 100).toInt()}% gone",
        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
    )
    ProgressRow("This week", spans.weekFraction)
    ProgressRow(spans.monthName, spans.monthFraction)
    ProgressRow("${spans.year}", spans.yearFraction)
    YearDots(dayOfYear = spans.dayOfYear, daysInYear = spans.daysInYear)
    MonthGrid(
        daysInMonth = spans.daysInMonth,
        today = spans.dayOfMonth,
        firstDayOffset = spans.firstDayOffset,
        monthName = spans.monthName,
    )
  }
}

@Composable
private fun ProgressRow(label: String, fraction: Float) {
  Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, color = Color(0xFFDADADA), fontSize = 14.sp)
      Text("${(fraction * 100).toInt()}%", color = Color(0xFF9A9A9A), fontSize = 14.sp)
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(8.dp)
            .background(track, RoundedCornerShape(4.dp)),
    ) {
      Box(
          modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
              .background(accent, RoundedCornerShape(4.dp)),
      )
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sun arc (home header)
// ─────────────────────────────────────────────────────────────────────────────

/** A sun riding an arc from sunrise (left) to sunset (right), live position.
 *  [sunriseMin]/[sunsetMin] are minutes-of-day; [riseLabel]/[setLabel] are the
 *  pre-formatted clock strings shown under the two horizon ends. */
@Composable
fun SunArc(
    sunriseMin: Int,
    sunsetMin: Int,
    riseLabel: String,
    setLabel: String,
    modifier: Modifier = Modifier,
) {
  var nowMin by remember { mutableStateOf(currentMinuteOfDay()) }
  LaunchedEffect(Unit) {
    while (true) { nowMin = currentMinuteOfDay(); delay(60_000) }
  }
  val daylight = (sunsetMin - sunriseMin).coerceAtLeast(1)
  val f = ((nowMin - sunriseMin).toFloat() / daylight).coerceIn(0f, 1f)
  val isDay = nowMin in sunriseMin..sunsetMin
  val sunColor = if (isDay) Color(0xFFFFC83D) else Color(0xFF6A6A6E)

  Column(modifier = modifier.fillMaxWidth(0.62f)) {
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
      val margin = 10f
      val w = size.width - margin * 2
      val baseY = size.height - 4f
      val arcH = size.height - 8f
      fun pt(t: Float) = Offset(
          x = margin + t * w,
          y = baseY - kotlin.math.sin(Math.PI * t).toFloat() * arcH,
      )
      // The full day arc, dashed and faint.
      val full = Path().apply {
        val start = pt(0f); moveTo(start.x, start.y)
        var t = 0f
        while (t <= 1f) { val p = pt(t); lineTo(p.x, p.y); t += 0.02f }
      }
      drawPath(
          full, color = Color(0x40FFFFFF),
          style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))),
      )
      // The elapsed portion, solid + warm.
      if (isDay && f > 0f) {
        val done = Path().apply {
          val start = pt(0f); moveTo(start.x, start.y)
          var t = 0f
          while (t <= f) { val p = pt(t); lineTo(p.x, p.y); t += 0.02f }
        }
        drawPath(done, color = Color(0x99FFC83D), style = Stroke(width = 3f))
      }
      // Horizon ends.
      drawCircle(Color(0x66FFFFFF), 3f, pt(0f))
      drawCircle(Color(0x66FFFFFF), 3f, pt(1f))
      // The sun itself, with a soft glow.
      val sun = pt(f)
      drawCircle(sunColor.copy(alpha = 0.25f), 12f, sun)
      drawCircle(sunColor, 6f, sun)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text("☀ $riseLabel", color = Color(0xFFB0B0B0), fontSize = 13.sp)
      Text("☾ $setLabel", color = Color(0xFFB0B0B0), fontSize = 13.sp)
    }
  }
}

private fun currentMinuteOfDay(): Int {
  val c = Calendar.getInstance()
  return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** 365 (or 366) dots, one per day. Past days dim, today bright, future faint. */
@Composable
private fun YearDots(dayOfYear: Int, daysInYear: Int) {
  val cols = 31 // ~12 rows; reads as a tidy block
  Canvas(
      modifier = Modifier.fillMaxWidth()
          .height((((daysInYear + cols - 1) / cols) * 9).dp),
  ) {
    val gap = size.width / cols
    val r = (gap * 0.30f).coerceAtMost(size.height / (((daysInYear + cols - 1) / cols)) * 0.30f)
    for (i in 0 until daysInYear) {
      val col = i % cols
      val row = i / cols
      val cx = gap * col + gap / 2f
      val cy = gap * row + gap / 2f
      val color = when {
        i + 1 < dayOfYear -> past
        i + 1 == dayOfYear -> accent
        else -> future
      }
      drawCircle(color = color, radius = r, center = Offset(cx, cy))
    }
  }
}

/** Current month as a Mon-first calendar grid; past days fade, today is lit. */
@Composable
private fun MonthGrid(daysInMonth: Int, today: Int, firstDayOffset: Int, monthName: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(monthName, color = Color(0xFF8A8A8A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val cells = firstDayOffset + daysInMonth
    val rows = (cells + 6) / 7
    var dayCounter = 1
    for (row in 0 until rows) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (col in 0 until 7) {
          val cellIndex = row * 7 + col
          val isDay = cellIndex >= firstDayOffset && dayCounter <= daysInMonth
          if (isDay) {
            val d = dayCounter
            val bg = if (d == today) accent else Color.Transparent
            val fg = when {
              d == today -> Color.White
              d < today -> Color(0xFF6A6A6E)
              else -> Color(0xFFDADADA)
            }
            Box(
                modifier = Modifier.weight(1f).height(22.dp)
                    .background(bg, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
              Text("$d", color = fg, fontSize = 12.sp,
                  fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
            }
            dayCounter++
          } else {
            Box(modifier = Modifier.weight(1f).height(22.dp))
          }
        }
      }
    }
  }
}
