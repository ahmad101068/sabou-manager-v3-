package ir.restaurant.management.domain.assets

import ir.restaurant.management.core.GlobalId
import kotlinx.coroutines.flow.Flow

enum class AssetAcquisitionSource(val title: String) {
    CASH("پرداخت از صندوق"),
    BANK("پرداخت از بانک یا کارت"),
    PAYABLE("خرید نسیه / حساب پرداختنی"),
    OWNER_CAPITAL("آورده مالک"),
}

enum class AssetLifecycleType { PURCHASE, TRANSFER, MAINTENANCE, DEPRECIATION, DEPRECIATION_REVERSAL, IMPAIRMENT, SALE, DISPOSAL }

data class AssetDraft(
    val assetCode: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val purchaseEpochDay: Long,
    val purchaseCostRial: Long,
    val salvageValueRial: Long,
    val usefulLifeMonths: Int,
    val location: String,
    val notes: String,
    val acquisitionSource: AssetAcquisitionSource = AssetAcquisitionSource.BANK,
    val branch: String = "",
    val responsiblePerson: String = "",
    val branchId: Long? = null,
    val supplierId: Long? = null,
    val payableDueEpochDay: Long? = null,
) {
    fun validated() = copy(
        assetCode = assetCode.trim(), name = name.trim(), category = category.trim(),
        location = location.trim(), notes = notes.trim(), branch = branch.trim(), responsiblePerson = responsiblePerson.trim(),
    ).also {
        require(it.assetCode.isNotBlank()) { "کد دارایی الزامی است." }
        require(it.name.isNotBlank()) { "نام دارایی الزامی است." }
        require(it.quantity > 0) { "تعداد دارایی باید مثبت باشد." }
        require(it.purchaseEpochDay > 0) { "تاریخ خرید معتبر نیست." }
        require(it.purchaseCostRial > 0) { "بهای خرید باید مثبت باشد." }
        require(it.salvageValueRial in 0..it.purchaseCostRial) { "ارزش اسقاط نامعتبر است." }
        require(it.usefulLifeMonths > 0) { "عمر مفید باید مثبت باشد." }
        require(it.branchId == null || it.branchId > 0) { "شناسه شعبه دارایی معتبر نیست." }
        if (it.acquisitionSource == AssetAcquisitionSource.PAYABLE) {
            require((it.supplierId ?: 0L) > 0) { "برای خرید نسیه، تأمین‌کننده الزامی است." }
            require((it.payableDueEpochDay ?: 0L) >= it.purchaseEpochDay) { "سررسید حساب پرداختنی دارایی معتبر نیست." }
        } else {
            require(it.supplierId == null && it.payableDueEpochDay == null) { "تأمین‌کننده و سررسید فقط برای خرید نسیه ثبت می‌شوند." }
        }
    }
}

data class AssetRecord(
    val id: Long,
    val assetCode: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val purchaseEpochDay: Long,
    val purchaseCostRial: Long,
    val salvageValueRial: Long,
    val accumulatedDepreciationRial: Long,
    val usefulLifeMonths: Int,
    val location: String,
    val notes: String,
    val isActive: Boolean,
    val isAccountingRecognized: Boolean,
    val branch: String = "",
    val responsiblePerson: String = "",
    val impairmentRial: Long = 0,
    val status: String = if (isActive) "ACTIVE" else "DISPOSED",
    val branchId: Long? = null,
) {
    val bookValueRial: Long get() = purchaseCostRial - accumulatedDepreciationRial - impairmentRial
}

data class DepreciationRecord(
    val id: Long,
    val assetId: Long,
    val assetName: String,
    val periodYear: Int,
    val periodMonth: Int,
    val amountRial: Long,
    val quantity: Int,
    val postingEpochDay: Long,
    val reason: String,
    val isReversed: Boolean,
)

data class DepreciationDraft(
    val assetId: Long,
    val periodYear: Int,
    val periodMonth: Int,
    val postingEpochDay: Long,
    val quantity: Int = 1,
    val reason: String = "ثبت استهلاک دوره",
    val commandId: String = GlobalId.new().value,
) {
    fun validated() = copy(reason = reason.trim(), commandId = commandId.trim()).also {
        require(it.assetId > 0)
        require(it.periodMonth in 1..12)
        require(it.periodYear > 1300)
        require(it.postingEpochDay > 0)
        require(it.quantity > 0) { "تعداد واحدهای مورد استهلاک باید مثبت باشد." }
        require(it.reason.length in 3..300) { "دلیل / شرح استهلاک باید بین ۳ تا ۳۰۰ نویسه باشد." }
        require(it.commandId.isNotBlank()) { "شناسه یکتای عملیات استهلاک الزامی است." }
    }
}

data class DepreciationReversalDraft(
    val depreciationId: Long,
    val reversalEpochDay: Long,
    val reason: String,
) {
    fun validated() = copy(reason = reason.trim()).also {
        require(it.depreciationId > 0 && it.reversalEpochDay > 0)
        require(it.reason.length in 3..300) { "دلیل برگشت استهلاک باید بین ۳ تا ۳۰۰ نویسه باشد." }
    }
}

data class AssetTransferDraft(
    val assetId: Long,
    val toLocation: String,
    val toBranch: String,
    val toResponsiblePerson: String,
    val businessEpochDay: Long,
    val reason: String,
    val toBranchId: Long? = null,
)

data class AssetMaintenanceDraft(
    val assetId: Long,
    val serviceType: String,
    val serviceEpochDay: Long,
    val costRial: Long,
    val contractor: String = "",
    val note: String = "",
    val nextServiceEpochDay: Long? = null,
    val paymentSource: AssetAcquisitionSource = AssetAcquisitionSource.BANK,
    val supplierId: Long? = null,
    val payableDueEpochDay: Long? = null,
)

data class AssetImpairmentDraft(val assetId: Long, val businessEpochDay: Long, val amountRial: Long, val reason: String)
data class AssetSaleDraft(val assetId: Long, val businessEpochDay: Long, val salePriceRial: Long, val receiptSource: AssetAcquisitionSource = AssetAcquisitionSource.BANK, val buyer: String = "", val reason: String)

data class AssetLifecycleRecord(
    val id: Long,
    val assetId: Long,
    val type: AssetLifecycleType,
    val businessEpochDay: Long,
    val amountRial: Long,
    val note: String,
)

data class AssetMaintenanceRecord(val id: Long, val assetId: Long, val serviceType: String, val serviceEpochDay: Long, val costRial: Long, val contractor: String, val nextServiceEpochDay: Long?)

interface AssetRepository {
    val assets: Flow<List<AssetRecord>>
    val depreciations: Flow<List<DepreciationRecord>>
    suspend fun save(id: Long?, draft: AssetDraft): Long
    suspend fun recognizeImportedAsset(id: Long): Long
    suspend fun dispose(id: Long)
    suspend fun postDepreciation(draft: DepreciationDraft): Long
    suspend fun reverseDepreciation(draft: DepreciationReversalDraft): Long
    suspend fun transfer(draft: AssetTransferDraft): Long
    suspend fun recordMaintenance(draft: AssetMaintenanceDraft): Long
    suspend fun impair(draft: AssetImpairmentDraft): Long
    suspend fun sell(draft: AssetSaleDraft): Long
    fun observeLifecycle(assetId: Long): Flow<List<AssetLifecycleRecord>>
    fun observeMaintenance(assetId: Long): Flow<List<AssetMaintenanceRecord>>
}
