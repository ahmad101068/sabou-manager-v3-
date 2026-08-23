from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'phase8-source')
crm = root / 'app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt'
e2e = root / 'app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt'

text = crm.read_text()
old = '        state.message?.let { message -> item { MessageCard(message, state.isError) } }\n'
new = '''        state.message?.let { message ->\n            item {\n                Column(Modifier.testTag(if (state.isError) "crm_command_error" else "crm_command_message")) {\n                    MessageCard(message, state.isError)\n                }\n            }\n        }\n'''
if old in text:
    text = text.replace(old, new, 1)
elif 'crm_command_error' not in text:
    raise SystemExit('CRM command message anchor missing')
crm.write_text(text)

text = e2e.read_text()
old = '''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            runBlocking {\n                app.container.crmUseCases.ledger(customerId).first().any {\n                    it.entryType == "COLLECTION" && it.creditRial == receiptAmount\n                }\n            }\n        }\n'''
new = '''        composeRule.waitUntil(timeoutMillis = 15_000) {\n            runBlocking {\n                app.container.crmUseCases.ledger(customerId).first().any {\n                    it.entryType == "COLLECTION" && it.creditRial == receiptAmount\n                }\n            } || composeRule.onAllNodesWithTag("crm_command_error").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onAllNodesWithTag("crm_command_error").fetchSemanticsNodes().firstOrNull()?.let { node ->\n            error("PHASE8_CRM_COLLECTION_UI_ERROR:${node.config}")\n        }\n'''
if old in text:
    text = text.replace(old, new, 1)
elif 'PHASE8_CRM_COLLECTION_UI_ERROR' not in text:
    raise SystemExit('CRM post-collection wait anchor missing')
e2e.write_text(text)

checks = [
    (crm, 'crm_command_error'),
    (crm, 'crm_command_message'),
    (e2e, 'PHASE8_CRM_COLLECTION_UI_ERROR'),
    (e2e, 'timeoutMillis = 15_000'),
]
for path, needle in checks:
    if needle not in path.read_text():
        raise SystemExit(f'missing diagnostic invariant {needle}')
print('PHASE8_HOTFIX_05_CRM_ERROR_OBSERVABILITY=PASS')
