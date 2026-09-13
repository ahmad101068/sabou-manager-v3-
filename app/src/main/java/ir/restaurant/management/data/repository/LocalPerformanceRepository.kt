package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.PerformanceGoalEntity
import ir.restaurant.management.data.db.PerformanceReviewEntity
import ir.restaurant.management.data.db.PerformanceScoreEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.personnel.PerformanceGoalDraft
import ir.restaurant.management.domain.personnel.PerformanceGoalRecord
import ir.restaurant.management.domain.personnel.PerformanceRepository
import ir.restaurant.management.domain.personnel.PerformanceReviewDraft
import ir.restaurant.management.domain.personnel.PerformanceReviewRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalPerformanceRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val authorizer: SessionAuthorizer,
    private val syncRecorder: SyncRecorder? = null,
) : PerformanceRepository {
    private val dao get() = database.performanceDao()
    private val personnel get() = database.personnelDao()

    override val goals: Flow<List<PerformanceGoalRecord>> = dao.observeGoals().map { rows ->
        rows.map { PerformanceGoalRecord(it.id, it.employeeId, it.employeeName, it.title, it.description, it.weightPercent, it.targetValueMicros, it.unit, it.periodStartEpochDay, it.periodEndEpochDay, it.status) }
    }

    override val reviews: Flow<List<PerformanceReviewRecord>> = dao.observeReviews().map { rows ->
        rows.map { PerformanceReviewRecord(it.id, it.employeeId, it.employeeName, it.periodStartEpochDay, it.periodEndEpochDay, it.reviewerName, it.finalScoreBasisPoints, it.status, it.managerComment, it.employeeComment) }
    }

    override suspend fun saveGoal(id: Long?, draft: PerformanceGoalDraft): Long {
        authorizer.require(Permission.PERSONNEL)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            require(employee.status == "ACTIVE") { "برای پرسنل غیرفعال نمی‌توان هدف ثبت کرد." }
            val existing = dao.activeGoalsForPeriod(valid.employeeId, valid.periodStartEpochDay, valid.periodEndEpochDay)
                .filterNot { it.id == id }
            require(existing.sumOf { it.weightPercent } + valid.weightPercent <= 100) { "جمع وزن اهداف هم‌پوشان نمی‌تواند بیشتر از ۱۰۰ درصد باشد." }
            val now = clock()
            if (id == null) {
                dao.insertGoal(PerformanceGoalEntity(employeeId = valid.employeeId, title = valid.title, description = valid.description, weightPercent = valid.weightPercent, legacyTargetValue = null, targetValueMicros = valid.targetValueMicros, unit = valid.unit, periodStartEpochDay = valid.periodStartEpochDay, periodEndEpochDay = valid.periodEndEpochDay, createdAtEpochMillis = now, updatedAtEpochMillis = now)).also { syncRecorder?.record("PERFORMANCE_GOAL",it,"CREATE",now) }
            } else {
                val current = dao.goalById(id) ?: error("هدف عملکرد پیدا نشد.")
                require(current.status == "ACTIVE") { "هدف بسته‌شده قابل ویرایش نیست." }
                check(dao.updateGoal(current.copy(title = valid.title, description = valid.description, weightPercent = valid.weightPercent, legacyTargetValue = null, targetValueMicros = valid.targetValueMicros, unit = valid.unit, periodStartEpochDay = valid.periodStartEpochDay, periodEndEpochDay = valid.periodEndEpochDay, updatedAtEpochMillis = now)) == 1)
                syncRecorder?.record("PERFORMANCE_GOAL",id,"UPDATE",now)
                id
            }
        }
    }

    override suspend fun deactivateGoal(id: Long) {
        authorizer.require(Permission.PERSONNEL)
        database.withTransaction {
            val current = dao.goalById(id) ?: error("هدف عملکرد پیدا نشد.")
            require(current.status == "ACTIVE") { "هدف قبلاً بسته شده است." }
            check(dao.updateGoal(current.copy(status = "CLOSED", updatedAtEpochMillis = clock())) == 1)
            syncRecorder?.record("PERFORMANCE_GOAL",id,"DEACTIVATE",clock())
        }
    }

    override suspend fun submitReview(draft: PerformanceReviewDraft): Long {
        authorizer.require(Permission.PERSONNEL)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val goals = dao.activeGoalsForPeriod(valid.employeeId, valid.periodStartEpochDay, valid.periodEndEpochDay)
            val goalMap = goals.associateBy { it.id }
            require(valid.scores.all { it.goalId in goalMap }) { "یکی از اهداف متعلق به این پرسنل یا دوره نیست." }
            val selectedWeight = valid.scores.sumOf { goalMap.getValue(it.goalId).weightPercent }
            require(selectedWeight == 100) { "جمع وزن اهداف ارزیابی باید دقیقاً ۱۰۰ درصد باشد." }
            val finalScore = valid.scores.sumOf { score ->
                score.scoreBasisPoints * goalMap.getValue(score.goalId).weightPercent / 100
            }.coerceIn(0, 10_000)
            val now = clock()
            val reviewId = dao.insertReview(PerformanceReviewEntity(employeeId = employee.id, periodStartEpochDay = valid.periodStartEpochDay, periodEndEpochDay = valid.periodEndEpochDay, reviewerName = valid.reviewerName, finalScoreBasisPoints = finalScore, status = "COMPLETED", managerComment = valid.managerComment, employeeComment = valid.employeeComment, submittedAtEpochMillis = now, completedAtEpochMillis = now, createdAtEpochMillis = now, updatedAtEpochMillis = now))
            dao.insertScores(valid.scores.map { score ->
                val weight = goalMap.getValue(score.goalId).weightPercent
                PerformanceScoreEntity(reviewId = reviewId, goalId = score.goalId, legacyAchievedValue = null, achievedValueMicros = score.achievedValueMicros, scoreBasisPoints = score.scoreBasisPoints, weightedScoreBasisPoints = score.scoreBasisPoints * weight / 100, notes = score.notes, createdAtEpochMillis = now, updatedAtEpochMillis = now)
            })
            syncRecorder?.record("PERFORMANCE_REVIEW",reviewId,"CREATE",now)
            reviewId
        }
    }
}
