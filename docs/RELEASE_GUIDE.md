# Release Guide

## Toolchain

JDK 17, Gradle 8.13, AGP 8.13.0, compileSdk/targetSdk 36. Run from a clean source checkout.

## Readiness sequence

1. `./gradlew --no-daemon clean`
2. Generate/compile Room/KSP output.
3. `git diff --exit-code -- app/schemas`
4. Run schema/documentation/hygiene/security/foundation/code-quality gates.
5. Run Android lint.
6. Build fresh Debug and Release/AAB artifacts as applicable.
7. Inspect native libraries/alignment and record SHA-256/source commit.

## Signing

Provide all four variables or none:

```text
RESTAURANT_MANAGEMENT_KEYSTORE_PATH
RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD
RESTAURANT_MANAGEMENT_KEY_ALIAS
RESTAURANT_MANAGEMENT_KEY_PASSWORD
```

Partial configuration intentionally fails. Do not place a keystore or password in Git. Without owner-provided production signing material, signed-release status is `REQUIRES_OWNER_SIGNING_SECRET`.

APK/AAB files are CI/build artifacts and must never be embedded in the source handoff ZIP.
