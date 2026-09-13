package ir.restaurant.management.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockMovementHistoryIntegrationTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() = database.close()

    @Test fun itemHistoryIsIsolatedAndNewestFirst() = runBlocking {
        val firstItem = database.inventoryDao().insert(InventoryItemEntity(name = "قهوه", category = "مواد اولیه", unit = "کیلوگرم", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        val secondItem = database.inventoryDao().insert(InventoryItemEntity(name = "شیر", category = "مواد اولیه", unit = "لیتر", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        database.stockMovementDao().insert(StockMovementEntity(itemId = firstItem, movementType = "PURCHASE", quantityDeltaMicros = 2_000_000, valueDeltaRial = 200_000, referenceType = "PURCHASE", referenceId = 1, movementEpochDay = 10, notes = "ورود", createdAtEpochMillis = 1, actorId = 1, locationId = 1, unitCostRial = 100_000, reasonCode = "PURCHASE_RECEIPT"))
        database.stockMovementDao().insert(StockMovementEntity(itemId = firstItem, movementType = "WASTE", quantityDeltaMicros = -100_000, valueDeltaRial = -10_000, referenceType = "WASTE", referenceId = 2, movementEpochDay = 11, notes = "ضایعات", createdAtEpochMillis = 2, actorId = 1, locationId = 1, unitCostRial = 100_000, reasonCode = "WASTE"))
        database.stockMovementDao().insert(StockMovementEntity(itemId = secondItem, movementType = "PURCHASE", quantityDeltaMicros = 1_000_000, valueDeltaRial = 50_000, referenceType = "PURCHASE", referenceId = 3, movementEpochDay = 12, notes = "کالای دیگر", createdAtEpochMillis = 3, actorId = 1, locationId = 1, unitCostRial = 50_000, reasonCode = "PURCHASE_RECEIPT"))

        val history = database.stockMovementDao().observeForItem(firstItem).first()

        assertEquals(listOf("WASTE", "PURCHASE"), history.map { it.movementType })
        assertEquals(setOf(firstItem), history.map { it.itemId }.toSet())
        assertEquals(listOf(secondItem, firstItem, firstItem), database.stockMovementDao().observeRecent().first().map { it.itemId })
    }
}
