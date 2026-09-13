package ir.restaurant.management.domain.common

import ir.restaurant.management.domain.security.Permission

sealed interface DomainFailure

enum class JournalInvalidReason {
    UNBALANCED,
    MISSING_LINES,
    INVALID_ACCOUNT,
    INACTIVE_ACCOUNT,
    BUSINESS_DATE_MISMATCH,
    UNKNOWN_STATUS,
    MISSING_POSTING_CONTEXT,
}

enum class NumericFailureReason {
    NEGATIVE,
    ZERO_OR_NEGATIVE,
    OUT_OF_RANGE,
    OVERFLOW,
}

/**
 * Compatibility hierarchy for existing callers. New failures must carry structured facts or
 * machine-readable reasons; presentation text belongs to the UI mapping boundary.
 */
sealed interface BusinessError : DomainFailure {
    data class InsufficientStock(
        val itemId: Long,
        val itemName: String,
        val requestedMicros: Long,
        val availableMicros: Long,
    ) : BusinessError

    data class InsufficientInventoryValue(
        val itemId: Long,
        val itemName: String,
        val requestedRial: Long,
        val availableRial: Long,
    ) : BusinessError

    data class ClosedAccountingPeriod(val epochDay: Long) : BusinessError
    data class ClosedInventoryPeriod(val epochDay: Long) : BusinessError
    data class ClosedSalesDay(val epochDay: Long) : BusinessError
    data class DuplicateDocument(val documentType: String, val documentNumber: String) : BusinessError
    data class InvalidRecipe(val menuItemId: Long, val reason: String) : BusinessError
    data class PermissionDenied(val permission: Permission) : BusinessError
    data object AuthenticationRequired : BusinessError
    data class ApprovalRequired(val operation: String, val requiredLevel: Int) : BusinessError
    data class SeparationOfDutiesViolation(val operation: String) : BusinessError
    data class SupplierInactive(val supplierId: Long) : BusinessError
    data class InvalidLocation(val locationId: Long, val reason: String) : BusinessError
    data class InvalidLot(val lotId: Long?, val reason: String) : BusinessError
    data class LotExpired(val lotId: Long, val expiryEpochDay: Long) : BusinessError
    data class LotBlocked(val lotId: Long, val status: String) : BusinessError
    data class CountNotApproved(val sessionId: Long) : BusinessError
    data class CountAlreadyPosted(val sessionId: Long) : BusinessError
    data class CountUnitCostRequired(val lineId: Long) : BusinessError
    data class WasteNotApproved(val wasteId: Long) : BusinessError
    data class WasteAlreadyPosted(val wasteId: Long) : BusinessError
    data class TransferNotApproved(val transferId: Long) : BusinessError
    data class TransferAlreadyIssued(val transferId: Long) : BusinessError
    data class TransferAlreadyReceived(val transferId: Long) : BusinessError
    data class TransferVarianceRequiresApproval(
        val transferId: Long,
        val lineId: Long,
        val issuedQuantityMicros: Long,
        val receivedQuantityMicros: Long,
    ) : BusinessError
    data class EntityNotFound(val entityType: String, val entityId: Long?) : BusinessError
    data class IdempotencyConflict(val idempotencyKey: String) : BusinessError
    data class ConcurrentModification(val entityType: String, val entityId: Long) : BusinessError
    data class InvalidBusinessState(val entityType: String, val state: String) : BusinessError
    data class InvalidInput(val field: String, val reason: String) : BusinessError
    data class DuplicatePosting(
        val sourceType: String,
        val sourceId: Long,
        val idempotencyKey: String,
    ) : BusinessError
    data class InvalidJournal(val reason: JournalInvalidReason, val accountCode: String? = null) : BusinessError
    data class InvalidQuantity(val field: String, val reason: NumericFailureReason) : BusinessError
    data class InvalidMoney(val field: String, val reason: NumericFailureReason) : BusinessError
    data class InvalidStateTransition(
        val entityType: String,
        val fromState: String,
        val toState: String,
    ) : BusinessError
    data class ConcurrencyConflict(val entityType: String, val entityId: Long) : BusinessError
    data class UnknownStoredValue(
        val ownerDomain: String,
        val field: String,
        val storedValue: String?,
    ) : BusinessError
    data class UnsupportedDomainOperation(val ownerDomain: String, val operation: String) : BusinessError
    data class EmployeeNotActive(val employeeId: Long, val status: String) : BusinessError
    data class NoEffectiveContract(val employeeId: Long, val businessEpochDay: Long) : BusinessError
    data class ConflictingContracts(
        val employeeId: Long,
        val businessEpochDay: Long,
        val contractIds: List<Long>,
    ) : BusinessError
    data class InvalidAttendance(val employeeId: Long, val businessEpochDay: Long, val reason: String) : BusinessError
    data class PayrollAlreadyCalculated(val batchId: Long) : BusinessError
    data class PayrollAlreadyApproved(val batchId: Long) : BusinessError
    data class PayrollPeriodClosed(val periodId: Long) : BusinessError
    data class PayrollAlreadyPaid(val payslipId: Long) : BusinessError
    data class DuplicatePayment(val idempotencyKey: String) : BusinessError
    data class InvalidPayrollComponent(val componentType: String, val reason: String) : BusinessError
    data class NegativeNetPay(val payslipId: Long?, val amountRial: Long) : BusinessError
    data class AdvanceOverAllocation(
        val advanceId: Long,
        val requestedRial: Long,
        val outstandingRial: Long,
    ) : BusinessError
    data class SelfApprovalNotAllowed(val operation: String, val actorId: Long) : BusinessError
}

sealed interface BusinessResult<out T> {
    data class Success<T>(val value: T) : BusinessResult<T>
    data class Failure(val error: DomainFailure) : BusinessResult<Nothing>
}

/**
 * Adapter exception used only to force rollback across Room transaction boundaries.
 * Application/UI boundaries must inspect [error], not parse [message].
 */
open class BusinessRuleViolation(
    val error: DomainFailure,
    cause: Throwable? = null,
) : IllegalStateException(null, cause)

fun DomainFailure.asViolation(cause: Throwable? = null): BusinessRuleViolation =
    BusinessRuleViolation(this, cause)

inline fun businessRequire(condition: Boolean, error: () -> DomainFailure) {
    if (!condition) throw error().asViolation()
}
