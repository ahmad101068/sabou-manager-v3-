package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetImpairmentDraft
import ir.restaurant.management.domain.assets.AssetLifecycleType
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.assets.AssetSaleDraft
import ir.restaurant.management.domain.assets.AssetTransferDraft
import ir.restaurant.management.domain.assets.DepreciationDraft
import ir.restaurant.management.domain.assets.DepreciationReversalDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
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
class AssetLifecycleIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var repository: LocalAssetRepository
    private var now = 3_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("owner-assets", "مالک دارایی", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        database.branchDao().insert(BranchEntity(id = 102L, globalId = "test:asset-branch:102", code = "A102", name = "شعبه دو", createdAtEpochMillis = now, updatedAtEpochMillis = now))
        repository = LocalAssetRepository(database, clock = { ++now }, syncRecorder = null, authorizer = authorizer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transfer_changesOperationalOwnership_preservesHistoricalCost_andAuditsLifecycle() = runBlocking {
        val assetId = repository.save(null, draft("AST-LIFE-1"))
        val historicalCost = requireNotNull(database.assetDao().assetById(assetId)).purchaseCostRial

        repository.transfer(
            AssetTransferDraft(
                assetId = assetId,
                toLocation = "انبار مرکزی",
                toBranch = "شعبه دو",
                toResponsiblePerson = "مسئول جدید",
                businessEpochDay = 20_010,
                reason = "انتقال بین شعب",
                toBranchId = 102L,
            ),
        )

        val after = requireNotNull(database.assetDao().assetById(assetId))
        assertEquals(historicalCost, after.purchaseCostRial)
        assertEquals("انبار مرکزی", after.location)
        assertEquals("شعبه دو", after.branch)
        assertEquals("مسئول جدید", after.responsiblePerson)
        val lifecycle = repository.observeLifecycle(assetId).first()
        assertTrue(lifecycle.any { it.type == AssetLifecycleType.PURCHASE })
        assertTrue(lifecycle.any { it.type == AssetLifecycleType.TRANSFER })
        val audits = database.auditLogDao().observeRecent(50).first()
        assertTrue(audits.any { it.entityType == "ASSET" && it.entityId == assetId && it.action == "TRANSFER" })
    }

    @Test
    fun maintenance_persistsSchedule_postsBalancedExpenseJournal_andKeepsAssetCostImmutable() = runBlocking {
        val assetId = repository.save(null, draft("AST-LIFE-2"))
        val costBefore = requireNotNull(database.assetDao().assetById(assetId)).purchaseCostRial

        repository.recordMaintenance(
            AssetMaintenanceDraft(
                assetId = assetId,
                serviceType = "سرویس دوره‌ای",
                serviceEpochDay = 20_020,
                costRial = 120_000L,
                contractor = "پیمانکار تست",
                note = "تعویض قطعه مصرفی",
                nextServiceEpochDay = 20_110,
                paymentSource = AssetAcquisitionSource.CASH,
            ),
        )

        val maintenance = repository.observeMaintenance(assetId).first().single()
        assertEquals(120_000L, maintenance.costRial)
        assertEquals(20_110L, maintenance.nextServiceEpochDay)
        assertEquals(costBefore, requireNotNull(database.assetDao().assetById(assetId)).purchaseCostRial)
        val maintenanceJournalId = assertBalanced("ASSET_MAINTENANCE", assetId)
        val maintenanceJournal = requireNotNull(database.accountingDao().entryById(maintenanceJournalId))
        assertEquals("BRANCH", maintenanceJournal.accountingScope)
        assertEquals(1L, maintenanceJournal.branchId)
        assertTrue(repository.observeLifecycle(assetId).first().any { it.type == AssetLifecycleType.MAINTENANCE })
    }

    @Test
    fun impairment_reducesBookValue_withoutMutatingHistoricalCost_andPostsBalancedJournal() = runBlocking {
        val assetId = repository.save(null, draft("AST-LIFE-3"))
        val before = requireNotNull(database.assetDao().assetById(assetId))

        repository.impair(AssetImpairmentDraft(assetId, 20_030, 1_100_000L, "افت ارزش فنی"))

        val after = requireNotNull(database.assetDao().assetById(assetId))
        assertEquals(before.purchaseCostRial, after.purchaseCostRial)
        assertEquals(1_100_000L, after.impairmentRial)
        assertEquals(before.purchaseCostRial - 1_100_000L, after.purchaseCostRial - after.accumulatedDepreciationRial - after.impairmentRial)
        assertBalanced("ASSET_IMPAIRMENT", assetId)
        assertTrue(repository.observeLifecycle(assetId).first().any { it.type == AssetLifecycleType.IMPAIRMENT })
    }

    @Test
    fun depreciation_supportsQuantityReasonIdempotencyAndControlledReversal() = runBlocking {
        val assetId = repository.save(null, draft("AST-DEP-5", quantity = 4))
        val firstCommand = GlobalId.new().value
        val firstId = repository.postDepreciation(
            DepreciationDraft(
                assetId = assetId,
                periodYear = 1405,
                periodMonth = 2,
                postingEpochDay = 20_060,
                quantity = 2,
                reason = "استهلاک دو واحد فعال",
                commandId = firstCommand,
            ),
        )
        val replayId = repository.postDepreciation(
            DepreciationDraft(assetId, 1405, 2, 20_060, 2, "استهلاک دو واحد فعال", firstCommand),
        )
        assertEquals(firstId, replayId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM asset_depreciations WHERE commandId='$firstCommand'"))

        val secondId = repository.postDepreciation(
            DepreciationDraft(assetId, 1405, 2, 20_060, 2, "تکمیل استهلاک دو واحد دیگر", GlobalId.new().value),
        )
        assertTrue(secondId > firstId)
        assertEquals(4L, scalar("SELECT COALESCE(SUM(quantity),0) FROM asset_depreciations WHERE assetId=$assetId AND periodYear=1405 AND periodMonth=2 AND reversedAtEpochMillis IS NULL"))
        val firstEntity = requireNotNull(database.assetDao().depreciationById(firstId))
        assertEquals(2, firstEntity.quantity)
        assertEquals("استهلاک دو واحد فعال", firstEntity.reason)
        assertBalancedEntry(firstEntity.journalEntryId)
        assertTrue(repository.observeLifecycle(assetId).first().count { it.type == AssetLifecycleType.DEPRECIATION } == 2)

        val beforeReverse = requireNotNull(database.assetDao().assetById(assetId)).accumulatedDepreciationRial
        val reversal = DepreciationReversalDraft(firstId, 20_061, "اصلاح ثبت اشتباه دو واحد")
        val reversalEntry = repository.reverseDepreciation(reversal)
        val replayReversalEntry = repository.reverseDepreciation(reversal)
        assertEquals(reversalEntry, replayReversalEntry)
        assertBalancedEntry(reversalEntry)
        val afterReverse = requireNotNull(database.assetDao().assetById(assetId)).accumulatedDepreciationRial
        assertEquals(beforeReverse - firstEntity.amountRial, afterReverse)
        val reversedEntity = requireNotNull(database.assetDao().depreciationById(firstId))
        assertTrue(reversedEntity.reversedAtEpochMillis != null)
        assertEquals("اصلاح ثبت اشتباه دو واحد", reversedEntity.reversalReason)
        assertTrue(repository.observeLifecycle(assetId).first().any { it.type == AssetLifecycleType.DEPRECIATION_REVERSAL })
        val audits = database.auditLogDao().observeRecent(100).first()
        assertTrue(audits.any { it.entityType == "ASSET" && it.entityId == assetId && it.action == "DEPRECIATE" })
        assertTrue(audits.any { it.entityType == "ASSET" && it.entityId == assetId && it.action == "REVERSE_DEPRECIATION" })
    }

    @Test
    fun impairmentRecalculatesFutureDepreciationWithoutRewritingHistoricalDepreciation() = runBlocking {
        val assetId = repository.save(null, draft("AST-IMP-6"))
        val firstId = repository.postDepreciation(
            DepreciationDraft(assetId, 1405, 1, 20_010, 1, "استهلاک پیش از کاهش ارزش", GlobalId.new().value),
        )
        val firstAmount = requireNotNull(database.assetDao().depreciationById(firstId)).amountRial
        repository.impair(AssetImpairmentDraft(assetId, 20_040, 1_100_000L, "کاهش ارزش پس از دوره اول"))
        val secondId = repository.postDepreciation(
            DepreciationDraft(assetId, 1405, 2, 20_071, 1, "استهلاک پس از کاهش ارزش", GlobalId.new().value),
        )
        val secondAmount = requireNotNull(database.assetDao().depreciationById(secondId)).amountRial
        assertTrue(secondAmount > 0)
        assertTrue("future depreciation should be re-amortized after impairment", secondAmount < firstAmount)
        assertEquals(firstAmount, requireNotNull(database.assetDao().depreciationById(firstId)).amountRial)
        assertEquals(12_000_000L, requireNotNull(database.assetDao().assetById(assetId)).purchaseCostRial)
    }

    @Test
    fun genericEditCannotBypassAssetTransferHistory_andNewOwnerCapitalAcquisitionIsBlocked() = runBlocking {
        val assetId = repository.save(null, draft("AST-GOV-6"))
        var transferBypassBlocked = false
        try {
            repository.save(assetId, draft("AST-GOV-6").copy(location = "محل بدون گردش انتقال"))
        } catch (_: IllegalArgumentException) {
            transferBypassBlocked = true
        }
        assertTrue(transferBypassBlocked)
        assertEquals("آشپزخانه اصلی", requireNotNull(database.assetDao().assetById(assetId)).location)

        var ownerCapitalBlocked = false
        try {
            repository.save(null, draft("AST-GOV-7").copy(acquisitionSource = AssetAcquisitionSource.OWNER_CAPITAL))
        } catch (_: IllegalArgumentException) {
            ownerCapitalBlocked = true
        }
        assertTrue(ownerCapitalBlocked)
    }

    @Test
    fun sale_afterDepreciationAndImpairment_preservesHistoricalCost_calculatesGain_andBalancesJournal() = runBlocking {
        val assetId = repository.save(null, draft("AST-LIFE-4"))
        repository.postDepreciation(DepreciationDraft(assetId, 1405, 1, 20_040))
        repository.impair(AssetImpairmentDraft(assetId, 20_041, 1_100_000L, "افت ارزش قبل از فروش"))
        val beforeSale = requireNotNull(database.assetDao().assetById(assetId))
        val expectedBook = beforeSale.purchaseCostRial - beforeSale.accumulatedDepreciationRial - beforeSale.impairmentRial
        val salePrice = expectedBook + 1_000_000L

        repository.sell(
            AssetSaleDraft(
                assetId = assetId,
                businessEpochDay = 20_050,
                salePriceRial = salePrice,
                receiptSource = AssetAcquisitionSource.BANK,
                buyer = "خریدار تست",
                reason = "فروش دارایی مازاد",
            ),
        )

        val sold = requireNotNull(database.assetDao().assetById(assetId))
        assertEquals("SOLD", sold.status)
        assertEquals(12_000_000L, sold.purchaseCostRial)
        assertEquals(beforeSale.accumulatedDepreciationRial, sold.accumulatedDepreciationRial)
        assertEquals(beforeSale.impairmentRial, sold.impairmentRial)
        assertEquals(salePrice, sold.salePriceRial)
        val saleEntry = assertBalanced("ASSET_SALE", assetId)
        val lines = database.accountingDao().linesByEntry(saleEntry)
        assertEquals(1_000_000L, lines.single { it.accountCode == "4102" }.creditRial)
        assertEquals(12_000_000L, lines.single { it.accountCode == "1501" }.creditRial)
        assertTrue(repository.observeLifecycle(assetId).first().any { it.type == AssetLifecycleType.SALE })
    }

    private suspend fun assertBalancedEntry(entryId: Long) {
        val lines = database.accountingDao().linesByEntry(entryId)
        assertTrue(lines.isNotEmpty())
        assertEquals(lines.sumOf { it.debitRial }, lines.sumOf { it.creditRial })
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private suspend fun assertBalanced(sourceType: String, assetId: Long): Long {
        val entry = database.accountingDao().entryBySource(sourceType, assetId)
        assertNotNull("journal missing for $sourceType", entry)
        val id = requireNotNull(entry).id
        val lines = database.accountingDao().linesByEntry(id)
        assertTrue(lines.isNotEmpty())
        assertEquals(lines.sumOf { it.debitRial }, lines.sumOf { it.creditRial })
        return id
    }

    private fun draft(code: String, quantity: Int = 1) = AssetDraft(
        assetCode = code,
        name = "فر صنعتی تست",
        category = "تجهیزات آشپزخانه",
        quantity = quantity,
        purchaseEpochDay = 20_000,
        purchaseCostRial = 12_000_000L,
        salvageValueRial = 1_200_000L,
        usefulLifeMonths = 12,
        location = "آشپزخانه اصلی",
        notes = "دارایی تست چرخه عمر",
        acquisitionSource = AssetAcquisitionSource.BANK,
        branch = "شعبه یک",
        responsiblePerson = "مسئول اولیه",
        branchId = 1L,
    )
}
