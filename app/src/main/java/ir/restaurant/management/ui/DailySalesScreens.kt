package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesItem
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.DailySalesStatus
import ir.restaurant.management.domain.sales.SalesSettlementType
import ir.restaurant.management.domain.sales.CustomerRecord
import ir.restaurant.management.domain.sales.PartyFinancialType

@Composable
fun DailySalesScreen(
    state: DailySalesUiState,
    onSearch: (String) -> Unit,
    onSaveDraft: (Long?, Long, Long, Long, Long, Long, Long, Long, Long, String, List<DailyMenuSaleDraft>, List<DailySalesSettlementDraft>, () -> Unit) -> Unit,
    onConfirm: (Long, () -> Unit) -> Unit,
    onPostConfirmed: (Long, () -> Unit) -> Unit,
    onReverse: (Long, Long, String, () -> Unit) -> Unit,
    onCloseDay: (Long, Long, String, String, () -> Unit) -> Unit,
    onReopenDay: (Long, Long, String, String, () -> Unit) -> Unit,
    onSetReportRange: (Long, Long) -> Unit,
    onNavigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var showEntry by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<DailySalesItem?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var reversalTarget by remember { mutableStateOf<DailySalesItem?>(null) }
    var closeTarget by remember { mutableStateOf<DailySalesItem?>(null) }
    var reopenTarget by remember { mutableStateOf<DailySalesItem?>(null) }
    Scaffold(
        topBar = { ProfessionalTopBar("ثبت فروش روزانه", "پیش‌نویس ← تأییدشده ← ثبت نهایی، با پنج روش تسویه", onBack) },
        bottomBar = { ErpBottomNavigation(AppScreen.SALES, onNavigateTopLevel) },
        floatingActionButton = { Button(onClick = { editingTarget = null; showEntry = true }, enabled = !state.busy) { Text("پیش‌نویس فروش") } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { item { MessageCard(it) } }
            state.error?.let { item { MessageCard(it, isError = true) } }
            item { PremiumHero("فروش ۳۰ روز", formatMoney(state.activeSalesRial), "نسیه جدید ثبت‌شده ${formatMoney(state.receivablesRial)}") }
            item { OutlinedTextField(search, { search = it; onSearch(it) }, label = { Text("جست‌وجوی یادداشت یا تاریخ") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedButton(onClick = { showReport = true }, modifier = Modifier.fillMaxWidth()) { Text("گزارش بازه‌ای و مهندسی منو") } }
            if (state.sales.isEmpty()) item { EmptyStatePanel("هنوز فروش روزانه‌ای ثبت نشده", "ابتدا پیش‌نویس بسازید، سپس تأیید و ثبت نهایی کنید.") }
            items(state.sales, key = { it.id }) { sale ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(epochDayToPersian(sale.businessEpochDay).display(), fontWeight = FontWeight.ExtraBold)
                            StatusPill(if (sale.isReversed) "VOIDED" else sale.status.name)
                        }
                        val branchLabel = state.branches.firstOrNull { it.id == sale.branchId }?.name ?: "#${sale.branchId}"
                        Text("شعبه $branchLabel · فروش خالص ${formatMoney(sale.netSalesRial)} · درآمد ${formatMoney(sale.revenueRial)} · مالیات ${formatMoney(sale.taxRial)} · قابل‌تسویه ${formatMoney(sale.amountToSettleRial)}")
                        Text("نقد ${formatMoney(sale.cashRial)} · کارت ${formatMoney(sale.cardRial)} · حواله ${formatMoney(sale.transferRial)}", style = MaterialTheme.typography.bodySmall)
                        val personal = sale.settlements.filter { it.type == SalesSettlementType.PERSONAL_CREDIT }.sumOf { it.amountRial }
                        val corporate = sale.settlements.filter { it.type == SalesSettlementType.CORPORATE_CREDIT }.sumOf { it.amountRial }
                        if (personal > 0 || corporate > 0) Text("نسیه ${formatMoney(personal)} · شرکتی ${formatMoney(corporate)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        if (sale.settlements.size > 3) Text("${sale.settlements.size} ردیف تسویه؛ چند ردیف از یک نوع مجاز است.", style = MaterialTheme.typography.bodySmall)
                        if (sale.notes.isNotBlank()) Text(sale.notes, style = MaterialTheme.typography.bodySmall)
                        if (sale.isReversed) Text("برگشت: ${sale.reversalReason}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        when {
                            sale.isLegacyArchive || sale.isReversed -> Unit
                            sale.status == DailySalesStatus.DRAFT -> {
                                OutlinedButton(onClick = { editingTarget = sale; showEntry = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("ویرایش پیش‌نویس و تسویه‌ها") }
                                Button(onClick = { onConfirm(sale.id) {} }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("تأیید فروش") }
                            }
                            sale.status == DailySalesStatus.CONFIRMED -> {
                                Button(onClick = { onPostConfirmed(sale.id) {} }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("ثبت نهایی و ایجاد آثار مالی") }
                            }
                            sale.status == DailySalesStatus.POSTED -> {
                                if (sale.isClosed) {
                                    Text("بسته‌شده توسط ${sale.closedBy ?: "مدیر"}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { reopenTarget = sale }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("بازگشایی کنترل‌شده (مالک)") }
                                } else {
                                    OutlinedButton(onClick = { reversalTarget = sale }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("برگشت فروش") }
                                    Button(onClick = { closeTarget = sale }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("بستن و امضای روز فروش") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showEntry) DailySalesEntryDialog(
        menuItems = state.menuItems,
        customers = state.customers,
        branches = state.branches,
        locations = state.locations,
        initial = editingTarget,
        commandMessage = state.error,
        onDismiss = { showEntry = false; editingTarget = null },
        onSave = onSaveDraft,
    )
    if (showReport) DailySalesReportDialog(state, onDismiss = { showReport = false }, onApply = onSetReportRange)
    reversalTarget?.let { sale -> DailySalesReversalDialog(sale, state.busy, { reversalTarget = null }) { epochDay, reason -> onReverse(sale.id, epochDay, reason) { reversalTarget = null } } }
    closeTarget?.let { sale -> SalesDayCloseDialog(sale, state.busy, state.message, { closeTarget = null }) { note, pin -> onCloseDay(sale.branchId, sale.businessEpochDay, note, pin) { closeTarget = null } } }
    reopenTarget?.let { sale -> SalesDayReopenDialog(sale, state.busy, state.message, { reopenTarget = null }) { reason, pin -> onReopenDay(sale.branchId, sale.businessEpochDay, reason, pin) { reopenTarget = null } } }
}

@Composable
private fun SalesDayReopenDialog(sale: DailySalesItem, busy: Boolean, message: String?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var reason by remember(sale.id) { mutableStateOf("") }
    var pin by remember(sale.id) { mutableStateOf("") }
    var submitted by remember(sale.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازگشایی روز فروش") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("قفل دیتابیس این روز با مجوز مالک باز می‌شود و رویداد مستقل حسابرسی و همگام‌سازی ثبت خواهد شد.")
            Text(epochDayToPersian(sale.businessEpochDay).display(), fontWeight = FontWeight.Bold)
            OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل اجباری بازگشایی") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (submitted) message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SensitivePinField(pin, { pin = it })
        } },
        confirmButton = { Button(enabled = !busy && reason.trim().length >= 5 && pin.length in 6..12, onClick = { val submittedPin = pin; pin = ""; submitted = true; onConfirm(reason.trim(), submittedPin) }) { Text("بازگشایی با ثبت رویداد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SalesDayCloseDialog(sale: DailySalesItem, busy: Boolean, message: String?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var note by remember(sale.id) { mutableStateOf("") }
    var pin by remember(sale.id) { mutableStateOf("") }
    var submitted by remember(sale.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بستن روز فروش") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("پس از تأیید، فروش، بهای تمام‌شده و روش‌های دریافت این روز snapshot می‌شوند و روز دیگر قابل برگشت یا ثبت مجدد نیست.")
            Text("${epochDayToPersian(sale.businessEpochDay).display()} · فروش خالص ${formatMoney(sale.netSalesRial)} · سود ناخالص ${formatMoney(SignedLongMath.subtract(sale.revenueRial, sale.theoreticalCostRial))}", fontWeight = FontWeight.Bold)
            OutlinedTextField(note, { note = it }, label = { Text("یادداشت مدیر (اختیاری)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (submitted) message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SensitivePinField(pin, { pin = it })
        } },
        confirmButton = { Button(enabled = !busy && pin.length in 6..12, onClick = { val submittedPin = pin; pin = ""; submitted = true; onConfirm(note.trim(), submittedPin) }) { Text("بستن قطعی") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DailySalesReversalDialog(
    sale: DailySalesItem,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var reversalDay by remember(sale.id) { mutableLongStateOf(maxOf(currentEpochDay(), sale.businessEpochDay)) }
    var reason by remember(sale.id) { mutableStateOf("") }
    var error by remember(sale.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت فروش روزانه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تمام آثار موجودی، لات و اسناد درآمد و بهای تمام‌شده خنثی می‌شوند. سپس همین روز را می‌توانید با ارقام صحیح دوباره ثبت کنید.")
                Text("روز فروش: ${epochDayToPersian(sale.businessEpochDay).display()} · مبلغ: ${formatMoney(sale.netSalesRial)}", fontWeight = FontWeight.Bold)
                PersianDateField("تاریخ برگشت", reversalDay, { reversalDay = it })
                OutlinedTextField(reason, { reason = it }, label = { Text("دلیل برگشت") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    when {
                        reversalDay < sale.businessEpochDay -> error = "تاریخ برگشت نمی‌تواند قبل از روز فروش باشد."
                        reason.trim().length !in 3..200 -> error = "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد."
                        else -> onConfirm(reversalDay, reason.trim())
                    }
                },
            ) { Text("برگشت قطعی") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private data class SettlementEditorRow(
    val type: SalesSettlementType,
    val amount: String = "",
    val partyId: Long? = null,
    val dueEpochDay: Long? = null,
    val contractId: String = "",
    val reference: String = "",
    val note: String = "",
)

@Composable
private fun DailySalesEntryDialog(
    menuItems: List<MenuItem>,
    customers: List<CustomerRecord>,
    branches: List<BranchRecord>,
    locations: List<InventoryLocationRecord>,
    initial: DailySalesItem?,
    commandMessage: String?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long, Long, Long, Long, Long, Long, Long, Long, String, List<DailyMenuSaleDraft>, List<DailySalesSettlementDraft>, () -> Unit) -> Unit,
) {
    var day by remember(initial?.id) { mutableLongStateOf(initial?.businessEpochDay ?: currentEpochDay()) }
    var selectedBranchId by remember(initial?.id, branches) {
        mutableStateOf(initial?.branchId ?: branches.singleOrNull()?.id)
    }
    var selectedLocationId by remember(initial?.id, locations) {
        mutableStateOf(initial?.locationId ?: locations.filter { it.branchId == (initial?.branchId ?: branches.singleOrNull()?.id) }.singleOrNull()?.id)
    }
    var locationExpanded by remember(initial?.id) { mutableStateOf(false) }
    var grossHeader by remember(initial?.id) { mutableStateOf(initial?.grossSalesRial?.takeIf { it > 0 }?.toString().orEmpty()) }
    var discount by remember(initial?.id) { mutableStateOf(initial?.discountRial?.takeIf { it > 0 }?.toString().orEmpty()) }
    var returns by remember(initial?.id) { mutableStateOf(initial?.returnRial?.takeIf { it > 0 }?.toString().orEmpty()) }
    var service by remember(initial?.id) { mutableStateOf(initial?.serviceRial?.takeIf { it > 0 }?.toString().orEmpty()) }
    var tax by remember(initial?.id) { mutableStateOf(initial?.taxRial?.takeIf { it > 0 }?.toString().orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var error by remember(initial?.id) { mutableStateOf<String?>(null) }
    val quantities = remember(initial?.id) { mutableStateMapOf<Long, String>().apply { initial?.profitabilityLines?.forEach { line -> line.menuItemId?.let { put(it, formatQuantity(line.quantityMicros)) } } } }
    val amounts = remember(initial?.id) { mutableStateMapOf<Long, String>().apply { initial?.profitabilityLines?.forEach { line -> line.menuItemId?.let { put(it, line.salesRial?.toString().orEmpty()) } } } }
    val settlementRows = remember(initial?.id) { mutableStateListOf<SettlementEditorRow>().apply {
        initial?.settlements?.forEach { row -> add(SettlementEditorRow(row.type, row.amountRial.toString(), row.partyId, row.dueEpochDay, row.contractId?.toString().orEmpty(), row.referenceNumber.orEmpty(), row.note.orEmpty())) }
    } }
    val branchId = selectedBranchId
    val locationId = selectedLocationId
    val branchLocations = locations.filter { it.branchId == branchId && it.active }
    val gross = parseMoneyInputOrNull(grossHeader) ?: -1L
    val discountRial = parseMoneyInputOrNull(discount) ?: 0L
    val returnRial = parseMoneyInputOrNull(returns) ?: 0L
    val serviceRial = parseMoneyInputOrNull(service) ?: 0L
    val taxRial = parseMoneyInputOrNull(tax) ?: 0L
    val invalidMoneyInput = (amounts.values + listOf(grossHeader, discount, returns, service, tax) + settlementRows.map { it.amount })
        .any { it.isNotBlank() && parseMoneyInputOrNull(it) == null }
    val lines = menuItems.mapNotNull { menu ->
        val quantity = try { parseQuantity(quantities[menu.id].orEmpty()).value } catch (_: IllegalArgumentException) { 0L }
        if (quantity <= 0) null else DailyMenuSaleDraft(menu.id, quantity, parseMoneyInputOrNull(amounts[menu.id].orEmpty()))
    }
    val netSales = try { SignedLongMath.subtract(SignedLongMath.subtract(gross, discountRial), returnRial) } catch (_: ArithmeticException) { -1L }
    val revenue = try { SignedLongMath.add(netSales, serviceRial) } catch (_: ArithmeticException) { -1L }
    val amountToSettle = try { SignedLongMath.add(revenue, taxRial) } catch (_: ArithmeticException) { -1L }
    val settlementTotal = settlementRows.mapNotNull { parseMoneyInputOrNull(it.amount) }.fold(0L, SignedLongMath::add)
    val settlementDifference = try { SignedLongMath.subtract(settlementTotal, amountToSettle) } catch (_: ArithmeticException) { null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "پیش‌نویس فروش روزانه" else "ویرایش پیش‌نویس فروش") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { PersianDateField("روز فروش", day, { day = it }) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CanonicalBranchSelector(
                            branches = branches,
                            selectedBranchId = selectedBranchId,
                            onBranchSelected = { nextBranch ->
                                selectedBranchId = nextBranch
                                if (locations.none { it.id == selectedLocationId && it.branchId == nextBranch }) {
                                    selectedLocationId = locations.filter { it.branchId == nextBranch && it.active }.singleOrNull()?.id
                                }
                            },
                            enabled = initial == null,
                            label = "شعبه فروش",
                            tag = "daily_sales_branch_selector",
                        )
                        if (branches.isEmpty()) Text("برای ثبت فروش ابتدا یک شعبه فعال در بخش مدیریت شعب تعریف کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { locationExpanded = true }, enabled = initial == null && branchId != null, modifier = Modifier.fillMaxWidth()) {
                            Text(branchLocations.firstOrNull { it.id == locationId }?.let { "انبار مصرف: ${it.name}" } ?: "انتخاب انبار/مکان مصرف")
                        }
                        DropdownMenu(expanded = locationExpanded, onDismissRequest = { locationExpanded = false }) {
                            branchLocations.forEach { location ->
                                DropdownMenuItem(text = { Text("${location.name} · ${location.code.value}") }, onClick = { selectedLocationId = location.id; locationExpanded = false })
                            }
                        }
                        if (branchId != null && branchLocations.isEmpty()) Text("برای شعبه انتخاب‌شده انبار مجاز و فعال وجود ندارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                item { OutlinedTextField(grossHeader, { grossHeader = formatMoneyInput(it) }, label = { Text("فروش ناخالص سربرگ") }, supportingText = { Text("مستقل از مبلغ ردیف‌های منو؛ مبلغ ردیف‌ها اختیاری است.") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { Text("ترکیب فروش (مبلغ هر آیتم اختیاری)", fontWeight = FontWeight.ExtraBold) }
                items(menuItems, key = { it.id }) { menu ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${menu.name} · ${menu.category}", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(quantities[menu.id].orEmpty(), { quantities[menu.id] = it }, label = { Text("تعداد") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(amounts[menu.id].orEmpty(), { amounts[menu.id] = formatMoneyInput(it) }, label = { Text("فروش آیتم (اختیاری)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    } }
                }
                item { HorizontalDivider() }
                item { OutlinedTextField(discount, { discount = formatMoneyInput(it) }, label = { Text("تخفیف کل") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { OutlinedTextField(returns, { returns = formatMoneyInput(it) }, label = { Text("برگشت فروش") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { OutlinedTextField(service, { service = formatMoneyInput(it) }, label = { Text("حق سرویس") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { OutlinedTextField(tax, { tax = formatMoneyInput(it) }, label = { Text("مالیات و عوارض") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { Text("روش‌های تسویه (چند ردیف از هر نوع مجاز است)", fontWeight = FontWeight.ExtraBold) }
                items(settlementRows.size) { index -> SettlementEditorCard(index, settlementRows[index], day, customers, { updated -> settlementRows[index] = updated }, { settlementRows.removeAt(index) }) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { settlementRows.add(SettlementEditorRow(SalesSettlementType.CASH)) }) { Text("+ نقد") }
                        TextButton(onClick = { settlementRows.add(SettlementEditorRow(SalesSettlementType.CARD)) }) { Text("+ کارت") }
                        TextButton(onClick = { settlementRows.add(SettlementEditorRow(SalesSettlementType.BANK_TRANSFER)) }) { Text("+ بانک") }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { settlementRows.add(SettlementEditorRow(SalesSettlementType.PERSONAL_CREDIT, dueEpochDay = day)) }) { Text("+ نسیه") }
                        TextButton(onClick = { settlementRows.add(SettlementEditorRow(SalesSettlementType.CORPORATE_CREDIT, dueEpochDay = day)) }) { Text("+ شرکتی") }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactInfoRow("فروش ناخالص", formatMoney(gross.coerceAtLeast(0)))
                            CompactInfoRow("تخفیف", formatMoney(discountRial))
                            CompactInfoRow("برگشت فروش", formatMoney(returnRial))
                            CompactInfoRow("فروش خالص", formatMoney(netSales.coerceAtLeast(0)))
                            CompactInfoRow("درآمد حق سرویس", formatMoney(serviceRial))
                            CompactInfoRow("مالیات و عوارض", formatMoney(taxRial))
                            CompactInfoRow("درآمد", formatMoney(revenue.coerceAtLeast(0)))
                            CompactInfoRow("مبلغ قابل تسویه", formatMoney(amountToSettle.coerceAtLeast(0)))
                            CompactInfoRow("جمع تسویه‌ها", formatMoney(settlementTotal))
                            CompactInfoRow(
                                "مغایرت",
                                settlementDifference?.let(::formatMoney) ?: "—",
                                settlementDifference != 0L,
                            )
                        }
                    }
                }
                item { OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth()) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                commandMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val drafts = settlementRows.mapNotNull { row ->
                    val amount = parseMoneyInputOrNull(row.amount) ?: return@mapNotNull null
                    if (amount <= 0) return@mapNotNull null
                    DailySalesSettlementDraft(row.type, amount, partyId = row.partyId, dueEpochDay = row.dueEpochDay, contractId = row.contractId.trim().takeIf { it.isNotEmpty() }?.toLongOrNull(), referenceNumber = row.reference.ifBlank { null }, note = row.note.ifBlank { null })
                }
                val invalidCredit = settlementRows.any { row ->
                    val amount = parseMoneyInputOrNull(row.amount) ?: 0L
                    amount > 0 && (row.type == SalesSettlementType.PERSONAL_CREDIT || row.type == SalesSettlementType.CORPORATE_CREDIT) && (row.partyId == null || row.dueEpochDay == null)
                }
                val invalidContract = settlementRows.any { row ->
                    row.type == SalesSettlementType.CORPORATE_CREDIT && row.contractId.isNotBlank() && (row.contractId.toLongOrNull()?.let { it > 0 } != true)
                }
                when {
                    invalidMoneyInput -> error = "یکی از مبالغ نامعتبر است."
                    branchId == null || branchId <= 0 -> error = "یک شعبه فعال انتخاب کنید."
                    locationId == null || locationId <= 0 || branchLocations.none { it.id == locationId } -> error = "یک انبار/مکان مصرف مجاز از همان شعبه انتخاب کنید."
                    gross <= 0 -> error = "فروش ناخالص سربرگ باید مثبت باشد."
                    lines.isEmpty() -> error = "حداقل یک ردیف فروش با تعداد معتبر لازم است؛ مبلغ ردیف اختیاری است."
                    netSales <= 0 -> error = "فروش خالص باید مثبت باشد."
                    settlementRows.isEmpty() -> error = "حداقل یک روش تسویه وارد کنید."
                    invalidCredit -> error = "نسیه/شرکتی به طرف‌حساب و سررسید معتبر نیاز دارد."
                    invalidContract -> error = "شناسه قرارداد شرکتی باید یک عدد مثبت باشد یا خالی بماند."
                    drafts.size != settlementRows.count { (parseMoneyInputOrNull(it.amount) ?: 0L) > 0 } -> error = "ردیف تسویه ناقص است."
                    settlementTotal != amountToSettle -> error = "جمع همه پنج نوع تسویه باید دقیقاً با مبلغ قابل تسویه (درآمد + مالیات) برابر باشد."
                    else -> onSave(initial?.id, branchId, locationId, day, gross, discountRial, returnRial, serviceRial, taxRial, notes, lines, drafts, onDismiss)
                }
            }) { Text("ذخیره پیش‌نویس") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SettlementEditorCard(
    index: Int,
    row: SettlementEditorRow,
    saleDay: Long,
    customers: List<CustomerRecord>,
    onChange: (SettlementEditorRow) -> Unit,
    onRemove: () -> Unit,
) {
    val isCredit = row.type == SalesSettlementType.PERSONAL_CREDIT || row.type == SalesSettlementType.CORPORATE_CREDIT
    val partyType = if (row.type == SalesSettlementType.CORPORATE_CREDIT) PartyFinancialType.COMPANY else PartyFinancialType.PERSON
    val eligible = customers.filter { it.partyType == partyType }
    var expanded by remember(index, row.type) { mutableStateOf(false) }
    val title = when (row.type) {
        SalesSettlementType.CASH -> "نقدی"
        SalesSettlementType.CARD -> "کارتخوان"
        SalesSettlementType.BANK_TRANSFER -> "حواله / واریز بانکی"
        SalesSettlementType.PERSONAL_CREDIT -> "نسیه شخصی"
        SalesSettlementType.CORPORATE_CREDIT -> "فروش شرکتی"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.Bold); TextButton(onClick = onRemove) { Text("حذف") } }
            OutlinedTextField(row.amount, { onChange(row.copy(amount = formatMoneyInput(it))) }, label = { Text("مبلغ") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (isCredit) {
                Column {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(eligible.firstOrNull { it.id == row.partyId }?.name ?: if (partyType == PartyFinancialType.COMPANY) "انتخاب شرکت" else "انتخاب شخص") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        eligible.forEach { party -> DropdownMenuItem(text = { Text("${party.name} · مانده ${formatMoney(party.outstandingRial)}") }, onClick = { onChange(row.copy(partyId = party.id)); expanded = false }) }
                    }
                }
                PersianDateField("سررسید", row.dueEpochDay ?: saleDay, { onChange(row.copy(dueEpochDay = it)) })
                if (row.type == SalesSettlementType.CORPORATE_CREDIT) {
                    OutlinedTextField(row.contractId, { onChange(row.copy(contractId = it.filter(Char::isDigit))) }, label = { Text("شناسه قرارداد (اختیاری)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(row.reference, { onChange(row.copy(reference = it)) }, label = { Text("مرجع (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.note, { onChange(row.copy(note = it)) }, label = { Text("یادداشت (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            } else if (row.type == SalesSettlementType.BANK_TRANSFER || row.type == SalesSettlementType.CARD) {
                OutlinedTextField(row.reference, { onChange(row.copy(reference = it)) }, label = { Text("شماره مرجع (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DailySalesReportDialog(state: DailySalesUiState, onDismiss: () -> Unit, onApply: (Long, Long) -> Unit) {
    var from by remember { mutableLongStateOf(state.reportFromEpochDay) }
    var to by remember { mutableLongStateOf(state.reportToEpochDay) }
    var profitabilitySort by remember { mutableStateOf("MARGIN") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش فروش تجمیعی") },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PersianDateField("از تاریخ", from, { from = it }) }
            item { PersianDateField("تا تاریخ", to, { to = it }) }
            state.report?.let { report -> item {
                val it = report
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("روزهای دارای رویداد فروش/برگشت: ${ErpDisplayFormatters.integer(it.dayCount)}")
                    Text("فروش خالص: ${formatMoney(it.salesRial)}")
                    Text("بهای مواد مصرف‌شده: ${formatMoney(it.costOfGoodsRial)}")
                    Text("حاشیه پس از هزینه مواد: ${formatMoney(it.grossProfitRial)}", fontWeight = FontWeight.Bold)
                    if (it.fullCostRial != null && it.fullMarginRial != null) {
                        Text("بهای کامل: ${formatMoney(it.fullCostRial)}")
                        Text("حاشیه پس از بهای کامل: ${formatMoney(it.fullMarginRial)}", fontWeight = FontWeight.ExtraBold)
                    } else if (it.totalLineCount > 0) {
                        Text(
                            "پوشش بهای کامل ${ErpDisplayFormatters.integer(it.fullCostCoverageLineCount)} از ${ErpDisplayFormatters.integer(it.totalLineCount)} ردیف است؛ جمع دوره برای جلوگیری از گزارش گمراه‌کننده نمایش داده نمی‌شود.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } }
            state.report?.takeIf { it.menuProfitability.isNotEmpty() }?.let { report ->
                item {
                    Text("سودآوری منو", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { profitabilitySort = "MARGIN" }) { Text("حاشیه کامل") }
                        TextButton(onClick = { profitabilitySort = "SALES" }) { Text("فروش") }
                        TextButton(onClick = { profitabilitySort = "COST" }) { Text("درصد هزینه") }
                    }
                }
                val sorted = when (profitabilitySort) {
                    "SALES" -> report.menuProfitability.sortedByDescending { it.salesRial ?: Long.MIN_VALUE }
                    "COST" -> report.menuProfitability.sortedByDescending { it.fullCostBasisPoints ?: Int.MIN_VALUE }
                    else -> report.menuProfitability.sortedByDescending { it.fullMarginRial ?: Long.MIN_VALUE }
                }
                items(sorted.take(20), key = { "profitability-${it.menuItemId}" }) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.name, fontWeight = FontWeight.Bold)
                            Text("${ErpDisplayFormatters.integer(row.unitsSold)} واحد · فروش ${row.salesRial?.let(::formatMoney) ?: "نامشخص"} · هزینه مواد ${formatMoney(row.foodCostRial)}", style = MaterialTheme.typography.bodySmall)
                            val full = row.fullMarginRial?.let(::formatMoney) ?: "فاقد تصویر ثابت بهای کامل"
                            Text("حاشیه کامل: $full", style = MaterialTheme.typography.bodySmall, color = if (row.fullMarginRial?.let { it < 0 } == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } },
        confirmButton = { Button(onClick = { onApply(from, to) }) { Text("اعمال بازه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
