package ir.restaurant.management.domain.security

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.businessRequire

/** Pure domain policy shared by approval command boundaries. */
object SegregationOfDuties {
    fun requireDifferentActors(
        operation: String,
        creatorActorId: Long,
        approverActorId: Long,
    ) {
        businessRequire(creatorActorId > 0 && approverActorId > 0 && creatorActorId != approverActorId) {
            BusinessError.SeparationOfDutiesViolation(operation)
        }
    }

    fun requireDifferentHistoricalAware(
        operation: String,
        creatorActorId: Long?,
        creatorDisplayName: String,
        approverActorId: Long,
        approverDisplayName: String,
    ) {
        if (creatorActorId != null) {
            requireDifferentActors(operation, creatorActorId, approverActorId)
            return
        }
        businessRequire(
            creatorDisplayName.trim().isNotEmpty() &&
                !creatorDisplayName.trim().equals(approverDisplayName.trim(), ignoreCase = true),
        ) { BusinessError.SeparationOfDutiesViolation(operation) }
    }
}
