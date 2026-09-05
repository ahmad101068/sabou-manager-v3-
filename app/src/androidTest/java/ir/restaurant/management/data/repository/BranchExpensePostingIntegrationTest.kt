package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchExpensePostingIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var assets: LocalAssetRepository
    private var now = 1_940_000_000_000L
    private val day = 22_200L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("branch-expense-owner", "مالک هزینه شعبه", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        database.branchDao().insert(BranchEntity(id = 2L, globalId = "test:expense:branch:2", code = "B2", name = "ونک", createdAtEpochMillis = now, updatedAtEpochMillis = now))
        assets = LocalAssetRepository(database, clock = { ++now }, authorizer = authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun branchMaintenancePostsRealBranchExpenseAndReachesBranchPnl() = runBlocking {
        val assetId = assets.save(null, assetDraft("EXP-B2", 2L))
        assets.recordMaintenance(
            AssetMaintenanceDraft(
                assetId = assetId,
                serviceType = "سرویس دوره‌ای",
                serviceEpochDay = day,
                costRial = 12_000_000L,
                contractor = "پیمانکار تست",
                paymentSource = AssetAcquisitionSource.CASH,
            ),
        )

        val journal = requireNotNull(database.accountingDao().entryBySource("ASSET_MAINTENANCE", assetId))
        assertEquals("BRANCH", journal.accountingScope)
        assertEquals(2L, journal.branchId)
        assertEquals(12_000_000L, database.accountingDao().branchProfitLoss(2L, day, day).operatingExpensesExcludingPayrollRial)
    }

    private fun assetDraft(code: String, branchId: Long?) = AssetDraft(
        assetCode = code,
        name = "دارایی تست $code",
        category = "تجهیزات",
        quantity = 1,
        purchaseEpochDay = day - 1,
        purchaseCostRial = 100_000_000L,
        salvageValueRial = 0,
        usefulLifeMonths = 60,
        location = "محل تست",
        notes = "branch expense producer fixture",
        acquisitionSource = AssetAcquisitionSource.BANK,
        branchId = branchId,
    )
}
