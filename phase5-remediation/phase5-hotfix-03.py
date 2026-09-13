#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: phase5-hotfix-03.py <source-root>")

root = Path(sys.argv[1])
path = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/DailySalesReversalIntegrationTest.kt"
text = path.read_text(encoding="utf-8")

old_first = "            branchId = 1L,\n        )"
new_first = "            branchId = 1L,\n            locationId = locationId,\n        )"
old_second = "                branchId = 1L,\n            ),"
new_second = "                branchId = 1L,\n                locationId = locationId,\n            ),"

if text.count(new_first) == 1 and text.count(new_second) == 1:
    print("PHASE5_HOTFIX_03=ALREADY_APPLIED")
    raise SystemExit(0)

if text.count(old_first) != 1:
    raise SystemExit(f"expected exactly one primary DailySalesDraft branch fixture, found {text.count(old_first)}")
if text.count(old_second) != 1:
    raise SystemExit(f"expected exactly one replacement DailySalesDraft branch fixture, found {text.count(old_second)}")

text = text.replace(old_first, new_first, 1)
text = text.replace(old_second, new_second, 1)

if text.count(new_first) != 1 or text.count(new_second) != 1:
    raise SystemExit("post-apply Daily Sales explicit location invariant failed")

path.write_text(text, encoding="utf-8")
print("PHASE5_HOTFIX_03=APPLIED_EXPLICIT_LOCATION")
