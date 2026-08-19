#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
source_root="${workspace}/${target}"

# Reconstruct the exact final Phase-2 candidate first.
bash "${workspace}/.github/scripts/reconstruct-phase2-canonical.sh" "${target}"

patch_dir="${workspace}/phase3-remediation"
work_dir="${workspace}/.phase3-remediation-work"
rm -rf "${work_dir}"
mkdir -p "${work_dir}"

patches=(
  phase3-branch-location-schema
  phase3-procurement-core
  phase3-procurement-tests-ui
  phase3-inventory-count-waste
  phase3-matrix
)

declare -A expected_sha=(
  [phase3-branch-location-schema]="312d43a9e47d090eb9c26ad0bd70dedfa8077b4a86382f7fdab23b60bd08f0d2"
  [phase3-procurement-core]="e1f40ac95591ff4b7adeac2da531c4c417adea9727fc53f6bea3d2599f9350c3"
  [phase3-procurement-tests-ui]="c1b7dadf1ee178d219f7bb73ad5013ad0e5075507f265840b67aa0e20bc0d45e"
  [phase3-inventory-count-waste]="05ad7fdc8e076f69d1f9a92476b31742dbffbdbfa5f8e7e01b6438f0adca3767"
  [phase3-matrix]="ad6e0f64161de436c353bd67951287e1cef47232a4d8a6860f2fb3d4abd1d8b4"
)

for name in "${patches[@]}"; do
  encoded="${patch_dir}/${name}.patch.xz.b64"
  patch="${work_dir}/${name}.patch"
  test -s "${encoded}"
  base64 --decode "${encoded}" | xz --decompress --stdout > "${patch}"
  actual="$(sha256sum "${patch}" | awk '{print $1}')"
  if [[ "${actual}" != "${expected_sha[$name]}" ]]; then
    echo "::error::Phase 3 patch digest mismatch: ${name}: ${actual}"
    exit 1
  fi
  git -C "${workspace}" apply --check --directory="${target}" "${patch}"
  git -C "${workspace}" apply --directory="${target}" "${patch}"
done

# Fail-closed Phase-3 reconstruction gates.
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 56' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Fq 'MIGRATION_55_56' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt"
grep -Fq 'destinationLocationId' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/PurchaseEntities.kt"
grep -Fq 'inventoryLocationId' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/DailySalesEntities.kt"
grep -Fq 'CANONICAL_PROCUREMENT_REQUIRED' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalPurchaseRepository.kt"
grep -Fq 'LOT_REQUIRED' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
grep -Fq 'POSITIVE_LOT_VARIANCE_REQUIRES_LOT_LEVEL_WORKFLOW' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCountService.kt"
grep -Fq 'VERSION_CREATE' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/ProcurementSourcingService.kt"
grep -Fq 'activeRequestExistsForItemAtLocation' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/ProcurementSourcingService.kt"
grep -Fq 'LEGACY_COMPATIBILITY_PATH_DISABLED_USE_CANONICAL_COUNT_WORKFLOW' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
grep -Fq 'LEGACY_COMPATIBILITY_PATH_DISABLED_USE_CANONICAL_WASTE_WORKFLOW' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
grep -Fq 'PHASE3_BASE_SHA=73ba5407f0b6e9182f23fee3a718fcebf385aab4' \
  "${source_root}/docs/MASTER_REMEDIATION_MATRIX.md"

if grep -R -q 'MIGRATION_56_57' "${source_root}/app/src"; then
  echo '::error::Phase 3 must not add migration 56->57'
  exit 1
fi

# Business writes must not silently resolve a default warehouse.
for f in \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt" \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/ProcurementReceivingService.kt"; do
  if grep -q 'defaultLocationId()' "${f}"; then
    echo "::error::Hidden default location fallback remains: ${f}"
    exit 1
  fi
done

# Phase-2 liquidity ownership must remain inside Treasury.
if grep -R -E 'SemanticAccountRole\.(CASH|BANK|CARD_SETTLEMENT|PETTY_CASH)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository"; then
  echo '::error::Phase 3 regressed direct liquidity GL posting outside Treasury'
  exit 1
fi

# Refresh the candidate manifest after the Phase-3 overlay.
rm -f "${source_root}/PART3B-CANDIDATE-SHA256SUMS.txt"
(
  cd "${source_root}"
  find . -type f ! -path './PART3B-CANDIDATE-SHA256SUMS.txt' -print0 \
    | sort -z \
    | xargs -0 sha256sum > PART3B-CANDIDATE-SHA256SUMS.txt
)

candidate_sha256="$(sha256sum "${source_root}/PART3B-CANDIDATE-SHA256SUMS.txt" | awk '{print $1}')"
echo "PHASE3_RECONSTRUCTION=PASS"
echo "PHASE3_BASE_SHA=73ba5407f0b6e9182f23fee3a718fcebf385aab4"
echo "ROOM_VERSION=56"
echo "MIGRATION_55_56=PRESENT"
echo "CANDIDATE_SHA256=${candidate_sha256}"
