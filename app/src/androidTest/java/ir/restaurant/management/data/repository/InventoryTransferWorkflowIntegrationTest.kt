package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.CreateInventoryTransferLine
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryTransferStatus
import ir.restaurant.management.domain.inventory.ReceiveInventoryTransferCommand
import ir.restaurant.management.domain.inventory.TransferActionCommand
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
class InventoryTransferWorkflowIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private lateinit var service: LocalInventoryTransferService
    private lateinit var fixture: Fixture
    private var now = 1_920_000_000_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
        val ownerId = security.save(
            null,
            UserDraft("transfer-owner", "مالک انتقال", "123456", UserRole.OWNER, "87654321"),
        )
        val cashierId = security.save(
            null,
            UserDraft("transfer-cashier", "صندوقدار انتقال", "654321", UserRole.CASHIER),
        )
        val branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)
        val sourceLocationId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "TRANSFER-SOURCE-TEST",
                name = "انبار مبدأ آزمون انتقال",
                branchName = "شعبه آزمون انتقال",
                branchId = branchId,
                kind = "WAREHOUSE",
                createdAtEpochMillis = now,
            ),
        )
        val destinationLocationId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "KITCHEN-TEST",
                name = "آشپزخانه آزمون",
                branchName = "شعبه آزمون انتقال",
                branchId = branchId,
                kind = "KITCHEN",
                createdAtEpochMillis = now,
            ),
        )
        val scopeDb = database.openHelper.writableDatabase
        listOf(ownerId, cashierId).forEach { userId ->
            scopeDb.execSQL(
                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",
                arrayOf<Any?>(userId, branchId, now),
            )
            scopeDb.execSQL(
                "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",
                arrayOf<Any?>(userId, branchId, now),
            )
            listOf(sourceLocationId, destinationLocationId).forEach { locationId ->
                scopeDb.execSQL(
                    "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",
                    arrayOf<Any?>(userId, locationId, now),
                )
            }
        }
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "پنیر انتقال",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                trackLot = true,
                trackExpiry = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).receive(
            itemId = itemId,
            quantityMicros = 5_000_000,
            valueRial = 1_000_000,
            movementType = InventoryMovementType.OPENING_BALANCE,
            referenceType = InventoryReferenceType.MIGRATION,
            referenceId = 81,
            movementEpochDay = BUSINESS_DAY,
            context = InventoryCommandContext.local(
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = 81,
                suffix = "transfer-fixture:$itemId",
                actorId = ownerId,
                reasonCode = InventoryReasonCode.OPENING_BALANCE,
                reason = "آماده‌سازی آزمون انتقال",
                correlationId = "transfer-test:fixture:$itemId",
                locationId = sourceLocationId,
            ),
            lot = InventoryReceiptLot(lotNumber = "LOT-TRANSFER-81", expiryEpochDay = BUSINESS_DAY + 90),
            enforceLotPolicy = true,
        )
        val sourceLotId = requireNotNull(
            database.inventoryLotDao().byNaturalKey(itemId, sourceLocationId, "LOT-TRANSFER-81"),
        ).id
        fixture = Fixture(ownerId, cashierId, sourceLocationId, destinationLocationId, itemId, sourceLotId)
        service = LocalInventoryTransferService(database, authorizer, clock = { ++now })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun issueAndReceiptTrackInTransitPreserveValueAndCreateNoJournal() = runBlocking {
        LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).receive(
            itemId = fixture.itemId,
            quantityMicros = 5_000_000,
            valueRial = 3_000_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 82,
            movementEpochDay = BUSINESS_DAY,
            context = InventoryCommandContext.local(
                referenceType = InventoryReferenceType.PURCHASE,
                referenceId = 82,
                suffix = "transfer-second-lot:${fixture.itemId}",
                actorId = fixture.ownerId,
                reasonCode = InventoryReasonCode.PURCHASE_RECEIPT,
                reason = "دریافت لات دوم برای اثبات میانگین موزون",
                correlationId = "transfer-test:second-lot:${fixture.itemId}",
                locationId = fixture.sourceLocationId,
            ),
            lot = InventoryReceiptLot(lotNumber = "LOT-TRANSFER-82", expiryEpochDay = BUSINESS_DAY + 120),
            enforceLotPolicy = true,
        )
        val transfer = createAndApprove(2_000_000)
        val issueCommand = action(transfer.id, "خروج کنترل‌شده")
        val issued = service.issue(issueCommand)
        val replayedIssue = service.issue(issueCommand)

        assertEquals(InventoryTransferStatus.IN_TRANSIT, issued.status)
        assertEquals(issued.id, replayedIssue.id)
        assertEquals(8_000_000L, balance(fixture.sourceLocationId).first)
        assertEquals(0L, balance(fixture.destinationLocationId).first)
        assertEquals(2_000_000L, balance(fixture.destinationLocationId).second)
        assertEquals(3_000_000L, database.inventoryLotDao().byId(fixture.sourceLotId)?.quantityMicros)
        val issuedLine = issued.lines.single()
        assertEquals(400_000L, issuedLine.unitCostRial)
        assertEquals(800_000L, issuedLine.valueRial)
        assertEquals(3_200_000L, database.inventoryBalanceDao().byKey(fixture.itemId, fixture.sourceLocationId)?.inventoryValueRial)
        assertEquals(10_000_000L, database.inventoryDao().byId(fixture.itemId)?.stockMicros)
        assertEquals(4_000_000L, database.inventoryDao().byId(fixture.itemId)?.inventoryValueRial)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='TRANSFER_OUT' AND referenceId=${transfer.id}"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='TRANSFER_IN' AND referenceId=${transfer.id}"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='STOCK_TRANSFER' AND sourceId=${transfer.id}"))

        val line = issued.lines.single()
        val receiveCommand = ReceiveInventoryTransferCommand(
            transferId = transfer.id,
            actorId = fixture.ownerId,
            businessEpochDay = BUSINESS_DAY + 1,
            receivedQuantityByLineId = mapOf(line.id to requireNotNull(line.issuedQuantityMicros)),
            reason = "تحویل کامل به آشپزخانه",
            commandId = GlobalId.new().value,
        )
        val completed = service.receive(receiveCommand)
        val replayedReceipt = service.receive(receiveCommand)

        assertEquals(InventoryTransferStatus.COMPLETED, completed.status)
        assertEquals(completed.id, replayedReceipt.id)
        assertEquals(2_000_000L, balance(fixture.destinationLocationId).first)
        assertEquals(0L, balance(fixture.destinationLocationId).second)
        assertEquals(800_000L, database.inventoryBalanceDao().byKey(fixture.itemId, fixture.destinationLocationId)?.inventoryValueRial)
        assertEquals(10_000_000L, database.inventoryDao().byId(fixture.itemId)?.stockMicros)
        assertEquals(4_000_000L, database.inventoryDao().byId(fixture.itemId)?.inventoryValueRial)
        assertEquals(
            2_000_000L,
            database.inventoryLotDao().byNaturalKey(
                fixture.itemId,
                fixture.destinationLocationId,
                "LOT-TRANSFER-81",
            )?.quantityMicros,
        )
        assertEquals(0L, scalar("SELECT SUM(quantityDeltaMicros) FROM stock_movements WHERE referenceType='STOCK_TRANSFER' AND referenceId=${transfer.id}"))
        assertEquals(0L, scalar("SELECT SUM(valueDeltaRial) FROM stock_movements WHERE referenceType='STOCK_TRANSFER' AND referenceId=${transfer.id}"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM stock_movements WHERE referenceType='STOCK_TRANSFER' AND referenceId=${transfer.id}"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='STOCK_TRANSFER' AND sourceId=${transfer.id}"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='INVENTORY_TRANSFER' AND action='ISSUE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='INVENTORY_TRANSFER' AND action='RECEIVE'"))
    }

    @Test
    fun quantityVarianceAndUnauthorizedReceiptCannotMutateDestination() = runBlocking {
        val transfer = createAndApprove(1_000_000)
        val issued = service.issue(action(transfer.id, "ارسال یک واحد"))
        val line = issued.lines.single()
        val movementCount = scalar("SELECT COUNT(*) FROM stock_movements")

        try {
            service.receive(
                ReceiveInventoryTransferCommand(
                    transfer.id,
                    fixture.ownerId,
                    BUSINESS_DAY + 1,
                    mapOf(line.id to 900_000),
                    "مغایرت دریافت آزمون",
                ),
            )
            fail("مغایرت دریافت بدون تأیید باید رد شود")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.TransferVarianceRequiresApproval)
        }
        assertEquals(0L, balance(fixture.destinationLocationId).first)
        assertEquals(1_000_000L, balance(fixture.destinationLocationId).second)
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM stock_movements"))

        security.switchUser(fixture.cashierId, "654321")
        try {
            service.receive(
                ReceiveInventoryTransferCommand(
                    transfer.id,
                    fixture.cashierId,
                    BUSINESS_DAY + 1,
                    mapOf(line.id to 1_000_000),
                    "تلاش دریافت بدون مجوز",
                ),
            )
            fail("کاربر بدون مجوز نباید انتقال را دریافت کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
        assertEquals(InventoryTransferStatus.IN_TRANSIT.storedValue, database.inventoryTransferDao().transfer(transfer.id)?.status)
        assertEquals(0L, balance(fixture.destinationLocationId).first)
    }

    @Test
    fun outboxFailureRollsBackIssueMovementLotProjectionAuditAndStatus() = runBlocking {
        val transfer = createAndApprove(1_500_000)
        val failing = LocalInventoryTransferService(
            database,
            authorizer,
            clock = { ++now },
            syncRecorder = object : SyncRecorder(database, "transfer-failing-device") {
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
        val auditCount = scalar("SELECT COUNT(*) FROM audit_logs")

        try {
            failing.issue(action(transfer.id, "خروجی که باید rollback شود"))
            fail("خرابی outbox باید کل خروج انتقال را rollback کند")
        } catch (_: IllegalStateException) {
            Unit
        }

        assertEquals(InventoryTransferStatus.APPROVED.storedValue, database.inventoryTransferDao().transfer(transfer.id)?.status)
        assertEquals(5_000_000L, balance(fixture.sourceLocationId).first)
        assertEquals(0L, balance(fixture.destinationLocationId).second)
        assertEquals(5_000_000L, database.inventoryLotDao().byId(fixture.sourceLotId)?.quantityMicros)
        assertEquals(movementCount, scalar("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(auditCount, scalar("SELECT COUNT(*) FROM audit_logs"))
        assertTrue(database.inventoryTransferDao().lines(transfer.id).single().issuedQuantityMicros == null)
    }

    @Test
    fun immediateCompatibilityFlowIsAtomicAndReplaySafe() = runBlocking {
        val command = CreateInventoryTransferCommand(
            sourceLocationId = fixture.sourceLocationId,
            destinationLocationId = fixture.destinationLocationId,
            businessEpochDay = BUSINESS_DAY,
            lines = listOf(CreateInventoryTransferLine(fixture.itemId, fixture.sourceLotId, 750_000)),
            notes = "انتقال فوری سازگار",
            actorId = fixture.ownerId,
            commandId = GlobalId.new().value,
            correlationId = "inventory_transfer:compatibility:${GlobalId.new().value}",
        )

        val completed = service.createAndComplete(command)
        val replay = service.createAndComplete(command)

        assertEquals(InventoryTransferStatus.COMPLETED, completed.status)
        assertEquals(completed.id, replay.id)
        assertEquals(2L, scalar("SELECT COUNT(*) FROM stock_movements WHERE referenceType='STOCK_TRANSFER' AND referenceId=${completed.id}"))
        assertEquals(4_250_000L, balance(fixture.sourceLocationId).first)
        assertEquals(750_000L, balance(fixture.destinationLocationId).first)
        assertEquals(0L, balance(fixture.destinationLocationId).second)
    }

    private suspend fun createAndApprove(quantityMicros: Long) = service.create(
        CreateInventoryTransferCommand(
            sourceLocationId = fixture.sourceLocationId,
            destinationLocationId = fixture.destinationLocationId,
            businessEpochDay = BUSINESS_DAY,
            lines = listOf(
                CreateInventoryTransferLine(fixture.itemId, fixture.sourceLotId, quantityMicros),
            ),
            notes = "انتقال آزمون بین انبار و آشپزخانه",
            actorId = fixture.ownerId,
            commandId = GlobalId.new().value,
            correlationId = "inventory_transfer:test:${GlobalId.new().value}",
        ),
    ).let { created ->
        service.approve(action(created.id, "تأیید مقدار انتقال"))
    }

    private fun action(transferId: Long, reason: String) = TransferActionCommand(
        transferId = transferId,
        actorId = fixture.ownerId,
        businessEpochDay = BUSINESS_DAY,
        reason = reason,
        commandId = GlobalId.new().value,
    )

    private suspend fun balance(locationId: Long): Pair<Long, Long> {
        val balance = database.inventoryBalanceDao().byKey(fixture.itemId, locationId) ?: return 0L to 0L
        return balance.onHandMicros to balance.inTransitMicros
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) 0L else cursor.getLong(0)
    }

    private data class Fixture(
        val ownerId: Long,
        val cashierId: Long,
        val sourceLocationId: Long,
        val destinationLocationId: Long,
        val itemId: Long,
        val sourceLotId: Long,
    )

    private companion object {
        const val BUSINESS_DAY = 21_100L
    }
}
