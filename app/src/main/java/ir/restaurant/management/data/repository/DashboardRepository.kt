package ir.restaurant.management.data.repository

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

enum class DashboardPeriod { TODAY, WEEK, MONTH, CUSTOM }

data class DashboardBranchOption(
    val id: Long,
    val name: String,
    val isActive: Boolean,
)

data class DashboardWarehouseOption(
    val id: Long,
    val name: String,
)

data class DashboardSnapshot(
    val fromEpochDay: Long = 0,
    val toEpochDay: Long = 0,
    val period: DashboardPeriod = DashboardPeriod.TODAY,
    val selectedBranchId: Long? = null,
    val selectedBranchName: String? = null,
    val selectedWarehouseLocationId: Long? = null,
    val availableBranches: List<DashboardBranchOption> = emptyList(),
    val availableWarehouses: List<DashboardWarehouseOption> = emptyList(),
    val inventoryValueRial: Long = 0,
    val supplierPayablesRial: Long = 0,
    val cashBalanceRial: Long = 0,
    val bankBalanceRial: Long = 0,
    val postedInvoiceCount: Long = 0,
    val postedInvoiceReturnCount: Long = 0,
    val postedInvoiceSalesRial: Long = 0,
    val postedInvoiceGrossProfitRial: Long = 0,
    val customerReceivablesRial: Long = 0,
    val grossSalesRial: Long = 0,
    val discountRial: Long = 0,
    val taxRial: Long = 0,
    val serviceRial: Long = 0,
    val salesReturnRial: Long = 0,
    val cogsRial: Long = 0,
    val purchaseRial: Long = 0,
    val purchaseReturnRial: Long = 0,
    val lowStockCount: Long = 0,
    val expiringLotCount: Long = 0,
    val wasteRial: Long = 0,
    val slowStockCount: Long = 0,
    val presentCount: Long = 0,
    val absentCount: Long = 0,
    val attendanceAnomalyCount: Long = 0,
    val unpaidPayrollRial: Long = 0,
    val assetBookValueRial: Long = 0,
    val accumulatedDepreciationRial: Long = 0,
    val dueMaintenanceCount: Long = 0,
)

class DashboardRepository(private val database: AppDatabase) {
    fun observeRange(
        fromEpochDay: Long,
        toEpochDay: Long,
        period: DashboardPeriod,
        branchId: Long? = null,
        warehouseLocationId: Long? = null,
    ): Flow<DashboardSnapshot> {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay)
        require(branchId == null || branchId > 0)
        require(warehouseLocationId == null || warehouseLocationId > 0)

        val branchContext = database.branchDao().observeAll().map { branches ->
            val selected = branchId?.let { id ->
                requireNotNull(branches.firstOrNull { it.id == id }) { "dashboard_branch_not_found" }
            }
            DashboardBranchContext(
                selectedBranchId = selected?.id,
                selectedBranchName = selected?.name,
                availableBranches = branches.filter { it.isActive }.map {
                    DashboardBranchOption(id = it.id, name = it.name, isActive = it.isActive)
                },
            )
        }.distinctUntilChanged()

        return branchContext.flatMapLatest { branch ->
            // Canonical branchId is retained end-to-end. Financial dashboard reads are based on
            // canonical Daily Sales/AR/Treasury sources; legacy display names are never identity.
            val analytics = database.dashboardAnalyticsDao().observeRange(
                fromEpochDay = fromEpochDay,
                toEpochDay = toEpochDay,
                branchId = branch.selectedBranchId,
                warehouseLocationId = warehouseLocationId,
            )
            combine(analytics, database.inventoryLocationDao().observeAll()) { row, warehouses ->
                DashboardSnapshot(
                    fromEpochDay = fromEpochDay,
                    toEpochDay = toEpochDay,
                    period = period,
                    selectedBranchId = branch.selectedBranchId,
                    selectedBranchName = branch.selectedBranchName,
                    selectedWarehouseLocationId = warehouseLocationId,
                    availableBranches = branch.availableBranches,
                    availableWarehouses = warehouses.filter { it.isActive }.map { DashboardWarehouseOption(it.id, it.name) },
                    inventoryValueRial = row.inventoryValueRial,
                    supplierPayablesRial = row.supplierPayablesRial,
                    cashBalanceRial = row.cashBalanceRial,
                    bankBalanceRial = row.bankBalanceRial,
                    postedInvoiceCount = row.invoiceCount,
                    postedInvoiceReturnCount = row.returnCount,
                    postedInvoiceSalesRial = row.netSalesRial,
                    postedInvoiceGrossProfitRial = SignedLongMath.subtract(row.netSalesRial, row.cogsRial),
                    customerReceivablesRial = row.customerReceivablesRial,
                    grossSalesRial = row.grossSalesRial,
                    discountRial = row.discountRial,
                    taxRial = row.taxRial,
                    serviceRial = row.serviceRial,
                    salesReturnRial = row.salesReturnRial,
                    cogsRial = row.cogsRial,
                    purchaseRial = row.purchaseRial,
                    purchaseReturnRial = row.purchaseReturnRial,
                    lowStockCount = row.lowStockCount,
                    expiringLotCount = row.expiringLotCount,
                    wasteRial = row.wasteRial,
                    slowStockCount = row.slowStockCount,
                    presentCount = row.presentCount,
                    absentCount = row.absentCount,
                    attendanceAnomalyCount = row.attendanceAnomalyCount,
                    unpaidPayrollRial = row.unpaidPayrollRial,
                    assetBookValueRial = row.assetBookValueRial,
                    accumulatedDepreciationRial = row.accumulatedDepreciationRial,
                    dueMaintenanceCount = row.dueMaintenanceCount,
                )
            }
        }
    }

    private data class DashboardBranchContext(
        val selectedBranchId: Long?,
        val selectedBranchName: String?,
        val availableBranches: List<DashboardBranchOption>,
    )
}
