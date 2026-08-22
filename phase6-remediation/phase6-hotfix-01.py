#!/usr/bin/env python3
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "Permission.PROCUREMENT"
replacement = "Permission.PURCHASES"
count = text.count(needle)
if count != 1:
    raise SystemExit(f"expected exactly one {needle} reference, found {count}")
path.write_text(text.replace(needle, replacement), encoding="utf-8")
print("PHASE6_HOTFIX_01=APPLIED_PERMISSION_PURCHASES")
