package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.SupplierEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineDraft
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.purchase.PurchaseSettlementDraft
import ir.restaurant.management.domain.purchase.SettlementPaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchPurchasePostingIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalPurchaseRepository
    private var now = 1_920_000_000_000L
    private var locationId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("branch-purchase-owner", "مالک خرید شعبه", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        database.branchDao().insert(
            BranchEntity(id = 2L, globalId = "test:purchase:branch:2", code = "P2", name = "ونک", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        locationId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "P2-WH", name = "انبار ونک", branchName = "ونک", branchId = 2L, kind = "WAREHOUSE",
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
            ),
        )
        repository = LocalPurchaseRepository(database, clock = { ++now }, authorizer = authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun blankInternalInvoiceNumber_isAllocatedUniquelyAndPersisted() = runBlocking {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "تأمین‌کننده شماره‌گذاری", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "کالای شماره‌گذاری",
                category = "مواد اولیه",
                unit = "عدد",
                supplierId = supplierId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        suspend fun postBlank() = repository.post(
            PurchaseDraft(
                invoiceNo = "",
                supplierId = supplierId,
                purchaseEpochDay = 30_001L,
                dueEpochDay = 30_010L,
                paymentMethod = PurchasePaymentMethod.PAYABLE,
                reminderEnabled = false,
                reminderEpochDay = null,
                lines = listOf(PurchaseLineDraft(itemId, QuantityMicros.positive(1_000_000L), MoneyRial.of(1_000_000L))),
                branchId = 2L,
                locationId = locationId,
                emergencyReason = "خرید اضطراری آزمون",
            ),
        )

        val first = postBlank()
        val second = postBlank()
        org.junit.Assert.assertTrue(first.invoiceNo.isNotBlank())
        org.junit.Assert.assertTrue(second.invoiceNo.isNotBlank())
        org.junit.Assert.assertNotEquals(first.invoiceNo, second.invoiceNo)
        assertEquals(first.invoiceNo, requireNotNull(database.purchaseDao().byId(first.purchaseId)).invoiceNo)
        assertEquals(second.invoiceNo, requireNotNull(database.purchaseDao().byId(second.purchaseId)).invoiceNo)
    }

    @Test
    fun payablePurchase_cardSettlementUsesTreasuryAndApGlExactlyOnce() = runBlocking {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "تأمین‌کننده تسویه خزانه", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "کالای تسویه خزانه", category = "مواد اولیه", unit = "عدد", supplierId = supplierId,
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
            ),
        )
        val purchase = repository.post(
            PurchaseDraft(
                invoiceNo = "BR-AP-CARD", supplierId = supplierId, purchaseEpochDay = 30_020L, dueEpochDay = 30_030L,
                paymentMethod = PurchasePaymentMethod.PAYABLE, reminderEnabled = false, reminderEpochDay = null,
                lines = listOf(PurchaseLineDraft(itemId, QuantityMicros.positive(1_000_000L), MoneyRial.of(10_000_000L))),
                branchId = 2L,
                locationId = locationId,
                emergencyReason = "خرید اضطراری آزمون",
            ),
        )
        val commandId = GlobalId.new().value
        val settlement = repository.settle(
            PurchaseSettlementDraft(
                purchaseId = purchase.purchaseId, settlementEpochDay = 30_021L, amount = MoneyRial.of(4_000_000L),
                paymentMethod = SettlementPaymentMethod.CARD, referenceNo = "CARD-REF", notes = "تسویه کارتخوان", commandId = commandId,
            ),
        )
        val replay = repository.settle(
            PurchaseSettlementDraft(
                purchaseId = purchase.purchaseId, settlementEpochDay = 30_021L, amount = MoneyRial.of(4_000_000L),
                paymentMethod = SettlementPaymentMethod.CARD, referenceNo = "CARD-REF", notes = "تسویه کارتخوان", commandId = commandId,
            ),
        )
        assertEquals(settlement.journalEntryId, replay.journalEntryId)
        val journal = requireNotNull(database.accountingDao().entryById(settlement.journalEntryId))
        assertEquals("PURCHASE_SETTLEMENT", journal.sourceType)
        assertEquals("BRANCH", journal.accountingScope)
        assertEquals(2L, journal.branchId)
        val lines = database.accountingDao().linesByEntry(journal.id)
        assertEquals(4_000_000L, lines.single { it.accountCode == "2101" }.debitRial)
        assertEquals(4_000_000L, lines.single { it.accountCode == "1104" }.creditRial)
        assertEquals(0, lines.count { it.accountCode == "1101" || it.accountCode == "1102" })
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE commandId='$commandId'"))
        assertEquals(4_000_000L, requireNotNull(database.purchaseDao().byId(purchase.purchaseId)).paidRial)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM supplier_payables WHERE sourceType='PURCHASE' AND sourceId=${purchase.purchaseId}"))
        assertEquals(4_000_000L, scalar("SELECT settledRial FROM supplier_payables WHERE sourceType='PURCHASE' AND sourceId=${purchase.purchaseId}"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM supplier_payable_ledger WHERE entryType='SETTLEMENT' AND commandId='$commandId'"))
    }

    @Test
    fun branchPurchaseUsesCanonicalBranchButDoesNotBecomeOperatingExpense() = runBlocking {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(name = "تأمین‌کننده خرید شعبه", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده اولیه خرید شعبه",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                supplierId = supplierId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

        val posted = repository.post(
            PurchaseDraft(
                invoiceNo = "BR-P-2",
                supplierId = supplierId,
                purchaseEpochDay = 30_000L,
                dueEpochDay = 30_010L,
                paymentMethod = PurchasePaymentMethod.PAYABLE,
                reminderEnabled = false,
                reminderEpochDay = null,
                lines = listOf(PurchaseLineDraft(itemId, QuantityMicros.positive(1_000_000L), MoneyRial.of(12_000_000L))),
                branchId = 2L,
                locationId = locationId,
                emergencyReason = "خرید اضطراری آزمون",
            ),
        )

        val journalId = requireNotNull(posted.journalEntryId)
        val journal = requireNotNull(database.accountingDao().entryById(journalId))
        assertEquals("BRANCH", journal.accountingScope)
        assertEquals(2L, journal.branchId)
        val lines = database.accountingDao().linesByEntry(journalId)
        assertEquals(12_000_000L, lines.single { it.accountCode == "1301" }.debitRial)
        assertEquals(0L, database.accountingDao().branchProfitLoss(2L, 30_000L, 30_000L).operatingExpensesExcludingPayrollRial)
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

}
