package ir.restaurant.management.domain.operations

/**
 * Compatibility contracts retained for pre-Foundation callers. Enterprise approval and
 * workforce scheduling use their dedicated services; these pure models perform no persistence.
 */
enum class ApprovalStatus { PENDING, APPROVED, REJECTED }

data class ApprovalRequest(
    val id: Long,
    val entityType: String,
    val entityId: Long,
    val amountRial: Long,
    val requestedBy: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val decidedBy: String? = null,
) {
    init {
        require(id > 0) { "شناسه درخواست تأیید معتبر نیست." }
        require(entityType.isNotBlank()) { "نوع سند تأیید الزامی است." }
        require(entityId > 0) { "شناسه سند تأیید معتبر نیست." }
        require(amountRial >= 0) { "مبلغ تأیید نمی‌تواند منفی باشد." }
        require(requestedBy.isNotBlank()) { "درخواست‌کننده تأیید الزامی است." }
    }

    fun approve(actor: String): ApprovalRequest {
        require(status == ApprovalStatus.PENDING) { "درخواست قبلاً تعیین تکلیف شده است." }
        require(actor.isNotBlank()) { "تأییدکننده الزامی است." }
        return copy(status = ApprovalStatus.APPROVED, decidedBy = actor)
    }
}

object ApprovalPolicy {
    fun requiresApproval(documentType: String, amountRial: Long, thresholdRial: Long): Boolean {
        require(documentType.isNotBlank()) { "نوع سند الزامی است." }
        require(amountRial >= 0 && thresholdRial >= 0) { "مبلغ و آستانه باید نامنفی باشند." }
        return amountRial >= thresholdRial
    }
}

data class ShiftDemand(
    val shiftId: Long,
    val startMinute: Int,
    val endMinute: Int,
    val requiredStaff: Int,
) {
    init {
        require(shiftId > 0) { "شناسه شیفت معتبر نیست." }
        require(startMinute in 0..1439 && endMinute in 1..1440 && startMinute < endMinute) {
            "بازه شیفت معتبر نیست."
        }
        require(requiredStaff > 0) { "تعداد نیروی موردنیاز باید مثبت باشد." }
    }
}

data class StaffAvailability(
    val employeeId: Long,
    val employeeName: String,
    val availableFromMinute: Int,
    val availableToMinute: Int,
    val jobTitle: String,
)

data class PlannedShiftAssignment(
    val shiftId: Long,
    val employeeId: Long,
    val employeeName: String,
    val jobTitle: String,
    val startMinute: Int,
    val endMinute: Int,
)

object ShiftPlanner {
    fun plan(demand: ShiftDemand, availability: List<StaffAvailability>): List<PlannedShiftAssignment> =
        availability.asSequence()
            .filter { it.availableFromMinute <= demand.startMinute && it.availableToMinute >= demand.endMinute }
            .distinctBy(StaffAvailability::employeeId)
            .take(demand.requiredStaff)
            .map {
                PlannedShiftAssignment(
                    shiftId = demand.shiftId,
                    employeeId = it.employeeId,
                    employeeName = it.employeeName,
                    jobTitle = it.jobTitle,
                    startMinute = demand.startMinute,
                    endMinute = demand.endMinute,
                )
            }
            .toList()
}
