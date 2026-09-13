package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.HrPayrollCommandReceiptEntity
import ir.restaurant.management.data.db.PayrollApprovalEventEntity
import ir.restaurant.management.data.db.PayrollPaymentEntity
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.audit.AuditService
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.personnel.PayPayslipCommand
import ir.restaurant.management.domain.personnel.PayrollBatchStateMachine
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollPaymentLedger
import ir.restaurant.management.domain.personnel.PayrollPaymentLedgerEntry
import ir.restaurant.management.domain.personnel.PayrollPaymentStatus
import ir.restaurant.management.domain.personnel.PayrollPayslipStateMachine
import ir.restaurant.management.domain.personnel.PayrollPayslipStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.personnel.ReversePayrollPaymentCommand
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import java.security.MessageDigest

/**
 * Owns the payroll payment/reversal responsibility. Calculation, approval and period lifecycle stay
 * in [LocalHrPayrollService]; this service is the single boundary for Treasury + payroll-ledger
 * mutation so payment retries and reversals remain atomic and independently testable.
 */
internal class PayrollPaymentPostingService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val treasury: TreasuryService,
    private val audit: AuditService = LocalAuditEventWriter(database),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val hr = database.hrPayrollDao()

    suspend fun pay(command: PayPayslipCommand): Long {
        val actor = authorizer.require(Permission.PAYROLL_PAY)
        val valid = command.validated()
        val key = receiptKey("PAY_PAYSLIP", valid.commandId)
        val payload = hash("${valid.payslipId}|${valid.amountRial}|${valid.treasuryAccountId}|${valid.channel}|${valid.paymentEpochDay}|${valid.paymentReference}")
        val correlation = correlation("payroll_payment", valid.commandId)
        return database.withTransaction {
            hr.payrollPaymentByKey(key)?.let { existing ->
                val existingPayload = hash("${existing.payslipId}|${existing.amountRial}|${existing.treasuryAccountId}|${existing.channel}|${existing.paymentEpochDay}|${existing.paymentReference}")
                if (existingPayload != payload) throw BusinessError.IdempotencyConflict(key).asViolation()
                return@withTransaction existing.id
            }
            val payslip = hr.payrollPayslip(valid.payslipId) ?: missing("PAYROLL_PAYSLIP", valid.payslipId)
            val currentStatus = PayrollPayslipStatus.fromStoredValue(payslip.status)
            if (currentStatus !in setOf(PayrollPayslipStatus.PAYMENT_PENDING, PayrollPayslipStatus.PARTIALLY_PAID)) {
                if (currentStatus == PayrollPayslipStatus.PAID) throw BusinessError.PayrollAlreadyPaid(payslip.id).asViolation()
                throw BusinessError.InvalidBusinessState("PAYROLL_PAYSLIP", payslip.status).asViolation()
            }
            val period = hr.payrollPeriod(payslip.periodId) ?: missing("PAYROLL_PERIOD", payslip.periodId)
            if (PayrollPeriodStatus.fromStoredValue(period.status) == PayrollPeriodStatus.CLOSED) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }
            if (valid.amountRial > payslip.remainingAmountRial) {
                throw BusinessError.InvalidInput("amountRial", "payroll_overpayment").asViolation()
            }
            val batch = hr.payrollBatch(payslip.batchId) ?: missing("PAYROLL_BATCH", payslip.batchId)
            val treasuryResult = treasury.execute(
                TreasuryCommand.Payment(
                    commandId = GlobalId.parse(valid.commandId),
                    businessEpochDay = valid.paymentEpochDay,
                    correlationId = correlation,
                    businessIntent = TreasuryBusinessIntent.PAYROLL_PAYMENT,
                    sourceId = payslip.id,
                    reason = "پرداخت حقوق ${valid.paymentReference}",
                    accountingScope = if (batch.branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION,
                    branchId = batch.branchId,
                    accountId = TreasuryAccountId.parse(valid.treasuryAccountId),
                    channel = valid.channel,
                    amount = MoneyRial.of(valid.amountRial),
                ),
            )
            val now = clock()
            val paymentId = hr.insertPayrollPayment(
                PayrollPaymentEntity(
                    globalId = valid.commandId,
                    idempotencyKey = key,
                    payslipId = payslip.id,
                    amountRial = valid.amountRial,
                    treasuryAccountId = valid.treasuryAccountId,
                    channel = valid.channel.storedValue,
                    paymentEpochDay = valid.paymentEpochDay,
                    paymentReference = valid.paymentReference,
                    status = PayrollPaymentStatus.POSTED.storedValue,
                    treasuryTransactionId = treasuryResult.id,
                    journalEntryId = treasuryResult.journalEntryId,
                    reversalOfPaymentId = null,
                    createdByActorId = actor.id,
                    createdAtEpochMillis = now,
                    reversedAtEpochMillis = null,
                    reversalReason = null,
                    correlationId = correlation.value,
                ),
            )
            val projection = paymentProjection(payslip.id, payslip.netPayRial)
            val target = projection.status
            PayrollPayslipStateMachine.requireTransition(currentStatus, target)
            optimistic(
                hr.updatePayslipPaymentProjection(
                    payslip.id,
                    projection.paidAmountRial,
                    projection.remainingAmountRial,
                    target.storedValue,
                    now.takeIf { projection.remainingAmountRial == 0L },
                    payslip.rowVersion,
                ),
                "PAYROLL_PAYSLIP",
                payslip.id,
            )
            refreshBatchPaymentStatus(payslip.batchId)
            hr.insertApprovalEvent(approvalEvent(payslip.batchId, payslip.id, "PAYMENT", currentStatus.storedValue, target.storedValue, actor.id, valid.paymentReference, now, correlation.value))
            recordAudit(actor.id, actor.displayName, "PAY", "PAYROLL_PAYMENT", paymentId, valid.paymentEpochDay, valid.paymentReference, "ثبت پرداخت حقوق", correlation.value, null, "status=POSTED;payslipId=${payslip.id}")
            receipt(key, "PAY_PAYSLIP", payload, "PAYROLL_PAYMENT", paymentId, target.storedValue, actor.id, now, correlation.value)
            paymentId
        }
    }

    suspend fun reverse(command: ReversePayrollPaymentCommand): Long {
        val actor = authorizer.require(Permission.PAYROLL_REVERSE)
        val valid = command.validated()
        val key = receiptKey("REVERSE_PAYMENT", valid.commandId)
        val payload = hash("${valid.paymentId}|${valid.reversalEpochDay}|${valid.reason}")
        val correlation = correlation("payroll_payment_reverse", valid.commandId)
        return database.withTransaction {
            replay(key, "REVERSE_PAYMENT", payload)?.let { return@withTransaction it.resultEntityId }
            val payment = hr.payrollPayment(valid.paymentId) ?: missing("PAYROLL_PAYMENT", valid.paymentId)
            require(payment.status == PayrollPaymentStatus.POSTED.storedValue && payment.reversalOfPaymentId == null) { "payroll_payment_not_reversible" }
            val payslip = hr.payrollPayslip(payment.payslipId) ?: missing("PAYROLL_PAYSLIP", payment.payslipId)
            val period = hr.payrollPeriod(payslip.periodId) ?: missing("PAYROLL_PERIOD", payslip.periodId)
            if (PayrollPeriodStatus.fromStoredValue(period.status) == PayrollPeriodStatus.CLOSED) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }
            val originalJournal = payment.journalEntryId ?: throw BusinessError.InvalidBusinessState("PAYROLL_PAYMENT", "MISSING_JOURNAL").asViolation()
            val treasuryResult = treasury.reverse(
                TreasuryReversalCommand(
                    commandId = GlobalId.parse(valid.commandId),
                    originalTransactionId = payment.treasuryTransactionId,
                    originalJournalEntryId = originalJournal,
                    businessEpochDay = valid.reversalEpochDay,
                    correlationId = correlation,
                    sourceType = "PAYROLL_PAYMENT_REVERSAL",
                    sourceId = payment.id,
                    reason = valid.reason,
                    accountId = TreasuryAccountId.parse(payment.treasuryAccountId),
                    channel = ir.restaurant.management.domain.treasury.TreasuryChannel.fromStoredValue(payment.channel),
                    amount = MoneyRial.of(payment.amountRial),
                ),
            )
            val now = clock()
            optimistic(hr.markPaymentReversed(payment.id, now, valid.reason), "PAYROLL_PAYMENT", payment.id)
            val reversalId = hr.insertPayrollPayment(
                payment.copy(
                    id = 0,
                    globalId = valid.commandId,
                    idempotencyKey = key,
                    paymentEpochDay = valid.reversalEpochDay,
                    paymentReference = "REV:${payment.paymentReference}",
                    status = PayrollPaymentStatus.REVERSED.storedValue,
                    treasuryTransactionId = treasuryResult.id,
                    journalEntryId = treasuryResult.journalEntryId,
                    reversalOfPaymentId = payment.id,
                    createdByActorId = actor.id,
                    createdAtEpochMillis = now,
                    reversedAtEpochMillis = now,
                    reversalReason = valid.reason,
                    correlationId = correlation.value,
                ),
            )
            val refreshed = hr.payrollPayslip(payslip.id) ?: missing("PAYROLL_PAYSLIP", payslip.id)
            val projection = paymentProjection(payslip.id, refreshed.netPayRial)
            val target = projection.status
            val from = PayrollPayslipStatus.fromStoredValue(refreshed.status)
            PayrollPayslipStateMachine.requireTransition(from, target)
            optimistic(
                hr.updatePayslipPaymentProjection(refreshed.id, projection.paidAmountRial, projection.remainingAmountRial, target.storedValue, null, refreshed.rowVersion),
                "PAYROLL_PAYSLIP",
                refreshed.id,
            )
            refreshBatchPaymentStatus(refreshed.batchId)
            hr.insertApprovalEvent(approvalEvent(refreshed.batchId, refreshed.id, "PAYMENT_REVERSAL", from.storedValue, target.storedValue, actor.id, valid.reason, now, correlation.value))
            recordAudit(actor.id, actor.displayName, "REVERSE", "PAYROLL_PAYMENT", reversalId, valid.reversalEpochDay, valid.reason, "برگشت پرداخت حقوق", correlation.value, "paymentId=${payment.id};status=POSTED", "paymentId=$reversalId;status=REVERSED")
            receipt(key, "REVERSE_PAYMENT", payload, "PAYROLL_PAYMENT", reversalId, "REVERSED", actor.id, now, correlation.value)
            reversalId
        }
    }

    private suspend fun refreshBatchPaymentStatus(batchId: Long) {
        val batch = hr.payrollBatch(batchId) ?: missing("PAYROLL_BATCH", batchId)
        val active = hr.batchPayslips(batchId).filter { PayrollPayslipStatus.fromStoredValue(it.status) != PayrollPayslipStatus.REVERSED }
        val target = when {
            active.isEmpty() -> PayrollBatchStatus.REVERSED
            active.all { it.remainingAmountRial == 0L } -> PayrollBatchStatus.PAID
            active.any { it.paidAmountRial > 0L } -> PayrollBatchStatus.PARTIALLY_PAID
            else -> PayrollBatchStatus.PAYMENT_PENDING
        }
        val from = PayrollBatchStatus.fromStoredValue(batch.status)
        if (from == target) return
        PayrollBatchStateMachine.requireTransition(from, target)
        optimistic(hr.transitionPayrollBatch(batch.id, from.storedValue, target.storedValue, batch.rowVersion), "PAYROLL_BATCH", batch.id)
    }

    private suspend fun paymentProjection(payslipId: Long, netPayRial: Long) = PayrollPaymentLedger.derive(
        netPayRial = netPayRial,
        entries = hr.payrollPayments(payslipId).map { payment ->
            PayrollPaymentLedgerEntry(payment.id, payment.amountRial, PayrollPaymentStatus.fromStoredValue(payment.status), payment.reversalOfPaymentId)
        },
    )

    private suspend fun replay(key: String, commandType: String, payloadHash: String): HrPayrollCommandReceiptEntity? =
        hr.commandReceipt(key)?.also { receipt ->
            if (receipt.commandType != commandType || receipt.payloadHash != payloadHash) throw BusinessError.IdempotencyConflict(key).asViolation()
        }

    private suspend fun receipt(
        key: String,
        commandType: String,
        payloadHash: String,
        resultEntityType: String,
        resultEntityId: Long,
        resultDetail: String,
        actorId: Long,
        now: Long,
        correlationId: String,
    ) {
        hr.insertCommandReceipt(
            HrPayrollCommandReceiptEntity(
                idempotencyKey = key,
                commandType = commandType,
                payloadHash = payloadHash,
                resultEntityType = resultEntityType,
                resultEntityId = resultEntityId,
                resultDetail = resultDetail.take(4_000),
                actorId = actorId,
                createdAtEpochMillis = now,
                correlationId = correlationId,
            ),
        )
    }

    private fun approvalEvent(
        batchId: Long,
        payslipId: Long?,
        eventType: String,
        fromStatus: String,
        toStatus: String,
        actorId: Long,
        reason: String,
        now: Long,
        correlationId: String,
    ) = PayrollApprovalEventEntity(batchId = batchId, payslipId = payslipId, eventType = eventType, fromStatus = fromStatus, toStatus = toStatus, actorId = actorId, reason = reason, snapshotHash = null, createdAtEpochMillis = now, correlationId = correlationId)

    private suspend fun recordAudit(
        actorId: Long,
        actorName: String,
        action: String,
        entityType: String,
        entityId: Long,
        businessEpochDay: Long?,
        reason: String,
        description: String,
        correlationId: String,
        before: String?,
        after: String?,
    ) {
        audit.record(
            AuditEventDraft(
                action = AuditAction.of(action), entityType = AuditEntityType.of(entityType), entityId = entityId,
                actorId = actorId, actorDisplayName = actorName, occurredAtEpochMillis = clock(), businessEpochDay = businessEpochDay,
                deviceId = "local-android", referenceType = entityType, referenceId = entityId, reason = reason,
                beforeSnapshot = before, afterSnapshot = after, correlationId = correlationId, description = description,
            ),
        )
    }

    private fun optimistic(result: Int, entityType: String, entityId: Long) {
        if (result != 1) throw BusinessError.ConcurrentModification(entityType, entityId).asViolation()
    }

    private fun missing(entityType: String, id: Long): Nothing = throw BusinessError.EntityNotFound(entityType, id).asViolation()
    private fun receiptKey(type: String, commandId: String): String = "HRPAY:$type:$commandId"
    private fun correlation(operation: String, commandId: String): CorrelationId = CorrelationId.forCommand(operation, GlobalId.parse(commandId))
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
}
