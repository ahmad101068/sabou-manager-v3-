#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase7-source}"
root="${workspace}/${target}"
patch_b64="${workspace}/.phase7-final.patch.xz.b64"
patch_file="${workspace}/.phase7-final.patch"
expected_patch_sha="4db68c04e22cf306e0b61c8b455869e150c2e29ec89e0d303dbdd4d6b95f6b55"
phase6_handoff_sha="adda2cefa738c29e18a1f6e15d75d5fee136b042"

require_file() {
  local file="$1" label="$2"
  test -s "$file" || { echo "::error::missing ${label}: ${file}"; exit 1; }
}

require_contains() {
  local token="$1" file="$2" label="$3"
  grep -Fq "$token" "$file" || { echo "::error::missing invariant ${label}: ${token}"; exit 1; }
}

# Phase 7 is an additive continuation of the exact deterministic Phase-6 source.
bash "${workspace}/.github/scripts/reconstruct-phase6-candidate.sh" "$target"

chunks=(
  phase7-remediation/phase7-final.patch.xz.b64.00
  phase7-remediation/phase7-final.patch.xz.b64.01
  phase7-remediation/phase7-final.patch.xz.b64.02
  phase7-remediation/phase7-final.patch.xz.b64.03
  phase7-remediation/phase7-final.patch.xz.b64.04
  phase7-remediation/phase7-final.patch.xz.b64.05
)

: > "$patch_b64"
for rel in "${chunks[@]}"; do
  require_file "${workspace}/${rel}" "$rel"
  cat "${workspace}/${rel}" >> "$patch_b64"
done

base64 --decode "$patch_b64" | xz --decompress > "$patch_file"
actual_patch_sha="$(sha256sum "$patch_file" | awk '{print $1}')"
test "$actual_patch_sha" = "$expected_patch_sha" || {
  echo "::error::Phase-7 patch digest mismatch: $actual_patch_sha"
  exit 1
}

git -C "$workspace" apply --check --directory="$target" "$patch_file"
git -C "$workspace" apply --directory="$target" "$patch_file"

# Fail-closed Phase-7 acceptance invariants.
require_contains 'APP_DATABASE_SCHEMA_VERSION = 59' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt" 'Room remains version 59'
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::destructive migration fallback is forbidden'
  exit 1
fi

paging_file="$(find "$root/app/src/main/java" -name 'UiPaging.kt' -print -quit)"
require_file "$paging_file" 'UiPaging.kt'
require_contains 'data class UiPageWindow' "$paging_file" 'paging window model'
require_contains 'fun uiPageWindow' "$paging_file" 'paging window function'

money_test="$(find "$root/app/src/test" -name 'MoneyFormatterTest.kt' -print -quit)"
require_file "$money_test" 'MoneyFormatterTest.kt'
require_contains '۵٬۰۰۰٬۰۰۰ ریال' "$money_test" 'exact Persian Rial formatting test'
require_contains '۳۵.۲۵٪' "$money_test" 'Persian percent formatting test'

paging_test="$(find "$root/app/src/test" -name 'UiPagingTest.kt' -print -quit)"
require_file "$paging_test" 'UiPagingTest.kt'

printer="$(find "$root/app/src/main/java" -name 'ReportPrinter.kt' -print -quit)"
require_file "$printer" 'ReportPrinter.kt'
require_contains 'size: A4 portrait' "$printer" 'A4 CSS print contract'
require_contains 'PrintAttributes.MediaSize.ISO_A4' "$printer" 'Android A4 print media size'

search_test="$(find "$root/app/src/androidTest" -name 'GlobalSearchIntegrationTest.kt' -print -quit)"
require_file "$search_test" 'GlobalSearchIntegrationTest.kt'

if ! grep -R -Fq 'BriefMetricCard("درآمد"' "$root/app/src/main/java"; then
  echo '::error::Persian management daily brief metric not found'
  exit 1
fi
if grep -R -Fq 'BriefMetricCard("Revenue"' "$root/app/src/main/java"; then
  echo '::error::English management daily brief Revenue label remains'
  exit 1
fi

# Guard against the previously identified duplicate report open invocation.
python3 - "$root" <<'PY'
from pathlib import Path
import re, sys
root = Path(sys.argv[1])
for path in root.joinpath('app/src/main/java').rglob('*.kt'):
    text = path.read_text(encoding='utf-8')
    if re.search(r'report\.open\(\)\s*\n\s*report\.open\(\)', text):
        raise SystemExit(f'duplicate report.open() remains in {path}')
PY

echo PHASE7_RECONSTRUCTION=PASS
echo PHASE6_HANDOFF_SHA=$phase6_handoff_sha
echo ROOM_VERSION=59
echo PATCH_SHA256=$expected_patch_sha
