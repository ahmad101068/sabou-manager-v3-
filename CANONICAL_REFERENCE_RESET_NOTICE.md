# Sabou Manager — Canonical Reference Notice

**DO NOT START NEW DEVELOPMENT FROM GITHUB main YET.**

The current canonical development reference is Phase 4, established on 2026-10-07.

Canonical reference identity:
- commit: `424ebff8e4df935f5d11ab50294310aa8c4011c3`
- tree: `d3b588104887db58ef4a9c97b1e44ce5b05e37a7`
- tag: `sabou-reference-scope-p4-2026-10-07`
- versionName/versionCode: `1.0.3 / 212`
- Room schema: `61`
- database generation: `2`
- database file: `sabou_core_v2.db`
- historical migrations in production path: `NO`
- old installation data compatibility: `NOT SUPPORTED`
- production readiness: `NOT PRODUCTION READY`
- next phase: `PHASE 5 — Payroll & Procurement Correctness`

Phase 4 centralizes backend branch/warehouse scope across Accounting, Daily Sales, Replenishment, Alerts and Assets, adds persisted generic command receipts with Asset Maintenance exactly-once retry, closes the direct Recipe activation permission bypass, and requires an independent audited reason for accounting-period reopen.

Android Gradle compile/instrumentation remains NOT VERIFIED in the audit environment because the wrapper could not resolve `services.gradle.org` before compilation.

This GitHub `main` remains older/different and is preserved only for provenance until the exact canonical Phase 4 tree is imported and verified.

Pre-reset GitHub state:
`archive/pre-reference-reset-20261007`
