package ir.restaurant.management.application.sales

import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.sales.CustomerRecord
import ir.restaurant.management.domain.sales.HistoricalSalesDayClosureRecord
import ir.restaurant.management.domain.sales.SalesDashboardSummary
import ir.restaurant.management.domain.sales.SalesHistoryRepository
import ir.restaurant.management.domain.sales.SalesInvoiceDetails
import ir.restaurant.management.domain.sales.SalesInvoiceRecord
import kotlinx.coroutines.flow.Flow

/** Read/customer-management boundary for historical posted sales facts. It cannot create order-level sales. */
class SalesHistoryUseCases(
    private val sales: SalesHistoryRepository,
) {
    val customers: Flow<List<CustomerRecord>> get() = sales.customers
    val dayClosures: Flow<List<HistoricalSalesDayClosureRecord>> get() = sales.dayClosures

    fun invoices(query: String): Flow<List<SalesInvoiceRecord>> = sales.observeInvoices(query.trim())
    fun recentInvoices(limit: Int = 5): Flow<List<SalesInvoiceRecord>> = sales.observeRecentInvoices(limit)
    fun dashboardSummary(
        fromEpochDay: Long,
        toEpochDay: Long,
        createdFromEpochMillis: Long,
        createdToEpochMillisExclusive: Long,
    ): Flow<SalesDashboardSummary> = sales.observeDashboardSummary(
        fromEpochDay, toEpochDay, createdFromEpochMillis, createdToEpochMillisExclusive,
    )
    fun invoiceDetails(invoiceId: Long): Flow<SalesInvoiceDetails?> = sales.observeDetails(invoiceId)

    suspend fun saveCustomer(id: Long?, draft: CustomerDraft): Long = sales.saveCustomer(id, draft)
    suspend fun deactivateCustomer(id: Long) = sales.deactivateCustomer(id)
}
