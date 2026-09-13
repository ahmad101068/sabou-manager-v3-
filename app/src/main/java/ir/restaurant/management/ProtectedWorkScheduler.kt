package ir.restaurant.management

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.restaurant.management.domain.operations.SyncSafetyGate
import java.util.concurrent.TimeUnit

/** Schedules background work that is forbidden to instantiate protected ERP modules pre-login. */
object ProtectedWorkScheduler {
    private const val ALERT_WORK = "restaurant-management-alert-refresh"
    private const val SYNC_WORK = "restaurant-management-cloud-sync"

    fun enable(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniquePeriodicWork(
            ALERT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AlertRefreshWorker>(6, TimeUnit.HOURS).build(),
        )
        if (SyncSafetyGate.isProductionReady) {
            workManager.enqueueUniquePeriodicWork(
                SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CloudSyncWorker>(1, TimeUnit.HOURS).build(),
            )
        } else {
            workManager.cancelUniqueWork(SYNC_WORK)
        }
    }

    fun disable(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(ALERT_WORK)
        workManager.cancelUniqueWork(SYNC_WORK)
    }
}
