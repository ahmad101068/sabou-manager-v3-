package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IranianNationalIdValidatorTest {
    @Test fun validChecksumAccepted() {
        // Known structurally valid test values generated from the official checksum rule.
        assertTrue(IranianNationalIdValidator.isValid("0013546783"))
    }

    @Test fun badChecksumRejected() {
        assertFalse(IranianNationalIdValidator.isValid("0013546788"))
    }

    @Test fun repeatedDigitsRejected() {
        assertFalse(IranianNationalIdValidator.isValid("1111111111"))
    }
}
