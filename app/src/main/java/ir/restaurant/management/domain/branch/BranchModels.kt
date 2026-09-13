package ir.restaurant.management.domain.branch

import ir.restaurant.management.core.GlobalId
import kotlinx.coroutines.flow.Flow

enum class BranchLifecycleStatus { ACTIVE, SUSPENDED, CLOSING, CLOSED, ARCHIVED }

data class BranchRecord(
    val id: Long,
    val globalId: String,
    val organizationId: Long?,
    val code: String?,
    val name: String,
    val isActive: Boolean,
    val status: BranchLifecycleStatus = if (isActive) BranchLifecycleStatus.ACTIVE else BranchLifecycleStatus.CLOSED,
)

data class BranchDraft(
    val name: String,
    val code: String? = null,
    val organizationId: Long? = null,
    val globalId: String = GlobalId.new().value,
) {
    fun validated(): BranchDraft {
        val normalizedName = name.trim()
        val normalizedCode = code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        require(normalizedName.length in 2..120) { "branch_name_invalid" }
        require(normalizedCode == null || normalizedCode.matches(Regex("[A-Z0-9._/-]{1,32}"))) { "branch_code_invalid" }
        require(organizationId == null || organizationId > 0) { "branch_organization_invalid" }
        return copy(name = normalizedName, code = normalizedCode, globalId = GlobalId.parse(globalId).value)
    }
}

interface BranchRepository {
    val branches: Flow<List<BranchRecord>>
    val activeBranches: Flow<List<BranchRecord>>
    suspend fun getById(id: Long): BranchRecord?
    suspend fun getByGlobalId(globalId: String): BranchRecord?
    suspend fun findDeterministicLegacyMapping(legacyKey: String): BranchRecord?
    suspend fun listActive(): List<BranchRecord>
    suspend fun create(draft: BranchDraft): Long
    suspend fun rename(id: Long, name: String)
    suspend fun setActive(id: Long, active: Boolean)
    suspend fun transitionStatus(id: Long, status: BranchLifecycleStatus, reason: String)
}
