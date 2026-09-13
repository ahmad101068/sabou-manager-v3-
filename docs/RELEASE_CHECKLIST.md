# v1.x Release Checklist

A production release is GO only when all mandatory items are satisfied for the exact release commit.

## Source and version

- [ ] Canonical source exists directly in Git; no ZIP/reconstruction dependency is required to build the release.
- [ ] `versionName` and `versionCode` are intentional and unique for the release.
- [ ] Room schema version and migration registry are consistent.
- [ ] No `fallbackToDestructiveMigration` exists in production source.
- [ ] No test is disabled/ignored to make the gate pass.
- [ ] Release commit SHA is recorded in release notes/provenance.

## Build and automated verification

- [ ] Clean JVM/unit test suite PASS.
- [ ] Android lint PASS.
- [ ] Debug assembly PASS.
- [ ] Release assembly/bundle PASS without production signing credentials.
- [ ] API 23 complete instrumentation PASS.
- [ ] API 35 complete instrumentation PASS.
- [ ] Android 15 / 16KB critical matrix PASS.
- [ ] Room migration matrix PASS, including the immediately previous production schema.
- [ ] Business E2E critical paths PASS.
- [ ] Large-data/performance gate PASS.

## Integrity and security

- [ ] Accounting postings remain balanced and reversal paths are compensating rather than destructive.
- [ ] Inventory movements and available-stock truth reconcile.
- [ ] Branch/location authorization is enforced below the UI boundary.
- [ ] Sensitive actions require the intended scoped re-authentication.
- [ ] Audit integrity verification passes and forensic boundary is intact.
- [ ] No secret/credential/keystore is committed or included in source artifacts.

## Operations

- [ ] Backup created with the candidate version.
- [ ] Restore drill PASS with representative domain control totals.
- [ ] Upgrade from previous supported production version PASS without clearing data.
- [ ] Rollback/recovery procedure is documented.
- [ ] Production artifact SHA-256 is recorded.
- [ ] Signing identity is verified externally to CI source validation.

## Release decision

P0 findings must be zero. P1 findings must be zero or have an explicit owner-approved containment that does not threaten integrity/security. A failed migration, restore, accounting/inventory reconciliation, authorization or critical E2E gate is an automatic NO-GO.
