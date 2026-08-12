#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/androidTest/java/ir/sabou/inventory/data/db/ProcurementMigration23To24Test.kt")
text = path.read_text(encoding="utf-8")
old = 'replace("${TABLE_NAME}", tableName)'
new = 'replace("${\'$\'}{TABLE_NAME}", tableName)'
count = text.count(old)
if count != 2:
    raise SystemExit(f"Expected exactly 2 Room schema TABLE_NAME placeholders, found {count}")
text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
print("DASHBOARD_UX2_ROOM_SCHEMA_LITERAL_FIX=PASS replacements=2")
