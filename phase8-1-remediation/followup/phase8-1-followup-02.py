#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / "app/src/test/java/ir/restaurant/management/data/security/SensitiveActionGateTest.kt"
text = path.read_text(encoding="utf-8")
before = text

resource_type_count = text.count("resourceType =")
resource_id_count = text.count("resourceId =")
if resource_type_count != 1:
    raise SystemExit(f"expected exactly 1 resourceType target, found {resource_type_count}")
if resource_id_count != 2:
    raise SystemExit(f"expected exactly 2 resourceId occurrences before alignment, found {resource_id_count}")

text = text.replace("resourceType =", "type =", 1)
text = text.replace("resourceId =", "id =", 1)

if text.count("resourceType =") != 0:
    raise SystemExit("SensitiveActionGateTest still contains legacy resourceType argument")
if text.count("resourceId =") != 1:
    raise SystemExit("SensitiveActionGateTest resourceId preservation invariant failed")
if text == before:
    raise SystemExit("SensitiveActionGateTest API alignment made no change")

path.write_text(text, encoding="utf-8")
print("PHASE8_1_TEST_API_ALIGNMENT=PASS")
print("REPLACED_RESOURCE_TYPE=1")
print("REPLACED_RESOURCE_ID=1")
print("PRESERVED_RESOURCE_ID=1")
