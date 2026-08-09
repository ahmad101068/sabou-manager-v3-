package ir.sabou.inventory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.sabou.inventory.domain.operations.UserDraft
import ir.sabou.inventory.domain.operations.UserRole
import ir.sabou.inventory.domain.personnel.AttendanceDraft
import ir.sabou.inventory.domain.personnel.EmployeeAdvanceDraft
import ir.sabou.inventory.domain.personnel.EmployeeContractDraft
import ir.sabou.inventory.domain.personnel.EmployeeDraft
import ir.sabou.inventory.domain.personnel.PayrollDraft
import ir.sabou.inventory.domain.personnel.PayrollPolicyDraft
import ir.sabou.inventory.domain.treasury.TreasuryChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds representative schema-43 data through Alpha159 production repositories. */
@RunWith(AndroidJUnit4::class)
class Alpha159UpgradeSeedTest {
    @Test
    fun seedRepresentativeEmployeeContractAttendanceAdvanceAndPayroll() = runBlocking {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as SabouApplication
        withContext(Dispatchers.IO) { application.container.initialize() }

        val security = application.container.securityRepository
        assertTrue("Upgrade seed requires a clean Alpha159 install", security.users.first().isEmpty())
        security.save(
            id = null,
            draft = UserDraft(
                username = TEST_USERNAME,
                displayName = "Runtime Owner",
                pin = TEST_PIN,
                role = UserRole.OWNER,
                recoveryCode = TEST_RECOVERY_CODE,
            ),
        )

        val personnel = application.container.personnelRepository
        val employeeId = personnel.saveEmployee(
            id = null,
            draft = EmployeeDraft(
                name = "کارمند مهاجرت",
                fatherName = "آزمون",
                jobTitle = "سرپرست شیفت",
                phone = "09120000000",
                monthlySalaryRial = 150_000_000L,
                nationalId = "0012345678",
                hireEpochDay = CONTRACT_START_EPOCH_DAY,
                employeeCode = "EMP-RUNTIME-001",
                branchName = "شعبه مرکزی",
                insuranceNumber = "INS-RUNTIME-001",
                bankCard = "6037991234567890",
                emergencyContact = "09121111111",
            ),
        )
        personnel.saveContract(
            id = null,
            draft = EmployeeContractDraft(
                employeeId = employeeId,
                contractType = "FULL_TIME",
                startEpochDay = CONTRACT_START_EPOCH_DAY,
                endEpochDay = null,
                baseSalaryRial = 150_000_000L,
                dailyWorkMinutes = 480,
                weeklyWorkDays = 6,
                notes = "Alpha159 runtime migration fixture",
            ),
        )
        personnel.savePayrollPolicy(
            PayrollPolicyDraft(
                title = "سیاست مهاجرت",
                effectiveFromEpochDay = CONTRACT_START_EPOCH_DAY,
                overtimeHourlyRateRial = 500_000L,
                absenceDailyDeductionRial = 5_000_000L,
                lateMinuteDeductionRial = 10_000L,
            ),
        )
        for (day in PERIOD_START_EPOCH_DAY..PERIOD_END_EPOCH_DAY) {
            personnel.saveAttendance(
                id = null,
                draft = AttendanceDraft(
                    employeeId = employeeId,
                    workEpochDay = day,
                    status = "PRESENT",
                    checkInMinute = 8 * 60,
                    checkOutMinute = 16 * 60,
                ),
            )
        }
        personnel.postAdvance(
            EmployeeAdvanceDraft(
                employeeId = employeeId,
                amountRial = 10_000_000L,
                advanceEpochDay = PERIOD_START_EPOCH_DAY,
                paymentMethod = TreasuryChannel.CASH,
                notes = "Alpha159 migration fixture",
            ),
        )
        personnel.postPayroll(
            PayrollDraft(
                employeeId = employeeId,
                periodYear = 1405,
                periodMonth = 5,
                bonusRial = 2_000_000L,
                allowancesRial = 1_000_000L,
                insuranceRial = 1_000_000L,
                taxRial = 500_000L,
                advanceDeductionRial = 5_000_000L,
                periodStartEpochDay = PERIOD_START_EPOCH_DAY,
                periodEndEpochDay = PERIOD_END_EPOCH_DAY,
                paymentEpochDay = PERIOD_END_EPOCH_DAY + 1,
                paymentMethod = TreasuryChannel.BANK,
                notes = "Alpha159 migration fixture",
            ),
        )

        assertEquals(1, personnel.employees.first().size)
        assertEquals(1, personnel.contracts(employeeId).first().size)
        assertEquals(PERIOD_DAY_COUNT, personnel.attendance.first().size)
        assertEquals(1, personnel.openAdvances.first().size)
        assertEquals(1, personnel.payrolls.first().size)
    }

    private companion object {
        const val TEST_USERNAME = "runtime_owner"
        const val TEST_PIN = "123456"
        const val TEST_RECOVERY_CODE = "87654321"
        const val CONTRACT_START_EPOCH_DAY = 19_900L
        const val PERIOD_START_EPOCH_DAY = 20_000L
        const val PERIOD_END_EPOCH_DAY = 20_029L
        const val PERIOD_DAY_COUNT = 30
    }
}
