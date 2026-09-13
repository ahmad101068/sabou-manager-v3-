#!/usr/bin/env python3
import argparse
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('root')
parser.add_argument('--shards', type=int, default=1)
parser.add_argument('--shard', type=int)
parser.add_argument('--summary', action='store_true')
parser.add_argument('--plan', action='store_true')
parser.add_argument('--only', help='comma-separated FQCN allow-list')
args = parser.parse_args()

root = Path(args.root)
files = sorted(root.rglob('*Test.kt'))
if not files:
    raise SystemExit(f'no *Test.kt files under {root}')

entries = []
for path in files:
    text = path.read_text(errors='strict')
    pkg_match = re.search(r'(?m)^\s*package\s+([A-Za-z_][\w.]*)\s*$', text)
    class_matches = re.findall(r'(?m)^\s*(?:(?:public|private|internal|protected)\s+)?class\s+([A-Za-z_][\w]*)\b', text)
    test_count = len(re.findall(r'(?m)^\s*@Test\b', text))
    if not pkg_match:
        raise SystemExit(f'missing package in {path}')
    if len(class_matches) != 1:
        raise SystemExit(f'expected exactly one top-level class in {path}, got {class_matches}')
    if test_count <= 0:
        raise SystemExit(f'no @Test methods in {path}')
    entries.append((f'{pkg_match.group(1)}.{class_matches[0]}', test_count, path.as_posix()))

if len(entries) != 89:
    raise SystemExit(f'expected 89 instrumentation test classes, got {len(entries)}')
if sum(test_count for _, test_count, _ in entries) != 230:
    raise SystemExit(f'expected 230 instrumentation @Test methods, got {sum(test_count for _, test_count, _ in entries)}')
if args.shards <= 0:
    raise SystemExit('--shards must be positive')

selected = entries
if args.only:
    requested = [item.strip() for item in args.only.split(',') if item.strip()]
    if len(requested) != len(set(requested)):
        raise SystemExit('--only contains duplicate classes')
    by_name = {fqcn: (fqcn, count, path) for fqcn, count, path in entries}
    missing = sorted(set(requested) - set(by_name))
    if missing:
        raise SystemExit(f'--only contains unknown instrumentation classes: {missing}')
    selected = [by_name[fqcn] for fqcn in requested]

# Greedy bin packing by static @Test count keeps shards balanced while assigning
# each complete test class to exactly one fresh emulator.
bins = [[] for _ in range(args.shards)]
loads = [0 for _ in range(args.shards)]
for fqcn, count, path in sorted(selected, key=lambda item: (-item[1], item[0])):
    target = min(range(args.shards), key=lambda idx: (loads[idx], idx))
    bins[target].append((fqcn, count, path))
    loads[target] += count

if sorted(fqcn for bucket in bins for fqcn, _, _ in bucket) != sorted(fqcn for fqcn, _, _ in selected):
    raise SystemExit('class sharding lost or duplicated instrumentation classes')

if args.summary:
    scope = 'full' if not args.only else 'filtered'
    print(
        f'PHASE81_INSTRUMENTATION_INVENTORY=PASS scope={scope} '
        f'classes={len(selected)} tests={sum(loads)} shards={args.shards} '
        f'loads={",".join(map(str, loads))}'
    )
    for idx, bucket in enumerate(bins):
        print(f'SHARD_{idx}_CLASSES={len(bucket)} SHARD_{idx}_TESTS={loads[idx]}')
    raise SystemExit(0)

if args.shard is None:
    bucket = sorted(selected, key=lambda item: item[0])
else:
    if not 0 <= args.shard < args.shards:
        raise SystemExit(f'--shard must be in [0,{args.shards - 1}]')
    bucket = sorted(bins[args.shard], key=lambda item: item[0])

if args.plan:
    for fqcn, count, _ in bucket:
        print(f'{fqcn}\t{count}')
else:
    print(','.join(fqcn for fqcn, _, _ in bucket))
