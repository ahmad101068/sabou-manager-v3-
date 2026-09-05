@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call scripts\windows-env.bat

if not defined RESTAURANT_MANAGEMENT_KEYSTORE_PATH goto :missing
if not defined RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD goto :missing
if not defined RESTAURANT_MANAGEMENT_KEY_ALIAS goto :missing
if not defined RESTAURANT_MANAGEMENT_KEY_PASSWORD goto :missing
if not exist "%RESTAURANT_MANAGEMENT_KEYSTORE_PATH%" (
  echo [ERROR] Keystore not found: %RESTAURANT_MANAGEMENT_KEYSTORE_PATH%
  pause
  exit /b 12
)

> local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=\\%
call gradlew.bat --no-daemon clean testReleaseUnitTest lintRelease assembleRelease bundleRelease
if errorlevel 1 (
  echo [ERROR] Release verification/build failed.
  pause
  exit /b 20
)

set "APK=%CD%\app\build\outputs\apk\release\app-release.apk"
set "AAB=%CD%\app\build\outputs\bundle\release\app-release.aab"
if not exist "%APK%" (
  echo [ERROR] Signed release APK was not found.
  pause
  exit /b 21
)
if not exist "%AAB%" (
  echo [ERROR] Signed release AAB was not found.
  pause
  exit /b 22
)

for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%APK%').Hash.ToLower()"') do set "APK_SHA=%%H"
for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%AAB%').Hash.ToLower()"') do set "AAB_SHA=%%H"
for %%F in ("%APK%") do set "APK_SIZE=%%~zF"
for %%F in ("%AAB%") do set "AAB_SIZE=%%~zF"

echo [OK] Signed release artifacts created from a clean build:
echo APK: %APK%
echo APK bytes: %APK_SIZE%
echo APK SHA-256: %APK_SHA%
echo AAB: %AAB%
echo AAB bytes: %AAB_SIZE%
echo AAB SHA-256: %AAB_SHA%
start "" explorer.exe /select,"%AAB%"
pause
exit /b 0

:missing
echo [ERROR] Signing variables are missing.
echo.
echo Set these variables in the same Command Prompt before running this file:
echo   set RESTAURANT_MANAGEMENT_KEYSTORE_PATH=C:\secure\restaurant-management-release.jks
echo   set RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD=your_store_password
echo   set RESTAURANT_MANAGEMENT_KEY_ALIAS=restaurant-management
echo   set RESTAURANT_MANAGEMENT_KEY_PASSWORD=your_key_password
echo   call build-release-apk.bat
echo.
echo Do not save passwords inside the project.
pause
exit /b 11
