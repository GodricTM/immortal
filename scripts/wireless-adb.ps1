# wireless-adb.ps1 - re-arm wireless ADB + Shizuku on a gen-1 Portal after a reboot.
#
# Why this exists: gen-1 Portal is Android 9, a "user" build with no root and
# SELinux enforcing. Wireless ADB (adb tcpip) and Shizuku BOTH reset on every
# reboot and cannot auto-start on-device without root - they must be kicked over
# USB once per boot. Run this with the Portal plugged in via USB-C (ADB enabled
# in Portal dev settings); afterwards you can unplug and work wirelessly until the
# next reboot.
#
# Usage:
#   .\scripts\wireless-adb.ps1            # port 5555
#   .\scripts\wireless-adb.ps1 -Port 5037 # custom port
param(
    [int]$Port = 5555
)

$ErrorActionPreference = "Stop"
$SHIZUKU_PKG = "moe.shizuku.privileged.api"

function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; Read-Host "Press Enter to close"; exit 1 }

# --- locate adb (PATH, then the default SDK location) ---
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) { $adb = $sdkAdb }
}
if (-not $adb) { Fail "adb not found on PATH or in %LOCALAPPDATA%\Android\Sdk\platform-tools." }
Write-Host "Using adb: $adb" -ForegroundColor DarkGray

# --- find the USB-connected Portal (serial without a ':' is USB; ':' means TCP) ---
$lines = & $adb devices | Select-Object -Skip 1
$usb = $null
foreach ($l in $lines) {
    if ($l -match '^(\S+)\s+device$') {
        $serial = $Matches[1]
        if ($serial -notmatch ':') { $usb = $serial; break }
    }
}
if (-not $usb) { Fail "No USB-connected device in 'adb devices'. Plug in the Portal via USB-C and make sure ADB is authorized." }
Write-Host "USB device: $usb" -ForegroundColor Green

# --- 1. flip adbd into TCP mode ---
Write-Host "`n[1/4] Enabling wireless ADB on port $Port ..."
& $adb -s $usb tcpip $Port | Out-Host
Start-Sleep -Seconds 2

# --- 2. read the device's wlan0 IP ---
Write-Host "[2/4] Reading wlan0 IP ..."
$ipOut = (& $adb -s $usb shell "ip -f inet addr show wlan0") -join "`n"
$ip = $null
if ($ipOut -match 'inet\s+(\d+\.\d+\.\d+\.\d+)') { $ip = $Matches[1] }
if (-not $ip) { Fail "Could not read wlan0 IP - is the Portal on Wi-Fi?" }
$target = "${ip}:$Port"
Write-Host "Device IP: $target" -ForegroundColor Green

# --- 3. connect wirelessly ---
Write-Host "[3/4] Connecting to $target ..."
& $adb connect $target | Out-Host

# --- 4. restart the Shizuku server via its bundled starter binary ---
Write-Host "[4/4] Starting Shizuku ..."
$apkPath = (& $adb -s $usb shell "pm path $SHIZUKU_PKG") -replace 'package:', ''
$apkPath = ($apkPath | Where-Object { $_ -match 'base.apk' } | Select-Object -First 1).Trim()
if (-not $apkPath) {
    Write-Host "Shizuku not installed - skipping." -ForegroundColor Yellow
} else {
    $apkDir = $apkPath -replace '/base\.apk$', ''
    $started = $false
    foreach ($abi in @('arm64', 'arm', 'arm64-v8a', 'armeabi-v7a')) {
        $starter = "$apkDir/lib/$abi/libshizuku.so"
        $exists = (& $adb -s $usb shell "[ -f '$starter' ] && echo yes").Trim()
        if ($exists -eq 'yes') {
            & $adb -s $usb shell "'$starter'" | Out-Host
            $started = $true
            break
        }
    }
    if (-not $started) {
        Write-Host "Could not find Shizuku starter under $apkDir/lib/*/libshizuku.so" -ForegroundColor Yellow
    } else {
        Start-Sleep -Seconds 2
        $srv = & $adb -s $usb shell "ps -A | grep shizuku_server"
        if ($srv) { Write-Host "Shizuku server is running." -ForegroundColor Green }
        else { Write-Host "Shizuku starter ran but server not detected - check the Shizuku app." -ForegroundColor Yellow }
    }
}

Write-Host "`n=========================================================" -ForegroundColor Cyan
Write-Host " Wireless ADB ready. You can unplug USB now." -ForegroundColor Cyan
Write-Host " Reconnect any time this boot with:" -ForegroundColor Cyan
Write-Host "     adb connect $target" -ForegroundColor White
Write-Host " Re-run this script (over USB) after the next reboot." -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Read-Host "Press Enter to close"
