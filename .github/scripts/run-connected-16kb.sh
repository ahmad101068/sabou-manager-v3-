#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="$GITHUB_WORKSPACE/phase3-source/app/build/evidence"
mkdir -p "$EVIDENCE_DIR"

page_size="$(adb shell getconf PAGE_SIZE | tr -d '\r')"
echo "RUNTIME_PAGE_SIZE=$page_size"
printf '%s\n' "$page_size" > "$EVIDENCE_DIR/16kb-page-size.txt"

if [[ "$page_size" != "16384" ]]; then
  adb shell getconf PAGE_SIZE > "$EVIDENCE_DIR/16kb-page-size-raw.txt" 2>&1 || true
  adb shell getprop > "$EVIDENCE_DIR/16kb-getprop.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$EVIDENCE_DIR/16kb-logcat.txt" 2>&1 || true
  exit 1
fi

cd "$GITHUB_WORKSPACE/phase3-source"
run_suite() {
  set +e
  gradle --no-daemon --no-build-cache :app:connectedDebugAndroidTest
  local suite_status=$?
  set -e
  return "$suite_status"
}

status=0
run_suite || status=$?

if [[ "$status" -ne 0 ]]; then
  adb shell pm list instrumentation > "$EVIDENCE_DIR/16kb-instrumentation.txt" 2>&1 || true
  adb shell getprop > "$EVIDENCE_DIR/16kb-getprop.txt" 2>&1 || true
  adb logcat -d -v threadtime > "$EVIDENCE_DIR/16kb-logcat.txt" 2>&1 || true

  if grep -Fq "androidx.test.services was killed, which is usually due to low memory conditions" "$EVIDENCE_DIR/16kb-logcat.txt"; then
    echo "Detected AndroidTestOrchestrator infrastructure crash caused by test-services memory pressure; retrying once."
    adb shell am force-stop androidx.test.orchestrator || true
    adb shell am force-stop androidx.test.services || true
    adb logcat -c || true
    sleep 3

    retry_status=0
    run_suite || retry_status=$?
    status=$retry_status

    if [[ "$status" -ne 0 ]]; then
      adb shell pm list instrumentation > "$EVIDENCE_DIR/16kb-instrumentation-retry.txt" 2>&1 || true
      adb shell getprop > "$EVIDENCE_DIR/16kb-getprop-retry.txt" 2>&1 || true
      adb logcat -d -v threadtime > "$EVIDENCE_DIR/16kb-logcat-retry.txt" 2>&1 || true
    fi
  fi
fi

exit "$status"
