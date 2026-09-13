package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySalesDao {
    @Query("SELECT EXISTS(SELECT 1 FROM sales_day_closures c JOIN daily_sales_summaries s ON s.id=c.summaryId WHERE s.branchId=:branchId AND c.businessEpochDay=:epochDay AND c.status='CLOSED')")
    suspend fun dayClosed(branchId: Long, epochDay: Long): Boolean

    @Query("SELECT c.* FROM sales_day_closures c JOIN daily_sales_summaries s ON s.id=c.summaryId WHERE s.branchId=:branchId AND c.businessEpochDay=:epochDay LIMIT 1")
    suspend fun dayClosure(branchId: Long, epochDay: Long): SalesDayClosureEntity?

    @Query("SELECT * FROM sales_day_closures WHERE businessEpochDay=:epochDay ORDER BY summaryId LIMIT 1")
    suspend fun dayClosureAnyBranch(epochDay: Long): SalesDayClosureEntity?

    @Query("SELECT COUNT(*) FROM sales_day_closures WHERE businessEpochDay=:epochDay AND status='CLOSED'")
    suspend fun closedDayClosureCountAnyBranch(epochDay: Long): Int

    @Query("SELECT * FROM sales_day_closures ORDER BY businessEpochDay DESC")
    fun observeDayClosures(): Flow<List<SalesDayClosureEntity>>

    @Insert
    suspend fun insertDayClosure(entity: SalesDayClosureEntity)

    @androidx.room.Update
    suspend fun updateDayClosure(entity: SalesDayClosureEntity): Int

    @Query("SELECT * FROM daily_sales_summaries WHERE branchId = :branchId AND businessEpochDay = :epochDay AND reversedAtEpochDay IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun activeSummaryByDay(branchId: Long, epochDay: Long): DailySalesSummaryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM daily_sales_summaries WHERE branchId = :branchId AND businessEpochDay = :epochDay AND reversedAtEpochDay IS NULL)")
    suspend fun dayExists(branchId: Long, epochDay: Long): Boolean

    @Query("SELECT * FROM daily_sales_summaries WHERE id = :summaryId LIMIT 1")
    suspend fun summary(summaryId: Long): DailySalesSummaryEntity?

    @Insert
    suspend fun insertSummary(entity: DailySalesSummaryEntity): Long

    @Insert
    suspend fun insertLines(entities: List<DailySalesMenuLineEntity>)

    @Query("DELETE FROM daily_sales_menu_lines WHERE summaryId=:summaryId")
    suspend fun deleteLines(summaryId: Long): Int

    @Query("""
        UPDATE daily_sales_summaries SET
            businessEpochDay=:businessEpochDay, locationId=:locationId, grossSalesRial=:grossSalesRial, discountRial=:discountRial, returnRial=:returnRial,
            serviceRial=:serviceRial, taxRial=:taxRial, netSalesRial=:netSalesRial, theoreticalCostRial=:theoreticalCostRial,
            cashRial=:cashRial, cardRial=:cardRial, transferRial=:transferRial, notes=:notes,
            updatedByUserId=:actorId, updatedAtEpochMillis=:updatedAt
        WHERE id=:summaryId AND status='DRAFT' AND reversedAtEpochDay IS NULL
    """)
    suspend fun updateDraftSummary(summaryId: Long, businessEpochDay: Long, locationId: Long, grossSalesRial: Long, discountRial: Long, returnRial: Long, serviceRial: Long, taxRial: Long, netSalesRial: Long, theoreticalCostRial: Long, cashRial: Long, cardRial: Long, transferRial: Long, notes: String, actorId: Long, updatedAt: Long): Int

    @Query("UPDATE daily_sales_summaries SET status=:toStatus, updatedByUserId=:actorId, updatedAtEpochMillis=:updatedAt WHERE id=:summaryId AND status=:fromStatus AND reversedAtEpochDay IS NULL")
    suspend fun transitionStatus(summaryId: Long, fromStatus: String, toStatus: String, actorId: Long, updatedAt: Long): Int

    @Query("UPDATE daily_sales_summaries SET theoreticalCostRial=:theoreticalCostRial, updatedByUserId=:actorId, updatedAtEpochMillis=:updatedAt WHERE id=:summaryId AND status='CONFIRMED' AND reversedAtEpochDay IS NULL")
    suspend fun updateConfirmedCost(summaryId: Long, theoreticalCostRial: Long, actorId: Long, updatedAt: Long): Int

    @Query("SELECT * FROM daily_sales_menu_lines WHERE summaryId = :summaryId ORDER BY id")
    suspend fun lines(summaryId: Long): List<DailySalesMenuLineEntity>

    @Query("SELECT * FROM daily_sales_menu_lines ORDER BY summaryId DESC, id")
    fun observeAllLines(): Flow<List<DailySalesMenuLineEntity>>

    @Query("UPDATE daily_sales_summaries SET journalEntryId = :journalEntryId, costJournalEntryId = :costJournalEntryId WHERE id = :summaryId")
    suspend fun linkJournals(summaryId: Long, journalEntryId: Long, costJournalEntryId: Long?): Int

    @Query(
        """
        UPDATE daily_sales_summaries
        SET reversedAtEpochDay = :reversalEpochDay,
            reversalReason = :reason,
            reversalJournalEntryId = :reversalJournalEntryId,
            reversalCostJournalEntryId = :reversalCostJournalEntryId,
            status = 'VOIDED', updatedByUserId=:actorId, updatedAtEpochMillis=:updatedAt
        WHERE id = :summaryId AND reversedAtEpochDay IS NULL AND status='POSTED' 
        """,
    )
    suspend fun markReversed(
        summaryId: Long,
        reversalEpochDay: Long,
        reason: String,
        reversalJournalEntryId: Long?,
        reversalCostJournalEntryId: Long?,
        actorId: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        SELECT * FROM daily_sales_summaries
        WHERE (:query = '' OR CAST(businessEpochDay AS TEXT) LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY businessEpochDay DESC, id DESC
        """,
    )
    fun observeSummaries(query: String): Flow<List<DailySalesSummaryEntity>>

    @Query(
        """SELECT summaryId,
            COALESCE(SUM(foodCostSnapshotRial + packagingCostSnapshotRial + directLaborCostSnapshotRial + allocatedOverheadSnapshotRial), 0) AS fullCostRial,
            COUNT(*) AS totalLineCount,
            SUM(CASE WHEN foodCostSnapshotRial IS NOT NULL THEN 1 ELSE 0 END) AS coveredLineCount
        FROM daily_sales_menu_lines GROUP BY summaryId""",
    )
    fun observeSummaryProfitability(): Flow<List<DailySalesProfitabilityRow>>

    @Query(
        """
        SELECT * FROM daily_sales_summaries
        WHERE (status='POSTED' OR reversedAtEpochDay IS NOT NULL)
          AND (businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay
           OR reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay)
        ORDER BY businessEpochDay DESC, id DESC
        """,
    )
    fun observeRange(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailySalesSummaryEntity>>

    @Query(
        """
        SELECT l.menuItemId, l.menuItemNameSnapshot AS name,
               CAST((
                   COALESCE(SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.quantityMicros ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.quantityMicros ELSE 0 END), 0)
               ) / 1000000 AS INTEGER) AS unitsSold,
               COALESCE(SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.grossSalesRial ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.grossSalesRial ELSE 0 END), 0) AS salesRial,
               COALESCE(SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.theoreticalCostRial ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN l.theoreticalCostRial ELSE 0 END), 0) AS costRial,
               COALESCE(SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.foodCostSnapshotRial IS NOT NULL
                   THEN l.foodCostSnapshotRial + l.packagingCostSnapshotRial + l.directLaborCostSnapshotRial + l.allocatedOverheadSnapshotRial ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.foodCostSnapshotRial IS NOT NULL
                   THEN l.foodCostSnapshotRial + l.packagingCostSnapshotRial + l.directLaborCostSnapshotRial + l.allocatedOverheadSnapshotRial ELSE 0 END), 0) AS fullCostRial,
               SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN 1 ELSE 0 END)
                 - SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay THEN 1 ELSE 0 END) AS totalLineCount,
               SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.grossSalesRial IS NOT NULL THEN 1 ELSE 0 END)
                 - SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.grossSalesRial IS NOT NULL THEN 1 ELSE 0 END) AS salesAmountLineCount,
               SUM(CASE WHEN s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.foodCostSnapshotRial IS NOT NULL THEN 1 ELSE 0 END)
                 - SUM(CASE WHEN s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND l.foodCostSnapshotRial IS NOT NULL THEN 1 ELSE 0 END) AS fullCostLineCount
        FROM daily_sales_menu_lines l
        INNER JOIN daily_sales_summaries s ON s.id = l.summaryId
        WHERE (s.status='POSTED' OR s.reversedAtEpochDay IS NOT NULL)
          AND (s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay
           OR s.reversedAtEpochDay BETWEEN :fromEpochDay AND :toEpochDay)
        GROUP BY l.menuItemId, l.menuItemNameSnapshot
        ORDER BY salesRial DESC, l.menuItemNameSnapshot
        """,
    )
    fun observeMenuPerformance(fromEpochDay: Long, toEpochDay: Long): Flow<List<DailyMenuPerformanceRow>>
}

data class DailyMenuPerformanceRow(
    val menuItemId: Long?,
    val name: String,
    val unitsSold: Long,
    val salesRial: Long,
    val costRial: Long,
    val fullCostRial: Long,
    val totalLineCount: Int,
    val salesAmountLineCount: Int,
    val fullCostLineCount: Int,
)

data class DailySalesProfitabilityRow(
    val summaryId: Long,
    val fullCostRial: Long,
    val totalLineCount: Int,
    val coveredLineCount: Int,
)
