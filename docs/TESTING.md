# Testing and Verification

## Gate split

Phase 3 has started; runtime/business/instrumentation execution is no longer deferred. Part 1 first closes Gate 0 and its regression coverage, then Parts 2–3 expand UI and full test execution. No critical test may be weakened, ignored, or hidden behind a false-green CI path.

`FULL_RUNTIME_BUSINESS_TESTS=ACTIVE_PHASE3`

## Historical migration tests

The upgrade contract includes and preserves:

- `FullMigration1ToCurrentTest`
- `Migration53To54Test`
- `Migration54To55Test` for the stable legacy-branch alias compatibility migration.
- other historical migration tests required by the v1→v55 chain.

They are not renamed/deleted merely to remove development-era terminology.

## Standardization-targeted checks

```bash
python3 scripts/verify-pre-phase3-standardization.py
python3 scripts/verify-documentation.py
python3 scripts/test-verify-documentation.py
python3 scripts/verify-room-schema.py
python3 scripts/test-verify-room-schema.py
python3 scripts/verify-repository-hygiene.py
python3 scripts/verify-security.py
python3 scripts/verify-code-quality.py
bash scripts/verify-foundation.sh
```

Compile/lint/build commands are recorded in the final standardization report only when actually executed.

## Phase-3 runtime workflow

The manual test workflow retains JVM tests and Android instrumentation/migration/business tests across legacy-supported and current API targets. A 16KB-page runtime environment belongs to that phase. Test assertions must not be weakened to obtain a green readiness gate.


## Phase-3 Gate-0 regression evidence

- `DashboardBranchRenameCompatibilityIntegrationTest`: historical Dashboard attribution survives Branch rename while selection remains `branchId`-based.
- `Migration54To55Test`: v55 creates stable aliases without rewriting historical sales rows.
- `FullMigration1ToCurrentTest`: the complete v1→current migration must end with the compatibility read-model table present and pass Room validation.
- `scripts/verify-dashboard-ux.py`: current workflow discovery and final failure evaluation are fail-closed; the stale workflow-path/late-check false-green behavior is removed.
