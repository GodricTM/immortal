/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.immortal.launcher.ImmortalSettings

private fun lightColorSchemeWithAccent(accent: Color) =
    lightColorScheme(
        primary = accent,
        onPrimary = OnMetaBlue,
        primaryContainer = accent.copy(alpha = 0.2f),
        onPrimaryContainer = accent,
        secondary = NeutralGrey,
        onSecondary = OnMetaBlue,
        background = BackgroundLight,
        surface = SurfaceLight,
        onBackground = ContentOnLight,
        onSurface = ContentOnLight,
    )

private fun darkColorSchemeWithAccent(accent: Color) =
    darkColorScheme(
        primary = accent,
        onPrimary = OnMetaBlue,
        primaryContainer = accent.copy(alpha = 0.3f),
        onPrimaryContainer = accent,
        secondary = NeutralGreyDark,
        onSecondary = OnMetaBlue,
        background = BackgroundDark,
        surface = SurfaceDark,
        onBackground = ContentOnDark,
        onSurface = ContentOnDark,
    )

fun accentColorFromString(key: String): Color = when (key) {
  ImmortalSettings.ACCENT_RED -> AccentRed
  ImmortalSettings.ACCENT_GREEN -> AccentGreen
  ImmortalSettings.ACCENT_PURPLE -> AccentPurple
  ImmortalSettings.ACCENT_ORANGE -> AccentOrange
  ImmortalSettings.ACCENT_PINK -> AccentPink
  ImmortalSettings.ACCENT_TEAL -> AccentTeal
  ImmortalSettings.ACCENT_YELLOW -> AccentYellow
  ImmortalSettings.ACCENT_INDIGO -> AccentIndigo
  ImmortalSettings.ACCENT_CYAN -> AccentCyan
  ImmortalSettings.ACCENT_LIME -> AccentLime
  ImmortalSettings.ACCENT_AMBER -> AccentAmber
  ImmortalSettings.ACCENT_DEEP_PURPLE -> AccentDeepPurple
  ImmortalSettings.ACCENT_BROWN -> AccentBrown
  ImmortalSettings.ACCENT_CORAL -> AccentCoral
  ImmortalSettings.ACCENT_MINT -> AccentMint
  else -> AccentBlue
}

@Composable
fun SampleAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disabled: dynamic color overrides all custom colors and appears over-saturated in MR headsets
    dynamicColor: Boolean = false,
    accentKey: String? = null,
    content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val resolvedKey = accentKey ?: ImmortalSettings.load(context).accentColor
  val accent = accentColorFromString(resolvedKey)

  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorSchemeWithAccent(accent)
        else -> lightColorSchemeWithAccent(accent)
      }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
