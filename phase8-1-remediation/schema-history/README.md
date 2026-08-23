# Phase 8.1 Room schema provenance

These historical schemas are retained as exact compressed exports from the formally verified phase artifacts so the 59→60 migration and historical lineage can be independently reproduced.

- 56.json: Phase 4 formal artifact, SHA-256 `3218181977b7fb8079bd478db3eac97c72874628da427d0b39b6ef2fe12f92fd`
- 57.json: Phase 4/5 formal artifact, SHA-256 `50eb41e5dc49e6b0f03275510f32c2ef9ac49db0ad157253be0531650cb88894`
- 58.json: Phase 5 formal artifact, SHA-256 `3ff188efb092b87ecaa6b3db3a4285a1f6749a992e2ea8611e15c61aade0a0d5`
- 59.json: Phase 8 formal artifact, SHA-256 `c23b7d1f794cdb6febc643fa79ddf4f68222eb6fe3ba42622bbbd36599a14e00`

The reconstruction script verifies the decompressed SHA-256 before copying each schema into `app/schemas/.../`.
