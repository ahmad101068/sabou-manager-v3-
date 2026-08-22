#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
repo_dir = root / "app/src/androidTest/java/ir/restaurant/management/data/repository"

# 1) Existing alert-state integration test: LocalAlertRepository uses the real system
# clock by design. Remove the invalid injected clock and make every snooze deadline
# used by this test derive from the real wall clock, without changing production code.
state_path = repo_dir / "AlertStateIntegrationTest.kt"
state = state_path.read_text(encoding="utf-8")
state = state.replace(
    "repository = LocalAlertRepository(database, authorizer, clock = { now })",
    "repository = LocalAlertRepository(database, authorizer)",
)
# Direct snooze calls based on the deterministic fixture clock.
state = re.sub(
    r"(repository\.snooze\([^,\n]+,\s*)now\s*\+\s*[^)\n]+(\))",
    r"\1System.currentTimeMillis() + 120_000L\2",
    state,
)
# Named snooze/until deadline variables based on the deterministic fixture clock.
state = re.sub(
    r"(val\s+[A-Za-z0-9_]*(?:snooze|Snooze|until|Until)[A-Za-z0-9_]*\s*=\s*)now\s*\+\s*[^\n]+",
    r"\1System.currentTimeMillis() + 120_000L",
    state,
)
state_path.write_text(state, encoding="utf-8")

# 2) Canonical receivable alert test created by hotfix-03: align names with the exact
# typed drill-down contract and remove the unsupported LocalAlertRepository clock arg.
recv_path = repo_dir / "AlertReceivableIntegrationTest.kt"
recv = recv_path.read_text(encoding="utf-8")
recv = recv.replace(
    "import ir.restaurant.management.domain.operations.AlertDrillDownType",
    "import ir.restaurant.management.domain.operations.AlertDrillDownTarget",
)
recv = recv.replace(
    "alerts = LocalAlertRepository(database, authorizer, clock = { now })",
    "alerts = LocalAlertRepository(database, authorizer)",
)
recv = recv.replace("AlertDrillDownType.RECEIVABLE", "AlertDrillDownTarget.RECEIVABLE")
recv = recv.replace(".drillDownType", ".drillDownTarget")
recv_path.write_text(recv, encoding="utf-8")

# 3) Dedicated Phase-6 alert integration test created by hotfix-04: align to the exact
# production contract. Security/session clocks remain injectable; alert repository does not.
p6_path = repo_dir / "Phase6AlertIntegrationTest.kt"
p6 = p6_path.read_text(encoding="utf-8")
p6 = p6.replace(
    "import ir.restaurant.management.domain.operations.AlertDrillDownType",
    "import ir.restaurant.management.domain.operations.AlertDrillDownTarget",
)
p6 = p6.replace(
    "LocalAlertRepository(database, authorizer, clock = { now })",
    "LocalAlertRepository(database, authorizer)",
)
p6 = p6.replace("AlertDrillDownType.INVENTORY", "AlertDrillDownTarget.INVENTORY_ITEM")
p6 = p6.replace(".drillDownType", ".drillDownTarget")
p6 = p6.replace("val until = now + 120_000", "val until = System.currentTimeMillis() + 120_000L")
p6_path.write_text(p6, encoding="utf-8")

# Fail closed if stale/non-production alert API assumptions remain.
for path in (state_path, recv_path, p6_path):
    text = path.read_text(encoding="utf-8")
    if "LocalAlertRepository(database, authorizer, clock" in text:
        raise SystemExit(f"unsupported LocalAlertRepository clock remains in {path.name}")
    if "AlertDrillDownType" in text or ".drillDownType" in text:
        raise SystemExit(f"obsolete alert drill-down API remains in {path.name}")

state_check = state_path.read_text(encoding="utf-8")
for line in state_check.splitlines():
    if "snooze(" in line and "now +" in line:
        raise SystemExit(f"stale fixture-clock snooze remains: {line.strip()}")

recv_check = recv_path.read_text(encoding="utf-8")
for token in (
    "AlertDrillDownTarget.RECEIVABLE",
    ".drillDownTarget",
    "database.businessOperationsDao().insertReceivable",
):
    if token not in recv_check:
        raise SystemExit(f"receivable alert contract missing: {token}")

p6_check = p6_path.read_text(encoding="utf-8")
for token in (
    "AlertDrillDownTarget.INVENTORY_ITEM",
    ".drillDownTarget",
    "System.currentTimeMillis() + 120_000L",
    "manager must not mutate another branch alert",
):
    if token not in p6_check:
        raise SystemExit(f"Phase6 alert test contract missing: {token}")

print("PHASE6_HOTFIX_05_ALERT_API_ALIGNMENT=APPLIED")
