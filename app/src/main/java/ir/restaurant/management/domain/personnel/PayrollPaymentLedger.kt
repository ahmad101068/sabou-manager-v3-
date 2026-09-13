package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

/**
 * Pure projection for the append-only payment ledger. Reversal rows compensate an original row;
 * they never become negative payments and never mutate the approved net amount.
 */
data class PayrollPaymentLedgerEntry(
    val id: Long,
    val amountRial: Long,
    val status: PayrollPaymentStatus,
    val reversalOfPaymentId: Long?,
)

data class PayrollPaymentProjection(
    val paidAmountRial: Long,
    val remainingAmountRial: Long,
    val status: PayrollPayslipStatus,
)

object PayrollPaymentLedger {
    fun derive(
        netPayRial: Long,
        entries: List<PayrollPaymentLedgerEntry>,
    ): PayrollPaymentProjection {
        MoneyRial.of(netPayRial)
        require(entries.all { it.id > 0 && it.amountRial > 0 }) { "payroll_payment_ledger_entry_invalid" }
        require(entries.map { it.id }.distinct().size == entries.size) { "payroll_payment_ledger_duplicate_id" }
        require(entries.none { it.status == PayrollPaymentStatus.LEGACY_UNKNOWN }) {
            "payroll_payment_ledger_status_unknown"
        }
        entries.forEach { MoneyRial.of(it.amountRial) }

        val byId = entries.associateBy(PayrollPaymentLedgerEntry::id)
        val reversals = entries.filter { it.reversalOfPaymentId != null }
        require(reversals.mapNotNull { it.reversalOfPaymentId }.distinct().size == reversals.size) {
            "payroll_payment_duplicate_reversal"
        }
        reversals.forEach { reversal ->
            val original = byId[reversal.reversalOfPaymentId]
                ?: error("payroll_payment_reversal_original_missing")
            require(original.reversalOfPaymentId == null) { "payroll_payment_reversal_chain_invalid" }
            require(original.status == PayrollPaymentStatus.REVERSED) { "payroll_payment_reversal_original_not_reversed" }
            require(reversal.status == PayrollPaymentStatus.REVERSED) { "payroll_payment_reversal_status_invalid" }
            require(reversal.amountRial == original.amountRial) { "payroll_payment_reversal_amount_mismatch" }
        }
        entries.filter { it.reversalOfPaymentId == null && it.status == PayrollPaymentStatus.REVERSED }
            .forEach { original ->
                require(reversals.any { it.reversalOfPaymentId == original.id }) {
                    "payroll_payment_reversal_record_missing"
                }
            }

        val paid = entries
            .asSequence()
            .filter { it.reversalOfPaymentId == null && it.status == PayrollPaymentStatus.POSTED }
            .fold(0L) { total, payment -> SignedLongMath.add(total, payment.amountRial) }
        if (paid > netPayRial) {
            throw BusinessError.InvalidInput("amountRial", "payroll_overpayment").asViolation()
        }
        val remaining = SignedLongMath.subtract(netPayRial, paid)
        val status = when {
            remaining == 0L -> PayrollPayslipStatus.PAID
            paid == 0L -> PayrollPayslipStatus.PAYMENT_PENDING
            else -> PayrollPayslipStatus.PARTIALLY_PAID
        }
        return PayrollPaymentProjection(paid, remaining, status)
    }
}
