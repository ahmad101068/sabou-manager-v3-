#!/usr/bin/env python3
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(sys.argv[1]); label=sys.argv[2]
xmls=list(root.rglob('TEST-*.xml'))
if not xmls: raise SystemExit(f'{label}: no connected test XML')
total=fail=errors=skipped=0; seen=set()
for path in xmls:
    text=path.read_text(errors='ignore')
    if 'initializationError' in text: raise SystemExit(f'{label}: initializationError in {path}')
    suite=ET.parse(path).getroot()
    total += int(suite.attrib.get('tests',0)); fail += int(suite.attrib.get('failures',0)); errors += int(suite.attrib.get('errors',0)); skipped += int(suite.attrib.get('skipped',0))
    for case in suite.findall('.//testcase'): seen.add((case.attrib.get('classname'), case.attrib.get('name')))
expected={
('ir.restaurant.management.data.db.FullMigration1ToCurrentTest','migratesVersionOneWithoutDestructiveFallback'),
('ir.restaurant.management.ui.EnterpriseCoreComposeE2ETest','purchaseGoodsReceipt_uiIncreasesStockAndPostsBalancedReceiptJournal'),
('ir.restaurant.management.ui.EnterpriseCoreComposeE2ETest','payrollRegistrationAndApproval_uiCalculatesReviewsAndPostsAccrual'),
('ir.restaurant.management.ui.EnterpriseCoreComposeE2ETest','assetDepreciation_uiUpdatesBookValueAndPostsBalancedJournal'),
('ir.restaurant.management.ui.EnterpriseCoreComposeE2ETest','treasuryReversal_uiCreatesCompensatingJournalLedgerReferenceAndMarksOriginalReversed'),
('ir.restaurant.management.data.repository.Phase2CorrectionIntegrationTest','fiveSettlementLifecycleDuplicatePostingCollectionAndReverseAreReal'),
('ir.restaurant.management.data.repository.DailySalesReversalIntegrationTest','reversalRestoresInventoryLotsAndAccountingThenAllowsCorrectedRepost'),
('ir.restaurant.management.data.repository.ManagementControlTransactionIntegrationTest','closePeriodCommitsOneAuditAndOutboxInSameTransaction'),
('ir.restaurant.management.data.repository.RecipeVersionIntegrationTest','appendsRevisionsAndSelectsFormulaByBusinessDate'),
}
missing=expected-seen
if fail or errors or skipped: raise SystemExit(f'{label}: tests={total} failures={fail} errors={errors} skipped={skipped}')
if missing: raise SystemExit(f'{label}: required E2E tests missing: {sorted(missing)}')
if total < 35: raise SystemExit(f'{label}: expected >=35 tests, got {total}')
print(f'{label}_INTEGRITY=PASS tests={total}')
