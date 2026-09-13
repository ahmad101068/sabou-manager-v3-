package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id AND isActive = 1")
    suspend fun activeById(id: Long): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE sku = :sku COLLATE NOCASE LIMIT 1")
    suspend fun bySku(sku: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE primaryBarcode = :barcode LIMIT 1")
    suspend fun byPrimaryBarcode(barcode: String): InventoryItemEntity?

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE (:includeInactive = 1 OR isActive = 1)
          AND (:query = '' OR name LIKE '%' || :query || '%' COLLATE NOCASE
               OR sku LIKE '%' || :query || '%' COLLATE NOCASE
               OR primaryBarcode = :query)
          AND (:category IS NULL OR category = :category)
          AND (:itemType IS NULL OR itemType = :itemType)
          AND (:supplierId IS NULL OR supplierId = :supplierId)
        ORDER BY isActive DESC, name, id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(
        query: String,
        category: String?,
        itemType: String?,
        supplierId: Long?,
        includeInactive: Boolean,
        limit: Int,
        offset: Int,
    ): List<InventoryItemEntity>

    @Query(
        """
        UPDATE inventory_items
        SET stockMicros = :stockMicros,
            inventoryValueRial = :inventoryValueRial,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :itemId AND isActive = 1
        """,
    )
    suspend fun updateValuation(
        itemId: Long,
        stockMicros: Long,
        inventoryValueRial: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_items
        SET stockMicros = :nextStockMicros,
            inventoryValueRial = :nextInventoryValueRial,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :itemId
          AND isActive = 1
          AND stockMicros = :expectedStockMicros
          AND inventoryValueRial = :expectedInventoryValueRial
        """,
    )
    suspend fun compareAndSetValuation(
        itemId: Long,
        expectedStockMicros: Long,
        expectedInventoryValueRial: Long,
        nextStockMicros: Long,
        nextInventoryValueRial: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_items
        SET stockMicros = :stockMicros,
            inventoryValueRial = :inventoryValueRial,
            isActive = 1,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :itemId
        """,
    )
    suspend fun restoreValuation(
        itemId: Long,
        stockMicros: Long,
        inventoryValueRial: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_items
        SET stockMicros = :nextStockMicros,
            inventoryValueRial = :nextInventoryValueRial,
            isActive = 1,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :itemId
          AND stockMicros = :expectedStockMicros
          AND inventoryValueRial = :expectedInventoryValueRial
        """,
    )
    suspend fun compareAndRestoreValuation(
        itemId: Long,
        expectedStockMicros: Long,
        expectedInventoryValueRial: Long,
        nextStockMicros: Long,
        nextInventoryValueRial: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name")
    suspend fun activeItems(): List<InventoryItemEntity>

    @Query(
        """
        SELECT COALESCE(SUM(inventoryValueRial), 0)
        FROM inventory_items
        WHERE isActive = 1
        """,
    )
    fun observeInventoryValueRial(): Flow<Long>

    @Insert
    suspend fun insert(entity: InventoryItemEntity): Long

    @Update
    suspend fun update(entity: InventoryItemEntity): Int

    @Query(
        """
        UPDATE inventory_items
        SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
          AND isActive = 1
          AND stockMicros = 0
          AND inventoryValueRial = 0
        """,
    )
    suspend fun deactivate(id: Long, updatedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE inventory_items
        SET supplierId = NULL, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE supplierId = :supplierId
        """,
    )
    suspend fun clearSupplierReference(supplierId: Long, updatedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE isActive = 1
          AND alertEnabled = 1
          AND stockMicros <= alertThresholdMicros
        ORDER BY stockMicros ASC, name ASC
        """,
    )
    fun observeLowStock(): Flow<List<InventoryItemEntity>>
}
