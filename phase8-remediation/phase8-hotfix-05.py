from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'phase8-source')
crm = root / 'app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt'
e2e = root / 'app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt'

text = crm.read_text()
old = '''                    emptyMessage = "مطالبه بازی برای این شعبه وجود ندارد.",
                    onRowClick = { collectingReceivable = it },
'''
new = '''                    emptyMessage = "مطالبه بازی برای این شعبه وجود ندارد.",
                    listTestTag = "receivables_open_list",
                    rowTestTag = { "receivable_select_${it.id}" },
                    onRowClick = { collectingReceivable = it },
'''
if old in text:
    text = text.replace(old, new, 1)
elif 'listTestTag = "receivables_open_list"' not in text or 'rowTestTag = { "receivable_select_${it.id}" }' not in text:
    raise SystemExit('open receivables list anchor missing')
crm.write_text(text)

text = e2e.read_text()
old = '''        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.receivableService.observeOpen(branchId).first().any { it.id == receivableId } } &&
                composeRule.onAllNodesWithText(customerName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(customerName)[0].performClick()
'''
new = '''        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.receivableService.observeOpen(branchId).first().any { it.id == receivableId } }
        }
        scrollTo("crm_list", "receivables_open_list")
        scrollTo("receivables_open_list", "receivable_select_$receivableId")
        composeRule.onNodeWithTag("receivable_select_$receivableId").performClick()
'''
if old in text:
    text = text.replace(old, new, 1)
elif 'scrollTo("receivables_open_list", "receivable_select_$receivableId")' not in text:
    raise SystemExit('CRM receivable viewport wait anchor missing')
e2e.write_text(text)

final_crm = crm.read_text()
final_e2e = e2e.read_text()
checks = (
    (final_crm, 'listTestTag = "receivables_open_list"'),
    (final_crm, 'rowTestTag = { "receivable_select_${it.id}" }'),
    (final_e2e, 'scrollTo("crm_list", "receivables_open_list")'),
    (final_e2e, 'scrollTo("receivables_open_list", "receivable_select_$receivableId")'),
    (final_e2e, 'composeRule.onNodeWithTag("receivable_select_$receivableId").performClick()'),
)
for text, needle in checks:
    if needle not in text:
        raise SystemExit(f'missing Phase8 CRM lazy-list invariant: {needle}')
if 'composeRule.onAllNodesWithText(customerName)[0].performClick()' in final_e2e:
    raise SystemExit('stale viewport-dependent CRM click remains')
print('PHASE8_HOTFIX_05_CRM_LAZY_LIST_NAVIGATION=PASS')
