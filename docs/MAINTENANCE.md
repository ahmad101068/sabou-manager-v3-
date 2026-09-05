# Maintenance Rules

## Database

Current Room version is 55. Never reset/squash the migration chain. A future schema change must increment the version and add a real migration plus Room-generated schema evidence and migration tests. Never hand-edit a schema JSON.

## Canonical paths

Before adding a repository/service, consult `ARCHITECTURE-CURRENT.md`. Do not add a second accounting ledger, receivable balance engine, P&L calculator, Branch resolver, or inventory truth.

## Branch

New branch-scoped writes use numeric `branchId` and validate the referenced/active Branch where the operation requires active status. Text branch fields are snapshots/display/legacy compatibility only.

## Refactoring

Large repository/service classes are intentionally not deeply refactored before the Phase-3 runtime/business test safety net. See `docs/KNOWN_ISSUES.md`.
