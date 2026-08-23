# Phase 8.1 Room schema provenance

The Phase 8.1 reconstruction directly bundles the exact Room 59 schema exported by the formally verified Phase 8 artifact because the new 59→60 migration must be exercised from a genuine prior schema rather than a hand-written fixture.

Canonical provenance hashes recovered from formal phase artifacts:

- 56.json: Phase 4 formal artifact, SHA-256 `3218181977b7fb8079bd478db3eac97c72874628da427d0b39b6ef2fe12f92fd`
- 57.json: Phase 4/5 formal artifact, SHA-256 `50eb41e5dc49e6b0f03275510f32c2ef9ac49db0ad157253be0531650cb88894`
- 58.json: Phase 5 formal artifact, SHA-256 `3ff188efb092b87ecaa6b3db3a4285a1f6749a992e2ea8611e15c61aade0a0d5`
- 59.json: Phase 8 formal artifact, SHA-256 `c23b7d1f794cdb6febc643fa79ddf4f68222eb6fe3ba42622bbbd36599a14e00`

`59.json.gz.b64` is the retained deterministic payload used by Phase 8.1. The reconstruction script decodes it, verifies the decompressed SHA-256 above, and copies the exact schema to the candidate's `app/schemas/.../59.json` before instrumentation compilation. Room/KSP then exports schema 60 from the real Phase 8.1 entities; the final verification records its SHA-256 and validates the genuine 59→60 migration on emulators.
