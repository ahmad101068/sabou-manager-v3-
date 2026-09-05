#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 5 ] || [ "$#" -gt 6 ]; then
  echo "usage: $0 APP_APK TEST_APK PLAN_TSV OUTPUT_DIR LABEL [CAPTURE_PERFORMANCE]" >&2
  exit 64
fi

app_apk="$1"
test_apk="$2"
plan="$3"
out="$4"
label="$5"
capture_performance="${6:-0}"

test -s "$app_apk"
test -s "$test_apk"
test -s "$plan"
mkdir -p "$out"

plan_rows="$(awk -F '\t' 'NF { if (NF != 2 || $2 !~ /^[1-9][0-9]*$/) exit 2; rows++; tests += $2 } END { if (rows == 0) exit 3; print rows }' "$plan")"
plan_expected="$(awk -F '\t' 'NF { tests += $2 } END { print tests + 0 }' "$plan")"
echo "PHASE81_PLAN_INPUT label=$label selectors=$plan_rows expected_tests=$plan_expected"

adb shell pm path android >/dev/null
adb shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true
adb install -r -t "$app_apk" >/dev/null
adb install -r -t "$test_apk" >/dev/null

runner="$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\) (target=ir\.restaurant\.management)$/\1/p' | head -1)"
if [ -z "$runner" ]; then
  echo "No instrumentation runner targeting ir.restaurant.management" >&2
  adb shell pm list instrumentation >&2 || true
  exit 65
fi

index=0
executed_expected=0
exec 3< "$plan"
while IFS=$'\t' read -r selector expected extra <&3; do
  [ -n "${selector:-}" ] || continue
  if [ -n "${extra:-}" ] || ! [[ "$expected" =~ ^[1-9][0-9]*$ ]]; then
    echo "Invalid plan row: selector='$selector' expected='$expected' extra='$extra'" >&2
    exit 66
  fi
  xml="$out/TEST-$(printf '%03d' "$index").xml"
  echo "PHASE81_SELECTOR_START label=$label index=$index expected=$expected selector=$selector"

  selector_passed=0
  for attempt in 1 2; do
    raw="$out/raw-$(printf '%03d' "$index")-attempt-${attempt}.txt"
    rm -f "$xml"
    adb shell am force-stop ir.restaurant.management >/dev/null 2>&1 || true

    set +e
    timeout 15m adb shell am instrument -w -r -e class "$selector" "$runner" </dev/null | tee "$raw"
    adb_rc=${PIPESTATUS[0]}
    set -e

    parser_rc=0
    python3 "$GITHUB_WORKSPACE/.github/scripts/parse-phase81-instrumentation-output.py" \
      "$raw" "$xml" "$label" "$selector" "$expected" || parser_rc=$?

    if [ "$adb_rc" -eq 0 ] && [ "$parser_rc" -eq 0 ]; then
      selector_passed=1
      break
    fi

    infra_crash=0
    if grep -Eq 'Native crash|Process crashed|Instrumentation run failed due to Native crash|INSTRUMENTATION_FAILED' "$raw"; then
      infra_crash=1
    fi
    if [ "$attempt" -lt 2 ] && [ "$infra_crash" -eq 1 ]; then
      echo "PHASE81_SELECTOR_INFRA_RETRY label=$label index=$index attempt=$attempt selector=$selector" >&2
      rm -f "$xml"
      adb shell pm path android >/dev/null
      adb shell am force-stop ir.restaurant.management >/dev/null 2>&1 || true
      adb install -r -t "$app_apk" >/dev/null
      adb install -r -t "$test_apk" >/dev/null
      sleep 2
      continue
    fi

    echo "PHASE81_SELECTOR_FAIL label=$label index=$index attempt=$attempt adb_rc=$adb_rc parser_rc=$parser_rc infra_crash=$infra_crash selector=$selector" >&2
    exit 67
  done

  if [ "$selector_passed" -ne 1 ]; then
    echo "PHASE81_SELECTOR_FAIL label=$label index=$index selector=$selector" >&2
    exit 67
  fi

  if [ "$capture_performance" = "1" ] && [[ "$selector" == ir.restaurant.management.data.repository.Phase81LargeDataPerformanceIntegrationTest* ]]; then
    adb shell run-as ir.restaurant.management cat files/phase81-performance-results.txt \
      | tr -d '\r' | tee "$out/performance-api35.txt"
    grep -Fq 'PHASE81_PERF inventory=10000 customers=50000 operational=100000' "$out/performance-api35.txt"
  fi

  executed_expected=$((executed_expected + expected))
  index=$((index + 1))
  adb shell am force-stop ir.restaurant.management >/dev/null 2>&1 || true
done
exec 3<&-

if [ "$index" -le 0 ]; then
  echo "Instrumentation plan contained no selectors" >&2
  exit 68
fi
if [ "$index" -ne "$plan_rows" ] || [ "$executed_expected" -ne "$plan_expected" ]; then
  echo "PHASE81_PLAN_INCOMPLETE label=$label executed_selectors=$index/$plan_rows executed_tests=$executed_expected/$plan_expected" >&2
  exit 69
fi
printf '%s\n' "$executed_expected" > "$out/expected-test-count.txt"
echo "PHASE81_PLAN_PASS label=$label selectors=$index expected_tests=$executed_expected"
