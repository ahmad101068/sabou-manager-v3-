package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRetryPolicyTest {
    @Test fun delaysAndStopsAfterEightAttempts() {
        assertFalse(SyncRetryPolicy.decide(2, 10_000, 0, 1_000, 20_000).canRetry)
        assertTrue(SyncRetryPolicy.decide(2, 14_000, 0, 1_000, 20_000).canRetry)
        assertFalse(SyncRetryPolicy.decide(8, 1_000_000, 0).canRetry)
    }

    @Test fun failureSchedulingMovesEighthAttemptToDeadLetter() {
        assertTrue(SyncRetryPolicy.afterFailure(7).canRetry)
        assertFalse(SyncRetryPolicy.afterFailure(8).canRetry)
    }
}
