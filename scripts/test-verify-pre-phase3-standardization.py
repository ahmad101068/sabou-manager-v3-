#!/usr/bin/env python3
from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = Path("app/src/main/java/ir/restaurant/management/data/repository/DashboardRepository.kt")
VERIFY = Path("scripts/verify-pre-phase3-standardization.py")
OLD = "        branchId: Long? = null,\n        warehouseLocationId: Long? = null,"
NEW = "        branchName: String? = null,\n        warehouseLocationId: Long? = null,"

with tempfile.TemporaryDirectory(prefix="prephase3-branch-negative-") as tmp:
    temp_root = Path(tmp) / "repo"
    shutil.copytree(
        ROOT,
        temp_root,
        ignore=shutil.ignore_patterns(".git", ".gradle", "build", "*.apk", "*.aab"),
    )
    target = temp_root / REPOSITORY
    source = target.read_text(encoding="utf-8")
    if OLD not in source:
        print("CANONICAL_BRANCH_NEGATIVE_TEST=FAIL missing mutation anchor")
        sys.exit(1)
    target.write_text(source.replace(OLD, NEW, 1), encoding="utf-8")
    proc = subprocess.run(
        [sys.executable, str(temp_root / VERIFY)],
        cwd=temp_root,
        capture_output=True,
        text=True,
    )

if proc.returncode == 0:
    print("CANONICAL_BRANCH_NEGATIVE_TEST=FAIL verifier accepted branchName identity")
    sys.exit(1)
if "DashboardRepository selection identity must be branchId: Long?" not in proc.stdout:
    print("CANONICAL_BRANCH_NEGATIVE_TEST=FAIL expected branch identity failure not reported")
    print(proc.stdout)
    sys.exit(1)

print("CANONICAL_BRANCH_NEGATIVE_TEST=PASS")
