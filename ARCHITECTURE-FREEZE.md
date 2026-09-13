# Architecture Freeze — Phase 3

The following production semantics were frozen at the approved Room-v54 baseline. Phase 3 must not redesign them without a test-proven defect and a minimal change record. Gate 0 identified one testable compatibility defect in Dashboard legacy branch attribution; Room v55 is limited to the stable alias read model needed to fix that defect and does not redesign the frozen Business Core.

## Frozen contracts

1. `branchId` is the only canonical Branch identity. No default/fallback Branch 1.
2. Accounting uses the existing Journal only; scopes remain `BRANCH`, `ORGANIZATION`, `UNASSIGNED_LEGACY`.
3. Revenue and tax remain separate; settlement includes tax without converting tax to revenue.
4. Receivable balance/aging has one canonical read truth; an old receivable collection is not new sales.
5. Collection and collection reversal remain receivable/accounting events, not Daily Sales creation.
6. Food Cost keeps distinct Theoretical, Actual, Unavailable Actual, and Variance semantics.
7. P&L keeps its canonical revenue/COGS/payroll/operating-expense formulas and branch isolation.
8. Inventory Ledger remains the canonical inventory truth.
9. The full Room migration chain through 53→54→55 and all historical tests remain part of the upgrade contract; 54→55 is compatibility-only.
10. Payroll, Daily Sales, Procurement and Management Control use the existing canonical posting/repository paths; no parallel engine is permitted.

## Change rule for Phase 3

A business-core change requires evidence recorded before patching:

```text
EVIDENCE=
ROOT_CAUSE=
AFFECTED_FILES=
MINIMAL_FIX=
REGRESSION_TEST=
```

A rename or UI cleanup is not permission to change financial formulas, migration SQL, posting order, collection semantics, branch identity, or inventory truth.
