package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryLocationDao {
    @Insert
    suspend fun insert(entity: StorageLocationEntity): Long

    @Update
    suspend fun update(entity: StorageLocationEntity): Int

    @Query("SELECT * FROM storage_locations WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): StorageLocationEntity?

    @Query("SELECT * FROM storage_locations WHERE id = :id AND isActive = 1 LIMIT 1")
    suspend fun activeById(id: Long): StorageLocationEntity?

    @Query("SELECT * FROM storage_locations WHERE code = :code COLLATE NOCASE LIMIT 1")
    suspend fun byCode(code: String): StorageLocationEntity?

    @Query("SELECT * FROM storage_locations WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): StorageLocationEntity?

    @Query("SELECT id FROM storage_locations WHERE code = 'MAIN' AND isActive = 1 LIMIT 1")
    suspend fun defaultLocationId(): Long?

    @Query(
        """
        SELECT * FROM storage_locations
        WHERE (:includeInactive = 1 OR isActive = 1)
          AND (:query = '' OR code LIKE '%' || :query || '%' COLLATE NOCASE OR name LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:type IS NULL OR kind = :type)
        ORDER BY isActive DESC, name, id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(
        query: String,
        type: String?,
        includeInactive: Boolean,
        limit: Int,
        offset: Int,
    ): List<StorageLocationEntity>

    @Query("SELECT * FROM storage_locations ORDER BY isActive DESC, name, id")
    fun observeAll(): Flow<List<StorageLocationEntity>>
}
