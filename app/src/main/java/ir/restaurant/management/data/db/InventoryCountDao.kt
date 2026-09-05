package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryCountDao {
    @Query(
        """
        SELECT item.id AS itemId,
               item.trackLot AS trackLot,
               COALESCE(balance.onHandMicros,0) AS quantityMicros,
               COALESCE(balance.inventoryValueRial,0) AS valueRial
        FROM inventory_items item
        LEFT JOIN inventory_balances balance
          ON balance.itemId=item.id AND balance.locationId=:locationId
        WHERE item.isActive=1
        ORDER BY item.id
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun locationSnapshot(locationId: Long, limit: Int, offset: Int): List<InventoryCountSnapshotRow>

    @Query(
        """
        SELECT lot.id AS lotId, lot.itemId AS itemId, lot.quantityMicros AS quantityMicros,
               lot.unitCostRial AS unitCostRial, lot.status AS status
        FROM inventory_lots lot
        INNER JOIN inventory_items item ON item.id = lot.itemId
        WHERE lot.locationId = :locationId
          AND item.isActive = 1
          AND item.trackLot = 1
          AND lot.status != 'LEGACY_UNKNOWN'
          AND (lot.quantityMicros > 0 OR lot.status != 'DEPLETED')
        ORDER BY lot.itemId, lot.id
        """,
    )
    suspend fun lotSnapshot(locationId: Long): List<InventoryCountLotSnapshotRow>

    @Insert
    suspend fun insertSession(entity: InventoryCountSessionEntity): Long

    @Insert
    suspend fun insertLines(entities: List<InventoryCountLineEntity>)

    @Query("SELECT * FROM inventory_count_sessions WHERE id = :id LIMIT 1")
    suspend fun session(id: Long): InventoryCountSessionEntity?

    @Query("SELECT * FROM inventory_count_sessions WHERE idempotencyKey = :key LIMIT 1")
    suspend fun byIdempotencyKey(key: String): InventoryCountSessionEntity?

    @Query("SELECT * FROM inventory_count_sessions WHERE postCommandId = :commandId LIMIT 1")
    suspend fun byPostCommandId(commandId: String): InventoryCountSessionEntity?

    @Query(
        """
        SELECT * FROM inventory_count_sessions
        WHERE (:status IS NULL OR status = :status)
          AND (:locationId IS NULL OR locationId = :locationId)
        ORDER BY businessEpochDay DESC, id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun searchSessions(status: String?, locationId: Long?, limit: Int, offset: Int): List<InventoryCountSessionEntity>

    @Query(
        """
        SELECT * FROM inventory_count_sessions
        ORDER BY CASE status
            WHEN 'PENDING_APPROVAL' THEN 0 WHEN 'RECOUNT_REQUIRED' THEN 1
            WHEN 'COUNTING' THEN 2 WHEN 'OPEN' THEN 3 WHEN 'DRAFT' THEN 4 ELSE 5 END,
            businessEpochDay DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeCenter(limit: Int = 200): Flow<List<InventoryCountSessionEntity>>

    @Query("SELECT * FROM inventory_count_lines WHERE sessionId = :sessionId ORDER BY itemId, lotKey, id")
    suspend fun lines(sessionId: Long): List<InventoryCountLineEntity>

    @Query("SELECT * FROM inventory_count_lines WHERE id = :id AND sessionId = :sessionId LIMIT 1")
    suspend fun line(sessionId: Long, id: Long): InventoryCountLineEntity?

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'OPEN', updatedAtEpochMillis = :now
        WHERE id = :id AND status = 'DRAFT'
        """,
    )
    suspend fun open(id: Long, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'COUNTING', startedAtEpochMillis = COALESCE(startedAtEpochMillis,:now),
            updatedAtEpochMillis = :now
        WHERE id = :id AND status IN ('OPEN','RECOUNT_REQUIRED')
        """,
    )
    suspend fun markCounting(id: Long, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_lines
        SET firstCountQuantityMicros = :firstCountQuantityMicros,
            secondCountQuantityMicros = :secondCountQuantityMicros,
            finalCountQuantityMicros = :finalCountQuantityMicros,
            finalCountValueRial = :finalCountValueRial,
            varianceQuantityMicros = :varianceQuantityMicros,
            varianceValueRial = :varianceValueRial,
            status = :nextStatus,
            reason = :reason,
            countedByActorId = :actorId,
            countedAtEpochMillis = :now,
            updatedAtEpochMillis = :now
        WHERE id = :id AND sessionId = :sessionId AND status = :expectedStatus
        """,
    )
    suspend fun recordLine(
        sessionId: Long,
        id: Long,
        expectedStatus: String,
        firstCountQuantityMicros: Long?,
        secondCountQuantityMicros: Long?,
        finalCountQuantityMicros: Long?,
        finalCountValueRial: Long?,
        varianceQuantityMicros: Long?,
        varianceValueRial: Long?,
        nextStatus: String,
        reason: String,
        actorId: Long,
        now: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM inventory_count_lines WHERE sessionId = :sessionId AND status = 'PENDING'")
    suspend fun pendingLineCount(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM inventory_count_lines WHERE sessionId = :sessionId AND status = 'RECOUNT_REQUIRED'")
    suspend fun recountLineCount(sessionId: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = :nextStatus, submittedAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND status = 'COUNTING'
        """,
    )
    suspend fun submit(id: Long, nextStatus: String, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'PENDING_APPROVAL', submittedAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND status = 'RECOUNT_REQUIRED'
          AND NOT EXISTS(SELECT 1 FROM inventory_count_lines line WHERE line.sessionId=:id AND line.status='RECOUNT_REQUIRED')
        """,
    )
    suspend fun submitAfterRecount(id: Long, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'APPROVED', approvedByActorId = :actorId,
            approvedAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND status = 'PENDING_APPROVAL'
        """,
    )
    suspend fun approve(id: Long, actorId: Long, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'POSTED', postCommandId = :commandId, postedByActorId = :actorId,
            postedAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND status = 'APPROVED' AND postCommandId IS NULL
        """,
    )
    suspend fun markPosted(id: Long, commandId: String, actorId: Long, now: Long): Int

    @Query(
        """
        UPDATE inventory_count_sessions
        SET status = 'CANCELLED', cancelledAtEpochMillis = :now, updatedAtEpochMillis = :now
        WHERE id = :id AND status IN ('DRAFT','OPEN','COUNTING','RECOUNT_REQUIRED','PENDING_APPROVAL')
        """,
    )
    suspend fun cancel(id: Long, now: Long): Int
}

data class InventoryCountSnapshotRow(
    val itemId: Long,
    val trackLot: Boolean,
    val quantityMicros: Long,
    val valueRial: Long,
)

data class InventoryCountLotSnapshotRow(
    val lotId: Long,
    val itemId: Long,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val status: String,
)
