package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

object EffectiveContractResolver {
    fun resolve(
        employeeId: Long,
        businessEpochDay: Long,
        contracts: List<EmploymentContractVersion>,
    ): EmploymentContractVersion {
        require(employeeId > 0 && businessEpochDay > 0)
        val candidates = contracts.filter { contract ->
            contract.employeeId == employeeId &&
                contract.status in setOf(
                    EmploymentContractStatus.APPROVED,
                    EmploymentContractStatus.ACTIVE,
                    EmploymentContractStatus.SUPERSEDED,
                    EmploymentContractStatus.LEGACY,
                ) &&
                contract.effectiveFromEpochDay <= businessEpochDay &&
                (contract.effectiveToEpochDay == null || contract.effectiveToEpochDay >= businessEpochDay)
        }
        // A superseded version remains historically effective until an approved successor in its
        // replacement chain is itself effective. This avoids both a pre-approval gap and rewriting
        // the predecessor's dates after a frozen payroll has referenced it.
        val byId = contracts.associateBy { it.id }
        val replacedCandidateIds = buildSet {
            candidates.forEach { candidate ->
                val visited = mutableSetOf<Long>()
                var predecessorId = candidate.replacesContractId
                while (predecessorId != null && visited.add(predecessorId)) {
                    add(predecessorId)
                    predecessorId = byId[predecessorId]?.replacesContractId
                }
            }
        }
        val effective = candidates.filterNot { it.id in replacedCandidateIds }
        if (effective.isEmpty()) {
            throw BusinessError.NoEffectiveContract(employeeId, businessEpochDay).asViolation()
        }
        if (effective.size > 1) {
            throw BusinessError.ConflictingContracts(
                employeeId = employeeId,
                businessEpochDay = businessEpochDay,
                contractIds = effective.map { it.id }.sorted(),
            ).asViolation()
        }
        return effective.single()
    }

    fun overlapping(
        employeeId: Long,
        fromEpochDay: Long,
        toEpochDay: Long?,
        contracts: List<EmploymentContractVersion>,
        excludeContractId: Long? = null,
    ): List<EmploymentContractVersion> {
        require(employeeId > 0 && fromEpochDay > 0)
        require(toEpochDay == null || toEpochDay >= fromEpochDay)
        val normalizedTo = toEpochDay ?: Long.MAX_VALUE
        return contracts.filter { contract ->
            contract.employeeId == employeeId &&
                contract.id != excludeContractId &&
                contract.status !in setOf(
                    EmploymentContractStatus.CANCELLED,
                    EmploymentContractStatus.SUPERSEDED,
                    EmploymentContractStatus.LEGACY_UNKNOWN,
                ) &&
                contract.effectiveFromEpochDay <= normalizedTo &&
                (contract.effectiveToEpochDay ?: Long.MAX_VALUE) >= fromEpochDay
        }
    }
}
