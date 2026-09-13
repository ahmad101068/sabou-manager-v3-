package ir.restaurant.management.domain.operations

import kotlinx.coroutines.flow.Flow

sealed interface AlertTarget {
    data class InventoryItem(val itemId: Long) : AlertTarget
    data class InventoryLot(val lotId: Long) : AlertTarget
    data class InventoryCount(val countId: Long) : AlertTarget
    data class Purchase(val purchaseId: Long) : AlertTarget
    data class PurchaseOrder(val purchaseOrderId: Long) : AlertTarget
    data class Receivable(val receivableId: Long) : AlertTarget
    data class EmploymentContract(val contractId: Long) : AlertTarget
    data class Payroll(val payrollId: Long) : AlertTarget
    data class AttendanceCorrection(val correctionId: Long) : AlertTarget
    data class Asset(val assetId: Long) : AlertTarget
    data class SecurityEvent(val eventId: Long) : AlertTarget
    data object None : AlertTarget
}

data class AppAlert(
    val id: Long,
    val sourceType: String,
    val sourceId: Long,
    val title: String,
    val message: String,
    val severity: String,
    val dueEpochDay: Long?,
    val isRead: Boolean,
    val isDismissed: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val status: String,
    val branchId: Long = 0,
    val locationId: Long = 0,
    val snoozedUntilEpochMillis: Long? = null,
    val target: AlertTarget = AlertTarget.None,
)

interface AlertRepository {
    fun alerts(): Flow<List<AppAlert>>
    suspend fun refresh(todayEpochDay: Long)
    suspend fun markRead(id: Long)
    suspend fun markActioned(id: Long)
    suspend fun resolve(id: Long)
    suspend fun dismiss(id: Long)
    suspend fun snooze(id: Long, untilEpochMillis: Long)
    suspend fun clearDismissed()
}
