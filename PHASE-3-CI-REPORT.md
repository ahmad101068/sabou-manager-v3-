# PHASE 3 — CI REPORT

## Source-of-truth model
GitHub branch `codex/phase3-part3b-20260816` is the sole source of truth. The Android candidate is reconstructed from the committed source archive parts and a fail-closed ordered patch chain by `.github/scripts/reconstruct-part3b-candidate.sh`.

Both required workflows use that same reconstruction script:
- `Phase 3 Part 3B Runtime and Business Tests`
- `Phase 3 Part 3B Production Readiness`

## Candidate-consistency controls
- Every patch is checked with `git apply --check` before apply.
- Known fix markers are verified after reconstruction.
- SHA-256 values of critical reconstructed files are emitted by the reconstruction step.
- Connected jobs clean candidate outputs before instrumentation.
- Instrumentation uses `--no-build-cache`.
- 16KB uses a single stateful shell and rejects any runtime page size other than 16384.

## Historical CI defects corrected
- A prior `git -C phase3-source apply` path could report success without applying changes to the reconstructed source; the current script applies patches with an explicit target directory and fail-closed markers.
- A previous 16KB script lost shell state between commands and could report zero discovered tests; the stateful runtime path now validates page size and executes the real suite.
- A follow-up CRM patch exposed a compile-time scope defect; the brace was minimally repaired and compile regression coverage retained.
- The final security test-hook follow-up includes its required Compose `Box` import and reconstruction verifies it.

## Final evidence fields
The final handoff must record, from the same final source commit:
- REPOSITORY_FULL_NAME=ahmad101068/sabou-manager-v3-
- BRANCH=codex/phase3-part3b-20260816
- SOURCE_COMMIT=<final head>
- HEAD_SHA=<final head>
- WORKFLOW_NAME=<required workflow>
- WORKFLOW_RUN_ID=<same-commit run>
- WORKFLOW_RUN_URL=<same-commit run URL>
- JOB_RESULTS=<actual final results>
- ARTIFACTS=<actual final artifacts>
- CANDIDATE_SHA256=<final reconstructed candidate evidence>

## Status rule
No older-commit run can serve as final evidence. If either required workflow on the final source commit fails, status is `PHASE 3 PART 3B NOT COMPLETE`.
