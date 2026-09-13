package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.SupplierEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft
import ir.restaurant.management.domain.purchase.SupplierOfferDraft
import ir.restaurant.management.domain.sales.CustomerDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtractedResponsibilityServicesIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private var now = 120_000L

    @Before
    fun setUp(): Unit = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, authorizer = authorizer, clock = { now }).save(
            null,
            UserDraft("refactor-owner", "مالک تست تفکیک مسئولیت", "123456", UserRole.OWNER, "87654321"),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun salesCustomerMasterService_ownsNumberingDuplicatePolicyOutstandingRuleAndAudit() = runBlocking {
        val service = SalesCustomerMasterService(database, authorizer, clock = { now++ })
        val firstId = service.save(
            null,
            CustomerDraft(
                name = "مشتری مستقل یک",
                phone = "09121234567",
                nationalId = "0012345678",
                creditLimitRial = 500_000L,
                branch = "شعبه الف",
            ),
        )
        val secondId = service.save(
            null,
            CustomerDraft(
                name = "مشتری مستقل دو",
                phone = "09129876543",
                nationalId = "0098765432",
                creditLimitRial = 300_000L,
                branch = "شعبه ب",
            ),
        )
        val first = requireNotNull(database.salesDao().customerById(firstId))
        val second = requireNotNull(database.salesDao().customerById(secondId))
        assertNotEquals(first.customerCode, second.customerCode)
        assertTrue(first.customerCode.isNotBlank() && second.customerCode.isNotBlank())
        assertEquals(2L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='CUSTOMER' AND action='CREATE'"))

        try {
            service.save(null, CustomerDraft(name = "مشتری تکراری", phone = first.phone))
            fail("شماره تماس تکراری باید در مرز Customer Master رد شود")
        } catch (_: IllegalStateException) {
            Unit
        }

        database.customerReceivableDao().insertLedger(
            CustomerReceivableLedgerEntity(
                customerId = firstId,
                businessEpochDay = 34_000L,
                entryType = "OPENING",
                debitRial = 10_000L,
                creditRial = 0L,
                sourceType = "TEST_OUTSTANDING",
                sourceId = 1L,
                reference = "refactor-outstanding",
                actorId = authorizer.actorIdentity().id,
                createdAtEpochMillis = now++,
            ),
        )
        try {
            service.deactivate(firstId)
            fail("مشتری دارای مانده نباید غیرفعال شود")
        } catch (_: IllegalArgumentException) {
            Unit
        }
        assertTrue(requireNotNull(database.salesDao().customerById(firstId)).isActive)
    }

    @Test
    fun procurementSourcingService_ownsValidatedPolicyAndSupplierOfferPersistence() = runBlocking {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(
                name = "تأمین‌کننده سرویس تأمین",
                createdAtEpochMillis = now++,
                updatedAtEpochMillis = now++,
            ),
        )
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "کالای سرویس تأمین",
                category = "مواد اولیه",
                unit = "عدد",
                safetyStockMicros = 5_000_000L,
                reorderPointMicros = 8_000_000L,
                leadTimeDays = 3,
                createdAtEpochMillis = now++,
                updatedAtEpochMillis = now++,
            ),
        )
        val service = ProcurementSourcingService(
            database = database,
            authorizer = authorizer,
            inventoryReplenishment = LocalInventoryReplenishmentService(database, authorizer),
            clock = { now++ },
            todayEpochDay = { 34_500L },
        )

        service.saveReplenishmentPolicy(
            ReplenishmentPolicyDraft(
                itemId = itemId,
                preferredSupplierId = supplierId,
                targetCoverDays = 14,
                leadTimeDays = 3,
                safetyStockMicros = 5_000_000L,
                orderMultipleMicros = 1_000_000L,
            ),
        )
        service.saveSupplierOffer(
            SupplierOfferDraft(
                supplierId = supplierId,
                itemId = itemId,
                supplierSku = "  SUP-001  ",
                unitCostRial = 72_000L,
                minimumOrderMicros = 2_000_000L,
                orderMultipleMicros = 1_000_000L,
                leadTimeDays = 3,
                validUntilEpochDay = 34_600L,
            ),
        )

        val policy = requireNotNull(database.procurementDao().replenishmentPolicy(itemId))
        assertEquals(supplierId, policy.preferredSupplierId)
        assertEquals(14, policy.targetCoverDays)
        val offers = database.procurementDao().validSupplierItemOffers(itemId, 34_500L)
        assertEquals(1, offers.size)
        assertEquals("SUP-001", offers.single().supplierSku)
        assertEquals(72_000L, offers.single().unitCostRial)
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
