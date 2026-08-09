#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 ALPHA159_PROJECT_ROOT ALPHA160_PROJECT_ROOT" >&2
  exit 64
fi

alpha159_root=$1
alpha160_root=$2
artifact_dir=${GITHUB_WORKSPACE:?}/runtime-artifacts/upgrade
alpha159_app=$alpha159_root/app/build/outputs/apk/debug/app-debug.apk
alpha159_test=$alpha159_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
alpha160_app=$alpha160_root/app/build/outputs/apk/debug/app-debug.apk
alpha160_test=$alpha160_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
runner=ir.sabou.inventory.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$artifact_dir"

capture_diagnostics() {
  local label=$1
  adb logcat -d -v threadtime > "$artifact_dir/${label}-full-logcat.txt" 2>&1 || true
  adb logcat -d -v threadtime AndroidRuntime:E ActivityManager:E SQLiteLog:E SQLiteDatabase:E Room:E '*:S' \
    > "$artifact_dir/${label}-crash-logcat.txt" 2>&1 || true
  adb shell pidof ir.sabou.inventory > "$artifact_dir/${label}-pid.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$artifact_dir/${label}-activities.txt" 2>&1 || true
  adb exec-out screencap -p > "$artifact_dir/${label}-screen.png" 2>/dev/null || true
}

for apk in "$alpha159_app" "$alpha159_test" "$alpha160_app" "$alpha160_test"; do
  if [[ ! -f "$apk" ]]; then
    echo "Required APK is missing: $apk" >&2
    exit 66
  fi
done

adb uninstall ir.sabou.inventory.test >/dev/null 2>&1 || true
adb uninstall ir.sabou.inventory >/dev/null 2>&1 || true
adb install "$alpha159_app" | tee "$artifact_dir/install-alpha159-app.txt"
adb install "$alpha159_test" | tee "$artifact_dir/install-alpha159-test.txt"

adb logcat -c
adb shell am instrument -w -r \
  -e class ir.sabou.inventory.Alpha159UpgradeSeedTest \
  "$runner" | tee "$artifact_dir/alpha159-seed-instrumentation.txt"
seed_status=${PIPESTATUS[0]}
capture_diagnostics alpha159-seed

adb shell am force-stop ir.sabou.inventory || true
adb install -r "$alpha160_app" | tee "$artifact_dir/upgrade-alpha160-app.txt"
adb install -r "$alpha160_test" | tee "$artifact_dir/upgrade-alpha160-test.txt"

adb logcat -c
adb shell am instrument -w -r \
  -e class ir.sabou.inventory.Alpha160UpgradeEntryRuntimeSmokeTest \
  "$runner" | tee "$artifact_dir/alpha160-upgrade-instrumentation.txt"
upgrade_status=${PIPESTATUS[0]}
capture_diagnostics alpha160-upgrade

if (( seed_status != 0 || upgrade_status != 0 )); then
  echo "Upgrade gate failed: alpha159Seed=$seed_status alpha160Upgrade=$upgrade_status" >&2
  exit 1
fi

echo "Alpha159 -> Alpha160 production database upgrade runtime gate passed."
