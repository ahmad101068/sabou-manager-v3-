#!/usr/bin/env python3
from pathlib import Path
import re


def read(path): return Path(path).read_text(encoding='utf-8')
def write(path, text): Path(path).write_text(text, encoding='utf-8')

def replace_once(path, old, new, label):
    text=read(path)
    count=text.count(old)
    if count != 1:
        raise SystemExit(f'FIX12_REPLACE_FAIL:{label}:count={count}')
    write(path,text.replace(old,new,1))

def ensure_import(path, anchor, line, label):
    text=read(path)
    if line in text: return
    if anchor not in text: raise SystemExit(f'FIX12_IMPORT_FAIL:{label}')
    write(path,text.replace(anchor,anchor+line,1))

def regex_replace_once(path, pattern, repl, label):
    text=read(path)
    text2,n=re.subn(pattern,repl,text,count=1,flags=re.S)
    if n != 1: raise SystemExit(f'FIX12_REGEX_FAIL:{label}:count={n}')
    write(path,text2)

E2E='app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt'
START='app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt'
PERM='app/src/main/java/ir/sabou/inventory/domain/security/Permission.kt'
INV='app/src/main/java/ir/sabou/inventory/ui/InventoryWorkspaceScreen.kt'
REST='app/src/main/java/ir/sabou/inventory/ui/RestaurantScreens.kt'
RECIPE='app/src/main/java/ir/sabou/inventory/ui/RecipeScreens.kt'
MIG='app/src/androidTest/java/ir/sabou/inventory/data/db/ReplenishmentMigration25To26Test.kt'

ensure_import(E2E,'import androidx.compose.ui.test.assertIsDisplayed\n','import androidx.compose.ui.test.hasTestTag\n','e2e_hasTestTag')
ensure_import(E2E,'import androidx.compose.ui.test.performScrollTo\n','import androidx.compose.ui.test.performScrollToNode\n','e2e_scrollToNode')
ensure_import(START,'import androidx.compose.ui.test.assertIsDisplayed\n','import androidx.compose.ui.test.hasTestTag\n','startup_hasTestTag')
ensure_import(START,'import androidx.compose.ui.test.performScrollTo\n','import androidx.compose.ui.test.performScrollToNode\n','startup_scrollToNode')

text=read(PERM)
if 'Permission.PAYROLL_APPROVE,' not in text[text.index('MANAGER('):text.index('CASHIER(')]:
    replace_once(PERM,'            Permission.PAYROLL_REVIEW,\n            Permission.JOURNAL_REVERSE,','            Permission.PAYROLL_REVIEW,\n            Permission.PAYROLL_APPROVE,\n            Permission.JOURNAL_REVERSE,','manager_payroll_approve')

for path, old, new, label in [
    (INV,'modifier = Modifier.fillMaxSize(),','modifier = Modifier.fillMaxSize().testTag("inventory_overview_list"),','inventory_list_tag'),
    (REST,'modifier = Modifier.fillMaxSize().padding(16.dp),','modifier = Modifier.fillMaxSize().padding(16.dp).testTag("restaurant_workspace_list"),','restaurant_list_tag'),
    (RECIPE,'modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp),','modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp).testTag("recipe_editor_list"),','recipe_editor_tag'),
]:
    if new not in read(path): replace_once(path,old,new,label)

text=read(E2E)
for st in ('SALES_INVOICE','SALES_RETURN','GOODS_RECEIPT','ASSET_DEPRECIATION'):
    old=f'app.container.accountingUseCases.journals("{st}").first()'
    new=f'app.container.accountingUseCases.journals("").first().filter {{ it.sourceType == "{st}" }}'
    text=text.replace(old,new)
write(E2E,text)

regex_replace_once(E2E,
    r'''    private fun openModule\(screen: AppScreen\) \{.*?\n    \}\n\n    private fun waitForTreasuryTransaction''',
    '''    private fun openModule(screen: AppScreen) {\n        if (composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isEmpty()) {\n            composeRule.onNodeWithTag("nav_more").performClick()\n            composeRule.waitUntil(timeoutMillis = 10_000) {\n                composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty()\n            }\n            composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_${screen.name}"))\n            composeRule.waitUntil(timeoutMillis = 10_000) {\n                composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isNotEmpty()\n            }\n        }\n        composeRule.onNodeWithTag("module_${screen.name}").performClick()\n    }\n\n    private fun waitForTreasuryTransaction''',
    'open_module_lazy')

text=read(E2E)
repls={
'composeRule.onNodeWithTag("inventory_section_COUNTS").performScrollTo().performClick()':'composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_COUNTS"))\n        composeRule.onNodeWithTag("inventory_section_COUNTS").performClick()',
'composeRule.onNodeWithTag("inventory_section_TRANSFERS").performScrollTo().performClick()':'composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_TRANSFERS"))\n        composeRule.onNodeWithTag("inventory_section_TRANSFERS").performClick()',
'composeRule.onNodeWithTag("recipe_create_draft_${originalActive.id}").performClick()':'composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_create_draft_${originalActive.id}"))\n        composeRule.onNodeWithTag("recipe_create_draft_${originalActive.id}").performClick()',
'composeRule.onNodeWithTag("recipe_activate_${draftId}").performClick()':'composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_activate_${draftId}"))\n        composeRule.onNodeWithTag("recipe_activate_${draftId}").performClick()',
'composeRule.onNodeWithTag("restaurant_add_menu_${fixture.menuItemId}").performScrollTo().performClick()':'composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_add_menu_${fixture.menuItemId}"))\n        composeRule.onNodeWithTag("restaurant_add_menu_${fixture.menuItemId}").performClick()',
}
for old,new in repls.items():
    if new not in text:
        if old not in text: raise SystemExit('FIX12_E2E_TARGET_MISSING:'+old[:50])
        text=text.replace(old,new,1)
write(E2E,text)

text=read(START)
old='''        composeRule.onNodeWithTag("nav_more").performClick()\n        composeRule.onNodeWithTag("module_SECURITY").performScrollTo().performClick()'''
new='''        composeRule.onNodeWithTag("nav_more").performClick()\n        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_SECURITY"))\n        composeRule.onNodeWithTag("module_SECURITY").performClick()'''
if new not in text:
    if old not in text: raise SystemExit('FIX12_STARTUP_SECURITY_TARGET_MISSING')
    text=text.replace(old,new,1)
old='''        composeRule.onNodeWithTag("nav_more").performClick()\n        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("module_PERSONNEL").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("module_PERSONNEL").performScrollTo().performClick()'''
new='''        composeRule.onNodeWithTag("nav_more").performClick()\n        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_PERSONNEL"))\n        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("module_PERSONNEL").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("module_PERSONNEL").performClick()'''
if new not in text:
    if old not in text: raise SystemExit('FIX12_STARTUP_PERSONNEL_TARGET_MISSING')
    text=text.replace(old,new,1)
write(START,text)

new_test='''    @Test\n    fun addsReplenishmentPoliciesAndKeepsExistingData() {\n        val helper = open(25)\n        try {\n            val db = helper.writableDatabase\n            createVersion25Fixture(db)\n            migrateTo26(db)\n            assertVersionAndLegacyData(db)\n            assertReplenishmentColumns(db)\n            assertNoForeignKeyViolations(db)\n        } finally {\n            helper.close()\n        }\n    }\n\n    private fun createVersion25Fixture(db: SupportSQLiteDatabase) {\n        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")\n        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")\n        db.execSQL("CREATE TABLE legacy_marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")\n        db.execSQL("INSERT INTO legacy_marker(id, value) VALUES (1, 'محفوظ')")\n    }\n\n    private fun migrateTo26(db: SupportSQLiteDatabase) {\n        db.beginTransaction()\n        try {\n            MIGRATION_25_26.migrate(db)\n            db.version = 26\n            db.setTransactionSuccessful()\n        } finally {\n            db.endTransaction()\n        }\n    }\n\n    private fun assertVersionAndLegacyData(db: SupportSQLiteDatabase) {\n        assertEquals(26, db.version)\n        val cursor = db.query("SELECT value FROM legacy_marker WHERE id = 1")\n        try {\n            assertTrue(cursor.moveToFirst())\n            assertEquals("محفوظ", cursor.getString(0))\n        } finally { cursor.close() }\n    }\n\n    private fun assertReplenishmentColumns(db: SupportSQLiteDatabase) {\n        val expected = mutableSetOf("itemId", "preferredSupplierId", "targetCoverDays", "leadTimeDays", "safetyStockMicros", "orderMultipleMicros", "isEnabled")\n        val cursor = db.query("PRAGMA table_info(inventory_replenishment_policies)")\n        try {\n            val nameIndex = cursor.getColumnIndexOrThrow("name")\n            while (cursor.moveToNext()) expected.remove(cursor.getString(nameIndex))\n        } finally { cursor.close() }\n        assertTrue("missing replenishment columns: $expected", expected.isEmpty())\n    }\n\n    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {\n        val cursor = db.query("PRAGMA foreign_key_check")\n        try { assertEquals(0, cursor.count) } finally { cursor.close() }\n    }\n'''
regex_replace_once(MIG,
    r'''    @Test\n    fun addsReplenishmentPoliciesAndKeepsExistingData\(\) \{.*?\n    \}\n\n    private fun open\(version: Int\): SupportSQLiteOpenHelper''',
    new_test+'\n    private fun open(version: Int): SupportSQLiteOpenHelper',
    'api23_replenishment_refactor')

checks=[
 (PERM,'Permission.PAYROLL_APPROVE,'),
 (E2E,'performScrollToNode(hasTestTag("module_${screen.name}"))'),
 (E2E,'filter { it.sourceType == "SALES_INVOICE" }'),
 (START,'performScrollToNode(hasTestTag("module_SECURITY"))'),
 (MIG,'private fun assertReplenishmentColumns'),
]
for path,needle in checks:
    if needle not in read(path): raise SystemExit(f'FIX12_VERIFY_FAIL:{path}:{needle}')
print('DASHBOARD_UX2_FIX12_FINAL_DEVICE_ROOTS=PASS api35_lazy_journal_permission=1 api23_art_refactor=1')
