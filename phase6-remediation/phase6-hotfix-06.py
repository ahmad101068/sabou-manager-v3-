#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
target = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6AlertIntegrationTest.kt"
text = target.read_text(encoding="utf-8")

old = "authorizer = SessionAuthorizer(database, clock = { now })"
new = "authorizer = SessionAuthorizer(database)"
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("expected Phase6 SessionAuthorizer fixture not found")

if "SessionAuthorizer(database, clock" in text:
    raise SystemExit("unsupported SessionAuthorizer clock remains")
if new not in text:
    raise SystemExit("canonical SessionAuthorizer constructor not established")

target.write_text(text, encoding="utf-8")
print("PHASE6_HOTFIX_06_SESSION_AUTHORIZER_API=APPLIED")
