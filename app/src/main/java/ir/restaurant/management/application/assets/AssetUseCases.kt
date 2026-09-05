package ir.restaurant.management.application.assets

import ir.restaurant.management.domain.assets.*

class AssetUseCases(private val repository: AssetRepository) {
    val assets get() = repository.assets
    val depreciations get() = repository.depreciations
    suspend fun save(id: Long?, draft: AssetDraft) = repository.save(id, draft)
    suspend fun recognizeImported(id: Long) = repository.recognizeImportedAsset(id)
    suspend fun dispose(id: Long) = repository.dispose(id)
    suspend fun depreciate(draft: DepreciationDraft) = repository.postDepreciation(draft)
    suspend fun reverseDepreciation(draft: DepreciationReversalDraft) = repository.reverseDepreciation(draft)
    suspend fun transfer(draft: AssetTransferDraft) = repository.transfer(draft)
    suspend fun maintenance(draft: AssetMaintenanceDraft) = repository.recordMaintenance(draft)
    suspend fun impair(draft: AssetImpairmentDraft) = repository.impair(draft)
    suspend fun sell(draft: AssetSaleDraft) = repository.sell(draft)
    fun lifecycle(assetId: Long) = repository.observeLifecycle(assetId)
    fun maintenanceHistory(assetId: Long) = repository.observeMaintenance(assetId)
}
