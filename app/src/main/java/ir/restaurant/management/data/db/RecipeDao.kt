package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface RecipeDao {
    @Insert
    suspend fun insertMenuItem(entity: MenuItemEntity): Long

    @Update
    suspend fun updateMenuItem(entity: MenuItemEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(entity: RecipeVersionEntity): Long

    @Update
    suspend fun updateVersion(entity: RecipeVersionEntity): Int

    @Query("DELETE FROM recipe_version_ingredients WHERE recipeVersionId=:recipeVersionId")
    suspend fun deleteVersionIngredients(recipeVersionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersionIngredients(ingredients: List<RecipeVersionIngredientEntity>)

    @Query("SELECT COUNT(*) + 1 FROM recipe_versions WHERE menuItemId = :menuItemId")
    suspend fun nextRevisionNo(menuItemId: Long): Int

    @Query(
        """SELECT rv.* FROM recipe_versions rv
        WHERE rv.menuItemId = :menuItemId
          AND rv.effectiveFromEpochDay <= :epochDay
          AND (
            rv.status = 'ACTIVE'
            OR (
              rv.status = 'RETIRED'
              AND EXISTS(
                SELECT 1 FROM recipe_versions successor
                WHERE successor.menuItemId = rv.menuItemId
                  AND successor.status = 'ACTIVE'
                  AND successor.effectiveFromEpochDay > :epochDay
                  AND successor.effectiveFromEpochDay > rv.effectiveFromEpochDay
              )
            )
          )
        ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC
        LIMIT 1""",
    )
    suspend fun effectiveVersion(menuItemId: Long, epochDay: Long): RecipeVersionEntity?

    @Query("SELECT * FROM recipe_version_ingredients WHERE recipeVersionId = :recipeVersionId ORDER BY inventoryItemId")
    suspend fun versionIngredients(recipeVersionId: Long): List<RecipeVersionIngredientEntity>

    @Query("SELECT * FROM menu_items WHERE isActive = 1 ORDER BY name")
    fun observeActiveMenuItems(): Flow<List<MenuItemEntity>>

    @Query(
        """SELECT mi.*,
        CAST(COALESCE((
            SELECT COUNT(*) FROM recipe_version_ingredients rvi
            WHERE rvi.recipeVersionId = (
                SELECT rv.id FROM recipe_versions rv
                WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay
                ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1
            )
        ), 0) AS INTEGER) AS ingredientCount,
        CAST(COALESCE((
            SELECT rv.revisionNo FROM recipe_versions rv
            WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay
            ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1
        ), 0) AS INTEGER) AS revisionNo,
        COALESCE((
            SELECT rv.effectiveFromEpochDay FROM recipe_versions rv
            WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay
            ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1
        ), 0) AS effectiveFromEpochDay,
        COALESCE((SELECT rv.yieldMicros FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 1000000) AS yieldMicros,
        COALESCE((SELECT rv.portionWeightMicros FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS portionWeightMicros,
        COALESCE((SELECT rv.preparationWasteBasisPoints FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS preparationWasteBasisPoints,
        COALESCE((SELECT rv.cookingWasteBasisPoints FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS cookingWasteBasisPoints,
        COALESCE((SELECT rv.packagingCostRial FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS packagingCostRial,
        COALESCE((SELECT rv.directLaborCostRial FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS directLaborCostRial,
        COALESCE((SELECT rv.allocatedOverheadRial FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), 0) AS allocatedOverheadRial,
        COALESCE((SELECT rv.note FROM recipe_versions rv WHERE rv.menuItemId = mi.id AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1), '') AS note
        FROM menu_items mi
        WHERE mi.isActive = 1
        ORDER BY mi.name""",
    )
    fun observeActiveMenuItemsWithCoverage(epochDay: Long): Flow<List<MenuItemCoverageRow>>

    @Query(
        """SELECT rvi.inventoryItemId, i.name AS inventoryName, i.recipeUnit AS unit,
        i.recipeToStockNumerator, i.recipeToStockDenominator, rvi.quantityMicrosPerUnit
        FROM recipe_version_ingredients rvi
        INNER JOIN inventory_items i ON i.id = rvi.inventoryItemId
        WHERE rvi.recipeVersionId = (
            SELECT rv.id FROM recipe_versions rv
            WHERE rv.menuItemId = :menuItemId AND rv.status = 'ACTIVE' AND rv.effectiveFromEpochDay <= :epochDay
            ORDER BY rv.effectiveFromEpochDay DESC, rv.revisionNo DESC LIMIT 1
        )
        ORDER BY i.name""",
    )
    fun observeIngredientRows(menuItemId: Long, epochDay: Long): Flow<List<RecipeIngredientRow>>

    @Query("SELECT * FROM recipe_versions WHERE menuItemId = :menuItemId ORDER BY revisionNo DESC")
    fun observeVersions(menuItemId: Long): Flow<List<RecipeVersionEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :id AND isActive = 1 LIMIT 1")
    suspend fun activeMenuItem(id: Long): MenuItemEntity?
}



data class RecipeIngredientRow(
    val inventoryItemId: Long, val inventoryName: String, val unit: String,
    val recipeToStockNumerator: Long, val recipeToStockDenominator: Long,
    val quantityMicrosPerUnit: Long,
)
data class MenuItemCoverageRow(
    val id: Long,
    val name: String,
    val category: String,
    val salePriceRial: Long,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val ingredientCount: Int,
    val revisionNo: Int,
    val effectiveFromEpochDay: Long,
    val yieldMicros: Long,
    val portionWeightMicros: Long,
    val preparationWasteBasisPoints: Int,
    val cookingWasteBasisPoints: Int,
    val packagingCostRial: Long,
    val directLaborCostRial: Long,
    val allocatedOverheadRial: Long,
    val note: String,
)
