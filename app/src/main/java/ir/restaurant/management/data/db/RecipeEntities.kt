package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items", indices = [Index(value = ["name"], unique = true)])
data class MenuItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    val salePriceRial: Long = 0,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

/**
 * Legacy latest-recipe mirror kept only for backward-compatible migrations.
 * New code must read/write [RecipeVersionEntity] and [RecipeVersionIngredientEntity].
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["menuItemId", "inventoryItemId"],
    foreignKeys = [
        ForeignKey(entity = MenuItemEntity::class, parentColumns = ["id"], childColumns = ["menuItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["inventoryItemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("inventoryItemId")],
)
data class RecipeIngredientEntity(
    val menuItemId: Long,
    val inventoryItemId: Long,
    val quantityMicrosPerUnit: Long,
)

/** Immutable recipe header. Every formula change appends a new revision. */
@Entity(
    tableName = "recipe_versions",
    foreignKeys = [
        ForeignKey(
            entity = MenuItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["menuItemId", "revisionNo"], unique = true),
        Index(value = ["menuItemId", "effectiveFromEpochDay"]),
        Index("createdAtEpochMillis"),
    ],
)
data class RecipeVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuItemId: Long,
    val revisionNo: Int,
    val effectiveFromEpochDay: Long,
    @ColumnInfo(defaultValue = "1000000") val yieldMicros: Long = 1_000_000L,
    @ColumnInfo(defaultValue = "0") val portionWeightMicros: Long = 0L,
    @ColumnInfo(defaultValue = "0") val preparationWasteBasisPoints: Int = 0,
    @ColumnInfo(defaultValue = "0") val cookingWasteBasisPoints: Int = 0,
    @ColumnInfo(defaultValue = "0") val packagingCostRial: Long = 0L,
    @ColumnInfo(defaultValue = "0") val directLaborCostRial: Long = 0L,
    @ColumnInfo(defaultValue = "0") val allocatedOverheadRial: Long = 0L,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val createdBy: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    val parentVersionId: Long? = null,
)

/** Immutable ingredient rows belonging to one recipe revision. */
@Entity(
    tableName = "recipe_version_ingredients",
    primaryKeys = ["recipeVersionId", "inventoryItemId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("inventoryItemId")],
)
data class RecipeVersionIngredientEntity(
    val recipeVersionId: Long,
    val inventoryItemId: Long,
    val quantityMicrosPerUnit: Long,
)
