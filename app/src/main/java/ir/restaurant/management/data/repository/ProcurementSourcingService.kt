package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryReplenishmentPolicyEntity
import ir.restaurant.management.data.db.SupplierItemOfferEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.inventory.InventoryReplenishmentRisk
import ir.restaurant.management.domain.inventory.InventoryReplenishmentService
import ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft
import ir.restaurant.management.domain.purchase.RequisitionLineDraft
import ir.restaurant.management.domain.purchase.SupplierOfferCandidate
import ir.restaurant.management.domain.purchase.SupplierOfferDraft
import ir.restaurant.management.domain.purchase.SupplierOfferRecord
import ir.restaurant.management.domain.purchase.SupplierSourcingAdvisor
import ir.restaurant.management.domain.security.Permission

/**
 * Sourcing/replenishment responsibility separated from requisition/order lifecycle.
 *
 * It owns policy/offer validation and turns inventory demand into a supplier-aware requisition while
 * keeping the active-request check and requisition submission in the same Room transaction.
 */
internal class ProcurementSourcingService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val inventoryReplenishment: InventoryReplenishmentService,
    private val syncRecorder: SyncRecorder? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayEpochDay: () -> Long,
) {
    suspend fun saveReplenishmentPolicy(draft: ReplenishmentPolicyDraft) {
        authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        database.withTransaction {
            database.inventoryDao().activeById(valid.itemId)
                ?: error("کالای فعال پیدا نشد.")
            valid.preferredSupplierId?.let { supplierId ->
                database.supplierDao().activeById(supplierId)
                    ?: error("تأمین‌کننده ترجیحی فعال نیست.")
            }
            val now = clock()
            database.procurementDao().upsertReplenishmentPolicy(
                InventoryReplenishmentPolicyEntity(
                    itemId = valid.itemId,
                    preferredSupplierId = valid.preferredSupplierId,
                    targetCoverDays = valid.targetCoverDays,
                    leadTimeDays = valid.leadTimeDays,
                    safetyStockMicros = valid.safetyStockMicros,
                    orderMultipleMicros = valid.orderMultipleMicros,
                    isEnabled = valid.isEnabled,
                    updatedBy = authorizer.actor(),
                    updatedAtEpochMillis = now,
                ),
            )
            syncRecorder?.record("REPLENISHMENT_POLICY", valid.itemId, "UPSERT", now)
        }
    }

    suspend fun saveSupplierOffer(draft: SupplierOfferDraft) {
        authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        database.withTransaction {
            database.inventoryDao().activeById(valid.itemId) ?: error("کالای فعال پیدا نشد.")
            database.supplierDao().activeById(valid.supplierId) ?: error("تأمین‌کننده فعال پیدا نشد.")
            val now = clock()
            val id = database.procurementDao().upsertSupplierItemOffer(
                SupplierItemOfferEntity(
                    supplierId = valid.supplierId,
                    itemId = valid.itemId,
                    supplierSku = valid.supplierSku,
                    unitCostRial = valid.unitCostRial,
                    minimumOrderMicros = valid.minimumOrderMicros,
                    orderMultipleMicros = valid.orderMultipleMicros,
                    leadTimeDays = valid.leadTimeDays,
                    validUntilEpochDay = valid.validUntilEpochDay,
                    isActive = valid.isActive,
                    updatedBy = authorizer.actor(),
                    updatedAtEpochMillis = now,
                ),
            )
            syncRecorder?.record("SUPPLIER_ITEM_OFFER", id, "UPSERT", now)
        }
    }

    suspend fun submitSuggestedRequisition(
        itemIds: List<Long>,
        submit: suspend (PurchaseRequisitionDraft) -> Long,
    ): Long {
        authorizer.require(Permission.PURCHASES)
        val selected = itemIds.distinct()
        require(selected.isNotEmpty() && selected.size <= 100) { "بین ۱ تا ۱۰۰ پیشنهاد را انتخاب کنید." }
        val today = todayEpochDay()
        return database.withTransaction {
            val prepared = selected.map { itemId ->
                val policyEntity = database.procurementDao().replenishmentPolicy(itemId)
                    ?: error("سیاست تأمین یکی از کالاها پیدا نشد.")
                require(policyEntity.isEnabled) { "سیاست تأمین یکی از کالاها غیرفعال است." }
                require(!database.procurementDao().activeRequestExistsForItem(itemId)) {
                    "برای یکی از کالاها درخواست خرید فعال وجود دارد."
                }
                val inventorySuggestion = inventoryReplenishment.recommendation(itemId, null, today)
                    ?: error("یکی از کالاهای انتخاب‌شده در موجودی فعال پیدا نشد.")
                require(inventorySuggestion.suggestedQuantityMicros > 0) {
                    "یکی از کالاهای انتخاب‌شده دیگر نیاز به سفارش ندارد."
                }
                val baseSuggestion = ir.restaurant.management.domain.purchase.ReplenishmentSuggestion(
                    itemId = inventorySuggestion.itemId,
                    itemName = inventorySuggestion.itemName,
                    preferredSupplierId = inventorySuggestion.preferredSupplierId,
                    preferredSupplierScore = null,
                    averageDailyUsageMicros = inventorySuggestion.averageDailyUsageMicros,
                    currentStockMicros = inventorySuggestion.onHandMicros,
                    openPurchaseOrderMicros = inventorySuggestion.onOrderMicros,
                    projectedAtDeliveryMicros = inventorySuggestion.projectedAtDeliveryMicros,
                    suggestedOrderMicros = inventorySuggestion.suggestedQuantityMicros,
                    estimatedUnitCostRial = inventorySuggestion.estimatedUnitCostRial,
                    estimatedOrderValueRial = inventorySuggestion.estimatedOrderValueRial,
                    daysOfCoverBasisPoints = inventorySuggestion.daysOfCoverBasisPoints ?: Long.MAX_VALUE,
                    risk = when (inventorySuggestion.risk) {
                        InventoryReplenishmentRisk.OUT_OF_STOCK,
                        InventoryReplenishmentRisk.BELOW_SAFETY_STOCK ->
                            ir.restaurant.management.domain.purchase.ReplenishmentRisk.CRITICAL
                        InventoryReplenishmentRisk.BELOW_REORDER_POINT,
                        InventoryReplenishmentRisk.LEAD_TIME_RISK ->
                            ir.restaurant.management.domain.purchase.ReplenishmentRisk.HIGH
                        InventoryReplenishmentRisk.NO_USAGE_HISTORY,
                        InventoryReplenishmentRisk.HEALTHY,
                        InventoryReplenishmentRisk.DISABLED ->
                            ir.restaurant.management.domain.purchase.ReplenishmentRisk.MEDIUM
                    },
                    blockedByPendingRequest = inventorySuggestion.hasPendingRequisition,
                )
                val candidates = database.procurementDao()
                    .validSupplierItemOffers(inventorySuggestion.itemId, today)
                    .map { offer ->
                        val supplier = database.supplierDao().activeById(offer.supplierId)
                            ?: error("یکی از تأمین‌کنندگان پیشنهاد قیمت فعال نیست.")
                        SupplierOfferCandidate(
                            SupplierOfferRecord(
                                id = offer.id,
                                supplierId = offer.supplierId,
                                supplierName = supplier.name,
                                itemId = offer.itemId,
                                itemName = inventorySuggestion.itemName,
                                supplierSku = offer.supplierSku,
                                unitCostRial = offer.unitCostRial,
                                minimumOrderMicros = offer.minimumOrderMicros,
                                orderMultipleMicros = offer.orderMultipleMicros,
                                leadTimeDays = offer.leadTimeDays,
                                validUntilEpochDay = offer.validUntilEpochDay,
                                isActive = offer.isActive,
                            ),
                            supplierScore = null,
                        )
                    }
                val sourcing = SupplierSourcingAdvisor.choose(
                    candidates,
                    baseSuggestion.suggestedOrderMicros,
                    inventorySuggestion.estimatedUnitCostRial,
                    inventorySuggestion.preferredSupplierId,
                )
                val suggestion = if (sourcing == null) baseSuggestion else baseSuggestion.copy(
                    suggestedOrderMicros = sourcing.orderQuantityMicros,
                    estimatedUnitCostRial = sourcing.offer.unitCostRial,
                    estimatedOrderValueRial = sourcing.orderValueRial,
                    recommendedSupplierId = sourcing.offer.supplierId,
                    recommendedSupplierName = sourcing.offer.supplierName,
                    recommendedSupplierSku = sourcing.offer.supplierSku,
                    comparedOfferCount = sourcing.comparedOfferCount,
                    recommendedLeadTimeDays = sourcing.offer.leadTimeDays,
                    offerValidUntilEpochDay = sourcing.offer.validUntilEpochDay,
                    estimatedSavingsRial = sourcing.estimatedSavingsRial,
                )
                suggestion to (suggestion.recommendedLeadTimeDays ?: inventorySuggestion.leadTimeDays)
            }
            submit(
                PurchaseRequisitionDraft(
                    department = "انبار / تأمین هوشمند",
                    requiredEpochDay = today + prepared.minOf { it.second }.toLong(),
                    note = "ایجاد خودکار بر اساس مصرف ۳۰روزه، ذخیره اطمینان و سفارش‌های باز",
                    lines = prepared.map { (suggestion, _) ->
                        RequisitionLineDraft(
                            itemId = suggestion.itemId,
                            quantityMicros = suggestion.suggestedOrderMicros,
                            estimatedUnitCostRial = suggestion.estimatedUnitCostRial,
                            recommendedSupplierId = suggestion.recommendedSupplierId,
                            supplierSku = suggestion.recommendedSupplierSku,
                            recommendedLeadTimeDays = suggestion.recommendedLeadTimeDays,
                            note = suggestion.recommendedSupplierName?.let { supplier ->
                                "تأمین پیشنهادی: $supplier؛ انتخاب‌شده از ${suggestion.comparedOfferCount} پیشنهاد معتبر"
                            } ?: "پوشش هدف و زمان تأمین محاسبه‌شده توسط موتور پیشنهاد سفارش",
                        )
                    },
                ),
            )
        }
    }
}
