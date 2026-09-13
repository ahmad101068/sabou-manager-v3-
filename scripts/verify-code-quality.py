#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
TEST_ROOTS = [ROOT / "app/src/test", ROOT / "app/src/androidTest"]
failures: list[str] = []

def source_files(root: Path):
    if not root.exists():
        return []
    return [p for p in root.rglob('*') if p.is_file() and p.suffix in {'.kt', '.java', '.xml', '.sql'}]

main_files = source_files(MAIN)
test_files = [p for root in TEST_ROOTS for p in source_files(root)]

def rel(p: Path) -> str:
    return str(p.relative_to(ROOT))

def fail(message: str) -> None:
    failures.append(message)

# Detect executable placeholders, not legitimate enum/status names such as ManagementTaskStatus.TODO.
placeholder_patterns = {
    "TODO()": re.compile(r"\bTODO\s*\("),
    "NotImplementedError": re.compile(r"\bNotImplementedError\s*\("),
    "FIXME comment": re.compile(r"(?m)^\s*//\s*FIXME\b|/\*[^*]*FIXME", re.S),
    "Coming Soon": re.compile(r"\bComing\s+Soon\b|\bComingSoon\b"),
    "UnsupportedOperationException": re.compile(r"\bUnsupportedOperationException\s*\("),
}
for marker, pattern in placeholder_patterns.items():
    hits = [rel(p) for p in main_files if pattern.search(p.read_text(encoding='utf-8', errors='ignore'))]
    if hits:
        fail(f"production marker {marker!r}: {hits[:8]}")

for p in main_files:
    body = p.read_text(encoding='utf-8', errors='ignore')
    if re.search(r"catch\s*\([^)]*\)\s*\{\s*\}", body, flags=re.S):
        fail(f"empty catch: {rel(p)}")
    if re.search(r"catch\s*\([^)]*Exception[^)]*\)\s*\{(?:(?!\}).)*Result\.success", body, flags=re.S):
        fail(f"exception converted to success: {rel(p)}")
    if "fallbackToDestructiveMigration" in body:
        fail(f"destructive migration fallback: {rel(p)}")
    if re.search(r"(?i)(amount|price|cost|rial|money|balance|salary|wage|tax|discount|total)[A-Za-z0-9_]*\s*:\s*(Float|Double)\b", body):
        fail(f"floating-point money contract: {rel(p)}")
    if re.search(r"(?m)^\s*(class|object)\s+(Fake|Mock|Demo)[A-Za-z0-9_]*(Repository|Service|Dao)\b", body):
        fail(f"fake/mock/demo production data boundary: {rel(p)}")
    if re.search(r"Log\.[a-zA-Z]+\([^\n]*(password|passwd|token|secret|pin)", body, flags=re.I):
        fail(f"possible sensitive log: {rel(p)}")

for p in test_files:
    body = p.read_text(encoding='utf-8', errors='ignore')
    if re.search(r"@(Ignore|Disabled)\b", body) or re.search(r"assume(?:True|False)\s*\(\s*false", body):
        fail(f"disabled/skipped test: {rel(p)}")

legacy_adapter = ROOT / "app/src/main/java/ir/restaurant/management/data/treasury/LocalTreasuryServiceAdapter.kt"
if legacy_adapter.exists():
    fail("dead LocalTreasuryServiceAdapter returned to production")


# UnsupportedDomainOperation may remain as a typed error and renderer, but no active production caller may construct/throw it.
for p in main_files:
    body = p.read_text(encoding='utf-8', errors='ignore')
    if 'UnsupportedDomainOperation(' in body and not rel(p).endswith('domain/common/BusinessError.kt'):
        fail(f"active UnsupportedDomainOperation construction: {rel(p)}")

# A runCatching used for a business command must surface the failure instead of converting it to null/default.
for p in main_files:
    if '/data/Backup' in rel(p):
        continue  # backup/restore is explicitly out of scope for this completion mission.
    body = p.read_text(encoding='utf-8', errors='ignore')
    for match in re.finditer(r"runCatching\s*\{([^{}]|\{[^{}]*\}){0,800}\}\s*\.(getOrNull|getOrDefault)\s*\(", body, flags=re.S):
        snippet = match.group(0)
        if re.search(
            r"(?:repository|Repository|useCases|UseCases|service|Service)\s*\.|"
            r"container\.(?:run|save|post|reverse|execute|resolve|sync)\s*\(",
            snippet,
        ):
            fail(f"silent runCatching around business operation: {rel(p)}")

# The pre-authenticated process must not schedule protected Alerts/Sync work.
management_application = (ROOT / 'app/src/main/java/ir/restaurant/management/RestaurantManagementApplication.kt').read_text(encoding='utf-8')
if 'ProtectedWorkScheduler.disable(this)' not in management_application:
    fail('protected background work is not disabled at process bootstrap')
management_app = (ROOT / 'app/src/main/java/ir/restaurant/management/ui/RestaurantManagementApp.kt').read_text(encoding='utf-8')
if 'owner.viewModelStore.clear()' not in management_app or 'if (authenticatedUser == null)' not in management_app:
    fail('protected Compose graph is not session-scoped')

# Sync credentials may only be sent over a validated HTTPS connection and redirects are
# deliberately not followed so Authorization/refresh-token material cannot cross hosts.
for relative in [
    'app/src/main/java/ir/restaurant/management/data/repository/HttpsSyncTransport.kt',
    'app/src/main/java/ir/restaurant/management/data/repository/SyncTokenRefresher.kt',
]:
    network_source = (ROOT / relative).read_text(encoding='utf-8')
    if 'HttpsURLConnection' not in network_source:
        fail(f'sync transport is not pinned to HttpsURLConnection: {relative}')
    if 'instanceFollowRedirects = false' not in network_source and 'instanceFollowRedirects=false' not in network_source:
        fail(f'sync transport follows redirects: {relative}')

# A current Room database may not silently claim schema proof without the exported JSON
# for the version declared by production source. Historical files are useful evidence, but
# they are not proof of the current schema.
db_source = ROOT / "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
db_text = db_source.read_text(encoding="utf-8")
version_match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)", db_text)
if version_match is None:
    version_match = re.search(r"version\s*=\s*(\d+)", db_text)
if version_match is None:
    fail("cannot determine current Room schema version from AppDatabase.kt")
    current_room_version = None
else:
    current_room_version = int(version_match.group(1))

schema_dir = ROOT / "app/schemas/ir.restaurant.management.data.db.AppDatabase"
exported_versions = sorted(
    (int(p.stem) for p in schema_dir.glob("*.json") if p.stem.isdigit()),
) if schema_dir.exists() else []
latest_schema_version = exported_versions[-1] if exported_versions else None

if failures:
    print("CODE_QUALITY=FAIL")
    for item in failures:
        print(f" - {item}")
    sys.exit(1)

print(f"STATIC_CODE_QUALITY=PASS production_files={len(main_files)} test_files={len(test_files)}")
print(f"CURRENT_ROOM_VERSION={current_room_version if current_room_version is not None else 'UNKNOWN'}")
print(f"LATEST_SCHEMA_FILE={latest_schema_version if latest_schema_version is not None else 'NONE'}")
if current_room_version is not None and current_room_version in exported_versions:
    print("ROOM_SCHEMA_EVIDENCE=PASS")
    print("OVERALL_VERIFICATION=PASS")
else:
    print("ROOM_SCHEMA_EVIDENCE=PENDING")
    print("OVERALL_VERIFICATION=BLOCKED")
    sys.exit(2)
