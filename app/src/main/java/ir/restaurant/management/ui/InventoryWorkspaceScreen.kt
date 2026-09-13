package ir.restaurant.management.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryDashboardSnapshot
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.inventory.InventoryMovementView

@Composable
fun InventoryWorkspaceScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
    operationsState: OperationsUiState,
    operations: OperationsViewModel,
    branches: List<BranchRecord>,
    onBack: () -> Unit,
    onNavigate: (AppScreen) -> Unit = {},
) {
    val section = state.section
    var showStockOutChooser by remember { mutableStateOf(false) }
    if (section == InventoryWorkspaceSection.OVERVIEW) {
        Scaffold(
            containerColor = ErpPalette.Canvas,
            bottomBar = { ErpBottomNavigation(AppScreen.INVENTORY, onNavigate) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                state.message?.let { MessageCard(it) }
                InventoryOverviewScreen(
                    state = state,
                    onOpen = viewModel::selectSection,
                    onRefresh = viewModel::refresh,
                    onOpenItems = viewModel::openItems,
                    onSetLocation = viewModel::setLocation,
                    onQuickAction = { action ->
                        when (action) {
                            InventoryOverviewAction.RECEIVE -> {
                                operations.requestProcurementAction(ProcurementLaunchAction.GOODS_RECEIPT)
                                onNavigate(AppScreen.PURCHASES)
                            }
                            InventoryOverviewAction.ISSUE -> showStockOutChooser = true
                            InventoryOverviewAction.TRANSFER -> viewModel.launchAction(InventoryWorkspaceAction.CREATE_TRANSFER)
                            InventoryOverviewAction.COUNT -> viewModel.launchAction(InventoryWorkspaceAction.CREATE_COUNT)
                            InventoryOverviewAction.WASTE -> viewModel.launchAction(InventoryWorkspaceAction.CREATE_WASTE)
                            InventoryOverviewAction.CREATE_ITEM -> viewModel.launchAction(InventoryWorkspaceAction.CREATE_ITEM)
                        }
                    },
                )
            }
        }
        if (showStockOutChooser) {
            ControlledStockOutDialog(
                state = state,
                onDismiss = { showStockOutChooser = false },
                onTransfer = {
                    showStockOutChooser = false
                    viewModel.launchAction(InventoryWorkspaceAction.CREATE_TRANSFER)
                },
                onWaste = {
                    showStockOutChooser = false
                    viewModel.launchAction(InventoryWorkspaceAction.CREATE_WASTE)
                },
            )
        }
        return
    }

    val title = inventorySectionTitle(section)
    val subtitle = inventorySectionSubtitle(section)
    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = title,
                subtitle = subtitle,
                onBack = { viewModel.selectSection(InventoryWorkspaceSection.OVERVIEW) },
                actionLabel = "تازه‌سازی",
                onAction = viewModel::refresh,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.message?.let { MessageCard(it) }
            when (section) {
                InventoryWorkspaceSection.OVERVIEW -> Unit
                InventoryWorkspaceSection.ITEMS -> InventoryItemCenterScreen(state, viewModel, branches)
                InventoryWorkspaceSection.EXPIRY -> InventoryExpiryCenterScreen(state, viewModel)
                InventoryWorkspaceSection.MOVEMENTS -> InventoryMovementCenterScreen(state)
                InventoryWorkspaceSection.COUNTS -> InventoryCountCenterScreen(state, viewModel)
                InventoryWorkspaceSection.WASTE -> InventoryWasteCenterScreen(state, viewModel)
                InventoryWorkspaceSection.TRANSFERS -> InventoryTransferCenterScreen(state, viewModel)
                InventoryWorkspaceSection.REPLENISHMENT -> InventoryReplenishmentCenterScreen(state, viewModel)
                InventoryWorkspaceSection.PERIODS -> InventoryPeriodCenterScreen(
                    state = operationsState,
                    onClose = operations::closeInventoryPeriod,
                    onReopen = operations::reopenInventoryPeriod,
                    onSelectClosure = operations::selectInventoryClosure,
                )
            }
        }
    }
}

@Composable
private fun InventoryOverviewScreen(
    state: InventoryWorkspaceUiState,
    onOpen: (InventoryWorkspaceSection) -> Unit,
    onRefresh: () -> Unit,
    onOpenItems: (String, ir.restaurant.management.domain.inventory.InventoryStockStatus, Long?) -> Unit,
    onSetLocation: (Long?) -> Unit,
    onQuickAction: (InventoryOverviewAction) -> Unit,
) {
    val dashboard = state.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("inventory_overview_list"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { InventoryHeader(state, onRefresh, onOpenItems, onSetLocation) }
        if (dashboard == null) {
            item {
                ErpStatePanel(
                    title = if (state.loading) "در حال بارگذاری انبار" else "داده‌های انبار در دسترس نیست",
                    description = if (state.loading) "خلاصه موجودی و کنترل‌های عملیاتی در حال خواندن است." else (state.message ?: "برای دریافت مجدد داده‌های انبار تلاش کنید."),
                    isError = !state.loading,
                    retry = if (state.loading) null else onRefresh,
                )
            }
        } else {
            item { InventoryHeroCard(dashboard) }
            item { InventoryNeedsAction(dashboard, onOpen) }
            item { InventoryStatusSection(state) }
        }
        item { InventoryQuickActions(state, onQuickAction) }
        item { RecentInventoryActivity(state.movements, onOpen) }
        item { InventoryControlCenters(state, onOpen) }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun InventoryHeader(
    state: InventoryWorkspaceUiState,
    onRefresh: () -> Unit,
    onOpenItems: (String, ir.restaurant.management.domain.inventory.InventoryStockStatus, Long?) -> Unit,
    onSetLocation: (Long?) -> Unit,
) {
    var searchDialog by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var warehouseDialog by remember { mutableStateOf(false) }
    var searchText by remember(state.query) { mutableStateOf(state.query) }
    var selectedStatus by remember(state.stockStatus) { mutableStateOf(state.stockStatus) }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ManagementGeometricLogo()
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("انبار", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ErpPalette.Ink)
                Text("مدیریت رستوران", style = MaterialTheme.typography.labelMedium, color = ErpPalette.Muted)
            }
            IconButton(onClick = { searchDialog = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Search, contentDescription = "جست‌وجوی کالا", tint = ErpPalette.Ink) }
            IconButton(onClick = { filterDialog = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.FilterAlt, contentDescription = "فیلتر موجودی", tint = ErpPalette.Ink) }
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.NotificationsNone, contentDescription = "تازه‌سازی", tint = ErpPalette.Ink) }
            Surface(shape = CircleShape, color = ErpPalette.IndigoSoft, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = "کاربر", tint = ErpPalette.Indigo, modifier = Modifier.padding(9.dp))
            }
        }

        val selectedWarehouse = state.locationId?.let { id -> state.locations.firstOrNull { it.id == id }?.name } ?: "همه محل‌ها"
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { warehouseDialog = true },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ErpPalette.Border),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                PastelIcon(Icons.Outlined.Inventory2, ErpPalette.TealLight, ErpPalette.Teal, Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("انبار / محل فعال", style = MaterialTheme.typography.labelSmall, color = ErpPalette.Muted)
                    Text(selectedWarehouse, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ErpPalette.Ink)
                }
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "انتخاب انبار", tint = ErpPalette.Muted.copy(alpha = .6f), modifier = Modifier.size(18.dp))
            }
        }
    }

    if (searchDialog) {
        AlertDialog(
            onDismissRequest = { searchDialog = false },
            title = { Text("جست‌وجوی کالا") },
            text = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it.take(80) },
                    label = { Text("نام، SKU یا بارکد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    searchDialog = false
                    onOpenItems(searchText, state.stockStatus, state.locationId)
                }) { Text("جست‌وجو") }
            },
            dismissButton = { TextButton(onClick = { searchDialog = false }) { Text("انصراف") } },
        )
    }
    if (filterDialog) {
        AlertDialog(
            onDismissRequest = { filterDialog = false },
            title = { Text("فیلتر وضعیت موجودی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ir.restaurant.management.domain.inventory.InventoryStockStatus.entries.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            label = { Text(inventoryStockStatusTitle(status)) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    filterDialog = false
                    onOpenItems(state.query, selectedStatus, state.locationId)
                }) { Text("اعمال فیلتر") }
            },
            dismissButton = { TextButton(onClick = { filterDialog = false }) { Text("انصراف") } },
        )
    }
    if (warehouseDialog) {
        AlertDialog(
            onDismissRequest = { warehouseDialog = false },
            title = { Text("انتخاب انبار / محل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { warehouseDialog = false; onSetLocation(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("همه محل‌ها") }
                    state.locations.filter { it.active }.take(20).forEach { location ->
                        OutlinedButton(
                            onClick = { warehouseDialog = false; onSetLocation(location.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(location.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { warehouseDialog = false }) { Text("بستن") } },
        )
    }
}

@Composable
private fun InventoryHeroCard(dashboard: InventoryDashboardSnapshot?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(ErpPalette.TealDark, ErpPalette.Teal, Color(0xFF179486))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ارزش موجودی", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatMoney(dashboard?.totalInventoryValueRial ?: 0L, CurrencyUnit.TOMAN),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.size(70.dp).background(Color.White.copy(alpha = .1f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Inventory, contentDescription = null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(38.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = .1f), RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HeroMetric("کالاها", toPersianDigits((dashboard?.activeItemCount ?: 0).toString()), Modifier.weight(1f))
                HeroMetric("کم‌موجود", toPersianDigits((dashboard?.lowStockItemCount ?: 0).toString()), Modifier.weight(1f))
                HeroMetric("اتمام", toPersianDigits((dashboard?.outOfStockItemCount ?: 0).toString()), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InventoryNeedsAction(
    dashboard: InventoryDashboardSnapshot?,
    onOpen: (InventoryWorkspaceSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ErpSectionTitle("نیازمند اقدام")
        InventoryActionRow(
            title = "کالاهای کم‌موجود",
            description = "${toPersianDigits((dashboard?.lowStockItemCount ?: 0).toString())} قلم کالا نیاز به سفارش یا تأمین دارند",
            icon = Icons.Outlined.WarningAmber,
            accent = ErpPalette.Amber,
            soft = ErpPalette.AmberSoft,
            target = InventoryWorkspaceSection.REPLENISHMENT,
            onOpen = onOpen,
        )
        InventoryActionRow(
            title = "کالاهای نزدیک به انقضا",
            description = "${toPersianDigits((dashboard?.expiringLotCount ?: 0).toString())} قلم کالا تا ۶۰ روز آینده منقضی می‌شوند",
            icon = Icons.Outlined.Inventory2,
            accent = ErpPalette.Red,
            soft = ErpPalette.RedSoft,
            target = InventoryWorkspaceSection.EXPIRY,
            onOpen = onOpen,
        )
        InventoryActionRow(
            title = "انتقال‌های در جریان",
            description = "${toPersianDigits((dashboard?.pendingTransferCount ?: 0).toString())} انتقال بین انبارها در حال انجام است",
            icon = Icons.Outlined.SwapHoriz,
            accent = ErpPalette.Blue,
            soft = ErpPalette.BlueSoft,
            target = InventoryWorkspaceSection.TRANSFERS,
            onOpen = onOpen,
        )
        InventoryActionRow(
            title = "جلسات شمارش باز",
            description = "${toPersianDigits((dashboard?.pendingCountSessionCount ?: 0).toString())} جلسه شمارش در حال انجام است",
            icon = Icons.Outlined.TaskAlt,
            accent = ErpPalette.Green,
            soft = ErpPalette.GreenSoft,
            target = InventoryWorkspaceSection.COUNTS,
            onOpen = onOpen,
        )
    }
}

@Composable
private fun InventoryActionRow(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    soft: Color,
    target: InventoryWorkspaceSection,
    onOpen: (InventoryWorkspaceSection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(target) },
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(accent, CircleShape))
            PastelIcon(icon, soft, accent, Modifier.padding(start = 10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = ErpPalette.Ink)
                Text(description, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = ErpPalette.Muted.copy(alpha = .55f), modifier = Modifier.size(18.dp))
        }
    }
}

private enum class InventoryOverviewAction { RECEIVE, ISSUE, TRANSFER, COUNT, WASTE, CREATE_ITEM }

@Composable
private fun InventoryQuickActions(
    state: InventoryWorkspaceUiState,
    onAction: (InventoryOverviewAction) -> Unit,
) {
    val role = state.currentUser?.role
    val actions = listOf(
        InventoryQuickAction("ورود کالا", Icons.Outlined.ArrowDownward, InventoryOverviewAction.RECEIVE, ErpPalette.GreenSoft, ErpPalette.Green, role?.allows(ir.restaurant.management.domain.security.Permission.PURCHASES) == true),
        InventoryQuickAction("خروج کالا", Icons.Outlined.ArrowUpward, InventoryOverviewAction.ISSUE, ErpPalette.RedSoft, ErpPalette.Red, role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_WASTE_CREATE) == true || role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_TRANSFER_CREATE) == true),
        InventoryQuickAction("انتقال", Icons.Outlined.SwapHoriz, InventoryOverviewAction.TRANSFER, ErpPalette.BlueSoft, ErpPalette.Blue, role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_TRANSFER_CREATE) == true),
        InventoryQuickAction("شمارش", Icons.Outlined.TaskAlt, InventoryOverviewAction.COUNT, ErpPalette.IndigoSoft, ErpPalette.Indigo, role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_COUNT_CREATE) == true),
        InventoryQuickAction("ضایعات", Icons.Outlined.DeleteOutline, InventoryOverviewAction.WASTE, ErpPalette.AmberSoft, ErpPalette.Amber, role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_WASTE_CREATE) == true),
        InventoryQuickAction("کالای جدید", Icons.Outlined.AddBox, InventoryOverviewAction.CREATE_ITEM, ErpPalette.TealLight, ErpPalette.Teal, role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_ITEM_MANAGE) == true),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ErpSectionTitle("عملیات سریع")
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { action ->
                    ErpQuickActionTile(
                        title = action.title,
                        icon = action.icon,
                        soft = action.soft,
                        accent = action.accent,
                        enabled = action.enabled,
                        onClick = { onAction(action.action) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class InventoryQuickAction(
    val title: String,
    val icon: ImageVector,
    val action: InventoryOverviewAction,
    val soft: Color,
    val accent: Color,
    val enabled: Boolean,
)

@Composable
private fun InventoryStatusSection(state: InventoryWorkspaceUiState) {
    val dashboard = state.dashboard
    val healthy = ((dashboard?.activeItemCount ?: 0) - (dashboard?.lowStockItemCount ?: 0) - (dashboard?.outOfStockItemCount ?: 0)).coerceAtLeast(0)
    val quarantined = dashboard?.quarantinedLotCount ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ErpSectionTitle("وضعیت موجودی")
        val cards = listOf(
            InventoryStatusUi("موجودی کافی", healthy, ErpPalette.Green, ErpPalette.GreenSoft),
            InventoryStatusUi("کم‌موجود", dashboard?.lowStockItemCount ?: 0, ErpPalette.Amber, ErpPalette.AmberSoft),
            InventoryStatusUi("نزدیک به انقضا", dashboard?.expiringLotCount ?: 0, ErpPalette.Red, ErpPalette.RedSoft),
            InventoryStatusUi("قرنطینه", quarantined, ErpPalette.Purple, ErpPalette.PurpleSoft),
        )
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { status ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, ErpPalette.Border),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(status.accent, CircleShape))
                            Column(Modifier.weight(1f).padding(horizontal = 9.dp)) {
                                Text(status.title, style = MaterialTheme.typography.labelMedium, color = ErpPalette.Muted)
                                Text(toPersianDigits(status.value.toString()), style = MaterialTheme.typography.titleLarge, color = ErpPalette.Ink, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun inventoryStockStatusTitle(status: ir.restaurant.management.domain.inventory.InventoryStockStatus): String = when (status) {
    ir.restaurant.management.domain.inventory.InventoryStockStatus.ALL -> "همه"
    ir.restaurant.management.domain.inventory.InventoryStockStatus.HEALTHY -> "سالم"
    ir.restaurant.management.domain.inventory.InventoryStockStatus.LOW -> "کمبود"
    ir.restaurant.management.domain.inventory.InventoryStockStatus.OUT_OF_STOCK -> "اتمام"
}

private data class InventoryStatusUi(val title: String, val value: Int, val accent: Color, val soft: Color)

@Composable
private fun RecentInventoryActivity(
    movements: List<InventoryMovementView>,
    onOpen: (InventoryWorkspaceSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ErpSectionTitle("گردش اخیر", "مشاهده همه", onAction = { onOpen(InventoryWorkspaceSection.MOVEMENTS) })
        if (movements.isEmpty()) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, ErpPalette.Border)) {
                Text("گردش ثبت‌شده‌ای برای نمایش وجود ندارد.", Modifier.fillMaxWidth().padding(15.dp), color = ErpPalette.Muted, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            movements.take(3).forEach { movement -> RecentMovementRow(movement) }
        }
    }
}

@Composable
private fun RecentMovementRow(movement: InventoryMovementView) {
    val positive = movement.quantityDeltaMicros >= 0
    val accent = if (positive) ErpPalette.Green else ErpPalette.Red
    val soft = if (positive) ErpPalette.GreenSoft else ErpPalette.RedSoft
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            PastelIcon(if (positive) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, soft, accent)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(movementTypeTitle(movement.movementType), fontWeight = FontWeight.Bold, color = ErpPalette.Ink, style = MaterialTheme.typography.bodyMedium)
                Text(movement.itemName, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(movement.locationName, movement.actorId?.let { "کاربر #${toPersianDigits(it.toString())}" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ErpPalette.Muted.copy(alpha = .85f),
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${if (positive) "+" else ""}${formatQuantity(movement.quantityDeltaMicros)} ${movement.baseUnit}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    ErpDisplayFormatters.activityDateTime(movement.businessEpochDay, movement.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = ErpPalette.Muted,
                )
            }
        }
    }
}

@Composable
private fun InventoryControlCenters(
    state: InventoryWorkspaceUiState,
    onOpen: (InventoryWorkspaceSection) -> Unit,
) {
    val actionableReplenishment = state.replenishment.count { it.isActionable }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ErpSectionTitle("مراکز کنترل")
        inventoryCenters.forEach { (target, description) ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("inventory_section_${target.name}").clickable { onOpen(target) },
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ErpPalette.Border),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(inventorySectionTitle(target), fontWeight = FontWeight.Bold, color = ErpPalette.Ink)
                        Text(
                            if (target == InventoryWorkspaceSection.REPLENISHMENT) "$description · ${toPersianDigits(actionableReplenishment.toString())} پیشنهاد قابل اقدام" else description,
                            color = ErpPalette.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                    }
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = ErpPalette.Muted.copy(alpha = .55f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ControlledStockOutDialog(
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onTransfer: () -> Unit,
    onWaste: () -> Unit,
) {
    val role = state.currentUser?.role
    val canTransfer = role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_TRANSFER_CREATE) == true
    val canWaste = role?.allows(ir.restaurant.management.domain.security.Permission.INVENTORY_WASTE_CREATE) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خروج کنترل‌شده کالا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("خروج مستقیم از دفترکل انجام نمی‌شود. نوع سند عملیاتی را انتخاب کنید تا ردگیری و Audit حفظ شود.")
                OutlinedButton(onClick = onTransfer, enabled = canTransfer, modifier = Modifier.fillMaxWidth()) { Text("انتقال به محل دیگر") }
                OutlinedButton(onClick = onWaste, enabled = canWaste, modifier = Modifier.fillMaxWidth()) { Text("ثبت ضایعات / خروج مستند") }
                if (!canTransfer && !canWaste) Text("برای ثبت خروج کنترل‌شده مجوز کافی ندارید.", color = ErpPalette.Red)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

private val inventoryCenters = listOf(
    InventoryWorkspaceSection.ITEMS to "کالا، SKU، بارکد، محل و مانده",
    InventoryWorkspaceSection.EXPIRY to "FEFO، انقضا، قرنطینه و لات",
    InventoryWorkspaceSection.MOVEMENTS to "دفتر غیرقابل‌ویرایش گردش موجودی",
    InventoryWorkspaceSection.COUNTS to "شمارش کور، بازشماری، تأیید و ثبت نهایی",
    InventoryWorkspaceSection.WASTE to "ضایعات ردیابی‌پذیر و بهای ثبت‌شده",
    InventoryWorkspaceSection.TRANSFERS to "انتقال سندمحور و کالای در راه",
    InventoryWorkspaceSection.REPLENISHMENT to "پوشش، نقطه سفارش و درخواست خرید",
    InventoryWorkspaceSection.PERIODS to "بستن و بازگشایی کنترل‌شده دوره انبار",
)

internal fun inventorySectionTitle(section: InventoryWorkspaceSection): String = when (section) {
    InventoryWorkspaceSection.OVERVIEW -> "انبار"
    InventoryWorkspaceSection.ITEMS -> "کالا و محل نگهداری"
    InventoryWorkspaceSection.EXPIRY -> "مرکز انقضا و لات"
    InventoryWorkspaceSection.MOVEMENTS -> "دفتر گردش موجودی"
    InventoryWorkspaceSection.COUNTS -> "مرکز انبارگردانی"
    InventoryWorkspaceSection.WASTE -> "مرکز ضایعات"
    InventoryWorkspaceSection.TRANSFERS -> "مرکز انتقال"
    InventoryWorkspaceSection.REPLENISHMENT -> "تأمین مجدد"
    InventoryWorkspaceSection.PERIODS -> "کنترل دوره انبار"
}

private fun inventorySectionSubtitle(section: InventoryWorkspaceSection): String = when (section) {
    InventoryWorkspaceSection.OVERVIEW -> "آنچه اکنون نیازمند تصمیم و اقدام است"
    InventoryWorkspaceSection.ITEMS -> "اطلاعات پایه، موجودی مکان‌محور و جست‌وجوی سریع"
    InventoryWorkspaceSection.EXPIRY -> "مصرف FEFO، قرنطینه و تبدیل صریح به ضایعات"
    InventoryWorkspaceSection.MOVEMENTS -> "ردیابی تا سند مبدأ، کاربر ثبت‌کننده و شناسه همبستگی"
    InventoryWorkspaceSection.COUNTS -> "چرخه پیش‌نویس تا ثبت نهایی با تفکیک وظایف"
    InventoryWorkspaceSection.WASTE -> "مقدار، علت، بهای تاریخی، تأیید و ثبت"
    InventoryWorkspaceSection.TRANSFERS -> "درخواست، صدور، کالای در راه و دریافت"
    InventoryWorkspaceSection.REPLENISHMENT -> "تقاضا، زمان تأمین، موجودی اطمینان و تدارکات"
    InventoryWorkspaceSection.PERIODS -> "کنترل ثبت اسناد Backdated موجودی"
}

@Composable
internal fun InventoryEmptyState(text: String) {
    EmptyStatePanel("موردی یافت نشد", text)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun InventoryOverviewPreview() {
    val state = InventoryWorkspaceUiState(
        loading = false,
        dashboard = InventoryDashboardSnapshot(
            totalInventoryValueRial = 1_284_500_000_000,
            activeItemCount = 1_248,
            lowStockItemCount = 86,
            outOfStockItemCount = 23,
            expiringLotCount = 32,
            expiredLotCount = 4,
            quarantinedLotCount = 6,
            wasteCostRial = 0,
            inventoryVarianceRial = 0,
            pendingTransferCount = 5,
            pendingCountSessionCount = 2,
        ),
    )
    MaterialTheme {
        Scaffold(containerColor = ErpPalette.Canvas, bottomBar = { ErpBottomNavigation(AppScreen.INVENTORY) {} }) { padding ->
            Box(Modifier.padding(padding)) {
                InventoryOverviewScreen(
                    state = state,
                    onOpen = {},
                    onRefresh = {},
                    onOpenItems = { _, _, _ -> },
                    onSetLocation = {},
                    onQuickAction = {},
                )
            }
        }
    }
}
