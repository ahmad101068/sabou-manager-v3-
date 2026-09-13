package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PerformanceGoalRow(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val title: String,
    val description: String,
    val weightPercent: Int,
    val targetValueMicros: Long?,
    val unit: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val status: String,
)

data class PerformanceReviewRow(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val reviewerName: String,
    val finalScoreBasisPoints: Int,
    val status: String,
    val managerComment: String,
    val employeeComment: String,
)

@Dao
interface PerformanceDao {
    @Query("""
        SELECT g.id, g.employeeId, e.name AS employeeName, g.title, g.description,
               g.weightPercent,
               COALESCE(g.targetValueMicros, CAST(ROUND(g.targetValue * 1000000) AS INTEGER)) AS targetValueMicros,
               g.unit, g.periodStartEpochDay,
               g.periodEndEpochDay, g.status
        FROM performance_goals g
        INNER JOIN employees e ON e.id = g.employeeId
        ORDER BY g.status, g.periodEndEpochDay DESC, g.id DESC
    """)
    fun observeGoals(): Flow<List<PerformanceGoalRow>>

    @Query("""
        SELECT r.id, r.employeeId, e.name AS employeeName, r.periodStartEpochDay,
               r.periodEndEpochDay, r.reviewerName, r.finalScoreBasisPoints,
               r.status, r.managerComment, r.employeeComment
        FROM performance_reviews r
        INNER JOIN employees e ON e.id = r.employeeId
        ORDER BY r.periodEndEpochDay DESC, r.id DESC
    """)
    fun observeReviews(): Flow<List<PerformanceReviewRow>>

    @Query("SELECT * FROM performance_goals WHERE id = :id LIMIT 1")
    suspend fun goalById(id: Long): PerformanceGoalEntity?

    @Query("SELECT * FROM performance_reviews WHERE id = :id LIMIT 1")
    suspend fun reviewById(id: Long): PerformanceReviewEntity?

    @Query("SELECT * FROM performance_scores WHERE reviewId = :reviewId ORDER BY goalId")
    suspend fun scoresForReview(reviewId: Long): List<PerformanceScoreEntity>

    @Query("SELECT * FROM performance_goals WHERE employeeId = :employeeId AND status = 'ACTIVE' AND periodStartEpochDay <= :endDay AND periodEndEpochDay >= :startDay ORDER BY id")
    suspend fun activeGoalsForPeriod(employeeId: Long, startDay: Long, endDay: Long): List<PerformanceGoalEntity>

    @Insert
    suspend fun insertGoal(entity: PerformanceGoalEntity): Long

    @Update
    suspend fun updateGoal(entity: PerformanceGoalEntity): Int

    @Insert
    suspend fun insertReview(entity: PerformanceReviewEntity): Long

    @Update
    suspend fun updateReview(entity: PerformanceReviewEntity): Int

    @Insert
    suspend fun insertScores(entities: List<PerformanceScoreEntity>)

    @Query("DELETE FROM performance_scores WHERE reviewId = :reviewId")
    suspend fun deleteDraftScores(reviewId: Long)
}
