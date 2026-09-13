package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface InventoryReplenishmentDao {
    @Query(
        """
        SELECT i.id AS itemId,i.name AS itemName,i.unit AS unit,
               CASE WHEN :locationId IS NULL THEN NULL ELSE :locationId END AS locationId,
               CASE WHEN :locationId IS NULL THEN NULL ELSE MAX(location.name) END AS locationName,
               SUM(balance.onHandMicros) AS onHandMicros,
               SUM(balance.reservedMicros) AS reservedMicros,
               SUM(balance.damagedMicros) AS damagedMicros,
               SUM(balance.quarantinedMicros) AS quarantinedMicros,
               SUM(balance.inTransitMicros) AS inTransitMicros,
               i.minimumStockMicros AS minimumStockMicros,
               i.maximumStockMicros AS maximumStockMicros,
               i.safetyStockMicros AS safetyStockMicros,
               i.reorderPointMicros AS configuredReorderPointMicros,
               i.leadTimeDays AS itemLeadTimeDays,
               i.alertEnabled AS alertEnabled,
               policy.targetCoverDays AS legacyTargetCoverDays,
               policy.leadTimeDays AS legacyLeadTimeDays,
               policy.safetyStockMicros AS legacySafetyStockMicros,
               policy.orderMultipleMicros AS legacyOrderMultipleMicros,
               policy.isEnabled AS legacyPolicyEnabled,
               COALESCE(policy.preferredSupplierId,i.supplierId) AS preferredSupplierId,
               preferred.name AS preferredSupplierName,
               CASE WHEN :locationId IS NULL
                         OR MAX(CASE WHEN location.code='MAIN' THEN 1 ELSE 0 END)=1
                    THEN MAX(COALESCE((
                        SELECT SUM(orderLine.orderedQtyMicros-orderLine.receivedQtyMicros)
                        FROM purchase_order_lines orderLine
                        INNER JOIN purchase_orders purchaseOrder ON purchaseOrder.id=orderLine.purchaseOrderId
                        WHERE orderLine.itemId=i.id
                          AND purchaseOrder.status IN ('OPEN','PARTIALLY_RECEIVED')
                    ),0),0) ELSE 0 END AS onOrderMicros,
               COALESCE((
                   SELECT MAX(COALESCE(SUM(-movement.quantityDeltaMicros),0),0)
                   FROM stock_movements movement
                   WHERE movement.itemId=i.id
                     AND (:locationId IS NULL OR movement.locationId=:locationId)
                     AND movement.movementEpochDay BETWEEN :fromEpochDay AND :asOfEpochDay
                     AND (
                         movement.movementType IN (
                             'SALE_CONSUMPTION','DAILY_SALES_CONSUMPTION',
                             'DAILY_SALES_REVERSAL','RECIPE_CONSUMPTION'
                         )
                         OR (:includeWaste=1 AND movement.movementType='WASTE')
                     )
               ),0) AS usageMicros,
               COALESCE((
                   SELECT purchaseLine.unitCostRial
                   FROM purchase_lines purchaseLine
                   INNER JOIN purchases purchase ON purchase.id=purchaseLine.purchaseId
                   WHERE purchaseLine.itemId=i.id AND purchase.paymentStatus!='REVERSED'
                   ORDER BY purchase.purchaseEpochDay DESC,purchaseLine.id DESC LIMIT 1
               ),0) AS estimatedUnitCostRial,
               EXISTS(
                   SELECT 1 FROM purchase_requisition_lines requestLine
                   INNER JOIN purchase_requisitions request ON request.id=requestLine.requisitionId
                   WHERE requestLine.itemId=i.id
                     AND request.status IN ('SUBMITTED','PENDING_SECOND_APPROVAL','APPROVED')
               ) AS hasPendingRequisition
        FROM inventory_items i
        INNER JOIN inventory_balances balance ON balance.itemId=i.id
        INNER JOIN storage_locations location ON location.id=balance.locationId
        LEFT JOIN inventory_replenishment_policies policy ON policy.itemId=i.id
        LEFT JOIN suppliers preferred ON preferred.id=COALESCE(policy.preferredSupplierId,i.supplierId)
            AND preferred.isActive=1
        WHERE i.isActive=1 AND location.isActive=1
          AND (:itemId IS NULL OR i.id=:itemId)
          AND (:locationId IS NULL OR balance.locationId=:locationId)
        GROUP BY i.id
        ORDER BY i.name,i.id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun inputs(
        itemId: Long?,
        locationId: Long?,
        fromEpochDay: Long,
        asOfEpochDay: Long,
        includeWaste: Int,
        limit: Int,
        offset: Int,
    ): List<InventoryReplenishmentInputRow>
}

data class InventoryReplenishmentInputRow(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val locationId: Long?,
    val locationName: String?,
    val onHandMicros: Long,
    val reservedMicros: Long,
    val damagedMicros: Long,
    val quarantinedMicros: Long,
    val inTransitMicros: Long,
    val minimumStockMicros: Long,
    val maximumStockMicros: Long,
    val safetyStockMicros: Long,
    val configuredReorderPointMicros: Long,
    val itemLeadTimeDays: Int,
    val alertEnabled: Boolean,
    val legacyTargetCoverDays: Int?,
    val legacyLeadTimeDays: Int?,
    val legacySafetyStockMicros: Long?,
    val legacyOrderMultipleMicros: Long?,
    val legacyPolicyEnabled: Boolean?,
    val preferredSupplierId: Long?,
    val preferredSupplierName: String?,
    val onOrderMicros: Long,
    val usageMicros: Long,
    val estimatedUnitCostRial: Long,
    val hasPendingRequisition: Boolean,
)
