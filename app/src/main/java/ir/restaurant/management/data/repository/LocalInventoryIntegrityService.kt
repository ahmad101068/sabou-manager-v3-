package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.inventory.InventoryIntegrityIssue
import ir.restaurant.management.domain.inventory.InventoryIntegrityIssueType
import ir.restaurant.management.domain.inventory.InventoryIntegrityReport
import ir.restaurant.management.domain.inventory.InventoryIntegrityService
import ir.restaurant.management.domain.inventory.InventoryIntegritySeverity
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission

class LocalInventoryIntegrityService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val businessEpochDay: () -> Long = ::currentLocalEpochDay,
) : InventoryIntegrityService {
    override suspend fun verify(): InventoryIntegrityReport {
        authorizer.require(Permission.INVENTORY_VIEW)
        val dao = database.inventoryBalanceDao()
        val issues = buildList {
            dao.invalidBalances().forEach { balance ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.NEGATIVE_BALANCE,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = balance.itemId,
                        locationId = balance.locationId,
                        lotId = null,
                        referenceId = null,
                        expectedQuantityMicros = 0,
                        actualQuantityMicros = balance.onHandMicros,
                        expectedValueRial = 0,
                        actualValueRial = balance.inventoryValueRial,
                    ),
                )
            }
            dao.ledgerProjectionMismatches().forEach { mismatch ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.LEDGER_PROJECTION_MISMATCH,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = mismatch.itemId,
                        locationId = mismatch.locationId,
                        lotId = null,
                        referenceId = null,
                        expectedQuantityMicros = mismatch.ledgerQuantityMicros,
                        actualQuantityMicros = mismatch.projectionQuantityMicros,
                        expectedValueRial = mismatch.ledgerValueRial,
                        actualValueRial = mismatch.projectionValueRial,
                    ),
                )
            }
            dao.aggregateMismatches().forEach { mismatch ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.AGGREGATE_PROJECTION_MISMATCH,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = mismatch.itemId,
                        locationId = null,
                        lotId = null,
                        referenceId = null,
                        expectedQuantityMicros = mismatch.locationQuantityMicros,
                        actualQuantityMicros = mismatch.aggregateQuantityMicros,
                        expectedValueRial = mismatch.locationValueRial,
                        actualValueRial = mismatch.aggregateValueRial,
                    ),
                )
            }
            database.inventoryLotDao().lotBalanceMismatches().forEach { mismatch ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.LOT_BALANCE_MISMATCH,
                        severity = if (mismatch.lotQuantityMicros > mismatch.balanceQuantityMicros) {
                            InventoryIntegritySeverity.CRITICAL
                        } else {
                            InventoryIntegritySeverity.HIGH
                        },
                        itemId = mismatch.itemId,
                        locationId = mismatch.locationId,
                        lotId = null,
                        referenceId = null,
                        expectedQuantityMicros = mismatch.balanceQuantityMicros,
                        actualQuantityMicros = mismatch.lotQuantityMicros,
                        expectedValueRial = null,
                        actualValueRial = null,
                    ),
                )
            }
            database.inventoryLotDao().invalidLots().forEach { lot ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.INVALID_LOT,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = lot.itemId,
                        locationId = lot.locationId,
                        lotId = lot.id,
                        referenceId = lot.sourceReceiptId,
                        expectedQuantityMicros = null,
                        actualQuantityMicros = lot.quantityMicros,
                        expectedValueRial = null,
                        actualValueRial = null,
                    ),
                )
            }
            database.inventoryLotDao().expiredActiveLots(businessEpochDay()).forEach { lot ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.EXPIRED_ACTIVE_LOT,
                        severity = InventoryIntegritySeverity.HIGH,
                        itemId = lot.itemId,
                        locationId = lot.locationId,
                        lotId = lot.id,
                        referenceId = lot.sourceReceiptId,
                        expectedQuantityMicros = null,
                        actualQuantityMicros = lot.quantityMicros,
                        expectedValueRial = null,
                        actualValueRial = null,
                    ),
                )
            }
            database.inventoryLotDao().orphanLots().forEach { lot ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.INVALID_LOCATION,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = lot.itemId,
                        locationId = lot.locationId,
                        lotId = lot.id,
                        referenceId = null,
                        expectedQuantityMicros = null,
                        actualQuantityMicros = lot.quantityMicros,
                        expectedValueRial = null,
                        actualValueRial = null,
                    ),
                )
            }
            dao.orphanMovements().forEach { movement ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.ORPHAN_MOVEMENT,
                        severity = InventoryIntegritySeverity.HIGH,
                        itemId = movement.itemId,
                        locationId = movement.locationId,
                        lotId = null,
                        referenceId = movement.movementId,
                        expectedQuantityMicros = null,
                        actualQuantityMicros = null,
                        expectedValueRial = null,
                        actualValueRial = null,
                    ),
                )
            }
            dao.transferImbalances().forEach { transfer ->
                add(
                    InventoryIntegrityIssue(
                        type = InventoryIntegrityIssueType.TRANSFER_IMBALANCE,
                        severity = InventoryIntegritySeverity.CRITICAL,
                        itemId = null,
                        locationId = null,
                        lotId = null,
                        referenceId = transfer.transferId,
                        expectedQuantityMicros = 0,
                        actualQuantityMicros = transfer.quantityDeltaMicros,
                        expectedValueRial = 0,
                        actualValueRial = transfer.valueDeltaRial,
                    ),
                )
            }
        }
        return InventoryIntegrityReport(clock(), issues)
    }
}
