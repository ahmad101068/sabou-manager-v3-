# PHASE 2 — Branch Port Report

## Executive result

**Baseline used = Version 53 Phase 2 Closure.**

The Branch Canonicalization work was **not** continued from the incorrect Room Version 2 source. The useful Branch changes were ported onto the Git baseline `origin/phase2/final-closure` at commit `e63c04b011524bfcb71b0256052a20631a604616`, whose `AppDatabase` declares Room version 53 and registers the existing migration registry.

The port appends a real non-destructive schema migration:

```text
53 -> 54
MIGRATION_53_54
```

The historical migration chain remains registered through `ALL_MIGRATIONS`. No historical migration was deleted or squashed.

**PHASE 3 NOT STARTED**

---

## 1. Baseline verification

| Check | Result |
|---|---|
| Baseline | `origin/phase2/final-closure` |
| Baseline commit | `e63c04b011524bfcb71b0256052a20631a604616` |
| Previous Room version | **53** |
| Baseline registration | `.addMigrations(*ALL_MIGRATIONS)` |
| `FullMigration1ToCurrentTest` present | **YES** |
| Historical schemas present | **YES** (`1.json`, `44.json`, `49.json`, `50.json`) |
| Version 2 source used as baseline | **NO** |

The Version 2 implementation was used only as a source of Branch-related code changes. Its database reset, `MIGRATION_1_2` final-migration model, and Version 2 verifier assumptions were not ported.

---

## 2. Database and migration chain

| Item | Result |
|---|---|
| Previous Room Version | `53` |
| Current Room Version | `54` |
| Latest migration | `MIGRATION_53_54` |
| Registered chain | `1 -> 2 -> ... -> 52 -> 53 -> 54` |
| Registry | `ALL_MIGRATIONS` |
| Destructive migration | **NOT USED** |
| Migration squash/reset | **NOT USED** |

Static registry verification found exactly 53 consecutive edges and no missing/extra migration edge:

```text
MIGRATION_CHAIN_STATIC=PASS
REGISTRY_LAST=[MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54]
```

Only two migration-area changes exist relative to the Version 53 baseline:

1. `AppMigrations.kt` appends `MIGRATION_53_54`.
2. `BranchCanonicalizationMigration.kt` adds the new 53 -> 54 migration.

**No previous migration was deleted or squashed.**

---

## 3. Schema evidence

The historical Room schema files from the Version 53 baseline are byte-preserved; `git diff` reports no change under `app/schemas`.

| Item | Result |
|---|---|
| Existing schemas preserved | **YES** |
| Existing exported versions | `1`, `44`, `49`, `50` |
| Latest generated schema file | `50.json` |
| Required current schema | `54.json` |
| `54.json` generated | **NO** |
| Fake/manual schema created | **NO** |
| `ROOM_SCHEMA_EVIDENCE` | **PENDING** |

Verifier output:

```text
CURRENT_ROOM_VERSION=54
LATEST_SCHEMA_FILE=50
ROOM_SCHEMA_EVIDENCE=PENDING
```

No `54.json` was copied, manually authored, or version-edited. It must be produced by Room in a complete Android build environment.

---

## 4. Canonical Branch Master

A single canonical Branch master is added:

```text
BranchEntity
branches
```

Identity policy:

- canonical business identity = `branchId` / `BranchEntity.id`;
- `globalId` has a unique index;
- `(organizationId, code)` has a unique Room index and repository-level deterministic duplicate validation;
- `name` is display metadata, not identity;
- rename preserves IDs and business references;
- active/inactive state is explicit;
- referenced branches are deactivated rather than hard-deleted through the new repository API;
- no second/parallel Branch master was introduced.

Canonical resolution is centralized in `CanonicalBranchResolver` and `LocalBranchRepository`.

The Version 53 baseline did not contain an existing Branch master entity/table, so the new `branches` table is the canonical master rather than a duplicate of another Branch system.

---

## 5. MIGRATION_53_54 behavior

`MIGRATION_53_54` is additive/non-destructive and performs the following operations:

1. Creates the canonical `branches` table and indexes.
2. Preserves already-numeric Branch identities from trusted Version 53 tables by creating canonical rows with the same numeric IDs.
3. Removes the historical `DEFAULT 1` from `daily_sales_summaries.branchId` by rebuilding the table from its real `sqlite_master` definition together with direct FK children, indexes, and triggers.
4. Adds nullable canonical `branchId` columns to legacy string-based domains where required:
   - `employees`
   - `employment_assignments`
   - `payroll_batches`
   - `storage_locations`
   - `purchases`
   - `fixed_assets`
   - `work_schedules`
   - `sales_cash_reconciliations`
5. Creates indexes for the new Branch columns.
6. Backfills legacy text keys only through exact normalized deterministic keys.
7. Leaves blank, unknown, or ambiguous legacy text unresolved (`branchId = null`).
8. Does not use `branchId = 1L`, `?: 1L`, name-to-1, or destructive fallback.

### Backfill evidence

No live production Version 53 database was supplied with the source archive, therefore **production row counts cannot be truthfully reported**. They are not invented here.

A host SQLite fixture simulation of the 53 -> 54 algorithm was executed with numeric Branch data, deterministic legacy text, deliberately ambiguous case variants, Daily Sales child FKs, an index, a trigger, and preserved rows:

```text
MIGRATION_53_54_SQLITE_SIMULATION=PASS
fixture numeric Branch identities preserved = 1
fixture legacy text rows examined = 5
fixture legacy text rows backfilled = 3
fixture ambiguous/unassigned rows = 2
```

Production migration counts:

| Metric | Production count |
|---|---|
| Rows examined | `N/A — no live Version 53 DB supplied` |
| Rows backfilled | `N/A — no live Version 53 DB supplied` |
| Rows organization-scoped | `N/A — accounting scope is not inferred from unknown Branch strings` |
| Rows unassigned | `N/A — determined at migration runtime` |
| Rows ambiguous | `N/A — determined at migration runtime` |

---

## 6. Branch changes ported from the Version 2 implementation

The useful Branch work ported onto Version 53 includes:

- `BranchEntity`, `BranchDao`, Branch domain models, `LocalBranchRepository` and `CanonicalBranchResolver`;
- canonical `branchId` identity for new business logic;
- active Branch validation for new Branch-specific transactions;
- Daily Sales functional Branch selector and removal of manual numeric Branch ID input;
- Branch-aware Daily Sales posting/reversal;
- Branch-aware Accounting posting validation and reversal attribution;
- Branch-aware Payroll batch/posting;
- Employee/Employment Assignment canonical Branch fields;
- Work Schedule canonical Branch field;
- Storage Location canonical Branch field;
- Waste posting from real Storage Location Branch;
- Purchase canonical Branch attribution without reclassifying inventory purchase as operating expense;
- Asset canonical Branch attribution for acquisition/depreciation/maintenance/impairment/disposal/sale;
- Receivable collection/reversal Branch preservation;
- Cash Reconciliation canonical attribution without inventing a P&L posting;
- Management Control validation for real Branch-specific actions;
- purchase price-control query filtering by canonical `purchases.branchId`;
- Branch P&L production-flow test coverage;
- canonical Branch rename/inactive behavior tests.

---

## 7. Changes intentionally NOT ported from Version 2

The following Version 2 assumptions/artifacts were deliberately rejected:

- Room version reset to `1`/`2`;
- `MIGRATION_1_2` as the final migration;
- `.addMigrations(MIGRATION_1_2)` as the only production/backup migration registration;
- any clean-baseline/fresh-start-only database model;
- migration squashing or deletion of Version 53 history;
- fake/manual `54.json` schema evidence;
- a parallel Accounting core;
- a parallel Branch core;
- final Phase 3 UI redesign;
- guessed multi-branch Payroll allocation;
- guessed Branch attribution from bank accounts;
- guessed Branch attribution for PO/GR flows that do not carry a trustworthy Branch source.

---

## 8. Accounting invariants

The existing Version 53 Accounting Journal remains canonical.

Scopes remain:

```text
BRANCH            -> branchId != null
ORGANIZATION      -> branchId = null
UNASSIGNED_LEGACY -> branchId = null
```

For new commands/drafts, the default scope is now `ORGANIZATION`, not `UNASSIGNED_LEGACY`.

A new `BRANCH` posting must reference an active canonical Branch. Reversals preserve the original Journal scope and `branchId`, including historical attribution after a Branch is deactivated.

`UNASSIGNED_LEGACY` remains a historical persisted-data concept and is not a silent fallback for new Branch-specific transactions.

---

## 9. Daily Sales

- `DailySalesSummaryEntity.branchId` remains the persisted Branch identity.
- create/update/confirm/post validate an active canonical Branch.
- revenue and COGS Journals use `AccountingScope.BRANCH` plus the real `branchId`.
- reversals preserve the original Journal scope and Branch.
- the Daily Sales UI no longer asks a user to type a numeric Branch ID.
- the functional selector is populated from active canonical Branches.
- no active Branch => save is blocked explicitly.
- the historical SQLite `DEFAULT 1` is removed by the 53 -> 54 migration rather than becoming a hidden fallback.

---

## 10. Payroll

Production Payroll is Branch-aware:

- `PayrollBatchEntity` and `PayrollBatchDraftV2` carry `branchId`;
- `scope = BRANCH` resolves a canonical Branch ID (legacy text is accepted only through the deterministic resolver for compatibility);
- employee selection uses `employee.branchId`, not string equality;
- Branch Payroll accrual posts `AccountingScope.BRANCH` + Batch `branchId`;
- `scope = ALL` posts `AccountingScope.ORGANIZATION` + `branchId = null`;
- new Branch Payroll with an invalid/unresolved Branch fails before posting;
- it does not silently become `UNASSIGNED_LEGACY`;
- multi-branch Payroll allocation is not guessed where an exact allocation model does not exist.

`BranchPayrollPostingIntegrationTest` uses the real `LocalHrPayrollService` production flow rather than direct Journal injection.

---

## 11. Employee, scheduling, storage and inventory

- `EmployeeEntity.branchId` is added and used by new Branch-sensitive business logic.
- `EmploymentAssignmentEntity.branchId` is added.
- personnel writes resolve legacy Branch text to canonical identity before business use where mapping is available/required.
- `WorkScheduleEntity.branchId` is added.
- `StorageLocationEntity.branchId` is added.
- inventory location writes resolve canonical Branch identity.
- inventory counts inherit Branch semantics through the Storage Location model where Branch-specific.
- legacy string fields remain compatibility/display/migration fields; new Branch decisions do not use them as final identity.

---

## 12. Waste and Operating Expense producers

Waste accounting reads the actual Storage Location:

- Branch Location -> `BRANCH + location.branchId`;
- Location without Branch -> `ORGANIZATION`;
- no Branch is guessed.

A real Branch-specific Operating Expense production path is covered through Asset Maintenance:

```text
LocalAssetRepository.recordMaintenance
-> Accounting Journal BRANCH + asset.branchId
-> Branch P&L operating expense
```

`BranchExpensePostingIntegrationTest` verifies the production producer path in source. Android execution is pending environment availability.

---

## 13. Purchases / Procurement

- `PurchaseEntity.branchId` added.
- Branch-specific purchase posting uses canonical Branch attribution.
- purchase settlement preserves Purchase Branch.
- purchase/settlement reversals preserve original Journal scope/Branch.
- Inventory Purchase remains inventory-asset/payable accounting and is **not** treated as direct Operating Expense.
- Procurement invoice matching preserves a canonical Branch when the invoice has a trustworthy Branch source.
- current PO/GR receiving aggregate has no trustworthy Branch field, therefore it remains `ORGANIZATION` rather than guessing.

---

## 14. Assets

`FixedAssetEntity.branchId` is canonicalized for applicable assets.

Branch attribution is propagated to:

- acquisition;
- depreciation;
- maintenance;
- impairment;
- disposal/sale.

Organization assets remain organization-scoped.

A rename test verifies that changing Branch display name does not alter Asset or Journal reference IDs.

---

## 15. Receivables / Collections

Previously approved Receivable unification is preserved.

- Receivable `branchId` remains the canonical Branch identity.
- collection posts to the Receivable Branch.
- collection reversal preserves original Journal scope/Branch.
- historical attribution can still read an existing inactive Branch by ID.
- Collection is not reclassified as Revenue.
- exact Aging allocation and previous credit-sale reversal corrections remain intact.

---

## 16. Management Control and Treasury

Management Issue/Task/Checklist Branch-specific write paths validate canonical Branch IDs.

Version 53 also has explicit `UNSCOPED_*` management signals for historical sources without reliable Branch attribution. Those records retain `branchId = 0` only as the pre-existing **unscoped sentinel**; Branch-specific issues must use a real canonical Branch. This avoids converting an unscoped legacy signal into a guessed Branch.

Treasury remains `ORGANIZATION` when the only available evidence is a bank/cash account. A Bank Account is not treated as proof of Branch identity.

Cash Reconciliation stores canonical Branch attribution when supplied or deterministically derived from a single closed sales-day Branch. Multiple possible Branch closures without an explicit Branch fail instead of guessing. No accounting expense producer was invented for Cash Reconciliation where the existing Version 53 model has none.

---

## 17. Accounting producer matrix

| Producer / path | Branch source | Resulting scope | Port status |
|---|---|---|---|
| Daily Sales revenue | Daily Sales canonical Branch | `BRANCH` | Ported |
| Daily Sales COGS | Daily Sales canonical Branch | `BRANCH` | Ported |
| Daily Sales reversal | Original Journal | original scope + Branch | Preserved |
| Receivable collection | Receivable `branchId` | `BRANCH` | Preserved/canonical |
| Collection reversal | Original Journal | original scope + Branch | Preserved |
| Payroll Branch | Payroll Batch canonical `branchId` | `BRANCH` | Ported |
| Payroll ALL | Organization-wide Batch | `ORGANIZATION` | Ported |
| Employee advance/settlement | Employee `branchId` when assigned | `BRANCH` / `ORGANIZATION` | Ported |
| Waste | Storage Location `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Purchase | Purchase canonical `branchId` | `BRANCH` / `ORGANIZATION` | Ported; not OpEx |
| Purchase settlement | Purchase `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Purchase reversal | Original Journal | original scope + Branch | Ported |
| Procurement invoice | Invoice Branch source | `BRANCH` / `ORGANIZATION` | Ported when source exists |
| Procurement PO/GR | no trustworthy Branch source | `ORGANIZATION` | Preserved; no guessing |
| Asset acquisition | Asset `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Asset depreciation | Asset `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Asset maintenance | Asset `branchId` | `BRANCH` / `ORGANIZATION` | Ported; real OpEx producer |
| Asset impairment | Asset `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Asset sale/disposal | Asset `branchId` | `BRANCH` / `ORGANIZATION` | Ported |
| Treasury V2 | account alone is not Branch evidence | `ORGANIZATION` | Explicit |
| Manual Journal | explicit caller scope | `ORGANIZATION` by default | No legacy fallback |
| Legacy unverifiable Journal | historical data only | `UNASSIGNED_LEGACY` | Preserved/excluded from Branch P&L |

---

## 18. Branch P&L and preserved financial semantics

The Version 53 canonical Accounting Journal remains the P&L source. Branch P&L requires:

```text
accountingScope = BRANCH
AND branchId = :branchId
```

Preserved results:

- Organization expenses do not leak into Branch P&L.
- `UNASSIGNED_LEGACY` does not leak into Branch P&L.
- Tax is not Revenue.
- Net Sales semantics remain Gross - Discount - Return.
- Revenue remains Net Sales + Service Revenue where applicable.
- Amount To Settle retains Revenue + Tax semantics.
- COGS is not double-counted as Operating Expense.
- Payroll is reported/subtracted separately and not double-counted in Operating Expense.
- Inventory Purchase is not direct Operating Expense.
- actual Food Cost semantics are preserved; actual is not forced to theoretical.
- Receivable Collection is not Revenue.

Host verifier result:

```text
PHASE2_ACCOUNTING_CLOSURE_HOST_VERIFICATION=PASS
DETERMINISTIC_BACKFILL=PASS
BRANCH_PNL_ISOLATION=PASS
TAX_EXCLUDED_FROM_REVENUE=PASS
UNASSIGNED_LEGACY_NOT_ALLOCATED=PASS
```

The existing P&L integration scenario was rewritten to use real production services for Revenue/COGS, Asset Maintenance OpEx and Payroll rather than directly injecting already-correct Journal rows.

---

## 19. Tests

### Preserved

- `FullMigration1ToCurrentTest` — unchanged from the Version 53 baseline; current version is read from `APP_DATABASE_SCHEMA_VERSION` and therefore now targets 54.
- historical migration tests — not deleted;
- Phase 1 / Phase 2 tests — no existing test file was deleted;
- Receivable tests — preserved;
- Food Cost tests — preserved;
- Daily Sales tests — preserved;
- P&L tests — preserved.

### Added / ported

- `Migration53To54Test`
- `BranchPayrollPostingIntegrationTest`
- `BranchExpensePostingIntegrationTest`
- `MultiBranchProfitAndLossIntegrationTest`
- `CanonicalBranchIdentityTest`
- `BranchRenameReferenceIntegrityTest`
- `BranchPurchasePostingIntegrationTest`

### Updated production-flow/regression coverage

- `Phase2CorrectionIntegrationTest.dailyBriefReadsRealCogsExpensesPayrollAndEstimatedProfit`
- `AccountingPostingIntegrationTest`
- `AssetLifecycleIntegrationTest`
- `InventoryWasteWorkflowIntegrationTest`
- `DailySalesReversalIntegrationTest`
- enterprise permission/UI fixtures to create and select canonical Branches.

No test file from the Version 53 baseline was deleted.

---

## 20. Targeted validation actually executed

### PASS

```text
MIGRATION_CHAIN_STATIC=PASS
MIGRATION_53_54_KOTLIN_SYNTAX_COMPILE=PASS
scripts/verify-branch-port-migration.py = PASS (`MIGRATION_53_54_SQLITE_SIMULATION=PASS`)
git diff --check = PASS
scripts/test-verify-room-schema.py = PASS
scripts/verify-documentation.py = PASS
scripts/verify-repository-hygiene.py = PASS
scripts/verify-security.py = PASS (static controls; runtime not verified)
scripts/verify-phase2-accounting-closure.py = PASS
```

Additional preservation checks:

- no historical Room schema file changed;
- no previous migration file was deleted;
- `FullMigration1ToCurrentTest` is unchanged;
- no `fallbackToDestructiveMigration` exists in production source;
- no active production POS/Table/KDS implementation was found;
- no prohibited source backup artifact was found;
- generated `.gradle/` created by the attempted build was removed before handoff.

### Expected/PENDING schema gate

```text
scripts/verify-room-schema.py
CURRENT_ROOM_VERSION=54
LATEST_SCHEMA_FILE=50
ROOM_SCHEMA_EVIDENCE=PENDING
exit = 2
```

`verify-code-quality.py` reports static code quality PASS but overall BLOCKED only because current Room schema evidence is pending. `verify-foundation.sh` similarly reports static foundation PASS but overall BLOCKED on missing Room-generated `54.json`.

### Gradle compile attempt

Gradle 8.13 supplied for this task was successfully launched under JDK 21. The requested compile did **not** reach Android/Kotlin compilation because the Android Gradle Plugin dependency was not available in local caches and this environment cannot resolve external Maven/Google hosts.

Attempted:

```text
/mnt/data/tools/gradle-8.13/bin/gradle --no-daemon --stacktrace :app:compileDebugKotlin
```

Actual configuration failure:

```text
Plugin [id: 'com.android.application', version: '8.13.0', apply: false] was not found
Plugin Repositories (could not resolve plugin artifact
'com.android.application:com.android.application.gradle.plugin:8.13.0')
Searched in: Google, MavenRepo, Gradle Central Plugin Repository
```

Direct host resolution checks also fail with DNS `Temporary failure in name resolution`. In addition, `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset and no Android SDK installation was found.

Therefore:

```text
ANDROID_GRADLE_COMPILE = NOT RUN TO COMPILATION / ENVIRONMENT BLOCKED
ANDROID_INSTRUMENTATION_TESTS = NOT RUN / ENVIRONMENT BLOCKED
ROOM_54_SCHEMA_GENERATION = PENDING
```

No claim of a successful Android build/test run is made.

---

## 21. Verifier truth

The verifier does not assume Version 1 as current.

Current source/verifier state:

```text
CURRENT_ROOM_VERSION=54
LATEST_SCHEMA_FILE=50
ROOM_SCHEMA_EVIDENCE=PENDING
```

Missing current schema is reported as PENDING/BLOCKED, never PASS.

---

## 22. Known limitations

1. Room-generated `54.json` is pending a complete Android build environment.
2. Android Room instrumentation execution of `Migration53To54Test` and `FullMigration1ToCurrentTest` is pending the Android/AGP environment.
3. Multi-branch Payroll allocation is not implemented because the current source has no exact allocation model; no Branch is guessed.
4. Organization-level expense allocation into individual Branch P&L is not implemented; organization expenses remain excluded.
5. Procurement PO/GR has no trustworthy Branch source in the current aggregate and remains organization-scoped.
6. The legacy Dashboard analytics layer still exposes branch-name filters; final Dashboard/UI redesign belongs to Phase 3. Core Accounting/P&L identity uses canonical `branchId`.
7. ALTER-added nullable Branch columns rely on production service validation rather than retrofitted SQLite FK constraints on the existing tables; the canonical Branch table itself is authoritative and new write paths validate identity.
8. Production migration row counts are unavailable without a real Version 53 database file; this report provides fixture evidence instead of fabricated counts.

---

## 23. Data preservation statement

The source implementation of `MIGRATION_53_54` is non-destructive and its host SQLite fixture simulation preserves parent/child rows, existing numeric Branch IDs, indexes and triggers while removing only the historical Daily Sales Branch-1 default and adding canonical Branch structures.

A real Android/Room upgrade of an actual production Version 53 database has **not** been executed in this environment. Therefore runtime production-upgrade verification remains pending rather than being reported as a false PASS.

---

## 24. Definition of Done audit

| Requirement | Result |
|---|---|
| Correct Version 53 baseline used | **YES** |
| Database version was not reset | **YES** |
| Migration chain 1 -> ... -> 53 preserved | **YES** |
| New Branch migration appended | **YES — 53 -> 54** |
| No migration squashing | **YES** |
| Existing schemas preserved | **YES** |
| FullMigration1ToCurrentTest preserved | **YES** |
| Branch Canonicalization ported | **YES** |
| No duplicate Branch system | **YES** |
| Canonical `branchId` identity | **YES** |
| Daily Sales uses canonical Branch | **YES** |
| Daily Sales numeric-ID input removed | **YES** |
| Payroll uses canonical Branch | **YES** |
| Payroll ALL = ORGANIZATION | **YES** |
| New Branch Payroll cannot become UNASSIGNED_LEGACY | **YES** |
| Real OpEx producer Branch-aware | **YES — Asset Maintenance** |
| Receivables Branch-aware | **YES** |
| Waste Branch-aware | **YES** |
| Assets Branch-aware where applicable | **YES** |
| Purchases Branch-aware where source exists | **YES** |
| Accounting Journal remains canonical | **YES** |
| Branch P&L canonical query preserved | **YES** |
| Organization expense excluded from Branch P&L | **YES** |
| Legacy unassigned excluded from Branch P&L | **YES** |
| Tax excluded from Revenue | **PRESERVED / HOST VERIFIER PASS** |
| Receivable fixes preserved | **YES** |
| Food Cost fixes preserved | **YES** |
| No POS/Table/KDS regression | **YES — static source scan** |
| Foundation verifier no longer assumes version 1 | **YES** |
| Current schema evidence honest | **YES — PENDING** |
| Migration53To54Test exists | **YES — execution pending** |
| Full Migration runtime test executed to 54 | **NO — environment blocked** |
| Android compile completed | **NO — environment blocked before compilation** |
| No source backup | **YES** |
| Phase 3 started | **NO** |

---

## 25. Final handoff

Expected source handoff:

```text
restaurant-management-phase-2-source-branch-ported.zip
```

Report:

```text
PHASE-2-BRANCH-PORT-REPORT.md
```

The handoff ZIP excludes build outputs, `.gradle/`, Android SDK/toolchains, APK/AAB and temporary packages. Git history may be retained in the handoff archive because the requester explicitly asked to keep `.git` in the ZIP; it is not used as a build artifact.

**PHASE 3 NOT STARTED**

**STOP — wait for audit approval.**
