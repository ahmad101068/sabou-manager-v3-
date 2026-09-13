@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call scripts\windows-env.bat

if not defined JAVA_HOME (
  echo [ERROR] JDK 17 not found. Run setup-android-sdk.bat after installing JDK 17.
  pause
  exit /b 10
)
if not exist "%ANDROID_SDK_ROOT%\platforms\android-36\android.jar" (
  echo [ERROR] Android SDK 36 is missing. Run setup-android-sdk.bat first.
  pause
  exit /b 11
)

> local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=\\%

echo Running verification and unit tests...
call gradlew.bat --no-daemon clean testDebugUnitTest assembleDebug
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Read the first error above.
  pause
  exit /b 20
)

set "APK=%CD%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo [ERROR] Build finished but APK was not found.
  pause
  exit /b 21
)

echo.
echo [OK] Debug APK created:
echo %APK%
start "" explorer.exe /select,"%APK%"
pause
