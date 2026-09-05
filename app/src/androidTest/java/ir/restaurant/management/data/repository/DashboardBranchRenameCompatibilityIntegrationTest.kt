package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.DailySalesSummaryEntity
import ir.restaurant.management.data.db.SalesInvoiceEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardBranchRenameCompatibilityIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var branches: LocalBranchRepository
    private var now = 1_980_000_000_000L
    private val day = 23_100L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("dashboard-rename-owner", "مالک داشبورد", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        branches = LocalBranchRepository(database, authorizer, clock = { ++now })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun canonicalDailySalesRemainAttributedToSameBranchIdAfterRename() = runBlocking {
        val branchId = branches.create(BranchDraft(name = "ونک", code = "VNK"))
        database.dailySalesDao().insertSummary(dailySale("rename-before", branchId, 100_000L))
        database.salesDao().insertInvoice(invoice("REN-LEGACY-BEFORE", "legacy-rename-before", "ونک", 900_000L))

        val before = DashboardRepository(database)
            .observeRange(day, day, DashboardPeriod.TODAY, branchId = branchId)
            .first()
        assertEquals(branchId, before.selectedBranchId)
        assertEquals(100_000L, before.grossSalesRial)

        branches.rename(branchId, "ونک مرکزی")
        database.dailySalesDao().insertSummary(dailySale("rename-after", branchId, 200_000L))
        database.salesDao().insertInvoice(invoice("REN-LEGACY-AFTER", "legacy-rename-after", "ونک مرکزی", 800_000L))

        val after = DashboardRepository(database)
            .observeRange(day, day, DashboardPeriod.TODAY, branchId = branchId)
            .first()
        assertEquals(branchId, after.selectedBranchId)
        assertEquals("ونک مرکزی", after.selectedBranchName)
        assertEquals(300_000L, after.grossSalesRial)
        assertEquals(300_000L, after.postedInvoiceSalesRial)
        assertEquals(listOf("ونک", "ونک مرکزی"), database.branchDao().legacyAliases(branchId))
    }

    @Test
    fun ambiguousLegacyDisplayNameIsNotDoubleAttributedAcrossBranches() = runBlocking {
        val first = branches.create(BranchDraft(name = "شعبه یک", code = "B1"))
        val second = branches.create(BranchDraft(name = "شعبه دو", code = "B2"))
        branches.rename(first, "نام مشترک")
        branches.rename(second, "نام مشترک")
        database.salesDao().insertInvoice(invoice("AMB-1", "ambiguous-name", "نام مشترک", 500_000L))

        val firstSnapshot = DashboardRepository(database).observeRange(day, day, DashboardPeriod.TODAY, branchId = first).first()
        val secondSnapshot = DashboardRepository(database).observeRange(day, day, DashboardPeriod.TODAY, branchId = second).first()
        val organization = DashboardRepository(database).observeRange(day, day, DashboardPeriod.TODAY, branchId = null).first()

        assertEquals(0L, firstSnapshot.grossSalesRial)
        assertEquals(0L, secondSnapshot.grossSalesRial)
        assertEquals(0L, organization.grossSalesRial)
    }


    private fun dailySale(globalId: String, branchId: Long, amount: Long) = DailySalesSummaryEntity(
        globalId = globalId, branchId = branchId, businessEpochDay = day, grossSalesRial = amount, discountRial = 0L,
        serviceRial = 0L, taxRial = 0L, netSalesRial = amount, theoreticalCostRial = 0L, cashRial = amount,
        cardRial = 0L, transferRial = 0L, notes = "canonical-rename-dashboard-regression", journalEntryId = null,
        costJournalEntryId = null, createdAtEpochMillis = ++now, status = "POSTED",
    )

    private fun invoice(no: String, command: String, branchName: String, amount: Long) = SalesInvoiceEntity(
        invoiceNo = no,
        commandId = command,
        businessEpochDay = day,
        branchName = branchName,
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
        notes = "branch-rename-dashboard-regression",
        createdByActorId = 1,
        createdAtEpochMillis = ++now,
    )
}
