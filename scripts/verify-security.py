#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BUILD = ROOT / "app/build.gradle.kts"

failures: list[str] = []
notes: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

manifest = MANIFEST.read_text(encoding="utf-8")
require('android:allowBackup="false"' in manifest, "manifest must disable Android backup")
require('android:usesCleartextTraffic="false"' in manifest, "manifest must disable cleartext traffic")
require("android.permission.INTERNET" in manifest, "INTERNET permission expected for guarded cloud-sync code")
permissions = set(re.findall(r'<uses-permission\s+android:name="([^"]+)"', manifest))
expected_permissions = {"android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS"}
require(permissions == expected_permissions, f"manifest permissions changed: {sorted(permissions)}")
require(not re.search(r'<(?:service|provider|receiver)\b[^>]*android:exported="true"', manifest, re.S),
        "unexpected exported service/provider/receiver")

build = BUILD.read_text(encoding="utf-8")
for env in (
    "RESTAURANT_MANAGEMENT_KEYSTORE_PATH",
    "RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD",
    "RESTAURANT_MANAGEMENT_KEY_ALIAS",
    "RESTAURANT_MANAGEMENT_KEY_PASSWORD",
):
    require(env in build, f"release signing variable missing from build script: {env}")
require("signingConfigured && !signingComplete" in build, "partial signing configuration must fail closed")

key_provider = (MAIN / "ir/restaurant/management/data/security/DatabaseKeyProvider.kt").read_text(encoding="utf-8")
for needle in ("AndroidKeyStore", "AES/GCM/NoPadding", "setKeySize(256)", "SecureRandom"):
    require(needle in key_provider, f"database key control missing: {needle}")

database = (MAIN / "ir/restaurant/management/data/db/AppDatabase.kt").read_text(encoding="utf-8")
require('System.loadLibrary("sqlcipher")' in database, "SQLCipher native library is not loaded")
require("SupportOpenHelperFactory" in database, "Room database is not wired through SQLCipher support factory")

for rel in (
    "ir/restaurant/management/data/repository/HttpsSyncTransport.kt",
    "ir/restaurant/management/data/repository/SyncTokenRefresher.kt",
):
    text = (MAIN / rel).read_text(encoding="utf-8")
    require("HttpsURLConnection" in text, f"{rel} must require HttpsURLConnection")
    require("instanceFollowRedirects = false" in text or "instanceFollowRedirects=false" in text,
            f"{rel} must disable automatic redirects")

all_main = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in MAIN.rglob("*.kt"))
for forbidden in ("TrustAllCertificates", "ALLOW_ALL_HOSTNAME_VERIFIER"):
    require(forbidden not in all_main, f"forbidden TLS bypass marker found: {forbidden}")

secret_assignment = re.compile(
    r'(?i)\b(?:api[_-]?key|client[_-]?secret|access[_-]?key|private[_-]?key|password|token)[A-Za-z0-9_]*\s*=\s*"([^"$]{8,})"'
)
for path in MAIN.rglob("*.kt"):
    body = path.read_text(encoding="utf-8", errors="replace")
    if secret_assignment.search(body):
        failures.append(f"possible hard-coded credential literal: {path.relative_to(ROOT)}")

# Flag literal HTTP endpoints in production Kotlin. URI namespace strings and comments are excluded conservatively.
http_hits: list[str] = []
for path in MAIN.rglob("*.kt"):
    for no, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        if "http://" in line and "schemas.android.com" not in line:
            http_hits.append(f"{path.relative_to(ROOT)}:{no}")
require(not http_hits, "literal cleartext HTTP endpoint(s): " + ", ".join(http_hits[:8]))

# This is intentionally conservative: a static source scan cannot prove runtime security.
if failures:
    print("STATIC_SECURITY_CONTROLS=FAIL")
    for item in failures:
        print(f" - {item}")
    sys.exit(1)

print("STATIC_SECURITY_CONTROLS=PASS")
print("MANIFEST_BACKUP=DISABLED")
print("MANIFEST_CLEARTEXT=DISABLED")
print("DATABASE_ENCRYPTION_WIRING=SQLCIPHER")
print("DATABASE_KEY_WRAP=ANDROID_KEYSTORE_AES_256_GCM")
print("SYNC_TRANSPORT=HTTPS_REDIRECTS_DISABLED")
print("RUNTIME_SECURITY=NOT_VERIFIED")
