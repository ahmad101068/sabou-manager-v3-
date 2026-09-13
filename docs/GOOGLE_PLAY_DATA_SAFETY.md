# Google Play Data Safety Working Inventory

**Distribution model: OWNER_DECISION_REQUIRED.** This document is a source-derived preparation aid, not a submitted Play Console declaration and not legal advice.

## Source-observable data classes

- App account/user identifiers and authorization records: collected/stored locally for authentication, authorization and audit.
- Personnel and payroll: collected/stored locally for HR/payroll; may leave device only through configured sync/export/backup paths.
- Customer/party and receivable information: collected/stored locally for business operations and accounting.
- Financial/accounting/inventory/procurement data: collected/stored locally for core ERP operation and audit.
- Application/device correlation identifiers: present for audit/sync correlation; no advertising-ID collection path found.
- Notifications: Android notification permission/operational alert behavior exists; no third-party push analytics SDK found.
- Backup/export: user/authorized operator can create/export encrypted backup material to a destination URI/provider.
- Cloud sync: infrastructure is optional and fail-closed until intentionally configured/production-enabled; HTTPS transport is required.
- Analytics and external crash reporting: no corresponding SDK/path found in the current dependency/source audit.
- Third-party telemetry sharing: no source evidence found.

## Encryption and sharing

Primary local database uses SQLCipher; key protection uses Android Keystore. Network sync requires HTTPS. Portable backups are encrypted/authenticated, with KDF modernization tracked separately. A user-selected storage/document provider receiving an exported backup is an off-device destination and must be represented accurately if public distribution is chosen.

## Retention/deletion

The app can create/manage local application users and primarily supports deactivation. If `PUBLIC_GOOGLE_PLAY` is selected, owner/product/legal review must decide whether a public account-deletion flow and external request mechanism are required. Posted financial/payroll/audit history must not be blindly deleted; documented retention/pseudonymization may be necessary.

Before store submission, the owner must reconcile this inventory with actual production backend configuration, support process, retention policy and Play Console questions.
