#!/usr/bin/env python3
from pathlib import Path
p = Path('app/src/androidTest/java/ir/sabou/inventory/data/db/ProcurementMigration23To24Test.kt')
text = p.read_text(encoding='utf-8')
old = '''    private fun migrateProductionChain(\n        db: SupportSQLiteDatabase,\n        fromVersion: Int,\n        toVersion: Int,\n    ) {\n        var current = fromVersion\n        while (current < toVersion) {\n            val next = current + 1\n            val migration = ALL_MIGRATIONS.singleOrNull {\n                it.startVersion == current && it.endVersion == next\n            } ?: error("Missing production migration ${'$'}current→${'$'}next")\n            migration.migrate(db)\n            db.version = next\n            current = next\n        }\n    }\n'''
new = '''    private fun migrateProductionChain(\n        db: SupportSQLiteDatabase,\n        fromVersion: Int,\n        toVersion: Int,\n    ) {\n        android.util.Log.i("ProcurementMigration", "chain-start $fromVersion→$toVersion")\n        db.beginTransaction()\n        try {\n            var current = fromVersion\n            while (current < toVersion) {\n                val next = current + 1\n                val migration = ALL_MIGRATIONS.singleOrNull {\n                    it.startVersion == current && it.endVersion == next\n                } ?: error("Missing production migration ${'$'}current→${'$'}next")\n                android.util.Log.i("ProcurementMigration", "before $current→$next")\n                migration.migrate(db)\n                db.version = next\n                android.util.Log.i("ProcurementMigration", "after $current→$next")\n                current = next\n            }\n            db.setTransactionSuccessful()\n        } finally {\n            db.endTransaction()\n        }\n        android.util.Log.i("ProcurementMigration", "chain-end $fromVersion→$toVersion")\n    }\n'''
if old not in text:
    raise SystemExit('fix10 target migrateProductionChain block not found exactly')
text = text.replace(old,new,1)
text = text.replace(
    '            db.execSQL(\n                """INSERT INTO suppliers(',
    '            android.util.Log.i("ProcurementMigration", "before supplier seed")\n            db.execSQL(\n                """INSERT INTO suppliers(',
    1,
)
text = text.replace(
    '            migrateProductionChain(db, fromVersion = 23, toVersion = 24)',
    '            android.util.Log.i("ProcurementMigration", "after supplier seed")\n            migrateProductionChain(db, fromVersion = 23, toVersion = 24)',
    1,
)
p.write_text(text, encoding='utf-8')
checks = ['beginTransaction()', 'setTransactionSuccessful()', 'android.util.Log.i("ProcurementMigration", "before $current→$next")', 'after supplier seed', 'PRAGMA foreign_key_check']
missing=[c for c in checks if c not in text]
if missing: raise SystemExit(f'fix10 verification failed: {missing}')
print('DASHBOARD_UX2_FIX10_API23_MIGRATION_TRANSACTION=PASS atomic_chain=1 markers=1')
