package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Inventory-owned ledger queries split from the legacy aggregate DAO file. */
@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(entity: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements WHERE idempotencyKey = :key LIMIT 1")
    suspend fun byIdempotencyKey(key: String): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE reversalOfMovementId = :movementId LIMIT 1")
    suspend fun reversalOf(movementId: Long): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE id = :movementId LIMIT 1")
    suspend fun byId(movementId: Long): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY movementEpochDay DESC, id DESC LIMIT 300")
    fun observeForItem(itemId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY movementEpochDay DESC, id DESC LIMIT 500")
    fun observeRecent(): Flow<List<StockMovementEntity>>

    @Query(
        """
        SELECT *
        FROM stock_movements
        WHERE referenceType = 'SALE'
          AND referenceId = :saleId
          AND movementType IN ('SALE_CONSUMPTION', 'DAILY_SALES_CONSUMPTION')
        ORDER BY id
        """,
    )
    suspend fun saleConsumptions(saleId: Long): List<StockMovementEntity>

    @Query(
        """
        SELECT *
        FROM stock_movements
        WHERE referenceType = 'DAILY_SALES'
          AND referenceId = :summaryId
          AND movementType = 'DAILY_SALES_CONSUMPTION'
        ORDER BY id
        """,
    )
    suspend fun dailySalesConsumptions(summaryId: Long): List<StockMovementEntity>

    @Query(
        """
        SELECT * FROM stock_movements
        WHERE referenceType = 'SALES_INVOICE'
          AND referenceId = :invoiceId
          AND movementType = 'SALES_INVOICE_CONSUMPTION'
        ORDER BY id
        """,
    )
    suspend fun invoiceSalesConsumptions(invoiceId: Long): List<StockMovementEntity>

    @Query(
        """
        SELECT i.id AS itemId, i.name AS itemName, i.unit AS unit,
               MAX(COALESCE(SUM(-sm.quantityDeltaMicros), 0), 0) AS usageMicros
        FROM inventory_items i
        LEFT JOIN stock_movements sm
          ON sm.itemId = i.id
         AND sm.movementEpochDay >= :fromEpochDay
         AND sm.movementType IN ('SALE_CONSUMPTION', 'DAILY_SALES_CONSUMPTION', 'SALES_INVOICE_CONSUMPTION', 'DAILY_SALES_REVERSAL', 'SALES_RETURN', 'SALES_VOID', 'WASTE')
        WHERE i.isActive = 1
        GROUP BY i.id, i.name, i.unit
        ORDER BY usageMicros DESC, i.name
        """,
    )
    fun observeUsageSince(fromEpochDay: Long): Flow<List<InventoryUsageRow>>

    @Query(
        """
        SELECT wd.id AS id, wd.itemId AS itemId, i.name AS itemName, i.unit AS unit,
               wd.quantityMicros AS quantityMicros, wd.valueRial AS valueRial,
               wd.wasteEpochDay AS wasteEpochDay,
               CASE WHEN wd.notes = '' THEN wd.reason ELSE wd.reason || ' — ' || wd.notes END AS reason
        FROM inventory_waste_documents wd
        INNER JOIN inventory_items i ON i.id = wd.itemId
        WHERE wd.status = 'POSTED'
        UNION ALL
        SELECT sm.id AS id, sm.itemId AS itemId, i.name AS itemName, i.unit AS unit,
               -sm.quantityDeltaMicros AS quantityMicros, -sm.valueDeltaRial AS valueRial,
               sm.movementEpochDay AS wasteEpochDay, sm.notes AS reason
        FROM stock_movements sm
        INNER JOIN inventory_items i ON i.id = sm.itemId
        WHERE sm.movementType = 'WASTE'
          AND NOT EXISTS (
              SELECT 1 FROM inventory_waste_documents wd
              WHERE wd.id = sm.referenceId AND sm.referenceType = 'WASTE'
          )
        ORDER BY wasteEpochDay DESC, id DESC
        LIMIT 200
        """,
    )
    fun observeWasteRecords(): Flow<List<WasteRow>>
}

data class InventoryUsageRow(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val usageMicros: Long,
)
data class WasteRow(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val valueRial: Long,
    val wasteEpochDay: Long,
    val reason: String,
)
