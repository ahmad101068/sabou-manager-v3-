package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchRenameReferenceIntegrityTest {
    private lateinit var database: AppDatabase
    private lateinit var branches: LocalBranchRepository
    private lateinit var assets: LocalAssetRepository
    private var now = 1_960_000_000_000L
    private val day = 22_400L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("rename-owner", "مالک تغییر نام", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        branches = LocalBranchRepository(database, authorizer, clock = { ++now })
        assets = LocalAssetRepository(database, clock = { ++now }, authorizer = authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun renameDoesNotChangeBusinessReferenceIdsOrJournalAttribution() = runBlocking {
        val branchId = branches.create(BranchDraft(name = "ونک", code = "VNK"))
        val assetId = assets.save(
            null,
            AssetDraft(
                assetCode = "REN-1",
                name = "دارایی شعبه",
                category = "تجهیزات",
                quantity = 1,
                purchaseEpochDay = day - 1,
                purchaseCostRial = 20_000_000L,
                salvageValueRial = 0,
                usefulLifeMonths = 24,
                location = "ونک",
                notes = "rename integrity fixture",
                acquisitionSource = AssetAcquisitionSource.BANK,
                branchId = branchId,
            ),
        )
        val acquisition = requireNotNull(database.accountingDao().entryBySource("ASSET_ACQUISITION", assetId))
        assertEquals(branchId, acquisition.branchId)

        branches.rename(branchId, "ونک مرکزی")
        assets.recordMaintenance(
            AssetMaintenanceDraft(
                assetId = assetId,
                serviceType = "سرویس پس از تغییر نام",
                serviceEpochDay = day,
                costRial = 1_000_000L,
                paymentSource = AssetAcquisitionSource.CASH,
            ),
        )

        assertEquals(branchId, requireNotNull(database.assetDao().assetById(assetId)).branchId)
        assertEquals(branchId, requireNotNull(database.accountingDao().entryBySource("ASSET_MAINTENANCE", assetId)).branchId)
        assertEquals("ونک مرکزی", requireNotNull(branches.getById(branchId)).name)
    }
}
