@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.restaurant.management.ui

import ir.restaurant.management.core.GlobalId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Print
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetRecord
import ir.restaurant.management.domain.assets.DepreciationDraft
import ir.restaurant.management.domain.assets.DepreciationReversalDraft
import ir.restaurant.management.domain.assets.AssetTransferDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.assets.AssetImpairmentDraft
import ir.restaurant.management.domain.assets.AssetSaleDraft

@Composable
fun AssetScreen(
    state: AssetUiState,
    branches: List<BranchRecord>,
    suppliers: List<SupplierRecord>,
    onSave: (Long?, AssetDraft) -> Unit,
    onRecognize: (Long) -> Unit,
    onDispose: (Long) -> Unit,
    onDepreciate: (DepreciationDraft) -> Unit,
    onReverseDepreciation: (DepreciationReversalDraft) -> Unit,
    onTransfer: (AssetTransferDraft) -> Unit,
    onMaintenance: (AssetMaintenanceDraft) -> Unit,
    onImpair: (AssetImpairmentDraft) -> Unit,
    onSell: (AssetSaleDraft) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var add by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var disposingAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var recognizingAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var dep by remember { mutableStateOf(false) }
    var transferringAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var maintenanceAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var impairmentAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var saleAsset by remember { mutableStateOf<AssetRecord?>(null) }
    var reversingDepreciationId by remember { mutableStateOf<Long?>(null) }
    val activeAssets = state.assets.filter { it.isActive }
    val depreciableAssets = activeAssets.filter { it.isAccountingRecognized }
    val summary = assetDashboardSummary(state)

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                title = "دارایی‌های ثابت",
                subtitle = "کنترل ارزش دفتری، استهلاک و چرخه عمر تجهیزات",
                onBack = onBack,
                actionLabel = "ثبت دارایی",
                onAction = { add = true },
            )
        },
        floatingActionButton = {
            if (depreciableAssets.isNotEmpty()) {
                Button(onClick = { dep = true }, modifier = Modifier.testTag("asset_depreciate_open")) {
                    Text("ثبت استهلاک")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ErpDashboardHero(
                    eyebrow = "ارزش دفتری دارایی‌ها",
                    value = ErpDisplayFormatters.money(summary.totalBookValueRial),
                    caption = "ارزش خرید ${ErpDisplayFormatters.money(summary.totalPurchaseValueRial)}",
                    metrics = listOf(
                        ErpKpiItem("فعال", ErpDisplayFormatters.integer(summary.activeAssetCount)),
                        ErpKpiItem("استهلاک", ErpDisplayFormatters.money(summary.accumulatedDepreciationRial)),
                        ErpKpiItem("خارج‌شده", ErpDisplayFormatters.integer(summary.disposedAssetCount)),
                    ),
                )
            }
            item {
                SectionHeading("عملیات سریع", "عملیات چرخه عمر روی رکوردهای واقعی دارایی اجرا می‌شوند")
                ErpQuickActionsGrid(
                    listOf(
                        ErpActionItem("دارایی جدید", Icons.Outlined.AddBusiness, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = { add = true }),
                        ErpActionItem("استهلاک", Icons.Outlined.Calculate, ErpPalette.TealSoft, ErpPalette.Teal, enabled = depreciableAssets.isNotEmpty(), onClick = { dep = true }),
                        ErpActionItem("چاپ دفتر", Icons.Outlined.Print, ErpPalette.AmberSoft, ErpPalette.Amber, enabled = state.assets.isNotEmpty(), onClick = { printAssetRegister(context, state.assets) }),
                    ),
                )
            }
            if (summary.unrecognizedAssetCount > 0) {
                item {
                    ErpAttentionRow(
                        title = "دارایی نیازمند تطبیق مالی",
                        description = "${ErpDisplayFormatters.integer(summary.unrecognizedAssetCount)} دارایی قدیمی هنوز به دفتر حسابداری متصل نشده است.",
                        accent = ErpPalette.Amber,
                        soft = ErpPalette.AmberSoft,
                    )
                }
            }
            state.message?.let { message ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            item { SectionHeading("فهرست دارایی‌ها", "ارزش فعلی و وضعیت بهره‌برداری هر دارایی") }
            if (state.assets.isEmpty()) {
                item { EmptyStatePanel("هنوز دارایی ثبت نشده", "برای ثبت تجهیزات، ماشین‌آلات یا اثاثیه از گزینه ثبت دارایی استفاده کنید.") }
            } else {
                items(state.assets, key = { assetRecordListKey(it.id) }) { asset ->
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${asset.assetCode} · ${asset.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                StatusPill(if (asset.isActive) "فعال" else "خارج‌شده")
                            }
                            Text("محل استقرار: ${asset.location.ifBlank { "ثبت نشده" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("تعداد: ${asset.quantity}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricTile("ارزش دفتری", ErpDisplayFormatters.money(asset.bookValueRial), Modifier.weight(1f))
                                MetricTile("استهلاک", ErpDisplayFormatters.money(asset.accumulatedDepreciationRial), Modifier.weight(1f))
                            }
                            if (!asset.isAccountingRecognized) {
                                Text(
                                    "این رکورد از نسخه قدیمی آمده و هنوز سند تحصیل ندارد؛ تا تطبیق مالی، استهلاک و خروج آن مسدود است.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (asset.isActive) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editingAsset = asset }, modifier = Modifier.weight(1f)) { Text("ویرایش") }
                                    OutlinedButton(onClick = { transferringAsset = asset }, modifier = Modifier.weight(1f)) { Text("انتقال") }
                                    OutlinedButton(onClick = { maintenanceAsset = asset }, modifier = Modifier.weight(1f)) { Text("سرویس") }
                                }
                                if (asset.isAccountingRecognized) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { impairmentAsset = asset }, modifier = Modifier.weight(1f)) { Text("کاهش ارزش") }
                                        OutlinedButton(onClick = { saleAsset = asset }, modifier = Modifier.weight(1f)) { Text("فروش") }
                                        OutlinedButton(onClick = { disposingAsset = asset }, modifier = Modifier.weight(1f)) { Text("خروج") }
                                    }
                                } else {
                                    OutlinedButton(onClick = { recognizingAsset = asset }, modifier = Modifier.fillMaxWidth()) { Text("تطبیق مالی") }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeading("سوابق استهلاک", "آخرین ثبت‌های دوره‌ای دارایی‌ها") }
            if (state.depreciations.isEmpty()) {
                item { EmptyStatePanel("سابقه‌ای وجود ندارد", "پس از ثبت استهلاک ماهانه، سوابق در این بخش نمایش داده می‌شوند.") }
            } else {
                items(state.depreciations, key = { assetDepreciationListKey(it.id) }) { depreciation ->
                    Card(shape = MaterialTheme.shapes.large) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(depreciation.assetName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("دوره ${depreciation.periodYear}/${depreciation.periodMonth}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ErpDisplayFormatters.money(depreciation.amountRial), fontWeight = FontWeight.ExtraBold)
                                Text("تعداد ${depreciation.quantity}", style = MaterialTheme.typography.bodySmall)
                                Text(depreciation.reason.ifBlank { "بدون شرح legacy" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!depreciation.isReversed) {
                                    TextButton(onClick = { reversingDepreciationId = depreciation.id }) { Text("برگشت کنترل‌شده") }
                                } else {
                                    Text("برگشت شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) AssetDialog(existing = null, branches = branches, suppliers = suppliers, onDismiss = { add = false }) { draft ->
        onSave(null, draft)
        add = false
    }
    editingAsset?.let { asset ->
        AssetDialog(existing = asset, branches = branches, suppliers = suppliers, onDismiss = { editingAsset = null }) { draft ->
            onSave(asset.id, draft)
            editingAsset = null
        }
    }
    if (dep) DepDialog(state, onDismiss = { dep = false }) { draft ->
        onDepreciate(draft)
        dep = false
    }
    reversingDepreciationId?.let { depreciationId ->
        DepreciationReversalDialog(
            depreciationId = depreciationId,
            onDismiss = { reversingDepreciationId = null },
            onSave = { draft ->
                reversingDepreciationId = null
                onReverseDepreciation(draft)
            },
        )
    }
    transferringAsset?.let { asset ->
        AssetTransferDialog(asset, branches, { transferringAsset = null }) { draft -> transferringAsset = null; onTransfer(draft) }
    }
    maintenanceAsset?.let { asset ->
        AssetMaintenanceDialog(asset, suppliers, { maintenanceAsset = null }) { draft -> maintenanceAsset = null; onMaintenance(draft) }
    }
    impairmentAsset?.let { asset ->
        AssetImpairmentDialog(asset, { impairmentAsset = null }) { draft -> impairmentAsset = null; onImpair(draft) }
    }
    saleAsset?.let { asset ->
        AssetSaleDialog(asset, { saleAsset = null }) { draft -> saleAsset = null; onSell(draft) }
    }
    recognizingAsset?.let { asset ->
        AssetRecognitionDialog(
            asset = asset,
            onDismiss = { recognizingAsset = null },
            onConfirm = {
                recognizingAsset = null
                onRecognize(asset.id)
            },
        )
    }
    disposingAsset?.let { asset ->
        AlertDialog(
            onDismissRequest = { disposingAsset = null },
            title = { Text("تأیید خروج دارایی") },
            text = {
                Text(
                    "برای «${asset.name}» سند خروج بدون عایدی صادر می‌شود؛ بهای دارایی و استهلاک انباشته بسته و ارزش دفتری باقیمانده به‌عنوان زیان ثبت خواهد شد. اگر دارایی فروخته شده است، این گزینه را استفاده نکنید.",
                )
            },
            confirmButton = {
                Button(onClick = { disposingAsset = null; onDispose(asset.id) }) { Text("خروج بدون عایدی") }
            },
            dismissButton = { TextButton(onClick = { disposingAsset = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun AssetRecognitionDialog(
    asset: AssetRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تطبیق مالی دارایی قدیمی") },
        text = {
            Text("برای «${asset.name}» در تاریخ امروز سند مانده افتتاحیه صادر می‌شود: بدهکار دارایی ثابت (۱۵۰۱) و بستانکار سرمایه (۳۱۰۱). اگر این دارایی قبلاً با سند دستی وارد دفتر شده است، عملیات را انجام ندهید و ابتدا با حسابدار تطبیق دهید.")
        },
        confirmButton = { Button(onClick = onConfirm) { Text("ثبت مانده افتتاحیه") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssetDialog(existing: AssetRecord?, branches: List<BranchRecord>, suppliers: List<SupplierRecord>, onDismiss: () -> Unit, onSave: (AssetDraft) -> Unit) {
    var code by remember(existing?.id) { mutableStateOf(existing?.assetCode.orEmpty()) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var quantity by remember(existing?.id) { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var acquisitionExpanded by remember { mutableStateOf(false) }
    var acquisitionSource by remember(existing?.id) { mutableStateOf(AssetAcquisitionSource.BANK) }
    var supplierExpanded by remember { mutableStateOf(false) }
    var selectedSupplierId by remember(existing?.id) { mutableStateOf<Long?>(null) }
    val categories = listOf("تجهیزات آشپزخانه", "اثاثیه", "وسایل نقلیه", "تجهیزات اداری", "تأسیسات", "ابزار و ماشین‌آلات")
    var cost by remember(existing?.id) { mutableStateOf(existing?.purchaseCostRial?.let(::formatMoneyInputFromRial).orEmpty()) }
    var salvage by remember(existing?.id) { mutableStateOf(existing?.salvageValueRial?.let(::formatMoneyInputFromRial) ?: "0") }
    var life by remember(existing?.id) { mutableStateOf(existing?.usefulLifeMonths?.toString().orEmpty()) }
    var location by remember(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    val initialBranchId = remember(existing?.id, branches) {
        existing?.branchId ?: existing?.branch?.takeIf { it.isNotBlank() }?.let { legacyName -> branches.filter { it.name == legacyName }.singleOrNull()?.id }
    }
    var selectedBranchId by remember(existing?.id, initialBranchId) { mutableStateOf(initialBranchId) }
    var responsible by remember(existing?.id) { mutableStateOf(existing?.responsiblePerson.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var purchaseDay by remember(existing?.id) { mutableLongStateOf(existing?.purchaseEpochDay ?: currentEpochDay()) }
    var payableDueDay by remember(existing?.id) { mutableLongStateOf(purchaseDay + 30) }
    val payableValid = acquisitionSource != AssetAcquisitionSource.PAYABLE ||
        ((selectedSupplierId ?: 0L) > 0L && payableDueDay >= purchaseDay)
    val valid = (existing == null || code.isNotBlank()) && name.isNotBlank() && category.isNotBlank() &&
        (quantity.toIntOrNull() ?: 0) > 0 && runCatching { parseMoneyInputOrZero(cost) }.getOrDefault(0L) > 0L &&
        (life.toIntOrNull() ?: 0) > 0 && payableValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "ثبت دارایی جدید" else "ویرایش دارایی") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(code, { code = it }, label = { Text("کد دارایی خودکار") }, placeholder = { Text("هنگام ثبت صادر می‌شود") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("نام دارایی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existing != null) {
                    Text("مشخصات مالی مبنای دارایی پس از صدور سند تحصیل قفل است؛ نام، دسته، محل و توضیحات قابل ویرایش می‌ماند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6) }, label = { Text("تعداد") }, readOnly = existing != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(category.ifBlank { "انتخاب دسته‌بندی" }) }
                    androidx.compose.material3.DropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                        categories.forEach { value -> androidx.compose.material3.DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryExpanded = false }) }
                    }
                }
                OutlinedTextField(cost, { cost = formatMoneyInput(it) }, label = { Text("بهای خرید کل (${currencyUnitLabel()})") }, readOnly = existing != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(salvage, { salvage = formatMoneyInput(it) }, label = { Text("ارزش اسقاط") }, readOnly = existing != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(life, { life = it.filter(Char::isDigit) }, label = { Text("عمر مفید (ماه)") }, readOnly = existing != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (existing == null) {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { acquisitionExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(acquisitionSource.title) }
                        androidx.compose.material3.DropdownMenu(acquisitionExpanded, { acquisitionExpanded = false }) {
                            AssetAcquisitionSource.entries.filter { it != AssetAcquisitionSource.OWNER_CAPITAL }.forEach { source ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(source.title) },
                                    onClick = { acquisitionSource = source; acquisitionExpanded = false },
                                )
                            }
                        }
                    }
                    if (acquisitionSource == AssetAcquisitionSource.PAYABLE) {
                        Box(Modifier.fillMaxWidth()) {
                            val selectedSupplier = suppliers.firstOrNull { it.id == selectedSupplierId }
                            OutlinedButton(onClick = { supplierExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(selectedSupplier?.let { "${it.code} · ${it.name}" } ?: "انتخاب تأمین‌کننده نسیه")
                            }
                            androidx.compose.material3.DropdownMenu(supplierExpanded, { supplierExpanded = false }) {
                                suppliers.filter { it.isActive }.forEach { supplier ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("${supplier.code} · ${supplier.name}") },
                                        onClick = { selectedSupplierId = supplier.id; supplierExpanded = false },
                                    )
                                }
                            }
                        }
                        PersianDateField("سررسید حساب پرداختنی", payableDueDay, { payableDueDay = it })
                    }
                }
                OutlinedTextField(location, { location = it }, label = { Text("محل استقرار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CanonicalBranchSelector(
                    branches = branches,
                    selectedBranchId = selectedBranchId,
                    onBranchSelected = { selectedBranchId = it },
                    label = "شعبه / سطح سازمان",
                    allowAllBranches = true,
                    tag = "asset_branch_selector",
                )
                OutlinedTextField(responsible, { responsible = it }, label = { Text("مسئول دارایی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
                if (existing == null) PersianDateField("تاریخ خرید دارایی", purchaseDay, { purchaseDay = it })
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        AssetDraft(
                            assetCode = code.trim(),
                            name = name.trim(),
                            category = category.trim(),
                            quantity = quantity.toInt(),
                            purchaseEpochDay = purchaseDay,
                            purchaseCostRial = parseMoneyRial(cost).value,
                            salvageValueRial = parseMoneyInputOrZero(salvage),
                            usefulLifeMonths = life.toInt(),
                            location = location.trim(),
                            notes = notes.trim(),
                            acquisitionSource = acquisitionSource,
                            branch = branches.firstOrNull { it.id == selectedBranchId }?.name.orEmpty(),
                            responsiblePerson = responsible.trim(),
                            branchId = selectedBranchId,
                            supplierId = selectedSupplierId.takeIf { acquisitionSource == AssetAcquisitionSource.PAYABLE },
                            payableDueEpochDay = payableDueDay.takeIf { acquisitionSource == AssetAcquisitionSource.PAYABLE },
                        ),
                    )
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DepDialog(state: AssetUiState, onDismiss: () -> Unit, onSave: (DepreciationDraft) -> Unit) {
    val active = state.assets.filter { it.isActive && it.isAccountingRecognized }
    var selected by remember { mutableStateOf(active.firstOrNull()?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    var postingDay by remember { mutableLongStateOf(currentEpochDay()) }
    var quantity by remember(selected) { mutableStateOf("1") }
    var reason by remember(selected) { mutableStateOf("") }
    val commandId = remember(selected, postingDay) { GlobalId.new().value }
    val persianDate = epochDayToPersian(postingDay)
    val selectedAsset = active.firstOrNull { it.id == selected }
    val valid = selected > 0L && (quantity.toIntOrNull() ?: 0) in 1..(selectedAsset?.quantity ?: 0) && reason.trim().length in 3..300

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت استهلاک ماهانه") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("asset_depreciation_picker")) {
                        Text(active.firstOrNull { it.id == selected }?.name ?: "انتخاب دارایی", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
                        active.forEach { asset ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(asset.name) },
                                onClick = { selected = asset.id; expanded = false },
                                modifier = Modifier.testTag("asset_depreciation_${asset.id}"),
                            )
                        }
                    }
                }
                PersianDateField("دوره و تاریخ ثبت استهلاک", postingDay, { postingDay = it })
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(6) }, label = { Text("تعداد واحد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                selectedAsset?.let { Text("حداکثر تعداد این دارایی: ${it.quantity}", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل / شرح استهلاک") }, modifier = Modifier.fillMaxWidth().testTag("asset_depreciation_reason"))
                Text("ثبت استهلاک، Audit و سند حسابداری در یک تراکنش انجام می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(DepreciationDraft(selected, persianDate.year, persianDate.month, postingDay, quantity.toInt(), reason.trim(), commandId)) },
                modifier = Modifier.testTag("asset_depreciation_submit"),
            ) { Text("ثبت و صدور سند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DepreciationReversalDialog(
    depreciationId: Long,
    onDismiss: () -> Unit,
    onSave: (DepreciationReversalDraft) -> Unit,
) {
    var reason by remember(depreciationId) { mutableStateOf("") }
    val normalized = reason.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت کنترل‌شده استهلاک") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("برگشت، سند حسابداری معکوس و سابقه ممیزی ثبت می‌کند؛ دلیل واقعی عملیات را وارد کنید.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 300) reason = it },
                    label = { Text("دلیل / شرح برگشت") },
                    modifier = Modifier.fillMaxWidth().testTag("asset_depreciation_reversal_reason"),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = normalized.length in 3..300,
                onClick = {
                    onSave(
                        DepreciationReversalDraft(
                            depreciationId = depreciationId,
                            reversalEpochDay = currentEpochDay(),
                            reason = normalized,
                        ),
                    )
                },
                modifier = Modifier.testTag("asset_depreciation_reversal_confirm"),
            ) { Text("ثبت برگشت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssetTransferDialog(asset: AssetRecord, branches: List<BranchRecord>, onDismiss: () -> Unit, onSave: (AssetTransferDraft) -> Unit) {
    var location by remember(asset.id) { mutableStateOf(asset.location) }
    val initialBranchId = remember(asset.id, branches) {
        asset.branchId ?: asset.branch.takeIf { it.isNotBlank() }?.let { legacyName -> branches.filter { it.name == legacyName }.singleOrNull()?.id }
    }
    var selectedBranchId by remember(asset.id, initialBranchId) { mutableStateOf(initialBranchId) }
    var responsible by remember(asset.id) { mutableStateOf(asset.responsiblePerson) }
    var day by remember(asset.id) { mutableLongStateOf(currentEpochDay()) }
    var reason by remember(asset.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتقال ${asset.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مبدأ: ${asset.branch.ifBlank { "—" }} / ${asset.location.ifBlank { "—" }} / ${asset.responsiblePerson.ifBlank { "—" }}")
                OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("محل جدید") })
                CanonicalBranchSelector(
                    branches = branches,
                    selectedBranchId = selectedBranchId,
                    onBranchSelected = { selectedBranchId = it },
                    label = "شعبه مقصد / سطح سازمان",
                    allowAllBranches = true,
                    tag = "asset_transfer_branch_selector",
                )
                OutlinedTextField(responsible, { responsible = it }, Modifier.fillMaxWidth(), label = { Text("مسئول جدید") })
                PersianDateField("تاریخ انتقال", day) { day = it }
                OutlinedTextField(reason, { reason = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("دلیل انتقال") })
            }
        },
        confirmButton = {
            Button(enabled = reason.trim().length >= 3, onClick = {
                onSave(
                    AssetTransferDraft(
                        assetId = asset.id,
                        toLocation = location,
                        toBranch = branches.firstOrNull { it.id == selectedBranchId }?.name.orEmpty(),
                        toResponsiblePerson = responsible,
                        businessEpochDay = day,
                        reason = reason,
                        toBranchId = selectedBranchId,
                    ),
                )
            }) { Text("ثبت انتقال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssetMaintenanceDialog(asset: AssetRecord, suppliers: List<SupplierRecord>, onDismiss: () -> Unit, onSave: (AssetMaintenanceDraft) -> Unit) {
    var serviceType by remember(asset.id) { mutableStateOf("") }
    var day by remember(asset.id) { mutableLongStateOf(currentEpochDay()) }
    var amount by remember(asset.id) { mutableStateOf("") }
    var contractor by remember(asset.id) { mutableStateOf("") }
    var note by remember(asset.id) { mutableStateOf("") }
    var nextDay by remember(asset.id) { mutableStateOf<Long?>(null) }
    var source by remember(asset.id) { mutableStateOf(AssetAcquisitionSource.BANK) }
    var supplierExpanded by remember(asset.id) { mutableStateOf(false) }
    var selectedSupplierId by remember(asset.id) { mutableStateOf<Long?>(null) }
    var payableDueDay by remember(asset.id) { mutableLongStateOf(day + 30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعمیر و نگهداری ${asset.name}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(serviceType, { serviceType = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("نوع سرویس") })
                PersianDateField("تاریخ سرویس", day) { day = it }
                OutlinedTextField(amount, { amount = formatMoneyInput(it) }, Modifier.fillMaxWidth(), label = { Text("هزینه سرویس") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(contractor, { contractor = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("پیمانکار") })
                OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("توضیحات") })
                OptionalPersianDateField("سرویس بعدی", nextDay, { nextDay = it }, defaultEpochDay = day + 30)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AssetAcquisitionSource.CASH, AssetAcquisitionSource.BANK, AssetAcquisitionSource.PAYABLE).forEach { option ->
                        TextButton(onClick = { source = option; if (option != AssetAcquisitionSource.PAYABLE) selectedSupplierId = null }) {
                            Text(if (source == option) "✓ ${option.title}" else option.title)
                        }
                    }
                }
                if (source == AssetAcquisitionSource.PAYABLE) {
                    Box(Modifier.fillMaxWidth()) {
                        val selectedSupplier = suppliers.firstOrNull { it.id == selectedSupplierId }
                        OutlinedButton(onClick = { supplierExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSupplier?.let { "${it.code} · ${it.name}" } ?: "انتخاب تأمین‌کننده سرویس")
                        }
                        androidx.compose.material3.DropdownMenu(supplierExpanded, { supplierExpanded = false }) {
                            suppliers.filter { it.isActive }.forEach { supplier ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("${supplier.code} · ${supplier.name}") },
                                    onClick = { selectedSupplierId = supplier.id; supplierExpanded = false },
                                )
                            }
                        }
                    }
                    PersianDateField("سررسید حساب پرداختنی", payableDueDay, { payableDueDay = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = serviceType.trim().isNotBlank() &&
                    (source != AssetAcquisitionSource.PAYABLE || ((selectedSupplierId ?: 0L) > 0L && payableDueDay >= day)),
                onClick = {
                onSave(
                    AssetMaintenanceDraft(
                        assetId = asset.id, serviceType = serviceType, serviceEpochDay = day,
                        costRial = parseMoneyInputOrZero(amount), contractor = contractor, note = note,
                        nextServiceEpochDay = nextDay, paymentSource = source,
                        supplierId = selectedSupplierId.takeIf { source == AssetAcquisitionSource.PAYABLE },
                        payableDueEpochDay = payableDueDay.takeIf { source == AssetAcquisitionSource.PAYABLE },
                    ),
                )
            }) { Text("ثبت سرویس و سند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssetImpairmentDialog(asset: AssetRecord, onDismiss: () -> Unit, onSave: (AssetImpairmentDraft) -> Unit) {
    var day by remember(asset.id) { mutableLongStateOf(currentEpochDay()) }
    var amount by remember(asset.id) { mutableStateOf("") }
    var reason by remember(asset.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کاهش ارزش ${asset.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بهای تاریخی: ${formatMoney(asset.purchaseCostRial)} · استهلاک: ${ErpDisplayFormatters.money(asset.accumulatedDepreciationRial)} · کاهش ارزش قبلی: ${formatMoney(asset.impairmentRial)}")
                Text("ارزش دفتری فعلی: ${ErpDisplayFormatters.money(asset.bookValueRial)} · کف ارزش اسقاط: ${formatMoney(asset.salvageValueRial)}")
                PersianDateField("تاریخ کاهش ارزش", day) { day = it }
                OutlinedTextField(amount, { amount = formatMoneyInput(it) }, Modifier.fillMaxWidth(), label = { Text("مبلغ کاهش ارزش") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(reason, { reason = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("دلیل") })
            }
        },
        confirmButton = {
            Button(enabled = parseMoneyInputOrZero(amount) > 0 && reason.trim().length >= 3, onClick = {
                onSave(AssetImpairmentDraft(asset.id, day, parseMoneyInputOrZero(amount), reason))
            }) { Text("ثبت کاهش ارزش و سند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AssetSaleDialog(asset: AssetRecord, onDismiss: () -> Unit, onSave: (AssetSaleDraft) -> Unit) {
    var day by remember(asset.id) { mutableLongStateOf(currentEpochDay()) }
    var price by remember(asset.id) { mutableStateOf("") }
    var buyer by remember(asset.id) { mutableStateOf("") }
    var reason by remember(asset.id) { mutableStateOf("") }
    var source by remember(asset.id) { mutableStateOf(AssetAcquisitionSource.BANK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فروش ${asset.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بهای تاریخی ${formatMoney(asset.purchaseCostRial)} · ارزش دفتری ${ErpDisplayFormatters.money(asset.bookValueRial)}")
                PersianDateField("تاریخ فروش", day) { day = it }
                OutlinedTextField(price, { price = formatMoneyInput(it) }, Modifier.fillMaxWidth(), label = { Text("مبلغ فروش") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(buyer, { buyer = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("خریدار") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AssetAcquisitionSource.CASH, AssetAcquisitionSource.BANK).forEach { option ->
                        TextButton(onClick = { source = option }) { Text(if (source == option) "✓ ${option.title}" else option.title) }
                    }
                }
                OutlinedTextField(reason, { reason = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("دلیل / شرح فروش") })
            }
        },
        confirmButton = {
            Button(enabled = reason.trim().length >= 3, onClick = {
                onSave(AssetSaleDraft(asset.id, day, parseMoneyInputOrZero(price), source, buyer, reason))
            }) { Text("ثبت فروش و سود/زیان") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
