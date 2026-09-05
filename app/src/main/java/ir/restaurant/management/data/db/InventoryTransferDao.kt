package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryTransferDao {
    @Insert
    suspend fun insertTransfer(entity: StockTransferEntity): Long

    @Insert
    suspend fun insertLines(entities: List<StockTransferLineEntity>)

    @Query("SELECT * FROM stock_transfers WHERE id=:id LIMIT 1")
    suspend fun transfer(id: Long): StockTransferEntity?

    @Query("SELECT * FROM stock_transfers WHERE idempotencyKey=:key LIMIT 1")
    suspend fun byIdempotencyKey(key: String): StockTransferEntity?

    @Query("SELECT * FROM stock_transfers WHERE issueCommandId=:commandId LIMIT 1")
    suspend fun byIssueCommand(commandId: String): StockTransferEntity?

    @Query("SELECT * FROM stock_transfers WHERE receiveCommandId=:commandId LIMIT 1")
    suspend fun byReceiveCommand(commandId: String): StockTransferEntity?

    @Query("SELECT * FROM stock_transfer_lines WHERE transferId=:transferId ORDER BY id")
    suspend fun lines(transferId: Long): List<StockTransferLineEntity>

    @Query(
        """SELECT * FROM stock_transfers
        WHERE (:status IS NULL OR status=:status)
          AND (:locationId IS NULL OR sourceLocationId=:locationId OR destinationLocationId=:locationId)
        ORDER BY transferEpochDay DESC,id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun search(status: String?, locationId: Long?, limit: Int, offset: Int): List<StockTransferEntity>

    @Query(
        """SELECT * FROM stock_transfers
        ORDER BY CASE status WHEN 'IN_TRANSIT' THEN 0 WHEN 'APPROVED' THEN 1 WHEN 'REQUESTED' THEN 2 ELSE 3 END,
            transferEpochDay DESC,id DESC LIMIT :limit""",
    )
    fun observeCenter(limit: Int = 200): Flow<List<StockTransferEntity>>

    @Query(
        """UPDATE stock_transfers SET status='APPROVED',approvedByActorId=:actorId,
            approvedAtEpochMillis=:now,updatedAtEpochMillis=:now
        WHERE id=:id AND status='REQUESTED'""",
    )
    suspend fun approve(id: Long, actorId: Long, now: Long): Int

    @Query(
        """UPDATE stock_transfers SET status='IN_TRANSIT',issueCommandId=:commandId,
            issuedByActorId=:actorId,issuedAtEpochMillis=:now,updatedAtEpochMillis=:now
        WHERE id=:id AND status='APPROVED' AND issueCommandId IS NULL""",
    )
    suspend fun markIssued(id: Long, commandId: String, actorId: Long, now: Long): Int

    @Query(
        """UPDATE stock_transfers SET status='COMPLETED',receiveCommandId=:commandId,
            receivedByActorId=:actorId,receivedAtEpochMillis=:now,updatedAtEpochMillis=:now
        WHERE id=:id AND status='IN_TRANSIT' AND receiveCommandId IS NULL""",
    )
    suspend fun markReceived(id: Long, commandId: String, actorId: Long, now: Long): Int

    @Query(
        """UPDATE stock_transfer_lines SET issuedQuantityMicros=:issuedQuantityMicros,
            unitCostRial=:unitCostRial,valueRial=:valueRial,updatedAtEpochMillis=:now
        WHERE id=:id AND transferId=:transferId AND issuedQuantityMicros IS NULL""",
    )
    suspend fun markLineIssued(
        transferId: Long,
        id: Long,
        issuedQuantityMicros: Long,
        unitCostRial: Long,
        valueRial: Long,
        now: Long,
    ): Int

    @Query(
        """UPDATE stock_transfer_lines SET receivedQuantityMicros=:receivedQuantityMicros,
            varianceQuantityMicros=:varianceQuantityMicros,updatedAtEpochMillis=:now
        WHERE id=:id AND transferId=:transferId AND issuedQuantityMicros IS NOT NULL
          AND receivedQuantityMicros IS NULL""",
    )
    suspend fun markLineReceived(
        transferId: Long,
        id: Long,
        receivedQuantityMicros: Long,
        varianceQuantityMicros: Long,
        now: Long,
    ): Int
}
