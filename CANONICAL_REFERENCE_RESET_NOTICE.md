# Sabou Manager — Canonical Reference Notice

**DO NOT START NEW DEVELOPMENT FROM GITHUB main YET.**

The current canonical development reference is Phase 5, established on 2026-10-08.

Canonical reference identity:
- commit: `bd14e73d5d1f0ceae317640d23a5fb3c670ae511`
- tree: `6da9eae690dd88b71eb99fcf495f67aedaa99746`
- tag: `sabou-reference-payroll-procurement-p5-2026-10-08`
- versionName/versionCode: `1.0.3 / 212`
- Room schema: `61`
- database generation: `2`
- database file: `sabou_core_v2.db`
- historical migrations in production path: `NO`
- old installation data compatibility: `NOT SUPPORTED`
- production readiness: `NOT PRODUCTION READY`
- next phase: `PHASE 6 — Recipe, COGS & Menu Engineering`

Phase 5 closes payroll late/absence double deduction, introduces versioned statutory payroll policy and employer liabilities, separates payroll tax/insurance settlement, makes purchase price variance line-aware including zero-price PO risk, and replaces same-session variance approval with persisted submit/independent-approve/post command evidence.

Android Gradle compile/instrumentation remains NOT VERIFIED in the audit environment because the wrapper could not resolve `services.gradle.org` before compilation.

This GitHub `main` remains older/different and is preserved only for provenance until the exact canonical Phase 5 tree is imported and verified.

Pre-reset GitHub state:
`archive/pre-reference-reset-20261007`
