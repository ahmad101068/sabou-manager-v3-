#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase4-source}"
source_root="${workspace}/${target}"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file"
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "::error::${label} digest mismatch: ${actual}"
    exit 1
  fi
  printf '%s' "$actual"
}

apply_hotfix() {
  local number="$1" encoded_sha="$2" patch_sha_expected="$3"
  local encoded="${workspace}/phase4-remediation/phase4-hotfix-${number}.patch.xz.b64"
  local patch="${workspace}/.phase4-hotfix-${number}.patch"
  verify_sha "$encoded" "$encoded_sha" "Phase-4 hotfix-${number} encoded" >/dev/null
  base64 --decode "$encoded" | xz --decompress > "$patch"
  local patch_sha
  patch_sha="$(verify_sha "$patch" "$patch_sha_expected" "Phase-4 hotfix-${number}")"
  git -C "$workspace" apply --check --directory="$target" "$patch"
  git -C "$workspace" apply --directory="$target" "$patch"
  printf '%s' "$patch_sha"
}

bash "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "$target"
hotfix1_sha="$(apply_hotfix 01 "1a5513b2b1588ee725b5ef53dcf458c0bfb0a641d1a30b853ea275546d631db1" "74e9ba4a6c9ee73149bfe50e8b7bc2eaf58b960c70d7e23fe2d421b0fece7bd4")"
hotfix2_sha="$(apply_hotfix 02 "526a9625be1185d58a0c23a470bba4c3d3195703a0d5337109ffbc698c63f3ca" "6524a6e0abe24ab87875b614defdb8d2ce8aa93ad1e548570508da23a54ca8bd")"
hotfix3_sha="$(apply_hotfix 03 "6ff21318d54c91b7ea8e38bb705d2049daf376556343e914de7736be196f50c5" "c2aef6ea573c281263eb4f3b049220ea1e02f66c7bda57780170444ee0826a17")"

test -s "${source_root}/app/src/main/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCode.kt"
test -s "${source_root}/app/src/main/java/ir/restaurant/management/domain/personnel/AttendanceSessionCalculator.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCodeTest.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/domain/personnel/AttendanceSessionCalculatorTest.kt"
grep -Fq 'PersonnelReferenceCode.newShiftCode()' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
grep -Fq 'PersonnelReferenceCode.newWorkScheduleCode()' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
grep -Fq 'AttendanceSessionCalculator.summarize' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PayrollBatchPreparationService.kt"
if grep -R -n -E 'MAX\([^)]+\)[^\n]*\+[[:space:]]*1' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/HrPayrollDao.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/PersonnelDao.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository" \
  "${source_root}/app/src/main/java/ir/restaurant/management/domain/personnel"; then
  echo '::error::Unsafe MAX()+1 HR/payroll allocation detected'
  exit 1
fi
if grep -n 'val firstIn = events.filter' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PayrollBatchPreparationService.kt"; then
  echo '::error::Payroll still uses first-in/last-out span instead of canonical punch sessions'
  exit 1
fi

echo "PHASE4_RECONSTRUCTION=PASS"
echo "PHASE3_BASELINE_SHA=ea7658058ddabbcd184a3b58c0d0e36c5ede5549"
echo "SCHEMA_CHANGED=NO"
echo "MIGRATION_ADDED=NO"
echo "HOTFIX_01_SHA256=${hotfix1_sha}"
echo "HOTFIX_02_SHA256=${hotfix2_sha}"
echo "HOTFIX_03_SHA256=${hotfix3_sha}"
