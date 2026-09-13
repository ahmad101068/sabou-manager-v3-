package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity

/**
 * Single business resolver for canonical branch identity.
 * Legacy text is accepted only as an input compatibility key and must resolve to exactly one active branch.
 */
internal class CanonicalBranchResolver(
    database: AppDatabase,
) {
    private val dao = database.branchDao()

    suspend fun requireExisting(branchId: Long): BranchEntity {
        require(branchId > 0) { "branch_id_invalid" }
        return dao.byId(branchId) ?: error("شعبه معتبر پیدا نشد.")
    }

    suspend fun requireActive(branchId: Long): BranchEntity {
        val branch = requireExisting(branchId)
        require(branch.isActive) { "شعبه معتبر و فعال پیدا نشد." }
        return branch
    }

    suspend fun resolveOptional(branchId: Long?, legacyKey: String?): BranchEntity? {
        if (branchId != null) return requireActive(branchId)
        val normalized = legacyKey?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val candidates = dao.legacyCandidates(normalized).filter { it.isActive }
        require(candidates.size == 1) { "شعبه legacy به‌صورت قطعی قابل تشخیص نیست." }
        return candidates.single()
    }

    suspend fun resolveRequired(branchId: Long?, legacyKey: String?, message: String = "شعبه معتبر مشخص نشده است."): BranchEntity =
        resolveOptional(branchId, legacyKey) ?: error(message)
}
