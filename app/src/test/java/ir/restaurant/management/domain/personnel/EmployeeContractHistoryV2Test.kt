package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class EmployeeContractHistoryV2Test {
    @Test
    fun resolvesExactlyOneEffectiveContractAndPreservesFutureVersion() {
        val history = listOf(
            contract(id = 11, from = 20_000, to = null, salary = 100_000_000, status = EmploymentContractStatus.SUPERSEDED),
            contract(id = 12, from = 20_100, to = null, salary = 130_000_000, replaces = 11),
        )

        assertEquals(11L, EffectiveContractResolver.resolve(7, 20_050, history).id)
        assertEquals(100_000_000L, EffectiveContractResolver.resolve(7, 20_050, history).baseSalary.value)
        assertEquals(12L, EffectiveContractResolver.resolve(7, 20_100, history).id)
    }

    @Test
    fun missingAndConflictingContractsAreTypedFailures() {
        val missing = assertFailsWith<BusinessRuleViolation> {
            EffectiveContractResolver.resolve(7, 20_000, emptyList())
        }
        assertIs<BusinessError.NoEffectiveContract>(missing.error)

        val conflicting = assertFailsWith<BusinessRuleViolation> {
            EffectiveContractResolver.resolve(
                7,
                20_050,
                listOf(
                    contract(id = 11, from = 20_000, to = 20_100, salary = 100),
                    contract(id = 12, from = 20_040, to = null, salary = 200),
                ),
            )
        }
        val failure = assertIs<BusinessError.ConflictingContracts>(conflicting.error)
        assertEquals(listOf(11L, 12L), failure.contractIds)
    }

    @Test
    fun overlapDetectionIncludesOpenEndedAndBoundaryTouchingRanges() {
        val history = listOf(
            contract(id = 11, from = 20_000, to = 20_100, salary = 100),
            contract(id = 12, from = 20_200, to = null, salary = 200),
        )

        assertEquals(listOf(11L), EffectiveContractResolver.overlapping(7, 20_100, 20_150, history).map { it.id })
        assertEquals(listOf(12L), EffectiveContractResolver.overlapping(7, 20_250, null, history).map { it.id })
        assertTrue(EffectiveContractResolver.overlapping(7, 20_101, 20_199, history).isEmpty())
    }

    @Test
    fun employmentStatusTransitionsEnforceTerminationRules() {
        EmploymentStatusTransitionValidator.requireAllowed(EmploymentStatus.APPLICANT, EmploymentStatus.ACTIVE)
        EmploymentStatusTransitionValidator.requireAllowed(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED, 20_000)

        val missingDate = assertFailsWith<BusinessRuleViolation> {
            EmploymentStatusTransitionValidator.requireAllowed(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED)
        }
        assertIs<BusinessError.InvalidInput>(missingDate.error)

        val invalid = assertFailsWith<BusinessRuleViolation> {
            EmploymentStatusTransitionValidator.requireAllowed(EmploymentStatus.TERMINATED, EmploymentStatus.ACTIVE)
        }
        assertIs<BusinessError.InvalidStateTransition>(invalid.error)
    }

    private fun contract(
        id: Long,
        from: Long,
        to: Long?,
        salary: Long,
        replaces: Long? = null,
        status: EmploymentContractStatus = EmploymentContractStatus.ACTIVE,
    ) = EmploymentContractVersion(
        id = id,
        employeeId = 7,
        contractNumber = "CTR-$id",
        versionNo = 1,
        replacesContractId = replaces,
        contractType = EmploymentContractType.FIXED_TERM,
        effectiveFromEpochDay = from,
        effectiveToEpochDay = to,
        baseSalary = MoneyRial.of(salary),
        standardDailyMinutes = 480,
        standardWeeklyMinutes = 2_880,
        overtimePolicyId = null,
        payrollPolicyId = 3,
        jobTitleSnapshot = "آشپز",
        departmentSnapshot = "آشپزخانه",
        branchSnapshot = "مرکزی",
        status = status,
        createdAtEpochMillis = 1,
        createdByActorId = 2,
        approvedAtEpochMillis = 2,
        approvedByActorId = 3,
    )
}
