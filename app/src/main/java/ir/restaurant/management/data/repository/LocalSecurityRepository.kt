package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AppSessionEntity
import ir.restaurant.management.data.db.AppUserEntity
import ir.restaurant.management.data.db.UserBranchScopeEntity
import ir.restaurant.management.data.db.UserScopeProfileEntity
import ir.restaurant.management.data.db.UserWarehouseScopeEntity
import ir.restaurant.management.data.security.AccessDeniedException
import ir.restaurant.management.data.security.AuthenticationRequiredException
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserDataScope
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConcurrentUserModificationException(userId: Long, detail: String = "اطلاعات کاربر هم‌زمان تغییر کرده است؛ داده را تازه‌سازی کنید.") :
    IllegalStateException("$detail [userId=$userId]")

class LocalSecurityRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val authorizer: SessionAuthorizer,
    private val sensitiveActionGate: SensitiveActionGate = SensitiveActionGate(),
    private val deviceIdProvider: () -> String = { "local" },
) : SecurityRepository {
    private val auditWriter = LocalAuditEventWriter(db)
    override val users: Flow<List<AppUserRecord>> = db.securityDao().observeUsers().map { list ->
        list.map { entity -> entity.asRecord() }
    }
    override val currentUser: Flow<AppUserRecord?> = db.securityDao().observeCurrentUser().map { entity -> entity?.asRecord() }

    override suspend fun save(id: Long?, draft: UserDraft): Long {
        sensitiveActionGate.invalidateAll()
        val bootstrap = db.securityDao().userCount() == 0
        if (bootstrap) {
            require(id == null && draft.role == UserRole.OWNER) { "اولین کاربر باید مالک باشد." }
        } else {
            authorizer.require(Permission.MANAGE_USERS)
        }
        val username = draft.username.trim().lowercase()
        val displayName = draft.displayName.trim()
        require(username.length >= 3) { "نام کاربری باید حداقل ۳ حرف باشد." }
        require(displayName.isNotBlank()) { "نام نمایشی الزامی است." }
        require(draft.pin.length in 6..12 && draft.pin.all(Char::isDigit)) { "رمز باید بین ۶ تا ۱۲ رقم باشد." }
        if (bootstrap) requireValidRecoveryCode(draft.recoveryCode, draft.pin)
        if (draft.recoveryCode.isNotBlank()) requireValidRecoveryCode(draft.recoveryCode, draft.pin)
        val now = clock()
        return db.withTransaction {
            val duplicate = db.securityDao().byUsername(username)
            require(duplicate == null || duplicate.id == id) { "این نام کاربری قبلاً ثبت شده است." }
            val before = id?.let { db.securityDao().byId(it) }
            if (before?.role == UserRole.OWNER.name && draft.role != UserRole.OWNER) {
                require(db.securityDao().activeOwnerCount() > 1) { "تنها کاربر مالک قابل تنزل نقش نیست." }
            }
            val saved = if (id == null) {
                val savedId = db.securityDao().insert(AppUserEntity(username=username, displayName=displayName, pinHash=hashPin(draft.pin), recoveryCodeHash=hashRecoveryCode(draft.recoveryCode), role=draft.role.name, createdAtEpochMillis=now, updatedAtEpochMillis=now))
                requireNotNull(db.securityDao().byId(savedId))
            } else {
                val current = before ?: error("کاربر پیدا نشد.")
                val expectedVersion = draft.expectedRowVersion ?: current.rowVersion
                val newPinHash = hashPin(draft.pin)
                val newRecoveryHash = if (draft.recoveryCode.isBlank()) current.recoveryCodeHash else hashRecoveryCode(draft.recoveryCode)
                if (db.securityDao().updateMasterCas(
                        id=current.id, username=username, displayName=displayName, pinHash=newPinHash, recoveryCodeHash=newRecoveryHash,
                        role=draft.role.name, isActive=true, failedPinAttempts=current.failedPinAttempts,
                        lockUntilEpochMillis=current.lockUntilEpochMillis, now=now, expectedVersion=expectedVersion,
                    ) != 1) throw ConcurrentUserModificationException(id)
                requireNotNull(db.securityDao().byId(id))
            }
            if (bootstrap) {
                db.securityDao().setSession(AppSessionEntity(currentUserId = saved.id, updatedAtEpochMillis = now))
            }
            insertSecurityAudit(
                action = if (before == null) "USER_CREATE" else "USER_UPDATE",
                target = saved,
                before = before,
                after = saved,
                reason = if (bootstrap) "BOOTSTRAP_OWNER" else "AUTHORIZED_USER_MANAGEMENT",
                now = now,
                actorOverride = if (bootstrap) saved.displayName.ifBlank { saved.username } else null,
            )
            saved.id
        }
    }

    override suspend fun deactivate(id: Long) {
        authorizer.require(Permission.MANAGE_USERS)
        val now = clock()
        db.withTransaction {
            val before = db.securityDao().byId(id) ?: error("کاربر پیدا نشد.")
            if (db.securityDao().deactivateCas(id, now, before.rowVersion) != 1) throw ConcurrentUserModificationException(id)
            val after = requireNotNull(db.securityDao().byId(id))
            insertSecurityAudit("USER_DEACTIVATE", after, before, after, "AUTHORIZED_DEACTIVATION", now)
        }
    }

    override suspend fun switchUser(id: Long, pin: String) {
        sensitiveActionGate.invalidateAll()
        val now = clock()
        var authenticationFailure: String? = null
        db.withTransaction {
            val user = db.securityDao().byId(id) ?: error("کاربر پیدا نشد.")
            require(user.isActive) { "این کاربر غیرفعال است." }
            require(now >= user.lockUntilEpochMillis) { "ورود موقتاً قفل است؛ کمی بعد دوباره تلاش کنید." }
            if (!verifyPin(pin, user.pinHash)) {
                val attempts = (user.failedPinAttempts + 1).coerceAtMost(20)
                val lockUntil = if (attempts >= MAX_ATTEMPTS) {
                    now + lockDurationMillis(attempts)
                } else {
                    0L
                }
                val after = user.copy(failedPinAttempts = attempts, lockUntilEpochMillis = lockUntil, updatedAtEpochMillis = now)
                updateAuthState(user, after, "ثبت تلاش ورود ناموفق انجام نشد.")
                insertSecurityAudit("LOGIN_FAILURE", user, user, after, "INVALID_PIN", now, "AUTHENTICATION")
                authenticationFailure = "رمز ورود نادرست است."
            } else {
                val after = when {
                    needsRehash(user.pinHash) -> user.copy(pinHash = hashPin(pin), failedPinAttempts = 0, lockUntilEpochMillis = 0, updatedAtEpochMillis = now)
                    user.failedPinAttempts != 0 || user.lockUntilEpochMillis != 0L -> user.copy(failedPinAttempts = 0, lockUntilEpochMillis = 0, updatedAtEpochMillis = now)
                    else -> user
                }
                if (after != user) updateAuthState(user, after, "به‌روزرسانی وضعیت ورود انجام نشد.")
                db.securityDao().setSession(AppSessionEntity(currentUserId = id, updatedAtEpochMillis = now))
                insertSecurityAudit("LOGIN_SUCCESS", after, user, after, "PIN_VERIFIED", now, after.displayName.ifBlank { after.username })
            }
        }
        authenticationFailure?.let { throw IllegalArgumentException(it) }
    }

    override suspend fun setRecoveryCode(id: Long, recoveryCode: String) {
        authorizer.require(Permission.MANAGE_USERS)
        requireValidRecoveryCode(recoveryCode)
        val now = clock()
        db.withTransaction {
            val user = db.securityDao().byId(id) ?: error("کاربر پیدا نشد.")
            require(user.isActive) { "این کاربر غیرفعال است." }
            val after = user.copy(
                    recoveryCodeHash = hashRecoveryCode(recoveryCode),
                    failedPinAttempts = 0,
                    lockUntilEpochMillis = 0,
                    updatedAtEpochMillis = now,
                )
            updateAuthState(user, after, "ذخیره کد بازیابی انجام نشد.")
            insertSecurityAudit("RECOVERY_CODE_SET", after, user, after, "AUTHORIZED_RECOVERY_CONFIGURATION", now)
        }
    }

    override suspend fun resetPinWithRecovery(id: Long, recoveryCode: String, newPin: String) {
        sensitiveActionGate.invalidateAll()
        require(newPin.length in 6..12 && newPin.all(Char::isDigit)) { "رمز جدید باید بین ۶ تا ۱۲ رقم باشد." }
        requireValidRecoveryCode(recoveryCode, newPin)
        val now = clock()
        var authenticationFailure: String? = null
        db.withTransaction {
            val user = db.securityDao().byId(id) ?: error("کاربر پیدا نشد.")
            require(user.isActive) { "این کاربر غیرفعال است." }
            require(user.recoveryCodeHash.isNotBlank()) { "برای این کاربر کد بازیابی تعیین نشده است." }
            require(now >= user.lockUntilEpochMillis) { "بازیابی موقتاً قفل است؛ کمی بعد دوباره تلاش کنید." }
            if (!verifySecret(recoveryCode, user.recoveryCodeHash)) {
                val after = failedAttemptState(user, now)
                updateAuthState(user, after, "ثبت تلاش بازیابی ناموفق انجام نشد.")
                insertSecurityAudit("PIN_RECOVERY_FAILURE", user, user, after, "INVALID_RECOVERY_CODE", now, "AUTHENTICATION")
                authenticationFailure = "کد بازیابی نادرست است."
            } else {
                val after = user.copy(
                    pinHash = hashPin(newPin),
                    failedPinAttempts = 0,
                    lockUntilEpochMillis = 0,
                    updatedAtEpochMillis = now,
                )
                updateAuthState(user, after, "تغییر رمز ورود انجام نشد.")
                db.securityDao().setSession(AppSessionEntity(currentUserId = id, updatedAtEpochMillis = now))
                insertSecurityAudit("PIN_RECOVERY_SUCCESS", after, user, after, "RECOVERY_CODE_VERIFIED", now, after.displayName.ifBlank { after.username })
            }
        }
        authenticationFailure?.let { throw IllegalArgumentException(it) }
    }

    override suspend fun authorizeSensitiveAction(action: SensitiveAction, pin: String, context: SensitiveActionContext) {
        sensitiveActionGate.invalidateAll()
        val now = clock()
        var authorizationFailure: RuntimeException? = null
        var authorizedUserId: Long? = null
        db.withTransaction {
            val user = db.securityDao().currentUser() ?: throw AuthenticationRequiredException()
            if (!user.isActive) throw AuthenticationRequiredException()
            val role = UserRole.fromStoredValue(user.role)
            when {
                action.ownerOnly && role != UserRole.OWNER -> {
                    insertSensitiveActionAudit(action, "SENSITIVE_AUTH_DENIED", user, user, "OWNER_REQUIRED", now)
                    authorizationFailure = AccessDeniedException(action.requiredPermission)
                }
                !role.allows(action.requiredPermission) -> {
                    insertSensitiveActionAudit(action, "SENSITIVE_AUTH_DENIED", user, user, "PERMISSION_DENIED", now)
                    authorizationFailure = AccessDeniedException(action.requiredPermission)
                }
                now < user.lockUntilEpochMillis -> {
                    insertSensitiveActionAudit(action, "SENSITIVE_AUTH_BLOCKED", user, user, "ACCOUNT_LOCKED", now)
                    authorizationFailure = IllegalArgumentException("احراز مجدد موقتاً قفل است؛ کمی بعد دوباره تلاش کنید.")
                }
                !verifyPin(pin, user.pinHash) -> {
                    val after = failedAttemptState(user, now)
                    updateAuthState(user, after, "ثبت احراز مجدد ناموفق انجام نشد.")
                    insertSensitiveActionAudit(action, "SENSITIVE_AUTH_FAILURE", user, after, "INVALID_PIN", now)
                    authorizationFailure = IllegalArgumentException("رمز ورود برای انجام عملیات حساس نادرست است.")
                }
                else -> {
                    val after = when {
                        needsRehash(user.pinHash) -> user.copy(
                            pinHash = hashPin(pin),
                            failedPinAttempts = 0,
                            lockUntilEpochMillis = 0,
                            updatedAtEpochMillis = now,
                        )
                        user.failedPinAttempts != 0 || user.lockUntilEpochMillis != 0L -> user.copy(
                            failedPinAttempts = 0,
                            lockUntilEpochMillis = 0,
                            updatedAtEpochMillis = now,
                        )
                        else -> user
                    }
                    if (after != user) {
                        updateAuthState(user, after, "به‌روزرسانی وضعیت احراز مجدد انجام نشد.")
                    }
                    insertSensitiveActionAudit(action, "SENSITIVE_AUTH_SUCCESS", user, after, "PIN_REVERIFIED", now)
                    authorizedUserId = user.id
                }
            }
        }
        authorizationFailure?.let { throw it }
        sensitiveActionGate.grant(requireNotNull(authorizedUserId), action, context)
    }

    override suspend fun dataScope(userId: Long): UserDataScope {
        authorizer.require(Permission.MANAGE_USERS)
        requireNotNull(db.securityDao().byId(userId)) { "کاربر پیدا نشد." }
        val profile = db.phase3Dao().scopeProfile(userId)
        return UserDataScope(
            userId = userId,
            primaryBranchId = profile?.primaryBranchId,
            allowedBranchIds = db.phase3Dao().branchIds(userId).toSet(),
            allowedWarehouseIds = db.phase3Dao().warehouseIds(userId).toSet(),
            expectedRowVersion = db.securityDao().byId(userId)?.rowVersion,
        )
    }

    override suspend fun updateDataScope(scope: UserDataScope, reason: String) {
        val actor = authorizer.require(Permission.MANAGE_USERS)
        val valid = scope.validated()
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل تغییر محدوده داده الزامی است." }
        val target = db.securityDao().byId(valid.userId) ?: error("کاربر پیدا نشد.")
        require(UserRole.fromStoredValue(target.role) != UserRole.OWNER || valid.allowedBranchIds.isEmpty()) {
            "مالک به صورت سازمانی دسترسی کامل دارد و Scope محدود برای او ذخیره نمی‌شود."
        }
        val now = clock()
        db.withTransaction {
            val freshTarget = db.securityDao().byId(valid.userId) ?: error("کاربر پیدا نشد.")
            val expectedVersion = valid.expectedRowVersion ?: freshTarget.rowVersion
            if (db.securityDao().touchVersionCas(valid.userId, now, expectedVersion) != 1) throw ConcurrentUserModificationException(valid.userId)
            val beforeBranches = db.phase3Dao().branchIds(valid.userId).toSet()
            val beforeWarehouses = db.phase3Dao().warehouseIds(valid.userId).toSet()
            valid.allowedBranchIds.forEach { branchId ->
                requireNotNull(db.branchDao().byId(branchId)) { "یکی از شعب انتخاب‌شده وجود ندارد." }
            }
            valid.allowedWarehouseIds.forEach { locationId ->
                val location = db.inventoryLocationDao().byId(locationId) ?: error("یکی از انبارهای انتخاب‌شده وجود ندارد.")
                val branchId = location.branchId ?: error("انبار بدون شعبه قابل تخصیص نیست.")
                require(branchId in valid.allowedBranchIds) { "انبار انتخاب‌شده خارج از شعب مجاز کاربر است." }
            }
            db.phase3Dao().clearWarehouses(valid.userId)
            db.phase3Dao().clearBranches(valid.userId)
            db.phase3Dao().upsertScopeProfile(UserScopeProfileEntity(valid.userId, valid.primaryBranchId, now))
            valid.allowedBranchIds.sorted().forEach { db.phase3Dao().grantBranch(UserBranchScopeEntity(valid.userId, it, now)) }
            valid.allowedWarehouseIds.sorted().forEach { db.phase3Dao().grantWarehouse(UserWarehouseScopeEntity(valid.userId, it, now)) }
            auditWriter.append(
                AuditEventDraft(
                    action = AuditAction.of("USER_DATA_SCOPE_UPDATE"),
                    entityType = AuditEntityType.of("SECURITY_USER"),
                    entityId = valid.userId,
                    actorId = actor.id,
                    actorDisplayName = actor.displayName,
                    occurredAtEpochMillis = now,
                    businessEpochDay = null,
                    deviceId = deviceIdProvider().sanitizeAuditValue(120).ifBlank { "local" },
                    referenceType = "USER_DATA_SCOPE",
                    referenceId = valid.userId,
                    reason = normalizedReason,
                    beforeSnapshot = "branches=${beforeBranches.sorted()};warehouses=${beforeWarehouses.sorted()}",
                    afterSnapshot = "primary=${valid.primaryBranchId};branches=${valid.allowedBranchIds.sorted()};warehouses=${valid.allowedWarehouseIds.sorted()}",
                    correlationId = "security:scope:${valid.userId}:$now",
                    description = "تغییر محدوده شعبه/انبار کاربر ${target.username}",
                ),
            )
        }
    }

    override suspend fun logout() {
        sensitiveActionGate.invalidateAll()
        val now = clock()
        db.withTransaction {
            db.securityDao().currentUser()?.let { user ->
                insertSecurityAudit("LOGOUT", user, user, user, "USER_REQUEST", now, user.displayName.ifBlank { user.username })
            }
            db.securityDao().clearSession()
        }
    }

    private fun AppUserEntity.asRecord() = AppUserRecord(
        id = id,
        username = username,
        displayName = displayName,
        role = UserRole.fromStoredValue(role),
        isActive = isActive,
        hasRecoveryCode = recoveryCodeHash.isNotBlank(),
        rowVersion = rowVersion,
    )

    private suspend fun updateAuthState(before: AppUserEntity, after: AppUserEntity, message: String) {
        if (db.securityDao().updateAuthCas(
                id=before.id, pinHash=after.pinHash, recoveryCodeHash=after.recoveryCodeHash,
                failedPinAttempts=after.failedPinAttempts, lockUntilEpochMillis=after.lockUntilEpochMillis,
                now=after.updatedAtEpochMillis, expectedVersion=before.rowVersion,
            ) != 1) throw ConcurrentUserModificationException(before.id, message)
    }

    private fun requireValidRecoveryCode(recoveryCode: String, pin: String? = null) {
        require(recoveryCode.length in 8..16 && recoveryCode.all(Char::isDigit)) { "کد بازیابی باید بین ۸ تا ۱۶ رقم باشد." }
        require(pin == null || recoveryCode != pin) { "کد بازیابی نباید با رمز ورود یکسان باشد." }
    }

    private fun hashRecoveryCode(recoveryCode: String): String =
        if (recoveryCode.isBlank()) "" else hashPin(recoveryCode)

    private fun hashPin(pin: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(pin.toCharArray(), salt, CURRENT_ITERATIONS, 256)
        val hash = SecretKeyFactory.getInstance(CURRENT_ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return listOf(CURRENT_PREFIX, CURRENT_ITERATIONS.toString(), Base64.encodeToString(salt, Base64.NO_WRAP), Base64.encodeToString(hash, Base64.NO_WRAP)).joinToString("$")
    }

    private fun verifyPin(pin: String, encoded: String): Boolean = verifySecret(pin, encoded)

    private fun verifySecret(secret: String, encoded: String): Boolean {
        if (!encoded.startsWith("pbkdf2")) {
            val legacy = MessageDigest.getInstance("SHA-256")
                .digest(("restaurant-management-v3:" + secret).toByteArray())
                .joinToString("") { "%02x".format(it) }
            return MessageDigest.isEqual(legacy.toByteArray(), encoded.toByteArray())
        }
        val parts = encoded.split('$')
        if (parts.size != 4) return false
        val algorithm = when (parts[0]) {
            "pbkdf2", "pbkdf2-sha256" -> "PBKDF2WithHmacSHA256"
            "pbkdf2-sha1" -> "PBKDF2WithHmacSHA1"
            else -> return false
        }
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.decode(parts[2], Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[3], Base64.NO_WRAP) }.getOrNull() ?: return false
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, expected.size * 8)
        val actual = try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } catch (error: java.security.NoSuchAlgorithmException) {
            spec.clearPassword()
            throw IllegalStateException("الگوریتم PIN این نسخه پشتیبانی نمی‌شود؛ PIN را روی دستگاه قبلی تغییر دهید.", error)
        }
        spec.clearPassword()
        return MessageDigest.isEqual(expected, actual)
    }

    private fun failedAttemptState(user: AppUserEntity, now: Long): AppUserEntity {
        val attempts = (user.failedPinAttempts + 1).coerceAtMost(20)
        val lockUntil = if (attempts >= MAX_ATTEMPTS) now + lockDurationMillis(attempts) else 0L
        return user.copy(failedPinAttempts = attempts, lockUntilEpochMillis = lockUntil, updatedAtEpochMillis = now)
    }

    private suspend fun insertSecurityAudit(
        action: String,
        target: AppUserEntity,
        before: AppUserEntity?,
        after: AppUserEntity?,
        reason: String,
        now: Long,
        actorOverride: String? = null,
    ) {
        val actor = actorOverride ?: authorizer.actor()
        val device = deviceIdProvider().sanitizeAuditValue(120).ifBlank { "local" }
        val sessionActor = db.securityDao().currentUser()
        auditWriter.append(
            AuditEventDraft(
                action = AuditAction.of(action),
                entityType = AuditEntityType.of("SECURITY_USER"),
                entityId = target.id,
                actorId = if (actorOverride != null) target.id else sessionActor?.id ?: target.id,
                actorDisplayName = actor.sanitizeAuditValue(120).ifBlank { "SYSTEM" },
                occurredAtEpochMillis = now,
                businessEpochDay = null,
                deviceId = device,
                referenceType = "SECURITY_USER",
                referenceId = target.id,
                reason = reason,
                beforeSnapshot = before?.safeAuditState(),
                afterSnapshot = after?.safeAuditState(),
                correlationId = "security:${target.id}:$action:$now",
                description = "$action برای کاربر ${target.username.sanitizeAuditValue(80)}",
            ),
        )
    }

    private suspend fun insertSensitiveActionAudit(
        sensitiveAction: SensitiveAction,
        auditAction: String,
        before: AppUserEntity,
        after: AppUserEntity,
        reason: String,
        now: Long,
    ) {
        val device = deviceIdProvider().sanitizeAuditValue(120).ifBlank { "local" }
        auditWriter.append(
            AuditEventDraft(
                action = AuditAction.of(auditAction),
                entityType = AuditEntityType.of("SENSITIVE_ACTION"),
                entityId = before.id,
                actorId = before.id,
                actorDisplayName = before.displayName.ifBlank { before.username }.sanitizeAuditValue(120),
                occurredAtEpochMillis = now,
                businessEpochDay = null,
                deviceId = device,
                referenceType = "SENSITIVE_ACTION",
                referenceId = before.id,
                reason = reason,
                beforeSnapshot = before.safeAuditState(),
                afterSnapshot = after.safeAuditState(),
                correlationId = "sensitive:${before.id}:${sensitiveAction.name}:$now",
                description = "احراز عملیات حساس ${sensitiveAction.name}: $auditAction",
            ),
        )
    }

    private fun AppUserEntity.safeAuditState(): String =
        "username=${username.sanitizeAuditValue(80)},displayName=${displayName.sanitizeAuditValue(120)},role=$role,active=$isActive,recoveryConfigured=${recoveryCodeHash.isNotBlank()},failedAttempts=$failedPinAttempts,lockUntil=$lockUntilEpochMillis"

    private fun String.sanitizeAuditValue(maxLength: Int): String =
        replace('\n', ' ').replace('\r', ' ').replace('|', '/').take(maxLength)

    private fun needsRehash(encoded: String): Boolean {
        if (!encoded.startsWith("${CURRENT_PREFIX}$")) return true
        val iterations = encoded.split('$').getOrNull(1)?.toIntOrNull() ?: return true
        return iterations < CURRENT_ITERATIONS
    }

    private fun lockDurationMillis(attempts: Int): Long {
        val exponent = (attempts - MAX_ATTEMPTS).coerceIn(0, 5)
        return (BASE_LOCK_MILLIS shl exponent).coerceAtMost(MAX_LOCK_MILLIS)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCK_MILLIS = 30_000L
        const val MAX_LOCK_MILLIS = 15 * 60_000L
        const val CURRENT_PREFIX = "pbkdf2-sha1"
        const val CURRENT_ALGORITHM = "PBKDF2WithHmacSHA1"
        const val CURRENT_ITERATIONS = 310_000
    }
}
