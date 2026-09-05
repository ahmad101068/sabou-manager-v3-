package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.crm.ReceivableAdjustmentDirection
import ir.restaurant.management.domain.crm.ReceivableAdjustmentEconomicNature
import ir.restaurant.management.domain.receivables.ReceivableCollectionDraft
import ir.restaurant.management.domain.receivables.ReceivableCollectionMethod
import ir.restaurant.management.domain.receivables.ReceivableRecord
import ir.restaurant.management.domain.receivables.ReceivableStatus
import ir.restaurant.management.domain.receivables.ReceivableType
import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.sales.CustomerRecord

@Composable
internal fun CrmScreen(
    state: CrmUiState,
    branches: List<BranchRecord>,
    onSelect: (Long?) -> Unit,
    onSaveCustomer: (Long?, CustomerDraft) -> Unit,
    onPostOpening: (Long, ReceivableAdjustmentDirection, Long, Long?, String) -> Unit,
    onPostAdjustment: (Long, ReceivableAdjustmentDirection, ReceivableAdjustmentEconomicNature, Long, Long?, String) -> Unit,
    onRefreshAging: (Long) -> Unit,
    onDetectDuplicates: (Long) -> Unit,
    onMerge: (Long, String) -> Unit,
    onSelectReceivableBranch: (Long?) -> Unit,
    onCollectReceivable: (ReceivableCollectionDraft) -> Unit,
    onOpenSales: () -> Unit,
    onOpenTreasury: () -> Unit,
    onNavigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var editingCustomer by remember { mutableStateOf<CustomerRecord?>(null) }
    var createCustomer by remember { mutableStateOf(false) }
    var accountAction by remember { mutableStateOf<String?>(null) }
    var mergeTarget by remember { mutableStateOf("") }
    var mergeReason by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var customerFilter by rememberSaveable { mutableStateOf("ALL") }
    var collectingReceivable by remember { mutableStateOf<ReceivableRecord?>(null) }
    val activeBranches = branches.filter { it.isActive }
    val selected = state.customers.firstOrNull { it.id == state.selectedCustomerId }
    val summary = crmDashboardSummary(state)
    val visibleCustomers = state.customers.filter { customer ->
        customerMatches(customer, search) && when (customerFilter) {
            "DEBTOR" -> customer.outstandingRial > 0L
            "ON_HOLD" -> customer.status == "ON_HOLD"
            "ACTIVE" -> customer.isActive && customer.status == "ACTIVE"
            else -> true
        }
    }
    val receivableSummary = receivableSummary(state.openReceivables, currentLocalEpochDay())

    LaunchedEffect(activeBranches, state.selectedReceivableBranchId) {
        if (state.selectedReceivableBranchId != null && state.selectedReceivableBranchId !in activeBranches.map { it.id }) {
            onSelectReceivableBranch(null)
        }
    }

    if (createCustomer || editingCustomer != null) {
        CustomerMasterDialog(
            customer = editingCustomer,
            busy = state.busy,
            onDismiss = { createCustomer = false; editingCustomer = null },
            onSave = { draft ->
                onSaveCustomer(editingCustomer?.id, draft)
                createCustomer = false
                editingCustomer = null
            },
        )
    }
    if (selected != null && accountAction != null) {
        CustomerAccountEntryDialog(
            title = if (accountAction == "OPENING") "ثبت مانده افتتاحیه" else "ثبت تعدیل حساب",
            confirmTag = if (accountAction == "OPENING") "crm_opening_confirm" else "crm_adjustment_confirm",
            busy = state.busy,
            adjustmentMode = accountAction == "ADJUSTMENT",
            onDismiss = { accountAction = null },
            onConfirm = { amount, direction, economicNature, businessDay, dueDay, reason ->
                if (accountAction == "OPENING") onPostOpening(amount, direction, businessDay, dueDay, reason)
                else onPostAdjustment(amount, direction, requireNotNull(economicNature), businessDay, dueDay, reason)
                accountAction = null
            },
        )
    }
    collectingReceivable?.let { receivable ->
        ReceivableCollectionDialog(
            receivable = receivable,
            partyName = state.customers.firstOrNull { it.id == receivable.partyId }?.name ?: "طرف‌حساب #${receivable.partyId}",
            busy = state.busy,
            onDismiss = { collectingReceivable = null },
            onConfirm = {
                onCollectReceivable(it)
                collectingReceivable = null
            },
        )
    }

    Scaffold(bottomBar = { ErpBottomNavigation(AppScreen.CRM, onNavigateTopLevel) }) { pagePadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pagePadding).padding(16.dp).testTag("crm_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            ErpModuleHeader(
                title = "طرف‌حساب‌ها و مطالبات",
                subtitle = if (branches.any { it.isActive }) "مطالبات بر مبنای اسناد مالی شعب؛ طرف‌حساب مستقل از هویت شعبه" else "حساب دریافتنی، اعتبار و گردش طرف‌حساب",
                trailing = { TextButton(onClick = onBack) { Text("بازگشت") } },
            )
        }
        state.message?.let { message -> item { MessageCard(message, state.isError) } }
        item {
            SectionHeading("مطالبات شعبه", "مقادیر واقعی دریافتنی و وصول بر اساس branchId")
            CanonicalBranchSelector(
                branches = activeBranches,
                selectedBranchId = state.selectedReceivableBranchId,
                onBranchSelected = onSelectReceivableBranch,
                label = "شعبه مطالبات",
                tag = "receivables_branch_selector",
            )
        }
        if (state.selectedReceivableBranchId != null) {
            item {
                ErpDashboardHero(
                    eyebrow = "کل مطالبات باز",
                    value = ErpDisplayFormatters.money(receivableSummary.totalRial),
                    caption = "صفر مقدار واقعی است؛ نبود داده فقط برای خوانش‌های ناموجود با — نمایش داده می‌شود",
                    metrics = listOf(
                        ErpKpiItem("شخصی", ErpDisplayFormatters.money(receivableSummary.personalRial)),
                        ErpKpiItem("حقوقی", ErpDisplayFormatters.money(receivableSummary.corporateRial)),
                        ErpKpiItem("سررسید گذشته", ErpDisplayFormatters.money(receivableSummary.overdueRial)),
                    ),
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactInfoRow(
                            "وصول امروز",
                            state.collectedTodayRial?.let { ErpDisplayFormatters.money(it) } ?: "— · داده کافی موجود نیست",
                        )
                        state.branchReceivableAging?.let { aging ->
                            CompactInfoRow("سن مطالبات جاری", ErpDisplayFormatters.money(aging.currentRial))
                            CompactInfoRow("۱–۷ روز", ErpDisplayFormatters.money(aging.overdue1To7Rial))
                            CompactInfoRow("۸–۳۰ روز", ErpDisplayFormatters.money(aging.overdue8To30Rial))
                            CompactInfoRow("۳۱–۶۰ روز", ErpDisplayFormatters.money(aging.overdue31To60Rial))
                            CompactInfoRow("۶۱–۹۰ روز", ErpDisplayFormatters.money(aging.overdue61To90Rial))
                            CompactInfoRow("بیش از ۹۰ روز", ErpDisplayFormatters.money(aging.overdueOver90Rial))
                        } ?: Text("سن مطالبات: — · داده کافی موجود نیست", style = MaterialTheme.typography.bodySmall)
        }
    }
}
            }
            item {
                AdaptiveManagementList(
                    rows = state.openReceivables,
                    columns = listOf(
                        ManagementGridColumn("party", "طرف‌حساب", 1.25f, { row -> state.customers.firstOrNull { it.id == row.partyId }?.name ?: "#${row.partyId}" }),
                        ManagementGridColumn("type", "نوع", 0.75f, { if (it.type == ReceivableType.PERSONAL) "شخصی" else "حقوقی" }),
                        ManagementGridColumn("outstanding", "مانده", 1f, { ErpDisplayFormatters.money(it.outstandingAmountRial) }, androidx.compose.ui.text.style.TextAlign.End),
                        ManagementGridColumn("issue", "تاریخ ایجاد", 0.9f, { epochDayToPersian(it.issueEpochDay).display() }),
                        ManagementGridColumn("due", "سررسید", 0.9f, { it.dueEpochDay?.let { day -> epochDayToPersian(day).display() } ?: "—" }),
                        ManagementGridColumn("status", "وضعیت", 0.85f, { receivableStatusTitle(it.status) }),
                    ),
                    key = { it.id },
                    mobileTitle = { row -> state.customers.firstOrNull { it.id == row.partyId }?.name ?: "طرف‌حساب #${row.partyId}" },
                    mobilePrimaryValue = { ErpDisplayFormatters.money(it.outstandingAmountRial) },
                    mobileSupporting = {
                        listOf(
                            "نوع" to if (it.type == ReceivableType.PERSONAL) "شخصی" else "حقوقی",
                            "سررسید" to (it.dueEpochDay?.let { day -> epochDayToPersian(day).display() } ?: "—"),
                        )
                    },
                    mobileStatus = { receivableStatusTitle(it.status) },
                    rowState = { if (it.isOverdue(currentLocalEpochDay())) GridRowState.WARNING else GridRowState.VIEW },
                    emptyMessage = "مطالبه بازی برای این شعبه وجود ندارد.",
                    listTestTag = "receivables_open_list",
                    rowTestTag = { "receivable_select_${it.id}" },
                    onRowClick = { collectingReceivable = it },
                )
            }
        item {
            ErpDashboardHero(
                eyebrow = "مانده دریافتنی مشتریان",
                value = ErpDisplayFormatters.money(summary.totalReceivableRial),
                caption = "مانده‌ها مستقیماً از حساب‌های واقعی مشتریان خوانده می‌شوند",
                metrics = listOf(
                    ErpKpiItem("فعال", ErpDisplayFormatters.integer(summary.activeCustomers)),
                    ErpKpiItem("بدهکار", ErpDisplayFormatters.integer(summary.debtorCustomers)),
                    ErpKpiItem("نزدیک سقف اعتبار", ErpDisplayFormatters.integer(summary.nearCreditLimitCustomers)),
                ),
            )
        }
        item {
            SectionHeading("عملیات سریع", "مسیرهای واقعی فروش و دریافت")
            ErpQuickActionsGrid(
                listOf(
                    ErpActionItem("مشتری جدید", Icons.Outlined.PersonAdd, ErpPalette.IndigoSoft, ErpPalette.Indigo, enabled = !state.busy, onClick = { createCustomer = true }),
                    ErpActionItem("فروش جدید", Icons.Outlined.AddShoppingCart, ErpPalette.TealSoft, ErpPalette.Teal, onClick = onOpenSales),
                    ErpActionItem("دریافت وجه", Icons.Outlined.CallReceived, ErpPalette.GreenSoft, ErpPalette.Green, onClick = onOpenTreasury),
                ),
            )
        }
        item {
            SectionHeading("مشتریان", "جست‌وجو و فیلتر روی اطلاعات ثبت‌شده")
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().testTag("crm_search"),
                label = { Text("جست‌وجوی نام، کد، تلفن یا شعبه") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = "جست‌وجو") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "همه", "ACTIVE" to "فعال", "DEBTOR" to "بدهکار", "ON_HOLD" to "متوقف").forEach { (key, label) ->
                    FilterChip(selected = customerFilter == key, onClick = { customerFilter = key }, label = { Text(label) })
                }
            }
        }
        item {
            AdaptiveManagementList(
                rows = visibleCustomers,
                columns = listOf(
                    ManagementGridColumn("code", "کد", 0.75f, { it.customerCode }),
                    ManagementGridColumn("party", "طرف‌حساب", 1.5f, { it.name }),
                    ManagementGridColumn("outstanding", "مانده", 1.1f, { ErpDisplayFormatters.money(it.outstandingRial) }, androidx.compose.ui.text.style.TextAlign.End),
                    ManagementGridColumn("limit", "سقف اعتبار", 1.1f, { ErpDisplayFormatters.money(it.creditLimitRial) }, androidx.compose.ui.text.style.TextAlign.End),
                    ManagementGridColumn("terms", "مهلت", 0.7f, { "${it.paymentTermsDays} روز" }),
                    ManagementGridColumn("status", "وضعیت", 0.8f, { customerStatusTitle(it.status) }),
                ),
                key = { it.id },
                mobileTitle = { "${it.customerCode} · ${it.name}" },
                mobilePrimaryValue = { ErpDisplayFormatters.money(it.outstandingRial) },
                mobileSupporting = {
                    listOf(
                        "سقف اعتبار" to ErpDisplayFormatters.money(it.creditLimitRial),
                        "تماس" to it.phone.ifBlank { it.mobile }.ifBlank { "—" },
                        "مهلت پرداخت" to "${it.paymentTermsDays} روز",
                    )
                },
                mobileStatus = { customerStatusTitle(it.status) },
                rowState = {
                    when {
                        it.status == "ON_HOLD" -> GridRowState.WARNING
                        !it.isActive || it.status == "INACTIVE" -> GridRowState.ERROR
                        else -> GridRowState.VIEW
                    }
                },
                emptyMessage = if (state.customers.isEmpty()) "هنوز طرف‌حسابی ثبت نشده است." else "طرف‌حساب مطابق فیلتر پیدا نشد.",
                listTestTag = "crm_customer_list",
                rowTestTag = { "crm_select_${it.id}" },
                onRowClick = { onSelect(it.id) },
            )
        }
        if (selected != null) {
            item {
                Text("حساب ${selected.name}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !state.busy, onClick = { onRefreshAging(selected.id) }) { Text("محاسبه سن مطالبات") }
                    OutlinedButton(enabled = !state.busy, onClick = { onDetectDuplicates(selected.id) }) { Text("بررسی تکراری") }
                    OutlinedButton(enabled = !state.busy, onClick = { editingCustomer = selected }) { Text("ویرایش اطلاعات") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !state.busy,
                        modifier = Modifier.testTag("crm_opening_action"),
                        onClick = { accountAction = "OPENING" },
                    ) { Text("مانده افتتاحیه") }
                    OutlinedButton(
                        enabled = !state.busy,
                        modifier = Modifier.testTag("crm_adjustment_action"),
                        onClick = { accountAction = "ADJUSTMENT" },
                    ) { Text("تعدیل حساب") }
                }
            }
            state.aging?.let { aging ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("سن مطالبات", style = MaterialTheme.typography.titleSmall)
                            Text("جاری: ${formatMoney(aging.currentRial)}")
                            Text("۱–۳۰: ${formatMoney(aging.days1To30Rial)} · ۳۱–۶۰: ${formatMoney(aging.days31To60Rial)}")
                            Text("۶۱–۹۰: ${formatMoney(aging.days61To90Rial)} · بیش از ۹۰: ${formatMoney(aging.over90Rial)}")
                            Text("کل: ${formatMoney(aging.totalRial)}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            item { Text("دفتر دریافتنی", style = MaterialTheme.typography.titleMedium) }
            if (state.ledger.isEmpty()) {
                item { Text("گردشی برای این مشتری ثبت نشده است.") }
            } else {
                items(state.ledger, key = { "crm-ledger-${it.id}" }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${row.entryType} · ${row.reference}")
                            Text("بدهکار ${ErpDisplayFormatters.money(row.debitRial)} · بستانکار ${ErpDisplayFormatters.money(row.creditRial)} · ${ErpDisplayFormatters.activityDateTime(row.businessEpochDay, null)}", style = MaterialTheme.typography.bodySmall)
                            row.dueEpochDay?.let { Text("سررسید ${epochDayToPersian(it).display()}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (state.duplicateCandidates.isNotEmpty()) {
                item { Text("مشتریان مشابه", style = MaterialTheme.typography.titleMedium) }
                items(state.duplicateCandidates, key = { "duplicate-${it.id}" }) { candidate ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { mergeTarget = candidate.id.toString() },
                    ) { Text("${candidate.customerCode} · ${candidate.name} · ${candidate.phone}") }
                }
                item {
                    OutlinedTextField(mergeTarget, { mergeTarget = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("شناسه مشتری مقصد") })
                    OutlinedTextField(mergeReason, { mergeReason = it }, Modifier.fillMaxWidth(), label = { Text("دلیل ادغام") })
                    Button(
                        enabled = !state.busy && mergeTarget.toLongOrNull() != null && mergeReason.trim().length >= 3,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onMerge(mergeTarget.toLongOrNull() ?: 0L, mergeReason) },
                    ) { Text("ادغام کنترل‌شده و انتقال مراجع") }
                }
            }
        }
    }
}
}

private data class BranchReceivableSummary(
    val totalRial: Long,
    val personalRial: Long,
    val corporateRial: Long,
    val overdueRial: Long,
)

private fun receivableSummary(rows: List<ReceivableRecord>, todayEpochDay: Long): BranchReceivableSummary {
    fun total(predicate: (ReceivableRecord) -> Boolean): Long = rows.asSequence()
        .filter(predicate)
        .fold(0L) { sum, row -> Math.addExact(sum, row.outstandingAmountRial) }
    return BranchReceivableSummary(
        totalRial = total { true },
        personalRial = total { it.type == ReceivableType.PERSONAL },
        corporateRial = total { it.type == ReceivableType.CORPORATE },
        overdueRial = total { it.isOverdue(todayEpochDay) },
    )
}

@Composable
private fun ReceivableCollectionDialog(
    receivable: ReceivableRecord,
    partyName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ReceivableCollectionDraft) -> Unit,
) {
    var amount by remember(receivable.id) { mutableStateOf(receivable.outstandingAmountRial.toString()) }
    var method by remember(receivable.id) { mutableStateOf(ReceivableCollectionMethod.CASH) }
    var reference by remember(receivable.id) { mutableStateOf("") }
    var businessDay by remember(receivable.id) { mutableStateOf(currentLocalEpochDay()) }
    val commandId = remember(receivable.id) { GlobalId.new().value }
    val parsedAmount = parseMoneyInputOrNull(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وصول مطالبه $partyName") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مانده: ${ErpDisplayFormatters.money(receivable.outstandingAmountRial)}")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth().testTag("receivable_collection_amount"),
                    label = { Text("مبلغ وصول (ریال)") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReceivableCollectionMethod.entries.forEach { option ->
                        FilterChip(
                            selected = method == option,
                            onClick = { method = option },
                            label = { Text(receivableCollectionMethodTitle(option)) },
                        )
                    }
                }
                PersianDateField("تاریخ وصول", businessDay) { businessDay = it }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مرجع اختیاری") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && parsedAmount != null && parsedAmount > 0L && parsedAmount <= receivable.outstandingAmountRial,
                modifier = Modifier.testTag("receivable_collection_confirm"),
                onClick = {
                    onConfirm(
                        ReceivableCollectionDraft(
                            commandId = commandId,
                            receivableId = receivable.id,
                            amountRial = parsedAmount ?: 0L,
                            method = method,
                            treasuryAccountId = method.canonicalTreasuryAccountId,
                            reference = reference,
                            businessEpochDay = businessDay,
                        ),
                    )
                },
            ) { Text("ثبت وصول") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun receivableStatusTitle(status: ReceivableStatus): String = when (status) {
    ReceivableStatus.OPEN -> "باز"
    ReceivableStatus.PARTIALLY_PAID -> "بخشی وصول‌شده"
    ReceivableStatus.PAID -> "وصول‌شده"
    ReceivableStatus.VOIDED -> "باطل"
}

private fun receivableCollectionMethodTitle(method: ReceivableCollectionMethod): String = when (method) {
    ReceivableCollectionMethod.CASH -> "نقد"
    ReceivableCollectionMethod.CARD -> "کارت"
    ReceivableCollectionMethod.BANK_TRANSFER -> "انتقال بانکی"
}

@Composable
private fun CustomerMasterDialog(
    customer: CustomerRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (CustomerDraft) -> Unit,
) {
    var name by remember(customer?.id) { mutableStateOf(customer?.name.orEmpty()) }
    var phone by remember(customer?.id) { mutableStateOf(customer?.phone.orEmpty()) }
    var mobile by remember(customer?.id) { mutableStateOf(customer?.mobile.orEmpty()) }
    var nationalId by remember(customer?.id) { mutableStateOf(customer?.nationalId.orEmpty()) }
    var address by remember(customer?.id) { mutableStateOf(customer?.address.orEmpty()) }
    val legacyBranchLabel = customer?.branch.orEmpty()
    var creditLimit by remember(customer?.id) { mutableStateOf(customer?.creditLimitRial?.toString().orEmpty()) }
    var paymentTerms by remember(customer?.id) { mutableStateOf(customer?.paymentTermsDays?.toString() ?: "0") }
    var status by remember(customer?.id) { mutableStateOf(customer?.status ?: "ACTIVE") }
    var notes by remember(customer?.id) { mutableStateOf(customer?.notes.orEmpty()) }
    val parsedCredit = if (creditLimit.isBlank()) 0L else parseMoneyInputOrNull(creditLimit)
    val parsedTerms = paymentTerms.toIntOrNull()
    val valid = name.trim().length >= 2 && parsedCredit != null && parsedCredit >= 0L && parsedTerms != null && parsedTerms in 0..3650 && status.trim().uppercase() in setOf("ACTIVE", "ON_HOLD", "INACTIVE")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (customer == null) "مشتری جدید" else "ویرایش ${customer.customerCode}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(customer?.customerCode ?: "پس از ثبت خودکار", {}, Modifier.fillMaxWidth(), enabled = false, label = { Text("کد مشتری") })
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("crm_customer_name"), label = { Text("نام") })
                OutlinedTextField(phone, { phone = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("crm_customer_phone"), label = { Text("تلفن") })
                OutlinedTextField(mobile, { mobile = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("crm_customer_mobile"), label = { Text("موبایل") })
                OutlinedTextField(nationalId, { nationalId = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("crm_customer_national_id"), label = { Text("کد ملی/شناسه") })
                OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth().testTag("crm_customer_address"), label = { Text("آدرس") })
                if (legacyBranchLabel.isNotBlank()) {
                    OutlinedTextField(
                        legacyBranchLabel,
                        {},
                        Modifier.fillMaxWidth().testTag("crm_customer_branch_legacy"),
                        enabled = false,
                        label = { Text("شعبه تاریخی (فقط نمایش)") },
                        supportingText = { Text("هویت شعبه در مطالبات جدید از branchId سند مالی تعیین می‌شود.") },
                    )
                }
                OutlinedTextField(creditLimit, { creditLimit = it }, Modifier.fillMaxWidth().testTag("crm_customer_credit_limit"), label = { Text("سقف اعتبار (ریال)") })
                OutlinedTextField(paymentTerms, { paymentTerms = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("crm_customer_payment_terms"), label = { Text("مهلت پرداخت (روز)") })
                OutlinedTextField(status, { status = it.uppercase() }, Modifier.fillMaxWidth().testTag("crm_customer_status"), label = { Text("وضعیت (فعال / متوقف / غیرفعال)") })
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().testTag("crm_customer_notes"), label = { Text("یادداشت") })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && valid,
                modifier = Modifier.testTag("crm_customer_save"),
                onClick = {
                    onSave(
                        CustomerDraft(
                            name = name,
                            phone = phone,
                            nationalId = nationalId,
                            creditLimitRial = parsedCredit ?: 0L,
                            notes = notes,
                            mobile = mobile,
                            address = address,
                            branch = legacyBranchLabel,
                            paymentTermsDays = parsedTerms ?: 0,
                            status = status,
                        ),
                    )
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun CustomerAccountEntryDialog(
    title: String,
    confirmTag: String,
    busy: Boolean,
    adjustmentMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, ReceivableAdjustmentDirection, ReceivableAdjustmentEconomicNature?, Long, Long?, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(ReceivableAdjustmentDirection.DEBIT) }
    var economicNature by remember { mutableStateOf(ReceivableAdjustmentEconomicNature.SALES_CORRECTION) }
    var businessDay by remember { mutableStateOf(currentLocalEpochDay()) }
    var dueDay by remember { mutableStateOf(currentLocalEpochDay()) }
    var reason by remember { mutableStateOf("") }
    val parsedAmount = parseMoneyInputOrNull(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth().testTag("crm_account_amount"), label = { Text("مبلغ (ریال)") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { direction = ReceivableAdjustmentDirection.DEBIT; if (economicNature == ReceivableAdjustmentEconomicNature.OPERATING_EXPENSE) economicNature = ReceivableAdjustmentEconomicNature.SALES_CORRECTION }) { Text(if (direction == ReceivableAdjustmentDirection.DEBIT) "✓ بدهکار" else "بدهکار") }
                    OutlinedButton(onClick = { direction = ReceivableAdjustmentDirection.CREDIT; if (economicNature == ReceivableAdjustmentEconomicNature.OTHER_INCOME) economicNature = ReceivableAdjustmentEconomicNature.SALES_CORRECTION }) { Text(if (direction == ReceivableAdjustmentDirection.CREDIT) "✓ بستانکار" else "بستانکار") }
                }
                if (adjustmentMode) {
                    Text("ماهیت اقتصادی تعدیل", style = MaterialTheme.typography.labelLarge)
                    val allowedNatures = if (direction == ReceivableAdjustmentDirection.DEBIT) {
                        listOf(ReceivableAdjustmentEconomicNature.SALES_CORRECTION, ReceivableAdjustmentEconomicNature.OTHER_INCOME)
                    } else {
                        listOf(ReceivableAdjustmentEconomicNature.SALES_CORRECTION, ReceivableAdjustmentEconomicNature.OPERATING_EXPENSE)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allowedNatures.forEach { nature ->
                            FilterChip(
                                selected = economicNature == nature,
                                onClick = { economicNature = nature },
                                label = { Text(receivableAdjustmentNatureTitle(nature)) },
                            )
                        }
                    }
                }
                PersianDateField("تاریخ ثبت", businessDay) { businessDay = it; if (dueDay < it) dueDay = it }
                PersianDateField("سررسید", dueDay) { dueDay = it }
                OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth().testTag("crm_account_reason"), label = { Text("دلیل") })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && parsedAmount != null && parsedAmount > 0 && dueDay >= businessDay && reason.trim().length >= 3,
                modifier = Modifier.testTag(confirmTag),
                onClick = { onConfirm(parsedAmount ?: 0L, direction, economicNature.takeIf { adjustmentMode }, businessDay, dueDay, reason) },
            ) { Text("ثبت با سند حسابداری") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}


private fun receivableAdjustmentNatureTitle(nature: ReceivableAdjustmentEconomicNature): String = when (nature) {
    ReceivableAdjustmentEconomicNature.SALES_CORRECTION -> "اصلاح فروش"
    ReceivableAdjustmentEconomicNature.OTHER_INCOME -> "سایر درآمد"
    ReceivableAdjustmentEconomicNature.OPERATING_EXPENSE -> "هزینه عملیاتی"
}

private fun customerStatusTitle(status: String): String = when (status) {
    "ACTIVE" -> "فعال"
    "ON_HOLD" -> "متوقف"
    "INACTIVE" -> "غیرفعال"
    else -> status
}
