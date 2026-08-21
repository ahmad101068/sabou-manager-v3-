#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase4-hotfix-05.py <phase4-source-root>')

root = Path(sys.argv[1]).resolve()
path = root / 'app/src/main/java/ir/restaurant/management/data/db/HrPayrollEntities.kt'
text = path.read_text(encoding='utf-8')
needle = 'import androidx.room.ColumnInfo\n'
if needle in text:
    print('PHASE4_HOTFIX_05=ALREADY_APPLIED')
    raise SystemExit(0)
anchor = 'import androidx.room.Entity\n'
if text.count(anchor) != 1:
    raise SystemExit(f'{path}: expected exactly one Entity import anchor')
if '@ColumnInfo' not in text:
    raise SystemExit(f'{path}: ColumnInfo annotation not found; refusing blind import insertion')
text = text.replace(anchor, needle + anchor, 1)
path.write_text(text, encoding='utf-8')
print('PHASE4_HOTFIX_05=APPLIED')
