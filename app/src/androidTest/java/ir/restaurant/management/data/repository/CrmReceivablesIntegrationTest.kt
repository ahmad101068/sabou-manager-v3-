package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.SalesInvoiceEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand
import ir.restaurant.management.domain.crm.CustomerReceivableAdjustmentCommand
import ir.restaurant.management.domain.crm.ReceivableAdjustmentDirection
import ir.restaurant.management.domain.crm.ReceivableAdjustmentEconomicNature
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrmReceivablesIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var service: LocalCustomerAccountService
    private var now = 80_000L
    private var actorId = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("crm-owner", "مالک CRM", "123456", UserRole.OWNER, "87654321"),
        )
        actorId = authorizer.actorIdentity().id
        service = LocalCustomerAccountService(database, authorizer, clock = { now++ })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aging_appliesCreditsFifoAcrossRequiredBuckets() = runBlocking {
        val customerId = insertCustomer("CUS-AGING", "مشتری سررسید")
        val today = 30_000L
        insertLedger(customerId, today - 120, today - 100, 10_000L, 0L, "SALE", 1)
        insertLedger(customerId, today - 80, today - 70, 20_000L, 0L, "SALE", 2)
        insertLedger(customerId, today - 50, today - 40, 30_000L, 0L, "SALE", 3)
        insertLedger(customerId, today - 20, today - 10, 40_000L, 0L, "SALE", 4)
        insertLedger(customerId, today, today + 10, 50_000L, 0L, "SALE", 5)
        insertLedger(customerId, today, null, 0L, 25_000L, "RECEIPT", 6)

        val aging = service.aging(customerId, today)

        // FIFO credit fully settles +90 bucket (10k) and 15k of 61-90 bucket.
        assertEquals(0L, aging.over90Rial)
        assertEquals(5_000L, aging.days61To90Rial)
        assertEquals(30_000L, aging.days31To60Rial)
        assertEquals(40_000L, aging.days1To30Rial)
        assertEquals(50_000L, aging.currentRial)
        assertEquals(125_000L, aging.totalRial)
    }

    @Test
    fun duplicateCandidates_matchesNormalizedPhoneOrNationalId_withoutReturningSelf() = runBlocking {
        val sourceId = insertCustomer("CUS-DUP-1", "مشتری یک", phone = "09121234567", nationalId = "0012345678")
        val phoneMatch = insertCustomer("CUS-DUP-2", "مشتری دو", phone = "09121234567")
        val nationalMatch = insertCustomer("CUS-DUP-3", "مشتری سه", nationalId = "0012345678")

        val candidates = service.duplicateCandidates(sourceId, "0912 123 4567", "001-234-5678")

        assertEquals(setOf(phoneMatch, nationalMatch), candidates.map { it.id }.toSet())
        assertFalse(candidates.any { it.id == sourceId })
    }

    @Test
    fun merge_preservesPostedFinancialHistory_andExposesLogicalCombinedLedger() = runBlocking {
        val sourceId = insertCustomer("CUS-MERGE-S", "مشتری مبدا")
        val targetId = insertCustomer("CUS-MERGE-T", "مشتری مقصد")
        insertLedger(sourceId, 31_000L, 31_010L, 70_000L, 0L, "SALE", 10)
        insertLedger(sourceId, 31_005L, null, 0L, 20_000L, "RECEIPT", 11)
        insertLedger(targetId, 31_000L, 31_020L, 40_000L, 0L, "SALE", 12)
        val postedInvoiceId = database.salesDao().insertInvoice(
            SalesInvoiceEntity(
                invoiceNo = "MERGE-HISTORY-1", commandId = "merge-history-command-1", businessEpochDay = 31_000L,
                customerId = sourceId, dueEpochDay = 31_010L, grossRial = 70_000L, discountRial = 0L,
                serviceRial = 0L, taxRial = 0L, netRial = 70_000L, creditRial = 70_000L,
                theoreticalCostRial = 0L, journalEntryId = null, cogsJournalEntryId = null, status = "POSTED",
                notes = "immutable merge history", createdByActorId = actorId, createdAtEpochMillis = now++,
            ),
        )
        val sourceBefore = database.customerReceivableDao().balanceRial(sourceId)
        val targetBefore = database.customerReceivableDao().balanceRial(targetId)
        val sourceLedgerIdsBefore = database.customerReceivableDao().ledger(sourceId).map { it.id }

        val mergeId = service.merge(sourceId, targetId, "حذف رکورد تکراری مشتری")

        val source = requireNotNull(database.salesDao().customerById(sourceId))
        assertFalse(source.isActive)
        assertEquals("MERGED", source.status)
        // Historical rows stay owned by their original party; merge is an alias/read-model relation.
        assertEquals(sourceBefore, database.customerReceivableDao().balanceRial(sourceId))
        assertEquals(targetBefore, database.customerReceivableDao().balanceRial(targetId))
        assertEquals(sourceId, requireNotNull(database.salesDao().invoiceById(postedInvoiceId)).customerId)
        assertEquals(sourceLedgerIdsBefore, database.customerReceivableDao().ledger(sourceId).map { it.id })
        assertEquals(0L, scalar("SELECT COUNT(*) FROM customer_receivable_ledger WHERE customerId=$targetId AND id IN (${sourceLedgerIdsBefore.joinToString()})"))
        val logicalLedger = service.observeLedger(targetId).first()
        assertEquals(3, logicalLedger.size)
        assertEquals(sourceBefore + targetBefore, logicalLedger.sumOf { it.debitRial - it.creditRial })
        assertEquals(1L, scalar("SELECT COUNT(*) FROM customer_merge_history WHERE id=$mergeId AND sourceCustomerId=$sourceId AND targetCustomerId=$targetId"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='CUSTOMER' AND entityId=$targetId AND action='MERGE'"))
    }


    @Test
    fun openingBalance_postsLedgerBalancedAccountingAudit_andReplaysIdempotently() = runBlocking {
        val customerId = insertCustomer("CUS-OPEN", "مشتری مانده افتتاحیه")
        val command = CustomerOpeningBalanceCommand(
            customerId = customerId,
            businessEpochDay = 32_000L,
            amountRial = 90_000L,
            direction = ReceivableAdjustmentDirection.DEBIT,
            dueEpochDay = 32_020L,
            reason = "مانده افتتاحیه تایید شده",
            commandId = "crm-opening-test-1",
        )

        val first = service.postOpeningBalance(command)
        val replay = service.postOpeningBalance(command)

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.ledgerId, replay.ledgerId)
        assertEquals(first.journalEntryId, replay.journalEntryId)
        val ledger = requireNotNull(database.customerReceivableDao().ledgerByReference("CRM_OPENING", command.commandId))
        assertEquals("OPENING", ledger.entryType)
        assertEquals(90_000L, ledger.debitRial)
        assertEquals(0L, ledger.creditRial)
        assertEquals(32_020L, ledger.dueEpochDay)
        assertEquals(90_000L, database.customerReceivableDao().balanceRial(customerId))
        assertEquals(
            scalar("SELECT COALESCE(SUM(debitRial),0) FROM journal_lines WHERE entryId=${first.journalEntryId}"),
            scalar("SELECT COALESCE(SUM(creditRial),0) FROM journal_lines WHERE entryId=${first.journalEntryId}"),
        )
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='OPENING' AND entityType='CUSTOMER_RECEIVABLE' AND entityId=${first.ledgerId} AND reason='مانده افتتاحیه تایید شده'"))
    }

    @Test
    fun adjustment_debitAndCredit_createRealLedgerAccountingAndAudit() = runBlocking {
        val customerId = insertCustomer("CUS-ADJ", "مشتری تعدیل")
        val debit = service.postAdjustment(
            CustomerReceivableAdjustmentCommand(
                customerId = customerId,
                businessEpochDay = 33_000L,
                amountRial = 40_000L,
                direction = ReceivableAdjustmentDirection.DEBIT,
                economicNature = ReceivableAdjustmentEconomicNature.SALES_CORRECTION,
                dueEpochDay = 33_010L,
                reason = "اصلاح بدهکار تایید شده",
                commandId = "crm-adjustment-debit-1",
            ),
        )
        val credit = service.postAdjustment(
            CustomerReceivableAdjustmentCommand(
                customerId = customerId,
                businessEpochDay = 33_001L,
                amountRial = 15_000L,
                direction = ReceivableAdjustmentDirection.CREDIT,
                economicNature = ReceivableAdjustmentEconomicNature.SALES_CORRECTION,
                reason = "اصلاح بستانکار تایید شده",
                commandId = "crm-adjustment-credit-1",
            ),
        )

        assertEquals(25_000L, database.customerReceivableDao().balanceRial(customerId))
        val rows = database.customerReceivableDao().ledger(customerId).filter { it.entryType == "ADJUSTMENT" }
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.debitRial == 40_000L && it.creditRial == 0L })
        assertTrue(rows.any { it.debitRial == 0L && it.creditRial == 15_000L })
        for (journalId in listOf(debit.journalEntryId, credit.journalEntryId)) {
            assertEquals(
                scalar("SELECT COALESCE(SUM(debitRial),0) FROM journal_lines WHERE entryId=$journalId"),
                scalar("SELECT COALESCE(SUM(creditRial),0) FROM journal_lines WHERE entryId=$journalId"),
            )
        }
        assertEquals(2L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='ADJUST' AND entityType='CUSTOMER_RECEIVABLE'"))
    }

    private suspend fun insertCustomer(code: String, name: String, phone: String = "", nationalId: String = ""): Long =
        database.salesDao().insertCustomer(
            CustomerEntity(
                customerCode = code,
                name = name,
                phone = phone.filter(Char::isDigit),
                nationalId = nationalId.filter(Char::isDigit),
                creditLimitRial = 1_000_000L,
                notes = "",
                createdAtEpochMillis = now++,
                updatedAtEpochMillis = now++,
            ),
        )

    private suspend fun insertLedger(
        customerId: Long,
        day: Long,
        due: Long?,
        debit: Long,
        credit: Long,
        type: String,
        sourceId: Long,
    ) {
        database.customerReceivableDao().insertLedger(
            CustomerReceivableLedgerEntity(
                customerId = customerId,
                businessEpochDay = day,
                entryType = type,
                debitRial = debit,
                creditRial = credit,
                sourceType = type,
                sourceId = sourceId,
                dueEpochDay = due,
                actorId = actorId,
                createdAtEpochMillis = now++,
            ),
        )
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
