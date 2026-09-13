package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Backend ownership boundary for Phase 3. UI filtering is convenience only; every command must
 * still call this service before reading or mutating branch/location-owned business data.
 */
internal class LocalDataScopeService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
) {
    suspend fun requireBranch(branchId: Long, operational: Boolean = true): BranchEntity {
        require(branchId > 0) { "شعبه معتبر نیست." }
        val actor = authorizer.actorIdentity()
        val branch = database.branchDao().byId(branchId) ?: error("شعبه پیدا نشد.")
        if (actor.role != UserRole.OWNER) {
            require(database.phase3Dao().hasBranch(actor.id, branchId)) { "دسترسی به این شعبه مجاز نیست." }
        }
        if (operational) {
            require(branch.isActive && branch.status == "ACTIVE") { "شعبه برای عملیات جدید فعال نیست." }
        }
        return branch
    }

    suspend fun requireLocation(locationId: Long, expectedBranchId: Long? = null): StorageLocationEntity {
        require(locationId > 0) { "انبار/مکان نگهداری باید صریح انتخاب شود." }
        val actor = authorizer.actorIdentity()
        val location = database.inventoryLocationDao().activeById(locationId)
            ?: error("انبار/مکان نگهداری فعال پیدا نشد.")
        val branchId = location.branchId ?: error("انبار بدون شعبه برای عملیات عملیاتی مجاز نیست.")
        if (expectedBranchId != null) require(branchId == expectedBranchId) { "انبار انتخاب‌شده متعلق به شعبه عملیات نیست." }
        requireBranch(branchId)
        if (actor.role != UserRole.OWNER) {
            val grantCount = database.phase3Dao().warehouseGrantCount(actor.id)
            val warehouseStrict = actor.role == UserRole.STOREKEEPER || actor.role == UserRole.INVENTORY || grantCount > 0
            if (warehouseStrict) {
                require(grantCount > 0 && database.phase3Dao().hasWarehouse(actor.id, locationId)) {
                    "دسترسی به این انبار/مکان نگهداری مجاز نیست."
                }
            }
        }
        return location
    }

    suspend fun activeBranches(): List<BranchEntity> {
        val actor = authorizer.actorIdentity()
        return if (actor.role == UserRole.OWNER) database.branchDao().listActive()
        else database.phase3Dao().listActiveBranches(actor.id)
    }

    suspend fun activeLocations(): List<StorageLocationEntity> {
        val actor = authorizer.actorIdentity()
        if (actor.role == UserRole.OWNER) {
            return database.inventoryLocationDao().search("", null, false, 10_000, 0)
        }
        val strict = actor.role == UserRole.STOREKEEPER || actor.role == UserRole.INVENTORY || database.phase3Dao().warehouseGrantCount(actor.id) > 0
        return database.phase3Dao().listActiveLocations(actor.id, strict)
    }

    fun scopedBranches(): Flow<List<BranchEntity>> = database.securityDao().observeCurrentUser().flatMapLatest { user ->
        when {
            user == null || !user.isActive -> flowOf(emptyList())
            UserRole.fromStoredValue(user.role) == UserRole.OWNER -> database.branchDao().observeAll()
            else -> database.phase3Dao().observeBranches(user.id)
        }
    }

    fun scopedActiveBranches(): Flow<List<BranchEntity>> = scopedBranches().flatMapLatest { rows ->
        flowOf(rows.filter { it.isActive && it.status == "ACTIVE" })
    }

    fun scopedLocations(): Flow<List<StorageLocationEntity>> = database.securityDao().observeCurrentUser().flatMapLatest { user ->
        when {
            user == null || !user.isActive -> flowOf(emptyList())
            UserRole.fromStoredValue(user.role) == UserRole.OWNER -> database.inventoryLocationDao().observeAll()
            else -> {
                val role = UserRole.fromStoredValue(user.role)
                val strict = role == UserRole.STOREKEEPER || role == UserRole.INVENTORY
                database.phase3Dao().observeLocations(user.id, strict)
            }
        }
    }
}
