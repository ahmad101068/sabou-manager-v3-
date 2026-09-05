package ir.restaurant.management

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.restaurant.management.domain.operations.SyncRetryPolicy
import ir.restaurant.management.domain.operations.SyncSafetyGate
import kotlinx.coroutines.flow.first

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!SyncSafetyGate.isProductionReady) return Result.success()
        val container = (applicationContext as RestaurantManagementApplication).container
        if (container.securityRepository.currentUser.first() == null) return Result.success()
        if (!container.syncConfig().enabled) return Result.success()
        val retry = SyncRetryPolicy.decide(runAttemptCount, System.currentTimeMillis(), 0)
        if (!retry.canRetry && runAttemptCount >= 8) return Result.failure()
        return runCatching {
            container.runSync()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
