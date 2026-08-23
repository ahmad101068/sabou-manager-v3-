#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / "app/src/test/java/ir/restaurant/management/data/security/SensitiveActionGateTest.kt"
text = path.read_text(encoding="utf-8")
before = text

replacements = {
    "resourceType =": "type =",
    "resourceId =": "id =",
}
counts = {}
for old, new in replacements.items():
    counts[old] = text.count(old)
    text = text.replace(old, new)

if not any(counts.values()):
    raise SystemExit("SensitiveActionGateTest API alignment target not found")
if "resourceType =" in text or "resourceId =" in text:
    raise SystemExit("SensitiveActionGateTest still contains legacy resource argument names")
if text == before:
    raise SystemExit("SensitiveActionGateTest API alignment made no change")

path.write_text(text, encoding="utf-8")
print("PHASE8_1_TEST_API_ALIGNMENT=PASS")
print("REPLACED_RESOURCE_TYPE=%d" % counts["resourceType ="])
print("REPLACED_RESOURCE_ID=%d" % counts["resourceId ="])
