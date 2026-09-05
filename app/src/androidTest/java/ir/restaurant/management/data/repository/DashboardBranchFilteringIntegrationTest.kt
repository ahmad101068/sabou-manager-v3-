package ir.restaurant.management.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.BranchLegacyAliasEntity
import ir.restaurant.management.data.db.DailySalesSummaryEntity
import ir.restaurant.management.data.db.PurchaseEntity
import ir.restaurant.management.data.db.SalesInvoiceEntity
import ir.restaurant.management.data.db.SupplierEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Regression proof that Dashboard selection identity is the canonical Branch master id. */
@RunWith(AndroidJUnit4::class)
class DashboardBranchFilteringIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() { database.close() }

    @Test
    fun canonicalBranchIdsIsolateDashboardDataAndIgnoreLegacySalesTruth() = runBlocking {
        val fixture = seedTwoBranches()
        val repository = DashboardRepository(database)

        val branchA = repository.observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = fixture.idA).first()
        val branchB = repository.observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = fixture.idB).first()
        val all = repository.observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = null).first()

        assertEquals(fixture.idA, branchA.selectedBranchId)
        assertEquals("Branch A", branchA.selectedBranchName)
        assertEquals(100L, branchA.grossSalesRial)
        assertEquals(100L, branchA.postedInvoiceSalesRial)
        assertEquals(1_000L, branchA.purchaseRial)

        assertEquals(fixture.idB, branchB.selectedBranchId)
        assertEquals("Branch B", branchB.selectedBranchName)
        assertEquals(300L, branchB.grossSalesRial)
        assertEquals(300L, branchB.postedInvoiceSalesRial)
        assertEquals(3_000L, branchB.purchaseRial)

        assertEquals(400L, all.grossSalesRial)
        assertEquals(400L, all.postedInvoiceSalesRial)
        assertEquals(4_000L, all.purchaseRial)
        assertEquals(setOf(fixture.idA, fixture.idB), all.availableBranches.map { it.id }.toSet())
    }

    @Test
    fun branchRenameKeepsCanonicalIdAndIdScopedDashboardData() = runBlocking {
        val fixture = seedTwoBranches()
        val original = requireNotNull(database.branchDao().byId(fixture.idA))
        assertEquals(1, database.branchDao().update(original.copy(name = "Branch A Renamed", updatedAtEpochMillis = original.updatedAtEpochMillis + 1)))

        val snapshot = DashboardRepository(database)
            .observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = fixture.idA)
            .first()

        assertEquals(fixture.idA, snapshot.selectedBranchId)
        assertEquals("Branch A Renamed", snapshot.selectedBranchName)
        assertEquals(1_000L, snapshot.purchaseRial)
        assertEquals("Branch A Renamed", snapshot.availableBranches.single { it.id == fixture.idA }.name)
    }

    @Test
    fun duplicateDisplayNamesRemainDeterministicForIdScopedDashboardData() = runBlocking {
        val fixture = seedTwoBranches()
        val a = requireNotNull(database.branchDao().byId(fixture.idA))
        val b = requireNotNull(database.branchDao().byId(fixture.idB))
        assertEquals(1, database.branchDao().update(a.copy(name = "Same Display Name", updatedAtEpochMillis = a.updatedAtEpochMillis + 1)))
        assertEquals(1, database.branchDao().update(b.copy(name = "Same Display Name", updatedAtEpochMillis = b.updatedAtEpochMillis + 1)))

        val repository = DashboardRepository(database)
        val snapshotA = repository.observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = fixture.idA).first()
        val snapshotB = repository.observeRange(fixture.day, fixture.day, DashboardPeriod.TODAY, branchId = fixture.idB).first()

        assertNotEquals(snapshotA.selectedBranchId, snapshotB.selectedBranchId)
        assertEquals("Same Display Name", snapshotA.selectedBranchName)
        assertEquals("Same Display Name", snapshotB.selectedBranchName)
        assertEquals(1_000L, snapshotA.purchaseRial)
        assertEquals(3_000L, snapshotB.purchaseRial)
        assertTrue(snapshotA.availableBranches.any { it.id == fixture.idA && it.name == "Same Display Name" })
        assertTrue(snapshotA.availableBranches.any { it.id == fixture.idB && it.name == "Same Display Name" })
    }

    private suspend fun seedTwoBranches(): Fixture {
        val day = 24_000L
        val now = day * 86_400_000L
        val idA = database.branchDao().insert(
            BranchEntity(globalId = "test:dashboard:branch:a", code = "DB-A", name = "Branch A", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val idB = database.branchDao().insert(
            BranchEntity(globalId = "test:dashboard:branch:b", code = "DB-B", name = "Branch B", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        require(idA > 0 && idB > 0 && idA != idB)
        database.branchDao().insertLegacyAlias(BranchLegacyAliasEntity(branchId = idA, aliasName = "Branch A", normalizedAlias = "branch a", createdAtEpochMillis = now))
        database.branchDao().insertLegacyAlias(BranchLegacyAliasEntity(branchId = idB, aliasName = "Branch B", normalizedAlias = "branch b", createdAtEpochMillis = now))

        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "Dashboard Supplier", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        database.purchaseDao().insert(purchase("PUR-A", supplierId, idA, "Branch A", day, 1_000))
        database.purchaseDao().insert(purchase("PUR-B", supplierId, idB, "Branch B", day, 3_000))
        database.dailySalesDao().insertSummary(dailySale("DS-A", idA, day, 100))
        database.dailySalesDao().insertSummary(dailySale("DS-B", idB, day, 300))
        // Legacy sales rows remain historical compatibility data and must not become a second dashboard truth.
        database.salesDao().insertInvoice(invoice("SAL-LEGACY-A", "legacy-cmd-a", "Branch A", day, 900))
        database.salesDao().insertInvoice(invoice("SAL-LEGACY-B", "legacy-cmd-b", "Branch B", day, 800))
        return Fixture(day, idA, idB)
    }

    private fun purchase(no: String, supplierId: Long, branchId: Long, branchName: String, day: Long, amount: Long) =
        PurchaseEntity(
            invoiceNo = no,
            supplierId = supplierId,
            purchaseEpochDay = day,
            branchName = branchName,
            branchId = branchId,
            commandId = "dashboard-purchase:$no",
            dueEpochDay = day + 30,
            totalRial = amount,
            paidRial = amount,
            paymentStatus = "PAID",
            paymentMethod = "CASH",
            reminderEnabled = false,
            reminderEpochDay = null,
            createdAtEpochMillis = day * 86_400_000L,
        )


    private fun dailySale(globalId: String, branchId: Long, day: Long, amount: Long) =
        DailySalesSummaryEntity(
            globalId = globalId, branchId = branchId, businessEpochDay = day,
            grossSalesRial = amount, discountRial = 0, serviceRial = 0, taxRial = 0, netSalesRial = amount,
            theoreticalCostRial = 0, cashRial = amount, cardRial = 0, transferRial = 0, notes = "canonical-dashboard-test",
            journalEntryId = null, costJournalEntryId = null, createdAtEpochMillis = day * 86_400_000L, status = "POSTED",
        )

    private fun invoice(no: String, command: String, branch: String, day: Long, amount: Long) =
        SalesInvoiceEntity(
            invoiceNo = no,
            commandId = command,
            businessEpochDay = day,
            branchName = branch,
            customerId = null,
            dueEpochDay = null,
            grossRial = amount,
            discountRial = 0,
            serviceRial = 0,
            taxRial = 0,
            netRial = amount,
            creditRial = 0,
            theoreticalCostRial = 0,
            journalEntryId = null,
            cogsJournalEntryId = null,
            status = "POSTED",
            notes = "branch-filter-test",
            createdByActorId = 1,
            createdAtEpochMillis = day * 86_400_000L,
        )

    private data class Fixture(val day: Long, val idA: Long, val idB: Long)
}
