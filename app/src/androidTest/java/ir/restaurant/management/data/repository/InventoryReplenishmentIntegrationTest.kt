package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryDemandUsagePolicy
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryReplenishmentQuery
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
class InventoryReplenishmentIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var service: LocalInventoryReplenishmentService
    private var now = 1_930_000_000_000L
    private var ownerId = 0L
    private var cashierId = 0L
    private var itemId = 0L
    private var locationId = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
        ownerId = security.save(null, UserDraft("replenish-owner", "مالک تأمین", "123456", UserRole.OWNER, "87654321"))
        cashierId = security.save(null, UserDraft("replenish-cash", "صندوقدار تأمین", "654321", UserRole.CASHIER))
        locationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())
        itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "مرغ تأمین",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                minimumStockMicros = 2_000_000,
                maximumStockMicros = 10_000_000,
                safetyStockMicros = 1_000_000,
                reorderPointMicros = 2_000_000,
                leadTimeDays = 2,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val engine = LocalInventoryCommandEngine(database, clock = { ++now }, authorizer = authorizer)
        engine.receive(
            itemId = itemId,
            quantityMicros = 4_000_000,
            valueRial = 800_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 91,
            movementEpochDay = BUSINESS_DAY - 10,
            context = context("replenish-opening:91", InventoryReasonCode.OPENING_BALANCE),
        )
        engine.issue(
            itemId = itemId,
            quantityMicros = 3_000_000,
            valueRial = 600_000,
            movementType = InventoryMovementType.RECIPE_CONSUMPTION,
            referenceType = InventoryReferenceType.RECIPE,
            referenceId = 92,
            movementEpochDay = BUSINESS_DAY - 5,
            context = context("replenish-recipe:92", InventoryReasonCode.SALES_CONSUMPTION),
        )
        engine.issue(
            itemId = itemId,
            quantityMicros = 500_000,
            valueRial = 100_000,
            movementType = InventoryMovementType.WASTE,
            referenceType = InventoryReferenceType.WASTE,
            referenceId = 93,
            movementEpochDay = BUSINESS_DAY - 2,
            context = context("replenish-waste:93", InventoryReasonCode.WASTE),
        )
        service = LocalInventoryReplenishmentService(database, authorizer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun boundedLocationQueryUsesLedgerDemandAndExcludesWasteByDefault() = runBlocking {
        val recommendation = service.recommendations(
            InventoryReplenishmentQuery(
                locationId = locationId,
                asOfEpochDay = BUSINESS_DAY,
                actionableOnly = true,
                limit = 50,
            ),
        ).single { it.itemId == itemId }

        assertEquals(500_000L, recommendation.onHandMicros)
        assertEquals(3_000_000L, recommendation.averageDailyUsageMicros * 30L)
        assertEquals(2_000_000L, recommendation.reorderPointMicros)
        assertTrue(recommendation.suggestedQuantityMicros > 0)

        val includingWaste = requireNotNull(
            service.recommendation(
                itemId,
                locationId,
                BUSINESS_DAY,
                InventoryDemandUsagePolicy.INCLUDE_WASTE,
            ),
        )
        assertEquals(3_500_010L, includingWaste.averageDailyUsageMicros * 30L)
    }

    @Test
    fun cashierCannotReadInventoryRecommendationBoundary() = runBlocking {
        security.switchUser(cashierId, "654321")
        try {
            service.recommendation(itemId, null, BUSINESS_DAY)
            fail("کاربر بدون مجوز مشاهده انبار نباید پیشنهاد تأمین را بخواند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
    }

    private fun context(key: String, reasonCode: InventoryReasonCode) = InventoryCommandContext(
        idempotencyKey = key,
        correlationId = "integration:$key",
        actorId = ownerId,
        deviceId = "instrumentation",
        locationId = locationId,
        reasonCode = reasonCode,
        reason = "آماده‌سازی آزمون پیشنهاد تأمین",
    )

    private companion object {
        const val BUSINESS_DAY = 21_200L
    }
}
