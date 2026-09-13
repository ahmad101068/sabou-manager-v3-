# Changelog

## Unreleased — Phase 2 canonical Branch port on Version 53 closure

### Changed

- Preserved the full Room migration chain through schema 53 and appended non-destructive `MIGRATION_53_54`.
- Added one canonical `BranchEntity` master and made `branchId` the new business identity for the Phase 2 branch-aware paths without creating a parallel Branch or accounting system.
- Ported branch-aware Daily Sales selection/posting, payroll, personnel/assignment, storage/waste, purchase, asset, receivable/collection and management-control changes from the earlier wrong-baseline implementation.
- Preserved existing Revenue/Tax, receivable, Aging, reversal, Food Cost and branch P&L semantics from the Version 53 closure baseline.
- Added Version 53→54 migration and branch production-flow/isolation/rename regression coverage.

### Unverified / blocked

- Room-generated schema 54 remains pending; historical generated schemas are preserved and no 54.json is fabricated.
- Android compile/instrumentation execution remains blocked until an Android SDK 36 toolchain is configured in the execution environment.

## Unreleased — Phase 2 final accounting closure

### Changed

- Upgraded the existing accounting journal to Room schema 53 with structural `branchId` and explicit `accountingScope`; no parallel ledger was introduced.
- Added deterministic historical backfill for non-archive Daily Sales and Phase-2 receivables/collections, leaving unverifiable history as `UNASSIGNED_LEGACY`.
- Made new Daily Sales and receivable collection journals branch-aware and made reversals inherit original accounting scope.
- Added a canonical branch P&L query over the existing journal with branch isolation, tax exclusion, payroll separation, and data-completeness signals; Daily Management Brief now consumes it.
- Added host-executable SQLite verification plus Android migration/posting/P&L regression tests for the closure patch.
- Established Git baseline and evidence-driven audit branch.
- Made current Room schema verification fail closed when source version lacks a matching Room-generated schema JSON.
- Added regression tests for the Room schema verifier.
- Hardened release signing configuration and Windows release helper against partial credentials/stale artifacts.
- Hardened sync transport to require HTTPS connections and disable automatic redirects on credential-bearing requests.
- Consolidated current developer/release/security/testing documentation and removed contradictory historical Alpha/Phase documents from the working tree (history remains in Git).
- Added static security and repository-hygiene verification.

### Unverified / blocked

- Room-generated schema 53.
- Android clean build, complete unit/instrumentation suite, lint, R8 release build.
- Fresh APK/AAB artifacts and their hashes.
- 16 KB compatibility of the final packaged native libraries.

These remain explicit blockers rather than being reported as green.
