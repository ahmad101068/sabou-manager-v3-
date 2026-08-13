#!/usr/bin/env python3
from pathlib import Path

E2E = Path("app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt")
text = E2E.read_text(encoding="utf-8")

replacements = {
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_select_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_approve_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_approve_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()''',
'''        composeRule.waitUntil(timeoutMillis = 10_000) {\n            composeRule.onAllNodesWithTag("inventory_count_post_${fixture.sessionId}").fetchSemanticsNodes().isNotEmpty()\n        }\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''':
'''        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_post_${fixture.sessionId}"))\n        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()''',
}

changed = 0
for old, new in replacements.items():
    if new in text:
        continue
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FIX19_COUNT_SCROLL_TARGET_MISSING:count={count}")
    text = text.replace(old, new, 1)
    changed += 1

E2E.write_text(text, encoding="utf-8")
check = E2E.read_text(encoding="utf-8")
for needle in (
    'performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))',
    'performScrollToNode(hasTestTag("inventory_count_approve_${fixture.sessionId}"))',
    'performScrollToNode(hasTestTag("inventory_count_post_${fixture.sessionId}"))',
):
    if needle not in check:
        raise SystemExit(f"FIX19_VERIFY_FAIL:{needle}")

print(f"DASHBOARD_UX2_FIX19_FINAL_TWO_GATES=PASS inventory_count_lazy_scroll=3 changed={changed}")
