package ir.restaurant.management

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.first

class AlertRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext as RestaurantManagementApplication
        val currentUser = app.container.securityRepository.currentUser.first() ?: return Result.success()
        if (!currentUser.role.allows(Permission.ACCOUNTING)) return Result.success()
        val repository = app.container.alertRepository
        repository.refresh(currentLocalEpochDay())
        val actionable = repository.alerts().first().count {
            it.severity in setOf("HIGH", "MEDIUM") && !it.isRead && !it.isDismissed
        }
        if (actionable > 0) notifyUrgent(actionable)
        Result.success()
    }.getOrElse { Result.retry() }

    private fun notifyUrgent(count: Int) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "هشدارهای مدیریتی",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        if (Build.VERSION.SDK_INT < 33 || applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val openApp = PendingIntent.getActivity(
                applicationContext,
                7001,
                Intent(applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            NotificationManagerCompat.from(applicationContext).notify(
                7001,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(ir.restaurant.management.R.mipmap.ic_launcher)
                    .setContentTitle(applicationContext.organizationDisplayName())
                    .setContentText("$count هشدار نیازمند بررسی است")
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
    private companion object { const val CHANNEL_ID = "restaurant-management_manager_alerts" }
}
