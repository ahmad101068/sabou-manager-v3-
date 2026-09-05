package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.personnel.ShiftCategory
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.WorkScheduleDayRule
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkSchedulePatternType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchPayrollPostingIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var personnel: LocalPersonnelRepository
    private lateinit var payroll: LocalHrPayrollService
    private var now = 1_910_000_000_000L
    private val day = 22_100L
    private var ownerId = 0L
    private var approverId = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        ownerId = security.save(null, UserDraft("branch-payroll-owner", "مالک حقوق شعبه", "123456", UserRole.OWNER, "87654321"))
        approverId = security.save(null, UserDraft("branch-payroll-approver", "تأییدکننده حقوق شعبه", "654321", UserRole.OWNER, "11223344"))
        security.switchUser(ownerId, "123456")
        database.branchDao().insert(
            BranchEntity(id = 2L, globalId = "test:payroll:branch:2", code = "B2", name = "ونک", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        personnel = LocalPersonnelRepository(database, clock = { ++now }, authorizer = authorizer)
        val accounting = LocalAccountingPostingEngine(database, clock = { ++now })
        payroll = LocalHrPayrollService(
            database = database,
            authorizer = authorizer,
            accountingPosting = accounting,
            treasury = LocalTreasuryServiceV2(database, accounting, authorizer, DefaultTreasuryAccountCatalog(), clock = { ++now }),
            clock = { ++now },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun branchSpecificPayrollPostsBranchJournalWithCanonicalBranchId() = runBlocking {
        val employeeId = seedPayrollReadyEmployee(branchId = 2L, salaryRial = 9_000_000L)
        val periodId = payroll.openPeriod(PayrollPeriodDraftV2("BRANCH-2100", day, day, day))
        val batchId = payroll.createBatch(PayrollBatchDraftV2(periodId = periodId, scope = "BRANCH", branchId = 2L))
        val outcome = payroll.calculateBatch(CalculatePayrollBatchCommand(batchId, listOf(employeeId)))
        assertTrue("blocking payroll exceptions: ${outcome.exceptions}", !outcome.hasBlockingExceptions)
        payroll.submitBatchForReview(ReviewPayrollBatchCommand(batchId, "بازبینی حقوق شعبه"))
        security.switchUser(approverId, "654321")
        payroll.approveBatch(ApprovePayrollBatchCommand(batchId, "تأیید مستقل حقوق شعبه"))

        val journal = database.openHelper.writableDatabase.query(
            "SELECT accountingScope, branchId FROM journal_entries WHERE sourceType='PAYROLL_ACCRUAL' ORDER BY id DESC LIMIT 1",
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getLong(1)
        }
        assertEquals("BRANCH", journal.first)
        assertEquals(2L, journal.second)
        assertEquals(
            9_000_000L,
            scalar("SELECT COALESCE(SUM(l.debitRial-l.creditRial),0) FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId WHERE e.sourceType='PAYROLL_ACCRUAL' AND e.branchId=2 AND l.accountCode='6101'"),
        )
    }

    @Test
    fun allPayrollPostsOrganizationJournalAndDoesNotLeakIntoBranchPnl() = runBlocking {
        val employeeId = seedPayrollReadyEmployee(branchId = 2L, salaryRial = 9_000_000L)
        val periodId = payroll.openPeriod(PayrollPeriodDraftV2("ORG-2100", day, day, day))
        val batchId = payroll.createBatch(PayrollBatchDraftV2(periodId = periodId, scope = "ALL"))
        val outcome = payroll.calculateBatch(CalculatePayrollBatchCommand(batchId, listOf(employeeId)))
        assertTrue("blocking payroll exceptions: ${outcome.exceptions}", !outcome.hasBlockingExceptions)
        payroll.submitBatchForReview(ReviewPayrollBatchCommand(batchId, "بازبینی حقوق سازمان"))
        security.switchUser(approverId, "654321")
        payroll.approveBatch(ApprovePayrollBatchCommand(batchId, "تأیید مستقل حقوق سازمان"))

        database.openHelper.writableDatabase.query(
            "SELECT accountingScope, branchId FROM journal_entries WHERE sourceType='PAYROLL_ACCRUAL' ORDER BY id DESC LIMIT 1",
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("ORGANIZATION", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        val pnl = database.accountingDao().branchProfitLoss(2L, day, day)
        assertEquals(0L, pnl.payrollRial)
    }

    @Test
    fun newBranchPayrollWithoutResolvableBranchIsBlockedBeforePosting() = runBlocking {
        val periodId = payroll.openPeriod(PayrollPeriodDraftV2("INVALID-2100", day, day, day))
        try {
            payroll.createBatch(PayrollBatchDraftV2(periodId = periodId, scope = "BRANCH", branchName = "شعبه ناشناخته"))
            throw AssertionError("unresolved new branch payroll must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("قطعی") || expected.message.orEmpty().contains("شعبه"))
        }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='PAYROLL_ACCRUAL'"))
    }

    private suspend fun seedPayrollReadyEmployee(branchId: Long, salaryRial: Long): Long {
        security.switchUser(ownerId, "123456")
        val policyId = personnel.savePayrollPolicy(
            PayrollPolicyDraft(
                title = "سیاست تست ${now}",
                effectiveFromEpochDay = day - 30,
                overtimeHourlyRateRial = 0,
                absenceDailyDeductionRial = 0,
                lateMinuteDeductionRial = 0,
                insuranceBasisPoints = 0,
                taxBasisPoints = 0,
            ),
        )
        val suffix = now.toString().takeLast(6)
        val employeeId = personnel.saveEmployee(
            null,
            EmployeeDraft(
                name = "کارمند تست $suffix",
                fatherName = "تست",
                jobTitle = "کارشناس",
                phone = "",
                monthlySalaryRial = salaryRial,
                hireEpochDay = day - 30,
                employeeCode = "BP$suffix",
                department = "عملیات",
                branchId = branchId,
            ),
        )
        val shiftId = personnel.saveShiftTemplate(
            null,
            ShiftTemplateDraft(
                code = "BS$suffix",
                name = "شیفت $suffix",
                category = ShiftCategory.MORNING,
                startMinute = 480,
                endMinute = 960,
                overtimeRequiresApproval = false,
                branchId = branchId,
            ),
        )
        val scheduleId = personnel.saveWorkSchedule(
            null,
            WorkScheduleDraft(
                code = "BW$suffix",
                name = "برنامه $suffix",
                patternType = WorkSchedulePatternType.WEEKLY_FIXED,
                cycleLengthDays = 7,
                effectiveFromEpochDay = day - 30,
                effectiveToEpochDay = day + 30,
                branchId = branchId,
                days = (0..6).map { sequenceDay ->
                    WorkScheduleDayRule(sequenceDay, sequenceDay + 1, shiftId, false)
                },
            ),
        )
        val contractId = personnel.saveContract(
            null,
            EmployeeContractDraft(
                employeeId = employeeId,
                contractType = "PERMANENT",
                startEpochDay = day - 30,
                endEpochDay = day + 30,
                baseSalaryRial = salaryRial,
                dailyWorkMinutes = 480,
                weeklyWorkDays = 7,
                payrollPolicyId = policyId,
                workScheduleId = scheduleId,
                defaultShiftTemplateId = shiftId,
            ),
        )
        security.switchUser(approverId, "654321")
        personnel.approveContract(contractId)
        security.switchUser(ownerId, "123456")
        personnel.saveAttendance(
            null,
            AttendanceDraft(
                employeeId = employeeId,
                workEpochDay = day,
                status = "PRESENT",
                checkInMinute = 480,
                checkOutMinute = 960,
                scheduledStartMinute = 480,
                scheduledEndMinute = 960,
            ),
        )
        return employeeId
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) 0L else cursor.getLong(0)
    }
}
