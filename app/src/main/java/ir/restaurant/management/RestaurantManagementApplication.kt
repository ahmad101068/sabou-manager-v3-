package ir.restaurant.management

import android.app.Application
import ir.restaurant.management.data.AppContainer
import ir.restaurant.management.data.AutomaticBackupScheduler
import ir.restaurant.management.data.BackupPolicyStore

class RestaurantManagementApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        // Protected ERP workers are session-scoped. A fresh process begins unauthenticated and must
        // not instantiate Alerts/Sync until RestaurantManagementApp enters the authenticated graph.
        ProtectedWorkScheduler.disable(this)
        AutomaticBackupScheduler.apply(this, BackupPolicyStore(this).load())
    }
}
