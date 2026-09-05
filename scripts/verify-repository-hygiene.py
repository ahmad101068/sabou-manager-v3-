#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []
known_binary = {"gradle/wrapper/gradle-wrapper.jar"}
known_backup_named_files = {"app/src/main/res/xml/backup_rules.xml"}
forbidden_dir_names = {"build", ".gradle", ".idea", "backup"}


def repository_paths() -> list[Path]:
    """Return files that are part of the repository handoff surface.

    In a Git worktree this means tracked files plus untracked files that are not
    excluded by .gitignore. CI-generated ignored output (for example app/build)
    is intentionally outside the repository surface and must not create a false
    hygiene failure after KSP/compile tasks have run.
    """
    try:
        result = subprocess.run(
            ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
            cwd=ROOT,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        return [ROOT / raw.decode("utf-8") for raw in result.stdout.split(b"\0") if raw]
    except (OSError, subprocess.CalledProcessError):
        return [path for path in ROOT.rglob("*") if path.is_file() and ".git" not in path.parts]


paths = repository_paths()

# Also inspect ignored files that are outside known generated/tooling directories.
# This catches a stray root APK/keystore/local.properties even if .gitignore would
# normally hide it, while still allowing ephemeral build outputs after CI tasks.
seen = {path.resolve() for path in paths}
for candidate in ROOT.rglob("*"):
    if not candidate.is_file() or ".git" in candidate.parts:
        continue
    rel_candidate = candidate.relative_to(ROOT)
    if any(part in forbidden_dir_names for part in rel_candidate.parts[:-1]):
        continue
    if candidate.resolve() not in seen:
        paths.append(candidate)
        seen.add(candidate.resolve())

for path in paths:
    rel_path = path.relative_to(ROOT)
    rel = rel_path.as_posix()
    if any(part in forbidden_dir_names for part in rel_path.parts[:-1]):
        failures.append(f"forbidden generated/backup directory content: {rel}")
        continue

    lower = path.name.lower()
    if lower == "old-source.zip":
        failures.append(f"historical source archive: {rel}")
    if "password" in lower and path.suffix.lower() not in {".md", ".kt", ".py", ".sh", ".yml", ".yaml"}:
        failures.append(f"password-named artifact: {rel}")
    if rel not in known_backup_named_files and (
        lower.endswith((".bak", ".old"))
        or re.search(r"(?:^|[_-])(backup|copy)(?:[_\-.]|$)", lower)
    ):
        failures.append(f"backup/copy artifact: {rel}")
    if path.suffix.lower() in {".apk", ".aab", ".aar", ".so", ".jks", ".keystore"}:
        failures.append(f"unexpected binary/release/secret artifact: {rel}")
    if path.suffix.lower() == ".jar" and rel not in known_binary:
        failures.append(f"unreviewed jar: {rel}")
    if path.name == "local.properties":
        failures.append(f"local machine configuration: {rel}")

if failures:
    print("REPOSITORY_HYGIENE=FAIL")
    for item in sorted(set(failures)):
        print(f" - {item}")
    sys.exit(1)

print("REPOSITORY_HYGIENE=PASS")
print("KNOWN_BINARY=gradle/wrapper/gradle-wrapper.jar")
print("BUILD_OUTPUTS_IN_REPOSITORY=NONE")
print("BACKUP_COPY_ARTIFACTS=NONE")
