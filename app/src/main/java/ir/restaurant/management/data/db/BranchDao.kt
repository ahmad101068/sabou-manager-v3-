package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Insert
    suspend fun insert(entity: BranchEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacyAlias(entity: BranchLegacyAliasEntity): Long

    @Query("SELECT aliasName FROM branch_legacy_aliases WHERE branchId = :branchId ORDER BY id")
    suspend fun legacyAliases(branchId: Long): List<String>

    @Update
    suspend fun update(entity: BranchEntity): Int

    @Query("SELECT * FROM branches WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): BranchEntity?

    @Query("SELECT * FROM branches WHERE globalId = :globalId LIMIT 1")
    suspend fun byGlobalId(globalId: String): BranchEntity?

    @Query("SELECT * FROM branches WHERE code = :code AND ((organizationId IS NULL AND :organizationId IS NULL) OR organizationId = :organizationId) LIMIT 1")
    suspend fun byOrganizationAndCode(organizationId: Long?, code: String): BranchEntity?

    @Query("SELECT * FROM branches WHERE id = :id AND isActive = 1 LIMIT 1")
    suspend fun activeById(id: Long): BranchEntity?

    @Query("SELECT * FROM branches WHERE isActive = 1 ORDER BY name, id")
    fun observeActive(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY isActive DESC, name, id")
    fun observeAll(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE isActive = 1 ORDER BY name, id")
    suspend fun listActive(): List<BranchEntity>

    @Query("SELECT * FROM branches WHERE lower(trim(name)) = lower(trim(:legacyKey)) OR (code IS NOT NULL AND lower(trim(code)) = lower(trim(:legacyKey))) ORDER BY id")
    suspend fun legacyCandidates(legacyKey: String): List<BranchEntity>
}
