package ir.restaurant.management.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AppSessionEntity
import ir.restaurant.management.data.db.AppUserEntity
import ir.restaurant.management.data.db.EmployeeEntity
import ir.restaurant.management.data.db.MenuItemEntity
import ir.restaurant.management.data.db.PayrollBatchEntity
import ir.restaurant.management.data.db.PayrollPayslipEntity
import ir.restaurant.management.data.db.PayrollPeriodEntity
import ir.restaurant.management.data.db.RecipeVersionEntity
import ir.restaurant.management.data.db.SalesCashReconciliationEntity
import ir.restaurant.management.domain.common.DocumentNumberType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberAllocationConcurrencyIntegrationTest {
    private lateinit var database: AppDatabase
    private var now = 1_950_000_000_000L

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recipe_allocation_increments_correctly_and_parallel_allocations_are_unique() = runBlocking {
        val dao = database.recipeDao()
        val menuId = dao.insertMenuItem(
            MenuItemEntity(
                name = "غذای تست هم‌زمانی",
                category = "TEST",
                salePriceRial = 1,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

        val first = allocateRecipeRevision(menuId, 1)
        assertEquals(1, first)

        val allocated = parallel(PARALLEL_WRITERS) { ordinal ->
            allocateRecipeRevision(menuId, ordinal + 2)
        }

        assertEquals(PARALLEL_WRITERS, allocated.distinct().size)
        assertEquals((2..PARALLEL_WRITERS + 1).toSet(), allocated.toSet())
        val persisted = dao.observeVersions(menuId).first().map { it.revisionNo }.toSet()
        assertEquals((1..PARALLEL_WRITERS + 1).toSet(), persisted)
    }

    @Test
    fun management_control_parallel_allocations_are_unique_and_persist_correctly() = runBlocking {
        val day = 22_500L
        val dao = database.managementControlDao()
        val allocator = LocalDocumentNumberAllocator(database, clock = { ++now })

        val allocated = parallel(PARALLEL_WRITERS) { ordinal ->
            database.withTransaction {
                val revision = allocator.nextRaw("SALES_CASH_REVISION:$day").toInt()
                dao.insertCashReconciliation(
                    SalesCashReconciliationEntity(
                        businessEpochDay = day,
                        revisionNo = revision,
                        expectedCashRial = 0,
                        expectedCardRial = 0,
                        expectedTransferRial = 0,
                        actualCashRial = 0,
                        actualCardRial = 0,
                        actualTransferRial = 0,
                        status = "MATCHED",
                        note = "concurrency-$ordinal",
                        reconciledBy = "TEST",
                        createdAtEpochMillis = now + ordinal,
                    ),
                )
                revision
            }
        }

        assertEquals(PARALLEL_WRITERS, allocated.distinct().size)
        assertEquals((1..PARALLEL_WRITERS).toSet(), allocated.toSet())
        assertEquals(PARALLEL_WRITERS.toLong(), scalar("SELECT COUNT(*) FROM sales_cash_reconciliations WHERE businessEpochDay=$day"))
        assertEquals(PARALLEL_WRITERS.toLong(), scalar("SELECT COUNT(DISTINCT revisionNo) FROM sales_cash_reconciliations WHERE businessEpochDay=$day"))
    }

    @Test
    fun personnel_document_allocator_is_unique_and_failed_transaction_does_not_consume_sequence() = runBlocking {
        val allocator = LocalDocumentNumberAllocator(database, clock = { ++now })

        val allocated = parallel(PARALLEL_WRITERS) {
            database.withTransaction { allocator.next(DocumentNumberType.EMPLOYEE) }
        }
        assertEquals(PARALLEL_WRITERS, allocated.distinct().size)
        assertEquals(
            (1..PARALLEL_WRITERS).map { "EMP-${it.toString().padStart(8, '0')}" }.toSet(),
            allocated.toSet(),
        )

        try {
            database.withTransaction {
                allocator.next(DocumentNumberType.EMPLOYEE)
                error("force rollback")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("force rollback", expected.message)
        }

        val afterRollback = database.withTransaction { allocator.next(DocumentNumberType.EMPLOYEE) }
        assertEquals("EMP-${(PARALLEL_WRITERS + 1).toString().padStart(8, '0')}", afterRollback)
        assertEquals(
            (PARALLEL_WRITERS + 2).toLong(),
            scalar("SELECT nextValue FROM document_sequences WHERE sequenceKey='employee'"),
        )
    }

    @Test
    fun payroll_revision_parallel_allocations_are_unique_and_persist_correctly() = runBlocking {
        val personnelDao = database.personnelDao()
        val payrollDao = database.hrPayrollDao()
        val employeeId = personnelDao.insertEmployee(
            EmployeeEntity(
                name = "کارمند تست هم‌زمانی",
                employeeCode = "EMP-CONCURRENCY",
                jobTitle = "TEST",
                monthlySalaryRial = 1_000_000,
                leaveBalanceMicros = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val periodId = payrollDao.insertPayrollPeriod(
            PayrollPeriodEntity(
                periodKey = "CONCURRENCY-PERIOD",
                startEpochDay = 22_500,
                endEpochDay = 22_500,
                paymentDueEpochDay = 22_500,
                status = "OPEN",
                openedByActorId = null,
                openedAtEpochMillis = now,
                closedAtEpochMillis = null,
                reopenedAtEpochMillis = null,
                rowVersion = 1,
                source = "TEST",
            ),
        )
        val batchIds = (1..PARALLEL_WRITERS).map { ordinal ->
            payrollDao.insertPayrollBatch(
                PayrollBatchEntity(
                    documentNumber = "PAY-CONCURRENCY-$ordinal",
                    idempotencyKey = "pay-concurrency-$ordinal",
                    periodId = periodId,
                    scope = "ALL",
                    branchName = null,
                    branchId = null,
                    department = null,
                    status = "CALCULATED",
                    createdByActorId = null,
                    calculatedByActorId = null,
                    calculatedAtEpochMillis = now,
                    reviewedByActorId = null,
                    reviewedAtEpochMillis = null,
                    approvedByActorId = null,
                    approvedAtEpochMillis = null,
                    correlationId = "pay-concurrency-$ordinal",
                    notes = "",
                    rowVersion = 1,
                    accrualJournalEntryId = null,
                    reversalJournalEntryId = null,
                    source = "TEST",
                ),
            )
        }

        val allocated = parallel(PARALLEL_WRITERS) { zeroBased ->
            val ordinal = zeroBased + 1
            database.withTransaction {
                // This mirrors the active PayrollBatchPreparationService allocation contract:
                // read the latest persisted revision, derive its successor, and persist before commit.
                val latest = payrollDao.latestPayslipForEmployeePeriod(employeeId, periodId)
                val revision = (latest?.revisionNo ?: 0) + 1
                payrollDao.insertPayrollPayslip(
                    PayrollPayslipEntity(
                        globalId = "pay-concurrency-global-$ordinal",
                        batchId = batchIds[zeroBased],
                        periodId = periodId,
                        employeeId = employeeId,
                        employeeCodeSnapshot = "EMP-CONCURRENCY",
                        employeeNameSnapshot = "کارمند تست هم‌زمانی",
                        revisionNo = revision,
                        replacesPayslipId = latest?.id,
                        legacyPayrollRunId = null,
                        contractId = null,
                        status = "CALCULATED",
                        grossPayRial = 1_000_000,
                        totalDeductionsRial = 0,
                        netPayRial = 1_000_000,
                        paidAmountRial = 0,
                        remainingAmountRial = 1_000_000,
                        componentDetailComplete = true,
                        calculatedAtEpochMillis = now + ordinal,
                        approvedAtEpochMillis = null,
                        paidAtEpochMillis = null,
                        correlationId = "pay-concurrency-slip-$ordinal",
                        source = "TEST",
                        rowVersion = 1,
                        accrualJournalEntryId = null,
                        reversalJournalEntryId = null,
                        reversalReason = null,
                        reversalEpochDay = null,
                        reversedAtEpochMillis = null,
                    ),
                )
                revision
            }
        }

        assertEquals(PARALLEL_WRITERS, allocated.distinct().size)
        assertEquals((1..PARALLEL_WRITERS).toSet(), allocated.toSet())
        assertEquals(PARALLEL_WRITERS.toLong(), scalar("SELECT COUNT(*) FROM payroll_payslips WHERE employeeId=$employeeId AND periodId=$periodId"))
        assertEquals(PARALLEL_WRITERS.toLong(), scalar("SELECT COUNT(DISTINCT revisionNo) FROM payroll_payslips WHERE employeeId=$employeeId AND periodId=$periodId"))
    }

    @Test
    fun sync_revision_parallel_records_are_unique_and_persist_correctly() = runBlocking {
        val actorId = database.securityDao().insert(
            AppUserEntity(
                username = "sync-concurrency-test",
                displayName = "Sync Concurrency Test Actor",
                pinHash = "not-used-by-concurrency-test",
                role = "OWNER",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        database.securityDao().setSession(
            AppSessionEntity(currentUserId = actorId, updatedAtEpochMillis = now),
        )
        val recorder = SyncRecorder(database, "concurrency-device")
        val entityId = 77L

        parallel(PARALLEL_WRITERS) { ordinal ->
            recorder.record(
                entityType = "CONCURRENCY_PROBE",
                entityId = entityId,
                changeType = "UPDATE",
                occurredAt = now + ordinal + 1,
            )
            ordinal
        }

        val rows = database.syncDao().observeAll().first().filter {
            it.entityType == "CONCURRENCY_PROBE" && it.entityId == entityId
        }
        assertEquals(PARALLEL_WRITERS, rows.size)
        assertEquals(PARALLEL_WRITERS, rows.map { it.revision }.distinct().size)
        assertEquals((1L..PARALLEL_WRITERS.toLong()).toSet(), rows.map { it.revision }.toSet())
        assertEquals(PARALLEL_WRITERS.toLong(), scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='CONCURRENCY_PROBE'"))
        assertEquals(
            PARALLEL_WRITERS.toLong(),
            scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='CONCURRENCY_PROBE' AND actorId=$actorId"),
        )
    }

    private suspend fun allocateRecipeRevision(menuId: Long, ordinal: Int): Int = database.withTransaction {
        val dao = database.recipeDao()
        val revision = dao.nextRevisionNo(menuId)
        dao.insertVersion(
            RecipeVersionEntity(
                menuItemId = menuId,
                revisionNo = revision,
                effectiveFromEpochDay = 22_500L + ordinal,
                createdBy = "TEST",
                createdAtEpochMillis = now + ordinal,
                status = "DRAFT",
            ),
        )
        revision
    }

    private suspend fun <T> parallel(count: Int, block: suspend (Int) -> T): List<T> = coroutineScope {
        (0 until count).map { ordinal ->
            async(Dispatchers.Default) { block(ordinal) }
        }.awaitAll()
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        assertTrue("query returned no rows: $sql", cursor.moveToFirst())
        cursor.getLong(0)
    }

    private companion object {
        const val PARALLEL_WRITERS = 20
    }
}
