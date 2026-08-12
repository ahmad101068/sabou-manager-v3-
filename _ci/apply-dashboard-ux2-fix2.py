#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/ir/sabou/inventory/ui/SabouApp.kt")
text = path.read_text(encoding="utf-8")

old_line = "val activity = LocalContext.current as? Activity"
count = text.count(old_line)
if count != 2:
    raise SystemExit(f"Expected exactly 2 LocalContext-to-Activity casts, found {count}")

if "import androidx.activity.compose.LocalActivity" not in text:
    anchor = "import androidx.activity.compose.BackHandler\n"
    if anchor not in text:
        raise SystemExit("BackHandler import anchor not found")
    text = text.replace(anchor, anchor + "import androidx.activity.compose.LocalActivity\n", 1)

text = text.replace(old_line, "val activity = LocalActivity.current")
text = text.replace("import android.app.Activity\n", "")

if "LocalContext.current as? Activity" in text:
    raise SystemExit("Context-to-Activity cast still present")
if text.count("LocalActivity.current") != 2:
    raise SystemExit("Expected exactly 2 LocalActivity.current usages")

path.write_text(text, encoding="utf-8")
print("DASHBOARD_UX2_LOCAL_ACTIVITY_FIX=PASS replacements=2")
