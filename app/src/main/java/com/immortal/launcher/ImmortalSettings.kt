/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import android.text.format.DateFormat
import java.util.Locale

/**
 * Immortal's own user preferences (as opposed to the screensaver's, which live in
 * [ScreensaverConfig]). Reached from the "Immortal" tile in the launcher's
 * Settings folder. Everything here defaults to the pre-1.25 behaviour so existing
 * installs are unaffected until the user changes something.
 */
object ImmortalSettings {

  private const val PREFS = "immortal_settings"

  // Weather temperature unit.
  const val UNIT_AUTO = "auto" // follow the device locale
  const val UNIT_F = "f"
  const val UNIT_C = "c"

  // Home-grid tile size.
  const val SIZE_STANDARD = "standard" // 6 columns, 88dp tiles (the original look)
  const val SIZE_LARGE = "large" // 5 columns, 110dp tiles (closer to the stock launcher)
  const val SIZE_XL = "xl" // 4 columns, 140dp tiles (for the big-screen Portal+)

  // Optional home-screen weather forecast widget, shown below the app grid.
  const val WIDGET_OFF = "off" // no forecast (default)
  const val WIDGET_HOURLY = "hourly" // hour-by-hour for the next several hours
  const val WIDGET_DAILY = "daily" // a high/low for each of the next 7 days

  // Calendar widget
  const val CALENDAR_OFF = "off"
  const val CALENDAR_ON = "on"

  const val STATS_OFF = "off"
  const val STATS_ON = "on"

  // Screensaver activation — controls when the dream activates.
  // When true, the dream activates on sleep/dock (needed for the digital clock
  // to show when the screen turns off, since the Portal's stock behaviour is
  // to go straight to Asleep). When false, the device sleeps normally and
  // the dream only shows when explicitly triggered.
  const val ACTIVATE_ON_SLEEP_DEFAULT = true
  const val ACTIVATE_ON_DOCK_DEFAULT = true

  // Clock format for the launcher header, screensaver, and hourly forecast labels.
  const val CLOCK_AUTO = "auto" // follow the device's 24-hour system setting (default)
  const val CLOCK_12 = "12" // force 12-hour (e.g. 1:05, 1 PM)
  const val CLOCK_24 = "24" // force 24-hour (e.g. 13:05, 13)

  // Accent color options
  const val ACCENT_BLUE = "blue"
  const val ACCENT_GREEN = "green"
  const val ACCENT_PURPLE = "purple"
  const val ACCENT_ORANGE = "orange"
  const val ACCENT_PINK = "pink"
  const val ACCENT_TEAL = "teal"
  const val ACCENT_RED = "red"
  const val ACCENT_YELLOW = "yellow"
  const val ACCENT_INDIGO = "indigo"
  const val ACCENT_CYAN = "cyan"
  const val ACCENT_LIME = "lime"
  const val ACCENT_AMBER = "amber"
  const val ACCENT_DEEP_PURPLE = "deep_purple"
  const val ACCENT_BROWN = "brown"
  const val ACCENT_CORAL = "coral"
  const val ACCENT_MINT = "mint"

  // Background image options
  const val BG_DARK = "dark"        // default dark background
  const val BG_IMAGE = "image"      // custom user image
  const val BG_BLUR = "blur"        // image with blur effect

  data class Settings(
      val weatherUnit: String = UNIT_AUTO,
      val tileSize: String = SIZE_STANDARD,
      val weatherWidget: String = WIDGET_OFF,
      val calendarWidget: String = CALENDAR_OFF,
      val statsMode: String = STATS_OFF,
      val clockFormat: String = CLOCK_AUTO,
      val accentColor: String = ACCENT_BLUE,
      val backgroundType: String = BG_DARK,
      val backgroundImagePath: String? = null,
      val activateOnSleep: Boolean = ACTIVATE_ON_SLEEP_DEFAULT,
      val activateOnDock: Boolean = ACTIVATE_ON_DOCK_DEFAULT,
  )

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context): Settings {
    val p = prefs(context)
    return Settings(
        weatherUnit = p.getString("weather_unit", UNIT_AUTO) ?: UNIT_AUTO,
        tileSize = p.getString("tile_size", SIZE_STANDARD) ?: SIZE_STANDARD,
        weatherWidget = p.getString("weather_widget", WIDGET_OFF) ?: WIDGET_OFF,
        calendarWidget = p.getString("calendar_widget", CALENDAR_OFF) ?: CALENDAR_OFF,
        statsMode = p.getString("stats_mode", STATS_OFF) ?: STATS_OFF,
        clockFormat = p.getString("clock_format", CLOCK_AUTO) ?: CLOCK_AUTO,
        accentColor = p.getString("accent_color", ACCENT_BLUE) ?: ACCENT_BLUE,
        backgroundType = p.getString("background_type", BG_DARK) ?: BG_DARK,
        backgroundImagePath = p.getString("background_image_path", null),
        activateOnSleep = p.getBoolean("activate_on_sleep", ACTIVATE_ON_SLEEP_DEFAULT),
        activateOnDock = p.getBoolean("activate_on_dock", ACTIVATE_ON_DOCK_DEFAULT),
    )
  }

  fun setWeatherUnit(c: Context, unit: String) =
      prefs(c).edit().putString("weather_unit", unit).apply()

  fun setTileSize(c: Context, size: String) = prefs(c).edit().putString("tile_size", size).apply()

  fun setWeatherWidget(c: Context, mode: String) =
      prefs(c).edit().putString("weather_widget", mode).apply()

  fun setCalendarWidget(c: Context, mode: String) =
      prefs(c).edit().putString("calendar_widget", mode).apply()

  fun setStatsMode(c: Context, mode: String) =
      prefs(c).edit().putString("stats_mode", mode).apply()

  fun setActivateOnSleep(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("activate_on_sleep", on).apply()

  fun setActivateOnDock(c: Context, on: Boolean) =
      prefs(c).edit().putBoolean("activate_on_dock", on).apply()

  fun setClockFormat(c: Context, fmt: String) =
      prefs(c).edit().putString("clock_format", fmt).apply()

  fun setAccentColor(c: Context, color: String) =
      prefs(c).edit().putString("accent_color", color).apply()

  fun setBackgroundType(c: Context, type: String) =
      prefs(c).edit().putString("background_type", type).apply()

  fun setBackgroundImagePath(c: Context, path: String?) =
      prefs(c).edit().putString("background_image_path", path).apply()

  /**
   * Whether the clock should render in 24-hour form. AUTO follows the device's
   * system 24-hour setting; 12/24 force it. Reads [DateFormat.is24HourFormat] for
   * the AUTO case; see [resolve24Hour] for the pure, testable core.
   */
  fun use24HourClock(context: Context): Boolean =
      resolve24Hour(load(context).clockFormat, DateFormat.is24HourFormat(context))

  /** Pure resolution of the clock preference against the system setting. */
  fun resolve24Hour(clockFormat: String, systemIs24Hour: Boolean): Boolean =
      when (clockFormat) {
        CLOCK_24 -> true
        CLOCK_12 -> false
        else -> systemIs24Hour
      }

  /** Resolved unit for a fetch: true → Fahrenheit, false → Celsius. */
  fun useFahrenheit(context: Context): Boolean =
      when (load(context).weatherUnit) {
        UNIT_F -> true
        UNIT_C -> false
        else -> localeUsesFahrenheit()
      }

  /**
   * The handful of territories that use Fahrenheit day-to-day; everywhere else
   * gets Celsius. Pure + injectable for unit tests.
   */
  fun localeUsesFahrenheit(locale: Locale = Locale.getDefault()): Boolean =
      locale.country.uppercase(Locale.ROOT) in FAHRENHEIT_COUNTRIES

  private val FAHRENHEIT_COUNTRIES =
      setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH", "PR", "GU", "VI", "AS")
}
