# Baseline Audit

Audit branch: `audit/final-readiness`

Baseline commit: `1ae2613c662211c62fe96f718231962bcc0eed8b`

This document records the source exactly as received before the final audit/refactor work. Anything not actually executed is marked **NOT VERIFIED**.

## Build configuration

| Item | Baseline value |
|---|---|
| Android Gradle Plugin | 8.13.0 |
| Gradle wrapper | 8.13 |
| Kotlin | 2.1.20 |
| KSP | 2.1.20-2.0.1 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 23 |
| versionCode | 209 |
| versionName | `3.2.0-phase1` |
| applicationId | `ir.restaurant.management` |
| Room database version | 52 (`APP_DATABASE_SCHEMA_VERSION`) |
| Latest exported Room schema present | 50.json |

## Important dependencies at baseline

- Compose BOM 2026.06.00
- AndroidX Core KTX 1.17.0
- Activity Compose 1.11.0
- Lifecycle 2.9.4
- Navigation Compose 2.9.8
- Room 2.8.4
- WorkManager 2.11.2
- DataStore 1.2.1
- Kotlin Coroutines 1.10.2
- SQLCipher for Android 4.17.0
- AndroidX SQLite 2.6.2
- JUnit 4.13.2

## Source and test inventory

- Production Kotlin files: 322
- Production XML files: 5
- Unit-test Kotlin files: 113
- Unit-test `@Test` methods: 330
- Instrumentation-test Kotlin files: 60
- Instrumentation-test `@Test` methods: 143

## Verification status before changes

| Check | Baseline result | Evidence |
|---|---|---|
| `scripts/verify-code-quality.py` | PASS for its static checks | `CODE_QUALITY=PASS`, current Room version 52, latest schema file 50, schema evidence PENDING |
| `scripts/verify-foundation.sh` | PASS for its static checks | Script output: `Restaurant Management ERP 2.0 foundation architecture static checks passed.` |
| Gradle wrapper startup | BLOCKED | Wrapper attempted to download Gradle 8.13 and failed with `UnknownHostException: services.gradle.org` |
| Kotlin/Android compile | NOT VERIFIED | Gradle cannot start in this environment |
| Unit tests | NOT VERIFIED | Gradle cannot start in this environment |
| Instrumentation tests | NOT VERIFIED | Android SDK/emulator and Gradle are unavailable in this environment |
| Android Lint | NOT VERIFIED | Gradle cannot start in this environment |
| Release APK | NOT VERIFIED | Gradle/Android SDK/signing credentials unavailable |
| Release AAB | NOT VERIFIED | Gradle/Android SDK/signing credentials unavailable |
| Room schema 52 export | NOT VERIFIED / MISSING | `app/schemas/...` contains 1, 44, 49, and 50 only |

## Baseline findings that require action

1. Source declares Room schema **52**, while exported schema evidence stops at **50**.
2. Root documentation is heavily duplicated and contradictory: current source metadata coexists with old Alpha/Phase reports that state schema versions such as 43 and 49.
3. `README.md`, `README-fa.md`, `ARCHITECTURE-FA.md`, and `STATUS-FA.md` are stale relative to production source.
4. Legacy signing documentation uses `CAFE_RESTAURANT_*`, while production build logic uses `RESTAURANT_MANAGEMENT_*` variables.
5. Release build, test, lint, current Room export, APK/AAB freshness, and 16 KB artifact compatibility cannot be truthfully marked verified in the current execution environment.
6. Several production files are very large (`LocalHrPayrollService.kt`, `LocalPersonnelRepository.kt`, `LocalDailySalesRepository.kt`, and others); invasive refactoring requires build/test verification and therefore must be risk-managed rather than performed only to reduce line count.
