#!/usr/bin/env bash
set -uo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 ALPHA160_PROJECT_ROOT" >&2
  exit 64
fi

project_root=$1
artifact_dir=${GITHUB_WORKSPACE:?}/runtime-artifacts/clean
app_apk=$project_root/app/build/outputs/apk/debug/app-debug.apk
test_apk=$project_root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
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

instrumentation_passed() {
  local output=$1
  grep -Eq '^OK \([0-9]+ tests?\)$' "$output" \
    && ! grep -q '^FAILURES!!!$' "$output" \
    && ! grep -q 'Process crashed' "$output"
}

if [[ ! -f "$app_apk" || ! -f "$test_apk" ]]; then
  echo "Alpha160 app/test APK is missing" >&2
  exit 66
fi

adb uninstall ir.sabou.inventory.test >/dev/null 2>&1 || true
adb uninstall ir.sabou.inventory >/dev/null 2>&1 || true
adb install "$app_apk" | tee "$artifact_dir/install-app.txt"
adb install "$test_apk" | tee "$artifact_dir/install-test.txt"

adb logcat -c
adb shell am instrument -w -r \
  -e class ir.sabou.inventory.Alpha160CleanEntryRuntimeSmokeTest \
  "$runner" | tee "$artifact_dir/clean-entry-instrumentation.txt"
clean_status=${PIPESTATUS[0]}
capture_diagnostics clean-entry
clean_ok=0
if (( clean_status == 0 )) && instrumentation_passed "$artifact_dir/clean-entry-instrumentation.txt"; then
  clean_ok=1
fi

adb logcat -c
adb shell am instrument -w -r \
  -e class ir.sabou.inventory.data.db.HrPayrollMigration43To44Test \
  "$runner" | tee "$artifact_dir/hr-migration-instrumentation.txt"
hr_migration_status=${PIPESTATUS[0]}
capture_diagnostics hr-migration
hr_migration_ok=0
if (( hr_migration_status == 0 )) && instrumentation_passed "$artifact_dir/hr-migration-instrumentation.txt"; then
  hr_migration_ok=1
fi

adb logcat -c
adb shell am instrument -w -r \
  -e class ir.sabou.inventory.data.db.FullMigration1ToCurrentTest \
  "$runner" | tee "$artifact_dir/full-migration-instrumentation.txt"
full_migration_status=${PIPESTATUS[0]}
capture_diagnostics full-migration
full_migration_ok=0
if (( full_migration_status == 0 )) && instrumentation_passed "$artifact_dir/full-migration-instrumentation.txt"; then
  full_migration_ok=1
fi

if (( clean_ok != 1 || hr_migration_ok != 1 || full_migration_ok != 1 )); then
  echo "Runtime gate failed: clean=$clean_ok hrMigration=$hr_migration_ok fullMigration=$full_migration_ok" >&2
  exit 1
fi

echo "Alpha160 clean-entry and migration runtime gates passed."
