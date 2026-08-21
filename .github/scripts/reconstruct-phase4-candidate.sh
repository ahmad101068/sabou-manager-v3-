#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase4-source}"
source_root="${workspace}/${target}"
encoded="${workspace}/phase4-remediation/phase4-hotfix-01.patch.xz.b64"
patch="${workspace}/.phase4-hotfix-01.patch"

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

bash "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "$target"
verify_sha "$encoded" "1a5513b2b1588ee725b5ef53dcf458c0bfb0a641d1a30b853ea275546d631db1" "Phase-4 hotfix-01 encoded" >/dev/null
base64 --decode "$encoded" | xz --decompress > "$patch"
patch_sha="$(verify_sha "$patch" "74e9ba4a6c9ee73149bfe50e8b7bc2eaf58b960c70d7e23fe2d421b0fece7bd4" "Phase-4 hotfix-01")"
git -C "$workspace" apply --check --directory="$target" "$patch"
git -C "$workspace" apply --directory="$target" "$patch"

test -s "${source_root}/app/src/main/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCode.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCodeTest.kt"
grep -Fq 'PersonnelReferenceCode.newShiftCode()' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
grep -Fq 'PersonnelReferenceCode.newWorkScheduleCode()' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
if grep -R -n -E 'MAX\([^)]+\)[[:space:]]*\+[[:space:]]*1' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCode.kt"; then
  echo '::error::Unsafe sequential HR reference-code allocation detected'
  exit 1
fi

echo "PHASE4_RECONSTRUCTION=PASS"
echo "PHASE3_BASELINE_SHA=ea7658058ddabbcd184a3b58c0d0e36c5ede5549"
echo "SCHEMA_CHANGED=NO"
echo "MIGRATION_ADDED=NO"
echo "HOTFIX_01_SHA256=${patch_sha}"
