package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TreasuryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(entity: TreasuryTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedgerEntries(entries: List<TreasuryLedgerEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReconciliation(entity: TreasuryReconciliationEntity): Long

    @Query("SELECT * FROM treasury_reconciliations WHERE transactionId=:transactionId LIMIT 1")
    suspend fun reconciliationByTransactionId(transactionId: String): TreasuryReconciliationEntity?

    @Query("SELECT * FROM treasury_transactions WHERE commandId=:commandId LIMIT 1")
    suspend fun byCommandId(commandId: String): TreasuryTransactionEntity?

    @Query("SELECT * FROM treasury_transactions WHERE id=:id LIMIT 1")
    suspend fun transactionById(id: String): TreasuryTransactionEntity?

    @Query("SELECT * FROM treasury_transactions WHERE journalEntryId=:journalEntryId LIMIT 1")
    suspend fun transactionByJournalEntryId(journalEntryId: Long): TreasuryTransactionEntity?

    @Query("SELECT * FROM treasury_transactions WHERE sourceType=:sourceType AND sourceId=:sourceId AND status='POSTED' AND reversalOfTransactionId IS NULL ORDER BY createdAtEpochMillis,id")
    suspend fun activeTransactionsBySource(sourceType: String, sourceId: Long): List<TreasuryTransactionEntity>

    @Query("SELECT * FROM treasury_ledger_entries WHERE transactionId=:transactionId ORDER BY id")
    suspend fun entriesForTransaction(transactionId: String): List<TreasuryLedgerEntryEntity>

    @Query("UPDATE treasury_transactions SET status='REVERSED', reversedAtEpochMillis=:now WHERE id=:id AND status='POSTED'")
    suspend fun markReversed(id: String, now: Long): Int

    @Query("SELECT * FROM treasury_transactions ORDER BY createdAtEpochMillis DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int = 250): Flow<List<TreasuryTransactionEntity>>

    @Query("SELECT COALESCE(SUM(CASE direction WHEN 'RECEIPT' THEN amountRial ELSE -amountRial END),0) FROM treasury_ledger_entries WHERE accountId=:accountId")
    fun observeLedgerBalance(accountId: String): Flow<Long>
}



data class ActiveRecipeVersionRow(
    val versionId: Long,
    val menuItemId: Long,
    val menuItemName: String,
    val revisionNo: Int,
)

@Dao
interface RecipeLifecycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComponents(entities: List<RecipeComponentEntity>)

    @Query("DELETE FROM recipe_components WHERE recipeVersionId=:recipeVersionId")
    suspend fun deleteComponents(recipeVersionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubstitution(entity: RecipeSubstitutionEntity): Long

    @Query("SELECT * FROM recipe_components WHERE recipeVersionId=:recipeVersionId ORDER BY id")
    suspend fun components(recipeVersionId: Long): List<RecipeComponentEntity>

    @Query("SELECT * FROM recipe_substitutions WHERE recipeVersionId=:recipeVersionId ORDER BY effectiveFromEpochDay,id")
    suspend fun substitutions(recipeVersionId: Long): List<RecipeSubstitutionEntity>

    @Query("SELECT * FROM recipe_substitutions WHERE recipeVersionId=:recipeVersionId AND effectiveFromEpochDay<=:businessEpochDay ORDER BY effectiveFromEpochDay,id")
    suspend fun effectiveSubstitutions(recipeVersionId: Long, businessEpochDay: Long): List<RecipeSubstitutionEntity>

    @Query("SELECT subRecipeVersionId FROM recipe_components WHERE recipeVersionId=:recipeVersionId")
    suspend fun childRecipeVersions(recipeVersionId: Long): List<Long>

    @Query("UPDATE recipe_versions SET status=:status WHERE id=:versionId AND status=:expected")
    suspend fun transitionStatus(versionId: Long, expected: String, status: String): Int

    @Query("UPDATE recipe_versions SET status='RETIRED' WHERE menuItemId=:menuItemId AND status='ACTIVE' AND id!=:exceptVersionId")
    suspend fun retireOtherActive(menuItemId: Long, exceptVersionId: Long): Int

    @Query("SELECT * FROM recipe_versions WHERE id=:versionId")
    suspend fun versionById(versionId: Long): RecipeVersionEntity?

    @Query("""
        SELECT rv.id AS versionId, rv.menuItemId AS menuItemId, mi.name AS menuItemName, rv.revisionNo AS revisionNo
        FROM recipe_versions rv
        INNER JOIN menu_items mi ON mi.id=rv.menuItemId
        WHERE rv.status='ACTIVE'
        ORDER BY mi.name,rv.revisionNo DESC
    """)
    fun observeActiveVersionOptions(): Flow<List<ActiveRecipeVersionRow>>
}

@Dao
interface AssetLifecycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(entity: AssetLifecycleEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaintenance(entity: AssetMaintenanceEntity): Long

    @Query("SELECT * FROM asset_lifecycle_events WHERE assetId=:assetId ORDER BY businessEpochDay DESC,id DESC")
    fun observeEvents(assetId: Long): Flow<List<AssetLifecycleEventEntity>>

    @Query("SELECT * FROM asset_maintenance WHERE assetId=:assetId ORDER BY serviceEpochDay DESC,id DESC")
    fun observeMaintenance(assetId: Long): Flow<List<AssetMaintenanceEntity>>
}

@Dao
interface CustomerReceivableDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLedger(entity: CustomerReceivableLedgerEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMerge(entity: CustomerMergeHistoryEntity): Long

    @Query("SELECT * FROM customer_receivable_ledger WHERE customerId=:customerId ORDER BY businessEpochDay,id")
    fun observeLedger(customerId: Long): Flow<List<CustomerReceivableLedgerEntity>>

    @Query("""
        WITH RECURSIVE party_ids(id) AS (
            SELECT :customerId
            UNION ALL
            SELECT h.sourceCustomerId FROM customer_merge_history h JOIN party_ids p ON h.targetCustomerId=p.id
        )
        SELECT l.* FROM customer_receivable_ledger l JOIN party_ids p ON p.id=l.customerId
        ORDER BY l.businessEpochDay,l.id
    """)
    fun observeLedgerIncludingMerged(customerId: Long): Flow<List<CustomerReceivableLedgerEntity>>

    @Query("""
        WITH RECURSIVE party_ids(id) AS (
            SELECT :customerId
            UNION ALL
            SELECT h.sourceCustomerId FROM customer_merge_history h JOIN party_ids p ON h.targetCustomerId=p.id
        )
        SELECT id FROM party_ids
    """)
    suspend fun mergedCustomerIds(customerId: Long): List<Long>

    @Query("SELECT * FROM customer_receivable_ledger WHERE customerId=:customerId ORDER BY businessEpochDay,id")
    suspend fun ledger(customerId: Long): List<CustomerReceivableLedgerEntity>

    @Query("SELECT * FROM customer_receivable_ledger WHERE customerId IN (:customerIds) ORDER BY customerId,businessEpochDay,id")
    suspend fun ledgerForCustomers(customerIds: List<Long>): List<CustomerReceivableLedgerEntity>

    @Query("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM customer_receivable_ledger WHERE customerId=:customerId")
    suspend fun balanceRial(customerId: Long): Long

    @Query("SELECT * FROM customer_receivable_ledger ORDER BY customerId,businessEpochDay,id")
    suspend fun allLedger(): List<CustomerReceivableLedgerEntity>

    @Query("SELECT COALESCE(SUM(balance),0) FROM (SELECT SUM(debitRial-creditRial) AS balance FROM customer_receivable_ledger GROUP BY customerId HAVING balance>0)")
    suspend fun totalPositiveBalanceRial(): Long

    @Query("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM customer_receivable_ledger WHERE customerId=:customerId AND reference=:reference")
    suspend fun balanceByReference(customerId: Long, reference: String): Long

    @Query("SELECT * FROM customer_receivable_ledger WHERE sourceType=:sourceType AND reference=:reference LIMIT 1")
    suspend fun ledgerByReference(sourceType: String, reference: String): CustomerReceivableLedgerEntity?

    @Query("UPDATE sales_invoices SET customerId=:targetId WHERE customerId=:sourceId")
    suspend fun moveSalesInvoices(sourceId: Long, targetId: Long): Int

    @Query("UPDATE customer_receivable_ledger SET customerId=:targetId WHERE customerId=:sourceId")
    suspend fun moveLedger(sourceId: Long, targetId: Long): Int
}
