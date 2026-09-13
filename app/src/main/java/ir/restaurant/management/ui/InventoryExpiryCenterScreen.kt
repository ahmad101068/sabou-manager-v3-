package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.InventoryLot
import ir.restaurant.management.domain.inventory.InventoryLotDraft
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.WasteReason
import ir.restaurant.management.domain.security.Permission

private enum class ExpiryCenterFilter { EXPIRING, EXPIRED, QUARANTINED, ALL }

@Composable
internal fun InventoryExpiryCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
) {
    var filter by rememberSaveable { mutableStateOf(ExpiryCenterFilter.EXPIRING) }
    var showRegister by remember { mutableStateOf(false) }
    var wasteTarget by remember { mutableStateOf<InventoryLot?>(null) }
    val today = currentEpochDay()
    val visible = state.lots.asSequence().filter { lot ->
        when (filter) {
            ExpiryCenterFilter.EXPIRING -> lot.status == InventoryLotStatus.ACTIVE && lot.expiryEpochDay?.let { it in today..(today + 7) } == true
            ExpiryCenterFilter.EXPIRED -> lot.status == InventoryLotStatus.EXPIRED || lot.expiryEpochDay?.let { it < today } == true
            ExpiryCenterFilter.QUARANTINED -> lot.status in setOf(InventoryLotStatus.QUARANTINED, InventoryLotStatus.BLOCKED)
            ExpiryCenterFilter.ALL -> true
        }
    }.sortedWith(compareBy(nullsLast()) { it.expiryEpochDay }).toList()
    val canManage = state.currentUser?.role?.allows(Permission.INVENTORY_LOT_MANAGE) == true
    val canWaste = state.currentUser?.role?.allows(Permission.INVENTORY_WASTE_CREATE) == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpiryCenterFilter.entries.forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(expiryFilterTitle(value)) })
                }
            }
        }
        item {
            Button(
                onClick = { showRegister = true },
                enabled = canManage && state.items.any { it.trackLot } && state.locations.any { it.active },
            ) { Text("ثبت لات برای موجودی فعلی") }
        }
        item { SectionHeading("لات‌های ردیابی‌شده", "FEFO فقط لات فعال، غیرمنقضی و غیرقرنطینه را پیشنهاد می‌کند") }
        if (visible.isEmpty()) item { InventoryEmptyState("لاتی در این وضعیت پیدا نشد.") }
        items(visible, key = { "lot-${it.id}" }) { lot ->
            val item = state.items.firstOrNull { it.id == lot.itemId }
            val location = state.locations.firstOrNull { it.id == lot.locationId }
            val isPastExpiry = lot.expiryEpochDay?.let { it < today } == true
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item?.name ?: "کالا #${lot.itemId}"} · ${lot.lotNumber}", fontWeight = FontWeight.Bold)
                    Text(location?.name ?: "محل #${lot.locationId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CompactInfoRow("وضعیت", lotStatusTitle(lot.status), lot.status != InventoryLotStatus.ACTIVE || isPastExpiry)
                    CompactInfoRow("باقیمانده", "${formatQuantity(lot.remainingQuantityMicros)} ${item?.baseUnit.orEmpty()}")
                    CompactInfoRow("انقضا", lot.expiryEpochDay?.let { epochDayToPersian(it).display() } ?: "بدون تاریخ", isPastExpiry)
                    CompactInfoRow("بهای واحد ثبت‌شده", formatMoney(lot.unitCostRial))
                    lot.sourceReceiptId?.let { CompactInfoRow("رسید مبدأ", "#$it") }
                    if (canManage) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lot.status == InventoryLotStatus.ACTIVE && !isPastExpiry) {
                                OutlinedButton(
                                    onClick = { viewModel.changeLotStatus(lot, InventoryLotStatus.QUARANTINED, "قرنطینه عملیاتی لات") },
                                    modifier = Modifier.weight(1f),
                                ) { Text("قرنطینه") }
                            }
                            if (lot.status == InventoryLotStatus.ACTIVE && isPastExpiry) {
                                OutlinedButton(
                                    onClick = { viewModel.changeLotStatus(lot, InventoryLotStatus.EXPIRED, "تأیید انقضای لات") },
                                    modifier = Modifier.weight(1f),
                                ) { Text("ثبت منقضی") }
                            }
                            if (lot.status in setOf(InventoryLotStatus.QUARANTINED, InventoryLotStatus.BLOCKED) && !isPastExpiry) {
                                OutlinedButton(
                                    onClick = { viewModel.changeLotStatus(lot, InventoryLotStatus.ACTIVE, "رفع محدودیت لات پس از بررسی") },
                                    modifier = Modifier.weight(1f),
                                ) { Text("رفع محدودیت") }
                            }
                            if (canWaste && lot.remainingQuantityMicros > 0) {
                                Button(onClick = { wasteTarget = lot }, modifier = Modifier.weight(1f)) { Text("سند ضایعات") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegister) {
        RegisterInventoryLotDialog(
            state = state,
            onDismiss = { showRegister = false },
            onSave = { draft, reason -> viewModel.registerLot(draft, reason) { showRegister = false } },
        )
    }
    wasteTarget?.let { lot ->
        ExpiredLotWasteDialog(
            lot = lot,
            state = state,
            onDismiss = { wasteTarget = null },
            onSave = { command -> viewModel.submitWaste(command) { wasteTarget = null; viewModel.selectSection(InventoryWorkspaceSection.WASTE) } },
        )
    }
}

@Composable
private fun RegisterInventoryLotDialog(
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onSave: (InventoryLotDraft, String) -> Unit,
) {
    val trackedItems = state.items.filter { it.trackLot && it.active }
    val locations = state.locations.filter { it.active }
    var itemId by remember { mutableLongStateOf(trackedItems.firstOrNull()?.id ?: 0) }
    var locationId by remember { mutableLongStateOf(locations.firstOrNull()?.id ?: 0) }
    var lotNumber by rememberSaveable { mutableStateOf("") }
    var supplierLot by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unitCost by rememberSaveable { mutableStateOf("") }
    var receivedDay by remember { mutableLongStateOf(currentEpochDay()) }
    var expiryDay by remember { mutableStateOf<Long?>(null) }
    var reason by rememberSaveable { mutableStateOf("تخصیص لات به موجودی فعلی") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت لات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                SelectionField("کالا", trackedItems.firstOrNull { it.id == itemId }?.name, trackedItems.map { it.id to it.name }) { itemId = it }
                SelectionField("محل", locations.firstOrNull { it.id == locationId }?.name, locations.map { it.id to it.name }) { locationId = it }
                OutlinedTextField(lotNumber, { lotNumber = it.take(80) }, label = { Text("شماره لات") })
                OutlinedTextField(supplierLot, { supplierLot = it.take(80) }, label = { Text("لات تأمین‌کننده (اختیاری)") })
                OutlinedTextField(barcode, { barcode = it.take(80) }, label = { Text("بارکد لات (اختیاری)") })
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار قابل تخصیص") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(unitCost, { unitCost = formatMoneyInput(it) }, label = { Text("بهای واحد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                PersianDateField("تاریخ دریافت", receivedDay) { receivedDay = it }
                OptionalPersianDateField(
                    label = "تاریخ انقضا",
                    epochDay = expiryDay,
                    onSelected = { selectedExpiryEpochDay -> expiryDay = selectedExpiryEpochDay },
                )
                OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل ثبت") })
            }
        },
        confirmButton = {
            Button(enabled = !state.busy, onClick = {
                runCatching {
                    InventoryLotDraft(
                        itemId = itemId,
                        locationId = locationId,
                        lotNumber = lotNumber,
                        supplierLotNumber = supplierLot.ifBlank { null },
                        receivedEpochDay = receivedDay,
                        expiryEpochDay = expiryDay,
                        quantityMicros = parseQuantity(quantity).value,
                        unitCostRial = parseMoneyRial(unitCost).value,
                        barcode = barcode.ifBlank { null },
                        correlationId = "inventory:lot:${GlobalId.new().value}",
                    ).validated(trackedItems.first { it.id == itemId }.trackExpiry)
                }.onSuccess { onSave(it, reason) }.onFailure { error = it.message }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ExpiredLotWasteDialog(
    lot: InventoryLot,
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onSave: (CreateWasteCommand) -> Unit,
) {
    val actorId = state.currentUser?.id ?: 0
    var quantity by rememberSaveable(lot.id) { mutableStateOf(formatQuantity(lot.remainingQuantityMicros)) }
    var notes by rememberSaveable(lot.id) { mutableStateOf("") }
    var error by remember(lot.id) { mutableStateOf<String?>(null) }
    val isExpired = lot.status == InventoryLotStatus.EXPIRED || lot.expiryEpochDay?.let { it < currentEpochDay() } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ایجاد سند ضایعات لات ${lot.lotNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("این اقدام فقط سند ایجاد می‌کند؛ کاهش موجودی پس از ثبت نهایی صریح انجام می‌شود.")
                error?.let { MessageCard(it, true) }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("توضیحات") })
            }
        },
        confirmButton = {
            Button(enabled = actorId > 0 && !state.busy, onClick = {
                runCatching {
                    CreateWasteCommand(
                        itemId = lot.itemId,
                        locationId = lot.locationId,
                        lotId = lot.id,
                        quantityMicros = parseQuantity(quantity).value,
                        reason = if (isExpired) WasteReason.EXPIRED else WasteReason.OTHER,
                        businessEpochDay = currentEpochDay(),
                        reasonDetail = if (isExpired) "خروج کنترل‌شده لات منقضی" else "خروج کنترل‌شده لات",
                        notes = notes,
                        actorId = actorId,
                    ).validated()
                }.onSuccess(onSave).onFailure { error = it.message }
            }) { Text("ایجاد سند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun expiryFilterTitle(filter: ExpiryCenterFilter): String = when (filter) {
    ExpiryCenterFilter.EXPIRING -> "نزدیک انقضا"
    ExpiryCenterFilter.EXPIRED -> "منقضی"
    ExpiryCenterFilter.QUARANTINED -> "قرنطینه"
    ExpiryCenterFilter.ALL -> "همه"
}

internal fun lotStatusTitle(status: InventoryLotStatus): String = when (status) {
    InventoryLotStatus.ACTIVE -> "فعال"
    InventoryLotStatus.QUARANTINED -> "قرنطینه"
    InventoryLotStatus.EXPIRED -> "منقضی"
    InventoryLotStatus.DEPLETED -> "مصرف‌شده"
    InventoryLotStatus.BLOCKED -> "مسدود"
    InventoryLotStatus.LEGACY_UNKNOWN -> "قدیمی نامعتبر"
}
