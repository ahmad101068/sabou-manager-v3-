package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PerformanceModelsTest {
    @Test
    fun goalRejectsWeightOutsideRange() {
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceGoalDraft(1, "هدف", weightPercent = 101, periodStartEpochDay = 1, periodEndEpochDay = 2).validated()
        }
    }

    @Test
    fun reviewTrimsReviewerAndRequiresScores() {
        val review = PerformanceReviewDraft(
            employeeId = 1,
            periodStartEpochDay = 1,
            periodEndEpochDay = 2,
            reviewerName = " مدیر ",
            scores = listOf(PerformanceScoreDraft(goalId = 4, scoreBasisPoints = 8_500)),
        ).validated()
        assertEquals("مدیر", review.reviewerName)
        assertEquals(8_500, review.scores.single().scoreBasisPoints)
    }

    @Test
    fun scoreRejectsOutOfHundred() {
        assertThrows(IllegalArgumentException::class.java) {
            PerformanceScoreDraft(goalId = 4, scoreBasisPoints = 10_001).validated()
        }
    }
}
