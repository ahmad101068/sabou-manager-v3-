#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-07.py <phase3-source-root>')

root = Path(sys.argv[1]).resolve()
path = root / 'app/src/androidTest/java/ir/restaurant/management/data/db/Migration55To56Test.kt'
schema_path = root / 'app/schemas/ir.restaurant.management.data.db.AppDatabase/55.json'
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


# Preferred form: INSERT with an explicit column list.
explicit = re.compile(
    rf'(INSERT(?:\s+OR\s+\w+)?\s+INTO\s+{TABLE_RE}\s*\()([^)]*)(\)\s*VALUES\s*\()([^)]*)(\))',
    re.IGNORECASE | re.DOTALL,
)
matches = list(explicit.finditer(text))
if len(matches) == 1:
    match = matches[0]
    columns = match.group(2)
    values = match.group(4)
    if re.search(r'\brecoveryCodeHash\b', columns, re.IGNORECASE):
        print('PHASE3_HOTFIX_07=ALREADY_APPLIED')
        raise SystemExit(0)
    replacement = (
        match.group(1)
        + columns.rstrip() + ', recoveryCodeHash'
        + match.group(3)
        + values.rstrip() + ', ' + FIXTURE_VALUE
        + match.group(5)
    )
    path.write_text(text[:match.start()] + replacement + text[match.end():], encoding='utf-8')
    print('PHASE3_HOTFIX_07=APPLIED_EXPLICIT_COLUMNS')
    raise SystemExit(0)

if len(matches) > 1:
    raise SystemExit(f'{path}: ambiguous app_users INSERT fixtures with explicit columns: {len(matches)}')

# Legacy fixture form: INSERT INTO app_users VALUES (...). Derive recoveryCodeHash position from v55 Room schema.
no_columns = re.compile(
    rf'(INSERT(?:\s+OR\s+\w+)?\s+INTO\s+{TABLE_RE}\s+VALUES\s*\()([^)]*)(\))',
    re.IGNORECASE | re.DOTALL,
)
matches = list(no_columns.finditer(text))
if len(matches) == 1:
    schema = json.loads(schema_path.read_text(encoding='utf-8'))
    entity = next((e for e in schema['database']['entities'] if e.get('tableName') == 'app_users'), None)
    if entity is None:
        raise SystemExit(f'{schema_path}: app_users entity missing')
    columns = [f['columnName'] for f in entity['fields']]
    try:
        recovery_index = columns.index('recoveryCodeHash')
    except ValueError as exc:
        raise SystemExit(f'{schema_path}: recoveryCodeHash column missing') from exc

    match = matches[0]
    values = split_sql_values(match.group(2))
    if len(values) != len(columns):
        raise SystemExit(
            f'{path}: app_users positional fixture has {len(values)} values but v55 schema has {len(columns)} columns'
        )
    current = values[recovery_index].strip()
    if current.upper() != 'NULL' and current not in ("''", '""'):
        print('PHASE3_HOTFIX_07=ALREADY_VALID_POSITIONAL')
        raise SystemExit(0)
    values[recovery_index] = FIXTURE_VALUE
    replacement = match.group(1) + ', '.join(values) + match.group(3)
    path.write_text(text[:match.start()] + replacement + text[match.end():], encoding='utf-8')
    print('PHASE3_HOTFIX_07=APPLIED_POSITIONAL')
    raise SystemExit(0)

# Fail closed with source context so a new fixture shape cannot silently bypass the gate.
context = []
for no, line in enumerate(text.splitlines(), 1):
    if 'app_users' in line.lower() or 'recoverycodehash' in line.lower():
        context.append(f'{no}: {line.strip()}')
if context:
    print('PHASE3_HOTFIX_07_CONTEXT:', file=sys.stderr)
    print('\n'.join(context[:20]), file=sys.stderr)
raise SystemExit(f'{path}: expected exactly one supported app_users INSERT fixture, found {len(matches)} positional fixtures')
