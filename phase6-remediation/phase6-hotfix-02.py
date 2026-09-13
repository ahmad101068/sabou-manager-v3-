#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

# 1) API35 alert lifecycle fixture must drive the same deterministic clock
# used by the repository. Do not weaken production snooze validation.
alert = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/AlertStateIntegrationTest.kt"
text = alert.read_text(encoding="utf-8")
if "clock = { now }" not in text.split("repository.snooze", 1)[0]:
    candidates = [
        (
            "repository = LocalAlertRepository(database, SessionAuthorizer(database))",
            "repository = LocalAlertRepository(database, SessionAuthorizer(database), clock = { now })",
        ),
        (
            "repository = LocalAlertRepository(database, authorizer)",
            "repository = LocalAlertRepository(database, authorizer, clock = { now })",
        ),
    ]
    applied = False
    for old, new in candidates:
        if old in text:
            text = text.replace(old, new, 1)
            applied = True
            break
    if not applied:
        raise SystemExit("AlertStateIntegrationTest repository clock fixture pattern not found")
    alert.write_text(text, encoding="utf-8")

alert_text = alert.read_text(encoding="utf-8")
if "clock = { now }" not in alert_text.split("repository.snooze", 1)[0]:
    raise SystemExit("AlertStateIntegrationTest deterministic clock was not established")
print("PHASE6_HOTFIX_02_ALERT_CLOCK=APPLIED_OR_ALREADY_CORRECT")

# 2) Phase-3 canonical user_branch_scopes schema uses createdAtEpochMillis.
# Correct only the Phase-6 integration fixture; production schema/migration stays unchanged.
security = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"
sec = security.read_text(encoding="utf-8")
count = sec.count("grantedAtEpochMillis")
if count:
    sec = sec.replace("grantedAtEpochMillis", "createdAtEpochMillis")
    security.write_text(sec, encoding="utf-8")
if "grantedAtEpochMillis" in security.read_text(encoding="utf-8"):
    raise SystemExit("legacy grantedAtEpochMillis fixture reference remains")
if "user_branch_scopes" not in security.read_text(encoding="utf-8"):
    raise SystemExit("expected user_branch_scopes fixture not found")
print(f"PHASE6_HOTFIX_02_SCOPE_FIXTURE=APPLIED_{count}")
