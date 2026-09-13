# PHASE-7 PLAN

Phase 7 continues from the formally verified Phase-6 handoff SHA `adda2cefa738c29e18a1f6e15d75d5fee136b042` without restarting prior phases.

Acceptance scope:
- dashboard UX/state/contrast hardening;
- Persian/RTL user-facing terminology and Persian digits;
- exact Rial and percentage formatting;
- reports/navigation/filter cleanup;
- A4 printing and fail-closed print/reprint audit;
- Room-backed scoped global search with typed targets and Persian/Arabic normalization;
- reusable paging for large lists/results;
- Daily Sales validation/persistence UX hardening;
- responsive treasury controls;
- compile, targeted JVM, API35 integration/integrity verification, and formal source handoff.

Database schema must remain version 59. Destructive migration fallbacks and weakened tests are not permitted. Full heavy API23/35 and end-to-end matrix remains Phase 8 scope.
