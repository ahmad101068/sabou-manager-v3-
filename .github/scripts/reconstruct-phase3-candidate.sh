#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
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

bash "${workspace}/.github/scripts/reconstruct-phase2-canonical.sh" "$target"
test -d "${source_root}/app/src/main"

encoded="${workspace}/.phase3-final.patch.xz.b64"
patch="${workspace}/.phase3-final.patch"
cat "${workspace}"/phase3-remediation/phase3-final.patch.xz.b64.* > "$encoded"
verify_sha "$encoded" "94d066c1ad553a8ce96b14cb0570d2f0f255d2e94b5ade186fc1c044ac78c3f7" "Phase-3 encoded patch" >/dev/null
base64 --decode "$encoded" | xz --decompress > "$patch"
patch_sha="$(verify_sha "$patch" "b2127b281b924a21e0b5330d54d397b12956357a6941fa71a431a4d182f31bb3" "Phase-3 patch")"
git -C "$workspace" apply --check --directory="$target" "$patch"
git -C "$workspace" apply --directory="$target" "$patch"

hotfix1_encoded="${workspace}/phase3-remediation/phase3-hotfix-01.patch.xz.b64"
hotfix1_patch="${workspace}/.phase3-hotfix-01.patch"
verify_sha "$hotfix1_encoded" "1ad7c46825ca037e2513f51d46457e157308b5f5a0678aeb937183f3d4a7be32" "Phase-3 hotfix-01 encoded" >/dev/null
base64 --decode "$hotfix1_encoded" | xz --decompress > "$hotfix1_patch"
hotfix1_sha="$(verify_sha "$hotfix1_patch" "735f273f582f6d1b3e5d1b14ab1af06377e75e62a397bbc2e0f8b110910cd762" "Phase-3 hotfix-01")"
git -C "$workspace" apply --check --directory="$target" "$hotfix1_patch"
git -C "$workspace" apply --directory="$target" "$hotfix1_patch"

hotfix2_encoded="${workspace}/phase3-remediation/phase3-hotfix-02.patch.xz.b64"
hotfix2_patch="${workspace}/.phase3-hotfix-02.patch"
verify_sha "$hotfix2_encoded" "fa95e7a3271f789d7b3e84d39501297b5d5ee00176af6a0eda4856cbba67f1bb" "Phase-3 hotfix-02 encoded" >/dev/null
base64 --decode "$hotfix2_encoded" | xz --decompress > "$hotfix2_patch"
hotfix2_sha="$(verify_sha "$hotfix2_patch" "27f30852ed4e70786db7e6b55c861f4a0882fdca2b30066c3547bb31bff26e5a" "Phase-3 hotfix-02")"
git -C "$workspace" apply --check --directory="$target" "$hotfix2_patch"
git -C "$workspace" apply --directory="$target" "$hotfix2_patch"

apply_python_hotfix() {
  local number="$1" expected="$2" script sha
  script="${workspace}/phase3-remediation/phase3-hotfix-${number}.py"
  sha="$(verify_sha "$script" "$expected" "Phase-3 hotfix-${number}")"
  python3 "$script" "$source_root" >&2
  printf '%s' "$sha"
}

hotfix3_sha="$(apply_python_hotfix 03 "7aa0916b8c6d9725ca1301ceee8edb51b494d1af0f69df0c0137a9fd0a4c23a8")"
hotfix4_sha="$(apply_python_hotfix 04 "da657c1d52a071653b5ee71bc69c0b05a5aafe0b546898d6c5a01648522195d6")"
hotfix5_sha="$(apply_python_hotfix 05 "0b958d5092cd7a54241b8494b12b9d5bc097be153eb82c18b369d0adc889d864")"
hotfix6_sha="$(apply_python_hotfix 06 "b8a2ff6d9a5146369ebefb8c60f6b94eed760fba6a0c92446ca088b0ec854597")"
hotfix7_sha="$(apply_python_hotfix 07 "a8456cfc3d9afb095722c5a1054b1322d936922802c00d73479fabd87ccac959")"

mkdir -p "${source_root}/.github/scripts" "${source_root}/.github/workflows"
cp "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "${source_root}/.github/scripts/reconstruct-phase3-candidate.sh"
if [[ -f "${workspace}/.github/workflows/phase3-targeted.yml" ]]; then
  cp "${workspace}/.github/workflows/phase3-targeted.yml" "${source_root}/.github/workflows/phase3-targeted.yml"
fi

grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 56' "${source_root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_55_56' "${source_root}/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'class LocalDataScopeService' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDataScopeService.kt"
grep -Fq 'class LocalSupplierPayableService' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalSupplierPayableService.kt"
test -s "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/db/Migration55To56Test.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/ui/InventoryRouteMappingTest.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/domain/purchase/SupplierInvoiceNumberTest.kt"

if grep -R -q 'fallbackToDestructiveMigration' "${source_root}/app/src/main"; then
  echo '::error::Destructive migration fallback is forbidden'
  exit 1
fi
if grep -R -E 'SemanticAccountRole\.(CASH|BANK|CARD_SETTLEMENT|PETTY_CASH)' "${source_root}/app/src/main/java/ir/restaurant/management/data/repository"; then
  echo '::error::Direct liquidity GL semantic posting detected outside Treasury'
  exit 1
fi

echo "PHASE3_RECONSTRUCTION=PASS"
echo "BASELINE_SHA=73ba5407f0b6e9182f23fee3a718fcebf385aab4"
echo "ROOM_VERSION=56"
echo "SCHEMA_CHANGED=YES"
echo "MIGRATION_ADDED=YES"
echo "PATCH_SHA256=${patch_sha}"
echo "HOTFIX_01_SHA256=${hotfix1_sha}"
echo "HOTFIX_02_SHA256=${hotfix2_sha}"
echo "HOTFIX_03_SHA256=${hotfix3_sha}"
echo "HOTFIX_04_SHA256=${hotfix4_sha}"
echo "HOTFIX_05_SHA256=${hotfix5_sha}"
echo "HOTFIX_06_SHA256=${hotfix6_sha}"
echo "HOTFIX_07_SHA256=${hotfix7_sha}"
