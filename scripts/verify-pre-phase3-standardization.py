#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java"
failures: list[str] = []


def require(ok: bool, message: str) -> None:
    if not ok:
        failures.append(message)


def text(rel: str) -> str:
    p = ROOT / rel
    if not p.is_file():
        failures.append(f"missing required file: {rel}")
        return ""
    return p.read_text(encoding="utf-8", errors="replace")

app_db = text("app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt")
migration_registry = text("app/src/main/java/ir/restaurant/management/data/db/migration/AppMigrations.kt")
build = text("app/build.gradle.kts")
room_match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)\b", app_db)
current_room_version = int(room_match.group(1)) if room_match else -1
require(current_room_version in (54, 55), "Room version must be baseline 54 or approved Phase-3 compatibility version 55")
require("MIGRATION_53_54" in migration_registry, "MIGRATION_53_54 must remain registered")
if current_room_version == 55:
    require("MIGRATION_54_55" in migration_registry, "MIGRATION_54_55 must be registered for Phase-3 branch rename compatibility")
require("fallbackToDestructiveMigration" not in "\n".join(p.read_text(encoding='utf-8', errors='replace') for p in (ROOT/'app/src/main').rglob('*') if p.is_file()), "destructive migration fallback is forbidden")
require((ROOT / "app/schemas/ir.restaurant.management.data.db.AppDatabase/54.json").is_file(), "Room-generated 54.json must be preserved")
require((ROOT / f"app/schemas/ir.restaurant.management.data.db.AppDatabase/{current_room_version}.json").is_file(), f"Room-generated {current_room_version}.json must be present")
require((ROOT / "app/src/androidTest/java/ir/restaurant/management/data/db/FullMigration1ToCurrentTest.kt").is_file(), "FullMigration1ToCurrentTest must be preserved")
require((ROOT / "app/src/androidTest/java/ir/restaurant/management/data/db/Migration53To54Test.kt").is_file(), "Migration53To54Test must be preserved")
require('versionName = "1.0.0"' in build, "production versionName must be 1.0.0")
require("versionCode = 209" in build, "versionCode 209 must be preserved")

# Branch-management fresh-install gate.
branch_contract = text("app/src/main/java/ir/restaurant/management/domain/branch/BranchModels.kt")
branch_repo = text("app/src/main/java/ir/restaurant/management/data/repository/LocalBranchRepository.kt")
branch_screen = text("app/src/main/java/ir/restaurant/management/ui/BranchManagementScreen.kt")
branch_vm = text("app/src/main/java/ir/restaurant/management/ui/BranchManagementViewModel.kt")
access = text("app/src/main/java/ir/restaurant/management/ui/AppScreenAccess.kt")
routes = text("app/src/main/java/ir/restaurant/management/ui/AdminRoutes.kt")
require("val branches: Flow<List<BranchRecord>>" in branch_contract, "BranchRepository must expose all branches for management")
require("override val branches" in branch_repo and "dao.observeAll()" in branch_repo, "LocalBranchRepository must observe all branches")
for method in ("repository.create", "repository.rename", "repository.setActive"):
    require(method in branch_vm, f"Branch management command missing: {method}")
for label in ("ایجاد اولین شعبه", "تغییر نام", "غیرفعال‌سازی", "فعال‌سازی"):
    require(label in branch_screen, f"Branch management UI action missing: {label}")
require("Permission.BRANCH_MANAGE" in access and "AppScreen.BRANCHES" in access, "Branch management route must require BRANCH_MANAGE")
require("AppScreen.BRANCHES" in routes and "BranchManagementScreen" in routes, "Branch management route must be functional")
require("authorizer.require(Permission.BRANCH_MANAGE)" in branch_repo, "Branch mutations must enforce BRANCH_MANAGE in application/data layer")

# Dashboard canonical branch identity gate. branchName is permitted only as display metadata
# or at the final compatibility boundary for legacy analytics tables without branchId.
dashboard_repo = text("app/src/main/java/ir/restaurant/management/data/repository/DashboardRepository.kt")
dashboard_vm = text("app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt")
dashboard_screen = text("app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt")
dashboard_app = text("app/src/main/java/ir/restaurant/management/ui/RestaurantManagementApp.kt")
dashboard_dao = text("app/src/main/java/ir/restaurant/management/data/db/DashboardAnalyticsDao.kt")
repo_contract = re.search(r"fun\s+observeRange\s*\((.*?)\)\s*:\s*Flow<DashboardSnapshot>", dashboard_repo, re.S)
require(repo_contract is not None, "DashboardRepository.observeRange contract missing")
if repo_contract is not None:
    params = repo_contract.group(1)
    require(re.search(r"\bbranchId\s*:\s*Long\?\s*=\s*null", params) is not None, "DashboardRepository selection identity must be branchId: Long?")
    require(re.search(r"\bbranchName\s*:\s*String\?", params) is None, "DashboardRepository public selection contract must not accept branchName")
require("val selectedBranchId: Long?" in dashboard_repo, "DashboardSnapshot must expose selectedBranchId")
require("data class DashboardBranchOption" in dashboard_repo and "val id: Long" in dashboard_repo and "val name: String" in dashboard_repo, "Dashboard branch options must carry canonical id and display name")
require("availableBranches: List<DashboardBranchOption>" in dashboard_repo, "Dashboard available branches must not be List<String>")
require("database.branchDao().observeAll()" in dashboard_repo, "DashboardRepository must source branch choices from canonical Branch master")
require("branchId = branch.selectedBranchId" in dashboard_repo, "DashboardRepository must pass canonical branchId to analytics boundary")
require("branchName = branch.selectedBranchName" not in dashboard_repo, "Dashboard repository must not convert canonical identity back to current display name")
require("branchId: Long?" in dashboard_dao, "Dashboard analytics boundary must receive canonical branchId")
if current_room_version == 55:
    require("branch_legacy_aliases" in dashboard_dao and "deterministic_branch_aliases" in dashboard_dao, "v55 Dashboard compatibility must use deterministic legacy branch aliases")
    require("branchName: String?" not in dashboard_dao, "v55 analytics boundary must not accept branchName identity")
require("fun observeBranches()" not in dashboard_dao and "Flow<List<String>>" not in dashboard_dao, "Dashboard analytics must not expose legacy String-only branch options")

require("private val selectedBranchId = MutableStateFlow<Long?>(null)" in dashboard_vm, "DashboardViewModel must store branch selection as Long?")
require(re.search(r"fun\s+selectBranch\s*\(branchId:\s*Long\?\)", dashboard_vm) is not None, "DashboardViewModel must expose selectBranch(branchId: Long?)")
require("private val branchName = MutableStateFlow<String?>(null)" not in dashboard_vm, "DashboardViewModel must not store branchName as selection identity")
require(re.search(r"fun\s+branch\s*\([^)]*String\?", dashboard_vm) is None, "legacy String-based DashboardViewModel branch selector is forbidden")
require("val branchId: Long?" in dashboard_vm and "selected.branchId" in dashboard_vm, "Dashboard query state must retain branchId")

require("onBranchSelected: (Long?) -> Unit" in dashboard_screen, "Dashboard UI callback must be ID-based")
require("state.selectedBranchId == branch.id" in dashboard_screen, "Dashboard branch chip selection must compare branchId")
require("onBranchSelected(branch.id)" in dashboard_screen, "Dashboard branch chip must emit branch.id")
require(re.search(r"onBranch(?:Selected)?\s*:\s*\(String\?\)\s*->", dashboard_screen) is None, "Dashboard UI must not expose String branch selection callback")
require("dashboard::selectBranch" in dashboard_app, "Dashboard route must bind the ID-based ViewModel selector")

# Test path integrity: every projectFile("...") target must exist.
missing_projectfile_targets: list[str] = []
for test_root in (ROOT / "app/src/test", ROOT / "app/src/androidTest"):
    if not test_root.is_dir():
        continue
    for test_file in test_root.rglob("*.kt"):
        body = test_file.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r'projectFile\(\s*"([^"\n]+)"\s*\)', body):
            target = ROOT / match.group(1)
            if not target.is_file():
                missing_projectfile_targets.append(f"{test_file.relative_to(ROOT)} -> {match.group(1)}")
for item in missing_projectfile_targets:
    failures.append(f"missing projectFile target: {item}")

# No active development-era names outside migration compatibility source.
phase_re = re.compile(r"Phase1|Phase2|phase1|phase2|alpha1|alpha2", re.I)
for p in MAIN.rglob("*.kt"):
    rel = p.relative_to(ROOT).as_posix()
    if "/migration/" in rel:
        continue
    if phase_re.search(p.name) or phase_re.search(p.read_text(encoding="utf-8", errors="replace")):
        failures.append(f"development-era naming in active production source: {rel}")

# Product-facing CRM marketing label is forbidden; internal CRM symbols/packages are explicitly allowed.
ui_root = MAIN / "ir/restaurant/management/ui"
for p in ui_root.rglob("*.kt"):
    body = p.read_text(encoding="utf-8", errors="replace")
    if re.search(r'"[^"\n]*\bCRM\b[^"\n]*"', body):
        failures.append(f"product-facing CRM label remains: {p.relative_to(ROOT)}")

# Active implementation scans exclude historical migrations.
forbidden_features = re.compile(r"\b(RestaurantTable|RestaurantHall|Reservation|RestaurantOrder|Waiter|KDS|KitchenTicket)\b|Restaurant POS", re.I)
brand = re.compile(r"sabou|sabu|saboo|صابو|سابو|سبوی عشق|CafeManager|ir\.sabou", re.I)
default_branch = re.compile(r"branchId\s*=\s*1\b|branchId\s*\?:\s*1\b")
for p in MAIN.rglob("*.kt"):
    rel = p.relative_to(ROOT).as_posix()
    if "/migration/" in rel:
        continue
    body = p.read_text(encoding="utf-8", errors="replace")
    if forbidden_features.search(body): failures.append(f"forbidden restaurant-service feature in active source: {rel}")
    if brand.search(body): failures.append(f"forbidden legacy brand in active source: {rel}")
    if default_branch.search(body): failures.append(f"default Branch 1 pattern in active source: {rel}")
    if "TODO()" in body or "NotImplementedError(" in body: failures.append(f"critical placeholder in active source: {rel}")

# Current documentation brand scan excludes docs/history by design.
current_docs = [ROOT/"README.md", ROOT/"ARCHITECTURE-CURRENT.md", ROOT/"ARCHITECTURE-FREEZE.md", ROOT/"PRODUCT-TERMINOLOGY.md", ROOT/"UI-STANDARDS.md", ROOT/"UI-COMPONENT-INVENTORY.md"]
current_docs += [p for p in (ROOT/"docs").glob("*.md")]
for p in current_docs:
    if p.is_file() and brand.search(p.read_text(encoding="utf-8", errors="replace")):
        failures.append(f"forbidden legacy brand in current documentation: {p.relative_to(ROOT)}")

readiness = text(".github/workflows/production-readiness.yml")
require("git diff --exit-code -- app/schemas" in readiness, "production readiness must hard-gate Room schema drift")
for forbidden in ("continue-on-error", "|| true", "allow_failure"):
    require(forbidden not in readiness, f"critical CI false-green marker is forbidden: {forbidden}")
for test_task in ("testDebugUnitTest", "testReleaseUnitTest", "connectedDebugAndroidTest"):
    require(test_task not in readiness, f"runtime test task must not hard-gate production readiness: {test_task}")
for gate in ("verify-room-schema.py", "verify-documentation.py", "test-verify-documentation.py", "verify-repository-hygiene.py", "verify-security.py", "verify-foundation.sh", "verify-code-quality.py", "lintDebug", "assembleDebug"):
    require(gate in readiness, f"production readiness gate missing: {gate}")

tests_workflow = text(".github/workflows/tests.yml")
require("workflow_dispatch" in tests_workflow, "test workflow must remain manually runnable for Phase 3")
require("testDebugUnitTest" in tests_workflow, "JVM unit test infrastructure missing")
require("connectedDebugAndroidTest" in tests_workflow, "instrumentation test infrastructure missing")

if failures:
    print("PRE_PHASE3_STANDARDIZATION=FAIL")
    for item in failures:
        print(f" - {item}")
    sys.exit(1)

print("PRE_PHASE3_STANDARDIZATION=PASS")
print(f"ROOM_VERSION={current_room_version}")
print("HISTORICAL_MIGRATIONS=PRESERVED")
print("BRANCH_MANAGEMENT_FRESH_INSTALL=VERIFIED_STATIC")
print("CANONICAL_BRANCH_IDENTITY_VERIFIER=PASS")
print("DASHBOARD_REPOSITORY_BRANCH_IDENTITY=branchId")
print("DASHBOARD_VIEWMODEL_BRANCH_IDENTITY=branchId")
print("DASHBOARD_UI_BRANCH_CALLBACK=Long?")
print(f"MISSING_PROJECTFILE_TARGETS={len(missing_projectfile_targets)}")
print("DEFAULT_BRANCH_1=ABSENT_ACTIVE_SOURCE")
print("ACTIVE_DEVELOPMENT_NAMING=0")
print("PRODUCT_CRM_MARKETING_LABELS=0")
print("FORBIDDEN_ACTIVE_POS_TABLE_KDS=0")
print("FORBIDDEN_BRANDS_CURRENT=0")
print("CRITICAL_TODO_NOT_IMPLEMENTED=0")
