package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "app_users", indices = [Index(value = ["username"], unique = true)])
data class AppUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val displayName: String,
    val pinHash: String,
    @ColumnInfo(defaultValue = "''") val recoveryCodeHash: String = "",
    val role: String,
    val isActive: Boolean = true,
    val failedPinAttempts: Int = 0,
    val lockUntilEpochMillis: Long = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val rowVersion: Long = 0,
)

@Entity(tableName = "app_session")
data class AppSessionEntity(
    @PrimaryKey val singletonId: Int = 1,
    val currentUserId: Long,
    val updatedAtEpochMillis: Long,
)
