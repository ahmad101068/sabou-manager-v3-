#!/usr/bin/env bash

set +e
set -uo pipefail

log_dir="${GITHUB_WORKSPACE}/_ci/logs"
mkdir -p "${log_dir}"
cd "${PROJECT_DIR}"

targets=(
  'ir.sabou.inventory.data.db.FinancialClosuresMigration34To35Test#addsPayrollRevisionsAndGuardsClosedSalesDays'
  'ir.sabou.inventory.data.repository.AssetOutboxIntegrationTest#successfulDisposalCommitsAssetAndVersionedOutboxTogether'
  'ir.sabou.inventory.data.repository.DailySalesReversalIntegrationTest'
  'ir.sabou.inventory.data.repository.ManagementControlTransactionIntegrationTest'
  'ir.sabou.inventory.data.repository.RecipeVersionIntegrationTest'
  'ir.sabou.inventory.data.repository.InventoryTransferWorkflowIntegrationTest#issueAndReceiptTrackInTransitPreserveValueAndCreateNoJournal'
  'ir.sabou.inventory.data.repository.InventoryTransferWorkflowIntegrationTest#quantityVarianceAndUnauthorizedReceiptCannotMutateDestination'
  'ir.sabou.inventory.data.repository.InventoryTransferWorkflowIntegrationTest#immediateCompatibilityFlowIsAtomicAndReplaySafe'
  'ir.sabou.inventory.data.repository.InventoryTransferWorkflowIntegrationTest#outboxFailureRollsBackIssueMovementLotProjectionAuditAndStatus'
  'ir.sabou.inventory.data.repository.InventoryWasteWorkflowIntegrationTest#expiredLotWasteUsesExactLotCostAndPostsAllEffectsAtomically'
)

overall_rc=0
index=0
for target in "${targets[@]}"; do
  index=$((index + 1))
  safe_name="$(printf '%s' "${target}" | tr '#.' '__')"
  log_file="${log_dir}/target-$(printf '%02d' "${index}")-${safe_name}.log"
  echo "Running target ${index}/${#targets[@]}: ${target}" | tee "${log_file}"
  ./gradlew --no-daemon connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${target}" \
    --stacktrace 2>&1 | tee -a "${log_file}"
  rc=${PIPESTATUS[0]}
  echo "target_${index}=${rc}" | tee -a "${log_file}" "${log_dir}/exit-codes.env"
  if [[ ${rc} -ne 0 ]]; then
    overall_rc=1
  fi
done

./gradlew --no-daemon connectedDebugAndroidTest --stacktrace \
  2>&1 | tee "${log_dir}/20-connectedDebugAndroidTest-full.log"
full_rc=${PIPESTATUS[0]}
echo "connected_full=${full_rc}" | tee -a "${log_dir}/exit-codes.env"
if [[ ${full_rc} -ne 0 ]]; then
  overall_rc=1
fi

exit "${overall_rc}"
