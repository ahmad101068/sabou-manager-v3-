package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.InventoryBalanceQuery
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementQuery
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryReadServiceIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var service: LocalInventoryReadService
    private var ownerId = 0L
    private var cashierId = 0L
    private var itemId = 0L
    private var locationId = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, authorizer = authorizer, clock = { NOW })
        ownerId = security.save(null, UserDraft("read-owner", "مالک انبار", "123456", UserRole.OWNER, "87654321"))
        cashierId = security.save(null, UserDraft("read-cashier", "صندوقدار", "654321", UserRole.CASHIER))
        locationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())
        itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "برنج خوانش",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                sku = "SKU-READ-RICE",
                reorderPointMicros = 1_000_000,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        LocalInventoryCommandEngine(database, clock = { NOW }, authorizer = authorizer).receive(
            itemId = itemId,
            quantityMicros = 3_000_000,
            valueRial = 900_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 43,
            movementEpochDay = BUSINESS_DAY,
            context = InventoryCommandContext(
                idempotencyKey = "inventory-read-opening:43",
                correlationId = "inventory-read:test-43",
                actorId = ownerId,
                deviceId = "test-device",
                locationId = locationId,
                reasonCode = InventoryReasonCode.OPENING_BALANCE,
                reason = "مانده آزمون read model",
            ),
        )
        service = LocalInventoryReadService(database, authorizer)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun dashboardBalanceAndMovementRemainLedgerTraceableAndBounded() = runBlocking {
        val dashboard = service.dashboard(BUSINESS_DAY)
        assertEquals(900_000L, dashboard.totalInventoryValueRial)
        assertEquals(1, dashboard.activeItemCount)

        val balances = service.balances(InventoryBalanceQuery(query = "SKU-READ", limit = 1))
        assertEquals(1, balances.size)
        assertEquals(3_000_000L, balances.single().availableMicros)
        assertEquals(locationId, balances.single().locationId)

        val movements = service.movements(InventoryMovementQuery(itemId = itemId, limit = 1))
        assertEquals(1, movements.size)
        assertEquals(InventoryReferenceType.MIGRATION, movements.single().sourceType)
        assertEquals("inventory-read:test-43", movements.single().correlationId)
    }

    @Test
    fun readBoundaryDeniesRoleWithoutInventoryPermission() = runBlocking {
        security.switchUser(cashierId, "654321")
        try {
            service.dashboard(BUSINESS_DAY)
            fail("کاربر فاقد مجوز نباید read model انبار را مشاهده کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
    }

    private companion object {
        const val NOW = 1_950_000_000_000L
        const val BUSINESS_DAY = 23_000L
    }
}
