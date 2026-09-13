#!/usr/bin/env python3
import sys
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
label = sys.argv[2]
xmls = list(root.rglob('TEST-*.xml'))
if not xmls:
    raise SystemExit(f'{label}: no connected test XML')

total = fail = errors = skipped = 0
cases = []
for path in xmls:
    text = path.read_text(errors='ignore')
    if 'initializationError' in text:
        raise SystemExit(f'{label}: initializationError in {path}')
    suite = ET.parse(path).getroot()
    total += int(suite.attrib.get('tests', 0))
    fail += int(suite.attrib.get('failures', 0))
    errors += int(suite.attrib.get('errors', 0))
    skipped += int(suite.attrib.get('skipped', 0))
    for case in suite.findall('.//testcase'):
        cases.append((case.attrib.get('classname'), case.attrib.get('name')))

counts = Counter(cases)
duplicates = sorted(case for case, count in counts.items() if count != 1)
seen = set(cases)
required_classes = {
    'ir.restaurant.management.data.db.FullMigration1ToCurrentTest',
    'ir.restaurant.management.data.db.Migration59To60Test',
    'ir.restaurant.management.data.db.Phase81RecentMigrationMatrixTest',
    'ir.restaurant.management.data.repository.Phase81AuditIntegrityIntegrationTest',
    'ir.restaurant.management.data.security.Phase81ForensicIntegrityLedgerIntegrationTest',
    'ir.restaurant.management.data.repository.Phase81UserOptimisticConcurrencyIntegrationTest',
    'ir.restaurant.management.data.repository.Phase81LargeDataPerformanceIntegrationTest',
    'ir.restaurant.management.ui.EnterpriseCoreComposeE2ETest',
    'ir.restaurant.management.data.repository.DailySalesReversalIntegrationTest',
    'ir.restaurant.management.data.repository.BranchPurchasePostingIntegrationTest',
    'ir.restaurant.management.data.repository.BranchPayrollPostingIntegrationTest',
    'ir.restaurant.management.data.repository.AssetLifecycleIntegrationTest',
    'ir.restaurant.management.data.repository.EnterprisePermissionIntegrationTest',
    'ir.restaurant.management.data.repository.TreasuryV2IntegrationTest',
}
seen_classes = {class_name for class_name, _ in seen}
missing = required_classes - seen_classes
if fail or errors or skipped:
    raise SystemExit(f'{label}: tests={total} failures={fail} errors={errors} skipped={skipped}')
if missing:
    raise SystemExit(f'{label}: mandatory classes missing: {sorted(missing)}')
if duplicates:
    raise SystemExit(f'{label}: duplicate testcase identities across shards: {duplicates[:20]}')
if total != 230:
    raise SystemExit(f'{label}: expected exactly 230 instrumentation tests, got {total}')
if len(seen) != 230:
    raise SystemExit(f'{label}: expected exactly 230 unique testcase identities, got {len(seen)}')
print(f'{label}_FULL_INSTRUMENTATION=PASS tests=230 unique=230 failures=0 errors=0 skipped=0')
