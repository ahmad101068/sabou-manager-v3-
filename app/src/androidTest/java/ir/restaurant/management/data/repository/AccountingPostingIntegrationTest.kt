package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.data.db.AccountingPeriodLockEntity
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.AccountingReversalCommand
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.accounting.ManualJournalDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
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
class AccountingPostingIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var posting: LocalAccountingPostingEngine

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        runBlocking {
            database.branchDao().insert(BranchEntity(id = 2L, globalId = "test:branch:2", code = "B2", name = "شعبه ۲", createdAtEpochMillis = NOW, updatedAtEpochMillis = NOW))
        }
        posting = LocalAccountingPostingEngine(database, clock = { NOW })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun balancedPostingIsIdempotentAndPostedJournalIsImmutable() = runBlocking {
        val draft = draft(epochDay = 100, sourceId = 55)
        val context = context("manual:55:integration", 55)

        val first = posting.postBalanced(draft, context, entryNoFactory = { "IT-$it" })
        val replay = posting.postBalanced(draft, context, entryNoFactory = { "IT-$it" })

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.entryId, replay.entryId)
        val entry = database.accountingDao().entryById(first.entryId)
        assertNotNull(entry)
        assertEquals("POSTED", entry?.status)
        assertEquals(ACTOR_ID, entry?.postedByActorId)
        assertEquals(NOW, entry?.postedAtEpochMillis)
        val lines = database.accountingDao().linesByEntry(first.entryId)
        assertEquals(lines.sumOf { it.debitRial }, lines.sumOf { it.creditRial })
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE idempotencyKey='manual:55:integration'"))
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='POST' AND entityId=${first.entryId}"),
        )

        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE journal_entries SET description='tampered' WHERE id=${first.entryId}",
            )
            fail("سند POSTED نباید ویرایش شود")
        } catch (_: Exception) {
            Unit
        }
        try {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM journal_lines WHERE entryId=${first.entryId}",
            )
            fail("آرتیکل سند POSTED نباید حذف شود")
        } catch (_: Exception) {
            Unit
        }
    }


    @Test
    fun branchPostingAndReversalPreserveStructuralScope() = runBlocking {
        val original = posting.postBalanced(
            draft = draft(epochDay = 120, sourceId = 88).copy(
                accountingScope = AccountingScope.BRANCH,
                branchId = 2,
            ),
            context = context("branch:88:integration", 88),
            entryNoFactory = { "BR-$it" },
        )
        val originalEntry = requireNotNull(database.accountingDao().entryById(original.entryId))
        assertEquals("BRANCH", originalEntry.accountingScope)
        assertEquals(2L, originalEntry.branchId)

        val reversed = posting.reverse(
            AccountingReversalCommand(
                originalEntryId = original.entryId,
                entryNo = "R-${original.entryId}",
                sourceType = "BRANCH_TEST_REVERSAL",
                sourceId = original.entryId,
                businessEpochDay = 121,
                reason = "branch scope regression",
                idempotencyKey = "branch:88:integration:reversal",
                correlationId = CorrelationId.parse("integration:branch:88:reversal"),
                actorId = ACTOR_ID,
            ),
        )
        val reversalEntry = requireNotNull(database.accountingDao().entryById(reversed.entryId))
        assertEquals("BRANCH", reversalEntry.accountingScope)
        assertEquals(2L, reversalEntry.branchId)
    }

    @Test
    fun canonicalBranchPnlExcludesOrganizationOtherBranchesAndTax() = runBlocking {
        suspend fun postPnl(
            sourceId: Long,
            scope: AccountingScope,
            branchId: Long?,
            debitCode: String,
            creditCode: String,
            amount: Long,
        ) {
            posting.postBalanced(
                draft = BalancedJournalDraft(
                    description = "branch pnl integration $sourceId",
                    entryEpochDay = 130,
                    sourceType = "BRANCH_PNL_TEST",
                    sourceId = sourceId,
                    accountingScope = scope,
                    branchId = branchId,
                    lines = listOf(
                        JournalLineDraft(accountCode = debitCode, debit = MoneyRial.of(amount)),
                        JournalLineDraft(accountCode = creditCode, credit = MoneyRial.of(amount)),
                    ),
                ),
                context = context("branch:pnl:$sourceId", sourceId),
                entryNoFactory = { "P-$it" },
            )
        }

        postPnl(201, AccountingScope.BRANCH, 2, "1101", "4101", 125_000_000)
        postPnl(202, AccountingScope.BRANCH, 2, "5101", "1301", 48_000_000)
        postPnl(203, AccountingScope.BRANCH, 2, "6105", "1101", 12_000_000)
        postPnl(204, AccountingScope.BRANCH, 2, "6101", "2102", 9_000_000)
        postPnl(205, AccountingScope.BRANCH, 1, "6105", "1101", 100_000_000)
        postPnl(206, AccountingScope.ORGANIZATION, null, "6105", "1101", 500_000_000)
        postPnl(207, AccountingScope.BRANCH, 2, "1101", "2103", 9_000_000)

        val pnl = database.accountingDao().branchProfitLoss(2, 130, 130)
        assertEquals(125_000_000L, pnl.revenueRial)
        assertEquals(48_000_000L, pnl.cogsRial)
        assertEquals(12_000_000L, pnl.operatingExpensesExcludingPayrollRial)
        assertEquals(9_000_000L, pnl.payrollRial)
        assertEquals(0L, pnl.unassignedRevenueLineCount)
        assertEquals(0L, pnl.unassignedCogsLineCount)
        assertEquals(0L, pnl.unassignedOperatingExpenseLineCount)
        assertEquals(0L, pnl.unassignedPayrollLineCount)
    }

    @Test
    fun closedPeriodFailureLeavesNoPartialDraftOrLines() = runBlocking {
        database.managementControlDao().insertAccountingPeriodLock(
            AccountingPeriodLockEntity(
                fromEpochDay = 200,
                toEpochDay = 210,
                reason = "integration test",
                closedBy = "tester",
                closedAtEpochMillis = NOW,
            ),
        )

        try {
            posting.postBalanced(
                draft = draft(epochDay = 205, sourceId = 66),
                context = context("manual:66:integration", 66),
                entryNoFactory = { "IT-$it" },
            )
            fail("دوره مالی بسته باید posting را rollback کند")
        } catch (_: Exception) {
            Unit
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE idempotencyKey='manual:66:integration'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_lines WHERE entryId NOT IN (SELECT id FROM journal_entries)"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE correlationId='integration:journal:66'"))
    }

    @Test
    fun reversalBoundaryIsAtomicIdempotentAndLinksOriginalEntry() = runBlocking {
        val original = posting.postBalanced(
            draft = draft(epochDay = 100, sourceId = 77),
            context = context("manual:77:integration", 77),
            entryNoFactory = { "IT-$it" },
        )
        val command = AccountingReversalCommand(
            originalEntryId = original.entryId,
            entryNo = "R-${original.entryId}",
            sourceType = "MANUAL_TEST_REVERSAL",
            sourceId = original.entryId,
            businessEpochDay = 101,
            reason = "integration correction",
            idempotencyKey = "manual:77:integration:reversal",
            correlationId = CorrelationId.parse("integration:journal:77:reversal"),
            actorId = ACTOR_ID,
        )

        val first = posting.reverse(command)
        val replay = posting.reverse(command)

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.entryId, replay.entryId)
        assertEquals(
            original.entryId,
            database.accountingDao().entryById(first.entryId)?.reversalOfEntryId,
        )
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM journal_entries WHERE reversalOfEntryId=${original.entryId}"),
        )
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='REVERSE' AND entityId=${first.entryId}"),
        )
    }

    @Test
    fun manualReversalRetryReturnsOriginalPostingWithoutDuplicateAudit() = runBlocking {
        val authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(
            db = database,
            clock = { NOW },
            authorizer = authorizer,
        ).save(
            null,
            UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"),
        )
        val repository = LocalAccountingRepository(
            database = database,
            clock = { NOW },
            authorizer = authorizer,
        )
        val original = repository.postManual(
            ManualJournalDraft(
                description = "سند دستی برای آزمون برگشت",
                entryEpochDay = 100,
                commandId = "123e4567-e89b-42d3-a456-426614174010",
                lines = listOf(
                    JournalLineDraft(accountCode = "1101", debit = MoneyRial.of(250_000)),
                    JournalLineDraft(accountCode = "3101", credit = MoneyRial.of(250_000)),
                ),
            ),
        )

        val first = repository.reverseManual(original.id, 101, "اصلاح طبقه‌بندی حساب")
        val replay = repository.reverseManual(original.id, 101, "اصلاح طبقه‌بندی حساب")

        assertEquals(first, replay)
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='REVERSAL' AND sourceId=${original.id}"),
        )
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='REVERSE' AND entityId=${first.id}"),
        )
    }

    private fun draft(epochDay: Long, sourceId: Long) = BalancedJournalDraft(
        description = "سند تست یکپارچگی",
        entryEpochDay = epochDay,
        sourceType = "MANUAL_TEST",
        sourceId = sourceId,
        lines = listOf(
            JournalLineDraft(accountCode = "1101", debit = MoneyRial.of(250_000)),
            JournalLineDraft(accountCode = "3101", credit = MoneyRial.of(250_000)),
        ),
    )

    private fun context(key: String, sourceId: Long) = AccountingPostingContext(
        idempotencyKey = key,
        correlationId = "integration:journal:$sourceId",
        actorId = ACTOR_ID,
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ACTOR_ID = 42L
    }
}
