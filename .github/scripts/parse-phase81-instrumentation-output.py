#!/usr/bin/env python3
import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('raw')
parser.add_argument('xml')
parser.add_argument('label')
parser.add_argument('selector')
parser.add_argument('expected', type=int)
args = parser.parse_args()

raw = Path(args.raw).read_text(errors='replace')
status = {}
started = set()
outcomes = {}
declared_counts = []

for line in raw.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: '):
        payload = line[len('INSTRUMENTATION_STATUS: '):]
        if '=' in payload:
            key, value = payload.split('=', 1)
            key = key.strip()
            value = value.strip()
            status[key] = value
            if key == 'numtests':
                try:
                    declared_counts.append(int(value))
                except ValueError:
                    pass
    elif line.startswith('INSTRUMENTATION_STATUS_CODE:'):
        try:
            code = int(line.split(':', 1)[1].strip())
        except ValueError:
            status = {}
            continue
        class_name = status.get('class')
        test_name = status.get('test')
        if class_name and test_name:
            test = (class_name, test_name)
            if code == 1:
                started.add(test)
            elif code == 0:
                outcomes[test] = ('success', '')
            elif code in (-3, -4):
                outcomes[test] = (
                    'skipped',
                    status.get('stack') or status.get('stream') or f'instrumentation status code {code}',
                )
            elif code < 0:
                outcomes[test] = (
                    'failure',
                    status.get('stack') or status.get('stream') or f'instrumentation status code {code}',
                )
        status = {}

suite = ET.Element('testsuite', name=args.label)
failures = 0
skipped = 0
for (class_name, test_name), (kind, detail) in sorted(outcomes.items()):
    case = ET.SubElement(suite, 'testcase', classname=class_name, name=test_name, time='0')
    if kind == 'failure':
        failures += 1
        element = ET.SubElement(case, 'failure', message=(detail or 'instrumentation failure')[:500])
        element.text = detail
    elif kind == 'skipped':
        skipped += 1
        element = ET.SubElement(case, 'skipped', message=(detail or 'instrumentation skipped')[:500])
        element.text = detail
suite.set('tests', str(len(outcomes)))
suite.set('failures', str(failures))
suite.set('errors', '0')
suite.set('skipped', str(skipped))
Path(args.xml).parent.mkdir(parents=True, exist_ok=True)
ET.ElementTree(suite).write(args.xml, encoding='utf-8', xml_declaration=True)

problems = []
if declared_counts and any(count != args.expected for count in declared_counts):
    problems.append(f'runner declared numtests={sorted(set(declared_counts))}, expected={args.expected}')
if len(outcomes) != args.expected:
    problems.append(f'completed={len(outcomes)}/{args.expected}')
if failures or skipped:
    problems.append(f'failures={failures} skipped={skipped}')
incomplete = started - set(outcomes)
if incomplete:
    problems.append(f'incomplete started tests={sorted(incomplete)}')
if 'Process crashed' in raw or 'INSTRUMENTATION_FAILED' in raw or 'Instrumentation run failed due to Native crash' in raw:
    problems.append('instrumentation process crash/failure marker present')

if '#' in args.selector:
    expected_class, expected_method = args.selector.split('#', 1)
    if set(outcomes) != {(expected_class, expected_method)}:
        problems.append(f'method selector mismatch observed={sorted(outcomes)}')
else:
    wrong_classes = sorted({class_name for class_name, _ in outcomes if class_name != args.selector})
    if wrong_classes:
        problems.append(f'class selector mismatch observed={wrong_classes}')

if problems:
    print(f'{args.label}: FAIL selector={args.selector}: ' + '; '.join(problems), file=sys.stderr)
    raise SystemExit(1)
print(f'{args.label}: PASS selector={args.selector} tests={args.expected}')
