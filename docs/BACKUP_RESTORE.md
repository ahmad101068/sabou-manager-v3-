# Backup and Restore — Sabou Manager v1.0.0

## Principle

A backup is not considered valid until it has been restored and verified in a controlled test. Production recovery must preserve accounting, inventory, payroll, assets, security scope and audit integrity.

## Backup procedure

1. Complete or safely stop any active critical transaction.
2. Create the application-supported backup through the production backup path.
3. Record application version, versionCode, Room schema version, timestamp, branch/site and backup identifier.
4. Store the backup outside the application data directory and protect access to it.
5. Where a digest is available, record SHA-256 and retain it with the deployment record.

Do not treat screenshots, exported reports or copied partial database files as a database backup.

## Restore drill

Perform restore drills on a non-production device or isolated test environment.

1. Start from a clean application installation compatible with the backup.
2. Restore only through the supported restore flow.
3. Confirm the application opens without migration/integrity errors.
4. Validate representative records and totals from each critical domain: sales, cash/treasury, receivables/payables, inventory quantities and movements, journal balances, payroll, fixed assets and audit trail.
5. Verify branch/location scope and authentication after restore.
6. Run database/audit integrity checks exposed by the application.
7. Compare agreed control totals to the source environment.

A restore is PASS only when integrity checks and control totals pass. Any mismatch is P0 until explained.

## Recovery after failed upgrade

If an upgrade fails before a successful migration, preserve evidence and retry only after the failure is understood. If a database has already migrated, do not downgrade the app onto that database. Recover using the matching pre-upgrade backup and the previously approved artifact.

## Retention

Maintain enough generations to recover from a defect that is discovered after more than one business day. At minimum, operational policy should include recent daily backups and at least one older known-good restore-tested generation. Retention duration and storage location must follow the restaurant/operator's legal and accounting requirements.

## Security

Backups can contain sensitive operational and personal data. Limit access, avoid public/shared storage, do not commit backups to Git, and do not place keystores, signing credentials or raw production databases in repository artifacts.
