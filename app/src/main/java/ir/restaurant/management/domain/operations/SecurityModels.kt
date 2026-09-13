package ir.restaurant.management.domain.operations

import kotlinx.coroutines.flow.Flow
import ir.restaurant.management.domain.security.Permission

data class AppUserRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val isActive: Boolean,
    val hasRecoveryCode: Boolean,
    val rowVersion: Long = 0,
)
data class UserDraft(
    val username: String,
    val displayName: String,
    val pin: String,
    val role: UserRole,
    val recoveryCode: String = "",
    val expectedRowVersion: Long? = null,
)

data class UserDataScope(
    val userId: Long,
    val primaryBranchId: Long?,
    val allowedBranchIds: Set<Long>,
    val allowedWarehouseIds: Set<Long>,
    val expectedRowVersion: Long? = null,
) {
    fun validated(): UserDataScope {
        require(userId > 0) { "کاربر معتبر نیست." }
        require(allowedBranchIds.all { it > 0 } && allowedWarehouseIds.all { it > 0 }) { "محدوده داده معتبر نیست." }
        require(primaryBranchId == null || primaryBranchId in allowedBranchIds) { "شعبه اصلی باید در محدوده مجاز کاربر باشد." }
        return this
    }
}

data class SensitiveActionContext(
    val resourceType: String,
    val resourceId: String,
    val branchId: Long? = null,
    val scope: String = "",
    val commandFingerprint: String = "",
) {
    init {
        require(resourceType.matches(Regex("[A-Z0-9_]{2,60}"))) { "نوع منبع عملیات حساس معتبر نیست." }
        require(resourceId.isNotBlank() && resourceId.length <= 160) { "شناسه منبع عملیات حساس معتبر نیست." }
        require(branchId == null || branchId > 0) { "شعبه عملیات حساس معتبر نیست." }
        require(scope.length <= 160 && commandFingerprint.length <= 200) { "زمینه عملیات حساس بیش از حد طولانی است." }
    }

    val permitKey: String get() = listOf(resourceType, resourceId, branchId?.toString().orEmpty(), scope, commandFingerprint).joinToString("|")

    companion object {
        fun global(action: SensitiveAction) = SensitiveActionContext("GLOBAL_ACTION", action.name)
        fun resource(type: String, id: Any, branchId: Long? = null, scope: String = "", commandFingerprint: String = "") =
            SensitiveActionContext(type, id.toString(), branchId, scope, commandFingerprint)
    }
}

/** High-impact commands that require the current user to prove their PIN again. */
enum class SensitiveAction(
    val title: String,
    val requiredPermission: Permission,
    val ownerOnly: Boolean = false,
) {
    RESTORE_BACKUP("بازیابی نسخه پشتیبان", Permission.RESTORE, ownerOnly = true),
    FACTORY_RESET("بازنشانی کامل برنامه", Permission.MANAGE_USERS, ownerOnly = true),
    ADJUST_INVENTORY("اصلاح موجودی", Permission.INVENTORY_ADJUST),
    CLOSE_INVENTORY_PERIOD("بستن دوره انبار", Permission.INVENTORY_PERIOD_CLOSE),
    REOPEN_INVENTORY_PERIOD("بازگشایی دوره انبار", Permission.INVENTORY_PERIOD_CLOSE, ownerOnly = true),
    CLOSE_SALES_DAY("بستن روز فروش", Permission.SALES_DAY_CLOSE),
    REOPEN_SALES_DAY("بازگشایی روز فروش", Permission.SALES_DAY_CLOSE, ownerOnly = true),
    CLOSE_ACCOUNTING_PERIOD("بستن دوره مالی", Permission.ACCOUNTING_PERIOD_CLOSE),
    REOPEN_ACCOUNTING_PERIOD("بازگشایی دوره مالی", Permission.ACCOUNTING_PERIOD_CLOSE, ownerOnly = true),
}

interface SecurityRepository {
    val users: Flow<List<AppUserRecord>>
    val currentUser: Flow<AppUserRecord?>
    suspend fun save(id: Long?, draft: UserDraft): Long
    suspend fun deactivate(id: Long)
    suspend fun switchUser(id: Long, pin: String)
    suspend fun setRecoveryCode(id: Long, recoveryCode: String)
    suspend fun resetPinWithRecovery(id: Long, recoveryCode: String, newPin: String)
    suspend fun authorizeSensitiveAction(action: SensitiveAction, pin: String, context: SensitiveActionContext = SensitiveActionContext.global(action))
    suspend fun dataScope(userId: Long): UserDataScope
    suspend fun updateDataScope(scope: UserDataScope, reason: String)
    suspend fun logout()
}
