@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call scripts\windows-env.bat
if not exist "%JAVA_HOME%\bin\keytool.exe" (
  echo [ERROR] JDK 17 keytool was not found.
  pause
  exit /b 10
)
set "KEY_DIR=%USERPROFILE%\CafeRestaurantKeys"
if not exist "%KEY_DIR%" mkdir "%KEY_DIR%"
set "KEY_PATH=%KEY_DIR%\cafe-restaurant-release.jks"
if exist "%KEY_PATH%" (
  echo [ERROR] Keystore already exists: %KEY_PATH%
  echo It was not overwritten.
  pause
  exit /b 11
)
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore "%KEY_PATH%" -alias cafe-restaurant -keyalg RSA -keysize 4096 -validity 18250
if errorlevel 1 exit /b 20
echo.
echo [OK] Keystore created: %KEY_PATH%
echo Keep this file and both passwords in multiple secure backups.
pause
