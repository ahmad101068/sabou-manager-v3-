package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.PostWasteCommand
import ir.restaurant.management.domain.inventory.WasteActionCommand
import ir.restaurant.management.domain.inventory.WasteApprovalPolicy
import ir.restaurant.management.domain.inventory.WasteReason
import ir.restaurant.management.domain.inventory.WasteStatus
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
class InventoryWasteWorkflowIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var actors: Actors
    private var now = 1_910_000_000_000L

    @Before
    fun setUp(): Unit = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
        val owner = security.save(null, UserDraft("waste-owner", "مالک ضایعات", "123456", UserRole.OWNER, "87654321"))
        val manager = security.save(null, UserDraft("waste-manager", "مدیر ضایعات", "654321", UserRole.MANAGER))
        val storekeeper = security.save(null, UserDraft("waste-store", "انباردار", "456789", UserRole.STOREKEEPER))
        actors = Actors(owner, manager, storekeeper)
        database.branchDao().insert(
            BranchEntity(id = 2L, globalId = "test:waste:branch:2", code = "W2", name = "ونک", createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun expiredLotWasteUsesWeightedAverageAndDrainsResidualValueAtomically() = runBlocking {
        val locationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())
        val location = requireNotNull(database.inventoryLocationDao().byId(locationId))
        database.inventoryLocationDao().update(location.copy(branchName = "ونک", branchId = 2L, updatedAtEpochMillis = ++now))
        val itemId = insertItem("خامه منقضی", trackLot = true, trackExpiry = true)
        LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).receive(
            itemId = itemId,
            quantityMicros = 3_000_000,
            valueRial = 1_000_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 71,
            movementEpochDay = 80,
            context = context("waste-receipt:71", actors.owner, locationId),
            lot = InventoryReceiptLot(lotNumber = "EXP-71", expiryEpochDay = 90),
            enforceLotPolicy = true,
        )
        val lot = requireNotNull(database.inventoryLotDao().byNaturalKey(itemId, locationId, "EXP-71"))
        val service = LocalInventoryWasteService(database, authorizer, clock = { ++now })

        val posted = service.submitAndPost(
            CreateWasteCommand(
                itemId = itemId,
                locationId = locationId,
                lotId = lot.id,
                quantityMicros = 3_000_000,
                reason = WasteReason.EXPIRED,
                businessEpochDay = 100,
                reasonDetail = "انقضای لات در سردخانه",
                actorId = actors.owner,
                commandId = GlobalId.new().value,
                correlationId = "inventory_waste:expired:71",
            ),
        )

        assertEquals(WasteStatus.POSTED, posted.status)
        assertEquals(333_333L, posted.unitCostRial)
        assertEquals(1_000_000L, posted.totalCostRial)
        assertEquals(0L, database.inventoryLotDao().byId(lot.id)?.quantityMicros)
        val depletedBalance = requireNotNull(database.inventoryBalanceDao().byKey(itemId, locationId))
        assertEquals(0L, depletedBalance.onHandMicros)
        assertEquals(0L, depletedBalance.inventoryValueRial)
        val depletedItem = requireNotNull(database.inventoryDao().byId(itemId))
        assertEquals(0L, depletedItem.stockMicros)
        assertEquals(0L, depletedItem.inventoryValueRial)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='WASTE' AND referenceId=${posted.id}"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='WASTE' AND sourceId=${posted.id}"))
        database.openHelper.writableDatabase.query(
            "SELECT accountingScope, branchId FROM journal_entries WHERE sourceType='WASTE' AND sourceId=${posted.id}",
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("BRANCH", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
        }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='INVENTORY_WASTE' AND action='POST'"))
    }

    @Test
    fun approvalRequiresPermissionAndDifferentActorThenCreatorCanPost() = runBlocking {
        val locationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())
        val itemId = insertItem("روغن کنترل ضایعات")
        LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).receive(
            itemId = itemId,
            quantityMicros = 1_000_000,
            valueRial = 200_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 72,
            movementEpochDay = 100,
            context = context("waste-opening:72", actors.owner, locationId),
        )
        val service = LocalInventoryWasteService(
            database,
            authorizer,
            approvalPolicy = WasteApprovalPolicy.ALWAYS_REQUIRE_APPROVAL,
            clock = { ++now },
        )
        val commandId = GlobalId.new().value
        val pending = service.submit(
            CreateWasteCommand(
                itemId = itemId,
                locationId = locationId,
                quantityMicros = 100_000,
                reason = WasteReason.SPOILAGE,
                businessEpochDay = 100,
                actorId = actors.owner,
                commandId = commandId,
                correlationId = "inventory_waste:approval:72",
            ),
        )
        assertEquals(WasteStatus.PENDING_APPROVAL, pending.status)

        try {
            service.approve(WasteActionCommand(pending.id, actors.owner, "تأیید توسط سازنده"))
            fail("سازنده نباید ضایعات خودش را تأیید کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.SeparationOfDutiesViolation)
        }

        security.switchUser(actors.storekeeper, "456789")
        try {
            service.approve(WasteActionCommand(pending.id, actors.storekeeper, "تلاش بدون مجوز"))
            fail("انباردار بدون مجوز تأیید نباید سند را تأیید کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }

        security.switchUser(actors.manager, "654321")
        assertEquals(
            WasteStatus.APPROVED,
            service.approve(WasteActionCommand(pending.id, actors.manager, "مقدار و علت بررسی شد")).status,
        )
        security.switchUser(actors.owner, "123456")
        val posted = service.post(PostWasteCommand(pending.id, actors.owner, commandId))
        assertEquals(WasteStatus.POSTED, posted.status)
        assertEquals(900_000L, database.inventoryBalanceDao().byKey(itemId, locationId)?.onHandMicros)
    }

    private suspend fun insertItem(name: String, trackLot: Boolean = false, trackExpiry: Boolean = false): Long =
        database.inventoryDao().insert(
            InventoryItemEntity(
                name = name,
                category = "مواد اولیه",
                unit = "کیلوگرم",
                trackLot = trackLot,
                trackExpiry = trackExpiry,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

    private fun context(key: String, actorId: Long, locationId: Long) = InventoryCommandContext(
        idempotencyKey = key,
        correlationId = "integration:$key",
        actorId = actorId,
        deviceId = "instrumentation",
        locationId = locationId,
        reasonCode = InventoryReasonCode.OPENING_BALANCE,
        reason = "آماده‌سازی آزمون ضایعات",
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private data class Actors(val owner: Long, val manager: Long, val storekeeper: Long)
}
