package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface SecurityDao {
    @Query("SELECT * FROM app_users ORDER BY isActive DESC, displayName")
    fun observeUsers(): Flow<List<AppUserEntity>>

    @Query("SELECT * FROM app_users WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AppUserEntity?

    @Query("SELECT * FROM app_users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun byUsername(username: String): AppUserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AppUserEntity): Long

    @Query("""
        UPDATE app_users SET
            username=:username, displayName=:displayName, pinHash=:pinHash, recoveryCodeHash=:recoveryCodeHash,
            role=:role, isActive=:isActive, failedPinAttempts=:failedPinAttempts, lockUntilEpochMillis=:lockUntilEpochMillis,
            updatedAtEpochMillis=:now, rowVersion=rowVersion+1
        WHERE id=:id AND rowVersion=:expectedVersion
    """)
    suspend fun updateMasterCas(
        id: Long, username: String, displayName: String, pinHash: String, recoveryCodeHash: String, role: String,
        isActive: Boolean, failedPinAttempts: Int, lockUntilEpochMillis: Long, now: Long, expectedVersion: Long,
    ): Int

    @Query("""
        UPDATE app_users SET pinHash=:pinHash, recoveryCodeHash=:recoveryCodeHash,
            failedPinAttempts=:failedPinAttempts, lockUntilEpochMillis=:lockUntilEpochMillis,
            updatedAtEpochMillis=:now, rowVersion=rowVersion+1
        WHERE id=:id AND rowVersion=:expectedVersion
    """)
    suspend fun updateAuthCas(
        id: Long, pinHash: String, recoveryCodeHash: String, failedPinAttempts: Int,
        lockUntilEpochMillis: Long, now: Long, expectedVersion: Long,
    ): Int

    @Query("UPDATE app_users SET isActive=0, updatedAtEpochMillis=:now, rowVersion=rowVersion+1 WHERE id=:id AND role!='OWNER' AND rowVersion=:expectedVersion")
    suspend fun deactivateCas(id: Long, now: Long, expectedVersion: Long): Int

    @Query("UPDATE app_users SET rowVersion=rowVersion+1, updatedAtEpochMillis=:now WHERE id=:id AND rowVersion=:expectedVersion")
    suspend fun touchVersionCas(id: Long, now: Long, expectedVersion: Long): Int

    @Query("SELECT u.* FROM app_session s INNER JOIN app_users u ON u.id = s.currentUserId WHERE s.singletonId = 1 LIMIT 1")
    fun observeCurrentUser(): Flow<AppUserEntity?>

    @Query("SELECT u.* FROM app_session s INNER JOIN app_users u ON u.id = s.currentUserId WHERE s.singletonId = 1 LIMIT 1")
    suspend fun currentUser(): AppUserEntity?

    @Query("SELECT * FROM app_session WHERE singletonId = 1 LIMIT 1")
    suspend fun currentSession(): AppSessionEntity?

    @Query("SELECT COUNT(*) FROM app_users")
    suspend fun userCount(): Int

    @Query("SELECT COUNT(*) FROM app_users WHERE role = 'OWNER' AND isActive = 1")
    suspend fun activeOwnerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSession(entity: AppSessionEntity)

    @Query("DELETE FROM app_session")
    suspend fun clearSession()
}
