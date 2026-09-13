@echo off
setlocal EnableExtensions

set "PROJECT_ROOT=%~dp0..\"
for %%I in ("%PROJECT_ROOT%") do set "PROJECT_ROOT=%%~fI"

if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%PROJECT_ROOT%\.android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

if not defined JAVA_HOME (
  if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
)
if not defined JAVA_HOME (
  for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%~fJ"
)
if not defined JAVA_HOME (
  for /d %%J in ("%ProgramFiles%\Java\jdk-17*") do set "JAVA_HOME=%%~fJ"
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin;%PATH%"

endlocal & (
  set "PROJECT_ROOT=%PROJECT_ROOT%"
  set "ANDROID_SDK_ROOT=%ANDROID_SDK_ROOT%"
  set "ANDROID_HOME=%ANDROID_HOME%"
  set "JAVA_HOME=%JAVA_HOME%"
  set "PATH=%PATH%"
)
