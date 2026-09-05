package ir.restaurant.management.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.data.repository.DashboardSnapshot

@Composable
internal fun DashboardScreen(
    state: DashboardSnapshot,
    home: DashboardUiState,
    managementOverview: HomeManagementOverviewUiState,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onBranchSelected: (Long?) -> Unit,
    onWarehouse: (Long?) -> Unit,
    onOpen: (AppScreen) -> Unit,
) {
    var showCustomRange by remember { mutableStateOf(false) }
    val windowClass = currentErpWindowClass()
    if (showCustomRange) {
        var from by remember(state.fromEpochDay) { mutableStateOf(state.fromEpochDay.coerceAtLeast(1L)) }
        var to by remember(state.toEpochDay) { mutableStateOf(state.toEpochDay.coerceAtLeast(from)) }
        AlertDialog(
            onDismissRequest = { showCustomRange = false },
            title = { Text("بازه دلخواه داشبورد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersianDateField("از تاریخ", from) { from = it; if (to < from) to = from }
                    PersianDateField("تا تاریخ", to) { to = it }
                }
            },
            confirmButton = { Button(onClick = { onCustomRange(from, to); showCustomRange = false }) { Text("اعمال") } },
            dismissButton = { TextButton(onClick = { showCustomRange = false }) { Text("انصراف") } },
        )
    }

    Scaffold(
        containerColor = ErpPalette.Canvas,
        bottomBar = { ErpBottomNavigation(AppScreen.DASHBOARD, onOpen) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("home_dashboard"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                HomeHeader(
                    home = home,
                    state = state,
                    onSearch = onSearch,
                    onSettings = onSettings,
                    onAlerts = { onOpen(AppScreen.ALERTS) },
                    onToday = onToday,
                    onWeek = onWeek,
                    onMonth = onMonth,
                    onCustom = { showCustomRange = true },
                    onBranchSelected = onBranchSelected,
                    onWarehouse = onWarehouse,
                )
            }
            if (state.selectedBranchId == null) {
                item { HomeBranchRequiredState(state, onBranchSelected) }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().testTag("home_kpi_section")) {
                        SalesHeroCard(home, managementOverview, onOpen)
                    }
                }
                item { HomeRevenueTrendCard(managementOverview) }
                item { HomeManagementOverview(managementOverview, onOpen) }
            }
            if (windowClass == ErpWindowClass.COMPACT) {
                item { HomeAttentionCenter(home.alerts, home.partialErrors, onOpen) }
                item { HomeQuickActions(home.quickActions, onOpen) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1.35f)) { HomeAttentionCenter(home.alerts, home.partialErrors, onOpen) }
                        Box(Modifier.weight(1f)) { HomeQuickActions(home.quickActions, onOpen) }
                    }
                }
            }
            item { TodayOverview(home, onOpen) }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun HomeHeader(
    home: DashboardUiState,
    state: DashboardSnapshot,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAlerts: () -> Unit,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
    onCustom: () -> Unit,
    onBranchSelected: (Long?) -> Unit,
    onWarehouse: (Long?) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("home_header"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ManagementGeometricLogo()
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    home.header.organizationName.ifBlank { "مدیریت رستوران" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = ErpPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "سامانه مدیریت و کنترل",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErpPalette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onSearch,
                modifier = Modifier.size(42.dp).testTag("home_search").semantics { contentDescription = "جست‌وجوی سراسری" },
            ) { Icon(Icons.Outlined.Search, contentDescription = null, tint = ErpPalette.Ink) }
            Box {
                IconButton(
                    onClick = onAlerts,
                    modifier = Modifier.size(42.dp).testTag("home_notifications").semantics { contentDescription = "مرکز اعلان‌ها" },
                ) { Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = ErpPalette.Ink) }
                if (home.header.unreadAttentionCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(17.dp)
                            .background(ErpPalette.Red, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            toPersianDigits(home.header.unreadAttentionCount.coerceAtMost(9).toString()),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Surface(
                onClick = onSettings,
                modifier = Modifier.size(42.dp).testTag("home_profile").semantics { contentDescription = "پروفایل و تنظیمات" },
                shape = CircleShape,
                color = ErpPalette.IndigoSoft,
            ) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = ErpPalette.Indigo, modifier = Modifier.padding(9.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val role = home.header.roleTitle.ifBlank { "مدیر شعبه" }
            Text("عصر بخیر، $role", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ErpPalette.Ink)
            Text(home.performanceText, style = MaterialTheme.typography.bodyMedium, color = ErpPalette.Muted)
        }

        DashboardContextFilters(
            state = state,
            selectedPeriod = home.period,
            onToday = onToday,
            onWeek = onWeek,
            onMonth = onMonth,
            onCustom = onCustom,
            onBranchSelected = onBranchSelected,
            onWarehouse = onWarehouse,
        )
    }
}

@Composable
private fun DashboardContextFilters(
    state: DashboardSnapshot,
    selectedPeriod: DashboardPeriod,
    onToday: () -> Unit,
    onWeek: () -> Unit,
    onMonth: () -> Unit,
    onCustom: () -> Unit,
    onBranchSelected: (Long?) -> Unit,
    onWarehouse: (Long?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        item { FilterChip(selectedPeriod == DashboardPeriod.TODAY, onToday, { Text("امروز") }, modifier = Modifier.testTag("home_period_today")) }
        item { FilterChip(selectedPeriod == DashboardPeriod.WEEK, onWeek, { Text("هفته") }) }
        item { FilterChip(selectedPeriod == DashboardPeriod.MONTH, onMonth, { Text("ماه") }) }
        item { FilterChip(selectedPeriod == DashboardPeriod.CUSTOM, onCustom, { Text("دلخواه") }) }
        if (state.availableBranches.size > 1) {
            item { FilterChip(state.selectedBranchId == null, { onBranchSelected(null) }, { Text("همه شعب") }) }
            items(state.availableBranches, key = { "branch_${it.id}" }) { branch ->
                FilterChip(state.selectedBranchId == branch.id, { onBranchSelected(branch.id) }, { Text(branch.name) })
            }
        }
        if (state.availableWarehouses.size > 1) {
            item { FilterChip(state.selectedWarehouseLocationId == null, { onWarehouse(null) }, { Text("همه انبارها") }) }
            items(state.availableWarehouses, key = { "warehouse_${it.id}" }) { warehouse ->
                FilterChip(state.selectedWarehouseLocationId == warehouse.id, { onWarehouse(warehouse.id) }, { Text(warehouse.name) })
            }
        }
    }
}

@Composable
private fun HomeBranchRequiredState(
    state: DashboardSnapshot,
    onBranchSelected: (Long?) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_no_branch_state"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("انتخاب شعبه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                if (state.availableBranches.isEmpty()) "هنوز شعبه فعالی تعریف نشده است."
                else "برای مشاهده شاخص‌های مدیریتی، ابتدا یک شعبه فعال انتخاب کنید.",
                color = ErpPalette.Muted,
            )
            state.availableBranches.take(6).forEach { branch ->
                Button(
                    onClick = { onBranchSelected(branch.id) },
                    modifier = Modifier.fillMaxWidth().testTag("home_choose_branch"),
                ) { Text(branch.name) }
            }
        }
    }
}

@Composable
private fun RevenueSparkline(points: List<HomeRevenueTrendPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) { Box(modifier); return }
    val max = points.maxOf { it.revenueRial }
    val min = points.minOf { it.revenueRial }
    val range = (max - min).coerceAtLeast(1L)
    Canvas(modifier) {
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        points.zipWithNext().forEachIndexed { index, pair ->
            val y1 = size.height - ((pair.first.revenueRial - min).toFloat() / range.toFloat()) * size.height
            val y2 = size.height - ((pair.second.revenueRial - min).toFloat() / range.toFloat()) * size.height
            drawLine(
                color = Color.White.copy(alpha = .92f),
                start = androidx.compose.ui.geometry.Offset(index * step, y1),
                end = androidx.compose.ui.geometry.Offset((index + 1) * step, y2),
                strokeWidth = 4f,
            )
        }
    }
}

@Composable
private fun HomeRevenueTrendCard(overview: HomeManagementOverviewUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_revenue_7d_chart"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ErpPalette.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("روند درآمد ۷ روز اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            when {
                overview.loading -> Text("در حال دریافت داده واقعی…", color = ErpPalette.Muted)
                overview.revenueTrend.size < 2 -> Text(
                    overview.unavailableMessage ?: "داده کافی برای رسم نمودار وجود ندارد.",
                    color = ErpPalette.Muted,
                    modifier = Modifier.testTag("home_revenue_chart_empty"),
                )
                else -> {
                    val points = overview.revenueTrend
                    val max = points.maxOf { it.revenueRial }
                    val min = points.minOf { it.revenueRial }
                    val range = (max - min).coerceAtLeast(1L)
                    Canvas(Modifier.fillMaxWidth().height(150.dp).testTag("home_revenue_chart_canvas")) {
                        val step = size.width / (points.size - 1).coerceAtLeast(1)
                        repeat(3) { grid ->
                            val y = size.height * grid / 2f
                            drawLine(ErpPalette.Border, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                        }
                        points.zipWithNext().forEachIndexed { index, pair ->
                            val y1 = size.height - ((pair.first.revenueRial - min).toFloat() / range.toFloat()) * size.height
                            val y2 = size.height - ((pair.second.revenueRial - min).toFloat() / range.toFloat()) * size.height
                            drawLine(ErpPalette.Teal, androidx.compose.ui.geometry.Offset(index * step, y1), androidx.compose.ui.geometry.Offset((index + 1) * step, y2), 5f)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        points.forEach { point ->
                            val date = epochDayToPersian(point.businessEpochDay)
                            Text(toPersianDigits("${date.month}/${date.day}"), style = MaterialTheme.typography.labelSmall, color = ErpPalette.Muted)
                        }
                    }
                    Text("کمینه ${formatMoney(min)} · بیشینه ${formatMoney(max)}", style = MaterialTheme.typography.labelMedium, color = ErpPalette.Muted)
                }
            }
        }
    }
}

@Composable
private fun SalesHeroCard(
    home: DashboardUiState,
    managementOverview: HomeManagementOverviewUiState,
    onOpen: (AppScreen) -> Unit,
) {
    val hero = home.hero
    if (hero == null) {
        val message = when (home.loadStatus) {
            DashboardLoadStatus.LOADING -> home.statusMessage ?: "در حال بارگذاری داده‌های داشبورد…"
            DashboardLoadStatus.EMPTY -> home.statusMessage ?: "هنوز داده‌ای برای این بازه ثبت نشده"
            DashboardLoadStatus.ERROR -> home.statusMessage ?: "داده‌های داشبورد در دسترس نیست"
            DashboardLoadStatus.LOADED -> "شاخصی برای نقش فعلی در دسترس نیست."
        }
        DashboardStatusCard(
            message = message,
            isError = home.loadStatus == DashboardLoadStatus.ERROR,
        )
        return
    }
    val heroModifier = home.kpis.firstOrNull()?.let { Modifier.testTag("home_kpi_${it.id}") } ?: Modifier

    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_kpi_summary").clickable(enabled = hero.destination != null) {
            hero.destination?.let(onOpen)
        },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(ErpPalette.TealDark, ErpPalette.Teal, Color(0xFF179486))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(modifier = heroModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(hero.title, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        hero.displayValue,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(shape = RoundedCornerShape(99.dp), color = Color.White.copy(alpha = .16f)) {
                        Text(
                            hero.trendText,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                RevenueSparkline(
                    points = managementOverview.revenueTrend,
                    modifier = Modifier.size(width = 108.dp, height = 62.dp).padding(top = 10.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = .10f))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HeroMetric(
                    "نقدینگی",
                    hero.liquidityDisplayValue,
                    Modifier.weight(1f).then(if (home.kpis.any { it.id == "liquidity" }) Modifier.testTag("home_kpi_liquidity") else Modifier),
                )
                HeroMetric(
                    "سود عملیاتی برآوردی",
                    managementOverview.readModel?.estimatedOperatingProfitRial?.let(::formatMoney) ?: "—",
                    Modifier.weight(1f).testTag("home_kpi_estimated_operating_profit"),
                )
            }
        }
    }
}

@Composable
private fun HomeManagementOverview(
    overview: HomeManagementOverviewUiState,
    onOpen: (AppScreen) -> Unit,
) {
    val readModel = overview.readModel
    val unavailableDetail = when {
        overview.loading -> "در حال بارگذاری"
        readModel == null -> overview.unavailableMessage ?: "داده کافی موجود نیست"
        else -> null
    }
    val metrics = listOf(
        HomeManagementMetric("درآمد", readModel?.revenueRial?.let(::formatMoney), AppScreen.SALES),
        HomeManagementMetric("سود ناخالص", readModel?.grossProfitRial?.let(::formatMoney), AppScreen.REPORTS),
        HomeManagementMetric("درصد بهای مواد غذایی", readModel?.foodCostBasisPoints?.let(::formatPercentBasisPoints), AppScreen.DAILY_BRIEF),
        HomeManagementMetric("سود عملیاتی تخمینی", readModel?.estimatedOperatingProfitRial?.let(::formatMoney), AppScreen.DAILY_BRIEF),
        HomeManagementMetric("مطالبات جدید", readModel?.newReceivablesRial?.let(::formatMoney), AppScreen.CRM),
        HomeManagementMetric("وصول مطالبات", readModel?.collectionsRial?.let(::formatMoney), AppScreen.CRM),
        HomeManagementMetric("مسائل بحرانی", readModel?.criticalIssues?.let { toPersianDigits(it.toString()) }, AppScreen.MANAGEMENT_ISSUES),
        HomeManagementMetric("وظایف معوق", readModel?.overdueTasks?.let { toPersianDigits(it.toString()) }, AppScreen.MANAGEMENT_TASKS),
    )
    Column(Modifier.fillMaxWidth().testTag("home_management_overview"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("نمای مدیریتی قطعی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ErpPalette.Ink)
            overview.asOfEpochDay?.let { day ->
                Text(
                    "تا ${epochDayToPersian(day).display()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ErpPalette.Muted,
                    modifier = Modifier.testTag("home_management_as_of"),
                )
            }
        }
        if (overview.isError) {
            Text(
                overview.unavailableMessage ?: "خطا در دریافت شاخص‌های مدیریتی قطعی",
                color = ErpPalette.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("home_management_error"),
            )
        }
        metrics.chunked(if (currentErpWindowClass() == ErpWindowClass.COMPACT) 2 else 4).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { metric ->
                    Surface(
                        onClick = { onOpen(metric.destination) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ErpPalette.Border),
                    ) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(metric.label, style = MaterialTheme.typography.labelSmall, color = ErpPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(metric.value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (metric.value == null) {
                                Text(
                                    unavailableDetail ?: "داده کافی موجود نیست",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ErpPalette.Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                repeat((if (currentErpWindowClass() == ErpWindowClass.COMPACT) 2 else 4) - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private data class HomeManagementMetric(
    val label: String,
    val value: String?,
    val destination: AppScreen,
)

internal fun formatPercentBasisPoints(value: Long): String {
    val whole = value / 100L
    val fraction = kotlin.math.abs(value % 100L).toString().padStart(2, '0')
    return "${toPersianDigits("$whole.$fraction")}٪"
}

@Composable
private fun DashboardStatusCard(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_dashboard_status"),
        shape = RoundedCornerShape(22.dp),
        color = if (isError) ErpPalette.RedSoft else Color.White,
        border = BorderStroke(1.dp, if (isError) ErpPalette.Red.copy(alpha = .22f) else ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) ErpPalette.Red else ErpPalette.Muted,
        )
    }
}

@Composable
private fun HomeAttentionCenter(
    alerts: List<DashboardAttentionUi>,
    partialErrors: List<String>,
    onOpen: (AppScreen) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("home_attention_center"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ErpSectionTitle("نیازمند توجه")
        partialErrors.forEach { error ->
            Surface(shape = RoundedCornerShape(16.dp), color = ErpPalette.RedSoft) {
                Text(error, Modifier.fillMaxWidth().padding(13.dp), color = ErpPalette.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (alerts.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, ErpPalette.Border)) {
                Text("در حال حاضر مورد فوری برای اقدام وجود ندارد.", Modifier.fillMaxWidth().padding(16.dp), color = ErpPalette.Muted)
            }
        } else {
            alerts.take(3).forEach { alert -> AttentionRow(alert, onOpen) }
        }
    }
}

@Composable
private fun AttentionRow(alert: DashboardAttentionUi, onOpen: (AppScreen) -> Unit) {
    val accent = when (alert.severity) {
        DashboardAttentionSeverity.CRITICAL -> ErpPalette.Red
        DashboardAttentionSeverity.WARNING -> ErpPalette.Amber
        DashboardAttentionSeverity.NOTICE -> ErpPalette.Blue
    }
    val soft = when (alert.severity) {
        DashboardAttentionSeverity.CRITICAL -> ErpPalette.RedSoft
        DashboardAttentionSeverity.WARNING -> ErpPalette.AmberSoft
        DashboardAttentionSeverity.NOTICE -> ErpPalette.BlueSoft
    }
    val icon = when (alert.id) {
        "low_stock" -> Icons.Outlined.Inventory2
        "expiry" -> Icons.Outlined.WarningAmber
        else -> Icons.Outlined.NotificationsNone
    }
    val title = when (alert.id) {
        "low_stock" -> "کالاهای با موجودی کم"
        "expiry" -> "کالاهای نزدیک به انقضا"
        else -> alert.title
    }
    val explanation = when (alert.id) {
        "low_stock" -> "${toPersianDigits(alert.count.toString())} قلم کالا موجودی کمتر از حداقل دارند"
        "expiry" -> "${toPersianDigits(alert.count.toString())} قلم کالا نیازمند بررسی تاریخ انقضا هستند"
        else -> alert.explanation
    }

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home_alert_${alert.id}").then(
            if (alert.id == "low_stock" || alert.id == "expiry") Modifier.testTag("home_kpi_${alert.id}") else Modifier,
        ).clickable { onOpen(alert.destination) },
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(accent, CircleShape))
            PastelIcon(icon, soft, accent, Modifier.padding(start = 10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = ErpPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(toPersianDigits(alert.count.toString()), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.ExtraBold)
                }
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = ErpPalette.Muted.copy(alpha = .55f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeQuickActions(actions: List<DashboardQuickActionUi>, onOpen: (AppScreen) -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("home_quick_actions"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErpSectionTitle("عملیات سریع")
        if (actions.isEmpty()) {
            Text("عملیات سریعی برای نقش فعلی در دسترس نیست.", color = ErpPalette.Muted, style = MaterialTheme.typography.bodySmall)
        } else {
            actions.take(6).chunked(3).forEach { rowActions ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    rowActions.forEach { action ->
                        QuickActionButton(action, Modifier.weight(1f), onOpen)
                    }
                    repeat(3 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(action: DashboardQuickActionUi, modifier: Modifier, onOpen: (AppScreen) -> Unit) {
    val visual = quickActionVisual(action.id)
    Surface(
        modifier = modifier.testTag("home_action_${action.id}").clickable { onOpen(action.destination) },
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 7.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PastelIcon(visual.first, visual.second, visual.third)
            Text(
                quickActionTitle(action),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ErpPalette.Ink,
                maxLines = 1,
            )
        }
    }
}

private fun quickActionVisual(id: String): Triple<ImageVector, Color, Color> = when (id) {
    "sale" -> Triple(Icons.Outlined.Assessment, ErpPalette.GreenSoft, ErpPalette.Green)
    "purchase" -> Triple(Icons.Outlined.ShoppingCart, ErpPalette.BlueSoft, ErpPalette.Blue)
    "inventory" -> Triple(Icons.Outlined.Inventory2, ErpPalette.AmberSoft, ErpPalette.Amber)
    "treasury" -> Triple(Icons.Outlined.Payments, ErpPalette.PurpleSoft, ErpPalette.Purple)
    "reports" -> Triple(Icons.Outlined.Assessment, ErpPalette.IndigoSoft, ErpPalette.Indigo)
    "customers" -> Triple(Icons.Outlined.Groups, ErpPalette.GreenSoft, ErpPalette.Teal)
    else -> Triple(Icons.Outlined.AccountBalanceWallet, ErpPalette.IndigoSoft, ErpPalette.Indigo)
}

private fun quickActionTitle(action: DashboardQuickActionUi): String = when (action.id) {
    "sale" -> "فروش"
    "purchase" -> "خرید"
    "inventory" -> "انبار"
    "treasury" -> "صندوق"
    "reports" -> "گزارش"
    "customers" -> "مشتریان"
    else -> action.title
}

@Composable
private fun TodayOverview(home: DashboardUiState, onOpen: (AppScreen) -> Unit) {
    val metrics = listOfNotNull(home.invoices, home.expenses)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ErpSectionTitle(home.labels.overviewTitle)
        if (home.loadStatus != DashboardLoadStatus.LOADED) {
            Text(
                home.statusMessage ?: when (home.loadStatus) {
                    DashboardLoadStatus.LOADING -> "در حال بارگذاری داده‌های داشبورد…"
                    DashboardLoadStatus.EMPTY -> "هنوز داده‌ای برای این بازه ثبت نشده"
                    DashboardLoadStatus.ERROR -> "داده‌های این بخش در دسترس نیست"
                    DashboardLoadStatus.LOADED -> ""
                },
                color = ErpPalette.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (metrics.isEmpty()) {
            Text("شاخصی برای نقش فعلی در دسترس نیست.", color = ErpPalette.Muted, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                metrics.take(3).forEach { metric ->
                    ErpMetricCard(
                        title = metric.title,
                        value = metric.displayValue,
                        change = metric.trendText,
                        direction = metric.trend.direction,
                        modifier = Modifier.weight(1f).then(
                            metric.destination?.let { destination -> Modifier.clickable { onOpen(destination) } } ?: Modifier,
                        ),
                    )
                }
            }
        }
    }
}
