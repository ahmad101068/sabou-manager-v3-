package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.inventory.InventoryItemMasterDraft
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.inventory.InventoryItemMasterRecord
import ir.restaurant.management.domain.inventory.InventoryItemType
import ir.restaurant.management.domain.inventory.InventoryLocationDraft
import ir.restaurant.management.domain.inventory.InventoryLocationType
import ir.restaurant.management.domain.inventory.InventorySku
import ir.restaurant.management.domain.inventory.InventoryStockStatus
import ir.restaurant.management.domain.inventory.InventoryStorageCondition
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun InventoryItemCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
    branches: List<BranchRecord>,
) {
    var editor by remember { mutableStateOf<InventoryItemMasterRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showLocationEditor by remember { mutableStateOf(false) }
    var detailItemId by remember { mutableStateOf<Long?>(null) }
    val canManageItems = state.currentUser?.role?.allows(Permission.INVENTORY_ITEM_MANAGE) == true
    val canManageLocations = state.currentUser?.role?.allows(Permission.INVENTORY_LOCATION_MANAGE) == true

    LaunchedEffect(state.pendingAction) {
        if (state.pendingAction == InventoryWorkspaceAction.CREATE_ITEM) {
            showEditor = true; editor = null
            viewModel.consumeAction(InventoryWorkspaceAction.CREATE_ITEM)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("نام، SKU یا بارکد") },
                trailingIcon = { TextButton(onClick = viewModel::search) { Text("جست‌وجو") } },
            )
        }
        item {
            SelectionField(
                label = "محل نگهداری",
                selectedText = state.locations.firstOrNull { it.id == state.locationId }?.name ?: "همه محل‌ها",
                options = listOf(0L to "همه محل‌ها") + state.locations.filter { it.active }.map { it.id to it.name },
                onSelected = { viewModel.setLocation(it.takeIf { id -> id != 0L }) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InventoryStockStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.stockStatus == status,
                        onClick = { viewModel.setStockStatus(status) },
                        label = { Text(stockStatusTitle(status)) },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { editor = null; showEditor = true },
                    enabled = canManageItems && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("کالای جدید") }
                OutlinedButton(
                    onClick = { showLocationEditor = true },
                    enabled = canManageLocations && !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text("محل جدید") }
            }
        }
        item { SectionHeading("مانده بر اساس محل", "نمایش حداکثر ۱۰۰ ردیف؛ فیلتر و جست‌وجو در SQL") }
        if (!state.loading && state.balances.isEmpty()) item { InventoryEmptyState("مانده‌ای با این فیلتر پیدا نشد.") }
        items(state.balances, key = { "balance-${it.itemId}-${it.locationId}" }) { balance ->
            val master = state.items.firstOrNull { it.id == balance.itemId }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(balance.itemName, fontWeight = FontWeight.Bold)
                            Text("${balance.sku} · ${balance.locationName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (canManageItems && master != null) TextButton(onClick = { editor = master; showEditor = true }) { Text("ویرایش") }
                    }
                    CompactInfoRow("موجودی فیزیکی", "${formatQuantity(balance.onHandMicros)} ${balance.baseUnit}")
                    CompactInfoRow("قابل مصرف", "${formatQuantity(balance.availableMicros)} ${balance.baseUnit}", balance.availableMicros <= balance.reorderPointMicros)
                    CompactInfoRow("در راه", "${formatQuantity(balance.inTransitMicros)} ${balance.baseUnit}", balance.inTransitMicros > 0)
                    CompactInfoRow("قرنطینه / آسیب", "${formatQuantity(balance.quarantinedMicros + balance.damagedMicros)} ${balance.baseUnit}", balance.quarantinedMicros + balance.damagedMicros > 0)
                    CompactInfoRow("ارزش", formatMoney(balance.inventoryValueRial), true)
                    OutlinedButton(onClick = { detailItemId = balance.itemId }, modifier = Modifier.fillMaxWidth()) { Text("جزئیات و سوابق") }
                    if (canManageItems && master?.active == true && balance.onHandMicros == 0L && balance.inTransitMicros == 0L) {
                        TextButton(onClick = { viewModel.deactivateItem(master.id) }, enabled = !state.busy) { Text("غیرفعال‌سازی کالا") }
                    }
                }
            }
        }
    }

    if (showEditor) {
        InventoryItemMasterDialog(
            existing = editor,
            busy = state.busy,
            onDismiss = { showEditor = false },
            onSave = { draft -> viewModel.saveItem(editor?.id, draft) { showEditor = false } },
        )
    }
    if (showLocationEditor) {
        InventoryLocationDialog(
            busy = state.busy,
            branches = branches,
            onDismiss = { showLocationEditor = false },
            onSave = { draft -> viewModel.saveLocation(null, draft) { showLocationEditor = false } },
        )
    }
    detailItemId?.let { itemId ->
        state.items.firstOrNull { it.id == itemId }?.let { item ->
            InventoryItemDetail2Dialog(item, state) { detailItemId = null }
        }
    }
}

@Composable
private fun InventoryItemMasterDialog(
    existing: InventoryItemMasterRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (InventoryItemMasterDraft) -> Unit,
) {
    val generatedSku = remember { InventorySku.generated().value }
    var sku by remember(existing?.id) { mutableStateOf(existing?.sku?.value ?: generatedSku) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "مواد اولیه") }
    var itemType by remember(existing?.id) { mutableStateOf(existing?.itemType ?: InventoryItemType.INGREDIENT) }
    var baseUnit by remember(existing?.id) { mutableStateOf(existing?.baseUnit ?: "کیلوگرم") }
    var barcode by remember(existing?.id) { mutableStateOf(existing?.primaryBarcode?.value.orEmpty()) }
    var brand by remember(existing?.id) { mutableStateOf(existing?.brand.orEmpty()) }
    var purchaseUnit by remember(existing?.id) { mutableStateOf(existing?.purchaseUnit ?: baseUnit) }
    var purchaseNumerator by remember(existing?.id) { mutableStateOf((existing?.purchaseToBaseNumerator ?: 1).toString()) }
    var purchaseDenominator by remember(existing?.id) { mutableStateOf((existing?.purchaseToBaseDenominator ?: 1).toString()) }
    var recipeUnit by remember(existing?.id) { mutableStateOf(existing?.recipeUnit ?: baseUnit) }
    var recipeNumerator by remember(existing?.id) { mutableStateOf((existing?.recipeToBaseNumerator ?: 1).toString()) }
    var recipeDenominator by remember(existing?.id) { mutableStateOf((existing?.recipeToBaseDenominator ?: 1).toString()) }
    var storage by remember(existing?.id) { mutableStateOf(existing?.storageCondition ?: InventoryStorageCondition.AMBIENT) }
    var shelfLife by remember(existing?.id) { mutableStateOf(existing?.shelfLifeDays?.toString().orEmpty()) }
    var trackLot by remember(existing?.id) { mutableStateOf(existing?.trackLot ?: false) }
    var trackExpiry by remember(existing?.id) { mutableStateOf(existing?.trackExpiry ?: false) }
    var minimum by remember(existing?.id) { mutableStateOf(formatQuantity(existing?.minimumStockMicros ?: 0)) }
    var maximum by remember(existing?.id) { mutableStateOf(formatQuantity(existing?.maximumStockMicros ?: 0)) }
    var safety by remember(existing?.id) { mutableStateOf(formatQuantity(existing?.safetyStockMicros ?: 0)) }
    var reorder by remember(existing?.id) { mutableStateOf(formatQuantity(existing?.reorderPointMicros ?: 0)) }
    var leadTime by remember(existing?.id) { mutableStateOf((existing?.leadTimeDays ?: 0).toString()) }
    var advanced by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تعریف کالای انبار" else "ویرایش ${existing.name}") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { MessageCard(it, true) }
                Text("اطلاعات پایه", fontWeight = FontWeight.Bold)
                OutlinedTextField(sku, { sku = it.uppercase().take(40) }, label = { Text("کد یکتای کالا (SKU)") }, enabled = existing == null)
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("نام کالا") })
                OutlinedTextField(category, { category = it.take(80) }, label = { Text("دسته‌بندی") })
                SelectionField("نوع کالا", itemTypeTitle(itemType), InventoryItemType.entries.mapIndexed { index, value -> index.toLong() to itemTypeTitle(value) }) { itemType = InventoryItemType.entries[it.toInt()] }
                OutlinedTextField(baseUnit, { baseUnit = it.take(40) }, label = { Text("واحد پایه") })
                OutlinedTextField(barcode, { barcode = it.take(80) }, label = { Text("بارکد اصلی (اختیاری)") })
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "بستن تنظیمات پیشرفته" else "واحدها، نگهداری و سیاست موجودی") }
                if (advanced) {
                    Text("تبدیل واحد", fontWeight = FontWeight.Bold)
                    OutlinedTextField(purchaseUnit, { purchaseUnit = it.take(40) }, label = { Text("واحد خرید") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(purchaseNumerator, { purchaseNumerator = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("صورت خرید") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(purchaseDenominator, { purchaseDenominator = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("مخرج خرید") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(recipeUnit, { recipeUnit = it.take(40) }, label = { Text("واحد رسپی") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(recipeNumerator, { recipeNumerator = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("صورت رسپی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(recipeDenominator, { recipeDenominator = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("مخرج رسپی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(brand, { brand = it.take(80) }, label = { Text("برند") })
                    SelectionField("شرایط نگهداری", storageTitle(storage), InventoryStorageCondition.entries.mapIndexed { index, value -> index.toLong() to storageTitle(value) }) { storage = InventoryStorageCondition.entries[it.toInt()] }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(trackLot, { checked -> trackLot = checked; if (!checked) trackExpiry = false })
                        Text("ردیابی لات")
                        Checkbox(trackExpiry, { checked -> trackExpiry = checked; if (checked) trackLot = true })
                        Text("ردیابی انقضا")
                    }
                    if (trackExpiry) OutlinedTextField(shelfLife, { shelfLife = it.filter(Char::isDigit) }, label = { Text("عمر ماندگاری (روز)") })
                    Text("سیاست موجودی", fontWeight = FontWeight.Bold)
                    OutlinedTextField(minimum, { minimum = it }, label = { Text("حداقل موجودی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(maximum, { maximum = it }, label = { Text("حداکثر موجودی؛ صفر یعنی بدون سقف") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(safety, { safety = it }, label = { Text("ذخیره ایمن") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(reorder, { reorder = it }, label = { Text("نقطه سفارش") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(leadTime, { leadTime = it.filter(Char::isDigit) }, label = { Text("زمان تأمین (روز)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    runCatching {
                        InventoryItemMasterDraft(
                            sku = sku,
                            name = name,
                            category = category,
                            itemType = itemType,
                            baseUnit = baseUnit,
                            purchaseUnit = purchaseUnit.ifBlank { baseUnit },
                            purchaseToBaseNumerator = purchaseNumerator.toLong(),
                            purchaseToBaseDenominator = purchaseDenominator.toLong(),
                            recipeUnit = recipeUnit.ifBlank { baseUnit },
                            recipeToBaseNumerator = recipeNumerator.toLong(),
                            recipeToBaseDenominator = recipeDenominator.toLong(),
                            primaryBarcode = barcode.ifBlank { null },
                            brand = brand,
                            storageCondition = storage,
                            shelfLifeDays = shelfLife.toIntOrNull(),
                            trackLot = trackLot,
                            trackExpiry = trackExpiry,
                            minimumStockMicros = parseQuantity(minimum).value,
                            maximumStockMicros = parseQuantity(maximum).value,
                            safetyStockMicros = parseQuantity(safety).value,
                            reorderPointMicros = parseQuantity(reorder).value,
                            preferredSupplierId = existing?.preferredSupplierId,
                            leadTimeDays = leadTime.toIntOrNull() ?: 0,
                            active = existing?.active ?: true,
                        ).validated()
                    }.onSuccess(onSave).onFailure { error = it.message ?: "اطلاعات کالا معتبر نیست." }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun InventoryLocationDialog(
    busy: Boolean,
    branches: List<BranchRecord>,
    onDismiss: () -> Unit,
    onSave: (InventoryLocationDraft) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val activeBranches = remember(branches) { branches.filter { it.isActive } }
    var selectedBranchId by rememberSaveable { mutableStateOf<Long?>(null) }
    var type by rememberSaveable { mutableStateOf(InventoryLocationType.WAREHOUSE) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("محل نگهداری جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                OutlinedTextField(code, { code = it.uppercase().take(20) }, label = { Text("کد یکتا؛ مانند MAIN یا FREEZER-1") })
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("نام محل") })
                CanonicalBranchSelector(
                    branches = branches,
                    selectedBranchId = selectedBranchId,
                    onBranchSelected = { selectedBranchId = it },
                    label = "شعبه محل / سطح سازمان",
                    allowAllBranches = true,
                    tag = "inventory_location_branch_selector",
                )
                SelectionField("نوع محل", locationTypeTitle(type), InventoryLocationType.entries.mapIndexed { index, value -> index.toLong() to locationTypeTitle(value) }) { type = InventoryLocationType.entries[it.toInt()] }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                runCatching {
                    InventoryLocationDraft(
                        code = code,
                        name = name,
                        type = type,
                        branchName = branches.firstOrNull { it.id == selectedBranchId }?.name.orEmpty(),
                        branchId = selectedBranchId,
                    ).validated()
                }
                    .onSuccess(onSave).onFailure { error = it.message }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun stockStatusTitle(status: InventoryStockStatus): String = when (status) {
    InventoryStockStatus.ALL -> "همه"
    InventoryStockStatus.HEALTHY -> "سالم"
    InventoryStockStatus.LOW -> "کمبود"
    InventoryStockStatus.OUT_OF_STOCK -> "اتمام"
}

internal fun itemTypeTitle(type: InventoryItemType): String = when (type) {
    InventoryItemType.INGREDIENT -> "ماده اولیه"
    InventoryItemType.PACKAGING -> "بسته‌بندی"
    InventoryItemType.CONSUMABLE -> "مصرفی"
    InventoryItemType.PREPARED_ITEM -> "نیمه‌آماده"
    InventoryItemType.FINISHED_GOOD -> "محصول نهایی"
}

private fun storageTitle(value: InventoryStorageCondition): String = when (value) {
    InventoryStorageCondition.AMBIENT -> "دمای محیط"
    InventoryStorageCondition.DRY -> "خشک"
    InventoryStorageCondition.CHILLED -> "سردخانه"
    InventoryStorageCondition.FROZEN -> "منجمد"
    InventoryStorageCondition.OTHER -> "سایر"
}

internal fun locationTypeTitle(type: InventoryLocationType): String = when (type) {
    InventoryLocationType.WAREHOUSE -> "انبار"
    InventoryLocationType.COLD_STORAGE -> "سردخانه"
    InventoryLocationType.FREEZER -> "فریزر"
    InventoryLocationType.KITCHEN -> "آشپزخانه"
    InventoryLocationType.PREP -> "آماده‌سازی"
    InventoryLocationType.BAR -> "بار"
    InventoryLocationType.OTHER -> "سایر"
}
