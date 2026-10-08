# Sabou Manager — Canonical Reference Notice

**DO NOT START NEW DEVELOPMENT FROM GITHUB main YET.**

The current canonical development reference is Phase 7, established on 2026-10-09.

Canonical reference identity:
- commit: `15db8c96d546d9a715d8ec84f47091c09b66f445`
- tree: `14cdc1cd59a8293265af8b1616c1fd89d1284de4`
- tag: `sabou-reference-backup-p7-2026-10-09`
- versionName/versionCode: `1.0.3 / 212`
- Room schema: `61`
- database generation: `2`
- database file: `sabou_core_v2.db`
- historical migrations in production path: `NO`
- old installation data compatibility: `NOT SUPPORTED`
- production readiness: `NOT PRODUCTION READY`
- next phase: `PHASE 8 — Failure Engineering & Concurrency`

Phase 7 makes automatic backup DAILY by default, adds a persistent SAF destination outside the app sandbox, upgrades new portable exports to versioned PBKDF2-HMAC-SHA256/600k + chunked AES-GCM, carries managed HR document bytes, validates restore row counts before and after opening the restored database, and performs crash-safe post-restore SQLCipher key rotation.

Phase 7 verification:
- Phase 7 structural gate: `34/34 PASS`
- Phase 2–6 regression gates: `PASS`
- Portable V4 Kotlin compile + large/tamper harness: `PASS`
- Room schema evidence: `PASS (61 unchanged)`
- Android Gradle compile/instrumentation: `NOT VERIFIED` because the wrapper could not resolve `services.gradle.org` before compilation.
- SQLCipher `VACUUM INTO` and `changePassword` device runtime: `NOT VERIFIED`.

This GitHub `main` remains older/different and is preserved only for provenance until the exact canonical Phase 7 tree is imported and verified.

Pre-reset GitHub state:
`archive/pre-reference-reset-20261007`
