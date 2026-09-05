package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AccountingPeriodLockEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryLotEntity
import ir.restaurant.management.data.db.InventoryPeriodClosureEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.operations.WasteDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryLedgerIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var engine: LocalInventoryCommandEngine
    private var defaultLocationId: Long = 0L

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        engine = LocalInventoryCommandEngine(database, clock = { NOW }, authorizer = FixedInventoryTestAuthorizer())
        defaultLocationId = runBlocking { requireNotNull(database.managementControlDao().defaultLocationId()) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun receiveIsIdempotentAndLedgerIsAppendOnly() = runBlocking {
        val itemId = insertItem("برنج")
        val context = command("purchase:77:item:$itemId", 77)

        val first = engine.receive(
            itemId = itemId,
            quantityMicros = 2_000_000,
            valueRial = 400_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 77,
            movementEpochDay = 100,
            context = context,
        )
        val replay = engine.receive(
            itemId = itemId,
            quantityMicros = 2_000_000,
            valueRial = 400_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 77,
            movementEpochDay = 100,
            context = context,
        )

        assertFalse(first.idempotentReplay)
        assertTrue(replay.idempotentReplay)
        assertEquals(first.movementId, replay.movementId)
        assertEquals(2_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(400_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        val defaultLocationId = requireNotNull(database.managementControlDao().defaultLocationId())
        assertEquals(2_000_000L, database.inventoryBalanceDao().byKey(itemId, defaultLocationId)?.onHandMicros)
        assertEquals(400_000L, database.inventoryBalanceDao().byKey(itemId, defaultLocationId)?.inventoryValueRial)
        assertEquals(1, database.stockMovementDao().observeForItem(itemId).first().size)

        try {
            engine.receive(
                itemId = itemId,
                quantityMicros = 2_000_000,
                valueRial = 500_000,
                movementType = InventoryMovementType.PURCHASE,
                referenceType = InventoryReferenceType.PURCHASE,
                referenceId = 77,
                movementEpochDay = 100,
                context = context,
            )
            fail("payload متفاوت نباید با همان idempotency key پذیرفته شود")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.IdempotencyConflict)
        }

        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE stock_movements SET notes='tampered' WHERE id=${first.movementId}",
            )
            fail("گردش موجودی ثبت‌شده نباید ویرایش شود")
        } catch (_: Exception) {
            Unit
        }
    }

    @Test
    fun closedPeriodFailureRollsBackProjectionAndLedger() = runBlocking {
        val itemId = insertItem("روغن")
        database.inventoryControlDao().insertClosure(
            InventoryPeriodClosureEntity(
                fromEpochDay = 200,
                toEpochDay = 210,
                openingValueRial = 0,
                netPurchaseValueRial = 0,
                recordedOutflowValueRial = 0,
                expectedClosingValueRial = 0,
                countedClosingValueRial = 0,
                varianceValueRial = 0,
                itemCount = 1,
                closedBy = "tester",
                note = "integration test",
                createdAtEpochMillis = NOW,
            ),
        )

        try {
            engine.receive(
                itemId = itemId,
                quantityMicros = 1_000_000,
                valueRial = 150_000,
                movementType = InventoryMovementType.PURCHASE,
                referenceType = InventoryReferenceType.PURCHASE,
                referenceId = 88,
                movementEpochDay = 205,
                context = command("purchase:88:item:$itemId", 88),
            )
            fail("دوره بسته باید کل فرمان را rollback کند")
        } catch (_: Exception) {
            Unit
        }

        val item = requireNotNull(database.inventoryDao().byId(itemId))
        assertEquals(0L, item.stockMicros)
        assertEquals(0L, item.inventoryValueRial)
        assertTrue(database.inventoryBalanceDao().byKey(itemId, requireNotNull(database.managementControlDao().defaultLocationId())) == null)
        assertTrue(database.stockMovementDao().observeForItem(itemId).first().isEmpty())
    }

    @Test
    fun stockAtAnotherLocationCannotMaskLocationShortage() = runBlocking {
        val itemId = insertItem("روغن مکان‌محور")
        engine.receive(
            itemId = itemId,
            quantityMicros = 2_000_000,
            valueRial = 400_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 501,
            movementEpochDay = 100,
            context = command("purchase:501:item:$itemId", 501),
        )
        val kitchenId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "KITCHEN-1",
                name = "آشپزخانه تست",
                kind = "KITCHEN",
                createdAtEpochMillis = NOW,
            ),
        )

        try {
            engine.issue(
                itemId = itemId,
                quantityMicros = 100_000,
                valueRial = 20_000,
                movementType = InventoryMovementType.WASTE,
                referenceType = InventoryReferenceType.WASTE,
                referenceId = 502,
                movementEpochDay = 101,
                context = InventoryCommandContext(
                    idempotencyKey = "waste:502:item:$itemId",
                    correlationId = "integration:inventory:502",
                    actorId = 42,
                    deviceId = "instrumentation",
                    locationId = kitchenId,
                    reasonCode = InventoryReasonCode.WASTE,
                    reason = "آزمون موجودی مکان‌محور",
                ),
                lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.NONE,
            )
            fail("موجودی محل دیگر نباید کمبود آشپزخانه را پنهان کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.InsufficientStock)
        }

        assertEquals(2_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(2_000_000L, database.inventoryBalanceDao().byKey(itemId, requireNotNull(database.managementControlDao().defaultLocationId()))?.onHandMicros)
        assertTrue(database.inventoryBalanceDao().byKey(itemId, kitchenId) == null)
        assertEquals(1, database.stockMovementDao().observeForItem(itemId).first().size)
    }

    @Test
    fun movementAndAggregateRollBackWhenLocationProjectionUpdateFails() = runBlocking {
        val itemId = insertItem("پروجکشن اتمیک")
        engine.receive(
            itemId = itemId,
            quantityMicros = 1_000_000,
            valueRial = 100_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 601,
            movementEpochDay = 100,
            context = command("purchase:601:item:$itemId", 601),
        )
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER force_inventory_balance_failure
            BEFORE UPDATE ON inventory_balances
            BEGIN SELECT RAISE(ABORT, 'forced projection failure'); END""",
        )
        try {
            engine.issue(
                itemId = itemId,
                quantityMicros = 100_000,
                valueRial = 10_000,
                movementType = InventoryMovementType.WASTE,
                referenceType = InventoryReferenceType.WASTE,
                referenceId = 602,
                movementEpochDay = 101,
                context = InventoryCommandContext(
                    idempotencyKey = "waste:602:item:$itemId",
                    correlationId = "integration:inventory:602",
                    actorId = 42,
                    deviceId = "instrumentation",
                    locationId = defaultLocationId,
                    reasonCode = InventoryReasonCode.WASTE,
                    reason = "آزمون rollback پروجکشن",
                ),
                lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.NONE,
            )
            fail("خرابی پروجکشن باید کل فرمان را rollback کند")
        } catch (_: Exception) {
            Unit
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS force_inventory_balance_failure")
        }

        val defaultLocationId = requireNotNull(database.managementControlDao().defaultLocationId())
        assertEquals(1_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(100_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        assertEquals(1_000_000L, database.inventoryBalanceDao().byKey(itemId, defaultLocationId)?.onHandMicros)
        assertEquals(100_000L, database.inventoryBalanceDao().byKey(itemId, defaultLocationId)?.inventoryValueRial)
        assertEquals(1, database.stockMovementDao().observeForItem(itemId).first().size)
    }

    @Test
    fun normalFefoConsumptionSkipsExpiredLotAndUsesEligibleLot() = runBlocking {
        val itemId = insertItem("کالای FEFO")
        val item = requireNotNull(database.inventoryDao().byId(itemId))
        assertEquals(1, database.inventoryDao().update(item.copy(trackExpiry = true)))
        engine.receive(
            itemId = itemId,
            quantityMicros = 3_000_000,
            valueRial = 300_000,
            movementType = InventoryMovementType.PURCHASE,
            referenceType = InventoryReferenceType.PURCHASE,
            referenceId = 701,
            movementEpochDay = 90,
            context = command("purchase:701:item:$itemId", 701),
        )
        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())
        val expiredId = database.inventoryLotDao().insert(
            InventoryLotEntity(
                itemId = itemId,
                locationId = locationId,
                lotCode = "EXPIRED-LOT",
                receivedEpochDay = 80,
                expiryEpochDay = 99,
                quantityMicros = 1_000_000,
                unitCostRial = 100_000,
                barcode = null,
                createdByActorId = 42,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        val eligibleId = database.inventoryLotDao().insert(
            InventoryLotEntity(
                itemId = itemId,
                locationId = locationId,
                lotCode = "ELIGIBLE-LOT",
                receivedEpochDay = 81,
                expiryEpochDay = 110,
                quantityMicros = 2_000_000,
                unitCostRial = 100_000,
                barcode = null,
                createdByActorId = 42,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )

        engine.issue(
            itemId = itemId,
            quantityMicros = 2_000_000,
            valueRial = 200_000,
            movementType = InventoryMovementType.DAILY_SALES_CONSUMPTION,
            referenceType = InventoryReferenceType.DAILY_SALES,
            referenceId = 702,
            movementEpochDay = 100,
            context = InventoryCommandContext(
                idempotencyKey = "daily-sales:702:item:$itemId",
                correlationId = "integration:inventory:702",
                actorId = 42,
                deviceId = "instrumentation",
                locationId = locationId,
                reasonCode = InventoryReasonCode.SALES_CONSUMPTION,
                reason = "آزمون FEFO",
            ),
            lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALL,
        )

        assertEquals(1_000_000L, database.inventoryLotDao().byId(expiredId)?.quantityMicros)
        assertEquals(0L, database.inventoryLotDao().byId(eligibleId)?.quantityMicros)
        assertEquals("DEPLETED", database.inventoryLotDao().byId(eligibleId)?.status)
    }

    @Test
    fun wasteDocumentRetryReturnsOriginalResultWithoutDuplicateEffects() = runBlocking {
        val gate = SensitiveActionGate(clockMillis = { NOW })
        signInOwner(gate)
        val repository = operationsRepository(gate)
        val itemId = insertItem("خامه", stockMicros = 2_000_000, inventoryValueRial = 400_000)
        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())
        database.inventoryLotDao().insert(
            InventoryLotEntity(
                itemId = itemId,
                locationId = locationId,
                lotCode = "LOT-WASTE-1",
                receivedEpochDay = 90,
                expiryEpochDay = 120,
                quantityMicros = 2_000_000,
                unitCostRial = 200_000,
                barcode = null,
                createdByActorId = 42,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        val draft = WasteDraft(
            itemId = itemId,
            quantityMicros = 500_000,
            wasteEpochDay = 100,
            reason = "فساد مواد",
            notes = "کنترل دما",
            commandId = "123e4567-e89b-42d3-a456-426614174000",
        )

        val firstId = repository.postWaste(draft)
        val replayId = repository.postWaste(draft)

        assertEquals(firstId, replayId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM inventory_waste_documents"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='WASTE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='WASTE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='INVENTORY_WASTE' AND action='POST'"))
        assertEquals(1_500_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(300_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
    }

    @Test
    fun inventoryCountRetryIsIdempotentAfterFreshSensitiveAuthorization() = runBlocking {
        val gate = SensitiveActionGate(clockMillis = { NOW })
        val security = signInOwner(gate)
        val repository = operationsRepository(gate)
        val itemId = insertItem("برنج شمارش", stockMicros = 1_000_000, inventoryValueRial = 100_000)
        val draft = InventoryCountDraft(
            itemId = itemId,
            countedQuantityMicros = 800_000,
            countedValueRial = 80_000,
            countEpochDay = 100,
            reason = "شمارش فیزیکی پایان شیفت",
            commandId = "123e4567-e89b-42d3-a456-426614174001",
            locationId = defaultLocationId,
        )

        val countContext = ir.restaurant.management.domain.operations.SensitiveActionContext.resource(
            "INVENTORY_COUNT", "${defaultLocationId}:${itemId}:${draft.countEpochDay}", commandFingerprint = draft.commandId,
        )
        security.authorizeSensitiveAction(SensitiveAction.ADJUST_INVENTORY, "123456", countContext)
        val firstId = repository.postInventoryCount(draft)
        security.authorizeSensitiveAction(SensitiveAction.ADJUST_INVENTORY, "123456", countContext)
        val replayId = repository.postInventoryCount(draft)

        assertEquals(firstId, replayId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM inventory_counts"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='INVENTORY_COUNT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='INVENTORY_COUNT'"))
        assertEquals(800_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(80_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
    }

    @Test
    fun unauthorizedUserCannotPostCompatibilityInventoryAdjustment() = runBlocking {
        val gate = SensitiveActionGate(clockMillis = { NOW })
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(
            db = database,
            clock = { NOW },
            authorizer = authorizer,
            sensitiveActionGate = gate,
        )
        security.save(null, UserDraft("count-owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        val cashierId = security.save(
            null,
            UserDraft("count-cashier", "صندوقدار", "654321", UserRole.CASHIER),
        )
        security.switchUser(cashierId, "654321")
        val repository = LocalOperationsRepository(
            database = database,
            clock = { NOW },
            authorizer = authorizer,
            sensitiveActionGate = gate,
        )
        val itemId = insertItem("کالای اصلاح غیرمجاز", stockMicros = 1_000_000, inventoryValueRial = 100_000)

        try {
            repository.postInventoryCount(
                InventoryCountDraft(
                    itemId = itemId,
                    countedQuantityMicros = 500_000,
                    countedValueRial = 50_000,
                    countEpochDay = 100,
                    reason = "تلاش اصلاح بدون مجوز",
                    commandId = "123e4567-e89b-42d3-a456-426614174099",
                ),
            )
            fail("کاربر بدون مجوز نباید موجودی را اصلاح کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_counts"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='INVENTORY_COUNT'"))
        assertEquals(1_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
    }

    @Test
    fun wasteRollsBackDocumentLotProjectionAndMovementWhenAccountingPeriodIsClosed() = runBlocking {
        val gate = SensitiveActionGate(clockMillis = { NOW })
        signInOwner(gate)
        val repository = operationsRepository(gate)
        val itemId = insertItem("شیر", stockMicros = 2_000_000, inventoryValueRial = 400_000)
        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())
        val lotId = database.inventoryLotDao().insert(
            InventoryLotEntity(
                itemId = itemId,
                locationId = locationId,
                lotCode = "LOT-ROLLBACK-1",
                receivedEpochDay = 90,
                expiryEpochDay = 120,
                quantityMicros = 2_000_000,
                unitCostRial = 200_000,
                barcode = null,
                createdByActorId = 42,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        database.managementControlDao().insertAccountingPeriodLock(
            AccountingPeriodLockEntity(
                fromEpochDay = 100,
                toEpochDay = 100,
                reason = "آزمون rollback",
                closedBy = "owner",
                closedAtEpochMillis = NOW,
            ),
        )

        try {
            repository.postWaste(
                WasteDraft(
                    itemId = itemId,
                    quantityMicros = 500_000,
                    wasteEpochDay = 100,
                    reason = "فساد مواد",
                    commandId = "123e4567-e89b-42d3-a456-426614174002",
                ),
            )
            fail("بسته‌بودن دوره مالی باید تمام عملیات ضایعات را rollback کند")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.ClosedAccountingPeriod)
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_waste_documents"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM stock_movements WHERE movementType='WASTE'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='WASTE'"))
        assertEquals(2_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(400_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        assertEquals(2_000_000L, database.inventoryLotDao().byId(lotId)?.quantityMicros)
    }

    @Test
    fun directInventoryCommandRejectsUnauthorizedSessionAndAuditsDenial() = runBlocking {
        val sessionAuthorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { NOW }, authorizer = sessionAuthorizer)
        security.save(null, UserDraft("ledger-owner-sec", "مالک امنیت", "123456", UserRole.OWNER, "87654321"))
        val cashierId = security.save(null, UserDraft("ledger-cash-sec", "صندوقدار امنیت", "654321", UserRole.CASHIER))
        security.switchUser(cashierId, "654321")
        val securedEngine = LocalInventoryCommandEngine(database, clock = { NOW }, authorizer = sessionAuthorizer)
        val itemId = insertItem("کالای مرز امنیت")

        try {
            securedEngine.receive(
                itemId = itemId,
                quantityMicros = 1_000_000,
                valueRial = 100_000,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = 9901,
                movementEpochDay = 100,
                context = InventoryCommandContext(
                    idempotencyKey = "security-inventory:9901",
                    correlationId = "security:inventory:9901",
                    actorId = cashierId,
                    deviceId = "instrumentation",
                    locationId = null,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "آزمون رد دسترسی مستقیم",
                ),
            )
            fail("فراخوانی مستقیم موتور موجودی توسط کاربر فاقد مجوز باید رد شود")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
        assertEquals(0L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='ACCESS_DENIED' AND actorId=$cashierId AND reason LIKE 'PERMISSION_DENIED:%'"),
        )
    }

    @Test
    fun directInventoryCommandRejectsActorMismatchAndAuditsDenial() = runBlocking {
        val sessionAuthorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { NOW }, authorizer = sessionAuthorizer)
        val ownerId = security.save(null, UserDraft("ledger-owner-actor", "مالک تطبیق actor", "123456", UserRole.OWNER, "87654321"))
        val securedEngine = LocalInventoryCommandEngine(database, clock = { NOW }, authorizer = sessionAuthorizer)
        val itemId = insertItem("کالای تطبیق actor")

        try {
            securedEngine.receive(
                itemId = itemId,
                quantityMicros = 1_000_000,
                valueRial = 100_000,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = 9902,
                movementEpochDay = 100,
                context = InventoryCommandContext(
                    idempotencyKey = "security-inventory:9902",
                    correlationId = "security:inventory:9902",
                    actorId = ownerId + 999L,
                    deviceId = "instrumentation",
                    locationId = null,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "آزمون عدم تطابق actor",
                ),
            )
            fail("actor داخل فرمان موجودی باید با Session جاری یکسان باشد")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.PermissionDenied)
        }
        assertEquals(0L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE action='ACCESS_DENIED' AND actorId=$ownerId AND reason LIKE 'ACTOR_MISMATCH:%'"),
        )
    }

    private suspend fun insertItem(
        name: String,
        stockMicros: Long = 0,
        inventoryValueRial: Long = 0,
    ): Long = database.inventoryDao().insert(
        InventoryItemEntity(
            name = name,
            category = "مواد اولیه",
            unit = "کیلوگرم",
            stockMicros = stockMicros,
            inventoryValueRial = inventoryValueRial,
            trackLot = true,
            createdAtEpochMillis = NOW,
            updatedAtEpochMillis = NOW,
        ),
    )

    private suspend fun signInOwner(gate: SensitiveActionGate): LocalSecurityRepository {
        val authorizer = SessionAuthorizer(database)
        return LocalSecurityRepository(
            db = database,
            clock = { NOW },
            authorizer = authorizer,
            sensitiveActionGate = gate,
        ).also { repository ->
            repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        }
    }

    private fun operationsRepository(gate: SensitiveActionGate) = LocalOperationsRepository(
        database = database,
        clock = { NOW },
        authorizer = SessionAuthorizer(database),
        sensitiveActionGate = gate,
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun command(key: String, referenceId: Long) = InventoryCommandContext(
        idempotencyKey = key,
        correlationId = "integration:inventory:$referenceId",
        actorId = 42,
        deviceId = "instrumentation",
        locationId = defaultLocationId,
        reasonCode = InventoryReasonCode.PURCHASE_RECEIPT,
        reason = "تست دریافت خرید",
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
