package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface AccountingDao {
    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun accountByCode(code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(entity: AccountEntity)

    @Update
    suspend fun updateAccount(entity: AccountEntity): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM journal_lines
        WHERE accountCode = :accountCode
        """,
    )
    suspend fun accountUsageCount(accountCode: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(jl.debitRial - jl.creditRial), 0)
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode
          AND je.status = 'POSTED'
        """,
    )
    suspend fun accountBalanceRial(accountCode: String): Long

    @Query(
        """
        SELECT
            a.code AS code,
            a.name AS name,
            a.type AS type,
            a.isSystem AS isSystem,
            COALESCE(SUM(
                CASE WHEN je.status = 'POSTED' THEN jl.debitRial ELSE 0 END
            ), 0) AS debitTurnoverRial,
            COALESCE(SUM(
                CASE WHEN je.status = 'POSTED' THEN jl.creditRial ELSE 0 END
            ), 0) AS creditTurnoverRial
        FROM accounts a
        LEFT JOIN journal_lines jl ON jl.accountCode = a.code
        LEFT JOIN journal_entries je ON je.id = jl.entryId
        WHERE a.isActive = 1
        GROUP BY a.code, a.name, a.type, a.isSystem
        ORDER BY a.code
        """,
    )
    fun observeAccountBalances(): Flow<List<AccountBalanceRow>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN a.type = 'REVENUE' THEN jl.creditRial - jl.debitRial ELSE 0 END), 0) AS revenueRial,
            COALESCE(SUM(CASE WHEN a.type = 'EXPENSE' THEN jl.debitRial - jl.creditRial ELSE 0 END), 0) AS expenseRial
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        INNER JOIN accounts a ON a.code = jl.accountCode
        WHERE je.status = 'POSTED'
          AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeProfitLoss(fromEpochDay: Long, toEpochDay: Long): Flow<ProfitLossRow>

    /**
     * Canonical branch P&L query. Only journals explicitly scoped to the requested numeric branch
     * participate. Organization-wide and unassigned legacy journals are excluded by construction.
     * Unassigned historical lines are counted separately as data-quality evidence.
     */
    @Query(
        """
        SELECT
            COALESCE((
                SELECT SUM(jl.creditRial - jl.debitRial)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'BRANCH'
                  AND je.branchId = :branchId
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode IN ('4101','4103')
            ), 0) AS revenueRial,
            COALESCE((
                SELECT SUM(jl.debitRial - jl.creditRial)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'BRANCH'
                  AND je.branchId = :branchId
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode = '5101'
            ), 0) AS cogsRial,
            COALESCE((
                SELECT SUM(jl.debitRial - jl.creditRial)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                JOIN accounts a ON a.code = jl.accountCode
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'BRANCH'
                  AND je.branchId = :branchId
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND a.type = 'EXPENSE'
                  AND jl.accountCode NOT IN ('5101','6101','6113','6114','6115')
            ), 0) AS operatingExpensesExcludingPayrollRial,
            COALESCE((
                SELECT SUM(jl.debitRial - jl.creditRial)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'BRANCH'
                  AND je.branchId = :branchId
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode IN ('6101','6113','6114','6115')
            ), 0) AS payrollRial,
            (
                SELECT COUNT(*)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'UNASSIGNED_LEGACY'
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode IN ('4101','4103')
            ) AS unassignedRevenueLineCount,
            (
                SELECT COUNT(*)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'UNASSIGNED_LEGACY'
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode = '5101'
            ) AS unassignedCogsLineCount,
            (
                SELECT COUNT(*)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                JOIN accounts a ON a.code = jl.accountCode
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'UNASSIGNED_LEGACY'
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND a.type = 'EXPENSE'
                  AND jl.accountCode NOT IN ('5101','6101','6113','6114','6115')
            ) AS unassignedOperatingExpenseLineCount,
            (
                SELECT COUNT(*)
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entryId
                WHERE je.status = 'POSTED'
                  AND je.accountingScope = 'UNASSIGNED_LEGACY'
                  AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
                  AND jl.accountCode IN ('6101','6113','6114','6115')
            ) AS unassignedPayrollLineCount
        """,
    )
    suspend fun branchProfitLoss(branchId: Long, fromEpochDay: Long, toEpochDay: Long): BranchProfitLossRow

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entity: JournalEntryEntity): Long

    @Query("SELECT * FROM journal_entries WHERE idempotencyKey = :key LIMIT 1")
    suspend fun entryByIdempotencyKey(key: String): JournalEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLinesUnchecked(lines: List<JournalLineEntity>)

    @Transaction
    suspend fun insertLines(lines: List<JournalLineEntity>) {
        JournalIntegrity.requireBalanced(lines)
        insertLinesUnchecked(lines)
    }

    @Query(
        """
        UPDATE journal_entries
        SET entryNo = :entryNo, sourceId = :sourceId
        WHERE id = :entryId AND status = 'DRAFT'
        """,
    )
    suspend fun finalizeEntryIdentity(
        entryId: Long,
        entryNo: String,
        sourceId: Long,
    ): Int

    @Query(
        """
        UPDATE journal_entries
        SET status = 'POSTED',
            postedAtEpochMillis = :postedAtEpochMillis,
            postedByActorId = :postedByActorId
        WHERE id = :entryId AND status = 'DRAFT'
        """,
    )
    suspend fun postDraftEntryUnchecked(
        entryId: Long,
        postedAtEpochMillis: Long,
        postedByActorId: Long,
    ): Int

    @Transaction
    suspend fun postDraftEntry(
        entryId: Long,
        postedAtEpochMillis: Long,
        postedByActorId: Long,
    ): Int {
        require(postedAtEpochMillis > 0 && postedByActorId > 0) { "مشخصات ثبت قطعی سند کامل نیست." }
        JournalIntegrity.requireBalanced(linesByEntry(entryId))
        return postDraftEntryUnchecked(entryId, postedAtEpochMillis, postedByActorId)
    }

    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun entryById(entryId: Long): JournalEntryEntity?

    @Query(
        """
        SELECT * FROM journal_entries
        WHERE reversalOfEntryId = :originalEntryId
          AND status = 'POSTED'
        ORDER BY id
        LIMIT 1
        """,
    )
    suspend fun postedReversalOf(originalEntryId: Long): JournalEntryEntity?

    @Query(
        """
        SELECT *
        FROM journal_lines
        WHERE entryId = :entryId
        ORDER BY id
        """,
    )
    suspend fun linesByEntry(entryId: Long): List<JournalLineEntity>

    @Query(
        """
        SELECT * FROM journal_entries
        WHERE sourceType = :sourceType
          AND sourceId = :sourceId
          AND status = 'POSTED'
        ORDER BY id
        LIMIT 1
        """,
    )
    suspend fun entryBySource(sourceType: String, sourceId: Long): JournalEntryEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM journal_entries
            WHERE sourceType = 'PURCHASE_REVERSAL'
              AND sourceId = :purchaseId
              AND status = 'POSTED'
        )
        """,
    )
    suspend fun hasPurchaseReversal(purchaseId: Long): Boolean

    @Query(
        """
        SELECT
            je.id AS journalEntryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS settlementEpochDay,
            debitLine.debitRial AS amountRial,
            CASE creditLine.accountCode
                WHEN '1101' THEN 'نقدی'
                WHEN '1104' THEN 'کارتخوان'
                WHEN '1102' THEN 'حواله'
                ELSE 'نامشخص'
            END AS paymentMethod,
            creditLine.memo AS referenceNo,
            debitLine.memo AS notes,
            EXISTS(
                SELECT 1
                FROM journal_entries reversal
                WHERE reversal.sourceType = 'PURCHASE_SETTLEMENT_REVERSAL'
                  AND reversal.reversalOfEntryId = je.id
                  AND reversal.status = 'POSTED'
            ) AS isReversed
        FROM journal_entries je
        INNER JOIN journal_lines debitLine
            ON debitLine.entryId = je.id
           AND debitLine.accountCode = '2101'
           AND debitLine.debitRial > 0
        INNER JOIN journal_lines creditLine
            ON creditLine.entryId = je.id
           AND creditLine.creditRial > 0
        WHERE je.sourceType = 'PURCHASE_SETTLEMENT'
          AND je.sourceId = :purchaseId
          AND je.status = 'POSTED'
        ORDER BY je.entryEpochDay DESC, je.id DESC
        """,
    )
    fun observePurchaseSettlements(purchaseId: Long): Flow<List<PurchaseSettlementRow>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM journal_entries
            WHERE sourceType = 'PURCHASE_SETTLEMENT_REVERSAL'
              AND reversalOfEntryId = :settlementEntryId
              AND status = 'POSTED'
        )
        """,
    )
    suspend fun hasSettlementReversal(settlementEntryId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE code = :code AND isActive = 1)")
    suspend fun activeAccountExists(code: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM journal_entries
            WHERE sourceType = 'REVERSAL'
              AND sourceId = :entryId
              AND status = 'POSTED'
        )
        """,
    )
    suspend fun hasPostedReversal(entryId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM journal_entries WHERE sourceType = 'PAYROLL_REVERSAL' AND sourceId = :payrollId AND status = 'POSTED')")
    suspend fun hasPayrollReversal(payrollId: Long): Boolean

    @Query(
        """
        SELECT
            je.id AS id,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS description,
            je.sourceType AS sourceType,
            COALESCE(SUM(jl.debitRial), 0) AS totalDebitRial,
            COALESCE(SUM(jl.creditRial), 0) AS totalCreditRial,
            EXISTS(
                SELECT 1
                FROM journal_entries reversal
                WHERE (
                        (reversal.sourceType = 'REVERSAL' AND reversal.sourceId = je.id)
                     OR (je.sourceType = 'DAILY_SALES' AND reversal.sourceType = 'DAILY_SALES_REVERSAL' AND reversal.sourceId = je.sourceId)
                     OR (je.sourceType = 'DAILY_SALES_COGS' AND reversal.sourceType = 'DAILY_SALES_COGS_REVERSAL' AND reversal.sourceId = je.sourceId)
                     OR (je.sourceType = 'PAYROLL' AND reversal.sourceType = 'PAYROLL_REVERSAL' AND reversal.sourceId = je.sourceId)
                )
                  AND reversal.status = 'POSTED'
            ) AS isReversed
        FROM journal_entries je
        LEFT JOIN journal_lines jl ON jl.entryId = je.id
        WHERE :query = ''
           OR je.entryNo LIKE '%' || :query || '%'
           OR je.description LIKE '%' || :query || '%'
           OR EXISTS(
                SELECT 1
                FROM journal_lines searchedLine
                INNER JOIN accounts searchedAccount
                    ON searchedAccount.code = searchedLine.accountCode
                WHERE searchedLine.entryId = je.id
                  AND (
                    searchedLine.accountCode LIKE '%' || :query || '%'
                    OR searchedAccount.name LIKE '%' || :query || '%'
                  )
           )
        GROUP BY
            je.id,
            je.entryNo,
            je.entryEpochDay,
            je.description,
            je.sourceType
        ORDER BY je.entryEpochDay DESC, je.id DESC
        """,
    )
    fun observeJournals(query: String): Flow<List<JournalListRow>>

    @Query(
        """
        SELECT
            je.id AS entryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS entryDescription,
            je.sourceType AS sourceType,
            je.sourceId AS sourceId,
            jl.id AS lineId,
            jl.accountCode AS accountCode,
            a.name AS accountName,
            jl.debitRial AS debitRial,
            jl.creditRial AS creditRial,
            jl.memo AS memo,
            EXISTS(
                SELECT 1
                FROM journal_entries reversal
                WHERE (
                        (reversal.sourceType = 'REVERSAL' AND reversal.sourceId = je.id)
                     OR (je.sourceType = 'DAILY_SALES' AND reversal.sourceType = 'DAILY_SALES_REVERSAL' AND reversal.sourceId = je.sourceId)
                     OR (je.sourceType = 'DAILY_SALES_COGS' AND reversal.sourceType = 'DAILY_SALES_COGS_REVERSAL' AND reversal.sourceId = je.sourceId)
                     OR (je.sourceType = 'PAYROLL' AND reversal.sourceType = 'PAYROLL_REVERSAL' AND reversal.sourceId = je.sourceId)
                )
                  AND reversal.status = 'POSTED'
            ) AS isReversed
        FROM journal_entries je
        INNER JOIN journal_lines jl ON jl.entryId = je.id
        INNER JOIN accounts a ON a.code = jl.accountCode
        WHERE je.id = :entryId
        ORDER BY jl.id
        """,
    )
    fun observeJournalDetails(entryId: Long): Flow<List<JournalDetailRow>>

    @Query(
        """
        SELECT
            jl.id AS lineId,
            je.id AS entryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS description,
            jl.debitRial AS debitRial,
            jl.creditRial AS creditRial
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode
          AND je.status = 'POSTED'
        ORDER BY je.entryEpochDay, je.id, jl.id
        """,
    )
    fun observeLedger(accountCode: String): Flow<List<AccountLedgerRow>>

    @Query(
        """
        SELECT COALESCE(SUM(jl.debitRial - jl.creditRial), 0)
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode AND je.status = 'POSTED'
        """,
    )
    fun observeBalanceRial(accountCode: String): Flow<Long>
}

data class AccountBalanceRow(
    val code: String,
    val name: String,
    val type: String,
    val isSystem: Boolean,
    val debitTurnoverRial: Long,
    val creditTurnoverRial: Long,
)
data class ProfitLossRow(
    val revenueRial: Long,
    val expenseRial: Long,
)

data class BranchProfitLossRow(
    val revenueRial: Long,
    val cogsRial: Long,
    val operatingExpensesExcludingPayrollRial: Long,
    val payrollRial: Long,
    val unassignedRevenueLineCount: Long,
    val unassignedCogsLineCount: Long,
    val unassignedOperatingExpenseLineCount: Long,
    val unassignedPayrollLineCount: Long,
)

data class JournalListRow(
    val id: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val totalDebitRial: Long,
    val totalCreditRial: Long,
    val isReversed: Boolean,
)

data class JournalDetailRow(
    val entryId: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val entryDescription: String,
    val sourceType: String,
    val sourceId: Long,
    val lineId: Long,
    val accountCode: String,
    val accountName: String,
    val debitRial: Long,
    val creditRial: Long,
    val memo: String,
    val isReversed: Boolean,
)

data class AccountLedgerRow(
    val lineId: Long,
    val entryId: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val debitRial: Long,
    val creditRial: Long,
)

data class PurchaseSettlementRow(
    val journalEntryId: Long,
    val entryNo: String,
    val settlementEpochDay: Long,
    val amountRial: Long,
    val paymentMethod: String,
    val referenceNo: String,
    val notes: String,
    val isReversed: Boolean,
)
