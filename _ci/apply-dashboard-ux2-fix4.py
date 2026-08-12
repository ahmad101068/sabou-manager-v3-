#!/usr/bin/env python3
from pathlib import Path

root = Path.cwd()
android_test = root / "app/src/androidTest/java/ir/sabou/inventory"
full_migration = android_test / "data/db/FullMigration1ToCurrentTest.kt"
enterprise_migration = android_test / "data/db/EnterpriseCompletionMigration44To47Test.kt"
procurement_migration = android_test / "data/db/ProcurementMigration23To24Test.kt"

# Room schema JSON files live in androidTest assets. ApplicationProvider returns the
# target-app context, which cannot see test-APK assets on device. Read schema evidence
# from the instrumentation APK instead while keeping the DB itself in target context.
for path, expected_schema in ((full_migration, "1.json"), (enterprise_migration, "44.json")):
    text = path.read_text(encoding="utf-8")
    if "import androidx.test.platform.app.InstrumentationRegistry" not in text:
        anchor = "import androidx.test.ext.junit.runners.AndroidJUnit4\n"
        if anchor not in text:
            raise SystemExit(f"AndroidJUnit4 import anchor missing in {path.name}")
        text = text.replace(anchor, anchor + "import androidx.test.platform.app.InstrumentationRegistry\n", 1)
    if "private val schemaAssets = InstrumentationRegistry.getInstrumentation().context.assets" not in text:
        context_anchor = "    private val context = ApplicationProvider.getApplicationContext<Context>()\n"
        if context_anchor not in text:
            raise SystemExit(f"Context anchor missing in {path.name}")
        text = text.replace(
            context_anchor,
            context_anchor + "    private val schemaAssets = InstrumentationRegistry.getInstrumentation().context.assets\n",
            1,
        )
    old = "val root = context.assets\n            .open(\"ir.sabou.inventory.data.db.AppDatabase/" + expected_schema + "\")"
    new = "val root = schemaAssets\n            .open(\"ir.sabou.inventory.data.db.AppDatabase/" + expected_schema + "\")"
    if old not in text and new not in text:
        raise SystemExit(f"Schema asset read not found in {path.name}")
    text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")

# The v23 procurement migration introduces FKs to inventory_items and purchases.
# The old test seed created only suppliers, which is not a structurally valid v23
# dependency set and crashes the Android 6 framework SQLite process during migration.
# Seed the minimal parent tables instead of weakening or skipping the migration test.
text = procurement_migration.read_text(encoding="utf-8")
seed_anchor = '            db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")\n'
parent_seed = (
    '            db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")\n'
    '            db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")\n'
)
if parent_seed not in text:
    if seed_anchor not in text:
        raise SystemExit("Procurement v23 supplier seed anchor missing")
    text = text.replace(seed_anchor, seed_anchor + parent_seed, 1)
if text.count("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)") != 1:
    raise SystemExit("Expected exactly one minimal inventory_items parent seed")
if text.count("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)") != 1:
    raise SystemExit("Expected exactly one minimal purchases parent seed")
procurement_migration.write_text(text, encoding="utf-8")

print("DASHBOARD_UX2_INSTRUMENTATION_MIGRATION_FIX=PASS schema_assets=2 procurement_parents=2")
