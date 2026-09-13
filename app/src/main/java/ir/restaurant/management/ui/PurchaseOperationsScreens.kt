package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.operations.StockMovementRecord
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.operations.WasteDraft
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineDraft
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod

@Composable
fun PurchasesScreen(
    state: OperationsUiState,
    branches: List<BranchRecord>,
    currentUser: AppUserRecord?,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onPurchaseToday: () -> Unit,
    onPurchaseWeek: () -> Unit,
    onPurchaseMonth: () -> Unit,
    onLoadPurchasePriceControl: (Long, Long, Long) -> Unit,
    onRequestProcurementAction: (ProcurementLaunchAction) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onSelect: (Long?) -> Unit,
    onSettle: (ir.restaurant.management.domain.purchase.PurchaseSettlementDraft, () -> Unit) -> Unit,
    onReverseSettlement: (ir.restaurant.management.domain.purchase.PurchaseSettlementReversalDraft, () -> Unit) -> Unit,
    onReverse: (ir.restaurant.management.domain.purchase.PurchaseReversalDraft, () -> Unit) -> Unit,
    onSubmitRequisition: (ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft, () -> Unit) -> Unit,
    onReviewRequisition: (Long, Boolean, String, () -> Unit) -> Unit,
    onCreateOrder: (ir.restaurant.management.domain.purchase.PurchaseOrderDraft, () -> Unit) -> Unit,
    onCreateSplitOrders: (ir.restaurant.management.domain.purchase.SplitPurchaseOrdersDraft, () -> Unit) -> Unit,
    onMarkOrderSent: (Long, ir.restaurant.management.domain.purchase.PurchaseOrderDispatchChannel) -> Unit,
    onAcknowledgeOrder: (ir.restaurant.management.domain.purchase.PurchaseOrderAcknowledgementDraft, () -> Unit) -> Unit,
    onReceiveGoods: (ir.restaurant.management.domain.purchase.GoodsReceiptDraft, () -> Unit) -> Unit,
    onReturnGoods: (ir.restaurant.management.domain.purchase.PurchaseReturnDraft, () -> Unit) -> Unit,
    onSaveReplenishmentPolicy: (ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft, () -> Unit) -> Unit,
    onSaveSupplierOffer: (ir.restaurant.management.domain.purchase.SupplierOfferDraft, () -> Unit) -> Unit,
    onSubmitSuggestedRequisition: (List<Long>, () -> Unit) -> Unit,
    onMatchInvoice: (Long, PurchaseDraft, Boolean, () -> Unit) -> Unit,
    onConsumeProcurementLaunchAction: (ProcurementLaunchAction) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val activeBranches = branches.filter { it.isActive }
    var selectedPriceBranchId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = remember { currentEpochDay() }
    LaunchedEffect(activeBranches, selectedPriceBranchId) {
        if (selectedPriceBranchId != null && selectedPriceBranchId !in activeBranches.map { it.id }) {
            selectedPriceBranchId = null
        }
    }
    LaunchedEffect(selectedPriceBranchId) {
        selectedPriceBranchId?.let { onLoadPurchasePriceControl(it, today - 29, today) }
    }
    Scaffold(
        containerColor = ErpPalette.Canvas,
        bottomBar = { ErpBottomNavigation(AppScreen.OPERATIONS_HUB, onNavigate) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("purchase_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ErpModuleHeader(
                    title = "خرید و تأمین",
                    subtitle = "مدیریت رستوران",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onRefresh, enabled = !state.refreshing) { Text(if (state.refreshing) "در حال تازه‌سازی…" else "تازه‌سازی") }
                            TextButton(onClick = onBack) { Text("بازگشت") }
                        }
                    },
                )
            }
            state.message?.let { item { MessageCard(it) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.purchaseDashboard.period == ir.restaurant.management.data.repository.DashboardPeriod.TODAY,
                        onClick = onPurchaseToday,
                        label = { Text("امروز") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.purchaseDashboard.period == ir.restaurant.management.data.repository.DashboardPeriod.WEEK,
                        onClick = onPurchaseWeek,
                        label = { Text("هفته") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.purchaseDashboard.period == ir.restaurant.management.data.repository.DashboardPeriod.MONTH,
                        onClick = onPurchaseMonth,
                        label = { Text("ماه") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (state.purchaseDashboard.status) {
                PurchaseDashboardLoadStatus.LOADING -> item { ErpStatePanel("در حال دریافت خرید", state.purchaseDashboard.message ?: "در حال دریافت خلاصه خرید…") }
                PurchaseDashboardLoadStatus.ERROR -> item { ErpStatePanel("خطا در دریافت خرید", state.purchaseDashboard.message ?: "دریافت خلاصه خرید انجام نشد.", isError = true) }
                PurchaseDashboardLoadStatus.EMPTY -> item { ErpStatePanel("هنوز خریدی ثبت نشده", state.purchaseDashboard.message ?: "هنوز داده‌ای برای این بازه ثبت نشده") }
                PurchaseDashboardLoadStatus.LOADED -> item { PurchaseHero(state.purchaseDashboard, onAdd) }
            }

            item { ErpSectionTitle("نیازمند توجه") }
            val attentionCount = (if (state.purchaseDashboard.overdueOrderCount > 0) 1 else 0) +
                (if (state.purchaseDashboard.pendingReceiptCount > 0) 1 else 0) +
                (if (state.purchaseDashboard.pendingApprovalCount > 0) 1 else 0) +
                (if (state.purchaseDashboard.supplierPayablesRial > 0L) 1 else 0) +
                (if (state.settlementAlerts.isNotEmpty()) 1 else 0)
            if (state.purchaseDashboard.overdueOrderCount > 0) item {
                ErpAttentionRow(
                    "سفارش‌های خرید دیرکرده",
                    "${ErpDisplayFormatters.integer(state.purchaseDashboard.overdueOrderCount)} سفارش از تاریخ دریافت مورد انتظار عبور کرده است",
                    androidx.compose.material.icons.Icons.Outlined.Schedule,
                    ErpPalette.Red,
                    ErpPalette.RedSoft,
                )
            }
            if (state.purchaseDashboard.pendingReceiptCount > 0) item {
                ErpAttentionRow(
                    "کالاهای در انتظار دریافت",
                    "${state.purchaseDashboard.pendingReceiptDisplay} سفارش هنوز کامل دریافت نشده است",
                    androidx.compose.material.icons.Icons.Outlined.LocalShipping,
                    ErpPalette.Blue,
                    ErpPalette.BlueSoft,
                    onClick = { onRequestProcurementAction(ProcurementLaunchAction.GOODS_RECEIPT) },
                )
            }
            if (state.purchaseDashboard.pendingApprovalCount > 0) item {
                ErpAttentionRow(
                    "درخواست‌های نیازمند تأیید",
                    "${ErpDisplayFormatters.integer(state.purchaseDashboard.pendingApprovalCount)} درخواست در چرخه تأیید است",
                    androidx.compose.material.icons.Icons.Outlined.Approval,
                    ErpPalette.Amber,
                    ErpPalette.AmberSoft,
                )
            }
            if (state.purchaseDashboard.supplierPayablesRial > 0L) item {
                ErpAttentionRow(
                    "بدهی به تأمین‌کنندگان",
                    state.purchaseDashboard.supplierPayablesDisplay,
                    androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet,
                    ErpPalette.Amber,
                    ErpPalette.AmberSoft,
                    onClick = { onNavigate(AppScreen.TREASURY) },
                )
            }
            if (state.settlementAlerts.isNotEmpty()) item {
                ErpAttentionRow(
                    "سررسیدهای پرداخت",
                    "${ErpDisplayFormatters.integer(state.settlementAlerts.size)} فاکتور به تاریخ یادآوری پرداخت رسیده است",
                    androidx.compose.material.icons.Icons.Outlined.NotificationsActive,
                    ErpPalette.Red,
                    ErpPalette.RedSoft,
                )
            }
            if (attentionCount == 0) item { ErpStatePanel("مورد فوری ثبت نشده", "در حال حاضر مورد نیازمند اقدام فوری در خرید وجود ندارد.") }

            item { ErpSectionTitle("عملیات سریع") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ErpQuickActionTile("درخواست خرید", androidx.compose.material.icons.Icons.Outlined.RequestQuote, ErpPalette.BlueSoft, ErpPalette.Blue, onClick = { onRequestProcurementAction(ProcurementLaunchAction.REQUISITION) }, modifier = Modifier.weight(1f))
                        ErpQuickActionTile("سفارش خرید", androidx.compose.material.icons.Icons.Outlined.ShoppingCartCheckout, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = { onRequestProcurementAction(ProcurementLaunchAction.PURCHASE_ORDER) }, modifier = Modifier.weight(1f))
                        ErpQuickActionTile("دریافت کالا", androidx.compose.material.icons.Icons.Outlined.Inventory, ErpPalette.GreenSoft, ErpPalette.Green, onClick = { onRequestProcurementAction(ProcurementLaunchAction.GOODS_RECEIPT) }, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ErpQuickActionTile("برگشت خرید", androidx.compose.material.icons.Icons.Outlined.AssignmentReturn, ErpPalette.RedSoft, ErpPalette.Red, onClick = { onRequestProcurementAction(ProcurementLaunchAction.PURCHASE_RETURN) }, modifier = Modifier.weight(1f))
                        ErpQuickActionTile("تأمین‌کننده جدید", androidx.compose.material.icons.Icons.Outlined.PersonAdd, ErpPalette.PurpleSoft, ErpPalette.Purple, onClick = { onNavigate(AppScreen.SUPPLIERS) }, modifier = Modifier.weight(1f))
                        ErpQuickActionTile("فاکتور خرید", androidx.compose.material.icons.Icons.Outlined.ReceiptLong, ErpPalette.AmberSoft, ErpPalette.Amber, onClick = onAdd, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (state.purchaseDashboard.status == PurchaseDashboardLoadStatus.LOADED) {
                item { ErpSectionTitle("نمای کلی تأمین") }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ErpMetricCard("سفارش خرید باز", state.purchaseDashboard.openOrderDisplay, "در انتظار تکمیل", TrendDirection.NOT_AVAILABLE, Modifier.weight(1f))
                        ErpMetricCard("تأمین‌کننده فعال", state.purchaseDashboard.activeSupplierDisplay, "تأمین‌کنندگان فعال", TrendDirection.NOT_AVAILABLE, Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ErpMetricCard("درخواست خرید باز", state.purchaseDashboard.openRequisitionDisplay, "در جریان تأمین", TrendDirection.NOT_AVAILABLE, Modifier.weight(1f))
                        ErpMetricCard("در انتظار دریافت", state.purchaseDashboard.pendingReceiptDisplay, "سفارش‌های باز", TrendDirection.NOT_AVAILABLE, Modifier.weight(1f))
                    }
                }
            }

            item { ErpSectionTitle("مرکز عملیات تأمین") }
            item {
                ProcurementControlPanel(
                    state = state,
                    branches = branches,
                    currentUser = currentUser,
                    onSubmit = onSubmitRequisition,
                    onReview = onReviewRequisition,
                    onCreateOrder = onCreateOrder,
                    onCreateSplitOrders = onCreateSplitOrders,
                    onMarkOrderSent = onMarkOrderSent,
                    onAcknowledgeOrder = onAcknowledgeOrder,
                    onReceive = onReceiveGoods,
                    onReturn = onReturnGoods,
                    onSaveReplenishmentPolicy = onSaveReplenishmentPolicy,
                    onSaveSupplierOffer = onSaveSupplierOffer,
                    onSubmitSuggestedRequisition = onSubmitSuggestedRequisition,
                    onMatchInvoice = onMatchInvoice,
                    onConsumeLaunchAction = onConsumeProcurementLaunchAction,
                )
            }

            item {
                ErpSectionTitle("کنترل قیمت خرید ۳۰ روزه")
                CanonicalBranchSelector(
                    branches = activeBranches,
                    selectedBranchId = selectedPriceBranchId,
                    onBranchSelected = { selectedPriceBranchId = it },
                    label = "شعبه تحلیل قیمت",
                    tag = "purchase_price_branch_selector",
                )
            }
            if (selectedPriceBranchId != null) item {
                AdaptiveManagementList(
                    rows = state.purchasePriceControlInsights,
                    columns = listOf(
                        ManagementGridColumn("item", "کالا", 1.3f, { it.itemName }),
                        ManagementGridColumn("supplier", "تأمین‌کننده", 1.1f, { insight -> state.supplierPriceInsights.firstOrNull { it.itemId == insight.itemId }?.supplierName ?: "—" }),
                        ManagementGridColumn("current", "قیمت فعلی", 1f, { ErpDisplayFormatters.money(it.currentPriceRial) }, TextAlign.End),
                        ManagementGridColumn("previous", "قیمت قبلی", 1f, { it.previousPriceRial?.let { value -> ErpDisplayFormatters.money(value) } ?: "—" }, TextAlign.End),
                        ManagementGridColumn("average", "میانگین ۳۰روزه", 1.1f, { it.average30DayRial?.let { value -> ErpDisplayFormatters.money(value) } ?: "—" }, TextAlign.End),
                        ManagementGridColumn("variance", "انحراف", 0.9f, { it.changeBasisPointsVs30Day?.let { value -> formatPercentBasisPoints(value.toLong()) } ?: "—" }, TextAlign.End),
                    ),
                    key = { it.itemId },
                    mobileTitle = { it.itemName },
                    mobilePrimaryValue = { ErpDisplayFormatters.money(it.currentPriceRial) },
                    mobileSupporting = { insight ->
                        listOf(
                            "تأمین‌کننده" to (state.supplierPriceInsights.firstOrNull { it.itemId == insight.itemId }?.supplierName ?: "—"),
                            "قیمت قبلی" to (insight.previousPriceRial?.let { ErpDisplayFormatters.money(it) } ?: "— · داده کافی موجود نیست"),
                            "میانگین ۳۰روزه" to (insight.average30DayRial?.let { ErpDisplayFormatters.money(it) } ?: "— · داده کافی موجود نیست"),
                            "انحراف" to (insight.changeBasisPointsVs30Day?.let { formatPercentBasisPoints(it.toLong()) } ?: "— · داده کافی موجود نیست"),
                        )
                    },
                    mobileStatus = { if (it.changeBasisPointsVs30Day == null) "ناموجود" else "واقعی" },
                    rowState = { if ((it.changeBasisPointsVs30Day ?: 0) > 0) GridRowState.WARNING else GridRowState.VIEW },
                    emptyMessage = "داده واقعی قیمت خرید برای این شعبه در ۳۰ روز اخیر موجود نیست.",
                )
            }

            item { ErpSectionTitle("فاکتورهای خرید") }
            item { PremiumSearchField(state.purchaseSearch, onSearch, "شماره فاکتور یا نام تأمین‌کننده") }
            item {
                AdaptiveManagementList(
                    rows = state.purchases,
                    columns = listOf(
                        ManagementGridColumn("invoice", "فاکتور", 0.9f, { it.invoiceNo }),
                        ManagementGridColumn("supplier", "تأمین‌کننده", 1.4f, { it.supplierName }),
                        ManagementGridColumn("date", "تاریخ", 0.9f, { epochDayToPersian(it.purchaseEpochDay).display() }),
                        ManagementGridColumn("total", "مبلغ", 1.0f, { ErpDisplayFormatters.money(it.totalRial) }, TextAlign.End),
                        ManagementGridColumn("outstanding", "مانده", 1.0f, { ErpDisplayFormatters.money(it.outstandingRial) }, TextAlign.End),
                        ManagementGridColumn("status", "وضعیت", 0.9f, { purchasePaymentStatusTitle(it.paymentStatus) }),
                    ),
                    key = { it.id },
                    mobileTitle = { "فاکتور ${it.invoiceNo} · ${it.supplierName}" },
                    mobilePrimaryValue = { ErpDisplayFormatters.money(it.totalRial) },
                    mobileSupporting = {
                        listOf(
                            "تاریخ خرید" to epochDayToPersian(it.purchaseEpochDay).display(),
                            "سررسید" to epochDayToPersian(it.dueEpochDay).display(),
                            "مانده" to ErpDisplayFormatters.money(it.outstandingRial),
                        )
                    },
                    mobileStatus = { purchasePaymentStatusTitle(it.paymentStatus) },
                    rowState = {
                        when (it.paymentStatus) {
                            PurchasePaymentStatus.LEGACY_UNKNOWN -> GridRowState.ERROR
                            PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIAL -> GridRowState.WARNING
                            else -> GridRowState.VIEW
                        }
                    },
                    emptyMessage = if (state.purchaseSearch.isBlank()) "هنوز خریدی ثبت نشده است." else "نتیجه‌ای مطابق جست‌وجو وجود ندارد.",
                    onRowClick = { onSelect(it.id) },
                )
            }
        }
    }
    state.selectedPurchase?.let { details -> PurchaseDetailsDialog(details, state.busy, { onSelect(null) }, onSettle, onReverseSettlement, onReverse) }
}

private fun purchasePaymentStatusTitle(status: PurchasePaymentStatus): String = when (status) {
    PurchasePaymentStatus.PAID -> "تسویه‌شده"
    PurchasePaymentStatus.PARTIAL -> "پرداخت ناقص"
    PurchasePaymentStatus.REVERSED -> "برگشت‌خورده"
    PurchasePaymentStatus.UNPAID -> "تسویه‌نشده"
    PurchasePaymentStatus.LEGACY_UNKNOWN -> "نیازمند بررسی"
}

@Composable
private fun PurchaseHero(ui: PurchaseDashboardUi, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ErpPalette.Indigo),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui.purchaseTitle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelLarge)
            Text(ui.periodPurchaseDisplay, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetric("سفارش باز", ui.openOrderDisplay, Modifier.weight(1f))
                HeroMetric("پرداختنی", ui.supplierPayablesDisplay, Modifier.weight(1f))
                HeroMetric("درخواست باز", ui.openRequisitionDisplay, Modifier.weight(1f))
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("ثبت فاکتور خرید") }
        }
    }
}


private data class PurchaseLineForm(
    val rowId: Int,
    val itemId: Long? = null,
    val quantity: String = "",
    val unitCostRial: String = "",
)

@Composable
fun PurchaseEntryScreen(
    state: OperationsUiState,
    branches: List<BranchRecord>,
    locations: List<InventoryLocationRecord>,
    onPost: (PurchaseDraft, (PostedPurchase) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val todayEpochDay = remember { currentEpochDay() }
    var invoiceNo by remember { mutableStateOf("") }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    val activeBranches = remember(branches) { branches.filter { it.isActive } }
    var selectedBranchId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedLocationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var purchaseEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var dueEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var paymentMethod by remember { mutableStateOf(PurchasePaymentMethod.PAYABLE) }
    var emergencyReason by rememberSaveable { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var nextRowId by remember { mutableIntStateOf(2) }
    var lines by remember { mutableStateOf(listOf(PurchaseLineForm(rowId = 1))) }
    var localError by remember { mutableStateOf<String?>(null) }
    var postedPurchase by remember { mutableStateOf<PostedPurchase?>(null) }

    postedPurchase?.let { posted ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("فاکتور خرید ثبت شد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("شماره داخلی فاکتور: ${posted.invoiceNo}", fontWeight = FontWeight.Bold)
                    Text("مبلغ ثبت‌شده: ${formatMoney(posted.total.value)}")
                    Text("این شماره توسط سیستم صادر شده و پس از ثبت تغییر نمی‌کند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = onBack, modifier = Modifier.testTag("purchase_invoice_issued_done")) { Text("بازگشت به خریدها") }
            },
        )
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "ثبت فاکتور خرید",
                actionLabel = null,
                onAction = {},
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { MessageCard(it) }
            localError?.let { MessageCard(it, isError = true) }
            if (state.suppliers.isEmpty() || state.inventoryItems.isEmpty()) {
                MessageCard(
                    "برای ثبت خرید، ابتدا حداقل یک تأمین‌کننده و یک کالا ثبت کنید.",
                    isError = true,
                )
            }
            OutlinedTextField(
                value = invoiceNo,
                onValueChange = { invoiceNo = it },
                label = { Text("شماره فاکتور خودکار") },
                placeholder = { Text("هنگام ثبت صادر می‌شود") },
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SelectionField(
                label = "تأمین‌کننده",
                selectedText = state.suppliers.firstOrNull { it.id == supplierId }?.name,
                options = state.suppliers.map { it.id to it.name },
                onSelected = { id ->
                    supplierId = id
                    val terms = state.suppliers.first { it.id == id }.paymentTermsDays
                    dueEpochDay = purchaseEpochDay + terms.toLong()
                    reminderEpochDay = dueEpochDay
                },
            )
            CanonicalBranchSelector(
                branches = branches,
                selectedBranchId = selectedBranchId,
                onBranchSelected = { branchId ->
                    selectedBranchId = branchId
                    if (locations.none { it.id == selectedLocationId && it.branchId == branchId && it.active }) {
                        selectedLocationId = null
                    }
                },
                label = "شعبه خرید",
                tag = "purchase_branch_selector",
            )
            val branchLocations = locations.filter { it.active && it.branchId == selectedBranchId }
            SelectionField(
                label = "انبار/محل مقصد",
                selectedText = branchLocations.firstOrNull { it.id == selectedLocationId }?.let { "${it.code.value} — ${it.name}" },
                options = branchLocations.map { it.id to "${it.code.value} — ${it.name}" },
                onSelected = { selectedLocationId = it },
            )
            if (selectedBranchId != null && branchLocations.isEmpty()) {
                MessageCard("برای شعبه انتخاب‌شده انبار/محل مجاز فعالی وجود ندارد.", isError = true)
            }
            OutlinedTextField(
                value = emergencyReason,
                onValueChange = { emergencyReason = it.take(300) },
                label = { Text("دلیل خرید اضطراری") },
                supportingText = { Text("ثبت مستقیم خرید فقط به‌عنوان خرید اضطراری کنترل‌شده مجاز است.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            PersianDateField(
                label = "تاریخ خرید",
                epochDay = purchaseEpochDay,
                onSelected = { selectedDate ->
                    val previousTerm = dueEpochDay - purchaseEpochDay
                    purchaseEpochDay = selectedDate
                    dueEpochDay = selectedDate + previousTerm.coerceAtLeast(0)
                    if (reminderEpochDay < selectedDate) reminderEpochDay = selectedDate
                },
            )
            PersianDateField(
                label = "تاریخ تسویه",
                epochDay = dueEpochDay,
                onSelected = { dueEpochDay = it },
            )
            PaymentMethodField(paymentMethod) { paymentMethod = it }
            if (paymentMethod == PurchasePaymentMethod.PAYABLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                    )
                    Text("یادآوری تسویه")
                }
                if (reminderEnabled) {
                    PersianDateField(
                        label = "تاریخ یادآوری",
                        epochDay = reminderEpochDay,
                        onSelected = { reminderEpochDay = it },
                    )
                }
            }

            Text("اقلام فاکتور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEachIndexed { index, line ->
                PurchaseLineEditor(
                    index = index,
                    line = line,
                    items = state.inventoryItems,
                    removable = lines.size > 1,
                    onChanged = { changed ->
                        lines = lines.map { if (it.rowId == line.rowId) changed else it }
                    },
                    onRemove = {
                        lines = lines.filterNot { it.rowId == line.rowId }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    lines = lines + PurchaseLineForm(rowId = nextRowId)
                    nextRowId++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("افزودن ردیف")
            }
            Button(
                enabled = !state.busy && state.suppliers.isNotEmpty() && state.inventoryItems.isNotEmpty(),
                onClick = {
                    try {
                        localError = null
                        val draft = PurchaseDraft(
                            invoiceNo = invoiceNo.trim(),
                            supplierId = requireNotNull(supplierId) { "تأمین‌کننده را انتخاب کنید." },
                            purchaseEpochDay = purchaseEpochDay,
                            branchName = branches.firstOrNull { it.id == selectedBranchId }?.name.orEmpty(),
                            dueEpochDay = dueEpochDay,
                            paymentMethod = paymentMethod,
                            reminderEnabled = paymentMethod == PurchasePaymentMethod.PAYABLE && reminderEnabled,
                            reminderEpochDay = if (
                                paymentMethod == PurchasePaymentMethod.PAYABLE && reminderEnabled
                            ) {
                                reminderEpochDay
                            } else {
                                null
                            },
                            branchId = requireNotNull(selectedBranchId) { "یک شعبه فعال انتخاب کنید." },
                            locationId = requireNotNull(selectedLocationId) { "انبار/محل مقصد خرید را انتخاب کنید." },
                            emergencyReason = emergencyReason.trim().also { require(it.length >= 3) { "دلیل خرید اضطراری را وارد کنید." } },
                            lines = lines.map { line ->
                                PurchaseLineDraft(
                                    itemId = requireNotNull(line.itemId) {
                                        "کالای ردیف فاکتور را انتخاب کنید."
                                    },
                                    quantity = parseQuantity(line.quantity),
                                    unitCost = parseMoneyRial(line.unitCostRial),
                                )
                            },
                        )
                        onPost(draft) { posted -> postedPurchase = posted }
                    } catch (error: Exception) {
                        localError = error.message ?: "اطلاعات فاکتور کامل نیست."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "در حال ثبت…" else "ثبت نهایی خرید")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PurchaseLineEditor(
    index: Int,
    line: PurchaseLineForm,
    items: List<InventoryItemRecord>,
    removable: Boolean,
    onChanged: (PurchaseLineForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ردیف ${index + 1}", fontWeight = FontWeight.Bold)
                if (removable) TextButton(onClick = onRemove) { Text("حذف ردیف") }
            }
            SelectionField(
                label = "شرح کالا",
                selectedText = items.firstOrNull { it.id == line.itemId }?.name,
                options = items.map { it.id to "${it.name} (${it.unit})" },
                onSelected = { onChanged(line.copy(itemId = it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = { onChanged(line.copy(quantity = it)) },
                    label = { Text("تعداد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = formatMoneyInput(line.unitCostRial),
                    onValueChange = { onChanged(line.copy(unitCostRial = formatMoneyInput(it))) },
                    label = { Text("فی واحد (${currencyUnitLabel()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            val selectedItem = items.firstOrNull { it.id == line.itemId }
            val rowTotal = runCatching { parseMoneyRial(line.unitCostRial).times(parseQuantity(line.quantity)).value }.getOrDefault(0L)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${selectedItem?.name ?: "شرح کالا"} • ${selectedItem?.unit.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("جمع: ${formatMoney(rowTotal)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PaymentMethodField(
    selected: PurchasePaymentMethod,
    onSelected: (PurchasePaymentMethod) -> Unit,
) {
    val labels = listOf(
        PurchasePaymentMethod.PAYABLE to "نسیه",
        PurchasePaymentMethod.CASH to "نقدی",
        PurchasePaymentMethod.CARD to "کارتخوان",
        PurchasePaymentMethod.TRANSFER to "حواله",
    )
    SelectionField(
        label = "روش پرداخت",
        selectedText = labels.first { it.first == selected }.second,
        options = labels.mapIndexed { index, value -> index.toLong() to value.second },
        onSelected = { index -> onSelected(labels[index.toInt()].first) },
    )
}
