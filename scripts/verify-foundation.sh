#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN_PACKAGE="$PROJECT_ROOT/app/src/main/java/ir/restaurant/management"

fail() {
  echo "ERP 2.0 foundation verification failed: $*" >&2
  exit 1
}

require_file() {
  test -s "$PROJECT_ROOT/$1" || fail "missing $1"
}

require_literal() {
  local needle="$1"
  local file="$2"
  rg -Fq "$needle" "$PROJECT_ROOT/$file" || fail "missing '$needle' in $file"
}

command -v rg >/dev/null 2>&1 || fail "ripgrep (rg) is required"

required_files=(
  "app/src/main/java/ir/restaurant/management/core/MoneyRial.kt"
  "app/src/main/java/ir/restaurant/management/core/QuantityMicros.kt"
  "app/src/main/java/ir/restaurant/management/core/FixedPointRatio.kt"
  "app/src/main/java/ir/restaurant/management/core/GlobalId.kt"
  "app/src/main/java/ir/restaurant/management/core/CorrelationId.kt"
  "app/src/main/java/ir/restaurant/management/domain/common/BusinessError.kt"
  "app/src/main/java/ir/restaurant/management/domain/accounting/AccountingPosting.kt"
  "app/src/main/java/ir/restaurant/management/domain/treasury/TreasuryModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/security/AuthorizationService.kt"
  "app/src/main/java/ir/restaurant/management/domain/audit/AuditService.kt"
  "app/src/main/java/ir/restaurant/management/domain/security/Permission.kt"
  "app/src/main/java/ir/restaurant/management/domain/security/SegregationOfDuties.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryLedgerModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryMasterModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryBalanceModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryCountModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryWasteModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryTransferModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryReplenishmentModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryReadModels.kt"
  "app/src/main/java/ir/restaurant/management/domain/audit/AuditEvent.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryCommandEntities.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryLedgerDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryBalanceDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryBalanceEntities.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryCountDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryCountEntities.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryLotDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryLotEntities.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryTransferDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryTransferEntities.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryReplenishmentDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryReadDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/SupplierDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/PurchaseDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/ProcurementDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/AccountingDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/PersonnelDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/RecipeDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/InventoryControlDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/AuditLogDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/SecurityDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/AssetDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/AlertDao.kt"
  "app/src/main/java/ir/restaurant/management/data/db/EnterpriseLedgerGuards.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/EarlySchemaMigrations.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/OperationsMigrations.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/EnterpriseMigrations.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/DatabaseIntegrityLifecycle.kt"
  "app/src/main/java/ir/restaurant/management/data/db/migration/DatabaseSeedCallback.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalAuditEventWriter.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryRepository.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryIntegrityService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryLotService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCountService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryWasteService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryTransferService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryReplenishmentService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryReadService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/LocalAccountingPostingEngine.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/ProcurementReceivingService.kt"
  "app/src/main/java/ir/restaurant/management/data/repository/ProcurementInvoiceMatchingService.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/db/EnterpriseLedgerMigration41To42Test.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/db/InventoryMasterMigration42To43Test.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/db/FullMigration1ToCurrentTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryLedgerIntegrationTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/AccountingPostingIntegrationTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryTransferWorkflowIntegrationTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryReplenishmentIntegrationTest.kt"
  "app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryReadServiceIntegrationTest.kt"
  "app/src/test/java/ir/restaurant/management/core/FixedPointRatioTest.kt"
  "app/src/test/java/ir/restaurant/management/core/GlobalIdTest.kt"
  "app/src/test/java/ir/restaurant/management/domain/security/SegregationOfDutiesTest.kt"
  "app/src/test/java/ir/restaurant/management/data/db/MigrationRegistryTest.kt"
  "app/src/test/java/ir/restaurant/management/data/repository/GoodsReceiptIdempotencyTest.kt"
  "README.md"
  "ARCHITECTURE-CURRENT.md"
  "ARCHITECTURE-FREEZE.md"
  "PRODUCT-TERMINOLOGY.md"
  "UI-STANDARDS.md"
  "UI-COMPONENT-INVENTORY.md"
  "docs/SECURITY.md"
  "docs/TESTING.md"
  "docs/RELEASE_GUIDE.md"
  "docs/MAINTENANCE.md"
  "docs/HANDOVER.md"
  "docs/KNOWN_ISSUES.md"
  "docs/DECISIONS.md"
  "docs/DEPENDENCIES.md"
  "docs/PRIVACY.md"
  "docs/GOOGLE_PLAY_DATA_SAFETY.md"
  "docs/PAYROLL-CALCULATION-SPEC.md"
  ".github/workflows/production-readiness.yml"
  ".github/workflows/tests.yml"
  "CHANGELOG.md"
  "LICENSES.md"
)

for relative in "${required_files[@]}"; do
  require_file "$relative"
done

schema_version="$(sed -nE 's/^internal const val APP_DATABASE_SCHEMA_VERSION = ([0-9]+)$/\1/p' "$MAIN_PACKAGE/data/db/AppDatabase.kt")"
[[ "$schema_version" =~ ^[1-9][0-9]*$ ]] || fail "database schema version must be a positive single-source constant"
require_literal "MIGRATION_$((schema_version - 1))_${schema_version}" "app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt"
require_literal 'version = APP_DATABASE_SCHEMA_VERSION' "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
rg -q 'versionCode = [1-9][0-9]*' "$PROJECT_ROOT/app/build.gradle.kts" || fail "versionCode is missing"
rg -q 'versionName = "[^"]+"' "$PROJECT_ROOT/app/build.gradle.kts" || fail "versionName is missing"
require_literal 'SupportOpenHelperFactory' "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
require_literal 'android:allowBackup="false"' "app/src/main/AndroidManifest.xml"
require_literal 'android:usesCleartextTraffic="false"' "app/src/main/AndroidManifest.xml"

require_literal 'database.withTransaction' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
require_literal 'database.withTransaction' "app/src/main/java/ir/restaurant/management/data/repository/LocalAccountingPostingEngine.kt"
require_literal 'BusinessError.InsufficientStock' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
require_literal 'IdempotencyConflict' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
require_literal 'idempotencyKey' "app/src/main/java/ir/restaurant/management/data/db/AccountingEntities.kt"
require_literal 'reversalOfEntryId' "app/src/main/java/ir/restaurant/management/data/db/AccountingEntities.kt"
require_literal 'compareAndSetValuation' "app/src/main/java/ir/restaurant/management/data/db/InventoryDao.kt"
require_literal 'compareAndSetOnHand' "app/src/main/java/ir/restaurant/management/data/db/InventoryBalanceDao.kt"
require_literal 'InventoryCommandService' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
require_literal 'FefoLotAllocator' "app/src/main/java/ir/restaurant/management/domain/inventory/InventoryLotModels.kt"
require_literal 'installPostedJournalGuards' "app/src/main/java/ir/restaurant/management/data/db/EnterpriseLedgerGuards.kt"
require_literal 'installStockMovementGuards' "app/src/main/java/ir/restaurant/management/data/db/EnterpriseLedgerGuards.kt"
require_literal 'installInventoryCountGuards' "app/src/main/java/ir/restaurant/management/data/db/EnterpriseLedgerGuards.kt"
require_literal 'installAuditLogGuards' "app/src/main/java/ir/restaurant/management/data/db/migration/DatabaseIntegrityLifecycle.kt"
require_literal 'seedSystemLocations' "app/src/main/java/ir/restaurant/management/data/db/migration/DatabaseSeedCallback.kt"
require_literal 'SegregationOfDuties.requireDifferentActors' "app/src/main/java/ir/restaurant/management/data/repository/LocalPersonnelRepository.kt"
require_literal 'SegregationOfDuties.requireDifferentHistoricalAware' "app/src/main/java/ir/restaurant/management/data/repository/LocalProcurementRepository.kt"
require_literal 'Permission.INVENTORY_ADJUST' "app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
require_literal 'SensitiveAction.ADJUST_INVENTORY' "app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
require_literal 'countByIdempotencyKey' "app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
require_literal 'InventoryWasteService' "app/src/main/java/ir/restaurant/management/data/repository/LocalOperationsRepository.kt"
require_literal 'wasteDocumentByIdempotencyKey' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryWasteService.kt"
require_literal 'InventoryCountService' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCountService.kt"
require_literal 'InventoryTransferService' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryTransferService.kt"
require_literal 'compareAndSetInTransit' "app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt"
require_literal 'InventoryReplenishmentCalculator' "app/src/main/java/ir/restaurant/management/domain/purchase/ProcurementModels.kt"
require_literal 'InventoryReplenishmentCalculator' "app/src/main/java/ir/restaurant/management/domain/operations/SmartReorderCalculator.kt"
require_literal 'activeSummaryByDay' "app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
# Legacy payroll remains queryable for migration/history, while all new financial writes use
# the HR/Payroll 2.0 command boundary and the shared accounting/treasury foundations.
require_literal 'payrollByGlobalId' "app/src/main/java/ir/restaurant/management/data/db/PersonnelDao.kt"
require_literal 'CorrelationId.forCommand' "app/src/main/java/ir/restaurant/management/data/repository/LocalHrPayrollService.kt"
if rg -Fq 'UnsupportedDomainOperation' "$PROJECT_ROOT/app/src/main/java/ir/restaurant/management/data/repository/LocalPersonnelRepository.kt"; then
  fail "legacy always-unsupported payroll API returned to LocalPersonnelRepository"
fi
require_literal 'postExistingDraft' "app/src/main/java/ir/restaurant/management/data/repository/LocalAccountingPostingEngine.kt"
require_literal 'AccountingPostingService' "app/src/main/java/ir/restaurant/management/data/repository/LocalHrPayrollService.kt"
require_literal 'TreasuryService' "app/src/main/java/ir/restaurant/management/data/repository/LocalHrPayrollService.kt"
require_literal 'HrPayrollCommandService' "app/src/main/java/ir/restaurant/management/data/repository/LocalHrPayrollService.kt"
require_literal 'UiErrorHandler.message' "app/src/main/java/ir/restaurant/management/ui/OperationsViewModel.kt"
require_literal 'git diff --exit-code -- app/schemas' ".github/workflows/production-readiness.yml"
require_literal 'lintDebug' ".github/workflows/production-readiness.yml"
require_literal 'assembleDebug' ".github/workflows/production-readiness.yml"
require_literal 'connectedDebugAndroidTest' ".github/workflows/tests.yml"
require_literal 'api-level: [23, 35]' ".github/workflows/tests.yml"
require_literal 'google_apis_ps16k' ".github/workflows/tests.yml"

if rg -n 'fallbackToDestructiveMigration' "$PROJECT_ROOT/app/src/main"; then
  fail "destructive Room migration fallback is forbidden"
fi

if rg -n '\bGlobalScope\b' "$PROJECT_ROOT/app/src/main"; then
  fail "GlobalScope is forbidden"
fi

if rg -n '\b[A-Za-z][A-Za-z0-9]*Dao\(\)' "$MAIN_PACKAGE/ui" -g '*.kt'; then
  fail "Compose/UI code must not call a DAO directly"
fi

if rg -n '(accountingDao|journalDao)\(|postDraftEntry\(|insertEntry\(' "$MAIN_PACKAGE/ui" -g '*.kt'; then
  fail "UI code must not mutate the journal ledger"
fi

if rg -n '^import ir\.restaurant\.management\.ui\.|ir\.restaurant\.management\.ui\.' "$MAIN_PACKAGE/domain" -g '*.kt'; then
  fail "domain code must not depend on presentation"
fi

if test -e "$MAIN_PACKAGE/data/db/Daos.kt"; then
  fail "God DAO file Daos.kt must remain split by owner domain"
fi

mapfile -t dao_types < <(
  sed -nE 's/^    abstract fun [A-Za-z0-9_]+Dao\(\): ([A-Za-z0-9_]+)$/\1/p' \
    "$MAIN_PACKAGE/data/db/AppDatabase.kt"
)
[[ "${#dao_types[@]}" -gt 0 ]] || fail "AppDatabase has no DAO accessors"
for dao_type in "${dao_types[@]}"; do
  declaration_count="$(rg -n "^interface ${dao_type}\\b" "$MAIN_PACKAGE/data/db" -g '*.kt' | wc -l)"
  [[ "$declaration_count" -eq 1 ]] || fail "$dao_type must have exactly one declaration"
done

app_database_accessor_count="$(rg -n '^    abstract fun .*Dao\(\):' "$MAIN_PACKAGE/data/db/AppDatabase.kt" | wc -l)"
dao_annotation_count="$(rg -n '^@Dao$' "$MAIN_PACKAGE/data/db" -g '*.kt' | wc -l)"
[[ "$app_database_accessor_count" -eq "${#dao_types[@]}" ]] || fail "AppDatabase DAO accessor parsing is inconsistent"
[[ "$dao_annotation_count" -eq "$app_database_accessor_count" ]] || fail "DAO declarations and AppDatabase accessors differ"

if rg -n 'authorizer\.(require|allows)\("[A-Z_]+"' "$PROJECT_ROOT/app/src/main"; then
  fail "stringly typed authorization reached a command boundary"
fi

if rg -n 'return@withTransaction' \
  "$MAIN_PACKAGE/data/repository/LocalInventoryCommandEngine.kt" \
  "$MAIN_PACKAGE/data/repository/LocalAccountingPostingEngine.kt"; then
  fail "transaction wrapper uses an out-of-scope Room label"
fi

if rg -n 'DELETE FROM (journal_entries|journal_lines|stock_movements|audit_logs)' "$PROJECT_ROOT/app/src/main"; then
  fail "direct deletion of immutable ledgers is forbidden"
fi

if rg -n '\b(Double|Float)\b' "$MAIN_PACKAGE/core" "$MAIN_PACKAGE/domain"; then
  fail "floating-point type reached the core/domain model"
fi

mapfile -t stock_projection_writers < <(
  rg -l 'compareAndSetValuation\(' "$MAIN_PACKAGE" -g '*.kt' | sort
)
expected_stock_projection_writers=(
  "$MAIN_PACKAGE/data/db/InventoryDao.kt"
  "$MAIN_PACKAGE/data/repository/LocalInventoryCommandEngine.kt"
)
if [[ "${stock_projection_writers[*]}" != "${expected_stock_projection_writers[*]}" ]]; then
  printf 'Unexpected inventory projection writer:\n%s\n' "${stock_projection_writers[*]}" >&2
  fail "inventory projection writes must pass through LocalInventoryCommandEngine"
fi

mapfile -t location_projection_writers < <(
  rg -l 'compareAndSetOnHand\(' "$MAIN_PACKAGE" -g '*.kt' | sort
)
expected_location_projection_writers=(
  "$MAIN_PACKAGE/data/db/InventoryBalanceDao.kt"
  "$MAIN_PACKAGE/data/repository/LocalInventoryCommandEngine.kt"
)
if [[ "${location_projection_writers[*]}" != "${expected_location_projection_writers[*]}" ]]; then
  printf 'Unexpected location projection writer:\n%s\n' "${location_projection_writers[*]}" >&2
  fail "location projection writes must pass through LocalInventoryCommandEngine"
fi

mapfile -t transfer_projection_writers < <(
  rg -l 'compareAndSet(InTransit|TransferReceipt)\(' "$MAIN_PACKAGE" -g '*.kt' | sort
)
if [[ "${transfer_projection_writers[*]}" != "${expected_location_projection_writers[*]}" ]]; then
  printf 'Unexpected transfer projection writer:\n%s\n' "${transfer_projection_writers[*]}" >&2
  fail "in-transit projection writes must pass through LocalInventoryCommandEngine"
fi

if rg -q 'inventory_lots|InventoryLotEntity' "$MAIN_PACKAGE/data/db/ManagementControlDao.kt"; then
  fail "ManagementControlDao must not own Inventory 2.0 lot persistence"
fi

if rg -q 'stock_transfers|stock_transfer_lines|StockTransfer' "$MAIN_PACKAGE/data/db/ManagementControlDao.kt"; then
  fail "ManagementControlDao must not own Inventory 2.0 transfer persistence"
fi

[[ ! -e "$MAIN_PACKAGE/ui/InventoryOperationsScreens.kt" ]] ||
  fail "legacy InventoryOperationsScreens god file must remain decomposed"

if rg -n 'InventoryLotRecord|LotRegistrationDraft|LotTransferDraft|on(CreateLocation|RegisterLot|TransferLot)' \
  "$MAIN_PACKAGE/ui/ManagementControlScreen.kt"; then
  fail "ManagementControl UI must redirect instead of owning inventory workflows"
fi

for bounded_query in 'suspend fun balances' 'suspend fun movements'; do
  rg -Fq "$bounded_query" "$MAIN_PACKAGE/data/db/InventoryReadDao.kt" ||
    fail "InventoryReadDao is missing $bounded_query"
done
[[ "$(rg -c 'LIMIT :limit OFFSET :offset' "$MAIN_PACKAGE/data/db/InventoryReadDao.kt")" -eq 2 ]] ||
  fail "inventory balance and movement read models must remain bounded in SQL"

mapfile -t migration_edges < <(
  rg -o 'Migration\([0-9]+, *[0-9]+\)' "$MAIN_PACKAGE/data/db/migration" -g '*.kt' \
    | sed -E 's/.*Migration\(([0-9]+), *([0-9]+)\)/\1-\2/' \
    | sort -V
)
[[ "${#migration_edges[@]}" -eq "$((schema_version - 1))" ]] ||
  fail "migration edge count must match the complete 1..$schema_version chain"
duplicate_migration_edges="$(printf '%s\n' "${migration_edges[@]}" | uniq -d)"
[[ -z "$duplicate_migration_edges" ]] || fail "duplicate migration edge: $duplicate_migration_edges"
for ((version = 1; version < schema_version; version++)); do
  expected_edge="$version-$((version + 1))"
  printf '%s\n' "${migration_edges[@]}" | rg -Fxq "$expected_edge" ||
    fail "missing migration edge $expected_edge"
done

if rg -n 'suspend fun post\(draft: SemanticJournalDraft\)' \
  "$MAIN_PACKAGE/data/repository/LocalAccountingPostingEngine.kt"; then
  fail "accounting posting requires an explicit actor/idempotency/correlation context"
fi

mapfile -t direct_draft_posters < <(
  rg -l 'postDraftEntry\(' "$MAIN_PACKAGE/data/repository" | sort
)
expected_draft_posters=(
  "$MAIN_PACKAGE/data/repository/LocalAccountingPostingEngine.kt"
)
if [[ "${direct_draft_posters[*]}" != "${expected_draft_posters[*]}" ]]; then
  printf 'Unexpected direct draft posting boundary:\n%s\n' "${direct_draft_posters[*]}" >&2
  fail "posted journals must pass through LocalAccountingPostingEngine"
fi

mapfile -t audit_entity_files < <(rg -l 'AuditLogEntity\(' "$PROJECT_ROOT/app/src/main" | sort)
expected_audit_files=(
  "$MAIN_PACKAGE/data/db/ControlEntities.kt"
  "$MAIN_PACKAGE/data/repository/LocalAuditEventWriter.kt"
)
if [[ "${audit_entity_files[*]}" != "${expected_audit_files[*]}" ]]; then
  printf 'Unexpected direct AuditLogEntity usage:\n%s\n' "${audit_entity_files[*]}" >&2
  fail "audit events must pass through LocalAuditEventWriter"
fi

if test -d "$PROJECT_ROOT/.git"; then
  git -C "$PROJECT_ROOT" diff --check
fi

echo "FOUNDATION_STATIC=PASS"
if ! python3 "$PROJECT_ROOT/scripts/verify-room-schema.py"; then
  echo "FOUNDATION_OVERALL=BLOCKED: current Room schema evidence is not verified." >&2
  exit 2
fi
echo "FOUNDATION_OVERALL=PASS"
