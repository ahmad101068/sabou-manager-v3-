package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole

enum class DashboardKpiKind { MONEY, COUNT }
enum class DashboardAttentionSeverity { NOTICE, WARNING, CRITICAL }
enum class DashboardLoadStatus { LOADING, LOADED, EMPTY, ERROR }

data class DashboardHeaderUi(
    val organizationName: String,
    val userName: String,
    val roleTitle: String,
    val unreadAttentionCount: Int,
)

data class DashboardKpiUi(
    val id: String,
    val title: String,
    val value: Long,
    val kind: DashboardKpiKind,
    val destination: AppScreen?,
)

data class DashboardQuickActionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val destination: AppScreen,
)

data class DashboardAttentionUi(
    val id: String,
    val severity: DashboardAttentionSeverity,
    val title: String,
    val explanation: String,
    val count: Long,
    val destination: AppScreen,
)

data class DashboardMetricUi(
    val id: String,
    val title: String,
    val displayValue: String,
    val trend: MetricTrend,
    val trendText: String,
    val destination: AppScreen?,
)

data class DashboardHeroUi(
    val title: String,
    val displayValue: String,
    val trend: MetricTrend,
    val trendText: String,
    val destination: AppScreen?,
    val liquidityDisplayValue: String,
    val grossProfitDisplayValue: String,
)

data class DashboardUiState(
    val header: DashboardHeaderUi = DashboardHeaderUi("", "", "", 0),
    val period: DashboardPeriod = DashboardPeriod.TODAY,
    val labels: DashboardPeriodLabels = DashboardPeriodLabelProvider.labels(DashboardPeriod.TODAY),
    val performanceText: String = "در حال بارگذاری داده‌های داشبورد",
    val kpis: List<DashboardKpiUi> = emptyList(),
    val quickActions: List<DashboardQuickActionUi> = emptyList(),
    val alerts: List<DashboardAttentionUi> = emptyList(),
    val hero: DashboardHeroUi? = null,
    val invoices: DashboardMetricUi? = null,
    val expenses: DashboardMetricUi? = null,
    val loadStatus: DashboardLoadStatus = DashboardLoadStatus.LOADING,
    val statusMessage: String? = null,
    val partialErrors: List<String> = emptyList(),
) {
    val loading: Boolean get() = loadStatus == DashboardLoadStatus.LOADING
}

/**
 * Presentation policy for Home. The screen receives only already-authorized KPIs/actions and
 * presentation-ready values. Trend math and period-sensitive copy stay out of Composables.
 */
internal object DashboardUxComposer {
    fun compose(
        snapshot: DashboardSnapshot,
        previousSnapshot: DashboardSnapshot,
        user: AppUserRecord?,
        organizationName: String,
    ): DashboardUiState {
        val labels = DashboardPeriodLabelProvider.labels(snapshot.period)
        if (user == null) {
            return DashboardUiState(
                period = snapshot.period,
                labels = labels,
                loadStatus = if (snapshot.fromEpochDay <= 0L) DashboardLoadStatus.LOADING else DashboardLoadStatus.LOADED,
            )
        }

        val alerts = resolveAlerts(snapshot, user)
        val kpis = resolveKpis(snapshot, user).take(4)
        val hasActivity = hasPeriodActivity(snapshot)
        val salesTrend = DashboardTrendCalculator.calculate(
            currentValue = snapshot.postedInvoiceSalesRial,
            previousValue = previousSnapshot.postedInvoiceSalesRial,
        )
        val invoiceTrend = DashboardTrendCalculator.calculate(
            currentValue = snapshot.postedInvoiceCount,
            previousValue = previousSnapshot.postedInvoiceCount,
        )
        val expenseTrend = DashboardTrendCalculator.calculate(
            currentValue = snapshot.purchaseRial,
            previousValue = previousSnapshot.purchaseRial,
        )


        val emptyDisplay = "—"
        val canSales = user.role.allows(Permission.DAILY_SALES_VIEW)
        val canPurchase = user.role.allows(Permission.PURCHASES)
        val invoices = if (canSales) {
            DashboardMetricUi(
                id = "invoice_count",
                title = labels.invoicesTitle,
                displayValue = if (hasActivity) DashboardPresentationFormatter.integer(snapshot.postedInvoiceCount) else emptyDisplay,
                trend = invoiceTrend,
                trendText = if (hasActivity) DashboardPresentationFormatter.trendText(invoiceTrend, labels.comparisonSuffix) else "هنوز داده‌ای ثبت نشده",
                destination = AppScreen.SALES,
            )
        } else null
        val expenses = if (canPurchase) {
            DashboardMetricUi(
                id = "expenses",
                title = labels.expensesTitle,
                displayValue = if (hasActivity) DashboardPresentationFormatter.compactToman(snapshot.purchaseRial) else emptyDisplay,
                trend = expenseTrend,
                trendText = if (hasActivity) DashboardPresentationFormatter.trendText(expenseTrend, labels.comparisonSuffix) else "هنوز داده‌ای ثبت نشده",
                destination = AppScreen.PURCHASES,
            )
        } else null


        val hero = buildHero(snapshot, labels, kpis, salesTrend, hasActivity, canSales)
        val loadStatus = if (hasActivity) DashboardLoadStatus.LOADED else DashboardLoadStatus.EMPTY
        val performanceText = if (canSales) {
            DashboardPerformanceTextResolver.resolve(salesTrend, hasActivity)
        } else if (loadStatus == DashboardLoadStatus.EMPTY) {
            "هنوز داده‌ای برای این بازه ثبت نشده"
        } else {
            "خلاصه شاخص‌های مجاز این دوره"
        }
        return DashboardUiState(
            header = header(user, organizationName, alerts),
            period = snapshot.period,
            labels = labels,
            performanceText = performanceText,
            kpis = kpis,
            quickActions = resolveQuickActions(user).take(6),
            alerts = alerts,
            hero = hero,
            invoices = invoices,
            expenses = expenses,
            loadStatus = loadStatus,
            statusMessage = if (loadStatus == DashboardLoadStatus.EMPTY) "هنوز داده‌ای برای این بازه ثبت نشده" else null,
            partialErrors = emptyList(),
        )
    }

    /** Compatibility helper for tests/callers that do not need period comparison. */
    fun compose(snapshot: DashboardSnapshot, user: AppUserRecord?, organizationName: String): DashboardUiState =
        compose(snapshot, snapshot, user, organizationName)

    fun loading(period: DashboardPeriod, user: AppUserRecord?, organizationName: String): DashboardUiState = DashboardUiState(
        header = header(user, organizationName, emptyList()),
        period = period,
        labels = DashboardPeriodLabelProvider.labels(period),
        performanceText = "در حال بارگذاری داده‌های داشبورد",
        quickActions = user?.let { resolveQuickActions(it).take(6) }.orEmpty(),
        loadStatus = DashboardLoadStatus.LOADING,
        statusMessage = "در حال بارگذاری داده‌های داشبورد…",
    )

    fun error(period: DashboardPeriod, user: AppUserRecord?, organizationName: String): DashboardUiState = DashboardUiState(
        header = header(user, organizationName, emptyList()),
        period = period,
        labels = DashboardPeriodLabelProvider.labels(period),
        performanceText = "داده‌های داشبورد در حال حاضر در دسترس نیست",
        quickActions = user?.let { resolveQuickActions(it).take(6) }.orEmpty(),
        loadStatus = DashboardLoadStatus.ERROR,
        statusMessage = "دریافت داده‌های داشبورد با خطا مواجه شد. دوباره تلاش کنید.",
    )

    internal fun resolveKpis(snapshot: DashboardSnapshot, user: AppUserRecord): List<DashboardKpiUi> = when (user.role) {
        UserRole.OWNER, UserRole.MANAGER -> ownerKpis(snapshot, user)
        UserRole.CASHIER -> listOfNotNull(
            permitted(user, Permission.DAILY_SALES_VIEW) { money("sales", "فروش بازه", snapshot.postedInvoiceSalesRial, AppScreen.SALES) },
            permitted(user, Permission.DAILY_SALES_VIEW) { count("invoice_count", "تعداد فاکتور", snapshot.postedInvoiceCount, AppScreen.SALES) },
            money("cash", "مبلغ صندوق", snapshot.cashBalanceRial, AppScreen.SALES),
        )
        UserRole.INVENTORY, UserRole.STOREKEEPER -> listOfNotNull(
            permitted(user, Permission.INVENTORY) { count("low_stock", "کم‌موجودی", snapshot.lowStockCount, AppScreen.INVENTORY) },
            permitted(user, Permission.INVENTORY) { count("expiry", "انقضای نزدیک", snapshot.expiringLotCount, AppScreen.INVENTORY) },
            permitted(user, Permission.INVENTORY) { count("slow_stock", "کم‌گردش", snapshot.slowStockCount, AppScreen.INVENTORY) },
            permitted(user, Permission.INVENTORY) { money("waste", "ضایعات بازه", snapshot.wasteRial, AppScreen.STOCK_MOVEMENTS) },
        )
        UserRole.ACCOUNTANT -> listOfNotNull(
            permitted(user, Permission.ACCOUNTING) { money("liquidity", "نقدینگی", snapshot.cashBalanceRial + snapshot.bankBalanceRial, AppScreen.TREASURY) },
            permitted(user, Permission.ACCOUNTING) { money("payables", "بدهی تأمین‌کنندگان", snapshot.supplierPayablesRial, AppScreen.PURCHASES) },
            permitted(user, Permission.ACCOUNTING) { money("receivables", "دریافتنی مشتریان", snapshot.customerReceivablesRial, AppScreen.CRM) },
            permitted(user, Permission.PERSONNEL) { money("unpaid_payroll", "حقوق پرداخت‌نشده", snapshot.unpaidPayrollRial, AppScreen.PERSONNEL) },
        )
        UserRole.RESTRICTED -> emptyList()
    }

    private fun ownerKpis(snapshot: DashboardSnapshot, user: AppUserRecord): List<DashboardKpiUi> = listOfNotNull(
        permitted(user, Permission.DAILY_SALES_VIEW) { money("sales", "فروش بازه", snapshot.postedInvoiceSalesRial, AppScreen.SALES) },
        permitted(user, Permission.DAILY_SALES_VIEW) { money("gross_profit", "سود ناخالص بازه", snapshot.postedInvoiceGrossProfitRial, AppScreen.REPORTS) },
        permitted(user, Permission.ACCOUNTING) { money("liquidity", "نقدینگی", snapshot.cashBalanceRial + snapshot.bankBalanceRial, AppScreen.TREASURY) },
        count("critical", "هشدارهای بحرانی", criticalCount(snapshot, user), AppScreen.ALERTS),
    )

    internal fun resolveQuickActions(user: AppUserRecord): List<DashboardQuickActionUi> {
        val allowed = buildList {
            if (user.role.allows(Permission.DAILY_SALES_VIEW)) add(DashboardQuickActionUi("sale", "فروش", "ثبت و مدیریت فروش", AppScreen.SALES))
            if (user.role.allows(Permission.PURCHASES)) add(DashboardQuickActionUi("purchase", "خرید", "ثبت خرید", AppScreen.NEW_PURCHASE))
            if (user.role.allows(Permission.INVENTORY)) add(DashboardQuickActionUi("inventory", "انبار", "موجودی و گردش", AppScreen.INVENTORY))
            if (user.role.allows(Permission.TREASURY)) add(DashboardQuickActionUi("treasury", "صندوق", "دریافت و پرداخت", AppScreen.TREASURY))
            if (user.role.allows(Permission.REPORTS)) add(DashboardQuickActionUi("reports", "گزارش", "مرکز گزارش", AppScreen.REPORTS))
            if (user.role.allows(Permission.CUSTOMERS)) add(DashboardQuickActionUi("customers", "مشتریان", "ارتباط و حساب مشتری", AppScreen.CRM))
        }.take(6).toMutableList()
        if (allowed.size < 6) allowed += DashboardQuickActionUi("more", "بیشتر", "همه بخش‌های مجاز", AppScreen.MORE)
        return allowed.take(6)
    }

    internal fun resolveAlerts(snapshot: DashboardSnapshot, user: AppUserRecord): List<DashboardAttentionUi> = buildList {
        if (user.role.allows(Permission.INVENTORY) && snapshot.lowStockCount > 0) add(
            DashboardAttentionUi("low_stock", DashboardAttentionSeverity.WARNING, "کالاهای کم‌موجودی", "موجودی این اقلام نیازمند بررسی است.", snapshot.lowStockCount, AppScreen.INVENTORY),
        )
        if (user.role.allows(Permission.INVENTORY) && snapshot.expiringLotCount > 0) add(
            DashboardAttentionUi("expiry", DashboardAttentionSeverity.CRITICAL, "انقضای نزدیک", "لات‌های نزدیک به انقضا را بررسی کنید.", snapshot.expiringLotCount, AppScreen.INVENTORY),
        )
        if (user.role.allows(Permission.PERSONNEL) && snapshot.attendanceAnomalyCount > 0) add(
            DashboardAttentionUi("attendance", DashboardAttentionSeverity.WARNING, "مغایرت حضور", "رکوردهای حضور نیازمند رسیدگی هستند.", snapshot.attendanceAnomalyCount, AppScreen.PERSONNEL),
        )
        if (user.role.allows(Permission.PERSONNEL) && snapshot.unpaidPayrollRial > 0) add(
            DashboardAttentionUi("payroll", DashboardAttentionSeverity.WARNING, "حقوق پرداخت‌نشده", "پرداخت‌های حقوق باز را بررسی کنید.", 1, AppScreen.PERSONNEL),
        )
        if (user.role.allows(Permission.ASSETS) && snapshot.dueMaintenanceCount > 0) add(
            DashboardAttentionUi("maintenance", DashboardAttentionSeverity.NOTICE, "سرویس دارایی", "دارایی‌های سررسید سرویس دارند.", snapshot.dueMaintenanceCount, AppScreen.ASSETS),
        )
    }

    private fun buildHero(
        snapshot: DashboardSnapshot,
        labels: DashboardPeriodLabels,
        kpis: List<DashboardKpiUi>,
        salesTrend: MetricTrend,
        hasActivity: Boolean,
        canSales: Boolean,
    ): DashboardHeroUi? {
        val primary = kpis.firstOrNull { it.id == "sales" } ?: kpis.firstOrNull() ?: return null
        val primaryTrend = if (primary.id == "sales") salesTrend else MetricTrend(
            currentValue = primary.value,
            previousValue = primary.value,
            percentage = null,
            direction = TrendDirection.NOT_AVAILABLE,
        )
        val primaryTitle = if (primary.id == "sales") labels.salesTitle else primary.title
        val primaryValue = when {
            !hasActivity && primary.id == "sales" -> "—"
            primary.kind == DashboardKpiKind.COUNT -> DashboardPresentationFormatter.integer(primary.value)
            else -> formatMoney(primary.value, CurrencyUnit.TOMAN)
        }
        val trendText = when {
            !hasActivity && primary.id == "sales" -> "هنوز داده‌ای ثبت نشده"
            primary.id == "sales" -> DashboardPresentationFormatter.trendText(primaryTrend, labels.comparisonSuffix)
            else -> "مقایسه دوره‌ای در دسترس نیست"
        }
        val liquidity = kpis.firstOrNull { it.id == "liquidity" }
        return DashboardHeroUi(
            title = primaryTitle,
            displayValue = primaryValue,
            trend = primaryTrend,
            trendText = trendText,
            destination = primary.destination,
            liquidityDisplayValue = liquidity?.let { DashboardPresentationFormatter.compactToman(it.value) } ?: "—",
            // Home renders canonical Gross Profit from DailyManagementBrief, never this dashboard aggregate.
            grossProfitDisplayValue = "—",
        )
    }

    private fun hasPeriodActivity(snapshot: DashboardSnapshot): Boolean = listOf(
        snapshot.postedInvoiceCount,
        snapshot.postedInvoiceReturnCount,
        snapshot.postedInvoiceSalesRial,
        snapshot.grossSalesRial,
        snapshot.salesReturnRial,
        snapshot.purchaseRial,
        snapshot.purchaseReturnRial,
        snapshot.wasteRial,
    ).any { it != 0L }

    private fun header(
        user: AppUserRecord?,
        organizationName: String,
        alerts: List<DashboardAttentionUi>,
    ): DashboardHeaderUi = DashboardHeaderUi(
        organizationName = organizationName,
        userName = user?.displayName.orEmpty(),
        roleTitle = user?.role?.title.orEmpty(),
        unreadAttentionCount = alerts.sumOf { it.count }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )

    private fun criticalCount(snapshot: DashboardSnapshot, user: AppUserRecord): Long = resolveAlerts(snapshot, user)
        .filter { it.severity == DashboardAttentionSeverity.CRITICAL || it.severity == DashboardAttentionSeverity.WARNING }
        .sumOf { it.count }

    private inline fun <T> permitted(user: AppUserRecord, permission: Permission, block: () -> T): T? =
        if (user.role.allows(permission)) block() else null

    private fun money(id: String, title: String, value: Long, destination: AppScreen?) =
        DashboardKpiUi(id, title, value, DashboardKpiKind.MONEY, destination)

    private fun count(id: String, title: String, value: Long, destination: AppScreen?) =
        DashboardKpiUi(id, title, value, DashboardKpiKind.COUNT, destination)
}
