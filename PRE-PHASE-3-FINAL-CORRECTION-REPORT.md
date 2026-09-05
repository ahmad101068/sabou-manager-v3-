# PRE-PHASE-3 FINAL CORRECTION REPORT

## Scope

SOURCE_BASELINE=restaurant-management-pre-phase3-standardized-source.zip
SOURCE_BASELINE_COMMIT=aaff20545859b231d30a43054f2c9cc2ba05e71f
SCOPE=A Dashboard canonical branch identity; B stale Phase2ManagementRules.kt test paths; C false-green standardization verifier
PHASE_3_STARTED=NO

No database rebase, migration edit, financial formula rewrite, dependency upgrade, security/KDF change, permission change, navigation redesign, or Phase 3 UI work was performed.

## A. Dashboard canonical branch identity

EVIDENCE=DashboardRepository.observeRange accepted branchName:String?; DashboardViewModel stored MutableStateFlow<String?> branchName; DashboardScreen emitted String? branch selection; availableBranches was List<String>.
ROOT_CAUSE=Dashboard branch selector had remained connected to legacy analytics name filtering after Canonical Branch Master was introduced.
AFFECTED_FILES=DashboardRepository.kt; DashboardAnalyticsDao.kt; DashboardViewModel.kt; DashboardScreen.kt; RestaurantManagementApp.kt; DashboardBranchFilteringIntegrationTest.kt
MINIMAL_FIX=Carry branchId:Long? from UI -> ViewModel -> Repository. Resolve display name from canonical branches only at DashboardRepository compatibility boundary. Use branchId directly in DashboardAnalyticsDao for tables that already have branchId; retain branchName only for legacy analytics tables that do not have branchId.
REGRESSION_TEST=DashboardBranchFilteringIntegrationTest now creates two real BranchEntity rows with generated IDs, proves ID isolation, rename stability for ID-backed dashboard data, duplicate-display-name determinism, and retains legacy sales compatibility assertions before rename.

Dashboard selection identity before = branchName: String?
Dashboard selection identity after = branchId: Long?
Legacy compatibility boundary = DashboardRepository: branchId -> canonical Branch lookup -> selectedBranchName display metadata -> DashboardAnalyticsDao only for legacy tables without branchId. Tables already carrying branchId are filtered by branchId.
Available branch options after = DashboardBranchOption(id: Long, name: String, isActive: Boolean)
DashboardViewModel selector after = selectBranch(branchId: Long?)
Dashboard UI callback after = onBranchSelected: (Long?) -> Unit
No default Branch 1 = YES

The obsolete DashboardAnalyticsDao.observeBranches(): Flow<List<String>> contract was removed after usage proof showed no remaining caller.

## B. Stale test path correction

Stale path before = app/src/main/java/ir/restaurant/management/data/repository/Phase2ManagementRules.kt
Current path after = app/src/main/java/ir/restaurant/management/data/repository/ManagementRules.kt
Affected test = app/src/test/java/ir/restaurant/management/phase2/FinalCorrectionBranchCompositionContractTest.kt
Test deleted/ignored/weakened = NO
Business assertions changed = NO
MISSING_PROJECTFILE_TARGETS=0

Targeted JVM execution:
- MultiBranchManagementRuleTest = PASS
- LegacyOverdueReceivableRuleTest = PASS

## C. Standardization verifier correction

The verifier no longer prints canonical branch identity without evidence. It now verifies all of the following before PASS:
- DashboardRepository public selection contract accepts branchId: Long? and does not accept branchName as selection identity.
- DashboardSnapshot contains selectedBranchId.
- Available branch choices carry canonical IDs rather than List<String>.
- DashboardRepository gets branch choices from canonical BranchDao.
- Dashboard analytics boundary receives branchId.
- DashboardViewModel stores/selects Long? branchId and has no String branch selection state.
- Dashboard UI callback is (Long?) -> Unit, compares selectedBranchId to branch.id, and emits branch.id.
- RestaurantManagementApp binds dashboard::selectBranch.
- No legacy Dashboard Flow<List<String>> branch-option contract remains.
- Every projectFile("...") test target exists.

CANONICAL_BRANCH_IDENTITY_VERIFIER=PASS
CANONICAL_BRANCH_NEGATIVE_TEST=PASS

Negative-test method: `scripts/test-verify-pre-phase3-standardization.py` copies the repository to a temporary directory, mutates the DashboardRepository selector from `branchId: Long?` to `branchName: String?`, runs the real verifier, requires a non-zero exit, and deletes the temporary copy. The original source is never modified by the negative test.

## Dashboard targeted integration evidence

DASHBOARD_BRANCH_ID_TEST=PASS
DASHBOARD_BRANCH_RENAME_TEST=PASS
DASHBOARD_DUPLICATE_DISPLAY_NAME_TEST=PASS
DASHBOARD_ANDROIDTEST_SOURCE_COMPILE=PASS
DASHBOARD_ANDROIDTEST_APK_ASSEMBLE=PASS

Evidence consists of:
1. The real Android instrumentation test `DashboardBranchFilteringIntegrationTest` using Room `AppDatabase` + real `BranchEntity` master IDs; its source compiles and packages successfully into the AndroidTest APK.
2. A runtime host SQLite equivalent of the corrected compatibility/filtering boundary executed during this correction: Branch A and Branch B generated distinct IDs; branchId-backed purchase data isolated 1000/3000/4000; rename preserved ID-backed data; duplicate display names remained deterministic by ID; legacy name-based sales compatibility returned 100/300/400 before rename.

Connected-device instrumentation execution was not performed because this build environment has no attached Android device/emulator. Full Runtime/Business/Instrumentation execution remains reserved for Phase 3; no runtime PASS beyond the targeted host SQLite equivalent is claimed.

## Database freeze

ROOM_VERSION=54
MIGRATION_CHAIN_UNCHANGED=YES
MIGRATION_EDGE_COUNT=53
MIGRATION_FIRST_EDGE=1->2
MIGRATION_LAST_EDGE=53->54
NEW_MIGRATION_CREATED=NO
MIGRATIONS_EDITED=NO
SCHEMA_UNCHANGED=YES
ROOM_SCHEMA_SHA256=4b2678a478b05f90e037ebe70c928c13a701d1eef8e377c332b6b54822898c04
BASELINE_ROOM_SCHEMA_SHA256=4b2678a478b05f90e037ebe70c928c13a701d1eef8e377c332b6b54822898c04

## Business Core freeze

FINANCIAL_FORMULAS_CHANGED=NO
ACCOUNTING_ARCHITECTURE_CHANGED=NO
RECEIVABLE_ARCHITECTURE_CHANGED=NO
FOOD_COST_ARCHITECTURE_CHANGED=NO
P&L_ARCHITECTURE_CHANGED=NO
DAILY_SALES_SEMANTICS_CHANGED=NO
COLLECTION_SEMANTICS_CHANGED=NO
PAYROLL_CORE_CHANGED=NO
INVENTORY_CORE_CHANGED=NO
PERMISSIONS_CHANGED=NO
DEPENDENCIES_CHANGED=NO
SECURITY_KDF_CHANGED=NO

The only data-query behavior changed is Dashboard branch filtering: branchId is now used where the existing V54 table already owns branchId. Aggregate formulas and monetary calculations are unchanged.

## Regression gates

ROOM_SCHEMA_VERIFIER=PASS
- CURRENT_ROOM_VERSION=54
- LATEST_SCHEMA_FILE=54
- ROOM_SCHEMA_EVIDENCE=PASS

DOCUMENTATION_VERIFIER=PASS
FOUNDATION=PASS
- FOUNDATION_STATIC=PASS
- FOUNDATION_OVERALL=PASS

CODE_QUALITY=PASS
- STATIC_CODE_QUALITY=PASS
- OVERALL_VERIFICATION=PASS

REPOSITORY_HYGIENE=PASS
- BUILD_OUTPUTS_IN_REPOSITORY=NONE
- BACKUP_COPY_ARTIFACTS=NONE

MIGRATION_53_54_HOST_VERIFICATION=PASS
- LEGACY_ONLY=PASS
- REAL_ONLY=PASS
- MIXED=PASS
- SAME_ID_LEGACY_AND_REAL=PASS
- DATA_PRESERVATION=PASS

ACCOUNTING_CLOSURE_VERIFICATION=PASS
- DETERMINISTIC_BACKFILL=PASS
- BRANCH_PNL_ISOLATION=PASS
- TAX_EXCLUDED_FROM_REVENUE=PASS
- UNASSIGNED_LEGACY_NOT_ALLOCATED=PASS

STATIC_SECURITY=PASS
RUNTIME_SECURITY=NOT_VERIFIED
PRE_PHASE3_STANDARDIZATION=PASS

## Scans

FORBIDDEN_ACTIVE_POS_TABLE_KDS=0
FORBIDDEN_BRANDS_ACTIVE_MAIN=0
BACKUP_SOURCE_FILES=0
DEFAULT_BRANCH_1_ACTIVE_SOURCE=0
STALE_PHASE2_MANAGEMENT_RULES_PATHS=0
MISSING_PROJECTFILE_TARGETS=0

## Compile / targeted validation

MAIN_KOTLIN_COMPILE=PASS
UNIT_TEST_SOURCE_COMPILE=PASS
ANDROID_TEST_SOURCE_COMPILE=PASS
TARGETED_STALE_PATH_JVM_TESTS=PASS
ANDROID_TEST_APK_ASSEMBLE=PASS
CANONICAL_BRANCH_NEGATIVE_TEST=PASS

Full Runtime/Business/Instrumentation suite = DEFERRED_TO_PHASE3

## Changed files and reasons

- `app/src/main/java/ir/restaurant/management/data/repository/DashboardRepository.kt` — change public selector to branchId, source options from Branch master, keep name only as compatibility/display metadata.
- `app/src/main/java/ir/restaurant/management/data/db/DashboardAnalyticsDao.kt` — accept branchId and use it for existing branchId-bearing tables; retain branchName for legacy-only analytics; remove unused String-only branch list.
- `app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt` — branch selection state and action changed from String? to Long?.
- `app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt` — ID-based callback, chip key and selection comparison.
- `app/src/main/java/ir/restaurant/management/ui/RestaurantManagementApp.kt` — bind DashboardScreen to dashboard::selectBranch.
- `app/src/androidTest/java/ir/restaurant/management/data/repository/DashboardBranchFilteringIntegrationTest.kt` — real Branch master ID, rename, duplicate-name and isolation assertions.
- `app/src/test/java/ir/restaurant/management/phase2/FinalCorrectionBranchCompositionContractTest.kt` — two stale source paths corrected; assertions unchanged.
- `scripts/verify-pre-phase3-standardization.py` — evidence-based canonical branch checks and projectFile target validation.
- `scripts/test-verify-pre-phase3-standardization.py` — reproducible negative verifier self-test in a temporary copy.
- `PRE-PHASE-3-FINAL-CORRECTION-REPORT.md` — this correction evidence.

No other source area is intentionally changed.

## Final status

FINAL CORRECTION IMPLEMENTED
READY FOR AUDIT
PHASE 3 NOT STARTED
