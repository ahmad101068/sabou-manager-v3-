#!/usr/bin/env python3
from pathlib import Path
import re

root = Path.cwd()

def read(rel):
    return (root / rel).read_text(encoding='utf-8')

def write(rel, text):
    (root / rel).write_text(text, encoding='utf-8')

def replace_exact(rel, old, new, expected=1):
    text = read(rel)
    count = text.count(old)
    if count != expected:
        raise SystemExit(f'{rel}: expected {expected} occurrences, found {count}: {old[:80]!r}')
    write(rel, text.replace(old, new))

# 1) Remove collision-prone journal number truncation. Keep full deterministic command/global id.
numbering_files = {
    'app/src/main/java/ir/sabou/inventory/data/repository/LocalCustomerAccountService.kt': [
        ('"افت-${valid.commandId.take(8)}"', '"افت-${valid.commandId}"'),
        ('"تعد-${valid.commandId.take(8)}"', '"تعد-${valid.commandId}"'),
    ],
    'app/src/main/java/ir/sabou/inventory/data/treasury/LocalTreasuryServiceV2.kt': [
        ('"خز-ب-${valid.commandId.value.take(8)}"', '"خز-ب-${valid.commandId.value}"'),
        ('"خز-${command.commandId.value.take(8)}"', '"خز-${command.commandId.value}"'),
    ],
    'app/src/main/java/ir/sabou/inventory/data/repository/LocalHrPayrollService.kt': [
        ('"REV-PAY-${payslip.globalId.take(8)}"', '"REV-PAY-${payslip.globalId}"'),
    ],
}
for rel, reps in numbering_files.items():
    text = read(rel)
    for old, new in reps:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f'{rel}: expected one numbering pattern {old}, found {count}')
        text = text.replace(old, new, 1)
    write(rel, text)

# 2) Recipe lifecycle guards: drafts are editable; active/retired historical content is immutable.
# ACTIVE -> RETIRED is the only allowed update of a non-draft header and may only change status.
rel = 'app/src/main/java/ir/sabou/inventory/data/db/migration/DatabaseIntegrityLifecycle.kt'
text = read(rel)
pattern = re.compile(r"internal fun installRecipeVersionGuards\(db: SupportSQLiteDatabase\) \{.*?\n\}\n\nprivate val factoryResetGuardNames", re.S)
replacement = '''internal fun installRecipeVersionGuards(db: SupportSQLiteDatabase) {
    listOf(
        "trg_recipe_versions_no_update",
        "trg_recipe_versions_no_delete",
        "trg_recipe_version_ingredients_no_update",
        "trg_recipe_version_ingredients_no_delete",
    ).forEach { name -> db.execSQL("DROP TRIGGER IF EXISTS $name") }

    db.execSQL(
        """CREATE TRIGGER trg_recipe_versions_no_update
        BEFORE UPDATE ON recipe_versions
        WHEN OLD.status != 'DRAFT'
          AND NOT (
            OLD.status = 'ACTIVE' AND NEW.status = 'RETIRED'
            AND NEW.menuItemId IS OLD.menuItemId
            AND NEW.revisionNo IS OLD.revisionNo
            AND NEW.effectiveFromEpochDay IS OLD.effectiveFromEpochDay
            AND NEW.yieldMicros IS OLD.yieldMicros
            AND NEW.portionWeightMicros IS OLD.portionWeightMicros
            AND NEW.preparationWasteBasisPoints IS OLD.preparationWasteBasisPoints
            AND NEW.cookingWasteBasisPoints IS OLD.cookingWasteBasisPoints
            AND NEW.packagingCostRial IS OLD.packagingCostRial
            AND NEW.directLaborCostRial IS OLD.directLaborCostRial
            AND NEW.allocatedOverheadRial IS OLD.allocatedOverheadRial
            AND NEW.note IS OLD.note
            AND NEW.createdBy IS OLD.createdBy
            AND NEW.createdAtEpochMillis IS OLD.createdAtEpochMillis
            AND NEW.parentVersionId IS OLD.parentVersionId
          )
        BEGIN SELECT RAISE(ABORT, 'recipe versions are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_versions_no_delete
        BEFORE DELETE ON recipe_versions
        BEGIN SELECT RAISE(ABORT, 'recipe versions are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_version_ingredients_no_update
        BEFORE UPDATE ON recipe_version_ingredients
        WHEN EXISTS(
            SELECT 1 FROM recipe_versions rv
            WHERE rv.id = OLD.recipeVersionId AND rv.status != 'DRAFT'
        )
        BEGIN SELECT RAISE(ABORT, 'recipe ingredients are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_version_ingredients_no_delete
        BEFORE DELETE ON recipe_version_ingredients
        WHEN EXISTS(
            SELECT 1 FROM recipe_versions rv
            WHERE rv.id = OLD.recipeVersionId AND rv.status != 'DRAFT'
        )
        BEGIN SELECT RAISE(ABORT, 'recipe ingredients are immutable'); END""",
    )
}

private val factoryResetGuardNames'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f'{rel}: recipe guard function replacement count={count}')
write(rel, text)

# 3) AndroidTest SQL must use the real journal_lines FK column: entryId.
android_test = root / 'app/src/androidTest/java'
query_count = 0
for path in android_test.rglob('*.kt'):
    text = path.read_text(encoding='utf-8')
    count = text.count('journal_lines WHERE journalEntryId=')
    if count:
        text = text.replace('journal_lines WHERE journalEntryId=', 'journal_lines WHERE entryId=')
        path.write_text(text, encoding='utf-8')
        query_count += count
if query_count != 12:
    raise SystemExit(f'Expected 12 journal_lines legacy FK queries, found {query_count}')

# 4) JUnit4 methods annotated as @Before/@Test must compile to void/Unit.
replace_exact(
    'app/src/androidTest/java/ir/sabou/inventory/data/repository/ExtractedResponsibilityServicesIntegrationTest.kt',
    '    fun setUp() = runBlocking {',
    '    fun setUp(): Unit = runBlocking {',
)
replace_exact(
    'app/src/androidTest/java/ir/sabou/inventory/data/repository/RecipeVersionIntegrationTest.kt',
    '    fun appendsRevisionsAndSelectsFormulaByBusinessDate() = runBlocking {',
    '    fun appendsRevisionsAndSelectsFormulaByBusinessDate(): Unit = runBlocking {',
)

# 5) A destination projection row is legitimately absent before the first movement.
replace_exact(
    'app/src/androidTest/java/ir/sabou/inventory/data/repository/InventoryTransferWorkflowIntegrationTest.kt',
    '        val balance = requireNotNull(database.inventoryBalanceDao().byKey(fixture.itemId, locationId))\n        return balance.onHandMicros to balance.inTransitMicros',
    '        val balance = database.inventoryBalanceDao().byKey(fixture.itemId, locationId) ?: return 0L to 0L\n        return balance.onHandMicros to balance.inTransitMicros',
)

# 6) Lazy containers do not compose far-off children. Scroll the parent to the tagged child first.
rel = 'app/src/androidTest/java/ir/sabou/inventory/ui/DashboardNavigationSettingsUx2ComposeTest.kt'
text = read(rel)
if 'import androidx.compose.ui.test.hasTestTag' not in text:
    text = text.replace('import androidx.compose.ui.test.assertIsDisplayed\n', 'import androidx.compose.ui.test.assertIsDisplayed\nimport androidx.compose.ui.test.hasTestTag\n')
if 'import androidx.compose.ui.test.performScrollToNode' not in text:
    text = text.replace('import androidx.compose.ui.test.performScrollTo\n', 'import androidx.compose.ui.test.performScrollTo\nimport androidx.compose.ui.test.performScrollToNode\n')
old = '''        composeRule.onNodeWithTag("module_SALES").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("module_AUDIT_LOG").performScrollTo().assertIsDisplayed()'''
new = '''        composeRule.onNodeWithTag("module_SALES").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_AUDIT_LOG"))
        composeRule.onNodeWithTag("module_AUDIT_LOG").assertIsDisplayed()'''
if text.count(old) != 1:
    raise SystemExit('Dashboard UX2 more-hub virtualization pattern missing')
text = text.replace(old, new, 1)
old = '        composeRule.onNodeWithTag("settings_section_SECURITY_AUDIT").performScrollTo().performClick()'
new = '''        composeRule.onNodeWithTag("settings_sections").performScrollToNode(hasTestTag("settings_section_SECURITY_AUDIT"))
        composeRule.onNodeWithTag("settings_section_SECURITY_AUDIT").performClick()'''
if text.count(old) != 1:
    raise SystemExit('Dashboard UX2 settings virtualization pattern missing')
text = text.replace(old, new, 1)
write(rel, text)

# 7) Persistent UI tests share the app database. All deterministic owners use the same test PIN;
# resolve any known deterministic owner instead of assuming a single class created it first.
rel = 'app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt'
text = read(rel)
old = '''            val owner = security.users.first().firstOrNull { it.username == TEST_OWNER_USERNAME }
                ?: error("E2E database contains users but not the deterministic test owner")'''
new = '''            val owner = security.users.first().firstOrNull {
                it.role == UserRole.OWNER && it.username in KNOWN_OWNER_USERNAMES
            } ?: error("E2E database contains no known deterministic test owner")'''
if text.count(old) != 1:
    raise SystemExit('EnterpriseCore owner lookup pattern missing')
text = text.replace(old, new, 1)
old_const = '        const val TEST_OWNER_PIN = "246810"\n'
if text.count(old_const) != 1:
    raise SystemExit('EnterpriseCore owner PIN constant pattern missing')
text = text.replace(old_const, old_const + '        val KNOWN_OWNER_USERNAMES = setOf(TEST_OWNER_USERNAME, "ux2owner", "authowner")\n', 1)
write(rel, text)

rel = 'app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt'
text = read(rel)
old = '        val KNOWN_OWNER_USERNAMES = setOf(OWNER_USERNAME, "e2eowner")'
new = '        val KNOWN_OWNER_USERNAMES = setOf(OWNER_USERNAME, "e2eowner", "ux2owner")'
if text.count(old) != 1:
    raise SystemExit('Startup known owner set pattern missing')
text = text.replace(old, new, 1)
write(rel, text)

# Final invariants.
all_main = '\n'.join(p.read_text(encoding='utf-8') for p in (root/'app/src/main/java').rglob('*.kt'))
if re.search(r'entryNo\s*=.*\.take\(8\)', all_main):
    raise SystemExit('Collision-prone take(8) journal numbering remains in production source')
all_tests = '\n'.join(p.read_text(encoding='utf-8') for p in android_test.rglob('*.kt'))
if 'journal_lines WHERE journalEntryId=' in all_tests:
    raise SystemExit('Legacy journal_lines journalEntryId query remains in AndroidTest')

print('DASHBOARD_UX2_INSTRUMENTATION_ROOT_FIX=PASS numbering=5 journal_fk_queries=12 recipe_lifecycle_guards=4 junit_void=2 ui_isolation=4')
