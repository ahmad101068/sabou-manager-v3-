package ir.restaurant.management.domain.personnel

/** Validates the official 10-digit Iranian national identification checksum. */
object IranianNationalIdValidator {
    fun isValid(raw: String): Boolean {
        val value = raw.trim()
        if (value.length != 10 || value.any { !it.isDigit() }) return false
        if (value.all { it == value[0] }) return false
        val sum = (0 until 9).sumOf { index ->
            (value[index] - '0') * (10 - index)
        }
        val remainder = sum % 11
        val expected = if (remainder < 2) remainder else 11 - remainder
        return value[9] - '0' == expected
    }

    fun requireValid(raw: String) {
        require(isValid(raw)) { "کد ملی معتبر نیست." }
    }
}
