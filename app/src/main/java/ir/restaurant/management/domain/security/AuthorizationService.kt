package ir.restaurant.management.domain.security

data class AuthorizedActor(
    val id: Long,
    val displayName: String,
    val role: UserRole,
) {
    init {
        require(id > 0) { "authorized_actor_id_invalid" }
        require(displayName.isNotBlank()) { "authorized_actor_name_missing" }
    }
}

/**
 * Application/domain authorization boundary. UI visibility checks are convenience only.
 */
interface AuthorizationService {
    suspend fun actorIdentity(): AuthorizedActor
    suspend fun can(permission: Permission): Boolean
    suspend fun require(permission: Permission): AuthorizedActor
}
