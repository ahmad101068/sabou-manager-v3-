package ir.restaurant.management.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

internal object DatabaseHealthValidator {
    fun validateStartup(sqlite: SupportSQLiteDatabase) {
        val quick = sqlite.query("PRAGMA quick_check").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(quick.size == 1 && quick.single().equals("ok", ignoreCase = true)) {
            "DATABASE_QUICK_CHECK_FAILED:${quick.joinToString("|")}"
        }
        val version = sqlite.query("PRAGMA user_version").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        check(version == APP_DATABASE_SCHEMA_VERSION) {
            "DATABASE_SCHEMA_VERSION_MISMATCH:$version/$APP_DATABASE_SCHEMA_VERSION"
        }
        validateRequiredGuards(sqlite)
    }

    fun validateForeignKeys(sqlite: SupportSQLiteDatabase) {
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "DATABASE_FOREIGN_KEY_CHECK_FAILED" }
        }
    }

    private fun validateRequiredGuards(sqlite: SupportSQLiteDatabase) {
        val required = setOf(
            "prevent_closed_sales_day_insert",
            "prevent_closed_accounting_insert",
            "validate_journal_line_insert",
            "trg_recipe_versions_no_update",
            "trg_audit_logs_no_update",
            "trg_stock_movements_no_update",
        )
        val existing = sqlite.query(
            "SELECT name FROM sqlite_master WHERE type='trigger' AND name IN (${required.joinToString(",") { "'${it}'" }})",
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        check(existing == required) {
            "DATABASE_REQUIRED_GUARDS_MISSING:${(required - existing).sorted().joinToString(",")}"
        }
    }
}
