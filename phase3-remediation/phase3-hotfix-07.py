#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-07.py <phase3-source-root>')
root = Path(sys.argv[1]).resolve()
path = root / 'app/src/main/java/ir/restaurant/management/data/db/migration/AccountingBranchScopeMigration.kt'
text = path.read_text(encoding='utf-8')
old = 'Phase-2 branchId=1 was only a migration'
new = 'Phase-2 default-branch assignment was only a migration'
if text.count(old) != 1:
    raise SystemExit(f'{path}: expected one legacy migration comment marker, found {text.count(old)}')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
print('PHASE3_HOTFIX_07=APPLIED')
