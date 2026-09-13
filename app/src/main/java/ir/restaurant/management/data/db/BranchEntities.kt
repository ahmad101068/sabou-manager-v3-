package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Canonical ERP branch master. All new business identity uses [id]/branchId.
 * [name] is display metadata only and may be renamed without changing references.
 */
@Entity(
    tableName = "branches",
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["organizationId", "code"], unique = true),
        Index("name"),
        Index("isActive"),
        Index("status"),
    ],
)
data class BranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val organizationId: Long? = null,
    val code: String? = null,
    val name: String,
    val isActive: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)


/**
 * Stable compatibility boundary for legacy rows that persisted a branch display name.
 * New business identity remains branchId; aliases are read-only compatibility metadata.
 */
@Entity(
    tableName = "branch_legacy_aliases",
    foreignKeys = [
        ForeignKey(
            entity = BranchEntity::class,
            parentColumns = ["id"],
            childColumns = ["branchId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["branchId", "normalizedAlias"], unique = true),
        Index("branchId"),
        Index("normalizedAlias"),
    ],
)
data class BranchLegacyAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val branchId: Long,
    val aliasName: String,
    val normalizedAlias: String,
    val createdAtEpochMillis: Long,
)
