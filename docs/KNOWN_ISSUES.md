# Known Issues and Deferred Work

## DEFERRED_REFACTOR_REQUIRES_TEST_SAFETY_NET

Large classes including `LocalHrPayrollService.kt`, `LocalPersonnelRepository.kt`, `LocalInventoryCommandEngine.kt`, and `LocalDailySalesRepository.kt` should not receive deep structural refactors before Phase-3 runtime/business/instrumentation coverage is executed.

## Gate-0 resolved — legacy Dashboard branch rename compatibility

Room v55 introduces `branch_legacy_aliases` as a compatibility-only read model. Dashboard selection stays `branchId`-based, Branch rename appends aliases, and historical sales/customer rows are not blindly rewritten. Alias mapping is accepted only when a normalized legacy name belongs to exactly one Branch; ambiguous historical text remains organization-wide/unassigned rather than being double-attributed. Regression coverage is in `DashboardBranchRenameCompatibilityIntegrationTest` and `Migration54To55Test`.

Some pre-canonical write DTOs outside Dashboard still carry optional legacy text keys. `CanonicalBranchResolver` converts those inputs to one active canonical `BranchEntity` before persistence; replacing every operational free-text branch input with the Phase-3 `CanonicalBranchSelector` remains Part-2 UI work, not a second identity model.

## PHASE3_CONSOLIDATION_ITEM — Room entity/UI boundaries

Where a screen/read model still consumes a persistence-oriented row/entity directly, consolidate only with a test safety net; no broad layer rewrite is performed here.

## PHASE3_PERFORMANCE_ITEM

Review N+1 query opportunities, large in-memory aggregation, unbounded flow collection and screen-local formatting during Phase 3 profiling. Only measured/obvious low-risk fixes should precede profiling.

## Security KDF modernization

Credential and portable-backup formats use PBKDF2-HMAC-SHA1. Upgrade to a versioned modern KDF format only with migration/import/login/recovery compatibility tests.

## External owner decisions

- Distribution model: `OWNER_DECISION_REQUIRED`.
- Signed production artifacts: `REQUIRES_OWNER_SIGNING_SECRET` until supplied securely.
- Account deletion/retention behavior for public-store distribution needs owner/legal product policy; financial and audit records must not be blindly destroyed.
