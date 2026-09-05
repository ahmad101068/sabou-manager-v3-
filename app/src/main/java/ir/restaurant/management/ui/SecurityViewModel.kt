package ir.restaurant.management.ui

import ir.restaurant.management.domain.security.Permission

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.data.AppContainer
import ir.restaurant.management.data.BackupDescriptor
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.operations.UserDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecurityUiState(
    val users: List<AppUserRecord> = emptyList(),
    val currentUser: AppUserRecord? = null,
    val backups: List<BackupDescriptor> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

class SecurityViewModel(private val repository: SecurityRepository, private val container: AppContainer) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val backups = MutableStateFlow<List<BackupDescriptor>>(emptyList())

    init {
        viewModelScope.launch {
            repository.currentUser.collectLatest { user ->
                backups.value = if (user?.role?.allows(Permission.BACKUP) == true) {
                    runCatching { container.describeBackups() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
        }
    }

    val state: StateFlow<SecurityUiState> = combine(repository.users, repository.currentUser, backups, busy, message) { users, current, copies, isBusy, msg ->
        SecurityUiState(users, current, copies, isBusy, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityUiState())

    fun save(id: Long?, draft: UserDraft, done: () -> Unit = {}) = run("کاربر ذخیره شد.", done) { repository.save(id, draft) }
    fun deactivate(id: Long) = run("کاربر غیرفعال شد.") { repository.deactivate(id) }
    fun switchUser(id: Long, pin: String, done: () -> Unit = {}) = run("کاربر فعال تغییر کرد.", done) { repository.switchUser(id, pin) }
    fun setRecoveryCode(id: Long, recoveryCode: String, done: () -> Unit = {}) =
        run("کد بازیابی با موفقیت ذخیره شد.", done) { repository.setRecoveryCode(id, recoveryCode) }
    fun recoverPin(id: Long, recoveryCode: String, newPin: String, done: () -> Unit = {}) =
        run("رمز ورود تغییر کرد و کاربر وارد برنامه شد.", done) { repository.resetPinWithRecovery(id, recoveryCode, newPin) }
    fun logout() = run("نشست کاربر بسته شد.") { repository.logout() }
    fun createBackup() = run("نسخه پشتیبان ساخته شد.") { container.createBackup(); backups.value = container.describeBackups() }
    fun factoryReset(pin: String, done: () -> Unit = {}) = run("اطلاعات برنامه به حالت اولیه بازگردانده شد.", done) {
        repository.authorizeSensitiveAction(SensitiveAction.FACTORY_RESET, pin, SensitiveActionContext.resource("DATABASE", "FACTORY_RESET"))
        container.factoryReset()
    }
    fun backupToDrive(destination: Uri, password: CharArray) = run("نسخه پشتیبان قابل‌انتقال در مقصد ابری ذخیره شد.") {
        val name = container.createBackup()
        container.exportBackup(name, password, destination)
        backups.value = container.describeBackups()
    }
    fun exportBackup(name: String, destination: Uri, password: CharArray) = run("نسخه پشتیبان قابل‌انتقال صادر شد.") { container.exportBackup(name, password, destination) }
    fun importBackup(source: Uri, password: CharArray) = run("نسخه پشتیبان وارد شد؛ پس از انتخاب بازیابی، برنامه را دوباره باز کنید.") { container.importBackup(source, password); backups.value = container.describeBackups() }
    fun restore(name: String, pin: String) = run("بازیابی برای اجرای بعدی برنامه زمان‌بندی شد؛ برنامه را کامل ببندید و دوباره باز کنید.") {
        repository.authorizeSensitiveAction(SensitiveAction.RESTORE_BACKUP, pin, SensitiveActionContext.resource("BACKUP", name))
        container.scheduleRestore(name)
    }
    fun deleteBackup(name: String) = run("نسخه پشتیبان حذف شد.") {
        container.deleteBackup(name)
        backups.value = container.describeBackups()
    }
    fun clearMessage() { message.value = null }

    private fun run(success: String, done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true; message.value = null
            try { block(); message.value = success; done() }
            catch (e: Exception) { message.value = UiErrorHandler.message("SecurityViewModel", e) }
            finally { busy.value = false }
        }
    }

    companion object {
        fun factory(repository: SecurityRepository, container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SecurityViewModel(repository, container) as T
        }
    }
}
