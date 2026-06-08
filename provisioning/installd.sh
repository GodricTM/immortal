#!/system/bin/sh
# Immortal install daemon. Runs as the shell user (uid 2000) — started once via
# ADB during provisioning — so it can `pm install` silently, bypassing the broken
# on-device installer dialog on the Gen-1 Portal+. The launcher drops APKs in the
# queue; this installs them and writes a heartbeat so the launcher knows it's alive.
#
# Note: like all non-root helpers (e.g. Shizuku), this does NOT survive a reboot;
# re-run `provision.sh --installd` (or the kit) to restart it.

Q="$1"
[ -n "$Q" ] || Q=/sdcard/Android/data/com.immortal.launcher/files/installq
mkdir -p "$Q" 2>/dev/null

# Per-app post-install grants. We run as the shell user (uid 2000), so we can set
# app-ops an ordinary app can't grant itself. Idempotent — safe to re-run.
post_install_hooks() {
  # Aurora Store installs Play-Store apps via its "Session" installer, which needs
  # REQUEST_INSTALL_PACKAGES to hand the APK off (it then routes through this
  # daemon). Grant it once Aurora is present so installs work without Shizuku.
  # No-op if Aurora isn't installed.
  if pm list packages com.aurora.store 2>/dev/null | grep -q "package:com.aurora.store"; then
    appops set com.aurora.store REQUEST_INSTALL_PACKAGES allow 2>/dev/null
  fi
}

while true; do
  for f in "$Q"/*.apk; do
    [ -e "$f" ] || continue
    # Copy to shell-owned storage first, and CAPTURE pm's output via a pipe.
    # (Redirecting pm's output straight to an /sdcard file makes the package
    # service — running as system — fail to write the FD, so the install errors
    # with "Failed transaction". Capturing then writing the log from the shell
    # avoids handing a /sdcard FD to the system service.)
    t=/data/local/tmp/immortal_install.apk
    cp "$f" "$t" 2>/dev/null
    out="$(pm install -r "$t" 2>&1)"
    rm -f "$t" 2>/dev/null
    echo "$out" > "$f.log" 2>/dev/null
    case "$out" in
      *Success*) mv "$f" "$f.done" 2>/dev/null; post_install_hooks ;;
      *)         mv "$f" "$f.failed" 2>/dev/null ;;
    esac
  done
  date +%s > "$Q/.heartbeat" 2>/dev/null
  sleep 2
done
