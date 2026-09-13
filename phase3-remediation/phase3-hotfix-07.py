#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-07.py <phase3-source-root>')

root = Path(sys.argv[1]).resolve()
path = root / 'app/src/androidTest/java/ir/restaurant/management/data/db/Migration55To56Test.kt'
text = path.read_text(encoding='utf-8')
FIXTURE_VALUE = "'phase3-migration-fixture-recovery-hash'"
TABLE_RE = r'[`"\[]?app_users[`"\]]?'


def split_sql_values(raw: str) -> list[str]:
    values: list[str] = []
    start = 0
    depth = 0
    quote = None
    i = 0
    while i < len(raw):
        ch = raw[i]
        if quote:
            if ch == quote:
                if i + 1 < len(raw) and raw[i + 1] == quote:
                    i += 2
                    continue
                quote = None
            elif ch == '\\':
                i += 2
                continue
        else:
            if ch in ("'", '"'):
                quote = ch
            elif ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
            elif ch == ',' and depth == 0:
                values.append(raw[start:i].strip())
                start = i + 1
        i += 1
    values.append(raw[start:].strip())
    return values


def normalize_column(raw: str) -> str:
    return raw.strip().strip('`"[]')


# Kotlin fixture may concatenate two string literals between the column list and VALUES.
pattern = re.compile(
    rf'(INSERT(?:\s+OR\s+\w+)?\s+INTO\s+{TABLE_RE}\s*\()'
    r'([^)]*)'
    r'(\)\s*(?:"\s*\+\s*")?\s*VALUES\s*\()'
    r'([^)]*)'
    r'(\))',
    re.IGNORECASE | re.DOTALL,
)
matches = list(pattern.finditer(text))
if len(matches) != 1:
    context = []
    for no, line in enumerate(text.splitlines(), 1):
        if 'app_users' in line.lower() or 'recoverycodehash' in line.lower() or 'values(' in line.lower():
            context.append(f'{no}: {line.strip()}')
    if context:
        print('PHASE3_HOTFIX_07_CONTEXT:', file=sys.stderr)
        print('\n'.join(context[:24]), file=sys.stderr)
    raise SystemExit(f'{path}: expected exactly one app_users INSERT fixture, found {len(matches)}')

match = matches[0]
columns = [normalize_column(c) for c in split_sql_values(match.group(2))]
values = split_sql_values(match.group(4))
if len(columns) != len(values):
    raise SystemExit(f'{path}: app_users fixture column/value mismatch: {len(columns)} columns vs {len(values)} values')
try:
    recovery_index = columns.index('recoveryCodeHash')
except ValueError as exc:
    raise SystemExit(f'{path}: app_users fixture is missing recoveryCodeHash column') from exc

current = values[recovery_index].strip()
if current.upper() != 'NULL':
    print('PHASE3_HOTFIX_07=ALREADY_VALID')
    raise SystemExit(0)

values[recovery_index] = FIXTURE_VALUE
replacement = (
    match.group(1)
    + match.group(2)
    + match.group(3)
    + ', '.join(values)
    + match.group(5)
)
path.write_text(text[:match.start()] + replacement + text[match.end():], encoding='utf-8')
print('PHASE3_HOTFIX_07=APPLIED_NULL_RECOVERY_CODE')
