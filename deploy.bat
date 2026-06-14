@echo off
cd /d "%~dp0"
echo Building Immortal debug APK...
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)
echo.
echo Installing via hzdb...
npx -y @meta-quest/hzdb app install app\build\outputs\apk\debug\app-debug.apk
echo.
echo Launching...
npx -y @meta-quest/hzdb app launch com.immortal.launcher
echo.
echo Done. You can close this window.
pause
