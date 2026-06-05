#!/usr/bin/env bash
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# Licensed under the MIT license found in the LICENSE file in the repo root.
#
# One-shot Portal provisioner. Finds (or downloads) ADB, waits for a connected
# Portal, then: installs the client app, pushes photo assets, pre-grants
# permissions, disables Meta's package verifier, and sets the custom launcher
# and screensaver. Run with --restore to undo everything.
#
# Usage:
#   ./provision.sh            provision the connected Portal
#   ./provision.sh --restore  put the stock launcher/screensaver/verifier back
#   ./provision.sh --status   show what's currently set

set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ----- pretty output ---------------------------------------------------------
if [ -t 1 ]; then B=$'\033[1m'; G=$'\033[32m'; Y=$'\033[33m'; R=$'\033[31m'; D=$'\033[2m'; N=$'\033[0m'; else B=; G=; Y=; R=; D=; N=; fi
step() { printf "%s==>%s %s\n" "$B" "$N" "$1"; }
ok()   { printf "  %s✓%s %s\n" "$G" "$N" "$1"; }
warn() { printf "  %s!%s %s\n" "$Y" "$N" "$1"; }
die()  { printf "%sERROR:%s %s\n" "$R" "$N" "$1" >&2; exit 1; }

# ----- load config -----------------------------------------------------------
[ -f config.env ] || die "config.env not found next to this script."
# shellcheck disable=SC1091
set -a; . ./config.env; set +a

# Per-device snapshot of the ORIGINAL stock launcher/screensaver, written on the
# device itself the first time we provision. Restore reads this so it works on
# any Portal model (Go/Mini/Plus/TV/Gen-1) and from any computer — the hardcoded
# STOCK_* in config.env are only fallbacks if this snapshot is missing.
STATE_FILE=/sdcard/immortal_restore.env

# ----- resolve adb (bundled -> PATH -> download) -----------------------------
resolve_adb() {
  if [ -x "$SCRIPT_DIR/platform-tools/adb" ]; then ADB="$SCRIPT_DIR/platform-tools/adb"; return; fi
  if command -v adb >/dev/null 2>&1; then ADB="$(command -v adb)"; return; fi
  step "Android platform-tools (adb) not found — downloading the official package from Google"
  local os zip url
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux)  os=linux ;;
    *) die "Unsupported OS for auto-download. Install Android platform-tools and re-run." ;;
  esac
  url="https://dl.google.com/android/repository/platform-tools-latest-${os}.zip"
  zip="$SCRIPT_DIR/platform-tools.zip"
  curl -fL "$url" -o "$zip" || die "Download failed. Check your internet connection."
  unzip -oq "$zip" -d "$SCRIPT_DIR" || die "Could not unzip platform-tools."
  rm -f "$zip"
  [ -x "$SCRIPT_DIR/platform-tools/adb" ] || die "adb missing after download."
  ADB="$SCRIPT_DIR/platform-tools/adb"
  ok "platform-tools installed locally"
}

a() { "$ADB" "$@"; }

# ----- wait for an authorized device -----------------------------------------
wait_for_device() {
  step "Looking for your Portal"
  a start-server >/dev/null 2>&1
  local printed_plug=0 printed_auth=0 printed_adb=0
  while true; do
    local line state
    line="$(a devices | awk 'NR>1 && NF{print; exit}')"
    state="$(printf "%s" "$line" | awk '{print $2}')"
    case "$state" in
      device)
        local model; model="$(a shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
        ok "Connected: ${model:-device}"
        case "$model" in
          *Portal*) : ;;
          "" ) : ;;
          *) warn "This doesn't look like a Portal (model: $model). Continuing anyway." ;;
        esac
        return ;;
      unauthorized)
        if [ "$printed_auth" = 0 ]; then
          printf "  %sOn the Portal screen, tap %sAllow%s (check \"Always allow from this computer\").%s\n" "$Y" "$B" "$N$Y" "$N"
          printed_auth=1
        fi ;;
      "")
        if [ "$printed_plug" = 0 ]; then
          printf "  %sPlug the Portal into this computer with a USB-C cable.%s\n" "$Y" "$N"
          printf "  %sOn the Portal: Settings > Debug > ADB Enabled.%s\n" "$D" "$N"
          printed_plug=1
        fi ;;
      *)
        if [ "$printed_adb" = 0 ]; then warn "Device state: $state — waiting…"; printed_adb=1; fi ;;
    esac
    sleep 2
  done
}

# ----- individual actions ----------------------------------------------------
install_client() {
  local apk
  apk="$(ls $APK_GLOB 2>/dev/null | head -1)"
  if [ -z "$apk" ] && [ -n "${RELEASE_APK_URL:-}" ]; then
    step "No local APK — downloading the latest Immortal release"
    mkdir -p "$(dirname "$APK_GLOB")"
    apk="$(dirname "$APK_GLOB")/immortal.apk"
    curl -fL "$RELEASE_APK_URL" -o "$apk" || die "Could not download the release APK. Check your connection."
    ok "Downloaded $(basename "$apk")"
  fi
  [ -n "$apk" ] || die "No client APK found matching '$APK_GLOB'. Drop your signed APK in apks/."
  step "Installing client app ($(basename "$apk"))"
  a install -r "$apk" >/dev/null 2>&1 && ok "Installed $PKG" || die "Install failed."
}

install_apps() {
  # Silent adb-install of the configured apps. Works on every model — the only
  # reliable path on devices whose on-device installer dialog is broken.
  local tmp="$(dirname "$APK_GLOB")"; mkdir -p "$tmp"
  for spec in ${PREINSTALL_FDROID:-}; do
    local id="${spec%%:*}" vc=""
    case "$spec" in *:*) vc="${spec##*:}" ;; esac
    if [ -z "$vc" ]; then
      vc="$(curl -fsL "https://f-droid.org/api/v1/packages/$id" 2>/dev/null | grep -o '"suggestedVersionCode":[0-9]*' | grep -o '[0-9]*' | head -1)"
    fi
    [ -n "$vc" ] || { warn "Skipping $id (couldn't resolve a version)"; continue; }
    step "Installing $id"
    if curl -fsL "https://f-droid.org/repo/${id}_${vc}.apk" -o "$tmp/$id.apk" 2>/dev/null \
       && a install -r "$tmp/$id.apk" >/dev/null 2>&1; then ok "$id installed"; else warn "$id failed"; fi
    rm -f "$tmp/$id.apk"
  done
  for url in ${PREINSTALL_APKS:-}; do
    local f="$tmp/$(basename "$url")"
    step "Installing $(basename "$url")"
    if curl -fsL "$url" -o "$f" 2>/dev/null && a install -r "$f" >/dev/null 2>&1; then ok "installed"; else warn "failed"; fi
    rm -f "$f"
  done
}

push_assets() {
  [ -d "$ASSET_DIR" ] || { warn "No assets/ folder — skipping photos"; return; }
  local dir="/sdcard/Android/data/$PKG/files"
  a shell mkdir -p "$dir" >/dev/null 2>&1
  local first=1 n=0
  for img in "$ASSET_DIR"/*.jpg "$ASSET_DIR"/*.jpeg "$ASSET_DIR"/*.png; do
    [ -e "$img" ] || continue
    if [ "$first" = 1 ]; then a push "$img" "$dir/frame.jpg" >/dev/null 2>&1; first=0; else a push "$img" "$dir/" >/dev/null 2>&1; fi
    n=$((n+1))
  done
  [ "$n" -gt 0 ] && ok "Pushed $n image(s) to the photo frame" || warn "No images in assets/"
}

grant_perms() {
  step "Granting permissions"
  for p in $PERMISSIONS; do a shell pm grant "$PKG" "$p" >/dev/null 2>&1; done
  # Self-healing: lets Immortal reaffirm its screensaver settings if reset.
  a shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1
  ok "Permissions granted"
}

disable_verifier() {
  [ "${DISABLE_VERIFIER:-true}" = true ] || return
  step "Disabling Meta's install verifier (lets the client install other apps on-device)"
  a shell pm disable-user --user 0 "$VERIFIER_PKG" >/dev/null 2>&1
  a shell settings put global package_verifier_enable 0 >/dev/null 2>&1
  ok "Verifier disabled"
}

disable_ota() {
  [ "${DISABLE_OTA:-false}" = true ] || return
  step "Disabling Meta OS updates (so a future OTA can't undo this setup)"
  for p in $OTA_PACKAGES; do a shell pm disable-user --user 0 "$p" >/dev/null 2>&1; done
  ok "OS updates disabled"
}

snapshot_stock() {
  # Record this device's real stock components for an accurate restore. Done
  # once per device; a re-provision keeps the original values.
  if a shell "[ -f $STATE_FILE ]" >/dev/null 2>&1; then return; fi
  local home dream ddream
  # The stock home is whichever HOME activity isn't ours, the system resolver, or
  # Settings' fallback. query-activities lists them all as flattened components,
  # so this is reliable across Portal models even with our launcher installed.
  home="$(a shell 'cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null \
            | tr -d '\r' \
            | grep -E '^[A-Za-z0-9_.]+/' \
            | grep -v "^$PKG/" \
            | grep -v '^android/' \
            | grep -v '^com.android.settings/' \
            | head -1)"
  # Screensaver: the live setting is the stock dream on a first provision (we
  # haven't overwritten it yet); guard against capturing ours on a re-run.
  dream="$(a shell settings get secure screensaver_components 2>/dev/null | tr -d '\r')"
  ddream="$(a shell settings get secure screensaver_default_component 2>/dev/null | tr -d '\r')"
  [ -n "$home" ] || home="$STOCK_HOME"
  case "$dream" in "$PKG"/*|""|null) dream="$STOCK_DREAM" ;; esac
  case "$ddream" in "$PKG"/*|""|null) ddream="$STOCK_DEFAULT_DREAM" ;; esac
  printf 'STOCK_HOME=%s\nSTOCK_DREAM=%s\nSTOCK_DEFAULT_DREAM=%s\n' "$home" "$dream" "$ddream" \
    | a shell "cat > $STATE_FILE" 2>/dev/null
  ok "Saved this device's stock launcher/screensaver for restore"
}

load_state() {
  # Pull the per-device snapshot (if any) over the config fallbacks.
  a shell "[ -f $STATE_FILE ]" >/dev/null 2>&1 || { warn "No saved snapshot on device — using config.env fallbacks for restore"; return; }
  local key val
  while IFS='=' read -r key val; do
    val="$(printf '%s' "$val" | tr -d '\r')"
    case "$key" in
      STOCK_HOME) [ -n "$val" ] && STOCK_HOME="$val" ;;
      STOCK_DREAM) [ -n "$val" ] && STOCK_DREAM="$val" ;;
      STOCK_DEFAULT_DREAM) [ -n "$val" ] && STOCK_DEFAULT_DREAM="$val" ;;
    esac
  done < <(a shell cat "$STATE_FILE" 2>/dev/null)
}

set_launcher() {
  [ "${SET_LAUNCHER:-true}" = true ] || return
  step "Setting custom home launcher"
  a shell cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null 2>&1 && ok "Home = $HOME_ACTIVITY" || warn "Could not set launcher"
}

set_screensaver() {
  [ "${SET_SCREENSAVER:-true}" = true ] || return
  step "Setting custom screensaver (active + dock/idle default)"
  # Active component (what Settings shows) AND the dock/idle default — Portal
  # uses the latter for its docked photo-frame path, so set both.
  a shell settings put secure screensaver_components "$DREAM_SERVICE" >/dev/null 2>&1
  a shell settings put secure screensaver_default_component "$DREAM_SERVICE" >/dev/null 2>&1
  a shell settings put secure screensaver_enabled 1 >/dev/null 2>&1
  a shell settings put secure screensaver_activate_on_dock 1 >/dev/null 2>&1
  a shell settings put secure screensaver_activate_on_sleep 1 >/dev/null 2>&1
  ok "Screensaver = $DREAM_SERVICE"
}

# ----- modes -----------------------------------------------------------------
do_provision() {
  printf "%sPortal Provisioner%s\n" "$B" "$N"
  printf "%sThis will modify your Portal: install an app, replace the home screen and screensaver,\nand disable Meta's app-install verifier. Run with --restore to undo. %s\n\n" "$D" "$N"
  resolve_adb
  wait_for_device
  install_client
  install_apps
  push_assets
  grant_perms
  disable_verifier
  disable_ota
  snapshot_stock
  set_launcher
  set_screensaver
  a shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  printf "\n%s✓ Done. Your Portal is provisioned.%s\n" "$G$B" "$N"
  printf "%sTo undo everything: re-run and choose restore (./provision.sh --restore).%s\n" "$D" "$N"
}

do_restore() {
  printf "%sPortal Restore%s\n\n" "$B" "$N"
  resolve_adb
  wait_for_device
  load_state # pull this device's real stock components (falls back to config)
  step "Re-enabling Meta's install verifier"
  a shell pm enable "$VERIFIER_PKG" >/dev/null 2>&1
  a shell settings put global package_verifier_enable 1 >/dev/null 2>&1; ok "Verifier restored"
  step "Re-enabling Meta OS updates"
  for p in $OTA_PACKAGES; do a shell pm enable "$p" >/dev/null 2>&1; done; ok "OS updates restored"
  step "Restoring stock launcher"
  a shell cmd package set-home-activity "$STOCK_HOME" >/dev/null 2>&1; ok "Home restored ($STOCK_HOME)"
  step "Restoring stock screensaver"
  a shell settings put secure screensaver_components "$STOCK_DREAM" >/dev/null 2>&1
  a shell settings put secure screensaver_default_component "$STOCK_DEFAULT_DREAM" >/dev/null 2>&1
  ok "Screensaver restored"
  a shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  printf "\n%s✓ Stock Portal settings restored.%s\n" "$G$B" "$N"
  printf "%sThe client app is left installed — uninstall with: adb uninstall %s%s\n" "$D" "$PKG" "$N"
}

do_status() {
  resolve_adb; wait_for_device
  step "Current state"
  printf "  home:       %s\n" "$(a shell 'cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | awk -F= '/packageName/{print $2; exit}' | tr -d '\r')"
  printf "  screensaver:%s\n" " $(a shell settings get secure screensaver_components 2>/dev/null | tr -d '\r')"
  printf "  verifier:   %s\n" "$(a shell pm list packages -d "$VERIFIER_PKG" 2>/dev/null | tr -d '\r' | grep -q . && echo disabled || echo enabled)"
  printf "  OS updates: %s\n" "$(a shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -q 'alohaotasetup' && echo disabled || echo enabled)"
  printf "  client:     %s\n" "$(a shell pm list packages "$PKG" 2>/dev/null | tr -d '\r' | grep -q . && echo installed || echo 'not installed')"
}

case "${1:-}" in
  --restore|-r) do_restore ;;
  --status|-s)  do_status ;;
  --apps|-a)    resolve_adb; wait_for_device; install_apps ;;
  --help|-h)    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//' ;;
  "")           do_provision ;;
  *)            die "Unknown option: $1 (use --restore, --status, or no argument)" ;;
esac
