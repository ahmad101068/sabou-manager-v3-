package ir.restaurant.management.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

enum class AutomaticBackupFrequency(val title: String) {
    OFF("خاموش"),
    DAILY("روزانه"),
    WEEKLY("هفتگی"),
    MONTHLY("ماهانه"),
}

data class BackupPolicy(
    val frequency: AutomaticBackupFrequency = AutomaticBackupFrequency.OFF,
    val maxFiles: Int = 50,
) {
    fun validated(): BackupPolicy = copy(maxFiles = maxFiles.coerceIn(1, 200))
}

class BackupPolicyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): BackupPolicy {
        val frequency = preferences.getString(KEY_FREQUENCY, null)
            ?.let { runCatching { AutomaticBackupFrequency.valueOf(it) }.getOrNull() }
            ?: AutomaticBackupFrequency.OFF
        return BackupPolicy(frequency, preferences.getInt(KEY_MAX_FILES, 50)).validated()
    }

    fun save(policy: BackupPolicy) {
        val valid = policy.validated()
        check(
            preferences.edit()
                .putString(KEY_FREQUENCY, valid.frequency.name)
                .putInt(KEY_MAX_FILES, valid.maxFiles)
                .commit(),
        ) { "ذخیره تنظیمات پشتیبان انجام نشد." }
    }

    private companion object {
        const val PREFS_NAME = "automatic_backup_policy"
        const val KEY_FREQUENCY = "frequency"
        const val KEY_MAX_FILES = "max_files"
    }
}

object AutomaticBackupScheduler {
    private const val WORK_NAME = "restaurant-manager-automatic-backup"

    fun apply(context: Context, policy: BackupPolicy) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val intervalDays = when (policy.frequency) {
            AutomaticBackupFrequency.OFF -> null
            AutomaticBackupFrequency.DAILY -> 1L
            AutomaticBackupFrequency.WEEKLY -> 7L
            AutomaticBackupFrequency.MONTHLY -> 30L
        }
        if (intervalDays == null) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(intervalDays, TimeUnit.DAYS).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class AutomaticBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val application = applicationContext as ir.restaurant.management.RestaurantManagementApplication
        val policy = BackupPolicyStore(applicationContext).load()
        if (policy.frequency != AutomaticBackupFrequency.OFF) {
            application.container.createAutomaticBackup(policy.maxFiles)
        }
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )
}
