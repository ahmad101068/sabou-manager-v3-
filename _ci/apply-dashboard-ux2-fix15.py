#!/usr/bin/env python3
from pathlib import Path


def read(path): return Path(path).read_text(encoding='utf-8')
def write(path, text): Path(path).write_text(text, encoding='utf-8')
def replace_once(path, old, new, label):
    text=read(path)
    if new in text: return
    count=text.count(old)
    if count != 1: raise SystemExit(f'FIX15_REPLACE_FAIL:{label}:count={count}')
    write(path,text.replace(old,new,1))

E2E='app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt'
COUNT='app/src/main/java/ir/sabou/inventory/ui/InventoryCountCenterScreen.kt'
TRANSFER='app/src/main/java/ir/sabou/inventory/ui/InventoryTransferCenterScreen.kt'
TREAS='app/src/main/java/ir/sabou/inventory/ui/TreasuryScreen.kt'
PAY='app/src/main/java/ir/sabou/inventory/ui/HrPayrollScreens.kt'

replace_once(COUNT,
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize(),''',
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize().testTag("inventory_count_list"),''',
'inventory_count_list_tag')
replace_once(TRANSFER,
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize(),''',
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize().testTag("inventory_transfer_list"),''',
'transfer_list_tag')
replace_once(TREAS,
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize().padding(16.dp),''',
'''    LazyColumn(\n        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("treasury_list"),''',
'treasury_list_tag')
replace_once(PAY,
'''    LazyColumn(\n        Modifier.fillMaxSize(),\n        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),\n        verticalArrangement = Arrangement.spacedBy(12.dp),\n    ) {''',
'''    LazyColumn(\n        Modifier.fillMaxSize().testTag("payroll_center_list"),\n        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),\n        verticalArrangement = Arrangement.spacedBy(12.dp),\n    ) {''',
'payroll_center_list_tag')
replace_once(PAY,
'''                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    item {\n                        FilterChip(\n                            selected = personnelState.selectedEmployeeId == null,''',
'''                LazyRow(\n                    modifier = Modifier.testTag("payroll_employee_row"),\n                    horizontalArrangement = Arrangement.spacedBy(6.dp),\n                ) {\n                    item {\n                        FilterChip(\n                            selected = personnelState.selectedEmployeeId == null,''',
'payroll_employee_row_tag')

text=read(E2E)
replacements={
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_select_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performScrollTo().performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_approve_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_approve_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_post_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_post_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_approve_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_approve_${fixture.transferId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_approve_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_approve_${fixture.transferId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_issue_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_issue_${fixture.transferId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_issue_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_issue_${fixture.transferId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_transfer_receive_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_transfer_receive_${fixture.transferId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_transfer_list").performScrollToNode(hasTestTag("inventory_transfer_receive_${fixture.transferId}"))\n        composeRule.onNodeWithTag("inventory_transfer_receive_${fixture.transferId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("payroll_employee_${fixture.employeeId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("payroll_employee_${fixture.employeeId}").performScrollTo().performClick()''':
'''        composeRule.onNodeWithTag("payroll_center_list").performScrollToNode(hasTestTag("payroll_employee_row"))\n        composeRule.onNodeWithTag("payroll_employee_row").performScrollToNode(hasTestTag("payroll_employee_${fixture.employeeId}"))\n        composeRule.onNodeWithTag("payroll_employee_${fixture.employeeId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("payroll_payslip_${payslipId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("payroll_payslip_${payslipId}").performScrollTo().performClick()''':
'''        composeRule.onNodeWithTag("payroll_center_list").performScrollToNode(hasTestTag("payroll_payslip_${payslipId}"))\n        composeRule.onNodeWithTag("payroll_payslip_${payslipId}").performClick()''',
'''        val originalId = waitForTreasuryTransaction(sourceType, sourceId, amount)\n        composeRule.onNodeWithTag("treasury_reverse_$originalId").performScrollTo().performClick()''':
'''        val originalId = waitForTreasuryTransaction(sourceType, sourceId, amount)\n        composeRule.onNodeWithTag("treasury_list").performScrollToNode(hasTestTag("treasury_reverse_$originalId"))\n        composeRule.onNodeWithTag("treasury_reverse_$originalId").performClick()''',
'''        listOf(\n            KitchenTicketStatus.ACCEPTED,\n            KitchenTicketStatus.PREPARING,\n            KitchenTicketStatus.READY,\n            KitchenTicketStatus.SERVED,\n        ).forEach { status ->\n            composeRule.waitUntil(timeoutMillis = 10_000) {\n                composeRule.onAllNodesWithTag("restaurant_kds_${ticket.id}_${status.name}").fetchSemanticsNodes().isNotEmpty()\n            }\n            composeRule.onNodeWithTag("restaurant_kds_${ticket.id}_${status.name}").performScrollTo().performClick()\n        }''':
'''        listOf(\n            KitchenTicketStatus.ACCEPTED,\n            KitchenTicketStatus.PREPARING,\n            KitchenTicketStatus.READY,\n            KitchenTicketStatus.SERVED,\n        ).forEach { status ->\n            composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_kds_${ticket.id}_${status.name}"))\n            composeRule.onNodeWithTag("restaurant_kds_${ticket.id}_${status.name}").performClick()\n        }''',
'''        composeRule.onNodeWithTag("restaurant_payment_method_CASH").performScrollTo().performClick()''':
'''        composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_payment_method_CASH"))\n        composeRule.onNodeWithTag("restaurant_payment_method_CASH").performClick()''',
'''        composeRule.onNodeWithTag("restaurant_payment_method_CARD").performClick()''':
'''        composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_payment_method_CARD"))\n        composeRule.onNodeWithTag("restaurant_payment_method_CARD").performClick()''',
'''        composeRule.onNodeWithTag("restaurant_close_table").performClick()''':
'''        composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_close_table"))\n        composeRule.onNodeWithTag("restaurant_close_table").performClick()''',
}
for old,new in replacements.items():
    if new in text: continue
    if old not in text: raise SystemExit('FIX15_E2E_TARGET_MISSING:'+old.splitlines()[0])
    text=text.replace(old,new,1)
write(E2E,text)

checks=[
(COUNT,'inventory_count_list'),(TRANSFER,'inventory_transfer_list'),(TREAS,'treasury_list'),(PAY,'payroll_center_list'),(PAY,'payroll_employee_row'),
(E2E,'performScrollToNode(hasTestTag("treasury_reverse_$originalId"))'),
(E2E,'performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))'),
(E2E,'performScrollToNode(hasTestTag("restaurant_kds_${ticket.id}_${status.name}"))'),
]
for path,needle in checks:
    if needle not in read(path): raise SystemExit(f'FIX15_VERIFY_FAIL:{path}:{needle}')
print('DASHBOARD_UX2_FIX15_LAZY_DEVICE_SYNC=PASS inventory_count=1 transfer=1 treasury=1 payroll=1 restaurant=1')
