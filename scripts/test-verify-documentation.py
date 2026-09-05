#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
VERIFY = ROOT / "scripts/verify-documentation.py"
original = README.read_text(encoding="utf-8")
room_line = next((line for line in original.splitlines() if line.strip().startswith("Room version =")), None)
if room_line is None:
    print("DOCUMENTATION_NEGATIVE_ROOM_VERSION=FAIL missing canonical Room field")
    sys.exit(1)


def must_detect(label: str, old: str, new: str) -> None:
    if old not in original:
        print(f"{label}=FAIL missing mutation anchor")
        sys.exit(1)
    mutated = original.replace(old, new, 1)
    with tempfile.TemporaryDirectory(prefix="doc-negative-") as tmp:
        path = Path(tmp) / "README.md"
        path.write_text(mutated, encoding="utf-8")
        proc = subprocess.run([sys.executable, str(VERIFY), "--readme", str(path)], cwd=ROOT, capture_output=True, text=True)
    if proc.returncode == 0:
        print(f"{label}=FAIL verifier accepted intentional mismatch")
        sys.exit(1)
    print(f"{label}=PASS")

must_detect("DOCUMENTATION_NEGATIVE_ROOM_VERSION", room_line, "Room version = 999")
must_detect("DOCUMENTATION_NEGATIVE_VERSION_NAME", "versionName = 1.0.0", "versionName = 999.0.0-bad")
