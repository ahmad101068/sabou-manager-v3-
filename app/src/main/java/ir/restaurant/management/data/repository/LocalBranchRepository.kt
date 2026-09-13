package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.BranchLegacyAliasEntity
import ir.restaurant.management.data.db.UserBranchScopeEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.branch.BranchLifecycleStatus
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.branch.BranchRepository
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalBranchRepository(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
) : BranchRepository {
    private val dao = database.branchDao()
    private val audit = LocalAuditEventWriter(database)
    private val scope = LocalDataScopeService(database, authorizer)

    override val branches: Flow<List<BranchRecord>> = scope.scopedBranches().map { rows -> rows.map(BranchEntity::toRecord) }
    override val activeBranches: Flow<List<BranchRecord>> = scope.scopedActiveBranches().map { rows -> rows.map(BranchEntity::toRecord) }

    override suspend fun getById(id: Long): BranchRecord? {
        val row = dao.byId(id) ?: return null
        scope.requireBranch(id, operational = false)
        return row.toRecord()
    }

    override suspend fun getByGlobalId(globalId: String): BranchRecord? {
        val row = dao.byGlobalId(globalId.trim()) ?: return null
        scope.requireBranch(row.id, operational = false)
        return row.toRecord()
    }

    override suspend fun findDeterministicLegacyMapping(legacyKey: String): BranchRecord? {
        val normalized = legacyKey.trim()
        if (normalized.isEmpty()) return null
        val candidates = dao.legacyCandidates(normalized).filter { row ->
            runCatching { scope.requireBranch(row.id, operational = false) }.isSuccess
        }
        return candidates.singleOrNull()?.toRecord()
    }

    override suspend fun listActive(): List<BranchRecord> = scope.activeBranches().map(BranchEntity::toRecord)

    override suspend fun create(draft: BranchDraft): Long {
        val actor = authorizer.require(Permission.BRANCH_MANAGE)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            require(dao.byGlobalId(valid.globalId) == null) { "branch_global_id_duplicate" }
            valid.code?.let { code ->
                require(dao.byOrganizationAndCode(valid.organizationId, code) == null) { "branch_organization_code_duplicate" }
            }
            val id = dao.insert(
                BranchEntity(
                    globalId = valid.globalId,
                    organizationId = valid.organizationId,
                    code = valid.code,
                    name = valid.name,
                    isActive = true,
                    status = BranchLifecycleStatus.ACTIVE.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            if (actor.role != UserRole.OWNER) {
                database.phase3Dao().grantBranch(UserBranchScopeEntity(actor.id, id, now))
            }
            dao.insertLegacyAlias(valid.name.toLegacyAlias(id, now))
            audit.appendAuthorized(authorizer, "CREATE", "BRANCH", id, "ایجاد شعبه ${valid.name}", now, reason = "ایجاد شعبه", afterSnapshot = "name=${valid.name};code=${valid.code.orEmpty()};status=ACTIVE", correlationId = "branch:$id:create")
            id
        }
    }

    override suspend fun rename(id: Long, name: String) {
        authorizer.require(Permission.BRANCH_MANAGE)
        scope.requireBranch(id, operational = false)
        val normalized = name.trim()
        require(normalized.length in 2..120) { "branch_name_invalid" }
        database.withTransaction {
            val current = dao.byId(id) ?: error("شعبه پیدا نشد.")
            val now = clock()
            dao.insertLegacyAlias(current.name.toLegacyAlias(id, now))
            dao.insertLegacyAlias(normalized.toLegacyAlias(id, now))
            check(dao.update(current.copy(name = normalized, updatedAtEpochMillis = now)) == 1)
            audit.appendAuthorized(authorizer, "RENAME", "BRANCH", id, "تغییر نام شعبه", now, reason = "تغییر نام شعبه", beforeSnapshot = "name=${current.name}", afterSnapshot = "name=$normalized", correlationId = "branch:$id:rename:$now")
        }
    }

    override suspend fun setActive(id: Long, active: Boolean) {
        transitionStatus(
            id = id,
            status = if (active) BranchLifecycleStatus.ACTIVE else BranchLifecycleStatus.CLOSED,
            reason = if (active) "فعال‌سازی شعبه" else "درخواست بستن شعبه",
        )
    }

    override suspend fun transitionStatus(id: Long, status: BranchLifecycleStatus, reason: String) {
        authorizer.require(Permission.BRANCH_MANAGE)
        scope.requireBranch(id, operational = false)
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل تغییر وضعیت شعبه الزامی است." }
        database.withTransaction {
            val current = dao.byId(id) ?: error("شعبه پیدا نشد.")
            val from = runCatching { BranchLifecycleStatus.valueOf(current.status) }
                .getOrElse { if (current.isActive) BranchLifecycleStatus.ACTIVE else BranchLifecycleStatus.CLOSED }
            if (from == status) return@withTransaction
            require(status in allowedTransitions(from)) { "تغییر وضعیت شعبه از ${from.name} به ${status.name} مجاز نیست." }
            val now = clock()
            if (status == BranchLifecycleStatus.CLOSED) {
                // Persist the transient state before performing dependency checks so concurrent new work is rejected by DB triggers.
                check(dao.update(current.copy(status = BranchLifecycleStatus.CLOSING.name, isActive = false, updatedAtEpochMillis = now)) == 1)
                val blockers = closureBlockers(id)
                require(blockers.isEmpty()) { "بستن شعبه ممکن نیست: ${blockers.joinToString("، ")}" }
                val closing = dao.byId(id) ?: error("شعبه پیدا نشد.")
                check(dao.update(closing.copy(status = BranchLifecycleStatus.CLOSED.name, isActive = false, updatedAtEpochMillis = now)) == 1)
            } else {
                val active = status in setOf(BranchLifecycleStatus.ACTIVE, BranchLifecycleStatus.SUSPENDED, BranchLifecycleStatus.CLOSING)
                check(dao.update(current.copy(status = status.name, isActive = active, updatedAtEpochMillis = now)) == 1)
            }
            val persisted = dao.byId(id) ?: error("شعبه پیدا نشد.")
            audit.appendAuthorized(
                authorizer, "STATUS_CHANGE", "BRANCH", id, "تغییر وضعیت شعبه",
                now, reason = normalizedReason,
                beforeSnapshot = "status=${from.name};active=${current.isActive}",
                afterSnapshot = "status=${persisted.status};active=${persisted.isActive}",
                correlationId = "branch:$id:status:$now",
            )
        }
    }

    private suspend fun closureBlockers(branchId: Long): List<String> = buildList {
        val phase3 = database.phase3Dao()
        if (phase3.branchStockDependencies(branchId) > 0) add("موجودی/رزرو/درراه")
        if (phase3.branchOpenSalesDependencies(branchId) > 0) add("فروش باز")
        if (phase3.branchOpenPurchaseDependencies(branchId) > 0) add("خرید باز")
        if (phase3.branchOpenPayableDependencies(branchId) > 0) add("حساب پرداختنی باز")
        if (phase3.branchOpenReceivableDependencies(branchId) > 0) add("حساب دریافتنی باز")
        if (phase3.branchEmployeeDependencies(branchId) > 0) add("پرسنل فعال")
        if (phase3.branchPayrollDependencies(branchId) > 0) add("حقوق باز")
        if (phase3.branchAssetDependencies(branchId) > 0) add("دارایی فعال")
    }

    private fun allowedTransitions(from: BranchLifecycleStatus): Set<BranchLifecycleStatus> = when (from) {
        BranchLifecycleStatus.ACTIVE -> setOf(BranchLifecycleStatus.SUSPENDED, BranchLifecycleStatus.CLOSING, BranchLifecycleStatus.CLOSED)
        BranchLifecycleStatus.SUSPENDED -> setOf(BranchLifecycleStatus.ACTIVE, BranchLifecycleStatus.CLOSING, BranchLifecycleStatus.CLOSED)
        BranchLifecycleStatus.CLOSING -> setOf(BranchLifecycleStatus.ACTIVE, BranchLifecycleStatus.CLOSED)
        BranchLifecycleStatus.CLOSED -> setOf(BranchLifecycleStatus.ACTIVE, BranchLifecycleStatus.ARCHIVED)
        BranchLifecycleStatus.ARCHIVED -> emptySet()
    }
}

internal fun BranchEntity.toRecord() = BranchRecord(
    id = id,
    globalId = globalId,
    organizationId = organizationId,
    code = code,
    name = name,
    isActive = isActive,
    status = runCatching { BranchLifecycleStatus.valueOf(status) }.getOrElse { if (isActive) BranchLifecycleStatus.ACTIVE else BranchLifecycleStatus.CLOSED },
)

private fun String.toLegacyAlias(branchId: Long, now: Long): BranchLegacyAliasEntity {
    val display = trim()
    require(display.isNotEmpty()) { "branch_alias_blank" }
    return BranchLegacyAliasEntity(
        branchId = branchId,
        aliasName = display,
        normalizedAlias = display.lowercase(),
        createdAtEpochMillis = now,
    )
}
