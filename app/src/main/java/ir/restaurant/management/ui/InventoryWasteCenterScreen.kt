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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.WasteReason
import ir.restaurant.management.domain.inventory.WasteStatus
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun InventoryWasteCenterScreen(
    state: InventoryWorkspaceUiState,
    viewModel: InventoryWorkspaceViewModel,
) {
    var showCreate by remember { mutableStateOf(false) }
    val role = state.currentUser?.role
    val canCreate = role?.allows(Permission.INVENTORY_WASTE_CREATE) == true
    val canApprove = role?.allows(Permission.INVENTORY_WASTE_APPROVE) == true

    LaunchedEffect(state.pendingAction) {
        if (state.pendingAction == InventoryWorkspaceAction.CREATE_WASTE) {
            showCreate = true
            viewModel.consumeAction(InventoryWorkspaceAction.CREATE_WASTE)
        }
    }
    val today = currentEpochDay()
    val weekCost = state.wasteDocuments.filter { it.status == WasteStatus.POSTED && it.businessEpochDay >= today - 6 }
        .fold(0L) { total, row -> SignedLongMath.add(total, row.totalCostRial) }
    val monthCost = state.wasteDocuments.filter { it.status == WasteStatus.POSTED && it.businessEpochDay >= today - 29 }
        .fold(0L) { total, row -> SignedLongMath.add(total, row.totalCostRial) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("هفته", formatMoney(weekCost), Modifier.weight(1f))
                MetricTile("۳۰ روز", formatMoney(monthCost), Modifier.weight(1f))
            }
        }
        item { Button(onClick = { showCreate = true }, enabled = canCreate && state.items.isNotEmpty() && state.locations.isNotEmpty() && !state.busy) { Text("سند ضایعات جدید") } }
        item { SectionHeading("اسناد ضایعات", "کاهش موجودی و اثر حسابداری فقط در ثبت نهایی اتمیک انجام می‌شود") }
        if (!state.loading && state.wasteDocuments.isEmpty()) item { InventoryEmptyState("سند ضایعاتی ثبت نشده است.") }
        items(state.wasteDocuments, key = { "waste-${it.id}" }) { document ->
            val item = state.items.firstOrNull { it.id == document.itemId }
            val location = state.locations.firstOrNull { it.id == document.locationId }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${document.documentNumber} · ${item?.name ?: "کالا #${document.itemId}"}", fontWeight = FontWeight.Bold)
                    Text("${location?.name ?: "محل #${document.locationId}"} · ${epochDayToPersian(document.businessEpochDay).display()}", style = MaterialTheme.typography.bodySmall)
                    CompactInfoRow("شعبه", location?.branchName?.ifBlank { "سطح سازمان" } ?: "—")
                    CompactInfoRow("وضعیت", wasteStatusTitle(document.status), document.status == WasteStatus.PENDING_APPROVAL)
                    CompactInfoRow("علت", wasteReasonTitle(document.reason))
                    CompactInfoRow("مقدار", "${formatQuantity(document.quantityMicros)} ${item?.baseUnit.orEmpty()}")
                    CompactInfoRow("بهای تاریخی", formatMoney(document.totalCostRial), true)
                    document.lotId?.let { CompactInfoRow("لات", "#$it") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (document.status == WasteStatus.PENDING_APPROVAL && canApprove) {
                            Button(onClick = { viewModel.approveWaste(document.id, "تأیید مستند ضایعات") }, modifier = Modifier.weight(1f)) { Text("تأیید") }
                        }
                        if (document.status == WasteStatus.APPROVED && canCreate) {
                            Button(onClick = { viewModel.postWaste(document.id) }, modifier = Modifier.weight(1f)) { Text("ثبت در دفترکل") }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreateWasteDialog(
            state = state,
            onDismiss = { showCreate = false },
            onSave = { viewModel.submitWaste(it) { showCreate = false } },
        )
    }
}

@Composable
private fun CreateWasteDialog(
    state: InventoryWorkspaceUiState,
    onDismiss: () -> Unit,
    onSave: (CreateWasteCommand) -> Unit,
) {
    val items = state.items.filter { it.active }
    val locations = state.locations.filter { it.active }
    var itemId by remember { mutableLongStateOf(items.firstOrNull()?.id ?: 0) }
    var locationId by remember { mutableLongStateOf(locations.firstOrNull()?.id ?: 0) }
    val matchingLots = state.lots.filter { it.itemId == itemId && it.locationId == locationId && it.remainingQuantityMicros > 0 }
    var lotId by remember(itemId, locationId) { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf(WasteReason.SPOILAGE) }
    var detail by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var businessDay by remember { mutableLongStateOf(currentEpochDay()) }
    var error by remember { mutableStateOf<String?>(null) }
    val actorId = state.currentUser?.id ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سند ضایعات جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { MessageCard(it, true) }
                SelectionField("کالا", items.firstOrNull { it.id == itemId }?.name, items.map { it.id to it.name }) { itemId = it }
                SelectionField(
                    "محل",
                    locations.firstOrNull { it.id == locationId }?.let(::wasteLocationLabel),
                    locations.map { it.id to wasteLocationLabel(it) },
                ) { locationId = it }
                if (matchingLots.isNotEmpty()) SelectionField(
                    "لات (برای کالای لات‌محور الزامی)",
                    matchingLots.firstOrNull { it.id == lotId }?.lotNumber,
                    listOf(0L to "انتخاب نشده") + matchingLots.map { it.id to "${it.lotNumber} · ${formatQuantity(it.remainingQuantityMicros)}" },
                ) { lotId = it.takeIf { id -> id != 0L } }
                OutlinedTextField(quantity, { quantity = it }, label = { Text("مقدار") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                SelectionField("علت", wasteReasonTitle(reason), WasteReason.entries.filter { it != WasteReason.LEGACY_UNKNOWN }.mapIndexed { index, value -> index.toLong() to wasteReasonTitle(value) }) { index -> reason = WasteReason.entries.filter { it != WasteReason.LEGACY_UNKNOWN }[index.toInt()] }
                PersianDateField("تاریخ عملیاتی", businessDay) { businessDay = it }
                OutlinedTextField(detail, { detail = it.take(300) }, label = { Text("شرح علت") })
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text("یادداشت") })
            }
        },
        confirmButton = {
            Button(enabled = !state.busy && actorId > 0, onClick = {
                runCatching {
                    CreateWasteCommand(
                        itemId = itemId,
                        locationId = locationId,
                        lotId = lotId,
                        quantityMicros = parseQuantity(quantity).value,
                        reason = reason,
                        businessEpochDay = businessDay,
                        reasonDetail = detail,
                        notes = notes,
                        actorId = actorId,
                    ).validated()
                }.onSuccess(onSave).onFailure { error = it.message }
            }) { Text("ایجاد سند") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun wasteLocationLabel(location: ir.restaurant.management.domain.inventory.InventoryLocationRecord): String =
    "${location.name} · ${location.branchName.ifBlank { "سطح سازمان" }}"

internal fun wasteReasonTitle(reason: WasteReason): String = when (reason) {
    WasteReason.SPOILAGE -> "فساد"
    WasteReason.EXPIRED -> "انقضا"
    WasteReason.PREPARATION_WASTE -> "ضایعات آماده‌سازی"
    WasteReason.OVERPRODUCTION -> "تولید مازاد"
    WasteReason.QUALITY_REJECT -> "رد کیفی"
    WasteReason.DAMAGE -> "آسیب"
    WasteReason.STAFF_MEAL -> "غذای کارکنان"
    WasteReason.COMPLIMENTARY -> "پذیرایی رایگان"
    WasteReason.OTHER -> "سایر"
    WasteReason.LEGACY_UNKNOWN -> "علت قدیمی نامعتبر"
}

private fun wasteStatusTitle(status: WasteStatus): String = when (status) {
    WasteStatus.PENDING_APPROVAL -> "در انتظار تأیید"
    WasteStatus.APPROVED -> "تأییدشده؛ آماده ثبت نهایی"
    WasteStatus.POSTED -> "ثبت‌شده"
    WasteStatus.LEGACY_UNKNOWN -> "وضعیت قدیمی نامعتبر"
}
