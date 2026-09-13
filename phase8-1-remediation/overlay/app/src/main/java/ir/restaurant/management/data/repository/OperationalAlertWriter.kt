package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase

/** Event-driven operational alerts. Periodic alert refresh never auto-resolves these rows. */
class OperationalAlertWriter(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun append(
        sourceType: String,
        sourceId: Long,
        title: String,
        message: String,
        severity: String = "HIGH",
        branchId: Long = 0L,
        locationId: Long = 0L,
    ) {
        val now = clock()
        val updated = database.alertDao().updateGenerated(
            sourceType = sourceType,
            sourceId = sourceId,
            title = title,
            message = message,
            severity = severity,
            dueEpochDay = null,
            branchId = branchId,
            locationId = locationId,
            now = now,
        )
        if (updated == 0) {
            database.alertDao().insertGeneratedIfAbsent(
                sourceType = sourceType,
                sourceId = sourceId,
                title = title,
                message = message,
                severity = severity,
                dueEpochDay = null,
                branchId = branchId,
                locationId = locationId,
                now = now,
            )
        }
    }
}
