#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-source}"
root="${workspace}/${target}"
phase7_sha="e7dcf11a7b56a6f738313f2a7dfd41fa4159ea80"
hotfix_01="${workspace}/phase8-remediation/phase8-hotfix-01.py"
hotfix_02="${workspace}/phase8-remediation/phase8-hotfix-02.py"
hotfix_03="${workspace}/phase8-remediation/phase8-hotfix-03.py"

verify_sha() {
  local file="$1" expected="$2" label="$3" actual
  test -s "$file" || { echo "::error::${label} missing"; return 1; }
  actual="$(sha256sum "$file" | awk '{print $1}')"
  test "$actual" = "$expected" || { echo "::error::${label} digest mismatch: $actual"; return 1; }
}

bash "${workspace}/.github/scripts/reconstruct-phase7-candidate.sh" "$target"

test -s "${workspace}/phase8-remediation/BASELINE.txt"
grep -Fq "PHASE7_FORMAL_HANDOFF_SHA=${phase7_sha}" "${workspace}/phase8-remediation/BASELINE.txt"

verify_sha "$hotfix_01" "8d3babc233b9067435283615249eddd1aa194cb2a18a9ed65b141d68d5b43d01" "Phase-8 hotfix-01"
verify_sha "$hotfix_02" "b216d25d7a4690989a78386ab820b0adc75f9b825907e4a45c021f6ed63f93b9" "Phase-8 hotfix-02"
verify_sha "$hotfix_03" "3288aadb8b8e3fbb275d7a7ead7c679e52df39c0fe1c0a01dca31876d293eef5" "Phase-8 hotfix-03"
python3 "$hotfix_01" "$root"
python3 "$hotfix_02" "$root"
python3 "$hotfix_03" "$root"

grep -Fq 'APP_DATABASE_SCHEMA_VERSION = 59' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then
  echo '::error::destructive migration fallback found'
  exit 1
fi

required=(
  app/src/androidTest/java/ir/restaurant/management/data/db/FullMigration1ToCurrentTest.kt
  app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/DailySalesReversalIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/BranchPurchasePostingIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/BranchPayrollPostingIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/AssetLifecycleIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/ManagementControlTransactionIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/RecipeVersionIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/RecipeLifecycleIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/EnterprisePermissionIntegrationTest.kt
  app/src/androidTest/java/ir/restaurant/management/data/repository/TreasuryV2IntegrationTest.kt
  app/src/test/java/ir/restaurant/management/data/repository/GoodsReceiptIdempotencyTest.kt
  app/src/test/java/ir/restaurant/management/ui/Part3BPerformanceContractTest.kt
)
for rel in "${required[@]}"; do test -s "$root/$rel" || { echo "::error::missing Phase8 coverage $rel"; exit 1; }; done

grep -Fq 'destinationLocationId = LOCATION_ID' "$root/app/src/test/java/ir/restaurant/management/data/repository/GoodsReceiptIdempotencyTest.kt"
grep -Fq 'assertTrue(adaptive.contains("val visibleRows = rows.page(pageWindow)"))' "$root/app/src/test/java/ir/restaurant/management/ui/Part3BPerformanceContractTest.kt"
grep -Fq 'assertTrue(adaptive.contains("items(visibleRows, key = key)"))' "$root/app/src/test/java/ir/restaurant/management/ui/Part3BPerformanceContractTest.kt"

for rel in \
  app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt \
  app/src/main/java/ir/restaurant/management/data/repository/RecipeMaterialResolver.kt; do
  if grep -Fq '.longValueExact()' "$root/$rel"; then
    echo "::error::API23-incompatible BigInteger.longValueExact remains in $rel"
    exit 1
  fi
  grep -Fq 'toLongExactCompat()' "$root/$rel"
  grep -Fq 'import ir.restaurant.management.core.toLongExactCompat' "$root/$rel"
done

e2e="$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
test "$(grep -Fc 'val sourceType = "OTHER_INCOME"' "$e2e")" -ge 2
grep -Fq 'fun crmCollection_viaCrmUi_updatesReceivableLedgerAndAgingBalance()' "$e2e"
grep -Fq 'app.container.receivableService.createFromDailySales(' "$e2e"
grep -Fq 'entryType == "COLLECTION"' "$e2e"
grep -Fq 'UserDataScope(' "$e2e"
grep -Fq 'allowedBranchIds = setOf(branchId)' "$e2e"
grep -Fq 'destinationLocationId = destinationLocationId' "$e2e"
grep -Fq 'branchId = branchId' "$e2e"
if grep -Fq 'UI_E2E_RECEIPT' "$e2e" || grep -Fq 'UI_E2E_REVERSAL' "$e2e" || grep -Fq 'performTextReplacement("CUSTOMER_RECEIVABLE")' "$e2e"; then
  echo '::error::stale/untyped manual treasury E2E intent remains'
  exit 1
fi

grep -Fq 'migratesVersionOneWithoutDestructiveFallback' "$root/app/src/androidTest/java/ir/restaurant/management/data/db/FullMigration1ToCurrentTest.kt"
grep -Fq 'purchaseGoodsReceipt_uiIncreasesStockAndPostsBalancedReceiptJournal' "$e2e"
grep -Fq 'payrollRegistrationAndApproval_uiCalculatesReviewsAndPostsAccrual' "$e2e"
grep -Fq 'attendanceUseCases.save' "$e2e"
grep -Fq 'assetDepreciation_uiUpdatesBookValueAndPostsBalancedJournal' "$e2e"
grep -Fq 'treasuryReversal_uiCreatesCompensatingJournalLedgerReferenceAndMarksOriginalReversed' "$e2e"
grep -Fq "sourceType='DAILY_SALES_COGS'" "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt"
grep -Fq 'closePeriodCommitsOneAuditAndOutboxInSameTransaction' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/ManagementControlTransactionIntegrationTest.kt"
grep -Fq 'appendsRevisionsAndSelectsFormulaByBusinessDate' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/RecipeVersionIntegrationTest.kt"
grep -Fq 'costSnapshot.recipeVersionId' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/DailySalesReversalIntegrationTest.kt"

echo PHASE8_RECONSTRUCTION=PASS
echo PHASE7_FORMAL_HANDOFF_SHA=$phase7_sha
echo ROOM_VERSION=59
echo HOTFIX_01_SHA256=8d3babc233b9067435283615249eddd1aa194cb2a18a9ed65b141d68d5b43d01
echo HOTFIX_02_SHA256=b216d25d7a4690989a78386ab820b0adc75f9b825907e4a45c021f6ed63f93b9
echo HOTFIX_03_SHA256=3288aadb8b8e3fbb275d7a7ead7c679e52df39c0fe1c0a01dca31876d293eef5
