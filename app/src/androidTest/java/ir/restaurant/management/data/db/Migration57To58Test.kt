package ir.restaurant.management.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration57To58Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun phase5MigrationBackfillsSubstitutionDateAndMakesDepreciationAuditableAndReversible() {
        val substitutionDay = 20_100L
        helper.createDatabase(DB_NAME, 57).use { db ->
            db.execSQL(
                "INSERT INTO inventory_items(id,sku,name,category,unit,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,recipeUnit,recipeToStockNumerator,recipeToStockDenominator,stockMicros,inventoryValueRial,alertEnabled,alertThresholdMicros,isActive,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(10,'PH5-MIG-10','ماده اصلی','مواد اولیه','گرم','گرم',1,1,'گرم',1,1,1000000,1000000,1,0,1,1,1)",
            )
            db.execSQL(
                "INSERT INTO inventory_items(id,sku,name,category,unit,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,recipeUnit,recipeToStockNumerator,recipeToStockDenominator,stockMicros,inventoryValueRial,alertEnabled,alertThresholdMicros,isActive,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(11,'PH5-MIG-11','ماده جایگزین','مواد اولیه','گرم','گرم',1,1,'گرم',1,1,1000000,1000000,1,0,1,1,1)",
            )
            db.execSQL("INSERT INTO menu_items(id,name,category,salePriceRial,isActive,createdAtEpochMillis,updatedAtEpochMillis) VALUES(20,'غذای مهاجرت','غذا',1000000,1,1,1)")
            db.execSQL("INSERT INTO recipe_versions(id,menuItemId,revisionNo,effectiveFromEpochDay,createdBy,createdAtEpochMillis,status) VALUES(30,20,1,20000,'legacy',1,'ACTIVE')")
            db.execSQL(
                "INSERT INTO recipe_substitutions(id,recipeVersionId,originalInventoryItemId,substituteInventoryItemId,ratioNumerator,ratioDenominator,reason,approvedByActorId,createdAtEpochMillis) " +
                    "VALUES(40,30,10,11,2,1,'legacy substitution',1,${substitutionDay * MILLIS_PER_DAY})",
            )
            db.execSQL(
                "INSERT INTO fixed_assets(id,assetCode,name,category,quantity,purchaseEpochDay,purchaseCostRial,salvageValueRial,usefulLifeMonths,accumulatedDepreciationRial,location,status,notes,createdAtEpochMillis,updatedAtEpochMillis,branch,responsiblePerson,impairmentRial,acquisitionSource) " +
                    "VALUES(50,'LEG-50','دارایی قدیمی','تجهیزات',2,20000,12000000,1200000,12,900000,'محل قدیمی','ACTIVE','legacy',1,1,'شعبه قدیمی','مسئول قدیمی',0,'BANK')",
            )
            db.execSQL("INSERT INTO asset_depreciations(id,assetId,periodYear,periodMonth,amountRial,journalEntryId,createdAtEpochMillis) VALUES(60,50,1405,1,900000,999,1)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 58, true, MIGRATION_57_58).use { db ->
            db.query("SELECT effectiveFromEpochDay FROM recipe_substitutions WHERE id=40").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(substitutionDay, cursor.getLong(0))
            }
            db.query("SELECT quantity,postingEpochDay,reason,commandId,reversedAtEpochMillis,reversalEpochDay,reversalReason,reversalJournalEntryId FROM asset_depreciations WHERE id=60").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(0L, cursor.getLong(1))
                assertEquals("", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
                assertEquals("", cursor.getString(6))
                assertTrue(cursor.isNull(7))
            }
            assertIndex(db, "asset_depreciations", "index_asset_depreciations_assetId_periodYear_periodMonth", expectedUnique = false)
            assertIndex(db, "asset_depreciations", "index_asset_depreciations_commandId", expectedUnique = true)
            assertIndex(db, "recipe_substitutions", "index_recipe_substitutions_effectiveFromEpochDay", expectedUnique = false)
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    @Test
    fun migrationRegistryContainsForwardOnly57To58() {
        val registry = ALL_MIGRATIONS.toList()
        assertTrue(registry.any { it.startVersion == 57 && it.endVersion == 58 })
        assertFalse(registry.any { it.startVersion == 58 && it.endVersion == 57 })
    }

    private fun assertIndex(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, name: String, expectedUnique: Boolean) {
        db.query("PRAGMA index_list('$table')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val uniqueIndex = cursor.getColumnIndex("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == name) {
                    found = true
                    assertEquals(expectedUnique, cursor.getInt(uniqueIndex) == 1)
                }
            }
            assertTrue("missing index $name", found)
        }
    }

    private companion object {
        const val DB_NAME = "phase5-migration-57-58"
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
