# Privacy Data Inventory

Distribution model: **OWNER_DECISION_REQUIRED** (`PUBLIC_GOOGLE_PLAY` or `PRIVATE_ENTERPRISE`). The table describes source-observable behavior; policy/legal decisions are not invented.

| Data type | Collected? | Stored locally? | Sent off-device? | Encrypted? | Shared? | Purpose | Retention | Deletion behavior |
|---|---|---|---|---|---|---|---|---|
| App users/auth data | Yes | Yes | Not by default | SQLCipher; secrets additionally protected/hashed as implemented | No source evidence of third-party sharing | authentication/authorization/audit | until administrator action / required audit history | user can be deactivated; hard-delete policy requires product/legal decision |
| Personnel/HR | Yes | Yes | Only if optional sync/export is enabled/used | SQLCipher locally; HTTPS for sync transport | no third-party SDK sharing found | HR, scheduling, payroll | business/legal policy required | deletion must consider payroll/audit retention |
| Payroll | Yes | Yes | Optional sync/export only | SQLCipher; HTTPS transport | no third-party SDK sharing found | payroll calculation/posting/payment history | legal/business retention required | do not blindly delete posted financial/audit records |
| Customers/parties | Yes | Yes | Optional sync/export only | SQLCipher; HTTPS transport | no third-party SDK sharing found | receivables/accounting/customer ledger | business policy required | merge/deactivation/retention rules apply; posted finance retained as required |
| Financial/accounting | Yes | Yes | Optional sync/export only | SQLCipher; HTTPS transport | no third-party SDK sharing found | accounting, tax/reconciliation, audit | legal/accounting policy required | posted journal/audit history should be retained/pseudonymized per policy |
| Device identifiers | Application-scoped identifiers are present in audit/sync models | Yes when events/sync changes store them | Optional sync only | SQLCipher locally; HTTPS in transit | no ad identifier SDK found | audit/sync correlation | follows audit/sync retention | delete/pseudonymize according to retained audit policy |
| Notifications | Permission/feature present | notification state may be local | no third-party push SDK found | local DB protection where persisted | no source evidence of sharing | operational alerts | application/business policy | cleared/deleted through app lifecycle as implemented |
| Backup files | On user/admin action or policy | Yes | User may export to a selected document/cloud provider URI | database encrypted; portable backup additionally encrypted/authenticated | destination chosen by user; provider controls external copy | disaster recovery/portability | configurable/local owner policy | local backups can be deleted; exported copies are controlled by destination provider/user |
| Cloud sync | Optional/fail-closed infrastructure exists | sync queue local | Only when configured and safety gate permits | HTTPS; tokens protected | configured organization endpoint only | multi-device/remote synchronization | endpoint policy required | local queue and remote deletion need owner/backend policy |
| Analytics | No analytics SDK/source path found | No | No | N/A | No | N/A | N/A | N/A |
| Crash data | No crash-reporting SDK/source path found | No external crash telemetry path found | No | N/A | No | N/A | N/A | N/A |
| Third-party SDK data | AndroidX/SQLCipher are runtime libraries, not observed telemetry collectors in source | local operational data only | no telemetry sharing path found | as above | no source evidence | application platform/storage | dependency-specific | follows app data lifecycle |

If public-store distribution is selected, account-deletion applicability and an external deletion request path must be reviewed against current store policy. Financial/audit retention must be reconciled with deletion through deactivation, pseudonymization or legally permitted retention rather than indiscriminate erasure.
