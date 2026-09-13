package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmployeeDraftTest {
    @Test
    fun fatherNameIsTrimmedWithOtherIdentityFields() {
        val result = EmployeeDraft(
            name = "  کارمند تست  ",
            fatherName = "  احمد  ",
            jobTitle = "  آشپز  ",
            phone = "09120000000",
            monthlySalaryRial = 10_000_000,
        ).validated()

        assertEquals("احمد", result.fatherName)
    }

    @Test
    fun extendedIdentityAndEmploymentFieldsAreTrimmed() {
        val result = EmployeeDraft(
            name = "کارمند تست",
            fatherName = "احمد",
            jobTitle = "آشپز",
            phone = "09120000000",
            monthlySalaryRial = 10_000_000,
            employeeCode = "  E-102  ",
            branchName = "  شعبه مرکزی  ",
            insuranceNumber = "  12345  ",
            bankCard = "6037997512345678",
            address = "  تهران  ",
            emergencyContact = "  09121111111  ",
        ).validated()

        assertEquals("E-102", result.employeeCode)
        assertEquals("شعبه مرکزی", result.branchName)
        assertEquals("12345", result.insuranceNumber)
        assertEquals("تهران", result.address)
        assertEquals("09121111111", result.emergencyContact)
    }

    @Test
    fun bankCardMustBeBlankOrSixteenDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            EmployeeDraft(
                name = "کارمند تست",
                fatherName = "احمد",
                jobTitle = "آشپز",
                phone = "09120000000",
                monthlySalaryRial = 10_000_000,
                bankCard = "1234",
            ).validated()
        }
    }
}
