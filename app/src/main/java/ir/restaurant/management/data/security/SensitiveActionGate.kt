package ir.restaurant.management.data.security

import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext

class SensitiveAuthenticationRequiredException(action: SensitiveAction) :
    IllegalStateException("برای «${action.title}» رمز ورود کاربر جاری را دوباره وارد کنید.")

/**
 * Process-local, user-bound and one-shot permits for high-impact commands.
 *
 * A permit is never persisted, does not survive process death and is consumed before the command
 * starts. The short timeout only bridges the reauthentication call and the following repository call.
 */
class SensitiveActionGate(
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val permitLifetimeMillis: Long = DEFAULT_PERMIT_LIFETIME_MILLIS,
) {
    private data class PermitKey(val userId: Long, val action: SensitiveAction, val contextKey: String)

    private val permits = mutableMapOf<PermitKey, Long>()

    init {
        require(permitLifetimeMillis in 1_000L..MAX_PERMIT_LIFETIME_MILLIS) {
            "زمان اعتبار مجوز عملیات حساس معتبر نیست."
        }
    }

    @Synchronized
    internal fun grant(userId: Long, action: SensitiveAction) = grant(userId, action, SensitiveActionContext.global(action))

    @Synchronized
    internal fun grant(userId: Long, action: SensitiveAction, context: SensitiveActionContext) {
        require(userId > 0L) { "کاربر مجوز عملیات حساس معتبر نیست." }
        val now = clockMillis()
        permits.entries.removeAll { it.value < now }
        permits[PermitKey(userId, action, context.permitKey)] = now + permitLifetimeMillis
    }

    @Synchronized
    internal fun requireAndConsume(userId: Long, action: SensitiveAction) =
        requireAndConsume(userId, action, SensitiveActionContext.global(action))

    @Synchronized
    internal fun requireAndConsume(userId: Long, action: SensitiveAction, context: SensitiveActionContext) {
        val expiresAt = permits.remove(PermitKey(userId, action, context.permitKey))
            ?: throw SensitiveAuthenticationRequiredException(action)
        if (clockMillis() > expiresAt) throw SensitiveAuthenticationRequiredException(action)
    }

    @Synchronized
    internal fun invalidateAll() {
        permits.clear()
    }

    private companion object {
        const val DEFAULT_PERMIT_LIFETIME_MILLIS = 30_000L
        const val MAX_PERMIT_LIFETIME_MILLIS = 120_000L
    }
}
