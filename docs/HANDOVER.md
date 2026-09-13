# Engineering Handover

## Baseline

The Phase-3 source is derived only from the approved Room-v54 Phase-2 candidate. Gate 0 introduced the test-backed compatibility-only Room 54→55 migration for stable Dashboard branch attribution; all earlier database history remains unchanged.

## Start here

1. Read `README.md` for exact build metadata.
2. Read `ARCHITECTURE-CURRENT.md` and `ARCHITECTURE-FREEZE.md` before changing business-core paths.
3. Run `scripts/verify-pre-phase3-standardization.py` and `scripts/verify-foundation.sh` before a change.
4. For schema changes, preserve migration continuity and regenerate Room schema with KSP.
5. Run `.github/workflows/tests.yml` during Phase 3 before deep architecture refactors.

## Owner actions

- Decide distribution model (`PUBLIC_GOOGLE_PLAY` vs `PRIVATE_ENTERPRISE`); current status is `OWNER_DECISION_REQUIRED`.
- Supply production release signing secrets out of band when a signed release is required.
- Approve retention/account-deletion policy before public distribution.
