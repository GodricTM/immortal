# Customization Guide

This guide covers how to extend and customize the features added in this
fork. It's aimed at developers who want to add their own clock styles,
fonts, colors, or settings.

## Adding a new accent color

1. Open `Color.kt` and add a new `Color(0xFF...)` constant
2. Open `ImmortalSettings.kt` and add a new `const val ACCENT_X = "x"`
3. Update `Theme.kt`'s `accentColorFromString()` to handle the new key
4. Update `ImmortalSettings.kt`'s `Settings` data class default and
   `load()` if you want a non-blue default
5. The color automatically appears in the settings UI via the
   `AccentColorGrid` composable — no UI changes needed

## Adding a new clock style

1. Add a new `const val STYLE_X = "x"` to `DigitalClockConfig.kt`
2. In `DigitalClockView.kt`:
   - Add a case in `applyClockStyleEffects()` if the style needs special
     effects (shadow, bold, etc.)
   - Add a case in `clockTypefaceForStyle()` for the typeface
   - If the style is a completely different layout (like Analog or
     Flip), add a branch in the `build()` function to render it
     differently — see `STYLE_ANALOG` and `STYLE_FLIP` for examples
3. Add the option to the style picker in `ClockSettingsActivity.kt`

## Adding a new bundled font

1. Download or create a `.ttf` file
2. Drop it into `app/src/main/assets/fonts/`
3. Add a new `const val FONT_X = "x"` to `DigitalClockConfig.kt`
4. Add a new case in `DigitalClockView.clockTypeface()` to load it via
   `loadBundledFont(context, "fonts/your_font.ttf")`
5. Add the option to the font picker in `ClockSettingsActivity.kt`

The font is only loaded once and cached, so adding many fonts has
minimal runtime cost.

## Adding a new background

1. Add a new `const val BG_X = "x"` to `DigitalClockConfig.kt`
2. Update `DigitalClockView.backgroundColor()` to handle the new mode
3. If the background is an image or gradient, update
   `DigitalClockView.build()` to render it
4. Add the option to the background picker in `ClockSettingsActivity.kt`

## Adding a new setting

1. Add the field to the `Settings` data class in the appropriate
   `Config.kt` file (`ImmortalSettings`, `DigitalClockConfig`, or
   `ScreensaverConfig`)
2. Add the key name to the `load()` function
3. Add a `setX()` setter function
4. Add UI in the appropriate `*SettingsActivity.kt` — the activity
   should read the setting via `load()` and call the setter on change
5. If the setting needs to affect the home screen, update `HomeActivity`
   to read it on resume (see `ImmortalSettings.load(context)` in
   `HomeActivity.onResume`)

## Adding a new clock style that needs its own view

For styles that need more than just a `TextView` (like Analog and Flip),
you'll need to add a custom view. The pattern is:

1. Create the custom `View` class (see `AnalogClockView` in
   `DigitalClockView.kt` for an example)
2. In `DigitalClockView.build()`, instantiate the view, add it to the
   root, and store it in the `Controller`
3. In `DigitalClockView.update()`, call the view's `update()` method
4. In the style branch, configure the view's properties (color, size,
   show seconds, etc.)

## Tweaking the analog clock

The `AnalogClockView` class has all its proportions expressed as
fractions of the view radius. To tweak:

- `unit = radius * 0.015f` — the base unit for all stroke widths and
  text sizes. Increase for a bolder look, decrease for a finer look
- Hand lengths are `radius * 0.55f` (hour) and `radius * 0.82f` (minute)
- Number position is `radius * 0.78f` from center
- Marker lengths: cardinals extend `unit * 14` inward, others `unit * 8`
- Center dot: outer radius `unit * 5`, inner cutout `unit * 1.8`
