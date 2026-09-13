package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface InventoryControlDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWasteDocument(entity: InventoryWasteDocumentEntity): Long

    @Query("SELECT * FROM inventory_waste_documents WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun wasteDocumentByIdempotencyKey(idempotencyKey: String): InventoryWasteDocumentEntity?

    @Query("SELECT * FROM inventory_waste_documents WHERE id = :id LIMIT 1")
    suspend fun wasteDocument(id: Long): InventoryWasteDocumentEntity?

    @Query("SELECT * FROM inventory_waste_documents WHERE postCommandId = :commandId LIMIT 1")
    suspend fun wasteDocumentByPostCommand(commandId: String): InventoryWasteDocumentEntity?

    @Query(
        """SELECT * FROM inventory_waste_documents
        WHERE (:status IS NULL OR status=:status)
          AND (:locationId IS NULL OR locationId=:locationId)
          AND wasteEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY wasteEpochDay DESC,id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun searchWasteDocuments(
        status: String?,
        locationId: Long?,
        fromEpochDay: Long,
        toEpochDay: Long,
        limit: Int,
        offset: Int,
    ): List<InventoryWasteDocumentEntity>

    @Query(
        """UPDATE inventory_waste_documents
        SET status='APPROVED',approvedByActorId=:actorId,approvedAtEpochMillis=:now,
            updatedAtEpochMillis=:now
        WHERE id=:id AND status='PENDING_APPROVAL'""",
    )
    suspend fun approveWaste(id: Long, actorId: Long, now: Long): Int

    @Query(
        """UPDATE inventory_waste_documents
        SET status='POSTED',postCommandId=:commandId,postedByActorId=:actorId,
            postedAtEpochMillis=:now,updatedAtEpochMillis=:now
        WHERE id=:id AND status='APPROVED' AND postCommandId IS NULL""",
    )
    suspend fun markWastePosted(id: Long, commandId: String, actorId: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCount(entity: InventoryCountEntity): Long

    @Query("SELECT * FROM inventory_counts WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun countByIdempotencyKey(idempotencyKey: String): InventoryCountEntity?

    @Query("SELECT * FROM inventory_counts ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecentCounts(limit: Int = 100): Flow<List<InventoryCountEntity>>

    @Query("SELECT * FROM inventory_counts WHERE countEpochDay = :epochDay ORDER BY createdAtEpochMillis DESC, id DESC")
    suspend fun countsOnDay(epochDay: Long): List<InventoryCountEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_period_closures WHERE status = 'CLOSED' AND fromEpochDay <= :toEpochDay AND toEpochDay >= :fromEpochDay)")
    suspend fun closureOverlaps(fromEpochDay: Long, toEpochDay: Long): Boolean

    @Query("SELECT MAX(toEpochDay) FROM inventory_period_closures WHERE status = 'CLOSED'")
    suspend fun lastClosedEpochDay(): Long?

    @Query("SELECT * FROM inventory_period_closures ORDER BY toEpochDay DESC, id DESC")
    fun observeClosures(): Flow<List<InventoryPeriodClosureEntity>>

    @Query("SELECT * FROM inventory_period_closures WHERE id = :closureId LIMIT 1")
    fun observeClosure(closureId: Long): Flow<InventoryPeriodClosureEntity?>

    @Query("SELECT * FROM inventory_period_closures WHERE id = :closureId LIMIT 1")
    suspend fun closureById(closureId: Long): InventoryPeriodClosureEntity?

    @Query("SELECT * FROM inventory_period_closures WHERE fromEpochDay = :fromEpochDay AND toEpochDay = :toEpochDay LIMIT 1")
    suspend fun closureByRange(fromEpochDay: Long, toEpochDay: Long): InventoryPeriodClosureEntity?

    @Query("SELECT * FROM inventory_period_closures WHERE status = 'CLOSED' ORDER BY toEpochDay DESC, id DESC LIMIT 1")
    suspend fun latestClosedClosure(): InventoryPeriodClosureEntity?

    @Query("SELECT * FROM inventory_period_closure_lines WHERE closureId = :closureId ORDER BY itemNameSnapshot, id")
    fun observeClosureLines(closureId: Long): Flow<List<InventoryPeriodClosureLineEntity>>

    @Query(
        """
        SELECT itemId,
               COALESCE(SUM(quantityDeltaMicros),0) AS netQuantityMicros,
               COALESCE(SUM(valueDeltaRial),0) AS netValueRial,
               COALESCE(SUM(CASE WHEN movementType IN ('PURCHASE','GOODS_RECEIPT','PURCHASE_RETURN','PURCHASE_REVERSAL') THEN quantityDeltaMicros ELSE 0 END),0) AS netPurchaseQuantityMicros,
               COALESCE(SUM(CASE WHEN movementType IN ('PURCHASE','GOODS_RECEIPT','PURCHASE_RETURN','PURCHASE_REVERSAL') THEN valueDeltaRial ELSE 0 END),0) AS netPurchaseValueRial,
               COALESCE(SUM(CASE WHEN movementType = 'INVENTORY_COUNT' THEN quantityDeltaMicros ELSE 0 END),0) AS countAdjustmentQuantityMicros,
               COALESCE(SUM(CASE WHEN movementType = 'INVENTORY_COUNT' THEN valueDeltaRial ELSE 0 END),0) AS countAdjustmentValueRial
        FROM stock_movements
        WHERE movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY itemId
        """,
    )
    suspend fun movementTotals(fromEpochDay: Long, toEpochDay: Long): List<InventoryMovementTotalsRow>

    @Insert
    suspend fun insertClosure(entity: InventoryPeriodClosureEntity): Long

    @Update
    suspend fun updateClosure(entity: InventoryPeriodClosureEntity): Int

    @Query("DELETE FROM inventory_period_closure_lines WHERE closureId = :closureId")
    suspend fun deleteClosureLines(closureId: Long): Int

    @Insert
    suspend fun insertClosureLines(entities: List<InventoryPeriodClosureLineEntity>)
}
