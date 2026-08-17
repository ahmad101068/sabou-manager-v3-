# PHASE 3 — PERFORMANCE REPORT

## Scope
Part 3B does not introduce a performance refactor. Performance evidence is taken from the existing JVM/business/performance test task and the real connected runtimes.

## Relevant observations
- Connected tests are executed after a clean candidate build and without the Gradle build cache for the instrumentation task.
- API 23 and API 35 run on real Android emulators in CI.
- The 16KB gate runs Android 35 `google_apis_ps16k` and requires runtime `getconf PAGESIZE` evidence of 16384 before accepting the suite.
- No test timeout was increased to hide synchronization defects.
- UI synchronization fixes use explicit state/test hooks within the existing 10-second test bounds.
- Home seven-day revenue is built from exactly seven DailyManagementBrief read models for the selected branch; no unbounded history scan or synthetic trend generator was introduced.
- Branch selector options remain bounded to a 320dp lazy list inside a Dialog, avoiding the invalid/unbounded popup measurement path proven by UAT.

## Final gate
Performance/regression status is accepted only when the final same-commit JVM/business/performance task and all connected suites pass on the final 177-test candidate. Any failure keeps the phase at `PHASE 3 PART 3B NOT COMPLETE`.

## Non-blocking warnings
Compiler deprecation/opt-in warnings are tracked as `NON_BLOCKING_WARNING` unless they are proven to cause a required-gate failure.
