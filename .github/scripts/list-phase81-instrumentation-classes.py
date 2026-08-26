#!/usr/bin/env python3
import argparse
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('root')
parser.add_argument('--shards', type=int, default=1)
parser.add_argument('--shard', type=int)
parser.add_argument('--summary', action='store_true')
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

# Greedy bin packing by static @Test count keeps shards balanced while assigning
# each complete test class to exactly one fresh emulator.
bins = [[] for _ in range(args.shards)]
loads = [0 for _ in range(args.shards)]
for fqcn, count, path in sorted(entries, key=lambda item: (-item[1], item[0])):
    target = min(range(args.shards), key=lambda idx: (loads[idx], idx))
    bins[target].append((fqcn, count, path))
    loads[target] += count

if sorted(fqcn for bucket in bins for fqcn, _, _ in bucket) != sorted(fqcn for fqcn, _, _ in entries):
    raise SystemExit('class sharding lost or duplicated instrumentation classes')

if args.summary:
    print(f'PHASE81_INSTRUMENTATION_INVENTORY=PASS classes={len(entries)} tests=230 shards={args.shards} loads={",".join(map(str, loads))}')
    for idx, bucket in enumerate(bins):
        print(f'SHARD_{idx}_CLASSES={len(bucket)} SHARD_{idx}_TESTS={loads[idx]}')
    raise SystemExit(0)

if args.shard is None:
    print('\n'.join(fqcn for fqcn, _, _ in entries))
    raise SystemExit(0)
if not 0 <= args.shard < args.shards:
    raise SystemExit(f'--shard must be in [0,{args.shards - 1}]')
print(','.join(fqcn for fqcn, _, _ in sorted(bins[args.shard], key=lambda item: item[0])))
