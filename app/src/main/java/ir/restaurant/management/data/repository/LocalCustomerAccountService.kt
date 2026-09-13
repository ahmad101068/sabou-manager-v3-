package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerMergeHistoryEntity
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.crm.CustomerAccountPostingResult
import ir.restaurant.management.domain.crm.CustomerAccountService
import ir.restaurant.management.domain.crm.CustomerDuplicateCandidate
import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand
import ir.restaurant.management.domain.crm.CustomerReceivableAdjustmentCommand
import ir.restaurant.management.domain.crm.ReceivableAdjustmentDirection
import ir.restaurant.management.domain.crm.ReceivableAdjustmentEconomicNature
import ir.restaurant.management.domain.crm.ReceivableAging
import ir.restaurant.management.domain.crm.ReceivableLedgerRecord
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalCustomerAccountService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val clock: () -> Long = System::currentTimeMillis,
) : CustomerAccountService {
    private val audit = LocalAuditEventWriter(database)
    private val accounting = LocalAccountingPostingEngine(database, clock = clock)

    override fun observeLedger(customerId: Long): Flow<List<ReceivableLedgerRecord>> {
        require(customerId > 0)
        return database.customerReceivableDao().observeLedgerIncludingMerged(customerId).map { rows ->
            rows.map { ReceivableLedgerRecord(it.id, it.customerId, it.businessEpochDay, it.entryType, it.debitRial, it.creditRial, it.sourceType, it.sourceId, it.reference, it.dueEpochDay) }
        }
    }

    override suspend fun aging(customerId: Long, todayEpochDay: Long): ReceivableAging {
        authorizer.require(Permission.CUSTOMERS)
        require(customerId > 0 && todayEpochDay > 0)
        database.salesDao().customerById(customerId) ?: error("مشتری پیدا نشد.")
        val lots = CanonicalReceivableReadModel(database).openLotsForParty(customerId)
        var current=0L; var d1=0L; var d31=0L; var d61=0L; var d90=0L
        lots.forEach { lot ->
            val days = todayEpochDay - lot.dueEpochDay
            when {
                days <= 0 -> current = SignedLongMath.add(current, lot.outstandingRial)
                days <= 30 -> d1 = SignedLongMath.add(d1, lot.outstandingRial)
                days <= 60 -> d31 = SignedLongMath.add(d31, lot.outstandingRial)
                days <= 90 -> d61 = SignedLongMath.add(d61, lot.outstandingRial)
                else -> d90 = SignedLongMath.add(d90, lot.outstandingRial)
            }
        }
        return ReceivableAging(current,d1,d31,d61,d90)
    }

    override suspend fun duplicateCandidates(customerId: Long, phone: String, nationalId: String): List<CustomerDuplicateCandidate> {
        authorizer.require(Permission.CUSTOMERS)
        val normalizedPhone = phone.filter(Char::isDigit)
        val normalizedNational = nationalId.filter(Char::isDigit)
        if (normalizedPhone.isBlank() && normalizedNational.isBlank()) return emptyList()
        return database.salesDao().duplicateCustomerCandidates(normalizedPhone, normalizedNational, customerId).map {
            CustomerDuplicateCandidate(it.id, it.customerCode, it.name, it.phone, it.nationalId)
        }
    }

    override suspend fun postOpeningBalance(command: CustomerOpeningBalanceCommand): CustomerAccountPostingResult {
        val actor = authorizer.require(Permission.ACCOUNTING)
        authorizer.require(Permission.CUSTOMERS)
        val valid = command.validated()
        return database.withTransaction {
            val customer = database.salesDao().activeCustomerById(valid.customerId) ?: error("مشتری فعال پیدا نشد.")
            val dao = database.customerReceivableDao()
            val reference = valid.commandId
            dao.ledgerByReference("CRM_OPENING", reference)?.let { existing ->
                require(existing.customerId == valid.customerId && existing.entryType == "OPENING") { "بازپخش فرمان افتتاحیه با داده متفاوت مجاز نیست." }
                require(existing.debitRial == if (valid.direction == ReceivableAdjustmentDirection.DEBIT) valid.amountRial else 0L) { "مبلغ بازپخش افتتاحیه متفاوت است." }
                require(existing.creditRial == if (valid.direction == ReceivableAdjustmentDirection.CREDIT) valid.amountRial else 0L) { "مبلغ بازپخش افتتاحیه متفاوت است." }
                val journal = database.accountingDao().entryByIdempotencyKey("CRM_OPENING:${valid.commandId}")
                    ?: error("سند حسابداری متناظر افتتاحیه پیدا نشد.")
                return@withTransaction CustomerAccountPostingResult(existing.id, journal.id, true)
            }
            require(dao.ledger(valid.customerId).none { it.entryType == "OPENING" }) { "مانده افتتاحیه این مشتری قبلاً ثبت شده است." }
            val now = clock()
            val debit = if (valid.direction == ReceivableAdjustmentDirection.DEBIT) valid.amountRial else 0L
            val credit = if (valid.direction == ReceivableAdjustmentDirection.CREDIT) valid.amountRial else 0L
            val ledgerId = dao.insertLedger(
                CustomerReceivableLedgerEntity(
                    customerId = valid.customerId,
                    businessEpochDay = valid.businessEpochDay,
                    entryType = "OPENING",
                    debitRial = debit,
                    creditRial = credit,
                    sourceType = "CRM_OPENING",
                    sourceId = 0L,
                    reference = reference,
                    dueEpochDay = valid.dueEpochDay,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            val money = MoneyRial.of(valid.amountRial)
            val lines = if (valid.direction == ReceivableAdjustmentDirection.DEBIT) {
                listOf(
                    SemanticJournalLine(SemanticAccountRole.CUSTOMER_RECEIVABLE, debit = money, memo = customer.name),
                    SemanticJournalLine(SemanticAccountRole.OWNER_CAPITAL, credit = money, memo = valid.reason),
                )
            } else {
                listOf(
                    SemanticJournalLine(SemanticAccountRole.OWNER_CAPITAL, debit = money, memo = valid.reason),
                    SemanticJournalLine(SemanticAccountRole.CUSTOMER_RECEIVABLE, credit = money, memo = customer.name),
                )
            }
            val journal = accounting.post(
                AccountingPostingCommand(
                    entryNo = "افت-${valid.commandId}",
                    sourceType = "CRM_OPENING",
                    sourceId = ledgerId,
                    businessEpochDay = valid.businessEpochDay,
                    description = "مانده افتتاحیه ${customer.name}: ${valid.reason}",
                    accountingScope = AccountingScope.ORGANIZATION,
                    branchId = null,
                    lines = lines,
                    idempotencyKey = "CRM_OPENING:${valid.commandId}",
                    correlationId = CorrelationId.parse("crm-opening:${valid.commandId}"),
                    actorId = actor.id,
                ),
            )
            audit.appendAuthorized(
                authorizer, "OPENING", "CUSTOMER_RECEIVABLE", ledgerId,
                "مانده افتتاحیه ${customer.customerCode}", now, reason = valid.reason,
                afterSnapshot = "customerId=${customer.id};debitRial=$debit;creditRial=$credit;journalEntryId=${journal.entryId}",
                correlationId = "crm-opening:${valid.commandId}", referenceType = "CUSTOMER", referenceId = customer.id,
            )
            CustomerAccountPostingResult(ledgerId, journal.entryId, false)
        }
    }

    override suspend fun postAdjustment(command: CustomerReceivableAdjustmentCommand): CustomerAccountPostingResult {
        val actor = authorizer.require(Permission.ACCOUNTING)
        authorizer.require(Permission.CUSTOMERS)
        val valid = command.validated()
        return database.withTransaction {
            val customer = database.salesDao().activeCustomerById(valid.customerId) ?: error("مشتری فعال پیدا نشد.")
            val dao = database.customerReceivableDao()
            val reference = valid.commandId
            dao.ledgerByReference("CRM_ADJUSTMENT", reference)?.let { existing ->
                require(existing.customerId == valid.customerId && existing.entryType == "ADJUSTMENT") { "بازپخش فرمان تعدیل با داده متفاوت مجاز نیست." }
                require(existing.debitRial == if (valid.direction == ReceivableAdjustmentDirection.DEBIT) valid.amountRial else 0L) { "مبلغ بازپخش تعدیل متفاوت است." }
                require(existing.creditRial == if (valid.direction == ReceivableAdjustmentDirection.CREDIT) valid.amountRial else 0L) { "مبلغ بازپخش تعدیل متفاوت است." }
                val journal = database.accountingDao().entryByIdempotencyKey("CRM_ADJUSTMENT:${valid.commandId}")
                    ?: error("سند حسابداری متناظر تعدیل پیدا نشد.")
                return@withTransaction CustomerAccountPostingResult(existing.id, journal.id, true)
            }
            val now = clock()
            val debit = if (valid.direction == ReceivableAdjustmentDirection.DEBIT) valid.amountRial else 0L
            val credit = if (valid.direction == ReceivableAdjustmentDirection.CREDIT) valid.amountRial else 0L
            val ledgerId = dao.insertLedger(
                CustomerReceivableLedgerEntity(
                    customerId = valid.customerId,
                    businessEpochDay = valid.businessEpochDay,
                    entryType = "ADJUSTMENT",
                    debitRial = debit,
                    creditRial = credit,
                    sourceType = "CRM_ADJUSTMENT",
                    sourceId = 0L,
                    reference = reference,
                    dueEpochDay = valid.dueEpochDay,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            val money = MoneyRial.of(valid.amountRial)
            val counterpartRole = when (valid.economicNature) {
                ReceivableAdjustmentEconomicNature.SALES_CORRECTION -> SemanticAccountRole.SALES_REVENUE
                ReceivableAdjustmentEconomicNature.OTHER_INCOME -> SemanticAccountRole.OTHER_INCOME
                ReceivableAdjustmentEconomicNature.OPERATING_EXPENSE -> SemanticAccountRole.OTHER_OPERATING_EXPENSE
            }
            val lines = if (valid.direction == ReceivableAdjustmentDirection.DEBIT) {
                listOf(
                    SemanticJournalLine(SemanticAccountRole.CUSTOMER_RECEIVABLE, debit = money, memo = customer.name),
                    SemanticJournalLine(counterpartRole, credit = money, memo = valid.reason),
                )
            } else {
                listOf(
                    SemanticJournalLine(counterpartRole, debit = money, memo = valid.reason),
                    SemanticJournalLine(SemanticAccountRole.CUSTOMER_RECEIVABLE, credit = money, memo = customer.name),
                )
            }
            val journal = accounting.post(
                AccountingPostingCommand(
                    entryNo = "تعد-${valid.commandId}",
                    sourceType = "CRM_ADJUSTMENT",
                    sourceId = ledgerId,
                    businessEpochDay = valid.businessEpochDay,
                    description = "تعدیل حساب ${customer.name}: ${valid.reason}",
                    accountingScope = AccountingScope.ORGANIZATION,
                    branchId = null,
                    lines = lines,
                    idempotencyKey = "CRM_ADJUSTMENT:${valid.commandId}",
                    correlationId = CorrelationId.parse("crm-adjustment:${valid.commandId}"),
                    actorId = actor.id,
                ),
            )
            audit.appendAuthorized(
                authorizer, "ADJUST", "CUSTOMER_RECEIVABLE", ledgerId,
                "تعدیل حساب ${customer.customerCode}", now, reason = valid.reason,
                afterSnapshot = "customerId=${customer.id};debitRial=$debit;creditRial=$credit;journalEntryId=${journal.entryId}",
                correlationId = "crm-adjustment:${valid.commandId}", referenceType = "CUSTOMER", referenceId = customer.id,
            )
            CustomerAccountPostingResult(ledgerId, journal.entryId, false)
        }
    }

    override suspend fun merge(sourceCustomerId: Long, targetCustomerId: Long, reason: String): Long {
        val actor = authorizer.require(Permission.CUSTOMER_MERGE)
        val normalized = reason.trim()
        require(sourceCustomerId > 0 && targetCustomerId > 0 && sourceCustomerId != targetCustomerId) { "مشتری مبدا و مقصد ادغام معتبر نیست." }
        require(normalized.length in 3..300) { "دلیل ادغام مشتری الزامی است." }
        return database.withTransaction {
            val source = database.salesDao().activeCustomerById(sourceCustomerId) ?: error("مشتری مبدا فعال پیدا نشد.")
            val target = database.salesDao().activeCustomerById(targetCustomerId) ?: error("مشتری مقصد فعال پیدا نشد.")
            val beforeSourceBalance = database.customerReceivableDao().balanceRial(source.id)
            val beforeTargetBalance = database.customerReceivableDao().balanceRial(target.id)
            val crm = database.customerReceivableDao()
            val expectedMergedBalance = SignedLongMath.add(beforeSourceBalance, beforeTargetBalance)
            val now = clock()
            check(database.salesDao().markCustomerMerged(source.id, now) == 1) { "غیرفعال‌سازی مشتری ادغام‌شده انجام نشد." }
            val mergeId = crm.insertMerge(
                CustomerMergeHistoryEntity(
                    sourceCustomerId = source.id,
                    targetCustomerId = target.id,
                    reason = normalized,
                    actorId = actor.id,
                    mergedAtEpochMillis = now,
                ),
            )
            audit.appendAuthorized(
                authorizer, "MERGE", "CUSTOMER", target.id,
                "ادغام ${source.customerCode} در ${target.customerCode}", now, reason = normalized,
                beforeSnapshot = "sourceBalance=$beforeSourceBalance;targetBalance=$beforeTargetBalance",
                afterSnapshot = "sourceStatus=MERGED;logicalMergedBalance=$expectedMergedBalance;historicalRowsRewritten=false;mergeId=$mergeId",
                correlationId = "customer_merge:$mergeId", referenceType = "CUSTOMER", referenceId = source.id,
            )
            mergeId
        }
    }
}
