# Dependency Baseline

Audit date: 2026-08-15.

This standardization gate does **not** upgrade Android application dependencies. The approved compatible set is preserved:

| Component | Version | Gate status |
|---|---:|---|
| Android Gradle Plugin | 8.13.0 | PRESERVED; real compile/lint/debug/release build passed |
| Kotlin | 2.1.20 | PRESERVED; real Kotlin compile passed |
| KSP | 2.1.20-2.0.1 | PRESERVED; Room/KSP schema generation passed |
| Compose BOM | 2026.06.00 | PRESERVED; stable BOM line used by the approved source |
| Room | 2.8.4 | PRESERVED; upstream stable release and schema generation passed |
| WorkManager | 2.11.2 | PRESERVED; upstream stable release |
| DataStore | 1.2.1 | PRESERVED; upstream stable release |
| Kotlin Coroutines | 1.10.2 | PRESERVED |
| SQLCipher Android | 4.17.0 | PRESERVED; current upstream Android integration line |
| AndroidX SQLite | 2.6.2 | PRESERVED; SQLCipher integration-compatible version |
| Gradle | 8.13 | PRESERVED; matches AGP 8.13 compatibility table |
| JDK | 17 (CI/release baseline) | PRESERVED; matches AGP 8.13 compatibility table |
| compileSdk / targetSdk | 36 / 36 | PRESERVED |

## Compatibility evidence

- Android's AGP 8.13 compatibility documentation specifies Gradle 8.13 and JDK 17, and supports API 36.x. The project keeps that toolchain combination.
- AndroidX lists Room 2.8.4 as the stable Room release. The repository's real KSP run generated/validated the current Room v55 schema with no hand-authored schema step.
- Android's Compose BOM documentation uses the stable `2026.06.00` BOM coordinate used by this source.
- AndroidX lists WorkManager 2.11.2 and DataStore 1.2.1 as stable releases.
- Zetetic's SQLCipher Android project documents `net.zetetic:sqlcipher-android:4.17.0` with `androidx.sqlite:sqlite:2.6.2`; the built artifacts contain its 16KB-aligned native library across the supported ABIs.
- Kotlin 2.1.20 is not asserted to be the newest Kotlin line. A later 2.1.21 patch and newer language lines exist; this gate intentionally does not upgrade Kotlin/KSP because the approved pair compiles successfully and no test-backed need for a toolchain change was established.

## Upstream references reviewed

- Android Gradle Plugin 8.13 release/compatibility notes: https://developer.android.com/build/releases/agp-8-13-0-release-notes
- Room release notes: https://developer.android.com/jetpack/androidx/releases/room
- Compose BOM guidance: https://developer.android.com/develop/ui/compose/bom
- WorkManager release notes: https://developer.android.com/jetpack/androidx/releases/work
- DataStore release notes: https://developer.android.com/jetpack/androidx/releases/datastore
- Kotlin release process / 2.1.20 history: https://kotlinlang.org/docs/releases.html
- SQLCipher Android upstream: https://github.com/sqlcipher/sqlcipher-android

## Security-review scope

The gate reviewed upstream release/status material and found no vendor notice that required an emergency application-dependency change for this frozen set. This is **not** represented as a comprehensive transitive SCA/CVE database attestation. Future dependency changes still require the normal security/advisory review plus compile, lint, Room-schema evidence and relevant runtime tests.

The objective is a stable, supported and mutually compatible toolchain rather than upgrading to “latest” without evidence. Any future Kotlin/KSP/Compose/Room/AGP change requires compile + lint + schema evidence and, where relevant, runtime tests.

GitHub workflow actions are supply-chain tools rather than application runtime dependencies. Production workflows pin official action releases to full immutable commit SHAs.
