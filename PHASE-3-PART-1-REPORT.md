# Phase 3 — Part 1 Report

## Scope

This checkpoint implements the agreed Phase 3 split **Part 1: Gate 0 + Foundation** only. It is not the final Phase 3 release candidate and does not claim final production approval.

Baseline source: `restaurant-management-pre-phase3-final-corrected-source.zip`  
Embedded baseline commit: `290b1dd4c8e08e506aed3e2e679e370e4a16f5ff`

## Gate 0 — Dashboard Branch Rename Compatibility

### Bug evidence

- **BUG_EVIDENCE:** Legacy dashboard analytics used mutable textual branch fields such as `sales_invoices.branchName` and `customers.branch` at the compatibility boundary. A branch rename could therefore detach historical legacy rows from the branch-selected dashboard.
- **ROOT_CAUSE:** Legacy rows did not carry canonical `branchId`; the compatibility path converted canonical selection back to the branch's current display name.
- **AFFECTED_DOMAIN:** Dashboard legacy analytics compatibility only.
- **MINIMAL_FIX:** Room 54→55 adds a stable legacy alias read model keyed by canonical `branchId`. Create/Rename records historical aliases; dashboard legacy queries resolve aliases deterministically and never return UI/ViewModel/Repository selection to string identity. Historical financial rows are not rewritten.
- **REGRESSION_TEST:** `Migration54To55Test`, `DashboardBranchRenameCompatibilityIntegrationTest`, ambiguity-isolation coverage, and `FullMigration1ToCurrentTest` current-schema assertion.

### Schema evidence

- Room version: **55**
- Generated schema: `app/schemas/ir.restaurant.management.data.db.AppDatabase/55.json`
- Schema SHA256: `f75fae9ab184e7f6de5ac0a8a9ddd0702f8f6a5f7cb3dc818c5d13bd2427d7f2`
- Schema generation: **real Room/KSP generation**; not manually authored.
- 54→55 structural comparison:
  - existing v54 tables: 121
  - v55 tables: 122
  - added table only: `branch_legacy_aliases`
  - existing tables removed: 0
  - existing table definitions changed: 0
- `MIGRATION_54_55` exists and preserves historical source rows.
- KSP regeneration after staging generated schema produced no schema worktree drift.

## Gate 0 — QA Verifier

`scripts/verify-dashboard-ux.py` was corrected rather than bypassed:

- current workflow discovery no longer depends on stale `.github/workflows/build-apk.yml` naming;
- the final verification predicates now execute before the final failure gate;
- a real false-green path in the prior script was removed;
- no `continue-on-error`, `|| true`, or assertion weakening was introduced.

Final result: `DASHBOARD_UX_VERIFICATION=PASS`.

## Foundation UI

Implemented the Phase 3 foundation needed by Part 2:

- five canonical top-level destinations: Home / Control / Operations / Finance / More;
- canonical hub routing and bottom-navigation mapping;
- reusable `ManagementDataGrid` foundation with view/edit/warning/error row states, money/quantity/status/trend cells, command bar, summary footer, mobile smart row, editable numeric cells, and inline warnings;
- canonical `CanonicalBranchSelector` using numeric `BranchRecord.id` as identity and branch name only as display text;
- stable-key lazy list behavior in the grid foundation.

## Baseline Test-Debt Evidence and Minimal Corrections

The first Part 1 full unit run exposed three failures. The exact three test classes were rerun against a pristine detached worktree at baseline commit `290b1dd4c8e08e506aed3e2e679e370e4a16f5ff`; all three failures reproduced on the untouched baseline.

Minimal corrections:

1. `BranchAccountingPnlTest`: expected numeric literals corrected from Int to Long; value and assertion semantics unchanged.
2. `DailySalesMixedSettlementTest`: two JUnit expression-body tests changed to Unit-returning block bodies; the same `assertFailsWith` assertions remain.
3. Cashier navigation regression: the existing Cashier role had legacy `SALES` permission but the new Daily Sales route requires `DAILY_SALES_VIEW`. Cashier received **view-only** Daily Sales access; no create/edit/confirm/post/void permission was granted.

No business formula was changed to make tests pass.

## Executed Verification — Part 1

| Gate | Result | Evidence |
|---|---|---|
| Room/KSP schema generation | PASS | real v55 `55.json` generated |
| `:app:compileDebugKotlin` | PASS | BUILD SUCCESSFUL in 1m 28s |
| Full `:app:testDebugUnitTest` | PASS | 333/333 passed; 0 failure; 0 error; 0 skipped |
| `:app:compileDebugAndroidTestKotlin` | PASS | BUILD SUCCESSFUL in 30s |
| `:app:lintDebug` | PASS | BUILD SUCCESSFUL in 2m 36s; 0 Error; 43 Warning; 9 Hint |
| `:app:assembleDebug` | PASS | BUILD SUCCESSFUL in 2m 7s |
| Schema regeneration drift vs generated staged v55 | PASS | no worktree drift under `app/schemas` |
| Room schema verifier | PASS | `ROOM_SCHEMA_EVIDENCE=PASS` |
| Pre-Phase3 standardization verifier | PASS | current Room 55, history preserved |
| Dashboard UX verifier | PASS | false-green removed |
| Branch migration host simulation | PASS | 53→54 fixtures preserved |
| Repository hygiene | PASS | no build/backup source artifacts tracked |
| Static security controls | PASS | runtime security not claimed in Part 1 |
| Static code quality | PASS | production_files=340, test_files=184 |
| Documentation verification + negative self-tests | PASS | Room 55 and version fields synchronized |
| Foundation verifier | PASS | `FOUNDATION_OVERALL=PASS` |
| `git diff --check` / cached diff check | PASS | whitespace validation clean |

### Unit test totals

- Suites: 136
- Tests: 333
- Passed: 333
- Failed: 0
- Errors: 0
- Skipped: 0

### Lint note

Lint reports 43 warnings and 9 hints but **zero lint errors**, and the Gradle lint task exits successfully. No lint rule was disabled or suppressed to obtain this result.

### AndroidTest note

Instrumentation/migration/Compose AndroidTest sources compile successfully in Part 1. Execution of the full connected/instrumentation matrix remains in the agreed **Part 3 verification segment** of the three-part split; this checkpoint does not falsely report those tests as executed.

## GitHub Tooling Evidence

Repository used for prebuilt Android tooling only: `ahmad101068/sabou-manager-v3-`.

The user-provided baseline snapshot itself is not present as a commit in the connected repository, so these runs are **tooling evidence, not current-source CI evidence**.

- Run `31856785271` — Prepare Offline Android Dependencies — successful. Supplied checksum-verified Gradle 8.13 dependency cache and Android SDK API 36.
- Run `31857297582` — Prepare Android Build Tools 35 — successful. Supplied checksum-verified Build Tools 35.0.0.
- Run `31873438285` — Prepare Pre-Phase3 Combined Offline Cache — successful. Supplied checksum-verified lint dependency cache.

Current Part 1 source was compiled/tested/linted locally using those checksum-verified GitHub artifacts in offline mode.

## Anti-Deception Check — Part 1

- Business Core redesigned: **NO**
- Historical migrations deleted: **NO**
- Historical migrations rewritten: **NO**
- Schema hand-edited: **NO**
- Tests deleted: **NO**
- Assertions weakened: **NO**
- Critical tests ignored: **NO**
- `continue-on-error` introduced: **NO**
- `|| true` introduced to hide a critical gate: **NO**
- Lint rules suppressed to obtain green: **NO**
- Forbidden POS/Table/KDS restored: **NO**
- Old brand restored: **NO**
- Secrets committed: **NO**
- Fake financial data introduced: **NO**
- Existing build artifacts reused as proof of current source: **NO**

## Part 1 Status

**PHASE 3 — PART 1 IMPLEMENTATION COMPLETE**  
**CHECKPOINT READY FOR PART 2**

This is intentionally not the final Phase 3 approval/status.
