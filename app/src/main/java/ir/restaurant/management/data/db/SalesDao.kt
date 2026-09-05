package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    @Insert suspend fun insertCustomer(entity: CustomerEntity): Long
    @Update suspend fun updateCustomer(entity: CustomerEntity): Int
    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1") suspend fun customerById(id: Long): CustomerEntity?
    @Query("SELECT * FROM customers WHERE id = :id AND isActive = 1 LIMIT 1") suspend fun activeCustomerById(id: Long): CustomerEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM customers WHERE phone = :phone AND phone != '' AND id != :excludeId)") suspend fun customerPhoneExists(phone: String, excludeId: Long = 0): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM customers WHERE nationalId = :nationalId AND nationalId != '' AND id != :excludeId)") suspend fun customerNationalIdExists(nationalId: String, excludeId: Long = 0): Boolean
    @Query("UPDATE customers SET isActive = 0, status='INACTIVE', updatedAtEpochMillis = :now WHERE id = :id AND isActive = 1") suspend fun deactivateCustomer(id: Long, now: Long): Int
    @Query("UPDATE customers SET isActive = 0, status='MERGED', updatedAtEpochMillis = :now WHERE id = :id AND isActive = 1") suspend fun markCustomerMerged(id: Long, now: Long): Int
    @Query("SELECT * FROM customers WHERE id != :excludeId AND isActive=1 AND ((:phone!='' AND phone=:phone) OR (:nationalId!='' AND nationalId=:nationalId)) ORDER BY name") suspend fun duplicateCustomerCandidates(phone: String, nationalId: String, excludeId: Long = 0): List<CustomerEntity>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED') AS todayInvoiceCount,
          (SELECT COUNT(*) FROM sales_returns WHERE returnEpochDay=:epochDay) AS todayReturnCount,
          (COALESCE((SELECT SUM(netRial) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED'),0)
           - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay),0)) AS todayNetSalesRial,
          (COALESCE((SELECT SUM(theoreticalCostRial) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED'),0)
           - COALESCE((SELECT SUM(cogsRial) FROM sales_returns WHERE returnEpochDay=:epochDay),0)) AS todayCogsRial,
          (COALESCE((SELECT SUM(creditRial) FROM sales_invoices WHERE status!='VOIDED'),0)
           - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE refundMethod='CREDIT'),0)) AS customerReceivablesRial
        """,
    )
    fun observeDashboardKpis(epochDay: Long): Flow<SalesDashboardKpiRow>

    @Query(
        """
        SELECT c.id, c.customerCode, c.name, c.phone, c.nationalId, c.creditLimitRial, c.notes, c.isActive,
               c.mobile, c.address, c.branch, c.paymentTermsDays, c.status, c.partyType,
               COALESCE((SELECT SUM(crl.debitRial-crl.creditRial) FROM customer_receivable_ledger crl WHERE crl.customerId=c.id), 0) AS outstandingRial
        FROM customers c
        ORDER BY c.isActive DESC, c.name
        """,
    )
    fun observeCustomers(): Flow<List<CustomerWithOutstandingRow>>

    @Query(
        """
        SELECT COALESCE((SELECT SUM(crl.debitRial-crl.creditRial) FROM customer_receivable_ledger crl WHERE crl.customerId=:customerId), 0)
        """,
    )
    suspend fun outstandingRial(customerId: Long): Long

    @Insert suspend fun insertInvoice(entity: SalesInvoiceEntity): Long
    @Update suspend fun updateInvoice(entity: SalesInvoiceEntity): Int
    @Query("SELECT * FROM sales_invoices WHERE id = :id LIMIT 1") suspend fun invoiceById(id: Long): SalesInvoiceEntity?
    @Query("SELECT * FROM sales_invoices WHERE commandId = :commandId LIMIT 1") suspend fun invoiceByCommandId(commandId: String): SalesInvoiceEntity?
    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(grossRial) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED'),0) AS grossSalesRial,
          COALESCE((SELECT SUM(netRial) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED'),0)
            - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay),0) AS netSalesRial,
          COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay),0) AS returnRial,
          COALESCE((SELECT SUM(theoreticalCostRial) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED'),0)
            - COALESCE((SELECT SUM(cogsRial) FROM sales_returns WHERE returnEpochDay=:epochDay),0) AS cogsRial,
          COALESCE((SELECT SUM(sp.amountRial) FROM sales_payments sp INNER JOIN sales_invoices si ON si.id=sp.invoiceId WHERE si.businessEpochDay=:epochDay AND si.status!='VOIDED' AND sp.method='CASH'),0)
            - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay AND refundMethod='CASH'),0) AS cashRial,
          COALESCE((SELECT SUM(sp.amountRial) FROM sales_payments sp INNER JOIN sales_invoices si ON si.id=sp.invoiceId WHERE si.businessEpochDay=:epochDay AND si.status!='VOIDED' AND sp.method='CARD'),0)
            - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay AND refundMethod='CARD'),0) AS cardRial,
          COALESCE((SELECT SUM(sp.amountRial) FROM sales_payments sp INNER JOIN sales_invoices si ON si.id=sp.invoiceId WHERE si.businessEpochDay=:epochDay AND si.status!='VOIDED' AND sp.method='TRANSFER'),0)
            - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay AND refundMethod='TRANSFER'),0) AS transferRial,
          COALESCE((SELECT SUM(sp.amountRial) FROM sales_payments sp INNER JOIN sales_invoices si ON si.id=sp.invoiceId WHERE si.businessEpochDay=:epochDay AND si.status!='VOIDED' AND sp.method='CREDIT'),0)
            - COALESCE((SELECT SUM(refundRial) FROM sales_returns WHERE returnEpochDay=:epochDay AND refundMethod='CREDIT'),0) AS creditRial,
          (SELECT COUNT(*) FROM sales_invoices WHERE businessEpochDay=:epochDay AND status!='VOIDED') AS invoiceCount,
          (SELECT COUNT(*) FROM sales_returns WHERE returnEpochDay=:epochDay) AS returnCount
        """,
    )
    suspend fun dayTotals(epochDay: Long): SalesDayTotalsRow

    @Query("SELECT EXISTS(SELECT 1 FROM invoice_sales_day_closures WHERE businessEpochDay=:epochDay AND status='CLOSED')")
    suspend fun salesDayClosed(epochDay: Long): Boolean
    @Query("SELECT * FROM invoice_sales_day_closures WHERE businessEpochDay=:epochDay LIMIT 1")
    suspend fun salesDayClosure(epochDay: Long): InvoiceSalesDayClosureEntity?
    @Query("SELECT * FROM invoice_sales_day_closures ORDER BY businessEpochDay DESC LIMIT 120")
    fun observeSalesDayClosures(): Flow<List<InvoiceSalesDayClosureEntity>>
    @Insert suspend fun insertSalesDayClosure(entity: InvoiceSalesDayClosureEntity): Long
    @Update suspend fun updateSalesDayClosure(entity: InvoiceSalesDayClosureEntity): Int

    @Query("SELECT EXISTS(SELECT 1 FROM sales_invoices WHERE invoiceNo = :invoiceNo)") suspend fun invoiceNoExists(invoiceNo: String): Boolean
    @Query("SELECT COUNT(*) FROM sales_invoices WHERE businessEpochDay = :epochDay AND status != 'VOIDED'") suspend fun activeInvoiceCountForDay(epochDay: Long): Long
    @Query("SELECT * FROM sales_invoices WHERE voidCommandId = :commandId LIMIT 1") suspend fun invoiceByVoidCommandId(commandId: String): SalesInvoiceEntity?
    @Query("UPDATE sales_invoices SET journalEntryId = :journalId, cogsJournalEntryId = :cogsJournalId WHERE id = :invoiceId")
    suspend fun linkInvoiceJournals(invoiceId: Long, journalId: Long, cogsJournalId: Long?): Int

    @Insert suspend fun insertInvoiceLine(entity: SalesInvoiceLineEntity): Long
    @Insert suspend fun insertPayments(entities: List<SalesPaymentEntity>)
    @Insert suspend fun insertConsumptionSnapshots(entities: List<SalesConsumptionSnapshotEntity>)
    @Query("SELECT * FROM sales_invoice_lines WHERE invoiceId = :invoiceId ORDER BY id") suspend fun invoiceLines(invoiceId: Long): List<SalesInvoiceLineEntity>
    @Query("SELECT * FROM sales_payments WHERE invoiceId = :invoiceId ORDER BY id") suspend fun payments(invoiceId: Long): List<SalesPaymentEntity>
    @Query("SELECT * FROM sales_consumption_snapshots WHERE invoiceLineId = :invoiceLineId ORDER BY id") suspend fun consumptionSnapshots(invoiceLineId: Long): List<SalesConsumptionSnapshotEntity>

    @Query(
        """
        SELECT si.id, si.invoiceNo, si.businessEpochDay, c.name AS customerName,
               si.grossRial, si.discountRial, si.serviceRial, si.taxRial, si.netRial,
               si.theoreticalCostRial, si.status, si.notes, si.createdAtEpochMillis
        FROM sales_invoices si
        LEFT JOIN customers c ON c.id = si.customerId
        WHERE :query = '' OR si.invoiceNo LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' OR si.notes LIKE '%' || :query || '%'
        ORDER BY si.businessEpochDay DESC, si.id DESC
        LIMIT 100
        """,
    )
    fun observeInvoices(query: String): Flow<List<SalesInvoiceListRow>>

    @Query(
        """
        SELECT si.id, si.invoiceNo, si.businessEpochDay, c.name AS customerName,
               si.grossRial, si.discountRial, si.serviceRial, si.taxRial, si.netRial,
               si.theoreticalCostRial, si.status, si.notes, si.createdAtEpochMillis
        FROM sales_invoices si
        LEFT JOIN customers c ON c.id = si.customerId
        ORDER BY si.businessEpochDay DESC, si.id DESC
        LIMIT :limit
        """,
    )
    fun observeRecentInvoices(limit: Int): Flow<List<SalesInvoiceListRow>>

    @Query(
        """
        SELECT
          (COALESCE((SELECT SUM(netRial) FROM sales_invoices
             WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status!='VOIDED'),0)
           - COALESCE((SELECT SUM(refundRial) FROM sales_returns
             WHERE returnEpochDay BETWEEN :fromEpochDay AND :toEpochDay),0)) AS netSalesRial,
          COALESCE((SELECT SUM(netRial) FROM sales_invoices
             WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status!='VOIDED'),0) AS invoiceNetRial,
          (SELECT COUNT(*) FROM sales_invoices
             WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status!='VOIDED') AS invoiceCount,
          COALESCE((SELECT SUM(refundRial) FROM sales_returns
             WHERE returnEpochDay BETWEEN :fromEpochDay AND :toEpochDay),0) AS returnRial,
          (SELECT COUNT(*) FROM customers
             WHERE createdAtEpochMillis>=:createdFromEpochMillis AND createdAtEpochMillis<:createdToEpochMillisExclusive) AS newCustomerCount,
          COALESCE((SELECT SUM(debitRial-creditRial) FROM customer_receivable_ledger),0) AS customerReceivablesRial
        """,
    )
    fun observeDashboardSummary(
        fromEpochDay: Long,
        toEpochDay: Long,
        createdFromEpochMillis: Long,
        createdToEpochMillisExclusive: Long,
    ): Flow<SalesDashboardSummaryRow>

    @Query(
        """
        SELECT si.id, si.invoiceNo, si.businessEpochDay, si.customerId, c.name AS customerName, si.dueEpochDay,
               si.grossRial, si.discountRial, si.serviceRial, si.taxRial, si.netRial,
               si.theoreticalCostRial, si.status, si.notes, si.createdAtEpochMillis
        FROM sales_invoices si LEFT JOIN customers c ON c.id = si.customerId
        WHERE si.id = :invoiceId LIMIT 1
        """,
    )
    fun observeInvoiceHeader(invoiceId: Long): Flow<SalesInvoiceHeaderRow?>

    @Query(
        """
        SELECT sil.id, sil.menuItemId, sil.recipeVersionId, sil.menuItemNameSnapshot, sil.quantityMicros,
               sil.unitPriceRial, sil.grossRial, sil.discountRial, sil.serviceRial, sil.taxRial,
               sil.netRial, sil.theoreticalCostRial,
               COALESCE((SELECT SUM(srl.quantityMicros) FROM sales_return_lines srl WHERE srl.invoiceLineId = sil.id), 0) AS returnedQuantityMicros
        FROM sales_invoice_lines sil
        WHERE sil.invoiceId = :invoiceId
        ORDER BY sil.id
        """,
    )
    fun observeInvoiceLines(invoiceId: Long): Flow<List<SalesInvoiceLineRow>>

    @Query("SELECT * FROM sales_payments WHERE invoiceId = :invoiceId ORDER BY id")
    fun observePayments(invoiceId: Long): Flow<List<SalesPaymentEntity>>

    @Query(
        """
        SELECT sil.id AS invoiceLineId,
               COALESCE(SUM(srl.quantityMicros), 0) AS quantityMicros,
               COALESCE(SUM(srl.grossRial), 0) AS grossRial,
               COALESCE(SUM(srl.discountRial), 0) AS discountRial,
               COALESCE(SUM(srl.serviceRial), 0) AS serviceRial,
               COALESCE(SUM(srl.taxRial), 0) AS taxRial,
               COALESCE(SUM(srl.netRial), 0) AS netRial,
               COALESCE(SUM(srl.cogsRial), 0) AS cogsRial
        FROM sales_invoice_lines sil
        LEFT JOIN sales_return_lines srl ON srl.invoiceLineId = sil.id
        WHERE sil.invoiceId = :invoiceId
        GROUP BY sil.id
        ORDER BY sil.id
        """,
    )
    suspend fun returnedTotals(invoiceId: Long): List<SalesReturnedTotalsRow>

    @Insert suspend fun insertReturn(entity: SalesReturnEntity): Long
    @Insert suspend fun insertReturnLines(entities: List<SalesReturnLineEntity>)
    @Query("SELECT * FROM sales_return_lines WHERE returnId = :returnId ORDER BY id") suspend fun returnLines(returnId: Long): List<SalesReturnLineEntity>
    @Query("SELECT * FROM sales_returns WHERE commandId = :commandId LIMIT 1") suspend fun returnByCommandId(commandId: String): SalesReturnEntity?
    @Query("UPDATE sales_returns SET journalEntryId = :journalId, cogsJournalEntryId = :cogsJournalId WHERE id = :returnId")
    suspend fun linkReturnJournals(returnId: Long, journalId: Long, cogsJournalId: Long?): Int
    @Query("SELECT COUNT(*) FROM sales_returns WHERE invoiceId = :invoiceId") suspend fun returnCount(invoiceId: Long): Long
    @Query("SELECT COALESCE(SUM(refundRial), 0) FROM sales_returns WHERE invoiceId = :invoiceId AND refundMethod = 'CREDIT'")
    suspend fun creditRefundedRial(invoiceId: Long): Long

    @Query(
        """
        UPDATE sales_invoices SET status = :status
        WHERE id = :invoiceId AND status IN ('POSTED', 'PARTIALLY_RETURNED')
        """,
    )
    suspend fun updateReturnStatus(invoiceId: Long, status: String): Int

    @Query(
        """
        UPDATE sales_invoices
        SET status = 'VOIDED', voidedAtEpochDay = :voidEpochDay, voidCommandId = :voidCommandId, voidReason = :reason,
            voidJournalEntryId = :journalEntryId, voidCogsJournalEntryId = :cogsJournalEntryId
        WHERE id = :invoiceId AND status = 'POSTED'
        """,
    )
    suspend fun markVoided(invoiceId: Long, voidEpochDay: Long, voidCommandId: String, reason: String, journalEntryId: Long?, cogsJournalEntryId: Long?): Int

}
