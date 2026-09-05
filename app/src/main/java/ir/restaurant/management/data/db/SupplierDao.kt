package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun byNormalizedName(normalizedName: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE legalId = :legalId LIMIT 1")
    suspend fun byLegalId(legalId: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE bankIban = :bankIban LIMIT 1")
    suspend fun byBankIban(bankIban: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE phone = :phone AND trim(phone) <> '' ORDER BY isActive DESC, id LIMIT 1")
    suspend fun byPhone(phone: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE id = :id AND isActive = 1")
    suspend fun activeById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<SupplierEntity>>

    @Insert
    suspend fun insert(entity: SupplierEntity): Long

    @Update
    suspend fun update(entity: SupplierEntity): Int

    @Query(
        """
        UPDATE suppliers
        SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND isActive = 1
        """,
    )
    suspend fun deactivate(id: Long, updatedAtEpochMillis: Long): Int
}
