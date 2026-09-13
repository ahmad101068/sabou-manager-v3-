package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.CreateInventoryCountSessionCommand
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryCountActionCommand
import ir.restaurant.management.domain.inventory.InventoryCountScope
import ir.restaurant.management.domain.inventory.InventoryCountStatus
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.PostInventoryCountCommand
import ir.restaurant.management.domain.inventory.RecordInventoryCountCommand
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryCountWorkflowIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var service: LocalInventoryCountService
    private var now = 1_900_000_000_000L
    private lateinit var fixture: Fixture

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
        val ownerId = security.save(
            null,
            UserDraft("count-owner", "مالک شمارش", "123456", UserRole.OWNER, "87654321"),
        )
        val managerId = security.save(
            null,
            UserDraft("count-manager", "مدیر شمارش", "654321", UserRole.MANAGER),
        )
        val cashierId = security.save(
            null,
            UserDraft("count-cashier", "صندوقدار شمارش", "456789", UserRole.CASHIER),
        )
        val branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)
        val locationId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "COUNT-WH-TEST",
                name = "انبار آزمون شمارش",
                branchName = "شعبه آزمون شمارش",
                branchId = branchId,
                kind = "WAREHOUSE",
                createdAtEpochMillis = now,
            ),
        )
        val scopeDb = database.openHelper.writableDatabase
        listOf(ownerId, managerId, cashierId).forEach { userId ->
            scopeDb.execSQL(
                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",
                arrayOf<Any?>(userId, branchId, now),
            )
            scopeDb.execSQL(
                "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",
                arrayOf<Any?>(userId, branchId, now),
            )
            scopeDb.execSQL(
                "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",
                arrayOf<Any?>(userId, locationId, now),
            )
        }
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "برنج شمارش",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).receive(
            itemId = itemId,
            quantityMicros = 10_000_000,
            valueRial = 2_000_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 1,
            movementEpochDay = BUSINESS_DAY,
            context = InventoryCommandContext.local(
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = 1,
                suffix = "count-test-opening:$itemId",
                actorId = ownerId,
                reasonCode = InventoryReasonCode.OPENING_BALANCE,
                reason = "مانده آغاز آزمون شمارش",
                correlationId = "count-test:opening:$itemId",
                locationId = locationId,
            ),
        )
        fixture = Fixture(ownerId, managerId, cashierId, locationId, itemId)
        service = LocalInventoryCountService(database, authorizer, clock = { ++now })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blindRecountApprovalAndPostAreAtomicAndReplaySafe() = runBlocking {
        val sessionId = createAndOpen()
        val line = service.lines(sessionId, canReviewVariance = false).single()
        assertNull(line.systemQuantityMicros)
        assertNull(line.varianceQuantityMicros)

        service.record(record(sessionId, line.lineId, 8_000_000))
        service.submit(action(sessionId, fixture.ownerId, "ارسال شمارش اول"))
        assertEquals(InventoryCountStatus.RECOUNT_REQUIRED, service.session(sessionId).status)

        service.record(record(sessionId, line.lineId, 8_000_000))
        service.submit(action(sessionId, fixture.ownerId, "ارسال بازشماری"))
        assertEquals(InventoryCountStatus.PENDING_APPROVAL, service.session(sessionId).status)

        security.switchUser(fixture.managerId, "654321")
        val review = service.lines(sessionId, canReviewVariance = true).single()
        assertEquals(10_000_000L, review.systemQuantityMicros)
        assertEquals(-2_000_000L, review.varianceQuantityMicros)
        service.approve(action(sessionId, fixture.managerId, "مغایرت بررسی شد"))

        val postCommandId = GlobalId.new().value
        val command = PostInventoryCountCommand(sessionId, fixture.managerId, postCommandId)
        val posted = service.post(command)
        val replay = service.post(command)

        assertEquals(InventoryCountStatus.POSTED, posted.status)
        assertEquals(posted.id, replay.id)
        assertEquals(8_000_000L, database.inventoryBalanceDao().byKey(fixture.itemId, fixture.locationId)?.onHandMicros)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='INVENTORY_COUNT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM inventory_counts WHERE itemId=${fixture.itemId}"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='INVENTORY_COUNT_SESSION' AND action='POST'"))
    }

    @Test
    fun lotControlledCountUsesNamedLotsAndPostsBalancedVarianceJournal() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "سس لات‌محور شمارش",
                category = "مواد اولیه",
                unit = "عدد",
                trackLot = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val engine = LocalInventoryCommandEngine(database, clock = { ++now }, authorizer = authorizer)
        listOf("LOT-A" to 4_000_000L, "LOT-B" to 6_000_000L).forEachIndexed { index, (lotNo, quantity) ->
            engine.receive(
                itemId = itemId,
                quantityMicros = quantity,
                valueRial = quantity / 10L,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = 100L + index,
                movementEpochDay = BUSINESS_DAY,
                context = InventoryCommandContext.local(
                    referenceType = InventoryReferenceType.MIGRATION,
                    referenceId = 100L + index,
                    suffix = "lot-count-opening:$index",
                    actorId = fixture.ownerId,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "مانده آغاز لات شمارش",
                    correlationId = "count-test:lot-opening:$index",
                    locationId = fixture.locationId,
                ),
                lot = InventoryReceiptLot(lotNumber = lotNo),
                enforceLotPolicy = true,
            )
        }

        val sessionId = service.create(
            CreateInventoryCountSessionCommand(
                locationId = fixture.locationId,
                scope = InventoryCountScope.ITEM_SELECTION,
                itemIds = setOf(itemId),
                blindCount = true,
                assignedToActorId = fixture.ownerId,
                businessEpochDay = BUSINESS_DAY,
                commandId = GlobalId.new().value,
                correlationId = "count-test:lot-session:$now",
            ),
        )
        service.open(action(sessionId, fixture.ownerId, "شروع شمارش لات‌محور"))
        val firstPass = service.lines(sessionId, false)
        assertEquals(2, firstPass.size)
        assertTrue(firstPass.all { it.lotId != null })
        val sorted = firstPass.sortedBy { it.lotId }
        service.record(record(sessionId, sorted[0].lineId, 5_000_000L))
        service.record(record(sessionId, sorted[1].lineId, 7_000_000L))
        service.submit(action(sessionId, fixture.ownerId, "ارسال شمارش لات‌محور"))
        if (service.session(sessionId).status == InventoryCountStatus.RECOUNT_REQUIRED) {
            val recount = service.lines(sessionId, false).sortedBy { it.lotId }
            service.record(record(sessionId, recount[0].lineId, 5_000_000L))
            service.record(record(sessionId, recount[1].lineId, 7_000_000L))
            service.submit(action(sessionId, fixture.ownerId, "ارسال بازشماری لات‌محور"))
        }

        security.switchUser(fixture.managerId, "654321")
        service.approve(action(sessionId, fixture.managerId, "تأیید مغایرت لات‌محور"))
        service.post(PostInventoryCountCommand(sessionId, fixture.managerId, GlobalId.new().value))

        assertEquals(12_000_000L, database.inventoryBalanceDao().byKey(itemId, fixture.locationId)?.onHandMicros)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_lots WHERE itemId=$itemId AND quantityMicros > 0 AND (lotCode IS NULL OR trim(lotCode)='')"))
        assertEquals(12_000_000L, scalar("SELECT COALESCE(SUM(quantityMicros),0) FROM inventory_lots WHERE itemId=$itemId AND locationId=${fixture.locationId}"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='INVENTORY_COUNT_SESSION' AND sourceId=$sessionId"))
        assertEquals(0L, scalar("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM journal_lines WHERE entryId=(SELECT id FROM journal_entries WHERE sourceType='INVENTORY_COUNT_SESSION' AND sourceId=$sessionId LIMIT 1)"))
    }

    @Test
    fun unapprovedCountCannotPostAndDoesNotMutateLedger() = runBlocking {
        val sessionId = createAndOpen()
        val line = service.lines(sessionId, false).single()
        service.record(record(sessionId, line.lineId, 10_000_000))
        service.submit(action(sessionId, fixture.ownerId, "ارسال شمارش بدون مغایرت"))
        val before = scalar("SELECT COUNT(*) FROM stock_movements")

        try {
            service.post(PostInventoryCountCommand(sessionId, fixture.ownerId, GlobalId.new().value))
            fail("ثبت جلسه تأییدنشده باید رد شود")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.CountNotApproved)
        }

        assertEquals(before, scalar("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_counts"))
    }

    @Test
    fun unauthorizedActorCannotApproveOrPostCount() = runBlocking {
        val sessionId = createAndOpen()
        val line = service.lines(sessionId, false).single()
        service.record(record(sessionId, line.lineId, 10_000_000))
        service.submit(action(sessionId, fixture.ownerId, "ارسال شمارش برای تأیید"))
        assertEquals(InventoryCountStatus.PENDING_APPROVAL, service.session(sessionId).status)
        val movementCount = scalar("SELECT COUNT(*) FROM stock_movements")

        security.switchUser(fixture.cashierId, "456789")
        try {
            service.approve(action(sessionId, fixture.cashierId, "تلاش تأیید بدون مجوز"))
            fail("کاربر بدون مجوز نباید شمارش را تأیید کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
        assertEquals(
            InventoryCountStatus.PENDING_APPROVAL.storedValue,
            database.inventoryCountDao().session(sessionId)?.status,
        )

        security.switchUser(fixture.managerId, "654321")
        service.approve(action(sessionId, fixture.managerId, "شمارش بررسی و تأیید شد"))
        security.switchUser(fixture.cashierId, "456789")
        try {
            service.post(PostInventoryCountCommand(sessionId, fixture.cashierId, GlobalId.new().value))
            fail("کاربر بدون مجوز نباید شمارش را ثبت نهایی کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }

        assertEquals(
            InventoryCountStatus.APPROVED.storedValue,
            database.inventoryCountDao().session(sessionId)?.status,
        )
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_counts"))
    }

    @Test
    fun outboxFailureRollsBackCountMovementProjectionAuditAndStatus() = runBlocking {
        val sessionId = approvedSession(countedQuantityMicros = 9_000_000)
        val failing = LocalInventoryCountService(
            database,
            authorizer,
            clock = { ++now },
            syncRecorder = object : SyncRecorder(database, "count-failing-device") {
                override suspend fun record(
                    entityType: String,
                    entityId: Long,
                    changeType: String,
                    occurredAt: Long,
                    payloadFields: Map<String, String>,
                    recordAudit: Boolean,
                ) {
                    error("forced outbox failure")
                }
            },
        )
        val movementCount = scalar("SELECT COUNT(*) FROM stock_movements")
        val postAuditCount = scalar("SELECT COUNT(*) FROM audit_logs WHERE action='POST'")

        try {
            failing.post(PostInventoryCountCommand(sessionId, fixture.managerId, GlobalId.new().value))
            fail("خرابی outbox باید کل تراکنش را rollback کند")
        } catch (_: IllegalStateException) {
            Unit
        }

        assertEquals(InventoryCountStatus.APPROVED, service.session(sessionId).status)
        assertEquals(10_000_000L, database.inventoryBalanceDao().byKey(fixture.itemId, fixture.locationId)?.onHandMicros)
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_counts"))
        assertEquals(postAuditCount, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='POST'"))
    }

    private suspend fun createAndOpen(): Long {
        val sessionId = service.create(
            CreateInventoryCountSessionCommand(
                locationId = fixture.locationId,
                scope = InventoryCountScope.ITEM_SELECTION,
                itemIds = setOf(fixture.itemId),
                blindCount = true,
                assignedToActorId = fixture.ownerId,
                businessEpochDay = BUSINESS_DAY,
                commandId = GlobalId.new().value,
                correlationId = "count-test:session:${now}",
            ),
        )
        service.open(action(sessionId, fixture.ownerId, "شروع شمارش فیزیکی"))
        return sessionId
    }

    private suspend fun approvedSession(countedQuantityMicros: Long): Long {
        val sessionId = createAndOpen()
        val line = service.lines(sessionId, false).single()
        service.record(record(sessionId, line.lineId, countedQuantityMicros))
        service.submit(action(sessionId, fixture.ownerId, "ارسال شمارش اول"))
        if (service.session(sessionId).status == InventoryCountStatus.RECOUNT_REQUIRED) {
            service.record(record(sessionId, line.lineId, countedQuantityMicros))
            service.submit(action(sessionId, fixture.ownerId, "ارسال بازشماری"))
        }
        security.switchUser(fixture.managerId, "654321")
        service.approve(action(sessionId, fixture.managerId, "مغایرت تأیید شد"))
        return sessionId
    }

    private fun action(sessionId: Long, actorId: Long, reason: String) =
        InventoryCountActionCommand(sessionId, actorId, reason)

    private fun record(sessionId: Long, lineId: Long, quantityMicros: Long) =
        RecordInventoryCountCommand(sessionId, lineId, quantityMicros, actorId = fixture.ownerId, reason = "شمارش کنترل‌شده")

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private data class Fixture(
        val ownerId: Long,
        val managerId: Long,
        val cashierId: Long,
        val locationId: Long,
        val itemId: Long,
    )

    private companion object {
        const val BUSINESS_DAY = 21_000L
    }
}
