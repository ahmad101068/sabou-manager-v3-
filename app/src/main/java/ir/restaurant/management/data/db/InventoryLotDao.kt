package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryLotDao {
    @Insert
    suspend fun insert(entity: InventoryLotEntity): Long

    @Query("SELECT * FROM inventory_lots WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): InventoryLotEntity?

    @Query(
        "SELECT * FROM inventory_lots WHERE itemId = :itemId AND locationId = :locationId " +
            "AND lotCode = :lotCode LIMIT 1",
    )
    suspend fun byNaturalKey(itemId: Long, locationId: Long, lotCode: String): InventoryLotEntity?

    @Query(
        """
        SELECT * FROM inventory_lots
        WHERE itemId = :itemId AND locationId = :locationId
          AND status != 'LEGACY_UNKNOWN'
          AND (quantityMicros > 0 OR status != 'DEPLETED')
        ORDER BY id
        """,
    )
    suspend fun countableAtLocation(itemId: Long, locationId: Long): List<InventoryLotEntity>

    @Query("SELECT * FROM inventory_lots WHERE barcode = :barcode ORDER BY id LIMIT 1")
    suspend fun byBarcode(barcode: String): InventoryLotEntity?

    @Query("SELECT COALESCE(SUM(quantityMicros), 0) FROM inventory_lots WHERE itemId = :itemId")
    suspend fun allocatedQuantity(itemId: Long): Long

    @Query(
        "SELECT COALESCE(SUM(quantityMicros), 0) FROM inventory_lots " +
            "WHERE itemId = :itemId AND locationId = :locationId",
    )
    suspend fun allocatedQuantityAtLocation(itemId: Long, locationId: Long): Long

    @Query(
        "SELECT COALESCE(SUM(quantityMicros),0) FROM inventory_lots " +
            "WHERE itemId=:itemId AND locationId=:locationId AND quantityMicros>0 " +
            "AND status='ACTIVE' AND expiryEpochDay IS NOT NULL AND expiryEpochDay < :businessEpochDay",
    )
    suspend fun expiredAvailableQuantity(itemId: Long, locationId: Long, businessEpochDay: Long): Long

    @Query(
        """
        SELECT * FROM inventory_lots
        WHERE itemId = :itemId AND locationId = :locationId AND quantityMicros > 0
        ORDER BY CASE WHEN expiryEpochDay IS NULL THEN 1 ELSE 0 END,
                 expiryEpochDay, receivedEpochDay, id
        """,
    )
    suspend fun allocationCandidates(itemId: Long, locationId: Long): List<InventoryLotEntity>

    @Query(
        """
        SELECT l.id, l.itemId, i.name AS itemName, l.locationId, location.name AS locationName,
               l.lotCode, l.supplierLotNumber, l.receivedEpochDay, l.productionEpochDay,
               l.expiryEpochDay, l.quantityMicros, l.initialQuantityMicros, l.unitCostRial,
               l.status, l.barcode, l.sourceReceiptId, l.globalId, l.correlationId
        FROM inventory_lots l
        INNER JOIN inventory_items i ON i.id = l.itemId
        INNER JOIN storage_locations location ON location.id = l.locationId
        WHERE (:itemId IS NULL OR l.itemId = :itemId)
          AND (:locationId IS NULL OR l.locationId = :locationId)
          AND (:status IS NULL OR l.status = :status)
          AND (:expiryFrom IS NULL OR l.expiryEpochDay >= :expiryFrom)
          AND (:expiryTo IS NULL OR l.expiryEpochDay <= :expiryTo)
        ORDER BY CASE WHEN l.expiryEpochDay IS NULL THEN 1 ELSE 0 END,
                 l.expiryEpochDay, i.name, l.id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(
        itemId: Long?,
        locationId: Long?,
        status: String?,
        expiryFrom: Long?,
        expiryTo: Long?,
        limit: Int,
        offset: Int,
    ): List<InventoryLotRow>

    @Query(
        """
        SELECT l.id, l.itemId, i.name AS itemName, l.locationId, location.name AS locationName,
               l.lotCode, l.supplierLotNumber, l.receivedEpochDay, l.productionEpochDay,
               l.expiryEpochDay, l.quantityMicros, l.initialQuantityMicros, l.unitCostRial,
               l.status, l.barcode, l.sourceReceiptId, l.globalId, l.correlationId
        FROM inventory_lots l
        INNER JOIN inventory_items i ON i.id = l.itemId
        INNER JOIN storage_locations location ON location.id = l.locationId
        WHERE l.quantityMicros > 0
        ORDER BY CASE WHEN l.status = 'ACTIVE' THEN 0 ELSE 1 END,
                 CASE WHEN l.expiryEpochDay IS NULL THEN 1 ELSE 0 END,
                 l.expiryEpochDay, i.name, l.id
        """,
    )
    fun observeActiveStock(): Flow<List<InventoryLotRow>>

    @Query(
        """
        UPDATE inventory_lots
        SET quantityMicros = :nextQuantityMicros,
            status = CASE WHEN :nextQuantityMicros = 0 THEN 'DEPLETED' ELSE status END,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
          AND quantityMicros = :expectedQuantityMicros
          AND status = :expectedStatus
          AND :nextQuantityMicros >= 0
          AND :nextQuantityMicros <= initialQuantityMicros
          AND status NOT IN ('DEPLETED', 'LEGACY_UNKNOWN')
        """,
    )
    suspend fun compareAndSetQuantity(
        id: Long,
        expectedQuantityMicros: Long,
        expectedStatus: String,
        nextQuantityMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_lots
        SET quantityMicros = quantityMicros + :quantityMicros,
            initialQuantityMicros = initialQuantityMicros + :quantityMicros,
            status = 'ACTIVE',
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND quantityMicros = :expectedQuantityMicros
          AND status NOT IN ('QUARANTINED', 'EXPIRED', 'BLOCKED', 'LEGACY_UNKNOWN')
        """,
    )
    suspend fun addTransferredQuantity(
        id: Long,
        expectedQuantityMicros: Long,
        quantityMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_lots
        SET quantityMicros = :nextQuantityMicros,
            initialQuantityMicros = CASE WHEN :nextQuantityMicros > initialQuantityMicros THEN :nextQuantityMicros ELSE initialQuantityMicros END,
            status = CASE
                WHEN :nextQuantityMicros = 0 THEN 'DEPLETED'
                WHEN status = 'DEPLETED' THEN 'ACTIVE'
                ELSE status
            END,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id
          AND quantityMicros = :expectedQuantityMicros
          AND status != 'LEGACY_UNKNOWN'
          AND :nextQuantityMicros >= 0
        """,
    )
    suspend fun compareAndSetCountQuantity(
        id: Long,
        expectedQuantityMicros: Long,
        nextQuantityMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_lots
        SET quantityMicros = :nextQuantityMicros,
            status = :nextStatus,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND quantityMicros = :expectedQuantityMicros
          AND :nextQuantityMicros <= initialQuantityMicros
          AND status IN ('ACTIVE', 'QUARANTINED', 'EXPIRED', 'BLOCKED', 'DEPLETED')
        """,
    )
    suspend fun restoreConsumedQuantity(
        id: Long,
        expectedQuantityMicros: Long,
        nextQuantityMicros: Long,
        nextStatus: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_lots
        SET status = :nextStatus, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND status = :expectedStatus AND quantityMicros = :expectedQuantityMicros
        """,
    )
    suspend fun compareAndSetStatus(
        id: Long,
        expectedStatus: String,
        nextStatus: String,
        expectedQuantityMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Insert
    suspend fun insertConsumption(entity: InventoryLotConsumptionEntity): Long

    @Query(
        "SELECT * FROM inventory_lot_consumptions WHERE stockMovementId = :stockMovementId " +
            "AND reversedQuantityMicros < quantityMicros ORDER BY id",
    )
    suspend fun consumptions(stockMovementId: Long): List<InventoryLotConsumptionEntity>

    @Query(
        "UPDATE inventory_lot_consumptions SET reversedQuantityMicros = quantityMicros " +
            "WHERE id = :id AND reversedQuantityMicros < quantityMicros",
    )
    suspend fun markConsumptionReversed(id: Long): Int

    @Query(
        """
        UPDATE inventory_lot_consumptions
        SET reversedQuantityMicros = :nextReversedQuantityMicros
        WHERE id = :id
          AND reversedQuantityMicros = :expectedReversedQuantityMicros
          AND :nextReversedQuantityMicros > :expectedReversedQuantityMicros
          AND :nextReversedQuantityMicros <= quantityMicros
        """,
    )
    suspend fun advanceConsumptionReversal(
        id: Long,
        expectedReversedQuantityMicros: Long,
        nextReversedQuantityMicros: Long,
    ): Int

    @Query(
        """
        SELECT l.itemId AS itemId, l.locationId AS locationId,
               COALESCE(SUM(l.quantityMicros), 0) AS lotQuantityMicros,
               b.onHandMicros AS balanceQuantityMicros
        FROM inventory_lots l
        INNER JOIN inventory_items i ON i.id = l.itemId AND i.trackLot = 1
        INNER JOIN inventory_balances b ON b.itemId = l.itemId AND b.locationId = l.locationId
        GROUP BY l.itemId, l.locationId
        HAVING COALESCE(SUM(l.quantityMicros), 0) != b.onHandMicros
        ORDER BY l.itemId, l.locationId
        """,
    )
    suspend fun lotBalanceMismatches(): List<InventoryLotBalanceMismatchRow>

    @Query(
        """
        SELECT * FROM inventory_lots
        WHERE status = 'ACTIVE' AND expiryEpochDay IS NOT NULL
          AND expiryEpochDay < :businessEpochDay AND quantityMicros > 0
        ORDER BY expiryEpochDay, id
        """,
    )
    suspend fun expiredActiveLots(businessEpochDay: Long): List<InventoryLotEntity>

    @Query(
        """
        SELECT l.* FROM inventory_lots l
        INNER JOIN inventory_items item ON item.id = l.itemId
        WHERE l.quantityMicros < 0 OR l.initialQuantityMicros < l.quantityMicros OR l.unitCostRial < 0
           OR l.status NOT IN ('ACTIVE','QUARANTINED','EXPIRED','DEPLETED','BLOCKED')
           OR (l.quantityMicros = 0 AND l.status != 'DEPLETED')
           OR (l.quantityMicros > 0 AND l.status = 'DEPLETED')
           OR (item.trackExpiry = 1 AND l.expiryEpochDay IS NULL)
           OR (l.productionEpochDay IS NOT NULL AND l.productionEpochDay > l.receivedEpochDay)
           OR (l.expiryEpochDay IS NOT NULL AND l.expiryEpochDay < COALESCE(l.productionEpochDay,l.receivedEpochDay))
        ORDER BY l.id
        """,
    )
    suspend fun invalidLots(): List<InventoryLotEntity>

    @Query(
        """
        SELECT l.* FROM inventory_lots l
        LEFT JOIN inventory_items item ON item.id = l.itemId
        LEFT JOIN storage_locations location ON location.id = l.locationId
        WHERE item.id IS NULL OR location.id IS NULL
        ORDER BY l.id
        """,
    )
    suspend fun orphanLots(): List<InventoryLotEntity>
}

data class InventoryLotBalanceMismatchRow(
    val itemId: Long,
    val locationId: Long,
    val lotQuantityMicros: Long,
    val balanceQuantityMicros: Long,
)
