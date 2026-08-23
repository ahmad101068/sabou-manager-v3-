package ir.restaurant.management.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.restaurant.management.data.db.AuditLogEntity
import ir.restaurant.management.data.db.installAuditLogGuards
import ir.restaurant.management.data.security.AuditIntegrityCanonicalizer
import ir.restaurant.management.core.BusinessCalendar

internal val MIGRATION_59_60 = object : Migration(59, 60) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS trg_audit_logs_no_update")
        db.execSQL("DROP TRIGGER IF EXISTS trg_audit_logs_validate_insert")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN integritySequence INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN previousEventHash TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN eventHash TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_users ADD COLUMN rowVersion INTEGER NOT NULL DEFAULT 0")

        var sequence = 1L
        var previousHash = ""
        db.query("SELECT * FROM audit_logs ORDER BY id ASC").use { cursor ->
            fun string(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name))
            fun nullableString(name: String): String? = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getString(it) }
            fun long(name: String): Long = cursor.getLong(cursor.getColumnIndexOrThrow(name))
            fun nullableLong(name: String): Long? = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getLong(it) }
            while (cursor.moveToNext()) {
                val createdAt = long("createdAtEpochMillis")
                val action = string("action")
                val entityType = string("entityType")
                val storedBusinessDay = nullableLong("businessEpochDay")
                val canonicalBusinessDay = storedBusinessDay ?: if (entityType == "EMPLOYEE" && action == "STATUS_CHANGE") {
                    BusinessCalendar.epochDayAt(createdAt)
                } else null
                val row = AuditLogEntity(
                    id = long("id"), action = action, entityType = entityType, entityId = nullableLong("entityId"),
                    description = string("description"), actor = string("actor"), createdAtEpochMillis = createdAt,
                    globalId = string("globalId"), actorId = nullableLong("actorId"), businessEpochDay = canonicalBusinessDay,
                    deviceId = string("deviceId"), referenceType = nullableString("referenceType"), referenceId = nullableLong("referenceId"),
                    reason = string("reason"), beforeSnapshot = nullableString("beforeSnapshot"), afterSnapshot = nullableString("afterSnapshot"),
                    correlationId = string("correlationId"), actorRoleSnapshot = string("actorRoleSnapshot"), actorBranchIdSnapshot = nullableLong("actorBranchIdSnapshot"),
                    integritySequence = sequence, previousEventHash = previousHash,
                )
                val eventHash = AuditIntegrityCanonicalizer.hashEvent(row)
                db.execSQL(
                    "UPDATE audit_logs SET businessEpochDay=?,integritySequence=?,previousEventHash=?,eventHash=? WHERE id=?",
                    arrayOf(row.businessEpochDay, sequence, previousHash, eventHash, row.id),
                )
                previousHash = eventHash
                sequence++
            }
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_audit_logs_integritySequence ON audit_logs(integritySequence)")
        // One-time historical bootstrap only. Runtime allocation is CAS-based and never queries MAX(revisionNo).
        db.execSQL("""
            INSERT OR IGNORE INTO document_sequences(sequenceKey,nextValue,updatedAtEpochMillis)
            SELECT 'SALES_CASH_REVISION:' || businessEpochDay, MAX(revisionNo)+1, 0
            FROM sales_cash_reconciliations GROUP BY businessEpochDay
        """.trimIndent())
        installAuditLogGuards(db)
    }
}
