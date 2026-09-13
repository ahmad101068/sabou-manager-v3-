#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors = []

def text(path):
    p = root / path
    if not p.exists():
        errors.append(f"missing:{path}")
        return ""
    return p.read_text(encoding="utf-8")

def require(condition, message):
    if not condition:
        errors.append(message)

home = text("app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt")
models = text("app/src/main/java/ir/restaurant/management/ui/DashboardUxModels.kt")
routes = text("app/src/main/java/ir/restaurant/management/ui/AppRoutes.kt")
hubs = text("app/src/main/java/ir/restaurant/management/ui/NavigationHubScreens.kt")
settings = text("app/src/main/java/ir/restaurant/management/ui/NavigationSettingsScreens.kt")
audit = text("app/src/main/java/ir/restaurant/management/ui/AuditPresentation.kt")
operations = text("app/src/main/java/ir/restaurant/management/ui/OperationsScreens.kt")
vm = text("app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt")
workflow_dir = root / ".github/workflows"
workflow_files = sorted(workflow_dir.glob("*.yml")) + sorted(workflow_dir.glob("*.yaml")) if workflow_dir.exists() else []
require(bool(workflow_files), "GitHub Actions workflow missing")
ci = "\n".join(p.read_text(encoding="utf-8") for p in workflow_files)

# Anti-clutter: exactly four primary tagged Home sections and no audit/module dump.
for tag in ("home_header", "home_kpi_summary", "home_quick_actions", "home_attention_center"):
    require(tag in home, f"home section missing:{tag}")
require(home.count('testTag("home_') >= 4, "Home UX test tags missing")
require("auditLogs" not in home, "Audit data must not be loaded/rendered by Home")
require("فعالیت‌های اخیر" not in home, "Recent audit activity is forbidden on Home")
require("همه بخش‌ها" not in home, "Full module list is forbidden on Home")
require("take(4)" in models, "KPI hard cap of four missing")
require("take(6)" in models, "Quick action hard cap of six missing")
require("DashboardUiState" in models and "partialErrors" in models, "Dashboard UI contract incomplete")
require("DashboardUxComposer.compose" in vm, "Dashboard permission-aware composition not in ViewModel")

# Navigation contract.
for token in ("CONTROL_HUB", "OPERATIONS_HUB", "FINANCE_HUB", "MORE"):
    require(token in routes, f"typed route missing:{token}")
for label in ("خانه", "کنترل", "عملیات", "مالی", "بیشتر"):
    require(f'"{label}"' in hubs or f'Text("{label}")' in home, f"bottom navigation label missing:{label}")
require(all(tag in hubs for tag in ("control_hub", "operations_hub", "finance_hub", "more_hub")), "Hub screens missing")

# Settings IA and primary Audit path.
for title in ("عمومی", "ظاهر", "عملیات", "چاپ", "اعلان‌ها", "داده و پشتیبان", "کاربران و دسترسی", "امنیت و حسابرسی", "درباره برنامه"):
    require(title in settings, f"settings section missing:{title}")
require("settings_security_audit" in settings, "Settings -> Security & Audit route missing")

# Audit localization and raw timestamp prevention.
for token in ("CREATE", "UPDATE", "DELETE", "APPROVE", "REJECT", "POST", "REVERSE", "PAY", "LOGIN", "LOGOUT"):
    require(f'"{token}"' in audit, f"audit action localization missing:{token}")
require("createdAtEpochMillis}" not in operations, "Raw audit timestamp leaked in UI")
require("AuditPresentationMapper.map" in operations, "Audit UI does not use central presentation mapper")
require("observeFiltered" in text("app/src/main/java/ir/restaurant/management/data/db/AuditLogDao.kt"), "Audit filters are not backed by a real DAO query")

# Tests and CI must remain enabled.
for path in (
    "app/src/test/java/ir/restaurant/management/ui/DashboardUxModelsTest.kt",
    "app/src/test/java/ir/restaurant/management/ui/AuditPresentationMapperTest.kt",
    "app/src/test/java/ir/restaurant/management/ui/SettingsInformationArchitectureTest.kt",
):
    require((root / path).exists(), f"required UX unit test missing:{path}")
require("continue-on-error" not in ci, "continue-on-error is forbidden in CI")

# Audit filters are real repository-query inputs, not UI-only decorations.
audit_query = text("app/src/main/java/ir/restaurant/management/domain/operations/OperationsModels.kt")
audit_dao = text("app/src/main/java/ir/restaurant/management/data/db/AuditLogDao.kt")
operations_vm = text("app/src/main/java/ir/restaurant/management/ui/OperationsViewModel.kt")
require(all(marker in audit_query for marker in ("entityId", "sourceReference", "severity")), "Audit query is missing entity/source/severity filters")
require(all(marker in audit_dao for marker in (":entityId", ":sourceReference", ":severity")), "Audit DAO does not apply the complete filter contract")
require("workspaceActive" in operations_vm and "setWorkspaceActive" in operations_vm, "Operations workspace is not deferred away from Home")

if errors:
    print("DASHBOARD_UX_VERIFICATION=FAIL")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("DASHBOARD_UX_VERIFICATION=PASS sections=4 kpi_max=4 quick_actions_max=6 settings_sections=9")
