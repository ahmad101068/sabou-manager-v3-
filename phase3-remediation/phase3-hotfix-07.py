#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-07.py <phase3-source-root>')
root = Path(sys.argv[1]).resolve()
path = root / 'app/src/androidTest/java/ir/restaurant/management/data/db/Migration55To56Test.kt'
text = path.read_text(encoding='utf-8')

# The v55 fixture must itself satisfy the real v55 app_users contract before MIGRATION_55_56 runs.
# Keep this test-only: production schema/migration behavior is not relaxed or bypassed.
pattern = re.compile(
    r'(INSERT(?:\s+OR\s+\w+)?\s+INTO\s+app_users\s*\()([^)]*)(\)\s*VALUES\s*\()([^)]*)(\))',
    re.IGNORECASE | re.DOTALL,
)
matches = list(pattern.finditer(text))
if len(matches) != 1:
    raise SystemExit(f'{path}: expected exactly one app_users INSERT fixture, found {len(matches)}')
match = matches[0]
columns = match.group(2)
values = match.group(4)
if re.search(r'\brecoveryCodeHash\b', columns, re.IGNORECASE):
    print('PHASE3_HOTFIX_07=ALREADY_APPLIED')
    raise SystemExit(0)

new_columns = columns.rstrip() + ', recoveryCodeHash'
new_values = values.rstrip() + ", 'phase3-migration-fixture-recovery-hash'"
replacement = match.group(1) + new_columns + match.group(3) + new_values + match.group(5)
text = text[:match.start()] + replacement + text[match.end():]
path.write_text(text, encoding='utf-8')
print('PHASE3_HOTFIX_07=APPLIED')
