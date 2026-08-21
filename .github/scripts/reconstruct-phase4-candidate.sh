#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase4-source}"
source_root="${workspace}/${target}"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "::error::${label} digest mismatch: ${actual}"
    return 1
  fi
  printf '%s' "$actual"
}

apply_patch_hotfix() {
  local number="$1" encoded_sha="$2" patch_sha_expected="$3" result_var="$4"
  local encoded="${workspace}/phase4-remediation/phase4-hotfix-${number}.patch.xz.b64"
  local patch="${workspace}/.phase4-hotfix-${number}.patch"
  local patch_sha
  verify_sha "$encoded" "$encoded_sha" "Phase-4 hotfix-${number} encoded" >/dev/null
  base64 --decode "$encoded" | xz --decompress > "$patch"
  patch_sha="$(verify_sha "$patch" "$patch_sha_expected" "Phase-4 hotfix-${number}")"
  git -C "$workspace" apply --check --directory="$target" "$patch"
  git -C "$workspace" apply --directory="$target" "$patch"
  printf -v "$result_var" '%s' "$patch_sha"
  echo "PHASE4_HOTFIX_${number}=APPLIED"
}

apply_python_hotfix() {
  local number="$1" expected_sha="$2" result_var="$3"
  local script="${workspace}/phase4-remediation/phase4-hotfix-${number}.py"
  local script_sha
  script_sha="$(verify_sha "$script" "$expected_sha" "Phase-4 hotfix-${number}")"
  python3 "$script" "$source_root"
  printf -v "$result_var" '%s' "$script_sha"
}

bash "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "$target"
apply_patch_hotfix 01 "1a5513b2b1588ee725b5ef53dcf458c0bfb0a641d1a30b853ea275546d631db1" "74e9ba4a6c9ee73149bfe50e8b7bc2eaf58b960c70d7e23fe2d421b0fece7bd4" hotfix1_sha
apply_patch_hotfix 02 "526a9625be1185d58a0c23a470bba4c3d3195703a0d5337109ffbc698c63f3ca" "6524a6e0abe24ab87875b614defdb8d2ce8aa93ad1e548570508da23a54ca8bd" hotfix2_sha
apply_python_hotfix 03 "0c4e5a6bd81f9aa1b72fbf0fa8063882f4508f21f65fbd4829ab38d5e083c064" hotfix3_sha

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
