package ir.restaurant.management.ui

import ir.restaurant.management.organizationDisplayTitle

import ir.restaurant.management.domain.personnel.PayrollStatus
import ir.restaurant.management.domain.recipe.MenuQuadrant
import ir.restaurant.management.core.FixedPointRatio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.accounting.CashFlowCalculator
import ir.restaurant.management.domain.accounting.CashFlowEvent
import ir.restaurant.management.domain.operations.ReorderInput
import ir.restaurant.management.domain.operations.SmartReorderCalculator
import ir.restaurant.management.domain.operations.RestaurantKpiCalculator

private data class ReportDestinationUi(
    val key: String,
    val group: String,
    val title: String,
    val description: String,
    val action: String,
    val open: () -> Unit,
)

@Composable
fun ReportsCenterScreen(
    organizationName: String,
    dashboard: DashboardSnapshot,
    sales: DailySalesUiState,
    accounting: AccountingUiState,
    operations: OperationsUiState,
    personnel: PersonnelUiState,
    assets: AssetUiState,
    onOpenSales: () -> Unit,
    onOpenAccounting: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenStockMovements: () -> Unit,
    onOpenPersonnel: () -> Unit,
    onSetReportRange: (Long, Long) -> Unit,
    navigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showRange by remember { mutableStateOf(false) }
    var reportSearch by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favoriteReports by remember { mutableStateOf(setOf<String>()) }
    var recentReports by remember { mutableStateOf(listOf<String>()) }
    val reportDestinations = listOf(
        ReportDestinationUi("sales", "فروش", "فروش و درآمد", "فروش روزانه، بهای تمام‌شده و سود ناخالص", "مشاهده فروش", onOpenSales),
        ReportDestinationUi("purchase", "خرید", "خرید و تأمین", "سفارش‌ها، دریافت‌ها و وضعیت تأمین", "مشاهده خرید", { navigateTopLevel(AppScreen.PURCHASES) }),
        ReportDestinationUi("inventory", "انبار", "موجودی و تأمین", "ارزش انبار، کمبودها و اصلاح موجودی", "مشاهده انبار", onOpenInventory),
        ReportDestinationUi("movements", "انبار", "دفتر گردش موجودی", "ورود، خروج، مصرف، ضایعات و اصلاحات", "ردیابی گردش‌ها", onOpenStockMovements),
        ReportDestinationUi("accounting", "مالی", "حسابداری و تراز", "سود و زیان، تراز آزمایشی و اسناد", "ورود به حسابداری", onOpenAccounting),
        ReportDestinationUi("customers", "مشتری", "مشتریان و دریافتنی", "مانده مشتریان و گردش حساب", "مشاهده مشتریان", { navigateTopLevel(AppScreen.CRM) }),
        ReportDestinationUi("personnel", "پرسنل", "پرسنل و حقوق", "هزینه حقوق، مساعده، حضور و سوابق فردی", "مشاهده پرسنل", onOpenPersonnel),
        ReportDestinationUi("assets", "دارایی", "دارایی‌های ثابت", "ارزش دفتری، استهلاک و وضعیت دارایی", "مشاهده دارایی", { navigateTopLevel(AppScreen.ASSETS) }),
    )
    val normalizedSearch = reportSearch.trim()
    val visibleReports = reportDestinations.filter { report ->
        val matchesSearch = normalizedSearch.isEmpty() || listOf(report.title, report.description, report.group).any { it.contains(normalizedSearch, ignoreCase = true) }
        val matchesFavorite = !favoritesOnly || report.key in favoriteReports
        matchesSearch && matchesFavorite
    }
    val netProfit = accounting.profitLoss.netProfitRial
    val liquidity = dashboard.cashBalanceRial + dashboard.bankBalanceRial
    val lowStock = operations.lowStockItems.size
    val openSettlements = operations.settlementAlerts.size
    val salesReport = sales.report
    val kpi = RestaurantKpiCalculator.calculate(
        salesRial = (salesReport?.salesRial ?: 0).coerceAtLeast(0),
        costRial = (salesReport?.costOfGoodsRial ?: 0).coerceAtLeast(0),
        invoiceCount = salesReport?.dayCount ?: 0,
    )
    val cashFlow = CashFlowCalculator.forecast(
        startBalanceRial = liquidity.coerceAtLeast(0),
        events = operations.purchases.filter { it.outstandingRial > 0 }.map {
            CashFlowEvent(it.dueEpochDay, it.outstandingRial, "بدهی ${it.invoiceNo}", incoming = false)
        },
    )
    val reorders = operations.inventoryItems.map { item ->
        val usage = operations.usageInsights.firstOrNull { it.itemId == item.id }
        SmartReorderCalculator.recommend(ReorderInput(
            itemId = item.id, itemName = item.name, unit = item.unit,
            currentStockMicros = item.stockMicros,
            averageDailyUsageMicros = usage?.averageDailyUsageMicros ?: 0,
            minimumStockMicros = item.alertThresholdMicros,
        ))
    }.filter { it.recommendedOrderMicros > 0 }.sortedByDescending { it.urgency.ordinal }
    val payrollCost = personnel.payrolls
        .filter { it.paymentEpochDay in sales.reportFromEpochDay..sales.reportToEpochDay && it.status != PayrollStatus.REVERSED }
        .sumOf { it.netPayRial }
    val laborCostPercent = if (kpi.salesRial <= 0L) 0 else FixedPointRatio
        .multiplyDivide(payrollCost, 100L, kpi.salesRial)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val activeAssetBookValue = assets.assets.filter { it.isActive }.sumOf { it.bookValueRial }
    val outstandingAdvances = personnel.openAdvances.sumOf { it.remainingAmountRial }
    Scaffold(
        topBar = { ProfessionalTopBar("مرکز گزارش‌ها", organizationDisplayTitle(organizationName), onBack) },
        bottomBar = { MainTopLevelNavigation(selected = AppScreen.REPORTS, navigate = navigateTopLevel) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PremiumHero("سود خالص بازه", formatMoney(netProfit), "نقدینگی در دسترس ${formatMoney(liquidity)}") }
            item {
                OutlinedButton(onClick = { showRange = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("بازه گزارش: ${epochDayToPersian(sales.reportFromEpochDay).display()} تا ${epochDayToPersian(sales.reportToEpochDay).display()}")
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricTile("فروش فعال", formatMoney(sales.activeSalesRial), Modifier.weight(1f)); MetricTile("سود ناخالص", formatMoney(sales.grossProfitRial), Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricTile("روزهای رویداد", ErpDisplayFormatters.integer(salesReport?.dayCount ?: 0), Modifier.weight(1f)); MetricTile("ارزش انبار", formatMoney(dashboard.inventoryValueRial), Modifier.weight(1f)) } }
            item {
                OutlinedButton(
                    onClick = {
                        printManagementSummary(
                            context = context,
                            netProfitRial = netProfit,
                            liquidityRial = liquidity,
                            salesRial = sales.activeSalesRial,
                            grossProfitRial = sales.grossProfitRial,
                            receivablesRial = sales.receivablesRial,
                            inventoryRial = dashboard.inventoryValueRial,
                            lowStockCount = lowStock,
                            overdueCount = openSettlements,
                            fromEpochDay = sales.reportFromEpochDay,
                            toEpochDay = sales.reportToEpochDay,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("چاپ گزارش مدیریتی / PDF") }
            }
            item { SectionHeading("شاخص‌های فروش و منو", "محاسبه زنده از آمار روزانه صندوق و رسپی‌ها") }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricTile("حاشیه سود", "${toPersianDigits(kpi.marginPercent.toString())}٪", Modifier.weight(1f)); MetricTile("روزهای رویداد", ErpDisplayFormatters.integer(kpi.invoiceCount), Modifier.weight(1f)) } }
            item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("پیش‌بینی جریان نقدی", fontWeight = FontWeight.ExtraBold)
                ReportAlertRow("مانده پایان دوره", formatMoney(cashFlow.endBalanceRial), cashFlow.endBalanceRial < 0)
                ReportAlertRow("کمترین مانده", formatMoney(cashFlow.minimumBalanceRial), cashFlow.minimumBalanceRial < 0)
                ReportAlertRow("روزهای کسری", "${ErpDisplayFormatters.integer(cashFlow.deficitDays.size)} روز", cashFlow.deficitDays.isNotEmpty())
            } } }
            sales.report?.menuPerformance?.takeIf { it.isNotEmpty() }?.let { menu -> item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("مهندسی منو", fontWeight = FontWeight.ExtraBold)
                menu.take(8).forEach { result -> ReportAlertRow(result.name, "${menuQuadrantTitle(result.quadrant)} · ${formatMoney(result.grossProfitRial)}", result.grossProfitRial < 0) }
            } } } }
            if (reorders.isNotEmpty()) item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("پیشنهاد سفارش مجدد بر اساس مصرف ۳۰روزه", fontWeight = FontWeight.ExtraBold)
                reorders.take(8).forEach { result -> ReportAlertRow(result.itemName, "${formatQuantity(result.recommendedOrderMicros)} ${result.unit}", true) }
            } } }
            operations.supplierPriceInsights.filter { it.changePercent >= 5 }.takeIf { it.isNotEmpty() }?.let { changes -> item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("هشدار افزایش قیمت خرید", fontWeight = FontWeight.ExtraBold)
                changes.sortedByDescending { it.changePercent }.take(8).forEach { result -> ReportAlertRow(result.itemName, "+${toPersianDigits(result.changePercent.toString())}٪ · ${result.supplierName}", true) }
            } } } }
            operations.wasteRecords.takeIf { it.isNotEmpty() }?.let { wastes -> item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("کنترل ضایعات", fontWeight = FontWeight.ExtraBold)
                ReportAlertRow("ارزش کل ضایعات ثبت‌شده", formatMoney(wastes.sumOf { it.valueRial }), wastes.sumOf { it.valueRial } > 0)
                wastes.take(5).forEach { waste -> ReportAlertRow(waste.itemName, "${formatQuantity(waste.quantityMicros)} ${waste.unit}", true) }
            } } } }
            item { SectionHeading("نیروی انسانی و دارایی", "کنترل هزینه کار و ظرفیت عملیاتی") }
            item { Card(shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ReportAlertRow("نسبت حقوق ثبت‌شده به فروش", "${toPersianDigits(laborCostPercent.toString())}٪", laborCostPercent > 35)
                ReportAlertRow("مرخصی در انتظار بررسی", "${ErpDisplayFormatters.integer(personnel.pendingLeaves.size)} درخواست", personnel.pendingLeaves.isNotEmpty())
                ReportAlertRow("مانده مساعده پرسنل", formatMoney(outstandingAdvances), outstandingAdvances > 0)
                ReportAlertRow("ارزش دفتری دارایی‌های فعال", formatMoney(activeAssetBookValue), false)
            } } }
            item { SectionHeading("نیازمند توجه", "مواردی که بهتر است امروز بررسی شوند") }
            item { Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) { ReportAlertRow("کالاهای کم‌موجود", "${ErpDisplayFormatters.integer(lowStock)} مورد", lowStock > 0); ReportAlertRow("تسویه‌های سررسید", "${ErpDisplayFormatters.integer(openSettlements)} مورد", openSettlements > 0); ReportAlertRow("بدهی تأمین‌کنندگان", formatMoney(dashboard.supplierPayablesRial), dashboard.supplierPayablesRial > 0) } } }
            item { SectionHeading("گزارش‌های تفصیلی", "جست‌وجو و دسترسی سریع به گزارش‌های هر حوزه") }
            item {
                OutlinedTextField(
                    value = reportSearch,
                    onValueChange = { reportSearch = it.take(80) },
                    label = { Text("جست‌وجوی گزارش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = !favoritesOnly },
                    label = { Text("فقط گزارش‌های منتخب") },
                )
            }
            if (recentReports.isNotEmpty()) {
                item { SectionHeading("گزارش‌های اخیر", "آخرین گزارش‌هایی که در این نشست باز کرده‌اید") }
                recentReports.mapNotNull { key -> reportDestinations.firstOrNull { it.key == key } }.forEach { report ->
                    item {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(report.title, fontWeight = FontWeight.SemiBold)
                                    Text(report.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = report.open) { Text("بازکردن") }
                            }
                        }
                    }
                }
            }
            if (visibleReports.isEmpty()) {
                item { EmptyStatePanel("گزارشی پیدا نشد", "عبارت جست‌وجو یا فیلتر منتخب‌ها را تغییر دهید.") }
            } else {
                visibleReports.forEach { report ->
                    item {
                        ReportDestinationCard(
                            title = report.title,
                            group = report.group,
                            description = report.description,
                            action = report.action,
                            favorite = report.key in favoriteReports,
                            onFavorite = {
                                favoriteReports = if (report.key in favoriteReports) favoriteReports - report.key else favoriteReports + report.key
                            },
                            onClick = {
                                recentReports = (listOf(report.key) + recentReports.filterNot { it == report.key }).take(5)
                                report.open()
                            },
                        )
                    }
                }
            }
        }
    }
    if (showRange) {
        ReportsRangeDialog(
            initialFrom = sales.reportFromEpochDay,
            initialTo = sales.reportToEpochDay,
            onDismiss = { showRange = false },
            onApply = { from, to ->
                onSetReportRange(from, to)
                showRange = false
            },
        )
    }
}

@Composable
private fun ReportsRangeDialog(
    initialFrom: Long,
    initialTo: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    var from by remember(initialFrom) { mutableLongStateOf(initialFrom) }
    var to by remember(initialTo) { mutableLongStateOf(initialTo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازه یکپارچه گزارش‌ها") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("فروش، سود ناخالص، سود خالص و نسبت هزینه حقوق با همین بازه محاسبه می‌شوند.")
                PersianDateField("از تاریخ", from, { from = it })
                PersianDateField("تا تاریخ", to, { to = it })
            }
        },
        confirmButton = {
            Button(onClick = { if (from > 0 && to >= from) onApply(from, to) }) { Text("اعمال بازه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun menuQuadrantTitle(value: MenuQuadrant): String = when (value) {
    MenuQuadrant.STAR -> "ستاره"
    MenuQuadrant.PLOWHORSE -> "پرفروش کم‌حاشیه"
    MenuQuadrant.PUZZLE -> "پرسود کم‌فروش"
    MenuQuadrant.DOG -> "کم‌فروش کم‌حاشیه"
}

@Composable
private fun ReportAlertRow(title: String, value: String, needsAttention: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.SemiBold)
        StatusPill(
            text = value,
            containerColor = if (needsAttention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (needsAttention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReportDestinationCard(
    title: String,
    group: String,
    description: String,
    action: String,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onFavorite) { Text(if (favorite) "★ منتخب" else "☆ افزودن") }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
