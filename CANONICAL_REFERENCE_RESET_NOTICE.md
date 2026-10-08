# Sabou Manager — Canonical Reference Notice

**DO NOT START NEW DEVELOPMENT FROM GITHUB main YET.**

The current canonical development reference is Phase 6, established on 2026-10-08.

Canonical reference identity:
- commit: `fef1146c2ce156e61994911b1086919bb369d4d6`
- tree: `d3f1944e06749fe2595c66b47d7cb6356f3169c2`
- tag: `sabou-reference-recipe-p6-2026-10-08`
- versionName/versionCode: `1.0.3 / 212`
- Room schema: `61`
- database generation: `2`
- database file: `sabou_core_v2.db`
- historical migrations in production path: `NO`
- old installation data compatibility: `NOT SUPPORTED`
- production readiness: `NOT PRODUCTION READY`
- next phase: `PHASE 7 — Backup, Recovery & Data Portability`

Phase 6 preserves sub-recipes through compatibility saves, fixes recipe-vs-stock unit conversion in UI and COGS preview, protects immutable historical recipe quantities from current conversion drift, hardens activation against closed accounting/inventory periods, adds chained/revocable/expiring substitution semantics with loop protection, clarifies management waste/yield semantics, and corrects Menu Engineering popularity/contribution-margin calculations.

Phase 6 verification:
- Phase 6 structural gate: `25/25 PASS`
- Phase 2–5 regression gates: `PASS`
- Room schema evidence: `PASS (61 unchanged)`
- domain harness: `PASS`
- Android Gradle compile/instrumentation: `NOT VERIFIED` because the wrapper could not resolve `services.gradle.org` before compilation.

This GitHub `main` remains older/different and is preserved only for provenance until the exact canonical Phase 6 tree is imported and verified.

Pre-reset GitHub state:
`archive/pre-reference-reset-20261007`
