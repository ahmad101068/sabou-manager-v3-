#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
source_root="${workspace}/${target}"
encoded="${workspace}/.phase3-final.patch.xz.b64"
patch="${workspace}/.phase3-final.patch"
hotfix_encoded="${workspace}/phase3-remediation/phase3-hotfix-01.patch.xz.b64"
hotfix_patch="${workspace}/.phase3-hotfix-01.patch"
hotfix2_encoded="${workspace}/phase3-remediation/phase3-hotfix-02.patch.xz.b64"
hotfix2_patch="${workspace}/.phase3-hotfix-02.patch"
hotfix3_script="${workspace}/phase3-remediation/phase3-hotfix-03.py"
hotfix4_script="${workspace}/phase3-remediation/phase3-hotfix-04.py"
hotfix5_script="${workspace}/phase3-remediation/phase3-hotfix-05.py"

# Always reconstruct the exact verified Phase-2 handoff first.
bash "${workspace}/.github/scripts/reconstruct-phase2-canonical.sh" "${target}"

test -d "${source_root}/app/src/main"
cat "${workspace}"/phase3-remediation/phase3-final.patch.xz.b64.* > "${encoded}"
encoded_sha="$(sha256sum "${encoded}" | awk '{print $1}')"
if [[ "${encoded_sha}" != "94d066c1ad553a8ce96b14cb0570d2f0f255d2e94b5ade186fc1c044ac78c3f7" ]]; then
  echo "::error::Phase-3 encoded patch digest mismatch: ${encoded_sha}"
  exit 1
fi
base64 --decode "${encoded}" | xz --decompress > "${patch}"
patch_sha="$(sha256sum "${patch}" | awk '{print $1}')"
if [[ "${patch_sha}" != "b2127b281b924a21e0b5330d54d397b12956357a6941fa71a431a4d182f31bb3" ]]; then
  echo "::error::Phase-3 patch digest mismatch: ${patch_sha}"
  exit 1
fi

git -C "${workspace}" apply --check --directory="${target}" "${patch}"
git -C "${workspace}" apply --directory="${target}" "${patch}"

# Apply deterministic compile hotfix discovered by the first real CI compile.
test -s "${hotfix_encoded}"
hotfix_encoded_sha="$(sha256sum "${hotfix_encoded}" | awk '{print $1}')"
if [[ "${hotfix_encoded_sha}" != "1ad7c46825ca037e2513f51d46457e157308b5f5a0678aeb937183f3d4a7be32" ]]; then
  echo "::error::Phase-3 hotfix encoded digest mismatch: ${hotfix_encoded_sha}"
  exit 1
fi
base64 --decode "${hotfix_encoded}" | xz --decompress > "${hotfix_patch}"
hotfix_sha="$(sha256sum "${hotfix_patch}" | awk '{print $1}')"
if [[ "${hotfix_sha}" != "735f273f582f6d1b3e5d1b14ab1af06377e75e62a397bbc2e0f8b110910cd762" ]]; then
  echo "::error::Phase-3 hotfix digest mismatch: ${hotfix_sha}"
  exit 1
fi
git -C "${workspace}" apply --check --directory="${target}" "${hotfix_patch}"
git -C "${workspace}" apply --directory="${target}" "${hotfix_patch}"

# Update legacy unit fixtures to satisfy the new explicit branch/location invariants without weakening assertions.
test -s "${hotfix2_encoded}"
hotfix2_encoded_sha="$(sha256sum "${hotfix2_encoded}" | awk '{print $1}')"
if [[ "${hotfix2_encoded_sha}" != "fa95e7a3271f789d7b3e84d39501297b5d5ee00176af6a0eda4856cbba67f1bb" ]]; then
  echo "::error::Phase-3 hotfix-02 encoded digest mismatch: ${hotfix2_encoded_sha}"
  exit 1
fi
base64 --decode "${hotfix2_encoded}" | xz --decompress > "${hotfix2_patch}"
hotfix2_sha="$(sha256sum "${hotfix2_patch}" | awk '{print $1}')"
if [[ "${hotfix2_sha}" != "27f30852ed4e70786db7e6b55c861f4a0882fdca2b30066c3547bb31bff26e5a" ]]; then
  echo "::error::Phase-3 hotfix-02 digest mismatch: ${hotfix2_sha}"
  exit 1
fi
git -C "${workspace}" apply --check --directory="${target}" "${hotfix2_patch}"
git -C "${workspace}" apply --directory="${target}" "${hotfix2_patch}"

# Align API35 integration fixtures with Phase-3 scope invariants and pin the Room-testing serializer compatibility runtime.
test -s "${hotfix3_script}"
hotfix3_sha="$(sha256sum "${hotfix3_script}" | awk '{print $1}')"
if [[ "${hotfix3_sha}" != "7aa0916b8c6d9725ca1301ceee8edb51b494d1af0f69df0c0137a9fd0a4c23a8" ]]; then
  echo "::error::Phase-3 hotfix-03 digest mismatch: ${hotfix3_sha}"
  exit 1
fi
python3 "${hotfix3_script}" "${source_root}"

# Align the remaining API35 sales fixtures and force the Room 2.8.4 serialization ABI consistently.
test -s "${hotfix4_script}"
hotfix4_sha="$(sha256sum "${hotfix4_script}" | awk '{print $1}')"
if [[ "${hotfix4_sha}" != "da657c1d52a071653b5ee71bc69c0b05a5aafe0b546898d6c5a01648522195d6" ]]; then
  echo "::error::Phase-3 hotfix-04 digest mismatch: ${hotfix4_sha}"
  exit 1
fi
python3 "${hotfix4_script}" "${source_root}"

# Keep the Room serialization compatibility fence on app/test APK runtimes without altering KSP.
test -s "${hotfix5_script}"
hotfix5_sha="$(sha256sum "${hotfix5_script}" | awk '{print $1}')"
if [[ "${hotfix5_sha}" != "0b958d5092cd7a54241b8494b12b9d5bc097be153eb82c18b369d0adc889d864" ]]; then
  echo "::error::Phase-3 hotfix-05 digest mismatch: ${hotfix5_sha}"
  exit 1
fi
python3 "${hotfix5_script}" "${source_root}"

# Copy Phase-3 verification plumbing into the reconstructed source handoff.
mkdir -p "${source_root}/.github/scripts" "${source_root}/.github/workflows"
cp "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "${source_root}/.github/scripts/reconstruct-phase3-candidate.sh"
if [[ -f "${workspace}/.github/workflows/phase3-targeted.yml" ]]; then
  cp "${workspace}/.github/workflows/phase3-targeted.yml" "${source_root}/.github/workflows/phase3-targeted.yml"
fi

# Fail-closed Phase-3 source gates before compilation.
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 56' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_55_56' "${source_root}/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'class LocalDataScopeService' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDataScopeService.kt"
grep -Fq 'class LocalSupplierPayableService' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalSupplierPayableService.kt"
test -s "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/db/Migration55To56Test.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/ui/InventoryRouteMappingTest.kt"
test -s "${source_root}/app/src/test/java/ir/restaurant/management/domain/purchase/SupplierInvoiceNumberTest.kt"

if grep -R -q 'fallbackToDestructiveMigration' "${source_root}/app/src/main"; then
  echo '::error::Destructive migration fallback is forbidden'
  exit 1
fi
if grep -R -E 'SemanticAccountRole\.(CASH|BANK|CARD_SETTLEMENT|PETTY_CASH)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository"; then
  echo '::error::Direct liquidity GL semantic posting detected outside Treasury'
  exit 1
fi

echo "PHASE3_RECONSTRUCTION=PASS"
echo "BASELINE_SHA=73ba5407f0b6e9182f23fee3a718fcebf385aab4"
echo "ROOM_VERSION=56"
echo "SCHEMA_CHANGED=YES"
echo "MIGRATION_ADDED=YES"
echo "PATCH_SHA256=${patch_sha}"
echo "HOTFIX_01_SHA256=${hotfix_sha}"
echo "HOTFIX_02_SHA256=${hotfix2_sha}"
echo "HOTFIX_03_SHA256=${hotfix3_sha}"
echo "HOTFIX_04_SHA256=${hotfix4_sha}"
echo "HOTFIX_05_SHA256=${hotfix5_sha}"
