package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.GlobalId
import java.util.Locale

/** Concurrency-safe, non-sequential document codes for HR masters. */
object PersonnelReferenceCode {
    private fun suffix(): String = GlobalId.new().value.replace("-", "").take(12).uppercase(Locale.US)

    fun newShiftCode(): String = "SHF-${suffix()}"
    fun newWorkScheduleCode(): String = "WSC-${suffix()}"
}
