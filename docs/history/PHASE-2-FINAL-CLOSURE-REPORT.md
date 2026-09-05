# PHASE 2 — FINAL CLOSURE REPORT

## Status

- Baseline source commit: `68824773dc3587974b630fa550572cead4c8b978`
- Working branch: `phase2/final-closure`
- Scope: Phase 2 final closure patch only
- **PHASE 3 NOT STARTED**
- Android/Gradle runtime verification: **BLOCKED** in this environment by Gradle wrapper DNS/bootstrap failure
- Room schema evidence: **PENDING** because genuine Room-generated schema 53 is not available

## A. Accounting Upgrade

The existing `journal_entries` / `journal_lines` accounting journal was upgraded in place. No second or parallel accounting engine was introduced.

Before this patch, `journal_entries` had no structural numeric branch dimension and `branchId = null` could not distinguish organization-wide accounting from historical records whose branch was unknown.

Schema 53 adds to the existing journal header:

- `branchId: Long?`
- `accountingScope: String`
- index on `branchId`
- composite index on `(accountingScope, branchId, entryEpochDay)`

Canonical scope model:

- `BRANCH` → `branchId > 0`
- `ORGANIZATION` → `branchId = null`, truly organization-wide
- `UNASSIGNED_LEGACY` → `branchId = null`, branch cannot be proven from available source data

`LocalAccountingPostingEngine` persists this scope and branch on the same existing journal. Any reversal with `reversalOfEntryId` inherits the original journal scope and branch before persistence, preventing branch drift in reversal flows.

Generic posting defaults are fail-closed to `UNASSIGNED_LEGACY`; only flows with explicit evidence opt into `BRANCH` or `ORGANIZATION`. Manual journals retain explicit organization scope by default.

## B. Database / Migration

- Previous Room version: **52**
- Current Room version: **53**
- Latest migration: `MIGRATION_52_53`
- Migration file: `app/src/main/java/ir/restaurant/management/data/db/migration/AccountingBranchScopeMigration.kt`
- Migration registry: `ALL_MIGRATIONS` now includes `MIGRATION_52_53`
- Destructive migration fallback: **not introduced**

Migration 52→53 is non-destructive: existing journal rows and journal lines are retained; the migration adds header dimensions/indexes and updates only the new scope/branch fields.

### Deterministic historical backfill rules

The migration uses only existing numeric relational evidence:

1. Non-archive `DAILY_SALES` / COGS / their reversals → `daily_sales_summaries.branchId`.
2. `RECEIVABLE` / receivable reversal → `receivables.branchId`.
3. Receivable collection / collection reversal → `receivable_collections.receivableId → receivables.branchId`.
4. Generic reversal rows with `reversalOfEntryId` → exact scope/branch of original journal.
5. Anything not proven remains `UNASSIGNED_LEGACY` with `branchId = null`.

Phase-1 converted Daily Sales rows marked `isLegacyArchive=1` are deliberately excluded from branch backfill because the later Phase-2 `branchId=1` migration default is compatibility data, not historical proof.

**No legacy accounting journal was assigned to a branch by default or guess.**

No description text, note, user name, branch label, first/default branch, account title, timestamp, or restaurant name is used to infer a branch.

### Historical backfill counts

A real production/user database was not available in this execution environment, so actual installed-database migration counts are **NOT VERIFIED** and are not fabricated.

Host-side executable SQLite evidence uses the actual migration UPDATE statements extracted from Kotlin source. Its fixture contains 5 historical journal rows:

| Fixture source | Rows examined | Backfilled to branch | Left unassigned |
|---|---:|---:|---:|
| Daily Sales | 2 | 1 | 1 legacy archive |
| Receivable collection | 1 | 1 | 0 |
| Generic reversal | 1 | 1 | 0 |
| Unknown legacy | 1 | 0 | 1 |
| **Fixture total** | **5** | **3** | **2** |

A separate Android migration test was added for direct Receivable, Collection, non-archive Daily Sales, archive Daily Sales, unknown legacy, and reversal inheritance. That Android test is **NOT VERIFIED** here because Gradle could not bootstrap.

Legacy domains that currently expose only textual `branchName` rather than a deterministic numeric `branchId` are not force-mapped by this patch. Their historical journals remain unassigned unless a proven numeric relation exists.

## C. New Posting Scope

### Daily Sales

New Daily Sales revenue and COGS journals now explicitly post:

- `accountingScope = BRANCH`
- `branchId = dailySales.branchId`

Daily Sales reversal journals inherit the exact scope/branch of the original journal.

### Receivables / Collections

Credit-sale receivable debit lines remain part of the branch-scoped Daily Sales revenue journal. `Receivable` masters already persist the same numeric sale branch.

Receivable collection journals now explicitly post to the Receivable branch. Collection reversal journals inherit the original collection journal branch through `reversalOfEntryId`.

### Payroll

The current payroll data model has textual `branchName` but no authoritative numeric branch mapping. Therefore this patch does not guess a numeric branch:

- payroll batch scope `ALL` → `ORGANIZATION`
- other payroll scopes without deterministic numeric branch evidence → `UNASSIGNED_LEGACY`

This avoids silently charging a specific branch or Branch 1. A future branch-master migration can replace this limitation only with validated numeric mappings.

## D. Canonical Branch P&L

`AccountingDao.branchProfitLoss(...)` is the canonical branch accounting query. It reads only:

```text
journal_entries.accountingScope = 'BRANCH'
AND journal_entries.branchId = :branchId
AND status = 'POSTED'
```

Therefore:

- another branch is excluded,
- `ORGANIZATION` journals are excluded,
- `UNASSIGNED_LEGACY` journals are excluded from amounts,
- unassigned P&L-class lines are counted separately as data-quality evidence.

No separate P&L ledger/calculator was created. `LocalDailyManagementBriefService` now consumes this canonical accounting result rather than leaving operating expenses, payroll and estimated operating profit permanently null.

### Account treatment

The P&L uses the existing chart/mapping semantics:

- Sales Revenue (`4101`) + Service Revenue (`4103`) → Revenue
- Tax Payable (`2103`) → liability, **never Revenue**
- COGS (`5101`) → COGS
- Payroll expense roles (`6101`, `6113`, `6114`, `6115`) → Payroll
- Other `EXPENSE` accounts excluding COGS/payroll codes → Operating Expenses Excluding Payroll

Inventory purchase asset/payable postings are therefore not automatically treated as operating expense. Payroll is separated from other operating expense so it is not subtracted twice.

### Formula

```text
Revenue = Sales Revenue + Service Revenue
Gross Profit = Revenue - COGS
Estimated Operating Profit =
    Revenue
    - COGS
    - Operating Expenses Excluding Payroll
    - Payroll
```

Tax remains outside Revenue:

```text
Net Sales = Gross - Discount - Return
Revenue = Net Sales + Service Revenue
Amount To Settle = Revenue + Tax
P&L Revenue = Revenue
```

## E. Organization Expense

`ORGANIZATION` journals are not automatically allocated into any Branch P&L. This is intentional. No cost-center/allocation engine was added in this patch.

A headquarters or truly organization-wide expense remains organization-scoped until a separately designed allocation policy exists.

## F. P&L Data Quality

Branch P&L distinguishes zero from unavailable/incomplete data.

- Revenue completeness requires no unassigned revenue lines and reconciliation between canonical journal revenue and the branch Daily Sales aggregate.
- COGS completeness requires no unassigned historical COGS lines in the period.
- Expense completeness requires no unassigned operating-expense lines in the period.
- Payroll completeness requires no unassigned payroll lines in the period.

`estimatedOperatingProfitRial` is only populated when all required categories are complete. Otherwise it is `null` and `unavailableReason` identifies missing attribution categories.

True organization-wide entries do not cause a branch amount to be fabricated and are not silently allocated.

## G. Daily Management Brief

`LocalDailyManagementBriefService` now obtains Revenue, COGS, Operating Expenses, Payroll and Estimated Operating Profit from the canonical branch accounting query.

It still uses Daily Sales aggregates for sales composition/liquidity and uses them as an independent revenue reconciliation source. Tax is not counted as Revenue.

Previous Receivable/Aging and Actual Food Cost behavior is preserved; this patch does not introduce a new receivable, food-cost, POS, ordering, table, reservation or KDS engine.

## H. Executed Verification Evidence

### Host accounting closure verifier — EXECUTED / PASS

Command:

```bash
python scripts/verify-phase2-accounting-closure.py
```

Actual output:

```text
PHASE2_ACCOUNTING_CLOSURE_HOST_VERIFICATION=PASS
DETERMINISTIC_BACKFILL=PASS
BRANCH_PNL_ISOLATION=PASS
TAX_EXCLUDED_FROM_REVENUE=PASS
UNASSIGNED_LEGACY_NOT_ALLOCATED=PASS
```

This script executes the actual backfill UPDATE statements and actual `branchProfitLoss` SQL extracted from Kotlin source against SQLite. It is host evidence, not a replacement for Android/Room instrumentation.

### Room verifier self-test — EXECUTED / PASS

Command:

```bash
python scripts/test-verify-room-schema.py
```

Result:

```text
ROOM_SCHEMA_VERIFIER_TEST=PASS
```

The self-test confirms dynamic current-version detection and PENDING/PASS/FAIL behavior.

### Static project checks

Executed results:

```text
STATIC_CODE_QUALITY=PASS
CURRENT_ROOM_VERSION=53
LATEST_SCHEMA_FILE=50
ROOM_SCHEMA_EVIDENCE=PENDING
OVERALL_VERIFICATION=BLOCKED

FOUNDATION_STATIC=PASS
FOUNDATION_OVERALL=BLOCKED

DOCUMENTATION_SYNC=PASS
REPOSITORY_HYGIENE=PASS
STATIC_SECURITY_CONTROLS=PASS
RUNTIME_SECURITY=NOT_VERIFIED
```

The non-zero code-quality/foundation status is intentional because missing current Room schema evidence blocks an overall green result.

## I. Android / Gradle Build Sanity

A targeted build/test/schema-generation attempt was made; the full suite was **not** requested or executed by this Phase-2 closure contract.

Command attempted:

```bash
./gradlew --no-daemon \
  :app:kspDebugKotlin \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest \
  --tests ir.restaurant.management.accounting.BranchAccountingPnlTest \
  --rerun-tasks
```

Result: **BLOCKED before Gradle task execution**.

Actual blocker:

```text
Downloading https://services.gradle.org/distributions/gradle-8.13-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Therefore the following are **NOT VERIFIED** in this environment:

- Kotlin/Android compile for this closure patch
- targeted JVM test execution
- Android instrumentation migration/integration tests
- KSP/Room schema generation

No build/test was reported as successful without execution evidence.

## J. Room Schema Evidence

```text
Current Room Version = 53
Latest Migration = MIGRATION_52_53
Latest Generated Schema = 50.json
Expected Current Schema = 53.json
Room Schema Evidence = PENDING
```

`53.json` was **not** copied from an older schema, hand-authored, or version-edited. It must be generated by Room/KSP in a working Gradle/Android environment.

## K. Tests Added / Modified

Added:

- `app/src/test/java/ir/restaurant/management/accounting/BranchAccountingPnlTest.kt`
  - complete Branch 2 P&L: 125M revenue / 48M COGS / 12M OpEx / 9M payroll → 77M gross profit / 56M estimated operating profit
  - missing payroll → estimated operating profit unavailable
  - accounting scope compatibility

- `app/src/androidTest/java/ir/restaurant/management/data/db/AccountingBranchScopeMigration52To53Test.kt`
  - deterministic Daily Sales / Receivable / Collection backfill
  - archived compatibility Branch 1 is not trusted
  - unknown legacy remains unassigned
  - reversal inherits original branch

Modified:

- `app/src/androidTest/java/ir/restaurant/management/data/repository/AccountingPostingIntegrationTest.kt`
  - branch journal persists Branch 2
  - reversal preserves Branch 2
  - canonical Branch 2 P&L excludes Branch 1 and Organization expenses
  - Tax Payable does not enter Revenue

Added host executable evidence:

- `scripts/verify-phase2-accounting-closure.py`

The Android/JVM tests above are code-complete but **NOT VERIFIED by Gradle execution** due the bootstrap blocker described above.

## L. Regression / Scope Guards

Static search performed on production source:

- `branchId ?: 1L` → no match
- `branchId = 1L` → no match
- `activeSummaryByDay(1L` → no match
- `DEFAULT_BRANCH` → no match

Active source search found no returned RestaurantTable/RestaurantOrder/RestaurantReservation/KitchenTicket implementation. Remaining `KDS` mentions are comments inside the historical Phase-1 cleanup migration describing removed features, not active feature restoration.

No new brand, POS, table service, restaurant ordering, reservation, KDS, AI/LLM, dashboard redesign, or Phase-3 UI work was introduced.

## M. Files

### Added

- `app/src/main/java/ir/restaurant/management/data/db/migration/AccountingBranchScopeMigration.kt`
- `app/src/main/java/ir/restaurant/management/domain/accounting/BranchProfitAndLoss.kt`
- `app/src/test/java/ir/restaurant/management/accounting/BranchAccountingPnlTest.kt`
- `app/src/androidTest/java/ir/restaurant/management/data/db/AccountingBranchScopeMigration52To53Test.kt`
- `scripts/verify-phase2-accounting-closure.py`
- `PHASE-2-FINAL-CLOSURE-REPORT.md`

### Modified

- `app/src/main/java/ir/restaurant/management/data/db/AccountingDao.kt`
- `app/src/main/java/ir/restaurant/management/data/db/AccountingEntities.kt`
- `app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt`
- `app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt`
- `app/src/main/java/ir/restaurant/management/data/repository/LocalAccountingPostingEngine.kt`
- `app/src/main/java/ir/restaurant/management/data/repository/LocalDailyManagementBriefService.kt`
- `app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt`
- `app/src/main/java/ir/restaurant/management/data/repository/LocalHrPayrollService.kt`
- `app/src/main/java/ir/restaurant/management/data/repository/LocalReceivableService.kt`
- `app/src/main/java/ir/restaurant/management/domain/accounting/AccountingPosting.kt`
- `app/src/main/java/ir/restaurant/management/domain/accounting/JournalDraft.kt`
- `app/src/androidTest/java/ir/restaurant/management/data/repository/AccountingPostingIntegrationTest.kt`
- `scripts/test-verify-room-schema.py`
- `README.md`
- `CHANGELOG.md`
- `docs/ARCHITECTURE.md`
- `docs/DECISIONS.md`
- `docs/DEPENDENCY_AUDIT.md`
- `docs/FINAL_AUDIT_REPORT.md` (supersession note; historical audit evidence retained)
- `docs/HANDOVER.md`
- `docs/KNOWN_ISSUES.md`
- `docs/TESTING.md`

### Deleted

- None.

## N. Git Traceability

Baseline:

```text
68824773dc3587974b630fa550572cead4c8b978
```

Closure commits created before the report/documentation commit:

```text
91e9251 feat/accounting-journal-branch-scope
6291614 feat/branch-pnl-and-accounting-verification
```

The repository uses Git for change history; no source backup/copy files were created in the project tree.

## O. Known Limitations / Required Follow-up

1. **Room schema 53 evidence is PENDING.** Genuine `53.json` must be generated by Room/KSP.
2. **Gradle compile/targeted tests are NOT VERIFIED** because Gradle 8.13 could not be downloaded due DNS/network restrictions.
3. **Actual production backfill row counts are NOT VERIFIED** because no user production database was supplied/executed. Migration logic and synthetic SQLite fixtures were verified, but production counts must be collected during real migration verification.
4. Legacy operational domains that only store textual branch names cannot be deterministically converted to the numeric Phase-2 branch dimension by this patch. They are intentionally not guessed into a branch. A future authoritative branch-master mapping is required for complete historical allocation.
5. Organization-level expenses are intentionally excluded from Branch P&L until a separately approved allocation/cost-center model exists.

## Final Phase Status

**PHASE 2 FINAL CLOSURE PATCH IMPLEMENTED IN SOURCE**

**ROOM_SCHEMA_EVIDENCE=PENDING**

**GRADLE_TARGETED_VERIFICATION=BLOCKED**

**PHASE 3 NOT STARTED**
