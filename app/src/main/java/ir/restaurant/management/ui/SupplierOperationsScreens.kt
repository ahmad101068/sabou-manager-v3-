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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.operations.StockMovementRecord
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierPartyType
import ir.restaurant.management.domain.operations.SupplierMergeDraft
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.operations.WasteDraft
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineDraft
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod

@Composable
fun SuppliersScreen(
    state: OperationsUiState,
    onSave: (Long?, SupplierDraft, () -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onDeactivate: (Long) -> Unit,
    onMerge: (SupplierMergeDraft, () -> Unit) -> Unit,
    onOpenPurchases: () -> Unit,
    onNewPurchase: () -> Unit,
    onBack: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SupplierRecord?>(null) }
    var deactivateTarget by remember { mutableStateOf<SupplierRecord?>(null) }
    var mergeSource by remember { mutableStateOf<SupplierRecord?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val summary = supplierDashboardSummary(state)
    val visibleSuppliers = state.suppliers.filter { supplier ->
        val q = search.trim()
        q.isBlank() || listOf(supplier.name, supplier.contactName, supplier.phone, supplier.address).any { it.contains(q, ignoreCase = true) }
    }
    Scaffold(topBar = { ProfessionalTopBar("تأمین‌کنندگان", "شبکه تأمین، تماس‌ها و شرایط پرداخت", onBack, "تأمین‌کننده جدید") { selected = null; editorOpen = true } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.message?.let { item { MessageCard(it) } }
            item {
                OutlinedButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.refreshing) "در حال تازه‌سازی…" else "تازه‌سازی اطلاعات تأمین‌کنندگان")
                }
            }
            item {
                ErpDashboardHero(
                    eyebrow = "شبکه تأمین",
                    value = "${ErpDisplayFormatters.integer(summary.activeSuppliers)} تأمین‌کننده",
                    caption = "میانگین مهلت پرداخت ${ErpDisplayFormatters.integer(summary.averagePaymentTermsDays)} روز",
                    metrics = listOf(
                        ErpKpiItem("خرید باز", ErpDisplayFormatters.integer(summary.openPurchaseCount)),
                        ErpKpiItem("پرداختنی", ErpDisplayFormatters.money(summary.payableRial)),
                        ErpKpiItem("سررسید", ErpDisplayFormatters.integer(summary.dueSettlementCount)),
                    ),
                )
            }
            item {
                SectionHeading("عملیات سریع", "عملیات به مسیرهای واقعی خرید و تسویه متصل‌اند")
                ErpQuickActionsGrid(
                    listOf(
                        ErpActionItem("تأمین‌کننده جدید", Icons.Outlined.AddBusiness, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = { selected = null; editorOpen = true }),
                        ErpActionItem("سفارش خرید", Icons.Outlined.AddShoppingCart, ErpPalette.TealSoft, ErpPalette.Teal, onClick = onOpenPurchases),
                        ErpActionItem("ثبت خرید", Icons.Outlined.ReceiptLong, ErpPalette.GreenSoft, ErpPalette.Green, onClick = onNewPurchase),
                        ErpActionItem("پرداخت و گردش", Icons.Outlined.Payments, ErpPalette.AmberSoft, ErpPalette.Amber, onClick = onOpenPurchases),
                    ),
                )
            }
            item {
                SectionHeading("شرکای تأمین", "اطلاعات تماس و شرایط همکاری")
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("جست‌وجوی نام، رابط، تلفن یا آدرس") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = "جست‌وجو") },
                    singleLine = true,
                )
            }
            if (visibleSuppliers.isEmpty()) item { ErpStatePanel("تأمین‌کننده‌ای پیدا نشد", if (state.suppliers.isEmpty()) "اولین شریک تأمین را اضافه کنید تا ثبت خرید سریع‌تر شود." else "عبارت جست‌وجو را تغییر دهید.") }
            items(visibleSuppliers, key = { it.id }) { supplier ->
                ElevatedCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.elevatedCardElevation(1.dp)) {
                    Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(supplier.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text(if (supplier.contactName.isBlank()) "بدون رابط ثبت‌شده" else supplier.contactName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill("${supplier.paymentTermsDays} روز")
                        }
                        CompactInfoRow("کد تأمین‌کننده", supplier.code.ifBlank { "—" })
                        CompactInfoRow("نوع", if (supplier.partyType == SupplierPartyType.COMPANY) "شرکت" else "شخص")
                        supplier.legalId?.takeIf { it.isNotBlank() }?.let { CompactInfoRow("شناسه ملی/حقوقی", it) }
                        supplier.bankIban?.takeIf { it.isNotBlank() }?.let { CompactInfoRow("شبای تسویه", it) }
                        if (supplier.phone.isNotBlank()) CompactInfoRow("شماره تماس", supplier.phone)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selected = supplier; editorOpen = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("ویرایش") }
                            OutlinedButton(onClick = { mergeSource = supplier }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("ادغام") }
                            TextButton(onClick = { deactivateTarget = supplier }, modifier = Modifier.weight(1f)) { Text("غیرفعال", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (editorOpen) SupplierEditorDialog(existing = selected, busy = state.busy, onDismiss = { editorOpen = false }, onSave = { draft -> onSave(selected?.id, draft) { editorOpen = false } })
    deactivateTarget?.let { target -> ConfirmDeactivateDialog("غیرفعال‌کردن تأمین‌کننده", "«${target.name}» از فهرست انتخاب‌ها حذف می‌شود؛ سوابق قبلی باقی می‌مانند.", { deactivateTarget = null }) { deactivateTarget = null; onDeactivate(target.id) } }
    mergeSource?.let { source ->
        SupplierMergeDialog(
            source = source,
            candidates = state.suppliers.filter { it.id != source.id },
            busy = state.busy,
            onDismiss = { mergeSource = null },
            onConfirm = { targetId, reason ->
                onMerge(SupplierMergeDraft(source.id, targetId, reason)) { mergeSource = null }
            },
        )
    }
}

@Composable
private fun SupplierMergeDialog(
    source: SupplierRecord,
    candidates: List<SupplierRecord>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    var targetId by remember(source.id) { mutableStateOf<Long?>(null) }
    var reason by remember(source.id) { mutableStateOf("") }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ادغام کنترل‌شده تأمین‌کننده") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سوابق مالی «${source.name}» بازنویسی نمی‌شوند؛ فقط رکورد پایه مبدأ پس از کنترل وابستگی‌ها غیرفعال می‌شود.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                SelectionField(
                    label = "تأمین‌کننده مقصد",
                    selectedText = candidates.firstOrNull { it.id == targetId }?.name,
                    options = candidates.map { it.id to "${it.code} — ${it.name}" },
                    onSelected = { targetId = it },
                )
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل ادغام") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = !busy && candidates.isNotEmpty(), onClick = {
                runCatching { onConfirm(requireNotNull(targetId) { "تأمین‌کننده مقصد را انتخاب کنید." }, reason) }
                    .onFailure { error = it.message }
            }) { Text("ثبت ادغام") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun SupplierEditorDialog(
    existing: SupplierRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplierDraft) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var partyType by remember(existing?.id) { mutableStateOf(existing?.partyType ?: SupplierPartyType.COMPANY) }
    var legalId by remember(existing?.id) { mutableStateOf(existing?.legalId.orEmpty()) }
    var economicCode by remember(existing?.id) { mutableStateOf(existing?.economicCode.orEmpty()) }
    var bankIban by remember(existing?.id) { mutableStateOf(existing?.bankIban.orEmpty()) }
    var contact by remember(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var address by remember(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var terms by remember(existing?.id) { mutableStateOf(existing?.paymentTermsDays?.toString() ?: "0") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تأمین‌کننده جدید" else "ویرایش تأمین‌کننده") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(name, { name = it }, label = { Text(if (partyType == SupplierPartyType.COMPANY) "نام شرکت" else "نام و نام خانوادگی") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = partyType == SupplierPartyType.COMPANY,
                        onClick = { partyType = SupplierPartyType.COMPANY },
                        label = { Text("شرکت") },
                    )
                    FilterChip(
                        selected = partyType == SupplierPartyType.PERSON,
                        onClick = { partyType = SupplierPartyType.PERSON },
                        label = { Text("شخص") },
                    )
                }
                OutlinedTextField(legalId, { legalId = it }, label = { Text("شناسه ملی/حقوقی") }, singleLine = true)
                OutlinedTextField(economicCode, { economicCode = it }, label = { Text("کد اقتصادی (اختیاری)") }, singleLine = true)
                OutlinedTextField(bankIban, { bankIban = it }, label = { Text("شماره شبا برای تسویه (اختیاری)") }, singleLine = true)
                OutlinedTextField(contact, { contact = it }, label = { Text("نام رابط") })
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("شماره تماس") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(address, { address = it }, label = { Text("نشانی") })
                OutlinedTextField(
                    terms,
                    { terms = it },
                    label = { Text("مهلت تسویه پیش‌فرض (روز)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت") })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val parsedTerms = normalizeNumberInput(terms).toInt()
                        onSave(
                            SupplierDraft(
                                name = name,
                                partyType = partyType,
                                legalId = legalId.ifBlank { null },
                                economicCode = economicCode.ifBlank { null },
                                bankIban = bankIban.ifBlank { null },
                                contactName = contact,
                                phone = phone,
                                address = address,
                                paymentTermsDays = parsedTerms,
                                notes = notes,
                            ),
                        )
                    } catch (failure: Exception) {
                        error = failure.message ?: "مهلت تسویه معتبر نیست."
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}


