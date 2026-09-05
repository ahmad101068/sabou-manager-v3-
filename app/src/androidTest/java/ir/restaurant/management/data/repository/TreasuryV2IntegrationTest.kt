package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.application.treasury.ReverseTreasuryTransactionUseCase
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasuryV2IntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var service: LocalTreasuryServiceV2
    private val cash = TreasuryAccountId.parse("cash_main")
    private val bank = TreasuryAccountId.parse("bank_main")
    private val card = TreasuryAccountId.parse("card_terminal")
    private val pettyCash = TreasuryAccountId.parse("petty_cash")
    private var now = 10_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("treasury-owner", "مالک خزانه", "123456", UserRole.OWNER, "87654321"),
        )
        service = LocalTreasuryServiceV2(
            database = database,
            accounting = LocalAccountingPostingEngine(database, clock = { now }),
            authorizer = authorizer,
            accountCatalog = DefaultTreasuryAccountCatalog(),
            clock = { now++ },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun receiptReplay_isIdempotentAcrossTreasuryLedgerAndAccounting() = runBlocking {
        val commandId = GlobalId.new()
        val command = TreasuryCommand.Receipt(
            commandId = commandId,
            businessEpochDay = 20_000L,
            correlationId = CorrelationId.forCommand("treasury_receipt", commandId),
            businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
            sourceId = 71L,
            reason = "دریافت تست یکپارچه",
            accountId = cash,
            channel = TreasuryChannel.CASH,
            amount = MoneyRial.of(25_000L),
        )

        val first = service.execute(command)
        val replay = service.execute(command)

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.id, replay.id)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE commandId='${commandId.value}'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${commandId.value}'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='OTHER_INCOME' AND sourceId=71"))
        assertEquals(25_000L, service.observeBalance(cash).first())
    }

    @Test
    fun receiptReplay_sameCommandIdWithDifferentAccount_failsIdempotencyConflict() = runBlocking {
        val commandId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = commandId,
                businessEpochDay = 20_001L,
                correlationId = CorrelationId.forCommand("treasury_replay_payload", commandId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 701L,
                reason = "دریافت برای آزمون تعارض بازپخش",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(8_000L),
            ),
        )

        try {
            service.execute(
                TreasuryCommand.Receipt(
                    commandId = commandId,
                    businessEpochDay = 20_001L,
                    correlationId = CorrelationId.forCommand("treasury_replay_payload", commandId),
                    businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                    sourceId = 701L,
                    reason = "دریافت برای آزمون تعارض بازپخش",
                    accountId = bank,
                    channel = TreasuryChannel.BANK,
                    amount = MoneyRial.of(8_000L),
                ),
            )
            fail("بازپخش همان commandId با حساب متفاوت باید رد شود")
        } catch (_: BusinessRuleViolation) {
            Unit
        }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE commandId='${commandId.value}'"))
        assertEquals(8_000L, service.observeBalance(cash).first())
        assertEquals(0L, service.observeBalance(bank).first())
    }

    @Test
    fun moduleOwnedGenericReceiptAndUnknownPayment_failClosedBeforePosting() = runBlocking {
        val beforeJournals = scalar("SELECT COUNT(*) FROM journal_entries")
        val beforeTransactions = scalar("SELECT COUNT(*) FROM treasury_transactions")

        try {
            TreasuryBusinessIntent.fromExternalSource("CUSTOMER_RECEIVABLE", TreasuryDirection.RECEIPT)
            fail("دریافت مستقیم دریافتنی باید فقط از مرز canonical AR مجاز باشد")
        } catch (_: BusinessRuleViolation) {
            Unit
        }
        try {
            TreasuryBusinessIntent.fromExternalSource("UNKNOWN_PAYMENT", TreasuryDirection.PAYMENT)
            fail("پرداخت با ماهیت اقتصادی نامشخص باید fail-closed باشد")
        } catch (_: BusinessRuleViolation) {
            Unit
        }

        assertEquals(beforeJournals, scalar("SELECT COUNT(*) FROM journal_entries"))
        assertEquals(beforeTransactions, scalar("SELECT COUNT(*) FROM treasury_transactions"))
    }

    @Test
    fun internalTransfer_preservesCombinedTreasuryBalanceAndPostsBalancedJournal() = runBlocking {
        val seedId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = seedId,
                businessEpochDay = 20_002L,
                correlationId = CorrelationId.forCommand("treasury_seed", seedId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 72L,
                reason = "تأمین موجودی اولیه صندوق",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(40_000L),
            ),
        )
        val beforeCash = service.observeBalance(cash).first()
        val beforeBank = service.observeBalance(bank).first()
        val commandId = GlobalId.new()

        val transfer = service.execute(
            TreasuryCommand.InternalTransfer(
                commandId = commandId,
                businessEpochDay = 20_003L,
                correlationId = CorrelationId.forCommand("treasury_transfer", commandId),
                                sourceId = 73L,
                reason = "انتقال صندوق به بانک",
                fromAccountId = cash,
                toAccountId = bank,
                amount = MoneyRial.of(15_000L),
            ),
        )

        val afterCash = service.observeBalance(cash).first()
        val afterBank = service.observeBalance(bank).first()
        assertEquals(beforeCash - 15_000L, afterCash)
        assertEquals(beforeBank + 15_000L, afterBank)
        assertEquals(beforeCash + beforeBank, afterCash + afterBank)
        assertNotNull(transfer.journalEntryId)
        val journalId = requireNotNull(transfer.journalEntryId)
        assertEquals(
            scalar("SELECT SUM(debitRial) FROM journal_lines WHERE entryId=$journalId"),
            scalar("SELECT SUM(creditRial) FROM journal_lines WHERE entryId=$journalId"),
        )
        assertEquals(2L, scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${commandId.value}'"))
    }

    @Test
    fun reconciliation_persistsDifferenceAndBalancedJournal_zeroDifferenceNeedsNoJournal() = runBlocking {
        val shortageId = GlobalId.new()
        val shortage = service.execute(
            TreasuryCommand.Reconciliation(
                commandId = shortageId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("cash_reconciliation", shortageId),
                                sourceId = 75L,
                reason = "کسری شمارش صندوق",
                accountId = cash,
                expected = MoneyRial.of(50_000L),
                actual = MoneyRial.of(47_000L),
            ),
        )
        assertNotNull(shortage.journalEntryId)
        assertEquals(-3_000L, scalar("SELECT differenceRial FROM treasury_reconciliations WHERE transactionId='${shortage.id}'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${shortage.id}' AND direction='PAYMENT' AND amountRial=3000"))
        val journalId = requireNotNull(shortage.journalEntryId)
        assertEquals(
            scalar("SELECT SUM(debitRial) FROM journal_lines WHERE entryId=$journalId"),
            scalar("SELECT SUM(creditRial) FROM journal_lines WHERE entryId=$journalId"),
        )

        val exactId = GlobalId.new()
        val exact = service.execute(
            TreasuryCommand.Reconciliation(
                commandId = exactId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("bank_reconciliation", exactId),
                sourceId = 76L,
                reason = "تطبیق کامل صورتحساب بانک",
                accountId = bank,
                expected = MoneyRial.of(80_000L),
                actual = MoneyRial.of(80_000L),
            ),
        )
        assertEquals(null, exact.journalEntryId)
        assertEquals(0L, scalar("SELECT differenceRial FROM treasury_reconciliations WHERE transactionId='${exact.id}'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${exact.id}'"))
    }

    @Test
    fun bankToCardTransfer_postsDistinctMappedGlAccounts() = runBlocking {
        val seedId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = seedId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("bank_card_seed", seedId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 77L,
                reason = "تأمین حساب بانکی برای آزمون کارتخوان",
                accountId = bank,
                channel = TreasuryChannel.BANK,
                amount = MoneyRial.of(50_000L),
            ),
        )
        val transferId = GlobalId.new()
        val result = service.execute(
            TreasuryCommand.InternalTransfer(
                commandId = transferId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("bank_to_card", transferId),
                sourceId = 78L,
                reason = "انتقال بانک به کارتخوان",
                fromAccountId = bank,
                toAccountId = card,
                amount = MoneyRial.of(12_000L),
            ),
        )
        val journalId = requireNotNull(result.journalEntryId)
        assertEquals(12_000L, scalar("SELECT debitRial FROM journal_lines WHERE entryId=$journalId AND accountCode='1104'"))
        assertEquals(12_000L, scalar("SELECT creditRial FROM journal_lines WHERE entryId=$journalId AND accountCode='1102'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_lines WHERE entryId=$journalId AND accountCode='1101'"))
    }

    @Test
    fun cashToPettyCashTransfer_postsDistinctMappedGlAccounts() = runBlocking {
        val seedId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = seedId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("cash_petty_seed", seedId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 79L,
                reason = "تأمین صندوق برای آزمون تنخواه",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(30_000L),
            ),
        )
        val transferId = GlobalId.new()
        val result = service.execute(
            TreasuryCommand.InternalTransfer(
                commandId = transferId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("cash_to_petty", transferId),
                sourceId = 80L,
                reason = "تأمین تنخواه از صندوق",
                fromAccountId = cash,
                toAccountId = pettyCash,
                amount = MoneyRial.of(9_000L),
            ),
        )
        val journalId = requireNotNull(result.journalEntryId)
        assertEquals(9_000L, scalar("SELECT debitRial FROM journal_lines WHERE entryId=$journalId AND accountCode='1103'"))
        assertEquals(9_000L, scalar("SELECT creditRial FROM journal_lines WHERE entryId=$journalId AND accountCode='1101'"))
    }

    @Test
    fun treasuryPosting_preservesBranchAndOrganizationScope() = runBlocking {
        val branchId = database.branchDao().insert(
            BranchEntity(
                globalId = GlobalId.new().value,
                code = "TR-BR",
                name = "شعبه خزانه",
                createdAtEpochMillis = now++,
                updatedAtEpochMillis = now++,
            ),
        )
        val branchCommandId = GlobalId.new()
        val branchResult = service.execute(
            TreasuryCommand.Receipt(
                commandId = branchCommandId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("branch_treasury", branchCommandId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 90L,
                reason = "دریافت شعبه‌ای آزمون",
                accountingScope = AccountingScope.BRANCH,
                branchId = branchId,
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(5_000L),
            ),
        )
        val branchJournal = requireNotNull(branchResult.journalEntryId)
        assertEquals(branchId, scalar("SELECT branchId FROM journal_entries WHERE id=$branchJournal"))
        assertEquals("BRANCH", text("SELECT accountingScope FROM journal_entries WHERE id=$branchJournal"))

        val orgCommandId = GlobalId.new()
        val orgResult = service.execute(
            TreasuryCommand.Receipt(
                commandId = orgCommandId,
                businessEpochDay = 20_006L,
                correlationId = CorrelationId.forCommand("org_treasury", orgCommandId),
                businessIntent = TreasuryBusinessIntent.OWNER_CAPITAL,
                sourceId = 91L,
                reason = "آورده سازمانی آزمون",
                accountingScope = AccountingScope.ORGANIZATION,
                branchId = null,
                accountId = bank,
                channel = TreasuryChannel.BANK,
                amount = MoneyRial.of(7_000L),
            ),
        )
        val orgJournal = requireNotNull(orgResult.journalEntryId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE id=$orgJournal AND accountingScope='ORGANIZATION' AND branchId IS NULL"))
    }

    @Test
    fun receiptReversal_restoresBalanceAndMarksOriginalReversed() = runBlocking {
        val commandId = GlobalId.new()
        val posted = service.execute(
            TreasuryCommand.Receipt(
                commandId = commandId,
                businessEpochDay = 20_004L,
                correlationId = CorrelationId.forCommand("treasury_reverse_source", commandId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 74L,
                reason = "دریافت قابل برگشت",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(13_000L),
            ),
        )
        assertEquals(13_000L, service.observeBalance(cash).first())
        val reverseId = GlobalId.new()

        val reversal = service.reverse(
            TreasuryReversalCommand(
                commandId = reverseId,
                originalTransactionId = posted.id,
                originalJournalEntryId = requireNotNull(posted.journalEntryId),
                businessEpochDay = 20_005L,
                correlationId = CorrelationId.forCommand("treasury_reverse", reverseId),
                sourceType = "OTHER_RECEIPT_REVERSAL",
                sourceId = 74L,
                reason = "ابطال دریافت آزمون",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(13_000L),
            ),
        )

        assertEquals(0L, service.observeBalance(cash).first())
        assertEquals("REVERSED", database.treasuryDao().transactionById(posted.id)?.status)
        assertNotNull(reversal.journalEntryId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE reversalOfTransactionId='${posted.id}'"))
    }


    @Test
    fun paymentReversal_viaUseCase_restoresBalance_setsLedgerReference_andBlocksDoubleReverse() = runBlocking {
        val seedId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = seedId,
                businessEpochDay = 20_008L,
                correlationId = CorrelationId.forCommand("payment_reverse_seed", seedId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 81L,
                reason = "تأمین صندوق برای تست پرداخت",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(50_000L),
            ),
        )
        val paymentId = GlobalId.new()
        val payment = service.execute(
            TreasuryCommand.Payment(
                commandId = paymentId,
                businessEpochDay = 20_008L,
                correlationId = CorrelationId.forCommand("payment_reverse_source", paymentId),
                businessIntent = TreasuryBusinessIntent.OPERATING_EXPENSE,
                sourceId = 82L,
                reason = "پرداخت قابل برگشت",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(12_000L),
            ),
        )
        assertEquals(38_000L, service.observeBalance(cash).first())

        val reversal = ReverseTreasuryTransactionUseCase(service, service)(payment.id, "ابطال پرداخت آزمون", 20_009L)

        assertEquals(50_000L, service.observeBalance(cash).first())
        assertNotNull(reversal.journalEntryId)
        assertEquals("REVERSED", database.treasuryDao().transactionById(payment.id)?.status)
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${reversal.id}' AND reference='REVERSAL_OF:${payment.id}'"),
        )
        try {
            ReverseTreasuryTransactionUseCase(service, service)(payment.id, "برگشت دوباره غیرمجاز", 20_010L)
            fail("برگشت دوباره یک تراکنش REVERSED باید رد شود")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    @Test
    fun internalTransferReversal_viaUseCase_restoresBothAccountsAndReversesBalancedJournal() = runBlocking {
        val seedId = GlobalId.new()
        service.execute(
            TreasuryCommand.Receipt(
                commandId = seedId,
                businessEpochDay = 20_011L,
                correlationId = CorrelationId.forCommand("transfer_reverse_seed", seedId),
                businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                sourceId = 83L,
                reason = "تأمین صندوق برای انتقال",
                accountId = cash,
                channel = TreasuryChannel.CASH,
                amount = MoneyRial.of(70_000L),
            ),
        )
        val beforeCash = service.observeBalance(cash).first()
        val beforeBank = service.observeBalance(bank).first()
        val transferId = GlobalId.new()
        val transfer = service.execute(
            TreasuryCommand.InternalTransfer(
                commandId = transferId,
                businessEpochDay = 20_011L,
                correlationId = CorrelationId.forCommand("transfer_reverse_source", transferId),
                                sourceId = 84L,
                reason = "انتقال قابل برگشت",
                fromAccountId = cash,
                toAccountId = bank,
                amount = MoneyRial.of(19_000L),
            ),
        )
        assertEquals(beforeCash - 19_000L, service.observeBalance(cash).first())
        assertEquals(beforeBank + 19_000L, service.observeBalance(bank).first())

        val reversal = ReverseTreasuryTransactionUseCase(service, service)(transfer.id, "ابطال انتقال داخلی", 20_012L)

        assertEquals(beforeCash, service.observeBalance(cash).first())
        assertEquals(beforeBank, service.observeBalance(bank).first())
        assertEquals(2L, scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='${reversal.id}' AND reference='REVERSAL_OF:${transfer.id}'"))
        val reversalJournal = requireNotNull(reversal.journalEntryId)
        assertEquals(
            scalar("SELECT SUM(debitRial) FROM journal_lines WHERE entryId=$reversalJournal"),
            scalar("SELECT SUM(creditRial) FROM journal_lines WHERE entryId=$reversalJournal"),
        )
    }

    private fun text(sql: String): String = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
