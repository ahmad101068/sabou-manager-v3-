package ir.restaurant.management.application.inventory

import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodReopenDraft
import ir.restaurant.management.domain.operations.OperationsRepository
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierMergeDraft
import ir.restaurant.management.domain.operations.WasteDraft

/**
 * Application boundary for the still-active legacy Operations inventory surface.
 *
 * The modern InventoryWorkspace uses [InventoryUseCases]. This boundary prevents the older
 * OperationsViewModel from bypassing application policy while that presentation surface remains
 * reachable. Validation and sensitive-action authorization happen here before repository commands.
 */
class OperationsInventoryUseCases(
    private val operations: OperationsRepository,
    private val security: SecurityRepository,
) {
    suspend fun saveSupplier(id: Long?, draft: SupplierDraft): Long {
        val valid = draft.validated()
        return if (id == null) operations.createSupplier(valid) else {
            require(id > 0) { "شناسه تأمین‌کننده معتبر نیست." }
            operations.updateSupplier(id, valid)
            id
        }
    }

    suspend fun deactivateSupplier(id: Long) {
        require(id > 0) { "شناسه تأمین‌کننده معتبر نیست." }
        operations.deactivateSupplier(id)
    }

    suspend fun mergeSupplier(draft: SupplierMergeDraft) {
        operations.mergeSupplier(draft.validated())
    }

    suspend fun saveInventoryItem(id: Long?, draft: InventoryItemDraft): Long {
        val valid = draft.validated()
        return if (id == null) operations.createInventoryItem(valid) else {
            require(id > 0) { "شناسه کالا معتبر نیست." }
            operations.updateInventoryItem(id, valid)
            id
        }
    }

    suspend fun deactivateInventoryItem(id: Long) {
        require(id > 0) { "شناسه کالا معتبر نیست." }
        operations.deactivateInventoryItem(id)
    }

    suspend fun postInventoryCount(draft: InventoryCountDraft, pin: String): Long {
        val valid = draft.validated()
        val context = SensitiveActionContext.resource("INVENTORY_COUNT", "${valid.locationId}:${valid.itemId}:${valid.countEpochDay}", commandFingerprint = valid.commandId)
        security.authorizeSensitiveAction(SensitiveAction.ADJUST_INVENTORY, pin, context)
        return operations.postInventoryCount(valid)
    }

    suspend fun closeInventoryPeriod(draft: InventoryPeriodCloseDraft, pin: String): Long {
        val valid = draft.validated()
        security.authorizeSensitiveAction(SensitiveAction.CLOSE_INVENTORY_PERIOD, pin, SensitiveActionContext.resource("INVENTORY_PERIOD", "${valid.fromEpochDay}:${valid.toEpochDay}"))
        return operations.closeInventoryPeriod(valid)
    }

    suspend fun reopenInventoryPeriod(closureId: Long, reason: String, pin: String) {
        val valid = InventoryPeriodReopenDraft(closureId, reason).validated()
        security.authorizeSensitiveAction(SensitiveAction.REOPEN_INVENTORY_PERIOD, pin, SensitiveActionContext.resource("INVENTORY_PERIOD", valid.closureId))
        operations.reopenInventoryPeriod(valid)
    }

    suspend fun postWaste(draft: WasteDraft): Long = operations.postWaste(draft.validated())
}
