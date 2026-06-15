/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import java.io.File
import kotlinx.coroutines.launch

private data class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    val folder: String? = null,
)

/**
 * The custom Portal home launcher. Replaces the stock Aloha home (selected via
 * `cmd package set-home-activity`). Shows a clock/date/weather header, an App
 * Store tile, and a grid of every installed launchable app. Built for Portal's
 * form factor: dark theme, top 64dp reserved for the system overlay, large
 * touch targets, landscape.
 */
class HomeActivity : ComponentActivity() {
  // Overnight re-sleep. Inside the overnight window a wake should normally go back
  // to sleep, but a deliberate tap must let the user actually use the device. We
  // can't tell the two apart synchronously in onResume — the waking tap is consumed
  // by the framework and isn't delivered to us before resume — so we never lock
  // immediately. Instead onResume arms a short grace timer; a stray wake gets no
  // interaction and sleeps when it fires, while a real touch (dispatchTouchEvent)
  // extends it to a normal screen-timeout, resetting on every interaction.
  private val resleepHandler = Handler(Looper.getMainLooper())
  private val resleep = Runnable {
    // Re-check at fire time: the window may have ended while the screen was on.
    if (SleepScheduler.isOvernightNow(this)) ScreenControl.sleep(this)
  }
  private var overnightWindow = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enterImmersive()
    if (intent?.action == "com.immortal.launcher.CLOSE_CURRENT_APP") moveTaskToBack(true)
    // First launch: show the friendly tour once so new users aren't dropped in cold.
    if (!HelpActivity.hasSeen(this)) {
      window.decorView.post {
        runCatching { startActivity(Intent(this, HelpActivity::class.java)) }
      }
    }
    setContent {
      SampleAppTheme(darkTheme = true) {
        LauncherScreen(
            onLaunch = { cn ->
              UserLayout.recordLaunch(this, cn.packageName)
              runCatching {
                val customAction = Curation.customLaunchAction[cn.packageName]
                val intent = if (customAction != null)
                  Intent(customAction).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                else
                  Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                      .setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
              }
            },
            onOpenStore = {
              runCatching { startActivity(Intent(this, StoreActivity::class.java)) }
            },
            onOpenHelp = {
              runCatching { startActivity(Intent(this, HelpActivity::class.java)) }
            },
            onStartScreensaver = {
              runCatching {
                startActivity(Intent(this, PhotoFramePreviewActivity::class.java))
              }
            },
            onOpenCamera = {
              runCatching { startActivity(Intent(this, CameraPreviewActivity::class.java)) }
            },
            onExitHome = { launchStockHome() },
            onUninstall = { pkg ->
              // System uninstall dialog; no special permission needed.
              runCatching {
                startActivity(
                    Intent(Intent.ACTION_DELETE)
                        .setData(android.net.Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              }
            },
        )
      }
    }
  }

  // Re-assert immersive fullscreen whenever the launcher regains focus — e.g.
  // after the screensaver or another app closes — since the system restores the
  // bars when another window takes over.
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) enterImmersive()
  }

  // Self-heal: if anything (e.g. the stock launcher) reset our screensaver
  // settings, put them back every time Immortal comes to the foreground.
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.action == "com.immortal.launcher.CLOSE_CURRENT_APP") moveTaskToBack(true)
  }

  override fun onResume() {
    super.onResume()
    if (intent?.action == "com.immortal.launcher.CLOSE_CURRENT_APP") moveTaskToBack(true)
    // The user is back on Immortal, so any stock-launcher call handoff is over:
    // allow the photo frame to resume its normal screensaver behaviour.
    DreamPolicy.inStockHandoff = false
    SettingsGuard.reaffirmScreensaver(this)
    // Back on the launcher: the idle screen-off session is over.
    SleepScheduler.cancelIdle(this)
    // Inside the overnight window, don't lock instantly — that traps a deliberate
    // tap in a wake/re-lock loop. Arm a short grace instead; a real touch extends it
    // (see dispatchTouchEvent), a stray wake just sleeps again when it fires. The
    // ACTION_OVERNIGHT_START alarm still does the authoritative lock at window start.
    overnightWindow = SleepScheduler.isOvernightNow(this)
    if (overnightWindow) armResleep(OVERNIGHT_STRAY_WAKE_MS)
  }

  override fun onPause() {
    super.onPause()
    // Don't carry a pending re-sleep into another activity or a real sleep.
    overnightWindow = false
    resleepHandler.removeCallbacks(resleep)
  }

  // dispatchTouchEvent is the top of the input chain, so it sees every touch before
  // Compose consumes it. Outside the overnight window this is a no-op.
  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    if (overnightWindow) armResleep(OVERNIGHT_ACTIVE_TIMEOUT_MS)
    return super.dispatchTouchEvent(ev)
  }

  private fun armResleep(delayMs: Long) {
    resleepHandler.removeCallbacks(resleep)
    resleepHandler.postDelayed(resleep, delayMs)
  }

  private fun enterImmersive() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  /**
   * The "Calls" tile bridges to the STOCK Portal home — the only caller Meta trusts
   * to launch Contacts/calling/camera.
   *
   * Why a deep link and not a plain HOME launch: the Contacts app
   * (com.facebook.alohaapps.contacts) enforces a signature-based trusted-caller
   * check (Meta's com.facebook.secure framework) that Immortal can never satisfy,
   * so we must route through the trusted stock launcher. A plain MAIN/HOME launch
   * cold-starts the stock launcher into its idle "dream" face, whose
   * DREAMING_STOPPED then makes our own screensaver relaunch over the top and trap
   * the user (the reported "Calls kicks me back into Immortal"). The stock
   * launcher's `portal://launcher/home` VIEW deep link instead resumes its
   * interactive Home tab directly — the Contacts/Favorites calling surface — and we
   * mark a bridge in flight so [DreamPolicy] doesn't claw the frame back during the
   * transition.
   */
  private fun launchStockHome() {
    // Suppress the screensaver-relaunch race while the stock home comes forward, and
    // keep suppressing the holding-frame relaunch until the user returns to Immortal
    // (cleared in onResume) so the frame can't slam over an in-progress call.
    DreamPolicy.bridgeAt = System.currentTimeMillis()
    DreamPolicy.inStockHandoff = true

    fun fire(intent: Intent): Boolean =
        runCatching {
              startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              true
            }
            .getOrDefault(false)

    // 1) Deep-link straight to the touchscreen stock launcher's Home tab.
    val deepLink =
        Intent(Intent.ACTION_VIEW, Uri.parse("portal://launcher/home"))
            .setPackage("com.facebook.alohaapps.launcher")
    if (fire(deepLink)) return

    // 2) Fallback for models without the portal:// deep link (e.g. the Portal TV's
    //    ripleyhome): this device's real stock HOME, excluding ourselves and the
    //    system fallback homes.
    val stock =
        packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo }
            .firstOrNull {
              it.packageName != packageName &&
                  it.packageName != "com.android.settings" &&
                  it.packageName != "com.android.tv.settings" &&
                  !it.name.contains("FallbackHome", ignoreCase = true)
            }
    if (stock != null) {
      fire(
          Intent(Intent.ACTION_MAIN)
              .addCategory(Intent.CATEGORY_LAUNCHER)
              .setComponent(ComponentName(stock.packageName, stock.name)))
    }
  }
}

@Composable
private fun LauncherScreen(
    onLaunch: (ComponentName) -> Unit,
    onOpenStore: () -> Unit,
    onOpenHelp: () -> Unit,
    onStartScreensaver: () -> Unit,
    onOpenCamera: () -> Unit,
    onExitHome: () -> Unit,
    onUninstall: (String) -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  // Bumped whenever an app is installed/removed so the grid refreshes live.
  var reload by remember { mutableStateOf(0) }
  DisposableEffect(Unit) {
    val receiver =
        object : android.content.BroadcastReceiver() {
          override fun onReceive(c: android.content.Context, i: Intent) {
            reload++
          }
        }
    val filter =
        android.content.IntentFilter().apply {
          addAction(Intent.ACTION_PACKAGE_ADDED)
          addAction(Intent.ACTION_PACKAGE_REMOVED)
          addAction(Intent.ACTION_PACKAGE_REPLACED)
          addDataScheme("package")
        }
    if (android.os.Build.VERSION.SDK_INT >= 33)
        context.registerReceiver(
            receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }
  val apps by
      produceState(initialValue = emptyList<AppEntry>(), reload) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
      }
  var editMode by remember { mutableStateOf(false) }
  var openFolder by remember { mutableStateOf<String?>(null) }

  // Immortal Settings the home screen reflects (tile size, and the optional weather
  // widget's mode/unit), re-read on resume so a change applies the moment the user
  // comes back to the home screen.
  var tileSize by remember { mutableStateOf(ImmortalSettings.load(context).tileSize) }
  var weatherWidget by remember { mutableStateOf(ImmortalSettings.load(context).weatherWidget) }
  var weatherFahrenheit by remember { mutableStateOf(ImmortalSettings.useFahrenheit(context)) }
  var backgroundType by remember { mutableStateOf(ImmortalSettings.load(context).backgroundType) }
  var backgroundImagePath by remember { mutableStateOf(ImmortalSettings.load(context).backgroundImagePath) }
  var backgroundGradient by remember { mutableStateOf(ImmortalSettings.load(context).backgroundGradient) }
  var showDayProgress by remember { mutableStateOf(ImmortalSettings.load(context).showDayProgress) }
  var calendarWidget by remember { mutableStateOf(ImmortalSettings.load(context).calendarWidget) }
  var statsMode by remember { mutableStateOf(ImmortalSettings.load(context).statsMode) }
  var dailyTileMode by remember { mutableStateOf(ImmortalSettings.load(context).dailyTileMode) }
  var sortMode by remember { mutableStateOf(ImmortalSettings.load(context).sortMode) }
  var showTabs by remember { mutableStateOf(ImmortalSettings.load(context).showTabs) }
  var dashboardPage by remember { mutableStateOf(ImmortalSettings.load(context).dashboardPage) }
  // Selected category tab (null = "All"); only meaningful when showTabs is on.
  var selectedTab by remember { mutableStateOf<String?>(null) }
  var heyPkg by remember { mutableStateOf(heyPackage(context)) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) {
        val s = ImmortalSettings.load(context)
        tileSize = s.tileSize
        weatherWidget = s.weatherWidget
        weatherFahrenheit = ImmortalSettings.useFahrenheit(context)
        backgroundType = s.backgroundType
        backgroundImagePath = s.backgroundImagePath
        backgroundGradient = s.backgroundGradient
        showDayProgress = s.showDayProgress
        calendarWidget = s.calendarWidget
        statsMode = s.statsMode
        dailyTileMode = s.dailyTileMode
        sortMode = s.sortMode
        showTabs = s.showTabs
        dashboardPage = s.dashboardPage
        heyPkg = heyPackage(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  // Remote support: land focus on the grid at startup so the D-pad works on the TV.
  val homeGridFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { homeGridFocus.requestFocus() } }

  // User-created folder assignments (via drag-drop), persisted, overlaying the
  // curated defaults: a user override wins over Curation.folderFor(). An empty
  // string is an explicit "ungrouped" override (used when dragging out of a
  // folder), so it beats a curated default too.
  val assignments = remember { mutableStateMapOf<String, String>() }
  val emptyFolders = remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(Unit) {
    assignments.putAll(UserLayout.load(context))
    emptyFolders.value = UserLayout.loadEmptyFolders(context)
  }
  val appsEff =
      remember(apps, assignments.toMap()) {
        apps.map { a ->
          val pkg = a.component.packageName
          val eff = if (assignments.containsKey(pkg)) assignments[pkg]!!.ifEmpty { null } else a.folder
          a.copy(folder = eff)
        }
      }
  var hiddenPkgs by remember { mutableStateOf(UserLayout.loadHiddenPackages(context)) }
  // Re-read hidden packages on resume so "Restore all" in settings takes effect immediately.
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) hiddenPkgs = UserLayout.loadHiddenPackages(context)
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }
  fun hideApp(pkg: String) {
    UserLayout.hidePackage(context, pkg)
    hiddenPkgs = hiddenPkgs + pkg
  }
  fun unhideApp(pkg: String) {
    UserLayout.unhidePackage(context, pkg)
    hiddenPkgs = hiddenPkgs - pkg
  }

  // Sort inputs for the non-manual modes (loaded only when the relevant mode is on).
  val launchCounts = remember(sortMode) {
    if (sortMode == ImmortalSettings.SORT_USED) UserLayout.loadLaunchCounts(context) else emptyMap()
  }
  val installTimes = remember(sortMode, appsEff) {
    if (sortMode == ImmortalSettings.SORT_RECENT) {
      val pm = context.packageManager
      appsEff.associate { app ->
        app.component.packageName to
            runCatching { pm.getPackageInfo(app.component.packageName, 0).firstInstallTime }
                .getOrDefault(0L)
      }
    } else emptyMap()
  }
  val ungrouped = remember(appsEff, hiddenPkgs, sortMode, launchCounts, installTimes) {
    val base = appsEff.filter { it.folder == null && it.component.packageName !in hiddenPkgs }
    when (sortMode) {
      ImmortalSettings.SORT_AZ -> base.sortedBy { it.label.lowercase(java.util.Locale.getDefault()) }
      ImmortalSettings.SORT_USED ->
          base.sortedByDescending { launchCounts[it.component.packageName] ?: 0 }
      ImmortalSettings.SORT_RECENT ->
          base.sortedByDescending { installTimes[it.component.packageName] ?: 0L }
      else -> base
    }
  }
  val hiddenAppsEff = remember(appsEff, hiddenPkgs) { appsEff.filter { it.component.packageName in hiddenPkgs } }
  val folderNames = remember(appsEff, emptyFolders.value) {
    (appsEff.mapNotNull { it.folder } + emptyFolders.value).distinct().sorted()
  }

  var showHiddenPanel by remember { mutableStateOf(false) }
  var showSpeedTest by remember { mutableStateOf(false) }
  var showDaily by remember { mutableStateOf(false) }
  var showNote by remember { mutableStateOf(false) }
  var showTransit by remember { mutableStateOf(false) }
  var showNowPlaying by remember { mutableStateOf(false) }
  // Bumped whenever a note changes so the home sticky card re-reads it.
  var noteVersion by remember { mutableStateOf(0) }
  // Bumped on every resume so home widgets that read SharedPrefs/files directly
  // (countdown chips, sticky note) refresh after returning from a settings screen.
  var homeResumeVersion by remember { mutableStateOf(0) }
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) { homeResumeVersion++; noteVersion++ }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  // Portrait layout: the bottom widget stack (weather / calendar / stats) spans
  // nearly the full width, so the corner action buttons (Alexa, hide, edit) would
  // sit on top of it. Measure the stack height and lift the buttons above it. In
  // landscape the capped 1264dp content column leaves the corners clear, so no
  // offset is applied.
  val isPortrait =
      androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
          android.content.res.Configuration.ORIENTATION_PORTRAIT
  val widgetDensity = androidx.compose.ui.platform.LocalDensity.current
  var bottomWidgetsHeight by remember { mutableStateOf(0.dp) }
  val bottomButtonLift = if (isPortrait) bottomWidgetsHeight else 0.dp

  // --- drag-and-drop folder management (Manage mode) --------------------------
  val tileBounds = remember { mutableStateMapOf<String, Rect>() }
  var containerOrigin by remember { mutableStateOf(Offset.Zero) }
  var dragPkg by remember { mutableStateOf<String?>(null) }
  var dragPos by remember { mutableStateOf(Offset.Zero) }
  // Pending folder creation awaiting a name (source+target packages).
  var pendingPair by remember { mutableStateOf<Pair<String, String>?>(null) }
  // Folder currently being renamed.
  var renaming by remember { mutableStateOf<String?>(null) }
  // Creating a new empty folder
  var creatingFolder by remember { mutableStateOf(false) }

  fun persist() = UserLayout.save(context, assignments.toMap())
  fun assign(pkg: String, folder: String) {
    assignments[pkg] = folder
    persist()
  }
  fun createFolder(a: String, b: String, name: String) {
    val n = name.trim().ifEmpty { "Folder" }
    assignments[a] = n
    assignments[b] = n
    persist()
    openFolder = n
  }
  fun createEmptyFolder(name: String) {
    val n = name.trim().ifEmpty { "Folder" }
    val existing = folderNames.toSet()
    var finalName = n
    var counter = 1
    while (existing.contains(finalName)) {
      finalName = "$n $counter"
      counter++
    }
    UserLayout.saveEmptyFolder(context, finalName)
    emptyFolders.value = emptyFolders.value + finalName
    openFolder = finalName
  }
  fun renameFolder(old: String, raw: String) {
    val new = raw.trim()
    if (new.isEmpty() || new == old) return
    appsEff.filter { it.folder == old }.forEach { assignments[it.component.packageName] = new }
    persist()
    openFolder = new
  }
  fun moveOut(pkg: String) {
    val folder = appsEff.firstOrNull { it.component.packageName == pkg }?.folder ?: return
    assignments[pkg] = "" // explicit ungroup
    // No single-app folders: if only one remains, pop it out too.
    val remaining = appsEff.filter { it.folder == folder && it.component.packageName != pkg }
    if (remaining.size == 1) assignments[remaining[0].component.packageName] = ""
    persist()
    if (remaining.size <= 1) openFolder = null
  }
  fun onDrop(sourcePkg: String, targetKey: String?) {
    if (targetKey == null) return
    when {
      targetKey.startsWith(FOLDER_KEY) -> assign(sourcePkg, targetKey.removePrefix(FOLDER_KEY))
      targetKey.startsWith(APP_KEY) -> {
        val targetPkg = targetKey.removePrefix(APP_KEY)
        if (targetPkg == sourcePkg) return
        pendingPair = sourcePkg to targetPkg // ask for a name first
      }
    }
  }

  // --- over-the-air self-update -----------------------------------------------
  var update by remember { mutableStateOf<UpdateInfo?>(null) }
  var updateStatus by remember { mutableStateOf<String?>(null) }
  var pendingConfirm by remember { mutableStateOf<Intent?>(null) }
  val confirmLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
  // Check on launch, then periodically while the launcher runs (it's the
  // long-lived home, so a one-shot check would go stale). The Updates tile also
  // lets the user force a check at any time.
  LaunchedEffect(Unit) {
    while (true) {
      UpdateManager.checkForUpdate(context) { update = it }
      delay(UPDATE_CHECK_INTERVAL_MS)
    }
  }
  LaunchedEffect(pendingConfirm) {
    pendingConfirm?.let {
      confirmLauncher.launch(it)
      pendingConfirm = null
    }
  }
  DisposableEffect(Unit) {
    val receiver =
        object : android.content.BroadcastReceiver() {
          override fun onReceive(c: android.content.Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
              PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingConfirm =
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                updateStatus = "Confirm to update…"
              }
              PackageInstaller.STATUS_SUCCESS -> updateStatus = "Updated"
              else -> updateStatus = "Update failed"
            }
          }
        }
    val filter = android.content.IntentFilter(UPDATE_INSTALL_ACTION)
    if (android.os.Build.VERSION.SDK_INT >= 33)
        context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }

  CompositionLocalProvider(LocalTileDp provides tileDpFor(tileSize)) {
  Box(modifier = Modifier.fillMaxSize()) {
      // Background layer
      when {
          (backgroundType == ImmortalSettings.BG_IMAGE ||
              backgroundType == ImmortalSettings.BG_BLUR) && backgroundImagePath != null ->
              BackgroundImage(
                  uriString = backgroundImagePath!!,
                  blur = backgroundType == ImmortalSettings.BG_BLUR,
              )
          backgroundType == ImmortalSettings.BG_GRADIENT -> GradientBackground(backgroundGradient)
          backgroundType == ImmortalSettings.BG_SKY -> SkyBackground()
          else -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
      }
      // Day-progress bar: a thin line at the very top tracking how far through the day
      // we are, tinted with the live sky colour. Only with the Sky background.
      if (backgroundType == ImmortalSettings.BG_SKY && showDayProgress) {
        DayProgressBar(modifier = Modifier.align(Alignment.TopCenter))
      }
      Box(modifier = Modifier.fillMaxSize()
      .pointerInput(Unit) {
          // Right-edge back gesture: swipe from the last 64dp of the screen
          // leftward to go back (close any open folder/overlay).
          val edgeWidth = 64.dp.toPx()
          var startX = 0f
          var startY = 0f
          awaitEachGesture {
            val down = awaitFirstDown()
            if (down.position.x > size.width - edgeWidth) {
              startX = down.position.x
              startY = down.position.y
              var event: androidx.compose.ui.input.pointer.PointerEvent
              do {
                event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) {
                  val dx = event.changes.first().position.x - startX
                  val dy = kotlin.math.abs(event.changes.first().position.y - startY)
                  if (dx < -120f && dy < 80f && openFolder != null) {
                    openFolder = null
                  }
                  break
                }
              } while (event.changes.any { it.pressed })
            }
          }
      }
  ) {
    Column(
        modifier =
            Modifier.fillMaxHeight()
                // Cap the content width and center it so the grid stays
                // comfortably sized on large displays (e.g. Portal+ 1920px)
                // instead of stretching 6 columns across the whole panel. On the
                // smaller models this is effectively full-width (unchanged).
                // (widthIn must precede fillMaxWidth so the cap wins, then align
                // centers the capped content.)
                .align(Alignment.TopCenter)
                .widthIn(max = 1264.dp)
                .fillMaxWidth()
                // Top is padded clear of the 60dp systemui status-bar window,
                // which silently eats touches even while hidden in immersive —
                // so header action buttons stay tappable.
                .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 24.dp)
    ) {
      HeaderBar(
          onScreensaver = onStartScreensaver,
          onSleep = { ScreenControl.sleep(context) },
          onClock = {
            // Enable digital clock and reassert screensaver settings
            DigitalClockConfig.setEnabled(context, true)
            SettingsGuard.reaffirmScreensaver(context)
            // Open the full-screen preview so the user can see the clock
            // immediately; the system Dream will use this clock on the next idle.
            val previewIntent = Intent(context, DigitalClockPreviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(previewIntent) }
          },
      )
      Spacer(Modifier.size(20.dp))
      CountdownChips(version = homeResumeVersion)
      HomeNoteCard(version = noteVersion, onEdit = { showNote = true })
      if (showTabs && folderNames.isNotEmpty()) {
        CategoryTabs(
            categories = folderNames,
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.size(12.dp))
      }
      // The scrolling app grid, parameterized by its area modifier so it can be
      // hosted either directly (single page) or inside a HorizontalPager page.
      @Composable
      fun appGridArea(areaModifier: Modifier) {
        Box(
            modifier =
                areaModifier
                    .onGloballyPositioned { containerOrigin = it.boundsInWindow().topLeft }
                    .pointerInput(editMode) {
                      if (!editMode) return@pointerInput
                      detectDragGesturesAfterLongPress(
                          onDragStart = { local ->
                            val win = local + containerOrigin
                            dragPkg =
                                tileBounds.entries
                                    .firstOrNull {
                                      it.key.startsWith(APP_KEY) && it.value.contains(win)
                                    }
                                    ?.key
                                    ?.removePrefix(APP_KEY)
                            dragPos = win
                          },
                          onDrag = { change, delta ->
                            change.consume()
                            dragPos += delta
                          },
                          onDragEnd = {
                            dragPkg?.let { src ->
                              val target =
                                  tileBounds.entries
                                      .firstOrNull {
                                        it.key != APP_KEY + src && it.value.contains(dragPos)
                                      }
                                      ?.key
                              onDrop(src, target)
                            }
                            dragPkg = null
                          },
                          onDragCancel = { dragPkg = null },
                      )
                    },
        ) {
          LazyVerticalGrid(
              columns = GridCells.Fixed(gridColumnsFor(tileSize)),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalArrangement = Arrangement.spacedBy(20.dp),
              modifier = Modifier.focusRequester(homeGridFocus).focusGroup(),
          ) {
            // When a category tab is selected, the grid shows just that folder's apps.
            val activeTab = if (showTabs) selectedTab else null
            if (activeTab != null) {
              val tabApps = appsEff.filter { it.folder == activeTab && it.component.packageName !in hiddenPkgs }
              items(tabApps, key = { it.component.packageName }) { app ->
                val pkg = app.component.packageName
                AppTile(
                    app = app,
                    editMode = editMode,
                    dimmed = dragPkg == pkg,
                    modifier = Modifier.onGloballyPositioned { tileBounds[APP_KEY + pkg] = it.boundsInWindow() },
                    onDelete = { onUninstall(pkg) },
                    onHide = { hideApp(pkg) },
                    onAppInfo = { openAppInfo(context, pkg) },
                    onClick = { onLaunch(app.component) },
                )
              }
              return@LazyVerticalGrid
            }
            // Special + folder tiles persist in Manage mode (non-uninstallable);
            // only regular apps get a delete badge and become draggable.
            item { PortalHomeTile(onExitHome) }
            item { PortalHomeShortcutTile(onExitHome) }
            item { CameraTile(onOpenCamera) }
            item { StoreTile(onOpenStore) }
            item { SpeedTestTile(onClick = { showSpeedTest = true }) }
            item { NowPlayingTile(onClick = { showNowPlaying = true }) }
            item { NoteTile(onClick = { showNote = true }) }
            item { TransitTile(onClick = { showTransit = true }) }
            item { MyNoiseTile() }
            if (DailyContent.modeOf(dailyTileMode) != DailyContent.Mode.OFF) {
              item { DailyTile(mode = dailyTileMode, onClick = { showDaily = true }) }
            }
            items(folderNames, key = { it }) { name ->
              FolderTile(
                  name = name,
                  apps = appsEff.filter { it.folder == name },
                  modifier =
                      Modifier.onGloballyPositioned {
                        tileBounds[FOLDER_KEY + name] = it.boundsInWindow()
                      },
                  onClick = { openFolder = name },
              )
            }
            items(ungrouped, key = { it.component.packageName }) { app ->
              val pkg = app.component.packageName
              AppTile(
                  app = app,
                  editMode = editMode,
                  dimmed = dragPkg == pkg,
                  modifier =
                      Modifier.onGloballyPositioned {
                        tileBounds[APP_KEY + pkg] = it.boundsInWindow()
                      },
                  onDelete = { onUninstall(pkg) },
                  onHide = { hideApp(pkg) },
                  onAppInfo = { openAppInfo(context, pkg) },
                  onClick = { onLaunch(app.component) },
              )
            }
            // Always-present Updates tile, parked at the end of the grid. Tapping
            // installs a ready update, or forces a fresh check when up to date.
            item {
              UpdatesTile(update = update, status = updateStatus) {
                val info = update
                if (info != null) {
                  UpdateManager.installUpdate(context, info) { updateStatus = it }
                } else {
                  updateStatus = "Checking…"
                  UpdateManager.checkForUpdate(context) {
                    update = it
                    updateStatus = if (it == null) "Up to date" else null
                  }
                }
              }
            }
          }
        }
      }

      if (dashboardPage && !editMode) {
        // Two swipeable pages: apps, then a glanceable info dashboard.
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
          androidx.compose.foundation.pager.HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxWidth().weight(1f),
          ) { page ->
            if (page == 0) appGridArea(Modifier.fillMaxSize())
            else DashboardPage()
          }
          PagerDots(pagerState.currentPage, 2)
        }
      } else {
        appGridArea(Modifier.fillMaxWidth().weight(1f))
      }
      // Bottom widget stack (weather / calendar / system stats), pinned full-width
      // below the scrolling grid. Measured so the floating corner buttons can be
      // lifted clear of it in portrait. All off by default; an empty stack measures
      // ~0dp so the buttons stay in the corners.
      Column(
          modifier =
              Modifier.fillMaxWidth().onGloballyPositioned {
                bottomWidgetsHeight = with(widgetDensity) { it.size.height.toDp() }
              }
      ) {
        // Only the weather rides at the bottom of the main page. Calendar and system
        // stats live on the dashboard (2nd) page so the home grid keeps its space.
        if (weatherWidget != ImmortalSettings.WIDGET_OFF) {
          Spacer(Modifier.size(16.dp))
          WeatherWidget(mode = weatherWidget, fahrenheit = weatherFahrenheit)
        }
      }
    }

    // Floating ghost of the app being dragged.
    dragPkg?.let { pkg ->
      appsEff.firstOrNull { it.component.packageName == pkg }?.let { dragged ->
        val ghostDp = LocalTileDp.current
        val half = with(androidx.compose.ui.platform.LocalDensity.current) { (ghostDp / 2).toPx() }
        Image(
            bitmap = dragged.icon,
            contentDescription = null,
            modifier =
                Modifier.offset {
                      IntOffset(
                          (dragPos.x - half).roundToInt(),
                          (dragPos.y - half).roundToInt(),
                      )
                    }
                    .size(ghostDp)
                    .clip(RoundedCornerShape(20.dp)),
        )
      }
    }

    // Hidden apps restore button: appears beside ✎ when there are hidden apps.
    if (hiddenPkgs.isNotEmpty()) {
      Surface(
          color = Color(0xCC2B2B2B),
          shape = androidx.compose.foundation.shape.CircleShape,
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(end = 112.dp, bottom = 32.dp + bottomButtonLift)
                  .size(60.dp)
                  .tvFocusable(androidx.compose.foundation.shape.CircleShape) {
                    showHiddenPanel = true
                  },
      ) {
        Box(contentAlignment = Alignment.Center) {
          // Eye icon to signal "show hidden apps"
          Canvas(modifier = Modifier.size(28.dp)) {
            val w = size.minDimension; val s = w * 0.09f
            val stroke = Stroke(width = s, cap = StrokeCap.Round)
            drawOval(Color.White, topLeft = Offset(w*0.04f, w*0.28f), size = Size(w*0.92f, w*0.44f), style = stroke)
            drawCircle(Color.White, radius = w*0.14f, center = Offset(w*0.5f, w*0.5f), style = stroke)
            // Badge: number of hidden apps
          }
        }
      }
    }
    // Alexa shortcut: bottom-left corner, always visible.
    heyPkg?.let { pkg ->
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.align(Alignment.BottomStart)
                  .padding(start = 36.dp, bottom = 28.dp + bottomButtonLift)
                  .size(96.dp)
                  .tvFocusable(
                      shape = androidx.compose.foundation.shape.CircleShape,
                      onLongClick = { openHeyPicker(context, pkg) },
                  ) { fireHey(context, pkg) },
      ) {
        Image(
            painter = painterResource(R.drawable.alexa_icon),
            contentDescription = "Alexa",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp).clip(androidx.compose.foundation.shape.CircleShape),
        )
      }
    }
    // Manage / Done toggle lives in the bottom-right corner.
    EditButton(
        editMode = editMode,
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 32.dp + bottomButtonLift),
        onClick = { editMode = !editMode },
    )

    if (editMode) {
      NewFolderButton(
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(end = 112.dp, bottom = 32.dp + bottomButtonLift),
          onClick = { creatingFolder = true },
      )
    }

    openFolder?.let { name ->
      val folderApps = appsEff.filter { it.folder == name }
      if (folderApps.isEmpty()) {
        LaunchedEffect(name) { openFolder = null }
      } else {
        FolderOverlay(
            name = name,
            apps = folderApps,
            onLaunch = {
              onLaunch(it)
              openFolder = null
            },
            onRename = { renaming = name },
            onMoveOut = { moveOut(it) },
            onDismiss = { openFolder = null },
            extras =
                if (name == "Settings")
                    listOf(
                        FolderExtra("Immortal", ICON_GEAR) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, ImmortalSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Clock", ICON_TIME) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, ClockSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Screensaver", ICON_IMAGE) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, ScreensaverSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Welcome", ICON_WAVING_HAND) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, WelcomeSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Sleep", ICON_TIME) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, SleepSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Sounds", ICON_BELL) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, ChimeSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Countdowns", ICON_HOURGLASS) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, CountdownSettingsActivity::class.java))
                          }
                        },
                        FolderExtra("Cameras", ICON_CAMERA) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, CameraViewerActivity::class.java))
                          }
                        },
                        FolderExtra("Intercom", ICON_CALL) {
                          openFolder = null
                          runCatching {
                            context.startActivity(
                                Intent(context, IntercomActivity::class.java))
                          }
                        },
                        FolderExtra("Help", ICON_HELP) {
                          openFolder = null
                          onOpenHelp()
                        })
                else emptyList(),
        )
      }
    }

    // Back button: shown when the Settings folder is open (and any other
    // folder). Lets the user close the folder without using the system
    // back button or tapping outside.
    if (openFolder != null) {
      FolderBackButton(onClick = { openFolder = null })
    }

    // Name a new folder (created by dropping one app on another).
    pendingPair?.let { (src, tgt) ->
      NameOverlay(
          title = "Name folder",
          initial = "Folder",
          confirmLabel = "Create",
          onConfirm = {
            createFolder(src, tgt, it)
            pendingPair = null
          },
          onCancel = { pendingPair = null },
      )
    }

    // Rename an existing folder.
    renaming?.let { old ->
      NameOverlay(
          title = "Rename folder",
          initial = old,
          confirmLabel = "Rename",
          onConfirm = {
            renameFolder(old, it)
            renaming = null
          },
          onCancel = { renaming = null },
      )
    }

    // Create a new empty folder (from the + button in Manage mode).
    if (creatingFolder) {
      NameOverlay(
          title = "New folder",
          initial = UserLayout.nextFolderName(folderNames.toSet()),
          confirmLabel = "Create",
          onConfirm = {
            createEmptyFolder(it)
            creatingFolder = false
          },
          onCancel = { creatingFolder = false },
      )
    }

    // Speed-test pop-out overlay (rendered at top level so it isn't clipped by the grid).
    if (showSpeedTest) {
      SpeedTestOverlay(onDismiss = { showSpeedTest = false })
    }

    // Daily quote / word / trivia pop-out overlay.
    if (showDaily) {
      DailyOverlay(mode = dailyTileMode, onDismiss = { showDaily = false })
    }

    // Leave-a-note overlay (typed sticky + voice memo).
    if (showNote) {
      NoteOverlay(onDismiss = { showNote = false; noteVersion++ })
    }

    // Dublin transit board overlay.
    if (showTransit) {
      TransitOverlay(onDismiss = { showTransit = false })
    }

    if (showNowPlaying) {
      NowPlayingOverlay(onDismiss = { showNowPlaying = false })
    }

    // Hidden apps panel: shows all hidden apps so users can restore them individually.
    if (showHiddenPanel) {
      BackHandler { showHiddenPanel = false }
      Box(
          contentAlignment = Alignment.Center,
          modifier =
              Modifier.fillMaxSize()
                  .background(Color(0xCC000000))
                  .clickable(
                      interactionSource = remember { MutableInteractionSource() },
                      indication = null,
                  ) { showHiddenPanel = false },
      ) {
        Surface(
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.widthIn(max = 600.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        ) {
          Column(modifier = Modifier.padding(28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text("Hidden apps", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
              if (hiddenAppsEff.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1565C0),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.tvFocusable(RoundedCornerShape(10.dp)) {
                      UserLayout.unhideAllPackages(context)
                      hiddenPkgs = emptySet()
                      showHiddenPanel = false
                    },
                ) {
                  Text("Restore all", color = Color.White, fontSize = 15.sp,
                      modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
              }
            }
            Spacer(Modifier.size(20.dp))
            if (hiddenAppsEff.isEmpty()) {
              Text("No hidden apps.", color = Color(0xFF9A9A9A), fontSize = 16.sp)
            } else {
              LazyVerticalGrid(
                  columns = GridCells.Fixed(4),
                  horizontalArrangement = Arrangement.spacedBy(16.dp),
                  verticalArrangement = Arrangement.spacedBy(16.dp),
                  modifier = Modifier.heightIn(max = 400.dp),
              ) {
                items(hiddenAppsEff, key = { it.component.packageName }) { app ->
                  Column(horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier.tvFocusable(RoundedCornerShape(16.dp)) {
                        unhideApp(app.component.packageName)
                      }) {
                    Box {
                      Image(bitmap = app.icon, contentDescription = app.label,
                          modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).alpha(0.5f))
                      Surface(color = Color(0xFF1565C0),
                          shape = androidx.compose.foundation.shape.CircleShape,
                          modifier = Modifier.size(24.dp).align(Alignment.TopEnd)) {
                        Box(contentAlignment = Alignment.Center) {
                          Text("+", color = Color.White, fontSize = 16.sp)
                        }
                      }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(app.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  } // outer background Box
  } // CompositionLocalProvider(LocalTileDp)
}

private const val APP_KEY = "app:"
private const val FOLDER_KEY = "folder:"

// Overnight re-sleep timings. A wake with no interaction is treated as stray and
// sleeps again after the short grace; once the user actually touches the screen we
// switch to a normal screen-timeout so they can use the device, resetting it on
// each interaction.
private const val OVERNIGHT_STRAY_WAKE_MS = 5_000L
private const val OVERNIGHT_ACTIVE_TIMEOUT_MS = 60_000L
private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 hours

// --- tile sizing ----------------------------------------------------------------
// The grid's tile edge, provided once at the top of the tree so every tile
// (apps, folders, built-ins, the drag ghost) follows the user's size setting.
// Standard is the original 6-column/88dp look; Large is 5 columns of 110dp tiles,
// closer to the stock Portal launcher.
private val LocalTileDp = compositionLocalOf { 88.dp }

private fun tileDpFor(size: String): Dp =
    when (size) {
      ImmortalSettings.SIZE_XL -> 140.dp
      ImmortalSettings.SIZE_LARGE -> 110.dp
      else -> 88.dp
    }

private fun gridColumnsFor(size: String): Int =
    when (size) {
      ImmortalSettings.SIZE_XL -> 4
      ImmortalSettings.SIZE_LARGE -> 5
      else -> 6
    }

@Composable
private fun HeaderBar(onScreensaver: () -> Unit, onClock: () -> Unit, onSleep: () -> Unit) {
  var now by remember { mutableStateOf(Date()) }
  androidx.compose.runtime.LaunchedEffect(Unit) {
    while (true) {
      now = Date()
      delay(1000)
    }
  }
  val context = androidx.compose.ui.platform.LocalContext.current
  // The unit preference is re-read on resume and keys the fetch loop, so flipping
  // °F/°C in Immortal Settings updates the header the moment the user returns.
  var weatherUnit by remember { mutableStateOf(ImmortalSettings.load(context).weatherUnit) }
  // The clock format is likewise re-read on resume so flipping Auto/12h/24h in
  // Immortal Settings updates the header the moment the user returns home.
  var use24Hour by remember { mutableStateOf(ImmortalSettings.use24HourClock(context)) }
  var showSunTimes by remember { mutableStateOf(ImmortalSettings.load(context).showSunTimes) }
  var showNameDay by remember { mutableStateOf(ImmortalSettings.load(context).showNameDay) }
  var showFeastDay by remember { mutableStateOf(ImmortalSettings.load(context).showFeastDay) }
  var showNextEvent by remember { mutableStateOf(ImmortalSettings.load(context).showNextEvent) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) {
        weatherUnit = ImmortalSettings.load(context).weatherUnit
        use24Hour = ImmortalSettings.use24HourClock(context)
        showSunTimes = ImmortalSettings.load(context).showSunTimes
        showNameDay = ImmortalSettings.load(context).showNameDay
        showFeastDay = ImmortalSettings.load(context).showFeastDay
        showNextEvent = ImmortalSettings.load(context).showNextEvent
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }
  // Today's sunrise/sunset, refreshed every few hours (keyless Open-Meteo).
  val sunTimes by produceState<Weather.SunTimes?>(initialValue = null) {
    while (true) {
      val s = withContext(Dispatchers.IO) { Weather.fetchSunTimes(context) }
      if (s != null) {
        value = s
        delay(6L * 60 * 60 * 1000)
      } else {
        delay(5L * 60 * 1000)
      }
    }
  }
  val weather by produceState(initialValue = "", weatherUnit) {
    // Retry soon on failure (e.g. a transient geolocation rate-limit), then
    // refresh periodically. Location is cached after the first success.
    while (true) {
      val w = withContext(Dispatchers.IO) { Weather.fetch(context) }
      if (w.isNotBlank()) {
        value = w
        delay(30L * 60 * 1000) // refresh every 30 min
      } else {
        delay(60L * 1000) // retry in 1 min
      }
    }
  }
  val battery = batteryState()

  // Layout: the big clock anchors the left; the weather and date stack on the
  // right, right-aligned, so the header reads as a balanced pair of blocks. The
  // clock and the right-hand stack are centred against each other.
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    // Clock text.
    Text(
        SimpleDateFormat(if (use24Hour) "H:mm" else "h:mm", Locale.getDefault()).format(now),
        color = Color.White,
        fontSize = 56.sp,
        fontWeight = FontWeight.Light,
        lineHeight = 56.sp,
    )
    Spacer(Modifier.size(28.dp))
    // Screensaver entry — the stock launcher's stacked-photo icon so the affordance
    // reads the same as the Portal users already know.
    Surface(
        color = Color(0x33FFFFFF),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            Modifier.size(56.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
              onScreensaver()
            },
    ) {
      Box(contentAlignment = Alignment.Center) { StackedPhotoIcon() }
    }
    Spacer(Modifier.size(12.dp))
    Surface(
        color = Color(0x33FFFFFF),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            Modifier.size(56.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
              onClock()
            },
    ) {
      Box(contentAlignment = Alignment.Center) { ClockIcon() }
    }
    Spacer(Modifier.size(12.dp))
    Surface(
        color = Color(0x33FFFFFF),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            Modifier.size(56.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
              onSleep()
            },
    ) {
      Box(contentAlignment = Alignment.Center) { MoonIcon() }
    }
    // Brightness button: cycles dim → medium → full using window-level brightness
    // (no WRITE_SETTINGS permission needed — affects this Activity's window only).
    val activity = remember { context as? android.app.Activity }
    var brightnessIdx by remember { mutableStateOf(2) }
    Spacer(Modifier.size(12.dp))
    Surface(
        color = Color(0x33FFFFFF),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            Modifier.size(56.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
              brightnessIdx = (brightnessIdx + 1) % 3
              val bval = when (brightnessIdx) { 0 -> 0.12f; 1 -> 0.50f; else -> 1.0f }
              activity?.window?.let { w -> val p = w.attributes; p.screenBrightness = bval; w.attributes = p }
            },
    ) {
      Box(contentAlignment = Alignment.Center) { SunIcon() }
    }
    // Volume button: tap to show/hide +/– controls inline.
    val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volLevel by remember { mutableStateOf(audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVol = remember { audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var showVol by remember { mutableStateOf(false) }
    Spacer(Modifier.size(12.dp))
    Surface(
        color = if (showVol) Color(0x88FFFFFF) else Color(0x33FFFFFF),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier =
            Modifier.size(56.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
              showVol = !showVol
            },
    ) {
      Box(contentAlignment = Alignment.Center) { VolumeIcon() }
    }
    if (showVol) {
      Spacer(Modifier.size(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = Color(0x33FFFFFF), shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(44.dp).clickable {
              volLevel = (volLevel - 1).coerceAtLeast(0)
              audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, volLevel, 0)
            }) {
          Box(contentAlignment = Alignment.Center) { Text("−", color = Color.White, fontSize = 22.sp) }
        }
        Text("$volLevel", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp))
        Surface(color = Color(0x33FFFFFF), shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(44.dp).clickable {
              volLevel = (volLevel + 1).coerceAtMost(maxVol)
              audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, volLevel, 0)
            }) {
          Box(contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 22.sp) }
        }
      }
    }
    Spacer(Modifier.weight(1f))
    Column(horizontalAlignment = Alignment.End) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        // Charge level is OPTIONAL — only Portal Go has a battery; mains-powered
        // Portals report no battery present, so we render nothing for them.
        if (battery.present) {
          BatteryIndicator(percent = battery.percent, charging = battery.charging)
        }
        if (weather.isNotBlank()) {
          Text(weather, color = Color.White, fontSize = 30.sp)
        }
      }
      Text(
          SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
          color = Color(0xFFDADADA),
          fontSize = 18.sp,
          modifier = Modifier.padding(top = 4.dp),
      )
      if (showSunTimes) {
        sunTimes?.let { st ->
          val (sr, ss) = st.formatted(use24Hour)
          SunArc(
              sunriseMin = minuteOfDay(st.sunriseMillis),
              sunsetMin = minuteOfDay(st.sunsetMillis),
              riseLabel = sr,
              setLabel = ss,
              modifier = Modifier.padding(top = 6.dp),
          )
        }
      }
      if (showNameDay) {
        val nameDay = remember(now) { NameDays.todayLabel() }
        if (nameDay.isNotEmpty()) {
          Text(
              "🎉 $nameDay",
              color = Color(0xFFB0B0B0),
              fontSize = 14.sp,
              modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
      if (showFeastDay) {
        val feast = remember(now) { FeastDays.forToday() }
        if (feast.isNotEmpty()) {
          Text(
              "✝ $feast",
              color = Color(0xFFB0B0B0),
              fontSize = 14.sp,
              modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
      if (showNextEvent) {
        val nextEvent by produceState<CalendarEvent?>(initialValue = null) {
          if (CalendarHelper.hasPermission(context)) {
            value = withContext(Dispatchers.IO) { CalendarHelper.upcoming(context).firstOrNull() }
          }
        }
        nextEvent?.let { ev ->
          val whenFmt = remember(ev.begin) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ev.begin }
            if (ev.allDay) SimpleDateFormat("MMM d", Locale.getDefault()).format(cal.time)
            else SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(cal.time)
          }
          Text(
              "📅 $whenFmt · ${ev.title}",
              color = Color(0xFFB0B0B0),
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(top = 2.dp),
          )
        }
      }
    }
  }
}

/** What the forecast widget currently has to show. */
private sealed interface ForecastState {
  /** First fetch still in flight — render nothing so the bar doesn't flash. */
  object Loading : ForecastState
  /** Tried and failed with nothing cached — show a friendly note instead. */
  object Unavailable : ForecastState
  data class Ready(val forecast: Weather.Forecast) : ForecastState
}

// The two swipeable forecast pages, in left-to-right order.
private const val PAGE_HOURLY = 0
private const val PAGE_DAILY = 1

/**
 * Optional home-screen forecast, shown full-width below the app grid when enabled in
 * Immortal Settings ▸ Weather. One network call fetches both views; the user swipes
 * left/right between the hourly and 7-day pages. [mode] picks which page shows first
 * (and jumps to it if the setting changes). [fahrenheit] only keys a re-fetch when the
 * unit changes — the unit itself is resolved inside [Weather.fetchForecast].
 *
 * Failure handling: the fetch retries every minute until it succeeds. While the very
 * first attempt is in flight nothing is drawn (no flash). If it can't be reached and
 * we have no forecast yet, a quiet "unavailable" note replaces the data; once a
 * forecast has loaded, a later failed refresh keeps the last good one on screen rather
 * than blanking it.
 */
@Composable
private fun WeatherWidget(mode: String, fahrenheit: Boolean) {
  val context = androidx.compose.ui.platform.LocalContext.current
  // Keyed on the unit only: switching pages is a local swipe, not a re-fetch.
  val state by
      produceState<ForecastState>(initialValue = ForecastState.Loading, fahrenheit) {
        while (true) {
          val f = withContext(Dispatchers.IO) { Weather.fetchForecast(context) }
          if (f != null) {
            value = ForecastState.Ready(f)
            delay(30L * 60 * 1000) // refresh every 30 min
          } else {
            // Keep showing the last good forecast if we have one; only surface the
            // note when there's nothing to display.
            if (value !is ForecastState.Ready) value = ForecastState.Unavailable
            delay(60L * 1000) // retry in 1 min
          }
        }
      }
  // Air quality / UV / pollen rides along under the forecast (keyless Open-Meteo,
  // refreshed every ~30 min). Lives here, with the weather, rather than in System stats.
  val air by
      produceState<Weather.AirQuality?>(initialValue = null) {
        while (true) {
          val aq = withContext(Dispatchers.IO) { Weather.fetchAirQuality(context) }
          if (aq != null) value = aq
          delay(30L * 60 * 1000)
        }
      }

  if (state is ForecastState.Loading) return

  val startPage = if (mode == ImmortalSettings.WIDGET_DAILY) PAGE_DAILY else PAGE_HOURLY
  val pagerState = rememberPagerState(initialPage = startPage) { 2 }
  // Follow the setting: if the user changes the default in Immortal Settings, jump to
  // that page when they come back (no-op on first composition, where it already matches).
  LaunchedEffect(mode) {
    if (pagerState.currentPage != startPage) pagerState.scrollToPage(startPage)
  }

  val ready = state as? ForecastState.Ready
  Surface(
      color = Color(0x14FFFFFF),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
  ) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
      // Header: the current page's title, plus a two-dot indicator hinting the swipe.
      Row(
          modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            when {
              ready == null -> "Forecast"
              pagerState.currentPage == PAGE_DAILY -> "7-day forecast"
              else -> "Hourly forecast"
            },
            color = Color(0xFFBFBFBF),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (ready != null) PageDots(selected = pagerState.currentPage, count = 2)
      }
      if (ready != null) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
          // Each page's cells share the width evenly, spanning the whole card.
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (page == PAGE_HOURLY) {
              ready.forecast.hours.forEach { HourCell(it, Modifier.weight(1f)) }
            } else {
              ready.forecast.days.forEach { DayCell(it, Modifier.weight(1f)) }
            }
          }
        }
      } else {
        // Unavailable: no connection / location yet. Retries quietly in the
        // background, so no action is needed from the user.
        Text(
            "Forecast unavailable. It'll appear once your Portal is back online.",
            color = Color(0xFF9A9A9A),
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
      }
      // Compact air-quality strip beneath the forecast: "AQI 42 · Good   UV 3.2   Pollen Low".
      air?.let { aq -> AirQualityStrip(aq) }
    }
  }
}

/** One-line air-quality / UV / pollen summary shown under the forecast pager, as
 *  colour-tinted emoji chips so it stands out from the muted forecast row. */
@Composable
private fun AirQualityStrip(aq: Weather.AirQuality) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    AqChip("💨", "AQI ${aq.aqi} · ${aq.aqiLabel}", aqiColor(aq.aqi))
    if (aq.uvIndex > 0.0) AqChip("☀️", "UV ${String.format("%.1f", aq.uvIndex)}", uvColor(aq.uvIndex))
    if (aq.pollen.isNotEmpty()) AqChip("🌸", "Pollen ${aq.pollen}", Color(0xFFB388FF))
  }
}

/** A small tinted chip: emoji + label, with a faint background of [accent]. */
@Composable
private fun AqChip(emoji: String, label: String, accent: Color) {
  Surface(color = accent.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
      Text(emoji, fontSize = 14.sp)
      Text("  $label", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
  }
}

/** EU-AQI banding colour (green→good, climbing to red→very poor). */
private fun aqiColor(aqi: Int): Color = when {
  aqi <= 20 -> Color(0xFF66BB6A)
  aqi <= 40 -> Color(0xFF9CCC65)
  aqi <= 60 -> Color(0xFFFFD54F)
  aqi <= 80 -> Color(0xFFFFB74D)
  aqi <= 100 -> Color(0xFFFF8A65)
  else -> Color(0xFFEF5350)
}

/** UV-index banding colour. */
private fun uvColor(uv: Double): Color = when {
  uv < 3 -> Color(0xFF66BB6A)
  uv < 6 -> Color(0xFFFFD54F)
  uv < 8 -> Color(0xFFFFB74D)
  uv < 11 -> Color(0xFFFF8A65)
  else -> Color(0xFFB388FF)
}

/** Small page-position dots, hinting the forecast can be swiped between its pages. */
@Composable
private fun PageDots(selected: Int, count: Int) {
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(count) { i ->
      Box(
          modifier =
              Modifier.size(7.dp)
                  .clip(androidx.compose.foundation.shape.CircleShape)
                  .background(if (i == selected) Color.White else Color(0x55FFFFFF)))
    }
  }
}

@Composable
private fun HourCell(h: Weather.HourForecast, modifier: Modifier = Modifier) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier.padding(vertical = 2.dp),
  ) {
    Text(h.label, color = Color(0xFFCFCFCF), fontSize = 12.sp, maxLines = 1)
    Text(Weather.emoji(h.code), fontSize = 22.sp, modifier = Modifier.padding(vertical = 3.dp))
    Text("${h.temp}°", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun DayCell(d: Weather.DayForecast, modifier: Modifier = Modifier) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier.padding(vertical = 2.dp),
  ) {
    Text(d.label, color = Color(0xFFCFCFCF), fontSize = 12.sp, maxLines = 1)
    Text(Weather.emoji(d.code), fontSize = 22.sp, modifier = Modifier.padding(vertical = 3.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("${d.hi}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
      Text("${d.lo}°", color = Color(0xFF9A9A9A), fontSize = 15.sp)
    }
  }
}

// --- "Hey" assistant trigger -------------------------------------------------
// Millennium publishes an exported receiver for this action; the launcher only
// fires it. Release build preferred, debug fallback for sideloaded test devices.
private const val HEY_TRIGGER_ACTION = "com.millennium.TRIGGER_ASSISTANT"
private val HEY_PACKAGES = listOf("com.millennium", "com.millennium.debug")

/** The installed Millennium ("hey") package, release preferred, or null if absent. */
private fun heyPackage(context: android.content.Context): String? =
    HEY_PACKAGES.firstOrNull { pkg ->
      try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
      } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
      }
    }

/** Ask Millennium to activate the user's active assistant (same path as a wake word). */
private fun fireHey(context: android.content.Context, pkg: String) {
  context.sendBroadcast(Intent(HEY_TRIGGER_ACTION).setPackage(pkg))
}

/** Millennium's assistant picker (premium: choose which assistant to talk to). */
private const val HEY_PICKER_ACTIVITY = "com.millennium.ui.HeyPickerActivity"

/** Long-press: open Millennium's picker. Falls back to a normal trigger if the
 *  installed Millennium predates the picker (so the gesture is never a dead end). */
private fun openHeyPicker(context: android.content.Context, pkg: String) {
  val ok =
      runCatching {
            context.startActivity(
                Intent()
                    .setClassName(pkg, HEY_PICKER_ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
          .isSuccess
  if (!ok) fireHey(context, pkg)
}

/** White line-art microphone glyph for the header "hey" button. */
@Composable
private fun MicGlyph() {
  Canvas(modifier = Modifier.size(28.dp)) {
    val w = size.minDimension
    val s = w * 0.08f
    val stroke =
        Stroke(
            width = s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    // Capsule mic body.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(w * 0.38f, w * 0.16f),
        size = Size(w * 0.24f, w * 0.40f),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        style = stroke,
    )
    // Cradle arc hugging the bottom of the body.
    drawArc(
        color = Color.White,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.26f, w * 0.24f),
        size = Size(w * 0.48f, w * 0.48f),
        style = stroke,
    )
    // Stem + base.
    drawLine(Color.White, Offset(w * 0.5f, w * 0.72f), Offset(w * 0.5f, w * 0.84f), strokeWidth = s)
    drawLine(Color.White, Offset(w * 0.38f, w * 0.84f), Offset(w * 0.62f, w * 0.84f), strokeWidth = s)
  }
}

/** White line-art photo glyph (single frame), matching the stock screensaver. */
@Composable
private fun StackedPhotoIcon() {
  Canvas(modifier = Modifier.size(30.dp)) {
    val w = size.minDimension
    val s = w * 0.075f // stroke width
    val stroke =
        Stroke(
            width = s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    // Photo frame outline.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(w * 0.16f, w * 0.20f),
        size = Size(w * 0.68f, w * 0.60f),
        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
        style = stroke,
    )
    // Sun.
    drawCircle(
        color = Color.White,
        radius = w * 0.075f,
        center = Offset(w * 0.36f, w * 0.40f),
        style = Stroke(width = s),
    )
    // Mountains.
    val path =
        androidx.compose.ui.graphics.Path().apply {
          moveTo(w * 0.20f, w * 0.72f)
          lineTo(w * 0.40f, w * 0.48f)
          lineTo(w * 0.52f, w * 0.60f)
          lineTo(w * 0.62f, w * 0.50f)
          lineTo(w * 0.80f, w * 0.72f)
        }
    drawPath(path, color = Color.White, style = stroke)
  }
}

/** Simple white line-art clock icon for the digital clock entry point. */
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
private fun EditButton(editMode: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Surface(
      color = if (editMode) Color(0xFFE53935) else Color(0xCC2B2B2B),
      shape = androidx.compose.foundation.shape.CircleShape,
      modifier =
          modifier.size(60.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
            onClick()
          },
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(if (editMode) "✓" else "✎", color = Color.White, fontSize = 28.sp)
    }
  }
}

/**
 * Floating back button shown in the bottom-right corner whenever a folder
 * or overlay is open in the launcher. Tapping it closes the folder/overlay.
 * Separate from the settings pages so we can position it over the folder
 * overlay specifically.
 */
@Composable
fun FolderBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val buttonColor = MaterialTheme.colorScheme.primary
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .padding(end = 36.dp, bottom = 32.dp)
              .wrapContentSize(Alignment.BottomEnd),
  ) {
    Surface(
        color = buttonColor,
        shape = androidx.compose.foundation.shape.CircleShape,
        onClick = onClick,
        modifier = Modifier.size(64.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(36.dp)) {
          val strokeWidth = 4.dp.toPx()
          val path = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.20f)
            lineTo(size.width * 0.28f, size.height * 0.50f)
            lineTo(size.width * 0.72f, size.height * 0.80f)
          }
          drawPath(
              path = path,
              color = Color.Black,
              style = androidx.compose.ui.graphics.drawscope.Stroke(
                  width = strokeWidth,
                  cap = androidx.compose.ui.graphics.StrokeCap.Round,
                  join = androidx.compose.ui.graphics.StrokeJoin.Round,
              ),
          )
          drawLine(
              color = Color.Black,
              start = Offset(size.width * 0.68f, size.height * 0.50f),
              end = Offset(size.width * 0.28f, size.height * 0.50f),
              strokeWidth = strokeWidth,
              cap = androidx.compose.ui.graphics.StrokeCap.Round,
          )
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

/** Page indicator dots for the home pager. */
@Composable
private fun PagerDots(current: Int, count: Int) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      horizontalArrangement = Arrangement.Center,
  ) {
    repeat(count) { i ->
      Box(
          modifier =
              Modifier.padding(horizontal = 5.dp)
                  .size(if (i == current) 9.dp else 7.dp)
                  .clip(androidx.compose.foundation.shape.CircleShape)
                  .background(if (i == current) Color.White else Color(0x55FFFFFF)),
      )
    }
  }
}

/** The second home page: a glanceable, full-screen info dashboard (big clock, date,
 * sun times, countdowns, weather, calendar, system stats — whatever the user has on).
 * Reuses the existing home widgets so it tracks their settings automatically. */
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
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.size(8.dp))
    Text(timeFmt.format(now), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
    Text(
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now),
        color = Color(0xFFDADADA),
        fontSize = 22.sp,
    )
    Spacer(Modifier.size(20.dp))
    CountdownChips()
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
        calendarOn = calendarOn,
        statsOn = statsOn,
        timeProgressOn = timeProgressOn,
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
    calendarOn: Boolean,
    statsOn: Boolean,
    timeProgressOn: Boolean,
    onCalendar: (Boolean) -> Unit,
    onStats: (Boolean) -> Unit,
    onTimeProgress: (Boolean) -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("Dashboard widgets", color = Color(0xFF8A8A8A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.size(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

private data class BatteryReading(val present: Boolean, val percent: Int, val charging: Boolean)

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
private fun PortalHomeTile(onClick: () -> Unit) {
  // Bridge to Meta's stock launcher — the only context allowed to open the
  // trusted-caller apps (Contacts, Camera, Photos), so this is how the user
  // reaches calling. Tapping Portal's home button returns to Immortal.
  BuiltInTile(
      label = "Calls",
      background = Color(0xFF1FA463),
      glyph = ICON_CALL,
      onClick = onClick,
  )
}

/**
 * Dedicated "Portal Home" shortcut. Use this to jump to the stock Portal
 * launcher when you need features only it provides (Messenger, etc.).
 * Distinct from the "Calls" tile above so the user can reach the Portal
 * home without having to go through the calling flow.
 */
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
  var listenerOn by remember { mutableStateOf(NowPlaying.listenerEnabled(context)) }
  var track by remember { mutableStateOf<NowPlaying.Track?>(null) }
  var playing by remember { mutableStateOf(audioMgr.isMusicActive) }

  // Re-check the listener grant on resume (the user may have just enabled it) and poll
  // the current track while open.
  LaunchedEffect(Unit) {
    while (true) {
      listenerOn = NowPlaying.listenerEnabled(context)
      track = if (listenerOn) withContext(Dispatchers.IO) { NowPlaying.current(context) } else null
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

        val art = track?.art
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
                  runCatching {
                    context.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                  }
                }) {
              Text("Enable now-playing access", color = Color.White, fontSize = 15.sp,
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

/** A non-app tile injected into a folder (e.g. the Screensaver settings entry). */
private data class FolderExtra(val label: String, val glyph: String, val onClick: () -> Unit)

/** A built-in launcher tile: a rounded colour tile with a centered white vector
 * glyph, styled to sit naturally beside real app icons. */
@Composable
private fun BuiltInTile(
    label: String,
    background: Color,
    glyph: String,
    onClick: () -> Unit,
) {
  val path = remember(glyph) { PathParser().parsePathString(glyph).toPath() }
  val tileDp = LocalTileDp.current
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(4.dp).tvFocusable(RoundedCornerShape(22.dp)) { onClick() },
  ) {
    Surface(
        color = background,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(tileDp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp * (tileDp / 88.dp))) {
          val s = size.minDimension / 24f
          scale(s, s, pivot = Offset.Zero) { drawPath(path, Color.White) }
        }
      }
    }
    Spacer(Modifier.size(8.dp))
    Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, textAlign = TextAlign.Center)
  }
}

@Composable
private fun FolderTile(
    name: String,
    apps: List<AppEntry>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
  val tileDp = LocalTileDp.current
  val scale = tileDp / 88.dp // mini-icon grid scales with the tile
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier.padding(4.dp).tvFocusable(RoundedCornerShape(22.dp)) { onClick() },
  ) {
    Surface(
        color = Color(0xFF3A3A3A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(tileDp),
    ) {
      Column(
          modifier = Modifier.padding(13.dp * scale),
          verticalArrangement = Arrangement.spacedBy(6.dp * scale),
      ) {
        apps.chunked(2).take(2).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
            row.take(2).forEach { app ->
              Image(
                  bitmap = app.icon,
                  contentDescription = null,
                  modifier = Modifier.size(25.dp * scale).clip(RoundedCornerShape(7.dp)),
              )
            }
          }
        }
      }
    }
    Spacer(Modifier.size(8.dp))
    Text(
        name,
        color = Color.White,
        fontSize = 15.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun FolderOverlay(
    name: String,
    apps: List<AppEntry>,
    onLaunch: (ComponentName) -> Unit,
    onRename: () -> Unit,
    onMoveOut: (String) -> Unit,
    onDismiss: () -> Unit,
    extras: List<FolderExtra> = emptyList(),
) {
  // Rendered inside the launcher's own (immersive) window — NOT a Dialog, which
  // would spawn a separate window and momentarily reveal the system bars.
  val noRipple = remember { MutableInteractionSource() }
  val tileBounds = remember { mutableStateMapOf<String, Rect>() }
  var panel by remember { mutableStateOf(Rect.Zero) }
  var dragPkg by remember { mutableStateOf<String?>(null) }
  var dragPos by remember { mutableStateOf(Offset.Zero) }

  // Remote support: Back closes the folder, and focus moves into the grid on open
  // so the D-pad works immediately (the folder was previously unusable by remote).
  BackHandler { onDismiss() }
  val gridFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

  // The folder widens (more columns) as it fills, so a packed folder spreads left/right
  // instead of becoming a tall narrow strip. Column count scales with the item count;
  // the panel width grows to match and is clamped to the screen (tiles shrink only if
  // it would otherwise overflow). The grid scrolls vertically once very full.
  val totalItems = extras.size + apps.size
  val cols = when {
    totalItems <= 6 -> 3
    totalItems <= 12 -> 4
    totalItems <= 20 -> 5
    else -> 6
  }
  val tileScale = LocalTileDp.current / 88.dp
  val desiredWidth = (420.dp + 126.dp * (cols - 3)) * tileScale
  val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
  val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

  Box(
      contentAlignment = Alignment.Center,
      modifier =
          Modifier.fillMaxSize()
              // Remote BACK closes the folder. Intercept in the preview (tunnelling)
              // phase so the focus system doesn't swallow it first.
              .onPreviewKeyEvent { e ->
                if (e.key == Key.Back || e.key == Key.Escape) {
                  if (e.type == KeyEventType.KeyUp) onDismiss()
                  true // consume down+up so the focus system doesn't eat it first
                } else false
              }
              .background(Color(0xCC000000))
              .clickable(interactionSource = noRipple, indication = null) { onDismiss() }
              // Drag an app out of the panel to remove it from the folder.
              .pointerInput(apps) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                      dragPkg =
                          tileBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                      dragPos = pos
                    },
                    onDrag = { change, delta ->
                      change.consume()
                      dragPos += delta
                    },
                    onDragEnd = {
                      val pkg = dragPkg
                      if (pkg != null && !panel.contains(dragPos)) onMoveOut(pkg)
                      dragPkg = null
                    },
                    onDragCancel = { dragPkg = null },
                )
              },
  ) {
    Surface(
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(28.dp),
        modifier =
            // Width grows with the column count and tile size, clamped to 94% of the
            // screen so it never overflows the panel off-screen.
            Modifier.width(desiredWidth)
                .widthIn(max = screenWidth * 0.94f)
                .onGloballyPositioned { panel = it.boundsInWindow() }
                .clickable(interactionSource = noRipple, indication = null) {},
    ) {
      Column(modifier = Modifier.padding(28.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(name, color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
          // Rename.
          Surface(
              color = Color(0x33FFFFFF),
              shape = androidx.compose.foundation.shape.CircleShape,
              modifier =
                  Modifier.size(40.dp).tvFocusable(androidx.compose.foundation.shape.CircleShape) {
                    onRename()
                  },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text("✎", color = Color.White, fontSize = 18.sp)
            }
          }
        }
        Spacer(Modifier.size(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            // Cap height so a very full folder scrolls instead of overflowing the screen.
            modifier = Modifier.heightIn(max = screenHeight * 0.62f).focusRequester(gridFocus).focusGroup(),
        ) {
          extras.forEach { extra ->
            item(key = "extra:${extra.label}") {
              BuiltInTile(
                  label = extra.label,
                  background = Color(0xFF5B6BC0),
                  glyph = extra.glyph,
                  onClick = extra.onClick,
              )
            }
          }
          items(apps, key = { it.component.packageName }) { app ->
            val pkg = app.component.packageName
            AppTile(
                app = app,
                editMode = false,
                dimmed = dragPkg == pkg,
                modifier =
                    Modifier.onGloballyPositioned { tileBounds[pkg] = it.boundsInWindow() },
                onClick = { onLaunch(app.component) },
            )
          }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            "Drag an app out to remove it",
            color = Color(0xFF8A8A8A),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
      }
    }
  }

  // Floating ghost of the app being dragged out.
  dragPkg?.let { pkg ->
    apps.firstOrNull { it.component.packageName == pkg }?.let { dragged ->
      val ghostDp = LocalTileDp.current
      val half = with(androidx.compose.ui.platform.LocalDensity.current) { (ghostDp / 2).toPx() }
      Image(
          bitmap = dragged.icon,
          contentDescription = null,
          modifier =
              Modifier.offset {
                    IntOffset((dragPos.x - half).roundToInt(), (dragPos.y - half).roundToInt())
                  }
                  .size(ghostDp)
                  .clip(RoundedCornerShape(20.dp)),
      )
    }
  }
}

/** Centered overlay with a text field for naming/renaming a folder. */
@Composable
private fun NameOverlay(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
  val noRipple = remember { MutableInteractionSource() }
  // Pre-select the whole name so the first keystroke replaces it (iOS-style).
  var field by remember {
    mutableStateOf(
        androidx.compose.ui.text.input.TextFieldValue(
            initial,
            selection = androidx.compose.ui.text.TextRange(0, initial.length),
        ))
  }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
          Modifier.fillMaxSize()
              .background(Color(0xCC000000))
              .clickable(interactionSource = noRipple, indication = null) { onCancel() },
  ) {
    Surface(
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(24.dp),
        modifier =
            Modifier.padding(top = 70.dp)
                .width(440.dp)
                .clickable(interactionSource = noRipple, indication = null) {},
    ) {
      Column(modifier = Modifier.padding(24.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.size(16.dp))
        BasicTextField(
            value = field,
            onValueChange = { field = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
            cursorBrush = SolidColor(Color.White),
            modifier =
                Modifier.fillMaxWidth()
                    .focusRequester(focus)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
        )
        Spacer(Modifier.size(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          Text(
              "Cancel",
              color = Color(0xFF8AB4F8),
              fontSize = 18.sp,
              modifier = Modifier.clickable { onCancel() }.padding(12.dp),
          )
          Spacer(Modifier.size(8.dp))
          Text(
              confirmLabel,
              color = Color(0xFF8AB4F8),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.clickable { onConfirm(field.text) }.padding(12.dp),
          )
        }
      }
    }
  }
}

/** Always-present Updates tile. Neutral + refresh glyph when up to date; blue +
 * download glyph (with a badge) when an update is ready. Shows transient status
 * text during a check or install. */
@Composable
private fun UpdatesTile(update: UpdateInfo?, status: String?, onClick: () -> Unit) {
  val available = update != null
  val label = status ?: if (available) "Update ready" else "Up to date"
  val background = if (available) Color(0xFF2D6CDF) else Color(0xFF2B2B2B)
  val glyph = if (available) ICON_DOWNLOAD else ICON_REFRESH
  val path = remember(glyph) { PathParser().parsePathString(glyph).toPath() }
  val tileDp = LocalTileDp.current
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(4.dp).tvFocusable(RoundedCornerShape(22.dp)) { onClick() },
  ) {
    Box {
      Surface(
          color = background,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.size(tileDp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Canvas(Modifier.size(44.dp * (tileDp / 88.dp))) {
            val s = size.minDimension / 24f
            scale(s, s, pivot = Offset.Zero) { drawPath(path, Color.White) }
          }
        }
      }
      if (available) {
        Surface(
            color = Color(0xFFE53935),
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(18.dp).align(Alignment.TopEnd),
        ) {}
      }
    }
    Spacer(Modifier.size(8.dp))
    Text(
        label,
        color = Color.White,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun StoreTile(onClick: () -> Unit) {
  BuiltInTile(
      label = "App Store",
      background = Color(0xFF2D6CDF),
      glyph = ICON_DOWNLOAD,
      onClick = onClick,
  )
}

@Composable
private fun AppTile(
    app: AppEntry,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onDelete: () -> Unit = {},
    onHide: () -> Unit = {},
    onAppInfo: () -> Unit = {},
    onClick: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      // In Manage mode the body tap is inert (drag to fold, ✕ to remove); the
      // icon launches normally otherwise. Long-press opens a quick-action menu.
      modifier =
          modifier.padding(4.dp).tvFocusable(
              RoundedCornerShape(22.dp),
              enabled = !editMode,
              onLongClick = { menuOpen = true },
          ) {
            onClick()
          },
  ) {
    Box {
      androidx.compose.material3.DropdownMenu(
          expanded = menuOpen,
          onDismissRequest = { menuOpen = false },
      ) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Open") },
            onClick = { menuOpen = false; onClick() },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("App info") },
            onClick = { menuOpen = false; onAppInfo() },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Hide from home") },
            onClick = { menuOpen = false; onHide() },
        )
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Uninstall") },
            onClick = { menuOpen = false; onDelete() },
        )
      }
      Image(
          bitmap = app.icon,
          contentDescription = app.label,
          modifier =
              Modifier.size(LocalTileDp.current)
                  .clip(RoundedCornerShape(20.dp))
                  .alpha(if (dimmed) 0.3f else 1f),
      )
      if (editMode) {
        // Hide (blue, top-start): removes from grid but keeps installed.
        Surface(
            color = Color(0xFF1565C0),
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(30.dp).align(Alignment.TopStart).clickable { onHide() },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(16.dp)) {
              val w = size.minDimension; val s = w * 0.12f
              val stroke = Stroke(width = s, cap = StrokeCap.Round)
              drawOval(Color.White, topLeft = Offset(w*0.04f, w*0.32f), size = Size(w*0.92f, w*0.36f), style = stroke)
              drawCircle(Color.White, radius = w*0.12f, center = Offset(w*0.5f, w*0.5f), style = stroke)
              drawLine(Color.White, Offset(w*0.08f, w*0.08f), Offset(w*0.92f, w*0.92f), strokeWidth = s, cap = StrokeCap.Round)
            }
          }
        }
        // Uninstall (red, top-end): opens system uninstall dialog.
        Surface(
            color = Color(0xFFE53935),
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(30.dp).align(Alignment.TopEnd).clickable { onDelete() },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontSize = 18.sp)
          }
        }
      }
    }
    Spacer(Modifier.size(8.dp))
    Text(
        app.label,
        color = Color.White,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
  }
}

/** Open the system "App info" screen for [pkg] (from the long-press menu). */
private fun openAppInfo(context: Context, pkg: String) {
  runCatching {
    val intent =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }
}

// --- data ---------------------------------------------------------------------

private fun loadApps(context: Context): List<AppEntry> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm.queryIntentActivities(intent, 0)
      .filter {
        val pkg = it.activityInfo.packageName
        pkg != context.packageName && !Curation.isHidden(pkg, "")
      }
      .mapNotNull { ri ->
        runCatching {
              val ai = ri.activityInfo
              val rawLabel = ri.loadLabel(pm).toString()
              val bmp: Bitmap = ri.loadIcon(pm).toBitmap(144, 144)
              AppEntry(
                  label = Curation.displayLabel(ai.packageName, rawLabel),
                  component = ComponentName(ai.packageName, ai.name),
                  icon = bmp.asImageBitmap(),
                  folder = Curation.folderFor(ai.packageName),
              ) to rawLabel
            }
            .getOrNull()
      }
      // Label-based hide (catches gated apps that share a package).
      .filterNot { (_, raw) -> raw in Curation.hiddenLabels }
      .map { it.first }
      // One tile per package — some apps (e.g. Portal Settings) expose a second
      // debug/launcher activity that would otherwise show as a duplicate.
      .distinctBy { it.component.packageName }
      .sortedBy { it.label.lowercase(Locale.getDefault()) }
      .let { discovered ->
        // Inject Portal built-ins (no LAUNCHER filter, but user-facing).
        val extras = Curation.portalBuiltins.mapNotNull { (label, component, folder) ->
          runCatching {
            pm.getPackageInfo(component.packageName, 0) // throws if not installed
            val icon = pm.getApplicationIcon(component.packageName).toBitmap(144, 144).asImageBitmap()
            AppEntry(label = label, component = component, icon = icon, folder = folder)
          }.getOrNull()
        }
        // Skip any that the main scan already found (shouldn't happen, but guard it).
        val existing = discovered.map { it.component.packageName }.toSet()
        discovered + extras.filter { it.component.packageName !in existing }
      }
}

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

