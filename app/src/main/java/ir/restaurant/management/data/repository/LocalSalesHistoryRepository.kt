package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.sales.CustomerRecord
import ir.restaurant.management.domain.sales.PartyFinancialType
import ir.restaurant.management.domain.sales.HistoricalSalesDayClosureRecord
import ir.restaurant.management.domain.sales.SalesDashboardSummary
import ir.restaurant.management.domain.sales.SalesHistoryRepository
import ir.restaurant.management.domain.sales.SalesInvoiceDetails
import ir.restaurant.management.domain.sales.SalesInvoiceLineRecord
import ir.restaurant.management.domain.sales.SalesInvoiceRecord
import ir.restaurant.management.domain.sales.SalesInvoiceStatus
import ir.restaurant.management.domain.sales.SalesPaymentMethod
import ir.restaurant.management.domain.sales.SalesPaymentRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Read-only boundary for historical posted invoice facts plus customer-master maintenance.
 * New order-level sales cannot be created through this repository.
 */
class LocalSalesHistoryRepository(
    private val database: AppDatabase,
    authorizer: SessionAuthorizer,
    syncRecorder: SyncRecorder? = null,
    clock: () -> Long = System::currentTimeMillis,
) : SalesHistoryRepository {
    private val customerMaster = SalesCustomerMasterService(database, authorizer, syncRecorder, clock)

    override val customers: Flow<List<CustomerRecord>> = database.salesDao().observeCustomers().map { rows ->
        rows.map { row ->
            CustomerRecord(
                id = row.id,
                customerCode = row.customerCode,
                name = row.name,
                phone = row.phone,
                nationalId = row.nationalId,
                creditLimitRial = row.creditLimitRial,
                outstandingRial = row.outstandingRial,
                notes = row.notes,
                isActive = row.isActive,
                mobile = row.mobile,
                address = row.address,
                branch = row.branch,
                paymentTermsDays = row.paymentTermsDays,
                status = row.status,
                partyType = PartyFinancialType.valueOf(row.partyType),
            )
        }
    }

    override val dayClosures: Flow<List<HistoricalSalesDayClosureRecord>> =
        database.salesDao().observeSalesDayClosures().map { rows ->
            rows.map { row ->
                HistoricalSalesDayClosureRecord(
                    row.businessEpochDay, row.netSalesRial, row.cogsRial, row.cashRial, row.cardRial,
                    row.transferRial, row.creditRial, row.invoiceCount, row.returnCount, row.status,
                    row.revisionNo, row.closedByName, row.note, row.reopenReason,
                )
            }
        }

    override fun observeInvoices(query: String): Flow<List<SalesInvoiceRecord>> =
        database.salesDao().observeInvoices(query.trim()).map { rows -> rows.map { it.toRecord() } }

    override fun observeRecentInvoices(limit: Int): Flow<List<SalesInvoiceRecord>> {
        require(limit in 1..20) { "تعداد فاکتورهای اخیر معتبر نیست." }
        return database.salesDao().observeRecentInvoices(limit).map { rows -> rows.map { it.toRecord() } }
    }

    override fun observeDashboardSummary(
        fromEpochDay: Long,
        toEpochDay: Long,
        createdFromEpochMillis: Long,
        createdToEpochMillisExclusive: Long,
    ): Flow<SalesDashboardSummary> {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay) { "بازه داشبورد فروش معتبر نیست." }
        require(createdFromEpochMillis >= 0 && createdToEpochMillisExclusive > createdFromEpochMillis) { "بازه زمانی مشتریان معتبر نیست." }
        return database.salesDao().observeDashboardSummary(
            fromEpochDay, toEpochDay, createdFromEpochMillis, createdToEpochMillisExclusive,
        ).map { row ->
            SalesDashboardSummary(
                netSalesRial = row.netSalesRial,
                invoiceNetRial = row.invoiceNetRial,
                invoiceCount = row.invoiceCount,
                returnRial = row.returnRial,
                newCustomerCount = row.newCustomerCount,
                customerReceivablesRial = row.customerReceivablesRial,
            )
        }
    }

    override fun observeDetails(invoiceId: Long): Flow<SalesInvoiceDetails?> = combine(
        database.salesDao().observeInvoiceHeader(invoiceId),
        database.salesDao().observeInvoiceLines(invoiceId),
        database.salesDao().observePayments(invoiceId),
    ) { header, lines, payments ->
        header?.let { h ->
            SalesInvoiceDetails(
                invoice = SalesInvoiceRecord(
                    id = h.id, invoiceNo = h.invoiceNo, businessEpochDay = h.businessEpochDay,
                    customerName = h.customerName, grossRial = h.grossRial, discountRial = h.discountRial,
                    serviceRial = h.serviceRial, taxRial = h.taxRial, netRial = h.netRial,
                    theoreticalCostRial = h.theoreticalCostRial, status = SalesInvoiceStatus.fromStoredValue(h.status),
                    notes = h.notes, createdAtEpochMillis = h.createdAtEpochMillis,
                ),
                customerId = h.customerId,
                dueEpochDay = h.dueEpochDay,
                lines = lines.map { row ->
                    SalesInvoiceLineRecord(
                        id = row.id, menuItemId = row.menuItemId, recipeVersionId = row.recipeVersionId,
                        name = row.menuItemNameSnapshot, quantityMicros = row.quantityMicros, unitPriceRial = row.unitPriceRial,
                        grossRial = row.grossRial, discountRial = row.discountRial, serviceRial = row.serviceRial,
                        taxRial = row.taxRial, netRial = row.netRial, theoreticalCostRial = row.theoreticalCostRial,
                        returnedQuantityMicros = row.returnedQuantityMicros,
                    )
                },
                payments = payments.map { payment ->
                    SalesPaymentRecord(payment.method.toPaymentMethod(), payment.amountRial, payment.referenceNo)
                },
            )
        }
    }

    override suspend fun saveCustomer(id: Long?, draft: CustomerDraft): Long = customerMaster.save(id, draft)

    override suspend fun deactivateCustomer(id: Long) = customerMaster.deactivate(id)

    private fun String.toPaymentMethod(): SalesPaymentMethod =
        SalesPaymentMethod.entries.firstOrNull { it.name == this } ?: error("روش پرداخت ناشناخته: $this")

    private fun ir.restaurant.management.data.db.SalesInvoiceListRow.toRecord() = SalesInvoiceRecord(
        id = id, invoiceNo = invoiceNo, businessEpochDay = businessEpochDay, customerName = customerName,
        grossRial = grossRial, discountRial = discountRial, serviceRial = serviceRial, taxRial = taxRial,
        netRial = netRial, theoreticalCostRial = theoreticalCostRial, status = SalesInvoiceStatus.fromStoredValue(status),
        notes = notes, createdAtEpochMillis = createdAtEpochMillis,
    )
}
