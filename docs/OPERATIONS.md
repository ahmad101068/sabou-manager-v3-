# Production Operations — Sabou Manager v1.0.0

## Operating baseline

Production installations must use an artifact built from the approved v1.0.0 release commit. Record the Git SHA, versionCode, versionName, Room schema version, build timestamp, artifact SHA-256, device model, Android version and restaurant/branch identifier in the deployment record.

## Daily controls

Before opening operations, confirm the application starts cleanly, authentication succeeds, the expected branch/location is active, the database health boundary does not report an integrity failure, and the most recent backup status is acceptable. At end of day, complete cash reconciliation, verify unresolved operational alerts, confirm accounting/inventory exceptions are reviewed, and verify a recoverable backup exists.

## Incident priority

P0: data loss/corruption, unauthorized access, accounting imbalance, inventory integrity failure, unrecoverable migration or inability to restore. Stop rollout and preserve device/database evidence.

P1: a core operational flow cannot complete correctly, repeated crash on a primary workflow, or reconciliation mismatch without evidence of corruption. Stop new rollout and use the last verified version until resolved.

P2/P3: non-blocking UX, reporting or performance issues. Record for maintenance release unless operational impact escalates.

## Upgrade procedure

1. Confirm the installed version and database schema.
2. Create and validate a backup before upgrade.
3. Install the approved signed artifact without clearing application data.
4. Launch once and allow Room migrations to complete.
5. Verify authentication, branch scope, sales/treasury access, inventory read, accounting read, audit health and backup status.
6. Reconcile representative totals against the pre-upgrade record.
7. Record the new artifact SHA and application/schema version.

Never use destructive migration, application-data clearing or ad-hoc database editing as an upgrade method.

## Rollback boundary

Do not install an older application version over a database that has already migrated to a newer schema unless a tested backward-compatible path explicitly exists. The supported recovery path is restore of a validated pre-upgrade backup plus installation of the matching approved application artifact.

## Evidence preservation

For integrity/security failures preserve the database/backup involved, application version and commit SHA, timestamps, device/Android information, relevant audit/forensic records and the steps that reproduced the failure. Do not modify the original evidence copy.
