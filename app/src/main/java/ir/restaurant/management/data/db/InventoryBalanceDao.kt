package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryBalanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initialize(entity: InventoryBalanceEntity): Long

    @Query("SELECT * FROM inventory_balances WHERE itemId = :itemId AND locationId = :locationId LIMIT 1")
    suspend fun byKey(itemId: Long, locationId: Long): InventoryBalanceEntity?

    @Query("SELECT COUNT(*) FROM inventory_balances WHERE itemId = :itemId")
    suspend fun countForItem(itemId: Long): Int

    @Query(
        """SELECT EXISTS(
            SELECT 1 FROM inventory_balances
            WHERE itemId = :itemId AND (
                onHandMicros != 0 OR inventoryValueRial != 0 OR reservedMicros != 0
                OR inTransitMicros != 0 OR damagedMicros != 0 OR quarantinedMicros != 0
            )
        )""",
    )
    suspend fun hasNonZeroState(itemId: Long): Boolean

    @Query(
        """
        UPDATE inventory_balances
        SET onHandMicros = :nextOnHandMicros,
            inventoryValueRial = :nextInventoryValueRial,
            quarantinedMicros = :nextQuarantinedMicros,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE itemId = :itemId
          AND locationId = :locationId
          AND onHandMicros = :expectedOnHandMicros
          AND inventoryValueRial = :expectedInventoryValueRial
          AND quarantinedMicros = :expectedQuarantinedMicros
          AND :nextOnHandMicros >= reservedMicros + damagedMicros + :nextQuarantinedMicros
          AND :nextQuarantinedMicros >= 0
          AND :nextInventoryValueRial >= 0
        """,
    )
    suspend fun compareAndSetOnHand(
        itemId: Long,
        locationId: Long,
        expectedOnHandMicros: Long,
        expectedInventoryValueRial: Long,
        expectedQuarantinedMicros: Long,
        nextOnHandMicros: Long,
        nextInventoryValueRial: Long,
        nextQuarantinedMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE inventory_balances
        SET quarantinedMicros = :nextQuarantinedMicros,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE itemId = :itemId AND locationId = :locationId
          AND quarantinedMicros = :expectedQuarantinedMicros
          AND :nextQuarantinedMicros >= 0
          AND reservedMicros + damagedMicros + :nextQuarantinedMicros <= onHandMicros
        """,
    )
    suspend fun compareAndSetQuarantined(
        itemId: Long,
        locationId: Long,
        expectedQuarantinedMicros: Long,
        nextQuarantinedMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """UPDATE inventory_balances
        SET inTransitMicros=:nextInTransitMicros,updatedAtEpochMillis=:updatedAtEpochMillis
        WHERE itemId=:itemId AND locationId=:locationId
          AND inTransitMicros=:expectedInTransitMicros AND :nextInTransitMicros>=0""",
    )
    suspend fun compareAndSetInTransit(
        itemId: Long,
        locationId: Long,
        expectedInTransitMicros: Long,
        nextInTransitMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """UPDATE inventory_balances
        SET onHandMicros=:nextOnHandMicros,inventoryValueRial=:nextInventoryValueRial,
            inTransitMicros=:nextInTransitMicros,updatedAtEpochMillis=:updatedAtEpochMillis
        WHERE itemId=:itemId AND locationId=:locationId
          AND onHandMicros=:expectedOnHandMicros
          AND inventoryValueRial=:expectedInventoryValueRial
          AND inTransitMicros=:expectedInTransitMicros
          AND :nextOnHandMicros>=reservedMicros+damagedMicros+quarantinedMicros
          AND :nextInventoryValueRial>=0 AND :nextInTransitMicros>=0""",
    )
    suspend fun compareAndSetTransferReceipt(
        itemId: Long,
        locationId: Long,
        expectedOnHandMicros: Long,
        expectedInventoryValueRial: Long,
        expectedInTransitMicros: Long,
        nextOnHandMicros: Long,
        nextInventoryValueRial: Long,
        nextInTransitMicros: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM inventory_balances ORDER BY itemId, locationId")
    fun observeAll(): Flow<List<InventoryBalanceEntity>>

    @Query("SELECT * FROM inventory_balances WHERE itemId = :itemId ORDER BY locationId")
    fun observeForItem(itemId: Long): Flow<List<InventoryBalanceEntity>>

    @Query("SELECT * FROM inventory_balances WHERE locationId = :locationId ORDER BY itemId LIMIT :limit OFFSET :offset")
    suspend fun forLocation(locationId: Long, limit: Int, offset: Int): List<InventoryBalanceEntity>

    @Query(
        """
        SELECT * FROM inventory_balances
        WHERE onHandMicros < 0 OR inventoryValueRial < 0
           OR reservedMicros < 0 OR inTransitMicros < 0 OR damagedMicros < 0 OR quarantinedMicros < 0
           OR reservedMicros + damagedMicros + quarantinedMicros > onHandMicros
        ORDER BY itemId, locationId
        """,
    )
    suspend fun invalidBalances(): List<InventoryBalanceEntity>

    @Query(
        """
        SELECT b.itemId AS itemId, b.locationId AS locationId,
               b.onHandMicros AS projectionQuantityMicros,
               COALESCE(l.quantityMicros, 0) AS ledgerQuantityMicros,
               b.inventoryValueRial AS projectionValueRial,
               COALESCE(l.valueRial, 0) AS ledgerValueRial
        FROM inventory_balances b
        LEFT JOIN (
            SELECT itemId, locationId, SUM(quantityDeltaMicros) AS quantityMicros, SUM(valueDeltaRial) AS valueRial
            FROM stock_movements
            WHERE locationId IS NOT NULL
            GROUP BY itemId, locationId
        ) l ON l.itemId = b.itemId AND l.locationId = b.locationId
        WHERE b.onHandMicros != COALESCE(l.quantityMicros, 0)
           OR b.inventoryValueRial != COALESCE(l.valueRial, 0)
        UNION ALL
        SELECT l.itemId AS itemId, l.locationId AS locationId,
               0 AS projectionQuantityMicros, l.quantityMicros AS ledgerQuantityMicros,
               0 AS projectionValueRial, l.valueRial AS ledgerValueRial
        FROM (
            SELECT itemId, locationId, SUM(quantityDeltaMicros) AS quantityMicros, SUM(valueDeltaRial) AS valueRial
            FROM stock_movements
            WHERE locationId IS NOT NULL
            GROUP BY itemId, locationId
        ) l
        LEFT JOIN inventory_balances b ON b.itemId = l.itemId AND b.locationId = l.locationId
        WHERE b.itemId IS NULL
        ORDER BY itemId, locationId
        """,
    )
    suspend fun ledgerProjectionMismatches(): List<InventoryBalanceMismatchRow>

    @Query(
        """
        SELECT i.id AS itemId,
               i.stockMicros AS aggregateQuantityMicros,
               COALESCE(SUM(b.onHandMicros + b.inTransitMicros), 0) AS locationQuantityMicros,
               i.inventoryValueRial AS aggregateValueRial,
               COALESCE(SUM(b.inventoryValueRial), 0) + COALESCE((
                   SELECT SUM(line.valueRial)
                   FROM stock_transfer_lines line
                   INNER JOIN stock_transfers transfer ON transfer.id=line.transferId
                   WHERE line.itemId=i.id AND transfer.status='IN_TRANSIT'
               ),0) AS locationValueRial
        FROM inventory_items i
        LEFT JOIN inventory_balances b ON b.itemId = i.id
        GROUP BY i.id
        HAVING i.stockMicros != COALESCE(SUM(b.onHandMicros + b.inTransitMicros), 0)
            OR i.inventoryValueRial != COALESCE(SUM(b.inventoryValueRial), 0) + COALESCE((
                SELECT SUM(line.valueRial)
                FROM stock_transfer_lines line
                INNER JOIN stock_transfers transfer ON transfer.id=line.transferId
                WHERE line.itemId=i.id AND transfer.status='IN_TRANSIT'
            ),0)
        ORDER BY i.id
        """,
    )
    suspend fun aggregateMismatches(): List<InventoryAggregateMismatchRow>

    @Query(
        """
        SELECT sm.id AS movementId, sm.itemId AS itemId, sm.locationId AS locationId
        FROM stock_movements sm
        LEFT JOIN storage_locations location ON location.id = sm.locationId
        WHERE sm.locationId IS NULL OR location.id IS NULL
        ORDER BY sm.id
        """,
    )
    suspend fun orphanMovements(): List<InventoryOrphanMovementRow>

    @Query(
        """
        SELECT transfer.id AS transferId,
               COALESCE(movement.quantityMicros,0) AS quantityDeltaMicros,
               COALESCE(movement.valueRial,0) AS valueDeltaRial,
               COALESCE(movement.movementCount,0) AS movementCount
        FROM stock_transfers transfer
        LEFT JOIN (
            SELECT referenceId,SUM(quantityDeltaMicros) AS quantityMicros,
                   SUM(valueDeltaRial) AS valueRial,COUNT(*) AS movementCount
            FROM stock_movements
            WHERE referenceType='STOCK_TRANSFER'
              AND movementType IN ('TRANSFER_OUT','TRANSFER_IN')
            GROUP BY referenceId
        ) movement ON movement.referenceId=transfer.id
        LEFT JOIN (
            SELECT transferId,COUNT(*) AS lineCount,
                   SUM(COALESCE(issuedQuantityMicros,0)) AS issuedQuantityMicros,
                   SUM(COALESCE(valueRial,0)) AS issuedValueRial
            FROM stock_transfer_lines GROUP BY transferId
        ) line ON line.transferId=transfer.id
        WHERE (transfer.status IN ('REQUESTED','APPROVED') AND COALESCE(movement.movementCount,0)!=0)
           OR (transfer.status='IN_TRANSIT' AND (
               COALESCE(movement.quantityMicros,0)!=-COALESCE(line.issuedQuantityMicros,0)
               OR COALESCE(movement.valueRial,0)!=-COALESCE(line.issuedValueRial,0)
               OR COALESCE(movement.movementCount,0)!=COALESCE(line.lineCount,0)
           ))
           OR (transfer.status='COMPLETED' AND (
               COALESCE(movement.quantityMicros,0)!=0 OR COALESCE(movement.valueRial,0)!=0
               OR COALESCE(movement.movementCount,0)!=2*COALESCE(line.lineCount,0)
           ))
        ORDER BY transfer.id
        """,
    )
    suspend fun transferImbalances(): List<InventoryTransferImbalanceRow>
}

data class InventoryBalanceMismatchRow(
    val itemId: Long,
    val locationId: Long,
    val projectionQuantityMicros: Long,
    val ledgerQuantityMicros: Long,
    val projectionValueRial: Long,
    val ledgerValueRial: Long,
)

data class InventoryAggregateMismatchRow(
    val itemId: Long,
    val aggregateQuantityMicros: Long,
    val locationQuantityMicros: Long,
    val aggregateValueRial: Long,
    val locationValueRial: Long,
)

data class InventoryOrphanMovementRow(val movementId: Long, val itemId: Long, val locationId: Long?)

data class InventoryTransferImbalanceRow(
    val transferId: Long,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val movementCount: Long,
)
