# Security Baseline

Statuses below are source/static evidence unless explicitly marked otherwise. No unexecuted runtime control is called verified.

## Preserved controls

- Manifest: `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`.
- Room is opened through SQLCipher (`SupportOpenHelperFactory`).
- Database key material is generated with `SecureRandom` and protected using Android Keystore AES-256-GCM.
- HTTPS sync transport requires `HttpsURLConnection`; credential-bearing requests disable automatic redirects.
- No production signing password/keystore is stored in source. Partial signing configuration fails closed.
- Production-secret/static logging checks remain in the repository QA scripts.

## Credential KDF audit

Current credential format is compatibility-sensitive and remains unchanged in this gate:

```text
prefix = pbkdf2-sha1
algorithm = PBKDF2WithHmacSHA1
iterations = 310000
```

Instrumentation tests explicitly assert the current stored prefix. Changing it without a versioned compatibility/migration test could lock out users or break recovery. This gate therefore records a security modernization target rather than silently mutating the format.

**Production target for test-backed security work:** versioned `PBKDF2WithHmacSHA256` (or a stronger Android-supported memory-hard construction after compatibility analysis), per-secret independent random salt, calibrated iteration/work factor, and constant-time comparison. Existing constant-time comparison behavior must be preserved.

## Portable backup KDF audit

Portable backup uses authenticated AES-GCM, but its password derivation also uses `PBKDF2WithHmacSHA1`. The envelope/format is compatibility-sensitive. Upgrade requires a versioned envelope plus import tests for existing backup material; it is deferred to test-backed security work rather than changed here.

## OWASP MASVS mapping

| Control group | Status | Source/static evidence | Remaining evidence |
|---|---|---|---|
| MASVS-STORAGE | IMPLEMENTED | SQLCipher Room, Keystore-wrapped DB key, `allowBackup=false`, protected local backup paths | device/runtime extraction testing in test phase |
| MASVS-CRYPTO | PARTIAL | AES-256-GCM, `SecureRandom`, SQLCipher; KDF formats audited | replace SHA1-based PBKDF2 only with compatibility tests |
| MASVS-AUTH | IMPLEMENTED | session/role model, `SessionAuthorizer`, permission checks including `BRANCH_MANAGE` | end-to-end runtime authorization tests |
| MASVS-NETWORK | IMPLEMENTED | HTTPS-only sync validation; `HttpsURLConnection`; redirects disabled; cleartext disabled | runtime TLS/proxy tests |
| MASVS-PLATFORM | PARTIAL | restricted manifest exports/backup settings and Android permission model | merged-manifest/device behavior tests |
| MASVS-CODE | IMPLEMENTED | static QA, no critical TODO/NotImplemented target, fail-closed signing config | runtime fuzz/negative testing |
| MASVS-RESILIENCE | TEST-PHASE | no claim of anti-tamper/obfuscation runtime resistance beyond release minification configuration | signed-release/runtime inspection |
| MASVS-PRIVACY | PARTIAL | data inventory and local-first design documented | owner distribution/retention policy and runtime verification |

## Release signing

Environment variables:

- `RESTAURANT_MANAGEMENT_KEYSTORE_PATH`
- `RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD`
- `RESTAURANT_MANAGEMENT_KEY_ALIAS`
- `RESTAURANT_MANAGEMENT_KEY_PASSWORD`

No value is committed. If production credentials are not supplied, status is `REQUIRES_OWNER_SIGNING_SECRET`; an unsigned artifact is not called a signed release.
