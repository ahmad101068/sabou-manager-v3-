package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import ir.restaurant.management.core.GlobalId

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String,
    val isSystem: Boolean,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["entryNo"], unique = true),
        Index(value = ["entryEpochDay"]),
        Index(value = ["sourceType", "sourceId"]),
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["correlationId"]),
        Index(value = ["reversalOfEntryId"], unique = true),
        Index(value = ["branchId"]),
        Index(value = ["accountingScope", "branchId", "entryEpochDay"]),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val sourceId: Long,
    val status: String = "DRAFT",
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val idempotencyKey: String? = "auto:${GlobalId.new().value}",
    @ColumnInfo(defaultValue = "''") val correlationId: String = "local:${GlobalId.new().value}",
    val reversalOfEntryId: Long? = null,
    val branchId: Long? = null,
    @ColumnInfo(defaultValue = "'UNASSIGNED_LEGACY'") val accountingScope: String = "UNASSIGNED_LEGACY",
    val postedAtEpochMillis: Long? = null,
    val postedByActorId: Long? = null,
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["code"],
            childColumns = ["accountCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("entryId"), Index("accountCode")],
)
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val accountCode: String,
    val debitRial: Long,
    val creditRial: Long,
    val memo: String = "",
)
