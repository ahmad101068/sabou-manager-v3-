package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface InventoryReadDao {
    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(inventoryValueRial) FROM inventory_items WHERE isActive=1),0) AS totalInventoryValueRial,
          (SELECT COUNT(*) FROM inventory_items WHERE isActive=1) AS activeItemCount,
          (SELECT COUNT(*) FROM (
             SELECT item.id FROM inventory_items item
             INNER JOIN inventory_balances balance ON balance.itemId=item.id
             WHERE item.isActive=1 AND item.alertEnabled=1
             GROUP BY item.id,item.reorderPointMicros
             HAVING SUM(balance.onHandMicros-balance.reservedMicros-balance.damagedMicros-balance.quarantinedMicros)<=item.reorderPointMicros
          )) AS lowStockItemCount,
          (SELECT COUNT(*) FROM (
             SELECT item.id FROM inventory_items item
             INNER JOIN inventory_balances balance ON balance.itemId=item.id
             WHERE item.isActive=1
             GROUP BY item.id
             HAVING SUM(balance.onHandMicros)<=0
          )) AS outOfStockItemCount,
          (SELECT COUNT(*) FROM inventory_lots
             WHERE quantityMicros>0 AND status='ACTIVE' AND expiryEpochDay BETWEEN :asOfEpochDay AND :expiryToEpochDay) AS expiringLotCount,
          (SELECT COUNT(*) FROM inventory_lots
             WHERE quantityMicros>0 AND (status='EXPIRED' OR (expiryEpochDay IS NOT NULL AND expiryEpochDay<:asOfEpochDay))) AS expiredLotCount,
          (SELECT COUNT(*) FROM inventory_lots
             WHERE quantityMicros>0 AND status='QUARANTINED') AS quarantinedLotCount,
          COALESCE((SELECT SUM(valueRial) FROM inventory_waste_documents
             WHERE status='POSTED' AND wasteEpochDay BETWEEN :reportingFromEpochDay AND :asOfEpochDay),0) AS wasteCostRial,
          COALESCE((SELECT SUM(ABS(COALESCE(line.varianceValueRial,0)))
             FROM inventory_count_lines line
             INNER JOIN inventory_count_sessions session ON session.id=line.sessionId
             WHERE session.status='POSTED' AND session.businessEpochDay BETWEEN :reportingFromEpochDay AND :asOfEpochDay),0) AS inventoryVarianceRial,
          (SELECT COUNT(*) FROM stock_transfers WHERE status IN ('REQUESTED','APPROVED','IN_TRANSIT')) AS pendingTransferCount,
          (SELECT COUNT(*) FROM inventory_count_sessions
             WHERE status IN ('DRAFT','OPEN','COUNTING','RECOUNT_REQUIRED','PENDING_APPROVAL','APPROVED')) AS pendingCountSessionCount
        """,
    )
    suspend fun dashboard(
        asOfEpochDay: Long,
        expiryToEpochDay: Long,
        reportingFromEpochDay: Long,
    ): InventoryDashboardRow

    @Query(
        """
        SELECT item.id AS itemId,item.name AS itemName,item.sku AS sku,item.unit AS baseUnit,
               location.id AS locationId,location.name AS locationName,
               COALESCE(balance.onHandMicros,0) AS onHandMicros,
               COALESCE(balance.reservedMicros,0) AS reservedMicros,
               COALESCE(balance.inTransitMicros,0) AS inTransitMicros,
               COALESCE(balance.damagedMicros,0) AS damagedMicros,
               COALESCE(balance.quarantinedMicros,0) AS quarantinedMicros,
               COALESCE(balance.inventoryValueRial,0) AS inventoryValueRial,
               item.reorderPointMicros AS reorderPointMicros
        FROM inventory_balances balance
        INNER JOIN inventory_items item ON item.id=balance.itemId
        INNER JOIN storage_locations location ON location.id=balance.locationId AND location.isActive=1
        WHERE (:includeInactive=1 OR item.isActive=1)
          AND (:locationId IS NULL OR location.id=:locationId)
          AND (:query='' OR item.name LIKE '%'||:query||'%' COLLATE NOCASE
               OR item.sku LIKE '%'||:query||'%' COLLATE NOCASE
               OR item.primaryBarcode=:query)
          AND (
            :stockStatus='ALL'
            OR (:stockStatus='OUT_OF_STOCK' AND balance.onHandMicros<=0)
            OR (:stockStatus='LOW' AND balance.onHandMicros>0
                AND balance.onHandMicros-balance.reservedMicros-balance.damagedMicros-balance.quarantinedMicros<=item.reorderPointMicros)
            OR (:stockStatus='HEALTHY' AND balance.onHandMicros>0
                AND balance.onHandMicros-balance.reservedMicros-balance.damagedMicros-balance.quarantinedMicros>item.reorderPointMicros)
          )
        ORDER BY CASE WHEN balance.onHandMicros<=0 THEN 0
                      WHEN balance.onHandMicros-balance.reservedMicros-balance.damagedMicros-balance.quarantinedMicros<=item.reorderPointMicros THEN 1
                      ELSE 2 END,
                 item.name,location.name,item.id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun balances(
        query: String,
        locationId: Long?,
        stockStatus: String,
        includeInactive: Boolean,
        limit: Int,
        offset: Int,
    ): List<InventoryBalanceViewRow>

    @Query(
        """
        SELECT movement.id AS id,movement.itemId AS itemId,item.name AS itemName,item.unit AS baseUnit,
               movement.movementType AS movementType,movement.quantityDeltaMicros AS quantityDeltaMicros,
               movement.valueDeltaRial AS valueDeltaRial,movement.unitCostRial AS unitCostRial,
               movement.movementEpochDay AS businessEpochDay,movement.createdAtEpochMillis AS createdAtEpochMillis,
               movement.locationId AS locationId,location.name AS locationName,
               (SELECT GROUP_CONCAT(lot.lotCode, ', ')
                  FROM inventory_lot_consumptions consumption
                  INNER JOIN inventory_lots lot ON lot.id=consumption.lotId
                  WHERE consumption.stockMovementId=movement.id) AS lotNumbers,
               movement.referenceType AS sourceType,movement.referenceId AS sourceId,
               movement.actorId AS actorId,movement.correlationId AS correlationId,
               movement.reversalOfMovementId AS reversalOfMovementId,movement.notes AS reason
        FROM stock_movements movement
        INNER JOIN inventory_items item ON item.id=movement.itemId
        LEFT JOIN storage_locations location ON location.id=movement.locationId
        WHERE (:itemId IS NULL OR movement.itemId=:itemId)
          AND (:locationId IS NULL OR movement.locationId=:locationId)
          AND (:movementType IS NULL OR movement.movementType=:movementType)
          AND (:fromEpochDay IS NULL OR movement.movementEpochDay>=:fromEpochDay)
          AND (:toEpochDay IS NULL OR movement.movementEpochDay<=:toEpochDay)
        ORDER BY movement.movementEpochDay DESC,movement.id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun movements(
        itemId: Long?,
        locationId: Long?,
        movementType: String?,
        fromEpochDay: Long?,
        toEpochDay: Long?,
        limit: Int,
        offset: Int,
    ): List<InventoryMovementViewRow>
}

data class InventoryDashboardRow(
    val totalInventoryValueRial: Long,
    val activeItemCount: Int,
    val lowStockItemCount: Int,
    val outOfStockItemCount: Int,
    val expiringLotCount: Int,
    val expiredLotCount: Int,
    val quarantinedLotCount: Int,
    val wasteCostRial: Long,
    val inventoryVarianceRial: Long,
    val pendingTransferCount: Int,
    val pendingCountSessionCount: Int,
)

data class InventoryBalanceViewRow(
    val itemId: Long,
    val itemName: String,
    val sku: String,
    val baseUnit: String,
    val locationId: Long,
    val locationName: String,
    val onHandMicros: Long,
    val reservedMicros: Long,
    val inTransitMicros: Long,
    val damagedMicros: Long,
    val quarantinedMicros: Long,
    val inventoryValueRial: Long,
    val reorderPointMicros: Long,
)

data class InventoryMovementViewRow(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val baseUnit: String,
    val movementType: String,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val unitCostRial: Long,
    val businessEpochDay: Long,
    val createdAtEpochMillis: Long,
    val locationId: Long?,
    val locationName: String?,
    val lotNumbers: String?,
    val sourceType: String,
    val sourceId: Long,
    val actorId: Long?,
    val correlationId: String,
    val reversalOfMovementId: Long?,
    val reason: String,
)
