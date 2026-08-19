#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"
source_root="${workspace}/${target}"

# Reconstruct the exact green Phase-1/Part-3B candidate first.
bash "${workspace}/.github/scripts/reconstruct-part3b-candidate.sh" "${target}"

patch_dir="${workspace}/phase2-remediation"
work_dir="${workspace}/.phase2-remediation-work"
rm -rf "${work_dir}"
mkdir -p "${work_dir}"

patches=(
  phase2-treasury
  phase2-receivables
  phase2-liquidity-boundaries
  phase2-financial-reads
  phase2-wiring
  phase2-ap-test
)

declare -A expected_sha=(
  [phase2-treasury]="e5ce3c397a7a2b1fc199bd11c948af33b1515b950dea05a811e5b918c4e01c9c"
  [phase2-receivables]="d32c9ff570fd93b3b4a878d9c934ede50ad9eb3dd2811993b7816fa0e6523261"
  [phase2-liquidity-boundaries]="9a7b3afe007066e96c3312872c97584a79a5b4a6cb487a5edea77d7caf2c91a2"
  [phase2-financial-reads]="f48791d9552fedb662f46a86666403470b29db648f30d660b319ff5513f31c2b"
  [phase2-wiring]="c09e467b6a9e35a09c8b2688438920dacf021ac769a9d5675a1bd19075ef9809"
  [phase2-ap-test]="449da3e855c607edcca4fd44d0e00c4798043680205eb2190c364eded97b63b4"
)

for name in "${patches[@]}"; do
  encoded="${patch_dir}/${name}.patch.gz.b64"
  patch="${work_dir}/${name}.patch"
  test -s "${encoded}"
  base64 --decode "${encoded}" | gzip --decompress > "${patch}"
  actual="$(sha256sum "${patch}" | awk '{print $1}')"
  if [[ "${actual}" != "${expected_sha[$name]}" ]]; then
    echo "::error::Phase 2 patch digest mismatch: ${name}: ${actual}"
    exit 1
  fi
  git -C "${workspace}" apply --check --directory="${target}" "${patch}"
  git -C "${workspace}" apply --directory="${target}" "${patch}"
done

# Canonical Phase-2 docs and verification plumbing are part of the source handoff.
mkdir -p "${source_root}/docs" "${source_root}/.github/scripts" "${source_root}/.github/workflows"
cp "${workspace}/docs/MASTER_REMEDIATION_MATRIX.md" "${source_root}/docs/MASTER_REMEDIATION_MATRIX.md"
cp "${workspace}/.github/scripts/reconstruct-phase2-canonical.sh" "${source_root}/.github/scripts/reconstruct-phase2-canonical.sh"
cp "${workspace}/.github/workflows/phase2-canonical-targeted.yml" "${source_root}/.github/workflows/phase2-canonical-targeted.yml"

# Phase 2 financial truth fail-closed reconstruction gates.
grep -Fq 'enum class TreasuryBusinessIntent' \
  "${source_root}/app/src/main/java/ir/restaurant/management/domain/treasury/TreasuryModels.kt"
grep -Fq 'SemanticAccountRole.CARD_SETTLEMENT' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury/DefaultTreasuryAccountCatalog.kt"
grep -Fq 'SemanticAccountRole.PETTY_CASH' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury/DefaultTreasuryAccountCatalog.kt"
grep -Fq 'businessIntent = TreasuryBusinessIntent.DAILY_SALES_SETTLEMENT' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'collectionByGlobalId' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalReceivableService.kt"
grep -Fq 'daily_sales_confirmed_cost_snapshot_changed' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'payablePurchase_cardSettlementUsesTreasuryAndApGlExactlyOnce' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/BranchPurchasePostingIntegrationTest.kt"
grep -Fq 'receiptReplay_sameCommandIdWithDifferentAccount_failsIdempotencyConflict' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/TreasuryV2IntegrationTest.kt"
grep -Fq 'receivableCollection_rollsBackTreasuryJournalLedgerAndMaster_whenFailureOccursAfterTreasury' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt"
grep -Fq 'merge_preservesPostedFinancialHistory_andExposesLogicalCombinedLedger' \
  "${source_root}/app/src/androidTest/java/ir/restaurant/management/data/repository/CrmReceivablesIntegrationTest.kt"
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 55' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"

schema_dir="${source_root}/app/schemas/ir.restaurant.management.data.db.AppDatabase"
test -s "${schema_dir}/55.json"
test ! -e "${schema_dir}/56.json"
if grep -R -q 'MIGRATION_55_56' "${source_root}/app/src"; then
  echo '::error::Phase 2 must not add migration 55->56'
  exit 1
fi

if grep -R -E 'sourceType\.contains|contains\("(CUSTOMER|RECEIVABLE|SUPPLIER|PURCHASE|PAYROLL|EMPLOYEE)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury"; then
  echo '::error::Free-text treasury financial classification remains'
  exit 1
fi

if grep -R -E 'SemanticAccountRole\.(CASH|BANK|CARD_SETTLEMENT|PETTY_CASH)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository"; then
  echo '::error::Repository direct liquidity GL posting remains outside Treasury'
  exit 1
fi

if grep -R -E '\.move(SalesInvoices|Ledger|Receivables)\(' \
  "${source_root}/app/src/main/java"; then
  echo '::error::Customer merge still rewrites posted financial history'
  exit 1
fi

# Refresh the candidate manifest after the Phase-2 overlay and copied metadata.
rm -f "${source_root}/PART3B-CANDIDATE-SHA256SUMS.txt"
(
  cd "${source_root}"
  find . -type f ! -path './PART3B-CANDIDATE-SHA256SUMS.txt' -print0 \
    | sort -z \
    | xargs -0 sha256sum > PART3B-CANDIDATE-SHA256SUMS.txt
)

candidate_sha256="$(sha256sum "${source_root}/PART3B-CANDIDATE-SHA256SUMS.txt" | awk '{print $1}')"
echo "PHASE2_RECONSTRUCTION=PASS"
echo "ROOM_VERSION=55"
echo "SCHEMA_CHANGED=NO"
echo "MIGRATION_ADDED=NO"
echo "CANDIDATE_SHA256=${candidate_sha256}"
