@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
call scripts\windows-env.bat

set "TOOLS_REV=15859902"
set "TOOLS_ZIP=commandlinetools-win-%TOOLS_REV%_latest.zip"
set "TOOLS_URL=https://dl.google.com/android/repository/%TOOLS_ZIP%"
set "TOOLS_SHA256=90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a"
set "DOWNLOAD_DIR=%CD%\.downloads"
set "ZIP_PATH=%DOWNLOAD_DIR%\%TOOLS_ZIP%"

if not defined JAVA_HOME (
  echo [ERROR] JDK 17 was not found.
  echo Install Temurin JDK 17 or Android Studio Embedded JDK, then run this file again.
  pause
  exit /b 10
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JAVA_HOME is invalid: %JAVA_HOME%
  pause
  exit /b 11
)

echo Java:
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 exit /b 12

if not exist "%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat" (
  echo Downloading Android command-line tools...
  if not exist "%DOWNLOAD_DIR%" mkdir "%DOWNLOAD_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%TOOLS_URL%' -OutFile '%ZIP_PATH%'"
  if errorlevel 1 (
    echo [ERROR] Download failed. Check internet, VPN, firewall, and system date.
    pause
    exit /b 20
  )

  for /f "tokens=*" %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%ZIP_PATH%').Hash.ToLower()"') do set "ACTUAL_SHA=%%H"
  if /I not "!ACTUAL_SHA!"=="%TOOLS_SHA256%" (
    echo [ERROR] SHA-256 mismatch.
    echo Expected: %TOOLS_SHA256%
    echo Actual:   !ACTUAL_SHA!
    del /q "%ZIP_PATH%" >nul 2>&1
    pause
    exit /b 21
  )

  set "TEMP_EXTRACT=%DOWNLOAD_DIR%\cmdline-temp"
  rmdir /s /q "!TEMP_EXTRACT!" >nul 2>&1
  mkdir "!TEMP_EXTRACT!"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_PATH%' -DestinationPath '!TEMP_EXTRACT!' -Force"
  if errorlevel 1 exit /b 22

  mkdir "%ANDROID_SDK_ROOT%\cmdline-tools\latest" >nul 2>&1
  xcopy /e /i /y "!TEMP_EXTRACT!\cmdline-tools\*" "%ANDROID_SDK_ROOT%\cmdline-tools\latest\" >nul
  rmdir /s /q "!TEMP_EXTRACT!"
)

set "SDKMANAGER=%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKMANAGER%" (
  echo [ERROR] sdkmanager was not installed correctly.
  pause
  exit /b 23
)

echo.
echo Installing Android SDK packages...
call "%SDKMANAGER%" --sdk_root="%ANDROID_SDK_ROOT%" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
if errorlevel 1 (
  echo [ERROR] SDK package installation failed.
  pause
  exit /b 30
)

echo.
echo Accepting Android SDK licenses...
(for /l %%N in (1,1,50) do @echo y) | call "%SDKMANAGER%" --sdk_root="%ANDROID_SDK_ROOT%" --licenses >nul

> local.properties echo sdk.dir=%ANDROID_SDK_ROOT:\=\\%

echo.
echo [OK] Android SDK is ready at:
echo %ANDROID_SDK_ROOT%
echo.
echo Next: double-click build-debug-apk.bat
pause
