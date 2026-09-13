package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AccountingPeriodLockEntity
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.PurchaseOrderEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.data.db.PurchaseRequisitionEntity
import ir.restaurant.management.data.db.SupplierEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.GoodsReceiptLineDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import ir.restaurant.management.domain.purchase.RequisitionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoodsReceiptTransactionIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalProcurementRepository
    private var orderId: Long = 0
    private var orderLineId: Long = 0
    private var itemId: Long = 0
    private var branchId: Long = 0
    private var destinationLocationId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val ownerId = LocalSecurityRepository(
            db = database,
            clock = { NOW },
            authorizer = authorizer,
            sensitiveActionGate = SensitiveActionGate(clockMillis = { NOW }),
        ).save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)
        destinationLocationId = database.inventoryLocationDao().insert(
            StorageLocationEntity(
                code = "GR-DEST-TEST",
                name = "انبار مقصد رسید آزمون",
                branchName = "شعبه آزمون دریافت",
                branchId = branchId,
                kind = "WAREHOUSE",
                createdAtEpochMillis = NOW,
            ),
        )
        val scopeDb = database.openHelper.writableDatabase
        scopeDb.execSQL(
            "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",
            arrayOf<Any?>(ownerId, branchId, NOW),
        )
        scopeDb.execSQL(
            "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",
            arrayOf<Any?>(ownerId, branchId, NOW),
        )
        scopeDb.execSQL(
            "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",
            arrayOf<Any?>(ownerId, destinationLocationId, NOW),
        )
        repository = LocalProcurementRepository(database, authorizer, clock = { NOW })
        seedOpenOrder()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun exactReplayReturnsOriginalReceiptWithoutDuplicateEffects() = runBlocking {
        val draft = receiptDraft()

        val firstId = repository.postGoodsReceipt(draft)
        val replayId = repository.postGoodsReceipt(draft)

        assertEquals(firstId, replayId)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM goods_receipts"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE referenceType='GOODS_RECEIPT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='GOODS_RECEIPT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='GOODS_RECEIPT' AND action='RECEIVE'"))
        assertEquals(1_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)

        try {
            repository.postGoodsReceipt(draft.copy(note = "payload changed"))
            fail("the same delivery-note key with a different payload must be rejected")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.IdempotencyConflict)
        }
    }

    @Test
    fun trackedGoodsReceiptCreatesLotMovementAndProjectionAtomically() = runBlocking {
        val item = requireNotNull(database.inventoryDao().byId(itemId))
        assertEquals(1, database.inventoryDao().update(item.copy(trackLot = true, trackExpiry = true)))

        val draft = trackedReceiptDraft()
        val receiptId = repository.postGoodsReceipt(draft)
        assertEquals(receiptId, repository.postGoodsReceipt(draft))
        val lot = database.inventoryLotDao().search(
            itemId = itemId,
            locationId = null,
            status = "ACTIVE",
            expiryFrom = null,
            expiryTo = null,
            limit = 10,
            offset = 0,
        ).single()

        assertEquals("SUP-LOT-1", lot.lotCode)
        assertEquals(RECEIPT_DAY + 30, lot.expiryEpochDay)
        assertEquals(receiptId, lot.sourceReceiptId)
        assertEquals(1_000_000L, lot.quantityMicros)
        assertEquals(1_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM stock_movements WHERE referenceType='GOODS_RECEIPT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM inventory_lots"))
    }

    @Test
    fun accountingFailureRollsBackReceiptInventoryProjectionAndMovement() = runBlocking {
        val item = requireNotNull(database.inventoryDao().byId(itemId))
        assertEquals(1, database.inventoryDao().update(item.copy(trackLot = true, trackExpiry = true)))
        database.managementControlDao().insertAccountingPeriodLock(
            AccountingPeriodLockEntity(
                fromEpochDay = RECEIPT_DAY,
                toEpochDay = RECEIPT_DAY,
                reason = "integration rollback",
                closedBy = "owner",
                closedAtEpochMillis = NOW,
            ),
        )

        try {
            repository.postGoodsReceipt(trackedReceiptDraft())
            fail("a closed accounting period must roll back the complete receipt")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.ClosedAccountingPeriod)
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM goods_receipts"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM stock_movements WHERE referenceType='GOODS_RECEIPT'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='GOODS_RECEIPT'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='GOODS_RECEIPT'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM inventory_lots"))
        assertEquals(0L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(0L, database.procurementDao().orderLines(orderId).single().receivedQtyMicros)
        assertEquals(PurchaseOrderStatus.OPEN.name, database.procurementDao().orderById(orderId)?.status)
    }

    private suspend fun seedOpenOrder() {
        val supplierId = database.supplierDao().insert(
            SupplierEntity(
                name = "تأمین‌کننده تست",
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "برنج تست دریافت",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                supplierId = supplierId,
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        val requisitionId = database.procurementDao().insertRequisition(
            PurchaseRequisitionEntity(
                requestNo = "REQ-TEST-1",
                department = "آشپزخانه",
                requiredEpochDay = RECEIPT_DAY,
                branchId = branchId,
                destinationLocationId = destinationLocationId,
                status = RequisitionStatus.APPROVED.name,
                requestedBy = "مالک",
                approvedBy = "مالک",
                note = "integration fixture",
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        orderId = database.procurementDao().insertOrder(
            PurchaseOrderEntity(
                orderNo = "PO-TEST-1",
                supplierId = supplierId,
                supplierNameSnapshot = "تأمین‌کننده تست",
                requisitionId = requisitionId,
                branchId = branchId,
                destinationLocationId = destinationLocationId,
                orderEpochDay = RECEIPT_DAY - 1,
                expectedEpochDay = RECEIPT_DAY,
                sentAtEpochMillis = NOW - 1,
                sentBy = "مالک",
                dispatchChannel = "HAND_DELIVERY",
                acknowledgedAtEpochMillis = NOW - 1,
                supplierConfirmationNo = "CONF-1",
                confirmedExpectedEpochDay = RECEIPT_DAY,
                status = PurchaseOrderStatus.OPEN.name,
                note = "integration fixture",
                createdBy = "مالک",
                createdAtEpochMillis = NOW,
                updatedAtEpochMillis = NOW,
            ),
        )
        database.procurementDao().insertOrderLines(
            listOf(
                PurchaseOrderLineEntity(
                    purchaseOrderId = orderId,
                    itemId = itemId,
                    itemNameSnapshot = "برنج تست دریافت",
                    supplierSkuSnapshot = null,
                    orderedQtyMicros = 1_000_000,
                    unitCostRial = 500_000,
                    receivedQtyMicros = 0,
                    rejectedQtyMicros = 0,
                ),
            ),
        )
        orderLineId = database.procurementDao().orderLines(orderId).single().id
    }

    private fun receiptDraft() = GoodsReceiptDraft(
        purchaseOrderId = orderId,
        receiptEpochDay = RECEIPT_DAY,
        deliveryNoteNo = "DN-TEST-1",
        destinationLocationId = destinationLocationId,
        note = "sealed delivery",
        lines = listOf(
            GoodsReceiptLineDraft(
                purchaseOrderLineId = orderLineId,
                deliveredQtyMicros = 1_000_000,
                acceptedQtyMicros = 1_000_000,
            ),
        ),
    )

    private fun trackedReceiptDraft() = receiptDraft().copy(
        lines = receiptDraft().lines.map {
            it.copy(
                lotNumber = "SUP-LOT-1",
                supplierLotNumber = "VENDOR-LOT-1",
                productionEpochDay = RECEIPT_DAY - 1,
                expiryEpochDay = RECEIPT_DAY + 30,
                lotBarcode = "626000000001",
            )
        },
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val RECEIPT_DAY = 20_000L
    }
}
