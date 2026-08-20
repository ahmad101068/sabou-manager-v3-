#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
source_root="${workspace}/${target}"
encoded="${workspace}/.phase3-final.patch.xz.b64"
patch="${workspace}/.phase3-final.patch"

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
