@echo off
REM Double-click to re-arm wireless ADB + Shizuku on the Portal (plug in USB first).
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\wireless-adb.ps1" %*
