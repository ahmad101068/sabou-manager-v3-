package ir.restaurant.management.domain.personnel

import kotlinx.coroutines.flow.Flow

data class PerformanceGoalRecord(
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

data class PerformanceGoalDraft(
    val employeeId: Long,
    val title: String,
    val description: String = "",
    val weightPercent: Int,
    val targetValueMicros: Long? = null,
    val unit: String = "",
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
) {
    fun validated(): PerformanceGoalDraft {
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(title.trim().length in 2..120) { "عنوان هدف معتبر نیست." }
        require(description.length <= 500) { "شرح هدف بیش از حد طولانی است." }
        require(weightPercent in 1..100) { "وزن هدف باید بین ۱ تا ۱۰۰ درصد باشد." }
        require(targetValueMicros == null || targetValueMicros >= 0L) { "مقدار هدف معتبر نیست." }
        require(periodStartEpochDay > 0 && periodEndEpochDay >= periodStartEpochDay) { "دوره هدف معتبر نیست." }
        return copy(title = title.trim(), description = description.trim(), unit = unit.trim())
    }
}

data class PerformanceScoreDraft(
    val goalId: Long,
    val achievedValueMicros: Long? = null,
    val scoreBasisPoints: Int,
    val notes: String = "",
) {
    fun validated(): PerformanceScoreDraft {
        require(goalId > 0) { "هدف ارزیابی معتبر نیست." }
        require(achievedValueMicros == null || achievedValueMicros >= 0L) { "مقدار عملکرد معتبر نیست." }
        require(scoreBasisPoints in 0..10_000) { "امتیاز باید بین صفر تا صد باشد." }
        return copy(notes = notes.trim())
    }
}

data class PerformanceReviewDraft(
    val employeeId: Long,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val reviewerName: String,
    val managerComment: String = "",
    val employeeComment: String = "",
    val scores: List<PerformanceScoreDraft>,
) {
    fun validated(): PerformanceReviewDraft {
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(periodStartEpochDay > 0 && periodEndEpochDay >= periodStartEpochDay) { "دوره ارزیابی معتبر نیست." }
        require(reviewerName.trim().isNotEmpty()) { "نام ارزیاب الزامی است." }
        require(scores.isNotEmpty()) { "حداقل یک هدف باید امتیازدهی شود." }
        require(scores.map { it.goalId }.distinct().size == scores.size) { "هر هدف فقط یک امتیاز می‌پذیرد." }
        return copy(
            reviewerName = reviewerName.trim(),
            managerComment = managerComment.trim(),
            employeeComment = employeeComment.trim(),
            scores = scores.map { it.validated() },
        )
    }
}

data class PerformanceReviewRecord(
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

interface PerformanceRepository {
    val goals: Flow<List<PerformanceGoalRecord>>
    val reviews: Flow<List<PerformanceReviewRecord>>
    suspend fun saveGoal(id: Long?, draft: PerformanceGoalDraft): Long
    suspend fun deactivateGoal(id: Long)
    suspend fun submitReview(draft: PerformanceReviewDraft): Long
}
