package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppAlertEntity
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.GeneratedAlertRow
import ir.restaurant.management.domain.operations.AlertRepository
import ir.restaurant.management.domain.operations.AlertTarget
import ir.restaurant.management.domain.operations.AppAlert
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class LocalAlertRepository(
    private val db: AppDatabase,
    private val authorizer: AuthorizationService,
) : AlertRepository {
    private val dataScope by lazy {
        require(authorizer is ir.restaurant.management.data.security.SessionAuthorizer) {
            "Alert scope requires the canonical session authorizer."
        }
        LocalDataScopeService(db, authorizer)
    }

    override fun alerts() = flow {
        authorizer.actorIdentity()
        requireAnyAlertDomainPermission()
        emitAll(
            combine(db.alertDao().observeVisible(), dataScope.scopedBranches(), dataScope.scopedLocations()) { rows, branches, locations ->
                val permissions = requireAnyAlertDomainPermission()
                val branchIds = branches.mapTo(mutableSetOf()) { it.id }
                val locationIds = locations.mapTo(mutableSetOf()) { it.id }
                val now = System.currentTimeMillis()
                rows.asSequence()
                    .filter { it.sourceType in permissions }
                    .filter { it.snoozedUntilEpochMillis == null || it.snoozedUntilEpochMillis <= now }
                    .filter { it.branchId == 0L || it.branchId in branchIds }
                    .filter { it.locationId == 0L || it.locationId in locationIds }
                    .map { it.toDomain() }
                    .toList()
            },
        )
    }

    override suspend fun refresh(todayEpochDay: Long) {
        require(todayEpochDay > 0)
        authorizer.actorIdentity()
        val permitted = requireAnyAlertDomainPermission()
        val scopedBranches = dataScope.activeBranches().mapTo(mutableSetOf()) { it.id }
        val scopedLocations = dataScope.activeLocations().mapTo(mutableSetOf()) { it.id }
        db.withTransaction {
            val now = System.currentTimeMillis()
            val horizon = todayEpochDay + 30
            val activeKeys = mutableSetOf<Triple<String, Long, Long>>()

            suspend fun upsert(sourceType: String, rows: List<GeneratedAlertRow>) {
                if (sourceType !in permitted) return
                rows.asSequence()
                    .filter { it.branchId == 0L || it.branchId in scopedBranches }
                    .filter { it.locationId == 0L || it.locationId in scopedLocations }
                    .forEach { row ->
                        activeKeys += Triple(sourceType, row.sourceId, row.locationId)
                        val updated = db.alertDao().updateGenerated(
                            sourceType = sourceType, sourceId = row.sourceId, title = row.title, message = row.message,
                            severity = row.severity, dueEpochDay = row.dueEpochDay, branchId = row.branchId,
                            locationId = row.locationId, now = now,
                        )
                        if (updated == 0) {
                            db.alertDao().insertGeneratedIfAbsent(
                                sourceType = sourceType, sourceId = row.sourceId, title = row.title, message = row.message,
                                severity = row.severity, dueEpochDay = row.dueEpochDay, branchId = row.branchId,
                                locationId = row.locationId, now = now,
                            )
                        }
                    }
            }

            upsert(PURCHASE_PAYABLE, db.alertDao().overduePurchases(todayEpochDay))
            upsert(LOW_STOCK, db.alertDao().lowStock(todayEpochDay))
            upsert(LOT_EXPIRING, db.alertDao().expiringLots(todayEpochDay, horizon))
            upsert(LOT_EXPIRED, db.alertDao().expiredLots(todayEpochDay))
            upsert(CUSTOMER_RECEIVABLE, db.alertDao().overdueReceivables(todayEpochDay))
            upsert(CONTRACT_EXPIRY, db.alertDao().expiringContracts(todayEpochDay, horizon))
            upsert(UNPAID_PAYROLL, db.alertDao().unpaidPayroll())
            upsert(ASSET_MAINTENANCE, db.alertDao().dueAssetMaintenance(todayEpochDay, horizon))
            upsert(ATTENDANCE_ANOMALY, db.alertDao().attendanceAnomalies())
            upsert(PURCHASE_DELIVERY_OVERDUE, db.alertDao().overdueDeliveries(todayEpochDay))
            upsert(INVENTORY_DISCREPANCY, db.alertDao().inventoryDiscrepancies())

            db.alertDao().generatedKeys().forEach { key ->
                if (key.sourceType in PERIODIC_SOURCE_TYPES && key.sourceType in permitted && Triple(key.sourceType, key.sourceId, key.locationId) !in activeKeys) {
                    db.alertDao().resolveGenerated(key.sourceType, key.sourceId, key.locationId, now)
                }
            }
        }
    }

    override suspend fun markRead(id: Long) { requireAlertAccess(id); require(db.alertDao().markRead(id, System.currentTimeMillis()) == 1) { "هشدار پیدا نشد." } }
    override suspend fun markActioned(id: Long) { requireAlertAccess(id); require(db.alertDao().markActioned(id, System.currentTimeMillis()) == 1) { "هشدار قابل اقدام پیدا نشد." } }
    override suspend fun resolve(id: Long) { requireAlertAccess(id); require(db.alertDao().resolve(id, System.currentTimeMillis()) == 1) { "هشدار قابل حل پیدا نشد." } }
    override suspend fun dismiss(id: Long) { requireAlertAccess(id); require(db.alertDao().dismiss(id, System.currentTimeMillis()) == 1) { "هشدار پیدا نشد." } }
    override suspend fun snooze(id: Long, untilEpochMillis: Long) {
        requireAlertAccess(id)
        val now = System.currentTimeMillis()
        require(untilEpochMillis > now) { "زمان تعویق هشدار باید در آینده باشد." }
        require(db.alertDao().snooze(id, untilEpochMillis, now) == 1) { "هشدار قابل تعویق پیدا نشد." }
    }

    override suspend fun clearDismissed() {
        authorizer.actorIdentity()
        db.alertDao().clearDismissedForTypes(requireAnyAlertDomainPermission().toList())
    }

    private suspend fun requireAlertAccess(id: Long): AppAlertEntity {
        val row = db.alertDao().byId(id) ?: error("هشدار پیدا نشد.")
        authorizer.require(requiredPermission(row.sourceType))
        if (row.locationId > 0) dataScope.requireLocation(row.locationId, row.branchId.takeIf { it > 0 })
        else if (row.branchId > 0) dataScope.requireBranch(row.branchId, operational = false)
        return row
    }

    private suspend fun permittedSourceTypes(): Set<String> = SOURCE_PERMISSION.entries
        .filter { (_, permission) -> authorizer.can(permission) }
        .mapTo(mutableSetOf()) { it.key }

    private suspend fun requireAnyAlertDomainPermission(): Set<String> {
        val permitted = permittedSourceTypes()
        if (permitted.isNotEmpty()) return permitted
        // There is deliberately no global "alert" permission: force a canonical
        // data-boundary denial only when the actor has none of the alert domains.
        authorizer.require(SOURCE_PERMISSION.values.first())
        error("unreachable")
    }

    private fun requiredPermission(sourceType: String): Permission = SOURCE_PERMISSION[sourceType] ?: Permission.AUDIT_VIEW

    private fun AppAlertEntity.toDomain() = AppAlert(
        id = id, sourceType = sourceType, sourceId = sourceId, title = title, message = message, severity = severity,
        dueEpochDay = dueEpochDay, isRead = isRead, isDismissed = isDismissed, createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis, status = status, branchId = branchId, locationId = locationId,
        snoozedUntilEpochMillis = snoozedUntilEpochMillis, target = when (sourceType) {
            LOW_STOCK -> AlertTarget.InventoryItem(sourceId)
            LOT_EXPIRING, LOT_EXPIRED -> AlertTarget.InventoryLot(sourceId)
            INVENTORY_DISCREPANCY -> AlertTarget.InventoryCount(sourceId)
            PURCHASE_PAYABLE -> AlertTarget.Purchase(sourceId)
            PURCHASE_DELIVERY_OVERDUE -> AlertTarget.PurchaseOrder(sourceId)
            CUSTOMER_RECEIVABLE -> AlertTarget.Receivable(sourceId)
            CONTRACT_EXPIRY -> AlertTarget.EmploymentContract(sourceId)
            UNPAID_PAYROLL -> AlertTarget.Payroll(sourceId)
            ATTENDANCE_ANOMALY -> AlertTarget.AttendanceCorrection(sourceId)
            ASSET_MAINTENANCE -> AlertTarget.Asset(sourceId)
            DATABASE_INTEGRITY_FAILURE, RECONCILIATION_FAILURE, BACKUP_FAILURE, RESTORE_ANOMALY -> AlertTarget.SecurityEvent(sourceId)
            else -> AlertTarget.None
        },
    )

    companion object {
        const val PURCHASE_PAYABLE = "PURCHASE_PAYABLE"
        const val LOW_STOCK = "LOW_STOCK"
        const val LOT_EXPIRING = "LOT_EXPIRING"
        const val LOT_EXPIRED = "LOT_EXPIRED"
        const val CUSTOMER_RECEIVABLE = "CUSTOMER_RECEIVABLE"
        const val CONTRACT_EXPIRY = "CONTRACT_EXPIRY"
        const val UNPAID_PAYROLL = "UNPAID_PAYROLL"
        const val ASSET_MAINTENANCE = "ASSET_MAINTENANCE"
        const val ATTENDANCE_ANOMALY = "ATTENDANCE_ANOMALY"
        const val PURCHASE_DELIVERY_OVERDUE = "PURCHASE_DELIVERY_OVERDUE"
        const val INVENTORY_DISCREPANCY = "INVENTORY_DISCREPANCY"
        const val DATABASE_INTEGRITY_FAILURE = "DATABASE_INTEGRITY_FAILURE"
        const val RECONCILIATION_FAILURE = "RECONCILIATION_FAILURE"
        const val BACKUP_FAILURE = "BACKUP_FAILURE"
        const val RESTORE_ANOMALY = "RESTORE_ANOMALY"

        private val PERIODIC_SOURCE_TYPES = setOf(
            PURCHASE_PAYABLE, LOW_STOCK, LOT_EXPIRING, LOT_EXPIRED, CUSTOMER_RECEIVABLE, CONTRACT_EXPIRY,
            UNPAID_PAYROLL, ASSET_MAINTENANCE, ATTENDANCE_ANOMALY, PURCHASE_DELIVERY_OVERDUE, INVENTORY_DISCREPANCY,
        )

        private val SOURCE_PERMISSION = mapOf(
            LOW_STOCK to Permission.INVENTORY_VIEW, LOT_EXPIRING to Permission.INVENTORY_VIEW, LOT_EXPIRED to Permission.INVENTORY_VIEW,
            INVENTORY_DISCREPANCY to Permission.INVENTORY_VIEW, PURCHASE_PAYABLE to Permission.PURCHASES,
            PURCHASE_DELIVERY_OVERDUE to Permission.PURCHASES, CUSTOMER_RECEIVABLE to Permission.RECEIVABLE_VIEW,
            CONTRACT_EXPIRY to Permission.PERSONNEL_VIEW, UNPAID_PAYROLL to Permission.PAYROLL_VIEW_ALL,
            ATTENDANCE_ANOMALY to Permission.PERSONNEL_VIEW, ASSET_MAINTENANCE to Permission.ASSETS,
            DATABASE_INTEGRITY_FAILURE to Permission.AUDIT_VIEW, RECONCILIATION_FAILURE to Permission.AUDIT_VIEW,
            BACKUP_FAILURE to Permission.BACKUP, RESTORE_ANOMALY to Permission.RESTORE,
        )
    }
}
