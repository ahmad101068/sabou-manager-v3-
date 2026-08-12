#!/usr/bin/env python3
from pathlib import Path

root = Path.cwd()
app_container = root / "app/src/main/java/ir/sabou/inventory/data/AppContainer.kt"
dashboard_test = root / "app/src/androidTest/java/ir/sabou/inventory/ui/DashboardNavigationSettingsUx2ComposeTest.kt"
startup_test = root / "app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt"
enterprise_test = root / "app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt"

# Keep the real Room instance private; expose only module-internal test access.
text = app_container.read_text(encoding="utf-8")
anchor = "    private val authorizer by lazy { SessionAuthorizer(database) }"
accessor = "    internal val databaseForTesting: AppDatabase\n        get() = database\n\n"
if accessor not in text:
    if anchor not in text:
        raise SystemExit("AppContainer authorizer anchor not found")
    text = text.replace(anchor, accessor + anchor, 1)
app_container.write_text(text, encoding="utf-8")

# Compose version in this project does not expose assertDoesNotExist as imported API.
# Preserve the assertions by checking the semantics collection is empty.
text = dashboard_test.read_text(encoding="utf-8")
if text.count("assertDoesNotExist") != 6:
    raise SystemExit(f"Expected 6 assertDoesNotExist tokens in dashboard test, found {text.count('assertDoesNotExist')}")
text = text.replace("import androidx.compose.ui.test.assertDoesNotExist\n", "")
if "import androidx.compose.ui.test.onAllNodesWithText\n" not in text:
    text = text.replace("import androidx.compose.ui.test.onAllNodesWithTag\n", "import androidx.compose.ui.test.onAllNodesWithTag\nimport androidx.compose.ui.test.onAllNodesWithText\n", 1)
if "import org.junit.Assert.assertTrue\n" not in text:
    text = text.replace("import org.junit.After\n", "import org.junit.Assert.assertTrue\nimport org.junit.After\n", 1)
replacements = {
    'composeRule.onNodeWithText("فعالیت‌های اخیر").assertDoesNotExist()': 'assertTrue(composeRule.onAllNodesWithText("فعالیت‌های اخیر").fetchSemanticsNodes().isEmpty())',
    'composeRule.onNodeWithTag("module_AUDIT_LOG").assertDoesNotExist()': 'assertTrue(composeRule.onAllNodesWithTag("module_AUDIT_LOG").fetchSemanticsNodes().isEmpty())',
    'composeRule.onNodeWithTag("home_action_personnel").assertDoesNotExist()': 'assertTrue(composeRule.onAllNodesWithTag("home_action_personnel").fetchSemanticsNodes().isEmpty())',
    'composeRule.onNodeWithTag("home_kpi_liquidity").assertDoesNotExist()': 'assertTrue(composeRule.onAllNodesWithTag("home_kpi_liquidity").fetchSemanticsNodes().isEmpty())',
}
for old, new in replacements.items():
    text = text.replace(old, new)
if "assertDoesNotExist" in text:
    raise SystemExit("Dashboard test still contains assertDoesNotExist")
dashboard_test.write_text(text, encoding="utf-8")

text = startup_test.read_text(encoding="utf-8")
if text.count("assertDoesNotExist") != 6:
    raise SystemExit(f"Expected 6 assertDoesNotExist tokens in startup test, found {text.count('assertDoesNotExist')}")
text = text.replace("import androidx.compose.ui.test.assertDoesNotExist\n", "")
if "import org.junit.Assert.assertTrue\n" not in text:
    text = text.replace("import org.junit.After\n", "import org.junit.Assert.assertTrue\nimport org.junit.After\n", 1)
for tag in ["home_dashboard", "home_action_personnel", "module_TREASURY"]:
    text = text.replace(
        f'composeRule.onNodeWithTag("{tag}").assertDoesNotExist()',
        f'assertTrue(composeRule.onAllNodesWithTag("{tag}").fetchSemanticsNodes().isEmpty())',
    )
if "assertDoesNotExist" in text:
    raise SystemExit("Startup test still contains assertDoesNotExist")
startup_test.write_text(text, encoding="utf-8")

# Add the missing Compose text-node import and route raw SQL checks through the internal test accessor.
text = enterprise_test.read_text(encoding="utf-8")
if "import androidx.compose.ui.test.onNodeWithText\n" not in text:
    text = text.replace("import androidx.compose.ui.test.onNodeWithTag\n", "import androidx.compose.ui.test.onNodeWithTag\nimport androidx.compose.ui.test.onNodeWithText\n", 1)
enterprise_test.write_text(text, encoding="utf-8")

changed_db_refs = 0
for path in (enterprise_test, startup_test):
    text = path.read_text(encoding="utf-8")
    count = text.count("app.container.database.openHelper.writableDatabase")
    changed_db_refs += count
    text = text.replace("app.container.database.openHelper.writableDatabase", "app.container.databaseForTesting.openHelper.writableDatabase")
    path.write_text(text, encoding="utf-8")
if changed_db_refs != 5:
    raise SystemExit(f"Expected exactly 5 raw database test references, found {changed_db_refs}")

print("DASHBOARD_UX2_ANDROIDTEST_COMPILE_FIX=PASS db_refs=5")
