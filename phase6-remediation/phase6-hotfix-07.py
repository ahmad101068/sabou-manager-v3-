#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
target = root / "app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6AlertIntegrationTest.kt"
text = target.read_text(encoding="utf-8")

needle = '''        security.save(null, UserDraft("phase6-alert-owner", "مالک هشدار فاز شش", "123456", UserRole.OWNER, "87654321"))
    }
'''
replacement = '''        security.save(null, UserDraft("phase6-alert-owner", "مالک هشدار فاز شش", "123456", UserRole.OWNER, "87654321"))
        Unit
    }
'''
if replacement not in text:
    count = text.count(needle)
    if count != 1:
        raise SystemExit(f"expected one Phase6 alert @Before return site, found {count}")
    text = text.replace(needle, replacement, 1)
    target.write_text(text, encoding="utf-8")

check = target.read_text(encoding="utf-8")
if replacement not in check:
    raise SystemExit("Phase6 alert @Before was not forced to Unit")
if 'fun setUp() = runBlocking {' not in check:
    raise SystemExit("unexpected Phase6 alert setUp shape")
print("PHASE6_HOTFIX_07_JUNIT_SETUP_UNIT=APPLIED_OR_ALREADY_CORRECT")
