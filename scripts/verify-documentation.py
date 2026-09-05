#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
DB = (ROOT / "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt").read_text(encoding="utf-8")

parser = argparse.ArgumentParser(description="Verify canonical documentation metadata exactly matches source fields.")
parser.add_argument("--readme", type=Path, default=ROOT / "README.md")
args = parser.parse_args()
readme_path = args.readme if args.readme.is_absolute() else ROOT / args.readme

failures: list[str] = []

def capture(pattern: str, text: str, label: str) -> str:
    match = re.search(pattern, text, flags=re.M)
    if not match:
        failures.append(f"cannot parse source field: {label}")
        return "UNKNOWN"
    return match.group(1)

source = {
    "applicationId": capture(r'^\s*applicationId\s*=\s*"([^"]+)"\s*$', BUILD, "applicationId"),
    "compileSdk": capture(r'^\s*compileSdk\s*=\s*(\d+)\s*$', BUILD, "compileSdk"),
    "targetSdk": capture(r'^\s*targetSdk\s*=\s*(\d+)\s*$', BUILD, "targetSdk"),
    "minSdk": capture(r'^\s*minSdk\s*=\s*(\d+)\s*$', BUILD, "minSdk"),
    "versionCode": capture(r'^\s*versionCode\s*=\s*(\d+)\s*$', BUILD, "versionCode"),
    "versionName": capture(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', BUILD, "versionName"),
    "Room version": capture(r'^internal const val APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)\s*$', DB, "Room version"),
}

required_docs = [
    "README.md", "CHANGELOG.md", "LICENSES.md", "ARCHITECTURE-CURRENT.md", "ARCHITECTURE-FREEZE.md",
    "PRODUCT-TERMINOLOGY.md", "UI-STANDARDS.md", "UI-COMPONENT-INVENTORY.md",
    "docs/SECURITY.md", "docs/TESTING.md", "docs/RELEASE_GUIDE.md", "docs/MAINTENANCE.md",
    "docs/HANDOVER.md", "docs/KNOWN_ISSUES.md", "docs/DECISIONS.md", "docs/DEPENDENCIES.md",
    "docs/PRIVACY.md", "docs/GOOGLE_PLAY_DATA_SAFETY.md",
]
for rel in required_docs:
    if not (ROOT / rel).is_file():
        failures.append(f"missing required current documentation: {rel}")

if not readme_path.is_file():
    failures.append(f"README file does not exist: {readme_path}")
    readme = ""
else:
    readme = readme_path.read_text(encoding="utf-8")

# Exact, anchored field parsing. Values elsewhere in prose cannot satisfy this gate.
readme_fields: dict[str, str] = {}
for key in source:
    matches = re.findall(rf'^\s*{re.escape(key)}\s*=\s*(.*?)\s*$', readme, flags=re.M)
    if len(matches) != 1:
        failures.append(f"README must define exactly one canonical field '{key}', found {len(matches)}")
        continue
    readme_fields[key] = matches[0].strip().strip('`')

for key, expected in source.items():
    actual = readme_fields.get(key)
    if actual is not None and expected != "UNKNOWN" and actual != expected:
        failures.append(f"README field mismatch: {key}: expected={expected!r} actual={actual!r}")

release = (ROOT / "docs/RELEASE_GUIDE.md").read_text(encoding="utf-8") if (ROOT / "docs/RELEASE_GUIDE.md").is_file() else ""
for env in (
    "RESTAURANT_MANAGEMENT_KEYSTORE_PATH",
    "RESTAURANT_MANAGEMENT_KEYSTORE_PASSWORD",
    "RESTAURANT_MANAGEMENT_KEY_ALIAS",
    "RESTAURANT_MANAGEMENT_KEY_PASSWORD",
):
    if env not in BUILD or env not in release:
        failures.append(f"signing variable is not synchronized between source and release guide: {env}")

# Current documentation surface must not accidentally re-introduce a second canonical README.
if (ROOT / "README-fa.md").exists():
    failures.append("README-fa.md remains; canonical metadata must have one source of truth or an exact-sync policy")

for path in ROOT.glob("CHANGELOG-alpha*.md"):
    failures.append(f"stale alpha changelog remains in current root: {path.name}")
for stale in ("ARCHITECTURE-FA.md", "STATUS-FA.md", "BUILD-APK-WINDOWS-FA.md"):
    if (ROOT / stale).exists():
        failures.append(f"stale root documentation remains: {stale}")

if failures:
    print("DOCUMENTATION_SYNC=FAIL")
    for item in failures:
        print(f" - {item}")
    sys.exit(1)

print("DOCUMENTATION_SYNC=PASS")
for key, value in source.items():
    print(f"{key}={value}")
