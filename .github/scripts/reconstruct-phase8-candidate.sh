#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-source}"
root="${workspace}/${target}"
phase7_sha="e7dcf11a7b56a6f738313f2a7dfd41fa4159ea80"

bash "${workspace}/.github/scripts/reconstruct-phase7-candidate.sh" "$target"

test -s "${workspace}/phase8-remediation/BASELINE.txt"
grep -Fq "PHASE7_FORMAL_HANDOFF_SHA=${phase7_sha}" "${workspace}/phase8-remediation/BASELINE.txt"
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
  app/src/test/java/ir/restaurant/management/ui/Part3BPerformanceContractTest.kt
)
for rel in "${required[@]}"; do test -s "$root/$rel" || { echo "::error::missing Phase8 coverage $rel"; exit 1; }; done

grep -Fq 'migratesVersionOneWithoutDestructiveFallback' "$root/app/src/androidTest/java/ir/restaurant/management/data/db/FullMigration1ToCurrentTest.kt"
grep -Fq 'purchaseGoodsReceipt_uiIncreasesStockAndPostsBalancedReceiptJournal' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'payrollRegistrationAndApproval_uiCalculatesReviewsAndPostsAccrual' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'attendanceUseCases.save' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'assetDepreciation_uiUpdatesBookValueAndPostsBalancedJournal' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'treasuryReversal_uiCreatesCompensatingJournalLedgerReferenceAndMarksOriginalReversed' "$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq "sourceType='DAILY_SALES_COGS'" "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt"
grep -Fq 'closePeriodCommitsOneAuditAndOutboxInSameTransaction' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/ManagementControlTransactionIntegrationTest.kt"
grep -Fq 'appendsRevisionsAndSelectsFormulaByBusinessDate' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/RecipeVersionIntegrationTest.kt"
grep -Fq 'costSnapshot.recipeVersionId' "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/DailySalesReversalIntegrationTest.kt"

echo PHASE8_RECONSTRUCTION=PASS
echo PHASE7_FORMAL_HANDOFF_SHA=$phase7_sha
echo ROOM_VERSION=59
