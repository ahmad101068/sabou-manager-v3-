package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface AssetDao {
    @Insert suspend fun insertAsset(entity: FixedAssetEntity): Long
    @Query("SELECT EXISTS(SELECT 1 FROM fixed_assets WHERE assetCode = :assetCode)") suspend fun assetCodeExists(assetCode: String): Boolean
    @Update suspend fun updateAsset(entity: FixedAssetEntity): Int
    @Query("SELECT * FROM fixed_assets WHERE id=:id LIMIT 1") suspend fun assetById(id: Long): FixedAssetEntity?
    @Query("""
        SELECT a.*,
               EXISTS(
                   SELECT 1 FROM journal_entries e
                   WHERE e.sourceType='ASSET_ACQUISITION'
                     AND e.sourceId=a.id
                     AND e.status='POSTED'
               ) AS isAccountingRecognized
        FROM fixed_assets a
        ORDER BY a.status, a.name
    """)
    fun observeAssets(): Flow<List<AssetAccountingRow>>
    @Query("UPDATE fixed_assets SET status='DISPOSED', updatedAtEpochMillis=:now WHERE id=:id AND status='ACTIVE'") suspend fun disposeAsset(id: Long, now: Long): Int
    @Query("SELECT * FROM asset_depreciations WHERE commandId=:commandId LIMIT 1") suspend fun depreciationByCommandId(commandId: String): AssetDepreciationEntity?
    @Query("SELECT * FROM asset_depreciations WHERE id=:id LIMIT 1") suspend fun depreciationById(id: Long): AssetDepreciationEntity?
    @Query("SELECT COALESCE(SUM(quantity),0) FROM asset_depreciations WHERE assetId=:assetId AND periodYear=:year AND periodMonth=:month AND reversedAtEpochMillis IS NULL") suspend fun activeDepreciatedQuantity(assetId: Long, year: Int, month: Int): Int
    @Query("SELECT COALESCE(SUM(amountRial),0) FROM asset_depreciations WHERE assetId=:assetId AND periodYear=:year AND periodMonth=:month AND reversedAtEpochMillis IS NULL") suspend fun activeDepreciatedAmount(assetId: Long, year: Int, month: Int): Long
    @Insert suspend fun insertDepreciation(entity: AssetDepreciationEntity): Long
    @Query("UPDATE fixed_assets SET accumulatedDepreciationRial=accumulatedDepreciationRial+:amount, updatedAtEpochMillis=:now WHERE id=:id AND status='ACTIVE' AND accumulatedDepreciationRial+:amount+impairmentRial <= purchaseCostRial-salvageValueRial") suspend fun addDepreciation(id: Long, amount: Long, now: Long): Int
    @Query("UPDATE fixed_assets SET accumulatedDepreciationRial=accumulatedDepreciationRial-:amount, updatedAtEpochMillis=:now WHERE id=:id AND accumulatedDepreciationRial>=:amount") suspend fun subtractDepreciation(id: Long, amount: Long, now: Long): Int
    @Query("UPDATE asset_depreciations SET reversedAtEpochMillis=:now,reversalEpochDay=:reversalEpochDay,reversalReason=:reason,reversalJournalEntryId=:journalId WHERE id=:id AND reversedAtEpochMillis IS NULL") suspend fun markDepreciationReversed(id: Long, now: Long, reversalEpochDay: Long, reason: String, journalId: Long): Int
    @Query("UPDATE fixed_assets SET location=:location, branch=:branch, branchId=:branchId, responsiblePerson=:responsible, updatedAtEpochMillis=:now WHERE id=:id AND status='ACTIVE'") suspend fun transferAsset(id: Long, location: String, branch: String, branchId: Long?, responsible: String, now: Long): Int
    @Query("UPDATE fixed_assets SET impairmentRial=impairmentRial+:amount, updatedAtEpochMillis=:now WHERE id=:id AND status='ACTIVE' AND accumulatedDepreciationRial+impairmentRial+:amount <= purchaseCostRial-salvageValueRial") suspend fun addImpairment(id: Long, amount: Long, now: Long): Int
    @Query("UPDATE fixed_assets SET status='SOLD', salePriceRial=:salePriceRial, disposedEpochDay=:epochDay, updatedAtEpochMillis=:now WHERE id=:id AND status='ACTIVE'") suspend fun markSold(id: Long, salePriceRial: Long, epochDay: Long, now: Long): Int
    @Query("""
      SELECT d.id,d.assetId,a.name AS assetName,d.periodYear,d.periodMonth,d.amountRial,d.quantity,d.postingEpochDay,d.reason,d.reversedAtEpochMillis
      FROM asset_depreciations d INNER JOIN fixed_assets a ON a.id=d.assetId
      ORDER BY d.periodYear DESC, d.periodMonth DESC, d.id DESC
    """) fun observeDepreciations(): Flow<List<AssetDepreciationRow>>
}

data class AssetDepreciationRow(
    val id:Long,val assetId:Long,val assetName:String,val periodYear:Int,val periodMonth:Int,val amountRial:Long,
    val quantity:Int,val postingEpochDay:Long,val reason:String,val reversedAtEpochMillis:Long?,
)

data class AssetAccountingRow(
    val id: Long,
    val assetCode: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val purchaseEpochDay: Long,
    val purchaseCostRial: Long,
    val salvageValueRial: Long,
    val usefulLifeMonths: Int,
    val accumulatedDepreciationRial: Long,
    val location: String,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val branch: String,
    val branchId: Long?,
    val responsiblePerson: String,
    val impairmentRial: Long,
    val disposedEpochDay: Long?,
    val salePriceRial: Long?,
    val isAccountingRecognized: Boolean,
)
