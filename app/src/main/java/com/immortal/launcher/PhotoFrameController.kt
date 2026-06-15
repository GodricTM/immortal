/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.exifinterface.media.ExifInterface
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import org.json.JSONObject

/**
 * The photo-frame UI + update logic, decoupled from any host. Used by both
 * [PhotoDreamService] (the real screensaver) and [PhotoFramePreviewActivity]
 * (an in-app preview you can launch on demand). Reproduces the stock Portal
 * idle screen: full-screen rotating media with a clock / battery / date /
 * weather cluster bottom-left.
 *
 * Source is configurable via [ScreensaverConfig]:
 *  - **default** — the built-in keyless photo feed (Lorem Picsum, or Unsplash if a
 *    key is supplied). This is also the fallback whenever a local source is unset,
 *    empty, or unreachable (e.g. a USB drive was unplugged), so the frame is never
 *    blank.
 *  - **folder** — photos and videos from a folder the user picked (internal, SD, or
 *    a USB-C drive), read through the Storage Access Framework.
 *
 * Weather is Open-Meteo + IP geolocation (both keyless). All network is best-effort.
 */
class PhotoFrameController(
    private val context: Context,
    /** Show the welcome-back overlay when [start] is called (set true for presence-triggered starts). */
    val showWelcome: Boolean = false,
    private val unsplashKey: String = "",
    private val unsplashQuery: String = "nature,landscape,scenic",
    private val weatherRefreshMs: Long = 30 * 60_000L,
) {
  private val io = Executors.newSingleThreadExecutor()
  private val ui = Handler(Looper.getMainLooper())

  private lateinit var photo: ImageView
  private lateinit var videoView: VideoView
  private lateinit var clock: TextView
  private lateinit var battery: TextView
  private lateinit var date: TextView
  private lateinit var weather: TextView
  private lateinit var batteryDot: View
  private lateinit var weatherDot: View
  private var weatherText: String = ""
  // Now-playing display (visible only when media is active).
  private lateinit var nowPlayingRow: LinearLayout
  private lateinit var nowPlayingArt: ImageView
  private lateinit var nowPlayingText: TextView
  private var nowPlayingPollCounter = 0

  // Ambient dashboard: full-screen glanceable info card shown periodically.
  private var dashboardPanel: View? = null
  private lateinit var dashClock: TextView
  private lateinit var dashDate: TextView
  private lateinit var dashWeather: TextView
  private lateinit var dashEvent: TextView
  private var dashboardVisible = false

  private var settings = ScreensaverConfig.Settings()

  // Welcome-back overlay state.
  private lateinit var welcomeOverlay: View
  private var welcomeVisible = false
  private val dismissWelcomeRunnable = Runnable { dismissWelcomeAnimated() }
  private var tts: TextToSpeech? = null
  @Volatile private var ttsReady = false
  @Volatile private var pendingSpeech: String? = null

  // Ambient soundscape (synthesized) played while the frame is up.
  private val soundscape = SoundscapePlayer()

  // Local-folder playback state.
  private var localMode = false
  private var playlist: List<MediaItem> = emptyList()
  private var localIndex = -1
  // Bumped on every advance so a slow image-decode or an old video's completion
  // callback can tell it's been superseded and bow out.
  private var gen = 0

  // Web-feed history so swipes can go back as well as forward.
  private val history = ArrayList<Bitmap>()
  private var index = -1

  /** Host (dream / preview activity) sets this to dismiss the frame on tap. */
  var onExit: (() -> Unit)? = null

  // Deterministic gesture from raw down/up deltas (robust to synthetic input
  // that omits MOVE events): clear horizontal swipe = prev/next, clear tap = exit.
  private var downX = 0f
  private var downY = 0f

  /** Hosts forward their touch events here. */
  fun onTouch(ev: MotionEvent) {
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = ev.x
        downY = ev.y
      }
      MotionEvent.ACTION_UP -> {
        val dx = ev.x - downX
        val dy = ev.y - downY
        // A tap while the welcome overlay is showing dismisses it early
        // rather than exiting the screensaver.
        if (welcomeVisible && abs(dx) < 48 && abs(dy) < 48) {
          dismissWelcome()
          return
        }
        if (abs(dx) > 120 && abs(dx) > abs(dy) * 1.5f) {
          if (dx < 0) next() else prev()
        } else if (abs(dx) < 48 && abs(dy) < 48) {
          onExit?.invoke()
        }
      }
    }
  }

  val view: View by lazy { buildUi() }

  fun start() {
    settings = ScreensaverConfig.load(context)
    applyFit()

    // Only spin up TTS when the welcome overlay will actually speak. The greeting uses
    // the Android TTS engine (reliable, instant, no large download). Piper neural TTS
    // was dropped from this path: its model download is unreliable on the Portal's
    // connection and a truncated model makes onnxruntime abort natively (SIGABRT),
    // taking the whole dream down. See [[project_piper_crash]].
    val welcomeCfg = WelcomeConfig.load(context)
    val ttsEnabled = showWelcome && settings.welcomeEnabled && welcomeCfg.enableTts
    if (ttsEnabled) {
      tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS && !ttsReady) {
          tts?.language = Locale.US
          tts?.setPitch(1.0f)
          tts?.setSpeechRate(0.9f)
          // Apply the user's chosen voice; if none chosen, auto-pick the highest-quality
          // voice the device has so the greeting sounds as good as possible by default.
          runCatching {
            val voices = tts?.voices
            val chosen =
                if (welcomeCfg.ttsVoice.isNotBlank()) voices?.firstOrNull { it.name == welcomeCfg.ttsVoice }
                else voices
                    ?.filter { it.locale.language == Locale.US.language && !it.isNetworkConnectionRequired }
                    ?.maxByOrNull { it.quality }
            chosen?.let { tts?.voice = it }
          }
          ttsReady = true
          pendingSpeech?.let { text ->
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            pendingSpeech = null
          }
        }
      }
    }

    // Start the ambient soundscape (no-op when set to Off).
    runCatching { soundscape.start(settings.soundscape, settings.soundscapeVolume) }

    // Schedule the ambient dashboard cycle (first card after ~45s).
    if (settings.ambientDashboard) ui.postDelayed(dashboardCycle, 45_000L)

    if (showWelcome && settings.welcomeEnabled) showWelcomeOverlay()
    tick.run()
    refreshWeather.run()
    if (settings.usesFolder) {
      val path = settings.folderPath
      if (path.isNullOrBlank()) {
        startWeb()
        return
      }
      io.execute {
        val list =
            if (LocalMedia.isAccessible(path)) LocalMedia.enumerate(path, settings.includeVideo)
            else emptyList()
        ui.post {
          if (list.isNotEmpty()) {
            playlist = if (settings.shuffle) list.shuffled() else list
            localMode = true
            localIndex = -1
            advanceLocal(+1)
          } else {
            // Folder empty / unreachable → never leave the frame blank.
            startWeb()
          }
        }
      }
    } else {
      startWeb()
    }
  }

  fun stop() {
    ui.removeCallbacks(dashboardCycle)
    ui.removeCallbacksAndMessages(null)
    runCatching { soundscape.stop() }
    if (this::videoView.isInitialized) runCatching { videoView.stopPlayback() }
    tts?.stop()
    tts?.shutdown()
    tts = null
    ttsReady = false
    pendingSpeech = null
    io.shutdownNow()
  }

  // --- UI ---------------------------------------------------------------------
  private fun buildUi(): View {
    val root = FrameLayout(context)
    root.setBackgroundColor(Color.BLACK)

    photo = ImageView(context)
    photo.scaleType = ImageView.ScaleType.CENTER_CROP
    root.addView(photo, FrameLayout.LayoutParams(MATCH, MATCH))

    videoView = VideoView(context)
    videoView.visibility = View.GONE
    root.addView(videoView, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))

    val scrim = View(context)
    scrim.background =
        GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(0xCC000000.toInt(), 0x00000000),
        )
    root.addView(scrim, FrameLayout.LayoutParams(MATCH, dp(320), Gravity.BOTTOM))

    val col = LinearLayout(context)
    col.orientation = LinearLayout.VERTICAL
    val colLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START)
    colLp.setMargins(dp(40), 0, 0, dp(40))
    root.addView(col, colLp)

    clock = text(96f, Color.WHITE, true)
    col.addView(clock)

    val row = LinearLayout(context)
    row.gravity = Gravity.CENTER_VERTICAL
    // Date is always present; battery and weather are optional. Each optional
    // field owns the divider that precedes it, so the dot disappears with the
    // field (no orphaned "•" when a battery-less Portal reports no charge).
    date = text(22f, Color.WHITE, false)
    battery = text(22f, Color.WHITE, false)
    weather = text(22f, Color.WHITE, false)
    batteryDot = divider()
    weatherDot = divider()
    row.addView(date)
    row.addView(batteryDot)
    row.addView(battery)
    row.addView(weatherDot)
    row.addView(weather)
    col.addView(row)

    // Now-playing row: album art thumb + "title — artist", shown only when media plays.
    nowPlayingRow = LinearLayout(context)
    nowPlayingRow.orientation = LinearLayout.HORIZONTAL
    nowPlayingRow.gravity = Gravity.CENTER_VERTICAL
    nowPlayingRow.visibility = View.GONE
    nowPlayingArt = ImageView(context)
    val artLp = LinearLayout.LayoutParams(dp(48), dp(48))
    artLp.setMargins(0, dp(10), dp(12), 0)
    nowPlayingArt.layoutParams = artLp
    nowPlayingArt.scaleType = ImageView.ScaleType.CENTER_CROP
    nowPlayingText = text(20f, Color.WHITE, false)
    nowPlayingRow.addView(nowPlayingArt)
    nowPlayingRow.addView(nowPlayingText)
    col.addView(nowPlayingRow)

    // Ambient dashboard panel — full-screen glanceable card, hidden until cycled in.
    dashboardPanel = buildDashboardPanel().also {
      it.visibility = View.GONE
      root.addView(it, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // Welcome-back overlay — added last so it renders above photos and scrim.
    welcomeOverlay = buildWelcomeOverlay()
    welcomeOverlay.visibility = View.GONE
    root.addView(welcomeOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

    return root
  }

  // --- welcome-back overlay ----------------------------------------------------

  private fun buildWelcomeOverlay(): View {
    val welcomeCfg = WelcomeConfig.load(context)

    val overlay = FrameLayout(context)
    // Semi-opaque dark scrim so the photo is faintly visible behind the greeting.
    val bgAlpha = (welcomeCfg.backgroundOpacity * 255).toInt()
    overlay.setBackgroundColor((bgAlpha shl 24) or 0x000000)

    val col = LinearLayout(context)
    col.orientation = LinearLayout.VERTICAL
    col.gravity = Gravity.CENTER_HORIZONTAL
    // Add horizontal padding to prevent text cutoff at screen edges
    col.setPadding(dp(40), dp(20), dp(40), dp(20))

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
      hour < 5  -> welcomeCfg.greetingNight
      hour < 12 -> welcomeCfg.greetingMorning
      hour < 17 -> welcomeCfg.greetingAfternoon
      hour < 22 -> welcomeCfg.greetingEvening
      else      -> welcomeCfg.greetingNight
    }
    val fullGreeting = if (welcomeCfg.userName.isNotBlank()) {
      "$greeting, ${welcomeCfg.userName}"
    } else {
      greeting
    }

    if (welcomeCfg.showGreeting) {
      val greetingView = text(welcomeCfg.greetingSize, welcomeCfg.greetingColor, false)
      greetingView.text = fullGreeting
      greetingView.gravity = Gravity.CENTER
      greetingView.letterSpacing = welcomeCfg.greetingLetterSpacing
      greetingView.maxLines = 2
      val greetingLp = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT)
      col.addView(greetingView, greetingLp)
    }

    if (welcomeCfg.showClock) {
      val clockPattern = if (ImmortalSettings.use24HourClock(context)) "H:mm" else "h:mm"
      val clockView = text(welcomeCfg.clockSize, welcomeCfg.clockColor, true)
      clockView.text = SimpleDateFormat(clockPattern, Locale.getDefault()).format(Date())
      clockView.gravity = Gravity.CENTER
      clockView.maxLines = 1
      val clockLp = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT)
      col.addView(clockView, clockLp)
    }

    if (welcomeCfg.showDate) {
      val dateView = text(welcomeCfg.dateSize, welcomeCfg.dateColor, false)
      dateView.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
      dateView.gravity = Gravity.CENTER
      dateView.maxLines = 1
      val dateLp = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT)
      dateLp.topMargin = dp(4)
      col.addView(dateView, dateLp)
    }

    // Use MATCH_PARENT width with the padding above to ensure text stays visible
    overlay.addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
    return overlay
  }

  private fun showWelcomeOverlay() {
    val welcomeCfg = WelcomeConfig.load(context)
    welcomeVisible = true
    welcomeOverlay.alpha = 0f
    welcomeOverlay.visibility = View.VISIBLE
    welcomeOverlay.animate().alpha(1f).setDuration(500).start()

    // Speak the greeting if TTS is enabled
    if (welcomeCfg.enableTts) {
      val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
      val greeting = when {
        hour < 5  -> welcomeCfg.greetingNight
        hour < 12 -> welcomeCfg.greetingMorning
        hour < 17 -> welcomeCfg.greetingAfternoon
        hour < 22 -> welcomeCfg.greetingEvening
        else      -> welcomeCfg.greetingNight
      }
      val fullGreeting = if (welcomeCfg.userName.isNotBlank()) {
        "$greeting, ${welcomeCfg.userName}"
      } else {
        greeting
      }
      // Speak immediately if TTS is ready, otherwise queue it
      if (ttsReady) {
        tts?.speak(fullGreeting, TextToSpeech.QUEUE_FLUSH, null, null)
      } else {
        pendingSpeech = fullGreeting
      }
    }

    // Auto-dismiss after configured duration.
    ui.postDelayed(dismissWelcomeRunnable, welcomeCfg.durationMs.toLong())
  }

  /** Dismiss immediately (e.g. on tap). */
  private fun dismissWelcome() {
    if (!welcomeVisible) return
    ui.removeCallbacks(dismissWelcomeRunnable)
    tts?.stop()
    pendingSpeech = null
    dismissWelcomeAnimated()
  }

  private fun dismissWelcomeAnimated() {
    welcomeVisible = false
    // Stop ongoing speech but leave pendingSpeech intact so it fires if TTS wasn't
    // ready before the overlay auto-dismissed. User tap (dismissWelcome) cancels it.
    tts?.stop()
    welcomeOverlay.animate()
        .alpha(0f)
        .setDuration(600)
        .withEndAction { welcomeOverlay.visibility = View.GONE }
        .start()
  }

  private fun applyFit() {
    // Video letterboxes either way (VideoView limitation); images honour the choice.
    photo.scaleType =
        if (settings.fit == ScreensaverConfig.FIT_FIT) ImageView.ScaleType.FIT_CENTER
        else ImageView.ScaleType.CENTER_CROP
  }

  private fun text(sizeSp: Float, color: Int, light: Boolean): TextView {
    val t = TextView(context)
    t.textSize = sizeSp
    t.setTextColor(color)
    if (light) t.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    t.setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
    return t
  }

  private fun divider(): View {
    val v = TextView(context)
    v.text = "   •   "
    v.textSize = 22f
    v.setTextColor(0x88FFFFFF.toInt())
    return v
  }

  private fun intervalMs(): Long = settings.intervalSec * 1000L

  // --- periodic loops ---------------------------------------------------------
  private val tick =
      object : Runnable {
        override fun run() {
          val now = Date()
          val clockPattern = if (ImmortalSettings.use24HourClock(context)) "H:mm" else "h:mm"
          clock.text = SimpleDateFormat(clockPattern, Locale.getDefault()).format(now)
          date.text = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
          val pct = batteryPct()
          val hasBattery = pct >= 0
          battery.text = if (hasBattery) "$pct%" else ""
          battery.visibility = if (hasBattery) View.VISIBLE else View.GONE
          batteryDot.visibility = if (hasBattery) View.VISIBLE else View.GONE
          val hasWeather = weatherText.isNotBlank()
          weather.text = weatherText
          weather.visibility = if (hasWeather) View.VISIBLE else View.GONE
          weatherDot.visibility = if (hasWeather) View.VISIBLE else View.GONE
          // Poll the active media session every ~3s for the now-playing display.
          if (nowPlayingPollCounter % 3 == 0) updateNowPlaying()
          nowPlayingPollCounter++
          ui.postDelayed(this, 1_000L)
        }
      }

  /** Refresh the now-playing strip from the active media session. */
  private fun updateNowPlaying() {
    if (!this::nowPlayingRow.isInitialized) return
    val track = runCatching { NowPlaying.current(context) }.getOrNull()
    if (track != null && track.title.isNotBlank()) {
      nowPlayingText.text =
          if (track.artist.isNotBlank()) "♪ ${track.title} — ${track.artist}" else "♪ ${track.title}"
      if (track.art != null) {
        nowPlayingArt.setImageBitmap(track.art)
        nowPlayingArt.visibility = View.VISIBLE
      } else {
        nowPlayingArt.visibility = View.GONE
      }
      nowPlayingRow.visibility = View.VISIBLE
    } else {
      nowPlayingRow.visibility = View.GONE
    }
  }

  // --- ambient dashboard ------------------------------------------------------

  private fun buildDashboardPanel(): View {
    val panel = FrameLayout(context)
    panel.setBackgroundColor(0xF20D0D12.toInt()) // near-opaque dark
    val col = LinearLayout(context)
    col.orientation = LinearLayout.VERTICAL
    col.gravity = Gravity.CENTER
    panel.addView(col, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    dashClock = text(120f, Color.WHITE, true).also { it.gravity = Gravity.CENTER; col.addView(it) }
    dashDate = text(28f, Color.WHITE, false).also { it.gravity = Gravity.CENTER; col.addView(it) }
    dashWeather = text(26f, Color.WHITE, false).also {
      it.gravity = Gravity.CENTER
      (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(18)
      col.addView(it)
    }
    dashEvent = text(22f, Color.WHITE, false).also {
      it.gravity = Gravity.CENTER
      (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10)
      col.addView(it)
    }
    return panel
  }

  /** Shows the dashboard card for a few seconds, then returns to the photos. */
  private val dashboardCycle =
      object : Runnable {
        override fun run() {
          if (!settings.ambientDashboard) return
          showDashboard()
          ui.postDelayed({ hideDashboard() }, 9_000L)
          ui.postDelayed(this, 90_000L) // reappear roughly every 90s
        }
      }

  private fun showDashboard() {
    val panel = dashboardPanel ?: return
    val now = Date()
    val clockPattern = if (ImmortalSettings.use24HourClock(context)) "H:mm" else "h:mm"
    dashClock.text = SimpleDateFormat(clockPattern, Locale.getDefault()).format(now)
    dashDate.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    dashWeather.text = weatherText
    dashWeather.visibility = if (weatherText.isNotBlank()) View.VISIBLE else View.GONE
    dashEvent.visibility = View.GONE
    io.execute {
      val ev = runCatching {
        if (CalendarHelper.hasPermission(context)) CalendarHelper.upcoming(context).firstOrNull() else null
      }.getOrNull()
      ui.post {
        if (ev != null) {
          val cal = java.util.Calendar.getInstance().apply { timeInMillis = ev.begin }
          val whenStr =
              if (ev.allDay) SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
              else SimpleDateFormat(if (ImmortalSettings.use24HourClock(context)) "HH:mm" else "h:mm a",
                  Locale.getDefault()).format(cal.time)
          dashEvent.text = "Next: $whenStr · ${ev.title}"
          if (dashboardVisible) dashEvent.visibility = View.VISIBLE
        }
      }
    }
    panel.alpha = 0f
    panel.visibility = View.VISIBLE
    panel.animate().alpha(1f).setDuration(600).start()
    dashboardVisible = true
  }

  private fun hideDashboard() {
    val panel = dashboardPanel ?: return
    panel.animate().alpha(0f).setDuration(600).withEndAction { panel.visibility = View.GONE }.start()
    dashboardVisible = false
  }

  private val rotate =
      object : Runnable {
        override fun run() {
          webNext()
          ui.postDelayed(this, intervalMs())
        }
      }

  private val refreshWeather =
      object : Runnable {
        override fun run() {
          fetchWeather()
          ui.postDelayed(this, weatherRefreshMs)
        }
      }

  // --- navigation (branches on the active source) -----------------------------
  fun next() {
    if (localMode) advanceLocal(+1) else webNext()
  }

  fun prev() {
    if (localMode) advanceLocal(-1) else webPrev()
  }

  // --- local folder playback --------------------------------------------------
  private val localTick =
      object : Runnable {
        override fun run() {
          advanceLocal(+1)
        }
      }

  private fun advanceLocal(dir: Int) {
    ui.removeCallbacks(localTick)
    if (playlist.isEmpty()) {
      startWeb()
      return
    }
    gen++
    localIndex = ((localIndex + dir) % playlist.size + playlist.size) % playlist.size
    val item = playlist[localIndex]
    if (item.isVideo) showVideo(item.path, gen) else showLocalImage(item.path, gen)
  }

  private fun showLocalImage(path: String, g: Int) {
    stopVideo()
    io.execute {
      val bmp = runCatching { decodeCorrected(path) }.getOrNull()
      ui.post {
        if (g != gen) return@post // superseded by a newer advance
        if (bmp == null) {
          advanceLocal(+1) // skip an unreadable file
          return@post
        }
        photo.visibility = View.VISIBLE
        show(bmp)
        ui.postDelayed(localTick, intervalMs())
      }
    }
  }

  /**
   * Decode a local image and apply its EXIF orientation. Phone cameras save the
   * raw sensor buffer and record the intended rotation in an EXIF tag rather than
   * baking it into the pixels, so [BitmapFactory.decodeFile] alone shows portrait
   * shots sideways and some landscapes upside-down. The web feed is unaffected (its
   * images carry no rotation flag), so this only matters for the folder source.
   *
   * Reading the tag is best-effort: a missing or unreadable EXIF block falls back to
   * the upright orientation so a quirky file still shows (just unrotated) instead of
   * being skipped.
   */
  private fun decodeCorrected(path: String): Bitmap? {
    // Downsample large camera files (12–48 MP) so the decode + the rotated copy below
    // both fit in the Portal's heap. Target the screen's ~2× so quality is unaffected.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > 2560) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
    val orientation =
        runCatching {
              ExifInterface(path)
                  .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.preScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(-90f)
        matrix.preScale(-1f, 1f)
      }
      else -> return bmp // NORMAL / UNDEFINED — already upright, no copy needed
    }
    // createBitmap allocates a second full-size bitmap; free the source once the
    // rotated copy exists so peak memory stays at one image (Portal heaps are small
    // and phone photos are large).
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    if (rotated != bmp) bmp.recycle()
    return rotated
  }

  private fun showVideo(path: String, g: Int) {
    photo.setImageDrawable(null)
    photo.visibility = View.GONE
    videoView.visibility = View.VISIBLE
    runCatching {
          videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            runCatching { mp.setVolume(0f, 0f) } // a screensaver shouldn't blare audio
            if (g == gen) videoView.start()
          }
          videoView.setOnCompletionListener {
            if (g == gen) advanceLocal(+1)
          }
          videoView.setOnErrorListener { _, _, _ ->
            if (g == gen) advanceLocal(+1)
            true
          }
          videoView.setVideoPath(path)
          // Safety net: advance even if a clip is very long or never reports done.
          ui.postDelayed(localTick, maxOf(intervalMs(), 5 * 60_000L))
        }
        .onFailure { if (g == gen) advanceLocal(+1) }
  }

  private fun stopVideo() {
    if (this::videoView.isInitialized && videoView.visibility != View.GONE) {
      runCatching { videoView.stopPlayback() }
      videoView.visibility = View.GONE
    }
  }

  // --- web feed (default + fallback) ------------------------------------------
  private fun startWeb() {
    localMode = false
    stopVideo()
    rotate.run()
  }

  /** Forward through history, loading a fresh photo when at the end. */
  private fun webNext() {
    if (index in 0 until history.size - 1) {
      index++
      show(history[index])
    } else {
      loadFresh()
    }
  }

  /** Back through history (no-op at the start). */
  private fun webPrev() {
    if (index > 0) {
      index--
      show(history[index])
    }
  }

  private fun loadFresh() {
    io.execute {
      val bmp = runCatching { downloadBitmap(directImageUrl()) }.getOrNull() ?: return@execute
      ui.post {
        photo.visibility = View.VISIBLE
        if (history.size >= 6) history.removeAt(0) // cap memory; GC reclaims
        history.add(bmp)
        index = history.size - 1
        show(bmp)
      }
    }
  }

  private fun show(bmp: Bitmap) {
    photo
        .animate()
        .alpha(0.15f)
        .setDuration(220)
        .withEndAction {
          photo.setImageBitmap(bmp)
          photo.animate().alpha(1f).setDuration(420).start()
        }
        .start()
  }

  /** Resolve the next image URL based on the configured online feed. Each branch
   *  falls back to Picsum if its API hiccups, so the frame is never blank. Runs on
   *  the io thread (httpGet blocks). */
  private fun directImageUrl(): String =
      runCatching {
        when (settings.feed) {
          ScreensaverConfig.FEED_MET -> metImageUrl()
          ScreensaverConfig.FEED_ARTIC -> articImageUrl()
          ScreensaverConfig.FEED_WIKIMEDIA -> wikimediaImageUrl()
          ScreensaverConfig.FEED_APOD -> apodImageUrl()
          else -> defaultPhotoUrl()
        }
      }.getOrDefault(picsumUrl())

  // Request photos at the device's actual screen size/orientation so they fill the
  // panel crisply instead of being upscaled from a fixed 1280×800 (Portal Mini is
  // 800×1280 portrait, Portal+ gen-2 is 2160×1440, etc.).
  private fun picsumUrl(): String {
    val m = context.resources.displayMetrics
    return "https://picsum.photos/${m.widthPixels}/${m.heightPixels}?random=${System.currentTimeMillis()}"
  }

  /** Picsum, or Unsplash if a key was supplied (the original built-in feed). */
  private fun defaultPhotoUrl(): String {
    if (unsplashKey.isBlank()) return picsumUrl()
    val m = context.resources.displayMetrics
    // Match the Unsplash crop to the screen aspect so portrait panels don't get a
    // letterboxed/upscaled landscape shot.
    val orientation = if (m.heightPixels > m.widthPixels) "portrait" else "landscape"
    val json =
        httpGet(
            "https://api.unsplash.com/photos/random?orientation=$orientation" +
                "&query=$unsplashQuery&client_id=$unsplashKey")
    return JSONObject(json).getJSONObject("urls").getString("regular")
  }

  // The Met search returns a big list of object IDs once; we then pick random ones and
  // fetch each object's primaryImage. The ID list is cached for the session.
  private var metIds: List<Int>? = null
  private fun metImageUrl(): String {
    val ids =
        metIds
            ?: run {
              val q =
                  listOf(
                          "landscape", "impressionism", "painting", "portrait", "seascape",
                          "river", "mountains", "flowers", "still life", "garden")
                      .random()
              val arr =
                  JSONObject(
                          httpGet(
                              "https://collectionapi.metmuseum.org/public/collection/v1/" +
                                  "search?hasImages=true&q=$q"))
                      .optJSONArray("objectIDs")
              val list =
                  if (arr != null) (0 until arr.length()).map { arr.getInt(it) } else emptyList()
              metIds = list
              list
            }
    if (ids.isEmpty()) return picsumUrl()
    repeat(5) {
      val id = ids.random()
      val img =
          runCatching {
                JSONObject(
                        httpGet(
                            "https://collectionapi.metmuseum.org/public/collection/v1/objects/$id"))
                    .optString("primaryImage")
              }
              .getOrDefault("")
      if (img.isNotBlank()) return img
    }
    return picsumUrl()
  }

  private fun articImageUrl(): String {
    val page = (1..100).random()
    val data =
        JSONObject(
                httpGet(
                    "https://api.artic.edu/api/v1/artworks?fields=id,image_id&limit=100&page=$page"))
            .optJSONArray("data") ?: return picsumUrl()
    val imageIds =
        (0 until data.length()).mapNotNull {
          data.getJSONObject(it).optString("image_id").takeIf { s -> s.isNotBlank() && s != "null" }
        }
    if (imageIds.isEmpty()) return picsumUrl()
    // IIIF image API: 1200px wide, auto height.
    return "https://www.artic.edu/iiif/2/${imageIds.random()}/full/1200,/0/default.jpg"
  }

  private fun wikimediaImageUrl(): String {
    // Picture of the Day is one per day, so sample a random recent date for variety.
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, -(0..120).random())
    val date =
        String.format(
            "%04d/%02d/%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH))
    val image =
        JSONObject(httpGet("https://api.wikimedia.org/feed/v1/wikipedia/en/featured/$date"))
            .optJSONObject("image") ?: return picsumUrl()
    val thumb = image.optJSONObject("thumbnail")?.optString("source").orEmpty()
    val full = image.optJSONObject("image")?.optString("source").orEmpty()
    return thumb.ifBlank { full }.ifBlank { picsumUrl() }
  }

  private fun apodImageUrl(): String {
    for (attempt in 0 until 3) {
      val obj =
          runCatching {
                org.json.JSONArray(
                        httpGet(
                            "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&count=1&thumbs=true"))
                    .optJSONObject(0)
              }
              .getOrNull() ?: continue
      if (obj.optString("media_type") == "image") {
        // Prefer the display-sized "url" over "hdurl": the HD master is often a
        // 5000–10000px, many-MB image that's slow to fetch and overkill for the panel.
        val u = obj.optString("url").ifBlank { obj.optString("hdurl") }
        if (u.isNotBlank()) return u
      } else {
        val t = obj.optString("thumbnail_url")
        if (t.isNotBlank()) return t
      }
    }
    return picsumUrl()
  }

  // --- data -------------------------------------------------------------------
  private fun batteryPct(): Int {
    val i =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
    // Only Portal Go has a battery; the mains-powered Portals (Portal+, Mini,
    // gen-2, TV) report no battery present but still publish a bogus level=0, so
    // gate on EXTRA_PRESENT to avoid showing a permanent "0%". -1 hides the field.
    if (!i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) return -1
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else -1
  }

  private fun fetchWeather() {
    io.execute {
      // Shared resilient fetch: cached location + multi-provider geolocation.
      val w = Weather.fetch(context)
      if (w.isNotBlank()) weatherText = w
    }
  }

  private fun httpGet(spec: String): String {
    val c = URL(spec).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    c.setRequestProperty("User-Agent", "PortalPhotoFrame/1.0")
    return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
  }

  private fun downloadBitmap(spec: String): Bitmap? {
    val c = URL(spec).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 12000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "PortalPhotoFrame/1.0")
    // Buffer the whole image so we can measure it, then decode downsampled. Art-feed
    // sources (the Met especially) return 4000–6000px masters; decoding those at full
    // size is ~50–100 MB of ARGB and OOMs the Portal's small heap — the frame would
    // go blank or take the dream down with it. Capping at the screen's ~2× kills that.
    val bytes = c.inputStream.use { it.readBytes() }
    return decodeSampled(bytes, 2560)
  }

  /** Decode [bytes] downsampled so the longer edge is ≤ [maxEdge] px. Guards OOM. */
  private fun decodeSampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
  }

  private val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
  private val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
  private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
