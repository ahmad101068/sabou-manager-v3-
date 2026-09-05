package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.common.asViolation

/** Converts only known invariant tokens; unknown technical failures retain their original type. */
internal fun mapDatabaseBusinessFailure(error: Throwable, businessEpochDay: Long): Throwable {
    if (error is BusinessRuleViolation) return error
    val diagnostic = generateSequence(error) { it.cause }
        .take(8)
        .joinToString(" | ") { it.message.orEmpty() }
    return when {
        "ACCOUNTING_PERIOD_CLOSED" in diagnostic ->
            BusinessError.ClosedAccountingPeriod(businessEpochDay).asViolation(error)
        "INVENTORY_PERIOD_CLOSED" in diagnostic ->
            BusinessError.ClosedInventoryPeriod(businessEpochDay).asViolation(error)
        "SALES_DAY_CLOSED" in diagnostic ->
            BusinessError.ClosedSalesDay(businessEpochDay).asViolation(error)
        else -> error
    }
}
