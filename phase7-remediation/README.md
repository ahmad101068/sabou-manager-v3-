# Phase 7 deterministic remediation payload

This directory carries the hash-pinned Phase-7 source patch applied on top of the verified Phase-6 reconstruction.

- Phase-6 formal handoff SHA: `adda2cefa738c29e18a1f6e15d75d5fee136b042`
- Phase-7 patch SHA-256: `4db68c04e22cf306e0b61c8b455869e150c2e29ec89e0d303dbdd4d6b95f6b55`
- Payload chunks: `phase7-final.patch.xz.b64.00` through `.05`
- Reconstruction: `.github/scripts/reconstruct-phase7-candidate.sh`
- Verification: `.github/workflows/phase7-pr-targeted.yml`

The patch is fail-closed: reconstruction verifies its digest before applying it, preserves Room schema version 59, forbids destructive migration fallback, and asserts the Phase-7 UI/search/paging/print invariants before CI compilation and tests.
