# Source Provenance — Sabou Manager v1.0.0

## Canonical release baseline

- Product version: `1.0.0`
- Android versionCode: `209`
- Room schema version: `60`
- Verified Phase 8.1 source commit: `5d9f1bda813225edf2f0b82cbc3ef6599e4c5017`
- Canonical source materialization commit: `45d2890b34ca6540c9f6f29b7a8fc6d729f6fd8c`
- Release branch: `release/v1.0.0`

The Phase 8.1 reconstruction pipeline passed before materialization. The canonical source tree was then copied without source transformations into the repository root. From this point onward, the Git source tree is the source of truth for v1.0.0 maintenance and release verification; historical reconstruction scripts remain provenance/audit evidence only.

## Verified Phase 8.1 evidence

The final Phase 8.1 production verification run was `33131974278` on source SHA `5d9f1bda813225edf2f0b82cbc3ef6599e4c5017` and completed successfully. Evidence covered JVM tests, lint, debug/release assembly, complete API 23 instrumentation, complete API 35 instrumentation, Android 15 16KB validation, Room migration validation through schema 60, large-data performance, security/integrity closure, and business E2E coverage.

## Release rule

No production tag may point to a commit that has unverified product-code changes after the canonical source commit. Documentation/CI-only commits must be followed by a canonical-source CI gate before tagging. Any future product-code modification requires the relevant regression suite and a new release candidate.
