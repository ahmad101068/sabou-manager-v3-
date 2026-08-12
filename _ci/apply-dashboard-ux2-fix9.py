from pathlib import Path

root = Path('.')

def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly 1 occurrence, found {count} in {path}')
    path.write_text(text.replace(old, new, 1))

# Enterprise E2E: use the new top-level More hub and create a real schedule/shift for payroll fixtures.
p = root / 'app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt'
replace_once(
    p,
    'import ir.sabou.inventory.domain.personnel.ReviewPayrollBatchCommand\n',
    'import ir.sabou.inventory.domain.personnel.ReviewPayrollBatchCommand\n'
    'import ir.sabou.inventory.domain.personnel.ShiftCategory\n'
    'import ir.sabou.inventory.domain.personnel.ShiftTemplateDraft\n'
    'import ir.sabou.inventory.domain.personnel.WorkScheduleDayRule\n'
    'import ir.sabou.inventory.domain.personnel.WorkScheduleDraft\n'
    'import ir.sabou.inventory.domain.personnel.WorkSchedulePatternType\n',
    'payroll scheduling imports',
)
replace_once(
    p,
    '''        if (composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isEmpty()) {\n            composeRule.onNodeWithTag("home_action_more").performClick()\n            composeRule.waitUntil(timeoutMillis = 10_000) {\n                composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isNotEmpty()\n            }\n        }\n''',
    '''        if (composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isEmpty()) {\n            composeRule.onNodeWithTag("nav_more").performClick()\n            composeRule.waitUntil(timeoutMillis = 10_000) {\n                composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isNotEmpty()\n            }\n        }\n''',
    'E2E More navigation',
)
replace_once(
    p,
    '''        val contractId = app.container.personnelUseCases.saveContract(\n            id = null,\n            draft = EmployeeContractDraft(\n''',
    '''        val shiftTemplateId = app.container.personnelUseCases.saveShiftTemplate(\n            id = null,\n            draft = ShiftTemplateDraft(\n                code = "E2ESHIFT$nonce",\n                name = "شیفت E2E $nonce",\n                category = ShiftCategory.MORNING,\n                startMinute = 8 * 60,\n                endMinute = 16 * 60,\n                overtimeRequiresApproval = false,\n                notes = "شیفت واقعی پیش‌نیاز Payroll E2E",\n            ),\n        )\n        val workScheduleId = app.container.personnelUseCases.saveWorkSchedule(\n            id = null,\n            draft = WorkScheduleDraft(\n                code = "E2ESCHED$nonce",\n                name = "برنامه کاری E2E $nonce",\n                patternType = WorkSchedulePatternType.WEEKLY_FIXED,\n                cycleLengthDays = 7,\n                effectiveFromEpochDay = day - 30,\n                effectiveToEpochDay = day + 30,\n                branchName = "شعبه E2E",\n                notes = "برنامه واقعی پیش‌نیاز Payroll E2E",\n                days = (0..6).map { sequenceDay ->\n                    WorkScheduleDayRule(\n                        sequenceDay = sequenceDay,\n                        dayOfWeek = sequenceDay + 1,\n                        shiftTemplateId = shiftTemplateId,\n                        isOffDay = false,\n                    )\n                },\n            ),\n        )\n        val contractId = app.container.personnelUseCases.saveContract(\n            id = null,\n            draft = EmployeeContractDraft(\n''',
    'real payroll schedule fixture',
)
replace_once(
    p,
    '''                payrollPolicyId = policyId,\n                notes = "قرارداد پیش‌نیاز E2E",\n''',
    '''                payrollPolicyId = policyId,\n                workScheduleId = workScheduleId,\n                defaultShiftTemplateId = shiftTemplateId,\n                notes = "قرارداد پیش‌نیاز E2E",\n''',
    'contract schedule linkage',
)

# Startup/auth tests: use stable top-level navigation instead of removed Home quick-action assumptions.
p = root / 'app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt'
replace_once(p, 'composeRule.onNodeWithTag("home_action_more").performClick()', 'composeRule.onNodeWithTag("nav_more").performClick()', 'startup More navigation')
replace_once(
    p,
    '''        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_action_personnel").fetchSemanticsNodes().isNotEmpty() }\n\n        composeRule.onNodeWithTag("home_action_personnel").performClick()\n        composeRule.onNodeWithText("منابع انسانی و حقوق").assertIsDisplayed()\n''',
    '''        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("nav_more").performClick()\n        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("module_PERSONNEL").fetchSemanticsNodes().isNotEmpty() }\n        composeRule.onNodeWithTag("module_PERSONNEL").performScrollTo().performClick()\n        composeRule.onNodeWithText("منابع انسانی و حقوق").assertIsDisplayed()\n''',
    'startup owner personnel navigation',
)

# Production recipe historical selection: a superseded retired revision remains effective for dates before its successor.
p = root / 'app/src/main/java/ir/sabou/inventory/data/db/RecipeDao.kt'
replace_once(
    p,
    '''        """SELECT * FROM recipe_versions\n        WHERE menuItemId = :menuItemId AND status = 'ACTIVE' AND effectiveFromEpochDay <= :epochDay\n        ORDER BY effectiveFromEpochDay DESC, revisionNo DESC\n        LIMIT 1""",\n''',
    '''        """SELECT rv.* FROM recipe_versions rv\n        WHERE rv.menuItemId = :menuItemId\n          AND rv.effectiveFromEpochDay <= :epochDay\n          AND (\n            rv.status = 'ACTIVE'\n            OR (\n              rv.status = 'RETIRED'\n              AND EXISTS(\n                SELECT 1 FROM recipe_versions successor\n                WHERE successor.menuItemId = rv.menuItemId\n                  AND successor.status = 'ACTIVE'\n                  AND successor.effectiveFromEpochDay > :epochDay\n                  AND successor.effectiveFromEpochDay > rv.effectiveFromEpochDay\n              )\n            )\n          )\n        ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC\n        LIMIT 1""",\n''',
    'historical recipe effective lookup',
)

# Factory-reset assertion follows current Inventory 2 seed contract exactly.
p = root / 'app/src/androidTest/java/ir/sabou/inventory/data/repository/RecipeVersionIntegrationTest.kt'
replace_once(
    p,
    'assertEquals(1L, scalar("SELECT COUNT(*) FROM storage_locations WHERE name=\'انبار اصلی\' AND kind=\'PRIMARY\' AND isActive=1"))',
    'assertEquals(1L, scalar("SELECT COUNT(*) FROM storage_locations WHERE code=\'MAIN\' AND name=\'انبار اصلی\' AND kind=\'WAREHOUSE\' AND isActive=1"))',
    'Inventory2 factory reset location assertion',
)

# Guardrails proving no obsolete navigation assumption remains in these regression suites.
for rel in [
    'app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt',
    'app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt',
]:
    text = (root / rel).read_text()
    if 'home_action_more' in text:
        raise SystemExit(f'fix9 obsolete home_action_more remains in {rel}')

print('DASHBOARD_UX2_FIX9=PASS nav_more=2 payroll_schedule=real recipe_history=as_of factory_reset=inventory2')
