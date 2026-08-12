#!/usr/bin/env python3
from pathlib import Path


def read(path): return Path(path).read_text(encoding='utf-8')
def write(path, text): Path(path).write_text(text, encoding='utf-8')
def replace_once(path, old, new, label):
    text=read(path)
    if new in text: return
    count=text.count(old)
    if count != 1: raise SystemExit(f'FIX17_REPLACE_FAIL:{label}:count={count}')
    write(path,text.replace(old,new,1))

VM='app/src/main/java/ir/sabou/inventory/ui/InventoryWorkspaceViewModel.kt'
PAY='app/src/main/java/ir/sabou/inventory/ui/HrPayrollScreens.kt'
E2E='app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt'

replace_once(VM,
'''    fun selectSection(section: InventoryWorkspaceSection) {\n        mutableState.update { it.copy(section = section, message = null) }\n    }''',
'''    fun selectSection(section: InventoryWorkspaceSection) {\n        mutableState.update { it.copy(section = section, message = null) }\n        // Operational lists are snapshots, not live Flows. Refresh when entering a section\n        // so data created by another workflow/process is visible without requiring a manual tap.\n        refresh()\n    }''',
'inventory_section_refresh')

replace_once(PAY,
'''        items(hrState.batches, key = { it.id }) { batch ->''',
'''        items(hrState.batches, key = { "payroll-batch-${it.id}" }) { batch ->''',
'payroll_batch_unique_key')
replace_once(PAY,
'''            items(hrState.employeePayslips, key = { it.id }) { payslip ->''',
'''            items(hrState.employeePayslips, key = { "payroll-payslip-${it.id}" }) { payslip ->''',
'payroll_payslip_unique_key')

text=read(E2E)
replacements={
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_select_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()''',
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_approve_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_approve_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''',
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_post_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_post_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''',
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_approve_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_approve_${fixture.transferId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_approve_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_approve_${fixture.transferId}").performClick()''',
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_issue_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_issue_${fixture.transferId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_issue_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_issue_${fixture.transferId}").performClick()''',
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_receive_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_receive_${fixture.transferId}").performClick()''':
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_receive_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_receive_${fixture.transferId}").performClick()''',
}
for old,new in replacements.items():
    if new in text: continue
    if old not in text: raise SystemExit('FIX17_E2E_TARGET_MISSING:'+old.splitlines()[0])
    text=text.replace(old,new,1)
write(E2E,text)

checks=[
(VM,'refresh()\n    }'),
(PAY,'payroll-batch-${it.id}'),
(PAY,'payroll-payslip-${it.id}'),
(E2E,'onAllNodesWithTag("inventory_count_select_${fixture.sessionId}")'),
(E2E,'onAllNodesWithTag("inventory_transfer_approve_${fixture.transferId}")'),
]
for path,needle in checks:
    if needle not in read(path): raise SystemExit(f'FIX17_VERIFY_FAIL:{path}:{needle}')
print('DASHBOARD_UX2_FIX17_FINAL_UI_STATE=PASS inventory_section_refresh=1 payroll_keys=2 count_transfer_sync=1')
