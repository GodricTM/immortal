/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher.settings

import android.content.Context
import com.immortal.launcher.CalendarFeed
import com.immortal.launcher.CalendarUrlEntryActivity
import com.immortal.launcher.DreamPolicy
import com.immortal.launcher.FaceCatalog
import com.immortal.launcher.FacePickerActivity
import com.immortal.launcher.FleetCalendar
import com.immortal.launcher.FleetConfig
import com.immortal.launcher.FleetScreensaver
import com.immortal.launcher.FrameMode
import com.immortal.launcher.PhotoFramePreviewActivity
import com.immortal.launcher.ScreensaverDismiss
import com.immortal.launcher.ScreensaverDismissAppActivity
import com.immortal.launcher.ScreensaverSourcesActivity
import com.immortal.launcher.ImmortalSettings
import com.immortal.launcher.MqttConfig
import com.immortal.launcher.MqttService
import com.immortal.launcher.MultiRoomService
import com.immortal.launcher.QuickBar
import com.immortal.launcher.QuickBarConfig
import com.immortal.launcher.ScreensaverConfig
import com.immortal.launcher.SettingsGuard
import org.json.JSONArray

/**
 * The registered settings domains — the single source of truth that the persistence façades, the
 * phone-remote PWA, and (later) the on-device settings UI all read from. Each domain binds to an
 * existing `*Config` object's getters/setters, so storage and its clamps are untouched.
 */
object SettingsDomains {

  private val CAL_SIZE_LABELS = listOf("Small", "Medium", "Large")

  private fun rangeLabel(range: String): String =
      when (range) {
        CalendarFeed.RANGE_DAY -> "Day"
        CalendarFeed.RANGE_3DAY -> "3 days"
        CalendarFeed.RANGE_WEEK -> "Week"
        CalendarFeed.RANGE_AGENDA -> "Agenda"
        else -> range
      }

  /**
   * The screensaver calendar widget. Mirrors the legacy `FleetCalendar` wire format exactly: the
   * writable controls are `widgetOn` (the on/off toggle, also accepted as the legacy alias
   * `enabled`), the feed `url`, and the `range`/`size`/`side`. The remaining keys are derived,
   * read-only views the client uses to distinguish "linked but hidden" from "no link" and to warn
   * on an unfetchable feed — `enabled` (effective: link set AND toggle on), `hasLink`, `provider`,
   * `supported`, and the advertised `ranges`.
   */
  val calendar: SettingsDomain<ScreensaverConfig.Settings> =
      SettingsDomain(
          id = "calendar",
          title = "Calendar",
          load = ScreensaverConfig::load,
          specs =
              listOf(
                  BoolSpec(
                      key = "widgetOn",
                      title = "Show calendar",
                      get = { it.calendarEnabled },
                      set = ScreensaverConfig::setCalendarEnabled,
                      aliases = listOf("enabled"),
                      visible = { _, s -> s.hasCalendarLink },
                  ),
                  StringSpec(
                      key = "url",
                      title = "Calendar feed",
                      get = { it.calendarUrl ?: "" },
                      set = ScreensaverConfig::setCalendarUrl,
                      entry = Entry.Nav(CalendarUrlEntryActivity::class.java),
                      help =
                          "A public iCalendar (.ics) link - a Google \"secret iCal\" address or an Apple " +
                              "iCloud public-calendar link. Shows your upcoming events on the frame.",
                  ),
                  EnumSpec(
                      key = "range",
                      title = "Show",
                      get = { it.calendarRange },
                      set = ScreensaverConfig::setCalendarRange,
                      options = FleetCalendar.RANGES.map { it to rangeLabel(it) },
                      visible = { _, s -> s.usesCalendar },
                  ),
                  IntSpec(
                      key = "size",
                      title = "Size",
                      get = { it.calendarSize },
                      set = ScreensaverConfig::setCalendarSize,
                      min = 0,
                      max = 2,
                      step = 1,
                      format = { CAL_SIZE_LABELS.getOrElse(it) { _ -> it.toString() } },
                      visible = { _, s -> s.usesCalendar },
                  ),
                  EnumSpec(
                      key = "side",
                      title = "Side",
                      get = { it.calendarSide },
                      set = ScreensaverConfig::setCalendarSide,
                      options =
                          listOf(
                              ScreensaverConfig.CAL_SIDE_LEFT to "Left",
                              ScreensaverConfig.CAL_SIDE_RIGHT to "Right"),
                      visible = { _, s -> s.usesCalendar },
                  ),
                  // Read-only derived views (flat-payload-only — they round-trip the legacy format).
                  DerivedSpec(key = "enabled", get = { it.usesCalendar }),
                  DerivedSpec(key = "hasLink", get = { it.hasCalendarLink }),
                  DerivedSpec(
                      key = "provider",
                      get = {
                        val u = it.calendarUrl.orEmpty()
                        if (u.isBlank()) "" else CalendarFeed.providerName(u)
                      }),
                  DerivedSpec(
                      key = "supported",
                      get = {
                        val u = it.calendarUrl.orEmpty()
                        u.isNotBlank() && CalendarFeed.isSupported(u)
                      }),
                  DerivedSpec(key = "ranges", get = { JSONArray(FleetCalendar.RANGES) }),
              ),
          defaults = { ScreensaverConfig.Settings() },
      )

  private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

  /**
   * A strict [EnumSpec] coercer: accept the value only if it's one of [allowed], else skip the write.
   * For enums whose setter does NOT normalise its input (e.g. the Immortal display enums write the
   * raw string straight to prefs), so a remote push of an unrecognised value can't persist garbage
   * into a constrained field. Enums whose setter normalises (calendar range/side) keep the default
   * pass-through coercer.
   */
  private fun oneOf(vararg allowed: String): (String) -> String? = { v -> v.takeIf { it in allowed } }

  /** Short label for the active photo source, shown on the "Photo source" nav row. */
  private fun sourceLabel(s: ScreensaverConfig.Settings): String =
      when {
        s.usesImmich -> "Immich"
        s.usesSmb -> "Network share"
        s.usesDav -> "WebDAV"
        s.usesWebUrl -> "Web page"
        s.usesUrl -> "Shared album"
        s.usesFolder -> "Local folder"
        else -> "Immortal photos"
      }

  /**
   * The photo-frame display settings — the slice the legacy `FleetScreensaver.toJson` reports.
   * Mirrors that flat wire format exactly (16 keys). The 13 plain display controls are writable
   * specs bound to the proven `ScreensaverConfig` setters; `source`/`folderPath`/`albumUrl` are
   * read-only here because their writes flip the active source atomically and belong to the
   * credential-group apply path (migrated, with the `/remote/settings` work, in a later phase).
   *
   * NOTE: this domain's [SettingsDomain.apply] is not yet wired into `FleetScreensaver.apply`; only
   * the read side ([SettingsDomain.flatJson]) backs the façade today. When apply migrates (with the
   * `/remote/settings` work), this domain gains the credential `GroupSpec`s and an `onApplied` hook
   * lifting the route-layer side effects (FleetRoutes reaffirm + overnight reschedule).
   */
  val screensaver: SettingsDomain<ScreensaverConfig.Settings> =
      SettingsDomain(
          id = "screensaver",
          title = "Screensaver",
          load = ScreensaverConfig::load,
          specs =
              listOf(
                  BoolSpec(
                      "enabled",
                      "Show the photo-frame screensaver",
                      get = { it.enabled },
                      set = ScreensaverConfig::setEnabled,
                      help =
                          "Turn this off to let your Portal's screen sleep on its own timer (or run " +
                              "your own screensaver). Immortal won't switch it back on."),
                  NavSpec(
                      "clockFace",
                      "Clock face",
                      value = { _, s -> FaceCatalog.entryFor(s.faceId).name },
                      activity = FacePickerActivity::class.java,
                      help =
                          "Choose how the time looks - the classic corner clock, a big centred clock, " +
                              "or the full-screen flip clock.",
                      visible = { _, s -> s.enabled }),
                  NavSpec(
                      "photoSource",
                      "Photo source",
                      value = { _, s -> sourceLabel(s) },
                      activity = ScreensaverSourcesActivity::class.java,
                      help =
                          "Where your photos come from - the built-in feed, your own folder or a shared " +
                              "album, or a self-hosted source like Immich or a NAS.",
                      visible = { _, s -> s.enabled }),
                  NavSpec(
                      "dismissTarget",
                      "Open when you tap to exit",
                      value = { c, _ -> ScreensaverDismiss.chosenLabel(c)?.let { "Opens $it" } ?: "Immortal launcher" },
                      activity = ScreensaverDismissAppActivity::class.java,
                      help =
                          "Tapping the screensaver wakes the Portal. By default that brings you home to " +
                              "Immortal - or pick an app (like Home Assistant) to drop straight into.",
                      visible = { _, s -> s.enabled }),
                  DerivedSpec("source", get = { it.source }),
                  DerivedSpec("folderPath", get = { it.folderPath ?: "" }),
                  DerivedSpec("albumUrl", get = { it.albumUrl ?: "" }),
                  IntSpec(
                      "albumRefreshMin",
                      "Album refresh",
                      get = { it.albumRefreshMin },
                      set = ScreensaverConfig::setAlbumRefreshMin,
                      min = 15,
                      max = 24 * 60,
                      step = 15,
                      format = { "$it min" },
                      help = "How often to re-fetch the photo list from a network album source.",
                      // Only meaningful for a network album (Immich/URL/SMB/WebDAV/web feed); the
                      // built-in feed and a local folder don't poll a remote listing, so on-device
                      // this row only appears for those sources (it was remote-only before).
                      visible = { _, s -> !s.usesFolder && s.source != ScreensaverConfig.SOURCE_DEFAULT }),
                  EnumSpec(
                      "fit",
                      "Fit",
                      get = { it.fit },
                      set = ScreensaverConfig::setFit,
                      options =
                          listOf(ScreensaverConfig.FIT_FILL to "Fill", ScreensaverConfig.FIT_FIT to "Fit"),
                      coerce = { FleetScreensaver.coerceFit(it) }),
                  IntSpec(
                      "intervalSec",
                      "Photo interval",
                      get = { it.intervalSec },
                      set = ScreensaverConfig::setInterval,
                      min = 5,
                      max = 600,
                      step = 5,
                      format = { "${it}s" }),
                  BoolSpec("shuffle", "Shuffle", get = { it.shuffle }, set = ScreensaverConfig::setShuffle),
                  BoolSpec(
                      "includeVideo",
                      "Play videos",
                      get = { it.includeVideo },
                      set = ScreensaverConfig::setIncludeVideo),
                  BoolSpec(
                      "batterySaver",
                      "Sleep on battery when nobody's around",
                      get = { it.batterySaver },
                      set = ScreensaverConfig::setBatterySaver,
                      help =
                          "Unplugged, keep showing photos while someone's nearby and sleep when the room " +
                              "empties (saves the battery). Off: the frame stays on, on battery too.",
                      visible = { c, _ -> DreamPolicy.hasBattery(c) }),
                  BoolSpec(
                      "showNowPlaying",
                      "Show now playing",
                      get = { it.showNowPlaying },
                      set = ScreensaverConfig::setShowNowPlaying,
                      help = "Overlay the current track and cover art on the photo frame while music is playing."),
                  BoolSpec(
                      "antiBurnIn",
                      "Reduce screen burn-in",
                      get = { it.antiBurnIn },
                      set = ScreensaverConfig::setAntiBurnIn),
                  EnumSpec(
                      "presenceMode",
                      "Power",
                      get = { it.presenceMode.name },
                      set = { c, v -> ScreensaverConfig.setPresenceMode(c, FrameMode.valueOf(v)) },
                      options =
                          listOf(
                              FrameMode.ALWAYS_ON.name to "Always on",
                              FrameMode.PRESENCE.name to "Follow presence"),
                      coerce = { FleetScreensaver.coercePresenceMode(it)?.name },
                      help =
                          "Follow presence: photos while someone's around, screen off (and multi-room " +
                              "music paused) when the room empties. Always on: a permanent frame on mains power."),
                  IntSpec(
                      "idleSleepMin",
                      "Idle screen-off",
                      get = { it.idleSleepMin },
                      set = ScreensaverConfig::setIdleSleepMin,
                      min = 0,
                      max = 120,
                      step = 5,
                      format = { if (it <= 0) "Off" else "$it min" },
                      help =
                          "After the screensaver shows this long with no touch, the screen turns off; " +
                              "tap to wake. A simple timer - it can't tell whether someone's in the room."),
                  BoolSpec(
                      "overnightEnabled",
                      "Overnight screen-off",
                      get = { it.overnightEnabled },
                      set = ScreensaverConfig::setOvernightEnabled,
                      help = "Keep the screen off (or show a dim night clock) between two times every night."),
                  IntSpec(
                      "overnightStartMin",
                      "Off from",
                      get = { it.overnightStartMin },
                      set = ScreensaverConfig::setOvernightStartMin,
                      min = 0,
                      max = 24 * 60 - 1,
                      step = 15,
                      wrap = true,
                      format = ::hhmm,
                      visible = { _, s -> s.overnightEnabled }),
                  IntSpec(
                      "overnightEndMin",
                      "Off until",
                      get = { it.overnightEndMin },
                      set = ScreensaverConfig::setOvernightEndMin,
                      min = 0,
                      max = 24 * 60 - 1,
                      step = 15,
                      wrap = true,
                      format = ::hhmm,
                      visible = { _, s -> s.overnightEnabled }),
                  BoolSpec(
                      "overnightNightClock",
                      "Show a dim night clock",
                      get = { it.overnightNightClock },
                      set = ScreensaverConfig::setOvernightNightClock,
                      visible = { _, s -> s.overnightEnabled }),
                  // ---- Fork-specific screensaver controls ----
                  EnumSpec(
                      "feed",
                      "Photo feed",
                      get = { it.feed },
                      set = ScreensaverConfig::setFeed,
                      options = ScreensaverConfig.FEEDS.map { it to ScreensaverConfig.feedLabel(it) },
                      coerce = { v -> v.takeIf { it in ScreensaverConfig.FEEDS } },
                      visible = { _, s -> s.source == ScreensaverConfig.SOURCE_DEFAULT },
                      help = "Which online photo feed to use with the built-in source."),
                  BoolSpec(
                      "sleepTimerEnabled",
                      "Sleep timer",
                      get = { it.sleepTimerEnabled },
                      set = ScreensaverConfig::setSleepTimerEnabled,
                      help = "Turn the screensaver off after a set time."),
                  IntSpec(
                      "sleepTimerMin",
                      "Sleep after",
                      get = { it.sleepTimerMin },
                      set = ScreensaverConfig::setSleepTimerMin,
                      min = 1,
                      max = 240,
                      step = 5,
                      format = { "$it min" },
                      visible = { _, s -> s.sleepTimerEnabled }),
                  BoolSpec(
                      "pauseAudioOnSleep",
                      "Pause audio on sleep",
                      get = { it.pauseAudioOnSleep },
                      set = ScreensaverConfig::setPauseAudioOnSleep,
                      visible = { _, s -> s.sleepTimerEnabled }),
                  BoolSpec(
                      "closeAppOnSleep",
                      "Close app on sleep",
                      get = { it.closeAppOnSleep },
                      set = ScreensaverConfig::setCloseAppOnSleep,
                      visible = { _, s -> s.sleepTimerEnabled }),
                  EnumSpec(
                      "soundscape",
                      "Ambient sound",
                      get = { it.soundscape },
                      set = ScreensaverConfig::setSoundscape,
                      options = ScreensaverConfig.SOUNDSCAPES.map { it to ScreensaverConfig.soundscapeLabel(it) },
                      coerce = { v -> v.takeIf { it in ScreensaverConfig.SOUNDSCAPES } },
                      help = "Synthesised ambient sound played while the screensaver shows."),
                  IntSpec(
                      "soundscapeVolume",
                      "Soundscape volume",
                      get = { it.soundscapeVolume },
                      set = ScreensaverConfig::setSoundscapeVolume,
                      min = 0,
                      max = 100,
                      step = 5,
                      format = { "$it%" },
                      visible = { _, s -> s.soundscape != ScreensaverConfig.SOUND_OFF }),
                  BoolSpec(
                      "ambientDashboard",
                      "Ambient dashboard",
                      get = { it.ambientDashboard },
                      set = ScreensaverConfig::setAmbientDashboard),
                  BoolSpec(
                      "gestureWave",
                      "Gesture wave to wake",
                      get = { it.gestureWave },
                      set = ScreensaverConfig::setGestureWave),
                  BoolSpec(
                      "welcomeEnabled",
                      "Welcome screen",
                      get = { it.welcomeEnabled },
                      set = ScreensaverConfig::setWelcomeEnabled),
              ),
          sections =
              mapOf(
                  "albumRefreshMin" to "Display",
                  "fit" to "Display",
                  "intervalSec" to "Display",
                  "shuffle" to "Display",
                  "includeVideo" to "Display",
                  "showNowPlaying" to "Display",
                  "antiBurnIn" to "Display",
                  "feed" to "Display",
                  "ambientDashboard" to "Display",
                  "gestureWave" to "Display",
                  "welcomeEnabled" to "Display",
                  "batterySaver" to "Power & sleep",
                  "presenceMode" to "Power & sleep",
                  "idleSleepMin" to "Power & sleep",
                  "overnightEnabled" to "Power & sleep",
                  "overnightStartMin" to "Power & sleep",
                  "overnightEndMin" to "Power & sleep",
                  "overnightNightClock" to "Power & sleep",
                  "sleepTimerEnabled" to "Sleep timer",
                  "sleepTimerMin" to "Sleep timer",
                  "pauseAudioOnSleep" to "Sleep timer",
                  "closeAppOnSleep" to "Sleep timer",
                  "soundscape" to "Audio",
                  "soundscapeVolume" to "Audio"),
          defaults = { ScreensaverConfig.Settings() },
          // The screensaver's post-apply side effects, lifted from the route layer (the same
          // reaffirm + overnight reschedule that `RemoteRoutes.applyConfig` / `FleetRoutes` run).
          // Fires once per /remote/settings batch; the legacy /screensaver and /remote/sources
          // routes keep their own reaffirm, so there's no double-fire.
          onApplied = { c, keys -> SettingsGuard.afterScreensaverApply(c, keys) },
      )

  /**
   * Immortal's own launcher preferences ([ImmortalSettings]). The multi-room fields gate on the
   * master toggle. `hideStatusBar` re-applies the immersive policy; the multi-room fields resync
   * the Snapcast/MA bridge — the same side effects the on-device screen runs.
   */
  val immortal: SettingsDomain<ImmortalSettings.Settings> =
      SettingsDomain(
          id = "immortal",
          title = "Immortal",
          load = ImmortalSettings::load,
          specs =
              listOf(
                  EnumSpec(
                      "weatherUnit",
                      "Temperature unit",
                      get = { it.weatherUnit },
                      set = ImmortalSettings::setWeatherUnit,
                      options =
                          listOf(
                              ImmortalSettings.UNIT_AUTO to "Auto",
                              ImmortalSettings.UNIT_F to "Fahrenheit",
                              ImmortalSettings.UNIT_C to "Celsius"),
                      coerce = oneOf(ImmortalSettings.UNIT_AUTO, ImmortalSettings.UNIT_F, ImmortalSettings.UNIT_C),
                      help = "Auto follows your Portal's language & region setting."),
                  EnumSpec(
                      "tileSize",
                      "App tile size",
                      get = { it.tileSize },
                      set = ImmortalSettings::setTileSize,
                      options =
                          listOf(
                              ImmortalSettings.SIZE_STANDARD to "Standard",
                              ImmortalSettings.SIZE_LARGE to "Large",
                              ImmortalSettings.SIZE_XL to "XL"),
                      coerce = oneOf(ImmortalSettings.SIZE_STANDARD, ImmortalSettings.SIZE_LARGE, ImmortalSettings.SIZE_XL),
                      help = "Large is closer to the stock Portal launcher."),
                  EnumSpec(
                      "weatherWidget",
                      "Weather widget",
                      get = { it.weatherWidget },
                      set = ImmortalSettings::setWeatherWidget,
                      options =
                          listOf(
                              ImmortalSettings.WIDGET_OFF to "Off",
                              ImmortalSettings.WIDGET_HOURLY to "Hourly",
                              ImmortalSettings.WIDGET_DAILY to "Daily"),
                      coerce = oneOf(ImmortalSettings.WIDGET_OFF, ImmortalSettings.WIDGET_HOURLY, ImmortalSettings.WIDGET_DAILY),
                      help = "Show a forecast below your apps. Off by default."),
                  EnumSpec(
                      "clockFormat",
                      "Clock",
                      get = { it.clockFormat },
                      set = ImmortalSettings::setClockFormat,
                      options =
                          listOf(
                              ImmortalSettings.CLOCK_AUTO to "Auto",
                              ImmortalSettings.CLOCK_12 to "12-hour",
                              ImmortalSettings.CLOCK_24 to "24-hour"),
                      coerce = oneOf(ImmortalSettings.CLOCK_AUTO, ImmortalSettings.CLOCK_12, ImmortalSettings.CLOCK_24),
                      help =
                          "Applies to the home screen, screensaver and forecast. Auto follows your Portal's system setting."),
                  BoolSpec(
                      "showMiniPlayer",
                      "Now-playing mini-player",
                      get = { it.showMiniPlayer },
                      set = ImmortalSettings::setShowMiniPlayer,
                      help =
                          "Show the current track, cover art and controls in the header while music is playing."),
                  BoolSpec(
                      "hideStatusBar",
                      "Hide status bar",
                      get = { it.hideStatusBar },
                      set = ImmortalSettings::setHideStatusBar,
                      help =
                          "Hidden by default for a cleaner full-screen look. Swipe down from the top to reveal it briefly."),
                  BoolSpec(
                      "constrainPageWidth",
                      "Constrain page width",
                      get = { it.constrainPageWidth },
                      set = ImmortalSettings::setConstrainPageWidth,
                      help =
                          "Cap the home screen width on large landscape displays instead of filling the whole panel. Off by default."),
                  BoolSpec(
                      "multiRoomEnabled",
                      "Multi-room audio",
                      get = { it.multiRoomEnabled },
                      set = ImmortalSettings::setMultiRoomEnabled),
                  StringSpec(
                      "snapcastHost",
                      "Snapcast host",
                      get = { it.snapcastHost },
                      set = ImmortalSettings::setSnapcastHost,
                      visible = { _, s -> s.multiRoomEnabled }),
                  IntSpec(
                      "maPort",
                      "Music Assistant port",
                      get = { it.maPort },
                      set = ImmortalSettings::setMaPort,
                      min = 1,
                      max = 65535,
                      asText = true,
                      help = "Music Assistant's web server port — 8095 by default.",
                      visible = { _, s -> s.multiRoomEnabled }),
                  StringSpec(
                      "maUsername",
                      "Music Assistant user",
                      get = { it.maUsername },
                      set = ImmortalSettings::setMaUsername,
                      visible = { _, s -> s.multiRoomEnabled }),
                  StringSpec(
                      "maPassword",
                      "Music Assistant password",
                      get = { it.maPassword },
                      set = ImmortalSettings::setMaPassword,
                      secret = true,
                      visible = { _, s -> s.multiRoomEnabled }),
                  // ---- Fork-specific home-screen controls ----
                  BoolSpec(
                      "showSunTimes",
                      "Sunrise & sunset",
                      get = { it.showSunTimes },
                      set = ImmortalSettings::setShowSunTimes,
                      help = "Show today's sunrise and sunset under the header weather."),
                  BoolSpec(
                      "showNameDay",
                      "Name day",
                      get = { it.showNameDay },
                      set = ImmortalSettings::setShowNameDay,
                      help = "Show today's Romanian name day (onomastica) in the header."),
                  BoolSpec(
                      "showFeastDay",
                      "Orthodox feast day",
                      get = { it.showFeastDay },
                      set = ImmortalSettings::setShowFeastDay,
                      help = "Show today's Orthodox feast in the header."),
                  BoolSpec(
                      "showNextEvent",
                      "Next calendar event",
                      get = { it.showNextEvent },
                      set = ImmortalSettings::setShowNextEvent,
                      help = "Show the next upcoming calendar event in the header."),
                  BoolSpec(
                      "showTabs",
                      "Category tabs",
                      get = { it.showTabs },
                      set = ImmortalSettings::setShowTabs,
                      help = "Show All, Apps, Tools, and Settings tabs above the grid for quick filtering."),
                  BoolSpec(
                      "dashboardPage",
                      "Dashboard page",
                      get = { it.dashboardPage },
                      set = ImmortalSettings::setDashboardPage,
                      help = "Add a second swipeable glanceable dashboard home page."),
                  BoolSpec(
                      "showTimeProgress",
                      "Year progress card",
                      get = { it.showTimeProgress },
                      set = ImmortalSettings::setShowTimeProgress,
                      help = "A 'year is N% gone' card on the dashboard page.",
                      visible = { _, s -> s.dashboardPage }),
                  BoolSpec(
                      "showDashClock",
                      "Dashboard clock",
                      get = { it.showDashClock },
                      set = ImmortalSettings::setShowDashClock,
                      visible = { _, s -> s.dashboardPage }),
                  BoolSpec(
                      "showDashCountdowns",
                      "Dashboard countdowns",
                      get = { it.showDashCountdowns },
                      set = ImmortalSettings::setShowDashCountdowns,
                      visible = { _, s -> s.dashboardPage }),
                  EnumSpec(
                      "sortMode",
                      "App sort",
                      get = { it.sortMode },
                      set = ImmortalSettings::setSortMode,
                      options =
                          listOf(
                              ImmortalSettings.SORT_MANUAL to "Manual",
                              ImmortalSettings.SORT_AZ to "A-Z",
                              ImmortalSettings.SORT_USED to "Most used",
                              ImmortalSettings.SORT_RECENT to "Recently installed"),
                      coerce = oneOf(
                          ImmortalSettings.SORT_MANUAL, ImmortalSettings.SORT_AZ,
                          ImmortalSettings.SORT_USED, ImmortalSettings.SORT_RECENT),
                      help = "How apps are ordered on the grid."),
                  EnumSpec(
                      "calendarWidget",
                      "Calendar widget",
                      get = { it.calendarWidget },
                      set = ImmortalSettings::setCalendarWidget,
                      options =
                          listOf(
                              ImmortalSettings.CALENDAR_OFF to "Off",
                              ImmortalSettings.CALENDAR_ON to "On"),
                      coerce = oneOf(ImmortalSettings.CALENDAR_OFF, ImmortalSettings.CALENDAR_ON)),
                  EnumSpec(
                      "statsMode",
                      "System stats",
                      get = { it.statsMode },
                      set = ImmortalSettings::setStatsMode,
                      options =
                          listOf(
                              ImmortalSettings.STATS_OFF to "Off",
                              ImmortalSettings.STATS_ON to "On"),
                      coerce = oneOf(ImmortalSettings.STATS_OFF, ImmortalSettings.STATS_ON)),
                  EnumSpec(
                      "accentColor",
                      "Accent colour",
                      get = { it.accentColor },
                      set = ImmortalSettings::setAccentColor,
                      options =
                          listOf(
                              ImmortalSettings.ACCENT_BLUE to "Blue",
                              ImmortalSettings.ACCENT_GREEN to "Green",
                              ImmortalSettings.ACCENT_PURPLE to "Purple",
                              ImmortalSettings.ACCENT_ORANGE to "Orange",
                              ImmortalSettings.ACCENT_PINK to "Pink",
                              ImmortalSettings.ACCENT_TEAL to "Teal",
                              ImmortalSettings.ACCENT_RED to "Red",
                              ImmortalSettings.ACCENT_YELLOW to "Yellow",
                              ImmortalSettings.ACCENT_INDIGO to "Indigo",
                              ImmortalSettings.ACCENT_CYAN to "Cyan",
                              ImmortalSettings.ACCENT_LIME to "Lime",
                              ImmortalSettings.ACCENT_AMBER to "Amber",
                              ImmortalSettings.ACCENT_DEEP_PURPLE to "Deep purple",
                              ImmortalSettings.ACCENT_BROWN to "Brown",
                              ImmortalSettings.ACCENT_CORAL to "Coral",
                              ImmortalSettings.ACCENT_MINT to "Mint"),
                      coerce = oneOf(
                          ImmortalSettings.ACCENT_BLUE, ImmortalSettings.ACCENT_GREEN,
                          ImmortalSettings.ACCENT_PURPLE, ImmortalSettings.ACCENT_ORANGE,
                          ImmortalSettings.ACCENT_PINK, ImmortalSettings.ACCENT_TEAL,
                          ImmortalSettings.ACCENT_RED, ImmortalSettings.ACCENT_YELLOW,
                          ImmortalSettings.ACCENT_INDIGO, ImmortalSettings.ACCENT_CYAN,
                          ImmortalSettings.ACCENT_LIME, ImmortalSettings.ACCENT_AMBER,
                          ImmortalSettings.ACCENT_DEEP_PURPLE, ImmortalSettings.ACCENT_BROWN,
                          ImmortalSettings.ACCENT_CORAL, ImmortalSettings.ACCENT_MINT),
                      help = "The accent colour used for highlights and active controls."),
                  EnumSpec(
                      "backgroundType",
                      "Background",
                      get = { it.backgroundType },
                      set = ImmortalSettings::setBackgroundType,
                      options =
                          listOf(
                              ImmortalSettings.BG_DARK to "Dark",
                              ImmortalSettings.BG_IMAGE to "Image",
                              ImmortalSettings.BG_BLUR to "Blurred image",
                              ImmortalSettings.BG_GRADIENT to "Gradient",
                              ImmortalSettings.BG_SKY to "Sky",
                              ImmortalSettings.BG_STARS to "Stars"),
                      coerce = oneOf(
                          ImmortalSettings.BG_DARK, ImmortalSettings.BG_IMAGE,
                          ImmortalSettings.BG_BLUR, ImmortalSettings.BG_GRADIENT,
                          ImmortalSettings.BG_SKY, ImmortalSettings.BG_STARS),
                      help = "The home screen background."),
                  EnumSpec(
                      "backgroundGradient",
                      "Gradient preset",
                      get = { it.backgroundGradient },
                      set = ImmortalSettings::setBackgroundGradient,
                      options = ImmortalSettings.GRADIENTS.map { it.first to ImmortalSettings.gradientLabel(it.first) },
                      coerce = { v -> v.takeIf { it in ImmortalSettings.GRADIENTS.map { g -> g.first } } },
                      visible = { _, s -> s.backgroundType == ImmortalSettings.BG_GRADIENT }),
                  BoolSpec(
                      "showDayProgress",
                      "Day progress bar",
                      get = { it.showDayProgress },
                      set = ImmortalSettings::setShowDayProgress,
                      visible = { _, s -> s.backgroundType == ImmortalSettings.BG_SKY },
                      help = "Thin top bar showing the day's progress (with the Sky background)."),
                  EnumSpec(
                      "dailyTileMode",
                      "Daily tile",
                      get = { it.dailyTileMode },
                      set = ImmortalSettings::setDailyTileMode,
                      options =
                          listOf(
                              "off" to "Off",
                              "quote" to "Quote",
                              "word" to "Word",
                              "trivia" to "Trivia"),
                      coerce = oneOf("off", "quote", "word", "trivia")),
                  BoolSpec(
                      "activateOnSleep",
                      "Activate screensaver on sleep",
                      get = { it.activateOnSleep },
                      set = ImmortalSettings::setActivateOnSleep,
                      help = "Start the dream when the screen turns off."),
                  BoolSpec(
                      "activateOnDock",
                      "Activate screensaver on dock",
                      get = { it.activateOnDock },
                      set = ImmortalSettings::setActivateOnDock,
                      help = "Start the dream when docked."),
              ),
          sections =
              mapOf(
                  "weatherUnit" to "Weather",
                  "weatherWidget" to "Weather",
                  "tileSize" to "Home screen",
                  "showMiniPlayer" to "Home screen",
                  "hideStatusBar" to "Home screen",
                  "constrainPageWidth" to "Home screen",
                  "clockFormat" to "Clock",
                  "multiRoomEnabled" to "Audio",
                  "snapcastHost" to "Audio",
                  "maPort" to "Audio",
                  "maUsername" to "Audio",
                  "maPassword" to "Audio",
                  "calendarWidget" to "Home screen",
                  "statsMode" to "Home screen",
                  "accentColor" to "Home screen",
                  "backgroundType" to "Home screen",
                  "backgroundGradient" to "Home screen",
                  "showDayProgress" to "Home screen",
                  "showSunTimes" to "Home screen",
                  "showNameDay" to "Home screen",
                  "showFeastDay" to "Home screen",
                  "showNextEvent" to "Home screen",
                  "dailyTileMode" to "Home screen",
                  "sortMode" to "Home screen",
                  "showTabs" to "Home screen",
                  "dashboardPage" to "Dashboard",
                  "showTimeProgress" to "Dashboard",
                  "showDashClock" to "Dashboard",
                  "showDashCountdowns" to "Dashboard",
                  "activateOnSleep" to "Screensaver",
                  "activateOnDock" to "Screensaver"),
          defaults = { ImmortalSettings.Settings() },
          onApplied = { c, keys ->
            if ("hideStatusBar" in keys) SettingsGuard.applyStatusBar(c)
            if (keys.any { it in setOf("multiRoomEnabled", "snapcastHost", "maPort", "maUsername", "maPassword") })
                MultiRoomService.sync(c)
          },
      )

  /**
   * The Home Assistant MQTT publisher ([MqttConfig]). Uses the Context itself as the snapshot
   * (this config has no aggregate `Settings`); the broker fields gate on the master toggle, and
   * cert validation on TLS. [SettingsDomain.explicitApply] = batch so a future on-device renderer
   * doesn't reconnect the broker per keystroke; each apply resyncs the service once.
   *
   * WARNING — Context-as-snapshot: because the snapshot is the Context (live reads), it never
   * "changes" on apply, so rendering this domain through the on-device [SettingsList] would NOT
   * recompose after a toggle (the value updates on disk but Compose sees no state change). It's
   * served on-device by the bespoke `MqttScreen` (which holds its own `mutableStateOf`); only the
   * remote renders it generically. Give it a real snapshot `Settings` before pointing `SettingsList`
   * at it.
   */
  val mqtt: SettingsDomain<Context> =
      SettingsDomain(
          id = "mqtt",
          title = "Home Assistant (MQTT)",
          load = { it },
          explicitApply = true,
          specs =
              listOf(
                  BoolSpec("enabled", "Publish to MQTT", get = { MqttConfig.isEnabled(it) }, set = MqttConfig::setEnabled),
                  StringSpec(
                      "host",
                      "Broker host",
                      get = { MqttConfig.host(it) },
                      set = MqttConfig::setHost,
                      visible = { c, _ -> MqttConfig.isEnabled(c) }),
                  IntSpec(
                      "port",
                      "Port",
                      get = { MqttConfig.port(it) },
                      set = MqttConfig::setPort,
                      min = 1,
                      max = 65535,
                      asText = true,
                      visible = { c, _ -> MqttConfig.isEnabled(c) }),
                  StringSpec(
                      "username",
                      "Username",
                      get = { MqttConfig.username(it) },
                      set = MqttConfig::setUsername,
                      visible = { c, _ -> MqttConfig.isEnabled(c) }),
                  StringSpec(
                      "password",
                      "Password",
                      get = { MqttConfig.password(it) },
                      set = MqttConfig::setPassword,
                      secret = true,
                      visible = { c, _ -> MqttConfig.isEnabled(c) }),
                  BoolSpec(
                      "useTls",
                      "Use TLS",
                      get = { MqttConfig.useTls(it) },
                      set = MqttConfig::setUseTls,
                      visible = { c, _ -> MqttConfig.isEnabled(c) }),
                  BoolSpec(
                      "validateCert",
                      "Validate certificate",
                      get = { MqttConfig.validateCert(it) },
                      set = MqttConfig::setValidateCert,
                      visible = { c, _ -> MqttConfig.isEnabled(c) && MqttConfig.useTls(c) }),
              ),
          sections =
              mapOf(
                  "host" to "Broker",
                  "port" to "Broker",
                  "username" to "Broker",
                  "password" to "Broker",
                  "useTls" to "Security",
                  "validateCert" to "Security"),
          onApplied = { c, keys ->
            // TLS<->port convenience hop, lifted out of the bespoke on-device screen so the phone
            // remote gets it too (it didn't before — a real drift): flipping TLS moves the port to
            // the conventional default IF the user left it on the other mode's default.
            if ("useTls" in keys) {
              val tls = MqttConfig.useTls(c)
              val port = MqttConfig.port(c)
              if (tls && port == MqttConfig.DEFAULT_PORT) MqttConfig.setPort(c, MqttConfig.DEFAULT_TLS_PORT)
              else if (!tls && port == MqttConfig.DEFAULT_TLS_PORT) MqttConfig.setPort(c, MqttConfig.DEFAULT_PORT)
            }
            MqttService.sync(c)
          },
      )

  /**
   * The floating quick-button cluster ([QuickBarConfig]). Backed by an immutable
   * [QuickBarConfig.Settings] snapshot, so both the phone remote AND the on-device `QuickButtonsSection`
   * render it through the generic pipeline ([SettingsList]) — one definition, no bespoke duplicate.
   */
  val quickbar: SettingsDomain<QuickBarConfig.Settings> =
      SettingsDomain(
          id = "quickbar",
          title = "Quick buttons",
          load = QuickBarConfig::load,
          specs =
              listOf(
                  BoolSpec(
                      "enabled",
                      "App-switcher button",
                      get = { it.enabled },
                      set = QuickBarConfig::setEnabled,
                      help = "A centered button at the top that opens your recent apps to switch between them."),
                  BoolSpec(
                      "alwaysShow",
                      "Always show",
                      get = { it.alwaysShow },
                      set = QuickBarConfig::setAlwaysShow,
                      help = "On: always visible. Off: only while the system top bar is revealed.",
                      visible = { _, s -> s.enabled }),
              ),
          defaults = { QuickBarConfig.Settings() },
          onApplied = { c, _ ->
            SettingsGuard.reconcileBarWatch(c)
            QuickBar.applyConfig()
          },
      )

  /**
   * Device identity. One control: the Portal's display name, shown in the phone remote's device
   * switcher and used as the Home Assistant device name. HA `unique_id` and the MQTT topic path key
   * off a separate stable device id ([MqttConfig.deviceId]), so renaming is cosmetic and safe.
   * Bound to [FleetConfig], whose setter also rewrites the shell-readable fleet manifest.
   */
  val fleet: SettingsDomain<Context> =
      SettingsDomain(
          id = "fleet",
          title = "Device",
          load = { it },
          specs =
              listOf(
                  StringSpec(
                      "name",
                      "Device name",
                      get = { FleetConfig.name(it) },
                      // Trim surrounding whitespace before storing. Any printable characters are
                      // allowed (HA device name and the mDNS service name tolerate them).
                      set = { c, v -> FleetConfig.setName(c, v.trim()) },
                      // Reject blank or over-long names; the trimmed form must be 1..48 chars.
                      applyWhen = { it.trim().length in 1..48 },
                      help = "Shown in the phone remote and in Home Assistant.",
                  ),
              ),
      )

  /** Every registered domain. */
  val all: List<SettingsDomain<*>> =
      listOf(screensaver, calendar, immortal, mqtt, quickbar, fleet)
}
