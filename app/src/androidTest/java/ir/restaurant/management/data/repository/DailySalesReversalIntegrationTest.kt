package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryLotEntity
import ir.restaurant.management.data.db.MenuItemEntity
import ir.restaurant.management.data.db.RecipeVersionEntity
import ir.restaurant.management.data.db.RecipeVersionIngredientEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesDraft
import ir.restaurant.management.domain.sales.DailySalesReversalDraft
import ir.restaurant.management.domain.sales.DailySalesStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySalesReversalIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private var now = 1_000_000L

    @Before
    fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
            .save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        Unit
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reversalRestoresInventoryLotsAndAccountingThenAllowsCorrectedRepost() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "برنج تست",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                stockMicros = 10_000_000,
                inventoryValueRial = 100_000,
                trackLot = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val menuId = database.recipeDao().insertMenuItem(
            MenuItemEntity(name = "غذای تست", salePriceRial = 25_000, createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val recipeVersionId = database.recipeDao().insertVersion(
            RecipeVersionEntity(
                menuItemId = menuId,
                revisionNo = 1,
                effectiveFromEpochDay = 1,
                preparationWasteBasisPoints = 1_000,
                cookingWasteBasisPoints = 0,
                packagingCostRial = 2_000,
                directLaborCostRial = 3_000,
                allocatedOverheadRial = 1_000,
                createdBy = "TEST",
                createdAtEpochMillis = now,
            ),
        )
        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(recipeVersionId, itemId, 1_000_000)),
        )
        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())
        val lotId = database.inventoryLotDao().insert(
            InventoryLotEntity(
                itemId = itemId,
                locationId = locationId,
                lotCode = "LOT-1",
                receivedEpochDay = 20990,
                expiryEpochDay = null,
                quantityMicros = 10_000_000,
                unitCostRial = 10_000,
                barcode = null,
                createdByActorId = 42,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val repository = LocalDailySalesRepository(database, authorizer, clock = { now })
        val saleDay = 21000L
        val saleDraft = DailySalesDraft(
            businessEpochDay = saleDay,
            discountRial = 0,
            serviceRial = 0,
            taxRial = 0,
            cashRial = 50_000,
            cardRial = 0,
            transferRial = 0,
            lines = listOf(DailyMenuSaleDraft(menuId, 2_000_000, 50_000)),
            branchId = 1L,
            locationId = locationId,
        )
        val summaryId = repository.createDraft(saleDraft)
        repository.confirm(summaryId)
        repository.postConfirmed(summaryId)
        repository.postConfirmed(summaryId)
        assertEquals(DailySalesStatus.POSTED.name, database.dailySalesDao().summary(summaryId)?.status)

        assertEquals(8_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(80_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        assertEquals(8_000_000L, database.inventoryLotDao().byId(lotId)?.quantityMicros)
        val costSnapshot = database.dailySalesDao().lines(summaryId).single()
        assertEquals(22_000L, costSnapshot.foodCostSnapshotRial)
        assertEquals(4_000L, costSnapshot.packagingCostSnapshotRial)
        assertEquals(6_000L, costSnapshot.directLaborCostSnapshotRial)
        assertEquals(2_000L, costSnapshot.allocatedOverheadSnapshotRial)
        assertEquals(recipeVersionId, costSnapshot.recipeVersionId)
        // Snapshot is independent from later valuation/configuration changes.
        assertEquals(34_000L, listOfNotNull(costSnapshot.foodCostSnapshotRial, costSnapshot.packagingCostSnapshotRial, costSnapshot.directLaborCostSnapshotRial, costSnapshot.allocatedOverheadSnapshotRial).sum())
        val accounting = LocalAccountingRepository(database, clock = { now }, authorizer = authorizer)
        val postedDayProfitLoss = accounting.profitLoss(saleDay, saleDay).first()
        assertEquals(50_000L, postedDayProfitLoss.revenueRial)
        assertEquals(20_000L, postedDayProfitLoss.expenseRial)
        assertEquals(30_000L, postedDayProfitLoss.netProfitRial)

        now += 1_000
        val reversalDraft = DailySalesReversalDraft(summaryId, saleDay + 1, "اصلاح مبلغ صندوق")
        repository.reverse(reversalDraft)
        repository.reverse(reversalDraft)

        val reversed = requireNotNull(database.dailySalesDao().summary(summaryId))
        assertEquals(saleDay + 1, reversed.reversedAtEpochDay)
        assertEquals("اصلاح مبلغ صندوق", reversed.reversalReason)
        assertNotNull(reversed.reversalJournalEntryId)
        assertNotNull(reversed.reversalCostJournalEntryId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='DAILY_SALES_REVERSAL' AND sourceId=$summaryId"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='DAILY_SALES_COGS_REVERSAL' AND sourceId=$summaryId"))
        assertEquals(10_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(100_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        assertEquals(10_000_000L, database.inventoryLotDao().byId(lotId)?.quantityMicros)

        val profitLoss = accounting.profitLoss(saleDay, saleDay + 1).first()
        assertEquals(0L, profitLoss.revenueRial)
        assertEquals(0L, profitLoss.expenseRial)
        assertEquals(0L, profitLoss.netProfitRial)
        val originalPeriod = accounting.profitLoss(saleDay, saleDay).first()
        assertEquals(30_000L, originalPeriod.netProfitRial)
        val reversedPeriodReport = repository.observeReport(saleDay, saleDay + 1).first()
        assertEquals(2, reversedPeriodReport.dayCount)
        assertEquals(0L, reversedPeriodReport.salesRial)
        assertEquals(50_000L, repository.observeReport(saleDay, saleDay).first().salesRial)

        now += 1_000
        val replacementId = repository.post(
            DailySalesDraft(
                businessEpochDay = saleDay,
                discountRial = 0,
                serviceRial = 0,
                taxRial = 0,
                cashRial = 25_000,
                cardRial = 0,
                transferRial = 0,
                lines = listOf(DailyMenuSaleDraft(menuId, 1_000_000, 25_000)),
                branchId = 1L,
                locationId = locationId,
            ),
        )
        assertTrue(replacementId > summaryId)
        assertEquals(2, repository.observe("").first().size)
        val correctedReport = repository.observeReport(saleDay, saleDay + 1).first()
        assertEquals(2, correctedReport.dayCount)
        assertEquals(25_000L, correctedReport.salesRial)
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
